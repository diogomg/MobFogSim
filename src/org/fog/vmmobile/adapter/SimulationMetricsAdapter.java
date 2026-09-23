package org.fog.vmmobile.adapter;

import java.util.List;
import java.util.function.Supplier;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.placement.SimulationMetricsSnapshot;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.port.SimulationMetricsPort;

/** Adapts the legacy metrics registries to the run-scoped metrics boundary. */
public final class SimulationMetricsAdapter implements SimulationMetricsPort {
	private final Supplier<MyStatistics> statistics;
	private final Supplier<TimeKeeper> timeKeeper;

	public SimulationMetricsAdapter(MyStatistics statistics,
		TimeKeeper timeKeeper) {
		this(constant(statistics, "Statistics"),
			constant(timeKeeper, "Time keeper"));
	}

	private SimulationMetricsAdapter(Supplier<MyStatistics> statistics,
		Supplier<TimeKeeper> timeKeeper) {
		if (statistics == null || timeKeeper == null) {
			throw new IllegalArgumentException(
				"Metric registry suppliers cannot be null");
		}
		this.statistics = statistics;
		this.timeKeeper = timeKeeper;
	}

	/** Defers compatibility lookups until capture, keeping graph creation pure. */
	public static SimulationMetricsAdapter current() {
		return new SimulationMetricsAdapter(MyStatistics::getInstance,
			TimeKeeper::getInstance);
	}

	@Override
	public SimulationMetricsSnapshot capture(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, List<MobileDevice> mobileDevices,
		double simulationTimeMillis, long wallTimeMillis) {
		MyStatistics currentStatistics = requireValue(statistics.get(),
			"Statistics");
		TimeKeeper currentTimeKeeper = requireValue(timeKeeper.get(),
			"Time keeper");
		long executionTimeMillis = Math.max(0L,
			wallTimeMillis - currentTimeKeeper.getSimulationStartTime());
		return SimulationMetricsSnapshot.capture(serverCloudlets, accessPoints,
			mobileDevices, currentStatistics, currentTimeKeeper,
			simulationTimeMillis, executionTimeMillis);
	}

	private static <T> Supplier<T> constant(T value, String description) {
		requireValue(value, description);
		return () -> value;
	}

	private static <T> T requireValue(T value, String description) {
		if (value == null) {
			throw new IllegalStateException(description + " is not available");
		}
		return value;
	}
}
