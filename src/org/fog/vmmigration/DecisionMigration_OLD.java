package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.List;
import org.fog.entities.*;
import org.fog.localization.*;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;


public class DecisionMigration_OLD {

	private static ApDevice correntAP;
	private static int nextApId;
	private static int nextServerClouletId;


	private static int smartThingPosition;
	private static boolean migZone;
	private static boolean migPoint;

	/**
	 * @return boolean
	 */
	public static boolean decisionMigration(List<FogDevice> serverCloudlets, 
			List<ApDevice> apDevices, MobileDevice smartThing, int migPointPolicy, int migStrategyPolicy){
		Migration migration = new Migration();
		
		if(smartThing.getSpeed()==0){//smartThing is mobile
			//	System.out.println("SmartThing is not mobile");
			return false;//no migration
		}

		setCorrentAP(smartThing.getSourceAp());			

		setSmartThingPosition(
				DiscoverLocalization.discoverLocal(getCorrentAP().getCoord()
						, smartThing.getCoord()));//return the relative position between Access point and smart thing -> set this value

		setMigPoint(migPointPolicyFunction(migPointPolicy //either (0 or 1) -> policies
				, smartThing));// 0 -> fixed or 1 -> with speed


//		setMigZone(migration.migrationZoneFunction(smartThing.getDirection(), getSmartThingPosition()));
		if(getCorrentAP().getServerCloudlet().equals(smartThing.getVmLocalServerCloudlet())){//the handoff already has occur. The worst case

			if(!(isMigPoint() && isMigZone())){
				//			System.out.println("SmartThing is not on migration zone...");

				return false;//no migration
			}

			if(migStrategyPolicy == Policies.LOWEST_LATENCY){
				//to do this policy
				setNextServerClouletId(migration.lowestLatencyCostServerCloudlet(serverCloudlets,apDevices, smartThing));
				if(getNextServerClouletId()<0){
					//				System.out.println("Does not exist nextServerCloulet");
					return false;
				}
				List<ApDevice> tempListAps = new ArrayList<>(); // It creates a temporary List to invoke the nextAp
				for(ApDevice ap: serverCloudlets.get(getNextServerClouletId()).getApDevices()){ 
					tempListAps.add(ap);
				}

				setNextApId(migration.nextAp(tempListAps, smartThing));		
				if(getNextApId() < 0){//index is negative -> A migração não deve ocorrer, pois caso o st faça um handoff, não será para nenhum ap deste ServerCloudlet
					//	System.out.println("Does not exist nextAp");
					return false;//no migration
				}
				if(!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
					//						System.out.println("#########Ap is not Edge######");
					return false;//no migration
				}

			}
			else if(migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET){

				setNextServerClouletId(migration.nextServerCloudlet(serverCloudlets, smartThing));//choose the closest cloudlet, it returns the Id or -1 
				if(getNextServerClouletId() < 0){// Does next ServerCloudlet exist?
					//		System.out.println("Does not exist nextServerCloulet");
					return false;
				}

				List<ApDevice> tempListAps = new ArrayList<>(); // It creates a temporary List to invoke the nextAp
				for(ApDevice ap: serverCloudlets.get(getNextServerClouletId()).getApDevices()){ 
					tempListAps.add(ap);
				}

				setNextApId(migration.nextAp(tempListAps, smartThing));		
				if(getNextApId() < 0){//index is negative -> A migração não deve ocorrer, pois caso o st faça um handoff, não será para nenhum ap deste ServerCloudlet
					//	System.out.println("Does not exist nextAp");
					return false;//no migration
				}
				if(!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
					//						System.out.println("#########Ap is not Edge######");
					return false;//no migration
				}

				//	Tenho um set de aps no ServerCloudlet e preciso passar uma lista de aps. talvez fazer outra? -> ok
				//			verificar melhor a parte dos 180 graus e dos 90 graus -> ok
				//			se uma cloudlet nao estiver disponivel, olhar para as proximas duas. acho que seja melhor integrar com a politica 
				//			de latencias. soma das latencias. -> ok
				//			Ainda não mostrei o nextStep 
			}
			else if(migStrategyPolicy == Policies.LOWEST_DIST_BW_SMARTTING_AP){
				setNextApId(migration.nextAp(apDevices, smartThing));// tem o mesmo comportamento da escolha pelo handoff
				if(getNextApId() < 0){//index is negative 
					//System.out.println("Does not exist nextAp");
					return false;//no migration
				}
				if(!migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
					//		System.out.println("#########Ap is not Edge######");
					return false;//no migration
				}
				setNextServerClouletId(
						apDevices.get(getNextApId()).getServerCloudlet().getMyId());//ServerCloudlet linked with nextap
			}
			
			if(!checkLinkStatus(smartThing.getSourceServerCloudlet(), serverCloudlets.get(getNextServerClouletId()))){
				//		System.out.println("Link between ServerCloudlets is down");

				return false;

			}

			if(!serverCloudlets.get(getNextServerClouletId()).isAvailable()){ //to define some policy for this available!!!
				//		System.out.println("Cloudlet is not available");
				return false;//no migration
			}
		}
		else{
			setNextServerClouletId(getCorrentAP().getServerCloudlet().getMyId());
			System.out.println("HANDOFF JA OCORREU....");
		}
		int serviceType = serverCloudlets.get(getNextServerClouletId()).getService().getType();

		if(serviceType == Services.PRIVATE){
			smartThing.setDestinationServerCloudlet(serverCloudlets.get(getNextServerClouletId()));//it saves the destination serverCloudlet
			//	smartThing.setDestinationAp(apDevices.get(getNextApId()));//it saves the destination ap
			//	System.out.println("NEXT AP: "+getNextApId());
			//System.out.println("Service is Private");
			return true;
		}
		else if(serviceType == Services.HIBRID){//it needs to define the policy
			smartThing.setDestinationServerCloudlet(serverCloudlets.get(getNextServerClouletId()));//it saves the destination serverCloudlet
			//				smartThing.setDestinationAp(apDevices.get(getNextApId()));//it saves the destination ap
			//				System.out.println("Service is Hibrid");
			return true;
		}
		else if(serviceType == Services.PUBLIC){
			float serviceValue = serverCloudlets.get(getNextServerClouletId()).getService().getValue();
			if(serviceValue <= smartThing.getMaxServiceValue()){
				smartThing.setDestinationServerCloudlet(serverCloudlets.get(getNextServerClouletId()));//it saves the destination serverCloudlet
				//					smartThing.setDestinationAp(apDevices.get(getNextApId()));//it saves the destination ap
				//					System.out.println("Service is Public");

				return true; //the smartThing agrees
			}
			else{
				//		System.out.println("The value is expensive...");
				return false;
			}
		}




		//ABORT SIMULATION
		System.out.println("******" +
				"*ERRO*" +
				"******");
		System.out.println("DecisionMigration");
		System.exit(0);
		return false;


	}//end class

