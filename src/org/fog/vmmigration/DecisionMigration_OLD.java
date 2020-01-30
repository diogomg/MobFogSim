package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.List;
import org.fog.entities.*;
import org.fog.localization.*;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;

public class DecisionMigration_OLD {

	private static ApDevice correntAP;
	private static int nextApId;
	private static int nextServerClouletId;

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
				setNextServerClouletId(migration.lowestLatencyCostServerCloudlet(serverCloudlets,
					apDevices, smartThing));
				if (getNextServerClouletId() < 0) {
					return false;
				}
				// It creates a temporary List to invoke the nextAp
				List<ApDevice> tempListAps = new ArrayList<>(); 
				for (ApDevice ap : serverCloudlets.get(getNextServerClouletId()).getApDevices()) {
					tempListAps.add(ap);
				}

				setNextApId(migration.nextAp(tempListAps, smartThing));
				if (getNextApId() < 0) {
					return false;// no migration
				}
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)) {
					return false;// no migration
				}

			}
			else if (migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET) {
				// choose the closest cloudlet, it returns the Id or -1
				setNextServerClouletId(migration.nextServerCloudlet(serverCloudlets, smartThing));
				if (getNextServerClouletId() < 0) {// Does next ServerCloudlet exist?
					return false;
				}
				// It creates a temporary List to invoke the nextAp
				List<ApDevice> tempListAps = new ArrayList<>(); 
				for (ApDevice ap : serverCloudlets.get(getNextServerClouletId()).getApDevices()) {
					tempListAps.add(ap);
				}

				setNextApId(migration.nextAp(tempListAps, smartThing));
				if (getNextApId() < 0) {
					return false;// no migration
				}
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)) {
					return false;// no migration
				}

			}
			else if (migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_AP) {
				setNextApId(migration.nextAp(apDevices, smartThing));
				if (getNextApId() < 0) {// index is negative
					return false;// no migration
				}
				// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
				if (!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)) {
					return false;// no migration
				}
				// ServerCloudlet linked with nextap
				setNextServerClouletId(apDevices.get(getNextApId()).getServerCloudlet().getMyId());
			}

			if (!checkLinkStatus(smartThing.getSourceServerCloudlet(),
				serverCloudlets.get(getNextServerClouletId()))) {
				return false;

			}

			// to define some policy for this available!!!
			if (!serverCloudlets.get(getNextServerClouletId()).isAvailable()) { 
				return false;// no migration
			}
		}
		else {
			setNextServerClouletId(getCorrentAP().getServerCloudlet().getMyId());
		}
		int serviceType = serverCloudlets.get(getNextServerClouletId()).getService().getType();

		if (serviceType == Services.PRIVATE) {
			// it saves the destination serverCloudlet
			smartThing.setDestinationServerCloudlet(serverCloudlets.get(getNextServerClouletId()));
			return true;
		}
		else if (serviceType == Services.HIBRID) {// it needs to define the policy
			// it saves the destination serverCloudlet
			smartThing.setDestinationServerCloudlet(serverCloudlets.get(getNextServerClouletId()));
			return true;
		}
		else if (serviceType == Services.PUBLIC) {
			float serviceValue = serverCloudlets.get(getNextServerClouletId()).getService().getValue();
			if (serviceValue <= smartThing.getMaxServiceValue()) {
				smartThing.setDestinationServerCloudlet(serverCloudlets
					.get(getNextServerClouletId()));// it saves the destination serverCloudlet
				return true; // the smartThing agrees
			}
			else {
				return false;
			}
		}

		// ABORT SIMULATION
		System.out.println("*******ERROR******");
		System.out.println("DecisionMigration");
		System.exit(0);
		return false;

	}// end class

	public static boolean migPointPolicyFunction(int policy, MobileDevice smartThing) {
		return false;
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

	public static int getNextApId() {
		return nextApId;
	}

	public static void setNextApId(int nextApId) {
		DecisionMigration_OLD.nextApId = nextApId;
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

	public static int getNextServerClouletId() {
		return nextServerClouletId;
	}

	public static void setNextServerClouletId(int nextCloulet) {
		DecisionMigration_OLD.nextServerClouletId = nextCloulet;
	}

}
