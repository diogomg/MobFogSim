package org.fog.gui.core;

/** Selects the topology schema read or written by the graph bridge. */
public enum TopologyType {
	PHYSICAL(0),
	VIRTUAL(1);

	private final int legacyValue;

	TopologyType(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static TopologyType fromLegacy(int value) {
		for (TopologyType type : values()) {
			if (type.legacyValue == value) {
				return type;
			}
		}
		throw new IllegalArgumentException("Topology type must be 0 (physical) or 1 (virtual)");
	}
}
