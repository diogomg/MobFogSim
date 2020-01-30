package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public interface DecisionMigration {

	public boolean shouldMigrate(MobileDevice smartThing);
}
