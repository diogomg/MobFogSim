package org.fog.entities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.utils.FogEntityFactory;
import org.junit.Before;
import org.junit.Test;

public class FogDeviceStateHistoryTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void fogDevicesDisableStateHistoryRecordingByDefault() {
		FogDevice device = FogEntityFactory.createFogDevice(
			"fog", 1000, 10000, 10000, 0.0, 0.0);

		assertFalse(device.getHost().isStateHistoryRecordingEnabled());

		device.setStateHistoryRecordingEnabled(true);

		assertTrue(device.getHost().isStateHistoryRecordingEnabled());
	}
}
