package org.fog.utils;

public class NetworkUsageMonitor {

	/** Mutable counters owned by one simulation context. */
	public static final class Metrics {
		private double tupleUsageByteMilliseconds;
		private double migrationUsageByteMilliseconds;
		private double migrationTransferredBytes;
	}

	private static Metrics metrics = new Metrics();

	private NetworkUsageMonitor() {
	}

	/** Records tuple bytes held in the network for the supplied latency. */
	public static void sendingTuple(double latencyMillis, double tupleBytes) {
		requireNonNegativeFinite(latencyMillis, "Tuple latency");
		requireNonNegativeFinite(tupleBytes, "Tuple size");
		metrics.tupleUsageByteMilliseconds += latencyMillis * tupleBytes;
	}

	/**
	 * Records one completed migration byte-transfer phase. Fixed, preparation,
	 * and slice-reallocation delays are deliberately excluded because no bytes
	 * traverse the link during those phases.
	 */
	public static void recordCompletedMigration(double transferredBytes,
		double transferDurationMillis) {
		requireNonNegativeFinite(transferredBytes,
			"Migration transferred bytes");
		requireNonNegativeFinite(transferDurationMillis,
			"Migration transfer duration");
		metrics.migrationTransferredBytes += transferredBytes;
		metrics.migrationUsageByteMilliseconds += transferredBytes
			* transferDurationMillis;
	}

	public static double getTupleUsageByteMilliseconds() {
		return metrics.tupleUsageByteMilliseconds;
	}

	public static double getMigrationUsageByteMilliseconds() {
		return metrics.migrationUsageByteMilliseconds;
	}

	public static double getMigrationTransferredBytes() {
		return metrics.migrationTransferredBytes;
	}

	public static double getTotalUsageByteMilliseconds() {
		return metrics.tupleUsageByteMilliseconds
			+ metrics.migrationUsageByteMilliseconds;
	}

	/** Clears all counters before a new simulation run or isolated test. */
	public static void reset() {
		metrics = new Metrics();
	}

	/** Activates counters owned by a simulation context. */
	public static void useMetrics(Metrics runMetrics) {
		if (runMetrics == null) {
			throw new IllegalArgumentException("Network metrics cannot be null");
		}
		metrics = runMetrics;
	}

	private static void requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
	}
}