	public static boolean migPointPolicyFunction(int policy, MobileDevice smartThing){
//		Migration migration = new Migration();
//		double distance = Distances.checkDistance(getCorrentAP().getCoord(), smartThing.getCoord());
//		smartThing.setMigTime(migration.migrationTimeFunction(smartThing.getVmSize()//smartThing.getVmMobileDevice().getSize(),//vmSize
//				, smartThing.getSourceServerCloudlet().getUplinkBandwidth()));// getUplinkLatency()));//Latency
//
//
//
//		if(policy == Policies.FIXED_MIGRATION_POINT){
//			return migration.migrationPointFunction(distance);
//		}
//		else {
//			return migration.migrationPointFunction(distance, smartThing.getMigTime(), smartThing.getSpeed());//relative according smartThing's speed
//		}
		return false;
	}

	public static boolean checkLinkStatus (FogDevice sourceServerCloudlet, FogDevice destinationServerCloudlet){

		if(sourceServerCloudlet.getNetServerCloudlets().get(destinationServerCloudlet)!=null){
			return true;
		}
		else{
			return false;
		}
	}
	public static ApDevice getCorrentAP() {
		return correntAP;
	}

	public static void setCorrentAP(ApDevice correntAP) {
		DecisionMigration_OLD.correntAP = correntAP;
	}


	public static int getSmartThingPosition() {
		return smartThingPosition;
	}


	public static void setSmartThingPosition(int smartThingPosition) {
		DecisionMigration_OLD.smartThingPosition = smartThingPosition;
	}


	public static int getNextApId() {
		return nextApId;
	}


	public static void setNextApId(int nextApId) {
		DecisionMigration_OLD.nextApId = nextApId;
	}


	public static boolean isMigPoint() {
		return migPoint;
	}


	public static void setMigPoint(boolean migPoint) {
		DecisionMigration_OLD.migPoint = migPoint;
	}


	public static boolean isMigZone() {
		return migZone;
	}


	public static void setMigZone(boolean migZone) {
		DecisionMigration_OLD.migZone = migZone;
	}
	public static int getNextServerClouletId() {
		return nextServerClouletId;
	}


	public static void setNextServerClouletId(int nextCloulet) {
		DecisionMigration_OLD.nextServerClouletId = nextCloulet;
	}



}
