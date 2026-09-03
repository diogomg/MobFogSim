package org.fog.placement;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.Sensor;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilityTimeline;
import org.fog.localization.Distances;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.MigrationCoordinator;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileUserApplicationFactory;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.SimulationContext;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MembershipAction;
import org.fog.scheduler.TupleScheduler;

public class MobileController extends SimEntity {
	private static boolean migrationAble;
	private static MigrationPointPolicy migPointPolicy = MigrationPointPolicy.FIXED;

	private static int stepPolicy; // Quantity of steps in the nextStep Function
	private static Coordinate coordDevices;

	private static MigrationStrategyPolicy migStrategyPolicy =
		MigrationStrategyPolicy.LOWEST_LATENCY;
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
	private final SimulationResultsService resultsService;
	private final MobilityService mobilityService;
	private final MobileAssociationService associationService;
	private final MigrationCoordinator migrationCoordinator;

	static final int numOfDepts = 1;
	static final int numOfMobilesPerDept = 4;
	private static Random rand;

	/** Clears compatibility state before and after an embedded simulation run. */
	public static void resetRunState() {
		migrationAble = false;
		migPointPolicy = MigrationPointPolicy.FIXED;
		stepPolicy = 0;
		coordDevices = null;
		migStrategyPolicy = MigrationStrategyPolicy.LOWEST_LATENCY;
		seed = 0;
		serverCloudlets = new ArrayList<FogDevice>();
		smartThings = new ArrayList<MobileDevice>();
		activeSensorApplications.clear();
		apDevices = new ArrayList<ApDevice>();
		brokerList = new ArrayList<FogBroker>();
		brokersByMobileId = new HashMap<Integer, FogBroker>();
		rand = null;
	}

	public MobileController() {
		this.resultsService = new SimulationResultsService(
			RunOutputManager.getInstance());
		this.mobilityService = new MobilityService();
		this.associationService = new MobileAssociationService();
		this.migrationCoordinator = new MigrationCoordinator();
	}

	public MobileController(String name, List<FogDevice> serverCloudlets, List<ApDevice> apDevices,
		List<MobileDevice> smartThings, List<FogBroker> brokers, ModuleMapping moduleMapping
		, int migPointPolicy, int migStrategyPolicy, int stepPolicy, Coordinate coordDevices,
		int seed, boolean migrationAble) {
		this(name, serverCloudlets, apDevices, smartThings, brokers, moduleMapping,
			MigrationPointPolicy.fromLegacy(migPointPolicy),
			MigrationStrategyPolicy.fromLegacy(migStrategyPolicy), stepPolicy,
			coordDevices, seed, migrationAble);
	}

	public MobileController(String name, List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, List<MobileDevice> smartThings,
		List<FogBroker> brokers, ModuleMapping moduleMapping,
		MigrationPointPolicy migPointPolicy,
		MigrationStrategyPolicy migStrategyPolicy, int stepPolicy,
		Coordinate coordDevices, int seed, boolean migrationAble) {
		super(name);
		this.resultsService = new SimulationResultsService(
			RunOutputManager.getInstance());
		this.mobilityService = new MobilityService();
		this.associationService = new MobileAssociationService();
		this.migrationCoordinator = new MigrationCoordinator();
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
		setMigrationPointPolicy(migPointPolicy);
		setMigrationStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices == null ? new Coordinate() : coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		SimulationContext context = SimulationContext.currentOrNull();
		setRand(context == null ? new Random(getSeed() * Long.MAX_VALUE)
			: context.random("mobile-controller"));
		setMigrationAble(migrationAble);
	}

	public MobileController(String name, List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, List<MobileDevice> smartThings,
		int migPointPolicy, int migStrategyPolicy, int stepPolicy,
		Coordinate coordDevices, int seed) {
		this(name, serverCloudlets, apDevices, smartThings,
			MigrationPointPolicy.fromLegacy(migPointPolicy),
			MigrationStrategyPolicy.fromLegacy(migStrategyPolicy), stepPolicy,
			coordDevices, seed);
	}

