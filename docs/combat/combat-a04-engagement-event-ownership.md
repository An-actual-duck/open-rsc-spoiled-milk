# A04 Combat Engagement and Event Ownership

## Outcome

A04 replaces the independently mutable combat-opponent and repeating-event
fields on `Mob` and `Player` with one per-mob `CombatEngagementAuthority`.
Existing getters and setters remain as compatibility facades, but they now
read from or write through that authority. No damage, accuracy, defense, XP,
cost, cadence, movement, eligibility, plugin, or packet formula moved.

This implementation adapts the directional engagement model in
Classic-Scape commits `0ec7af10`, `e1f10922`, `cc2a8bf3`, and `593368bc`.
It does not port their broader combat engine. Spoiled Milk's current event
classes, A03 attack transactions, content gates, and compatibility behavior
remain authoritative.

## Authoritative model

Each mob owns:

- at most one outgoing attack direction;
- any number of incoming attack directions, keyed by attacker identity;
- six exclusive compatibility event slots: reciprocal melee, PvM melee, NPC
  range, player range, player throwing, and player magic; and
- participant-lifecycle snapshots and a stable encounter identity.

A counterattack against an incoming attacker reuses the same encounter but
owns a separate direction. Either direction can end without clearing the
other. Retargeting closes only the source's old direction and moves its event
binding to the new target. One target can therefore retain several incoming
attackers without choosing one as its own opponent.

`CombatEngagement` records relationship and direction state.
`CombatEngagementAuthority` is the only mutable owner. Event accessors in
`Mob` and `Player` are projections over typed slots; the removed fields are not
maintained in parallel.

## Compatibility boundary

The legacy `getOpponent()` projection intentionally remains narrower than the
new directional model:

- melee engagement projects a legacy opponent;
- ordinary player bow, throwing, magic, and NPC range directions do not;
- a ranged counter that already entered the legacy melee stance retains that
  projection for its encounter; and
- `inCombat()` remains combat-sprite 8–15 plus a projected legacy opponent.

This preserves the existing meaning of busy, walk, logout, duel/PvP, and
range-specific restrictions. Callers that need all attack ownership must use
`getOutgoingCombatTarget()`, `hasOutgoingAttack()`, incoming-attacker queries,
or `isMutuallyEngagedWith()` rather than broadening `inCombat()`.

`lastCombatWith` remains separate by design. It is historical reattack and
cooldown compatibility state, not live event or encounter ownership.

## Event and lifecycle rules

Every maintained repeating combat event registers its exact object and target
snapshot in a typed slot. A callback first proves it still owns that slot.
Replaced or late callbacks stop themselves and cannot clear a newer event or
retargeted relationship.

Normal event completion clears its exact slot and closes its direction only
when no other current slot owns the encounter. Reciprocal melee clears the
matching slot and direction on both participants. Ending an incoming PvM event
does not clear a still-current ranged, thrown, magic, or melee counterattack.

Logout, death, player/NPC teleport, and NPC despawn terminate both outgoing
and incoming owned events, then close all directions. Cleanup is idempotent;
participant generation snapshots prevent an old NPC lifetime or player
session callback from attaching to a later one.

`CombatEngagementTerminalReason` records why a direction ended without
changing combat results. The reasons are diagnostic state, not a new gameplay
decision layer.

## Audit and repair

`auditCombatOwnership(false)` reports stale outgoing state or stopped/stale
event slots without repairing them. `auditCombatOwnership(true)` is an
explicit diagnostic repair path and logs a bounded owner/issue summary before
removing anomalies and closing an orphan direction.

No scheduled watchdog invokes this repair. Routine event and lifecycle paths
must maintain ownership themselves; needing audit repair during ordinary
combat is a defect rather than accepted control flow.

## Executable verification

A04 grows the authoritative Java combat gate from 26 to 32 scenarios. Its six
new scenarios cover:

1. one outgoing/many incoming directions, mutual counterattacks, retargeting,
   and independent ranged-counter teardown;
2. stale ranged and PvM callbacks after replacement;
3. outgoing and incoming cleanup on teleport, logout, and death;
4. passive player behavior with auto-retaliate disabled, followed by an
   enabled retaliation;
5. read-only discrepancy reporting and explicit audit repair; and
6. reciprocal player-melee teardown on both participants.

The complete gate continues to exercise current formulas, ticks, eligibility,
plugins, layered line-of-effect, NPC lifecycle, poison, Cleric effects,
summons, scythe, shuriken, AoE targeting, and death-listener behavior.

Acceptance also requires the authoritative Ant core/plugin build, artifact
exclusion checks, deliberate-failure and zero-scenario gate checks, focused
Python regressions, and changed-code static analysis. A04 changes no client
presentation or packet shape, so it has no private visual acceptance surface.

## Deferred work

A04 does not introduce damage requests/results, projectile settlement ledgers,
effect registries, DoT provenance, contribution ownership, or NPC profile
generation. Those remain A05 and later work. It also does not broaden legacy
combat restrictions to treat every incoming or non-melee direction as
`inCombat()`; that would require a separate behavior decision and new
characterization.
