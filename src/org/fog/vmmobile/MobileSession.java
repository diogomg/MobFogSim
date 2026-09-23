package org.fog.vmmobile;

import org.fog.entities.MobileDeviceLifecycle;

/**
 * Guarded workflow state for one mobile user, independent of CloudSim events.
 * Generation identifiers let coordinators reject delayed events from an older
 * handoff or migration attempt.
 */
public final class MobileSession {
	public enum HandoffState {
		IDLE,
		RESERVED,
		TRANSFERRING,
		COMPLETED,
		CANCELLED
	}

	public enum MigrationState {
		IDLE,
		DECIDED,
		PREPARING,
		TRANSFERRING,
		FIXED_DELAY,
		READY_TO_INSTALL,
		INSTALLING,
		INSTALLED,
		ABORTED
	}

	private MobileDeviceLifecycle lifecycle = MobileDeviceLifecycle.ACTIVE;
	private HandoffState handoff = HandoffState.IDLE;
	private MigrationState migration = MigrationState.IDLE;
	private long associationGeneration;
	private long migrationGeneration;

	public MobileDeviceLifecycle getLifecycle() {
		return lifecycle;
	}

	public HandoffState getHandoff() {
		return handoff;
	}

	public MigrationState getMigration() {
		return migration;
	}

	public long getAssociationGeneration() {
		return associationGeneration;
	}

	public long getMigrationGeneration() {
		return migrationGeneration;
	}

	public void schedule() {
		ensureNotFinished("schedule");
		lifecycle = MobileDeviceLifecycle.SCHEDULED;
	}

	public void beginEntry() {
		requireLifecycle(MobileDeviceLifecycle.SCHEDULED, "begin entry");
		lifecycle = MobileDeviceLifecycle.SEARCHING_FOR_AP;
	}

