package org.fog.utils;

/** Immutable data rate stored in bits per second. */
public final class DataRate {
	private static final double BITS_PER_MEBIBIT = 1024.0 * 1024.0;

	public static final DataRate ZERO = new DataRate(0.0);

	private final double bitsPerSecond;

	private DataRate(double bitsPerSecond) {
		if (!Double.isFinite(bitsPerSecond) || bitsPerSecond < 0.0) {
			throw new IllegalArgumentException(
				"Data rate must be finite and non-negative");
		}
		this.bitsPerSecond = bitsPerSecond;
	}

	public static DataRate ofBitsPerSecond(double bitsPerSecond) {
		return bitsPerSecond == 0.0 ? ZERO : new DataRate(bitsPerSecond);
	}

	public static DataRate ofMebibitsPerSecond(double mebibitsPerSecond) {
		if (!Double.isFinite(mebibitsPerSecond) || mebibitsPerSecond < 0.0) {
			throw new IllegalArgumentException(
				"Data rate must be finite and non-negative");
		}
		return ofBitsPerSecond(mebibitsPerSecond * BITS_PER_MEBIBIT);
	}

	public double toBitsPerSecond() {
		return bitsPerSecond;
	}

	public double toMebibitsPerSecond() {
		return bitsPerSecond / BITS_PER_MEBIBIT;
	}

	public boolean isZero() {
		return bitsPerSecond == 0.0;
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof DataRate)) {
			return false;
		}
		DataRate rate = (DataRate) other;
		return Double.doubleToLongBits(bitsPerSecond)
			== Double.doubleToLongBits(rate.bitsPerSecond);
	}

	@Override
	public int hashCode() {
		long bits = Double.doubleToLongBits(bitsPerSecond);
		return (int) (bits ^ (bits >>> 32));
	}

	@Override
	public String toString() {
		return bitsPerSecond + " bit/s";
	}
}
