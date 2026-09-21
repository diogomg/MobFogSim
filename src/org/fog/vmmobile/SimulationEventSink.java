package org.fog.vmmobile;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.function.Supplier;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;

/**
 * Run-scoped boundary for human-readable simulation events.
 *
 * <p>Summary messages are written to the caller-provided console. Detail and
 * trace messages are written only for full-output runs. Suppliers are evaluated
 * only when their level is enabled.</p>
 */
public final class SimulationEventSink implements AutoCloseable {

	public enum Level {
		OFF,
		SUMMARY,
		DETAIL,
		TRACE
	}

	private static final SimulationEventSink OFF =
		new SimulationEventSink(Level.OFF, null, null, false);
	private static volatile SimulationEventSink current = OFF;

	private final Level level;
	private final PrintStream summaryOutput;
	private final PrintStream detailedOutput;
	private final boolean ownsDetailedOutput;
	private boolean closed;

	private SimulationEventSink(Level level, PrintStream summaryOutput,
		PrintStream detailedOutput, boolean ownsDetailedOutput) {
		this.level = level;
		this.summaryOutput = summaryOutput;
		this.detailedOutput = detailedOutput;
		this.ownsDetailedOutput = ownsDetailedOutput;
	}

	/** Opens a sink whose enabled levels follow the configured output mode. */
	public static SimulationEventSink open(RunOutputManager outputManager,
		PrintStream summaryOutput) {
		if (outputManager == null || summaryOutput == null) {
			throw new IllegalArgumentException(
				"Output manager and summary stream cannot be null");
		}
		RunOutputMode mode = outputManager.getOutputMode();
		if (mode == RunOutputMode.NONE) {
			return OFF;
		}
		if (mode == RunOutputMode.SUMMARY) {
			return new SimulationEventSink(Level.SUMMARY, summaryOutput, null, false);
		}
		try {
			return new SimulationEventSink(Level.TRACE, summaryOutput,
				outputManager.newFullPrintStream("out.txt"), true);
		}
		catch (IOException error) {
			throw new UncheckedIOException(
				"Could not open the simulation event output", error);
		}
	}

	public static SimulationEventSink current() {
		return current;
	}

	static void use(SimulationEventSink sink) {
		if (sink == null) {
			throw new IllegalArgumentException("Simulation event sink cannot be null");
		}
		current = sink;
	}

	static void reset() {
		current = OFF;
	}

	public Level getLevel() {
		return level;
	}

	public boolean isEnabled(Level requestedLevel) {
		return !closed && requestedLevel != null && requestedLevel != Level.OFF
			&& level.ordinal() >= requestedLevel.ordinal();
	}

	public void summary(Supplier<String> message) {
		writeRaw(Level.SUMMARY, message);
	}

	/** Writes a pre-rendered human report line to the detailed output. */
	public void detailLine(Supplier<String> message) {
		writeRaw(Level.DETAIL, message);
	}

	public void detail(String source, Supplier<String> message) {
		writeEvent(Level.DETAIL, source, message);
	}

	public void trace(String source, Supplier<String> message) {
		writeEvent(Level.TRACE, source, message);
	}

	private void writeEvent(Level requestedLevel, String source,
		Supplier<String> message) {
		if (!isEnabled(requestedLevel)) {
			return;
		}
		if (source == null || source.trim().isEmpty() || message == null) {
			throw new IllegalArgumentException(
				"Simulation event source and message cannot be empty");
		}
		write(requestedLevel, "Clock: " + CloudSim.clock() + " - " + source
			+ ": " + message.get());
	}

	private void writeRaw(Level requestedLevel, Supplier<String> message) {
		if (!isEnabled(requestedLevel)) {
			return;
		}
		if (message == null) {
			throw new IllegalArgumentException("Simulation event message cannot be null");
		}
		write(requestedLevel, message.get());
	}

	private synchronized void write(Level requestedLevel, String message) {
		PrintStream output = requestedLevel == Level.SUMMARY
			? summaryOutput : detailedOutput;
		if (output != null) {
			output.println(message);
		}
	}

	/** Flushes and closes the detailed stream before atomic result publication. */
	public synchronized void finish() throws IOException {
		if (closed) {
			return;
		}
		if (summaryOutput != null) {
			summaryOutput.flush();
		}
		if (detailedOutput != null) {
			detailedOutput.flush();
			if (detailedOutput.checkError()) {
				throw new IOException("Could not complete the simulation event output");
			}
			if (ownsDetailedOutput) {
				detailedOutput.close();
			}
		}
		closed = true;
	}

	@Override
	public void close() {
		try {
			finish();
		}
		catch (IOException error) {
			throw new IllegalStateException(error.getMessage(), error);
		}
	}
}
