package org.cloudbus.cloudsim.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CloudSimTerminationTest {

	private Path outputFile;

	@Before
	public void createOutputFile() throws IOException {
		Log.disable();
		outputFile = Files.createTempFile("cloudsim-termination-", ".txt");
	}

	@After
	public void removeOutputFile() throws IOException {
		BufferedFileManager.closeAll();
		Files.deleteIfExists(outputFile);
	}

	@Test
	public void terminateSimulationStopsAtTheRequestAndRunsCleanup()
		throws IOException {
		CloudSim.init(0, Calendar.getInstance(), false);
		LifecycleEntity entity = new LifecycleEntity("lifecycleEntity", false);
		BufferedFileManager.writeLine(outputFile.toFile(), "graceful");

		double finalClock = CloudSim.startSimulation();

		assertEquals(10.0, finalClock, 0.0);
		assertTrue(entity.isShutdown());
		assertFalse(entity.wasLateEventProcessed());
		assertFalse(CloudSim.running());
		assertEquals(1L, CloudSim.getEventCounters().getTotalDispatched());
		assertEquals(1L, CloudSim.getEventCounters().getQueuedDispatched());
		assertEquals(0L, CloudSim.getEventCounters().getPeriodicDispatched());
		assertEquals(1L, CloudSim.getEventCounters()
			.getDispatchedForTag(LifecycleEntity.STOP_OR_FAIL));
		assertEquals(0L, CloudSim.getEventCounters()
			.getDispatchedForTag(LifecycleEntity.LATE_EVENT));
		assertEquals("graceful", onlyOutputLine());
	}

	@Test
	public void entityFailureStillRunsShutdownAndFlushesOutput()
		throws IOException {
		CloudSim.init(0, Calendar.getInstance(), false);
		LifecycleEntity entity = new LifecycleEntity("failingEntity", true);
		BufferedFileManager.writeLine(outputFile.toFile(), "failure");

		try {
			CloudSim.startSimulation();
			fail("Expected the entity failure to propagate");
		} catch (IllegalStateException expected) {
			assertEquals("simulated entity failure", expected.getMessage());
		}

		assertTrue(entity.isShutdown());
		assertFalse(entity.wasLateEventProcessed());
		assertFalse(CloudSim.running());
		assertEquals(1L, CloudSim.getEventCounters().getTotalDispatched());
		assertEquals(1L, CloudSim.getEventCounters()
			.getDispatchedForTag(LifecycleEntity.STOP_OR_FAIL));
		assertEquals("failure", onlyOutputLine());
	}

	@Test
	public void eventCountersSeparateQueuedAndPeriodicDispatches() {
		CloudSim.init(0, Calendar.getInstance(), false);
		new CountingEntity("countingEntity");

		CloudSim.startSimulation();

		SimulationEventCounters counters = CloudSim.getEventCounters();
		assertEquals(5L, counters.getTotalDispatched());
		assertEquals(2L, counters.getQueuedDispatched());
		assertEquals(3L, counters.getPeriodicDispatched());
		assertEquals(5L, counters.getDispatchedForInternalType(SimEvent.SEND));
		assertEquals(3L, counters.getDispatchedForTag(CountingEntity.PERIODIC));
		assertEquals(1L, counters.getDispatchedForTag(CountingEntity.ONCE));
		assertEquals(1L, counters.getDispatchedForTag(CountingEntity.STOP));
		try {
			counters.getDispatchedByTag().put(Integer.valueOf(1), Long.valueOf(1L));
			fail("Counter maps must be immutable");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}

		CloudSim.init(0, Calendar.getInstance(), false);
		assertEquals(0L, CloudSim.getEventCounters().getTotalDispatched());
		CloudSim.finishSimulation();
	}

	private String onlyOutputLine() throws IOException {
		List<String> lines = Files.readAllLines(outputFile,
			Charset.defaultCharset());
		assertEquals(1, lines.size());
		return lines.get(0);
	}

	private static final class LifecycleEntity extends SimEntity {
		private static final int STOP_OR_FAIL = 8101;
		private static final int LATE_EVENT = 8102;

		private final boolean fail;
		private boolean shutdown;
		private boolean lateEventProcessed;

		private LifecycleEntity(String name, boolean fail) {
			super(name);
			this.fail = fail;
		}

		@Override
		public void startEntity() {
			schedule(getId(), 10.0, STOP_OR_FAIL);
			schedule(getId(), 100.0, LATE_EVENT);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == STOP_OR_FAIL) {
				if (fail) {
					throw new IllegalStateException("simulated entity failure");
				}
				CloudSim.terminateSimulation();
			}
			else if (event.getTag() == LATE_EVENT) {
				lateEventProcessed = true;
			}
		}

		@Override
		public void shutdownEntity() {
			shutdown = true;
		}

		private boolean isShutdown() {
			return shutdown;
		}

		private boolean wasLateEventProcessed() {
			return lateEventProcessed;
		}
	}

	private static final class CountingEntity extends SimEntity {
		private static final int PERIODIC = 8201;
		private static final int ONCE = 8202;
		private static final int STOP = 8203;

		private CountingEntity(String name) {
			super(name);
		}

		@Override
		public void startEntity() {
			CloudSim.sendPeriodic(getId(), getId(), 1.0, 1.0, 4.0,
				PERIODIC, null);
			schedule(getId(), 2.5, ONCE);
			schedule(getId(), 3.5, STOP);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == STOP) {
				CloudSim.terminateSimulation();
			}
		}

		@Override
		public void shutdownEntity() {
			// Nothing to release.
		}
	}
}
