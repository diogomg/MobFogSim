package org.fog.placement;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ModuleMapping {
	/**
	 * Mapping from node name to list of <moduleName, numInstances> of instances
	 * to be launched on node
	 */
	protected Map<String, Map<String, Integer>> moduleMapping;

	public static ModuleMapping createModuleMapping() {
		return new ModuleMapping();
	}

	public Map<String, Map<String, Integer>> getModuleMapping() {
		Map<String, Map<String, Integer>> snapshot =
			new HashMap<String, Map<String, Integer>>();
		for (Map.Entry<String, Map<String, Integer>> entry
			: moduleMapping.entrySet()) {
			snapshot.put(entry.getKey(), Collections.unmodifiableMap(
				new HashMap<String, Integer>(entry.getValue())));
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public void setModuleMapping(Map<String, Map<String, Integer>> moduleMapping) {
		if (moduleMapping == null) {
			throw new IllegalArgumentException("Module mapping cannot be null");
		}
		this.moduleMapping = new HashMap<String, Map<String, Integer>>();
		for (Map.Entry<String, Map<String, Integer>> entry
			: moduleMapping.entrySet()) {
			this.moduleMapping.put(entry.getKey(),
				new HashMap<String, Integer>(entry.getValue()));
		}
	}

	protected ModuleMapping() {
		moduleMapping = new HashMap<String, Map<String, Integer>>();
	}

	/**
	 * Add 1 instance of module moduleName to device deviceName
	 * 
	 * @param moduleName
	 * @param deviceName
	 */
	public void addModuleToDevice(String moduleName, String deviceName) {
		addModuleToDevice(moduleName, deviceName, 1);
	}

	/**
	 * Add <b>instanceCount</b> number of instances of module <b>moduleName</b>
	 * to <b>device deviceName</b>
	 * 
	 * @param moduleName
	 * @param deviceName
	 * @param instanceCount
	 */
	public void addModuleToDevice(String moduleName, String deviceName, int instanceCount) {
		if (moduleName == null || moduleName.trim().isEmpty()
			|| deviceName == null || deviceName.trim().isEmpty()) {
			throw new IllegalArgumentException(
				"Module and device names cannot be empty");
		}
		if (instanceCount < 0) {
			throw new IllegalArgumentException("Instance count cannot be negative");
		}
		if (!moduleMapping.containsKey(deviceName))
			moduleMapping.put(deviceName, new HashMap<String, Integer>());
		if (!moduleMapping.get(deviceName).containsKey(moduleName))
			moduleMapping.get(deviceName).put(moduleName, instanceCount);
	}

	public boolean removeDevice(String deviceName) {
		return moduleMapping.remove(deviceName) != null;
	}

	/** Removes one module from every device and prunes empty device entries. */
	public boolean removeModule(String moduleName) {
		if (moduleName == null || moduleName.trim().isEmpty()) {
			throw new IllegalArgumentException("Module name cannot be empty");
		}
		boolean removed = false;
		Iterator<Map.Entry<String, Map<String, Integer>>> entries =
			moduleMapping.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry<String, Map<String, Integer>> entry = entries.next();
			removed |= entry.getValue().remove(moduleName) != null;
			if (entry.getValue().isEmpty()) {
				entries.remove();
			}
		}
		return removed;
	}

	/** Moves VM placement between devices without exposing intermediate state. */
	public void moveModule(String sourceDeviceName, String destinationDeviceName,
		String moduleName, int instanceCount) {
		if (sourceDeviceName == null || sourceDeviceName.trim().isEmpty()) {
			throw new IllegalArgumentException("Source device name cannot be empty");
		}
		if (moduleName == null || moduleName.trim().isEmpty()
			|| destinationDeviceName == null
			|| destinationDeviceName.trim().isEmpty() || instanceCount < 0) {
			throw new IllegalArgumentException(
				"Destination, module, and non-negative count are required");
		}
		moduleMapping.remove(sourceDeviceName);
		Map<String, Integer> destination =
			moduleMapping.get(destinationDeviceName);
		if (destination == null) {
			destination = new HashMap<String, Integer>();
			moduleMapping.put(destinationDeviceName, destination);
		}
		if (!destination.containsKey(moduleName)) {
			destination.put(moduleName, instanceCount);
		}
	}

}
