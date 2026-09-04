package org.fog.placement;

import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.MobileUserRegistration;

/** Owns mobile-user association and lifecycle transitions. */
public final class MobileAssociationService {

	/**
	 * Establishes both wireless and server associations. Registration and
	 * peripheral activation deliberately remain separate transactional steps.
	 */
	public MobileDeviceLifecycle associate(MobileDevice mobileDevice,
		List<ApDevice> accessPoints, Random random) {
		if (mobileDevice == null) {
			throw new IllegalArgumentException("Mobile device cannot be null");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return MobileDeviceLifecycle.FINISHED;
		}
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.SCHEDULED) {
			MobileUserRegistration.beginEntry(mobileDevice);
		}
		MobileDeviceLifecycle previousState = mobileDevice.getLifecycleState();
		normaliseAssociation(mobileDevice);
		if (mobileDevice.getSourceAp() == null
			&& (accessPoints == null || accessPoints.isEmpty()
				|| !ApDevice.connectApSmartThing(accessPoints, mobileDevice,
					random.nextDouble()))) {
			MobileUserRegistration.awaitAssociation(mobileDevice);
			return previousState;
		}

		ApDevice sourceAp = mobileDevice.getSourceAp();
		if (sourceAp.getServerCloudlet() == null) {
			MobileUserRegistration.disconnectNetwork(mobileDevice);
			MobileUserRegistration.awaitAssociation(mobileDevice);
			throw new IllegalStateException("Access point " + sourceAp.getName()
				+ " has no server cloudlet for entering user " + mobileDevice.getName());
		}
		if (mobileDevice.getSourceServerCloudlet() != null
			&& mobileDevice.getSourceServerCloudlet() != sourceAp.getServerCloudlet()) {
			mobileDevice.getSourceServerCloudlet()
				.desconnectServerCloudletSmartThing(mobileDevice);
		}
		if (mobileDevice.getSourceServerCloudlet() == null) {
			try {
				sourceAp.getServerCloudlet()
					.connectServerCloudletSmartThing(mobileDevice);
			}
			catch (RuntimeException error) {
				MobileUserRegistration.disconnectNetwork(mobileDevice);
				MobileUserRegistration.awaitAssociation(mobileDevice);
				throw new IllegalStateException("Could not complete network association for "
					+ mobileDevice.getName(), error);
			}
		}
		return previousState;
	}

	/** Completes an association after the caller has ensured that a VM exists. */
	public void activate(MobileDevice mobileDevice,
		MobileDeviceLifecycle previousState) {
		MobileUserRegistration.activatePeripherals(mobileDevice);
		if (previousState == MobileDeviceLifecycle.DISCONNECTED) {
			MyStatistics.getInstance().finalWithoutConnection(
				mobileDevice.getMyId(), CloudSim.clock());
		}
	}

	/** Disconnects a user while preserving its VM and application state. */
	public void disconnect(MobileDevice mobileDevice) {
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		boolean wasActive = mobileDevice.getLifecycleState()
			== MobileDeviceLifecycle.ACTIVE;
		MobileUserRegistration.disconnectNetwork(mobileDevice);
		MobileUserRegistration.awaitAssociation(mobileDevice);
		if (wasActive) {
			MyStatistics.getInstance().startWithoutConnetion(
				mobileDevice.getMyId(), CloudSim.clock());
		}
	}

	/** Releases network/migration state and permanently finishes a trace. */
	public void finish(MobileDevice mobileDevice) {
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.DISCONNECTED) {
			MyStatistics.getInstance().finalWithoutConnection(
				mobileDevice.getMyId(), CloudSim.clock());
		}
		NetworkSlicing.releaseBandwidth(mobileDevice);
		mobileDevice.setMigStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setLockedToMigration(false);
		NextStep.finishMobility(mobileDevice);
	}

	private static void normaliseAssociation(MobileDevice mobileDevice) {
		if (mobileDevice.getSourceAp() != null
			&& !mobileDevice.getSourceAp().getSmartThings().contains(mobileDevice)) {
			MobileUserRegistration.disconnectNetwork(mobileDevice);
		}
		if (mobileDevice.getSourceAp() == null
			&& mobileDevice.getSourceServerCloudlet() != null) {
			MobileUserRegistration.disconnectNetwork(mobileDevice);
		}
	}
}
