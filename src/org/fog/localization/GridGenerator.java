package org.fog.localization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Generates fixed and random positions through one bounds-validation path. */
public final class GridGenerator {

	private final MapBounds bounds;

	public GridGenerator(MapBounds bounds) {
		if (bounds == null) {
			throw new IllegalArgumentException("Map bounds cannot be null");
		}
		this.bounds = bounds;
	}

	/**
	 * Returns positions in X-major order, preserving the historical topology
	 * construction sequence.
	 */
	public List<GridPosition> fixedPositions(int spacing) {
		if (spacing <= 0) {
			throw new IllegalArgumentException("Grid spacing must be positive");
		}
		List<GridPosition> positions = new ArrayList<GridPosition>();
		for (int x = 0; x < bounds.getWidth(); x += spacing) {
			for (int y = 0; y < bounds.getHeight(); y += spacing) {
				positions.add(position(x, y));
			}
		}
		return Collections.unmodifiableList(positions);
	}

	public GridPosition randomPosition(Random random) {
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		return position(random.nextInt(bounds.getWidth()),
			random.nextInt(bounds.getHeight()));
	}

	public GridPosition position(int x, int y) {
		return new GridPosition(bounds, x, y);
	}

	public static int spacingForCoverage(int coverage) {
		if (coverage <= 0) {
			throw new IllegalArgumentException("Grid coverage must be positive");
		}
		return 2 * coverage - (2 * coverage / 3);
	}
}
