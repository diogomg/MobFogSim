package org.fog.placement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.vmmobile.SimulationBuildException;

public class ModulePlacementMapping extends ModulePlacement {

	private ModuleMapping moduleMapping;
	protected Map<Integer, Double> currentCpuLoad;

	@Override
	protected void mapModules() {
		Map<String, Map<String, Integer>> mapping = moduleMapping.getModuleMapping();

		for (String deviceName : mapping.keySet()) {
			FogDevice device = getDeviceByName(deviceName);
			if (device == null) {
				throw new SimulationBuildException(
					"Module mapping references unknown device " + deviceName);
			}
			for (String moduleName : mapping.get(deviceName).keySet()) {
				AppModule module = getApplication().getModuleByName(moduleName);
				if (module == null)
					continue;

				int numModules = mapping.get(deviceName).get(moduleName).intValue();
				if (!createModuleInstanceOnDevice(module, device)) {
					throw new SimulationBuildException("Could not place module "
						+ moduleName + " on " + deviceName);
				}
				getCurrentCpuLoad().put(device.getId(),
					getCurrentCpuLoad().get(device.getId()) + (module.getMips() * numModules));
				moduleInstanceCountRegistry().get(device.getId()).put(moduleName,
					mapping.get(deviceName).get(moduleName));
			}
		}
	}

	protected void mapModulesMigrate(FogDevice serverCloudlet) {
		Map<String, Map<String, Integer>> mapping = moduleMapping.getModuleMapping();

		System.out.println("ModulePlacementMapping.java: " + serverCloudlet.getName());
		System.out.println("mapping: " + mapping);
		for (String moduleName : mapping.get(serverCloudlet.getName()).keySet()) {
			AppModule module = getApplication().getModuleByName(moduleName);
			if (module == null)
				continue;
			int numModules = mapping.get(serverCloudlet.getName()).get(moduleName).intValue();
			if (!createModuleInstanceOnDevice(module, serverCloudlet)) {
				throw new SimulationBuildException("Could not place migrating module "
					+ moduleName + " on " + serverCloudlet.getName());
			}
			getCurrentCpuLoad().put(serverCloudlet.getId(),
				getCurrentCpuLoad().get(serverCloudlet.getId()) + (module.getMips() * numModules));
			moduleInstanceCountRegistry().get(serverCloudlet.getId()).put(moduleName,
				mapping.get(serverCloudlet.getName()).get(moduleName));
			return;
		}
	}

	public ModulePlacementMapping(List<FogDevice> fogDevices, Application application,
		ModuleMapping moduleMapping, Map<Integer, Double> globalCPULoad) {
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		setCurrentCpuLoad(globalCPULoad);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
		for (FogDevice device : getFogDevices())
			moduleInstanceCountRegistry().put(device.getId(),
				new HashMap<String, Integer>());
		mapModules();
	}

	public ModulePlacementMapping(List<FogDevice> fogDevices, Application application,
		ModuleMapping moduleMapping, Map<Integer, Double> globalCPULoad, boolean migration) {
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		setCurrentCpuLoad(globalCPULoad);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
		for (FogDevice device : getFogDevices()) {
			if (moduleInstanceCountRegistry().put(device.getId(),
				new HashMap<String, Integer>()) == null) {
				System.out.println(getModuleInstanceCountMap() + " - " + device.getName());
			}
			mapModulesMigrate(device);
		}
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, Double> getCurrentCpuLoad() {
		return currentCpuLoad;
	}

	public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
		this.currentCpuLoad = currentCpuLoad;
	}

}
