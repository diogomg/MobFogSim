package org.fog.vmmobile.port;

import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.utils.DataRate;
import org.fog.utils.EntityId;
import org.fog.utils.PropagationDelay;
import org.fog.vmmobile.SimulationClock;

/** Run boundary for the CloudSim APIs used by domain services. */
public interface CloudSimPort extends SimulationClock {
	Object entityOrNull(int entityId);

	default Object entityOrNull(EntityId entityId) {
		if (entityId == null) {
			throw new IllegalArgumentException("CloudSim entity ID cannot be null");
		}
		return entityOrNull(entityId.intValue());
	}

	int entityCount();

	SimulationEventCounters eventCounters();

	void addNetworkLink(int sourceId, int destinationId, double bandwidth,
		double latency);

	default void addNetworkLink(EntityId sourceId, EntityId destinationId,
		DataRate bandwidth, PropagationDelay latency) {
		if (sourceId == null || destinationId == null || bandwidth == null
			|| latency == null) {
			throw new IllegalArgumentException(
				"CloudSim network-link values cannot be null");
		}
		addNetworkLink(sourceId.intValue(), destinationId.intValue(),
			bandwidth.toBitsPerSecond(), latency.toMilliseconds());
	}
}
