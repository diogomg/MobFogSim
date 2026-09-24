# MobFogSim overall architecture

This document describes the current production architecture of MobFogSim. It
focuses on ownership, runtime collaboration, and the main extension points
rather than listing every helper class. The diagrams use Mermaid and render in
GitHub and other Mermaid-aware Markdown viewers.

## Architectural style

MobFogSim is a discrete-event fog/mobile simulator built on CloudSim. The code
combines four architectural areas:

1. **Run orchestration** parses immutable configuration, opens one run-scoped
   context, builds the topology, starts CloudSim, and publishes the result.
2. **Domain services** implement mobility, association, handoff, migration,
   tuple routing, topology construction, and terminal metric capture.
3. **CloudSim entities** translate scheduled events into service calls and own
   the mutable state required while the event loop is running.
4. **GUI topology editing** uses a separate serializable graph model. It does
   not represent the live simulation registry.

The preferred dependency direction is from orchestration and entities toward
constructor-injected services, then toward narrow ports. Adapters isolate the
remaining CloudSim and legacy singleton APIs.

## Package responsibilities

| Package | Responsibility |
| --- | --- |
| `org.fog.vmmobile` | CLI parsing, run context, service composition, topology assembly, and run results |
| `org.fog.vmmobile.port` | Interfaces around the clock/event loop, lifecycle, metrics, network slicing, statistics, and output |
| `org.fog.vmmobile.adapter` | Production and legacy implementations of the ports |
| `org.fog.entities` | CloudSim entities, tuple routing, wireless association, and handoff execution |
| `org.fog.placement` | Controllers, module placement, mobility/association services, metrics, and reporting |
| `org.fog.vmmigration` | Migration decision strategies, immutable decisions, and migration transactions |
| `org.fog.application` | Application modules, tuple edges, monitored loops, and selectivity rules |
| `org.fog.utils` | Unit-bearing values, network slicing/accounting, event IDs, and shared factories |
| `org.fog.localization` | Coordinates, mobility samples/timelines, and distance calculations |
| `org.fog.gui.core` | Serializable editor graph, stable node identity, snapshots, commands, and JSON bridge |
| `org.fog.gui.dialog` / `org.fog.gui.example` | Swing editing dialogs and application shell |

## Runtime class diagram

This diagram shows the main ownership and collaboration paths. A filled diamond
means that the source owns the target for the duration of a run; an ordinary
arrow means that it invokes or references it.

