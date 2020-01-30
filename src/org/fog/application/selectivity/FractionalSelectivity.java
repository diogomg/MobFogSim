package org.fog.application.selectivity;

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
	double selectivity;

	/**
	 * Defines the selectivity value
	 * 
	 * @param selectivity
	 */
	public FractionalSelectivity(double selectivity) {
		setSelectivity(selectivity);
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
		this.selectivity = selectivity;
	}

	/**
	 * Randomly defines if the tuple will be select based its selectivity rate
	 * 
	 * @return true in case the random value
	 */
	@Override
	public boolean canSelect() {
		if (Math.random() < getSelectivity()) // if the probability condition is satisfied
			return true;
		return false;
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
}
