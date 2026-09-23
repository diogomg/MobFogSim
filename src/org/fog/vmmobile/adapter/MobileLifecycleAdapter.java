package org.fog.vmmobile.adapter;

import org.fog.entities.MobileDevice;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.port.MobileLifecyclePort;

/** Compatibility adapter for lifecycle operations that are still static. */
public final class MobileLifecycleAdapter implements MobileLifecyclePort {
	public static final MobileLifecycleAdapter INSTANCE =
		new MobileLifecycleAdapter();

	private MobileLifecycleAdapter() {
	}

	@Override
	public void beginEntry(MobileDevice mobileDevice) {
		MobileUserRegistration.beginEntry(mobileDevice);
	}

	@Override
	public void awaitAssociation(MobileDevice mobileDevice) {
		MobileUserRegistration.awaitAssociation(mobileDevice);
	}

	@Override
	public void activatePeripherals(MobileDevice mobileDevice) {
		MobileUserRegistration.activatePeripherals(mobileDevice);
	}

	@Override
	public void disconnectNetwork(MobileDevice mobileDevice) {
		MobileUserRegistration.disconnectNetwork(mobileDevice);
	}

	@Override
	public void finishMobility(MobileDevice mobileDevice) {
		NextStep.finishMobility(mobileDevice);
	}
}
