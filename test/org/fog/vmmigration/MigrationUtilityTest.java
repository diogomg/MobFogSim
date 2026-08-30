package org.fog.vmmigration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.fog.vmmobile.constants.Directions;
import org.junit.After;
import org.junit.Test;

public class MigrationUtilityTest {

	@After
	public void resetStaticMigrationState() {
		Migration.setMigrationPoint(false);
		Migration.setMigrationZone(false);
		Migration.setPolicyReplicaVM(0);
		Migration.setApsAvailable(null);
		Migration.setServerCloudletsAvailable(null);
	}

	@Test
	public void insideConeHandlesDirectionWraparound() {
		assertTrue(Migration.insideCone(Directions.EAST, Directions.SOUTHEAST));
		assertTrue(Migration.insideCone(Directions.EAST, Directions.EAST));
		assertTrue(Migration.insideCone(Directions.EAST, Directions.NORTHEAST));
		assertFalse(Migration.insideCone(Directions.EAST, Directions.WEST));

		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.SOUTH));
		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.SOUTHEAST));
		assertTrue(Migration.insideCone(Directions.SOUTHEAST, Directions.EAST));
		assertFalse(Migration.insideCone(Directions.SOUTHEAST, Directions.NORTH));
	}

	@Test
	public void insideConeHandlesNonBoundaryDirections() {
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTHEAST));
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTH));
		assertTrue(Migration.insideCone(Directions.NORTH, Directions.NORTHWEST));
		assertFalse(Migration.insideCone(Directions.NORTH, Directions.SOUTH));
	}

	@Test
	public void migrationStaticStateRoundTripsAndCanBeReset() {
		Migration.setMigrationPoint(true);
		Migration.setMigrationZone(true);
		Migration.setPolicyReplicaVM(7);
		Migration.setApsAvailable(Collections.emptyList());
		Migration.setServerCloudletsAvailable(Collections.emptyList());

		assertTrue(Migration.isMigrationPoint());
		assertTrue(Migration.isMigrationZone());
		assertEquals(7, Migration.getPolicyReplicaVM());
		assertTrue(Migration.getApsAvailable().isEmpty());
		assertTrue(Migration.getServerCloudletsAvailable().isEmpty());
	}

	@Test
	public void serviceValueObjectRoundTrips() {
		Service service = new Service();
		service.setType(2);
		service.setValue(1.25f);

		assertEquals(2, service.getType());
		assertEquals(1.25f, service.getValue(), 0.0f);
	}

	@Test
	public void serviceAgreementStateRoundTrips() {
		ServiceAgreement.setServiceType(2);
		ServiceAgreement.setServiceValue(3.5f);

		assertEquals(2, ServiceAgreement.getServiceType());
		assertEquals(3.5f, ServiceAgreement.getServiceValue(), 0.0f);
	}
}

