package org.fog.vmmobile.port;

import java.util.List;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.placement.SimulationMetricsSnapshot;

/** Run boundary for capturing one immutable terminal metrics snapshot. */
public interface SimulationMetricsPort {
	SimulationMetricsSnapshot capture(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, List<MobileDevice> mobileDevices,
		double simulationTimeMillis, long wallTimeMillis);
}
