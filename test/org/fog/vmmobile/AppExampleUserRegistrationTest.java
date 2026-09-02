package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileDeviceLifecycle;
import org.fog.entities.MobileSensor;
import org.fog.utils.distribution.DeterministicDistribution;
import org.junit.Before;
import org.junit.Test;

public class AppExampleUserRegistrationTest {

	@Before
	public void initializeCloudSim() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void keepsFutureUserPendingAndDisablesItsPeripherals() {
		MobileDevice pending = mobile("pending", 4);
		MobileSensor sensor = new MobileSensor("sensor", "EEG4", 4,
			"MyApp_vr_game4", new DeterministicDistribution(10));
		MobileActuator actuator = new MobileActuator("actuator", 4,
			"MyApp_vr_game4", "DISPLAY4");
		pending.setSensors(new HashSet<MobileSensor>(Arrays.asList(sensor)));
		pending.setActuators(new HashSet<MobileActuator>(Arrays.asList(actuator)));
		pending.setStartTravelTime(150);

		MobileUserRegistration.preparePendingUsers(Arrays.asList(pending));

		assertEquals(150.0, pending.getStartTravelTime(), 0.0);
		assertEquals(0, pending.getTravelTimeId());
		assertSame(MobileDeviceLifecycle.SCHEDULED, pending.getLifecycleState());
		assertFalse(pending.isStatus());
		assertFalse(sensor.isEnabled());
		assertFalse(actuator.isEnabled());
	}

	@Test
	public void rejectsInconsistentAccessPointAndServerAssociations() {
		MobileDevice user = mobile("inconsistent", 3);
		ApDevice accessPoint = new ApDevice("inconsistentAp", 0, 0, 0);
		accessPoint.setServerCloudlet(new FogDevice("apServer", 0, 0, 0));
		user.setSourceAp(accessPoint);
		user.setSourceServerCloudlet(new FogDevice("userServer", 0, 0, 1));

		FogBroker broker;
		try {
			broker = new FogBroker("inconsistentBroker");
		} catch (Exception error) {
			throw new AssertionError(error);
		}
		user.setVmMobileDevice(new Vm(3, broker.getId(), 1000, 1, 128, 1000,
			128, "test", new CloudletSchedulerTimeShared()));

		try {
			MobileUserRegistration.submitVm(broker, user);
			fail("Expected inconsistent association to be rejected");
		} catch (IllegalStateException error) {
			assertEquals("Cannot register user inconsistent with inconsistent network "
				+ "associations", error.getMessage());
		}
	}

	@Test
	public void nullVmIsRejectedWithoutMutatingBrokerRegistration() throws Exception {
		MobileDevice user = mobile("withoutVm", 6);
		connect(user, "withoutVmNetwork");
		FogBroker broker = new FogBroker("withoutVmBroker");

		try {
			MobileUserRegistration.submitVm(broker, user);
			fail("Expected null VM to be rejected");
		} catch (IllegalStateException error) {
			assertEquals("Cannot register user withoutVm without a VM", error.getMessage());
		}
		assertEquals(0, broker.getVmList().size());
	}

	@Test
	public void submitsOnlyTheValidatedNonNullVm() throws Exception {
		MobileDevice user = mobile("registered", 12);
		connect(user, "registeredNetwork");
		FogBroker broker = new FogBroker("registeredBroker");
		Vm vm = new Vm(12, broker.getId(), 1000, 1, 128, 1000, 128,
			"test", new CloudletSchedulerTimeShared());
		user.setVmMobileDevice(vm);

		MobileUserRegistration.submitVm(broker, user);

		assertEquals(1, broker.getVmList().size());
		assertSame(vm, broker.getVmList().get(0));
	}

	@Test
	public void rejectsVmOwnedByAnotherBroker() throws Exception {
		MobileDevice user = mobile("wrongBroker", 14);
		connect(user, "wrongBrokerNetwork");
		FogBroker expectedBroker = new FogBroker("expectedBroker");
		FogBroker otherBroker = new FogBroker("otherBroker");
		user.setVmMobileDevice(new Vm(14, otherBroker.getId(), 1000, 1, 128, 1000,
			128, "test", new CloudletSchedulerTimeShared()));

		try {
			MobileUserRegistration.submitVm(expectedBroker, user);
			fail("Expected VM ownership mismatch to be rejected");
		} catch (IllegalStateException error) {
			assertEquals("Cannot register user wrongBroker with a VM owned by another broker",
				error.getMessage());
		}
		assertEquals(0, expectedBroker.getVmList().size());
	}

	private static MobileDevice mobile(String name, int id) {
		return new MobileDevice(name, 0, 0, id, 0, 0);
	}

	private static void connect(MobileDevice user, String name) {
		FogDevice server = new FogDevice(name + "Server", 0, 0, user.getMyId());
		ApDevice accessPoint = new ApDevice(name + "Ap", 0, 0, user.getMyId());
		accessPoint.setServerCloudlet(server);
		user.setSourceAp(accessPoint);
		user.setSourceServerCloudlet(server);
	}
}
