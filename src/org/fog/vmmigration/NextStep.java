package org.fog.vmmigration;

import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.localization.Coordinate;
import org.fog.placement.MobileController;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.MobileUserRegistration;

public class NextStep {

	private static void saveMobility(MobileDevice st) {
		String sourceServer = st.getSourceServerCloudlet() == null
			? "Source server: null Apps: null Map: null"
			: "Source server: " + st.getSourceServerCloudlet().getName() + " Apps: "
				+ st.getSourceServerCloudlet().getActiveApplications() + " Map "
				+ st.getSourceServerCloudlet().getApplicationMap();
		String destinationServer = st.getDestinationServerCloudlet() == null
			? "Dest server: null Apps: null Map: null"
			: "Dest server: " + st.getDestinationServerCloudlet().getName()
				+ " Apps: " + st.getDestinationServerCloudlet().getActiveApplications()
				+ " Map " + st.getDestinationServerCloudlet().getApplicationMap();

		String host = st.getCharacteristics() == null || st.getHostList().isEmpty()
			? "null" : Integer.toString(st.getHost().getId());
		FogDevice localServer = st.getVmLocalServerCloudlet();
		String localServerDescription = localServer == null
			? "Local server: null Apps: null Map: null"
			: "Local server: " + localServer.getName() + " Apps "
				+ localServer.getActiveApplications() + " Map "
				+ localServer.getApplicationMap();

		BufferedFileManager.writeLines(st.getMyId() + "out.txt",
			CloudSim.clock() + " " + st.getMyId() + " Position: "
				+ st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: "
				+ st.getDirection() + " Speed: " + st.getSpeed(),
			"Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp()
				+ " Host: " + host,
			localServerDescription,
			sourceServer, destinationServer);

		BufferedFileManager.writeLine(st.getMyId() + "route.txt",
			st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t"
				+ st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" + st.getSpeed()
				+ "\t" + CloudSim.clock());

		BufferedFileManager.open(st.getMyId() + "migrationPos.txt");
		if (st.getSourceServerCloudlet() == null) {
			BufferedFileManager.writeLine(st.getMyId() + "migrationPos.txt",
				st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() +
					"\t" + CloudSim.clock() + "\t" + st.getMigTime() + "\t"
					+ (CloudSim.clock() + st.getMigTime()));
		}

		BufferedFileManager.open(st.getMyId() + "handoffPos.txt");
		if (st.isLockedToHandoff()) {
			BufferedFileManager.writeLine(st.getMyId() + "handoffPos.txt",
				st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY()
					+ "\t" + CloudSim.clock());
		}

		if (MyStatistics.getInstance().getInitialWithoutVmTime().get(st.getMyId()) != null) {
			BufferedFileManager.open(st.getMyId() + "withoutVmTime.txt");
			if (st.getSourceServerCloudlet() == null) {
				BufferedFileManager.writeLine(st.getMyId() + "withoutVmTime.txt",
					st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY()
						+ "\t" + CloudSim.clock());
			}
		}
	}

	public static void nextStep(List<FogDevice> serverCloudlets, List<ApDevice> apDevices,
		List<MobileDevice> smartThings,
		Coordinate coordDevices, int stepPolicy, int seed) {
		MobileDevice st = null;
		Coordinate coordinate = new Coordinate();
		// It makes the new position according direction and speed
		for (int i = smartThings.size() - 1; i >= 0; i--) {
			st = smartThings.get(i);
			if (!st.getLifecycleState().acceptsMobilityUpdates()) {
				continue;
			}
			coordinate.newCoordinate(st);
			if (!processCurrentPosition(st)) {
				finishMobility(st);
			}
		}
	}

	/** Records a valid current position or removes a device outside the map. */
	public static boolean processCurrentPosition(MobileDevice smartThing) {
		if (smartThing.getCoord().getCoordX() == -1) {
			return false;
		}
		System.out.println(smartThing.getMyId() + "\t"
			+ smartThing.getCoord().getCoordX() + "\t"
			+ smartThing.getCoord().getCoordY() + "\t" + CloudSim.clock() + "\t"
			+ Calendar.getInstance().getTime());
		saveMobility(smartThing);
		return true;
	}

	/** Ends a trace after its final timestamp has been observed. */
	public static void finishMobility(MobileDevice smartThing) {
		new Coordinate().desableSmartThing(smartThing);
		removeFromSimulation(smartThing);
	}

	private static void removeFromSimulation(MobileDevice smartThing) {
		MobileUserRegistration.disconnectNetwork(smartThing);
		smartThing.setMigStatus(false);
		MobileController.removeSmartThing(smartThing);
		MobileUserRegistration.finishUser(smartThing);
		LogMobile.debug("NextStep.java", smartThing.getName() + " was removed!");
	}
}
