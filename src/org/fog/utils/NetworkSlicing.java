package org.fog.utils;

import org.fog.entities.FogDevice;

/**
 * Fixed bandwidth slices for links between server cloudlets.
 *
 * A slice reserves its configured percentage of every cloudlet-to-cloudlet
 * link. Unused capacity in one slice is intentionally not borrowed by another
 * slice, so simulation results represent a strict network-slicing policy.
 */
public final class NetworkSlicing {

	private static double[] percentages = new double[] { 100.0 };

	private NetworkSlicing() {
	}

	/**
	 * Configures two or three slices from a comma-separated percentage list,
	 * for example {@code "50,50"} or {@code "50,30,20"}.
	 */
	public static void configure(String percentageList) {
		if (percentageList == null || percentageList.trim().isEmpty()) {
			percentages = new double[] { 100.0 };
			return;
		}

		String[] values = percentageList.split(",");
		if (values.length != 2 && values.length != 3) {
			throw new IllegalArgumentException(
				"Network slice percentages must contain exactly two or three values");
		}

		double[] parsed = new double[values.length];
		double total = 0.0;
		for (int index = 0; index < values.length; index++) {
			parsed[index] = Double.parseDouble(values[index].trim());
			if (parsed[index] <= 0.0 || parsed[index] > 100.0) {
				throw new IllegalArgumentException(
					"Each network slice percentage must be greater than 0 and at most 100");
			}
			total += parsed[index];
		}
		if (Math.abs(total - 100.0) > 0.000001) {
			throw new IllegalArgumentException("Network slice percentages must sum to 100");
		}
		percentages = parsed;
	}

	public static int getSliceCount() {
		return percentages.length;
	}

	public static double getPercentage(int sliceId) {
		return percentages[validateSliceId(sliceId)];
	}

	/** Returns the fixed capacity available to one slice on a physical link. */
	public static double getSliceBandwidth(double physicalBandwidth, int sliceId) {
		if (physicalBandwidth <= 0.0) {
			return physicalBandwidth;
		}
		return physicalBandwidth * getPercentage(sliceId) / 100.0;
	}

	/**
	 * Returns the bandwidth of the directed source-to-destination cloudlet link
	 * for a user slice. This is the same physical-link calculation used when
	 * AppExample builds the cloudlet network.
	 */
	public static double getSliceBandwidth(FogDevice source, FogDevice destination,
		int sliceId) {
		double physicalBandwidth = source.getUplinkBandwidth();
		if (destination != null) {
			physicalBandwidth = Math.min(physicalBandwidth,
				destination.getDownlinkBandwidth());
		}
		return getSliceBandwidth(physicalBandwidth, sliceId);
	}

	private static int validateSliceId(int sliceId) {
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		return sliceId;
	}
}
