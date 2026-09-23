package org.fog.vmmigration;

import org.fog.entities.MobileDevice;
import org.fog.utils.MigrationTransferSpec;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MovementDirection;

public class LiveMigration implements VmMigrationTechnique {
	private MigrationPointPolicy migPointPolicy;

	public LiveMigration(int migPointPolicy) {
		this(MigrationPointPolicy.fromLegacy(migPointPolicy));
	}

	public LiveMigration(MigrationPointPolicy migPointPolicy) {
		setMigrationPointPolicy(migPointPolicy);
	}

	@Override
	public double migrationTimeFunction(double vmSize, double bandwidth) {
		return MigrationTransferSpec.transferTimeMillis(
			getTransferSizeBytes(vmSize), bandwidth);
	}

	@Override
	public double getTransferSizeBytes(double vmSizeMebibytes) {
		return MigrationTransferSpec.mebibytesToBytes(vmSizeMebibytes) * 1.3;
	}

	@Override
	public double getFixedDelayMillis(MobileDevice smartThing) {
		return MigrationFixedDelay.calculateMillis(smartThing);
	}

	@Override
	public boolean migrationPointFunction(double distance, double migTime,
		int speed) {
		// ((migTime/1000.0) * speed);//minimal distance to migration
		double newDistance = (double) (migTime / 1000.0) * speed;
		newDistance += MaxAndMin.MIG_POINT;
		if ((distance >= MaxAndMin.AP_COVERAGE - newDistance || distance >= MaxAndMin.AP_COVERAGE
			- MaxAndMin.MAX_DISTANCE_TO_HANDOFF) && distance < MaxAndMin.AP_COVERAGE)
			return true;
		else
			return false;
	}

	@Override
	public boolean migrationPointFunction(double distance) {

		// Right now it is not consider the user's speed -> it is a fixed point
		if (distance >= MaxAndMin.AP_COVERAGE - MaxAndMin.LIVE_MIG_POINT &&
			distance < MaxAndMin.AP_COVERAGE)
			return true;
		else
			return false;
	}

	@Override
	public boolean migrationZoneFunction(MovementDirection smartThingDirection,
		MovementDirection zoneDirection) {
		return smartThingDirection != null
			&& smartThingDirection.containsInMigrationCone(zoneDirection);
	}

	public int getMigPointPolicy() {
		return migPointPolicy.legacyValue();
	}

	public void setMigPointPolicy(int migPointPolicy) {
		setMigrationPointPolicy(MigrationPointPolicy.fromLegacy(migPointPolicy));
	}

	public MigrationPointPolicy getMigrationPointPolicy() {
		return migPointPolicy;
	}

	public void setMigrationPointPolicy(MigrationPointPolicy migPointPolicy) {
		if (migPointPolicy == null) {
			throw new IllegalArgumentException("Migration point policy cannot be null");
		}
		this.migPointPolicy = migPointPolicy;
	}

}
