package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.DiscoverLocalization;

public class LowestDistBwSmartThingServerCloudlet implements DecisionMigration {

	private List<FogDevice> serverCloudlets;
	private List<ApDevice> apDevices;
	private int migPointPolicy;
	private ApDevice correntAP;
	private ApDevice nextAp;
	private FogDevice nextServerCloudlet;
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
	}

	@Override
	public boolean shouldMigrate(MobileDevice smartThing) {
		// TODO Auto-generated method stub

		if (smartThing.getSpeed() == 0) {// smartThing is mobile
			return false;// no migration
		}

		setCorrentAP(smartThing.getSourceAp());
		// return the relative position between Access point and smart thing -> set this value
		setSmartThingPosition(DiscoverLocalization.discoverLocal(getCorrentAP().getCoord(), smartThing.getCoord()));

		smartThing.getMigrationTechnique().verifyPoints(smartThing, getSmartThingPosition());
		// handoff already has occur. The worst case
		if (!(smartThing.isMigPoint() && smartThing.isMigZone())) {
			return false;// no migration
		}
		else {
			Optional<FogDevice> selectedServerCloudlet =
				Migration.nextServerCloudlet(serverCloudlets, smartThing);
			if (!selectedServerCloudlet.isPresent()) {
				return false;
			}
			setNextServerCloudlet(selectedServerCloudlet.get());
			// It creates a temporary List to invoke the nextAp
			List<ApDevice> tempListAps = new ArrayList<>();
			for (ApDevice ap : getNextServerCloudlet().getApDevices()) {
				tempListAps.add(ap);
			}
			Optional<ApDevice> selectedAp = Migration.nextAp(tempListAps, smartThing);
			if (!selectedAp.isPresent()) {
				return false;
			}
			setNextAp(selectedAp.get());
			// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
			if (!Migration.isEdgeAp(getNextAp(), smartThing)) {
				return false;// no migration
			}
		}
		return MobileEdgeHostSelector.selectDestination(smartThing,
			getNextServerCloudlet());
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

	public ApDevice getCorrentAP() {
		return correntAP;
	}

	public void setCorrentAP(ApDevice correntAP) {
		this.correntAP = correntAP;
	}

	public ApDevice getNextAp() {
		return nextAp;
	}

	public void setNextAp(ApDevice nextAp) {
		this.nextAp = nextAp;
	}

	public FogDevice getNextServerCloudlet() {
		return nextServerCloudlet;
	}

	public void setNextServerCloudlet(FogDevice nextServerCloudlet) {
		this.nextServerCloudlet = nextServerCloudlet;
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

	public int getPolicyReplicaVM() {
		return policyReplicaVM;
	}

	public void setPolicyReplicaVM(int policyReplicaVM) {
		this.policyReplicaVM = policyReplicaVM;
	}

}
