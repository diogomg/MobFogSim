# Performance baseline

Run the three historical process-isolated fixtures with:

```text
make benchmark
```

List or run the expanded one-factor-at-a-time matrix with:

```text
make list-benchmark-fixtures BENCHMARK_FIXTURES=matrix
make benchmark-matrix
```

Run the 18 scenarios derived from the ten-user matrix with an arbitrary user
count by using a numeric `<count>-users` group:

```text
make list-benchmark-fixtures BENCHMARK_FIXTURES=37-users
make benchmark BENCHMARK_FIXTURES=37-users
```

Compilation happens before measurement. Each fixture then starts in a fresh
JVM with a 256 MiB initial heap, a 14 GiB maximum heap, UTC, British English,
seed `290538`, output mode `none`, no warm-up, and one measured trial. Override
the generated result directory, fixture selection, or JVM options when needed:

```text
make benchmark \
  BENCHMARK_OUTPUT=benchmarks/candidate \
  BENCHMARK_FIXTURES='reference u10-prediction-60' \
  BENCHMARK_JAVA_OPTS='-Xms256m -Xmx4g -Dfile.encoding=UTF-8'
```

`BENCHMARK_OUTPUT` must not already exist. Omitting it creates a UTC-stamped
`run-*` directory under `benchmarks`.

Numeric user groups accept positive integers without leading zeroes. Counts of
two or more clone all 18 `u10-*` scenarios and generate names such as
`u37-base`; `1-users` selects the 13 established single-slice `u1-*`
scenarios. `10-users` and `50-users` resolve to their existing fixture names. Repeated names
or overlapping groups are de-duplicated while preserving their first
occurrence.

The selected user count must also be supported by the mobility inputs. The
current `input/inputOrder.csv` contains 480 assignments, so current runs are
limited to 480 users. Larger workloads
may require additional heap and substantially more execution time.

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

The default benchmark retains the three original baseline workload shapes.
Compare results only when their recorded configuration and input manifests
match. Run the migration-enabled stress fixture explicitly with:

```text
make benchmark BENCHMARK_FIXTURES='large-migration'
```

This fixture previously exposed a stale server-connect event during concurrent
hand-off and live migration. It now serves as a reproducible regression workload
for the generation-checked network-association transition.

## Expanded scenario matrix

The `matrix` group contains 49 unique scenarios: 31 one-factor-at-a-time one-
and ten-user scenarios plus all 18 50-user variants cloned from the ten-user
set. Each base scenario changes one relevant option from a common live-migration
reference, which keeps individual parameter effects interpretable and avoids a
10,368-run Cartesian product. The existing `reference` fixture remains the
one-user reference configuration and is not duplicated inside the matrix.

The shared reference values are migration enabled, fixed migration point,
lowest-latency strategy, live-container migration, no prediction or prediction
error, transport-only dynamic slicing, hybrid VM destinations, seed `290538`,
74 Mbps cloudlet bandwidth, 3 units of cloudlet latency, a two-second slice
reallocation delay, and output mode `none`.

Every fixture using dynamic slicing has a two-second reallocation delay except
`u10-three-slices-weighted-delay-60`, which uses 60 seconds. Fixed slicing
ignores the configured delay.

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
u1-scope-end-to-end
u1-scope-wireless
u1-slicing-static
u1-destination-edge
u1-destination-device
```

The ten-user group contains 18 fixtures:

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
u10-scope-end-to-end
u10-scope-wireless
u10-allocation-equal-two
u10-three-slices-weighted
u10-three-slices-weighted-delay-60
u10-three-slices-equal
u10-slicing-static
u10-destination-edge
u10-destination-device
```

The ten-user slicing configurations are:

| Fixture basis | User allocation (parameter 12) | Bandwidth shares (parameter 13) | Reallocation delay (parameter 15) |
| --- | --- | --- | ---: |
| `u10-base` and non-slicing variants | `70,30` | `50,50` | 2 s |
| `u10-allocation-equal-two` | `50,50` | `50,50` | 2 s |
| `u10-three-slices-weighted` | `50,30,20` | `33.34,33.33,33.33` | 2 s |
| `u10-three-slices-weighted-delay-60` | `50,30,20` | `33.34,33.33,33.33` | 60 s |
| `u10-three-slices-equal` | `33.333334,33.333333,33.333333` | `33.34,33.33,33.33` | 2 s |

All benchmark fixtures use transport-only slicing by default. The
`scope-wireless` and `scope-end-to-end` fixtures are the explicit scope
comparisons.

`33.34,33.33,33.33` is used instead of `33,33,33` because configuration
percentages must total exactly 100 within the parser tolerance.


## Measurements

GNU `time` records end-to-end wall time and peak resident set size (RSS) for
each JVM. The Java harness records dispatched queued and periodic events,
initial topology sizes, entity and tuple counts, run-directory output bytes,
and a SHA-256 digest of the same semantic characterisation used by the golden
tests. The compact `results.tsv` also contains the slice-reconfiguration count,
slice outage in seconds, the per-slice received-bandwidth sum, and wireless
queue limit, final size, maximum aggregate size, maximum per-direction depth,
and dropped-tuple count. Per-slice bandwidth is encoded as a comma-separated
`slice=value` list in bit/s. The shell runner adds captured standard-output and standard-error bytes;
`total_output_bytes` is their sum plus run-directory files.

`environment.properties`, `input-files.sha256`, and `source-files.sha256`
record the execution environment and exact inputs. `results.tsv` is the compact
comparison table. Each fixture's `simulation.properties` additionally contains
the complete stable simulation snapshot, covering energy, latency, network,
migration, tuple, user, and slicing metrics even though benchmark fixtures use
output mode `none`. Per-fixture logs remain available for diagnosis. Generated
runs live in UTC-stamped `run-*` directories under `benchmarks`.
