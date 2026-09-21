package org.fog.utils;

import java.util.function.Supplier;

import org.fog.vmmobile.SimulationEventSink;

public class Logger {

	public static final int ERROR = 1;
	public static final int DEBUG = 0;

	public static int LOG_LEVEL = Logger.DEBUG;
	public static boolean ENABLED = false;;

	public static void setLogLevel(int level) {
		Logger.LOG_LEVEL = level;
	}

	public static void debug(String name, String message) {
		debug(name, () -> message);
	}

	public static void debug(String name, Supplier<String> message) {
		if (!ENABLED)
			return;
		if (Logger.LOG_LEVEL <= Logger.DEBUG)
			SimulationEventSink.current().trace(name, message);
	}

	public static void error(String name, String message) {
		if (!ENABLED)
			return;
		if (Logger.LOG_LEVEL <= Logger.ERROR)
			SimulationEventSink.current().detail(name, () -> message);
	}

}
