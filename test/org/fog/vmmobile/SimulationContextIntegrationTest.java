package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.placement.SimulationMetricsSnapshot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimulationContextIntegrationTest {
	private static final double DELTA = 0.000001;
	private Path temporaryDirectory;

	@Before
	public void createTemporaryDirectory() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobfogsim-context-");
	}

	@After
	public void cleanUp() throws IOException {
		SimulationContext active = SimulationContext.currentOrNull();
		if (active != null) {
			active.close();
		}
		Files.walkFileTree(temporaryDirectory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
				BasicFileAttributes attributes) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory,
				IOException error) throws IOException {
				if (error != null) {
					throw error;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Test
	public void outputModesHaveIsolatedIdenticalResultsAndExpectedOutput()
		throws Exception {
		Path noneDirectory = temporaryDirectory.resolve("none");
		Path summaryDirectory = temporaryDirectory.resolve("summary");
		Path fullDirectory = temporaryDirectory.resolve("full");
		SimulationConfig configuration = referenceConfiguration();
		PrintStream originalOutput = System.out;
		ByteArrayOutputStream noneConsole = new ByteArrayOutputStream();
		ByteArrayOutputStream summaryConsole = new ByteArrayOutputStream();
		ByteArrayOutputStream fullConsole = new ByteArrayOutputStream();
		SimulationRunResult none;
		SimulationRunResult summary;
		SimulationRunResult full;
		try (PrintStream noneOutput = new PrintStream(noneConsole);
			PrintStream summaryOutput = new PrintStream(summaryConsole);
			PrintStream fullOutput = new PrintStream(fullConsole)) {
			System.setOut(noneOutput);
			none = AppExample.run(configuration
				.withOutputDirectory(noneDirectory)
				.withOutputMode(RunOutputMode.NONE));
			assertContextWasReleased();
			System.setOut(summaryOutput);
			summary = AppExample.run(configuration
				.withOutputDirectory(summaryDirectory)
				.withOutputMode(RunOutputMode.SUMMARY));
			assertContextWasReleased();
			System.setOut(fullOutput);
			full = AppExample.run(configuration
				.withOutputDirectory(fullDirectory)
				.withOutputMode(RunOutputMode.FULL));
		}
		finally {
			System.setOut(originalOutput);
		}

		assertEquivalent(none, summary);
		assertEquivalent(none, full);
		assertReferenceTopology(none.getTopologySize());
		assertEquals(0, noneConsole.size());
		assertTrue("Summary console output must remain bounded",
			summaryConsole.size() < 32768);
		assertTrue(summaryConsole.size() > 0);
		assertTrue("Full console output must remain bounded",
			fullConsole.size() < 32768);
		assertFalse(Files.exists(noneDirectory.resolve("out.txt")));
		assertFalse(Files.exists(summaryDirectory.resolve("out.txt")));
		assertTrue(Files.size(fullDirectory.resolve("out.txt")) > 0);
		assertFalse(Files.exists(noneDirectory.resolve("report")));
		assertTrue(Files.exists(summaryDirectory.resolve("report/summary.csv")));
		assertTrue(Files.exists(fullDirectory.resolve("report/summary.csv")));
		assertContextWasReleased();
	}

	@Test
	public void namedStreamsRepeatBySeedAndDoNotDependOnLookupOrder() {
		SimulationConfig configuration = referenceConfiguration()
			.withOutputDirectory(temporaryDirectory.resolve("streams"));
		long firstTopologyValue;
		long firstSelectivityValue;
		try (SimulationContext context = SimulationContext.open(configuration)) {
			firstTopologyValue = context.random("topology").nextLong();
			firstSelectivityValue = context.random("selectivity").nextLong();
			try {
				SimulationContext.open(configuration);
				fail("Concurrent contexts must be rejected");
			}
			catch (IllegalStateException expected) {
				// Expected.
			}
		}

		try (SimulationContext context = SimulationContext.open(configuration)) {
			Random selectivity = context.random("selectivity");
			Random topology = context.random("topology");
			assertEquals(firstSelectivityValue, selectivity.nextLong());
			assertEquals(firstTopologyValue, topology.nextLong());
		}
	}

	@Test
	public void failedContextConstructionDoesNotLeakIntoTheNextRun()
		throws IOException {
		Path blockingFile = temporaryDirectory.resolve("not-a-directory");
		Files.write(blockingFile, new byte[] { 1 });
		SimulationConfig configuration = referenceConfiguration()
			.withOutputMode(RunOutputMode.SUMMARY);
		try {
			SimulationContext.open(configuration.withOutputDirectory(
				blockingFile.resolve("run")));
			fail("An output path below a regular file must fail");
		}
		catch (RuntimeException expected) {
			// Expected.
		}
		assertContextWasReleased();

		try (SimulationContext context = SimulationContext.open(
			configuration.withOutputDirectory(
				temporaryDirectory.resolve("after-failure")))) {
			assertNotNull(context.getServices());
		}
		assertContextWasReleased();
	}

	private static SimulationConfig referenceConfiguration() {
		return SimulationConfig.parse(new String[] {
			"0", "290538", "0", "0", "1", "11", "0", "61", "0", "0",
			"2", "100", "100", "1", "0", "2", "none"
		});
	}

	private static void assertContextWasReleased() {
		assertNull(SimulationContext.currentOrNull());
		assertTrue(AppExample.getSmartThings().isEmpty());
		assertTrue(AppExample.getServerCloudlets().isEmpty());
		assertTrue(AppExample.getApDevices().isEmpty());
		assertFalse(NetworkTopology.isNetworkEnabled());
	}

	private static void assertReferenceTopology(SimulationTopologySize topology) {
		assertEquals(1, topology.getMobileDeviceCount());
		assertEquals(144, topology.getServerCloudletCount());
		assertEquals(144, topology.getAccessPointCount());
		assertEquals(288, topology.getNetworkNodeCount());
		assertEquals(10440, topology.getNetworkLinkCount());
		assertEquals(20592L, topology.getServerTransportRouteCount());
	}

	private static void assertEquivalent(SimulationRunResult first,
		SimulationRunResult second) {
		assertEquals(first.getCloudSimEntityCount(), second.getCloudSimEntityCount());
		assertEquals(first.getGeneratedFogEntityCount(),
			second.getGeneratedFogEntityCount());
		assertEquals(first.getGeneratedTupleCount(), second.getGeneratedTupleCount());
		assertEquals(first.getGeneratedActualTupleCount(),
			second.getGeneratedActualTupleCount());
		assertEquals(first.getEventCounters(), second.getEventCounters());
		assertEquals(first.getTopologySize(), second.getTopologySize());
		assertEquals(first.toSemanticSnapshot(), second.toSemanticSnapshot());
		assertEquals(first.toCharacterisationText(),
			second.toCharacterisationText());
		assertMetricSnapshotsEqual(first.getMetrics(), second.getMetrics());
	}

	private static void assertMetricSnapshotsEqual(
		SimulationMetricsSnapshot first, SimulationMetricsSnapshot second) {
		assertEquals(first.getSimulationTimeMillis(),
			second.getSimulationTimeMillis(), DELTA);
		assertEquals(first.getTotalCloudletEnergy(),
			second.getTotalCloudletEnergy(), DELTA);
		assertEquals(first.getTotalAccessPointEnergy(),
			second.getTotalAccessPointEnergy(), DELTA);
		assertEquals(first.getAverageLoopDelay(),
			second.getAverageLoopDelay(), DELTA);
		assertEquals(first.getAverageMaximumLoopDelay(),
			second.getAverageMaximumLoopDelay(), DELTA);
		assertEquals(first.getTupleUsageByteMilliseconds(),
			second.getTupleUsageByteMilliseconds(), DELTA);
		assertEquals(first.getMigrationUsageByteMilliseconds(),
			second.getMigrationUsageByteMilliseconds(), DELTA);
		assertEquals(first.getMigrationTransferredBytes(),
			second.getMigrationTransferredBytes(), DELTA);
		assertEquals(first.getMobilePowerHistory(), second.getMobilePowerHistory());
		assertEquals(first.getMobileEnergyHistory(), second.getMobileEnergyHistory());
		assertEquals(first.getTupleCpuTimes(), second.getTupleCpuTimes());
		assertLoopTimingsEqual(first.getLoopTimings(), second.getLoopTimings());
		assertStatisticsEqual(first.getStatistics(), second.getStatistics());
	}

	private static void assertLoopTimingsEqual(
		Map<Integer, SimulationMetricsSnapshot.LoopTiming> first,
		Map<Integer, SimulationMetricsSnapshot.LoopTiming> second) {
		assertEquals(first.keySet(), second.keySet());
		for (Integer loopId : first.keySet()) {
			assertEquals(first.get(loopId).getAverage(),
				second.get(loopId).getAverage(), DELTA);
			assertEquals(first.get(loopId).getMaximum(),
				second.get(loopId).getMaximum(), DELTA);
		}
	}

	private static void assertStatisticsEqual(
		SimulationMetricsSnapshot.Statistics first,
		SimulationMetricsSnapshot.Statistics second) {
		assertEquals(first.getSeed(), second.getSeed());
		assertEquals(first.getOutputLabel(), second.getOutputLabel());
		assertEquals(first.getTotalMigrations(), second.getTotalMigrations());
		assertEquals(first.getTotalHandoffs(), second.getTotalHandoffs());
		assertEquals(first.getLostTuples(), second.getLostTuples());
		assertEquals(first.getTotalTuples(), second.getTotalTuples());
		assertMetricSeriesEqual(first.getWithoutConnection(),
			second.getWithoutConnection());
		assertMetricSeriesEqual(first.getWithoutVm(), second.getWithoutVm());
		assertMetricSeriesEqual(first.getDelayAfterConnection(),
			second.getDelayAfterConnection());
		assertMetricSeriesEqual(first.getMigrationTime(), second.getMigrationTime());
		assertMetricSeriesEqual(first.getDowntime(), second.getDowntime());
	}

	private static void assertMetricSeriesEqual(
		SimulationMetricsSnapshot.MetricSeries first,
		SimulationMetricsSnapshot.MetricSeries second) {
		assertEquals(first.getLatestByUserId(), second.getLatestByUserId());
		assertEquals(first.getMaximumByUserId(), second.getMaximumByUserId());
		assertEquals(first.getAverage(), second.getAverage(), DELTA);
		assertEquals(first.getMaximum(), second.getMaximum(), DELTA);
	}
}
