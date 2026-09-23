package org.fog.vmmobile.port;

/** Timing observations needed by association and migration workflows. */
public interface MobileStatisticsPort {
	void startWithoutConnection(int mobileDeviceId, double timeMillis);

	void finishWithoutConnection(int mobileDeviceId, double timeMillis);

	void startWithoutVm(int mobileDeviceId, double timeMillis);

	void discardOpenIntervals(int mobileDeviceId);
}
