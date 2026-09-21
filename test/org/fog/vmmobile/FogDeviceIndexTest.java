package org.fog.vmmobile;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Calendar;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.junit.Before;
import org.junit.Test;

public class FogDeviceIndexTest {

	@Before
	public void initialiseCloudSim() {
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@Test
	public void resolvesStableDevicesByIdAndName() {
		FogDevice first = new FogDevice("first", 0, 0, 1);
		FogDevice second = new FogDevice("second", 0, 0, 2);
		FogDeviceIndex index = FogDeviceIndex.copyOf(
			Arrays.asList(first, second));

		assertSame(first, index.getById(first.getId()));
		assertSame(second, index.getByName("second"));
		try {
			index.byId().clear();
			fail("Expected an immutable ID index");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	@Test
	public void duplicateNamesAreRejected() {
		FogDevice first = new FogDevice("duplicate", 0, 0, 1);
		FogDevice second = new FogDevice("duplicate", 0, 0, 2);

		try {
			FogDeviceIndex.copyOf(Arrays.asList(first, second));
			fail("Expected duplicate names to be rejected");
		}
		catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("Duplicate fog device name"));
		}
	}
}
