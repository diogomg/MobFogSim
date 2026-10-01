package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppExampleMultipleApplicationsTest {

	@Test
	public void applicationCountsAreConfiguredInTheExampleSources() {
		assertEquals(1, AppExample.APPLICATIONS_PER_USER);
		assertEquals(2,
			AppExampleMultipleApplications.APPLICATIONS_PER_USER);
		assertEquals("latency-critical",
			AppExampleMultipleApplications.APPLICATION_PROFILES.get(0).getName());
		assertEquals("traffic-intensive",
			AppExampleMultipleApplications.APPLICATION_PROFILES.get(1).getName());
		assertEquals(8192.0,
			AppExampleMultipleApplications.APPLICATION_PROFILES.get(1)
				.getUplinkTupleBytes(), 0.0);
		assertTrue(AppExampleMultipleApplications.APPLICATION_PROFILES.get(0)
			.usesLowestLatencyMigration());
	}

	@Test
	public void preservesLegacyNamesAndNamespacesAdditionalApplications() {
		MobileApplicationDescriptor first =
			MobileApplicationDescriptor.forUser(7, "SmartThing7", 0);
		MobileApplicationDescriptor second =
			MobileApplicationDescriptor.forUser(7, "SmartThing7", 1);

		assertEquals("MyApp_vr_game7", first.getApplicationId());
		assertEquals("AppModuleVm_SmartThing7", first.getVmName());
		assertEquals("EEG7", first.getSensorTupleType());
		assertEquals("MyApp_vr_game7_app1", second.getApplicationId());
		assertEquals("AppModuleVm_SmartThing7_app1", second.getVmName());
		assertEquals("EEG7_app1", second.getSensorTupleType());
	}
}
