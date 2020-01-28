/*
 * Title: CloudSimSDN Description: SDN extension for CloudSim Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2015, The University of
 * Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;

/**
 * This represents switches that maintain routing information. Note that all
 * traffic estimation is calculated within NOS class, not in Switch class.
 * Energy consumption of Switch is calculated in this class by utilization
 * history.
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Switch extends SimEntity implements Node {

	// private static long cont=0;
	// private static long MULTI = 1;

	private static double POWER_CONSUMPTION_IDLE = 66.7;
	private static double POWER_CONSUMPTION_PER_ACTIVE_PORT = 1;
	/*
	 * based on CARPO: Correlation-Aware Power Optimization in Data Center
	 * Networks by Xiaodong Wang et al.
	 */

	int bw;
	long iops;
	double previousTime;
	int rank = -1;
	int currentupports = 0;
	int currentdownports = 0;
	NetworkOperatingSystem nos;

	Node[] upports;
	Node[] downports;

	ArrayList<Link> links = new ArrayList<Link>();

	ForwardingRule forwardingTable;
	RoutingTable routingTable;
	Hashtable<Package, Long> processingTable;

	public Switch(String name, int bw, long iops, int upports, int downports,
		NetworkOperatingSystem nos) {
		super(name);
		this.bw = bw;
		this.iops = iops;
		this.previousTime = 0.0;
		this.nos = nos;

		if (upports > 0)
			this.upports = new Node[upports];
		this.downports = new Node[downports];

		this.forwardingTable = new ForwardingRule();
		this.processingTable = new Hashtable<Package, Long>();
		this.routingTable = new RoutingTable();
	}

	@Override
	public void startEntity() {}

	@Override
	public void shutdownEntity() {}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();

		switch (tag) {
		// case Constants.SDN_INTERNAL_PACKAGE_PROCESS:
		// internalPackageProcessing(); break;
		// case Constants.SDN_PACKAGE: sendToBuffer((Package) ev.getData());
		// break;
		default:
			System.out.println("Unknown event received by " + super.getName() + ". Tag:"
				+ ev.getTag());
		}
	}

	public void addLink(Link l) {
		this.links.add(l);
	}

	/************************************************
	 * Calculate Utilization history
	 ************************************************/
	private List<HistoryEntry> utilizationHistories = null;
	// if switch was idle for 1 hours, it's turned off.
	private static double powerOffDuration = 0;

	public class HistoryEntry {
		public double startTime;
		public int numActivePorts;

		HistoryEntry(double t, int n) {
			startTime = t;
			numActivePorts = n;
		}
	}

	public List<HistoryEntry> getUtilizationHisotry() {
		return utilizationHistories;
	}

	public double getUtilizationEnergyConsumption() {

		double total = 0;
		double lastTime = 0;
		int lastPort = 0;
		if (this.utilizationHistories == null)
			return 0;

		for (HistoryEntry h : this.utilizationHistories) {
			double duration = h.startTime - lastTime;
			double power = calculatePower(lastPort);
			double energyConsumption = power * duration;

			// Assume that the host is turned off when duration is long enough
			if (duration > powerOffDuration && lastPort == 0)
				energyConsumption = 0;

			total += energyConsumption;
			lastTime = h.startTime;
			lastPort = h.numActivePorts;
		}
		return total / 3600;	// transform to Whatt*hour from What*seconds
	}

	public void updateNetworkUtilization() {
		this.addUtilizationEntry();
	}

	public void addUtilizationEntryTermination(double finishTime) {
		if (this.utilizationHistories != null)
			this.utilizationHistories.add(new HistoryEntry(finishTime, 0));
	}

	private void addUtilizationEntry() {
		double time = CloudSim.clock();
		int totalActivePorts = getTotalActivePorts();
		if (utilizationHistories == null)
			utilizationHistories = new ArrayList<HistoryEntry>();
		else {
			HistoryEntry hist = this.utilizationHistories.get(this.utilizationHistories.size() - 1);
			if (hist.numActivePorts == totalActivePorts) {
				return;
			}
		}
		this.utilizationHistories.add(new HistoryEntry(time, totalActivePorts));
	}

	private double calculatePower(int numActivePort) {
		double power = POWER_CONSUMPTION_IDLE + POWER_CONSUMPTION_PER_ACTIVE_PORT * numActivePort;
		return power;
	}

	private int getTotalActivePorts() {
		int num = 0;
		for (Link l : this.links) {
			if (l.isActive())
				num++;
		}
		return num;
	}

	/******* Routeable interface implementation methods ******/

	@Override
	public int getAddress() {
		return super.getId();
	}

	@Override
	public long getBandwidth() {
		return bw;
	}

	@Override
	public void clearVMRoutingTable() {
		this.forwardingTable.clear();
	}

	@Override
	public void addVMRoute(int src, int dest, int flowId, Node to) {
		this.forwardingTable.addRule(src, dest, flowId, to);
	}

	@Override
	public Node getVMRoute(int src, int dest, int flowId) {
		Node route = this.forwardingTable.getRoute(src, dest, flowId);
		if (route == null) {
			this.printVMRoute();
			System.err.println("SDNSwitch.getRoute() ERROR: Cannot find route:" +
				NetworkOperatingSystem.debugVmIdName.get(src) + "->" +
				NetworkOperatingSystem.debugVmIdName.get(dest) + ", flow =" + flowId);
		}

		return route;
	}

	@Override
	public void removeVMRoute(int src, int dest, int flowId) {
		forwardingTable.removeRule(src, dest, flowId);
	}

	@Override
	public void setRank(int rank) {
		this.rank = rank;
	}

	@Override
	public int getRank() {
		return rank;
	}

	@Override
	public void printVMRoute() {
		forwardingTable.printForwardingTable(getName());
	}

	public String toString() {
		return "Switch: " + this.getName();
	}

	@Override
	public void addRoute(Node destHost, Link to) {
		this.routingTable.addRoute(destHost, to);

	}

	@Override
	public List<Link> getRoute(Node destHost) {
		return this.routingTable.getRoute(destHost);
	}

	@Override
	public RoutingTable getRoutingTable() {
		return this.routingTable;
	}
}
