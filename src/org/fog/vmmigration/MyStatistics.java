package org.fog.vmmigration;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.fog.vmmobile.LogMobile;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.cloudbus.cloudsim.util.RunOutputManager;

public class MyStatistics {
	private static MyStatistics instance;

	private int totalMigrations;

	private long myCountLostTuple;
	private long myCountTotalTuple;

	private String toPrint;
	private Map<Integer, Integer> myCount;

	private Map<Integer, File> fileMap;
	private int seed;
	private int myCountTuple;
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
		totalMigrations = 0;
		myCountWithoutConnection = 0;
		myCountWithoutVmTime = 0;
		myCountDelayAfterNewConnection = 0;
		myCountMigrationTime = 0;
		myCountDowntime = 0;
		myCount = new HashMap<Integer, Integer>();
		myCountTotalTuple = 0L;
		myCountLostTuple = 0L;
		averageMigrationTime = 0.0;
		averageDelayAfterNewConnection = 0.0;
		averageDowntime = 0.0;
		averageWithoutConnection = 0.0;
		averageWithoutVmTime = 0.0;

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
		Integer current = myCount.get(smartThingMyId);
		if (current == null) {
			throw new IllegalStateException("Latency counter is not initialised for user "
				+ smartThingMyId);
		}
		int i = current + 1;
		myCount.put(smartThingMyId, i);
		BufferedFileManager.writeLine(getFileMap().get(smartThingMyId),
			Integer.toString(i) + " - " + Double.toString(time) + " - "
				+ Double.toString(latency) + " - " + appId + " - smartThingMyId: "
				+ smartThingMyId + " - " + serverCloudletName + " - TupleType: - " + tupleType);
	}

	public void putLantencyFileName(String name, int smartThingMyId) {
		BufferedFileManager.writeLine(getFileMap().get(smartThingMyId), name);
	}

	public void startWithoutConnetion(int id, double clock) {
		initialTimeWithoutConnection.put(id, clock);
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
		initialWithoutVmTime.put(id, clock);
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
		initialTimeDelayAfterNewConnection.put(id, clock);
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

	public void initialiseLatencyCounter(int mobileDeviceId) {
		myCount.put(mobileDeviceId, 0);
	}

	/** Discards unfinished intervals when a migration is aborted or completed. */
	public void discardOpenIntervals(int mobileDeviceId) {
		initialWithoutVmTime.remove(mobileDeviceId);
		initialTimeDelayAfterNewConnection.remove(mobileDeviceId);
		initialTimeWithoutConnection.remove(mobileDeviceId);
	}

	public void discardWithoutVmInterval(int mobileDeviceId) {
		initialWithoutVmTime.remove(mobileDeviceId);
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

	public int getTotalMigrations() {
		return totalMigrations;
	}

	public void setTotalMigrations(int totalMigrations) {
		this.totalMigrations = totalMigrations;
	}

	public static void setInstance(MyStatistics instance) {
		MyStatistics.instance = instance;
	}

	public Map<Integer, File> getFileMap() {
		return Collections.unmodifiableMap(fileMap);
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

	public int getMyCountTuple() {
		return myCountTuple;
	}

	public void setMyCountTuple(int myCountTuple) {
		this.myCountTuple = myCountTuple;
	}

	public Map<Integer, Double> getWithoutConnectionTime() {
		return Collections.unmodifiableMap(withoutConnectionTime);
	}

	public void setWithoutConnectionTime(Map<Integer, Double> withoutConnectionTime) {
		this.withoutConnectionTime = copyMap(withoutConnectionTime,
			"Without-connection time map");
	}

	public Map<Integer, Double> getInitialTimeWithoutConnection() {
		return Collections.unmodifiableMap(initialTimeWithoutConnection);
	}

	public void setInitialTimeWithoutConnection(Map<Integer, Double> initialTimeWithoutConnection) {
		this.initialTimeWithoutConnection = copyMap(initialTimeWithoutConnection,
			"Initial without-connection time map");
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
		return Collections.unmodifiableMap(powerHistory);
	}

	public void setPowerHistory(Map<Integer, Double> powerHistory) {
		this.powerHistory = copyMap(powerHistory, "Power history map");
	}

	public Map<Integer, Double> getEnergyHistory() {
		return Collections.unmodifiableMap(energyHistory);
	}

	public void setEnergyHistory(Map<Integer, Double> energyHistory) {
		this.energyHistory = copyMap(energyHistory, "Energy history map");
	}

	public Map<Integer, Double> getMaxWithoutConnectionTime() {
		return Collections.unmodifiableMap(maxWithoutConnectionTime);
	}

	public void setMaxWithoutConnectionTime(
		Map<Integer, Double> maxWithoutConnectionTime) {
		this.maxWithoutConnectionTime = copyMap(maxWithoutConnectionTime,
			"Maximum without-connection time map");
	}

	public Map<Integer, Double> getInitialTimeDelayAfterNewConnection() {
		return Collections.unmodifiableMap(initialTimeDelayAfterNewConnection);
	}

	public void setInitialTimeDelayAfterNewConnection(
		Map<Integer, Double> initialTimeDelayAfterNewConnection) {
		this.initialTimeDelayAfterNewConnection = copyMap(
			initialTimeDelayAfterNewConnection,
			"Initial post-connection delay map");
	}

	public Map<Integer, Double> getDelayAfterNewConnection() {
		return Collections.unmodifiableMap(DelayAfterNewConnection);
	}

	public void setDelayAfterNewConnection(
		Map<Integer, Double> delayAfterNewConnection) {
		DelayAfterNewConnection = copyMap(delayAfterNewConnection,
			"Post-connection delay map");
	}

	public Map<Integer, Double> getMaxDelayAfterNewConnection() {
		return Collections.unmodifiableMap(maxDelayAfterNewConnection);
	}

	public void setMaxDelayAfterNewConnection(
		Map<Integer, Double> maxDelayAfterNewConnection) {
		this.maxDelayAfterNewConnection = copyMap(maxDelayAfterNewConnection,
			"Maximum post-connection delay map");
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
		return Collections.unmodifiableMap(withoutVmTime);
	}

	public void setWithoutVmTime(Map<Integer, Double> withoutVmTime) {
		this.withoutVmTime = copyMap(withoutVmTime,
			"Without-VM time map");
	}

	public Map<Integer, Double> getMaxWithoutVmTime() {
		return Collections.unmodifiableMap(maxWithoutVmTime);
	}

	public void setMaxWithoutVmTime(Map<Integer, Double> maxWithoutVmTime) {
		this.maxWithoutVmTime = copyMap(maxWithoutVmTime,
			"Maximum without-VM time map");
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
		return Collections.unmodifiableMap(initialWithoutVmTime);
	}

	public void setInitialWithoutVmTime(Map<Integer, Double> initialWithoutVmTime) {
		this.initialWithoutVmTime = copyMap(initialWithoutVmTime,
			"Initial without-VM time map");
	}

	public Map<Integer, Double> getMigrationTime() {
		return Collections.unmodifiableMap(migrationTime);
	}

	public void setMigrationTime(Map<Integer, Double> migrationTime) {
		this.migrationTime = copyMap(migrationTime, "Migration time map");
	}

	public Map<Integer, Double> getMaxMigrationTime() {
		return Collections.unmodifiableMap(maxMigrationTime);
	}

	public void setMaxMigrationTime(Map<Integer, Double> maxMigrationTime) {
		this.maxMigrationTime = copyMap(maxMigrationTime,
			"Maximum migration time map");
	}

	public double getAverageMigrationTime() {
		return averageMigrationTime;
	}

	public void setAverageMigrationTime(double averageMigrationTime) {
		this.averageMigrationTime = averageMigrationTime;
	}

	public Map<Integer, Double> getDowntime() {
		return Collections.unmodifiableMap(downtime);
	}

	public void setDowntime(Map<Integer, Double> downtime) {
		this.downtime = copyMap(downtime, "Downtime map");
	}

	public Map<Integer, Double> getMaxDowntime() {
		return Collections.unmodifiableMap(maxDowntime);
	}

	public void setMaxDowntime(Map<Integer, Double> maxDowntime) {
		this.maxDowntime = copyMap(maxDowntime, "Maximum downtime map");
	}

	public double getAverageDowntime() {
		return averageDowntime;
	}

	public void setAverageDowntime(double averageDowntime) {
		this.averageDowntime = averageDowntime;
	}

	public Map<Integer, Double> getInitialDowntime() {
		return Collections.unmodifiableMap(initialDowntime);
	}

	public void setInitialDowntime(Map<Integer, Double> initialDowntime) {
		this.initialDowntime = copyMap(initialDowntime,
			"Initial downtime map");
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

	public int getTotalHandoff() {
		return totalHandoff;
	}

	public void setTotalHandoff(int totalHandoff) {
		this.totalHandoff = totalHandoff;
	}

	public void setMyCount(Map<Integer, Integer> myCount) {
		this.myCount = copyMap(myCount, "Latency counter map");
	}

	public Map<Integer, Integer> getMyCount() {
		return Collections.unmodifiableMap(myCount);
	}

	private static <K, V> Map<K, V> copyMap(Map<K, V> values,
		String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new HashMap<K, V>(values);
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
