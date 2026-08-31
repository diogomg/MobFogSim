package org.fog.vmmigration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.*;
import org.fog.localization.Coordinate;
import org.fog.localization.DiscoverLocalization;
import org.fog.localization.Distances;
import org.fog.vmmobile.AppExample;
import org.fog.vmmobile.constants.*;

public class Migration {

	private static boolean migrationPoint;
	private static boolean migrationZone;
	private int location;
	private ApDevice correntAP;
	private FogDevice correntServerCloudlet;
	private MobileDevice correntSmartThing;
	private ApDevice apAvailable;
	private FogDevice serverCloudletAvailable;
	private int flowDirection;
	private static List<ApDevice> apsAvailable;
	private static List<FogDevice> serverCloudletsAvailable;
	private static int policyReplicaVM;

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
		// It return apDevice list without the smartThing's sourceAp
		setApsAvailable(apAvailableList(apDevices, smartThing));
		return Distances.findClosestAp(getApsAvailable(), smartThing);
	}

	public int nextApFromCloudlet(Set<ApDevice> apDevices, MobileDevice smartThing) {

		return 0;
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

		ArrayList<String[]> path = smartThing.getPath();
		// related to the eighth parameter - User Mobility prediction, in seconds
		int travelTimeId = smartThing.getTravelTimeId() + smartThing.getTravelPredicTime();
		if (travelTimeId >= path.size()) {
			travelTimeId = path.size() - 1;
		}

		String[] coodinates = path.get(travelTimeId);

		int x = (int) Double.parseDouble(coodinates[2]);
		int y = (int) Double.parseDouble(coodinates[3]);
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
		// Policy: the closest serverCloudlet
		setServerCloudletsAvailable(serverClouletsAvailableList(serverCloudlets, smartThing));
		return Distances.findClosestServerCloudlet(getServerCloudletsAvailable(), smartThing);
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

	public static void lowestLatencyCostServerCloudletILP(List<FogDevice> oldServerCloudlets,
		List<ApDevice> oldApDevices, MobileDevice smartThing) {
		List<FogDevice> clusterOfCloudlets = new ArrayList<>();
		List<Double> costList = new ArrayList<>();

		Double sumCost;
		Optional<ApDevice> nextAp = nextAp(oldApDevices, smartThing);
		if (!nextAp.isPresent()) {
			return;
		}

		clusterOfCloudlets = serverClouletsAvailableList(oldServerCloudlets, smartThing);
		for (FogDevice sc : clusterOfCloudlets) {
			sumCost = sumCostFunction(sc, nextAp.get(), smartThing);
			costList.add(sumCost);
		}
		List<List<Double>> latencyMatrix = getLatencyMatrix(smartThing.getFutureCoord());
		latencyMatrix.add(costList);
		setLatencyMatrix(findCluster(smartThing.getFutureCoord()), latencyMatrix);
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

	private static List<List<Double>> getLatencyMatrix(Coordinate futureCoord) {
		// TODO Auto-generated method stub
		return null;
	}

	static void setLatencyMatrix(int cluster, List<List<Double>> latencyMatrix) {

	}

	static int findCluster(Coordinate stCoord) {
		return 1;
	}

	static List<FogDevice> getCluster(int i) {
		return null;
	}

	public static boolean isMigrationPoint() {
		return migrationPoint;
	}

	public static void setMigrationPoint(boolean migrationPoint) {
		Migration.migrationPoint = migrationPoint;
	}

	public static boolean isMigrationZone() {
		return migrationZone;
	}

	public static void setMigrationZone(boolean migrationZone) {
		Migration.migrationZone = migrationZone;
	}

	public int getLocation() {
		return location;
	}

	public void setLocation(int location) {
		this.location = location;
	}

	public ApDevice getCorrentAP() {
		return correntAP;
	}

	public void setCorrentAP(ApDevice correntAP) {
		this.correntAP = correntAP;
	}

	public FogDevice getCorrentServerCloudlet() {
		return correntServerCloudlet;
	}

	public void setCorrentServerCloudlet(FogDevice correntServerCloudlet) {
		this.correntServerCloudlet = correntServerCloudlet;
	}

	public MobileDevice getCorrentSmartThing() {
		return correntSmartThing;
	}

	public void setCorrentSmartThing(MobileDevice correntSmartThing) {
		this.correntSmartThing = correntSmartThing;
	}

	public ApDevice getApAvailable() {
		return apAvailable;
	}

	public void setApAvailable(ApDevice apAvailable) {
		this.apAvailable = apAvailable;
	}

	public FogDevice getServerCloudletAvailable() {
		return serverCloudletAvailable;
	}

	public void setServerCloudletAvailable(FogDevice serverCloudletAvailable) {
		this.serverCloudletAvailable = serverCloudletAvailable;
	}

	public int getFlowDirection() {
		return flowDirection;
	}

	public void setFlowDirection(int flowDirection) {
		this.flowDirection = flowDirection;
	}

	public static List<ApDevice> getApsAvailable() {
		return apsAvailable;
	}

	public static void setApsAvailable(List<ApDevice> apsAvailable) {
		Migration.apsAvailable = apsAvailable;
	}

	public static List<FogDevice> getServerCloudletsAvailable() {
		return serverCloudletsAvailable;
	}

	public static void setServerCloudletsAvailable(List<FogDevice> serverCloudletsAvailable) {
		Migration.serverCloudletsAvailable = serverCloudletsAvailable;
	}

	public static int getPolicyReplicaVM() {
		return policyReplicaVM;
	}

	public static void setPolicyReplicaVM(int policyReplicaVM) {
		Migration.policyReplicaVM = policyReplicaVM;
	}
}
