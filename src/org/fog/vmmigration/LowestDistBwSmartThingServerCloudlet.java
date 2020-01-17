package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.List;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.DiscoverLocalization;

public class LowestDistBwSmartThingServerCloudlet implements DecisionMigration {
	
	private List<FogDevice> serverCloudlets;
	private List<ApDevice> apDevices;
	private int migPointPolicy;
	private ApDevice correntAP;
	private int nextApId;
	private int nextServerClouletId;
	private int smartThingPosition;
	private boolean migZone;
	private boolean migPoint;
	private int policyReplicaVM;
	
	
	public LowestDistBwSmartThingServerCloudlet(List<FogDevice> serverCloudlets, 
			List<ApDevice> apDevices, int migPointPolicy, int policyReplicaVM) {
		super();
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setMigPointPolicy(migPointPolicy);
		setPolicyReplicaVM(policyReplicaVM);
		// TODO Auto-generated constructor stub
	}

	

	@Override
	public boolean shouldMigrate(MobileDevice smartThing) {
		// TODO Auto-generated method stub

		if(smartThing.getSpeed()==0){//smartThing is mobile
//				System.out.println("LDSTSC SmartThing is not mobile");
			return false;//no migration
		}

		setCorrentAP(smartThing.getSourceAp());			
		setSmartThingPosition(DiscoverLocalization.discoverLocal(getCorrentAP().getCoord()
						, smartThing.getCoord()));//return the relative position between Access point and smart thing -> set this value

		smartThing.getMigrationTechnique().verifyPoints(smartThing, getSmartThingPosition());
//		if(getCorrentAP().getServerCloudlet().equals(smartThing.getVmLocalServerCloudlet())){//the handoff already has occur. The worst case
			if(!(smartThing.isMigPoint() && smartThing.isMigZone())){
//							System.out.println("LDSTSC SmartThing is not on migration zone...");
				return false;//no migration
			}
			else{
				setNextServerClouletId(Migration.nextServerCloudlet(serverCloudlets, smartThing));//choose the closest cloudlet, it returns the Id or -1 
				if(getNextServerClouletId() < 0){// Does next ServerCloudlet exist?
//							System.out.println("LDSTSC Does not exist nextServerCloulet");
					return false;
				}
				List<ApDevice> tempListAps = new ArrayList<>(); // It creates a temporary List to invoke the nextAp
				for(ApDevice ap: serverCloudlets.get(getNextServerClouletId()).getApDevices()){ 
					tempListAps.add(ap);
				}
				setNextApId(Migration.nextAp(tempListAps, smartThing));		
				if(getNextApId() < 0){//index is negative -> A migração não deve ocorrer, pois caso o st faça um handoff, não será para nenhum ap deste ServerCloudlet
//						System.out.println("LDSTSC Does not exist nextAp");
//					return false;//no migration
				}
				else if(!Migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
//					System.out.println("LDSTSC #########Ap is not Edge###### "+smartThing.getName());
					return false;//no migration
				}	
			}
//		}
//		else{ 
////			if(!Migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
////				//		System.out.println("#########Ap is not Edge######");
////				return false;//no migration
////			}
////			else{
//			if(!(isMigPoint() && isMigZone())){
//				//			System.out.println("SmartThing is not on migration zone...");
//				return false;//no migration
//			}
//				setNextServerClouletId(getCorrentAP().getServerCloudlet().getMyId());
//				System.out.println("Clock: " + CloudSim.clock()+"HANDOFF JA OCORREU.... ou Delivery ja ocorreu: "+ smartThing.getName());
//			}
//		}

		return ServiceAgreement.serviceAgreement(serverCloudlets.get(getNextServerClouletId()), smartThing);
	}



	public  List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}



	public  void setServerCloudlets(List<FogDevice> serverCloudlets) {
		this.serverCloudlets = serverCloudlets;
	}



	public  List<ApDevice> getApDevices() {
		return apDevices;
	}



	public  void setApDevices(List<ApDevice> apDevices) {
		this.apDevices = apDevices;
	}



	public  int getMigPointPolicy() {
		return migPointPolicy;
	}



	public  void setMigPointPolicy(int migPointPolicy) {
		this.migPointPolicy = migPointPolicy;
	}



	public  ApDevice getCorrentAP() {
		return correntAP;
	}



	public  void setCorrentAP(ApDevice correntAP) {
		this.correntAP = correntAP;
	}



	public  int getNextApId() {
		return nextApId;
	}



	public  void setNextApId(int nextApId) {
		this.nextApId = nextApId;
	}



	public  int getNextServerClouletId() {
		return nextServerClouletId;
	}



	public  void setNextServerClouletId(int nextServerClouletId) {
		this.nextServerClouletId = nextServerClouletId;
	}



	public  int getSmartThingPosition() {
		return smartThingPosition;
	}



	public  void setSmartThingPosition(int smartThingPosition) {
		this.smartThingPosition = smartThingPosition;
	}



	public  boolean isMigZone() {
		return migZone;
	}



	public  void setMigZone(boolean migZone) {
		this.migZone = migZone;
	}



	public  boolean isMigPoint() {
		return migPoint;
	}



	public  void setMigPoint(boolean migPoint) {
		this.migPoint = migPoint;
	}



	public int getPolicyReplicaVM() {
		return policyReplicaVM;
	}



	public void setPolicyReplicaVM(int policyReplicaVM) {
		this.policyReplicaVM = policyReplicaVM;
	}

}
