package org.fog.vmmobile.policy;

/** Access model advertised by an edge service. */
public enum ServiceType {
	PUBLIC(0),
	PRIVATE(1),
	HYBRID(2);

	private final int legacyValue;

	ServiceType(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static ServiceType fromLegacy(int value) {
		for (ServiceType type : values()) {
			if (type.legacyValue == value) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unsupported service type " + value
			+ "; expected 0 (public), 1 (private), or 2 (hybrid)");
	}
}
