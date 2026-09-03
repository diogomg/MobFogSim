package org.fog.vmmobile;

import org.fog.placement.SimulationMetricsSnapshot;

/** Immutable result and deterministic counters returned by an embedded run. */
public final class SimulationRunResult {
	private final SimulationMetricsSnapshot metrics;
	private final int cloudSimEntityCount;
	private final int generatedFogEntityCount;
	private final int generatedTupleCount;
	private final int generatedActualTupleCount;

	SimulationRunResult(SimulationMetricsSnapshot metrics,
		int cloudSimEntityCount, SimulationIdentifiers identifiers) {
		if (metrics == null || identifiers == null) {
			throw new IllegalArgumentException("A completed run requires metrics and identifiers");
		}
		this.metrics = metrics;
		this.cloudSimEntityCount = cloudSimEntityCount;
		this.generatedFogEntityCount = identifiers.getGeneratedEntityCount();
		this.generatedTupleCount = identifiers.getGeneratedTupleCount();
		this.generatedActualTupleCount = identifiers.getGeneratedActualTupleCount();
	}

	public SimulationMetricsSnapshot getMetrics() {
		return metrics;
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
