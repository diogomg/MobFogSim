package org.fog.vmmobile.policy;

/** Selects the condition used to start migration preparation. */
public enum MigrationPointPolicy {
	FIXED(0),
	SPEED(1);

	private final int legacyValue;

	MigrationPointPolicy(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static MigrationPointPolicy fromLegacy(int value) {
		for (MigrationPointPolicy policy : values()) {
			if (policy.legacyValue == value) {
				return policy;
			}
		}
		throw new IllegalArgumentException(
			"Migration point policy must be 0 (fixed) or 1 (speed-based)");
	}
}
