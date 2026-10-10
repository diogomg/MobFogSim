package org.fog.utils;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class SliceTrafficMetricsTest {
	private static final double DELTA = 0.000001;

	@Test
	public void recordsPacketDelayAndMigrationMetricsBySlice() {
		SliceTrafficMetrics metrics = new SliceTrafficMetrics(3);
		metrics.recordWirelessTuple(1,
			new NetworkTransferUsage(10.0, 2.0, 3.0, 5.0));
		metrics.recordWirelessTuple(1,
			new NetworkTransferUsage(20.0, 4.0, 6.0, 10.0));
		metrics.recordMigrationTransfer(2, DataSize.ofBytes(10.0),
			SimulationDuration.ofMilliseconds(3.0));
		metrics.recordMigrationTransfer(2, DataSize.ofBytes(20.0),
			SimulationDuration.ofMilliseconds(7.0));
		metrics.recordCompletedMigration(2, 10.0, 2.0);
		metrics.recordCompletedMigration(2, 30.0, 6.0);

		assertArrayEquals(new long[] { 0L, 2L, 0L },
			metrics.getWirelessTupleTransferCount());
		assertArrayEquals(new double[] { 0.0, 30.0, 0.0 },
			metrics.getTupleTransferredBytes(), DELTA);
		assertArrayEquals(new double[] { 0.0, 30.0, 0.0 },
			metrics.getTupleNetworkDelayTotalMilliseconds(), DELTA);
		assertArrayEquals(new double[] { 0.0, 15.0, 0.0 },
			metrics.getTupleNetworkDelayAverageMilliseconds(), DELTA);
		assertArrayEquals(new long[] { 0L, 0L, 2L },
			metrics.getMigrationTransferCount());
		assertArrayEquals(new double[] { 0.0, 0.0, 30.0 },
			metrics.getMigrationTransferredBytes(), DELTA);
		assertArrayEquals(new double[] { 0.0, 0.0, 10.0 },
			metrics.getMigrationTransferDurationMilliseconds(), DELTA);
		assertArrayEquals(new double[] { 0.0, 0.0, 170.0 },
			metrics.getMigrationUsageByteMilliseconds(), DELTA);
		assertArrayEquals(new long[] { 0L, 0L, 2L },
			metrics.getCompletedMigrationCount());
		assertArrayEquals(new double[] { 0.0, 0.0, 20.0 },
			metrics.getMigrationTimeAverageMilliseconds(), DELTA);
		assertArrayEquals(new double[] { 0.0, 0.0, 30.0 },
			metrics.getMigrationTimeMaximumMilliseconds(), DELTA);
		assertArrayEquals(new double[] { 0.0, 0.0, 4.0 },
			metrics.getDowntimeAverageMilliseconds(), DELTA);
		assertArrayEquals(new double[] { 0.0, 0.0, 6.0 },
			metrics.getDowntimeMaximumMilliseconds(), DELTA);
	}
}
