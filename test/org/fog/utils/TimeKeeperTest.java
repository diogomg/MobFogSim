package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.Tuple;
import org.junit.Before;
import org.junit.Test;

public class TimeKeeperTest {

	private static final double DELTA = 0.0000001;
	private TimeKeeper timeKeeper;

	@Before
	public void resetTimeKeeper() {
		CloudSim.init(0, Calendar.getInstance(), false);
		timeKeeper = TimeKeeper.getInstance();
		timeKeeper.getTupleIdToCpuStartTime().clear();
		timeKeeper.getTupleTypeToAverageCpuTime().clear();
		timeKeeper.getTupleTypeToExecutedTupleCount().clear();
	}

	@Test
	public void cpuAverageUsesAndRetainsTheActualExecutionCount() {
		completeTuple(1, "TEMPERATURE", 10.0);
		completeTuple(2, "TEMPERATURE", 20.0);
		completeTuple(3, "TEMPERATURE", 30.0);

		assertEquals(20.0,
			timeKeeper.getTupleTypeToAverageCpuTime().get("TEMPERATURE"), DELTA);
		assertEquals(Integer.valueOf(3),
			timeKeeper.getTupleTypeToExecutedTupleCount().get("TEMPERATURE"));
	}

	private void completeTuple(int id, String tupleType, double duration) {
		Tuple tuple = new Tuple("app", id, Tuple.UP, 100, 1, 100, 100,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
		tuple.setTupleType(tupleType);
		timeKeeper.getTupleIdToCpuStartTime().put(id, -duration);
		timeKeeper.tupleEndedExecution(tuple);
	}
}
