package org.fog.vmmobile.adapter;

import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.port.MobileStatisticsPort;
import org.fog.vmmobile.port.SimulationEventLog;
import org.fog.vmmobile.port.SimulationOutput;

/**
 * Named quarantine for static compatibility lookups during the strangler
 * migration to constructor-injected run services.
 */
public final class LegacySimulationAdapters {
	private LegacySimulationAdapters() {
	}

	public static MobileStatisticsPort statistics() {
		return MyStatisticsAdapter.current();
	}

	public static SimulationEventLog events() {
		return SimulationEventSink.current();
	}

	public static SimulationOutput output() {
		return RunOutputAdapter.current();
	}
}
