# Full user-scaling benchmark results

This report summarizes the 67 process-isolated simulations in
`benchmarks/12-10-2026`.
The suite covers the established scenarios at 1, 10, 100, and 200 users. All
fixtures produced one terminal result row and completed without standard-output,
standard-error, or run-directory output.

The tables below summarize the selected metrics from [`results.tsv`](results.tsv).
The richer per-fixture `simulation.properties` snapshots also contain indexed
per-user, per-loop, per-slice, per-device, and tuple-type series. Those series
are not duplicated wholesale here: the largest snapshot has 7,288 properties.
Their aggregate terminal metrics are summarized below, and the original
snapshots remain the authoritative source for individual indexed observations.

## Executive summary

- The 67 fixtures took **12 h 51 min 26.10 s** of summed fixture wall time.
  Mean wall time was **11 min 30.84 s** per independent JVM.
- Mean RAM usage was **2.81 GiB**. The largest peak was **7.17 GiB** in
  `u200-error-500`.
- The simulations dispatched **14,744,585,938 events** and generated
  **2,424,948,993 tuples**, equivalent to about **318,553 events/s** over the
  summed fixture wall time.
- The snapshots recorded **34,245 handoffs**, **4,789 migrations**, and an
  aggregate tuple-loss rate of **2.8997%** (111,669,109 of 3,851,091,138
  observed tuples).
- There were **755,362 slice reconfigurations** and **1,558,748 seconds** of
  aggregate slice outage. The wireless/end-to-end scope fixtures account for
  98.47% of reconfigurations and 95.43% of outage.
- Every wireless queue finished empty. Only `u200-scope-end-to-end` and
  `u200-scope-wireless` dropped tuples: 4,070 and 5,376 respectively, for
  **9,446 total drops**. Both reached the 10,000-tuple per-direction limit.
- The longest fixture was `u200-scope-end-to-end` at **42 min 9.94 s**. It
  represented 5.47% of total serial fixture time; no single fixture dominated
  the suite.

Each fixture was measured once with no warm-up, so wall time and RAM usage are
descriptive observations rather than distributions or confidence estimates.

## Environment and reproducibility

| Property | Value |
|---|---|
| Dataset created | 2026-10-12 23:39:20 UTC |
| Fixture selection | `user-scaling` |
| Fixtures | 67: 13 at 1 user and 18 each at 10, 100, and 200 users |
| Compact benchmark schema | `4` |
| Simulation snapshot schema | `13` (`schema.version=8`) |
| Git commit | `6eb5ff4e0e7891b2a0295afb3d6fcbf084bf591a` |
| Worktree | Dirty at measurement time |
| Source-tree SHA-256 | `99b424214ead36d5b1997cf93e59c4710dd683f5f7e403ab6dcd831fd87f5dc4` |
| Input-files SHA-256 | `825c80555ffb68517e40508f54d80b64d10a3f7436a7ed4f19a51f50c9ab1e2a` |
| Operating system | Linux 6.8.0-138-generic, x86-64 |
| CPU | Intel Core i7-8565U at 1.80 GHz; 8 logical processors |
| Physical memory | 15.44 GiB |
| Java | OpenJDK 21.0.12.1 |
| JVM | `-Xms256m -Xmx14g`, UTF-8, `en_GB`, UTC |
| Measurement | Fresh JVM per fixture; no warm-up; one measured trial |
| Simulation seed | `290538` |
| Output mode | `none` |

All rows carry the same input hash shown above. Per-fixture characterization
hashes are preserved in [`results.tsv`](results.tsv), while exact source and
input file hashes are in [`source-files.sha256`](source-files.sha256) and
[`input-files.sha256`](input-files.sha256).

## Aggregate results by workload

RAM usage is the maximum resident set size of each fresh JVM. Mean RAM usage is
the mean of independent peaks, not simultaneous memory use.

