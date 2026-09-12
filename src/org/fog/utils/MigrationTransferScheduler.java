package org.fog.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fluid-share scheduler for migration transfers on directed physical links.
 *
 * Transfer work is expressed in bytes and allocated bandwidth in bits per
 * second. Whenever the active transfer set changes, the scheduler first
 * accounts for bytes completed at the previous rates and then returns
 * replacement completion schedules using the new rates. In dynamic slicing,
 * a configured reallocation delay pauses the affected link whenever its set of
 * active slices changes.
 */
final class MigrationTransferScheduler {

	private static final double TIME_EPSILON = 0.000001;
	private static final double BITS_PER_BYTE = 8.0;
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	static final class Schedule {
		private final int transferId;
		private final long generation;
		private final double delay;
		private final double bandwidth;
		private final double elapsedTime;

		private Schedule(int transferId, long generation, double delay,
			double bandwidth, double elapsedTime) {
			this.transferId = transferId;
			this.generation = generation;
			this.delay = delay;
			this.bandwidth = bandwidth;
			this.elapsedTime = elapsedTime;
		}

		int getTransferId() {
			return transferId;
		}

		long getGeneration() {
			return generation;
		}

		double getDelay() {
			return delay;
		}

		double getBandwidth() {
			return bandwidth;
		}

		double getTotalDuration() {
			return elapsedTime + delay;
		}
	}

	static final class Completion {
		private final boolean accepted;
		private final int transferId;
		private final double duration;
		private final double reallocationDelayDuration;
		private final double transferredBytes;
		private final List<Schedule> schedules;

		private Completion(boolean accepted, int transferId, double duration,
			double reallocationDelayDuration, double transferredBytes,
			List<Schedule> schedules) {
			this.accepted = accepted;
			this.transferId = transferId;
			this.duration = duration;
			this.reallocationDelayDuration = reallocationDelayDuration;
			this.transferredBytes = transferredBytes;
			this.schedules = schedules;
		}

		boolean isAccepted() {
			return accepted;
		}

		int getTransferId() {
			return transferId;
		}

		double getDuration() {
			return duration;
		}

		double getReallocationDelayDuration() {
			return reallocationDelayDuration;
		}

		double getTransferredBytes() {
			return transferredBytes;
		}

		List<Schedule> getSchedules() {
			return schedules;
		}
	}

	private static final class Transfer {
		private final int id;
		private final TransportLinkId link;
		private final int sliceId;
		private final double startedAt;
		private final double transferBytes;
		private double remainingBytes;
		private double bandwidth;
		private double reallocationDelayDuration;
		private long generation;
		private double completionTime;

		private Transfer(int id, TransportLinkId link, int sliceId, double transferBytes,
			double startedAt) {
			this.id = id;
			this.link = link;
			this.sliceId = sliceId;
			this.transferBytes = transferBytes;
			this.remainingBytes = transferBytes;
			this.startedAt = startedAt;
		}
	}

	private static final class LinkState {
		private final Map<Integer, Transfer> transfers =
			new HashMap<Integer, Transfer>();
		private final Set<Integer> activeSlices = new LinkedHashSet<Integer>();
		private double physicalBandwidth;
		private double lastUpdated;
		private double allocationStartsAt;

		private LinkState(double physicalBandwidth, double now) {
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
	private final Map<TransportLinkId, LinkState> links =
		new HashMap<TransportLinkId, LinkState>();
	private final Map<Integer, Transfer> transfers = new HashMap<Integer, Transfer>();
	private long nextGeneration = 1L;

	MigrationTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing) {
		this(percentages, slicingEnabled, dynamicBorrowing, 0.0);
	}

	MigrationTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing, double reallocationDelayMillis) {
		this(percentages, slicingEnabled, dynamicBorrowing,
			reallocationDelayMillis,
			new SliceReconfigurationMetrics(percentages.length));
	}

