package org.fog.vmmigration;

import java.util.Collection;
import java.util.Optional;

import org.cloudbus.cloudsim.Vm;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;
import org.fog.placement.MobileController;

/** Selects an eligible peer mobile device as a VM host. */
public final class MobileEdgeHostSelector {

	private MobileEdgeHostSelector() {
	}

	/**
	 * Replaces the currently selected cloudlet destination only when a peer
	 * device is closer to the application owner and has sufficient resources.
	 * The owner itself is never considered as a peer host.
	 */
	public static boolean preferClosestPeerHost(MobileDevice applicationOwner) {
		FogDevice currentDestination = applicationOwner.getDestinationServerCloudlet();
		MobileDevice peer = findClosestEligiblePeer(applicationOwner,
			MobileController.getSmartThings());
		if (peer == null) {
			return false;
		}

		double peerDistance = Distances.checkDistance(applicationOwner.getCoord(),
			peer.getCoord());
		if (currentDestination != null) {
			double currentDistance = Distances.checkDistance(applicationOwner.getCoord(),
				currentDestination.getCoord());
			if (peerDistance >= currentDistance) {
				return false;
			}
		}
		applicationOwner.setDestinationServerCloudlet(peer);
		return true;
	}

	/** Applies the configured edge-device/end-device destination policy. */
	public static boolean selectDestination(MobileDevice applicationOwner,
		FogDevice edgeServerCandidate) {
		Optional<FogDevice> destination = chooseDestination(applicationOwner,
			edgeServerCandidate, MobileController.getSmartThings(),
			VmDestinationPolicy.getDestination());
		if (!destination.isPresent()) {
			return false;
		}
		applicationOwner.setDestinationServerCloudlet(destination.get());
		return true;
	}

	/** Selects a destination without mutating the application owner. */
	public static Optional<FogDevice> chooseDestination(
		MobileDevice applicationOwner, FogDevice edgeServerCandidate,
		Collection<MobileDevice> activeMobileDevices,
		VmDestinationPolicy.Destination policy) {
		if (applicationOwner == null || activeMobileDevices == null
			|| policy == null) {
			throw new IllegalArgumentException(
				"Destination selection inputs cannot be null");
		}
		FogDevice selected = null;
		FogDevice comparisonDestination =
			applicationOwner.getDestinationServerCloudlet();
		if (policy == VmDestinationPolicy.Destination.EDGE_SERVERS
			|| policy == VmDestinationPolicy.Destination.HYBRID) {
			ServiceAgreement.Evaluation service = ServiceAgreement.evaluate(
				edgeServerCandidate, applicationOwner);
			if (service.isAccepted()) {
				selected = edgeServerCandidate;
				comparisonDestination = edgeServerCandidate;
			}
		}
		if (policy == VmDestinationPolicy.Destination.END_DEVICES
			|| policy == VmDestinationPolicy.Destination.HYBRID) {
			MobileDevice peer = findClosestEligiblePeer(applicationOwner,
				activeMobileDevices);
			if (peer != null && (comparisonDestination == null
				|| Distances.checkDistance(applicationOwner.getCoord(), peer.getCoord())
					< Distances.checkDistance(applicationOwner.getCoord(),
						comparisonDestination.getCoord()))) {
				selected = peer;
			}
		}
		return Optional.ofNullable(selected);
	}

	private static MobileDevice findClosestEligiblePeer(
		MobileDevice applicationOwner,
		Collection<MobileDevice> activeMobileDevices) {
		MobileDevice closest = null;
		double closestDistance = Double.MAX_VALUE;
		Vm vm = applicationOwner.getVmMobileDevice();
		for (MobileDevice candidate : activeMobileDevices) {
			if (candidate == applicationOwner || candidate == applicationOwner.getVmLocalServerCloudlet()
				|| candidate.getSourceAp() == null || candidate.isMigStatus()
				|| !candidate.isAvailable() || !candidate.getHost().isSuitableForVm(vm)) {
				continue;
			}
			double distance = Distances.checkDistance(applicationOwner.getCoord(), candidate.getCoord());
			if (distance < closestDistance) {
				closest = candidate;
				closestDistance = distance;
			}
		}
		return closest;
	}
}
