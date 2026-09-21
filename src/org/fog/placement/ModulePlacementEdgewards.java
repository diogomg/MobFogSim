package org.fog.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.vmmobile.SimulationEventSink;

public class ModulePlacementEdgewards extends ModulePlacement {
	private final SimulationEventSink events = SimulationEventSink.current();

	protected ModuleMapping moduleMapping;
	protected List<Sensor> sensors;
	protected List<Actuator> actuators;
	protected Map<Integer, Double> currentCpuLoad;

	/**
	 * Stores the current mapping of application modules to fog devices
	 */
	protected Map<Integer, List<String>> currentModuleMap;
	protected Map<Integer, Map<String, Double>> currentModuleLoadMap;
	protected Map<Integer, Map<String, Integer>> currentModuleInstanceNum;

	public ModulePlacementEdgewards(List<FogDevice> fogDevices, List<Sensor> sensors,
		List<Actuator> actuators, Application application, ModuleMapping moduleMapping) {
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		setSensors(sensors);
		setActuators(actuators);
		setCurrentCpuLoad(new HashMap<Integer, Double>());
		updateCurrentCpuLoad();
		setCurrentModuleMap(new HashMap<Integer, List<String>>());
		setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
		setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
		for (FogDevice dev : getFogDevices()) {
			getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
			getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
			getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
		}

		mapModules();

		events.trace("ModulePlacementEdgewards", () ->
			"Module instances " + getCurrentModuleInstanceNum());
		setModuleInstanceCountMap(getCurrentModuleInstanceNum());
	}

	/**
	 * Function to calculate the available MIPS on each device at the time
	 * placement is called.
	 */
	private void updateCurrentCpuLoad() {
		for (FogDevice device : getFogDevices()) {
			getCurrentCpuLoad().put(device.getId(), 0.0);
		}
	}

	@Override
	protected void mapModules() {

		for (String deviceName : getModuleMapping().getModuleMapping().keySet()) {
			for (String moduleName : getModuleMapping().getModuleMapping().get(deviceName).keySet()) {
				int deviceId = CloudSim.getEntityId(deviceName);
				getCurrentModuleMap().get(deviceId).add(moduleName);
				getCurrentModuleLoadMap().get(deviceId).put(moduleName, 0.0);
				getCurrentModuleInstanceNum().get(deviceId).put(moduleName, 0);
			}
		}

		List<List<Integer>> leafToRootPaths = getLeafToRootPaths();

		for (List<Integer> path : leafToRootPaths) {
			placeModulesInPath(path);
		}

		for (int deviceId : getCurrentModuleMap().keySet()) {
			events.trace("ModulePlacementEdgewards", () ->
				getFogDeviceById(deviceId).getName() + " ---> "
					+ getCurrentModuleMap().get(deviceId));
			for (String module : getCurrentModuleMap().get(deviceId)) {
				createModuleInstanceOnDevice(getApplication().getModuleByName(module),
					getFogDeviceById(deviceId));
			}
		}
	}

	private List<String> getModulesToPlace(List<String> placedOperators) {
		events.trace("ModulePlacementEdgewards", () ->
			"Placed modules " + placedOperators);
		Application app = getApplication();
		List<String> modulesToPlace_1 = new ArrayList<String>();
		List<String> modulesToPlace = new ArrayList<String>();
		for (AppModule module : app.getModules()) {
			if (!placedOperators.contains(module.getName()))
				modulesToPlace_1.add(module.getName());
		}
		for (String moduleName : modulesToPlace_1) {
			boolean toBePlaced = true;

			for (AppEdge edge : app.getEdges()) {
				if (edge.getSource().equals(moduleName) && edge.getDirection() == Tuple.DOWN
					&& !placedOperators.contains(edge.getDestination()))
					toBePlaced = false;
				if (edge.getDestination().equals(moduleName) && edge.getDirection() == Tuple.UP
					&& !placedOperators.contains(edge.getSource()))
					toBePlaced = false;
			}
			if (toBePlaced)
				modulesToPlace.add(moduleName);
		}
		events.trace("ModulePlacementEdgewards", () ->
			"Modules to place " + modulesToPlace);

		return modulesToPlace;
	}

