package org.fog.vmmobile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.cloudbus.cloudsim.core.SimulationEventCounters;

/** Process-isolated entry point used by the reproducible performance baseline. */
public final class SimulationBenchmark {
	static final int BENCHMARK_SCHEMA_VERSION = 7;

	private SimulationBenchmark() {
	}

	public static void main(String[] arguments) throws Exception {
		if (arguments.length > 0 && "--list".equals(arguments[0])) {
			if (arguments.length == 1) {
				throw new IllegalArgumentException(
					"Expected at least one benchmark fixture or group after --list");
			}
			String[] selections = Arrays.copyOfRange(arguments, 1, arguments.length);
			for (String name : fixtureNames(selections)) {
				System.out.println(name);
			}
			return;
		}
		if (arguments.length != 3) {
			throw new IllegalArgumentException(
				"Expected fixture name, run-output directory and metrics file");
		}
		Fixture fixture = Fixture.fromName(arguments[0]);
		Path runOutput = Paths.get(arguments[1]);
		Path metricsFile = Paths.get(arguments[2]);
		SimulationConfig configuration = fixture.configuration(runOutput);

		SimulationRunResult result = AppExample.run(configuration);
		writeMetrics(metricsFile, fixture, configuration, result,
			inventory(runOutput));
	}

	static List<String> fixtureNames(String... selections) {
		return Fixture.namesFor(selections);
	}

	static String[] fixtureArguments(String name) {
		return Fixture.fromName(name).arguments.clone();
	}

