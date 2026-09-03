package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.Tuple;
import org.fog.vmmobile.constants.Policies;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccessPointNetworkSlicingTest {

	private static final double DELTA = 0.000001;
	private ApDevice accessPoint;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure("70,30");
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		accessPoint = new ApDevice("accessPoint", 0, 0, 0);
		accessPoint.setUplinkBandwidth(1000.0);
		accessPoint.setDownlinkBandwidth(800.0);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
	}

	@Test
	public void fixedSlicesPartitionBothAccessPointDirections() {
		NetworkSlicing.setDynamicBorrowing(false);
		MobileDevice largeSlice = connect("largeSlice", 0, 900.0, 700.0);
		MobileDevice smallSlice = connect("smallSlice", 1, 900.0, 700.0);

		assertEquals(700.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, largeSlice), DELTA);
		assertEquals(560.0,
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, largeSlice), DELTA);
		assertEquals(300.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, smallSlice), DELTA);
		assertEquals(240.0,
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, smallSlice), DELTA);
	}

	@Test
	public void mobileLinkCapabilityCapsItsAccessPointShare() {
		NetworkSlicing.setDynamicBorrowing(false);
		MobileDevice mobile = connect("mobile", 0, 400.0, 300.0);

		assertEquals(400.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, mobile), DELTA);
		assertEquals(300.0,
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, mobile), DELTA);
	}

	@Test
	public void loneConnectedSliceBorrowsIdleAccessPointCapacity() {
		MobileDevice mobile = connect("mobile", 1, 2000.0, 2000.0);

		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, mobile), DELTA);
		assertEquals(800.0,
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, mobile), DELTA);
	}

	@Test
	public void associatedUsersDoNotConsumeDynamicCapacityWithoutAFlow() {
		MobileDevice largeSlice = connect("largeSlice", 0, 2000.0, 2000.0);
		MobileDevice smallSlice = connect("smallSlice", 1, 2000.0, 2000.0);

		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, largeSlice), DELTA);
		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, smallSlice), DELTA);
	}

	@Test
	public void prospectiveRateIsNotPreDividedByAssociatedUsers() {
		MobileDevice first = connect("first", 0, 2000.0, 2000.0);
		MobileDevice second = connect("second", 0, 2000.0, 2000.0);

		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, first), DELTA);
		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, second), DELTA);
		assertEquals(800.0,
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, first), DELTA);
	}

	@Test
	public void dynamicProspectiveRateCanUseTheWholeAccessPoint() {
		NetworkSlicing.configure("50,30,20");
		MobileDevice first = connect("first", 0, 2000.0, 2000.0);
		MobileDevice second = connect("second", 1, 2000.0, 2000.0);

		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, first), DELTA);
		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, second), DELTA);
	}

	@Test
	public void prospectiveConnectionParticipatesInItsSlice() {
		MobileDevice mobile = mobile("prospective", 1, 2000.0, 2000.0);

		assertEquals(1000.0,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, mobile), DELTA);
	}

	@Test
	public void tupleLinksUseTheWirelessSliceBandwidth() {
		NetworkSlicing.setDynamicBorrowing(false);
		ExposedMobileDevice mobile = exposedMobile("mobile", 0, 2000.0, 2000.0);
		mobile.setSourceAp(accessPoint);
		accessPoint.setSmartThings(mobile, Policies.ADD);
		ExposedFogDevice server = new ExposedFogDevice("server");
		server.setDownlinkBandwidth(5000.0);

		assertEquals(700.0, mobile.effectiveUplinkBandwidth(), DELTA);
		assertEquals(560.0, server.effectiveDownlinkBandwidth(mobile.getId()), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void wirelessSliceRequiresAnAccessPoint() {
		NetworkSlicing.getAccessPointUplinkBandwidth(null,
			mobile("mobile", 0, 1000.0, 1000.0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void wirelessSliceRequiresAMobileDevice() {
		NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, null);
	}

	private MobileDevice connect(String name, int sliceId, double uplink,
		double downlink) {
		MobileDevice mobile = mobile(name, sliceId, uplink, downlink);
		mobile.setSourceAp(accessPoint);
		accessPoint.setSmartThings(mobile, Policies.ADD);
		return mobile;
	}

	private static MobileDevice mobile(String name, int sliceId, double uplink,
		double downlink) {
		MobileDevice mobile = new MobileDevice(name, 0, 0, 0, 0, 0);
		mobile.setNetworkSliceId(sliceId);
		mobile.setUplinkBandwidth(uplink);
		mobile.setDownlinkBandwidth(downlink);
		return mobile;
	}

	private static ExposedMobileDevice exposedMobile(String name, int sliceId,
		double uplink, double downlink) {
		ExposedMobileDevice mobile = new ExposedMobileDevice(name);
		mobile.setNetworkSliceId(sliceId);
		mobile.setUplinkBandwidth(uplink);
		mobile.setDownlinkBandwidth(downlink);
		return mobile;
	}

	private static final class ExposedMobileDevice extends MobileDevice {
		private ExposedMobileDevice(String name) {
			super(name, 0, 0, 0, 0, 0);
		}

		private double effectiveUplinkBandwidth() {
			return getUplinkBandwidthForTuple(null);
		}
	}

	private static final class ExposedFogDevice extends FogDevice {
		private ExposedFogDevice(String name) {
			super(name, 0, 0, 0);
		}

		private double effectiveDownlinkBandwidth(int childId) {
			return getDownlinkBandwidthForTuple((Tuple) null, childId);
		}
	}
}
