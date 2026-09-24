package org.fog.gui.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * The model that represents node (host or vm) for the graph.
 */
public class Node implements Serializable {
	private static final long serialVersionUID = 823544330517091616L;

	private NodeId nodeId;
	private Coordinates coord;
	private String name;
	private NodeType type;
	private boolean isPlaced;

	public Node() {
		nodeId = NodeId.create();
		isPlaced = false;
		coord = new Coordinates();
	}

	public Node(String name, String type) {
		this(name, NodeType.fromExternal(type));
	}

	public Node(String name, NodeType type) {
		this(NodeId.create(), name, type);
	}

	protected Node(NodeId nodeId, String name, NodeType type) {
		if (nodeId == null) {
			throw new IllegalArgumentException("Node ID cannot be null");
		}
		this.nodeId = nodeId;
		this.name = name;
		this.type = requireNodeType(type);
		isPlaced = false;
		coord = new Coordinates();
	}

	void renameTo(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Node name cannot be empty");
		}
		this.name = name;
	}

	public NodeId getNodeId() {
		return nodeId;
	}

	public String getName() {
		return name;
	}

	public void setType(String type) {
		setNodeType(NodeType.fromExternal(type));
	}

	public String getType() {
		return type == null ? null : type.externalValue();
	}

	public void setNodeType(NodeType type) {
		this.type = requireNodeType(type);
	}

	private static NodeType requireNodeType(NodeType type) {
		if (type == null) {
			throw new IllegalArgumentException("Node type cannot be null");
		}
		return type;
	}

	public NodeType getNodeType() {
		return type;
	}

	public void setCoordinate(Coordinates coord) {
		this.coord.setX(coord.getX());
		this.coord.setY(coord.getY());
	}

	public Coordinates getCoordinate() {
		return coord;
	}

	@Override
	public int hashCode() {
		return nodeId.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Node))
			return false;
		Node other = (Node) obj;
		return nodeId.equals(other.nodeId);
	}

	@Override
	public String toString() {
		return "Node [name=" + name + " type=" + type + "]";
	}

	public boolean isPlaced() {
		return isPlaced;
	}

	public void setPlaced(boolean isPlaced) {
		this.isPlaced = isPlaced;
	}

	/** Restores a stable identity when reading pre-NodeId Java serialisations. */
	private void readObject(ObjectInputStream input)
		throws IOException, ClassNotFoundException {
		input.defaultReadObject();
		if (nodeId == null) {
			nodeId = NodeId.fromLegacyName(name, type);
		}
		if (coord == null) {
			coord = new Coordinates();
		}
	}

}
