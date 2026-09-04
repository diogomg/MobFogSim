package org.fog.entities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.localization.*;
import org.fog.placement.MobileController;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;

public class ApDevice extends FogDevice {

	private FogDevice serverCloudlet;
	private int maxSmartThing;
	private boolean status;
	private int edge;// verify if ap is edge -> NONE, UPON, BOTTOM, RIGHT or LEFT

	public ApDevice() {
	}

	@Override
	protected void processOtherEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case MobileEvents.START_HANDOFF:
			handoff(ev, MobileController.getRand().nextDouble());
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

	private void handoff(SimEvent ev, double delay) {
		MobileDevice smartThing = (MobileDevice) ev.getData();
		if (smartThing == null
			|| smartThing.getLifecycleState() != MobileDeviceLifecycle.ACTIVE
			|| smartThing.getSourceAp() == null
			|| smartThing.getDestinationAp() == null) {
			return;
		}

		if (getSmartThings().contains(smartThing)) {
			ApDevice destination = smartThing.getDestinationAp();
			if (!transferMobileDevice(smartThing, destination, delay)) {
				return;
			}

			smartThing.setDestinationAp(null);
			smartThing.setHandoffStatus(false);
			LogMobile.debug("ApDevice.java", smartThing.getName()
				+ " was desconnected (inHandoff) to " + getName());

			if (smartThing.isMigStatus()) {
				System.out .println("+++++++++++++++++MAKING THE HANDOFF DURING MIGRATION+++++++++++++: "
					+ smartThing.getName() + " temp: " + CloudSim.clock());
			}
			else {
				System.out.println("++++++++++++++++++++HandoffSimple++++++++++++++++++++: "
					+ smartThing.getName() + " temp: " + CloudSim.clock());
			}
			LogMobile.debug("ApDevice.java", smartThing.getName()
				+ " was connected (inHandoff) to " + smartThing.getSourceAp().getName());

		}
		else {
			System.out.println("*_*_*_*_*_*_*_*_*_*_*_*_*_ABORT MIGRATION*_*_*_*_*_*_*_*_*_*_*_*: "
				+ smartThing.getId());
			smartThing.setMigStatus(false);
			smartThing.setPostCopyStatus(false);
			smartThing.setMigStatusLive(false);
		}
		smartThing.setTimeFinishHandoff(CloudSim.clock());

	}

	public boolean desconnectApSmartThing(MobileDevice st) {
		if (st == null || st.getSourceAp() != this) {
			return false;
		}
		NetworkSlicing.cancelWirelessTransfers(st);
		boolean removed = dissociateMobileDevice(st);
		st.setSourceAp(null);
		if (removed) {
			setUplinkLatency(getUplinkLatency() - 0.002);
		}
		LogMobile.debug("ApDevice.java", st.getName() + " was desconnected to " + getName());
		// remove link
		NetworkTopology.addLink(this.getId(), st.getId(), 0.0, 0.0);
		return removed;
	}

	/** Moves both sides of a wireless association as one validated transition. */
	private boolean transferMobileDevice(MobileDevice mobileDevice,
		ApDevice destination, double delay) {
		if (mobileDevice == null || destination == null
			|| mobileDevice.getSourceAp() != this
			|| !getSmartThings().contains(mobileDevice)) {
			return false;
		}
		if (!Double.isFinite(delay) || delay < 0.0) {
			throw new IllegalArgumentException(
				"Handoff delay must be finite and non-negative");
		}
		NetworkSlicing.cancelWirelessTransfers(mobileDevice);
		dissociateMobileDevice(mobileDevice);
		destination.associateMobileDevice(mobileDevice);
		mobileDevice.setSourceAp(destination);
		setUplinkLatency(Math.max(0.0, getUplinkLatency() - delay));
		destination.setUplinkLatency(destination.getUplinkLatency() + delay);
		NetworkTopology.addLink(getId(), mobileDevice.getId(), 0.0, 0.0);
		NetworkTopology.addLink(destination.getId(), mobileDevice.getId(),
			NetworkSlicing.getAccessPointUplinkBandwidth(destination, mobileDevice),
			delay);
		return true;
	}

	public static boolean connectApSmartThing(List<ApDevice> apDevices, MobileDevice st,
		double delay) {
		if (apDevices == null || st == null) {
			throw new IllegalArgumentException(
				"Access-point list and mobile device cannot be null");
		}
		if (st.getSourceAp() != null) {
			return st.getSourceAp().getSmartThings().contains(st);
		}
		Optional<ApDevice> closestAp = Distances.findClosestAp(apDevices, st);
		if (!closestAp.isPresent()) {
			return false;
		}

		ApDevice apDevice = closestAp.get();
		if (apDevice.getMaxSmartThing() <= apDevice.getSmartThings().size()) {
			return false;
		}

		apDevice.associateMobileDevice(st);
		st.setSourceAp(apDevice);
		NetworkTopology.addLink(apDevice.getId(), st.getId(),
			NetworkSlicing.getAccessPointUplinkBandwidth(apDevice, st), delay);
		LogMobile.debug("ApDevice.java", st.getName() + " was connected to "
			+ apDevice.getName());
		apDevice.setUplinkLatency(apDevice.getUplinkLatency() + delay);
		return true;
	}

	public ApDevice(String name, int coordX, int coordY, int id) {
		super(name, coordX, coordY, id);
		smartThings = new HashSet<>();
		setServerCloudlet(null);
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
		return this.getName() + " [serverCloulet=" + serverCloudlet.getName() + "]";
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
