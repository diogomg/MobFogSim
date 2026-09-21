package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fog.application.Application;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.GeoCoverage;

/** Mutable topology and application registry scoped to one simulation run. */
public final class SimulationTopology {
	private final List<MobileDevice> mobileDevices =
		new ArrayList<MobileDevice>();
	private final List<MobileDevice> activeMobileDevices =
		new ArrayList<MobileDevice>();
	private final List<FogDevice> serverCloudlets = new ArrayList<FogDevice>();
	private final List<ApDevice> accessPoints = new ArrayList<ApDevice>();
	private final List<FogBroker> brokers = new ArrayList<FogBroker>();
	private final List<String> applicationIds = new ArrayList<String>();
	private final List<Application> applications = new ArrayList<Application>();
	private final Map<String, GeoCoverage> applicationCoverage =
		new HashMap<String, GeoCoverage>();

	public List<MobileDevice> getMobileDevices() {
		return Collections.unmodifiableList(mobileDevices);
	}

	/**
	 * Returns the users that can still participate in the simulation. The
	 * all-user registry returned by {@link #getMobileDevices()} is archival and
	 * is never shortened when a trace completes.
	 */
	public List<MobileDevice> getActiveMobileDevices() {
		return Collections.unmodifiableList(activeMobileDevices);
	}

	/** Initialises the active view from the already-built all-user registry. */
	public void activateRegisteredMobileDevices(List<MobileDevice> devices) {
		if (devices == null) {
			throw new IllegalArgumentException("Active mobile-device list cannot be null");
		}
		for (MobileDevice device : devices) {
			if (device == null || !mobileDevices.contains(device)) {
				throw new IllegalArgumentException(
					"Active mobile devices must belong to the all-user registry");
			}
		}
		activeMobileDevices.clear();
		for (MobileDevice device : devices) {
			if (!activeMobileDevices.contains(device)) {
				activeMobileDevices.add(device);
			}
		}
	}

	/** Retires a user from active processing without erasing its identity. */
	public boolean retireMobileDevice(MobileDevice device) {
		return device != null && activeMobileDevices.remove(device);
	}

	public List<FogDevice> getServerCloudlets() {
		return Collections.unmodifiableList(serverCloudlets);
	}

	public List<ApDevice> getAccessPoints() {
		return Collections.unmodifiableList(accessPoints);
	}

	public List<FogBroker> getBrokers() {
		return Collections.unmodifiableList(brokers);
	}

	public List<String> getApplicationIds() {
		return Collections.unmodifiableList(applicationIds);
	}

	public List<Application> getApplications() {
		return Collections.unmodifiableList(applications);
	}

	public Map<String, GeoCoverage> getApplicationCoverage() {
		return Collections.unmodifiableMap(applicationCoverage);
	}

	/* Package-private registries keep legacy orchestration inside this owner. */
	List<MobileDevice> mobileDeviceRegistry() {
		return mobileDevices;
	}

	List<FogDevice> serverCloudletRegistry() {
		return serverCloudlets;
	}

	List<ApDevice> accessPointRegistry() {
		return accessPoints;
	}

	List<FogBroker> brokerRegistry() {
		return brokers;
	}

	List<String> applicationIdRegistry() {
		return applicationIds;
	}

	List<Application> applicationRegistry() {
		return applications;
	}

	public void registerApplicationCoverage(String applicationId,
		GeoCoverage coverage) {
		if (applicationId == null || applicationId.trim().isEmpty()) {
			throw new IllegalArgumentException("Application ID cannot be empty");
		}
		applicationCoverage.put(applicationId, coverage);
	}

	public void unregisterApplicationCoverage(String applicationId) {
		if (applicationId != null) {
			applicationCoverage.remove(applicationId);
		}
	}
}
