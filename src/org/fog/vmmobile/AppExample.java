package org.fog.vmmobile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;
import org.fog.entities.Tuple;
import org.fog.localization.Coordinate;
import org.fog.localization.Distances;
import org.fog.placement.MobileController;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
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
		/*
		 *  Simulation steps
		 *  
		 *  First step: Follow the following steps
		 *  Second step: Provide the user mobility dataset in the input directory
		 *  Third step: Initialize the CloudSim package. It should be called
		 *  before creating any entities.
		 *  Fourth step: Create all devices
		 *  Fifth step: Create Broker
		 *  Sixth step: Create one virtual machine
		 *  Seventh step: Create one Application (appModule, appEdge, appLoop and tuples)
		 *  Eight step: Configure the network
		 *  Ninth step: Starts the simulation
		 *  Final step: Print results when simulation is over
		 *  
		 *  Example parameters
		 *  
		 *  1 290538 0 0 1 11 0 0 0 61
		 *  
		 *  First parameter: 0/1 -> migrations are denied or allowed
		 *  Second parameter: Positive Integer -> seed to be used in the random numbers generation
		 *  Third parameter: 0/1 -> Migration point approach is fixed (0) or based on the user speed (1)
		 *  Fourth parameter: 0/1/2 -> Migration strategy approach is based on the lowest latency (0), lowest distance between the user and cloudlet (1), or lowest distance between user and Access Point (2)
		 *  Fifth parameter: Positive Integer -> Number of users
		 *  Sixth parameter: Positive Integer -> Base Network Bandwidth between cloudlets
		 *  Seventh parameter: 0/1/2 -> Migration policy based on Complete VM/Cold migration (0), Complete Container migration (1), or Container Live Migration (3)
		 *  Eighth parameter: Non Negative Integer -> User Mobility prediction, in seconds
		 *  Ninth parameter: Non Negative Integer -> User Mobility prediction inaccuracy, in meters
		 *  Tenth parameter: Positive negative Integer -> Base Network Latency between cloudlets
		 */

		Log.disable();

		int numUser = 1; // number of cloud users
		Calendar calendar = Calendar.getInstance();
		boolean traceFlag = false; // mean trace events
		CloudSim.init(numUser, calendar, traceFlag);

		setPositionApPolicy(Policies.FIXED_AP_LOCATION);
		setPositionScPolicy(Policies.FIXED_SC_LOCATION);
		setSeed(Integer.parseInt(args[1]));

		setStepPolicy(1);
		if (Integer.parseInt(args[0]) == 0) {
			setMigrationAble(false);
		} else {
			setMigrationAble(true);
		}
		if (getSeed() < 1) {
			System.out.println("Seed cannot be less than 1");
			System.exit(0);
		}
		setRand(new Random(getSeed() * Integer.MAX_VALUE));
		// FIXED_MIGRATION_POINT = 0;
		// SPEED_MIGRATION_POINT = 1;
		setMigPointPolicy(Integer.parseInt(args[2]));
		// LOWEST_LATENCY = 0;
		// LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET = 1;
		// LOWEST_DIST_BW_SMARTTING_AP = 2;
		setMigStrategyPolicy(Integer.parseInt(args[3]));
		setMaxSmartThings(Integer.parseInt(args[4]));
		setMaxBandwidth(Integer.parseInt(args[5]));
		// MIGRATION_COMPLETE_VM = 0;
		// MIGRATION_CONTAINER_VM = 1;
		// LIVE_MIGRATION = 2;
		setPolicyReplicaVM(Integer.parseInt(args[6]));
		setTravelPredicTimeForST(Integer.parseInt(args[7]));
		setMobilityPredictionError(Integer.parseInt(args[8]));
		setLatencyBetweenCloudlets(Double.parseDouble(args[9]));

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
		createServerCloudletsNetwork(getServerCloudlets());
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
		for (int i = 0; i < getMaxSmartThings(); i++) {// it creates the SmartThings
			addSmartThing(smartThings, coordDevices, i);
		}

		readMoblityData();

		int index;// Auxiliary
		int myCount = 0;

		// it makes the connection between SmartThing and the closest AccessPoint
		for (MobileDevice st : getSmartThings()) {
			if (!ApDevice.connectApSmartThing(getApDevices(), st,
				getRand().nextDouble())) {
				myCount++;
				LogMobile.debug("AppExample.java",
					st.getName() + " isn't connected");
			}
		}
		LogMobile.debug("AppExample.java", "total no connection: " + myCount);

		// it makes the connection between AccessPoint and the closest ServerCloudlet
		for (ApDevice ap : getApDevices()) {
			index = Distances.theClosestServerCloudletToAp(getServerCloudlets(), ap);
			ap.setServerCloudlet(getServerCloudlets().get(index));
			ap.setParentId(getServerCloudlets().get(index).getId());
			getServerCloudlets().get(index).setApDevices(ap, Policies.ADD);
			NetworkTopology.addLink(serverCloudlets.get(index).getId(),
				ap.getId(), ap.getDownlinkBandwidth(),
				getRand().nextDouble());

			// it makes the symbolic link between smartThing and ServerCloudlet
			for (MobileDevice st : ap.getSmartThings()) {
				getServerCloudlets().get(index).connectServerCloudletSmartThing(st);
				getServerCloudlets().get(index).setSmartThingsWithVm(st, Policies.ADD);

			}
		}
		/** STEP 3: CREATE BROKER**/

		for (MobileDevice st : getSmartThings()) {
			getBrokerList().add(new FogBroker(
				"My_broker" + Integer.toString(st.getMyId())));
		}

		/**
		 * STEP 4: CREATE ONE VIRTUAL MACHINE FOR EACH BROKER/USER -> example
		 * from: CloudSim - example5.java
		 **/
		// It only creates the virtual machine for each smartThing
		for (MobileDevice st : getSmartThings()) {
			if (st.getSourceAp() != null) {
				CloudletScheduler cloudletScheduler = new TupleScheduler(500, 1); 
				long sizeVm = 128;
				AppModule vmSmartThingTest = new AppModule(st.getMyId() // id
					, "AppModuleVm_" + st.getName() // name
					, "MyApp_vr_game" + st.getMyId() // appId
					, getBrokerList().get(st.getMyId()).getId() // userId
					, 281 // mips
					, 128 // (int) sizeVm/3 //ram
					, 1000 // bw
					, sizeVm, "Vm_" + st.getName() // vmm
					, cloudletScheduler,
					new HashMap<Pair<String, String>, SelectivityModel>());

				st.setVmMobileDevice(vmSmartThingTest);
				st.getSourceServerCloudlet().getHost().vmCreate(vmSmartThingTest);
				st.setVmLocalServerCloudlet(st.getSourceServerCloudlet());

				System.out.println(st.getMyId() + " Position: "
					+ st.getCoord().getCoordX() + ", "
					+ st.getCoord().getCoordY() + " Direction: "
					+ st.getDirection() + " Speed: " + st.getSpeed());
				System.out.println("Source AP: " + st.getSourceAp()
					+ " Dest AP: " + st.getDestinationAp() + " Host: "
					+ st.getHost().getId());
				System.out.println("Local server: "
					+ st.getVmLocalServerCloudlet().getName() + " Apps "
					+ st.getVmLocalServerCloudlet().getActiveApplications() + " Map "
					+ st.getVmLocalServerCloudlet().getApplicationMap());
				if (st.getDestinationServerCloudlet() == null) {
					System.out.println("Dest server: null Apps: null Map: null");
				} else {
					System.out.println("Dest server: "
						+ st.getDestinationServerCloudlet().getName() + " Apps: " 
						+ st.getDestinationServerCloudlet().getActiveApplications()
						+ " Map " + st.getDestinationServerCloudlet().getApplicationMap());
				}
			}
		}
		int i = 0;
		// Each broker receives one smartThing's VM
		for (FogBroker br : getBrokerList()) {
			List<Vm> tempVmList = new ArrayList<>();
			tempVmList.add(getSmartThings().get(i++).getVmMobileDevice());
			br.submitVmList(tempVmList);
		}

		/**
		 * STEP 5: CREATE THE APPLICATION
		 **/
		i = 0;

		for (FogBroker br : getBrokerList()) {
			getAppIdList().add("MyApp_vr_game" + Integer.toString(i));

			Application myApp = createApplication(getAppIdList().get(i), br.getId(), i,
				(AppModule) getSmartThings().get(i).getVmMobileDevice());
			getApplicationList().add(myApp);
			i++;
		}

		/**
		 * STEP 5.1: IT LINKS SENSORS AND ACTUATORS FOR EACH BROKER -> example
		 * from: CloudSim and iFogSim
		 **/
		for (MobileDevice st : getSmartThings()) {
			int brokerId = getBrokerList().get(st.getMyId()).getId();
			String appId = getAppIdList().get(st.getMyId());
			if (st.getSourceAp() != null) {
				for (MobileSensor s : st.getSensors()) {
					s.setAppId(appId);
					s.setUserId(brokerId);
					s.setGatewayDeviceId(st.getId());
					s.setLatency(6.0);
				}
				for (MobileActuator a : st.getActuators()) {
					a.setUserId(brokerId);
					a.setAppId(appId);
					a.setGatewayDeviceId(st.getId());
					a.setLatency(1.0);
					a.setActuatorType("DISPLAY" + st.getMyId());
				}
			}
		}

		/**
		 * STEP 6: CREATE MAPPING, CONTROLLER, AND SUBMIT APPLICATION
		 **/

		MobileController mobileController = null;
		// initializing a module mapping
		ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

		for (Application app : getApplicationList()) {
			app.setPlacementStrategy("Mapping");
		}
		i = 0;
		for (FogDevice sc : getServerCloudlets()) {
			i = 0;
			for (MobileDevice st : getSmartThings()) {
				if (st.getApDevices() != null) {
					if (sc.equals(st.getSourceServerCloudlet())) {
						moduleMapping.addModuleToDevice(((AppModule) st.getVmMobileDevice()).getName(),
							sc.getName(), 1);
						moduleMapping.addModuleToDevice("client" + st.getMyId(),
							st.getName(), 1);
					}
				}
				i++;
			}
		}

		mobileController = new MobileController("MobileController",
			getServerCloudlets(), getApDevices(), getSmartThings(),
			getBrokerList(), moduleMapping, getMigPointPolicy(),
			getMigStrategyPolicy(), getStepPolicy(), getCoordDevices(),
			getSeed(), isMigrationAble());
		i = 0;
		for (Application app : applicationList) {
			mobileController.submitApplication(app, 1);
		}
		TimeKeeper.getInstance().setSimulationStartTime(
			Calendar.getInstance().getTimeInMillis());
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

		myCount = 0;

		for (MobileDevice st : getSmartThings()) {
			if (st.getSourceAp() != null) {
				System.out.println("Distance between " + st.getName() + " and "
					+ st.getSourceAp().getName() + ": "
					+ Distances.checkDistance(st.getCoord(),
						st.getSourceAp().getCoord()));
			}
		}
		for (MobileDevice st : getSmartThings()) {
			System.out.println(
				st.getName() + "- X: " + st.getCoord().getCoordX() + " Y: "
					+ st.getCoord().getCoordY() + " Direction: "
					+ st.getDirection() + " Speed: " + st.getSpeed()
					+ " VmSize: " + st.getVmMobileDevice().getSize());
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
		System.setOut(new PrintStream("out.txt"));
		System.out.println("Inicio: " + Calendar.getInstance().getTime());
		CloudSim.startSimulation();
		System.out.println("Simulation over");
		CloudSim.stopSimulation();
	}

	private static void readMoblityData() {

		File folder = new File("input");
		File[] listOfFiles = folder.listFiles();

		Arrays.sort(listOfFiles);
		int[] ordem = readDevicePathOrder(listOfFiles[listOfFiles.length - 1]);
		for (int i = 0; i < getSmartThings().size(); i++) {
			readDevicePath(getSmartThings().get(i), "input/" + listOfFiles[ordem[i]].getName());
		}
	}

	private static int[] readDevicePathOrder(File filename) {

		String line = "";
		String cvsSplitBy = "\t";

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {

			int i = 1;
			while (((line = br.readLine()) != null)) {
				if (i == 1) {
					break;
				}
				i++;
			}
			// use comma as separator
			String[] position = line.split(cvsSplitBy);
			int order[] = new int[getSmartThings().size()];
			for (int j = 0; j < getSmartThings().size(); j++) {
				order[j] = Integer.valueOf(position[j]);
			}
			Arrays.sort(order);
			return order;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static void readDevicePath(MobileDevice st, String filename) {

		String line = "";
		String cvsSplitBy = "\t";

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {

			while ((line = br.readLine()) != null) {

				// use comma as separator
				String[] position = line.split(cvsSplitBy);

				st.getPath().add(position);
			}

			Coordinate coordinate = new Coordinate();
			coordinate.setInitialCoordinate(st);
			saveMobility(st);
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void saveMobility(MobileDevice st) {

		try (FileWriter fw1 = new FileWriter(st.getMyId() + "out.txt", true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(st.getMyId() + " Position: " + st.getCoord().getCoordX() + ", "
				+ st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: "
				+ st.getSpeed());
			out1.println("Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp()
				+ " Host: " + st.getHost().getId());
			out1.println("Local server: null  Apps null Map null");
			if (st.getDestinationServerCloudlet() == null) {
				out1.println("Dest server: null Apps: null Map: null");
			}
			else {
				out1.println("Dest server: " + st.getDestinationServerCloudlet().getName()
					+ " Apps: " + st.getDestinationServerCloudlet().getActiveApplications()
					+ " Map " + st.getDestinationServerCloudlet().getApplicationMap());
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(st.getMyId() + "route.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t"
				+ st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" + st.getSpeed()
				+ "\t" + CloudSim.clock());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
			double maxBandwidth = getMaxBandwidth() * 1024 * 1024;
			double minBandwidth = (getMaxBandwidth() - 1) * 1024 * 1024;
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
					double maxBandwidth = getMaxBandwidth() * 1024 * 1024;
					double minBandwidth = (getMaxBandwidth() - 1) * 1024 * 1024;
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

	private static void createServerCloudletsNetwork(List<FogDevice> serverCloudlets) {
		// for no full graph, use -1 to link
		HashMap<FogDevice, Double> net = new HashMap<>();
		int i = 0, j = 0, linha, coluna;
		for (FogDevice sc : serverCloudlets) {// It makes a full graph
			j = 0;
			for (FogDevice sc1 : serverCloudlets) {
				if (sc.equals(sc1)) {
					break;
				}

				linha = ((int) (j / 12) - (int) (i / 12));
				if (linha < 0)
					linha *= -1;
				coluna = ((int) (j % 12) - (int) (i % 12));
				if (coluna < 0)
					coluna *= -1;
				if (sc.getUplinkBandwidth() < sc1.getDownlinkBandwidth()) {
					net.put(sc1, sc.getUplinkBandwidth());
					NetworkTopology.addLink(sc.getId(), sc1.getId(),
						sc.getUplinkBandwidth(), (Math.max(linha, coluna))
							* getLatencyBetweenCloudlets() + getRand().nextDouble());
				} else {
					net.put(sc1, sc1.getDownlinkBandwidth());
					NetworkTopology.addLink(sc.getId(), sc1.getId(),
						sc1.getDownlinkBandwidth(), (Math.max(linha, coluna))
							* getLatencyBetweenCloudlets() + getRand().nextDouble());
				}
				j++;
			}
			i++;
			sc.setNetServerCloudlets(net);
		}
	}

	@SuppressWarnings("unused")
	private static Application createApplication(String appId, int userId,
		int myId, AppModule userVm) {

		// creates an empty application model (empty directed graph)
		Application application = Application.createApplication(appId, userId);
		// adding module Client to the application model
		application.addAppModule(userVm);
		application.addAppModule("client" + myId, "appModuleClient" + myId, 10);

		/*
		 * Connecting the application modules (vertices) in the application
		 * model (directed graph) with edges
		 */
		if (EEG_TRANSMISSION_TIME >= 10)
			// adding edge from EEG (sensor) to Client module carrying tuples of type EEG
			application.addAppEdge("EEG" + myId, "client" + myId, 966, 54,
				"EEG" + myId, Tuple.UP, AppEdge.SENSOR);
		else
			application.addAppEdge("EEG" + myId, "client" + myId, 966, 54,
				"EEG" + myId, Tuple.UP, AppEdge.SENSOR);

		// adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		application.addAppEdge("client" + myId, userVm.getName(), 966, 54,
			"_SENSOR" + myId, Tuple.UP, AppEdge.MODULE);

		// adding periodic edge (period=1000ms) from Concentration Calculator to
		//Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge(userVm.getName(), userVm.getName(), 1000, 966,
			54, "PLAYER_GAME_STATE" + myId, Tuple.UP, AppEdge.MODULE);
		// adding edge from Concentration Calculator to Client module carrying
		//tuples of type CONCENTRATION
		application.addAppEdge(userVm.getName(), "client" + myId, 2439, 87,
			"CONCENTRATION" + myId, Tuple.DOWN, AppEdge.MODULE);
		// adding periodic edge (period=1000ms) from Connector to Client module
		//carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge(userVm.getName(), "client" + myId, 2439, 28,
			87, "GLOBAL_GAME_STATE" + myId, Tuple.DOWN, AppEdge.MODULE);
		// adding edge from Client module to Display (actuator) carrying tuples
		// of type SELF_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 2439, 87,
			"SELF_STATE_UPDATE" + myId, Tuple.DOWN, AppEdge.ACTUATOR);
		// adding edge from Client module to Display (actuator) carrying tuples
		// of type GLOBAL_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 2439, 87,
			"GLOBAL_STATE_UPDATE" + myId, Tuple.DOWN, AppEdge.ACTUATOR);

		/*
		 * Defining the input-output relationships (represented by selectivity)
		 * of the application modules.
		 */

		// 0.9 tuples of type _SENSOR are emitted by Client module per incoming
		//tuple of type EEG
		application.addTupleMapping("client" + myId, "EEG" + myId,
			"_SENSOR" + myId, new FractionalSelectivity(0.9));
		// 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per
		//incoming tuple of type CONCENTRATION
		application.addTupleMapping("client" + myId, "CONCENTRATION" + myId,
			"SELF_STATE_UPDATE" + myId, new FractionalSelectivity(1.0));
		// 1.0 tuples of type CONCENTRATION are emitted by Concentration
		//Calculator module per incoming tuple of type _SENSOR
		application.addTupleMapping(userVm.getName(), "_SENSOR" + myId,
			"CONCENTRATION" + myId, new FractionalSelectivity(1.0));
		// 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module
		//per incoming tuple of type GLOBAL_GAME_STATE
		application.addTupleMapping("client" + myId, "GLOBAL_GAME_STATE" + myId,
			"GLOBAL_STATE_UPDATE" + myId, new FractionalSelectivity(1.0));

		/*
		 * Defining application loops to monitor the latency of. Here, we add
		 * only one loop for monitoring : EEG(sensor) -> Client -> Concentration
		 * Calculator -> Client -> DISPLAY (actuator)
		 */
		final String client = "client" + myId;// userVm.getName();
		final String concentration = userVm.getName();
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
		AppExample.policyReplicaVM = policyReplicaVM;
	}

	public static int getTravelPredicTimeForST() {
		return travelPredicTimeForST;
	}

	public static void setTravelPredicTimeForST(int travelPredicTimeForST) {
		AppExample.travelPredicTimeForST = travelPredicTimeForST;
	}

	public static int getMobilityPrecitionError() {
		return mobilityPrecitionError;
	}

	public static void setMobilityPredictionError(int mobilityPrecitionError) {
		AppExample.mobilityPrecitionError = mobilityPrecitionError;
	}

	public static double getLatencyBetweenCloudlets() {
		return latencyBetweenCloudlets;
	}

	public static void setLatencyBetweenCloudlets(double latencyBetweenCloudlets) {
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
		AppExample.migPointPolicy = migPointPolicy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigStrategyPolicy(int migStrategyPolicy) {
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
		AppExample.maxSmartThings = maxSmartThings;
	}

	public static Random getRand() {
		return rand;
	}

	public static void setRand(Random rand) {
		AppExample.rand = rand;
	}

	public static int getMaxBandwidth() {
		return maxBandwidth;
	}

	public static void setMaxBandwidth(int maxBandwidth) {
		AppExample.maxBandwidth = maxBandwidth;
	}

	public static boolean isMigrationAble() {
		return migrationAble;
	}

	public static void setMigrationAble(boolean migrationAble) {
		AppExample.migrationAble = migrationAble;
	}

}
