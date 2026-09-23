package org.fog.gui.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
