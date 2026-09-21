package org.fog.placement;

import java.io.IOException;

/** Persistence port for one terminal run report or failure manifest. */
public interface ResultWriter {

	void write(RunReport report) throws IOException;

	void writeFailure(RunReport.Metadata metadata, Throwable failure)
		throws IOException;

	boolean isTerminal();
}
