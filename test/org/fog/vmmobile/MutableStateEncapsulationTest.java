package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.junit.Before;
import org.junit.Test;

public class MutableStateEncapsulationTest {

	@Before
	public void initialiseCloudSim() {
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@Test
	public void childMembershipAndLatencyChangeTogether() {
		FogDevice parent = new FogDevice("parent", 0, 0, 1);
		FogDevice child = new FogDevice("child", 0, 0, 2);

		parent.attachChild(child.getId(), 3.5);
		assertTrue(parent.getChildrenIds().contains(child.getId()));
		assertEquals(3.5, parent.getChildToLatencyMap().get(child.getId()),
			0.000001);

		parent.detachChild(child.getId());
		assertFalse(parent.getChildrenIds().contains(child.getId()));
		assertFalse(parent.getChildToLatencyMap().containsKey(child.getId()));
		assertFalse(parent.getChildToOperatorsMap().containsKey(child.getId()));
	}

	@Test
	public void fogDeviceAssociationViewsAreReadOnly() {
		FogDevice server = new FogDevice("server", 0, 0, 1);
		ApDevice accessPoint = new ApDevice("ap", 0, 0, 2);
		MobileDevice mobileDevice = new MobileDevice();
		server.attachAccessPoint(accessPoint);
		server.associateMobileDevice(mobileDevice);
		assertEquals(server, accessPoint.getServerCloudlet());
		assertEquals(server.getId(), accessPoint.getParentId());

		assertUnsupported(new Runnable() {
			@Override
			public void run() {
				server.getApDevices().clear();
			}
		});
		assertUnsupported(new Runnable() {
			@Override
			public void run() {
				server.getSmartThings().remove(mobileDevice);
			}
		});
	}

	@Test
	public void assignedCollectionsAreDefensivelyCopied() {
		FogDevice device = new FogDevice("server", 0, 0, 1);
		List<Integer> children = new ArrayList<Integer>(Arrays.asList(2));
		Map<Integer, Double> latencies = new HashMap<Integer, Double>();
		latencies.put(2, 4.0);
		device.setChildrenIds(children);
		device.setChildToLatencyMap(latencies);

		children.clear();
		latencies.clear();
		assertEquals(Arrays.asList(2), device.getChildrenIds());
		assertEquals(4.0, device.getChildToLatencyMap().get(2), 0.000001);
	}

	@Test
	public void simulationTopologyExposesOnlyReadViews() {
		SimulationTopology topology = new SimulationTopology();
		MobileDevice mobileDevice = new MobileDevice();
		topology.mobileDeviceRegistry().add(mobileDevice);
		topology.activateRegisteredMobileDevices(
			Collections.singletonList(mobileDevice));

		assertEquals(1, topology.getMobileDevices().size());
		assertEquals(1, topology.getActiveMobileDevices().size());
		assertTrue(topology.retireMobileDevice(mobileDevice));
		assertTrue(topology.getActiveMobileDevices().isEmpty());
		assertEquals(1, topology.getMobileDevices().size());
		assertUnsupported(new Runnable() {
			@Override
			public void run() {
				topology.getMobileDevices().clear();
			}
		});
	}

	@Test
	public void timingAndStatisticsMapsExposeOnlyReadViews() {
		TimeKeeper timeKeeper = new TimeKeeper();
		timeKeeper.recordEmission(7, 1.0);
		MyStatistics statistics = new MyStatistics();
		statistics.recordPowerAndEnergy(7, 2.0, 3.0);

		assertUnsupported(new Runnable() {
			@Override
			public void run() {
				timeKeeper.getEmitTimes().clear();
			}
		});
		assertUnsupported(new Runnable() {
			@Override
			public void run() {
				statistics.getPowerHistory().clear();
			}
		});
	}

	private static void assertUnsupported(Runnable mutation) {
		try {
			mutation.run();
			fail("Expected collection view to reject mutation");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}
}
