package org.fog.gui.core;

import java.io.Serializable;

public class ActuatorGui extends Node implements Serializable {

	private static final long serialVersionUID = 4087896123649020073L;

	private String actuatorType;

	public ActuatorGui(String name, String actuatorType) {
		this(NodeId.create(), name, actuatorType);
	}

	ActuatorGui(NodeId nodeId, String name, String actuatorType) {
		super(nodeId, name, NodeType.ACTUATOR);
		this.actuatorType = actuatorType;
	}

	@Override
	public String toString() {
		return "Actuator []";
	}

	public String getActuatorType() {
		return actuatorType;
	}

	public void setActuatorType(String actuatorType) {
		this.actuatorType = actuatorType;
	}

}
