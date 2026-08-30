package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DynamicNetworkSlicingTest {

	private static final double DELTA = 0.000001;
	private FogDevice source;
	private FogDevice destination;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure("70,30");
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		source = cloudlet("source", 1000.0, 1000.0);
		destination = cloudlet("destination", 1000.0, 800.0);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
	}

	@Test
	public void fixedModeReturnsOnlyTheReservedSlice() {
		NetworkSlicing.setDynamicBorrowing(false);

		assertEquals(560.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("mobile0", 0)), DELTA);
		assertEquals(240.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("mobile1", 1)), DELTA);
	}

	@Test
	public void fixedModeSharesASliceBetweenConcurrentMigrations() {
		NetworkSlicing.setDynamicBorrowing(false);
		MobileDevice first = mobile("first", 0);
		MobileDevice second = mobile("second", 0);

		assertEquals(560.0,
			NetworkSlicing.reserveBandwidth(source, destination, first), DELTA);
		assertEquals(280.0,
			NetworkSlicing.reserveBandwidth(source, destination, second), DELTA);
		assertEquals(280.0,
			NetworkSlicing.reserveBandwidth(source, destination, first), DELTA);
	}

	@Test
	public void loneActiveSliceBorrowsAllIdleCapacity() {
		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("mobile", 0)), DELTA);
	}

	@Test
	public void activeSlicesReturnToTheirConfiguredShares() {
		MobileDevice largeSlice = mobile("largeSlice", 0);
		MobileDevice smallSlice = mobile("smallSlice", 1);

		NetworkSlicing.reserveBandwidth(source, destination, largeSlice);
		assertEquals(240.0,
			NetworkSlicing.reserveBandwidth(source, destination, smallSlice), DELTA);
		assertEquals(560.0,
			NetworkSlicing.reserveBandwidth(source, destination, largeSlice), DELTA);
	}

	@Test
	public void concurrentMigrationsInOneSliceShareItsCapacity() {
		MobileDevice first = mobile("first", 0);
		MobileDevice second = mobile("second", 0);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, first), DELTA);
		assertEquals(400.0,
			NetworkSlicing.reserveBandwidth(source, destination, second), DELTA);
		assertEquals(400.0,
			NetworkSlicing.reserveBandwidth(source, destination, first), DELTA);
	}

	@Test
	public void idleCapacityIsSharedEquallyBetweenActiveSlices() {
		NetworkSlicing.configure("50,30,20");
		MobileDevice first = mobile("first", 0);
		MobileDevice second = mobile("second", 1);

		NetworkSlicing.reserveBandwidth(source, destination, first);
		assertEquals(320.0,
			NetworkSlicing.reserveBandwidth(source, destination, second), DELTA);
		assertEquals(480.0,
			NetworkSlicing.reserveBandwidth(source, destination, first), DELTA);
	}

	@Test
	public void releaseMakesIdleCapacityBorrowableAgain() {
		MobileDevice largeSlice = mobile("largeSlice", 0);
		MobileDevice smallSlice = mobile("smallSlice", 1);

		NetworkSlicing.reserveBandwidth(source, destination, largeSlice);
		NetworkSlicing.reserveBandwidth(source, destination, smallSlice);
		NetworkSlicing.releaseBandwidth(smallSlice);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, largeSlice), DELTA);
	}

	@Test
	public void releasingUnknownMigrationIsHarmless() {
		NetworkSlicing.releaseBandwidth(mobile("neverReserved", 0));

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("active", 0)), DELTA);
	}

	@Test
	public void linksHaveIndependentReservations() {
		FogDevice otherDestination = cloudlet("otherDestination", 1000.0, 600.0);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("first", 0)), DELTA);
		assertEquals(600.0,
			NetworkSlicing.reserveBandwidth(source, otherDestination, mobile("second", 1)), DELTA);
	}

	@Test
	public void directedLinksHaveIndependentReservations() {
		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("forward", 0)), DELTA);
		assertEquals(1000.0,
			NetworkSlicing.reserveBandwidth(destination, source, mobile("reverse", 1)), DELTA);
	}

	@Test
	public void movingReservationReleasesThePreviousLink() {
		FogDevice otherDestination = cloudlet("otherDestination", 1000.0, 600.0);
		MobileDevice moving = mobile("moving", 0);

		NetworkSlicing.reserveBandwidth(source, destination, moving);
		NetworkSlicing.reserveBandwidth(source, otherDestination, moving);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("replacement", 1)), DELTA);
	}

	@Test
	public void reconfigurationClearsActiveReservations() {
		NetworkSlicing.reserveBandwidth(source, destination, mobile("old", 0));
		NetworkSlicing.configure("50,50");

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("new", 1)), DELTA);
	}

	@Test
	public void changingDynamicModeClearsActiveReservations() {
		NetworkSlicing.reserveBandwidth(source, destination, mobile("old", 0));
		NetworkSlicing.setDynamicBorrowing(false);
		NetworkSlicing.setDynamicBorrowing(true);

		assertEquals(800.0,
			NetworkSlicing.reserveBandwidth(source, destination, mobile("new", 1)), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void dynamicReservationRequiresSourceCloudlet() {
		NetworkSlicing.reserveBandwidth(null, destination, mobile("mobile", 0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void dynamicReservationRequiresDestinationCloudlet() {
		NetworkSlicing.reserveBandwidth(source, null, mobile("mobile", 0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void reservationRejectsUnknownMobileSlice() {
		NetworkSlicing.reserveBandwidth(source, destination, mobile("mobile", 2));
	}

	private static FogDevice cloudlet(String name, double uplink, double downlink) {
		FogDevice device = new FogDevice(name, 0, 0, 0);
		device.setUplinkBandwidth(uplink);
		device.setDownlinkBandwidth(downlink);
		return device;
	}

	private static MobileDevice mobile(String name, int sliceId) {
		MobileDevice mobile = new MobileDevice(name, 0, 0, 0, 0, 0);
		mobile.setNetworkSliceId(sliceId);
		return mobile;
	}
}
