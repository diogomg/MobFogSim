package org.fog.placement;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.SimulationConfig;

/** Immutable, typed summary of one completed simulation run. */
public final class RunReport {

	public static final int SCHEMA_VERSION = 1;

	/** Configuration and publication policy needed by success/failure manifests. */
	public static final class Metadata {
		private final int seed;
		private final RunOutputMode outputMode;
		private final Map<String, String> configuration;

		private Metadata(int seed, RunOutputMode outputMode,
			Map<String, String> configuration) {
			this.seed = seed;
			this.outputMode = outputMode;
			this.configuration = Collections.unmodifiableMap(
				new LinkedHashMap<String, String>(configuration));
		}

		public int getSeed() {
			return seed;
		}

		public RunOutputMode getOutputMode() {
			return outputMode;
		}

		public Map<String, String> getConfiguration() {
			return configuration;
		}
	}

	/** One stable summary column with an explicit unit and value type. */
	public static final class Field {
		private final String name;
		private final String type;
		private final String unit;
		private final String value;

		private Field(String name, String type, String unit, String value) {
			this.name = name;
			this.type = type;
			this.unit = unit;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getType() {
			return type;
		}

		public String getUnit() {
			return unit;
		}

		public String getValue() {
			return value;
		}
	}

	private final Metadata metadata;
	private final SimulationMetricsSnapshot metrics;
	private final List<Field> fields;

	private RunReport(Metadata metadata, SimulationMetricsSnapshot metrics) {
		if (metadata == null || metrics == null) {
			throw new IllegalArgumentException(
				"Run report metadata and metrics cannot be null");
		}
		this.metadata = metadata;
		this.metrics = metrics;
		this.fields = Collections.unmodifiableList(buildFields(metadata, metrics));
	}

	public static RunReport capture(SimulationConfig configuration,
		SimulationMetricsSnapshot metrics) {
		if (configuration == null) {
			throw new IllegalArgumentException(
				"Run report configuration cannot be null");
		}
		return new RunReport(metadata(configuration), metrics);
	}

	/** Creates a complete schema with explicit unavailable configuration values. */
	public static RunReport standalone(RunOutputMode outputMode,
		SimulationMetricsSnapshot metrics) {
		if (outputMode == null || metrics == null) {
			throw new IllegalArgumentException(
				"Standalone report mode and metrics cannot be null");
		}
		Map<String, String> values = emptyConfiguration();
		values.put("seed", Integer.toString(metrics.getStatistics().getSeed()));
		values.put("output_mode", outputMode.name().toLowerCase(
			java.util.Locale.ROOT));
		return new RunReport(new Metadata(metrics.getStatistics().getSeed(),
			outputMode, values), metrics);
	}

	public static Metadata metadata(SimulationConfig configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException(
				"Run report configuration cannot be null");
		}
		Map<String, String> values = emptyConfiguration();
		values.put("migration_enabled",
			Boolean.toString(configuration.isMigrationEnabled()));
		values.put("seed", Integer.toString(configuration.getSeed()));
		values.put("migration_point_policy",
			Integer.toString(configuration.getMigrationPointPolicy()));
		values.put("migration_strategy_policy",
			Integer.toString(configuration.getMigrationStrategyPolicy()));
		values.put("configured_users",
			Integer.toString(configuration.getMaximumUsers()));
		values.put("maximum_bandwidth",
			number(configuration.getMaximumBandwidthRate()
				.toMebibitsPerSecond()));
		values.put("vm_migration_policy",
			Integer.toString(configuration.getVmMigrationPolicy()));
		values.put("cloudlet_latency_ms",
			number(configuration.getCloudletPropagationDelay()
				.toMilliseconds()));
		values.put("travel_prediction_s",
			number(configuration.getTravelPredictionDuration().toSeconds()));
		values.put("mobility_prediction_error_m",
			Integer.toString(configuration.getMobilityPredictionError()));
		NetworkSlicing.Configuration slicing =
			configuration.getSlicingConfiguration();
		values.put("slice_scope", Integer.toString(slicing.getScope().legacyValue()));
		values.put("slice_mode", slicing.getMode().allowsDynamicBorrowing()
			? "dynamic" : "fixed");
		values.put("user_slice_percentages",
			percentageList(slicing.getUserPercentages()));
		values.put("bandwidth_slice_percentages",
			percentageList(slicing.getBandwidthPercentages()));
		values.put("slice_reallocation_delay_s",
			number(slicing.getReallocationDelay().toSeconds()));
		values.put("vm_destination_policy",
			Integer.toString(configuration.getVmDestinationPolicy()));
		values.put("mobility_directory",
			path(configuration.getMobilityDirectory()));
		values.put("mobility_order_manifest",
			path(configuration.getMobilityOrderManifest()));
		values.put("output_directory", path(configuration.getOutputDirectory()));
		values.put("output_mode", configuration.getOutputMode().name()
			.toLowerCase(java.util.Locale.ROOT));
		return new Metadata(configuration.getSeed(), configuration.getOutputMode(),
			values);
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public SimulationMetricsSnapshot getMetrics() {
		return metrics;
	}

