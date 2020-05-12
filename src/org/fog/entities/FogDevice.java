package org.fog.entities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
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
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.localization.Coordinate;// myiFogSim
import org.fog.localization.Distances;
import org.fog.placement.MobileController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.BeforeMigration;
import org.fog.vmmigration.CompleteVM;
import org.fog.vmmigration.ContainerVM;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LiveMigration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.Service;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;

public class FogDevice extends PowerDatacenter {
	protected Queue<Tuple> northTupleQueue;
	protected Queue<Pair<Tuple, Integer>> southTupleQueue;

	protected List<String> activeApplications;
	protected ArrayList<String[]> path;

	protected Map<String, Application> applicationMap;
	protected Map<String, List<String>> appToModulesMap;
	protected Map<Integer, Double> childToLatencyMap;

	protected Map<Integer, Integer> cloudTrafficMap;

	protected double lockTime;

	/**
	 * ID of the parent Fog Device
	 */
	protected int parentId;
	protected int volatilParentId;

	/**
	 * ID of the Controller
	 */
	protected int controllerId;
	/**
	 * IDs of the children Fog devices
	 */
	protected List<Integer> childrenIds;

	protected Map<Integer, List<String>> childToOperatorsMap;

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
	protected List<Pair<Integer, Double>> associatedActuatorIds;

	protected double energyConsumption;
	protected double lastUtilizationUpdateTime;
	protected double lastUtilization;
	private int level;

	protected double ratePerMips;

	protected double totalCost;

	protected Map<String, Map<String, Integer>> moduleInstanceCount;

	protected Coordinate coord;
	protected Set<ApDevice> apDevices;
	protected Set<MobileDevice> smartThings;
	protected Set<MobileDevice> smartThingsWithVm;
	protected Set<FogDevice> serverCloudlets;
	protected boolean available;
	protected Service service;
	private HashMap<FogDevice, Double> netServerCloudlets;
	protected DecisionMigration migrationStrategy;
	protected int policyReplicaVM;
	private FogDevice serverCloudletToVmMigrate;
	protected BeforeMigration beforeMigration;
	protected int startTravelTime;
	protected int travelTimeId;
	protected int travelPredicTime;
	protected int mobilityPrecitionError;

	protected int myId;

	public int getMyId() {
		return myId;
	}

	public void setMyId(int myId) {
		this.myId = myId;
	}

	public HashMap<FogDevice, Double> getNetServerCloudlets() {
		return netServerCloudlets;
	}

	public void setNetServerCloudlets(HashMap<FogDevice, Double> netServerCloudlets) {
		this.netServerCloudlets = netServerCloudlets;
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
		return smartThings;
	}

	public void setSmartThings(MobileDevice st, int action) {
		if (action == Policies.ADD) {
			this.smartThings.add(st);
		}
		else {
			this.smartThings.remove(st);
		}
	}

	public Set<ApDevice> getApDevices() {
		return apDevices;
	}

	public void setApDevices(ApDevice ap, int action) {
		if (action == Policies.ADD) {
			this.apDevices.add(ap);
		}
		else {
			this.apDevices.remove(ap);
		}
	}

	public Coordinate getCoord() {
		return coord;
	}

	public void setCoord(int coordX, int coordY) {
		this.coord.setCoordX(coordX);
		this.coord.setCoordY(coordY);
	}

	public int getStartTravelTime() {
		return startTravelTime;
	}

	public void setStartTravelTime(int startTravelTime) {
		this.startTravelTime = startTravelTime;
	}

	public int getTravelTimeId() {
		return travelTimeId;
	}

	public void setTravelTimeId(int travelTimeId) {
		this.travelTimeId = travelTimeId;
	}

	public int getTravelPredicTime() {
		return travelPredicTime;
	}

	public void setTravelPredicTime(int travelPredicTime) {
		this.travelPredicTime = travelPredicTime;
	}

	public int getMobilityPrecitionError() {
		return mobilityPrecitionError;
	}

