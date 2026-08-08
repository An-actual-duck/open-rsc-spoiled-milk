# A08.2 DoT Lifecycle Characterization

Status: executable current-state evidence milestone complete; the first A08.3
target-policy foundation is now compiled and independently guarded. The four
gameplay-policy decisions below were approved by the project owner on
2026-08-07. A third exceptional-boundaries fixture now covers the legacy PvP
producer, valid large generic-burn values, scheduler callback failure, and
failed logout-save cleanup. The remaining exceptional producer assertions are
source-order guards; typed runtime implementation can now begin.

Baseline: published `main` at `904218197c72be996329a50c9acd8d873fac6a7d`.

This phase adds deterministic fixtures around the A08.1 poison/burn audit. It
does not change production behavior or choose reward, ownership, or generic-burn
compatibility policy. Several assertions deliberately preserve defective
compatibility behavior so the later migration cannot change it accidentally or
hide it behind equal final Hits.

## Executable coverage added

`CurrentCombatDotLifecycleCharacterization` now establishes:

- generic poison's additive current power, nonshrinking ceiling, capped power,
  one-event rule, and preserved tick countdown on reapplication;
- latest-call player ownership, including the current ownership transfer when
  an application adds no power at the ceiling and source clearing when that
  capped call is unattributed;
- poison pulse order and presentation: damage from pre-drain power, three-power
  drain, poison hitsplat, factual-damage Blood Necklace Leach, and no NPC damage
  contribution;
- cure/damage boundaries at powers 9, 10, 19, and 20, including the one-pulse
  delay before a post-drain sub-10 remainder cures, plus live Nature Cleansing
  equipment changing only later decay;
- factual-damage Leach boundaries for Goblin Tenacity, NPC overkill, and zero
  actual damage, including the compatibility distinction between requested
  hitsplat damage and capped HP loss;
- poison continuing while its player source is offline, Leach stopping while
  that source is absent, and Leach resuming through the username-derived stable
  UUID after a new session equips the necklace;
- legacy player poison restoration with current/maximum values, no restored
  source, exactly one fresh event, and a full eight-tick countdown;
- legacy poison-without-maximum fallback and current orphan-cache cleanup
  behavior;
- nonnumeric, negative, inverted current/maximum, orphan-maximum, signed
  overflow, and wrong-runtime-attribute poison failure behavior;
- generic burn first application, pulse/cache order, standard hitsplat,
  replacement behavior, complete-pair restoration, partial-pair behavior, and
  NPC-removal behavior;
- nonnumeric burn-cache and wrong-runtime-attribute burn failure behavior;
- scheduler-dispatched invalid poison callback behavior: the event boundary
  catches the exception, reports failure, and stops the event (unlike direct
  `run()` invocation, which leaves it running);
- failed NPC death-listener behavior through the shared combat fixture: the
  terminal lifecycle is acquired, removal and preceding rewards occur once,
  and a later lethal request cannot replay the throwing callback; and
- missing-row logout-save behavior through the real `PlayerSaveRequest` and
  `PlayerService` path: current error formatting throws before request cleanup,
  leaving the live session, save/logout flags, and owner-bound poison event in
  place;
- three repeated fresh target sessions restoring poison and burn exactly once,
  with logout stopping/removing each session's scheduler entries and each new
  session receiving new event identities/full countdowns;
- deliberate duplicate scheduler admission for poison versus scheduler refusal
  for generic burn, including the detached poison stream's independent damage;
- zero-damage, zero-pulse, and negative complete burn-cache pairs scheduling
  invalid events that clear only on their first pulse;
- the legacy non-My-World PvP poison producer, including its My World gate,
  poisoned-equipment requirement, deterministic roll, antidote guard, and
  unattributed 48/48 application; and
- large valid generic-burn values, including `Integer.MAX_VALUE` damage
  reaching ordinary lethal handling while the visible hitsplat caps at 255;
- player death clearing generic poison and generic burn runtime/cache state;
- lethal poison credit when a different opponent is engaged and the
  opponentless lethal boundary; and
- player-target lethal poison attribution for player, NPC, and unattributed
  application sources when a different opponent is attached at death;
- deterministic successful and guarded/failed application through reciprocal
  melee, PvM melee, projectile weapon poison, dual-element Acid, primary and
  secondary Guthix poison, Corrosive Aura, and NPC poison; and
- Elder armor burn stopping on player-source logout and Elder boss burn
  stopping on dragon-source removal.

