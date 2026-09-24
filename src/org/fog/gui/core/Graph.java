package org.fog.gui.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A graph model. Normally a model should not have any logic, but in this case
 * we implement logic to manipulate the adjacencyList like reorganizing, adding
 * nodes, removing nodes, e.g
 */
public class Graph implements Serializable {
	private static final long serialVersionUID = 745864022429447529L;

	private HashMap<Node, List<Edge>> adjacencyList;
	private transient GraphSnapshot cachedSnapshot;

	public Graph() {
		// when creating a new graph ensure that a new adjacencyList is created
		adjacencyList = new LinkedHashMap<Node, List<Edge>>();
	}

	public Graph(Map<Node, List<Edge>> adjacencyList) {
		this.adjacencyList = copyAdjacencyList(adjacencyList);
	}

	public void setAdjacencyList(Map<Node, List<Edge>> adjacencyList) {
		this.adjacencyList = copyAdjacencyList(adjacencyList);
		invalidateSnapshot();
	}

	private static HashMap<Node, List<Edge>> copyAdjacencyList(
		Map<Node, List<Edge>> adjacencyList) {
		if (adjacencyList == null) {
			throw new IllegalArgumentException("Adjacency list cannot be null");
		}
		HashMap<Node, List<Edge>> copy =
			new LinkedHashMap<Node, List<Edge>>();
		Set<String> names = new HashSet<String>();
		for (Entry<Node, List<Edge>> entry : adjacencyList.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) {
				throw new IllegalArgumentException(
					"Graph nodes and edge lists cannot be null");
			}
			requireNodeName(entry.getKey().getName());
			if (!names.add(entry.getKey().getName())) {
				throw new IllegalArgumentException("Graph contains duplicate node name '"
					+ entry.getKey().getName() + "'");
			}
			copy.put(entry.getKey(), new ArrayList<Edge>(entry.getValue()));
		}
		for (Entry<Node, List<Edge>> entry : copy.entrySet()) {
			for (Edge edge : entry.getValue()) {
				if (edge == null || edge.getNode() == null
					|| !copy.containsKey(edge.getNode())) {
					throw new IllegalArgumentException(
						"Graph edges must reference registered nodes");
				}
			}
		}
		return copy;
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
		if (key == null || value == null || value.getNode() == null) {
			throw new IllegalArgumentException(
				"Graph edge source, value, and destination cannot be null");
		}
		if (!adjacencyList.containsKey(key)
			|| !adjacencyList.containsKey(value.getNode())) {
			throw new IllegalArgumentException(
				"Graph edge endpoints must already belong to the graph");
		}
		adjacencyList.get(key).add(value);
		invalidateSnapshot();
	}

	/** Simply adds a new node, without setting any edges */
	public void addNode(Node node) {
		if (node == null) {
			throw new IllegalArgumentException("Graph node cannot be null");
		}
		requireNodeName(node.getName());
		if (adjacencyList.containsKey(node)) {
			throw new IllegalArgumentException(
				"Graph already contains node ID " + node.getNodeId());
		}
		if (nodeWithName(node.getName(), null) != null) {
			throw new IllegalArgumentException(
				"Graph already contains node name '" + node.getName() + "'");
		}
		adjacencyList.put(node, new ArrayList<Edge>());
		invalidateSnapshot();
	}

	/** Renames a registered node without changing its identity or graph edges. */
	public void renameNode(Node node, String newName) {
		if (node == null || !adjacencyList.containsKey(node)) {
			throw new IllegalArgumentException(
				"Only a registered graph node can be renamed");
		}
		requireNodeName(newName);
		Node registered = registeredNode(node);
		Node duplicate = nodeWithName(newName, registered);
		if (duplicate != null) {
			throw new IllegalArgumentException(
				"Graph already contains node name '" + newName + "'");
		}
		if (!newName.equals(registered.getName())) {
			registered.renameTo(newName);
			invalidateSnapshot();
		}
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

	private Node registeredNode(Node node) {
		for (Node candidate : adjacencyList.keySet()) {
			if (candidate.equals(node)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Node is not registered in the graph");
	}

	private Node nodeWithName(String name, Node excluded) {
		for (Node candidate : adjacencyList.keySet()) {
			if (candidate != excluded && name.equals(candidate.getName())) {
				return candidate;
			}
		}
		return null;
	}

	private static void requireNodeName(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Graph node name cannot be empty");
		}
	}

	/** Normalises pre-A4 serialisations to deterministic insertion ordering. */
	private void readObject(ObjectInputStream input)
		throws IOException, ClassNotFoundException {
		input.defaultReadObject();
		adjacencyList = copyAdjacencyList(adjacencyList);
		cachedSnapshot = null;
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
