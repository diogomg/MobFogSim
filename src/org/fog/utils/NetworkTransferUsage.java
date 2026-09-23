package org.fog.utils;

/**
 * Unit-safe accounting value for one completed transfer.
 *
 * <p>Durations remain separate from bytes. Byte-milliseconds are derived only
 * by this type, preventing byte totals from being added to residence-time
 * totals accidentally.</p>
 */
public final class NetworkTransferUsage {

	private final DataSize transferredData;
	private final SimulationDuration queueDuration;
	private final SimulationDuration transferDuration;
	private final SimulationDuration propagationDuration;

	/** Legacy primitive adapter. Prefer the unit-bearing constructor. */
	public NetworkTransferUsage(double transferredBytes,
		double queueDurationMillis, double transferDurationMillis,
		double propagationDurationMillis) {
		this(DataSize.ofBytes(transferredBytes),
			SimulationDuration.ofMilliseconds(queueDurationMillis),
			SimulationDuration.ofMilliseconds(transferDurationMillis),
			SimulationDuration.ofMilliseconds(propagationDurationMillis));
	}

	public NetworkTransferUsage(DataSize transferredData,
		SimulationDuration queueDuration, SimulationDuration transferDuration,
		SimulationDuration propagationDuration) {
		if (transferredData == null || queueDuration == null
			|| transferDuration == null || propagationDuration == null) {
			throw new IllegalArgumentException(
				"Network transfer usage values cannot be null");
		}
		this.transferredData = transferredData;
		this.queueDuration = queueDuration;
		this.transferDuration = transferDuration;
		this.propagationDuration = propagationDuration;
		requireFinite(getTotalUsageByteMilliseconds(),
			"Transfer byte-milliseconds");
	}

	public double getTransferredBytes() {
		return transferredData.toBytes();
	}

	public DataSize getTransferredData() {
		return transferredData;
	}

	public double getQueueDurationMillis() {
		return queueDuration.toMilliseconds();
	}

	public SimulationDuration getQueueDuration() {
		return queueDuration;
	}

	/** Elapsed serialization/contention time after the transfer became active. */
	public double getTransferDurationMillis() {
		return transferDuration.toMilliseconds();
	}

	public SimulationDuration getTransferDuration() {
		return transferDuration;
	}

	public double getPropagationDurationMillis() {
		return propagationDuration.toMilliseconds();
	}

	public SimulationDuration getPropagationDuration() {
		return propagationDuration;
	}

	public double getQueueUsageByteMilliseconds() {
		return transferredData.toBytes() * queueDuration.toMilliseconds();
	}

	public double getTransferUsageByteMilliseconds() {
		return transferredData.toBytes() * transferDuration.toMilliseconds();
	}

	public double getPropagationUsageByteMilliseconds() {
		return transferredData.toBytes()
			* propagationDuration.toMilliseconds();
	}

	public double getTotalUsageByteMilliseconds() {
		return getQueueUsageByteMilliseconds()
			+ getTransferUsageByteMilliseconds()
			+ getPropagationUsageByteMilliseconds();
	}

	private static void requireFinite(double value, String description) {
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(description + " must be finite");
		}
	}
}
