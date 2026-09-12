package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilitySample;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class OrchestrationServicesTest {
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		RunOutputManager.initialize(
			Paths.get("build", "test-output", "orchestration-services"),
			RunOutputMode.NONE);
		NetworkUsageMonitor.reset();
		NetworkSlicing.useDefaultRuntimeState();
	}

	@Test
	public void mobilityServiceOwnsEntryAdvanceAndNextTimestamp() {
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 1, 0, 0);
		mobileDevice.setMobilityPath(Arrays.asList(
			new MobilitySample(2.5, 0, 10, 20, 1),
			new MobilitySample(7.0, 0, 30, 40, 2)));
		Coordinate coordinate = new Coordinate();
		coordinate.setInitialCoordinate(mobileDevice);
		MobileUserRegistration.preparePendingUser(mobileDevice);
		MobilityService service = new MobilityService();

		assertEquals(MobilityService.EntryOutcome.READY,
			service.enter(mobileDevice, coordinate));
		assertEquals(4500.0, service.nextUpdateDelay(mobileDevice, 2500.0), 0.0);

		service.advance(mobileDevice, coordinate, 7000.0);

		assertEquals(30, mobileDevice.getCoord().getCoordX());
		assertEquals(40, mobileDevice.getCoord().getCoordY());
		assertTrue(service.isFinished(mobileDevice));
	}

	@Test
	public void associationServiceLeavesUncoveredUserAwaitingAnAccessPoint() {
		MobileDevice mobileDevice = new MobileDevice("uncovered", 5000, 5000,
			2, 0, 0);
		MobileUserRegistration.preparePendingUser(mobileDevice);
		MobileAssociationService service = new MobileAssociationService();

		service.associate(mobileDevice, Collections.emptyList(), new Random(1));

		assertNull(mobileDevice.getSourceAp());
		assertFalse(mobileDevice.isStatus());
		assertEquals(MobileDeviceLifecycle.SEARCHING_FOR_AP,
			mobileDevice.getLifecycleState());
	}

	@Test
	public void resultServiceHasASafeLabelForAnUnknownLoop() {
		assertEquals("Loop 42", SimulationResultsService.loopLabel(42,
			Collections.emptyMap()));
	}

	@Test
	public void resultServiceAcceptsAnEmptySnapshot() {
		SimulationMetricsSnapshot metrics = SimulationMetricsSnapshot.capture(
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), new MyStatistics(), new TimeKeeper(), 0.0, 0L);

		new SimulationResultsService(RunOutputManager.getInstance()).write(
			metrics, Collections.emptyMap());

		assertEquals(0.0, metrics.getTotalUsageByteMilliseconds(), 0.0);
	}

	@Test
	public void resultServiceWritesSliceMetricsInSummaryMode() throws IOException {
		Path outputRoot = temporaryFolder.newFolder("slice-metrics").toPath();
		RunOutputManager output = RunOutputManager.initialize(outputRoot,
			RunOutputMode.SUMMARY);
		SimulationMetricsSnapshot metrics = SimulationMetricsSnapshot.capture(
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), new MyStatistics(), new TimeKeeper(), 0.0, 0L);

		new SimulationResultsService(output).write(metrics, Collections.emptyMap());

		assertEquals("0", read(outputRoot.resolve("sliceReconfigurations.txt")));
		assertEquals("0.0", read(outputRoot.resolve("sliceOutage.txt")));
		assertEquals("0=0.0",
			read(outputRoot.resolve("sliceReceivedBandwidth.txt")));
	}

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
	}
}
