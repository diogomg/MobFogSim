package org.cloudbus.cloudsim.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps append-only simulation output files open and buffered.
 */
public final class BufferedFileManager {
	private static final int BUFFER_SIZE = 64 * 1024;
	private static final int FLUSH_INTERVAL = 1024;
	private static final Map<Path, BufferedWriter> WRITERS =
		new LinkedHashMap<Path, BufferedWriter>();
	private static int recordsSinceFlush;

	private BufferedFileManager() {}

	public static synchronized void writeLine(String filename, String line) {
		writeLines(new File(filename), line);
	}

	public static synchronized void writeLine(File file, String line) {
		writeLines(file, line);
	}

	public static synchronized void writeLines(String filename, String... lines) {
		writeLines(new File(filename), lines);
	}

	public static synchronized void writeLines(File file, String... lines) {
		RunOutputManager output = RunOutputManager.getInstance();
		if (!output.isDetailedOutputEnabled()) {
			return;
		}
		try {
			BufferedWriter writer = getWriter(file);
			for (String line : lines) {
				writer.write(line);
				writer.newLine();
			}
			if (++recordsSinceFlush >= FLUSH_INTERVAL) {
				flushAll();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static synchronized void open(String filename) {
		open(new File(filename));
	}

	public static synchronized void open(File file) {
		if (!RunOutputManager.getInstance().isFullOutputEnabled()) {
			return;
		}
		try {
			getWriter(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static BufferedWriter getWriter(File file) throws IOException {
		Path requested = file.toPath();
		Path path;
		if (requested.isAbsolute()) {
			path = requested.normalize();
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
		}
		else {
			path = RunOutputManager.getInstance().resolve(requested);
		}
		BufferedWriter writer = WRITERS.get(path);
		if (writer == null) {
			writer = new BufferedWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND), BUFFER_SIZE);
			WRITERS.put(path, writer);
		}
		return writer;
	}

	public static synchronized void flushAll() {
		for (BufferedWriter writer : WRITERS.values()) {
			try {
				writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		recordsSinceFlush = 0;
	}

	public static synchronized void closeAll() {
		for (BufferedWriter writer : WRITERS.values()) {
			try {
				writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		WRITERS.clear();
		recordsSinceFlush = 0;
	}
}