	protected double getRateOfSensor(String sensorType) {
		for (Sensor sensor : getSensors()) {
			if (sensor.getTupleType().equals(sensorType))
				return 1 / sensor.getTransmitDistribution().getMeanInterTransmitTime();
		}
		return 0;
	}

	private void placeModulesInPath(List<Integer> path) {
		if (path.size() == 0)
			return;
		List<String> placedOperators = new ArrayList<String>();
		Map<AppEdge, Double> appEdgeToRate = new HashMap<AppEdge, Double>();

		for (AppEdge edge : getApplication().getEdges()) {
			if (edge.isPeriodic()) {
				appEdgeToRate.put(edge, 1 / edge.getPeriodicity());
			}
		}

		for (Integer deviceId : path) {
			events.trace("ModulePlacementEdgewards", () ->
				"On device " + CloudSim.getEntityName(deviceId));
			FogDevice device = getFogDeviceById(deviceId);
			Map<String, Integer> sensorsAssociated = getAssociatedSensors(device);
			Map<String, Integer> actuatorsAssociated = getAssociatedActuators(device);
			// Adding all sensors to placed list
			placedOperators.addAll(sensorsAssociated.keySet());
			// Adding all actuators to placed list
			placedOperators.addAll(actuatorsAssociated.keySet());
			events.trace("ModulePlacementEdgewards", () ->
				"Currently placed operators " + placedOperators);
			for (String sensor : sensorsAssociated.keySet()) {
				for (AppEdge edge : getApplication().getEdges()) {
					if (edge.getSource().equals(sensor)) {
						appEdgeToRate.put(edge, sensorsAssociated.get(sensor)
							* getRateOfSensor(sensor));
					}
				}
			}

			// now updating the AppEdge rates for the entire application based
			// on the knowledge so far
			boolean changed = true;
			while (changed) {// Loop runs as long as something new is added to the map
				changed = false;
				Map<AppEdge, Double> rateMap = new HashMap<AppEdge, Double>(appEdgeToRate);
				for (AppEdge edge : rateMap.keySet()) {
					AppModule destModule = getApplication().getModuleByName(edge.getDestination());
					if (destModule == null)
						continue;
					Map<Pair<String, String>, SelectivityModel> map =
						destModule.getSelectivityMap();
					for (Pair<String, String> pair : map.keySet()) {
						if (pair.getFirst().equals(edge.getTupleType())) {
							double outputRate = appEdgeToRate.get(edge)
								* map.get(pair).getMeanRate();
							AppEdge outputEdge = getApplication().getEdgeMap()
								.get(pair.getSecond());
							if (!appEdgeToRate.containsKey(outputEdge)
								|| appEdgeToRate.get(outputEdge) != outputRate) {
								events.trace("ModulePlacementEdgewards", () ->
									outputEdge.getSource() + " ---> "
										+ outputEdge.getDestination() + ": " + outputRate);
								changed = true;
							}
							appEdgeToRate.put(outputEdge, outputRate);
						}
					}
				}
			}

			List<String> operatorsToPlace = getModulesToPlace(placedOperators);

			while (operatorsToPlace.size() > 0) {
				String operatorName = operatorsToPlace.get(0);
				double totalCpuLoad = 0;

				int upsteamDeviceId = isPlacedUpstream(operatorName, path);
				if (upsteamDeviceId > 0) {
					if (upsteamDeviceId == deviceId) {
						placedOperators.add(operatorName);
						operatorsToPlace = getModulesToPlace(placedOperators);

						// take all incoming edges
						for (AppEdge edge : getApplication().getEdges()) {
							if (edge.getDestination().equals(operatorName)) {
								double rate = appEdgeToRate.get(edge);
								totalCpuLoad += rate * edge.getTupleCpuLength();
							}
						}
						if (totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost()
							.getTotalMips()) {
							List<String> _placedOperators = shiftModuleNorth(operatorName,
								totalCpuLoad, deviceId);
							operatorsToPlace.removeAll(_placedOperators);
							for (String placedOperator : _placedOperators) {
								if (!placedOperators.contains(placedOperator))
									placedOperators.add(placedOperator);
							}
						} else {
							placedOperators.add(operatorName);
							getCurrentCpuLoad().put(deviceId,
								getCurrentCpuLoad().get(deviceId) + totalCpuLoad);
							getCurrentModuleInstanceNum().get(deviceId).put(operatorName,
								getCurrentModuleInstanceNum().get(deviceId).get(operatorName) + 1);
						}
					}
				} else {
					// Finding out whether placement of operator on device is possible
					for (AppEdge edge : getApplication().getEdges()) {// take all incoming edges
						if (edge.getDestination().equals(operatorName)) {
							double rate = appEdgeToRate.get(edge);
							totalCpuLoad += rate * edge.getTupleCpuLength();
							events.trace("ModulePlacementEdgewards", () ->
								"Tuple " + edge.getTupleType() + " rate " + rate
									+ " CPU load " + rate * edge.getTupleCpuLength());
						}
					}
					events.trace("ModulePlacementEdgewards", () ->
						"Trying to place module " + operatorName + " on "
							+ device.getName());

					if (totalCpuLoad + getCurrentCpuLoad().get(deviceId) > device.getHost().getTotalMips()) {
						final double rejectedCpuLoad = totalCpuLoad;
						events.trace("ModulePlacementEdgewards", () ->
							"Placement of " + operatorName + " rejected on "
								+ device.getName() + ": requested " + rejectedCpuLoad
								+ ", current " + getCurrentCpuLoad().get(deviceId)
								+ ", capacity " + device.getHost().getTotalMips());
					}
					else {
						getCurrentCpuLoad().put(deviceId,
							totalCpuLoad + getCurrentCpuLoad().get(deviceId));

						events.trace("ModulePlacementEdgewards", () ->
							"Placed " + operatorName + " on " + device.getName()
								+ "; updated CPU load "
								+ getCurrentCpuLoad().get(deviceId));
						if (!currentModuleMap.containsKey(deviceId))
							currentModuleMap.put(deviceId, new ArrayList<String>());
						currentModuleMap.get(deviceId).add(operatorName);
						placedOperators.add(operatorName);
						operatorsToPlace = getModulesToPlace(placedOperators);
						getCurrentModuleLoadMap().get(device.getId()).put(operatorName,
							totalCpuLoad);

						int max = 1;
						for (AppEdge edge : getApplication().getEdges()) {
							if (edge.getSource().equals(operatorName)
								&& actuatorsAssociated.containsKey(edge.getDestination()))
								max = Math.max(actuatorsAssociated.get(edge.getDestination()), max);
							if (edge.getDestination().equals(operatorName)
								&& sensorsAssociated.containsKey(edge.getSource()))
								max = Math.max(sensorsAssociated.get(edge.getSource()), max);
						}
						getCurrentModuleInstanceNum().get(deviceId).put(operatorName, max);
						final int instanceCount = max;
						events.trace("ModulePlacementEdgewards", () ->
							"Instances for " + operatorName + ": " + instanceCount);
					}
				}
				operatorsToPlace.remove(operatorName);
			}
		}
	}

