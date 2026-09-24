package org.fog.utils;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.Predicate;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.Tuple;

/**
 * Bandwidth slices for transport links and wireless access points.
 *
 * A slice reserves its configured percentage of every cloudlet-to-cloudlet
 * link and each access point's uplink and downlink capacity. While a slice has
 * no active migration on a transport link, its reservation can be borrowed by
 * migrations in the active slices on that link. Wireless capacity is allocated
 * independently for each AP direction from active tuple transfers. Idle
 * associated users consume no capacity; in dynamic mode, active slices borrow
 * reservations belonging to slices with no active flow on that AP direction.
 * A configured reallocation delay pauses all transfers on an affected sliced
 * resource while a changed active-slice allocation is installed.
 */
public final class NetworkSlicing {

	public static final int TRANSPORT_NETWORK = 0;
	public static final int WIRELESS_NETWORK = 1;
	public static final int END_TO_END_NETWORK = 2;
	public static final double DEFAULT_REALLOCATION_DELAY_SECONDS = 2.0;
	public static final int DEFAULT_MAXIMUM_WIRELESS_QUEUE_SIZE = 10000;
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	private static RuntimeState activeState = RuntimeState.defaults();

	/** AP channel direction. Uplink and downlink capacity are independent. */
	public enum WirelessDirection {
		UPLINK,
		DOWNLINK
	}

	/** Portion of the network on which slice reservations are enforced. */
	public enum Scope {
		TRANSPORT(TRANSPORT_NETWORK, true, false),
		WIRELESS(WIRELESS_NETWORK, false, true),
		END_TO_END(END_TO_END_NETWORK, true, true);

		private final int legacyValue;
		private final boolean transport;
		private final boolean wireless;

		Scope(int legacyValue, boolean transport, boolean wireless) {
			this.legacyValue = legacyValue;
			this.transport = transport;
			this.wireless = wireless;
		}

		public int legacyValue() {
			return legacyValue;
		}

		public boolean coversTransportNetwork() {
			return transport;
		}

		public boolean coversWirelessNetwork() {
			return wireless;
		}

		public static Scope fromLegacy(int value) {
			for (Scope scope : values()) {
				if (scope.legacyValue == value) {
					return scope;
				}
			}
			throw new IllegalArgumentException(
				"Network slice scope must be 0 (transport), 1 (wireless), or 2 (end-to-end)");
		}
	}

	/** Whether idle slice capacity remains reserved or may be borrowed. */
	public enum Mode {
		FIXED(false),
		DYNAMIC(true);

		private final boolean dynamicBorrowing;

		Mode(boolean dynamicBorrowing) {
			this.dynamicBorrowing = dynamicBorrowing;
		}

		public boolean allowsDynamicBorrowing() {
			return dynamicBorrowing;
		}

		public static Mode fromDynamicBorrowing(boolean enabled) {
			return enabled ? DYNAMIC : FIXED;
		}
	}

	/** Immutable, fully validated slicing settings for one simulation run. */
	public static final class Configuration {
		private final double[] bandwidthPercentages;
		private final double[] userPercentages;
		private final Mode mode;
		private final Scope scope;
		private final SimulationDuration reallocationDelay;

		private Configuration(double[] bandwidthPercentages,
			double[] userPercentages, Scope scope, Mode mode,
			SimulationDuration reallocationDelay) {
			this.bandwidthPercentages = bandwidthPercentages.clone();
			this.userPercentages = userPercentages.clone();
			if (scope == null || mode == null || reallocationDelay == null) {
				throw new IllegalArgumentException(
					"Slicing scope, mode, and reallocation delay cannot be null");
			}
			this.scope = scope;
			this.mode = mode;
			this.reallocationDelay = reallocationDelay;
		}

		public Scope getScope() {
			return scope;
		}

		public Mode getMode() {
			return mode;
		}

		public double[] getBandwidthPercentages() {
			return bandwidthPercentages.clone();
		}

		public double[] getUserPercentages() {
			return userPercentages.clone();
		}

		/** Time required to apply a dynamic slice allocation, in seconds. */
		public double getReallocationDelaySeconds() {
			return reallocationDelay.toSeconds();
		}

		public SimulationDuration getReallocationDelay() {
			return reallocationDelay;
		}
	}

	/** Mutable schedulers and transfer registries owned by one simulation run. */
	public static final class RuntimeState {
		private double[] percentages;
		private double[] userAllocationPercentages;
		private Mode mode;
		private Scope scope;
		private double reallocationDelayMillis;
		private final SliceReconfigurationMetrics reconfigurationMetrics;
		private final Map<Integer, MigrationTransferMetadata> migrationTransfers =
			new HashMap<Integer, MigrationTransferMetadata>();
		private MigrationTransferScheduler migrationScheduler;
		private final Map<Long, WirelessTransferMetadata> wirelessTransfers =
			new HashMap<Long, WirelessTransferMetadata>();
		private final Map<String, Long> activeWirelessTransfers =
			new HashMap<String, Long>();
		private final Map<String, ArrayDeque<Long>> queuedWirelessTransfers =
			new HashMap<String, ArrayDeque<Long>>();
		private AccessPointTransferScheduler wirelessScheduler;
		private long nextWirelessTransferId = 1L;
		private long queuedWirelessTransferCount;
		private long maximumQueuedWirelessTransferCount;
		private int maximumWirelessQueueDepth;
		private long droppedWirelessTupleCount;

		private RuntimeState(Configuration configuration) {
			percentages = configuration.bandwidthPercentages.clone();
			userAllocationPercentages = configuration.userPercentages.clone();
			mode = configuration.mode;
			scope = configuration.scope;
			reallocationDelayMillis = configuration.reallocationDelay
				.toMilliseconds();
			reconfigurationMetrics =
				new SliceReconfigurationMetrics(percentages.length);
			migrationScheduler = new MigrationTransferScheduler(percentages,
				scope.coversTransportNetwork(), mode.allowsDynamicBorrowing(),
				reallocationDelayMillis, reconfigurationMetrics);
			wirelessScheduler = new AccessPointTransferScheduler(percentages,
				scope.coversWirelessNetwork(), mode.allowsDynamicBorrowing(),
				reallocationDelayMillis, reconfigurationMetrics);
		}

