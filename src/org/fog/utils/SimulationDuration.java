package org.fog.utils;

/** Immutable duration expressed in the simulator's millisecond time unit. */
public final class SimulationDuration {
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	public static final SimulationDuration ZERO = new SimulationDuration(0.0);

	private final double milliseconds;

	private SimulationDuration(double milliseconds) {
		if (!Double.isFinite(milliseconds) || milliseconds < 0.0) {
			throw new IllegalArgumentException(
				"Simulation duration must be finite and non-negative");
		}
		this.milliseconds = milliseconds;
	}

	public static SimulationDuration ofMilliseconds(double milliseconds) {
		return milliseconds == 0.0 ? ZERO : new SimulationDuration(milliseconds);
	}

	public static SimulationDuration ofSeconds(double seconds) {
		if (!Double.isFinite(seconds) || seconds < 0.0) {
			throw new IllegalArgumentException(
				"Simulation duration must be finite and non-negative");
		}
		return ofMilliseconds(seconds * MILLISECONDS_PER_SECOND);
	}

	public double toMilliseconds() {
		return milliseconds;
	}

	public double toSeconds() {
		return milliseconds / MILLISECONDS_PER_SECOND;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SimulationDuration)) {
			return false;
		}
		SimulationDuration duration = (SimulationDuration) other;
		return Double.doubleToLongBits(milliseconds)
			== Double.doubleToLongBits(duration.milliseconds);
	}

	@Override
	public int hashCode() {
		long bits = Double.doubleToLongBits(milliseconds);
		return (int) (bits ^ (bits >>> 32));
	}

	@Override
	public String toString() {
		return milliseconds + " ms";
	}
}
