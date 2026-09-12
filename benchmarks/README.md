# Performance baseline

The current reviewed result is the
[`4 September 2026 baseline`](BASELINE_2026-09-04.md).
The expanded parameter study is recorded in the
[`4 September 2026 benchmark matrix`](MATRIX_2026-09-04.md).

Run the three historical process-isolated fixtures with:

```text
make benchmark
```

List or run the expanded one-factor-at-a-time matrix with:

```text
make list-benchmark-fixtures BENCHMARK_FIXTURES=matrix
make benchmark-matrix
```

Compilation happens before measurement. Each fixture then starts in a fresh
JVM with a 256 MiB initial heap, a 4 GiB maximum heap, UTC, British English,
seed `290538`, output mode `none`, no warm-up, and one measured trial. Override
the generated result directory, fixture selection, or JVM options when needed:

```text
make benchmark \
  BENCHMARK_OUTPUT=build/benchmarks/candidate \
  BENCHMARK_FIXTURES='reference u10-prediction-60' \
  BENCHMARK_JAVA_OPTS='-Xms256m -Xmx4g -Dfile.encoding=UTF-8'
```

`BENCHMARK_OUTPUT` must not already exist. Omitting it creates a UTC-stamped
directory under `build/benchmarks`.

## Fixtures

| Fixture | Users | Migration | Migration technique | Purpose |
| --- | ---: | --- | --- | --- |
| `small` | 1 | Disabled | Complete VM (inactive) | Cheapest end-to-end golden configuration. |
| `reference` | 1 | Enabled | Live container | Exact live-migration semantic golden configuration. |
| `large` | 10 | Disabled | Complete VM (inactive) | Multi-user scaling workload that reaches normal completion. |
| `large-migration` | 10 | Enabled | Live container | Optional multi-user hand-off and migration stress workload. |

All fixtures retain the simulator's fixed infrastructure of 144 server
cloudlets and 144 access points. The initial physical graph contains 288 nodes
and 10,440 undirected links; the server-cloudlet closure contains 20,592
directed adjacency entries.

The default benchmark retains the three original baseline fixtures so new runs
remain directly comparable with the dated table. Run the migration-enabled
stress fixture explicitly with:

```text
make benchmark BENCHMARK_FIXTURES='large-migration'
```

This fixture previously exposed a stale server-connect event during concurrent
hand-off and live migration. It now serves as a reproducible regression workload
for the generation-checked network-association transition.

## Expanded scenario matrix

The `matrix` group contains 30 unique one-factor-at-a-time scenarios. Each
scenario changes one relevant option from a common live-migration reference,
which keeps individual parameter effects interpretable and avoids a 10,368-run
Cartesian product. The existing `reference` fixture remains the one-user
reference configuration and is not duplicated inside the matrix.

The shared reference values are migration enabled, fixed migration point,
lowest-latency strategy, live-container migration, no prediction or prediction
error, end-to-end dynamic slicing, hybrid VM destinations, seed `290538`,
11 Mbps cloudlet bandwidth, 61 units of cloudlet latency, a zero-second slice
reallocation delay, and output mode `none`.

The 13 one-user fixtures use the default single slice (`100` user allocation
and `100` bandwidth share):

```text
u1-migration-off
u1-point-speed
u1-strategy-cloudlet-distance
u1-strategy-ap-distance
u1-policy-complete-vm
u1-policy-container
u1-prediction-60
u1-error-500
u1-scope-transport
u1-scope-wireless
u1-slicing-static
u1-destination-edge
u1-destination-device
```

The 17 ten-user fixtures use `u10-base` as their common reference:

```text
u10-base
u10-migration-off
u10-point-speed
u10-strategy-cloudlet-distance
u10-strategy-ap-distance
u10-policy-complete-vm
u10-policy-container
u10-prediction-60
u10-error-500
u10-scope-transport
u10-scope-wireless
u10-allocation-equal-two
u10-three-slices-weighted
u10-three-slices-equal
u10-slicing-static
u10-destination-edge
u10-destination-device
```

The ten-user slicing configurations are:

| Fixture basis | User allocation (parameter 12) | Bandwidth shares (parameter 13) |
| --- | --- | --- |
| `u10-base` and non-slicing variants | `70,30` | `50,50` |
| `u10-allocation-equal-two` | `50,50` | `50,50` |
| `u10-three-slices-weighted` | `50,30,20` | `33.34,33.33,33.33` |
| `u10-three-slices-equal` | `33.333334,33.333333,33.333333` | `33.34,33.33,33.33` |

`33.34,33.33,33.33` is used instead of `33,33,33` because configuration
percentages must total exactly 100 within the parser tolerance.

`BENCHMARK_FIXTURES` accepts individual fixture names and three group aliases:
`baseline` expands to the original three fixtures, `matrix` expands to the 30
scenarios above, and `all` expands to the baseline, `large-migration`, and the
matrix. Repeated names or overlapping groups are de-duplicated while preserving
their first occurrence.

## Measurements

GNU `time` records end-to-end wall time and peak resident set size (RSS) for
each JVM. The Java harness records dispatched queued and periodic events,
initial topology sizes, entity and tuple counts, run-directory output bytes,
and a SHA-256 digest of the same semantic characterisation used by the golden
tests. The shell runner adds captured standard-output and standard-error bytes;
`total_output_bytes` is their sum plus run-directory files.

`environment.properties`, `input-files.sha256`, and `source-files.sha256`
record the execution environment and exact inputs. `results.tsv` is the compact
comparison table. Per-fixture logs and raw property files remain available for
diagnosis. Generated runs live below the ignored `build` directory; reviewed
baselines belong in a dated Markdown report under this directory.
