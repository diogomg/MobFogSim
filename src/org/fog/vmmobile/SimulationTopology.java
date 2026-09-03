package org.fog.vmmobile;

import java.util.ArrayList;
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
	private final List<FogDevice> serverCloudlets = new ArrayList<FogDevice>();
	private final List<ApDevice> accessPoints = new ArrayList<ApDevice>();
	private final List<FogBroker> brokers = new ArrayList<FogBroker>();
	private final List<String> applicationIds = new ArrayList<String>();
	private final List<Application> applications = new ArrayList<Application>();
	private final Map<String, GeoCoverage> applicationCoverage =
		new HashMap<String, GeoCoverage>();

	public List<MobileDevice> getMobileDevices() {
		return mobileDevices;
	}

	public List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}

	public List<ApDevice> getAccessPoints() {
		return accessPoints;
	}

	public List<FogBroker> getBrokers() {
		return brokers;
	}

	public List<String> getApplicationIds() {
		return applicationIds;
	}

	public List<Application> getApplications() {
		return applications;
	}

	public Map<String, GeoCoverage> getApplicationCoverage() {
		return applicationCoverage;
	}
}
