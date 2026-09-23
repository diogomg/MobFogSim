package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.fog.entities.MobileDeviceLifecycle;
import org.junit.Test;

public class MobileSessionTest {

	@Test
	public void lifecycleTransitionsAreGuardedAndFinishedIsTerminal() {
		MobileSession session = new MobileSession();
		session.schedule();
		session.beginEntry();
		session.awaitAssociation(false);
		session.activate();
		session.awaitAssociation(true);
		assertEquals(MobileDeviceLifecycle.DISCONNECTED, session.getLifecycle());
		session.activate();
		session.finish();

		assertEquals(MobileDeviceLifecycle.FINISHED, session.getLifecycle());
		try {
			session.schedule();
			fail("A finished session must be terminal");
		}
		catch (IllegalStateException expected) {
			// Expected.
		}
	}

	@Test
	public void staleHandoffGenerationCannotMutateCurrentWorkflow() {
		MobileSession session = new MobileSession();
		long first = session.reserveHandoff();
		assertTrue(session.beginHandoff(first));
		assertTrue(session.cancelHandoff(first));
		session.unlockHandoff();
		long second = session.reserveHandoff();

		assertFalse(session.completeHandoff(first));
		assertEquals(MobileSession.HandoffState.RESERVED, session.getHandoff());
		assertTrue(session.beginHandoff(second));
		assertTrue(session.completeHandoff(second));
		assertEquals(MobileSession.HandoffState.COMPLETED, session.getHandoff());
	}

	@Test
	public void staleMigrationGenerationCannotInstallNewerAttempt() {
		MobileSession session = new MobileSession();
		long first = session.decideMigration();
		session.beginMigrationPreparation();
		assertTrue(session.beginMigrationTransfer(first));
		session.abortMigration();
		session.unlockMigration();

		long second = session.decideMigration();
		session.beginMigrationPreparation();
		assertFalse(session.installMigration(first));
		assertTrue(session.beginMigrationTransfer(second));
		assertTrue(session.awaitMigrationFixedDelay(second));
		assertTrue(session.finishMigrationFixedDelay(second));
		assertFalse(session.finishMigrationFixedDelay(second));
		assertTrue(session.scheduleMigrationInstallation(second));
		assertFalse(session.scheduleMigrationInstallation(second));
		assertTrue(session.installMigration(second));
		assertEquals(MobileSession.MigrationState.INSTALLED,
			session.getMigration());
	}
}
