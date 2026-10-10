package org.fog.utils;

/** Run-local packet and migration metrics attributed to network slices. */
final class SliceTrafficMetrics {
	private long[] wirelessTupleTransferCount;
	private double[] tupleTransferredBytes;
	private double[] tupleQueueDurationMilliseconds;
	private double[] tupleTransferDurationMilliseconds;
	private double[] tuplePropagationDurationMilliseconds;
	private long[] migrationTransferCount;
	private double[] migrationTransferredBytes;
	private double[] migrationTransferDurationMilliseconds;
	private double[] migrationUsageByteMilliseconds;
	private long[] completedMigrationCount;
	private double[] migrationTimeTotalMilliseconds;
	private double[] migrationTimeMaximumMilliseconds;
	private double[] downtimeTotalMilliseconds;
	private double[] downtimeMaximumMilliseconds;

	SliceTrafficMetrics(int sliceCount) {
		reset(sliceCount);
	}

	void recordWirelessTuple(int sliceId, NetworkTransferUsage usage) {
		validateSliceId(sliceId);
		if (usage == null) {
			throw new IllegalArgumentException(
				"Wireless tuple usage cannot be null");
		}
		wirelessTupleTransferCount[sliceId]++;
		tupleTransferredBytes[sliceId] += usage.getTransferredBytes();
		tupleQueueDurationMilliseconds[sliceId] +=
			usage.getQueueDurationMillis();
		tupleTransferDurationMilliseconds[sliceId] +=
			usage.getTransferDurationMillis();
		tuplePropagationDurationMilliseconds[sliceId] +=
			usage.getPropagationDurationMillis();
	}

	void recordMigrationTransfer(int sliceId, DataSize transferredData,
		SimulationDuration transferDuration) {
		validateSliceId(sliceId);
		if (transferredData == null || transferDuration == null) {
			throw new IllegalArgumentException(
				"Migration transfer usage cannot be null");
		}
		double transferredBytes = transferredData.toBytes();
		double durationMillis = transferDuration.toMilliseconds();
		migrationTransferCount[sliceId]++;
		migrationTransferredBytes[sliceId] += transferredBytes;
		migrationTransferDurationMilliseconds[sliceId] += durationMillis;
		migrationUsageByteMilliseconds[sliceId] +=
			transferredBytes * durationMillis;
	}

	void recordCompletedMigration(int sliceId, double migrationTimeMillis,
		double downtimeMillis) {
		validateSliceId(sliceId);
		validateDuration(migrationTimeMillis, "Migration time");
		validateDuration(downtimeMillis, "Migration downtime");
		completedMigrationCount[sliceId]++;
		migrationTimeTotalMilliseconds[sliceId] += migrationTimeMillis;
		migrationTimeMaximumMilliseconds[sliceId] = Math.max(
			migrationTimeMaximumMilliseconds[sliceId], migrationTimeMillis);
		downtimeTotalMilliseconds[sliceId] += downtimeMillis;
		downtimeMaximumMilliseconds[sliceId] = Math.max(
			downtimeMaximumMilliseconds[sliceId], downtimeMillis);
	}

	long[] getWirelessTupleTransferCount() {
		return wirelessTupleTransferCount.clone();
	}

	double[] getTupleTransferredBytes() {
		return tupleTransferredBytes.clone();
	}

	double[] getTupleQueueDurationMilliseconds() {
		return tupleQueueDurationMilliseconds.clone();
	}

	double[] getTupleTransferDurationMilliseconds() {
		return tupleTransferDurationMilliseconds.clone();
	}

	double[] getTuplePropagationDurationMilliseconds() {
		return tuplePropagationDurationMilliseconds.clone();
	}

	double[] getTupleNetworkDelayTotalMilliseconds() {
		double[] totals = new double[wirelessTupleTransferCount.length];
		for (int sliceId = 0; sliceId < totals.length; sliceId++) {
			totals[sliceId] = tupleQueueDurationMilliseconds[sliceId]
				+ tupleTransferDurationMilliseconds[sliceId]
				+ tuplePropagationDurationMilliseconds[sliceId];
		}
		return totals;
	}

	double[] getTupleNetworkDelayAverageMilliseconds() {
		return averages(getTupleNetworkDelayTotalMilliseconds(),
			wirelessTupleTransferCount);
	}

	long[] getMigrationTransferCount() {
		return migrationTransferCount.clone();
	}

	double[] getMigrationTransferredBytes() {
		return migrationTransferredBytes.clone();
	}

	double[] getMigrationTransferDurationMilliseconds() {
		return migrationTransferDurationMilliseconds.clone();
	}

	double[] getMigrationUsageByteMilliseconds() {
		return migrationUsageByteMilliseconds.clone();
	}

	double[] getMigrationUsageMeanByteMilliseconds() {
		return averages(migrationUsageByteMilliseconds,
			completedMigrationCount);
	}

	long[] getCompletedMigrationCount() {
		return completedMigrationCount.clone();
	}

	double[] getMigrationTimeAverageMilliseconds() {
		return averages(migrationTimeTotalMilliseconds,
			completedMigrationCount);
	}

	double[] getMigrationTimeMaximumMilliseconds() {
		return migrationTimeMaximumMilliseconds.clone();
	}

	double[] getDowntimeAverageMilliseconds() {
		return averages(downtimeTotalMilliseconds, completedMigrationCount);
	}

	double[] getDowntimeMaximumMilliseconds() {
		return downtimeMaximumMilliseconds.clone();
	}

	void reset(int sliceCount) {
		if (sliceCount <= 0) {
			throw new IllegalArgumentException("Slice count must be positive");
		}
		wirelessTupleTransferCount = new long[sliceCount];
		tupleTransferredBytes = new double[sliceCount];
		tupleQueueDurationMilliseconds = new double[sliceCount];
		tupleTransferDurationMilliseconds = new double[sliceCount];
		tuplePropagationDurationMilliseconds = new double[sliceCount];
		migrationTransferCount = new long[sliceCount];
		migrationTransferredBytes = new double[sliceCount];
		migrationTransferDurationMilliseconds = new double[sliceCount];
		migrationUsageByteMilliseconds = new double[sliceCount];
		completedMigrationCount = new long[sliceCount];
		migrationTimeTotalMilliseconds = new double[sliceCount];
		migrationTimeMaximumMilliseconds = new double[sliceCount];
		downtimeTotalMilliseconds = new double[sliceCount];
		downtimeMaximumMilliseconds = new double[sliceCount];
	}

	private void validateSliceId(int sliceId) {
		if (sliceId < 0 || sliceId >= wirelessTupleTransferCount.length) {
			throw new IllegalArgumentException(
				"Slice ID is outside the configured range");
		}
	}

	private static void validateDuration(double value, String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
	}

	private static double[] averages(double[] totals, long[] counts) {
		double[] averages = new double[totals.length];
		for (int sliceId = 0; sliceId < totals.length; sliceId++) {
			averages[sliceId] = counts[sliceId] == 0L ? 0.0
				: totals[sliceId] / counts[sliceId];
		}
		return averages;
	}
}
