package org.fog.placement;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppLoop;
import org.fog.application.Application;

/** Formats and persists one immutable simulation-metrics snapshot. */
public final class SimulationResultsService {
	private final RunOutputManager output;

	public SimulationResultsService(RunOutputManager output) {
		if (output == null) {
			throw new IllegalArgumentException("Run output manager cannot be null");
		}
		this.output = output;
	}

	/** Writes all console and file result views from the same snapshot. */
	public void write(SimulationMetricsSnapshot metrics,
		Map<String, Application> applications) {
		if (metrics == null) {
			throw new IllegalArgumentException("Simulation metrics cannot be null");
		}
		if (applications == null) {
			throw new IllegalArgumentException("Application map cannot be null");
		}
		writeTiming(metrics, applications);
		writePower(metrics);
		writeNetworkUsage(metrics);
		writeSliceReconfiguration(metrics);
		writeMigration(metrics);
	}

	/** Appends one value to a summary output file. */
	public void appendSummary(String value, String filename) {
		try (PrintWriter writer = output.newSummaryPrintWriter(filename, true)) {
			writer.println(value);
		} catch (IOException error) {
			throw new IllegalStateException(
				"Could not write simulation result " + filename, error);
		}
	}

	private void writePower(SimulationMetricsSnapshot metrics) {
		System.out.println("=========================================");
		System.out.println("CLOUDLETS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy cloudlet : metrics.getCloudlets()) {
			System.out.println(cloudlet.getName() + ": Power = " + cloudlet.getPower());
			System.out.println(cloudlet.getName() + ": Energy Consumed = "
				+ cloudlet.getEnergy());
		}
		System.out.println("Total cloudlet energy: " + metrics.getTotalCloudletEnergy()
			+ " Mean: " + metrics.getAverageCloudletEnergy());
		appendSummary(String.valueOf(metrics.getAverageCloudletEnergy()),
			"averageEnergyHistoryDevice.txt");
		appendSummary(metrics.getTotalCloudletEnergy() + "\t"
			+ metrics.getAverageCloudletEnergy(), "results.txt");
		System.out.println("=========================================");
		System.out.println("AP DEVICES ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy accessPoint
			: metrics.getAccessPoints()) {
			System.out.println(accessPoint.getName() + ": Energy Consumed = "
				+ accessPoint.getEnergy());
		}
		System.out.println("Total AP energy: " + metrics.getTotalAccessPointEnergy()
			+ " Mean: " + metrics.getAverageAccessPointEnergy());
		System.out.println("=========================================");
		System.out.println("SMARTTHINGS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy mobileDevice
			: metrics.getMobileDevices()) {
			System.out.println(mobileDevice.getName() + ": Power = "
				+ mobileDevice.getPower());
			System.out.println(mobileDevice.getName() + ": Energy Consumed = "
				+ mobileDevice.getEnergy());
		}
		for (Entry<Integer, Double> power : metrics.getMobilePowerHistory().entrySet()) {
			System.out.println("SmartThing" + power.getKey() + ": Power = "
				+ power.getValue());
		}
		for (Entry<Integer, Double> energy : metrics.getMobileEnergyHistory().entrySet()) {
			System.out.println("SmartThing" + energy.getKey() + ": Energy Consumed = "
				+ energy.getValue());
			appendSummary(String.valueOf(energy.getValue()), "results.txt");
		}
	}

	private void writeTiming(SimulationMetricsSnapshot metrics,
		Map<String, Application> applications) {
		System.out.println("=========================================");
		System.out.println("============== RESULTS ==================");
		System.out.println("=========================================");
		System.out.println("EXECUTION TIME : " + metrics.getExecutionTimeMillis());
		System.out.println("=========================================");
		System.out.println("APPLICATION LOOP DELAYS");
		System.out.println("=========================================");
		for (SimulationMetricsSnapshot.LoopTiming loop
			: metrics.getLoopTimings().values()) {
			System.out.println(loopLabel(loop.getLoopId(), applications) + " ---> "
				+ loop.getAverage() + " MaxExecutionTime: " + loop.getMaximum());
			appendSummary(String.valueOf(loop.getAverage()), "results.txt");
			appendSummary(String.valueOf(loop.getMaximum()), "results.txt");
		}
		appendSummary(String.valueOf(metrics.getAverageLoopDelay()),
			"averageLoopIdToCurrentAverage.txt");
		appendSummary(String.valueOf(metrics.getAverageMaximumLoopDelay()),
			"averageMaxLoopExecutionTime.txt");
		System.out.println("=========================================");
		System.out.println("TUPLE CPU EXECUTION DELAY");
		System.out.println("=========================================");
		for (Entry<String, Double> tupleCpuTime
			: metrics.getTupleCpuTimes().entrySet()) {
			System.out.println(tupleCpuTime.getKey() + " ---> " + tupleCpuTime.getValue());
		}
		System.out.println("=========================================");
	}

	static String loopLabel(int loopId, Map<String, Application> applications) {
		for (Application application : applications.values()) {
			for (AppLoop loop : application.getLoops()) {
				if (loop.getLoopId() == loopId) {
					return loop.getModules().toString();
				}
			}
		}
		return "Loop " + loopId;
	}

