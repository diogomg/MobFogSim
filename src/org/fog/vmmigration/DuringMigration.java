package org.fog.vmmigration;

public class DuringMigration {

	public boolean managementBetweenServerCloudlets() {
		return true;
	}

	/** @deprecated Use {@link #managementBetweenServerCloudlets()}. */
	@Deprecated
	public boolean managermentBetweeServerCloudlets() {
		return managementBetweenServerCloudlets();
	}

}
