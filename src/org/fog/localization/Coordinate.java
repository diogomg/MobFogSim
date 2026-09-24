package org.fog.localization;

import java.util.List;

import org.fog.entities.MobileDevice;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.MovementDirection;

public class Coordinate { // extends Map {

	private int coordX;
	private int coordY;

	public Coordinate() {
	}

	public boolean isWithinLimitPosition(Coordinate c) {
		if (c.getCoordX() >= MaxAndMin.MAX_X ||
			c.getCoordX() < 0 ||
			c.getCoordY() >= MaxAndMin.MAX_Y ||
			c.getCoordY() < 0)
			return false;
		else
			return true;
	}

	public void desableSmartThing(MobileDevice smartThing) {
		smartThing.setCoord(-1, -1);
	}

	public static double radiansToDegree(Double direction) {

		double degree = direction * (180 / Math.PI);

		if (degree < 0)
			degree += 360;

		return degree;
	}

	public static int convertDirection(Double direction) {
		return convertMovementDirection(direction).legacyValue();
	}

	public static MovementDirection convertMovementDirection(Double direction) {

		double degree = radiansToDegree(direction);

		if (degree > 337.5 || degree <= 22.5)
			return MovementDirection.EAST;
		else if (degree > 22.5 && degree <= 67.5)
			return MovementDirection.NORTHEAST;
		else if (degree > 67.5 && degree <= 112.5)
			return MovementDirection.NORTH;
		else if (degree > 112.5 && degree <= 157.5)
			return MovementDirection.NORTHWEST;
		else if (degree > 157.5 && degree <= 202.5)
			return MovementDirection.WEST;
		else if (degree > 202.5 && degree <= 247.5)
			return MovementDirection.SOUTHWEST;
		else if (degree > 247.5 && degree <= 292.5)
			return MovementDirection.SOUTH;
		else
			return MovementDirection.SOUTHEAST;
	}

	public void newCoordinate(MobileDevice smartThing) {
		List<MobilitySample> path = smartThing.getMobilityPath();
		if (smartThing.getTravelTimeId() >= 0
			&& smartThing.getTravelTimeId() < path.size()) {
			MobilitySample sample = path.get(smartThing.getTravelTimeId());
			smartThing.setTravelTimeId(smartThing.getTravelTimeId() + 1);
			applySample(smartThing, sample);
		}
		else {
			desableSmartThing(smartThing);
		}
	}

	/** Applies every unconsumed sample due at the supplied absolute trace time. */
	public int advanceToTime(MobileDevice smartThing, double traceTimeSeconds) {
		if (!Double.isFinite(traceTimeSeconds)) {
			throw new IllegalArgumentException("Mobility replay time must be finite");
		}
		List<MobilitySample> path = smartThing.getMobilityPath();
		int cursor = smartThing.getTravelTimeId();
		if (cursor < 0) {
			throw new IllegalStateException(
				"A mobile-device mobility cursor cannot be negative");
		}

		int applied = 0;
		while (cursor < path.size()
			&& MobilityTimeline.isDue(path.get(cursor), traceTimeSeconds)) {
			applySample(smartThing, path.get(cursor));
			cursor++;
			applied++;
			if (smartThing.getCoord().getCoordX() == -1) {
				break;
			}
		}
		smartThing.setTravelTimeId(cursor);
		return applied;
	}

	public void setInitialCoordinate(MobileDevice smartThing) {

		List<MobilitySample> path = smartThing.getMobilityPath();
		if (!path.isEmpty()) {
			MobilitySample sample = path.get(0);

			// The cursor always identifies the next unconsumed row. Lifecycle state,
			// rather than a sentinel cursor value, controls when replay may begin.
			smartThing.setTravelTimeId(1);
			smartThing.setStartTravelTime(sample.getTimeSeconds());
			applySample(smartThing, sample);
		}
		else {
			smartThing.setTravelTimeId(0);
			desableSmartThing(smartThing);
		}
	}

	private void applySample(MobileDevice smartThing, MobilitySample sample) {
		int x = (int) sample.getX();
		int y = (int) sample.getY();
		if (x < 0 || y < 0 || x >= MaxAndMin.MAX_X || y >= MaxAndMin.MAX_Y) {
			desableSmartThing(smartThing);
			return;
		}
		smartThing.setMovementDirection(
			convertMovementDirection(sample.getDirectionRadians()));
		smartThing.getCoord().setCoordX(x);
		smartThing.getCoord().setCoordY(y);
		smartThing.setSpeed((int) sample.getSpeed());
	}

