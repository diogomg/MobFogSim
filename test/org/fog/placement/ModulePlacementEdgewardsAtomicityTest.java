package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.junit.Before;
import org.junit.Test;

public class ModulePlacementEdgewardsAtomicityTest {

	private static final double DELTA = 0.0000001;

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@Test
	public void successfulShiftAddsToExistingDestinationCpuLoad() {
		Application application = application("processor", "unrelated");
		TestFogDevice source = device("source", 50);
		TestFogDevice parent = device("parent", 100);
		connect(source, parent);
		TestPlacement placement = placement(application, source, parent);
		placement.place(source, "processor", 40.0, 1);
		placement.place(parent, "unrelated", 15.0, 1);

		List<String> shifted = placement.shift("processor", 20.0, source.getId());

		assertEquals(Collections.singletonList("processor"), shifted);
		assertEquals(0.0, placement.getCurrentCpuLoad().get(source.getId()), DELTA);
		assertTrue(placement.getCurrentModuleMap().get(source.getId()).isEmpty());
		assertEquals(75.0, placement.getCurrentCpuLoad().get(parent.getId()), DELTA);
		assertEquals(15.0, moduleLoad(placement, parent, "unrelated"), DELTA);
		assertEquals(60.0, moduleLoad(placement, parent, "processor"), DELTA);
		assertEquals(Integer.valueOf(2), moduleInstances(placement, parent, "processor"));
		assertEquals(placement.getCurrentModuleInstanceNum(),
			placement.getModuleInstanceCountMap());
	}

	@Test
	public void insufficientParentCapacityMovesDependenciesToGrandparent() {
		Application application = application("processor", "analytics", "cache", "root-base");
		application.addAppEdge("processor", "analytics", 100, 100, "ANALYTICS",
			Tuple.UP, AppEdge.MODULE);
		TestFogDevice source = device("source", 50);
		TestFogDevice edge = device("edge", 100);
		TestFogDevice root = device("root", 100);
		connect(source, edge);
		connect(edge, root);
		TestPlacement placement = placement(application, source, edge, root);
		placement.place(source, "processor", 20.0, 1);
		placement.place(edge, "analytics", 30.0, 2);
		placement.place(edge, "cache", 50.0, 1);
		placement.place(root, "root-base", 10.0, 1);

		List<String> shifted = placement.shift("processor", 20.0, source.getId());

		assertEquals(Arrays.asList("processor", "analytics"), shifted);
		assertEquals(0.0, placement.getCurrentCpuLoad().get(source.getId()), DELTA);
		assertEquals(50.0, placement.getCurrentCpuLoad().get(edge.getId()), DELTA);
		assertFalse(placement.getCurrentModuleMap().get(edge.getId()).contains("analytics"));
		assertEquals(50.0, moduleLoad(placement, edge, "cache"), DELTA);
		assertEquals(80.0, placement.getCurrentCpuLoad().get(root.getId()), DELTA);
		assertEquals(40.0, moduleLoad(placement, root, "processor"), DELTA);
		assertEquals(30.0, moduleLoad(placement, root, "analytics"), DELTA);
		assertEquals(Integer.valueOf(2), moduleInstances(placement, root, "processor"));
		assertEquals(Integer.valueOf(2), moduleInstances(placement, root, "analytics"));
	}

	@Test
	public void rootCapacityFailureLeavesAllPlacementStateUnchanged() {
		Application application = application("processor", "root-base");
		TestFogDevice source = device("source", 50);
		TestFogDevice root = device("root", 50);
		connect(source, root);
		TestPlacement placement = placement(application, source, root);
		placement.place(source, "processor", 40.0, 1);
		placement.place(root, "root-base", 10.0, 1);
		PlacementSnapshot before = new PlacementSnapshot(placement);

		List<String> shifted = placement.shift("processor", 20.0, source.getId());

		assertTrue(shifted.isEmpty());
		before.assertUnchanged(placement);
	}

	@Test
	public void invalidInstanceStateFailsBeforeAnyPlacementMutation() {
		Application application = application("processor");
		TestFogDevice source = device("source", 50);
		TestFogDevice parent = device("parent", 100);
		connect(source, parent);
		TestPlacement placement = placement(application, source, parent);
		placement.placeWithoutInstanceCount(source, "processor", 40.0);
		PlacementSnapshot before = new PlacementSnapshot(placement);

		try {
			placement.shift("processor", 20.0, source.getId());
			fail("Expected inconsistent instance state to be rejected");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("Inconsistent placement state"));
		}

