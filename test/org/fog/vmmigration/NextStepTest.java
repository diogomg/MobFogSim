package org.fog.vmmigration;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.placement.MobileController;
import org.fog.vmmobile.constants.Directions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NextStepTest {

	@Before
	public void initializeCloudSim() {
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@After
	public void clearMobileDevices() {
		MobileController.setSmartThings(new ArrayList<MobileDevice>());
	}

	@Test
	public void consecutiveInactiveDevicesAreAllRemoved() {
		List<MobileDevice> devices = new ArrayList<MobileDevice>();
		devices.add(inactiveDevice("inactive-one", 1));
		devices.add(inactiveDevice("inactive-two", 2));
		MobileController.setSmartThings(devices);

		NextStep.nextStep(Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), devices, new Coordinate(), 0, 0);

		assertTrue(devices.isEmpty());
		assertTrue(MobileController.getSmartThings().isEmpty());
	}

	private static MobileDevice inactiveDevice(String name, int id) {
		MobileDevice device = new MobileDevice(name, new Coordinate(), 0, 0, id);
		device.setTravelTimeId(0);
		device.setDirection(Directions.NONE);
		device.setCoord(-1, -1);
		return device;
	}
}