	public void newCoordinate(MobileDevice smartThing, int add, Coordinate coordDevices) {
		if (smartThing.getSpeed() != 0) {
			int increaseX = (smartThing.getCoord().getCoordX() + (smartThing.getSpeed() * add));
			int increaseY = (smartThing.getCoord().getCoordY() + (smartThing.getSpeed() * add));
			int decreaseX = (smartThing.getCoord().getCoordX() - (smartThing.getSpeed() * add));
			int decreaseY = (smartThing.getCoord().getCoordY() - (smartThing.getSpeed() * add));
			MovementDirection direction = smartThing.getMovementDirection();

			if (decreaseX < 0 || decreaseY < 0 || increaseX >= MaxAndMin.MAX_X
				|| increaseY >= MaxAndMin.MAX_Y) {// It checks the CoordDevices limits.
				desableSmartThing(smartThing);
				return;
			}

			if (direction == MovementDirection.EAST) {
				/* same Y, increase X */
				smartThing.getCoord().setCoordX(increaseX);
			}
			else if (direction == MovementDirection.WEST) {
				/* same Y, decrease X */
				// next position in the same direction
				smartThing.getCoord().setCoordX(decreaseX);
			}
			else if (direction == MovementDirection.SOUTH) {
				/* same X, increase Y */
				// next position in the same direction
				smartThing.getCoord().setCoordY(increaseY);
			}
			else if (direction == MovementDirection.NORTH) {
				/* same X, decrease Y */
				smartThing.getCoord().setCoordY(decreaseY);
			}
			else if (direction == MovementDirection.SOUTHEAST) {
				/* increase X and Y */
				smartThing.getCoord().setCoordX(increaseX);
				smartThing.getCoord().setCoordY(increaseY);
			}
			else if (direction == MovementDirection.NORTHWEST) {
				/* decrease X and Y */
				smartThing.getCoord().setCoordX(decreaseX);
				smartThing.getCoord().setCoordY(decreaseY);
			}
			else if (direction == MovementDirection.SOUTHWEST) {
				/* decrease X increase Y */
				smartThing.getCoord().setCoordX(decreaseX);
				smartThing.getCoord().setCoordY(increaseY);
			}
			else if (direction == MovementDirection.NORTHEAST) {
				/* increase X decrease Y */
				smartThing.getCoord().setCoordX(increaseX);
				smartThing.getCoord().setCoordY(decreaseY);
			}
		}
	}

	public static Coordinate newCoordinateWithError(Coordinate coord, int mobilityPredictionError,
		int direction) {
		return newCoordinateWithError(coord, mobilityPredictionError,
			MovementDirection.fromLegacy(direction));
	}

	public static Coordinate newCoordinateWithError(Coordinate coord,
		int mobilityPredictionError, MovementDirection direction) {
		if (direction == null) {
			throw new IllegalArgumentException("Prediction-error direction cannot be null");
		}

		int x = coord.getCoordX(), y = coord.getCoordY();

		if (direction == MovementDirection.EAST) {
			x += mobilityPredictionError;
		}
		else if (direction == MovementDirection.NORTHEAST) {
			x += mobilityPredictionError;
			y -= mobilityPredictionError;
		}
		else if (direction == MovementDirection.NORTH) {
			y -= mobilityPredictionError;
		}
		else if (direction == MovementDirection.NORTHWEST) {
			x -= mobilityPredictionError;
			y -= mobilityPredictionError;
		}
		else if (direction == MovementDirection.WEST) {
			x -= mobilityPredictionError;
		}
		else if (direction == MovementDirection.SOUTHWEST) {
			x -= mobilityPredictionError;
			y += mobilityPredictionError;
		}
		else if (direction == MovementDirection.SOUTH) {
			y += mobilityPredictionError;
		}
		else {
			x += mobilityPredictionError;
			y += mobilityPredictionError;
		}

		if (x < 0)
			x = 0;
		if (y < 0)
			y = 0;
		if (x >= MaxAndMin.MAX_X)
			x = MaxAndMin.MAX_X - 1;
		if (y >= MaxAndMin.MAX_Y)
			y = MaxAndMin.MAX_Y - 1;

		Coordinate coord_result = new Coordinate();
		coord_result.setCoordX(x);
		coord_result.setCoordY(y);
		return coord_result;
	}

	public int getCoordX() {
		return coordX;
	}

	public void setCoordX(int coordX) {
		this.coordX = coordX;
	}

	public int getCoordY() {
		return coordY;
	}

	public void setCoordY(int coordY) {
		this.coordY = coordY;
	}
}
