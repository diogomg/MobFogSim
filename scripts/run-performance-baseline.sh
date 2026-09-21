#!/usr/bin/env bash
set -euo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repository_root=$(cd -- "${script_directory}/.." && pwd)
cd "${repository_root}"

fixture_selection=${BENCHMARK_FIXTURES:-baseline}
java_options_text=${BENCHMARK_JAVA_OPTS:--Xms256m -Xmx4g -Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=GB -Duser.timezone=UTC}
read -r -a requested_fixtures <<< "${fixture_selection}"
read -r -a java_options <<< "${java_options_text}"

if [[ ${#requested_fixtures[@]} -eq 0 ]]; then
	echo "BENCHMARK_FIXTURES must name at least one fixture" >&2
	exit 2
fi
if [[ ! -x /usr/bin/time ]]; then
	echo "The benchmark requires GNU time at /usr/bin/time" >&2
	exit 2
fi

classpath=${repository_root}/build/test-classes:${repository_root}/build/classes:${repository_root}/jars/*:${repository_root}/jars/commons-math3-3.5/*
resolved_fixture_names=$(java -classpath "${classpath}" \
	org.fog.vmmobile.SimulationBenchmark --list "${requested_fixtures[@]}")
mapfile -t fixtures <<< "${resolved_fixture_names}"
if [[ ${#fixtures[@]} -eq 0 || -z ${fixtures[0]} ]]; then
	echo "Benchmark fixture selection resolved to an empty list" >&2
	exit 2
fi
fixture_names=${fixtures[*]}

if [[ -n ${BENCHMARK_OUTPUT:-} ]]; then
	benchmark_root=${BENCHMARK_OUTPUT}
	if [[ ${benchmark_root} != /* ]]; then
		benchmark_root=${repository_root}/${benchmark_root}
	fi
else
	benchmark_root=${repository_root}/benchmarks/run-$(date -u +%Y%m%dT%H%M%SZ)
fi
if [[ -e ${benchmark_root} ]]; then
	echo "Benchmark output already exists: ${benchmark_root}" >&2
	exit 2
fi
mkdir -p "${benchmark_root}"

find input -maxdepth 1 -type f \( -name '*log.csv' -o -name 'inputOrder.csv' \) \
	-print0 | LC_ALL=C sort -z | xargs -0 sha256sum \
	> "${benchmark_root}/input-files.sha256"
input_files_sha256=$(sha256sum "${benchmark_root}/input-files.sha256" \
	| awk '{print $1}')

{
	find src test -type f -name '*.java' -print
	printf '%s\n' Makefile scripts/run-performance-baseline.sh
} | LC_ALL=C sort -u | xargs sha256sum \
	> "${benchmark_root}/source-files.sha256"
source_tree_sha256=$(sha256sum "${benchmark_root}/source-files.sha256" \
	| awk '{print $1}')

git_commit=$(git rev-parse HEAD 2>/dev/null || printf 'unknown')
git_worktree_dirty=false
if [[ -n $(git status --porcelain 2>/dev/null) ]]; then
	git_worktree_dirty=true
fi
cpu_model=$(awk -F: '/model name/ { sub(/^[[:space:]]+/, "", $2); print $2; exit }' \
	/proc/cpuinfo 2>/dev/null || true)
physical_memory_kib=$(awk '/MemTotal/ { print $2; exit }' /proc/meminfo \
	2>/dev/null || true)
java_runtime=$(java -version 2>&1 | awk 'NR == 1 { print; exit }')

{
	printf 'benchmark_schema_version=4\n'
	printf 'created_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
	printf 'git_commit=%s\n' "${git_commit}"
	printf 'git_worktree_dirty=%s\n' "${git_worktree_dirty}"
	printf 'source_tree_sha256=%s\n' "${source_tree_sha256}"
	printf 'input_files_sha256=%s\n' "${input_files_sha256}"
	printf 'operating_system=%s\n' "$(uname -srm)"
	printf 'cpu_model=%s\n' "${cpu_model:-unknown}"
	printf 'logical_processors=%s\n' "$(getconf _NPROCESSORS_ONLN)"
	printf 'physical_memory_kib=%s\n' "${physical_memory_kib:-unknown}"
	printf 'java_runtime=%s\n' "${java_runtime}"
	printf 'java_options=%s\n' "${java_options_text}"
	printf 'measurement_process=fresh JVM per fixture\n'
	printf 'warm_up=none\n'
	printf 'measured_trials_per_fixture=1\n'
	printf 'output_mode=none\n'
	printf 'fixture_selection=%s\n' "${fixture_selection}"
	printf 'fixtures=%s\n' "${fixture_names}"
} > "${benchmark_root}/environment.properties"

results_file=${benchmark_root}/results.tsv
printf 'fixture\twall_seconds\tpeak_rss_kib\tevents_total\tevents_queued\tevents_periodic\tservers\taccess_points\tusers\tnetwork_nodes\tnetwork_links\tserver_transport_routes\tcloudsim_entities\tgenerated_tuples\tslice_reconfigurations\tslice_outage_seconds\tslice_received_bandwidth_bits_per_second_sum\twireless_queue_limit\twireless_queue_final\twireless_queue_max_total\twireless_queue_max_per_direction\twireless_queue_dropped_tuples\tstdout_bytes\tstderr_bytes\trun_output_bytes\ttotal_output_bytes\tcharacterisation_sha256\tinput_files_sha256\n' \
	> "${results_file}"

property_value() {
	local property_name=$1
	local property_file=$2
	awk -F= -v wanted="${property_name}" '
		$1 == wanted {
			print substr($0, length(wanted) + 2)
			found = 1
			exit
		}
		END { if (!found) exit 1 }
	' "${property_file}"
}

for fixture in "${fixtures[@]}"; do
	fixture_directory=${benchmark_root}/${fixture}
	run_output=${fixture_directory}/run-output
	metrics_file=${fixture_directory}/simulation.properties
	time_file=${fixture_directory}/time.properties
	stdout_file=${fixture_directory}/stdout.log
	stderr_file=${fixture_directory}/stderr.log
	mkdir -p "${fixture_directory}"

	echo "Running ${fixture} benchmark fixture..."
	if ! LC_ALL=C TZ=UTC /usr/bin/time --quiet \
		--format='wall_seconds=%e\npeak_rss_kib=%M' --output="${time_file}" \
		java "${java_options[@]}" -classpath "${classpath}" \
		org.fog.vmmobile.SimulationBenchmark "${fixture}" "${run_output}" \
		"${metrics_file}" > "${stdout_file}" 2> "${stderr_file}"; then
		echo "Benchmark fixture failed; inspect ${stderr_file}" >&2
		exit 1
	fi

	wall_seconds=$(property_value wall_seconds "${time_file}")
	peak_rss_kib=$(property_value peak_rss_kib "${time_file}")
	events_total=$(property_value events_dispatched_total "${metrics_file}")
	events_queued=$(property_value events_dispatched_queued "${metrics_file}")
	events_periodic=$(property_value events_dispatched_periodic "${metrics_file}")
	servers=$(property_value topology_server_cloudlets "${metrics_file}")
	access_points=$(property_value topology_access_points "${metrics_file}")
	users=$(property_value topology_mobile_devices "${metrics_file}")
	network_nodes=$(property_value topology_network_nodes "${metrics_file}")
	network_links=$(property_value topology_network_links "${metrics_file}")
	transport_routes=$(property_value topology_server_transport_routes \
		"${metrics_file}")
	cloudsim_entities=$(property_value cloudsim_entities "${metrics_file}")
	generated_tuples=$(property_value generated_tuples "${metrics_file}")
	slice_reconfigurations=$(property_value slice_reconfigurations \
		"${metrics_file}")
	slice_outage_seconds=$(property_value slice_outage_seconds \
		"${metrics_file}")
	slice_received_bandwidth=$(property_value \
		slice_received_bandwidth_bits_per_second_sum "${metrics_file}")
	wireless_queue_limit=$(property_value wireless_queue_limit "${metrics_file}")
	wireless_queue_final=$(property_value wireless_queue_final "${metrics_file}")
	wireless_queue_max_total=$(property_value wireless_queue_max_total \
		"${metrics_file}")
	wireless_queue_max_per_direction=$(property_value \
		wireless_queue_max_per_direction "${metrics_file}")
	wireless_queue_dropped_tuples=$(property_value \
		wireless_queue_dropped_tuples "${metrics_file}")
	run_output_bytes=$(property_value run_output_bytes "${metrics_file}")
	characterisation_sha256=$(property_value characterisation_sha256 \
		"${metrics_file}")
	stdout_bytes=$(wc -c < "${stdout_file}")
	stderr_bytes=$(wc -c < "${stderr_file}")
	total_output_bytes=$((stdout_bytes + stderr_bytes + run_output_bytes))

	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
		"${fixture}" "${wall_seconds}" "${peak_rss_kib}" \
		"${events_total}" "${events_queued}" "${events_periodic}" \
		"${servers}" "${access_points}" "${users}" "${network_nodes}" \
		"${network_links}" "${transport_routes}" "${cloudsim_entities}" \
		"${generated_tuples}" "${slice_reconfigurations}" \
		"${slice_outage_seconds}" "${slice_received_bandwidth}" \
		"${wireless_queue_limit}" "${wireless_queue_final}" \
		"${wireless_queue_max_total}" \
		"${wireless_queue_max_per_direction}" \
		"${wireless_queue_dropped_tuples}" \
		"${stdout_bytes}" "${stderr_bytes}" \
		"${run_output_bytes}" "${total_output_bytes}" \
		"${characterisation_sha256}" "${input_files_sha256}" \
		>> "${results_file}"
done

echo "Benchmark results: ${results_file}"