	public void awaitAssociation(boolean applicationRegistered) {
		if (lifecycle == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		lifecycle = applicationRegistered
			? MobileDeviceLifecycle.DISCONNECTED
			: MobileDeviceLifecycle.SEARCHING_FOR_AP;
	}

	public void activate() {
		if (lifecycle != MobileDeviceLifecycle.SEARCHING_FOR_AP
			&& lifecycle != MobileDeviceLifecycle.DISCONNECTED
			&& lifecycle != MobileDeviceLifecycle.ACTIVE) {
			throw new IllegalStateException("Cannot activate a mobile session from "
				+ lifecycle);
		}
		lifecycle = MobileDeviceLifecycle.ACTIVE;
	}

	public void finish() {
		lifecycle = MobileDeviceLifecycle.FINISHED;
		invalidateAssociation();
		abortMigration();
	}

	/** Compatibility transition retained while callers migrate to intent methods. */
	public void setLifecycle(MobileDeviceLifecycle next) {
		if (next == null) {
			throw new IllegalArgumentException("Mobile lifecycle cannot be null");
		}
		if (lifecycle == MobileDeviceLifecycle.FINISHED
			&& next != MobileDeviceLifecycle.FINISHED) {
			throw new IllegalStateException(
				"A finished mobile session cannot return to " + next);
		}
		lifecycle = next;
	}

	public long reserveHandoff() {
		requireLifecycle(MobileDeviceLifecycle.ACTIVE, "reserve a handoff");
		if (handoff != HandoffState.IDLE && handoff != HandoffState.CANCELLED) {
			throw new IllegalStateException("Cannot reserve a handoff from " + handoff);
		}
		associationGeneration = nextGeneration(associationGeneration,
			"association");
		handoff = HandoffState.RESERVED;
		return associationGeneration;
	}

	public boolean beginHandoff(long generation) {
		if (!isCurrentAssociation(generation)
			|| handoff != HandoffState.RESERVED
			|| lifecycle != MobileDeviceLifecycle.ACTIVE) {
			return false;
		}
		handoff = HandoffState.TRANSFERRING;
		return true;
	}

	public boolean completeHandoff(long generation) {
		if (!isCurrentAssociation(generation)
			|| (handoff != HandoffState.RESERVED
				&& handoff != HandoffState.TRANSFERRING)) {
			return false;
		}
		handoff = HandoffState.COMPLETED;
		return true;
	}

	public boolean cancelHandoff(long generation) {
		if (!isCurrentAssociation(generation)) {
			return false;
		}
		handoff = HandoffState.CANCELLED;
		associationGeneration = nextGeneration(associationGeneration,
			"association");
		return true;
	}

	public void unlockHandoff() {
		if (handoff == HandoffState.RESERVED
			|| handoff == HandoffState.TRANSFERRING) {
			throw new IllegalStateException("Cannot unlock an unfinished handoff");
		}
		handoff = HandoffState.IDLE;
	}

	public long invalidateAssociation() {
		associationGeneration = nextGeneration(associationGeneration,
			"association");
		handoff = HandoffState.IDLE;
		return associationGeneration;
	}

	public boolean isHandoffInProgress() {
		return handoff == HandoffState.RESERVED
			|| handoff == HandoffState.TRANSFERRING;
	}

	public boolean isHandoffLocked() {
		return isHandoffInProgress() || handoff == HandoffState.COMPLETED;
	}

	public long decideMigration() {
		requireLifecycle(MobileDeviceLifecycle.ACTIVE, "decide a migration");
		if (migration != MigrationState.IDLE
			&& migration != MigrationState.INSTALLED
			&& migration != MigrationState.ABORTED) {
			throw new IllegalStateException(
				"Cannot decide a migration from " + migration);
		}
		migrationGeneration = nextGeneration(migrationGeneration, "migration");
		migration = MigrationState.DECIDED;
		return migrationGeneration;
	}

	/**
	 * Commits a decision only when the session still matches the snapshot used
	 * by its policy evaluation.
	 */
	public boolean decideMigration(long expectedGeneration,
		MigrationState expectedState) {
		if (expectedState == null
			|| lifecycle != MobileDeviceLifecycle.ACTIVE
			|| migrationGeneration != expectedGeneration
			|| migration != expectedState
			|| (migration != MigrationState.IDLE
				&& migration != MigrationState.INSTALLED
				&& migration != MigrationState.ABORTED)) {
			return false;
		}
		migrationGeneration = nextGeneration(migrationGeneration, "migration");
		migration = MigrationState.DECIDED;
		return true;
	}

	public long beginMigrationPreparation() {
		if (migration == MigrationState.IDLE
			|| migration == MigrationState.INSTALLED
			|| migration == MigrationState.ABORTED) {
			decideMigration();
		}
		if (migration != MigrationState.DECIDED) {
			throw new IllegalStateException(
				"Cannot prepare a migration from " + migration);
		}
		migration = MigrationState.PREPARING;
		return migrationGeneration;
	}

	public boolean beginMigrationTransfer(long generation) {
		if (!isCurrentMigration(generation)
			|| (migration != MigrationState.DECIDED
				&& migration != MigrationState.PREPARING)) {
			return false;
		}
		migration = MigrationState.TRANSFERRING;
		return true;
	}

	public boolean awaitMigrationFixedDelay(long generation) {
		if (!isCurrentMigration(generation)
			|| migration != MigrationState.TRANSFERRING) {
			return false;
		}
		migration = MigrationState.FIXED_DELAY;
		return true;
	}

	public boolean finishMigrationFixedDelay(long generation) {
		if (!isCurrentMigration(generation)
			|| migration != MigrationState.FIXED_DELAY) {
			return false;
		}
		migration = MigrationState.READY_TO_INSTALL;
		return true;
	}

	public boolean scheduleMigrationInstallation(long generation) {
		if (!isCurrentMigration(generation)
			|| (migration != MigrationState.TRANSFERRING
				&& migration != MigrationState.READY_TO_INSTALL)) {
			return false;
		}
		migration = MigrationState.INSTALLING;
		return true;
	}

	public boolean installMigration(long generation) {
		if (!isCurrentMigration(generation)
			|| (migration != MigrationState.PREPARING
				&& migration != MigrationState.TRANSFERRING
				&& migration != MigrationState.FIXED_DELAY
				&& migration != MigrationState.READY_TO_INSTALL
				&& migration != MigrationState.INSTALLING)) {
			return false;
		}
		migration = MigrationState.INSTALLED;
		return true;
	}

	public long abortMigration() {
		migrationGeneration = nextGeneration(migrationGeneration, "migration");
		migration = MigrationState.ABORTED;
		return migrationGeneration;
	}

	public void unlockMigration() {
		if (migration == MigrationState.PREPARING
			|| migration == MigrationState.TRANSFERRING
			|| migration == MigrationState.FIXED_DELAY
			|| migration == MigrationState.READY_TO_INSTALL
			|| migration == MigrationState.INSTALLING) {
			throw new IllegalStateException("Cannot unlock an active migration");
		}
		migration = MigrationState.IDLE;
	}

	public boolean isCurrentAssociation(long generation) {
		return generation > 0L && generation == associationGeneration;
	}

	public boolean isCurrentMigration(long generation) {
		return generation > 0L && generation == migrationGeneration;
	}

	private void ensureNotFinished(String action) {
		if (lifecycle == MobileDeviceLifecycle.FINISHED) {
			throw new IllegalStateException("Cannot " + action
				+ " for a finished mobile session");
		}
	}

	private void requireLifecycle(MobileDeviceLifecycle expected, String action) {
		if (lifecycle != expected) {
			throw new IllegalStateException("Cannot " + action + " from " + lifecycle);
		}
	}

	private static long nextGeneration(long current, String workflow) {
		if (current == Long.MAX_VALUE) {
			throw new IllegalStateException(workflow
				+ " generation identifiers are exhausted");
		}
		return current + 1L;
	}
}
