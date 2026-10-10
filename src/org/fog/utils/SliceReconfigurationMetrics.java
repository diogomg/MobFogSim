package org.fog.utils;

import java.util.Set;

/** Mutable run-local aggregates produced by dynamic slice reconfigurations. */
final class SliceReconfigurationMetrics {
	private long reconfigurationCount;
	private long[] reconfigurationCountBySlice;
	private double[] receivedBandwidthBySlice;

	SliceReconfigurationMetrics(int sliceCount) {
		reset(sliceCount);
	}

	void record(Set<Integer> affectedSlices, double[] receivedBandwidth) {
		if (affectedSlices == null) {
			throw new IllegalArgumentException(
				"Affected slices cannot be null");
		}
		if (receivedBandwidth == null
			|| receivedBandwidth.length != receivedBandwidthBySlice.length) {
			throw new IllegalArgumentException(
				"Reconfiguration bandwidth must contain one value per slice");
		}
		for (Integer sliceId : affectedSlices) {
			if (sliceId == null || sliceId < 0
				|| sliceId >= reconfigurationCountBySlice.length) {
				throw new IllegalArgumentException(
					"Affected slice ID is outside the configured range");
			}
		}
		for (int sliceId = 0; sliceId < receivedBandwidth.length; sliceId++) {
			double bandwidth = receivedBandwidth[sliceId];
			if (!Double.isFinite(bandwidth) || bandwidth < 0.0) {
				throw new IllegalArgumentException(
					"Received slice bandwidth must be finite and non-negative");
			}
		}
		reconfigurationCount++;
		for (Integer sliceId : affectedSlices) {
			reconfigurationCountBySlice[sliceId]++;
		}
		for (int sliceId = 0; sliceId < receivedBandwidth.length; sliceId++) {
			double bandwidth = receivedBandwidth[sliceId];
			receivedBandwidthBySlice[sliceId] += bandwidth;
		}
	}

	long getReconfigurationCount() {
		return reconfigurationCount;
	}

	long[] getReconfigurationCountBySlice() {
		return reconfigurationCountBySlice.clone();
	}

	double[] getReceivedBandwidthBySlice() {
		return receivedBandwidthBySlice.clone();
	}

	void reset(int sliceCount) {
		if (sliceCount <= 0) {
			throw new IllegalArgumentException("Slice count must be positive");
		}
		reconfigurationCount = 0L;
		reconfigurationCountBySlice = new long[sliceCount];
		receivedBandwidthBySlice = new double[sliceCount];
	}
}
