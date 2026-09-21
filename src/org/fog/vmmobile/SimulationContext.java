package org.fog.vmmobile;

import java.io.IOException;
import java.util.Random;

import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.util.BufferedFileManager;
import org.cloudbus.cloudsim.util.RunOutputManager;
import org.fog.placement.AtomicResultWriter;
import org.fog.placement.MobileController;
import org.fog.placement.ResultWriter;
import org.fog.placement.RunReport;
import org.fog.placement.SimulationMetricsSnapshot;
import org.fog.utils.NetworkSlicing;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmigration.VmDestinationPolicy;

/**
 * Owns all mutable services and registries that belong to one embedded run.
 *
 * <p>The underlying simulator still exposes legacy static entry points. They
 * are activated against this context for the duration of a run and detached
 * on close. Concurrent simulations in one JVM are deliberately rejected.</p>
 */
public final class SimulationContext implements AutoCloseable {
	private static SimulationContext activeContext;

	private final SimulationConfig configuration;
	private final SimulationTopology topology = new SimulationTopology();
	private final SimulationIdentifiers identifiers = new SimulationIdentifiers();
	private final RandomStreams randomStreams;
	private final TimeKeeper timeKeeper = new TimeKeeper();
	private final MyStatistics statistics = new MyStatistics();
	private final NetworkUsageMonitor.Metrics networkMetrics =
		new NetworkUsageMonitor.Metrics();
	private final NetworkSlicing.RuntimeState slicingState;
	private final SimulationClock clock;
	private SimulationProgressBar progressBar;
	private SimulationEventSink eventSink;
	private RunOutputManager outputManager;
	private ResultWriter resultWriter;
	private SimulationMetricsSnapshot metricsSnapshot;
	private SimulationTopologySize topologySize;
	private int cloudSimEntityCount;
	private boolean closed;

	private SimulationContext(SimulationConfig configuration,
		SimulationClock clock) {
		if (configuration == null || clock == null) {
			throw new IllegalArgumentException(
				"Simulation configuration and clock cannot be null");
		}
		this.configuration = configuration;
		this.clock = clock;
		this.randomStreams = new RandomStreams(configuration.getSeed());
		this.slicingState = NetworkSlicing.createRuntimeState(
			configuration.getSlicingConfiguration());
	}

	public static synchronized SimulationContext open(
		SimulationConfig configuration) {
		return open(configuration, SimulationClock.SYSTEM);
	}

	static synchronized SimulationContext open(SimulationConfig configuration,
		SimulationClock clock) {
		if (activeContext != null) {
			throw new IllegalStateException(
				"A SimulationContext is already active in this JVM");
		}
		SimulationContext context = new SimulationContext(configuration, clock);
		activeContext = context;
		try {
			NetworkTopology.reset();
			MobileController.resetRunState();
			TimeKeeper.setInstance(context.timeKeeper);
			MyStatistics.setInstance(context.statistics);
			NetworkUsageMonitor.useMetrics(context.networkMetrics);
			NetworkSlicing.useRuntimeState(context.slicingState);
			VmDestinationPolicy.configure(configuration.getVmDestination());
			context.outputManager = RunOutputManager.initialize(
				configuration.getOutputDirectory(), configuration.getOutputMode());
			context.eventSink = SimulationEventSink.open(context.outputManager,
				System.out);
			SimulationEventSink.use(context.eventSink);
			context.progressBar = new SimulationProgressBar(System.out,
				context.eventSink.isEnabled(SimulationEventSink.Level.SUMMARY));
			context.resultWriter = new AtomicResultWriter(context.outputManager);
			AppExample.useSimulationContext(context);
			return context;
		}
		catch (RuntimeException error) {
			try {
				context.close();
			}
			catch (RuntimeException cleanupError) {
				error.addSuppressed(cleanupError);
			}
			throw error;
		}
	}

	public static synchronized SimulationContext currentOrNull() {
		return activeContext;
	}

	public static synchronized SimulationContext requireCurrent() {
		if (activeContext == null) {
			throw new IllegalStateException("No SimulationContext is active");
		}
		return activeContext;
	}

	public SimulationConfig getConfiguration() {
		return configuration;
	}

	public SimulationTopology getTopology() {
		return topology;
	}

	public SimulationIdentifiers getIdentifiers() {
		return identifiers;
	}

	public TimeKeeper getTimeKeeper() {
		return timeKeeper;
	}

