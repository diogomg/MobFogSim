package org.fog.utils;

import java.util.HashMap;
import java.util.Map;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

/**
 * Fixed bandwidth slices for links between server cloudlets.
 *
 * A slice reserves its configured percentage of every cloudlet-to-cloudlet
 * link. While a slice has no active migration on a link, its reservation can
 * be borrowed by migrations in the active slices on that link.
 */
public final class NetworkSlicing {

	private static double[] percentages = new double[] { 100.0 };
	private static boolean dynamicBorrowing = true;
	/* Active migration counts, indexed by directed cloudlet link and slice. */
	private static final Map<String, int[]> activeTransfers = new HashMap<String, int[]>();
	private static final Map<Integer, String> transferLinks = new HashMap<Integer, String>();

	private NetworkSlicing() {
	}

	/**
	 * Configures two or three slices from a comma-separated percentage list,
	 * for example {@code "50,50"} or {@code "50,30,20"}.
	 */
	public static void configure(String percentageList) {
		if (percentageList == null || percentageList.trim().isEmpty()) {
			percentages = new double[] { 100.0 };
			resetUsage();
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
		resetUsage();
	}

	public static int getSliceCount() {
		return percentages.length;
	}

	/** Enables (true) or disables (false) borrowing of idle slice capacity. */
	public static void setDynamicBorrowing(boolean enabled) {
		dynamicBorrowing = enabled;
		resetUsage();
	}

	public static boolean isDynamicBorrowing() {
		return dynamicBorrowing;
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

	/**
	 * Starts accounting for a migration on a link and returns the bandwidth
	 * currently available to it. A migration receives its reserved share plus
	 * an equal share of the capacity reserved by slices that have no active
	 * migration on this same directed link. Concurrent migrations in one slice
	 * share that slice's current capacity equally.
	 */
	public static synchronized double reserveBandwidth(FogDevice source,
		FogDevice destination, MobileDevice mobileDevice) {
		int sliceId = validateSliceId(mobileDevice.getNetworkSliceId());
		if (!dynamicBorrowing) {
			return getSliceBandwidth(source, destination, sliceId);
		}
		String link = linkKey(source, destination);
		releaseBandwidth(mobileDevice);

		int[] activeBySlice = activeTransfers.get(link);
		if (activeBySlice == null) {
			activeBySlice = new int[percentages.length];
			activeTransfers.put(link, activeBySlice);
		}
		activeBySlice[sliceId]++;
		transferLinks.put(mobileDevice.getId(), link);

		return availableBandwidth(source, destination, activeBySlice, sliceId);
	}

	/** Releases a migration's reservation when it finishes or is aborted. */
	public static synchronized void releaseBandwidth(MobileDevice mobileDevice) {
		String link = transferLinks.remove(mobileDevice.getId());
		if (link == null) {
			return;
		}
		int[] activeBySlice = activeTransfers.get(link);
		if (activeBySlice == null) {
			return;
		}
		int sliceId = validateSliceId(mobileDevice.getNetworkSliceId());
		if (activeBySlice[sliceId] > 0) {
			activeBySlice[sliceId]--;
		}
		for (int active : activeBySlice) {
			if (active > 0) {
				return;
			}
		}
		activeTransfers.remove(link);
	}

	private static double availableBandwidth(FogDevice source, FogDevice destination,
		int[] activeBySlice, int requestedSlice) {
		double physicalBandwidth = Math.min(source.getUplinkBandwidth(),
			destination.getDownlinkBandwidth());
		int activeSlices = 0;
		double idlePercentage = 0.0;
		for (int sliceId = 0; sliceId < activeBySlice.length; sliceId++) {
			if (activeBySlice[sliceId] == 0) {
				idlePercentage += percentages[sliceId];
			}
			else {
				activeSlices++;
			}
		}
		double slicePercentage = percentages[requestedSlice]
			+ idlePercentage / activeSlices;
		return physicalBandwidth * slicePercentage / 100.0
			/ activeBySlice[requestedSlice];
	}

	private static String linkKey(FogDevice source, FogDevice destination) {
		if (source == null || destination == null) {
			throw new IllegalArgumentException("Network-slice reservations require source and destination cloudlets");
		}
		return source.getId() + "->" + destination.getId();
	}

	private static void resetUsage() {
		activeTransfers.clear();
		transferLinks.clear();
	}

	private static int validateSliceId(int sliceId) {
		if (sliceId < 0 || sliceId >= percentages.length) {
			throw new IllegalArgumentException("Unknown network slice: " + sliceId);
		}
		return sliceId;
	}
}
