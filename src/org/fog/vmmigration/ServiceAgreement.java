package org.fog.vmmigration;

import org.cloudbus.cloudsim.NetworkTopology;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.SimulationEventSink;
import org.fog.vmmobile.policy.ServiceType;

public class ServiceAgreement {
	private static ServiceType serviceType = ServiceType.PUBLIC;
	private static float serviceValue;

	public static boolean serviceAgreement(FogDevice serverCloudlet, MobileDevice smartThing) {
		Evaluation evaluation = evaluate(serverCloudlet, smartThing);
		setServiceType(evaluation.getServiceType());
		setServiceValue(evaluation.getServiceValue());
		if (!evaluation.isAccepted()) {
			if (evaluation.getReason() != null) {
				SimulationEventSink.current().trace("ServiceAgreement",
					() -> evaluation.getReason());
			}
			return false;
		}
		smartThing.setDestinationServerCloudlet(serverCloudlet);
		return true;
	}

	/** Evaluates service and connectivity constraints without changing either node. */
	public static Evaluation evaluate(FogDevice serverCloudlet,
		MobileDevice smartThing) {
		if (serverCloudlet == null || smartThing == null
			|| serverCloudlet.getService() == null) {
			return Evaluation.rejected(ServiceType.PUBLIC, 0.0f,
				"Migration service inputs are incomplete");
		}
		ServiceType type = serverCloudlet.getService().getServiceType();
		float value = serverCloudlet.getService().getValue();
		if (!checkLinkStatus(smartThing.getVmLocalServerCloudlet(), serverCloudlet)) {
			return Evaluation.rejected(type, value,
				"Migration destination has no direct transport link");
		}
		if (!serverCloudlet.isAvailable()) {
			return Evaluation.rejected(type, value,
				"Migration destination is unavailable");
		}
		if (type == ServiceType.PRIVATE || type == ServiceType.HYBRID) {
			return Evaluation.accepted(type, value);
		}
		if (type == ServiceType.PUBLIC) {
			if (value <= smartThing.getMaxServiceValue()) {
				return Evaluation.accepted(type, value);
			}
			String sourceName = smartThing.getSourceServerCloudlet() == null
				? "null" : smartThing.getSourceServerCloudlet().getName();
			String hostName = smartThing.getVmLocalServerCloudlet() == null
				? "null" : smartThing.getVmLocalServerCloudlet().getName();
			return Evaluation.rejected(type, value,
				"Service at " + serverCloudlet.getName() + " is too expensive for "
					+ smartThing.getName() + "; source " + sourceName
					+ ", VM host " + hostName);
		}
		throw new IllegalStateException("Unsupported service type " + type
			+ " for server cloudlet " + serverCloudlet.getName());
	}

	public static boolean checkLinkStatus(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {
		if (sourceServerCloudlet == null || destinationServerCloudlet == null) {
			return false;
		}
		// Mobile devices use their existing wireless topology path rather than
		// the logical cloudlet-to-cloudlet closure.
		if (sourceServerCloudlet instanceof MobileDevice) {
			return true;
		}

		return NetworkTopology.hasDirectLink(sourceServerCloudlet.getId(),
			destinationServerCloudlet.getId());
	}

	public static int getServiceType() {
		return serviceType.legacyValue();
	}

	public static void setServiceType(int serviceType) {
		setServiceType(ServiceType.fromLegacy(serviceType));
	}

	public static ServiceType getTypedServiceType() {
		return serviceType;
	}

	public static void setServiceType(ServiceType serviceType) {
		if (serviceType == null) {
			throw new IllegalArgumentException("Service type cannot be null");
		}
		ServiceAgreement.serviceType = serviceType;
	}

	public static float getServiceValue() {
		return serviceValue;
	}

	public static void setServiceValue(float serviceValue) {
		ServiceAgreement.serviceValue = serviceValue;
	}

	/** Immutable result of service-policy evaluation. */
	public static final class Evaluation {
		private final boolean accepted;
		private final ServiceType serviceType;
		private final float serviceValue;
		private final String reason;

		private Evaluation(boolean accepted, ServiceType serviceType,
			float serviceValue, String reason) {
			this.accepted = accepted;
			this.serviceType = serviceType;
			this.serviceValue = serviceValue;
			this.reason = reason;
		}

		static Evaluation accepted(ServiceType serviceType, float serviceValue) {
			return new Evaluation(true, serviceType, serviceValue, null);
		}

		static Evaluation rejected(ServiceType serviceType, float serviceValue,
			String reason) {
			return new Evaluation(false, serviceType, serviceValue, reason);
		}

		public boolean isAccepted() {
			return accepted;
		}

		public ServiceType getServiceType() {
			return serviceType;
		}

		public float getServiceValue() {
			return serviceValue;
		}

		public String getReason() {
			return reason;
		}
	}

}
