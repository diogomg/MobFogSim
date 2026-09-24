package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.LocationPolicy;

/** Validated, rollback-safe topology assembly for the bundled example. */
public final class ExampleTopologyPlan {
	private final SimulationContext context;
	private final ExampleSimulationConfiguration configuration;

	public ExampleTopologyPlan(SimulationContext context,
		ExampleSimulationConfiguration configuration) {
		if (context == null || configuration == null) {
			throw new IllegalArgumentException(
				"Simulation context and example configuration cannot be null");
		}
		this.context = context;
		this.configuration = configuration;
	}

	/** Builds the complete topology or restores the pre-build registration set. */
	public void build() {
		SimulationTopology topology = context.getTopology();
		List<ApDevice> accessPoints = topology.accessPointRegistry();
		List<FogDevice> serverCloudlets = topology.serverCloudletRegistry();
		List<MobileDevice> mobileDevices = topology.mobileDeviceRegistry();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildAccessPoints(accessPoints);
			buildServerCloudlets(serverCloudlets);
			context.getServices().getTopology().createTransportNetwork(
				serverCloudlets, configuration.getCloudletPropagationDelay(),
				configuration.getRandom());
			buildMobileDevices(mobileDevices);
			context.getServices().getTopology().loadMobility(
				configuration.getMobilityDirectory(),
				configuration.getMobilityOrderManifest(), mobileDevices);
			MobileUserRegistration.preparePendingUsers(mobileDevices);
			context.getServices().getTopology().connectAccessPoints(
				serverCloudlets, accessPoints, configuration.getRandom());
			context.recordInitialTopologySize();
		}
		catch (Exception error) {
			accessPoints.clear();
			serverCloudlets.clear();
			mobileDevices.clear();
			NetworkTopology.reset();
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw new SimulationBuildException(
				"Could not assemble the simulation topology", error);
		}
	}

	private void buildAccessPoints(List<ApDevice> accessPoints) {
		if (configuration.getAccessPointLocation() == LocationPolicy.FIXED) {
			AppExample.addApDevicesFixed(accessPoints,
				configuration.getCoordinateSpace());
			return;
		}
		for (int index = 0; index < MaxAndMin.MAX_AP_DEVICE; index++) {
			AppExample.addApDevicesRandom(accessPoints,
				configuration.getCoordinateSpace(), index);
		}
	}

	private void buildServerCloudlets(List<FogDevice> serverCloudlets) {
		if (configuration.getServerCloudletLocation() == LocationPolicy.FIXED) {
			AppExample.addServerCloudlet(serverCloudlets,
				configuration.getCoordinateSpace());
			return;
		}
		for (int index = 0; index < MaxAndMin.MAX_SERVER_CLOUDLET; index++) {
			AppExample.addServerCloudlet(serverCloudlets,
				configuration.getCoordinateSpace(), index);
		}
	}

	private void buildMobileDevices(List<MobileDevice> mobileDevices) {
		int[] userSliceAssignments = NetworkSlicing.getUserSliceAssignments(
			configuration.getMaximumUsers());
		List<MobileDevice> planned = new ArrayList<MobileDevice>();
		for (int index = 0; index < configuration.getMaximumUsers(); index++) {
			AppExample.addSmartThing(planned,
				configuration.getCoordinateSpace(), index);
			planned.get(index).setNetworkSliceId(userSliceAssignments[index]);
		}
		mobileDevices.addAll(planned);
	}
}
