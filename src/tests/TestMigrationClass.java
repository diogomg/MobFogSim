package tests;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
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
import org.fog.localization.Distances;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.vmmigration.DecisionMigration;
import org.fog.vmmigration.LowestDistBwSmartThingAP;
import org.fog.vmmigration.LowestDistBwSmartThingServerCloudlet;
import org.fog.vmmigration.LowestLatency;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.Service;
import org.fog.vmmobile.AppExemplo2;
import org.fog.vmmobile.constants.MaxAndMin;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestMigrationClass {
//
//	private static int stepPolicy; //Quantity of steps in the nextStep Function
//	private static List<MobileDevice> smartThings = new ArrayList<MobileDevice>();
//	private static List<FogDevice> serverCloudlets = new ArrayList<>();
//	private static List<ApDevice> apDevices = new ArrayList<>();
//	private static int migPointPolicy;
//	private static int migStrategyPolicy;
//	private static int positionApPolicy;
//	private static int policyReplicaVM;
//	private static Coordinate coordDevices;=//new Coordinate(MaxAndMin.MAX_X, MaxAndMin.MAX_Y);//Grid/Map 
//
//
//	@BeforeClass
//	public static void setUpBeforeClass() throws Exception {
//		/**********It's necessary to CloudSim.java for working correctly**********/
//		Log.disable();
//		int numUser = 5; // number of cloud users
//		Calendar calendar = Calendar.getInstance();
//		boolean traceFlag = false; // mean trace events
//		CloudSim.init(numUser, calendar, traceFlag);
//		/**************************************************************************/
//
//		//		setMigPointPolicy(Policies.FIXED_MIGRATION_POINT);
//		setMigPointPolicy(Policies.SPEED_MIGRATION_POINT);
//
//		setPolicyReplicaVM(Policies.MIGRATION_COMPLETE_VM);
//		//	setPolicyReplicaVM(Policies.MIGRATION_CONTAINER_VM);
//
//
//		//		int migStrategyPolicy = Policies.LOWEST_LATENCY;//26 
//		//		int migStrategyPolicy = Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET;//21
//		//		int migStrategyPolicy = Policies.LOWEST_DIST_BW_SMARTTING_AP;//32
//		//		setMigStrategyPolicy(Policies.LOWEST_LATENCY);
//		setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_AP);
//
//		//		setMigStrategyPolicy(Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET);
//
//
//		//		int positionApPolicy = Policies.FIXED_AP_LOCATION;//It should receive by initial parameters (for the first Part)
//		//		int positionApPolicy = Policies.RANDOM_AP_LOCATION;//It should receive by initial parameters (for the first Part)
//		setPositionApPolicy(Policies.FIXED_AP_LOCATION);
//		//		setPositionApPolicy(Policies.RANDOM_AP_LOCATION);
//
//
//		setStepPolicy(1);
//
//		ApDevice ap1 =null;
//		
//		//configureAps 
//		int j=0;
//		for(int i=0; i<6;i++){
//			 ap1 = new ApDevice("AccessPoint"+Integer.toString(i),//name
//					(short) (600+j),(short)1100//coordX, coordY
//					,i//ap.set//id
//					,100//downLinkBandwidth
//					,200//engergyConsuption
//					,MaxAndMin.MAX_ST_IN_AP//maxSmartThing
//					,50//upLinkBandwidth
//					,100//upLinkLatency
//					);//ver valores reais e melhores
//			apDevices.add(ap1);
//			coordDevices.setPositions(ap1.getId(), ap1.getCoord().getCoordX(), ap1.getCoord().getCoordY());
//			j+=400;
//		}
//
//		//ConfigureServerSmartThings
//		
//		/***************Start MobileDevice Configurations ****************/
//
//		FogLinearPowerModel powerModel = new FogLinearPowerModel(87.53d, 82.44d);//10//maxPower
//
//		List<Pe> peList = new ArrayList<>();
//		int mips = 2800;
//		// 3. Create PEs and add these into a list.
//		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating - to CloudSim 
//		int hostId = FogUtils.generateEntityId();
//		long storage = 1000; // host storage
//		int bw = 1000;
//		int ram = 8000;
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
//		double vmSize = 200;
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
//		MobileDevice st = null;
//		st = new MobileDevice("SmartThing"+ Integer.toString(0)
//				, characteristics
//				, null//vmAllocationPolicy - seria a maquina que executa dentro do fogDevice?
//				, null
//				, 10//schedulingInterval
//				, 512//uplinkBandwidth
//				, 1024//downlinkBandwidth
//				, 5//uplinkLatency
//				, 0.01//mipsPer..
//				, (short)750, (short)1200
//				, 0//id
//				, 1//direction
//				, 2//speed
//				, 1.1f//maxServiceValue
//				, 0);
//		st.setTempSimulation(0);
//		st.setTimeFinishDeliveryVm(0);
//		st.setTimeFinishHandoff(0);
//		st.setSensors(null);
//		st.setActuators(null);
//
//		smartThings.add(st);
//		coordDevices.setPositions(st.getId(), st.getCoord().getCoordX(), st.getCoord().getCoordY());
//
//		//ConfigureServerCloudlets
//
//
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
//
//		
//		// 3. Create PEs and add these into a list.
//		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating - to CloudSim 
//
//		int hostIdfog[] = {FogUtils.generateEntityId(),FogUtils.generateEntityId(),FogUtils.generateEntityId()};
//		storage = 1000; // host storage
//		 bw = 1000;
//		 ram = 8000;
//		 host = new PowerHost(//To the hardware's characteristics (MobileDevice) - to CloudSim
//				hostIdfog[0],
//				new RamProvisionerSimple(ram),
//				new BwProvisionerOverbooking(bw),
//				storage,
//				peList,
//				new StreamOperatorScheduler(peList),
//				powerModel
//				);
//
////		List<Host> hostList = new ArrayList<Host>();//why to create a list?
////		hostList.add(host);
//
//
//		 arch = "x86"; // system architecture
//		 os = "Linux"; // operating system
//		 vmm = "Empty";//Empty 
//		 time_zone = 10.0; // time zone this resource located
//		 cost = 3.0; // the cost of using processing in this resource
//		 costPerMem = 0.05; // the cost of using memory in this resource
//		 costPerStorage = 0.001; // the cost of using storage in this
//		// resource
//		 costPerBw = 0.0; // the cost of using bw in this resource
//		 storageList = new LinkedList<Storage>(); // we are not adding SAN
//		// devices by now
//
//		characteristics = new FogDeviceCharacteristics(arch
//				, os
//				, vmm
//				, host
//				, time_zone
//				, cost
//				, costPerMem
//				, costPerStorage
//				, costPerBw);
//
////		AppModuleAllocationPolicy vmAllocationPolicy = new AppModuleAllocationPolicy(hostList);
//
//		
//		
//
//		FogDevice  sc = null;
//		Service serviceOffer = new Service(); 
//		serviceOffer.setType(Services.PUBLIC);
//		serviceOffer.setValue(10f);
//		
//		sc =  new FogDevice("ServerCloudlet"+ Integer.toString(0) //name
//				, characteristics
//				, null
//				, null
//				, 10//schedulingInterval
//				, 1000//uplinkBandwidth
//				, 2000//downlinkBandwidth
//				, 10//uplinkLatency
//				, 0.01//mipsPer..
//				, (short) 700, (short)400
//				, 0
//				, serviceOffer
//				, migrationStrategy
//				, getPolicyReplicaVM()
//				);
//		serverCloudlets.add(sc);
//		coordDevices.setPositions(sc.getId(), sc.getCoord().getCoordX(), sc.getCoord().getCoordY());
//	
//		host = new PowerHost(//To the hardware's characteristics (MobileDevice) - to CloudSim
//				hostIdfog[1],
//				new RamProvisionerSimple(ram),
//				new BwProvisionerOverbooking(bw),
//				storage,
//				peList,
//				new StreamOperatorScheduler(peList),
//				powerModel
//				);
//		characteristics = new FogDeviceCharacteristics(arch
//				, os
//				, vmm
//				, host
//				, time_zone
//				, cost
//				, costPerMem
//				, costPerStorage
//				, costPerBw);
//		serviceOffer.setType(Services.HIBRID);
//		serviceOffer.setValue(5f);
//		
//		sc =  new FogDevice("ServerCloudlet"+ Integer.toString(1) //name
//				, characteristics
//				, null
//				, null
//				, 10//schedulingInterval
//				, 2000//uplinkBandwidth
//				, 3000//downlinkBandwidth
//				, 5//uplinkLatency
//				, 0.01//mipsPer..
//				, (short) 2000, (short)600
//				, 1//id
//				, serviceOffer
//				, migrationStrategy
//				, getPolicyReplicaVM()
//				);
//		serverCloudlets.add(sc);
//		coordDevices.setPositions(sc.getId(), sc.getCoord().getCoordX(), sc.getCoord().getCoordY());
//
//		
//		host = new PowerHost(//To the hardware's characteristics (MobileDevice) - to CloudSim
//				hostIdfog[2],
//				new RamProvisionerSimple(ram),
//				new BwProvisionerOverbooking(bw),
//				storage,
//				peList,
//				new StreamOperatorScheduler(peList),
//				powerModel
//				);
//		characteristics = new FogDeviceCharacteristics(arch
//				, os
//				, vmm
//				, host
//				, time_zone
//				, cost
//				, costPerMem
//				, costPerStorage
//				, costPerBw);
//		serviceOffer.setType(Services.HIBRID);
//		serviceOffer.setValue(5f);
//		
//		sc =  new FogDevice("ServerCloudlet"+ Integer.toString(2) //name
//				, characteristics
//				, null
//				, null
//				, 10//schedulingInterval
//				, 4000//uplinkBandwidth
//				, 8000//downlinkBandwidth
//				, 1//uplinkLatency
//				, 0.01//mipsPer..
//				, (short) 2600, (short)1500
//				, 0
//				, serviceOffer
//				, migrationStrategy
//				, getPolicyReplicaVM()
//				);
//		serverCloudlets.add(sc);
//		coordDevices.setPositions(sc.getId(), sc.getCoord().getCoordX(), sc.getCoord().getCoordY());
//
//		//--------------
//		
//		int index;//Auxiliary  
//		int myCount=0;
//		for(MobileDevice stt:smartThings){//it makes the connection between SmartThing and the closest AccessPoint
//			if(!ApDevice.connectApSmartThing(apDevices,stt,0.0)){
//				myCount++;
//			}
//		}
//		System.out.println("total no connection: "+myCount);
//
//		for(ApDevice ap: apDevices){ //it makes the connection between AccessPoint and the closest ServerCloudlet
//			index = Distances.theClosestServerCloudletToAp(serverCloudlets,ap);
//
//			ap.setServerCloulet(serverCloudlets.get(index));
//			serverCloudlets.get(index).setApDevices(ap,Policies.ADD);
//			for(MobileDevice stt : ap.getSmartThings()){//it makes the symbolic link between smartThing and ServerCloudlet
//				serverCloudlets.get(index).connectServerCloudletSmartThing(stt);
//			}
//		}		
//
//		createServerCloudletsNetwork(serverCloudlets);
//	}
//	private static void createServerCloudletsNetwork(List<FogDevice> serverCloudlets){
//		//for no full graph, use -1 to link
//		HashMap<FogDevice, Double> net = new HashMap<>();
//		Random rand = new Random(100);//melhorar
//		for(FogDevice sc : serverCloudlets){//It makes a full graph 
//			for(FogDevice sc1 : serverCloudlets){
//				if(sc.equals(sc1)){
//					break;
//				}
//				if(rand.nextInt()%10 == 0){
//					break;
//				}
//				//	net.keySet().add(sc1);
//				if(sc.getUplinkBandwidth() < sc1.getDownlinkBandwidth()){
//					net.put(sc1, sc.getUplinkBandwidth());
//				}
//				else{
//					net.put(sc1,sc1.getDownlinkBandwidth());
//				}	
//			}
//			sc.setNetServerCloudlets(net);
//		}
//	}
//
//	@AfterClass
//	public static void tearDownAfterClass() throws Exception {
//	}
//
//	@Before
//	public void setUp() throws Exception {
//
//	}
//
//	@After
//	public void tearDown() throws Exception {
//	}
//
//	@Test
//	public final void testApAvailableList() {
//		List<ApDevice> expectedApList = new ArrayList<>();
//		expectedApList.add(getApDevices().get(1));
//		getSmartThings().get(0).setCoord((short)900,(short)1200);
//		assertEquals(getApDevices().get(0), getSmartThings().get(0).getSourceAp());
//		assertEquals(expectedApList, Migration.apAvailableList(getApDevices(), getSmartThings().get(0)));
//	}
//
//	@Test
//	public final void testMigrationPointFunctionDouble() {
//		assertEquals((short)750,getSmartThings().get(0).getCoord().getCoordX() );
//	}
//
//	@Test
//	public final void testMigrationPointFunctionDoubleDoubleInt() {
//
//	}
//
//	@Test
//	public final void testMigrationTimeFunction() {
//
//	}
//
//	@Test
//	public final void testMigrationZoneFunction() {
//
//	}
//
//	@Test
//	public final void testNextAp() {
//		
//		assertEquals(-1, Migration.nextAp(getApDevices(), getSmartThings().get(0)));
//
//	}
//
//	@Test
//	public final void testNextApFromCloudlet() {
//
//	}
//
//	@Test
//	public final void testServerClouletsAvailableList() {
//
//	}
//
//	@Test
//	public final void testNextServerCloudlet() {
//
//	}
//
//	@Test
//	public final void testMigPointPolicyFunction() {
//
//	}
//
//	@Test
//	public final void testIsEdgeAp() {
//
//	}
//
//	@Test
//	public final void testLowestLatencyCostServerCloudlet() {
//
//	}
//
//	@Test
//	public final void testSumCostFunction() {
//
//	}
//
//	@Test
//	public final void testIsMigrationPoint() {
//
//	}
//
//	@Test
//	public final void testSetMigrationPoint() {
//
//	}
//
//	@Test
//	public final void testIsMigrationZone() {
//
//	}
//
//	@Test
//	public final void testSetMigrationZone() {
//
//	}
//
//	public static int getStepPolicy() {
//		return stepPolicy;
//	}
//
//	public static void setStepPolicy(int stepPolicy) {
//		TestMigrationClass.stepPolicy = stepPolicy;
//	}
//
//	public static List<MobileDevice> getSmartThings() {
//		return smartThings;
//	}
//
//	public static void setSmartThings(List<MobileDevice> smartThings) {
//		TestMigrationClass.smartThings = smartThings;
//	}
//
//	public static List<FogDevice> getServerCloudlets() {
//		return serverCloudlets;
//	}
//
//	public static void setServerCloudlets(List<FogDevice> serverCloudlets) {
//		TestMigrationClass.serverCloudlets = serverCloudlets;
//	}
//
//	public static List<ApDevice> getApDevices() {
//		return apDevices;
//	}
//
//	public static void setApDevices(List<ApDevice> apDevices) {
//		TestMigrationClass.apDevices = apDevices;
//	}
//
//	public static int getMigPointPolicy() {
//		return migPointPolicy;
//	}
//
//	public static void setMigPointPolicy(int migPointPolicy) {
//		TestMigrationClass.migPointPolicy = migPointPolicy;
//	}
//
//	public static int getMigStrategyPolicy() {
//		return migStrategyPolicy;
//	}
//
//	public static void setMigStrategyPolicy(int migStrategyPolicy) {
//		TestMigrationClass.migStrategyPolicy = migStrategyPolicy;
//	}
//
//	public static int getPositionApPolicy() {
//		return positionApPolicy;
//	}
//
//	public static void setPositionApPolicy(int positionApPolicy) {
//		TestMigrationClass.positionApPolicy = positionApPolicy;
//	}
//
//	public static int getPolicyReplicaVM() {
//		return policyReplicaVM;
//	}
//
//	public static void setPolicyReplicaVM(int policyReplicaVM) {
//		TestMigrationClass.policyReplicaVM = policyReplicaVM;
//	}
//
//	public static Coordinate getCoordDevices() {
//		return coordDevices;
//	}
//
//	public static void setCoordDevices(Coordinate coordDevices) {
//		TestMigrationClass.coordDevices = coordDevices;
//	}
//
}
