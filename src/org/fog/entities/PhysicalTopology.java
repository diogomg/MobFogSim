package org.fog.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PhysicalTopology {

	private List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	private List<Sensor> sensors = new ArrayList<Sensor>();
	private List<Actuator> actuators = new ArrayList<Actuator>();

	public List<FogDevice> getFogDevices() {
		return Collections.unmodifiableList(fogDevices);
	}

	public void setFogDevices(List<FogDevice> fogDevices) {
		this.fogDevices = copy(fogDevices, "Fog device list");
	}

	public List<Sensor> getSensors() {
		return Collections.unmodifiableList(sensors);
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = copy(sensors, "Sensor list");
	}

	public List<Actuator> getActuators() {
		return Collections.unmodifiableList(actuators);
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = copy(actuators, "Actuator list");
	}

	private static <T> List<T> copy(List<T> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new ArrayList<T>(values);
	}

}
