package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class MigrationTransferSchedulerTest {

	private static final double DELTA = 0.000001;
	private static final String LINK = "source->destination";

	@Test
	public void dynamicTransfersAreRescheduledWhenAnotherTransferStartsAndFinishes() {
		MigrationTransferScheduler scheduler = dynamicScheduler();

		MigrationTransferScheduler.Schedule first = only(
			scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0));
		assertEquals(800.0, first.getBandwidth(), DELTA);
		assertEquals(70.0, first.getDelay(), DELTA);

		List<MigrationTransferScheduler.Schedule> overlap =
			scheduler.start(2, LINK, 0, 7.0, 800.0, 35.0);
		MigrationTransferScheduler.Schedule rescheduledFirst = find(overlap, 1);
		MigrationTransferScheduler.Schedule second = find(overlap, 2);
		assertEquals(400.0, rescheduledFirst.getBandwidth(), DELTA);
		assertEquals(400.0, second.getBandwidth(), DELTA);
		assertEquals(70.0, rescheduledFirst.getDelay(), DELTA);
		assertEquals(140.0, second.getDelay(), DELTA);

		MigrationTransferScheduler.Completion completion = scheduler.complete(1,
			rescheduledFirst.getGeneration(), 105.0);
		assertTrue(completion.isAccepted());
		assertEquals(105.0, completion.getDuration(), DELTA);
		assertEquals(7.0, completion.getTransferredBytes(), DELTA);
		MigrationTransferScheduler.Schedule acceleratedSecond =
			only(completion.getSchedules());
		assertEquals(800.0, acceleratedSecond.getBandwidth(), DELTA);
		assertEquals(35.0, acceleratedSecond.getDelay(), DELTA);
		assertEquals(105.0, acceleratedSecond.getTotalDuration(), DELTA);
	}

	@Test
	public void obsoleteCompletionIsRejectedAfterARebalance() {
		MigrationTransferScheduler scheduler = dynamicScheduler();
		MigrationTransferScheduler.Schedule original = only(
			scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0));

		scheduler.start(2, LINK, 0, 7.0, 800.0, 35.0);
		MigrationTransferScheduler.Completion stale = scheduler.complete(1,
			original.getGeneration(), 70.0);

		assertFalse(stale.isAccepted());
		assertTrue(scheduler.contains(1));
		assertTrue(stale.getSchedules().isEmpty());
	}

	@Test
	public void dynamicActiveSlicesTogetherUseExactlyThePhysicalCapacity() {
		MigrationTransferScheduler scheduler = dynamicScheduler();

		scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0);
		List<MigrationTransferScheduler.Schedule> schedules =
			scheduler.start(2, LINK, 1, 3.0, 800.0, 0.0);

		assertEquals(560.0, find(schedules, 1).getBandwidth(), DELTA);
		assertEquals(240.0, find(schedules, 2).getBandwidth(), DELTA);
		assertEquals(800.0, totalBandwidth(schedules), DELTA);
		assertEquals(100.0, find(schedules, 1).getDelay(), DELTA);
		assertEquals(100.0, find(schedules, 2).getDelay(), DELTA);
	}

	@Test
	public void fixedTransfersInOneSliceShareOnlyThatSlicesCapacity() {
		MigrationTransferScheduler scheduler = new MigrationTransferScheduler(
			new double[] { 70.0, 30.0 }, true, false);

		MigrationTransferScheduler.Schedule first = only(
			scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0));
		assertEquals(560.0, first.getBandwidth(), DELTA);

		List<MigrationTransferScheduler.Schedule> overlap =
			scheduler.start(2, LINK, 0, 7.0, 800.0, 50.0);
		MigrationTransferScheduler.Schedule rescheduledFirst = find(overlap, 1);
		MigrationTransferScheduler.Schedule second = find(overlap, 2);
		assertEquals(280.0, rescheduledFirst.getBandwidth(), DELTA);
		assertEquals(280.0, second.getBandwidth(), DELTA);

		MigrationTransferScheduler.Completion completion = scheduler.complete(1,
			rescheduledFirst.getGeneration(), 150.0);
		MigrationTransferScheduler.Schedule remaining = only(completion.getSchedules());
		assertEquals(560.0, remaining.getBandwidth(), DELTA);
		assertEquals(50.0, remaining.getDelay(), DELTA);
		assertEquals(150.0, remaining.getTotalDuration(), DELTA);
	}

	@Test
	public void fixedActiveSlicesNeverExceedPhysicalBandwidth() {
		MigrationTransferScheduler scheduler = new MigrationTransferScheduler(
			new double[] { 70.0, 30.0 }, true, false);

		scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0);
		List<MigrationTransferScheduler.Schedule> schedules =
			scheduler.start(2, LINK, 1, 3.0, 800.0, 0.0);

		assertEquals(560.0, find(schedules, 1).getBandwidth(), DELTA);
		assertEquals(240.0, find(schedules, 2).getBandwidth(), DELTA);
		assertEquals(800.0, totalBandwidth(schedules), DELTA);
	}

	@Test
	public void unslicedTransportStillSharesThePhysicalLink() {
		MigrationTransferScheduler scheduler = new MigrationTransferScheduler(
			new double[] { 70.0, 30.0 }, false, true);

		scheduler.start(1, LINK, 0, 10.0, 800.0, 0.0);
		List<MigrationTransferScheduler.Schedule> schedules =
			scheduler.start(2, LINK, 1, 10.0, 800.0, 0.0);

		assertEquals(400.0, find(schedules, 1).getBandwidth(), DELTA);
		assertEquals(400.0, find(schedules, 2).getBandwidth(), DELTA);
		assertEquals(800.0, totalBandwidth(schedules), DELTA);
	}

	@Test
	public void cancellingATransferImmediatelyReallocatesItsCapacity() {
		MigrationTransferScheduler scheduler = dynamicScheduler();
		scheduler.start(1, LINK, 0, 7.0, 800.0, 0.0);
		scheduler.start(2, LINK, 0, 7.0, 800.0, 35.0);

		MigrationTransferScheduler.Schedule remaining =
			only(scheduler.cancel(1, 55.0));

		assertFalse(scheduler.contains(1));
		assertEquals(800.0, remaining.getBandwidth(), DELTA);
		assertEquals(60.0, remaining.getDelay(), DELTA);
		assertEquals(80.0, remaining.getTotalDuration(), DELTA);
	}

	private static MigrationTransferScheduler dynamicScheduler() {
		return new MigrationTransferScheduler(new double[] { 70.0, 30.0 }, true, true);
	}

	private static MigrationTransferScheduler.Schedule only(
		List<MigrationTransferScheduler.Schedule> schedules) {
		assertEquals(1, schedules.size());
		return schedules.get(0);
	}

	private static MigrationTransferScheduler.Schedule find(
		List<MigrationTransferScheduler.Schedule> schedules, int transferId) {
		for (MigrationTransferScheduler.Schedule schedule : schedules) {
			if (schedule.getTransferId() == transferId) {
				return schedule;
			}
		}
		throw new AssertionError("No schedule for transfer " + transferId);
	}

	private static double totalBandwidth(
		List<MigrationTransferScheduler.Schedule> schedules) {
		double total = 0.0;
		for (MigrationTransferScheduler.Schedule schedule : schedules) {
			total += schedule.getBandwidth();
		}
		return total;
	}
}
