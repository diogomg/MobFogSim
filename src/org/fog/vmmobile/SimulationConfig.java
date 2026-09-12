package org.fog.vmmobile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/** Immutable, fully validated command-line configuration for one simulation. */
public final class SimulationConfig {
	private static final int REQUIRED_PARAMETER_COUNT = 10;
	private static final int MAXIMUM_PARAMETER_COUNT = 16;
	private static final DateTimeFormatter RUN_DIRECTORY_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private final boolean migrationEnabled;
	private final int seed;
	private final MigrationPointPolicy migrationPointPolicy;
	private final MigrationStrategyPolicy migrationStrategyPolicy;
	private final int maximumUsers;
	private final int maximumBandwidth;
	private final MigrationTechniquePolicy vmMigrationPolicy;
	private final double cloudletLatency;
	private final int travelPredictionTime;
	private final int mobilityPredictionError;
	private final NetworkSlicing.Configuration slicingConfiguration;
	private final VmDestinationPolicy.Destination vmDestinationPolicy;
	private final Path mobilityDirectory;
	private final Path mobilityOrderManifest;
	private final Path outputDirectory;
	private final RunOutputMode outputMode;

	private SimulationConfig(boolean migrationEnabled, int seed,
		MigrationPointPolicy migrationPointPolicy,
		MigrationStrategyPolicy migrationStrategyPolicy, int maximumUsers,
		int maximumBandwidth, MigrationTechniquePolicy vmMigrationPolicy,
		double cloudletLatency,
		int travelPredictionTime, int mobilityPredictionError,
		NetworkSlicing.Configuration slicingConfiguration,
		VmDestinationPolicy.Destination vmDestinationPolicy,
		Path mobilityDirectory, Path mobilityOrderManifest, Path outputDirectory,
		RunOutputMode outputMode) {
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
		this.outputDirectory = outputDirectory;
		this.outputMode = outputMode;
	}

