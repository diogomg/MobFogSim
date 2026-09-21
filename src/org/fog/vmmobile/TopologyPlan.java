package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.localization.Distances;

/** Immutable, fully validated physical-topology mutations. */
public final class TopologyPlan {

	/** One undirected CloudSim network link. */
	public static final class Link {
		private final int sourceId;
		private final int destinationId;
		private final double bandwidth;
		private final double latency;

		private Link(int sourceId, int destinationId, double bandwidth,
			double latency) {
			if (sourceId < 0 || destinationId < 0 || sourceId == destinationId) {
				throw new SimulationBuildException(
					"Topology links require two registered, distinct entities");
			}
			if (!Double.isFinite(bandwidth) || bandwidth < 0.0
				|| !Double.isFinite(latency) || latency < 0.0) {
				throw new SimulationBuildException(
					"Topology link bandwidth and latency must be finite and non-negative");
			}
			this.sourceId = sourceId;
			this.destinationId = destinationId;
			this.bandwidth = bandwidth;
			this.latency = latency;
		}

		public int getSourceId() {
			return sourceId;
		}

		public int getDestinationId() {
			return destinationId;
		}

		public double getBandwidth() {
			return bandwidth;
		}

		public double getLatency() {
			return latency;
		}
	}

	/** One access point's chosen server and pre-sampled link latency. */
	public static final class AccessPointAttachment {
		private final ApDevice accessPoint;
		private final FogDevice serverCloudlet;
		private final Link link;

		private AccessPointAttachment(ApDevice accessPoint,
			FogDevice serverCloudlet, double latency) {
			this.accessPoint = accessPoint;
			this.serverCloudlet = serverCloudlet;
			this.link = new Link(serverCloudlet.getId(), accessPoint.getId(),
				accessPoint.getDownlinkBandwidth(), latency);
		}

		public ApDevice getAccessPoint() {
			return accessPoint;
		}

		public FogDevice getServerCloudlet() {
			return serverCloudlet;
		}

		public Link getLink() {
			return link;
		}
	}

	private final Map<FogDevice, Map<FogDevice, Double>> adjacency;
	private final List<Link> links;
	private final List<AccessPointAttachment> accessPointAttachments;

	private TopologyPlan(Map<FogDevice, Map<FogDevice, Double>> adjacency,
		List<Link> links, List<AccessPointAttachment> accessPointAttachments) {
		this.adjacency = immutableAdjacency(adjacency);
		this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
		this.accessPointAttachments = Collections.unmodifiableList(
			new ArrayList<AccessPointAttachment>(accessPointAttachments));
	}

