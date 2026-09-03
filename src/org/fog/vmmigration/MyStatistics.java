package org.fog.vmmigration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.fog.vmmobile.LogMobile;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.cloudbus.cloudsim.util.RunOutputManager;

public class MyStatistics {
	private static MyStatistics instance;

	private double totalMigTimes;
	private double timeOutApplication;
	private int totalMigrations;

	private long myCountLostTuple;
	private long myCountTotalTuple;

	private String toPrint;
	private Map<Integer, Integer> myCount;

	private FileWriter fileLatency;
	private BufferedWriter printFile;
	private Map<Integer, File> fileMap;
	private int seed;
	private Map<Integer, Double> tupleLatency;
	private int myCountTuple;
	private int myCountLowestLatency;
	private int totalHandoff;

	private Map<Integer, Double> withoutConnectionTime;
	private Map<Integer, Double> maxWithoutConnectionTime;
	private double averageWithoutConnection;
	private int myCountWithoutConnection;
	private Map<Integer, Double> initialTimeWithoutConnection;

	private Map<Integer, Double> initialTimeDelayAfterNewConnection;
	private Map<Integer, Double> DelayAfterNewConnection;
	private Map<Integer, Double> maxDelayAfterNewConnection;
	private double averageDelayAfterNewConnection;
	private int myCountDelayAfterNewConnection;

	private Map<Integer, Double> withoutVmTime;
	private Map<Integer, Double> maxWithoutVmTime;
	private double averageWithoutVmTime;
	private int myCountWithoutVmTime;
	private Map<Integer, Double> initialWithoutVmTime;
	private Map<Integer, Double> initialDowntime;

	private Map<Integer, Double> migrationTime;
	private Map<Integer, Double> maxMigrationTime;
	private double averageMigrationTime;
	private int myCountMigrationTime;
	private Map<Integer, Double> downtime;
	private Map<Integer, Double> maxDowntime;
	private double averageDowntime;
	private int myCountDowntime;

	private Map<Integer, Double> powerHistory;
	private Map<Integer, Double> energyHistory;

	public MyStatistics() {
		totalMigTimes = 0.0;
		timeOutApplication = 0.0;
		totalMigrations = 0;
		myCountWithoutConnection = 0;
		myCountWithoutVmTime = 0;
		myCountDelayAfterNewConnection = 0;
		myCountMigrationTime = 0;
		myCountLowestLatency = 0;
		myCountDowntime = 0;
		myCount = new HashMap<Integer, Integer>();
		myCountTotalTuple = 0L;
		myCountLostTuple = 0L;
		averageMigrationTime = 0.0;
		averageDelayAfterNewConnection = 0.0;
		averageDowntime = 0.0;
		averageWithoutConnection = 0.0;
		averageWithoutVmTime = 0.0;

		tupleLatency = new HashMap<Integer, Double>();
		this.fileMap = new HashMap<Integer, File>();

		withoutConnectionTime = new HashMap<Integer, Double>();
		withoutVmTime = new HashMap<Integer, Double>();
		DelayAfterNewConnection = new HashMap<Integer, Double>();
		migrationTime = new HashMap<Integer, Double>();
		downtime = new HashMap<Integer, Double>();

		initialTimeWithoutConnection = new HashMap<Integer, Double>();
		initialWithoutVmTime = new HashMap<Integer, Double>();
		initialDowntime = new HashMap<Integer, Double>();
		initialTimeDelayAfterNewConnection = new HashMap<Integer, Double>();

		maxWithoutConnectionTime = new HashMap<Integer, Double>();
		maxWithoutVmTime = new HashMap<Integer, Double>();
		maxDelayAfterNewConnection = new HashMap<Integer, Double>();
		maxMigrationTime = new HashMap<Integer, Double>();
		maxDowntime = new HashMap<Integer, Double>();

		powerHistory = new HashMap<Integer, Double>();
		energyHistory = new HashMap<Integer, Double>();

	}

	public static MyStatistics getInstance() {
		if (instance == null)
			instance = new MyStatistics();
		return instance;
	}

	public void countMigration() {
		totalMigrations++;
	}

	public void putLatencyFileValue(double latency, double time, String appId, int smartThingMyId,
		String serverCloudletName, String tupleType) {
		int i = getMyCount().get(smartThingMyId) + 1;
		getMyCount().put(smartThingMyId, i);
		BufferedFileManager.writeLine(getFileMap().get(smartThingMyId),
			Integer.toString(i) + " - " + Double.toString(time) + " - "
				+ Double.toString(latency) + " - " + appId + " - smartThingMyId: "
				+ smartThingMyId + " - " + serverCloudletName + " - TupleType: - " + tupleType);
	}