```mermaid
classDiagram
    direction LR

    class AppExample {
        +main(args)
    }
    class SimulationCli {
        +parse(args) SimulationConfig
    }
    class SimulationConfig {
        <<immutable>>
    }
    class SimulationRunner {
        +run(config) SimulationRunResult
    }
    class SimulationContext {
        +open(config) SimulationContext
        +publishResults()
        +close()
    }
    class ExampleTopologyPlan {
        +build()
    }
    class SimulationTopology {
        +getMobileDevices()
        +getActiveMobileDevices()
        +getFogDeviceById(id)
    }
    class SimulationServices {
        +getTopology()
        +getMobility()
        +getAssociation()
        +getHandoff()
        +getMigration()
        +getTupleRouting()
        +getResults()
    }

    class SimEntity {
        <<CloudSim>>
    }
    class Datacenter {
        <<CloudSim>>
    }
    class PowerDatacenter {
        <<CloudSim>>
    }
    class Cloudlet {
        <<CloudSim>>
    }
    class PowerVm {
        <<CloudSim>>
    }
    class Controller
    class MobileController
    class FogDevice
    class ApDevice
    class MobileDevice
    class Sensor
    class MobileSensor
    class Actuator
    class MobileActuator

    class Application
    class AppModule
    class AppEdge
    class AppLoop
    class Tuple

    class SimulationResultsService
    class SimulationMetricsService
    class SimulationMetricsSnapshot {
        <<immutable>>
    }
    class RunReport {
        <<immutable>>
    }
    class ResultWriter {
        <<interface>>
    }
    class AtomicResultWriter
    class SimulationRunResult {
        <<immutable>>
    }

    AppExample --> SimulationCli : parses arguments
    AppExample --> SimulationRunner : starts run
    SimulationCli --> SimulationConfig : creates
    SimulationRunner --> SimulationContext : opens / closes
    SimulationRunner --> ExampleTopologyPlan : creates
    SimulationRunner --> MobileController : creates
    SimulationContext *-- SimulationConfig
    SimulationContext *-- SimulationTopology
    SimulationContext *-- SimulationServices
    SimulationContext *-- ResultWriter
    ExampleTopologyPlan --> SimulationContext
    ExampleTopologyPlan --> SimulationTopology : populates

    Controller --|> SimEntity
    MobileController --|> SimEntity
    Datacenter --|> SimEntity
    PowerDatacenter --|> Datacenter
    FogDevice --|> PowerDatacenter
    ApDevice --|> FogDevice
    MobileDevice --|> FogDevice
    Sensor --|> SimEntity
    MobileSensor --|> Sensor
    Actuator --|> SimEntity
    MobileActuator --|> Actuator

    SimulationTopology o-- FogDevice : server registry
    SimulationTopology o-- ApDevice : access-point registry
    SimulationTopology o-- MobileDevice : all / active users
    MobileController --> SimulationServices : uses
    Controller --> SimulationServices : uses
    MobileController --> Application : deploys
    Controller --> Application : deploys
    Application *-- AppModule
    Application *-- AppEdge
    Application *-- AppLoop
    AppModule --|> PowerVm
    Tuple --|> Cloudlet
    Sensor --> Tuple : emits
    FogDevice --> Tuple : routes / executes
    Actuator --> Tuple : consumes

    SimulationServices *-- SimulationResultsService
    SimulationServices *-- SimulationMetricsService
    SimulationResultsService --> SimulationMetricsService
    SimulationMetricsService --> SimulationMetricsSnapshot : captures
    SimulationContext --> SimulationMetricsSnapshot : retains terminal snapshot
    SimulationContext --> RunReport : publishes
    RunReport *-- SimulationMetricsSnapshot
    ResultWriter <|.. AtomicResultWriter
    SimulationContext --> SimulationRunResult : returns
    SimulationRunResult *-- SimulationMetricsSnapshot
```

## Run-scoped service graph

`SimulationServices` is the composition root for operational domain services.
Production runs obtain it from `SimulationContext`; compatibility constructors
fall back to `LegacySimulationAdapters` only when no context is active.

```mermaid
classDiagram
    direction LR

    class SimulationServices
    class TopologyService
    class MobilityService
    class MobileAssociationService
    class AccessPointAssociationService
    class HandoffCoordinator
    class MigrationCoordinator
    class TupleRoutingService
    class SimulationResultsService
    class SimulationMetricsService

    class CloudSimPort {
        <<interface>>
    }
    class SimulationClock {
        <<interface>>
    }
    class MobileLifecyclePort {
        <<interface>>
    }
    class NetworkSlicePort {
        <<interface>>
    }
    class MobileStatisticsPort {
        <<interface>>
    }
    class SimulationEventLog {
        <<interface>>
    }
    class SimulationOutput {
        <<interface>>
    }
    class SimulationMetricsPort {
        <<interface>>
    }

    class CloudSimAdapter
    class MobileLifecycleAdapter
    class NetworkSliceAdapter
    class MyStatisticsAdapter
    class SimulationEventSink
    class RunOutputAdapter
    class SimulationMetricsAdapter

    SimulationServices *-- TopologyService
    SimulationServices *-- MobilityService
    SimulationServices *-- MobileAssociationService
    SimulationServices *-- AccessPointAssociationService
    SimulationServices *-- HandoffCoordinator
    SimulationServices *-- MigrationCoordinator
    SimulationServices *-- TupleRoutingService
    SimulationServices *-- SimulationResultsService
    SimulationServices *-- SimulationMetricsService

    TopologyService --> CloudSimPort
    TopologyService --> SimulationOutput
    MobilityService --> MobileLifecyclePort
    MobileAssociationService --> SimulationClock
    MobileAssociationService --> MobileLifecyclePort
    MobileAssociationService --> NetworkSlicePort
    MobileAssociationService --> MobileStatisticsPort
    AccessPointAssociationService --> SimulationClock
    HandoffCoordinator --> CloudSimPort
    HandoffCoordinator --> NetworkSlicePort
    HandoffCoordinator --> SimulationEventLog
    MigrationCoordinator --> SimulationClock
    MigrationCoordinator --> NetworkSlicePort
    MigrationCoordinator --> MobileStatisticsPort
    MigrationCoordinator --> SimulationEventLog
    MigrationCoordinator --> SimulationOutput
    TupleRoutingService --> CloudSimPort
    SimulationResultsService --> SimulationEventLog
    SimulationMetricsService --> SimulationClock
    SimulationMetricsService --> SimulationMetricsPort

    SimulationClock <|-- CloudSimPort
    CloudSimPort <|.. CloudSimAdapter
    MobileLifecyclePort <|.. MobileLifecycleAdapter
    NetworkSlicePort <|.. NetworkSliceAdapter
    MobileStatisticsPort <|.. MyStatisticsAdapter
    SimulationEventLog <|.. SimulationEventSink
    SimulationOutput <|.. RunOutputAdapter
    SimulationMetricsPort <|.. SimulationMetricsAdapter
```

