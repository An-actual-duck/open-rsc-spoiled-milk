# A08.2 DoT Lifecycle Characterization

Status: second executable current-state evidence milestone complete; the four
gameplay-policy decisions below were approved by the project owner on
2026-08-07. Remaining exceptional producer and failure-injection fixtures
still precede typed runtime implementation.

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
- three repeated fresh target sessions restoring poison and burn exactly once,
  with logout stopping/removing each session's scheduler entries and each new
  session receiving new event identities/full countdowns;
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

## Current behavior now guarded

| Boundary | Executable result |
| --- | --- |
| Poison reapply below ceiling | Adds power, raises ceiling if needed, reuses event, preserves countdown |
| Poison reapply at ceiling | No power increase; latest call still replaces/clears player owner |
| Poison source logout | Target continues ticking; Leach pauses |
| Same source relog | Stable UUID resolves new session; live equipment can resume Leach |
| Poison target relog | Current/maximum restore; owner is lost; one event starts at eight ticks |
| Three repeated target relogs | Logout removes each scheduled event; each fresh session restores exactly one new poison and burn event |
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

This checkpoint intentionally does not claim the full future matrix is done.
The next characterization slice should cover:

- deliberate duplicate scheduler registration attempts;
- excessive pulse/damage burn values and mismatched mixed cache values;
- failed logout-save and tick/death callback failure injection;
- exceptional producer integration for legacy non-My-World PvP poison,
  Sinister Chest, and admin self-poison, plus producer-inventory drift checks;
  and
- server-restart persistence once a versioned source record exists to preserve.

Cases governed by the four approved decisions should gain explicit target-
policy fixtures alongside (not silently replacing) the current-behavior
migration baselines when typed state/settlement is introduced.

## Verification

- `./server/test_combat` — PASS, 105/105 scenarios.
- `python3 tests/myworld/test-poison-balance.py` — PASS.
- `python3 tests/myworld/test-npc-poison-death-lifecycle.py` — PASS.
- `python3 tests/myworld/test-jewelry-runtime-effects.py` — PASS.
- `python3 tests/myworld/test-poison-model.py` — PASS.
- `./scripts/lint.sh all --offline --base 904218197c72be996329a50c9acd8d873fac6a7d`
  — PASS; no new javac, Checkstyle, PMD, or SpotBugs findings.
