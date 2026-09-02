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

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonFiniteMigrationDuration() {
		NetworkUsageMonitor.recordCompletedMigration(10.0,
			Double.POSITIVE_INFINITY);
	}
}
