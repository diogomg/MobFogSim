package org.fog.localization;

/** Immutable position sample from a mobility trace. */
public final class MobilitySample {
	private static final int COLUMN_COUNT = 5;

	private final double timeSeconds;
	private final double directionRadians;
	private final double x;
	private final double y;
	private final double speed;

	public MobilitySample(double timeSeconds, double directionRadians, double x,
		double y, double speed) {
		requireFinite("time", timeSeconds);
		requireFinite("direction", directionRadians);
		requireFinite("x coordinate", x);
		requireFinite("y coordinate", y);
		requireFinite("speed", speed);
		if (timeSeconds < 0.0) {
			throw new IllegalArgumentException("Mobility sample time cannot be negative");
		}
		if (speed < 0.0) {
			throw new IllegalArgumentException("Mobility sample speed cannot be negative");
		}
		this.timeSeconds = timeSeconds;
		this.directionRadians = directionRadians;
		this.x = x;
		this.y = y;
		this.speed = speed;
	}

	public static MobilitySample fromColumns(String[] columns) {
		if (columns == null || columns.length != COLUMN_COUNT) {
			throw new IllegalArgumentException(
				"A mobility sample must contain exactly five columns");
		}
		return new MobilitySample(Double.parseDouble(columns[0]),
			Double.parseDouble(columns[1]), Double.parseDouble(columns[2]),
			Double.parseDouble(columns[3]), Double.parseDouble(columns[4]));
	}

	public double getTimeSeconds() {
		return timeSeconds;
	}

	public double getDirectionRadians() {
		return directionRadians;
	}

	public double getX() {
		return x;
	}

	public double getY() {
		return y;
	}

	public double getSpeed() {
		return speed;
	}

	public String[] toColumns() {
		return new String[] { Double.toString(timeSeconds),
			Double.toString(directionRadians), Double.toString(x), Double.toString(y),
			Double.toString(speed) };
	}

	private static void requireFinite(String name, double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException("Mobility sample " + name + " must be finite");
		}
	}
}
