# ADR-0001: Time, data, and rate units

- Status: Accepted
- Date: 2026-09-23

## Context

CloudSim exposes time as dimensionless `double` values, while MobFogSim inputs
historically mixed seconds and milliseconds. VM sizes were supplied in MiB,
tuple sizes in bytes, and network capacities in bit/s. Passing those primitives
through several layers made dimension errors hard to detect.

## Decision

The simulation clock and all scheduled event delays use **milliseconds**.
Propagation, queue, transfer, preparation, fixed, handoff, and slice
reallocation durations are therefore milliseconds inside the model. CLI values
documented in seconds—mobility look-ahead and reallocation delay—are converted
once at the parsing/configuration boundary.

Data quantities are stored as **bytes** and link capacities as **bits per
second**. Legacy VM-size inputs in MiB are converted once by the migration
transfer specification. Transfer time is:

```text
milliseconds = bytes * 8 * 1000 / bits_per_second
```

New cross-boundary APIs use `SimulationDuration`, `PropagationDelay`,
`DataSize`, and `DataRate`. Primitive compatibility methods must name or
document their unit and delegate to these values.

## Consequences

- Unit conversions are visible and testable at boundaries.
- Report fields carry explicit units; no bare network “usage” total is emitted.
- CloudSim adapters unwrap typed values only when calling the legacy API.
- Changing the simulator clock unit requires a replacement ADR and a semantic
  snapshot migration.

Enforced by
[`SimulationDuration`](../../../src/org/fog/utils/SimulationDuration.java),
[`DataSize`](../../../src/org/fog/utils/DataSize.java),
[`MigrationTransferSpec`](../../../src/org/fog/utils/MigrationTransferSpec.java),
and [`BoundaryValueTypesTest`](../../../test/org/fog/utils/BoundaryValueTypesTest.java).

