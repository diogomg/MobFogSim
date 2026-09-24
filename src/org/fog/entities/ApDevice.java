package org.fog.entities;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.localization.*;
import org.fog.placement.MobileController;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.PropagationDelay;
import org.fog.utils.SimulationDuration;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.constants.MobileEvents;

public class ApDevice extends FogDevice {
	private final HandoffCoordinator handoffCoordinator =
		new HandoffCoordinator();

	private FogDevice serverCloudlet;
	private int maxSmartThing;
	private boolean status;
	private int edge;// verify if ap is edge -> NONE, UPON, BOTTOM, RIGHT or LEFT
	private final Map<Long, HandoffReservation> handoffReservations =
		new LinkedHashMap<Long, HandoffReservation>();
	private long nextHandoffReservationId = 1L;

	public ApDevice() {
	}

	@Override
	protected void processOtherEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case MobileEvents.START_HANDOFF:
			handoffCoordinator.complete(this, ev.getData(),
				SimulationDuration.ofMilliseconds(
				MobileController.getRand().nextDouble()));
			break;
		case MobileEvents.UNLOCKED_HANDOFF:
			if (handoffCoordinator.unlock(ev.getData())) {
				MobileDevice mobileDevice = ev.getData() instanceof HandoffUnlockRequest
					? ((HandoffUnlockRequest) ev.getData()).getMobileDevice()
					: (MobileDevice) ev.getData();
				LogMobile.debug("ApDevice.java", mobileDevice.getName()
					+ " has the handoff unlocked");
			}
			break;
		}
	}

	public boolean disconnectApSmartThing(MobileDevice st) {
		if (st == null || st.getSourceAp() != this) {
			return false;
		}
		st.cancelPendingHandoffReservation();
		NetworkSlicing.cancelWirelessTransfers(st);
		boolean removed = dissociateMobileDevice(st);
		st.setSourceAp(null);
		LogMobile.debug("ApDevice.java", st.getName() + " was disconnected from " + getName());
		// remove link
		NetworkTopology.addLink(this.getId(), st.getId(), 0.0, 0.0);
		return removed;
	}

	/** @deprecated Use {@link #disconnectApSmartThing(MobileDevice)}. */
	@Deprecated
	public boolean desconnectApSmartThing(MobileDevice st) {
		return disconnectApSmartThing(st);
	}

	/** Atomically reserves one capacity slot for a delayed handoff. */
	public synchronized Optional<HandoffReservation> reserveHandoffSlot(
		MobileDevice mobileDevice, ApDevice expectedSource,
		double expiresAtMillis, SimulationDuration handoffSetupDuration) {
		if (mobileDevice == null || expectedSource == null
			|| handoffSetupDuration == null
			|| !Double.isFinite(expiresAtMillis)) {
			throw new IllegalArgumentException(
				"Handoff reservation fields cannot be null or non-finite");
		}
		purgeExpiredHandoffReservations(CloudSim.clock());
		if (mobileDevice.getLifecycleState() != MobileDeviceLifecycle.ACTIVE
			|| mobileDevice.getSourceAp() != expectedSource
			|| expectedSource == this
			|| mobileDevice.getPendingHandoffReservation() != null
			|| getMaxSmartThing() <= 0
			|| getSmartThings().size() + handoffReservations.size()
				>= getMaxSmartThing()) {
			return Optional.empty();
		}
		double now = CloudSim.clock();
		if (expiresAtMillis < now
			|| expiresAtMillis < now
				+ handoffSetupDuration.toMilliseconds()) {
			throw new IllegalArgumentException(
				"Handoff reservation expires before completion");
		}
		if (nextHandoffReservationId == Long.MAX_VALUE) {
			throw new IllegalStateException(
				"Handoff reservation identifiers are exhausted for " + getName());
		}
		long generation = mobileDevice.getSession().getHandoff()
			== MobileSession.HandoffState.RESERVED
			? mobileDevice.getNetworkAssociationGeneration()
			: mobileDevice.beginHandoffReservation();
		HandoffReservation reservation = new HandoffReservation(
			nextHandoffReservationId++, mobileDevice, expectedSource, this,
			generation, now, expiresAtMillis, handoffSetupDuration);
		handoffReservations.put(reservation.getReservationId(), reservation);
		mobileDevice.installHandoffReservation(reservation);
		return Optional.of(reservation);
	}

	/** Converts exactly one current reservation into permission to associate. */
	public synchronized boolean completeHandoffReservation(
		HandoffReservation reservation) {
		purgeExpiredHandoffReservations(CloudSim.clock());
		if (reservation == null
			|| reservation.getDestinationAccessPoint() != this
			|| handoffReservations.get(reservation.getReservationId())
				!= reservation) {
			return false;
		}
		MobileDevice mobileDevice = reservation.getMobileDevice();
		if (mobileDevice.getPendingHandoffReservation() != reservation
			|| mobileDevice.getNetworkAssociationGeneration()
				!= reservation.getAssociationGeneration()
			|| mobileDevice.getLifecycleState() != MobileDeviceLifecycle.ACTIVE
			|| mobileDevice.getSourceAp()
				!= reservation.getSourceAccessPoint()
			|| mobileDevice.getDestinationAp() != this
			|| !reservation.getSourceAccessPoint().getSmartThings()
				.contains(mobileDevice)
			|| getSmartThings().size() >= getMaxSmartThing()) {
			cancelHandoffReservation(reservation);
			return false;
		}
		handoffReservations.remove(reservation.getReservationId());
		mobileDevice.clearHandoffReservation(reservation);
		if (!mobileDevice.beginHandoffTransfer(
			reservation.getAssociationGeneration())) {
			return false;
		}
		return true;
	}

	/** Releases only the matching token; another user's slot is untouched. */
	public synchronized boolean cancelHandoffReservation(
		HandoffReservation reservation) {
		if (reservation == null
			|| reservation.getDestinationAccessPoint() != this
			|| handoffReservations.get(reservation.getReservationId())
				!= reservation) {
			return false;
		}
		handoffReservations.remove(reservation.getReservationId());
		recoverReservationOwner(reservation);
		return true;
	}

	public synchronized int getHandoffReservationCount() {
		purgeExpiredHandoffReservations(CloudSim.clock());
		return handoffReservations.size();
	}

	public synchronized boolean hasAvailableCapacity() {
		purgeExpiredHandoffReservations(CloudSim.clock());
		return getMaxSmartThing() > 0
			&& getSmartThings().size() + handoffReservations.size()
			< getMaxSmartThing();
	}

	private void purgeExpiredHandoffReservations(double now) {
		Iterator<Map.Entry<Long, HandoffReservation>> iterator =
			handoffReservations.entrySet().iterator();
		while (iterator.hasNext()) {
			HandoffReservation reservation = iterator.next().getValue();
			if (reservation.isExpiredAt(now)) {
				iterator.remove();
				recoverReservationOwner(reservation);
			}
		}
	}

	private void recoverReservationOwner(HandoffReservation reservation) {
		MobileDevice mobileDevice = reservation.getMobileDevice();
		boolean ownedByMobileDevice =
			mobileDevice.getPendingHandoffReservation() == reservation;
		mobileDevice.clearHandoffReservation(reservation);
		mobileDevice.cancelHandoff(reservation.getAssociationGeneration());
		if (ownedByMobileDevice && mobileDevice.getDestinationAp() == this) {
			mobileDevice.setDestinationAp(null);
			mobileDevice.setHandoffStatus(false);
			mobileDevice.setLockedToHandoff(false);
		}
	}

	public static boolean connectApSmartThing(List<ApDevice> apDevices, MobileDevice st,
		SimulationDuration associationEstablishmentDuration) {
		if (apDevices == null || st == null) {
			throw new IllegalArgumentException(
				"Access-point list and mobile device cannot be null");
		}
		if (associationEstablishmentDuration == null) {
			throw new IllegalArgumentException(
				"Association establishment duration cannot be null");
		}
		if (st.getSourceAp() != null) {
			return st.getSourceAp().getSmartThings().contains(st);
		}
		Optional<ApDevice> closestAp = Distances.findClosestAp(apDevices, st);
		if (!closestAp.isPresent()) {
			return false;
		}

		ApDevice apDevice = closestAp.get();
		if (!apDevice.hasAvailableCapacity()) {
			return false;
		}

		apDevice.associateMobileDevice(st);
		PropagationDelay propagationDelay =
			PropagationDelay.ofMilliseconds(st.getUplinkLatency());
		st.establishWirelessAssociation(apDevice, propagationDelay,
			associationEstablishmentDuration);
		NetworkTopology.addLink(apDevice.getId(), st.getId(),
			st.getWirelessAssociation().getBandwidthBitsPerSecond(
				NetworkSlicing.WirelessDirection.UPLINK),
			propagationDelay.toMilliseconds());
		LogMobile.debug("ApDevice.java", st.getName() + " was connected to "
			+ apDevice.getName());
		return true;
	}

	// The access point is registered only after its mutable topology state is set.
	@SuppressWarnings("this-escape")
	public ApDevice(String name, int coordX, int coordY, int id) {
		super(name, coordX, coordY, id);
		smartThings = new HashSet<>();
		setServerCloudlet(null);
		setParentId(-1);
		setMaxSmartThing(0);
		setStatus(true);
		setEdge(0);

	}

	@SuppressWarnings("this-escape")
	public ApDevice(String name, int coordX, int coordY,
		int id, double downLink, double energyCons,
		int max, double upLinkBand, double upLinkLat) {
		super(name, coordX, coordY, id);
		smartThings = new HashSet<>();
		setServerCloudlet(null);
		setParentId(-1);
		setMaxSmartThing(0);
		setStatus(true);
		setEdge(0);
		setDownlinkBandwidth(downLink);
		setEnergyConsumption(energyCons);
		setLevel(2);// 0 - Cloud, 1 - ServerCloudlet, 2 - AccessPoint, 3 - SmartThing
		setMaxSmartThing(max);
		setUplinkBandwidth(upLinkBand);
		setUplinkLatency(upLinkLat);

	}

	@Override
	public String toString() {
		String serverName = serverCloudlet == null
			? "unassigned" : serverCloudlet.getName();
		return getName() + " [serverCloudlet=" + serverName + "]";
	}

	public FogDevice getServerCloudlet() {
		return serverCloudlet;
	}

	public void setServerCloudlet(FogDevice serverCloudlet) {
		this.serverCloudlet = serverCloudlet;
	}

	public int getMaxSmartThing() {
		return maxSmartThing;
	}

	public void setMaxSmartThing(int maxSmartThing) {
		this.maxSmartThing = maxSmartThing;
	}

	public boolean isStatus() {
		return status;
	}

	public void setStatus(boolean status) {
		this.status = status;
	}

	public int getEdge() {
		return edge;
	}

	public void setEdge(int edge) {
		this.edge = edge;
	}
}
