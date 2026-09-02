package org.fog.localization;

import java.util.List;

/** Time conversion and lookup operations for ordered mobility traces. */
public final class MobilityTimeline {
	public static final double SIMULATION_UNITS_PER_SECOND = 1000.0;
	private static final double TIME_EPSILON_SECONDS = 1.0e-9;

	private MobilityTimeline() {
	}

	public static double toSimulationTime(double traceTimeSeconds) {
		return traceTimeSeconds * SIMULATION_UNITS_PER_SECOND;
	}

	public static double toTraceTime(double simulationTime) {
		return simulationTime / SIMULATION_UNITS_PER_SECOND;
	}

	public static boolean isDue(MobilitySample sample, double traceTimeSeconds) {
		return sample.getTimeSeconds() <= traceTimeSeconds + TIME_EPSILON_SECONDS;
	}

	/**
	 * Returns the last sample at or before the target time. Targets outside the
	 * trace are clamped to its first or last sample. For duplicate timestamps,
	 * the last sample in input order wins.
	 */
	public static MobilitySample sampleAtOrBefore(List<MobilitySample> samples,
		double targetTimeSeconds) {
		if (samples == null || samples.isEmpty()) {
			throw new IllegalArgumentException("Mobility timeline cannot be empty");
		}
		if (!Double.isFinite(targetTimeSeconds)) {
			throw new IllegalArgumentException("Mobility target time must be finite");
		}

		int low = 0;
		int high = samples.size();
		while (low < high) {
			int middle = low + (high - low) / 2;
			if (samples.get(middle).getTimeSeconds() <= targetTimeSeconds) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return samples.get(low == 0 ? 0 : low - 1);
	}

	public static void validateOrdered(List<MobilitySample> samples) {
		if (samples == null) {
			throw new IllegalArgumentException("Mobility timeline cannot be null");
		}
		double previousTime = -1.0;
		for (int index = 0; index < samples.size(); index++) {
			MobilitySample sample = samples.get(index);
			if (sample == null) {
				throw new IllegalArgumentException(
					"Mobility timeline cannot contain null samples");
			}
			if (sample.getTimeSeconds() < previousTime) {
				throw new IllegalArgumentException(
					"Mobility sample times must be nondecreasing");
			}
			previousTime = sample.getTimeSeconds();
		}
	}
}
