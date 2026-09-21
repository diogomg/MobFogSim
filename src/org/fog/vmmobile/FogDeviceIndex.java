package org.fog.vmmobile;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fog.entities.FogDevice;

/** Immutable ID and name indexes over one stable fog-device collection. */
public final class FogDeviceIndex {
	private static final FogDeviceIndex EMPTY = new FogDeviceIndex(
		Collections.<Integer, FogDevice>emptyMap(),
		Collections.<String, FogDevice>emptyMap());

	private final Map<Integer, FogDevice> byId;
	private final Map<String, FogDevice> byName;

	private FogDeviceIndex(Map<Integer, FogDevice> byId,
		Map<String, FogDevice> byName) {
		this.byId = Collections.unmodifiableMap(byId);
		this.byName = Collections.unmodifiableMap(byName);
	}

	public static FogDeviceIndex empty() {
		return EMPTY;
	}

	public static FogDeviceIndex copyOf(List<? extends FogDevice> devices) {
		if (devices == null) {
			throw new IllegalArgumentException("Fog device list cannot be null");
		}
		Map<Integer, FogDevice> byId = new HashMap<Integer, FogDevice>();
		Map<String, FogDevice> byName = new HashMap<String, FogDevice>();
		for (FogDevice device : devices) {
			index(device, byId, byName);
		}
		return byId.isEmpty() ? EMPTY : new FogDeviceIndex(byId, byName);
	}

	private static void index(FogDevice device,
		Map<Integer, FogDevice> byId, Map<String, FogDevice> byName) {
		if (device == null) {
			throw new IllegalArgumentException("Fog device cannot be null");
		}
		String name = device.getName();
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Fog device name cannot be empty");
		}
		FogDevice duplicateId = byId.put(device.getId(), device);
		if (duplicateId != null) {
			throw new IllegalArgumentException("Duplicate fog device ID "
				+ device.getId());
		}
		FogDevice duplicateName = byName.put(name, device);
		if (duplicateName != null) {
			throw new IllegalArgumentException("Duplicate fog device name " + name);
		}
	}

	public FogDevice getById(int id) {
		return byId.get(id);
	}

	public FogDevice getByName(String name) {
		return name == null ? null : byName.get(name);
	}

	public Map<Integer, FogDevice> byId() {
		return byId;
	}

	public Map<String, FogDevice> byName() {
		return byName;
	}
}
