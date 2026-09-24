package org.fog.vmmobile;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.utils.DataRate;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.policy.LocationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/** Example-specific defaults and derived services for one validated run. */
public final class ExampleSimulationConfiguration {
	private static final int DEFAULT_STEP_POLICY = 1;

	private final SimulationConfig simulation;
	private final Random random;
	private final LocationPolicy accessPointLocation = LocationPolicy.FIXED;
	private final LocationPolicy serverCloudletLocation = LocationPolicy.FIXED;
	private final Coordinate coordinateSpace = new Coordinate();

	public ExampleSimulationConfiguration(SimulationConfig simulation,
		Random random) {
		if (simulation == null || random == null) {
			throw new IllegalArgumentException(
				"Simulation configuration and random stream cannot be null");
		}
		this.simulation = simulation;
		this.random = random;
	}

	/**
	 * Keeps old static callers operational while production orchestration uses
	 * this value directly.
	 */
	void activateLegacyCompatibility() {
		AppExample.setMigrationAble(isMigrationEnabled());
		AppExample.setSeed(getSeed());
		AppExample.setRand(random);
		AppExample.setMigrationPointPolicy(getMigrationPoint());
		AppExample.setMigrationStrategyPolicy(getMigrationStrategy());
		AppExample.setMaxSmartThings(getMaximumUsers());
		AppExample.setMaxBandwidth(getMaximumBandwidth());
		AppExample.setMigrationTechniquePolicy(getMigrationTechnique());
		AppExample.setLatencyBetweenCloudlets(getCloudletLatency());
		AppExample.setTravelPredictionTimeForST(getTravelPredictionTime());
		AppExample.setMobilityPredictionError(getMobilityPredictionError());
		AppExample.setAccessPointLocationPolicy(accessPointLocation);
		AppExample.setServerCloudletLocationPolicy(serverCloudletLocation);
		AppExample.setStepPolicy(DEFAULT_STEP_POLICY);
		AppExample.setCoordDevices(coordinateSpace);
		AppExample.setMobilityDirectory(getMobilityDirectory());
		AppExample.setMobilityOrderManifest(getMobilityOrderManifest());
		AppExample.setOutputDirectory(getOutputDirectory());
		AppExample.setOutputMode(getOutputMode());
		NetworkSlicing.applyConfiguration(simulation.getSlicingConfiguration());
		VmDestinationPolicy.configure(simulation.getVmDestination());
	}

	void configureStatistics(List<MobileDevice> mobileDevices,
		MyStatistics statistics) {
		if (mobileDevices == null || statistics == null) {
			throw new IllegalArgumentException(
				"Mobile devices and statistics cannot be null");
		}
		statistics.setSeed(getSeed());
		String experiment = experimentLabel();
		statistics.setToPrint(experiment);
		for (MobileDevice mobileDevice : mobileDevices) {
			int mobileId = mobileDevice.getMyId();
			String suffix = experiment + "_seed_" + getSeed() + "_st_" + mobileId;
			statistics.setFileMap("./outputLatencies/" + mobileId
				+ "/latencies_" + suffix + ".txt", mobileId);
			statistics.putLatencyFileName(suffix, mobileId);
			statistics.putLatencyFileName("Time-latency", mobileId);
			statistics.initialiseLatencyCounter(mobileId);
		}
	}

	String experimentLabel() {
		String point = getMigrationPoint() == MigrationPointPolicy.FIXED
			? "FIXED_MIGRATION_POINT" : "SPEED_MIGRATION_POINT";
		String strategy;
		switch (getMigrationStrategy()) {
		case LOWEST_LATENCY:
			strategy = "LOWEST_LATENCY";
			break;
		case LOWEST_DISTANCE_TO_ACCESS_POINT:
			strategy = "LOWEST_DIST_BW_SMART_THING_AP";
			break;
		case LOWEST_DISTANCE_TO_SERVER_CLOUDLET:
			strategy = "LOWEST_DIST_BW_SMART_THING_SERVER_CLOUDLET";
			break;
		default:
			throw new IllegalStateException(
				"Unsupported migration strategy " + getMigrationStrategy());
		}
		return point + "_with_" + strategy;
	}

	public SimulationConfig getSimulationConfig() {
		return simulation;
	}

	public Random getRandom() {
		return random;
	}

	public LocationPolicy getAccessPointLocation() {
		return accessPointLocation;
	}

	public LocationPolicy getServerCloudletLocation() {
		return serverCloudletLocation;
	}

	public Coordinate getCoordinateSpace() {
		return coordinateSpace;
	}

	public int getStepPolicy() {
		return DEFAULT_STEP_POLICY;
	}

	public boolean isMigrationEnabled() {
		return simulation.isMigrationEnabled();
	}

	public int getSeed() {
		return simulation.getSeed();
	}

	public MigrationPointPolicy getMigrationPoint() {
		return simulation.getMigrationPoint();
	}

	public MigrationStrategyPolicy getMigrationStrategy() {
		return simulation.getMigrationStrategy();
	}

	public MigrationTechniquePolicy getMigrationTechnique() {
		return simulation.getMigrationTechnique();
	}

	public int getMaximumUsers() {
		return simulation.getMaximumUsers();
	}

	public int getMaximumBandwidth() {
		return simulation.getMaximumBandwidth();
	}

	public DataRate getMaximumBandwidthRate() {
		return simulation.getMaximumBandwidthRate();
	}

	public double getCloudletLatency() {
		return simulation.getCloudletLatency();
	}

	public PropagationDelay getCloudletPropagationDelay() {
		return simulation.getCloudletPropagationDelay();
	}

	public int getTravelPredictionTime() {
		return simulation.getTravelPredictionTime();
	}

	public SimulationDuration getTravelPredictionDuration() {
		return simulation.getTravelPredictionDuration();
	}

	public int getMobilityPredictionError() {
		return simulation.getMobilityPredictionError();
	}

	public Path getMobilityDirectory() {
		return simulation.getMobilityDirectory();
	}

	public Path getMobilityOrderManifest() {
		return simulation.getMobilityOrderManifest();
	}

	public Path getOutputDirectory() {
		return simulation.getOutputDirectory();
	}

	public RunOutputMode getOutputMode() {
		return simulation.getOutputMode();
	}
}
