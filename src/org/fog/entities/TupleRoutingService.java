package org.fog.entities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;

/** Resolves tuple destinations without sending simulation events. */
public final class TupleRoutingService {

	/** One actuator route selected for a tuple. */
	public static final class ActuatorRoute {
		private final int actuatorId;
		private final double delay;

		private ActuatorRoute(int actuatorId, double delay) {
			this.actuatorId = actuatorId;
			this.delay = delay;
		}

		public int getActuatorId() {
			return actuatorId;
		}

		public double getDelay() {
			return delay;
		}
	}

	/** Returns the immediate child on the route to a descendant entity. */
	public int childRoute(List<Integer> children, int targetDeviceId) {
		if (children == null) {
			throw new IllegalArgumentException("Child list cannot be null");
		}
		for (Integer childId : children) {
			if (childId == null) {
				continue;
			}
			if (targetDeviceId == childId.intValue()) {
				return childId.intValue();
			}
			Object child = entityOrNull(childId.intValue());
			if (child instanceof FogDevice
				&& ((FogDevice) child).getChildIdWithRouteTo(targetDeviceId) != -1) {
				return childId.intValue();
			}
		}
		return -1;
	}

	/** Returns the child route for an actuator tuple, if one exists. */
	public int childRoute(Tuple tuple, List<Integer> children) {
		if (tuple == null) {
			throw new IllegalArgumentException("Tuple cannot be null");
		}
		if (tuple.getDirection() != Tuple.ACTUATOR) {
			return -1;
		}
		Object entity = entityOrNull(tuple.getActuatorId());
		if (!(entity instanceof Actuator)) {
			return -1;
		}
		return childRoute(children, ((Actuator) entity).getGatewayDeviceId());
	}

	/** Selects the directly associated actuator matching the tuple destination. */
	public ActuatorRoute actuatorRoute(Tuple tuple,
		List<Pair<Integer, Double>> associations) {
		if (tuple == null || associations == null) {
			throw new IllegalArgumentException(
				"Tuple and actuator associations cannot be null");
		}
		for (Pair<Integer, Double> association : associations) {
			Object entity = entityOrNull(association.getFirst());
			if (entity instanceof Actuator
				&& tuple.getDestModuleName() != null
				&& tuple.getDestModuleName().equals(
					((Actuator) entity).getActuatorType())) {
				return new ActuatorRoute(association.getFirst(), association.getSecond());
			}
		}
		return null;
	}

	/** Finds the current compute host of a mobile user's migrated VM. */
	public FogDevice vmHost(Tuple tuple, List<MobileDevice> mobileDevices) {
		if (tuple == null || mobileDevices == null) {
			throw new IllegalArgumentException("Tuple and mobile list cannot be null");
		}
		if (tuple.getDestModuleName() == null) {
			return null;
		}
		for (MobileDevice mobileDevice : mobileDevices) {
			if (!(mobileDevice.getVmMobileDevice() instanceof AppModule)) {
				continue;
			}
			AppModule module = (AppModule) mobileDevice.getVmMobileDevice();
			if (tuple.getAppId().equals(module.getAppId())
				&& tuple.getDestModuleName().equals(module.getName())) {
				return mobileDevice.getVmLocalServerCloudlet();
			}
		}
		return null;
	}

	/** Selects mobile children whose VM belongs to the tuple's application. */
	public List<Integer> downstreamChildren(Tuple tuple, List<Integer> childIds) {
		if (tuple == null || childIds == null) {
			throw new IllegalArgumentException("Tuple and child list cannot be null");
		}
		List<Integer> matches = new ArrayList<Integer>();
		for (Integer childId : childIds) {
			if (childId == null) {
				continue;
			}
			Object entity = entityOrNull(childId.intValue());
			if (!(entity instanceof MobileDevice)) {
				continue;
			}
			MobileDevice mobileDevice = (MobileDevice) entity;
			if (mobileDevice.getVmMobileDevice() instanceof AppModule
				&& tuple.getAppId().equals(
					((AppModule) mobileDevice.getVmMobileDevice()).getAppId())) {
				matches.add(childId);
			}
		}
		return Collections.unmodifiableList(matches);
	}

	private static Object entityOrNull(int entityId) {
		try {
			return CloudSim.getEntity(entityId);
		} catch (IndexOutOfBoundsException error) {
			// Stale child/association IDs are not routes.
			return null;
		}
	}
}
