package org.fog.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.Sensor;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.vmmobile.FogDeviceIndex;
import org.fog.vmmobile.SimulationContext;
import org.fog.vmmobile.SimulationServices;
import org.fog.vmmobile.port.SimulationEventLog;

public class Controller extends SimEntity {

	private List<FogDevice> fogDevices;
	private List<Sensor> sensors;
	private List<Actuator> actuators;

	private Map<String, Application> applications;
	private Map<String, Integer> appLaunchDelays;
	private ModuleMapping moduleMapping;
	private Map<Integer, Double> globalCurrentCpuLoad;
	private boolean shutdownRequested;
	private final SimulationEventLog events;
	private final SimulationResultsService resultsService;

	public Controller(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
		List<Actuator> actuators, ModuleMapping moduleMapping) {
		this(name, fogDevices, sensors, actuators, moduleMapping,
			SimulationServices.currentOrLegacy());
	}

	// CloudSim entities are registered only after controller topology setup.
	@SuppressWarnings("this-escape")
	public Controller(String name, List<FogDevice> fogDevices, List<Sensor> sensors,
		List<Actuator> actuators, ModuleMapping moduleMapping,
		SimulationServices services) {
		super(name);
		if (services == null) {
			throw new IllegalArgumentException("Simulation services cannot be null");
		}
		this.events = services.getEvents();
		this.resultsService = services.getResults();
		this.applications = new HashMap<String, Application>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setModuleMapping(moduleMapping);
		for (FogDevice fogDevice : fogDevices) {
			fogDevice.setControllerId(getId());
		}
		setFogDevices(fogDevices);
		setActuators(actuators);
		setSensors(sensors);
		services.getTopology().connectHierarchy(getFogDevices(), getFogDevices());
	}

	@Override
	public void startEntity() {
		for (String appId : applications.keySet()) {
			if (getAppLaunchDelays().get(appId) == 0)
				processAppSubmit(applications.get(appId));
			else {
				line("startEntity");
				send(getId(), getAppLaunchDelays().get(appId), FogEvents.APP_SUBMIT,
					applications.get(appId));
			}
		}

		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);

		send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);

		for (FogDevice dev : getFogDevices())
			sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

	}

	@Override
	public void processEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case FogEvents.APP_SUBMIT:
			processAppSubmit(ev);
			break;
		case FogEvents.CONTROLLER_RESOURCE_MANAGE:
			manageResources();
			break;
		case FogEvents.STOP_SIMULATION:
			requestSimulationStop();
			break;

		}
	}

	private void requestSimulationStop() {
		if (shutdownRequested) {
			return;
		}
		shutdownRequested = true;
		SimulationMetricsSnapshot metrics = resultsService.captureAndWrite(
			getFogDevices(), Collections.<ApDevice>emptyList(),
			Collections.<MobileDevice>emptyList(), getApplications());
		SimulationContext context = SimulationContext.currentOrNull();
		if (context != null) {
			context.recordMetrics(metrics);
		}
		CloudSim.terminateSimulation();
	}

	protected void manageResources() {
		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
	}

	private void line(String value) {
		events.detailLine(() -> value);
	}

	@Override
	public void shutdownEntity() {
	}

	public void submitApplication(Application application, int delay) {
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);
		appLaunchDelays.put(application.getAppId(), delay);
		for (Sensor sensor : sensors) {
			if (application.getAppId().equals(sensor.getAppId())) {
				sensor.setApp(application);
			}
		}
		for (Actuator ac : actuators) {
			if (application.getAppId().equals(ac.getAppId())) {
				ac.setApp(application);
			}
		}

		for (AppEdge edge : application.getEdges()) {
			if (edge.getEdgeType() == AppEdge.ACTUATOR) {
				String moduleName = edge.getSource();
				for (Actuator actuator : getActuators()) {
					if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
						application.getModuleByName(moduleName).subscribeActuator(actuator.getId(),
							edge.getTupleType());
				}
			}
		}

	}

	private void processAppSubmit(SimEvent ev) {
		Application app = (Application) ev.getData();
		processAppSubmit(app);
	}

	private void processAppSubmit(Application application) {
		line("Controller " + CloudSim.clock() + " Submitted application "
			+ application.getAppId());
		FogUtils.registerApplicationCoverage(application.getAppId(),
			application.getGeoCoverage());
		applications.put(application.getAppId(), application);

		ModulePlacement modulePlacement = new ModulePlacementMapping(getFogDevices(), application,
			getModuleMapping(), globalCurrentCpuLoad);
		for (FogDevice fogDevice : fogDevices) {
			sendNow(fogDevice.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		}

		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		Map<Integer, Map<String, Integer>> instanceCountMap = modulePlacement
			.getModuleInstanceCountMap();
		for (Integer deviceId : deviceToModuleMap.keySet()) {
			for (AppModule module : deviceToModuleMap.get(deviceId)) {
				line("processAppSubmit");
				sendNow(deviceId, FogEvents.APP_SUBMIT, application);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE_INSTANCE,
					new ModuleLaunchConfig(module, instanceCountMap.get(deviceId).get(
						module.getName())));
			}
		}
	}

	public List<FogDevice> getFogDevices() {
		return Collections.unmodifiableList(fogDevices);
	}

	public void setFogDevices(List<FogDevice> fogDevices) {
		List<FogDevice> copy = copyList(fogDevices, "Fog device list");
		FogDeviceIndex.copyOf(copy);
		this.fogDevices = copy;
	}

	public Map<String, Integer> getAppLaunchDelays() {
		return Collections.unmodifiableMap(appLaunchDelays);
	}

	public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
		this.appLaunchDelays = copyMap(appLaunchDelays,
			"Application launch delay map");
	}

	public Map<String, Application> getApplications() {
		return Collections.unmodifiableMap(applications);
	}

	public void setApplications(Map<String, Application> applications) {
		this.applications = copyMap(applications, "Application map");
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public List<Sensor> getSensors() {
		return Collections.unmodifiableList(sensors);
	}

	public void setSensors(List<Sensor> sensors) {
		for (Sensor sensor : sensors)
			sensor.setControllerId(getId());
		this.sensors = copyList(sensors, "Sensor list");
	}

	public List<Actuator> getActuators() {
		return Collections.unmodifiableList(actuators);
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = copyList(actuators, "Actuator list");
	}

	public Map<Integer, Double> getGlobalCurrentCpuLoad() {
		return globalCurrentCpuLoad == null ? null
			: Collections.unmodifiableMap(globalCurrentCpuLoad);
	}

	public void setGlobalCurrentCpuLoad(Map<Integer, Double> globalCurrentCpuLoad) {
		this.globalCurrentCpuLoad = globalCurrentCpuLoad == null ? null
			: new HashMap<Integer, Double>(globalCurrentCpuLoad);
	}

	private static <T> List<T> copyList(List<T> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new ArrayList<T>(values);
	}

	private static <K, V> Map<K, V> copyMap(Map<K, V> values,
		String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new HashMap<K, V>(values);
	}
}
