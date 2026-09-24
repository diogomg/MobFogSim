package org.fog.gui.core;

public class PlaceHolder {

	protected Coordinates coordinates;
	protected boolean isOccupied;
	protected Node node;

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public boolean isOccupied() {
		return isOccupied;
	}

	public void setOccupied(boolean isOccupied) {
		this.isOccupied = isOccupied;
	}

	public PlaceHolder(Coordinates coordinates) {
		this.coordinates = coordinates;
		isOccupied = false;
	}

	public PlaceHolder() {
		coordinates = new Coordinates();
		isOccupied = false;
	}

	public PlaceHolder(int x, int y) {
		coordinates = new Coordinates(x, y);
		isOccupied = false;
	}

	public Coordinates getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(Coordinates coordinates) {
		this.coordinates = coordinates;
	}
}
