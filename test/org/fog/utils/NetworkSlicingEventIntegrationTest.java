package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkSlicingEventIntegrationTest {

	private static final int START_FIRST = 7001;
	private static final int START_SECOND = 7002;
	private static final int TRANSFER_COMPLETE = 7003;
	private static final int FIXED_DELAY_COMPLETE = 7004;
	private static final double DELTA = 0.000001;
	private static final double TRANSFER_BYTES = 7.0;
	private static final double FIXED_DELAY_MILLIS = 25.0;

	private FogDevice source;
	private FogDevice destination;
	private MobileDevice first;
	private MobileDevice second;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.configure("70,30");
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
		NetworkUsageMonitor.reset();

		source = cloudlet("source", 1000.0, 1000.0);
		destination = cloudlet("destination", 1000.0, 800.0);
		first = mobile("first", 0);
		second = mobile("second", 0);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
	}

	@Test
	public void cloudSimCompletionEventsAreReplacedWhenRatesChange() {
		TransferHarness harness = new TransferHarness("transferHarness", source,
			destination, first, second);
		CloudSim.terminateSimulation(180.0);

		CloudSim.startSimulation();

		assertEquals(105.0, harness.getTransferCompletionTime(first), DELTA);
		assertEquals(140.0, harness.getTransferCompletionTime(second), DELTA);
		assertEquals(130.0, harness.getCompletionTime(first), DELTA);
		assertEquals(165.0, harness.getCompletionTime(second), DELTA);
		assertEquals(130.0, first.getMigTime(), DELTA);
		assertEquals(130.0, second.getMigTime(), DELTA);
		assertEquals(TRANSFER_BYTES * 2.0,
			NetworkUsageMonitor.getMigrationTransferredBytes(), DELTA);
		assertEquals(TRANSFER_BYTES * 105.0 * 2.0,
			NetworkUsageMonitor.getMigrationUsageByteMilliseconds(), DELTA);
		assertFalse(NetworkSlicing.hasActiveMigrationTransfer(first));
		assertFalse(NetworkSlicing.hasActiveMigrationTransfer(second));
	}

	@Test
	public void abortedTransferDoesNotRecordACompletedMigration() {
		AbortHarness harness = new AbortHarness("abortHarness", source, destination,
			first);
		CloudSim.terminateSimulation(100.0);

		CloudSim.startSimulation();

		assertFalse(NetworkSlicing.hasActiveMigrationTransfer(first));
		assertEquals(0.0,
			NetworkUsageMonitor.getMigrationTransferredBytes(), DELTA);
		assertEquals(0.0,
			NetworkUsageMonitor.getMigrationUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void transportDynamicAllocationIncludesReallocationDelay() {
		NetworkSlicing.setReallocationDelaySeconds(0.1);
		SingleTransferHarness harness = new SingleTransferHarness(
			"singleTransferHarness", source, destination, first);
		CloudSim.terminateSimulation(220.0);

		CloudSim.startSimulation();

		assertEquals(170.0, harness.transferCompletionTime, DELTA);
		assertEquals(100.0, harness.reallocationDelayMillis, DELTA);
		assertEquals(195.0, first.getMigTime(), DELTA);
		assertEquals(TRANSFER_BYTES * 70.0,
			NetworkUsageMonitor.getMigrationUsageByteMilliseconds(), DELTA);
		assertEquals(2L, NetworkSlicing.getReconfigurationCount());
		assertEquals(0.2, NetworkSlicing.getSliceOutageSeconds(), DELTA);
		assertEquals(800.0,
			NetworkSlicing.getReceivedBandwidthBySlice()[0], DELTA);
		assertEquals(0.0,
			NetworkSlicing.getReceivedBandwidthBySlice()[1], DELTA);
	}

	private static final class TransferHarness extends SimEntity {
		private final FogDevice source;
		private final FogDevice destination;
		private final MobileDevice first;
		private final MobileDevice second;
		private final Map<Integer, Double> transferCompletionTimes =
			new HashMap<Integer, Double>();
		private final Map<Integer, Double> completionTimes =
			new HashMap<Integer, Double>();

		private TransferHarness(String name, FogDevice source,
			FogDevice destination, MobileDevice first, MobileDevice second) {
			super(name);
			this.source = source;
			this.destination = destination;
			this.first = first;
			this.second = second;
		}

		@Override
		public void startEntity() {
			scheduleNow(getId(), START_FIRST);
			schedule(getId(), 35.0, START_SECOND);
		}

		@Override
		public void processEvent(SimEvent event) {
			switch (event.getTag()) {
			case START_FIRST:
				start(first);
				break;
			case START_SECOND:
				start(second);
				break;
			case TRANSFER_COMPLETE:
				NetworkSlicing.MigrationTransferResult result =
					NetworkSlicing.completeMigrationTransfer(
					(NetworkSlicing.MigrationTransferCompletion) event.getData());
				if (result != null) {
					MobileDevice completed = result.getMobileDevice();
					transferCompletionTimes.put(completed.getId(), CloudSim.clock());
					schedule(getId(), result.getFixedDelayMillis(),
						FIXED_DELAY_COMPLETE, completed);
				}
				break;
			case FIXED_DELAY_COMPLETE:
				MobileDevice completed = (MobileDevice) event.getData();
				completionTimes.put(completed.getId(), CloudSim.clock());
				break;
			default:
				break;
			}
		}

		@Override
		public void shutdownEntity() {
		}

		private void start(MobileDevice mobileDevice) {
			NetworkSlicing.startMigrationTransfer(
				new MigrationTransferSpec(source, destination, mobileDevice,
					TRANSFER_BYTES, FIXED_DELAY_MILLIS, 0.0, getId(),
					TRANSFER_COMPLETE));
		}

		private double getTransferCompletionTime(MobileDevice mobileDevice) {
			return transferCompletionTimes.get(mobileDevice.getId());
		}

		private double getCompletionTime(MobileDevice mobileDevice) {
			return completionTimes.get(mobileDevice.getId());
		}
	}

	private static final class AbortHarness extends SimEntity {
		private final FogDevice source;
		private final FogDevice destination;
		private final MobileDevice mobileDevice;

		private AbortHarness(String name, FogDevice source, FogDevice destination,
			MobileDevice mobileDevice) {
			super(name);
			this.source = source;
			this.destination = destination;
			this.mobileDevice = mobileDevice;
		}

		@Override
		public void startEntity() {
			NetworkSlicing.startMigrationTransfer(new MigrationTransferSpec(source,
				destination, mobileDevice, TRANSFER_BYTES, FIXED_DELAY_MILLIS,
				0.0, getId(), TRANSFER_COMPLETE));
			schedule(getId(), 35.0, START_FIRST);
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == START_FIRST) {
				NetworkSlicing.releaseBandwidth(mobileDevice);
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}

	private static final class SingleTransferHarness extends SimEntity {
		private final FogDevice source;
		private final FogDevice destination;
		private final MobileDevice mobileDevice;
		private double transferCompletionTime = -1.0;
		private double reallocationDelayMillis = -1.0;

		private SingleTransferHarness(String name, FogDevice source,
			FogDevice destination, MobileDevice mobileDevice) {
			super(name);
			this.source = source;
			this.destination = destination;
			this.mobileDevice = mobileDevice;
		}

		@Override
		public void startEntity() {
			NetworkSlicing.startMigrationTransfer(new MigrationTransferSpec(source,
				destination, mobileDevice, TRANSFER_BYTES, FIXED_DELAY_MILLIS,
				0.0, getId(), TRANSFER_COMPLETE));
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() != TRANSFER_COMPLETE) {
				return;
			}
			NetworkSlicing.MigrationTransferResult result =
				NetworkSlicing.completeMigrationTransfer(
					(NetworkSlicing.MigrationTransferCompletion) event.getData());
			if (result != null) {
				transferCompletionTime = CloudSim.clock();
				reallocationDelayMillis = result.getReallocationDelayMillis();
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}

	private static FogDevice cloudlet(String name, double uplink, double downlink) {
		FogDevice device = new FogDevice(name, 0, 0, 0);
		device.setUplinkBandwidth(uplink);
		device.setDownlinkBandwidth(downlink);
		return device;
	}

	private static MobileDevice mobile(String name, int sliceId) {
		MobileDevice mobile = new MobileDevice(name, 0, 0, 0, 0, 0);
		mobile.setNetworkSliceId(sliceId);
		return mobile;
	}
}
