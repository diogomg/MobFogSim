package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.vmmigration.ServiceAgreement;
import org.junit.Before;
import org.junit.Test;

public class ServerCloudletNetworkTest {

	private static final double DELTA = 0.000001;
	private final TopologyService topologyService = new TopologyService();

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void everyCloudletOwnsACompleteIndependentAdjacencyMap() {
		FogDevice first = cloudlet("first", 100.0, 700.0);
		FogDevice second = cloudlet("second", 200.0, 50.0);
		FogDevice third = cloudlet("third", 300.0, 250.0);
		List<FogDevice> cloudlets = Arrays.asList(first, second, third);

		topologyService.createServerCloudletAdjacency(cloudlets);

		assertNotSame(first.getNetServerCloudlets(), second.getNetServerCloudlets());
		assertNotSame(first.getNetServerCloudlets(), third.getNetServerCloudlets());
		for (FogDevice source : cloudlets) {
			assertEquals(2, source.getNetServerCloudlets().size());
			assertFalse(source.getNetServerCloudlets().containsKey(source));
			for (FogDevice destination : cloudlets) {
				assertEquals(source != destination,
					ServiceAgreement.checkLinkStatus(source, destination));
			}
		}

		assertEquals(50.0, first.getNetServerCloudlets().get(second), DELTA);
		assertEquals(200.0, second.getNetServerCloudlets().get(first), DELTA);
		assertEquals(100.0, first.getNetServerCloudlets().get(third), DELTA);
		assertEquals(300.0, third.getNetServerCloudlets().get(first), DELTA);
	}

	@Test
	public void mutatingOneCloudletsMapDoesNotChangeAnotherCloudlet() {
		FogDevice first = cloudlet("first", 100.0, 100.0);
		FogDevice second = cloudlet("second", 100.0, 100.0);
		FogDevice third = cloudlet("third", 100.0, 100.0);
		topologyService.createServerCloudletAdjacency(
			Arrays.asList(first, second, third));

		first.getNetServerCloudlets().remove(third);

		assertFalse(first.getNetServerCloudlets().containsKey(third));
		assertTrue(second.getNetServerCloudlets().containsKey(third));
		assertTrue(third.getNetServerCloudlets().containsKey(first));
	}

	@Test
	public void fogDeviceDefensivelyCopiesAnAssignedAdjacencyMap() {
		FogDevice first = cloudlet("first", 100.0, 100.0);
		FogDevice second = cloudlet("second", 100.0, 100.0);
		FogDevice destination = cloudlet("destination", 100.0, 100.0);
		HashMap<FogDevice, Double> shared = new HashMap<FogDevice, Double>();
		shared.put(destination, 100.0);

		first.setNetServerCloudlets(shared);
		second.setNetServerCloudlets(shared);
		shared.clear();

		assertNotSame(first.getNetServerCloudlets(), second.getNetServerCloudlets());
		assertTrue(first.getNetServerCloudlets().containsKey(destination));
		assertTrue(second.getNetServerCloudlets().containsKey(destination));
	}

	@Test
	public void oneCloudletHasAnEmptyAdjacencyMap() {
		FogDevice only = cloudlet("only", 100.0, 100.0);

		topologyService.createServerCloudletAdjacency(Arrays.asList(only));

		assertTrue(only.getNetServerCloudlets().isEmpty());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDuplicateCloudlets() {
		FogDevice duplicate = cloudlet("duplicate", 100.0, 100.0);
		topologyService.createServerCloudletAdjacency(
			Arrays.asList(duplicate, duplicate));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullCloudletEntries() {
		List<FogDevice> cloudlets = new ArrayList<FogDevice>();
		cloudlets.add(cloudlet("valid", 100.0, 100.0));
		cloudlets.add(null);

		topologyService.createServerCloudletAdjacency(cloudlets);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullCloudletLists() {
		topologyService.createServerCloudletAdjacency(null);
	}

	@Test
	public void accessPointAssociationUsesCloudletReferenceInsteadOfSparseId() {
		FogDevice farther = new FogDevice("farther", 900, 0, 700);
		FogDevice closest = new FogDevice("closest", 10, 0, 1200);
		ApDevice accessPoint = new ApDevice("accessPoint", 0, 0, 1800);
		accessPoint.setDownlinkBandwidth(1000.0);

		topologyService.connectAccessPoints(
			Arrays.asList(farther, closest), Arrays.asList(accessPoint),
			new Random(1));

		assertSame(closest, accessPoint.getServerCloudlet());
		assertEquals(closest.getId(), accessPoint.getParentId());
		assertTrue(closest.getApDevices().contains(accessPoint));
		assertFalse(farther.getApDevices().contains(accessPoint));
	}

	private static FogDevice cloudlet(String name, double uplink,
		double downlink) {
		FogDevice cloudlet = new FogDevice(name, 0, 0, 0);
		cloudlet.setUplinkBandwidth(uplink);
		cloudlet.setDownlinkBandwidth(downlink);
		return cloudlet;
	}
}
