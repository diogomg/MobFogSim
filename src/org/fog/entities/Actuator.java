package org.fog.entities;

import java.io.IOException;
import java.io.PrintWriter;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.placement.MobileController;
import org.fog.utils.FogEvents;
import org.fog.utils.GeoLocation;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.LatencyByDistance;
import org.fog.vmmigration.MyStatistics;

public class Actuator extends SimEntity {

	private int gatewayDeviceId;
	private double latency;
	private GeoLocation geoLocation;
	private String appId;
	private int userId;
	private String actuatorType;
	private Application app;
	private int myId;
	private boolean enabled = true;

	public Actuator(String name, int userId, String appId, int gatewayDeviceId, double latency,
		GeoLocation geoLocation, String actuatorType, String srcModuleName) {
		super(name);
		this.setAppId(appId);
		this.gatewayDeviceId = gatewayDeviceId;
		this.geoLocation = geoLocation;
		setUserId(userId);
		setActuatorType(actuatorType);
		setLatency(latency);
		setMyId(userId);
	}

	public Actuator(String name, int userId, String appId, String actuatorType) {
		super(name);
		this.setAppId(appId);
		setUserId(userId);
		setActuatorType(actuatorType);
		setMyId(userId);
	}

	@Override
	public void startEntity() {
		if (!isEnabled()) {
			return;
		}
		scheduleJoin();
	}

	private void scheduleJoin() {
		sendNow(gatewayDeviceId, FogEvents.ACTUATOR_JOINED, getLatency());
	}

	@Override
	public void processEvent(SimEvent ev) {
		if (!isEnabled()) {
			return;
		}
		switch (ev.getTag()) {
		case FogEvents.TUPLE_ARRIVAL:
			processTupleArrival(ev);
			break;
		}
	}

	public void printResults(String a, String filename) {
		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true))
		{
			out1.println(a);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void processTupleArrival(SimEvent ev) {
		Tuple tuple = (Tuple) ev.getData();
		tuple.setFinalTime(CloudSim.clock());

		String srcModule = tuple.getSrcModuleName();
		String destModule = tuple.getDestModuleName();
		Application app = getApp();
		if (app == null) {
			throw new IllegalStateException(
				"Actuator " + getName() + " has no submitted application");
		}

		for (AppLoop loop : app.getLoops()) {
			if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
				Logger.debug(getName(),
					"Received tuple " + tuple.getCloudletId() + " on " + tuple.getDestModuleName()
						+ ". TupleSource: " + tuple.getSrcModuleName());

				TimeKeeper timeKeeper = TimeKeeper.getInstance();
				Double startTime = timeKeeper.getEmissionTime(
					tuple.getActualTupleId());
				if (startTime == null)
					break;
				if (timeKeeper.initialiseLoopTiming(loop.getLoopId())) {
					printResults(String.valueOf(0), loop.getLoopId() + "LoopId.txt");
					printResults(String.valueOf(0), loop.getLoopId() + "LoopMaxId.txt");
				}
				double delay = CloudSim.clock() - startTime;
				MobileDevice mobileGateway = getMobileGateway();
				if (isActiveMobileDevice(mobileGateway)) {
					delay = addMobilePathLatency(delay, mobileGateway);
					recordMobileLatency(delay, app, tuple, mobileGateway);
				}

				if (timeKeeper.recordLoopDelay(loop.getLoopId(), delay)) {
					printResults(String.valueOf(delay), loop.getLoopId() + "LoopMaxId.txt");
				}
				timeKeeper.consumeEmissionTime(tuple.getActualTupleId());
				printResults(String.valueOf(delay), loop.getLoopId() + "LoopId.txt");
				break;
			}
		}
	}

	private MobileDevice getMobileGateway() {
		SimEntity gateway = CloudSim.getEntity(getGatewayDeviceId());
		return gateway instanceof MobileDevice ? (MobileDevice) gateway : null;
	}

	private boolean isActiveMobileDevice(MobileDevice mobileDevice) {
		return mobileDevice != null && MobileController.getSmartThings() != null
			&& MobileController.getSmartThings().contains(mobileDevice)
			&& mobileDevice.getLifecycleState() == MobileDeviceLifecycle.ACTIVE;
	}

	private double addMobilePathLatency(double delay, MobileDevice mobileDevice) {
		if (mobileDevice.getSourceAp() == null
			|| mobileDevice.getSourceAp().getServerCloudletToVmMigrate() == null
			|| mobileDevice.getSourceAp().getServerCloudlet() == null
			|| mobileDevice.getVmLocalServerCloudlet() == null) {
			return delay;
		}

		if (mobileDevice.getSourceAp().getServerCloudlet()
			.equals(mobileDevice.getVmLocalServerCloudlet())) {
			return delay + NetworkTopology.getDelay(mobileDevice.getId(),
				mobileDevice.getSourceAp().getId())
				+ NetworkTopology.getDelay(mobileDevice.getSourceAp().getId(),
					mobileDevice.getVmLocalServerCloudlet().getId())
				+ LatencyByDistance.latencyConnection(
					mobileDevice.getVmLocalServerCloudlet(), mobileDevice);
		}

		return delay + NetworkTopology.getDelay(mobileDevice.getId(),
			mobileDevice.getSourceAp().getId())
			+ NetworkTopology.getDelay(mobileDevice.getSourceAp().getId(),
				mobileDevice.getSourceAp().getServerCloudlet().getId())
			+ 1.0 // router
			+ NetworkTopology.getDelay(
				mobileDevice.getSourceAp().getServerCloudlet().getId(),
				mobileDevice.getVmLocalServerCloudlet().getId())
			+ LatencyByDistance.latencyConnection(
				mobileDevice.getVmLocalServerCloudlet(), mobileDevice);
	}

	private void recordMobileLatency(double delay, Application application, Tuple tuple,
		MobileDevice mobileDevice) {
		if (mobileDevice.getVmLocalServerCloudlet() == null) {
			return;
		}
		MyStatistics.getInstance().putLatencyFileValue(delay, CloudSim.clock(),
			application.getAppId(), getMyId(),
			mobileDevice.getVmLocalServerCloudlet().getName(), tuple.getTupleType());
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

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getActuatorType() {
		return actuatorType;
	}

	public void setActuatorType(String actuatorType) {
		this.actuatorType = actuatorType;
	}

	public Application getApp() {
		return app;
	}

	public void setApp(Application app) {
		this.app = app;
	}

	public double getLatency() {
		return latency;
	}

	public void setLatency(double latency) {
		this.latency = latency;
	}

	public int getMyId() {
		return myId;
	}

	public void setMyId(int myId) {
		this.myId = myId;
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
			scheduleJoin();
		}
	}

}
