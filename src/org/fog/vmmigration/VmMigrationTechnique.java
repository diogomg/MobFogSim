package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public interface VmMigrationTechnique {

	public void verifyPoints(MobileDevice smartThing, int relativePosition);

	public double migrationTimeFunction(double vmSize, double bandwidth);

	public boolean migPointPolicyFunction(int policy, MobileDevice smartThing);

	public boolean migrationPointFunction(double distance, double migTime, int speed);

	public boolean migrationPointFunction(double distance);

	public boolean migrationZoneFunction(int smartThingDirection, int zoneDirection);

}
