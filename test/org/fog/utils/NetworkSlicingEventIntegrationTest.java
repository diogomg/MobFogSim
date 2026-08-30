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
	private static final double DELTA = 0.000001;

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
	}

	@Test
	public void cloudSimCompletionEventsAreReplacedWhenRatesChange() {
		TransferHarness harness = new TransferHarness("transferHarness", source,
			destination, first, second);
		CloudSim.terminateSimulation(180.0);

		CloudSim.startSimulation();

		assertEquals(105.0, harness.getCompletionTime(first), DELTA);
		assertEquals(140.0, harness.getCompletionTime(second), DELTA);
		assertEquals(105.0, first.getMigTime(), DELTA);
		assertEquals(105.0, second.getMigTime(), DELTA);
		assertFalse(NetworkSlicing.hasActiveMigrationTransfer(first));
		assertFalse(NetworkSlicing.hasActiveMigrationTransfer(second));
	}

	private static final class TransferHarness extends SimEntity {
		private final FogDevice source;
		private final FogDevice destination;
		private final MobileDevice first;
		private final MobileDevice second;
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
				MobileDevice completed = NetworkSlicing.completeMigrationTransfer(
					(NetworkSlicing.MigrationTransferCompletion) event.getData());
				if (completed != null) {
					completionTimes.put(completed.getId(), CloudSim.clock());
				}
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
				new NetworkSlicing.MigrationTransferRequest(source, destination,
					mobileDevice, 100.0, getId(), TRANSFER_COMPLETE));
		}

		private double getCompletionTime(MobileDevice mobileDevice) {
			return completionTimes.get(mobileDevice.getId());
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
