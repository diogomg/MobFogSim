package org.fog.entities;

import org.fog.utils.GeoLocation;
import org.fog.utils.distribution.Distribution;

public class MobileSensor extends Sensor {

	public MobileSensor(String name, int userId, String appId, int gatewayDeviceId, double latency,
		GeoLocation geoLocation, Distribution transmitDistribution, int cpuLength, int nwLength,
		String tupleType, String destModuleName) {
		super(name, userId, appId, gatewayDeviceId, latency, geoLocation,
			transmitDistribution, cpuLength, nwLength, tupleType, destModuleName);

		// TODO Auto-generated constructor stub
	}

	public MobileSensor(String name, int userId, String appId, int gatewayDeviceId, double latency,
		GeoLocation geoLocation, Distribution transmitDistribution, String tupleType) {
		super(name, userId, appId, gatewayDeviceId, latency, geoLocation, transmitDistribution,
			tupleType);
	}

	public MobileSensor(String name, String tupleType, int userId, String appId,
		Distribution transmitDistribution) {
		super(name, tupleType, userId, appId, transmitDistribution);
	}

}