| Workload | Fixtures | Total wall | Mean wall | Max wall | Mean RAM usage | Maximum RAM usage | Events total |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 user | 13 | 2 min 37.88 s | 12.14 s | 13.58 s | 0.55 GiB | 0.70 GiB | 242,007,289 |
| 10 users | 18 | 18 min 54.99 s | 1 min 3.05 s | 1 min 13.79 s | 2.38 GiB | 3.08 GiB | 757,466,623 |
| 100 users | 18 | 3 h 10 min 3.21 s | 10 min 33.51 s | 13 min 40.64 s | 3.11 GiB | 3.74 GiB | 4,831,662,362 |
| 200 users | 18 | 9 h 19 min 50.02 s | 31 min 6.11 s | 42 min 9.94 s | 4.58 GiB | 7.17 GiB | 8,913,449,664 |
| **All** | **67** | **12 h 51 min 26.10 s** | **11 min 30.84 s** | **42 min 9.94 s** | **2.81 GiB** | **7.17 GiB** | **14,744,585,938** |

| Workload | Reconfigurations | Outage (s) |
| --- | ---: | ---: |
| 1 user | 0 | 0 |
| 10 users | 39,626 | 81,572 |
| 100 users | 286,078 | 588,396 |
| 200 users | 429,658 | 888,780 |
| **All** | **755,362** | **1,558,748** |

### Base-fixture scaling

There is no `u1-base` fixture. The otherwise matching base cases show the
following measured scaling:

| Fixture | Wall time | RAM usage | Events |
| --- | ---: | ---: | ---: |
| `u10-base` | 1 min 3.70 s | 2.64 GiB | 43,133,491 |
| `u100-base` | 10 min 27.11 s | 3.49 GiB | 277,455,625 |
| `u200-base` | 30 min 45.34 s | 5.61 GiB | 514,763,888 |

## Terminal simulation metrics

The duration fields in the snapshots are milliseconds; the tables below
convert them to seconds where labelled. A “median average” is the median of the
non-zero fixture-level averages in that workload. Post-connection delay remains
in the simulator-reported unit because the snapshot does not identify one.

### Mobility and migration

| Workload | Handoffs | Migrations | Median avg migration (s) | Max migration (s) | Median avg downtime (s) | Max downtime (s) |
|---|---:|---:|---:|---:|---:|---:|
| 1 user | 91 | 11 | 14.021 | 14.021 | 2.103 | 14.021 |
| 10 users | 1,261 | 301 | 140.601 | 1,329.187 | 21.191 | 616.439 |
| 100 users | 11,272 | 1,728 | 178.708 | 1,322.108 | 27.468 | 616.438 |
| 200 users | 21,621 | 2,749 | 263.348 | 1,323.108 | 40.281 | 888.008 |

> **Interpreting the migration times.** The increase from 14.021 s for one
> user to 140.601 s for ten users is not a fixed tenfold cost per user. Each
> table entry is the median of the fixture-level arithmetic means, so a small
> number of very long migrations can raise those means sharply. For example,
> `u10-base` completed 19 migrations with a mean of 141.285 s: most of its
> latest per-user observations are between 8.578 s and 18.218 s, but it also
> records migrations of 660.607 s and 1,327.173 s. With several users, active
> migrations may share the same transport link and slice; the scheduler divides
> the available bandwidth among concurrent transfers and includes any slice
> reallocation pauses. Migration time is the simulated data-transfer duration
> plus the fixed propagation/setup delay. Therefore, values near 1,327 s
> represent long simulated transfers at low effective bandwidth under
> contention, not 22 minutes of benchmark wall-clock processing and not a
> simple multiplication by the user count.

### Service availability

| Workload | Median avg without VM (s) | Max without VM (s) | Median avg without connection (s) | Max without connection (s) | Median avg post-connection delay (raw) | Max post-connection delay (raw) |
|---|---:|---:|---:|---:|---:|---:|
| 1 user | 0.000 | 0.000 | 0.966 | 1.000 | 0.000 | 0.000 |
| 10 users | 383.533 | 1,549.626 | 0.956 | 1.000 | 497,887.127 | 1,177,006.338 |
| 100 users | 461.007 | 1,554.523 | 0.960 | 1.000 | 520,248.806 | 1,284,720.249 |
| 200 users | 561.638 | 1,555.538 | 0.962 | 1.000 | 580,214.411 | 1,667,717.044 |