	public void setMobilityPredictionError(int mobilityPrecitionError) {
		this.mobilityPrecitionError = mobilityPrecitionError;
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
		netServerCloudlets = new HashMap<>();
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

		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

		this.coord = new Coordinate();
		this.setCoord(coordX, coordY);
		this.setMyId(id);
		smartThings = new HashSet<>();
		smartThingsWithVm = new HashSet<>();
		apDevices = new HashSet<>();
		netServerCloudlets = new HashMap<>();
		setVolatilParentId(-1);
		this.setAvailable(true);
		this.setService(service);

		setBeforeMigrate(beforeMigration);
		setPolicyReplicaVM(policyReplicaVM);
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
		setPath(new ArrayList<String[]>());
		setTravelTimeId(-1);
		setTravelPredicTime(0);
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
		netServerCloudlets = new HashMap<>();
		setVolatilParentId(-1);

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
		setPath(new ArrayList<String[]>());
		setTravelTimeId(-1);
		setTravelPredicTime(0);
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
		setPath(new ArrayList<String[]>());
		setTravelTimeId(-1);
		setTravelPredicTime(0);
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
		setPath(new ArrayList<String[]>());
		setTravelTimeId(-1);
		setTravelPredicTime(0);
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
		case MobileEvents.ABORT_MIGRATION:
			invokeAbortMigration(ev);
			break;
		case MobileEvents.REMOVE_VM_OLD_CLOUDLET:
			removeVmOldServerCloudlet(ev);
			break;
		case MobileEvents.ADD_VM_NEW_CLOUDLET:
			addVmNewServerCloudlet(ev);
			break;
		case MobileEvents.DELIVERY_VM:
			deliveryVM(ev);
			break;
		case MobileEvents.CONNECT_ST_TO_SC:
			connectServerCloudletSmartThing(ev);
			break;
		case MobileEvents.DESCONNECT_ST_TO_SC:
			desconnectServerCloudletSmartThing(ev);
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
		// TODO Auto-generated method stub

		MobileDevice smartThing = (MobileDevice) ev.getData();
		System.out.println("local " + smartThing.getVmLocalServerCloudlet().getName() + " "
			+ smartThing.getVmLocalServerCloudlet().getActiveApplications() +
			" apps "
			+ smartThing.getVmLocalServerCloudlet().getApplicationMap().values().toString());
		System.out.println("dest: " + smartThing.getDestinationServerCloudlet().getName() + " "
			+ smartThing.getDestinationServerCloudlet().getActiveApplications() +
			" apps "
			+ smartThing.getDestinationServerCloudlet().getApplicationMap().values().toString());
		System.out.println("smartthing id: " + smartThing.getMyId());
		smartThing.getVmLocalServerCloudlet().applicationMap.values();
		Application app = smartThing.getVmLocalServerCloudlet().applicationMap.get("MyApp_vr_game"
			+ smartThing.getMyId());
		if (app == null) {
			System.out.println("Clock: " + CloudSim.clock() + " - FogDevice.java - App == Null");
			System.exit(0);
		}
		getApplicationMap().put(app.getAppId(), app);

		if (smartThing.getVmLocalServerCloudlet().getApplicationMap().remove(app.getAppId()) == null) {
			System.out.println("FogDevice.java - applicationMap did not remove. return == null");
			System.exit(0);
		}

		MobileController mobileController = (MobileController) CloudSim
			.getEntity("MobileController");

		mobileController.getModuleMapping().addModuleToDevice(
			((AppModule) smartThing.getVmMobileDevice()).getName(), getName(), 1);
		System.out.println("Antes de entrar no submitApplicationMigration - " + getName());
		mobileController.getModuleMapping().getModuleMapping()
			.remove(smartThing.getVmLocalServerCloudlet().getName());
		if (!mobileController.getModuleMapping().getModuleMapping().containsKey(getName())) {
			mobileController.getModuleMapping().getModuleMapping()
				.put(getName(), new HashMap<String, Integer>());
			mobileController.getModuleMapping().getModuleMapping().get(getName())
				.put("AppModuleVm_" + smartThing.getName(), 1);
		}
		mobileController.submitApplicationMigration(smartThing, app, 1);

		sendNow(mobileController.getId(), MobileEvents.APP_SUBMIT_MIGRATE, app);
	}

	private void unLockedMigration(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		smartThing.setLockedToMigration(false);
		smartThing.setTimeFinishDeliveryVm(-1);
		LogMobile.debug("FogDevice.java", smartThing.getName() + " had the migration unlocked");
	}

	private void desconnectServerCloudletSmartThing(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		desconnectServerCloudletSmartThing(smartThing);
		MyStatistics.getInstance().startWithoutConnetion(smartThing.getMyId(), CloudSim.clock());
	}

