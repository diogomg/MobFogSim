package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.Vm;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.policy.MovementDirection;

/** Immutable policy result that can be committed only against its input snapshot. */
public final class MigrationDecision {
	private final MobileDevice mobileDevice;
	private final FogDevice destination;
	private final MigrationPointEvaluation pointEvaluation;
	private final List<MigrationPrediction> predictions;
	private final String reason;
	private final ApDevice expectedSourceAp;
	private final FogDevice expectedSourceServer;
	private final FogDevice expectedVmHost;
	private final FogDevice expectedDestination;
	private final Vm expectedVm;
	private final MobileDeviceLifecycle expectedLifecycle;
	private final MobileSession.MigrationState expectedMigrationState;
	private final long expectedAssociationGeneration;
	private final long expectedMigrationGeneration;
	private final int expectedX;
	private final int expectedY;
	private final MovementDirection expectedDirection;
	private final int expectedSpeed;
	private final boolean expectedMigrationLock;

	private MigrationDecision(MobileDevice mobileDevice, FogDevice destination,
		MigrationPointEvaluation pointEvaluation,
		List<MigrationPrediction> predictions, String reason) {
		if (mobileDevice == null || predictions == null) {
			throw new IllegalArgumentException(
				"A migration decision requires a mobile device and predictions");
		}
		this.mobileDevice = mobileDevice;
		this.destination = destination;
		this.pointEvaluation = pointEvaluation;
		this.predictions = Collections.unmodifiableList(
			new ArrayList<MigrationPrediction>(predictions));
		this.reason = reason;
		this.expectedSourceAp = mobileDevice.getSourceAp();
		this.expectedSourceServer = mobileDevice.getSourceServerCloudlet();
		this.expectedVmHost = mobileDevice.getVmLocalServerCloudlet();
		this.expectedDestination = mobileDevice.getDestinationServerCloudlet();
		this.expectedVm = mobileDevice.getVmMobileDevice();
		this.expectedLifecycle = mobileDevice.getLifecycleState();
		this.expectedMigrationState = mobileDevice.getSession().getMigration();
		this.expectedAssociationGeneration = mobileDevice.getSession()
			.getAssociationGeneration();
		this.expectedMigrationGeneration = mobileDevice.getSession()
			.getMigrationGeneration();
		this.expectedX = mobileDevice.getCoord().getCoordX();
		this.expectedY = mobileDevice.getCoord().getCoordY();
		this.expectedDirection = mobileDevice.getMovementDirection();
		this.expectedSpeed = mobileDevice.getSpeed();
		this.expectedMigrationLock = mobileDevice.isLockedToMigration();
	}

	public static MigrationDecision stay(MobileDevice mobileDevice,
		MigrationPointEvaluation pointEvaluation,
		List<MigrationPrediction> predictions, String reason) {
		return new MigrationDecision(mobileDevice, null, pointEvaluation,
			predictions, reason);
	}

	public static MigrationDecision migrate(MobileDevice mobileDevice,
		FogDevice destination, MigrationPointEvaluation pointEvaluation,
		List<MigrationPrediction> predictions) {
		if (destination == null || pointEvaluation == null
			|| !pointEvaluation.allowsMigration()) {
			throw new IllegalArgumentException(
				"A migration decision requires an allowed point and destination");
		}
		return new MigrationDecision(mobileDevice, destination, pointEvaluation,
			predictions, null);
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public boolean shouldMigrate() {
		return destination != null;
	}

	public FogDevice getDestination() {
		return destination;
	}

	public MigrationPointEvaluation getPointEvaluation() {
		return pointEvaluation;
	}

	public List<MigrationPrediction> getPredictions() {
		return predictions;
	}

	public String getReason() {
		return reason;
	}

	public long getExpectedMigrationGeneration() {
		return expectedMigrationGeneration;
	}

	public MobileSession.MigrationState getExpectedMigrationState() {
		return expectedMigrationState;
	}

	/** Returns false when any policy input changed after evaluation. */
	public boolean isCurrent() {
		return mobileDevice.getSourceAp() == expectedSourceAp
			&& mobileDevice.getSourceServerCloudlet() == expectedSourceServer
			&& mobileDevice.getVmLocalServerCloudlet() == expectedVmHost
			&& mobileDevice.getDestinationServerCloudlet() == expectedDestination
			&& mobileDevice.getVmMobileDevice() == expectedVm
			&& mobileDevice.getLifecycleState() == expectedLifecycle
			&& mobileDevice.getSession().getMigration() == expectedMigrationState
			&& mobileDevice.getSession().getAssociationGeneration()
				== expectedAssociationGeneration
			&& mobileDevice.getSession().getMigrationGeneration()
				== expectedMigrationGeneration
			&& mobileDevice.getCoord().getCoordX() == expectedX
			&& mobileDevice.getCoord().getCoordY() == expectedY
			&& mobileDevice.getMovementDirection() == expectedDirection
			&& mobileDevice.getSpeed() == expectedSpeed
			&& mobileDevice.isLockedToMigration() == expectedMigrationLock;
	}
}