		private static RuntimeState defaults() {
			return new RuntimeState(new Configuration(new double[] { 100.0 },
				new double[] { 100.0 }, Scope.END_TO_END, Mode.DYNAMIC,
				SimulationDuration.ofSeconds(
					DEFAULT_REALLOCATION_DELAY_SECONDS)));
		}
	}

	/** Versioned payload used to ignore an obsolete completion event. */
	public static final class MigrationTransferCompletion {
		private final int transferId;
		private final long generation;

		private MigrationTransferCompletion(int transferId, long generation) {
			this.transferId = transferId;
			this.generation = generation;
		}
	}

	/** Versioned internal event used to complete an active wireless transfer. */
	public static final class WirelessTransferCompletion {
		private final long transferId;
		private final long generation;

		private WirelessTransferCompletion(long transferId, long generation) {
			this.transferId = transferId;
			this.generation = generation;
		}
	}

	/** Tuple delivery released after an accepted AP byte-transfer completion. */
	public static final class WirelessTransferResult {
		private final Tuple tuple;
		private final EntityId destinationEntityId;
		private final PropagationDelay propagationDelay;
		private final SimulationDuration queueDuration;
		private final SimulationDuration transferDuration;

		private WirelessTransferResult(WirelessTransferMetadata metadata,
			AccessPointTransferScheduler.Completion completion) {
			this.tuple = metadata.tuple;
			this.destinationEntityId = metadata.destinationEntityId;
			this.propagationDelay = metadata.propagationDelay;
			this.queueDuration = SimulationDuration.ofMilliseconds(
				metadata.getQueueDurationMillis());
			this.transferDuration = SimulationDuration.ofMilliseconds(
				completion.getDurationMillis());
		}

		public Tuple getTuple() {
			return tuple;
		}

		public int getDestinationEntityId() {
			return destinationEntityId.intValue();
		}

		public EntityId getDestination() {
			return destinationEntityId;
		}

		public double getPropagationDelayMillis() {
			return propagationDelay.toMilliseconds();
		}

		public PropagationDelay getPropagationDelay() {
			return propagationDelay;
		}

		public double getQueueDurationMillis() {
			return queueDuration.toMilliseconds();
		}

		public SimulationDuration getQueueDuration() {
			return queueDuration;
		}

		public double getTransferDurationMillis() {
			return transferDuration.toMilliseconds();
		}

		public SimulationDuration getTransferDuration() {
			return transferDuration;
		}
	}

	/**
	 * Accepted completion of the byte-transfer phase. Fixed delay remains to be
	 * applied by the receiving simulation entity without holding link capacity.
	 */
	public static final class MigrationTransferResult {
		private final MobileDevice mobileDevice;
		private final DataSize transferredData;
		private final SimulationDuration transferDuration;
		private final SimulationDuration reallocationDelay;
		private final SimulationDuration fixedDelay;
		private final long migrationGeneration;

		private MigrationTransferResult(MigrationTransferMetadata metadata,
			MigrationTransferScheduler.Completion completion) {
			this.mobileDevice = metadata.spec.getMobileDevice();
			this.transferredData = DataSize.ofBytes(
				completion.getTransferredBytes());
			this.transferDuration = SimulationDuration.ofMilliseconds(
				completion.getDuration());
			this.reallocationDelay = SimulationDuration.ofMilliseconds(
				completion.getReallocationDelayDuration());
			this.fixedDelay = metadata.spec.getFixedDelay();
			this.migrationGeneration = metadata.spec.getMigrationGeneration();
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
		}

		public double getTransferredBytes() {
			return transferredData.toBytes();
		}

		public DataSize getTransferredData() {
			return transferredData;
		}

		/** Wall-clock byte-transfer phase, including reallocation pauses. */
		public double getTransferDurationMillis() {
			return transferDuration.toMilliseconds();
		}

		public SimulationDuration getTransferDuration() {
			return transferDuration;
		}

		/** Portion of the transfer phase spent paused for slice reallocation. */
		public double getReallocationDelayMillis() {
			return reallocationDelay.toMilliseconds();
		}

		public SimulationDuration getReallocationDelay() {
			return reallocationDelay;
		}

		/** Time during which bytes actually traversed the transport link. */
		public double getDataTransferDurationMillis() {
			return Math.max(0.0,
				transferDuration.toMilliseconds()
					- reallocationDelay.toMilliseconds());
		}

		public SimulationDuration getDataTransferDuration() {
			return SimulationDuration.ofMilliseconds(
				getDataTransferDurationMillis());
		}

		public double getFixedDelayMillis() {
			return fixedDelay.toMilliseconds();
		}

		public SimulationDuration getFixedDelay() {
			return fixedDelay;
		}

		public long getMigrationGeneration() {
			return migrationGeneration;
		}

		public double getTotalDurationMillis() {
			return transferDuration.toMilliseconds()
				+ fixedDelay.toMilliseconds();
		}
	}

	private static final class MigrationTransferMetadata {
		private final MigrationTransferSpec spec;
		private final EntityId eventSourceId;
		private final EntityId completionDestinationId;
		private final int completionEventTag;

		private MigrationTransferMetadata(MigrationTransferSpec spec) {
			this.spec = spec;
			this.eventSourceId = spec.getCompletionDestination();
			this.completionDestinationId = spec.getCompletionDestination();
			this.completionEventTag = spec.getCompletionEventTag();
		}
	}

	private static final class WirelessTransferMetadata {
		private final long transferId;
		private final ApDevice accessPoint;
		private final MobileDevice mobileDevice;
		private final WirelessDirection direction;
		private final Tuple tuple;
		private final EntityId eventSourceId;
		private final EntityId destinationEntityId;
		private final PropagationDelay propagationDelay;
		private final String mobileDirectionKey;
		private final double enqueuedAtMillis;
		private double activatedAtMillis = Double.NaN;
		private boolean active;

