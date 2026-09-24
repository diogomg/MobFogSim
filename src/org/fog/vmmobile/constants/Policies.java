package org.fog.vmmobile.constants;

public class Policies {
	public static final int LOWEST_LATENCY = 0;
	public static final int LOWEST_DIST_BW_SMART_THING_SERVER_CLOUDLET = 1;
	public static final int LOWEST_DIST_BW_SMART_THING_AP = 2;
	/** @deprecated Use {@link #LOWEST_DIST_BW_SMART_THING_SERVER_CLOUDLET}. */
	@Deprecated
	public static final int LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET =
		LOWEST_DIST_BW_SMART_THING_SERVER_CLOUDLET;
	/** @deprecated Use {@link #LOWEST_DIST_BW_SMART_THING_AP}. */
	@Deprecated
	public static final int LOWEST_DIST_BW_SMARTTING_AP =
		LOWEST_DIST_BW_SMART_THING_AP;
	public static final int ILP = 3;
	public static final int FIXED_MIGRATION_POINT = 0;
	public static final int SPEED_MIGRATION_POINT = 1;
	public static final int FIXED_AP_LOCATION = 0;
	public static final int RANDOM_AP_LOCATION = 1;
	public static final int MIGRATION_COMPLETE_VM = 0;
	public static final int MIGRATION_CONTAINER_VM = 1;
	public static final int LIVE_MIGRATION = 2;
	public static final int ADD = 0;
	public static final int REMOVE = 1;
	public static final int FIXED_SC_LOCATION = 0;
	public static final int RANDOM_SC_LOCATION = 1;

}
