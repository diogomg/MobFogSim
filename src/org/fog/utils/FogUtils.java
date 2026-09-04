package org.fog.utils;

import java.util.HashMap;
import java.util.Collections;
import java.util.Map;

import org.fog.vmmobile.SimulationContext;
import org.fog.vmmobile.SimulationIdentifiers;

public class FogUtils {
	private static final SimulationIdentifiers LEGACY_IDENTIFIERS =
		new SimulationIdentifiers();
	private static final Map<String, GeoCoverage> LEGACY_APPLICATION_COVERAGE =
		new HashMap<String, GeoCoverage>();

	public static int generateTupleId() {
		return identifiers().nextTupleId();
	}

	public static String getSensorTypeFromSensorName(String sensorName) {
		return sensorName.substring(sensorName.indexOf('-') + 1, sensorName.lastIndexOf('-'));
	}

	public static int generateEntityId() {
		return identifiers().nextEntityId();
	}

	public static int generateActualTupleId() {
		return identifiers().nextActualTupleId();
	}

	public static final int MAX = 10000000;

	/** Returns the application coverage registry for the active run. */
	public static Map<String, GeoCoverage> getApplicationCoverage() {
		SimulationContext context = SimulationContext.currentOrNull();
		return Collections.unmodifiableMap(context == null
			? LEGACY_APPLICATION_COVERAGE
			: context.getTopology().getApplicationCoverage());
	}

	/** Registers application coverage in the current run's topology. */
	public static void registerApplicationCoverage(String applicationId,
		GeoCoverage coverage) {
		SimulationContext context = SimulationContext.currentOrNull();
		if (context == null) {
			if (applicationId == null || applicationId.trim().isEmpty()) {
				throw new IllegalArgumentException("Application ID cannot be empty");
			}
			LEGACY_APPLICATION_COVERAGE.put(applicationId, coverage);
			return;
		}
		context.getTopology().registerApplicationCoverage(applicationId, coverage);
	}

	private static SimulationIdentifiers identifiers() {
		SimulationContext context = SimulationContext.currentOrNull();
		return context == null ? LEGACY_IDENTIFIERS : context.getIdentifiers();
	}
}
