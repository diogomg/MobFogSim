package org.fog.vmmobile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Locale;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.utils.DataRate;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;

/** Immutable, fully validated command-line configuration for one simulation. */
public final class SimulationConfig {
	private final boolean migrationEnabled;
	private final int seed;
	private final MigrationPointPolicy migrationPointPolicy;
	private final MigrationStrategyPolicy migrationStrategyPolicy;
	private final int maximumUsers;
	private final DataRate maximumBandwidth;
	private final MigrationTechniquePolicy vmMigrationPolicy;
	private final PropagationDelay cloudletLatency;
	private final SimulationDuration travelPredictionTime;
	private final int mobilityPredictionError;
	private final NetworkSlicing.Configuration slicingConfiguration;
	private final VmDestinationPolicy.Destination vmDestinationPolicy;
	private final Path mobilityDirectory;
	private final Path mobilityOrderManifest;
	private final Path outputDirectory;
	private final RunOutputMode outputMode;

	SimulationConfig(boolean migrationEnabled, int seed,
		MigrationPointPolicy migrationPointPolicy,
		MigrationStrategyPolicy migrationStrategyPolicy, int maximumUsers,
		DataRate maximumBandwidth, MigrationTechniquePolicy vmMigrationPolicy,
		PropagationDelay cloudletLatency,
		SimulationDuration travelPredictionTime, int mobilityPredictionError,
		NetworkSlicing.Configuration slicingConfiguration,
		VmDestinationPolicy.Destination vmDestinationPolicy,
		Path mobilityDirectory, Path mobilityOrderManifest, Path outputDirectory,
		RunOutputMode outputMode) {
		if (migrationPointPolicy == null || migrationStrategyPolicy == null
			|| maximumBandwidth == null || vmMigrationPolicy == null
			|| cloudletLatency == null || travelPredictionTime == null
			|| slicingConfiguration == null || vmDestinationPolicy == null
			|| mobilityDirectory == null || mobilityOrderManifest == null
			|| outputDirectory == null || outputMode == null) {
			throw new IllegalArgumentException(
				"Simulation configuration values cannot be null");
		}
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
		return SimulationCli.parse(args);
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
		return (int) maximumBandwidth.toMebibitsPerSecond();
	}

	/** Maximum configured link rate; the CLI expresses it in Mibit/s. */
	public DataRate getMaximumBandwidthRate() {
		return maximumBandwidth;
	}

	public int getVmMigrationPolicy() {
		return vmMigrationPolicy.legacyValue();
	}

	public MigrationTechniquePolicy getMigrationTechnique() {
		return vmMigrationPolicy;
	}

	public double getCloudletLatency() {
		return cloudletLatency.toMilliseconds();
	}

	public PropagationDelay getCloudletPropagationDelay() {
		return cloudletLatency;
	}

	public int getTravelPredictionTime() {
		return (int) travelPredictionTime.toSeconds();
	}

	public SimulationDuration getTravelPredictionDuration() {
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
			+ "; bandwidth=" + getMaximumBandwidth()
			+ "; migrationPolicy=" + vmMigrationPolicy.legacyValue()
			+ "; cloudletLatency=" + plainNumber(
				cloudletLatency.toMilliseconds())
			+ "; travelPrediction=" + getTravelPredictionTime()
			+ "; predictionError=" + mobilityPredictionError
			+ "; sliceScope=" + slicingConfiguration.getScope().legacyValue()
			+ "; userAllocation=" + percentageList(
				slicingConfiguration.getUserPercentages())
			+ "; bandwidthAllocation=" + percentageList(
				slicingConfiguration.getBandwidthPercentages())
			+ "; sliceMode="
			+ (slicingConfiguration.getMode().allowsDynamicBorrowing() ? 1 : 0)
			+ "; reallocationDelay=" + plainNumber(
				slicingConfiguration.getReallocationDelaySeconds())
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

	/** Returns an equivalent configuration using the supplied output policy. */
	public SimulationConfig withOutputMode(RunOutputMode runOutputMode) {
		if (runOutputMode == null) {
			throw new IllegalArgumentException("Run output mode cannot be null");
		}
		return new SimulationConfig(migrationEnabled, seed, migrationPointPolicy,
			migrationStrategyPolicy, maximumUsers, maximumBandwidth,
			vmMigrationPolicy, cloudletLatency, travelPredictionTime,
			mobilityPredictionError, slicingConfiguration, vmDestinationPolicy,
			mobilityDirectory, mobilityOrderManifest, outputDirectory, runOutputMode);
	}
}
