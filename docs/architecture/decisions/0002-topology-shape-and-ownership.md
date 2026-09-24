# ADR-0002: Topology shape and ownership

- Status: Accepted
- Date: 2026-09-23

## Context

MobFogSim has a stable infrastructure graph, a parent/child tuple-routing
hierarchy, and mobile associations that change during a run. Treating these as
one mutable graph caused asymmetric membership, changing propagation delays,
and retired users disappearing from reports.

## Decision

The example infrastructure has three explicit layers:

1. Server cloudlets form a complete CloudSim transport mesh. A planned link is
   registered once; its capacity is bounded by the source uplink and destination
   downlink, and its propagation delay is derived from grid distance plus a
   seeded sample.
2. Each access point attaches to exactly one closest server cloudlet. Both
   endpoints record the attachment before the CloudSim link is registered.
3. Fog parent/child routing is represented by the child's `parentId` and the
   parent's child/latency registries. The child's configured uplink latency is
   the hierarchy-edge propagation delay.

A mobile wireless association is runtime state, not a rewrite of the stable
transport graph. The mobile, AP, and serving cloudlet must agree on membership;
handoff and disconnect services update both sides and use generations to reject
stale events.

`SimulationTopology` owns run-scoped registries. Its all-mobile list is
archival and never shrinks. Its active-mobile list is the working set used for
mobility, association, and migration decisions.

## Consequences

- Topology plans can be validated before CloudSim is mutated.
- Physical propagation latency does not become handoff establishment time.
- Runtime algorithms cannot accidentally erase an entity needed by terminal
  metrics.
- Adding a new topology layer requires an owner and an explicit relationship to
  the three existing representations.

Enforced by [`TopologyPlan`](../../../src/org/fog/vmmobile/TopologyPlan.java),
[`TopologyService`](../../../src/org/fog/vmmobile/TopologyService.java),
[`SimulationTopology`](../../../src/org/fog/vmmobile/SimulationTopology.java),
and [`ServerCloudletNetworkTest`](../../../test/org/fog/vmmobile/ServerCloudletNetworkTest.java).
