package org.fog.vmmobile;

import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.MobilitySample;
import org.fog.localization.MobilityTimeline;
import org.fog.placement.MobileController;
import org.fog.placement.ModuleMapping;
import org.fog.vmmobile.constants.MaxAndMin;

/** Owns the context, simulator, controller, and publication lifecycle of a run. */
public final class SimulationRunner {

	public SimulationRunResult run(SimulationConfig configuration)
		throws Exception {
		if (configuration == null) {
			throw new IllegalArgumentException(
				"Simulation configuration cannot be null");
		}
		try (SimulationContext context = SimulationContext.open(configuration)) {
			try {
				execute(context, configuration);
				context.publishResults();
				return context.result();
			}
			catch (Exception error) {
				context.recordFailure(error);
				throw error;
			}
			catch (Error error) {
				context.recordFailure(error);
				throw error;
			}
		}
	}

	void execute(SimulationContext context, SimulationConfig configuration)
		throws Exception {
		Log.disable();
		SimulationEventSink events = context.getEventSink();
		events.summary(configuration::toSummaryLine);
		CloudSim.init(1, Calendar.getInstance(), false);

		ExampleSimulationConfiguration example =
			new ExampleSimulationConfiguration(configuration,
				context.random("application"));
		example.activateLegacyCompatibility();
		new ExampleTopologyPlan(context, example).build();

		SimulationTopology topology = context.getTopology();
		List<FogDevice> serverCloudlets = topology.serverCloudletRegistry();
		List<ApDevice> accessPoints = topology.accessPointRegistry();
		List<MobileDevice> mobileDevices = topology.mobileDeviceRegistry();
		List<FogBroker> brokers = topology.brokerRegistry();
		new MobileController("MobileController", serverCloudlets, accessPoints,
			mobileDevices, brokers, ModuleMapping.createModuleMapping(),
			example.getMigrationPoint(), example.getMigrationStrategy(),
			example.getStepPolicy(), example.getCoordinateSpace(), example.getSeed(),
			example.isMigrationEnabled(), context.getServices());

		context.getTimeKeeper().setSimulationStartTime(
			context.getClock().wallTimeMillis());
		example.configureStatistics(mobileDevices, context.getStatistics());
		for (MobileDevice mobileDevice : mobileDevices) {
			events.detail("AppExample", () -> mobileDevice.getName() + "- X: "
				+ mobileDevice.getCoord().getCoordX() + " Y: "
				+ mobileDevice.getCoord().getCoordY() + " Direction: "
				+ mobileDevice.getDirection() + " Speed: "
				+ mobileDevice.getSpeed() + " EntryTime: "
				+ mobileDevice.getStartTravelTime() + " seconds");
		}
		context.startProgress(expectedSimulationEndTime(mobileDevices));
		events.detail("AppExample", () -> "Started at "
			+ Calendar.getInstance().getTime());
		CloudSim.startSimulation();
		context.completeProgress();
		events.detail("AppExample", () -> "Simulation over");
	}

	static double expectedSimulationEndTime(List<MobileDevice> mobileDevices) {
		if (mobileDevices == null) {
			throw new IllegalArgumentException("Mobile device list cannot be null");
		}
		double latestTraceTime = 0.0;
		for (MobileDevice mobileDevice : mobileDevices) {
			List<MobilitySample> path = mobileDevice.getMobilityPath();
			if (!path.isEmpty()) {
				latestTraceTime = Math.max(latestTraceTime,
					MobilityTimeline.toSimulationTime(
						path.get(path.size() - 1).getTimeSeconds()));
			}
		}
		return latestTraceTime <= 0.0 ? MaxAndMin.MAX_SIMULATION_TIME
			: Math.min(latestTraceTime, MaxAndMin.MAX_SIMULATION_TIME);
	}
}