	public List<Field> getFields() {
		return fields;
	}

	/** Stable schema used by tests and downstream consumers. */
	public String schemaHeader() {
		StringBuilder header = new StringBuilder();
		for (Field field : fields) {
			if (header.length() > 0) {
				header.append(',');
			}
			header.append(field.name);
		}
		return header.toString();
	}

	private static List<Field> buildFields(Metadata metadata,
		SimulationMetricsSnapshot metrics) {
		List<Field> result = new ArrayList<Field>();
		add(result, "schema_version", SCHEMA_VERSION);
		for (Map.Entry<String, String> entry
			: metadata.configuration.entrySet()) {
			add(result, entry.getKey(), entry.getValue());
		}
		add(result, "execution_time_ms", "ms", metrics.getExecutionTimeMillis());
		add(result, "simulation_time_ms", "ms", metrics.getSimulationTimeMillis());
		add(result, "cloudlet_count", metrics.getCloudlets().size());
		add(result, "cloudlet_energy_total", "W-ms",
			metrics.getTotalCloudletEnergy());
		add(result, "cloudlet_energy_average", "W-ms",
			metrics.getAverageCloudletEnergy());
		add(result, "access_point_count", metrics.getAccessPoints().size());
		add(result, "access_point_energy_total", "W-ms",
			metrics.getTotalAccessPointEnergy());
		add(result, "access_point_energy_average", "W-ms",
			metrics.getAverageAccessPointEnergy());
		add(result, "mobile_device_count", metrics.getMobileDevices().size());
		add(result, "mobile_energy_total", "W-ms", metrics.getTotalMobileEnergy());
		add(result, "mobile_energy_average", "W-ms",
			metrics.getAverageMobileEnergy());
		add(result, "loop_count", metrics.getLoopTimings().size());
		add(result, "loop_delay_average_ms", "ms",
			metrics.getAverageLoopDelay());
		add(result, "loop_maximum_delay_average_ms", "ms",
			metrics.getAverageMaximumLoopDelay());
		add(result, "tuple_cpu_type_count", metrics.getTupleCpuTimes().size());
		add(result, "tuple_transferred_bytes", "byte",
			metrics.getTupleTransferredBytes());
		add(result, "tuple_queue_duration_ms", "ms",
			metrics.getTupleQueueDurationMilliseconds());
		add(result, "tuple_transfer_duration_ms", "ms",
			metrics.getTupleTransferDurationMilliseconds());
		add(result, "tuple_propagation_duration_ms", "ms",
			metrics.getTuplePropagationDurationMilliseconds());
		add(result, "tuple_queue_usage_byte_ms", "byte-ms",
			metrics.getTupleQueueUsageByteMilliseconds());
		add(result, "tuple_transfer_usage_byte_ms", "byte-ms",
			metrics.getTupleTransferUsageByteMilliseconds());
		add(result, "tuple_propagation_usage_byte_ms", "byte-ms",
			metrics.getTuplePropagationUsageByteMilliseconds());
		add(result, "tuple_usage_byte_ms", "byte-ms",
			metrics.getTupleUsageByteMilliseconds());
		add(result, "migration_transferred_bytes", "byte",
			metrics.getMigrationTransferredBytes());
		add(result, "migration_transfer_duration_ms", "ms",
			metrics.getMigrationTransferDurationMilliseconds());
		add(result, "migration_usage_byte_ms", "byte-ms",
			metrics.getMigrationUsageByteMilliseconds());
		add(result, "migration_usage_mean_byte_ms", "byte-ms",
			metrics.getMeanMigrationUsageByteMilliseconds());
		add(result, "total_network_usage_byte_ms", "byte-ms",
			metrics.getTotalUsageByteMilliseconds());
		add(result, "slice_reconfigurations",
			metrics.getSliceReconfigurationCount());
		add(result, "slice_outage_s", "s", metrics.getSliceOutageSeconds());
		addIndexedSeries(result, "slice_received_bandwidth_by_slice",
			"bit/s", metrics.getReceivedBandwidthBySlice());
		add(result, "wireless_queue_limit",
			metrics.getMaximumWirelessQueueSize());
		add(result, "wireless_queue_final",
			metrics.getQueuedWirelessTransferCount());
		add(result, "wireless_queue_maximum_total",
			metrics.getMaximumQueuedWirelessTransferCount());
		add(result, "wireless_queue_maximum_per_direction",
			metrics.getMaximumWirelessQueueDepth());
		add(result, "wireless_queue_dropped_tuples",
			metrics.getDroppedWirelessTupleCount());
		SimulationMetricsSnapshot.Statistics statistics = metrics.getStatistics();
		add(result, "migrations", statistics.getTotalMigrations());
		add(result, "handoffs", statistics.getTotalHandoffs());
		add(result, "tuples_lost", statistics.getLostTuples());
		add(result, "tuples_total", statistics.getTotalTuples());
		add(result, "tuples_lost_percentage", "%",
			statistics.getLostTuplePercentage());
		addSeries(result, "without_connection", statistics.getWithoutConnection());
		addSeries(result, "without_vm", statistics.getWithoutVm());
		addSeries(result, "delay_after_connection",
			statistics.getDelayAfterConnection());
		addSeries(result, "migration_time", statistics.getMigrationTime());
		addSeries(result, "downtime", statistics.getDowntime());
		return result;
	}

