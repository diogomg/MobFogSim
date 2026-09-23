package org.fog.vmmigration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.SimulationClock;
import org.fog.vmmobile.adapter.CloudSimAdapter;
import org.fog.vmmobile.adapter.LegacySimulationAdapters;
import org.fog.vmmobile.adapter.NetworkSliceAdapter;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.port.MobileStatisticsPort;
import org.fog.vmmobile.port.NetworkSlicePort;
import org.fog.vmmobile.port.SimulationEventLog;
import org.fog.vmmobile.port.SimulationOutput;
import org.fog.vmmobile.policy.MembershipAction;

/**
 * Coordinates migration transfer lifecycle independently of a FogDevice's
 * event switch. The entity remains responsible only for scheduling any fixed
 * post-transfer delay returned here.
 */
public final class MigrationCoordinator {
	private final SimulationClock clock;
	private final NetworkSlicePort networkSlices;
	private final MobileStatisticsPort statistics;
	private final SimulationEventLog events;
	private final SimulationOutput output;
	private final Random decisionRandom;

	/** Compatibility constructor; run code injects these ports via SimulationServices. */
	public MigrationCoordinator() {
		this(CloudSimAdapter.INSTANCE, NetworkSliceAdapter.INSTANCE,
			LegacySimulationAdapters.statistics(), LegacySimulationAdapters.events(),
			LegacySimulationAdapters.output(),
			LegacySimulationAdapters.migrationRandom());
	}

	public MigrationCoordinator(SimulationClock clock,
		NetworkSlicePort networkSlices, MobileStatisticsPort statistics,
		SimulationEventLog events, SimulationOutput output) {
		this(clock, networkSlices, statistics, events, output, new Random(0L));
	}

	public MigrationCoordinator(SimulationClock clock,
		NetworkSlicePort networkSlices, MobileStatisticsPort statistics,
		SimulationEventLog events, SimulationOutput output,
		Random decisionRandom) {
		if (clock == null || networkSlices == null || statistics == null
			|| events == null || output == null || decisionRandom == null) {
			throw new IllegalArgumentException(
				"Migration coordinator dependencies cannot be null");
		}
		this.clock = clock;
		this.networkSlices = networkSlices;
		this.statistics = statistics;
		this.events = events;
		this.output = output;
		this.decisionRandom = decisionRandom;
	}
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
		private final long migrationGeneration;
		private final double remainingFixedDelayMillis;

		private Completion(MobileDevice mobileDevice,
			long migrationGeneration, double remainingFixedDelayMillis) {
			this.mobileDevice = mobileDevice;
			this.migrationGeneration = migrationGeneration;
			this.remainingFixedDelayMillis = remainingFixedDelayMillis;
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
		}

		public double getRemainingFixedDelayMillis() {
			return remainingFixedDelayMillis;
		}

		public MigrationEvent event() {
			return new MigrationEvent(mobileDevice, migrationGeneration,
				MigrationEvent.Stage.FIXED_DELAY_COMPLETE);
		}

