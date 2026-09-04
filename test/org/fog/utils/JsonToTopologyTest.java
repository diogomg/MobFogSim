package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.PhysicalTopology;
import org.fog.entities.Sensor;
import org.junit.Before;
import org.junit.Test;

public class JsonToTopologyTest {

	@Before
	public void initialiseCloudSim() {
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void repositoryTopologyLoadsAsACompleteConnectedModel()
		throws Exception {
		PhysicalTopology topology = JsonToTopology.getPhysicalTopology(7,
			"router-app", "src/topologies/routerTopology");

		assertEquals(4, topology.getFogDevices().size());
		assertEquals(2, topology.getSensors().size());
		assertEquals(2, topology.getActuators().size());
		for (Sensor sensor : topology.getSensors()) {
			assertTrue(sensor.getGatewayDeviceId() >= 0);
		}
		for (Actuator actuator : topology.getActuators()) {
			assertTrue(actuator.getGatewayDeviceId() >= 0);
		}
		int roots = 0;
		for (FogDevice device : topology.getFogDevices()) {
			if (device.getParentId() < 0) {
				roots++;
			}
		}
		assertEquals(1, roots);
	}
}
