package org.fog.vmmigration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/**
 * Coordinates migration transfer lifecycle independently of a FogDevice's
 * event switch. The entity remains responsible only for scheduling any fixed
 * post-transfer delay returned here.
 */
public final class MigrationCoordinator {
	/** Minimal event port implemented by the owning simulation entity. */
	@FunctionalInterface
	public interface EventDispatcher {
		void send(int destinationId, double delay, int eventTag, Object payload);

		default void sendNow(int destinationId, int eventTag, Object payload) {
			send(destinationId, 0.0, eventTag, payload);
		}
	}

	/** Immutable result of accepting a migration-completion event. */
	public static final class Completion {
		private final MobileDevice mobileDevice;
		private final double remainingFixedDelayMillis;

		private Completion(MobileDevice mobileDevice,
			double remainingFixedDelayMillis) {
			this.mobileDevice = mobileDevice;
			this.remainingFixedDelayMillis = remainingFixedDelayMillis;
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
		}

		public double getRemainingFixedDelayMillis() {
			return remainingFixedDelayMillis;
		}

		public boolean requiresFixedDelay() {
			return remainingFixedDelayMillis > 0.0;
		}
	}

	/** Starts byte transfer only for a live, non-aborted migration. */
	public boolean startTransfer(MigrationTransferSpec transferSpec,
		List<MobileDevice> activeMobileDevices) {
		if (transferSpec == null) {
			throw new IllegalArgumentException("Migration transfer cannot be null");
		}
		if (activeMobileDevices == null) {
			throw new IllegalArgumentException("Active mobile-device list cannot be null");
		}
		MobileDevice mobileDevice = transferSpec.getMobileDevice();
		if (!activeMobileDevices.contains(mobileDevice)
			|| mobileDevice.isAbortMigration()
			|| (!mobileDevice.isMigStatus() && !mobileDevice.isMigStatusLive())) {
			NetworkSlicing.releaseBandwidth(mobileDevice);
			return false;
		}
		NetworkSlicing.startMigrationTransfer(transferSpec);
		return true;
	}

	/**
	 * Accepts a current scheduler completion or a legacy mobile-device payload.
	 * A {@code null} result identifies a stale scheduler event.
	 */
	public Completion completeTransfer(Object payload) {
		if (payload instanceof NetworkSlicing.MigrationTransferCompletion) {
			NetworkSlicing.MigrationTransferResult result =
				NetworkSlicing.completeMigrationTransfer(
					(NetworkSlicing.MigrationTransferCompletion) payload);
			if (result == null) {
				return null;
			}
			return new Completion(result.getMobileDevice(),
				result.getFixedDelayMillis());
		}
		if (!(payload instanceof MobileDevice)) {
			throw new IllegalArgumentException(
				"Migration completion payload has an unsupported type");
		}
		MobileDevice mobileDevice = (MobileDevice) payload;
		NetworkSlicing.releaseBandwidth(mobileDevice);
		return new Completion(mobileDevice, 0.0);
	}

