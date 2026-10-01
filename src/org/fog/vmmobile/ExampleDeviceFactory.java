package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;
import org.fog.localization.Coordinate;
import org.fog.localization.GridGenerator;
import org.fog.localization.GridPosition;
import org.fog.localization.MapBounds;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.vmmigration.BeforeMigration;
import org.fog.vmmigration.CompleteVM;
import org.fog.vmmigration.ContainerVM;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LiveMigration;
import org.fog.vmmigration.LowestDistBwSmartThingAP;
import org.fog.vmmigration.LowestDistBwSmartThingServerCloudlet;
import org.fog.vmmigration.LowestLatency;
import org.fog.vmmigration.PrepareCompleteVM;
import org.fog.vmmigration.PrepareContainerVM;
import org.fog.vmmigration.PrepareLiveMigration;
import org.fog.vmmigration.Service;
import org.fog.vmmigration.VmMigrationTechnique;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.policy.MigrationStrategyPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.policy.MovementDirection;
import org.fog.vmmobile.policy.ServiceType;

/** Constructs the physical devices used by the example simulations. */
final class ExampleDeviceFactory {

	private ExampleDeviceFactory() {
	}

	static void addFixedAccessPoints(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, MapBounds bounds) {
		int index = 0;
		GridGenerator grid = new GridGenerator(bounds);
		for (GridPosition position : grid.fixedPositions(
			GridGenerator.spacingForCoverage(MaxAndMin.AP_COVERAGE))) {
			accessPoints.add(index, new ApDevice("AccessPoint" + index,
				position.getX(), position.getY(), index,
				100 * 1024 * 1024, 200, MaxAndMin.MAX_ST_IN_AP,
				100 * 1024 * 1024, 4));
			index++;
		}
		LogMobile.debug("AppExample.java", "Total of accessPoints: " + index);
	}

	static void addRandomAccessPoint(List<ApDevice> accessPoints,
		Coordinate coordinateSpace, int index, MapBounds bounds) {
		GridPosition position = new GridGenerator(bounds)
			.randomPosition(AppExample.getRand());
		accessPoints.add(index, new ApDevice("AccessPoint" + index,
			position.getX(), position.getY(), index,
			100 * 1024 * 1024, 200, MaxAndMin.MAX_ST_IN_AP,
			100 * 1024 * 1024, 4));
	}

