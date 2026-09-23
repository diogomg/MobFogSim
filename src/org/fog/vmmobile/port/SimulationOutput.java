package org.fog.vmmobile.port;

import java.io.IOException;
import java.io.PrintWriter;

/** Run-owned detailed-file output boundary. */
public interface SimulationOutput {
	PrintWriter newDetailedPrintWriter(String relativePath, boolean append)
		throws IOException;
}
