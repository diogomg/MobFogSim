package org.fog.placement;

import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.vmmobile.SimulationEventSink;

/** Formats the console view of one immutable simulation-metrics snapshot. */
public final class SimulationResultsService {
	private final SimulationEventSink events;

	/** Retains the historical constructor while persistence is context-owned. */
	public SimulationResultsService(RunOutputManager output) {
		if (output == null) {
			throw new IllegalArgumentException("Run output manager cannot be null");
		}
		this.events = SimulationEventSink.current();
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
		line("=========================================");
		line("CLOUDLETS ENERGY CONSUMPTION");
		line("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy cloudlet : metrics.getCloudlets()) {
			line(cloudlet.getName() + ": Power = " + cloudlet.getPower());
			line(cloudlet.getName() + ": Energy Consumed = "
				+ cloudlet.getEnergy());
		}
		line("Total cloudlet energy: " + metrics.getTotalCloudletEnergy()
			+ " Mean: " + metrics.getAverageCloudletEnergy());
		line("=========================================");
		line("AP DEVICES ENERGY CONSUMPTION");
		line("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy accessPoint
			: metrics.getAccessPoints()) {
			line(accessPoint.getName() + ": Energy Consumed = "
				+ accessPoint.getEnergy());
		}
		line("Total AP energy: " + metrics.getTotalAccessPointEnergy()
			+ " Mean: " + metrics.getAverageAccessPointEnergy());
		line("=========================================");
		line("SMARTTHINGS ENERGY CONSUMPTION");
		line("=========================================");
		for (SimulationMetricsSnapshot.DeviceEnergy mobileDevice
			: metrics.getMobileDevices()) {
			line(mobileDevice.getName() + ": Power = "
				+ mobileDevice.getPower());
			line(mobileDevice.getName() + ": Energy Consumed = "
				+ mobileDevice.getEnergy());
		}
		line("Total SmartThing energy: "
			+ metrics.getTotalMobileEnergy() + " Mean: "
			+ metrics.getAverageMobileEnergy());
		for (Entry<Integer, Double> power
			: metrics.getMobilePowerHistory().entrySet()) {
			line("SmartThing" + power.getKey() + ": Power = "
				+ power.getValue());
		}
		for (Entry<Integer, Double> energy
			: metrics.getMobileEnergyHistory().entrySet()) {
			line("SmartThing" + energy.getKey() + ": Energy Consumed = "
				+ energy.getValue());
		}
	}

	private void writeTiming(SimulationMetricsSnapshot metrics,
		Map<String, Application> applications) {
		line("=========================================");
		line("============== RESULTS ==================");
		line("=========================================");
		line("EXECUTION TIME : " + metrics.getExecutionTimeMillis());
		line("=========================================");
		line("APPLICATION LOOP DELAYS");
		line("=========================================");
		for (SimulationMetricsSnapshot.LoopTiming loop
			: metrics.getLoopTimings().values()) {
			line(loopLabel(loop.getLoopId(), applications) + " ---> "
				+ loop.getAverage() + " MaxExecutionTime: " + loop.getMaximum());
		}
		line("=========================================");
		line("TUPLE CPU EXECUTION DELAY");
		line("=========================================");
		for (Entry<String, Double> tupleCpuTime
			: metrics.getTupleCpuTimes().entrySet()) {
			line(tupleCpuTime.getKey() + " ---> "
				+ tupleCpuTime.getValue());
		}
		line("=========================================");
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
		line("=========================================");
		line("=============NETWORK USAGE===============");
		line("=========================================");
		line("Tuple bytes transferred = "
			+ metrics.getTupleTransferredBytes());
		line("Tuple queue duration (ms) = "
			+ metrics.getTupleQueueDurationMilliseconds());
		line("Tuple serialization/contention duration (ms) = "
			+ metrics.getTupleTransferDurationMilliseconds());
		line("Tuple propagation duration (ms) = "
			+ metrics.getTuplePropagationDurationMilliseconds());
		line("Tuple queue usage (byte-ms) = "
			+ metrics.getTupleQueueUsageByteMilliseconds());
		line("Tuple serialization/contention usage (byte-ms) = "
			+ metrics.getTupleTransferUsageByteMilliseconds());
		line("Tuple propagation usage (byte-ms) = "
			+ metrics.getTuplePropagationUsageByteMilliseconds());
		line("VM data transferred in migration (MiB) = "
			+ metrics.getMigrationTransferredMebibytes());
		line("Tuple network usage (byte-ms) = "
			+ metrics.getTupleUsageByteMilliseconds());
		line("Migration network usage (total byte-ms) = "
			+ metrics.getMigrationUsageByteMilliseconds());
		line("Migration data-transfer duration (ms) = "
			+ metrics.getMigrationTransferDurationMilliseconds());
		line("Migration network usage (mean byte-ms) = "
			+ metrics.getMeanMigrationUsageByteMilliseconds());
		line("Total network usage (byte-ms) = "
			+ metrics.getTotalUsageByteMilliseconds());
	}

