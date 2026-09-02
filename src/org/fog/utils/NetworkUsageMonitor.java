package org.fog.utils;

public class NetworkUsageMonitor {

	private static double tupleUsageByteMilliseconds = 0.0;
	private static double migrationUsageByteMilliseconds = 0.0;
	private static double migrationTransferredBytes = 0.0;

	private NetworkUsageMonitor() {
	}

	/** Records tuple bytes held in the network for the supplied latency. */
	public static void sendingTuple(double latencyMillis, double tupleBytes) {
		requireNonNegativeFinite(latencyMillis, "Tuple latency");
		requireNonNegativeFinite(tupleBytes, "Tuple size");
		tupleUsageByteMilliseconds += latencyMillis * tupleBytes;
	}

	/**
	 * Records one completed migration byte-transfer phase. Fixed and preparation
	 * delays are deliberately excluded because no bytes traverse the link during
	 * those phases.
	 */
	public static void recordCompletedMigration(double transferredBytes,
		double transferDurationMillis) {
		requireNonNegativeFinite(transferredBytes,
			"Migration transferred bytes");
		requireNonNegativeFinite(transferDurationMillis,
			"Migration transfer duration");
		migrationTransferredBytes += transferredBytes;
		migrationUsageByteMilliseconds += transferredBytes
			* transferDurationMillis;
	}

	public static double getTupleUsageByteMilliseconds() {
		return tupleUsageByteMilliseconds;
	}

	public static double getMigrationUsageByteMilliseconds() {
		return migrationUsageByteMilliseconds;
	}

	public static double getMigrationTransferredBytes() {
		return migrationTransferredBytes;
	}

	public static double getTotalUsageByteMilliseconds() {
		return tupleUsageByteMilliseconds + migrationUsageByteMilliseconds;
	}

	/** Clears all counters before a new simulation run or isolated test. */
	public static void reset() {
		tupleUsageByteMilliseconds = 0.0;
		migrationUsageByteMilliseconds = 0.0;
		migrationTransferredBytes = 0.0;
	}

	private static void requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
	}
}
