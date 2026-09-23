package org.fog.entities;

import java.util.Optional;

import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.adapter.CloudSimAdapter;
import org.fog.vmmobile.adapter.LegacySimulationAdapters;
import org.fog.vmmobile.adapter.NetworkSliceAdapter;
import org.fog.vmmobile.port.CloudSimPort;
import org.fog.vmmobile.port.NetworkSlicePort;
import org.fog.vmmobile.port.SimulationEventLog;

/** Owns guarded handoff completion and unlock workflow transitions. */
public final class HandoffCoordinator {
	private final CloudSimPort cloudSim;
	private final NetworkSlicePort networkSlices;
	private final SimulationEventLog events;

	/** Compatibility constructor for entities not yet context-constructed. */
	public HandoffCoordinator() {
		this(CloudSimAdapter.INSTANCE, NetworkSliceAdapter.INSTANCE,
			LegacySimulationAdapters.events());
	}

	public HandoffCoordinator(CloudSimPort cloudSim,
		NetworkSlicePort networkSlices, SimulationEventLog events) {
		if (cloudSim == null || networkSlices == null || events == null) {
			throw new IllegalArgumentException(
				"Handoff coordinator dependencies cannot be null");
		}
		this.cloudSim = cloudSim;
		this.networkSlices = networkSlices;
		this.events = events;
	}

	/** Completes one current reservation; stale and invalid events are harmless. */
	public boolean complete(ApDevice source, Object payload,
		SimulationDuration associationEstablishmentDuration) {
		if (source == null || associationEstablishmentDuration == null) {
			throw new IllegalArgumentException(
				"Handoff source and establishment duration cannot be null");
		}
		HandoffReservation reservation = payload instanceof HandoffReservation
			? (HandoffReservation) payload : null;
		MobileDevice mobileDevice = reservation == null
			? payload instanceof MobileDevice ? (MobileDevice) payload : null
			: reservation.getMobileDevice();
		if (!isEligible(mobileDevice, reservation)) {
			cancel(reservation);
			return false;
		}
		if (!source.getSmartThings().contains(mobileDevice)) {
			recover(source, mobileDevice, reservation);
			events.detail("HandoffCoordinator", () ->
				"Aborting handoff migration for entity " + mobileDevice.getId());
			mobileDevice.setMigStatus(false);
			mobileDevice.setPostCopyStatus(false);
			mobileDevice.setMigStatusLive(false);
			mobileDevice.setTimeFinishHandoff(cloudSim.simulationTimeMillis());
			return false;
		}
		if (!transfer(source, mobileDevice, reservation,
			associationEstablishmentDuration)) {
			recover(source, mobileDevice, reservation);
			return false;
		}

		mobileDevice.setDestinationAp(null);
		mobileDevice.completeHandoff(
			mobileDevice.getNetworkAssociationGeneration());
		events.detail("HandoffCoordinator", () -> mobileDevice.isMigStatus()
			? "Completing handoff during migration for " + mobileDevice.getName()
			: "Completing handoff for " + mobileDevice.getName());
		LogMobile.debug("HandoffCoordinator.java", mobileDevice.getName()
			+ " was connected to " + mobileDevice.getSourceAp().getName());
		mobileDevice.setTimeFinishHandoff(cloudSim.simulationTimeMillis());
		return true;
	}

	/** Unlocks only the generation named by the delayed event. */
	public boolean unlock(Object payload) {
		HandoffUnlockRequest request = payload instanceof HandoffUnlockRequest
			? (HandoffUnlockRequest) payload : null;
		MobileDevice mobileDevice = request == null
			? payload instanceof MobileDevice ? (MobileDevice) payload : null
			: request.getMobileDevice();
		if (mobileDevice == null
			|| mobileDevice.getLifecycleState() == MobileDeviceLifecycle.FINISHED
			|| (request != null && !request.isCurrent())) {
			return false;
		}
		MobileSession.HandoffState state = mobileDevice.getSession().getHandoff();
		if (state != MobileSession.HandoffState.COMPLETED
			&& state != MobileSession.HandoffState.CANCELLED
			&& state != MobileSession.HandoffState.IDLE) {
			return false;
		}
		mobileDevice.getSession().unlockHandoff();
		return true;
	}

	private boolean transfer(ApDevice source, MobileDevice mobileDevice,
		HandoffReservation reservation,
		SimulationDuration associationEstablishmentDuration) {
		ApDevice destination = reservation == null
			? mobileDevice.getDestinationAp()
			: reservation.getDestinationAccessPoint();
		if (destination == null || mobileDevice.getSourceAp() != source) {
			return false;
		}
		if (reservation == null) {
			Optional<HandoffReservation> immediate = destination.reserveHandoffSlot(
				mobileDevice, source, cloudSim.simulationTimeMillis(),
				SimulationDuration.ZERO);
			if (!immediate.isPresent()) {
				return false;
			}
			reservation = immediate.get();
		}
		if (reservation.getSourceAccessPoint() != source
			|| reservation.getMobileDevice() != mobileDevice
			|| mobileDevice.getDestinationAp() != destination
			|| !destination.completeHandoffReservation(reservation)) {
			return false;
		}
		PropagationDelay propagationDelay = mobileDevice.getWirelessAssociation()
			== null
			? PropagationDelay.ofMilliseconds(mobileDevice.getUplinkLatency())
			: mobileDevice.getWirelessAssociation().getPropagationDelay();
		networkSlices.cancelWirelessTransfers(mobileDevice);
		source.dissociateMobileDevice(mobileDevice);
		destination.associateMobileDevice(mobileDevice);
		mobileDevice.establishWirelessAssociation(destination, propagationDelay,
			associationEstablishmentDuration);
		cloudSim.addNetworkLink(source.getId(), mobileDevice.getId(), 0.0, 0.0);
		cloudSim.addNetworkLink(destination.getId(), mobileDevice.getId(),
			mobileDevice.getWirelessAssociation().getBandwidthBitsPerSecond(
				org.fog.utils.NetworkSlicing.WirelessDirection.UPLINK),
			propagationDelay.toMilliseconds());
		if (mobileDevice.getSourceServerCloudlet() != null) {
			mobileDevice.getSourceServerCloudlet().attachChild(mobileDevice.getId(),
				propagationDelay.toMilliseconds());
		}
		return true;
	}

	private static boolean isEligible(MobileDevice mobileDevice,
		HandoffReservation reservation) {
		return mobileDevice != null
			&& mobileDevice.getLifecycleState() == MobileDeviceLifecycle.ACTIVE
			&& mobileDevice.getSourceAp() != null
			&& mobileDevice.getDestinationAp() != null
			&& (reservation == null
				|| mobileDevice.getSession().isCurrentAssociation(
					reservation.getAssociationGeneration()));
	}

	private static void recover(ApDevice source, MobileDevice mobileDevice,
		HandoffReservation reservation) {
		cancel(reservation);
		if (mobileDevice != null && mobileDevice.getSourceAp() == source
			&& source.getSmartThings().contains(mobileDevice)) {
			mobileDevice.setDestinationAp(null);
			mobileDevice.setHandoffStatus(false);
			mobileDevice.setLockedToHandoff(false);
		}
	}

	private static void cancel(HandoffReservation reservation) {
		if (reservation != null) {
			reservation.getDestinationAccessPoint()
				.cancelHandoffReservation(reservation);
		}
	}
}
