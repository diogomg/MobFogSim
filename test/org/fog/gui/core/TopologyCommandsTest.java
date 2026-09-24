package org.fog.gui.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TopologyCommandsTest {

	@Test
	public void commandsApplyGraphMutationsWithoutSwing() {
		Graph graph = new Graph();
		TopologyCommands commands = new TopologyCommands(graph);
		Node source = new Node("source", NodeType.FOG_DEVICE);
		Node destination = new Node("destination", NodeType.SENSOR);

		commands.addNode(source);
		commands.addNode(destination);
		commands.addEdge(source, new Edge(destination, 4.5));

		assertEquals(2, commands.snapshot().nodes().size());
		assertEquals(1, commands.snapshot().edgesFrom(source).size());
		assertEquals(destination,
			commands.snapshot().edgesFrom(source).get(0).getNode());
	}

	@Test
	public void commandsRejectDuplicateNamesAndUnregisteredEndpoints() {
		TopologyCommands commands = new TopologyCommands(new Graph());
		Node registered = new Node("node", NodeType.FOG_DEVICE);
		commands.addNode(registered);

		assertRejected(() -> commands.addNode(
			new Node("node", NodeType.SENSOR)));
		assertRejected(() -> commands.addEdge(registered,
			new Edge(new Node("missing", NodeType.ACTUATOR), 1.0)));
	}

	@Test
	public void renamePreservesStableIdentityEdgesAndLookups() {
		Graph graph = new Graph();
		TopologyCommands commands = new TopologyCommands(graph);
		Node source = new Node("source", NodeType.FOG_DEVICE);
		Node destination = new Node("destination", NodeType.SENSOR);
		commands.addNode(source);
		commands.addNode(destination);
		commands.addEdge(source, new Edge(destination, 4.5));
		commands.addEdge(destination, new Edge(source, 4.5));
		NodeId sourceId = source.getNodeId();
		int sourceHash = source.hashCode();

		commands.renameNode(source, "renamed-source");

		assertEquals("renamed-source", source.getName());
		assertEquals(sourceId, source.getNodeId());
		assertEquals(sourceHash, source.hashCode());
		assertTrue(commands.snapshot().contains(source));
		assertSame(destination,
			commands.snapshot().edgesFrom(source).get(0).getNode());
		assertSame(source,
			commands.snapshot().edgesFrom(destination).get(0).getNode());
	}

	@Test
	public void duplicateRenameIsRejectedWithoutChangingTheGraph() {
		Graph graph = new Graph();
		TopologyCommands commands = new TopologyCommands(graph);
		Node first = new Node("first", NodeType.FOG_DEVICE);
		Node second = new Node("second", NodeType.SENSOR);
		commands.addNode(first);
		commands.addNode(second);
		commands.addEdge(first, new Edge(second, 1.0));
		GraphSnapshot before = commands.snapshot();

		assertRejected(() -> commands.renameNode(first, "second"));

		assertEquals("first", first.getName());
		assertSame(before, commands.snapshot());
		assertEquals(1, commands.snapshot().edgesFrom(first).size());
	}

	@Test
	public void removingNodeCleansIncomingAndOutgoingEdges() {
		Graph graph = new Graph();
		TopologyCommands commands = new TopologyCommands(graph);
		Node first = new Node("first", NodeType.FOG_DEVICE);
		Node second = new Node("second", NodeType.FOG_DEVICE);
		commands.addNode(first);
		commands.addNode(second);
		commands.addEdge(first, new Edge(second, 1.0));
		commands.addEdge(second, new Edge(first, 1.0));

		commands.removeNode(first);

		assertFalse(commands.snapshot().contains(first));
		assertTrue(commands.snapshot().contains(second));
		assertTrue(commands.snapshot().edgesFrom(second).isEmpty());
	}

	@Test
	public void placementChangesAlsoUseTheCommandBoundary() {
		TopologyCommands commands = new TopologyCommands(new Graph());
		Node node = new Node("node", NodeType.FOG_DEVICE);
		commands.addNode(node);

		commands.placeNode(node, new Coordinates(10, 20));
		assertTrue(node.isPlaced());
		assertEquals(10, node.getCoordinate().getX());
		assertEquals(20, node.getCoordinate().getY());

		commands.resetPlacement(node);
		assertFalse(node.isPlaced());
	}

	private static void assertRejected(Runnable command) {
		try {
			command.run();
			fail("Expected topology command to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}
}
