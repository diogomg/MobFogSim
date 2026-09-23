package org.fog.vmmobile.adapter;

import org.fog.entities.MobileDevice;
import org.fog.utils.MigrationTransferSpec;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.port.NetworkSlicePort;

/** Compatibility adapter for the static network-slicing facade. */
public final class NetworkSliceAdapter implements NetworkSlicePort {
	public static final NetworkSliceAdapter INSTANCE = new NetworkSliceAdapter();

	private NetworkSliceAdapter() {
	}

	@Override
	public void releaseBandwidth(MobileDevice mobileDevice) {
		NetworkSlicing.releaseBandwidth(mobileDevice);
	}

	@Override
	public void cancelWirelessTransfers(MobileDevice mobileDevice) {
		NetworkSlicing.cancelWirelessTransfers(mobileDevice);
	}

	@Override
	public void startMigrationTransfer(MigrationTransferSpec transferSpec) {
		NetworkSlicing.startMigrationTransfer(transferSpec);
	}

	@Override
	public NetworkSlicing.MigrationTransferResult completeMigrationTransfer(
		NetworkSlicing.MigrationTransferCompletion completion) {
		return NetworkSlicing.completeMigrationTransfer(completion);
	}
}
