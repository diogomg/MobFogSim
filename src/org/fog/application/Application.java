package org.fog.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Tuple;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoCoverage;

/**
 * Class represents an application in the Distributed Dataflow Model.
 * 
 * @author Harshit Gupta
 */
public class Application {

	private String appId;
	private int userId;
	private GeoCoverage geoCoverage;
	private String placementStrategy; // add by myiFogSim -> like Position3.java

	/**
	 * List of application modules in the application
	 */
	private List<AppModule> modules;

	/**
	 * List of application edges in the application
	 */
	private List<AppEdge> edges;

	/**
	 * List of application loops to monitor for delay
	 */
	private List<AppLoop> loops;

	private Map<String, AppEdge> edgeMap;

	/**
	 * Creates a plain vanilla application with no modules and edges.
	 * 
	 * @param appId
	 * @param userId
	 * @return
	 */
	public static Application createApplication(String appId, int userId) {
		return new Application(appId, userId);
	}

	/**
	 * Adds an application module to the application.
	 * 
	 * @param moduleName
	 * @param ram
	 */
	public void addAppModule(String moduleName, int ram) {
		int mips = 1000;
		long size = 10000;
		long bw = 1000;
		String vmm = "Xen";

		AppModule module = new AppModule(FogUtils.generateEntityId(), moduleName, appId, userId,
			mips, ram, bw, size, vmm, new TupleScheduler(mips, 1),
			new HashMap<Pair<String, String>, SelectivityModel>());

		modules.add(module);

	}

	/**
	 * Adds an application module to the application.
	 * 
	 * @param moduleName
	 * @param ram
	 * @param vmm
	 */
	public void addAppModule(String moduleName, String vmm, int ram) {
		int mips = 1000;
		long size = 10000;
		long bw = 1000;
		// String vmm = "Xen";

		AppModule module = new AppModule(FogUtils.generateEntityId(), moduleName, appId, userId,
			mips, ram, bw, size, vmm, new TupleScheduler(mips, 1),
			new HashMap<Pair<String, String>, SelectivityModel>());

		modules.add(module);

	}

	/**
	 * Adds an application module to the application.
	 * 
	 * @param moduleName
	 */
	public void addAppModule(AppModule userVm) {

		if (userVm == null) {
			throw new IllegalArgumentException("Application module cannot be null");
		}
		modules.add(userVm);
	}

	/**
	 * Adds a non-periodic edge to the application model.
	 * 
	 * @param source
	 * @param destination
	 * @param tupleCpuLength
	 * @param tupleNwLength
	 * @param tupleType
	 * @param direction
	 * @param edgeType
	 */
	public void addAppEdge(String source, String destination, double tupleCpuLength,
		double tupleNwLength, String tupleType, int direction, int edgeType) {
		AppEdge edge = new AppEdge(source, destination, tupleCpuLength, tupleNwLength,
			tupleType, direction, edgeType);
		registerEdge(edge);
	}

	/**
	 * Adds a periodic edge to the application model.
	 * 
	 * @param source
	 * @param destination
	 * @param tupleCpuLength
	 * @param tupleNwLength
	 * @param tupleType
	 * @param direction
	 * @param edgeType
	 */
	public void addAppEdge(String source, String destination, double periodicity,
		double tupleCpuLength,
		double tupleNwLength, String tupleType, int direction, int edgeType) {
		AppEdge edge = new AppEdge(source, destination, periodicity, tupleCpuLength,
			tupleNwLength, tupleType, direction, edgeType);
		registerEdge(edge);
	}

	/**
	 * Define the input-output relationship of an application module for a given
	 * input tuple type.
	 * 
	 * @param moduleName
	 *        Name of the module
	 * @param inputTupleType
	 *        Type of tuples carried by the incoming edge
	 * @param outputTupleType
	 *        Type of tuples carried by the output edge
	 * @param selectivityModel
	 *        Selectivity model governing the relation between the incoming and
	 *        outgoing edge
	 */
	public void addTupleMapping(String moduleName, String inputTupleType, String outputTupleType,
		SelectivityModel selectivityModel) {
		AppModule module = getModuleByName(moduleName);
		if (module == null) {
			throw new IllegalArgumentException(
				"Unknown application module: " + moduleName);
		}
		if (inputTupleType == null || inputTupleType.trim().isEmpty()
			|| outputTupleType == null || outputTupleType.trim().isEmpty()) {
			throw new IllegalArgumentException("Tuple mapping types cannot be blank");
		}
		if (selectivityModel == null) {
			throw new IllegalArgumentException("Selectivity model cannot be null");
		}
		boolean matchingOutputEdge = false;
		for (AppEdge edge : getEdges()) {
			if (edge.getSource().equals(moduleName)
				&& edge.getTupleType().equals(outputTupleType)) {
				matchingOutputEdge = true;
				break;
			}
		}
		if (!matchingOutputEdge) {
			throw new IllegalArgumentException("No output edge from " + moduleName
				+ " produces tuple type " + outputTupleType);
		}
		module.addSelectivity(inputTupleType, outputTupleType, selectivityModel);
	}

