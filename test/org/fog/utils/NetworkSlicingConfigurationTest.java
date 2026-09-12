package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkSlicingConfigurationTest {

	private static final double DELTA = 0.000001;

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
	}

	@Test
	public void nullOrBlankConfigurationSelectsOneFullBandwidthSlice() {
		assertEquals(1, NetworkSlicing.getSliceCount());
		assertEquals(100.0, NetworkSlicing.getPercentage(0), DELTA);

		NetworkSlicing.configure("   ");
		assertEquals(1, NetworkSlicing.getSliceCount());
		assertEquals(100.0, NetworkSlicing.getPercentage(0), DELTA);
	}

	@Test
	public void configuresTwoSlicesAndTrimsWhitespace() {
		NetworkSlicing.configure(" 70 , 30 ");

		assertEquals(2, NetworkSlicing.getSliceCount());
		assertEquals(70.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(30.0, NetworkSlicing.getPercentage(1), DELTA);
	}

	@Test
	public void configuresThreeFractionalSlices() {
		NetworkSlicing.configure("50.5,29.5,20");

		assertEquals(3, NetworkSlicing.getSliceCount());
		assertEquals(50.5, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(29.5, NetworkSlicing.getPercentage(1), DELTA);
		assertEquals(20.0, NetworkSlicing.getPercentage(2), DELTA);
	}

	@Test
	public void calculatesFixedSliceBandwidth() {
		NetworkSlicing.configure("70,30");

		assertEquals(700.0, NetworkSlicing.getSliceBandwidth(1000.0, 0), DELTA);
		assertEquals(300.0, NetworkSlicing.getSliceBandwidth(1000.0, 1), DELTA);
		assertEquals(0.0, NetworkSlicing.getSliceBandwidth(0.0, 0), DELTA);
	}

	@Test
	public void rejectsNonFiniteAndNegativePhysicalBandwidths() {
		double[] invalidBandwidths = {
			Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -10.0
		};
		for (double invalidBandwidth : invalidBandwidths) {
			try {
				NetworkSlicing.getSliceBandwidth(invalidBandwidth, 0);
				throw new AssertionError("Expected invalid bandwidth to be rejected");
			}
			catch (IllegalArgumentException expected) {
				// Expected validation failure.
			}
		}
	}

	@Test
	public void physicalLinkUsesTheNarrowerDirectionCapacity() {
		NetworkSlicing.configure("70,30");
		FogDevice source = cloudlet("source", 1000.0, 9000.0);
		FogDevice destination = cloudlet("destination", 9000.0, 800.0);

		assertEquals(560.0, NetworkSlicing.getSliceBandwidth(source, destination, 0), DELTA);
		assertEquals(240.0, NetworkSlicing.getSliceBandwidth(source, destination, 1), DELTA);
		assertEquals(700.0, NetworkSlicing.getSliceBandwidth(source, null, 0), DELTA);
	}

	@Test
	public void dynamicBorrowingFlagRoundTrips() {
		assertTrue(NetworkSlicing.isDynamicBorrowing());
		NetworkSlicing.setDynamicBorrowing(false);
		assertFalse(NetworkSlicing.isDynamicBorrowing());
	}

	@Test
	public void reallocationDelayRoundTripsInSeconds() {
		NetworkSlicing.Configuration configuration =
			NetworkSlicing.parseConfiguration("70,30", "50,50",
				NetworkSlicing.Scope.END_TO_END, NetworkSlicing.Mode.DYNAMIC, 2.5);

		assertEquals(2.5, configuration.getReallocationDelaySeconds(), DELTA);
		NetworkSlicing.applyConfiguration(configuration);
		assertEquals(2.5, NetworkSlicing.getReallocationDelaySeconds(), DELTA);
	}

	@Test
	public void omittedReallocationDelayDefaultsToTwoSeconds() {
		NetworkSlicing.Configuration configuration =
			NetworkSlicing.parseConfiguration("70,30", "50,50",
				NetworkSlicing.Scope.END_TO_END, NetworkSlicing.Mode.DYNAMIC);

		assertEquals(2.0, configuration.getReallocationDelaySeconds(), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeReallocationDelay() {
		NetworkSlicing.parseConfiguration("70,30", "50,50",
			NetworkSlicing.Scope.END_TO_END, NetworkSlicing.Mode.DYNAMIC, -1.0);
	}

	@Test
	public void acceptsOneConfiguredSlice() {
		NetworkSlicing.configure("100");

		assertEquals(1, NetworkSlicing.getSliceCount());
		assertEquals(100.0, NetworkSlicing.getPercentage(0), DELTA);
	}

	@Test
	public void acceptsAnyNumberOfConfiguredSlices() {
		NetworkSlicing.configure("40,30,20,10");

		assertEquals(4, NetworkSlicing.getSliceCount());
		assertEquals(40.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(10.0, NetworkSlicing.getPercentage(3), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsZeroPercentage() {
		NetworkSlicing.configure("100,0");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativePercentage() {
		NetworkSlicing.configure("110,-10");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsPercentageAboveOneHundred() {
		NetworkSlicing.configure("101,1");
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsPercentagesThatDoNotSumToOneHundred() {
		NetworkSlicing.configure("60,30");
	}

	@Test(expected = NumberFormatException.class)
	public void rejectsNonNumericPercentages() {
		NetworkSlicing.configure("fast,slow");
	}

	@Test
	public void rejectsNonFinitePercentagesWithoutChangingConfiguration() {
		NetworkSlicing.configure("70,30");
		NetworkSlicing.configureUserAllocation("60,40");

		assertInvalidConfigurationPreservesState("NaN,NaN");
		assertInvalidConfigurationPreservesState("Infinity,1");
		assertInvalidConfigurationPreservesState("-Infinity,100");
	}

	@Test
	public void combinedSlicingConfigurationIsAtomic() {
		NetworkSlicing.configure("70,30");
		NetworkSlicing.configureUserAllocation("60,40");
		NetworkSlicing.setScope(NetworkSlicing.WIRELESS_NETWORK);
		NetworkSlicing.setDynamicBorrowing(false);

		try {
			NetworkSlicing.parseConfiguration("50,50", "NaN,NaN",
				NetworkSlicing.TRANSPORT_NETWORK, true);
			throw new AssertionError("Expected non-finite user allocation to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}

		assertEquals(2, NetworkSlicing.getSliceCount());
		assertEquals(70.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(30.0, NetworkSlicing.getPercentage(1), DELTA);
		assertEquals(60.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(40.0, NetworkSlicing.getUserAllocationPercentage(1), DELTA);
		assertEquals(NetworkSlicing.WIRELESS_NETWORK, NetworkSlicing.getScope());
		assertFalse(NetworkSlicing.isDynamicBorrowing());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeSliceId() {
		NetworkSlicing.getPercentage(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsSliceIdBeyondConfiguredSlices() {
		NetworkSlicing.configure("50,50");
		NetworkSlicing.getPercentage(2);
	}

	private static FogDevice cloudlet(String name, double uplink, double downlink) {
		FogDevice device = new FogDevice(name, 0, 0, 0);
		device.setUplinkBandwidth(uplink);
		device.setDownlinkBandwidth(downlink);
		return device;
	}

	private static void assertInvalidConfigurationPreservesState(String percentages) {
		try {
			NetworkSlicing.configure(percentages);
			throw new AssertionError("Expected invalid percentages to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
		assertEquals(2, NetworkSlicing.getSliceCount());
		assertEquals(70.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(30.0, NetworkSlicing.getPercentage(1), DELTA);
		assertEquals(60.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(40.0, NetworkSlicing.getUserAllocationPercentage(1), DELTA);
	}
}
