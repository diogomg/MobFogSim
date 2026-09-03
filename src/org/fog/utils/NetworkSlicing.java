package org.fog.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 */
public final class NetworkSlicing {

	public static final int TRANSPORT_NETWORK = 0;
	public static final int WIRELESS_NETWORK = 1;
	public static final int END_TO_END_NETWORK = 2;

	private static RuntimeState activeState = RuntimeState.defaults();

	/** AP channel direction. Uplink and downlink capacity are independent. */
	public enum WirelessDirection {
		UPLINK,
		DOWNLINK
	}

	/** Immutable, fully validated slicing settings for one simulation run. */
	public static final class Configuration {
		private final double[] bandwidthPercentages;
		private final double[] userPercentages;
		private final boolean dynamicBorrowing;
		private final int scope;

		private Configuration(double[] bandwidthPercentages,
			double[] userPercentages, boolean dynamicBorrowing, int scope) {
			this.bandwidthPercentages = bandwidthPercentages.clone();
			this.userPercentages = userPercentages.clone();
			this.dynamicBorrowing = dynamicBorrowing;
			this.scope = scope;
		}
	}

	/** Mutable schedulers and transfer registries owned by one simulation run. */
	public static final class RuntimeState {
		private double[] percentages;
		private double[] userAllocationPercentages;
		private boolean dynamicBorrowing;
		private int scope;
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

		private RuntimeState(Configuration configuration) {
			percentages = configuration.bandwidthPercentages.clone();
			userAllocationPercentages = configuration.userPercentages.clone();
			dynamicBorrowing = configuration.dynamicBorrowing;
			scope = configuration.scope;
			migrationScheduler = new MigrationTransferScheduler(percentages,
				coversTransportNetwork(scope), dynamicBorrowing);
			wirelessScheduler = new AccessPointTransferScheduler(percentages,
				coversWirelessNetwork(scope), dynamicBorrowing);
		}

		private static RuntimeState defaults() {
			return new RuntimeState(new Configuration(new double[] { 100.0 },
				new double[] { 100.0 }, true, END_TO_END_NETWORK));
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
		private final int destinationEntityId;
		private final double propagationDelayMillis;
		private final double transferDurationMillis;

		private WirelessTransferResult(WirelessTransferMetadata metadata,
			AccessPointTransferScheduler.Completion completion) {
			this.tuple = metadata.tuple;
			this.destinationEntityId = metadata.destinationEntityId;
			this.propagationDelayMillis = metadata.propagationDelayMillis;
			this.transferDurationMillis = completion.getDurationMillis();
		}

		public Tuple getTuple() {
			return tuple;
		}

		public int getDestinationEntityId() {
			return destinationEntityId;
		}

		public double getPropagationDelayMillis() {
			return propagationDelayMillis;
		}

		public double getTransferDurationMillis() {
			return transferDurationMillis;
		}
	}

	/**
	 * Accepted completion of the byte-transfer phase. Fixed delay remains to be
	 * applied by the receiving simulation entity without holding link capacity.
	 */
	public static final class MigrationTransferResult {
		private final MobileDevice mobileDevice;
		private final double transferredBytes;
		private final double transferDurationMillis;
		private final double fixedDelayMillis;

		private MigrationTransferResult(MigrationTransferMetadata metadata,
			MigrationTransferScheduler.Completion completion) {
			this.mobileDevice = metadata.spec.getMobileDevice();
			this.transferredBytes = completion.getTransferredBytes();
			this.transferDurationMillis = completion.getDuration();
			this.fixedDelayMillis = metadata.spec.getFixedDelayMillis();
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
		}

		public double getTransferredBytes() {
			return transferredBytes;
		}

		public double getTransferDurationMillis() {
			return transferDurationMillis;
		}

		public double getFixedDelayMillis() {
			return fixedDelayMillis;
		}

		public double getTotalDurationMillis() {
			return transferDurationMillis + fixedDelayMillis;
		}
	}

	private static final class MigrationTransferMetadata {
		private final MigrationTransferSpec spec;
		private final int eventSourceId;
		private final int completionDestinationId;
		private final int completionEventTag;

		private MigrationTransferMetadata(MigrationTransferSpec spec) {
			this.spec = spec;
			this.eventSourceId = spec.getCompletionDestinationId();
			this.completionDestinationId = spec.getCompletionDestinationId();
			this.completionEventTag = spec.getCompletionEventTag();
		}
	}

	private static final class WirelessTransferMetadata {
		private final long transferId;
		private final ApDevice accessPoint;
		private final MobileDevice mobileDevice;
		private final WirelessDirection direction;
		private final Tuple tuple;
		private final int eventSourceId;
		private final int destinationEntityId;
		private final double propagationDelayMillis;
		private final String mobileDirectionKey;
		private boolean active;

