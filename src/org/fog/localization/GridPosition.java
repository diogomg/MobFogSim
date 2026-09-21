package org.fog.localization;

/** Coordinate value produced by a validated map-grid generator. */
public final class GridPosition {

	private final int x;
	private final int y;

	public GridPosition(int x, int y) {
		if (x < 0 || y < 0) {
			throw new IllegalArgumentException(
				"Grid coordinates must be non-negative");
		}
		this.x = x;
		this.y = y;
	}

	GridPosition(MapBounds bounds, int x, int y) {
		if (bounds == null) {
			throw new IllegalArgumentException("Map bounds cannot be null");
		}
		bounds.requireContains(x, y);
		this.x = x;
		this.y = y;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	/** Chebyshev coordinate distance, suitable for the simulator's square grid. */
	public long chebyshevDistanceTo(GridPosition other) {
		if (other == null) {
			throw new IllegalArgumentException("Other grid position cannot be null");
		}
		long xDistance = Math.abs((long) x - other.x);
		long yDistance = Math.abs((long) y - other.y);
		return Math.max(xDistance, yDistance);
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof GridPosition)) {
			return false;
		}
		GridPosition other = (GridPosition) object;
		return x == other.x && y == other.y;
	}

	@Override
	public int hashCode() {
		return 31 * x + y;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ")";
	}
}
