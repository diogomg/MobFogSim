package org.fog.utils;

/** Immutable identity of one directed transport-network link. */
public final class TransportLinkId {
	private final int sourceEntityId;
	private final int destinationEntityId;

	private TransportLinkId(int sourceEntityId, int destinationEntityId) {
		if (sourceEntityId < 0 || destinationEntityId < 0) {
			throw new IllegalArgumentException("Transport-link entity IDs cannot be negative");
		}
		if (sourceEntityId == destinationEntityId) {
			throw new IllegalArgumentException("A transport link requires distinct endpoints");
		}
		this.sourceEntityId = sourceEntityId;
		this.destinationEntityId = destinationEntityId;
	}

	public static TransportLinkId directed(int sourceEntityId,
		int destinationEntityId) {
		return new TransportLinkId(sourceEntityId, destinationEntityId);
	}

	public int getSourceEntityId() {
		return sourceEntityId;
	}

	public int getDestinationEntityId() {
		return destinationEntityId;
	}

	@Override
	public boolean equals(Object candidate) {
		if (this == candidate) {
			return true;
		}
		if (!(candidate instanceof TransportLinkId)) {
			return false;
		}
		TransportLinkId other = (TransportLinkId) candidate;
		return sourceEntityId == other.sourceEntityId
			&& destinationEntityId == other.destinationEntityId;
	}

	@Override
	public int hashCode() {
		return 31 * sourceEntityId + destinationEntityId;
	}

	@Override
	public String toString() {
		return sourceEntityId + "->" + destinationEntityId;
	}
}
