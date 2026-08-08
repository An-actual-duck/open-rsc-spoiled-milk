# A07.5E Red Dragon Fire Proc

## Scope and authority

A07.5E moves one proven-identical proc family, Red Dragon fire, into
`RedDragonFireProc`. The shared unit owns only the complete-set gate, chance
and damage draws, invocation of an event-owned auxiliary true-damage callback,
and target-debuff application. Reciprocal melee, PvM melee, and projectile
impact retain the surviving-target/player-source phase gate, auxiliary damage
transaction, mitigation, contribution, presentation, death adapter, and all
surrounding effect order.

This is a behavior-preserving ownership extraction. It changes no equipment,
chance, damage, magnitude, duration, defense calculation, contribution, aggro,
lifesteal, death, packet, animation, timing, or balance rule.

## Preserved contract

- The proc remains after Earth Dragon slow and before Black/King Black Dragon
  breath follow-ups in each leather on-hit chain.
- A complete Red Dragon set is required. Rejected equipment consumes no random
  value.
- An eligible attempt draws the existing 20% chance exactly once. A failed
  attempt consumes no damage draw.
- Success draws inclusive damage from 0 through 10. A zero roll still applies
  the debuff but does not invoke the auxiliary-damage callback.
- Settled zero primary damage remains eligible after the event reaches the
  surviving-target phase.
- Reciprocal melee and projectile owners retain the production random adapter;
  PvM melee retains its injected event random source.
- Positive rolled damage invokes the original event helper before applying the
  debuff. That preserves armor-proc hitsplats, combat-style contribution,
  player mitigation/stat packets, and each event's existing death adapter.
- The target retains the existing max-merge 6% defense penalty and five-attack
  refresh policy. Repeat success refreshes five remaining attacks rather than
  creating an independent stack. Other fire-defense penalties retain their
  separately owned additive composition rules.

The descriptor catalog remains descriptive. Runtime execution does not query
`SecondaryEffectDescriptor`.

## Explicit exclusions

Black Dragon, King Black Dragon, Infernal Fire, poison, splash, boss, summon,
delayed, reflection, AoE, and DoT behavior remain outside this branch. Similar
source shape does not approve a breath follow-up or any other family for
consolidation without its own executable characterization. Debuff state,
composition, defense application, and per-target-attack consumption remain
owned by `Mob`.

## Executable evidence

The compiled A07.5E scenario executes all three private production owners
before and after extraction. It proves:

- success on settled-zero primary damage;
- exact chance-then-inclusive-damage RNG transcripts;
- positive auxiliary damage, armor-proc presentation, and combat contribution;
- zero-damage success still applying and refreshing the debuff;
- one chance draw on failure and no draw for incomplete equipment, dead
  targets, or non-player sources; and
- 6% defense penalty, two representative attack consumptions, refresh after
  partial consumption, and expiry after five target attacks.

The authoritative combat gate grows from 92 to 93 scenarios. Existing
auxiliary-damage, leather, projectile, primary-damage, death, and debuff
coverage continues to guard the callback boundary and surrounding ordering. No
private visual acceptance is required for this server-only internal extraction.
