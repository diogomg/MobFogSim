package org.fog.utils.distribution;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Test;

public class RandomizedDistributionTest {

	private static final double DELTA = 0.0000001;

	@Test
	public void uniformDistributionInitializesItsDefaultRandomGenerator() {
		UniformDistribution distribution = new UniformDistribution(2.0, 5.0);

		for (int i = 0; i < 100; i++) {
			double value = distribution.getNextValue();
			assertTrue(value >= 2.0);
			assertTrue(value < 5.0);
		}
	}

	@Test
	public void randomizedDistributionsAcceptSeededGenerators() {
		Random expectedUniform = new Random(17L);
		UniformDistribution uniform = new UniformDistribution(-4.0, 6.0,
			new Random(17L));
		assertEquals(expectedUniform.nextDouble() * 10.0 - 4.0,
			uniform.getNextValue(), DELTA);

		Random expectedNormal = new Random(29L);
		NormalDistribution normal = new NormalDistribution(3.0, 2.0,
			new Random(29L));
		assertEquals(expectedNormal.nextGaussian() * 2.0 + 3.0,
			normal.getNextValue(), DELTA);
	}

	@Test
	public void uniformDistributionRejectsInvalidBounds() {
		try {
			new UniformDistribution(5.0, 2.0);
			fail("Expected invalid bounds to be rejected");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("minimum"));
		}
	}
}
