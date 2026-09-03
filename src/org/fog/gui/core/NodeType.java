package org.fog.gui.core;

/** Stable node categories used by graph logic and topology JSON boundaries. */
public enum NodeType {
	HOST("host"),
	CORE_SWITCH("core"),
	EDGE_SWITCH("edge"),
	VM("vm"),
	FOG_DEVICE("FOG_DEVICE"),
	SENSOR("SENSOR"),
	ACTUATOR("ACTUATOR"),
	APP_MODULE("APP_MODULE"),
	SENSOR_MODULE("SENSOR_MODULE"),
	ACTUATOR_MODULE("ACTUATOR_MODULE");

	private final String externalValue;

	NodeType(String externalValue) {
		this.externalValue = externalValue;
	}

	public String externalValue() {
		return externalValue;
	}

	public boolean isPhysicalNode() {
		return this == HOST || this == CORE_SWITCH || this == EDGE_SWITCH
			|| this == FOG_DEVICE || this == SENSOR || this == ACTUATOR;
	}

	public boolean isEndpoint() {
		return this == HOST || this == FOG_DEVICE || this == SENSOR
			|| this == ACTUATOR;
	}

	public static NodeType fromExternal(String value) {
		if (value != null) {
			for (NodeType type : values()) {
				if (type.externalValue.equalsIgnoreCase(value.trim())) {
					return type;
				}
			}
		}
		throw new IllegalArgumentException("Unknown topology node type: " + value);
	}
}
