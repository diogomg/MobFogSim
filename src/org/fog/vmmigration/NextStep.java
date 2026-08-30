package org.fog.vmmigration;

import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.placement.MobileController;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.Directions;
import org.fog.vmmobile.constants.Policies;

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

		BufferedFileManager.writeLines(st.getMyId() + "out.txt",
			CloudSim.clock() + " " + st.getMyId() + " Position: "
				+ st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: "
				+ st.getDirection() + " Speed: " + st.getSpeed(),
			"Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp()
				+ " Host: " + st.getHost().getId(),
			"Local server: " + st.getVmLocalServerCloudlet().getName() + " Apps "
				+ st.getVmLocalServerCloudlet().getActiveApplications() + " Map "
				+ st.getVmLocalServerCloudlet().getApplicationMap(),
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
			if (st.getTravelTimeId() == -1) {
				continue;
			}
			if ((st.getDirection() != Directions.NONE)) {
				coordinate.newCoordinate(st);
			}
			if (st.getCoord().getCoordX() == -1) {

				if (st.getSourceServerCloudlet() != null) {
					int j = 0, indexCloud = 0;
					for (FogDevice sc : serverCloudlets) {
						if (st.getSourceServerCloudlet().equals(sc)) {
							indexCloud = j;
							break;
						}
						j++;
					}

					serverCloudlets.get(indexCloud).getSmartThings().remove(st);

					j = 0;
					int indexAp = 0;
					for (ApDevice ap : apDevices) {
						if (st.getSourceAp().equals(ap)) {
							indexAp = j;
							break;
						}
						j++;
					}
					apDevices.get(indexAp).getSmartThings().remove(st);

					st.setSourceAp(null);
					st.setSourceServerCloudlet(null);

					st.setMigStatus(false);

				}
				if (st.getSourceAp() == null) {
					MobileController.removeSmartThing(st);
					LogMobile.debug("NextStep.java", st.getName() + " was removed!");
				}
				else {
					if (st.getSourceServerCloudlet() != null) {
						// it'll remove the smartThing from serverCloudlets-smartThing's set
						st.getSourceServerCloudlet().setSmartThings(st, Policies.REMOVE);
					}
					// it'll remove the smartThing from ap-smartThing's set
					st.getSourceAp().setSmartThings(st, Policies.REMOVE);
					LogMobile.debug("NextStep.java", st.getName() + " was removed!");
					MobileController.removeSmartThing(st);
				}
			}
			else {
				System.out.println(st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t"
					+ st.getCoord().getCoordY() + "\t" + CloudSim.clock() + "\t"
					+ Calendar.getInstance().getTime());
				saveMobility(st);
			}
		}
	}
}
