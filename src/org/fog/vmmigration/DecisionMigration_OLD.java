package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.fog.entities.*;
import org.fog.localization.*;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;

public class DecisionMigration_OLD {

	private static ApDevice correntAP;
	private static ApDevice nextAp;
	private static FogDevice nextServerCloudlet;

	private static int smartThingPosition;
	private static boolean migZone;
	private static boolean migPoint;

	/**
	 * @return boolean
	 */
	public static boolean decisionMigration(List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, MobileDevice smartThing, int migPointPolicy, int migStrategyPolicy) {
		Migration migration = new Migration();

		if (smartThing.getSpeed() == 0) {// smartThing is mobile
			return false;// no migration
		}

		setCorrentAP(smartThing.getSourceAp());

		// return the relative position between Access point and smart thing -> set this value
		setSmartThingPosition(DiscoverLocalization.discoverLocal(getCorrentAP().getCoord()
			, smartThing.getCoord()));

		setMigPoint(migPointPolicyFunction(migPointPolicy // either (0 or 1) ->policies
			, smartThing));// 0 -> fixed or 1 -> with speed

		// the handoff already has occur. The worst case
		if (getCorrentAP().getServerCloudlet().equals(smartThing.getVmLocalServerCloudlet())) {

			if (!(isMigPoint() && isMigZone())) {
				return false;// no migration
			}

			if (migStrategyPolicy == Policies.LOWEST_LATENCY) {
				// to do this policy
				Optional<FogDevice> selectedServerCloudlet =
					migration.lowestLatencyCostServerCloudlet(
						serverCloudlets, apDevices, smartThing);
				if (!selectedServerCloudlet.isPresent()) {
					return false;
				}
				setNextServerCloudlet(selectedServerCloudlet.get());
				// It creates a temporary List to invoke the nextAp
				List<ApDevice> tempListAps = new ArrayList<>(); 
				for (ApDevice ap : getNextServerCloudlet().getApDevices()) {
					tempListAps.add(ap);
				}

				Optional<ApDevice> selectedAp = migration.nextAp(tempListAps, smartThing);
				if (!selectedAp.isPresent()) {
					return false;// no migration
				}
				setNextAp(selectedAp.get());
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(getNextAp(), smartThing)) {
					return false;// no migration
				}

			}
			else if (migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
				Optional<FogDevice> selectedServerCloudlet =
					migration.nextServerCloudlet(serverCloudlets, smartThing);
				if (!selectedServerCloudlet.isPresent()) {
					return false;
				}
				setNextServerCloudlet(selectedServerCloudlet.get());
				// It creates a temporary List to invoke the nextAp
				List<ApDevice> tempListAps = new ArrayList<>(); 
				for (ApDevice ap : getNextServerCloudlet().getApDevices()) {
					tempListAps.add(ap);
				}

				Optional<ApDevice> selectedAp = migration.nextAp(tempListAps, smartThing);
				if (!selectedAp.isPresent()) {
					return false;// no migration
				}
				setNextAp(selectedAp.get());
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(getNextAp(), smartThing)) {
					return false;// no migration
				}

			}
			else if (migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
				Optional<ApDevice> selectedAp = migration.nextAp(apDevices, smartThing);
				if (!selectedAp.isPresent()) {
					return false;// no migration
				}
				setNextAp(selectedAp.get());
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(getNextAp(), smartThing)) {
					return false;// no migration
				}
				// ServerCloudlet linked with nextap
				setNextServerCloudlet(getNextAp().getServerCloudlet());
			}

			if (!checkLinkStatus(smartThing.getSourceServerCloudlet(),
				getNextServerCloudlet())) {
				return false;

			}

			// to define some policy for this available!!!
			if (!getNextServerCloudlet().isAvailable()) {
				return false;// no migration
			}
		}
		else {
			setNextServerCloudlet(getCorrentAP().getServerCloudlet());
		}
		int serviceType = getNextServerCloudlet().getService().getType();

		if (serviceType == Services.PRIVATE) {
			// it saves the destination serverCloudlet
			smartThing.setDestinationServerCloudlet(getNextServerCloudlet());
			return true;
		}
		else if (serviceType == Services.HIBRID) {// it needs to define the policy
			// it saves the destination serverCloudlet
			smartThing.setDestinationServerCloudlet(getNextServerCloudlet());
			return true;
		}
		else if (serviceType == Services.PUBLIC) {
			float serviceValue = getNextServerCloudlet().getService().getValue();
			if (serviceValue <= smartThing.getMaxServiceValue()) {
				smartThing.setDestinationServerCloudlet(getNextServerCloudlet());
				return true; // the smartThing agrees
			}
			else {
				return false;
			}
		}

		throw new IllegalStateException("Unsupported service type " + serviceType
			+ " for migration destination "
			+ getNextServerCloudlet().getName());

	}// end class

	public static boolean migPointPolicyFunction(int policy, MobileDevice smartThing) {
		return false;
	}

	public static boolean checkLinkStatus(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {

		return sourceServerCloudlet.getNetServerCloudlets() != null
			&& sourceServerCloudlet.getNetServerCloudlets()
				.containsKey(destinationServerCloudlet);
	}

	public static ApDevice getCorrentAP() {
		return correntAP;
	}

	public static void setCorrentAP(ApDevice correntAP) {
		DecisionMigration_OLD.correntAP = correntAP;
	}

	public static int getSmartThingPosition() {
		return smartThingPosition;
	}

	public static void setSmartThingPosition(int smartThingPosition) {
		DecisionMigration_OLD.smartThingPosition = smartThingPosition;
	}

	public static ApDevice getNextAp() {
		return nextAp;
	}

	public static void setNextAp(ApDevice nextAp) {
		DecisionMigration_OLD.nextAp = nextAp;
	}

	public static boolean isMigPoint() {
		return migPoint;
	}

	public static void setMigPoint(boolean migPoint) {
		DecisionMigration_OLD.migPoint = migPoint;
	}

	public static boolean isMigZone() {
		return migZone;
	}

	public static void setMigZone(boolean migZone) {
		DecisionMigration_OLD.migZone = migZone;
	}

	public static FogDevice getNextServerCloudlet() {
		return nextServerCloudlet;
	}

	public static void setNextServerCloudlet(FogDevice nextServerCloudlet) {
		DecisionMigration_OLD.nextServerCloudlet = nextServerCloudlet;
	}

}
