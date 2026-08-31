package org.fog.localization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Optional;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.Migration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DistancesTest {

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure(null);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
	}

	@After
	public void resetMigrationState() {
		Migration.setApsAvailable(null);
		Migration.setServerCloudletsAvailable(null);
	}

	@Test
	public void closestAccessPointReturnsTheDeviceRatherThanItsSparseId() {
		ApDevice farther = accessPoint("farther", 500, 0, 701);
		ApDevice closest = accessPoint("closest", 10, 0, 913);
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 1201);

		Optional<ApDevice> result = Distances.findClosestAp(
			Arrays.asList(farther, closest), mobileDevice);

		assertTrue(result.isPresent());
		assertSame(closest, result.get());
	}

	@Test
	public void accessPointOutsideCoverageReturnsEmpty() {
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 41);
		ApDevice accessPoint = accessPoint("outside", 0, 1001, 82);

		assertFalse(Distances.findClosestAp(
			Collections.singletonList(accessPoint), mobileDevice).isPresent());
	}

	@Test
	public void closestCloudletReturnsTheDeviceFromAReorderedList() {
		FogDevice farther = fogDevice("farther", 800, 0, 900);
		FogDevice closest = fogDevice("closest", 100, 0, 450);
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 73);
		mobileDevice.setFutureCoord(90, 0);

		Optional<FogDevice> result = Distances.findClosestServerCloudlet(
			Arrays.asList(farther, closest), mobileDevice);

		assertTrue(result.isPresent());
		assertSame(closest, result.get());
	}

	@Test
	public void closestCloudletToAccessPointDoesNotUseItsIdAsAnIndex() {
		FogDevice farther = fogDevice("farther", 900, 0, 300);
		FogDevice closest = fogDevice("closest", 20, 0, 800);
		ApDevice accessPoint = accessPoint("accessPoint", 0, 0, 1200);

		Optional<FogDevice> result = Distances.findClosestServerCloudletToAp(
			Arrays.asList(farther, closest), accessPoint);

		assertTrue(result.isPresent());
		assertSame(closest, result.get());
	}

	@Test
	public void emptyCandidateListsReturnEmptySelections() {
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 7);
		ApDevice accessPoint = accessPoint("accessPoint", 0, 0, 11);

		assertFalse(Distances.findClosestAp(
			Collections.<ApDevice>emptyList(), mobileDevice).isPresent());
		assertFalse(Distances.findClosestServerCloudlet(
			Collections.<FogDevice>emptyList(), mobileDevice).isPresent());
		assertFalse(Distances.findClosestServerCloudletToAp(
			Collections.<FogDevice>emptyList(), accessPoint).isPresent());
	}

	@Test
	public void accessPointConnectionUsesTheSelectedDeviceReference() {
		ApDevice farther = accessPoint("farther", 700, 0, 500);
		ApDevice closest = accessPoint("closest", 10, 0, 900);
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 1300);

		assertTrue(ApDevice.connectApSmartThing(
			Arrays.asList(farther, closest), mobileDevice, 0.25));
		assertSame(closest, mobileDevice.getSourceAp());
		assertTrue(closest.getSmartThings().contains(mobileDevice));
	}

	@Test
	public void nextAccessPointReturnsAReferenceFromTheFilteredCandidates() {
		ApDevice source = accessPoint("source", 0, 0, 600);
		ApDevice farther = accessPoint("farther", 500, 0, 800);
		ApDevice closest = accessPoint("closest", 10, 0, 1000);
		MobileDevice mobileDevice = mobileDevice("mobile", 0, 0, 1400);
		mobileDevice.setSourceAp(source);

		Optional<ApDevice> result = Migration.nextAp(
			Arrays.asList(farther, source, closest), mobileDevice);

		assertTrue(result.isPresent());
		assertSame(closest, result.get());
	}

	private static ApDevice accessPoint(String name, int x, int y, int id) {
		ApDevice accessPoint = new ApDevice(name, x, y, id);
		accessPoint.setMaxSmartThing(10);
		accessPoint.setUplinkBandwidth(1000.0);
		accessPoint.setDownlinkBandwidth(1000.0);
		return accessPoint;
	}

	private static FogDevice fogDevice(String name, int x, int y, int id) {
		return new FogDevice(name, x, y, id);
	}

	private static MobileDevice mobileDevice(String name, int x, int y, int id) {
		MobileDevice mobileDevice = new MobileDevice(name, x, y, id, 0, 0);
		mobileDevice.setUplinkBandwidth(1000.0);
		mobileDevice.setDownlinkBandwidth(1000.0);
		return mobileDevice;
	}
}
