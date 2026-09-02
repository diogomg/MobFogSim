package org.fog.vmmobile;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.fog.localization.MobilitySample;

/** Loads and validates ordered SUMO mobility traces. */
final class MobilityDataLoader {

	private static final int TRACE_COLUMN_COUNT = 5;

	private MobilityDataLoader() {
	}

	static List<MobilityTrace> load(Path mobilityDirectory, Path orderManifest,
		int userCount) {
		validateInputs(mobilityDirectory, orderManifest, userCount);
		List<Path> traceFiles = findTraceFiles(mobilityDirectory, orderManifest);
		List<Integer> order = readOrderManifest(orderManifest);

		if (order.size() < userCount) {
			throw new MobilityInputException("Mobility order manifest " + orderManifest
				+ " contains " + order.size() + " entries, but " + userCount
				+ " users were requested");
		}
		for (int position = 0; position < order.size(); position++) {
			int traceIndex = order.get(position);
			if (traceIndex < 0 || traceIndex >= traceFiles.size()) {
				throw new MobilityInputException("Mobility order entry " + (position + 1)
					+ " in " + orderManifest + " references trace index " + traceIndex
					+ ", but valid indexes are 0 through " + (traceFiles.size() - 1));
			}
		}

		List<MobilityTrace> traces = new ArrayList<MobilityTrace>(userCount);
		for (int userIndex = 0; userIndex < userCount; userIndex++) {
			Path traceFile = traceFiles.get(order.get(userIndex));
			traces.add(new MobilityTrace(traceFile, readTrace(traceFile)));
		}
		return Collections.unmodifiableList(traces);
	}

	private static void validateInputs(Path mobilityDirectory, Path orderManifest,
		int userCount) {
		if (mobilityDirectory == null) {
			throw new MobilityInputException("Mobility directory cannot be null");
		}
		if (!Files.isDirectory(mobilityDirectory)) {
			throw new MobilityInputException(
				"Mobility directory does not exist: " + mobilityDirectory);
		}
		if (!Files.isReadable(mobilityDirectory)) {
			throw new MobilityInputException(
				"Mobility directory is not readable: " + mobilityDirectory);
		}
		if (orderManifest == null) {
			throw new MobilityInputException("Mobility order manifest cannot be null");
		}
		if (!hasCsvExtension(orderManifest)) {
			throw new MobilityInputException(
				"Mobility order manifest must use the .csv extension: " + orderManifest);
		}
		if (!Files.isRegularFile(orderManifest) || !Files.isReadable(orderManifest)) {
			throw new MobilityInputException(
				"Mobility order manifest is missing or unreadable: " + orderManifest);
		}
		if (userCount < 0) {
			throw new MobilityInputException("Mobility user count cannot be negative");
		}
	}

	private static List<Path> findTraceFiles(Path mobilityDirectory,
		Path orderManifest) {
		List<Path> traceFiles = new ArrayList<Path>();
		Path normalizedManifest = orderManifest.toAbsolutePath().normalize();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(mobilityDirectory)) {
			for (Path entry : entries) {
				String fileName = entry.getFileName().toString().toLowerCase(Locale.ENGLISH);
				if (Files.isRegularFile(entry) && fileName.endsWith("log.csv")
					&& !entry.toAbsolutePath().normalize().equals(normalizedManifest)) {
					traceFiles.add(entry);
				}
			}
		} catch (IOException error) {
			throw new MobilityInputException(
				"Unable to list mobility directory " + mobilityDirectory, error);
		}

		Collections.sort(traceFiles, new Comparator<Path>() {
			@Override
			public int compare(Path first, Path second) {
				return first.getFileName().toString()
					.compareTo(second.getFileName().toString());
			}
		});
		if (traceFiles.isEmpty()) {
			throw new MobilityInputException("Mobility directory " + mobilityDirectory
				+ " contains no trace files matching *log.csv");
		}
		return traceFiles;
	}

	private static List<Integer> readOrderManifest(Path manifest) {
		List<Integer> order = new ArrayList<Integer>();
		try (BufferedReader reader = Files.newBufferedReader(manifest,
			StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.trim().isEmpty()) {
					throw new MobilityInputException("Empty row in mobility order manifest "
						+ manifest + " at line " + lineNumber);
				}
				String[] values = line.split("\\t", -1);
				for (int column = 0; column < values.length; column++) {
					String value = values[column].trim();
					if (value.isEmpty()) {
						throw new MobilityInputException("Empty trace index in " + manifest
							+ " at line " + lineNumber + ", column " + (column + 1));
					}
					try {
						order.add(Integer.valueOf(value));
					} catch (NumberFormatException error) {
						throw new MobilityInputException("Invalid trace index '" + value
							+ "' in " + manifest + " at line " + lineNumber + ", column "
							+ (column + 1), error);
					}
				}
			}
		} catch (IOException error) {
			throw new MobilityInputException(
				"Unable to read mobility order manifest " + manifest, error);
		}
		if (order.isEmpty()) {
			throw new MobilityInputException(
				"Mobility order manifest is empty: " + manifest);
		}
		return order;
	}

	private static List<MobilitySample> readTrace(Path traceFile) {
		List<MobilitySample> samples = new ArrayList<MobilitySample>();
		try (BufferedReader reader = Files.newBufferedReader(traceFile,
			StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			double previousTime = -1.0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String[] columns = line.split("\\t", -1);
				if (columns.length != TRACE_COLUMN_COUNT) {
					throw new MobilityInputException("Mobility trace " + traceFile
						+ " must contain exactly five tab-separated columns; line "
						+ lineNumber + " contains " + columns.length);
				}

				double[] numericValues = new double[TRACE_COLUMN_COUNT];
				for (int column = 0; column < columns.length; column++) {
					String value = columns[column].trim();
					try {
						numericValues[column] = Double.parseDouble(value);
					} catch (NumberFormatException error) {
						throw new MobilityInputException("Invalid numeric value '" + value
							+ "' in mobility trace " + traceFile + " at line " + lineNumber
							+ ", column " + (column + 1), error);
					}
					if (Double.isNaN(numericValues[column])
						|| Double.isInfinite(numericValues[column])) {
						throw new MobilityInputException("Non-finite value in mobility trace "
							+ traceFile + " at line " + lineNumber + ", column "
							+ (column + 1));
					}
				}

				if (numericValues[0] < 0.0 || numericValues[0] < previousTime) {
					throw new MobilityInputException("Mobility trace times must be"
						+ " non-negative and nondecreasing in " + traceFile + " at line "
						+ lineNumber);
				}
				if (numericValues[4] < 0.0) {
					throw new MobilityInputException("Mobility speed cannot be negative in "
						+ traceFile + " at line " + lineNumber);
				}
				previousTime = numericValues[0];
				samples.add(new MobilitySample(numericValues[0], numericValues[1],
					numericValues[2], numericValues[3], numericValues[4]));
			}
		} catch (IOException error) {
			throw new MobilityInputException(
				"Unable to read mobility trace " + traceFile, error);
		}

		if (samples.isEmpty()) {
			throw new MobilityInputException("Mobility trace is empty: " + traceFile);
		}
		return samples;
	}

	private static boolean hasCsvExtension(Path path) {
		return path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".csv");
	}

	static final class MobilityTrace {
		private final Path source;
		private final List<MobilitySample> samples;

		private MobilityTrace(Path source, List<MobilitySample> samples) {
			this.source = source;
			this.samples = Collections.unmodifiableList(
				new ArrayList<MobilitySample>(samples));
		}

		Path getSource() {
			return source;
		}

		List<MobilitySample> getSamples() {
			return samples;
		}
	}
}
