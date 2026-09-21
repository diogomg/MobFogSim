package org.fog.entities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
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
import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;

public class ApDevice extends FogDevice {

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
			handoff(ev, SimulationDuration.ofMilliseconds(
				MobileController.getRand().nextDouble()));
			break;
		case MobileEvents.UNLOCKED_HANDOFF:
			unLockedHandoff(ev);
			break;
		}
	}

	private void unLockedHandoff(SimEvent ev) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		if (smartThing == null
			|| smartThing.getLifecycleState() == MobileDeviceLifecycle.FINISHED) {
			return;
		}
		smartThing.setLockedToHandoff(false);
		LogMobile.debug("ApDevice.java", smartThing.getName() + " has the handoff unlocked");
	}

	private void handoff(SimEvent ev,
		SimulationDuration associationEstablishmentDuration) {
		Object payload = ev.getData();
		HandoffReservation reservation = payload instanceof HandoffReservation
			? (HandoffReservation) payload : null;
		MobileDevice smartThing = reservation == null
			? payload instanceof MobileDevice ? (MobileDevice) payload : null
			: reservation.getMobileDevice();
		if (smartThing == null
			|| smartThing.getLifecycleState() != MobileDeviceLifecycle.ACTIVE
			|| smartThing.getSourceAp() == null
			|| smartThing.getDestinationAp() == null) {
			if (reservation != null) {
				reservation.getDestinationAccessPoint()
					.cancelHandoffReservation(reservation);
			}
			return;
		}

		if (getSmartThings().contains(smartThing)) {
			if (!transferMobileDevice(smartThing, reservation,
				associationEstablishmentDuration)) {
				recoverRejectedHandoff(smartThing, reservation);
				return;
			}

			smartThing.setDestinationAp(null);
			smartThing.setHandoffStatus(false);
			LogMobile.debug("ApDevice.java", smartThing.getName()
				+ " was desconnected (inHandoff) to " + getName());

			if (smartThing.isMigStatus()) {
				SimulationEventSink.current().detail("ApDevice", () ->
					"Completing handoff during migration for " + smartThing.getName());
			}
			else {
				SimulationEventSink.current().detail("ApDevice", () ->
					"Completing handoff for " + smartThing.getName());
			}
			LogMobile.debug("ApDevice.java", smartThing.getName()
				+ " was connected (inHandoff) to " + smartThing.getSourceAp().getName());

		}
		else {
			recoverRejectedHandoff(smartThing, reservation);
			SimulationEventSink.current().detail("ApDevice", () ->
				"Aborting handoff migration for entity " + smartThing.getId());
			smartThing.setMigStatus(false);
			smartThing.setPostCopyStatus(false);
			smartThing.setMigStatusLive(false);
		}
		smartThing.setTimeFinishHandoff(CloudSim.clock());

	}

	private void recoverRejectedHandoff(MobileDevice mobileDevice,
		HandoffReservation reservation) {
		if (reservation != null) {
			reservation.getDestinationAccessPoint()
				.cancelHandoffReservation(reservation);
		}
		if (mobileDevice.getSourceAp() == this
			&& getSmartThings().contains(mobileDevice)) {
			mobileDevice.setDestinationAp(null);
			mobileDevice.setHandoffStatus(false);
			mobileDevice.setLockedToHandoff(false);
		}
	}

	public boolean desconnectApSmartThing(MobileDevice st) {
		if (st == null || st.getSourceAp() != this) {
			return false;
		}
		st.cancelPendingHandoffReservation();
		NetworkSlicing.cancelWirelessTransfers(st);
		boolean removed = dissociateMobileDevice(st);
		st.setSourceAp(null);
		LogMobile.debug("ApDevice.java", st.getName() + " was desconnected to " + getName());
		// remove link
		NetworkTopology.addLink(this.getId(), st.getId(), 0.0, 0.0);
		return removed;
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
		long generation = mobileDevice.advanceNetworkAssociationGeneration();
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
		if (mobileDevice.getNetworkAssociationGeneration()
			== reservation.getAssociationGeneration()) {
			mobileDevice.advanceNetworkAssociationGeneration();
		}
		if (ownedByMobileDevice && mobileDevice.getDestinationAp() == this) {
			mobileDevice.setDestinationAp(null);
			mobileDevice.setHandoffStatus(false);
			mobileDevice.setLockedToHandoff(false);
		}
	}

	/** Moves both sides of a wireless association as one validated transition. */
	private boolean transferMobileDevice(MobileDevice mobileDevice,
		HandoffReservation reservation,
		SimulationDuration associationEstablishmentDuration) {
		if (mobileDevice == null) {
			return false;
		}
		ApDevice destination = reservation == null
			? mobileDevice.getDestinationAp()
			: reservation.getDestinationAccessPoint();
		if (destination == null
			|| mobileDevice.getSourceAp() != this
			|| !getSmartThings().contains(mobileDevice)) {
			return false;
		}
		if (associationEstablishmentDuration == null) {
			throw new IllegalArgumentException(
				"Association establishment duration cannot be null");
		}
		if (reservation == null) {
			Optional<HandoffReservation> immediateReservation =
				destination.reserveHandoffSlot(mobileDevice, this,
					CloudSim.clock(), SimulationDuration.ZERO);
			if (!immediateReservation.isPresent()) {
				return false;
			}
			reservation = immediateReservation.get();
		}
		if (reservation.getSourceAccessPoint() != this
			|| reservation.getMobileDevice() != mobileDevice
			|| mobileDevice.getDestinationAp() != destination
			|| !destination.completeHandoffReservation(reservation)) {
			return false;
		}
		PropagationDelay propagationDelay = mobileDevice.getWirelessAssociation()
			== null
			? PropagationDelay.ofMilliseconds(mobileDevice.getUplinkLatency())
			: mobileDevice.getWirelessAssociation().getPropagationDelay();
		NetworkSlicing.cancelWirelessTransfers(mobileDevice);
		dissociateMobileDevice(mobileDevice);
		destination.associateMobileDevice(mobileDevice);
		mobileDevice.establishWirelessAssociation(destination, propagationDelay,
			associationEstablishmentDuration);
		NetworkTopology.addLink(getId(), mobileDevice.getId(), 0.0, 0.0);
		NetworkTopology.addLink(destination.getId(), mobileDevice.getId(),
			mobileDevice.getWirelessAssociation().getBandwidthBitsPerSecond(
				NetworkSlicing.WirelessDirection.UPLINK),
			propagationDelay.toMilliseconds());
		if (mobileDevice.getSourceServerCloudlet() != null) {
			mobileDevice.getSourceServerCloudlet().attachChild(mobileDevice.getId(),
				propagationDelay.toMilliseconds());
		}
		return true;
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

	public ApDevice(String name, int coordX, int coordY, int id) {
		super(name, coordX, coordY, id);
		smartThings = new HashSet<>();
		setServerCloudlet(null);
		setParentId(-1);
		setMaxSmartThing(0);
		setStatus(true);
		setEdge(0);

	}

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
