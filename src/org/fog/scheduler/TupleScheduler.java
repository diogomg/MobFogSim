package org.fog.scheduler;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.ResCloudlet;

public class TupleScheduler extends CloudletSchedulerTimeShared {
	private final double mipsPerPe;
	private final int numberOfPes;

	public TupleScheduler(double mips, int numberOfPes) {
		super();
		if (!Double.isFinite(mips) || mips <= 0.0) {
			throw new IllegalArgumentException(
				"Tuple scheduler MIPS must be finite and positive");
		}
		if (numberOfPes <= 0) {
			throw new IllegalArgumentException(
				"Tuple scheduler PE count must be positive");
		}
		this.mipsPerPe = mips;
		this.numberOfPes = numberOfPes;
	}

	/**
	 * Get estimated cloudlet completion time.
	 * 
	 * @param rcl
	 *        the rcl
	 * @param time
	 *        the time
	 * @return the estimated finish time
	 */
	public double getEstimatedFinishTime(ResCloudlet rcl, double time) {
		if (!Double.isFinite(time) || time < 0.0) {
			throw new IllegalArgumentException(
				"Estimation time must be finite and non-negative");
		}
		double capacity = getTotalCurrentAllocatedMipsForCloudlet(rcl, time);
		return time + rcl.getRemainingCloudletLength() / capacity;
	}

	@Override
	public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl,
		double time) {
		if (rcl == null) {
			throw new IllegalArgumentException("Reserved cloudlet cannot be null");
		}
		int allocatedPes = Math.min(numberOfPes, rcl.getNumberOfPes());
		if (allocatedPes <= 0) {
			throw new IllegalArgumentException(
				"Reserved cloudlet must request at least one PE");
		}
		return mipsPerPe * allocatedPes;
	}
}
