package org.fog.vmmobile;

import java.util.Collections;
import java.util.List;

import org.fog.entities.FogBroker;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;

/** Validates transitions between pending and registered mobile-user state. */
public final class MobileUserRegistration {

	private MobileUserRegistration() {
	}

	public static void preparePendingUsers(List<MobileDevice> users) {
		if (users == null) {
			throw new IllegalArgumentException("Mobile user list cannot be null");
		}
		for (MobileDevice user : users) {
			preparePendingUser(user);
		}
	}

	public static void preparePendingUser(MobileDevice user) {
		validateUserAndPeripherals(user);
		user.setStatus(false);
		for (MobileSensor sensor : user.getSensors()) {
			sensor.deactivate();
		}
		for (MobileActuator actuator : user.getActuators()) {
			actuator.deactivate();
		}
	}

	public static void configurePeripherals(MobileDevice user, FogBroker broker,
		String appId) {
		validateUserAndPeripherals(user);
		if (broker == null) {
			throw new IllegalArgumentException("User broker cannot be null");
		}
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("User application ID cannot be empty");
		}
		for (MobileSensor sensor : user.getSensors()) {
			sensor.setAppId(appId);
			sensor.setUserId(broker.getId());
			sensor.setGatewayDeviceId(user.getId());
			sensor.setLatency(6.0);
		}
		for (MobileActuator actuator : user.getActuators()) {
			actuator.setUserId(broker.getId());
			actuator.setAppId(appId);
			actuator.setGatewayDeviceId(user.getId());
			actuator.setLatency(1.0);
			actuator.setActuatorType("DISPLAY" + user.getMyId());
		}
	}

	public static void submitVm(FogBroker broker, MobileDevice user) {
		if (broker == null) {
			throw new IllegalArgumentException("User broker cannot be null");
		}
		validateUserAndPeripherals(user);
		if (user.getSourceAp() == null || user.getSourceServerCloudlet() == null) {
			throw new IllegalStateException("Cannot register disconnected user "
				+ user.getName());
		}
		if (user.getSourceAp().getServerCloudlet() != user.getSourceServerCloudlet()) {
			throw new IllegalStateException("Cannot register user " + user.getName()
				+ " with inconsistent network associations");
		}
		if (user.getVmMobileDevice() == null) {
			throw new IllegalStateException("Cannot register user " + user.getName()
				+ " without a VM");
		}
		if (user.getVmMobileDevice().getUserId() != broker.getId()) {
			throw new IllegalStateException("Cannot register user " + user.getName()
				+ " with a VM owned by another broker");
		}
		broker.submitVmList(Collections.singletonList(user.getVmMobileDevice()));
	}

	public static void activatePeripherals(MobileDevice user) {
		validateUserAndPeripherals(user);
		user.setStatus(true);
		for (MobileSensor sensor : user.getSensors()) {
			sensor.activate();
		}
		for (MobileActuator actuator : user.getActuators()) {
			actuator.activate();
		}
	}

	private static void validateUserAndPeripherals(MobileDevice user) {
		if (user == null) {
			throw new IllegalArgumentException("Mobile user cannot be null");
		}
		if (user.getSensors() == null || user.getActuators() == null) {
			throw new IllegalStateException("Mobile user " + user.getName()
				+ " has an incomplete peripheral configuration");
		}
	}
}
