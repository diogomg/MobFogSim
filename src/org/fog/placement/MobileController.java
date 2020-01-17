package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.Actuator;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;
import org.fog.entities.Sensor;
import org.fog.localization.Coordinate;
import org.fog.localization.Distances;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.ModuleLaunchConfig;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.NextStep;
import org.fog.vmmobile.LogMobile;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.MobileEvents;

public class MobileController extends SimEntity {
	private static boolean migrationAble;
	private static int migPointPolicy;

	private static int stepPolicy; //Quantity of steps in the nextStep Function
	private static Coordinate coordDevices;//=new Coordinate(MaxAndMin.MAX_X, MaxAndMin.MAX_Y);//Grid/Map

	private static int migStrategyPolicy;
	private static int seed;

	private static List<FogDevice> serverCloudlets;
	private static List<MobileDevice> smartThings;
	private static List<ApDevice> apDevices;
	private static List<FogBroker> brokerList;


	private Map<String, Application> applications;
	private Map<String, Integer> appLaunchDelays;
	private ModuleMapping moduleMapping;
	private Map<Integer, Double> globalCurrentCpuLoad;

	static final int numOfDepts = 1;
	static final int numOfMobilesPerDept = 4;
	private static Random rand;
	public MobileController(){

	}
	public MobileController(String name, List<FogDevice> serverCloudlets, List<ApDevice> apDevices, List<MobileDevice> smartThings,List<FogBroker> brokers, ModuleMapping moduleMapping
			, int migPointPolicy, int migStrategyPolicy, int stepPolicy, Coordinate coordDevices, int seed, boolean migrationAble) {
		// TODO Auto-generated constructor stub
		super(name);
		this.applications = new HashMap<String, Application>();
		this.globalCurrentCpuLoad = new HashMap <Integer, Double>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setModuleMapping(moduleMapping);
		for(FogDevice sc : serverCloudlets){
			sc.setControllerId(getId());
		}
		setSeed(seed);
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setSmartThings(smartThings);
		setBrokerList(brokers);
		setMigPointPolicy(migPointPolicy);
		setMigStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		setRand(new Random(getSeed()*Long.MAX_VALUE));
		setMigrationAble(migrationAble);
	}
	public MobileController(String name, List<FogDevice> serverCloudlets,
			List<ApDevice> apDevices, List<MobileDevice> smartThings,
			int migPointPolicy, int migStrategyPolicy, int stepPolicy,
			Coordinate coordDevices, int seed) {
		// TODO Auto-generated constructor stub
		super(name);
		this.applications = new HashMap<String, Application>();
		this.globalCurrentCpuLoad = new HashMap <Integer, Double>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setModuleMapping(moduleMapping);
		for(FogDevice sc : serverCloudlets){
			sc.setControllerId(getId());
		}
		setSeed(seed);
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setSmartThings(smartThings);
		setMigPointPolicy(migPointPolicy);
		setMigStrategyPolicy(migStrategyPolicy);
		setStepPolicy(stepPolicy);
		setCoordDevices(coordDevices);
		connectWithLatencies();
		initializeCPULoads();
		setRand(new Random(getSeed()*Long.MAX_VALUE));

	}
	private void connectWithLatencies(){
		for(FogDevice st : getSmartThings()){
			FogDevice parent = getFogDeviceById(st.getParentId());
			if(parent == null){
				continue;
			}
			double latency = st.getUplinkLatency();
			parent.getChildToLatencyMap().put(st.getId(), latency);
			parent.getChildrenIds().add(st.getId());
		}
	}
	private FogDevice getFogDeviceById(int id){
		for(FogDevice sc : getServerCloudlets()){
			if(id==sc.getId())
				return sc;
		}
		return null;
	}

	private void initializeCPULoads() {
		//		Map<String, Map<String, Integer>> mapping = moduleMapping.getModuleMapping();
		//		for(String deviceName : mapping.keySet()){
		//			FogDevice device = getDeviceByName(deviceName);
		//			for(String moduleName : mapping.get(deviceName).keySet()){
		//
		//				AppModule module = getApplication().getModuleByName(moduleName);
		//				if(module == null)
		//					continue;
		//				getCurrentCpuLoad().put(device.getId(), getCurrentCpuLoad().get(device.getId()).doubleValue() + module.getMips());
		//			}
		//		}
		for(FogDevice sc : getServerCloudlets()){
			this.globalCurrentCpuLoad.put(sc.getId(), 0.0);
		}
		for(MobileDevice st : getSmartThings()){
			this.globalCurrentCpuLoad.put(st.getId(), 0.0);
		}
	}
	@Override
	public void startEntity() {
		// TODO Auto-generated method stub
		for(String appId : applications.keySet()){
			LogMobile.debug("MobileController.java",appId +" - "+getAppLaunchDelays().get(appId));
//			if(getAppLaunchDelays().get(appId)==0)
				processAppSubmit(applications.get(appId));
//			else{
//				System.out.println("MobileController 174 startEntity "+getAppLaunchDelays().get(appId));
//				send(getId(), getAppLaunchDelays().get(appId), FogEvents.APP_SUBMIT, applications.get(appId));
//			}
		}

		for(int i = 0; i<MaxAndMin.MAX_SIMULATION_TIME; i+=1000){
			send(getId()//Application
					, i //delay -> When the event will occur
					, MobileEvents.NEXT_STEP
					);//, getSmartThings());
			send(getId()
					,i
					,MobileEvents.CHECK_NEW_STEP);
		}

		if(isMigrationAble()){
			for(FogDevice sc: getServerCloudlets()){
				for(int i = 0; i<MaxAndMin.MAX_SIMULATION_TIME; i+=1000){
					send(sc.getId()//serverCloudlet
							, i //delay -> When the event will occur
							, MobileEvents.MAKE_DECISION_MIGRATION
							, sc.getSmartThings());
				}
			}
		}

		for (MobileDevice st : getSmartThings()){
			System.out.println(st.getStartTravelTime()*1000);
			send(getId(), st.getStartTravelTime()*1000, MobileEvents.CREATE_NEW_SMARTTHING, st);
			st.getSourceAp().desconnectApSmartThing(st);
			st.getSourceServerCloudlet().desconnectServerCloudletSmartThing(st);
			if(st.isLockedToMigration()||st.isMigStatus()){
				sendNow(st.getVmLocalServerCloudlet().getId(), MobileEvents.ABORT_MIGRATION,st);
			}
		}

		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);

