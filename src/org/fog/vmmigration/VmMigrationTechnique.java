package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public interface VmMigrationTechnique {

	public void verifyPoints(MobileDevice smartThing, int relativePosition);

	public double migrationTimeFunction(double vmSize, double bandwidth);

	/** Returns the bytes placed on the transport network by this technique. */
	public double getTransferSizeBytes(double vmSizeMebibytes);

	/** Returns propagation/setup delay in milliseconds, excluding preparation. */
	public double getFixedDelayMillis(MobileDevice smartThing);

	public boolean migPointPolicyFunction(int policy, MobileDevice smartThing);

	public boolean migrationPointFunction(double distance, double migTime, int speed);

	public boolean migrationPointFunction(double distance);

	public boolean migrationZoneFunction(int smartThingDirection, int zoneDirection);

}
