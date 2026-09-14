package org.fog.vmmobile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.fog.placement.SimulationMetricsSnapshot;
import org.fog.placement.SimulationMetricsSnapshot.DeviceEnergy;
import org.fog.placement.SimulationMetricsSnapshot.LoopTiming;
import org.fog.placement.SimulationMetricsSnapshot.MetricSeries;
import org.fog.placement.SimulationMetricsSnapshot.Statistics;

/**
 * Canonical, immutable characterisation of a completed simulation.
 *
 * <p>The snapshot contains deterministic run totals and model results, but
 * deliberately excludes wall-clock execution time, output paths and CloudSim
 * entity IDs. Floating-point values are rounded to nine decimal places so that
 * insignificant platform-level representation differences do not invalidate a
 * semantic golden file.</p>
 */
public final class SimulationSemanticSnapshot {

	public static final int SCHEMA_VERSION = 4;
	private static final int DECIMAL_PLACES = 9;

	private final Map<String, String> values;

	private SimulationSemanticSnapshot(Map<String, String> values) {
		this.values = Collections.unmodifiableMap(
			new TreeMap<String, String>(values));
	}

	/** Captures every stable result field from a completed run. */
	public static SimulationSemanticSnapshot capture(SimulationRunResult run) {
		if (run == null) {
			throw new IllegalArgumentException("Simulation run result cannot be null");
		}
		TreeMap<String, String> values = new TreeMap<String, String>();
		put(values, "schema.version", SCHEMA_VERSION);
		put(values, "entities.cloudsim", run.getCloudSimEntityCount());
		put(values, "identifiers.fog_entities",
			run.getGeneratedFogEntityCount());
		put(values, "identifiers.tuples", run.getGeneratedTupleCount());
		put(values, "identifiers.actual_tuples",
			run.getGeneratedActualTupleCount());
		addMetrics(values, run.getMetrics());
		return new SimulationSemanticSnapshot(values);
	}

	public Map<String, String> getValues() {
		return values;
	}

