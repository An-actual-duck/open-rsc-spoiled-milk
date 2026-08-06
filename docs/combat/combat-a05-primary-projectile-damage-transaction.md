# A05.3 Primary Projectile-Impact Damage Transaction

## Outcome and authority boundary

A05.3 moves only the direct Hits mutation inside
`ProjectileEvent.projectileDamage` for maintained primary attack types `1`,
`2`, `4`, and `5` through the server-owned `ResolvedDamageTransaction`.
The moved statements remain at the existing impact point: after all current
outgoing adjustments and mitigation, including Cleric Ward/Aegis, and before
the impact animation, contribution, lifesteal, survivor effects, and death
adapter.

The transaction continues to own only:

1. current Hits capture;
2. the existing `Skills.subtractLevel(HITS, resolvedDamage, false)` call;
3. the existing damage update;
4. exactly one existing hitsplat;
5. an immutable factual result; and
6. safe optional observation.

It does not calculate accuracy or damage, apply mitigation, consume or recover
resources, grant XP, select targets, launch or validate a projectile, apply an
impact effect, record contribution, perform lifesteal, dispatch packets,
choose survivor effects, or handle death. Production observation remains
disabled.

## Maintained primary variants

| Current type/path | Transaction style | Stable effect key |
| --- | --- | --- |
| Player spell, type `1` | Magic | `projectile-player-magic-primary` |
| Player bow/crossbow, type `2` | Ranged | `projectile-player-ranged-primary` |
| Player knife/dart/shuriken, type `2` | Ranged | `projectile-player-thrown-primary` |
| NPC magic, type `1` | Magic | `projectile-npc-magic-primary` |
| NPC ranged, type `2` | Ranged | `projectile-npc-ranged-primary` |
| Summon magic, type `1` | Magic | `projectile-summon-magic-primary` |
| Summon ranged, type `2` | Ranged | `projectile-summon-ranged-primary` |
| Iban Blast compatibility, type `4` | Magic | `projectile-iban-primary` |
| Multicannon compatibility, type `5` | Ranged | `projectile-cannon-primary` |

Thrown identity is derived from the existing authoritative throwing-item sets,
not from a second item table. Every request retains its event identity,
optional current encounter identity, source/target lifecycle snapshots,
already-resolved damage, style, and existing summon-aware hitsplat type.

The summon remains the request's actor, matching the direct-melee convention;
the established post-hit code still attributes summon damage to its owner.
Unknown compatibility/debug types retain the original local Hits, damage, and
hitsplat statements. The ordinary type-`3` gnomeball path continues to use
`BenignProjectileEvent` and is not converted into combat damage.

## Preserved impact order

`ProjectileEvent` still performs, in order:

1. summon eligibility and outgoing-damage cap;
2. removed-target/range handling, player projectile buffs, and Ogre/Startle
   suppression;
3. robe, magic/ranged potion, summon-absorption, Frostbite, True Defense, and
   Cleric mitigation;
4. the bounded direct-damage transaction;
5. the existing on-entity impact effect;
6. NPC-to-player tracking and Balrog splash or player/summon contribution and
   Divine Grace;
7. summon, Blood Amulet, and Cleric lifesteal/effect ordering;
8. stat and party packets, Corrosive Aura, Divine Retribution, Death Ring, and
   Splinter;
9. the existing death adapter or unchanged poison, leather, dragon,
   elder-dragon, elemental-debuff, and chase hooks; then
10. the caller's unchanged post-impact chain lightning, recoil/Ring of Life,
    and attack-buff consumption.

`DamageResult.getLegacyDamageDealt()` supplies the same historical
`min(resolvedDamage, hitsBefore)` value previously calculated beside the Hits
mutation. This is necessary when the shared Hits setter activates Goblin's
Tenacity: factual HP loss can be smaller while downstream tracking and hooks
must retain their existing value.

## Launch and resource authority remains upstream

No launch producer changed:

- `RangeEvent` retains formula, arrow/bolt choice, XP timing, ammunition loss,
  recovery, sound, and delay;
- `ThrowingEvent` retains target selection, thrown-item loss/recovery, XP,
  duplicate-projectile policy, and shuriken behavior;
- `SpellHandler` retains rune validation/consumption, spell formula, Iban and
  god-spell behavior, finalization, and secondary scheduling;
- `NpcBehavior` and `RangeEventNpc` retain NPC style, profile, range, timing,
  prayer, and visual policy;
- `Summoning` retains attack cadence, movement, max-hit capping, owner credit,
  and summon-specific visuals; and
- `FireCannonEvent` retains cannonball consumption, formula, target selection,
  sound, and scheduling.

Launch/impact validation and settlement ledgers remain the separate A06
boundary.

## Explicit exclusions

The branch does not migrate chain lightning, recoil, Frostbite reflection,
Splinter, Blood/Death robe splash, Balrog splash, Cleric Thorns, auxiliary
magic/true damage, god-spell or Salarin secondary hits, dragon AoE, poison,
environmental damage, or any broad `Mob.damage` caller. These paths retain
their existing direct mutation and terminal adapters for later source-family
work.

## Executable verification

The authoritative combat gate grows from 38 to 42 scenarios. The four A05.3
scenarios cover:

- all nine maintained player-ranged, thrown, player-magic, NPC, summon, Iban,
  and cannon variants with exact projectile visual, impact effect, Hits,
  damage update, hitsplat type/cardinality, contribution family, damage
  ownership, and impact-phase XP behavior;
- zero damage, displayed overkill, exact capped contribution, current 28 Magic
  and 9 Hits XP settlement, kill type, terminal removal, and exactly one death
  callback;
- one `APPLIED_CURRENT_PATH` result per primary variant with stable key, style,
  category, identities, snapshots, resolved value, and factual outcome;
- magic and ranged Goblin's Tenacity results, including factual four-HP loss,
  historical five-damage hook value, and preserved displayed seven; and
- executable proof that an unknown non-primary compatibility type and chain
  lightning still settle locally and publish no A05.3 result.

The existing gate continues to cover deterministic formulas, ranged/throwing
cooldowns, one-tick projectile impact, attack-start transactions, missing-rune
rollback, Cleric direct-effect order, shuriken timing, summon owner credit,
poison, layered domains, death callbacks, and observer-failure isolation.
Focused combat/content tests and the authoritative core/plugin build remain
required. Production artifacts must contain `ResolvedDamageTransaction.class`
and no `CurrentCombat*` fixture classes.

No packet, animation, projectile visual, or client code changes in this slice,
so there is no private visual acceptance surface. Any formula, mitigation,
resource cost/recovery, XP, timing, contribution, lifesteal, effect, packet,
death, RNG-draw, or callback-cardinality delta is a stop condition.

## Provenance and next boundary

This is a selective adaptation of the request/result and transaction
separation from Classic-Scape commit
`e00c154b4650dc0a80f9cca91fde99cde77d01fa` by aicovergod. Spoiled Milk keeps
its current projectile types and event-specific policy; it does not import the
source pipeline's broad flags, contribution authority, deduplication, or
rejection behavior.

A05.4 must inventory secondary, reflection, and environmental families
individually before moving any of them. A05.3 does not establish that their
mitigation, presentation, attribution, or death semantics are interchangeable.
