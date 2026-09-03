package org.fog.vmmobile;

/** Mutable identifier sequences owned by one simulation context. */
public final class SimulationIdentifiers {
	private int nextTupleId = 1;
	private int nextEntityId = 1;
	private int nextActualTupleId = 1;

	public synchronized int nextTupleId() {
		return nextTupleId++;
	}

	public synchronized int nextEntityId() {
		return nextEntityId++;
	}

	public synchronized int nextActualTupleId() {
		return nextActualTupleId++;
	}

	public synchronized int getGeneratedTupleCount() {
		return nextTupleId - 1;
	}

	public synchronized int getGeneratedEntityCount() {
		return nextEntityId - 1;
	}

	public synchronized int getGeneratedActualTupleCount() {
		return nextActualTupleId - 1;
	}
}
