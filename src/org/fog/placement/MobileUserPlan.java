package org.fog.placement;

import java.util.Map;

import org.fog.application.Application;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.vmmobile.SimulationBuildException;

/** Immutable, validated inputs for one delayed mobile-user registration. */
public final class MobileUserPlan {
	private final MobileDevice mobileDevice;
	private final FogDevice serverCloudlet;
	private final String applicationId;
	private final String vmName;
	private final String clientModuleName;

	private MobileUserPlan(MobileDevice mobileDevice, FogDevice serverCloudlet,
		String applicationId, String vmName, String clientModuleName) {
		this.mobileDevice = mobileDevice;
		this.serverCloudlet = serverCloudlet;
		this.applicationId = applicationId;
		this.vmName = vmName;
		this.clientModuleName = clientModuleName;
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

		String applicationId = "MyApp_vr_game" + mobileDevice.getMyId();
		String vmName = "AppModuleVm_" + mobileDevice.getName();
		String clientModuleName = "client" + mobileDevice.getMyId();
		if (applications.containsKey(applicationId)) {
			throw new SimulationBuildException("Application " + applicationId
				+ " is already registered");
		}
		for (Map<String, Integer> modules
			: moduleMapping.getModuleMapping().values()) {
			if (modules.containsKey(vmName)
				|| modules.containsKey(clientModuleName)) {
				throw new SimulationBuildException("Module mapping for "
					+ mobileDevice.getName() + " already exists");
			}
		}
		return new MobileUserPlan(mobileDevice, serverCloudlet, applicationId,
			vmName, clientModuleName);
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
		return applicationId;
	}

	public String getVmName() {
		return vmName;
	}

	public String getClientModuleName() {
		return clientModuleName;
	}
}
