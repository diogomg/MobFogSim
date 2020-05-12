package org.fog.entities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.localization.*;
import org.fog.placement.MobileController;
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
		// TODO Auto-generated method stub
		MobileDevice smartThing = (MobileDevice) ev.getData();
		smartThing.setLockedToHandoff(false);
		LogMobile.debug("ApDevice.java", smartThing.getName() + " has the handoff unlocked");
	}

	private void handoff(SimEvent ev, double delay) {
		MobileDevice smartThing = (MobileDevice) ev.getData();

		if (getSmartThings().contains(smartThing)) {
			// it'll remove the smartThing from ap-smartThing's set
			smartThing.getSourceAp().setSmartThings(smartThing, Policies.REMOVE);
			smartThing.getSourceAp().setUplinkLatency(getUplinkLatency() - delay);
			// remove link
			NetworkTopology.addLink(smartThing.getSourceAp().getId(), smartThing.getId(), 0.0, 0.0);
			smartThing.setSourceAp(smartThing.getDestinationAp());

			smartThing.getSourceAp().setSmartThings(smartThing, Policies.ADD);
			smartThing.getSourceAp().setUplinkLatency(getUplinkLatency() + delay);
			NetworkTopology.addLink(smartThing.getSourceAp().getId(), smartThing.getId(),
				smartThing.getUplinkBandwidth(), delay);

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

	public void desconnectApSmartThing(MobileDevice st) {
		setSmartThings(st, Policies.REMOVE);
		st.setSourceAp(null);
		setUplinkLatency(getUplinkLatency() - 0.002);
		LogMobile.debug("ApDevice.java", st.getName() + " was desconnected to " + getName());
		// remove link
		NetworkTopology.addLink(this.getId(), st.getId(), 0.0, 0.0);
	}

	public static boolean connectApSmartThing(List<ApDevice> apDevices, MobileDevice st,
		double delay) {
		int index = Distances.theClosestAp(apDevices, st);

		if (index >= 0) {
			// it checks the accessPoint limit
			if (apDevices.get(index).getMaxSmartThing() > apDevices.get(index).getSmartThings().size()) {
				st.setSourceAp(apDevices.get(index));
				apDevices.get(index).setSmartThings(st, Policies.ADD);
				NetworkTopology.addLink(apDevices.get(index).getId(), st.getId(),
					st.getUplinkBandwidth(), delay);
				LogMobile.debug("ApDevice.java", st.getName() + " was connected to "
					+ st.getSourceAp().getName());
				apDevices.get(index).setUplinkLatency(
					apDevices.get(index).getUplinkLatency() + delay);
				return true;
			}
			else {// Ap is full
				return false;
			}
		}
		else {// The next Ap is far way
			return false;
		}

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
