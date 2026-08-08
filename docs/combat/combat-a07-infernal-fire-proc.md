# A07.5H Infernal Fire Proc

## Scope and authority

A07.5H extracts only the common Infernal Fire core from reciprocal melee,
PvM melee, and projectile impact into `InfernalFireProc`: equipped-tier lookup,
one chance draw, inclusive payload draw, target presentation, event-owned
auxiliary magic-damage callback, defense-debuff refresh, and a post-damage
owner callback.

The immutable result exists solely to preserve each owner's existing debug
record. It carries the same configured max hit, chance, roll, trigger state,
rolled payload, and settled damage values used before extraction.

## Explicit exclusions

The event owners retain surviving-target/player-source eligibility, magic
damage mitigation, contribution, hitsplats, lifesteal, packets, death adapters,
debug source labels, and all Hell's Inferno area policies. PvM melee and
projectile retain NPC-only Hell's Inferno splash; reciprocal melee retains its
additional PvP splash. Summon fire traits and all unrelated boss effects remain
outside this extractor.

## Ordering contract

On a successful chance, the shared unit preserves this order:

1. target Infernal/Blaze/Inferno presentation;
2. inclusive payload draw;
3. owner auxiliary magic-damage settlement;
4. target Infernal defense-debuff application; and
5. owner-specific post-damage follow-up.

The post-damage callback runs for a zero payload and after a terminal damage
adapter, matching the prior owner-local code. A missing full Infernal set makes
no random draw; a configured failed chance makes exactly one.

## Executable evidence

The compiled A07.5H fixture proves chance/payload draw cardinality, zero
payload behavior, presentation, callback ordering, defense-debuff refresh and
five-target-attack expiry, settled-damage forwarding, failed chance, and no-set
rejection. Existing Hell's Inferno and auxiliary-damage characterization keeps
the deliberately event-owned behavior under regression coverage.
