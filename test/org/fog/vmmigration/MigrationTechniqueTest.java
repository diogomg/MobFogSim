package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.fog.vmmobile.constants.Directions;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.Policies;
import org.junit.Test;

public class MigrationTechniqueTest {

	private static final double DELTA = 0.000001;

	@Test
	public void completeVmTransferTimeScalesWithSizeAndBandwidth() {
		CompleteVM technique = new CompleteVM(Policies.FIXED_MIGRATION_POINT);
		double bandwidth = 8.0 * 1024 * 1024;

		assertEquals(128000.0, technique.migrationTimeFunction(128, bandwidth), DELTA);
		assertEquals(256000.0, technique.migrationTimeFunction(256, bandwidth), DELTA);
		assertEquals(64000.0, technique.migrationTimeFunction(128, bandwidth * 2), DELTA);
	}

	@Test
	public void containerTransferUsesConfiguredFractionOfCompleteVm() {
		CompleteVM complete = new CompleteVM(Policies.FIXED_MIGRATION_POINT);
		ContainerVM container = new ContainerVM(Policies.FIXED_MIGRATION_POINT);
		double bandwidth = 8.0 * 1024 * 1024;

		assertEquals(complete.migrationTimeFunction(128, bandwidth) * MaxAndMin.SIZE_CONTAINER,
			container.migrationTimeFunction(128, bandwidth), DELTA);
	}

	@Test
	public void liveMigrationIncludesItsThirtyPercentTransferOverhead() {
		CompleteVM complete = new CompleteVM(Policies.FIXED_MIGRATION_POINT);
		LiveMigration live = new LiveMigration(Policies.FIXED_MIGRATION_POINT);
		double bandwidth = 8.0 * 1024 * 1024;

		assertEquals(complete.migrationTimeFunction(128, bandwidth) * 1.3,
			live.migrationTimeFunction(128, bandwidth), DELTA);
	}

	@Test
	public void techniquesExposeTheirActualTransferSizesInBytes() {
		double completeBytes = 128.0 * 1024.0 * 1024.0;

		assertEquals(completeBytes,
			new CompleteVM(Policies.FIXED_MIGRATION_POINT)
				.getTransferSizeBytes(128.0), DELTA);
		assertEquals(completeBytes * MaxAndMin.SIZE_CONTAINER,
			new ContainerVM(Policies.FIXED_MIGRATION_POINT)
				.getTransferSizeBytes(128.0), DELTA);
		assertEquals(completeBytes * 1.3,
			new LiveMigration(Policies.FIXED_MIGRATION_POINT)
				.getTransferSizeBytes(128.0), DELTA);
	}

	@Test
	public void completeAndContainerUseTheStandardFixedMigrationBoundary() {
		assertStandardFixedBoundary(new CompleteVM(Policies.FIXED_MIGRATION_POINT));
		assertStandardFixedBoundary(new ContainerVM(Policies.FIXED_MIGRATION_POINT));
	}

	@Test
	public void liveMigrationUsesItsEarlierFixedMigrationBoundary() {
		LiveMigration technique = new LiveMigration(Policies.FIXED_MIGRATION_POINT);
		double threshold = MaxAndMin.AP_COVERAGE - MaxAndMin.LIVE_MIG_POINT;

		assertFalse(technique.migrationPointFunction(Math.nextDown(threshold)));
		assertTrue(technique.migrationPointFunction(threshold));
		assertTrue(technique.migrationPointFunction(Math.nextDown((double) MaxAndMin.AP_COVERAGE)));
		assertFalse(technique.migrationPointFunction(MaxAndMin.AP_COVERAGE));
	}

	@Test
	public void speedPolicyMovesCompleteAndContainerMigrationPointEarlier() {
		assertSpeedBoundary(new CompleteVM(Policies.SPEED_MIGRATION_POINT),
			MaxAndMin.MAX_DISTANCE_TO_HANDOFF / 2.0);
		assertSpeedBoundary(new ContainerVM(Policies.SPEED_MIGRATION_POINT),
			MaxAndMin.MAX_DISTANCE_TO_HANDOFF / 2.0);
	}

	@Test
	public void speedPolicyMovesLiveMigrationPointEarlier() {
		assertSpeedBoundary(new LiveMigration(Policies.SPEED_MIGRATION_POINT), MaxAndMin.MIG_POINT);
	}

	@Test
	public void migrationZoneIncludesDirectionAndItsTwoNeighbours() {
		VmMigrationTechnique[] techniques = techniques();
		int[] directions = { Directions.EAST, Directions.NORTHEAST, Directions.NORTH,
			Directions.NORTHWEST, Directions.WEST, Directions.SOUTHWEST, Directions.SOUTH,
			Directions.SOUTHEAST };

		for (VmMigrationTechnique technique : techniques) {
			for (int direction : directions) {
				int previous = direction == Directions.EAST ? Directions.SOUTHEAST : direction - 1;
				int next = direction == Directions.SOUTHEAST ? Directions.EAST : direction + 1;
				int opposite = ((direction - 1 + 4) % 8) + 1;

				assertTrue(technique.migrationZoneFunction(direction, previous));
				assertTrue(technique.migrationZoneFunction(direction, direction));
				assertTrue(technique.migrationZoneFunction(direction, next));
				assertFalse(technique.migrationZoneFunction(direction, opposite));
			}
		}
	}

	@Test
	public void migrationPointPolicyCanBeChanged() {
		CompleteVM complete = new CompleteVM(Policies.FIXED_MIGRATION_POINT);
		ContainerVM container = new ContainerVM(Policies.FIXED_MIGRATION_POINT);
		LiveMigration live = new LiveMigration(Policies.FIXED_MIGRATION_POINT);

		complete.setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);
		container.setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);
		live.setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);

		assertEquals(Policies.SPEED_MIGRATION_POINT, complete.getMigPointPolicy());
		assertEquals(Policies.SPEED_MIGRATION_POINT, container.getMigPointPolicy());
		assertEquals(Policies.SPEED_MIGRATION_POINT, live.getMigPointPolicy());
	}

	private static void assertStandardFixedBoundary(VmMigrationTechnique technique) {
		double threshold = MaxAndMin.AP_COVERAGE - MaxAndMin.MIG_POINT;
		assertFalse(technique.migrationPointFunction(Math.nextDown(threshold)));
		assertTrue(technique.migrationPointFunction(threshold));
		assertTrue(technique.migrationPointFunction(Math.nextDown((double) MaxAndMin.AP_COVERAGE)));
		assertFalse(technique.migrationPointFunction(MaxAndMin.AP_COVERAGE));
	}

	private static void assertSpeedBoundary(VmMigrationTechnique technique, double fixedMargin) {
		double migrationTime = 5000.0;
		int speed = 20;
		double threshold = MaxAndMin.AP_COVERAGE
			- ((migrationTime / 1000.0) * speed + fixedMargin);

		assertFalse(technique.migrationPointFunction(Math.nextDown(threshold), migrationTime, speed));
		assertTrue(technique.migrationPointFunction(threshold, migrationTime, speed));
		assertFalse(technique.migrationPointFunction(MaxAndMin.AP_COVERAGE, migrationTime, speed));
	}

	private static VmMigrationTechnique[] techniques() {
		return new VmMigrationTechnique[] {
			new CompleteVM(Policies.FIXED_MIGRATION_POINT),
			new ContainerVM(Policies.FIXED_MIGRATION_POINT),
			new LiveMigration(Policies.FIXED_MIGRATION_POINT)
		};
	}
}
