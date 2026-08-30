run:
	java -Xmx10g -Dfile.encoding=UTF-8 -classpath bin:jars/cloudsim-3.0.3-sources.jar:jars/cloudsim-3.0.3.jar:jars/cloudsim-examples-3.0.3-sources.jar:jars/cloudsim-examples-3.0.3.jar:jars/commons-math3-3.5/commons-math3-3.5.jar:jars/guava-18.0.jar:jars/json-simple-1.1.1.jar:jars/junit.jar:jars/org.hamcrest.core_1.3.0.v201303031735.jar org.fog.vmmobile.AppExample 1 290538 0 0 3 11 0 61 0 0

TEST_CLASSES := build/test-classes
TEST_CLASSPATH := $(TEST_CLASSES):jars/*:jars/commons-math3-3.5/*
TEST_SUITES := \
	org.cloudbus.cloudsim.core.CloudSimTerminationTest \
	org.fog.entities.ActuatorTest \
	org.fog.entities.FogDeviceChildStateTest \
	org.fog.entities.SensorTest \
	org.fog.localization.CoordinateTest \
	org.fog.placement.ModulePlacementEdgewardsAtomicityTest \
	org.fog.vmmigration.MigrationTechniqueTest \
	org.fog.vmmigration.MigrationPreparationTest \
	org.fog.vmmigration.MigrationUtilityTest \
	org.fog.vmmigration.ServiceAgreementTest \
	org.fog.vmmigration.VmDestinationPolicyTest \
	org.fog.vmmigration.MobileEdgeHostSelectorTest \
	org.fog.vmmigration.NextStepTest \
	org.fog.vmmobile.AppExampleParametersTest \
	org.fog.vmmobile.ServerCloudletNetworkTest \
	org.fog.utils.TimeKeeperTest \
	org.fog.utils.distribution.RandomizedDistributionTest \
	org.fog.utils.NetworkSlicingConfigurationTest \
	org.fog.utils.UserAllocationNetworkSlicingTest \
	org.fog.utils.NetworkSlicingScopeTest \
	org.fog.utils.AccessPointNetworkSlicingTest \
	org.fog.utils.DynamicNetworkSlicingTest \
	org.fog.utils.MigrationTransferSchedulerTest \
	org.fog.utils.NetworkSlicingEventIntegrationTest

.PHONY: test
test:
	mkdir -p $(TEST_CLASSES)
	find src test -name '*.java' -print > build/test-sources.list
	javac -encoding UTF-8 -classpath 'jars/*:jars/commons-math3-3.5/*' -d $(TEST_CLASSES) @build/test-sources.list
	java -classpath '$(TEST_CLASSPATH)' org.junit.runner.JUnitCore $(TEST_SUITES)

clean:
	rm *.txt
