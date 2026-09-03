package org.fog.vmmigration;

import org.fog.entities.MobileDevice;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MovementDirection;

public interface VmMigrationTechnique {

	public void verifyPoints(MobileDevice smartThing,
		MovementDirection relativePosition);

	public default void verifyPoints(MobileDevice smartThing,
		int relativePosition) {
		verifyPoints(smartThing, MovementDirection.fromLegacy(relativePosition));
	}

	public double migrationTimeFunction(double vmSize, double bandwidth);

	/** Returns the bytes placed on the transport network by this technique. */
	public double getTransferSizeBytes(double vmSizeMebibytes);

	/** Returns propagation/setup delay in milliseconds, excluding preparation. */
	public double getFixedDelayMillis(MobileDevice smartThing);

	public boolean migPointPolicyFunction(MigrationPointPolicy policy,
		MobileDevice smartThing);

	public default boolean migPointPolicyFunction(int policy,
		MobileDevice smartThing) {
		return migPointPolicyFunction(MigrationPointPolicy.fromLegacy(policy),
			smartThing);
	}

	public boolean migrationPointFunction(double distance, double migTime, int speed);

	public boolean migrationPointFunction(double distance);

	public boolean migrationZoneFunction(MovementDirection smartThingDirection,
		MovementDirection zoneDirection);

	public default boolean migrationZoneFunction(int smartThingDirection,
		int zoneDirection) {
		return migrationZoneFunction(
			MovementDirection.fromLegacy(smartThingDirection),
			MovementDirection.fromLegacy(zoneDirection));
	}

}
