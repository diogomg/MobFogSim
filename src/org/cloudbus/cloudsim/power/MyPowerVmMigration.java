package org.cloudbus.cloudsim.power;

import java.util.List;

import org.cloudbus.cloudsim.Host;

public class MyPowerVmMigration extends
	PowerVmAllocationPolicyMigrationAbstract {

	public MyPowerVmMigration(List<? extends Host> hostList,
		PowerVmSelectionPolicy vmSelectionPolicy) {
		super(hostList, vmSelectionPolicy);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected boolean isHostOverUtilized(PowerHost host) {
		// TODO Auto-generated method stub
		return false;
	}

}
