package org.fog.vmmobile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;

/**
 * Loads mobility input and builds the topology used by a simulation.
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

	/** Builds and registers the simulation's centralized logical transport mesh. */
	public void createTransportNetwork(List<FogDevice> serverCloudlets,
		double baseLatency, Random random) {
		TopologyPlan plan = TopologyPlan.transport(serverCloudlets, baseLatency,
			random);
		for (TopologyPlan.Link link : plan.getLinks()) {
			NetworkTopology.addLink(link.getSourceId(), link.getDestinationId(),
				link.getBandwidth(), link.getLatency());
		}
	}

	/** Associates each access point with its closest server cloudlet. */
	public void connectAccessPoints(List<FogDevice> serverCloudlets,
		List<ApDevice> accessPoints, Random random) {
		TopologyPlan plan = TopologyPlan.accessPoints(serverCloudlets,
			accessPoints, random);
		for (TopologyPlan.AccessPointAttachment attachment
			: plan.getAccessPointAttachments()) {
			attachment.getServerCloudlet().attachAccessPoint(
				attachment.getAccessPoint());
			TopologyPlan.Link link = attachment.getLink();
			NetworkTopology.addLink(link.getSourceId(), link.getDestinationId(),
				link.getBandwidth(), link.getLatency());
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

}
