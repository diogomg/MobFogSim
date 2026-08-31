package org.fog.vmmigration;

import org.fog.entities.MobileDevice;
import org.fog.vmmobile.constants.MaxAndMin;

public class PrepareContainerVM extends AbstractMigrationPreparation {
	private static final int MAXIMUM_CONNECTION_ATTEMPTS = 5;

	public PrepareContainerVM() {
		super(MAXIMUM_CONNECTION_ATTEMPTS);
	}

	public PrepareContainerVM(ConnectionAttemptPolicy connectionAttemptPolicy) {
		super(MAXIMUM_CONNECTION_ATTEMPTS, connectionAttemptPolicy);
	}

	@Override
	protected double getPreparationWorkload(MobileDevice smartThing) {
		return smartThing.getVmMobileDevice().getSize() * 1024 * 1024
			* MaxAndMin.PROCESS_CONTAINER;
	}
}
