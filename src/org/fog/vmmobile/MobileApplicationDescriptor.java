package org.fog.vmmobile;

/**
 * Deterministic names for one application/VM pair owned by a mobile user.
 * Application zero retains the historical names for backwards compatibility.
 */
public final class MobileApplicationDescriptor {
	private final int mobileId;
	private final String mobileName;
	private final int applicationIndex;
	private final String suffix;

	private MobileApplicationDescriptor(int mobileId, String mobileName,
		int applicationIndex) {
		if (mobileId < 0) {
			throw new IllegalArgumentException("Mobile ID cannot be negative");
		}
		if (mobileName == null || mobileName.trim().isEmpty()) {
			throw new IllegalArgumentException("Mobile name cannot be empty");
		}
		if (applicationIndex < 0) {
			throw new IllegalArgumentException(
				"Application index cannot be negative");
		}
		this.mobileId = mobileId;
		this.mobileName = mobileName;
		this.applicationIndex = applicationIndex;
		this.suffix = applicationIndex == 0
			? "" : "_app" + applicationIndex;
	}

	public static MobileApplicationDescriptor forUser(int mobileId,
		String mobileName, int applicationIndex) {
		return new MobileApplicationDescriptor(mobileId, mobileName,
			applicationIndex);
	}

	public int getApplicationIndex() {
		return applicationIndex;
	}

	public int getVmId() {
		return mobileId + applicationIndex;
	}

	public String getApplicationId() {
		return "MyApp_vr_game" + mobileId + suffix;
	}

	public String getVmName() {
		return "AppModuleVm_" + mobileName + suffix;
	}

	public String getVmMonitorName() {
		return "Vm_" + mobileName + suffix;
	}

	public String getClientModuleName() {
		return "client" + mobileId + suffix;
	}

	public String getClientImageName() {
		return "appModuleClient" + mobileId + suffix;
	}

	public String getSensorEntityName() {
		return "Sensor" + mobileId + suffix;
	}

	public String getSensorTupleType() {
		return "EEG" + mobileId + suffix;
	}

	public String getActuatorEntityName() {
		return "Actuator" + mobileId + suffix;
	}

	public String getActuatorType() {
		return "DISPLAY" + mobileId + suffix;
	}

	public String getSensorOutputTupleType() {
		return "_SENSOR" + mobileId + suffix;
	}

	public String getConcentrationTupleType() {
		return "CONCENTRATION" + mobileId + suffix;
	}

	public String getPlayerStateTupleType() {
		return "PLAYER_GAME_STATE" + mobileId + suffix;
	}

	public String getGlobalStateTupleType() {
		return "GLOBAL_GAME_STATE" + mobileId + suffix;
	}

	public String getSelfStateUpdateTupleType() {
		return "SELF_STATE_UPDATE" + mobileId + suffix;
	}

	public String getGlobalStateUpdateTupleType() {
		return "GLOBAL_STATE_UPDATE" + mobileId + suffix;
	}
}
