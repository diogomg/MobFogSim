package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.Sensor;
import org.fog.localization.Coordinate;
import org.fog.localization.Distances;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileUserApplicationFactory;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.scheduler.TupleScheduler;

public class MobileController extends SimEntity {
	private static boolean migrationAble;
	private static int migPointPolicy;

	private static int stepPolicy; // Quantity of steps in the nextStep Function
	private static Coordinate coordDevices;

	private static int migStrategyPolicy;
	private static int seed;

	private static List<FogDevice> serverCloudlets;
	private static List<MobileDevice> smartThings;
	private static final Map<String, Integer> activeSensorApplications =
		new HashMap<String, Integer>();
	private static List<ApDevice> apDevices;
	private static List<FogBroker> brokerList;
	private static Map<Integer, FogBroker> brokersByMobileId =
		new HashMap<Integer, FogBroker>();

	private Map<String, Application> applications;
	private Map<String, Integer> appLaunchDelays;
	private ModuleMapping moduleMapping;
	private Map<Integer, Double> globalCurrentCpuLoad;
	private boolean shutdownRequested;

	static final int numOfDepts = 1;
	static final int numOfMobilesPerDept = 4;
	private static Random rand;

	public MobileController() {

	}

	public MobileController(String name, List<FogDevice> serverCloudlets, List<ApDevice> apDevices,
		List<MobileDevice> smartThings, List<FogBroker> brokers, ModuleMapping moduleMapping
		, int migPointPolicy, int migStrategyPolicy, int stepPolicy, Coordinate coordDevices,
		int seed, boolean migrationAble) {
		super(name);
		this.applications = new HashMap<String, Application>();
		this.globalCurrentCpuLoad = new HashMap<Integer, Double>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setModuleMapping(moduleMapping);
		for (FogDevice sc : serverCloudlets) {
			sc.setControllerId(getId());
		}
		setSeed(seed);
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setSmartThings(smartThings);
		setBrokerList(brokers);
		setBrokersByMobileId(indexBrokersByMobileId(smartThings, brokers));
		setMigPointPolicy(migPointPolicy);
		setMigStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		setRand(new Random(getSeed() * Long.MAX_VALUE));
		setMigrationAble(migrationAble);
	}

	public MobileController(String name, List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, List<MobileDevice> smartThings,
		int migPointPolicy, int migStrategyPolicy, int stepPolicy,
		Coordinate coordDevices, int seed) {
		super(name);
		this.applications = new HashMap<String, Application>();
		this.globalCurrentCpuLoad = new HashMap<Integer, Double>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setModuleMapping(moduleMapping);
		for (FogDevice sc : serverCloudlets) {
			sc.setControllerId(getId());
		}
		setSeed(seed);
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setSmartThings(smartThings);
		setBrokerList(new ArrayList<FogBroker>());
		setBrokersByMobileId(new HashMap<Integer, FogBroker>());
		setMigPointPolicy(migPointPolicy);
		setMigStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		setRand(new Random(getSeed() * Long.MAX_VALUE));

	}

	private void connectWithLatencies() {
		for (FogDevice st : getSmartThings()) {
			FogDevice parent = getFogDeviceById(st.getParentId());
			if (parent == null) {
				continue;
			}
			double latency = st.getUplinkLatency();
			parent.getChildToLatencyMap().put(st.getId(), latency);
			parent.getChildrenIds().add(st.getId());
		}
	}

	private FogDevice getFogDeviceById(int id) {
		for (FogDevice sc : getServerCloudlets()) {
			if (id == sc.getId())
				return sc;
		}
		return null;
	}

	private void initializeCPULoads() {
		for (FogDevice sc : getServerCloudlets()) {
			this.globalCurrentCpuLoad.put(sc.getId(), 0.0);
		}
		for (MobileDevice st : getSmartThings()) {
			this.globalCurrentCpuLoad.put(st.getId(), 0.0);
		}
	}

	@Override
	public void startEntity() {
		for (String appId : applications.keySet()) {
			LogMobile.debug("MobileController.java", appId + " - "
				+ getAppLaunchDelays().get(appId));
			processAppSubmit(applications.get(appId));
		}

		schedulePeriodic(getId(), 0, 1000, MaxAndMin.MAX_SIMULATION_TIME,
			MobileEvents.NEXT_STEP);
		schedulePeriodic(getId(), 0, 1000, MaxAndMin.MAX_SIMULATION_TIME,
			MobileEvents.CHECK_NEW_STEP);

		if (isMigrationAble()) {
			for (FogDevice sc : getServerCloudlets()) {
				schedulePeriodic(sc.getId(), 0, 1000, MaxAndMin.MAX_SIMULATION_TIME,
					MobileEvents.MAKE_DECISION_MIGRATION, sc.getSmartThings());
			}
		}

		for (MobileDevice st : getSmartThings()) {
			send(getId(), st.getStartTravelTime() * 1000, MobileEvents.CREATE_NEW_SMARTTHING, st);
		}

		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);

		for (FogDevice dev : getServerCloudlets())
			sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

