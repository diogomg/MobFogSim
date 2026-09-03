package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class AccessPointTransferSchedulerTest {

	private static final double DELTA = 0.000001;
	private static final String UPLINK = "7:UPLINK";

	@Test
	public void unslicedCapacityIsSharedOnlyByActiveFlows() {
		AccessPointTransferScheduler scheduler = scheduler(false, true);

		List<AccessPointTransferScheduler.Schedule> first = scheduler.start(
			1L, UPLINK, 0, 1000.0, 8000.0, 8000.0, 0.0);
		List<AccessPointTransferScheduler.Schedule> shared = scheduler.start(
			2L, UPLINK, 1, 1000.0, 8000.0, 8000.0, 0.0);

		assertEquals(8000.0, schedule(first, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(4000.0, schedule(shared, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(4000.0, schedule(shared, 2L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(8000.0,
			schedule(shared, 1L).getBandwidthBitsPerSecond()
				+ schedule(shared, 2L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(2000.0, schedule(shared, 1L).getDelayMillis(), DELTA);
	}

	@Test
	public void dynamicSlicesBorrowOnlyWhenAnotherSliceHasNoActiveFlow() {
		AccessPointTransferScheduler scheduler = scheduler(true, true);

		List<AccessPointTransferScheduler.Schedule> borrowed = scheduler.start(
			1L, UPLINK, 1, 1000.0, 8000.0, 8000.0, 0.0);
		List<AccessPointTransferScheduler.Schedule> partitioned = scheduler.start(
			2L, UPLINK, 0, 1000.0, 8000.0, 8000.0, 0.0);

		assertEquals(8000.0,
			schedule(borrowed, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(2400.0,
			schedule(partitioned, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(5600.0,
			schedule(partitioned, 2L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(8000.0,
			schedule(partitioned, 1L).getBandwidthBitsPerSecond()
				+ schedule(partitioned, 2L).getBandwidthBitsPerSecond(), DELTA);
	}

	@Test
	public void fixedSlicesNeverBorrowIdleReservations() {
		AccessPointTransferScheduler scheduler = scheduler(true, false);

		List<AccessPointTransferScheduler.Schedule> schedules = scheduler.start(
			1L, UPLINK, 1, 1000.0, 8000.0, 8000.0, 0.0);

		assertEquals(2400.0,
			schedule(schedules, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(1000.0 * 8.0 * 1000.0 / 2400.0,
			schedule(schedules, 1L).getDelayMillis(), DELTA);
	}

	@Test
	public void maxMinAllocationHonoursDeviceCaps() {
		AccessPointTransferScheduler scheduler =
			new AccessPointTransferScheduler(new double[] { 100.0 }, false, true);
		scheduler.start(1L, UPLINK, 0, 1000.0, 8000.0, 2000.0, 0.0);

		List<AccessPointTransferScheduler.Schedule> schedules = scheduler.start(
			2L, UPLINK, 0, 1000.0, 8000.0, 8000.0, 0.0);

		assertEquals(2000.0,
			schedule(schedules, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(6000.0,
			schedule(schedules, 2L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(8000.0,
			schedule(schedules, 1L).getBandwidthBitsPerSecond()
				+ schedule(schedules, 2L).getBandwidthBitsPerSecond(), DELTA);
	}

	@Test
	public void APDirectionsHaveIndependentCapacity() {
		AccessPointTransferScheduler scheduler = scheduler(false, true);

		List<AccessPointTransferScheduler.Schedule> uplink = scheduler.start(
			1L, UPLINK, 0, 1000.0, 8000.0, 8000.0, 0.0);
		List<AccessPointTransferScheduler.Schedule> downlink = scheduler.start(
			2L, "7:DOWNLINK", 0, 1000.0, 6000.0, 6000.0, 0.0);

		assertEquals(8000.0,
			schedule(uplink, 1L).getBandwidthBitsPerSecond(), DELTA);
		assertEquals(6000.0,
			schedule(downlink, 2L).getBandwidthBitsPerSecond(), DELTA);
	}

	private static AccessPointTransferScheduler scheduler(boolean slicing,
		boolean dynamic) {
		return new AccessPointTransferScheduler(new double[] { 70.0, 30.0 },
			slicing, dynamic);
	}

	private static AccessPointTransferScheduler.Schedule schedule(
		List<AccessPointTransferScheduler.Schedule> schedules, long transferId) {
		for (AccessPointTransferScheduler.Schedule schedule : schedules) {
			if (schedule.getTransferId() == transferId) {
				return schedule;
			}
		}
		throw new AssertionError("No schedule for transfer " + transferId);
	}
}
