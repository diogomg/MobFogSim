package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.utils.NetworkSlicing;
import org.fog.vmmigration.CompleteVM;
import org.fog.vmmigration.Service;
import org.fog.vmmigration.VmDestinationPolicy;
import org.fog.vmmobile.policy.MembershipAction;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.policy.MovementDirection;
import org.fog.vmmobile.policy.ServiceType;
import org.junit.Before;
import org.junit.Test;

public class TypedPolicyTest {
	@Before
	public void initialiseCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void commandLinePoliciesAreTypedAfterParsing() {
		SimulationConfig configuration = SimulationConfig.parse(new String[] {
			"1", "7", "1", "2", "3", "11", "1", "61", "60", "5",
			"1", "50,50", "70,30", "0", "0", "1", "none"
		});

		assertEquals(MigrationPointPolicy.SPEED,
			configuration.getMigrationPoint());
		assertEquals(MigrationStrategyPolicy.LOWEST_DISTANCE_TO_ACCESS_POINT,
			configuration.getMigrationStrategy());
		assertEquals(MigrationTechniquePolicy.CONTAINER_VM,
			configuration.getMigrationTechnique());
		assertEquals(NetworkSlicing.Scope.WIRELESS,
			configuration.getSlicingConfiguration().getScope());
		assertEquals(NetworkSlicing.Mode.FIXED,
			configuration.getSlicingConfiguration().getMode());
		assertEquals(VmDestinationPolicy.Destination.END_DEVICES,
			configuration.getVmDestination());
	}

	@Test
	public void typedDomainObjectsRetainTheirValues() {
		MobileDevice mobile = new MobileDevice("mobile", 0, 0, 1,
			MovementDirection.NORTHWEST, 2);
		CompleteVM technique = new CompleteVM(MigrationPointPolicy.SPEED);
		Service service = new Service();
		service.setServiceType(ServiceType.HYBRID);

		assertEquals(MovementDirection.NORTHWEST,
			mobile.getMovementDirection());
		assertEquals(MigrationPointPolicy.SPEED,
			technique.getMigrationPointPolicy());
		assertEquals(ServiceType.HYBRID, service.getServiceType());
	}

	@Test
	public void typedMembershipMutationAvoidsIntegerActionMixups() {
		FogDevice cloudlet = new FogDevice("cloudlet", 0, 0, 1);
		MobileDevice mobile = new MobileDevice("mobile", 0, 0, 2,
			MovementDirection.NONE, 0);

		cloudlet.setSmartThings(mobile, MembershipAction.ADD);
		assertEquals(1, cloudlet.getSmartThings().size());
		cloudlet.setSmartThings(mobile, MembershipAction.REMOVE);
		assertEquals(0, cloudlet.getSmartThings().size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void legacyDirectionAdapterRejectsAnUnrelatedInteger() {
		new MobileDevice("mobile", 0, 0, 1, 99, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void legacyMembershipAdapterRejectsAnUnrelatedInteger() {
		FogDevice cloudlet = new FogDevice("cloudlet", 0, 0, 1);
		cloudlet.setSmartThings(new MobileDevice(), 99);
	}
}
