package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class SliceBandwidthTimeSeriesTest {

	private static final double DELTA = 0.000001;

	@Test
	public void samplesAggregateResourceAllocationsAtWholeSeconds() {
		SliceBandwidthTimeSeries series = new SliceBandwidthTimeSeries(2);
		series.update("resource-a", 0.0, new double[] { 700.0, 300.0 });
		series.update("resource-b", 500.0, new double[] { 200.0, 0.0 });
		series.update("resource-a", 1000.0, new double[] { 0.0, 300.0 });

		List<SliceBandwidthTimeSeries.Sample> samples = series.snapshot(2500.0);

		assertEquals(3, samples.size());
		assertSample(samples.get(0), 0L, 700.0, 300.0);
		assertSample(samples.get(1), 1L, 200.0, 300.0);
		assertSample(samples.get(2), 2L, 200.0, 300.0);
	}

	@Test
	public void replacingAndRemovingAResourceDoesNotDoubleCountIt() {
		SliceBandwidthTimeSeries series = new SliceBandwidthTimeSeries(2);
		series.update("resource", 0.0, new double[] { 100.0, 0.0 });
		series.update("resource", 400.0, new double[] { 40.0, 60.0 });
		series.update("resource", 1000.0, new double[] { 0.0, 0.0 });

		List<SliceBandwidthTimeSeries.Sample> samples = series.snapshot(1000.0);

		assertEquals(2, samples.size());
		assertSample(samples.get(0), 0L, 100.0, 0.0);
		assertSample(samples.get(1), 1L, 0.0, 0.0);
	}

	private static void assertSample(SliceBandwidthTimeSeries.Sample sample,
		long second, double sliceZero, double sliceOne) {
		assertEquals(second, sample.getElapsedSecond());
		double[] bandwidth = sample.getBandwidthBySlice();
		assertEquals(sliceZero, bandwidth[0], DELTA);
		assertEquals(sliceOne, bandwidth[1], DELTA);
	}
}
