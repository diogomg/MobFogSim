package org.fog.vmmigration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.*;
import org.fog.localization.Coordinate;
import org.fog.localization.DiscoverLocalization;
import org.fog.localization.Distances;
import org.fog.localization.MobilitySample;
import org.fog.localization.MobilityTimeline;
import org.fog.vmmobile.AppExample;
import org.fog.vmmobile.constants.*;

public final class Migration {

	private Migration() {
	}

	/**
	 * @param args
	 * @author Marcio Moraes Lopes
	 */

	public static List<ApDevice> apAvailableList(List<ApDevice> oldApList
		, MobileDevice smartThing) {// It looks to cone and return the Aps
									// available list
		List<ApDevice> newApList = new ArrayList<>();
		for (ApDevice ap : oldApList) {
			if (!smartThing.getSourceAp().equals(ap))
				newApList.add(ap);
		}
		return newApList;
	}

	// Policy: the closest Ap
	public static Optional<ApDevice> nextAp(List<ApDevice> apDevices,
		MobileDevice smartThing) {
		return Distances.findClosestAp(apAvailableList(apDevices, smartThing),
			smartThing);
	}

	public static boolean insideCone(int smartThingDirection, int zoneDirection) {//
		int ajust1, ajust2;

		if (smartThingDirection == Directions.EAST) {
			ajust1 = Directions.SOUTHEAST;
			ajust2 = Directions.EAST + 1;
		}
		else if (smartThingDirection == Directions.SOUTHEAST) {
			ajust1 = Directions.SOUTHEAST - 1;
			ajust2 = Directions.EAST;
		}
		else {
			ajust1 = smartThingDirection - 1; /* plus 45 degree */
			ajust2 = smartThingDirection + 1;
		}

		/*
		 * Define Migration Zone -> it looks for 135 degree = 45 way + 45 way1
		 * +45 way2
		 */
		if (zoneDirection == smartThingDirection ||
			zoneDirection == ajust1 ||
			zoneDirection == ajust2)
			return true;
		else
			return false;
	}

