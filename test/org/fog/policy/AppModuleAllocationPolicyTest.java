package org.fog.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.cloudbus.cloudsim.util.RunOutputMode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AppModuleAllocationPolicyTest {

	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setUp() throws IOException {
		Log.disable();
		RunOutputManager.initialize(temporaryFolder.newFolder("output").toPath(),
			RunOutputMode.NONE);
	}

	@After
	public void tearDown() {
		RunOutputManager.resetToDefault();
		Log.enable();
	}

	@Test
	public void constructorRejectsNullListsAndElements() {
		assertInvalidHosts(null);
		assertInvalidHosts(Collections.<Host>singletonList(null));
	}

	@Test
	public void zeroHostPolicyCannotAllocateAndReturnsNoFogHost() {
		AppModuleAllocationPolicy policy = new AppModuleAllocationPolicy(
			Collections.<Host>emptyList());

		assertFalse(policy.allocateHostForVm(vm(1, 1, 100)));
		assertNull(policy.getFogHost());
		assertTrue(policy.getHostList().isEmpty());
	}

	@Test
	public void constructorRetainsOneHostIndependentlyOfCallerList() {
		Host host = host(0, 1000);
		List<Host> hosts = new ArrayList<Host>();
		hosts.add(host);
		AppModuleAllocationPolicy policy = new AppModuleAllocationPolicy(hosts);

		hosts.clear();

		assertSame(host, policy.getFogHost());
		assertEquals(1, policy.getHostList().size());
		assertSame(host, policy.getHostList().get(0));
	}

	@Test
	public void allocationAndLookupReflectActualHostMembership() {
		Host host = host(0, 1000);
		AppModuleAllocationPolicy policy = policy(host);
		Vm vm = vm(7, 3, 100);

		assertTrue(policy.allocateHostForVm(vm));
		assertSame(host, policy.getHost(vm));
		assertSame(host, policy.getHost(7, 3));
		assertNull(policy.getHost(7, 4));
		assertEquals(Collections.singletonList(Integer.valueOf(7)),
			policy.getAppModuleIds());
		assertTrue(policy.allocateHostForVm(vm));
		assertFalse(policy.allocateHostForVm(vm(7, 3, 100)));
		assertEquals(1, policy.getAppModuleIds().size());
	}

	@Test
	public void allocationAdoptsAnIdenticalVmAlreadyReservedOnTheHost() {
		Host host = host(0, 1000);
		AppModuleAllocationPolicy policy = policy(host);
		Vm vm = vm(18, 4, 100);
		assertTrue(host.vmCreate(vm));
		long reservedStorage = host.getStorage();

		assertTrue(policy.allocateHostForVm(vm));

		assertSame(host, policy.getHost(vm));
		assertEquals(reservedStorage, host.getStorage());
		assertEquals(1, host.getVmList().size());
		assertEquals(Collections.singletonList(Integer.valueOf(18)),
			policy.getAppModuleIds());
	}

	@Test
	public void explicitAllocationTracksAnUnconfiguredDestinationHost() {
		Host configured = host(0, 1000);
		Host other = host(1, 1000);
		AppModuleAllocationPolicy policy = policy(configured);
		Vm vm = vm(8, 2, 100);

		assertTrue(policy.allocateHostForVm(vm, other));
		assertSame(other, vm.getHost());
		assertSame(other, policy.getHost(vm));
		assertTrue(configured.getVmList().isEmpty());

		policy.deallocateHostForVm(vm);
		assertNull(vm.getHost());
		assertTrue(other.getVmList().isEmpty());
		assertTrue(policy.allocateHostForVm(vm, configured));
		assertSame(configured, policy.getHost(vm));
	}

	@Test
	public void multipleHostPolicyUsesTheFirstHostWithCapacity() {
		Host undersized = host(0, 50);
		Host suitable = host(1, 1000);
		AppModuleAllocationPolicy policy = new AppModuleAllocationPolicy(
			Arrays.asList(undersized, suitable));
		Vm vm = vm(12, 1, 100);

		assertTrue(policy.allocateHostForVm(vm));
		assertSame(suitable, policy.getHost(vm));
		assertNull(policy.getFogHost());
		assertTrue(undersized.getVmList().isEmpty());
	}

	@Test
	public void deallocationRemovesRegistryEntryAndAllowsReallocation() {
		Host host = host(0, 1000);
		AppModuleAllocationPolicy policy = policy(host);
		Vm vm = vm(9, 1, 100);
		long originalStorage = host.getStorage();

		assertTrue(policy.allocateHostForVm(vm));
		policy.deallocateHostForVm(vm);

		assertNull(vm.getHost());
		assertNull(policy.getHost(vm));
		assertTrue(policy.getAppModuleIds().isEmpty());
		assertEquals(originalStorage, host.getStorage());
		assertTrue(policy.allocateHostForVm(vm));
		assertEquals(Collections.singletonList(Integer.valueOf(9)),
			policy.getAppModuleIds());
	}

	@Test
	public void deallocatingUnknownVmDoesNotChangeHostCapacity() {
		Host host = host(0, 1000);
		AppModuleAllocationPolicy policy = policy(host);
		long originalStorage = host.getStorage();

		policy.deallocateHostForVm(vm(17, 1, 100));
		policy.deallocateHostForVm(null);

		assertEquals(originalStorage, host.getStorage());
		assertTrue(host.getVmList().isEmpty());
	}

	@Test
	public void failedAllocationDoesNotPublishRegistryState() {
		Host host = host(0, 50);
		AppModuleAllocationPolicy policy = policy(host);
		Vm oversized = vm(10, 1, 100);

		assertFalse(policy.allocateHostForVm(oversized));

		assertNull(oversized.getHost());
		assertNull(policy.getHost(oversized));
		assertTrue(policy.getAppModuleIds().isEmpty());
		assertTrue(host.getVmList().isEmpty());
	}

	@Test
	public void registryAndOptimizationPlanAreImmutableAndNonNull() {
		AppModuleAllocationPolicy policy = policy(host(0, 1000));
		assertTrue(policy.allocateHostForVm(vm(11, 1, 100)));

		try {
			policy.getAppModuleIds().add(Integer.valueOf(12));
			fail("The module ID registry must be immutable");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}

		assertTrue(policy.optimizeAllocation(Collections.<Vm>emptyList()).isEmpty());
		try {
			policy.optimizeAllocation(Collections.<Vm>emptyList()).add(null);
			fail("The empty optimization plan must be immutable");
		}
		catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	private static void assertInvalidHosts(List<? extends Host> hosts) {
		try {
			new AppModuleAllocationPolicy(hosts);
			fail("Expected an invalid host-list failure");
		}
		catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static AppModuleAllocationPolicy policy(Host host) {
		return new AppModuleAllocationPolicy(Collections.singletonList(host));
	}

	private static Host host(int id, long storage) {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerSimple(2000)));
		return new Host(id, new RamProvisionerSimple(4096),
			new BwProvisionerSimple(10000), storage, processingElements,
			new VmSchedulerTimeShared(processingElements));
	}

	private static Vm vm(int id, int userId, long size) {
		return new Vm(id, userId, 500, 1, 128, 1000, size, "Xen",
			new CloudletSchedulerTimeShared());
	}
}
