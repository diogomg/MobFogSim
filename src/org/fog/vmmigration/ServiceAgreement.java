package org.fog.vmmigration;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.constants.Services;

public class ServiceAgreement {
	private static int serviceType;
	private static float serviceValue;

	public static boolean serviceAgreement(FogDevice serverCloudlet, MobileDevice smartThing) {
		setServiceType(serverCloudlet.getService().getType());

		if (!checkLinkStatus(smartThing.getVmLocalServerCloudlet(), serverCloudlet)) {
			return false;
		}
		else if (!serverCloudlet.isAvailable()) {
			return false;// no migration
		}
		else if (getServiceType() == Services.PRIVATE) {
			smartThing.setDestinationServerCloudlet(serverCloudlet);
			return true;
		}
		else if (getServiceType() == Services.HIBRID) {
			smartThing.setDestinationServerCloudlet(serverCloudlet);
			return true;
		}
		else if (getServiceType() == Services.PUBLIC) {
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
			System.out.println("ServiceAgreement.java - Nao pode passar aqui!");
			System.exit(0);
			return false;
		}
	}

	public static boolean checkLinkStatus(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {

		if (sourceServerCloudlet.getNetServerCloudlets().get(destinationServerCloudlet) != null) {
			return true;
		}
		else {
			return false;
		}
	}

	public static int getServiceType() {
		return serviceType;
	}

	public static void setServiceType(int serviceType) {
		ServiceAgreement.serviceType = serviceType;
	}

	public static float getServiceValue() {
		return serviceValue;
	}

	public static void setServiceValue(float serviceValue) {
		ServiceAgreement.serviceValue = serviceValue;
	}

}