The test harness gained only test-side support for pre-login cache seeding and
the same successful-logout event/world removal order used by
`PlayerSaveRequest`. No database or public server is involved.

## Newly proven defects and corrections to A08.1

### Generic burn reapplication loses the burn

A08.1 described generic burn as replacement with a reset countdown. The
executable path is worse:

1. `applyBurn` writes the new damage and pulse count.
2. `startBurnEvent` sees the old attribute and calls `extinguish`.
3. `extinguish` clears the newly written damage/pulses and stops the old event.
4. A zero-damage/zero-pulse replacement object is attached.
5. `ONE_PER_MOB` rejects that replacement because the stopped prior event still
   occupies the scheduler until cleanup.

The result is a running-looking attribute with no scheduler clock and zero
state. It does not expire unless another path explicitly extinguishes it. A08.3
must treat application/state/event registration as one atomic operation; it
must not preserve this defect as intended replacement semantics.

### Generic burn crosses NPC removal

`Npc.remove()` calls `curePoison()` rather than `cure()`. Generic burn remains
attached when unregistering begins, and a scheduled pulse can still damage the
terminal NPC. This is a target-lifecycle leak. Generic burn is presently a
dormant compatibility family, but any retained or migrated form must clear at
the same authoritative NPC lifetime boundary as poison.

### Orphan poison cache survives cure

`curePoison()` removes `poisoned` and `poisoned_max` only inside the branch
where a valid `poisonEvent` attribute exists. If the event/attribute is missing,
runtime power is reset but both cache keys remain and can restore poison on a
later login. A08.4 must make state removal independent of event presence.

### Corrupt optional state can abort login or poison the scheduler

A nonnumeric `poisoned` value throws during `setLoggedIn(true)` after a
zero-power poison event has already been attached and scheduled, while the
player never reaches logged-in state. A nonnumeric burn value also aborts
login, though burn parsing fails before event creation. Negative poison
restores into a running event and throws on its first pulse. Current power above
the stored maximum is accepted without clamping; an orphan maximum remains
stale; additive integer overflow produces `Integer.MIN_VALUE` poison and a
later pulse exception. Wrong object types under either runtime event attribute
throw during cleanup.

A08.4 must parse optional effects independently before scheduling anything,
bound arithmetic before mutation, reject/quarantine invalid records without
failing account login, and make cleanup tolerate missing or malformed legacy
runtime markers.

### Lethal poison follows opponent, not poison source

Generic poison records no contribution. Its compatibility damage helper calls
`killedBy(target.getOpponent())` before presenting the hit. With an engaged
opponent, that opponent receives NPC kill credit even when another player owns
the poison and receives Leach. Without an opponent, `Npc.killedBy(null)` cures
the poison and returns: the NPC is not removed and its Hits remain unchanged,
yet the helper reports factual lethal damage and that result still heals the
poison owner through Leach.

This is evidence for the A08/A09 attribution decision, not authorization to
redirect rewards in this phase.

### Player poison deaths discard causal source identity

The player-target cases confirm the same opponent substitution at a more
dangerous boundary. A player poison owner remains available to `PoisonEvent`
only as a UUID for Leach, while an NPC source is discarded during application
and environment/script poison has no source. When the pulse kills a player,
all three paths call `killedBy` with the victim's current opponent. The death
lifecycle therefore records an unrelated engaged NPC as killer in each tested
case, not the poison applier or an explicit environment cause.

A08.3 needs a causal source kind and durable identity that can be passed into
death settlement. It must not infer poison ownership from the mutable opponent
field, and it must define a safe offline/despawned-source fallback without
holding a stale live `Mob` reference.

### Core and named producer parity

The executable producer slice now proves both no-damage/failed guards and
successful application for the common combat routes. It also pins the current
power/source differences: Rune dagger poison applies 40 with a 100 ceiling,
dual-element Acid 40/40, ordinary primary Guthix 20/40, advanced secondary
Guthix 20/40 after its 50% roll, full-health Corrosive Aura 10/10, and default
Dungeon Spider poison 38/38 without source identity. Antidote protection blocks
the NPC producer before its random roll.

This coverage uses production methods and deterministic production random
sources; it does not substitute a model-only calculation. Sinister Chest,
admin self-poison, and the disabled-on-My-World legacy PvP script remain for a
later exceptional-producer slice because their value lies chiefly in plugin,
command, and configuration integration boundaries rather than another direct
`Mob.applyPoison` formula.

