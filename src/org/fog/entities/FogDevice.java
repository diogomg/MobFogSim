package org.fog.entities;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilitySample;
import org.fog.localization.MobilityTimeline;
import org.fog.placement.HandoffConnectionRequest;
import org.fog.placement.MobileController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.EntityId;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.BeforeMigration;
import org.fog.vmmigration.CompleteVM;
import org.fog.vmmigration.ContainerVM;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LiveMigration;
import org.fog.vmmigration.MigrationCoordinator;
import org.fog.vmmigration.MigrationEvent;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.Service;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.policy.MembershipAction;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

public class FogDevice extends PowerDatacenter {
	private final MigrationCoordinator migrationCoordinator =
		new MigrationCoordinator();
	private final TupleRoutingService tupleRoutingService =
		new TupleRoutingService();
	protected Queue<Tuple> northTupleQueue;
	protected Queue<Pair<Tuple, Integer>> southTupleQueue;

	protected List<String> activeApplications = new ArrayList<String>();
	protected List<MobilitySample> mobilityPath = new ArrayList<MobilitySample>();

	protected Map<String, Application> applicationMap =
		new HashMap<String, Application>();
	protected Map<String, List<String>> appToModulesMap =
		new HashMap<String, List<String>>();
	protected Map<Integer, Double> childToLatencyMap =
		new HashMap<Integer, Double>();

	protected Map<Integer, Integer> cloudTrafficMap;

	protected double lockTime;

	/**
	 * ID of the parent Fog Device
	 */
	protected int parentId;
	/** @deprecated Use {@link #getVolatileParentId()} and
	 * {@link #setVolatileParentId(int)}. */
	@Deprecated
	protected int volatilParentId;

	/**
	 * ID of the Controller
	 */
	protected int controllerId;
	/**
	 * IDs of the children Fog devices
	 */
	protected List<Integer> childrenIds = new ArrayList<Integer>();

	protected Map<Integer, List<String>> childToOperatorsMap =
		new HashMap<Integer, List<String>>();

	/**
	 * Flag denoting whether the link southwards from this FogDevice is busy
	 */
	protected boolean isSouthLinkBusy;

	/**
	 * Flag denoting whether the link northwards from this FogDevice is busy
	 */
	protected boolean isNorthLinkBusy;

	protected double uplinkBandwidth;
	protected double downlinkBandwidth;
	protected double uplinkLatency;
	protected List<Pair<Integer, Double>> associatedActuatorIds =
		new ArrayList<Pair<Integer, Double>>();

	protected double energyConsumption;
	protected double lastUtilizationUpdateTime;
	protected double lastUtilization;
	private int level;

	protected double ratePerMips;

	protected double totalCost;

	protected Map<String, Map<String, Integer>> moduleInstanceCount =
		new HashMap<String, Map<String, Integer>>();

	protected Coordinate coord;
	protected Set<ApDevice> apDevices = new HashSet<ApDevice>();
	protected Set<MobileDevice> smartThings = new HashSet<MobileDevice>();
	protected Set<MobileDevice> smartThingsWithVm = new HashSet<MobileDevice>();
	protected Set<FogDevice> serverCloudlets = new HashSet<FogDevice>();
	protected boolean available;
	protected Service service;
	protected DecisionMigration migrationStrategy;
	protected MigrationTechniquePolicy policyReplicaVM =
		MigrationTechniquePolicy.COMPLETE_VM;
	private FogDevice serverCloudletToVmMigrate;
	protected BeforeMigration beforeMigration;
	protected double startTravelTime;
	protected int travelTimeId;
	/** @deprecated Use {@link #getTravelPredictionTime()} and
	 * {@link #setTravelPredictionTime(int)}. */
	@Deprecated
	protected int travelPredicTime;
	/** @deprecated Use {@link #getMobilityPredictionError()} and
	 * {@link #setMobilityPredictionError(int)}. */
	@Deprecated
	protected int mobilityPrecitionError;

	protected int myId;

	public int getMyId() {
		return myId;
	}

	public void setMyId(int myId) {
		this.myId = myId;
	}

	public Service getService() {
		return service;
	}

	public void setService(Service service) {
		this.service = service;
	}

	public boolean isAvailable() {
		return available;
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}

	public Set<MobileDevice> getSmartThings() {
		return Collections.unmodifiableSet(smartThings);
	}

	public boolean associateMobileDevice(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		return smartThings.add(mobileDevice);
	}

	public boolean dissociateMobileDevice(MobileDevice mobileDevice) {
		return mobileDevice != null && smartThings.remove(mobileDevice);
	}

	public void setSmartThings(MobileDevice st, int action) {
		setSmartThings(st, MembershipAction.fromLegacy(action));
	}

	public void setSmartThings(MobileDevice st, MembershipAction action) {
		if (action == null) {
			throw new IllegalArgumentException("Membership action cannot be null");
		}
		if (action == MembershipAction.ADD) {
			associateMobileDevice(st);
		}
		else {
			dissociateMobileDevice(st);
		}
	}

	public Set<ApDevice> getApDevices() {
		return Collections.unmodifiableSet(apDevices);
	}

	public boolean attachAccessPoint(ApDevice accessPoint) {
		if (accessPoint == null) {
			throw new IllegalArgumentException("Access point cannot be null");
		}
		if (accessPoint.getServerCloudlet() != null
			&& accessPoint.getServerCloudlet() != this) {
			throw new IllegalStateException("Access point " + accessPoint.getName()
				+ " is already attached to "
				+ accessPoint.getServerCloudlet().getName());
		}
		accessPoint.setServerCloudlet(this);
		accessPoint.setParentId(getId());
		return apDevices.add(accessPoint);
	}

	public boolean detachAccessPoint(ApDevice accessPoint) {
		if (accessPoint == null || !apDevices.remove(accessPoint)) {
			return false;
		}
		if (accessPoint.getServerCloudlet() == this) {
			accessPoint.setServerCloudlet(null);
			accessPoint.setParentId(-1);
		}
		return true;
	}

	public void setApDevices(ApDevice ap, int action) {
		setApDevices(ap, MembershipAction.fromLegacy(action));
	}

	public void setApDevices(ApDevice ap, MembershipAction action) {
		if (action == null) {
			throw new IllegalArgumentException("Membership action cannot be null");
		}
		if (action == MembershipAction.ADD) {
			attachAccessPoint(ap);
		}
		else {
			detachAccessPoint(ap);
		}
	}

	public Coordinate getCoord() {
		return coord;
	}

	public void setCoord(int coordX, int coordY) {
		this.coord.setCoordX(coordX);
		this.coord.setCoordY(coordY);
	}

	public double getStartTravelTime() {
		return startTravelTime;
	}

	public void setStartTravelTime(double startTravelTime) {
		this.startTravelTime = startTravelTime;
	}

	public int getTravelTimeId() {
		return travelTimeId;
	}

	public void setTravelTimeId(int travelTimeId) {
		this.travelTimeId = travelTimeId;
	}

	public int getTravelPredictionTime() {
		return travelPredicTime;
	}

	public void setTravelPredictionTime(int travelPredictionTime) {
		this.travelPredicTime = travelPredictionTime;
	}

	/** @deprecated Use {@link #getTravelPredictionTime()}. */
	@Deprecated
	public int getTravelPredicTime() {
		return getTravelPredictionTime();
	}

	/** @deprecated Use {@link #setTravelPredictionTime(int)}. */
	@Deprecated
	public void setTravelPredicTime(int travelPredictionTime) {
		setTravelPredictionTime(travelPredictionTime);
	}

	public int getMobilityPredictionError() {
		return mobilityPrecitionError;
	}

	/** @deprecated Use {@link #getMobilityPredictionError()}. */
	@Deprecated
	public int getMobilityPrecitionError() {
		return getMobilityPredictionError();
	}

	public void setMobilityPredictionError(int mobilityPredictionError) {
		this.mobilityPrecitionError = mobilityPredictionError;
	}

	public FogDevice() {

	}

	public FogDevice(String name, int coordX, int coordY, int id) {
		super(name);
		this.coord = new Coordinate();
		this.setCoord(coordX, coordY);
		this.setMyId(id);
		smartThings = new HashSet<>();
		apDevices = new HashSet<>();
		serverCloudlets = new HashSet<>();
		this.setAvailable(true);

	}

	public FogDevice(String name) {
		super(name);
	}