		private WirelessTransferMetadata(long transferId, ApDevice accessPoint,
			MobileDevice mobileDevice, WirelessDirection direction, Tuple tuple,
			int eventSourceId, int destinationEntityId,
			double propagationDelayMillis) {
			this.transferId = transferId;
			this.accessPoint = accessPoint;
			this.mobileDevice = mobileDevice;
			this.direction = direction;
			this.tuple = tuple;
			this.eventSourceId = eventSourceId;
			this.destinationEntityId = destinationEntityId;
			this.propagationDelayMillis = propagationDelayMillis;
			this.mobileDirectionKey = mobileDirectionKey(mobileDevice, direction);
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
			state().dynamicBorrowing));
	}

	/**
	 * Parses every slicing option without changing global slicing state.
	 */
	public static Configuration parseConfiguration(String bandwidthPercentageList,
		String userPercentageList, int selectedScope, boolean useDynamicBorrowing) {
		validateScope(selectedScope);
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
			useDynamicBorrowing, selectedScope);
	}

	/** Applies one already validated slicing configuration in a single update. */
	public static synchronized void applyConfiguration(Configuration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException("Network slicing configuration cannot be null");
		}
		state().percentages = configuration.bandwidthPercentages.clone();
		state().userAllocationPercentages = configuration.userPercentages.clone();
		state().dynamicBorrowing = configuration.dynamicBorrowing;
		state().scope = configuration.scope;
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
		state().dynamicBorrowing = enabled;
		resetUsage();
	}

	public static boolean isDynamicBorrowing() {
		return state().dynamicBorrowing;
	}

	/**
	 * Selects where slicing is applied: {@link #TRANSPORT_NETWORK},
	 * {@link #WIRELESS_NETWORK}, or {@link #END_TO_END_NETWORK}.
	 */
	public static void setScope(int selectedScope) {
		state().scope = validateScope(selectedScope);
		resetUsage();
	}

	public static int getScope() {
		return state().scope;
	}

	public static boolean coversTransportNetwork() {
		return coversTransportNetwork(state().scope);
	}

	public static boolean coversWirelessNetwork() {
		return coversWirelessNetwork(state().scope);
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
		validateWirelessTransfer(accessPoint, mobileDevice, direction, tuple,
			eventSourceId, destinationEntityId, propagationDelayMillis);
		long transferId = state().nextWirelessTransferId++;
		WirelessTransferMetadata metadata = new WirelessTransferMetadata(
			transferId, accessPoint, mobileDevice, direction, tuple, eventSourceId,
			destinationEntityId, propagationDelayMillis);
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
			queue.addLast(transferId);
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
		AccessPointTransferScheduler.Completion schedulerCompletion =
			state().wirelessScheduler.complete(completion.transferId,
				completion.generation, CloudSim.clock());
		if (!schedulerCompletion.isAccepted()) {
			return null;
		}

		WirelessTransferMetadata metadata = state().wirelessTransfers.remove(
			schedulerCompletion.getTransferId());
		if (metadata == null || !metadata.active) {
			return null;
		}
		state().activeWirelessTransfers.remove(metadata.mobileDirectionKey);
		NetworkUsageMonitor.sendingTuple(metadata.propagationDelayMillis,
			metadata.tuple.getCloudletFileSize());
		WirelessTransferResult result = new WirelessTransferResult(metadata,
			schedulerCompletion);

		WirelessTransferMetadata next = nextQueuedWirelessTransfer(
			metadata.mobileDirectionKey);
		if (next == null) {
			applyWirelessSchedules(schedulerCompletion.getSchedules());
		}
		else {
			state().activeWirelessTransfers.put(next.mobileDirectionKey, next.transferId);
			startWirelessTransfer(next);
		}
		return result;
	}

	/** Drops every active and queued wireless tuple for a disconnected user. */
	public static synchronized void cancelWirelessTransfers(
		MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		List<Long> matchingTransfers = new ArrayList<Long>();
		for (Map.Entry<Long, WirelessTransferMetadata> entry
			: state().wirelessTransfers.entrySet()) {
			if (entry.getValue().mobileDevice == mobileDevice) {
				matchingTransfers.add(entry.getKey());
			}
		}

		for (Long transferId : matchingTransfers) {
			WirelessTransferMetadata metadata =
				state().wirelessTransfers.remove(transferId);
			if (metadata == null) {
				continue;
			}
			ArrayDeque<Long> queue = state().queuedWirelessTransfers.get(
				metadata.mobileDirectionKey);
			if (queue != null) {
				queue.remove(transferId);
				if (queue.isEmpty()) {
					state().queuedWirelessTransfers.remove(metadata.mobileDirectionKey);
				}
			}
			if (!metadata.active) {
				continue;
			}
			state().activeWirelessTransfers.remove(metadata.mobileDirectionKey);
			cancelWirelessCompletionEvent(metadata);
			applyWirelessSchedules(state().wirelessScheduler.cancel(transferId,
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
			spec.getNetworkSliceId(), spec.getTransferBytes(), physicalBandwidth,
			CloudSim.clock());
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
			transferResult.getTransferredBytes(),
			transferResult.getTransferDurationMillis());
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
		if (coversWirelessNetwork() && !state().dynamicBorrowing) {
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

	private static String linkKey(FogDevice source, FogDevice destination) {
		if (source == null || destination == null) {
			throw new IllegalArgumentException("Network-slice reservations require source and destination cloudlets");
		}
		return source.getId() + "->" + destination.getId();
	}

	private static int validateScope(int selectedScope) {
		if (selectedScope < TRANSPORT_NETWORK || selectedScope > END_TO_END_NETWORK) {
			throw new IllegalArgumentException(
				"Network slice scope must be 0 (transport), 1 (wireless), or 2 (end-to-end)");
		}
		return selectedScope;
	}

	private static boolean coversTransportNetwork(int selectedScope) {
		return selectedScope == TRANSPORT_NETWORK
			|| selectedScope == END_TO_END_NETWORK;
	}

	private static boolean coversWirelessNetwork(int selectedScope) {
		return selectedScope == WIRELESS_NETWORK
			|| selectedScope == END_TO_END_NETWORK;
	}

	private static void validateWirelessTransfer(ApDevice accessPoint,
		MobileDevice mobileDevice, WirelessDirection direction, Tuple tuple,
		int eventSourceId, int destinationEntityId,
		double propagationDelayMillis) {
		if (accessPoint == null || mobileDevice == null || direction == null
			|| tuple == null) {
			throw new IllegalArgumentException(
				"A wireless transfer requires an AP, mobile device, direction, and tuple");
		}
		if (mobileDevice.getSourceAp() != accessPoint
			|| !accessPoint.getSmartThings().contains(mobileDevice)) {
			throw new IllegalStateException(
				"A wireless transfer requires an active AP association");
		}
		if (eventSourceId < 0 || destinationEntityId < 0) {
			throw new IllegalArgumentException(
				"Wireless transfer endpoints must be valid entity IDs");
		}
		if (direction == WirelessDirection.UPLINK
			&& eventSourceId != mobileDevice.getId()) {
			throw new IllegalArgumentException(
				"A wireless uplink must originate at its mobile device");
		}
		if (direction == WirelessDirection.DOWNLINK
			&& destinationEntityId != mobileDevice.getId()) {
			throw new IllegalArgumentException(
				"A wireless downlink must terminate at its mobile device");
		}
		validateSliceId(mobileDevice.getNetworkSliceId());
		validateNonNegativeFinite(propagationDelayMillis,
			"Wireless propagation delay");
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

	private static WirelessTransferMetadata nextQueuedWirelessTransfer(
		String mobileDirection) {
		ArrayDeque<Long> queue =
			state().queuedWirelessTransfers.get(mobileDirection);
		while (queue != null && !queue.isEmpty()) {
			Long transferId = queue.removeFirst();
			WirelessTransferMetadata metadata =
				state().wirelessTransfers.get(transferId);
			if (metadata != null) {
				if (queue.isEmpty()) {
					state().queuedWirelessTransfers.remove(mobileDirection);
				}
				return metadata;
			}
		}
		state().queuedWirelessTransfers.remove(mobileDirection);
		return null;
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
				CloudSim.send(metadata.eventSourceId, metadata.eventSourceId,
					schedule.getDelayMillis(), FogEvents.WIRELESS_TRANSFER_COMPLETE,
					new WirelessTransferCompletion(schedule.getTransferId(),
						schedule.getGeneration()));
			}
		}
	}

	private static void cancelWirelessCompletionEvent(
		WirelessTransferMetadata metadata) {
		if (metadata != null && metadata.active && CloudSim.running()) {
			CloudSim.cancelAll(metadata.eventSourceId,
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
		state().migrationScheduler = new MigrationTransferScheduler(
			state().percentages, coversTransportNetwork(),
			state().dynamicBorrowing);
		state().wirelessTransfers.clear();
		state().activeWirelessTransfers.clear();
		state().queuedWirelessTransfers.clear();
		state().wirelessScheduler = new AccessPointTransferScheduler(
			state().percentages, coversWirelessNetwork(),
			state().dynamicBorrowing);
		state().nextWirelessTransferId = 1L;
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
			CloudSim.send(metadata.eventSourceId, metadata.completionDestinationId,
				schedule.getDelay(), metadata.completionEventTag,
				new MigrationTransferCompletion(schedule.getTransferId(),
					schedule.getGeneration()));
		}
	}

	private static void cancelCompletionEvent(int transferId,
		MigrationTransferMetadata metadata) {
		if (metadata != null && CloudSim.running()) {
			CloudSim.cancelAll(metadata.eventSourceId,
				new MigrationCompletionPredicate(transferId));
		}
	}

	private static void validateTransferSpec(MigrationTransferSpec spec) {
		if (spec == null) {
			throw new IllegalArgumentException(
				"Migration transfer specification cannot be null");
		}
		linkKey(spec.getSource(), spec.getDestination());
		validateSliceId(spec.getNetworkSliceId());
		if (spec.getMobileDevice().getNetworkSliceId()
			!= spec.getNetworkSliceId()) {
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
