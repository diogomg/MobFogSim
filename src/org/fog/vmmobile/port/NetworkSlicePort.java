package org.fog.vmmobile.port;

import org.fog.entities.MobileDevice;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;

/** Migration-transfer subset of the run-scoped network-slicing service. */
public interface NetworkSlicePort {
	void releaseBandwidth(MobileDevice mobileDevice);

	void cancelWirelessTransfers(MobileDevice mobileDevice);

	void startMigrationTransfer(MigrationTransferSpec transferSpec);

	NetworkSlicing.MigrationTransferResult completeMigrationTransfer(
		NetworkSlicing.MigrationTransferCompletion completion);
}
