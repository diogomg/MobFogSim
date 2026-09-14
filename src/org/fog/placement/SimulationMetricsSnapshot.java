package org.fog.placement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;

/**
 * Immutable end-of-run view used by every mobile-simulation result formatter.
 * Empty samples have a mean, maximum, and rate of {@code 0.0}.
 */
public final class SimulationMetricsSnapshot {

	private static final double BYTES_PER_MEBIBYTE = 1024.0 * 1024.0;

	/** One device's final power and energy readings. */
	public static final class DeviceEnergy {
		private final int entityId;
		private final String name;
		private final double power;
		private final double energy;

		private DeviceEnergy(FogDevice device) {
			this.entityId = device.getId();
			this.name = device.getName();
			this.power = currentPower(device);
			this.energy = finiteOrZero(device.getEnergyConsumption());
		}

		public int getEntityId() {
			return entityId;
		}

		public String getName() {
			return name;
		}

		public double getPower() {
			return power;
		}

		public double getEnergy() {
			return energy;
		}
	}

	/** Final timing values for one registered application loop. */
	public static final class LoopTiming {
		private final int loopId;
		private final double average;
		private final double maximum;

		private LoopTiming(int loopId, double average, double maximum) {
			this.loopId = loopId;
			this.average = average;
			this.maximum = maximum;
		}

		public int getLoopId() {
			return loopId;
		}

		public double getAverage() {
			return average;
		}

		public double getMaximum() {
			return maximum;
		}
	}

	/** Latest and maximum per-user observations plus their all-sample mean. */
	public static final class MetricSeries {
		private final Map<Integer, Double> latestByUserId;
		private final Map<Integer, Double> maximumByUserId;
		private final double average;
		private final double maximum;

		private MetricSeries(Map<Integer, Double> latestByUserId,
			Map<Integer, Double> maximumByUserId, double average) {
			this.latestByUserId = immutableDoubleMap(latestByUserId);
			TreeMap<Integer, Double> completeMaxima = sanitisedDoubleMap(
				maximumByUserId);
			for (Map.Entry<Integer, Double> entry : latestByUserId.entrySet()) {
				if (!completeMaxima.containsKey(entry.getKey())) {
					completeMaxima.put(entry.getKey(),
						valueOrZero(entry.getValue()));
				}
			}
			this.maximumByUserId = Collections.unmodifiableMap(completeMaxima);
			this.average = finiteOrZero(average);
			this.maximum = safeMaximum(completeMaxima.values());
		}

		public Map<Integer, Double> getLatestByUserId() {
			return latestByUserId;
		}

		public Map<Integer, Double> getMaximumByUserId() {
			return maximumByUserId;
		}

		public double getMaximumForUser(int userId) {
			Double value = maximumByUserId.get(userId);
			return value == null ? 0.0 : value;
		}

		public double getAverage() {
			return average;
		}

		public double getMaximum() {
			return maximum;
		}
	}

	/** Immutable counters and user timing series from {@link MyStatistics}. */
	public static final class Statistics {
		private final int seed;
		private final String outputLabel;
		private final int totalMigrations;
		private final int totalHandoffs;
		private final long lostTuples;
		private final long totalTuples;
		private final double lostTuplePercentage;
		private final MetricSeries withoutConnection;
		private final MetricSeries withoutVm;
		private final MetricSeries delayAfterConnection;
		private final MetricSeries migrationTime;
		private final MetricSeries downtime;

		private Statistics(MyStatistics statistics) {
			this.seed = statistics.getSeed();
			this.outputLabel = statistics.getToPrint();
			this.totalMigrations = statistics.getTotalMigrations();
			this.totalHandoffs = statistics.getTotalHandoff();
			this.lostTuples = statistics.getMyCountLostTuple();
			this.totalTuples = statistics.getMyCountTotalTuple();
			this.lostTuplePercentage = totalTuples == 0L ? 0.0
				: (double) lostTuples / totalTuples * 100.0;
			this.withoutConnection = new MetricSeries(
				statistics.getWithoutConnectionTime(),
				statistics.getMaxWithoutConnectionTime(),
				statistics.getAverageWithoutConnection());
			this.withoutVm = new MetricSeries(statistics.getWithoutVmTime(),
				statistics.getMaxWithoutVmTime(),
				statistics.getAverageWithoutVmTime());
			this.delayAfterConnection = new MetricSeries(
				statistics.getDelayAfterNewConnection(),
				statistics.getMaxDelayAfterNewConnection(),
				statistics.getAverageDelayAfterNewConnection());
			this.migrationTime = new MetricSeries(statistics.getMigrationTime(),
				statistics.getMaxMigrationTime(),
				statistics.getAverageMigrationTime());
			this.downtime = new MetricSeries(statistics.getDowntime(),
				statistics.getMaxDowntime(), statistics.getAverageDowntime());
		}

