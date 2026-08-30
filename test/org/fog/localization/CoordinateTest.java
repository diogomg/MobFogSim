package org.fog.localization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.fog.vmmobile.constants.Directions;
import org.fog.vmmobile.constants.MaxAndMin;
import org.junit.Test;

public class CoordinateTest {

	@Test
	public void predictionClampsUpperBoundsToTheLastValidCoordinate() {
		Coordinate origin = coordinate(MaxAndMin.MAX_X - 2, MaxAndMin.MAX_Y - 2);

		Coordinate predicted = Coordinate.newCoordinateWithError(origin, 10,
			Directions.SOUTHEAST);

		assertEquals(MaxAndMin.MAX_X - 1, predicted.getCoordX());
		assertEquals(MaxAndMin.MAX_Y - 1, predicted.getCoordY());
		assertTrue(new Coordinate().isWithinLimitPosition(predicted));
	}

	@Test
	public void predictionStillClampsLowerBoundsToZero() {
		Coordinate origin = coordinate(2, 2);

		Coordinate predicted = Coordinate.newCoordinateWithError(origin, 10,
			Directions.NORTHWEST);

		assertEquals(0, predicted.getCoordX());
		assertEquals(0, predicted.getCoordY());
		assertTrue(new Coordinate().isWithinLimitPosition(predicted));
	}

	private static Coordinate coordinate(int x, int y) {
		Coordinate coordinate = new Coordinate();
		coordinate.setCoordX(x);
		coordinate.setCoordY(y);
		return coordinate;
	}
}
