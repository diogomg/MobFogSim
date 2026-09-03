package org.fog.vmmobile;

import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
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
import org.cloudbus.cloudsim.util.RunOutputManager;
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

public class AppExample {
	private static int stepPolicy; // Quantity of steps in the nextStep Function
	private static List<MobileDevice> smartThings = new ArrayList<MobileDevice>();
	private static List<FogDevice> serverCloudlets = new ArrayList<>();
	private static List<ApDevice> apDevices = new ArrayList<>();
	private static List<FogBroker> brokerList = new ArrayList<>();
	private static List<String> appIdList = new ArrayList<>();
	private static List<Application> applicationList = new ArrayList<>();

	private static boolean migrationAble;

	private static int migPointPolicy;
	private static int migStrategyPolicy;
	private static int positionApPolicy;
	private static int positionScPolicy;
	private static int policyReplicaVM;
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
	private static final TopologyService TOPOLOGY_SERVICE = new TopologyService();
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
			executeSimulation(configuration);
			return context.result();
		}
	}

	private static void executeSimulation(SimulationConfig configuration)
		throws Exception {
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
		 *  1 290538 0 0 10 11 0 61 0 0 0 60,40 70,30 1 2 summary
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
		 *  Fifteenth parameter: 0 edge servers only, 1 end devices only, 2 hybrid.
		 *  Sixteenth parameter: summary, full, or none output mode
		 *  (default: summary).
		 */

		Log.disable();

		int numUser = 1; // number of cloud users
		Calendar calendar = Calendar.getInstance();
		boolean traceFlag = false; // mean trace events
		CloudSim.init(numUser, calendar, traceFlag);
		setPositionApPolicy(Policies.FIXED_AP_LOCATION);
		setPositionScPolicy(Policies.FIXED_SC_LOCATION);
		setStepPolicy(1);
		applySimulationConfiguration(configuration);

		/**
		 * STEP 2: CREATE ALL DEVICES -> example from: CloudSim - example5.java
		 **/

		/* It is creating Access Points. It makes according positionApPolicy */
		if (positionApPolicy == Policies.FIXED_AP_LOCATION) {
			// it creates the Access Point according coordDevices' size
			addApDevicesFixed(apDevices, coordDevices);
		} else {
			// it creates the Access Points
			for (int i = 0; i < MaxAndMin.MAX_AP_DEVICE; i++) {
				addApDevicesRandon(apDevices, coordDevices, i);
			}
		}

		/* It is creating Server Cloudlets. */
		if (getPositionScPolicy() == Policies.FIXED_SC_LOCATION) {
			addServerCloudlet(serverCloudlets, coordDevices);
		} else {
			// it creates the ServerCloudlets
			for (int i = 0; i < MaxAndMin.MAX_SERVER_CLOUDLET; i++) {
				addServerCloudlet(serverCloudlets, coordDevices, i);
			}
		}
		TOPOLOGY_SERVICE.createTransportNetwork(getServerCloudlets(),
			getLatencyBetweenCloudlets(), getRand());
		for (FogDevice sc : getServerCloudlets()) {
			for (FogDevice sc1 : getServerCloudlets()) {
				if (sc.equals(sc1)) {
					break;
				}
				System.out.println("Delay between " + sc.getName() + " and "
					+ sc1.getName() + ": "
					+ NetworkTopology.getDelay(sc.getId(), sc1.getId()));
				System.out.println(
					sc.getName() + ": " + sc.getDownlinkBandwidth());
			}
		}

		/* It is creating Smart Things. */
		int[] userSliceAssignments = NetworkSlicing.getUserSliceAssignments(
			getMaxSmartThings());
		for (int i = 0; i < getMaxSmartThings(); i++) {// it creates the SmartThings
			addSmartThing(smartThings, coordDevices, i);
			smartThings.get(i).setNetworkSliceId(userSliceAssignments[i]);
		}

		TOPOLOGY_SERVICE.loadMobility(getMobilityDirectory(),
			getMobilityOrderManifest(), getSmartThings());
		MobileUserRegistration.preparePendingUsers(getSmartThings());

		TOPOLOGY_SERVICE.connectAccessPoints(getServerCloudlets(),
			getApDevices(), getRand());

		/**
		 * STEP 3: CREATE CONTROLLER. Brokers, VMs, and applications are created
		 * when each user's scheduled entry event occurs.
		 **/

		MobileController mobileController = null;
		// initializing a module mapping
		ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

		mobileController = new MobileController("MobileController",
			getServerCloudlets(), getApDevices(), getSmartThings(),
			getBrokerList(), moduleMapping, getMigPointPolicy(),
			getMigStrategyPolicy(), getStepPolicy(), getCoordDevices(),
			getSeed(), isMigrationAble());
		TimeKeeper.getInstance().setSimulationStartTime(
			SimulationContext.requireCurrent().getClock().wallTimeMillis());
		MyStatistics.getInstance().setSeed(getSeed());
		for (MobileDevice st : getSmartThings()) {
			if (getMigPointPolicy() == Policies.FIXED_MIGRATION_POINT) {
				if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {

					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId() + ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
							+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY");
				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/"+ st.getMyId()
						+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId() + ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET");
				}
			} else if (getMigPointPolicy() == Policies.SPEED_MIGRATION_POINT) {
				if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/" + st.getMyId()
						+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
						+ getSeed() + "_st_" + st.getMyId(), st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
					MyStatistics.getInstance().setFileMap("./outputLatencies/"+ st.getMyId()
						+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId()+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
						+ getSeed() + "_st_" + st.getMyId(),st.getMyId());
					MyStatistics.getInstance().setToPrint(
						"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
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
			MyStatistics.getInstance().getMyCount().put(st.getMyId(), 0);
		}

		for (MobileDevice st : getSmartThings()) {
			System.out.println(
				st.getName() + "- X: " + st.getCoord().getCoordX() + " Y: "
					+ st.getCoord().getCoordY() + " Direction: "
					+ st.getDirection() + " Speed: " + st.getSpeed()
					+ " EntryTime: " + st.getStartTravelTime() + " seconds");
		}
		System.out
			.println("_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_");
		for (FogDevice sc : getServerCloudlets()) {
			System.out.println(sc.getName() + "- X: " + sc.getCoord().getCoordX()
				+ " Y: " + sc.getCoord().getCoordY()
				+ " UpLinkLatency: " + sc.getUplinkLatency());
		}
		System.out
			.println("_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_");
		for (ApDevice ap : getApDevices()) {
			System.out.println(ap.getName() + "- X: " + ap.getCoord().getCoordX() + " Y: "
				+ ap.getCoord().getCoordY() + " connected to "
				+ ap.getServerCloudlet().getName());

		}
		PrintStream console = System.out;
		try (PrintStream simulationOutput = RunOutputManager.getInstance()
			.newFullPrintStream("out.txt")) {
			System.setOut(simulationOutput);
			System.out.println("Inicio: " + Calendar.getInstance().getTime());
			CloudSim.startSimulation();
			System.out.println("Simulation over");
		}
		finally {
			System.setOut(console);
		}
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
		setMigPointPolicy(configuration.getMigrationPointPolicy());
		setMigStrategyPolicy(configuration.getMigrationStrategyPolicy());
		setMaxSmartThings(configuration.getMaximumUsers());
		setMaxBandwidth(configuration.getMaximumBandwidth());
		setPolicyReplicaVM(configuration.getVmMigrationPolicy());
		setLatencyBetweenCloudlets(configuration.getCloudletLatency());
		setTravelPredicTimeForST(configuration.getTravelPredictionTime());
		setMobilityPredictionError(configuration.getMobilityPredictionError());
		NetworkSlicing.applyConfiguration(configuration.getSlicingConfiguration());
		VmDestinationPolicy.configure(configuration.getVmDestinationPolicy());
		setMobilityDirectory(configuration.getMobilityDirectory());
		setMobilityOrderManifest(configuration.getMobilityOrderManifest());
		setOutputDirectory(configuration.getOutputDirectory());
		setOutputMode(configuration.getOutputMode());
	}

	static void useSimulationContext(SimulationContext context) {
		SimulationTopology topology = context.getTopology();
		setSmartThings(topology.getMobileDevices());
		setServerCloudlets(topology.getServerCloudlets());
		setApDevices(topology.getAccessPoints());
		setBrokerList(topology.getBrokers());
		setAppIdList(topology.getApplicationIds());
		setApplicationList(topology.getApplications());
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
		int i = 0;
		boolean control = true;
		int coordY = 0;
		for (int coordX = 0; coordX < MaxAndMin.MAX_X; coordX += (2
			* MaxAndMin.AP_COVERAGE
			- (2 * MaxAndMin.AP_COVERAGE / 3))) { /* evenly distributed */
			System.out.println("Creating Ap devices");
			for (coordY = 0; coordY < MaxAndMin.MAX_Y; coordY += (2
				* MaxAndMin.AP_COVERAGE
				- (2 * MaxAndMin.AP_COVERAGE / 3)), i++) {

				ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
					coordX, coordY, i// ap.set//id
					, 100 * 1024 * 1024// downLinkBandwidth - 100Mbits
					, 200// engergyConsuption
					, MaxAndMin.MAX_ST_IN_AP// maxSmartThing
					, 100 * 1024 * 1024// upLinkBandwidth - 100Mbits
					, 4// upLinkLatency
				);
				apDevices.add(i, ap);
			}
		}
		LogMobile.debug("AppExample.java", "Total of accessPoints: " + i);

	}

	private static void addApDevicesRandon(List<ApDevice> apDevices,
		Coordinate coordDevices, int i) {
		int coordX, coordY;
		coordX = getRand().nextInt(MaxAndMin.MAX_X);
		coordY = getRand().nextInt(MaxAndMin.MAX_Y);
		ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
			coordX, coordY, i// id
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

		int coordX = 0, coordY = 0;
		int direction, speed;
		direction = getRand().nextInt(MaxAndMin.MAX_DIRECTION - 1) + 1;
		speed = getRand().nextInt(MaxAndMin.MAX_SPEED - 1) + 1;
		/*************** Start set of Mobile Sensors ****************/
		VmMigrationTechnique migrationTechnique = null;

		if (getPolicyReplicaVM() == Policies.MIGRATION_COMPLETE_VM) {
			migrationTechnique = new CompleteVM(getMigPointPolicy());
		} else if (getPolicyReplicaVM() == Policies.MIGRATION_CONTAINER_VM) {
			migrationTechnique = new ContainerVM(getMigPointPolicy());
		} else if (getPolicyReplicaVM() == Policies.LIVE_MIGRATION) {
			migrationTechnique = new LiveMigration(getMigPointPolicy());
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
			st.setSensors(sensors);
			st.setActuators(actuators);
			st.setTravelPredicTime(getTravelPredicTimeForST());
			st.setMobilityPredictionError(getMobilityPrecitionError());
			smartThing.add(i, st);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices, int i) {

		int coordX, coordY;
		DecisionMigration migrationStrategy;
		if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
			migrationStrategy = new LowestLatency(getServerCloudlets(),
				getApDevices(), getMigPointPolicy(), getPolicyReplicaVM());
		} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
			migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
				getServerCloudlets(), getApDevices(), getMigPointPolicy(),
				getPolicyReplicaVM());
		} else { // Policies.LOWEST_DIST_BW_SMARTTING_AP
			migrationStrategy = new LowestDistBwSmartThingAP(
				getServerCloudlets(), getApDevices(), getMigPointPolicy(),
				getPolicyReplicaVM());
		}

		BeforeMigration beforeMigration = null;
		if (getPolicyReplicaVM() == Policies.MIGRATION_COMPLETE_VM) {
			beforeMigration = new PrepareCompleteVM();
		} else if (getPolicyReplicaVM() == Policies.MIGRATION_CONTAINER_VM) {
			beforeMigration = new PrepareContainerVM();
		} else if (getPolicyReplicaVM() == Policies.LIVE_MIGRATION) {
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
		if (serviceOffer.getType() == Services.HIBRID
			|| serviceOffer.getType() == Services.PUBLIC) {
			serviceOffer.setValue(getRand().nextFloat() * 10);
		} else {
			serviceOffer.setValue(0);
		}
		try {
			coordX = getRand().nextInt(MaxAndMin.MAX_X);
			coordY = getRand().nextInt(MaxAndMin.MAX_X);
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
				, coordX, coordY, i, serviceOffer, migrationStrategy,
				getPolicyReplicaVM(), beforeMigration);
			serverCloudlets.add(i, sc);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordDevices) {
		int i = 0;
		int coordX, coordY;

		for (coordX = 0; coordX < MaxAndMin.MAX_X; coordX += (2
			* MaxAndMin.CLOUDLET_COVERAGE
			- (2 * MaxAndMin.CLOUDLET_COVERAGE / 3))) { /* evenly distributed */
			System.out.println("Creating Server cloudlets");
			for (coordY = 0; coordY < MaxAndMin.MAX_X; coordY += (2
				* MaxAndMin.CLOUDLET_COVERAGE
				- (2 * MaxAndMin.CLOUDLET_COVERAGE
				/ 3)), i++) { /* evenly distributed */
				DecisionMigration migrationStrategy;
				if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
					migrationStrategy = new LowestLatency(getServerCloudlets(),
						getApDevices(), getMigPointPolicy(), getPolicyReplicaVM());
				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
					migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
						getServerCloudlets(), getApDevices(),
						getMigPointPolicy(), getPolicyReplicaVM());
				} else { // LOWEST_DIST_BW_SMARTTING_AP
					migrationStrategy = new LowestDistBwSmartThingAP(
						getServerCloudlets(), getApDevices(),
						getMigPointPolicy(), getPolicyReplicaVM());
				}

				BeforeMigration beforeMigration = null;
				if (getPolicyReplicaVM() == Policies.MIGRATION_COMPLETE_VM) {
					beforeMigration = new PrepareCompleteVM();
				} else if (getPolicyReplicaVM() == Policies.MIGRATION_CONTAINER_VM) {
					beforeMigration = new PrepareContainerVM();
				} else if (getPolicyReplicaVM() == Policies.LIVE_MIGRATION) {
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
				if (serviceOffer.getType() == Services.HIBRID
					|| serviceOffer.getType() == Services.PUBLIC) {
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
						migrationStrategy, getPolicyReplicaVM(),
						beforeMigration);
					serverCloudlets.add(i, sc);
					sc.setParentId(-1);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		LogMobile.debug("AppExample.java", "Total of serverCloudlets: " + i);
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
		return policyReplicaVM;
	}

	public static void setPolicyReplicaVM(int policyReplicaVM) {
		if (policyReplicaVM < Policies.MIGRATION_COMPLETE_VM
			|| policyReplicaVM > Policies.LIVE_MIGRATION) {
			throw new IllegalArgumentException("VM migration policy must be between 0 and 2");
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
		return smartThings;
	}

	public static void setSmartThings(List<MobileDevice> smartThings) {
		AppExample.smartThings = smartThings;
	}

	public static List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}

	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		AppExample.serverCloudlets = serverCloudlets;
	}

	public static List<ApDevice> getApDevices() {
		return apDevices;
	}

	public static void setApDevices(List<ApDevice> apDevices) {
		AppExample.apDevices = apDevices;
	}

	public static int getMigPointPolicy() {
		return migPointPolicy;
	}

	public static void setMigPointPolicy(int migPointPolicy) {
		if (migPointPolicy < Policies.FIXED_MIGRATION_POINT
			|| migPointPolicy > Policies.SPEED_MIGRATION_POINT) {
			throw new IllegalArgumentException(
				"Migration point policy must be between 0 and 1");
		}
		AppExample.migPointPolicy = migPointPolicy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigStrategyPolicy(int migStrategyPolicy) {
		if (migStrategyPolicy < Policies.LOWEST_LATENCY
			|| migStrategyPolicy > Policies.LOWEST_DIST_BW_SMARTTING_AP) {
			throw new IllegalArgumentException(
				"Migration strategy policy must be between 0 and 2");
		}
		AppExample.migStrategyPolicy = migStrategyPolicy;
	}

	public static int getPositionApPolicy() {
		return positionApPolicy;
	}

	public static void setPositionApPolicy(int positionApPolicy) {
		AppExample.positionApPolicy = positionApPolicy;
	}

	public static Coordinate getCoordDevices() {
		return coordDevices;
	}

	public static void setCoordDevices(Coordinate coordDevices) {
		AppExample.coordDevices = coordDevices;
	}

	public static List<FogBroker> getBrokerList() {
		return brokerList;
	}

	public static void setBrokerList(List<FogBroker> brokerList) {
		AppExample.brokerList = brokerList;
	}

	public static List<String> getAppIdList() {
		return appIdList;
	}

	public static void setAppIdList(List<String> appIdList) {
		AppExample.appIdList = appIdList;
	}

	public static List<Application> getApplicationList() {
		return applicationList;
	}

	public static void setApplicationList(List<Application> applicationList) {
		AppExample.applicationList = applicationList;
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
		return positionScPolicy;
	}

	public static void setPositionScPolicy(int positionScPolicy) {
		AppExample.positionScPolicy = positionScPolicy;
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