### Application latency and tuple loss

“Observed maximum” is the largest individual `loops.N.maximum_ms` in the
workload, whereas “median avg loop maximum” is the median fixture-level mean of
all loop maxima.

| Workload | Median avg loop delay (ms) | Median avg loop maximum (ms) | Observed max loop delay (ms) | Tuples lost | Tuples total | Loss rate |
|---|---:|---:|---:|---:|---:|---:|
| 1 user | 17.143 | 27.688 | 35.562 | 293,685 | 10,723,117 | 2.7388% |
| 10 users | 20.798 | 43.784 | 6,721.972 | 2,647,645 | 140,753,504 | 1.8811% |
| 100 users | 21.658 | 57.154 | 35,016.976 | 37,220,711 | 1,293,125,748 | 2.8784% |
| 200 users | 24.952 | 79.522 | 1,360,227.417 | 71,507,068 | 2,406,488,769 | 2.9714% |
| **All** | — | — | **1,360,227.417** | **111,669,109** | **3,851,091,138** | **2.8997%** |


### Network accounting

Usage is byte-milliseconds. Tuple usage includes queue, transfer, and
propagation phases; migration usage covers migration byte transfer. The total
network usage is tuple usage plus migration usage.

| Workload | Total network usage (byte-ms) | Migration duration (s) |
| --- | ---: | ---: |
| 1 user | 1.948710e+13 | 148.551 |
| 10 users | 4.767693e+15 | 31,113.379 |
| 100 users | 6.922939e+16 | 419,993.944 |
| 200 users | 1.562019e+17 | 964,233.245 |

| Workload | Queue (s) |
| --- | ---: |
| 1 user | 0.061 |
| 10 users | 4,982,162.299 |
| 100 users | 44,971,653.818 |
| 200 users | 87,861,738.539 |

## Complete compact metrics

The following tables combine selected columns from `results.tsv` with metrics
derived from each `simulation.properties` snapshot. The retained fixed
infrastructure and output metrics are listed after the tables. Wall time is
formatted for readability; its source precision is two decimal places in
`wall_seconds`.

### Mobility and application latency

