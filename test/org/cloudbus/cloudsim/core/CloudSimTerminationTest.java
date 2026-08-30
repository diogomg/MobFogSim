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
		assertEquals("failure", onlyOutputLine());
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
}
