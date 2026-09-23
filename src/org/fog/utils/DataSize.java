package org.fog.utils;

/** Immutable data quantity stored in bytes. */
public final class DataSize {
	private static final double BYTES_PER_MEBIBYTE = 1024.0 * 1024.0;
	private static final double BITS_PER_BYTE = 8.0;
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	public static final DataSize ZERO = new DataSize(0.0);

	private final double bytes;

	private DataSize(double bytes) {
		if (!Double.isFinite(bytes) || bytes < 0.0) {
			throw new IllegalArgumentException(
				"Data size must be finite and non-negative");
		}
		this.bytes = bytes;
	}

	public static DataSize ofBytes(double bytes) {
		return bytes == 0.0 ? ZERO : new DataSize(bytes);
	}

	public static DataSize ofMebibytes(double mebibytes) {
		if (!Double.isFinite(mebibytes) || mebibytes < 0.0) {
			throw new IllegalArgumentException(
				"Data size must be finite and non-negative");
		}
		return ofBytes(mebibytes * BYTES_PER_MEBIBYTE);
	}

	public double toBytes() {
		return bytes;
	}

	public double toMebibytes() {
		return bytes / BYTES_PER_MEBIBYTE;
	}

	/** Serialization time for this quantity at the supplied positive rate. */
	public SimulationDuration transferDurationAt(DataRate rate) {
		if (rate == null || rate.isZero()) {
			throw new IllegalArgumentException(
				"Transfer data rate must be positive");
		}
		return SimulationDuration.ofMilliseconds(bytes * BITS_PER_BYTE
			/ rate.toBitsPerSecond() * MILLISECONDS_PER_SECOND);
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof DataSize)) {
			return false;
		}
		DataSize size = (DataSize) other;
		return Double.doubleToLongBits(bytes)
			== Double.doubleToLongBits(size.bytes);
	}

	@Override
	public int hashCode() {
		long bits = Double.doubleToLongBits(bytes);
		return (int) (bits ^ (bits >>> 32));
	}

	@Override
	public String toString() {
		return bytes + " bytes";
	}
}