	private static void addSeries(List<Field> fields, String prefix,
		SimulationMetricsSnapshot.MetricSeries series) {
		add(fields, prefix + "_average_ms", "ms", series.getAverage());
		add(fields, prefix + "_maximum_ms", "ms", series.getMaximum());
	}

	private static void add(List<Field> fields, String name, String value) {
		fields.add(new Field(name, "string", "", value));
	}

	private static void addIndexedSeries(List<Field> fields, String name,
		String unit, Map<Integer, Double> values) {
		fields.add(new Field(name, "map<integer,number>", unit,
			indexedValues(values)));
	}

	private static void add(List<Field> fields, String name, long value) {
		fields.add(new Field(name, "integer", "count", Long.toString(value)));
	}

	private static void add(List<Field> fields, String name, String unit,
		long value) {
		fields.add(new Field(name, "integer", unit, Long.toString(value)));
	}

	private static void add(List<Field> fields, String name, String unit,
		double value) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(
				"Run report field must be finite: " + name);
		}
		fields.add(new Field(name, "number", unit, number(value)));
	}

	private static Map<String, String> emptyConfiguration() {
		LinkedHashMap<String, String> values =
			new LinkedHashMap<String, String>();
		values.put("migration_enabled", "unspecified");
		values.put("seed", "0");
		values.put("migration_point_policy", "unspecified");
		values.put("migration_strategy_policy", "unspecified");
		values.put("configured_users", "unspecified");
		values.put("maximum_bandwidth", "unspecified");
		values.put("vm_migration_policy", "unspecified");
		values.put("cloudlet_latency_ms", "unspecified");
		values.put("travel_prediction_s", "unspecified");
		values.put("mobility_prediction_error_m", "unspecified");
		values.put("slice_scope", "unspecified");
		values.put("slice_mode", "unspecified");
		values.put("user_slice_percentages", "unspecified");
		values.put("bandwidth_slice_percentages", "unspecified");
		values.put("slice_reallocation_delay_s", "unspecified");
		values.put("vm_destination_policy", "unspecified");
		values.put("mobility_directory", "unspecified");
		values.put("mobility_order_manifest", "unspecified");
		values.put("output_directory", "unspecified");
		values.put("output_mode", "unspecified");
		return values;
	}

	private static String percentageList(double[] values) {
		StringBuilder result = new StringBuilder();
		for (double value : values) {
			if (result.length() > 0) {
				result.append(',');
			}
			result.append(number(value));
		}
		return result.toString();
	}

	private static String indexedValues(Map<Integer, Double> values) {
		StringBuilder result = new StringBuilder();
		for (Map.Entry<Integer, Double> entry : values.entrySet()) {
			if (result.length() > 0) {
				result.append(';');
			}
			result.append(entry.getKey()).append('=').append(number(entry.getValue()));
		}
		return result.toString();
	}

	private static String path(Path value) {
		return value.toAbsolutePath().normalize().toString();
	}

	private static String number(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}
}
