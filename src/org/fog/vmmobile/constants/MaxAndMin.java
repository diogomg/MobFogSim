package org.fog.vmmobile.constants;

public final class MaxAndMin {

	public static final int AP_COVERAGE = 1000; //Max Ap coverage distance - It should modify
	public static final int CLOUDLET_COVERAGE = 1000; //Max cloudlet distance - It should modify
	public static final int MAX_DISTANCE_TO_HANDOFF = 40; //It cannot be less than Max_SPEED
	public static final int MIG_POINT = (int) (MAX_DISTANCE_TO_HANDOFF*1.3);// 0; //Distance from boundary - it should modify
	public static final int LIVE_MIG_POINT = 200;//It can be based on the Network's Bandwidth
	public static final int MAX_HANDOFF_TIME = 1200;
	public static final int MIN_HANDOFF_TIME = 700;
	public static final int MAX_AP_DEVICE = 15;
	public static final int MAX_SMART_THING = 7;
	public static final int MAX_SERVER_CLOUDLET = 10;
	public static final int MAX_X = 16000;
	public static final int MAX_Y = 16000;
	public static final int MAX_SPEED = 120;
	public static final int MAX_DIRECTION = 9;
	public static final int MAX_SERVICES = 3;
	public static final float MAX_VALUE_SERVICE = 1.1f;
	public static final float MAX_VALUE_AGREE = 70f;
	public static final int MAX_ST_IN_AP = 500;
	public static final int MAX_SIMULATION_TIME = 1000*60*2500; //30 minutes
	public static final int MAX_VM_SIZE = 128; //200MB
	public static final int MIN_VM_SIZE = 128; //100MB
	public static final int MAX_BANDWIDTH = 15 * 1024 * 1024;
	public static final int MIN_BANDWIDTH = 5 * 1024 * 1024;
	public static final int DELAY_PROCESS = 500;
	public static final double SIZE_CONTAINER = 0.6;
	public static final double PROCESS_CONTAINER = 1.3;

}
