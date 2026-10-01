package org.fog.vmmobile;

import java.util.Collections;
import java.util.List;

import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
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
		if (user.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			throw new IllegalStateException("Finished mobile user " + user.getName()
				+ " cannot be scheduled again");
		}
		user.getSession().schedule();
		user.setStatus(false);
		deactivatePeripherals(user);
	}

	public static void beginEntry(MobileDevice user) {
		validateUserAndPeripherals(user);
		if (user.getLifecycleState() != MobileDeviceLifecycle.SCHEDULED) {
			throw new IllegalStateException("Mobile user " + user.getName()
				+ " cannot enter from lifecycle state " + user.getLifecycleState());
		}
		user.getSession().beginEntry();
		user.setStatus(false);
	}

	/** Marks an entered user as waiting for a first or replacement AP. */
	public static void awaitAssociation(MobileDevice user) {
		validateUserAndPeripherals(user);
		if (user.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		user.getSession().awaitAssociation(user.getVmMobileDevice() != null);
		user.setStatus(false);
	}

	public static void configurePeripherals(MobileDevice user, FogBroker broker,
		String appId) {
		configurePeripherals(user, broker, appId, true);
	}

	/** Configures all peripherals while retaining their per-application IDs. */
	public static void configurePeripherals(MobileDevice user,
		FogBroker broker) {
		configurePeripherals(user, broker, null, false);
	}

	private static void configurePeripherals(MobileDevice user,
		FogBroker broker, String appId, boolean overwriteApplication) {
		validateUserAndPeripherals(user);
		if (broker == null) {
			throw new IllegalArgumentException("User broker cannot be null");
		}
		if (overwriteApplication && (appId == null || appId.trim().isEmpty())) {
			throw new IllegalArgumentException("User application ID cannot be empty");
		}
		for (MobileSensor sensor : user.getSensors()) {
			MobileApplicationProfile profile = overwriteApplication
				? user.getApplicationProfile(0)
				: profileFor(user, sensor.getAppId());
			if (overwriteApplication) {
				sensor.setAppId(appId);
			}
			else if (sensor.getAppId() == null
				|| sensor.getAppId().trim().isEmpty()) {
				throw new IllegalStateException(
					"Mobile sensor application ID cannot be empty");
			}
			sensor.setUserId(broker.getId());
			sensor.setGatewayDeviceId(user.getId());
			sensor.setLatency(profile.getSensorLatencyMillis());
		}
		for (MobileActuator actuator : user.getActuators()) {
			MobileApplicationProfile profile = overwriteApplication
				? user.getApplicationProfile(0)
				: profileFor(user, actuator.getAppId());
			actuator.setUserId(broker.getId());
			if (overwriteApplication) {
				actuator.setAppId(appId);
				actuator.setActuatorType("DISPLAY" + user.getMyId());
			}
			else if (actuator.getAppId() == null
				|| actuator.getAppId().trim().isEmpty()
				|| actuator.getActuatorType() == null
				|| actuator.getActuatorType().trim().isEmpty()) {
				throw new IllegalStateException(
					"Mobile actuator application and type cannot be empty");
			}
			actuator.setGatewayDeviceId(user.getId());
			actuator.setLatency(profile.getActuatorLatencyMillis());
		}
	}

	private static MobileApplicationProfile profileFor(MobileDevice user,
		String applicationId) {
		for (int applicationIndex = 0;
			applicationIndex < user.getApplicationsPerUser(); applicationIndex++) {
			MobileApplicationDescriptor descriptor =
				MobileApplicationDescriptor.forUser(user.getMyId(), user.getName(),
					applicationIndex);
			if (descriptor.getApplicationId().equals(applicationId)) {
				return user.getApplicationProfile(applicationIndex);
			}
		}
		throw new IllegalStateException("No application profile is configured for "
			+ applicationId + " on user " + user.getName());
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
		if (user.getVmMobileDevices().isEmpty()) {
			throw new IllegalStateException("Cannot register user " + user.getName()
				+ " without a VM");
		}
		for (org.cloudbus.cloudsim.Vm virtualMachine
			: user.getVmMobileDevices()) {
			if (virtualMachine.getUserId() != broker.getId()) {
				throw new IllegalStateException("Cannot register user " + user.getName()
					+ " with a VM owned by another broker");
			}
		}
		broker.submitVmList(user.getVmMobileDevices());
	}

	public static void activatePeripherals(MobileDevice user) {
		validateUserAndPeripherals(user);
		validateNetworkAssociation(user);
		if (user.getVmMobileDevices().isEmpty()) {
			throw new IllegalStateException("Cannot activate user " + user.getName()
				+ " without a VM");
		}
		user.getSession().activate();
		user.setStatus(true);
		for (MobileSensor sensor : user.getSensors()) {
			sensor.activate();
		}
		for (MobileActuator actuator : user.getActuators()) {
			actuator.activate();
		}
	}

	/** Clears both sides of the current network association, if present. */
	public static void disconnectNetwork(MobileDevice user) {
		validateUserAndPeripherals(user);
		user.cancelPendingHandoffReservation();
		ApDevice sourceAp = user.getSourceAp();
		FogDevice sourceServer = user.getSourceServerCloudlet();
		if (sourceAp != null) {
			sourceAp.disconnectApSmartThing(user);
		}
		if (sourceServer != null) {
			sourceServer.disconnectServerCloudletSmartThing(user);
		}
		user.setSourceAp(null);
		user.setSourceServerCloudlet(null);
		user.setDestinationAp(null);
		user.setParentId(-1);
		user.setHandoffStatus(false);
		user.setLockedToHandoff(false);
		user.advanceNetworkAssociationGeneration();
	}

	public static void finishUser(MobileDevice user) {
		validateUserAndPeripherals(user);
		user.getSession().finish();
		user.setStatus(false);
		deactivatePeripherals(user);
	}

	private static void validateNetworkAssociation(MobileDevice user) {
		if (user.getSourceAp() == null || user.getSourceServerCloudlet() == null) {
			throw new IllegalStateException("Cannot activate disconnected user "
				+ user.getName());
		}
		if (user.getSourceAp().getServerCloudlet() != user.getSourceServerCloudlet()) {
			throw new IllegalStateException("Cannot activate user " + user.getName()
				+ " with inconsistent network associations");
		}
		if (!user.getSourceAp().getSmartThings().contains(user)
			|| !user.getSourceServerCloudlet().getSmartThings().contains(user)) {
			throw new IllegalStateException("Cannot activate user " + user.getName()
				+ " before both network endpoints contain it");
		}
	}

	private static void deactivatePeripherals(MobileDevice user) {
		for (MobileSensor sensor : user.getSensors()) {
			sensor.deactivate();
		}
		for (MobileActuator actuator : user.getActuators()) {
			actuator.deactivate();
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
