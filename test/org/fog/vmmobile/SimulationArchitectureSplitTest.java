package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

import org.cloudbus.cloudsim.util.RunOutputMode;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.MobileDevice;
import org.fog.localization.MobilitySample;
import org.fog.vmmobile.constants.MaxAndMin;
import org.junit.Test;

public class SimulationArchitectureSplitTest {
	private static final double DELTA = 0.000001;

	@Test
	public void cliOwnsParsingWhileTheHistoricalEntryPointStillDelegates() {
		String[] arguments = arguments("1", "2");
		SimulationConfig direct = SimulationCli.parse(arguments);
		SimulationConfig compatibility = SimulationConfig.parse(arguments);

		assertEquals(direct.toSummaryLine(), compatibility.toSummaryLine());
		assertEquals(RunOutputMode.NONE, direct.getOutputMode());
	}

	@Test
	public void exampleConfigurationDerivesStableExperimentNames() {
		assertEquals("FIXED_MIGRATION_POINT_with_LOWEST_LATENCY",
			example(arguments("0", "0")).experimentLabel());
		assertEquals("FIXED_MIGRATION_POINT_with_LOWEST_DIST_BW_SMARTTING_AP",
			example(arguments("0", "2")).experimentLabel());
		assertEquals("SPEED_MIGRATION_POINT_with_"
			+ "LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET",
			example(arguments("1", "1")).experimentLabel());
	}

	@Test
	public void runnerCalculatesTheProgressHorizonWithoutStartingCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MobileDevice mobile = new MobileDevice("mobile", 0, 0, 1, 0, 0);
		mobile.setMobilityPath(Arrays.asList(
			new MobilitySample(2.0, 0, 0, 0, 0),
			new MobilitySample(7.5, 0, 0, 0, 0)));

		assertEquals(7500.0, SimulationRunner.expectedSimulationEndTime(
			Collections.singletonList(mobile)), DELTA);
		assertEquals(MaxAndMin.MAX_SIMULATION_TIME,
			SimulationRunner.expectedSimulationEndTime(
				Collections.<MobileDevice>emptyList()), DELTA);
	}

	private static ExampleSimulationConfiguration example(String[] arguments) {
		return new ExampleSimulationConfiguration(SimulationCli.parse(arguments),
			new Random(1L));
	}

	private static String[] arguments(String migrationPoint,
		String migrationStrategy) {
		return new String[] {
			"1", "123", migrationPoint, migrationStrategy, "1", "11", "0",
			"61", "0", "0", "2", "100", "100", "1", "2", "2", "none"
		};
	}
}
