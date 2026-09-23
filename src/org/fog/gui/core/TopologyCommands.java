package org.fog.gui.core;

import java.util.List;

/** Non-Swing command boundary for editing a topology graph. */
public final class TopologyCommands {
	private final Graph graph;

	public TopologyCommands(Graph graph) {
		if (graph == null) {
			throw new IllegalArgumentException("Topology graph cannot be null");
		}
		this.graph = graph;
	}

	public GraphSnapshot snapshot() {
		return graph.snapshot();
	}

	public void addNode(Node node) {
		if (node == null) {
			throw new IllegalArgumentException("Topology node cannot be null");
		}
		String name = node.getName();
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Topology node name cannot be empty");
		}
		for (Node existing : snapshot().nodes()) {
			if (name.equals(existing.getName())) {
				throw new IllegalArgumentException(
					"Topology already contains node '" + name + "'");
			}
		}
		graph.addNode(node);
	}

	public void addEdge(Node source, Edge edge) {
		if (source == null || edge == null || edge.getNode() == null) {
			throw new IllegalArgumentException(
				"Topology edge source, value, and destination cannot be null");
		}
		GraphSnapshot current = snapshot();
		if (!current.nodes().contains(source)
			|| !current.nodes().contains(edge.getNode())) {
			throw new IllegalArgumentException(
				"Topology edge endpoints must already belong to the graph");
		}
		List<Edge> existingEdges = current.edgesFrom(source);
		for (Edge existing : existingEdges) {
			if (existing.getNode().equals(edge.getNode())) {
				throw new IllegalArgumentException(
					"Topology edge already connects the selected nodes");
			}
		}
		graph.addEdge(source, edge);
	}

	public void removeEdge(Node source, Edge edge) {
		graph.removeEdge(source, edge);
	}

	public void removeNode(Node node) {
		graph.removeNode(node);
	}

	/** Applies view-layout state without exposing node mutation to Swing code. */
	public void placeNode(Node node, Coordinates coordinates) {
		requireMember(node);
		if (coordinates == null) {
			throw new IllegalArgumentException("Node coordinates cannot be null");
		}
		node.setCoordinate(coordinates);
		node.setPlaced(true);
	}

	public void resetPlacement(Node node) {
		requireMember(node);
		node.setPlaced(false);
	}

	public void clear() {
		graph.clearGraph();
	}

	private void requireMember(Node node) {
		if (node == null || !snapshot().nodes().contains(node)) {
			throw new IllegalArgumentException(
				"Topology node must belong to the graph");
		}
	}
}