	private static void saveDistance(int travelTimeId, Coordinate coord_atual,
		Coordinate coord_prev, Coordinate coord_erro, Double dist_atual_prev,
		Double dist_atual_erro, Double dist_prev_erro, int velocidade, String filename) {

		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true))
		{
			out1.println(travelTimeId + "\t" + coord_atual.getCoordX() + "\t"
				+ coord_atual.getCoordY() + "\t" + coord_prev.getCoordX() + "\t"
				+ coord_prev.getCoordY() + "\t" + coord_erro.getCoordX() + "\t"
				+ coord_erro.getCoordY() + "\t" + dist_atual_prev + "\t" + dist_atual_erro + "\t"
				+ dist_prev_erro + "\t" + velocidade);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static List<FogDevice> serverClouletsAvailableList(List<FogDevice> oldServerCloudlets,
		MobileDevice smartThing) {

		Coordinate coord_real = smartThing.getCoord();

		List<MobilitySample> path = smartThing.getMobilityPath();
		// The prediction parameter is elapsed trace time, not a number of rows.
		double targetTime = MobilityTimeline.toTraceTime(CloudSim.clock())
			+ smartThing.getTravelPredicTime();
		MobilitySample predictedSample = MobilityTimeline.sampleAtOrBefore(path, targetTime);

		int x = (int) predictedSample.getX();
		int y = (int) predictedSample.getY();
		Coordinate coord_prev = new Coordinate();
		coord_prev.setCoordX(x);
		coord_prev.setCoordY(y);

		int directionMPError = AppExample.getRand().nextInt(8) + 1;

		// related to the ninth parameter: User Mobility prediction inaccuracy, in meters
		Coordinate coord_inaccurated = Coordinate.newCoordinateWithError(coord_prev,
			smartThing.getMobilityPrecitionError(), directionMPError);

		saveDistance(smartThing.getTravelTimeId(), coord_real, coord_prev, coord_inaccurated,
			Distances.checkDistance(coord_real, coord_prev),
			Distances.checkDistance(coord_real, coord_inaccurated),
			Distances.checkDistance(coord_prev, coord_inaccurated), smartThing.getSpeed(),
			"distance_between_user_cloudlet.txt");

		smartThing.setFutureCoord(coord_inaccurated.getCoordX(), coord_inaccurated.getCoordY());

		List<FogDevice> newServerCloudlets = new ArrayList<>();

		int localServerCloudlet;
		boolean cone;
		for (FogDevice sc : oldServerCloudlets) {
			// return the relative position between Server Cloudlet and smart
			// thing -> set this value
			localServerCloudlet = DiscoverLocalization.discoverLocal(
				smartThing.getFutureCoord(), sc.getCoord());
			cone = insideCone(localServerCloudlet, directionMPError);
			if (cone && sc != smartThing.getSourceServerCloudlet()) {
				newServerCloudlets.add(sc);
			}
		}
		return newServerCloudlets;
	}

	public static Optional<FogDevice> nextServerCloudlet(List<FogDevice> serverCloudlets,
		MobileDevice smartThing) {
		return Distances.findClosestServerCloudlet(
			serverClouletsAvailableList(serverCloudlets, smartThing), smartThing);
	}

	public static boolean isEdgeAp(ApDevice apDevice, MobileDevice smartThing) {
		return apDevice.getServerCloudlet() != smartThing.getSourceServerCloudlet();
	}

	public static Optional<FogDevice> lowestLatencyCostServerCloudlet(
		List<FogDevice> oldServerCloudlets,
		List<ApDevice> oldApDevices, MobileDevice smartThing) {
		List<FogDevice> newServerCloudlets = new ArrayList<>();
		List<FogDevice> numServerCloudlets = new ArrayList<>();

		for (FogDevice sc : oldServerCloudlets) {
			newServerCloudlets.add(sc);
		}

		for (int i = 0; i < 9; i++) {
			Optional<FogDevice> destinationServerCloudlet =
				nextServerCloudlet(newServerCloudlets, smartThing);
			if (!destinationServerCloudlet.isPresent()) {
				break;
			}
			FogDevice selectedServerCloudlet = destinationServerCloudlet.get();
			numServerCloudlets.add(selectedServerCloudlet);
			newServerCloudlets.remove(selectedServerCloudlet);
		}

		if (numServerCloudlets.size() == 0) {
			return Optional.empty();
		}
		// this point numServerCloudlets has + than 0 and - than 10 sc
		Optional<ApDevice> nextAp = nextAp(oldApDevices, smartThing);
		if (!nextAp.isPresent()) {
			return Optional.empty();
		}

		for (FogDevice sc : oldServerCloudlets) {
			System.out.println(sumCostFunction(sc, nextAp.get(), smartThing));
		}

		FogDevice selectedServerCloudlet = null;
		double minimumCost = Double.POSITIVE_INFINITY;
		for (FogDevice sc : numServerCloudlets) {
			double sumCost = sumCostFunction(sc, nextAp.get(), smartThing);
			System.out.println(sumCost);
			if (sumCost < 0) {
				continue;
			}
			if (sumCost < minimumCost) {
				minimumCost = sumCost;
				selectedServerCloudlet = sc;
			}
		}
		return Optional.ofNullable(selectedServerCloudlet);
	}

	public static double sumCostFunction(FogDevice serverCloudlet, ApDevice nextAp,
		MobileDevice smartThing) {
		double sum = -1;
		if (nextAp.getServerCloudlet().equals(serverCloudlet)) {
			sum = NetworkTopology.getDelay(smartThing.getId(), nextAp.getId())
				+ NetworkTopology.getDelay(nextAp.getId(), nextAp.getServerCloudlet().getId())
				+ (1.0 / nextAp.getServerCloudlet().getHost().getAvailableMips())
				+ LatencyByDistance.latencyConnection(nextAp.getServerCloudlet(), smartThing);
		}
		else {
			sum = NetworkTopology.getDelay(smartThing.getId(), nextAp.getId())
				+ NetworkTopology.getDelay(nextAp.getId(), nextAp.getServerCloudlet().getId())
				+ 1.0 // router
				+ NetworkTopology.getDelay(nextAp.getServerCloudlet().getId(),
					serverCloudlet.getId())
				+ (1.0 / serverCloudlet.getHost().getAvailableMips())
				+ LatencyByDistance.latencyConnection(serverCloudlet, smartThing);
		}
		return sum;
	}

}
