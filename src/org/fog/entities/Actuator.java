package org.fog.entities;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
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
		sendNow(gatewayDeviceId, FogEvents.ACTUATOR_JOINED, getLatency());
	}

	@Override
	public void processEvent(SimEvent ev) {
		switch (ev.getTag()) {
		case FogEvents.TUPLE_ARRIVAL:
			processTupleArrival(ev);
			break;
		}
	}

	public void printResults(String a, String filename) {
		try (FileWriter fw1 = new FileWriter(filename, true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(a);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
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

		for (AppLoop loop : app.getLoops()) {
			if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
				Logger.debug(getName(),
					"Received tuple " + tuple.getCloudletId() + " on " + tuple.getDestModuleName()
						+ ". TupleSource: " + tuple.getSrcModuleName());

				Double startTime = TimeKeeper.getInstance().getEmitTimes()
					.get(tuple.getActualTupleId());
				if (startTime == null)
					break;
				if (!TimeKeeper.getInstance().getLoopIdToCurrentAverage()
					.containsKey(loop.getLoopId())) {
					TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), 0.0);
					TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), 0);
					TimeKeeper.getInstance().getMaxLoopExecutionTime().put(loop.getLoopId(), 0.0);
					printResults(String.valueOf(0), loop.getLoopId() + "LoopId.txt");
					printResults(String.valueOf(0), loop.getLoopId() + "LoopMaxId.txt");
				}
				MobileDevice st = (MobileDevice) CloudSim.getEntity(getGatewayDeviceId());
				double currentAverage = TimeKeeper.getInstance().getLoopIdToCurrentAverage()
					.get(loop.getLoopId());
				int currentCount = TimeKeeper.getInstance().getLoopIdToCurrentNum()
					.get(loop.getLoopId());
				double delay = CloudSim.clock()
					- TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());
				if (MobileController.getSmartThings().contains(st)) {
					if (st != null) {
						if (st.getSourceAp() != null) {
							if (st.getSourceAp().getServerCloudletToVmMigrate() != null) {
								if (st.getVmLocalServerCloudlet() != null) {
									System.out.println("Nao NULO");
									if (st.getSourceAp().getServerCloudlet()
										.equals(st.getVmLocalServerCloudlet())) {
										System.out.println("Primeiro IF");
										delay += NetworkTopology.getDelay(st.getId(), st
											.getSourceAp().getId())
											+ NetworkTopology.getDelay(st.getSourceAp().getId(), st
												.getVmLocalServerCloudlet().getId())
											+ LatencyByDistance.latencyConnection(
												st.getVmLocalServerCloudlet(), st);
									}
									else {
										double sum = NetworkTopology.getDelay(st.getId(), st
											.getSourceAp().getId())
											+ NetworkTopology.getDelay(st.getSourceAp().getId(), st
												.getSourceAp().getServerCloudlet().getId())
											+ 1.0 // router
											+ NetworkTopology.getDelay(st.getSourceAp()
												.getServerCloudlet().getId(), st
												.getVmLocalServerCloudlet().getId())
											+ LatencyByDistance.latencyConnection(
												st.getVmLocalServerCloudlet(), st);
										delay += sum;
									}
								}
								else {
									st.getVmLocalServerCloudlet();
								}
							}
							else {
								st.getSourceAp().getServerCloudletToVmMigrate();
							}
						}
						else {
							st.getSourceAp();
						}
					}

					MyStatistics.getInstance().putLatencyFileValue(delay, CloudSim.clock(),
						app.getAppId(), getMyId(), st.getVmLocalServerCloudlet().getName(),
						tuple.getTupleType());
					if (delay > TimeKeeper.getInstance().getMaxLoopExecutionTime()
						.get(loop.getLoopId())) {
						TimeKeeper.getInstance().getMaxLoopExecutionTime()
							.put(loop.getLoopId(), delay);
						printResults(String.valueOf(delay), loop.getLoopId() + "LoopMaxId.txt");
					}
					TimeKeeper.getInstance().getEmitTimes().remove(tuple.getActualTupleId());
					double newAverage = (currentAverage * currentCount + delay)
						/ (currentCount + 1);
					TimeKeeper.getInstance().getLoopIdToCurrentAverage()
						.put(loop.getLoopId(), newAverage);
					TimeKeeper.getInstance().getLoopIdToCurrentNum()
						.put(loop.getLoopId(), currentCount + 1);
					printResults(String.valueOf(delay), loop.getLoopId() + "LoopId.txt");
					break;
				}
			}
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

}
