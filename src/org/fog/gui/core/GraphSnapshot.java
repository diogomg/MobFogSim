package org.fog.gui.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable structural view of a graph at one point in time. */
public final class GraphSnapshot {
	private final Map<Node, List<Edge>> adjacency;

	GraphSnapshot(Map<Node, List<Edge>> source) {
		Map<Node, List<Edge>> copy = new LinkedHashMap<Node, List<Edge>>();
		for (Map.Entry<Node, List<Edge>> entry : source.entrySet()) {
			copy.put(entry.getKey(), Collections.unmodifiableList(
				new ArrayList<Edge>(entry.getValue())));
		}
		adjacency = Collections.unmodifiableMap(copy);
	}

	public Set<Node> nodes() {
		return adjacency.keySet();
	}

	public List<Edge> edgesFrom(Node node) {
		List<Edge> edges = adjacency.get(node);
		return edges == null ? Collections.<Edge>emptyList() : edges;
	}

	public boolean contains(Node node) {
		return adjacency.containsKey(node);
	}

	public int size() {
		return adjacency.size();
	}

	public Map<Node, List<Edge>> asMap() {
		return adjacency;
	}
}
