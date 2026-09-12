package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public class SimulationBenchmarkScenarioTest {

	@Test
	public void matrixContainsThirtyUniqueValidScenarios() {
		List<String> names = SimulationBenchmark.fixtureNames("matrix");
		Set<String> configurations = new HashSet<String>();
		int oneUserScenarios = 0;
		int tenUserScenarios = 0;

		assertEquals(30, names.size());
		assertEquals(30, new HashSet<String>(names).size());
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
			else {
				assertEquals(10, configuration.getMaximumUsers());
				tenUserScenarios++;
			}
		}

		assertEquals(13, oneUserScenarios);
		assertEquals(17, tenUserScenarios);
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
		assertEquals(values("0", "1", "2"), parameterValues(names, 14, null));

		assertEquals(values("70,30", "50,50", "50,30,20",
			"33.333334,33.333333,33.333333"),
			parameterValues(names, 11, "10"));
		assertEquals(values("50,50", "33.34,33.33,33.33"),
			parameterValues(names, 12, "10"));
	}

	@Test
	public void groupsExpandInOrderAndDiscardDuplicates() {
		assertEquals(Arrays.asList("small", "reference", "large"),
			SimulationBenchmark.fixtureNames("baseline"));
		assertEquals(33,
			SimulationBenchmark.fixtureNames("baseline", "matrix", "small",
				"matrix").size());
		assertEquals(34, SimulationBenchmark.fixtureNames("all").size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void unknownFixtureIsRejectedBeforeBenchmarkExecution() {
		SimulationBenchmark.fixtureNames("not-a-fixture");
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
