BUILD_DIR := build
MAIN_CLASSES := $(BUILD_DIR)/classes
MAIN_SOURCE_LIST := $(BUILD_DIR)/main-sources.list
MAIN_CLASSPATH := $(MAIN_CLASSES):jars/*:jars/commons-math3-3.5/*
TEST_CLASSES := $(BUILD_DIR)/test-classes
TEST_SOURCE_LIST := $(BUILD_DIR)/test-sources.list
TEST_SUITE_LIST := $(BUILD_DIR)/test-suites.list
TEST_CLASSPATH := $(TEST_CLASSES):$(MAIN_CLASSPATH)
FOG_LINT_CLASSES := $(BUILD_DIR)/lint-classes
FOG_SOURCE_LIST := $(BUILD_DIR)/fog-sources.list
P0_REGRESSION_SUITES := org.fog.placement.P0CorrectnessRegressionTest
RUN_ARGS ?= 1 290538 0 0 80 61 0 11 0 0 0 60,40 70,30 1 2 0 summary
JACOCO_VERSION := 0.8.15
JACOCO_DIR := $(BUILD_DIR)/tools/jacoco
JACOCO_AGENT := $(JACOCO_DIR)/org.jacoco.agent-$(JACOCO_VERSION)-runtime.jar
JACOCO_CLI := $(JACOCO_DIR)/org.jacoco.cli-$(JACOCO_VERSION)-nodeps.jar
JACOCO_AGENT_SHA256 := fb5b0036a0899ea97edfa0fc2c7985b55f3f7c5695028163e5e60d4f3cf6075d
JACOCO_CLI_SHA256 := d2b74b20b415163c1f53261e7c5ef4445b788327094208ff821e0c2baf9bc8f1
JACOCO_BASE_URL := https://repo.maven.apache.org/maven2/org/jacoco
COVERAGE_EXEC := $(BUILD_DIR)/jacoco.exec
COVERAGE_REPORT_DIR := $(BUILD_DIR)/reports/coverage
BENCHMARK_FIXTURES ?= baseline
BENCHMARK_OUTPUT ?=
BENCHMARK_JAVA_OPTS ?= -Xms256m -Xmx14g -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=GB -Duser.timezone=UTC

.PHONY: compile compile-tests discover-tests list-tests run test lint coverage \
	coverage-tools benchmark benchmark-matrix list-benchmark-fixtures \
	demonstrate-p0-defects clean

compile:
	rm -rf $(MAIN_CLASSES)
	mkdir -p $(MAIN_CLASSES) $(MAIN_CLASSES)/images $(MAIN_CLASSES)/topologies
	find src -name '*.java' -print > $(MAIN_SOURCE_LIST)
	javac -encoding UTF-8 -classpath 'jars/*:jars/commons-math3-3.5/*' -d $(MAIN_CLASSES) @$(MAIN_SOURCE_LIST)
	cp -R src/images/* $(MAIN_CLASSES)/images/
	cp -R src/topologies/* $(MAIN_CLASSES)/topologies/

# The bundled CloudSim sources retain legacy deprecation warnings. Enforce the
# zero-warning contract on the locally maintained org.fog code.
lint: compile
	rm -rf $(FOG_LINT_CLASSES)
	mkdir -p $(FOG_LINT_CLASSES)
	find src/org/fog -name '*.java' -print > $(FOG_SOURCE_LIST)
	javac -encoding UTF-8 -Xlint:all -Werror -Xmaxwarns 10000 \
		-classpath '$(MAIN_CLASSPATH)' -d $(FOG_LINT_CLASSES) @$(FOG_SOURCE_LIST)

run: compile
	java -Xmx10g -Dfile.encoding=UTF-8 -classpath '$(MAIN_CLASSPATH)' org.fog.vmmobile.AppExample $(RUN_ARGS)

compile-tests: compile
	rm -rf $(TEST_CLASSES)
	mkdir -p $(TEST_CLASSES)
	find test -name '*.java' -print > $(TEST_SOURCE_LIST)
	javac -encoding UTF-8 -classpath '$(MAIN_CLASSPATH)' -d $(TEST_CLASSES) @$(TEST_SOURCE_LIST)
	cp -R test/resources/. $(TEST_CLASSES)/

discover-tests: compile-tests
	find test -type f -name '*Test.java' -print | LC_ALL=C sort | \
		sed -e 's#^test/##' -e 's#/#.#g' -e 's#\.java$$##' > $(TEST_SUITE_LIST)
	test -s $(TEST_SUITE_LIST)
	printf 'Discovered %s test classes.\n' "$$(wc -l < $(TEST_SUITE_LIST))"

list-tests: discover-tests
	cat $(TEST_SUITE_LIST)

test: discover-tests
	xargs java -classpath '$(TEST_CLASSPATH)' org.junit.runner.JUnitCore < $(TEST_SUITE_LIST)

coverage-tools: $(JACOCO_AGENT) $(JACOCO_CLI)

$(JACOCO_AGENT):
	mkdir -p $(JACOCO_DIR)
	curl --fail --location --retry 3 --silent --show-error \
		--output $@.tmp \
		$(JACOCO_BASE_URL)/org.jacoco.agent/$(JACOCO_VERSION)/org.jacoco.agent-$(JACOCO_VERSION)-runtime.jar
	printf '%s  %s\n' '$(JACOCO_AGENT_SHA256)' '$@.tmp' | sha256sum --check --status
	mv $@.tmp $@

$(JACOCO_CLI):
	mkdir -p $(JACOCO_DIR)
	curl --fail --location --retry 3 --silent --show-error \
		--output $@.tmp \
		$(JACOCO_BASE_URL)/org.jacoco.cli/$(JACOCO_VERSION)/org.jacoco.cli-$(JACOCO_VERSION)-nodeps.jar
	printf '%s  %s\n' '$(JACOCO_CLI_SHA256)' '$@.tmp' | sha256sum --check --status
	mv $@.tmp $@

coverage: coverage-tools discover-tests
	rm -rf $(COVERAGE_REPORT_DIR) $(COVERAGE_EXEC)
	mkdir -p $(COVERAGE_REPORT_DIR)
	xargs java -javaagent:$(JACOCO_AGENT)=destfile=$(COVERAGE_EXEC),append=false \
		-classpath '$(TEST_CLASSPATH)' org.junit.runner.JUnitCore < $(TEST_SUITE_LIST)
	java -jar $(JACOCO_CLI) report $(COVERAGE_EXEC) \
		--classfiles $(MAIN_CLASSES)/org/fog \
		--sourcefiles src \
		--html $(COVERAGE_REPORT_DIR)/html \
		--xml $(COVERAGE_REPORT_DIR)/jacoco.xml \
		--csv $(COVERAGE_REPORT_DIR)/jacoco.csv \
		--name MobFogSim
	awk -F, 'function percentage(covered, missed) { \
		return covered + missed == 0 ? "n/a" : sprintf("%.2f%%", 100 * covered / (covered + missed)) } \
		NR > 1 { im += $$4; ic += $$5; bm += $$6; bc += $$7; lm += $$8; lc += $$9 } \
		END { printf "Coverage: instructions %s, branches %s, lines %s\n", \
		percentage(ic, im), percentage(bc, bm), percentage(lc, lm) }' \
		$(COVERAGE_REPORT_DIR)/jacoco.csv
	printf 'HTML report: %s\n' '$(COVERAGE_REPORT_DIR)/html/index.html'

benchmark: compile-tests
	BENCHMARK_FIXTURES='$(BENCHMARK_FIXTURES)' \
	BENCHMARK_OUTPUT='$(BENCHMARK_OUTPUT)' \
	BENCHMARK_JAVA_OPTS='$(BENCHMARK_JAVA_OPTS)' \
		bash scripts/run-performance-baseline.sh

benchmark-matrix: BENCHMARK_FIXTURES := matrix
benchmark-matrix: benchmark

list-benchmark-fixtures: compile-tests
	java -classpath '$(TEST_CLASSPATH)' org.fog.vmmobile.SimulationBenchmark \
		--list $(BENCHMARK_FIXTURES)

# Historical entry point retained for explicitly running the C1-C5 safety net.
demonstrate-p0-defects: compile-tests
	java -Dmobfogsim.runKnownP0Defects=true -classpath '$(TEST_CLASSPATH)' \
		org.junit.runner.JUnitCore $(P0_REGRESSION_SUITES)

clean:
	rm -rf $(BUILD_DIR)
