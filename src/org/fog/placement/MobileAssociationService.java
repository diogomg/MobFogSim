package org.fog.placement;

import java.util.List;
import java.util.Random;

import org.fog.entities.ApDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.utils.SimulationDuration;
import org.fog.vmmobile.SimulationClock;
import org.fog.vmmobile.adapter.CloudSimAdapter;
import org.fog.vmmobile.adapter.MobileLifecycleAdapter;
import org.fog.vmmobile.adapter.MyStatisticsAdapter;
import org.fog.vmmobile.adapter.NetworkSliceAdapter;
import org.fog.vmmobile.port.MobileLifecyclePort;
import org.fog.vmmobile.port.MobileStatisticsPort;
import org.fog.vmmobile.port.NetworkSlicePort;

/** Owns mobile-user association and lifecycle transitions. */
public final class MobileAssociationService {
	private final SimulationClock clock;
	private final MobileLifecyclePort lifecycle;
	private final NetworkSlicePort networkSlices;
	private final MobileStatisticsPort statistics;

	/** Compatibility constructor; run code injects these ports via SimulationServices. */
	public MobileAssociationService() {
		this(CloudSimAdapter.INSTANCE, MobileLifecycleAdapter.INSTANCE,
			NetworkSliceAdapter.INSTANCE, MyStatisticsAdapter.current());
	}

	public MobileAssociationService(SimulationClock clock,
		MobileLifecyclePort lifecycle, NetworkSlicePort networkSlices,
		MobileStatisticsPort statistics) {
		if (clock == null || lifecycle == null || networkSlices == null
			|| statistics == null) {
			throw new IllegalArgumentException(
				"Association service dependencies cannot be null");
		}
		this.clock = clock;
		this.lifecycle = lifecycle;
		this.networkSlices = networkSlices;
		this.statistics = statistics;
	}

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
			lifecycle.beginEntry(mobileDevice);
		}
		MobileDeviceLifecycle previousState = mobileDevice.getLifecycleState();
		normaliseAssociation(mobileDevice);
		if (mobileDevice.getSourceAp() == null
			&& (accessPoints == null || accessPoints.isEmpty()
				|| !ApDevice.connectApSmartThing(accessPoints, mobileDevice,
					SimulationDuration.ofMilliseconds(random.nextDouble())))) {
			lifecycle.awaitAssociation(mobileDevice);
			return previousState;
		}

		ApDevice sourceAp = mobileDevice.getSourceAp();
		if (sourceAp.getServerCloudlet() == null) {
			lifecycle.disconnectNetwork(mobileDevice);
			lifecycle.awaitAssociation(mobileDevice);
			throw new IllegalStateException("Access point " + sourceAp.getName()
				+ " has no server cloudlet for entering user " + mobileDevice.getName());
		}
		if (mobileDevice.getSourceServerCloudlet() != null
			&& mobileDevice.getSourceServerCloudlet() != sourceAp.getServerCloudlet()) {
			mobileDevice.getSourceServerCloudlet()
				.disconnectServerCloudletSmartThing(mobileDevice);
		}
		if (mobileDevice.getSourceServerCloudlet() == null) {
			try {
				sourceAp.getServerCloudlet()
					.connectServerCloudletSmartThing(mobileDevice);
			}
			catch (RuntimeException error) {
				lifecycle.disconnectNetwork(mobileDevice);
				lifecycle.awaitAssociation(mobileDevice);
				throw new IllegalStateException("Could not complete network association for "
					+ mobileDevice.getName(), error);
			}
		}
		return previousState;
	}

	/** Completes an association after the caller has ensured that a VM exists. */
	public void activate(MobileDevice mobileDevice,
		MobileDeviceLifecycle previousState) {
		lifecycle.activatePeripherals(mobileDevice);
		if (previousState == MobileDeviceLifecycle.DISCONNECTED) {
			statistics.finishWithoutConnection(mobileDevice.getMyId(),
				clock.simulationTimeMillis());
		}
	}

	/** Disconnects a user while preserving its VM and application state. */
	public void disconnect(MobileDevice mobileDevice) {
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		boolean wasActive = mobileDevice.getLifecycleState()
			== MobileDeviceLifecycle.ACTIVE;
		lifecycle.disconnectNetwork(mobileDevice);
		lifecycle.awaitAssociation(mobileDevice);
		if (wasActive) {
			statistics.startWithoutConnection(mobileDevice.getMyId(),
				clock.simulationTimeMillis());
		}
	}

	/**
	 * Releases association and transfer state for the controller-owned user
	 * retirement transaction.
	 */
	void releaseForRetirement(MobileDevice mobileDevice) {
		if (mobileDevice.getLifecycleState() == MobileDeviceLifecycle.DISCONNECTED) {
			statistics.finishWithoutConnection(mobileDevice.getMyId(),
				clock.simulationTimeMillis());
		}
		networkSlices.releaseBandwidth(mobileDevice);
		networkSlices.cancelWirelessTransfers(mobileDevice);
		mobileDevice.setMigStatus(false);
		mobileDevice.setMigStatusLive(false);
		mobileDevice.setPostCopyStatus(false);
		mobileDevice.setLockedToMigration(false);
		lifecycle.finishMobility(mobileDevice);
	}

	private void normaliseAssociation(MobileDevice mobileDevice) {
		if (mobileDevice.getSourceAp() != null
			&& !mobileDevice.getSourceAp().getSmartThings().contains(mobileDevice)) {
			lifecycle.disconnectNetwork(mobileDevice);
		}
		if (mobileDevice.getSourceAp() == null
			&& mobileDevice.getSourceServerCloudlet() != null) {
			lifecycle.disconnectNetwork(mobileDevice);
		}
	}
}
