package org.fog.placement;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;

/** Publishes a verified report directory with one same-filesystem rename. */
public final class AtomicResultWriter implements ResultWriter {

	public static final String REPORT_DIRECTORY = "report";
	public static final String SUMMARY_FILE = "summary.csv";
	public static final String MANIFEST_FILE = "manifest.json";

	/** Injectable checked-writer factory used by failure-path tests. */
	public interface WriterFactory {
		Writer open(Path path) throws IOException;
	}

	private static final WriterFactory UTF8_WRITERS = new WriterFactory() {
		@Override
		public Writer open(Path path) throws IOException {
			return Files.newBufferedWriter(path, StandardCharsets.UTF_8);
		}
	};

	private final RunOutputManager output;
	private final WriterFactory writers;
	private boolean terminal;

	public AtomicResultWriter(RunOutputManager output) {
		this(output, UTF8_WRITERS);
	}

	public AtomicResultWriter(RunOutputManager output, WriterFactory writers) {
		if (output == null || writers == null) {
			throw new IllegalArgumentException(
				"Result output and writer factory cannot be null");
		}
		this.output = output;
		this.writers = writers;
	}

	@Override
	public synchronized void write(RunReport report) throws IOException {
		if (report == null) {
			throw new IllegalArgumentException("Run report cannot be null");
		}
		requireOpen();
		if (output.getOutputMode() == RunOutputMode.NONE) {
			terminal = true;
			return;
		}

		Path staging = createStagingDirectory();
		try {
			String summary = summaryCsv(report);
			writeAndVerify(staging.resolve(SUMMARY_FILE), summary);
			String manifest = successManifest(report, sha256(summary));
			writeAndVerify(staging.resolve(MANIFEST_FILE), manifest);
			publish(staging);
			terminal = true;
		}
		catch (IOException error) {
			cleanup(staging, error);
			try {
				writeFailureInternal(report.getMetadata(), error);
			}
			catch (IOException manifestError) {
				error.addSuppressed(manifestError);
			}
			terminal = true;
			throw error;
		}
		catch (RuntimeException error) {
			cleanup(staging, error);
			try {
				writeFailureInternal(report.getMetadata(), error);
			}
			catch (IOException manifestError) {
				error.addSuppressed(manifestError);
			}
			terminal = true;
			throw error;
		}
	}

	@Override
	public synchronized void writeFailure(RunReport.Metadata metadata,
		Throwable failure) throws IOException {
		if (metadata == null || failure == null) {
			throw new IllegalArgumentException(
				"Failure metadata and cause cannot be null");
		}
		if (terminal) {
			return;
		}
		if (output.getOutputMode() != RunOutputMode.NONE) {
			writeFailureInternal(metadata, failure);
		}
		terminal = true;
	}

	@Override
	public synchronized boolean isTerminal() {
		return terminal;
	}

	private void writeFailureInternal(RunReport.Metadata metadata,
		Throwable failure) throws IOException {
		Path staging = createStagingDirectory();
		try {
			writeAndVerify(staging.resolve(MANIFEST_FILE),
				failureManifest(metadata, failure));
			publish(staging);
		}
		catch (IOException error) {
			cleanup(staging, error);
			throw error;
		}
		catch (RuntimeException error) {
			cleanup(staging, error);
			throw error;
		}
	}

	private Path createStagingDirectory() throws IOException {
		Path root = output.getOutputRoot();
		Files.createDirectories(root);
		Path report = root.resolve(REPORT_DIRECTORY);
		if (Files.exists(report)) {
			throw new IOException("Run report was already published: " + report);
		}
		return Files.createTempDirectory(root, ".report-staging-");
	}

	private void publish(Path staging) throws IOException {
		Path destination = output.getOutputRoot().resolve(REPORT_DIRECTORY);
		try {
			Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException unsupported) {
			Files.move(staging, destination);
		}
	}

	private void writeAndVerify(Path path, String content) throws IOException {
		try (Writer writer = writers.open(path)) {
			writer.write(content);
			writer.flush();
		}
		String actual = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
		if (!content.equals(actual)) {
			throw new IOException("Result verification failed for " + path);
		}
	}

	private void requireOpen() {
		if (terminal) {
			throw new IllegalStateException("A terminal run result was already written");
		}
	}

	private static String summaryCsv(RunReport report) {
		StringBuilder header = new StringBuilder();
		StringBuilder row = new StringBuilder();
		for (RunReport.Field field : report.getFields()) {
			if (header.length() > 0) {
				header.append(',');
				row.append(',');
			}
			header.append(csv(field.getName()));
			row.append(csv(field.getValue()));
		}
		return header.append('\n').append(row).append('\n').toString();
	}

