package org.fog.placement;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;

/**
 * Immutable identity and state preconditions for a delayed hand-off connection.
 */
public final class HandoffConnectionRequest {
	private final MobileDevice mobileDevice;
	private final FogDevice expectedSourceServer;
	private final ApDevice destinationAccessPoint;
	private final FogDevice destinationServer;
	private final long associationGeneration;

	public HandoffConnectionRequest(MobileDevice mobileDevice,
		FogDevice expectedSourceServer, ApDevice destinationAccessPoint,
		long associationGeneration) {
		if (mobileDevice == null || expectedSourceServer == null
			|| destinationAccessPoint == null
			|| destinationAccessPoint.getServerCloudlet() == null) {
			throw new IllegalArgumentException(
				"A hand-off connection requires a user, source, AP, and destination server");
		}
		if (mobileDevice.getSourceServerCloudlet() != expectedSourceServer) {
			throw new IllegalArgumentException(
				"The expected source is not the user's current access server");
		}
		if (associationGeneration <= 0L
			|| mobileDevice.getNetworkAssociationGeneration()
				!= associationGeneration) {
			throw new IllegalArgumentException(
				"The hand-off connection generation is not current");
		}
		this.mobileDevice = mobileDevice;
		this.expectedSourceServer = expectedSourceServer;
		this.destinationAccessPoint = destinationAccessPoint;
		this.destinationServer = destinationAccessPoint.getServerCloudlet();
		this.associationGeneration = associationGeneration;
	}

	/** Returns whether the delayed request still describes the current hand-off. */
	public boolean isCurrentFor(FogDevice receivingServer) {
		return receivingServer == destinationServer
			&& mobileDevice.getNetworkAssociationGeneration()
				== associationGeneration
			&& mobileDevice.getLifecycleState() == MobileDeviceLifecycle.ACTIVE
			&& mobileDevice.getSourceServerCloudlet() == null
			&& mobileDevice.getSourceAp() == destinationAccessPoint
			&& destinationAccessPoint.getServerCloudlet() == receivingServer;
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public FogDevice getExpectedSourceServer() {
		return expectedSourceServer;
	}

	public ApDevice getDestinationAccessPoint() {
		return destinationAccessPoint;
	}

	public FogDevice getDestinationServer() {
		return destinationServer;
	}

	public long getAssociationGeneration() {
		return associationGeneration;
	}
}
