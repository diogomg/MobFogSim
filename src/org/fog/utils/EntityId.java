package org.fog.utils;

/** Immutable identifier for an entity registered with CloudSim. */
public final class EntityId implements Comparable<EntityId> {
	private final int value;

	private EntityId(int value) {
		if (value < 0) {
			throw new IllegalArgumentException(
				"CloudSim entity ID must be non-negative");
		}
		this.value = value;
	}

	public static EntityId of(int value) {
		return new EntityId(value);
	}

	public int intValue() {
		return value;
	}

	@Override
	public int compareTo(EntityId other) {
		if (other == null) {
			throw new NullPointerException("Entity ID cannot be null");
		}
		return Integer.compare(value, other.value);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EntityId && value == ((EntityId) other).value;
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public String toString() {
		return Integer.toString(value);
	}
}
