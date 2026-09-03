package org.fog.vmmigration;

import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MovementDirection;

public class ContainerVM implements VmMigrationTechnique {
	private MigrationPointPolicy migPointPolicy;

	public ContainerVM(int migPointPolicy) {
		this(MigrationPointPolicy.fromLegacy(migPointPolicy));
	}

	public ContainerVM(MigrationPointPolicy migPointPolicy) {
		setMigrationPointPolicy(migPointPolicy);
	}

	@Override
	public void verifyPoints(MobileDevice smartThing,
		MovementDirection relativePosition) {
		// either (0 or 1) -> policies
		smartThing.setMigPoint(migPointPolicyFunction(getMigrationPointPolicy()
			, smartThing));// 0 -> fixed or 1 -> with speed
		smartThing.setMigZone(migrationZoneFunction(
			smartThing.getMovementDirection(), relativePosition));
	}

	@Override
	public double migrationTimeFunction(double vmSize, double bandwidth) {
		return MigrationTransferSpec.transferTimeMillis(
			getTransferSizeBytes(vmSize), bandwidth);
	}

	@Override
	public double getTransferSizeBytes(double vmSizeMebibytes) {
		return MigrationTransferSpec.mebibytesToBytes(vmSizeMebibytes)
			* MaxAndMin.SIZE_CONTAINER;
	}

	@Override
	public double getFixedDelayMillis(MobileDevice smartThing) {
		return MigrationFixedDelay.calculateMillis(smartThing);
	}

	@Override
	public boolean migPointPolicyFunction(MigrationPointPolicy policy,
		MobileDevice smartThing) {
		if (policy == null) {
			throw new IllegalArgumentException("Migration point policy cannot be null");
		}
		double distance = Distances.checkDistance(smartThing.getSourceAp().getCoord(),
			smartThing.getCoord());
		double bandwidth = NetworkSlicing.getSliceBandwidth(
			smartThing.getVmLocalServerCloudlet(), smartThing.getDestinationServerCloudlet(),
			smartThing.getNetworkSliceId());

		double transferTimeMillis = migrationTimeFunction(
			smartThing.getVmMobileDevice().getSize(), bandwidth);
		double fixedDelayMillis = getFixedDelayMillis(smartThing);
		smartThing.setMigTime(transferTimeMillis + fixedDelayMillis);

		System.out.println("Container VM " + smartThing.getMigTime() + " size: "
				+ smartThing.getVmMobileDevice().getSize() + " bandwidth: " + bandwidth
				+ " tempo " + transferTimeMillis
				+ " cloudlet uplink latency " + smartThing.getVmLocalServerCloudlet().getUplinkLatency()
				+ " fixed migration delay " + fixedDelayMillis);

		if (policy == MigrationPointPolicy.FIXED) {
			return migrationPointFunction(distance);
		}
		else {
			// relative according smartThing's speed
			return migrationPointFunction(distance, smartThing.getMigTime(), smartThing.getSpeed());
		}
	}

	@Override
	public boolean migrationPointFunction(double distance, double migTime,
		int speed) {
		// ((migTime/1000.0) * speed);//minimal distance to migration
		double newDistance = (double) ((migTime) / 1000.0) * speed;
		// the boundary is on the middle between the two APs
		newDistance += MaxAndMin.MAX_DISTANCE_TO_HANDOFF / 2.0;
		if ((distance >= MaxAndMin.AP_COVERAGE - newDistance || distance >= MaxAndMin.AP_COVERAGE
			- MaxAndMin.MAX_DISTANCE_TO_HANDOFF) && distance < MaxAndMin.AP_COVERAGE)
			return true;
		else
			return false;
	}

	@Override
	public boolean migrationPointFunction(double distance) {
		//Right now it is not consider the user's speed -> it is a fixed point
		if (distance >= MaxAndMin.AP_COVERAGE - MaxAndMin.MIG_POINT &&
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