	private void writeNetworkUsage(SimulationMetricsSnapshot metrics) {
		System.out.println("=========================================");
		System.out.println("=============NETWORK USAGE===============");
		System.out.println("=========================================");
		double transferredMebibytes = metrics.getMigrationTransferredMebibytes();
		double deviceNetworkUsage = metrics.getTupleUsageByteMilliseconds();
		System.out.println("VM data transferred in migration (MiB) = "
			+ transferredMebibytes);
		String migrationTransfer = rateLine(metrics, transferredMebibytes);
		appendSummary(migrationTransfer, "results.txt");
		appendSummary(migrationTransfer, "vmsizesended.txt");
		System.out.println("Device network usage (byte-ms) = " + deviceNetworkUsage);
		String deviceUsage = rateLine(metrics, deviceNetworkUsage);
		appendSummary(deviceUsage, "results.txt");
		appendSummary(deviceUsage, "deviceNetworkUsage.txt");
		System.out.println("Migration network usage (total byte-ms) = "
			+ metrics.getMigrationUsageByteMilliseconds());
		System.out.println("Migration network usage (mean byte-ms) = "
			+ metrics.getMeanMigrationUsageByteMilliseconds());
		String migrationUsage = rateLine(metrics,
			metrics.getMigrationUsageByteMilliseconds());
		appendSummary(migrationUsage, "results.txt");
		appendSummary(migrationUsage, "cloudletNetworkUsage.txt");
		System.out.println("Total network usage (byte-ms) = "
			+ metrics.getTotalUsageByteMilliseconds());
		String totalUsage = rateLine(metrics, metrics.getTotalUsageByteMilliseconds());
		appendSummary(totalUsage, "results.txt");
		appendSummary(totalUsage, "totalNetworkUsage.txt");
	}

	private static String rateLine(SimulationMetricsSnapshot metrics, double value) {
		return metrics.perSimulationMillisecond(value) + "\t" + value + "\t"
			+ metrics.getSimulationTimeMillis();
	}

	private void writeSliceReconfiguration(SimulationMetricsSnapshot metrics) {
		System.out.println("=========================================");
		System.out.println("==========SLICE RECONFIGURATION==========");
		System.out.println("=========================================");
		System.out.println("Number of slice reconfigurations: "
			+ metrics.getSliceReconfigurationCount());
		System.out.println("Slice outage (seconds): "
			+ metrics.getSliceOutageSeconds());
		appendSummary(String.valueOf(metrics.getSliceReconfigurationCount()),
			"sliceReconfigurations.txt");
		appendSummary(String.valueOf(metrics.getSliceOutageSeconds()),
			"sliceOutage.txt");
		appendSummary(String.valueOf(metrics.getSliceReconfigurationCount()),
			"results.txt");
		appendSummary(String.valueOf(metrics.getSliceOutageSeconds()), "results.txt");

		StringBuilder receivedBandwidth = new StringBuilder();
		for (Entry<Integer, Double> slice
			: metrics.getReceivedBandwidthBySlice().entrySet()) {
			System.out.println("Slice " + slice.getKey()
				+ " received bandwidth sum (bit/s): " + slice.getValue());
			if (receivedBandwidth.length() > 0) {
				receivedBandwidth.append('\t');
			}
			receivedBandwidth.append(slice.getKey()).append('=')
				.append(slice.getValue());
		}
		appendSummary(receivedBandwidth.toString(), "sliceReceivedBandwidth.txt");
		appendSummary(receivedBandwidth.toString(), "results.txt");
		System.out.println("Wireless queue limit per direction: "
			+ metrics.getMaximumWirelessQueueSize());
		System.out.println("Wireless tuples queued at simulation end: "
			+ metrics.getQueuedWirelessTransferCount());
		System.out.println("Maximum wireless tuples queued across all directions: "
			+ metrics.getMaximumQueuedWirelessTransferCount());
		System.out.println("Maximum wireless queue depth for one direction: "
			+ metrics.getMaximumWirelessQueueDepth());
		System.out.println("Wireless tuples dropped at queue limit: "
			+ metrics.getDroppedWirelessTupleCount());
		appendSummary(metrics.getMaximumWirelessQueueSize() + "\t"
			+ metrics.getQueuedWirelessTransferCount() + "\t"
			+ metrics.getMaximumQueuedWirelessTransferCount() + "\t"
			+ metrics.getMaximumWirelessQueueDepth() + "\t"
			+ metrics.getDroppedWirelessTupleCount(), "wirelessQueue.txt");
	}