	MigrationTransferScheduler(double[] percentages, boolean slicingEnabled,
		boolean dynamicBorrowing, double reallocationDelayMillis,
		SliceReconfigurationMetrics reconfigurationMetrics) {
		if (!isFinite(reallocationDelayMillis) || reallocationDelayMillis < 0.0) {
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

	List<Schedule> start(int transferId, TransportLinkId link, int sliceId,
		double transferBytes,
		double physicalBandwidth, double now) {
		validateStart(transferId, link, sliceId, transferBytes,
			physicalBandwidth, now);

		Set<TransportLinkId> affectedLinks =
			new LinkedHashSet<TransportLinkId>();
		Transfer previous = transfers.remove(transferId);
		if (previous != null) {
			LinkState previousLink = links.get(previous.link);
			advance(previousLink, now);
			previousLink.transfers.remove(transferId);
			affectedLinks.add(previous.link);
		}

		LinkState linkState = links.get(link);
		if (linkState == null) {
			linkState = new LinkState(physicalBandwidth, now);
			links.put(link, linkState);
		}
		else {
			advance(linkState, now);
			// The same directed link must never be allocated above its narrowest
			// physical capacity if device capabilities change during a run.
			linkState.physicalBandwidth = Math.min(linkState.physicalBandwidth,
				physicalBandwidth);
		}

		Transfer transfer = new Transfer(transferId, link, sliceId, transferBytes,
			now);
		linkState.transfers.put(transferId, transfer);
		transfers.put(transferId, transfer);
		affectedLinks.add(link);

		return rebalance(affectedLinks, now);
	}

	Completion complete(int transferId, long generation, double now) {
		Transfer transfer = transfers.get(transferId);
		if (transfer == null || transfer.generation != generation
			|| now + TIME_EPSILON < transfer.completionTime) {
			return new Completion(false, transferId, 0.0, 0.0, 0.0,
				new ArrayList<Schedule>());
		}

		LinkState linkState = links.get(transfer.link);
		advance(linkState, now);
		linkState.transfers.remove(transferId);
		transfers.remove(transferId);
		double duration = now - transfer.startedAt;

		Set<TransportLinkId> affectedLinks =
			new LinkedHashSet<TransportLinkId>();
		affectedLinks.add(transfer.link);
		return new Completion(true, transferId, duration,
			transfer.reallocationDelayDuration, transfer.transferBytes,
			rebalance(affectedLinks, now));
	}

	List<Schedule> cancel(int transferId, double now) {
		Transfer transfer = transfers.remove(transferId);
		if (transfer == null) {
			return new ArrayList<Schedule>();
		}

		LinkState linkState = links.get(transfer.link);
		advance(linkState, now);
		linkState.transfers.remove(transferId);

		Set<TransportLinkId> affectedLinks =
			new LinkedHashSet<TransportLinkId>();
		affectedLinks.add(transfer.link);
		return rebalance(affectedLinks, now);
	}

	boolean contains(int transferId) {
		return transfers.containsKey(transferId);
	}

	private List<Schedule> rebalance(Set<TransportLinkId> affectedLinks,
		double now) {
		List<Schedule> schedules = new ArrayList<Schedule>();
		for (TransportLinkId link : affectedLinks) {
			LinkState linkState = links.get(link);
			if (linkState == null) {
				continue;
			}
			int[] activeBySlice = activeBySlice(linkState);
			Set<Integer> newActiveSlices = activeSlices(activeBySlice);
			boolean reconfiguration = isSliceReconfiguration(
				linkState.activeSlices, newActiveSlices);
			if (reconfiguration && !linkState.transfers.isEmpty()) {
				linkState.allocationStartsAt = now + reallocationDelayMillis;
			}
			linkState.activeSlices.clear();
			linkState.activeSlices.addAll(newActiveSlices);
			if (linkState.transfers.isEmpty()) {
				if (reconfiguration) {
					reconfigurationMetrics.record(new double[percentages.length]);
				}
				links.remove(link);
				continue;
			}
			double pendingDelay = Math.max(0.0,
				linkState.allocationStartsAt - now);
			double[] receivedBandwidth = new double[percentages.length];
			for (Transfer transfer : linkState.transfers.values()) {
				transfer.bandwidth = allocatedBandwidth(linkState.physicalBandwidth,
					activeBySlice, transfer.sliceId);
				if (transfer.bandwidth <= 0.0) {
					throw new IllegalStateException(
						"An active migration received no transport bandwidth");
				}
				receivedBandwidth[transfer.sliceId] += transfer.bandwidth;
				double transferDelay = transferTimeMillis(
					transfer.remainingBytes, transfer.bandwidth);
				// A different event at this same timestamp may have advanced this
				// transfer to completion already. It must not wait for a reallocation
				// that only became necessary after its bytes finished.
				double delay = transfer.generation > 0L
					&& now + TIME_EPSILON >= transfer.completionTime
					? 0.0 : pendingDelay + transferDelay;
				transfer.generation = nextGeneration++;
				transfer.completionTime = now + delay;
				schedules.add(new Schedule(transfer.id, transfer.generation, delay,
					transfer.bandwidth, now - transfer.startedAt));
			}
			if (reconfiguration) {
				reconfigurationMetrics.record(receivedBandwidth);
			}
		}
		return schedules;
	}

	private double allocatedBandwidth(double physicalBandwidth,
		int[] activeBySlice, int requestedSlice) {
		if (!slicingEnabled) {
			int activeTransfers = 0;
			for (int count : activeBySlice) {
				activeTransfers += count;
			}
			return physicalBandwidth / activeTransfers;
		}

		if (!dynamicBorrowing) {
			return physicalBandwidth * percentages[requestedSlice] / 100.0
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

	private int[] activeBySlice(LinkState linkState) {
		int[] activeBySlice = new int[percentages.length];
		for (Transfer transfer : linkState.transfers.values()) {
			activeBySlice[transfer.sliceId]++;
		}
		return activeBySlice;
	}

	private static Set<Integer> activeSlices(int[] activeBySlice) {
		Set<Integer> activeSlices = new LinkedHashSet<Integer>();
		for (int sliceId = 0; sliceId < activeBySlice.length; sliceId++) {
			if (activeBySlice[sliceId] > 0) {
				activeSlices.add(sliceId);
			}
		}
		return activeSlices;
	}

	private boolean isSliceReconfiguration(Set<Integer> previousActiveSlices,
		Set<Integer> newActiveSlices) {
		return slicingEnabled && dynamicBorrowing && percentages.length > 1
			&& !previousActiveSlices.equals(newActiveSlices);
	}

	private static void advance(LinkState linkState, double now) {
		if (linkState == null) {
			return;
		}
		if (now + TIME_EPSILON < linkState.lastUpdated) {
			throw new IllegalArgumentException("Simulation time cannot move backwards");
		}

		double serviceStartedAt = Math.max(linkState.lastUpdated,
			linkState.allocationStartsAt);
		double elapsed = Math.max(0.0, now - serviceStartedAt);
		double pausedElapsed = Math.max(0.0,
			Math.min(now, linkState.allocationStartsAt) - linkState.lastUpdated);
		for (Transfer transfer : linkState.transfers.values()) {
			transfer.reallocationDelayDuration += pausedElapsed;
			transfer.remainingBytes = Math.max(0.0,
				transfer.remainingBytes - bytesTransferred(transfer.bandwidth,
					elapsed));
		}
		linkState.lastUpdated = now;
	}

	private void validateStart(int transferId, TransportLinkId link, int sliceId,
		double transferBytes, double physicalBandwidth, double now) {
		if (transferId < 0) {
			throw new IllegalArgumentException("Transfer ID cannot be negative");
		}
		if (link == null) {
			throw new IllegalArgumentException("A transfer requires a physical link");
		}
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		if (!isFinite(transferBytes) || transferBytes < 0.0) {
			throw new IllegalArgumentException(
				"Transfer bytes must be finite and non-negative");
		}
		if (!isFinite(physicalBandwidth) || physicalBandwidth <= 0.0) {
			throw new IllegalArgumentException(
				"Physical link bandwidth must be finite and positive");
		}
		if (!isFinite(now) || now < 0.0) {
			throw new IllegalArgumentException("Simulation time must be finite and non-negative");
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

	private static boolean isFinite(double value) {
		return !Double.isNaN(value) && !Double.isInfinite(value);
	}
}
