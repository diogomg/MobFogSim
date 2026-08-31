package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public class PrepareCompleteVM extends AbstractMigrationPreparation {
	private static final int MAXIMUM_CONNECTION_ATTEMPTS = 3;

	public PrepareCompleteVM() {
		super(MAXIMUM_CONNECTION_ATTEMPTS);
	}

	public PrepareCompleteVM(ConnectionAttemptPolicy connectionAttemptPolicy) {
		super(MAXIMUM_CONNECTION_ATTEMPTS, connectionAttemptPolicy);
	}

	@Override
	protected double getPreparationWorkload(MobileDevice smartThing) {
		return smartThing.getVmMobileDevice().getSize() * 1024 * 1024 * 8;
	}
}
