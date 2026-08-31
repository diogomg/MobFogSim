package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public class PrepareLiveMigration extends AbstractMigrationPreparation {
	private static final int MAXIMUM_CONNECTION_ATTEMPTS = 5;

	public PrepareLiveMigration() {
		super(MAXIMUM_CONNECTION_ATTEMPTS);
	}

	public PrepareLiveMigration(ConnectionAttemptPolicy connectionAttemptPolicy) {
		super(MAXIMUM_CONNECTION_ATTEMPTS, connectionAttemptPolicy);
	}

	@Override
	protected double getPreparationWorkload(MobileDevice smartThing) {
		return smartThing.getVmMobileDevice().getSize() * 1024 * 1024;
	}
}
