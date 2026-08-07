# A05.6 Direct-Hits Mutation Cleanup

## Scope and conclusion

A05.6 reruns the direct-Hits inventory against published main
`c3d7b29a6`, after A05.5 atomic death lifecycle integration. The broad A05
sequence has reduced the original 32 direct `subtractLevel(HITS, ...)` sites
outside `ResolvedDamageTransaction` to five retained exceptions. Two explicit
administrative `setLevel(HITS, 0)` terminal paths also remain.

No remaining path is dead code. This branch moves the one non-negative core
settlement that is exactly representable by the existing request/result
contract: an unclassified `ProjectileEvent` hit. It does not force active
signed adjustments, administrative commands, scripted self-harm, or the broad
`Mob.damage` compatibility helper through a combat-shaped abstraction.

## Reproducible inventory

Run from the repository root:

```bash
rg -n --glob '*.java' \
  'subtractLevel\(Skill(s)?\.HITS(\.id\(\))?' server/src server/plugins
rg -n --glob '*.java' \
  'set(Level|TemporaryLevelAndMaxStat)\(Skill(s)?\.HITS(\.id\(\))?' \
  server/src server/plugins
rg -n --glob '*.java' \
  '\.(damage|damageAndGetActualDamage)\(' server/src server/plugins
```

After this branch, the first command reports the transaction's authoritative
mutation plus these five compatibility exceptions:

| Symbol | Active contract | Decision |
| --- | --- | --- |
| `ProjectileEvent.projectileDamage`, negative unclassified value | The elevated `Admins.npcShootPlayer` hook accepts an arbitrary signed type/value. A negative value raises Hits above maximum, publishes a negative `Damage`, and produces a zero-clamped hitsplat. | Retain only the signed branch. `DamageRequest` correctly rejects negative values because they are not damage. |
| `Admins.damagePlayer` | Elevated command accepts signed integers. Positive values apply Goblin Tenacity and immediately send a player stat packet; negative values can raise Hits without a maximum clamp. | Retain until command semantics and negative-input policy are approved and executable plugin coverage exists. |
| `Admins.damageNpc` | Elevated command accepts signed integers, publishes damage only, and contains compatibility recovery for an already-`killed` visible NPC before direct death dispatch. | Retain as an administrative policy, not a combat exemplar. |
| `ResetCrystal.smiteNpc` | Elevated Reset Crystal NPC action applies 9,999 damage, publishes damage only, and preserves the same already-`killed` compatibility recovery as `damagenpc`. | Retain with the Reset Crystal tool until its complete admin contract has a compiled fixture. |
| `ArmyOfObscurity.onOpInv` | Reading the Necronomicon above three Hits applies one point of nonlethal self-harm, including current player stat transport and Goblin Tenacity behavior, before dialogue continues. | Retain as active content. It is neither ordinary combat nor terminal damage. |

The two terminal zero assignments are also active and intentionally retained:

- `Admins.killPlayer` bypasses ordinary Hits mitigation, sends the current
  skill update, publishes a damage-only value equal to current Hits, then calls
  `killedBy(admin)`; and
- `Development.killNearbyCombatNpcs` seeds combat contribution, forces zero
  Hits, repairs an anomalous legacy `killed` projection when necessary, then
  invokes the normal NPC death adapter.

Healing, spawn initialization, tutorial/quest restoration, temporary maximum
Hits, and administrator `sethits` operations returned by the second command are
not damage and are outside A05.

## Bounded core migration

Unclassified projectile types are active primarily through the elevated
`npcshoot` compatibility hook; type `3` with the gnomeball visual is the
compiled representative. For a non-negative value, the old adjacent block did
exactly four things:

1. subtract already-resolved damage from Hits without sending a stat packet;
2. return damage capped to the target's pre-hit Hits;
3. publish one damage update and one summon-selected hitsplat; and
4. leave contribution, style, mitigation, death, and later event hooks to the
   existing `ProjectileEvent` caller.

That block now uses `ResolvedDamageTransaction` with stable effect identity
`projectile-unclassified-compatibility`, `ACTOR` provenance, the event UUID,
no invented `CombatStyle`, and the existing hitsplat type. The event still
owns all subsequent contribution branches, effects, player packets, and
`handleDeath` ordering. Because the unknown type matches no Magic/Ranged
branch, it still records no style contribution or XP.

The negative branch remains visibly adjacent and documented. Extending the
non-negative damage request to represent above-maximum healing would corrupt
the request/result invariants and make observability misleading.

## Explicitly retained compatibility helper

`Mob.damage` / `damageAndGetActualDamage` remains active at 139 call sites,
including 129 plugin calls in 54 plugin files. Its death-before-presentation,
current-opponent attribution, displayed-overkill, Goblin Tenacity, negative
input, and raw lethal Hits behavior remain incompatible with the present
resolved-damage transaction. Poison and burn still require A08 provenance and
lifecycle design before that helper can change.

This branch therefore does not delete the helper, change its callers, migrate
delayed lethal god/Iban settlement, or alter script/environmental death order.

## Executable parity

The existing unknown-projectile scenario is strengthened without inflating the
71-scenario combat gate. It executes:

- a nonlethal type-3 hit with exact Hits, damage, hitsplat, zero contribution,
  stable identity, actor category, null style, and one observation;
- a lethal overkill with factual/legacy damage, overkill, presentation before
  the NPC death listener, one callback, and the existing death adapter;
- the signed negative compatibility adjustment, including above-maximum Hits,
  negative damage presentation, zero-clamped hitsplat, and no misleading
  damage observation; and
- chain lightning afterward to prove its independent owned-effect identity and
  exact total observation cardinality remain intact.

## A05 stopping point

A05 now owns all ordinary primary melee/projectile and approved secondary
settlement blocks, plus the one non-negative unclassified core projectile
block. The remaining direct sites are active policy boundaries, not redundant
post-migration blocks. A05 stops here rather than converting unlike behavior
merely to make an inventory command empty.

Follow-up work must remain separately approved and characterized:

1. A08 typed poison/burn provenance and lifecycle;
2. signed administrative command validation and compiled plugin fixtures;
3. Reset Crystal/development-tool terminal semantics;
4. scripted/environmental `Mob.damage` categories and presentation order; and
5. delayed lethal god/Iban compatibility settlement.