	/**
	 * Shifts a module moduleName from device deviceId northwards
	 * 
	 * @param moduleName
	 * @param cpuLoad
	 *        cpuLoad of the module
	 * @param deviceId
	 */
	protected List<String> shiftModuleNorth(String moduleName, double cpuLoad,
		Integer deviceId) {
		events.detail("ModulePlacementEdgewards", () ->
			"Shifting module " + moduleName + " northwards");

		PlacementPlan plan = createPlacementPlan(moduleName, cpuLoad, deviceId);
		if (plan == null) {
			events.detail("ModulePlacementEdgewards", () ->
				"Could not place module " + moduleName + " northwards");
			return Collections.emptyList();
		}

		commitPlacementPlan(plan);
		events.detail("ModulePlacementEdgewards", () -> "Placed "
			+ plan.getPlacedModules() + " at device "
			+ CloudSim.getEntityName(plan.getDestinationDeviceId()));
		return plan.getPlacedModules();
	}

	private PlacementPlan createPlacementPlan(String moduleName, double cpuLoad,
		Integer sourceDeviceId) {
		if (moduleName == null || moduleName.trim().isEmpty()) {
			throw new IllegalArgumentException("Module name cannot be empty");
		}
		validateNonNegativeFinite(cpuLoad, "Additional CPU load");
		if (sourceDeviceId == null) {
			throw new IllegalArgumentException("Source device ID cannot be null");
		}

		PlacementState state = copyCurrentPlacementState();
		validateDeviceState(state, sourceDeviceId);
		validateApplicationModule(moduleName);

		List<String> modulesToShift = findModulesToShift(moduleName, sourceDeviceId,
			state.moduleMap);
		Map<String, Double> movingLoads = new LinkedHashMap<String, Double>();
		Map<String, Integer> movingInstances = new LinkedHashMap<String, Integer>();
		double totalCpuLoad = 0.0;

		for (String module : modulesToShift) {
			validateApplicationModule(module);
			double moduleLoad = requireModuleLoad(state, sourceDeviceId, module);
			int instanceCount = requireModuleInstanceCount(state, sourceDeviceId, module);
			movingLoads.put(module, moduleLoad);
			movingInstances.put(module, incrementInstanceCount(instanceCount, module));
			totalCpuLoad = addFinite(totalCpuLoad, moduleLoad, "Shifted CPU load");
			removeModule(state, sourceDeviceId, module, moduleLoad);
		}

		double expandedModuleLoad = addFinite(movingLoads.get(moduleName), cpuLoad,
			"CPU load for module " + moduleName);
		movingLoads.put(moduleName, expandedModuleLoad);
		totalCpuLoad = addFinite(totalCpuLoad, cpuLoad, "Shifted CPU load");

		Set<Integer> visitedDevices = new HashSet<Integer>();
		visitedDevices.add(sourceDeviceId);
		int candidateId = getValidatedParentId(sourceDeviceId);
		while (candidateId != -1) {
			if (!visitedDevices.add(candidateId)) {
				throw new IllegalStateException(
					"Cycle detected in fog-device parent hierarchy at device " + candidateId);
			}

			validateDeviceState(state, candidateId);
			FogDevice candidate = requireFogDevice(candidateId);
			double capacity = candidate.getHost().getTotalMips();
			validateNonNegativeFinite(capacity,
				"CPU capacity for device " + candidate.getName());
			double candidateLoad = state.cpuLoad.get(candidateId);
			double combinedLoad = addFinite(candidateLoad, totalCpuLoad,
				"CPU load for device " + candidate.getName());

			if (combinedLoad <= capacity) {
				addModules(state, candidateId, movingLoads, movingInstances,
					totalCpuLoad);
				return new PlacementPlan(state, candidateId,
					new ArrayList<String>(movingLoads.keySet()));
			}

			List<String> expandedModules = findModulesToShift(modulesToShift,
				candidateId, state.moduleMap);
			for (String module : expandedModules) {
				if (modulesToShift.contains(module)) {
					continue;
				}
				validateApplicationModule(module);
				double moduleLoad = requireModuleLoad(state, candidateId, module);
				int instanceCount = requireModuleInstanceCount(state, candidateId, module);
				movingLoads.put(module, moduleLoad);
				movingInstances.put(module, instanceCount);
				totalCpuLoad = addFinite(totalCpuLoad, moduleLoad, "Shifted CPU load");
				removeModule(state, candidateId, module, moduleLoad);
			}
			modulesToShift = expandedModules;
			candidateId = getValidatedParentId(candidateId);
		}

		return null;
	}

