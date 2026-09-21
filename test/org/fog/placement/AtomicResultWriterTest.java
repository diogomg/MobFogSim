package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AtomicResultWriterTest {

	private Path temporaryDirectory;

	@Before
	public void setUp() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobfogsim-report-");
		NetworkUsageMonitor.reset();
		NetworkSlicing.useDefaultRuntimeState();
	}

	@After
	public void tearDown() throws IOException {
		RunOutputManager.resetToDefault();
		deleteTree(temporaryDirectory);
	}

	@Test
	public void summaryModePublishesVerifiedSummaryAndManifest() throws Exception {
		Path root = temporaryDirectory.resolve("summary");
		RunOutputManager output = RunOutputManager.initialize(root,
			RunOutputMode.SUMMARY);

		new AtomicResultWriter(output).write(report(RunOutputMode.SUMMARY));

		assertTrue(Files.isRegularFile(reportFile(root,
			AtomicResultWriter.SUMMARY_FILE)));
		String manifest = read(reportFile(root, AtomicResultWriter.MANIFEST_FILE));
		assertTrue(JSONValue.parse(manifest) instanceof JSONObject);
		assertTrue(manifest.contains("\"status\": \"success\""));
		assertTrue(manifest.contains(
			"\"detailed_output_status\": \"not_requested\""));
		assertTrue(manifest.contains("\"total_network_usage_byte_ms\""));
		assertFalse(Files.exists(root.resolve("results.txt")));
	}

	@Test
	public void fullModeMarksClosedDetailedOutputComplete() throws Exception {
		Path root = temporaryDirectory.resolve("full");
		RunOutputManager output = RunOutputManager.initialize(root,
			RunOutputMode.FULL);

		new AtomicResultWriter(output).write(report(RunOutputMode.FULL));

		String manifest = read(reportFile(root, AtomicResultWriter.MANIFEST_FILE));
		assertTrue(JSONValue.parse(manifest) instanceof JSONObject);
		assertTrue(manifest.contains(
			"\"detailed_output_status\": \"complete\""));
	}

	@Test
	public void noneModePublishesNoFiles() throws Exception {
		Path root = temporaryDirectory.resolve("none");
		RunOutputManager output = RunOutputManager.initialize(root,
			RunOutputMode.NONE);

		new AtomicResultWriter(output).write(report(RunOutputMode.NONE));

		assertFalse(Files.exists(root.resolve(AtomicResultWriter.REPORT_DIRECTORY)));
		try (java.util.stream.Stream<Path> paths = Files.list(root)) {
			assertEquals(0L, paths.count());
		}
	}

	@Test
	public void failingWriterLeavesFailureManifestWithoutSuccessSummary()
		throws Exception {
		Path root = temporaryDirectory.resolve("failed");
		RunOutputManager output = RunOutputManager.initialize(root,
			RunOutputMode.FULL);
		AtomicResultWriter.WriterFactory factory = new FailsFirstWriterFactory();
		AtomicResultWriter writer = new AtomicResultWriter(output, factory);

		try {
			writer.write(report(RunOutputMode.FULL));
			fail("Expected the injected summary write failure");
		}
		catch (IOException expected) {
			assertTrue(expected.getMessage().contains("injected write failure"));
		}

		assertFalse(Files.exists(reportFile(root,
			AtomicResultWriter.SUMMARY_FILE)));
		String manifest = read(reportFile(root, AtomicResultWriter.MANIFEST_FILE));
		assertTrue(JSONValue.parse(manifest) instanceof JSONObject);
		assertTrue(manifest.contains("\"status\": \"failed\""));
		assertTrue(manifest.contains("\"schema_version\": 1"));
		assertTrue(manifest.contains("\"seed\": 7"));
		assertTrue(manifest.contains("\"configuration\""));
		assertTrue(manifest.contains("\"units\""));
		assertTrue(manifest.contains(
			"\"detailed_output_status\": \"incomplete\""));
		assertNoStagingDirectories(root);
	}

	@Test
	public void csvSchemaIsGoldenAndFormattingIsLocaleIndependent()
		throws Exception {
		Locale original = Locale.getDefault();
		try {
			Locale.setDefault(Locale.GERMANY);
			Path root = temporaryDirectory.resolve("locale");
			RunOutputManager output = RunOutputManager.initialize(root,
				RunOutputMode.SUMMARY);
			new AtomicResultWriter(output).write(report(RunOutputMode.SUMMARY));

			List<String> lines = Files.readAllLines(reportFile(root,
				AtomicResultWriter.SUMMARY_FILE), StandardCharsets.UTF_8);
			assertEquals(resource("/org/fog/placement/run-report-schema.csv").trim(),
				lines.get(0));
			assertTrue(lines.get(1).contains(",1.5,"));
			assertFalse(lines.get(1).contains(",1,5,"));
		}
		finally {
			Locale.setDefault(original);
		}
	}

	private static RunReport report(RunOutputMode mode) {
		MyStatistics statistics = new MyStatistics();
		statistics.setSeed(7);
		SimulationMetricsSnapshot metrics = SimulationMetricsSnapshot.capture(
			Collections.<FogDevice>emptyList(), Collections.<ApDevice>emptyList(),
			Collections.<MobileDevice>emptyList(), statistics, new TimeKeeper(),
			1.5, 2L);
		return RunReport.standalone(mode, metrics);
	}

	private static Path reportFile(Path root, String name) {
		return root.resolve(AtomicResultWriter.REPORT_DIRECTORY).resolve(name);
	}

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	private static String resource(String name) throws IOException {
		try (InputStream stream = AtomicResultWriterTest.class
			.getResourceAsStream(name)) {
			if (stream == null) {
				throw new IOException("Missing test resource " + name);
			}
			java.io.ByteArrayOutputStream content =
				new java.io.ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int read;
			while ((read = stream.read(buffer)) >= 0) {
				content.write(buffer, 0, read);
			}
			return new String(content.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static void assertNoStagingDirectories(Path root) throws IOException {
		try (java.util.stream.Stream<Path> paths = Files.list(root)) {
			assertFalse(paths.anyMatch(path -> path.getFileName().toString()
				.startsWith(".report-staging-")));
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		List<Path> paths;
		try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
			paths = stream.collect(java.util.stream.Collectors.toList());
		}
		Collections.sort(paths, java.util.Comparator.reverseOrder());
		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}

	private static final class FailsFirstWriterFactory
		implements AtomicResultWriter.WriterFactory {
		private boolean first = true;

		@Override
		public Writer open(Path path) throws IOException {
			if (first) {
				first = false;
				return new Writer() {
					@Override
					public void write(char[] buffer, int offset, int length)
						throws IOException {
						throw new IOException("injected write failure");
					}

					@Override
					public void flush() {}

					@Override
					public void close() {}
				};
			}
			return Files.newBufferedWriter(path, StandardCharsets.UTF_8);
		}
	}
}
