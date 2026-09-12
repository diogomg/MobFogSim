package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Characterisation tests for the four established one-user reference runs. */
public class ReferenceSimulationGoldenTest {

	private static final ReferenceCase MIGRATION_DISABLED =
		new ReferenceCase("migration-disabled", false, 0);
	private static final ReferenceCase COMPLETE_VM =
		new ReferenceCase("complete-vm", true, 0);
	private static final ReferenceCase CONTAINER =
		new ReferenceCase("container", true, 1);
	private static final ReferenceCase LIVE_MIGRATION =
		new ReferenceCase("live-migration", true, 2);

	private Path temporaryDirectory;

	@Before
	public void createTemporaryDirectory() throws IOException {
		temporaryDirectory = Files.createTempDirectory("mobfogsim-golden-");
	}

	@After
	public void cleanUp() throws IOException {
		SimulationContext active = SimulationContext.currentOrNull();
		if (active != null) {
			active.close();
		}
		deleteTree(temporaryDirectory);
	}

	@Test
	public void migrationDisabledMatchesGoldenSnapshot() throws Exception {
		assertMatchesGolden(MIGRATION_DISABLED);
	}

	@Test
	public void completeVmMatchesGoldenSnapshot() throws Exception {
		assertMatchesGolden(COMPLETE_VM);
	}

	@Test
	public void containerMatchesGoldenSnapshot() throws Exception {
		assertMatchesGolden(CONTAINER);
	}

	@Test
	public void liveMigrationMatchesGoldenSnapshot() throws Exception {
		assertMatchesGolden(LIVE_MIGRATION);
	}

	private void assertMatchesGolden(ReferenceCase referenceCase)
		throws Exception {
		SimulationRunResult result = runQuietly(referenceCase.configuration(
			temporaryDirectory.resolve(referenceCase.name)));
		String actual = result.toCharacterisationText();
		String expected = readResource(referenceCase.resourcePath());
		assertEquals("Reference characterisation changed for " + referenceCase.name,
			expected, actual);
	}

	/**
	 * Prints one freshly captured snapshot for intentional baseline updates.
	 * Invoke with one of: migration-disabled, complete-vm, container,
	 * live-migration.
	 */
	public static void main(String[] arguments) throws Exception {
		if (arguments.length != 1) {
			throw new IllegalArgumentException("Expected one reference-case name");
		}
		ReferenceCase referenceCase = findCase(arguments[0]);
		Path output = Files.createTempDirectory("mobfogsim-golden-printer-");
		try {
			SimulationRunResult result = runQuietly(
				referenceCase.configuration(output));
			System.out.print(result.toCharacterisationText());
		}
		finally {
			deleteTree(output);
		}
	}

	private static ReferenceCase findCase(String name) {
		ReferenceCase[] cases = {
			MIGRATION_DISABLED, COMPLETE_VM, CONTAINER, LIVE_MIGRATION
		};
		for (ReferenceCase referenceCase : cases) {
			if (referenceCase.name.equals(name)) {
				return referenceCase;
			}
		}
		throw new IllegalArgumentException("Unknown reference case: " + name);
	}

	private static SimulationRunResult runQuietly(SimulationConfig configuration)
		throws Exception {
		PrintStream originalOutput = System.out;
		try (PrintStream quiet = new PrintStream(new ByteArrayOutputStream())) {
			System.setOut(quiet);
			return AppExample.run(configuration);
		}
		finally {
			System.setOut(originalOutput);
		}
	}

	private static String readResource(String path) throws IOException {
		InputStream stream = ReferenceSimulationGoldenTest.class
			.getResourceAsStream(path);
		assertNotNull("Missing golden snapshot resource " + path, stream);
		StringBuilder text = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(
			stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				text.append(line).append('\n');
			}
		}
		return text.toString();
	}

	private static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
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

	private static final class ReferenceCase {
		private final String name;
		private final boolean migrationEnabled;
		private final int migrationPolicy;

		private ReferenceCase(String name, boolean migrationEnabled,
			int migrationPolicy) {
			this.name = name;
			this.migrationEnabled = migrationEnabled;
			this.migrationPolicy = migrationPolicy;
		}

		private SimulationConfig configuration(Path outputDirectory) {
			return SimulationConfig.parse(new String[] {
				migrationEnabled ? "1" : "0", "290538", "0", "0", "1",
				"11", Integer.toString(migrationPolicy), "61", "0", "0",
				"2", "100", "100", "1", "2", "none"
			}).withOutputDirectory(outputDirectory);
		}

		private String resourcePath() {
			return "/org/fog/vmmobile/golden/" + name + ".snapshot";
		}
	}
}
