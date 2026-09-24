package org.fog.gui.core;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.utils.distribution.Distribution;
import org.fog.utils.distribution.NormalDistribution;
import org.fog.utils.distribution.UniformDistribution;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/** Transactional JSON reader and writer for GUI topology graphs. */
public final class Bridge {

	private Bridge() {
	}

	public static Graph jsonToGraph(String fileName, int type) {
		return jsonToGraph(fileName, TopologyType.fromLegacy(type));
	}

	public static Graph jsonToGraph(String fileName, TopologyType type) {
		if (type == null) {
			throw new IllegalArgumentException("Topology type cannot be null");
		}
		Path path = requirePath(fileName);
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			Object parsed = JSONValue.parse(reader);
			if (!(parsed instanceof JSONObject)) {
				throw invalid(path, "document must be a JSON object", null);
			}
			return parseGraph((JSONObject) parsed, type, path);
		}
		catch (IOException error) {
			throw invalid(path, "could not be read", error);
		}
		catch (TopologyException error) {
			throw error;
		}
		catch (RuntimeException error) {
			throw invalid(path, "contains invalid topology data", error);
		}
	}

	private static Graph parseGraph(JSONObject document, TopologyType type,
		Path path) {
		JSONArray nodeValues = array(document, "nodes", path);
		JSONArray linkValues = array(document, "links", path);
		Graph graph = new Graph();
		Map<String, Node> nodesByName = new HashMap<String, Node>();

		for (Object value : nodeValues) {
			JSONObject object = object(value, "node", path);
			Node node = type == TopologyType.PHYSICAL
				? parsePhysicalNode(object, path)
				: parseVirtualNode(object, path);
			if (nodesByName.put(node.getName(), node) != null) {
				throw invalid(path, "contains duplicate node name '"
					+ node.getName() + "'", null);
			}
			graph.addNode(node);
		}

		for (Object value : linkValues) {
			JSONObject object = object(value, "link", path);
			String sourceName = string(object, "source", path);
			String destinationName = string(object, "destination", path);
			Node source = nodesByName.get(sourceName);
			Node destination = nodesByName.get(destinationName);
			if (source == null || destination == null) {
				throw invalid(path, "link '" + sourceName + "' -> '"
					+ destinationName + "' references an unknown node", null);
			}
			Edge edge = type == TopologyType.PHYSICAL
				? new Edge(destination, nonNegativeDouble(object, "latency", path))
				: parseVirtualEdge(object, destination, path);
			graph.addEdge(source, edge);
		}
		return graph;
	}

	private static Node parsePhysicalNode(JSONObject object, Path path) {
		NodeType type = nodeType(object, path);
		String name = string(object, "name", path);
		NodeId nodeId = nodeId(object, name, type, path);
		switch (type) {
		case HOST:
			int copies = object.get("nums") == null ? 1
				: positiveInt(object, "nums", path);
			if (copies != 1) {
				throw invalid(path, "node '" + name
					+ "' uses nums=" + copies
					+ "; each expanded host must have a unique name", null);
			}
			return new HostNode(nodeId, name, type,
				positiveLong(object, "pes", path),
				positiveLong(object, "mips", path),
				positiveInt(object, "ram", path),
				positiveLong(object, "storage", path),
				positiveLong(object, "bw", path));
		case FOG_DEVICE:
			return new FogDeviceGui(nodeId, name,
				positiveLong(object, "mips", path),
				positiveInt(object, "ram", path),
				positiveLong(object, "upBw", path),
				positiveLong(object, "downBw", path),
				integer(object, "level", path),
				nonNegativeDouble(object, "ratePerMips", path));
		case SENSOR:
			return new SensorGui(nodeId, name,
				string(object, "sensorType", path),
				parseDistribution(object, path));
		case ACTUATOR:
			return new ActuatorGui(nodeId, name,
				string(object, "actuatorType", path));
		case CORE_SWITCH:
		case EDGE_SWITCH:
			return new SwitchNode(nodeId, name, type,
				positiveLong(object, "iops", path),
				positiveInt(object, "upports", path),
				positiveInt(object, "downports", path),
				positiveLong(object, "bw", path));
		default:
			throw invalid(path, "node '" + name
				+ "' has non-physical type " + type, null);
		}
	}

	private static Node parseVirtualNode(JSONObject object, Path path) {
		NodeType type = nodeType(object, path);
		String name = string(object, "name", path);
		NodeId nodeId = nodeId(object, name, type, path);
		switch (type) {
		case VM:
			return new VmNode(nodeId, name, type,
				positiveLong(object, "size", path),
				positiveInt(object, "pes", path),
				positiveLong(object, "mips", path),
				positiveInt(object, "ram", path));
		case APP_MODULE:
			return new AppModule(nodeId, name);
		case SENSOR_MODULE:
			return new SensorModule(nodeId, name,
				stringOrDefault(object, "sensorType", name));
		case ACTUATOR_MODULE:
			return new ActuatorModule(nodeId, name,
				stringOrDefault(object, "actuatorType", name));
		default:
			throw invalid(path, "node '" + name
				+ "' has non-virtual type " + type, null);
		}
	}

	private static Edge parseVirtualEdge(JSONObject object, Node destination,
		Path path) {
		if (object.containsKey("tupleCpuLength")
			|| object.containsKey("tupleNetworkLength")) {
			return new Edge(destination, string(object, "name", path),
				stringOrDefault(object, "tupleType",
					string(object, "name", path)),
				nonNegativeDouble(object, "tupleCpuLength", path),
				nonNegativeDouble(object, "tupleNetworkLength", path));
		}
		String name = string(object, "name", path);
		long bandwidth = object.get("bandwidth") == null ? 0L
			: nonNegativeLong(object, "bandwidth", path);
		return new Edge(destination, name, bandwidth);
	}

	private static Distribution parseDistribution(JSONObject object, Path path) {
		int type = integer(object, "distribution", path);
		if (type == Distribution.DETERMINISTIC) {
			return new DeterministicDistribution(
				nonNegativeDouble(object, "value", path));
		}
		if (type == Distribution.NORMAL) {
			return new NormalDistribution(
				nonNegativeDouble(object, "mean", path),
				nonNegativeDouble(object, "stdDev", path));
		}
		if (type == Distribution.UNIFORM) {
			return new UniformDistribution(
				nonNegativeDouble(object, "min", path),
				nonNegativeDouble(object, "max", path));
		}
		throw invalid(path, "contains unknown sensor distribution " + type,
			null);
	}

	public static String graphToJson(Graph graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Graph cannot be null");
		}
		GraphSnapshot snapshot = graph.snapshot();
		TopologyType type = TopologyType.VIRTUAL;
		for (Node node : snapshot.nodes()) {
			if (node.getNodeType().isPhysicalNode()) {
				type = TopologyType.PHYSICAL;
				break;
			}
		}
		return graphToJson(snapshot, type);
	}

	public static String graphToJson(Graph graph, TopologyType type) {
		if (graph == null || type == null) {
			throw new IllegalArgumentException(
				"Graph and topology type cannot be null");
		}
		return graphToJson(graph.snapshot(), type);
	}

	private static String graphToJson(GraphSnapshot snapshot, TopologyType type) {
		Map<String, Object> topology = new LinkedHashMap<String, Object>();
		List<Object> nodes = new ArrayList<Object>();
		List<Object> links = new ArrayList<Object>();
		Map<UndirectedLink, Double> physicalLinks =
			new HashMap<UndirectedLink, Double>();
		Set<String> nodeNames = new HashSet<String>();
		Map<Node, List<Edge>> adjacency = snapshot.asMap();
		List<Node> orderedNodes = new ArrayList<Node>(snapshot.nodes());

		for (Node node : orderedNodes) {
			validateNodeForTopology(node, type);
			if (!nodeNames.add(node.getName())) {
				throw new IllegalArgumentException(
					"Graph contains duplicate node name '" + node.getName() + "'");
			}
		}
		Collections.sort(orderedNodes, NODE_ORDER);

		for (Node source : orderedNodes) {
			nodes.add(nodeToJson(source));
			List<Edge> orderedEdges = new ArrayList<Edge>(adjacency.get(source));
			Collections.sort(orderedEdges, EDGE_ORDER);
			for (Edge edge : orderedEdges) {
				Node destination = edge.getNode();
				validateNodeForTopology(destination, type);
				if (!adjacency.containsKey(destination)) {
					throw new IllegalArgumentException("Graph edge "
						+ source.getName() + " -> " + destination.getName()
						+ " references a node outside the graph");
				}
				if (type == TopologyType.PHYSICAL) {
					UndirectedLink link = new UndirectedLink(source.getName(),
						destination.getName());
					Double firstLatency = physicalLinks.get(link);
					if (firstLatency != null) {
						if (Double.compare(firstLatency.doubleValue(),
							edge.getLatency()) != 0) {
							throw new IllegalArgumentException("Physical link "
								+ source.getName() + " <-> "
								+ destination.getName()
								+ " has inconsistent directional latencies");
						}
						continue;
					}
					physicalLinks.put(link, edge.getLatency());
				}
				links.add(edgeToJson(source, edge, type));
			}
		}
		topology.put("nodes", nodes);
		topology.put("links", links);
		return JSONObject.toJSONString(topology);
	}

	private static Map<String, Object> nodeToJson(Node node) {
		Map<String, Object> object = new LinkedHashMap<String, Object>();
		object.put("id", node.getNodeId().toString());
		object.put("name", node.getName());
		object.put("type", node.getType());
		switch (node.getNodeType()) {
		case HOST:
			HostNode host = (HostNode) node;
			object.put("pes", host.getPes());
			object.put("mips", host.getMips());
			object.put("ram", host.getRam());
			object.put("storage", host.getStorage());
			object.put("bw", host.getBw());
			break;
		case CORE_SWITCH:
		case EDGE_SWITCH:
			SwitchNode networkSwitch = (SwitchNode) node;
			object.put("iops", networkSwitch.getIops());
			object.put("upports", networkSwitch.getUpports());
			object.put("downports", networkSwitch.getDownports());
			object.put("bw", networkSwitch.getBw());
			break;
		case VM:
			VmNode vm = (VmNode) node;
			object.put("size", vm.getSize());
			object.put("pes", vm.getPes());
			object.put("mips", vm.getMips());
			object.put("ram", vm.getRam());
			break;
		case FOG_DEVICE:
			FogDeviceGui fogDevice = (FogDeviceGui) node;
			object.put("mips", fogDevice.getMips());
			object.put("ram", fogDevice.getRam());
			object.put("upBw", fogDevice.getUpBw());
			object.put("downBw", fogDevice.getDownBw());
			object.put("level", fogDevice.getLevel());
			object.put("ratePerMips", fogDevice.getRatePerMips());
			break;
		case SENSOR:
			writeSensor(object, (SensorGui) node);
			break;
		case ACTUATOR:
			object.put("actuatorType", ((ActuatorGui) node).getActuatorType());
			break;
		case SENSOR_MODULE:
			object.put("sensorType", ((SensorModule) node).getSensorType());
			break;
		case ACTUATOR_MODULE:
			object.put("actuatorType",
				((ActuatorModule) node).getActuatorType());
			break;
		case APP_MODULE:
			break;
		default:
			throw new IllegalArgumentException(
				"Unsupported topology node type " + node.getNodeType());
		}
		return object;
	}

	private static void writeSensor(Map<String, Object> object,
		SensorGui sensor) {
		object.put("sensorType", sensor.getSensorType());
		object.put("distribution", sensor.getDistributionType());
		Distribution distribution = sensor.getDistribution();
		if (distribution instanceof DeterministicDistribution) {
			object.put("value",
				((DeterministicDistribution) distribution).getValue());
		}
		else if (distribution instanceof NormalDistribution) {
			object.put("mean", ((NormalDistribution) distribution).getMean());
			object.put("stdDev",
				((NormalDistribution) distribution).getStdDev());
		}
		else if (distribution instanceof UniformDistribution) {
			object.put("min", ((UniformDistribution) distribution).getMin());
			object.put("max", ((UniformDistribution) distribution).getMax());
		}
		else {
			throw new IllegalArgumentException("Unsupported sensor distribution "
				+ distribution);
		}
	}

	private static Map<String, Object> edgeToJson(Node source, Edge edge,
		TopologyType type) {
		Map<String, Object> object = new LinkedHashMap<String, Object>();
		object.put("source", source.getName());
		object.put("destination", edge.getNode().getName());
		if (type == TopologyType.PHYSICAL) {
			if (edge.getEdgeType() != Edge.EdgeType.PHYSICAL) {
				throw new IllegalArgumentException("Physical topology edge "
					+ source.getName() + " -> " + edge.getNode().getName()
					+ " does not carry latency");
			}
			object.put("latency", edge.getLatency());
		}
		else if (edge.getEdgeType() == Edge.EdgeType.APPLICATION) {
			object.put("name", edge.getName());
			object.put("tupleType", edge.getTupleType());
			object.put("tupleCpuLength", edge.getTupleCpuLength());
			object.put("tupleNetworkLength", edge.getTupleNetworkLength());
		}
		else {
			if (edge.getEdgeType() != Edge.EdgeType.VIRTUAL) {
				throw new IllegalArgumentException("Virtual topology edge "
					+ source.getName() + " -> " + edge.getNode().getName()
					+ " has no virtual-link data");
			}
			object.put("name", edge.getName());
			if (edge.getBandwidth() > 0L) {
				object.put("bandwidth", edge.getBandwidth());
			}
		}
		return object;
	}

	private static void validateNodeForTopology(Node node, TopologyType type) {
		if (node == null || node.getNodeType() == null) {
			throw new IllegalArgumentException("Topology node and type are required");
		}
		if (node.getName() == null || node.getName().trim().isEmpty()) {
			throw new IllegalArgumentException("Topology node name cannot be empty");
		}
		boolean physical = node.getNodeType().isPhysicalNode();
		if ((type == TopologyType.PHYSICAL) != physical) {
			throw new IllegalArgumentException("Node " + node.getName() + " of type "
				+ node.getNodeType() + " is not valid in a " + type + " topology");
		}
	}

	private static Path requirePath(String fileName) {
		if (fileName == null || fileName.trim().isEmpty()) {
			throw new IllegalArgumentException("Topology file name cannot be empty");
		}
		return Paths.get(fileName);
	}

	private static JSONArray array(JSONObject object, String key, Path path) {
		Object value = object.get(key);
		if (!(value instanceof JSONArray)) {
			throw invalid(path, "field '" + key + "' must be an array", null);
		}
		return (JSONArray) value;
	}

	private static JSONObject object(Object value, String label, Path path) {
		if (!(value instanceof JSONObject)) {
			throw invalid(path, label + " entry must be an object", null);
		}
		return (JSONObject) value;
	}

	private static NodeType nodeType(JSONObject object, Path path) {
		try {
			return NodeType.fromExternal(string(object, "type", path));
		}
		catch (IllegalArgumentException error) {
			throw invalid(path, error.getMessage(), error);
		}
	}

	private static NodeId nodeId(JSONObject object, String name, NodeType type,
		Path path) {
		Object value = object.get("id");
		if (value == null) {
			return NodeId.fromLegacyName(name, type);
		}
		if (!(value instanceof String)) {
			throw invalid(path, "field 'id' must be a UUID string", null);
		}
		try {
			return NodeId.parse((String) value);
		}
		catch (IllegalArgumentException error) {
			throw invalid(path, error.getMessage(), error);
		}
	}

	private static String string(JSONObject object, String key, Path path) {
		Object value = object.get(key);
		if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
			throw invalid(path, "field '" + key
				+ "' must be a non-empty string", null);
		}
		return (String) value;
	}

	private static String stringOrDefault(JSONObject object, String key,
		String defaultValue) {
		Object value = object.get(key);
		return value instanceof String && !((String) value).trim().isEmpty()
			? (String) value : defaultValue;
	}

	private static int integer(JSONObject object, String key, Path path) {
		try {
			return number(object, key, path).intValueExact();
		}
		catch (ArithmeticException error) {
			throw invalid(path, "field '" + key + "' must be an integer", error);
		}
	}

	private static int positiveInt(JSONObject object, String key, Path path) {
		int value = integer(object, key, path);
		if (value <= 0) {
			throw invalid(path, "field '" + key + "' must be positive", null);
		}
		return value;
	}

	private static long positiveLong(JSONObject object, String key, Path path) {
		long value = longValue(object, key, path);
		if (value <= 0L) {
			throw invalid(path, "field '" + key + "' must be positive", null);
		}
		return value;
	}

	private static long nonNegativeLong(JSONObject object, String key,
		Path path) {
		long value = longValue(object, key, path);
		if (value < 0L) {
			throw invalid(path, "field '" + key
				+ "' cannot be negative", null);
		}
		return value;
	}

	private static long longValue(JSONObject object, String key, Path path) {
		try {
			return number(object, key, path).longValueExact();
		}
		catch (ArithmeticException error) {
			throw invalid(path, "field '" + key + "' must be an integer", error);
		}
	}

	private static double nonNegativeDouble(JSONObject object, String key,
		Path path) {
		double value = number(object, key, path).doubleValue();
		if (!Double.isFinite(value) || value < 0.0) {
			throw invalid(path, "field '" + key
				+ "' must be finite and non-negative", null);
		}
		return value;
	}

	private static BigDecimal number(JSONObject object, String key, Path path) {
		Object value = object.get(key);
		if (!(value instanceof Number)) {
			throw invalid(path, "field '" + key + "' must be numeric", null);
		}
		return new BigDecimal(value.toString());
	}

	private static TopologyException invalid(Path path, String message,
		Throwable cause) {
		String contextualMessage = "Topology file " + path + " " + message;
		return cause == null ? new TopologyException(contextualMessage)
			: new TopologyException(contextualMessage, cause);
	}

	private static final Comparator<Node> NODE_ORDER = new Comparator<Node>() {
		@Override
		public int compare(Node first, Node second) {
			int nameComparison = first.getName().compareTo(second.getName());
			return nameComparison != 0 ? nameComparison
				: first.getNodeId().compareTo(second.getNodeId());
		}
	};

	private static final Comparator<Edge> EDGE_ORDER = new Comparator<Edge>() {
		@Override
		public int compare(Edge first, Edge second) {
			int comparison = first.getNode().getName().compareTo(
				second.getNode().getName());
			if (comparison != 0) {
				return comparison;
			}
			comparison = first.getEdgeType().compareTo(second.getEdgeType());
			if (comparison != 0) {
				return comparison;
			}
			comparison = first.getName().compareTo(second.getName());
			if (comparison != 0) {
				return comparison;
			}
			comparison = first.getTupleType().compareTo(second.getTupleType());
			if (comparison != 0) {
				return comparison;
			}
			comparison = Long.compare(first.getBandwidth(), second.getBandwidth());
			if (comparison != 0) {
				return comparison;
			}
			comparison = Double.compare(first.getLatency(), second.getLatency());
			if (comparison != 0) {
				return comparison;
			}
			comparison = Double.compare(first.getTupleCpuLength(),
				second.getTupleCpuLength());
			return comparison != 0 ? comparison
				: Double.compare(first.getTupleNetworkLength(),
					second.getTupleNetworkLength());
		}
	};

	private static final class UndirectedLink {
		private final String first;
		private final String second;

		private UndirectedLink(String left, String right) {
			if (left.compareTo(right) <= 0) {
				first = left;
				second = right;
			}
			else {
				first = right;
				second = left;
			}
		}

		@Override
		public boolean equals(Object object) {
			if (this == object) {
				return true;
			}
			if (!(object instanceof UndirectedLink)) {
				return false;
			}
			UndirectedLink other = (UndirectedLink) object;
			return first.equals(other.first) && second.equals(other.second);
		}

		@Override
		public int hashCode() {
			return 31 * first.hashCode() + second.hashCode();
		}
	}
}
