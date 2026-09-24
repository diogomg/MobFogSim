package org.fog.entities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.placement.HandoffConnectionRequest;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.MigrationEvent;
import org.fog.vmmobile.MobileUserRegistration;
import org.fog.vmmobile.constants.MobileEvents;
import org.junit.Before;
import org.junit.Test;

public class HandoffConnectionEventTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		MyStatistics.setInstance(new MyStatistics());
	}

	@Test
	public void peerVmHostDoesNotReplaceTheAccessServer() {
		FogDevice sourceServer = server("source-server", 1);
		FogDevice destinationServer = server("destination-server", 2);
		MobileDevice peerVmHost = mobile("peer-vm-host", 3);
		MobileDevice user = mobile("user", 4);
		ApDevice sourceAp = accessPoint("source-ap", sourceServer, 5);
		ApDevice destinationAp = accessPoint("destination-ap", destinationServer, 6);
		associate(user, sourceAp, sourceServer);
		user.setVmLocalServerCloudlet(peerVmHost);
		user.setTimeFinishDeliveryVm(-1.0);
		user.setVmMobileDevice(new Vm(7, 1, 1000, 1, 512, 1000, 1000,
			"Xen", new CloudletSchedulerTimeShared()));

		HandoffConnectionRequest request = request(user, sourceServer,
			destinationAp);
		sourceServer.disconnectServerCloudletSmartThing(user);
		moveWirelessAssociation(user, sourceAp, destinationAp);
		sendConnection(user, destinationServer, request);

		assertSame(destinationServer, user.getSourceServerCloudlet());
		assertSame(destinationAp.getServerCloudlet(),
			user.getSourceServerCloudlet());
		assertFalse(peerVmHost.getSmartThings().contains(user));
	}

	@Test
	public void supersededConnectionEventCannotReplaceANewerAssociation() {
		FogDevice sourceServer = server("source-server", 1);
		FogDevice staleDestination = server("stale-destination", 2);
		FogDevice currentServer = server("current-server", 3);
		MobileDevice user = mobile("user", 4);
		ApDevice sourceAp = accessPoint("source-ap", sourceServer, 5);
		ApDevice staleDestinationAp = accessPoint("stale-ap", staleDestination, 6);
		ApDevice currentAp = accessPoint("current-ap", currentServer, 7);
		associate(user, sourceAp, sourceServer);

		HandoffConnectionRequest staleRequest = request(user, sourceServer,
			staleDestinationAp);
		sourceServer.disconnectServerCloudletSmartThing(user);
		moveWirelessAssociation(user, sourceAp, staleDestinationAp);
		MobileUserRegistration.disconnectNetwork(user);
		associate(user, currentAp, currentServer);
		sendConnection(user, staleDestination, staleRequest);

		assertSame(currentServer, user.getSourceServerCloudlet());
		assertSame(currentAp, user.getSourceAp());
		assertFalse(staleDestination.getSmartThings().contains(user));
	}

	@Test
	public void peerHostProcessesInheritedMigrationControlEvents() {
		MobileDevice peerVmHost = mobile("peer-vm-host", 1);
		MobileDevice user = mobile("user", 2);
		user.setLockedToMigration(true);

		CloudSim.send(user.getId(), peerVmHost.getId(), 0.0,
			MobileEvents.UNLOCKED_MIGRATION, user);
		runPendingEvents();

		assertFalse(user.isLockedToMigration());
	}

	@Test
	public void staleMigrationUnlockCannotUnlockANewerAttempt() {
		MobileDevice peerVmHost = mobile("peer-vm-host", 1);
		MobileDevice user = mobile("user", 2);
		long first = user.getSession().decideMigration();
		MigrationEvent stale = new MigrationEvent(user, first);
		user.getSession().abortMigration();
		user.getSession().unlockMigration();
		user.getSession().decideMigration();
		user.setLockedToMigration(true);

		CloudSim.send(user.getId(), peerVmHost.getId(), 0.0,
			MobileEvents.UNLOCKED_MIGRATION, stale);
		runPendingEvents();

		assertTrue(user.isLockedToMigration());
	}

	private static HandoffConnectionRequest request(MobileDevice user,
		FogDevice sourceServer, ApDevice destinationAp) {
		long generation = user.advanceNetworkAssociationGeneration();
		return new HandoffConnectionRequest(user, sourceServer, destinationAp,
			generation);
	}

	private static void sendConnection(MobileDevice user, FogDevice destination,
		HandoffConnectionRequest request) {
		CloudSim.send(user.getId(), destination.getId(), 0.0,
			MobileEvents.CONNECT_ST_TO_SC, request);
		runPendingEvents();
	}

	private static void runPendingEvents() {
		CloudSim.terminateSimulation(1.0);
		CloudSim.startSimulation();
	}

	private static void associate(MobileDevice user, ApDevice accessPoint,
		FogDevice server) {
		accessPoint.associateMobileDevice(user);
		user.setSourceAp(accessPoint);
		server.connectServerCloudletSmartThing(user);
	}

	private static void moveWirelessAssociation(MobileDevice user,
		ApDevice source, ApDevice destination) {
		source.dissociateMobileDevice(user);
		destination.associateMobileDevice(user);
		user.setSourceAp(destination);
	}

	private static FogDevice server(String name, int logicalId) {
		FogDevice server = new FogDevice(name, 0, 0, logicalId);
		server.setUplinkLatency(1.0);
		return server;
	}

	private static MobileDevice mobile(String name, int logicalId) {
		MobileDevice mobileDevice = new MobileDevice(name, 0, 0, logicalId, 0, 0);
		mobileDevice.setUplinkLatency(1.0);
		return mobileDevice;
	}

	private static ApDevice accessPoint(String name, FogDevice server,
		int logicalId) {
		ApDevice accessPoint = new ApDevice(name, 0, 0, logicalId);
		accessPoint.setServerCloudlet(server);
		return accessPoint;
	}
}