	public static SimulationConfig parse(String[] args) {
		if (args == null || args.length < REQUIRED_PARAMETER_COUNT) {
			throw new IllegalArgumentException(
				"AppExample requires at least the first ten simulation parameters");
		}
		if (args.length > MAXIMUM_PARAMETER_COUNT) {
			throw new IllegalArgumentException(
				"AppExample accepts at most sixteen simulation parameters");
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
		int maximumBandwidth = requirePositive(
			parseInteger(args, 5, "Network bandwidth"), "Network bandwidth");
		MigrationTechniquePolicy vmMigrationPolicy =
			MigrationTechniquePolicy.fromLegacy(
				parseInteger(args, 6, "VM migration policy"));
		double cloudletLatency = requirePositiveFinite(
			parseDouble(args, 7, "Cloudlet latency"), "Cloudlet latency");
		int travelPredictionTime = requireNonNegative(
			parseInteger(args, 8, "Travel prediction time"),
			"Travel prediction time");
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
		NetworkSlicing.Configuration slicingConfiguration =
			NetworkSlicing.parseConfiguration(bandwidthAllocation, userAllocation,
				scope, slicingMode);

		VmDestinationPolicy.Destination vmDestinationPolicy = args.length > 14
			? VmDestinationPolicy.Destination.fromLegacy(
				parseInteger(args, 14, "VM destination policy"))
			: VmDestinationPolicy.Destination.HYBRID;

		Path mobilityDirectory = Paths.get("input");
		Path mobilityOrderManifest = mobilityDirectory.resolve("inputOrder.csv");
		Path outputDirectory = defaultOutputDirectory(seed);
		RunOutputMode outputMode = args.length > 15
			? RunOutputMode.parse(args[15]) : RunOutputMode.SUMMARY;

		return new SimulationConfig(migrationEnabled, seed, migrationPointPolicy,
			migrationStrategyPolicy, maximumUsers, maximumBandwidth,
			vmMigrationPolicy, cloudletLatency, travelPredictionTime,
			mobilityPredictionError, slicingConfiguration, vmDestinationPolicy,
			mobilityDirectory, mobilityOrderManifest, outputDirectory, outputMode);
	}

	private static Path defaultOutputDirectory(int seed) {
		String timestamp = LocalDateTime.now().format(RUN_DIRECTORY_TIMESTAMP);
		String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
		return Paths.get("runs", "seed-" + seed + "-" + timestamp + "-"
			+ uniqueSuffix);
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

	public boolean isMigrationEnabled() {
		return migrationEnabled;
	}

	public int getSeed() {
		return seed;
	}

	public int getMigrationPointPolicy() {
		return migrationPointPolicy.legacyValue();
	}

	public MigrationPointPolicy getMigrationPoint() {
		return migrationPointPolicy;
	}

	public int getMigrationStrategyPolicy() {
		return migrationStrategyPolicy.legacyValue();
	}

	public MigrationStrategyPolicy getMigrationStrategy() {
		return migrationStrategyPolicy;
	}

	public int getMaximumUsers() {
		return maximumUsers;
	}

	public int getMaximumBandwidth() {
		return maximumBandwidth;
	}

	public int getVmMigrationPolicy() {
		return vmMigrationPolicy.legacyValue();
	}

	public MigrationTechniquePolicy getMigrationTechnique() {
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
		return vmDestinationPolicy.legacyValue();
	}

	public VmDestinationPolicy.Destination getVmDestination() {
		return vmDestinationPolicy;
	}

	public Path getMobilityDirectory() {
		return mobilityDirectory;
	}

	public Path getMobilityOrderManifest() {
		return mobilityOrderManifest;
	}

	public Path getOutputDirectory() {
		return outputDirectory;
	}

	public RunOutputMode getOutputMode() {
		return outputMode;
	}

	/** Returns one concise line containing every effective CLI parameter. */
	public String toSummaryLine() {
		return "Simulation parameters: migration=" + (migrationEnabled ? 1 : 0)
			+ "; seed=" + seed
			+ "; migrationPoint=" + migrationPointPolicy.legacyValue()
			+ "; migrationStrategy=" + migrationStrategyPolicy.legacyValue()
			+ "; users=" + maximumUsers
			+ "; bandwidth=" + maximumBandwidth
			+ "; migrationPolicy=" + vmMigrationPolicy.legacyValue()
			+ "; cloudletLatency=" + plainNumber(cloudletLatency)
			+ "; travelPrediction=" + travelPredictionTime
			+ "; predictionError=" + mobilityPredictionError
			+ "; sliceScope=" + slicingConfiguration.getScope().legacyValue()
			+ "; userAllocation=" + percentageList(
				slicingConfiguration.getUserPercentages())
			+ "; bandwidthAllocation=" + percentageList(
				slicingConfiguration.getBandwidthPercentages())
			+ "; sliceMode="
			+ (slicingConfiguration.getMode().allowsDynamicBorrowing() ? 1 : 0)
			+ "; vmDestination=" + vmDestinationPolicy.legacyValue()
			+ "; outputMode=" + outputMode.name().toLowerCase(Locale.ENGLISH);
	}

	private static String percentageList(double[] percentages) {
		StringBuilder result = new StringBuilder();
		for (double percentage : percentages) {
			if (result.length() > 0) {
				result.append(',');
			}
			result.append(plainNumber(percentage));
		}
		return result.toString();
	}

	private static String plainNumber(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	/** Returns an equivalent configuration writing beneath the supplied root. */
	public SimulationConfig withOutputDirectory(Path runOutputDirectory) {
		if (runOutputDirectory == null) {
			throw new IllegalArgumentException("Run output directory cannot be null");
		}
		return new SimulationConfig(migrationEnabled, seed, migrationPointPolicy,
			migrationStrategyPolicy, maximumUsers, maximumBandwidth,
			vmMigrationPolicy, cloudletLatency, travelPredictionTime,
			mobilityPredictionError, slicingConfiguration, vmDestinationPolicy,
			mobilityDirectory, mobilityOrderManifest, runOutputDirectory, outputMode);
	}
}
