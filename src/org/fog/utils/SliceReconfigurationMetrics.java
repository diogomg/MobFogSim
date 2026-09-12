package org.fog.utils;

/** Mutable run-local aggregates produced by dynamic slice reconfigurations. */
final class SliceReconfigurationMetrics {
	private long reconfigurationCount;
	private double[] receivedBandwidthBySlice;

	SliceReconfigurationMetrics(int sliceCount) {
		reset(sliceCount);
	}

	void record(double[] receivedBandwidth) {
		if (receivedBandwidth == null
			|| receivedBandwidth.length != receivedBandwidthBySlice.length) {
			throw new IllegalArgumentException(
				"Reconfiguration bandwidth must contain one value per slice");
		}
		reconfigurationCount++;
		for (int sliceId = 0; sliceId < receivedBandwidth.length; sliceId++) {
			double bandwidth = receivedBandwidth[sliceId];
			if (!Double.isFinite(bandwidth) || bandwidth < 0.0) {
				throw new IllegalArgumentException(
					"Received slice bandwidth must be finite and non-negative");
			}
			receivedBandwidthBySlice[sliceId] += bandwidth;
		}
	}

	long getReconfigurationCount() {
		return reconfigurationCount;
	}

	double[] getReceivedBandwidthBySlice() {
		return receivedBandwidthBySlice.clone();
	}

	void reset(int sliceCount) {
		if (sliceCount <= 0) {
			throw new IllegalArgumentException("Slice count must be positive");
		}
		reconfigurationCount = 0L;
		receivedBandwidthBySlice = new double[sliceCount];
	}
}