	/** Clears reservation and transient state for an aborted migration. */
	public void abort(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		NetworkSlicing.releaseBandwidth(mobileDevice);
		MyStatistics statistics = MyStatistics.getInstance();
		statistics.discardOpenIntervals(mobileDevice.getMyId());
		mobileDevice.setMigStatus(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setLockedToMigration(false);
		mobileDevice.setTimeFinishDeliveryVm(-1.0);
		mobileDevice.setAbortMigration(true);
		mobileDevice.setDestinationServerCloudlet(
			mobileDevice.getVmLocalServerCloudlet());
	}

	/** Clears the migration lock after its policy-defined hold time. */
	public void unlock(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		mobileDevice.setLockedToMigration(false);
		mobileDevice.setTimeFinishDeliveryVm(-1);
		LogMobile.debug("MigrationCoordinator.java", mobileDevice.getName()
			+ " had the migration unlocked");
	}

	/** Records that a decision did not start another migration. */
	public void noMigration(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		String state = mobileDevice.isLockedToMigration()
			? " already is in migration Process or the migration is locked"
			: " is not in Migrate";
		LogMobile.debug("MigrationCoordinator.java", "NO MIGRATE: "
			+ mobileDevice.getName() + state);
	}

	/** Prepares and, for non-live policies, schedules migration byte transfer. */
	public void prepare(MobileDevice mobileDevice,
		List<MobileDevice> activeMobileDevices, BeforeMigration preparation,
		int migrationPolicy, EventDispatcher events) {
		prepare(mobileDevice, activeMobileDevices, preparation,
			MigrationTechniquePolicy.fromLegacy(migrationPolicy), events);
	}

	public void prepare(MobileDevice mobileDevice,
		List<MobileDevice> activeMobileDevices, BeforeMigration preparation,
		MigrationTechniquePolicy migrationPolicy, EventDispatcher events) {
		if (mobileDevice == null || activeMobileDevices == null
			|| preparation == null || events == null) {
			throw new IllegalArgumentException(
				"Migration preparation dependencies cannot be null");
		}
		if (!activeMobileDevices.contains(mobileDevice)) {
			events.sendNow(mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.ABORT_MIGRATION, mobileDevice);
			return;
		}
		if (mobileDevice.getSourceAp() == null || mobileDevice.isMigStatus()) {
			events.sendNow(mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.NO_MIGRATION, mobileDevice);
			clearRejectedPreparation(mobileDevice);
			return;
		}

		double preparationDelay = preparation.dataprepare(mobileDevice);
		System.out.println("delayProcess" + preparationDelay);
		if (preparationDelay < 0.0) {
			return;
		}
		if (migrationPolicy == MigrationTechniquePolicy.LIVE_MIGRATION) {
			mobileDevice.setPostCopyStatus(true);
			mobileDevice.setTimeStartLiveMigration(CloudSim.clock());
		} else {
			mobileDevice.setMigStatus(true);
			MyStatistics.getInstance().startWithoutVmTime(
				mobileDevice.getMyId(), CloudSim.clock());
			mobileDevice.setTimeFinishDeliveryVm(-1.0);
			MigrationTransferSpec transfer = new MigrationTransferSpec(
				mobileDevice.getVmLocalServerCloudlet(),
				mobileDevice.getDestinationServerCloudlet(), mobileDevice,
				mobileDevice.getMigrationTechnique().getTransferSizeBytes(
					mobileDevice.getVmMobileDevice().getSize()),
				mobileDevice.getMigrationTechnique().getFixedDelayMillis(mobileDevice),
				preparationDelay, mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.START_MIGRATION);
			events.send(transfer.getSource().getId(),
				transfer.getPreparationDelayMillis(),
				MobileEvents.START_MIGRATION_TRANSFER, transfer);
		}
		mobileDevice.setLockedToMigration(true);
	}

	/** Evaluates eligible users and emits the resulting migration events. */
	public void decide(Collection<MobileDevice> mobileDevices, int coordinatorEntityId,
		EventDispatcher events) {
		if (mobileDevices == null || events == null) {
			throw new IllegalArgumentException(
				"Migration decision dependencies cannot be null");
		}
		for (MobileDevice mobileDevice : mobileDevices) {
			if (mobileDevice.getSourceAp() == null
				|| mobileDevice.isLockedToMigration()
				|| mobileDevice.getVmLocalServerCloudlet() == null
				|| !mobileDevice.getVmLocalServerCloudlet().getMigrationStrategy()
					.shouldMigrate(mobileDevice)) {
				events.sendNow(coordinatorEntityId, MobileEvents.NO_MIGRATION,
					mobileDevice);
				continue;
			}
			if (mobileDevice.getVmLocalServerCloudlet()
				.equals(mobileDevice.getDestinationServerCloudlet())) {
				events.sendNow(coordinatorEntityId, MobileEvents.NO_MIGRATION,
					mobileDevice);
				continue;
			}

			System.out.println("====================ToMigrate================== "
				+ mobileDevice.getName() + " " + mobileDevice.getId());
			LogMobile.debug("MigrationCoordinator.java", "Distance between "
				+ mobileDevice.getName() + " and "
				+ mobileDevice.getSourceAp().getName() + ": "
				+ Distances.checkDistance(mobileDevice.getCoord(),
					mobileDevice.getSourceAp().getCoord()));
			System.out.println("Migration time: " + mobileDevice.getMigTime());
			LogMobile.debug("MigrationCoordinator.java", "Made the decisionMigration for "
				+ mobileDevice.getName());
			events.sendNow(mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.TO_MIGRATION, mobileDevice);
			clearTimingStarts(mobileDevice);
			mobileDevice.setLockedToMigration(true);
			mobileDevice.setTimeFinishDeliveryVm(-1.0);
			writeDecision(mobileDevice);
		}
	}

	/** Calculates the live-migration bytes not copied during pre-copy. */
	public double remainingLiveMigrationBytes(MobileDevice mobileDevice,
		double baselineBandwidthBitsPerSecond, double nowMillis) {
		if (mobileDevice == null || mobileDevice.getMigrationTechnique() == null
			|| mobileDevice.getVmMobileDevice() == null) {
			throw new IllegalArgumentException(
				"Live migration requires a mobile device, VM, and technique");
		}
		if (!Double.isFinite(baselineBandwidthBitsPerSecond)
			|| baselineBandwidthBitsPerSecond < 0.0
			|| !Double.isFinite(nowMillis) || nowMillis < 0.0) {
			throw new IllegalArgumentException(
				"Live-migration bandwidth and time must be finite and non-negative");
		}
		double elapsedMillis = Math.max(0.0,
			nowMillis - mobileDevice.getTimeStartLiveMigration());
		double copiedBytes = baselineBandwidthBitsPerSecond * elapsedMillis
			/ (8.0 * 1000.0);
		double totalBytes = mobileDevice.getMigrationTechnique()
			.getTransferSizeBytes(mobileDevice.getVmMobileDevice().getSize());
		return Math.max(0.0, totalBytes - copiedBytes);
	}

	private static void clearRejectedPreparation(MobileDevice mobileDevice) {
		clearTimingStarts(mobileDevice);
		mobileDevice.setMigStatus(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setLockedToMigration(false);
		mobileDevice.setTimeFinishDeliveryVm(-1.0);
		mobileDevice.setAbortMigration(true);
	}

	private static void clearTimingStarts(MobileDevice mobileDevice) {
		MyStatistics.getInstance().discardOpenIntervals(mobileDevice.getMyId());
	}

	private static void writeDecision(MobileDevice mobileDevice) {
		System.out.println("MIGRATION " + mobileDevice.getMyId() + " Position: "
			+ mobileDevice.getCoord().getCoordX() + ", "
			+ mobileDevice.getCoord().getCoordY() + " Direction: "
			+ mobileDevice.getDirection() + " Speed: " + mobileDevice.getSpeed());
		try (PrintWriter writer = RunOutputManager.getInstance()
			.newDetailedPrintWriter(mobileDevice.getMyId() + "migration.txt", true)) {
			writer.println(mobileDevice.getMyId() + "\t"
				+ mobileDevice.getCoord().getCoordX() + "\t"
				+ mobileDevice.getCoord().getCoordY() + "\t"
				+ mobileDevice.getDirection() + "\t" + mobileDevice.getSpeed() + "\t"
				+ mobileDevice.getVmLocalServerCloudlet().getName() + "\t"
				+ mobileDevice.getDestinationServerCloudlet().getName() + "\t"
				+ CloudSim.clock() + "\t" + mobileDevice.getMigTime() + "\t"
				+ (CloudSim.clock() + mobileDevice.getMigTime()));
		} catch (IOException error) {
			throw new IllegalStateException("Could not record migration decision for "
				+ mobileDevice.getName(), error);
		}
	}
}