The service roles are deliberately narrow:

- `TopologyService` loads mobility and registers transport/AP/hierarchy links.
- `MobilityService` advances timestamped traces.
- `MobileAssociationService` owns mobile lifecycle and network association
  transactions.
- `AccessPointAssociationService` selects and reserves handoff capacity.
- `HandoffCoordinator` validates delayed events and commits or cancels a
  reservation.
- `MigrationCoordinator` evaluates pure migration strategies and commits an
  accepted decision transactionally.
- `TupleRoutingService` resolves tuple routes without owning entity event loops.
- `SimulationMetricsService` captures one immutable terminal snapshot;
  `SimulationResultsService` renders it.

## Migration decision model

Migration policy evaluation is separated from mutation. Strategies inspect a
`MigrationDecisionContext` and return an immutable `MigrationDecision`.
`MigrationCoordinator` checks that the captured source state is still current
before applying an accepted decision.

```mermaid
classDiagram
    direction LR

    class MigrationCoordinator {
        +decide(devices, coordinatorEntityId, ...)
        +commitDecision(decision, ...)
    }
    class DecisionMigration {
        <<interface>>
        +evaluate(device, context) MigrationDecision
    }
    class LowestLatency
    class LowestDistBwSmartThingAP
    class LowestDistBwSmartThingServerCloudlet
    class MigrationDecisionContext {
        <<immutable input>>
    }
    class MigrationDecision {
        <<immutable result>>
    }
    class MigrationPointEvaluation
    class MigrationPrediction
    class MobileDevice
    class FogDevice

    DecisionMigration <|.. LowestLatency
    DecisionMigration <|.. LowestDistBwSmartThingAP
    DecisionMigration <|.. LowestDistBwSmartThingServerCloudlet
    MigrationCoordinator --> DecisionMigration : selects
    MigrationCoordinator --> MigrationDecisionContext : creates
    DecisionMigration --> MigrationDecision : returns
    MigrationDecision *-- MigrationPointEvaluation
    MigrationDecision *-- MigrationPrediction
    MigrationDecision --> MobileDevice : captured subject
    MigrationDecision --> FogDevice : optional destination
    MigrationCoordinator --> MigrationDecision : validates / commits
```

## GUI topology model

The editor graph is intentionally independent from `SimulationTopology`.
`NodeId` is immutable and is the sole basis of node equality and hashing;
display names may change only through the graph command boundary. Snapshots are
immutable structural views, and `Bridge` performs deterministic JSON I/O.

```mermaid
classDiagram
    direction LR

    class Graph
    class GraphSnapshot {
        <<immutable view>>
    }
    class TopologyCommands
    class Bridge
    class GraphView
    class Node {
        -NodeId nodeId
        -String name
        -NodeType type
    }
    class NodeId {
        <<immutable>>
    }
    class Edge
    class HostNode
    class SwitchNode
    class FogDeviceGui
    class SensorGui
    class ActuatorGui
    class VmNode
    class AppModuleNode
    class SensorModule
    class ActuatorModule

    Graph *-- Node : registered nodes
    Graph *-- Edge : adjacency lists
    Edge --> Node : destination
    Node *-- NodeId : stable identity
    Graph --> GraphSnapshot : creates / caches
    TopologyCommands --> Graph : mutates
    TopologyCommands --> GraphSnapshot : reads
    GraphView --> TopologyCommands
    GraphView --> GraphSnapshot
    Bridge --> Graph : reads / writes JSON

    Node <|-- HostNode
    Node <|-- SwitchNode
    Node <|-- FogDeviceGui
    Node <|-- SensorGui
    Node <|-- ActuatorGui
    Node <|-- VmNode
    Node <|-- AppModuleNode
    Node <|-- SensorModule
    Node <|-- ActuatorModule
```