### Poison permits detached duplicate scheduler streams

Normal `applyPoison` calls reuse the mob's canonical event, but `PoisonEvent`
declares `ALLOW_MULTIPLE`. A deliberate second registration for the same target
is therefore admitted. Both streams damage independently, while only the first
event remains reachable through the mob's `poisonEvent` attribute and
`getCurrentPoisonPower`. Cure can stop only that canonical event; an already
admitted detached stream is outside the target's cleanup ownership.

Generic `BurnEvent` instead declares `ONE_PER_MOB`, and the same direct
duplicate attempt is rejected. This scheduler protection does not solve the
active-reapplication orphan described above because burn state mutation and
attribute replacement happen before the failed registration. Typed A08 state
must own exactly one clock by identity and make registry admission, state
mutation, and cleanup atomic.

Complete but nonsensical burn cache pairs (`0/3`, `7/0`, and negative values)
all schedule events during login. Their first pulse recognizes invalid state
and clears them without damage. This is less severe than the nonnumeric login
failure, but still violates fail-closed restoration: invalid optional state
should be normalized before scheduler admission, not one clock cycle later.

### Legacy PvP poison and large valid burn boundaries

`PlayerPoisonScript` correctly refuses to run while `WANT_MYWORLD` is true.
With the compatibility mode enabled, it requires a wielded item whose name
contains `poisoned`, passes its one-in-four random gate, and applies an
unattributed 48/48 poison state. Its execute path separately honors the
target's antidote protection. It is therefore an active compatibility producer,
not a safe generic producer to delete or migrate by a My World-only search.

Positive burn values have no upper input bound. A 39-damage event with
`Integer.MAX_VALUE` pulses persists the decremented pulse count correctly. An
`Integer.MAX_VALUE` damage pulse reaches normal player death handling and
cleans up its event; the combat presentation layer caps that hitsplat at 255.
Typed A08 state must decide and validate its maximums explicitly, while
preserving that client-facing cap wherever protocol compatibility requires it.

### Failure boundaries are not transactional today

There are two distinct failure boundaries. A direct `PoisonEvent.run()` failure
leaves the event running, but the ordinary scheduler `GameTickEvent.call()`
catches the same callback exception and stops the event. Neither path rolls
back any state already changed by a callback; typed A08 ticking must validate
before mutation and define whether an invalid record is quarantined, cured, or
reported.

The real missing-row logout-save route reveals a separate, pre-existing
failure: `PlayerService.savePlayer` attempts to build an error message with an
invalid `MessageFormat` pattern. That unchecked exception escapes
`PlayerSaveRequest` (which catches only `GameDatabaseException`) before it can
clear `saving`/`loggingOut` or execute its documented failed-save session
cleanup. The player and its active poison event remain live. A08.4 must not
rely on a successful logout-save for safe effect cleanup; the save/logout path
also needs its own atomic failure correction before any durable provenance
record is introduced.

## Current behavior now guarded

| Boundary | Executable result |
| --- | --- |
| Poison reapply below ceiling | Adds power, raises ceiling if needed, reuses event, preserves countdown |
| Poison reapply at ceiling | No power increase; latest call still replaces/clears player owner |
| Poison source logout | Target continues ticking; Leach pauses |
| Same source relog | Stable UUID resolves new session; live equipment can resume Leach |
| Poison target relog | Current/maximum restore; owner is lost; one event starts at eight ticks |
| Three repeated target relogs | Logout removes each scheduled event; each fresh session restores exactly one new poison and burn event |
| Direct duplicate poison registration | Scheduler admits a detached second stream; both streams damage |
| Direct duplicate burn registration | Scheduler rejects the second stream |
| Zero/negative complete burn cache | Invalid event schedules, then clears without damage on first pulse |
| Legacy PvP poison | Disabled in My World; compatibility path applies unattributed 48/48 poison after its own guard/roll |
| Large positive generic burn | Large pulse count decrements safely; maximum damage is lethal but visible hitsplat caps at 255 |
| Poison contribution | Always zero through generic tick path |
| Normal poison cure | Runtime and cache clear |
| Cure with missing event | Runtime clears; legacy cache incorrectly remains |
| First generic burn | Configured damage/pulses work and cache updates after pulse |
| Active generic burn reapply | New state is cleared; replacement is not scheduled |
| Complete generic burn cache pair | Restores one fresh eight-tick event |
| Partial generic burn pair | No event; lone cache key remains stale |
| NPC generic burn removal | Burn remains and can tick after unregistering begins |
| Nonnumeric cache value | Login throws; poison may leave a scheduled zero-state event |
| Negative/overflow poison | Invalid running state restores/forms, then pulse validation throws |
| Scheduler-dispatched invalid poison | Scheduler catches the callback error and stops the event; direct `run()` does not |
| Missing-row logout save | Error-format exception escapes before flags, session, or poison cleanup |
| Wrong runtime attribute type | Cure/extinguish throws instead of failing closed |
| Player death | Generic poison and burn state/cache clear |
| Player poison death | Mutable current opponent, not poison source, owns death lifecycle |
| NPC/environment poison death | Applying cause is absent; mutable current opponent owns death lifecycle |
| Elder armor source logout | Event clears before another pulse |
| Elder boss source removal | Event and target markers clear before another pulse |
| Core poison producers | Reciprocal/PvM/projectile weapon and Acid success plus zero-damage guard characterized |
| Named poison producers | Guthix, Corrosive Aura, and NPC success/failed gates characterized |

