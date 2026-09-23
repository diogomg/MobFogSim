package org.fog.vmmobile.adapter;

import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.vmmobile.SimulationClock;
import org.fog.vmmobile.port.CloudSimPort;

/** CloudSim adapter with an overridable clock for deterministic context tests. */
public final class ClockedCloudSimAdapter implements CloudSimPort {
	private final CloudSimPort cloudSim;
	private final SimulationClock clock;

	public ClockedCloudSimAdapter(CloudSimPort cloudSim, SimulationClock clock) {
		if (cloudSim == null || clock == null) {
			throw new IllegalArgumentException("CloudSim adapter and clock cannot be null");
		}
		this.cloudSim = cloudSim;
		this.clock = clock;
	}

	@Override
	public double simulationTimeMillis() {
		return clock.simulationTimeMillis();
	}

	@Override
	public long wallTimeMillis() {
		return clock.wallTimeMillis();
	}

	@Override
	public Object entityOrNull(int entityId) {
		return cloudSim.entityOrNull(entityId);
	}

	@Override
	public int entityCount() {
		return cloudSim.entityCount();
	}

	@Override
	public SimulationEventCounters eventCounters() {
		return cloudSim.eventCounters();
	}

	@Override
	public void addNetworkLink(int sourceId, int destinationId,
		double bandwidth, double latency) {
		cloudSim.addNetworkLink(sourceId, destinationId, bandwidth, latency);
	}
}
