package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

/** Immutable generation token carried by delayed migration workflow events. */
public final class MigrationEvent {
	public enum Stage {
		GENERIC,
		PREPARE,
		FIXED_DELAY_COMPLETE,
		START_DELIVERY,
		INSTALL_VM,
		MIGRATE_APPLICATION,
		ABORT,
		UNLOCK
	}

	private final MobileDevice mobileDevice;
	private final long migrationGeneration;
	private final Stage stage;

	public MigrationEvent(MobileDevice mobileDevice, long migrationGeneration) {
		this(mobileDevice, migrationGeneration, Stage.GENERIC);
	}

	public MigrationEvent(MobileDevice mobileDevice, long migrationGeneration,
		Stage stage) {
		if (mobileDevice == null || migrationGeneration <= 0L || stage == null) {
			throw new IllegalArgumentException(
				"A migration event requires a mobile device and generation");
		}
		this.mobileDevice = mobileDevice;
		this.migrationGeneration = migrationGeneration;
		this.stage = stage;
	}

	public static MigrationEvent current(MobileDevice mobileDevice) {
		return current(mobileDevice, Stage.GENERIC);
	}

	public static MigrationEvent current(MobileDevice mobileDevice, Stage stage) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		return new MigrationEvent(mobileDevice,
			mobileDevice.getSession().getMigrationGeneration(), stage);
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public long getMigrationGeneration() {
		return migrationGeneration;
	}

	public Stage getStage() {
		return stage;
	}

	public boolean isCurrent() {
		return mobileDevice.getSession().isCurrentMigration(migrationGeneration);
	}
}
