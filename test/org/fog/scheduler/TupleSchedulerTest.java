package org.fog.scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Calendar;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.junit.Before;
import org.junit.Test;

public class TupleSchedulerTest {

	@Before
	public void setUp() {
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void constructorRejectsZeroNegativeAndNonFiniteCapacity() {
		assertInvalidCapacity(0.0, 1);
		assertInvalidCapacity(-1.0, 1);
		assertInvalidCapacity(Double.NaN, 1);
		assertInvalidCapacity(Double.POSITIVE_INFINITY, 1);
		assertInvalidCapacity(1000.0, 0);
		assertInvalidCapacity(1000.0, -1);
	}

	@Test
	public void finishEstimateUsesConfiguredMipsAndProcessingElements() {
		TupleScheduler scheduler = new TupleScheduler(1000.0, 2);
		ResCloudlet cloudlet = reservedCloudlet(4000, 4);

		assertEquals(2000.0,
			scheduler.getTotalCurrentAllocatedMipsForCloudlet(cloudlet, 10.0),
			0.0);
		assertEquals(18.0,
			scheduler.getEstimatedFinishTime(cloudlet, 10.0), 0.000001);
	}

	@Test
	public void completedCloudletFinishesAtTheSuppliedTime() {
		TupleScheduler scheduler = new TupleScheduler(1000.0, 2);
		ResCloudlet cloudlet = reservedCloudlet(4000, 2);
		cloudlet.updateCloudletFinishedSoFar(
			cloudlet.getCloudlet().getCloudletTotalLength() * Consts.MILLION);

		assertEquals(25.0,
			scheduler.getEstimatedFinishTime(cloudlet, 25.0), 0.0);
	}

	@Test
	public void finishEstimateRejectsNullCloudletAndInvalidTime() {
		TupleScheduler scheduler = new TupleScheduler(1000.0, 1);
		assertInvalidEstimate(scheduler, null, 0.0);
		assertInvalidEstimate(scheduler, reservedCloudlet(1000, 1), -1.0);
		assertInvalidEstimate(scheduler, reservedCloudlet(1000, 1),
			Double.NaN);
	}

	private static ResCloudlet reservedCloudlet(long length, int pes) {
		UtilizationModelFull utilization = new UtilizationModelFull();
		Cloudlet cloudlet = new Cloudlet(1, length, pes, 1, 1,
			utilization, utilization, utilization);
		cloudlet.setResourceParameter(0, 1.0);
		return new ResCloudlet(cloudlet);
	}

	private static void assertInvalidCapacity(double mips, int pes) {
		try {
			new TupleScheduler(mips, pes);
			fail("Expected invalid scheduler capacity to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void assertInvalidEstimate(TupleScheduler scheduler,
		ResCloudlet cloudlet, double time) {
		try {
			scheduler.getEstimatedFinishTime(cloudlet, time);
			fail("Expected invalid completion estimate input to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}
}
