package org.fog.vmmobile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.application.Application;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.localization.MapBounds;
import org.fog.vmmobile.policy.LocationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/** Entry point and legacy compatibility facade for the original example. */
public class AppExample {
	private static int stepPolicy;
	private static List<MobileDevice> smartThings =
		new ArrayList<MobileDevice>();
	private static List<FogDevice> serverCloudlets =
		new ArrayList<FogDevice>();
	private static List<ApDevice> apDevices = new ArrayList<ApDevice>();
	private static List<FogBroker> brokerList = new ArrayList<FogBroker>();
	private static List<String> appIdList = new ArrayList<String>();
	private static List<Application> applicationList =
		new ArrayList<Application>();
	private static boolean migrationAble;
	private static MigrationPointPolicy migPointPolicy =
		MigrationPointPolicy.FIXED;
	private static MigrationStrategyPolicy migStrategyPolicy =
		MigrationStrategyPolicy.LOWEST_LATENCY;
	private static LocationPolicy positionApPolicy = LocationPolicy.FIXED;
	private static LocationPolicy positionScPolicy = LocationPolicy.FIXED;
	private static MigrationTechniquePolicy policyReplicaVM =
		MigrationTechniquePolicy.COMPLETE_VM;
	private static int travelPredictionTimeForST;
	private static int mobilityPredictionError;
	private static double latencyBetweenCloudlets;
	private static int maxBandwidth;
	private static int maxSmartThings;
	private static Path mobilityDirectory = Paths.get("input");
	private static Path mobilityOrderManifest =
		mobilityDirectory.resolve("inputOrder.csv");
	private static Path outputDirectory = Paths.get("runs", "unconfigured");
	private static RunOutputMode outputMode = RunOutputMode.SUMMARY;
	private static Coordinate coordDevices;
	private static int seed;
	private static Random rand;

	/** Application workloads created for each user in this example. */
	public static final List<MobileApplicationProfile> APPLICATION_PROFILES =
		Collections.singletonList(MobileApplicationProfile.standard());
	public static final int APPLICATIONS_PER_USER = APPLICATION_PROFILES.size();

	public static void main(String[] args) throws Exception {
		run(args);
	}

	/** Runs one isolated simulation and returns its immutable final result. */
	public static SimulationRunResult run(String[] args) throws Exception {
		return run(SimulationCli.parse(args));
	}

	/** Runs one already validated configuration for an embedded caller. */
	public static SimulationRunResult run(SimulationConfig configuration)
		throws Exception {
		return new SimulationRunner().run(configuration, APPLICATION_PROFILES);
	}

	static void configureSimulationParameters(String[] args) {
		SimulationConfig configuration = SimulationCli.parse(args);
		SimulationContext context = SimulationContext.currentOrNull();
		Random random = context == null
			? new Random(configuration.getSeed() * Integer.MAX_VALUE)
			: context.random("application");
		new ExampleSimulationConfiguration(configuration, random)
			.activateLegacyCompatibility();
	}

	static void useSimulationContext(SimulationContext context) {
		SimulationTopology topology = context.getTopology();
		smartThings = topology.mobileDeviceRegistry();
		serverCloudlets = topology.serverCloudletRegistry();
		apDevices = topology.accessPointRegistry();
		brokerList = topology.brokerRegistry();
		appIdList = topology.applicationIdRegistry();
		applicationList = topology.applicationRegistry();
	}

	static void releaseSimulationContext(SimulationContext context) {
		if (SimulationContext.currentOrNull() != context) {
			return;
		}
		setSmartThings(new ArrayList<MobileDevice>());
		setServerCloudlets(new ArrayList<FogDevice>());
		setApDevices(new ArrayList<ApDevice>());
		setBrokerList(new ArrayList<FogBroker>());
		setAppIdList(new ArrayList<String>());
		setApplicationList(new ArrayList<Application>());
		setCoordDevices(null);
		rand = null;
	}

	static void addApDevicesFixed(List<ApDevice> accessPoints,
		Coordinate coordinateSpace) {
		addApDevicesFixed(accessPoints, coordinateSpace, MapBounds.defaults());
	}

	static void addApDevicesFixed(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, MapBounds bounds) {
		ExampleDeviceFactory.addFixedAccessPoints(accessPoints, coordinateSpace,
			bounds);
	}

	static void addApDevicesRandom(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, int index) {
		addApDevicesRandom(accessPoints, coordinateSpace, index,
			MapBounds.defaults());
	}

	/** @deprecated Use {@link #addApDevicesRandom(List, Coordinate, int)}. */
	@Deprecated
	static void addApDevicesRandon(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, int index) {
		addApDevicesRandom(accessPoints, coordinateSpace, index);
	}

	static void addApDevicesRandom(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, int index, MapBounds bounds) {
		ExampleDeviceFactory.addRandomAccessPoint(accessPoints, coordinateSpace,
			index, bounds);
	}

