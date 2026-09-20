package org.fog.entities;

import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkSlicing.WirelessDirection;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;

/**
 * Immutable link state for one mobile-to-access-point association.
 *
 * <p>Establishment time is metadata about joining the AP. It is deliberately
 * separate from the propagation delay used for tuple delivery.</p>
 */
public final class WirelessAssociation {

	private final ApDevice accessPoint;
	private final MobileDevice mobileDevice;
	private final PropagationDelay propagationDelay;
	private final SimulationDuration establishmentDuration;
	private final double uplinkBandwidthBitsPerSecond;
	private final double downlinkBandwidthBitsPerSecond;

	private WirelessAssociation(ApDevice accessPoint, MobileDevice mobileDevice,
		PropagationDelay propagationDelay,
		SimulationDuration establishmentDuration,
		double uplinkBandwidthBitsPerSecond,
		double downlinkBandwidthBitsPerSecond) {
		if (accessPoint == null || mobileDevice == null
			|| propagationDelay == null || establishmentDuration == null) {
			throw new IllegalArgumentException(
				"Wireless association fields cannot be null");
		}
		validateBandwidth(uplinkBandwidthBitsPerSecond);
		validateBandwidth(downlinkBandwidthBitsPerSecond);
		this.accessPoint = accessPoint;
		this.mobileDevice = mobileDevice;
		this.propagationDelay = propagationDelay;
		this.establishmentDuration = establishmentDuration;
		this.uplinkBandwidthBitsPerSecond = uplinkBandwidthBitsPerSecond;
		this.downlinkBandwidthBitsPerSecond = downlinkBandwidthBitsPerSecond;
	}

	public static WirelessAssociation establish(ApDevice accessPoint,
		MobileDevice mobileDevice, PropagationDelay propagationDelay,
		SimulationDuration establishmentDuration) {
		return new WirelessAssociation(accessPoint, mobileDevice,
			propagationDelay, establishmentDuration,
			NetworkSlicing.getAccessPointUplinkBandwidth(accessPoint, mobileDevice),
			NetworkSlicing.getAccessPointDownlinkBandwidth(accessPoint, mobileDevice));
	}

	public ApDevice getAccessPoint() {
		return accessPoint;
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public PropagationDelay getPropagationDelay() {
		return propagationDelay;
	}

	public SimulationDuration getEstablishmentDuration() {
		return establishmentDuration;
	}

	public double getBandwidthBitsPerSecond(WirelessDirection direction) {
		if (direction == null) {
			throw new IllegalArgumentException("Wireless direction cannot be null");
		}
		return direction == WirelessDirection.UPLINK
			? uplinkBandwidthBitsPerSecond : downlinkBandwidthBitsPerSecond;
	}

	private static void validateBandwidth(double bandwidthBitsPerSecond) {
		if (!Double.isFinite(bandwidthBitsPerSecond)
			|| bandwidthBitsPerSecond < 0.0) {
			throw new IllegalArgumentException(
				"Wireless bandwidth must be finite and non-negative");
		}
	}
}
