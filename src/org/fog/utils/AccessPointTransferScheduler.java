package org.fog.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluid-share scheduler for active tuple transfers on AP direction channels.
 *
 * Work is expressed in bytes and bandwidth in bits per second. A channel is one
 * access point in one direction. Slice capacity is assigned from active flows,
 * not from associated users, and flows within a slice receive max-min fair
 * rates subject to their mobile device's directional capability. In dynamic
 * slicing, a configured reallocation delay pauses the affected AP direction
 * whenever its set of active slices changes.
 */
final class AccessPointTransferScheduler {

	private static final double TIME_EPSILON = 0.000001;
	private static final double BITS_PER_BYTE = 8.0;
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	static final class Schedule {
		private final long transferId;
		private final long generation;
		private final double delayMillis;
		private final double bandwidthBitsPerSecond;

		private Schedule(long transferId, long generation, double delayMillis,
			double bandwidthBitsPerSecond) {
			this.transferId = transferId;
			this.generation = generation;
			this.delayMillis = delayMillis;
			this.bandwidthBitsPerSecond = bandwidthBitsPerSecond;
		}

		long getTransferId() {
			return transferId;
		}

		long getGeneration() {
			return generation;
		}

		double getDelayMillis() {
			return delayMillis;
		}

		double getBandwidthBitsPerSecond() {
			return bandwidthBitsPerSecond;
		}
	}

	static final class Completion {
		private final boolean accepted;
		private final long transferId;
		private final double durationMillis;
		private final List<Schedule> schedules;

		private Completion(boolean accepted, long transferId,
			double durationMillis, List<Schedule> schedules) {
			this.accepted = accepted;
			this.transferId = transferId;
			this.durationMillis = durationMillis;
			this.schedules = schedules;
		}

		boolean isAccepted() {
			return accepted;
		}

		long getTransferId() {
			return transferId;
		}

		double getDurationMillis() {
			return durationMillis;
		}

		List<Schedule> getSchedules() {
			return schedules;
		}
	}

	private static final class Transfer {
		private final long id;
		private final String channel;
		private final int sliceId;
		private final double deviceBandwidth;
		private final double startedAt;
		private double remainingBytes;
		private double bandwidth;
		private long generation;
		private double completionTime;

		private Transfer(long id, String channel, int sliceId,
			double transferBytes, double deviceBandwidth, double startedAt) {
			this.id = id;
			this.channel = channel;
			this.sliceId = sliceId;
			this.remainingBytes = transferBytes;
			this.deviceBandwidth = deviceBandwidth;
			this.startedAt = startedAt;
		}
	}

	private static final class ChannelState {
		private final Map<Long, Transfer> transfers =
			new LinkedHashMap<Long, Transfer>();
		private final Set<Integer> activeSlices = new LinkedHashSet<Integer>();
		private double physicalBandwidth;
		private double lastUpdated;
		private double allocationStartsAt;

		private ChannelState(double physicalBandwidth, double now) {
			this.physicalBandwidth = physicalBandwidth;
			this.lastUpdated = now;
			this.allocationStartsAt = now;
		}
	}

	private final double[] percentages;
	private final boolean slicingEnabled;
	private final boolean dynamicBorrowing;
	private final double reallocationDelayMillis;
	private final SliceReconfigurationMetrics reconfigurationMetrics;
	private final Map<String, ChannelState> channels =
		new LinkedHashMap<String, ChannelState>();
	private final Map<Long, Transfer> transfers =
		new LinkedHashMap<Long, Transfer>();
	private long nextGeneration = 1L;

	AccessPointTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing) {
		this(percentages, slicingEnabled, dynamicBorrowing, 0.0);
	}

	AccessPointTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing, double reallocationDelayMillis) {
		this(percentages, slicingEnabled, dynamicBorrowing,
			reallocationDelayMillis,
			new SliceReconfigurationMetrics(percentages.length));
	}

	AccessPointTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing, double reallocationDelayMillis,
		SliceReconfigurationMetrics reconfigurationMetrics) {
		if (!Double.isFinite(reallocationDelayMillis)
			|| reallocationDelayMillis < 0.0) {
			throw new IllegalArgumentException(
				"Reallocation delay must be finite and non-negative");
		}
		if (reconfigurationMetrics == null) {
			throw new IllegalArgumentException(
				"Slice reconfiguration metrics cannot be null");
		}
		this.percentages = percentages.clone();
		this.slicingEnabled = slicingEnabled;
		this.dynamicBorrowing = dynamicBorrowing;
		this.reallocationDelayMillis = reallocationDelayMillis;
		this.reconfigurationMetrics = reconfigurationMetrics;
	}

	List<Schedule> start(long transferId, String channel, int sliceId,
		double transferBytes, double physicalBandwidth,
		double deviceBandwidth, double now) {
		validateStart(transferId, channel, sliceId, transferBytes,
			physicalBandwidth, deviceBandwidth, now);
		if (transfers.containsKey(transferId)) {
			throw new IllegalArgumentException(
				"Wireless transfer ID is already active: " + transferId);
		}

		ChannelState channelState = channels.get(channel);
		if (channelState == null) {
			channelState = new ChannelState(physicalBandwidth, now);
			channels.put(channel, channelState);
		}
		else {
			advance(channelState, now);
			channelState.physicalBandwidth = Math.min(
				channelState.physicalBandwidth, physicalBandwidth);
		}

		Transfer transfer = new Transfer(transferId, channel, sliceId,
			transferBytes, deviceBandwidth, now);
		channelState.transfers.put(transferId, transfer);
		transfers.put(transferId, transfer);
		return rebalance(channel, now);
	}

	Completion complete(long transferId, long generation, double now) {
		return completeAndStart(transferId, generation, null, now);
	}

	/**
	 * Completes one transfer and atomically installs its same-channel FIFO
	 * successor. Keeping the channel populated across the hand-off prevents a
	 * transient empty active-slice set from being mistaken for a reallocation.
	 */
	Completion completeAndStart(long transferId, long generation,
		long nextTransferId, int nextSliceId, double nextTransferBytes,
		double physicalBandwidth, double deviceBandwidth, double now) {
		Transfer current = transfers.get(transferId);
		if (current == null || current.generation != generation
			|| now + TIME_EPSILON < current.completionTime) {
			return rejectedCompletion(transferId);
		}
		validateStart(nextTransferId, current.channel, nextSliceId,
			nextTransferBytes, physicalBandwidth, deviceBandwidth, now);
		if (transfers.containsKey(nextTransferId)) {
			throw new IllegalArgumentException(
				"Wireless transfer ID is already active: " + nextTransferId);
		}
		Transfer replacement = new Transfer(nextTransferId, current.channel,
			nextSliceId, nextTransferBytes, deviceBandwidth, now);
		return completeAndStart(transferId, generation, replacement,
			physicalBandwidth, now);
	}

	private Completion completeAndStart(long transferId, long generation,
		Transfer replacement, double now) {
		return completeAndStart(transferId, generation, replacement,
			Double.NaN, now);
	}

	private Completion completeAndStart(long transferId, long generation,
		Transfer replacement, double physicalBandwidth, double now) {
		Transfer transfer = transfers.get(transferId);
		if (transfer == null || transfer.generation != generation
			|| now + TIME_EPSILON < transfer.completionTime) {
			return rejectedCompletion(transferId);
		}

		ChannelState channelState = channels.get(transfer.channel);
		advance(channelState, now);
		channelState.transfers.remove(transferId);
		transfers.remove(transferId);
		if (replacement != null) {
			channelState.physicalBandwidth = Math.min(
				channelState.physicalBandwidth, physicalBandwidth);
			channelState.transfers.put(replacement.id, replacement);
			transfers.put(replacement.id, replacement);
		}
		return new Completion(true, transferId, now - transfer.startedAt,
			rebalance(transfer.channel, now));
	}

	private static Completion rejectedCompletion(long transferId) {
		return new Completion(false, transferId, 0.0,
			new ArrayList<Schedule>());
	}

	List<Schedule> cancel(long transferId, double now) {
		Transfer transfer = transfers.remove(transferId);
		if (transfer == null) {
			return new ArrayList<Schedule>();
		}
		ChannelState channelState = channels.get(transfer.channel);
		advance(channelState, now);
		channelState.transfers.remove(transferId);
		return rebalance(transfer.channel, now);
	}

	boolean contains(long transferId) {
		return transfers.containsKey(transferId);
	}

	int activeCount(String channel) {
		ChannelState channelState = channels.get(channel);
		return channelState == null ? 0 : channelState.transfers.size();
	}

	private List<Schedule> rebalance(String channel, double now) {
		List<Schedule> schedules = new ArrayList<Schedule>();
		ChannelState channelState = channels.get(channel);
		if (channelState == null) {
			return schedules;
		}
		Set<Integer> newActiveSlices = activeSlices(channelState);
		boolean reconfiguration = isSliceReconfiguration(
			channelState.activeSlices, newActiveSlices);
		if (reconfiguration && !channelState.transfers.isEmpty()) {
			channelState.allocationStartsAt = now + reallocationDelayMillis;
		}
		channelState.activeSlices.clear();
		channelState.activeSlices.addAll(newActiveSlices);
		if (channelState.transfers.isEmpty()) {
			if (reconfiguration) {
				reconfigurationMetrics.record(new double[percentages.length]);
			}
			channels.remove(channel);
			return schedules;
		}
		double pendingDelay = Math.max(0.0,
			channelState.allocationStartsAt - now);
		Map<Long, Double> allocations = allocate(channelState);
		double[] receivedBandwidth = new double[percentages.length];
		for (Transfer transfer : channelState.transfers.values()) {
			Double allocation = allocations.get(transfer.id);
			if (allocation == null || allocation <= 0.0) {
				throw new IllegalStateException(
					"An active wireless transfer received no bandwidth");
			}
			transfer.bandwidth = allocation;
			receivedBandwidth[transfer.sliceId] += transfer.bandwidth;
			double transferDelay = transferTimeMillis(
				transfer.remainingBytes, transfer.bandwidth);
			// Do not delay a flow whose bytes completed at this same timestamp.
			double delay = transfer.generation > 0L
				&& now + TIME_EPSILON >= transfer.completionTime
				? 0.0 : pendingDelay + transferDelay;
			transfer.generation = nextGeneration++;
			transfer.completionTime = now + delay;
			schedules.add(new Schedule(transfer.id, transfer.generation,
				delay, transfer.bandwidth));
		}
		if (reconfiguration) {
			reconfigurationMetrics.record(receivedBandwidth);
		}
		return schedules;
	}

	private static Set<Integer> activeSlices(ChannelState channelState) {
		Set<Integer> activeSlices = new LinkedHashSet<Integer>();
		for (Transfer transfer : channelState.transfers.values()) {
			activeSlices.add(transfer.sliceId);
		}
		return activeSlices;
	}

	private boolean isSliceReconfiguration(Set<Integer> previousActiveSlices,
		Set<Integer> newActiveSlices) {
		return slicingEnabled && dynamicBorrowing && percentages.length > 1
			&& !previousActiveSlices.equals(newActiveSlices);
	}

	private Map<Long, Double> allocate(ChannelState channelState) {
		Map<Long, Double> allocations = new LinkedHashMap<Long, Double>();
		if (!slicingEnabled) {
			allocateMaxMin(channelState.transfers.values(),
				channelState.physicalBandwidth, allocations);
			return allocations;
		}

		List<List<Transfer>> bySlice = new ArrayList<List<Transfer>>(
			percentages.length);
		int activeSlices = 0;
		double idlePercentage = 0.0;
		for (int sliceId = 0; sliceId < percentages.length; sliceId++) {
			bySlice.add(new ArrayList<Transfer>());
		}
		for (Transfer transfer : channelState.transfers.values()) {
			bySlice.get(transfer.sliceId).add(transfer);
		}
		for (int sliceId = 0; sliceId < percentages.length; sliceId++) {
			if (bySlice.get(sliceId).isEmpty()) {
				idlePercentage += percentages[sliceId];
			}
			else {
				activeSlices++;
			}
		}

		for (int sliceId = 0; sliceId < percentages.length; sliceId++) {
			if (bySlice.get(sliceId).isEmpty()) {
				continue;
			}
			double percentage = percentages[sliceId];
			if (dynamicBorrowing) {
				percentage += idlePercentage / activeSlices;
			}
			allocateMaxMin(bySlice.get(sliceId),
				channelState.physicalBandwidth * percentage / 100.0,
				allocations);
		}
		return allocations;
	}

	private static void allocateMaxMin(Collection<Transfer> candidates,
		double capacity, Map<Long, Double> allocations) {
		Set<Transfer> uncapped = new LinkedHashSet<Transfer>(candidates);
		double remainingCapacity = capacity;
		while (!uncapped.isEmpty()) {
			double equalShare = remainingCapacity / uncapped.size();
			List<Transfer> capped = new ArrayList<Transfer>();
			for (Transfer transfer : uncapped) {
				if (transfer.deviceBandwidth <= equalShare + TIME_EPSILON) {
					allocations.put(transfer.id, transfer.deviceBandwidth);
					remainingCapacity = Math.max(0.0,
						remainingCapacity - transfer.deviceBandwidth);
					capped.add(transfer);
				}
			}
			if (capped.isEmpty()) {
				for (Transfer transfer : uncapped) {
					allocations.put(transfer.id, equalShare);
				}
				return;
			}
			uncapped.removeAll(capped);
		}
	}

	private static void advance(ChannelState channelState, double now) {
		if (channelState == null) {
			return;
		}
		if (now + TIME_EPSILON < channelState.lastUpdated) {
			throw new IllegalArgumentException(
				"Simulation time cannot move backwards");
		}
		double serviceStartedAt = Math.max(channelState.lastUpdated,
			channelState.allocationStartsAt);
		double elapsed = Math.max(0.0, now - serviceStartedAt);
		for (Transfer transfer : channelState.transfers.values()) {
			transfer.remainingBytes = Math.max(0.0,
				transfer.remainingBytes - bytesTransferred(transfer.bandwidth,
					elapsed));
		}
		channelState.lastUpdated = now;
	}

	private void validateStart(long transferId, String channel, int sliceId,
		double transferBytes, double physicalBandwidth,
		double deviceBandwidth, double now) {
		if (transferId < 0L) {
			throw new IllegalArgumentException("Transfer ID cannot be negative");
		}
		if (channel == null || channel.trim().isEmpty()) {
			throw new IllegalArgumentException(
				"A wireless transfer requires an AP direction channel");
		}
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		if (!Double.isFinite(transferBytes) || transferBytes < 0.0) {
			throw new IllegalArgumentException(
				"Transfer bytes must be finite and non-negative");
		}
		if (!Double.isFinite(physicalBandwidth) || physicalBandwidth <= 0.0) {
			throw new IllegalArgumentException(
				"Access-point bandwidth must be finite and positive");
		}
		if (!Double.isFinite(deviceBandwidth) || deviceBandwidth <= 0.0) {
			throw new IllegalArgumentException(
				"Mobile-device bandwidth must be finite and positive");
		}
		if (!Double.isFinite(now) || now < 0.0) {
			throw new IllegalArgumentException(
				"Simulation time must be finite and non-negative");
		}
	}

	private static double bytesTransferred(double bandwidthBitsPerSecond,
		double elapsedMillis) {
		return bandwidthBitsPerSecond * elapsedMillis
			/ (BITS_PER_BYTE * MILLISECONDS_PER_SECOND);
	}

	private static double transferTimeMillis(double transferBytes,
		double bandwidthBitsPerSecond) {
		return transferBytes * BITS_PER_BYTE * MILLISECONDS_PER_SECOND
			/ bandwidthBitsPerSecond;
	}
}