	public MyStatistics getStatistics() {
		return statistics;
	}

	public NetworkUsageMonitor.Metrics getNetworkMetrics() {
		return networkMetrics;
	}

	public NetworkSlicing.RuntimeState getSlicingState() {
		return slicingState;
	}

	public RunOutputManager getOutputManager() {
		return outputManager;
	}

	public ResultWriter getResultWriter() {
		return resultWriter;
	}

	public SimulationEventSink getEventSink() {
		return eventSink;
	}

	public SimulationClock getClock() {
		return clock;
	}

	public Random random(String streamName) {
		return randomStreams.get(streamName);
	}

	public static Random currentRandom(String streamName) {
		return requireCurrent().random(streamName);
	}

	public void startProgress(double finalSimulationTime) {
		progressBar.start(finalSimulationTime);
	}

	public void updateProgress(double simulationTime) {
		progressBar.update(simulationTime);
	}

	public void completeProgress() {
		progressBar.complete();
	}

	public void recordMetrics(SimulationMetricsSnapshot metrics) {
		if (metrics == null) {
			throw new IllegalArgumentException("Simulation metrics cannot be null");
		}
		if (metricsSnapshot != null) {
			throw new IllegalStateException("Simulation metrics were already recorded");
		}
		metricsSnapshot = metrics;
		cloudSimEntityCount = CloudSim.getNumEntities();
	}

	/** Closes streamed detail buffers, then atomically publishes the final report. */
	public void publishResults() throws IOException {
		if (metricsSnapshot == null) {
			throw new IllegalStateException(
				"Cannot publish results before metrics are recorded");
		}
		eventSink.finish();
		BufferedFileManager.closeAll();
		resultWriter.write(RunReport.capture(configuration, metricsSnapshot));
	}

	/** Records a terminal failure without masking the original simulation error. */
	public void recordFailure(Throwable failure) {
		if (failure == null || resultWriter == null || resultWriter.isTerminal()) {
			return;
		}
		try {
			resultWriter.writeFailure(RunReport.metadata(configuration), failure);
		}
		catch (IOException manifestError) {
			failure.addSuppressed(manifestError);
		}
		catch (RuntimeException manifestError) {
			failure.addSuppressed(manifestError);
		}
	}

	/** Freezes the initially constructed physical and logical topology sizes. */
	void recordInitialTopologySize() {
		if (topologySize != null) {
			throw new IllegalStateException("Simulation topology size was already recorded");
		}
		topologySize = SimulationTopologySize.capture(topology);
	}

	public SimulationRunResult result() {
		if (metricsSnapshot == null) {
			throw new IllegalStateException(
				"The simulation ended without recording a metric snapshot");
		}
		if (topologySize == null) {
			throw new IllegalStateException(
				"The simulation started without recording its topology size");
		}
		return new SimulationRunResult(metricsSnapshot, CloudSim.getEventCounters(),
			cloudSimEntityCount, identifiers, topologySize);
	}

	@Override
	public void close() {
		synchronized (SimulationContext.class) {
			if (closed) {
				return;
			}
			if (activeContext != this) {
				throw new IllegalStateException(
					"Only the active SimulationContext can be closed");
			}
			RuntimeException failure = null;
			try {
				if (progressBar != null) {
					progressBar.close();
				}
			}
			catch (RuntimeException error) {
				failure = error;
			}
			try {
				if (eventSink != null) {
					eventSink.close();
				}
			}
			catch (RuntimeException error) {
				if (failure == null) {
					failure = error;
				}
				else {
					failure.addSuppressed(error);
				}
			}
			try {
				BufferedFileManager.closeAll();
			}
			catch (RuntimeException error) {
				if (failure == null) {
					failure = error;
				}
				else {
					failure.addSuppressed(error);
				}
			}
			try {
				SimulationEventSink.reset();
				AppExample.releaseSimulationContext(this);
				MobileController.resetRunState();
				NetworkTopology.reset();
				NetworkSlicing.useDefaultRuntimeState();
				NetworkUsageMonitor.reset();
				TimeKeeper.setInstance(null);
				MyStatistics.setInstance(null);
				RunOutputManager.resetToDefault();
				VmDestinationPolicy.configure(
					VmDestinationPolicy.Destination.HYBRID);
			}
			finally {
				activeContext = null;
				closed = true;
			}
			if (failure != null) {
				throw failure;
			}
		}
	}
}