		public int getSeed() {
			return seed;
		}

		public String getOutputLabel() {
			return outputLabel == null ? "unspecified" : outputLabel;
		}

		public int getTotalMigrations() {
			return totalMigrations;
		}

		public int getTotalHandoffs() {
			return totalHandoffs;
		}

		public long getLostTuples() {
			return lostTuples;
		}

		public long getTotalTuples() {
			return totalTuples;
		}

		public double getLostTuplePercentage() {
			return lostTuplePercentage;
		}

		public MetricSeries getWithoutConnection() {
			return withoutConnection;
		}

		public MetricSeries getWithoutVm() {
			return withoutVm;
		}

		public MetricSeries getDelayAfterConnection() {
			return delayAfterConnection;
		}

		public MetricSeries getMigrationTime() {
			return migrationTime;
		}

		public MetricSeries getDowntime() {
			return downtime;
		}
	}

	private final long executionTimeMillis;
	private final double simulationTimeMillis;
	private final List<DeviceEnergy> cloudlets;
	private final List<DeviceEnergy> accessPoints;
	private final List<DeviceEnergy> mobileDevices;
	private final double averageCloudletEnergy;
	private final double averageAccessPointEnergy;
	private final Map<Integer, Double> mobilePowerHistory;
	private final Map<Integer, Double> mobileEnergyHistory;
	private final Map<Integer, LoopTiming> loopTimings;
	private final double averageLoopDelay;
	private final double averageMaximumLoopDelay;
	private final Map<String, Double> tupleCpuTimes;
	private final double tupleUsageByteMilliseconds;
	private final double migrationUsageByteMilliseconds;
	private final double totalUsageByteMilliseconds;
	private final double migrationTransferredBytes;
	private final long sliceReconfigurationCount;
	private final double sliceOutageSeconds;
	private final Map<Integer, Double> receivedBandwidthBySlice;
	private final int maximumWirelessQueueSize;
	private final long queuedWirelessTransferCount;
	private final long maximumQueuedWirelessTransferCount;
	private final int maximumWirelessQueueDepth;
	private final long droppedWirelessTupleCount;
	private final Statistics statistics;

	private SimulationMetricsSnapshot(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, List<MobileDevice> mobileDevices,
		MyStatistics statistics, TimeKeeper timeKeeper, double simulationTimeMillis,
		long executionTimeMillis) {
		this.executionTimeMillis = Math.max(0L, executionTimeMillis);
		this.simulationTimeMillis = Math.max(0.0,
			finiteOrZero(simulationTimeMillis));
		this.cloudlets = deviceEnergy(serverCloudlets);
		this.accessPoints = deviceEnergy(accessPoints);
		this.mobileDevices = deviceEnergy(mobileDevices);
		this.averageCloudletEnergy = averageEnergy(this.cloudlets);
		this.averageAccessPointEnergy = averageEnergy(this.accessPoints);
		this.mobilePowerHistory = immutableDoubleMap(statistics.getPowerHistory());
		this.mobileEnergyHistory = immutableDoubleMap(statistics.getEnergyHistory());
		this.loopTimings = loopTimings(timeKeeper);
		this.averageLoopDelay = safeMean(
			timeKeeper.getLoopIdToCurrentAverage().values());
		this.averageMaximumLoopDelay = safeMean(
			timeKeeper.getMaxLoopExecutionTime().values());
		this.tupleCpuTimes = immutableStringDoubleMap(
			timeKeeper.getTupleTypeToAverageCpuTime());
		this.tupleUsageByteMilliseconds = finiteOrZero(
			NetworkUsageMonitor.getTupleUsageByteMilliseconds());
		this.migrationUsageByteMilliseconds = finiteOrZero(
			NetworkUsageMonitor.getMigrationUsageByteMilliseconds());
		this.totalUsageByteMilliseconds = finiteOrZero(
			NetworkUsageMonitor.getTotalUsageByteMilliseconds());
		this.migrationTransferredBytes = finiteOrZero(
			NetworkUsageMonitor.getMigrationTransferredBytes());
		this.sliceReconfigurationCount = NetworkSlicing.getReconfigurationCount();
		this.sliceOutageSeconds = finiteOrZero(
			NetworkSlicing.getSliceOutageSeconds());
		this.receivedBandwidthBySlice = indexedDoubleMap(
			NetworkSlicing.getReceivedBandwidthBySlice());
		this.maximumWirelessQueueSize =
			NetworkSlicing.getMaximumWirelessQueueSize();
		this.queuedWirelessTransferCount =
			NetworkSlicing.getQueuedWirelessTransferCount();
		this.maximumQueuedWirelessTransferCount =
			NetworkSlicing.getMaximumQueuedWirelessTransferCount();
		this.maximumWirelessQueueDepth =
			NetworkSlicing.getMaximumWirelessQueueDepth();
		this.droppedWirelessTupleCount =
			NetworkSlicing.getDroppedWirelessTupleCount();
		this.statistics = new Statistics(statistics);
	}

