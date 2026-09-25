package org.cloudbus.cloudsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.junit.Before;
import org.junit.Test;

public class HostDynamicWorkloadStateHistoryTest {

	@Before
	public void disableLogging() {
		Log.disable();
	}

	@Test
	public void stateHistoryRecordingRemainsEnabledByDefaultForCloudSimHosts() {
		HostDynamicWorkload host = createHostWithVm();

		assertTrue(host.isStateHistoryRecordingEnabled());

		host.updateVmsProcessing(1.0);

		assertEquals(1, host.getStateHistory().size());
		assertEquals(1, host.getVmList().get(0).getStateHistory().size());
	}

	@Test
	public void disablingRecordingClearsAndStopsHostAndVmHistories() {
		HostDynamicWorkload host = createHostWithVm();
		Vm vm = host.getVmList().get(0);
		host.updateVmsProcessing(1.0);

		host.setStateHistoryRecordingEnabled(false);

		assertFalse(host.isStateHistoryRecordingEnabled());
		assertTrue(host.getStateHistory().isEmpty());
		assertTrue(vm.getStateHistory().isEmpty());

		host.updateVmsProcessing(2.0);

		assertTrue(host.getStateHistory().isEmpty());
		assertTrue(vm.getStateHistory().isEmpty());

		host.setStateHistoryRecordingEnabled(true);
		host.updateVmsProcessing(3.0);

		assertEquals(1, host.getStateHistory().size());
		assertEquals(1, vm.getStateHistory().size());
	}

	private static HostDynamicWorkload createHostWithVm() {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerSimple(1000)));
		HostDynamicWorkload host = new HostDynamicWorkload(0,
			new RamProvisionerSimple(2048), new BwProvisionerSimple(10000),
			100000, processingElements, new VmSchedulerTimeShared(processingElements));
		Vm vm = new TestVm();
		assertTrue(host.vmCreate(vm));
		return host;
	}

	private static final class TestVm extends Vm {
		private TestVm() {
			super(0, 0, 1000, 1, 512, 1000, 10000, "Xen",
				new CloudletSchedulerTimeShared());
		}

		@Override
		public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
			return 0.0;
		}
	}
}