## Approved policy decisions

The project owner approved the following A08 migration policy on 2026-08-07:

1. **Capped ownership transfer:** an application that adds zero power does not
   replace poison ownership. Only a state-changing application may transfer
   ownership.
2. **Poison contribution:** factual poison damage counts toward its durable
   player's NPC contribution so contribution and kill ownership agree.
3. **Offline owner death:** an offline poison owner receives no synthesized
   loot or XP. An unrelated current opponent also must not receive the poison
   kill merely because it occupies the legacy opponent field. Durable source
   identity remains available for audit and an explicit no-online-owner reward
   result.
4. **Generic burn disposition:** retire/migrate the dormant generic burn family
   instead of preserving it as an active effect family. Recognized legacy rows
   must be handled deterministically and safely; retirement must not turn
   malformed optional state into an account-login failure.

These decisions authorize future fixture expectations and A08.3/A08.4 design.
They do not retroactively turn the current-behavior assertions into desired
behavior, and this characterization branch still makes no runtime changes.

The broader source-kind question from A08.1 also remains: NPC, environment, and
script poison need explicit causal identity for player deaths rather than a
coincidental opponent.

## Remaining matrix before A08.3/A08.4

This checkpoint completes the currently actionable generic lifecycle and
failure-injection characterization. The remaining implementation-phase work is:

- add target-policy fixtures for the four approved migration decisions;
- replace source-order producer guards for Sinister Chest/admin self-poison
  with plugin/command integration tests if a narrow plugin harness becomes
  available; and
- characterize server-restart provenance persistence only after A08.3 creates
  a versioned durable source record to preserve.

Cases governed by the four approved decisions should gain explicit target-
policy fixtures alongside (not silently replacing) the current-behavior
migration baselines when typed state/settlement is introduced.

## A08.3 target-policy foundation

`PeriodicEffectProvenance` and `PoisonTargetPolicy` now make the approved
decisions explicit without retaining live entities or changing current combat
settlement. They distinguish durable player identity, NPC runtime identity plus
lifetime generation, and stable environment/script keys. The executable
foundation fixes these migration invariants before registry/tick wiring:

- an application can transfer poison provenance only when it increases current
  poison power;
- only a live player provenance is eligible for player contribution/reward
  attribution—an offline player, NPC, environment, or script source cannot
  fall back to a target's unrelated current opponent; and
- generic burn is explicitly marked `RETIRE_AND_MIGRATE`, rather than being
  silently adapted into either active Elder burn family.

These classes do not yet replace `Mob` fields, `PoisonEvent`, cache keys, or
the legacy damage helper. The next branch must attach the value types to one
atomic target registry/application path, then migrate settlement only with the
approved contribution and lethal-credit tests enabled.

## Verification

- `./server/test_combat` — PASS, 113/113 scenarios on the combined published
  combat baseline.
- `python3 tests/myworld/test-poison-balance.py` — PASS.
- `python3 tests/myworld/test-npc-poison-death-lifecycle.py` — PASS.
- `python3 tests/myworld/test-jewelry-runtime-effects.py` — PASS.
- `python3 tests/myworld/test-poison-model.py` — PASS.
- `./scripts/lint.sh all --offline --base 904218197c72be996329a50c9acd8d873fac6a7d`
  — PASS; no new javac, Checkstyle, PMD, or SpotBugs findings.