	/** Returns sorted {@code key=value} records with a final newline. */
	public String toCanonicalText() {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, String> entry : values.entrySet()) {
			text.append(entry.getKey()).append('=').append(entry.getValue())
				.append('\n');
		}
		return text.toString();
	}

	private static void addMetrics(Map<String, String> values,
		SimulationMetricsSnapshot metrics) {
		put(values, "metrics.simulation_time_ms",
			metrics.getSimulationTimeMillis());
		addDevices(values, "energy.cloudlets", metrics.getCloudlets());
		addDevices(values, "energy.access_points", metrics.getAccessPoints());
		addDevices(values, "energy.mobile_devices", metrics.getMobileDevices());
		put(values, "energy.cloudlets.average",
			metrics.getAverageCloudletEnergy());
		put(values, "energy.cloudlets.total", metrics.getTotalCloudletEnergy());
		put(values, "energy.access_points.average",
			metrics.getAverageAccessPointEnergy());
		put(values, "energy.access_points.total",
			metrics.getTotalAccessPointEnergy());
		addDoubleMap(values, "energy.mobile_power_history",
			metrics.getMobilePowerHistory());
		addDoubleMap(values, "energy.mobile_energy_history",
			metrics.getMobileEnergyHistory());
		addLoopTimings(values, metrics.getLoopTimings());
		put(values, "loops.average_delay_ms", metrics.getAverageLoopDelay());
		put(values, "loops.average_maximum_delay_ms",
			metrics.getAverageMaximumLoopDelay());
		addStringDoubleMap(values, "tuple_cpu_times_ms",
			metrics.getTupleCpuTimes());
		put(values, "network.tuple_usage_byte_ms",
			metrics.getTupleUsageByteMilliseconds());
		put(values, "network.migration_usage_byte_ms",
			metrics.getMigrationUsageByteMilliseconds());
		put(values, "network.total_usage_byte_ms",
			metrics.getTotalUsageByteMilliseconds());
		put(values, "network.migration_transferred_bytes",
			metrics.getMigrationTransferredBytes());
		put(values, "network_slicing.reconfigurations",
			metrics.getSliceReconfigurationCount());
		put(values, "network_slicing.outage_seconds",
			metrics.getSliceOutageSeconds());
		addDoubleMap(values,
			"network_slicing.received_bandwidth_bits_per_second_sum",
			metrics.getReceivedBandwidthBySlice());
		put(values, "network_slicing.wireless_queue.limit_per_direction",
			metrics.getMaximumWirelessQueueSize());
		put(values, "network_slicing.wireless_queue.final_total",
			metrics.getQueuedWirelessTransferCount());
		put(values, "network_slicing.wireless_queue.maximum_total",
			metrics.getMaximumQueuedWirelessTransferCount());
		put(values, "network_slicing.wireless_queue.maximum_per_direction",
			metrics.getMaximumWirelessQueueDepth());
		put(values, "network_slicing.wireless_queue.dropped_tuples",
			metrics.getDroppedWirelessTupleCount());
		addStatistics(values, metrics.getStatistics());
	}

	private static void addDevices(Map<String, String> values, String prefix,
		List<DeviceEnergy> devices) {
		TreeMap<String, DeviceGroup> groups = new TreeMap<String, DeviceGroup>();
		for (DeviceEnergy device : devices) {
			String signature = decimal(device.getPower(), prefix + ".power") + '|'
				+ decimal(device.getEnergy(), prefix + ".energy");
			DeviceGroup group = groups.get(signature);
			if (group == null) {
				group = new DeviceGroup(device.getPower(), device.getEnergy());
				groups.put(signature, group);
			}
			group.names.add(device.getName());
		}
		put(values, prefix + ".count", devices.size());
		put(values, prefix + ".groups.count", groups.size());
		int groupIndex = 0;
		for (DeviceGroup group : groups.values()) {
			Collections.sort(group.names);
			String item = prefix + ".groups." + index(groupIndex++);
			put(values, item + ".count", group.names.size());
			put(values, item + ".power", group.power);
			put(values, item + ".energy", group.energy);
			put(values, item + ".member_names_sha256", hashNames(group.names));
			if (group.names.size() <= 8) {
				put(values, item + ".members", join(group.names));
			}
		}
	}

	private static void addLoopTimings(Map<String, String> values,
		Map<Integer, LoopTiming> timings) {
		put(values, "loops.count", timings.size());
		int index = 0;
		for (Map.Entry<Integer, LoopTiming> entry : timings.entrySet()) {
			String item = "loops." + index(index++);
			put(values, item + ".id", entry.getKey().intValue());
			put(values, item + ".average_ms", entry.getValue().getAverage());
			put(values, item + ".maximum_ms", entry.getValue().getMaximum());
		}
	}

	private static void addStatistics(Map<String, String> values,
		Statistics statistics) {
		put(values, "statistics.seed", statistics.getSeed());
		put(values, "statistics.output_label", statistics.getOutputLabel());
		put(values, "statistics.migrations", statistics.getTotalMigrations());
		put(values, "statistics.handoffs", statistics.getTotalHandoffs());
		put(values, "statistics.tuples.lost", statistics.getLostTuples());
		put(values, "statistics.tuples.total", statistics.getTotalTuples());
		put(values, "statistics.tuples.lost_percentage",
			statistics.getLostTuplePercentage());
		addMetricSeries(values, "statistics.without_connection",
			statistics.getWithoutConnection());
		addMetricSeries(values, "statistics.without_vm",
			statistics.getWithoutVm());
		addMetricSeries(values, "statistics.delay_after_connection",
			statistics.getDelayAfterConnection());
		addMetricSeries(values, "statistics.migration_time",
			statistics.getMigrationTime());
		addMetricSeries(values, "statistics.downtime",
			statistics.getDowntime());
	}

	private static void addMetricSeries(Map<String, String> values, String prefix,
		MetricSeries series) {
		put(values, prefix + ".average", series.getAverage());
		put(values, prefix + ".maximum", series.getMaximum());
		addDoubleMap(values, prefix + ".latest_by_user",
			series.getLatestByUserId());
		addDoubleMap(values, prefix + ".maximum_by_user",
			series.getMaximumByUserId());
	}

	private static void addDoubleMap(Map<String, String> values, String prefix,
		Map<Integer, Double> source) {
		put(values, prefix + ".count", source.size());
		int index = 0;
		for (Map.Entry<Integer, Double> entry : source.entrySet()) {
			String item = prefix + "." + index(index++);
			put(values, item + ".key", entry.getKey().intValue());
			put(values, item + ".value", entry.getValue().doubleValue());
		}
	}

	private static void addStringDoubleMap(Map<String, String> values,
		String prefix, Map<String, Double> source) {
		put(values, prefix + ".count", source.size());
		int index = 0;
		for (Map.Entry<String, Double> entry : source.entrySet()) {
			String item = prefix + "." + index(index++);
			put(values, item + ".key", entry.getKey());
			put(values, item + ".value", entry.getValue().doubleValue());
		}
	}

	private static String index(int index) {
		return String.format(java.util.Locale.ROOT, "%04d", index);
	}

	private static void put(Map<String, String> values, String key, String value) {
		if (value == null) {
			throw new IllegalArgumentException("Snapshot value cannot be null: " + key);
		}
		values.put(key, escape(value));
	}

	private static void put(Map<String, String> values, String key, long value) {
		values.put(key, Long.toString(value));
	}

	private static void put(Map<String, String> values, String key, int value) {
		values.put(key, Integer.toString(value));
	}

	private static void put(Map<String, String> values, String key, double value) {
		values.put(key, decimal(value, key));
	}

	private static String decimal(double value, String key) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(
				"Snapshot value must be finite: " + key + '=' + value);
		}
		BigDecimal decimal = BigDecimal.valueOf(value)
			.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP).stripTrailingZeros();
		return decimal.signum() == 0 ? "0" : decimal.toPlainString();
	}

	private static String join(List<String> values) {
		StringBuilder joined = new StringBuilder();
		for (String value : values) {
			if (joined.length() > 0) {
				joined.append(',');
			}
			joined.append(value);
		}
		return joined.toString();
	}

	private static String hashNames(List<String> names) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (String name : names) {
				byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
				digest.update((byte) (bytes.length >>> 24));
				digest.update((byte) (bytes.length >>> 16));
				digest.update((byte) (bytes.length >>> 8));
				digest.update((byte) bytes.length);
				digest.update(bytes);
			}
			StringBuilder hexadecimal = new StringBuilder(64);
			for (byte value : digest.digest()) {
				hexadecimal.append(String.format(java.util.Locale.ROOT, "%02x",
					value & 0xff));
			}
			return hexadecimal.toString();
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static String escape(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
			case '\\':
				escaped.append("\\\\");
				break;
			case '\n':
				escaped.append("\\n");
				break;
			case '\r':
				escaped.append("\\r");
				break;
			default:
				escaped.append(character);
			}
		}
		return escaped.toString();
	}

	@Override
	public boolean equals(Object object) {
		return this == object || object instanceof SimulationSemanticSnapshot
			&& values.equals(((SimulationSemanticSnapshot) object).values);
	}

	@Override
	public int hashCode() {
		return values.hashCode();
	}

	@Override
	public String toString() {
		return toCanonicalText();
	}

	private static final class DeviceGroup {
		private final double power;
		private final double energy;
		private final List<String> names = new ArrayList<String>();

		private DeviceGroup(double power, double energy) {
			this.power = power;
			this.energy = energy;
		}
	}
}
