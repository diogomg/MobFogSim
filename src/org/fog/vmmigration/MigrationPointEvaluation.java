package org.fog.vmmigration;

/** Immutable result of evaluating a migration point and direction cone. */
public final class MigrationPointEvaluation {
	private final boolean migrationPoint;
	private final boolean migrationZone;
	private final double migrationTimeMillis;

	public MigrationPointEvaluation(boolean migrationPoint,
		boolean migrationZone, double migrationTimeMillis) {
		if (!Double.isFinite(migrationTimeMillis) || migrationTimeMillis < 0.0) {
			throw new IllegalArgumentException(
				"Migration time must be finite and non-negative");
		}
		this.migrationPoint = migrationPoint;
		this.migrationZone = migrationZone;
		this.migrationTimeMillis = migrationTimeMillis;
	}

	public boolean isMigrationPoint() {
		return migrationPoint;
	}

	public boolean isMigrationZone() {
		return migrationZone;
	}

	public double getMigrationTimeMillis() {
		return migrationTimeMillis;
	}

	public boolean allowsMigration() {
		return migrationPoint && migrationZone;
	}
}
