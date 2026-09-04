package org.cloudbus.cloudsim.core;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable counts of events dispatched by one CloudSim run.
 *
 * <p>A dispatched event is an occurrence removed from either the ordinary
 * future-event queue or a compact periodic schedule and handed to CloudSim's
 * core event processor. Events left pending when a simulation terminates are
 * therefore not counted.</p>
 */
public final class SimulationEventCounters {

	private static final SimulationEventCounters EMPTY =
		new SimulationEventCounters(0L, 0L, 0L,
			Collections.<Integer, Long>emptyMap(),
			Collections.<Integer, Long>emptyMap());

	private final long totalDispatched;
	private final long queuedDispatched;
	private final long periodicDispatched;
	private final Map<Integer, Long> dispatchedByInternalType;
	private final Map<Integer, Long> dispatchedByTag;

	SimulationEventCounters(long totalDispatched, long queuedDispatched,
		long periodicDispatched, Map<Integer, Long> dispatchedByInternalType,
		Map<Integer, Long> dispatchedByTag) {
		if (totalDispatched < 0L || queuedDispatched < 0L
			|| periodicDispatched < 0L) {
			throw new IllegalArgumentException("Event counts cannot be negative");
		}
		if (queuedDispatched + periodicDispatched != totalDispatched) {
			throw new IllegalArgumentException(
				"Queued and periodic event counts must equal the total");
		}
		this.totalDispatched = totalDispatched;
		this.queuedDispatched = queuedDispatched;
		this.periodicDispatched = periodicDispatched;
		this.dispatchedByInternalType = immutableCounts(
			dispatchedByInternalType, "internal event type");
		this.dispatchedByTag = immutableCounts(dispatchedByTag, "event tag");
		if (sum(this.dispatchedByInternalType) != totalDispatched) {
			throw new IllegalArgumentException(
				"Internal-type event counts must equal the total");
		}
	}

	/** Returns an empty counter snapshot. */
	public static SimulationEventCounters empty() {
		return EMPTY;
	}

	public long getTotalDispatched() {
		return totalDispatched;
	}

	public long getQueuedDispatched() {
		return queuedDispatched;
	}

	public long getPeriodicDispatched() {
		return periodicDispatched;
	}

	public Map<Integer, Long> getDispatchedByInternalType() {
		return dispatchedByInternalType;
	}

	public long getDispatchedForInternalType(int internalType) {
		return count(dispatchedByInternalType, internalType);
	}

	public Map<Integer, Long> getDispatchedByTag() {
		return dispatchedByTag;
	}

	public long getDispatchedForTag(int tag) {
		return count(dispatchedByTag, tag);
	}

	private static Map<Integer, Long> immutableCounts(
		Map<Integer, Long> source, String description) {
		if (source == null) {
			throw new IllegalArgumentException(description + " counts cannot be null");
		}
		TreeMap<Integer, Long> copy = new TreeMap<Integer, Long>();
		for (Map.Entry<Integer, Long> entry : source.entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null
				|| entry.getValue().longValue() < 0L) {
				throw new IllegalArgumentException(
					description + " counts must have non-null keys and non-negative values");
			}
			copy.put(entry.getKey(), entry.getValue());
		}
		return Collections.unmodifiableMap(copy);
	}

	private static long count(Map<Integer, Long> counts, int key) {
		Long value = counts.get(Integer.valueOf(key));
		return value == null ? 0L : value.longValue();
	}

	private static long sum(Map<Integer, Long> counts) {
		long total = 0L;
		for (Long count : counts.values()) {
			total += count.longValue();
		}
		return total;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof SimulationEventCounters)) {
			return false;
		}
		SimulationEventCounters other = (SimulationEventCounters) object;
		return totalDispatched == other.totalDispatched
			&& queuedDispatched == other.queuedDispatched
			&& periodicDispatched == other.periodicDispatched
			&& dispatchedByInternalType.equals(other.dispatchedByInternalType)
			&& dispatchedByTag.equals(other.dispatchedByTag);
	}

	@Override
	public int hashCode() {
		int result = Long.valueOf(totalDispatched).hashCode();
		result = 31 * result + Long.valueOf(queuedDispatched).hashCode();
		result = 31 * result + Long.valueOf(periodicDispatched).hashCode();
		result = 31 * result + dispatchedByInternalType.hashCode();
		result = 31 * result + dispatchedByTag.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "SimulationEventCounters{total=" + totalDispatched
			+ ", queued=" + queuedDispatched + ", periodic="
			+ periodicDispatched + '}';
	}
}