	private void commitPlacementPlan(PlacementPlan plan) {
		PlacementState committedState = plan.toMutableState();
		setCurrentCpuLoad(committedState.cpuLoad);
		setCurrentModuleMap(committedState.moduleMap);
		setCurrentModuleLoadMap(committedState.moduleLoadMap);
		setCurrentModuleInstanceNum(committedState.moduleInstanceCount);
		setModuleInstanceCountMap(committedState.moduleInstanceCount);
	}

	private PlacementState copyCurrentPlacementState() {
		if (getCurrentCpuLoad() == null || getCurrentModuleMap() == null
			|| getCurrentModuleLoadMap() == null || getCurrentModuleInstanceNum() == null) {
			throw new IllegalStateException("Placement state has not been initialized");
		}

		Map<Integer, Double> cpuLoad = new HashMap<Integer, Double>(getCurrentCpuLoad());
		Map<Integer, List<String>> moduleMap = new HashMap<Integer, List<String>>();
		for (Map.Entry<Integer, List<String>> entry : getCurrentModuleMap().entrySet()) {
			if (entry.getValue() == null) {
				throw new IllegalStateException(
					"Module list is missing for device " + entry.getKey());
			}
			moduleMap.put(entry.getKey(), new ArrayList<String>(entry.getValue()));
		}

		Map<Integer, Map<String, Double>> moduleLoadMap =
			new HashMap<Integer, Map<String, Double>>();
		for (Map.Entry<Integer, Map<String, Double>> entry
			: getCurrentModuleLoadMap().entrySet()) {
			if (entry.getValue() == null) {
				throw new IllegalStateException(
					"Module-load map is missing for device " + entry.getKey());
			}
			moduleLoadMap.put(entry.getKey(),
				new HashMap<String, Double>(entry.getValue()));
		}

		Map<Integer, Map<String, Integer>> moduleInstanceCount =
			new HashMap<Integer, Map<String, Integer>>();
		for (Map.Entry<Integer, Map<String, Integer>> entry
			: getCurrentModuleInstanceNum().entrySet()) {
			if (entry.getValue() == null) {
				throw new IllegalStateException(
					"Module-instance map is missing for device " + entry.getKey());
			}
			moduleInstanceCount.put(entry.getKey(),
				new HashMap<String, Integer>(entry.getValue()));
		}
		return new PlacementState(cpuLoad, moduleMap, moduleLoadMap,
			moduleInstanceCount);
	}