	private void writeSliceReconfiguration(SimulationMetricsSnapshot metrics) {
		line("=========================================");
		line("==========SLICE RECONFIGURATION==========");
		line("=========================================");
		line("Number of slice reconfigurations: "
			+ metrics.getSliceReconfigurationCount());
		line("Slice outage (seconds): "
			+ metrics.getSliceOutageSeconds());
		for (Entry<Integer, Double> slice
			: metrics.getReceivedBandwidthBySlice().entrySet()) {
			line("Slice " + slice.getKey()
				+ " received bandwidth sum (bit/s): " + slice.getValue());
		}
		line("Wireless queue limit per direction: "
			+ metrics.getMaximumWirelessQueueSize());
		line("Wireless tuples queued at simulation end: "
			+ metrics.getQueuedWirelessTransferCount());
		line("Maximum wireless tuples queued across all directions: "
			+ metrics.getMaximumQueuedWirelessTransferCount());
		line("Maximum wireless queue depth for one direction: "
			+ metrics.getMaximumWirelessQueueDepth());
		line("Wireless tuples dropped at queue limit: "
			+ metrics.getDroppedWirelessTupleCount());
	}

	private void writeMigration(SimulationMetricsSnapshot metrics) {
		SimulationMetricsSnapshot.Statistics statistics = metrics.getStatistics();
		line("=========================================");
		line("==============MIGRATIONS=================");
		line("=========================================");
		line("Total of migrations: " + statistics.getTotalMigrations());
		line("Total of handoff: " + statistics.getTotalHandoffs());
		writeObservation("without connection", statistics.getWithoutConnection());
		writeObservation("without Vm", statistics.getWithoutVm());
		writeObservation("delay after new Connection",
			statistics.getDelayAfterConnection());
		writeObservation("Time of Migrations", statistics.getMigrationTime());
		line("Highest Time of Migrations: "
			+ statistics.getMigrationTime().getMaximum());
		writeObservation("Downtime", statistics.getDowntime());
		line("Max Downtime: " + statistics.getDowntime().getMaximum());
		line("Tuple lost: " + statistics.getLostTuplePercentage() + "%");
		line("Tuple lost: " + statistics.getLostTuples());
		line("Total tuple: " + statistics.getTotalTuples());
	}

	private void writeObservation(String label,
		SimulationMetricsSnapshot.MetricSeries observation) {
		line("---" + label + "---");
		for (Entry<Integer, Double> value
			: observation.getLatestByUserId().entrySet()) {
			line("SmartThing" + value.getKey() + ": " + value.getValue()
				+ " - Max: " + observation.getMaximumForUser(value.getKey()));
		}
		line("Average of " + label + ": "
			+ observation.getAverage());
	}

	private void line(String value) {
		events.detailLine(() -> value);
	}
}
