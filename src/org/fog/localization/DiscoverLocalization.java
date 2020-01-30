package org.fog.localization;

public class DiscoverLocalization {

	private static int coordX;
	private static int coordY;
	private static double theta;
	private static int direction;

	public DiscoverLocalization() {
		// TODO Auto-generated constructor stub
		//
	}

	/**
	 * @param firstCoord
	 *        - The first device -> it can be the fixed device (serverCloudlet
	 *        or ApDevice)
	 * @param secondCoord
	 *        - The second device -> it can be the fixed or mobile device
	 *        (ApDevice or smartThing) As an alternative, the first and second
	 *        can be two serverCloudlets
	 * @return - It is a regional (NORTH, SOUTH, EAST... )
	 */
	public static int discoverLocal(Coordinate firstCoord, Coordinate secondCoord) {
		// first becomes the coord (0,0) and the second becomes a new position -
		// review this comment
		// Because the second is in relative position from first
		// transposition to plane to first becomes coord(0,0)
		// making cross because the findTheta's formula
		setCoordX((firstCoord.getCoordX() - secondCoord.getCoordX()) * (-1));
		// making cross
		setCoordY((firstCoord.getCoordY() - secondCoord.getCoordY()) * (-1));
		setTheta(Distances.findTheta(getCoordX(), getCoordY()));
		// verifying the relative position between firstCoord and secondCoord
		return Distances.findPosition(getTheta());
	}

	public static int getCoordX() {
		return coordX;
	}

	public static void setCoordX(int coordX) {
		DiscoverLocalization.coordX = coordX;
	}

	public static int getCoordY() {
		return coordY;
	}

	public static void setCoordY(int coordY) {
		DiscoverLocalization.coordY = coordY;
	}

	public static double getTheta() {
		return theta;
	}

	public static void setTheta(double theta) {
		DiscoverLocalization.theta = theta;
	}

	public static int getDirection() {
		return direction;
	}

	public static void setDirection(int direction) {
		DiscoverLocalization.direction = direction;
	}

}
