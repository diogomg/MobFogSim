package org.fog.vmmigration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class VmDestinationPolicyTest {

	@After
	public void restoreDefaultPolicy() {
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);
	}

	@Test
	public void fogOnlyAllowsOnlyEdgeServers() {
		VmDestinationPolicy.configure(VmDestinationPolicy.EDGE_SERVERS_ONLY);

		assertTrue(VmDestinationPolicy.allowsEdgeServers());
		assertFalse(VmDestinationPolicy.allowsEndDevices());
	}

	@Test
	public void deviceOnlyAllowsOnlyEndDevices() {
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertFalse(VmDestinationPolicy.allowsEdgeServers());
		assertTrue(VmDestinationPolicy.allowsEndDevices());
	}

	@Test
	public void hybridAllowsBothDestinationTypes() {
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);

		assertTrue(VmDestinationPolicy.allowsEdgeServers());
		assertTrue(VmDestinationPolicy.allowsEndDevices());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsPolicyBelowFogOnly() {
		VmDestinationPolicy.configure(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsPolicyAboveHybrid() {
		VmDestinationPolicy.configure(3);
	}
}