	public void putLantencyFileName(String name, int smartThingMyId) {
		BufferedFileManager.writeLine(getFileMap().get(smartThingMyId), name);
	}

	public void startWithoutConnetion(int id, double clock) {
		getInitialTimeWithoutConnection().put(id, clock);
	}

	public void finalWithoutConnection(int id, double clock) {
		Double startedAt = initialTimeWithoutConnection.remove(id);
		if (startedAt == null) {
			return;
		}
		double delay = clock - startedAt;
		averageWithoutConnection = runningMean(averageWithoutConnection,
			myCountWithoutConnection, delay);
		myCountWithoutConnection++;
		withoutConnectionTime.put(id, delay);
		observeMaximum(maxWithoutConnectionTime, id, delay);
	}

	public void startWithoutVmTime(int id, double clock) {
		getInitialWithoutVmTime().put(id, clock);
	}

	public void finalWithoutVmTime(int id, double clock) {
		Double startedAt = initialWithoutVmTime.remove(id);
		if (startedAt == null) {
			return;
		}
		double delay = clock - startedAt;
		LogMobile.debug("MyStatistics.java", "SmartThing" + id + " - Downtime: " + delay);
		averageWithoutVmTime = runningMean(averageWithoutVmTime,
			myCountWithoutVmTime, delay);
		myCountWithoutVmTime++;
		withoutVmTime.put(id, delay);
		observeMaximum(maxWithoutVmTime, id, delay);
	}

	public void startDelayAfterNewConnection(int id, double clock) {
		getInitialTimeDelayAfterNewConnection().put(id, clock);
	}

	public void finalDelayAfterNewConnection(int id, double clock) {// T8
		Double startedAt = initialTimeDelayAfterNewConnection.remove(id);
		if (startedAt == null) {
			return;
		}
		double delay = clock - startedAt;
		LogMobile.debug("MyStatistics.java", "SmartThing" + id
			+ " - DelayAfterNewConnection: " + delay);
		averageDelayAfterNewConnection = runningMean(
			averageDelayAfterNewConnection, myCountDelayAfterNewConnection, delay);
		myCountDelayAfterNewConnection++;
		DelayAfterNewConnection.put(id, delay);
		observeMaximum(maxDelayAfterNewConnection, id, delay);
	}

	public void observeMigrationTime(int id, double time) {
		averageMigrationTime = runningMean(averageMigrationTime,
			myCountMigrationTime, time);
		myCountMigrationTime++;
		migrationTime.put(id, time);
		observeMaximum(maxMigrationTime, id, time);
	}

	public void observeDowntime(int id, double time) {
		averageDowntime = runningMean(averageDowntime, myCountDowntime, time);
		myCountDowntime++;
		downtime.put(id, time);
		observeMaximum(maxDowntime, id, time);
	}

	public void recordPowerAndEnergy(int id, double power, double energy) {
		powerHistory.put(id, power);
		energyHistory.put(id, energy);
	}

	public void incrementLowestLatencyCloudletCount() {
		myCountLowestLatency++;
	}

	public void incrementHandoffCount() {
		totalHandoff++;
	}

	public void incrementLostTupleCount() {
		myCountLostTuple++;
	}

	public void incrementTotalTupleCount() {
		myCountTotalTuple++;
	}

	public int nextTupleId() {
		return ++myCountTuple;
	}

	private static double runningMean(double currentMean, int currentCount,
		double observation) {
		return (currentMean * currentCount + observation) / (currentCount + 1);
	}

	private static void observeMaximum(Map<Integer, Double> maxima, int id,
		double observation) {
		Double currentMaximum = maxima.get(id);
		if (currentMaximum == null || observation > currentMaximum) {
			maxima.put(id, observation);
		}
	}

	public double getTotalMigTimes() {
		return totalMigTimes;
	}

	public void setTotalMigTimes(double totalMigTimes) {
		this.totalMigTimes = totalMigTimes;
	}

	public double getTimeOutApplication() {
		return timeOutApplication;
	}

	public void setTimeOutApplication(double timeOutApplication) {
		this.timeOutApplication = timeOutApplication;
	}

	public int getTotalMigrations() {
		return totalMigrations;
	}

	public void setTotalMigrations(int totalMigrations) {
		this.totalMigrations = totalMigrations;
	}

