package org.fog.vmmigration;

import java.util.List;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.DiscoverLocalization;

public class LowestLatency implements DecisionMigration {

	private List<FogDevice> serverCloudlets;
	private List<ApDevice> apDevices;
	private int migPointPolicy;
	private ApDevice correntAP;
	private int nextApId;
	private int nextServerClouletId;
	private int policyReplicaVM;


	private int smartThingPosition;
	private boolean migZone;
	private boolean migPoint;

	public LowestLatency(List<FogDevice> serverCloudlets, 
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
			//	System.out.println("SmartThing is not mobile");
			return false;//no migration
		}
		setCorrentAP(smartThing.getSourceAp());
		setSmartThingPosition(
				DiscoverLocalization.discoverLocal(getCorrentAP().getCoord()
						, smartThing.getCoord()));//return the relative position between Access point and smart thing -> set this value		
			
		smartThing.getMigrationTechnique().verifyPoints(smartThing, getSmartThingPosition());
				
//		if(getCorrentAP().getServerCloudlet().equals(smartThing.getVmLocalServerCloudlet())){//the handoff already has occur. The worst case

			if(!(smartThing.isMigPoint() && smartThing.isMigZone())){
				//			System.out.println("SmartThing is not on migration zone...");
				return false;//no migration
			}
			else{
				setNextServerClouletId(Migration.lowestLatencyCostServerCloudlet(serverCloudlets, apDevices, smartThing));
				if(getNextServerClouletId()<0){
					//				System.out.println("Does not exist nextServerCloulet");
					return false;
				}
				else{
//					List<ApDevice> tempListAps = new ArrayList<>(); // It creates a temporary List to invoke the nextAp
//					for(ApDevice ap: serverCloudlets.get(getNextServerClouletId()).getApDevices()){ 
//						tempListAps.add(ap);
//					}
					setNextApId( Migration.nextAp(apDevices, smartThing));//tempListAps, smartThing));		
					if(getNextApId() < 0){//index is negative -> A migração não deve ocorrer, pois caso o st faça um handoff, não será para nenhum ap deste ServerCloudlet
						//	System.out.println("Does not exist nextAp");
//						return false;//no migration
					}
					else{
						if(! Migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
							//						System.out.println("#########Ap is not Edge######");
							return false;//no migration
						}	
					}
				}
			}
//		}
//		else{
//			if(!Migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)){// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
//				//		System.out.println("#########Ap is not Edge######");
//				return false;//no migration
//			}
//			else{
			
				//Actual = apSourceLatency+localVmLatency+networkBetweenCloudlets+MipsVmLocal
				//new = apSourceLatency+SourceClouletLatency+mipsSourceCloudlet
//				double actualCost = NetworkTopology.getDelay(smartThing.getId(), getCorrentAP().getId()) 
//						 + NetworkTopology.getDelay(smartThing.getSourceAp().getId(),getCorrentAP().getServerCloudlet().getId())
//						 + 1
//						 + NetworkTopology.getDelay(getCorrentAP().getServerCloudlet().getId(),smartThing.getVmLocalServerCloudlet().getId())
//						 + (1.0/smartThing.getVmLocalServerCloudlet().getHost().getAvailableMips())
//				 		 + LatencyByDistance.latencyConnection(smartThing.getVmLocalServerCloudlet(), smartThing); 
//							
						
//						smartThing.getSourceAp().getUplinkLatency() +
//									smartThing.getSourceServerCloudlet().getUplinkLatency() +
//									NetworkTopology.getDelay(smartThing.getSourceServerCloudlet().getId(),smartThing.getVmLocalServerCloudlet().getId()) +
//									(1.0/smartThing.getVmLocalServerCloudlet().getHost().getAvailableMips());
									
//				double newCost =  NetworkTopology.getDelay(smartThing.getId(), getCorrentAP().getId()) 
//						 + NetworkTopology.getDelay(smartThing.getSourceAp().getId(),getCorrentAP().getServerCloudlet().getId())
//						 + (1.0/getCorrentAP().getServerCloudlet().getHost().getAvailableMips())
//						 + LatencyByDistance.latencyConnection(getCorrentAP().getServerCloudlet(), smartThing);
//						
						
//						smartThing.getSourceAp().getUplinkLatency() +
//							     smartThing.getSourceServerCloudlet().getUplinkLatency() +
//							     (1.0/smartThing.getSourceServerCloudlet().getHost().getAvailableMips());
				
//				if(newCost<actualCost){
//					if(!(isMigPoint() && isMigZone())){
//						return false;//no migration
//					}					
//					setNextServerClouletId(getCorrentAP().getServerCloudlet().getMyId());
//					System.out.println("Clock: " + CloudSim.clock()+"HANDOFF JA OCORREU.... ou Delivery ja ocorreu: "+ smartThing.getName());
//					System.out.println("Novo custo eh menor");
//				}
//				else{
//					return false;
//				}					
//			}
		
		return ServiceAgreement.serviceAgreement(serverCloudlets.get(getNextServerClouletId()), smartThing);
	}

	public  ApDevice getCorrentAP() {
		return correntAP;
	}


	public List<FogDevice> getServerCloudlets() {
		return serverCloudlets;
	}


	public void setServerCloudlets(List<FogDevice> serverCloudlets) {
		this.serverCloudlets = serverCloudlets;
	}


	public List<ApDevice> getApDevices() {
		return apDevices;
	}


	public void setApDevices(List<ApDevice> apDevices) {
		this.apDevices = apDevices;
	}


	public int getMigPointPolicy() {
		return migPointPolicy;
	}


	public void setMigPointPolicy(int migPointPolicy) {
		this.migPointPolicy = migPointPolicy;
	}


	public int getNextApId() {
		return nextApId;
	}


	public void setNextApId(int nextApId) {
		this.nextApId = nextApId;
	}


	public int getNextServerClouletId() {
		return nextServerClouletId;
	}


	public void setNextServerClouletId(int nextServerClouletId) {
		this.nextServerClouletId = nextServerClouletId;
	}


	public int getSmartThingPosition() {
		return smartThingPosition;
	}


	public void setSmartThingPosition(int smartThingPosition) {
		this.smartThingPosition = smartThingPosition;
	}


	public boolean isMigZone() {
		return migZone;
	}


	public void setMigZone(boolean migZone) {
		this.migZone = migZone;
	}


	public boolean isMigPoint() {
		return migPoint;
	}


	public void setMigPoint(boolean migPoint) {
		this.migPoint = migPoint;
	}


	public void setCorrentAP(ApDevice correntAP) {
		this.correntAP = correntAP;
	}


	public int getPolicyReplicaVM() {
		return policyReplicaVM;
	}


	public void setPolicyReplicaVM(int policyReplicaVM) {
		this.policyReplicaVM = policyReplicaVM;
	}

}