	public static void addSmartThing(List<MobileDevice> mobileDevices,
		Coordinate coordinateSpace, int index) {
		addSmartThing(mobileDevices, coordinateSpace, index,
			APPLICATION_PROFILES);
	}

	public static void addSmartThing(List<MobileDevice> mobileDevices,
		Coordinate coordinateSpace, int index, int applicationsPerUser) {
		addSmartThing(mobileDevices, coordinateSpace, index,
			MobileApplicationProfile.standardProfiles(applicationsPerUser));
	}

	public static void addSmartThing(List<MobileDevice> mobileDevices,
		Coordinate coordinateSpace, int index,
		List<MobileApplicationProfile> applicationProfiles) {
		ExampleDeviceFactory.addMobileDevice(mobileDevices, coordinateSpace,
			index, applicationProfiles);
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace, int index) {
		addServerCloudlet(serverCloudlets, coordinateSpace, index,
			MapBounds.defaults());
	}

	static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace, int index, MapBounds bounds) {
		ExampleDeviceFactory.addRandomServerCloudlet(serverCloudlets,
			coordinateSpace, index, bounds);
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace) {
		addServerCloudlet(serverCloudlets, coordinateSpace, MapBounds.defaults());
	}

	static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace, MapBounds bounds) {
		ExampleDeviceFactory.addFixedServerCloudlets(serverCloudlets,
			coordinateSpace, bounds);
	}

	public static int getPolicyReplicaVM() {
		return policyReplicaVM.legacyValue();
	}

	public static void setPolicyReplicaVM(int policy) {
		setMigrationTechniquePolicy(MigrationTechniquePolicy.fromLegacy(policy));
	}

	public static MigrationTechniquePolicy getMigrationTechniquePolicy() {
		return policyReplicaVM;
	}

	public static void setMigrationTechniquePolicy(
		MigrationTechniquePolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException("Migration technique cannot be null");
		}
		policyReplicaVM = policy;
	}

	public static int getTravelPredictionTimeForST() {
		return travelPredictionTimeForST;
	}

	public static void setTravelPredictionTimeForST(int predictionTime) {
		if (predictionTime < 0) {
			throw new IllegalArgumentException(
				"Travel prediction time cannot be negative");
		}
		travelPredictionTimeForST = predictionTime;
	}

	/** @deprecated Use {@link #getTravelPredictionTimeForST()}. */
	@Deprecated
	public static int getTravelPredicTimeForST() {
		return getTravelPredictionTimeForST();
	}

	/** @deprecated Use {@link #setTravelPredictionTimeForST(int)}. */
	@Deprecated
	public static void setTravelPredicTimeForST(int predictionTime) {
		setTravelPredictionTimeForST(predictionTime);
	}

	public static int getMobilityPredictionError() {
		return mobilityPredictionError;
	}

	/** @deprecated Use {@link #getMobilityPredictionError()}. */
	@Deprecated
	public static int getMobilityPrecitionError() {
		return getMobilityPredictionError();
	}

	public static void setMobilityPredictionError(int predictionError) {
		if (predictionError < 0) {
			throw new IllegalArgumentException(
				"Mobility prediction error cannot be negative");
		}
		mobilityPredictionError = predictionError;
	}

	public static double getLatencyBetweenCloudlets() {
		return latencyBetweenCloudlets;
	}

	public static void setLatencyBetweenCloudlets(double latency) {
		if (!Double.isFinite(latency) || latency <= 0.0) {
			throw new IllegalArgumentException(
				"Cloudlet latency must be finite and positive");
		}
		latencyBetweenCloudlets = latency;
	}

	public static int getStepPolicy() {
		return stepPolicy;
	}

	public static void setStepPolicy(int policy) {
		stepPolicy = policy;
	}

	public static List<MobileDevice> getSmartThings() {
		return Collections.unmodifiableList(smartThings);
	}

	public static void setSmartThings(List<MobileDevice> mobileDevices) {
		smartThings = requireList(mobileDevices, "Mobile device list");
	}

	public static List<FogDevice> getServerCloudlets() {
		return Collections.unmodifiableList(serverCloudlets);
	}

	public static void setServerCloudlets(List<FogDevice> cloudlets) {
		serverCloudlets = requireList(cloudlets, "Server cloudlet list");
	}

	public static List<ApDevice> getApDevices() {
		return Collections.unmodifiableList(apDevices);
	}

	public static void setApDevices(List<ApDevice> accessPoints) {
		apDevices = requireList(accessPoints, "Access point list");
	}

	public static int getMigPointPolicy() {
		return migPointPolicy.legacyValue();
	}

	public static void setMigPointPolicy(int policy) {
		setMigrationPointPolicy(MigrationPointPolicy.fromLegacy(policy));
	}

	public static MigrationPointPolicy getMigrationPointPolicy() {
		return migPointPolicy;
	}

	public static void setMigrationPointPolicy(MigrationPointPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException(
				"Migration point policy cannot be null");
		}
		migPointPolicy = policy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy.legacyValue();
	}

	public static void setMigStrategyPolicy(int policy) {
		setMigrationStrategyPolicy(MigrationStrategyPolicy.fromLegacy(policy));
	}

	public static MigrationStrategyPolicy getMigrationStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigrationStrategyPolicy(
		MigrationStrategyPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException(
				"Migration strategy cannot be null");
		}
		migStrategyPolicy = policy;
	}

	public static int getPositionApPolicy() {
		return positionApPolicy.legacyValue();
	}

	public static void setPositionApPolicy(int policy) {
		setAccessPointLocationPolicy(LocationPolicy.fromLegacy(policy));
	}

	public static LocationPolicy getAccessPointLocationPolicy() {
		return positionApPolicy;
	}

	public static void setAccessPointLocationPolicy(LocationPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException(
				"Access-point location policy cannot be null");
		}
		positionApPolicy = policy;
	}

	public static Coordinate getCoordDevices() {
		return coordDevices;
	}

	public static void setCoordDevices(Coordinate coordinateSpace) {
		coordDevices = coordinateSpace;
	}

	public static List<FogBroker> getBrokerList() {
		return Collections.unmodifiableList(brokerList);
	}

	public static void setBrokerList(List<FogBroker> brokers) {
		brokerList = requireList(brokers, "Broker list");
	}

	public static List<String> getAppIdList() {
		return Collections.unmodifiableList(appIdList);
	}

	public static void setAppIdList(List<String> applicationIds) {
		appIdList = requireList(applicationIds, "Application ID list");
	}

	public static List<Application> getApplicationList() {
		return Collections.unmodifiableList(applicationList);
	}

	public static void setApplicationList(List<Application> applications) {
		applicationList = requireList(applications, "Application list");
	}

	private static <T> List<T> requireList(List<T> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return values;
	}

	public static int getSeed() {
		return seed;
	}

	public static void setSeed(int value) {
		if (value <= 0) {
			throw new IllegalArgumentException("Seed must be positive");
		}
		seed = value;
	}

	public static int getPositionScPolicy() {
		return positionScPolicy.legacyValue();
	}

	public static void setPositionScPolicy(int policy) {
		setServerCloudletLocationPolicy(LocationPolicy.fromLegacy(policy));
	}

	public static LocationPolicy getServerCloudletLocationPolicy() {
		return positionScPolicy;
	}

	public static void setServerCloudletLocationPolicy(LocationPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException(
				"Server-cloudlet location policy cannot be null");
		}
		positionScPolicy = policy;
	}

	public static int getMaxSmartThings() {
		return maxSmartThings;
	}

	public static void setMaxSmartThings(int maximumUsers) {
		if (maximumUsers <= 0) {
			throw new IllegalArgumentException("Number of users must be positive");
		}
		maxSmartThings = maximumUsers;
	}

	public static Path getMobilityDirectory() {
		return mobilityDirectory;
	}

	public static void setMobilityDirectory(Path directory) {
		if (directory == null) {
			throw new IllegalArgumentException(
				"Mobility directory cannot be null");
		}
		mobilityDirectory = directory;
	}

	public static Path getMobilityOrderManifest() {
		return mobilityOrderManifest;
	}

	public static void setMobilityOrderManifest(Path manifest) {
		if (manifest == null) {
			throw new IllegalArgumentException(
				"Mobility order manifest cannot be null");
		}
		mobilityOrderManifest = manifest;
	}

	public static Path getOutputDirectory() {
		return outputDirectory;
	}

	public static void setOutputDirectory(Path directory) {
		if (directory == null) {
			throw new IllegalArgumentException(
				"Run output directory cannot be null");
		}
		outputDirectory = directory;
	}

	public static RunOutputMode getOutputMode() {
		return outputMode;
	}

	public static void setOutputMode(RunOutputMode mode) {
		if (mode == null) {
			throw new IllegalArgumentException("Run output mode cannot be null");
		}
		outputMode = mode;
	}

	public static Random getRand() {
		return rand;
	}

	public static void setRand(Random random) {
		if (random == null) {
			throw new IllegalArgumentException(
				"Random generator cannot be null");
		}
		rand = random;
	}

	public static int getMaxBandwidth() {
		return maxBandwidth;
	}

	public static void setMaxBandwidth(int bandwidth) {
		if (bandwidth <= 0) {
			throw new IllegalArgumentException(
				"Network bandwidth must be positive");
		}
		maxBandwidth = bandwidth;
	}

	public static boolean isMigrationAble() {
		return migrationAble;
	}

	public static void setMigrationAble(boolean enabled) {
		migrationAble = enabled;
	}
}
