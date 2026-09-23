package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import org.cloudbus.cloudsim.core.SimulationEventCounters;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.placement.SimulationMetricsSnapshot;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.port.CloudSimPort;
import org.fog.vmmobile.port.MobileLifecyclePort;
import org.fog.vmmobile.port.MobileStatisticsPort;
import org.fog.vmmobile.port.NetworkSlicePort;
import org.fog.vmmobile.port.SimulationEventLog;
import org.fog.vmmobile.port.SimulationMetricsPort;
import org.fog.vmmobile.port.SimulationOutput;
import org.junit.Test;

/** These tests deliberately do not initialise or reset CloudSim globals. */
public class SimulationServicesTest {

	@Test
	public void injectedServiceGraphRunsAgainstFakePortsWithoutCloudSim() {
		FakeCloudSim cloudSim = new FakeCloudSim();
		RecordingEvents events = new RecordingEvents();
		RecordingMetrics metrics = new RecordingMetrics();
		SimulationServices services = new SimulationServices(cloudSim,
			new NoOpLifecycle(), new NoOpNetworkSlices(), new NoOpStatistics(),
			events, new InMemoryOutput(), metrics, new Random(0L));

		assertSame(cloudSim, services.getCloudSim());
		assertNotNull(services.getTopology());
		assertNotNull(services.getMobility());
		assertNotNull(services.getAssociation());
		assertNotNull(services.getAccessPointAssociation());
		assertNotNull(services.getHandoff());
		assertNotNull(services.getMigration());
		assertNotNull(services.getTupleRouting());
		assertNotNull(services.getResults());

		services.getTopology().createTransportNetwork(
			Collections.emptyList(), 1.0, new java.util.Random(1));
		assertEquals(0, cloudSim.linkCount);
		assertEquals(-1, services.getTupleRouting().childRoute(
			Collections.<Integer>emptyList(), 99));

		SimulationMetricsSnapshot snapshot = services.getResults().captureAndWrite(
			Collections.emptyList(), Collections.emptyList(),
			Collections.emptyList(), Collections.emptyMap());
		assertTrue(events.detailLines > 0);
		assertNotNull(snapshot);
		assertEquals(123.0, metrics.simulationTimeMillis, 0.0);
		assertEquals(456L, metrics.wallTimeMillis);
	}

	private static final class FakeCloudSim implements CloudSimPort {
		private int linkCount;

		@Override
		public double simulationTimeMillis() {
			return 123.0;
		}

		@Override
		public long wallTimeMillis() {
			return 456L;
		}

		@Override
		public Object entityOrNull(int entityId) {
			return null;
		}

		@Override
		public int entityCount() {
			return 0;
		}

		@Override
		public SimulationEventCounters eventCounters() {
			return SimulationEventCounters.empty();
		}

		@Override
		public void addNetworkLink(int sourceId, int destinationId,
			double bandwidth, double latency) {
			linkCount++;
		}
	}

	private static final class RecordingEvents implements SimulationEventLog {
		private int detailLines;

		@Override public void summary(Supplier<String> message) { }
		@Override public void detail(String source, Supplier<String> message) { }
		@Override public void trace(String source, Supplier<String> message) { }

		@Override
		public void detailLine(Supplier<String> message) {
			message.get();
			detailLines++;
		}
	}

	private static final class InMemoryOutput implements SimulationOutput {
		@Override
		public PrintWriter newDetailedPrintWriter(String relativePath,
			boolean append) {
			return new PrintWriter(new StringWriter());
		}
	}

	private static final class NoOpLifecycle implements MobileLifecyclePort {
		@Override public void beginEntry(MobileDevice mobileDevice) { }
		@Override public void awaitAssociation(MobileDevice mobileDevice) { }
		@Override public void activatePeripherals(MobileDevice mobileDevice) { }
		@Override public void disconnectNetwork(MobileDevice mobileDevice) { }
		@Override public void finishMobility(MobileDevice mobileDevice) { }
	}

	private static final class NoOpNetworkSlices implements NetworkSlicePort {
		@Override public void releaseBandwidth(MobileDevice mobileDevice) { }
		@Override public void cancelWirelessTransfers(MobileDevice mobileDevice) { }
		@Override public void startMigrationTransfer(MigrationTransferSpec spec) { }
		@Override
		public NetworkSlicing.MigrationTransferResult completeMigrationTransfer(
			NetworkSlicing.MigrationTransferCompletion completion) {
			return null;
		}
	}

	private static final class NoOpStatistics implements MobileStatisticsPort {
		@Override public void startWithoutConnection(int id, double time) { }
		@Override public void finishWithoutConnection(int id, double time) { }
		@Override public void startWithoutVm(int id, double time) { }
		@Override public void discardOpenIntervals(int id) { }
	}

	private static final class RecordingMetrics implements SimulationMetricsPort {
		private double simulationTimeMillis;
		private long wallTimeMillis;

		@Override
		public SimulationMetricsSnapshot capture(List<FogDevice> serverCloudlets,
			List<ApDevice> accessPoints, List<MobileDevice> mobileDevices,
			double simulationTimeMillis, long wallTimeMillis) {
			this.simulationTimeMillis = simulationTimeMillis;
			this.wallTimeMillis = wallTimeMillis;
			return SimulationMetricsSnapshot.capture(serverCloudlets, accessPoints,
				mobileDevices, new MyStatistics(), new TimeKeeper(),
				simulationTimeMillis, 0L);
		}
	}
}
