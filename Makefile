BUILD_DIR := build
MAIN_CLASSES := $(BUILD_DIR)/classes
MAIN_SOURCE_LIST := $(BUILD_DIR)/main-sources.list
MAIN_CLASSPATH := $(MAIN_CLASSES):jars/*:jars/commons-math3-3.5/*
TEST_CLASSES := build/test-classes
TEST_CLASSPATH := $(TEST_CLASSES):jars/*:jars/commons-math3-3.5/*
RUN_ARGS ?= 1 290538 0 0 3 11 0 61 0 0
TEST_SUITES := \
	org.cloudbus.cloudsim.core.CloudSimTerminationTest \
	org.cloudbus.cloudsim.util.RunOutputManagerTest \
	org.fog.entities.ActuatorTest \
	org.fog.entities.FogDeviceChildStateTest \
	org.fog.entities.SensorTest \
	org.fog.localization.CoordinateTest \
	org.fog.localization.DistancesTest \
	org.fog.placement.MobileControllerDelayedEntryTest \
	org.fog.placement.ModulePlacementEdgewardsAtomicityTest \
	org.fog.vmmigration.MigrationTechniqueTest \
	org.fog.vmmigration.MigrationPreparationTest \
	org.fog.vmmigration.MigrationUtilityTest \
	org.fog.vmmigration.ServiceAgreementTest \
	org.fog.vmmigration.VmDestinationPolicyTest \
	org.fog.vmmigration.MobileEdgeHostSelectorTest \
	org.fog.vmmigration.NextStepTest \
	org.fog.vmmobile.AppExampleParametersTest \
	org.fog.vmmobile.AppExampleUserRegistrationTest \
	org.fog.vmmobile.MobilityDataLoaderTest \
	org.fog.vmmobile.ServerCloudletNetworkTest \
	org.fog.utils.TimeKeeperTest \
	org.fog.utils.distribution.RandomizedDistributionTest \
	org.fog.utils.NetworkSlicingConfigurationTest \
	org.fog.utils.UserAllocationNetworkSlicingTest \
	org.fog.utils.NetworkSlicingScopeTest \
	org.fog.utils.AccessPointNetworkSlicingTest \
	org.fog.utils.MigrationTransferSpecTest \
	org.fog.utils.MigrationTransferSchedulerTest \
	org.fog.utils.NetworkSlicingEventIntegrationTest \
	org.fog.utils.NetworkUsageMonitorTest

.PHONY: compile run test clean

compile:
	rm -rf $(MAIN_CLASSES)
	mkdir -p $(MAIN_CLASSES) $(MAIN_CLASSES)/images $(MAIN_CLASSES)/topologies
	find src -name '*.java' -print > $(MAIN_SOURCE_LIST)
	javac -encoding UTF-8 -classpath 'jars/*:jars/commons-math3-3.5/*' -d $(MAIN_CLASSES) @$(MAIN_SOURCE_LIST)
	cp -R src/images/* $(MAIN_CLASSES)/images/
	cp -R src/topologies/* $(MAIN_CLASSES)/topologies/

run: compile
	java -Xmx10g -Dfile.encoding=UTF-8 -classpath '$(MAIN_CLASSPATH)' org.fog.vmmobile.AppExample $(RUN_ARGS)

test:
	rm -rf $(TEST_CLASSES)
	mkdir -p $(TEST_CLASSES)
	find src test -name '*.java' -print > $(BUILD_DIR)/test-sources.list
	javac -encoding UTF-8 -classpath 'jars/*:jars/commons-math3-3.5/*' -d $(TEST_CLASSES) @$(BUILD_DIR)/test-sources.list
	java -classpath '$(TEST_CLASSPATH)' org.junit.runner.JUnitCore $(TEST_SUITES)

clean:
	rm -rf $(BUILD_DIR)