		send(getId(), MaxAndMin.MAX_SIMULATION_TIME, MobileEvents.STOP_SIMULATION);
	}

	private void processAppSubmit(SimEvent ev) {
		Application app = (Application) ev.getData();
		processAppSubmit(app);
	}

	private void processAppSubmit(Application application) {
		System.out.println("MobileController 213 processAppSubmit " + CloudSim.clock()
			+ " Submitted application " + application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		List<FogDevice> tempAllDevices = new ArrayList<>();
		for (FogDevice sc : getServerCloudlets()) {
			tempAllDevices.add(sc);
		}

		for (MobileDevice st : getSmartThings()) {
			tempAllDevices.add(st);
		}

		ModulePlacement modulePlacement = new ModulePlacementMapping(tempAllDevices// getServerCloudlets()
			, application, getModuleMapping(), globalCurrentCpuLoad);

		for (FogDevice fogDevice : getServerCloudlets()) {
			sendNow(fogDevice.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		}
		for (MobileDevice st : getSmartThings()) {
			sendNow(st.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		}

		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		Map<Integer, Map<String, Integer>> instanceCountMap = modulePlacement
			.getModuleInstanceCountMap();
		for (Integer deviceId : deviceToModuleMap.keySet()) {
			for (AppModule module : deviceToModuleMap.get(deviceId)) {
				System.out.println("MobileController 240 ProcessAppSubmit");
				sendNow(deviceId, FogEvents.APP_SUBMIT, application);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE_INSTANCE,
					new ModuleLaunchConfig(module, instanceCountMap.get(deviceId).get(module.getName())));
			}
		}
	}

	private void processAppSubmitMigration(SimEvent ev) {
		Application application = (Application) ev.getData();
		System.out.println(CloudSim.clock() + " Submitted application after migration "
			+ application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		FogDevice sc = (FogDevice) CloudSim.getEntity(ev.getSource());
		List<FogDevice> tempList = new ArrayList<>();
		tempList.add(sc);
		ModulePlacement modulePlacement = new ModulePlacementMapping(tempList
			, application, getModuleMapping(), globalCurrentCpuLoad, true);

		sendNow(sc.getId(), FogEvents.ACTIVE_APP_UPDATE, application);

		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		Map<Integer, Map<String, Integer>> instanceCountMap = modulePlacement
			.getModuleInstanceCountMap();
		for (AppModule module : deviceToModuleMap.get(sc.getId())) {
			System.out.println("MobileController 268 processAppSubmitMigration");
			sendNow(sc.getId(), FogEvents.APP_SUBMIT, application);
			sendNow(sc.getId(), FogEvents.LAUNCH_MODULE, module);
			sendNow(
				sc.getId(),
				FogEvents.LAUNCH_MODULE_INSTANCE,
				new ModuleLaunchConfig(module, instanceCountMap.get(sc.getId()).get(
					module.getName())));
		}
	}

	private void processTupleFinished(SimEvent ev) {}

	protected void manageResources() {
		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
	}

	@Override
	public void processEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case FogEvents.APP_SUBMIT:
			System.out.println("APP_SUBMIT");
			processAppSubmit(ev);
			break;
		case MobileEvents.APP_SUBMIT_MIGRATE:
			processAppSubmitMigration(ev);
			break;
		case FogEvents.TUPLE_FINISHED:
			System.out.println("TUPLE_FINISHED");
			processTupleFinished(ev);
			break;
		case FogEvents.CONTROLLER_RESOURCE_MANAGE:
			manageResources();
			break;
		case MobileEvents.NEXT_STEP:
			NextStep.nextStep(getServerCloudlets()
				, getApDevices()
				, getSmartThings()
				, getCoordDevices()
				, getStepPolicy()
				, getSeed());
			break;
		case MobileEvents.CREATE_NEW_SMARTTHING:
			createNewSmartThing(ev);
			break;
		case MobileEvents.CHECK_NEW_STEP:
			checkNewStep();
			if (getSmartThings().isEmpty())
				sendNow(getId(), MobileEvents.STOP_SIMULATION);
			break;
		case MobileEvents.STOP_SIMULATION:
			requestSimulationStop();
			break;

		}
	}

	private void requestSimulationStop() {
		if (shutdownRequested) {
			return;
		}
		shutdownRequested = true;
		System.out
			.println("*********************Stopping simulation********************");
		System.out.println("CloudSim.clock(): " + CloudSim.clock());
		System.out.println("Size SmartThings: " + getSmartThings().size());
		printTimeDetails();
		printPowerDetails();
		printCostDetails();
		printNetworkUsageDetails();
		printMigrationsDetalis();
		CloudSim.terminateSimulation();
	}

	private void createNewSmartThing(SimEvent ev) {
		MobileDevice st = (MobileDevice) ev.getData();
		if (st.getTravelTimeId() != -1) {
			return;
		}
		st.setTravelTimeId(0);
		activateMobileUser(st);
	}

	protected boolean activateMobileUser(MobileDevice mobileDevice) {
		if (mobileDevice.getSourceAp() == null
			&& (getApDevices() == null || getApDevices().isEmpty()
				|| !ApDevice.connectApSmartThing(getApDevices(), mobileDevice,
					getRand().nextDouble()))) {
			LogMobile.debug("MobileController.java", mobileDevice.getName()
				+ " entered the simulation outside access-point coverage");
			return false;
		}

		ApDevice sourceAp = mobileDevice.getSourceAp();
		if (sourceAp.getServerCloudlet() == null) {
			throw new IllegalStateException("Access point " + sourceAp.getName()
				+ " has no server cloudlet for entering user " + mobileDevice.getName());
		}
		if (mobileDevice.getSourceServerCloudlet() == null) {
			sourceAp.getServerCloudlet().connectServerCloudletSmartThing(mobileDevice);
		}
		LogMobile.debug("MobileController.java", mobileDevice.getName()
			+ " connected to access point " + sourceAp.getName()
			+ " and server cloudlet "
			+ mobileDevice.getSourceServerCloudlet().getName());
		if (mobileDevice.getVmMobileDevice() == null) {
			registerMobileUser(mobileDevice);
		}
		return true;
	}

	private void registerMobileUser(MobileDevice mobileDevice) {
		FogBroker broker;
		try {
			broker = new FogBroker("My_broker" + mobileDevice.getMyId());
		} catch (Exception error) {
			throw new IllegalStateException("Could not create broker for entering user "
				+ mobileDevice.getName(), error);
		}
		addBrokerFor(mobileDevice, broker);

		String appId = "MyApp_vr_game" + mobileDevice.getMyId();
		CloudletScheduler scheduler = new TupleScheduler(500, 1);
		long vmSize = 128;
		AppModule vm = new AppModule(mobileDevice.getMyId(),
			"AppModuleVm_" + mobileDevice.getName(), appId, broker.getId(), 281,
			128, 1000, vmSize, "Vm_" + mobileDevice.getName(), scheduler,
			new HashMap<Pair<String, String>, SelectivityModel>());
		mobileDevice.setVmMobileDevice(vm);

		if (!mobileDevice.getSourceServerCloudlet().getHost().vmCreate(vm)) {
			mobileDevice.setVmMobileDevice(null);
			removeBrokerFor(mobileDevice, broker);
			throw new IllegalStateException("Could not allocate VM for entering user "
				+ mobileDevice.getName() + " on "
				+ mobileDevice.getSourceServerCloudlet().getName());
		}

		mobileDevice.setVmLocalServerCloudlet(mobileDevice.getSourceServerCloudlet());
		mobileDevice.setLockedToMigration(false);
		mobileDevice.getSourceServerCloudlet().setSmartThingsWithVm(
			mobileDevice, Policies.ADD);
		MobileUserRegistration.submitVm(broker, mobileDevice);

		Application application = MobileUserApplicationFactory.create(appId,
			broker.getId(), mobileDevice.getMyId(), vm);
		MobileUserRegistration.configurePeripherals(mobileDevice, broker, appId);
		getModuleMapping().addModuleToDevice(vm.getName(),
			mobileDevice.getSourceServerCloudlet().getName(), 1);
		getModuleMapping().addModuleToDevice("client" + mobileDevice.getMyId(),
			mobileDevice.getName(), 1);
		submitApplication(application, 0);
		processAppSubmit(application);
		MobileUserRegistration.activatePeripherals(mobileDevice);
		registerActiveSensors(mobileDevice);
	}

	private double migrationTimeToLiveMigration(MobileDevice smartThing) {
		double runTime = CloudSim.clock() - smartThing.getTimeStartLiveMigration();
		if (smartThing.getMigTime() > runTime) {
			runTime = smartThing.getMigTime() - runTime;
			return runTime;
		}
		else {
			return 0;
		}

	}

	private void checkNewStep() {
		int index = 0;
		for (MobileDevice st : getSmartThings()) {
			if (st.getTravelTimeId() == -1) {
				continue;
			}
			MyStatistics.getInstance().getEnergyHistory()
				.put(st.getMyId(), st.getEnergyConsumption());
			MyStatistics.getInstance().getPowerHistory().put(st.getMyId(), st.getHost().getPower());

			if (st.getSourceAp() != null) {
				System.out.println(st.getName() + "\t" + st.getCoord().getCoordX() + "\t"
					+ st.getCoord().getCoordY());
				System.out.println(st.getSourceAp().getName() + "\t"
					+ st.getSourceAp().getCoord().getCoordX() + "\t"
					+ st.getSourceAp().getCoord().getCoordY());
				System.out.println(Distances.checkDistance(st.getCoord(), st.getSourceAp()
					.getCoord()));
				if (!st.isLockedToHandoff()) {
					double distance = Distances.checkDistance(st.getCoord(), st.getSourceAp()
						.getCoord());

					System.out.println("Distance " + distance + "Diff "
						+ (MaxAndMin.AP_COVERAGE - MaxAndMin.MAX_DISTANCE_TO_HANDOFF) + " max "
						+ MaxAndMin.AP_COVERAGE);
					if (distance >= MaxAndMin.AP_COVERAGE - MaxAndMin.MAX_DISTANCE_TO_HANDOFF
						&& distance < MaxAndMin.AP_COVERAGE) {
						index = Migration.nextAp(getApDevices(), st);
						if (index >= 0) {// index isn't negative
							st.setDestinationAp(getApDevices().get(index));
							st.setHandoffStatus(true);
							st.setLockedToHandoff(true);

							double handoffTime = MaxAndMin.MIN_HANDOFF_TIME
								+ (MaxAndMin.MAX_HANDOFF_TIME - MaxAndMin.MIN_HANDOFF_TIME)
								* getRand().nextDouble();
							float handoffLocked = (float) (handoffTime * 4);
							int delayConnection = 100; // connection between SmartT and ServerCloudlet

							if (!st.getDestinationAp().getServerCloudlet()
								.equals(st.getSourceServerCloudlet())) {

								if (isMigrationAble()) {
									LogMobile.debug("MobileController.java", st.getName()
										+ " will be desconnected from " +
										st.getSourceServerCloudlet().getName() + " by handoff");
									sendNow(st.getSourceServerCloudlet().getId(),
										MobileEvents.MAKE_DECISION_MIGRATION, st);
									sendNow(st.getSourceServerCloudlet().getId(),
										MobileEvents.DESCONNECT_ST_TO_SC, st);
									send(st.getDestinationAp().getServerCloudlet().getId(),
										handoffTime + delayConnection,
										MobileEvents.CONNECT_ST_TO_SC, st);
								}
								if (st.isPostCopyStatus() && !st.isMigStatus()) {
									if (!st.isMigStatusLive()) {
										st.setMigStatusLive(true);
										double newMigTime = migrationTimeToLiveMigration(st);
										double baselineBandwidth = NetworkSlicing.getSliceBandwidth(
											st.getVmLocalServerCloudlet(),
											st.getDestinationServerCloudlet(), st.getNetworkSliceId());
										if (newMigTime == 0) {
											newMigTime = ((st.getVmMobileDevice().getHost()
												.getRamProvisioner().getUsedRam() * 8 * 1024 * 1024)
												/ baselineBandwidth) * 1000.0;
										}
										double delayProcess = st.getVmLocalServerCloudlet()
											.getCharacteristics().getCpuTime((st.getVmMobileDevice()
												.getSize() * 1024 * 1024 * 8) * 0.7, 0.0);// the connection already opened
										st.setTimeFinishDeliveryVm(-1.0);
										MyStatistics.getInstance().startWithoutVmTime(
											st.getMyId(),CloudSim.clock());
										NetworkSlicing.MigrationTransferRequest transferRequest =
											new NetworkSlicing.MigrationTransferRequest(
												st.getVmLocalServerCloudlet(),
												st.getDestinationServerCloudlet(), st, newMigTime,
												st.getVmLocalServerCloudlet().getId(),
												MobileEvents.SET_MIG_STATUS_TRUE);
										send(st.getVmLocalServerCloudlet().getId(), delayProcess,
											MobileEvents.START_MIGRATION_TRANSFER, transferRequest);
									}
								}
							}

							send(st.getSourceAp().getId(), handoffTime, MobileEvents.START_HANDOFF,st);
							send(st.getDestinationAp().getId(), handoffLocked,
								MobileEvents.UNLOCKED_HANDOFF, st);
							MyStatistics.getInstance().setTotalHandoff(1);

							saveHandOff(st);

							LogMobile.debug("MobileController.java", st.getName()
								+ " handoff was scheduled! " + "SourceAp: "
								+ st.getSourceAp().getName() + " NextAp: "
								+ st.getDestinationAp().getName() + "\n");
							LogMobile.debug("MobileController.java", "Distance between "
									+ st.getName() + " and " + st.getSourceAp().getName()
									+ ": " + Distances.checkDistance(st.getCoord(),
										st.getSourceAp().getCoord()));
						}
						else {
							LogMobile.debug("MobileController.java", st.getName()
								+ " can't make handoff because don't exist closest nextAp");
						}
					}
					else if (distance >= MaxAndMin.AP_COVERAGE) {
						st.getSourceAp().desconnectApSmartThing(st);
						st.getSourceServerCloudlet().desconnectServerCloudletSmartThing(st);
						if (st.isLockedToMigration() || st.isMigStatus()) {
							sendNow(st.getVmLocalServerCloudlet().getId(),
								MobileEvents.ABORT_MIGRATION, st);
						}
						LogMobile.debug("MobileController.java", st.getName()
							+ " desconnected by AP_COVERAGE - Distance: " + distance);
						LogMobile.debug("MobileController.java", st.getName() + " X: "
							+ st.getCoord().getCoordX() + " Y: " + st.getCoord().getCoordY());
					}
				}
			}
			else {
				activateMobileUser(st);
			}
		}
	}

	private static void saveHandOff(MobileDevice st) {
		System.out.println("HANDOFF " + st.getMyId() + " Position: " + st.getCoord().getCoordX()
			+ ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: "
			+ st.getSpeed());
		try (FileWriter fw = new FileWriter(st.getMyId() + "handoff.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + CloudSim.clock() + "\t" + st.getCoord().getCoordX()
				+ "\t" + st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t"
				+ st.getSpeed() + "\t" + st.getSourceAp() + "\t" + st.getDestinationAp());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void shutdownEntity() {
	}

	private void printCostDetails() {
	}

	private FogDevice getCloud() {
		for (FogDevice dev : getServerCloudlets())
			if (dev.getName().equals("cloud"))
				return dev;
		return null;
	}

	public void printResults(String a, String filename) {
		try (FileWriter fw1 = new FileWriter(filename, true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(a);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void printPowerDetails() {
		double energyConsumedMean = 0.0;
		int j = 0;
		System.out.println("=========================================");
		System.out.println("CLOUDLETS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (FogDevice fogDevice : getServerCloudlets()) {
			if (fogDevice.getEnergyConsumption() != 5.8736831999993116E7) {
				System.out.println(fogDevice.getName() + ": Power = "
					+ fogDevice.getHost().getPower());
				System.out.println(fogDevice.getName() + ": Energy Consumed = "
					+ fogDevice.getEnergyConsumption());
				energyConsumedMean += fogDevice.getEnergyConsumption();
				j++;
			}
		}
		System.out.println("Total consumido Coudlets: " + energyConsumedMean + " Media: "
			+ energyConsumedMean / j);
		printResults(String.valueOf(energyConsumedMean / j), "averageEnergyHistoryDevice.txt");
		printResults(
			String.valueOf(energyConsumedMean) + "\t" + String.valueOf(energyConsumedMean / j),
			"results.txt");
		energyConsumedMean = 0.0;
		System.out.println("=========================================");
		System.out.println("AP DEVICES ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (FogDevice apDevice : getApDevices()) {
			System.out.println(apDevice.getName() + ": Energy Consumed = "
				+ apDevice.getEnergyConsumption());
			energyConsumedMean += apDevice.getEnergyConsumption();
			j++;
		}
		System.out.println("Total consumido AP: " + energyConsumedMean + " Media: "
			+ energyConsumedMean / j);
		energyConsumedMean = 0.0;
		System.out.println("=========================================");
		System.out.println("SMARTTHINGS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (FogDevice mobileDevice : getSmartThings()) {
			System.out.println(mobileDevice.getName() + ": Power = "
				+ mobileDevice.getHost().getPower());
			System.out.println(mobileDevice.getName() + ": Energy Consumed = "
				+ mobileDevice.getEnergyConsumption());
		}
		for (int i = 0; i < MyStatistics.getInstance().getPowerHistory().size(); i++) {
			System.out.println("SmartThing" + i + ": Power = "
				+ MyStatistics.getInstance().getPowerHistory().get(i));
		}
		for (int i = 0; i < MyStatistics.getInstance().getEnergyHistory().size(); i++) {
			System.out.println("SmartThing" + i + ": Energy Consumed = "
				+ MyStatistics.getInstance().getEnergyHistory().get(i));
			printResults(String.valueOf(MyStatistics.getInstance().getEnergyHistory().get(i)),
				"results.txt");
			energyConsumedMean += MyStatistics.getInstance().getEnergyHistory().get(i);
		}
	}

	private String getStringForLoopId(int loopId) {
		for (String appId : getApplications().keySet()) {
			Application app = getApplications().get(appId);
			for (AppLoop loop : app.getLoops()) {
				if (loop.getLoopId() == loopId)
					return loop.getModules().toString();
			}
		}
		return null;
	}

	private void printTimeDetails() {

		System.out.println("=========================================");
		System.out.println("============== RESULTS ==================");
		System.out.println("=========================================");
		System.out.println("EXECUTION TIME : "
			+ (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance()
				.getSimulationStartTime()));
		System.out.println("=========================================");
		System.out.println("APPLICATION LOOP DELAYS");
		System.out.println("=========================================");
		double mediaLatencia = 0.0;
		double mediaLatenciaMax = 0.0;
		for (Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()) {
			System.out.println(getStringForLoopId(loopId) + " ---> "
				+ TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId)
				+ " MaxExecutionTime: "
				+ TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId));
			printResults(
				String.valueOf(TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId)),
				"results.txt");
			printResults(
				String.valueOf(TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId)),
				"results.txt");
			mediaLatencia += TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId);
			mediaLatenciaMax += TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId);
		}
		printResults(
			String.valueOf(mediaLatencia
				/ TimeKeeper.getInstance().getLoopIdToCurrentAverage().keySet().size()),
			"averageLoopIdToCurrentAverage.txt");
		printResults(
			String.valueOf(mediaLatenciaMax
				/ TimeKeeper.getInstance().getMaxLoopExecutionTime().keySet().size()),
			"averageMaxLoopExecutionTime.txt");
		System.out.println("=========================================");
		System.out.println("TUPLE CPU EXECUTION DELAY");
		System.out.println("=========================================");

		for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
			System.out.println(tupleType + " ---> "
				+ TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
		}

		System.out.println("=========================================");
	}

	private void printNetworkUsageDetails() {
		System.out.println("=========================================");
		System.out.println("=============NETWORK USAGE===============");
		System.out.println("=========================================");
		double deviceNetworkUsage = NetworkUsageMonitor.getNetworkUsage()
			- NetworkUsageMonitor.getNetWorkUsageInMigration();
		System.out.println("VM data transferred in migration = "
			+ NetworkUsageMonitor.getVMTransferredData());
		printResults(
			String.valueOf(NetworkUsageMonitor.getVMTransferredData() / CloudSim.clock()) + '\t'
				+ String.valueOf(NetworkUsageMonitor.getVMTransferredData()) + '\t'
				+ CloudSim.clock(), "results.txt");
		printResults(
			String.valueOf(NetworkUsageMonitor.getVMTransferredData() / CloudSim.clock()) + '\t'
				+ String.valueOf(NetworkUsageMonitor.getVMTransferredData()) + '\t'
				+ CloudSim.clock(), "vmsizesended.txt");
		System.out.println("Device's network usage = " + deviceNetworkUsage);
		printResults(
			String.valueOf(deviceNetworkUsage / CloudSim.clock()) + '\t'
				+ String.valueOf(deviceNetworkUsage) + '\t' + CloudSim.clock(), "results.txt");
		printResults(
			String.valueOf(deviceNetworkUsage / CloudSim.clock()) + '\t'
				+ String.valueOf(deviceNetworkUsage) + '\t' + CloudSim.clock(),
			"deviceNetworkUsage.txt");
		System.out.println("Migration' network usage (total)= "
			+ NetworkUsageMonitor.getNetWorkUsageInMigration());
		System.out.println("Migration' network usage (mean)= "
			+ NetworkUsageMonitor.getNetWorkUsageInMigration()
			/ MyStatistics.getInstance().getTotalMigrations());
		printResults(
			String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration() / CloudSim.clock())
				+ '\t' + String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()) + '\t'
				+ CloudSim.clock(), "results.txt");
		printResults(
			String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration() / CloudSim.clock())
				+ '\t' + String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()) + '\t'
				+ CloudSim.clock(), "cloudletNetworkUsage.txt");
		System.out.println("Total network usage = " + NetworkUsageMonitor.getNetworkUsage());
		printResults(
			String.valueOf(NetworkUsageMonitor.getNetworkUsage() / CloudSim.clock()) + '\t'
				+ String.valueOf(NetworkUsageMonitor.getNetworkUsage()) + '\t' + CloudSim.clock(),
			"results.txt");
		printResults(
			String.valueOf(NetworkUsageMonitor.getNetworkUsage() / CloudSim.clock()) + '\t'
				+ String.valueOf(NetworkUsageMonitor.getNetworkUsage()) + '\t' + CloudSim.clock(),
			"totalNetworkUsage.txt");
	}

	private void printMigrationsDetalis() {
		System.out.println("=========================================");
		System.out.println("==============MIGRATIONS=================");
		System.out.println("=========================================");
		System.out.println("Total of migrations: "
			+ MyStatistics.getInstance().getTotalMigrations());
		System.out.println("Total of handoff: " + MyStatistics.getInstance().getTotalHandoff());
		System.out.println("Different Cloudlets reached along the user's path: "
			+ MyStatistics.getInstance().getMyCountLowestLatency());

		printResults(String.valueOf(MyStatistics.getInstance().getTotalMigrations()),
			"results.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getTotalHandoff()), "results.txt");

		printResults(String.valueOf(MyStatistics.getInstance().getTotalMigrations()),
			"totalMigrations.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getMyCountLowestLatency()),
			"totalMyCountLowestLatency.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getTotalHandoff()),
			"totalHandoff.txt");

		MyStatistics.getInstance().printResults();
		System.out.println("***Last time without connection***");

		for (Entry<Integer, Double> test : MyStatistics.getInstance().getWithoutConnectionTime()
			.entrySet()) {
			System.out.println("SmartThing" + test.getKey() + ": "
				+ MyStatistics.getInstance().getWithoutConnectionTime().get(test.getKey())
				+ " - Max: "
				+ MyStatistics.getInstance().getMaxWithoutConnectionTime().get(test.getKey()));
		}

		System.out.println("Average of without connection: "
			+ MyStatistics.getInstance().getAverageWithoutConnection());

		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutConnection()),
			"results.txt");

		System.out.println("***Last time without Vm***");

		for (Entry<Integer, Double> test : MyStatistics.getInstance().getWithoutVmTime().entrySet()) {
			System.out.println("SmartThing" + test.getKey() + ": "
				+ MyStatistics.getInstance().getWithoutVmTime().get(test.getKey()) + " - Max: "
				+ MyStatistics.getInstance().getMaxWithoutVmTime().get(test.getKey()));
		}

		System.out.println("Average of without Vm: "
			+ MyStatistics.getInstance().getAverageWithoutVmTime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutVmTime()),
			"results.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutVmTime()),
			"averageWithoutVmTime.txt");

		System.out.println("===Last delay after connection===");
		for (Entry<Integer, Double> test : MyStatistics.getInstance().getDelayAfterNewConnection()
			.entrySet()) {
			System.out.println("SmartThing" + test.getKey() + ": "
				+ MyStatistics.getInstance().getDelayAfterNewConnection().get(test.getKey())
				+ " - Max: "
				+ MyStatistics.getInstance().getMaxDelayAfterNewConnection().get(test.getKey()));
		}
		System.out.println("Average of delay after new Connection: "
			+ MyStatistics.getInstance().getAverageDelayAfterNewConnection());
		printResults(
			String.valueOf(MyStatistics.getInstance().getAverageDelayAfterNewConnection()),
			"results.txt");
		printResults(
			String.valueOf(MyStatistics.getInstance().getAverageDelayAfterNewConnection()),
			"averageDelayAfterNewConnection.txt");

		System.out.println("---Average of Time of Migrations---");
		double tempoMigracaoMax = 0.0;
		for (Entry<Integer, Double> test : MyStatistics.getInstance().getMigrationTime().entrySet()) {
			System.out.println("SmartThing" + test.getKey() + ": "
				+ MyStatistics.getInstance().getMigrationTime().get(test.getKey()) + " - Max: "
				+ MyStatistics.getInstance().getMaxMigrationTime().get(test.getKey()));
			tempoMigracaoMax = Math.max(tempoMigracaoMax, MyStatistics.getInstance()
				.getMaxMigrationTime().get(test.getKey()));
		}
		System.out.println("Average of Time of Migrations: "
			+ MyStatistics.getInstance().getAverageMigrationTime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageMigrationTime()),
			"results.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageMigrationTime()),
			"averageMigrationTime.txt");
		System.out.println("Hightest Time of Migrations: " + tempoMigracaoMax);
		printResults(String.valueOf(tempoMigracaoMax), "averageMigrationMaxTime.txt");
		System.out.println("---Average of Downtime---");
		double tempoDowntimeMax = 0.0;
		for (Entry<Integer, Double> test : MyStatistics.getInstance().getDowntime().entrySet()) {
			System.out.println("SmartThing" + test.getKey() + ": "
				+ MyStatistics.getInstance().getDowntime().get(test.getKey()) + " - Max: "
				+ MyStatistics.getInstance().getMaxDowntime().get(test.getKey()));
			tempoDowntimeMax += MyStatistics.getInstance().getMaxDowntime().get(test.getKey());
		}
		System.out.println("Average of Downtime: "
			+ MyStatistics.getInstance().getAverageDowntime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDowntime()),
			"results.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDowntime()),
			"averageDowntime.txt");
		System.out.println("Max Downtime: " + tempoDowntimeMax);
		printResults(String.valueOf(tempoDowntimeMax), "averageDowntimeMax.txt");
		System.out.println("Tuple lost: "
			+ (((double) MyStatistics.getInstance().getMyCountLostTuple() / MyStatistics
				.getInstance().getMyCountTotalTuple())) * 100 + "%");
		System.out.println("Tuple lost: " + MyStatistics.getInstance().getMyCountLostTuple());
		System.out.println("Total tuple: " + MyStatistics.getInstance().getMyCountTotalTuple());

	}

	public void submitApplication(Application application, int delay) {
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		getAppLaunchDelays().put(application.getAppId(), delay);
		for (MobileDevice st : getSmartThings()) {
			for (Sensor s : st.getSensors()) {
				if (s.getAppId().equals(application.getAppId()))
					s.setApp(application);
			}
			for (Actuator a : st.getActuators()) {
				if (a.getAppId().equals(application.getAppId()))
					a.setApp(application);
			}
		}
		for (AppEdge edge : application.getEdges()) {
			if (edge.getEdgeType() == AppEdge.ACTUATOR) {
				String moduleName = edge.getSource();
				for (MobileDevice st : getSmartThings()) {
					for (Actuator actuator : st.getActuators()) {
						if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
							application.getModuleByName(moduleName).subscribeActuator(
								actuator.getId(), edge.getTupleType());
					}
				}
			}
		}

	}

	public void submitApplicationMigration(MobileDevice smartThing, Application application,
		int delay) {
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		getAppLaunchDelays().put(application.getAppId(), delay);

		for (AppEdge edge : application.getEdges()) {
			if (edge.getEdgeType() == AppEdge.ACTUATOR) {
				String moduleName = edge.getSource();
				for (MobileDevice st : getSmartThings()) {
					for (Actuator actuator : st.getActuators()) {
						if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
							application.getModuleByName(moduleName).subscribeActuator(
								actuator.getId(), edge.getTupleType());
					}
				}
			}
		}
	}

	public Map<String, Application> getApplications() {
		return applications;
	}

	public void setApplications(Map<String, Application> applications) {
		this.applications = applications;
	}

	public Map<String, Integer> getAppLaunchDelays() {
		return appLaunchDelays;
	}

	public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
		this.appLaunchDelays = appLaunchDelays;
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, Double> getGlobalCurrentCpuLoad() {
		return globalCurrentCpuLoad;
	}

	public void setGlobalCurrentCpuLoad(Map<Integer, Double> globalCurrentCpuLoad) {
		this.globalCurrentCpuLoad = globalCurrentCpuLoad;
	}

	public void setGlobalCPULoad(Map<Integer, Double> currentCpuLoad) {
		for (FogDevice device : getServerCloudlets()) {
			this.globalCurrentCpuLoad.put(device.getId(), currentCpuLoad.get(device.getId()));
		}
	}

	public static int getMigPointPolicy() {
		return migPointPolicy;
	}

	public static void setMigPointPolicy(int migPointPolicy) {
		MobileController.migPointPolicy = migPointPolicy;
	}

	public static int getMigStrategyPolicy() {
		return migStrategyPolicy;
	}

	public static void setMigStrategyPolicy(int migStrategyPolicy) {
		MobileController.migStrategyPolicy = migStrategyPolicy;
	}

	public static int getStepPolicy() {
		return stepPolicy;
	}

	public static void setStepPolicy(int stepPolicy) {
		MobileController.stepPolicy = stepPolicy;
	}

	public static Coordinate getCoordDevices() {
		return coordDevices;
	}

	public static void setCoordDevices(Coordinate coordDevices) {
		MobileController.coordDevices = coordDevices;
	}

	public List<FogBroker> getBrokerList() {
		return brokerList;
	}

	public void setBrokerList(List<FogBroker> brokerList) {
		this.brokerList = brokerList;
	}

	private static Map<Integer, FogBroker> indexBrokersByMobileId(
		List<MobileDevice> mobileDevices, List<FogBroker> brokers) {
		if (mobileDevices == null || brokers == null) {
			throw new IllegalArgumentException(
				"Mobile devices and brokers cannot be null");
		}
		if (!brokers.isEmpty() && mobileDevices.size() != brokers.size()) {
			throw new IllegalArgumentException("Expected one broker per registered mobile user");
		}
		Map<Integer, FogBroker> indexedBrokers = new HashMap<Integer, FogBroker>();
		if (brokers.isEmpty()) {
			return indexedBrokers;
		}
		for (int i = 0; i < mobileDevices.size(); i++) {
			MobileDevice mobileDevice = mobileDevices.get(i);
			FogBroker broker = brokers.get(i);
			if (mobileDevice == null || broker == null) {
				throw new IllegalArgumentException(
					"Registered mobile users and brokers cannot contain null entries");
			}
			if (indexedBrokers.put(mobileDevice.getMyId(), broker) != null) {
				throw new IllegalArgumentException("Duplicate mobile user ID: "
					+ mobileDevice.getMyId());
			}
		}
		return indexedBrokers;
	}

	private static void setBrokersByMobileId(Map<Integer, FogBroker> brokers) {
		brokersByMobileId = new HashMap<Integer, FogBroker>(brokers);
	}

	private void addBrokerFor(MobileDevice mobileDevice, FogBroker broker) {
		if (brokersByMobileId.containsKey(mobileDevice.getMyId())) {
			throw new IllegalStateException("A broker is already registered for mobile user "
				+ mobileDevice.getName());
		}
		brokersByMobileId.put(mobileDevice.getMyId(), broker);
		getBrokerList().add(broker);
	}

	private void removeBrokerFor(MobileDevice mobileDevice, FogBroker broker) {
		brokersByMobileId.remove(mobileDevice.getMyId());
		getBrokerList().remove(broker);
	}

	public static int getSeed() {
		return seed;
	}

	public static void setSeed(int seed) {
		MobileController.seed = seed;
	}

	public static List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}

	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		MobileController.serverCloudlets = serverCloudlets;
	}

	public static List<MobileDevice> getSmartThings() {
		return smartThings;
	}

	public static void setSmartThings(List<MobileDevice> smartThings) {
		MobileController.smartThings = smartThings;
		rebuildActiveSensorApplications();
	}

	private static void rebuildActiveSensorApplications() {
		activeSensorApplications.clear();
		if (smartThings == null) {
			return;
		}
		for (MobileDevice smartThing : smartThings) {
			for (Sensor sensor : smartThing.getSensors()) {
				if (!sensor.isEnabled()) {
					continue;
				}
				String appId = sensor.getAppId();
				Integer count = activeSensorApplications.get(appId);
				activeSensorApplications.put(appId, count == null ? 1 : count + 1);
			}
		}
	}

	public static boolean isApplicationActive(String appId) {
		return activeSensorApplications.containsKey(appId);
	}

	private static void registerActiveSensors(MobileDevice smartThing) {
		for (Sensor sensor : smartThing.getSensors()) {
			if (!sensor.isEnabled()) {
				continue;
			}
			String appId = sensor.getAppId();
			Integer count = activeSensorApplications.get(appId);
			activeSensorApplications.put(appId, count == null ? 1 : count + 1);
		}
	}

	public static boolean removeSmartThing(MobileDevice smartThing) {
		if (!smartThings.remove(smartThing)) {
			return false;
		}
		for (Sensor sensor : smartThing.getSensors()) {
			if (!sensor.isEnabled()) {
				continue;
			}
			String appId = sensor.getAppId();
			Integer count = activeSensorApplications.get(appId);
			if (count == null || count <= 1) {
				activeSensorApplications.remove(appId);
			} else {
				activeSensorApplications.put(appId, count - 1);
			}
		}
		return true;
	}

	public static List<ApDevice> getApDevices() {
		return apDevices;
	}

	public static void setApDevices(List<ApDevice> apDevices) {
		MobileController.apDevices = apDevices;
	}

	public static Random getRand() {
		return rand;
	}

	public static void setRand(Random rand) {
		MobileController.rand = rand;
	}

	public static boolean isMigrationAble() {
		return migrationAble;
	}

	public static void setMigrationAble(boolean migrationAble) {
		MobileController.migrationAble = migrationAble;
	}

}
