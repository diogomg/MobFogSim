package org.fog.vmmobile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;
import org.fog.entities.Tuple;
import org.fog.localization.Coordinate;
import org.fog.localization.GridGenerator;
import org.fog.localization.GridPosition;
import org.fog.localization.MapBounds;
import org.fog.localization.MobilitySample;
import org.fog.localization.MobilityTimeline;
import org.fog.placement.MobileController;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.vmmigration.BeforeMigration;
import org.fog.vmmigration.CompleteVM;
import org.fog.vmmigration.ContainerVM;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LiveMigration;
import org.fog.vmmigration.LowestDistBwSmartThingAP;
import org.fog.vmmigration.LowestDistBwSmartThingServerCloudlet;
import org.fog.vmmigration.LowestLatency;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.PrepareCompleteVM;
import org.fog.vmmigration.PrepareContainerVM;
import org.fog.vmmigration.PrepareLiveMigration;
import org.fog.vmmigration.Service;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmigration.VmMigrationTechnique;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;
import org.fog.vmmobile.policy.LocationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.policy.MovementDirection;
import org.fog.vmmobile.policy.ServiceType;

public class AppExample {
	private static int stepPolicy; // Quantity of steps in the nextStep Function
	private static List<MobileDevice> smartThings = new ArrayList<MobileDevice>();
	private static List<FogDevice> serverCloudlets = new ArrayList<>();
	private static List<ApDevice> apDevices = new ArrayList<>();
	private static List<FogBroker> brokerList = new ArrayList<>();
	private static List<String> appIdList = new ArrayList<>();
	private static List<Application> applicationList = new ArrayList<>();

	private static boolean migrationAble;

	private static MigrationPointPolicy migPointPolicy = MigrationPointPolicy.FIXED;
	private static MigrationStrategyPolicy migStrategyPolicy =
		MigrationStrategyPolicy.LOWEST_LATENCY;
	private static LocationPolicy positionApPolicy = LocationPolicy.FIXED;
	private static LocationPolicy positionScPolicy = LocationPolicy.FIXED;
	private static MigrationTechniquePolicy policyReplicaVM =
		MigrationTechniquePolicy.COMPLETE_VM;
	private static int travelPredicTimeForST; // in seconds
	private static int mobilityPrecitionError;// in meters
	private static double latencyBetweenCloudlets;
	private static int maxBandwidth;
	private static int maxSmartThings;
	private static Path mobilityDirectory = Paths.get("input");
	private static Path mobilityOrderManifest = mobilityDirectory.resolve("inputOrder.csv");
	private static Path outputDirectory = Paths.get("runs", "unconfigured");
	private static RunOutputMode outputMode = RunOutputMode.SUMMARY;
	private static Coordinate coordDevices;
	private static int seed;
	private static Random rand;
	static final boolean CLOUD = true;

	static final int numOfDepts = 1;
	static final int numOfMobilesPerDept = 4;
	static final double EEG_TRANSMISSION_TIME = 10;

	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 * @author Diogo M Gonçalves
	 * @throws Exception
	 */

	public static void main(String[] args) throws Exception {
		run(args);
	}

	/** Runs one isolated simulation and returns its immutable final result. */
	public static SimulationRunResult run(String[] args) throws Exception {
		return run(SimulationConfig.parse(args));
	}

