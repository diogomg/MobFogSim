package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.localization.Coordinate;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.SimulationDuration;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.AppExample;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.TopologyService;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.junit.Before;
import org.junit.Test;

/**
 * Executable specifications for the C1-C5 P0 correctness findings.
 *
 * <p>The known-defect cases are skipped by the normal suite. Run them with
 * {@code -Dmobfogsim.runKnownP0Defects=true} to observe the desired invariants
 * fail before their Phase 1 fixes. Remove each assumption when its defect is
 * fixed; a fixed invariant must become part of the ordinary regression suite.</p>
 */
public class P0CorrectnessRegressionTest {

	private static final String RUN_KNOWN_DEFECTS_PROPERTY =
		"mobfogsim.runKnownP0Defects";
	private static final double DELTA = 0.000001;

	@Before
	public void initialiseServices() {
		Log.disable();
		MobileController.resetRunState();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkTopology.reset();
		NetworkSlicing.useDefaultRuntimeState();
		RunOutputManager.initialize(
			Paths.get("build", "test-output", "p0-correctness"),
			RunOutputMode.NONE);
		MyStatistics.setInstance(new MyStatistics());
	}

	@Test
	public void initialAssociationAlreadyHonoursAccessPointCapacity() {
		ApDevice accessPoint = accessPoint("capacityAp", 1, 4.0);
		MobileDevice first = mobile("first", 1);
		MobileDevice second = mobile("second", 2);

		assertTrue(ApDevice.connectApSmartThing(
			Collections.singletonList(accessPoint), first,
			SimulationDuration.ofMilliseconds(0.5)));
		assertFalse(ApDevice.connectApSmartThing(
			Collections.singletonList(accessPoint), second,
			SimulationDuration.ofMilliseconds(0.5)));

		assertEquals(1, accessPoint.getSmartThings().size());
		assertTrue(accessPoint.getSmartThings().contains(first));
		assertNull(second.getSourceAp());
	}

	@Test
	public void finishingAnAlreadyFinishedUserIsAlreadyIdempotent() {
		MobileDevice user = mobile("idempotent", 3);
		List<MobileDevice> activeUsers = new ArrayList<MobileDevice>();
		activeUsers.add(user);
		MobileController.setSmartThings(activeUsers);
		MobileUserRegistration.preparePendingUser(user);
		MobileAssociationService service = new MobileAssociationService();

		service.finish(user);
		service.finish(user);

		assertEquals(MobileDeviceLifecycle.FINISHED, user.getLifecycleState());
		assertFalse(user.isStatus());
		assertTrue(MobileController.getSmartThings().isEmpty());
	}

	@Test
	public void associationFailureForAnApWithoutAServerAlreadyRollsBack() {
		ApDevice accessPoint = accessPoint("orphanAp", 1, 4.0);
		MobileDevice user = mobile("orphanUser", 4);
		MobileUserRegistration.preparePendingUser(user);

		try {
			new MobileAssociationService().associate(user,
				Collections.singletonList(accessPoint), new Random(1));
			fail("Expected the missing server cloudlet to reject association");
		}
		catch (IllegalStateException expected) {
			// Expected.
		}

		assertNull(user.getSourceAp());
		assertNull(user.getSourceServerCloudlet());
		assertFalse(accessPoint.getSmartThings().contains(user));
		assertEquals(MobileDeviceLifecycle.SEARCHING_FOR_AP,
			user.getLifecycleState());
	}

