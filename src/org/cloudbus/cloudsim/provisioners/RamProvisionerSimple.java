/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for
 * Modeling and Simulation of Clouds Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2009-2012, The University
 * of Melbourne, Australia
 */

package org.cloudbus.cloudsim.provisioners;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

/**
 * RamProvisionerSimple is an extension of RamProvisioner which uses a
 * best-effort policy to allocate memory to a VM.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class RamProvisionerSimple extends RamProvisioner {

	/** The RAM table. */
	private Map<String, Integer> ramTable;

	/**
	 * Instantiates a new ram provisioner simple.
	 * 
	 * @param availableRam
	 *        the available ram
	 */
	public RamProvisionerSimple(int availableRam) {
		super(availableRam);
		setRamTable(new HashMap<String, Integer>());
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#allocateRamForVm(cloudsim.Vm,
	 * int)
	 */
	@Override
	public boolean allocateRamForVm(Vm vm, int ram) {
		int maxRam = vm.getRam();

		if (ram >= maxRam) {
			ram = maxRam;
		}

		deallocateRamForVm(vm);

		try (FileWriter fw1 = new FileWriter("creating_modules.txt", true);
			BufferedWriter bw1 = new BufferedWriter(fw1);
			PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.print(CloudSim.clock() + " RAM " + getAvailableRam() + " required " + ram
				+ " result " + (getAvailableRam() - ram) + " App " + vm.getVmm() + " device ");
			if (vm.getHost() != null) {
				out1.println(vm.getHost().getDatacenter().getName());
			}
			else {
				out1.println("null");
			}
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (getAvailableRam() >= ram) {
			setAvailableRam(getAvailableRam() - ram);
			getRamTable().put(vm.getUid(), ram);
			vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));
			return true;
		}

		vm.setCurrentAllocatedRam(getAllocatedRamForVm(vm));

		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see
	 * cloudsim.provisioners.RamProvisioner#getAllocatedRamForVm(cloudsim.Vm)
	 */
	@Override
	public int getAllocatedRamForVm(Vm vm) {
		if (getRamTable().containsKey(vm.getUid())) {
			return getRamTable().get(vm.getUid());
		}
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#deallocateRamForVm(cloudsim.Vm)
	 */
	@Override
	public void deallocateRamForVm(Vm vm) {
		if (getRamTable().containsKey(vm.getUid())) {
			int amountFreed = getRamTable().remove(vm.getUid());
			setAvailableRam(getAvailableRam() + amountFreed);
			vm.setCurrentAllocatedRam(0);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#deallocateRamForVm(cloudsim.Vm)
	 */
	@Override
	public void deallocateRamForAllVms() {
		super.deallocateRamForAllVms();
		getRamTable().clear();
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.provisioners.RamProvisioner#isSuitableForVm(cloudsim.Vm,
	 * int)
	 */
	@Override
	public boolean isSuitableForVm(Vm vm, int ram) {
		int allocatedRam = getAllocatedRamForVm(vm);
		boolean result = allocateRamForVm(vm, ram);
		deallocateRamForVm(vm);
		if (allocatedRam > 0) {
			allocateRamForVm(vm, allocatedRam);
		}
		return result;
	}

	/**
	 * Gets the ram table.
	 * 
	 * @return the ram table
	 */
	protected Map<String, Integer> getRamTable() {
		return ramTable;
	}

	/**
	 * Sets the ram table.
	 * 
	 * @param ramTable
	 *        the ram table
	 */
	protected void setRamTable(Map<String, Integer> ramTable) {
		this.ramTable = ramTable;
	}

}
