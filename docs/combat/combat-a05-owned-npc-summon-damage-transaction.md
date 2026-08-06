# A05.4D Owned NPC and Summon Damage Transaction

## Bounded authority change

A05.4D moves only the existing Hits subtraction, damage update, and hitsplat
for three owned secondary-damage families through
`ResolvedDamageTransaction`:

- Balrog Magic splash in `ProjectileEvent.applyBalrogMagicSplash`;
- Elder Green Dragon melee sweep, ranged fireshot, compatibility Magic, and
  burn settlement in `ElderGreenDragonSpecialAttacks.inflictPlayerDamage`;
  and
- summon trait bonus damage in `Summoning.inflictSummonBonusDamage`.

No formula, random draw, target selection, range/layer check, mitigation,
contribution, tracking, effect, packet, reflection, Ring of Life, aggro, or
death authority moves. Each request contains damage already resolved by its
original caller, and the transaction owns only the same three adjacent
state/presentation mutations.

## Stable identities

Every request uses `OWNED_EFFECT`. Balrog child hits retain the owning
projectile event UUID. Elder and summon helper requests have no event UUID.

| Family | Stable effect identity | Style | Hitsplat |
| --- | --- | --- | --- |
| Balrog splash | `projectile-balrog-magic-splash` | Magic | armor proc |
| Elder melee sweep | `elder-green-dragon-melee-sweep` | Melee | standard |
| Elder ranged fireshot | `elder-green-dragon-ranged-fireshot` | Ranged | armor proc |
| Elder compatibility Magic | `elder-green-dragon-magic-secondary` | Magic | caller-selected |
| Elder burn pulse | `elder-green-dragon-burn-pulse` | unset | armor proc |
| summon Magic bonus | `summon-bonus-magic` | Magic | armor proc |
| summon Melee bonus | `summon-bonus-melee` | Melee | armor proc |

Burn intentionally has no combat style. This preserves its existing exclusion
from True Defense and keeps the owned Elder burn distinct from primary Magic
damage and from the generic `BurnEvent` compatibility helper.

## Preserved family policies

### Balrog splash

Positive primary Magic damage, Balrog identity, half-damage rounding, radius
two selection around the primary target, signed-level filtering, summon target
eligibility, and Guard Dog suppression remain caller-owned. Fire-element robe
mitigation, Magic potion reduction, and True Defense retain their exact order.
A fully blocked splash still sends one Hits stat packet and the True Defense
effect without publishing a damage request or hitsplat. Positive child hits
retain the inherited projectile impact effect, Balrog damage tracking, stat
packet, displayed overkill, and direct `killedBy(balrog)` order.

### Elder Green Dragon

Each Elder style retains its own robe/potion mitigation followed by summon
absorption. True Defense still applies to melee, ranged, and compatibility
Magic but not burn. Explicit zero settlements still publish the caller's
hitsplat, while legacy melee formula tracking is not duplicated. Hits stat and
party packets, Corrosive Aura, Divine Retribution, reflected-dragon death,
victim death, combat timer, Ring of Life, and returned damage remain in their
original order outside the transaction.

### Summon bonus damage

Trait selection, bonus rolls, and all eligibility remain outside the helper.
Player targets retain generic robe mitigation followed by only the matching
Magic or Melee potion reduction. NPC targets retain capped summon-owner
contribution when the owner is online. Player stat packets remain caller-owned,
and the helper continues to return lethality without invoking any local death,
aggro, or lifesteal adapter.

## Executable parity

Three runtime characterization scenarios were committed before production
migration. The existing poison regression had already brought the post-A05.4C
gate to 60 scenarios; A05.4D grows it from 60 to 63. Together the new scenarios
execute:

- Balrog mitigation, target eligibility, Guard Dog suppression, signed-level
  filtering, True Defense, effects, tracking, packets, and child death;
- Elder melee, ranged, burn, zero-hit True Defense, tracking, party packets,
  Ring of Life, Divine Retribution, reflection ordering, and level filtering;
  and
- summon Magic/Melee mitigation, online/offline owner contribution,
  nonlethal and overkill settlement, player packets, returned lethality, and
  caller-owned death.

Every effective settlement asserts source/target identity, stable key,
category, style, event-identity policy, resolved/actual/legacy/overkill values,
terminal fact, hitsplat type, and exact transaction cardinality. The Elder
fixture also asserts that the incoming result is published before the existing
Divine Retribution result.

## Explicit exclusions

A05.4D does not migrate delayed spell secondaries, generic poison or burn,
environmental or script damage, boss mechanics outside the named Elder and
Balrog paths, primary summon attacks, reflection implementations, unknown
projectile compatibility settlement, or `Mob.damage`. It does not consolidate
selectors, generalize mitigation, add encounter identity, or alter client
packets. This server-only branch has no private visual acceptance surface.
