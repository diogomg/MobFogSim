package org.fog.vmmobile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.localization.Distances;

/**
 * Loads mobility input and builds the physical topology used by a simulation.
 *
 * <p>The service owns topology I/O and graph construction so the application
 * entry point only decides which topology to create.</p>
 */
public final class TopologyService {

	/** Loads, assigns, and records the initial position of every mobile user. */
	public void loadMobility(Path mobilityDirectory, Path orderManifest,
		List<MobileDevice> mobileDevices) {
		if (mobileDevices == null) {
			throw new IllegalArgumentException("Mobile device list cannot be null");
		}
		List<MobilityDataLoader.MobilityTrace> traces = MobilityDataLoader.load(
			mobilityDirectory, orderManifest, mobileDevices.size());
		for (int index = 0; index < mobileDevices.size(); index++) {
			MobileDevice mobileDevice = mobileDevices.get(index);
			mobileDevice.setMobilityPath(traces.get(index).getSamples());
			new Coordinate().setInitialCoordinate(mobileDevice);
			writeInitialMobility(mobileDevice);
		}
	}

	/** Creates the complete transport graph and registers its physical links. */
	public void createTransportNetwork(List<FogDevice> serverCloudlets,
		double baseLatency, Random random) {
		validateServerCloudlets(serverCloudlets);
		if (!Double.isFinite(baseLatency) || baseLatency < 0.0) {
			throw new IllegalArgumentException(
				"Cloudlet base latency must be finite and non-negative");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		createServerCloudletAdjacency(serverCloudlets);

		// CloudSim models these links as undirected. Register each pair once,
		// retaining the lower-triangle order for seeded reproducibility.
		for (int sourceIndex = 0; sourceIndex < serverCloudlets.size(); sourceIndex++) {
			FogDevice source = serverCloudlets.get(sourceIndex);
			for (int destinationIndex = 0; destinationIndex < sourceIndex;
				destinationIndex++) {
				FogDevice destination = serverCloudlets.get(destinationIndex);
				int rowDistance = Math.abs(destinationIndex / 12 - sourceIndex / 12);
				int columnDistance = Math.abs(destinationIndex % 12 - sourceIndex % 12);
				double bandwidth = Math.min(source.getUplinkBandwidth(),
					destination.getDownlinkBandwidth());
				double latency = Math.max(rowDistance, columnDistance)
					* baseLatency + random.nextDouble();
				NetworkTopology.addLink(source.getId(), destination.getId(), bandwidth,
					latency);
			}
		}
	}

	/** Associates each access point with its closest server cloudlet. */
	public void connectAccessPoints(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, Random random) {
		validateServerCloudlets(serverCloudlets);
		if (accessPoints == null) {
			throw new IllegalArgumentException("Access point list cannot be null");
		}
		if (random == null) {
			throw new IllegalArgumentException("Random generator cannot be null");
		}
		for (ApDevice accessPoint : accessPoints) {
			if (accessPoint == null) {
				throw new IllegalArgumentException(
					"Access point list cannot contain null entries");
			}
			if (accessPoint.getMaxSmartThing() <= 0) {
				throw new IllegalArgumentException(
					"Access-point capacity must be positive: "
						+ accessPoint.getName());
			}
		}

		for (ApDevice accessPoint : accessPoints) {
			FogDevice closestServerCloudlet = Distances
				.findClosestServerCloudletToAp(serverCloudlets, accessPoint)
				.orElseThrow(() -> new IllegalStateException(
					"Cannot connect access point without a server cloudlet"));
			closestServerCloudlet.attachAccessPoint(accessPoint);
			NetworkTopology.addLink(closestServerCloudlet.getId(),
				accessPoint.getId(), accessPoint.getDownlinkBandwidth(),
				random.nextDouble());
		}
	}

	/** Assigns an independent, complete directed adjacency map to each cloudlet. */
	public void createServerCloudletAdjacency(List<FogDevice> serverCloudlets) {
		validateServerCloudlets(serverCloudlets);
		for (FogDevice source : serverCloudlets) {
			HashMap<FogDevice, Double> adjacency = new HashMap<FogDevice, Double>();
			for (FogDevice destination : serverCloudlets) {
				if (source != destination) {
					adjacency.put(destination, Math.min(source.getUplinkBandwidth(),
						destination.getDownlinkBandwidth()));
				}
			}
			source.setNetServerCloudlets(adjacency);
		}
	}

	private void writeInitialMobility(MobileDevice mobileDevice) {
		RunOutputManager output = RunOutputManager.getInstance();
		try (PrintWriter details = output.newDetailedPrintWriter(
			mobileDevice.getMyId() + "out.txt", true)) {
			details.println(mobileDevice.getMyId() + " Position: "
				+ mobileDevice.getCoord().getCoordX() + ", "
				+ mobileDevice.getCoord().getCoordY() + " Direction: "
				+ mobileDevice.getDirection() + " Speed: " + mobileDevice.getSpeed());
			details.println("Source AP: " + mobileDevice.getSourceAp() + " Dest AP: "
				+ mobileDevice.getDestinationAp() + " Host: "
				+ mobileDevice.getHost().getId());
			details.println("Local server: null  Apps null Map null");
			if (mobileDevice.getDestinationServerCloudlet() == null) {
				details.println("Dest server: null Apps: null Map: null");
			} else {
				details.println("Dest server: "
					+ mobileDevice.getDestinationServerCloudlet().getName() + " Apps: "
					+ mobileDevice.getDestinationServerCloudlet().getActiveApplications()
					+ " Map "
					+ mobileDevice.getDestinationServerCloudlet().getApplicationMap());
			}
		} catch (IOException error) {
			throw new IllegalStateException("Could not record initial mobility for "
				+ mobileDevice.getName(), error);
		}

		try (PrintWriter route = output.newDetailedPrintWriter(
			mobileDevice.getMyId() + "route.txt", true)) {
			route.println(mobileDevice.getMyId() + "\t"
				+ mobileDevice.getCoord().getCoordX() + "\t"
				+ mobileDevice.getCoord().getCoordY() + "\t"
				+ mobileDevice.getDirection() + "\t" + mobileDevice.getSpeed()
				+ "\t" + CloudSim.clock());
		} catch (IOException error) {
			throw new IllegalStateException("Could not record initial route for "
				+ mobileDevice.getName(), error);
		}
	}

	private void validateServerCloudlets(List<FogDevice> serverCloudlets) {
		if (serverCloudlets == null) {
			throw new IllegalArgumentException("Server cloudlet list cannot be null");
		}
		Set<FogDevice> uniqueCloudlets = new HashSet<FogDevice>();
		for (FogDevice serverCloudlet : serverCloudlets) {
			if (serverCloudlet == null) {
				throw new IllegalArgumentException(
					"Server cloudlet list cannot contain null entries");
			}
			if (!uniqueCloudlets.add(serverCloudlet)) {
				throw new IllegalArgumentException(
					"Server cloudlet list cannot contain duplicate devices");
			}
		}
	}
}