	public MobileController(String name, List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, List<MobileDevice> smartThings,
		MigrationPointPolicy migPointPolicy,
		MigrationStrategyPolicy migStrategyPolicy, int stepPolicy,
		Coordinate coordDevices, int seed) {
		super(name);
		this.resultsService = new SimulationResultsService(
			RunOutputManager.getInstance());
		this.mobilityService = new MobilityService();
		this.associationService = new MobileAssociationService();
		this.migrationCoordinator = new MigrationCoordinator();
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
		setMigrationPointPolicy(migPointPolicy);
		setMigrationStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices == null ? new Coordinate() : coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		SimulationContext context = SimulationContext.currentOrNull();
		setRand(context == null ? new Random(getSeed() * Long.MAX_VALUE)
			: context.random("mobile-controller"));

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

		if (isMigrationAble()) {
			for (FogDevice sc : getServerCloudlets()) {
				schedulePeriodic(sc.getId(), 0, 1000, MaxAndMin.MAX_SIMULATION_TIME,
					MobileEvents.MAKE_DECISION_MIGRATION, sc.getSmartThings());
			}
		}

		for (MobileDevice st : getSmartThings()) {
			if (st.getLifecycleState() == MobileDeviceLifecycle.SCHEDULED) {
				send(getId(), MobilityTimeline.toSimulationTime(st.getStartTravelTime()),
					MobileEvents.CREATE_NEW_SMARTTHING, st);
			}
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
		FogUtils.getApplicationCoverage().put(application.getAppId(), application.getGeoCoverage());
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
		FogUtils.getApplicationCoverage().put(application.getAppId(), application.getGeoCoverage());
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
		case MobileEvents.CREATE_NEW_SMARTTHING:
			createNewSmartThing(ev);
			break;
		case MobileEvents.MOBILITY_UPDATE:
			processMobilityUpdate(ev);
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
		SimulationContext context = SimulationContext.currentOrNull();
		long wallTime = context == null
			? Calendar.getInstance().getTimeInMillis()
			: context.getClock().wallTimeMillis();
		SimulationMetricsSnapshot metrics = SimulationMetricsSnapshot.capture(
			getServerCloudlets(), getApDevices(), getSmartThings(),
			MyStatistics.getInstance(), TimeKeeper.getInstance(), CloudSim.clock(),
			wallTime
				- TimeKeeper.getInstance().getSimulationStartTime());
		if (context != null) {
			context.recordMetrics(metrics);
		}
		resultsService.write(metrics, getApplications());
		CloudSim.terminateSimulation();
	}

	private void createNewSmartThing(SimEvent ev) {
		MobileDevice st = (MobileDevice) ev.getData();
		if (st == null
			|| st.getLifecycleState() != MobileDeviceLifecycle.SCHEDULED
			|| !getSmartThings().contains(st)) {
			return;
		}
		MobilityService.EntryOutcome entry = mobilityService.enter(st,
			getCoordDevices());
		if (entry == MobilityService.EntryOutcome.NO_TRACE) {
			// Compatibility for programmatically-created users without trace data.
			if (!activateMobileUser(st)) {
				finishMobileUser(st);
				requestStopIfNoMobiles();
			}
			return;
		}
		if (entry == MobilityService.EntryOutcome.FINISHED) {
			finishMobileUser(st);
			requestStopIfNoMobiles();
			return;
		}
		activateMobileUser(st);
		scheduleNextMobilityUpdate(st);
	}

	private void processMobilityUpdate(SimEvent event) {
		MobileDevice smartThing = (MobileDevice) event.getData();
		if (smartThing == null || !getSmartThings().contains(smartThing)
			|| !smartThing.getLifecycleState().acceptsMobilityUpdates()) {
			return;
		}

		mobilityService.advance(smartThing, getCoordDevices(), CloudSim.clock());
		if (!processCurrentMobilityPosition(smartThing)) {
			finishMobileUser(smartThing);
			requestStopIfNoMobiles();
			return;
		}
		onMobilityPositionUpdated(smartThing);
		if (mobilityService.isFinished(smartThing)) {
			finishMobileUser(smartThing);
			requestStopIfNoMobiles();
			return;
		}
		if (smartThing.getLifecycleState() == MobileDeviceLifecycle.ACTIVE) {
			checkNewStep(smartThing);
		}
		else {
			activateMobileUser(smartThing);
		}

		if (!getSmartThings().contains(smartThing)
			|| smartThing.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			requestStopIfNoMobiles();
			return;
		}
		scheduleNextMobilityUpdate(smartThing);
	}

	private void scheduleNextMobilityUpdate(MobileDevice smartThing) {
		if (!smartThing.getLifecycleState().acceptsMobilityUpdates()) {
			return;
		}
		double delay = mobilityService.nextUpdateDelay(smartThing, CloudSim.clock());
		send(getId(), delay, MobileEvents.MOBILITY_UPDATE, smartThing);
	}

	/** Hook used by observers after all rows at one timestamp have been applied. */
	protected void onMobilityPositionUpdated(MobileDevice smartThing) {
	}

	/** Hook for recording or rejecting the position applied by a mobility event. */
	protected boolean processCurrentMobilityPosition(MobileDevice smartThing) {
		return NextStep.processCurrentPosition(smartThing);
	}

	private void requestStopIfNoMobiles() {
		if (getSmartThings().isEmpty()) {
			sendNow(getId(), MobileEvents.STOP_SIMULATION);
		}
	}

	protected boolean activateMobileUser(MobileDevice mobileDevice) {
		if (mobileDevice == null
			|| mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return false;
		}
		MobileDeviceLifecycle previousState = associationService.associate(
			mobileDevice, getApDevices(), getRand());
		if (mobileDevice.getSourceAp() == null) {
			LogMobile.debug("MobileController.java", mobileDevice.getName()
				+ " entered the simulation outside access-point coverage");
			return false;
		}
		ApDevice sourceAp = mobileDevice.getSourceAp();
		LogMobile.debug("MobileController.java", mobileDevice.getName()
			+ " connected to access point " + sourceAp.getName()
			+ " and server cloudlet "
			+ mobileDevice.getSourceServerCloudlet().getName());
		if (mobileDevice.getVmMobileDevice() == null) {
			registerMobileUser(mobileDevice);
		}
		associationService.activate(mobileDevice, previousState);
		return true;
	}

	private void disconnectMobileUser(MobileDevice mobileDevice) {
		associationService.disconnect(mobileDevice);
	}

	private void finishMobileUser(MobileDevice mobileDevice) {
		associationService.finish(mobileDevice);
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
			mobileDevice, MembershipAction.ADD);
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

	protected void checkNewStep(MobileDevice st) {
			if (st.getLifecycleState() != MobileDeviceLifecycle.ACTIVE
				|| st.getSourceAp() == null
				|| st.getSourceServerCloudlet() == null) {
				disconnectMobileUser(st);
				return;
			}
			if (st.getCharacteristics() != null && !st.getHostList().isEmpty()) {
				MyStatistics.getInstance().recordPowerAndEnergy(st.getMyId(),
					st.getHost().getPower(), st.getEnergyConsumption());
			}

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
						Optional<ApDevice> nextAp = Migration.nextAp(getApDevices(), st);
						if (nextAp.isPresent()) {
							st.setDestinationAp(nextAp.get());
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
										double baselineBandwidth = NetworkSlicing.getSliceBandwidth(
											st.getVmLocalServerCloudlet(),
											st.getDestinationServerCloudlet(), st.getNetworkSliceId());
										double remainingTransferBytes =
										migrationCoordinator.remainingLiveMigrationBytes(
											st, baselineBandwidth, CloudSim.clock());
										if (remainingTransferBytes == 0.0) {
											remainingTransferBytes = MigrationTransferSpec
												.mebibytesToBytes(st.getVmMobileDevice().getHost()
													.getRamProvisioner().getUsedRam());
										}
										double delayProcess = st.getVmLocalServerCloudlet()
											.getCharacteristics().getCpuTime((st.getVmMobileDevice()
												.getSize() * 1024 * 1024 * 8) * 0.7, 0.0);// the connection already opened
										st.setTimeFinishDeliveryVm(-1.0);
										MyStatistics.getInstance().startWithoutVmTime(
											st.getMyId(),CloudSim.clock());
										MigrationTransferSpec transferSpec =
											new MigrationTransferSpec(
												st.getVmLocalServerCloudlet(),
												st.getDestinationServerCloudlet(), st,
												remainingTransferBytes,
												st.getMigrationTechnique().getFixedDelayMillis(st),
												delayProcess,
												st.getVmLocalServerCloudlet().getId(),
												MobileEvents.SET_MIG_STATUS_TRUE);
										send(st.getVmLocalServerCloudlet().getId(),
											transferSpec.getPreparationDelayMillis(),
											MobileEvents.START_MIGRATION_TRANSFER, transferSpec);
									}
								}
							}

