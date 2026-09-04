package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

public abstract class ModulePlacement {

	public static int ONLY_CLOUD = 1;
	public static int EDGEWARDS = 2;
	public static int USER_MAPPING = 3;

	private List<FogDevice> fogDevices;
	private List<MobileDevice> mobileDevices;
	private Application application;
	private Map<String, List<Integer>> moduleToDeviceMap;
	private Map<Integer, List<AppModule>> deviceToModuleMap;
	private Map<Integer, Map<String, Integer>> moduleInstanceCountMap;

	protected abstract void mapModules();

	protected boolean canBeCreated(FogDevice fogDevice, AppModule module) {
		return fogDevice.getVmAllocationPolicy().allocateHostForVm(module);
	}

	protected int getParentDevice(int fogDeviceId) {
		return ((FogDevice) CloudSim.getEntity(fogDeviceId)).getParentId();
	}

	protected FogDevice getFogDeviceById(int fogDeviceId) {
		return (FogDevice) CloudSim.getEntity(fogDeviceId);
	}

	protected boolean createModuleInstanceOnDevice(AppModule _module, final FogDevice device,
		int instanceCount) {
		return false;
	}

	protected boolean createModuleInstanceOnDevice(AppModule _module, final FogDevice device) {
		AppModule module = null;
		if (moduleToDeviceMap.containsKey(_module.getName()))
			module = new AppModule(_module);
		else
			module = _module;
		if (canBeCreated(device, module)) {
			System.out.println("Creating " + module.getName() + " on device " + device.getName());
			if (!deviceToModuleMap.containsKey(device.getId()))
				deviceToModuleMap.put(device.getId(), new ArrayList<AppModule>());
			deviceToModuleMap.get(device.getId()).add(module);

			if (!moduleToDeviceMap.containsKey(module.getName()))
				moduleToDeviceMap.put(module.getName(), new ArrayList<Integer>());
			moduleToDeviceMap.get(module.getName()).add(device.getId());
			return true;
		} else {
			System.out.println("Creating " + module.getName() + " on device " + device.getName()
				+ " was not possible");
			System.err.println("Module " + module.getName() + " cannot be created on device "
				+ device.getName());
			System.err.println("Terminating");
			return false;
		}
	}

	protected FogDevice getDeviceByName(String deviceName) {
		for (FogDevice dev : getFogDevices()) {
			if (dev.getName().equals(deviceName))
				return dev;
		}
		return null;
	}

	protected FogDevice getDeviceById(int id) {
		for (FogDevice dev : getFogDevices()) {
			if (dev.getId() == id)
				return dev;
		}
		return null;
	}

	public List<FogDevice> getFogDevices() {
		return Collections.unmodifiableList(fogDevices);
	}

	public void setFogDevices(List<FogDevice> fogDevices) {
		this.fogDevices = copyList(fogDevices, "Fog device list");
	}

	public Application getApplication() {
		return application;
	}

	public void setApplication(Application application) {
		this.application = application;
	}

	public Map<String, List<Integer>> getModuleToDeviceMap() {
		return immutableListMap(moduleToDeviceMap);
	}

	public void setModuleToDeviceMap(Map<String, List<Integer>> moduleToDeviceMap) {
		this.moduleToDeviceMap = copyListMap(moduleToDeviceMap,
			"Module-to-device map");
	}

	public Map<Integer, List<AppModule>> getDeviceToModuleMap() {
		return immutableListMap(deviceToModuleMap);
	}

	public void setDeviceToModuleMap(Map<Integer, List<AppModule>> deviceToModuleMap) {
		this.deviceToModuleMap = copyListMap(deviceToModuleMap,
			"Device-to-module map");
	}

	public Map<Integer, Map<String, Integer>> getModuleInstanceCountMap() {
		return immutableMapMap(moduleInstanceCountMap);
	}

	public void setModuleInstanceCountMap(Map<Integer, Map<String, Integer>> moduleInstanceCountMap) {
		this.moduleInstanceCountMap = copyMapMap(moduleInstanceCountMap,
			"Module instance count map");
	}

	public List<MobileDevice> getMobileDevices() {
		return mobileDevices == null ? null
			: Collections.unmodifiableList(mobileDevices);
	}

	public void setMobileDevices(List<MobileDevice> mobileDevices) {
		this.mobileDevices = mobileDevices == null ? null
			: new ArrayList<MobileDevice>(mobileDevices);
	}

	protected Map<Integer, Map<String, Integer>> moduleInstanceCountRegistry() {
		return moduleInstanceCountMap;
	}

	private static <T> List<T> copyList(List<T> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new ArrayList<T>(values);
	}

	private static <K, V> Map<K, List<V>> copyListMap(
		Map<K, List<V>> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		Map<K, List<V>> copy = new HashMap<K, List<V>>();
		for (Map.Entry<K, List<V>> entry : values.entrySet()) {
			copy.put(entry.getKey(), new ArrayList<V>(entry.getValue()));
		}
		return copy;
	}

	private static <K, V> Map<K, List<V>> immutableListMap(
		Map<K, List<V>> values) {
		Map<K, List<V>> copy = copyListMap(values, "Map");
		for (Map.Entry<K, List<V>> entry : copy.entrySet()) {
			entry.setValue(Collections.unmodifiableList(entry.getValue()));
		}
		return Collections.unmodifiableMap(copy);
	}

	private static <K, L, V> Map<K, Map<L, V>> copyMapMap(
		Map<K, Map<L, V>> values, String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		Map<K, Map<L, V>> copy = new HashMap<K, Map<L, V>>();
		for (Map.Entry<K, Map<L, V>> entry : values.entrySet()) {
			copy.put(entry.getKey(), new HashMap<L, V>(entry.getValue()));
		}
		return copy;
	}

	private static <K, L, V> Map<K, Map<L, V>> immutableMapMap(
		Map<K, Map<L, V>> values) {
		Map<K, Map<L, V>> copy = copyMapMap(values, "Map");
		for (Map.Entry<K, Map<L, V>> entry : copy.entrySet()) {
			entry.setValue(Collections.unmodifiableMap(entry.getValue()));
		}
		return Collections.unmodifiableMap(copy);
	}

}
