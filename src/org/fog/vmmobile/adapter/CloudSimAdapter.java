package org.fog.vmmobile.adapter;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.vmmobile.port.CloudSimPort;

/** The only production adapter through which extracted services use CloudSim. */
public final class CloudSimAdapter implements CloudSimPort {
	public static final CloudSimAdapter INSTANCE = new CloudSimAdapter();

	private CloudSimAdapter() {
	}

	@Override
	public double simulationTimeMillis() {
		return CloudSim.clock();
	}

	@Override
	public long wallTimeMillis() {
		return System.currentTimeMillis();
	}

	@Override
	public Object entityOrNull(int entityId) {
		try {
			return CloudSim.getEntity(entityId);
		}
		catch (IndexOutOfBoundsException error) {
			return null;
		}
	}

	@Override
	public int entityCount() {
		return CloudSim.getNumEntities();
	}

	@Override
	public SimulationEventCounters eventCounters() {
		return CloudSim.getEventCounters();
	}

	@Override
	public void addNetworkLink(int sourceId, int destinationId,
		double bandwidth, double latency) {
		NetworkTopology.addLink(sourceId, destinationId, bandwidth, latency);
	}
}
