package org.fog.entities;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.fog.application.AppModule;
import org.fog.localization.*;
import org.fog.placement.MobileController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.utils.Config;
import org.fog.utils.EntityId;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.VmMigrationTechnique;
import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.policy.MovementDirection;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.Vm;

public class MobileDevice extends FogDevice {

	private MovementDirection direction = MovementDirection.NONE;
	private int speed; // in m/s
	protected Coordinate futureCoord;// = new Coordinate();//myiFogSim
	private FogDevice sourceServerCloudlet;
	private FogDevice destinationServerCloudlet;
	private FogDevice vmLocalServerCloudlet;
	private ApDevice sourceAp;
	private ApDevice destinationAp;
	private WirelessAssociation wirelessAssociation;
	private HandoffReservation pendingHandoffReservation;
	private Vm vmMobileDevice;
	private double migTime;
	private boolean migPoint;
	private boolean migZone;
	private Set<MobileSensor> sensors;// Set of Sensors
	private Set<MobileActuator> actuators;// Set of Actuators
	private float maxServiceValue;
	private boolean migStatus;
	private boolean postCopyStatus;
	private boolean lockedToMigration;
	private boolean abortMigration;
	private double vmSize;
	private double tempSimulation;
	private double timeFinishHandoff = 0;
	private double timeFinishDeliveryVm = 0;
	private double timeStartLiveMigration = 0;
	private boolean status;
	private boolean migStatusLive;
	protected VmMigrationTechnique migrationTechnique;
	private int networkSliceId;
	private final MobileSession session = new MobileSession();

	@Override
	protected double getUplinkBandwidthForTuple(Tuple tuple) {
		if (getSourceAp() == null) {
			return super.getUplinkBandwidthForTuple(tuple);
		}
		return NetworkSlicing.getAccessPointUplinkBandwidth(getSourceAp(), this);
	}

