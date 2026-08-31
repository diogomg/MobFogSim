package org.cloudbus.cloudsim.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.fog.vmmigration.MyStatistics;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RunOutputManagerTest {

	private Path temporaryDirectory;
	private Path runRoot;

	@Before
	public void createTemporaryRunRoot() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobfogsim-output-");
		runRoot = temporaryDirectory.resolve("nested").resolve("run");
		RunOutputManager.initialize(runRoot);
	}

	@After
	public void cleanTemporaryRunRoot() throws IOException {
		BufferedFileManager.closeAll();
		MyStatistics.setInstance(null);
		RunOutputManager.resetForTests();
		Files.walkFileTree(temporaryDirectory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
				throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException error)
				throws IOException {
				if (error != null) {
					throw error;
				}
				Files.delete(directory);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Test
	public void initializationAndResolutionCreateMissingDirectories() {
		Path output = RunOutputManager.getInstance().resolve(
			"outputLatencies", "12", "latencies.txt");

		assertTrue(Files.isDirectory(runRoot));
		assertTrue(Files.isDirectory(runRoot.resolve("outputLatencies/12")));
		assertEquals(runRoot.resolve("outputLatencies/12/latencies.txt")
			.toAbsolutePath().normalize(), output);
		assertFalse(Files.exists(output));
	}

	@Test
	public void bufferedWriterKeepsRelativeFilesInsideTheRunRoot() throws IOException {
		BufferedFileManager.writeLine("diagnostics/user/data.txt", "line");
		BufferedFileManager.closeAll();

		Path output = runRoot.resolve("diagnostics/user/data.txt");
		assertTrue(Files.isRegularFile(output));
		assertEquals("line", new String(Files.readAllBytes(output),
			StandardCharsets.UTF_8).trim());
	}

	@Test
	public void statisticsCreateLatencyAndAverageDirectoriesFromScratch()
		throws IOException {
		MyStatistics statistics = new MyStatistics();
		MyStatistics.setInstance(statistics);
		statistics.setToPrint("test-policy");
		statistics.setFileMap("outputLatencies/7/latencies.txt", 7);
		statistics.getMyCount().put(7, 0);
		statistics.putLantencyFileName("header", 7);
		statistics.printResults();
		BufferedFileManager.closeAll();

		assertTrue(Files.isRegularFile(
			runRoot.resolve("outputLatencies/7/latencies.txt")));
		assertTrue(Files.isRegularFile(
			runRoot.resolve("averages/withoutConnection_test-policy")));
		assertTrue(Files.isRegularFile(
			runRoot.resolve("averages/all_test-policy")));
	}

	@Test
	public void summaryModeWritesSummariesButSkipsDetailedFiles() throws IOException {
		RunOutputManager output = RunOutputManager.initialize(runRoot,
			RunOutputMode.SUMMARY);

		try (PrintWriter summary = output.newSummaryPrintWriter(
			"summary/result.txt", false);
			PrintWriter detail = output.newDetailedPrintWriter(
				"details/events.txt", false)) {
			summary.println("summary");
			detail.println("detail");
		}
		BufferedFileManager.writeLine("details/buffered.txt", "detail");
		BufferedFileManager.closeAll();

		assertTrue(Files.isRegularFile(runRoot.resolve("summary/result.txt")));
		assertFalse(Files.exists(runRoot.resolve("details/events.txt")));
		assertFalse(Files.exists(runRoot.resolve("details/buffered.txt")));
	}

	@Test
	public void summaryStatisticsDoNotCreateLatencyDirectories() throws IOException {
		RunOutputManager.initialize(runRoot, RunOutputMode.SUMMARY);
		MyStatistics statistics = new MyStatistics();
		MyStatistics.setInstance(statistics);
		statistics.setToPrint("summary-policy");
		statistics.setFileMap("outputLatencies/7/latencies.txt", 7);
		statistics.getMyCount().put(7, 0);
		statistics.putLantencyFileName("header", 7);
		statistics.putLatencyFileValue(1.0, 2.0, "app", 7, "server", "tuple");
		statistics.printResults();
		BufferedFileManager.closeAll();

		assertFalse(Files.exists(runRoot.resolve("outputLatencies")));
		assertTrue(Files.isRegularFile(
			runRoot.resolve("averages/all_summary-policy")));
	}

	@Test
	public void noneModeDoesNotCreateOutputFiles() throws IOException {
		RunOutputManager output = RunOutputManager.initialize(runRoot,
			RunOutputMode.NONE);

		try (PrintWriter summary = output.newSummaryPrintWriter(
			"summary/result.txt", false);
			PrintWriter detail = output.newDetailedPrintWriter(
				"details/events.txt", false)) {
			summary.println("summary");
			detail.println("detail");
		}
		BufferedFileManager.writeLine("details/buffered.txt", "detail");
		BufferedFileManager.closeAll();

		assertFalse(Files.exists(runRoot.resolve("summary/result.txt")));
		assertFalse(Files.exists(runRoot.resolve("details/events.txt")));
		assertFalse(Files.exists(runRoot.resolve("details/buffered.txt")));
	}

	@Test
	public void freshFileReplacesPreviousContents() throws IOException {
		Path output = RunOutputManager.getInstance().resolve("nested/value.txt");
		Files.write(output, "old".getBytes(StandardCharsets.UTF_8));

		RunOutputManager.getInstance().createFreshFile("nested/value.txt");

		assertTrue(Files.isRegularFile(output));
		assertEquals(0, Files.size(output));
	}

	@Test
	public void rejectsPathsOutsideTheRunRoot() {
		assertRejected("../outside.txt");
		assertRejected(temporaryDirectory.resolve("absolute.txt").toString());
	}

	@Test
	public void rejectsAnOutputRootThatIsAFile() throws IOException {
		Path file = temporaryDirectory.resolve("not-a-directory");
		Files.write(file, new byte[] { 1 });

		try {
			RunOutputManager.initialize(file);
			fail("Expected a file output root to be rejected");
		}
		catch (UncheckedIOException expected) {
			assertTrue(expected.getMessage().contains("Unable to create run output directory"));
		}
	}

	private static void assertRejected(String path) {
		try {
			RunOutputManager.getInstance().resolve(path);
			fail("Expected output path to be rejected: " + path);
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}
}
