package org.fog.entities;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogEvents;
import org.fog.utils.FogLinearPowerModel;
import org.junit.Before;
import org.junit.Test;

public class PeriodicTupleLifecycleTest {

	private static final int REMOVE_APPLICATION = 7001;

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void periodicTupleSeriesStopsWhenItsApplicationIsRemoved() {
		RecordingMobileDevice mobile = new RecordingMobileDevice("mobile");
		Application migrating = periodicApplication("migrating-app");
		AppModule module = migrating.getModuleByName("source");
		AppEdge edge = migrating.getPeriodicEdges(module.getName()).get(0);
		mobile.host(module);
		mobile.installApplication(migrating);
		mobile.installApplication(Application.createApplication("other-app", 1));
		mobile.setModuleInstanceCount(instanceCounts(module));
		new ApplicationRemoval("application-removal", mobile,
			migrating.getAppId(), 5.0);

		CloudSim.send(mobile.getId(), mobile.getId(), 0.0,
			FogEvents.SEND_PERIODIC_TUPLE, edge);
		CloudSim.terminateSimulation(25.0);
		CloudSim.startSimulation();

		assertEquals(2, mobile.getPeriodicEventCount());
		assertEquals(1, mobile.getTupleCount());
	}

	private static Application periodicApplication(String appId) {
		Application application = Application.createApplication(appId, 1);
		application.addAppModule("source", 128);
		application.addAppEdge("source", "sink", 10.0, 1.0, 1.0,
			"periodic", Tuple.UP, AppEdge.MODULE);
		return application;
	}

	private static Map<String, Map<String, Integer>> instanceCounts(
		AppModule module) {
		Map<String, Integer> moduleCounts = new HashMap<String, Integer>();
		moduleCounts.put(module.getName(), 1);
		Map<String, Map<String, Integer>> applicationCounts =
			new HashMap<String, Map<String, Integer>>();
		applicationCounts.put(module.getAppId(), moduleCounts);
		return applicationCounts;
	}

	private static PowerHost createHost() {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerOverbooking(2000)));
		return new PowerHost(0, new RamProvisionerSimple(4096),
			new BwProvisionerOverbooking(10000), 100000,
			processingElements, new StreamOperatorScheduler(processingElements),
			new FogLinearPowerModel(100.0, 50.0));
	}

	private static final class RecordingMobileDevice extends MobileDevice {
		private final PowerHost host = createHost();
		private int periodicEventCount;
		private int tupleCount;

		private RecordingMobileDevice(String name) {
			super(name, 0, 0, 0, 0, 0);
		}

		private void host(AppModule module) {
			host.getVmList().add(module);
		}

		@Override
		public PowerHost getHost() {
			return host;
		}

		@Override
		protected void processOtherEvent(SimEvent event) {
			if (event.getTag() == FogEvents.SEND_PERIODIC_TUPLE) {
				periodicEventCount++;
			}
			else if (event.getTag() == FogEvents.TUPLE_ARRIVAL) {
				tupleCount++;
				return;
			}
			super.processOtherEvent(event);
		}

		private int getPeriodicEventCount() {
			return periodicEventCount;
		}

		private int getTupleCount() {
			return tupleCount;
		}
	}

	private static final class ApplicationRemoval extends SimEntity {
		private final FogDevice device;
		private final String applicationId;
		private final double delay;

		private ApplicationRemoval(String name, FogDevice device,
			String applicationId, double delay) {
			super(name);
			this.device = device;
			this.applicationId = applicationId;
			this.delay = delay;
		}

		@Override
		public void startEntity() {
			schedule(getId(), delay, REMOVE_APPLICATION);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == REMOVE_APPLICATION) {
				device.removeApplication(applicationId);
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}
}
