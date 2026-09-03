package org.fog.application.selectivity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;
import java.util.function.DoubleSupplier;

import org.junit.Test;

public class SelectivityModelTest {
	private static final double DELTA = 0.0000001;

	@Test
	public void equalSeedsProduceEqualFractionalDecisions() {
		FractionalSelectivity first = new FractionalSelectivity(0.37,
			new Random(12345L));
		FractionalSelectivity second = new FractionalSelectivity(0.37,
			new Random(12345L));

		for (int index = 0; index < 1_000; index++) {
			assertEquals(first.canSelect(), second.canSelect());
		}
	}

	@Test
	public void fractionalBoundariesAreExact() {
		FractionalSelectivity never = new FractionalSelectivity(0.0,
			new Random(1L));
		FractionalSelectivity always = new FractionalSelectivity(1.0,
			new Random(1L));
		for (int index = 0; index < 100; index++) {
			assertFalse(never.canSelect());
			assertTrue(always.canSelect());
		}
		assertEquals(0.0, never.getMeanRate(), DELTA);
		assertEquals(1.0, always.getMaxRate(), DELTA);
	}

	@Test
	public void fractionalSelectivityRejectsInvalidProbabilities() {
		for (final double probability : new double[] {
			-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY,
			Double.NEGATIVE_INFINITY
		}) {
			assertRejected(new Runnable() {
				@Override
				public void run() {
					new FractionalSelectivity(probability, new Random(1L));
				}
			});
		}
	}

	@Test
	public void burstStartsAtFirstHighTimeAndUsesHalfOpenHighIntervals() {
		final double[] time = new double[] { 9.0 };
		BurstySelectivity selectivity = new BurstySelectivity(3.0, 2.0,
			10.0, new DoubleSupplier() {
				@Override
				public double getAsDouble() {
					return time[0];
				}
			});

		assertFalse(selectivity.canSelect());
		time[0] = 10.0;
		assertTrue(selectivity.canSelect());
		time[0] = 11.999;
		assertTrue(selectivity.canSelect());
		time[0] = 12.0;
		assertFalse(selectivity.canSelect());
		time[0] = 15.0;
		assertTrue(selectivity.canSelect());
		assertEquals(0.4, selectivity.getMeanRate(), DELTA);
	}

	@Test
	public void burstConfigurationRejectsInvalidCyclesAndClock() {
		for (final double[] values : new double[][] {
			{ -1.0, 1.0, 0.0 }, { 1.0, -1.0, 0.0 },
			{ 0.0, 0.0, 0.0 }, { Double.NaN, 1.0, 0.0 },
			{ Double.MAX_VALUE, Double.MAX_VALUE, 0.0 },
			{ 1.0, 1.0, Double.POSITIVE_INFINITY }
		}) {
			assertRejected(new Runnable() {
				@Override
				public void run() {
					new BurstySelectivity(values[0], values[1], values[2]);
				}
			});
		}
		assertRejected(new Runnable() {
			@Override
			public void run() {
				new BurstySelectivity(1.0, 1.0, 0.0, null);
			}
		});
	}

	private static void assertRejected(Runnable action) {
		try {
			action.run();
			fail("Expected selectivity configuration to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}
}
