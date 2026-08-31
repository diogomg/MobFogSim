# MobFogSim
MobFogSim - Simulation of Mobility and Migration for Fog Computing

MobFogSim extends [iFogSim](https://github.com/Cloudslab/iFogSim1) simulator to enable the modelling of device mobility and service migration in fog computing.

More details can be found in the following paper: Puliafito, Carlo, et al. "MobFogSim: Simulation of Mobility and Migration for Fog Computing." Simulation Modelling Practice and Theory (2019)

## Running MobFogSim

Building your own simulation
*  First step: Follow the following steps;
*  Second step: Provide the user mobility dataset and order manifest;
*  Third step: Initialize the CloudSim package. It should be called before creating any entities;
*  Fourth step: Create all devices;
*  Fifth step: Create a Broker;
*  Sixth step: Create one virtual machine;
*  Seventh step: Create one Application (appModule, appEdge, appLoop, and tuples);
*  Eighth step: Configure the network;
*  Ninth step: Starts the simulation;
*  Final step: Print results when the simulation is over.

An example of an application is set in src/org/fog/vmmobile/AppExample.java

### Running in the command line:
Make run

### Running in Eclipse IDE:
Create a new project defining this repository as the main directory

Settings: Project -> Proprieties -> Java Build Path -> Libraries -> ADD External JARs -> Select the JARs files in the directory jars

In src/org/fog/vmmobile/AppExample.java, run as -> run configurations -> Java Application -> AppExample -> Arguments -> Program arguments -> Insert the parameters as you demand

In src/org/fog/vmmobile/AppExample.java, run as -> Java application

### Requirements
* JAVA SDK

Optional:

* IBM CPLEX for optimization algorithms

* cscope for browsing the source code

## Parameters

*  First parameter: 0/1 -> Migration processes are denied or allowed
*  Second parameter: Positive Integer -> seed to be used in the random numbers generation
*  Third parameter: 0/1 -> Migration point approach is fixed (0) or based on the user speed (1)
*  Fourth parameter: 0/1/2 -> Migration strategy approach to select the destination cloudlet. It can be based on the lowest latency (0), the lowest distance between the user and cloudlet (1), or the lowest distance between the user and Access Point (2)
*  Fifth parameter: Positive Integer -> Number of users
*  Sixth parameter: Positive Integer -> Base Network Bandwidth between cloudlets
*  Seventh parameter: 0/1/2 -> Migration policy based on Complete VM/Cold migration (0), Complete Container migration (1), or Container Live Migration (2)
*  Eighth parameter: Positive number -> Base Network Latency between cloudlets.
*  Ninth parameter: Non-Negative Integer -> User Mobility prediction, in seconds.
*  Tenth parameter: Non-Negative Integer -> User Mobility prediction inaccuracy, in meters.
*  Eleventh  parameter: network-slice scope: 0 transport network only (physical links between servers), 1 wireless network only (access-point uplink and downlink), or 2 end-to-end (transport and wireless). It defaults to 2 when omitted.
*  Twelfth parameter: comma-separated percentages of users assigned to each slice. There must be one value per slice, and the values must be greater than zero and sum to 100.
*  Thirteenth parameter: comma-separated bandwidth percentages for the slices. Values must be greater than zero and sum to 100.
*  Fourteenth parameter: 0 keeps the configured slice bandwidth fixed (static slicing); 1 lets active slices borrow idle capacity (dynamic slicing).
*  Fifteenth parameter: VM destination type: 0 edge servers only, 1 end devices only, or 2 hybrid.
*  Sixteenth parameter: optional path to the mobility trace directory. It defaults to `input`.
*  Seventeenth parameter: optional path to the mobility order manifest. It defaults to `inputOrder.csv` inside the selected mobility directory.

### Example

```text
1 290538 0 0 10 11 0 61 0 0 0 60,40 70,30 1 2 input input/inputOrder.csv
```


This example configures the simulation as follows:

| Parameter | Value | Meaning |
| --- | ---: | --- |
| Migration | 1 | Enables service migration. |
| Random seed | 290538 | Makes random decisions reproducible. |
| Migration point | 0 | Uses the fixed migration-point policy. |
| Migration strategy | 0 | Selects the destination with the lowest latency. |
| Users | 10 | Simulates ten mobile users. |
| Cloudlet bandwidth | 11 | Sets the base bandwidth between server cloudlets to 11 Mbps. |
| Migration policy | 0 | Uses complete-VM cold migration. |
| Cloudlet latency | 61 | Sets the base latency between server cloudlets to 61. |
| Mobility prediction | 0 | Disables look-ahead mobility prediction. |
| Prediction error | 0 | Uses no additional mobility-prediction error. |
| Slice scope | 0 | Applies slicing only to the transport network between servers. |
| User allocation | 60,40 | Assigns 60% of users to slice 0 and 40% to slice 1: six and four users in this ten-user example. |
| Slice shares | 70,30 | Gives slice 0 70% and slice 1 30% of bandwidth in the selected scope. |
| Slice mode | 1 | Enables dynamic borrowing of idle slice capacity. |
| VM destinations | 2 | Allows both edge servers and eligible end devices as migration destinations. |
| Mobility directory | input | Reads mobility traces from the repository's default input directory. |
| Mobility manifest | input/inputOrder.csv | Maps users to traces in the manifest's original order. |

When a user-allocation percentage produces a fractional number of users, the simulator assigns the remaining users to the slices with the largest fractional remainders. Within the selected scope, each group receives its configured bandwidth share. For example, a physical link or AP direction with 11 Mbps and a 70/30 configuration gives group 0 7.7 Mbps and group 1 3.3 Mbps. Users connected to the same AP and assigned to the same slice share that slice's wireless capacity equally; their effective rate is also capped by the individual mobile device's link rate. A network outside the selected scope is not divided into slices; AP capacity is shared equally among connected users in that case. During VM migration, an active group may borrow transport capacity reserved for groups with no active migration on that same directed link. Concurrent migrations on the same directed link share the available capacity; whenever a migration starts, finishes, or is aborted, the simulator updates the remaining data, recalculates every affected rate, and replaces their completion events so their combined rate never exceeds the physical link. On an AP, connected groups may borrow the capacity of groups that have no connected users there. Borrowed capacity is returned automatically when a migration finishes, or when AP membership changes; when more than one active slice borrows capacity, the idle capacity is divided equally between them.

Every connected mobile device can host another user's application VM. During a migration, the simulator keeps the selected server-cloudlet destination unless a different connected mobile device is closer to the application owner and has sufficient host resources. The owner device and the current VM host are excluded. VM traffic is then routed to the VM's current host using the existing network topology, and a later migration may move that VM back to a server cloudlet or to another eligible mobile device.


## Input

Mobility data is read from `.csv` files generated from mobility patterns
such as SUMO (Simulation of Urban MObility). Trace filenames must match
`*log.csv`; unrelated files in the same directory are ignored.

An example of an offline mobility dataset from [Luxembourg SUMO Traffic](https://github.com/lcodeca/LuSTScenario) is placed in the directory named as 'input'.

Each non-empty trace must contain exactly five tab-separated, finite numeric
columns: time in seconds, direction in radians, X position, Y position, and
speed in metres per second. Time must be non-negative and nondecreasing, and
speed must be non-negative.

The order manifest is also tab-separated despite its `.csv` extension.
It contains zero-based indexes into the lexicographically sorted `*log.csv`
files. Indexes can span multiple rows, are applied exactly in their written
order, and the manifest must contain at least one entry per simulated user. For
example, `2<TAB>0<TAB>1` assigns trace 2 to user 0, trace 0 to user 1, and trace
1 to user 2. Missing files, malformed rows, insufficient entries, and invalid trace
indexes stop startup with a contextual `MobilityInputException`.

Example input/1702log.csv 

2.1    -1.51173    10370.1    2233.67    0

3.1    -1.68755    10369.2    2234.57    2.34286

4.1    -2.09045    10366.9    2236.81    4.11058

5.1    -2.36655    10363.1    2240.26    6.03548

6.1    -2.41103    10357.9    2244.92    7.94067

7.1    -2.43504    10350.9    2250.8    10.0297

8.1    -2.43476    10342.4    2258.09    12.1859

9.1    -2.42554    10332.5    2266.75    14.044

10.1    -2.42553    10323.3    2274.71    10.638

.

.

.

## How to cite MobFogSim

Puliafito, C. et. al. MobFogSim: Simulation of mobility and migration for fog computing. Simulation Modelling Practice and Theory. 2020.
bibtex
@article{puliafito2020mobfogsim,
  title={MobFogSim: Simulation of mobility and migration for fog computing},
  author={Puliafito, Carlo and Gon{\c{c}}alves, Diogo M and Lopes, M{\'a}rcio M and Martins, Leonardo L and Madeira, Edmundo and Mingozzi, Enzo and Rana, Omer and Bittencourt, Luiz F},
  journal={Simulation Modelling Practice and Theory},
  volume={101},
  pages={102062},
  year={2020},
  publisher={Elsevier}
}

DOI https://doi.org/10.1016/j.simpat.2019.102062

### Additional papers regarding MobFogSim features

Goncalves, D. et. al. Dynamic network slicing in fog computing for mobile users in MobFogSim. IEEE/ACM 13th International Conference on Utility and Cloud Computing. 2020.
 bibtex
@inproceedings{gonccalves2020dynamic,
  title={Dynamic network slicing in fog computing for mobile users in MobFogSim},
  author={Gon{\c{c}}alves, Diogo and Puliafito, Carlo and Mingozzi, Enzo and Rana, Omer and Bittencourt, Luiz and Madeira, Edmundo},
  booktitle={2020 IEEE/ACM 13th International Conference on Utility and Cloud Computing (UCC)},
  pages={237--246},
  year={2020},
  organization={IEEE}
}

DOI https://doi.org/10.1109/UCC48980.2020.00042

Goncalves, D. et. al. End-to-end network slicing in vehicular clouds using the MobFogSim simulator. Ad Hoc Networks. 2023.
 bibtex
@article{gonccalves2023end,
  title={End-to-end network slicing in vehicular clouds using the MobFogSim simulator},
  author={Gon{\c{c}}alves, Diogo M and Puliafito, Carlo and Mingozzi, Enzo and Bittencourt, Luiz F and Madeira, Edmundo RM},
  journal={Ad Hoc Networks},
  volume={141},
  pages={103096},
  year={2023},
  publisher={Elsevier}
}

DOI https://doi.org/10.1016/j.adhoc.2023.103096
