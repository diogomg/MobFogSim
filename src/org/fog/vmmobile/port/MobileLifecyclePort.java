package org.fog.vmmobile.port;

import org.fog.entities.MobileDevice;

/** Transitional boundary for legacy mobile-user lifecycle operations. */
public interface MobileLifecyclePort {
	void beginEntry(MobileDevice mobileDevice);

	void awaitAssociation(MobileDevice mobileDevice);

	void activatePeripherals(MobileDevice mobileDevice);

	void disconnectNetwork(MobileDevice mobileDevice);

	void finishMobility(MobileDevice mobileDevice);
}
