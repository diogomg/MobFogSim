package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Tuple;

/** Builds the application graph owned by one mobile user. */
public final class MobileUserApplicationFactory {

	private MobileUserApplicationFactory() {
	}

	public static Application create(String appId, int brokerId, int mobileId,
		AppModule userVm) {
		MobileApplicationDescriptor descriptor =
			MobileApplicationDescriptor.forUser(mobileId,
				mobileNameFromVm(userVm), 0);
		return create(appId, brokerId, userVm, descriptor,
			MobileApplicationProfile.standard());
	}

	public static Application create(MobileApplicationDescriptor descriptor,
		int brokerId, AppModule userVm) {
		return create(descriptor, brokerId, userVm,
			MobileApplicationProfile.standard());
	}

	public static Application create(MobileApplicationDescriptor descriptor,
		int brokerId, AppModule userVm, MobileApplicationProfile profile) {
		if (descriptor == null) {
			throw new IllegalArgumentException(
				"Mobile application descriptor cannot be null");
		}
		if (profile == null) {
			throw new IllegalArgumentException(
				"Mobile application profile cannot be null");
		}
		return create(descriptor.getApplicationId(), brokerId, userVm,
			descriptor, profile);
	}

	private static Application create(String appId, int brokerId,
		AppModule userVm, MobileApplicationDescriptor descriptor,
		MobileApplicationProfile profile) {
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("Mobile application ID cannot be empty");
		}
		if (userVm == null) {
			throw new IllegalArgumentException("Mobile application VM cannot be null");
		}

		Application application = Application.createApplication(appId, brokerId);
		application.addAppModule(userVm);
		String client = descriptor.getClientModuleName();
		String eeg = descriptor.getSensorTupleType();
		String display = descriptor.getActuatorType();
		String sensorTuple = descriptor.getSensorOutputTupleType();
		String concentrationTuple = descriptor.getConcentrationTupleType();
		String globalStateTuple = descriptor.getGlobalStateTupleType();
		String vmName = userVm.getName();
		application.addAppModule(client, descriptor.getClientImageName(),
			profile.getClientRamMebibytes());

		application.addAppEdge(eeg, client, profile.getTupleCpuLength(),
			profile.getUplinkTupleBytes(), eeg, Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge(client, vmName, profile.getTupleCpuLength(),
			profile.getUplinkTupleBytes(), sensorTuple, Tuple.UP, AppEdge.MODULE);
		application.addAppEdge(vmName, vmName,
			profile.getStateUpdateIntervalMillis(), profile.getTupleCpuLength(),
			profile.getUplinkTupleBytes(),
			descriptor.getPlayerStateTupleType(), Tuple.UP, AppEdge.MODULE);
		application.addAppEdge(vmName, client, profile.getResponseCpuLength(),
			profile.getDownlinkTupleBytes(), concentrationTuple, Tuple.DOWN,
			AppEdge.MODULE);
		application.addAppEdge(vmName, client,
			profile.getGlobalUpdateIntervalMillis(),
			profile.getGlobalUpdateCpuLength(), profile.getDownlinkTupleBytes(),
			globalStateTuple, Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge(client, display,
			profile.getResponseCpuLength(), profile.getDownlinkTupleBytes(),
			descriptor.getSelfStateUpdateTupleType(), Tuple.DOWN, AppEdge.ACTUATOR);
		application.addAppEdge(client, display,
			profile.getResponseCpuLength(), profile.getDownlinkTupleBytes(),
			descriptor.getGlobalStateUpdateTupleType(), Tuple.DOWN, AppEdge.ACTUATOR);

		application.addTupleMapping(client, eeg, sensorTuple,
			new FractionalSelectivity(profile.getSelectivity()));
		application.addTupleMapping(client, concentrationTuple,
			descriptor.getSelfStateUpdateTupleType(),
			new FractionalSelectivity(1.0));
		application.addTupleMapping(vmName, sensorTuple, concentrationTuple,
			new FractionalSelectivity(1.0));
		application.addTupleMapping(client, globalStateTuple,
			descriptor.getGlobalStateUpdateTupleType(),
			new FractionalSelectivity(1.0));

		List<String> modules = new ArrayList<String>();
		modules.add(eeg);
		modules.add(client);
		modules.add(vmName);
		modules.add(client);
		modules.add(display);
		application.setLoops(Collections.singletonList(new AppLoop(modules)));
		application.setPlacementStrategy("Mapping");
		return application;
	}

	private static String mobileNameFromVm(AppModule userVm) {
		if (userVm == null) {
			return "unknown";
		}
		String prefix = "AppModuleVm_";
		return userVm.getName().startsWith(prefix)
			? userVm.getName().substring(prefix.length()) : userVm.getName();
	}
}