	private void connectServerCloudletSmartThing(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		connectServerCloudletSmartThing(smartThing);
		MyStatistics.getInstance().finalWithoutConnection(smartThing.getMyId(), CloudSim.clock());

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
				MyStatistics.getInstance().getInitialWithoutVmTime().remove(smartThing.getMyId());
			}
			LogMobile.debug("FogDevice.java", smartThing.getName()
				+ " had migStatus to false - connectServerCloudlet");
			MyStatistics.getInstance().startDelayAfterNewConnection(smartThing.getMyId(), 0.0);
			MyStatistics.getInstance().finalDelayAfterNewConnection(smartThing.getMyId(),
				getCharacteristics().getCpuTime( smartThing.getVmMobileDevice().getSize() * 1024 * 1024 * 8, 0.0));
		}
		if (!smartThing.getSourceServerCloudlet().equals(smartThing.getVmLocalServerCloudlet())) {
			smartThing.getSourceServerCloudlet().desconnectServerCloudletSmartThing(smartThing);
			smartThing.getVmLocalServerCloudlet().connectServerCloudletSmartThing(smartThing);
			MyStatistics.getInstance().setMyCountLowestLatency(1);
		}
	}

	private void addVmNewServerCloudlet(SimEvent ev) {
	}

	private void removeVmOldServerCloudlet(SimEvent ev) {
	}

	private void invokeAbortMigration(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		System.out.println("*_*_*_*_*_*_*_*_*_*_*_*_*_ABORT MIGRATION -> beforeMigration*_*_*_*_*_*_*_*_*_*_*_*: "
				+ smartThing.getName());
		MyStatistics.getInstance().getInitialWithoutVmTime().remove(smartThing.getMyId());
		MyStatistics.getInstance().getInitialTimeDelayAfterNewConnection() .remove(smartThing.getMyId());
		MyStatistics.getInstance().getInitialTimeWithoutConnection().remove(smartThing.getMyId());
		smartThing.setMigStatus(false);
		smartThing.setPostCopyStatus(false);
		smartThing.setMigStatusLive(false);
		smartThing.setLockedToMigration(false);
		smartThing.setTimeFinishDeliveryVm(-1.0);
		smartThing.setAbortMigration(true);
		smartThing.setDestinationServerCloudlet(smartThing.getVmLocalServerCloudlet());
	}

	public boolean connectServerCloudletSmartThing(MobileDevice st) {

		st.setSourceServerCloudlet(this);

		setSmartThings(st, Policies.ADD);
		st.setParentId(getId());
		double latency = st.getUplinkLatency();

		getChildToLatencyMap().put(st.getId(), latency);
		addChild(st.getId());
		setUplinkLatency(getUplinkLatency() + 0.123812950236);//
		LogMobile.debug("FogDevice.java", st.getName() + " was connected to " + getName());

		return true;
	}

	public boolean desconnectServerCloudletSmartThing(MobileDevice st) {
		setSmartThings(st, Policies.REMOVE); // it'll remove the smartThing from serverCloudlets-smartThing's set
		st.setSourceServerCloudlet(null);
		// NetworkTopology.addLink(this.getId(), st.getId(), 0.0, 0.0);
		setUplinkLatency(getUplinkLatency() - 0.123812950236);
		removeChild(st.getId());
		LogMobile.debug("FogDevice.java", st.getName() + " was desconnected to " + getName());
		return true;

	}

	private void invokeStartMigration(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();

		// the smartThing is outside of the map
		if (MobileController.getSmartThings().contains(smartThing)) {
			if (!smartThing.isAbortMigration()) {
				// the smartThing isn't connected in any ap right now
				if (smartThing.getSourceAp() != null) {
					int srcId = getId();
					int entityId = smartThing.getDestinationServerCloudlet().getId();
					Double delay = 1.0;
					if (entityId != srcId) {// does not delay self messages
						delay += getNetworkDelay(srcId, entityId);
					}
					send(smartThing.getVmLocalServerCloudlet().getId(), delay,
						MobileEvents.DELIVERY_VM, smartThing);
					LogMobile.debug("FogDevice.java", smartThing.getName()
						+ " was scheduled the DELIVERY_VM  from " +
						smartThing.getVmLocalServerCloudlet().getName() + " to "
						+ smartThing.getDestinationServerCloudlet().getName());
					System.out.println("FogDevice.java" + smartThing.getName()
						+ " was scheduled the DELIVERY_VM  from " +
						smartThing.getVmLocalServerCloudlet().getName() + " to "
						+ smartThing.getDestinationServerCloudlet().getName() + " in "
						+ CloudSim.clock() + " with delay " + delay);

					sendNow(smartThing.getDestinationServerCloudlet().getId(),
						MobileEvents.VM_MIGRATE, smartThing);
					Map<String, Object> ma;
					ma = new HashMap<String, Object>();

					if (smartThing.getVmMobileDevice() == null) {
						System.out.println(smartThing.getName() + " has a null VM");
					}
					ma.put("vm", smartThing.getVmMobileDevice());
					ma.put("host", smartThing.getDestinationServerCloudlet().getHost());
					if (ma.size() < 2) {
						sendNow(getId(), MobileEvents.ABORT_MIGRATION, smartThing);
						System.out.println("FogDevice.java ma.size()<2");
						System.exit(0);
					}
					else {
						sendNow(smartThing.getVmLocalServerCloudlet().getId(),
							CloudSimTags.VM_MIGRATE, ma);
						LogMobile.debug("FogDevice.java",
							"CloudSim.VM_MIGRATE was scheduled  to VM#: "
								+ smartThing.getVmMobileDevice().getId() + " HOST#: " +
								smartThing.getDestinationServerCloudlet().getHost().getId());
						System.out.println("FogDevice.java"
							+ " CloudSim.VM_MIGRATE was scheduled  to VM#: "
							+ smartThing.getVmMobileDevice().getId() + " HOST#: " +
							smartThing.getDestinationServerCloudlet().getHost().getId());
					}
				}
				else {
					sendNow(smartThing.getVmLocalServerCloudlet().getId(),
						MobileEvents.ABORT_MIGRATION, smartThing);
				}
			}
			else {
				smartThing.setAbortMigration(false);
			}
		}
		else {
			LogMobile.debug("FogDevice.java", smartThing.getName()
				+ " was excluded from List of SmartThings!");
		}
	}

	private void deliveryVM(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		if (MobileController.getSmartThings().contains(smartThing)) {

			LogMobile.debug("FogDevice.java", "DELIVERY VM: " + smartThing.getName() + " (id: "
				+ smartThing.getId() + ") from " + smartThing.getVmLocalServerCloudlet().getName()
				+ " to " + smartThing.getDestinationServerCloudlet().getName());

			smartThing.getVmLocalServerCloudlet().setSmartThingsWithVm(smartThing, Policies.REMOVE);

			smartThing.setVmLocalServerCloudlet(smartThing.getDestinationServerCloudlet());
			smartThing.setDestinationServerCloudlet(null);

			smartThing.getVmLocalServerCloudlet().setSmartThingsWithVm(smartThing, Policies.ADD);

			if (MyStatistics.getInstance().getInitialTimeDelayAfterNewConnection()
				.containsKey(smartThing.getMyId())) {
				smartThing.setMigStatus(false);
				smartThing.setPostCopyStatus(false);
				smartThing.setMigStatusLive(false);
				if (MyStatistics.getInstance().getInitialWithoutVmTime().get(smartThing.getMyId()) != null) {
					MyStatistics.getInstance().finalWithoutVmTime(smartThing.getMyId(), CloudSim.clock());
					System.out.println("finalWithoutVmTime: " + CloudSim.clock());
					MyStatistics.getInstance().getInitialWithoutVmTime() .remove(smartThing.getMyId());
				}
				LogMobile.debug("FogDevice.java", smartThing.getName()
					+ " had migStatus to false - deliveryVM");
				// handoff has been occurred first than delivery
				MyStatistics.getInstance().finalDelayAfterNewConnection(smartThing.getMyId(), CloudSim.clock()
						+ getCharacteristics().getCpuTime(smartThing.getVmMobileDevice().getSize() * 1024 * 1024 * 8, 0.0));
				if (smartThing.getSourceServerCloudlet() == null) {
					smartThing.setSourceServerCloudlet(smartThing.getVmLocalServerCloudlet());
					System.out.println("CRASH " + smartThing.getMyId() + "\t source c "
						+ smartThing.getSourceServerCloudlet()
						+ "\t local server " + smartThing.getVmLocalServerCloudlet());
				}
				if (!smartThing.getSourceServerCloudlet().equals(
					smartThing.getVmLocalServerCloudlet())) {
					smartThing.getSourceServerCloudlet().desconnectServerCloudletSmartThing(
						smartThing);
					smartThing.getVmLocalServerCloudlet().connectServerCloudletSmartThing(
						smartThing);
				}
			}

			float migrationLocked = (smartThing.getVmMobileDevice().getSize() * (smartThing
				.getSpeed() + 1)) + 20000;
			if (migrationLocked < smartThing.getTravelPredicTime() * 1000) {
				migrationLocked = smartThing.getTravelPredicTime() * 1000;
			}
			send(smartThing.getVmLocalServerCloudlet().getId(), migrationLocked,
				MobileEvents.UNLOCKED_MIGRATION, smartThing);
			MyStatistics.getInstance().countMigration();
			MyStatistics.getInstance().historyMigrationTime(smartThing.getMyId(),
				smartThing.getMigTime());
			if (smartThing.getMigrationTechnique() instanceof CompleteVM) {
				MyStatistics.getInstance().historyDowntime(smartThing.getMyId(),
					smartThing.getMigTime());
			}
			else if (smartThing.getMigrationTechnique() instanceof ContainerVM) {
				MyStatistics.getInstance().historyDowntime(smartThing.getMyId(),
					smartThing.getMigTime());
			}
			else if (smartThing.getMigrationTechnique() instanceof LiveMigration) {
				MyStatistics.getInstance().historyDowntime(smartThing.getMyId(),
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
		MobileDevice smartThing = (MobileDevice) ev.getData();

		if (smartThing.isLockedToMigration()) {// isMigStatus()){
			LogMobile.debug("FogDevice.java", "NO MIGRATE: " + smartThing.getName()
				+ " already is in migration Process or the migration is locked");
		}
		else {
			LogMobile.debug("FogDevice.java", "NO MIGRATE: " + smartThing.getName()
				+ " is not in Migrate");
		}
	}

	private void invokeBeforeMigration(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		if (MobileController.getSmartThings().contains(smartThing)) {
			// the smartThing isn't connected in any ap right now
			if (smartThing.getSourceAp() != null && !smartThing.isMigStatus()) {
				double delayProcess = getBeforeMigrate().dataprepare(smartThing);
				System.out.println("delayProcess" + delayProcess);
				if (delayProcess >= 0) {
					if (getPolicyReplicaVM() == Policies.LIVE_MIGRATION) {
						smartThing.setPostCopyStatus(true);
						smartThing.setTimeStartLiveMigration(CloudSim.clock());
					}
					else {
						smartThing.setMigStatus(true);
						MyStatistics.getInstance().startWithoutVmTime(smartThing.getMyId(),
							CloudSim.clock());
						smartThing.setTimeFinishDeliveryVm(-1.0);
						// It'll happen according the Migration Time
						send(smartThing.getVmLocalServerCloudlet().getId(), smartThing.getMigTime()
							+ delayProcess, MobileEvents.START_MIGRATION, smartThing);
					}
					smartThing.setLockedToMigration(true);
				}
			}
			else {
				sendNow(smartThing.getVmLocalServerCloudlet().getId(), MobileEvents.NO_MIGRATION,
					smartThing);
				MyStatistics.getInstance().getInitialWithoutVmTime().remove(smartThing.getMyId());
				MyStatistics.getInstance().getInitialTimeDelayAfterNewConnection()
					.remove(smartThing.getMyId());
				MyStatistics.getInstance().getInitialTimeWithoutConnection()
					.remove(smartThing.getMyId());
				smartThing.setMigStatus(false);
				smartThing.setPostCopyStatus(false);
				smartThing.setMigStatusLive(false);
				smartThing.setLockedToMigration(false);
				smartThing.setTimeFinishDeliveryVm(-1.0);
				smartThing.setAbortMigration(true);
			}
		}
		else {
			sendNow(smartThing.getVmLocalServerCloudlet().getId(), MobileEvents.ABORT_MIGRATION,
				smartThing);
		}
	}

	private void migStatusToLiveMigration(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		sendNow(smartThing.getVmLocalServerCloudlet().getId(),
			MobileEvents.START_MIGRATION, smartThing);// It'll happen according the Migration Time
	}

	private void invokeDecisionMigration(SimEvent ev) {
		for (MobileDevice st : getSmartThings()) {
			//Only the connected smartThings
			if (st.getSourceAp() != null && (!st.isLockedToMigration())) {
				if (st.getVmLocalServerCloudlet().getMigrationStrategy().shouldMigrate(st)) {
					if (!st.getVmLocalServerCloudlet().equals(st.getDestinationServerCloudlet())) {
						System.out.println("====================ToMigrate================== "
							+ st.getName() + " " + st.getId());
						LogMobile.debug("FogDevice.java", "Distance between " + st.getName()
							+ " and " + st.getSourceAp().getName() + ": " +
							Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord()));
						System.out.println("Migration time: " + st.getMigTime());
						LogMobile.debug("FogDevice.java",
							"Made the decisionMigration for " + st.getName());
						LogMobile.debug("FogDevice.java", "from "
							+ st.getVmLocalServerCloudlet().getName() + " to "
							+ st.getDestinationServerCloudlet().getName() +
							" -> Connected by: " + st.getSourceServerCloudlet().getName());
						sendNow(st.getVmLocalServerCloudlet().getId(), MobileEvents.TO_MIGRATION, st);
						MyStatistics.getInstance().getInitialWithoutVmTime().remove(st.getMyId());
						MyStatistics.getInstance().getInitialTimeDelayAfterNewConnection()
							.remove(st.getMyId());
						MyStatistics.getInstance().getInitialTimeWithoutConnection()
							.remove(st.getMyId());
						st.setLockedToMigration(true);
						st.setTimeFinishDeliveryVm(-1.0);
						saveMigration(st);
					}
					else {
						sendNow(getId(), MobileEvents.NO_MIGRATION, st);
					}
				}
				else {
					sendNow(getId(), MobileEvents.NO_MIGRATION, st);
				}
			}
			else {
				sendNow(getId(), MobileEvents.NO_MIGRATION, st);

			}
		}
	}

	private static void saveMigration(MobileDevice st) {
		System.out.println("MIGRATION " + st.getMyId() + " Position: " + st.getCoord().getCoordX()
			+ ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: "
			+ st.getSpeed());
		System.out.println("Distance between " + st.getName() + " and "
			+ st.getSourceAp().getName() + ": " +
			Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord())
			+ " Migration time: " + st.getMigTime());
		NetworkUsageMonitor.migrationTrafficUsage(st.getVmLocalServerCloudlet()
			.getUplinkBandwidth(), st.getVmMobileDevice().getSize());
		NetworkUsageMonitor.migrationVMTransferredData(st.getVmMobileDevice().getSize());
		try (FileWriter fw = new FileWriter(st.getMyId() + "migration.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t" +
				st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" +
				st.getSpeed() + "\t" + st.getVmLocalServerCloudlet().getName() + "\t" +
				st.getDestinationServerCloudlet().getName() + "\t" +
				CloudSim.clock() + "\t" + st.getMigTime() + "\t"
				+ (CloudSim.clock() + st.getMigTime()));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
		System.out.println(getName() + " Creating " + config.getInstanceCount()
			+ " instances of module " + config.getModule().getName());
	}

	/**
	 * Sending periodic tuple for an application edge. Note that for multiple
	 * instances of a single source module, only one tuple is sent DOWN while
	 * instanceCount number of tuples are sent UP.
	 * 
	 * @param ev
	 *        SimEvent instance containing the edge to send tuple on
	 */
	private void sendPeriodicTuple(SimEvent ev) {
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

		int instanceCount = getModuleInstanceCount().get(module.getAppId()).get(srcModule);

		/*
		 * Since tuples sent through a DOWN application edge are anyways
		 * broadcasted, only UP tuples are replicated
		 */
		for (int i = 0; i < ((edge.getDirection() == Tuple.UP) ? instanceCount : 1); i++) {
			if (applicationMap.isEmpty()) {
				continue;
			}
			else {
				Application app = getApplicationMap().get(module.getAppId());
				if (app == null) {
					continue;
				}
				Tuple tuple = applicationMap.get(module.getAppId()).createTuple(edge, getId());
				updateTimingsOnSending(tuple);
				sendToSelf(tuple);
			}
		}
		if (applicationMap.isEmpty()) {
			return;
		}
		else {
			send(getId(), edge.getPeriodicity(), FogEvents.SEND_PERIODIC_TUPLE, edge);
		}
	}

	protected void processActuatorJoined(SimEvent ev) {
		int actuatorId = ev.getSource();
		double delay = (double) ev.getData();
		getAssociatedActuatorIds().add(new Pair<Integer, Double>(actuatorId, delay));
	}

	protected void updateActiveApplications(SimEvent ev) {
		Application app = (Application) ev.getData();
		if (!getActiveApplications().contains(app.getAppId()))
			getActiveApplications().add(app.getAppId());
		System.out.println(" Apps " + getActiveApplications());
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
							resTuple.getModuleCopyMap().put(((AppModule) vm).getName(), vm.getId());
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
				if (!TimeKeeper.getInstance().getLoopIdToTupleIds().containsKey(loop.getLoopId()))
					TimeKeeper.getInstance().getLoopIdToTupleIds()
						.put(loop.getLoopId(), new ArrayList<Integer>());
				TimeKeeper.getInstance().getLoopIdToTupleIds().get(loop.getLoopId()).add(tupleId);
				TimeKeeper.getInstance().getEmitTimes().put(tupleId, CloudSim.clock());
			}
		}
	}

	protected int getChildIdWithRouteTo(int targetDeviceId) {
		for (Integer childId : getChildrenIds()) {
			if (targetDeviceId == childId)
				return childId;
			if (((FogDevice) CloudSim.getEntity(childId)).getChildIdWithRouteTo(targetDeviceId) != -1)
				return childId;
		}
		return -1;
	}

	protected int getChildIdForTuple(Tuple tuple) {
		if (tuple.getDirection() == Tuple.ACTUATOR) {
			int gatewayId = ((Actuator) CloudSim.getEntity(tuple.getActuatorId()))
				.getGatewayDeviceId();
			return getChildIdWithRouteTo(gatewayId);
		}
		return -1;
	}

	protected void updateAllocatedMips(String incomingOperator) {
		getHost().getVmScheduler().deallocatePesForAllVms();
		for (final Vm vm : getHost().getVmList()) {
			if (vm.getCloudletScheduler().runningCloudlets() > 0
				|| ((AppModule) vm).getName().equals(incomingOperator)) {
				getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
					protected static final long serialVersionUID = 1L;
					{
						add((double) getHost().getTotalMips());
					}
				});
			} else {
				getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
					protected static final long serialVersionUID = 1L;
					{
						add(0.0);
					}
				});
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
		applicationMap.put(app.getAppId(), app);
	}

	protected void addChild(int childId) {
		if (CloudSim.getEntityName(childId).toLowerCase().contains("sensor"))
			return;
		if (!getChildrenIds().contains(childId) && childId != getId())
			getChildrenIds().add(childId);
		if (!getChildToOperatorsMap().containsKey(childId))
			getChildToOperatorsMap().put(childId, new ArrayList<String>());
	}

	protected void removeChild(int childId) {
		getChildrenIds().remove(CloudSim.getEntity(childId));
		getChildToOperatorsMap().remove(childId);
	}

	protected void updateCloudTraffic() {
		int time = (int) CloudSim.clock() / 1000;
		if (!cloudTrafficMap.containsKey(time))
			cloudTrafficMap.put(time, 0);
		cloudTrafficMap.put(time, cloudTrafficMap.get(time) + 1);
	}

	protected void sendTupleToActuator(Tuple tuple) {
		for (Pair<Integer, Double> actuatorAssociation : getAssociatedActuatorIds()) {
			int actuatorId = actuatorAssociation.getFirst();
			double delay = actuatorAssociation.getSecond();
			String actuatorType = ((Actuator) CloudSim.getEntity(actuatorId)).getActuatorType();
			if (tuple.getDestModuleName().equals(actuatorType)) {
				send(actuatorId, delay, FogEvents.TUPLE_ARRIVAL, tuple);
				return;
			}
		}
		for (int childId : getChildrenIds()) {
			sendDown(tuple, childId);
		}
	}

	int numClients = 0;

	public void saveLostTupple(String a, String filename) {
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

	protected void processTupleArrival(SimEvent ev) {
		Tuple tuple = (Tuple) ev.getData();
		MyStatistics.getInstance().setMyCountTotalTuple(1);

		boolean flagContinue = false;
		for (MobileDevice st : MobileController.getSmartThings()) {
			for (Sensor s : st.getSensors()) {
				if (tuple.getAppId().equals(s.getAppId())) {
					flagContinue = true;
					break;
				}
			}
		}
		if (!flagContinue) {
			return;
		}

		if (tuple.getInitialTime() == -1) {
			MyStatistics.getInstance().getTupleLatency()
				.put(tuple.getMyTupleId(), CloudSim.clock() - getUplinkLatency());
			tuple.setInitialTime(CloudSim.clock() - getUplinkLatency());
		}

		for (MobileDevice st : getSmartThings()) {
			if (st.getId() == ev.getSource()) {
				if ((!st.isHandoffStatus() && !st.isMigStatus())) {
					break;
				}
				else {
					MyStatistics.getInstance().setMyCountLostTuple(1);
					saveLostTupple(String.valueOf(CloudSim.clock()), st.getId()
						+ "fdlostTupple.txt");
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

		if (FogUtils.appIdToGeoCoverageMap.containsKey(tuple.getAppId())) {
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
					getHost().getVmScheduler().allocatePesForVm(operator, new ArrayList<Double>() {
						protected static final long serialVersionUID = 1L;
						{
							add((double) getHost().getTotalMips());
						}
					});
				}

				break;
			}
		}
		if (getName().equals("cloud") && tuple.getDestModuleName() == null) {
			sendNow(getControllerId(), FogEvents.TUPLE_FINISHED, null);
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
						for (int childId : getChildrenIds()) {
							MobileDevice tempSt = (MobileDevice) CloudSim.getEntity(childId);
							if (tuple.getAppId().equals(
								((AppModule) tempSt.getVmMobileDevice()).getAppId())) {
								sendDown(tuple, childId);
							}
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
				for (int childId : getChildrenIds()) {
					MobileDevice tempSt = (MobileDevice) CloudSim.getEntity(childId);
					if (tuple.getAppId().equals(((AppModule) tempSt.getVmMobileDevice()).getAppId())) {
						sendDown(tuple, childId);
					}
				}

			}
		}
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
				Double startTime = TimeKeeper.getInstance().getEmitTimes()
					.get(tuple.getActualTupleId());
				if (startTime == null)
					break;
				if (!TimeKeeper.getInstance().getLoopIdToCurrentAverage()
					.containsKey(loop.getLoopId())) {
					TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), 0.0);
					TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), 0);
					TimeKeeper.getInstance().getMaxLoopExecutionTime().put(loop.getLoopId(), 0.0);
					printResults(String.valueOf(0), loop.getLoopId() + "LoopId.txt");
					printResults(String.valueOf(0), loop.getLoopId() + "LoopMaxId.txt");
				}
				double currentAverage = TimeKeeper.getInstance().getLoopIdToCurrentAverage()
					.get(loop.getLoopId());
				int currentCount = TimeKeeper.getInstance().getLoopIdToCurrentNum()
					.get(loop.getLoopId());
				double delay = CloudSim.clock()
					- TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());// +plusLatency);
				if (delay > TimeKeeper.getInstance().getMaxLoopExecutionTime()
					.get(loop.getLoopId())) {
					TimeKeeper.getInstance().getMaxLoopExecutionTime().put(loop.getLoopId(), delay);
					printResults(String.valueOf(delay), loop.getLoopId() + "LoopMaxId.txt");
				}
				TimeKeeper.getInstance().getEmitTimes().remove(tuple.getActualTupleId());
				double newAverage = (currentAverage * currentCount + delay) / (currentCount + 1);
				TimeKeeper.getInstance().getLoopIdToCurrentAverage()
					.put(loop.getLoopId(), newAverage);
				TimeKeeper.getInstance().getLoopIdToCurrentNum()
					.put(loop.getLoopId(), currentCount + 1);
				printResults(String.valueOf(newAverage), loop.getLoopId() + "LoopId.txt");
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

		boolean flagContinue = false;
		for (MobileDevice st : MobileController.getSmartThings()) {
			for (Sensor s : st.getSensors()) {
				if (tuple.getAppId().equals(s.getAppId())) {
					flagContinue = true;
					break;
				}
			}
		}
		if (!flagContinue) {
			return;
		}

		Logger.debug(getName(), "Executing tuple " + tuple.getCloudletId() + " on module "
			+ operatorId);

		if (MyStatistics.getInstance().getTupleLatency().get(tuple.getMyTupleId()) != null) {
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
		if (!getNorthTupleQueue().isEmpty()) {
			Tuple tuple = getNorthTupleQueue().poll();
			sendUpFreeLink(tuple);
		} else {
			setNorthLinkBusy(false);
		}
	}

	protected void sendUpFreeLink(Tuple tuple) {
		double networkDelay = tuple.getCloudletFileSize() / getUplinkBandwidth();
		setNorthLinkBusy(true);
		send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
		send(parentId, networkDelay + getUplinkLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.sendingTuple(getUplinkLatency(), tuple.getCloudletFileSize());
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
		if (!getSouthTupleQueue().isEmpty()) {
			Pair<Tuple, Integer> pair = getSouthTupleQueue().poll();
			sendDownFreeLink(pair.getFirst(), pair.getSecond());
		} else {
			setSouthLinkBusy(false);
		}
	}

	protected void sendDownFreeLink(Tuple tuple, int childId) {
		double networkDelay = tuple.getCloudletFileSize() / getDownlinkBandwidth();
		setSouthLinkBusy(true);
		double latency = getChildToLatencyMap().get(childId);
		send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
		send(childId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
		NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
	}

	protected void sendDown(Tuple tuple, int childId) {
		if (getChildrenIds().contains(childId)) {
			if (!isSouthLinkBusy()) {
				sendDownFreeLink(tuple, childId);
			} else {
				southTupleQueue.add(new Pair<Tuple, Integer>(tuple, childId));
			}
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
		return childrenIds;
	}

	public void setChildrenIds(List<Integer> childrenIds) {
		this.childrenIds = childrenIds;
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
		return activeApplications;
	}

	public void setActiveApplications(List<String> activeApplications) {
		this.activeApplications = activeApplications;
	}

	public ArrayList<String[]> getPath() {
		return path;
	}

	public void setPath(ArrayList<String[]> path) {
		this.path = path;
	}

	public Map<Integer, List<String>> getChildToOperatorsMap() {
		return childToOperatorsMap;
	}

	public void setChildToOperatorsMap(Map<Integer, List<String>> childToOperatorsMap) {
		this.childToOperatorsMap = childToOperatorsMap;
	}

	public Map<String, Application> getApplicationMap() {
		return applicationMap;
	}

	public void setApplicationMap(Map<String, Application> applicationMap) {
		this.applicationMap = applicationMap;
	}

	public Queue<Tuple> getNorthTupleQueue() {
		return northTupleQueue;
	}

	public void setNorthTupleQueue(Queue<Tuple> northTupleQueue) {
		this.northTupleQueue = northTupleQueue;
	}

	public Queue<Pair<Tuple, Integer>> getSouthTupleQueue() {
		return southTupleQueue;
	}

	public void setSouthTupleQueue(Queue<Pair<Tuple, Integer>> southTupleQueue) {
		this.southTupleQueue = southTupleQueue;
	}

	public double getDownlinkBandwidth() {
		return downlinkBandwidth;
	}

	public void setDownlinkBandwidth(double downlinkBandwidth) {
		this.downlinkBandwidth = downlinkBandwidth;
	}

	public List<Pair<Integer, Double>> getAssociatedActuatorIds() {
		return associatedActuatorIds;
	}

	public void setAssociatedActuatorIds(List<Pair<Integer, Double>> associatedActuatorIds) {
		this.associatedActuatorIds = associatedActuatorIds;
	}

	public double getEnergyConsumption() {
		return energyConsumption;
	}

	public void setEnergyConsumption(double energyConsumption) {
		this.energyConsumption = energyConsumption;
	}

	public Map<Integer, Double> getChildToLatencyMap() {
		return childToLatencyMap;
	}

	public void setChildToLatencyMap(Map<Integer, Double> childToLatencyMap) {
		this.childToLatencyMap = childToLatencyMap;
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
		return moduleInstanceCount;
	}

	public void setModuleInstanceCount(
		Map<String, Map<String, Integer>> moduleInstanceCount) {
		this.moduleInstanceCount = moduleInstanceCount;
	}

	public DecisionMigration getMigrationStrategy() {
		return migrationStrategy;
	}

	public void setMigrationStrategy(DecisionMigration migrationStrategy) {
		this.migrationStrategy = migrationStrategy;
	}

	public int getPolicyReplicaVM() {
		return policyReplicaVM;
	}

	public void setPolicyReplicaVM(int policyReplicaVM) {
		this.policyReplicaVM = policyReplicaVM;
	}

	public Set<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}

	public void setServerCloudlets(FogDevice sc, int action) {// myiFogSim
		if (action == Policies.ADD) {
			this.serverCloudlets.add(sc);
		}
		else {
			this.serverCloudlets.remove(sc);
		}
	}

	public FogDevice getServerCloudletToVmMigrate() {
		return serverCloudletToVmMigrate;
	}

	public void setServerCloudletToVmMigrate(FogDevice serverCloudletToVmMigrate) {
		this.serverCloudletToVmMigrate = serverCloudletToVmMigrate;
	}

	public Set<MobileDevice> getSmartThingsWithVm() {
		return smartThingsWithVm;
	}

	public void setSmartThingsWithVm(MobileDevice st, int action) {// myiFogSim
		if (action == Policies.ADD) {
			this.smartThingsWithVm.add(st);
		}
		else {
			this.smartThingsWithVm.remove(st);
		}
	}

	public int getVolatilParentId() {
		return volatilParentId;
	}

	public void setVolatilParentId(int volatilParentId) {
		this.volatilParentId = volatilParentId;
	}

	public BeforeMigration getBeforeMigrate() {
		return beforeMigration;
	}

	public void setBeforeMigrate(BeforeMigration beforeMigration) {
		this.beforeMigration = beforeMigration;
	}
}
