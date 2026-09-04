# Reference simulation semantic snapshots

These files freeze model-level results for the established one-user seed
`290538` runs. All cases use the positional arguments below, with the first and
seventh arguments varied as shown.

| Case | Arguments |
| --- | --- |
| Migration disabled | `0 290538 0 0 1 11 0 61 0 0 2 100 100 1 2 none` |
| Complete-VM migration | `1 290538 0 0 1 11 0 61 0 0 2 100 100 1 2 none` |
| Container migration | `1 290538 0 0 1 11 1 61 0 0 2 100 100 1 2 none` |
| Live migration | `1 290538 0 0 1 11 2 61 0 0 2 100 100 1 2 none` |

Schema version 1 sorts all fields, formats numbers independently of the
machine locale, and rounds floating-point results to nine decimal places. It
excludes wall-clock execution time, output paths, and CloudSim entity IDs.
Each characterisation file combines that semantic snapshot with separate event
measurements. Event totals count occurrences that the CloudSim core actually
dispatched; events left queued at termination are excluded. Device energy rows
are grouped by equal power and energy readings; each group's sorted member names
are covered by a SHA-256 digest, while small groups also list their members for
readability.

To inspect a candidate baseline after compiling the tests, run:

```sh
java -classpath 'build/test-classes:jars/*:jars/commons-math3-3.5/*' \
  org.fog.vmmobile.ReferenceSimulationGoldenTest migration-disabled
```

Replace the final case name with `complete-vm`, `container`, or
`live-migration` as required. Review every semantic difference before updating
a checked-in snapshot.