	public FogDevice(String name, FogDeviceCharacteristics characteristics,
		VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList,
		double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
		double uplinkLatency, double ratePerMips, int coordX, int coordY, int id, Service service,
		DecisionMigration migrationStrategy, int policyReplicaVM, BeforeMigration beforeMigration)
		throws Exception {
		this(name, characteristics, vmAllocationPolicy, storageList,
			schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency,
			ratePerMips, coordX, coordY, id, service, migrationStrategy,
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM), beforeMigration);
	}

	public FogDevice(String name, FogDeviceCharacteristics characteristics,
		VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList,
		double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
		double uplinkLatency, double ratePerMips, int coordX, int coordY, int id,
		Service service, DecisionMigration migrationStrategy,
		MigrationTechniquePolicy policyReplicaVM, BeforeMigration beforeMigration)
		throws Exception {

		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

		this.coord = new Coordinate();
		this.setCoord(coordX, coordY);
		this.setMyId(id);
		smartThings = new HashSet<>();
		smartThingsWithVm = new HashSet<>();
		apDevices = new HashSet<>();
		setVolatileParentId(-1);
		this.setAvailable(true);
		this.setService(service);

		setBeforeMigrate(beforeMigration);
		setMigrationTechniquePolicy(policyReplicaVM);
		setMigrationStrategy(migrationStrategy);
		setCharacteristics(characteristics);
		setVmAllocationPolicy(vmAllocationPolicy);
		setLastProcessTime(0.0);
		setStorageList(storageList);
		setVmList(new ArrayList<Vm>());
		setSchedulingInterval(schedulingInterval);
		setUplinkBandwidth(uplinkBandwidth);
		setDownlinkBandwidth(downlinkBandwidth);
		setUplinkLatency(uplinkLatency);
		setRatePerMips(ratePerMips);
		setServerCloudletToVmMigrate(null);
		setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
		for (Host host : getCharacteristics().getHostList()) {
			host.setDatacenter(this);
		}
		setActiveApplications(new ArrayList<String>());
		setMobilityPath(Collections.<MobilitySample>emptyList());
		setTravelTimeId(-1);
		setTravelPredictionTime(0);
		setMobilityPredictionError(0);
		// If this resource doesn't have any PEs then no useful at all
		if (getCharacteristics().getNumberOfPes() == 0) {
			throw new Exception(super.getName()
				+ " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
		}
		// stores id of this class
		getCharacteristics().setId(super.getId());

		applicationMap = new HashMap<String, Application>();
		appToModulesMap = new HashMap<String, List<String>>();
		northTupleQueue = new LinkedList<Tuple>();
		southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
		setNorthLinkBusy(false);
		setSouthLinkBusy(false);

		setChildrenIds(new ArrayList<Integer>());
		setChildToOperatorsMap(new HashMap<Integer, List<String>>());

		this.cloudTrafficMap = new HashMap<Integer, Integer>();

		this.lockTime = 0;

		this.energyConsumption = 0;
		this.lastUtilization = 0;
		setTotalCost(0);
		setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
		setChildToLatencyMap(new HashMap<Integer, Double>());
	}

	public FogDevice(
		String name,
		FogDeviceCharacteristics characteristics,
		VmAllocationPolicy vmAllocationPolicy,
		List<Storage> storageList,
		double schedulingInterval,
		double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips
		, int coordX, int coordY, int id

	) throws Exception {

		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

		this.coord = new Coordinate();
		this.setCoord(coordX, coordY);
		this.setMyId(id);
		smartThings = new HashSet<>();
		smartThingsWithVm = new HashSet<>();

		apDevices = new HashSet<>();
		setVolatileParentId(-1);

		this.setAvailable(true);
		setCharacteristics(characteristics);
		setVmAllocationPolicy(vmAllocationPolicy);
		setLastProcessTime(0.0);
		setStorageList(storageList);
		setVmList(new ArrayList<Vm>());
		setSchedulingInterval(schedulingInterval);
		setUplinkBandwidth(uplinkBandwidth);
		setDownlinkBandwidth(downlinkBandwidth);
		setUplinkLatency(uplinkLatency);
		setRatePerMips(ratePerMips);
		setServerCloudletToVmMigrate(null);

		setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
		for (Host host : getCharacteristics().getHostList()) {
			host.setDatacenter(this);
		}
		setActiveApplications(new ArrayList<String>());
		setMobilityPath(Collections.<MobilitySample>emptyList());
		setTravelTimeId(-1);
		setTravelPredictionTime(0);
		setMobilityPredictionError(0);
		// If this resource doesn't have any PEs then no useful at all
		if (getCharacteristics().getNumberOfPes() == 0) {
			throw new Exception(super.getName()
				+ " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
		}
		// stores id of this class
		getCharacteristics().setId(super.getId());

		applicationMap = new HashMap<String, Application>();
		appToModulesMap = new HashMap<String, List<String>>();
		northTupleQueue = new LinkedList<Tuple>();
		southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
		setNorthLinkBusy(false);
		setSouthLinkBusy(false);

		setChildrenIds(new ArrayList<Integer>());
		setChildToOperatorsMap(new HashMap<Integer, List<String>>());

		this.cloudTrafficMap = new HashMap<Integer, Integer>();

		this.lockTime = 0;

		this.energyConsumption = 0;
		this.lastUtilization = 0;
		setTotalCost(0);
		setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
		setChildToLatencyMap(new HashMap<Integer, Double>());
	}

	public FogDevice(
		String name,
		FogDeviceCharacteristics characteristics,
		VmAllocationPolicy vmAllocationPolicy,
		List<Storage> storageList,
		double schedulingInterval,
		double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips)
		throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
		setCharacteristics(characteristics);
		setVmAllocationPolicy(vmAllocationPolicy);
		setLastProcessTime(0.0);
		setStorageList(storageList);
		setVmList(new ArrayList<Vm>());
		setSchedulingInterval(schedulingInterval);
		setUplinkBandwidth(uplinkBandwidth);
		setDownlinkBandwidth(downlinkBandwidth);
		setUplinkLatency(uplinkLatency);
		setRatePerMips(ratePerMips);
		setServerCloudletToVmMigrate(null);

		setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
		for (Host host : getCharacteristics().getHostList()) {
			host.setDatacenter(this);
		}
		setActiveApplications(new ArrayList<String>());
		setMobilityPath(Collections.<MobilitySample>emptyList());
		setTravelTimeId(-1);
		setTravelPredictionTime(0);
		setMobilityPredictionError(0);
		// If this resource doesn't have any PEs then no useful at all
		if (getCharacteristics().getNumberOfPes() == 0) {
			throw new Exception(super.getName()
				+ " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
		}
		// stores id of this class
		getCharacteristics().setId(super.getId());

		applicationMap = new HashMap<String, Application>();
		appToModulesMap = new HashMap<String, List<String>>();
		northTupleQueue = new LinkedList<Tuple>();
		southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
		setNorthLinkBusy(false);
		setSouthLinkBusy(false);

		setChildrenIds(new ArrayList<Integer>());
		setChildToOperatorsMap(new HashMap<Integer, List<String>>());

		this.cloudTrafficMap = new HashMap<Integer, Integer>();

		this.lockTime = 0;

		this.energyConsumption = 0;
		this.lastUtilization = 0;
		setTotalCost(0);
		setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
		setChildToLatencyMap(new HashMap<Integer, Double>());
	}

	public FogDevice(
		String name, long mips, int ram,
		double uplinkBandwidth, double downlinkBandwidth, double ratePerMips, PowerModel powerModel)
		throws Exception {
		super(name, null, null, new LinkedList<Storage>(), 0);

		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		// need to store Pe id and MIPS Rating
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList),
			powerModel);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		setVmAllocationPolicy(new AppModuleAllocationPolicy(hostList));

		String arch = Config.FOG_DEVICE_ARCH;
		String os = Config.FOG_DEVICE_OS;
		String vmm = Config.FOG_DEVICE_VMM;
		double time_zone = Config.FOG_DEVICE_TIMEZONE;
		double cost = Config.FOG_DEVICE_COST;
		double costPerMem = Config.FOG_DEVICE_COST_PER_MEMORY;
		double costPerStorage = Config.FOG_DEVICE_COST_PER_STORAGE;
		double costPerBw = Config.FOG_DEVICE_COST_PER_BW;

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
			arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);

		setCharacteristics(characteristics);

		setLastProcessTime(0.0);
		setVmList(new ArrayList<Vm>());
		setUplinkBandwidth(uplinkBandwidth);
		setDownlinkBandwidth(downlinkBandwidth);
		setUplinkLatency(uplinkLatency);
		setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
		for (Host host1 : getCharacteristics().getHostList()) {
			host1.setDatacenter(this);
		}
		setActiveApplications(new ArrayList<String>());
		setMobilityPath(Collections.<MobilitySample>emptyList());
		setTravelTimeId(-1);
		setTravelPredictionTime(0);
		setMobilityPredictionError(0);
		if (getCharacteristics().getNumberOfPes() == 0) {
			throw new Exception(super.getName()
				+ " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
		}

		getCharacteristics().setId(super.getId());

		applicationMap = new HashMap<String, Application>();
		appToModulesMap = new HashMap<String, List<String>>();
		northTupleQueue = new LinkedList<Tuple>();
		southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
		setNorthLinkBusy(false);
		setSouthLinkBusy(false);

		setChildrenIds(new ArrayList<Integer>());
		setChildToOperatorsMap(new HashMap<Integer, List<String>>());

		this.cloudTrafficMap = new HashMap<Integer, Integer>();

		this.lockTime = 0;

		this.energyConsumption = 0;
		this.lastUtilization = 0;
		setTotalCost(0);
		setChildToLatencyMap(new HashMap<Integer, Double>());
		setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
	}

	/**
	 * Overrides this method when making a new and different type of resource. <br>
	 * <b>NOTE:</b> You do not need to override {@link #body()} method, if you
	 * use this method.
	 * 
	 * @pre $none
	 * @post $none
	 */
	@Override
	protected void registerOtherEntity() {

	}

	@Override
	protected void processOtherEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case FogEvents.TUPLE_ARRIVAL:
			processTupleArrival(ev);
			break;
		case FogEvents.LAUNCH_MODULE:
			processModuleArrival(ev);
			break;
		case FogEvents.RELEASE_OPERATOR:
			processOperatorRelease(ev);
			break;
		case FogEvents.SENSOR_JOINED:
			processSensorJoining(ev);
			break;
		case FogEvents.SEND_PERIODIC_TUPLE:
			sendPeriodicTuple(ev);
			break;
		case FogEvents.APP_SUBMIT:
			processAppSubmit(ev);
			break;
		case FogEvents.UPDATE_NORTH_TUPLE_QUEUE:
			updateNorthTupleQueue();
			break;
		case FogEvents.UPDATE_SOUTH_TUPLE_QUEUE:
			updateSouthTupleQueue();
			break;
		case FogEvents.WIRELESS_TRANSFER_COMPLETE:
			completeWirelessTupleTransfer(ev);
			break;
		case FogEvents.ACTIVE_APP_UPDATE:
			updateActiveApplications(ev);
			break;
		case FogEvents.ACTUATOR_JOINED:
			processActuatorJoined(ev);
			break;
		case FogEvents.LAUNCH_MODULE_INSTANCE:
			updateModuleInstanceCount(ev);
			break;
		case FogEvents.RESOURCE_MGMT:
			manageResources(ev);
			break;
		case MobileEvents.MAKE_DECISION_MIGRATION:
			invokeDecisionMigration(ev);
			break;
		case MobileEvents.TO_MIGRATION:
			invokeBeforeMigration(ev);
			break;
		case MobileEvents.NO_MIGRATION:
			invokeNoMigration(ev);
			break;
		case MobileEvents.START_MIGRATION:
			invokeStartMigration(ev);
			break;
		case MobileEvents.START_MIGRATION_TRANSFER:
			startMigrationTransfer(ev);
			break;
		case MobileEvents.ABORT_MIGRATION:
			invokeAbortMigration(ev);
			break;
		case MobileEvents.DELIVERY_VM:
			deliveryVM(ev);
			break;
		case MobileEvents.CONNECT_ST_TO_SC:
			connectServerCloudletSmartThing(ev);
			break;
		case MobileEvents.DISCONNECT_ST_TO_SC:
			disconnectServerCloudletSmartThing(ev);
			break;
		case MobileEvents.UNLOCKED_MIGRATION:
			unLockedMigration(ev);
			break;
		case MobileEvents.VM_MIGRATE:
			myVmMigrate(ev);
			break;
		case MobileEvents.SET_MIG_STATUS_TRUE:
			migStatusToLiveMigration(ev);
			break;

		default:
			break;
		}
	}

	private void myVmMigrate(SimEvent ev) {
		MobileDevice smartThing = migrationCoordinator.currentMobile(ev.getData(),
			MigrationEvent.Stage.MIGRATE_APPLICATION);
		if (!migrationCoordinator.isInstallationScheduled(smartThing)) {
			return;
		}
		SimulationEventSink.current().trace("FogDevice", () -> "Migrating user "
			+ smartThing.getMyId() + " from "
			+ smartThing.getVmLocalServerCloudlet().getName() + " applications "
			+ smartThing.getVmLocalServerCloudlet().getActiveApplications() + " to "
			+ smartThing.getDestinationServerCloudlet().getName() + " applications "
			+ smartThing.getDestinationServerCloudlet().getActiveApplications());
		Application app = smartThing.getVmLocalServerCloudlet().applicationMap.get("MyApp_vr_game"
			+ smartThing.getMyId());
		if (app == null) {
			scheduleMigrationAbort(smartThing,
				"application MyApp_vr_game" + smartThing.getMyId()
					+ " is missing from the current VM host");
			return;
		}
		installApplication(app);

		if (smartThing.getVmLocalServerCloudlet()
			.removeApplication(app.getAppId()) == null) {
			removeApplication(app.getAppId());
			scheduleMigrationAbort(smartThing,
				"application " + app.getAppId() + " could not be removed from "
					+ smartThing.getVmLocalServerCloudlet().getName());
			return;
		}

		MobileController mobileController = (MobileController) CloudSim
			.getEntity("MobileController");

		mobileController.getModuleMapping().moveModule(
			smartThing.getVmLocalServerCloudlet().getName(), getName(),
			((AppModule) smartThing.getVmMobileDevice()).getName(), 1);
		SimulationEventSink.current().trace("FogDevice", () ->
			"Submitting migrated application on " + getName());
		mobileController.submitApplicationMigration(smartThing, app, 1);

		sendNow(mobileController.getId(), MobileEvents.APP_SUBMIT_MIGRATE, app);
	}

	private void unLockedMigration(SimEvent ev) {
		MobileDevice mobileDevice = migrationCoordinator.currentMobile(ev.getData(),
			MigrationEvent.Stage.UNLOCK);
		if (mobileDevice != null) {
			migrationCoordinator.unlock(mobileDevice);
		}
	}

	private void disconnectServerCloudletSmartThing(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		if (smartThing == null
			|| smartThing.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		if (disconnectServerCloudletSmartThing(smartThing)) {
			MyStatistics.getInstance().startWithoutConnection(
				smartThing.getMyId(), CloudSim.clock());
		}
	}

	private void connectServerCloudletSmartThing(SimEvent ev) {
		if (!(ev.getData() instanceof HandoffConnectionRequest)) {
			return;
		}
		HandoffConnectionRequest request =
			(HandoffConnectionRequest) ev.getData();
		if (!request.isCurrentFor(this)) {
			return;
		}
		MobileDevice smartThing = request.getMobileDevice();
		if (smartThing.getLifecycleState() == MobileDeviceLifecycle.FINISHED
			|| !connectServerCloudletSmartThing(smartThing)) {
			return;
		}
		MyStatistics.getInstance().finalWithoutConnection(smartThing.getMyId(), CloudSim.clock());
		if (smartThing.getVmLocalServerCloudlet() == null
			|| smartThing.getVmMobileDevice() == null) {
			return;
		}

		if (smartThing.getTimeFinishDeliveryVm() == -1) {
			MyStatistics.getInstance().startDelayAfterNewConnection(smartThing.getMyId(),
				CloudSim.clock());
		}
		else {
			smartThing.setMigStatus(false);
			smartThing.setPostCopyStatus(false);
			smartThing.setMigStatusLive(false);
			if (MyStatistics.getInstance().getInitialWithoutVmTime().get(smartThing.getMyId()) != null) {
				MyStatistics.getInstance().finalWithoutVmTime(smartThing.getMyId(), CloudSim.clock());
			}
			LogMobile.debug("FogDevice.java", smartThing.getName()
				+ " had migStatus to false - connectServerCloudlet");
			MyStatistics.getInstance().startDelayAfterNewConnection(smartThing.getMyId(), 0.0);
			MyStatistics.getInstance().finalDelayAfterNewConnection(smartThing.getMyId(),
				getCharacteristics().getCpuTime( smartThing.getVmMobileDevice().getSize() * 1024 * 1024 * 8, 0.0));
		}
	}

	private void invokeAbortMigration(SimEvent ev) {
		MobileDevice smartThing = migrationCoordinator.currentMobile(ev.getData(),
			MigrationEvent.Stage.ABORT);
		if (smartThing == null) {
			return;
		}
		SimulationEventSink.current().detail("FogDevice", () ->
			"Aborting migration preparation for " + smartThing.getName());
		migrationCoordinator.abort(smartThing);
	}

	public boolean connectServerCloudletSmartThing(MobileDevice st) {
		if (st == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		if (st.getSourceServerCloudlet() == this) {
			return false;
		}
		if (st.getSourceServerCloudlet() != null) {
			throw new IllegalStateException("Mobile device " + st.getName()
				+ " is already connected to "
				+ st.getSourceServerCloudlet().getName());
		}

		double latency = st.getWirelessPropagationDelayMillis();
		if (!Double.isFinite(latency) || latency < 0.0) {
			throw new IllegalArgumentException(
				"Mobile-device uplink latency must be finite and non-negative");
		}

		st.setSourceServerCloudlet(this);
		associateMobileDevice(st);
		st.setParentId(getId());
		attachChild(st.getId(), latency);
		LogMobile.debug("FogDevice.java", st.getName() + " was connected to " + getName());

		return true;
	}

	public boolean disconnectServerCloudletSmartThing(MobileDevice st) {
		if (st == null || st.getSourceServerCloudlet() != this) {
			return false;
		}
		boolean removed = dissociateMobileDevice(st);
		st.setSourceServerCloudlet(null);
		if (st.getParentId() == getId()) {
			st.setParentId(-1);
		}
		detachChild(st.getId());
		LogMobile.debug("FogDevice.java", st.getName() + " was disconnected from " + getName());
		return removed;

	}

	/** @deprecated Use {@link #disconnectServerCloudletSmartThing(MobileDevice)}. */
	@Deprecated
	public boolean desconnectServerCloudletSmartThing(MobileDevice st) {
		return disconnectServerCloudletSmartThing(st);
	}

	private void invokeStartMigration(SimEvent ev) {
		MobileDevice smartThing = completeMigrationTransfer(ev);
		if (smartThing == null) {
			return;
		}
		MigrationCoordinator.StartPlan plan = migrationCoordinator.planStart(
			smartThing, MobileController.getSmartThings());
		if (plan.getDisposition()
			== MigrationCoordinator.StartDisposition.IGNORE) {
			return;
		}
		if (plan.getDisposition()
			== MigrationCoordinator.StartDisposition.ABORT) {
			scheduleMigrationAbort(smartThing, plan.getReason());
			return;
		}

		int srcId = getId();
		int entityId = smartThing.getDestinationServerCloudlet().getId();
		double delay = entityId == srcId
			? 1.0 : 1.0 + getNetworkDelay(srcId, entityId);
		final double deliveryDelay = delay;
		send(smartThing.getVmLocalServerCloudlet().getId(), delay,
			MobileEvents.DELIVERY_VM, migrationCoordinator.event(smartThing,
				MigrationEvent.Stage.INSTALL_VM));
		SimulationEventSink.current().trace("FogDevice", () ->
			smartThing.getName() + " scheduled VM delivery from "
				+ smartThing.getVmLocalServerCloudlet().getName() + " to "
				+ smartThing.getDestinationServerCloudlet().getName()
				+ " with delay " + deliveryDelay);

		sendNow(smartThing.getDestinationServerCloudlet().getId(),
			MobileEvents.VM_MIGRATE, migrationCoordinator.event(smartThing,
				MigrationEvent.Stage.MIGRATE_APPLICATION));
		Map<String, Object> migration = new HashMap<String, Object>();
		migration.put("vm", smartThing.getVmMobileDevice());
		migration.put("host", smartThing.getDestinationServerCloudlet().getHost());
		sendNow(smartThing.getVmLocalServerCloudlet().getId(),
			CloudSimTags.VM_MIGRATE, migration);
		SimulationEventSink.current().trace("FogDevice", () ->
			"Scheduled VM " + smartThing.getVmMobileDevice().getId()
				+ " migration to host "
				+ smartThing.getDestinationServerCloudlet().getHost().getId());
	}

	private void scheduleMigrationAbort(MobileDevice smartThing, String reason) {
		SimulationEventSink.current().detail("FogDevice", () ->
			"Aborting migration for " + smartThing.getName() + " because " + reason);
		FogDevice currentHost = smartThing.getVmLocalServerCloudlet();
		int abortHandlerId = currentHost == null ? getId() : currentHost.getId();
		sendNow(abortHandlerId, MobileEvents.ABORT_MIGRATION,
			migrationCoordinator.event(smartThing, MigrationEvent.Stage.ABORT));
	}

	private void deliveryVM(SimEvent ev) {
		MobileDevice smartThing = migrationCoordinator.currentMobile(ev.getData(),
			MigrationEvent.Stage.INSTALL_VM);
		if (smartThing == null) {
			return;
		}
		if (MobileController.getSmartThings().contains(smartThing)) {
			FogDevice source = smartThing.getVmLocalServerCloudlet();
			FogDevice destination = smartThing.getDestinationServerCloudlet();
			if (!migrationCoordinator.installVm(smartThing,
				MobileController.getSmartThings())) {
				return;
			}
			LogMobile.debug("FogDevice.java", "DELIVERY VM: " + smartThing.getName() + " (id: "
				+ smartThing.getId() + ") from " + source.getName()
				+ " to " + destination.getName());

			if (MyStatistics.getInstance().getInitialTimeDelayAfterNewConnection()
				.containsKey(smartThing.getMyId())) {
				smartThing.setMigStatus(false);
				smartThing.setPostCopyStatus(false);
				smartThing.setMigStatusLive(false);
					if (MyStatistics.getInstance().getInitialWithoutVmTime().get(smartThing.getMyId()) != null) {
						MyStatistics.getInstance().finalWithoutVmTime(smartThing.getMyId(), CloudSim.clock());
						SimulationEventSink.current().trace("FogDevice", () ->
							"Finalised without-VM interval for " + smartThing.getName());
					}
				LogMobile.debug("FogDevice.java", smartThing.getName()
					+ " had migStatus to false - deliveryVM");
				// handoff has been occurred first than delivery
				MyStatistics.getInstance().finalDelayAfterNewConnection(smartThing.getMyId(), CloudSim.clock()
						+ getCharacteristics().getCpuTime(smartThing.getVmMobileDevice().getSize() * 1024 * 1024 * 8, 0.0));
			}

			float migrationLocked = (smartThing.getVmMobileDevice().getSize() * (smartThing
				.getSpeed() + 1)) + 20000;
			if (migrationLocked < smartThing.getTravelPredictionTime() * 1000) {
				migrationLocked = smartThing.getTravelPredictionTime() * 1000;
			}
			send(smartThing.getVmLocalServerCloudlet().getId(), migrationLocked,
				MobileEvents.UNLOCKED_MIGRATION,
				migrationCoordinator.event(smartThing, MigrationEvent.Stage.UNLOCK));
			MyStatistics.getInstance().countMigration();
			MyStatistics.getInstance().observeMigrationTime(smartThing.getMyId(),
				smartThing.getMigTime());
			if (smartThing.getMigrationTechnique() instanceof CompleteVM) {
				MyStatistics.getInstance().observeDowntime(smartThing.getMyId(),
					smartThing.getMigTime());
			}
			else if (smartThing.getMigrationTechnique() instanceof ContainerVM) {
				MyStatistics.getInstance().observeDowntime(smartThing.getMyId(),
					smartThing.getMigTime());
			}
			else if (smartThing.getMigrationTechnique() instanceof LiveMigration) {
				MyStatistics.getInstance().observeDowntime(smartThing.getMyId(),
					smartThing.getMigTime() * 0.15);
			}
			smartThing.setTimeFinishDeliveryVm(CloudSim.clock());
		}
		else {
			LogMobile.debug("FogDevice.java", smartThing.getName()
				+ " was excluded by List of SmartThings! (inside Delivery Vm)");
		}
	}

	private void invokeNoMigration(SimEvent ev) {
		MobileDevice mobileDevice = migrationCoordinator.currentMobile(ev.getData());
		if (mobileDevice != null) {
			migrationCoordinator.noMigration(mobileDevice);
		}
	}

	private void invokeBeforeMigration(SimEvent ev) {
		MobileDevice mobileDevice = migrationCoordinator.currentMobile(ev.getData(),
			MigrationEvent.Stage.PREPARE);
		if (mobileDevice == null) {
			return;
		}
		migrationCoordinator.prepare(mobileDevice,
			MobileController.getSmartThings(), getBeforeMigrate(),
			getMigrationTechniquePolicy(),
			(destinationId, delay, eventTag, payload) ->
				send(destinationId, delay, eventTag, payload));
	}

	private void migStatusToLiveMigration(SimEvent ev) {
		MobileDevice smartThing = completeMigrationTransfer(ev);
		if (smartThing == null) {
			return;
		}
		sendNow(smartThing.getVmLocalServerCloudlet().getId(),
			MobileEvents.START_MIGRATION,
			migrationCoordinator.event(smartThing,
				MigrationEvent.Stage.START_DELIVERY));
	}

	private void startMigrationTransfer(SimEvent ev) {
		MigrationTransferSpec transferSpec = (MigrationTransferSpec) ev.getData();
		migrationCoordinator.startTransfer(transferSpec,
			MobileController.getSmartThings());
	}

	private MobileDevice completeMigrationTransfer(SimEvent ev) {
		MigrationCoordinator.Completion completion =
			migrationCoordinator.completeTransfer(ev.getData());
		if (completion == null) {
			return null;
		}
		if (completion.requiresFixedDelay()) {
			send(getId(), completion.getRemainingFixedDelayMillis(), ev.getTag(),
				completion.event());
			return null;
		}
		return completion.getMobileDevice();
	}

	private void invokeDecisionMigration(SimEvent ev) {
		evaluateMigrationDecisions();
	}

	/**
	 * Evaluates the users currently attached to this server. The mobile
	 * controller calls this entry point from its single priority decision tick,
	 * avoiding one periodic CloudSim event for every empty server.
	 */
	public void evaluateMigrationDecisions() {
		migrationCoordinator.decide(getSmartThings(), getId(),
			(destinationId, delay, eventTag, payload) ->
				send(destinationId, delay, eventTag, payload));
	}

	/**
	 * Perform miscellaneous resource management tasks
	 * 
	 * @param ev
	 */
	private void manageResources(SimEvent ev) {
		updateEnergyConsumption();
		send(getId(), Config.RESOURCE_MGMT_INTERVAL, FogEvents.RESOURCE_MGMT);
	}

	/**
	 * Updating the number of modules of an application module on this device
	 * 
	 * @param ev
	 *        instance of SimEvent containing the module and no of instances
	 */
	private void updateModuleInstanceCount(SimEvent ev) {
		ModuleLaunchConfig config = (ModuleLaunchConfig) ev.getData();
		String appId = config.getModule().getAppId();
		if (!moduleInstanceCount.containsKey(appId))
			moduleInstanceCount.put(appId, new HashMap<String, Integer>());
		moduleInstanceCount.get(appId).put(config.getModule().getName(), config.getInstanceCount());
		SimulationEventSink.current().trace("FogDevice", () -> getName()
			+ " creating " + config.getInstanceCount() + " instances of module "
			+ config.getModule().getName());
	}

	/**
	 * Sending periodic tuple for an application edge. Note that for multiple
	 * instances of a single source module, only one tuple is sent DOWN while
	 * instanceCount number of tuples are sent UP.
	 * 
	 * @param ev
	 *        SimEvent instance containing the edge to send tuple on
	 */
	protected void sendPeriodicTuple(SimEvent ev) {
		AppEdge edge = (AppEdge) ev.getData();
		String srcModule = edge.getSource();
		AppModule module = null;
		for (Vm vm : getHost().getVmList()) {
			if (((AppModule) vm).getName().equals(srcModule)) {
				module = (AppModule) vm;
				break;
			}
		}
		if (module == null)
			return;

		String appId = module.getAppId();
		Application application = applicationMap.get(appId);
		if (application == null) {
			return;
		}

		Map<String, Integer> applicationInstanceCounts =
			moduleInstanceCount.get(appId);
		if (applicationInstanceCounts == null) {
			return;
		}
		Integer instanceCount = applicationInstanceCounts.get(srcModule);
		if (instanceCount == null || instanceCount <= 0) {
			return;
		}

		/*
		 * Since tuples sent through a DOWN application edge are anyways
		 * broadcasted, only UP tuples are replicated
		 */
		for (int i = 0; i < ((edge.getDirection() == Tuple.UP) ? instanceCount : 1); i++) {
			for (Tuple tuple : application.createTuples(edge, getId())) {
				updateTimingsOnSending(tuple);
				sendToSelf(tuple);
			}
		}
		send(getId(), edge.getPeriodicity(), FogEvents.SEND_PERIODIC_TUPLE, edge);
	}

	protected void processActuatorJoined(SimEvent ev) {
		int actuatorId = ev.getSource();
		double delay = (double) ev.getData();
		associateActuator(actuatorId, delay);
	}

	protected void updateActiveApplications(SimEvent ev) {
		Application app = (Application) ev.getData();
		activateApplication(app.getAppId());
		SimulationEventSink.current().trace("FogDevice", () ->
			"Active applications on " + getName() + ": " + getActiveApplications());
	}

	public String getOperatorName(int vmId) {
		for (Vm vm : this.getHost().getVmList()) {
			if (vm.getId() == vmId)
				return ((AppModule) vm).getName();
		}
		return null;
	}

	/**
	 * Update cloudet processing without scheduling future events.
	 * 
	 * @return the double
	 */
	@Override
	protected double updateCloudetProcessingWithoutSchedulingFutureEventsForce() {
		double currentTime = CloudSim.clock();
		double minTime = Double.MAX_VALUE;
		double timeDiff = currentTime - getLastProcessTime();
		double timeFrameDatacenterEnergy = 0.0;

		for (PowerHost host : this.<PowerHost>getHostList()) {
			Log.printLine();

			// inform VMs to update processing
			double time = host.updateVmsProcessing(currentTime);
			if (time < minTime) {
				minTime = time;
			}

			Log.formatLine("%.2f: [Host #%d] utilization is %.2f%%", currentTime,
				host.getId(), host.getUtilizationOfCpu() * 100);
		}

		if (timeDiff > 0) {
			Log.formatLine(
				"\nEnergy consumption for the last time frame from %.2f to %.2f:",
				getLastProcessTime(),
				currentTime);

			for (PowerHost host : this.<PowerHost>getHostList()) {
				double previousUtilizationOfCpu = host.getPreviousUtilizationOfCpu();
				double utilizationOfCpu = host.getUtilizationOfCpu();
				double timeFrameHostEnergy = host.getEnergyLinearInterpolation(
					previousUtilizationOfCpu, utilizationOfCpu, timeDiff);
				timeFrameDatacenterEnergy += timeFrameHostEnergy;

				Log.printLine();
				Log.formatLine("%.2f: [Host #%d] utilization at %.2f was %.2f%%, now is %.2f%%",
					currentTime, host.getId(), getLastProcessTime(), previousUtilizationOfCpu * 100,
					utilizationOfCpu * 100);
				Log.formatLine("%.2f: [Host #%d] energy is %.2f W*sec", currentTime,
					host.getId(), timeFrameHostEnergy);
			}

			Log.formatLine("\n%.2f: Data center's energy is %.2f W*sec\n",
				currentTime, timeFrameDatacenterEnergy);
		}

		setPower(getPower() + timeFrameDatacenterEnergy);

		checkCloudletCompletion();

		Log.printLine();

		setLastProcessTime(currentTime);
		return minTime;
	}

	@Override
	protected void checkCloudletCompletion() {
		boolean cloudletCompleted = false;
		List<Vm> removeVmList = new ArrayList<>();
		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {
						cloudletCompleted = true;
						Tuple tuple = (Tuple) cl;
						TimeKeeper.getInstance().tupleEndedExecution(tuple);
						Application application = getApplicationMap().get(tuple.getAppId());
						if (application == null) {
							removeVmList.add(vm);
							continue;
						}

						Logger.debug(getName(), "Completed execution of tuple " + tuple.getCloudletId() + " on "
								+ tuple.getDestModuleName());

						List<Tuple> resultantTuples = application.getResultantTuples(
							tuple.getDestModuleName(), tuple, getId());
						for (Tuple resTuple : resultantTuples) {
							resTuple.setModuleCopyMap(new HashMap<String, Integer>(tuple
								.getModuleCopyMap()));
							resTuple.recordModuleCopy(((AppModule) vm).getName(), vm.getId());
							updateTimingsOnSending(resTuple);
							sendToSelf(resTuple);
						}
						sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
					}
				}
			}
			for (Vm vm : removeVmList) {
				host.getVmList().remove(vm);
			}
			removeVmList.clear();
		}

		if (cloudletCompleted)
			updateAllocatedMips(null);
	}

	protected void updateTimingsOnSending(Tuple resTuple) {
		String srcModule = resTuple.getSrcModuleName();
		String destModule = resTuple.getDestModuleName();
		for (AppLoop loop : getApplicationMap().get(resTuple.getAppId()).getLoops()) {
			if (loop.hasEdge(srcModule, destModule) && loop.isStartModule(srcModule)) {
				int tupleId = TimeKeeper.getInstance().getUniqueId();
				resTuple.setActualTupleId(tupleId);
				TimeKeeper.getInstance().registerLoop(loop.getLoopId());
				TimeKeeper.getInstance().recordEmission(tupleId, CloudSim.clock());
			}
		}
	}

	protected int getChildIdWithRouteTo(int targetDeviceId) {
		return tupleRoutingService.childRoute(getChildrenIds(), targetDeviceId);
	}

	protected int getChildIdForTuple(Tuple tuple) {
		return tupleRoutingService.childRoute(tuple, getChildrenIds());
	}

	protected void updateAllocatedMips(String incomingOperator) {
		getHost().getVmScheduler().deallocatePesForAllVms();
		for (final Vm vm : getHost().getVmList()) {
			if (vm.getCloudletScheduler().runningCloudlets() > 0
				|| ((AppModule) vm).getName().equals(incomingOperator)) {
				getHost().getVmScheduler().allocatePesForVm(vm,
					java.util.Collections.singletonList((double) getHost().getTotalMips()));
			} else {
				getHost().getVmScheduler().allocatePesForVm(vm,
					java.util.Collections.singletonList(0.0));
			}
			// }
		}

		updateEnergyConsumption();

	}

	private void updateEnergyConsumption() {
		double totalMipsAllocated = 0;
		for (final Vm vm : getHost().getVmList()) {
			totalMipsAllocated += getHost().getTotalAllocatedMipsForVm(vm);
		}

		double timeNow = CloudSim.clock();
		double currentEnergyConsumption = getEnergyConsumption();
		double newEnergyConsumption = currentEnergyConsumption + (timeNow - lastUtilizationUpdateTime)
			* getHost().getPowerModel().getPower(lastUtilization);
		setEnergyConsumption(newEnergyConsumption);
		double currentCost = getTotalCost();
		double newcost = currentCost + (timeNow - lastUtilizationUpdateTime) * getRatePerMips()
			* lastUtilization * getHost().getTotalMips();
		setTotalCost(newcost);

		lastUtilization = Math.min(1, totalMipsAllocated / getHost().getTotalMips());
		lastUtilizationUpdateTime = timeNow;
	}

	protected void processAppSubmit(SimEvent ev) {
		Application app = (Application) ev.getData();
		installApplication(app);
	}

	protected void addChild(int childId) {
		if (CloudSim.getEntityName(childId).toLowerCase().contains("sensor"))
			return;
		addChildRecord(childId);
	}

	protected void removeChild(int childId) {
		detachChild(childId);
	}

	protected void updateCloudTraffic() {
		int time = (int) CloudSim.clock() / 1000;
		if (!cloudTrafficMap.containsKey(time))
			cloudTrafficMap.put(time, 0);
		cloudTrafficMap.put(time, cloudTrafficMap.get(time) + 1);
	}

	protected void sendTupleToActuator(Tuple tuple) {
		TupleRoutingService.ActuatorRoute route = tupleRoutingService.actuatorRoute(
			tuple, getAssociatedActuatorIds());
		if (route != null) {
			send(route.getActuatorId(), route.getDelay(), FogEvents.TUPLE_ARRIVAL,
				tuple);
			return;
		}
		for (int childId : getChildrenIds()) {
			sendDown(tuple, childId);
		}
	}

	int numClients = 0;

	public void saveLostTuple(String a, String filename) {
		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true))
		{
			out1.println(a);
		} catch (IOException e) {
			throw new IllegalStateException(
				"Could not record lost tuple " + filename, e);
		}
	}

	/** @deprecated Use {@link #saveLostTuple(String, String)}. */
	@Deprecated
	public void saveLostTupple(String a, String filename) {
		saveLostTuple(a, filename);
	}

	protected void processTupleArrival(SimEvent ev) {
		Tuple tuple = (Tuple) ev.getData();
		MyStatistics.getInstance().incrementTotalTupleCount();

		if (!MobileController.isApplicationActive(tuple.getAppId())) {
			return;
		}

		if (tuple.getInitialTime() == -1) {
			tuple.setInitialTime(CloudSim.clock() - getUplinkLatency());
		}

		for (MobileDevice st : getSmartThings()) {
			if (st.getId() == ev.getSource()) {
				if ((!st.isHandoffStatus() && !st.isMigStatus())) {
					break;
				}
				else {
					MyStatistics.getInstance().incrementLostTupleCount();
					saveLostTuple(String.valueOf(CloudSim.clock()), st.getId()
						+ "fdlostTuple.txt");
					if (st.isMigStatus()) {
						LogMobile.debug("FogDevice.java", st.getName() + " is in Migration");
						return;
					}
					else {
						return;
					}
				}
			}

		}
		if (getName().equals("cloud")) {
			updateCloudTraffic();
		}

		Logger.debug( getName(), "Received tuple " + tuple.getCloudletId()
			+ " with tupleType = " + tuple.getTupleType() + "\t| Source : "
			+ CloudSim.getEntityName(ev.getSource()) + "|Dest : "
			+ CloudSim.getEntityName(ev.getDestination()));
		send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

		FogDevice vmHost = tupleRoutingService.vmHost(tuple,
			MobileController.getSmartThings());
		if (vmHost != null && vmHost != this) {
			if (vmHost instanceof MobileDevice) {
				MobileDevice mobileHost = (MobileDevice) vmHost;
				FogDevice accessServer = mobileHost.getSourceServerCloudlet();
				if (accessServer == this) {
					sendDown(tuple, mobileHost.getId());
				}
				else if (accessServer != null) {
					send(accessServer.getId(), 0.0, FogEvents.TUPLE_ARRIVAL,
						tuple);
				}
				else {
					MyStatistics.getInstance().incrementLostTupleCount();
				}
			}
			else {
				send(vmHost.getId(), 0.0, FogEvents.TUPLE_ARRIVAL, tuple);
			}
			return;
		}

		if (tuple.getDirection() == Tuple.ACTUATOR) {
			sendTupleToActuator(tuple);
			return;
		}
		if (getHost().getVmList().size() > 0) {
			for (Vm vm : getHost().getVmList()) {
				final AppModule operator = (AppModule) vm;
				if (CloudSim.clock() > 0) {
					getHost().getVmScheduler().deallocatePesForVm(operator);
					getHost().getVmScheduler().allocatePesForVm(operator,
						java.util.Collections.singletonList((double) getHost().getTotalMips()));
				}

				break;
			}
		}
		if (appToModulesMap.containsKey(tuple.getAppId())) {
			if (appToModulesMap.get(tuple.getAppId()).contains(tuple.getDestModuleName())) {
				int vmId = -1;
				for (Vm vm : getHost().getVmList()) {
					if (((AppModule) vm).getName().equals(tuple.getDestModuleName()))
						vmId = vm.getId();
				}
				if (vmId < 0
					|| (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
					tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
					return;
				}
				tuple.setVmId(vmId);
				updateTimingsOnReceipt(tuple);

				executeTuple(ev, tuple.getDestModuleName());
			} else {
				if (tuple.getDestModuleName() != null) {
					if (tuple.getDirection() == Tuple.UP)
						sendUp(tuple);
					else if (tuple.getDirection() == Tuple.DOWN) {
						for (int childId : tupleRoutingService.downstreamChildren(
							tuple, getChildrenIds())) {
							sendDown(tuple, childId);
						}
					}
				}
				else {
					sendUp(tuple);
				}
			}
		} else {
			if (tuple.getDirection() == Tuple.UP)
				sendUp(tuple);
			else if (tuple.getDirection() == Tuple.DOWN) {
				for (int childId : tupleRoutingService.downstreamChildren(
					tuple, getChildrenIds())) {
					sendDown(tuple, childId);
				}

			}
		}
	}

	public void printResults(String a, String filename) {
		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true))
		{
			out1.println(a);
		} catch (IOException e) {
			throw new IllegalStateException(
				"Could not record loop result " + filename, e);
		}
	}

	protected void updateTimingsOnReceipt(Tuple tuple) {
		Application app = getApplicationMap().get(tuple.getAppId());
		if (app == null) {
			return;
		}
		String srcModule = tuple.getSrcModuleName();
		String destModule = tuple.getDestModuleName();
		List<AppLoop> loops = app.getLoops();
		for (AppLoop loop : loops) {
			if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
				TimeKeeper timeKeeper = TimeKeeper.getInstance();
				Double startTime = timeKeeper.getEmissionTime(
					tuple.getActualTupleId());
				if (startTime == null)
					break;
				if (timeKeeper.initialiseLoopTiming(loop.getLoopId())) {
					printResults(String.valueOf(0), loop.getLoopId() + "LoopId.txt");
					printResults(String.valueOf(0), loop.getLoopId() + "LoopMaxId.txt");
				}
				double delay = CloudSim.clock() - startTime;
				if (timeKeeper.recordLoopDelay(loop.getLoopId(), delay)) {
					printResults(String.valueOf(delay), loop.getLoopId() + "LoopMaxId.txt");
				}
				timeKeeper.consumeEmissionTime(tuple.getActualTupleId());
				printResults(String.valueOf(timeKeeper.getLoopIdToCurrentAverage()
					.get(loop.getLoopId())), loop.getLoopId() + "LoopId.txt");
				break;
			}
		}
	}

	protected void processSensorJoining(SimEvent ev) {
		send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);
	}

	protected void executeTuple(SimEvent ev, String operatorId) {
		// TODO Power funda
		Tuple tuple = (Tuple) ev.getData();

		if (!MobileController.isApplicationActive(tuple.getAppId())) {
			return;
		}

		Logger.debug(getName(), "Executing tuple " + tuple.getCloudletId() + " on module "
			+ operatorId);

		if (tuple.getInitialTime() != -1) {
			tuple.setFinalTime(CloudSim.clock() + getUplinkLatency());
		}

		Application app = getApplicationMap().get(tuple.getAppId());
		if (app == null) {
			return;
		}

		TimeKeeper.getInstance().tupleStartedExecution(tuple);
		updateAllocatedMips(operatorId);
		processCloudletSubmit(ev, false);
		updateAllocatedMips(operatorId);
	}

	protected void processModuleArrival(SimEvent ev) {
		AppModule module = (AppModule) ev.getData();
		String appId = module.getAppId();
		if (!appToModulesMap.containsKey(appId)) {
			appToModulesMap.put(appId, new ArrayList<String>());
		}
		appToModulesMap.get(appId).add(module.getName());
		processVmCreate(ev, false);
		if (module.isBeingInstantiated()) {
			module.setBeingInstantiated(false);
		}

		initializePeriodicTuples(module);

		module.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(module)
			.getVmScheduler()
			.getAllocatedMipsForVm(module));
	}

	private void initializePeriodicTuples(AppModule module) {
		String appId = module.getAppId();
		Application app = getApplicationMap().get(appId);
		List<AppEdge> periodicEdges = app.getPeriodicEdges(module.getName());
		for (AppEdge edge : periodicEdges) {
			send(getId(), edge.getPeriodicity(), FogEvents.SEND_PERIODIC_TUPLE, edge);
		}
	}

	protected void processOperatorRelease(SimEvent ev) {
		this.processVmMigrate(ev, false);
	}

	protected void updateNorthTupleQueue() {
		if (!northTupleQueue.isEmpty()) {
			Tuple tuple = northTupleQueue.poll();
			sendUpFreeLink(tuple);
		} else {
			setNorthLinkBusy(false);
		}
	}

	protected void sendUpFreeLink(Tuple tuple) {
		double networkDelay = tuple.getCloudletFileSize()
			/ getUplinkBandwidthForTuple(tuple);
		setNorthLinkBusy(true);
		send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
		send(parentId, networkDelay + getUplinkLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.sendingTuple(getUplinkLatency(), tuple.getCloudletFileSize());
	}

	/** Returns the effective uplink bandwidth for this tuple. */
	protected double getUplinkBandwidthForTuple(Tuple tuple) {
		return getUplinkBandwidth();
	}

	protected void sendUp(Tuple tuple) {
		if (parentId > 0) {
			if (!isNorthLinkBusy()) {
				sendUpFreeLink(tuple);
			} else {
				northTupleQueue.add(tuple);
			}
		}
	}

	protected void updateSouthTupleQueue() {
		if (!southTupleQueue.isEmpty()) {
			Pair<Tuple, Integer> pair = southTupleQueue.poll();
			sendDownFreeLink(pair.getFirst(), pair.getSecond());
		} else {
			setSouthLinkBusy(false);
		}
	}

	protected void sendDownFreeLink(Tuple tuple, int childId) {
		double networkDelay = tuple.getCloudletFileSize()
			/ getDownlinkBandwidthForTuple(tuple, childId);
		setSouthLinkBusy(true);
		double latency = getChildToLatencyMap().get(childId);
		send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
		send(childId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
	}

	/**
	 * Returns the effective downlink bandwidth for this tuple. Mobile children
	 * are additionally limited by their wireless access-point slice.
	 */
	protected double getDownlinkBandwidthForTuple(Tuple tuple, int childId) {
		Object childDevice = CloudSim.getEntity(childId);
		if (childDevice instanceof MobileDevice) {
			MobileDevice mobileDevice = (MobileDevice) childDevice;
			if (mobileDevice.getSourceAp() != null) {
				return Math.min(getDownlinkBandwidth(),
					NetworkSlicing.getAccessPointDownlinkBandwidth(
						mobileDevice.getSourceAp(), mobileDevice));
			}
		}
		return getDownlinkBandwidth();
	}

	protected void sendDown(Tuple tuple, int childId) {
		if (getChildrenIds().contains(childId)) {
			Object childDevice = CloudSim.getEntity(childId);
			if (childDevice instanceof MobileDevice) {
				MobileDevice mobileDevice = (MobileDevice) childDevice;
				if (mobileDevice.getSourceAp() != null
					&& mobileDevice.getSourceServerCloudlet() == this
					&& mobileDevice.getSourceAp().getSmartThings()
						.contains(mobileDevice)) {
					WirelessAssociation association =
						mobileDevice.getWirelessAssociation();
					if (association == null) {
						throw new IllegalStateException(
							"Wireless child has no propagation latency: " + childId);
					}
					NetworkSlicing.startWirelessTupleTransfer(
						mobileDevice.getSourceAp(), mobileDevice,
						NetworkSlicing.WirelessDirection.DOWNLINK, tuple,
						EntityId.of(getId()), EntityId.of(childId),
						association.getPropagationDelay());
					return;
				}
			}
			if (!isSouthLinkBusy()) {
				sendDownFreeLink(tuple, childId);
			} else {
				southTupleQueue.add(new Pair<Tuple, Integer>(tuple, childId));
			}
		}
	}

	protected void completeWirelessTupleTransfer(SimEvent event) {
		Object payload = event.getData();
		if (!(payload instanceof NetworkSlicing.WirelessTransferCompletion)) {
			return;
		}
		NetworkSlicing.WirelessTransferResult result =
			NetworkSlicing.completeWirelessTransfer(
				(NetworkSlicing.WirelessTransferCompletion) payload);
		if (result != null) {
			send(result.getDestination().intValue(),
				result.getPropagationDelay().toMilliseconds(), FogEvents.TUPLE_ARRIVAL,
				result.getTuple());
		}
	}

	protected void sendToSelf(Tuple tuple) {
		send(getId(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ARRIVAL, tuple);
	}

	public PowerHost getHost() {
		return (PowerHost) getHostList().get(0);
	}

	public int getParentId() {
		return parentId;
	}

	public void setParentId(int parentId) {
		this.parentId = parentId;
	}

	public List<Integer> getChildrenIds() {
		return Collections.unmodifiableList(childrenIds);
	}

	public void setChildrenIds(List<Integer> childrenIds) {
		if (childrenIds == null) {
			throw new IllegalArgumentException("Child ID list cannot be null");
		}
		this.childrenIds = new ArrayList<Integer>(childrenIds);
	}

	/** Adds or updates a child and its latency as one state transition. */
	public void attachChild(int childId, double latency) {
		if (childId == getId()) {
			throw new IllegalArgumentException("A fog device cannot be its own child");
		}
		if (!Double.isFinite(latency) || latency < 0.0) {
			throw new IllegalArgumentException(
				"Child latency must be finite and non-negative");
		}
		addChildRecord(childId);
		childToLatencyMap.put(childId, latency);
	}

	/** Removes all state associated with a child. */
	public boolean detachChild(int childId) {
		boolean removed = childrenIds.remove(Integer.valueOf(childId));
		childToOperatorsMap.remove(childId);
		childToLatencyMap.remove(childId);
		return removed;
	}

	private void addChildRecord(int childId) {
		if (!childrenIds.contains(childId) && childId != getId()) {
			childrenIds.add(childId);
		}
		if (!childToOperatorsMap.containsKey(childId)) {
			childToOperatorsMap.put(childId, new ArrayList<String>());
		}
	}

	public double getUplinkBandwidth() {
		return uplinkBandwidth;
	}

	public void setUplinkBandwidth(double uplinkBandwidth) {
		this.uplinkBandwidth = uplinkBandwidth;
	}

	public double getUplinkLatency() {
		return uplinkLatency;
	}

	public void setUplinkLatency(double uplinkLatency) {
		this.uplinkLatency = uplinkLatency;
	}

	public boolean isSouthLinkBusy() {
		return isSouthLinkBusy;
	}

	public boolean isNorthLinkBusy() {
		return isNorthLinkBusy;
	}

	public void setSouthLinkBusy(boolean isSouthLinkBusy) {
		this.isSouthLinkBusy = isSouthLinkBusy;
	}

	public void setNorthLinkBusy(boolean isNorthLinkBusy) {
		this.isNorthLinkBusy = isNorthLinkBusy;
	}

	public int getControllerId() {
		return controllerId;
	}

	public void setControllerId(int controllerId) {
		this.controllerId = controllerId;
	}

	public List<String> getActiveApplications() {
		return Collections.unmodifiableList(activeApplications);
	}

	public void setActiveApplications(List<String> activeApplications) {
		if (activeApplications == null) {
			throw new IllegalArgumentException(
				"Active application list cannot be null");
		}
		this.activeApplications = new ArrayList<String>(activeApplications);
	}

	public boolean activateApplication(String applicationId) {
		if (applicationId == null || applicationId.trim().isEmpty()) {
			throw new IllegalArgumentException("Application ID cannot be empty");
		}
		if (activeApplications.contains(applicationId)) {
			return false;
		}
		return activeApplications.add(applicationId);
	}

	public List<MobilitySample> getMobilityPath() {
		return Collections.unmodifiableList(mobilityPath);
	}

	public void setMobilityPath(List<MobilitySample> mobilityPath) {
		MobilityTimeline.validateOrdered(mobilityPath);
		this.mobilityPath = new ArrayList<MobilitySample>(mobilityPath);
	}

	/** @deprecated Use {@link #getMobilityPath()} for typed mobility samples. */
	@Deprecated
	public ArrayList<String[]> getPath() {
		ArrayList<String[]> rows = new ArrayList<String[]>(mobilityPath.size());
		for (MobilitySample sample : mobilityPath) {
			rows.add(sample.toColumns());
		}
		return rows;
	}

	/** @deprecated Use {@link #setMobilityPath(List)} for typed mobility samples. */
	@Deprecated
	public void setPath(ArrayList<String[]> path) {
		if (path == null) {
			throw new IllegalArgumentException("Mobility path cannot be null");
		}
		List<MobilitySample> samples = new ArrayList<MobilitySample>(path.size());
		for (String[] row : path) {
			samples.add(MobilitySample.fromColumns(row));
		}
		setMobilityPath(samples);
	}

	public Map<Integer, List<String>> getChildToOperatorsMap() {
		Map<Integer, List<String>> snapshot =
			new HashMap<Integer, List<String>>();
		for (Map.Entry<Integer, List<String>> entry
			: childToOperatorsMap.entrySet()) {
			snapshot.put(entry.getKey(), Collections.unmodifiableList(
				new ArrayList<String>(entry.getValue())));
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public void setChildToOperatorsMap(Map<Integer, List<String>> childToOperatorsMap) {
		if (childToOperatorsMap == null) {
			throw new IllegalArgumentException(
				"Child operator map cannot be null");
		}
		this.childToOperatorsMap = new HashMap<Integer, List<String>>();
		for (Map.Entry<Integer, List<String>> entry
			: childToOperatorsMap.entrySet()) {
			this.childToOperatorsMap.put(entry.getKey(),
				new ArrayList<String>(entry.getValue()));
		}
	}

	public Map<String, Application> getApplicationMap() {
		return Collections.unmodifiableMap(applicationMap);
	}

	public void setApplicationMap(Map<String, Application> applicationMap) {
		if (applicationMap == null) {
			throw new IllegalArgumentException("Application map cannot be null");
		}
		this.applicationMap =
			new HashMap<String, Application>(applicationMap);
	}

	public void installApplication(Application application) {
		if (application == null || application.getAppId() == null
			|| application.getAppId().trim().isEmpty()) {
			throw new IllegalArgumentException("Application and its ID are required");
		}
		applicationMap.put(application.getAppId(), application);
	}

	public Application removeApplication(String applicationId) {
		Application removed = applicationMap.remove(applicationId);
		activeApplications.remove(applicationId);
		appToModulesMap.remove(applicationId);
		moduleInstanceCount.remove(applicationId);
		return removed;
	}

	public Queue<Tuple> getNorthTupleQueue() {
		return new LinkedList<Tuple>(northTupleQueue);
	}

	public void setNorthTupleQueue(Queue<Tuple> northTupleQueue) {
		if (northTupleQueue == null) {
			throw new IllegalArgumentException("North tuple queue cannot be null");
		}
		this.northTupleQueue = new LinkedList<Tuple>(northTupleQueue);
	}

	public Queue<Pair<Tuple, Integer>> getSouthTupleQueue() {
		return new LinkedList<Pair<Tuple, Integer>>(southTupleQueue);
	}

	public void setSouthTupleQueue(Queue<Pair<Tuple, Integer>> southTupleQueue) {
		if (southTupleQueue == null) {
			throw new IllegalArgumentException("South tuple queue cannot be null");
		}
		this.southTupleQueue =
			new LinkedList<Pair<Tuple, Integer>>(southTupleQueue);
	}

	public double getDownlinkBandwidth() {
		return downlinkBandwidth;
	}

	public void setDownlinkBandwidth(double downlinkBandwidth) {
		this.downlinkBandwidth = downlinkBandwidth;
	}

	public List<Pair<Integer, Double>> getAssociatedActuatorIds() {
		return Collections.unmodifiableList(associatedActuatorIds);
	}

	public void setAssociatedActuatorIds(List<Pair<Integer, Double>> associatedActuatorIds) {
		if (associatedActuatorIds == null) {
			throw new IllegalArgumentException(
				"Associated actuator list cannot be null");
		}
		this.associatedActuatorIds =
			new ArrayList<Pair<Integer, Double>>(associatedActuatorIds);
	}

	public void associateActuator(int actuatorId, double delay) {
		if (!Double.isFinite(delay) || delay < 0.0) {
			throw new IllegalArgumentException(
				"Actuator delay must be finite and non-negative");
		}
		associatedActuatorIds.add(
			new Pair<Integer, Double>(actuatorId, delay));
	}

	public double getEnergyConsumption() {
		return energyConsumption;
	}

	public void setEnergyConsumption(double energyConsumption) {
		this.energyConsumption = energyConsumption;
	}

	public Map<Integer, Double> getChildToLatencyMap() {
		return Collections.unmodifiableMap(childToLatencyMap);
	}

	public void setChildToLatencyMap(Map<Integer, Double> childToLatencyMap) {
		if (childToLatencyMap == null) {
			throw new IllegalArgumentException("Child latency map cannot be null");
		}
		this.childToLatencyMap =
			new HashMap<Integer, Double>(childToLatencyMap);
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public double getRatePerMips() {
		return ratePerMips;
	}

	public void setRatePerMips(double ratePerMips) {
		this.ratePerMips = ratePerMips;
	}

	public double getTotalCost() {
		return totalCost;
	}

	public void setTotalCost(double totalCost) {
		this.totalCost = totalCost;
	}

	public Map<String, Map<String, Integer>> getModuleInstanceCount() {
		Map<String, Map<String, Integer>> snapshot =
			new HashMap<String, Map<String, Integer>>();
		for (Map.Entry<String, Map<String, Integer>> entry
			: moduleInstanceCount.entrySet()) {
			snapshot.put(entry.getKey(), Collections.unmodifiableMap(
				new HashMap<String, Integer>(entry.getValue())));
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public void setModuleInstanceCount(
		Map<String, Map<String, Integer>> moduleInstanceCount) {
		if (moduleInstanceCount == null) {
			throw new IllegalArgumentException(
				"Module instance count cannot be null");
		}
		this.moduleInstanceCount =
			new HashMap<String, Map<String, Integer>>();
		for (Map.Entry<String, Map<String, Integer>> entry
			: moduleInstanceCount.entrySet()) {
			this.moduleInstanceCount.put(entry.getKey(),
				new HashMap<String, Integer>(entry.getValue()));
		}
	}

	public DecisionMigration getMigrationStrategy() {
		return migrationStrategy;
	}

	public void setMigrationStrategy(DecisionMigration migrationStrategy) {
		this.migrationStrategy = migrationStrategy;
	}

	public int getPolicyReplicaVM() {
		return policyReplicaVM.legacyValue();
	}

	public void setPolicyReplicaVM(int policyReplicaVM) {
		setMigrationTechniquePolicy(
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM));
	}

	public MigrationTechniquePolicy getMigrationTechniquePolicy() {
		return policyReplicaVM;
	}

	public void setMigrationTechniquePolicy(
		MigrationTechniquePolicy policyReplicaVM) {
		if (policyReplicaVM == null) {
			throw new IllegalArgumentException("Migration technique cannot be null");
		}
		this.policyReplicaVM = policyReplicaVM;
	}

	public Set<FogDevice> getServerCloudlets() {
		return Collections.unmodifiableSet(serverCloudlets);
	}

	public boolean attachServerCloudlet(FogDevice serverCloudlet) {
		if (serverCloudlet == null || serverCloudlet == this) {
			throw new IllegalArgumentException(
				"Server cloudlet must be a different device");
		}
		return serverCloudlets.add(serverCloudlet);
	}

	public boolean detachServerCloudlet(FogDevice serverCloudlet) {
		return serverCloudlet != null && serverCloudlets.remove(serverCloudlet);
	}

	public void setServerCloudlets(FogDevice sc, int action) {// myiFogSim
		setServerCloudlets(sc, MembershipAction.fromLegacy(action));
	}

	public void setServerCloudlets(FogDevice sc, MembershipAction action) {
		if (action == null) {
			throw new IllegalArgumentException("Membership action cannot be null");
		}
		if (action == MembershipAction.ADD) {
			attachServerCloudlet(sc);
		}
		else {
			detachServerCloudlet(sc);
		}
	}

	public FogDevice getServerCloudletToVmMigrate() {
		return serverCloudletToVmMigrate;
	}

	public void setServerCloudletToVmMigrate(FogDevice serverCloudletToVmMigrate) {
		this.serverCloudletToVmMigrate = serverCloudletToVmMigrate;
	}

	public Set<MobileDevice> getSmartThingsWithVm() {
		return Collections.unmodifiableSet(smartThingsWithVm);
	}

	public boolean registerHostedMobileVm(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		return smartThingsWithVm.add(mobileDevice);
	}

	public boolean unregisterHostedMobileVm(MobileDevice mobileDevice) {
		return mobileDevice != null && smartThingsWithVm.remove(mobileDevice);
	}

	public void setSmartThingsWithVm(MobileDevice st, int action) {// myiFogSim
		setSmartThingsWithVm(st, MembershipAction.fromLegacy(action));
	}

	public void setSmartThingsWithVm(MobileDevice st,
		MembershipAction action) {
		if (action == null) {
			throw new IllegalArgumentException("Membership action cannot be null");
		}
		if (action == MembershipAction.ADD) {
			registerHostedMobileVm(st);
		}
		else {
			unregisterHostedMobileVm(st);
		}
	}

	public int getVolatileParentId() {
		return volatilParentId;
	}

	public void setVolatileParentId(int volatileParentId) {
		this.volatilParentId = volatileParentId;
	}

	/** @deprecated Use {@link #getVolatileParentId()}. */
	@Deprecated
	public int getVolatilParentId() {
		return getVolatileParentId();
	}

	/** @deprecated Use {@link #setVolatileParentId(int)}. */
	@Deprecated
	public void setVolatilParentId(int volatileParentId) {
		setVolatileParentId(volatileParentId);
	}

	public BeforeMigration getBeforeMigrate() {
		return beforeMigration;
	}

	public void setBeforeMigrate(BeforeMigration beforeMigration) {
		this.beforeMigration = beforeMigration;
	}
}
