# ADR-0005: Terminal metric definitions

- Status: Accepted
- Date: 2026-09-23

## Context

Metrics were previously calculated independently by console and file output,
with ambiguous denominators and a network total that mixed quantities with
different dimensions. Empty samples and users retired before shutdown also
produced inconsistent results.

## Decision

One immutable `SimulationMetricsSnapshot` is captured at shutdown and consumed
by every formatter. Definitions are:

- `execution_time_ms` is elapsed wall-clock time; `simulation_time_ms` is the
  final CloudSim clock. They are not interchangeable.
- Device energy totals sum terminal readings. Averages divide by the number of
  devices in that category; the mobile category uses the archival all-user
  registry, including retired users. Empty categories report `0.0`.
- A timing series mean is the running mean of completed observations. Its
  per-user latest value and maximum remain distinct. Open intervals at
  retirement are discarded. Empty means and maxima report `0.0`.
- Lost-tuple percentage is `lost / total * 100`; it is `0.0` when no tuples were
  observed.
- Network durations separately sum queue, byte-transfer, and propagation time.
  Network usage is byte-milliseconds: transferred bytes multiplied by the
  corresponding duration. Tuple usage includes all three phases; migration
  usage includes only the byte-transfer phase and excludes preparation, fixed,
  propagation, and reallocation delays. Total network usage is tuple usage plus
  migration usage.
- A slice reconfiguration is one active-slice-set change on one sliced directed
  resource. Aggregate outage is the reconfiguration count multiplied by the
  configured delay. Received bandwidth is the per-slice sum of allocations
  sampled at those reconfigurations, not a time-weighted throughput average.
- Wireless queue metrics distinguish current waiting tuples, maximum aggregate
  waiting tuples, maximum per-direction depth, and dropped tuples.

Every stable report field has a name, type, and unit. Non-finite legacy values
are normalised to zero at snapshot capture; new producers must reject invalid
values earlier.

## Consequences

- Summary, full, benchmark, and semantic outputs share one definition source.
- Fields with different dimensions cannot be silently added.
- A metric-definition change requires a schema/ADR review and intentional
  update of all four deterministic reference snapshots.

Enforced by
[`SimulationMetricsSnapshot`](../../../src/org/fog/placement/SimulationMetricsSnapshot.java),
[`NetworkUsageMonitor`](../../../src/org/fog/utils/NetworkUsageMonitor.java),
[`RunReport`](../../../src/org/fog/placement/RunReport.java), and
[`ReferenceSimulationGoldenTest`](../../../test/org/fog/vmmobile/ReferenceSimulationGoldenTest.java).

