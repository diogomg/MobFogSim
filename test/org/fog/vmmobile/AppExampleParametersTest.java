package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.VmDestinationPolicy;
import org.junit.After;
import org.junit.Test;

public class AppExampleParametersTest {

	private static final double DELTA = 0.000001;

	@After
	public void resetGlobalConfiguration() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setDynamicBorrowing(true);
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);
	}

	@Test
	public void readsParametersInReadmeOrder() {
		AppExample.configureSimulationParameters(new String[] {
			"1", "123", "1", "2", "10", "11", "2", "61.5", "12", "34",
			"1", "10,20,30,40", "40,30,20,10", "0", "1"
		});

		assertTrue(AppExample.isMigrationAble());
		assertEquals(123, AppExample.getSeed());
		assertEquals(1, AppExample.getMigPointPolicy());
		assertEquals(2, AppExample.getMigStrategyPolicy());
		assertEquals(10, AppExample.getMaxSmartThings());
		assertEquals(11, AppExample.getMaxBandwidth());
		assertEquals(2, AppExample.getPolicyReplicaVM());
		assertEquals(61.5, AppExample.getLatencyBetweenCloudlets(), DELTA);
		assertEquals(12, AppExample.getTravelPredicTimeForST());
		assertEquals(34, AppExample.getMobilityPrecitionError());
		assertEquals(NetworkSlicing.WIRELESS_NETWORK, NetworkSlicing.getScope());
		assertEquals(4, NetworkSlicing.getSliceCount());
		assertEquals(40.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(10.0, NetworkSlicing.getPercentage(3), DELTA);
		assertEquals(10.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertEquals(40.0, NetworkSlicing.getUserAllocationPercentage(3), DELTA);
		assertEquals(4, NetworkSlicing.getUserAllocations(10)[3]);
		assertFalse(NetworkSlicing.isDynamicBorrowing());
		assertFalse(VmDestinationPolicy.allowsEdgeServers());
		assertTrue(VmDestinationPolicy.allowsEndDevices());
	}

	@Test
	public void omittedSlicingParametersUseDefaults() {
		AppExample.configureSimulationParameters(new String[] {
			"0", "123", "0", "0", "1", "11", "0", "61", "0", "0"
		});

		assertFalse(AppExample.isMigrationAble());
		assertEquals(NetworkSlicing.END_TO_END_NETWORK, NetworkSlicing.getScope());
		assertEquals(1, NetworkSlicing.getSliceCount());
		assertEquals(100.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(100.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertTrue(NetworkSlicing.isDynamicBorrowing());
		assertTrue(VmDestinationPolicy.allowsEdgeServers());
		assertTrue(VmDestinationPolicy.allowsEndDevices());
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsInvalidDynamicSlicingParameter() {
		AppExample.configureSimulationParameters(new String[] {
			"1", "123", "0", "0", "1", "11", "0", "61", "0", "0",
			"2", "50,50", "70,30", "2", "2"
		});
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsMissingRequiredParameters() {
		AppExample.configureSimulationParameters(new String[] {
			"1", "123", "0", "0", "1", "11", "0", "61", "0"
		});
	}
}
