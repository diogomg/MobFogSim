package org.fog.vmmigration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.Before;
import org.junit.Test;

public class MigrationCoordinatorTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MyStatistics.setInstance(new MyStatistics());
	}

	@Test
	public void abortReleasesAllTransientMigrationState() {
		FogDevice currentHost = new FogDevice("current", 0, 0, 0);
		FogDevice destination = new FogDevice("destination", 0, 0, 1);
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 2, 0, 0);
		mobileDevice.setVmLocalServerCloudlet(currentHost);
		mobileDevice.setDestinationServerCloudlet(destination);
		mobileDevice.setMigStatus(true);
		mobileDevice.setMigStatusLive(true);
		mobileDevice.setPostCopyStatus(true);
		mobileDevice.setLockedToMigration(true);

		new MigrationCoordinator().abort(mobileDevice);

		assertFalse(mobileDevice.isMigStatus());
		assertFalse(mobileDevice.isMigStatusLive());
		assertFalse(mobileDevice.isPostCopyStatus());
		assertFalse(mobileDevice.isLockedToMigration());
		assertTrue(mobileDevice.isAbortMigration());
		assertTrue(currentHost == mobileDevice.getDestinationServerCloudlet());
	}

	@Test(expected = IllegalArgumentException.class)
	public void completionRejectsUnknownPayloadTypes() {
		new MigrationCoordinator().completeTransfer("not a migration");
	}
}
