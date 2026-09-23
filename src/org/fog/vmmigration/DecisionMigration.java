package org.fog.vmmigration;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.MobileDevice;
import org.fog.placement.MobileController;
import org.fog.vmmobile.adapter.LegacySimulationAdapters;

public interface DecisionMigration {

	/** Returns a decision without mutating the mobile device or destination. */
	MigrationDecision evaluate(MobileDevice smartThing,
		MigrationDecisionContext context);

	/** Compatibility evaluation using the active legacy run adapters. */
	default MigrationDecision evaluate(MobileDevice smartThing) {
		return evaluate(smartThing, new MigrationDecisionContext(CloudSim.clock(),
			LegacySimulationAdapters.migrationRandom(),
			MobileController.getSmartThings(),
			VmDestinationPolicy.getDestination()));
	}

	/** Compatibility predicate; unlike the historical implementation it is pure. */
	default boolean shouldMigrate(MobileDevice smartThing) {
		return evaluate(smartThing).shouldMigrate();
	}
}
