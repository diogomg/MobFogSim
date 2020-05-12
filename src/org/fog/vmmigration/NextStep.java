package org.fog.vmmigration;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.Directions;
import org.fog.vmmobile.constants.Policies;

public class NextStep {

	private static void saveMobility(MobileDevice st) {

		try (FileWriter fw1 = new FileWriter(st.getMyId() + "out.txt", true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(CloudSim.clock() + " " + st.getMyId() + " Position: "
				+ st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: "
				+ st.getDirection() + " Speed: " + st.getSpeed());
			out1.println("Source AP: " + st.getSourceAp() + " Dest AP: " + st.getDestinationAp()
				+ " Host: " + st.getHost().getId());
			out1.println("Local server: " + st.getVmLocalServerCloudlet().getName() + " Apps "
				+ st.getVmLocalServerCloudlet().getActiveApplications() + " Map "
				+ st.getVmLocalServerCloudlet().getApplicationMap());
			if (st.getSourceServerCloudlet() == null) {
				out1.println("Source server: null Apps: null Map: null");
			}
			else {
				out1.println("Source server: " + st.getSourceServerCloudlet().getName() + " Apps: "
					+ st.getSourceServerCloudlet().getActiveApplications() + " Map "
					+ st.getSourceServerCloudlet().getApplicationMap());
			}
			if (st.getDestinationServerCloudlet() == null) {
				out1.println("Dest server: null Apps: null Map: null");
			}
			else {
				out1.println("Dest server: " + st.getDestinationServerCloudlet().getName()
					+ " Apps: " + st.getDestinationServerCloudlet().getActiveApplications()
					+ " Map " + st.getDestinationServerCloudlet().getApplicationMap());
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(st.getMyId() + "route.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + st.getCoord().getCoordX() + "\t"
				+ st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" + st.getSpeed()
				+ "\t" + CloudSim.clock());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(st.getMyId() + "migrationPos.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			if (st.getSourceServerCloudlet() == null)
				out.println(st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() +
					"\t" + CloudSim.clock() + "\t" + st.getMigTime() + "\t"
					+ (CloudSim.clock() + st.getMigTime()));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileWriter fw = new FileWriter(st.getMyId() + "handoffPos.txt", true);
			BufferedWriter bw = new BufferedWriter(fw);
			PrintWriter out = new PrintWriter(bw))
		{
			if (st.isLockedToHandoff())
				out.println(st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() +
					"\t" + CloudSim.clock());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (MyStatistics.getInstance().getInitialWithoutVmTime().get(st.getMyId()) != null) {
			try (FileWriter fw = new FileWriter(st.getMyId() + "withoutVmTime.txt", true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
			{
				if (st.getSourceServerCloudlet() == null)
					out.println(st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() +
						"\t" + CloudSim.clock());
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void nextStep(List<FogDevice> serverCloudlets, List<ApDevice> apDevices,
		List<MobileDevice> smartThings,
		Coordinate coordDevices, int stepPolicy, int seed) {
		MobileDevice st = null;
		Coordinate coordinate = new Coordinate();
		// It makes the new position according direction and speed
		for (int i = 0; i < smartThings.size(); i++) {
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
					smartThings.remove(st);
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
					smartThings.remove(st);
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
