package org.fog.utils;

public class NetworkUsageMonitor {

	/** Mutable counters owned by one simulation context. */
	public static final class Metrics {
		private double tupleTransferredBytes;
		private double tupleQueueDurationMilliseconds;
		private double tupleTransferDurationMilliseconds;
		private double tuplePropagationDurationMilliseconds;
		private double tupleQueueUsageByteMilliseconds;
		private double tupleTransferUsageByteMilliseconds;
		private double tuplePropagationUsageByteMilliseconds;
		private double migrationUsageByteMilliseconds;
		private double migrationTransferredBytes;
		private double migrationTransferDurationMilliseconds;
	}

	/** Immutable accounting snapshot shared by all result formatters. */
	public static final class Snapshot {
		private final double tupleTransferredBytes;
		private final double tupleQueueDurationMilliseconds;
		private final double tupleTransferDurationMilliseconds;
		private final double tuplePropagationDurationMilliseconds;
		private final double tupleQueueUsageByteMilliseconds;
		private final double tupleTransferUsageByteMilliseconds;
		private final double tuplePropagationUsageByteMilliseconds;
		private final double migrationUsageByteMilliseconds;
		private final double migrationTransferredBytes;
		private final double migrationTransferDurationMilliseconds;

		private Snapshot(Metrics source) {
			tupleTransferredBytes = source.tupleTransferredBytes;
			tupleQueueDurationMilliseconds =
				source.tupleQueueDurationMilliseconds;
			tupleTransferDurationMilliseconds =
				source.tupleTransferDurationMilliseconds;
			tuplePropagationDurationMilliseconds =
				source.tuplePropagationDurationMilliseconds;
			tupleQueueUsageByteMilliseconds =
				source.tupleQueueUsageByteMilliseconds;
			tupleTransferUsageByteMilliseconds =
				source.tupleTransferUsageByteMilliseconds;
			tuplePropagationUsageByteMilliseconds =
				source.tuplePropagationUsageByteMilliseconds;
			migrationUsageByteMilliseconds =
				source.migrationUsageByteMilliseconds;
			migrationTransferredBytes = source.migrationTransferredBytes;
			migrationTransferDurationMilliseconds =
				source.migrationTransferDurationMilliseconds;
		}

		public double getTupleTransferredBytes() {
			return tupleTransferredBytes;
		}

		public double getTupleQueueDurationMilliseconds() {
			return tupleQueueDurationMilliseconds;
		}

		public double getTupleTransferDurationMilliseconds() {
			return tupleTransferDurationMilliseconds;
		}

		public double getTuplePropagationDurationMilliseconds() {
			return tuplePropagationDurationMilliseconds;
		}

		public double getTupleQueueUsageByteMilliseconds() {
			return tupleQueueUsageByteMilliseconds;
		}

		public double getTupleTransferUsageByteMilliseconds() {
			return tupleTransferUsageByteMilliseconds;
		}

		public double getTuplePropagationUsageByteMilliseconds() {
			return tuplePropagationUsageByteMilliseconds;
		}

		public double getTupleUsageByteMilliseconds() {
			return tupleQueueUsageByteMilliseconds
				+ tupleTransferUsageByteMilliseconds
				+ tuplePropagationUsageByteMilliseconds;
		}

		public double getMigrationUsageByteMilliseconds() {
			return migrationUsageByteMilliseconds;
		}

		public double getMigrationTransferredBytes() {
			return migrationTransferredBytes;
		}

		public double getMigrationTransferDurationMilliseconds() {
			return migrationTransferDurationMilliseconds;
		}

		public double getTotalUsageByteMilliseconds() {
			return getTupleUsageByteMilliseconds()
				+ migrationUsageByteMilliseconds;
		}
	}

	private static Metrics metrics = new Metrics();

	private NetworkUsageMonitor() {
	}

	/**
	 * Records a legacy direct tuple hop for which only propagation is modelled.
	 */
	public static void sendingTuple(double latencyMillis, double tupleBytes) {
		recordCompletedTuple(new NetworkTransferUsage(tupleBytes, 0.0, 0.0,
			latencyMillis));
	}

	/** Records all distinct dimensions of one completed tuple transfer. */
	public static void recordCompletedTuple(NetworkTransferUsage usage) {
		if (usage == null) {
			throw new IllegalArgumentException("Tuple transfer usage cannot be null");
		}
		metrics.tupleTransferredBytes += usage.getTransferredBytes();
		metrics.tupleQueueDurationMilliseconds +=
			usage.getQueueDurationMillis();
		metrics.tupleTransferDurationMilliseconds +=
			usage.getTransferDurationMillis();
		metrics.tuplePropagationDurationMilliseconds +=
			usage.getPropagationDurationMillis();
		metrics.tupleQueueUsageByteMilliseconds +=
			usage.getQueueUsageByteMilliseconds();
		metrics.tupleTransferUsageByteMilliseconds +=
			usage.getTransferUsageByteMilliseconds();
		metrics.tuplePropagationUsageByteMilliseconds +=
			usage.getPropagationUsageByteMilliseconds();
	}

	/**
	 * Records one completed migration byte-transfer phase. Fixed, preparation,
	 * and slice-reallocation delays are deliberately excluded because no bytes
	 * traverse the link during those phases.
	 */
	public static void recordCompletedMigration(double transferredBytes,
		double transferDurationMillis) {
		NetworkTransferUsage usage = new NetworkTransferUsage(transferredBytes,
			0.0, transferDurationMillis, 0.0);
		metrics.migrationTransferredBytes += usage.getTransferredBytes();
		metrics.migrationTransferDurationMilliseconds +=
			usage.getTransferDurationMillis();
		metrics.migrationUsageByteMilliseconds +=
			usage.getTransferUsageByteMilliseconds();
	}

	public static Snapshot snapshot() {
		return new Snapshot(metrics);
	}

	public static double getTupleTransferredBytes() {
		return snapshot().getTupleTransferredBytes();
	}

	public static double getTupleQueueDurationMilliseconds() {
		return snapshot().getTupleQueueDurationMilliseconds();
	}

	public static double getTupleTransferDurationMilliseconds() {
		return snapshot().getTupleTransferDurationMilliseconds();
	}

	public static double getTuplePropagationDurationMilliseconds() {
		return snapshot().getTuplePropagationDurationMilliseconds();
	}

	public static double getTupleQueueUsageByteMilliseconds() {
		return snapshot().getTupleQueueUsageByteMilliseconds();
	}

	public static double getTupleTransferUsageByteMilliseconds() {
		return snapshot().getTupleTransferUsageByteMilliseconds();
	}

	public static double getTuplePropagationUsageByteMilliseconds() {
		return snapshot().getTuplePropagationUsageByteMilliseconds();
	}

	public static double getTupleUsageByteMilliseconds() {
		return snapshot().getTupleUsageByteMilliseconds();
	}

	public static double getMigrationUsageByteMilliseconds() {
		return snapshot().getMigrationUsageByteMilliseconds();
	}

	public static double getMigrationTransferredBytes() {
		return snapshot().getMigrationTransferredBytes();
	}

	public static double getMigrationTransferDurationMilliseconds() {
		return snapshot().getMigrationTransferDurationMilliseconds();
	}

	public static double getTotalUsageByteMilliseconds() {
		return snapshot().getTotalUsageByteMilliseconds();
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

}
