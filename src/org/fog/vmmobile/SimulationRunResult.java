package org.fog.vmmobile;

import java.util.Map;
import java.util.TreeMap;

import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.placement.SimulationMetricsSnapshot;

/** Immutable result and deterministic counters returned by an embedded run. */
public final class SimulationRunResult {
	private final SimulationMetricsSnapshot metrics;
	private final SimulationEventCounters eventCounters;
	private final int cloudSimEntityCount;
	private final int generatedFogEntityCount;
	private final int generatedTupleCount;
	private final int generatedActualTupleCount;
	private final SimulationTopologySize topologySize;

	SimulationRunResult(SimulationMetricsSnapshot metrics,
		SimulationEventCounters eventCounters, int cloudSimEntityCount,
		SimulationIdentifiers identifiers, SimulationTopologySize topologySize) {
		if (metrics == null || eventCounters == null || identifiers == null
			|| topologySize == null) {
			throw new IllegalArgumentException("A completed run requires metrics,"
				+ " event counters, identifiers, and topology size");
		}
		this.metrics = metrics;
		this.eventCounters = eventCounters;
		this.cloudSimEntityCount = cloudSimEntityCount;
		this.generatedFogEntityCount = identifiers.getGeneratedEntityCount();
		this.generatedTupleCount = identifiers.getGeneratedTupleCount();
		this.generatedActualTupleCount = identifiers.getGeneratedActualTupleCount();
		this.topologySize = topologySize;
	}

	public SimulationMetricsSnapshot getMetrics() {
		return metrics;
	}

	public SimulationEventCounters getEventCounters() {
		return eventCounters;
	}

	/** Returns the stable, wall-clock-independent characterisation of this run. */
	public SimulationSemanticSnapshot toSemanticSnapshot() {
		return SimulationSemanticSnapshot.capture(this);
	}

	/**
	 * Returns the canonical semantic snapshot plus deterministic dispatch
	 * counters used by golden tests and performance baselines.
	 */
	public String toCharacterisationText() {
		TreeMap<String, String> values = new TreeMap<String, String>(
			toSemanticSnapshot().getValues());
		values.put("events.dispatched.total",
			Long.toString(eventCounters.getTotalDispatched()));
		values.put("events.dispatched.queued",
			Long.toString(eventCounters.getQueuedDispatched()));
		values.put("events.dispatched.periodic",
			Long.toString(eventCounters.getPeriodicDispatched()));
		addCounterMap(values, "events.by_internal_type",
			eventCounters.getDispatchedByInternalType());
		addCounterMap(values, "events.by_tag",
			eventCounters.getDispatchedByTag());
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			text.append(entry.getKey()).append('=').append(entry.getValue())
				.append('\n');
		}
		return text.toString();
	}

	private static void addCounterMap(Map<String, String> values, String prefix,
		Map<Integer, Long> counters) {
		values.put(prefix + ".count", Integer.toString(counters.size()));
		int index = 0;
		for (Map.Entry<Integer, Long> entry : counters.entrySet()) {
			String item = prefix + "." + String.format(java.util.Locale.ROOT,
				"%04d", index++);
			values.put(item + ".key", Integer.toString(entry.getKey().intValue()));
			values.put(item + ".value", Long.toString(entry.getValue().longValue()));
		}
	}

	public int getCloudSimEntityCount() {
		return cloudSimEntityCount;
	}

	public int getGeneratedFogEntityCount() {
		return generatedFogEntityCount;
	}

	public int getGeneratedTupleCount() {
		return generatedTupleCount;
	}

	public int getGeneratedActualTupleCount() {
		return generatedActualTupleCount;
	}

	public SimulationTopologySize getTopologySize() {
		return topologySize;
	}
}
