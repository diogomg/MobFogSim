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
	/* Active migration counts, indexed by directed cloudlet link and slice. */
	private static final Map<String, int[]> activeTransfers = new HashMap<String, int[]>();
	private static final Map<Integer, String> transferLinks = new HashMap<Integer, String>();
	private static final Map<Integer, MigrationTransferMetadata> migrationTransfers =
		new HashMap<Integer, MigrationTransferMetadata>();
	private static MigrationTransferScheduler migrationScheduler =
		new MigrationTransferScheduler(percentages, true, dynamicBorrowing);

	/** Description of a migration that is ready to start using a transport link. */
	public static final class MigrationTransferRequest {
		private final FogDevice source;
		private final FogDevice destination;
		private final MobileDevice mobileDevice;
		private final double baselineDuration;
		private final int completionDestinationId;
		private final int completionEventTag;

		public MigrationTransferRequest(FogDevice source, FogDevice destination,
			MobileDevice mobileDevice, double baselineDuration,
			int completionDestinationId, int completionEventTag) {
			this.source = source;
			this.destination = destination;
			this.mobileDevice = mobileDevice;
			this.baselineDuration = baselineDuration;
			this.completionDestinationId = completionDestinationId;
			this.completionEventTag = completionEventTag;
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
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

	private static final class MigrationTransferMetadata {
		private final MobileDevice mobileDevice;
		private final int eventSourceId;
		private final int completionDestinationId;
		private final int completionEventTag;

		private MigrationTransferMetadata(MigrationTransferRequest request) {
			this.mobileDevice = request.mobileDevice;
			this.eventSourceId = request.completionDestinationId;
			this.completionDestinationId = request.completionDestinationId;
			this.completionEventTag = request.completionEventTag;
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
		if (percentageList == null || percentageList.trim().isEmpty()) {
			percentages = new double[] { 100.0 };
			userAllocationPercentages = new double[] { 100.0 };
			resetUsage();
			return;
		}

		percentages = parsePercentages(percentageList, "Network slice");
		userAllocationPercentages = equalPercentages(percentages.length);
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
		if (selectedScope < TRANSPORT_NETWORK || selectedScope > END_TO_END_NETWORK) {
			throw new IllegalArgumentException(
				"Network slice scope must be 0 (transport), 1 (wireless), or 2 (end-to-end)");
		}
		scope = selectedScope;
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
		if (physicalBandwidth <= 0.0) {
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

	/**
	 * Starts snapshot accounting for a migration on a link and returns the
	 * bandwidth currently available to it. In fixed mode, concurrent migrations
	 * in one slice share its reserved capacity. In dynamic mode, active slices
	 * also share the reservations of idle slices. Actual migration execution
	 * uses {@link #startMigrationTransfer(MigrationTransferRequest)} so existing
	 * completion events can be updated when this allocation changes.
	 */
	public static synchronized double reserveBandwidth(FogDevice source,
		FogDevice destination, MobileDevice mobileDevice) {
		int sliceId = validateSliceId(mobileDevice.getNetworkSliceId());
		String link = linkKey(source, destination);
		releaseBandwidth(mobileDevice);

		int[] activeBySlice = activeTransfers.get(link);
		if (activeBySlice == null) {
			activeBySlice = new int[percentages.length];
			activeTransfers.put(link, activeBySlice);
		}
		activeBySlice[sliceId]++;
		transferLinks.put(mobileDevice.getId(), link);

		return availableBandwidth(source, destination, activeBySlice, sliceId);
	}

	/** Releases a migration's reservation when it finishes or is aborted. */
	public static synchronized void releaseBandwidth(MobileDevice mobileDevice) {
		cancelMigrationTransfer(mobileDevice);

		String link = transferLinks.remove(mobileDevice.getId());
		if (link == null) {
			return;
		}
		int[] activeBySlice = activeTransfers.get(link);
		if (activeBySlice == null) {
			return;
		}
		int sliceId = validateSliceId(mobileDevice.getNetworkSliceId());
		if (activeBySlice[sliceId] > 0) {
			activeBySlice[sliceId]--;
		}
		for (int active : activeBySlice) {
			if (active > 0) {
				return;
			}
		}
		activeTransfers.remove(link);
	}

	/**
	 * Starts a time-aware transfer and schedules its completion. Existing
	 * transfers on the same directed link are progressed at their previous
	 * rates and receive replacement completion events at their new rates.
	 */
	public static synchronized void startMigrationTransfer(
		MigrationTransferRequest request) {
		validateTransferRequest(request);

		int transferId = request.mobileDevice.getId();
		MigrationTransferMetadata previous = migrationTransfers.remove(transferId);
		cancelCompletionEvent(transferId, previous);

		double physicalBandwidth = getPhysicalBandwidth(request.source,
			request.destination);
		double baselineBandwidth = getSliceBandwidth(request.source,
			request.destination, request.mobileDevice.getNetworkSliceId());
		double work = request.baselineDuration * baselineBandwidth;

		List<MigrationTransferScheduler.Schedule> schedules = migrationScheduler.start(
			transferId, linkKey(request.source, request.destination),
			request.mobileDevice.getNetworkSliceId(), work, physicalBandwidth,
			CloudSim.clock());
		migrationTransfers.put(transferId, new MigrationTransferMetadata(request));
		applySchedules(schedules);
	}

	/**
	 * Completes the transfer represented by an event payload. Returns the mobile
	 * device for a current event, or {@code null} for a stale rescheduled event.
	 */
	public static synchronized MobileDevice completeMigrationTransfer(
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
		metadata.mobileDevice.setMigTime(result.getDuration());
		applySchedules(result.getSchedules());
		return metadata.mobileDevice;
	}

	/** Returns whether a mobile device currently owns a timed transfer. */
	public static synchronized boolean hasActiveMigrationTransfer(
		MobileDevice mobileDevice) {
		return mobileDevice != null && migrationScheduler.contains(mobileDevice.getId());
	}

	private static double availableBandwidth(FogDevice source, FogDevice destination,
		int[] activeBySlice, int requestedSlice) {
		double physicalBandwidth = Math.min(source.getUplinkBandwidth(),
			destination.getDownlinkBandwidth());
		if (!coversTransportNetwork()) {
			int activeTransferCount = 0;
			for (int active : activeBySlice) {
				activeTransferCount += active;
			}
			return physicalBandwidth / activeTransferCount;
		}
		if (!dynamicBorrowing) {
			return getSliceBandwidth(physicalBandwidth, requestedSlice)
				/ activeBySlice[requestedSlice];
		}

		int activeSlices = 0;
		double idlePercentage = 0.0;
		for (int sliceId = 0; sliceId < activeBySlice.length; sliceId++) {
			if (activeBySlice[sliceId] == 0) {
				idlePercentage += percentages[sliceId];
			}
			else {
				activeSlices++;
			}
		}
		double slicePercentage = percentages[requestedSlice]
			+ idlePercentage / activeSlices;
		return physicalBandwidth * slicePercentage / 100.0
			/ activeBySlice[requestedSlice];
	}

	private static double getAccessPointBandwidth(ApDevice accessPoint,
		MobileDevice mobileDevice, double accessPointBandwidth,
		double mobileDeviceBandwidth) {
		if (accessPoint == null || mobileDevice == null) {
			throw new IllegalArgumentException(
				"Wireless network slicing requires an access point and mobile device");
		}

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
		double physicalBandwidth = source.getUplinkBandwidth();
		if (destination != null) {
			physicalBandwidth = Math.min(physicalBandwidth,
				destination.getDownlinkBandwidth());
		}
		return physicalBandwidth;
	}

	private static double[] parsePercentages(String percentageList,
		String description) {
		String[] values = percentageList.split(",");
		double[] parsed = new double[values.length];
		double total = 0.0;
		for (int index = 0; index < values.length; index++) {
			parsed[index] = Double.parseDouble(values[index].trim());
			if (parsed[index] <= 0.0 || parsed[index] > 100.0) {
				throw new IllegalArgumentException("Each " + description
					+ " percentage must be greater than 0 and at most 100");
			}
			total += parsed[index];
		}
		if (Math.abs(total - 100.0) > 0.000001) {
			throw new IllegalArgumentException(description
				+ " percentages must sum to 100");
		}
		return parsed;
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

	private static void resetUsage() {
		if (CloudSim.running()) {
			for (Map.Entry<Integer, MigrationTransferMetadata> entry
				: migrationTransfers.entrySet()) {
				cancelCompletionEvent(entry.getKey(), entry.getValue());
			}
		}
		activeTransfers.clear();
		transferLinks.clear();
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
			metadata.mobileDevice.setMigTime(schedule.getTotalDuration());
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

	private static void validateTransferRequest(MigrationTransferRequest request) {
		if (request == null || request.mobileDevice == null) {
			throw new IllegalArgumentException(
				"A migration transfer requires a mobile device");
		}
		linkKey(request.source, request.destination);
		validateSliceId(request.mobileDevice.getNetworkSliceId());
		if (Double.isNaN(request.baselineDuration)
			|| Double.isInfinite(request.baselineDuration)
			|| request.baselineDuration < 0.0) {
			throw new IllegalArgumentException(
				"Migration duration must be finite and non-negative");
		}
		if (request.completionDestinationId < 0) {
			throw new IllegalArgumentException(
				"Migration completion requires a destination entity");
		}
	}

	private static int validateSliceId(int sliceId) {
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		return sliceId;
	}
}
