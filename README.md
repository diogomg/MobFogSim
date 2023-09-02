# MobFogSim
MobFogSim - Simulation of Mobility and Migration for Fog Computing

MobFogSim extends [iFogSim](https://github.com/Cloudslab/iFogSim1) simulator to enable the modelling of device mobility and service migration in fog computing.

More details can be found in the following paper: Puliafito, Carlo, et al. "MobFogSim: Simulation of Mobility and Migration for Fog Computing." Simulation Modelling Practice and Theory (2019)

## Running MobFogSim

Building your own simulation
*  First step: Follow the following steps;
*  Second step: Provide the user mobility dataset in the input directory;
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
*  Seventh parameter: 0/1/2 -> Migration policy based on Complete VM/Cold migration (0), Complete Container migration (1), or Container Live Migration (3)
*  Eighth parameter: Non-Negative Integer -> User Mobility prediction, in seconds
*  Ninth parameter: Non-Negative Integer -> User Mobility prediction inaccuracy, in meters
*  Tenth parameter: Positive negative Integer -> Base Network Latency between cloudlets

Example
1 290538 0 0 1 11 0 0 0 61


## Input

Mobility data can be read as .csv files. These files can be taken from mobility patterns of SUMO - Simulation of Uban MObility

An example of an offline mobility dataset from [Luxembourg SUMO Traffic](https://github.com/lcodeca/LuSTScenario) is placed in the directory named as 'input'.

The user mobility is based on the following parameters: time (in seconds), direction (in rad), position x and y, and speed (in m/s)

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
DOI https://doi.org/10.1016/j.simpat.2019.102062

### Additional papers regarding MobFogSim features

Goncalves, D. et. al. Dynamic network slicing in fog computing for mobile users in MobFogSim. IEEE/ACM 13th International Conference on Utility and Cloud Computing. 2020.
``` bibtex
@inproceedings{gonccalves2020dynamic,
  title={Dynamic network slicing in fog computing for mobile users in MobFogSim},
  author={Gon{\c{c}}alves, Diogo and Puliafito, Carlo and Mingozzi, Enzo and Rana, Omer and Bittencourt, Luiz and Madeira, Edmundo},
  booktitle={2020 IEEE/ACM 13th International Conference on Utility and Cloud Computing (UCC)},
  pages={237--246},
  year={2020},
  organization={IEEE}
}
```
DOI https://doi.org/10.1109/UCC48980.2020.00042

Goncalves, D. et. al. End-to-end network slicing in vehicular clouds using the MobFogSim simulator. Ad Hoc Networks. 2023.
``` bibtex
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
DOI https://doi.org/10.1016/j.adhoc.2023.103096
