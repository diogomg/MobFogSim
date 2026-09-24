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

public class LowestDistBwSmartThingServerCloudlet implements DecisionMigration {

	private List<FogDevice> serverCloudlets;
	private List<ApDevice> apDevices;
	private MigrationPointPolicy migPointPolicy;
	private ApDevice currentAP;
	private ApDevice nextAp;
	private FogDevice nextServerCloudlet;
	private MovementDirection smartThingPosition;
	private boolean migZone;
	private boolean migPoint;
	private MigrationTechniquePolicy policyReplicaVM;

	public LowestDistBwSmartThingServerCloudlet(List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, int migPointPolicy, int policyReplicaVM) {
		this(serverCloudlets, apDevices,
			MigrationPointPolicy.fromLegacy(migPointPolicy),
			MigrationTechniquePolicy.fromLegacy(policyReplicaVM));
	}

	public LowestDistBwSmartThingServerCloudlet(List<FogDevice> serverCloudlets,
		List<ApDevice> apDevices, MigrationPointPolicy migPointPolicy,
		MigrationTechniquePolicy policyReplicaVM) {
		this.serverCloudlets = new ArrayList<FogDevice>(serverCloudlets);
		this.apDevices = new ArrayList<ApDevice>(apDevices);
		if (migPointPolicy == null || policyReplicaVM == null) {
			throw new IllegalArgumentException("Migration policies cannot be null");
		}
		this.migPointPolicy = migPointPolicy;
		this.policyReplicaVM = policyReplicaVM;
	}

	@Override
	public MigrationDecision evaluate(MobileDevice smartThing,
		MigrationDecisionContext context) {
		if (smartThing == null || context == null) {
			throw new IllegalArgumentException(
				"Migration evaluation inputs cannot be null");
		}
		if (smartThing.getSpeed() == 0) {// smartThing is mobile
			return MigrationDecision.stay(smartThing, null,
				context.getPredictions(), "mobile device is stationary");
		}

		ApDevice currentAp = smartThing.getSourceAp();
		if (currentAp == null) {
			return MigrationDecision.stay(smartThing, null,
				context.getPredictions(), "mobile device has no access point");
		}
		// return the relative position between Access point and smart thing -> set this value
		MovementDirection relativeDirection = DiscoverLocalization.discoverDirection(
			currentAp.getCoord(), smartThing.getCoord());

		MigrationPointEvaluation points = smartThing.getMigrationTechnique()
			.evaluatePoints(smartThing, relativeDirection);
		// handoff already has occur. The worst case
		if (!points.allowsMigration()) {
			return MigrationDecision.stay(smartThing, points,
				context.getPredictions(), "outside migration point or direction cone");
		}
		else {
			Optional<FogDevice> selectedServerCloudlet =
				Migration.nextServerCloudlet(serverCloudlets, smartThing, context);
			if (!selectedServerCloudlet.isPresent()) {
				return MigrationDecision.stay(smartThing, points,
					context.getPredictions(), "no server candidate is available");
			}
			FogDevice nextServer = selectedServerCloudlet.get();
			// It creates a temporary List to invoke the nextAp
			List<ApDevice> tempListAps = new ArrayList<>();
			for (ApDevice ap : nextServer.getApDevices()) {
				tempListAps.add(ap);
			}
			Optional<ApDevice> selectedAp = Migration.nextAp(tempListAps, smartThing);
			if (!selectedAp.isPresent()) {
				return MigrationDecision.stay(smartThing, points,
					context.getPredictions(), "no access-point candidate is available");
			}
			ApDevice nextAccessPoint = selectedAp.get();
			// verify if the next Ap is edge (return false if the ServerCloudlet destination is the same ServerCloud source)
			if (!Migration.isEdgeAp(nextAccessPoint, smartThing)) {
				return MigrationDecision.stay(smartThing, points,
					context.getPredictions(), "next access point uses the current server");
			}
			Optional<FogDevice> destination = MobileEdgeHostSelector.chooseDestination(
				smartThing, nextServer, context.getActiveMobileDevices(),
				context.getDestinationPolicy());
			return destination.isPresent()
				? MigrationDecision.migrate(smartThing, destination.get(), points,
					context.getPredictions())
				: MigrationDecision.stay(smartThing, points,
					context.getPredictions(), "no eligible VM destination");
		}
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

	public ApDevice getCurrentAP() {
		return currentAP;
	}

	/** @deprecated Use {@link #getCurrentAP()}. */
	@Deprecated
	public ApDevice getCorrentAP() {
		return getCurrentAP();
	}

	public void setCurrentAP(ApDevice currentAP) {
		this.currentAP = currentAP;
	}

	/** @deprecated Use {@link #setCurrentAP(ApDevice)}. */
	@Deprecated
	public void setCorrentAP(ApDevice currentAP) {
		setCurrentAP(currentAP);
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
