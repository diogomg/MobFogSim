package org.fog.vmmigration;

import org.fog.vmmobile.policy.ServiceType;

public class Service {
	private ServiceType type = ServiceType.PUBLIC;
	private float value;

	public Service() {
	}

	public int getType() {
		return type.legacyValue();
	}

	public void setType(int type) {
		setServiceType(ServiceType.fromLegacy(type));
	}

	public ServiceType getServiceType() {
		return type;
	}

	public void setServiceType(ServiceType type) {
		if (type == null) {
			throw new IllegalArgumentException("Service type cannot be null");
		}
		this.type = type;
	}

	public float getValue() {
		return value;
	}

	public void setValue(float value) {
		this.value = value;
	}

}
