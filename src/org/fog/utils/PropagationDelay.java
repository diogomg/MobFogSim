package org.fog.utils;

/** Immutable one-way propagation delay in simulator milliseconds. */
public final class PropagationDelay {

	public static final PropagationDelay ZERO = new PropagationDelay(0.0);

	private final double milliseconds;

	private PropagationDelay(double milliseconds) {
		if (!Double.isFinite(milliseconds) || milliseconds < 0.0) {
			throw new IllegalArgumentException(
				"Propagation delay must be finite and non-negative");
		}
		this.milliseconds = milliseconds;
	}

	public static PropagationDelay ofMilliseconds(double milliseconds) {
		return milliseconds == 0.0 ? ZERO : new PropagationDelay(milliseconds);
	}

	public double toMilliseconds() {
		return milliseconds;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PropagationDelay)) {
			return false;
		}
		PropagationDelay delay = (PropagationDelay) other;
		return Double.doubleToLongBits(milliseconds)
			== Double.doubleToLongBits(delay.milliseconds);
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
