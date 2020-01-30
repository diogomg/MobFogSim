package org.fog.utils;

public class NetworkUsageMonitor {

	private static double networkUsageDevice = 0.0;
	private static double networkUsageCoudlets = 0.0;
	private static double networkUsageVMs = 0.0;

	public static void sendingTuple(double latency, double tupleNwSize) {
		networkUsageDevice += latency * tupleNwSize;
	}

	public static double getNetworkUsage() {
		return networkUsageDevice + networkUsageCoudlets;
	}

	public static void migrationTrafficUsage(double migrationDurationTime, double vmSize) {
		networkUsageCoudlets += migrationDurationTime * vmSize;
	}

	public static double getNetWorkUsageInMigration() {
		return networkUsageCoudlets;
	}

	public static void migrationVMTransferredData(double vmSize) {
		networkUsageVMs += vmSize;
	}

	public static double getVMTransferredData() {
		return networkUsageVMs;
	}
}
