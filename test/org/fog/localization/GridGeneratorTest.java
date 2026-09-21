package org.fog.localization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class GridGeneratorTest {

	@Test
	public void rectangularFixedGridUsesIndependentWidthAndHeightBounds() {
		MapBounds bounds = new MapBounds(10, 5);
		List<GridPosition> positions =
			new GridGenerator(bounds).fixedPositions(4);

		assertEquals(Arrays.asList(
			new GridPosition(0, 0), new GridPosition(0, 4),
			new GridPosition(4, 0), new GridPosition(4, 4),
			new GridPosition(8, 0), new GridPosition(8, 4)), positions);
		for (GridPosition position : positions) {
			assertTrue(bounds.contains(position.getX(), position.getY()));
		}
	}

	@Test
	public void rectangularRandomGridSamplesEachIndependentBound() {
		BoundRecordingRandom random = new BoundRecordingRandom();
		GridPosition position = new GridGenerator(new MapBounds(17, 5))
			.randomPosition(random);

		assertEquals(Arrays.asList(17, 5), random.getBounds());
		assertEquals(new GridPosition(16, 4), position);
	}

	private static final class BoundRecordingRandom extends Random {
		private static final long serialVersionUID = 1L;
		private final java.util.ArrayList<Integer> bounds =
			new java.util.ArrayList<Integer>();

		@Override
		public int nextInt(int bound) {
			bounds.add(bound);
			return bound - 1;
		}

		private List<Integer> getBounds() {
			return bounds;
		}
	}
}
