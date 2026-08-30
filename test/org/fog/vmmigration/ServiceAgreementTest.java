package org.fog.vmmigration;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.Before;
import org.junit.Test;

public class ServiceAgreementTest {

	private FogDevice source;
	private FogDevice destination;
	private MobileDevice mobileDevice;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		source = new FogDevice("source", 0, 0, 0);
		destination = new FogDevice("destination", 0, 0, 1);
		source.getNetServerCloudlets().put(destination, 1000.0);
		destination.setAvailable(true);
		mobileDevice = new MobileDevice("mobile", 0, 0, 0, 0, 0);
		mobileDevice.setVmLocalServerCloudlet(source);
	}

	@Test
	public void unsupportedServiceTypeRaisesADescriptiveException() {
		Service service = new Service();
		service.setType(999);
		destination.setService(service);

		try {
			ServiceAgreement.serviceAgreement(destination, mobileDevice);
			fail("Expected an unsupported service type to fail");
		} catch (IllegalStateException expected) {
			assertTrue(expected.getMessage().contains("999"));
			assertTrue(expected.getMessage().contains("destination"));
		}
	}
}