	public static TopologyPlan transport(List<FogDevice> serverCloudlets,
		double baseLatency, Random random) {
		validateServerCloudlets(serverCloudlets);
		if (!Double.isFinite(baseLatency) || baseLatency < 0.0) {
			throw new IllegalArgumentException(
				"Cloudlet base latency must be finite and non-negative");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		Map<FogDevice, Map<FogDevice, Double>> adjacency =
			buildAdjacency(serverCloudlets);
		List<Link> links = new ArrayList<Link>();
		for (int sourceIndex = 0; sourceIndex < serverCloudlets.size(); sourceIndex++) {
			FogDevice source = serverCloudlets.get(sourceIndex);
			for (int destinationIndex = 0; destinationIndex < sourceIndex;
				destinationIndex++) {
				FogDevice destination = serverCloudlets.get(destinationIndex);
				int rowDistance = Math.abs(destinationIndex / 12 - sourceIndex / 12);
				int columnDistance = Math.abs(destinationIndex % 12 - sourceIndex % 12);
				double sampledLatency;
				try {
					sampledLatency = random.nextDouble();
				}
				catch (RuntimeException error) {
					throw new SimulationBuildException("Could not plan transport link "
						+ source.getName() + " -> " + destination.getName(), error);
				}
				links.add(new Link(source.getId(), destination.getId(),
					Math.min(source.getUplinkBandwidth(),
						destination.getDownlinkBandwidth()),
					Math.max(rowDistance, columnDistance) * baseLatency
						+ sampledLatency));
			}
		}
		return new TopologyPlan(adjacency, links,
			Collections.<AccessPointAttachment>emptyList());
	}

	public static TopologyPlan accessPoints(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, Random random) {
		validateServerCloudlets(serverCloudlets);
		if (serverCloudlets.isEmpty()) {
			throw new IllegalArgumentException(
				"At least one server cloudlet is required");
		}
		if (accessPoints == null) {
			throw new IllegalArgumentException("Access point list cannot be null");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		Set<ApDevice> uniqueAccessPoints = new HashSet<ApDevice>();
		for (ApDevice accessPoint : accessPoints) {
			if (accessPoint == null) {
				throw new IllegalArgumentException(
					"Access point list cannot contain null entries");
			}
			if (!uniqueAccessPoints.add(accessPoint)) {
				throw new IllegalArgumentException(
					"Access point list cannot contain duplicate devices");
			}
			if (accessPoint.getMaxSmartThing() <= 0) {
				throw new IllegalArgumentException(
					"Access-point capacity must be positive: "
						+ accessPoint.getName());
			}
		}

		List<AccessPointAttachment> attachments =
			new ArrayList<AccessPointAttachment>();
		for (ApDevice accessPoint : accessPoints) {
			FogDevice closest = Distances
				.findClosestServerCloudletToAp(serverCloudlets, accessPoint)
				.orElseThrow(() -> new SimulationBuildException(
					"Cannot connect access point " + accessPoint.getName()
						+ " without a server cloudlet"));
			if (accessPoint.getServerCloudlet() != null
				&& accessPoint.getServerCloudlet() != closest) {
				throw new SimulationBuildException("Access point "
					+ accessPoint.getName() + " is already attached to "
					+ accessPoint.getServerCloudlet().getName());
			}
			double latency;
			try {
				latency = random.nextDouble();
			}
			catch (RuntimeException error) {
				throw new SimulationBuildException("Could not plan access-point link "
					+ accessPoint.getName() + " -> " + closest.getName(), error);
			}
			attachments.add(new AccessPointAttachment(accessPoint, closest,
				latency));
		}
		return new TopologyPlan(
			Collections.<FogDevice, Map<FogDevice, Double>>emptyMap(),
			Collections.<Link>emptyList(), attachments);
	}

	public static TopologyPlan adjacency(List<FogDevice> serverCloudlets) {
		validateServerCloudlets(serverCloudlets);
		return new TopologyPlan(buildAdjacency(serverCloudlets),
			Collections.<Link>emptyList(),
			Collections.<AccessPointAttachment>emptyList());
	}

	public Map<FogDevice, Map<FogDevice, Double>> getAdjacency() {
		return adjacency;
	}

	public List<Link> getLinks() {
		return links;
	}

	public List<AccessPointAttachment> getAccessPointAttachments() {
		return accessPointAttachments;
	}

	private static Map<FogDevice, Map<FogDevice, Double>> buildAdjacency(
		List<FogDevice> serverCloudlets) {
		Map<FogDevice, Map<FogDevice, Double>> result =
			new HashMap<FogDevice, Map<FogDevice, Double>>();
		for (FogDevice source : serverCloudlets) {
			Map<FogDevice, Double> peers = new HashMap<FogDevice, Double>();
			for (FogDevice destination : serverCloudlets) {
				if (source != destination) {
					peers.put(destination, Math.min(source.getUplinkBandwidth(),
						destination.getDownlinkBandwidth()));
				}
			}
			result.put(source, peers);
		}
		return result;
	}

	private static Map<FogDevice, Map<FogDevice, Double>> immutableAdjacency(
		Map<FogDevice, Map<FogDevice, Double>> source) {
		Map<FogDevice, Map<FogDevice, Double>> result =
			new HashMap<FogDevice, Map<FogDevice, Double>>();
		for (Map.Entry<FogDevice, Map<FogDevice, Double>> entry
			: source.entrySet()) {
			result.put(entry.getKey(), Collections.unmodifiableMap(
				new HashMap<FogDevice, Double>(entry.getValue())));
		}
		return Collections.unmodifiableMap(result);
	}

	private static void validateServerCloudlets(
		List<FogDevice> serverCloudlets) {
		if (serverCloudlets == null) {
			throw new IllegalArgumentException("Server cloudlet list cannot be null");
		}
		Set<FogDevice> uniqueCloudlets = new HashSet<FogDevice>();
		for (FogDevice serverCloudlet : serverCloudlets) {
			if (serverCloudlet == null) {
				throw new IllegalArgumentException(
					"Server cloudlet list cannot contain null entries");
			}
			if (!uniqueCloudlets.add(serverCloudlet)) {
				throw new IllegalArgumentException(
					"Server cloudlet list cannot contain duplicate devices");
			}
		}
	}
}
