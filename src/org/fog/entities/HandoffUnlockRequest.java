package org.fog.entities;

/** Immutable generation token for a delayed handoff-unlock event. */
public final class HandoffUnlockRequest {
	private final MobileDevice mobileDevice;
	private final long associationGeneration;

	public HandoffUnlockRequest(MobileDevice mobileDevice,
		long associationGeneration) {
		if (mobileDevice == null || associationGeneration <= 0L) {
			throw new IllegalArgumentException(
				"A handoff unlock requires a mobile device and generation");
		}
		this.mobileDevice = mobileDevice;
		this.associationGeneration = associationGeneration;
	}

	public MobileDevice getMobileDevice() {
		return mobileDevice;
	}

	public long getAssociationGeneration() {
		return associationGeneration;
	}

	public boolean isCurrent() {
		return mobileDevice.getSession().isCurrentAssociation(
			associationGeneration);
	}
}
