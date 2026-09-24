package org.fog.vmmigration;

import org.cloudbus.cloudsim.NetworkTopology;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;

/**
 * Shared, stateless connection preparation for VM migration strategies.
 */
abstract class AbstractMigrationPreparation implements BeforeMigration {
	private static final double SUCCESSFUL_ATTEMPT_DELAY = 10.0;
	private static final double FAILED_ATTEMPT_DELAY = 30.0;

	private static final ConnectionAttemptPolicy ALWAYS_SUCCEEDS =
		new ConnectionAttemptPolicy() {
			@Override
			public boolean tryOpenConnection(FogDevice sourceServerCloudlet,
				FogDevice destinationServerCloudlet, int attemptNumber) {
				return true;
			}
		};

	private final ConnectionAttemptPolicy connectionAttemptPolicy;
	private final int maximumConnectionAttempts;

	protected AbstractMigrationPreparation(int maximumConnectionAttempts) {
		this(maximumConnectionAttempts, ALWAYS_SUCCEEDS);
	}

	protected AbstractMigrationPreparation(int maximumConnectionAttempts,
		ConnectionAttemptPolicy connectionAttemptPolicy) {
		if (maximumConnectionAttempts <= 0) {
			throw new IllegalArgumentException("maximumConnectionAttempts must be positive");
		}
		if (connectionAttemptPolicy == null) {
			throw new IllegalArgumentException("connectionAttemptPolicy cannot be null");
		}
		this.maximumConnectionAttempts = maximumConnectionAttempts;
		this.connectionAttemptPolicy = connectionAttemptPolicy;
	}

	@Override
	public final double prepareData(MobileDevice smartThing) {
		FogDevice sourceServerCloudlet = smartThing.getVmLocalServerCloudlet();
		FogDevice destinationServerCloudlet = smartThing.getDestinationServerCloudlet();
		ConnectionPreparationResult connection = prepareConnection(
			sourceServerCloudlet, destinationServerCloudlet);
		if (!connection.isConnected()) {
			return -1.0;
		}

		double processingDelay = sourceServerCloudlet.getCharacteristics()
			.getCpuTime(getPreparationWorkload(smartThing), 0.0);
		return processingDelay + connection.getDelay()
			+ getTopologyLatency(sourceServerCloudlet, destinationServerCloudlet);
	}

	/** @deprecated Use {@link #prepareData(MobileDevice)}. */
	@Override
	@Deprecated
	public final double dataprepare(MobileDevice smartThing) {
		return prepareData(smartThing);
	}

	final ConnectionPreparationResult prepareConnection(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {
		double connectionDelay = 0.0;
		for (int attemptNumber = 1; attemptNumber <= maximumConnectionAttempts;
			attemptNumber++) {
			if (connectionAttemptPolicy.tryOpenConnection(sourceServerCloudlet,
				destinationServerCloudlet, attemptNumber)) {
				return new ConnectionPreparationResult(true,
					connectionDelay + SUCCESSFUL_ATTEMPT_DELAY);
			}
			connectionDelay += FAILED_ATTEMPT_DELAY;
		}
		return new ConnectionPreparationResult(false, connectionDelay);
	}

	protected double getTopologyLatency(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet) {
		return NetworkTopology.getDelay(sourceServerCloudlet.getId(),
			destinationServerCloudlet.getId());
	}

	protected abstract double getPreparationWorkload(MobileDevice smartThing);
}
