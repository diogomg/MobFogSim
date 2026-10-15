package org.cloudbus.cloudsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.junit.Before;
import org.junit.Test;

public class HostMigrationReservationTest {

	@Before
	public void disableLogging() {
		Log.disable();
	}

	@Test
	public void failedReservationLeavesDestinationAndVmUnchanged() {
		PowerHost source = host(1, 1024, 10000, 10000);
		PowerHost destination = host(2, 256, 10000, 10000);
		Vm migrating = vm(1, 512, 1000, 1000);
		assertTrue(source.vmCreate(migrating));

		long storageBefore = destination.getStorage();
		assertFalse(destination.addMigratingInVm(migrating));

		assertFalse(migrating.isInMigration());
		assertSame(source, migrating.getHost());
		assertTrue(source.getVmList().contains(migrating));
		assertFalse(destination.getVmList().contains(migrating));
		assertTrue(destination.getVmsMigratingIn().isEmpty());
		assertEquals(256, destination.getRamProvisioner().getAvailableRam());
		assertEquals(storageBefore, destination.getStorage());
	}

	@Test
	public void successfulReservationCanBeReleasedWithoutMovingVm() {
		PowerHost source = host(1, 1024, 10000, 10000);
		PowerHost destination = host(2, 1024, 10000, 10000);
		Vm migrating = vm(1, 512, 1000, 1000);
		assertTrue(source.vmCreate(migrating));

		long storageBefore = destination.getStorage();
		assertTrue(destination.addMigratingInVm(migrating));
		assertTrue(destination.getVmsMigratingIn().contains(migrating));
		assertEquals(512, destination.getRamProvisioner().getAvailableRam());

		destination.removeMigratingInVm(migrating);

		assertFalse(migrating.isInMigration());
		assertSame(source, migrating.getHost());
		assertTrue(source.getVmList().contains(migrating));
		assertFalse(destination.getVmList().contains(migrating));
		assertEquals(1024, destination.getRamProvisioner().getAvailableRam());
		assertEquals(storageBefore, destination.getStorage());
	}

	private static PowerHost host(int id, int ram, long bandwidth,
		long storage) {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerOverbooking(5000)));
		return new PowerHost(id, new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bandwidth), storage, processingElements,
			new StreamOperatorScheduler(processingElements),
			new FogLinearPowerModel(100.0, 50.0));
	}

	private static Vm vm(int id, int ram, long bandwidth, long size) {
		return new TestVm(id, ram, bandwidth, size);
	}

	private static final class TestVm extends Vm {
		TestVm(int id, int ram, long bandwidth, long size) {
			super(id, 1, 1000, 1, ram, bandwidth, size, "Xen",
				new CloudletSchedulerTimeShared());
		}

		@Override
		public double updateVmProcessing(double currentTime,
			List<Double> mipsShare) {
			return 0.0;
		}
	}
}
