package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Paths;

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
		AppExample.setMobilityDirectory(Paths.get("input"));
		AppExample.setMobilityOrderManifest(Paths.get("input", "inputOrder.csv"));
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

	@Test
	public void readsExplicitMobilityInputPaths() {
		AppExample.configureSimulationParameters(new String[] {
			"1", "123", "0", "0", "1", "11", "0", "61", "0", "0",
			"2", "100", "100", "1", "2", "mobility-fixtures",
			"config/user-order.csv"
		});

		assertEquals(Paths.get("mobility-fixtures"), AppExample.getMobilityDirectory());
		assertEquals(Paths.get("config/user-order.csv"),
			AppExample.getMobilityOrderManifest());
	}

	@Test
	public void directoryOnlyUsesItsDefaultOrderManifest() {
		AppExample.configureSimulationParameters(new String[] {
			"1", "123", "0", "0", "1", "11", "0", "61", "0", "0",
			"2", "100", "100", "1", "2", "mobility-fixtures"
		});

		assertEquals(Paths.get("mobility-fixtures", "inputOrder.csv"),
			AppExample.getMobilityOrderManifest());
	}

	@Test
	public void rejectsNonFiniteLatencyWithoutPartiallyApplyingArguments() {
		AppExample.configureSimulationParameters(baselineArguments());
		String[] invalid = changedArguments();

		for (String invalidLatency : new String[] {
			"NaN", "Infinity", "-Infinity"
		}) {
			invalid[7] = invalidLatency;
			assertConfigurationRejected(invalid);
			assertBaselineConfiguration();
		}
	}

	@Test
	public void invalidSlicingPercentagesDoNotApplyEarlierParameters() {
		AppExample.configureSimulationParameters(baselineArguments());
		String[] invalid = changedArguments();
		invalid[12] = "NaN,NaN";

		assertConfigurationRejected(invalid);

		assertBaselineConfiguration();
	}

	@Test
	public void invalidLateParameterLeavesCompleteConfigurationUnchanged() {
		AppExample.configureSimulationParameters(baselineArguments());
		String[] invalid = changedArguments();
		invalid[14] = "9";

		assertConfigurationRejected(invalid);

		assertBaselineConfiguration();
	}

	@Test
	public void rejectsValuesOutsideDocumentedIntegerRanges() {
		String[][] invalidValues = {
			{ "0", "2" },
			{ "1", "0" },
			{ "2", "2" },
			{ "3", "3" },
			{ "4", "0" },
			{ "5", "0" },
			{ "6", "3" },
			{ "8", "-1" },
			{ "9", "-1" },
			{ "10", "3" },
			{ "13", "2" },
			{ "14", "3" }
		};

		for (String[] invalidValue : invalidValues) {
			String[] arguments = baselineArguments();
			arguments[Integer.parseInt(invalidValue[0])] = invalidValue[1];
			assertConfigurationRejected(arguments);
		}
	}

	private static String[] baselineArguments() {
		return new String[] {
			"1", "123", "1", "2", "10", "11", "2", "61.5", "12", "34",
			"1", "60,40", "70,30", "0", "1", "baseline-mobility",
			"baseline/order.csv"
		};
	}

	private static String[] changedArguments() {
		return new String[] {
			"0", "999", "0", "0", "3", "99", "0", "77", "1", "2",
			"0", "50,50", "50,50", "1", "2", "changed-mobility",
			"changed/order.csv"
		};
	}

	private static void assertConfigurationRejected(String[] arguments) {
		try {
			AppExample.configureSimulationParameters(arguments);
			throw new AssertionError("Expected simulation configuration to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}

	private static void assertBaselineConfiguration() {
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
		assertEquals(2, NetworkSlicing.getSliceCount());
		assertEquals(70.0, NetworkSlicing.getPercentage(0), DELTA);
		assertEquals(60.0, NetworkSlicing.getUserAllocationPercentage(0), DELTA);
		assertFalse(NetworkSlicing.isDynamicBorrowing());
		assertFalse(VmDestinationPolicy.allowsEdgeServers());
		assertTrue(VmDestinationPolicy.allowsEndDevices());
		assertEquals(Paths.get("baseline-mobility"), AppExample.getMobilityDirectory());
		assertEquals(Paths.get("baseline/order.csv"),
			AppExample.getMobilityOrderManifest());
	}
}
