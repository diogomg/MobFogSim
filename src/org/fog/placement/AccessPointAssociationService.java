package org.fog.placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fog.entities.ApDevice;
import org.fog.entities.HandoffReservation;
import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.utils.SimulationDuration;
import org.fog.vmmobile.adapter.CloudSimAdapter;
import org.fog.vmmobile.SimulationClock;

/** Selects an AP and reserves its capacity as one handoff-acceptance step. */
public final class AccessPointAssociationService {
	private final SimulationClock clock;

	/** Compatibility constructor; run code injects this port via SimulationServices. */
	public AccessPointAssociationService() {
		this(CloudSimAdapter.INSTANCE);
	}

	public AccessPointAssociationService(SimulationClock clock) {
		if (clock == null) {
			throw new IllegalArgumentException("Simulation clock cannot be null");
		}
		this.clock = clock;
	}

	public Optional<HandoffReservation> reserveClosestHandoff(
		List<ApDevice> accessPoints, MobileDevice mobileDevice,
		SimulationDuration handoffSetupDuration,
		SimulationDuration reservationLifetime) {
		if (accessPoints == null || mobileDevice == null
			|| handoffSetupDuration == null || reservationLifetime == null) {
			throw new IllegalArgumentException(
				"Handoff reservation inputs cannot be null");
		}
		if (mobileDevice.getSourceAp() == null) {
			return Optional.empty();
		}
		if (reservationLifetime.toMilliseconds()
			< handoffSetupDuration.toMilliseconds()) {
			throw new IllegalArgumentException(
				"A handoff reservation must cover its setup duration");
		}

		List<ApDevice> candidates = new ArrayList<ApDevice>();
		for (ApDevice accessPoint : accessPoints) {
			if (accessPoint == null) {
				throw new IllegalArgumentException(
					"Access-point list cannot contain null entries");
			}
			if (accessPoint != mobileDevice.getSourceAp()) {
				candidates.add(accessPoint);
			}
		}

		double expiresAtMillis = clock.simulationTimeMillis()
			+ reservationLifetime.toMilliseconds();
		while (!candidates.isEmpty()) {
			Optional<ApDevice> closest =
				Distances.findClosestAp(candidates, mobileDevice);
			if (!closest.isPresent()) {
				return Optional.empty();
			}
			ApDevice destination = closest.get();
			Optional<HandoffReservation> reservation =
				destination.reserveHandoffSlot(mobileDevice,
					mobileDevice.getSourceAp(), expiresAtMillis,
					handoffSetupDuration);
			if (reservation.isPresent()) {
				mobileDevice.setDestinationAp(destination);
				return reservation;
			}
			candidates.remove(destination);
		}
		return Optional.empty();
	}
}
