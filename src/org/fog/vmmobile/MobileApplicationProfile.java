package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable workload, resource, and migration requirements for one app. */
public final class MobileApplicationProfile {
	private final String name;
	private final double sensorIntervalMillis;
	private final double tupleCpuLength;
	private final double responseCpuLength;
	private final double globalUpdateCpuLength;
	private final double uplinkTupleBytes;
	private final double downlinkTupleBytes;
	private final double stateUpdateIntervalMillis;
	private final double globalUpdateIntervalMillis;
	private final double selectivity;
	private final double sensorLatencyMillis;
	private final double actuatorLatencyMillis;
	private final double vmMips;
	private final int vmRamMebibytes;
	private final long vmBandwidth;
	private final long vmSizeMebibytes;
	private final int clientRamMebibytes;
	private final boolean lowestLatencyMigration;
	private final boolean liveMigration;

	private MobileApplicationProfile(Builder builder) {
		this.name = requiredName(builder.name);
		this.sensorIntervalMillis = positive(builder.sensorIntervalMillis,
			"Sensor interval");
		this.tupleCpuLength = positive(builder.tupleCpuLength,
			"Tuple CPU length");
		this.responseCpuLength = positive(builder.responseCpuLength,
			"Response CPU length");
		this.globalUpdateCpuLength = positive(builder.globalUpdateCpuLength,
			"Global-update CPU length");
		this.uplinkTupleBytes = positive(builder.uplinkTupleBytes,
			"Uplink tuple size");
		this.downlinkTupleBytes = positive(builder.downlinkTupleBytes,
			"Downlink tuple size");
		this.stateUpdateIntervalMillis = positive(
			builder.stateUpdateIntervalMillis, "State-update interval");
		this.globalUpdateIntervalMillis = positive(
			builder.globalUpdateIntervalMillis, "Global-update interval");
		if (!Double.isFinite(builder.selectivity) || builder.selectivity < 0.0
			|| builder.selectivity > 1.0) {
			throw new IllegalArgumentException(
				"Tuple selectivity must be finite and between zero and one");
		}
		this.selectivity = builder.selectivity;
		this.sensorLatencyMillis = nonNegative(builder.sensorLatencyMillis,
			"Sensor latency");
		this.actuatorLatencyMillis = nonNegative(builder.actuatorLatencyMillis,
			"Actuator latency");
		this.vmMips = positive(builder.vmMips, "VM MIPS");
		this.vmRamMebibytes = positive(builder.vmRamMebibytes, "VM RAM");
		this.vmBandwidth = positive(builder.vmBandwidth, "VM bandwidth");
		this.vmSizeMebibytes = positive(builder.vmSizeMebibytes, "VM size");
		this.clientRamMebibytes = positive(builder.clientRamMebibytes,
			"Client RAM");
		this.lowestLatencyMigration = builder.lowestLatencyMigration;
		this.liveMigration = builder.liveMigration;
	}

	public static Builder builder(String name) {
		return new Builder(name);
	}

	/** Historical AppExample settings. */
	public static MobileApplicationProfile standard() {
		return builder("standard").build();
	}

	public static List<MobileApplicationProfile> standardProfiles(int count) {
		if (count <= 0) {
			throw new IllegalArgumentException("Application count must be positive");
		}
		List<MobileApplicationProfile> profiles =
			new ArrayList<MobileApplicationProfile>();
		for (int index = 0; index < count; index++) {
			profiles.add(standard());
		}
		return Collections.unmodifiableList(profiles);
	}

	public String getName() {
		return name;
	}

	public double getSensorIntervalMillis() {
		return sensorIntervalMillis;
	}

	public double getTupleCpuLength() {
		return tupleCpuLength;
	}

	public double getResponseCpuLength() {
		return responseCpuLength;
	}

	public double getGlobalUpdateCpuLength() {
		return globalUpdateCpuLength;
	}

	public double getUplinkTupleBytes() {
		return uplinkTupleBytes;
	}

	public double getDownlinkTupleBytes() {
		return downlinkTupleBytes;
	}

	public double getStateUpdateIntervalMillis() {
		return stateUpdateIntervalMillis;
	}

	public double getGlobalUpdateIntervalMillis() {
		return globalUpdateIntervalMillis;
	}

	public double getSelectivity() {
		return selectivity;
	}

