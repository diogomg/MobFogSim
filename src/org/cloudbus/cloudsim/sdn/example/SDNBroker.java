/*
 * Title: CloudSimSDN Description: SDN extension for CloudSim Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2015, The University of
 * Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.Constants;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;

/**
 * Broker class for CloudSimSDN example. This class represents a broker (Service
 * Provider) who uses the Cloud data center.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class SDNBroker extends SimEntity {

	private SDNDatacenter datacenter = null;
	private String applicationFileName = null;
	private List<String> workloadFileNames = null;

	private List<Cloudlet> cloudletList;
	private List<Workload> workloads;

	public SDNBroker(String name) throws Exception {
		super(name);
		this.workloadFileNames = new ArrayList<String>();
		this.cloudletList = new ArrayList<Cloudlet>();
		this.workloads = new ArrayList<Workload>();
	}

	@Override
	public void startEntity() {
		sendNow(this.datacenter.getId(), Constants.APPLICATION_SUBMIT, this.applicationFileName);
	}

	@Override
	public void shutdownEntity() {
		List<Vm> vmList = this.datacenter.getVmList();
		for (Vm vm : vmList) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Shuttingdown.. VM:" + vm.getId());
		}
	}

	public void submitDeployApplication(SDNDatacenter dc, String filename) {
		this.datacenter = dc;
		this.applicationFileName = filename;
	}

	public void submitRequests(String filename) {
		this.workloadFileNames.add(filename);
	}

	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();

		switch (tag) {
		case CloudSimTags.VM_CREATE_ACK:
			processVmCreate(ev);
			break;
		case Constants.APPLICATION_SUBMIT_ACK:
			applicationSubmitCompleted(ev);
			break;
		case Constants.REQUEST_COMPLETED:
			requestCompleted(ev);
			break;
		default:
			System.out.println("Unknown event received by " + super.getName() + ". Tag:"
				+ ev.getTag());
		}
	}

	private void processVmCreate(SimEvent ev) {

	}

	private void requestCompleted(SimEvent ev) {

	}

	public List<Cloudlet> getCloudletReceivedList() {
		return cloudletList;
	}

	public static int appId = 0;

	private void applicationSubmitCompleted(SimEvent ev) {
		for (String workloadFileName : this.workloadFileNames) {
			scheduleRequest(workloadFileName);
			SDNBroker.appId++;
		}
	}

	private void scheduleRequest(String workloadFile) {
		WorkloadParser rp = new WorkloadParser(workloadFile, this.getId(),
			new UtilizationModelFull(),
			this.datacenter.getVmNameIdTable(), this.datacenter.getFlowNameIdTable());

		for (Workload wl : rp.getWorkloads()) {
			send(this.datacenter.getId(), wl.time, Constants.REQUEST_SUBMIT, wl.request);
			wl.appId = SDNBroker.appId;
		}

		this.cloudletList.addAll(rp.getAllCloudlets());
		this.workloads.addAll(rp.getWorkloads());
	}

	public List<Workload> getWorkloads() {
		return this.workloads;
	}

}
