package org.fog.vmmobile.policy;

/** Compass direction used by mobility and migration prediction. */
public enum MovementDirection {
	NONE(0),
	EAST(1),
	NORTHEAST(2),
	NORTH(3),
	NORTHWEST(4),
	WEST(5),
	SOUTHWEST(6),
	SOUTH(7),
	SOUTHEAST(8);

	private final int legacyValue;

	MovementDirection(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	/** Returns whether the candidate is this heading or either adjacent heading. */
	public boolean containsInMigrationCone(MovementDirection candidate) {
		if (candidate == null) {
			return false;
		}
		if (this == NONE) {
			return candidate == NONE || candidate == EAST;
		}
		int heading = legacyValue - 1;
		int candidateHeading = candidate.legacyValue - 1;
		if (candidateHeading < 0) {
			return false;
		}
		int clockwiseDistance = (candidateHeading - heading + 8) % 8;
		return clockwiseDistance == 0 || clockwiseDistance == 1
			|| clockwiseDistance == 7;
	}

	public static MovementDirection fromLegacy(int value) {
		for (MovementDirection direction : values()) {
			if (direction.legacyValue == value) {
				return direction;
			}
		}
		throw new IllegalArgumentException("Movement direction must be between 0 and 8");
	}
}
