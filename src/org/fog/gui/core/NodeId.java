package org.fog.gui.core;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Immutable identity of one GUI topology node. */
public final class NodeId implements Comparable<NodeId>, Serializable {
	private static final long serialVersionUID = 1L;
	private static final String LEGACY_NAMESPACE = "mobfogsim:gui-node:";

	private final UUID value;

	private NodeId(UUID value) {
		if (value == null) {
			throw new IllegalArgumentException("Node ID cannot be null");
		}
		this.value = value;
	}

	/** Creates a new identity for a node created interactively or in code. */
	public static NodeId create() {
		return new NodeId(UUID.randomUUID());
	}

	/** Parses the canonical UUID representation persisted in topology JSON. */
	public static NodeId parse(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Node ID cannot be empty");
		}
		try {
			return new NodeId(UUID.fromString(value));
		}
		catch (IllegalArgumentException error) {
			throw new IllegalArgumentException("Invalid node ID: " + value, error);
		}
	}

	/**
	 * Derives a repeatable identity for topology files written before IDs were
	 * persisted. The ID remains unchanged after the node is renamed.
	 */
	static NodeId fromLegacyName(String name, NodeType type) {
		String seed = LEGACY_NAMESPACE + String.valueOf(type) + ':' + name;
		return new NodeId(UUID.nameUUIDFromBytes(
			seed.getBytes(StandardCharsets.UTF_8)));
	}

	@Override
	public int compareTo(NodeId other) {
		if (other == null) {
			throw new NullPointerException("Node ID cannot be compared with null");
		}
		return value.compareTo(other.value);
	}

	@Override
	public boolean equals(Object other) {
		return this == other || other instanceof NodeId
			&& value.equals(((NodeId) other).value);
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return value.toString();
	}
}
