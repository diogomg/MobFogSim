package org.fog.entities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.placement.MobileController;
import org.fog.utils.FogEvents;
import org.fog.utils.TimeKeeper;
import org.junit.Before;
import org.junit.Test;

public class ActuatorTest {

	private static final int STOP = 8701;
	private static final double DELTA = 0.0000001;
	private TimeKeeper timeKeeper;

	@Before
	public void resetSimulationState() {
		Log.disable();
		CloudSim.init(0, Calendar.getInstance(), false);
		timeKeeper = TimeKeeper.getInstance();
		timeKeeper.getEmitTimes().clear();
		timeKeeper.getLoopIdToCurrentAverage().clear();
		timeKeeper.getLoopIdToCurrentNum().clear();
		timeKeeper.getMaxLoopExecutionTime().clear();
		MobileController.setSmartThings(null);
	}

	@Test
	public void genericFogDeviceGatewayStillRecordsLoopCompletion() throws IOException {
		Application application = Application.createApplication("generic-app", 1);
		AppLoop loop = new AppLoop(Arrays.asList("processor", "display"));
		application.setLoops(Arrays.asList(loop));

		Actuator actuator = new Actuator("display-actuator", 1,
			application.getAppId(), "display");
		actuator.setApp(application);

		Tuple tuple = tuple(901, "processor", "display");
		int actualTupleId = timeKeeper.getUniqueId();
		tuple.setActualTupleId(actualTupleId);
		GenericFogGateway gateway = new GenericFogGateway("generic-gateway",
			actuator.getId(), tuple, actualTupleId);
		actuator.setGatewayDeviceId(gateway.getId());

		try {
			CloudSim.startSimulation();

			assertEquals(10.0,
				timeKeeper.getLoopIdToCurrentAverage().get(loop.getLoopId()), DELTA);
			assertEquals(Integer.valueOf(1),
				timeKeeper.getLoopIdToCurrentNum().get(loop.getLoopId()));
			assertFalse(timeKeeper.getEmitTimes().containsKey(actualTupleId));
		} finally {
			Files.deleteIfExists(Paths.get(loop.getLoopId() + "LoopId.txt"));
			Files.deleteIfExists(Paths.get(loop.getLoopId() + "LoopMaxId.txt"));
		}
	}

	private static Tuple tuple(int id, String source, String destination) {
		Tuple tuple = new Tuple("generic-app", id, Tuple.DOWN, 100, 1, 100, 100,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
		tuple.setSrcModuleName(source);
		tuple.setDestModuleName(destination);
		tuple.setTupleType("DISPLAY_UPDATE");
		return tuple;
	}

	private static final class GenericFogGateway extends FogDevice {
		private final int actuatorId;
		private final Tuple tuple;
		private final int actualTupleId;

		private GenericFogGateway(String name, int actuatorId, Tuple tuple,
			int actualTupleId) {
			super(name, 0, 0, 0);
			this.actuatorId = actuatorId;
			this.tuple = tuple;
			this.actualTupleId = actualTupleId;
		}

		@Override
		public void startEntity() {
			TimeKeeper.getInstance().getEmitTimes().put(actualTupleId, CloudSim.clock());
			schedule(actuatorId, 10.0, FogEvents.TUPLE_ARRIVAL, tuple);
			schedule(getId(), 11.0, STOP);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == STOP) {
				CloudSim.terminateSimulation();
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}
}
