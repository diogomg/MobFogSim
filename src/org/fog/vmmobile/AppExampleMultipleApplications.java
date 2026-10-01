package org.fog.vmmobile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * AppExample variant in which every user owns a configurable number of
 * independent application/VM pairs.
 *
 * <p>The command-line positions are identical to {@link AppExample}. Change
 * {@link #APPLICATION_PROFILES} and recompile to configure the applications
 * created for every user.</p>
 */
public final class AppExampleMultipleApplications {
	/**
	 * Hardcoded per-application scenarios. Add, remove, or tune profiles here.
	 */
	public static final List<MobileApplicationProfile> APPLICATION_PROFILES =
		Collections.unmodifiableList(Arrays.asList(
			MobileApplicationProfile.builder("latency-critical")
				.tupleCpuLength(250.0)
				.uplinkTupleBytes(32.0)
				.downlinkTupleBytes(32.0)
				.sensorLatencyMillis(1.0)
				.actuatorLatencyMillis(0.25)
				.vmMips(1000.0)
				.lowestLatencyMigration(true)
				.build(),
			MobileApplicationProfile.builder("traffic-intensive")
				.sensorIntervalMillis(2.0)
				.uplinkTupleBytes(8192.0)
				.downlinkTupleBytes(4096.0)
				.stateUpdateIntervalMillis(250.0)
				.globalUpdateIntervalMillis(500.0)
				.vmBandwidth(16000L)
				.build()));
	public static final int APPLICATIONS_PER_USER = APPLICATION_PROFILES.size();

	private AppExampleMultipleApplications() {
	}

	public static void main(String[] args) throws Exception {
		run(args);
	}

	public static SimulationRunResult run(String[] args) throws Exception {
		return run(SimulationCli.parse(args));
	}

	public static SimulationRunResult run(SimulationConfig configuration)
		throws Exception {
		return new SimulationRunner().run(configuration,
			APPLICATION_PROFILES);
	}
}
