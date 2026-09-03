package org.fog.application.selectivity;

import java.util.function.DoubleSupplier;

import org.cloudbus.cloudsim.core.CloudSim;

/**
 * Generates an output tuple for every input tuple according to a bursty model.
 * During high burst period, all input tuples result in an output tuple. During
 * low burst period, no input tuples result in an output tuple.
 * 
 * @author Harshit Gupta
 */
public class BurstySelectivity implements SelectivityModel {

	/**
	 * Duration of the low burst period
	 */
	private final double burstLowPeriod;

	/**
	 * Duration of the high burst period
	 */
	private final double burstHighPeriod;

	/**
	 * First instance of the start of high burst period, using which subsequent
	 * burst periods will be calculated.
	 */
	private final double firstHighTime;
	private final DoubleSupplier clock;

	public BurstySelectivity(double burstLowPeriod, double burstHighPeriod, double firstHighTime) {
		this(burstLowPeriod, burstHighPeriod, firstHighTime,
			new DoubleSupplier() {
				@Override
				public double getAsDouble() {
					return CloudSim.clock();
				}
			});
	}

	/** Creates a model with an injected clock for deterministic testing. */
	public BurstySelectivity(double burstLowPeriod, double burstHighPeriod,
		double firstHighTime, DoubleSupplier clock) {
		requireNonNegativeFinite(burstLowPeriod, "Low burst period");
		requireNonNegativeFinite(burstHighPeriod, "High burst period");
		requireNonNegativeFinite(firstHighTime, "First high-burst time");
		if (clock == null) {
			throw new IllegalArgumentException("Burst clock cannot be null");
		}
		double totalPeriod = burstLowPeriod + burstHighPeriod;
		if (!Double.isFinite(totalPeriod) || totalPeriod <= 0.0) {
			throw new IllegalArgumentException(
				"The total burst period must be finite and positive");
		}
		this.burstLowPeriod = burstLowPeriod;
		this.burstHighPeriod = burstHighPeriod;
		this.firstHighTime = firstHighTime;
		this.clock = clock;
	}

	/**
	 * If the current time falls in the high burst period of the specified burst
	 * model, an output tuple is generated for the incoming tuple.
	 */
	@Override
	public boolean canSelect() {
		double time = clock.getAsDouble() - getFirstHighTime();
		if (time < 0.0 || getBurstHighPeriod() == 0.0) {
			return false;
		}
		double burstPeriod = getBurstHighPeriod() + getBurstLowPeriod();
		double phase = time % burstPeriod;
		return phase < getBurstHighPeriod();
	}

	/**
	 * The mean tuple generation rate is the fraction of high burst period in
	 * the total burst period.
	 */
	@Override
	public double getMeanRate() {
		return getBurstHighPeriod() / (getBurstHighPeriod() + getBurstLowPeriod());
	}

	/**
	 * Maximum tuple generation rate equals 1 (when the burst is high)
	 */
	@Override
	public double getMaxRate() {
		return 1;
	}

	public double getBurstLowPeriod() {
		return burstLowPeriod;
	}

	public double getBurstHighPeriod() {
		return burstHighPeriod;
	}

	public double getFirstHighTime() {
		return firstHighTime;
	}

	private static void requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
	}

}
