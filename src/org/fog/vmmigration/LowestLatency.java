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
	}

	@Override
	public boolean shouldMigrate(MobileDevice smartThing) {
		if (smartThing.getSpeed() == 0) {// smartThing is mobile
			return false;// no migration
		}
		setCorrentAP(smartThing.getSourceAp());
		// return the relative position between access point and smart thing -> set this value
		setSmartThingPosition(DiscoverLocalization.discoverLocal(getCorrentAP().getCoord(), smartThing.getCoord()));

		smartThing.getMigrationTechnique().verifyPoints(smartThing, getSmartThingPosition());

		if (!(smartThing.isMigPoint() && smartThing.isMigZone())) {
			return false;// no migration
		}
		else {
			setNextServerClouletId(Migration.lowestLatencyCostServerCloudlet(serverCloudlets, apDevices, smartThing));
			if (getNextServerClouletId() < 0) {
				return false;
			}
			else {
				setNextApId(Migration.nextAp(apDevices, smartThing));
				if (getNextApId() >= 0) {
					// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
					if (!Migration.isEdgeAp(apDevices.get(getNextApId()), smartThing)) {
						return false;// no migration
					}
				}
			}
		}
		return ServiceAgreement.serviceAgreement(serverCloudlets.get(getNextServerClouletId()),
			smartThing);
	}

	public ApDevice getCorrentAP() {
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
