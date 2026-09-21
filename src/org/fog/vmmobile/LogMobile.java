package org.fog.vmmobile;

import java.util.function.Supplier;

public class LogMobile {
	public static final int ERROR = 1;
	public static final int DEBUG = 0;
	
	public static int LOG_LEVEL = LogMobile.DEBUG;
	public static boolean ENABLED = false;;
	
	public static void setLogLevel(int level){
		LogMobile.LOG_LEVEL = level;
	}
	
	public static void debug(String classJava, String message){
		debug(classJava, () -> message);
	}

	public static void debug(String classJava, Supplier<String> message){
		if(!ENABLED)
			return;
		if(LogMobile.LOG_LEVEL <= LogMobile.DEBUG)
			SimulationEventSink.current().trace(classJava, message);
	}
	
}
