package org.fog.utils;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

/**
 * Immutable description of one migration before it enters the transport
 * scheduler.
 *
 * Transfer size is measured in bytes, bandwidth in bits per second, and every
 * delay in CloudSim milliseconds. Preparation and fixed delays do not consume
 * transport bandwidth; only {@link #getTransferSize()} enters the fluid-share
 * scheduler.
 */
public final class MigrationTransferSpec {

	private final FogDevice source;
	private final FogDevice destination;
	private final MobileDevice mobileDevice;
	private final NetworkSliceId networkSliceId;
	private final long migrationGeneration;
	private final DataSize transferSize;
	private final SimulationDuration fixedDelay;
	private final SimulationDuration preparationDelay;
	private final EntityId completionDestinationId;
	private final int completionEventTag;

	/** Legacy primitive adapter. Prefer the unit-bearing constructor. */
	public MigrationTransferSpec(FogDevice source, FogDevice destination,
		MobileDevice mobileDevice, double transferBytes, double fixedDelayMillis,
		double preparationDelayMillis, int completionDestinationId,
		int completionEventTag) {
		this(source, destination, mobileDevice, DataSize.ofBytes(transferBytes),
			SimulationDuration.ofMilliseconds(fixedDelayMillis),
			SimulationDuration.ofMilliseconds(preparationDelayMillis),
			EntityId.of(completionDestinationId), completionEventTag);
	}

	public MigrationTransferSpec(FogDevice source, FogDevice destination,
		MobileDevice mobileDevice, DataSize transferSize,
		SimulationDuration fixedDelay, SimulationDuration preparationDelay,
		EntityId completionDestinationId, int completionEventTag) {
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
		this.networkSliceId = NetworkSliceId.of(mobileDevice.getNetworkSliceId());
		this.migrationGeneration = mobileDevice.getSession()
			.getMigrationGeneration();
		if (transferSize == null || fixedDelay == null
			|| preparationDelay == null || completionDestinationId == null) {
			throw new IllegalArgumentException(
				"Migration transfer values cannot be null");
		}
		this.transferSize = transferSize;
		this.fixedDelay = fixedDelay;
		this.preparationDelay = preparationDelay;
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
		return networkSliceId.intValue();
	}

	public NetworkSliceId getNetworkSlice() {
		return networkSliceId;
	}

	/** Aggregate generation that owned this transfer when it was prepared. */
	public long getMigrationGeneration() {
		return migrationGeneration;
	}

	public double getTransferBytes() {
		return transferSize.toBytes();
	}

	public DataSize getTransferSize() {
		return transferSize;
	}

	public double getFixedDelayMillis() {
		return fixedDelay.toMilliseconds();
	}

	public SimulationDuration getFixedDelay() {
		return fixedDelay;
	}

	public double getPreparationDelayMillis() {
		return preparationDelay.toMilliseconds();
	}

	public SimulationDuration getPreparationDelay() {
		return preparationDelay;
	}

	EntityId getCompletionDestination() {
		return completionDestinationId;
	}

	int getCompletionEventTag() {
		return completionEventTag;
	}

	/** Converts a size expressed in MiB to bytes. */
	public static double mebibytesToBytes(double mebibytes) {
		return DataSize.ofMebibytes(mebibytes).toBytes();
	}

	/** Returns transfer time in milliseconds for bytes sent at bits per second. */
	public static double transferTimeMillis(double transferBytes,
		double bandwidthBitsPerSecond) {
		return DataSize.ofBytes(transferBytes).transferDurationAt(
			DataRate.ofBitsPerSecond(bandwidthBitsPerSecond)).toMilliseconds();
	}
}