	private void validateDeviceState(PlacementState state, int deviceId) {
		if (!state.cpuLoad.containsKey(deviceId) || state.cpuLoad.get(deviceId) == null
			|| !state.moduleMap.containsKey(deviceId)
			|| !state.moduleLoadMap.containsKey(deviceId)
			|| !state.moduleInstanceCount.containsKey(deviceId)) {
			throw new IllegalStateException(
				"Incomplete placement state for device " + deviceId);
		}
		validateNonNegativeFinite(state.cpuLoad.get(deviceId),
			"Current CPU load for device " + deviceId);
	}

	private double requireModuleLoad(PlacementState state, int deviceId, String module) {
		validateModuleState(state, deviceId, module);
		double load = state.moduleLoadMap.get(deviceId).get(module);
		validateNonNegativeFinite(load,
			"CPU load for module " + module + " on device " + deviceId);
		return load;
	}

	private int requireModuleInstanceCount(PlacementState state, int deviceId,
		String module) {
		validateModuleState(state, deviceId, module);
		int count = state.moduleInstanceCount.get(deviceId).get(module);
		if (count < 0) {
			throw new IllegalStateException("Negative instance count for module " + module
				+ " on device " + deviceId);
		}
		return count;
	}

	private void validateModuleState(PlacementState state, int deviceId, String module) {
		validateDeviceState(state, deviceId);
		int occurrences = 0;
		for (String placedModule : state.moduleMap.get(deviceId)) {
			if (module.equals(placedModule)) {
				occurrences++;
			}
		}
		if (occurrences != 1
			|| !state.moduleLoadMap.get(deviceId).containsKey(module)
			|| state.moduleLoadMap.get(deviceId).get(module) == null
			|| !state.moduleInstanceCount.get(deviceId).containsKey(module)
			|| state.moduleInstanceCount.get(deviceId).get(module) == null) {
			throw new IllegalStateException("Inconsistent placement state for module "
				+ module + " on device " + deviceId);
		}
	}