		private WirelessTransferMetadata(long transferId, ApDevice accessPoint,
			MobileDevice mobileDevice, WirelessDirection direction, Tuple tuple,
			EntityId eventSourceId, EntityId destinationEntityId,
			PropagationDelay propagationDelay) {
			this.transferId = transferId;
			this.accessPoint = accessPoint;
			this.mobileDevice = mobileDevice;
			this.direction = direction;
			this.tuple = tuple;
			this.eventSourceId = eventSourceId;
			this.destinationEntityId = destinationEntityId;
			this.propagationDelay = propagationDelay;
			this.mobileDirectionKey = mobileDirectionKey(mobileDevice, direction);
			this.enqueuedAtMillis = CloudSim.clock();
		}

		private double getQueueDurationMillis() {
			if (!Double.isFinite(activatedAtMillis)) {
				throw new IllegalStateException(
					"A completed wireless transfer must have an activation time");
			}
			return Math.max(0.0, activatedAtMillis - enqueuedAtMillis);
		}
	}

	private static final class MigrationCompletionPredicate extends Predicate {
		private final int transferId;

		private MigrationCompletionPredicate(int transferId) {
			this.transferId = transferId;
		}

		@Override
		public boolean match(SimEvent event) {
			Object data = event.getData();
			return data instanceof MigrationTransferCompletion
				&& ((MigrationTransferCompletion) data).transferId == transferId;
		}
	}

	private static final class WirelessCompletionPredicate extends Predicate {
		private final long transferId;

		private WirelessCompletionPredicate(long transferId) {
			this.transferId = transferId;
		}

		@Override
		public boolean match(SimEvent event) {
			Object data = event.getData();
			return data instanceof WirelessTransferCompletion
				&& ((WirelessTransferCompletion) data).transferId == transferId;
		}
	}

	private NetworkSlicing() {
	}