	/**
	 * Get a list of all periodic edges in the application.
	 * 
	 * @param srcModule
	 * @return
	 */
	public List<AppEdge> getPeriodicEdges(String srcModule) {
		List<AppEdge> result = new ArrayList<AppEdge>();
		for (AppEdge edge : edges) {
			if (edge.isPeriodic() && edge.getSource().equals(srcModule))
				result.add(edge);
		}
		return Collections.unmodifiableList(result);
	}

	public Application(String appId, int userId) {
		setAppId(appId);
		setUserId(userId);
		setModules(new ArrayList<AppModule>());
		setEdges(new ArrayList<AppEdge>());
		setGeoCoverage(null);
		setLoops(new ArrayList<AppLoop>());
		setEdgeMap(new HashMap<String, AppEdge>());
	}

	public Application(String appId, List<AppModule> modules,
		List<AppEdge> edges, List<AppLoop> loops, GeoCoverage geoCoverage) {
		setAppId(appId);
		setModules(modules);
		setEdges(edges);
		setGeoCoverage(geoCoverage);
		setLoops(loops);
		setEdgeMap(new HashMap<String, AppEdge>());
		for (AppEdge edge : edges) {
			registerEdgeInMap(edge);
		}
	}

	/**
	 * Search and return an application module by its module name
	 * 
	 * @param name
	 *        the module name to be returned
	 * @return
	 */
	public AppModule getModuleByName(String name) {
		for (AppModule module : modules) {
			if (module.getName().equals(name))
				return module;
		}
		return null;
	}

	/**
	 * Get the tuples generated upon execution of incoming tuple
	 * <i>inputTuple</i> by module named <i>moduleName</i>
	 * 
	 * @param moduleName
	 *        name of the module performing execution of incoming tuple and
	 *        emitting resultant tuples
	 * @param inputTuple
	 *        incoming tuple, whose execution creates resultant tuples
	 * @param sourceDeviceId
	 * @return
	 */
	public List<Tuple> getResultantTuples(String moduleName, Tuple inputTuple, int sourceDeviceId) {
		if (inputTuple == null) {
			throw new IllegalArgumentException("Input tuple cannot be null");
		}
		List<Tuple> tuples = new ArrayList<Tuple>();
		AppModule module = getModuleByName(moduleName);
		if (module == null) {
			throw new IllegalArgumentException(
				"Unknown application module: " + moduleName);
		}
		for (AppEdge edge : getEdges()) {
			if (edge.getSource().equals(moduleName)) {
				Pair<String, String> pair = new Pair<String, String>(inputTuple.getTupleType(),
					edge.getTupleType());

				if (module.getSelectivityMap().get(pair) == null)
					continue;
				SelectivityModel selectivityModel = module.getSelectivityMap().get(pair);
				if (selectivityModel.canSelect()) {
					if (edge.getEdgeType() == AppEdge.ACTUATOR) {
						for (Integer actuatorId : actuatorSubscriptions(module, edge)) {
							Tuple tuple = resultantTuple(edge, inputTuple);
							tuple.setDirection(Tuple.ACTUATOR);
							tuple.setSourceDeviceId(sourceDeviceId);
							tuple.setActuatorId(actuatorId);
							tuples.add(tuple);
						}
					}
					else {
						tuples.add(resultantTuple(edge, inputTuple));
					}
				}
			}
		}
		return tuples;
	}

	/**
	 * Create a tuple for a given application edge
	 * 
	 * @param edge
	 * @param sourceDeviceId
	 * @return
	 */
	public List<Tuple> createTuples(AppEdge edge, int sourceDeviceId) {
		if (edge == null) {
			throw new IllegalArgumentException("Application edge cannot be null");
		}
		List<Tuple> tuples = new ArrayList<Tuple>();
		AppModule module = getModuleByName(edge.getSource());
		if (module == null) {
			throw new IllegalArgumentException(
				"Unknown source module: " + edge.getSource());
		}
		if (edge.getEdgeType() == AppEdge.ACTUATOR) {
			for (Integer actuatorId : actuatorSubscriptions(module, edge)) {
				Tuple tuple = periodicTuple(edge);
				tuple.setDirection(Tuple.ACTUATOR);
				tuple.setSourceDeviceId(sourceDeviceId);
				tuple.setActuatorId(actuatorId);
				tuples.add(tuple);
			}
		}
		else {
			tuples.add(periodicTuple(edge));
		}
		return tuples;
	}

