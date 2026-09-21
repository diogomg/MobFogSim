package org.fog.localization;

import org.fog.vmmobile.constants.MaxAndMin;

/** Immutable, exclusive upper bounds for a simulation map. */
public final class MapBounds {

	private final int width;
	private final int height;

	public MapBounds(int width, int height) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException(
				"Map width and height must be positive");
		}
		this.width = width;
		this.height = height;
	}

	public static MapBounds defaults() {
		return new MapBounds(MaxAndMin.MAX_X, MaxAndMin.MAX_Y);
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean contains(int x, int y) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}

	void requireContains(int x, int y) {
		if (!contains(x, y)) {
			throw new IllegalArgumentException("Position (" + x + ", " + y
				+ ") is outside map bounds " + width + " x " + height);
		}
	}
}