	public static void setInstance(MyStatistics instance) {
		MyStatistics.instance = instance;
	}

	public FileWriter getFileLatency() {
		return fileLatency;
	}

	public void setFileLatency(FileWriter fileLatency) {
		this.fileLatency = fileLatency;
	}

	public void setPrintFile(BufferedWriter printFile) {
		this.printFile = printFile;
	}

	public BufferedWriter getPrintFile() {
		return printFile;
	}

	public Map<Integer, File> getFileMap() {
		return fileMap;
	}

	public void setFileMap(String name, int id) {
		try {
			File file = RunOutputManager.getInstance().createFreshDetailedFile(name);
			this.fileMap.put(id, file);
		} catch (IOException error) {
			throw new UncheckedIOException(
				"Unable to create latency output file " + name, error);
		}
	}

	public int getSeed() {
		return seed;
	}

	public void setSeed(int seed) {
		this.seed = seed;
	}

	public Map<Integer, Double> getTupleLatency() {
		return tupleLatency;
	}

	public void setTupleLatency(Map<Integer, Double> tupleLatency) {
		this.tupleLatency = tupleLatency;
	}

	public int getMyCountTuple() {
		return myCountTuple;
	}

	public void setMyCountTuple(int myCountTuple) {
		this.myCountTuple = myCountTuple;
	}

	public Map<Integer, Double> getWithoutConnectionTime() {
		return withoutConnectionTime;
	}

	public void setWithoutConnectionTime(Map<Integer, Double> withoutConnectionTime) {
		this.withoutConnectionTime = withoutConnectionTime;
	}

	public Map<Integer, Double> getInitialTimeWithoutConnection() {
		return initialTimeWithoutConnection;
	}

	public void setInitialTimeWithoutConnection(Map<Integer, Double> initialTimeWithoutConnection) {
		this.initialTimeWithoutConnection = initialTimeWithoutConnection;
	}

	public double getAverageWithoutConnection() {
		return averageWithoutConnection;
	}

	public void setAverageWithoutConnection(double averageWithoutConnection) {
		this.averageWithoutConnection = averageWithoutConnection;
	}

	public int getMyCountWithoutConnection() {
		return myCountWithoutConnection;
	}

	public void setMyCountWithoutConnection(int myCountWithoutConnection) {
		this.myCountWithoutConnection = myCountWithoutConnection;
	}

	public Map<Integer, Double> getPowerHistory() {
		return powerHistory;
	}

	public void setPowerHistory(Map<Integer, Double> powerHistory) {
		this.powerHistory = powerHistory;
	}

	public Map<Integer, Double> getEnergyHistory() {
		return energyHistory;
	}

	public void setEnergyHistory(Map<Integer, Double> energyHistory) {
		this.energyHistory = energyHistory;
	}

	public Map<Integer, Double> getMaxWithoutConnectionTime() {
		return maxWithoutConnectionTime;
	}

	public void setMaxWithoutConnectionTime(
		Map<Integer, Double> maxWithoutConnectionTime) {
		this.maxWithoutConnectionTime = maxWithoutConnectionTime;
	}

	public Map<Integer, Double> getInitialTimeDelayAfterNewConnection() {
		return initialTimeDelayAfterNewConnection;
	}

	public void setInitialTimeDelayAfterNewConnection(
		Map<Integer, Double> initialTimeDelayAfterNewConnection) {
		this.initialTimeDelayAfterNewConnection = initialTimeDelayAfterNewConnection;
	}

	public Map<Integer, Double> getDelayAfterNewConnection() {
		return DelayAfterNewConnection;
	}

	public void setDelayAfterNewConnection(
		Map<Integer, Double> delayAfterNewConnection) {
		DelayAfterNewConnection = delayAfterNewConnection;
	}

	public Map<Integer, Double> getMaxDelayAfterNewConnection() {
		return maxDelayAfterNewConnection;
	}

	public void setMaxDelayAfterNewConnection(
		Map<Integer, Double> maxDelayAfterNewConnection) {
		this.maxDelayAfterNewConnection = maxDelayAfterNewConnection;
	}

	public double getAverageDelayAfterNewConnection() {
		return averageDelayAfterNewConnection;
	}

	public void setAverageDelayAfterNewConnection(
		double averageDelayAfterNewConnection) {
		this.averageDelayAfterNewConnection = averageDelayAfterNewConnection;
	}

	public int getMyCountDelayAfterNewConnection() {
		return myCountDelayAfterNewConnection;
	}

