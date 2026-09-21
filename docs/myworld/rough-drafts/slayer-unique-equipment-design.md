# Slayer unique equipment and future weapon coating

Status: owner-approved design direction; not implemented.
Updated: 2026-09-20.

Related: [drops, rarity and assembly](slayer-special-drops-and-assembly.md)
and [tower integration](slayer-tower-npc-integration.md).

## Scope and shared rules

This is the reference for reward behavior, component recipes and exact examine
text. The linked drop document owns the monster roster, baseline materials,
rarity policy, ordinary loot and assembly transaction requirements.

- Assembly exchanges the listed components **plus Slayer currency**. The
  currency tier follows the monster supplying the parts, not the finished
  equipment's stat tier. The charge must be sizable but **less than that tier's
  unchanged backpack-upgrade price**. Exact amounts and the source-tier-only
  assembly exception are approved in the
  [assembly price table](slayer-tower-task-expansion.md#approved-assembly-prices-below-the-backpack-benchmark).
  Currency is an additional effort gate even when the player buys components.
- **All these components and finished rewards are tradable**, and purchased
  parts are valid for assembly. There is no personal-kill requirement. This
  does not repeal the separate grandfathered retired-leather untradability rule.
- Rewards occupy weapon, shield or jewelry slots, preserving leather set slots
  and bonuses. Weapon wear requirements are approved below; the pendant and
  shield have no wear requirements. IDs, death behavior and
  exact numeric stat mappings remain undecided unless stated below.
- Tier references identify the intended existing stat benchmark, not a newly
  invented item level. Resolve the actual tier 9/10 log items and weapon stats
  from current game definitions before implementation.
- Preserve the supplied item names and quoted descriptions. Do not silently
  correct spelling in names such as Leaching Bow or Abyssal Vertibrae.
- Effects below are confirmed goals. Open implementation questions are not
  additional approved rules, and none of these rewards exists merely because
  this document describes it.
- Whip/pendant hit-trigger effects require **actual positive damage from the
  primary weapon/attack only**. Secondary/off-hand damage, poison ticks,
  reflected damage and other secondary damage sources do not trigger them.
  Pendant retaliation cannot trigger another retaliation. Map primary attack
  events explicitly for NPCs and resolve direct-spell eligibility before coding;
  do not treat every damage callback as an eligible weapon hit.

## Approved wear requirements

The owner's correction supersedes the earlier blanket no-restrictions answer:
weapons require the standard level for the explicitly selected requirement
tier. **Non-weapon equipment has no wear requirements** (Sullen Pendant and
Cockatrice feather shield). Do not add Slayer-rank, quest or extra skill gates
to wearing these rewards. Acquisition/shop access is a separate contract.

| Weapon | Approved requirement tier | Required skill level | Stat benchmark remains |
| --- | --- | ---: | --- |
| Abyssal Whip | Tier 10 melee | 70 Melee | Tier 10 longsword power, dagger speed |
| Dagger of Terror | Tier 5 melee | 30 Melee | Approximately tier 5 dagger damage, one-tick attacks |
| Thunder Spire Staff | Tier 9 magic | 62 Magic | Tier 10 magic staff stats |
| Leaching Bow | Tier 8 ranged | 54 Ranged | Tier 9 longbow stats |

Verified against tracked definitions at `dd0f64f22`: Rune long sword `75`
requires 70 and Steel dagger `63` requires 30 in
[ItemDefs.json](../../../server/conf/server/defs/ItemDefs.json). Magic Staff
`1784` requires 62 Magic and Ebony Longbow `2125` requires 54 Ranged in
[ItemDefsMyWorld.json](../../../server/conf/server/defs/ItemDefsMyWorld.json).
The same overrides give Blood Staff `2146` tier-10 stats and Magic Longbow
`656` tier-9 stats; the new rewards' lower wear tiers are intentional, not a
request to lower their approved stats or change their log recipes.

Use the current Melee requirement path rather than reintroducing separate
legacy Attack/Strength gates. Cover every supported equip route, including
poisoned Dagger of Terror variants, so alternate forms cannot bypass the gate.

## Abyssal Whip

- Recipe: **1 Abyssal Rib, 10 Abyssal Vertibrae, 50 Slimey Residue**.
- Attack power: **tier 10 longsword**; attack speed: **dagger**.
- On each eligible positive-damage primary hit, a **10% chance to delay the
  opponent's actions by one tick**.
- Visual construction: rib handle, spinal-column lash, sticky flesh binding.
  Slimey Residue is the baseline Abyssal material, not a second material item
  distinct from the previously written Slimey residue.

Exact examine text:

> Made from an abyssal demon's rib and spinal column, and held together with it's gooey flesh. Disgusting

Before implementation: define affected action categories, repeated-proc scheduling and
PvP applicability. Do not silently turn the one-tick delay into the demon's
three-second full-action trap or the future stacking-slow system.

## Thunder Spire Staff

- Recipe: **1 Lightning Horn and 1 tier 10 log**.
- **Tier 10 magic staff stats**.
- Makes **all thunder spells area-of-effect**.
- The three thunder spell tiers gain **1-, 2- and 3-tile radii**, respectively,
  around the target. This is radius, not diameter; identify actual spell IDs
  in ascending tier order before implementation.
- Removes the damage cap from **Thunder Strike**.
- Visual: a staff with a Dark Beast's horn attached at its end.

Exact examine text:

> A staff that conducts electricity using a Dark Beast's horn

Before implementation: audit thunder spell membership and the existing Thunder
Strike cap; define eligible targets, distance metric and how spell damage is rolled
for additional targets. Removing this spell's damage cap does not authorize
removing unrelated engine safety limits. Equip/cast/projectile timing and
PvP/multi-combat interaction also need explicit handling.

## Leaching Bow

- Recipe: **2 Leach Tongue components and 1 tier 9 log**.
- **Tier 9 longbow stats**.
- **20% lifesteal**.
- Visual construction: two tongues woven into the bowstring.

Exact examine text:

> Two Bloodveld tongues are woven together to make the bowstring

Before implementation: define eligible damage sources, damage basis, rounding,
overkill and healing limits, including any interaction with ammunition effects.
No minimum heal on a miss is specified.

## Dagger of Terror

- Recipe: **1 Terror Fang**.
- Attacks **once per tick**.
- Approximately **tier 5 dagger maximum damage**, with its faster cadence.
- **Accepts poison**.

Exact examine text:

> It stabs swiftly and ferociously

Before implementation: resolve exact damage/stat values and current poison
variants. Poison compatibility is approved; compatibility with the separate
future slow coating is not an instruction to implement that system now.

## Sullen Pendant

- Recipe: **1 Frozen Tear**, plus the shared Slayer-currency assembly cost.
- Visual: **a crystallized tear on a string**.
- Equipment slot: **neck**.
- **10% chance each time the wearer takes eligible primary-hit damage** to have the pendant
  **"cry out in pain"** and retaliate against the offending enemy.
- Retaliation rolls **1%–20% of that enemy's maximum HP**, displayed as
  **yellow damage**. This uses enemy maximum HP, not wearer HP, damage
  received or enemy remaining HP.
- Against **bosses and players**, reduce the retaliation ceiling to **10% of
  their maximum HP** (working interpretation: a 1%–10% roll). The activation
  chance stays 10%; the owner changed the damage percentage, not proc chance.
- Support **per-boss overrides**, potentially complete immunity with an
  activation message such as **"The King Black Dragon is unaffected by the
  Sullen Pendant"**. This is an example of an allowed override, not a finalized
  King Black Dragon immunity assignment or a blanket boss exemption.
- Concept: a smaller, reactive analogue of the Banshee's wail.

Exact examine text:

> You carry the Banshee's sorrows with you

Before implementation: settle percentage-roll distribution/
rounding, mitigation and individual boss overrides. Use the shared primary-hit
and positive-damage rules; retaliation chaining is excluded. No further hidden
caps or exemptions are approved.

## Cockatrice feather shield (name pending)

- Recipe: **500 Cockatrice Feathers**.
- Approximately **tier 3 shield stats**; deliberately weak ordinary blocking.
- **Prevention only while equipped**. Equipping does not clear an existing
  debuff or bypass the demon trap's prohibition on equipment/other actions.
- Nullifies the **frog's Slimy Spit, cockatrice's Stony Glare and Abyssal
  demon's sticky-flesh/slime debuffs**.
- This includes frog attack prevention despite it not being a movement root,
  and the demon's full-action trap, not just its movement restriction.
- Broader intent: immunity to immobilization effects. Inventory all other
  relevant effects before defining a generic immunity category.

Exact examine text:

> A lightweight shield that doesn't block well but keeps you mobile

Before implementation: choose the item name and exact stats. Decide
whether solvent duration still drains when this shield supplies immunity.
Protection against the named debuffs does not establish poison, wail, lightning
or Feeding Frenzy immunity.

## Naga: deferred unique component

**Serpant's Tail is on hold and must not be added to drops for now.** Naga stays
a normal/filler encounter with its ordinary loot and default hide/bones.
Its hide follows the now-approved tanning/Crafting leather-set direction;
exact set recipes and bonuses belong to the leather audit/design pass. No
Tail reward or active icon requirement is established.

## Future coating: collectible ingredients now, system later

**Cockatrice Eye** and **Sticky Saliva Gland** are each confirmed at **1/128 per
kill of their respective monsters**. Their eventual potion uses **one of each**
plus an **unspecified herb**, producing a new weapon coating. Herb identity/
quantity, names, production requirements and yields are undecided.

Long-term behavior:

- A **weaker stacking poison** plus **stacking slow points**.
- Slow requires substantially more accumulation before an effective stack is
  reached; it is not an extra tick of delay for every individual application.
- Each effective slow stack adds a tick to the enemy's action interval.
- Owner's illustrative threshold: **50 slow points per effective stack**;
  **100 points would add two ticks per action**. The example does not yet lock
  in 50 as the final threshold, nor imply an immediate two-tick stun.
- Maximums are required to prevent abuse. Exact caps are not set.

Deferred implementation work includes application amounts/chances, thresholds,
durations/decay, refresh rules, stack ownership, effective-stack caps, affected
action categories, boss/PvP policy, interaction with other delays, weaker-poison
values, potion crafting, and the full set of new coated weapon variants.

Do not implement a usable coating or placeholder slow behavior as part of the
initial ingredient-drop pass. Collectible ingredient definitions and drop
balancing can proceed without implementing this system.

## Next balance and implementation decisions

The [new monster task counts and payouts](slayer-tower-task-expansion.md) are
approved, not implemented. That plan records unchanged backpack prices and
approved below-backpack, source-tier-only assembly charges for all six rewards.

1. Follow the confirmed whip rates: **Abyssal Rib 1/2,000; single Abyssal
   Vertibrae 1/128**. These replace the earlier very-rare-vertebra direction;
   the rib is the intended rare component. The drop document distinguishes
   average rib acquisition from collecting all whip components. The other
   equipment rates are also approved: horn 1/2,000 and tongue/fang/tear 1/1,000,
   each one per successful independent roll, including off-task kills.
2. Use the approved fixed Slayer-currency amounts and source-tier-only assembly
   rule. The pendant requires one Frozen Tear. Feather
   quantities are 1–3 per kill with equal probability for each amount.
3. Resolve the effect-policy questions above and inspect existing tier stats,
   log identities, poison variants and combat scheduling before coding.
4. Produce approved icons using these designs and NPC references, including
   the separate backlog of five existing gimmick consumables.
5. Implement and test drops, atomic shop assembly and approved equipment
   effects in focused passes. Keep the coating system a separate future task.

This update is documentation only: no items, drops, combat behavior, sprite
assets or running servers were changed.