	/** Creates isolated runtime state without changing the active run. */
	public static RuntimeState createRuntimeState(Configuration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException(
				"Network slicing configuration cannot be null");
		}
		return new RuntimeState(configuration);
	}

	/** Activates slicing state owned by the current simulation context. */
	public static synchronized void useRuntimeState(RuntimeState runtimeState) {
		if (runtimeState == null) {
			throw new IllegalArgumentException("Network slicing state cannot be null");
		}
		activeState = runtimeState;
	}

	/** Replaces the active state with clean default slicing. */
	public static synchronized void useDefaultRuntimeState() {
		resetUsage();
		activeState = RuntimeState.defaults();
	}

	private static RuntimeState state() {
		return activeState;
	}

	/**
	 * Configures slices from a comma-separated percentage list, for example
	 * {@code "50,50"} or {@code "40,30,20,10"}.
	 */
	public static void configure(String percentageList) {
		applyConfiguration(parseConfiguration(percentageList, null, state().scope,
			state().mode, getReallocationDelay()));
	}

	/**
	 * Parses every slicing option without changing global slicing state.
	 */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, int selectedScope, boolean useDynamicBorrowing) {
		return parseConfiguration(bandwidthPercentageList, userPercentageList,
			Scope.fromLegacy(selectedScope),
			Mode.fromDynamicBorrowing(useDynamicBorrowing),
			DEFAULT_REALLOCATION_DELAY_SECONDS);
	}

	/** Parses every slicing option, including reallocation delay in seconds. */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, int selectedScope, boolean useDynamicBorrowing,
		double reallocationDelaySeconds) {
		return parseConfiguration(bandwidthPercentageList, userPercentageList,
			Scope.fromLegacy(selectedScope),
			Mode.fromDynamicBorrowing(useDynamicBorrowing),
			SimulationDuration.ofSeconds(reallocationDelaySeconds));
	}

	/** Parses typed slicing options without changing global slicing state. */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, Scope selectedScope, Mode selectedMode) {
		return parseConfiguration(bandwidthPercentageList, userPercentageList,
			selectedScope, selectedMode, SimulationDuration.ofSeconds(
				DEFAULT_REALLOCATION_DELAY_SECONDS));
	}

	/** Parses typed slicing options, including reallocation delay in seconds. */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, Scope selectedScope, Mode selectedMode,
		double reallocationDelaySeconds) {
		return parseConfiguration(bandwidthPercentageList, userPercentageList,
			selectedScope, selectedMode,
			SimulationDuration.ofSeconds(reallocationDelaySeconds));
	}

	/** Parses typed slicing options with a unit-bearing reallocation delay. */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, Scope selectedScope, Mode selectedMode,
		SimulationDuration reallocationDelay) {
		if (selectedScope == null || selectedMode == null) {
			throw new IllegalArgumentException("Slicing scope and mode cannot be null");
		}
		if (reallocationDelay == null) {
			throw new IllegalArgumentException(
				"Slicing reallocation delay cannot be null");
		}
		double[] parsedBandwidthPercentages = parsePercentagesOrDefault(
			bandwidthPercentageList, "Network slice");
		double[] parsedUserPercentages;
		if (userPercentageList == null || userPercentageList.trim().isEmpty()) {
			parsedUserPercentages = equalPercentages(parsedBandwidthPercentages.length);
		}
		else {
			parsedUserPercentages = parsePercentages(userPercentageList,
				"User allocation");
			if (parsedUserPercentages.length != parsedBandwidthPercentages.length) {
				throw new IllegalArgumentException(
					"User allocation must contain one percentage for each network slice");
			}
		}
		return new Configuration(parsedBandwidthPercentages, parsedUserPercentages,
			selectedScope, selectedMode, reallocationDelay);
	}

	/** Applies one already validated slicing configuration in a single update. */
	public static synchronized void applyConfiguration(Configuration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Network slicing configuration cannot be null");
		}
		state().percentages = configuration.bandwidthPercentages.clone();
		state().userAllocationPercentages = configuration.userPercentages.clone();
		state().mode = configuration.mode;
		state().scope = configuration.scope;
		state().reallocationDelayMillis = configuration.reallocationDelay
			.toMilliseconds();
		resetUsage();
	}

	/**
	 * Configures the percentage of users assigned to each slice. The number of
	 * values must match the number of configured bandwidth slices.
	 */
	public static void configureUserAllocation(String percentageList) {
		if (percentageList == null || percentageList.trim().isEmpty()) {
			state().userAllocationPercentages =
				equalPercentages(state().percentages.length);
			return;
		}

		double[] parsed = parsePercentages(percentageList, "User allocation");
		if (parsed.length != state().percentages.length) {
			throw new IllegalArgumentException(
				"User allocation must contain one percentage for each network slice");
		}
		state().userAllocationPercentages = parsed;
	}

	public static int getSliceCount() {
		return state().percentages.length;
	}

	/** Enables (true) or disables (false) borrowing of idle slice capacity. */
	public static void setDynamicBorrowing(boolean enabled) {
		setMode(Mode.fromDynamicBorrowing(enabled));
	}

	public static void setMode(Mode mode) {
		if (mode == null) {
			throw new IllegalArgumentException("Network slicing mode cannot be null");
		}
		state().mode = mode;
		resetUsage();
	}

	public static boolean isDynamicBorrowing() {
		return state().mode.allowsDynamicBorrowing();
	}

	public static Mode getMode() {
		return state().mode;
	}

	/**
	 * Selects where slicing is applied: {@link #TRANSPORT_NETWORK},
	 * {@link #WIRELESS_NETWORK}, or {@link #END_TO_END_NETWORK}.
	 */
	public static void setScope(int selectedScope) {
		setScope(Scope.fromLegacy(selectedScope));
	}

	public static void setScope(Scope selectedScope) {
		if (selectedScope == null) {
			throw new IllegalArgumentException("Network slicing scope cannot be null");
		}
		state().scope = selectedScope;
		resetUsage();
	}

	public static int getScope() {
		return state().scope.legacyValue();
	}

	public static Scope getTypedScope() {
		return state().scope;
	}

	/** Sets the dynamic slice reallocation penalty, in seconds. */
	public static void setReallocationDelaySeconds(double seconds) {
		setReallocationDelay(SimulationDuration.ofSeconds(seconds));
	}

	/** Sets the dynamic slice reallocation penalty with an explicit unit. */
	public static void setReallocationDelay(SimulationDuration delay) {
		if (delay == null) {
			throw new IllegalArgumentException(
				"Network slicing reallocation delay cannot be null");
		}
		state().reallocationDelayMillis = delay.toMilliseconds();
		resetUsage();
	}

	/** Returns the dynamic slice reallocation penalty, in seconds. */
	public static double getReallocationDelaySeconds() {
		return state().reallocationDelayMillis / MILLISECONDS_PER_SECOND;
	}

	public static SimulationDuration getReallocationDelay() {
		return SimulationDuration.ofMilliseconds(
			state().reallocationDelayMillis);
	}

	/** Fixed maximum FIFO depth retained for one mobile and AP direction. */
	public static int getMaximumWirelessQueueSize() {
		return DEFAULT_MAXIMUM_WIRELESS_QUEUE_SIZE;
	}

	/** Number of wireless tuples still waiting behind an active transfer. */
	public static synchronized long getQueuedWirelessTransferCount() {
		return state().queuedWirelessTransferCount;
	}

	/** Largest aggregate wireless FIFO population observed during this run. */
	public static synchronized long getMaximumQueuedWirelessTransferCount() {
		return state().maximumQueuedWirelessTransferCount;
	}

	/** Largest FIFO depth observed for one mobile and AP direction. */
	public static synchronized int getMaximumWirelessQueueDepth() {
		return state().maximumWirelessQueueDepth;
	}

	/** Tuples discarded because their per-direction FIFO reached its limit. */
	public static synchronized long getDroppedWirelessTupleCount() {
		return state().droppedWirelessTupleCount;
	}

	/** Number of dynamic active-slice allocation changes in this run. */
	public static synchronized long getReconfigurationCount() {
		return state().reconfigurationMetrics.getReconfigurationCount();
	}

	/** Aggregate configured outage across all reconfigurations, in seconds. */
	public static synchronized double getSliceOutageSeconds() {
		return getReconfigurationCount() * getReallocationDelaySeconds();
	}

	/**
	 * Per-slice sum of bandwidth allocated at each reconfiguration, in bits per
	 * second. Transport links and wireless AP directions are combined.
	 */
	public static synchronized double[] getReceivedBandwidthBySlice() {
		return state().reconfigurationMetrics.getReceivedBandwidthBySlice();
	}

	public static boolean coversTransportNetwork() {
		return state().scope.coversTransportNetwork();
	}

	public static boolean coversWirelessNetwork() {
		return state().scope.coversWirelessNetwork();
	}

	public static double getPercentage(int sliceId) {
		return state().percentages[validateSliceId(sliceId)];
	}

	public static double getUserAllocationPercentage(int sliceId) {
		return state().userAllocationPercentages[validateSliceId(sliceId)];
	}

	/**
	 * Returns integer user quotas using the largest-remainder method, so all
	 * users are assigned even when percentages produce fractional counts.
	 */
	public static int[] getUserAllocations(int totalUsers) {
		if (totalUsers < 0) {
			throw new IllegalArgumentException("Total users cannot be negative");
		}

		int[] allocations = new int[state().userAllocationPercentages.length];
		double[] remainders = new double[state().userAllocationPercentages.length];
		double configuredTotal = 0.0;
		for (double percentage : state().userAllocationPercentages) {
			configuredTotal += percentage;
		}

		int assignedUsers = 0;
		for (int sliceId = 0; sliceId < allocations.length; sliceId++) {
			double exactAllocation = totalUsers
				* state().userAllocationPercentages[sliceId] / configuredTotal;
			allocations[sliceId] = (int) Math.floor(exactAllocation);
			remainders[sliceId] = exactAllocation - allocations[sliceId];
			assignedUsers += allocations[sliceId];
		}

		while (assignedUsers < totalUsers) {
			int selectedSlice = 0;
			for (int sliceId = 1; sliceId < remainders.length; sliceId++) {
				if (remainders[sliceId] > remainders[selectedSlice]) {
					selectedSlice = sliceId;
				}
			}
			allocations[selectedSlice]++;
			remainders[selectedSlice] = -1.0;
			assignedUsers++;
		}
		return allocations;
	}

	/** Returns the slice assigned to a zero-based user index. */
	public static int getSliceForUser(int userIndex, int totalUsers) {
		if (userIndex < 0 || userIndex >= totalUsers) {
			throw new IllegalArgumentException("User index is outside the configured population");
		}
		return getUserSliceAssignments(totalUsers)[userIndex];
	}

	/** Returns one slice ID for every user in the configured population. */
	public static int[] getUserSliceAssignments(int totalUsers) {
		int[] allocations = getUserAllocations(totalUsers);
		int[] assignments = new int[totalUsers];
		int userIndex = 0;
		for (int sliceId = 0; sliceId < allocations.length; sliceId++) {
			for (int userInSlice = 0; userInSlice < allocations[sliceId]; userInSlice++) {
				assignments[userIndex++] = sliceId;
			}
		}
		if (userIndex != totalUsers) {
			throw new IllegalStateException("User allocation percentages did not assign every user");
		}
		return assignments;
	}

	/** Returns the fixed capacity available to one slice on a physical link. */
	public static double getSliceBandwidth(double physicalBandwidth, int sliceId) {
		validateNonNegativeFinite(physicalBandwidth, "Physical bandwidth");
		if (physicalBandwidth == 0.0) {
			return physicalBandwidth;
		}
		return physicalBandwidth * getPercentage(sliceId) / 100.0;
	}

	/**
	 * Returns the bandwidth of the directed source-to-destination cloudlet link
	 * for a user slice. This is the same physical-link calculation used when
	 * AppExample builds the cloudlet network.
	 */
	public static double getSliceBandwidth(FogDevice source, FogDevice destination,
		int sliceId) {
		validateSliceId(sliceId);
		double physicalBandwidth = getPhysicalBandwidth(source, destination);
		if (!coversTransportNetwork()) {
			return physicalBandwidth;
		}
		return getSliceBandwidth(physicalBandwidth, sliceId);
	}

	/**
	 * Returns the maximum uplink rate for a prospective single active flow. The
	 * event-level scheduler calculates the actual rate from all active flows.
	 */
	public static double getAccessPointUplinkBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice) {
		return getAccessPointBandwidth(accessPoint, mobileDevice,
			accessPoint == null ? 0.0 : accessPoint.getUplinkBandwidth(),
			mobileDevice == null ? 0.0 : mobileDevice.getUplinkBandwidth());
	}

	/**
	 * Returns the maximum downlink rate for a prospective single active flow. The
	 * event-level scheduler calculates the actual rate from all active flows.
	 */
	public static double getAccessPointDownlinkBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice) {
		return getAccessPointBandwidth(accessPoint, mobileDevice,
			accessPoint == null ? 0.0 : accessPoint.getDownlinkBandwidth(),
			mobileDevice == null ? 0.0 : mobileDevice.getDownlinkBandwidth());
	}

	/**
	 * Adds a tuple to one AP direction. Each mobile can have one active transfer
	 * in a direction; further tuples wait in FIFO order without consuming AP
	 * capacity. Returns the internal transfer ID used for diagnostics and tests.
	 */
	public static synchronized long startWirelessTupleTransfer(
		ApDevice accessPoint, MobileDevice mobileDevice,
		WirelessDirection direction, Tuple tuple, int eventSourceId,
		int destinationEntityId, double propagationDelayMillis) {
		return startWirelessTupleTransfer(accessPoint, mobileDevice, direction,
			tuple, EntityId.of(eventSourceId), EntityId.of(destinationEntityId),
			PropagationDelay.ofMilliseconds(propagationDelayMillis));
	}

	/** Adds a wireless tuple transfer with typed routing and delay values. */
	public static synchronized long startWirelessTupleTransfer(
		ApDevice accessPoint, MobileDevice mobileDevice,
		WirelessDirection direction, Tuple tuple, EntityId eventSourceId,
		EntityId destinationEntityId, PropagationDelay propagationDelay) {
		validateWirelessTransfer(accessPoint, mobileDevice, direction, tuple,
			eventSourceId, destinationEntityId, propagationDelay);
		long transferId = state().nextWirelessTransferId++;
		WirelessTransferMetadata metadata = new WirelessTransferMetadata(
			transferId, accessPoint, mobileDevice, direction, tuple, eventSourceId,
			destinationEntityId, propagationDelay);
		state().wirelessTransfers.put(transferId, metadata);

		if (!state().activeWirelessTransfers.containsKey(metadata.mobileDirectionKey)) {
			state().activeWirelessTransfers.put(metadata.mobileDirectionKey, transferId);
			startWirelessTransfer(metadata);
		}
		else {
			ArrayDeque<Long> queue = state().queuedWirelessTransfers.get(
				metadata.mobileDirectionKey);
			if (queue == null) {
				queue = new ArrayDeque<Long>();
				state().queuedWirelessTransfers.put(metadata.mobileDirectionKey, queue);
			}
			enqueueWirelessTransfer(queue, transferId);
		}
		return transferId;
	}

	/** Accepts a current AP completion event and releases its tuple for delivery. */
	public static synchronized WirelessTransferResult completeWirelessTransfer(
		WirelessTransferCompletion completion) {
		if (completion == null) {
			throw new IllegalArgumentException(
				"Wireless completion cannot be null");
		}
		WirelessTransferMetadata metadata = state().wirelessTransfers.get(
			completion.transferId);
		if (metadata == null || !metadata.active) {
			return null;
		}
		WirelessTransferMetadata next = peekQueuedWirelessTransfer(
			metadata.mobileDirectionKey);
		AccessPointTransferScheduler.Completion schedulerCompletion;
		if (next == null) {
			schedulerCompletion = state().wirelessScheduler.complete(
				completion.transferId, completion.generation, CloudSim.clock());
		}
		else {
			double accessPointBandwidth = next.direction == WirelessDirection.UPLINK
				? next.accessPoint.getUplinkBandwidth()
				: next.accessPoint.getDownlinkBandwidth();
			double mobileBandwidth = next.direction == WirelessDirection.UPLINK
				? next.mobileDevice.getUplinkBandwidth()
				: next.mobileDevice.getDownlinkBandwidth();
			schedulerCompletion = state().wirelessScheduler.completeAndStart(
				completion.transferId, completion.generation, next.transferId,
				next.mobileDevice.getNetworkSliceId(),
				next.tuple.getCloudletFileSize(), accessPointBandwidth,
				mobileBandwidth, CloudSim.clock());
		}
		if (!schedulerCompletion.isAccepted()) {
			return null;
		}

		metadata = state().wirelessTransfers.remove(
			schedulerCompletion.getTransferId());
		if (metadata == null || !metadata.active) {
			return null;
		}
		state().activeWirelessTransfers.remove(metadata.mobileDirectionKey);
		NetworkUsageMonitor.recordCompletedTuple(new NetworkTransferUsage(
			DataSize.ofBytes(metadata.tuple.getCloudletFileSize()),
			SimulationDuration.ofMilliseconds(
				metadata.getQueueDurationMillis()),
			SimulationDuration.ofMilliseconds(
				schedulerCompletion.getDurationMillis()),
			SimulationDuration.ofMilliseconds(
				metadata.propagationDelay.toMilliseconds())));
		WirelessTransferResult result = new WirelessTransferResult(metadata,
			schedulerCompletion);

		if (next != null) {
			removeQueuedWirelessTransfer(metadata.mobileDirectionKey,
				next.transferId);
			next.active = true;
			next.activatedAtMillis = CloudSim.clock();
			state().activeWirelessTransfers.put(next.mobileDirectionKey, next.transferId);
		}
		applyWirelessSchedules(schedulerCompletion.getSchedules());
		return result;
	}

	/** Drops every active and queued wireless tuple for a disconnected user. */
	public static synchronized void cancelWirelessTransfers(
		MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		for (WirelessDirection direction : WirelessDirection.values()) {
			String directionKey = mobileDirectionKey(mobileDevice, direction);
			ArrayDeque<Long> queue =
				state().queuedWirelessTransfers.remove(directionKey);
			if (queue != null) {
				state().queuedWirelessTransferCount -= queue.size();
				for (Long queuedTransferId : queue) {
					state().wirelessTransfers.remove(queuedTransferId);
				}
			}

			Long activeTransferId =
				state().activeWirelessTransfers.remove(directionKey);
			if (activeTransferId == null) {
				continue;
			}
			WirelessTransferMetadata metadata =
				state().wirelessTransfers.remove(activeTransferId);
			if (metadata == null) {
				continue;
			}
			cancelWirelessCompletionEvent(metadata);
			applyWirelessSchedules(state().wirelessScheduler.cancel(activeTransferId,
				CloudSim.clock()));
		}
	}

	/** Returns the number of active flows on one AP direction. */
	public static synchronized int getActiveWirelessTransferCount(
		ApDevice accessPoint, WirelessDirection direction) {
		if (accessPoint == null || direction == null) {
			return 0;
		}
		return state().wirelessScheduler.activeCount(
			wirelessChannelKey(accessPoint, direction));
	}

	/** Returns whether this user owns an active flow in the supplied direction. */
	public static synchronized boolean hasActiveWirelessTransfer(
		MobileDevice mobileDevice, WirelessDirection direction) {
		return mobileDevice != null && direction != null
			&& state().activeWirelessTransfers.containsKey(
				mobileDirectionKey(mobileDevice, direction));
	}

	/** Cancels an active migration transfer when it is aborted. */
	public static synchronized void releaseBandwidth(MobileDevice mobileDevice) {
		cancelMigrationTransfer(mobileDevice);
	}

	/**
	 * Starts a time-aware transfer and schedules its completion. Existing
	 * transfers on the same directed link are progressed at their previous
	 * rates and receive replacement completion events at their new rates.
	 */
	public static synchronized void startMigrationTransfer(
		MigrationTransferSpec spec) {
		validateTransferSpec(spec);

		int transferId = spec.getMobileDevice().getId();
		MigrationTransferMetadata previous =
			state().migrationTransfers.remove(transferId);
		cancelCompletionEvent(transferId, previous);

		double physicalBandwidth = getPhysicalBandwidth(spec.getSource(),
			spec.getDestination());

		List<MigrationTransferScheduler.Schedule> schedules =
			state().migrationScheduler.start(
			transferId, linkKey(spec.getSource(), spec.getDestination()),
			spec.getNetworkSlice().intValue(), spec.getTransferSize().toBytes(),
			physicalBandwidth, CloudSim.clock());
		state().migrationTransfers.put(transferId,
			new MigrationTransferMetadata(spec));
		applySchedules(schedules);
	}

	/**
	 * Completes the byte transfer represented by an event payload. Returns its
	 * measured result for a current event, or {@code null} for a stale
	 * rescheduled event. The fixed delay in the result must be applied once after
	 * this method returns.
	 */
	public static synchronized MigrationTransferResult completeMigrationTransfer(
		MigrationTransferCompletion completion) {
		if (completion == null) {
			throw new IllegalArgumentException("Migration completion cannot be null");
		}

		MigrationTransferScheduler.Completion result =
			state().migrationScheduler.complete(
			completion.transferId, completion.generation, CloudSim.clock());
		if (!result.isAccepted()) {
			return null;
		}

		MigrationTransferMetadata metadata = state().migrationTransfers.remove(
			result.getTransferId());
		if (metadata == null) {
			return null;
		}
		MigrationTransferResult transferResult =
			new MigrationTransferResult(metadata, result);
		metadata.spec.getMobileDevice().setMigTime(
			transferResult.getTotalDurationMillis());
		NetworkUsageMonitor.recordCompletedMigration(
			transferResult.getTransferredData(),
			transferResult.getDataTransferDuration());
		applySchedules(result.getSchedules());
		return transferResult;
	}

	/** Returns whether a mobile device currently owns a timed transfer. */
	public static synchronized boolean hasActiveMigrationTransfer(
		MobileDevice mobileDevice) {
		return mobileDevice != null
			&& state().migrationScheduler.contains(mobileDevice.getId());
	}

	private static double getAccessPointBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice, double accessPointBandwidth,
		double mobileDeviceBandwidth) {
		if (accessPoint == null || mobileDevice == null) {
			throw new IllegalArgumentException(
				"Wireless network slicing requires an access point and mobile device");
		}
		validateNonNegativeFinite(accessPointBandwidth,
			"Access point bandwidth");
		validateNonNegativeFinite(mobileDeviceBandwidth,
			"Mobile-device bandwidth");

		int requestedSlice = validateSliceId(mobileDevice.getNetworkSliceId());
		double maximumAccessPointRate = accessPointBandwidth;
		if (coversWirelessNetwork() && !state().mode.allowsDynamicBorrowing()) {
			maximumAccessPointRate *= state().percentages[requestedSlice] / 100.0;
		}
		return Math.min(mobileDeviceBandwidth, maximumAccessPointRate);
	}

	private static double getPhysicalBandwidth(FogDevice source,
		FogDevice destination) {
		if (source == null) {
			throw new IllegalArgumentException(
				"Physical bandwidth requires a source cloudlet");
		}
		double physicalBandwidth = source.getUplinkBandwidth();
		validateNonNegativeFinite(physicalBandwidth, "Source uplink bandwidth");
		if (destination != null) {
			validateNonNegativeFinite(destination.getDownlinkBandwidth(),
				"Destination downlink bandwidth");
			physicalBandwidth = Math.min(physicalBandwidth,
				destination.getDownlinkBandwidth());
		}
		return physicalBandwidth;
	}

	private static double[] parsePercentages(String percentageList,
		String description) {
		String[] values = percentageList.split(",", -1);
		double[] parsed = new double[values.length];
		double total = 0.0;
		for (int index = 0; index < values.length; index++) {
			parsed[index] = Double.parseDouble(values[index].trim());
			if (!Double.isFinite(parsed[index])
				|| parsed[index] <= 0.0 || parsed[index] > 100.0) {
				throw new IllegalArgumentException("Each " + description
					+ " percentage must be finite, greater than 0, and at most 100");
			}
			total += parsed[index];
		}
		if (!Double.isFinite(total) || Math.abs(total - 100.0) > 0.000001) {
			throw new IllegalArgumentException(description
				+ " percentages must sum to 100");
		}
		return parsed;
	}

	private static double[] parsePercentagesOrDefault(String percentageList,
		String description) {
		if (percentageList == null || percentageList.trim().isEmpty()) {
			return new double[] { 100.0 };
		}
		return parsePercentages(percentageList, description);
	}

	private static double[] equalPercentages(int sliceCount) {
		double[] equal = new double[sliceCount];
		for (int sliceId = 0; sliceId < sliceCount; sliceId++) {
			equal[sliceId] = 100.0 / sliceCount;
		}
		return equal;
	}

	private static TransportLinkId linkKey(FogDevice source, FogDevice destination) {
		if (source == null || destination == null) {
			throw new IllegalArgumentException("Network-slice reservations require source and destination cloudlets");
		}
		return TransportLinkId.directed(source.getId(), destination.getId());
	}

	private static void validateWirelessTransfer(ApDevice accessPoint,
		MobileDevice mobileDevice, WirelessDirection direction, Tuple tuple,
		EntityId eventSourceId, EntityId destinationEntityId,
		PropagationDelay propagationDelay) {
		if (accessPoint == null || mobileDevice == null || direction == null
			|| tuple == null || eventSourceId == null || destinationEntityId == null
			|| propagationDelay == null) {
			throw new IllegalArgumentException(
				"A wireless transfer requires an AP, mobile device, direction, and tuple");
		}
		if (mobileDevice.getSourceAp() != accessPoint
			|| !accessPoint.getSmartThings().contains(mobileDevice)) {
			throw new IllegalStateException(
				"A wireless transfer requires an active AP association");
		}
		if (direction == WirelessDirection.UPLINK
			&& eventSourceId.intValue() != mobileDevice.getId()) {
			throw new IllegalArgumentException(
				"A wireless uplink must originate at its mobile device");
		}
		if (direction == WirelessDirection.DOWNLINK
			&& destinationEntityId.intValue() != mobileDevice.getId()) {
			throw new IllegalArgumentException(
				"A wireless downlink must terminate at its mobile device");
		}
		validateSliceId(mobileDevice.getNetworkSliceId());
		if (tuple.getCloudletFileSize() < 0L) {
			throw new IllegalArgumentException(
				"Wireless tuple size cannot be negative");
		}
		double accessPointBandwidth = direction == WirelessDirection.UPLINK
			? accessPoint.getUplinkBandwidth() : accessPoint.getDownlinkBandwidth();
		double mobileBandwidth = direction == WirelessDirection.UPLINK
			? mobileDevice.getUplinkBandwidth() : mobileDevice.getDownlinkBandwidth();
		if (!Double.isFinite(accessPointBandwidth) || accessPointBandwidth <= 0.0
			|| !Double.isFinite(mobileBandwidth) || mobileBandwidth <= 0.0) {
			throw new IllegalArgumentException(
				"Wireless endpoint bandwidth must be finite and positive");
		}
	}

	private static void startWirelessTransfer(
		WirelessTransferMetadata metadata) {
		metadata.active = true;
		metadata.activatedAtMillis = CloudSim.clock();
		double accessPointBandwidth = metadata.direction == WirelessDirection.UPLINK
			? metadata.accessPoint.getUplinkBandwidth()
			: metadata.accessPoint.getDownlinkBandwidth();
		double mobileBandwidth = metadata.direction == WirelessDirection.UPLINK
			? metadata.mobileDevice.getUplinkBandwidth()
			: metadata.mobileDevice.getDownlinkBandwidth();
		applyWirelessSchedules(state().wirelessScheduler.start(metadata.transferId,
			wirelessChannelKey(metadata.accessPoint, metadata.direction),
			metadata.mobileDevice.getNetworkSliceId(),
			metadata.tuple.getCloudletFileSize(), accessPointBandwidth,
			mobileBandwidth, CloudSim.clock()));
	}

	private static void enqueueWirelessTransfer(ArrayDeque<Long> queue,
		long transferId) {
		if (queue.size() >= DEFAULT_MAXIMUM_WIRELESS_QUEUE_SIZE) {
			Long droppedTransferId = queue.removeFirst();
			state().wirelessTransfers.remove(droppedTransferId);
			state().queuedWirelessTransferCount--;
			state().droppedWirelessTupleCount++;
		}
		queue.addLast(transferId);
		state().queuedWirelessTransferCount++;
		state().maximumQueuedWirelessTransferCount = Math.max(
			state().maximumQueuedWirelessTransferCount,
			state().queuedWirelessTransferCount);
		state().maximumWirelessQueueDepth = Math.max(
			state().maximumWirelessQueueDepth, queue.size());
	}

	private static WirelessTransferMetadata peekQueuedWirelessTransfer(
		String mobileDirection) {
		ArrayDeque<Long> queue =
			state().queuedWirelessTransfers.get(mobileDirection);
		return queue == null || queue.isEmpty() ? null
			: state().wirelessTransfers.get(queue.peekFirst());
	}

	private static void removeQueuedWirelessTransfer(String mobileDirection,
		long expectedTransferId) {
		ArrayDeque<Long> queue =
			state().queuedWirelessTransfers.get(mobileDirection);
		if (queue == null || queue.isEmpty()) {
			throw new IllegalStateException(
				"Atomic wireless successor disappeared from its FIFO");
		}
		Long transferId = queue.removeFirst();
		state().queuedWirelessTransferCount--;
		if (transferId.longValue() != expectedTransferId) {
			throw new IllegalStateException(
				"Wireless FIFO order changed during atomic completion");
		}
		if (queue.isEmpty()) {
			state().queuedWirelessTransfers.remove(mobileDirection);
		}
	}

	private static void applyWirelessSchedules(
		List<AccessPointTransferScheduler.Schedule> schedules) {
		for (AccessPointTransferScheduler.Schedule schedule : schedules) {
			WirelessTransferMetadata metadata = state().wirelessTransfers.get(
				schedule.getTransferId());
			if (metadata == null || !metadata.active) {
				continue;
			}
			cancelWirelessCompletionEvent(metadata);
			if (CloudSim.running()) {
				CloudSim.send(metadata.eventSourceId.intValue(),
					metadata.eventSourceId.intValue(),
					schedule.getDelayMillis(), FogEvents.WIRELESS_TRANSFER_COMPLETE,
					new WirelessTransferCompletion(schedule.getTransferId(),
						schedule.getGeneration()));
			}
		}
	}

	private static void cancelWirelessCompletionEvent(
		WirelessTransferMetadata metadata) {
		if (metadata != null && metadata.active && CloudSim.running()) {
			CloudSim.cancelAll(metadata.eventSourceId.intValue(),
				new WirelessCompletionPredicate(metadata.transferId));
		}
	}

	private static String wirelessChannelKey(ApDevice accessPoint,
		WirelessDirection direction) {
		return accessPoint.getId() + ":" + direction.name();
	}

	private static String mobileDirectionKey(MobileDevice mobileDevice,
		WirelessDirection direction) {
		return mobileDevice.getId() + ":" + direction.name();
	}

	private static double validateNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
		return value;
	}

	private static void resetUsage() {
		if (CloudSim.running()) {
			for (Map.Entry<Integer, MigrationTransferMetadata> entry
				: state().migrationTransfers.entrySet()) {
				cancelCompletionEvent(entry.getKey(), entry.getValue());
			}
			for (WirelessTransferMetadata metadata
				: state().wirelessTransfers.values()) {
				cancelWirelessCompletionEvent(metadata);
			}
		}
		state().migrationTransfers.clear();
		state().reconfigurationMetrics.reset(state().percentages.length);
		state().migrationScheduler = new MigrationTransferScheduler(
			state().percentages, coversTransportNetwork(),
			state().mode.allowsDynamicBorrowing(),
			state().reallocationDelayMillis, state().reconfigurationMetrics);
		state().wirelessTransfers.clear();
		state().activeWirelessTransfers.clear();
		state().queuedWirelessTransfers.clear();
		state().wirelessScheduler = new AccessPointTransferScheduler(
			state().percentages, coversWirelessNetwork(),
			state().mode.allowsDynamicBorrowing(),
			state().reallocationDelayMillis, state().reconfigurationMetrics);
		state().nextWirelessTransferId = 1L;
		state().queuedWirelessTransferCount = 0L;
		state().maximumQueuedWirelessTransferCount = 0L;
		state().maximumWirelessQueueDepth = 0;
		state().droppedWirelessTupleCount = 0L;
	}

	private static void cancelMigrationTransfer(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		int transferId = mobileDevice.getId();
		MigrationTransferMetadata metadata =
			state().migrationTransfers.remove(transferId);
		if (metadata == null
			&& !state().migrationScheduler.contains(transferId)) {
			return;
		}
		cancelCompletionEvent(transferId, metadata);
		List<MigrationTransferScheduler.Schedule> schedules =
			state().migrationScheduler.cancel(transferId, CloudSim.clock());
		applySchedules(schedules);
	}

	private static void applySchedules(
		List<MigrationTransferScheduler.Schedule> schedules) {
		for (MigrationTransferScheduler.Schedule schedule : schedules) {
			MigrationTransferMetadata metadata = state().migrationTransfers.get(
				schedule.getTransferId());
			if (metadata == null) {
				continue;
			}
			metadata.spec.getMobileDevice().setMigTime(schedule.getTotalDuration()
				+ metadata.spec.getFixedDelayMillis());
			if (!CloudSim.running()) {
				continue;
			}
			cancelCompletionEvent(schedule.getTransferId(), metadata);
			CloudSim.send(metadata.eventSourceId.intValue(),
				metadata.completionDestinationId.intValue(),
				schedule.getDelay(), metadata.completionEventTag,
				new MigrationTransferCompletion(schedule.getTransferId(),
					schedule.getGeneration()));
		}
	}

	private static void cancelCompletionEvent(int transferId,
		MigrationTransferMetadata metadata) {
		if (metadata != null && CloudSim.running()) {
			CloudSim.cancelAll(metadata.eventSourceId.intValue(),
				new MigrationCompletionPredicate(transferId));
		}
	}

	private static void validateTransferSpec(MigrationTransferSpec spec) {
		if (spec == null) {
			throw new IllegalArgumentException(
				"Migration transfer specification cannot be null");
		}
		linkKey(spec.getSource(), spec.getDestination());
		validateSliceId(spec.getNetworkSlice().intValue());
		if (spec.getMobileDevice().getNetworkSliceId()
			!= spec.getNetworkSlice().intValue()) {
			throw new IllegalArgumentException(
				"A migration cannot change slices after its transfer is prepared");
		}
	}

	private static int validateSliceId(int sliceId) {
		if (sliceId < 0 || sliceId >= state().percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		return sliceId;
	}
}
