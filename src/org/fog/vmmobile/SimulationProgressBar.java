package org.fog.vmmobile;

import java.io.PrintStream;

/** Renders throttled simulation-time progress without affecting simulation events. */
final class SimulationProgressBar {
	private static final int WIDTH = 40;

	private final PrintStream output;
	private double finalSimulationTime;
	private int lastPercentage = -1;
	private boolean started;
	private boolean closed;

	SimulationProgressBar(PrintStream output) {
		if (output == null) {
			throw new IllegalArgumentException("Progress output cannot be null");
		}
		this.output = output;
	}

	void start(double finalSimulationTime) {
		if (!Double.isFinite(finalSimulationTime) || finalSimulationTime <= 0.0) {
			throw new IllegalArgumentException(
				"Final simulation time must be finite and positive");
		}
		this.finalSimulationTime = finalSimulationTime;
		started = true;
		closed = false;
		lastPercentage = -1;
		update(0.0);
	}

	void update(double simulationTime) {
		if (!started || closed || !Double.isFinite(simulationTime)) {
			return;
		}
		int percentage = (int) Math.floor(simulationTime / finalSimulationTime * 100.0);
		percentage = Math.max(0, Math.min(100, percentage));
		if (percentage <= lastPercentage) {
			return;
		}
		render(percentage);
		lastPercentage = percentage;
		if (percentage == 100) {
			finishLine();
		}
	}

	void complete() {
		if (started && !closed) {
			render(100);
			lastPercentage = 100;
			finishLine();
		}
	}

	void close() {
		if (started && !closed) {
			finishLine();
		}
	}

	private void render(int percentage) {
		int completed = percentage * WIDTH / 100;
		StringBuilder line = new StringBuilder(WIDTH + 32);
		line.append('\r').append("Simulation progress: [");
		for (int position = 0; position < WIDTH; position++) {
			line.append(position < completed ? '#' : '-');
		}
		line.append("] ");
		if (percentage < 10) {
			line.append("  ");
		}
		else if (percentage < 100) {
			line.append(' ');
		}
		line.append(percentage).append('%');
		output.print(line.toString());
		output.flush();
	}

	private void finishLine() {
		output.println();
		output.flush();
		closed = true;
	}
}
