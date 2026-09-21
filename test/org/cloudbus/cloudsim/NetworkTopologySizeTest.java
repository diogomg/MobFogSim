package org.cloudbus.cloudsim;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkTopologySizeTest {

	@Before
	@After
	public void resetTopology() {
		NetworkTopology.reset();
	}

	@Test
	public void emptyTopologyHasNoNodesOrLinks() {
		assertEquals(0, NetworkTopology.getNumberOfNodes());
		assertEquals(0, NetworkTopology.getNumberOfLinks());
	}

	@Test
	public void sizeReflectsRegisteredNodesAndPhysicalLinks() {
		NetworkTopology.addLink(10, 20, 1000.0, 4.0);
		NetworkTopology.addLink(20, 30, 1000.0, 4.0);
		NetworkTopology.addLink(10, 30, 1000.0, 8.0);

		assertEquals(3, NetworkTopology.getNumberOfNodes());
		assertEquals(3, NetworkTopology.getNumberOfLinks());
		assertEquals(6L, NetworkTopology.getDirectedLinkCountAmong(
			Arrays.asList(10, 20, 30)));
	}

	@Test
	public void directLinkIndexDoesNotConfuseRoutesWithSelfOrMissingLinks() {
		NetworkTopology.addLink(10, 20, 0.0, 4.0);
		NetworkTopology.addLink(20, 30, 1000.0, 4.0);

		assertTrue(NetworkTopology.hasDirectLink(10, 20));
		assertTrue(NetworkTopology.hasDirectLink(20, 10));
		assertFalse(NetworkTopology.hasDirectLink(10, 10));
		assertFalse(NetworkTopology.hasDirectLink(10, 30));
	}

	@Test
	public void largeEntityIdsUseTheBoundedLookupFallback() {
		NetworkTopology.addLink(1000001, 2000002, 1000.0, 4.0);

		assertTrue(NetworkTopology.hasDirectLink(1000001, 2000002));
		assertTrue(NetworkTopology.hasDirectLink(2000002, 1000001));
	}
}
