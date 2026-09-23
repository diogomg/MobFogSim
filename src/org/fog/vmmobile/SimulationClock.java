package org.fog.vmmobile;

import org.fog.vmmobile.adapter.CloudSimAdapter;

/** Clock boundary used by a simulation context. */
public interface SimulationClock {
	double simulationTimeMillis();

	long wallTimeMillis();

	SimulationClock SYSTEM = CloudSimAdapter.INSTANCE;
}