	private List<Integer> actuatorSubscriptions(AppModule module, AppEdge edge) {
		List<Integer> subscriptions = module.getActuatorSubscriptions()
			.get(edge.getTupleType());
		return subscriptions == null ? java.util.Collections.<Integer>emptyList()
			: new ArrayList<Integer>(subscriptions);
	}

	private Tuple resultantTuple(AppEdge edge, Tuple inputTuple) {
		Tuple tuple = new Tuple(appId, FogUtils.generateTupleId(), edge.getDirection(),
			(long) edge.getTupleCpuLength(), inputTuple.getNumberOfPes(),
			(long) edge.getTupleNwLength(), inputTuple.getCloudletOutputSize(),
			inputTuple.getUtilizationModelCpu(), inputTuple.getUtilizationModelRam(),
			inputTuple.getUtilizationModelBw());
		tuple.setActualTupleId(inputTuple.getActualTupleId());
		tuple.setUserId(inputTuple.getUserId());
		tuple.setAppId(inputTuple.getAppId());
		tuple.setDestModuleName(edge.getDestination());
		tuple.setSrcModuleName(edge.getSource());
		tuple.setDirection(edge.getDirection());
		tuple.setTupleType(edge.getTupleType());
		return tuple;
	}

	private Tuple periodicTuple(AppEdge edge) {
		Tuple tuple = new Tuple(appId, FogUtils.generateTupleId(), edge.getDirection(),
			(long) edge.getTupleCpuLength(), 1, (long) edge.getTupleNwLength(), 100,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
		tuple.setUserId(getUserId());
		tuple.setAppId(getAppId());
		tuple.setDestModuleName(edge.getDestination());
		tuple.setSrcModuleName(edge.getSource());
		tuple.setDirection(edge.getDirection());
		tuple.setTupleType(edge.getTupleType());
		return tuple;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public List<AppModule> getModules() {
		return Collections.unmodifiableList(modules);
	}

	public void setModules(List<AppModule> modules) {
		if (modules == null) {
			throw new IllegalArgumentException("Application modules cannot be null");
		}
		this.modules = new ArrayList<AppModule>(modules);
	}

	public List<AppEdge> getEdges() {
		return Collections.unmodifiableList(edges);
	}

	public void setEdges(List<AppEdge> edges) {
		if (edges == null) {
			throw new IllegalArgumentException("Application edges cannot be null");
		}
		this.edges = new ArrayList<AppEdge>(edges);
	}

	public GeoCoverage getGeoCoverage() {
		return geoCoverage;
	}

	public void setGeoCoverage(GeoCoverage geoCoverage) {
		this.geoCoverage = geoCoverage;
	}

	public List<AppLoop> getLoops() {
		return Collections.unmodifiableList(loops);
	}

	public void setLoops(List<AppLoop> loops) {
		if (loops == null) {
			throw new IllegalArgumentException("Application loops cannot be null");
		}
		this.loops = new ArrayList<AppLoop>(loops);
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public Map<String, AppEdge> getEdgeMap() {
		return Collections.unmodifiableMap(edgeMap);
	}

	public void setEdgeMap(Map<String, AppEdge> edgeMap) {
		if (edgeMap == null) {
			throw new IllegalArgumentException("Application edge map cannot be null");
		}
		this.edgeMap = new HashMap<String, AppEdge>(edgeMap);
	}

	private void registerEdge(AppEdge edge) {
		edges.add(edge);
		registerEdgeInMap(edge);
	}

	private void registerEdgeInMap(AppEdge edge) {
		if (edge == null || edge.getTupleType() == null
			|| edge.getTupleType().trim().isEmpty()) {
			throw new IllegalArgumentException("Application edge and tuple type are required");
		}
		edgeMap.put(edge.getTupleType(), edge);
	}

	public String getPlacementStrategy() {
		return placementStrategy;
	}

	public void setPlacementStrategy(String placementStrategy) {
		this.placementStrategy = placementStrategy;
	}

	@Override
	public String toString() {
		return "Application [appId=" + appId + ", userId=" + userId
			+ ", geoCoverage=" + geoCoverage + ", placementStrategy="
			+ placementStrategy + ", modules=" + modules + ", edges="
			+ edges + ", loops=" + loops + ", edgeMap=" + edgeMap + "]";
	}
}
