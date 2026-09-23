package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmobile.MobileSession;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.policy.MovementDirection;
import org.fog.vmmobile.policy.ServiceType;
import org.junit.Before;
import org.junit.Test;

public class MigrationDecisionTest {
	private FogDevice source;
	private FogDevice destination;
	private ApDevice sourceAccessPoint;
	private ApDevice destinationAccessPoint;
	private MobileDevice mobileDevice;
	private DecisionMigration policy;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		NetworkTopology.reset();
		NetworkSlicing.configure(null);
		NetworkSlicing.setScope(NetworkSlicing.END_TO_END_NETWORK);

		source = cloudlet("source", 0, 0);
		destination = cloudlet("destination", 1000, 0);
		NetworkTopology.addLink(source.getId(), destination.getId(),
			8.0 * 1024.0 * 1024.0, 1.0);
		sourceAccessPoint = new ApDevice("source-ap", 0, 0, 0);
		destinationAccessPoint = new ApDevice("destination-ap", 1000, 0, 1);
		source.attachAccessPoint(sourceAccessPoint);
		destination.attachAccessPoint(destinationAccessPoint);

		mobileDevice = new MobileDevice("mobile", 960, 0, 2,
			MovementDirection.EAST, 10);
		mobileDevice.setSourceAp(sourceAccessPoint);
		mobileDevice.setSourceServerCloudlet(source);
		mobileDevice.setVmLocalServerCloudlet(source);
		mobileDevice.setVmMobileDevice(new Vm(1, 1, 1000, 1, 128,
			1000, 1, "Xen", new CloudletSchedulerTimeShared()));
		mobileDevice.setMigrationTechnique(new CompleteVM(
			MigrationPointPolicy.FIXED));
		policy = new LowestDistBwSmartThingAP(
			Arrays.asList(source, destination),
			Arrays.asList(sourceAccessPoint, destinationAccessPoint),
			MigrationPointPolicy.FIXED,
			MigrationTechniquePolicy.COMPLETE_VM);
	}

	@Test
	public void policyEvaluationIsPureUntilCoordinatorCommit() {
		MigrationDecision decision = policy.evaluate(mobileDevice, context());

		assertTrue(decision.shouldMigrate());
		assertSame(destination, decision.getDestination());
		assertNull(mobileDevice.getDestinationServerCloudlet());
		assertFalse(mobileDevice.isMigPoint());
		assertFalse(mobileDevice.isMigZone());
		assertEquals(0.0, mobileDevice.getMigTime(), 0.0);
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.IDLE);

		assertTrue(new MigrationCoordinator().commitDecision(decision,
			Collections.singletonList(mobileDevice))
			== MigrationCoordinator.DecisionCommit.MIGRATION);
		assertSame(destination, mobileDevice.getDestinationServerCloudlet());
		assertTrue(mobileDevice.isMigPoint());
		assertTrue(mobileDevice.isMigZone());
		assertTrue(mobileDevice.getMigTime() > 0.0);
		assertTrue(mobileDevice.isLockedToMigration());
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.DECIDED);
	}

	@Test
	public void staleDecisionIsRejectedWithoutPartialMutation() {
		MigrationDecision decision = policy.evaluate(mobileDevice, context());
		mobileDevice.setCoord(959, 0);

		assertTrue(new MigrationCoordinator().commitDecision(decision,
			Collections.singletonList(mobileDevice))
			== MigrationCoordinator.DecisionCommit.REJECTED);
		assertNull(mobileDevice.getDestinationServerCloudlet());
		assertFalse(mobileDevice.isMigPoint());
		assertFalse(mobileDevice.isMigZone());
		assertEquals(0.0, mobileDevice.getMigTime(), 0.0);
		assertFalse(mobileDevice.isLockedToMigration());
		assertTrue(mobileDevice.getSession().getMigration()
			== MobileSession.MigrationState.IDLE);
	}

	@Test
	public void serviceAndHostSelectionArePureQueries() {
		assertTrue(ServiceAgreement.evaluate(destination, mobileDevice).isAccepted());
		assertSame(destination, MobileEdgeHostSelector.chooseDestination(
			mobileDevice, destination,
			Collections.singletonList(mobileDevice),
			VmDestinationPolicy.Destination.EDGE_SERVERS).get());
		assertNull(mobileDevice.getDestinationServerCloudlet());
	}

	private MigrationDecisionContext context() {
		return new MigrationDecisionContext(0.0, new Random(1L),
			Collections.singletonList(mobileDevice),
			VmDestinationPolicy.Destination.EDGE_SERVERS);
	}

	private static FogDevice cloudlet(String name, int x, int y) {
		FogDevice cloudlet = new FogDevice(name, x, y, 0);
		cloudlet.setUplinkBandwidth(8.0 * 1024.0 * 1024.0);
		cloudlet.setDownlinkBandwidth(8.0 * 1024.0 * 1024.0);
		Service service = new Service();
		service.setServiceType(ServiceType.PRIVATE);
		cloudlet.setService(service);
		cloudlet.setAvailable(true);
		return cloudlet;
	}
}
