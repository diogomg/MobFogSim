package org.fog.vmmobile;

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

	SimulationRunResult(SimulationMetricsSnapshot metrics,
		SimulationEventCounters eventCounters, int cloudSimEntityCount,
		SimulationIdentifiers identifiers) {
		if (metrics == null || eventCounters == null || identifiers == null) {
			throw new IllegalArgumentException("A completed run requires metrics and identifiers");
		}
		this.metrics = metrics;
		this.eventCounters = eventCounters;
		this.cloudSimEntityCount = cloudSimEntityCount;
		this.generatedFogEntityCount = identifiers.getGeneratedEntityCount();
		this.generatedTupleCount = identifiers.getGeneratedTupleCount();
		this.generatedActualTupleCount = identifiers.getGeneratedActualTupleCount();
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
}
