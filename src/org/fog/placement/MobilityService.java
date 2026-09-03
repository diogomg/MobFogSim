package org.fog.placement;

import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.localization.MobilitySample;
import org.fog.localization.MobilityTimeline;
import org.fog.vmmobile.MobileUserRegistration;

/** Applies timestamped mobility traces independently of CloudSim event dispatch. */
public final class MobilityService {
	public enum EntryOutcome {
		READY,
		NO_TRACE,
		FINISHED
	}

	/** Starts a scheduled user and consumes all rows due at its entry time. */
	public EntryOutcome enter(MobileDevice mobileDevice, Coordinate coordinate) {
		validate(mobileDevice, coordinate);
		MobileUserRegistration.beginEntry(mobileDevice);
		if (mobileDevice.getMobilityPath().isEmpty()) {
			mobileDevice.setTravelTimeId(0);
			return EntryOutcome.NO_TRACE;
		}
		if (mobileDevice.getTravelTimeId() < 1) {
			mobileDevice.setTravelTimeId(1);
		}
		coordinate.advanceToTime(mobileDevice, mobileDevice.getStartTravelTime());
		return isFinished(mobileDevice) ? EntryOutcome.FINISHED : EntryOutcome.READY;
	}

	/** Consumes all rows due at the supplied CloudSim timestamp. */
	public void advance(MobileDevice mobileDevice, Coordinate coordinate,
		double simulationTime) {
		validate(mobileDevice, coordinate);
		coordinate.advanceToTime(mobileDevice,
			MobilityTimeline.toTraceTime(simulationTime));
	}

	/** Returns whether the current row ends or invalidates this trace. */
	public boolean isFinished(MobileDevice mobileDevice) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		return mobileDevice.getCoord().getCoordX() == -1
			|| mobileDevice.getTravelTimeId() >= mobileDevice.getMobilityPath().size();
	}

	/** Calculates the delay to the next unconsumed row. */
	public double nextUpdateDelay(MobileDevice mobileDevice, double simulationTime) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		if (mobileDevice.getTravelTimeId() < 0
			|| mobileDevice.getTravelTimeId() >= mobileDevice.getMobilityPath().size()) {
			throw new IllegalStateException("Mobile device " + mobileDevice.getName()
				+ " has no next mobility sample");
		}
		MobilitySample next = mobileDevice.getMobilityPath()
			.get(mobileDevice.getTravelTimeId());
		double delay = MobilityTimeline.toSimulationTime(next.getTimeSeconds())
			- simulationTime;
		if (delay < 0.0) {
			throw new IllegalStateException("Mobility timeline for "
				+ mobileDevice.getName() + " moved backwards at sample "
				+ mobileDevice.getTravelTimeId());
		}
		return delay;
	}

	private static void validate(MobileDevice mobileDevice, Coordinate coordinate) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		if (coordinate == null) {
			throw new IllegalArgumentException("Coordinate service cannot be null");
		}
	}
}
