# MobFogSim
MobFogSim - Simulation of Mobility and Migration for Fog Computing

MobFogSim extends iFogSim to enable modeling of device mobility and service migration in fog computing

More details can be found in the following paper: Puliafito, Carlo, et al. "MobFogSim: Simulation of Mobility and Migration for Fog Computing." Simulation Modelling Practice and Theory (2019)

## Running MobFogSim

Building your simulation
*  First step: Follow the following steps
*  Second step: Provide the user mobility dataset in the input directory
*  Third step: Initialize the CloudSim package. It should be called before creating any entities.
*  Fourth step: Create all devices
*  Fifth step: Create Broker
*  Sixth step: Create one virtual machine
*  Seventh step: Create one Application (appModule, appEdge, appLoop, and tuples)
*  Eighth step: Configure the network
*  Ninth step: Starts the simulation
*  Final step: Print results when the simulation is over

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

*  First parameter: 0/1 -> migration processes are denied or allowed
*  Second parameter: Positive Integer -> seed to be used in the random numbers generation
*  Third parameter: 0/1 -> Migration point approach is fixed (0) or based on the user speed (1)
*  Fourth parameter: 0/1/2 -> Migration strategy approach to select the destination Cloudlet. It can be based on the lowest latency (0), the lowest distance between the user and cloudlet (1), or the lowest distance between the user and Access Point (2)
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

An example of offline mobility dataset is placed in the directory input

The user mobility is based on the following parameters: time (in seconds), direction (in rad), position x and y, speed (in m/s)

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



