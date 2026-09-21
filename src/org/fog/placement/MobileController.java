package org.fog.placement;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
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
import org.fog.entities.HandoffReservation;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.entities.Sensor;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilityTimeline;
import org.fog.localization.Distances;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoCoverage;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.SimulationDuration;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.MigrationCoordinator;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.FogDeviceIndex;
import org.fog.vmmobile.MobileUserApplicationFactory;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.SimulationBuildException;
import org.fog.vmmobile.SimulationContext;
import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MembershipAction;
import org.fog.scheduler.TupleScheduler;

public class MobileController extends SimEntity {
	static final double MIGRATION_DECISION_INTERVAL_MILLIS = 1000.0;

	private static boolean migrationAble;
	private static MigrationPointPolicy migPointPolicy = MigrationPointPolicy.FIXED;

	private static int stepPolicy; // Quantity of steps in the nextStep Function
	private static Coordinate coordDevices;

	private static MigrationStrategyPolicy migStrategyPolicy =
		MigrationStrategyPolicy.LOWEST_LATENCY;
	private static int seed;

	private static List<FogDevice> serverCloudlets;
	private static FogDeviceIndex serverCloudletIndex = FogDeviceIndex.empty();
	private static List<MobileDevice> smartThings;
	private static List<MobileDevice> allSmartThings;
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
	private long migrationDecisionGeneration;
	private final SimulationResultsService resultsService;
	private final MobilityService mobilityService;
	private final MobileAssociationService associationService;
	private final AccessPointAssociationService accessPointAssociationService;
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
		serverCloudletIndex = FogDeviceIndex.empty();
		smartThings = new ArrayList<MobileDevice>();
		allSmartThings = new ArrayList<MobileDevice>();
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
		this.accessPointAssociationService = new AccessPointAssociationService();
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
		this.accessPointAssociationService = new AccessPointAssociationService();
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
		this.accessPointAssociationService = new AccessPointAssociationService();
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
			parent.attachChild(st.getId(), latency);
		}
	}

	private FogDevice getFogDeviceById(int id) {
		return serverCloudletIndex.getById(id);
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
			migrationDecisionGeneration++;
			schedulePeriodic(getId(), 0, MIGRATION_DECISION_INTERVAL_MILLIS,
				MaxAndMin.MAX_SIMULATION_TIME,
				MobileEvents.MIGRATION_DECISION_TICK,
				new MigrationDecisionTick(migrationDecisionGeneration));
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
		SimulationEventSink.current().detail("MobileController", () ->
			"Submitted application " + application.getAppId());
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);
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
				SimulationEventSink.current().trace("MobileController", () ->
					"Launching module " + module.getName() + " on entity " + deviceId);
				sendNow(deviceId, FogEvents.APP_SUBMIT, application);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE_INSTANCE,
					new ModuleLaunchConfig(module, instanceCountMap.get(deviceId).get(module.getName())));
			}
		}
	}

	private void processAppSubmitMigration(SimEvent ev) {
		Application application = (Application) ev.getData();
		SimulationEventSink.current().detail("MobileController", () ->
			"Submitted application after migration " + application.getAppId());
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);
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
			SimulationEventSink.current().trace("MobileController", () ->
				"Launching migrated module " + module.getName() + " on "
					+ sc.getName());
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
			SimulationEventSink.current().trace("MobileController", () ->
				"Received APP_SUBMIT");
			processAppSubmit(ev);
			break;
		case MobileEvents.APP_SUBMIT_MIGRATE:
			processAppSubmitMigration(ev);
			break;
		case FogEvents.TUPLE_FINISHED:
			SimulationEventSink.current().trace("MobileController", () ->
				"Received TUPLE_FINISHED");
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
		case MobileEvents.MIGRATION_DECISION_TICK:
			processMigrationDecisionTick(ev);
			break;
		case MobileEvents.STOP_SIMULATION:
			requestSimulationStop();
			break;

		}
	}

	private void processMigrationDecisionTick(SimEvent event) {
		if (!(event.getData() instanceof MigrationDecisionTick)) {
			return;
		}
		MigrationDecisionTick tick = (MigrationDecisionTick) event.getData();
		if (tick.generation != migrationDecisionGeneration || shutdownRequested) {
			return;
		}
		evaluateMigrationDecisions();
	}

	/**
	 * Evaluates only occupied servers. Sorting by entity ID reproduces the
	 * order in which CloudSim ran the former per-server periodic events.
	 */
	void evaluateMigrationDecisions() {
		for (FogDevice serverCloudlet
			: migrationDecisionTargets(getServerCloudlets())) {
			serverCloudlet.evaluateMigrationDecisions();
		}
	}

	static List<FogDevice> migrationDecisionTargets(
		List<FogDevice> configuredServers) {
		if (configuredServers == null || configuredServers.isEmpty()) {
			return Collections.emptyList();
		}
		List<FogDevice> targets = new ArrayList<FogDevice>();
		for (FogDevice serverCloudlet : configuredServers) {
			if (serverCloudlet != null
				&& !serverCloudlet.getSmartThings().isEmpty()) {
				targets.add(serverCloudlet);
			}
		}
		Collections.sort(targets, Comparator.comparingInt(FogDevice::getId));
		return Collections.unmodifiableList(targets);
	}

	private static final class MigrationDecisionTick {
		private final long generation;

		private MigrationDecisionTick(long generation) {
			this.generation = generation;
		}
	}

	private void requestSimulationStop() {
		if (shutdownRequested) {
			return;
		}
		shutdownRequested = true;
		migrationDecisionGeneration++;
		SimulationEventSink.current().detail("MobileController", () ->
			"Stopping simulation with " + getSmartThings().size()
				+ " active mobile devices");
		SimulationContext context = SimulationContext.currentOrNull();
		long wallTime = context == null
			? Calendar.getInstance().getTimeInMillis()
			: context.getClock().wallTimeMillis();
		for (MobileDevice mobileDevice : getAllSmartThings()) {
			recordTerminalPowerAndEnergy(mobileDevice);
		}
		SimulationMetricsSnapshot metrics = SimulationMetricsSnapshot.capture(
			getServerCloudlets(), getApDevices(), getAllSmartThings(),
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
		updateProgress();
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
		updateProgress();

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

	private void updateProgress() {
		SimulationContext context = SimulationContext.currentOrNull();
		if (context != null) {
			context.updateProgress(CloudSim.clock());
		}
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
		MobileDeviceLifecycle lifecycleBefore = mobileDevice.getLifecycleState();
		boolean statusBefore = mobileDevice.isStatus();
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
		boolean registrationRequired = mobileDevice.getVmMobileDevice() == null;
		MobileUserRegistrationTransaction registration = null;
		try {
			if (registrationRequired) {
				registration = registerMobileUser(mobileDevice);
				registration.apply();
			}
			associationService.activate(mobileDevice, previousState);
			if (registration != null) {
				onRegistrationStep(mobileDevice,
					RegistrationStep.PERIPHERALS_ACTIVATED);
				registerActiveSensors(mobileDevice);
				registration.commit();
			}
			return true;
		}
		catch (RuntimeException error) {
			if (registration != null) {
				registration.rollback();
			}
			else if (registrationRequired) {
				rollbackFailedEntry(mobileDevice);
			}
			if (registrationRequired) {
				mobileDevice.setLifecycleState(lifecycleBefore);
				mobileDevice.setStatus(statusBefore);
			}
			if (error instanceof SimulationBuildException) {
				throw error;
			}
			throw new SimulationBuildException("Could not register entering user "
				+ mobileDevice.getName(), error);
		}
	}

	private void disconnectMobileUser(MobileDevice mobileDevice) {
		associationService.disconnect(mobileDevice);
	}

	/**
	 * Default user-retirement transaction. It is deliberately idempotent: the
	 * broker and archival user identity remain for CloudSim compatibility and
	 * final reporting, while runtime network, application and VM allocation
	 * state is released.
	 */
	void finishMobileUser(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		recordTerminalPowerAndEnergy(mobileDevice);
		associationService.releaseForRetirement(mobileDevice);
		retireApplication(mobileDevice);
		MyStatistics.getInstance().discardOpenIntervals(mobileDevice.getMyId());
	}

	void retireApplication(MobileDevice mobileDevice) {
		String applicationId = "MyApp_vr_game" + mobileDevice.getMyId();
		Vm vm = mobileDevice.getVmMobileDevice();
		for (SimEntity entity : CloudSim.getEntityList()) {
			if (!(entity instanceof FogDevice)) {
				continue;
			}
			FogDevice fogDevice = (FogDevice) entity;
			releaseVmFromHost(fogDevice, vm);
			fogDevice.unregisterHostedMobileVm(mobileDevice);
			fogDevice.removeApplication(applicationId);
		}
		if (getModuleMapping() != null) {
			getModuleMapping().removeModule(
				"AppModuleVm_" + mobileDevice.getName());
			getModuleMapping().removeDevice(mobileDevice.getName());
		}
		FogBroker broker = brokersByMobileId.get(mobileDevice.getMyId());
		if (broker != null && vm != null) {
			broker.getVmList().remove(vm);
			broker.getVmsCreatedList().remove(vm);
		}
		mobileDevice.setVmMobileDevice(null);
		mobileDevice.setVmLocalServerCloudlet(null);
		mobileDevice.setDestinationServerCloudlet(null);
		mobileDevice.setServerCloudletToVmMigrate(null);
	}

	private static void recordTerminalPowerAndEnergy(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		double power = mobileDevice.getCharacteristics() == null
			|| mobileDevice.getHostList().isEmpty()
			? 0.0 : mobileDevice.getHost().getPower();
		MyStatistics.getInstance().recordPowerAndEnergy(mobileDevice.getMyId(),
			power, mobileDevice.getEnergyConsumption());
	}

	private static void releaseVmFromHost(FogDevice serverCloudlet, Vm vm) {
		if (serverCloudlet == null || vm == null
			|| serverCloudlet.getCharacteristics() == null
			|| serverCloudlet.getHostList().isEmpty()) {
			return;
		}
		Host host = serverCloudlet.getHost();
		while (host.getVmList().contains(vm)) {
			host.vmDestroy(vm);
		}
	}

	private MobileUserRegistrationTransaction registerMobileUser(
		MobileDevice mobileDevice) {
		MobileUserPlan plan = MobileUserPlan.create(mobileDevice,
			getModuleMapping(), applications, brokersByMobileId);
		return new MobileUserRegistrationTransaction(plan);
	}

	/** Registration milestones exposed to deterministic failure-injection tests. */
	protected enum RegistrationStep {
		BROKER_REGISTERED,
		VM_ALLOCATED,
		VM_REGISTRATION_PUBLISHED,
		PERIPHERALS_CONFIGURED,
		MODULE_MAPPINGS_ADDED,
		APPLICATION_SUBMITTED,
		APPLICATION_DEPLOYED,
		PERIPHERALS_ACTIVATED
	}

	/** Hook for failure-injection tests; production registration performs no work. */
	protected void onRegistrationStep(MobileDevice mobileDevice,
		RegistrationStep step) {
	}

	private final class MobileUserRegistrationTransaction {
		private final MobileUserPlan plan;
		private final Map<String, Map<String, Integer>> moduleMappingBefore;
		private final Map<String, Application> applicationsBefore;
		private final Map<String, Integer> appLaunchDelaysBefore;
		private final Map<Integer, Double> cpuLoadBefore;
		private final Map<String, Integer> activeSensorApplicationsBefore;
		private final boolean applicationCoverageExisted;
		private final GeoCoverage applicationCoverageBefore;
		private final List<SensorRegistrationState> sensorStates;
		private final List<ActuatorRegistrationState> actuatorStates;
		private final CloudSim.EntityRegistrationCheckpoint entitiesBefore;
		private FogBroker broker;
		private AppModule vm;
		private Application application;
		private boolean committed;
		private boolean rolledBack;

		private MobileUserRegistrationTransaction(MobileUserPlan plan) {
			this.plan = plan;
			this.moduleMappingBefore = getModuleMapping().getModuleMapping();
			this.applicationsBefore =
				new HashMap<String, Application>(applications);
			this.appLaunchDelaysBefore =
				new HashMap<String, Integer>(appLaunchDelays);
			this.cpuLoadBefore =
				new HashMap<Integer, Double>(globalCurrentCpuLoad);
			this.activeSensorApplicationsBefore =
				new HashMap<String, Integer>(activeSensorApplications);
			this.applicationCoverageExisted = FogUtils.getApplicationCoverage()
				.containsKey(plan.getApplicationId());
			this.applicationCoverageBefore = FogUtils.getApplicationCoverage()
				.get(plan.getApplicationId());
			this.sensorStates = captureSensorStates(plan.getMobileDevice());
			this.actuatorStates = captureActuatorStates(plan.getMobileDevice());
			this.entitiesBefore = CloudSim.checkpointEntityRegistrations();
		}

		private void apply() {
			MobileDevice mobileDevice = plan.getMobileDevice();
			try {
				broker = new FogBroker("My_broker" + mobileDevice.getMyId());
				addBrokerFor(mobileDevice, broker);
				onRegistrationStep(mobileDevice,
					RegistrationStep.BROKER_REGISTERED);

				CloudletScheduler scheduler = new TupleScheduler(500, 1);
				long vmSize = 128;
				vm = new AppModule(mobileDevice.getMyId(), plan.getVmName(),
					plan.getApplicationId(), broker.getId(), 281, 128, 1000,
					vmSize, "Vm_" + mobileDevice.getName(), scheduler,
					new HashMap<Pair<String, String>, SelectivityModel>());
				mobileDevice.setVmMobileDevice(vm);
				if (!plan.getServerCloudlet().getHost().vmCreate(vm)) {
					throw new SimulationBuildException(
						"Could not allocate VM for entering user "
							+ mobileDevice.getName() + " on "
							+ plan.getServerCloudlet().getName());
				}
				onRegistrationStep(mobileDevice, RegistrationStep.VM_ALLOCATED);

				mobileDevice.setVmLocalServerCloudlet(plan.getServerCloudlet());
				mobileDevice.setLockedToMigration(false);
				plan.getServerCloudlet().setSmartThingsWithVm(mobileDevice,
					MembershipAction.ADD);
				MobileUserRegistration.submitVm(broker, mobileDevice);
				onRegistrationStep(mobileDevice,
					RegistrationStep.VM_REGISTRATION_PUBLISHED);

				application = MobileUserApplicationFactory.create(
					plan.getApplicationId(), broker.getId(), mobileDevice.getMyId(), vm);
				MobileUserRegistration.configurePeripherals(mobileDevice, broker,
					plan.getApplicationId());
				onRegistrationStep(mobileDevice,
					RegistrationStep.PERIPHERALS_CONFIGURED);

				getModuleMapping().addModuleToDevice(plan.getVmName(),
					plan.getServerCloudlet().getName(), 1);
				getModuleMapping().addModuleToDevice(plan.getClientModuleName(),
					mobileDevice.getName(), 1);
				onRegistrationStep(mobileDevice,
					RegistrationStep.MODULE_MAPPINGS_ADDED);

				submitApplication(application, 0);
				onRegistrationStep(mobileDevice,
					RegistrationStep.APPLICATION_SUBMITTED);
				processAppSubmit(application);
				onRegistrationStep(mobileDevice,
					RegistrationStep.APPLICATION_DEPLOYED);
			}
			catch (RuntimeException error) {
				rollback();
				if (error instanceof SimulationBuildException) {
					throw error;
				}
				throw new SimulationBuildException(
					"Could not register entering user " + mobileDevice.getName(),
					error);
			}
			catch (Exception error) {
				rollback();
				throw new SimulationBuildException(
					"Could not create broker for entering user "
						+ mobileDevice.getName(), error);
			}
		}

		private void commit() {
			committed = true;
		}

		private void rollback() {
			if (committed || rolledBack) {
				return;
			}
			rolledBack = true;
			MobileDevice mobileDevice = plan.getMobileDevice();
			for (SimEntity entity : CloudSim.getEntityList()) {
				if (!(entity instanceof FogDevice)) {
					continue;
				}
				FogDevice fogDevice = (FogDevice) entity;
				releaseApplicationVms(fogDevice, plan.getApplicationId(), vm);
				fogDevice.unregisterHostedMobileVm(mobileDevice);
				fogDevice.removeApplication(plan.getApplicationId());
			}
			getModuleMapping().setModuleMapping(moduleMappingBefore);
			setApplications(applicationsBefore);
			setAppLaunchDelays(appLaunchDelaysBefore);
			setGlobalCurrentCpuLoad(cpuLoadBefore);
			activeSensorApplications.clear();
			activeSensorApplications.putAll(activeSensorApplicationsBefore);
			if (applicationCoverageExisted) {
				FogUtils.registerApplicationCoverage(plan.getApplicationId(),
					applicationCoverageBefore);
			}
			else {
				FogUtils.unregisterApplicationCoverage(plan.getApplicationId());
			}
			if (broker != null) {
				broker.getVmList().clear();
				broker.getVmsCreatedList().clear();
				removeBrokerFor(mobileDevice, broker);
			}
			CloudSim.restoreEntityRegistrations(entitiesBefore);
			mobileDevice.setVmMobileDevice(null);
			mobileDevice.setVmLocalServerCloudlet(null);
			mobileDevice.setDestinationServerCloudlet(null);
			mobileDevice.setServerCloudletToVmMigrate(null);
			restoreSensorStates(sensorStates);
			restoreActuatorStates(actuatorStates);
			rollbackFailedEntry(mobileDevice);
		}
	}

	private static void rollbackFailedEntry(MobileDevice mobileDevice) {
		MobileUserRegistration.disconnectNetwork(mobileDevice);
		MobileUserRegistration.awaitAssociation(mobileDevice);
	}

	private static void releaseApplicationVms(FogDevice fogDevice,
		String applicationId, Vm registeredVm) {
		if (fogDevice.getCharacteristics() == null
			|| fogDevice.getHostList().isEmpty()) {
			return;
		}
		List<Vm> hostedVms = new ArrayList<Vm>(fogDevice.getHost().getVmList());
		for (Vm hostedVm : hostedVms) {
			boolean belongsToApplication = hostedVm == registeredVm
				|| hostedVm instanceof AppModule
					&& applicationId.equals(((AppModule) hostedVm).getAppId());
			if (belongsToApplication) {
				while (fogDevice.getHost().getVmList().contains(hostedVm)) {
					fogDevice.getHost().vmDestroy(hostedVm);
				}
			}
		}
	}

	private static final class SensorRegistrationState {
		private final MobileSensor sensor;
		private final String applicationId;
		private final int userId;
		private final int gatewayDeviceId;
		private final Double latency;
		private final Application application;
		private final boolean enabled;

		private SensorRegistrationState(MobileSensor sensor) {
			this.sensor = sensor;
			this.applicationId = sensor.getAppId();
			this.userId = sensor.getUserId();
			this.gatewayDeviceId = sensor.getGatewayDeviceId();
			this.latency = sensor.getLatency();
			this.application = sensor.getApp();
			this.enabled = sensor.isEnabled();
		}
	}

	private static final class ActuatorRegistrationState {
		private final MobileActuator actuator;
		private final String applicationId;
		private final int userId;
		private final int gatewayDeviceId;
		private final double latency;
		private final String actuatorType;
		private final Application application;
		private final boolean enabled;

		private ActuatorRegistrationState(MobileActuator actuator) {
			this.actuator = actuator;
			this.applicationId = actuator.getAppId();
			this.userId = actuator.getUserId();
			this.gatewayDeviceId = actuator.getGatewayDeviceId();
			this.latency = actuator.getLatency();
			this.actuatorType = actuator.getActuatorType();
			this.application = actuator.getApp();
			this.enabled = actuator.isEnabled();
		}
	}

	private static List<SensorRegistrationState> captureSensorStates(
		MobileDevice mobileDevice) {
		List<SensorRegistrationState> states =
			new ArrayList<SensorRegistrationState>();
		for (MobileSensor sensor : mobileDevice.getSensors()) {
			states.add(new SensorRegistrationState(sensor));
		}
		return states;
	}

	private static List<ActuatorRegistrationState> captureActuatorStates(
		MobileDevice mobileDevice) {
		List<ActuatorRegistrationState> states =
			new ArrayList<ActuatorRegistrationState>();
		for (MobileActuator actuator : mobileDevice.getActuators()) {
			states.add(new ActuatorRegistrationState(actuator));
		}
		return states;
	}

	private static void restoreSensorStates(
		List<SensorRegistrationState> states) {
		for (SensorRegistrationState state : states) {
			state.sensor.setAppId(state.applicationId);
			state.sensor.setUserId(state.userId);
			state.sensor.setGatewayDeviceId(state.gatewayDeviceId);
			state.sensor.setLatency(state.latency);
			if (state.application == null) {
				state.sensor.clearApplication();
			}
			else {
				state.sensor.setApp(state.application);
			}
			state.sensor.setEnabled(state.enabled);
		}
	}

	private static void restoreActuatorStates(
		List<ActuatorRegistrationState> states) {
		for (ActuatorRegistrationState state : states) {
			state.actuator.setAppId(state.applicationId);
			state.actuator.setUserId(state.userId);
			state.actuator.setGatewayDeviceId(state.gatewayDeviceId);
			state.actuator.setLatency(state.latency);
			state.actuator.setActuatorType(state.actuatorType);
			state.actuator.setApp(state.application);
			state.actuator.setEnabled(state.enabled);
		}
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
				if (!st.isLockedToHandoff()) {
					double distance = Distances.checkDistance(st.getCoord(), st.getSourceAp()
						.getCoord());
					SimulationEventSink.current().trace("MobileController", () ->
						st.getName() + " at " + st.getCoord() + " is " + distance
							+ "m from " + st.getSourceAp().getName());
					if (distance >= MaxAndMin.AP_COVERAGE - MaxAndMin.MAX_DISTANCE_TO_HANDOFF
						&& distance < MaxAndMin.AP_COVERAGE) {
						Optional<ApDevice> nextAp = Migration.nextAp(getApDevices(), st);
						if (nextAp.isPresent()) {
							SimulationDuration handoffSetupDuration =
								SimulationDuration.ofMilliseconds(
									MaxAndMin.MIN_HANDOFF_TIME
									+ (MaxAndMin.MAX_HANDOFF_TIME
										- MaxAndMin.MIN_HANDOFF_TIME)
									* getRand().nextDouble());
							double handoffTime =
								handoffSetupDuration.toMilliseconds();
							float handoffLocked = (float) (handoffTime * 4);
							SimulationDuration reservationLifetime =
								SimulationDuration.ofMilliseconds(handoffTime * 4);
							Optional<HandoffReservation> reservedHandoff =
								accessPointAssociationService.reserveClosestHandoff(
									getApDevices(), st, handoffSetupDuration,
									reservationLifetime);
							if (reservedHandoff.isPresent()) {
								HandoffReservation reservation = reservedHandoff.get();
								int delayConnection = 100;

								if (!st.getDestinationAp().getServerCloudlet()
									.equals(st.getSourceServerCloudlet())) {
									if (isMigrationAble()) {
										FogDevice sourceServer = st.getSourceServerCloudlet();
										ApDevice destinationAccessPoint = st.getDestinationAp();
										HandoffConnectionRequest connectionRequest =
											new HandoffConnectionRequest(st, sourceServer,
												destinationAccessPoint,
												reservation.getAssociationGeneration());
										LogMobile.debug("MobileController.java", st.getName()
											+ " will be desconnected from "
											+ sourceServer.getName() + " by handoff");
										sendNow(sourceServer.getId(),
											MobileEvents.MAKE_DECISION_MIGRATION, st);
										sendNow(sourceServer.getId(),
											MobileEvents.DESCONNECT_ST_TO_SC, st);
										send(connectionRequest.getDestinationServer().getId(),
											handoffTime + delayConnection,
											MobileEvents.CONNECT_ST_TO_SC, connectionRequest);
									}
									if (st.isPostCopyStatus() && !st.isMigStatus()
										&& !st.isMigStatusLive()) {
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
												.getSize() * 1024 * 1024 * 8) * 0.7, 0.0);
										st.setTimeFinishDeliveryVm(-1.0);
										MyStatistics.getInstance().startWithoutVmTime(
											st.getMyId(), CloudSim.clock());
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

								send(st.getSourceAp().getId(), handoffTime,
									MobileEvents.START_HANDOFF, reservation);
								send(st.getDestinationAp().getId(), handoffLocked,
									MobileEvents.UNLOCKED_HANDOFF, st);
								MyStatistics.getInstance().incrementHandoffCount();
								saveHandOff(st);
								LogMobile.debug("MobileController.java", st.getName()
									+ " handoff was scheduled! SourceAp: "
									+ st.getSourceAp().getName() + " NextAp: "
									+ st.getDestinationAp().getName() + "\n");
								LogMobile.debug("MobileController.java", "Distance between "
									+ st.getName() + " and " + st.getSourceAp().getName()
									+ ": " + Distances.checkDistance(st.getCoord(),
										st.getSourceAp().getCoord()));
							}
							else {
								LogMobile.debug("MobileController.java", st.getName()
									+ " remains on its source AP because no destination "
									+ "capacity could be reserved");
							}
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
		SimulationEventSink.current().detail("MobileController", () ->
			"HANDOFF " + st.getMyId() + " Position: " + st.getCoord().getCoordX()
				+ ", " + st.getCoord().getCoordY() + " Direction: "
				+ st.getDirection() + " Speed: " + st.getSpeed());
		try (PrintWriter out = RunOutputManager.getInstance()
			.newDetailedPrintWriter(st.getMyId() + "handoff.txt", true))
		{
			out.println(st.getMyId() + "\t" + CloudSim.clock() + "\t" + st.getCoord().getCoordX()
				+ "\t" + st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t"
				+ st.getSpeed() + "\t" + st.getSourceAp() + "\t" + st.getDestinationAp());
		} catch (IOException e) {
			throw new IllegalStateException(
				"Could not record handoff for " + st.getName(), e);
		}
	}

	@Override
	public void shutdownEntity() {
	}

	public void submitApplication(Application application, int delay) {
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);
		appLaunchDelays.put(application.getAppId(), delay);
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
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);
		appLaunchDelays.put(application.getAppId(), delay);

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
		return Collections.unmodifiableMap(applications);
	}

	public void setApplications(Map<String, Application> applications) {
		if (applications == null) {
			throw new IllegalArgumentException("Application map cannot be null");
		}
		this.applications = new HashMap<String, Application>(applications);
	}

	public Map<String, Integer> getAppLaunchDelays() {
		return Collections.unmodifiableMap(appLaunchDelays);
	}

	public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
		if (appLaunchDelays == null) {
			throw new IllegalArgumentException(
				"Application launch delay map cannot be null");
		}
		this.appLaunchDelays = new HashMap<String, Integer>(appLaunchDelays);
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, Double> getGlobalCurrentCpuLoad() {
		return Collections.unmodifiableMap(globalCurrentCpuLoad);
	}

	public void setGlobalCurrentCpuLoad(Map<Integer, Double> globalCurrentCpuLoad) {
		if (globalCurrentCpuLoad == null) {
			throw new IllegalArgumentException("Global CPU load map cannot be null");
		}
		this.globalCurrentCpuLoad =
			new HashMap<Integer, Double>(globalCurrentCpuLoad);
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
		return Collections.unmodifiableList(brokerList);
	}

	public void setBrokerList(List<FogBroker> brokerList) {
		if (brokerList == null) {
			throw new IllegalArgumentException("Broker list cannot be null");
		}
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
		brokerList.add(broker);
	}

	private void removeBrokerFor(MobileDevice mobileDevice, FogBroker broker) {
		brokersByMobileId.remove(mobileDevice.getMyId());
		brokerList.remove(broker);
	}

	public static int getSeed() {
		return seed;
	}

	public static void setSeed(int seed) {
		MobileController.seed = seed;
	}

	public static List<FogDevice> getServerCloudlets() {
		return serverCloudlets == null ? null
			: Collections.unmodifiableList(serverCloudlets);
	}

	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		if (serverCloudlets == null) {
			MobileController.serverCloudlets = null;
			serverCloudletIndex = FogDeviceIndex.empty();
			return;
		}
		List<FogDevice> copy = new ArrayList<FogDevice>(serverCloudlets);
		FogDeviceIndex index = FogDeviceIndex.copyOf(copy);
		MobileController.serverCloudlets = copy;
		serverCloudletIndex = index;
	}

	public static List<MobileDevice> getSmartThings() {
		return smartThings == null ? null
			: Collections.unmodifiableList(smartThings);
	}

	/** Returns every user registered for this run, including retired users. */
	public static List<MobileDevice> getAllSmartThings() {
		return allSmartThings == null ? null
			: Collections.unmodifiableList(allSmartThings);
	}

	public static void setSmartThings(List<MobileDevice> smartThings) {
		if (smartThings == null) {
			MobileController.smartThings = null;
			MobileController.allSmartThings = null;
			rebuildActiveSensorApplications();
			return;
		}
		MobileController.smartThings =
			new ArrayList<MobileDevice>(smartThings);
		SimulationContext context = SimulationContext.currentOrNull();
		if (context == null) {
			MobileController.allSmartThings =
				new ArrayList<MobileDevice>(smartThings);
		}
		else {
			context.getTopology().activateRegisteredMobileDevices(smartThings);
			MobileController.allSmartThings = context.getTopology().getMobileDevices();
		}
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
		if (smartThings == null || !smartThings.remove(smartThing)) {
			return false;
		}
		SimulationContext context = SimulationContext.currentOrNull();
		if (context != null) {
			context.getTopology().retireMobileDevice(smartThing);
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
		return apDevices == null ? null : Collections.unmodifiableList(apDevices);
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