	private void writeMigration(SimulationMetricsSnapshot metrics) {
		SimulationMetricsSnapshot.Statistics statistics = metrics.getStatistics();
		System.out.println("=========================================");
		System.out.println("==============MIGRATIONS=================");
		System.out.println("=========================================");
		System.out.println("Total of migrations: " + statistics.getTotalMigrations());
		System.out.println("Total of handoff: " + statistics.getTotalHandoffs());
		System.out.println("Different Cloudlets reached along the user's path: "
			+ statistics.getDistinctCloudletsReached());
		appendSummary(String.valueOf(statistics.getTotalMigrations()), "results.txt");
		appendSummary(String.valueOf(statistics.getTotalHandoffs()), "results.txt");
		appendSummary(String.valueOf(statistics.getTotalMigrations()),
			"totalMigrations.txt");
		appendSummary(String.valueOf(statistics.getDistinctCloudletsReached()),
			"totalMyCountLowestLatency.txt");
		appendSummary(String.valueOf(statistics.getTotalHandoffs()), "totalHandoff.txt");
		writeStatisticsAverages(statistics);
		writeObservation("without connection", statistics.getWithoutConnection());
		appendSummary(String.valueOf(statistics.getWithoutConnection().getAverage()),
			"results.txt");
		writeObservation("without Vm", statistics.getWithoutVm());
		appendSummary(String.valueOf(statistics.getWithoutVm().getAverage()), "results.txt");
		appendSummary(String.valueOf(statistics.getWithoutVm().getAverage()),
			"averageWithoutVmTime.txt");
		writeObservation("delay after new Connection", statistics.getDelayAfterConnection());
		appendSummary(String.valueOf(statistics.getDelayAfterConnection().getAverage()),
			"results.txt");
		appendSummary(String.valueOf(statistics.getDelayAfterConnection().getAverage()),
			"averageDelayAfterNewConnection.txt");
		writeObservation("Time of Migrations", statistics.getMigrationTime());
		appendSummary(String.valueOf(statistics.getMigrationTime().getAverage()),
			"results.txt");
		appendSummary(String.valueOf(statistics.getMigrationTime().getAverage()),
			"averageMigrationTime.txt");
		System.out.println("Highest Time of Migrations: "
			+ statistics.getMigrationTime().getMaximum());
		appendSummary(String.valueOf(statistics.getMigrationTime().getMaximum()),
			"averageMigrationMaxTime.txt");
		writeObservation("Downtime", statistics.getDowntime());
		appendSummary(String.valueOf(statistics.getDowntime().getAverage()), "results.txt");
		appendSummary(String.valueOf(statistics.getDowntime().getAverage()),
			"averageDowntime.txt");
		System.out.println("Max Downtime: " + statistics.getDowntime().getMaximum());
		appendSummary(String.valueOf(statistics.getDowntime().getMaximum()),
			"averageDowntimeMax.txt");
		System.out.println("Tuple lost: " + statistics.getLostTuplePercentage() + "%");
		System.out.println("Tuple lost: " + statistics.getLostTuples());
		System.out.println("Total tuple: " + statistics.getTotalTuples());
		appendSummary(statistics.getLostTuples() + "\t" + statistics.getTotalTuples()
			+ "\t" + statistics.getLostTuplePercentage(), "tupleLoss.txt");
	}

	private void writeObservation(String label,
		SimulationMetricsSnapshot.MetricSeries observation) {
		System.out.println("---" + label + "---");
		for (Entry<Integer, Double> value : observation.getLatestByUserId().entrySet()) {
			System.out.println("SmartThing" + value.getKey() + ": " + value.getValue()
				+ " - Max: " + observation.getMaximumForUser(value.getKey()));
		}
		System.out.println("Average of " + label + ": " + observation.getAverage());
	}

	private void writeStatisticsAverages(
		SimulationMetricsSnapshot.Statistics statistics) {
		String suffix = statistics.getOutputLabel();
		try (PrintWriter withoutConnection = output.newSummaryPrintWriter(
			"averages/withoutConnection_" + suffix, true);
			PrintWriter withoutVm = output.newSummaryPrintWriter(
				"averages/withoutVM_" + suffix, true);
			PrintWriter delayAfterConnection = output.newSummaryPrintWriter(
				"averages/delayAfterConnection_" + suffix, true);
			PrintWriter migrationTime = output.newSummaryPrintWriter(
				"averages/timeOfMigration_" + suffix, true);
			PrintWriter downtime = output.newSummaryPrintWriter(
				"averages/downtime_" + suffix, true);
			PrintWriter all = output.newSummaryPrintWriter(
				"averages/all_" + suffix, true)) {
			int seed = statistics.getSeed();
			withoutConnection.println(statistics.getWithoutConnection().getAverage()
				+ " " + seed);
			withoutVm.println(statistics.getWithoutVm().getAverage() + " " + seed);
			delayAfterConnection.println(statistics.getDelayAfterConnection().getAverage()
				+ " " + seed);
			migrationTime.println(statistics.getMigrationTime().getAverage() + " " + seed);
			downtime.println(statistics.getDowntime().getAverage() + " " + seed);
			all.println(statistics.getWithoutConnection().getAverage() + " "
				+ statistics.getWithoutVm().getAverage() + " "
				+ statistics.getDelayAfterConnection().getAverage() + " "
				+ statistics.getMigrationTime().getAverage() + " "
				+ statistics.getDowntime().getAverage() + " "
				+ statistics.getTotalMigrations() + " "
				+ statistics.getTotalHandoffs() + " " + seed);
		} catch (IOException error) {
			throw new IllegalStateException(
				"Could not write simulation statistics averages", error);
		}
	}
}
