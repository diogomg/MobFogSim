package org.fog.vmmigration;

import org.fog.entities.FogDevice;

public interface AfterMigration {

	public void closeConnection(FogDevice sourceServerCloudlet, FogDevice destinationServerCloudlet);

}
