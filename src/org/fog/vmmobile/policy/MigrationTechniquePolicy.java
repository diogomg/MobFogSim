package org.fog.vmmobile.policy;

/** Selects the VM state-transfer technique. */
public enum MigrationTechniquePolicy {
	COMPLETE_VM(0),
	CONTAINER_VM(1),
	LIVE_MIGRATION(2);

	private final int legacyValue;

	MigrationTechniquePolicy(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static MigrationTechniquePolicy fromLegacy(int value) {
		for (MigrationTechniquePolicy policy : values()) {
			if (policy.legacyValue == value) {
				return policy;
			}
		}
		throw new IllegalArgumentException(
			"VM migration policy must be 0 (complete VM), 1 (container), or 2 (live migration)");
	}
}
