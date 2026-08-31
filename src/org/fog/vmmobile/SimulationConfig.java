package org.fog.vmmobile;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.constants.Policies;

/** Immutable, fully validated command-line configuration for one simulation. */
public final class SimulationConfig {
	private static final int REQUIRED_PARAMETER_COUNT = 10;
	private static final int MAXIMUM_PARAMETER_COUNT = 17;

	private final boolean migrationEnabled;
	private final int seed;
	private final int migrationPointPolicy;
	private final int migrationStrategyPolicy;
	private final int maximumUsers;
	private final int maximumBandwidth;
	private final int vmMigrationPolicy;
	private final double cloudletLatency;
	private final int travelPredictionTime;
	private final int mobilityPredictionError;
	private final NetworkSlicing.Configuration slicingConfiguration;
	private final int vmDestinationPolicy;
	private final Path mobilityDirectory;
	private final Path mobilityOrderManifest;

	private SimulationConfig(boolean migrationEnabled, int seed,
		int migrationPointPolicy, int migrationStrategyPolicy, int maximumUsers,
		int maximumBandwidth, int vmMigrationPolicy, double cloudletLatency,
		int travelPredictionTime, int mobilityPredictionError,
		NetworkSlicing.Configuration slicingConfiguration, int vmDestinationPolicy,
		Path mobilityDirectory, Path mobilityOrderManifest) {
		this.migrationEnabled = migrationEnabled;
		this.seed = seed;
		this.migrationPointPolicy = migrationPointPolicy;
		this.migrationStrategyPolicy = migrationStrategyPolicy;
		this.maximumUsers = maximumUsers;
		this.maximumBandwidth = maximumBandwidth;
		this.vmMigrationPolicy = vmMigrationPolicy;
		this.cloudletLatency = cloudletLatency;
		this.travelPredictionTime = travelPredictionTime;
		this.mobilityPredictionError = mobilityPredictionError;
		this.slicingConfiguration = slicingConfiguration;
		this.vmDestinationPolicy = vmDestinationPolicy;
		this.mobilityDirectory = mobilityDirectory;
		this.mobilityOrderManifest = mobilityOrderManifest;
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
		int migrationPointPolicy = requireRange(
			parseInteger(args, 2, "Migration point policy"),
			Policies.FIXED_MIGRATION_POINT, Policies.SPEED_MIGRATION_POINT,
			"Migration point policy");
		int migrationStrategyPolicy = requireRange(
			parseInteger(args, 3, "Migration strategy policy"),
			Policies.LOWEST_LATENCY, Policies.LOWEST_DIST_BW_SMARTTING_AP,
			"Migration strategy policy");
		int maximumUsers = requirePositive(
			parseInteger(args, 4, "Number of users"), "Number of users");
		int maximumBandwidth = requirePositive(
			parseInteger(args, 5, "Network bandwidth"), "Network bandwidth");
		int vmMigrationPolicy = requireRange(
			parseInteger(args, 6, "VM migration policy"),
			Policies.MIGRATION_COMPLETE_VM, Policies.LIVE_MIGRATION,
			"VM migration policy");
		double cloudletLatency = requirePositiveFinite(
			parseDouble(args, 7, "Cloudlet latency"), "Cloudlet latency");
		int travelPredictionTime = requireNonNegative(
			parseInteger(args, 8, "Travel prediction time"),
			"Travel prediction time");
		int mobilityPredictionError = requireNonNegative(
			parseInteger(args, 9, "Mobility prediction error"),
			"Mobility prediction error");

		int scope = args.length > 10
			? parseInteger(args, 10, "Network slicing scope")
			: NetworkSlicing.END_TO_END_NETWORK;
		String userAllocation = args.length > 11 ? args[11] : null;
		String bandwidthAllocation = args.length > 12 ? args[12] : null;
		boolean dynamicSlicing = args.length <= 13
			|| parseFlag(args, 13, "Dynamic slicing");
		NetworkSlicing.Configuration slicingConfiguration =
			NetworkSlicing.parseConfiguration(bandwidthAllocation, userAllocation,
				scope, dynamicSlicing);

		int vmDestinationPolicy = args.length > 14
			? parseInteger(args, 14, "VM destination policy")
			: VmDestinationPolicy.HYBRID;
		requireRange(vmDestinationPolicy, VmDestinationPolicy.EDGE_SERVERS_ONLY,
			VmDestinationPolicy.HYBRID, "VM destination policy");

		Path mobilityDirectory = parsePath(args.length > 15 ? args[15] : "input",
			"Mobility directory");
		Path mobilityOrderManifest = args.length > 16
			? parsePath(args[16], "Mobility order manifest")
			: mobilityDirectory.resolve("inputOrder.csv");

		return new SimulationConfig(migrationEnabled, seed, migrationPointPolicy,
			migrationStrategyPolicy, maximumUsers, maximumBandwidth,
			vmMigrationPolicy, cloudletLatency, travelPredictionTime,
			mobilityPredictionError, slicingConfiguration, vmDestinationPolicy,
			mobilityDirectory, mobilityOrderManifest);
	}

	private static boolean parseFlag(String[] args, int index, String description) {
		int value = parseInteger(args, index, description);
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException(description + " must be 0 or 1");
		}
		return value == 1;
	}

	private static int parseInteger(String[] args, int index, String description) {
		try {
			return Integer.parseInt(requiredValue(args[index], description));
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException(description + " must be an integer", error);
		}
	}

	private static double parseDouble(String[] args, int index, String description) {
		try {
			return Double.parseDouble(requiredValue(args[index], description));
		}
		catch (NumberFormatException error) {
			throw new IllegalArgumentException(description + " must be a number", error);
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

	private static int requireRange(int value, int minimum, int maximum,
		String description) {
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException(description + " must be between "
				+ minimum + " and " + maximum);
		}
		return value;
	}

	private static double requirePositiveFinite(double value, String description) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and positive");
		}
		return value;
	}

	private static Path parsePath(String value, String description) {
		String parsedValue = requiredValue(value, description);
		try {
			return Paths.get(parsedValue);
		}
		catch (InvalidPathException error) {
			throw new IllegalArgumentException(
				"Invalid " + description.toLowerCase() + ": " + error.getInput(), error);
		}
	}

	public boolean isMigrationEnabled() {
		return migrationEnabled;
	}

	public int getSeed() {
		return seed;
	}

	public int getMigrationPointPolicy() {
		return migrationPointPolicy;
	}

	public int getMigrationStrategyPolicy() {
		return migrationStrategyPolicy;
	}

	public int getMaximumUsers() {
		return maximumUsers;
	}

	public int getMaximumBandwidth() {
		return maximumBandwidth;
	}

	public int getVmMigrationPolicy() {
		return vmMigrationPolicy;
	}

	public double getCloudletLatency() {
		return cloudletLatency;
	}

	public int getTravelPredictionTime() {
		return travelPredictionTime;
	}

	public int getMobilityPredictionError() {
		return mobilityPredictionError;
	}

	public NetworkSlicing.Configuration getSlicingConfiguration() {
		return slicingConfiguration;
	}

	public int getVmDestinationPolicy() {
		return vmDestinationPolicy;
	}

	public Path getMobilityDirectory() {
		return mobilityDirectory;
	}

	public Path getMobilityOrderManifest() {
		return mobilityOrderManifest;
	}
}
