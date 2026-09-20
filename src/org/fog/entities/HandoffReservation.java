package org.fog.entities;

import org.fog.utils.SimulationDuration;

/** Immutable ownership token for one delayed access-point handoff. */
public final class HandoffReservation {

	private final long reservationId;
	private final MobileDevice mobileDevice;
	private final ApDevice sourceAccessPoint;
	private final ApDevice destinationAccessPoint;
	private final long associationGeneration;
	private final double createdAtMillis;
	private final double expiresAtMillis;
	private final SimulationDuration handoffSetupDuration;

	HandoffReservation(long reservationId, MobileDevice mobileDevice,
		ApDevice sourceAccessPoint, ApDevice destinationAccessPoint,
		long associationGeneration, double createdAtMillis,
		double expiresAtMillis, SimulationDuration handoffSetupDuration) {
		if (reservationId <= 0L || mobileDevice == null
			|| sourceAccessPoint == null || destinationAccessPoint == null
			|| associationGeneration <= 0L || handoffSetupDuration == null
			|| !Double.isFinite(createdAtMillis)
			|| !Double.isFinite(expiresAtMillis)
			|| expiresAtMillis < createdAtMillis) {
			throw new IllegalArgumentException("Invalid handoff reservation");
		}
		this.reservationId = reservationId;
		this.mobileDevice = mobileDevice;
		this.sourceAccessPoint = sourceAccessPoint;
		this.destinationAccessPoint = destinationAccessPoint;
		this.associationGeneration = associationGeneration;
		this.createdAtMillis = createdAtMillis;
		this.expiresAtMillis = expiresAtMillis;
		this.handoffSetupDuration = handoffSetupDuration;
	}

	public long getReservationId() {
		return reservationId;
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public ApDevice getSourceAccessPoint() {
		return sourceAccessPoint;
	}

	public ApDevice getDestinationAccessPoint() {
		return destinationAccessPoint;
	}

	public long getAssociationGeneration() {
		return associationGeneration;
	}

	public double getCreatedAtMillis() {
		return createdAtMillis;
	}

	public double getExpiresAtMillis() {
		return expiresAtMillis;
	}

	public SimulationDuration getHandoffSetupDuration() {
		return handoffSetupDuration;
	}

	public boolean isExpiredAt(double simulationTimeMillis) {
		return simulationTimeMillis > expiresAtMillis;
	}
}
