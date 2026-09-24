package org.fog.gui.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.utils.distribution.NormalDistribution;
import org.fog.utils.distribution.UniformDistribution;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Test;

public class TopologyTypesTest {

	@Test
	public void legacySpellingIsParsedOnceIntoANodeType() {
		Node node = new Node("host", new String("HOST"));

		assertEquals(NodeType.HOST, node.getNodeType());
		assertEquals("host", node.getType());
		assertTrue(node.getNodeType().isPhysicalNode());
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownNodeTypeIsRejectedAtTheBoundary() {
		new Node("mystery", "not-a-node-type");
	}

	@Test
	public void virtualLinkSerialisationUsesTypedNodeCategory() {
		Graph graph = new Graph();
		VmNode source = new VmNode("source", NodeType.VM, 10, 1, 100, 128);
		VmNode destination = new VmNode("destination", NodeType.VM,
			10, 1, 100, 128);
		graph.addNode(source);
		graph.addNode(destination);
		graph.addEdge(source, new Edge(destination, "flow", 512));

		JSONObject json = (JSONObject) JSONValue.parse(Bridge.graphToJson(graph));
		JSONArray links = (JSONArray) json.get("links");
		JSONObject link = (JSONObject) links.get(0);
		assertEquals(Long.valueOf(512), link.get("bandwidth"));
		assertEquals("flow", link.get("name"));
		assertFalse(link.containsKey("latency"));
	}

	@Test
	public void physicalGraphRoundTripPreservesPortsLatencyAndMixedNumbers()
		throws Exception {
		String json = "{\"nodes\":["
			+ "{\"name\":\"host-a\",\"type\":\"host\",\"pes\":2.0,"
			+ "\"mips\":1000,\"ram\":2048.0,\"storage\":10000,\"bw\":5000},"
			+ "{\"name\":\"core-a\",\"type\":\"core\",\"iops\":9000,"
			+ "\"upports\":3,\"downports\":7,\"bw\":8000.0}],"
			+ "\"links\":[{\"source\":\"host-a\","
			+ "\"destination\":\"core-a\",\"latency\":4}]}";
		Path file = writeTemporaryTopology(json);
		try {
			Graph graph = Bridge.jsonToGraph(file.toString(),
				TopologyType.PHYSICAL);
			SwitchNode networkSwitch = (SwitchNode) node(graph, "core-a");
			assertEquals(3, networkSwitch.getUpports());
			assertEquals(7, networkSwitch.getDownports());
			assertEquals(4.0, edge(graph, "host-a", "core-a").getLatency(),
				0.000001);
			NodeId hostId = node(graph, "host-a").getNodeId();

			Graph reloaded = roundTrip(graph, TopologyType.PHYSICAL);
			SwitchNode reloadedSwitch = (SwitchNode) node(reloaded, "core-a");
			assertEquals(3, reloadedSwitch.getUpports());
			assertEquals(7, reloadedSwitch.getDownports());
			assertEquals(4.0, edge(reloaded, "host-a", "core-a").getLatency(),
				0.000001);
			assertEquals(hostId, node(reloaded, "host-a").getNodeId());
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	public void sensorDistributionsSurvivePhysicalRoundTrip() throws Exception {
		Graph graph = new Graph();
		FogDeviceGui fog = new FogDeviceGui("fog", 1000, 1024, 1000,
			1000, 1, 0.1);
		SensorGui deterministic = new SensorGui("det", "temperature",
			new DeterministicDistribution(5.5));
		SensorGui normal = new SensorGui("normal", "temperature",
			new NormalDistribution(10.5, 2.25));
		SensorGui uniform = new SensorGui("uniform", "temperature",
			new UniformDistribution(1.25, 9.75));
		graph.addNode(fog);
		graph.addNode(deterministic);
		graph.addNode(normal);
		graph.addNode(uniform);
		graph.addEdge(deterministic, new Edge(fog, 1.0));
		graph.addEdge(normal, new Edge(fog, 2.0));
		graph.addEdge(uniform, new Edge(fog, 3.0));

		Graph reloaded = roundTrip(graph, TopologyType.PHYSICAL);
		assertEquals(5.5, ((DeterministicDistribution) ((SensorGui)
			node(reloaded, "det")).getDistribution()).getValue(), 0.000001);
		NormalDistribution normalDistribution = (NormalDistribution)
			((SensorGui) node(reloaded, "normal")).getDistribution();
		assertEquals(10.5, normalDistribution.getMean(), 0.000001);
		assertEquals(2.25, normalDistribution.getStdDev(), 0.000001);
		UniformDistribution uniformDistribution = (UniformDistribution)
			((SensorGui) node(reloaded, "uniform")).getDistribution();
		assertEquals(1.25, uniformDistribution.getMin(), 0.000001);
		assertEquals(9.75, uniformDistribution.getMax(), 0.000001);
	}

	@Test
	public void graphJavaSerialisationPreservesTopologyState() throws Exception {
		Graph graph = new Graph();
		FogDeviceGui fog = new FogDeviceGui("fog", 1000, 1024, 1000,
			1000, 1, 0.1);
		SensorGui sensor = new SensorGui("sensor", "temperature",
			new DeterministicDistribution(5.5));
		fog.setCoordinate(new Coordinates(7, 9));
		sensor.setCoordinate(new Coordinates(3, 4));
		graph.addNode(fog);
		graph.addNode(sensor);
		graph.addEdge(sensor, new Edge(fog, 2.5));

		Graph restored = javaSerialisationRoundTrip(graph);
		SensorGui restoredSensor = (SensorGui) node(restored, "sensor");
		assertEquals(3, restoredSensor.getCoordinate().getX());
		assertEquals(4, restoredSensor.getCoordinate().getY());
		assertEquals(5.5, ((DeterministicDistribution)
			restoredSensor.getDistribution()).getValue(), 0.000001);
		assertEquals(2.5, edge(restored, "sensor", "fog").getLatency(),
			0.000001);
		assertEquals(sensor.getNodeId(), restoredSensor.getNodeId());
	}

	@Test
	public void legacyJsonWithoutIdsGetsRepeatableStableIdentity()
		throws Exception {
		String json = "{\"nodes\":[{\"name\":\"vm\",\"type\":\"vm\","
			+ "\"size\":10,\"pes\":1,\"mips\":100,\"ram\":128}],"
			+ "\"links\":[]}";
		Path file = writeTemporaryTopology(json);
		try {
			Graph first = Bridge.jsonToGraph(file.toString(),
				TopologyType.VIRTUAL);
			Graph second = Bridge.jsonToGraph(file.toString(),
				TopologyType.VIRTUAL);

			assertEquals(node(first, "vm").getNodeId(),
				node(second, "vm").getNodeId());
			assertTrue(Bridge.graphToJson(first, TopologyType.VIRTUAL)
				.contains("\"id\""));
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	public void jsonIsDeterministicAcrossInsertionOrders() {
		NodeId alphaId = NodeId.parse("00000000-0000-0000-0000-000000000001");
		NodeId betaId = NodeId.parse("00000000-0000-0000-0000-000000000002");
		NodeId gammaId = NodeId.parse("00000000-0000-0000-0000-000000000003");
		VmNode alpha = vm(alphaId, "alpha");
		VmNode beta = vm(betaId, "beta");
		VmNode gamma = vm(gammaId, "gamma");
		Graph first = new Graph();
		first.addNode(gamma);
		first.addNode(alpha);
		first.addNode(beta);
		first.addEdge(alpha, new Edge(gamma, "alpha-gamma", 300));
		first.addEdge(alpha, new Edge(beta, "alpha-beta", 200));
		first.addEdge(gamma, new Edge(beta, "gamma-beta", 100));

		VmNode secondAlpha = vm(alphaId, "alpha");
		VmNode secondBeta = vm(betaId, "beta");
		VmNode secondGamma = vm(gammaId, "gamma");
		Graph second = new Graph();
		second.addNode(secondBeta);
		second.addNode(secondAlpha);
		second.addNode(secondGamma);
		second.addEdge(secondGamma,
			new Edge(secondBeta, "gamma-beta", 100));
		second.addEdge(secondAlpha,
			new Edge(secondBeta, "alpha-beta", 200));
		second.addEdge(secondAlpha,
			new Edge(secondGamma, "alpha-gamma", 300));

		assertEquals(Bridge.graphToJson(first, TopologyType.VIRTUAL),
			Bridge.graphToJson(second, TopologyType.VIRTUAL));
	}

	@Test
	public void physicalWriterRejectsInconsistentDirectionalLatencies() {
		Graph graph = new Graph();
		FogDeviceGui first = new FogDeviceGui("first", 1000, 1024, 1000,
			1000, 1, 0.1);
		FogDeviceGui second = new FogDeviceGui("second", 1000, 1024, 1000,
			1000, 0, 0.1);
		graph.addNode(first);
		graph.addNode(second);
		graph.addEdge(first, new Edge(second, 1.0));
		graph.addEdge(second, new Edge(first, 2.0));

		try {
			Bridge.graphToJson(graph, TopologyType.PHYSICAL);
			fail("Expected inconsistent physical latencies to be rejected");
		}
		catch (IllegalArgumentException error) {
			assertTrue(error.getMessage().contains("inconsistent"));
			assertTrue(error.getMessage().contains("first"));
			assertTrue(error.getMessage().contains("second"));
		}
	}

	@Test
	public void virtualRoundTripPreservesBothDirectedLinks() throws Exception {
		Graph graph = new Graph();
		VmNode first = new VmNode("first", NodeType.VM, 10, 1, 100, 128);
		VmNode second = new VmNode("second", NodeType.VM, 20, 2, 200, 256);
		graph.addNode(first);
		graph.addNode(second);
		graph.addEdge(first, new Edge(second, "requests", 512));
		graph.addEdge(second, new Edge(first, "responses", 256));

		Graph reloaded = roundTrip(graph, TopologyType.VIRTUAL);
		Edge requests = edge(reloaded, "first", "second");
		Edge responses = edge(reloaded, "second", "first");
		assertEquals("requests", requests.getName());
		assertEquals(512L, requests.getBandwidth());
		assertEquals("responses", responses.getName());
		assertEquals(256L, responses.getBandwidth());
	}

	@Test
	public void removingOneVirtualDirectionPreservesTheReverseDirection() {
		Graph graph = new Graph();
		VmNode first = new VmNode("first", NodeType.VM, 10, 1, 100, 128);
		VmNode second = new VmNode("second", NodeType.VM, 20, 2, 200, 256);
		Edge forward = new Edge(second, "requests", 512);
		graph.addNode(first);
		graph.addNode(second);
		graph.addEdge(first, forward);
		graph.addEdge(second, new Edge(first, "responses", 256));

		graph.removeEdge(first, forward);

		assertTrue(graph.getAdjacencyList().get(first).isEmpty());
		assertEquals(1, graph.getAdjacencyList().get(second).size());
	}

	@Test
	public void applicationRoundTripPreservesTupleLengthsAndTypes()
		throws Exception {
		Graph graph = new Graph();
		AppModule source = new AppModule("filter");
		ActuatorModule destination = new ActuatorModule("display", "DISPLAY");
		graph.addNode(source);
		graph.addNode(destination);
		graph.addEdge(source, new Edge(destination, "filter-display",
			"DISPLAY_UPDATE", 1200.5, 640.25));

		Graph reloaded = roundTrip(graph, TopologyType.VIRTUAL);
		Edge edge = edge(reloaded, "filter", "display");
		assertEquals(Edge.EdgeType.APPLICATION, edge.getEdgeType());
		assertEquals("DISPLAY_UPDATE", edge.getTupleType());
		assertEquals(1200.5, edge.getTupleCpuLength(), 0.000001);
		assertEquals(640.25, edge.getTupleNetworkLength(), 0.000001);
	}

	@Test
	public void danglingLinkFailsWithFileAndEndpointContext() throws Exception {
		Path file = writeTemporaryTopology("{\"nodes\":[{\"name\":\"vm\","
			+ "\"type\":\"vm\",\"size\":1,\"pes\":1,\"mips\":1,"
			+ "\"ram\":1}],\"links\":[{\"name\":\"flow\","
			+ "\"source\":\"vm\",\"destination\":\"missing\"}]}" );
		try {
			Bridge.jsonToGraph(file.toString(), TopologyType.VIRTUAL);
			fail("Expected a contextual topology exception");
		}
		catch (TopologyException error) {
			assertTrue(error.getMessage().contains(file.toString()));
			assertTrue(error.getMessage().contains("missing"));
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	@Test
	public void missingFileIsNotConvertedIntoAnEmptyGraph() {
		String missing = "build/does-not-exist/topology.json";
		try {
			Bridge.jsonToGraph(missing, TopologyType.PHYSICAL);
			fail("Expected a contextual topology exception");
		}
		catch (TopologyException error) {
			assertTrue(error.getMessage().contains(missing));
			assertNotNull(error.getCause());
		}
	}

	@Test
	public void graphCollectionsCannotBeMutatedThroughReadViews() {
		Graph graph = new Graph();
		VmNode vm = new VmNode("vm", NodeType.VM, 10, 1, 100, 128);
		graph.addNode(vm);
		Map<Node, List<Edge>> adjacency = graph.getAdjacencyList();
		try {
			adjacency.clear();
			fail("Expected an immutable adjacency map");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}
		try {
			adjacency.get(vm).add(new Edge(vm, "self", 1));
			fail("Expected an immutable edge list");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	@Test
	public void graphSnapshotIsReusedUntilTheNextMutation() {
		Graph graph = new Graph();
		VmNode first = new VmNode("first", NodeType.VM, 10, 1, 100, 128);
		VmNode second = new VmNode("second", NodeType.VM, 10, 1, 100, 128);
		graph.addNode(first);

		GraphSnapshot before = graph.snapshot();
		assertSame(before, graph.snapshot());
		assertSame(before.asMap(), graph.getAdjacencyList());
		assertTrue(before.contains(first));
		assertFalse(before.contains(second));

		graph.addNode(second);
		GraphSnapshot after = graph.snapshot();
		assertNotSame(before, after);
		assertFalse(before.contains(second));
		assertTrue(after.contains(second));
		assertSame(after, graph.snapshot());
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownLegacyTopologyKindIsRejected() {
		TopologyType.fromLegacy(2);
	}

	private static Graph roundTrip(Graph graph, TopologyType type)
		throws Exception {
		Path file = writeTemporaryTopology(Bridge.graphToJson(graph, type));
		try {
			return Bridge.jsonToGraph(file.toString(), type);
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	private static Graph javaSerialisationRoundTrip(Graph graph)
		throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
			output.writeObject(graph);
		}
		try (ObjectInputStream input = new ObjectInputStream(
			new ByteArrayInputStream(bytes.toByteArray()))) {
			return (Graph) input.readObject();
		}
	}

	private static Path writeTemporaryTopology(String json) throws Exception {
		Path file = Files.createTempFile("mobfogsim-topology-", ".json");
		Files.write(file, json.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private static VmNode vm(NodeId nodeId, String name) {
		return new VmNode(nodeId, name, NodeType.VM, 10, 1, 100, 128);
	}

	private static Node node(Graph graph, String name) {
		for (Node node : graph.getAdjacencyList().keySet()) {
			if (name.equals(node.getName())) {
				return node;
			}
		}
		fail("Missing node " + name);
		return null;
	}

	private static Edge edge(Graph graph, String source, String destination) {
		Node sourceNode = node(graph, source);
		for (Edge edge : graph.getAdjacencyList().get(sourceNode)) {
			if (destination.equals(edge.getNode().getName())) {
				return edge;
			}
		}
		fail("Missing edge " + source + " -> " + destination);
		return null;
	}
}
