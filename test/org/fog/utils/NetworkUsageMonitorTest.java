package org.fog.utils;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkUsageMonitorTest {

	private static final double DELTA = 0.000001;

	@Before
	@After
	public void resetMonitor() {
		NetworkUsageMonitor.reset();
	}

	@Test
	public void recordsActualMigrationBytesAndTransferTimeInExplicitUnits() {
		NetworkUsageMonitor.recordCompletedMigration(16.0, 25.0);

		assertEquals(16.0,
			NetworkUsageMonitor.getMigrationTransferredBytes(), DELTA);
		assertEquals(400.0,
			NetworkUsageMonitor.getMigrationUsageByteMilliseconds(), DELTA);
		assertEquals(400.0,
			NetworkUsageMonitor.getTotalUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void totalUsageCombinesCompatibleByteMillisecondMetrics() {
		NetworkUsageMonitor.sendingTuple(5.0, 4.0);
		NetworkUsageMonitor.recordCompletedMigration(10.0, 3.0);

		assertEquals(20.0,
			NetworkUsageMonitor.getTupleUsageByteMilliseconds(), DELTA);
		assertEquals(50.0,
			NetworkUsageMonitor.getTotalUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void tupleDimensionsRemainDistinctAndDeriveByteMilliseconds() {
		NetworkUsageMonitor.recordCompletedTuple(new NetworkTransferUsage(
			4.0, 3.0, 5.0, 7.0));

		assertEquals(4.0, NetworkUsageMonitor.getTupleTransferredBytes(), DELTA);
		assertEquals(3.0,
			NetworkUsageMonitor.getTupleQueueDurationMilliseconds(), DELTA);
		assertEquals(5.0,
			NetworkUsageMonitor.getTupleTransferDurationMilliseconds(), DELTA);
		assertEquals(7.0,
			NetworkUsageMonitor.getTuplePropagationDurationMilliseconds(), DELTA);
		assertEquals(12.0,
			NetworkUsageMonitor.getTupleQueueUsageByteMilliseconds(), DELTA);
		assertEquals(20.0,
			NetworkUsageMonitor.getTupleTransferUsageByteMilliseconds(), DELTA);
		assertEquals(28.0,
			NetworkUsageMonitor.getTuplePropagationUsageByteMilliseconds(), DELTA);
		assertEquals(60.0,
			NetworkUsageMonitor.getTupleUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void zeroDurationTransferRecordsBytesWithoutInventingUsage() {
		NetworkUsageMonitor.recordCompletedTuple(new NetworkTransferUsage(
			16.0, 0.0, 0.0, 0.0));

		assertEquals(16.0,
			NetworkUsageMonitor.getTupleTransferredBytes(), DELTA);
		assertEquals(0.0,
			NetworkUsageMonitor.getTupleUsageByteMilliseconds(), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonFiniteMigrationDuration() {
		NetworkUsageMonitor.recordCompletedMigration(10.0,
			Double.POSITIVE_INFINITY);
	}
}
