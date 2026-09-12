package org.fog.entities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.utils.FogEvents;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.placement.MobileController;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AccessPointContentionEventTest {

	private static final int START = 9101;
	private static final int DISCONNECT = 9102;
	private static final double DELTA = 0.000001;
	private static final long TUPLE_BYTES = 1000L;
	private static final double BANDWIDTH_BITS_PER_SECOND = 8000.0;
	private static final double PROPAGATION_DELAY_MILLIS = 10.0;

	private RecordingServer server;
	private ApDevice accessPoint;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MyStatistics.setInstance(new MyStatistics());
		NetworkUsageMonitor.reset();
		NetworkSlicing.configure("70,30");
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
		MobileController.setRand(new Random(1L));
		server = new RecordingServer("server");
		accessPoint = new ApDevice("ap", 0, 0, 0);
		accessPoint.setUplinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		accessPoint.setDownlinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		accessPoint.setServerCloudlet(server);
	}

	@After
	public void resetSlicing() {
		NetworkSlicing.configure(null);
		NetworkSlicing.setDynamicBorrowing(true);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);
		NetworkSlicing.setReallocationDelaySeconds(0.0);
		NetworkUsageMonitor.reset();
	}

	@Test
	public void idleAssociatedUsersDoNotReduceAnActiveFlowsRate() {
		RecordingMobile active = connect("active", 1);
		connect("idle", 0);
		Tuple tuple = tuple(1, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				active.emitUp(tuple);
			}
		});

		runUntil(1500.0);

		assertEquals(1010.0, server.arrivalTime(1), DELTA);
	}

	@Test
	public void dynamicSlicesRebalanceWhenAnActiveSliceCompletes() {
		RecordingMobile largeSlice = connect("large", 0);
		RecordingMobile smallSlice = connect("small", 1);
		Tuple first = tuple(1, Tuple.UP);
		Tuple second = tuple(2, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				largeSlice.emitUp(first);
				smallSlice.emitUp(second);
			}
		});

		runUntil(2500.0);

		assertEquals(1000.0 * 8.0 * 1000.0 / 5600.0
			+ PROPAGATION_DELAY_MILLIS, server.arrivalTime(1), DELTA);
		assertEquals(2010.0, server.arrivalTime(2), DELTA);
		assertEquals(2.0 * TUPLE_BYTES * PROPAGATION_DELAY_MILLIS,
			NetworkUsageMonitor.getTupleUsageByteMilliseconds(), DELTA);
		assertEquals(4L, NetworkSlicing.getReconfigurationCount());
		assertEquals(0.0, NetworkSlicing.getSliceOutageSeconds(), DELTA);
		double[] receivedBandwidth =
			NetworkSlicing.getReceivedBandwidthBySlice();
		assertEquals(13600.0, receivedBandwidth[0], DELTA);
		assertEquals(10400.0, receivedBandwidth[1], DELTA);
	}

	@Test
	public void wirelessDynamicAllocationIncludesReallocationDelay() {
		NetworkSlicing.setReallocationDelaySeconds(1.0);
		RecordingMobile mobile = connect("delayed", 0);
		Tuple tuple = tuple(1, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				mobile.emitUp(tuple);
			}
		});

		runUntil(2500.0);

		assertEquals(2010.0, server.arrivalTime(1), DELTA);
		assertEquals(2L, NetworkSlicing.getReconfigurationCount());
		assertEquals(2.0, NetworkSlicing.getSliceOutageSeconds(), DELTA);
		assertEquals(BANDWIDTH_BITS_PER_SECOND,
			NetworkSlicing.getReceivedBandwidthBySlice()[0], DELTA);
	}

	@Test
	public void fixedSlicesKeepTheirReservationsAfterAnotherSliceFinishes() {
		NetworkSlicing.setDynamicBorrowing(false);
		RecordingMobile largeSlice = connect("large", 0);
		RecordingMobile smallSlice = connect("small", 1);
		Tuple first = tuple(1, Tuple.UP);
		Tuple second = tuple(2, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				largeSlice.emitUp(first);
				smallSlice.emitUp(second);
			}
		});

		runUntil(4000.0);

		assertEquals(1000.0 * 8.0 * 1000.0 / 2400.0
			+ PROPAGATION_DELAY_MILLIS, server.arrivalTime(2), DELTA);
		assertEquals(0L, NetworkSlicing.getReconfigurationCount());
	}

	@Test
	public void uplinkAndDownlinkUseIndependentAPCapacity() {
		RecordingMobile mobile = connect("mobile", 0);
		Tuple uplink = tuple(1, Tuple.UP);
		Tuple downlink = tuple(2, Tuple.DOWN);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				mobile.emitUp(uplink);
				server.emitDown(downlink, mobile.getId());
			}
		});

		runUntil(1500.0);

		assertEquals(1010.0, server.arrivalTime(1), DELTA);
		assertEquals(1010.0, mobile.arrivalTime(2), DELTA);
	}

	@Test
	public void oneMobileQueuesMultipleFlowsInTheSameDirection() {
		RecordingMobile mobile = connect("mobile", 0);
		Tuple first = tuple(1, Tuple.UP);
		Tuple second = tuple(2, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				mobile.emitUp(first);
				mobile.emitUp(second);
			}
		});

		runUntil(2500.0);

		assertEquals(1010.0, server.arrivalTime(1), DELTA);
		assertEquals(2010.0, server.arrivalTime(2), DELTA);
	}

	@Test
	public void disconnectionCancelsActiveAndQueuedWirelessFlows() {
		RecordingMobile mobile = connect("mobile", 0);
		Tuple first = tuple(1, Tuple.UP);
		Tuple second = tuple(2, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				mobile.emitUp(first);
				mobile.emitUp(second);
			}
		}, new Runnable() {
			@Override
			public void run() {
				accessPoint.desconnectApSmartThing(mobile);
			}
		}, 500.0);

		runUntil(2500.0);

		assertFalse(server.hasArrival(1));
		assertFalse(server.hasArrival(2));
		assertFalse(NetworkSlicing.hasActiveWirelessTransfer(mobile,
			NetworkSlicing.WirelessDirection.UPLINK));
		assertEquals(0, NetworkSlicing.getActiveWirelessTransferCount(
			accessPoint, NetworkSlicing.WirelessDirection.UPLINK));
		assertEquals(0.0,
			NetworkUsageMonitor.getTupleUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void handoffCancelsTrafficOnTheFormerAccessPoint() {
		RecordingMobile mobile = connect("mobile", 0);
		ApDevice destinationAccessPoint = new ApDevice("destination-ap", 0, 0, 1);
		destinationAccessPoint.setUplinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		destinationAccessPoint.setDownlinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		destinationAccessPoint.setServerCloudlet(server);
		mobile.setDestinationAp(destinationAccessPoint);
		Tuple tuple = tuple(1, Tuple.UP);
		new ActionHarness("harness", new Runnable() {
			@Override
			public void run() {
				mobile.emitUp(tuple);
			}
		}, new Runnable() {
			@Override
			public void run() {
				CloudSim.send(mobile.getId(), accessPoint.getId(), 0.0,
					MobileEvents.START_HANDOFF, mobile);
			}
		}, 500.0);

		runUntil(2500.0);

		assertSame(destinationAccessPoint, mobile.getSourceAp());
		assertFalse(server.hasArrival(1));
		assertEquals(0, NetworkSlicing.getActiveWirelessTransferCount(
			accessPoint, NetworkSlicing.WirelessDirection.UPLINK));
	}

	private RecordingMobile connect(String name, int sliceId) {
		RecordingMobile mobile = new RecordingMobile(name);
		mobile.setNetworkSliceId(sliceId);
		mobile.setUplinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		mobile.setDownlinkBandwidth(BANDWIDTH_BITS_PER_SECOND);
		mobile.setUplinkLatency(PROPAGATION_DELAY_MILLIS);
		mobile.setSourceAp(accessPoint);
		accessPoint.setSmartThings(mobile, Policies.ADD);
		server.connectServerCloudletSmartThing(mobile);
		return mobile;
	}

	private static Tuple tuple(int id, int direction) {
		return new Tuple("app", id, direction, 1L, 1, TUPLE_BYTES, 1L,
			new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
	}

	private static void runUntil(double time) {
		CloudSim.terminateSimulation(time);
		CloudSim.startSimulation();
	}

	private static class RecordingServer extends FogDevice {
		private final Map<Integer, Double> arrivals =
			new HashMap<Integer, Double>();

		private RecordingServer(String name) {
			super(name, 0, 0, 0);
			setChildrenIds(new ArrayList<Integer>());
			setChildToLatencyMap(new HashMap<Integer, Double>());
			setChildToOperatorsMap(new HashMap<Integer, List<String>>());
		}

		private void emitDown(Tuple tuple, int mobileId) {
			sendDown(tuple, mobileId);
		}

		@Override
		protected void processOtherEvent(SimEvent event) {
			if (event.getTag() == FogEvents.TUPLE_ARRIVAL) {
				Tuple tuple = (Tuple) event.getData();
				arrivals.put(tuple.getCloudletId(), CloudSim.clock());
				return;
			}
			super.processOtherEvent(event);
		}

		private double arrivalTime(int tupleId) {
			return arrivals.get(tupleId);
		}

		private boolean hasArrival(int tupleId) {
			return arrivals.containsKey(tupleId);
		}
	}

	private static final class RecordingMobile extends MobileDevice {
		private final Map<Integer, Double> arrivals =
			new HashMap<Integer, Double>();

		private RecordingMobile(String name) {
			super(name, 0, 0, 0, 0, 0);
		}

		private void emitUp(Tuple tuple) {
			sendUp(tuple);
		}

		@Override
		protected void processOtherEvent(SimEvent event) {
			if (event.getTag() == FogEvents.TUPLE_ARRIVAL) {
				Tuple tuple = (Tuple) event.getData();
				arrivals.put(tuple.getCloudletId(), CloudSim.clock());
				return;
			}
			super.processOtherEvent(event);
		}

		private double arrivalTime(int tupleId) {
			return arrivals.get(tupleId);
		}
	}

	private static final class ActionHarness extends SimEntity {
		private final Runnable startAction;
		private final Runnable laterAction;
		private final double laterDelay;

		private ActionHarness(String name, Runnable startAction) {
			this(name, startAction, null, 0.0);
		}

		private ActionHarness(String name, Runnable startAction,
			Runnable laterAction, double laterDelay) {
			super(name);
			this.startAction = startAction;
			this.laterAction = laterAction;
			this.laterDelay = laterDelay;
		}

		@Override
		public void startEntity() {
			scheduleNow(getId(), START);
			if (laterAction != null) {
				schedule(getId(), laterDelay, DISCONNECT);
			}
		}

		@Override
		public void processEvent(SimEvent event) {
			if (event.getTag() == START) {
				startAction.run();
			}
			else if (event.getTag() == DISCONNECT) {
				laterAction.run();
			}
		}

		@Override
		public void shutdownEntity() {
		}
	}
}
