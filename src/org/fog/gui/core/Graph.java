package org.fog.gui.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A graph model. Normally a model should not have any logic, but in this case
 * we implement logic to manipulate the adjacencyList like reorganizing, adding
 * nodes, removing nodes, e.g
 */
public class Graph implements Serializable {
	private static final long serialVersionUID = 745864022429447529L;

	private Map<Node, List<Edge>> adjacencyList;
	private transient GraphSnapshot cachedSnapshot;

	public Graph() {
		// when creating a new graph ensure that a new adjacencyList is created
		adjacencyList = new HashMap<Node, List<Edge>>();
	}

	public Graph(Map<Node, List<Edge>> adjacencyList) {
		setAdjacencyList(adjacencyList);
	}

	public void setAdjacencyList(Map<Node, List<Edge>> adjacencyList) {
		if (adjacencyList == null) {
			throw new IllegalArgumentException("Adjacency list cannot be null");
		}
		Map<Node, List<Edge>> copy = new HashMap<Node, List<Edge>>();
		for (Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				throw new IllegalArgumentException(
					"Graph nodes and edge lists cannot be null");
			}
			copy.put(entry.getKey(), new ArrayList<Edge>(entry.getValue()));
		}
		this.adjacencyList = copy;
		invalidateSnapshot();
	}

	public Map<Node, List<Edge>> getAdjacencyList() {
		return snapshot().asMap();
	}

	/** Returns one cached immutable view until the graph is next mutated. */
	public GraphSnapshot snapshot() {
		if (cachedSnapshot == null) {
			cachedSnapshot = new GraphSnapshot(adjacencyList);
		}
		return cachedSnapshot;
	}

	private void invalidateSnapshot() {
		cachedSnapshot = null;
	}

	/**
	 * Adds a given edge to the adjacency list. If the base node is not yet part
	 * of the adjacency list a new entry is added
	 */
	public void addEdge(Node key, Edge value) {
		if (key == null) {
			throw new IllegalArgumentException("Graph node cannot be null");
		}
		if (value != null && value.getNode() == null) {
			throw new IllegalArgumentException("Edge destination cannot be null");
		}

		if (adjacencyList.containsKey(key)) {
			if (adjacencyList.get(key) == null) {
				adjacencyList.put(key, new ArrayList<Edge>());
			}
			// add edge if not null
			if (value != null) {
				adjacencyList.get(key).add(value);
			}
		} else {
			List<Edge> edges = new ArrayList<Edge>();
			// add edge if not null
			if (value != null) {
				edges.add(value);
			}

			adjacencyList.put(key, edges);
		}
		invalidateSnapshot();
	}

	/** Simply adds a new node, without setting any edges */
	public void addNode(Node node) {
		addEdge(node, null);
	}

	public void removeEdge(Node key, Edge value) {

		if (!adjacencyList.containsKey(key)) {
			throw new IllegalArgumentException(
				"The adjacency list does not contain a node for the given key: " + key);
		}
		List<Edge> edges = adjacencyList.get(key);

		if (!edges.contains(value)) {
			throw new IllegalArgumentException(
				"The list of edges does not contain the given edge to remove: " + value);
		}

		edges.remove(value);
		invalidateSnapshot();
		if (value.getEdgeType() != Edge.EdgeType.PHYSICAL) {
			return;
		}
		// Physical topology links are logically bidirectional.
		List<Edge> reverseEdges = adjacencyList.get(value.getNode());
		if (reverseEdges == null) {
			return;
		}
		List<Edge> toRemove = new ArrayList<Edge>();
		for (Edge edge : reverseEdges) {
			if (edge.getNode().equals(key)) {
				toRemove.add(edge);
			}
		}
		// normally only one element
		reverseEdges.removeAll(toRemove);
	}

	/** Deletes a node */
	public void removeNode(Node key) {

		if (!adjacencyList.containsKey(key)) {
			throw new IllegalArgumentException(
				"The adjacency list does not contain a node for the given key: " + key);
		}

		adjacencyList.remove(key);

		// clean up all edges
		for (Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {

			List<Edge> toRemove = new ArrayList<Edge>();

			for (Edge edge : entry.getValue()) {
				if (edge.getNode().equals(key)) {
					toRemove.add(edge);
				}
			}
			entry.getValue().removeAll(toRemove);
		}
		invalidateSnapshot();
	}

	public void clearGraph() {
		adjacencyList.clear();
		invalidateSnapshot();
	}

	public String toJsonString() {
		return Bridge.graphToJson(this);
	}

	public String toJsonString(TopologyType type) {
		return Bridge.graphToJson(this, type);
	}

	@Override
	public String toString() {
		return "Graph [adjacencyList=" + adjacencyList + "]";
	}

}