	private static void writeMetrics(Path metricsFile, Fixture fixture,
		SimulationConfig configuration, SimulationRunResult result,
		OutputInventory output) throws IOException {
		TreeMap<String, String> values = new TreeMap<String, String>();
		SimulationEventCounters events = result.getEventCounters();
		SimulationTopologySize topology = result.getTopologySize();
		values.put("benchmark_schema_version",
			Integer.toString(BENCHMARK_SCHEMA_VERSION));
		values.put("fixture", fixture.name);
		values.put("configuration", fixture.argumentsText());
		values.put("seed", Integer.toString(configuration.getSeed()));
		values.put("configured_users",
			Integer.toString(configuration.getMaximumUsers()));
		values.put("migration_enabled",
			Boolean.toString(configuration.isMigrationEnabled()));
		values.put("migration_policy",
			Integer.toString(configuration.getVmMigrationPolicy()));
		values.put("output_mode", configuration.getOutputMode().name()
			.toLowerCase(Locale.ENGLISH));
		values.put("java_version", System.getProperty("java.version"));
		values.put("java_vm", System.getProperty("java.vm.name"));
		values.put("maximum_heap_bytes",
			Long.toString(Runtime.getRuntime().maxMemory()));
		values.put("simulation_time_ms",
			Double.toString(result.getMetrics().getSimulationTimeMillis()));
		values.put("cloudsim_entities",
			Integer.toString(result.getCloudSimEntityCount()));
		values.put("generated_fog_entities",
			Integer.toString(result.getGeneratedFogEntityCount()));
		values.put("generated_tuples",
			Integer.toString(result.getGeneratedTupleCount()));
		values.put("generated_actual_tuples",
			Integer.toString(result.getGeneratedActualTupleCount()));
		values.put("events_dispatched_total",
			Long.toString(events.getTotalDispatched()));
		values.put("events_dispatched_queued",
			Long.toString(events.getQueuedDispatched()));
		values.put("events_dispatched_periodic",
			Long.toString(events.getPeriodicDispatched()));
		values.put("event_internal_types",
			Integer.toString(events.getDispatchedByInternalType().size()));
		values.put("event_tags",
			Integer.toString(events.getDispatchedByTag().size()));
		values.put("topology_mobile_devices",
			Integer.toString(topology.getMobileDeviceCount()));
		values.put("topology_server_cloudlets",
			Integer.toString(topology.getServerCloudletCount()));
		values.put("topology_access_points",
			Integer.toString(topology.getAccessPointCount()));
		values.put("topology_network_nodes",
			Integer.toString(topology.getNetworkNodeCount()));
		values.put("topology_network_links",
			Integer.toString(topology.getNetworkLinkCount()));
		values.put("topology_server_adjacency_entries",
			Long.toString(topology.getServerAdjacencyEntryCount()));
		values.put("run_output_files", Long.toString(output.fileCount));
		values.put("run_output_bytes", Long.toString(output.byteCount));
		values.put("slice_reconfigurations",
			Long.toString(result.getMetrics().getSliceReconfigurationCount()));
		values.put("slice_outage_seconds",
			Double.toString(result.getMetrics().getSliceOutageSeconds()));
		values.put("slice_received_bandwidth_bits_per_second_sum",
			formatSliceBandwidth(
				result.getMetrics().getReceivedBandwidthBySlice()));
		values.put("wireless_queue_limit",
			Integer.toString(result.getMetrics().getMaximumWirelessQueueSize()));
		values.put("wireless_queue_final",
			Long.toString(result.getMetrics().getQueuedWirelessTransferCount()));
		values.put("wireless_queue_max_total",
			Long.toString(result.getMetrics()
				.getMaximumQueuedWirelessTransferCount()));
		values.put("wireless_queue_max_per_direction",
			Integer.toString(result.getMetrics().getMaximumWirelessQueueDepth()));
		values.put("wireless_queue_dropped_tuples",
			Long.toString(result.getMetrics().getDroppedWirelessTupleCount()));
		// Preserve every stable run metric, including variable-cardinality device,
		// loop, user, tuple-type, and per-slice values, in the detailed fixture file.
		values.putAll(result.toSemanticSnapshot().getValues());
		values.put("characterisation_sha256",
			sha256(result.toCharacterisationText()));

		Path parent = metricsFile.toAbsolutePath().normalize().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = metricsFile.resolveSibling(
			metricsFile.getFileName().toString() + ".tmp");
		try (BufferedWriter writer = Files.newBufferedWriter(temporary,
			StandardCharsets.UTF_8)) {
			for (Map.Entry<String, String> entry : values.entrySet()) {
				writer.write(entry.getKey());
				writer.write('=');
				writer.write(entry.getValue());
				writer.newLine();
			}
		}
		try {
			Files.move(temporary, metricsFile, StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(temporary, metricsFile,
				StandardCopyOption.REPLACE_EXISTING);
		}
	}

	static String formatSliceBandwidth(Map<Integer, Double> bandwidthBySlice) {
		if (bandwidthBySlice == null) {
			throw new IllegalArgumentException(
				"Received slice bandwidth cannot be null");
		}
		StringBuilder text = new StringBuilder();
		for (Map.Entry<Integer, Double> entry
			: new TreeMap<Integer, Double>(bandwidthBySlice).entrySet()) {
			if (text.length() > 0) {
				text.append(',');
			}
			text.append(entry.getKey()).append('=').append(entry.getValue());
		}
		return text.toString();
	}

	private static OutputInventory inventory(Path root) throws IOException {
		final long[] totals = new long[2];
		if (!Files.exists(root)) {
			return new OutputInventory(0L, 0L);
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
				BasicFileAttributes attributes) throws IOException {
				if (attributes.isRegularFile()) {
					totals[0]++;
					totals[1] += attributes.size();
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return new OutputInventory(totals[0], totals[1]);
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexadecimal = new StringBuilder(hash.length * 2);
			for (byte item : hash) {
				hexadecimal.append(String.format(Locale.ROOT, "%02x", item & 0xff));
			}
			return hexadecimal.toString();
		}
		catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	private static final class OutputInventory {
		private final long fileCount;
		private final long byteCount;

		private OutputInventory(long fileCount, long byteCount) {
			this.fileCount = fileCount;
			this.byteCount = byteCount;
		}
	}

	private static final class Fixture {
		private static final String BASELINE_GROUP = "baseline";
		private static final String MATRIX_GROUP = "matrix";
		private static final String FIFTY_USERS_GROUP = "fifty-users";
		private static final String NUMERIC_USERS_GROUP_SUFFIX = "-users";
		private static final String TEN_USER_FIXTURE_PREFIX = "u10-";
		private static final int USER_ARGUMENT_INDEX = 4;
		private static final String DEFAULT_CLOUDLET_BANDWIDTH = "74";
		private static final String DEFAULT_CLOUDLET_LATENCY = "3";
		private static final String ALL_GROUP = "all";
		private static final List<String> BASELINE_NAMES =
			Collections.unmodifiableList(Arrays.asList(
				"small", "reference", "large"));
		private static final Map<String, Fixture> ALL_FIXTURES;
		private static final List<String> MATRIX_NAMES;
		private static final List<String> FIFTY_USER_NAMES;

		static {
			LinkedHashMap<String, Fixture> fixtures =
				new LinkedHashMap<String, Fixture>();
			ArrayList<String> matrixNames = new ArrayList<String>();
			ArrayList<String> fiftyUserNames = new ArrayList<String>();

			register(fixtures, "small", new String[] {
				"0", "290538", "0", "0", "1", DEFAULT_CLOUDLET_BANDWIDTH,
				"0", DEFAULT_CLOUDLET_LATENCY, "0", "0",
				"0", "100", "100", "1", "2", "2", "none"
			});
			register(fixtures, "reference", referenceArguments(1, "100", "100"));
			register(fixtures, "large", new String[] {
				"0", "290538", "0", "0", "10", DEFAULT_CLOUDLET_BANDWIDTH,
				"0", DEFAULT_CLOUDLET_LATENCY, "0", "0",
				"0", "100", "100", "1", "2", "2", "none"
			});
			register(fixtures, "large-migration",
				referenceArguments(10, "100", "100"));

			registerOneUserMatrix(fixtures, matrixNames);
			registerTenUserMatrix(fixtures, matrixNames);
			registerFiftyUserScenarios(fixtures, matrixNames, fiftyUserNames);
			matrixNames.addAll(fiftyUserNames);
			ALL_FIXTURES = Collections.unmodifiableMap(fixtures);
			MATRIX_NAMES = Collections.unmodifiableList(matrixNames);
			FIFTY_USER_NAMES = Collections.unmodifiableList(fiftyUserNames);
		}

		private final String name;
		private final String[] arguments;

		private Fixture(String name, String[] arguments) {
			this.name = name;
			this.arguments = arguments.clone();
		}

		private static Fixture fromName(String name) {
			Fixture fixture = ALL_FIXTURES.get(name);
			if (fixture == null) {
				fixture = scaledFixture(name);
			}
			if (fixture == null) {
				throw new IllegalArgumentException("Unknown benchmark fixture or group '"
					+ name
					+ "'; use --list baseline, matrix, fifty-users, "
					+ "<positive-integer>-users, or all");
			}
			return fixture;
		}

		private static List<String> namesFor(String[] selections) {
			if (selections == null || selections.length == 0) {
				throw new IllegalArgumentException(
					"At least one benchmark fixture or group is required");
			}
			Set<String> names = new LinkedHashSet<String>();
			for (String selection : selections) {
				if (BASELINE_GROUP.equals(selection)) {
					names.addAll(BASELINE_NAMES);
				}
				else if (MATRIX_GROUP.equals(selection)) {
					names.addAll(MATRIX_NAMES);
				}
				else if (FIFTY_USERS_GROUP.equals(selection)) {
					names.addAll(FIFTY_USER_NAMES);
				}
				else if (ALL_GROUP.equals(selection)) {
					names.addAll(BASELINE_NAMES);
					names.add("large-migration");
					names.addAll(MATRIX_NAMES);
				}
				else {
					Integer userCount = numericUserGroupCount(selection);
					if (userCount != null) {
						names.addAll(namesForUserCount(userCount.intValue()));
					}
					else {
						fromName(selection);
						names.add(selection);
					}
				}
			}
			return Collections.unmodifiableList(new ArrayList<String>(names));
		}

		private static Integer numericUserGroupCount(String selection) {
			if (!selection.endsWith(NUMERIC_USERS_GROUP_SUFFIX)) {
				return null;
			}
			String count = selection.substring(0,
				selection.length() - NUMERIC_USERS_GROUP_SUFFIX.length());
			return parsePositiveUserCount(count, selection);
		}

		private static List<String> namesForUserCount(int userCount) {
			ArrayList<String> names = new ArrayList<String>();
			String sourcePrefix = userCount == 1 ? "u1-"
				: TEN_USER_FIXTURE_PREFIX;
			for (String matrixName : MATRIX_NAMES) {
				if (!matrixName.startsWith(sourcePrefix)) {
					continue;
				}
				if (userCount == 1 || userCount == 10) {
					names.add(matrixName);
				}
				else {
					names.add("u" + userCount + "-"
						+ matrixName.substring(TEN_USER_FIXTURE_PREFIX.length()));
				}
			}
			return names;
		}

		private static Fixture scaledFixture(String name) {
			if (!name.startsWith("u")) {
				return null;
			}
			int separator = name.indexOf('-');
			if (separator <= 1 || separator == name.length() - 1) {
				return null;
			}
			String count = name.substring(1, separator);
			if (!isDecimal(count)) {
				return null;
			}
			int userCount = parsePositiveUserCount(count, name).intValue();
			if (userCount == 1) {
				return null;
			}
			String sourceName = TEN_USER_FIXTURE_PREFIX
				+ name.substring(separator + 1);
			Fixture source = ALL_FIXTURES.get(sourceName);
			if (source == null) {
				return null;
			}
			String[] arguments = source.arguments.clone();
			arguments[USER_ARGUMENT_INDEX] = Integer.toString(userCount);
			return new Fixture(name, arguments);
		}

		private static Integer parsePositiveUserCount(String count,
			String selection) {
			if (!isDecimal(count)) {
				return null;
			}
			if (count.charAt(0) == '0') {
				throw new IllegalArgumentException("Benchmark user count must be a "
					+ "positive integer without leading zeroes: " + selection);
			}
			try {
				return Integer.valueOf(count);
			}
			catch (NumberFormatException invalid) {
				throw new IllegalArgumentException(
					"Benchmark user count is too large: " + selection, invalid);
			}
		}

		private static boolean isDecimal(String value) {
			if (value.isEmpty()) {
				return false;
			}
			for (int index = 0; index < value.length(); index++) {
				if (!Character.isDigit(value.charAt(index))) {
					return false;
				}
			}
			return true;
		}

		private static void registerOneUserMatrix(
			Map<String, Fixture> fixtures, List<String> matrixNames) {
			String[] baseline = referenceArguments(1, "100", "100");
			registerVariant(fixtures, matrixNames, "u1-migration-off",
				baseline, 0, "0");
			registerVariant(fixtures, matrixNames, "u1-point-speed",
				baseline, 2, "1");
			registerVariant(fixtures, matrixNames,
				"u1-strategy-cloudlet-distance", baseline, 3, "1");
			registerVariant(fixtures, matrixNames, "u1-strategy-ap-distance",
				baseline, 3, "2");
			registerVariant(fixtures, matrixNames, "u1-policy-complete-vm",
				baseline, 6, "0");
			registerVariant(fixtures, matrixNames, "u1-policy-container",
				baseline, 6, "1");
			registerVariant(fixtures, matrixNames, "u1-prediction-60",
				baseline, 8, "60");
			registerVariant(fixtures, matrixNames, "u1-error-500",
				baseline, 9, "500");
			registerVariant(fixtures, matrixNames, "u1-scope-end-to-end",
				baseline, 10, "2");
			registerVariant(fixtures, matrixNames, "u1-scope-wireless",
				baseline, 10, "1");
			registerVariant(fixtures, matrixNames, "u1-slicing-static",
				baseline, 13, "0");
			registerVariant(fixtures, matrixNames, "u1-destination-edge",
				baseline, 15, "0");
			registerVariant(fixtures, matrixNames, "u1-destination-device",
				baseline, 15, "1");
		}

		private static void registerTenUserMatrix(
			Map<String, Fixture> fixtures, List<String> matrixNames) {
			String[] baseline = referenceArguments(10, "70,30", "50,50");
			registerMatrix(fixtures, matrixNames, "u10-base", baseline);
			registerVariant(fixtures, matrixNames, "u10-migration-off",
				baseline, 0, "0");
			registerVariant(fixtures, matrixNames, "u10-point-speed",
				baseline, 2, "1");
			registerVariant(fixtures, matrixNames,
				"u10-strategy-cloudlet-distance", baseline, 3, "1");
			registerVariant(fixtures, matrixNames, "u10-strategy-ap-distance",
				baseline, 3, "2");
			registerVariant(fixtures, matrixNames, "u10-policy-complete-vm",
				baseline, 6, "0");
			registerVariant(fixtures, matrixNames, "u10-policy-container",
				baseline, 6, "1");
			registerVariant(fixtures, matrixNames, "u10-prediction-60",
				baseline, 8, "60");
			registerVariant(fixtures, matrixNames, "u10-error-500",
				baseline, 9, "500");
			registerVariant(fixtures, matrixNames, "u10-scope-end-to-end",
				baseline, 10, "2");
			registerVariant(fixtures, matrixNames, "u10-scope-wireless",
				baseline, 10, "1");
			registerMatrix(fixtures, matrixNames, "u10-allocation-equal-two",
				referenceArguments(10, "50,50", "50,50"));
			String[] weightedThreeSlices = referenceArguments(10, "50,30,20",
				"33.34,33.33,33.33");
			registerMatrix(fixtures, matrixNames, "u10-three-slices-weighted",
				weightedThreeSlices);
			registerVariant(fixtures, matrixNames,
				"u10-three-slices-weighted-delay-60", weightedThreeSlices, 14,
				"60");
			registerMatrix(fixtures, matrixNames, "u10-three-slices-equal",
				referenceArguments(10,
					"33.333334,33.333333,33.333333", "33.34,33.33,33.33"));
			registerVariant(fixtures, matrixNames, "u10-slicing-static",
				baseline, 13, "0");
			registerVariant(fixtures, matrixNames, "u10-destination-edge",
				baseline, 15, "0");
			registerVariant(fixtures, matrixNames, "u10-destination-device",
				baseline, 15, "1");
		}

		private static void registerFiftyUserScenarios(
			Map<String, Fixture> fixtures, List<String> matrixNames,
			List<String> fiftyUserNames) {
			for (String matrixName : matrixNames) {
				if (!matrixName.startsWith("u10-")) {
					continue;
				}
				String fiftyUserName = "u50-"
					+ matrixName.substring(TEN_USER_FIXTURE_PREFIX.length());
				String[] arguments = fixtures.get(matrixName).arguments.clone();
				arguments[USER_ARGUMENT_INDEX] = "50";
				register(fixtures, fiftyUserName, arguments);
				fiftyUserNames.add(fiftyUserName);
			}
		}

		private static String[] referenceArguments(int users,
			String userAllocation, String sliceShares) {
			return new String[] {
				"1", "290538", "0", "0", Integer.toString(users),
				DEFAULT_CLOUDLET_BANDWIDTH, "2", DEFAULT_CLOUDLET_LATENCY,
				"0", "0", "0", userAllocation, sliceShares, "1", "2", "2",
				"none"
			};
		}

		private static void registerVariant(Map<String, Fixture> fixtures,
			List<String> matrixNames, String name, String[] baseline, int index,
			String value) {
			String[] arguments = baseline.clone();
			arguments[index] = value;
			registerMatrix(fixtures, matrixNames, name, arguments);
		}

		private static void registerMatrix(Map<String, Fixture> fixtures,
			List<String> matrixNames, String name, String[] arguments) {
			register(fixtures, name, arguments);
			matrixNames.add(name);
		}

		private static void register(Map<String, Fixture> fixtures, String name,
			String[] arguments) {
			if (fixtures.containsKey(name)) {
				throw new IllegalStateException("Duplicate benchmark fixture name: "
					+ name);
			}
			for (Fixture existing : fixtures.values()) {
				if (Arrays.equals(existing.arguments, arguments)) {
					throw new IllegalStateException("Benchmark fixture " + name
						+ " duplicates " + existing.name);
				}
			}
			fixtures.put(name, new Fixture(name, arguments));
		}

		private SimulationConfig configuration(Path outputDirectory) {
			return SimulationConfig.parse(arguments)
				.withOutputDirectory(outputDirectory);
		}

		private String argumentsText() {
			StringBuilder text = new StringBuilder();
			for (String argument : arguments) {
				if (text.length() > 0) {
					text.append(' ');
				}
				text.append(argument);
			}
			return text.toString();
		}
	}
}