	@Test
	public void c1AssociationMustNotRewriteEndpointTopologyLatency() {
		ApDevice accessPoint = accessPoint("immutableLatencyAp", 1, 4.0);
		FogDevice server = new FogDevice("immutableLatencyServer", 0, 0, 0);
		server.setUplinkLatency(7.0);
		MobileDevice user = mobile("associatedUser", 5);
		user.setUplinkLatency(2.0);

		assertTrue(ApDevice.connectApSmartThing(
			Collections.singletonList(accessPoint), user,
			SimulationDuration.ofMilliseconds(0.25)));
		assertTrue(server.connectServerCloudletSmartThing(user));

		boolean unchanged = accessPoint.getUplinkLatency() == 4.0
			&& server.getUplinkLatency() == 7.0;
		assertTrue("C1: association duration changed endpoint topology latency "
			+ "(AP=" + accessPoint.getUplinkLatency() + ", server="
			+ server.getUplinkLatency() + ')', unchanged);
		assertNotNull(user.getWirelessAssociation());
		assertEquals(0.25, user.getWirelessAssociation()
			.getEstablishmentDuration().toMilliseconds(), DELTA);
		assertEquals(2.0, user.getWirelessAssociation()
			.getPropagationDelay().toMilliseconds(), DELTA);
		assertEquals(2.0,
			NetworkTopology.getDelay(accessPoint.getId(), user.getId()), DELTA);
		assertEquals(2.0,
			server.getChildToLatencyMap().get(user.getId()), DELTA);
	}

	@Test
	public void c1AssociationCyclesMustNotMutateTopologyLatency() {
		ApDevice accessPoint = accessPoint("stableLatencyAp", 1, 4.0);
		FogDevice server = new FogDevice("stableLatencyServer", 0, 0, 0);
		server.setUplinkLatency(7.0);
		MobileDevice user = mobile("cyclingUser", 5);
		final double originalAccessPointLatency = accessPoint.getUplinkLatency();
		final double originalServerLatency = server.getUplinkLatency();

		for (int cycle = 0; cycle < 1000; cycle++) {
			assertTrue(ApDevice.connectApSmartThing(
				Collections.singletonList(accessPoint), user,
				SimulationDuration.ofMilliseconds(0.25)));
			assertTrue(server.connectServerCloudletSmartThing(user));
			assertTrue(accessPoint.desconnectApSmartThing(user));
			assertTrue(server.desconnectServerCloudletSmartThing(user));
		}

		assertEquals("C1: AP topology latency drifted across associations",
			originalAccessPointLatency, accessPoint.getUplinkLatency(), DELTA);
		assertEquals("C1: server topology latency drifted across associations",
			originalServerLatency, server.getUplinkLatency(), DELTA);
	}

	@Test
	public void c1HandoffSetupTimeMustNotBecomePropagationLatency() {
		ApDevice source = accessPoint("handoffSource", 1, 4.0);
		ApDevice destination = accessPoint("handoffDestination", 1, 6.0);
		MobileDevice user = activeAt(source, "handoffLatencyUser", 12);
		user.setDestinationAp(destination);
		user.setHandoffStatus(true);
		MobileController.setRand(new Random(1));

		CloudSim.send(user.getId(), source.getId(), 900.0,
			MobileEvents.START_HANDOFF, user);
		double finalClock = CloudSim.startSimulation();

		assertEquals("The configured setup delay must control completion time",
			900.0, finalClock, DELTA);
		assertEquals(900.0, user.getTimeFinishHandoff(), DELTA);
		assertSame(destination, user.getSourceAp());
		assertEquals("C1: source topology latency must remain immutable",
			4.0, source.getUplinkLatency(), DELTA);
		assertEquals("C1: destination topology latency must remain immutable",
			6.0, destination.getUplinkLatency(), DELTA);
		assertEquals("C1: handoff setup must not replace wireless propagation",
			2.0, user.getWirelessAssociation().getPropagationDelay()
				.toMilliseconds(), DELTA);
		assertEquals(2.0,
			NetworkTopology.getDelay(destination.getId(), user.getId()), DELTA);
	}

