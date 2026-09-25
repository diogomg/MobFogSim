package org.cloudbus.cloudsim.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.junit.Before;
import org.junit.Test;

public class CloudSimEventEngineTest {

	@Before
	public void disableLogging() {
		Log.disable();
	}

	@Test
	public void periodicAndPriorityEventsKeepDeterministicSameTimeOrder() {
		CloudSim.init(0, Calendar.getInstance(), false);
		OrderingEntity entity = new OrderingEntity("ordering");

		CloudSim.startSimulation();

		assertEquals(Arrays.asList(
			Integer.valueOf(OrderingEntity.PERIODIC),
			Integer.valueOf(OrderingEntity.PRIORITY_SECOND),
			Integer.valueOf(OrderingEntity.PRIORITY_FIRST),
			Integer.valueOf(OrderingEntity.ORDINARY_FIRST),
			Integer.valueOf(OrderingEntity.ORDINARY_SECOND)),
			entity.getProcessedTags());
	}

	@Test
	public void runnableEntitiesAreProcessedInAscendingEntityIdOrder() {
		CloudSim.init(0, Calendar.getInstance(), false);
		List<String> processingOrder = new ArrayList<String>();
		RecordingEntity lowerId = new RecordingEntity("lower", processingOrder);
		RecordingEntity higherId = new RecordingEntity("higher", processingOrder);
		new ReverseDeliveryEntity("sender", lowerId, higherId);

		CloudSim.startSimulation();

		assertEquals(Arrays.asList("lower", "higher"), processingOrder);
	}

	@Test
	public void cancellationRemovesTheEarliestMatchAndReportsChanges() {
		CloudSim.init(0, Calendar.getInstance(), false);
		try {
			int source = 77;
			int tag = 8801;
			CloudSim.send(source, 0, 5.0, tag, null);
			CloudSim.send(source, 0, 1.0, tag, null);
			CloudSim.send(source, 0, 3.0, tag, null);

			SimEvent cancelled = CloudSim.cancel(source, new PredicateType(tag));

			assertEquals(1.0, cancelled.eventTime(), 0.0);
			assertTrue(CloudSim.cancelAll(source, new PredicateType(tag)));
			assertFalse(CloudSim.cancelAll(source, new PredicateType(tag)));
			assertNull(CloudSim.cancel(source, new PredicateType(tag)));
			assertEquals(0, CloudSim.getFutureEventCount());
		}
		finally {
			CloudSim.finishSimulation();
		}
	}

	@Test
	public void primitiveCountersPreserveSparseAndNegativeTags() {
		CloudSim.init(0, Calendar.getInstance(), false);
		new SparseTagEntity("sparse-tags");

		CloudSim.startSimulation();

		SimulationEventCounters counters = CloudSim.getEventCounters();
		assertEquals(2L, counters.getTotalDispatched());
		assertEquals(1L, counters.getDispatchedForTag(SparseTagEntity.NEGATIVE_TAG));
		assertEquals(1L, counters.getDispatchedForTag(SparseTagEntity.LARGE_TAG));
		assertEquals(2L, counters.getDispatchedForInternalType(SimEvent.SEND));
	}

	private static final class OrderingEntity extends SimEntity {
		private static final int PERIODIC = 8701;
		private static final int ORDINARY_FIRST = 8702;
		private static final int ORDINARY_SECOND = 8703;
		private static final int PRIORITY_FIRST = 8704;
		private static final int PRIORITY_SECOND = 8705;

		private final List<Integer> processedTags = new ArrayList<Integer>();

		private OrderingEntity(String name) {
			super(name);
		}

		@Override
		public void startEntity() {
			schedule(getId(), 1.0, ORDINARY_FIRST);
			schedule(getId(), 1.0, ORDINARY_SECOND);
			scheduleFirst(getId(), 1.0, PRIORITY_FIRST);
			scheduleFirst(getId(), 1.0, PRIORITY_SECOND);
			schedulePeriodic(getId(), 1.0, 1.0, 2.0, PERIODIC);
		}

		@Override
		public void processEvent(SimEvent event) {
			processedTags.add(Integer.valueOf(event.getTag()));
		}

		@Override
		public void shutdownEntity() {}

		private List<Integer> getProcessedTags() {
			return processedTags;
		}
	}

	private static final class RecordingEntity extends SimEntity {
		private static final int RECORD = 8710;
		private final List<String> processingOrder;

		private RecordingEntity(String name, List<String> processingOrder) {
			super(name);
			this.processingOrder = processingOrder;
		}

		@Override
		public void startEntity() {}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == RECORD) {
				processingOrder.add(getName());
			}
		}

		@Override
		public void shutdownEntity() {}
	}

	private static final class ReverseDeliveryEntity extends SimEntity {
		private final RecordingEntity lowerId;
		private final RecordingEntity higherId;

		private ReverseDeliveryEntity(String name, RecordingEntity lowerId,
			RecordingEntity higherId) {
			super(name);
			this.lowerId = lowerId;
			this.higherId = higherId;
		}

		@Override
		public void startEntity() {
			schedule(higherId.getId(), 1.0, RecordingEntity.RECORD);
			schedule(lowerId.getId(), 1.0, RecordingEntity.RECORD);
		}

		@Override
		public void processEvent(SimEvent event) {}

		@Override
		public void shutdownEntity() {}
	}

	private static final class SparseTagEntity extends SimEntity {
		private static final int NEGATIVE_TAG = -7000;
		private static final int LARGE_TAG = 500412;

		private SparseTagEntity(String name) {
			super(name);
		}

		@Override
		public void startEntity() {
			schedule(getId(), 1.0, NEGATIVE_TAG);
			schedule(getId(), 2.0, LARGE_TAG);
		}

		@Override
		public void processEvent(SimEvent event) {}

		@Override
		public void shutdownEntity() {}
	}
}
