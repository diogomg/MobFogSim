package org.fog.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.Predicate;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

/**
 * Bandwidth slices for transport links and wireless access points.
 *
 * A slice reserves its configured percentage of every cloudlet-to-cloudlet
 * link and each access point's uplink and downlink capacity. While a slice has
 * no active migration on a transport link, its reservation can be borrowed by
 * migrations in the active slices on that link. On an access point, the users
 * associated with one slice share its capacity and, in dynamic mode, slices
 * with associated users share the capacity of slices that have no users on
 * that access point.
 */
public final class NetworkSlicing {

	public static final int TRANSPORT_NETWORK = 0;
	public static final int WIRELESS_NETWORK = 1;
	public static final int END_TO_END_NETWORK = 2;

	private static double[] percentages = new double[] { 100.0 };
	private static double[] userAllocationPercentages = new double[] { 100.0 };
	private static boolean dynamicBorrowing = true;
	private static int scope = END_TO_END_NETWORK;
	private static final Map<Integer, MigrationTransferMetadata> migrationTransfers =
		new HashMap<Integer, MigrationTransferMetadata>();
	private static MigrationTransferScheduler migrationScheduler =
		new MigrationTransferScheduler(percentages, true, dynamicBorrowing);

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

	/** Versioned payload used to ignore an obsolete completion event. */
	public static final class MigrationTransferCompletion {
		private final int transferId;
		private final long generation;

