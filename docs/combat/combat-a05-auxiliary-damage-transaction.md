# A05.4A Auxiliary Damage Transaction

## Bounded authority change

A05.4A moves only the adjacent Hits subtraction, damage update, and armor-proc
hitsplat in these six existing helpers through `ResolvedDamageTransaction`:

- `CombatEvent.inflictAuxiliaryMagicDamage`;
- `CombatEvent.inflictAuxiliaryTrueDamage`;
- `PvmMeleeEvent.inflictAuxiliaryMagicDamage`;
- `PvmMeleeEvent.inflictAuxiliaryTrueDamage`;
- `ProjectileEvent.inflictAuxiliaryMagicDamage`; and
- `ProjectileEvent.inflictAuxiliaryTrueDamage`.

No formula, RNG, effect selection, mitigation, contribution, lifesteal, aggro,
packet, return-value, or death authority moves. The helpers still reject zero,
negative, dead-target, and fully mitigated damage before entering the
transaction.

## Stable identities

Each event family and contribution family remains distinguishable:

| Event owner | Magic identity | Combat/true identity |
| --- | --- | --- |
| `CombatEvent` | `reciprocal-melee-auxiliary-magic` | `reciprocal-melee-auxiliary-true` |
| `PvmMeleeEvent` | `pvm-melee-auxiliary-magic` | `pvm-melee-auxiliary-true` |
| `ProjectileEvent` | `projectile-auxiliary-magic` | `projectile-auxiliary-true` |

All six requests use `OWNED_EFFECT`, their owning event UUID, and the existing
armor-proc hitsplat. Magic helpers use `CombatStyle.MAGIC`; true helpers use
`CombatStyle.MELEE` because they continue to feed the historical combat/melee
contribution ledger rather than the Magic ledger. "True" remains the existing
helper name, not a promise to bypass all mitigation.

## Preserved ordering and behavior

Before the transaction:

- Magic damage applies generic robe handling followed by Magic-resistance
  potion reduction for player targets.
- True damage applies generic robe handling only and ignores Magic, Melee, and
  Ranged resistance potions.
- Fully mitigated Magic damage returns zero and publishes no hit.

The transaction publishes the same resolved damage update and armor-proc
hitsplat, including lethal overkill. Its legacy result replaces only the
caller's former `min(damage, lastHits)` calculation.

After the transaction:

- Magic returns capped legacy damage and records player-to-NPC Magic
  contribution.
- True remains `void` and records player-to-NPC combat contribution.
- Player Hits stat packets remain after contribution.
- `CombatEvent` and `PvmMeleeEvent` retain their distinct `onDeath` adapters.
- `ProjectileEvent` retains `handleDeath`, including its ranged reset rules.
- No auxiliary helper adds local lifesteal or aggro.

The production observer remains disabled. When explicitly enabled for tests,
it sees the result after Hits/update/hitsplat settlement and before the
caller's existing contribution, packet, and terminal hooks.

## Explicit exclusions

A05.4A does not migrate or alter chain lightning, recoil, Frostbite, Splinter,
blood/death robe splash, Scythe cleave, Balrog or Elder Green Dragon splash,
Divine Retribution, Cleric Thorns, summon bonus damage, poison, burn,
environmental/script damage, delayed Salarin or god/Iban damage, unknown
projectile compatibility types, or `Mob.damage`.

The remaining source-family order from the A05.4 inventory is unchanged.
Reflection is the next listed family, but requires a separately authorized
branch and its own simultaneous-death and attribution fixtures.

## Executable parity

The authoritative combat gate grows from 46 to 48 scenarios. The A05.4A
fixtures exercise all six helpers and pin:

- nonlethal Hits, damage update, armor-proc hitsplat, return value, Magic versus
  combat contribution, and absence of local lifesteal;
- lethal displayed overkill, capped contribution, exact death callback,
  terminal unregister, and each existing event death adapter;
- Magic-potion reduction versus the true helper's robe-only path;
- zero and fully mitigated requests producing no update, hitsplat, or observed
  transaction; and
- all six stable keys, source category, style, event UUID, resolved/factual/
  legacy/overkill values, terminal fact, and result cardinality.

No client, packet format, animation, or visual behavior changes, so this branch
has no private visual-verification surface.