`AppModuleNode` in the diagram denotes `org.fog.gui.core.AppModule`; the alias
avoids confusing it with the runtime `org.fog.application.AppModule`.

## Runtime sequence

1. `AppExample` delegates argument handling to `SimulationCli` and execution to
   `SimulationRunner`.
2. `SimulationRunner` opens a `SimulationContext`. The context installs one
   run's clock, random streams, topology registry, service graph, metrics,
   output, and compatibility adapters.
3. `ExampleTopologyPlan` builds access points, server cloudlets, mobile devices,
   mobility traces, and transport links as a rollback-safe operation.
4. `MobileController` registers applications and drives mobility, association,
   handoff, and migration through `SimulationServices`. CloudSim entities
   continue to own event dispatch and device-local runtime state.
5. Sensors emit `Tuple` objects; fog/mobile devices route and execute them;
   actuators consume terminal tuples. Network slicing and accounting are
   applied at the relevant wired, wireless, and migration boundaries.
6. On terminal shutdown, the controller captures one
   `SimulationMetricsSnapshot`. The context writes a `RunReport`, constructs a
   `SimulationRunResult`, and detaches run-scoped compatibility state.

## Important invariants

- Only one `SimulationContext` may be active in a JVM because the underlying
  CloudSim runtime and some compatibility APIs remain static.
- The context owns mutable services and registries for exactly one run and must
  always be closed.
- `SimulationTopology.getMobileDevices()` is the archival all-user registry;
  `getActiveMobileDevices()` is the mutable participation view.
- Access-point, server, and mobile membership must be updated bidirectionally.
- Delayed handoff and migration events carry generation information so stale
  events cannot mutate a newer session.
- Migration strategies produce decisions; the coordinator alone commits them.
- Terminal metrics are captured once and shared by console rendering, reports,
  and programmatic results.
- Durations are milliseconds, data sizes are bytes, and data rates are bits per
  second unless an explicit boundary documents a conversion.
- GUI `Node` identity is independent of its display name, and topology JSON is
  serialized deterministically.

## Extension guidance

- Add a migration policy by implementing `DecisionMigration`; keep evaluation
  free of mutations and let `MigrationCoordinator` commit the result.
- Add an infrastructure integration behind a `port` interface and bind it in
  `SimulationServices` rather than calling another static API from entities.
- Add runtime topology mutations through `SimulationTopology` and the relevant
  lifecycle service so ordered registries and lookup indexes remain coherent.
- Add GUI mutations through `TopologyCommands`; never mutate collections from a
  `GraphSnapshot`.
- Add terminal outputs from `SimulationMetricsSnapshot` or `RunReport` so all
  consumers retain the same metric definitions.

## Architecture decisions

These records define cross-package simulation contracts that are easy to lose
when reading individual CloudSim entities. They describe the current model,
not every historical behaviour.

| Decision | Contract |
| --- | --- |
| [ADR-0001](decisions/0001-time-data-and-rate-units.md) | Time, data-size, and data-rate units |
| [ADR-0002](decisions/0002-topology-shape-and-ownership.md) | Physical topology shape and ownership |
| [ADR-0003](decisions/0003-network-contention-and-slicing.md) | Contention, queues, and network slicing |
| [ADR-0004](decisions/0004-mobile-user-retirement.md) | Terminal mobile-user retirement |
| [ADR-0005](decisions/0005-terminal-metric-definitions.md) | Terminal metric definitions |

An accepted decision changes only through a replacement ADR and corresponding
tests. `make lint` checks maintained Java sources with strict warnings, while
`make test` includes the deterministic semantic reference simulations.
