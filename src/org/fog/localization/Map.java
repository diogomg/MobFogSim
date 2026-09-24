package org.fog.localization;

public class Map {
	private int sizeMap;
	private int map[][];

	public Map(int sizeMap) {
		this.sizeMap = sizeMap;
		map = new int[sizeMap][sizeMap];
	}

	public Map() {

	}

	public int getSizeMap() {
		return sizeMap;
	}

	public void setSizeMap(int sizeMap) {
		this.sizeMap = sizeMap;
	}

	public final int[][] getMap() {
		return map;
	}

	public final void setMap(int[][] map) {
		this.map = new int[this.getSizeMap()][this.getSizeMap()];
	}

}
