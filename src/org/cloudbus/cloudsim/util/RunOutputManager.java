package org.cloudbus.cloudsim.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Owns the output root for one simulation run and creates output directories
 * before writers are opened.
 */
public final class RunOutputManager {
	private static final Writer DISCARDING_WRITER = new Writer() {
		@Override
		public void write(char[] buffer, int offset, int length) {}

		@Override
		public void flush() {}

		@Override
		public void close() {}
	};
	private static final OutputStream DISCARDING_OUTPUT_STREAM = new OutputStream() {
		@Override
		public void write(int value) {}

		@Override
		public void write(byte[] buffer, int offset, int length) {}
	};

	private static volatile RunOutputManager instance =
		new RunOutputManager(Paths.get("runs", "unconfigured"), RunOutputMode.FULL);

	private final Path outputRoot;
	private final RunOutputMode outputMode;

	private RunOutputManager(Path outputRoot, RunOutputMode outputMode) {
		if (outputRoot == null) {
			throw new IllegalArgumentException("Run output directory cannot be null");
		}
		if (outputMode == null) {
			throw new IllegalArgumentException("Run output mode cannot be null");
		}
		this.outputRoot = outputRoot.toAbsolutePath().normalize();
		this.outputMode = outputMode;
	}

	/** Configures full output and creates the root for compatibility with callers. */
	public static synchronized RunOutputManager initialize(Path outputRoot) {
		return initialize(outputRoot, RunOutputMode.FULL);
	}

	/** Configures the output policy and creates the root for the current run. */
	public static synchronized RunOutputManager initialize(Path outputRoot,
		RunOutputMode outputMode) {
		RunOutputManager configured = new RunOutputManager(outputRoot, outputMode);
		configured.createDirectory(configured.outputRoot);
		instance = configured;
		return configured;
	}

	public static RunOutputManager getInstance() {
		return instance;
	}

	/** Installs an output manager owned by a simulation context. */
	public static synchronized void use(RunOutputManager outputManager) {
		if (outputManager == null) {
			throw new IllegalArgumentException("Run output manager cannot be null");
		}
		instance = outputManager;
	}

	public Path getOutputRoot() {
		return outputRoot;
	}

	public RunOutputMode getOutputMode() {
		return outputMode;
	}

	public boolean isSummaryOutputEnabled() {
		return outputMode.includesSummary();
	}

	public boolean isDetailedOutputEnabled() {
		return outputMode.includesDetailedRecords();
	}

	public boolean isFullOutputEnabled() {
		return outputMode == RunOutputMode.FULL;
	}

	/** Resolves a relative run-output path and creates its parent directories. */
	public Path resolve(String first, String... more) {
		if (first == null || first.trim().isEmpty()) {
			throw new IllegalArgumentException("Run output path cannot be empty");
		}
		return resolve(Paths.get(first, more));
	}

	/** Resolves a relative run-output path and creates its parent directories. */
	public Path resolve(Path relativePath) {
		Path resolved = validatedPath(relativePath);
		Path parent = resolved.getParent();
		if (parent != null) {
			createDirectory(parent);
		}
		return resolved;
	}

	private Path validatedPath(Path relativePath) {
		if (relativePath == null) {
			throw new IllegalArgumentException("Run output path cannot be null");
		}
		if (relativePath.isAbsolute()) {
			throw new IllegalArgumentException(
				"Run output path must be relative: " + relativePath);
		}
		Path resolved = outputRoot.resolve(relativePath).normalize();
		if (!resolved.startsWith(outputRoot) || resolved.equals(outputRoot)) {
			throw new IllegalArgumentException(
				"Run output path escapes the configured directory: " + relativePath);
		}
		return resolved;
	}

	public File resolveFile(String first, String... more) {
		return resolve(first, more).toFile();
	}

	/** Creates an empty file, replacing an existing file with the same name. */
	public File createFreshFile(String relativePath) throws IOException {
		Path path = resolve(relativePath);
		Files.deleteIfExists(path);
		Files.createFile(path);
		return path.toFile();
	}

	/** Creates a detailed file only when the selected mode includes details. */
	public File createFreshDetailedFile(String relativePath) throws IOException {
		Path path = validatedPath(relativePath);
		if (!isDetailedOutputEnabled()) {
			return path.toFile();
		}
		return createFreshFile(relativePath);
	}

	public BufferedWriter newBufferedWriter(String relativePath,
		boolean append) throws IOException {
		Path path = resolve(relativePath);
		OpenOption[] options = append
			? new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
				StandardOpenOption.APPEND }
			: new OpenOption[] { StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
		return Files.newBufferedWriter(path, StandardCharsets.UTF_8, options);
	}

	public BufferedWriter newSummaryBufferedWriter(String relativePath,
		boolean append) throws IOException {
		validatedPath(relativePath);
		if (!isSummaryOutputEnabled()) {
			return new BufferedWriter(DISCARDING_WRITER);
		}
		return newBufferedWriter(relativePath, append);
	}

	public PrintWriter newPrintWriter(String relativePath, boolean append)
		throws IOException {
		return new PrintWriter(newBufferedWriter(relativePath, append));
	}

	public PrintWriter newSummaryPrintWriter(String relativePath, boolean append)
		throws IOException {
		validatedPath(relativePath);
		if (!isSummaryOutputEnabled()) {
			return new PrintWriter(DISCARDING_WRITER);
		}
		return newPrintWriter(relativePath, append);
	}

	public PrintWriter newDetailedPrintWriter(String relativePath, boolean append)
		throws IOException {
		validatedPath(relativePath);
		if (!isDetailedOutputEnabled()) {
			return new PrintWriter(DISCARDING_WRITER);
		}
		return newPrintWriter(relativePath, append);
	}

	public PrintStream newPrintStream(String relativePath) throws IOException {
		return new PrintStream(resolve(relativePath).toFile(),
			StandardCharsets.UTF_8.name());
	}

	/** Writes continuous console traces only in full mode. */
	public PrintStream newFullPrintStream(String relativePath) throws IOException {
		validatedPath(relativePath);
		if (!isFullOutputEnabled()) {
			return new PrintStream(DISCARDING_OUTPUT_STREAM);
		}
		return newPrintStream(relativePath);
	}

	private Path validatedPath(String relativePath) {
		if (relativePath == null || relativePath.trim().isEmpty()) {
			throw new IllegalArgumentException("Run output path cannot be empty");
		}
		return validatedPath(Paths.get(relativePath));
	}

	private void createDirectory(Path directory) {
		try {
			Files.createDirectories(directory);
		}
		catch (IOException error) {
			throw new UncheckedIOException(
				"Unable to create run output directory " + directory, error);
		}
	}

	static synchronized void resetForTests() {
		resetToDefault();
	}

	/** Detaches the previous run without creating another output directory. */
	public static synchronized void resetToDefault() {
		instance = new RunOutputManager(Paths.get("runs", "unconfigured"),
			RunOutputMode.FULL);
	}
}