	private String successManifest(RunReport report, String summaryHash) {
		StringBuilder json = manifestStart("success", report.getMetadata());
		property(json, "summary_file", SUMMARY_FILE, true);
		property(json, "summary_sha256", summaryHash, true);
		property(json, "detailed_output_status",
			detailedStatus(report.getMetadata().getOutputMode(), true), true);
		appendUnits(json, report);
		json.append("\n}\n");
		return json.toString();
	}

	private String failureManifest(RunReport.Metadata metadata,
		Throwable failure) {
		StringBuilder json = manifestStart("failed", metadata);
		property(json, "failure_type", failure.getClass().getName(), true);
		property(json, "failure_message", safeMessage(failure), true);
		property(json, "detailed_output_status",
			detailedStatus(metadata.getOutputMode(), false), true);
		appendBaseUnits(json);
		json.append("\n}\n");
		return json.toString();
	}

	private static StringBuilder manifestStart(String status,
		RunReport.Metadata metadata) {
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		property(json, "schema_version", RunReport.SCHEMA_VERSION, true);
		property(json, "status", status, true);
		property(json, "seed", metadata.getSeed(), true);
		property(json, "output_mode", metadata.getOutputMode().name()
			.toLowerCase(java.util.Locale.ROOT), true);
		json.append("  \"configuration\": {");
		int index = 0;
		for (Map.Entry<String, String> entry
			: metadata.getConfiguration().entrySet()) {
			json.append(index++ == 0 ? "\n" : ",\n");
			json.append("    \"").append(json(entry.getKey())).append("\": \"")
				.append(json(entry.getValue())).append('"');
		}
		json.append("\n  },\n");
		return json;
	}

	private static void appendUnits(StringBuilder json, RunReport report) {
		json.append("  \"units\": {\n");
		List<RunReport.Field> unitFields = new ArrayList<RunReport.Field>();
		for (RunReport.Field field : report.getFields()) {
			if (!field.getUnit().isEmpty()) {
				unitFields.add(field);
			}
		}
		for (int index = 0; index < unitFields.size(); index++) {
			RunReport.Field field = unitFields.get(index);
			json.append("    \"").append(json(field.getName())).append("\": \"")
				.append(json(field.getUnit())).append('"');
			json.append(index + 1 == unitFields.size() ? '\n' : ",\n");
		}
		json.append("  }");
	}

	private static void appendBaseUnits(StringBuilder json) {
		json.append("  \"units\": {\n")
			.append("    \"duration\": \"ms\",\n")
			.append("    \"data\": \"byte\",\n")
			.append("    \"network_usage\": \"byte-ms\",\n")
			.append("    \"energy\": \"W-ms\"\n")
			.append("  }");
	}

	private static void property(StringBuilder json, String name, String value,
		boolean comma) {
		json.append("  \"").append(json(name)).append("\": \"")
			.append(json(value)).append('"').append(comma ? ",\n" : "\n");
	}

	private static void property(StringBuilder json, String name, long value,
		boolean comma) {
		json.append("  \"").append(json(name)).append("\": ").append(value)
			.append(comma ? ",\n" : "\n");
	}

	private static String detailedStatus(RunOutputMode mode, boolean success) {
		if (mode != RunOutputMode.FULL) {
			return "not_requested";
		}
		return success ? "complete" : "incomplete";
	}

	private static String safeMessage(Throwable failure) {
		String message = failure.getMessage();
		return message == null ? failure.getClass().getSimpleName() : message;
	}

	private static String csv(String value) {
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0
			&& value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private static String json(String value) {
		StringBuilder escaped = new StringBuilder();
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			switch (character) {
			case '"': escaped.append("\\\""); break;
			case '\\': escaped.append("\\\\"); break;
			case '\b': escaped.append("\\b"); break;
			case '\f': escaped.append("\\f"); break;
			case '\n': escaped.append("\\n"); break;
			case '\r': escaped.append("\\r"); break;
			case '\t': escaped.append("\\t"); break;
			default:
				if (character < 0x20) {
					escaped.append(String.format(java.util.Locale.ROOT,
						"\\u%04x", (int) character));
				}
				else {
					escaped.append(character);
				}
			}
		}
		return escaped.toString();
	}

	private static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder result = new StringBuilder(hash.length * 2);
			for (byte item : hash) {
				result.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static void cleanup(Path root, Throwable original) {
		try {
			deleteTree(root);
		}
		catch (IOException cleanupError) {
			original.addSuppressed(cleanupError);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (root == null || !Files.exists(root)) {
			return;
		}
		List<Path> paths = new ArrayList<Path>();
		try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
			stream.forEach(paths::add);
		}
		Collections.sort(paths, Comparator.reverseOrder());
		for (Path path : paths) {
			Files.deleteIfExists(path);
		}
	}
}
