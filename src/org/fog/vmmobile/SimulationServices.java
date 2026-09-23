package org.fog.vmmobile;

import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.entities.TupleRoutingService;
import org.fog.entities.HandoffCoordinator;
import org.fog.placement.AccessPointAssociationService;
import org.fog.placement.MobileAssociationService;
import org.fog.placement.MobilityService;
import org.fog.placement.SimulationResultsService;
import org.fog.vmmigration.MigrationCoordinator;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.adapter.CloudSimAdapter;
import org.fog.vmmobile.adapter.LegacySimulationAdapters;
import org.fog.vmmobile.adapter.MobileLifecycleAdapter;
import org.fog.vmmobile.adapter.MyStatisticsAdapter;
import org.fog.vmmobile.adapter.NetworkSliceAdapter;
import org.fog.vmmobile.adapter.RunOutputAdapter;
import org.fog.vmmobile.port.CloudSimPort;
import org.fog.vmmobile.port.MobileLifecyclePort;
import org.fog.vmmobile.port.MobileStatisticsPort;
import org.fog.vmmobile.port.NetworkSlicePort;
import org.fog.vmmobile.port.SimulationEventLog;
import org.fog.vmmobile.port.SimulationOutput;

/**
 * Constructor-injected service graph owned by exactly one simulation run.
 *
 * <p>The compatibility factory exists only while legacy entities cannot all be
 * constructed by {@link SimulationContext}. New orchestration code should
 * receive this object from the context instead.</p>
 */
public final class SimulationServices {
	private final CloudSimPort cloudSim;
	private final TopologyService topology;
	private final MobilityService mobility;
	private final MobileAssociationService association;
	private final AccessPointAssociationService accessPointAssociation;
	private final HandoffCoordinator handoff;
	private final MigrationCoordinator migration;
	private final TupleRoutingService tupleRouting;
	private final SimulationResultsService results;

	public SimulationServices(CloudSimPort cloudSim,
		MobileLifecyclePort lifecycle, NetworkSlicePort networkSlices,
		MobileStatisticsPort statistics, SimulationEventLog events,
		SimulationOutput output) {
		if (cloudSim == null || lifecycle == null || networkSlices == null
			|| statistics == null || events == null || output == null) {
			throw new IllegalArgumentException(
				"Simulation service dependencies cannot be null");
		}
		this.cloudSim = cloudSim;
		this.topology = new TopologyService(cloudSim, output);
		this.mobility = new MobilityService(lifecycle);
		this.association = new MobileAssociationService(cloudSim, lifecycle,
			networkSlices, statistics);
		this.accessPointAssociation = new AccessPointAssociationService(cloudSim);
		this.handoff = new HandoffCoordinator(cloudSim, networkSlices, events);
		this.migration = new MigrationCoordinator(cloudSim, networkSlices,
			statistics, events, output);
		this.tupleRouting = new TupleRoutingService(cloudSim);
		this.results = new SimulationResultsService(events);
	}

	/** Builds the production graph around objects owned by a run context. */
	static SimulationServices forRun(CloudSimPort cloudSim,
		MyStatistics statistics, SimulationEventSink events,
		RunOutputManager output) {
		return new SimulationServices(cloudSim, MobileLifecycleAdapter.INSTANCE,
			NetworkSliceAdapter.INSTANCE, new MyStatisticsAdapter(statistics), events,
			new RunOutputAdapter(output));
	}

	/** Temporary bridge for tests and legacy constructors outside a run context. */
	public static SimulationServices legacy() {
		return new SimulationServices(CloudSimAdapter.INSTANCE,
			MobileLifecycleAdapter.INSTANCE, NetworkSliceAdapter.INSTANCE,
			LegacySimulationAdapters.statistics(), LegacySimulationAdapters.events(),
			LegacySimulationAdapters.output());
	}

	public CloudSimPort getCloudSim() {
		return cloudSim;
	}

	public TopologyService getTopology() {
		return topology;
	}

	public MobilityService getMobility() {
		return mobility;
	}

	public MobileAssociationService getAssociation() {
		return association;
	}

	public AccessPointAssociationService getAccessPointAssociation() {
		return accessPointAssociation;
	}

	public HandoffCoordinator getHandoff() {
		return handoff;
	}

	public MigrationCoordinator getMigration() {
		return migration;
	}

	public TupleRoutingService getTupleRouting() {
		return tupleRouting;
	}

	public SimulationResultsService getResults() {
		return results;
	}
}
