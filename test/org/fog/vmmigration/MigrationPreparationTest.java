package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Collections;

import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.Before;
import org.junit.Test;

public class MigrationPreparationTest {

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
	}

	@Test
	public void containerConnectionSucceedsOnFirstAttempt() {
		ConnectionPreparationResult result = new PrepareContainerVM()
			.prepareConnection(null, null);

		assertTrue(result.isConnected());
		assertEquals(10.0, result.getDelay(), 0.0);
	}

	@Test
	public void containerConnectionAccountsForFailuresBeforeSuccess() {
		PrepareContainerVM preparation = new PrepareContainerVM(
			succeedOnAttempt(3));

		ConnectionPreparationResult result = preparation.prepareConnection(null, null);

		assertTrue(result.isConnected());
		assertEquals(70.0, result.getDelay(), 0.0);
	}

	@Test
	public void containerConnectionStopsAfterFiveFailures() {
		CountingConnectionAttemptPolicy attempts = new CountingConnectionAttemptPolicy(false);
		PrepareContainerVM preparation = new PrepareContainerVM(attempts);

		ConnectionPreparationResult result = preparation.prepareConnection(null, null);

		assertFalse(result.isConnected());
		assertEquals(150.0, result.getDelay(), 0.0);
		assertEquals(5, attempts.getAttempts());
	}

	@Test
	public void liveConnectionUsesTheSameFiveAttemptPolicy() {
		CountingConnectionAttemptPolicy attempts = new CountingConnectionAttemptPolicy(true);
		PrepareLiveMigration preparation = new PrepareLiveMigration(attempts);

		ConnectionPreparationResult result = preparation.prepareConnection(null, null);

		assertTrue(result.isConnected());
		assertEquals(130.0, result.getDelay(), 0.0);
		assertEquals(5, attempts.getAttempts());
	}

	@Test
	public void completeConnectionStopsAfterThreeFailures() {
		CountingConnectionAttemptPolicy attempts = new CountingConnectionAttemptPolicy(false);
		PrepareCompleteVM preparation = new PrepareCompleteVM(attempts);

		ConnectionPreparationResult result = preparation.prepareConnection(null, null);

		assertFalse(result.isConnected());
		assertEquals(90.0, result.getDelay(), 0.0);
		assertEquals(3, attempts.getAttempts());
	}

	@Test
	public void independentRequestsDoNotAccumulateConnectionDelay() {
		AbstractMigrationPreparation[] preparations = {
			new PrepareCompleteVM(succeedOnAttempt(2)),
			new PrepareContainerVM(succeedOnAttempt(2)),
			new PrepareLiveMigration(succeedOnAttempt(2))
		};

		for (AbstractMigrationPreparation preparation : preparations) {
			assertEquals(40.0, preparation.prepareConnection(null, null).getDelay(), 0.0);
			assertEquals(40.0, preparation.prepareConnection(null, null).getDelay(), 0.0);
		}
	}

	@Test
	public void dataPreparationIncludesRequestLocalRetriesAndTopologyLatency() {
		FogDevice source = new FixedCpuTimeFogDevice("source", 5.0);
		FogDevice destination = new FogDevice("destination", 0, 0, 1);
		MobileDevice mobileDevice = mobileDevice(source, destination);
		PrepareCompleteVM preparation = new FixedTopologyLatencyPreparation(
			succeedOnAttempt(2), 7.0);

		assertEquals(52.0, preparation.dataprepare(mobileDevice), 0.0);
		assertEquals(52.0, preparation.dataprepare(mobileDevice), 0.0);
	}

	@Test
	public void failedConnectionAbortsDataPreparation() {
		MobileDevice mobileDevice = mobileDevice(
			new FogDevice("source", 0, 0, 0),
			new FogDevice("destination", 0, 0, 1));

		assertEquals(-1.0, new PrepareCompleteVM(succeedOnAttempt(4))
			.dataprepare(mobileDevice), 0.0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNullConnectionAttemptPolicy() {
		new PrepareCompleteVM(null);
	}

	@Test
	public void duringMigrationManagementCompletes() {
		assertTrue(new DuringMigration().managermentBetweeServerCloudlets());
	}

	private static ConnectionAttemptPolicy succeedOnAttempt(final int successfulAttempt) {
		return new ConnectionAttemptPolicy() {
			@Override
			public boolean tryOpenConnection(FogDevice sourceServerCloudlet,
				FogDevice destinationServerCloudlet, int attemptNumber) {
				return attemptNumber == successfulAttempt;
			}
		};
	}

	private static MobileDevice mobileDevice(FogDevice source, FogDevice destination) {
		MobileDevice mobileDevice = new MobileDevice("mobile", 0, 0, 0, 0, 0);
		mobileDevice.setVmLocalServerCloudlet(source);
		mobileDevice.setDestinationServerCloudlet(destination);
		mobileDevice.setVmMobileDevice(new Vm(1, 1, 1000, 1, 128, 1000, 1,
			"Xen", new CloudletSchedulerTimeShared()));
		return mobileDevice;
	}

	private static final class CountingConnectionAttemptPolicy
		implements ConnectionAttemptPolicy {
		private final boolean succeedOnFifthAttempt;
		private int attempts;

		CountingConnectionAttemptPolicy(boolean succeedOnFifthAttempt) {
			this.succeedOnFifthAttempt = succeedOnFifthAttempt;
		}

		@Override
		public boolean tryOpenConnection(FogDevice sourceServerCloudlet,
			FogDevice destinationServerCloudlet, int attemptNumber) {
			attempts++;
			return succeedOnFifthAttempt && attemptNumber == 5;
		}

		int getAttempts() {
			return attempts;
		}
	}

	private static final class FixedTopologyLatencyPreparation extends PrepareCompleteVM {
		private final double topologyLatency;

		FixedTopologyLatencyPreparation(ConnectionAttemptPolicy connectionAttemptPolicy,
			double topologyLatency) {
			super(connectionAttemptPolicy);
			this.topologyLatency = topologyLatency;
		}

		@Override
		protected double getTopologyLatency(FogDevice sourceServerCloudlet,
			FogDevice destinationServerCloudlet) {
			return topologyLatency;
		}
	}

	private static final class FixedCpuTimeFogDevice extends FogDevice {
		private final DatacenterCharacteristics characteristics;

		FixedCpuTimeFogDevice(String name, double cpuTime) {
			super(name, 0, 0, 0);
			characteristics = new FixedCpuTimeCharacteristics(cpuTime);
		}

		@Override
		public DatacenterCharacteristics getCharacteristics() {
			return characteristics;
		}
	}

	private static final class FixedCpuTimeCharacteristics
		extends DatacenterCharacteristics {
		private final double cpuTime;

		FixedCpuTimeCharacteristics(double cpuTime) {
			super("x86", "Linux", "Xen", Collections.<Host>emptyList(),
				0.0, 0.0, 0.0, 0.0, 0.0);
			this.cpuTime = cpuTime;
		}

		@Override
		public double getCpuTime(double cloudletLength, double load) {
			return cpuTime;
		}
	}
}
