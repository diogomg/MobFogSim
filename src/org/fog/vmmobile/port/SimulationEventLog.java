package org.fog.vmmobile.port;

import java.util.function.Supplier;

/** Output boundary for human-readable run events and reports. */
public interface SimulationEventLog {
	void summary(Supplier<String> message);

	void detailLine(Supplier<String> message);

	void detail(String source, Supplier<String> message);

	void trace(String source, Supplier<String> message);
}
