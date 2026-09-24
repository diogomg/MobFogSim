package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import org.fog.entities.ApDevice;
import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Coordinate;
import org.fog.vmmigration.BeforeMigration;
import org.fog.vmmigration.DuringMigration;
import org.fog.vmmigration.LowestDistBwSmartThingAP;
import org.fog.vmmigration.LowestDistBwSmartThingServerCloudlet;
import org.fog.vmmigration.LowestLatency;
import org.fog.vmmigration.Migration;
import org.fog.vmmigration.MyStatistics;
import org.fog.vmmobile.constants.MobileEvents;
import org.fog.vmmobile.constants.Policies;
import org.fog.vmmobile.constants.Services;
import org.fog.vmmobile.policy.MigrationPointPolicy;
import org.fog.vmmobile.policy.MigrationTechniquePolicy;
import org.junit.Test;

public class LegacyNamingCompatibilityTest {

	@Test
	@SuppressWarnings("deprecation")
	public void correctedConstantsRetainLegacyValues() {
		assertEquals(MobileEvents.DISCONNECT_ST_TO_SC,
			MobileEvents.DESCONNECT_ST_TO_SC);
		assertEquals(Services.HYBRID, Services.HIBRID);
		assertEquals(Policies.LOWEST_DIST_BW_SMART_THING_AP,
			Policies.LOWEST_DIST_BW_SMARTTING_AP);
		assertEquals(Policies.LOWEST_DIST_BW_SMART_THING_SERVER_CLOUDLET,
			Policies.LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void legacyAccessorsDelegateToCanonicalNames() {
		FogDevice device = new FogDevice();
		device.setTravelPredictionTime(12);
		assertEquals(12, device.getTravelPredicTime());
		device.setTravelPredicTime(18);
		assertEquals(18, device.getTravelPredictionTime());
		device.setMobilityPredictionError(7);
		assertEquals(7, device.getMobilityPrecitionError());
		device.setVolatilParentId(42);
		assertEquals(42, device.getVolatileParentId());
		device.setVolatileParentId(84);
		assertEquals(84, device.getVolatilParentId());

		AppExample.setTravelPredictionTimeForST(20);
		assertEquals(20, AppExample.getTravelPredicTimeForST());
		AppExample.setTravelPredicTimeForST(30);
		assertEquals(30, AppExample.getTravelPredictionTimeForST());
		AppExample.setMobilityPredictionError(9);
		assertEquals(9, AppExample.getMobilityPrecitionError());

		ApDevice accessPoint = new ApDevice();
		LowestLatency latency = new LowestLatency(Collections.<FogDevice>emptyList(),
			Collections.<ApDevice>emptyList(), MigrationPointPolicy.FIXED,
			MigrationTechniquePolicy.COMPLETE_VM);
		latency.setCorrentAP(accessPoint);
		assertSame(accessPoint, latency.getCurrentAP());

		LowestDistBwSmartThingAP accessPointStrategy =
			new LowestDistBwSmartThingAP(Collections.<FogDevice>emptyList(),
				Collections.<ApDevice>emptyList(), MigrationPointPolicy.FIXED,
				MigrationTechniquePolicy.COMPLETE_VM);
		accessPointStrategy.setCurrentAP(accessPoint);
		assertSame(accessPoint, accessPointStrategy.getCorrentAP());

		LowestDistBwSmartThingServerCloudlet serverStrategy =
			new LowestDistBwSmartThingServerCloudlet(
				Collections.<FogDevice>emptyList(), Collections.<ApDevice>emptyList(),
				MigrationPointPolicy.FIXED,
				MigrationTechniquePolicy.COMPLETE_VM);
		serverStrategy.setCorrentAP(accessPoint);
		assertSame(accessPoint, serverStrategy.getCurrentAP());

		assertTrue(new DuringMigration().managermentBetweeServerCloudlets());

		AppExample.setTravelPredictionTimeForST(0);
		AppExample.setMobilityPredictionError(0);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void oldMigrationPreparationImplementationsWorkThroughCanonicalApi() {
		BeforeMigration legacy = new BeforeMigration() {
			@Override
			public double dataprepare(MobileDevice mobileDevice) {
				return 17.5;
			}
		};
		assertEquals(17.5, legacy.prepareData(new MobileDevice()), 0.0);
	}

	@Test
	public void everyLegacySpellingIsExplicitlyDeprecated() throws Exception {
		assertDeprecated(MobileEvents.class.getField("DESCONNECT_ST_TO_SC"));
		assertDeprecated(Services.class.getField("HIBRID"));
		assertDeprecated(Policies.class.getField(
			"LOWEST_DIST_BW_SMARTTING_AP"));
		assertDeprecated(Policies.class.getField(
			"LOWEST_DIST_BW_SMARTTING_SERVERCLOUDLET"));

		assertDeprecated(ApDevice.class.getMethod("desconnectApSmartThing",
			MobileDevice.class));
		assertDeprecated(FogDevice.class.getMethod(
			"desconnectServerCloudletSmartThing", MobileDevice.class));
		assertDeprecated(FogDevice.class.getMethod("getTravelPredicTime"));
		assertDeprecated(FogDevice.class.getMethod("setTravelPredicTime",
			int.class));
		assertDeprecated(FogDevice.class.getMethod("getMobilityPrecitionError"));
		assertDeprecated(FogDevice.class.getMethod("getVolatilParentId"));
		assertDeprecated(FogDevice.class.getMethod("setVolatilParentId", int.class));
		assertDeprecated(FogDevice.class.getDeclaredField("travelPredicTime"));
		assertDeprecated(FogDevice.class.getDeclaredField("mobilityPrecitionError"));
		assertDeprecated(FogDevice.class.getDeclaredField("volatilParentId"));
		assertDeprecated(FogDevice.class.getMethod("saveLostTupple",
			String.class, String.class));
		assertDeprecated(MobileDevice.class.getMethod("saveLostTupple",
			String.class, String.class));
		assertDeprecated(Coordinate.class.getMethod("desableSmartThing",
			MobileDevice.class));
		assertDeprecated(MyStatistics.class.getMethod("putLantencyFileName",
			String.class, int.class));
		assertDeprecated(MyStatistics.class.getMethod("startWithoutConnetion",
			int.class, double.class));
		assertDeprecated(Migration.class.getMethod("serverClouletsAvailableList",
			java.util.List.class, MobileDevice.class));
		assertDeprecated(BeforeMigration.class.getMethod("dataprepare",
			MobileDevice.class));
		assertDeprecated(DuringMigration.class.getMethod(
			"managermentBetweeServerCloudlets"));
		assertDeprecated(AppExample.class.getDeclaredMethod("addApDevicesRandon",
			java.util.List.class, Coordinate.class, int.class));
		assertDeprecated(AppExample.class.getMethod("getTravelPredicTimeForST"));
		assertDeprecated(AppExample.class.getMethod("setTravelPredicTimeForST",
			int.class));
		assertDeprecated(AppExample.class.getMethod("getMobilityPrecitionError"));
		assertDeprecated(LowestLatency.class.getMethod("getCorrentAP"));
		assertDeprecated(LowestLatency.class.getMethod("setCorrentAP",
			ApDevice.class));
		assertDeprecated(LowestDistBwSmartThingAP.class.getMethod("getCorrentAP"));
		assertDeprecated(LowestDistBwSmartThingAP.class.getMethod("setCorrentAP",
			ApDevice.class));
		assertDeprecated(LowestDistBwSmartThingServerCloudlet.class.getMethod(
			"getCorrentAP"));
		assertDeprecated(LowestDistBwSmartThingServerCloudlet.class.getMethod(
			"setCorrentAP", ApDevice.class));
	}

	private static void assertDeprecated(Field field) {
		assertTrue(field.toString(), field.isAnnotationPresent(Deprecated.class));
	}

	private static void assertDeprecated(Method method) {
		assertTrue(method.toString(), method.isAnnotationPresent(Deprecated.class));
	}
}
