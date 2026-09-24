# ADR-0004: Mobile-user retirement

- Status: Accepted
- Date: 2026-09-23

## Context

A mobility trace may end before the simulation does. Removing that mobile from
every collection lost terminal energy and identity, while keeping it active
allowed stale mobility, handoff, migration, sensor, and VM state to continue.

## Decision

`FINISHED` is terminal. Retirement is an idempotent transaction that:

1. records the user's final power and energy;
2. cancels pending association work and active/queued wireless transfers;
3. disconnects the mobile from both its AP and server cloudlet;
4. finishes its session, invalidating handoff and migration generations;
5. deactivates sensors and actuators;
6. removes it from the active-user working set;
7. releases its application, module mappings, hosted-VM registrations, and VM
   allocation from every possible host; and
8. discards incomplete timing intervals rather than reporting partial samples.

The CloudSim entity, broker compatibility state, and archival all-user registry
remain until terminal reporting. A finished session cannot be scheduled or
reactivated. When the active set becomes empty, the controller captures metrics
from the archival registries before terminating CloudSim.

## Consequences

- Runtime registries are bounded by live work without losing terminal metrics.
- Delayed events become harmless through active-membership and generation
  checks.
- Energy averages use all configured users, including those retired early.
- New user-owned resources must be added to the retirement transaction and its
  failure/idempotency tests.

Enforced by
[`MobileSession`](../../../src/org/fog/vmmobile/MobileSession.java),
[`MobileUserRegistration`](../../../src/org/fog/vmmobile/MobileUserRegistration.java),
[`MobileController`](../../../src/org/fog/placement/MobileController.java), and
[`MobileControllerDelayedEntryTest`](../../../test/org/fog/placement/MobileControllerDelayedEntryTest.java).