		private MigrationTransferCompletion(int transferId, long generation) {
			this.transferId = transferId;
			this.generation = generation;
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

	private NetworkSlicing() {
	}

	/**
	 * Configures slices from a comma-separated percentage list, for example
	 * {@code "50,50"} or {@code "40,30,20,10"}.
	 */
	public static void configure(String percentageList) {
		applyConfiguration(parseConfiguration(percentageList, null, scope,
			dynamicBorrowing));
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
		percentages = configuration.bandwidthPercentages.clone();
		userAllocationPercentages = configuration.userPercentages.clone();
		dynamicBorrowing = configuration.dynamicBorrowing;
		scope = configuration.scope;
		resetUsage();
	}

	/**
	 * Configures the percentage of users assigned to each slice. The number of
	 * values must match the number of configured bandwidth slices.
	 */
	public static void configureUserAllocation(String percentageList) {
		if (percentageList == null || percentageList.trim().isEmpty()) {
			userAllocationPercentages = equalPercentages(percentages.length);
			return;
		}

		double[] parsed = parsePercentages(percentageList, "User allocation");
		if (parsed.length != percentages.length) {
			throw new IllegalArgumentException(
				"User allocation must contain one percentage for each network slice");
		}
		userAllocationPercentages = parsed;
	}

	public static int getSliceCount() {
		return percentages.length;
	}

	/** Enables (true) or disables (false) borrowing of idle slice capacity. */
	public static void setDynamicBorrowing(boolean enabled) {
		dynamicBorrowing = enabled;
		resetUsage();
	}

	public static boolean isDynamicBorrowing() {
		return dynamicBorrowing;
	}

	/**
	 * Selects where slicing is applied: {@link #TRANSPORT_NETWORK},
	 * {@link #WIRELESS_NETWORK}, or {@link #END_TO_END_NETWORK}.
	 */
	public static void setScope(int selectedScope) {
		scope = validateScope(selectedScope);
		resetUsage();
	}

	public static int getScope() {
		return scope;
	}

	public static boolean coversTransportNetwork() {
		return scope == TRANSPORT_NETWORK || scope == END_TO_END_NETWORK;
	}

	public static boolean coversWirelessNetwork() {
		return scope == WIRELESS_NETWORK || scope == END_TO_END_NETWORK;
	}

	public static double getPercentage(int sliceId) {
		return percentages[validateSliceId(sliceId)];
	}

	public static double getUserAllocationPercentage(int sliceId) {
		return userAllocationPercentages[validateSliceId(sliceId)];
	}

	/**
	 * Returns integer user quotas using the largest-remainder method, so all
	 * users are assigned even when percentages produce fractional counts.
	 */
	public static int[] getUserAllocations(int totalUsers) {
		if (totalUsers < 0) {
			throw new IllegalArgumentException("Total users cannot be negative");
		}

		int[] allocations = new int[userAllocationPercentages.length];
		double[] remainders = new double[userAllocationPercentages.length];
		double configuredTotal = 0.0;
		for (double percentage : userAllocationPercentages) {
			configuredTotal += percentage;
		}

		int assignedUsers = 0;
		for (int sliceId = 0; sliceId < allocations.length; sliceId++) {
			double exactAllocation = totalUsers
				* userAllocationPercentages[sliceId] / configuredTotal;
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
	 * Returns the wireless uplink bandwidth available to a mobile device. The
	 * access point's slice share is divided equally among the connected users
	 * in that slice, then capped by the mobile device's own uplink capability.
	 */
	public static double getAccessPointUplinkBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice) {
		return getAccessPointBandwidth(accessPoint, mobileDevice,
			accessPoint == null ? 0.0 : accessPoint.getUplinkBandwidth(),
			mobileDevice == null ? 0.0 : mobileDevice.getUplinkBandwidth());
	}

	/**
	 * Returns the wireless downlink bandwidth available to a mobile device. The
	 * access point's slice share is divided equally among the connected users
	 * in that slice, then capped by the mobile device's own downlink capability.
	 */
	public static double getAccessPointDownlinkBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice) {
		return getAccessPointBandwidth(accessPoint, mobileDevice,
			accessPoint == null ? 0.0 : accessPoint.getDownlinkBandwidth(),
			mobileDevice == null ? 0.0 : mobileDevice.getDownlinkBandwidth());
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
		MigrationTransferMetadata previous = migrationTransfers.remove(transferId);
		cancelCompletionEvent(transferId, previous);

		double physicalBandwidth = getPhysicalBandwidth(spec.getSource(),
			spec.getDestination());

		List<MigrationTransferScheduler.Schedule> schedules = migrationScheduler.start(
			transferId, linkKey(spec.getSource(), spec.getDestination()),
			spec.getNetworkSliceId(), spec.getTransferBytes(), physicalBandwidth,
			CloudSim.clock());
		migrationTransfers.put(transferId, new MigrationTransferMetadata(spec));
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

		MigrationTransferScheduler.Completion result = migrationScheduler.complete(
			completion.transferId, completion.generation, CloudSim.clock());
		if (!result.isAccepted()) {
			return null;
		}

		MigrationTransferMetadata metadata = migrationTransfers.remove(
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
		return mobileDevice != null && migrationScheduler.contains(mobileDevice.getId());
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
		if (!coversWirelessNetwork()) {
			int connectedUsers = accessPoint.getSmartThings().size();
			if (!accessPoint.getSmartThings().contains(mobileDevice)) {
				connectedUsers++;
			}
			return Math.min(mobileDeviceBandwidth,
				accessPointBandwidth / connectedUsers);
		}

		int[] usersBySlice = new int[percentages.length];
		for (MobileDevice connectedDevice : accessPoint.getSmartThings()) {
			usersBySlice[validateSliceId(connectedDevice.getNetworkSliceId())]++;
		}

		// Also support calculating a prospective association before it is added.
		if (!accessPoint.getSmartThings().contains(mobileDevice)) {
			usersBySlice[requestedSlice]++;
		}

		double slicePercentage = percentages[requestedSlice];
		if (dynamicBorrowing) {
			int activeSlices = 0;
			double idlePercentage = 0.0;
			for (int sliceId = 0; sliceId < usersBySlice.length; sliceId++) {
				if (usersBySlice[sliceId] == 0) {
					idlePercentage += percentages[sliceId];
				}
				else {
					activeSlices++;
				}
			}
			slicePercentage += idlePercentage / activeSlices;
		}

		double sharedAccessPointBandwidth = accessPointBandwidth
			* slicePercentage / 100.0 / usersBySlice[requestedSlice];
		return Math.min(mobileDeviceBandwidth, sharedAccessPointBandwidth);
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
				: migrationTransfers.entrySet()) {
				cancelCompletionEvent(entry.getKey(), entry.getValue());
			}
		}
		migrationTransfers.clear();
		migrationScheduler = new MigrationTransferScheduler(percentages,
			coversTransportNetwork(), dynamicBorrowing);
	}

	private static void cancelMigrationTransfer(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		int transferId = mobileDevice.getId();
		MigrationTransferMetadata metadata = migrationTransfers.remove(transferId);
		if (metadata == null && !migrationScheduler.contains(transferId)) {
			return;
		}
		cancelCompletionEvent(transferId, metadata);
		List<MigrationTransferScheduler.Schedule> schedules =
			migrationScheduler.cancel(transferId, CloudSim.clock());
		applySchedules(schedules);
	}

	private static void applySchedules(
		List<MigrationTransferScheduler.Schedule> schedules) {
		for (MigrationTransferScheduler.Schedule schedule : schedules) {
			MigrationTransferMetadata metadata = migrationTransfers.get(
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
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		return sliceId;
	}
}
