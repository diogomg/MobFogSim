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

public class AppExemplo2 {
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
	private static int travelPredicTimeForST; //in seconds
	private static int mobilityPrecitionError;//in meters
	private static double latencyBetweenCloudlets;
	private static int maxBandwidth;
	private static int maxSmartThings;
	private static Coordinate coordDevices;// =new Coordinate(MaxAndMin.MAX_X,
											// MaxAndMin.MAX_Y);//Grid/Map
	private static int seed;
	private static Random rand;
	static final boolean CLOUD = true;

	static final int numOfDepts = 1;
	static final int numOfMobilesPerDept = 4;
	static final double EEG_TRANSMISSION_TIME = 10;

	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 * @throws Exception
	 */

	public static void main(String[] args) throws Exception {
		// First step: Initialize the CloudSim package. It should be called
		// before creating any entities.
		// Second step: Create all devices
		// Third step: Create Broker
		// Fourth step: Create one virtual machine
		// Fifth step: Create one Application (appModule, appEdge, appLoop and
		// tuples)
		// Sixth step: configure network
		// Seventh step: Starts the simulation
		// Final step: Print results when simulation is over
		// Constar na dissertacao a politica de migracao deve estar alinhada com
		// a politica de handoff.
		// http://sbrc2016.ufba.br/downloads/WoCCES/155119.pdf
		// https://arxiv.org/pdf/1611.05539.pdf -> para referenciar muitos
		// aspectos de fogComputing
		// Proximos steps:
		// - verificar e talvez configurar a rede entre os ServerCloudlets
		// - Buscar os valores reais de acordo com a literatura e colocar o link
		// dos artigos como comentario na atribuição destes valores
		// - pensar nos pontos de mediçao

		/*
		 * Alguns artigos sobre o projeto
		 * https://arxiv.org/pdf/1611.05539.pdf (Fog Computing: A Taxonomy,
		 * Survey and Future Directions)
		 * http://www.cs.wm.edu/~liqun/paper/hotweb15.pdf (Fog Computing:
		 * Platform and Applications)
		 * http://sbrc2016.ufba.br/downloads/WoCCES/155119.pdf (Avaliacao de
		 * desempenho de procedimentos de handoff em redes IPv6 e uma discussao
		 * sobre a viabilidade de aplicacao em sistemas criticos)
		 * http://ieeexplore.ieee.org/stamp/stamp.jsp?arnumber=7098039 (Fog
		 * Computing Micro Datacenter Based Dynamic Resource Estimation and
		 * Pricing Model for IoT)
		 */
		// verificar se eh viavel colocar a criacao dos appmodules na conexao
		// com o ServerCloudlet.
		// TODO Auto-generated method stub
		/**********
		 * It's necessary to CloudSim.java for working correctly
		 **********/
		Log.disable();

		// Logger.ENABLED=true;
		// LogMobile.ENABLED=true;

		int numUser = 1; // number of cloud users
		Calendar calendar = Calendar.getInstance();
		boolean traceFlag = false; // mean trace events
		CloudSim.init(numUser, calendar, traceFlag);
		/**************************************************************************/
		// setSeed(1);
		//
		//
		//// setMigPointPolicy(Policies.FIXED_MIGRATION_POINT);
		// setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);
		//
		// setPolicyReplicaVM(Policies.MIGRATION_COMPLETE_VM); //0
		// setPolicyReplicaVM(Policies.MIGRATION_CONTAINER_VM);//1
		// setPolicyReplicaVM(Policies.LIVE_MIGRATION);//2
		//
		//// setMigStrategyPolicy(Policies.LOWEST_LATENCY); //0
		// setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET);//1
		//// setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_AP);//2

		//
		setPositionApPolicy(Policies.FIXED_AP_LOCATION);
		// setPositionApPolicy(Policies.RANDOM_AP_LOCATION);
		//
		setPositionScPolicy(Policies.FIXED_SC_LOCATION);
		// setPositionScPolicy(Policies.RANDOM_SC_LOCATION);
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
//		FIXED_MIGRATION_POINT = 0;
//		SPEED_MIGRATION_POINT = 1;
		setMigPointPolicy(Integer.parseInt(args[2]));
//		LOWEST_LATENCY = 0;
//		LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET = 1;
//		LOWEST_DIST_BW_SMARTTING_AP = 2;
		setMigStrategyPolicy(Integer.parseInt(args[3]));
		setMaxSmartThings(Integer.parseInt(args[4]));
		setMaxBandwidth(Integer.parseInt(args[5]));
//		MIGRATION_COMPLETE_VM = 0;
//		MIGRATION_CONTAINER_VM = 1;
//		LIVE_MIGRATION = 2;
		setPolicyReplicaVM(Integer.parseInt(args[6]));
		setTravelPredicTimeForST(Integer.parseInt(args[7]));
		setMobilityPredictionError(Integer.parseInt(args[8]));
		setLatencyBetweenCloudlets(Double.parseDouble(args[9]));

		/**
		 * STEP 2: CREATE ALL DEVICES -> example from: CloudSim - example5.java
		 **/

		/* It is creating Access Points. It makes according positionApPolicy */
		if (positionApPolicy == Policies.FIXED_AP_LOCATION) {
			addApDevicesFixed(apDevices, coordDevices);// it creates the Access
														// Point according
														// coordDevices' size
		} else {
			for (int i = 0; i < MaxAndMin.MAX_AP_DEVICE; i++) {// it creates the
																// Access Points
																// - initial
																// parameter?
				addApDevicesRandon(apDevices, coordDevices, i);
			}
		}

		/* It is creating Server Cloudlets. */
		if (getPositionScPolicy() == Policies.FIXED_SC_LOCATION) {
			addServerCloudlet(serverCloudlets, coordDevices);
		} else {
			for (int i = 0; i < MaxAndMin.MAX_SERVER_CLOUDLET; i++) { // it
																		// creates
																		// the
																		// ServerCloudlets
																		// -
																		// initial
																		// parameter?
																		// in
																		// runtime,
																		// schedule
																		// status
																		// to
																		// false
																		// or
																		// true
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

		// System.exit(0);

		/* It is creating Smart Things. */
		for (int i = 0; i < getMaxSmartThings(); i++) {// it creates the
														// SmartThings - initial
														// parameter? -> in
														// runtime, schedule
														// events to add or
														// remove items
			addSmartThing(smartThings, coordDevices, i);
		}

		readMoblityData();

		int index;// Auxiliary
		int myCount = 0;

		for (MobileDevice st : getSmartThings()) {// it makes the connection
													// between SmartThing and
													// the closest AccessPoint
			if (!ApDevice.connectApSmartThing(getApDevices(), st,
					getRand().nextDouble())) {
				myCount++;
				LogMobile.debug("AppExemplo2.java",
						st.getName() + " isn't connected");
			}
		}
		LogMobile.debug("AppExemplo2.java", "total no connection: " + myCount);

		for (ApDevice ap : getApDevices()) { // it makes the connection between
												// AccessPoint and the closest
												// ServerCloudlet
			index = Distances.theClosestServerCloudletToAp(getServerCloudlets(),
					ap);
			ap.setServerCloudlet(getServerCloudlets().get(index));
			ap.setParentId(getServerCloudlets().get(index).getId());
			getServerCloudlets().get(index).setApDevices(ap, Policies.ADD);
			NetworkTopology.addLink(serverCloudlets.get(index).getId(),
					ap.getId(), ap.getDownlinkBandwidth(),
					getRand().nextDouble());

			for (MobileDevice st : ap.getSmartThings()) {// it makes the
															// symbolic link
															// between
															// smartThing and
															// ServerCloudlet
				getServerCloudlets().get(index)
						.connectServerCloudletSmartThing(st);
				getServerCloudlets().get(index).setSmartThingsWithVm(st,
						Policies.ADD);

			}
		}
		/** STEP 3: CREATE BROKER -> example from: CloudSim - example5.java **/

		for (MobileDevice st : getSmartThings()) {
			getBrokerList().add(new FogBroker(
					"My_broker" + Integer.toString(st.getMyId())));
		}

		/**
		 * STEP 4: CREATE ONE VIRTUAL MACHINE FOR EACH BROKER/USER -> example
		 * from: CloudSim - example5.java
		 **/
		// Random rand = new Random(getSeed());
		for (MobileDevice st : getSmartThings()) {// It only creates the virtual
													// machine for each
													// smartThing
			if (st.getSourceAp() != null) {
				CloudletScheduler cloudletScheduler = new TupleScheduler(500,
						1); // CloudletSchedulerDynamicWorkload(500,1);
				long sizeVm = 128;//MaxAndMin.MIN_VM_SIZE
						//+ (long) ((MaxAndMin.MAX_VM_SIZE
								//- MaxAndMin.MIN_VM_SIZE)
								//* (getRand().nextDouble())));
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

				// Vm vmSmartThing = new Vm(st.getMyId()//id
				// , getBrokerList().get(st.getMyId()).getId()//userId -> It
				// should be the brokerId
				// , 500//mips
				// , 1//numberOfPes -> it should be the number of cpus
				// , 256//ram -> vm memory (MB)
				// , 100//bw
				// , MaxAndMin.MAX_VM_SIZE//size
				// , "Vm_"+st.getName()//vmm - I think this is the Vm name
				// , cloudletScheduler);
				st.setVmMobileDevice(vmSmartThingTest);
				st.getSourceServerCloudlet().getHost()
						.vmCreate(vmSmartThingTest);
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
						+ st.getVmLocalServerCloudlet().getActiveApplications()
						+ " Map "
						+ st.getVmLocalServerCloudlet().getApplicationMap());
				if (st.getDestinationServerCloudlet() == null) {
					System.out
							.println("Dest server: null Apps: null Map: null");
				} else {
					System.out.println("Dest server: "
							+ st.getDestinationServerCloudlet().getName()
							+ " Apps: "
							+ st.getDestinationServerCloudlet()
									.getActiveApplications()
							+ " Map " + st.getDestinationServerCloudlet()
									.getApplicationMap());
				}
			}
		}
		int i = 0;
		for (FogBroker br : getBrokerList()) {// Each broker receives one
												// smartThing's VM
			List<Vm> tempVmList = new ArrayList<>();
			tempVmList.add(getSmartThings().get(i++).getVmMobileDevice());
			br.submitVmList(tempVmList);
		}
		// identifier of the application

		/**
		 * STEP 5: CREATE THE APPLICATION -> example from: CloudSim and iFogSim
		 **/
		i = 0;

		for (FogBroker br : getBrokerList()) {
			getAppIdList().add("MyApp_vr_game" + Integer.toString(i));

			Application myApp = createApplication(getAppIdList().get(i),
					br.getId(), i,
					(AppModule) getSmartThings().get(i).getVmMobileDevice());
			getApplicationList().add(myApp);
			// getApplicationList().get(i).setUserId(br.getId());
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
					// st.getSourceServerCloudlet().getChildrenIds().add(s.getId());
				}
				for (MobileActuator a : st.getActuators()) {
					a.setUserId(brokerId);
					a.setAppId(appId);
					a.setGatewayDeviceId(st.getId());
					a.setLatency(1.0);
					a.setActuatorType("DISPLAY" + st.getMyId());
					// st.getSourceServerCloudlet().getChildrenIds().add(a.getId());
				}
			}
		}