	/** Runs one already validated configuration for an embedded caller. */
	public static SimulationRunResult run(SimulationConfig configuration)
		throws Exception {
		try (SimulationContext context = SimulationContext.open(configuration)) {
			try {
				executeSimulation(configuration);
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

	private static void executeSimulation(SimulationConfig configuration)
		throws Exception {
		SimulationContext context = SimulationContext.requireCurrent();
		SimulationServices services = context.getServices();
		/*
		 *  Simulation steps
		 *  
		 *  First step: Follow the following steps
		 *  Second step: Provide the user mobility dataset in the input directory
		 *  Third step: Initialize the CloudSim package. It should be called
		 *  before creating any entities.
		 *  Fourth step: Create all devices
		 *  Fifth step: Configure users as pending until their mobility entry time
		 *  Sixth step: Configure the network
		 *  Seventh step: Start the simulation; each user creates its broker, VM,
		 *  and application after its scheduled entry and wireless association
		 *  Final step: Print results when simulation is over
		 *  
		 *  Example parameters
		 *  
		 *  1 290538 0 0 10 11 0 61 0 0 0 60,40 70,30 1 2.5 2 summary
		 *  
		 *  First parameter: 0/1 -> migrations are denied or allowed
		 *  Second parameter: Positive Integer -> seed to be used in the random numbers generation
		 *  Third parameter: 0/1 -> Migration point approach is fixed (0) or based on the user speed (1)
		 *  Fourth parameter: 0/1/2 -> Migration strategy approach is based on the lowest latency (0), lowest distance between the user and cloudlet (1), or lowest distance between user and Access Point (2)
		 *  Fifth parameter: Positive Integer -> Number of users
		 *  Sixth parameter: Positive Integer -> Base Network Bandwidth between cloudlets
		 *  Seventh parameter: 0/1/2 -> Migration policy based on Complete VM/Cold migration (0), Complete Container migration (1), or Container Live Migration (2)
		 *  Eighth parameter: Positive number -> Base Network Latency between cloudlets
		 *  Ninth parameter: Non Negative Integer -> User Mobility prediction, in seconds
		 *  Tenth parameter: Non Negative Integer -> User Mobility prediction inaccuracy, in meters
		 *  Eleventh parameter: Slice scope: 0 transport network only,
		 *  1 wireless network only, or 2 end-to-end. The default is 2 when omitted.
		 *  Twelfth parameter: Comma-separated percentages of users assigned to each slice.
		 *  Thirteenth parameter: Comma-separated network-slice bandwidth percentages.
		 *  Fourteenth parameter: 0 for fixed slices or 1 to borrow idle slice capacity.
		 *  Fifteenth parameter: Dynamic slice reallocation delay in seconds
		 *  (default: 2).
		 *  Sixteenth parameter: 0 edge servers only, 1 end devices only, 2 hybrid.
		 *  Seventeenth parameter: summary, full, or none output mode
		 *  (default: summary).
		 */

		Log.disable();
		SimulationEventSink events = context.getEventSink();
		events.summary(configuration::toSummaryLine);

		int numUser = 1; // number of cloud users
		Calendar calendar = Calendar.getInstance();
		boolean traceFlag = false; // mean trace events
		CloudSim.init(numUser, calendar, traceFlag);
		setAccessPointLocationPolicy(LocationPolicy.FIXED);
		setServerCloudletLocationPolicy(LocationPolicy.FIXED);
		setStepPolicy(1);
		applySimulationConfiguration(configuration);

		/**
		 * STEP 2: CREATE ALL DEVICES -> example from: CloudSim - example5.java
		 **/

		CloudSim.EntityRegistrationCheckpoint topologyCheckpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			/* It is creating Access Points. It makes according positionApPolicy */
			if (positionApPolicy == LocationPolicy.FIXED) {
				// it creates the Access Point according coordDevices' size
				addApDevicesFixed(apDevices, coordDevices);
			} else {
				// it creates the Access Points
				for (int i = 0; i < MaxAndMin.MAX_AP_DEVICE; i++) {
					addApDevicesRandon(apDevices, coordDevices, i);
				}
			}

			/* It is creating Server Cloudlets. */
			if (getServerCloudletLocationPolicy() == LocationPolicy.FIXED) {
				addServerCloudlet(serverCloudlets, coordDevices);
			} else {
				// it creates the ServerCloudlets
				for (int i = 0; i < MaxAndMin.MAX_SERVER_CLOUDLET; i++) {
					addServerCloudlet(serverCloudlets, coordDevices, i);
				}
			}
			services.getTopology().createTransportNetwork(getServerCloudlets(),
				getLatencyBetweenCloudlets(), getRand());

			/* It is creating Smart Things. */
			int[] userSliceAssignments = NetworkSlicing.getUserSliceAssignments(
				getMaxSmartThings());
			List<MobileDevice> plannedSmartThings = new ArrayList<MobileDevice>();
			for (int i = 0; i < getMaxSmartThings(); i++) {
				addSmartThing(plannedSmartThings, coordDevices, i);
				plannedSmartThings.get(i)
					.setNetworkSliceId(userSliceAssignments[i]);
			}
			smartThings.addAll(plannedSmartThings);

			services.getTopology().loadMobility(getMobilityDirectory(),
				getMobilityOrderManifest(), getSmartThings());
			MobileUserRegistration.preparePendingUsers(getSmartThings());

			services.getTopology().connectAccessPoints(getServerCloudlets(),
				getApDevices(), getRand());
			SimulationContext.requireCurrent().recordInitialTopologySize();
		}
		catch (Exception error) {
			apDevices.clear();
			serverCloudlets.clear();
			smartThings.clear();
			NetworkTopology.reset();
			CloudSim.restoreEntityRegistrations(topologyCheckpoint);
			throw new SimulationBuildException(
				"Could not assemble the simulation topology", error);
		}

		/**
		 * STEP 3: CREATE CONTROLLER. Brokers, VMs, and applications are created
		 * when each user's scheduled entry event occurs.
		 **/

		MobileController mobileController = null;
		// initializing a module mapping
		ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

		mobileController = new MobileController("MobileController",
			serverCloudlets, apDevices, smartThings,
			brokerList, moduleMapping, getMigrationPointPolicy(),
			getMigrationStrategyPolicy(), getStepPolicy(), getCoordDevices(),
			getSeed(), isMigrationAble(), services);
		TimeKeeper.getInstance().setSimulationStartTime(
			SimulationContext.requireCurrent().getClock().wallTimeMillis());
		MyStatistics.getInstance().setSeed(getSeed());
		for (MobileDevice st : getSmartThings()) {
			if (getMigrationPointPolicy() == MigrationPointPolicy.FIXED) {
				if (getMigrationStrategyPolicy() == MigrationStrategyPolicy.LOWEST_LATENCY) {

					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId() + ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
							+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY");
				} else if (getMigrationStrategyPolicy()
					== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_ACCESS_POINT) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigrationStrategyPolicy()
					== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_SERVER_CLOUDLET) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/"+ st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId() + ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET");
				}
			} else if (getMigrationPointPolicy() == MigrationPointPolicy.SPEED) {
				if (getMigrationStrategyPolicy() == MigrationStrategyPolicy.LOWEST_LATENCY) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY");

				} else if (getMigrationStrategyPolicy()
					== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_ACCESS_POINT) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/"+ st.getMyId()
						+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId(),st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigrationStrategyPolicy()
					== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_SERVER_CLOUDLET) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/"+ st.getMyId()
						+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId(),st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET");
				}
			}
			MyStatistics.getInstance().putLantencyFileName("Time-latency", st.getMyId());
			MyStatistics.getInstance().initialiseLatencyCounter(st.getMyId());
		}

		for (MobileDevice st : getSmartThings()) {
			events.detail("AppExample", () ->
				st.getName() + "- X: " + st.getCoord().getCoordX() + " Y: "
					+ st.getCoord().getCoordY() + " Direction: "
					+ st.getDirection() + " Speed: " + st.getSpeed()
					+ " EntryTime: " + st.getStartTravelTime() + " seconds");
		}
		context.startProgress(expectedSimulationEndTime(getSmartThings()));
		events.detail("AppExample", () -> "Started at "
			+ Calendar.getInstance().getTime());
		CloudSim.startSimulation();
		context.completeProgress();
		events.detail("AppExample", () -> "Simulation over");
	}

	private static double expectedSimulationEndTime(
		List<MobileDevice> mobileDevices) {
		double latestTraceTime = 0.0;
		for (MobileDevice mobileDevice : mobileDevices) {
			List<MobilitySample> path = mobileDevice.getMobilityPath();
			if (!path.isEmpty()) {
				latestTraceTime = Math.max(latestTraceTime,
					MobilityTimeline.toSimulationTime(
						path.get(path.size() - 1).getTimeSeconds()));
			}
		}
		if (latestTraceTime <= 0.0) {
			return MaxAndMin.MAX_SIMULATION_TIME;
		}
		return Math.min(latestTraceTime, MaxAndMin.MAX_SIMULATION_TIME);
	}

	static void configureSimulationParameters(String[] args) {
		applySimulationConfiguration(SimulationConfig.parse(args));
	}

	private static void applySimulationConfiguration(SimulationConfig configuration) {
		setMigrationAble(configuration.isMigrationEnabled());
		setSeed(configuration.getSeed());
		SimulationContext context = SimulationContext.currentOrNull();
		setRand(context == null
			? new Random(configuration.getSeed() * Integer.MAX_VALUE)
			: context.random("application"));
		setMigrationPointPolicy(configuration.getMigrationPoint());
		setMigrationStrategyPolicy(configuration.getMigrationStrategy());
		setMaxSmartThings(configuration.getMaximumUsers());
		setMaxBandwidth(configuration.getMaximumBandwidth());
		setMigrationTechniquePolicy(configuration.getMigrationTechnique());
		setLatencyBetweenCloudlets(configuration.getCloudletLatency());
		setTravelPredicTimeForST(configuration.getTravelPredictionTime());
		setMobilityPredictionError(configuration.getMobilityPredictionError());
		NetworkSlicing.applyConfiguration(configuration.getSlicingConfiguration());
		VmDestinationPolicy.configure(configuration.getVmDestination());
		setMobilityDirectory(configuration.getMobilityDirectory());
		setMobilityOrderManifest(configuration.getMobilityOrderManifest());
		setOutputDirectory(configuration.getOutputDirectory());
		setOutputMode(configuration.getOutputMode());
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

	private static void addApDevicesFixed(List<ApDevice> apDevices,
		Coordinate coordDevices) {
		addApDevicesFixed(apDevices, coordDevices, MapBounds.defaults());
	}

	static void addApDevicesFixed(List<ApDevice> apDevices,
		Coordinate coordDevices, MapBounds bounds) {
		int i = 0;
		GridGenerator grid = new GridGenerator(bounds);
		for (GridPosition position : grid.fixedPositions(
			GridGenerator.spacingForCoverage(MaxAndMin.AP_COVERAGE))) {
			ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
				position.getX(), position.getY(), i// ap.set//id
				, 100 * 1024 * 1024// downLinkBandwidth - 100Mbits
				, 200// engergyConsuption
				, MaxAndMin.MAX_ST_IN_AP// maxSmartThing
				, 100 * 1024 * 1024// upLinkBandwidth - 100Mbits
				, 4// upLinkLatency
			);
			apDevices.add(i, ap);
			i++;
		}
		LogMobile.debug("AppExample.java", "Total of accessPoints: " + i);

	}

	private static void addApDevicesRandon(List<ApDevice> apDevices,
		Coordinate coordDevices, int i) {
		addApDevicesRandom(apDevices, coordDevices, i, MapBounds.defaults());
	}

	static void addApDevicesRandom(List<ApDevice> apDevices,
		Coordinate coordDevices, int i, MapBounds bounds) {
		GridPosition position = new GridGenerator(bounds).randomPosition(getRand());
		ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
			position.getX(), position.getY(), i// id
			, 100 * 1024 * 1024// downLinkBandwidth - 100 Mbits
			, 200// engergyConsuption
			, MaxAndMin.MAX_ST_IN_AP// maxSmartThing
			, 100 * 1024 * 1024// upLinkBandwidth 100 Mbits
			, 4// upLinkLatency
		);
		apDevices.add(i, ap);
	}

	public static void addSmartThing(List<MobileDevice> smartThing,
		Coordinate coordDevices, int i) {
		int listSizeBefore = smartThing.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildSmartThing(smartThing, coordDevices, i);
		}
		catch (RuntimeException error) {
			truncateList(smartThing, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildSmartThing(List<MobileDevice> smartThing,
		Coordinate coordDevices, int i) {

		int coordX = 0, coordY = 0;
		MovementDirection direction;
		int speed;
		direction = MovementDirection.fromLegacy(
			getRand().nextInt(MaxAndMin.MAX_DIRECTION - 1) + 1);
		speed = getRand().nextInt(MaxAndMin.MAX_SPEED - 1) + 1;
		/*************** Start set of Mobile Sensors ****************/
		VmMigrationTechnique migrationTechnique = null;
		BeforeMigration beforeMigration = null;

		if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.COMPLETE_VM) {
			migrationTechnique = new CompleteVM(getMigrationPointPolicy());
			beforeMigration = new PrepareCompleteVM();
		} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.CONTAINER_VM) {
			migrationTechnique = new ContainerVM(getMigrationPointPolicy());
			beforeMigration = new PrepareContainerVM();
		} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.LIVE_MIGRATION) {
			migrationTechnique = new LiveMigration(getMigrationPointPolicy());
			beforeMigration = new PrepareLiveMigration();
		}

		DeterministicDistribution distribution0 = new DeterministicDistribution(
			EEG_TRANSMISSION_TIME);// +(i*getRand().nextDouble()));

		Set<MobileSensor> sensors = new HashSet<>();

		MobileSensor sensor = new MobileSensor("Sensor" + i // Tuple's name
		, "EEG" + i // Tuple's type
		, i // User Id
			, "MyApp_vr_game" + i // app's name
			, distribution0);
		sensors.add(sensor);

		/*************** End set of Mobile Sensors ****************/

		/*************** Start set of Mobile Actuators ****************/

		MobileActuator actuator0 = new MobileActuator("Actuator" + i, i,
			"MyApp_vr_game" + i, "DISPLAY" + i);

		Set<MobileActuator> actuators = new HashSet<>();
		actuators.add(actuator0);

		/*************** End set of Mobile Actuators ****************/

		/*************** Start MobileDevice Configurations ****************/

		FogLinearPowerModel powerModel = new FogLinearPowerModel(87.53d,
			82.44d);// 10//maxPower

		List<Pe> peList = new ArrayList<>();
		int mips = 46533;
		// 3. Create PEs and add these into a list.
		// need to storage Pe id and MIPS Rating - to CloudSim
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

		int hostId = FogUtils.generateEntityId();
		long storage = 512 * 1024;
		// host storage
		int bw = 1000 * 1024 * 1024;
		int ram = 1024 * 16;
		// To the hardware's characteristics (MobileDevice) - to CloudSim
		PowerHost host = new PowerHost(
			hostId, new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bw), storage, peList,
			new StreamOperatorScheduler(peList), powerModel);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Android"; // operating system
		String vmm = "empty";// Empty
		double vmSize = 4;
		double time_zone = 10.0; // time zone this resource located
		double cost = 1.0; // the cost of using processing in this resource
		double costPerMem = 0.005; // the cost of using memory in this resource
		double costPerStorage = 0.0001; // the cost of using storage in this resource
		double costPerBw = 0.001; // the cost of using bw in this resource
		// we are not adding SAN devices by now
		LinkedList<Storage> storageList = new LinkedList<Storage>();

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
			arch, os, vmm, host, time_zone, cost, costPerMem,
			costPerStorage, costPerBw);

		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(
			hostList);

		MobileDevice st = null;

		float maxServiceValue = getRand().nextFloat() * 100;
		try {
			st = new MobileDevice("SmartThing" + Integer.toString(i),
				characteristics, vmAllocationPolicy
				, storageList, 2// schedulingInterval
				, 1 * 1024 * 1024// uplinkBandwidth - 1 Mbit
				, 2 * 1024 * 1024// downlinkBandwidth - 2 Mbits
				, 2// uplinkLatency
				, 0.01// mipsPer..
				, coordX, coordY, i// id
				, direction, speed, maxServiceValue, vmSize,
				migrationTechnique);
			st.setTempSimulation(0);
			st.setTimeFinishDeliveryVm(-1);
			st.setTimeFinishHandoff(0);
			st.setMigrationTechniquePolicy(getMigrationTechniquePolicy());
			st.setBeforeMigrate(beforeMigration);
			st.setSensors(sensors);
			st.setActuators(actuators);
			st.setTravelPredicTime(getTravelPredicTimeForST());
			st.setMobilityPredictionError(getMobilityPrecitionError());
			smartThing.add(i, st);
		} catch (Exception e) {
			throw new SimulationBuildException("Could not create mobile user " + i,
				e);
		}
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices, int i) {
		addServerCloudlet(serverCloudlets, coordDevices, i, MapBounds.defaults());
	}

	static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices, int i, MapBounds bounds) {
		int listSizeBefore = serverCloudlets.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildRandomServerCloudlet(serverCloudlets, coordDevices, i, bounds);
		}
		catch (RuntimeException error) {
			truncateList(serverCloudlets, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildRandomServerCloudlet(
		List<FogDevice> serverCloudlets, Coordinate coordDevices, int i,
		MapBounds bounds) {

		DecisionMigration migrationStrategy;
		if (getMigrationStrategyPolicy() == MigrationStrategyPolicy.LOWEST_LATENCY) {
			migrationStrategy = new LowestLatency(getServerCloudlets(),
				getApDevices(), getMigrationPointPolicy(), getMigrationTechniquePolicy());
		} else if (getMigrationStrategyPolicy()
			== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_SERVER_CLOUDLET) {
			migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
				getServerCloudlets(), getApDevices(), getMigrationPointPolicy(),
				getMigrationTechniquePolicy());
		} else { // Policies.LOWEST_DIST_BW_SMARTTING_AP
			migrationStrategy = new LowestDistBwSmartThingAP(
				getServerCloudlets(), getApDevices(), getMigrationPointPolicy(),
				getMigrationTechniquePolicy());
		}

		BeforeMigration beforeMigration = null;
		if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.COMPLETE_VM) {
			beforeMigration = new PrepareCompleteVM();
		} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.CONTAINER_VM) {
			beforeMigration = new PrepareContainerVM();
		} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.LIVE_MIGRATION) {
			beforeMigration = new PrepareLiveMigration();
		}

		FogLinearPowerModel powerModel = new FogLinearPowerModel(107.339d, 83.433d);

		// CloudSim Pe (Processing Element) class represents CPU unit, defined in terms of Millions Instructions Per Second (MIPS) rating
		List<Pe> peList = new ArrayList<>();
		int mips = 3234;
		// 3. Create PEs and add these into a list.
		// need to store Pe id and MIPS Rating - to CloudSim
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

		int hostId = FogUtils.generateEntityId();
		long storage = 16 * 1024 * 1024;// host storage
		int bw = 1000 * 1024 * 1024;
		int ram = 1024;// host memory (MB)
		// To the hardware's characteristics (MobileDevice) - to CloudSim
		PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bw), storage, peList,
			new StreamOperatorScheduler(peList), powerModel);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Empty";// Empty
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		// we are not adding SAN devices by now
		LinkedList<Storage> storageList = new LinkedList<Storage>();
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
			arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);

		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);
		FogDevice sc = null;
		Service serviceOffer = new Service();
		serviceOffer.setType(getRand().nextInt(10000) % MaxAndMin.MAX_SERVICES);
		if (serviceOffer.getServiceType() == ServiceType.HYBRID
			|| serviceOffer.getServiceType() == ServiceType.PUBLIC) {
			serviceOffer.setValue(getRand().nextFloat() * 10);
		} else {
			serviceOffer.setValue(0);
		}
		try {
			GridPosition position = new GridGenerator(bounds)
				.randomPosition(getRand());
			double maxBandwidth = getMaxBandwidth() * 1024.0 * 1024.0;
			double minBandwidth = (getMaxBandwidth() - 1) * 1024.0 * 1024.0;
			double upLinkRandom = minBandwidth
				+ (maxBandwidth - minBandwidth) * getRand().nextDouble();
			double downLinkRandom = minBandwidth
				+ (maxBandwidth - minBandwidth) * getRand().nextDouble();

			sc = new FogDevice("ServerCloudlet" + Integer.toString(i) // name
			, characteristics, vmAllocationPolicy// vmAllocationPolicy
				, storageList, 10// schedulingInterval
				, upLinkRandom// uplinkBandwidth
				, downLinkRandom// downlinkBandwidth
				, 4// rand.nextDouble()//uplinkLatency
				, 0.01// mipsPer..
				, position.getX(), position.getY(), i, serviceOffer, migrationStrategy,
				getMigrationTechniquePolicy(), beforeMigration);
			serverCloudlets.add(i, sc);
		} catch (Exception e) {
			throw new SimulationBuildException(
				"Could not create random server cloudlet " + i, e);
		}
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices) {
		addServerCloudlet(serverCloudlets, coordDevices, MapBounds.defaults());
	}

	static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices, MapBounds bounds) {
		int listSizeBefore = serverCloudlets.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildFixedServerCloudlets(serverCloudlets, coordDevices, bounds);
		}
		catch (RuntimeException error) {
			truncateList(serverCloudlets, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildFixedServerCloudlets(
		List<FogDevice> serverCloudlets, Coordinate coordDevices,
		MapBounds bounds) {
		int i = 0;
		GridGenerator grid = new GridGenerator(bounds);

		for (GridPosition position : grid.fixedPositions(
			GridGenerator.spacingForCoverage(MaxAndMin.CLOUDLET_COVERAGE))) {
				int coordX = position.getX();
				int coordY = position.getY();
				DecisionMigration migrationStrategy;
				if (getMigrationStrategyPolicy() == MigrationStrategyPolicy.LOWEST_LATENCY) {
					migrationStrategy = new LowestLatency(getServerCloudlets(),
						getApDevices(), getMigrationPointPolicy(), getMigrationTechniquePolicy());
				} else if (getMigrationStrategyPolicy()
					== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_SERVER_CLOUDLET) {
					migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
						getServerCloudlets(), getApDevices(),
						getMigrationPointPolicy(), getMigrationTechniquePolicy());
				} else { // LOWEST_DIST_BW_SMARTTING_AP
					migrationStrategy = new LowestDistBwSmartThingAP(
						getServerCloudlets(), getApDevices(),
						getMigrationPointPolicy(), getMigrationTechniquePolicy());
				}

				BeforeMigration beforeMigration = null;
				if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.COMPLETE_VM) {
					beforeMigration = new PrepareCompleteVM();
				} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.CONTAINER_VM) {
					beforeMigration = new PrepareContainerVM();
				} else if (getMigrationTechniquePolicy() == MigrationTechniquePolicy.LIVE_MIGRATION) {
					beforeMigration = new PrepareLiveMigration();
				}

				FogLinearPowerModel powerModel = new FogLinearPowerModel(107.339d, 83.433d);

				// CloudSim Pe (Processing Element) class represents CPU unit,
				// defined in terms of Millions Instructions Per Second (MIPS) rating
				List<Pe> peList = new ArrayList<>();
				int mips = 3234;
				// 3. Create PEs and add these into a list.
				// need to store Pe id and MIPS Rating - to CloudSim
				peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

				int hostId = FogUtils.generateEntityId();
				long storage = 16 * 1024 * 1024;// host storage
				int bw = 1000 * 1024 * 1024;
				int ram = 1024;// host memory (MB)
				// To the hardware's characteristics (MobileDevice) - to CloudSim
				PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram),
					new BwProvisionerOverbooking(bw), storage, peList,
					new StreamOperatorScheduler(peList), powerModel);

				List<Host> hostList = new ArrayList<Host>();
				hostList.add(host);

				String arch = "x86"; // system architecture
				String os = "Linux"; // operating system
				String vmm = "Empty";// Empty
				double time_zone = 10.0; // time zone this resource located
				double cost = 3.0; // the cost of using processing in this resource
				double costPerMem = 0.05; // the cost of using memory in this resource
				double costPerStorage = 0.001; // the cost of using storage in this resource
				double costPerBw = 0.0; // the cost of using bw in this resource
				// we are not adding SAN devices by now
				LinkedList<Storage> storageList = new LinkedList<Storage>();
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
					arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);

				AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);
				FogDevice sc = null;
				Service serviceOffer = new Service();
				serviceOffer.setType(getRand().nextInt(10000) % MaxAndMin.MAX_SERVICES);
				if (serviceOffer.getServiceType() == ServiceType.HYBRID
					|| serviceOffer.getServiceType() == ServiceType.PUBLIC) {
					serviceOffer.setValue(getRand().nextFloat() * 10);
				} else {
					serviceOffer.setValue(0);
				}
				try {
					double maxBandwidth = getMaxBandwidth() * 1024.0 * 1024.0;
					double minBandwidth = (getMaxBandwidth() - 1) * 1024.0 * 1024.0;
					double upLinkRandom = minBandwidth + (maxBandwidth - minBandwidth)
						* getRand().nextDouble();
					double downLinkRandom = minBandwidth + (maxBandwidth - minBandwidth)
						* getRand().nextDouble();
					sc = new FogDevice("ServerCloudlet" + Integer.toString(i) // name
					, characteristics, vmAllocationPolicy// vmAllocationPolicy
						, storageList, 10// schedulingInterval
						, upLinkRandom// uplinkBandwidth
						, downLinkRandom// downlinkBandwidth
						, 4//uplinkLatency
						, 0.01// mipsPer..
						, coordX, coordY, i, serviceOffer,
						migrationStrategy, getMigrationTechniquePolicy(),
						beforeMigration);
					serverCloudlets.add(i, sc);
					sc.setParentId(-1);
				} catch (Exception e) {
					throw new SimulationBuildException(
						"Could not create fixed server cloudlet " + i
							+ " at (" + coordX + ", " + coordY + ")", e);
				}
				i++;
		}
		LogMobile.debug("AppExample.java", "Total of serverCloudlets: " + i);
	}

	private static <T> void truncateList(List<T> values, int targetSize) {
		while (values.size() > targetSize) {
			values.remove(values.size() - 1);
		}
	}

	@SuppressWarnings("unused")
	private static Application createApplication(String appId, int userId, int myId) {

		// creates an empty application model (empty directed graph)
		Application application = Application.createApplication(appId, userId);
		// adding module Client to the application model
		application.addAppModule("client" + myId, 10);
		// adding module Concentration Calculator to the application model
		application.addAppModule("concentration_calculator" + myId, 10);
		// adding module Connector to the application model
		application.addAppModule("connector" + myId, 10);

		/*
		 * Connecting the application modules (vertices) in the application
		 * model (directed graph) with edges
		 */
		if (EEG_TRANSMISSION_TIME == 10)
			// adding edge from EEG (sensor) to Client module carrying tuples of type EEG
			application.addAppEdge("EEG" + myId, "client" + myId, 2000, 500,
				"EEG" + myId, Tuple.UP, AppEdge.SENSOR);
		else
			application.addAppEdge("EEG" + myId, "client" + myId, 3000, 500,
				"EEG" + myId, Tuple.UP, AppEdge.SENSOR);

		// adding edge from Client to Concentration Calculator module carrying
		// tuples of type _SENSOR
		application.addAppEdge("client" + myId, "concentration_calculator" + myId,
			3500, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE);
		// adding periodic edge (period=1000ms) from Concentration Calculator to
		//Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("concentration_calculator" + myId, "connector" + myId,
			1000, 1000, 1000, "PLAYER_GAME_STATE", Tuple.UP, AppEdge.MODULE);

		// adding edge from Concentration Calculator to Client module carrying 
		// tuples of type CONCENTRATION
		application.addAppEdge("concentration_calculator" + myId, "client" + myId,
			14, 500, "CONCENTRATION", Tuple.DOWN, AppEdge.MODULE);
		// adding periodic edge (period=1000ms) from Connector to Client module
		// carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge("connector" + myId, "client" + myId, 1000, 28,
			1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE);
		// adding edge from Client module to Display (actuator) carrying tuples
		// of type SELF_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 1000, 500,
			"SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR); 
		// adding edge from Client module to Display (actuator) carrying tuples
		// of type GLOBAL_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 1000, 500,
			"GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);

		/*
		 * Defining the input-output relationships (represented by selectivity)
		 * of the application modules.
		 */
		// 0.9 tuples of type _SENSOR are emitted by Client module per incoming
		// tuple of type EEG
		application.addTupleMapping("client" + myId, "EEG" + myId, "_SENSOR",
			new FractionalSelectivity(0.9));
		// 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per
		// incoming tuple of type CONCENTRATION
		application.addTupleMapping("client" + myId, "CONCENTRATION",
			"SELF_STATE_UPDATE", new FractionalSelectivity(1.0));
		 // 1.0 tuples of type CONCENTRATION are emitted by Concentration
		// Calculator module per incoming tuple of type _SENSOR
		application.addTupleMapping("concentration_calculator" + myId,
			"_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0));
		// 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module
		// per incoming tuple of type GLOBAL_GAME_STATE
		application.addTupleMapping("client" + myId, "GLOBAL_GAME_STATE",
			"GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); 

		/*
		 * Defining application loops to monitor the latency of. Here, we add
		 * only one loop for monitoring : EEG(sensor) -> Client -> Concentration
		 * Calculator -> Client -> DISPLAY (actuator)
		 */
		final String client = "client" + myId;
		final String concentration = "concentration_calculator" + myId;
		final String eeg = "EEG" + myId;
		final String display = "DISPLAY" + myId;
		final AppLoop loop1 = new AppLoop(new ArrayList<String>() {
			{
				add(eeg);
				add(client);
				add(concentration);
				add(client);
				add(display);
			}
		});
		List<AppLoop> loops = new ArrayList<AppLoop>() {
			{
				add(loop1);
			}
		};
		application.setLoops(loops);

		return application;
	}

	public static int getPolicyReplicaVM() {
		return policyReplicaVM.legacyValue();
	}

	public static void setPolicyReplicaVM(int policyReplicaVM) {
		setMigrationTechniquePolicy(
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM));
	}

	public static MigrationTechniquePolicy getMigrationTechniquePolicy() {
		return policyReplicaVM;
	}

	public static void setMigrationTechniquePolicy(
		MigrationTechniquePolicy policyReplicaVM) {
		if (policyReplicaVM == null) {
			throw new IllegalArgumentException("Migration technique cannot be null");
		}
		AppExample.policyReplicaVM = policyReplicaVM;
	}

	public static int getTravelPredicTimeForST() {
		return travelPredicTimeForST;
	}

	public static void setTravelPredicTimeForST(int travelPredicTimeForST) {
		if (travelPredicTimeForST < 0) {
			throw new IllegalArgumentException("Travel prediction time cannot be negative");
		}
		AppExample.travelPredicTimeForST = travelPredicTimeForST;
	}

	public static int getMobilityPrecitionError() {
		return mobilityPrecitionError;
	}

	public static void setMobilityPredictionError(int mobilityPrecitionError) {
		if (mobilityPrecitionError < 0) {
			throw new IllegalArgumentException("Mobility prediction error cannot be negative");
		}
		AppExample.mobilityPrecitionError = mobilityPrecitionError;
	}

	public static double getLatencyBetweenCloudlets() {
		return latencyBetweenCloudlets;
	}

	public static void setLatencyBetweenCloudlets(double latencyBetweenCloudlets) {
		if (!Double.isFinite(latencyBetweenCloudlets)
			|| latencyBetweenCloudlets <= 0.0) {
			throw new IllegalArgumentException(
				"Cloudlet latency must be finite and positive");
		}
		AppExample.latencyBetweenCloudlets = latencyBetweenCloudlets;
	}

	public static int getStepPolicy() {
		return stepPolicy;
	}

	public static void setStepPolicy(int stepPolicy) {
		AppExample.stepPolicy = stepPolicy;
	}

	public static List<MobileDevice> getSmartThings() {
		return Collections.unmodifiableList(smartThings);
	}

	public static void setSmartThings(List<MobileDevice> smartThings) {
		AppExample.smartThings = requireList(smartThings, "Mobile device list");
	}

	public static List<FogDevice> getServerCloudlets() {
		return Collections.unmodifiableList(serverCloudlets);
	}

	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		AppExample.serverCloudlets = requireList(serverCloudlets,
			"Server cloudlet list");
	}

	public static List<ApDevice> getApDevices() {
		return Collections.unmodifiableList(apDevices);
	}

	public static void setApDevices(List<ApDevice> apDevices) {
		AppExample.apDevices = requireList(apDevices, "Access point list");
	}

	public static int getMigPointPolicy() {
		return migPointPolicy.legacyValue();
	}

	public static void setMigPointPolicy(int migPointPolicy) {
		setMigrationPointPolicy(MigrationPointPolicy.fromLegacy(migPointPolicy));
	}

	public static MigrationPointPolicy getMigrationPointPolicy() {
		return migPointPolicy;
	}

	public static void setMigrationPointPolicy(
		MigrationPointPolicy migPointPolicy) {
		if (migPointPolicy == null) {
			throw new IllegalArgumentException("Migration point policy cannot be null");
		}
		AppExample.migPointPolicy = migPointPolicy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy.legacyValue();
	}

	public static void setMigStrategyPolicy(int migStrategyPolicy) {
		setMigrationStrategyPolicy(
			MigrationStrategyPolicy.fromLegacy(migStrategyPolicy));
	}

	public static MigrationStrategyPolicy getMigrationStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigrationStrategyPolicy(
		MigrationStrategyPolicy migStrategyPolicy) {
		if (migStrategyPolicy == null) {
			throw new IllegalArgumentException("Migration strategy cannot be null");
		}
		AppExample.migStrategyPolicy = migStrategyPolicy;
	}

	public static int getPositionApPolicy() {
		return positionApPolicy.legacyValue();
	}

	public static void setPositionApPolicy(int positionApPolicy) {
		setAccessPointLocationPolicy(LocationPolicy.fromLegacy(positionApPolicy));
	}

	public static LocationPolicy getAccessPointLocationPolicy() {
		return positionApPolicy;
	}

	public static void setAccessPointLocationPolicy(LocationPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException("Access-point location policy cannot be null");
		}
		AppExample.positionApPolicy = policy;
	}

	public static Coordinate getCoordDevices() {
		return coordDevices;
	}

	public static void setCoordDevices(Coordinate coordDevices) {
		AppExample.coordDevices = coordDevices;
	}

	public static List<FogBroker> getBrokerList() {
		return Collections.unmodifiableList(brokerList);
	}

	public static void setBrokerList(List<FogBroker> brokerList) {
		AppExample.brokerList = requireList(brokerList, "Broker list");
	}

	public static List<String> getAppIdList() {
		return Collections.unmodifiableList(appIdList);
	}

	public static void setAppIdList(List<String> appIdList) {
		AppExample.appIdList = requireList(appIdList, "Application ID list");
	}

	public static List<Application> getApplicationList() {
		return Collections.unmodifiableList(applicationList);
	}

	public static void setApplicationList(List<Application> applicationList) {
		AppExample.applicationList = requireList(applicationList,
			"Application list");
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

	public static void setSeed(int seed) {
		if (seed <= 0) {
			throw new IllegalArgumentException("Seed must be positive");
		}
		AppExample.seed = seed;
	}

	public static int getPositionScPolicy() {
		return positionScPolicy.legacyValue();
	}

	public static void setPositionScPolicy(int positionScPolicy) {
		setServerCloudletLocationPolicy(LocationPolicy.fromLegacy(positionScPolicy));
	}

	public static LocationPolicy getServerCloudletLocationPolicy() {
		return positionScPolicy;
	}

	public static void setServerCloudletLocationPolicy(LocationPolicy policy) {
		if (policy == null) {
			throw new IllegalArgumentException("Server-cloudlet location policy cannot be null");
		}
		AppExample.positionScPolicy = policy;
	}

	public static int getMaxSmartThings() {
		return maxSmartThings;
	}

	public static void setMaxSmartThings(int maxSmartThings) {
		if (maxSmartThings <= 0) {
			throw new IllegalArgumentException("Number of users must be positive");
		}
		AppExample.maxSmartThings = maxSmartThings;
	}

	public static Path getMobilityDirectory() {
		return mobilityDirectory;
	}

	public static void setMobilityDirectory(Path mobilityDirectory) {
		if (mobilityDirectory == null) {
			throw new IllegalArgumentException("Mobility directory cannot be null");
		}
		AppExample.mobilityDirectory = mobilityDirectory;
	}

	public static Path getMobilityOrderManifest() {
		return mobilityOrderManifest;
	}

	public static void setMobilityOrderManifest(Path mobilityOrderManifest) {
		if (mobilityOrderManifest == null) {
			throw new IllegalArgumentException(
				"Mobility order manifest cannot be null");
		}
		AppExample.mobilityOrderManifest = mobilityOrderManifest;
	}

	public static Path getOutputDirectory() {
		return outputDirectory;
	}

	public static void setOutputDirectory(Path outputDirectory) {
		if (outputDirectory == null) {
			throw new IllegalArgumentException("Run output directory cannot be null");
		}
		AppExample.outputDirectory = outputDirectory;
	}

	public static RunOutputMode getOutputMode() {
		return outputMode;
	}

	public static void setOutputMode(RunOutputMode outputMode) {
		if (outputMode == null) {
			throw new IllegalArgumentException("Run output mode cannot be null");
		}
		AppExample.outputMode = outputMode;
	}

	public static Random getRand() {
		return rand;
	}

	public static void setRand(Random rand) {
		if (rand == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		AppExample.rand = rand;
	}

	public static int getMaxBandwidth() {
		return maxBandwidth;
	}

	public static void setMaxBandwidth(int maxBandwidth) {
		if (maxBandwidth <= 0) {
			throw new IllegalArgumentException("Network bandwidth must be positive");
		}
		AppExample.maxBandwidth = maxBandwidth;
	}

	public static boolean isMigrationAble() {
		return migrationAble;
	}

	public static void setMigrationAble(boolean migrationAble) {
		AppExample.migrationAble = migrationAble;
	}

}
