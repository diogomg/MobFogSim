package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.constants.Policies;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkSlicingScopeTest {

	private static final double DELTA = 0.000001;
	private FogDevice source;
	private FogDevice destination;
	private ApDevice accessPoint;
	private MobileDevice largeSlice;
	private MobileDevice smallSlice;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure("70,30");
		NetworkSlicing.setDynamicBorrowing(false);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);

		source = cloudlet("source", 1000.0, 1000.0);
		destination = cloudlet("destination", 1000.0, 800.0);
		accessPoint = new ApDevice("accessPoint", 0, 0, 0);
		accessPoint.setUplinkBandwidth(1000.0);
		accessPoint.setDownlinkBandwidth(800.0);
		largeSlice = connect("largeSlice", 0);
		smallSlice = connect("smallSlice", 1);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
	}

	@Test
	public void transportScopeSlicesOnlyServerLinks() {
		NetworkSlicing.setScope(NetworkSlicing.TRANSPORT_NETWORK);

		assertEquals(560.0,
			NetworkSlicing.getSliceBandwidth(source, destination, 0), DELTA);
		assertEquals(240.0,
			NetworkSlicing.getSliceBandwidth(source, destination, 1), DELTA);
		assertEquals(500.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, largeSlice), DELTA);
		assertEquals(500.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, smallSlice), DELTA);
	}

	@Test
	public void wirelessScopeSlicesOnlyAccessPointLinks() {
		NetworkSlicing.setScope(NetworkSlicing.WIRELESS_NETWORK);

		assertEquals(800.0,
			NetworkSlicing.getSliceBandwidth(source, destination, 0), DELTA);
		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, largeSlice), DELTA);
		assertEquals(700.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, largeSlice), DELTA);
		assertEquals(300.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, smallSlice), DELTA);
	}

	@Test
	public void endToEndScopeSlicesBothNetworks() {
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);

		assertEquals(560.0,
			NetworkSlicing.getSliceBandwidth(source, destination, 0), DELTA);
		assertEquals(700.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, largeSlice), DELTA);
	}

	@Test
	public void scopeFlagsDescribeTheSelectedNetworks() {
		NetworkSlicing.setScope(NetworkSlicing.TRANSPORT_NETWORK);
		assertTrue(NetworkSlicing.coversTransportNetwork());
		assertFalse(NetworkSlicing.coversWirelessNetwork());

		NetworkSlicing.setScope(NetworkSlicing.WIRELESS_NETWORK);
		assertFalse(NetworkSlicing.coversTransportNetwork());
		assertTrue(NetworkSlicing.coversWirelessNetwork());

		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		assertTrue(NetworkSlicing.coversTransportNetwork());
		assertTrue(NetworkSlicing.coversWirelessNetwork());
	}

	@Test
	public void changingScopeClearsTransportReservations() {
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.reserveBandwidth(source, destination, largeSlice);
		NetworkSlicing.setScope(NetworkSlicing.WIRELESS_NETWORK);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, smallSlice), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeScope() {
		NetworkSlicing.setScope(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUnknownScope() {
		NetworkSlicing.setScope(3);
	}

	private MobileDevice connect(String name, int sliceId) {
		MobileDevice mobile = new MobileDevice(name, 0, 0, 0, 0, 0);
		mobile.setNetworkSliceId(sliceId);
		mobile.setUplinkBandwidth(2000.0);
		mobile.setDownlinkBandwidth(2000.0);
		mobile.setSourceAp(accessPoint);
		accessPoint.setSmartThings(mobile, Policies.ADD);
		return mobile;
	}

	private static FogDevice cloudlet(String name, double uplink, double downlink) {
		FogDevice device = new FogDevice(name, 0, 0, 0);
		device.setUplinkBandwidth(uplink);
		device.setDownlinkBandwidth(downlink);
		return device;
	}
}
