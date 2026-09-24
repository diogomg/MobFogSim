# ADR-0003: Network contention and slicing

- Status: Accepted
- Date: 2026-09-23

## Context

Independent delay calculations let concurrent transfers each consume the full
capacity of the same resource. Network slicing additionally needs stable rules
for reservations, idle-capacity borrowing, reconfiguration pauses, queues, and
obsolete completion events.

## Decision

Contention is modelled per directed resource using fluid sharing:

- Migration transfers contend on a directed server-to-server transport link.
- Wireless tuples contend on an access-point direction (uplink or downlink).
  A mobile may have one active tuple per direction; later tuples wait in FIFO
  order.
- Unsliced capacity is shared by active transfers. Static slicing first assigns
  each slice its configured reservation. Dynamic slicing divides reservations
  for idle slices equally among active slices. Transfers within a slice share
  that slice's allocation; wireless allocation also respects each mobile
  endpoint's rate cap.

Whenever the active transfer set changes, completed bytes are accounted at the
old rates before new rates and completion events are calculated. Each new
schedule has a generation; superseded completion events are ignored.

In dynamic multi-slice mode, a change to the active-slice set pauses affected
traffic for the configured reallocation duration. The wireless FIFO retains at
most 10,000 waiting tuples per mobile and AP direction; at the limit it drops
the oldest waiting tuple and records the drop.

Preparation, fixed migration delay, propagation, and slice reallocation are
separate from byte-transfer service and do not consume transport bandwidth.

## Consequences

- Capacity is conserved for overlapping transfers.
- Directional links and AP directions do not contend with their reverse path.
- Cancellation and reconfiguration may create stale events, so event payloads
  must retain their schedule generation.
- Queue and reconfiguration counters are semantic outputs, not debug data.

Enforced by
[`MigrationTransferScheduler`](../../../src/org/fog/utils/MigrationTransferScheduler.java),
[`AccessPointTransferScheduler`](../../../src/org/fog/utils/AccessPointTransferScheduler.java),
[`NetworkSlicing`](../../../src/org/fog/utils/NetworkSlicing.java), and their
corresponding scheduler and event-integration tests under
[`test/org/fog/utils`](../../../test/org/fog/utils/).

