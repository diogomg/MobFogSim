package org.fog.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UserAllocationNetworkSlicingTest {

	private static final double DELTA = 0.000001;

	@Before
	public void setUp() {
		NetworkSlicing.configure("70,30");
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
	}

	@Test
	public void configuresASeparateUserPercentageForEachSlice() {
		NetworkSlicing.configureUserAllocation("60,40");

		assertEquals(60.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(40.0, NetworkSlicing.getUserAllocationPercentage(1), DELTA);
		assertArrayEquals(new int[] { 6, 4 }, NetworkSlicing.getUserAllocations(10));
	}

	@Test
	public void defaultsToEqualUserDistribution() {
		assertEquals(50.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(50.0, NetworkSlicing.getUserAllocationPercentage(1), DELTA);
		assertArrayEquals(new int[] { 5, 5 }, NetworkSlicing.getUserAllocations(10));
	}

	@Test
	public void largestRemaindersReceiveFractionalUsers() {
		NetworkSlicing.configure("50,30,20");
		NetworkSlicing.configureUserAllocation("50,30,20");

		assertArrayEquals(new int[] { 4, 2, 1 }, NetworkSlicing.getUserAllocations(7));
	}

	@Test
	public void mapsUserIndexesToTheirAllocatedSlices() {
		NetworkSlicing.configure("50,30,20");
		NetworkSlicing.configureUserAllocation("50,30,20");

		assertEquals(0, NetworkSlicing.getSliceForUser(0, 7));
		assertEquals(0, NetworkSlicing.getSliceForUser(3, 7));
		assertEquals(1, NetworkSlicing.getSliceForUser(4, 7));
		assertEquals(1, NetworkSlicing.getSliceForUser(5, 7));
		assertEquals(2, NetworkSlicing.getSliceForUser(6, 7));
	}

	@Test
	public void supportsMoreSlicesThanUsers() {
		NetworkSlicing.configure("25,25,25,25");
		NetworkSlicing.configureUserAllocation("25,25,25,25");

		assertArrayEquals(new int[] { 1, 1, 0, 0 },
			NetworkSlicing.getUserAllocations(2));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsDifferentUserAndSliceCounts() {
		NetworkSlicing.configureUserAllocation("40,30,20,10");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUserPercentagesThatDoNotSumToOneHundred() {
		NetworkSlicing.configureUserAllocation("60,30");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsZeroUserPercentage() {
		NetworkSlicing.configureUserAllocation("100,0");
	}

	@Test
	public void rejectsNonFiniteUserPercentagesWithoutChangingAllocation() {
		NetworkSlicing.configureUserAllocation("60,40");

		try {
			NetworkSlicing.configureUserAllocation("NaN,NaN");
			throw new AssertionError("Expected non-finite allocation to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}

		assertEquals(60.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(40.0, NetworkSlicing.getUserAllocationPercentage(1), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeTotalUsers() {
		NetworkSlicing.getUserAllocations(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsUserIndexOutsidePopulation() {
		NetworkSlicing.getSliceForUser(10, 10);
	}
}
