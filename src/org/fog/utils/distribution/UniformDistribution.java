package org.fog.utils.distribution;

import java.util.Random;

public class UniformDistribution extends Distribution {

	private double min;
	private double max;

	public UniformDistribution(double min, double max) {
		this(min, max, new Random());
	}

	public UniformDistribution(double min, double max, Random random) {
		super();
		if (Double.isNaN(min) || Double.isInfinite(min)
			|| Double.isNaN(max) || Double.isInfinite(max)) {
			throw new IllegalArgumentException("Uniform bounds must be finite");
		}
		if (min > max) {
			throw new IllegalArgumentException(
				"Uniform minimum cannot be greater than maximum");
		}
		setMin(min);
		setMax(max);
		setRandom(random);
	}

	@Override
	public double getNextValue() {
		return getRandom().nextDouble() * (getMax() - getMin()) + getMin();
	}

	public double getMin() {
		return min;
	}

	public void setMin(double min) {
		this.min = min;
	}

	public double getMax() {
		return max;
	}

	public void setMax(double max) {
		this.max = max;
	}

	@Override
	public int getDistributionType() {
		return Distribution.UNIFORM;
	}

	@Override
	public double getMeanInterTransmitTime() {
		return (min + max) / 2;
	}

}
