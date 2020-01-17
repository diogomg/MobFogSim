package org.fog.vmmobile;

import java.text.DecimalFormat;

import org.cloudbus.cloudsim.core.CloudSim;

public class LogMobile {
	public static final int ERROR = 1;
	public static final int DEBUG = 0;
	
	public static int LOG_LEVEL = LogMobile.DEBUG;
	private static DecimalFormat df = new DecimalFormat("#.00"); 

	public static boolean ENABLED = false;;
	
	public static void setLogLevel(int level){
		LogMobile.LOG_LEVEL = level;
	}
	
	public static void debug(String classJava, String message){
		if(!ENABLED)
			return;
		if(LogMobile.LOG_LEVEL <= LogMobile.DEBUG)
			System.out.println("Clock: "+CloudSim.clock()+" - "+classJava+": "+message);
	}
//	public static void error(String name, String message){
//		if(!ENABLED)
//			return;
//		if(LogMobile.LOG_LEVEL <= LogMobile.ERROR)
//			System.out.println(df.format(CloudSim.clock())+" : "+name+" : "+message);
//	}
	
}
