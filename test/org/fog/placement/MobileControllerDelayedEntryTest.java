package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.nio.file.Paths;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilitySample;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.vmmobile.AppExample;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmigration.MyStatistics;
import org.junit.Before;
import org.junit.Test;

public class MobileControllerDelayedEntryTest {

	private static final int CHECK_BEFORE_ENTRY = 8801;
	private static final int CHECK_AFTER_ENTRY = 8802;
	private static final int STOP = 8803;
	private static final int ACTIVATE_SENSOR = 8804;
	private static final int CHECK_BEFORE_SECOND_TRACE_TIME = 8805;
	private static final int CHECK_SEARCHING_AFTER_ENTRY = 8806;
	private static final int CHECK_SEARCHING_AFTER_MOVEMENT = 8807;
	private static final int CHECK_ACTIVE_AFTER_RETRY = 8808;
	private static final int CHECK_DISCONNECTED_AFTER_ACTIVATION = 8809;
	private static final int CHECK_RECONNECTED = 8810;
	private static final double DELTA = 0.000001;

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		RunOutputManager.initialize(
			Paths.get("build", "test-output", "mobile-lifecycle"),
			RunOutputMode.NONE);
		MyStatistics.setInstance(new MyStatistics());
	}

	@Test
	public void activatesUserAtFirstTraceTimeInsteadOfAtStartup() {
		MobileDevice futureUser = new MobileDevice("futureUser", 0, 0, 7, 0, 0);
		futureUser.setStartTravelTime(150);
		MobileUserRegistration.preparePendingUser(futureUser);
		RecordingController controller = new RecordingController(futureUser);
		EntryProbe probe = new EntryProbe(controller, futureUser);

		CloudSim.startSimulation();

		assertTrue(probe.wasPendingBeforeEntry());
		assertTrue(probe.wasActiveAfterEntry());
		assertEquals(150000.0, controller.getActivationTime(), DELTA);
		assertEquals(0, futureUser.getTravelTimeId());
	}

	@Test
	public void entryCreatesCompleteUserGraphOnlyAfterAssociation() {
		List<FogDevice> servers = new ArrayList<FogDevice>();
		List<ApDevice> accessPoints = new ArrayList<ApDevice>();
		List<MobileDevice> users = new ArrayList<MobileDevice>();
		List<FogBroker> brokers = new ArrayList<FogBroker>();
		configureAppExample(servers, accessPoints, users);
		AppExample.addServerCloudlet(servers, new Coordinate(), 0);
		ApDevice accessPoint = new ApDevice("entryAp", 0, 0, 0,
			100 * 1024 * 1024, 200, 500, 100 * 1024 * 1024, 4);
		accessPoint.setServerCloudlet(servers.get(0));
		accessPoints.add(accessPoint);
		AppExample.addSmartThing(users, new Coordinate(), 0);
		MobileDevice user = users.get(0);
		MyStatistics.getInstance().getMyCount().put(user.getMyId(), 0);
		user.setCoord(0, 0);
		user.setTravelTimeId(0);
		MobileUserRegistration.preparePendingUser(user);
		MobileController controller = new MobileController("activationController",
			servers, accessPoints, users, brokers, ModuleMapping.createModuleMapping(),
			Policies.FIXED_MIGRATION_POINT, Policies.LOWEST_LATENCY, 1,
			new Coordinate(), 1, false);

		assertFalse(user.isStatus());
		assertTrue(controller.activateMobileUser(user));

		assertTrue(user.isStatus());
		assertNotNull(user.getSourceAp());
		assertNotNull(user.getSourceServerCloudlet());
		assertNotNull(user.getVmMobileDevice());
		assertNotNull(user.getVmLocalServerCloudlet());
		assertEquals(1, brokers.size());
		assertEquals(1, brokers.get(0).getVmList().size());
		assertTrue(controller.getApplications().containsKey("MyApp_vr_game0"));
		assertTrue(user.getSensors().iterator().next().isEnabled());
		assertTrue(user.getActuators().iterator().next().isEnabled());
	}

	@Test
	public void pendingSensorStartsItsEmissionScheduleAtActivationTime() {
		CountingSensor sensor = new CountingSensor();
		sensor.deactivate();
		SensorActivationProbe probe = new SensorActivationProbe(sensor);
		sensor.setGatewayDeviceId(probe.getId());

		CloudSim.startSimulation();

		assertEquals(1, sensor.getEmissionTimes().size());
		assertEquals(150010.0, sensor.getEmissionTimes().get(0), DELTA);
	}

	@Test
	public void userOutsideCoverageRemainsPendingWithoutBrokerOrVm() {
		MobileDevice user = new MobileDevice("outsideCoverage", 5000, 5000, 3, 0, 0);
		user.setTravelTimeId(0);
		MobileUserRegistration.preparePendingUser(user);
		List<FogBroker> brokers = new ArrayList<FogBroker>();
		MobileController controller = new MobileController("coverageController",
			Collections.<FogDevice>emptyList(), Collections.<ApDevice>emptyList(),
			new ArrayList<MobileDevice>(Collections.singletonList(user)), brokers,
			ModuleMapping.createModuleMapping(), 0, 0, 1, new Coordinate(), 1, false);

		assertFalse(controller.activateMobileUser(user));

		assertFalse(user.isStatus());
		assertNull(user.getSourceAp());
		assertNull(user.getVmMobileDevice());
		assertTrue(brokers.isEmpty());
	}

	@Test
	public void replaysIrregularTraceAtExactTimestampsWithoutReplayingInitialRow() {
		MobileDevice user = new MobileDevice("irregularUser", 0, 0, 9, 0, 0);
		user.setMobilityPath(Arrays.asList(
			new MobilitySample(2.5, 0, 10, 10, 1),
			new MobilitySample(5.0, 0, 20, 20, 1),
			new MobilitySample(5.0, 0, 30, 30, 1),
			new MobilitySample(11.75, 0, 40, 40, 1)));
		new Coordinate().setInitialCoordinate(user);
		MobileUserRegistration.preparePendingUser(user);
		TimelineRecordingController controller = new TimelineRecordingController(user);
		PositionProbe probe = new PositionProbe(user);

		CloudSim.startSimulation();

		assertEquals(2500.0, controller.getActivationTime(), DELTA);
		assertEquals(10, controller.getActivationX());
		assertEquals(10, probe.getPositionBeforeSecondTimestamp());
		assertEquals(Arrays.asList(5000.0, 11750.0), controller.getUpdateTimes());
		assertEquals(Arrays.asList(30, 40), controller.getUpdatePositions());
		assertEquals(11750.0, controller.getStopTime(), DELTA);
		assertEquals(4, user.getTravelTimeId());
		assertEquals(-1, user.getCoord().getCoordX());
		assertTrue(MobileController.getSmartThings().isEmpty());
	}

	@Test
	public void outsideCoverageUserMovesRetriesActivatesOnceAndFinishesCleanly() {
		List<FogDevice> servers = new ArrayList<FogDevice>();
		List<ApDevice> accessPoints = new ArrayList<ApDevice>();
		List<MobileDevice> users = new ArrayList<MobileDevice>();
		List<FogBroker> brokers = new ArrayList<FogBroker>();
		configureAppExample(servers, accessPoints, users);
		AppExample.addServerCloudlet(servers, new Coordinate(), 0);
		FogDevice server = servers.get(0);
		server.setCoord(0, 0);
		ApDevice accessPoint = new ApDevice("lifecycleAp", 0, 0, 0,
			100 * 1024 * 1024, 200, 500, 100 * 1024 * 1024, 4);
		accessPoint.setServerCloudlet(server);
		accessPoint.setParentId(server.getId());
		server.setApDevices(accessPoint, Policies.ADD);
		accessPoints.add(accessPoint);

		AppExample.addSmartThing(users, new Coordinate(), 0);
		MobileDevice user = users.get(0);
		MyStatistics.getInstance().getMyCount().put(user.getMyId(), 0);
		user.setMobilityPath(Arrays.asList(
			new MobilitySample(2.5, 0, 1500, 1500, 1),
			new MobilitySample(5.0, 0, 1200, 1200, 1),
			new MobilitySample(11.75, 0, 100, 100, 1),
			new MobilitySample(14.0, 0, 1500, 1500, 1),
			new MobilitySample(18.0, 0, 100, 100, 1),
			new MobilitySample(19.0, 0, 1500, 1500, 1),
			new MobilitySample(20.0, 0, 1600, 1600, 1)));
		new Coordinate().setInitialCoordinate(user);
		MobileUserRegistration.preparePendingUser(user);

		LifecycleRecordingController controller = new LifecycleRecordingController(
			servers, accessPoints, users, brokers);
		LifecycleProbe probe = new LifecycleProbe(user, accessPoint, server, brokers);

		double finalClock = CloudSim.startSimulation();

		assertTrue(probe.wasScheduledBeforeEntry());
		assertTrue(probe.wasSearchingAfterEntry());
		assertTrue(probe.wasSearchingAfterMovement());
		assertTrue(probe.wasActiveAfterRetry());
		assertTrue(probe.wasDisconnectedAfterActivation());
		assertTrue(probe.wasReconnected());
		assertEquals(4, controller.getActivationAttempts());
		assertEquals(1, controller.getRegistrations());
		assertEquals(11750.0, controller.getActivationTime(), DELTA);
		assertEquals(20000.0, finalClock, DELTA);
		assertEquals(MobileDeviceLifecycle.FINISHED, user.getLifecycleState());
		assertFalse(user.isStatus());
		assertEquals(-1, user.getCoord().getCoordX());
		assertEquals(-1, user.getParentId());
		assertNull(user.getSourceAp());
		assertNull(user.getSourceServerCloudlet());
		assertFalse(accessPoint.getSmartThings().contains(user));
		assertFalse(server.getSmartThings().contains(user));
		assertFalse(user.getSensors().iterator().next().isEnabled());
		assertFalse(user.getActuators().iterator().next().isEnabled());
		assertFalse(MyStatistics.getInstance().getInitialTimeWithoutConnection()
			.containsKey(user.getMyId()));
		assertEquals(1, brokers.size());
		assertFalse(controller.activateMobileUser(user));
		assertEquals(MobileDeviceLifecycle.FINISHED, user.getLifecycleState());
		assertEquals(1, brokers.size());
	}

	private static void configureAppExample(List<FogDevice> servers,
		List<ApDevice> accessPoints, List<MobileDevice> users) {
		AppExample.setServerCloudlets(servers);
		AppExample.setApDevices(accessPoints);
		AppExample.setSmartThings(users);
		AppExample.setRand(new Random(1));
		AppExample.setMaxBandwidth(11);
		AppExample.setPolicyReplicaVM(Policies.MIGRATION_COMPLETE_VM);
		AppExample.setMigPointPolicy(Policies.FIXED_MIGRATION_POINT);
		AppExample.setMigStrategyPolicy(Policies.LOWEST_LATENCY);
		AppExample.setTravelPredicTimeForST(0);
		AppExample.setMobilityPredictionError(0);
	}

	private static final class RecordingController extends MobileController {
		private double activationTime = -1.0;

		private RecordingController(MobileDevice mobileDevice) {
			super("recordingController", Collections.<FogDevice>emptyList(),
				Collections.<ApDevice>emptyList(),
				new ArrayList<MobileDevice>(Collections.singletonList(mobileDevice)),
				0, 0, 1, new Coordinate(), 1);
		}

		@Override
		protected boolean activateMobileUser(MobileDevice mobileDevice) {
			activationTime = CloudSim.clock();
			mobileDevice.setLifecycleState(MobileDeviceLifecycle.ACTIVE);
			mobileDevice.setStatus(true);
			return true;
		}

		private double getActivationTime() {
			return activationTime;
		}
	}

	private static final class TimelineRecordingController extends MobileController {
		private double activationTime = -1.0;
		private int activationX = -1;
		private double stopTime = -1.0;
		private final List<Double> updateTimes = new ArrayList<Double>();
		private final List<Integer> updatePositions = new ArrayList<Integer>();

		private TimelineRecordingController(MobileDevice mobileDevice) {
			super("timelineController", Collections.<FogDevice>emptyList(),
				Collections.<ApDevice>emptyList(),
				new ArrayList<MobileDevice>(Collections.singletonList(mobileDevice)),
				new ArrayList<FogBroker>(), ModuleMapping.createModuleMapping(),
				0, 0, 1, new Coordinate(), 1, false);
		}

		@Override
		protected boolean activateMobileUser(MobileDevice mobileDevice) {
			activationTime = CloudSim.clock();
			activationX = mobileDevice.getCoord().getCoordX();
			mobileDevice.setLifecycleState(MobileDeviceLifecycle.ACTIVE);
			mobileDevice.setStatus(true);
			return true;
		}

		@Override
		protected boolean processCurrentMobilityPosition(MobileDevice mobileDevice) {
			return true;
		}

		@Override
		protected void onMobilityPositionUpdated(MobileDevice mobileDevice) {
			updateTimes.add(CloudSim.clock());
			updatePositions.add(mobileDevice.getCoord().getCoordX());
		}

		@Override
		protected void checkNewStep(MobileDevice mobileDevice) {
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == MobileEvents.STOP_SIMULATION) {
				stopTime = CloudSim.clock();
				CloudSim.terminateSimulation();
				return;
			}
			super.processEvent(event);
		}

		private double getActivationTime() {
			return activationTime;
		}

		private int getActivationX() {
			return activationX;
		}

		private double getStopTime() {
			return stopTime;
		}

		private List<Double> getUpdateTimes() {
			return updateTimes;
		}

		private List<Integer> getUpdatePositions() {
			return updatePositions;
		}
	}

	private static final class LifecycleRecordingController extends MobileController {
		private int activationAttempts;
		private int registrations;
		private double activationTime = -1.0;

		private LifecycleRecordingController(List<FogDevice> servers,
			List<ApDevice> accessPoints, List<MobileDevice> users,
			List<FogBroker> brokers) {
			super("lifecycleController", servers, accessPoints, users, brokers,
				ModuleMapping.createModuleMapping(), Policies.FIXED_MIGRATION_POINT,
				Policies.LOWEST_LATENCY, 1, new Coordinate(), 1, false);
		}

		@Override
		protected boolean activateMobileUser(MobileDevice mobileDevice) {
			activationAttempts++;
			boolean wasRegistered = mobileDevice.getVmMobileDevice() != null;
			boolean activated = super.activateMobileUser(mobileDevice);
			if (activated && !wasRegistered) {
				registrations++;
				activationTime = CloudSim.clock();
			}
			return activated;
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == MobileEvents.STOP_SIMULATION) {
				CloudSim.terminateSimulation();
				return;
			}
			super.processEvent(event);
		}

		private int getActivationAttempts() {
			return activationAttempts;
		}

		private int getRegistrations() {
			return registrations;
		}

		private double getActivationTime() {
			return activationTime;
		}
	}

	private static final class LifecycleProbe extends SimEntity {
		private final MobileDevice user;
		private final ApDevice accessPoint;
		private final FogDevice server;
		private final List<FogBroker> brokers;
		private boolean scheduledBeforeEntry;
		private boolean searchingAfterEntry;
		private boolean searchingAfterMovement;
		private boolean activeAfterRetry;
		private boolean disconnectedAfterActivation;
		private boolean reconnected;
		private Vm registeredVm;

		private LifecycleProbe(MobileDevice user, ApDevice accessPoint,
			FogDevice server, List<FogBroker> brokers) {
			super("lifecycleProbe");
			this.user = user;
			this.accessPoint = accessPoint;
			this.server = server;
			this.brokers = brokers;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 2499.0, CHECK_BEFORE_ENTRY);
			schedule(getId(), 2501.0, CHECK_SEARCHING_AFTER_ENTRY);
			schedule(getId(), 5001.0, CHECK_SEARCHING_AFTER_MOVEMENT);
			schedule(getId(), 11751.0, CHECK_ACTIVE_AFTER_RETRY);
			schedule(getId(), 14001.0, CHECK_DISCONNECTED_AFTER_ACTIVATION);
			schedule(getId(), 18001.0, CHECK_RECONNECTED);
		}

		@Override
		public void processEvent(SimEvent event) {
			switch (event.getTag()) {
			case CHECK_BEFORE_ENTRY:
				scheduledBeforeEntry = user.getLifecycleState()
					== MobileDeviceLifecycle.SCHEDULED
					&& user.getTravelTimeId() == 1 && user.getVmMobileDevice() == null;
				break;
			case CHECK_SEARCHING_AFTER_ENTRY:
				searchingAfterEntry = isSearchingAt(1500);
				break;
			case CHECK_SEARCHING_AFTER_MOVEMENT:
				searchingAfterMovement = isSearchingAt(1200);
				break;
			case CHECK_ACTIVE_AFTER_RETRY:
				registeredVm = user.getVmMobileDevice();
				activeAfterRetry = user.getLifecycleState()
					== MobileDeviceLifecycle.ACTIVE
					&& user.getCoord().getCoordX() == 100
					&& user.getSourceAp() == accessPoint
					&& user.getSourceServerCloudlet() == server
					&& user.getVmMobileDevice() != null
					&& accessPoint.getSmartThings().contains(user)
					&& server.getSmartThings().contains(user)
					&& brokers.size() == 1;
				break;
			case CHECK_DISCONNECTED_AFTER_ACTIVATION:
				disconnectedAfterActivation = user.getLifecycleState()
					== MobileDeviceLifecycle.DISCONNECTED
					&& user.getCoord().getCoordX() == 1500
					&& user.getSourceAp() == null
					&& user.getSourceServerCloudlet() == null
					&& user.getParentId() == -1
					&& user.getVmMobileDevice() == registeredVm
					&& !accessPoint.getSmartThings().contains(user)
					&& !server.getSmartThings().contains(user)
					&& brokers.size() == 1;
				break;
			case CHECK_RECONNECTED:
				reconnected = user.getLifecycleState() == MobileDeviceLifecycle.ACTIVE
					&& user.getCoord().getCoordX() == 100
					&& user.getSourceAp() == accessPoint
					&& user.getSourceServerCloudlet() == server
					&& user.getVmMobileDevice() == registeredVm
					&& accessPoint.getSmartThings().contains(user)
					&& server.getSmartThings().contains(user)
					&& brokers.size() == 1;
				break;
			default:
				break;
			}
		}

		private boolean isSearchingAt(int x) {
			return user.getLifecycleState() == MobileDeviceLifecycle.SEARCHING_FOR_AP
				&& user.getCoord().getCoordX() == x
				&& user.getSourceAp() == null
				&& user.getSourceServerCloudlet() == null
				&& user.getVmMobileDevice() == null && brokers.isEmpty();
		}

		@Override
		public void shutdownEntity() {
		}

		private boolean wasScheduledBeforeEntry() {
			return scheduledBeforeEntry;
		}

		private boolean wasSearchingAfterEntry() {
			return searchingAfterEntry;
		}

		private boolean wasSearchingAfterMovement() {
			return searchingAfterMovement;
		}

		private boolean wasActiveAfterRetry() {
			return activeAfterRetry;
		}

		private boolean wasDisconnectedAfterActivation() {
			return disconnectedAfterActivation;
		}

		private boolean wasReconnected() {
			return reconnected;
		}
	}

	private static final class PositionProbe extends SimEntity {
		private final MobileDevice mobileDevice;
		private int positionBeforeSecondTimestamp = -1;

		private PositionProbe(MobileDevice mobileDevice) {
			super("irregularPositionProbe");
			this.mobileDevice = mobileDevice;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 4999.0, CHECK_BEFORE_SECOND_TRACE_TIME);
		}

		@Override
		public void processEvent(SimEvent event) {
			positionBeforeSecondTimestamp = mobileDevice.getCoord().getCoordX();
		}

		@Override
		public void shutdownEntity() {
		}

		private int getPositionBeforeSecondTimestamp() {
			return positionBeforeSecondTimestamp;
		}
	}

	private static final class EntryProbe extends SimEntity {
		private final RecordingController controller;
		private final MobileDevice mobileDevice;
		private boolean pendingBeforeEntry;
		private boolean activeAfterEntry;

		private EntryProbe(RecordingController controller, MobileDevice mobileDevice) {
			super("entryProbe");
			this.controller = controller;
			this.mobileDevice = mobileDevice;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 149999.0, CHECK_BEFORE_ENTRY);
			schedule(getId(), 150001.0, CHECK_AFTER_ENTRY);
			schedule(getId(), 150002.0, STOP);
		}

		@Override
		public void processEvent(SimEvent event) {
			switch (event.getTag()) {
			case CHECK_BEFORE_ENTRY:
				pendingBeforeEntry = controller.getActivationTime() < 0
					&& mobileDevice.getLifecycleState()
						== MobileDeviceLifecycle.SCHEDULED
					&& !mobileDevice.isStatus();
				break;
			case CHECK_AFTER_ENTRY:
				activeAfterEntry = controller.getActivationTime() == 150000.0
					&& mobileDevice.getTravelTimeId() == 0
					&& mobileDevice.isStatus();
				break;
			case STOP:
				CloudSim.terminateSimulation();
				break;
			default:
				break;
			}
		}

		@Override
		public void shutdownEntity() {
		}

		private boolean wasPendingBeforeEntry() {
			return pendingBeforeEntry;
		}

		private boolean wasActiveAfterEntry() {
			return activeAfterEntry;
		}
	}

	private static final class CountingSensor extends MobileSensor {
		private final List<Double> emissionTimes = new ArrayList<Double>();

		private CountingSensor() {
			super("delayedSensor", "EEG", 1, "delayedApp",
				new DeterministicDistribution(10));
		}

		@Override
		public void transmit() {
			emissionTimes.add(CloudSim.clock());
		}

		private List<Double> getEmissionTimes() {
			return emissionTimes;
		}
	}

	private static final class SensorActivationProbe extends SimEntity {
		private final MobileSensor sensor;

		private SensorActivationProbe(MobileSensor sensor) {
			super("sensorActivationProbe");
			this.sensor = sensor;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 150000.0, ACTIVATE_SENSOR);
			schedule(getId(), 150015.0, STOP);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == ACTIVATE_SENSOR) {
				sensor.activate();
			} else if (event.getTag() == STOP) {
				CloudSim.terminateSimulation();
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}
}
