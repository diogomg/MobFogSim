package org.fog.placement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkTransferUsage;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.junit.Before;
import org.junit.Test;

public class SimulationMetricsSnapshotTest {

	private static final double DELTA = 0.000001;
	private MyStatistics statistics;
	private TimeKeeper timeKeeper;

	@Before
	public void resetMetricSources() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkUsageMonitor.reset();
		NetworkSlicing.useDefaultRuntimeState();
		statistics = new MyStatistics();
		timeKeeper = TimeKeeper.getInstance();
		timeKeeper.setLoopIdToTupleIds(
			new HashMap<Integer, List<Integer>>());
		timeKeeper.setLoopIdToCurrentAverage(
			new HashMap<Integer, Double>());
		timeKeeper.setMaxLoopExecutionTime(
			new HashMap<Integer, Double>());
		timeKeeper.setTupleTypeToAverageCpuTime(
			new HashMap<String, Double>());
	}

	@Test
	public void emptyRunUsesFiniteZeroSemantics() {
		SimulationMetricsSnapshot snapshot = capture(
			Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), 0.0, -1L);

		assertEquals(0L, snapshot.getExecutionTimeMillis());
		assertEquals(0.0, snapshot.getAverageCloudletEnergy(), DELTA);
		assertEquals(0.0, snapshot.getAverageAccessPointEnergy(), DELTA);
		assertEquals(0.0, snapshot.getAverageLoopDelay(), DELTA);
		assertEquals(0.0, snapshot.getAverageMaximumLoopDelay(), DELTA);
		assertEquals(0.0,
			snapshot.getMeanMigrationUsageByteMilliseconds(), DELTA);
		assertEquals(0.0, snapshot.perSimulationMillisecond(123.0), DELTA);
		assertEquals(0.0,
			snapshot.getStatistics().getLostTuplePercentage(), DELTA);
		assertEquals(0L, snapshot.getSliceReconfigurationCount());
		assertEquals(0.0, snapshot.getSliceOutageSeconds(), DELTA);
		assertEquals(0.0, snapshot.getReceivedBandwidthBySlice().get(0), DELTA);
		assertEquals(NetworkSlicing.DEFAULT_MAXIMUM_WIRELESS_QUEUE_SIZE,
			snapshot.getMaximumWirelessQueueSize());
		assertEquals(0L, snapshot.getQueuedWirelessTransferCount());
		assertEquals(0L, snapshot.getMaximumQueuedWirelessTransferCount());
		assertEquals(0, snapshot.getMaximumWirelessQueueDepth());
		assertEquals(0L, snapshot.getDroppedWirelessTupleCount());
		assertTrue(snapshot.getLoopTimings().isEmpty());
	}

	@Test
	public void energyAveragesUseExplicitRolesAndIncludeEveryCloudlet() {
		FogDevice cloudletWithFormerSentinelEnergy =
			new FogDevice("cloudlet-a", 0, 0, 7);
		cloudletWithFormerSentinelEnergy.setEnergyConsumption(
			5.8736831999993116E7);
		FogDevice cloudlet = new FogDevice("cloudlet-b", 0, 0, 42);
		cloudlet.setEnergyConsumption(40.0);
		ApDevice firstAccessPoint = new ApDevice("ap-a", 0, 0, 100);
		firstAccessPoint.setEnergyConsumption(10.0);
		ApDevice secondAccessPoint = new ApDevice("ap-b", 0, 0, 101);
		secondAccessPoint.setEnergyConsumption(30.0);

		SimulationMetricsSnapshot snapshot = capture(
			Arrays.asList(cloudletWithFormerSentinelEnergy, cloudlet),
			Arrays.asList(firstAccessPoint, secondAccessPoint), 1.0, 1L);

		assertEquals(2, snapshot.getCloudlets().size());
		assertEquals((5.8736831999993116E7 + 40.0) / 2.0,
			snapshot.getAverageCloudletEnergy(), DELTA);
		assertEquals(20.0, snapshot.getAverageAccessPointEnergy(), DELTA);
	}

	@Test
	public void cloudExecutionCostIsCapturedWithoutRequiringACloudDevice() {
		FogDevice cloud = new FogDevice("cloud", 0, 0, 7);
		cloud.setTotalCost(12.5);

		SimulationMetricsSnapshot withCloud = capture(
			Collections.singletonList(cloud), Collections.<ApDevice>emptyList(),
			1.0, 1L);
		SimulationMetricsSnapshot withoutCloud = capture(
			Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), 1.0, 1L);

		assertEquals(12.5, withCloud.getCloudExecutionCost(), DELTA);
		assertEquals(0.0, withoutCloud.getCloudExecutionCost(), DELTA);
	}

	@Test
	public void networkAccountingIsCapturedWithExplicitDimensions() {
		NetworkUsageMonitor.recordCompletedTuple(new NetworkTransferUsage(
			10.0, 2.0, 3.0, 5.0));
		NetworkUsageMonitor.recordCompletedMigration(20.0, 7.0);

		SimulationMetricsSnapshot snapshot = capture(
			Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), 100.0, 1L);
		NetworkUsageMonitor.reset();

		assertEquals(10.0, snapshot.getTupleTransferredBytes(), DELTA);
		assertEquals(2.0,
			snapshot.getTupleQueueDurationMilliseconds(), DELTA);
		assertEquals(3.0,
			snapshot.getTupleTransferDurationMilliseconds(), DELTA);
		assertEquals(5.0,
			snapshot.getTuplePropagationDurationMilliseconds(), DELTA);
		assertEquals(20.0,
			snapshot.getTupleQueueUsageByteMilliseconds(), DELTA);
		assertEquals(30.0,
			snapshot.getTupleTransferUsageByteMilliseconds(), DELTA);
		assertEquals(50.0,
			snapshot.getTuplePropagationUsageByteMilliseconds(), DELTA);
		assertEquals(7.0,
			snapshot.getMigrationTransferDurationMilliseconds(), DELTA);
		assertEquals(240.0,
			snapshot.getTotalUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void sparseIdsArePreservedAndCapturedCollectionsAreImmutable() {
		statistics.recordPowerAndEnergy(7, 1.5, 2.5);
		statistics.recordPowerAndEnergy(42, Double.NaN,
			Double.POSITIVE_INFINITY);
		statistics.observeMigrationTime(42, 10.0);
		statistics.observeMigrationTime(42, 30.0);
		statistics.observeMigrationTime(7, 20.0);
		timeKeeper.registerLoop(7);
		timeKeeper.registerLoop(42);
		timeKeeper.registerLoop(99);
		Map<Integer, Double> loopAverages = new HashMap<Integer, Double>();
		loopAverages.put(7, 10.0);
		loopAverages.put(42, 30.0);
		loopAverages.put(99, Double.NaN);
		timeKeeper.setLoopIdToCurrentAverage(loopAverages);
		Map<Integer, Double> loopMaximums = new HashMap<Integer, Double>();
		loopMaximums.put(42, 50.0);
		timeKeeper.setMaxLoopExecutionTime(loopMaximums);
		Map<String, Double> tupleCpuTimes = new HashMap<String, Double>();
		tupleCpuTimes.put("sensor", Double.NaN);
		timeKeeper.setTupleTypeToAverageCpuTime(tupleCpuTimes);

		SimulationMetricsSnapshot snapshot = capture(
			Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), 100.0, 1L);
		statistics.recordPowerAndEnergy(8, 99.0, 99.0);
		loopAverages.put(7, 999.0);
		timeKeeper.setLoopIdToCurrentAverage(loopAverages);

		assertEquals(Arrays.asList(7, 42),
			new ArrayList<Integer>(snapshot.getMobilePowerHistory().keySet()));
		assertFalse(snapshot.getMobilePowerHistory().containsKey(8));
		assertEquals(0.0, snapshot.getMobilePowerHistory().get(42), DELTA);
		assertEquals(Arrays.asList(7, 42, 99),
			new ArrayList<Integer>(snapshot.getLoopTimings().keySet()));
		assertEquals(10.0, snapshot.getLoopTimings().get(7).getAverage(), DELTA);
		assertEquals(0.0, snapshot.getLoopTimings().get(7).getMaximum(), DELTA);
		assertEquals(0.0, snapshot.getLoopTimings().get(99).getAverage(), DELTA);
		assertEquals(20.0, snapshot.getAverageLoopDelay(), DELTA);
		assertEquals(50.0, snapshot.getAverageMaximumLoopDelay(), DELTA);
		assertEquals(20.0,
			snapshot.getStatistics().getMigrationTime().getAverage(), DELTA);
		assertEquals(30.0,
			snapshot.getStatistics().getMigrationTime().getMaximum(), DELTA);
		assertEquals(0.0, snapshot.getTupleCpuTimes().get("sensor"), DELTA);

		try {
			snapshot.getMobilePowerHistory().put(9, 1.0);
			fail("Snapshot maps must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	@Test
	public void statisticQueriesDoNotChangeCounters() {
		assertEquals(0, statistics.getMyCountTuple());
		assertEquals(0, statistics.getMyCountTuple());
		assertEquals(1, statistics.nextTupleId());
		assertEquals(1, statistics.getMyCountTuple());
		assertEquals(1, statistics.getMyCountTuple());

		statistics.setTotalMigrations(4);
		statistics.setTotalMigrations(2);
		assertEquals(2, statistics.getTotalMigrations());
		statistics.startWithoutConnection(42, 10.0);
		statistics.finalWithoutConnection(42, 15.0);
		assertEquals(1, statistics.getMyCountWithoutConnection());
		assertEquals(1, statistics.getMyCountWithoutConnection());
		assertEquals(5.0, statistics.getAverageWithoutConnection(), DELTA);
	}

	private SimulationMetricsSnapshot capture(List<FogDevice> cloudlets,
		List<ApDevice> accessPoints, double simulationTimeMillis,
		long executionTimeMillis) {
		return SimulationMetricsSnapshot.capture(cloudlets, accessPoints,
			Collections.<MobileDevice>emptyList(), statistics, timeKeeper,
			simulationTimeMillis, executionTimeMillis);
	}
}
