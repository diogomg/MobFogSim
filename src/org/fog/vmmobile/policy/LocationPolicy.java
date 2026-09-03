package org.fog.vmmobile.policy;

/** Selects fixed or random infrastructure placement. */
public enum LocationPolicy {
	FIXED(0),
	RANDOM(1);

	private final int legacyValue;

	LocationPolicy(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static LocationPolicy fromLegacy(int value) {
		for (LocationPolicy policy : values()) {
			if (policy.legacyValue == value) {
				return policy;
			}
		}
		throw new IllegalArgumentException("Location policy must be 0 (fixed) or 1 (random)");
	}
}
