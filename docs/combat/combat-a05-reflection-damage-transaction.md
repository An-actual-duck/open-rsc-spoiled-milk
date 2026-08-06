# A05.4B Reflection Damage Transaction

## Bounded authority change

A05.4B moves only the existing Hits subtraction, damage update, and
armor-proc hitsplat for five reflection policies through
`ResolvedDamageTransaction`:

- Frostbite in `CombatEvent`, `PvmMeleeEvent`, and `ProjectileEvent`;
- Cleric Thorns in those same three event owners;
- melee jewelry recoil in `CombatEvent` and `PvmMeleeEvent`;
- projectile jewelry recoil in `ProjectileEvent`; and
- `DivineRetribution.apply`.

These remain separate policies. No formula, proc chance, RNG draw, target
selection, mitigation, incoming-hit reduction, contribution, lifesteal,
recursion, packet, result, or death authority moves.

## Stable identities

All requests use `OWNED_EFFECT` and the existing armor-proc hitsplat.

| Policy | Event owner | Stable effect identity | Style | Event UUID |
| --- | --- | --- | --- | --- |
| Frostbite | reciprocal melee | `reciprocal-melee-frostbite-reflection` | Magic | owner |
| Frostbite | PvM melee | `pvm-melee-frostbite-reflection` | Magic | owner |
| Frostbite | projectile | `projectile-frostbite-reflection` | Magic | owner |
| Cleric Thorns | reciprocal melee | `reciprocal-melee-cleric-thorns` | Melee | owner |
| Cleric Thorns | PvM melee | `pvm-melee-cleric-thorns` | Melee | owner |
| Cleric Thorns | projectile | `projectile-cleric-thorns` | Melee | owner |
| jewelry recoil | reciprocal melee | `reciprocal-melee-jewelry-recoil` | Melee | owner |
| jewelry recoil | PvM melee | `pvm-melee-jewelry-recoil` | Melee | owner |
| jewelry recoil | projectile | `projectile-jewelry-recoil` | unset | owner |
| Divine Retribution | shared content helper | `divine-retribution` | Melee | none |

The style metadata describes existing contribution policy rather than a new
formula. Frostbite continues to use the Magic ledger. Melee Thorns/recoil and
Divine Retribution continue to use the combat ledger. Projectile recoil keeps
style unset because it historically records no style contribution.
`DivineRetribution.apply` has multiple event and boss callers, so it does not
claim a single caller event UUID.

## Preserved policy and order

### Frostbite

The attacker consumes one Frostbite source before settlement. Half of the
pending hit, rounded up, is reflected and the same amount is removed from the
pending primary hit. The consumed source remains the credited source;
player-to-NPC damage remains Magic contribution. Player targets receive the
same Hits stat packet after contribution. Reciprocal and PvM melee retain their
own `onDeath` adapters, while projectile Frostbite retains direct
`killedBy(creditedSource)`.

### Cleric Thorns

The Cleric runtime still selects and rolls Thorns after established summon,
Blood Amulet, and spell lifesteal. The protected player remains the source and
NPC contribution remains combat contribution. Thorns produces no new
lifesteal, recoil, or recursive Thorns processing. Each event retains its old
death adapter, including simultaneous primary-victim and reflected-attacker
death.

### Jewelry recoil

Melee recoil keeps its equipment chance/divisor and combat contribution, stat
packet, and owning event death adapter. Its settlement now uses a dedicated
wrapper so the historically shared chain-lightning helper remains outside the
transaction. Projectile recoil keeps its chance/divisor, records no
contribution and no player Hits stat packet, resets range only for lethal
ranged types 2/5, checks Ring of Life only after nonlethal damage, and calls
`killedBy(opponent)` directly.

### Divine Retribution

Prayer, equipment, chance curve, and twice-incoming-damage calculation remain
in `DivineRetribution`. The Divine combat-effect update remains caller-owned
and is installed with the reflected hit presentation. NPC attackers retain
capped combat contribution; player attackers retain one Hits stat packet. The
helper still returns `didProc`, requested damage, and the terminal-attacker
fact without invoking death. Combat, projectile, and boss callers retain their
own ranged-reset and simultaneous-death order.

## Explicit exclusions

A05.4B does not migrate or alter chain lightning, Splinter, blood/death robe
splash, Scythe cleave, Death Amulet, Death Ring, poison, burn, AoE/splash,
auxiliary damage, delayed spells, boss primary/splash damage, summon bonus
damage, unknown projectile compatibility types, scripts, or `Mob.damage`.
Divine Retribution may still run from an existing boss caller, but only the
shared reflected hit inside `DivineRetribution.apply` moves; the boss's own
damage and caller order do not.

## Executable parity

Five characterization scenarios grow the authoritative gate from 48 to 52
because one earlier combined Frostbite/Thorns scenario was replaced by five
policy-specific scenarios. The fixtures execute all event owners and pin:

- pending-hit reduction, one-use Frostbite consumption, Magic attribution,
  displayed overkill, player stat packets, and all three death adapters;
- post-primary/post-lifesteal Thorns ordering, combat attribution, recursion
  exclusion, and simultaneous death;
- both melee recoil event adapters, combat attribution, player stat packets,
  and executable exclusion of the chain helper;
- projectile recoil's Ring of Life branch, ranged-only reset, direct death,
  absent contribution, and intentionally absent player stat packet; and
- Divine result semantics, combat-effect presentation, contribution, player
  stat packet, caller-owned death, ranged reset, and simultaneous death.

Every effective reflection also asserts source/target identity, stable key,
category, style, event identity, resolved/actual/legacy/overkill values,
terminal fact, hitsplat type, and transaction cardinality. No client rendering
or packet format changes, so this branch has no private visual acceptance
surface.
