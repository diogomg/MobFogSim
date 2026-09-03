package org.fog.gui.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
		assertFalse(link.containsKey("latency"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownLegacyTopologyKindIsRejected() {
		TopologyType.fromLegacy(2);
	}
}
