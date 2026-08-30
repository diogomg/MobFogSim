package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MigrationPreparationTest {

	@Test
	public void containerConnectionSucceedsOnFirstAttempt() {
		ControlledContainerPreparation preparation =
			new ControlledContainerPreparation(true);

		assertTrue(preparation.openConnection(null, null));
		assertEquals(10.0, preparation.getTimeToOpenConnection(), 0.0);
	}

	@Test
	public void containerConnectionAccountsForFailuresBeforeSuccess() {
		ControlledContainerPreparation preparation =
			new ControlledContainerPreparation(false, false, true);

		assertTrue(preparation.openConnection(null, null));
		assertEquals(70.0, preparation.getTimeToOpenConnection(), 0.0);
	}

	@Test
	public void containerConnectionStopsAfterFiveFailures() {
		ControlledContainerPreparation preparation =
			new ControlledContainerPreparation(false, false, false, false, false);

		assertFalse(preparation.openConnection(null, null));
		assertEquals(150.0, preparation.getTimeToOpenConnection(), 0.0);
		assertEquals(5, preparation.getAttempts());
	}

	@Test
	public void liveConnectionUsesTheSameFiveAttemptPolicy() {
		ControlledLivePreparation preparation =
			new ControlledLivePreparation(false, false, false, false, true);

		assertTrue(preparation.openConnection(null, null));
		assertEquals(130.0, preparation.getTimeToOpenConnection(), 0.0);
		assertEquals(5, preparation.getAttempts());
	}

	@Test
	public void completeConnectionStopsAfterThreeFailures() {
		ControlledCompletePreparation preparation =
			new ControlledCompletePreparation(false, false, false);

		assertFalse(preparation.openConnection(null, null));
		assertEquals(90.0, preparation.getTimeToOpenConnection(), 0.0);
		assertEquals(3, preparation.getAttempts());
	}

	@Test
	public void duringMigrationManagementCompletes() {
		assertTrue(new DuringMigration().managermentBetweeServerCloudlets());
	}

	private abstract static class ControlledAttempts {
		private final boolean[] results;
		private int attempts;

		ControlledAttempts(boolean... results) {
			this.results = results;
		}

		boolean nextResult() {
			return results[attempts++];
		}

		int getAttempts() {
			return attempts;
		}
	}

	private static final class ControlledContainerPreparation extends PrepareContainerVM {
		private final ControlledAttempts attempts;

		ControlledContainerPreparation(final boolean... results) {
			attempts = new ControlledAttempts(results) { };
		}

		@Override
		public boolean tryOpenConnection() {
			return attempts.nextResult();
		}

		int getAttempts() {
			return attempts.getAttempts();
		}
	}

	private static final class ControlledLivePreparation extends PrepareLiveMigration {
		private final ControlledAttempts attempts;

		ControlledLivePreparation(final boolean... results) {
			attempts = new ControlledAttempts(results) { };
		}

		@Override
		public boolean tryOpenConnection() {
			return attempts.nextResult();
		}

		int getAttempts() {
			return attempts.getAttempts();
		}
	}

	private static final class ControlledCompletePreparation extends PrepareCompleteVM {
		private final ControlledAttempts attempts;

		ControlledCompletePreparation(final boolean... results) {
			attempts = new ControlledAttempts(results) { };
		}

		@Override
		public boolean tryOpenConnection() {
			return attempts.nextResult();
		}

		int getAttempts() {
			return attempts.getAttempts();
		}
	}
}