		/**
		 * STEP 6: CREATE MAPPING, CONTROLLER, AND SUBMIT APPLICATION -> example
		 * from: iFogSim - Position3.java
		 **/

		MobileController mobileController = null;
		ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing
																			// a
																			// module
																			// mapping

		for (Application app : getApplicationList()) {
			app.setPlacementStrategy("Mapping");
		}
		i = 0;
		for (FogDevice sc : getServerCloudlets()) {
			i = 0;
			for (MobileDevice st : getSmartThings()) {
				if (st.getApDevices() != null) {
					if (sc.equals(st.getSourceServerCloudlet())) {
						// moduleMapping.addModuleToDevice("client"+i,
						// sc.getName(), 1);//MaxAndMin.MAX_SMART_THING);//
						// numOfDepts*numOfMobilesPerDept);
						// moduleMapping.addModuleToDevice("connector"+i,
						// sc.getName() ,1);// MaxAndMin.MAX_SMART_THING); //
						// fixing all instances of the Connector module to
						// cloudlets
						// moduleMapping.addModuleToDevice("concentration_calculator"+i,
						// sc.getName(), 1);//MaxAndMin.MAX_SMART_THING);
						moduleMapping.addModuleToDevice(
								((AppModule) st.getVmMobileDevice()).getName(),
								sc.getName(), 1);// MaxAndMin.MAX_SMART_THING);//
													// numOfDepts*numOfMobilesPerDept);
						moduleMapping.addModuleToDevice("client" + st.getMyId(),
								st.getName(), 1);
						// moduleMapping.addModuleToDevice("connector"+st.getMyId(),
						// st.getName(), 1);
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

					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"FIXED_MIGRATION_POINT_with_LOWEST_LATENCY");
				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET");

				}
			} else if (getMigPointPolicy() == Policies.SPEED_MIGRATION_POINT) {
				if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"SPEED_MIGRATION_POINT_with_LOWEST_LATENCY");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP");

				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
					MyStatistics.getInstance()
							.setFileMap("./outputLatencies/" + st.getMyId()
									+ "/latencies_SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
									+ getSeed() + "_st_" + st.getMyId()
									+ ".txt", st.getMyId());
					MyStatistics.getInstance().putLantencyFileName(
							"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET_seed_"
									+ getSeed() + "_st_" + st.getMyId(),
							st.getMyId());
					MyStatistics.getInstance().setToPrint(
							"SPEED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET");

				}
			}
			MyStatistics.getInstance().putLantencyFileName("Time-latency",
					st.getMyId());
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
			System.out
					.println(sc.getName() + "- X: " + sc.getCoord().getCoordX()
							+ " Y: " + sc.getCoord().getCoordY()
							+ " UpLinkLatency: " + sc.getUplinkLatency());
		}
		System.out
				.println("_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_+_");
		for (ApDevice ap : getApDevices()) {
			System.out.println(
					ap.getName() + "- X: " + ap.getCoord().getCoordX() + " Y: "
							+ ap.getCoord().getCoordY() + " connected to "
							+ ap.getServerCloudlet().getName());

		}

		System.setOut(new PrintStream("out.txt"));
		System.out.println("Inicio: "+Calendar.getInstance().getTime());
		CloudSim.startSimulation();
		System.out.println("Simulation over");
		CloudSim.stopSimulation();

	}

	private static void readMoblityData(){

		File folder = new File("input");
		File[] listOfFiles = folder.listFiles();
//		int i = 0;

		Arrays.sort(listOfFiles);
		int[] ordem = readDevicePathOrder(listOfFiles[listOfFiles.length-1]);
		for (int i=0; i < getSmartThings().size(); i++){
			readDevicePath(getSmartThings().get(i), "input/"+listOfFiles[ordem[i]].getName());
		}
//		for (MobileDevice st : getSmartThings()) {
//			readDevicePath(st, "input/"+listOfFiles[i++].getName());
//		}
	}


	private static int[] readDevicePathOrder(File filename) {

		String line = "";
		String cvsSplitBy = "\t";

		try (BufferedReader br = new BufferedReader(new FileReader(filename))) {

			int i=1;
			while (((line = br.readLine()) != null)){
//				if(i == getSeed()) {
				if(i == 1) {
					break;
				}
				i++;
			}
				// use comma as separator
				String[] position = line.split(cvsSplitBy);
				int ordem[] = new int[getSmartThings().size()];
				for (int j=0; j < getSmartThings().size(); j++){
					ordem[j] = Integer.valueOf(position[j]);
				}				
				Arrays.sort(ordem);
				return ordem;
				// System.out.println(country[0]+"
				// "+Double.parseDouble(country[0])*(180/Math.PI)+"
				// "+country[1]+" "+country[2]+" "+country[3]);


		}
		catch (IOException e) {
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

				// System.out.println(country[0]+"
				// "+Double.parseDouble(country[0])*(180/Math.PI)+"
				// "+country[1]+" "+country[2]+" "+country[3]);

				st.getPath().add(position);
			}

			Coordinate coordinate = new Coordinate();
			coordinate.setInitialCoordinate(st);
			saveMobility(st);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void saveMobility(MobileDevice st){
//		System.out.println(st.getMyId() + " Position: " + st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: " + st.getSpeed());
//		System.out.println("Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp() + " Host: " + st.getHost().getId());
//		System.out.println("Local server: " + st.getVmLocalServerCloudlet().getName() + " Apps " + st.getVmLocalServerCloudlet().getActiveApplications() + " Map " + st.getVmLocalServerCloudlet().getApplicationMap());
//		if(st.getDestinationServerCloudlet() == null){
//			System.out.println("Dest server: null Apps: null Map: null");
//		}
//		else{
//			System.out.println("Dest server: " + st.getDestinationServerCloudlet().getName() + " Apps: " + st.getDestinationServerCloudlet().getActiveApplications() +  " Map " + st.getDestinationServerCloudlet().getApplicationMap());
//		}
		try(FileWriter fw1 = new FileWriter(st.getMyId()+"out.txt", true);
			    BufferedWriter bw1 = new BufferedWriter(fw1);
			    PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(st.getMyId() + " Position: " + st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: " + st.getSpeed());
			out1.println("Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp() + " Host: " + st.getHost().getId());
			out1.println("Local server: null  Apps null Map null");
			if(st.getDestinationServerCloudlet() == null){
				out1.println("Dest server: null Apps: null Map: null");
			}
			else{
				out1.println("Dest server: " + st.getDestinationServerCloudlet().getName() + " Apps: " + st.getDestinationServerCloudlet().getActiveApplications() +  " Map " + st.getDestinationServerCloudlet().getApplicationMap());
			}
		}
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try(FileWriter fw = new FileWriter(st.getMyId()+"route.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" + st.getSpeed() + "\t" + CloudSim.clock());
		}
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void addApDevicesFixed(List<ApDevice> apDevices,
			Coordinate coordDevices) {
		int i = 0;
		boolean control = true;
		int coordY = 0;
//		 for(int coordX=1050; coordX<MaxAndMin.MAX_X-990; coordX+=2001){
		for (int coordX = 0; coordX < MaxAndMin.MAX_X; coordX += (2
				* MaxAndMin.AP_COVERAGE
				- (2 * MaxAndMin.AP_COVERAGE / 3))) { /* evenly distributed */
			System.out.println("Creating Ap devices");
			// if(control){
			// coordY=4000;
			// }
			// else{
			// coordY = 10500;
			// }
			// control=!control;
			// for(coordY=1050; coordY<MaxAndMin.MAX_Y-990; coordY+=2001, i++){
			for (coordY = 0; coordY < MaxAndMin.MAX_Y; coordY += (2
					* MaxAndMin.AP_COVERAGE
					- (2 * MaxAndMin.AP_COVERAGE / 3)), i++) {
				// if(coordDevices.getPositions(coordX, coordY)==-1){
				// ApDevice ap = new
				// ApDevice("AccessPoint"+Integer.toString(i),coordX,coordY,i);//my
				// construction
				ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
						coordX, coordY, i// ap.set//id
						, 100 * 1024 * 1024// downLinkBandwidth - 100Mbits
						, 200// engergyConsuption
						, MaxAndMin.MAX_ST_IN_AP// maxSmartThing
						, 100 * 1024 * 1024// upLinkBandwidth - 100Mbits
						, 4// upLinkLatency
				);// ver valores reais e melhores
				apDevices.add(i, ap);
				// coordDevices.setPositions(ap.getId(),
				// ap.getCoord().getCoordX(), ap.getCoord().getCoordY());
			}
		}
		LogMobile.debug("AppExemplo2.java", "Total of accessPoints: " + i);

	}

	private static void addApDevicesRandon(List<ApDevice> apDevices,
			Coordinate coordDevices, int i) {
		// Random rand = new Random((Integer.MAX_VALUE/getSeed())*(i+1));
		int coordX, coordY;
		// while(true){
		coordX = getRand().nextInt(MaxAndMin.MAX_X);
		coordY = getRand().nextInt(MaxAndMin.MAX_Y);
		// if(coordDevices.getPositions(coordX, coordY)==-1){//verify if it is
		// empty
		// ApDevice ap = new
		// ApDevice("AccessPoint"+Integer.toString(i),coordX,coordY,i);//my
		// construction
		ApDevice ap = new ApDevice("AccessPoint" + Integer.toString(i), // name
				coordX, coordY, i// id
				, 100 * 1024 * 1024// downLinkBandwidth - 100 Mbits
				, 200// engergyConsuption
				, MaxAndMin.MAX_ST_IN_AP// maxSmartThing
				, 100 * 1024 * 1024// upLinkBandwidth 100 Mbits
				, 4// upLinkLatency
		);// ver valores reais e melhores
		apDevices.add(i, ap);
		// coordDevices.setPositions(ap.getId(), ap.getCoord().getCoordX(),
		// ap.getCoord().getCoordY());

		// System.out.println("i: "+i);
		// break;
		// }
		// else {
		// LogMobile.debug("AppExemplo2.java", "POSITION ISN'T AVAILABLE... (AP)
		// X ="+ coordX+ " Y = " +coordY+" Reallocating..." );
		// }
		// }
	}

	public static void addSmartThing(List<MobileDevice> smartThing,
			Coordinate coordDevices, int i) {

		// Random rand = new Random((Integer.MAX_VALUE*getSeed())/(i+1));
		int coordX=0, coordY=0;
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

		MobileSensor sensor = new MobileSensor("Sensor" + i // Tuple's name ->
															// ACHO QUE DÁ PARA
															// USAR ESTE
															// CONSTRUTOR
				, "EEG" + i // Tuple's type
				, i // User Id
				, "MyApp_vr_game" + i // app's name
				, distribution0);
		sensors.add(sensor);

		// MobileSensor sensor1 = new MobileSensor("Sensor1" //Tuple's name ->
		// ACHO QUE DÁ PARA USAR ESTE CONSTRUTOR
		// ,"EEG" //Tuple's type
		// ,i //User Id
		// ,"appId1" //app's name
		// ,distribution0 );
		// sensors.add(sensor1);

		// MobileSensor sensor0 = new MobileSensor("Sensor0" //Tuple's name ->
		// ACHO QUE DÁ PARA USAR ESTE CONSTRUTOR
		// ,"EEG" //Tuple's type
		// ,i //User Id
		// ,"appId0" //app's name
		// ,distribution0);// find into the paper about tuples and distribution
		// MobileSensor sensor1 = new
		// MobileSensor("Sensor1","EEG",i,"appId1",distribution1);
		// MobileSensor sensor2 = new MobileSensor("Sensor2"// tuple's name
		// , i// userId
		// , "EEG"//Tuple's name
		// , -1//gatewayDeviceId - I think it is the ServerCloudlet id - as it
		// not creation yet, -1
		// , 20//latency
		// , null//geoLocation - it uses the MobileDevice's localization
		// , distribution2//transmitDistribution
		// , 2000//cpuLength
		// , 10//nwLength
		// , "EEG"
		// , "destModuleName2");
		//
		// MobileSensor sensor3 = new MobileSensor("Sensor3"
		// , i// userId
		// , "EEG"
		// , -1//gatewayDeviceId - I think it is the ServerCloudlet id - as it
		// not creation yet, -1
		// , 20//latency
		// , null//geoLocation - it uses the MobileDevice's localization
		// , distribution3//transmitDistribution
		// , 2000//cpuLength
		// , 10//nwLength
		// , "EEG"
		// , "destModuleName3");
		// Set<MobileSensor> sensors = new HashSet<>();
		// sensors.add(sensor0);
		// sensors.add(sensor1);
		// sensors.add(sensor2);
		// sensors.add(sensor3);

		/*************** End set of Mobile Sensors ****************/

		/*************** Start set of Mobile Actuators ****************/

		MobileActuator actuator0 = new MobileActuator("Actuator" + i, i,
				"MyApp_vr_game" + i, "DISPLAY" + i);

		// MobileActuator actuator1 = new MobileActuator("Actuator1", i,
		// "appId1", "actuatorType1");
		// MobileActuator actuator2 = new MobileActuator("Actuator2"
		// , i// userId
		// , "appId2"
		// , -1 //gatewayDeviceId
		// , 2 //latency
		// , null //geoLocation
		// , "actuatorType2"
		// , "srcModuleName2");
		// MobileActuator actuator3 = new MobileActuator("Actuator"+i
		// , i// userId
		// , "MyApp_vr_game"+i
		// , -1 //gatewayDeviceId
		// , 2 //latency
		// , null //geoLocation
		// , "DISPLAY"+i
		// , "DISPLAY"+i); // ../..PAREI AQUI: POR QUE O ATUADOR ESTA RECEBENDO
		// EM UM DISPLAY DIFERENTE

		Set<MobileActuator> actuators = new HashSet<>();
		// actuators.add(actuator0);
		// actuators.add(actuator1);
		// actuators.add(actuator2);
		actuators.add(actuator0);

		/*************** End set of Mobile Actuators ****************/

		/*************** Start MobileDevice Configurations ****************/

		FogLinearPowerModel powerModel = new FogLinearPowerModel(87.53d,
				82.44d);// 10//maxPower

		List<Pe> peList = new ArrayList<>();
		int mips = 46533;
		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to
																	// storage
																	// Pe id and
																	// MIPS
																	// Rating -
																	// to
																	// CloudSim

		int hostId = FogUtils.generateEntityId();
		long storage = 512*1024;
		// host storage
		int bw = 1000 * 1024 * 1024;
		int ram = 1024*16;
		PowerHost host = new PowerHost(// To the hardware's characteristics
										// (MobileDevice) - to CloudSim
				hostId, new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw), storage, peList,
				new StreamOperatorScheduler(peList), powerModel);

		List<Host> hostList = new ArrayList<Host>();// why to create a list?
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Android"; // operating system
		String vmm = "empty";// Empty
		double vmSize = 4;
		double time_zone = 10.0; // time zone this resource located
		double cost = 1.0; // the cost of using processing in this resource
		double costPerMem = 0.005; // the cost of using memory in this resource
		double costPerStorage = 0.0001; // the cost of using storage in this
		// resource
		double costPerBw = 0.001; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are
																		// not
																		// adding
																		// SAN
		// devices by now

		// for Characteristics

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(
				hostList);

		MobileDevice st = null;
		// Vm vmTemp = new Vm(id, userId, mips, numberOfPes, ram, bw, size, vmm,
		// cloudletScheduler);
		float maxServiceValue = getRand().nextFloat() * 100;
		try {

//			while (true) {
//				coordX = getRand().nextInt((int) (MaxAndMin.MAX_X * 0.8));
//				coordY = getRand().nextInt((int) (MaxAndMin.MAX_Y * 0.8));
//				if ((coordX < MaxAndMin.MAX_X * 0.2)
//						|| (coordY < MaxAndMin.MAX_Y * 0.2)) {
//					continue;
//				}
//				// if(coordDevices.getPositions(coordX, coordY)==-1){//verify if
//				// it is empty

				st = new MobileDevice("SmartThing" + Integer.toString(i),
						characteristics, vmAllocationPolicy// - seria a maquina
															// que executa
															// dentro do
															// fogDevice?
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
				// coordDevices.setPositions(st.getId(),
				// st.getCoord().getCoordX(), st.getCoord().getCoordY());
//				break;
				// }
				// else{
				// LogMobile.debug("AppExemplo2.java","POSITION ISN'T
				// AVAILABLE... (ST)X ="+ coordX+ " Y = " +coordY+"
				// Reallocating..." );
				// }
//			}
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
			Coordinate coordDevices, int i) {

		// Random rand = new Random(((Long.MAX_VALUE/getSeed())/(i*i+1))*2);
		int coordX, coordY;
		DecisionMigration migrationStrategy;
		if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
			migrationStrategy = new LowestLatency(getServerCloudlets(),
					getApDevices(), getMigPointPolicy(), getPolicyReplicaVM());
		} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
			migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
					getServerCloudlets(), getApDevices(), getMigPointPolicy(),
					getPolicyReplicaVM());

		} else { //Policies.LOWEST_DIST_BW_SMARTTING_AP
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

		FogLinearPowerModel powerModel = new FogLinearPowerModel(107.339d,
				83.433d);// 10//maxPower

		List<Pe> peList = new ArrayList<>();// CloudSim Pe (Processing Element)
											// class represents CPU unit,
											// defined in terms of Millions
		// * Instructions Per Second (MIPS) rating
		int mips = 3234;
		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to
																	// store Pe
																	// id and
																	// MIPS
																	// Rating -
																	// to
																	// CloudSim

		int hostId = FogUtils.generateEntityId();
		long storage = 16 * 1024 * 1024;// Long.MAX_VALUE; // host storage
		int bw = 1000 * 1024 * 1024;
		int ram = 1024;// host memory (MB)
		PowerHost host = new PowerHost(// To the hardware's characteristics
										// (MobileDevice) - to CloudSim
				hostId, new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw), storage, peList,
				new StreamOperatorScheduler(peList), powerModel);

		List<Host> hostList = new ArrayList<Host>();// why to create a list?
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Empty";// Empty
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
		// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are
																		// not
																		// adding
																		// SAN
		// devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(
				hostList);
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

			// while(true){
			coordX = getRand().nextInt(MaxAndMin.MAX_X);
			coordY = getRand().nextInt(MaxAndMin.MAX_X);
			// if(coordDevices.getPositions(coordX, coordY)==-1){//verify if it
			// is empty
			double maxBandwidth = getMaxBandwidth() * 1024 * 1024;// MaxAndMin.MAX_BANDWIDTH;
			double minBandwidth = (getMaxBandwidth() - 1) * 1024 * 1024;// MaxAndMin.MIN_BANDWIDTH;
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
			// coordDevices.setPositions(sc.getId(), sc.getCoord().getCoordX(),
			// sc.getCoord().getCoordY());

			// break;
			// }
			// else{
			// LogMobile.debug("AppExemplo2.java","POSITION ISN'T AVAILABLE...
			// (SC)X ="+ coordX+ " Y = " +coordY+" Reallocating..." );
			//
			// }
			// }

		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} // id
	}

	public static void addServerCloudlet(List<FogDevice> serverCloudlets,
			Coordinate coordDevices) {
		int i = 0;
		int coordX, coordY;

		// for(coordX=1001; coordX<MaxAndMin.MAX_X-990; coordX+=2001){ /*evenly
		// distributed*/
		for (coordX = 0; coordX < MaxAndMin.MAX_X; coordX += (2
				* MaxAndMin.CLOUDLET_COVERAGE
				- (2 * MaxAndMin.CLOUDLET_COVERAGE / 3))) { /* evenly distributed */
			System.out.println("Creating Server cloudlets");
			// for(coordY=1001; coordY<MaxAndMin.MAX_Y-990; coordY+=2001, i++){
			// /*evenly distributed*/
			for (coordY = 0; coordY < MaxAndMin.MAX_X; coordY += (2
					* MaxAndMin.CLOUDLET_COVERAGE
					- (2 * MaxAndMin.CLOUDLET_COVERAGE
							/ 3)), i++) { /* evenly distributed */
				// Random rand = new
				// Random(getSeed()*(i+1));//((Long.MAX_VALUE/getSeed())/(i+1))*2);
				DecisionMigration migrationStrategy;
				if (getMigStrategyPolicy() == Policies.LOWEST_LATENCY) {
					migrationStrategy = new LowestLatency(getServerCloudlets(),
							getApDevices(), getMigPointPolicy(),
							getPolicyReplicaVM());
				} else if (getMigStrategyPolicy() == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
					migrationStrategy = new LowestDistBwSmartThingServerCloudlet(
							getServerCloudlets(), getApDevices(),
							getMigPointPolicy(), getPolicyReplicaVM());
				} else { //LOWEST_DIST_BW_SMARTTING_AP
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

				FogLinearPowerModel powerModel = new FogLinearPowerModel(
						107.339d, 83.433d);// 10//maxPower

				List<Pe> peList = new ArrayList<>();// CloudSim Pe (Processing
													// Element) class represents
													// CPU unit, defined in
													// terms of Millions
				// * Instructions Per Second (MIPS) rating
				int mips = 3234;
				// 3. Create PEs and add these into a list.
				peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need
																			// to
																			// store
																			// Pe
																			// id
																			// and
																			// MIPS
																			// Rating
																			// -
																			// to
																			// CloudSim

				int hostId = FogUtils.generateEntityId();
				long storage = 16 * 1024 * 1024;// Long.MAX_VALUE; // host
													// storage
				int bw = 1000 * 1024 * 1024;
				int ram = 1024;// host memory (MB)
				PowerHost host = new PowerHost(// To the hardware's
												// characteristics
												// (MobileDevice) - to CloudSim
						hostId, new RamProvisionerSimple(ram),
						new BwProvisionerOverbooking(bw), storage, peList,
						new StreamOperatorScheduler(peList), powerModel);

				List<Host> hostList = new ArrayList<Host>();// why to create a
															// list?
				hostList.add(host);

				String arch = "x86"; // system architecture
				String os = "Linux"; // operating system
				String vmm = "Empty";// Empty
				double time_zone = 10.0; // time zone this resource located
				double cost = 3.0; // the cost of using processing in this
									// resource
				double costPerMem = 0.05; // the cost of using memory in this
											// resource
				double costPerStorage = 0.001; // the cost of using storage in
												// this
				// resource
				double costPerBw = 0.0; // the cost of using bw in this resource
				LinkedList<Storage> storageList = new LinkedList<Storage>(); // we
																				// are
																				// not
																				// adding
																				// SAN
				// devices by now
				FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
						arch, os, vmm, host, time_zone, cost, costPerMem,
						costPerStorage, costPerBw);

				AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(
						hostList);
				FogDevice sc = null;
				Service serviceOffer = new Service();
				serviceOffer.setType(
						getRand().nextInt(10000) % MaxAndMin.MAX_SERVICES);
				if (serviceOffer.getType() == Services.HIBRID
						|| serviceOffer.getType() == Services.PUBLIC) {
					serviceOffer.setValue(getRand().nextFloat() * 10);
				} else {
					serviceOffer.setValue(0);
				}
				try {
					// if(coordDevices.getPositions(coordX, coordY)==-1){
					// ApDevice ap = new
					// ApDevice("AccessPoint"+Integer.toString(i),coordX,coordY,i);//my
					// construction
					double maxBandwidth = getMaxBandwidth() * 1024 * 1024;// MaxAndMin.MAX_BANDWIDTH;
					double minBandwidth = (getMaxBandwidth() - 1) * 1024 * 1024;// MaxAndMin.MIN_BANDWIDTH;
					double upLinkRandom = minBandwidth
							+ (maxBandwidth - minBandwidth)
									* getRand().nextDouble();
					double downLinkRandom = minBandwidth
							+ (maxBandwidth - minBandwidth)
									* getRand().nextDouble();
					sc = new FogDevice("ServerCloudlet" + Integer.toString(i) // name
							, characteristics, vmAllocationPolicy// vmAllocationPolicy
							, storageList, 10// schedulingInterval
							, upLinkRandom// uplinkBandwidth
							, downLinkRandom// downlinkBandwidth
							, 4// rand.nextDouble()//uplinkLatency
							, 0.01// mipsPer..
							, coordX, coordY, i, serviceOffer,
							migrationStrategy, getPolicyReplicaVM(),
							beforeMigration);
					serverCloudlets.add(i, sc);
					// coordDevices.setPositions(sc.getId(),
					// sc.getCoord().getCoordX(), sc.getCoord().getCoordY());
					sc.setParentId(-1);

					// }

				}
				catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // id

			}
		}
		LogMobile.debug("AppExemplo2.java", "Total of serverCloudlets: " + i);
	}

	private static void createServerCloudletsNetwork(List<FogDevice> serverCloudlets) {
		// for no full graph, use -1 to link
		HashMap<FogDevice, Double> net = new HashMap<>();
		// Random rand = new Random(100*getSeed());
		int i=0, j=0, linha, coluna;
		for (FogDevice sc : serverCloudlets) {// It makes a full graph
			j=0;
			for (FogDevice sc1 : serverCloudlets) {
				if (sc.equals(sc1)) {
					break;
				}

				// if(rand.nextInt(100)%20 == 0){
				// break;
				// }
				// net.keySet().add(sc1);
				linha=((int)(j/12)-(int)(i/12));
				if(linha < 0) linha*=-1;
				coluna=((int)(j%12)-(int)(i%12));
				if(coluna < 0) coluna*=-1;
				if (sc.getUplinkBandwidth() < sc1.getDownlinkBandwidth()) {
					net.put(sc1, sc.getUplinkBandwidth());
					NetworkTopology.addLink(sc.getId(), sc1.getId(),
							sc.getUplinkBandwidth(), (Math.max(linha, coluna))*getLatencyBetweenCloudlets()+getRand().nextDouble());
				} else {
					net.put(sc1, sc1.getDownlinkBandwidth());
					NetworkTopology.addLink(sc.getId(), sc1.getId(),
							sc1.getDownlinkBandwidth(), (Math.max(linha, coluna))*getLatencyBetweenCloudlets()+getRand().nextDouble());
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

		Application application = Application.createApplication(appId, userId); // creates
																				// an
																				// empty
																				// application
																				// model
																				// (empty
																				// directed
																				// graph)
		application.addAppModule(userVm); // adding module Client to the
											// application model
		application.addAppModule("client" + myId, "appModuleClient" + myId, 10);
		// application.addAppModule("connector"+myId, 10);

		/*
		 * Connecting the application modules (vertices) in the application
		 * model (directed graph) with edges
		 */
		if (EEG_TRANSMISSION_TIME >= 10)
			application.addAppEdge("EEG" + myId, "client" + myId, 966, 54,
					"EEG" + myId, Tuple.UP, AppEdge.SENSOR); // adding edge from
																// EEG (sensor)
																// to Client
																// module
																// carrying
																// tuples of
																// type EEG
		else
			application.addAppEdge("EEG" + myId, "client" + myId, 966, 54,
					"EEG" + myId, Tuple.UP, AppEdge.SENSOR);

		// application.addAppEdge(source, destination, tupleCpuLength,
		// tupleNwLength, tupleType, direction, edgeType)
		application.addAppEdge("client" + myId, userVm.getName(), 966, 54,
				"_SENSOR" + myId, Tuple.UP, AppEdge.MODULE); // adding edge from
																// Client to
																// Concentration
																// Calculator
																// module
																// carrying
																// tuples of
																// type _SENSOR
		// application.addAppEdge(source, destination, periodicity,
		// tupleCpuLength, tupleNwLength, tupleType, direction, edgeType)?

		application.addAppEdge(userVm.getName(), userVm.getName(), 1000, 966,
				54, "PLAYER_GAME_STATE" + myId, Tuple.UP, AppEdge.MODULE); // adding
																				// periodic
																				// edge
																				// (period=1000ms)
																				// from
																				// Concentration
																				// Calculator
																				// to
																				// Connector
																				// module
																				// carrying
																				// tuples
																				// of
																				// type
																				// PLAYER_GAME_STATE
		application.addAppEdge(userVm.getName(), "client" + myId, 2439, 87,
				"CONCENTRATION" + myId, Tuple.DOWN, AppEdge.MODULE); // adding
																		// edge
																		// from
																		// Concentration
																		// Calculator
																		// to
																		// Client
																		// module
																		// carrying
																		// tuples
																		// of
																		// type
																		// CONCENTRATION
		application.addAppEdge(userVm.getName(), "client" + myId, 2439, 28,
				87, "GLOBAL_GAME_STATE" + myId, Tuple.DOWN, AppEdge.MODULE); // adding
																				// periodic
																				// edge
																				// (period=1000ms)
																				// from
																				// Connector
																				// to
																				// Client
																				// module
																				// carrying
																				// tuples
																				// of
																				// type
																				// GLOBAL_GAME_STATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 2439, 87,
				"SELF_STATE_UPDATE" + myId, Tuple.DOWN, AppEdge.ACTUATOR); // adding
																			// edge
																			// from
																			// Client
																			// module
																			// to
																			// Display
																			// (actuator)
																			// carrying
																			// tuples
																			// of
																			// type
																			// SELF_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 2439, 87,
				"GLOBAL_STATE_UPDATE" + myId, Tuple.DOWN, AppEdge.ACTUATOR); // adding
																				// edge
																				// from
																				// Client
																				// module
																				// to
																				// Display
																				// (actuator)
																				// carrying
																				// tuples
																				// of
																				// type
																				// GLOBAL_STATE_UPDATE

		/*
		 * Defining the input-output relationships (represented by selectivity)
		 * of the application modules.
		 */

		application.addTupleMapping("client" + myId, "EEG" + myId,
				"_SENSOR" + myId, new FractionalSelectivity(0.9)); // 0.9 tuples
																	// of type
																	// _SENSOR
																	// are
																	// emitted
																	// by Client
																	// module
																	// per
																	// incoming
																	// tuple of
																	// type EEG
		application.addTupleMapping("client" + myId, "CONCENTRATION" + myId,
				"SELF_STATE_UPDATE" + myId, new FractionalSelectivity(1.0)); // 1.0
																				// tuples
																				// of
																				// type
																				// SELF_STATE_UPDATE
																				// are
																				// emitted
																				// by
																				// Client
																				// module
																				// per
																				// incoming
																				// tuple
																				// of
																				// type
																				// CONCENTRATION
		application.addTupleMapping(userVm.getName(), "_SENSOR" + myId,
				"CONCENTRATION" + myId, new FractionalSelectivity(1.0)); // 1.0
																			// tuples
																			// of
																			// type
																			// CONCENTRATION
																			// are
																			// emitted
																			// by
																			// Concentration
																			// Calculator
																			// module
																			// per
																			// incoming
																			// tuple
																			// of
																			// type
																			// _SENSOR
		application.addTupleMapping("client" + myId, "GLOBAL_GAME_STATE" + myId,
				"GLOBAL_STATE_UPDATE" + myId, new FractionalSelectivity(1.0)); // 1.0
																				// tuples
																				// of
																				// type
																				// GLOBAL_STATE_UPDATE
																				// are
																				// emitted
																				// by
																				// Client
																				// module
																				// per
																				// incoming
																				// tuple
																				// of
																				// type
																				// GLOBAL_GAME_STATE

		/*
		 * Defining application loops to monitor the latency of.
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client ->
		 * Concentration Calculator -> Client -> DISPLAY (actuator)
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
	private static Application createApplication(String appId, int userId,
			int myId) {

		Application application = Application.createApplication(appId, userId); // creates
																				// an
																				// empty
																				// application
																				// model
																				// (empty
																				// directed
																				// graph)

		application.addAppModule("client" + myId, 10); // adding module Client
														// to the application
														// model
		application.addAppModule("concentration_calculator" + myId, 10); // adding
																			// module
																			// Concentration
																			// Calculator
																			// to
																			// the
																			// application
																			// model
		application.addAppModule("connector" + myId, 10); // adding module
															// Connector to the
															// application model

		/*
		 * Connecting the application modules (vertices) in the application
		 * model (directed graph) with edges
		 */
		if (EEG_TRANSMISSION_TIME == 10)
			application.addAppEdge("EEG" + myId, "client" + myId, 2000, 500,
					"EEG" + myId, Tuple.UP, AppEdge.SENSOR); // adding edge from
																// EEG (sensor)
																// to Client
																// module
																// carrying
																// tuples of
																// type EEG
		else
			application.addAppEdge("EEG" + myId, "client" + myId, 3000, 500,
					"EEG" + myId, Tuple.UP, AppEdge.SENSOR);

		// application.addAppEdge(source, destination, tupleCpuLength,
		// tupleNwLength, tupleType, direction, edgeType)
		application.addAppEdge("client" + myId,
				"concentration_calculator" + myId, 3500, 500, "_SENSOR",
				Tuple.UP, AppEdge.MODULE); // adding edge from Client to
											// Concentration Calculator module
											// carrying tuples of type _SENSOR
		// application.addAppEdge(source, destination, periodicity,
		// tupleCpuLength, tupleNwLength, tupleType, direction, edgeType)?
		application.addAppEdge("concentration_calculator" + myId,
				"connector" + myId, 1000, 1000, 1000, "PLAYER_GAME_STATE",
				Tuple.UP, AppEdge.MODULE); // adding periodic edge
											// (period=1000ms) from
											// Concentration Calculator to
											// Connector module carrying tuples
											// of type PLAYER_GAME_STATE

		application.addAppEdge("concentration_calculator" + myId,
				"client" + myId, 14, 500, "CONCENTRATION", Tuple.DOWN,
				AppEdge.MODULE); // adding edge from Concentration Calculator to
									// Client module carrying tuples of type
									// CONCENTRATION
		application.addAppEdge("connector" + myId, "client" + myId, 1000, 28,
				1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE); // adding
																		// periodic
																		// edge
																		// (period=1000ms)
																		// from
																		// Connector
																		// to
																		// Client
																		// module
																		// carrying
																		// tuples
																		// of
																		// type
																		// GLOBAL_GAME_STATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 1000, 500,
				"SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR); // adding
																	// edge from
																	// Client
																	// module to
																	// Display
																	// (actuator)
																	// carrying
																	// tuples of
																	// type
																	// SELF_STATE_UPDATE
		application.addAppEdge("client" + myId, "DISPLAY" + myId, 1000, 500,
				"GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR); // adding
																		// edge
																		// from
																		// Client
																		// module
																		// to
																		// Display
																		// (actuator)
																		// carrying
																		// tuples
																		// of
																		// type
																		// GLOBAL_STATE_UPDATE

		/*
		 * Defining the input-output relationships (represented by selectivity)
		 * of the application modules.
		 */
		application.addTupleMapping("client" + myId, "EEG" + myId, "_SENSOR",
				new FractionalSelectivity(0.9)); // 0.9 tuples of type _SENSOR
													// are emitted by Client
													// module per incoming tuple
													// of type EEG
		application.addTupleMapping("client" + myId, "CONCENTRATION",
				"SELF_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0
																		// tuples
																		// of
																		// type
																		// SELF_STATE_UPDATE
																		// are
																		// emitted
																		// by
																		// Client
																		// module
																		// per
																		// incoming
																		// tuple
																		// of
																		// type
																		// CONCENTRATION
		application.addTupleMapping("concentration_calculator" + myId,
				"_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0)); // 1.0
																				// tuples
																				// of
																				// type
																				// CONCENTRATION
																				// are
																				// emitted
																				// by
																				// Concentration
																				// Calculator
																				// module
																				// per
																				// incoming
																				// tuple
																				// of
																				// type
																				// _SENSOR
		application.addTupleMapping("client" + myId, "GLOBAL_GAME_STATE",
				"GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0
																		// tuples
																		// of
																		// type
																		// GLOBAL_STATE_UPDATE
																		// are
																		// emitted
																		// by
																		// Client
																		// module
																		// per
																		// incoming
																		// tuple
																		// of
																		// type
																		// GLOBAL_GAME_STATE

		/*
		 * Defining application loops to monitor the latency of.
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client ->
		 * Concentration Calculator -> Client -> DISPLAY (actuator)
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
		AppExemplo2.policyReplicaVM = policyReplicaVM;
	}

	public static int getTravelPredicTimeForST() {
		return travelPredicTimeForST;
	}

	public static void setTravelPredicTimeForST(int travelPredicTimeForST) {
		AppExemplo2.travelPredicTimeForST = travelPredicTimeForST;
	}

	public static int getMobilityPrecitionError() {
		return mobilityPrecitionError;
	}

	public static void setMobilityPredictionError(int mobilityPrecitionError) {
		AppExemplo2.mobilityPrecitionError = mobilityPrecitionError;
	}
	
	public static double getLatencyBetweenCloudlets() {
		return latencyBetweenCloudlets;
	}

	public static void setLatencyBetweenCloudlets(double latencyBetweenCloudlets) {
		AppExemplo2.latencyBetweenCloudlets = latencyBetweenCloudlets;
	}

	public static int getStepPolicy() {
		return stepPolicy;
	}

	public static void setStepPolicy(int stepPolicy) {
		AppExemplo2.stepPolicy = stepPolicy;
	}

	public static List<MobileDevice> getSmartThings() {
		return smartThings;
	}

	public static void setSmartThings(List<MobileDevice> smartThings) {
		AppExemplo2.smartThings = smartThings;
	}

	public static List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}

	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		AppExemplo2.serverCloudlets = serverCloudlets;
	}

	public static List<ApDevice> getApDevices() {
		return apDevices;
	}

	public static void setApDevices(List<ApDevice> apDevices) {
		AppExemplo2.apDevices = apDevices;
	}

	public static int getMigPointPolicy() {
		return migPointPolicy;
	}

	public static void setMigPointPolicy(int migPointPolicy) {
		AppExemplo2.migPointPolicy = migPointPolicy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigStrategyPolicy(int migStrategyPolicy) {
		AppExemplo2.migStrategyPolicy = migStrategyPolicy;
	}

	public static int getPositionApPolicy() {
		return positionApPolicy;
	}

	public static void setPositionApPolicy(int positionApPolicy) {
		AppExemplo2.positionApPolicy = positionApPolicy;
	}

	public static Coordinate getCoordDevices() {
		return coordDevices;
	}

	public static void setCoordDevices(Coordinate coordDevices) {
		AppExemplo2.coordDevices = coordDevices;
	}

	public static List<FogBroker> getBrokerList() {
		return brokerList;
	}

	public static void setBrokerList(List<FogBroker> brokerList) {
		AppExemplo2.brokerList = brokerList;
	}

	public static List<String> getAppIdList() {
		return appIdList;
	}

	public static void setAppIdList(List<String> appIdList) {
		AppExemplo2.appIdList = appIdList;
	}

	public static List<Application> getApplicationList() {
		return applicationList;
	}

	public static void setApplicationList(List<Application> applicationList) {
		AppExemplo2.applicationList = applicationList;
	}

	public static int getSeed() {
		return seed;
	}

	public static void setSeed(int seed) {
		AppExemplo2.seed = seed;
	}

	public static int getPositionScPolicy() {
		return positionScPolicy;
	}

	public static void setPositionScPolicy(int positionScPolicy) {
		AppExemplo2.positionScPolicy = positionScPolicy;
	}

	public static int getMaxSmartThings() {
		return maxSmartThings;
	}

	public static void setMaxSmartThings(int maxSmartThings) {
		AppExemplo2.maxSmartThings = maxSmartThings;
	}

	public static Random getRand() {
		return rand;
	}

	public static void setRand(Random rand) {
		AppExemplo2.rand = rand;
	}

	public static int getMaxBandwidth() {
		return maxBandwidth;
	}

	public static void setMaxBandwidth(int maxBandwidth) {
		AppExemplo2.maxBandwidth = maxBandwidth;
	}

	public static boolean isMigrationAble() {
		return migrationAble;
	}

	public static void setMigrationAble(boolean migrationAble) {
		AppExemplo2.migrationAble = migrationAble;
	}

}
