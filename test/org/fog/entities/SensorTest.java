package org.fog.entities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Calendar;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.utils.distribution.DeterministicDistribution;
import org.junit.Before;
import org.junit.Test;

public class SensorTest {

	@Before
	public void initializeCloudSim() {
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@Test
	public void allConstructorsInitializeSensorNameFromTupleType() {
		Sensor detailed = new Sensor("detailed-sensor", 1, "app", 0, 1.0, null,
			new DeterministicDistribution(5.0), 100, 100, "TEMPERATURE", "filter");
		Sensor simple = new Sensor("simple-sensor", 1, "app", 0, 1.0, null,
			new DeterministicDistribution(5.0), "PRESSURE");

		assertEquals("TEMPERATURE", detailed.getSensorName());
		assertEquals("PRESSURE", simple.getSensorName());
	}

	@Test
	public void submittingApplicationWithoutSensorEdgeFailsClearly() {
		Sensor sensor = new Sensor("orphan-sensor", "TEMPERATURE", 1, "app",
			new DeterministicDistribution(5.0));
		Application application = Application.createApplication("app", 1);

		try {
			sensor.setApp(application);
			fail("Expected a missing sensor edge to be rejected");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("does not define an edge"));
			assertTrue(expected.getMessage().contains("TEMPERATURE"));
		}
	}

	@Test
	public void configuredDestinationDisambiguatesSensorEdges() {
		Sensor sensor = new Sensor("routed-sensor", 1, "app", 0, 1.0, null,
			new DeterministicDistribution(5.0), 100, 100, "TEMPERATURE", "filter-b");
		Application application = Application.createApplication("app", 1);
		application.addAppEdge("TEMPERATURE", "filter-a", 100, 100, "A",
			Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("TEMPERATURE", "filter-b", 100, 100, "B",
			Tuple.UP, AppEdge.SENSOR);

		sensor.setApp(application);

		assertEquals(application, sensor.getApp());
	}

	@Test
	public void constructorRejectsMissingTransmitDistribution() {
		try {
			new Sensor("invalid-sensor", "TEMPERATURE", 1, "app", null);
			fail("Expected a missing distribution to be rejected");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("distribution"));
		}
	}
}
