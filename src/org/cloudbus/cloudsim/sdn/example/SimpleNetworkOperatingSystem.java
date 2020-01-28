/*
 * Title: CloudSimSDN Description: SDN extension for CloudSim Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2015, The University of
 * Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Middlebox;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.TimedVm;

/**
 * Simple network operating system class for the example. In this example,
 * network operating system (aka SDN controller) finds shortest path when
 * deploying the application onto the cloud.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SimpleNetworkOperatingSystem extends NetworkOperatingSystem {

	public SimpleNetworkOperatingSystem(String fileName) {
		super(fileName);
	}

	@Override
	public boolean deployApplication(List<Vm> vms, List<Middlebox> middleboxes, List<Arc> links) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying application..");

		for (Vm vm : vms)
		{
			TimedVm tvm = (TimedVm) vm;
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #"
				+ vm.getId() + " in " + datacenter.getName() + ", (" + tvm.getStartTime() + "~"
				+ tvm.getFinishTime() + ")");
			send(datacenter.getId(), tvm.getStartTime(), CloudSimTags.VM_CREATE_ACK, vm);

			if (tvm.getFinishTime() != Double.POSITIVE_INFINITY) {
				// System.err.println("VM will be terminated at: "+tvm.getFinishTime());
				send(datacenter.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, vm);
				send(this.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, vm);
			}
		}
		return true;
	}

	public boolean deployFlow(List<Arc> links) {
		for (Arc link : links) {
			int srcVm = link.getSrcId();
			int dstVm = link.getDstId();
			int flowId = link.getFlowId();

			SDNHost srchost = findSDNHost(srcVm);
			SDNHost dsthost = findSDNHost(dstVm);
			if (srchost == null || dsthost == null) {
				continue;
			}

			if (srchost.equals(dsthost)) {
				Log.printLine(CloudSim.clock() + ": " + getName()
					+ ": Source SDN Host is same as Destination. Go loopback");
				srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
			}
			else {
				Log.printLine(CloudSim.clock() + ": " + getName()
					+ ": VMs are in different hosts. Create entire routing table (hosts, switches)");
				boolean findRoute = buildForwardingTables(srchost, srcVm, dstVm, flowId, null);

				if (!findRoute) {
					System.err.println("SimpleNetworkOperatingSystem.deployFlow: Could not find route!!"
						+ NetworkOperatingSystem.debugVmIdName.get(srcVm) + "->"
						+ NetworkOperatingSystem.debugVmIdName.get(dstVm));
				}
			}

		}

		// Print all routing tables.
		for (Node node : this.topology.getAllNodes()) {
			node.printVMRoute();
		}
		return true;
	}

	int i = 0;

	private Link selectLinkByFlow(List<Link> links, int flowId) {
		if (flowId == -1)
			return links.get(0);
		else
			return links.get(1 % links.size());

	}



	private boolean buildForwardingTables(Node node, int srcVm, int dstVm, int flowId, Node prevNode) {
		// There are many links. Determine which hop to go.
		SDNHost desthost = findSDNHost(dstVm);
		if (node.equals(desthost))
			return true;

		List<Link> nextLinks = node.getRoute(desthost);

		// Let's choose the first link. make simple
		Link nextLink = selectLinkByFlow(nextLinks, flowId);
		Node nextHop = nextLink.getOtherNode(node);

		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
		buildForwardingTables(nextHop, srcVm, dstVm, flowId, null);

		return true;

	}

	@Override
	protected Middlebox deployMiddlebox(String type, Vm vm) {
		return null;
	}

	@Override
	public void processVmCreateAck(SimEvent ev) {
		// print the created VM info
		TimedVm vm = (TimedVm) ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + ": VM Created: " + vm.getId() + " in "
			+ this.findSDNHost(vm.getId()));
		deployFlow(this.arcList);
	}
}