	private void removeModule(PlacementState state, int deviceId, String module,
		double moduleLoad) {
		double remainingLoad = state.cpuLoad.get(deviceId) - moduleLoad;
		if (remainingLoad < -0.0000001) {
			throw new IllegalStateException("Module loads exceed current CPU load on device "
				+ deviceId);
		}
		state.cpuLoad.put(deviceId, Math.max(0.0, remainingLoad));
		state.moduleMap.get(deviceId).remove(module);
		state.moduleLoadMap.get(deviceId).remove(module);
		state.moduleInstanceCount.get(deviceId).remove(module);
	}

	private void addModules(PlacementState state, int deviceId,
		Map<String, Double> movingLoads, Map<String, Integer> movingInstances,
		double totalCpuLoad) {
		for (String module : movingLoads.keySet()) {
			boolean modulePresent = state.moduleMap.get(deviceId).contains(module);
			boolean loadPresent = state.moduleLoadMap.get(deviceId).containsKey(module);
			boolean countPresent = state.moduleInstanceCount.get(deviceId).containsKey(module);
			if (modulePresent != loadPresent || modulePresent != countPresent) {
				throw new IllegalStateException("Inconsistent destination state for module "
					+ module + " on device " + deviceId);
			}

			double finalLoad = movingLoads.get(module);
			int finalInstances = movingInstances.get(module);
			if (modulePresent) {
				finalLoad = addFinite(requireModuleLoad(state, deviceId, module), finalLoad,
					"CPU load for module " + module);
				finalInstances = addInstanceCounts(
					requireModuleInstanceCount(state, deviceId, module), finalInstances,
					module);
			} else {
				state.moduleMap.get(deviceId).add(module);
			}
			state.moduleLoadMap.get(deviceId).put(module, finalLoad);
			state.moduleInstanceCount.get(deviceId).put(module, finalInstances);
		}
		state.cpuLoad.put(deviceId, addFinite(state.cpuLoad.get(deviceId), totalCpuLoad,
			"CPU load for device " + deviceId));
	}

	private int incrementInstanceCount(int count, String module) {
		return addInstanceCounts(count, 1, module);
	}

	private int addInstanceCounts(int first, int second, String module) {
		long result = (long) first + second;
		if (result > Integer.MAX_VALUE) {
			throw new IllegalStateException(
				"Instance count overflow for module " + module);
		}
		return (int) result;
	}

	private double addFinite(double first, double second, String description) {
		double result = first + second;
		if (Double.isNaN(result) || Double.isInfinite(result)) {
			throw new IllegalStateException(description + " is not finite");
		}
		return result;
	}

