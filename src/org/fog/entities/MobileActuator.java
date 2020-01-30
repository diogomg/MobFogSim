package org.fog.entities;

import org.fog.utils.GeoLocation;

public class MobileActuator extends Actuator {

	public MobileActuator(String name, int userId, String appId, int gatewayDeviceId,
		double latency, GeoLocation geoLocation, String actuatorType, String srcModuleName) {
		super(name, userId, appId, gatewayDeviceId, latency, geoLocation, actuatorType,
			srcModuleName);
	}

	public MobileActuator(String name, int userId, String appId, String actuatorType) {
		super(name, userId, appId, actuatorType);
	}

}