		before.assertUnchanged(placement);
	}

	private static TestPlacement placement(Application application,
		TestFogDevice... devices) {
		return new TestPlacement(Arrays.<FogDevice>asList(devices), application);
	}

	private static Application application(String... modules) {
		Application application = Application.createApplication("placement-app", 1);
		for (String module : modules) {
			application.addAppModule(module, 10);
		}
		return application;
	}

	private static TestFogDevice device(String name, int capacity) {
		return new TestFogDevice(name, capacity);
	}

	private static void connect(TestFogDevice child, TestFogDevice parent) {
		child.setParentId(parent.getId());
		parent.setParentId(-1);
	}

	private static double moduleLoad(TestPlacement placement, FogDevice device,
		String module) {
		return placement.getCurrentModuleLoadMap().get(device.getId()).get(module);
	}

	private static Integer moduleInstances(TestPlacement placement, FogDevice device,
		String module) {
		return placement.getCurrentModuleInstanceNum().get(device.getId()).get(module);
	}

	private static PowerHost host(int totalMips) {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerOverbooking(totalMips)));
		return new PowerHost(0, new RamProvisionerSimple(1024),
			new BwProvisionerOverbooking(10000), 100000, processingElements,
			new StreamOperatorScheduler(processingElements),
			new FogLinearPowerModel(100.0, 50.0));
	}

	private static final class TestFogDevice extends FogDevice {
		private final PowerHost host;

		private TestFogDevice(String name, int capacity) {
			super(name, 0, 0, 0);
			host = host(capacity);
			setParentId(-1);
		}

		@Override
		public PowerHost getHost() {
			return host;
		}
	}

	private static final class TestPlacement extends ModulePlacementEdgewards {
		private TestPlacement(List<FogDevice> devices, Application application) {
			super(devices, Collections.<Sensor>emptyList(),
				Collections.<Actuator>emptyList(), application,
				ModuleMapping.createModuleMapping());
		}

		@Override
		protected void mapModules() {
			// Tests install an explicit state after the superclass initializes its maps.
		}

		private void place(FogDevice device, String module, double load, int instances) {
			getCurrentModuleMap().get(device.getId()).add(module);
			getCurrentModuleLoadMap().get(device.getId()).put(module, load);
			getCurrentModuleInstanceNum().get(device.getId()).put(module, instances);
			getCurrentCpuLoad().put(device.getId(),
				getCurrentCpuLoad().get(device.getId()) + load);
		}

		private void placeWithoutInstanceCount(FogDevice device, String module,
			double load) {
			getCurrentModuleMap().get(device.getId()).add(module);
			getCurrentModuleLoadMap().get(device.getId()).put(module, load);
			getCurrentCpuLoad().put(device.getId(), load);
		}

		private List<String> shift(String module, double load, int sourceDeviceId) {
			return shiftModuleNorth(module, load, sourceDeviceId);
		}
	}

	private static final class PlacementSnapshot {
		private final Map<Integer, Double> cpuLoadReference;
		private final Map<Integer, List<String>> moduleMapReference;
		private final Map<Integer, Map<String, Double>> moduleLoadMapReference;
		private final Map<Integer, Map<String, Integer>> instanceMapReference;
		private final Map<Integer, Double> cpuLoads;
		private final Map<Integer, List<String>> modules;
		private final Map<Integer, Map<String, Double>> moduleLoads;
		private final Map<Integer, Map<String, Integer>> instances;

		private PlacementSnapshot(TestPlacement placement) {
			cpuLoadReference = placement.getCurrentCpuLoad();
			moduleMapReference = placement.getCurrentModuleMap();
			moduleLoadMapReference = placement.getCurrentModuleLoadMap();
			instanceMapReference = placement.getCurrentModuleInstanceNum();
			cpuLoads = new HashMap<Integer, Double>(cpuLoadReference);
			modules = copyLists(moduleMapReference);
			moduleLoads = copyNestedMaps(moduleLoadMapReference);
			instances = copyNestedMaps(instanceMapReference);
		}

		private void assertUnchanged(TestPlacement placement) {
			assertSame(cpuLoadReference, placement.getCurrentCpuLoad());
			assertSame(moduleMapReference, placement.getCurrentModuleMap());
			assertSame(moduleLoadMapReference, placement.getCurrentModuleLoadMap());
			assertSame(instanceMapReference, placement.getCurrentModuleInstanceNum());
			assertEquals(cpuLoads, placement.getCurrentCpuLoad());
			assertEquals(modules, placement.getCurrentModuleMap());
			assertEquals(moduleLoads, placement.getCurrentModuleLoadMap());
			assertEquals(instances, placement.getCurrentModuleInstanceNum());
		}

		private static Map<Integer, List<String>> copyLists(
			Map<Integer, List<String>> source) {
			Map<Integer, List<String>> copy = new HashMap<Integer, List<String>>();
			for (Map.Entry<Integer, List<String>> entry : source.entrySet()) {
				copy.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
			}
			return copy;
		}

		private static <T> Map<Integer, Map<String, T>> copyNestedMaps(
			Map<Integer, Map<String, T>> source) {
			Map<Integer, Map<String, T>> copy =
				new HashMap<Integer, Map<String, T>>();
			for (Map.Entry<Integer, Map<String, T>> entry : source.entrySet()) {
				copy.put(entry.getKey(), new HashMap<String, T>(entry.getValue()));
			}
			return copy;
		}
	}
}
