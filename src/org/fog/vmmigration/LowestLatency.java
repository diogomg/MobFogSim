package org.fog.vmmigration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.DiscoverLocalization;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.fog.vmmobile.policy.MovementDirection;

public class LowestLatency implements DecisionMigration {

	private List<FogDevice> serverCloudlets;
	private List<ApDevice> apDevices;
	private MigrationPointPolicy migPointPolicy;
	private ApDevice correntAP;
	private ApDevice nextAp;
	private FogDevice nextServerCloudlet;
	private MigrationTechniquePolicy policyReplicaVM;

	private MovementDirection smartThingPosition;
	private boolean migZone;
	private boolean migPoint;

	public LowestLatency(List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, int migPointPolicy, int policyReplicaVM) {
		this(serverCloudlets, apDevices,
			MigrationPointPolicy.fromLegacy(migPointPolicy),
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM));
	}

	public LowestLatency(List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, MigrationPointPolicy migPointPolicy,
		MigrationTechniquePolicy policyReplicaVM) {
		setServerCloudlets(serverCloudlets);
		setApDevices(apDevices);
		setMigPointPolicy(migPointPolicy);
		setMigrationTechniquePolicy(policyReplicaVM);
	}

	@Override
	public boolean shouldMigrate(MobileDevice smartThing) {
		if (smartThing.getSpeed() == 0) {// smartThing is mobile
			return false;// no migration
		}
		setCorrentAP(smartThing.getSourceAp());
		// return the relative position between access point and smart thing -> set this value
		setSmartThingDirection(DiscoverLocalization.discoverDirection(
			getCorrentAP().getCoord(), smartThing.getCoord()));

		smartThing.getMigrationTechnique().verifyPoints(smartThing,
			getSmartThingDirection());

		if (!(smartThing.isMigPoint() && smartThing.isMigZone())) {
			return false;// no migration
		}
		else {
			Optional<FogDevice> selectedServerCloudlet =
				Migration.lowestLatencyCostServerCloudlet(
					serverCloudlets, apDevices, smartThing);
			if (!selectedServerCloudlet.isPresent()) {
				return false;
			}
			setNextServerCloudlet(selectedServerCloudlet.get());
			Optional<ApDevice> selectedAp = Migration.nextAp(apDevices, smartThing);
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

	public ApDevice getCorrentAP() {
		return correntAP;
	}

	public List<FogDevice> getServerCloudlets() {
		return Collections.unmodifiableList(serverCloudlets);
	}

	public void setServerCloudlets(List<FogDevice> serverCloudlets) {
		this.serverCloudlets = new ArrayList<FogDevice>(serverCloudlets);
	}

	public List<ApDevice> getApDevices() {
		return Collections.unmodifiableList(apDevices);
	}

	public void setApDevices(List<ApDevice> apDevices) {
		this.apDevices = new ArrayList<ApDevice>(apDevices);
	}

	public int getMigPointPolicy() {
		return migPointPolicy.legacyValue();
	}

	public void setMigPointPolicy(int migPointPolicy) {
		setMigPointPolicy(MigrationPointPolicy.fromLegacy(migPointPolicy));
	}

	public MigrationPointPolicy getMigrationPointPolicy() {
		return migPointPolicy;
	}

	public void setMigPointPolicy(MigrationPointPolicy migPointPolicy) {
		if (migPointPolicy == null) {
			throw new IllegalArgumentException("Migration point policy cannot be null");
		}
		this.migPointPolicy = migPointPolicy;
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
		return smartThingPosition.legacyValue();
	}

	public void setSmartThingPosition(int smartThingPosition) {
		setSmartThingDirection(MovementDirection.fromLegacy(smartThingPosition));
	}

	public MovementDirection getSmartThingDirection() {
		return smartThingPosition;
	}

	public void setSmartThingDirection(MovementDirection smartThingPosition) {
		if (smartThingPosition == null) {
			throw new IllegalArgumentException("Relative direction cannot be null");
		}
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
		return policyReplicaVM.legacyValue();
	}

	public void setPolicyReplicaVM(int policyReplicaVM) {
		setMigrationTechniquePolicy(
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM));
	}

	public MigrationTechniquePolicy getMigrationTechniquePolicy() {
		return policyReplicaVM;
	}

	public void setMigrationTechniquePolicy(
		MigrationTechniquePolicy policyReplicaVM) {
		if (policyReplicaVM == null) {
			throw new IllegalArgumentException("Migration technique cannot be null");
		}
		this.policyReplicaVM = policyReplicaVM;
	}

}