	private void validateNonNegativeFinite(double value, String description) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0) {
			throw new IllegalArgumentException(description
				+ " must be a non-negative finite value");
		}
	}

	private void validateApplicationModule(String module) {
		if (getApplication() == null || getApplication().getModuleByName(module) == null) {
			throw new IllegalStateException(
				"Application does not define placed module " + module);
		}
	}

	private FogDevice requireFogDevice(int deviceId) {
		FogDevice device = getDeviceById(deviceId);
		if (device == null) {
			throw new IllegalStateException(
				"Placement references unavailable fog device " + deviceId);
		}
		if (device.getHost() == null) {
			throw new IllegalStateException(
				"Fog device " + device.getName() + " has no host");
		}
		return device;
	}

	private int getValidatedParentId(int deviceId) {
		return requireFogDevice(deviceId).getParentId();
	}

	private List<String> findModulesToShift(String module, Integer deviceId,
		Map<Integer, List<String>> moduleMap) {
		List<String> upstreamModules = new ArrayList<String>();
		upstreamModules.add(module);
		return findModulesToShift(upstreamModules, deviceId, moduleMap);
	}

	private List<String> findModulesToShift(List<String> modules, Integer deviceId,
		Map<Integer, List<String>> moduleMap) {
		if (!moduleMap.containsKey(deviceId) || moduleMap.get(deviceId) == null) {
			throw new IllegalStateException(
				"Module list is missing for device " + deviceId);
		}
		List<String> upstreamModules = new ArrayList<String>();
		upstreamModules.addAll(modules);
		boolean changed = true;
		while (changed) {
			changed = false;
			for (AppEdge edge : getApplication().getEdges()) {
				if (upstreamModules.contains(edge.getSource()) && edge.getDirection() == Tuple.UP
					&& moduleMap.get(deviceId).contains(edge.getDestination())
					&& !upstreamModules.contains(edge.getDestination())) {
					upstreamModules.add(edge.getDestination());
					changed = true;
				}
			}
		}
		return upstreamModules;

	}

	private static final class PlacementState {
		private final Map<Integer, Double> cpuLoad;
		private final Map<Integer, List<String>> moduleMap;
		private final Map<Integer, Map<String, Double>> moduleLoadMap;
		private final Map<Integer, Map<String, Integer>> moduleInstanceCount;

		private PlacementState(Map<Integer, Double> cpuLoad,
			Map<Integer, List<String>> moduleMap,
			Map<Integer, Map<String, Double>> moduleLoadMap,
			Map<Integer, Map<String, Integer>> moduleInstanceCount) {
			this.cpuLoad = cpuLoad;
			this.moduleMap = moduleMap;
			this.moduleLoadMap = moduleLoadMap;
			this.moduleInstanceCount = moduleInstanceCount;
		}
	}

	private static final class PlacementPlan {
		private final Map<Integer, Double> cpuLoad;
		private final Map<Integer, List<String>> moduleMap;
		private final Map<Integer, Map<String, Double>> moduleLoadMap;
		private final Map<Integer, Map<String, Integer>> moduleInstanceCount;
		private final int destinationDeviceId;
		private final List<String> placedModules;

		private PlacementPlan(PlacementState state, int destinationDeviceId,
			List<String> placedModules) {
			this.cpuLoad = Collections.unmodifiableMap(
				new HashMap<Integer, Double>(state.cpuLoad));
			this.moduleMap = immutableLists(state.moduleMap);
			this.moduleLoadMap = immutableNestedMaps(state.moduleLoadMap);
			this.moduleInstanceCount = immutableNestedMaps(state.moduleInstanceCount);
			this.destinationDeviceId = destinationDeviceId;
			this.placedModules = Collections.unmodifiableList(
				new ArrayList<String>(placedModules));
		}

		private PlacementState toMutableState() {
			Map<Integer, List<String>> mutableModuleMap =
				new HashMap<Integer, List<String>>();
			for (Map.Entry<Integer, List<String>> entry : moduleMap.entrySet()) {
				mutableModuleMap.put(entry.getKey(),
					new ArrayList<String>(entry.getValue()));
			}

			Map<Integer, Map<String, Double>> mutableLoadMap =
				copyNestedMaps(moduleLoadMap);
			Map<Integer, Map<String, Integer>> mutableInstanceCount =
				copyNestedMaps(moduleInstanceCount);
			return new PlacementState(new HashMap<Integer, Double>(cpuLoad),
				mutableModuleMap, mutableLoadMap, mutableInstanceCount);
		}

		private static Map<Integer, List<String>> immutableLists(
			Map<Integer, List<String>> source) {
			Map<Integer, List<String>> copy = new HashMap<Integer, List<String>>();
			for (Map.Entry<Integer, List<String>> entry : source.entrySet()) {
				copy.put(entry.getKey(), Collections.unmodifiableList(
					new ArrayList<String>(entry.getValue())));
			}
			return Collections.unmodifiableMap(copy);
		}

		private static <T> Map<Integer, Map<String, T>> immutableNestedMaps(
			Map<Integer, Map<String, T>> source) {
			Map<Integer, Map<String, T>> copy =
				new HashMap<Integer, Map<String, T>>();
			for (Map.Entry<Integer, Map<String, T>> entry : source.entrySet()) {
				copy.put(entry.getKey(), Collections.unmodifiableMap(
					new HashMap<String, T>(entry.getValue())));
			}
			return Collections.unmodifiableMap(copy);
		}

		private static <T> Map<Integer, Map<String, T>> copyNestedMaps(
			Map<Integer, Map<String, T>> source) {
			Map<Integer, Map<String, T>> copy =
				new HashMap<Integer, Map<String, T>>();
			for (Map.Entry<Integer, Map<String, T>> entry : source.entrySet()) {
				copy.put(entry.getKey(), new HashMap<String, T>(entry.getValue()));
			}
			return copy;
		}

		private int getDestinationDeviceId() {
			return destinationDeviceId;
		}

		private List<String> getPlacedModules() {
			return new ArrayList<String>(placedModules);
		}
	}

	private int isPlacedUpstream(String operatorName, List<Integer> path) {
		events.trace("ModulePlacementEdgewards", () -> "Placement path " + path);
		for (int deviceId : path) {
			if (currentModuleMap.containsKey(deviceId)
				&& currentModuleMap.get(deviceId).contains(operatorName))
				return deviceId;
		}
		return -1;
	}

	private Map<String, Integer> getAssociatedSensors(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for (Sensor sensor : getSensors()) {
			if (sensor.getGatewayDeviceId() == device.getId()) {
				if (!endpoints.containsKey(sensor.getTupleType()))
					endpoints.put(sensor.getTupleType(), 0);
				endpoints.put(sensor.getTupleType(), endpoints.get(sensor.getTupleType()) + 1);
			}
		}
		return endpoints;
	}

	private Map<String, Integer> getAssociatedActuators(FogDevice device) {
		Map<String, Integer> endpoints = new HashMap<String, Integer>();
		for (Actuator actuator : getActuators()) {
			if (actuator.getGatewayDeviceId() == device.getId()) {
				if (!endpoints.containsKey(actuator.getActuatorType()))
					endpoints.put(actuator.getActuatorType(), 0);
				endpoints.put(actuator.getActuatorType(),
					endpoints.get(actuator.getActuatorType()) + 1);
			}
		}
		return endpoints;
	}

	@SuppressWarnings("serial")
	protected List<List<Integer>> getPaths(final int fogDeviceId) {
		FogDevice device = (FogDevice) CloudSim.getEntity(fogDeviceId);
		if (device.getChildrenIds().size() == 0) {
			final List<Integer> path = (new ArrayList<Integer>() {
				{
					add(fogDeviceId);
				}
			});
			List<List<Integer>> paths = (new ArrayList<List<Integer>>() {
				{
					add(path);
				}
			});
			return paths;
		}
		List<List<Integer>> paths = new ArrayList<List<Integer>>();
		for (int childId : device.getChildrenIds()) {
			List<List<Integer>> childPaths = getPaths(childId);
			for (List<Integer> childPath : childPaths)
				childPath.add(fogDeviceId);
			paths.addAll(childPaths);
		}
		return paths;
	}

	protected List<List<Integer>> getLeafToRootPaths() {
		FogDevice cloud = null;
		for (FogDevice device : getFogDevices()) {
			if (device.getName().equals("cloud"))
				cloud = device;
		}
		return getPaths(cloud.getId());
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, List<String>> getCurrentModuleMap() {
		return currentModuleMap;
	}

	public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
		this.currentModuleMap = currentModuleMap;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		this.sensors = sensors;
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

	public Map<Integer, Double> getCurrentCpuLoad() {
		return currentCpuLoad;
	}

	public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
		this.currentCpuLoad = currentCpuLoad;
	}

	public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
		return currentModuleLoadMap;
	}

	public void setCurrentModuleLoadMap(
		Map<Integer, Map<String, Double>> currentModuleLoadMap) {
		this.currentModuleLoadMap = currentModuleLoadMap;
	}

	public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
		return currentModuleInstanceNum;
	}

	public void setCurrentModuleInstanceNum(
		Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
		this.currentModuleInstanceNum = currentModuleInstanceNum;
	}

}
