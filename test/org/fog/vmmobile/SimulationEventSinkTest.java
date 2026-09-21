package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicInteger;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SimulationEventSinkTest {
	private Path temporaryDirectory;

	@Before
	public void createTemporaryDirectory() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobfogsim-events-");
	}

	@After
	public void cleanUp() throws IOException {
		SimulationEventSink.reset();
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
	public void noneModeDoesNotEvaluateMessagesOrCreateTraceFiles()
		throws Exception {
		RunOutputManager output = RunOutputManager.initialize(
			temporaryDirectory.resolve("none"), RunOutputMode.NONE);
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		AtomicInteger evaluations = new AtomicInteger();
		SimulationEventSink sink = SimulationEventSink.open(output,
			new PrintStream(console));

		sink.summary(() -> message(evaluations, "summary"));
		sink.detail("test", () -> message(evaluations, "detail"));
		sink.trace("test", () -> message(evaluations, "trace"));
		sink.finish();

		assertEquals(SimulationEventSink.Level.OFF, sink.getLevel());
		assertEquals(0, evaluations.get());
		assertEquals(0, console.size());
		assertFalse(Files.exists(temporaryDirectory.resolve("none/out.txt")));
	}

	@Test
	public void summaryModeWritesOnlyBoundedSummaryMessages() throws Exception {
		RunOutputManager output = RunOutputManager.initialize(
			temporaryDirectory.resolve("summary"), RunOutputMode.SUMMARY);
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		AtomicInteger evaluations = new AtomicInteger();
		SimulationEventSink sink = SimulationEventSink.open(output,
			new PrintStream(console));

		sink.summary(() -> message(evaluations, "summary"));
		sink.detail("test", () -> message(evaluations, "detail"));
		sink.trace("test", () -> message(evaluations, "trace"));
		sink.finish();

		assertEquals(1, evaluations.get());
		assertEquals("summary\n", console.toString(StandardCharsets.UTF_8.name()));
		assertFalse(Files.exists(temporaryDirectory.resolve("summary/out.txt")));
	}

	@Test
	public void fullModeKeepsSelectedDetailAndTraceEvents() throws Exception {
		Path root = temporaryDirectory.resolve("full");
		RunOutputManager output = RunOutputManager.initialize(root,
			RunOutputMode.FULL);
		ByteArrayOutputStream console = new ByteArrayOutputStream();
		AtomicInteger evaluations = new AtomicInteger();
		SimulationEventSink sink = SimulationEventSink.open(output,
			new PrintStream(console));

		sink.summary(() -> message(evaluations, "summary"));
		sink.detail("test", () -> message(evaluations, "detail"));
		sink.trace("test", () -> message(evaluations, "trace"));
		sink.detailLine(() -> message(evaluations, "rendered result"));
		sink.finish();

		String details = new String(Files.readAllBytes(root.resolve("out.txt")),
			StandardCharsets.UTF_8);
		assertEquals(4, evaluations.get());
		assertTrue(console.toString(StandardCharsets.UTF_8.name())
			.contains("summary"));
		assertTrue(details.contains("test: detail"));
		assertTrue(details.contains("test: trace"));
		assertTrue(details.contains("rendered result"));
	}

	private static String message(AtomicInteger evaluations, String value) {
		evaluations.incrementAndGet();
		return value;
	}
}