	public void setMyCountDelayAfterNewConnection(int myCountDelayAfterNewConnection) {
		this.myCountDelayAfterNewConnection = myCountDelayAfterNewConnection;
	}

	public Map<Integer, Double> getWithoutVmTime() {
		return withoutVmTime;
	}

	public void setWithoutVmTime(Map<Integer, Double> withoutVmTime) {
		this.withoutVmTime = withoutVmTime;
	}

	public Map<Integer, Double> getMaxWithoutVmTime() {
		return maxWithoutVmTime;
	}

	public void setMaxWithoutVmTime(Map<Integer, Double> maxWithoutVmTime) {
		this.maxWithoutVmTime = maxWithoutVmTime;
	}

	public double getAverageWithoutVmTime() {
		return averageWithoutVmTime;
	}

	public void setAverageWithoutVmTime(double averageWithoutVmTime) {
		this.averageWithoutVmTime = averageWithoutVmTime;
	}

	public int getMyCountWithoutVmTime() {
		return myCountWithoutVmTime;
	}

	public void setMyCountWithoutVmTime(int myCountWithoutVmTime) {
		this.myCountWithoutVmTime = myCountWithoutVmTime;
	}

	public Map<Integer, Double> getInitialWithoutVmTime() {
		return initialWithoutVmTime;
	}

	public void setInitialWithoutVmTime(Map<Integer, Double> initialWithoutVmTime) {
		this.initialWithoutVmTime = initialWithoutVmTime;
	}

	public Map<Integer, Double> getMigrationTime() {
		return migrationTime;
	}

	public void setMigrationTime(Map<Integer, Double> migrationTime) {
		this.migrationTime = migrationTime;
	}

	public Map<Integer, Double> getMaxMigrationTime() {
		return maxMigrationTime;
	}

	public void setMaxMigrationTime(Map<Integer, Double> maxMigrationTime) {
		this.maxMigrationTime = maxMigrationTime;
	}

	public double getAverageMigrationTime() {
		return averageMigrationTime;
	}

	public void setAverageMigrationTime(double averageMigrationTime) {
		this.averageMigrationTime = averageMigrationTime;
	}

	public Map<Integer, Double> getDowntime() {
		return downtime;
	}

	public void setDowntime(Map<Integer, Double> downtime) {
		this.downtime = downtime;
	}

	public Map<Integer, Double> getMaxDowntime() {
		return maxDowntime;
	}

	public void setMaxDowntime(Map<Integer, Double> maxDowntime) {
		this.maxDowntime = maxDowntime;
	}

	public double getAverageDowntime() {
		return averageDowntime;
	}

	public void setAverageDowntime(double averageDowntime) {
		this.averageDowntime = averageDowntime;
	}

	public Map<Integer, Double> getInitialDowntime() {
		return initialDowntime;
	}

	public void setInitialDowntime(Map<Integer, Double> initialDowntime) {
		this.initialDowntime = initialDowntime;
	}

	public int getMyCountMigrationTime() {
		return myCountMigrationTime;
	}

	public void setMyCountMigrationTime(int myCountMigrationTime) {
		this.myCountMigrationTime = myCountMigrationTime;
	}

	public int getMyCountDowntime() {
		return myCountDowntime;
	}

	public void setMyCountDowntime(int myCountDowntime) {
		this.myCountDowntime = myCountDowntime;
	}

	public String getToPrint() {
		return toPrint;
	}

	public void setToPrint(String toPrint) {
		this.toPrint = toPrint;
	}

	public int getMyCountLowestLatency() {
		return myCountLowestLatency;
	}

	public void setMyCountLowestLatency(int myCountLowestLatency) {
		this.myCountLowestLatency = myCountLowestLatency;
	}

	public int getTotalHandoff() {
		return totalHandoff;
	}

	public void setTotalHandoff(int totalHandoff) {
		this.totalHandoff = totalHandoff;
	}

	public void setMyCount(Map<Integer, Integer> myCount) {
		this.myCount = myCount;
	}

	public Map<Integer, Integer> getMyCount() {
		return myCount;
	}

	public long getMyCountLostTuple() {
		return myCountLostTuple;
	}

	public void setMyCountLostTuple(long myCountLostTuple) {
		this.myCountLostTuple = myCountLostTuple;
	}

	public long getMyCountTotalTuple() {
		return myCountTotalTuple;
	}

	public void setMyCountTotalTuple(long myCountTotalTuple) {
		this.myCountTotalTuple = myCountTotalTuple;
	}

}
