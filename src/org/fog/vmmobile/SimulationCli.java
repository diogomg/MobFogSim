package org.fog.vmmobile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.utils.DataRate;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/** Parses and validates the command-line contract for one simulation run. */
public final class SimulationCli {
	private static final int REQUIRED_PARAMETER_COUNT = 10;
	private static final int MAXIMUM_PARAMETER_COUNT = 17;
	private static final DateTimeFormatter RUN_DIRECTORY_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private SimulationCli() {
	}

	public static SimulationConfig parse(String[] args) {
		if (args == null || args.length < REQUIRED_PARAMETER_COUNT) {
			throw new IllegalArgumentException(
				"AppExample requires at least the first ten simulation parameters");
		}
		if (args.length > MAXIMUM_PARAMETER_COUNT) {
			throw new IllegalArgumentException(
				"AppExample accepts at most seventeen simulation parameters");
		}

		boolean migrationEnabled = parseFlag(args, 0, "Migration enabled");
		int seed = requirePositive(parseInteger(args, 1, "Seed"), "Seed");
		MigrationPointPolicy migrationPointPolicy = MigrationPointPolicy.fromLegacy(
			parseInteger(args, 2, "Migration point policy"));
		MigrationStrategyPolicy migrationStrategyPolicy =
			MigrationStrategyPolicy.fromLegacy(
				parseInteger(args, 3, "Migration strategy policy"));
		int maximumUsers = requirePositive(
			parseInteger(args, 4, "Number of users"), "Number of users");
		DataRate maximumBandwidth = DataRate.ofMebibitsPerSecond(requirePositive(
			parseInteger(args, 5, "Network bandwidth"), "Network bandwidth"));
		MigrationTechniquePolicy migrationTechnique =
			MigrationTechniquePolicy.fromLegacy(
				parseInteger(args, 6, "VM migration policy"));
		PropagationDelay cloudletLatency = PropagationDelay.ofMilliseconds(
			requirePositiveFinite(parseDouble(args, 7, "Cloudlet latency"),
				"Cloudlet latency"));
		SimulationDuration travelPredictionTime = SimulationDuration.ofSeconds(
			requireNonNegative(
			parseInteger(args, 8, "Travel prediction time"),
			"Travel prediction time"));
		int mobilityPredictionError = requireNonNegative(
			parseInteger(args, 9, "Mobility prediction error"),
			"Mobility prediction error");

		NetworkSlicing.Scope scope = args.length > 10
			? NetworkSlicing.Scope.fromLegacy(
				parseInteger(args, 10, "Network slicing scope"))
			: NetworkSlicing.Scope.END_TO_END;
		String userAllocation = args.length > 11 ? args[11] : null;
		String bandwidthAllocation = args.length > 12 ? args[12] : null;
		NetworkSlicing.Mode slicingMode = args.length <= 13
			|| parseFlag(args, 13, "Dynamic slicing")
			? NetworkSlicing.Mode.DYNAMIC : NetworkSlicing.Mode.FIXED;
		SimulationDuration reallocationDelay = SimulationDuration.ofSeconds(
			args.length > 14 ? requireNonNegativeFinite(
				parseDouble(args, 14, "Network slice reallocation delay"),
				"Network slice reallocation delay")
			: NetworkSlicing.DEFAULT_REALLOCATION_DELAY_SECONDS);
		NetworkSlicing.Configuration slicingConfiguration =
			NetworkSlicing.parseConfiguration(bandwidthAllocation, userAllocation,
				scope, slicingMode, reallocationDelay);

		VmDestinationPolicy.Destination vmDestination = args.length > 15
			? VmDestinationPolicy.Destination.fromLegacy(
				parseInteger(args, 15, "VM destination policy"))
			: VmDestinationPolicy.Destination.HYBRID;
		Path mobilityDirectory = Paths.get("input");
		Path mobilityOrderManifest = mobilityDirectory.resolve("inputOrder.csv");
		Path outputDirectory = defaultOutputDirectory(seed);
		RunOutputMode outputMode = args.length > 16
			? RunOutputMode.parse(args[16]) : RunOutputMode.SUMMARY;

		return new SimulationConfig(migrationEnabled, seed, migrationPointPolicy,
			migrationStrategyPolicy, maximumUsers, maximumBandwidth,
			migrationTechnique, cloudletLatency, travelPredictionTime,
			mobilityPredictionError, slicingConfiguration, vmDestination,
			mobilityDirectory, mobilityOrderManifest, outputDirectory, outputMode);
	}

	private static Path defaultOutputDirectory(int seed) {
		String timestamp = LocalDateTime.now().format(RUN_DIRECTORY_TIMESTAMP);
		String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
		return Paths.get("runs", "seed-" + seed + "-" + timestamp + "-"
			+ uniqueSuffix);
	}

	private static boolean parseFlag(String[] args, int index,
		String description) {
		int value = parseInteger(args, index, description);
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException(description + " must be 0 or 1");
		}
		return value == 1;
	}

	private static int parseInteger(String[] args, int index,
		String description) {
		try {
			return Integer.parseInt(requiredValue(args[index], description));
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException(
				description + " must be an integer", error);
		}
	}

	private static double parseDouble(String[] args, int index,
		String description) {
		try {
			return Double.parseDouble(requiredValue(args[index], description));
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException(
				description + " must be a number", error);
		}
	}

	private static String requiredValue(String value, String description) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException(description + " cannot be empty");
		}
		return value.trim();
	}

	private static int requirePositive(int value, String description) {
		if (value <= 0) {
			throw new IllegalArgumentException(description + " must be positive");
		}
		return value;
	}

	private static int requireNonNegative(int value, String description) {
		if (value < 0) {
			throw new IllegalArgumentException(description + " cannot be negative");
		}
		return value;
	}

	private static double requirePositiveFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and positive");
		}
		return value;
	}

	private static double requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
		return value;
	}
}
