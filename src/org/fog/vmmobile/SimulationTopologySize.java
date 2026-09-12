package org.fog.vmmobile;

import org.cloudbus.cloudsim.NetworkTopology;
import org.fog.entities.FogDevice;

/** Immutable size measurements for the initially constructed run topology. */
public final class SimulationTopologySize {
	private final int mobileDeviceCount;
	private final int serverCloudletCount;
	private final int accessPointCount;
	private final int networkNodeCount;
	private final int networkLinkCount;
	private final long serverAdjacencyEntryCount;

	private SimulationTopologySize(int mobileDeviceCount,
		int serverCloudletCount, int accessPointCount, int networkNodeCount,
		int networkLinkCount, long serverAdjacencyEntryCount) {
		this.mobileDeviceCount = requireNonNegative(mobileDeviceCount,
			"Mobile-device count");
		this.serverCloudletCount = requireNonNegative(serverCloudletCount,
			"Server-cloudlet count");
		this.accessPointCount = requireNonNegative(accessPointCount,
			"Access-point count");
		this.networkNodeCount = requireNonNegative(networkNodeCount,
			"Network-node count");
		this.networkLinkCount = requireNonNegative(networkLinkCount,
			"Network-link count");
		this.serverAdjacencyEntryCount = requireNonNegative(
			serverAdjacencyEntryCount, "Server-adjacency entry count");
	}

	static SimulationTopologySize capture(SimulationTopology topology) {
		if (topology == null) {
			throw new IllegalArgumentException("Simulation topology cannot be null");
		}
		long adjacencyEntries = 0L;
		for (FogDevice serverCloudlet : topology.getServerCloudlets()) {
			adjacencyEntries += serverCloudlet.getNetServerCloudlets().size();
		}
		return new SimulationTopologySize(topology.getMobileDevices().size(),
			topology.getServerCloudlets().size(), topology.getAccessPoints().size(),
			NetworkTopology.getNumberOfNodes(), NetworkTopology.getNumberOfLinks(),
			adjacencyEntries);
	}

	private static int requireNonNegative(int value, String description) {
		if (value < 0) {
			throw new IllegalArgumentException(description + " cannot be negative");
		}
		return value;
	}

	private static long requireNonNegative(long value, String description) {
		if (value < 0L) {
			throw new IllegalArgumentException(description + " cannot be negative");
		}
		return value;
	}

	public int getMobileDeviceCount() {
		return mobileDeviceCount;
	}

	public int getServerCloudletCount() {
		return serverCloudletCount;
	}

	public int getAccessPointCount() {
		return accessPointCount;
	}

	public int getNetworkNodeCount() {
		return networkNodeCount;
	}

	public int getNetworkLinkCount() {
		return networkLinkCount;
	}

	public long getServerAdjacencyEntryCount() {
		return serverAdjacencyEntryCount;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof SimulationTopologySize)) {
			return false;
		}
		SimulationTopologySize other = (SimulationTopologySize) object;
		return mobileDeviceCount == other.mobileDeviceCount
			&& serverCloudletCount == other.serverCloudletCount
			&& accessPointCount == other.accessPointCount
			&& networkNodeCount == other.networkNodeCount
			&& networkLinkCount == other.networkLinkCount
			&& serverAdjacencyEntryCount == other.serverAdjacencyEntryCount;
	}

	@Override
	public int hashCode() {
		int result = mobileDeviceCount;
		result = 31 * result + serverCloudletCount;
		result = 31 * result + accessPointCount;
		result = 31 * result + networkNodeCount;
		result = 31 * result + networkLinkCount;
		result = 31 * result
			+ (int) (serverAdjacencyEntryCount ^ serverAdjacencyEntryCount >>> 32);
		return result;
	}
}
