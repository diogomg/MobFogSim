package org.fog.vmmobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class SimulationBenchmarkScenarioTest {

	@Test
	public void matrixContainsFortyNineUniqueValidScenarios() {
		List<String> names = SimulationBenchmark.fixtureNames("matrix");
		Set<String> configurations = new HashSet<String>();
		int oneUserScenarios = 0;
		int tenUserScenarios = 0;
		int fiftyUserScenarios = 0;

		assertEquals(49, names.size());
		assertEquals(49, new HashSet<String>(names).size());
		for (String name : names) {
			String[] arguments = SimulationBenchmark.fixtureArguments(name);
			assertTrue("Duplicate configuration for " + name,
				configurations.add(Arrays.toString(arguments)));
			SimulationConfig configuration = SimulationConfig.parse(arguments);
			if (configuration.getMaximumUsers() == 1) {
				oneUserScenarios++;
				assertEquals("100", arguments[11]);
				assertEquals("100", arguments[12]);
			}
			else if (configuration.getMaximumUsers() == 10) {
				tenUserScenarios++;
			}
			else {
				assertEquals(50, configuration.getMaximumUsers());
				fiftyUserScenarios++;
			}
		}

		assertEquals(13, oneUserScenarios);
		assertEquals(18, tenUserScenarios);
		assertEquals(18, fiftyUserScenarios);
	}

	@Test
	public void matrixCoversEveryRequestedOption() {
		List<String> names = SimulationBenchmark.fixtureNames("matrix");
		assertEquals(values("0", "1"), parameterValues(names, 0, null));
		assertEquals(values("0", "1"), parameterValues(names, 2, null));
		assertEquals(values("0", "1", "2"), parameterValues(names, 3, null));
		assertEquals(values("0", "1", "2"), parameterValues(names, 6, null));
		assertEquals(values("0", "60"), parameterValues(names, 8, null));
		assertEquals(values("0", "500"), parameterValues(names, 9, null));
		assertEquals(values("0", "1", "2"), parameterValues(names, 10, null));
		assertEquals(values("0", "1"), parameterValues(names, 13, null));
		assertEquals(values("2", "60"), parameterValues(names, 14, null));
		assertEquals(values("0", "1", "2"), parameterValues(names, 15, null));

		assertEquals(values("70,30", "50,50", "50,30,20",
			"33.333334,33.333333,33.333333"),
			parameterValues(names, 11, "10"));
		assertEquals(values("50,50", "33.34,33.33,33.33"),
			parameterValues(names, 12, "10"));
	}

	@Test
	public void transportIsTheSharedBenchmarkSliceScope() {
		for (String name : SimulationBenchmark.fixtureNames("all", "fifty-users")) {
			String[] arguments = SimulationBenchmark.fixtureArguments(name);
			String expectedScope = name.endsWith("scope-end-to-end") ? "2"
				: name.endsWith("scope-wireless") ? "1" : "0";
			assertEquals(name, expectedScope, arguments[10]);
		}
	}

	@Test
	public void allFixturesUseSharedCloudletNetworkDefaults() {
		for (String name : SimulationBenchmark.fixtureNames("all", "fifty-users")) {
			SimulationConfig configuration = SimulationConfig.parse(
				SimulationBenchmark.fixtureArguments(name));
			assertEquals(name, 74, configuration.getMaximumBandwidth());
			assertEquals(name, 3.0, configuration.getCloudletLatency(), 0.0);
		}
	}

	@Test
	public void groupsExpandInOrderAndDiscardDuplicates() {
		assertEquals(Arrays.asList("small", "reference", "large"),
			SimulationBenchmark.fixtureNames("baseline"));
		assertEquals(52,
			SimulationBenchmark.fixtureNames("baseline", "matrix", "small",
				"matrix").size());
		assertEquals(53, SimulationBenchmark.fixtureNames("all").size());
	}

	@Test
	public void fiftyUserGroupClonesEveryTenUserScenario() {
		List<String> names = SimulationBenchmark.fixtureNames("fifty-users");
		assertEquals(18, names.size());
		assertTrue(SimulationBenchmark.fixtureNames("matrix").containsAll(names));
		for (String name : names) {
			assertTrue(name, name.startsWith("u50-"));
			String[] fiftyUsers = SimulationBenchmark.fixtureArguments(name);
			String[] tenUsers = SimulationBenchmark.fixtureArguments(
				"u10-" + name.substring(4));
			assertEquals("50", fiftyUsers[4]);
			tenUsers[4] = "50";
			assertArrayEquals(name, tenUsers, fiftyUsers);
		}
	}

	@Test
	public void numericUserGroupsCloneEveryTenUserScenario() {
		for (int userCount : new int[] {2, 37, 50, 480}) {
			List<String> names = SimulationBenchmark.fixtureNames(
				userCount + "-users");
			assertEquals(18, names.size());
			for (String name : names) {
				String prefix = "u" + userCount + "-";
				assertTrue(name, name.startsWith(prefix));
				String[] scaled = SimulationBenchmark.fixtureArguments(name);
				String[] tenUsers = SimulationBenchmark.fixtureArguments(
					"u10-" + name.substring(prefix.length()));
				assertEquals(Integer.toString(userCount), scaled[4]);
				assertEquals(userCount,
					SimulationConfig.parse(scaled).getMaximumUsers());
				tenUsers[4] = Integer.toString(userCount);
				assertArrayEquals(name, tenUsers, scaled);
			}
		}
	}

	@Test
	public void oneUserGroupUsesEstablishedSingleSliceScenarios() {
		List<String> names = SimulationBenchmark.fixtureNames("1-users");
		assertEquals(13, names.size());
		for (String name : names) {
			assertTrue(name, name.startsWith("u1-"));
			String[] arguments = SimulationBenchmark.fixtureArguments(name);
			assertEquals("1", arguments[4]);
			assertEquals("100", arguments[11]);
			assertEquals("100", arguments[12]);
		}
	}

	@Test
	public void numericAndLegacyFiftyUserGroupsAreAliases() {
		assertEquals(SimulationBenchmark.fixtureNames("fifty-users"),
			SimulationBenchmark.fixtureNames("50-users"));
		assertEquals(18, SimulationBenchmark.fixtureNames(
			"50-users", "fifty-users", "u50-base").size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void zeroUserGroupIsRejected() {
		SimulationBenchmark.fixtureNames("0-users");
	}

	@Test(expected = IllegalArgumentException.class)
	public void userGroupWithLeadingZeroIsRejected() {
		SimulationBenchmark.fixtureNames("050-users");
	}

	@Test(expected = IllegalArgumentException.class)
	public void overflowingUserGroupIsRejected() {
		SimulationBenchmark.fixtureNames("2147483648-users");
	}

	@Test
	public void dynamicFixturesUseTwoSecondDelayExceptSixtySecondVariant() {
		for (String name : SimulationBenchmark.fixtureNames(
			"all", "fifty-users")) {
			String[] arguments = SimulationBenchmark.fixtureArguments(name);
			if ("1".equals(arguments[13])) {
				assertEquals(name, name.endsWith(
					"three-slices-weighted-delay-60") ? "60" : "2",
					arguments[14]);
			}
		}
	}

	@Test
	public void sixtySecondFixtureOnlyChangesWeightedReallocationDelay() {
		String[] weighted = SimulationBenchmark.fixtureArguments(
			"u10-three-slices-weighted");
		String[] delayed = SimulationBenchmark.fixtureArguments(
			"u10-three-slices-weighted-delay-60");

		assertEquals("2", weighted[14]);
		assertEquals("60", delayed[14]);
		assertEquals(60.0, SimulationConfig.parse(delayed)
			.getSlicingConfiguration().getReallocationDelaySeconds(), 0.0);
		weighted[14] = "60";
		assertArrayEquals(weighted, delayed);
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownFixtureIsRejectedBeforeBenchmarkExecution() {
		SimulationBenchmark.fixtureNames("not-a-fixture");
	}

	@Test
	public void sliceBandwidthUsesStableCompactBenchmarkEncoding() {
		Map<Integer, Double> bandwidth = new LinkedHashMap<Integer, Double>();
		bandwidth.put(2, 30.0);
		bandwidth.put(0, 10.5);
		bandwidth.put(1, 0.0);

		assertEquals("0=10.5,1=0.0,2=30.0",
			SimulationBenchmark.formatSliceBandwidth(bandwidth));
	}

	private static Set<String> parameterValues(List<String> names, int index,
		String requiredUserCount) {
		Set<String> found = new HashSet<String>();
		for (String name : names) {
			String[] arguments = SimulationBenchmark.fixtureArguments(name);
			if (requiredUserCount == null
				|| requiredUserCount.equals(arguments[4])) {
				found.add(arguments[index]);
			}
		}
		return found;
	}

	private static Set<String> values(String... items) {
		return new HashSet<String>(Arrays.asList(items));
	}
}
