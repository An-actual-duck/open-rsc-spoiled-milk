# A08.2 DoT Lifecycle Characterization

Status: executable current-state evidence complete; gameplay-policy decisions
remain open.

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
- poison continuing while its player source is offline, Leach stopping while
  that source is absent, and Leach resuming through the username-derived stable
  UUID after a new session equips the necklace;
- legacy player poison restoration with current/maximum values, no restored
  source, exactly one fresh event, and a full eight-tick countdown;
- legacy poison-without-maximum fallback and current orphan-cache cleanup
  behavior;
- generic burn first application, pulse/cache order, standard hitsplat,
  replacement behavior, complete-pair restoration, partial-pair behavior, and
  NPC-removal behavior;
- player death clearing generic poison and generic burn runtime/cache state;
- lethal poison credit when a different opponent is engaged and the
  opponentless lethal boundary; and
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

## Current behavior now guarded

| Boundary | Executable result |
| --- | --- |
| Poison reapply below ceiling | Adds power, raises ceiling if needed, reuses event, preserves countdown |
| Poison reapply at ceiling | No power increase; latest call still replaces/clears player owner |
| Poison source logout | Target continues ticking; Leach pauses |
| Same source relog | Stable UUID resolves new session; live equipment can resume Leach |
| Poison target relog | Current/maximum restore; owner is lost; one event starts at eight ticks |
| Poison contribution | Always zero through generic tick path |
| Normal poison cure | Runtime and cache clear |
| Cure with missing event | Runtime clears; legacy cache incorrectly remains |
| First generic burn | Configured damage/pulses work and cache updates after pulse |
| Active generic burn reapply | New state is cleared; replacement is not scheduled |
| Complete generic burn cache pair | Restores one fresh eight-tick event |
| Partial generic burn pair | No event; lone cache key remains stale |
| NPC generic burn removal | Burn remains and can tick after unregistering begins |
| Player death | Generic poison and burn state/cache clear |
| Elder armor source logout | Event clears before another pulse |
| Elder boss source removal | Event and target markers clear before another pulse |

## Decisions still required before typed settlement

A08.3 must not begin reward-changing settlement until the owner approves these
choices:

1. **Capped ownership transfer:** should an application that adds zero power
   replace poison ownership? Recommendation remains no; only a state-changing
   application should transfer ownership.
2. **Poison contribution:** should factual poison damage count toward the
   source's NPC contribution? Recommendation remains yes so contribution and
   kill ownership agree.
3. **Offline owner death:** if poison kills an NPC while its durable player
   owner is offline, should rewards be withheld, deferred, or given to an
   eligible current participant? Recommendation remains no synthesized offline
   loot/XP and no unrelated-opponent credit.
4. **Generic burn disposition:** should generic burn remain as a repaired
   compatibility family or should recognized legacy rows be retired? There are
   no active production applications, so migration/retirement is the smaller
   long-term surface if persisted legacy rows can be handled safely.

The broader source-kind question from A08.1 also remains: NPC, environment, and
script poison need explicit causal identity for player deaths rather than a
coincidental opponent.

## Remaining matrix before A08.3/A08.4

This checkpoint intentionally does not claim the full future matrix is done.
The next characterization slice should cover:

- overkill, zero factual damage, and Goblin Tenacity;
- repeated target relogs and scheduler duplication attempts;
- malformed, nonnumeric, negative, excessive, and mismatched cache values;
- failed logout-save and tick/death callback failure injection;
- player-target poison lethal attribution for player/NPC/environment sources;
- deterministic successful/failed application parity for every producer
  family; and
- server-restart persistence once a versioned source record exists to preserve.

Cases whose expected result depends on the four decisions above should remain
decision evidence, not guessed acceptance assertions.

## Verification

- `./server/test_combat` — PASS, 99/99 scenarios.
- `python3 tests/myworld/test-poison-balance.py` — PASS.
- `python3 tests/myworld/test-npc-poison-death-lifecycle.py` — PASS.
- `python3 tests/myworld/test-jewelry-runtime-effects.py` — PASS.
- `python3 tests/myworld/test-poison-model.py` — PASS.
- `./scripts/lint.sh all --offline --base 904218197c72be996329a50c9acd8d873fac6a7d`
  — PASS; no new javac, Checkstyle, PMD, or SpotBugs findings.
