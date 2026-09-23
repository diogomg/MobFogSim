package org.fog.vmmigration;

/** Immutable mobility-prediction observation produced during policy evaluation. */
public final class MigrationPrediction {
	private final int travelTimeId;
	private final int actualX;
	private final int actualY;
	private final int predictedX;
	private final int predictedY;
	private final int adjustedX;
	private final int adjustedY;
	private final double actualToPredictedDistance;
	private final double actualToAdjustedDistance;
	private final double predictedToAdjustedDistance;
	private final int speed;

	MigrationPrediction(int travelTimeId, int actualX, int actualY,
		int predictedX, int predictedY, int adjustedX, int adjustedY,
		double actualToPredictedDistance,
		double actualToAdjustedDistance,
		double predictedToAdjustedDistance, int speed) {
		this.travelTimeId = travelTimeId;
		this.actualX = actualX;
		this.actualY = actualY;
		this.predictedX = predictedX;
		this.predictedY = predictedY;
		this.adjustedX = adjustedX;
		this.adjustedY = adjustedY;
		this.actualToPredictedDistance = actualToPredictedDistance;
		this.actualToAdjustedDistance = actualToAdjustedDistance;
		this.predictedToAdjustedDistance = predictedToAdjustedDistance;
		this.speed = speed;
	}

	public int getTravelTimeId() {
		return travelTimeId;
	}

	public int getActualX() {
		return actualX;
	}

	public int getActualY() {
		return actualY;
	}

	public int getPredictedX() {
		return predictedX;
	}

	public int getPredictedY() {
		return predictedY;
	}

	public int getAdjustedX() {
		return adjustedX;
	}

	public int getAdjustedY() {
		return adjustedY;
	}

	public double getActualToPredictedDistance() {
		return actualToPredictedDistance;
	}

	public double getActualToAdjustedDistance() {
		return actualToAdjustedDistance;
	}

	public double getPredictedToAdjustedDistance() {
		return predictedToAdjustedDistance;
	}

	public int getSpeed() {
		return speed;
	}
}
