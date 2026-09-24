package org.fog.vmmigration;

import org.fog.entities.MobileDevice;

public interface BeforeMigration {
	default double prepareData(MobileDevice smartThing) {
		return dataprepare(smartThing);
	}

	/** @deprecated Implement or call {@link #prepareData(MobileDevice)}. */
	@Deprecated
	public double dataprepare(MobileDevice smartThing);
}
