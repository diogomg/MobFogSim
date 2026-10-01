package org.fog.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.vmmobile.MobileApplicationDescriptor;
import org.fog.vmmobile.SimulationBuildException;

/** Immutable, validated inputs for one delayed mobile-user registration. */
public final class MobileUserPlan {
	private final MobileDevice mobileDevice;
	private final FogDevice serverCloudlet;
	private final List<MobileApplicationDescriptor> applicationDescriptors;

	private MobileUserPlan(MobileDevice mobileDevice, FogDevice serverCloudlet,
		List<MobileApplicationDescriptor> applicationDescriptors) {
		this.mobileDevice = mobileDevice;
		this.serverCloudlet = serverCloudlet;
		this.applicationDescriptors = Collections.unmodifiableList(
			new ArrayList<MobileApplicationDescriptor>(applicationDescriptors));
	}

	public static MobileUserPlan create(MobileDevice mobileDevice,
		ModuleMapping moduleMapping, Map<String, Application> applications,
		Map<Integer, FogBroker> brokersByMobileId) {
		if (mobileDevice == null || moduleMapping == null || applications == null
			|| brokersByMobileId == null) {
			throw new IllegalArgumentException(
				"Registration plan inputs cannot be null");
		}
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			throw new SimulationBuildException("Finished mobile user "
				+ mobileDevice.getName() + " cannot be registered");
		}
		if (mobileDevice.getVmMobileDevice() != null
			|| mobileDevice.getVmLocalServerCloudlet() != null) {
			throw new SimulationBuildException("Mobile user "
				+ mobileDevice.getName() + " already has VM registration state");
		}
		if (mobileDevice.getSourceAp() == null
			|| mobileDevice.getSourceServerCloudlet() == null
			|| mobileDevice.getSourceAp().getServerCloudlet()
				!= mobileDevice.getSourceServerCloudlet()
			|| !mobileDevice.getSourceAp().getSmartThings().contains(mobileDevice)
			|| !mobileDevice.getSourceServerCloudlet().getSmartThings()
				.contains(mobileDevice)) {
			throw new SimulationBuildException("Mobile user "
				+ mobileDevice.getName()
				+ " must have a complete network association before registration");
		}
		FogDevice serverCloudlet = mobileDevice.getSourceServerCloudlet();
		if (serverCloudlet.getCharacteristics() == null
			|| serverCloudlet.getHostList().isEmpty()) {
			throw new SimulationBuildException("Server cloudlet "
				+ serverCloudlet.getName() + " has no VM host");
		}
		if (brokersByMobileId.containsKey(mobileDevice.getMyId())) {
			throw new SimulationBuildException("A broker is already registered for "
				+ mobileDevice.getName());
		}
		validatePeripherals(mobileDevice);

		List<MobileApplicationDescriptor> descriptors =
			new ArrayList<MobileApplicationDescriptor>();
		for (int applicationIndex = 0;
			applicationIndex < mobileDevice.getApplicationsPerUser();
			applicationIndex++) {
			MobileApplicationDescriptor descriptor =
				MobileApplicationDescriptor.forUser(mobileDevice.getMyId(),
					mobileDevice.getName(), applicationIndex);
			if (applications.containsKey(descriptor.getApplicationId())) {
				throw new SimulationBuildException("Application "
					+ descriptor.getApplicationId() + " is already registered");
			}
			for (Map<String, Integer> modules
				: moduleMapping.getModuleMapping().values()) {
				if (modules.containsKey(descriptor.getVmName())
					|| modules.containsKey(descriptor.getClientModuleName())) {
					throw new SimulationBuildException("Module mapping for "
						+ mobileDevice.getName() + " application "
						+ applicationIndex + " already exists");
				}
			}
			descriptors.add(descriptor);
		}
		validatePeripheralApplications(mobileDevice, descriptors);
		return new MobileUserPlan(mobileDevice, serverCloudlet, descriptors);
	}

	private static void validatePeripheralApplications(MobileDevice mobileDevice,
		List<MobileApplicationDescriptor> descriptors) {
		Map<String, Integer> sensorCounts = new HashMap<String, Integer>();
		Map<String, Integer> actuatorCounts = new HashMap<String, Integer>();
		for (MobileSensor sensor : mobileDevice.getSensors()) {
			increment(sensorCounts, sensor.getAppId());
		}
		for (MobileActuator actuator : mobileDevice.getActuators()) {
			increment(actuatorCounts, actuator.getAppId());
		}
		for (MobileApplicationDescriptor descriptor : descriptors) {
			String applicationId = descriptor.getApplicationId();
			if (!Integer.valueOf(1).equals(sensorCounts.get(applicationId))
				|| !Integer.valueOf(1).equals(actuatorCounts.get(applicationId))) {
				throw new SimulationBuildException("Mobile user "
					+ mobileDevice.getName() + " requires exactly one sensor and "
					+ "one actuator for application " + applicationId);
			}
		}
		if (sensorCounts.size() != descriptors.size()
			|| actuatorCounts.size() != descriptors.size()) {
			throw new SimulationBuildException("Mobile user "
				+ mobileDevice.getName()
				+ " has peripherals for an unconfigured application");
		}
	}

	private static void increment(Map<String, Integer> counts, String key) {
		Integer count = counts.get(key);
		counts.put(key, count == null ? 1 : count + 1);
	}

	private static void validatePeripherals(MobileDevice mobileDevice) {
		if (mobileDevice.getSensors() == null
			|| mobileDevice.getActuators() == null) {
			throw new SimulationBuildException("Mobile user "
				+ mobileDevice.getName()
				+ " has an incomplete peripheral configuration");
		}
		for (MobileSensor sensor : mobileDevice.getSensors()) {
			if (sensor == null) {
				throw new SimulationBuildException("Mobile user "
					+ mobileDevice.getName() + " contains a null sensor");
			}
		}
		for (MobileActuator actuator : mobileDevice.getActuators()) {
			if (actuator == null) {
				throw new SimulationBuildException("Mobile user "
					+ mobileDevice.getName() + " contains a null actuator");
			}
		}
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public FogDevice getServerCloudlet() {
		return serverCloudlet;
	}

	public String getApplicationId() {
		return applicationDescriptors.get(0).getApplicationId();
	}

	public String getVmName() {
		return applicationDescriptors.get(0).getVmName();
	}

	public String getClientModuleName() {
		return applicationDescriptors.get(0).getClientModuleName();
	}

	public List<MobileApplicationDescriptor> getApplicationDescriptors() {
		return applicationDescriptors;
	}
}
