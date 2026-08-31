package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

import org.fog.vmmobile.MobilityDataLoader.MobilityTrace;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MobilityDataLoaderTest {

	private Path temporaryDirectory;

	@Before
	public void createTemporaryDirectory() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobility-input-");
	}

	@After
	public void removeTemporaryDirectory() throws IOException {
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
	public void preservesManifestOrderAcrossRowsAndIgnoresUnrelatedFiles()
		throws IOException {
		writeTrace("100log.csv", "1\t0\t100\t101\t2");
		writeTrace("200log.csv", "1\t0\t200\t201\t2");
		writeTrace("300log.csv", "1\t0\t300\t301\t2");
		write("heatmap.csv", "0\t100\t2");
		write("notes.txt", "not mobility data");
		Path manifest = write("order.csv", "2\t0", "1");

		List<MobilityTrace> traces = MobilityDataLoader.load(
			temporaryDirectory, manifest, 3);

		assertEquals("300log.csv", fileName(traces.get(0)));
		assertEquals("100log.csv", fileName(traces.get(1)));
		assertEquals("200log.csv", fileName(traces.get(2)));
		assertEquals("300", traces.get(0).getRows().get(0)[2]);
	}

	@Test
	public void repositoryManifestRetainsItsFirstConfiguredTrace() {
		List<MobilityTrace> traces = MobilityDataLoader.load(Paths.get("input"),
			Paths.get("input", "inputOrder.csv"), 2);

		assertEquals("2116log.csv", fileName(traces.get(0)));
		assertEquals("1708log.csv", fileName(traces.get(1)));
	}

	@Test
	public void rejectsManifestWithTooFewUsers() throws IOException {
		writeTrace("100log.csv", "1\t0\t100\t101\t2");
		Path manifest = write("order.csv", "0");

		try {
			MobilityDataLoader.load(temporaryDirectory, manifest, 2);
			fail("Expected a short order manifest to be rejected");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains("1 entries"));
			assertTrue(expected.getMessage().contains("2 users"));
		}
	}

	@Test
	public void rejectsEveryOutOfRangeManifestReference() throws IOException {
		writeTrace("100log.csv", "1\t0\t100\t101\t2");
		Path manifest = write("order.csv", "0\t4");

		try {
			MobilityDataLoader.load(temporaryDirectory, manifest, 1);
			fail("Expected an out-of-range trace index to be rejected");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains("entry 2"));
			assertTrue(expected.getMessage().contains("trace index 4"));
		}
	}

	@Test
	public void rejectsMalformedTraceRowsWithFileAndLineContext() throws IOException {
		writeTrace("100log.csv", "1\t0\t100\t101");
		Path manifest = write("order.csv", "0");

		try {
			MobilityDataLoader.load(temporaryDirectory, manifest, 1);
			fail("Expected a malformed trace row to be rejected");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains("100log.csv"));
			assertTrue(expected.getMessage().contains("line 1"));
			assertTrue(expected.getMessage().contains("five"));
		}
	}

	@Test
	public void rejectsEmptyTraceFiles() throws IOException {
		write("100log.csv");
		Path manifest = write("order.csv", "0");

		try {
			MobilityDataLoader.load(temporaryDirectory, manifest, 1);
			fail("Expected an empty trace file to be rejected");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains("is empty"));
		}
	}

	@Test
	public void rejectsManifestWithoutCsvExtension() throws IOException {
		writeTrace("100log.csv", "1\t0\t100\t101\t2");
		Path manifest = write("order.txt", "0");

		try {
			MobilityDataLoader.load(temporaryDirectory, manifest, 1);
			fail("Expected the manifest extension to be validated");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains(".csv extension"));
		}
	}

	@Test
	public void rejectsMissingMobilityDirectoryWithContext() throws IOException {
		Path manifest = write("order.csv", "0");
		Path missingDirectory = temporaryDirectory.resolve("missing");

		try {
			MobilityDataLoader.load(missingDirectory, manifest, 1);
			fail("Expected a missing mobility directory to be rejected");
		} catch (MobilityInputException expected) {
			assertTrue(expected.getMessage().contains("does not exist"));
			assertTrue(expected.getMessage().contains("missing"));
		}
	}

	private Path writeTrace(String name, String... rows) throws IOException {
		return write(name, rows);
	}

	private Path write(String name, String... lines) throws IOException {
		Path file = temporaryDirectory.resolve(name);
		Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
		return file;
	}

	private static String fileName(MobilityTrace trace) {
		return trace.getSource().getFileName().toString();
	}
}
