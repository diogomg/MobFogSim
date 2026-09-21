package org.fog.placement;

import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppLoop;
import org.fog.application.Application;

/** Formats the console view of one immutable simulation-metrics snapshot. */
public final class SimulationResultsService {

	/** Retains the historical constructor while persistence is context-owned. */
	public SimulationResultsService(RunOutputManager output) {
		if (output == null) {
			throw new IllegalArgumentException("Run output manager cannot be null");
		}
	}

	/** Writes the console view; the run context publishes the atomic report. */
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
		System.out.println("Total SmartThing energy: "
			+ metrics.getTotalMobileEnergy() + " Mean: "
			+ metrics.getAverageMobileEnergy());
		for (Entry<Integer, Double> power
			: metrics.getMobilePowerHistory().entrySet()) {
			System.out.println("SmartThing" + power.getKey() + ": Power = "
				+ power.getValue());
		}
		for (Entry<Integer, Double> energy
			: metrics.getMobileEnergyHistory().entrySet()) {
			System.out.println("SmartThing" + energy.getKey() + ": Energy Consumed = "
				+ energy.getValue());
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
		}
		System.out.println("=========================================");
		System.out.println("TUPLE CPU EXECUTION DELAY");
		System.out.println("=========================================");
		for (Entry<String, Double> tupleCpuTime
			: metrics.getTupleCpuTimes().entrySet()) {
			System.out.println(tupleCpuTime.getKey() + " ---> "
				+ tupleCpuTime.getValue());
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
		System.out.println("Tuple bytes transferred = "
			+ metrics.getTupleTransferredBytes());
		System.out.println("Tuple queue duration (ms) = "
			+ metrics.getTupleQueueDurationMilliseconds());
		System.out.println("Tuple serialization/contention duration (ms) = "
			+ metrics.getTupleTransferDurationMilliseconds());
		System.out.println("Tuple propagation duration (ms) = "
			+ metrics.getTuplePropagationDurationMilliseconds());
		System.out.println("Tuple queue usage (byte-ms) = "
			+ metrics.getTupleQueueUsageByteMilliseconds());
		System.out.println("Tuple serialization/contention usage (byte-ms) = "
			+ metrics.getTupleTransferUsageByteMilliseconds());
		System.out.println("Tuple propagation usage (byte-ms) = "
			+ metrics.getTuplePropagationUsageByteMilliseconds());
		System.out.println("VM data transferred in migration (MiB) = "
			+ metrics.getMigrationTransferredMebibytes());
		System.out.println("Tuple network usage (byte-ms) = "
			+ metrics.getTupleUsageByteMilliseconds());
		System.out.println("Migration network usage (total byte-ms) = "
			+ metrics.getMigrationUsageByteMilliseconds());
		System.out.println("Migration data-transfer duration (ms) = "
			+ metrics.getMigrationTransferDurationMilliseconds());
		System.out.println("Migration network usage (mean byte-ms) = "
			+ metrics.getMeanMigrationUsageByteMilliseconds());
		System.out.println("Total network usage (byte-ms) = "
			+ metrics.getTotalUsageByteMilliseconds());
	}

	private void writeSliceReconfiguration(SimulationMetricsSnapshot metrics) {
		System.out.println("=========================================");
		System.out.println("==========SLICE RECONFIGURATION==========");
		System.out.println("=========================================");
		System.out.println("Number of slice reconfigurations: "
			+ metrics.getSliceReconfigurationCount());
		System.out.println("Slice outage (seconds): "
			+ metrics.getSliceOutageSeconds());
		for (Entry<Integer, Double> slice
			: metrics.getReceivedBandwidthBySlice().entrySet()) {
			System.out.println("Slice " + slice.getKey()
				+ " received bandwidth sum (bit/s): " + slice.getValue());
		}
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
	}

	private void writeMigration(SimulationMetricsSnapshot metrics) {
		SimulationMetricsSnapshot.Statistics statistics = metrics.getStatistics();
		System.out.println("=========================================");
		System.out.println("==============MIGRATIONS=================");
		System.out.println("=========================================");
		System.out.println("Total of migrations: " + statistics.getTotalMigrations());
		System.out.println("Total of handoff: " + statistics.getTotalHandoffs());
		writeObservation("without connection", statistics.getWithoutConnection());
		writeObservation("without Vm", statistics.getWithoutVm());
		writeObservation("delay after new Connection",
			statistics.getDelayAfterConnection());
		writeObservation("Time of Migrations", statistics.getMigrationTime());
		System.out.println("Highest Time of Migrations: "
			+ statistics.getMigrationTime().getMaximum());
		writeObservation("Downtime", statistics.getDowntime());
		System.out.println("Max Downtime: " + statistics.getDowntime().getMaximum());
		System.out.println("Tuple lost: " + statistics.getLostTuplePercentage() + "%");
		System.out.println("Tuple lost: " + statistics.getLostTuples());
		System.out.println("Total tuple: " + statistics.getTotalTuples());
	}

	private void writeObservation(String label,
		SimulationMetricsSnapshot.MetricSeries observation) {
		System.out.println("---" + label + "---");
		for (Entry<Integer, Double> value
			: observation.getLatestByUserId().entrySet()) {
			System.out.println("SmartThing" + value.getKey() + ": " + value.getValue()
				+ " - Max: " + observation.getMaximumForUser(value.getKey()));
		}
		System.out.println("Average of " + label + ": "
			+ observation.getAverage());
	}
}
