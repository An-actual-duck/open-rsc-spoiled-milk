# A05.4C Player-Child and Area Damage Transaction

## Bounded authority change

A05.4C moves only the existing Hits subtraction, damage update, and hitsplat
for six player-outgoing child/area families through
`ResolvedDamageTransaction`:

- chain lightning in `CombatEvent`, `PvmMeleeEvent`, and `ProjectileEvent`;
- projectile Splinter and blood-robe splash;
- death-robe overkill splash in all three event owners;
- PvM Scythe cleave;
- `Player.applyDeathAmuletBurst`; and
- `Player.applyDeathRingChargeHit`.

No formula, RNG draw, target selection, layer/range check, area suppression,
mitigation, contribution, summon assist, aggro, charge, lifesteal, packet, or
death authority moves. The transaction receives already-resolved damage and
owns only the same three adjacent presentation/state mutations.

## Stable identities

Every request uses `OWNED_EFFECT`. Event-owned requests keep their existing
event UUID; the two player-owned equipment helpers have no event UUID.

| Family | Owner/path | Stable effect identity | Style | Hitsplat |
| --- | --- | --- | --- | --- |
| chain lightning | reciprocal melee | `reciprocal-melee-chain-lightning` | Melee | armor proc |
| chain lightning | PvM melee | `pvm-melee-chain-lightning` | Melee | armor proc |
| chain lightning | projectile | `projectile-chain-lightning` | originating Magic or Ranged | armor proc |
| Splinter | projectile | `projectile-splinter` | Magic | armor proc |
| blood-robe splash | projectile | `projectile-blood-robe-splash` | Magic | armor proc |
| death-robe overkill | reciprocal melee | `reciprocal-melee-death-robe-overkill` | Melee | armor proc |
| death-robe overkill | PvM melee | `pvm-melee-death-robe-overkill` | Melee | armor proc |
| death-robe overkill | projectile | `projectile-death-robe-overkill` | originating Magic or Ranged | armor proc |
| Scythe cleave | PvM melee | `pvm-melee-scythe-cleave` | Melee | standard |
| Death Amulet | player equipment | `player-death-amulet-burst` | Melee | armor proc |
| Death Ring | player equipment | `player-death-ring-charge-hit` | Melee | armor proc |

Projectile chain-lightning compatibility invocations with an unrecognized
projectile type retain unset style metadata and no style contribution. This
does not make that unknown primary projectile type part of A05.3.

## Preserved family policies

### Chain lightning

Chaos-equipment chance, target uniqueness, hop cap, halving, projectile
presentation, same-layer/range selection, summon exclusion, and guard-dog area
suppression remain caller-owned. Projectile child hits retain type-specific
Magic/Ranged potion mitigation and contribution. Melee child hits retain combat
contribution. No child path gains aggro or lifesteal. Each child is presented
before its existing direct/event death adapter runs.

### Splinter and robe splashes

Splinter retains its radius-two same-layer non-summon selection, guard-dog
suppression, half-damage rounding, Magic contribution, and conditional chase.
Blood robes retain blood-spell/equipment eligibility, area filtering, Magic
contribution, summon-owner assist, direct Magic death, and no child aggro or
lifesteal. Death robes retain positive-overkill eligibility, area filtering,
originating combat/Magic/Ranged contribution, summon-owner assist, source-style
kill state, and per-child death order.

### Scythe cleave

Selection and the summon outgoing-damage cap remain outside the transaction.
An explicit zero hit still produces a standard zero hitsplat and starts aggro.
Positive hits retain combat contribution, summon assist, Divine Grace, Blood
Amulet and summon lifesteal, Death Ring, death-robe splash, party update,
combat timer, and terminal handling in their original order.

### Death Amulet and Death Ring

Death Amulet retains kill-triggered charge acquisition/spending, equipment
tier, random damage range, layer/radius filtering, summon exclusion, area
suppression, combat contribution, no summon assist, and direct per-child death.
Death Ring retains its caller-owned eligibility and charge requirement, fixed
bonus damage, summon exclusion, combat contribution, summon assist, unchanged
stored charge, and returned lethal flag. Its caller still owns death order.

## Executable parity

Seven characterization scenarios were committed before production migration
and grow the authoritative combat gate from 52 to 59. Together they execute:

- reciprocal, PvM, Magic-projectile, and Ranged-projectile chain policies;
- Splinter selection, contribution, chase, and suppression;
- blood-robe nonlethal/lethal and area-filter policies;
- all four death-robe owner/style paths, including child death;
- Scythe zero, nonlethal, lethal, aggro, assist, lifesteal, and level checks;
- Death Amulet charge, area, suppression, random-range, and death policies; and
- Death Ring charge retention, assist, returned-lethal, and caller death.

Every effective child settlement asserts source/target identity, stable key,
category, style, event-identity policy, resolved/actual/legacy/overkill values,
terminal fact, hitsplat type, and transaction cardinality. The fixtures also
assert representative same-layer/range, cross-level, summon, guard-suppression,
contribution, aggro, charge, lifesteal, and presentation-before-death behavior.

## Explicit exclusions

A05.4C does not migrate unknown primary projectile compatibility settlement,
Frostbite, Cleric Thorns, jewelry recoil, auxiliary damage, Balrog/dragon/boss
area damage, summon bonus damage, poison, burn, delayed spells, environmental
or script damage, or `Mob.damage`. It does not consolidate selectors or
generalize area policy. No client packet or rendering format changes, so this
server-only branch has no private visual acceptance surface.
