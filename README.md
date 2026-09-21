# MobFogSim

MobFogSim — Simulation of Mobility and Migration for Fog Computing

MobFogSim extends the [iFogSim](https://github.com/Cloudslab/iFogSim1)
simulator to support the modelling of device mobility and service migration in
fog computing.

Further details are available in Puliafito et al., ‘MobFogSim: Simulation of
mobility and migration for fog computing’, *Simulation Modelling Practice and
Theory* (2020).

## Running MobFogSim

### Building your own simulation

1. Provide the user mobility dataset and order manifest.
2. Initialise CloudSim before creating any simulation entities.
3. Create the infrastructure and mobile devices.
4. Configure users and their scheduled entry times.
5. Configure the network.
6. Start the simulation. When a user enters and associates with an access
   point, create its broker, VM, application, module mappings, sensors, and
   actuators.
7. Collect and print the results after the simulation finishes.

An example application is provided in
[`src/org/fog/vmmobile/AppExample.java`](src/org/fog/vmmobile/AppExample.java).

### Running from the command line

Compile production classes and runtime resources into `build/classes`:

```text
make compile
```

Compile and run the example with the default arguments:

```text
make run
```

`make run` depends on `compile` and uses `build/classes`; it does not use
precompiled files from `bin`. Override the simulation arguments when needed:

```text
make run RUN_ARGS='1 290538 0 0 10 11 0 61 0 0 0 60,40 70,30 1 2 2 summary'
```

Run the tests with `make test`. Test classes are discovered automatically from
files whose names end in `Test.java`; `make list-tests` prints the exact suite
that will run. Generate HTML, XML, and CSV coverage reports with
`make coverage`. This target downloads a pinned, checksum-verified JaCoCo
release into the ignored `build/tools` directory and writes the reports under
`build/reports/coverage`. Use `make clean` to remove the generated `build`
directory only.

Run the reproducible small, reference, and large performance fixtures with
`make benchmark`. This records wall time, peak RSS, event counts, topology
sizes, output bytes, environment details, input checksums, and semantic digests
in UTC-stamped run directories under `benchmarks`. See
[`benchmarks/README.md`](benchmarks/README.md) for the fixture definitions and
override options. Use `make benchmark-matrix` for the expanded 49-scenario
comparison, or list its fixture names with
`make list-benchmark-fixtures BENCHMARK_FIXTURES=matrix`.
Use `make benchmark BENCHMARK_FIXTURES=37-users` to run the 18 ten-user matrix
scenarios with any supported positive user count. The current mobility-order
manifest supports up to 480 users.

### Running in the Eclipse IDE

1. Create a Java project that uses the repository root as its project
   directory.
2. Select **Project > Properties > Java Build Path > Libraries > Add External
   JARs**, then add the JAR files from the `jars` directory.
3. Open `src/org/fog/vmmobile/AppExample.java`, then select **Run As > Run
   Configurations > Java Application > AppExample > Arguments** and enter the
   required values under **Program arguments**.
4. Run `AppExample.java` by selecting **Run As > Java Application**.

### Requirements

- Java Development Kit (JDK); the continuous-integration build uses JDK 21
- GNU Make
- `curl` and `sha256sum` when running `make coverage`
- GNU `time` at `/usr/bin/time` when running `make benchmark`

Optional dependency:

- IBM ILOG CPLEX for optimisation algorithms

## Parameters

The first ten parameters are required. Parameters 11 through 17 are optional
and use the defaults shown below. Because the interface is positional, all
preceding parameters must be supplied when setting a later one.

| Order | Parameter | Type, format, and accepted values | Brief explanation |
| ---: | --- | --- | --- |
| 1 | Migration | Required integer flag: `0` or `1` | `0` disables service/VM migration; `1` allows the simulator to migrate an application's VM when its trigger and destination conditions are satisfied. User mobility and wireless handoffs can still occur when migration is disabled. |
| 2 | Random seed | Required positive integer (`> 0`) | Seeds the simulator's pseudo-random choices, making runs with the same inputs and configuration reproducible. |
| 3 | Migration point | Required integer: `0` fixed, `1` speed-aware | Selects when migration preparation is triggered. The fixed policy uses a predefined distance from the source AP's coverage boundary; the speed-aware policy also considers the user's speed and the estimated migration duration. |
| 4 | Migration strategy | Required integer: `0` lowest latency, `1` shortest user-to-cloudlet distance, `2` shortest user-to-AP distance | Selects the destination server cloudlet after the user reaches a valid migration point and movement zone. |
| 5 | Number of users | Required positive integer (`> 0`) | Sets the number of mobile users. The `input` directory must contain enough matching mobility traces, and `input/inputOrder.csv` must contain at least this many assignments. |
| 6 | Cloudlet bandwidth | Required positive integer (`> 0`), in Mbps | Sets the base network bandwidth used to generate server-cloudlet link capacities. Network slicing divides the resulting physical capacity when transport slicing is enabled. |
| 7 | Migration policy | Required integer: `0` complete-VM cold migration, `1` complete-container migration, `2` container live migration | Selects the state to transfer and determines how the simulator models migration time and service downtime. |
| 8 | Cloudlet latency | Required finite positive number (`> 0`) | Sets the base latency per grid hop between server cloudlets; the simulator adds a small seeded random component to each directed link. |
| 9 | Mobility prediction | Required non-negative integer (`>= 0`), in seconds | Looks this far ahead in the selected mobility trace when estimating the user's future position for destination selection. `0` uses the current trace position. |
| 10 | Prediction error | Required non-negative integer (`>= 0`), in metres | Adds a displacement of this magnitude to the predicted position, in a seeded random direction, to model location-prediction inaccuracy. `0` represents an error-free prediction. |
| 11 | Slice scope | Optional integer: `0` transport, `1` wireless, `2` end-to-end; default `2` | Chooses whether slice bandwidth shares apply to physical links between servers, AP uplink and downlink capacities, or both. |
| 12 | User allocation per slice | Optional comma-separated finite percentages, for example `60,40`; each value must be in `(0, 100]`, values must sum to `100`, and the count must match parameter 13 | Assigns the configured percentage of users to each slice. If omitted, users are divided equally among the slices. Fractional user counts are resolved using the largest-remainder method. |
| 13 | Bandwidth share per slice | Optional comma-separated finite percentages, for example `70,30`; each value must be in `(0, 100]` and values must sum to `100`; default `100` | Defines the number of slices and the percentage of capacity reserved for each slice within the selected scope. The default creates one slice with all capacity. |
| 14 | Slice mode | Optional integer flag: `0` static, `1` dynamic; default `1` | Static mode keeps each slice at its configured share. Dynamic mode lets active slices borrow capacity reserved for slices that are idle on the same link or AP. |
| 15 | Reallocation delay | Optional finite non-negative number (`>= 0`), in seconds; default `2` | Sets how long traffic pauses while a dynamic slice allocation is applied. The delay affects all active transfers sharing the reallocated transport link and/or wireless AP direction, according to parameter 11. It is ignored in static mode and on network portions outside the slice scope. |
| 16 | VM destination type | Optional integer: `0` edge servers only, `1` end devices only, `2` hybrid; default `2` | Restricts the eligible nodes that may host a migrated VM. Hybrid mode considers both server cloudlets and connected mobile devices with sufficient resources. |
| 17 | Output mode | Optional, case-insensitive text: `summary`, `full`, or `none`; default `summary` | Controls whether a run writes bounded aggregate results, aggregate results plus detailed event records, or no result files. |

### Example

```text
1 290538 0 0 10 11 0 61 0 0 0 60,40 70,30 1 2.5 2 summary
```

This example configures the simulation as follows:

| Parameter | Value | Meaning |
| --- | ---: | --- |
| Migration | `1` | Enables service migration. |
| Random seed | `290538` | Makes random decisions reproducible. |
| Migration point | `0` | Uses the fixed migration-point policy. |
| Migration strategy | `0` | Selects the destination with the lowest latency. |
| Users | `10` | Simulates ten mobile users. |
| Cloudlet bandwidth | `11` | Sets the base bandwidth between server cloudlets to 11 Mbps. |
| Migration policy | `0` | Uses complete-VM cold migration. |
| Cloudlet latency | `61` | Sets the base latency between server cloudlets to 61. |
| Mobility prediction | `0` | Disables look-ahead mobility prediction. |
| Prediction error | `0` | Adds no mobility-prediction error. |
| Slice scope | `0` | Applies slicing only to the transport network between servers. |
| User allocation | `60,40` | Assigns 60% of users to slice 0 and 40% to slice 1: six and four users, respectively, in this ten-user example. |
| Slice shares | `70,30` | Gives slice 0 70% and slice 1 30% of the bandwidth in the selected scope. |
| Slice mode | `1` | Enables dynamic borrowing of idle slice capacity. |
| Reallocation delay | `2.5` | Pauses affected traffic for 2.5 seconds whenever the set of active slices changes and dynamic bandwidth shares must be reapplied. |
| VM destinations | `2` | Allows both edge servers and eligible end devices to host migrated VMs. |
| Output mode | `summary` | Writes bounded end-of-run metrics and omits detailed event records. |

#### Simulation concepts used in this example

**Migration** means moving a mobile user's application VM away from its
current host to a better host as the user travels. Parameter 1 is set to `1`,
so MobFogSim may initiate that relocation when all migration conditions are
met; this does not mean that a migration is forced at every handoff.

The **migration point** controls *when* the simulator starts considering a
migration. Value `0` uses the fixed policy: the user must enter a predefined
area near the edge of the current AP's coverage and be moving through an
eligible directional zone. Value `1` would adjust this trigger according to
the user's speed and the estimated time needed to migrate, allowing a slower
transfer or a faster-moving user to be considered earlier.

The **migration strategy** controls *where* the VM should move after the
trigger. Value `0` evaluates candidate server cloudlets and selects the one
with the lowest estimated latency cost. This is distinct from parameter 16:
the strategy chooses the destination server region, while the hybrid VM
destination policy may use a suitable connected end device as the final host.

The **migration policy** controls *what* the simulator transfers and *how* it
performs that transfer. Value `0` models a cold migration of the complete VM,
so the entire VM state is transferred and the model counts the migration
interval as downtime. Container and live migration policies model different
transfer sizes and downtime behaviour.

**Mobility prediction** lets destination selection use a future location from
the user's trace instead of only the current position. Its value is `0` here,
so there is no look-ahead. A value such as `10` would make the selection logic
consider the predicted position ten seconds ahead, limited by the available
trace.

The **prediction error** represents uncertainty in that predicted position.
Value `0` leaves the selected trace coordinate unchanged. A positive value
displaces it by that many metres in a pseudo-random direction; the random seed
makes this perturbation reproducible.

The **slice scope** controls which parts of the network enforce the configured
slice shares. Value `0` applies slicing only to the transport network: physical
links between server cloudlets are divided among slices, while wireless AP
uplink and downlink capacity remains shared without slice reservations. Value
`1` would slice only the wireless network, and `2` would apply the same slicing
model end to end.

The **user allocation** determines how the simulated population is distributed
among slices. With ten users and `60,40`, six users belong to slice 0 and four
belong to slice 1. If a percentage produces a fractional user count, MobFogSim
uses the largest remainders to ensure that every user is assigned exactly once.

The **slice shares** specify how much capacity each slice reserves inside the
selected scope. Value `70,30` reserves 70% of each sliced transport link for
slice 0 and 30% for slice 1. These percentages describe network capacity and
are independent of the `60,40` population split, so a slice may serve fewer
users while receiving a larger bandwidth share.

The **slice mode** controls whether unused reservations can be reused. Value
`1` enables dynamic slicing, allowing a slice with active migrations to borrow
capacity from slices that are idle on the same directed transport link. Value `0` keeps
every slice limited to its configured share. Concurrent transfers within a
slice share that capacity.

The **reallocation delay** models the control-plane time needed to install a
new dynamic allocation. When a slice becomes active or idle on a sliced
resource, every active transfer on that directed transport link or AP direction
pauses for the configured number of seconds before using the new rates. A new
active-slice change during an unfinished reallocation restarts the delay. The
setting does not affect static slices or unsliced portions of the network.

The **VM destinations** setting limits the types of hosts considered after the
migration strategy selects a destination server region. Value `2` enables
hybrid placement: MobFogSim may retain the selected edge server or choose a
closer connected end device with sufficient host resources. Values `0` and `1`
restrict placement to edge servers or end devices, respectively.

Mobility traces are always read from `input`, using `input/inputOrder.csv` as
the order manifest. Every simulation receives an automatically generated,
unique output directory below `runs/`; its name contains the seed, timestamp,
and a short unique identifier.

Every enabled simulator-generated file is written beneath the run output
directory. The selected mode determines which files are written:

| Mode | Output behaviour |
| --- | --- |
| `summary` | Default. Publishes `report/summary.csv` and `report/manifest.json`, containing the versioned configuration, units, end-of-run averages, totals, network usage, slice, queue, migration, and tuple-loss statistics. |
| `full` | Publishes the same report after closing all detailed latency, route, mobility, handoff, migration, module-creation, loop-delay, lost-tuple, and console-trace records. |
| `none` | Disables every output file. The configured run root may still be created, but it remains empty. |

The report is first written and verified in a temporary directory, then
published with one same-filesystem rename. A successful manifest records the
report schema version, status, complete configuration, seed, units, output
mode, summary checksum, and detailed-output status. If a run fails, its report
contains only a `manifest.json` with `status` set to `failed`; full-mode event
output is explicitly marked `incomplete` and no success summary is published.

### Slice reconfiguration metrics

Dynamic slicing with multiple configured slices reports three additional
metrics. **Number of slice reconfigurations** counts active-slice set changes
on sliced transport links and wireless AP directions, including transitions to
or from an entirely idle resource. **Slice outage** is that count multiplied by the configured
reallocation delay and is reported in seconds. **Received bandwidth** is
reported for each slice as the sum of its actual allocations at every
reconfiguration, in bits per second; transport and wireless allocations are
combined when end-to-end scope is selected.

Summary and full output modes write these values as named columns in
`report/summary.csv`, including the per-slice received-bandwidth map.
Wireless queues are hard-limited to 10,000 waiting tuples per user and AP
direction. The summary also records the fixed limit, final aggregate queue
size, maximum aggregate queue size, maximum single-direction depth, and number
of tuples dropped at the limit.

## Input

Mobility data are read from `.csv` files generated by mobility simulators such
as SUMO (Simulation of Urban MObility). Trace filenames must match
`*log.csv`; unrelated files in the same directory are ignored.

The `input` directory contains an example offline mobility dataset from the
[Luxembourg SUMO Traffic (LuST) Scenario](https://github.com/lcodeca/LuSTScenario).

Each non-empty trace must contain exactly five tab-separated, finite numeric
columns: time in seconds, direction in radians, x-position, y-position, and
speed in metres per second. Time must be non-negative and non-decreasing, and
speed must be non-negative.

The order manifest is also tab-separated, despite its `.csv` extension. It
contains zero-based indices into the lexicographically sorted `*log.csv`
files. Entries may span multiple rows and are applied in exactly the order in
which they are written. The manifest must contain at least one entry per
simulated user. For example, `2<TAB>0<TAB>1` assigns trace 2 to user 0, trace 0
to user 1, and trace 1 to user 2. Missing files, malformed rows, insufficient
entries, and invalid trace indices cause start-up to fail with a contextual
`MobilityInputException`.

The first trace timestamp is the user's simulation-entry time in seconds. Until
that time, the user remains pending: it has no broker, VM, application, or
network association, and its sensors and actuators are disabled. At the entry
event, the simulator attempts to associate the user with an access point. Once
association succeeds, it creates the broker, VM, application, mappings, and
peripheral bindings. If the first position is outside wireless coverage, the
user continues along its mobility trace and retries the association process.

For example, `input/1702log.csv` begins as follows. The displayed columns are
tab-separated in the file.

```text
2.1     -1.51173    10370.1    2233.67    0
3.1     -1.68755    10369.2    2234.57    2.34286
4.1     -2.09045    10366.9    2236.81    4.11058
5.1     -2.36655    10363.1    2240.26    6.03548
6.1     -2.41103    10357.9    2244.92    7.94067
7.1     -2.43504    10350.9    2250.8     10.0297
8.1     -2.43476    10342.4    2258.09    12.1859
9.1     -2.42554    10332.5    2266.75    14.044
10.1    -2.42553    10323.3    2274.71    10.638
...
```

## How to cite MobFogSim

Puliafito, C. et al. (2020). ‘MobFogSim: Simulation of mobility and migration
for fog computing’. *Simulation Modelling Practice and Theory*, 101, 102062.
[DOI: 10.1016/j.simpat.2019.102062](https://doi.org/10.1016/j.simpat.2019.102062)

BibTeX:

```bibtex
@article{puliafito2020mobfogsim,
  title={MobFogSim: Simulation of mobility and migration for fog computing},
  author={Puliafito, Carlo and Gon{\c{c}}alves, Diogo M and Lopes, M{\'a}rcio M and Martins, Leonardo L and Madeira, Edmundo and Mingozzi, Enzo and Rana, Omer and Bittencourt, Luiz F},
  journal={Simulation Modelling Practice and Theory},
  volume={101},
  pages={102062},
  year={2020},
  publisher={Elsevier}
}
```

### Additional papers on MobFogSim features

Gonçalves, D. et al. (2020). ‘Dynamic network slicing in fog computing for
mobile users in MobFogSim’. *2020 IEEE/ACM 13th International Conference on
Utility and Cloud Computing (UCC)*, 237–246.
[DOI: 10.1109/UCC48980.2020.00042](https://doi.org/10.1109/UCC48980.2020.00042)

BibTeX:

```bibtex
@inproceedings{gonccalves2020dynamic,
  title={Dynamic network slicing in fog computing for mobile users in MobFogSim},
  author={Gon{\c{c}}alves, Diogo and Puliafito, Carlo and Mingozzi, Enzo and Rana, Omer and Bittencourt, Luiz and Madeira, Edmundo},
  booktitle={2020 IEEE/ACM 13th International Conference on Utility and Cloud Computing (UCC)},
  pages={237--246},
  year={2020},
  organization={IEEE}
}
```

Gonçalves, D. et al. (2023). ‘End-to-end network slicing in vehicular clouds
using the MobFogSim simulator’. *Ad Hoc Networks*, 141, 103096.
[DOI: 10.1016/j.adhoc.2023.103096](https://doi.org/10.1016/j.adhoc.2023.103096)

BibTeX:

```bibtex
@article{gonccalves2023end,
  title={End-to-end network slicing in vehicular clouds using the MobFogSim simulator},
  author={Gon{\c{c}}alves, Diogo M and Puliafito, Carlo and Mingozzi, Enzo and Bittencourt, Luiz F and Madeira, Edmundo RM},
  journal={Ad Hoc Networks},
  volume={141},
  pages={103096},
  year={2023},
  publisher={Elsevier}
}
```
