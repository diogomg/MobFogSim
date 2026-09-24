package org.fog.vmmobile.adapter;

import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.port.MobileStatisticsPort;

/** Adapts one run-owned statistics collector to orchestration services. */
public final class MyStatisticsAdapter implements MobileStatisticsPort {
	private final MyStatistics statistics;

	public MyStatisticsAdapter(MyStatistics statistics) {
		if (statistics == null) {
			throw new IllegalArgumentException("Statistics cannot be null");
		}
		this.statistics = statistics;
	}

	/** Compatibility factory for entities not yet constructed by a run context. */
	public static MyStatisticsAdapter current() {
		return new MyStatisticsAdapter(MyStatistics.getInstance());
	}

	@Override
	public void startWithoutConnection(int mobileDeviceId, double timeMillis) {
		statistics.startWithoutConnection(mobileDeviceId, timeMillis);
	}

	@Override
	public void finishWithoutConnection(int mobileDeviceId, double timeMillis) {
		statistics.finalWithoutConnection(mobileDeviceId, timeMillis);
	}

	@Override
	public void startWithoutVm(int mobileDeviceId, double timeMillis) {
		statistics.startWithoutVmTime(mobileDeviceId, timeMillis);
	}

	@Override
	public void discardOpenIntervals(int mobileDeviceId) {
		statistics.discardOpenIntervals(mobileDeviceId);
	}
}
