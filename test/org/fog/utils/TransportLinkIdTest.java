package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class TransportLinkIdTest {

	@Test
	public void directedEndpointsDefineIdentity() {
		TransportLinkId forward = TransportLinkId.directed(10, 20);
		TransportLinkId same = TransportLinkId.directed(10, 20);
		TransportLinkId reverse = TransportLinkId.directed(20, 10);

		assertEquals(forward, same);
		assertEquals(forward.hashCode(), same.hashCode());
		assertNotEquals(forward, reverse);
		assertEquals("10->20", forward.toString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void selfLinksAreRejected() {
		TransportLinkId.directed(10, 10);
	}
}
