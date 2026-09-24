package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.MobilitySample;
import org.fog.vmmobile.AppExample;
import org.fog.vmmobile.constants.Directions;
import org.junit.Test;

public class MigrationUtilityTest {
	private static final int RUN_PREDICTION = 8201;

	@Test
	public void insideConeHandlesDirectionWraparound() {
		assertTrue(Migration.insideCone(Directions.EAST, Directions.SOUTHEAST));
		assertTrue(Migration.insideCone(Directions.EAST, Directions.EAST));
		assertTrue(Migration.insideCone(Directions.EAST, Directions.NORTHEAST));
		assertFalse(Migration.insideCone(Directions.EAST, Directions.WEST));

		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.SOUTH));
		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.SOUTHEAST));
		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.EAST));
		assertFalse(Migration.insideCone(Directions.SOUTHEAST, Directions.NORTH));
	}

	@Test
	public void insideConeHandlesNonBoundaryDirections() {
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTHEAST));
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTH));
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTHWEST));
		assertFalse(Migration.insideCone(Directions.NORTH, Directions.SOUTH));
	}

	@Test
	public void serviceValueObjectRoundTrips() {
		Service service = new Service();
		service.setType(2);
		service.setValue(1.25f);

		assertEquals(2, service.getType());
		assertEquals(1.25f, service.getValue(), 0.0f);
	}

	@Test
	public void serviceAgreementStateRoundTrips() {
		ServiceAgreement.setServiceType(2);
		ServiceAgreement.setServiceValue(3.5f);

		assertEquals(2, ServiceAgreement.getServiceType());
		assertEquals(3.5f, ServiceAgreement.getServiceValue(), 0.0f);
	}

	@Test
	public void sixtySecondPredictionUsesIrregularTraceTimestamps() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		RunOutputManager.initialize(
			Paths.get("build", "test-output", "migration-utility"),
			RunOutputMode.NONE);
		AppExample.setRand(new Random(1));

		MobileDevice user = new MobileDevice("irregular-trace-user", 20, 20,
			0, Directions.EAST, 0);
		user.setMobilityPath(Arrays.asList(
			new MobilitySample(2.0, 0.0, 10.0, 11.0, 1.0),
			new MobilitySample(5.0, 0.0, 20.0, 21.0, 1.0),
			new MobilitySample(17.5, 0.0, 30.0, 31.0, 1.0),
			new MobilitySample(64.0, 0.0, 40.0, 41.0, 1.0),
			new MobilitySample(65.0, 0.0, 50.0, 51.0, 1.0),
			new MobilitySample(65.0, 0.0, 55.0, 56.0, 1.0),
			new MobilitySample(83.25, 0.0, 60.0, 61.0, 1.0)));
		user.setTravelTimeId(2);
		user.setTravelPredictionTime(60);
		user.setMobilityPredictionError(0);

		PredictionProbe probe = new PredictionProbe("prediction-probe", user);
		double finalClock = CloudSim.startSimulation();

		assertTrue(probe.wasInvoked());
		assertEquals(5000.0, finalClock, 0.0);
		assertEquals(55, user.getFutureCoord().getCoordX());
		assertEquals(56, user.getFutureCoord().getCoordY());
	}

	private static final class PredictionProbe extends SimEntity {
		private final MobileDevice user;
		private boolean invoked;

		private PredictionProbe(String name, MobileDevice user) {
			super(name);
			this.user = user;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 5000.0, RUN_PREDICTION);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == RUN_PREDICTION) {
				Migration.serverCloudletsAvailableList(
					Collections.<FogDevice>emptyList(), user);
				invoked = true;
				CloudSim.terminateSimulation();
			}
		}

		@Override
		public void shutdownEntity() {
		}

		private boolean wasInvoked() {
			return invoked;
		}
	}
}
