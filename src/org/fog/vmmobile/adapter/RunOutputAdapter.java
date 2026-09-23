package org.fog.vmmobile.adapter;

import java.io.IOException;
import java.io.PrintWriter;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.vmmobile.port.SimulationOutput;

/** Adapts one run-owned output manager to domain-service output. */
public final class RunOutputAdapter implements SimulationOutput {
	private final RunOutputManager output;

	public RunOutputAdapter(RunOutputManager output) {
		if (output == null) {
			throw new IllegalArgumentException("Run output manager cannot be null");
		}
		this.output = output;
	}

	/** Compatibility factory for callers not yet constructed by a run context. */
	public static RunOutputAdapter current() {
		return new RunOutputAdapter(RunOutputManager.getInstance());
	}

	@Override
	public PrintWriter newDetailedPrintWriter(String relativePath, boolean append)
		throws IOException {
		return output.newDetailedPrintWriter(relativePath, append);
	}
}
