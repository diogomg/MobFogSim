package org.fog.vmmobile.policy;

/** Selects how a migration destination is chosen. */
public enum MigrationStrategyPolicy {
	LOWEST_LATENCY(0),
	LOWEST_DISTANCE_TO_SERVER_CLOUDLET(1),
	LOWEST_DISTANCE_TO_ACCESS_POINT(2);

	private final int legacyValue;

	MigrationStrategyPolicy(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static MigrationStrategyPolicy fromLegacy(int value) {
		for (MigrationStrategyPolicy policy : values()) {
			if (policy.legacyValue == value) {
				return policy;
			}
		}
		throw new IllegalArgumentException(
			"Migration strategy policy must be 0 (latency), 1 (server distance), or 2 (AP distance)");
	}
}
