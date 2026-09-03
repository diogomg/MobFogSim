package org.fog.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Tuple;
import org.junit.Test;

public class ApplicationContractTest {

	@Test
	public void resultantActuatorTuplesAddressEverySubscriber() {
		Application application = actuatorApplication();
		Tuple input = inputTuple();

		List<Tuple> tuples = application.getResultantTuples(
			"processor", input, 77);

		assertEquals(2, tuples.size());
		assertEquals(11, tuples.get(0).getActuatorId());
		assertEquals(22, tuples.get(1).getActuatorId());
		for (Tuple tuple : tuples) {
			assertEquals(Tuple.ACTUATOR, tuple.getDirection());
			assertEquals(77, tuple.getSourceDeviceId());
			assertEquals(input.getActualTupleId(), tuple.getActualTupleId());
		}
	}

	@Test
	public void periodicActuatorEdgeCreatesOneTuplePerSubscriber() {
		Application application = actuatorApplication();
		AppEdge edge = application.getEdges().get(0);

		List<Tuple> tuples = application.createTuples(edge, 88);

		assertEquals(2, tuples.size());
		assertEquals(11, tuples.get(0).getActuatorId());
		assertEquals(22, tuples.get(1).getActuatorId());
	}

	@Test
	public void actuatorEdgeWithoutSubscribersProducesAnEmptyList() {
		Application application = Application.createApplication("app", 1);
		application.addAppModule("processor", 10);
		application.addAppEdge("processor", "DISPLAY", 1, 1, "UPDATE",
			Tuple.DOWN, AppEdge.ACTUATOR);

		assertEquals(0, application.createTuples(
			application.getEdges().get(0), 1).size());
	}

	@Test
	public void tupleMappingRequiresAnExistingModuleEdgeAndModel() {
		Application application = Application.createApplication("app", 1);
		application.addAppModule("processor", 10);
		application.addAppEdge("processor", "sink", 1, 1, "OUTPUT",
			Tuple.UP, AppEdge.MODULE);

		assertRejected(new Runnable() {
			@Override
			public void run() {
				application.addTupleMapping("missing", "INPUT", "OUTPUT",
					new FractionalSelectivity(1.0, new Random(1L)));
			}
		});
		assertRejected(new Runnable() {
			@Override
			public void run() {
				application.addTupleMapping("processor", "INPUT", "MISSING",
					new FractionalSelectivity(1.0, new Random(1L)));
			}
		});
		assertRejected(new Runnable() {
			@Override
			public void run() {
				application.addTupleMapping("processor", "INPUT", "OUTPUT", null);
			}
		});
	}

	@Test
	public void applicationLoopIsValidatedImmutableAndHasSafeSuccessors() {
		List<String> source = new ArrayList<String>(
			Arrays.asList("sensor", "processor", "actuator"));
		AppLoop loop = new AppLoop(source);
		source.set(1, "changed");

		assertEquals(Arrays.asList("sensor", "processor", "actuator"),
			loop.getModules());
		assertEquals("processor", loop.getNextModuleInLoop("sensor").get());
		assertFalse(loop.getNextModuleInLoop("actuator").isPresent());
		assertFalse(loop.getNextModuleInLoop("missing").isPresent());
		try {
			loop.getModules().add("changed");
			fail("Loop modules must be immutable");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}

		assertRejected(new Runnable() {
			@Override
			public void run() {
				new AppLoop(Arrays.asList("only-one"));
			}
		});
		assertRejected(new Runnable() {
			@Override
			public void run() {
				new AppLoop(Arrays.asList("sensor", " "));
			}
		});
	}

	private static Application actuatorApplication() {
		Application application = Application.createApplication("app", 1);
		application.addAppModule("processor", 10);
		application.addAppEdge("processor", "DISPLAY", 1, 1, "UPDATE",
			Tuple.DOWN, AppEdge.ACTUATOR);
		application.addTupleMapping("processor", "INPUT", "UPDATE",
			new FractionalSelectivity(1.0, new Random(1L)));
		application.getModuleByName("processor").subscribeActuator(11, "UPDATE");
		application.getModuleByName("processor").subscribeActuator(22, "UPDATE");
		return application;
	}

	private static Tuple inputTuple() {
		Tuple tuple = new Tuple("app", 1, Tuple.UP, 1, 1, 1, 1,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
		tuple.setTupleType("INPUT");
		tuple.setActualTupleId(42);
		tuple.setUserId(1);
		return tuple;
	}

	private static void assertRejected(Runnable action) {
		try {
			action.run();
			fail("Expected invalid application configuration to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}
}
