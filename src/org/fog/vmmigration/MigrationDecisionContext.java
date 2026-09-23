package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.vmmobile.policy.MovementDirection;

/**
 * Read-only run inputs plus request-local prediction observations used by one
 * migration policy evaluation.
 */
public final class MigrationDecisionContext {
	private final double simulationTimeMillis;
	private final Random predictionRandom;
	private final List<MobileDevice> activeMobileDevices;
	private final VmDestinationPolicy.Destination destinationPolicy;
	private final List<MigrationPrediction> predictions =
		new ArrayList<MigrationPrediction>();

	public MigrationDecisionContext(double simulationTimeMillis,
		Random predictionRandom, Collection<MobileDevice> activeMobileDevices,
		VmDestinationPolicy.Destination destinationPolicy) {
		if (!Double.isFinite(simulationTimeMillis) || simulationTimeMillis < 0.0
			|| predictionRandom == null || activeMobileDevices == null
			|| destinationPolicy == null) {
			throw new IllegalArgumentException(
				"Migration decision context inputs cannot be null or invalid");
		}
		this.simulationTimeMillis = simulationTimeMillis;
		this.predictionRandom = predictionRandom;
		this.activeMobileDevices = Collections.unmodifiableList(
			new ArrayList<MobileDevice>(activeMobileDevices));
		this.destinationPolicy = destinationPolicy;
	}

	public double getSimulationTimeMillis() {
		return simulationTimeMillis;
	}

	public List<MobileDevice> getActiveMobileDevices() {
		return activeMobileDevices;
	}

	public VmDestinationPolicy.Destination getDestinationPolicy() {
		return destinationPolicy;
	}

	Migration.ServerPrediction predictServers(List<FogDevice> serverCloudlets,
		MobileDevice mobileDevice) {
		MovementDirection predictionDirection = MovementDirection.fromLegacy(
			predictionRandom.nextInt(8) + 1);
		Migration.ServerPrediction prediction = Migration.predictAvailableServers(
			serverCloudlets, mobileDevice, simulationTimeMillis,
			predictionDirection);
		predictions.add(prediction.getPrediction());
		return prediction;
	}

	public List<MigrationPrediction> getPredictions() {
		return Collections.unmodifiableList(
			new ArrayList<MigrationPrediction>(predictions));
	}
}
