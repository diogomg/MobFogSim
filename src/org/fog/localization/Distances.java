package org.fog.localization;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.constants.Directions;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.MovementDirection;

public final class Distances {

	private Distances() {
	}

	public static Optional<ApDevice> findClosestAp(List<ApDevice> apDevices,
		MobileDevice smartThing) {
		Objects.requireNonNull(smartThing, "smartThing cannot be null");
		ApDevice closest = findClosestAp(apDevices, smartThing.getCoord());
		if (closest == null
			|| checkDistance(closest.getCoord(), smartThing.getCoord()) > MaxAndMin.AP_COVERAGE) {
			return Optional.empty();
		}
		return Optional.of(closest);
	}

	public static Optional<FogDevice> findClosestServerCloudlet(
		List<FogDevice> serverCloudlets, MobileDevice smartThing) {
		Objects.requireNonNull(smartThing, "smartThing cannot be null");
		return findClosestServerCloudlet(serverCloudlets,
			smartThing.getFutureCoord());
	}

	/** Pure overload for a separately calculated mobility prediction. */
	public static Optional<FogDevice> findClosestServerCloudlet(
		List<FogDevice> serverCloudlets, Coordinate predictedCoordinate) {
		return Optional.ofNullable(findClosestFogDevice(serverCloudlets,
			predictedCoordinate));
	}

	public static Optional<FogDevice> findClosestServerCloudletToAp(
		List<FogDevice> serverCloudlets, ApDevice apDevice) {
		Objects.requireNonNull(apDevice, "apDevice cannot be null");
		return Optional.ofNullable(findClosestFogDevice(serverCloudlets,
			apDevice.getCoord()));
	}

	private static ApDevice findClosestAp(List<ApDevice> apDevices,
		Coordinate target) {
		Objects.requireNonNull(apDevices, "apDevices cannot be null");
		ApDevice closest = null;
		double minimumDistance = Double.POSITIVE_INFINITY;
		for (ApDevice apDevice : apDevices) {
			Objects.requireNonNull(apDevice, "apDevices cannot contain null");
			double candidateDistance = checkDistance(apDevice.getCoord(), target);
			if (candidateDistance < minimumDistance) {
				closest = apDevice;
				minimumDistance = candidateDistance;
			}
		}
		return closest;
	}

	private static FogDevice findClosestFogDevice(List<FogDevice> fogDevices,
		Coordinate target) {
		Objects.requireNonNull(fogDevices, "fogDevices cannot be null");
		Objects.requireNonNull(target, "target coordinate cannot be null");
		FogDevice closest = null;
		double minimumDistance = Double.POSITIVE_INFINITY;
		for (FogDevice fogDevice : fogDevices) {
			Objects.requireNonNull(fogDevice, "fogDevices cannot contain null");
			double candidateDistance = checkDistance(fogDevice.getCoord(), target);
			if (candidateDistance < minimumDistance) {
				closest = fogDevice;
				minimumDistance = candidateDistance;
			}
		}
		return closest;
	}

	public static double findTheta(int coordX, int coordY) {
		double angle = Math.toDegrees(Math.atan2(coordY, coordX));
		return angle < 0.0 ? angle + 360.0 : angle;
	}

	public static int findPosition(double theta) {
		return findMovementDirection(theta).legacyValue();
	}

	public static MovementDirection findMovementDirection(double theta) {
		if ((theta >= 0 && theta <= 22.5) || (theta > 337.5 && theta <= 360))
			return MovementDirection.EAST;
		else if (theta > 22.5 && theta <= 67.5)
			return MovementDirection.SOUTHEAST;
		else if (theta > 67.5 && theta <= 112.5)
			return MovementDirection.SOUTH;
		else if (theta > 112.5 && theta <= 157.5)
			return MovementDirection.SOUTHWEST;
		else if (theta > 157.5 && theta <= 202.5)
			return MovementDirection.WEST;
		else if (theta > 202.5 && theta <= 247.5)
			return MovementDirection.NORTHWEST;
		else if (theta > 247.5 && theta <= 292.5)
			return MovementDirection.NORTH;
		else if (theta > 292.5 && theta <= 337.5)
			return MovementDirection.NORTHEAST;
		return MovementDirection.NONE;
	}

	public static double checkDistance(Coordinate firstCoord, Coordinate secondCoord) {
		Objects.requireNonNull(firstCoord, "firstCoord cannot be null");
		Objects.requireNonNull(secondCoord, "secondCoord cannot be null");
		double deltaX = (double) firstCoord.getCoordX() - secondCoord.getCoordX();
		double deltaY = (double) firstCoord.getCoordY() - secondCoord.getCoordY();
		return Math.hypot(deltaX, deltaY);
	}
}
