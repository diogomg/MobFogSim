package org.fog.vmmigration;

import org.cloudbus.cloudsim.NetworkTopology;
import org.fog.entities.MobileDevice;

/** Calculates delays that do not represent transferable migration data. */
final class MigrationFixedDelay {

	private MigrationFixedDelay() {
	}

	static double calculateMillis(MobileDevice mobileDevice) {
		return mobileDevice.getVmLocalServerCloudlet().getUplinkLatency()
			+ NetworkTopology.getDelay(mobileDevice.getId(),
				mobileDevice.getVmLocalServerCloudlet().getId())
			+ LatencyByDistance.latencyConnection(
				mobileDevice.getVmLocalServerCloudlet(), mobileDevice);
	}
}
