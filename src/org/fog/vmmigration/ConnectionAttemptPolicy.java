package org.fog.vmmigration;

import org.fog.entities.FogDevice;

/**
 * Decides whether a migration-preparation connection attempt succeeds.
 */
public interface ConnectionAttemptPolicy {

	boolean tryOpenConnection(FogDevice sourceServerCloudlet,
		FogDevice destinationServerCloudlet, int attemptNumber);
}
