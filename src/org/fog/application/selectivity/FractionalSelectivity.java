package org.fog.application.selectivity;

import java.util.Random;

import org.fog.vmmobile.SimulationContext;

/**
 * Generates an output tuple for an incoming input tuple with a fixed
 * probability
 * 
 * @author Harshit Gupta
 */
public class FractionalSelectivity implements SelectivityModel {

	/**
	 * The fixed probability of output tuple creation per incoming input tuple
	 */
	private double selectivity;
	private final Random random;

	/**
	 * Defines the selectivity value
	 * 
	 * @param selectivity
	 */
	public FractionalSelectivity(double selectivity) {
		this(selectivity, contextRandom());
	}

	/** Creates a model with an explicitly injected reproducible stream. */
	public FractionalSelectivity(double selectivity, Random random) {
		if (random == null) {
			throw new IllegalArgumentException("Selectivity random stream cannot be null");
		}
		this.random = random;
		this.selectivity = requireSelectivity(selectivity);
	}

	/**
	 * Gets the selectivity.
	 * 
	 * @return selectivity value
	 */
	public double getSelectivity() {
		return selectivity;
	}

	/**
	 * Sets the selectivity.
	 * 
	 * @param selectivity
	 *        value
	 */
	public void setSelectivity(double selectivity) {
		this.selectivity = requireSelectivity(selectivity);
	}

	private static double requireSelectivity(double selectivity) {
		if (!Double.isFinite(selectivity)
			|| selectivity < 0.0 || selectivity > 1.0) {
			throw new IllegalArgumentException(
				"Selectivity must be finite and between 0 and 1");
		}
		return selectivity;
	}

	/**
	 * Randomly defines if the tuple will be select based its selectivity rate
	 * 
	 * @return true in case the random value
	 */
	@Override
	public boolean canSelect() {
		return random.nextDouble() < getSelectivity();
	}

	/**
	 * Gets the average rate of tuple generation which is fixed by the
	 * selectivity value
	 * 
	 * @return selectivity value
	 */
	@Override
	public double getMeanRate() {
		return getSelectivity();
	}

	/**
	 * Gets the maximum rate of tuple generation which is fixed by the
	 * selectivity value
	 * 
	 * @return selectivity value
	 */
	@Override
	public double getMaxRate() {
		return getSelectivity();
	}

	private static Random contextRandom() {
		SimulationContext context = SimulationContext.currentOrNull();
		return context == null ? new Random()
			: context.random("fractional-selectivity");
	}
}