	public static SimulationMetricsSnapshot capture(
		List<FogDevice> serverCloudlets, List<ApDevice> accessPoints,
		List<MobileDevice> mobileDevices, MyStatistics statistics,
		TimeKeeper timeKeeper, double simulationTimeMillis,
		long executionTimeMillis) {
		if (serverCloudlets == null || accessPoints == null || mobileDevices == null
			|| statistics == null || timeKeeper == null) {
			throw new IllegalArgumentException(
				"Metric snapshot inputs cannot be null");
		}
		return new SimulationMetricsSnapshot(serverCloudlets, accessPoints,
			mobileDevices, statistics, timeKeeper, simulationTimeMillis,
			executionTimeMillis);
	}

	public long getExecutionTimeMillis() {
		return executionTimeMillis;
	}

	public double getSimulationTimeMillis() {
		return simulationTimeMillis;
	}

	public List<DeviceEnergy> getCloudlets() {
		return cloudlets;
	}

	public List<DeviceEnergy> getAccessPoints() {
		return accessPoints;
	}

	public List<DeviceEnergy> getMobileDevices() {
		return mobileDevices;
	}

	public double getAverageCloudletEnergy() {
		return averageCloudletEnergy;
	}

	public double getTotalCloudletEnergy() {
		return totalEnergy(cloudlets);
	}

	public double getAverageAccessPointEnergy() {
		return averageAccessPointEnergy;
	}

	public double getTotalAccessPointEnergy() {
		return totalEnergy(accessPoints);
	}

	public Map<Integer, Double> getMobilePowerHistory() {
		return mobilePowerHistory;
	}

	public Map<Integer, Double> getMobileEnergyHistory() {
		return mobileEnergyHistory;
	}

	public Map<Integer, LoopTiming> getLoopTimings() {
		return loopTimings;
	}

	public double getAverageLoopDelay() {
		return averageLoopDelay;
	}

	public double getAverageMaximumLoopDelay() {
		return averageMaximumLoopDelay;
	}

	public Map<String, Double> getTupleCpuTimes() {
		return tupleCpuTimes;
	}

	public double getTupleUsageByteMilliseconds() {
		return tupleUsageByteMilliseconds;
	}

	public double getMigrationUsageByteMilliseconds() {
		return migrationUsageByteMilliseconds;
	}

	public double getTotalUsageByteMilliseconds() {
		return totalUsageByteMilliseconds;
	}

	public double getMigrationTransferredBytes() {
		return migrationTransferredBytes;
	}

	public double getMigrationTransferredMebibytes() {
		return migrationTransferredBytes / BYTES_PER_MEBIBYTE;
	}

	public double getMeanMigrationUsageByteMilliseconds() {
		return statistics.totalMigrations == 0 ? 0.0
			: migrationUsageByteMilliseconds / statistics.totalMigrations;
	}

	public long getSliceReconfigurationCount() {
		return sliceReconfigurationCount;
	}

	public double getSliceOutageSeconds() {
		return sliceOutageSeconds;
	}

	public int getMaximumWirelessQueueSize() {
		return maximumWirelessQueueSize;
	}

	public long getQueuedWirelessTransferCount() {
		return queuedWirelessTransferCount;
	}

	public long getMaximumQueuedWirelessTransferCount() {
		return maximumQueuedWirelessTransferCount;
	}

