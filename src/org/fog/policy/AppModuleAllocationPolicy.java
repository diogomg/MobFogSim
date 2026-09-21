package org.fog.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;

/** Allocation policy with authoritative VM-to-host lookup state. */
public final class AppModuleAllocationPolicy extends VmAllocationPolicy {

	private final Map<String, Host> vmToHost =
		new LinkedHashMap<String, Host>();
	private final List<Integer> appModuleIds = new ArrayList<Integer>();

	public AppModuleAllocationPolicy(List<? extends Host> hosts) {
		super(validatedHostList(hosts));
	}

	@Override
	public boolean allocateHostForVm(Vm vm) {
		if (vm == null) {
			throw new IllegalArgumentException("VM cannot be null");
		}
		Host existing = findHost(vm);
		if (existing != null) {
			return adoptExisting(vm, existing);
		}
		for (Host host : this.<Host>getHostList()) {
			if (allocate(vm, host)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (vm == null) {
			throw new IllegalArgumentException("VM cannot be null");
		}
		if (host == null) {
			return false;
		}
		Host existing = findHost(vm);
		if (existing != null) {
			return existing == host && adoptExisting(vm, existing);
		}
		return allocate(vm, host);
	}

	@Override
	public List<Map<String, Object>> optimizeAllocation(
		List<? extends Vm> vmList) {
		return Collections.emptyList();
	}

	@Override
	public void deallocateHostForVm(Vm vm) {
		if (vm == null) {
			return;
		}
		String uid = Vm.getUid(vm.getUserId(), vm.getId());
		Host host = findHost(vm);
		if (host == null) {
			vmToHost.remove(uid);
			if (!containsModuleId(vm.getId())) {
				appModuleIds.remove(Integer.valueOf(vm.getId()));
			}
			return;
		}
		Vm allocated = host.getVm(vm.getId(), vm.getUserId());
		if (allocated != null) {
			host.vmDestroy(allocated);
		}
		vmToHost.remove(uid);
		if (!containsModuleId(vm.getId())) {
			appModuleIds.remove(Integer.valueOf(vm.getId()));
		}
	}

	@Override
	public Host getHost(Vm vm) {
		return vm == null ? null : findHost(vm);
	}

	@Override
	public Host getHost(int vmId, int userId) {
		return findHost(vmId, userId);
	}

	/**
	 * Returns the sole configured host, or {@code null} when the policy has zero
	 * or multiple configured hosts.
	 */
	public Host getFogHost() {
		List<Host> hosts = getHostList();
		return hosts.size() == 1 ? hosts.get(0) : null;
	}

	/** Returns an immutable snapshot of IDs allocated through this policy. */
	public List<Integer> getAppModuleIds() {
		return Collections.unmodifiableList(
			new ArrayList<Integer>(appModuleIds));
	}

	/** @deprecated Use {@link #getAppModuleIds()}. */
	@Deprecated
	public List<Integer> getAppModuleIdsIds() {
		return getAppModuleIds();
	}

	private boolean allocate(Vm vm, Host host) {
		if (!host.vmCreate(vm)) {
			return false;
		}
		register(vm, host);
		return true;
	}

	private boolean adoptExisting(Vm vm, Host host) {
		if (host.getVm(vm.getId(), vm.getUserId()) != vm) {
			return false;
		}
		register(vm, host);
		return true;
	}

	private void register(Vm vm, Host host) {
		vmToHost.put(vm.getUid(), host);
		Integer moduleId = Integer.valueOf(vm.getId());
		if (!appModuleIds.contains(moduleId)) {
			appModuleIds.add(moduleId);
		}
	}

	private Host findHost(int vmId, int userId) {
		String uid = Vm.getUid(userId, vmId);
		Host mapped = vmToHost.get(uid);
		if (containsVm(mapped, vmId, userId)) {
			return mapped;
		}
		vmToHost.remove(uid);
		for (Host host : this.<Host>getHostList()) {
			if (containsVm(host, vmId, userId)) {
				vmToHost.put(uid, host);
				return host;
			}
		}
		return null;
	}

	private Host findHost(Vm vm) {
		Host known = findHost(vm.getId(), vm.getUserId());
		if (known != null) {
			return known;
		}
		Host attached = vm.getHost();
		if (containsVm(attached, vm.getId(), vm.getUserId())) {
			vmToHost.put(vm.getUid(), attached);
			return attached;
		}
		return null;
	}

	private boolean containsModuleId(int moduleId) {
		for (Map.Entry<String, Host> entry : vmToHost.entrySet()) {
			Host host = entry.getValue();
			for (Vm hosted : host.getVmList()) {
				if (hosted.getId() == moduleId
					&& hosted.getUid().equals(entry.getKey())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean containsVm(Host host, int vmId, int userId) {
		return host != null && host.getVm(vmId, userId) != null;
	}

	private static List<? extends Host> validatedHostList(
		List<? extends Host> hosts) {
		if (hosts == null) {
			throw new IllegalArgumentException("Fog host list cannot be null");
		}
		List<Host> copy = new ArrayList<Host>(hosts.size());
		for (Host host : hosts) {
			if (host == null) {
				throw new IllegalArgumentException("Fog host list cannot contain null");
			}
			copy.add(host);
		}
		return Collections.unmodifiableList(copy);
	}
}
