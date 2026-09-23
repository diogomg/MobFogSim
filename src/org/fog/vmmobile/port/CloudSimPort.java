package org.fog.vmmobile.port;

import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.vmmobile.SimulationClock;

/** Run boundary for the CloudSim APIs used by domain services. */
public interface CloudSimPort extends SimulationClock {
	Object entityOrNull(int entityId);

	int entityCount();

	SimulationEventCounters eventCounters();

	void addNetworkLink(int sourceId, int destinationId, double bandwidth,
		double latency);
}
