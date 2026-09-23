package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.localization.GridGenerator;
import org.fog.localization.GridPosition;
import org.fog.localization.MapBounds;
import org.fog.utils.DataRate;
import org.fog.utils.EntityId;
import org.fog.utils.PropagationDelay;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmigration.ServiceAgreement;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.junit.Before;
import org.junit.Test;

public class ServerCloudletNetworkTest {

	private static final double DELTA = 0.000001;
	private final TopologyService topologyService = new TopologyService();

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkTopology.reset();
		AppExample.setServerCloudlets(new ArrayList<FogDevice>());
		AppExample.setApDevices(new ArrayList<ApDevice>());
		AppExample.setRand(new Random(1L));
		AppExample.setMaxBandwidth(11);
		AppExample.setMigrationPointPolicy(MigrationPointPolicy.FIXED);
		AppExample.setMigrationStrategyPolicy(
			MigrationStrategyPolicy.LOWEST_LATENCY);
		AppExample.setMigrationTechniquePolicy(
			MigrationTechniquePolicy.COMPLETE_VM);
	}

	@Test
	public void logicalTransportClosureIsCentralizedInNetworkTopology() {
		FogDevice first = cloudlet("first", 100.0, 700.0);
		FogDevice second = cloudlet("second", 200.0, 50.0);
		FogDevice third = cloudlet("third", 300.0, 250.0);
		List<FogDevice> cloudlets = Arrays.asList(first, second, third);

		topologyService.createTransportNetwork(cloudlets, 4.0,
			new ZeroRandom());

		assertEquals(3, NetworkTopology.getNumberOfLinks());
		assertEquals(6L, NetworkTopology.getDirectedLinkCountAmong(Arrays.asList(
			first.getId(), second.getId(), third.getId())));
		for (FogDevice source : cloudlets) {
			for (FogDevice destination : cloudlets) {
				if (source == destination) {
					assertFalse(NetworkTopology.hasDirectLink(source.getId(),
						destination.getId()));
				}
				else {
					assertTrue(NetworkTopology.hasDirectLink(source.getId(),
						destination.getId()));
					assertTrue(ServiceAgreement.checkLinkStatus(source, destination));
				}
			}
		}
	}

	@Test
	public void oneCloudletHasNoTransportRoutes() {
		FogDevice only = cloudlet("only", 100.0, 100.0);

		topologyService.createTransportNetwork(Arrays.asList(only), 4.0,
			new ZeroRandom());

		assertEquals(0, NetworkTopology.getNumberOfLinks());
		assertEquals(0L, NetworkTopology.getDirectedLinkCountAmong(
			Collections.singletonList(only.getId())));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDuplicateCloudlets() {
		FogDevice duplicate = cloudlet("duplicate", 100.0, 100.0);
		topologyService.createTransportNetwork(
			Arrays.asList(duplicate, duplicate), 4.0, new ZeroRandom());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullCloudletEntries() {
		List<FogDevice> cloudlets = new ArrayList<FogDevice>();
		cloudlets.add(cloudlet("valid", 100.0, 100.0));
		cloudlets.add(null);

		topologyService.createTransportNetwork(cloudlets, 4.0,
			new ZeroRandom());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullCloudletLists() {
		topologyService.createTransportNetwork(null, 4.0, new ZeroRandom());
	}

	@Test
	public void accessPointAssociationUsesCloudletReferenceInsteadOfSparseId() {
		FogDevice farther = new FogDevice("farther", 900, 0, 700);
		FogDevice closest = new FogDevice("closest", 10, 0, 1200);
		ApDevice accessPoint = new ApDevice("accessPoint", 0, 0, 1800);
		accessPoint.setDownlinkBandwidth(1000.0);
		accessPoint.setMaxSmartThing(1);

		topologyService.connectAccessPoints(
			Arrays.asList(farther, closest), Arrays.asList(accessPoint),
			new Random(1));

		assertSame(closest, accessPoint.getServerCloudlet());
		assertEquals(closest.getId(), accessPoint.getParentId());
		assertTrue(closest.getApDevices().contains(accessPoint));
		assertFalse(farther.getApDevices().contains(accessPoint));
	}

	@Test
	public void transportLatencyUsesCoordinatesOnANonTwelveColumnGrid() {
		int spacing = GridGenerator.spacingForCoverage(
			MaxAndMin.CLOUDLET_COVERAGE);
		List<FogDevice> cloudlets = cloudletsAt(
			new GridGenerator(new MapBounds(spacing * 3, spacing * 2))
				.fixedPositions(spacing));

		TopologyPlan plan = TopologyPlan.transport(cloudlets, 4.0,
			new ZeroRandom());
		TopologyPlan.Link firstLink = plan.getLinks().get(0);

		assertEquals(8.0, latency(plan, "cloudlet-0", "cloudlet-4"), DELTA);
		assertEquals(4.0, latency(plan, "cloudlet-0", "cloudlet-3"), DELTA);
		assertEquals(EntityId.of(firstLink.getSourceId()),
			firstLink.getSourceEntityId());
		assertEquals(EntityId.of(firstLink.getDestinationId()),
			firstLink.getDestinationEntityId());
		assertEquals(DataRate.ofBitsPerSecond(firstLink.getBandwidth()),
			firstLink.getDataRate());
		assertEquals(PropagationDelay.ofMilliseconds(firstLink.getLatency()),
			firstLink.getPropagationDelay());
	}

	@Test
	public void shuffledServerInputDoesNotChangeLinkCharacteristics() {
		int spacing = GridGenerator.spacingForCoverage(
			MaxAndMin.CLOUDLET_COVERAGE);
		List<FogDevice> cloudlets = cloudletsAt(
			new GridGenerator(new MapBounds(spacing * 3, spacing * 2))
				.fixedPositions(spacing));
		TopologyPlan ordered = TopologyPlan.transport(cloudlets, 4.0,
			new Random(37L));
		List<FogDevice> shuffled = new ArrayList<FogDevice>(cloudlets);
		Collections.shuffle(shuffled, new Random(91L));
		TopologyPlan reordered = TopologyPlan.transport(shuffled, 4.0,
			new Random(37L));

		assertEquals(linkCharacteristics(ordered), linkCharacteristics(reordered));
	}

	@Test
	public void randomServerBuilderUsesTheRectangularHeightBound() {
		List<FogDevice> cloudlets = new ArrayList<FogDevice>();
		AppExample.setServerCloudlets(cloudlets);
		AppExample.setRand(new MaximumRandom());

		AppExample.addServerCloudlet(cloudlets, null, 0,
			new MapBounds(17, 5));

		assertEquals(16, cloudlets.get(0).getCoord().getCoordX());
		assertEquals(4, cloudlets.get(0).getCoord().getCoordY());
	}

	private static List<FogDevice> cloudletsAt(List<GridPosition> positions) {
		List<FogDevice> cloudlets = new ArrayList<FogDevice>();
		for (int index = 0; index < positions.size(); index++) {
			GridPosition position = positions.get(index);
			FogDevice cloudlet = new FogDevice("cloudlet-" + index,
				position.getX(), position.getY(), index);
			cloudlet.setUplinkBandwidth(100.0 + index);
			cloudlet.setDownlinkBandwidth(200.0 + index);
			cloudlets.add(cloudlet);
		}
		return cloudlets;
	}

	private static double latency(TopologyPlan plan, String first,
		String second) {
		return linkCharacteristics(plan).get(pair(first, second)).get(1);
	}

	private static Map<String, List<Double>> linkCharacteristics(
		TopologyPlan plan) {
		Map<String, List<Double>> characteristics =
			new java.util.TreeMap<String, List<Double>>();
		for (TopologyPlan.Link link : plan.getLinks()) {
			characteristics.put(pair(CloudSim.getEntityName(link.getSourceId()),
				CloudSim.getEntityName(link.getDestinationId())), Arrays.asList(
					link.getBandwidth(), link.getLatency()));
		}
		return characteristics;
	}

	private static String pair(String first, String second) {
		return first.compareTo(second) < 0 ? first + ":" + second
			: second + ":" + first;
	}

	private static final class ZeroRandom extends Random {
		private static final long serialVersionUID = 1L;

		@Override
		public double nextDouble() {
			return 0.0;
		}
	}

	private static final class MaximumRandom extends Random {
		private static final long serialVersionUID = 1L;

		@Override
		public int nextInt(int bound) {
			return bound - 1;
		}
	}

	private static FogDevice cloudlet(String name, double uplink,
		double downlink) {
		FogDevice cloudlet = new FogDevice(name, 0, 0, 0);
		cloudlet.setUplinkBandwidth(uplink);
		cloudlet.setDownlinkBandwidth(downlink);
		return cloudlet;
	}
}
