package org.fog.localization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.MobileDevice;
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

	@Test
	public void initialCoordinatePreservesFractionalEntryTime() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MobileDevice mobileDevice = new MobileDevice("fractionalEntry", 0, 0, 0, 0, 0);
		ArrayList<String[]> path = new ArrayList<String[]>();
		path.add(new String[] { "150.25", "0", "10", "20", "1" });
		mobileDevice.setPath(path);

		new Coordinate().setInitialCoordinate(mobileDevice);

		assertEquals(150.25, mobileDevice.getStartTravelTime(), 0.0);
		assertEquals(-1, mobileDevice.getTravelTimeId());
	}

	private static Coordinate coordinate(int x, int y) {
		Coordinate coordinate = new Coordinate();
		coordinate.setCoordX(x);
		coordinate.setCoordY(y);
		return coordinate;
	}
}