		public boolean requiresFixedDelay() {
			return remainingFixedDelayMillis > 0.0;
		}
	}

	public enum StartDisposition {
		START,
		ABORT,
		IGNORE
	}

	public enum DecisionCommit {
		MIGRATION,
		STAY,
		REJECTED
	}

	/** Guarded decision for the entity-side VM migration dispatch. */
	public static final class StartPlan {
		private final StartDisposition disposition;
		private final MobileDevice mobileDevice;
		private final String reason;

		private StartPlan(StartDisposition disposition, MobileDevice mobileDevice,
			String reason) {
			this.disposition = disposition;
			this.mobileDevice = mobileDevice;
			this.reason = reason;
		}

		public StartDisposition getDisposition() {
			return disposition;
		}

		public MobileDevice getMobileDevice() {
			return mobileDevice;
		}

		public String getReason() {
			return reason;
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
		if (transferSpec.getMigrationGeneration() > 0L
			&& !mobileDevice.getSession().isCurrentMigration(
				transferSpec.getMigrationGeneration())) {
			return false;
		}
		if (!activeMobileDevices.contains(mobileDevice)
			|| mobileDevice.isAbortMigration()
			|| (!mobileDevice.isMigStatus() && !mobileDevice.isMigStatusLive())) {
			networkSlices.releaseBandwidth(mobileDevice);
			return false;
		}
		long generation = ensureMigrationPreparation(mobileDevice);
		if (mobileDevice.getSession().getMigration()
			!= MobileSession.MigrationState.TRANSFERRING
			&& !mobileDevice.getSession().beginMigrationTransfer(generation)) {
			networkSlices.releaseBandwidth(mobileDevice);
			return false;
		}
		networkSlices.startMigrationTransfer(transferSpec);
		return true;
	}

	/**
	 * Accepts a current scheduler completion or a legacy mobile-device payload.
	 * A {@code null} result identifies a stale scheduler event.
	 */
	public Completion completeTransfer(Object payload) {
		if (payload instanceof NetworkSlicing.MigrationTransferCompletion) {
			NetworkSlicing.MigrationTransferResult result =
				networkSlices.completeMigrationTransfer(
					(NetworkSlicing.MigrationTransferCompletion) payload);
			if (result == null) {
				return null;
			}
			MobileDevice completedMobile = result.getMobileDevice();
			long generation = result.getMigrationGeneration();
			if (generation <= 0L) {
				generation = completedMobile.getSession().getMigrationGeneration();
			}
			if (generation > 0L
				&& !completedMobile.getSession().isCurrentMigration(generation)) {
				return null;
			}
			if (result.getFixedDelayMillis() > 0.0) {
				completedMobile.getSession().awaitMigrationFixedDelay(
					generation);
			}
			return new Completion(completedMobile, generation,
				result.getFixedDelayMillis());
		}
		if (payload instanceof MigrationEvent) {
			MigrationEvent event = (MigrationEvent) payload;
			if (!event.isCurrent()) {
				return null;
			}
			if (event.getStage() == MigrationEvent.Stage.FIXED_DELAY_COMPLETE
				&& !event.getMobileDevice().getSession()
					.finishMigrationFixedDelay(event.getMigrationGeneration())) {
				return null;
			}
			if (event.getStage() != MigrationEvent.Stage.FIXED_DELAY_COMPLETE
				&& event.getStage() != MigrationEvent.Stage.START_DELIVERY
				&& event.getStage() != MigrationEvent.Stage.GENERIC) {
				return null;
			}
		}
		MobileDevice mobileDevice = currentMobile(payload);
		if (mobileDevice == null) {
			if (payload instanceof MigrationEvent) {
				return null;
			}
			throw new IllegalArgumentException(
				"Migration completion payload has an unsupported type");
		}
		networkSlices.releaseBandwidth(mobileDevice);
		return new Completion(mobileDevice,
			mobileDevice.getSession().getMigrationGeneration(), 0.0);
	}

	/** Clears reservation and transient state for an aborted migration. */
	public void abort(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		mobileDevice.cancelPendingHandoffReservation();
		networkSlices.releaseBandwidth(mobileDevice);
		statistics.discardOpenIntervals(mobileDevice.getMyId());
		mobileDevice.setMigStatus(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setLockedToMigration(false);
		mobileDevice.setTimeFinishDeliveryVm(-1.0);
		mobileDevice.setAbortMigration(true);
		mobileDevice.getSession().abortMigration();
		mobileDevice.setDestinationServerCloudlet(
			mobileDevice.getVmLocalServerCloudlet());
	}

	/** Clears the migration lock after its policy-defined hold time. */
	public void unlock(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return;
		}
		mobileDevice.setLockedToMigration(false);
		if (mobileDevice.getSession().getMigration()
			!= MobileSession.MigrationState.IDLE) {
			mobileDevice.getSession().unlockMigration();
		}
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
		if (mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.DECIDED) {
			mobileDevice.getSession().unlockMigration();
		}
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
				MobileEvents.ABORT_MIGRATION,
				event(mobileDevice, MigrationEvent.Stage.ABORT));
			return;
		}
		if (mobileDevice.getSourceAp() == null || mobileDevice.isMigStatus()) {
			clearRejectedPreparation(mobileDevice);
			events.sendNow(mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.NO_MIGRATION, event(mobileDevice));
			return;
		}

		double preparationDelay = preparation.dataprepare(mobileDevice);
		this.events.trace("MigrationCoordinator", () ->
			"Migration preparation delay " + preparationDelay);
		if (preparationDelay < 0.0) {
			return;
		}
		mobileDevice.getSession().beginMigrationPreparation();
		if (migrationPolicy == MigrationTechniquePolicy.LIVE_MIGRATION) {
			mobileDevice.setPostCopyStatus(true);
			mobileDevice.setTimeStartLiveMigration(clock.simulationTimeMillis());
		} else {
			mobileDevice.setMigStatus(true);
			statistics.startWithoutVm(mobileDevice.getMyId(),
				clock.simulationTimeMillis());
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
			FogDevice accessServer = mobileDevice.getSourceServerCloudlet();
			DecisionMigration strategy = accessServer == null
				? null : accessServer.getMigrationStrategy();
			if (mobileDevice.getSourceAp() == null
				|| accessServer == null
				|| mobileDevice.isLockedToMigration()
				|| mobileDevice.getVmLocalServerCloudlet() == null
				|| strategy == null) {
				events.sendNow(coordinatorEntityId, MobileEvents.NO_MIGRATION,
					mobileDevice);
				continue;
			}

			MigrationDecision decision = strategy.evaluate(mobileDevice,
				new MigrationDecisionContext(clock.simulationTimeMillis(),
					decisionRandom, mobileDevices,
					VmDestinationPolicy.getDestination()));
			DecisionCommit commit = commitDecision(decision, mobileDevices);
			if (commit != DecisionCommit.REJECTED) {
				recordDecisionObservations(decision);
			}
			if (commit != DecisionCommit.MIGRATION) {
				if (decision.getReason() != null) {
					this.events.trace("MigrationCoordinator",
						() -> decision.getReason());
				}
				events.sendNow(coordinatorEntityId, MobileEvents.NO_MIGRATION,
					mobileDevice);
				continue;
			}

			this.events.detail("MigrationCoordinator", () ->
				"Migration selected for " + mobileDevice.getName() + " (entity "
					+ mobileDevice.getId() + ")");
			LogMobile.debug("MigrationCoordinator.java", "Distance between "
				+ mobileDevice.getName() + " and "
				+ mobileDevice.getSourceAp().getName() + ": "
				+ Distances.checkDistance(mobileDevice.getCoord(),
					mobileDevice.getSourceAp().getCoord()));
			this.events.trace("MigrationCoordinator", () ->
				"Migration time " + mobileDevice.getMigTime());
			LogMobile.debug("MigrationCoordinator.java", "Made the decisionMigration for "
				+ mobileDevice.getName());
			events.sendNow(mobileDevice.getVmLocalServerCloudlet().getId(),
				MobileEvents.TO_MIGRATION,
				event(mobileDevice, MigrationEvent.Stage.PREPARE));
			clearTimingStarts(mobileDevice);
			writeDecision(mobileDevice);
		}
	}

	/**
	 * Atomically applies an immutable policy result when its complete input
	 * snapshot is still current. No external event or output is emitted here.
	 */
	public DecisionCommit commitDecision(MigrationDecision decision,
		Collection<MobileDevice> activeMobileDevices) {
		if (decision == null || activeMobileDevices == null) {
			throw new IllegalArgumentException(
				"Migration commit inputs cannot be null");
		}
		MobileDevice mobileDevice = decision.getMobileDevice();
		if (!activeMobileDevices.contains(mobileDevice)
			|| !decision.isCurrent()) {
			return DecisionCommit.REJECTED;
		}
		boolean startsMigration = decision.shouldMigrate()
			&& decision.getDestination()
				!= mobileDevice.getVmLocalServerCloudlet();
		if (startsMigration) {
			if (mobileDevice.getSourceAp() == null
				|| mobileDevice.getVmLocalServerCloudlet() == null
				|| mobileDevice.getVmMobileDevice() == null
				|| mobileDevice.isLockedToMigration()
				|| !mobileDevice.getSession().decideMigration(
					decision.getExpectedMigrationGeneration(),
					decision.getExpectedMigrationState())) {
				return DecisionCommit.REJECTED;
			}
		}

		applyDecisionObservations(decision);
		if (!startsMigration) {
			return DecisionCommit.STAY;
		}
		mobileDevice.setDestinationServerCloudlet(decision.getDestination());
		mobileDevice.setLockedToMigration(true);
		mobileDevice.setTimeFinishDeliveryVm(-1.0);
		return DecisionCommit.MIGRATION;
	}

	private static void applyDecisionObservations(MigrationDecision decision) {
		MobileDevice mobileDevice = decision.getMobileDevice();
		MigrationPointEvaluation pointEvaluation =
			decision.getPointEvaluation();
		if (pointEvaluation != null) {
			mobileDevice.setMigPoint(pointEvaluation.isMigrationPoint());
			mobileDevice.setMigZone(pointEvaluation.isMigrationZone());
			mobileDevice.setMigTime(pointEvaluation.getMigrationTimeMillis());
		}
		List<MigrationPrediction> predictions = decision.getPredictions();
		if (!predictions.isEmpty()) {
			MigrationPrediction last = predictions.get(predictions.size() - 1);
			mobileDevice.setFutureCoord(last.getAdjustedX(), last.getAdjustedY());
		}
	}

	private static void recordDecisionObservations(MigrationDecision decision) {
		for (MigrationPrediction prediction : decision.getPredictions()) {
			Migration.recordPrediction(prediction);
		}
	}

	/** Starts the live-transfer substate after pre-copy reaches its handoff point. */
	public boolean beginLiveTransfer(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			return false;
		}
		long generation = ensureMigrationPreparation(mobileDevice);
		if (!mobileDevice.getSession().beginMigrationTransfer(generation)) {
			return false;
		}
		mobileDevice.setMigStatusLive(true);
		return true;
	}

	/** Decides whether a completed transfer may dispatch VM delivery. */
	public StartPlan planStart(MobileDevice mobileDevice,
		Collection<MobileDevice> activeMobileDevices) {
		if (mobileDevice == null || activeMobileDevices == null) {
			throw new IllegalArgumentException(
				"Migration start dependencies cannot be null");
		}
		if (!activeMobileDevices.contains(mobileDevice)) {
			return new StartPlan(StartDisposition.IGNORE, mobileDevice,
				"mobile device is no longer active");
		}
		if (mobileDevice.isAbortMigration()) {
			mobileDevice.setAbortMigration(false);
			return new StartPlan(StartDisposition.IGNORE, mobileDevice,
				"migration was already aborted");
		}
		if (mobileDevice.getSourceAp() == null) {
			return new StartPlan(StartDisposition.ABORT, mobileDevice,
				"mobile device has no access-point association");
		}
		if (mobileDevice.getVmLocalServerCloudlet() == null
			|| mobileDevice.getDestinationServerCloudlet() == null
			|| mobileDevice.getVmMobileDevice() == null
			|| mobileDevice.getDestinationServerCloudlet().getHost() == null) {
			return new StartPlan(StartDisposition.ABORT, mobileDevice,
				"migration requires current and destination hosts plus a VM");
		}
		if (!mobileDevice.getSession().scheduleMigrationInstallation(
			mobileDevice.getSession().getMigrationGeneration())) {
			return new StartPlan(StartDisposition.IGNORE, mobileDevice,
				"migration installation is already scheduled or obsolete");
		}
		return new StartPlan(StartDisposition.START, mobileDevice, null);
	}

	/** Commits VM-host membership and aggregate state as one coordinator step. */
	public boolean installVm(MobileDevice mobileDevice,
		Collection<MobileDevice> activeMobileDevices) {
		if (mobileDevice == null || activeMobileDevices == null
			|| !activeMobileDevices.contains(mobileDevice)) {
			return false;
		}
		FogDevice source = mobileDevice.getVmLocalServerCloudlet();
		FogDevice destination = mobileDevice.getDestinationServerCloudlet();
		long generation = mobileDevice.getSession().getMigrationGeneration();
		MobileSession.MigrationState state =
			mobileDevice.getSession().getMigration();
		if (source == null || destination == null
			|| (state != MobileSession.MigrationState.PREPARING
				&& state != MobileSession.MigrationState.TRANSFERRING
				&& state != MobileSession.MigrationState.FIXED_DELAY
				&& state != MobileSession.MigrationState.READY_TO_INSTALL
				&& state != MobileSession.MigrationState.INSTALLING)) {
			return false;
		}
		source.setSmartThingsWithVm(mobileDevice, MembershipAction.REMOVE);
		mobileDevice.setVmLocalServerCloudlet(destination);
		mobileDevice.setDestinationServerCloudlet(null);
		destination.setSmartThingsWithVm(mobileDevice, MembershipAction.ADD);
		if (!mobileDevice.getSession().installMigration(generation)) {
			throw new IllegalStateException(
				"Validated migration installation was rejected");
		}
		return true;
	}

	public boolean isInstallationScheduled(MobileDevice mobileDevice) {
		return mobileDevice != null
			&& mobileDevice.getSession().getMigration()
				== MobileSession.MigrationState.INSTALLING
			&& mobileDevice.getVmLocalServerCloudlet() != null
			&& mobileDevice.getDestinationServerCloudlet() != null
			&& mobileDevice.getVmMobileDevice() != null;
	}

	/** Resolves a current generation-bearing payload, retaining raw compatibility. */
	public MobileDevice currentMobile(Object payload) {
		if (payload instanceof MigrationEvent) {
			MigrationEvent event = (MigrationEvent) payload;
			return event.isCurrent() ? event.getMobileDevice() : null;
		}
		return payload instanceof MobileDevice ? (MobileDevice) payload : null;
	}

	public MobileDevice currentMobile(Object payload,
		MigrationEvent.Stage expectedStage) {
		if (payload instanceof MigrationEvent) {
			MigrationEvent event = (MigrationEvent) payload;
			if (event.getStage() != MigrationEvent.Stage.GENERIC
				&& event.getStage() != expectedStage) {
				return null;
			}
		}
		return currentMobile(payload);
	}

	public Object event(MobileDevice mobileDevice) {
		return event(mobileDevice, MigrationEvent.Stage.GENERIC);
	}

	public Object event(MobileDevice mobileDevice, MigrationEvent.Stage stage) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		return mobileDevice.getSession().getMigrationGeneration() > 0L
			? MigrationEvent.current(mobileDevice, stage) : mobileDevice;
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

	private void clearRejectedPreparation(MobileDevice mobileDevice) {
		clearTimingStarts(mobileDevice);
		mobileDevice.setMigStatus(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setLockedToMigration(false);
		mobileDevice.setTimeFinishDeliveryVm(-1.0);
		mobileDevice.setAbortMigration(true);
		mobileDevice.getSession().abortMigration();
	}

	private long ensureMigrationPreparation(MobileDevice mobileDevice) {
		MobileSession.MigrationState state = mobileDevice.getSession().getMigration();
		if (state == MobileSession.MigrationState.PREPARING) {
			return mobileDevice.getSession().getMigrationGeneration();
		}
		if (state == MobileSession.MigrationState.DECIDED
			|| state == MobileSession.MigrationState.IDLE
			|| state == MobileSession.MigrationState.INSTALLED
			|| state == MobileSession.MigrationState.ABORTED) {
			return mobileDevice.getSession().beginMigrationPreparation();
		}
		return mobileDevice.getSession().getMigrationGeneration();
	}

	private void clearTimingStarts(MobileDevice mobileDevice) {
		statistics.discardOpenIntervals(mobileDevice.getMyId());
	}

	private void writeDecision(MobileDevice mobileDevice) {
		events.detail("MigrationCoordinator", () ->
			"MIGRATION " + mobileDevice.getMyId() + " Position: "
				+ mobileDevice.getCoord().getCoordX() + ", "
				+ mobileDevice.getCoord().getCoordY() + " Direction: "
				+ mobileDevice.getDirection() + " Speed: " + mobileDevice.getSpeed());
		try (PrintWriter writer = output.newDetailedPrintWriter(
			mobileDevice.getMyId() + "migration.txt", true)) {
			writer.println(mobileDevice.getMyId() + "\t"
				+ mobileDevice.getCoord().getCoordX() + "\t"
				+ mobileDevice.getCoord().getCoordY() + "\t"
				+ mobileDevice.getDirection() + "\t" + mobileDevice.getSpeed() + "\t"
				+ mobileDevice.getVmLocalServerCloudlet().getName() + "\t"
				+ mobileDevice.getDestinationServerCloudlet().getName() + "\t"
				+ clock.simulationTimeMillis() + "\t" + mobileDevice.getMigTime() + "\t"
				+ (clock.simulationTimeMillis() + mobileDevice.getMigTime()));
		} catch (IOException error) {
			throw new IllegalStateException("Could not record migration decision for "
				+ mobileDevice.getName(), error);
		}
	}
}