	public int getMaximumWirelessQueueDepth() {
		return maximumWirelessQueueDepth;
	}

	public long getDroppedWirelessTupleCount() {
		return droppedWirelessTupleCount;
	}

	/** Per-slice cumulative allocation sampled at reconfigurations, in bit/s. */
	public Map<Integer, Double> getReceivedBandwidthBySlice() {
		return receivedBandwidthBySlice;
	}

	public double perSimulationMillisecond(double value) {
		return simulationTimeMillis == 0.0 ? 0.0 : value / simulationTimeMillis;
	}

	public Statistics getStatistics() {
		return statistics;
	}

	private static List<DeviceEnergy> deviceEnergy(
		List<? extends FogDevice> devices) {
		List<DeviceEnergy> result = new ArrayList<DeviceEnergy>(devices.size());
		for (FogDevice device : devices) {
			if (device != null) {
				result.add(new DeviceEnergy(device));
			}
		}
		return Collections.unmodifiableList(result);
	}

	private static double currentPower(FogDevice device) {
		if (device.getCharacteristics() == null || device.getHostList().isEmpty()) {
			return 0.0;
		}
		return finiteOrZero(device.getHost().getPower());
	}

	private static double totalEnergy(List<DeviceEnergy> devices) {
		double total = 0.0;
		for (DeviceEnergy device : devices) {
			total += finiteOrZero(device.energy);
		}
		return total;
	}

	private static double averageEnergy(List<DeviceEnergy> devices) {
		return devices.isEmpty() ? 0.0 : totalEnergy(devices) / devices.size();
	}

	private static Map<Integer, LoopTiming> loopTimings(TimeKeeper timeKeeper) {
		Set<Integer> loopIds = new TreeSet<Integer>();
		loopIds.addAll(timeKeeper.getLoopIdToTupleIds().keySet());
		loopIds.addAll(timeKeeper.getLoopIdToCurrentAverage().keySet());
		loopIds.addAll(timeKeeper.getMaxLoopExecutionTime().keySet());
		Map<Integer, LoopTiming> timings = new TreeMap<Integer, LoopTiming>();
		for (Integer loopId : loopIds) {
			timings.put(loopId, new LoopTiming(loopId,
				valueOrZero(timeKeeper.getLoopIdToCurrentAverage().get(loopId)),
				valueOrZero(timeKeeper.getMaxLoopExecutionTime().get(loopId))));
		}
		return Collections.unmodifiableMap(timings);
	}

	private static Map<Integer, Double> immutableDoubleMap(
		Map<Integer, Double> source) {
		return Collections.unmodifiableMap(sanitisedDoubleMap(source));
	}

	private static Map<Integer, Double> indexedDoubleMap(double[] source) {
		Map<Integer, Double> indexed = new TreeMap<Integer, Double>();
		for (int index = 0; index < source.length; index++) {
			indexed.put(index, source[index]);
		}
		return Collections.unmodifiableMap(indexed);
	}

	private static Map<String, Double> immutableStringDoubleMap(
		Map<String, Double> source) {
		Map<String, Double> result = new TreeMap<String, Double>();
		for (Map.Entry<String, Double> entry : source.entrySet()) {
			result.put(entry.getKey(), valueOrZero(entry.getValue()));
		}
		return Collections.unmodifiableMap(result);
	}

	private static TreeMap<Integer, Double> sanitisedDoubleMap(
		Map<Integer, Double> source) {
		TreeMap<Integer, Double> result = new TreeMap<Integer, Double>();
		for (Map.Entry<Integer, Double> entry : source.entrySet()) {
			result.put(entry.getKey(), valueOrZero(entry.getValue()));
		}
		return result;
	}

	private static double safeMean(Iterable<Double> values) {
		double total = 0.0;
		int count = 0;
		for (Double value : values) {
			if (value != null && Double.isFinite(value)) {
				total += value;
				count++;
			}
		}
		return count == 0 ? 0.0 : total / count;
	}

	private static double safeMaximum(Iterable<Double> values) {
		double maximum = 0.0;
		for (Double value : values) {
			if (value != null && Double.isFinite(value)) {
				maximum = Math.max(maximum, value);
			}
		}
		return maximum;
	}

	private static double valueOrZero(Double value) {
		return value == null ? 0.0 : finiteOrZero(value);
	}

	private static double finiteOrZero(double value) {
		return Double.isFinite(value) ? value : 0.0;
	}
}