	static void addMobileDevice(List<MobileDevice> mobileDevices,
		Coordinate coordinateSpace, int index,
		List<MobileApplicationProfile> applicationProfiles) {
		validateApplicationProfiles(applicationProfiles);
		int listSizeBefore = mobileDevices.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildMobileDevice(mobileDevices, coordinateSpace, index,
				applicationProfiles);
		}
		catch (RuntimeException error) {
			truncateList(mobileDevices, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildMobileDevice(List<MobileDevice> mobileDevices,
		Coordinate coordinateSpace, int index,
		List<MobileApplicationProfile> applicationProfiles) {
		MovementDirection direction = MovementDirection.fromLegacy(
			AppExample.getRand().nextInt(MaxAndMin.MAX_DIRECTION - 1) + 1);
		int speed = AppExample.getRand().nextInt(MaxAndMin.MAX_SPEED - 1) + 1;
		MigrationTechniquePolicy techniquePolicy =
			effectiveMigrationTechnique(applicationProfiles);
		VmMigrationTechnique migrationTechnique =
			createMobileMigrationTechnique(techniquePolicy);
		BeforeMigration beforeMigration = createBeforeMigration(techniquePolicy);

		Set<MobileSensor> sensors = new HashSet<MobileSensor>();
		Set<MobileActuator> actuators = new HashSet<MobileActuator>();
		for (int applicationIndex = 0;
			applicationIndex < applicationProfiles.size(); applicationIndex++) {
			MobileApplicationProfile profile =
				applicationProfiles.get(applicationIndex);
			MobileApplicationDescriptor descriptor =
				MobileApplicationDescriptor.forUser(index, "SmartThing" + index,
					applicationIndex);
			sensors.add(new MobileSensor(descriptor.getSensorEntityName(),
				descriptor.getSensorTupleType(), index,
				descriptor.getApplicationId(), new DeterministicDistribution(
					profile.getSensorIntervalMillis())));
			actuators.add(new MobileActuator(descriptor.getActuatorEntityName(),
				index, descriptor.getApplicationId(), descriptor.getActuatorType()));
		}

		PowerHost host = createHost(46533, 1024 * 16, 512 * 1024,
			1000 * 1024 * 1024, 87.53d, 82.44d);
		List<Host> hostList = singletonHost(host);
		FogDeviceCharacteristics characteristics = characteristics("Android",
			"empty", host, 1.0, 0.005, 0.0001, 0.001);
		AppModuleAllocationPolicy allocationPolicy =
			new AppModuleAllocationPolicy(hostList);
		float maxServiceValue = AppExample.getRand().nextFloat() * 100;
		try {
			MobileDevice mobileDevice = new MobileDevice("SmartThing" + index,
				characteristics, allocationPolicy, new LinkedList<Storage>(), 2,
				1 * 1024 * 1024, 2 * 1024 * 1024, 2, 0.01,
				0, 0, index, direction, speed, maxServiceValue, 4,
				migrationTechnique);
			mobileDevice.setTempSimulation(0);
			mobileDevice.setTimeFinishDeliveryVm(-1);
			mobileDevice.setTimeFinishHandoff(0);
			mobileDevice.setMigrationTechniquePolicy(techniquePolicy);
			mobileDevice.setBeforeMigrate(beforeMigration);
			mobileDevice.setApplicationProfiles(applicationProfiles);
			if (usesLowestLatencyMigration(applicationProfiles)) {
				mobileDevice.setMigrationStrategy(new LowestLatency(
					AppExample.getServerCloudlets(), AppExample.getApDevices(),
					AppExample.getMigrationPointPolicy(), techniquePolicy));
			}
			mobileDevice.setSensors(sensors);
			mobileDevice.setActuators(actuators);
			mobileDevice.setTravelPredictionTime(
				AppExample.getTravelPredictionTimeForST());
			mobileDevice.setMobilityPredictionError(
				AppExample.getMobilityPredictionError());
			mobileDevices.add(index, mobileDevice);
		}
		catch (Exception error) {
			throw new SimulationBuildException(
				"Could not create mobile user " + index, error);
		}
	}

	static void addRandomServerCloudlet(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace, int index, MapBounds bounds) {
		int listSizeBefore = serverCloudlets.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildRandomServerCloudlet(serverCloudlets, coordinateSpace, index,
				bounds);
		}
		catch (RuntimeException error) {
			truncateList(serverCloudlets, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildRandomServerCloudlet(
		List<FogDevice> serverCloudlets, Coordinate coordinateSpace, int index,
		MapBounds bounds) {
		DecisionMigration migrationStrategy = createMigrationStrategy();
		MigrationTechniquePolicy techniquePolicy =
			AppExample.getMigrationTechniquePolicy();
		BeforeMigration beforeMigration = createBeforeMigration(techniquePolicy);
		PowerHost host = createHost(3234, 1024, 16 * 1024 * 1024L,
			1000 * 1024 * 1024, 107.339d, 83.433d);
		FogDeviceCharacteristics characteristics = characteristics("Linux",
			"Empty", host, 3.0, 0.05, 0.001, 0.0);
		AppModuleAllocationPolicy allocationPolicy =
			new AppModuleAllocationPolicy(singletonHost(host));
		Service serviceOffer = randomServiceOffer();
		try {
			GridPosition position = new GridGenerator(bounds)
				.randomPosition(AppExample.getRand());
			double upLink = randomCloudletBandwidth();
			double downLink = randomCloudletBandwidth();
			FogDevice cloudlet = new FogDevice("ServerCloudlet" + index,
				characteristics, allocationPolicy, new LinkedList<Storage>(), 10,
				upLink, downLink, 4, 0.01, position.getX(), position.getY(), index,
				serviceOffer, migrationStrategy, techniquePolicy, beforeMigration);
			serverCloudlets.add(index, cloudlet);
		}
		catch (Exception error) {
			throw new SimulationBuildException(
				"Could not create random server cloudlet " + index, error);
		}
	}

	static void addFixedServerCloudlets(List<FogDevice> serverCloudlets,
		Coordinate coordinateSpace, MapBounds bounds) {
		int listSizeBefore = serverCloudlets.size();
		CloudSim.EntityRegistrationCheckpoint checkpoint =
			CloudSim.checkpointEntityRegistrations();
		try {
			buildFixedServerCloudlets(serverCloudlets, coordinateSpace, bounds);
		}
		catch (RuntimeException error) {
			truncateList(serverCloudlets, listSizeBefore);
			CloudSim.restoreEntityRegistrations(checkpoint);
			throw error;
		}
	}

	private static void buildFixedServerCloudlets(
		List<FogDevice> serverCloudlets, Coordinate coordinateSpace,
		MapBounds bounds) {
		int index = 0;
		GridGenerator grid = new GridGenerator(bounds);
		for (GridPosition position : grid.fixedPositions(
			GridGenerator.spacingForCoverage(MaxAndMin.CLOUDLET_COVERAGE))) {
			DecisionMigration migrationStrategy = createMigrationStrategy();
			MigrationTechniquePolicy techniquePolicy =
				AppExample.getMigrationTechniquePolicy();
			BeforeMigration beforeMigration =
				createBeforeMigration(techniquePolicy);
			PowerHost host = createHost(3234, 1024, 16 * 1024 * 1024L,
				1000 * 1024 * 1024, 107.339d, 83.433d);
			FogDeviceCharacteristics characteristics = characteristics("Linux",
				"Empty", host, 3.0, 0.05, 0.001, 0.0);
			AppModuleAllocationPolicy allocationPolicy =
				new AppModuleAllocationPolicy(singletonHost(host));
			Service serviceOffer = randomServiceOffer();
			try {
				double upLink = randomCloudletBandwidth();
				double downLink = randomCloudletBandwidth();
				FogDevice cloudlet = new FogDevice("ServerCloudlet" + index,
					characteristics, allocationPolicy, new LinkedList<Storage>(), 10,
					upLink, downLink, 4, 0.01, position.getX(), position.getY(),
					index, serviceOffer, migrationStrategy, techniquePolicy,
					beforeMigration);
				serverCloudlets.add(index, cloudlet);
				cloudlet.setParentId(-1);
			}
			catch (Exception error) {
				throw new SimulationBuildException(
					"Could not create fixed server cloudlet " + index + " at ("
						+ position.getX() + ", " + position.getY() + ")", error);
			}
			index++;
		}
		LogMobile.debug("AppExample.java",
			"Total of serverCloudlets: " + index);
	}

	private static DecisionMigration createMigrationStrategy() {
		MigrationStrategyPolicy strategy =
			AppExample.getMigrationStrategyPolicy();
		if (strategy == MigrationStrategyPolicy.LOWEST_LATENCY) {
			return new LowestLatency(AppExample.getServerCloudlets(),
				AppExample.getApDevices(), AppExample.getMigrationPointPolicy(),
				AppExample.getMigrationTechniquePolicy());
		}
		if (strategy
			== MigrationStrategyPolicy.LOWEST_DISTANCE_TO_SERVER_CLOUDLET) {
			return new LowestDistBwSmartThingServerCloudlet(
				AppExample.getServerCloudlets(), AppExample.getApDevices(),
				AppExample.getMigrationPointPolicy(),
				AppExample.getMigrationTechniquePolicy());
		}
		return new LowestDistBwSmartThingAP(AppExample.getServerCloudlets(),
			AppExample.getApDevices(), AppExample.getMigrationPointPolicy(),
			AppExample.getMigrationTechniquePolicy());
	}

	private static VmMigrationTechnique createMobileMigrationTechnique(
		MigrationTechniquePolicy policy) {
		if (policy == MigrationTechniquePolicy.COMPLETE_VM) {
			return new CompleteVM(AppExample.getMigrationPointPolicy());
		}
		if (policy == MigrationTechniquePolicy.CONTAINER_VM) {
			return new ContainerVM(AppExample.getMigrationPointPolicy());
		}
		if (policy == MigrationTechniquePolicy.LIVE_MIGRATION) {
			return new LiveMigration(AppExample.getMigrationPointPolicy());
		}
		throw new IllegalArgumentException(
			"Unsupported migration technique " + policy);
	}

	private static BeforeMigration createBeforeMigration(
		MigrationTechniquePolicy policy) {
		if (policy == MigrationTechniquePolicy.COMPLETE_VM) {
			return new PrepareCompleteVM();
		}
		if (policy == MigrationTechniquePolicy.CONTAINER_VM) {
			return new PrepareContainerVM();
		}
		if (policy == MigrationTechniquePolicy.LIVE_MIGRATION) {
			return new PrepareLiveMigration();
		}
		throw new IllegalArgumentException(
			"Unsupported migration technique " + policy);
	}

	private static MigrationTechniquePolicy effectiveMigrationTechnique(
		List<MobileApplicationProfile> profiles) {
		for (MobileApplicationProfile profile : profiles) {
			if (profile.requiresLiveMigration()) {
				return MigrationTechniquePolicy.LIVE_MIGRATION;
			}
		}
		return AppExample.getMigrationTechniquePolicy();
	}

	private static boolean usesLowestLatencyMigration(
		List<MobileApplicationProfile> profiles) {
		for (MobileApplicationProfile profile : profiles) {
			if (profile.usesLowestLatencyMigration()) {
				return true;
			}
		}
		return false;
	}

	private static void validateApplicationProfiles(
		List<MobileApplicationProfile> applicationProfiles) {
		if (applicationProfiles == null || applicationProfiles.isEmpty()) {
			throw new IllegalArgumentException(
				"At least one application profile is required");
		}
		if (applicationProfiles.contains(null)) {
			throw new IllegalArgumentException(
				"Application profiles cannot contain null entries");
		}
	}

	private static Service randomServiceOffer() {
		Service service = new Service();
		service.setType(AppExample.getRand().nextInt(10000)
			% MaxAndMin.MAX_SERVICES);
		if (service.getServiceType() == ServiceType.HYBRID
			|| service.getServiceType() == ServiceType.PUBLIC) {
			service.setValue(AppExample.getRand().nextFloat() * 10);
		}
		else {
			service.setValue(0);
		}
		return service;
	}

	private static double randomCloudletBandwidth() {
		double maximum = AppExample.getMaxBandwidth() * 1024.0 * 1024.0;
		double minimum = (AppExample.getMaxBandwidth() - 1) * 1024.0 * 1024.0;
		return minimum + (maximum - minimum) * AppExample.getRand().nextDouble();
	}

	private static PowerHost createHost(int mips, int ram, long storage,
		int bandwidth, double maximumPower, double staticPower) {
		List<Pe> processingElements = new ArrayList<Pe>();
		processingElements.add(new Pe(0, new PeProvisionerOverbooking(mips)));
		return new PowerHost(FogUtils.generateEntityId(),
			new RamProvisionerSimple(ram),
			new BwProvisionerOverbooking(bandwidth), storage, processingElements,
			new StreamOperatorScheduler(processingElements),
			new FogLinearPowerModel(maximumPower, staticPower));
	}

	private static List<Host> singletonHost(Host host) {
		List<Host> hosts = new ArrayList<Host>();
		hosts.add(host);
		return hosts;
	}

	private static FogDeviceCharacteristics characteristics(String os,
		String virtualMachineMonitor, Host host, double processingCost,
		double memoryCost, double storageCost, double bandwidthCost) {
		return new FogDeviceCharacteristics("x86", os, virtualMachineMonitor,
			host, 10.0, processingCost, memoryCost, storageCost, bandwidthCost);
	}

	private static <T> void truncateList(List<T> values, int targetSize) {
		while (values.size() > targetSize) {
			values.remove(values.size() - 1);
		}
	}
}