	@Override
	protected void sendUp(Tuple tuple) {
		if (getParentId() > 0 && getSourceAp() != null
			&& getSourceAp().getSmartThings().contains(this)) {
			NetworkSlicing.startWirelessTupleTransfer(getSourceAp(), this,
				NetworkSlicing.WirelessDirection.UPLINK, tuple, EntityId.of(getId()),
				EntityId.of(getParentId()), PropagationDelay.ofMilliseconds(
					getWirelessPropagationDelayMillis()));
			return;
		}
		super.sendUp(tuple);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.getName() == null) ? 0 : this.getName().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MobileDevice other = (MobileDevice) obj;
		if (this.getName() == null) {
			if (other.getName() != null)
				return false;
		} else if (!this.getName().equals(other.getName()))
			return false;
		return true;
	}

	public MobileDevice() {
		// TODO Auto-generated constructor stub
	}

	public MobileDevice(String name, Coordinate coord, int coordX, int coordY, int id) {
		// TODO Auto-generated constructor stub
		super(name, coordX, coordY, id);

		setDirection(0);
		setSpeed(0);
		setSourceServerCloudlet(null);
		setDestinationServerCloudlet(null);
		setVmLocalServerCloudlet(null);
		setSourceAp(null);
		setDestinationAp(null);
		setVmMobileDevice(null);
		setMigTime(0);
		setMigStatus(false);
		setMigStatusLive(false);
		setPostCopyStatus(false);
		setHandoffStatus(false);
		setLockedToHandoff(false);
		setLockedToMigration(false);
		setAbortMigration(false);
		setMigPoint(false);
		setMigZone(false);
		actuators = new HashSet<>();
		sensors = new HashSet<>();
		setStatus(true);
		this.futureCoord = new Coordinate();
		setFutureCoord(-1, -1);

	}

	public MobileDevice(String name, int coordX, int coordY, int id, int dir, int sp) {
		this(name, coordX, coordY, id, MovementDirection.fromLegacy(dir), sp);
	}

	public MobileDevice(String name, int coordX, int coordY, int id,
		MovementDirection direction, int sp) {

		super(name, coordX, coordY, id);
		setMovementDirection(direction);
		setSpeed(sp);
		setSourceServerCloudlet(null);
		setDestinationServerCloudlet(null);
		setVmLocalServerCloudlet(null);
		setSourceAp(null);
		setDestinationAp(null);
		setVmMobileDevice(null);
		setMigTime(0);
		setMigStatus(false);
		setMigStatusLive(false);
		setPostCopyStatus(false);

		setHandoffStatus(false);
		setLockedToHandoff(false);
		setLockedToMigration(false);
		setStatus(true);
		setAbortMigration(false);
		this.futureCoord = new Coordinate();
		setFutureCoord(-1, -1);

		actuators = new HashSet<>();
		sensors = new HashSet<>();

	}

	// @Override
	public MobileDevice(String name, FogDeviceCharacteristics characteristics,
		AppModuleAllocationPolicy vmAllocationPolicy, LinkedList<Storage> storageList,
		double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
		double uplinkLatency, double d, int coordX, int coordY, int id, int dir, int sp,
		float maxServiceValue, double vmSize
		, VmMigrationTechnique migrationTechnique) throws Exception {
		this(name, characteristics, vmAllocationPolicy, storageList,
			schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency,
			d, coordX, coordY, id, MovementDirection.fromLegacy(dir), sp,
			maxServiceValue, vmSize, migrationTechnique);
	}

	public MobileDevice(String name, FogDeviceCharacteristics characteristics,
		AppModuleAllocationPolicy vmAllocationPolicy,
		LinkedList<Storage> storageList, double schedulingInterval,
		double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency,
		double d, int coordX, int coordY, int id, MovementDirection direction,
		int sp, float maxServiceValue, double vmSize,
		VmMigrationTechnique migrationTechnique) throws Exception {

		super(name, characteristics, vmAllocationPolicy
			, storageList, schedulingInterval
			, uplinkBandwidth
			, downlinkBandwidth
			, uplinkLatency, sp, coordX, coordY, id);
		setMovementDirection(direction);
		setSpeed(sp);
		setSourceServerCloudlet(null);
		setDestinationServerCloudlet(null);
		setVmLocalServerCloudlet(null);
		setSourceAp(null);
		setDestinationAp(null);
		setVmMobileDevice(null);
		setMigTime(0);
		setMigStatus(false);
		setMigStatusLive(false);
		setPostCopyStatus(false);

		setVmSize(vmSize);
		setHandoffStatus(false);
		setLockedToHandoff(false);
		setStatus(true);
		setAbortMigration(false);
		setMigrationTechnique(migrationTechnique);
		this.futureCoord = new Coordinate();
		setFutureCoord(-1, -1);
		actuators = new HashSet<>();
		sensors = new HashSet<>();
		setMaxServiceValue(maxServiceValue);

	}

	@Override
	public String toString() {
		return this.getName() + "[coordX=" + this.getCoord().getCoordX() + ", coordY="
			+ this.getCoord().getCoordY() + ", direction=" + getDirection() + ", speed=" + speed
			+ ", sourceCloudletServer=" + sourceServerCloudlet + ", destinationCloudletServer="
			+ destinationServerCloudlet + ", sourceAp=" + sourceAp + ", destinationAp="
			+ destinationAp + ", vmMobileDevice=" + vmMobileDevice + ", migTime=" + migTime
			+ ", sensors=" + sensors + ", actuators=" + actuators + "]";
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
		default:
			super.processOtherEvent(ev);
			break;
		}
	}

	public void saveLostTupple(String a, String filename) {
		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true)) {
			out1.println(a);
		} catch (IOException e) {
			throw new IllegalStateException(
				"Could not record lost tuple " + filename, e);
		}
	}

	protected void processTupleArrival(SimEvent ev) {

		Tuple tuple = (Tuple) ev.getData();
		MyStatistics.getInstance().incrementTotalTupleCount();

		if (!MobileController.getSmartThings().contains(this)) {
			return;
		}

		if (!MobileController.isApplicationActive(tuple.getAppId())) {
			return;
		}

		if (tuple.getInitialTime() == -1) {
			tuple.setInitialTime(CloudSim.clock() - getUplinkLatency());
		}

		if (getName().equals("cloud")) {
			updateCloudTraffic();
		}

		Logger.debug(
			getName(),
			"Received tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType()
				+ "\t| Source : " + CloudSim.getEntityName(ev.getSource()) + "|Dest : "
				+ CloudSim.getEntityName(ev.getDestination()));
		send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

		if (FogUtils.getApplicationCoverage().containsKey(tuple.getAppId())) {
		}

		if (tuple.getDirection() == Tuple.ACTUATOR) {
			sendTupleToActuator(tuple);
			return;
		}

		if ((isMigStatus() || isHandoffStatus())) {
			MyStatistics.getInstance().incrementLostTupleCount();
			saveLostTupple(String.valueOf(CloudSim.clock()), tuple.getUserId() + "mdlostTupple.txt");
			return;
		}
		else {
			if (getHost().getVmList().size() > 0) {
				for (Vm vm : getHost().getVmList()) {
					final AppModule operator = (AppModule) vm;
					if (CloudSim.clock() > 0) {
						getHost().getVmScheduler().deallocatePesForVm(operator);
						getHost().getVmScheduler().allocatePesForVm(operator,
							new ArrayList<Double>() {
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
						|| (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) && tuple
							.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
						return;
					}
					tuple.setVmId(vmId);

					updateTimingsOnReceipt(tuple);

					executeTuple(ev, tuple.getDestModuleName());
				} else if (tuple.getDestModuleName() != null) {
					if (tuple.getDirection() == Tuple.UP)
						sendUp(tuple);
					else if (tuple.getDirection() == Tuple.DOWN) {
						for (int childId : getChildrenIds())
							sendDown(tuple, childId);
					}
				} else {
					sendUp(tuple);
				}
			} else {
				if (tuple.getDirection() == Tuple.UP)
					sendUp(tuple);
				else if (tuple.getDirection() == Tuple.DOWN) {
					for (int childId : getChildrenIds())
						sendDown(tuple, childId);
				}
			}
		}
	}

	private void updateModuleInstanceCount(SimEvent ev) {
		ModuleLaunchConfig config = (ModuleLaunchConfig) ev.getData();
		String appId = config.getModule().getAppId();
		if (!moduleInstanceCount.containsKey(appId))
			moduleInstanceCount.put(appId, new HashMap<String, Integer>());
		moduleInstanceCount.get(appId).put(config.getModule().getName(), config.getInstanceCount());
		SimulationEventSink.current().trace("MobileDevice", () -> getName()
			+ " creating " + config.getInstanceCount() + " instances of module "
			+ config.getModule().getName());
	}

	private void manageResources(SimEvent ev) {
		send(getId(), Config.RESOURCE_MGMT_INTERVAL, FogEvents.RESOURCE_MGMT);
	}

	public float getMaxServiceValue() {
		return maxServiceValue;
	}

	public void setMaxServiceValue(float maxServiceValue) {
		this.maxServiceValue = maxServiceValue;
	}

	public int getDirection() {
		return direction.legacyValue();
	}

	public void setDirection(int direction) {
		setMovementDirection(MovementDirection.fromLegacy(direction));
	}

	public MovementDirection getMovementDirection() {
		return direction;
	}

	public void setMovementDirection(MovementDirection direction) {
		if (direction == null) {
			throw new IllegalArgumentException("Movement direction cannot be null");
		}
		this.direction = direction;
	}

	public int getSpeed() {
		return speed;
	}

	public void setSpeed(int speed) {
		this.speed = speed;
	}

	public Coordinate getFutureCoord() {// myiFogSim
		return futureCoord;
	}

	public void setFutureCoord(int coordX, int coordY) {
		this.futureCoord.setCoordX(coordX);
		this.futureCoord.setCoordY(coordY);
	}

	public FogDevice getSourceServerCloudlet() {
		return sourceServerCloudlet;
	}

	public void setSourceServerCloudlet(FogDevice sourceServerCloudlet) {
		this.sourceServerCloudlet = sourceServerCloudlet;
	}

	public FogDevice getDestinationServerCloudlet() {
		return destinationServerCloudlet;
	}

	public void setDestinationServerCloudlet(FogDevice destinationServerCloudlet) {
		this.destinationServerCloudlet = destinationServerCloudlet;
	}

	public ApDevice getSourceAp() {
		return sourceAp;
	}

	public void setSourceAp(ApDevice sourceAp) {
		this.sourceAp = sourceAp;
		if (sourceAp == null) {
			wirelessAssociation = null;
		}
		else if (wirelessAssociation == null
			|| wirelessAssociation.getAccessPoint() != sourceAp) {
			wirelessAssociation = WirelessAssociation.establish(sourceAp, this,
				PropagationDelay.ofMilliseconds(getUplinkLatency()),
				SimulationDuration.ZERO);
		}
	}

	public WirelessAssociation getWirelessAssociation() {
		return wirelessAssociation;
	}

	/** Installs both sides of the mobile's current wireless-link state. */
	void establishWirelessAssociation(ApDevice accessPoint,
		PropagationDelay propagationDelay,
		SimulationDuration establishmentDuration) {
		wirelessAssociation = WirelessAssociation.establish(accessPoint, this,
			propagationDelay, establishmentDuration);
		sourceAp = accessPoint;
	}

	public double getWirelessPropagationDelayMillis() {
		return wirelessAssociation == null
			? getUplinkLatency()
			: wirelessAssociation.getPropagationDelay().toMilliseconds();
	}

	public ApDevice getDestinationAp() {
		return destinationAp;
	}

	public void setDestinationAp(ApDevice destinationAp) {
		this.destinationAp = destinationAp;
	}

	public HandoffReservation getPendingHandoffReservation() {
		return pendingHandoffReservation;
	}

	void installHandoffReservation(HandoffReservation reservation) {
		if (reservation == null || reservation.getMobileDevice() != this) {
			throw new IllegalArgumentException(
				"A handoff reservation must belong to this mobile device");
		}
		if (pendingHandoffReservation != null
			&& pendingHandoffReservation != reservation) {
			throw new IllegalStateException(
				"Mobile device already owns a handoff reservation");
		}
		pendingHandoffReservation = reservation;
	}

	void clearHandoffReservation(HandoffReservation reservation) {
		if (pendingHandoffReservation == reservation) {
			pendingHandoffReservation = null;
		}
	}

	/** Cancels the current reservation and invalidates all events using it. */
	public boolean cancelPendingHandoffReservation() {
		HandoffReservation reservation = pendingHandoffReservation;
		return reservation != null
			&& reservation.getDestinationAccessPoint()
				.cancelHandoffReservation(reservation);
	}

	public Vm getVmMobileDevice() {
		return vmMobileDevice;
	}

	public void setVmMobileDevice(Vm vmMobileDevice) {
		this.vmMobileDevice = vmMobileDevice;
	}

	public double getVmSize() {
		return vmSize;
	}

	public void setVmSize(double vmSize) {
		this.vmSize = vmSize;
	}

	public double getMigTime() {
		return migTime;
	}

	public void setMigTime(double d) {
		this.migTime = d;
	}

	public Set<MobileSensor> getSensors() {
		return java.util.Collections.unmodifiableSet(sensors);
	}

	public void setSensors(Set<MobileSensor> sensors) {
		if (sensors == null) {
			throw new IllegalArgumentException("Mobile sensor set cannot be null");
		}
		this.sensors = new java.util.HashSet<MobileSensor>(sensors);
	}

	public Set<MobileActuator> getActuators() {
		return java.util.Collections.unmodifiableSet(actuators);
	}

	public void setActuators(Set<MobileActuator> actuators) {
		if (actuators == null) {
			throw new IllegalArgumentException("Mobile actuator set cannot be null");
		}
		this.actuators = new java.util.HashSet<MobileActuator>(actuators);
	}

	public boolean isMigStatus() {
		return migStatus;
	}

	public void setMigStatus(boolean migStatus) {
		this.migStatus = migStatus;
	}

	public boolean isMigStatusLive() {
		return migStatusLive;
	}

	public void setMigStatusLive(boolean migStatusLive) {
		this.migStatusLive = migStatusLive;
	}

	public double getTempSimulation() {
		return tempSimulation;
	}

	public void setTempSimulation(double tempSimulation) {
		this.tempSimulation = tempSimulation;
	}

	public boolean isHandoffStatus() {
		return session.isHandoffInProgress();
	}

	public void setHandoffStatus(boolean handoffStatus) {
		if (handoffStatus) {
			if (!session.isHandoffInProgress()) {
				session.reserveHandoff();
			}
		}
		else if (session.isHandoffInProgress()) {
			session.completeHandoff(session.getAssociationGeneration());
		}
	}

	public double getTimeFinishHandoff() {
		return timeFinishHandoff;
	}

	public void setTimeFinishHandoff(double timeFinishHandoff) {
		this.timeFinishHandoff = timeFinishHandoff;
	}

	public double getTimeFinishDeliveryVm() {
		return timeFinishDeliveryVm;
	}

	public void setTimeFinishDeliveryVm(double timeFinishDeliveryVm) {
		this.timeFinishDeliveryVm = timeFinishDeliveryVm;
	}

	public FogDevice getVmLocalServerCloudlet() {
		return vmLocalServerCloudlet;
	}

	public void setVmLocalServerCloudlet(FogDevice vmLocalServerCloudlet) {
		this.vmLocalServerCloudlet = vmLocalServerCloudlet;
	}

	public boolean isStatus() {
		return status;
	}

	public void setStatus(boolean status) {
		this.status = status;
	}

	public MobileDeviceLifecycle getLifecycleState() {
		return session.getLifecycle();
	}

	public void setLifecycleState(MobileDeviceLifecycle lifecycleState) {
		if (lifecycleState == null) {
			throw new IllegalArgumentException("Mobile-device lifecycle state cannot be null");
		}
		session.setLifecycle(lifecycleState);
	}

	public boolean isLockedToHandoff() {
		return session.isHandoffLocked();
	}

	public void setLockedToHandoff(boolean LockedToHandoff) {
		if (LockedToHandoff) {
			if (!session.isHandoffLocked()) {
				session.reserveHandoff();
			}
			return;
		}
		if (session.isHandoffInProgress()) {
			session.cancelHandoff(session.getAssociationGeneration());
		}
		session.unlockHandoff();
	}

	/** Invalidates older delayed events and returns the new association generation. */
	public long advanceNetworkAssociationGeneration() {
		return session.invalidateAssociation();
	}

	public long getNetworkAssociationGeneration() {
		return session.getAssociationGeneration();
	}

	public MobileSession getSession() {
		return session;
	}

	public long beginHandoffReservation() {
		return session.reserveHandoff();
	}

	public boolean beginHandoffTransfer(long generation) {
		return session.beginHandoff(generation);
	}

	public boolean completeHandoff(long generation) {
		return session.completeHandoff(generation);
	}

	public boolean cancelHandoff(long generation) {
		return session.cancelHandoff(generation);
	}

	public boolean isLockedToMigration() {
		return lockedToMigration;
	}

	public void setLockedToMigration(boolean lockedToMigration) {
		this.lockedToMigration = lockedToMigration;
	}

	public boolean isAbortMigration() {
		return abortMigration;
	}

	public void setAbortMigration(boolean abortMigration) {
		this.abortMigration = abortMigration;
	}

	public VmMigrationTechnique getMigrationTechnique() {
		return migrationTechnique;
	}

	public void setMigrationTechnique(VmMigrationTechnique migrationTechnique) {
		this.migrationTechnique = migrationTechnique;
	}

	public int getNetworkSliceId() {
		return networkSliceId;
	}

	public void setNetworkSliceId(int networkSliceId) {
		this.networkSliceId = networkSliceId;
	}

	public boolean isMigPoint() {
		return migPoint;
	}

	public void setMigPoint(boolean migPoint) {
		this.migPoint = migPoint;
	}

	public boolean isMigZone() {
		return migZone;
	}

	public void setMigZone(boolean migZone) {
		this.migZone = migZone;
	}

	public boolean isPostCopyStatus() {
		return postCopyStatus;
	}

	public void setPostCopyStatus(boolean postCopyStatus) {
		this.postCopyStatus = postCopyStatus;
	}

	public double getTimeStartLiveMigration() {
		return timeStartLiveMigration;
	}

	public void setTimeStartLiveMigration(double timeStartLiveMigration) {
		this.timeStartLiveMigration = timeStartLiveMigration;
	}

}
