package org.fog.utils;

/**
 * Unit-safe accounting value for one completed transfer.
 *
 * <p>Durations remain separate from bytes. Byte-milliseconds are derived only
 * by this type, preventing byte totals from being added to residence-time
 * totals accidentally.</p>
 */
public final class NetworkTransferUsage {

	private final double transferredBytes;
	private final double queueDurationMillis;
	private final double transferDurationMillis;
	private final double propagationDurationMillis;

	public NetworkTransferUsage(double transferredBytes,
		double queueDurationMillis, double transferDurationMillis,
		double propagationDurationMillis) {
		this.transferredBytes = requireNonNegativeFinite(transferredBytes,
			"Transferred bytes");
		this.queueDurationMillis = requireNonNegativeFinite(queueDurationMillis,
			"Queue duration");
		this.transferDurationMillis = requireNonNegativeFinite(
			transferDurationMillis, "Transfer duration");
		this.propagationDurationMillis = requireNonNegativeFinite(
			propagationDurationMillis, "Propagation duration");
		requireFinite(getTotalUsageByteMilliseconds(),
			"Transfer byte-milliseconds");
	}

	public double getTransferredBytes() {
		return transferredBytes;
	}

	public double getQueueDurationMillis() {
		return queueDurationMillis;
	}

	/** Elapsed serialization/contention time after the transfer became active. */
	public double getTransferDurationMillis() {
		return transferDurationMillis;
	}

	public double getPropagationDurationMillis() {
		return propagationDurationMillis;
	}

	public double getQueueUsageByteMilliseconds() {
		return transferredBytes * queueDurationMillis;
	}

	public double getTransferUsageByteMilliseconds() {
		return transferredBytes * transferDurationMillis;
	}

	public double getPropagationUsageByteMilliseconds() {
		return transferredBytes * propagationDurationMillis;
	}

	public double getTotalUsageByteMilliseconds() {
		return getQueueUsageByteMilliseconds()
			+ getTransferUsageByteMilliseconds()
			+ getPropagationUsageByteMilliseconds();
	}

	private static double requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
		return value;
	}

	private static void requireFinite(double value, String description) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(description + " must be finite");
		}
	}
}
