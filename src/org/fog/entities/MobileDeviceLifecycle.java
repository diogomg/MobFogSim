package org.fog.entities;

/**
 * Explicit lifecycle for a mobile user whose mobility trace can begin after
 * simulation start and can temporarily lie outside access-point coverage.
 */
public enum MobileDeviceLifecycle {
	/** Waiting for the first mobility-trace timestamp. */
	SCHEDULED,
	/** Entered the simulation but has not yet registered through an access point. */
	SEARCHING_FOR_AP,
	/** Registered and currently associated with an access point and server. */
	ACTIVE,
	/** Registered previously but temporarily has no network association. */
	DISCONNECTED,
	/** Mobility has ended and no further lifecycle events should be processed. */
	FINISHED;

	public boolean acceptsMobilityUpdates() {
		return this == SEARCHING_FOR_AP || this == ACTIVE || this == DISCONNECTED;
	}

	public boolean hasRegisteredApplication() {
		return this == ACTIVE || this == DISCONNECTED;
	}
}
