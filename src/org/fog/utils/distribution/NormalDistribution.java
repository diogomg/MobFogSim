package org.fog.utils.distribution;

import java.util.Random;

public class NormalDistribution extends Distribution {

	private double mean;
	private double stdDev;

	public NormalDistribution(double mean, double stdDev) {
		this(mean, stdDev, new Random());
	}

	public NormalDistribution(double mean, double stdDev, Random random) {
		setMean(mean);
		setStdDev(stdDev);
		setRandom(random);
	}

	@Override
	public double getNextValue() {
		return random.nextGaussian() * stdDev + mean;
	}

	public double getMean() {
		return mean;
	}

	public void setMean(double mean) {
		this.mean = mean;
	}

	public double getStdDev() {
		return stdDev;
	}

	public void setStdDev(double stdDev) {
		this.stdDev = stdDev;
	}

	@Override
	public int getDistributionType() {
		return Distribution.NORMAL;
	}

	@Override
	public double getMeanInterTransmitTime() {
		return mean;
	}

}
