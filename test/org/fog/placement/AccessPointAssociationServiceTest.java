package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Optional;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.HandoffReservation;
import org.fog.entities.HandoffCoordinator;
import org.fog.entities.HandoffUnlockRequest;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.utils.SimulationDuration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.constants.MobileEvents;
import org.junit.Before;
import org.junit.Test;

public class AccessPointAssociationServiceTest {

	private static final double DELTA = 0.000001;
	private final AccessPointAssociationService service =
		new AccessPointAssociationService();

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MyStatistics.setInstance(new MyStatistics());
		MobileController.setRand(new Random(1));
	}

	@Test
	public void simultaneousRequestsHaveOneDeterministicWinner() {
		ApDevice firstSource = accessPoint("first-source", 1);
		ApDevice secondSource = accessPoint("second-source", 1);
		ApDevice destination = accessPoint("destination", 1);
		MobileDevice first = activeAt(firstSource, "first", 1);
		MobileDevice second = activeAt(secondSource, "second", 2);

		Optional<HandoffReservation> firstReservation = reserve(first,
			firstSource, destination);
		Optional<HandoffReservation> secondReservation = reserve(second,
			secondSource, destination);

		assertTrue(firstReservation.isPresent());
		assertFalse(secondReservation.isPresent());
		assertSame(destination, first.getDestinationAp());
		assertNull(second.getDestinationAp());
		assertEquals(1, destination.getHandoffReservationCount());

		CloudSim.send(first.getId(), firstSource.getId(), 10.0,
			MobileEvents.START_HANDOFF, firstReservation.get());
		CloudSim.startSimulation();

		assertSame(destination, first.getSourceAp());
		assertSame(secondSource, second.getSourceAp());
		assertEquals(1, destination.getSmartThings().size());
		assertEquals(0, destination.getHandoffReservationCount());
		assertEquals(10.0, first.getTimeFinishHandoff(), DELTA);
	}

	@Test
	public void cancellationInvalidatesGenerationAndReleasesCapacity() {
		ApDevice firstSource = accessPoint("first-source", 1);
		ApDevice secondSource = accessPoint("second-source", 1);
		ApDevice destination = accessPoint("destination", 1);
		MobileDevice first = activeAt(firstSource, "first", 1);
		MobileDevice second = activeAt(secondSource, "second", 2);
		HandoffReservation firstReservation = reserve(first, firstSource,
			destination).get();
		long reservedGeneration = firstReservation.getAssociationGeneration();

		assertFalse(reserve(second, secondSource, destination).isPresent());
		assertTrue(first.cancelPendingHandoffReservation());
		assertTrue(first.getNetworkAssociationGeneration() > reservedGeneration);
		assertNull(first.getPendingHandoffReservation());
		assertNull(first.getDestinationAp());
		assertFalse(first.isHandoffStatus());
		assertFalse(first.isLockedToHandoff());

		Optional<HandoffReservation> replacement = reserve(second,
			secondSource, destination);
		assertTrue(replacement.isPresent());
		assertSame(second, replacement.get().getMobileDevice());
		assertEquals(1, destination.getHandoffReservationCount());
	}

	@Test
	public void staleCompletionReleasesItsSlotWithoutMovingTheUser() {
		ApDevice firstSource = accessPoint("first-source", 1);
		ApDevice secondSource = accessPoint("second-source", 1);
		ApDevice destination = accessPoint("destination", 1);
		MobileDevice first = activeAt(firstSource, "first", 1);
		MobileDevice second = activeAt(secondSource, "second", 2);
		HandoffReservation stale = reserve(first, firstSource, destination).get();
		first.advanceNetworkAssociationGeneration();

		assertFalse(destination.completeHandoffReservation(stale));
		assertEquals(0, destination.getHandoffReservationCount());
		assertSame(firstSource, first.getSourceAp());
		assertNull(first.getPendingHandoffReservation());
		assertNull(first.getDestinationAp());
		assertTrue(reserve(second, secondSource, destination).isPresent());
	}

	@Test
	public void completionIsIdempotentAndCannotConsumeAnotherReservation() {
		ApDevice firstSource = accessPoint("first-source", 1);
		ApDevice secondSource = accessPoint("second-source", 1);
		ApDevice destination = accessPoint("destination", 1);
		MobileDevice first = activeAt(firstSource, "first", 1);
		MobileDevice second = activeAt(secondSource, "second", 2);
		HandoffReservation firstReservation = reserve(first, firstSource,
			destination).get();

		assertTrue(destination.completeHandoffReservation(firstReservation));
		Optional<HandoffReservation> secondReservation = reserve(second,
			secondSource, destination);
		assertTrue(secondReservation.isPresent());
		assertFalse(destination.completeHandoffReservation(firstReservation));
		assertSame(secondReservation.get(),
			second.getPendingHandoffReservation());
		assertEquals(1, destination.getHandoffReservationCount());
	}

	@Test
	public void staleUnlockCannotUnlockANewerReservation() {
		ApDevice source = accessPoint("source", 1);
		ApDevice destination = accessPoint("destination", 1);
		MobileDevice mobileDevice = activeAt(source, "mobile", 1);
		HandoffReservation first = reserve(mobileDevice, source, destination).get();
		HandoffUnlockRequest staleUnlock = new HandoffUnlockRequest(mobileDevice,
			first.getAssociationGeneration());
		assertTrue(mobileDevice.cancelPendingHandoffReservation());
		HandoffReservation second = reserve(mobileDevice, source, destination).get();

		assertFalse(new HandoffCoordinator().unlock(staleUnlock));
		assertTrue(mobileDevice.isLockedToHandoff());
		assertSame(second, mobileDevice.getPendingHandoffReservation());
	}

	private Optional<HandoffReservation> reserve(MobileDevice mobileDevice,
		ApDevice source, ApDevice destination) {
		assertSame(source, mobileDevice.getSourceAp());
		return service.reserveClosestHandoff(
			Arrays.asList(source, destination), mobileDevice,
			SimulationDuration.ofMilliseconds(10.0),
			SimulationDuration.ofMilliseconds(40.0));
	}

	private static MobileDevice activeAt(ApDevice source, String name, int id) {
		MobileDevice mobileDevice = new MobileDevice(name, 0, 0, id, 0, 0);
		mobileDevice.setUplinkLatency(2.0);
		source.associateMobileDevice(mobileDevice);
		mobileDevice.setSourceAp(source);
		mobileDevice.setLifecycleState(MobileDeviceLifecycle.ACTIVE);
		mobileDevice.setStatus(true);
		return mobileDevice;
	}

	private static ApDevice accessPoint(String name, int capacity) {
		ApDevice accessPoint = new ApDevice(name, 0, 0, 0,
			1000.0, 0.0, capacity, 1000.0, 4.0);
		accessPoint.setDownlinkBandwidth(1000.0);
		return accessPoint;
	}
}
