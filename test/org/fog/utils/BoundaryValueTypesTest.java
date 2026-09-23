package org.fog.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class BoundaryValueTypesTest {
	private static final double DELTA = 0.000001;

	@Test
	public void preservesIdentifierKindsWithoutInterchangeablePrimitives() {
		EntityId entity = EntityId.of(17);
		NetworkSliceId slice = NetworkSliceId.of(17);

		assertEquals(17, entity.intValue());
		assertEquals(17, slice.intValue());
		assertFalse(entity.equals(slice));
		assertEquals(EntityId.of(17), entity);
		assertEquals(NetworkSliceId.of(17), slice);
	}

	@Test
	public void convertsRatesSizesAndDurationsAtOneExplicitBoundary() {
		DataSize size = DataSize.ofMebibytes(2.0);
		DataRate rate = DataRate.ofMebibitsPerSecond(8.0);
		SimulationDuration duration = size.transferDurationAt(rate);

		assertEquals(2.0 * 1024.0 * 1024.0, size.toBytes(), DELTA);
		assertEquals(8.0 * 1024.0 * 1024.0, rate.toBitsPerSecond(), DELTA);
		assertEquals(2.0, duration.toSeconds(), DELTA);
		assertEquals(2000.0, duration.toMilliseconds(), DELTA);
	}

	@Test
	public void canonicalizesZeroValues() {
		assertSame(DataSize.ZERO, DataSize.ofBytes(0.0));
		assertSame(DataRate.ZERO, DataRate.ofBitsPerSecond(0.0));
		assertSame(SimulationDuration.ZERO,
			SimulationDuration.ofSeconds(0.0));
	}

	@Test
	public void networkUsageRetainsTypedDimensions() {
		NetworkTransferUsage usage = new NetworkTransferUsage(
			DataSize.ofBytes(16.0), SimulationDuration.ofMilliseconds(2.0),
			SimulationDuration.ofMilliseconds(3.0),
			SimulationDuration.ofMilliseconds(5.0));

		assertEquals(DataSize.ofBytes(16.0), usage.getTransferredData());
		assertEquals(SimulationDuration.ofMilliseconds(2.0),
			usage.getQueueDuration());
		assertEquals(SimulationDuration.ofMilliseconds(3.0),
			usage.getTransferDuration());
		assertEquals(SimulationDuration.ofMilliseconds(5.0),
			usage.getPropagationDuration());
		assertEquals(160.0, usage.getTotalUsageByteMilliseconds(), DELTA);
	}

	@Test
	public void rejectsInvalidPrimitiveValuesBeforeTheyCrossBoundaries() {
		assertRejected(new Runnable() {
			@Override public void run() { EntityId.of(-1); }
		});
		assertRejected(new Runnable() {
			@Override public void run() { NetworkSliceId.of(-1); }
		});
		assertRejected(new Runnable() {
			@Override public void run() { DataSize.ofBytes(Double.NaN); }
		});
		assertRejected(new Runnable() {
			@Override public void run() { DataRate.ofBitsPerSecond(-1.0); }
		});
		assertRejected(new Runnable() {
			@Override public void run() {
				DataSize.ofBytes(1.0).transferDurationAt(DataRate.ZERO);
			}
		});
	}

	private static void assertRejected(Runnable operation) {
		try {
			operation.run();
			throw new AssertionError("Expected invalid boundary value to be rejected");
		}
		catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}
}
