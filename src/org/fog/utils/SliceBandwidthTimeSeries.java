package org.fog.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Samples the aggregate allocation of a set of independently scheduled
 * resources at whole simulation seconds.
 */
final class SliceBandwidthTimeSeries {

	private static final double SAMPLE_INTERVAL_MILLIS = 1000.0;
	private static final double TIME_EPSILON = 0.000001;

	static final class Sample {
		private final long elapsedSecond;
		private final double[] bandwidthBySlice;

		private Sample(long elapsedSecond, double[] bandwidthBySlice) {
			this.elapsedSecond = elapsedSecond;
			this.bandwidthBySlice = bandwidthBySlice.clone();
		}

		long getElapsedSecond() {
			return elapsedSecond;
		}

		double[] getBandwidthBySlice() {
			return bandwidthBySlice.clone();
		}
	}

	private final Map<Object, double[]> allocationsByResource =
		new HashMap<Object, double[]>();
	private final List<Sample> samples = new ArrayList<Sample>();
	private double[] aggregateBandwidth;
	private long nextSampleSecond;
	private double lastUpdateMillis;

	SliceBandwidthTimeSeries(int sliceCount) {
		reset(sliceCount);
	}

	void update(Object resource, double nowMillis, double[] bandwidthBySlice) {
		if (resource == null || bandwidthBySlice == null) {
			throw new IllegalArgumentException(
				"Bandwidth resource and allocation cannot be null");
		}
		if (!Double.isFinite(nowMillis) || nowMillis < 0.0
			|| nowMillis + TIME_EPSILON < lastUpdateMillis) {
			throw new IllegalArgumentException(
				"Bandwidth sample time must be finite, non-negative, and monotonic");
		}
		if (bandwidthBySlice.length != aggregateBandwidth.length) {
			throw new IllegalArgumentException(
				"Bandwidth allocation must contain one value per slice");
		}
		appendSamplesBefore(nowMillis);

		double[] previous = allocationsByResource.remove(resource);
		if (previous != null) {
			addToAggregate(previous, -1.0);
		}
		double[] allocation = sanitisedAllocation(bandwidthBySlice);
		if (hasBandwidth(allocation)) {
			allocationsByResource.put(resource, allocation);
			addToAggregate(allocation, 1.0);
		}
		lastUpdateMillis = Math.max(lastUpdateMillis, nowMillis);
	}

	List<Sample> snapshot(double endMillis) {
		if (!Double.isFinite(endMillis) || endMillis < 0.0
			|| endMillis + TIME_EPSILON < lastUpdateMillis) {
			throw new IllegalArgumentException(
				"Bandwidth snapshot time must be finite and not precede updates");
		}
		while (sampleTimeMillis(nextSampleSecond) <= endMillis + TIME_EPSILON) {
			appendSample();
		}
		return new ArrayList<Sample>(samples);
	}

	void reset(int sliceCount) {
		if (sliceCount <= 0) {
			throw new IllegalArgumentException("At least one slice is required");
		}
		allocationsByResource.clear();
		samples.clear();
		aggregateBandwidth = new double[sliceCount];
		nextSampleSecond = 0L;
		lastUpdateMillis = 0.0;
	}

	private void appendSamplesBefore(double nowMillis) {
		while (sampleTimeMillis(nextSampleSecond) + TIME_EPSILON < nowMillis) {
			appendSample();
		}
	}

	private void appendSample() {
		samples.add(new Sample(nextSampleSecond, aggregateBandwidth));
		nextSampleSecond++;
	}

	private void addToAggregate(double[] allocation, double sign) {
		for (int sliceId = 0; sliceId < allocation.length; sliceId++) {
			aggregateBandwidth[sliceId] += sign * allocation[sliceId];
			if (Math.abs(aggregateBandwidth[sliceId]) <= TIME_EPSILON) {
				aggregateBandwidth[sliceId] = 0.0;
			}
		}
	}

	private static double[] sanitisedAllocation(double[] source) {
		double[] result = source.clone();
		for (int sliceId = 0; sliceId < result.length; sliceId++) {
			if (!Double.isFinite(result[sliceId]) || result[sliceId] < 0.0) {
				throw new IllegalArgumentException(
					"Slice bandwidth must be finite and non-negative");
			}
		}
		return result;
	}

	private static boolean hasBandwidth(double[] allocation) {
		for (double bandwidth : allocation) {
			if (bandwidth > 0.0) {
				return true;
			}
		}
		return false;
	}

	private static double sampleTimeMillis(long elapsedSecond) {
		return elapsedSecond * SAMPLE_INTERVAL_MILLIS;
	}
}
