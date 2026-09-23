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
import org.fog.placement.MobileController;
import org.fog.vmmobile.adapter.LegacySimulationAdapters;
import org.fog.vmmobile.constants.*;
import org.fog.vmmobile.policy.MovementDirection;

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
		return insideCone(MovementDirection.fromLegacy(smartThingDirection),
			MovementDirection.fromLegacy(zoneDirection));
	}

	public static boolean insideCone(MovementDirection smartThingDirection,
		MovementDirection zoneDirection) {
		return smartThingDirection != null
			&& smartThingDirection.containsInMigrationCone(zoneDirection);
	}

	private static void saveDistance(MigrationPrediction prediction,
		String filename) {

		try (PrintWriter out1 = RunOutputManager.getInstance()
			.newDetailedPrintWriter(filename, true))
		{
			out1.println(prediction.getTravelTimeId() + "\t"
				+ prediction.getActualX() + "\t" + prediction.getActualY() + "\t"
				+ prediction.getPredictedX() + "\t" + prediction.getPredictedY() + "\t"
				+ prediction.getAdjustedX() + "\t" + prediction.getAdjustedY() + "\t"
				+ prediction.getActualToPredictedDistance() + "\t"
				+ prediction.getActualToAdjustedDistance() + "\t"
				+ prediction.getPredictedToAdjustedDistance() + "\t"
				+ prediction.getSpeed());
		} catch (IOException e) {
			throw new IllegalStateException(
				"Could not record migration distance " + filename, e);
		}
	}

	public static List<FogDevice> serverClouletsAvailableList(List<FogDevice> oldServerCloudlets,
		MobileDevice smartThing) {
		ServerPrediction result = predictAvailableServers(oldServerCloudlets,
			smartThing, CloudSim.clock(), MovementDirection.fromLegacy(
				LegacySimulationAdapters.migrationRandom().nextInt(8) + 1));
		applyPrediction(smartThing, result.getPrediction());
		return result.getAvailableServers();
	}

	/** Pure candidate calculation used by migration policies. */
	static ServerPrediction predictAvailableServers(
		List<FogDevice> oldServerCloudlets, MobileDevice smartThing,
		double simulationTimeMillis, MovementDirection predictionErrorDirection) {
		if (oldServerCloudlets == null || smartThing == null
			|| predictionErrorDirection == null) {
			throw new IllegalArgumentException(
				"Migration prediction inputs cannot be null");
		}

		Coordinate coord_real = smartThing.getCoord();

		List<MobilitySample> path = smartThing.getMobilityPath();
		// The prediction parameter is elapsed trace time, not a number of rows.
		double targetTime = MobilityTimeline.toTraceTime(simulationTimeMillis)
			+ smartThing.getTravelPredicTime();
		MobilitySample predictedSample = MobilityTimeline.sampleAtOrBefore(path, targetTime);

		int x = (int) predictedSample.getX();
		int y = (int) predictedSample.getY();
		Coordinate coord_prev = new Coordinate();
		coord_prev.setCoordX(x);
		coord_prev.setCoordY(y);

		// related to the ninth parameter: User Mobility prediction inaccuracy, in meters
		Coordinate coord_inaccurated = Coordinate.newCoordinateWithError(coord_prev,
			smartThing.getMobilityPrecitionError(), predictionErrorDirection);

		MigrationPrediction prediction = new MigrationPrediction(
			smartThing.getTravelTimeId(), coord_real.getCoordX(), coord_real.getCoordY(),
			coord_prev.getCoordX(), coord_prev.getCoordY(),
			coord_inaccurated.getCoordX(), coord_inaccurated.getCoordY(),
			Distances.checkDistance(coord_real, coord_prev),
			Distances.checkDistance(coord_real, coord_inaccurated),
			Distances.checkDistance(coord_prev, coord_inaccurated),
			smartThing.getSpeed());

		List<FogDevice> newServerCloudlets = new ArrayList<>();

		MovementDirection localServerCloudlet;
		boolean cone;
		for (FogDevice sc : oldServerCloudlets) {
			// return the relative position between Server Cloudlet and smart
			// thing -> set this value
			localServerCloudlet = DiscoverLocalization.discoverDirection(
				coord_inaccurated, sc.getCoord());
			cone = insideCone(localServerCloudlet, predictionErrorDirection);
			if (cone && sc != smartThing.getSourceServerCloudlet()) {
				newServerCloudlets.add(sc);
			}
		}
		return new ServerPrediction(newServerCloudlets, prediction);
	}

	public static Optional<FogDevice> nextServerCloudlet(List<FogDevice> serverCloudlets,
		MobileDevice smartThing) {
		return Distances.findClosestServerCloudlet(
			serverClouletsAvailableList(serverCloudlets, smartThing), smartThing);
	}

	static Optional<FogDevice> nextServerCloudlet(List<FogDevice> serverCloudlets,
		MobileDevice smartThing, MigrationDecisionContext context) {
		ServerPrediction prediction = context.predictServers(serverCloudlets,
			smartThing);
		return Distances.findClosestServerCloudlet(
			prediction.getAvailableServers(), prediction.adjustedCoordinate());
	}

	public static boolean isEdgeAp(ApDevice apDevice, MobileDevice smartThing) {
		return apDevice.getServerCloudlet() != smartThing.getSourceServerCloudlet();
	}

	public static Optional<FogDevice> lowestLatencyCostServerCloudlet(
		List<FogDevice> oldServerCloudlets,
		List<ApDevice> oldApDevices, MobileDevice smartThing) {
		MigrationDecisionContext context = new MigrationDecisionContext(
			CloudSim.clock(), LegacySimulationAdapters.migrationRandom(),
			MobileController.getSmartThings(),
			VmDestinationPolicy.getDestination());
		Optional<FogDevice> result = lowestLatencyCostServerCloudlet(
			oldServerCloudlets, oldApDevices, smartThing, context);
		for (MigrationPrediction prediction : context.getPredictions()) {
			applyPrediction(smartThing, prediction);
		}
		return result;
	}

	static Optional<FogDevice> lowestLatencyCostServerCloudlet(
		List<FogDevice> oldServerCloudlets, List<ApDevice> oldApDevices,
		MobileDevice smartThing, MigrationDecisionContext context) {
		List<FogDevice> newServerCloudlets = new ArrayList<>();
		List<FogDevice> numServerCloudlets = new ArrayList<>();

		for (FogDevice sc : oldServerCloudlets) {
			newServerCloudlets.add(sc);
		}

		for (int i = 0; i < 9; i++) {
			Optional<FogDevice> destinationServerCloudlet =
				nextServerCloudlet(newServerCloudlets, smartThing, context);
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

		FogDevice selectedServerCloudlet = null;
		double minimumCost = Double.POSITIVE_INFINITY;
		for (FogDevice sc : numServerCloudlets) {
			double sumCost = sumCostFunction(sc, nextAp.get(), smartThing);
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

	static void applyPrediction(MobileDevice mobileDevice,
		MigrationPrediction prediction) {
		mobileDevice.setFutureCoord(prediction.getAdjustedX(),
			prediction.getAdjustedY());
		recordPrediction(prediction);
	}

	static void recordPrediction(MigrationPrediction prediction) {
		saveDistance(prediction, "distance_between_user_cloudlet.txt");
	}

	/** Pure candidate result paired with the observation used to derive it. */
	static final class ServerPrediction {
		private final List<FogDevice> availableServers;
		private final MigrationPrediction prediction;

		private ServerPrediction(List<FogDevice> availableServers,
			MigrationPrediction prediction) {
			this.availableServers = new ArrayList<FogDevice>(availableServers);
			this.prediction = prediction;
		}

		List<FogDevice> getAvailableServers() {
			return new ArrayList<FogDevice>(availableServers);
		}

		MigrationPrediction getPrediction() {
			return prediction;
		}

		Coordinate adjustedCoordinate() {
			Coordinate coordinate = new Coordinate();
			coordinate.setCoordX(prediction.getAdjustedX());
			coordinate.setCoordY(prediction.getAdjustedY());
			return coordinate;
		}
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
