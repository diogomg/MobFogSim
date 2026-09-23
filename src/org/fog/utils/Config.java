package org.fog.utils;

public final class Config {

	public static final double RESOURCE_MGMT_INTERVAL = 100;
	public static final int MAX_SIMULATION_TIME = 10000;
	public static final int RESOURCE_MANAGE_INTERVAL = 100;
	public static final String FOG_DEVICE_ARCH = "x86";
	public static final String FOG_DEVICE_OS = "Linux";
	public static final String FOG_DEVICE_VMM = "Xen";
	public static final double FOG_DEVICE_TIMEZONE = 10.0;
	public static final double FOG_DEVICE_COST = 3.0;
	public static final double FOG_DEVICE_COST_PER_MEMORY = 0.05;
	public static final double FOG_DEVICE_COST_PER_STORAGE = 0.001;
	public static final double FOG_DEVICE_COST_PER_BW = 0.0;

	private Config() {
	}
}
