package org.fog.entities;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApDeviceContractTest {

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@After
	public void tearDown() {
		Log.enable();
	}

	@Test
	public void stringRepresentationHandlesUnassignedServer() {
		ApDevice accessPoint = new ApDevice("access-point", 0, 0, 1);

		assertEquals("access-point [serverCloudlet=unassigned]",
			accessPoint.toString());
	}

	@Test
	public void stringRepresentationIncludesAssignedServerName() {
		ApDevice accessPoint = new ApDevice("access-point", 0, 0, 1);
		accessPoint.setServerCloudlet(new FogDevice("server", 0, 0, 2));

		assertEquals("access-point [serverCloudlet=server]",
			accessPoint.toString());
	}
}
