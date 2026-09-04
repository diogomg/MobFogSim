package org.fog.entities;


import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.GeoLocation;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.Distribution;

public class Sensor extends SimEntity {

	private int gatewayDeviceId;
	private GeoLocation geoLocation;
	private long outputSize;
	private String appId;
	private int userId;
	private String tupleType;
	private String sensorName;
	private String destModuleName;
	private Distribution transmitDistribution;
	private int controllerId;
	private Application app;
	private double latency;
	private boolean enabled = true;

	public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency,
		GeoLocation geoLocation, Distribution transmitDistribution, int cpuLength, int nwLength,
		String tupleType, String destModuleName) {
		super(validateRequiredFields(name, appId, tupleType, transmitDistribution));
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		this.outputSize = 3;
		this.setTransmitDistribution(transmitDistribution);
		setUserId(userId);
		setDestModuleName(destModuleName);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setLatency(latency);

	}

	public Sensor(String name, int userId, String appId, int gatewayDeviceId, double latency,
		GeoLocation geoLocation, Distribution transmitDistribution, String tupleType) {
		super(validateRequiredFields(name, appId, tupleType, transmitDistribution));
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		this.outputSize = 3;
		this.setTransmitDistribution(transmitDistribution);
		setUserId(userId);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setLatency(latency);
	}

	/**
	 * This constructor is called from the code that generates PhysicalTopology
	 * from JSON
	 * 
	 * @param name
	 * @param tupleType
	 * @param string
	 * @param userId
	 * @param appId
	 * @param transmitDistribution
	 */
	public Sensor(String name, String tupleType, int userId, String appId,
		Distribution transmitDistribution) {
		super(validateRequiredFields(name, appId, tupleType, transmitDistribution));
		this.setAppId(appId);
		this.setTransmitDistribution(transmitDistribution);
		setTupleType(tupleType);
		setSensorName(tupleType);
		setUserId(userId);
	}

	public void transmit() {
		AppEdge edge = findApplicationEdge(getApp());

		long cpuLength = (long) edge.getTupleCpuLength();
		long nwLength = (long) edge.getTupleNwLength();

		Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1,
			nwLength, outputSize, new UtilizationModelFull(), new UtilizationModelFull(),
			new UtilizationModelFull());
		tuple.setUserId(getUserId());
		tuple.setTupleType(getTupleType());

		tuple.setDestModuleName(edge.getDestination());
		tuple.setSrcModuleName(getSensorName());
		Logger.debug(getName(), "Sending tuple with tupleId = " + tuple.getCloudletId() + " to "
			+ CloudSim.getEntityName(gatewayDeviceId));

		int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
		tuple.setActualTupleId(actualTupleId);

		send(gatewayDeviceId, getLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
	}

	private int updateTimings(String src, String dest) {
		Application application = getApp();
		for (AppLoop loop : application.getLoops()) {
			if (loop.hasEdge(src, dest)) {

				int tupleId = TimeKeeper.getInstance().getUniqueId();
				TimeKeeper.getInstance().registerLoop(loop.getLoopId());
				TimeKeeper.getInstance().recordEmission(tupleId, CloudSim.clock());
				return tupleId;
			}
		}
		return -1;
	}

	private static String validateRequiredFields(String name, String appId, String tupleType,
		Distribution transmitDistribution) {
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("Sensor application ID cannot be empty");
		}
		if (tupleType == null || tupleType.trim().isEmpty()) {
			throw new IllegalArgumentException("Sensor tuple type cannot be empty");
		}
		if (transmitDistribution == null) {
			throw new IllegalArgumentException(
				"Sensor transmit distribution cannot be null");
		}
		return name;
	}

	private AppEdge findApplicationEdge(Application application) {
		if (application == null) {
			throw new IllegalStateException(
				"Sensor " + getName() + " has no submitted application");
		}

		AppEdge matchingEdge = null;
		for (AppEdge edge : application.getEdges()) {
			if (!getTupleType().equals(edge.getSource())) {
				continue;
			}
			if (getDestModuleName() != null
				&& !getDestModuleName().equals(edge.getDestination())) {
				continue;
			}
			if (matchingEdge != null) {
				throw new IllegalArgumentException("Application " + application.getAppId()
					+ " defines multiple edges for sensor tuple type " + getTupleType());
			}
			matchingEdge = edge;
		}

		if (matchingEdge == null) {
			String destination = getDestModuleName() == null ? ""
				: " to " + getDestModuleName();
			throw new IllegalArgumentException("Application " + application.getAppId()
				+ " does not define an edge from sensor tuple type " + getTupleType()
				+ destination);
		}
		return matchingEdge;
	}

	@Override
	public void startEntity() {
		if (!isEnabled()) {
			return;
		}
		scheduleInitialEvents();
	}

	private void scheduleInitialEvents() {
		send(gatewayDeviceId, CloudSim.getMinTimeBetweenEvents(), FogEvents.SENSOR_JOINED, geoLocation);
		send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
	}

	@Override
	public void processEvent(SimEvent ev) {
		if (!isEnabled()) {
			return;
		}
		switch (ev.getTag()) {
		case FogEvents.TUPLE_ACK:
			break;
		case FogEvents.EMIT_TUPLE:
			transmit();
			send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
			break;
		}

	}

	@Override
	public void shutdownEntity() {

	}

	public int getGatewayDeviceId() {
		return gatewayDeviceId;
	}

	public void setGatewayDeviceId(int gatewayDeviceId) {
		this.gatewayDeviceId = gatewayDeviceId;
	}

	public GeoLocation getGeoLocation() {
		return geoLocation;
	}

	public void setGeoLocation(GeoLocation geoLocation) {
		this.geoLocation = geoLocation;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public String getTupleType() {
		return tupleType;
	}

	public void setTupleType(String tupleType) {
		this.tupleType = tupleType;
	}

	public String getSensorName() {
		return sensorName;
	}

	public void setSensorName(String sensorName) {
		this.sensorName = sensorName;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getDestModuleName() {
		return destModuleName;
	}

	public void setDestModuleName(String destModuleName) {
		this.destModuleName = destModuleName;
	}

	public Distribution getTransmitDistribution() {
		return transmitDistribution;
	}

	public void setTransmitDistribution(Distribution transmitDistribution) {
		this.transmitDistribution = transmitDistribution;
	}

	public int getControllerId() {
		return controllerId;
	}

	public void setControllerId(int controllerId) {
		this.controllerId = controllerId;
	}

	public Application getApp() {
		return app;
	}

	public void setApp(Application app) {
		findApplicationEdge(app);
		this.app = app;
	}

	public Double getLatency() {
		return latency;
	}

	public void setLatency(Double latency) {
		this.latency = latency;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void deactivate() {
		setEnabled(false);
	}

	public void activate() {
		if (isEnabled()) {
			return;
		}
		setEnabled(true);
		if (CloudSim.running()) {
			scheduleInitialEvents();
		}
	}

}
