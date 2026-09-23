package org.fog.utils;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.junit.Before;
import org.junit.Test;

public class MigrationTransferSpecTest {

	private static final double DELTA = 0.000001;
	private FogDevice source;
	private FogDevice destination;
	private MobileDevice mobileDevice;

	@Before
	public void setUp() {
		Log.disable();
		CloudSim.init(1, Calendar.getInstance(), false);
		source = new FogDevice("source", 0, 0, 0);
		destination = new FogDevice("destination", 0, 0, 0);
		mobileDevice = new MobileDevice("mobile", 0, 0, 0, 0, 0);
		mobileDevice.setNetworkSliceId(2);
	}

	@Test
	public void preservesTransferAndNonTransferPhasesWithExplicitUnits() {
		MigrationTransferSpec spec = new MigrationTransferSpec(source, destination,
			mobileDevice, DataSize.ofBytes(4096.0),
			SimulationDuration.ofMilliseconds(12.5),
			SimulationDuration.ofMilliseconds(30.0), EntityId.of(source.getId()),
			42);

		assertEquals(4096.0, spec.getTransferBytes(), DELTA);
		assertEquals(12.5, spec.getFixedDelayMillis(), DELTA);
		assertEquals(30.0, spec.getPreparationDelayMillis(), DELTA);
		assertEquals(2, spec.getNetworkSliceId());
		assertEquals(DataSize.ofBytes(4096.0), spec.getTransferSize());
		assertEquals(SimulationDuration.ofMilliseconds(12.5),
			spec.getFixedDelay());
		assertEquals(SimulationDuration.ofMilliseconds(30.0),
			spec.getPreparationDelay());
		assertEquals(NetworkSliceId.of(2), spec.getNetworkSlice());
		assertEquals(EntityId.of(source.getId()),
			spec.getCompletionDestination());
	}

	@Test
	public void convertsMebibytesAndBitRatesWithoutMixingUnits() {
		double bytes = MigrationTransferSpec.mebibytesToBytes(2.0);

		assertEquals(2.0 * 1024.0 * 1024.0, bytes, DELTA);
		assertEquals(2000.0,
			MigrationTransferSpec.transferTimeMillis(bytes,
				8.0 * 1024.0 * 1024.0), DELTA);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonFiniteTransferBytes() {
		new MigrationTransferSpec(source, destination, mobileDevice,
			Double.NaN, 0.0, 0.0, source.getId(), 42);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegativeFixedDelay() {
		new MigrationTransferSpec(source, destination, mobileDevice,
			1.0, -1.0, 0.0, source.getId(), 42);
	}
}
