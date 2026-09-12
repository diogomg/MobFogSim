package org.cloudbus.cloudsim;

import static org.junit.Assert.assertEquals;

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
	}
}