	@Test
	public void c2FinishedUsersMustRemainInTerminalMetricInventory() {
		requireKnownDefectRun("C2");
		MobileDevice first = mobile("finishedFirst", 6);
		MobileDevice second = mobile("finishedSecond", 7);
		List<MobileDevice> activeUsers = new ArrayList<MobileDevice>(
			Arrays.asList(first, second));
		MobileController.setSmartThings(activeUsers);

		NextStep.finishMobility(first);
		NextStep.finishMobility(second);

		assertTrue("The active-user view should be empty",
			MobileController.getSmartThings().isEmpty());
		SimulationMetricsSnapshot snapshot = SimulationMetricsSnapshot.capture(
			Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), MobileController.getSmartThings(),
			new MyStatistics(), new TimeKeeper(), 20.0, 0L);
		assertEquals("C2: both completed users must be captured exactly once",
			2, snapshot.getMobileDevices().size());
	}

	@Test
	public void c3SimultaneousHandoffsMustNotOverbookOneSlot() {
		ApDevice firstSource = accessPoint("firstSource", 1, 4.0);
		ApDevice secondSource = accessPoint("secondSource", 1, 4.0);
		ApDevice destination = accessPoint("oneSlotDestination", 1, 4.0);
		MobileDevice first = activeAt(firstSource, "firstHandoffUser", 8);
		MobileDevice second = activeAt(secondSource, "secondHandoffUser", 9);
		first.setDestinationAp(destination);
		second.setDestinationAp(destination);
		first.setHandoffStatus(true);
		second.setHandoffStatus(true);
		MobileController.setRand(new Random(1));

		CloudSim.send(first.getId(), firstSource.getId(), 1.0,
			MobileEvents.START_HANDOFF, first);
		CloudSim.send(second.getId(), secondSource.getId(), 1.0,
			MobileEvents.START_HANDOFF, second);
		CloudSim.startSimulation();

		assertEquals("C3: a one-slot AP must accept only one handoff",
			1, destination.getSmartThings().size());
		assertTrue("C3: equal-time contention must have a deterministic winner",
			destination.getSmartThings().contains(first));
		assertFalse(destination.getSmartThings().contains(second));
		assertSame(secondSource, second.getSourceAp());
		assertNull(second.getDestinationAp());
	}

	@Test
	public void c3NonPositiveCapacityMustFailTopologyValidation() {
		FogDevice server = new FogDevice("capacityServer", 0, 0, 0);
		for (int capacity : new int[] { 0, -1 }) {
			ApDevice invalid = accessPoint("invalidCapacityAp" + capacity,
				capacity, 4.0);
			try {
				new TopologyService().connectAccessPoints(
					Collections.singletonList(server),
					Collections.singletonList(invalid), new Random(1));
				fail("C3: AP capacity " + capacity
					+ " should be rejected before topology mutation");
			}
			catch (IllegalArgumentException expected) {
				// Expected once topology validation enforces positive capacity.
			}
		}
	}

	@Test
	public void c4FinishedUserMustReleaseItsVmResources() {
		requireKnownDefectRun("C4");
		RegistrationFixture fixture = new RegistrationFixture(10);
		assertTrue(fixture.controller.activateMobileUser(fixture.user));
		Vm registeredVm = fixture.user.getVmMobileDevice();
		assertTrue(fixture.server.getHost().getVmList().contains(registeredVm));

		new MobileAssociationService().finish(fixture.user);

		assertEquals(MobileDeviceLifecycle.FINISHED,
			fixture.user.getLifecycleState());
		assertFalse("C4: the departed VM must release host capacity",
			fixture.server.getHost().getVmList().contains(registeredVm));
		assertFalse(fixture.server.getSmartThingsWithVm().contains(fixture.user));
		assertNull(fixture.user.getVmMobileDevice());
		assertNull(fixture.user.getVmLocalServerCloudlet());
	}

	@Test
	public void c5TopologyFailureMustRollBackEarlierAttachments() {
		requireKnownDefectRun("C5");
		FogDevice server = new FogDevice("transactionServer", 0, 0, 0);
		ApDevice first = accessPoint("firstValidAp", 1, 4.0);
		List<ApDevice> accessPoints = new ArrayList<ApDevice>();
		accessPoints.add(first);
		accessPoints.add(null);

		try {
			new TopologyService().connectAccessPoints(
				Collections.singletonList(server), accessPoints, new Random(1));
			fail("Expected invalid topology input to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}

		assertNull("C5: failed assembly must restore the AP",
			first.getServerCloudlet());
		assertEquals(-1, first.getParentId());
		assertTrue("C5: failed assembly must restore the server",
			server.getApDevices().isEmpty());
		assertFalse("C5: failed assembly must not publish network links",
			NetworkTopology.isNetworkEnabled());
	}

	@Test
	public void c5LateRegistrationFailureMustRollBackEveryMutation() {
		requireKnownDefectRun("C5");
		RegistrationFixture fixture = new RegistrationFixture(11);
		Set<MobileSensor> malformedSensors = new HashSet<MobileSensor>();
		malformedSensors.add(null);
		fixture.user.setSensors(malformedSensors);

		try {
			fixture.controller.activateMobileUser(fixture.user);
			fail("Expected injected late registration failure");
		}
		catch (RuntimeException expected) {
			// The null sensor injects failure after broker and VM registration.
		}

		boolean unchanged = fixture.brokers.isEmpty()
			&& fixture.server.getHost().getVmList().isEmpty()
			&& fixture.server.getSmartThingsWithVm().isEmpty()
			&& fixture.controller.getApplications().isEmpty()
			&& fixture.moduleMapping.getModuleMapping().isEmpty()
			&& fixture.accessPoint.getSmartThings().isEmpty()
			&& fixture.server.getSmartThings().isEmpty()
			&& fixture.user.getSourceAp() == null
			&& fixture.user.getSourceServerCloudlet() == null
			&& fixture.user.getVmMobileDevice() == null
			&& fixture.user.getVmLocalServerCloudlet() == null;
		assertTrue("C5: registration failure left partially committed state",
			unchanged);
	}

	private static void requireKnownDefectRun(String finding) {
		assumeTrue(finding + " remains an opt-in red regression until fixed",
			Boolean.getBoolean(RUN_KNOWN_DEFECTS_PROPERTY));
	}

	private static MobileDevice mobile(String name, int id) {
		return new MobileDevice(name, 0, 0, id, 0, 0);
	}

	private static ApDevice accessPoint(String name, int capacity,
		double topologyLatency) {
		return new ApDevice(name, 0, 0, 0, 100 * 1024 * 1024,
			200, capacity, 100 * 1024 * 1024, topologyLatency);
	}

	private static MobileDevice activeAt(ApDevice source, String name, int id) {
		MobileDevice mobileDevice = mobile(name, id);
		mobileDevice.setUplinkLatency(2.0);
		source.associateMobileDevice(mobileDevice);
		mobileDevice.setSourceAp(source);
		mobileDevice.setLifecycleState(MobileDeviceLifecycle.ACTIVE);
		mobileDevice.setStatus(true);
		return mobileDevice;
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

	private static final class RegistrationFixture {
		private final List<FogDevice> servers = new ArrayList<FogDevice>();
		private final List<ApDevice> accessPoints = new ArrayList<ApDevice>();
		private final List<MobileDevice> users = new ArrayList<MobileDevice>();
		private final List<FogBroker> brokers = new ArrayList<FogBroker>();
		private final ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
		private final FogDevice server;
		private final ApDevice accessPoint;
		private final MobileDevice user;
		private final MobileController controller;

		private RegistrationFixture(int userId) {
			configureAppExample(servers, accessPoints, users);
			AppExample.addServerCloudlet(servers, new Coordinate(), 0);
			server = servers.get(0);
			server.setCoord(0, 0);
			accessPoint = accessPoint("registrationAp" + userId, 1, 4.0);
			server.attachAccessPoint(accessPoint);
			accessPoints.add(accessPoint);
			AppExample.addSmartThing(users, new Coordinate(), 0);
			user = users.get(0);
			user.setCoord(0, 0);
			user.setTravelTimeId(0);
			MobileUserRegistration.preparePendingUser(user);
			controller = new MobileController("registrationController" + userId,
				servers, accessPoints, users, brokers, moduleMapping,
				Policies.FIXED_MIGRATION_POINT, Policies.LOWEST_LATENCY, 1,
				new Coordinate(), 1, false);
		}
	}
}
