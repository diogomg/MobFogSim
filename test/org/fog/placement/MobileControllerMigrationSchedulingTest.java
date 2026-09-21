package org.fog.placement;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MobileControllerMigrationSchedulingTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@After
	public void resetControllerState() {
		MobileController.resetRunState();
	}

	@Test
	public void excludesEmptyServersAndUsesCloudSimEntityOrder() {
		FogDevice firstCreated = new FogDevice("first", 0, 0, 0);
		FogDevice empty = new FogDevice("empty", 0, 0, 1);
		FogDevice lastCreated = new FogDevice("last", 0, 0, 2);
		firstCreated.associateMobileDevice(mobileDevice("first-user", 0));
		lastCreated.associateMobileDevice(mobileDevice("last-user", 1));

		List<FogDevice> targets = MobileController.migrationDecisionTargets(
			Arrays.asList(lastCreated, empty, firstCreated));

		assertEquals(Arrays.asList(firstCreated, lastCreated), targets);
	}

	@Test
	public void oneControllerTickEvaluatesEachOccupiedServerOnce() {
		List<Integer> order = new ArrayList<Integer>();
		RecordingFogDevice first = new RecordingFogDevice("first", 0, order);
		RecordingFogDevice empty = new RecordingFogDevice("empty", 1, order);
		RecordingFogDevice last = new RecordingFogDevice("last", 2, order);
		first.associateMobileDevice(mobileDevice("first-user", 0));
		last.associateMobileDevice(mobileDevice("last-user", 1));
		MobileController.setServerCloudlets(Arrays.<FogDevice>asList(
			last, empty, first));
		MobileController controller = new MobileController();

		controller.evaluateMigrationDecisions();

		assertEquals(Arrays.asList(first.getId(), last.getId()), order);
		assertEquals(0, empty.getEvaluationCount());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void decisionTargetSnapshotIsImmutable() {
		FogDevice server = new FogDevice("server", 0, 0, 0);
		server.associateMobileDevice(mobileDevice("user", 0));
		MobileController.migrationDecisionTargets(
			Collections.singletonList(server)).clear();
	}

	private static MobileDevice mobileDevice(String name, int id) {
		return new MobileDevice(name, 0, 0, id, 0, 0);
	}

	private static final class RecordingFogDevice extends FogDevice {
		private final List<Integer> order;
		private int evaluationCount;

		private RecordingFogDevice(String name, int logicalId,
			List<Integer> order) {
			super(name, 0, 0, logicalId);
			this.order = order;
		}

		@Override
		public void evaluateMigrationDecisions() {
			evaluationCount++;
			order.add(getId());
		}

		private int getEvaluationCount() {
			return evaluationCount;
		}
	}
}
