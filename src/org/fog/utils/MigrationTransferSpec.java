package org.fog.utils;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

/**
 * Immutable description of one migration before it enters the transport
 * scheduler.
 *
 * Transfer size is measured in bytes, bandwidth in bits per second, and every
 * delay in CloudSim milliseconds. Preparation and fixed delays do not consume
 * transport bandwidth; only {@link #getTransferBytes()} enters the fluid-share
 * scheduler.
 */
public final class MigrationTransferSpec {

	private static final double BYTES_PER_MEBIBYTE = 1024.0 * 1024.0;
	private static final double BITS_PER_BYTE = 8.0;
	private static final double MILLISECONDS_PER_SECOND = 1000.0;

	private final FogDevice source;
	private final FogDevice destination;
	private final MobileDevice mobileDevice;
	private final int networkSliceId;
	private final long migrationGeneration;
	private final double transferBytes;
	private final double fixedDelayMillis;
	private final double preparationDelayMillis;
	private final int completionDestinationId;
	private final int completionEventTag;

	public MigrationTransferSpec(FogDevice source, FogDevice destination,
		MobileDevice mobileDevice, double transferBytes, double fixedDelayMillis,
		double preparationDelayMillis, int completionDestinationId,
		int completionEventTag) {
		if (source == null || destination == null) {
			throw new IllegalArgumentException(
				"A migration transfer requires source and destination cloudlets");
		}
		if (mobileDevice == null) {
			throw new IllegalArgumentException(
				"A migration transfer requires a mobile device");
		}
		this.source = source;
		this.destination = destination;
		this.mobileDevice = mobileDevice;
		this.networkSliceId = mobileDevice.getNetworkSliceId();
		this.migrationGeneration = mobileDevice.getSession()
			.getMigrationGeneration();
		this.transferBytes = requireNonNegativeFinite(transferBytes,
			"Migration transfer bytes");
		this.fixedDelayMillis = requireNonNegativeFinite(fixedDelayMillis,
			"Migration fixed delay");
		this.preparationDelayMillis = requireNonNegativeFinite(
			preparationDelayMillis, "Migration preparation delay");
		if (completionDestinationId < 0) {
			throw new IllegalArgumentException(
				"Migration completion requires a destination entity");
		}
		this.completionDestinationId = completionDestinationId;
		this.completionEventTag = completionEventTag;
	}

	public FogDevice getSource() {
		return source;
	}

	public FogDevice getDestination() {
		return destination;
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public int getNetworkSliceId() {
		return networkSliceId;
	}

	/** Aggregate generation that owned this transfer when it was prepared. */
	public long getMigrationGeneration() {
		return migrationGeneration;
	}

	public double getTransferBytes() {
		return transferBytes;
	}

	public double getFixedDelayMillis() {
		return fixedDelayMillis;
	}

	public double getPreparationDelayMillis() {
		return preparationDelayMillis;
	}

	int getCompletionDestinationId() {
		return completionDestinationId;
	}

	int getCompletionEventTag() {
		return completionEventTag;
	}

	/** Converts a size expressed in MiB to bytes. */
	public static double mebibytesToBytes(double mebibytes) {
		requireNonNegativeFinite(mebibytes, "Migration size in MiB");
		return requireNonNegativeFinite(mebibytes * BYTES_PER_MEBIBYTE,
			"Migration size in bytes");
	}

	/** Returns transfer time in milliseconds for bytes sent at bits per second. */
	public static double transferTimeMillis(double transferBytes,
		double bandwidthBitsPerSecond) {
		requireNonNegativeFinite(transferBytes, "Migration transfer bytes");
		if (!Double.isFinite(bandwidthBitsPerSecond)
			|| bandwidthBitsPerSecond <= 0.0) {
			throw new IllegalArgumentException(
				"Migration bandwidth must be finite and positive");
		}
		return transferBytes * BITS_PER_BYTE / bandwidthBitsPerSecond
			* MILLISECONDS_PER_SECOND;
	}

	private static double requireNonNegativeFinite(double value,
		String description) {
		if (!Double.isFinite(value) || value < 0.0) {
			throw new IllegalArgumentException(
				description + " must be finite and non-negative");
		}
		return value;
	}
}
