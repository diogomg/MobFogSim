package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.Tuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class NetworkSlicingWirelessQueueTest {

	private ApDevice accessPoint;
	private MobileDevice mobile;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkSlicing.applyConfiguration(NetworkSlicing.parseConfiguration(
			"50,50", "50,50", NetworkSlicing.Scope.WIRELESS,
			NetworkSlicing.Mode.DYNAMIC, 2.0));
		accessPoint = new ApDevice("accessPoint", 0, 0, 0);
		accessPoint.setUplinkBandwidth(1_000_000.0);
		accessPoint.setDownlinkBandwidth(1_000_000.0);
		mobile = new MobileDevice("mobile", 0, 0, 0, 0, 0);
		mobile.setNetworkSliceId(0);
		mobile.setUplinkBandwidth(1_000_000.0);
		mobile.setDownlinkBandwidth(1_000_000.0);
		accessPoint.associateMobileDevice(mobile);
		mobile.setSourceAp(accessPoint);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.useDefaultRuntimeState();
	}

	@Test
	public void boundsLargePerDirectionFifoAndCleansItInOneCancellation() {
		int limit = NetworkSlicing.DEFAULT_MAXIMUM_WIRELESS_QUEUE_SIZE;
		int transferCount = limit * 2 + 2;
		for (int id = 0; id < transferCount; id++) {
			NetworkSlicing.startWirelessTupleTransfer(accessPoint, mobile,
				NetworkSlicing.WirelessDirection.UPLINK, tuple(id), mobile.getId(),
				accessPoint.getId(), 0.0);
		}

		assertEquals(limit, NetworkSlicing.getQueuedWirelessTransferCount());
		assertEquals(limit,
			NetworkSlicing.getMaximumQueuedWirelessTransferCount());
		assertEquals(limit, NetworkSlicing.getMaximumWirelessQueueDepth());
		assertEquals(limit + 1L,
			NetworkSlicing.getDroppedWirelessTupleCount());

		NetworkSlicing.cancelWirelessTransfers(mobile);

		assertEquals(0L, NetworkSlicing.getQueuedWirelessTransferCount());
		assertFalse(NetworkSlicing.hasActiveWirelessTransfer(mobile,
			NetworkSlicing.WirelessDirection.UPLINK));
	}

	private static Tuple tuple(int id) {
		return new Tuple("app", id, Tuple.UP, 1L, 1, 54L, 1L,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
	}
}
