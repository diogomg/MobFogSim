package org.fog.placement;

import java.util.List;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.SimulationClock;
import org.fog.vmmobile.port.SimulationMetricsPort;

/** Captures terminal metrics from the clock and registries owned by one run. */
public final class SimulationMetricsService {
	private final SimulationClock clock;
	private final SimulationMetricsPort metrics;

	public SimulationMetricsService(SimulationClock clock,
		SimulationMetricsPort metrics) {
		if (clock == null || metrics == null) {
			throw new IllegalArgumentException(
				"Simulation clock and metrics port cannot be null");
		}
		this.clock = clock;
		this.metrics = metrics;
	}

	public SimulationMetricsSnapshot capture(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, List<MobileDevice> mobileDevices) {
		return metrics.capture(serverCloudlets, accessPoints, mobileDevices,
			clock.simulationTimeMillis(), clock.wallTimeMillis());
	}
}
