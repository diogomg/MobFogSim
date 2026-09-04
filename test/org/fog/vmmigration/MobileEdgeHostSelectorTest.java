package org.fog.vmmigration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.placement.MobileController;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.vmmobile.constants.Services;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MobileEdgeHostSelectorTest {

	private HostedMobileDevice owner;
	private FogDevice source;
	private FogDevice fogCandidate;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);

		owner = mobile("owner", 0, 0, 4096, 10000, 10000);
		owner.setVmMobileDevice(vm(1, 512, 1000, 1000));
		source = fog("source", 500, 500);
		fogCandidate = fog("fogCandidate", 100, 0);
		connect(source, fogCandidate);
		owner.setSourceServerCloudlet(source);
		owner.setVmLocalServerCloudlet(source);
		MobileController.setSmartThings(new ArrayList<MobileDevice>(Collections.singletonList(owner)));
	}

	@After
	public void resetGlobalState() {
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);
		MobileController.setSmartThings(Collections.<MobileDevice>emptyList());
	}

	@Test
	public void fogOnlySelectsFogEvenWhenACloserDeviceExists() {
		HostedMobileDevice peer = connectedPeer("peer", 10, 0);
		setMobiles(owner, peer);
		VmDestinationPolicy.configure(VmDestinationPolicy.EDGE_SERVERS_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(fogCandidate, owner.getDestinationServerCloudlet());
	}

	@Test
	public void fogOnlyFailsWhenFogServiceAgreementFails() {
		VmDestinationPolicy.configure(VmDestinationPolicy.EDGE_SERVERS_ONLY);
		fogCandidate.setAvailable(false);

		assertFalse(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertNull(owner.getDestinationServerCloudlet());
	}

	@Test
	public void deviceOnlyChoosesClosestEligibleDevice() {
		HostedMobileDevice farther = connectedPeer("farther", 40, 0);
		HostedMobileDevice closer = connectedPeer("closer", 10, 0);
		setMobiles(owner, farther, closer);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(closer, owner.getDestinationServerCloudlet());
	}

	@Test
	public void deviceOnlyDoesNotFallBackToFog() {
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertFalse(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertNull(owner.getDestinationServerCloudlet());
	}

	@Test
	public void hybridReplacesFogWithACloserDevice() {
		HostedMobileDevice peer = connectedPeer("peer", 10, 0);
		setMobiles(owner, peer);
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(peer, owner.getDestinationServerCloudlet());
	}

	@Test
	public void hybridKeepsFogWhenFogIsCloser() {
		HostedMobileDevice peer = connectedPeer("peer", 150, 0);
		setMobiles(owner, peer);
		VmDestinationPolicy.configure(VmDestinationPolicy.HYBRID);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(fogCandidate, owner.getDestinationServerCloudlet());
	}

	@Test
	public void hybridKeepsFogWhenPeerIsEquallyDistant() {
		HostedMobileDevice peer = connectedPeer("peer", 100, 0);
		setMobiles(owner, peer);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(fogCandidate, owner.getDestinationServerCloudlet());
	}

	@Test
	public void hybridFallsBackToDeviceWhenFogIsUnavailable() {
		HostedMobileDevice peer = connectedPeer("peer", 50, 0);
		setMobiles(owner, peer);
		fogCandidate.setAvailable(false);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(peer, owner.getDestinationServerCloudlet());
	}

	@Test
	public void ownerCannotHostItsOwnVm() {
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);
		setMobiles(owner);

		assertFalse(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
	}

	@Test
	public void currentVmHostIsExcluded() {
		HostedMobileDevice currentHost = connectedPeer("currentHost", 5, 0);
		HostedMobileDevice alternative = connectedPeer("alternative", 20, 0);
		owner.setVmLocalServerCloudlet(currentHost);
		setMobiles(owner, currentHost, alternative);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(alternative, owner.getDestinationServerCloudlet());
	}

	@Test
	public void disconnectedDeviceIsExcluded() {
		HostedMobileDevice disconnected = mobile("disconnected", 5, 0, 4096, 10000, 10000);
		HostedMobileDevice connected = connectedPeer("connected", 20, 0);
		setMobiles(owner, disconnected, connected);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(connected, owner.getDestinationServerCloudlet());
	}

	@Test
	public void migratingDeviceIsExcluded() {
		HostedMobileDevice migrating = connectedPeer("migrating", 5, 0);
		migrating.setMigStatus(true);
		HostedMobileDevice idle = connectedPeer("idle", 20, 0);
		setMobiles(owner, migrating, idle);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(idle, owner.getDestinationServerCloudlet());
	}

	@Test
	public void unavailableDeviceIsExcluded() {
		HostedMobileDevice unavailable = connectedPeer("unavailable", 5, 0);
		unavailable.setAvailable(false);
		HostedMobileDevice available = connectedPeer("available", 20, 0);
		setMobiles(owner, unavailable, available);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(available, owner.getDestinationServerCloudlet());
	}

	@Test
	public void deviceWithoutEnoughResourcesIsExcluded() {
		HostedMobileDevice undersized = mobile("undersized", 5, 0, 256, 500, 500);
		undersized.setSourceAp(new ApDevice("undersizedAp", 5, 0, 0));
		HostedMobileDevice suitable = connectedPeer("suitable", 20, 0);
		setMobiles(owner, undersized, suitable);
		VmDestinationPolicy.configure(VmDestinationPolicy.END_DEVICES_ONLY);

		assertTrue(MobileEdgeHostSelector.selectDestination(owner, fogCandidate));
		assertSame(suitable, owner.getDestinationServerCloudlet());
	}

	private HostedMobileDevice connectedPeer(String name, int x, int y) {
		HostedMobileDevice peer = mobile(name, x, y, 4096, 10000, 10000);
		peer.setSourceAp(new ApDevice(name + "Ap", x, y, 0));
		return peer;
	}

	private void setMobiles(MobileDevice... devices) {
		MobileController.setSmartThings(new ArrayList<MobileDevice>(Arrays.asList(devices)));
	}

	private static HostedMobileDevice mobile(String name, int x, int y, int ram,
		long bandwidth, long storage) {
		return new HostedMobileDevice(name, x, y, host(ram, bandwidth, storage));
	}

	private static FogDevice fog(String name, int x, int y) {
		FogDevice fog = new FogDevice(name, x, y, 0);
		Service service = new Service();
		service.setType(Services.PRIVATE);
		fog.setService(service);
		fog.setAvailable(true);
		return fog;
	}

	private static void connect(FogDevice source, FogDevice destination) {
		source.connectTransportPeer(destination, 1000.0);
	}

	private static Vm vm(int id, int ram, long bandwidth, long size) {
		return new Vm(id, 1, 1000, 1, ram, bandwidth, size, "Xen",
			new CloudletSchedulerTimeShared());
	}

	private static PowerHost host(int ram, long bandwidth, long storage) {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerOverbooking(2000)));
		return new PowerHost(0, new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bandwidth), storage, processingElements,
			new StreamOperatorScheduler(processingElements),
			new FogLinearPowerModel(100.0, 50.0));
	}

	private static final class HostedMobileDevice extends MobileDevice {
		private final PowerHost host;

		HostedMobileDevice(String name, int x, int y, PowerHost host) {
			super(name, x, y, 0, 0, 0);
			this.host = host;
		}

		@Override
		public PowerHost getHost() {
			return host;
		}
	}
}
