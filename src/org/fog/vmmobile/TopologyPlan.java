package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.localization.Distances;
import org.fog.localization.GridGenerator;
import org.fog.localization.GridPosition;
import org.fog.utils.DataRate;
import org.fog.utils.EntityId;
import org.fog.utils.PropagationDelay;
import org.fog.vmmobile.constants.MaxAndMin;

/** Immutable, fully validated logical-topology mutations. */
public final class TopologyPlan {

	/** One undirected CloudSim network link. */
	public static final class Link {
		private final EntityId sourceId;
		private final EntityId destinationId;
		private final DataRate bandwidth;
		private final PropagationDelay latency;

		private Link(EntityId sourceId, EntityId destinationId,
			DataRate bandwidth, PropagationDelay latency) {
			if (sourceId == null || destinationId == null || bandwidth == null
				|| latency == null || sourceId.equals(destinationId)) {
				throw new SimulationBuildException(
					"Topology links require two registered, distinct entities");
			}
			this.sourceId = sourceId;
			this.destinationId = destinationId;
			this.bandwidth = bandwidth;
			this.latency = latency;
		}

		public int getSourceId() {
			return sourceId.intValue();
		}

		public EntityId getSourceEntityId() {
			return sourceId;
		}

		public int getDestinationId() {
			return destinationId.intValue();
		}

		public EntityId getDestinationEntityId() {
			return destinationId;
		}

		public double getBandwidth() {
			return bandwidth.toBitsPerSecond();
		}

		public DataRate getDataRate() {
			return bandwidth;
		}

		public double getLatency() {
			return latency.toMilliseconds();
		}

		public PropagationDelay getPropagationDelay() {
			return latency;
		}
	}

	/** One access point's chosen server and pre-sampled link latency. */
	public static final class AccessPointAttachment {
		private final ApDevice accessPoint;
		private final FogDevice serverCloudlet;
		private final Link link;

		private AccessPointAttachment(ApDevice accessPoint,
			FogDevice serverCloudlet, PropagationDelay latency) {
			this.accessPoint = accessPoint;
			this.serverCloudlet = serverCloudlet;
			this.link = new Link(EntityId.of(serverCloudlet.getId()),
				EntityId.of(accessPoint.getId()), DataRate.ofBitsPerSecond(
					accessPoint.getDownlinkBandwidth()), latency);
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

	private final List<Link> links;
	private final List<AccessPointAttachment> accessPointAttachments;

	private TopologyPlan(List<Link> links,
		List<AccessPointAttachment> accessPointAttachments) {
		this.links = Collections.unmodifiableList(new ArrayList<Link>(links));
		this.accessPointAttachments = Collections.unmodifiableList(
			new ArrayList<AccessPointAttachment>(accessPointAttachments));
	}

	public static TopologyPlan transport(List<FogDevice> serverCloudlets,
		double baseLatency, Random random) {
		return transport(serverCloudlets,
			PropagationDelay.ofMilliseconds(baseLatency), random);
	}

	public static TopologyPlan transport(List<FogDevice> serverCloudlets,
		PropagationDelay baseLatency, Random random) {
		validateServerCloudlets(serverCloudlets);
		if (baseLatency == null) {
			throw new IllegalArgumentException(
				"Cloudlet base latency cannot be null");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		final Map<FogDevice, GridPosition> positions =
			new HashMap<FogDevice, GridPosition>();
		for (FogDevice cloudlet : serverCloudlets) {
			positions.put(cloudlet, positionOf(cloudlet));
		}
		List<FogDevice> orderedCloudlets =
			new ArrayList<FogDevice>(serverCloudlets);
		Collections.sort(orderedCloudlets, new Comparator<FogDevice>() {
			@Override
			public int compare(FogDevice first, FogDevice second) {
				GridPosition firstPosition = positions.get(first);
				GridPosition secondPosition = positions.get(second);
				int xComparison = Integer.compare(firstPosition.getX(),
					secondPosition.getX());
				if (xComparison != 0) {
					return xComparison;
				}
				int yComparison = Integer.compare(firstPosition.getY(),
					secondPosition.getY());
				if (yComparison != 0) {
					return yComparison;
				}
				int nameComparison = first.getName().compareTo(second.getName());
				return nameComparison != 0 ? nameComparison
					: Integer.compare(first.getId(), second.getId());
			}
		});
		double gridSpacing = GridGenerator.spacingForCoverage(
			MaxAndMin.CLOUDLET_COVERAGE);
		List<Link> links = new ArrayList<Link>();
		for (int sourceIndex = 0; sourceIndex < orderedCloudlets.size(); sourceIndex++) {
			FogDevice source = orderedCloudlets.get(sourceIndex);
			for (int destinationIndex = 0; destinationIndex < sourceIndex;
				destinationIndex++) {
				FogDevice destination = orderedCloudlets.get(destinationIndex);
				GridPosition sourcePosition = positions.get(source);
				GridPosition destinationPosition = positions.get(destination);
				double gridDistance = sourcePosition.chebyshevDistanceTo(
					destinationPosition) / gridSpacing;
				double sampledLatency;
				try {
					sampledLatency = random.nextDouble();
				}
				catch (RuntimeException error) {
					throw new SimulationBuildException("Could not plan transport link "
						+ source.getName() + " -> " + destination.getName(), error);
				}
				links.add(new Link(EntityId.of(source.getId()),
					EntityId.of(destination.getId()), DataRate.ofBitsPerSecond(
						Math.min(source.getUplinkBandwidth(),
							destination.getDownlinkBandwidth())),
					PropagationDelay.ofMilliseconds(gridDistance
						* baseLatency.toMilliseconds() + sampledLatency)));
			}
		}
		return new TopologyPlan(links,
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
			PropagationDelay latency;
			try {
				latency = PropagationDelay.ofMilliseconds(random.nextDouble());
			}
			catch (RuntimeException error) {
				throw new SimulationBuildException("Could not plan access-point link "
					+ accessPoint.getName() + " -> " + closest.getName(), error);
			}
			attachments.add(new AccessPointAttachment(accessPoint, closest,
				latency));
		}
		return new TopologyPlan(Collections.<Link>emptyList(), attachments);
	}

	public List<Link> getLinks() {
		return links;
	}

	public List<AccessPointAttachment> getAccessPointAttachments() {
		return accessPointAttachments;
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

	private static GridPosition positionOf(FogDevice cloudlet) {
		try {
			return new GridPosition(cloudlet.getCoord().getCoordX(),
				cloudlet.getCoord().getCoordY());
		}
		catch (IllegalArgumentException error) {
			throw new SimulationBuildException("Server cloudlet "
				+ cloudlet.getName() + " has an invalid grid coordinate", error);
		}
	}
}