		for(FogDevice dev : getServerCloudlets())
			sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

		send(getId(), MaxAndMin.MAX_SIMULATION_TIME, MobileEvents.STOP_SIMULATION);
	}
	private void processAppSubmit(SimEvent ev){
		Application app = (Application) ev.getData();
		processAppSubmit(app);
	}
	private void processAppSubmit(Application application){
		System.out.println("MobileController 213 processAppSubmit "+CloudSim.clock()+" Submitted application "+ application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		List<FogDevice> tempAllDevices = new ArrayList<>();
		for(FogDevice sc: getServerCloudlets()){
			tempAllDevices.add(sc);
		}

		for(MobileDevice st: getSmartThings()){
			tempAllDevices.add(st);
		}

		ModulePlacement modulePlacement = new ModulePlacementMapping(tempAllDevices//getServerCloudlets()
				, application, getModuleMapping(),globalCurrentCpuLoad);


		for(FogDevice fogDevice : getServerCloudlets()){
			sendNow(fogDevice.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		}
		for(MobileDevice st : getSmartThings()){
			sendNow(st.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		}

		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		Map<Integer, Map<String, Integer>> instanceCountMap = modulePlacement.getModuleInstanceCountMap();
		for(Integer deviceId : deviceToModuleMap.keySet()){
			for(AppModule module : deviceToModuleMap.get(deviceId)){
				System.out.println("MobileController 240 ProcessAppSubmit");
				sendNow(deviceId, FogEvents.APP_SUBMIT, application);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
				sendNow(deviceId, FogEvents.LAUNCH_MODULE_INSTANCE,
						new ModuleLaunchConfig(module, instanceCountMap.get(deviceId).get(module.getName())));
			}

		}
	}
	private void processAppSubmitMigration(SimEvent ev){
		Application application = (Application) ev.getData();
		System.out.println(CloudSim.clock()+" Submitted application after migration "+ application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		FogDevice sc = (FogDevice)CloudSim.getEntity(ev.getSource());
		List<FogDevice> tempList =new ArrayList<>();
		tempList.add(sc);
		ModulePlacement modulePlacement = new ModulePlacementMapping(tempList//getServerCloudlets()
				, application, getModuleMapping(),globalCurrentCpuLoad,true);

		//		for(FogDevice fogDevice : getServerCloudlets()){
		sendNow(sc.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
		//		}

		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		Map<Integer, Map<String, Integer>> instanceCountMap = modulePlacement.getModuleInstanceCountMap();
		//		for(Integer deviceId : deviceToModuleMap.keySet()){
		for(AppModule module : deviceToModuleMap.get(sc.getId())){
			System.out.println("MobileController 268 processAppSubmitMigration");
			sendNow(sc.getId(), FogEvents.APP_SUBMIT, application);
			sendNow(sc.getId(), FogEvents.LAUNCH_MODULE, module);
			sendNow(sc.getId(), FogEvents.LAUNCH_MODULE_INSTANCE,
					new ModuleLaunchConfig(module, instanceCountMap.get(sc.getId()).get(module.getName())));
		}

		//		}
	}

	private void processTupleFinished(SimEvent ev) {
	}
	protected void manageResources(){
		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
	}


	@Override
	public void processEvent(SimEvent ev) {
		// TODO Auto-generated method stub
		switch(ev.getTag()){
		case FogEvents.APP_SUBMIT:
			System.out.println("APP_SUBMIT");
			processAppSubmit(ev);
			break;
		case MobileEvents.APP_SUBMIT_MIGRATE:
			processAppSubmitMigration(ev);
			break;

		case FogEvents.TUPLE_FINISHED:
			System.out.println("TUPLE_FINISHED");
			processTupleFinished(ev);
			break;
		case FogEvents.CONTROLLER_RESOURCE_MANAGE:
			manageResources();
			break;
		case MobileEvents.NEXT_STEP:
			NextStep.nextStep(getServerCloudlets()
					, getApDevices()
					, getSmartThings()
					, getCoordDevices()
					, getStepPolicy()
					, getSeed());

			break;
		case MobileEvents.CREATE_NEW_SMARTTHING:
			createNewSmartThing(ev);
			break;
		case MobileEvents.CHECK_NEW_STEP:
			checkNewStep();
			System.out.println("SmartThingListSize: "+getSmartThings().size());
			if(getSmartThings().isEmpty())
				sendNow(getId(), MobileEvents.STOP_SIMULATION);
			break;
		case MobileEvents.STOP_SIMULATION:
			System.out.println("*********************myStopSimulation MobilieController 149 ***********");
			System.out.println("CloudSim.clock(): "+CloudSim.clock());
			System.out.println("Size SmartThings: "+getSmartThings().size());
			CloudSim.stopSimulation();
			printTimeDetails();
			printPowerDetails();
			printCostDetails();
			printNetworkUsageDetails();
			printMigrationsDetalis();
			System.exit(0);
			break;

		}
	}


	private void createNewSmartThing(SimEvent ev) {
		MobileDevice st = (MobileDevice) ev.getData();
		// TODO Auto-generated method stub

		System.out.println("criado...");
		st.setTravelTimeId(0);
//		if(ApDevice.connectApSmartThing(getApDevices(), st, getRand().nextDouble())){
//			st.getSourceAp().getServerCloudlet().connectServerCloudletSmartThing(st);
//			System.out.println("conectado... "+st.getSourceServerCloudlet().getName());
//		}

	}
	//	{antigo createNewsmartthing
	////
	////		int create=0;
	////		create = rand.nextInt(2);//2 -> 50%, 4 -> 25%, 5 -> 20%, 10 -> 10%, 20 -> 5%, 50 -> 2% and 100 -> 1%
	//
	////		if(create == 0){//(!smartThing.isStatus()){
	//			int i=AppExemplo2.getServerCloudlets().get(1).getSmartThings().size();
	//			//rand = new Random(i);
	//			short coordX,coordY;
	//			int direction, speed;
	//			direction = rand.nextInt(MaxAndMin.MAX_DIRECTION-1)+1;
	//			speed = rand.nextInt(MaxAndMin.MAX_SPEED);
	//			while(true){
	//				coordX = (short) rand.nextInt(MaxAndMin.MAX_X);
	//				coordY = (short) rand.nextInt(MaxAndMin.MAX_Y);
	//				if(AppExemplo2.getCoordDevices().getPositions(coordX, coordY)==null){//verify if it is empty
	//					smartThing.setDirection(direction);
	//					smartThing.setSpeed(speed);
	//					smartThing.setSourceServerCloudlet(null);
	//					smartThing.setDestinationServerCloudlet(null);
	//					smartThing.setVmLocalServerCloudlet(null);
	//					smartThing.setSourceAp(null);
	//					smartThing.setDestinationAp(null);
	//					smartThing.setVmMobileDevice(null);
	//					smartThing.setMigTime(0);
	//					smartThing.setMigStatus(false);
	//					smartThing.setHandoffStatus(false);
	//					smartThing.setStatus(true);
	//					smartThing.setCoord(coordX, coordY);
	//					AppExemplo2.getCoordDevices().setPositions(smartThing.getName()
	//							, smartThing.getCoord().getCoordX(), smartThing.getCoord().getCoordY());
	//					smartThing.setTempSimulation(0);
	//					smartThing.setTimeFinishDeliveryVm(0);
	//					smartThing.setTimeFinishHandoff(0);
	//
	//					if(ApDevice.connectApSmartThing(AppExemplo2.getApDevices(), smartThing)){
	//						FogDevice.connectServerCloudletSmartThing(smartThing.getSourceAp().getServerCloulet()
	//								, smartThing);
	//					}
	//
	//					break;
	//				}
	//			}
	////		}
	//	}
	private double migrationTimeToLiveMigration(MobileDevice smartThing) {
		// TODO Auto-generated method stub
		double runTime = CloudSim.clock()-smartThing.getTimeStartLiveMigration();
		if(smartThing.getMigTime()>runTime){
			runTime = smartThing.getMigTime()-runTime;
			return runTime;
		}
		else{
			return 0;
		}

	}

	private void checkNewStep() {
		// TODO Auto-generated method stub
		int index=0;
		//	Random rand = new Random((long) (getSeed()+CloudSim.clock()));
		//		Migration migration = new Migration();
		for(MobileDevice st: getSmartThings()){
			if(st.getTravelTimeId()==-1){
				continue;
			}
			MyStatistics.getInstance().getEnergyHistory().put(st.getMyId(), st.getEnergyConsumption());
			MyStatistics.getInstance().getPowerHistory().put(st.getMyId(),st.getHost().getPower());

			if(st.getSourceAp()!=null){
				System.out.println(st.getName()+"\t"+ st.getCoord().getCoordX()+"\t"+st.getCoord().getCoordY());
				System.out.println(st.getSourceAp().getName()+ "\t"+st.getSourceAp().getCoord().getCoordX()+"\t"+st.getSourceAp().getCoord().getCoordY());
				System.out.println(Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord()));
				if(!st.isLockedToHandoff()){//(!st.isHandoffStatus()){
					double distance=Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord());
					//					List<ApDevice> tempApList=new ArrayList<>();

					System.out.println("Distance "+distance+"Diff "+ (MaxAndMin.AP_COVERAGE-MaxAndMin.MAX_DISTANCE_TO_HANDOFF) + " max " + MaxAndMin.AP_COVERAGE);
					if(distance>=MaxAndMin.AP_COVERAGE-MaxAndMin.MAX_DISTANCE_TO_HANDOFF && distance<MaxAndMin.AP_COVERAGE){ //Handoff Zone
						index=Migration.nextAp(getApDevices(), st);
						if(index >= 0){//index isn't negative
							st.setDestinationAp(getApDevices().get(index));
							st.setHandoffStatus(true);
							st.setLockedToHandoff(true);

							double handoffTime = MaxAndMin.MIN_HANDOFF_TIME + (MaxAndMin.MAX_HANDOFF_TIME - MaxAndMin.MIN_HANDOFF_TIME) * getRand().nextDouble(); //"Maximo" tempo para handoff
//							float handoffLocked = (MaxAndMin.MAX_DISTANCE_TO_HANDOFF/(st.getSpeed()+1))*2000;
							float handoffLocked = (float) (handoffTime*4);
							int delayConnection = 100; //connection between SmartT and ServerCloudlet


							if(!st.getDestinationAp().getServerCloudlet().equals(st.getSourceServerCloudlet())){

								//send(st.getDestinationAp().getServerCloulet().getId(), handoffTime+delayConnection+10,MobileEvents.MAKE_DECISION_MIGRATION,st);
								if(isMigrationAble()){
									LogMobile.debug("MobileController.java", st.getName()+" will be desconnected from "+
												st.getSourceServerCloudlet().getName()+" by handoff");
									sendNow(st.getSourceServerCloudlet().getId(),MobileEvents.MAKE_DECISION_MIGRATION,st);
									sendNow(st.getSourceServerCloudlet().getId(),MobileEvents.DESCONNECT_ST_TO_SC,st);
									send(st.getDestinationAp().getServerCloudlet().getId(), handoffTime+delayConnection,MobileEvents.CONNECT_ST_TO_SC,st);
//									sendNow(st.getDestinationAp().getServerCloudlet().getId(),MobileEvents.MAKE_DECISION_MIGRATION,st);
								}
								if(st.isPostCopyStatus() && !st.isMigStatus()){
									if(!st.isMigStatusLive()){
										st.setMigStatusLive(true);
										double newMigTime = migrationTimeToLiveMigration(st);
										if(newMigTime==0){
											newMigTime=((st.getVmMobileDevice().getHost().getRamProvisioner().getUsedRam()*8*1024*1024)/st.getVmLocalServerCloudlet().getUplinkBandwidth())*1000.0;
										}
										double delayProcess = st.getVmLocalServerCloudlet().getCharacteristics().
												getCpuTime((st.getVmMobileDevice().getSize()*1024*1024*8)*0.7, 0.0)
												;//the connection already is opened
										st.setTimeFinishDeliveryVm(-1.0);
										System.out.println(CloudSim.clock()+ " startWithoutVmTime");
										MyStatistics.getInstance().startWithoutVmTime(st.getMyId(), CloudSim.clock());
										send(st.getVmLocalServerCloudlet().getId(),newMigTime+delayProcess,MobileEvents.SET_MIG_STATUS_TRUE, st);
									}
								}
							}



							send(st.getSourceAp().getId(),handoffTime,MobileEvents.START_HANDOFF,st);
							send(st.getDestinationAp().getId(),handoffLocked,MobileEvents.UNLOCKED_HANDOFF,st);
							MyStatistics.getInstance().setTotalHandoff(1);

							saveHandOff(st);

							LogMobile.debug("MobileController.java", st.getName()+" handoff was scheduled! "+"SourceAp: "+st.getSourceAp().getName()
									+" NextAp: "+st.getDestinationAp().getName()+"\n");
							LogMobile.debug("MobileController.java", "Distance between "+ st.getName()+" and "+st.getSourceAp().getName()+": "+
									Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord()));
						}
						else{
							LogMobile.debug("MobileController.java", st.getName()+" can't make handoff because don't exist closest nextAp");
						}
					}
					else if(distance>=MaxAndMin.AP_COVERAGE) {
						st.getSourceAp().desconnectApSmartThing(st);
						st.getSourceServerCloudlet().desconnectServerCloudletSmartThing(st);
						if(st.isLockedToMigration()||st.isMigStatus()){
							sendNow(st.getVmLocalServerCloudlet().getId(), MobileEvents.ABORT_MIGRATION,st);
						}
						LogMobile.debug("MobileController.java", st.getName()+" desconnected by AP_COVERAGE - Distance: "+distance);
						LogMobile.debug("MobileController.java", st.getName()+" X: "+st.getCoord().getCoordX()+ " Y: "+st.getCoord().getCoordY());
					}
				}
			}
			else{
				if(ApDevice.connectApSmartThing(getApDevices(), st, getRand().nextDouble())){
					st.getSourceAp().getServerCloudlet().connectServerCloudletSmartThing(st);
					LogMobile.debug("MobileController.java", st.getName() +" has a new connection - SourceAp: "+st.getSourceAp().getName()+
							" SourceServerCouldlet: "+st.getSourceServerCloudlet().getName());

					CloudletScheduler cloudletScheduler = new CloudletSchedulerTimeShared();

					long sizeVm = (MaxAndMin.MIN_VM_SIZE + (long)((MaxAndMin.MAX_VM_SIZE - MaxAndMin.MIN_VM_SIZE) * (getRand().nextDouble())));
					AppModule vmSmartThing = new AppModule(st.getMyId(), "AppModuleVm_"+st.getName()
							, "MyApp_vr_game"+st.getMyId()
							, getBrokerList().get(st.getMyId()).getId()
							, 2000
							, 64
							, 1000
							, sizeVm
							, "Vm_"+st.getName()
							, cloudletScheduler
							, new HashMap<Pair<String, String>, SelectivityModel>());
					System.out.println("before: "+st.getVmLocalServerCloudlet().getName());
					st.setVmMobileDevice(vmSmartThing);
					st.getSourceServerCloudlet().getHost().vmCreate(vmSmartThing);
					st.setVmLocalServerCloudlet(st.getSourceServerCloudlet());
					st.setLockedToMigration(false);
					System.out.println("after: " +st.getVmLocalServerCloudlet().getName());

					System.out.println(st.getName()+"\t"+ st.getCoord().getCoordX()+"\t"+st.getCoord().getCoordY());
					System.out.println(st.getSourceAp().getName()+ "\t"+st.getSourceAp().getCoord().getCoordX()+"\t"+st.getSourceAp().getCoord().getCoordY());
					System.out.println(Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord()));

					//					System.out.println("Vm allocated to "+st.getName());
					int brokerId=getBrokerList().get(st.getMyId()).getId();
					for(MobileSensor s: st.getSensors()){
						s.setAppId("MyApp_vr_game"+st.getMyId());
						s.setUserId(brokerId);
						s.setGatewayDeviceId(st.getId());
						s.setLatency(6.0);

					}
					for(MobileActuator a: st.getActuators()){
						a.setUserId(brokerId);
						a.setAppId("MyApp_vr_game"+st.getMyId());
						a.setGatewayDeviceId(st.getId());
						a.setLatency(1.0);
						a.setActuatorType("DISPLAY"+st.getMyId());

					}
					ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
					moduleMapping.addModuleToDevice(((AppModule)st.getVmMobileDevice()).getName(), st.getSourceServerCloudlet().getName(), 1);//numOfDepts*numOfMobilesPerDept);
					moduleMapping.addModuleToDevice("client"+st.getMyId(), st.getName(), 1);
					//					moduleMapping.addModuleToDevice("connector"+st.getMyId(), st.getSourceServerCloudlet().getName() ,1);// MaxAndMin.MAX_SMART_THING); // fixing all instances of the Connector module to cloudlets
					//					moduleMapping.addModuleToDevice("concentration_calculator"+st.getMyId(), st.getSourceServerCloudlet().getName(), 1);//MaxAndMin.MAX_SMART_THING);
					processAppSubmit(getApplications().get("MyApp_vr_game"+st.getMyId()));
				}
				else{
					//To do something
				}
			}
		}
	}

	private static void saveHandOff(MobileDevice st){
		System.out.println("HANDOFF "+st.getMyId() + " Position: " + st.getCoord().getCoordX() + ", " + st.getCoord().getCoordY() + " Direction: " + st.getDirection() + " Speed: " + st.getSpeed());
		try(FileWriter fw = new FileWriter(st.getMyId()+"handoff.txt", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
		{
			out.println(st.getMyId() + "\t" + CloudSim.clock() + "\t" + st.getCoord().getCoordX() + "\t" + st.getCoord().getCoordY() + "\t" + st.getDirection() + "\t" + st.getSpeed() + "\t" + st.getSourceAp()+ "\t" + st.getDestinationAp());
		}
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	@Override
	public void shutdownEntity() {
		// TODO Auto-generated method stub

	}

	private void printCostDetails(){
		//System.out.println("Cost of execution in cloud = "+getCloud().getTotalCost());
	}
	private FogDevice getCloud(){
		for(FogDevice dev : getServerCloudlets())
			if(dev.getName().equals("cloud"))
				return dev;
		return null;
	}
	
	public void printResults(String a, String filename){
		try(FileWriter fw1 = new FileWriter(filename, true);
			    BufferedWriter bw1 = new BufferedWriter(fw1);
			    PrintWriter out1 = new PrintWriter(bw1))
		{
			out1.println(a);
		}
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private void printPowerDetails() {
		// TODO Auto-generated method stub
		double energyConsumedMean = 0.0;
		int j=0;
		System.out.println("=========================================");
		System.out.println("CLOUDLETS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for(FogDevice fogDevice : getServerCloudlets()){
			if(fogDevice.getEnergyConsumption() != 5.8736831999993116E7){
				System.out.println(fogDevice.getName()+ ": Power = "+fogDevice.getHost().getPower());
				System.out.println(fogDevice.getName() + ": Energy Consumed = "+fogDevice.getEnergyConsumption());
				energyConsumedMean+=fogDevice.getEnergyConsumption();
				j++;
			}
		}
		System.out.println("Total consumido Coudlets: " + energyConsumedMean+" Media: "+energyConsumedMean/j);
		printResults(String.valueOf(energyConsumedMean/j), "averageEnergyHistoryDevice.txt");
		printResults(String.valueOf(energyConsumedMean) + "\t" + String.valueOf(energyConsumedMean/j),"resultados.txt");
		energyConsumedMean = 0.0;
		System.out.println("=========================================");
		System.out.println("AP DEVICES ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for(FogDevice apDevice : getApDevices()){
//			System.out.println(apDevice.getName()+ ": Power = "+apDevice.getHost().getPower());
			System.out.println(apDevice.getName() + ": Energy Consumed = "+apDevice.getEnergyConsumption());
			energyConsumedMean+=apDevice.getEnergyConsumption();
			j++;
		}
		System.out.println("Total consumido AP: " + energyConsumedMean+" Media: "+energyConsumedMean/j);
		energyConsumedMean = 0.0;
		System.out.println("=========================================");
		System.out.println("SMARTTHINGS ENERGY CONSUMPTION");
		System.out.println("=========================================");
		for(FogDevice mobileDevice : getSmartThings()){
			System.out.println(mobileDevice.getName()+ ": Power = "+mobileDevice.getHost().getPower());
			System.out.println(mobileDevice.getName() + ": Energy Consumed = "+mobileDevice.getEnergyConsumption());
		}
		for(int i=0;i<MyStatistics.getInstance().getPowerHistory().size();i++){
			System.out.println("SmartThing"+i+": Power = "+MyStatistics.getInstance().getPowerHistory().get(i));
		}
		for(int i=0;i<MyStatistics.getInstance().getEnergyHistory().size();i++){
			System.out.println("SmartThing"+i+": Energy Consumed = "+MyStatistics.getInstance().getEnergyHistory().get(i));
			printResults(String.valueOf(MyStatistics.getInstance().getEnergyHistory().get(i)), "resultados.txt");
			energyConsumedMean += MyStatistics.getInstance().getEnergyHistory().get(i);
		}
	}
	private String getStringForLoopId(int loopId){
		for(String appId : getApplications().keySet()){
			Application app = getApplications().get(appId);
			for(AppLoop loop : app.getLoops()){
				if(loop.getLoopId() == loopId)
					return loop.getModules().toString();
			}
		}
		return null;
	}
	private void printTimeDetails() {

		System.out.println("=========================================");
		System.out.println("============== RESULTS ==================");
		System.out.println("=========================================");
		System.out.println("EXECUTION TIME : "+ (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
		System.out.println("=========================================");
		System.out.println("APPLICATION LOOP DELAYS");
		System.out.println("=========================================");
		double mediaLatencia = 0.0;
		double mediaLatenciaMax = 0.0;
		for(Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()){
			//			double average = 0, count = 0;
			//			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
			//				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
			//				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
			//				if(startTime == null || endTime == null)
			//					break;
			//				average += endTime-startTime;
			//				count += 1;
			//			}
			//			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));
			System.out.println(getStringForLoopId(loopId) + " ---> "+TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId)+" MaxExecutionTime: "+TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId));
			printResults(String.valueOf(TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId)), "resultados.txt");
			printResults(String.valueOf(TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId)), "resultados.txt");
			mediaLatencia += TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId);
			mediaLatenciaMax += TimeKeeper.getInstance().getMaxLoopExecutionTime().get(loopId);
		}
		printResults(String.valueOf(mediaLatencia/TimeKeeper.getInstance().getLoopIdToCurrentAverage().keySet().size()), "averageLoopIdToCurrentAverage.txt");
		printResults(String.valueOf(mediaLatenciaMax/TimeKeeper.getInstance().getMaxLoopExecutionTime().keySet().size()), "averageMaxLoopExecutionTime.txt");
		System.out.println("=========================================");
		System.out.println("TUPLE CPU EXECUTION DELAY");
		System.out.println("=========================================");

		for(String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()){
			System.out.println(tupleType + " ---> "+TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
		}

		System.out.println("=========================================");
	}
	private void printNetworkUsageDetails() {
		System.out.println("=========================================");
		System.out.println("=============NETWORK USAGE===============");
		System.out.println("=========================================");
		double deviceNetworkUsage = NetworkUsageMonitor.getNetworkUsage()-NetworkUsageMonitor.getNetWorkUsageInMigration();
		System.out.println("VM data transferred in migration = "+NetworkUsageMonitor.getVMTransferredData());
		printResults(String.valueOf(NetworkUsageMonitor.getVMTransferredData()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getVMTransferredData()) + '\t'+ CloudSim.clock(), "resultados.txt");
		printResults(String.valueOf(NetworkUsageMonitor.getVMTransferredData()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getVMTransferredData()) + '\t'+ CloudSim.clock(), "vmsizesended.txt");
		System.out.println("Device's network usage = "+deviceNetworkUsage);
		printResults(String.valueOf(deviceNetworkUsage/CloudSim.clock())+ '\t' + String.valueOf(deviceNetworkUsage) + '\t'+ CloudSim.clock(), "resultados.txt");
		printResults(String.valueOf(deviceNetworkUsage/CloudSim.clock())+ '\t' + String.valueOf(deviceNetworkUsage) + '\t'+ CloudSim.clock(), "deviceNetworkUsage.txt");
		System.out.println("Migration' network usage (total)= "+NetworkUsageMonitor.getNetWorkUsageInMigration());
		System.out.println("Migration' network usage (mean)= "+NetworkUsageMonitor.getNetWorkUsageInMigration()/MyStatistics.getInstance().getTotalMigrations());
		printResults(String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()) + '\t'+ CloudSim.clock(), "resultados.txt");
		printResults(String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getNetWorkUsageInMigration()) + '\t'+ CloudSim.clock(), "cloudletNetworkUsage.txt");
		System.out.println("Total network usage = "+NetworkUsageMonitor.getNetworkUsage());
		printResults(String.valueOf(NetworkUsageMonitor.getNetworkUsage()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getNetworkUsage()) + '\t'+ CloudSim.clock(), "resultados.txt");
		printResults(String.valueOf(NetworkUsageMonitor.getNetworkUsage()/CloudSim.clock())+ '\t' + String.valueOf(NetworkUsageMonitor.getNetworkUsage()) + '\t'+ CloudSim.clock(), "totalNetworkUsage.txt");
	}
	private void printMigrationsDetalis(){
		System.out.println("=========================================");
		System.out.println("==============MIGRATIONS=================");
		System.out.println("=========================================");
		System.out.println("Total of migrations: "+MyStatistics.getInstance().getTotalMigrations());
		System.out.println("Total of handoff: "+MyStatistics.getInstance().getTotalHandoff());
		System.out.println("Total of migration to differents SC: " + MyStatistics.getInstance().getMyCountLowestLatency());
		
		printResults(String.valueOf(MyStatistics.getInstance().getTotalMigrations()), "resultados.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getTotalHandoff()), "resultados.txt");

		printResults(String.valueOf(MyStatistics.getInstance().getTotalMigrations()), "totalMigrations.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getMyCountLowestLatency()), "totalMyCountLowestLatency.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getTotalHandoff()), "totalHandoff.txt");
		
		MyStatistics.getInstance().printResults();
		System.out.println("***Last time without connection***");

		for(Entry<Integer, Double> test :MyStatistics.getInstance().getWithoutConnectionTime().entrySet()){
			System.out.println("SmartThing"+test.getKey()+": "+MyStatistics.getInstance().getWithoutConnectionTime().get(test.getKey())+" - Max: "+MyStatistics.getInstance().getMaxWithoutConnectionTime().get(test.getKey()));
		}

		System.out.println("Average of without connection: "+MyStatistics.getInstance().getAverageWithoutConnection());
		
		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutConnection()), "resultados.txt");

		System.out.println("***Last time without Vm***");

		for(Entry<Integer, Double> test :MyStatistics.getInstance().getWithoutVmTime().entrySet()){
			System.out.println("SmartThing"+test.getKey()+": "+MyStatistics.getInstance().getWithoutVmTime().get(test.getKey())+" - Max: "+MyStatistics.getInstance().getMaxWithoutVmTime().get(test.getKey()));
		}

		System.out.println("Average of without Vm: "+MyStatistics.getInstance().getAverageWithoutVmTime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutVmTime()), "resultados.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageWithoutVmTime()), "averageWithoutVmTime.txt");

		System.out.println("===Last delay after connection===");
		for(Entry<Integer, Double> test : MyStatistics.getInstance().getDelayAfterNewConnection().entrySet()){
			System.out.println("SmartThing"+test.getKey()+": "+MyStatistics.getInstance().getDelayAfterNewConnection().get(test.getKey())+" - Max: "+MyStatistics.getInstance().getMaxDelayAfterNewConnection().get(test.getKey()));
		}
		System.out.println("Average of delay after new Connection: "+MyStatistics.getInstance().getAverageDelayAfterNewConnection());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDelayAfterNewConnection()), "resultados.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDelayAfterNewConnection()), "averageDelayAfterNewConnection.txt");

		System.out.println("---Average of Time of Migrations---");
		double tempoMigracaoMax = 0.0;
		for(Entry<Integer, Double> test : MyStatistics.getInstance().getMigrationTime().entrySet()){
			System.out.println("SmartThing"+test.getKey()+": "+MyStatistics.getInstance().getMigrationTime().get(test.getKey())+" - Max: "+MyStatistics.getInstance().getMaxMigrationTime().get(test.getKey()));
			tempoMigracaoMax = Math.max(tempoMigracaoMax, MyStatistics.getInstance().getMaxMigrationTime().get(test.getKey()));
		}
		System.out.println("Average of Time of Migrations: "+MyStatistics.getInstance().getAverageMigrationTime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageMigrationTime()), "resultados.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageMigrationTime()), "averageMigrationTime.txt");
		System.out.println("Hightest Time of Migrations: "+ tempoMigracaoMax);
		printResults(String.valueOf(tempoMigracaoMax), "averageMigrationMaxTime.txt");
		System.out.println("---Average of Downtime---");
		double tempoDowntimeMax = 0.0;
		for(Entry<Integer, Double> test : MyStatistics.getInstance().getDowntime().entrySet()){
			System.out.println("SmartThing"+test.getKey()+": "+MyStatistics.getInstance().getDowntime().get(test.getKey())+" - Max: "+MyStatistics.getInstance().getMaxDowntime().get(test.getKey()));
			tempoDowntimeMax += MyStatistics.getInstance().getMaxDowntime().get(test.getKey());
		}
		System.out.println("Average of Downtime: "+MyStatistics.getInstance().getAverageDowntime());
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDowntime()), "resultados.txt");
		printResults(String.valueOf(MyStatistics.getInstance().getAverageDowntime()), "averageDowntime.txt");
		System.out.println("Max Downtime: "+ tempoDowntimeMax);
		printResults(String.valueOf(tempoDowntimeMax), "averageDowntimeMax.txt");
		System.out.println("Tuple lost: "+(((double)MyStatistics.getInstance().getMyCountLostTuple()/MyStatistics.getInstance().getMyCountTotalTuple()))*100+"%");
		System.out.println("Tuple lost: "+MyStatistics.getInstance().getMyCountLostTuple());
		System.out.println("Total tuple: "+ MyStatistics.getInstance().getMyCountTotalTuple());

	}

	public void submitApplication(Application application, int delay){
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		getAppLaunchDelays().put(application.getAppId(), delay);
		for(MobileDevice st: getSmartThings()){
			for(Sensor s : st.getSensors()){
				if(s.getAppId().equals(application.getAppId()))
					s.setApp(application);
			}
			for(Actuator a : st.getActuators()){
				if(a.getAppId().equals(application.getAppId()))
					a.setApp(application);
			}
		}
		for(AppEdge edge : application.getEdges()){
			if(edge.getEdgeType() == AppEdge.ACTUATOR){
				String moduleName = edge.getSource();
				for(MobileDevice st: getSmartThings()){
					for(Actuator actuator : st.getActuators()){
						if(actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
							application.getModuleByName(moduleName).subscribeActuator(actuator.getId(), edge.getTupleType());
					}
				}
			}
		}

	}
	public void submitApplicationMigration(MobileDevice smartThing, Application application, int delay){
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		getAppLaunchDelays().put(application.getAppId(), delay);

		//			for(Sensor s : smartThing.getSensors()){
		////				if(s.getAppId().equals(application.getAppId()))
		//					s.setApp(application);
		//			}
		//			for(Actuator a : smartThing.getActuators()){
		////				if(a.getAppId().equals(application.getAppId()))
		//					a.setApp(application);
		//			}
		//
		for(AppEdge edge : application.getEdges()){
			if(edge.getEdgeType() == AppEdge.ACTUATOR){
				String moduleName = edge.getSource();
				for(MobileDevice st: getSmartThings()){
					for(Actuator actuator : st.getActuators()){
						if(actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
							application.getModuleByName(moduleName).subscribeActuator(actuator.getId(), edge.getTupleType());
					}
				}
			}
		}

	}

	public Map<String, Application> getApplications() {
		return applications;
	}

	public void setApplications(Map<String, Application> applications) {
		this.applications = applications;
	}

	public Map<String, Integer> getAppLaunchDelays() {
		return appLaunchDelays;
	}

	public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
		this.appLaunchDelays = appLaunchDelays;
	}

	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}

	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	public Map<Integer, Double> getGlobalCurrentCpuLoad() {
		return globalCurrentCpuLoad;
	}

	public void setGlobalCurrentCpuLoad(Map<Integer, Double> globalCurrentCpuLoad) {
		this.globalCurrentCpuLoad = globalCurrentCpuLoad;
	}
	public void setGlobalCPULoad(Map<Integer, Double> currentCpuLoad) {
		for(FogDevice device : getServerCloudlets()){
			this.globalCurrentCpuLoad.put(device.getId(), currentCpuLoad.get(device.getId()));
		}
	}

	public static int getMigPointPolicy() {
		return migPointPolicy;
	}
	public static void setMigPointPolicy(int migPointPolicy) {
		MobileController.migPointPolicy = migPointPolicy;
	}
	public static int getMigStrategyPolicy() {
		return migStrategyPolicy;
	}
	public static void setMigStrategyPolicy(int migStrategyPolicy) {
		MobileController.migStrategyPolicy = migStrategyPolicy;
	}
	public static int getStepPolicy() {
		return stepPolicy;
	}
	public static void setStepPolicy(int stepPolicy) {
		MobileController.stepPolicy = stepPolicy;
	}
	public static Coordinate getCoordDevices() {
		return coordDevices;
	}
	public static void setCoordDevices(Coordinate coordDevices) {
		MobileController.coordDevices = coordDevices;
	}
	public List<FogBroker> getBrokerList() {
		return brokerList;
	}
	public void setBrokerList(List<FogBroker> brokerList) {
		this.brokerList = brokerList;
	}
	public static int getSeed() {
		return seed;
	}
	public static void setSeed(int seed) {
		MobileController.seed = seed;
	}
	public static List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}
	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
		MobileController.serverCloudlets = serverCloudlets;
	}
	public static List<MobileDevice> getSmartThings() {
		return smartThings;
	}
	public static void setSmartThings(List<MobileDevice> smartThings) {
		MobileController.smartThings = smartThings;
	}
	public static List<ApDevice> getApDevices() {
		return apDevices;
	}
	public static void setApDevices(List<ApDevice> apDevices) {
		MobileController.apDevices = apDevices;
	}
	public static Random getRand() {
		return rand;
	}
	public static void setRand(Random rand) {
		MobileController.rand = rand;
	}
	public static boolean isMigrationAble() {
		return migrationAble;
	}
	public static void setMigrationAble(boolean migrationAble) {
		MobileController.migrationAble = migrationAble;
	}

}
