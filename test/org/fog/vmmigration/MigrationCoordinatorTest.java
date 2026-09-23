package org.fog.vmmigration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Collections;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.MigrationTransferSpec;
import org.fog.vmmobile.MobileSession;
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

	@Test
	public void staleTransferSpecificationCannotStartANewerAttempt() {
		FogDevice source = new FogDevice("source", 0, 0, 0);
		FogDevice destination = new FogDevice("destination", 0, 0, 1);
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 2, 0, 0);
		mobileDevice.getSession().decideMigration();
		mobileDevice.getSession().beginMigrationPreparation();
		mobileDevice.setMigStatus(true);
		MigrationTransferSpec stale = new MigrationTransferSpec(source,
			destination, mobileDevice, 1.0, 0.0, 0.0, source.getId(), 1);
		mobileDevice.getSession().abortMigration();
		mobileDevice.getSession().unlockMigration();
		mobileDevice.getSession().decideMigration();
		mobileDevice.getSession().beginMigrationPreparation();

		assertFalse(new MigrationCoordinator().startTransfer(stale,
			Collections.singletonList(mobileDevice)));
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.PREPARING);
	}

	@Test
	public void duplicateFixedDelayCompletionCannotAdvanceMigrationTwice() {
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 2, 0, 0);
		mobileDevice.getSession().decideMigration();
		long generation = mobileDevice.getSession().beginMigrationPreparation();
		mobileDevice.getSession().beginMigrationTransfer(generation);
		mobileDevice.getSession().awaitMigrationFixedDelay(generation);
		MigrationEvent completion = new MigrationEvent(mobileDevice, generation,
			MigrationEvent.Stage.FIXED_DELAY_COMPLETE);
		MigrationCoordinator coordinator = new MigrationCoordinator();

		assertSame(mobileDevice,
			coordinator.completeTransfer(completion).getMobileDevice());
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.READY_TO_INSTALL);
		assertNull(coordinator.completeTransfer(completion));
	}

	@Test
	public void installationMovesVmMembershipAndAggregateStateTogether() {
		FogDevice source = new FogDevice("source", 0, 0, 0);
		FogDevice destination = new FogDevice("destination", 0, 0, 1);
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 2, 0, 0);
		mobileDevice.setVmLocalServerCloudlet(source);
		mobileDevice.setDestinationServerCloudlet(destination);
		source.registerHostedMobileVm(mobileDevice);
		mobileDevice.getSession().decideMigration();
		mobileDevice.getSession().beginMigrationPreparation();

		assertTrue(new MigrationCoordinator().installVm(mobileDevice,
			Collections.singletonList(mobileDevice)));
		assertSame(destination, mobileDevice.getVmLocalServerCloudlet());
		assertTrue(destination.getSmartThingsWithVm().contains(mobileDevice));
		assertFalse(source.getSmartThingsWithVm().contains(mobileDevice));
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.INSTALLED);
	}
}
