package org.fog.vmmigration;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.policy.ServiceType;

public class ServiceAgreement {
	private static ServiceType serviceType = ServiceType.PUBLIC;
	private static float serviceValue;

	public static boolean serviceAgreement(FogDevice serverCloudlet, MobileDevice smartThing) {
		setServiceType(serverCloudlet.getService().getServiceType());

		if (!checkLinkStatus(smartThing.getVmLocalServerCloudlet(), serverCloudlet)) {
			return false;
		}
		else if (!serverCloudlet.isAvailable()) {
			return false;// no migration
		}
		else if (getTypedServiceType() == ServiceType.PRIVATE) {
			smartThing.setDestinationServerCloudlet(serverCloudlet);
			return true;
		}
		else if (getTypedServiceType() == ServiceType.HYBRID) {
			smartThing.setDestinationServerCloudlet(serverCloudlet);
			return true;
		}
		else if (getTypedServiceType() == ServiceType.PUBLIC) {
			setServiceValue(serverCloudlet.getService().getValue());
			if (getServiceValue() <= smartThing.getMaxServiceValue()) {
				smartThing.setDestinationServerCloudlet(serverCloudlet);
				return true; // the smartThing agrees
			}
			else {
				System.out.println("The value is expensive for the " + serverCloudlet.getName());
				System.out.println(smartThing.getName() + ": Source "
					+ smartThing.getSourceServerCloudlet().getName() +
					" - LocalVm " + smartThing.getVmLocalServerCloudlet().getName());
				return false;
			}
		}
		else {
			throw new IllegalStateException("Unsupported service type "
				+ getServiceType() + " for server cloudlet "
				+ serverCloudlet.getName());
		}
	}

	public static boolean checkLinkStatus(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {
		// Mobile devices use their existing wireless topology path rather than
		// the cloudlet-to-cloudlet adjacency map.
		if (sourceServerCloudlet instanceof MobileDevice) {
			return true;
		}

		return sourceServerCloudlet.getNetServerCloudlets() != null
			&& sourceServerCloudlet.getNetServerCloudlets()
				.containsKey(destinationServerCloudlet);
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

}
