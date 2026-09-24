package org.fog.utils.distribution;

public class DeterministicDistribution extends Distribution {
	private static final long serialVersionUID = 1L;

	private double value;

	public DeterministicDistribution(double value) {
		super();
		this.value = value;
	}

	@Override
	public double getNextValue() {
		return value;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	@Override
	public int getDistributionType() {
		return Distribution.DETERMINISTIC;
	}

	@Override
	public double getMeanInterTransmitTime() {
		return value;
	}

}
