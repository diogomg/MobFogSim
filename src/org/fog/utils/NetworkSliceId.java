package org.fog.utils;

/** Immutable, non-negative network-slice identifier. */
public final class NetworkSliceId implements Comparable<NetworkSliceId> {
	private final int value;

	private NetworkSliceId(int value) {
		if (value < 0) {
			throw new IllegalArgumentException(
				"Network slice ID must be non-negative");
		}
		this.value = value;
	}

	public static NetworkSliceId of(int value) {
		return new NetworkSliceId(value);
	}

	public int intValue() {
		return value;
	}

	@Override
	public int compareTo(NetworkSliceId other) {
		if (other == null) {
			throw new NullPointerException("Network slice ID cannot be null");
		}
		return Integer.compare(value, other.value);
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof NetworkSliceId
			&& value == ((NetworkSliceId) other).value;
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
