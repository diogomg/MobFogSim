package org.fog.vmmobile;

import org.cloudbus.cloudsim.core.CloudSim;

/** Clock boundary used by a simulation context. */
public interface SimulationClock {
	double simulationTimeMillis();

	long wallTimeMillis();

	SimulationClock SYSTEM = new SimulationClock() {
		@Override
		public double simulationTimeMillis() {
			return CloudSim.clock();
		}

		@Override
		public long wallTimeMillis() {
			return System.currentTimeMillis();
		}
	};
}