| Workload | Handoffs | Migrations | Median avg migration (s) | Median avg loop delay (ms) | Average slice reconfigurations | Outage (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 user | 91 | 11 | 14.021 | 17.143 | 0.000 | 0 |
| 10 users | 1,261 | 301 | 140.601 | 20.798 | 2,201.444 | 81,572 |
| 100 users | 11,272 | 1,728 | 178.708 | 21.658 | 15,893.222 | 588,396 |
| 200 users | 21,621 | 2,749 | 263.348 | 24.952 | 23,869.889 | 888,780 |

`Avg migration` is the snapshot running average of completed migrations; a
zero means that the fixture completed no migration. `Avg loop delay` is the
arithmetic mean of its `loops.N.average_ms` values. The workload-level
`Median avg migration` and `Median avg loop delay` metrics are respectively
the median of the non-zero per-fixture migration averages and the median of
all per-fixture loop-delay averages shown below. `Reconfigurations` and
`Outage` are exact terminal values in the detailed tables. The summary reports
mean slice reconfigurations per fixture and summed outage for each workload.

#### 1 user

| Fixture | Handoffs | Migrations | Avg migration (s) | Avg loop delay (ms) | Reconfigurations | Outage (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `u1-migration-off` | 7 | 0 | 0.000 | 24.751 | 0 | 0.0 |
| `u1-point-speed` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-strategy-cloudlet-distance` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-strategy-ap-distance` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-policy-complete-vm` | 7 | 1 | 14.021 | 16.869 | 0 | 0.0 |
| `u1-policy-container` | 7 | 1 | 8.416 | 16.869 | 0 | 0.0 |
| `u1-prediction-60` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-error-500` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-scope-end-to-end` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-scope-wireless` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-slicing-static` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-destination-edge` | 7 | 1 | 14.021 | 17.143 | 0 | 0.0 |
| `u1-destination-device` | 7 | 0 | 0.000 | 16.869 | 0 | 0.0 |

#### 10 users

| Fixture | Handoffs | Migrations | Avg migration (s) | Avg loop delay (ms) | Reconfigurations | Outage (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `u10-base` | 70 | 19 | 141.285 | 20.798 | 44 | 88.0 |
| `u10-migration-off` | 71 | 0 | 0.000 | 26.233 | 0 | 0.0 |
| `u10-point-speed` | 70 | 20 | 43.786 | 20.869 | 44 | 88.0 |
| `u10-strategy-cloudlet-distance` | 70 | 16 | 53.838 | 20.599 | 34 | 68.0 |
| `u10-strategy-ap-distance` | 70 | 19 | 48.011 | 20.690 | 42 | 84.0 |
| `u10-policy-complete-vm` | 70 | 14 | 87.123 | 20.869 | 32 | 64.0 |
| `u10-policy-container` | 70 | 21 | 96.570 | 20.467 | 42 | 84.0 |
| `u10-prediction-60` | 70 | 18 | 50.430 | 20.812 | 42 | 84.0 |
| `u10-error-500` | 70 | 19 | 141.270 | 20.825 | 44 | 88.0 |
| `u10-scope-end-to-end` | 70 | 19 | 141.266 | 3,082.933 | 19,342 | 38,684.0 |
| `u10-scope-wireless` | 70 | 18 | 61.032 | 3,134.337 | 19,730 | 39,460.0 |
| `u10-allocation-equal-two` | 70 | 19 | 141.285 | 20.798 | 44 | 88.0 |
| `u10-three-slices-weighted` | 70 | 19 | 144.379 | 20.794 | 44 | 88.0 |
| `u10-three-slices-weighted-delay-60` | 70 | 16 | 151.757 | 20.547 | 40 | 2,400.0 |
| `u10-three-slices-equal` | 70 | 19 | 144.379 | 20.794 | 44 | 88.0 |
| `u10-slicing-static` | 70 | 19 | 140.601 | 20.502 | 0 | 0.0 |
| `u10-destination-edge` | 70 | 21 | 14.501 | 20.429 | 42 | 84.0 |
| `u10-destination-device` | 70 | 5 | 606.183 | 20.805 | 16 | 32.0 |

#### 100 users

| Fixture | Handoffs | Migrations | Avg migration (s) | Avg loop delay (ms) | Reconfigurations | Outage (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `u100-base` | 626 | 98 | 184.045 | 21.658 | 276 | 552.0 |
| `u100-migration-off` | 630 | 0 | 0.000 | 27.562 | 0 | 0.0 |
| `u100-point-speed` | 626 | 79 | 125.449 | 21.932 | 256 | 512.0 |
| `u100-strategy-cloudlet-distance` | 626 | 110 | 86.784 | 21.167 | 260 | 520.0 |
| `u100-strategy-ap-distance` | 626 | 120 | 98.681 | 21.238 | 290 | 580.0 |
| `u100-policy-complete-vm` | 626 | 116 | 88.971 | 22.474 | 268 | 536.0 |
| `u100-policy-container` | 626 | 146 | 88.621 | 22.552 | 314 | 628.0 |
| `u100-prediction-60` | 626 | 101 | 178.713 | 21.531 | 280 | 560.0 |
| `u100-error-500` | 626 | 95 | 189.375 | 21.737 | 274 | 548.0 |
| `u100-scope-end-to-end` | 626 | 101 | 178.708 | 3,106.689 | 141,258 | 282,516.0 |
| `u100-scope-wireless` | 626 | 103 | 183.118 | 3,100.310 | 141,002 | 282,004.0 |
| `u100-allocation-equal-two` | 626 | 98 | 184.045 | 21.658 | 276 | 552.0 |
| `u100-three-slices-weighted` | 626 | 96 | 172.898 | 21.550 | 274 | 548.0 |
| `u100-three-slices-weighted-delay-60` | 626 | 97 | 222.886 | 21.559 | 280 | 16,800.0 |
| `u100-three-slices-equal` | 626 | 96 | 172.898 | 21.547 | 274 | 548.0 |
| `u100-slicing-static` | 626 | 87 | 259.833 | 21.513 | 0 | 0.0 |
| `u100-destination-edge` | 626 | 146 | 12.957 | 21.312 | 300 | 600.0 |
| `u100-destination-device` | 626 | 39 | 649.683 | 21.694 | 196 | 392.0 |

#### 200 users

| Fixture | Handoffs | Migrations | Avg migration (s) | Avg loop delay (ms) | Reconfigurations | Outage (s) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `u200-base` | 1,201 | 147 | 279.387 | 24.950 | 512 | 1,024.0 |
| `u200-migration-off` | 1,204 | 0 | 0.000 | 31.032 | 0 | 0.0 |
| `u200-point-speed` | 1,201 | 124 | 226.273 | 24.955 | 478 | 956.0 |
| `u200-strategy-cloudlet-distance` | 1,201 | 167 | 142.058 | 24.331 | 468 | 936.0 |
| `u200-strategy-ap-distance` | 1,201 | 182 | 166.064 | 24.211 | 534 | 1,068.0 |
| `u200-policy-complete-vm` | 1,201 | 191 | 154.568 | 27.206 | 524 | 1,048.0 |
| `u200-policy-container` | 1,201 | 265 | 141.628 | 27.883 | 614 | 1,228.0 |
| `u200-prediction-60` | 1,201 | 154 | 270.019 | 24.868 | 516 | 1,032.0 |
| `u200-error-500` | 1,201 | 148 | 277.154 | 25.116 | 512 | 1,024.0 |
| `u200-scope-end-to-end` | 1,201 | 153 | 260.842 | 3,206.430 | 211,690 | 423,380.0 |
| `u200-scope-wireless` | 1,201 | 154 | 263.348 | 3,253.172 | 210,772 | 421,544.0 |
| `u200-allocation-equal-two` | 1,201 | 147 | 279.366 | 24.954 | 510 | 1,020.0 |
| `u200-three-slices-weighted` | 1,201 | 147 | 268.538 | 24.922 | 508 | 1,016.0 |
| `u200-three-slices-weighted-delay-60` | 1,201 | 145 | 311.474 | 24.860 | 508 | 30,480.0 |
| `u200-three-slices-equal` | 1,201 | 147 | 268.539 | 24.921 | 508 | 1,016.0 |
| `u200-slicing-static` | 1,201 | 110 | 257.704 | 24.844 | 0 | 0.0 |
| `u200-destination-edge` | 1,201 | 291 | 13.712 | 24.373 | 590 | 1,180.0 |
| `u200-destination-device` | 1,201 | 77 | 650.113 | 25.008 | 414 | 828.0 |

### Execution and event processing

#### 1 user

| Fixture | Wall time | RAM usage (KiB) | Events total |
| --- | ---: | ---: | ---: |
| `u1-migration-off` | 13.58 s | 733,232 | 19,518,838 |
| `u1-point-speed` | 12.47 s | 497,080 | 18,846,661 |
| `u1-strategy-cloudlet-distance` | 12.37 s | 616,360 | 18,846,661 |
| `u1-strategy-ap-distance` | 12.59 s | 621,096 | 18,846,661 |
| `u1-policy-complete-vm` | 9.38 s | 412,780 | 17,058,546 |
| `u1-policy-container` | 9.68 s | 429,280 | 17,058,956 |
| `u1-prediction-60` | 12.62 s | 599,852 | 18,846,661 |
| `u1-error-500` | 12.69 s | 534,460 | 18,846,661 |
| `u1-scope-end-to-end` | 12.90 s | 600,312 | 18,846,661 |
| `u1-scope-wireless` | 12.62 s | 604,160 | 18,846,661 |
| `u1-slicing-static` | 12.41 s | 591,496 | 18,846,661 |
| `u1-destination-edge` | 12.57 s | 603,892 | 18,846,661 |
| `u1-destination-device` | 12.00 s | 638,908 | 18,751,000 |

#### 10 users

| Fixture | Wall time | RAM usage (KiB) | Events total |
| --- | ---: | ---: | ---: |
| `u10-base` | 1.06 min | 2,767,436 | 43,133,491 |
| `u10-migration-off` | 1.23 min | 1,571,568 | 50,193,572 |
| `u10-point-speed` | 1.12 min | 2,588,836 | 44,072,023 |
| `u10-strategy-cloudlet-distance` | 1.11 min | 2,419,768 | 44,153,181 |
| `u10-strategy-ap-distance` | 1.07 min | 2,800,524 | 44,416,079 |
| `u10-policy-complete-vm` | 38.12 s | 1,610,212 | 28,450,948 |
| `u10-policy-container` | 36.78 s | 1,528,672 | 27,719,696 |
| `u10-prediction-60` | 1.01 min | 2,707,208 | 42,913,089 |
| `u10-error-500` | 1.13 min | 2,915,884 | 43,392,433 |
| `u10-scope-end-to-end` | 1.15 min | 2,470,188 | 43,575,741 |
| `u10-scope-wireless` | 1.13 min | 2,449,560 | 43,233,428 |
| `u10-allocation-equal-two` | 1.06 min | 2,885,408 | 43,133,491 |
| `u10-three-slices-weighted` | 1.08 min | 2,769,620 | 43,112,196 |
| `u10-three-slices-weighted-delay-60` | 1.06 min | 2,452,796 | 42,993,474 |
| `u10-three-slices-equal` | 1.15 min | 2,420,108 | 43,112,713 |
| `u10-slicing-static` | 1.04 min | 2,505,316 | 43,037,349 |
| `u10-destination-edge` | 1.12 min | 2,756,304 | 43,228,037 |
| `u10-destination-device` | 1.13 min | 3,234,104 | 43,595,682 |

#### 100 users

| Fixture | Wall time | RAM usage (KiB) | Events total |
| --- | ---: | ---: | ---: |
| `u100-base` | 10.45 min | 3,663,796 | 277,455,625 |
| `u100-migration-off` | 13.68 min | 809,336 | 353,802,572 |
| `u100-point-speed` | 10.77 min | 3,271,792 | 275,972,008 |
| `u100-strategy-cloudlet-distance` | 11.20 min | 3,204,328 | 287,140,404 |
| `u100-strategy-ap-distance` | 11.81 min | 2,705,908 | 294,318,180 |
| `u100-policy-complete-vm` | 5.68 min | 3,566,728 | 139,953,322 |
| `u100-policy-container` | 5.37 min | 3,921,924 | 138,051,613 |
| `u100-prediction-60` | 10.64 min | 3,288,136 | 278,420,650 |
| `u100-error-500` | 10.61 min | 3,654,148 | 277,429,493 |
| `u100-scope-end-to-end` | 11.61 min | 3,499,892 | 275,548,681 |
| `u100-scope-wireless` | 11.93 min | 3,278,332 | 275,983,467 |
| `u100-allocation-equal-two` | 10.72 min | 3,618,000 | 277,455,625 |
| `u100-three-slices-weighted` | 10.92 min | 3,206,576 | 277,263,800 |
| `u100-three-slices-weighted-delay-60` | 11.00 min | 3,265,364 | 278,227,658 |
| `u100-three-slices-equal` | 10.85 min | 3,673,204 | 277,279,575 |
| `u100-slicing-static` | 11.07 min | 3,212,780 | 280,641,832 |
| `u100-destination-edge` | 10.91 min | 3,292,988 | 287,872,957 |
| `u100-destination-device` | 10.82 min | 3,575,080 | 278,844,900 |

#### 200 users

| Fixture | Wall time | RAM usage (KiB) | Events total |
| --- | ---: | ---: | ---: |
| `u200-base` | 30.76 min | 5,882,964 | 514,763,888 |
| `u200-migration-off` | 36.63 min | 1,343,980 | 652,926,684 |
| `u200-point-speed` | 30.90 min | 5,458,032 | 512,848,643 |
| `u200-strategy-cloudlet-distance` | 32.33 min | 4,256,156 | 526,701,730 |
| `u200-strategy-ap-distance` | 33.98 min | 6,347,072 | 533,007,535 |
| `u200-policy-complete-vm` | 15.00 min | 3,963,144 | 236,261,012 |
| `u200-policy-container` | 14.90 min | 4,891,424 | 242,555,866 |
| `u200-prediction-60` | 31.78 min | 4,115,936 | 516,170,061 |
| `u200-error-500` | 30.56 min | 7,518,976 | 514,197,177 |
| `u200-scope-end-to-end` | 42.17 min | 4,441,868 | 518,005,587 |
| `u200-scope-wireless` | 41.30 min | 5,784,216 | 517,125,882 |
| `u200-allocation-equal-two` | 30.22 min | 4,007,052 | 514,754,946 |
| `u200-three-slices-weighted` | 30.93 min | 4,229,748 | 514,829,615 |
| `u200-three-slices-weighted-delay-60` | 31.36 min | 5,505,672 | 516,178,011 |
| `u200-three-slices-equal` | 31.92 min | 4,270,268 | 514,879,284 |
| `u200-slicing-static` | 29.89 min | 4,308,668 | 519,176,124 |
| `u200-destination-edge` | 30.71 min | 5,346,424 | 533,002,225 |
| `u200-destination-device` | 34.50 min | 4,850,024 | 516,065,394 |


### Infrastructure and topology

Infrastructure is fixed for all fixtures; only mobile-user and CloudSim entity
counts scale with workload.

| Workload | Servers | Access points | Users | Network nodes | Network links | Server transport routes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 user | 144 | 144 | 1 | 288 | 10,440 | 20,592 |
| 10 users | 144 | 144 | 10 | 288 | 10,440 | 20,592 |
| 100 users | 144 | 144 | 100 | 288 | 10,440 | 20,592 |
| 200 users | 144 | 144 | 200 | 288 | 10,440 | 20,592 |

### Output sizes

For every fixture, all four output-size metrics were exactly zero:

| Fixtures | `stdout_bytes` | `stderr_bytes` | `run_output_bytes` | `total_output_bytes` |
|---:|---:|---:|---:|---:|
| 67 | 0 | 0 | 0 | 0 |

## Detailed snapshot metric coverage

Each `<fixture>/simulation.properties` file records the complete stable
terminal snapshot. In addition to the metrics summarized in this report, it
contains:

- exact configuration, seed, JVM identity, simulation clock, heap ceiling,
  generated identifiers, and event/tag counts;
- every application loop's average and maximum delay and every tuple type's CPU
  time;
- aggregate and per-slice migration counts, duration, bytes, byte-ms usage,
  timing, downtime, reconfiguration, outage, and received bandwidth;
- aggregate and per-slice wireless tuple counts, bytes, queue/transfer/
  propagation durations, and total/average network delay;
- latest and maximum per-user migration, downtime, without-VM,
  without-connection, and post-connection-delay observations; and
- terminal topology, tuple-loss, wireless-queue, output, and reproducibility
  values.

Metric definitions and units are documented in
[`../../docs/architecture/decisions/0005-terminal-metric-definitions.md`](../../docs/architecture/decisions/0005-terminal-metric-definitions.md).
The complete flat result schema is listed in
[`../../test/resources/org/fog/placement/run-report-schema.csv`](../../test/resources/org/fog/placement/run-report-schema.csv).

## Source files

- Compact results: [`results.tsv`](results.tsv)
- Environment: [`environment.properties`](environment.properties)
- Source manifest: [`source-files.sha256`](source-files.sha256)
- Input manifest: [`input-files.sha256`](input-files.sha256)
- Fixture details: `<fixture>/simulation.properties`
- Resource measurements: `<fixture>/time.properties`
- Captured logs: `<fixture>/stdout.log` and `<fixture>/stderr.log`
- Fixture definitions and methodology: [`../README.md`](../README.md)

