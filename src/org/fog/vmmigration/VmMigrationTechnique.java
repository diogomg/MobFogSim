package org.fog.vmmigration;

import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MovementDirection;

public interface VmMigrationTechnique {

	/** Evaluates migration timing and geometry without mutating the mobile device. */
	public default MigrationPointEvaluation evaluatePoints(
		MobileDevice smartThing, MovementDirection relativePosition) {
		if (smartThing == null || relativePosition == null
			|| smartThing.getSourceAp() == null
			|| smartThing.getVmLocalServerCloudlet() == null
			|| smartThing.getVmMobileDevice() == null) {
			throw new IllegalArgumentException(
				"Migration-point evaluation requires a complete mobile snapshot");
		}
		double bandwidth = NetworkSlicing.getSliceBandwidth(
			smartThing.getVmLocalServerCloudlet(),
			smartThing.getDestinationServerCloudlet(),
			smartThing.getNetworkSliceId());
		double migrationTimeMillis = migrationTimeFunction(
			smartThing.getVmMobileDevice().getSize(), bandwidth)
			+ getFixedDelayMillis(smartThing);
		double distance = Distances.checkDistance(
			smartThing.getSourceAp().getCoord(), smartThing.getCoord());
		boolean migrationPoint = getMigrationPointPolicy()
			== MigrationPointPolicy.FIXED
			? migrationPointFunction(distance)
			: migrationPointFunction(distance, migrationTimeMillis,
				smartThing.getSpeed());
		boolean migrationZone = migrationZoneFunction(
			smartThing.getMovementDirection(), relativePosition);
		return new MigrationPointEvaluation(migrationPoint, migrationZone,
			migrationTimeMillis);
	}

	/** Compatibility adapter for callers that still expect in-place flags. */
	public default void verifyPoints(MobileDevice smartThing,
		MovementDirection relativePosition) {
		MigrationPointEvaluation evaluation = evaluatePoints(smartThing,
			relativePosition);
		smartThing.setMigPoint(evaluation.isMigrationPoint());
		smartThing.setMigZone(evaluation.isMigrationZone());
		smartThing.setMigTime(evaluation.getMigrationTimeMillis());
	}

	public default void verifyPoints(MobileDevice smartThing,
		int relativePosition) {
		verifyPoints(smartThing, MovementDirection.fromLegacy(relativePosition));
	}

	public double migrationTimeFunction(double vmSize, double bandwidth);

	/** Returns the bytes placed on the transport network by this technique. */
	public double getTransferSizeBytes(double vmSizeMebibytes);

	/** Returns propagation/setup delay in milliseconds, excluding preparation. */
	public double getFixedDelayMillis(MobileDevice smartThing);

	public default boolean migPointPolicyFunction(MigrationPointPolicy policy,
		MobileDevice smartThing) {
		if (policy == null || smartThing == null
			|| smartThing.getSourceAp() == null
			|| smartThing.getVmLocalServerCloudlet() == null
			|| smartThing.getVmMobileDevice() == null) {
			throw new IllegalArgumentException(
				"Migration-point policy requires a complete mobile snapshot");
		}
		double bandwidth = NetworkSlicing.getSliceBandwidth(
			smartThing.getVmLocalServerCloudlet(),
			smartThing.getDestinationServerCloudlet(),
			smartThing.getNetworkSliceId());
		double migrationTimeMillis = migrationTimeFunction(
			smartThing.getVmMobileDevice().getSize(), bandwidth)
			+ getFixedDelayMillis(smartThing);
		double distance = Distances.checkDistance(
			smartThing.getSourceAp().getCoord(), smartThing.getCoord());
		return policy == MigrationPointPolicy.FIXED
			? migrationPointFunction(distance)
			: migrationPointFunction(distance, migrationTimeMillis,
				smartThing.getSpeed());
	}

	public default boolean migPointPolicyFunction(int policy,
		MobileDevice smartThing) {
		return migPointPolicyFunction(MigrationPointPolicy.fromLegacy(policy),
			smartThing);
	}

	public boolean migrationPointFunction(double distance, double migTime, int speed);

	public boolean migrationPointFunction(double distance);

	public boolean migrationZoneFunction(MovementDirection smartThingDirection,
		MovementDirection zoneDirection);

	public MigrationPointPolicy getMigrationPointPolicy();

	public default boolean migrationZoneFunction(int smartThingDirection,
		int zoneDirection) {
		return migrationZoneFunction(
			MovementDirection.fromLegacy(smartThingDirection),
			MovementDirection.fromLegacy(zoneDirection));
	}

}
