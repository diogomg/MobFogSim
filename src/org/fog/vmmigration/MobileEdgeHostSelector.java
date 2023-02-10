package org.fog.vmmigration;

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
		MobileDevice peer = findClosestEligiblePeer(applicationOwner);
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
		boolean edgeServerAvailable = VmDestinationPolicy.allowsEdgeServers()
			&& ServiceAgreement.serviceAgreement(edgeServerCandidate, applicationOwner);
		if (VmDestinationPolicy.allowsEndDevices()
			&& preferClosestPeerHost(applicationOwner)) {
			return true;
		}
		return edgeServerAvailable;
	}

	private static MobileDevice findClosestEligiblePeer(MobileDevice applicationOwner) {
		MobileDevice closest = null;
		double closestDistance = Double.MAX_VALUE;
		Vm vm = applicationOwner.getVmMobileDevice();
		for (MobileDevice candidate : MobileController.getSmartThings()) {
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
