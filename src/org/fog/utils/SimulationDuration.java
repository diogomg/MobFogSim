package org.fog.utils;

/** Immutable duration expressed in the simulator's millisecond time unit. */
public final class SimulationDuration {

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

	public double toMilliseconds() {
		return milliseconds;
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
