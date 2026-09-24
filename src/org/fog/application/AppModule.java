package org.fog.application;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.power.PowerVm;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;

/**
 * Class representing an application module, the processing elements of the
 * application model of iFogSim.
 * 
 * @author Harshit Gupta
 */
public class AppModule extends PowerVm {

	private String name;
	private String appId;
	private Map<Pair<String, String>, SelectivityModel> selectivityMap;

	/**
	 * Mapping from tupleType emitted by this AppModule to Actuators subscribing
	 * to that tupleType
	 */
	private Map<String, List<Integer>> actuatorSubscriptions;

	// CloudSim's Vm contract initializes modules through inherited mutable APIs.
	@SuppressWarnings("this-escape")
	public AppModule(
		int id,
		String name,
		String appId,
		int userId,
		double mips,
		int ram,
		long bw,
		long size,
		String vmm,
		CloudletScheduler cloudletScheduler,
		Map<Pair<String, String>, SelectivityModel> selectivityMap) {
		super(id, userId, mips, 1, ram, bw, size, 1, vmm, cloudletScheduler, 300);
		setName(name);
		setId(id);
		setAppId(appId);
		setUserId(userId);
		setUid(getUid(userId, id));
		setMips(mips);
		setNumberOfPes(1);
		setRam(ram);
		setBw(bw);
		setSize(size);
		setVmm(vmm);
		setCloudletScheduler(cloudletScheduler);
		setInMigration(false);
		setBeingInstantiated(true);
		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
		setSelectivityMap(selectivityMap);
		setActuatorSubscriptions(new HashMap<String, List<Integer>>());
	}

	@SuppressWarnings("this-escape")
	public AppModule(AppModule operator) {
		super(FogUtils.generateEntityId(), operator.getUserId(), operator.getMips(), 1, operator
			.getRam(), operator.getBw(), operator.getSize(), 1, operator.getVmm(),
			new TupleScheduler(operator.getMips(), 1), operator.getSchedulingInterval());
		setName(operator.getName());
		setAppId(operator.getAppId());
		setInMigration(false);
		setBeingInstantiated(true);
		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
		setSelectivityMap(operator.getSelectivityMap());
		setActuatorSubscriptions(new HashMap<String, List<Integer>>());

	}

	public void subscribeActuator(int id, String tupleType) {
		if (!actuatorSubscriptions.containsKey(tupleType))
			actuatorSubscriptions.put(tupleType, new ArrayList<Integer>());
		actuatorSubscriptions.get(tupleType).add(id);
	}

	public void addSelectivity(String inputTupleType, String outputTupleType,
		SelectivityModel selectivityModel) {
		if (selectivityModel == null) {
			throw new IllegalArgumentException("Selectivity model cannot be null");
		}
		selectivityMap.put(new Pair<String, String>(inputTupleType,
			outputTupleType), selectivityModel);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Map<Pair<String, String>, SelectivityModel> getSelectivityMap() {
		return Collections.unmodifiableMap(selectivityMap);
	}

	public void setSelectivityMap(Map<Pair<String, String>, SelectivityModel> selectivityMap) {
		if (selectivityMap == null) {
			throw new IllegalArgumentException("Selectivity map cannot be null");
		}
		this.selectivityMap =
			new HashMap<Pair<String, String>, SelectivityModel>(selectivityMap);
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public Map<String, List<Integer>> getActuatorSubscriptions() {
		Map<String, List<Integer>> snapshot =
			new HashMap<String, List<Integer>>();
		for (Map.Entry<String, List<Integer>> entry
			: actuatorSubscriptions.entrySet()) {
			snapshot.put(entry.getKey(), Collections.unmodifiableList(
				new ArrayList<Integer>(entry.getValue())));
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public void setActuatorSubscriptions(Map<String, List<Integer>> actuatorSubscriptions) {
		if (actuatorSubscriptions == null) {
			throw new IllegalArgumentException(
				"Actuator subscriptions cannot be null");
		}
		this.actuatorSubscriptions = new HashMap<String, List<Integer>>();
		for (Map.Entry<String, List<Integer>> entry
			: actuatorSubscriptions.entrySet()) {
			this.actuatorSubscriptions.put(entry.getKey(),
				new ArrayList<Integer>(entry.getValue()));
		}
	}

	@Override
	public String toString() {
		return "AppModule [name=" + name + ", appId=" + appId + ", selectivityMap="
			+ selectivityMap + ", actuatorSubscriptions=" + actuatorSubscriptions + "]";
	}
}
