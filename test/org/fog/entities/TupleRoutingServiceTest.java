package org.fog.entities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.junit.Before;
import org.junit.Test;

public class TupleRoutingServiceTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void directChildIsSelectedWithoutAssumingDenseEntityIds() {
		TupleRoutingService service = new TupleRoutingService();

		assertEquals(91, service.childRoute(Arrays.asList(17, 91, 204), 91));
		assertEquals(-1, service.childRoute(Collections.<Integer>emptyList(), 91));
	}

	@Test
	public void directlyAssociatedActuatorIsSelectedByType() {
		Actuator actuator = new Actuator("display", 1, "app", "DISPLAY");
		Tuple tuple = tuple(7, Tuple.ACTUATOR);
		tuple.setDestModuleName("DISPLAY");
		TupleRoutingService service = new TupleRoutingService();

		TupleRoutingService.ActuatorRoute route = service.actuatorRoute(tuple,
			Collections.singletonList(new Pair<Integer, Double>(actuator.getId(), 3.5)));

		assertNotNull(route);
		assertEquals(actuator.getId(), route.getActuatorId());
		assertEquals(3.5, route.getDelay(), 0.0);
	}

	private static Tuple tuple(int id, int direction) {
		return new Tuple("app", id, direction, 1L, 1, 100L, 1L,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
	}
}
