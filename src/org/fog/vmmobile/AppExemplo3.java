package org.fog.vmmobile;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.ApDevice;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MobileActuator;
import org.fog.entities.MobileDevice;
import org.fog.entities.MobileSensor;
import org.fog.localization.Coordinate;
import org.fog.localization.Distances;
import org.fog.placement.MobileController;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LowestDistBwSmartThingAP;
import org.fog.vmmigration.LowestDistBwSmartThingServerCloudlet;
import org.fog.vmmigration.LowestLatency;
import org.fog.vmmigration.Service;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;

public class AppExemplo3 {
//
//	//Application with the CloudSim.Cloudlet and CloudSim.Broker -> Does it work?
//	// TODO Auto-generated constructor stub
//	private static int stepPolicy; //Quantity of steps in the nextStep Function
//	private static List<MobileDevice> smartThings = new ArrayList<MobileDevice>();
//	private static List<FogDevice> serverCloudlets = new ArrayList<>();
//	private static List<ApDevice> apDevices = new ArrayList<>();
//	private static int migPointPolicy;
//	private static int migStrategyPolicy;
//	private static int positionApPolicy;
//	private static int policyReplicaVM;
//	private static int seed;
//	private static Coordinate coordDevices;//=new Coordinate(MaxAndMin.MAX_X, MaxAndMin.MAX_Y);//Grid/Map 
//	private static List<Cloudlet> cloudletList;//this is an Application
//	static final boolean CLOUD = true;
//
//	static final int numOfDepts = 1;
//	static final int numOfMobilesPerDept = 4;
//	static final double EEG_TRANSMISSION_TIME = 5.1;
//
//
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//
//		// First step: Initialize the CloudSim package. It should be called before creating any entities.
//		// Second step: Create all devices
//		// Third step: Create Broker
//		// Fourth step: Create one virtual machine
//		// Fifth step: Create one Cloudlet
//		// Sixth step: configure network
//		// Seventh step: Starts the simulation
//		// Final step: Print results when simulation is over
//
//		
//		
//		// TODO Auto-generated method stub
//		/**********It's necessary to CloudSim.java for working correctly**********/
//		//		Log.disable();
//		int numUser = 2000; // number of cloud users
//		Calendar calendar = Calendar.getInstance();
//		boolean traceFlag = false; // mean trace events
//		CloudSim.init(numUser, calendar, traceFlag);
//		/**************************************************************************/
//		setSeed(1);
//		
//		//		setMigPointPolicy(Policies.FIXED_MIGRATION_POINT);
//		setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);
//
//		setPolicyReplicaVM(Policies.MIGRATION_COMPLETE_VM);
//		//	setPolicyReplicaVM(Policies.MIGRATION_CONTAINER_VM);
//
//
//		//		setMigStrategyPolicy(Policies.LOWEST_LATENCY);
//		setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_AP);
//
//		//		setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET);
//
//		setPositionApPolicy(Policies.FIXED_AP_LOCATION);
//		//		setPositionApPolicy(Policies.RANDOM_AP_LOCATION);
//
//		setStepPolicy(1);
//
//
//		/*It is creating Access Points. It makes according positinApPolicy*/
//		if(positionApPolicy==Policies.FIXED_AP_LOCATION){
//			addApDevicesFixed(apDevices,coordDevices);//it creates the Access Point according coordDevices' size
//		}
//		else{
//			for(int i=0; i< MaxAndMin.MAX_AP_DEVICE;i++){//it creates the Access Points - initial parameter?
//				addApDevicesRandon(apDevices, coordDevices,i);
//			}
//		}
//
//		/*It is creating Smart Things.*/
//		for(int i=0; i< MaxAndMin.MAX_SMART_THING;i++){//it creates the SmartThings - initial parameter? -> in runtime, schedule events to add or remove items
//			addSmartThing(smartThings,coordDevices, i);
//		}
//
//		/*It is creating Server Cloudlets.*/
//		for (int i=0;i<MaxAndMin.MAX_SERVER_CLOUDLET;i++){ //it creates the ServerCloudlets - initial parameter? in runtime, schedule status to false or true
//			addServerCloudlet(serverCloudlets, coordDevices, i);
//		}
//
//		List<FogBroker> brokerList = new ArrayList<>();
//
//		for(MobileDevice st: getSmartThings()){
//			//appIdList.add("MyApp_vr_game"+Integer.toString(st.getMyId()));
//			try {
//				brokerList.add(new FogBroker("My_broker"+Integer.toString(st.getMyId())));
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}			
//		}
//		System.out.println("Criei os Brokers...");
//		int index=-1;
//		int countBroker=0;
//		int myCount=0;
//		for(MobileDevice st:smartThings){//it makes the connection between SmartThing and the closest AccessPoint
//			if(!ApDevice.connectApSmartThing(apDevices,st, 0.0)){
//				myCount++;
//			}
//		}
//		System.out.println("total no connection: "+myCount);
//		for(ApDevice ap: getApDevices()){ 
//			index = Distances.theClosestServerCloudletToAp(serverCloudlets,ap);
//			ap.setServerCloulet(serverCloudlets.get(index));
//			serverCloudlets.get(index).setApDevices(ap,Policies.ADD);
////			NetworkTopology.addLink(getServerCloudlets().get(index).getId(), ap.getId(), ap.getDownlinkBandwidth(), 0.02);
//			for(MobileDevice st : ap.getSmartThings()){
//				serverCloudlets.get(index).connectServerCloudletSmartThing(st);
//				CloudletScheduler cloudletScheduler = new CloudletSchedulerTimeShared();
//				Vm vmSmartThing = new Vm(st.getMyId()//id
//						, st.getMyId()// brokerList.get(countBroker++).getId() //   //userId -> id of CloudSim
//						, 1000//mips
//						, 1//numberOfPes
//						, 512//ram
//						, 100//bw
//						, MaxAndMin.MAX_VM_SIZE//size
//						, "Vm_"+st.getName()//vmm - I think this is the Vm name
//						, cloudletScheduler);
//				if(st.getSourceServerCloudlet().getHost().vmCreate(vmSmartThing)){
//					//To do anything
//					System.out.println("Criado");
//				}
//				else{
//					//To do anything
//					System.out.println("nao criado...");
//				}
//
//
//			}	
//		}
//		System.out.println("Conectei os devices e criei as vms");
//		setCloudletList(new ArrayList<Cloudlet>());
//
//		//Cloudlet properties
//		int id = 0;
//		long length = 40000;
//		long fileSize = 300;
//		long outputSize = 300;
//		int pesNumber = 1; //number of cpus
//		UtilizationModel utilizationModel = new UtilizationModelFull();
//		Cloudlet newCloudlet;
//		countBroker=0;
//		for(MobileDevice st: getSmartThings()){
//			newCloudlet = new Cloudlet(id++, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
//			newCloudlet.setUserId(brokerList.get(countBroker).getId());
//			//add the cloudlet to the list
//			cloudletList.add(newCloudlet);
//		}
//
//		for(FogBroker fb: brokerList){
//			//submit cloudlet list to the broker
//			fb.submitCloudletList(cloudletList);
//		}
//		System.out.println("Criei as Cloudlets... Applications");
//		createServerCloudletsNetwork(serverCloudlets);
//		System.out.println("Conectei os ServerCloudlets....");
////		for(FogDevice sc: getServerCloudlets()){
////			for(FogBroker fb: brokerList){
////		MUITO LENTO...-> NetworkTopology.addLink(sc.getId(),fb.getId(),10.0,10);
////		VERIFICAR EM COMO GERAR UM ARQUIVO BRITE
////			}
////		}
//		System.out.println("Conectei os brokers com as cloudlets...");
//		 
//		MobileController test = new MobileController("MobileController",getServerCloudlets(),getApDevices(),getSmartThings()
//				,getMigPointPolicy(),getMigStrategyPolicy(),getStepPolicy()
//				,getCoordDevices(),getSeed());
//		CloudSim.startSimulation();
//
//		// Final step: Print results when simulation is over
//		List<Cloudlet> newList = brokerList.get(0).getCloudletReceivedList();
//
//		CloudSim.stopSimulation();
//
//		printCloudletList(newList);
//
//		Log.printLine("NetworkExample4 finished!");
//		
//		System.out.println("Ok");	
//
//	}
//	private static void printCloudletList(List<Cloudlet> list) {
//		int size = list.size();
//		Cloudlet cloudlet;
//
//		String indent = "    ";
//		Log.printLine();
//		Log.printLine("========== OUTPUT ==========");
//		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent +
//				"Data center ID" + indent + "VM ID" + indent + "Time" + indent + "Start Time" + indent + "Finish Time");
//
//		for (int i = 0; i < size; i++) {
//			cloudlet = list.get(i);
//			Log.print(indent + cloudlet.getCloudletId() + indent + indent);
//
//			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS){
//				Log.print("SUCCESS");
//
//				DecimalFormat dft = new DecimalFormat("###.##");
//				Log.printLine( indent + indent + cloudlet.getResourceId() + indent + indent + indent + cloudlet.getVmId() +
//						indent + indent + dft.format(cloudlet.getActualCPUTime()) + indent + indent + dft.format(cloudlet.getExecStartTime())+
//						indent + indent + dft.format(cloudlet.getFinishTime()));
//			}
//		}
//
//	}
//
//
//	private static void addApDevicesFixed(List<ApDevice> apDevices,
//			Coordinate coordDevices) {
//		int i=0;
//		for(short coordX=400; coordX<MaxAndMin.MAX_X-400; coordX+=800) /*evenly distributed*/
//			for(short coordY=400; coordY<MaxAndMin.MAX_Y-400; coordY+=800, i++)
//				if(coordDevices.getPositions(coordX, coordY)==-1){
//					//ApDevice ap = new ApDevice("AccessPoint"+Integer.toString(i),coordX,coordY,i);//my construction
//					ApDevice ap = new ApDevice("AccessPoint"+Integer.toString(i),//name
//							coordX,coordY
//							,i//ap.set//id
//							,100//downLinkBandwidth
//							,200//engergyConsuption
//							,MaxAndMin.MAX_ST_IN_AP//maxSmartThing
//							,50//upLinkBandwidth
//							,i//upLinkLatency
//							);//ver valores reais e melhores
//					apDevices.add(i, ap);
//					coordDevices.setPositions(ap.getId(), ap.getCoord().getCoordX(), ap.getCoord().getCoordY());
//
//				}
//
//		System.out.println("Total of accessPoints: "+i);
//	}
//	private static void addApDevicesRandon(List<ApDevice> apDevices,
//			Coordinate coordDevices, int i) {
//		Random rand = new Random((i*i+200)/3);
//		short coordX,coordY;
//		while(true){
//			coordX = (short) rand.nextInt(MaxAndMin.MAX_X);
//			coordY = (short) rand.nextInt(MaxAndMin.MAX_Y);
//			if(coordDevices.getPositions(coordX, coordY)==-1){//verify if it is empty
//				//ApDevice ap = new ApDevice("AccessPoint"+Integer.toString(i),coordX,coordY,i);//my construction
//				ApDevice ap = new ApDevice("AccessPoint"+Integer.toString(i),//name
//						coordX,coordY
//						,i//id
//						,100//downLinkBandwidth
//						,200//engergyConsuption
//						,MaxAndMin.MAX_ST_IN_AP//maxSmartThing
//						,50//upLinkBandwidth
//						,i//upLinkLatency
//						);//ver valores reais e melhores
//				apDevices.add(i, ap);
//				coordDevices.setPositions(ap.getId(), ap.getCoord().getCoordX(), ap.getCoord().getCoordY());
//
//				//	System.out.println("i: "+i);
//				break;
//			}
//			else {
//				System.out.println("POSITION ISN'T AVAILABLE... (AP) X ="+ coordX+ " Y = " +coordY+" Reallocating..." );			
//			}
//		}
//	}
//
//	public static void addSmartThing(List<MobileDevice> smartThing, Coordinate coordDevices, int i){
//
//		Random rand = new Random(i);
//		int coordX,coordY;
//		int direction, speed;
//		direction = rand.nextInt(MaxAndMin.MAX_DIRECTION-1)+1;
//		speed = rand.nextInt(MaxAndMin.MAX_SPEED);
//		/***************Start set of Mobile Sensors****************/
//
//		DeterministicDistribution distribution0 = new DeterministicDistribution(EEG_TRANSMISSION_TIME);
//
//		Set<MobileSensor> sensors = new HashSet<>();
//
//		MobileSensor sensor0 = new MobileSensor("Sensor0" //Tuple's name -> ACHO QUE DÁ PARA USAR ESTE CONSTRUTOR 
//				,"EEG" //Tuple's type
//				,i //User Id
//				,"appId0" //app's name
//				,distribution0 );
//		sensors.add(sensor0);
//
//		MobileSensor sensor1 = new MobileSensor("Sensor1" //Tuple's name -> ACHO QUE DÁ PARA USAR ESTE CONSTRUTOR 
//				,"EEG" //Tuple's type
//				,i //User Id
//				,"appId1" //app's name
//				,distribution0 );
//		sensors.add(sensor1);
//
//
//
//		//		MobileSensor sensor0 = new MobileSensor("Sensor0" //Tuple's name -> ACHO QUE DÁ PARA USAR ESTE CONSTRUTOR 
//		//												,"EEG" //Tuple's type
//		//												,i //User Id
//		//												,"appId0" //app's name
//		//												,distribution0);// find into the paper about tuples and distribution
//		//		MobileSensor sensor1 = new MobileSensor("Sensor1","EEG",i,"appId1",distribution1);
//		//		MobileSensor sensor2 = new MobileSensor("Sensor2"// tuple's name
//		//				, i// userId
//		//				, "EEG"//Tuple's name
//		//				, -1//gatewayDeviceId - I think it is the ServerCloudlet id - as it not creation yet, -1
//		//				, 20//latency
//		//				, null//geoLocation - it uses the MobileDevice's localization 
//		//				, distribution2//transmitDistribution
//		//				, 2000//cpuLength
//		//				, 10//nwLength
//		//				, "EEG"
//		//				, "destModuleName2");
//		//
//		//		MobileSensor sensor3 = new MobileSensor("Sensor3"
//		//				, i// userId
//		//				, "EEG"
//		//				, -1//gatewayDeviceId - I think it is the ServerCloudlet id - as it not creation yet, -1
//		//				, 20//latency
//		//				, null//geoLocation - it uses the MobileDevice's localization 
//		//				, distribution3//transmitDistribution
//		//				, 2000//cpuLength
//		//				, 10//nwLength
//		//				, "EEG"
//		//				, "destModuleName3");
//		//		Set<MobileSensor> sensors = new HashSet<>();
//		//		sensors.add(sensor0);
//		//		sensors.add(sensor1);
//		//		sensors.add(sensor2);
//		//		sensors.add(sensor3);
//
//		/***************End set of Mobile Sensors****************/
//
//
//		/***************Start set of Mobile Actuators ****************/
//
//		MobileActuator actuator0 = new MobileActuator("Actuator0", i, "appId0", "actuatorType0");
//		MobileActuator actuator1 = new MobileActuator("Actuator1", i, "appId1", "actuatorType1");
//		MobileActuator actuator2 = new MobileActuator("Actuator2"
//				, i// userId
//				, "appId2"
//				, -1 //gatewayDeviceId
//				, 2 //latency
//				, null //geoLocation
//				, "actuatorType2"
//				, "srcModuleName2");
//		MobileActuator actuator3 = new MobileActuator("Actuator3"
//				, i// userId
//				, "appId3"
//				, -1 //gatewayDeviceId
//				, 2 //latency
//				, null //geoLocation
//				, "actuatorType3"
//				, "srcModuleName3");
//
//
//		Set<MobileActuator> actuators = new HashSet<>();
//		actuators.add(actuator0);
//		actuators.add(actuator1);
//		actuators.add(actuator2);
//		actuators.add(actuator3);
//
//		/***************End set of Mobile Actuators ****************/
//
//
//
//
//
//		/***************Start MobileDevice Configurations ****************/
//
//		FogLinearPowerModel powerModel = new FogLinearPowerModel(87.53d, 82.44d);//10//maxPower
//
//		List<Pe> peList = new ArrayList<>();
//		int mips = 1000;
//		// 3. Create PEs and add these into a list.
//		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to storage Pe id and MIPS Rating - to CloudSim 
//
//		int hostId = FogUtils.generateEntityId();
//		long storage = 1000; // host storage
//		int bw = 1000;
//		int ram = 1000;
//		PowerHost host = new PowerHost(//To the hardware's characteristics (MobileDevice) - to CloudSim
//				hostId,
//				new RamProvisionerSimple(ram),
//				new BwProvisionerOverbooking(bw),
//				storage,
//				peList,
//				new StreamOperatorScheduler(peList),
//				powerModel
//				);
//
//		//		List<Host> hostList = new ArrayList<Host>();//why to create a list?
//		//		hostList.add(host);
//
//
//		String arch = "x86"; // system architecture
//		String os = "Android"; // operating system
//		String vmm = "empty";//Empty 
//		double vmSize = MaxAndMin.MAX_VM_SIZE;
//		double time_zone = 10.0; // time zone this resource located
//		double cost = 3.0; // the cost of using processing in this resource
//		double costPerMem = 0.05; // the cost of using memory in this resource
//		double costPerStorage = 0.001; // the cost of using storage in this
//		// resource
//		double costPerBw = 0.0; // the cost of using bw in this resource
//		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
//		// devices by now
//
//		//for Characteristics 
//
//		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch
//				, os
//				, vmm
//				, host
//				, time_zone
//				, cost
//				, costPerMem
//				, costPerStorage
//				, costPerBw);
//
//		//		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);
//
//		MobileDevice st = null;
//		//Vm vmTemp = new Vm(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
//		float maxServiceValue = rand.nextFloat()*100;
//		try {
//
//			while(true){
//				coordX =  rand.nextInt(MaxAndMin.MAX_X);
//				coordY = rand.nextInt(MaxAndMin.MAX_Y);
//				if(coordDevices.getPositions(coordX, coordY)==-1){//verify if it is empty
//
//					st = new MobileDevice("SmartThing"+ Integer.toString(i)
//							, characteristics
//							, null//vmAllocationPolicy - seria a maquina que executa dentro do fogDevice?
//							, storageList
//							, 10//schedulingInterval
//							, 512//uplinkBandwidth
//							, 1024//downlinkBandwidth
//							, 5//uplinkLatency
//							, 0.01//mipsPer..
//							, coordX, coordY
//							, i//id
//							, direction
//							, speed
//							, maxServiceValue
//							, vmSize);
//					st.setTempSimulation(0);
//					st.setTimeFinishDeliveryVm(0);
//					st.setTimeFinishHandoff(0);
//					st.setSensors(sensors);
//					st.setActuators(actuators);
//
//					smartThing.add(st);
//					coordDevices.setPositions(st.getId(), st.getCoord().getCoordX(), st.getCoord().getCoordY());
//					break;
//				}
//				else{
//					System.out.println("POSITION ISN'T AVAILABLE... (ST)X ="+ coordX+ " Y = " +coordY+" Reallocating..." );
//
//				}
//			} 
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//	}
//
//	public static void addServerCloudlet(List<FogDevice> serverCloudlets, Coordinate coordDevices, int i){
//
//		Random rand = new Random(i*i);
//		int coordX,coordY;
//		DecisionMigration migrationStrategy;
//		if(getMigStrategyPolicy()==Policies.LOWEST_LATENCY){
//			migrationStrategy = new LowestLatency(getServerCloudlets(), getApDevices(), getMigPointPolicy());
//		}
//		else if(getMigStrategyPolicy()==Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET){
//			migrationStrategy = new LowestDistBwSmartThingServerCloudlet(getServerCloudlets(), getApDevices(), getMigPointPolicy());
//
//		}
//		else{ //if(getMigStrategyPolicy()==Policies.LOWEST_DIST_BW_SMARTTING_AP){
//			migrationStrategy = new LowestDistBwSmartThingAP(getServerCloudlets(), getApDevices(), getMigPointPolicy());
//		}
//
//
//		FogLinearPowerModel powerModel = new FogLinearPowerModel(87.53d, 50.44d);//10//maxPower
//
//		List<Pe> peList = new ArrayList<>();//CloudSim Pe (Processing Element) class represents CPU unit, defined in terms of Millions
//		//		 * Instructions Per Second (MIPS) rating
//		int mips = 2800*(i+1);
//		// 3. Create PEs and add these into a list.
//		peList.add(new Pe(i, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating - to CloudSim 
//
//		int hostId = FogUtils.generateEntityId();
//		long storage = 1000*1024*1024; // host storage -> infinity!
//		int bw = 5000;
//		int ram = 10000;
//		PowerHost host = new PowerHost(//To the hardware's characteristics (MobileDevice) - to CloudSim
//				hostId,
//				new RamProvisionerSimple(ram),
//				new BwProvisionerOverbooking(bw),
//				storage,
//				peList,
//				new StreamOperatorScheduler(peList),
//				powerModel
//				);
//
//		List<Host> hostList = new ArrayList<Host>();//why to create a list?
//		hostList.add(host);
//
//
//
//
//		String arch = "x86"; // system architecture
//		String os = "Linux"; // operating system
//		String vmm = "Empty";//Empty 
//		double time_zone = 10.0; // time zone this resource located
//		double cost = 3.0; // the cost of using processing in this resource
//		double costPerMem = 0.05; // the cost of using memory in this resource
//		double costPerStorage = 0.001; // the cost of using storage in this
//		// resource
//		double costPerBw = 0.0; // the cost of using bw in this resource
//		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
//		// devices by now
//
//		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch
//				, os
//				, vmm
//				, host
//				, time_zone
//				, cost
//				, costPerMem
//				, costPerStorage
//				, costPerBw);
//
//		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);
//
//		FogDevice  sc = null;
//		Service serviceOffer = new Service(); 
//		serviceOffer.setType(rand.nextInt(10000)%MaxAndMin.MAX_SERVICES);
//		if(serviceOffer.getType()==Services.HIBRID || serviceOffer.getType() == Services.PUBLIC){
//			serviceOffer.setValue(rand.nextFloat()*10);
//		}
//		else {
//			serviceOffer.setValue(0);
//		}
//		try {
//
//			while(true){
//				coordX = rand.nextInt(MaxAndMin.MAX_X);
//				coordY =  rand.nextInt(MaxAndMin.MAX_X);
//				if(coordDevices.getPositions(coordX, coordY)==-1){//verify if it is empty
//					sc =  new FogDevice("ServerCloudlet"+ Integer.toString(i) //name
//							, characteristics
//							, vmAllocationPolicy
//							, storageList
//							, 10//schedulingInterval
//							, 1000//uplinkBandwidth
//							, 2000//downlinkBandwidth
//							, i//uplinkLatency
//							, 0.01//mipsPer..
//							, coordX, coordY
//							, i
//							, serviceOffer
//							, migrationStrategy
//							, getPolicyReplicaVM()
//							);
//					serverCloudlets.add(i,sc);
//					coordDevices.setPositions(sc.getId(), sc.getCoord().getCoordX(), sc.getCoord().getCoordY());
//
//					break;
//				}
//				else{
//					System.out.println("POSITION ISN'T AVAILABLE... (SC) X ="+ coordX+ " Y = " +coordY+" Reallocating... i: "+ i);
//
//				}
//			}
//
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}//id
//	}
//	private static void createServerCloudletsNetwork(List<FogDevice> serverCloudlets){
//		//for no full graph, use -1 to link
//		HashMap<FogDevice, Double> net = new HashMap<>();
//		Random rand = new Random(100);
//		for(FogDevice sc : serverCloudlets){//It makes a full graph 
//			for(FogDevice sc1 : serverCloudlets){
//				if(sc.equals(sc1)){
//					break;
//				}
//				if(rand.nextInt()%20 == 0){
//					break;
//				}
//				//	net.keySet().add(sc1);
//				if(sc.getUplinkBandwidth() < sc1.getDownlinkBandwidth()){
//					net.put(sc1, sc.getUplinkBandwidth());
////					NetworkTopology.addLink(sc.getId(), sc1.getId(), sc.getUplinkBandwidth(), 0.01);
//				}
//				else{
//					net.put(sc1,sc1.getDownlinkBandwidth());
////					NetworkTopology.addLink(sc.getId(), sc1.getId(), sc1.getDownlinkBandwidth(), 0.01);
//				}	
//			}
//			sc.setNetServerCloudlets(net);
//		}
//	}
//
//
//
//	public static int getStepPolicy() {
//		return stepPolicy;
//	}
//
//
//	public static void setStepPolicy(int stepPolicy) {
//		AppExemplo3.stepPolicy = stepPolicy;
//	}
//
//
//	public static List<MobileDevice> getSmartThings() {
//		return smartThings;
//	}
//
//
//	public static void setSmartThings(List<MobileDevice> smartThings) {
//		AppExemplo3.smartThings = smartThings;
//	}
//
//
//	public static List<FogDevice> getServerCloudlets() {
//		return serverCloudlets;
//	}
//
//
//	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
//		AppExemplo3.serverCloudlets = serverCloudlets;
//	}
//
//
//	public static List<ApDevice> getApDevices() {
//		return apDevices;
//	}
//
//
//	public static void setApDevices(List<ApDevice> apDevices) {
//		AppExemplo3.apDevices = apDevices;
//	}
//
//
//	public static int getMigPointPolicy() {
//		return migPointPolicy;
//	}
//
//
//	public static void setMigPointPolicy(int migPointPolicy) {
//		AppExemplo3.migPointPolicy = migPointPolicy;
//	}
//
//
//	public static int getMigStrategyPolicy() {
//		return migStrategyPolicy;
//	}
//
//
//	public static void setMigStrategyPolicy(int migStrategyPolicy) {
//		AppExemplo3.migStrategyPolicy = migStrategyPolicy;
//	}
//
//
//	public static int getPositionApPolicy() {
//		return positionApPolicy;
//	}
//
//
//	public static void setPositionApPolicy(int positionApPolicy) {
//		AppExemplo3.positionApPolicy = positionApPolicy;
//	}
//
//
//	public static int getPolicyReplicaVM() {
//		return policyReplicaVM;
//	}
//
//
//	public static void setPolicyReplicaVM(int policyReplicaVM) {
//		AppExemplo3.policyReplicaVM = policyReplicaVM;
//	}
//
//
//	public static Coordinate getCoordDevices() {
//		return coordDevices;
//	}
//
//
//	public static void setCoordDevices(Coordinate coordDevices) {
//		AppExemplo3.coordDevices = coordDevices;
//	}
//
//
//	public static boolean isCloud() {
//		return CLOUD;
//	}
//
//
//	public static int getNumofdepts() {
//		return numOfDepts;
//	}
//
//
//	public static int getNumofmobilesperdept() {
//		return numOfMobilesPerDept;
//	}
//
//
//	public static double getEegTransmissionTime() {
//		return EEG_TRANSMISSION_TIME;
//	}
//
//
//	public static List<Cloudlet> getCloudletList() {
//		return cloudletList;
//	}
//
//
//	public static void setCloudletList(List<Cloudlet> cloudletList) {
//		AppExemplo3.cloudletList = cloudletList;
//	}
//	public static int getSeed() {
//		return seed;
//	}
//	public static void setSeed(int seed) {
//		AppExemplo3.seed = seed;
//	}
//
}
