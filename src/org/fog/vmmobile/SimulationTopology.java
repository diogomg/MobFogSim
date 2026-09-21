package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.fog.application.Application;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.GeoCoverage;

/** Mutable topology and application registry scoped to one simulation run. */
public final class SimulationTopology {
	private final Map<Integer, FogDevice> fogDevicesById =
		new HashMap<Integer, FogDevice>();
	private final Map<String, FogDevice> fogDevicesByName =
		new HashMap<String, FogDevice>();
	private final List<MobileDevice> mobileDevices =
		new IndexedFogDeviceList<MobileDevice>();
	private final List<MobileDevice> activeMobileDevices =
		new ArrayList<MobileDevice>();
	private final List<FogDevice> serverCloudlets =
		new IndexedFogDeviceList<FogDevice>();
	private final List<ApDevice> accessPoints =
		new IndexedFogDeviceList<ApDevice>();
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

	/** Returns any registered server, access point, or mobile device by ID. */
	public FogDevice getFogDeviceById(int entityId) {
		return fogDevicesById.get(entityId);
	}

	/** Returns any registered server, access point, or mobile device by name. */
	public FogDevice getFogDeviceByName(String name) {
		return name == null ? null : fogDevicesByName.get(name);
	}

	public Map<Integer, FogDevice> getFogDevicesById() {
		return Collections.unmodifiableMap(fogDevicesById);
	}

	public Map<String, FogDevice> getFogDevicesByName() {
		return Collections.unmodifiableMap(fogDevicesByName);
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

	private void validateAvailable(FogDevice device, FogDevice replaced,
		Map<Integer, FogDevice> ids, Map<String, FogDevice> names) {
		if (device == null) {
			throw new IllegalArgumentException("Fog device cannot be null");
		}
		String name = device.getName();
		if (device.getId() >= 0) {
			FogDevice matchingId = ids.get(device.getId());
			if (matchingId != null && matchingId != replaced) {
				throw new IllegalArgumentException("Duplicate fog device ID "
					+ device.getId());
			}
		}
		if (name != null && !name.trim().isEmpty()) {
			FogDevice matchingName = names.get(name);
			if (matchingName != null && matchingName != replaced) {
				throw new IllegalArgumentException(
					"Duplicate fog device name " + name);
			}
		}
	}

	private void index(FogDevice device) {
		if (device.getId() >= 0) {
			fogDevicesById.put(device.getId(), device);
		}
		if (device.getName() != null && !device.getName().trim().isEmpty()) {
			fogDevicesByName.put(device.getName(), device);
		}
	}

	private void unindex(FogDevice device) {
		if (device.getId() >= 0 && fogDevicesById.get(device.getId()) == device) {
			fogDevicesById.remove(device.getId());
		}
		if (device.getName() != null
			&& fogDevicesByName.get(device.getName()) == device) {
			fogDevicesByName.remove(device.getName());
		}
	}

	/**
	 * Mutation boundary that updates ordered topology membership and both lookup
	 * indexes as one operation.
	 */
	private final class IndexedFogDeviceList<T extends FogDevice>
		extends ArrayList<T> {
		private static final long serialVersionUID = 1L;

		@Override
		public void add(int index, T device) {
			validateAvailable(device, null, fogDevicesById, fogDevicesByName);
			super.add(index, device);
			index(device);
		}

		@Override
		public boolean add(T device) {
			add(size(), device);
			return true;
		}

		@Override
		public boolean addAll(Collection<? extends T> devices) {
			return addAll(size(), devices);
		}

		@Override
		public boolean addAll(int index, Collection<? extends T> devices) {
			if (devices == null) {
				throw new IllegalArgumentException("Fog device collection cannot be null");
			}
			List<T> additions = new ArrayList<T>(devices);
			if (additions.isEmpty()) {
				return false;
			}
			Map<Integer, FogDevice> candidateIds =
				new HashMap<Integer, FogDevice>(fogDevicesById);
			Map<String, FogDevice> candidateNames =
				new HashMap<String, FogDevice>(fogDevicesByName);
			for (T device : additions) {
				validateAvailable(device, null, candidateIds, candidateNames);
				if (device.getId() >= 0) {
					candidateIds.put(device.getId(), device);
				}
				if (device.getName() != null
					&& !device.getName().trim().isEmpty()) {
					candidateNames.put(device.getName(), device);
				}
			}
			super.addAll(index, additions);
			for (T device : additions) {
				index(device);
			}
			return true;
		}

		@Override
		public T set(int index, T device) {
			T previous = super.get(index);
			if (previous == device) {
				return previous;
			}
			validateAvailable(device, previous, fogDevicesById, fogDevicesByName);
			unindex(previous);
			super.set(index, device);
			index(device);
			return previous;
		}

		@Override
		public T remove(int index) {
			T removed = super.remove(index);
			unindex(removed);
			return removed;
		}

		@Override
		public boolean remove(Object device) {
			int index = indexOf(device);
			if (index < 0) {
				return false;
			}
			remove(index);
			return true;
		}

		@Override
		public boolean removeAll(Collection<?> devices) {
			return removeMatching(new Predicate<T>() {
				@Override
				public boolean test(T device) {
					return devices.contains(device);
				}
			});
		}

		@Override
		public boolean retainAll(Collection<?> devices) {
			return removeMatching(new Predicate<T>() {
				@Override
				public boolean test(T device) {
					return !devices.contains(device);
				}
			});
		}

		@Override
		public boolean removeIf(Predicate<? super T> filter) {
			if (filter == null) {
				throw new NullPointerException("Fog device filter cannot be null");
			}
			return removeMatching(filter);
		}

		private boolean removeMatching(Predicate<? super T> filter) {
			boolean changed = false;
			for (int index = size() - 1; index >= 0; index--) {
				if (filter.test(get(index))) {
					remove(index);
					changed = true;
				}
			}
			return changed;
		}

		@Override
		protected void removeRange(int fromIndex, int toIndex) {
			List<T> removed = new ArrayList<T>(subList(fromIndex, toIndex));
			super.removeRange(fromIndex, toIndex);
			for (T device : removed) {
				unindex(device);
			}
		}

		@Override
		public void replaceAll(UnaryOperator<T> operator) {
			if (operator == null) {
				throw new NullPointerException("Fog device operator cannot be null");
			}
			List<T> replacements = new ArrayList<T>(size());
			for (T device : this) {
				replacements.add(operator.apply(device));
			}
			replaceContents(replacements);
		}

		private void replaceContents(List<T> replacements) {
			Map<Integer, FogDevice> candidateIds =
				new HashMap<Integer, FogDevice>(fogDevicesById);
			Map<String, FogDevice> candidateNames =
				new HashMap<String, FogDevice>(fogDevicesByName);
			for (T device : this) {
				candidateIds.remove(device.getId());
				candidateNames.remove(device.getName());
			}
			for (T device : replacements) {
				validateAvailable(device, null, candidateIds, candidateNames);
				if (device.getId() >= 0) {
					candidateIds.put(device.getId(), device);
				}
				if (device.getName() != null
					&& !device.getName().trim().isEmpty()) {
					candidateNames.put(device.getName(), device);
				}
			}
			clear();
			addAll(replacements);
		}

		@Override
		public void clear() {
			if (isEmpty()) {
				return;
			}
			List<T> removed = new ArrayList<T>(this);
			super.clear();
			for (T device : removed) {
				unindex(device);
			}
		}
	}
}