							send(st.getSourceAp().getId(), handoffTime, MobileEvents.START_HANDOFF,st);
							send(st.getDestinationAp().getId(), handoffLocked,
								MobileEvents.UNLOCKED_HANDOFF, st);
							MyStatistics.getInstance().incrementHandoffCount();

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
						disconnectMobileUser(st);
						if (st.isLockedToMigration() || st.isMigStatus()) {
							if (st.getVmLocalServerCloudlet() != null) {
								sendNow(st.getVmLocalServerCloudlet().getId(),
									MobileEvents.ABORT_MIGRATION, st);
							}
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

	private static void saveHandOff(MobileDevice st) {
		System.out.println("HANDOFF " + st.getMyId() + " Position: " + st.getCoord().getCoordX()
			+ ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: "
			+ st.getSpeed());
		try (PrintWriter out = RunOutputManager.getInstance()
			.newDetailedPrintWriter(st.getMyId() + "handoff.txt", true))
		{
			out.println(st.getMyId() + "\t" + CloudSim.clock() + "\t" + st.getCoord().getCoordX()
				+ "\t" + st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t"
				+ st.getSpeed() + "\t" + st.getSourceAp() + "\t" + st.getDestinationAp());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void shutdownEntity() {
	}

	public void printResults(String a, String filename) {
		resultsService.appendSummary(a, filename);
	}

	public void submitApplication(Application application, int delay) {
		FogUtils.getApplicationCoverage().put(application.getAppId(), application.getGeoCoverage());
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
		FogUtils.getApplicationCoverage().put(application.getAppId(), application.getGeoCoverage());
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
		MobileController.migPointPolicy = migPointPolicy;
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