	public double getSensorLatencyMillis() {
		return sensorLatencyMillis;
	}

	public double getActuatorLatencyMillis() {
		return actuatorLatencyMillis;
	}

	public double getVmMips() {
		return vmMips;
	}

	public int getVmRamMebibytes() {
		return vmRamMebibytes;
	}

	public long getVmBandwidth() {
		return vmBandwidth;
	}

	public long getVmSizeMebibytes() {
		return vmSizeMebibytes;
	}

	public int getClientRamMebibytes() {
		return clientRamMebibytes;
	}

	public boolean usesLowestLatencyMigration() {
		return lowestLatencyMigration;
	}

	public boolean requiresLiveMigration() {
		return liveMigration;
	}

	private static String requiredName(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Application profile name is required");
		}
		return value.trim();
	}

	private static double positive(double value, String description) {
		if (!Double.isFinite(value) || value <= 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and positive");
		}
		return value;
	}

	private static int positive(int value, String description) {
		if (value <= 0) {
			throw new IllegalArgumentException(description + " must be positive");
		}
		return value;
	}

	private static long positive(long value, String description) {
		if (value <= 0L) {
			throw new IllegalArgumentException(description + " must be positive");
		}
		return value;
	}

	private static double nonNegative(double value, String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
		return value;
	}

	public static final class Builder {
		private final String name;
		private double sensorIntervalMillis = 10.0;
		private double tupleCpuLength = 966.0;
		private double responseCpuLength = 2439.0;
		private double globalUpdateCpuLength = 28.0;
		private double uplinkTupleBytes = 54.0;
		private double downlinkTupleBytes = 87.0;
		private double stateUpdateIntervalMillis = 1000.0;
		private double globalUpdateIntervalMillis = 2439.0;
		private double selectivity = 0.9;
		private double sensorLatencyMillis = 6.0;
		private double actuatorLatencyMillis = 1.0;
		private double vmMips = 281.0;
		private int vmRamMebibytes = 128;
		private long vmBandwidth = 1000L;
		private long vmSizeMebibytes = 128L;
		private int clientRamMebibytes = 10;
		private boolean lowestLatencyMigration;
		private boolean liveMigration;

		private Builder(String name) {
			this.name = name;
		}

		public Builder sensorIntervalMillis(double value) {
			this.sensorIntervalMillis = value;
			return this;
		}

		public Builder tupleCpuLength(double value) {
			this.tupleCpuLength = value;
			return this;
		}

		public Builder responseCpuLength(double value) {
			this.responseCpuLength = value;
			return this;
		}

		public Builder globalUpdateCpuLength(double value) {
			this.globalUpdateCpuLength = value;
			return this;
		}

		public Builder uplinkTupleBytes(double value) {
			this.uplinkTupleBytes = value;
			return this;
		}

		public Builder downlinkTupleBytes(double value) {
			this.downlinkTupleBytes = value;
			return this;
		}

		public Builder stateUpdateIntervalMillis(double value) {
			this.stateUpdateIntervalMillis = value;
			return this;
		}

		public Builder globalUpdateIntervalMillis(double value) {
			this.globalUpdateIntervalMillis = value;
			return this;
		}

		public Builder selectivity(double value) {
			this.selectivity = value;
			return this;
		}

		public Builder sensorLatencyMillis(double value) {
			this.sensorLatencyMillis = value;
			return this;
		}

		public Builder actuatorLatencyMillis(double value) {
			this.actuatorLatencyMillis = value;
			return this;
		}

		public Builder vmMips(double value) {
			this.vmMips = value;
			return this;
		}

		public Builder vmRamMebibytes(int value) {
			this.vmRamMebibytes = value;
			return this;
		}

		public Builder vmBandwidth(long value) {
			this.vmBandwidth = value;
			return this;
		}

		public Builder vmSizeMebibytes(long value) {
			this.vmSizeMebibytes = value;
			return this;
		}

		public Builder clientRamMebibytes(int value) {
			this.clientRamMebibytes = value;
			return this;
		}

		public Builder lowestLatencyMigration(boolean value) {
			this.lowestLatencyMigration = value;
			return this;
		}

		public Builder liveMigration(boolean value) {
			this.liveMigration = value;
			return this;
		}

		public MobileApplicationProfile build() {
			return new MobileApplicationProfile(this);
		}
	}
}
