package org.fog.localization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

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
		mobileDevice.setMobilityPath(Arrays.asList(
			new MobilitySample(150.25, 0, 10, 20, 1)));

		new Coordinate().setInitialCoordinate(mobileDevice);

		assertEquals(150.25, mobileDevice.getStartTravelTime(), 0.0);
		assertEquals(1, mobileDevice.getTravelTimeId());
	}

	@Test
	public void advancesOnlyDueRowsAndPreservesDuplicateTimestampOrder() {
		MobileDevice mobileDevice = new MobileDevice("irregular", 0, 0, 0, 0, 0);
		mobileDevice.setMobilityPath(irregularSamples());
		Coordinate coordinate = new Coordinate();
		coordinate.setInitialCoordinate(mobileDevice);
		mobileDevice.setTravelTimeId(1);

		assertEquals(0, coordinate.advanceToTime(mobileDevice, 4.999));
		assertEquals(10, mobileDevice.getCoord().getCoordX());
		assertEquals(1, mobileDevice.getTravelTimeId());

		assertEquals(2, coordinate.advanceToTime(mobileDevice, 5.0));
		assertEquals(30, mobileDevice.getCoord().getCoordX());
		assertEquals(3, mobileDevice.getTravelTimeId());

		assertEquals(1, coordinate.advanceToTime(mobileDevice, 11.75));
		assertEquals(40, mobileDevice.getCoord().getCoordX());
		assertEquals(4, mobileDevice.getTravelTimeId());
	}

	@Test
	public void predictionLookupUsesElapsedTimeAndLastDuplicateSample() {
		List<MobilitySample> samples = irregularSamples();

		assertEquals(10.0,
			MobilityTimeline.sampleAtOrBefore(samples, 4.0).getX(), 0.0);
		assertEquals(30.0,
			MobilityTimeline.sampleAtOrBefore(samples, 5.0).getX(), 0.0);
		assertEquals(30.0,
			MobilityTimeline.sampleAtOrBefore(samples, 7.0).getX(), 0.0);
		assertEquals(40.0,
			MobilityTimeline.sampleAtOrBefore(samples, 100.0).getX(), 0.0);
	}

	private static List<MobilitySample> irregularSamples() {
		return Arrays.asList(new MobilitySample(2.5, 0, 10, 10, 1),
			new MobilitySample(5.0, 0, 20, 20, 1),
			new MobilitySample(5.0, 0, 30, 30, 1),
			new MobilitySample(11.75, 0, 40, 40, 1));
	}

	private static Coordinate coordinate(int x, int y) {
		Coordinate coordinate = new Coordinate();
		coordinate.setCoordX(x);
		coordinate.setCoordY(y);
		return coordinate;
	}
}
