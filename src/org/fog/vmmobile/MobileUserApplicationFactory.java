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
		if (appId == null || appId.trim().isEmpty()) {
			throw new IllegalArgumentException("Mobile application ID cannot be empty");
		}
		if (userVm == null) {
			throw new IllegalArgumentException("Mobile application VM cannot be null");
		}

		Application application = Application.createApplication(appId, brokerId);
		application.addAppModule(userVm);
		String client = "client" + mobileId;
		String eeg = "EEG" + mobileId;
		String display = "DISPLAY" + mobileId;
		String sensorTuple = "_SENSOR" + mobileId;
		String concentrationTuple = "CONCENTRATION" + mobileId;
		String globalStateTuple = "GLOBAL_GAME_STATE" + mobileId;
		String vmName = userVm.getName();
		application.addAppModule(client, "appModuleClient" + mobileId, 10);

		application.addAppEdge(eeg, client, 966, 54, eeg, Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge(client, vmName, 966, 54, sensorTuple,
			Tuple.UP, AppEdge.MODULE);
		application.addAppEdge(vmName, vmName, 1000, 966, 54,
			"PLAYER_GAME_STATE" + mobileId, Tuple.UP, AppEdge.MODULE);
		application.addAppEdge(vmName, client, 2439, 87, concentrationTuple,
			Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge(vmName, client, 2439, 28, 87, globalStateTuple,
			Tuple.DOWN, AppEdge.MODULE);
		application.addAppEdge(client, display, 2439, 87,
			"SELF_STATE_UPDATE" + mobileId, Tuple.DOWN, AppEdge.ACTUATOR);
		application.addAppEdge(client, display, 2439, 87,
			"GLOBAL_STATE_UPDATE" + mobileId, Tuple.DOWN, AppEdge.ACTUATOR);

		application.addTupleMapping(client, eeg, sensorTuple,
			new FractionalSelectivity(0.9));
		application.addTupleMapping(client, concentrationTuple,
			"SELF_STATE_UPDATE" + mobileId, new FractionalSelectivity(1.0));
		application.addTupleMapping(vmName, sensorTuple, concentrationTuple,
			new FractionalSelectivity(1.0));
		application.addTupleMapping(client, globalStateTuple,
			"GLOBAL_STATE_UPDATE" + mobileId, new FractionalSelectivity(1.0));

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
}
