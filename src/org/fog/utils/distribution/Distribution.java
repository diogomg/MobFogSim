package org.fog.utils.distribution;

import java.io.Serializable;
import java.util.Random;

public abstract class Distribution implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int NORMAL = 1;
	public static final int DETERMINISTIC = 2;
	public static final int UNIFORM = 3;

	protected Random random;

	public abstract double getNextValue();

	public Random getRandom() {
		return random;
	}

	public void setRandom(Random random) {
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		this.random = random;
	}

	public abstract int getDistributionType();

	public abstract double getMeanInterTransmitTime();
}
