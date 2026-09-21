# Slayer unique equipment and future weapon coating

Status: Shield of Mobility and normal-shop ingredient purchases implemented;
other rewards remain approved design. See [implementation](slayer-equipment-implementation.md).
Updated: 2026-09-21.

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
- Assembly is an ordinary Slayer shop purchase: ingredients and currency are
  displayed together in the existing cost panel. No separate assembly screen,
  crafting activity or associate dialogue transaction is needed.
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
  Pendant retaliation cannot trigger another retaliation. For the pendant,
  direct melee, ranged and magic hits all qualify when they deal positive
  primary damage. Map primary attack events explicitly for NPCs; do not treat
  every damage callback as eligible. This does not give the whip a proc from
  casting spells while holding it; its effect belongs to its own primary hits.

## Approved wear requirements

The owner's correction supersedes the earlier blanket no-restrictions answer:
weapons require the standard level for the explicitly selected requirement
tier. **Non-weapon equipment has no wear requirements** (Sullen Pendant and
Shield of Mobility). Do not add Slayer-rank, quest or extra skill gates
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
  opponent's attacks and movement for one tick**.
- **No stacking or refreshing**. When that tick ends, the target gains
  **three ticks of immunity** to this effect. Track delay/immunity on the
  target across all whip attackers; another attacker must not bypass the
  protection. Hits during the delay or immunity do not queue another delay
  or extend either timer.
- This is not a full-action trap: it does not add restrictions on eating,
  potions or other actions. The future PvP intent is a chasing weapon, subject
  to legal PvP targeting; PvP remains disabled for now.
- Visual construction: rib handle, spinal-column lash, sticky flesh binding.
  Slimey Residue is the baseline Abyssal material, not a second material item
  distinct from the previously written Slimey residue.

Exact examine text:

> Made from an abyssal demon's rib and spinal column, and held together with it's gooey flesh. Disgusting

Before implementation: map the tick boundaries to the existing attack/movement
scheduler and test multiple attackers. Do not turn this delay into the demon's
three-second full-action trap or the future stacking-slow system.

## Thunder Spire Staff

- Recipe: **1 Lightning Horn and 1 tier 10 log**.
- **Tier 10 magic staff stats**.
- Makes **all thunder spells area-of-effect**.
- The three thunder spell tiers gain **1-, 2- and 3-tile radii**, respectively,
  around the target. This is radius, not diameter; identify actual spell IDs
  in ascending tier order before implementation.
- Approved secondary splash power, in the same ascending tier order:
  **15% at radius 1, 25% at radius 2, and 40% at radius 3**. Apply the
  percentage to that thunder spell's maximum power before separate secondary
  damage rolls, following the god-spell calculation pattern. Do not reduce
  the primary hit or derive splash from its rolled damage. These percentages
  replace the god spells' splash coefficients for this staff.
- Removes the damage cap from **Thunder Strike**.
- Otherwise follow existing **god-spell AoE rules** for NPC splash. See the
  implementation reference below; the thunder radii replace the god spells'
  fixed radius, not their eligibility/suppression rules.
- Future player splash requires **PvP enabled**, legal combat in a **PvP
  area**, and a target who is **neither in the caster's party nor clan**.
  Do not enable PvP or damage players through splash while PvP is off.
- Visual: a staff with a Dark Beast's horn attached at its end.

Exact examine text:

> A staff that conducts electricity using a Dark Beast's horn

Implementation reference, inspected at `fc6d19a8d`:

- [SpellHandler.java](../../../server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java)
  `applyGodSpellAreaEffects` respects player area-effect suppression, excludes
  the primary target from splash, and rolls each secondary NPC separately.
  `isValidGodSpellAreaTarget` admits living, attackable, non-removed NPCs,
  excludes summons, and checks distance from the primary target. Candidates
  come from the caster's view area. Splash can cause idle NPCs to chase.
- [Point.java](../../../server/src/com/openrsc/server/model/Point.java)
  `withinRange` uses Pythagorean distance, not a square tile neighborhood.
- Current god-spell secondary power is **25% of maximum for ordinary god
  spells and 50% for advanced god spells**, before the secondary magic damage
  calculation. These are not guaranteed final-damage percentages. The staff's
  approved **15% / 25% / 40%** progression resolves the former open mapping;
  do not use the god-spell coefficients or full-strength splash for thunder.
  The design intent is to keep thunder splash below god-spell splash strength;
  verify actual damage during balancing because base spell power also matters.
  God-specific debuffs/lifesteal are not
  part of this staff's approved effects.
- The target helper does not itself establish line-of-sight, single-combat or
  player relationship checks. Audit the authoritative permission/damage path
  during implementation rather than claiming these are already covered.

Before implementation: resolve actual thunder spell IDs and the existing
Thunder Strike cap. Removing this cap does not authorize
removing unrelated engine safety limits. Equip/cast/projectile timing and
combat-permission handling need tests; this is not authorization to redesign
shared god-spell behavior.

## Leaching Bow

- Recipe: **2 Leach Tongue components and 1 tier 9 log**.
- **Tier 9 longbow stats**.
- **20% lifesteal, primary hit only**. Secondary/off-hand hits, splash,
  poison and other secondary damage sources do not heal through this bow.
- Calculate healing from **actual HP removed**, excluding overkill, and cap
  healing at the wearer's maximum HP. Carry fractional healing between
  qualifying hits so repeated small hits retain the intended 20% return;
  do not round each hit down and discard its fraction. No heal on a miss.
- Visual construction: two tongues woven into the bowstring.

Exact examine text:

> Two Bloodveld tongues are woven together to make the bowstring

Before implementation: map ammunition damage to primary versus secondary
events and define the fractional remainder's lifecycle across equipment and
session changes. The accumulator is for fractions, not banked whole-point
overhealing above maximum HP.

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
- Direct **melee, ranged and magic** hits all qualify when they deal positive
  primary damage. Splash, poison and other secondary damage do not qualify.
- Retaliation rolls **1%–10% of a normal enemy's maximum HP**, displayed as
  **yellow damage**. This uses enemy maximum HP, not wearer HP, damage
  received or enemy remaining HP.
- Retaliation **bypasses defenses**.
- Against **players**, the future ceiling is **5% of their maximum HP**
  (1%–5% roll), only when PvP returns and the retaliation is legally allowed.
  No player retaliation damage while PvP is disabled.
- **Bosses are immune**. On activation against a boss, show the response
  **"Is uneffected"** rather than dealing retaliation damage. This supersedes
  the earlier 10% boss ceiling and proposed individual boss exceptions.
- The **activation chance remains 10%**. The latest changes lower damage
  ceilings, not proc chance. The ranges above retain the previously specified
  1% lower bound; the owner did not change that lower bound.
- Concept: a smaller, reactive analogue of the Banshee's wail.

Exact examine text:

> You carry the Banshee's sorrows with you

Before implementation: settle percentage-roll distribution/rounding, identify
the authoritative boss classification, and audit non-defense protections and
combat permissions. Defense bypass does not authorize bypassing PvP-off or
boss immunity. Use the shared primary-hit and positive-damage rules;
retaliation chaining is excluded.

## Shield of Mobility

- Recipe: **500 Cockatrice Feathers**.
- Approximately **tier 3 shield stats**; deliberately weak ordinary blocking.
- While equipped, prevents the Abyssal demon's **extra Solvent timer drain**
  as well as its trap. An active Solvent buff's normal countdown continues.
- **Prevention only while equipped**. Equipping does not clear an existing
  debuff or bypass the demon trap's prohibition on equipment/other actions.
- Nullifies the **frog's Slimy Spit, cockatrice's Stony Glare and Abyssal
  demon's sticky-flesh/slime debuffs**.
- This includes frog attack prevention despite it not being a movement root,
  and the demon's full-action trap, not just its movement restriction.
- Prevents **all immobilization effects from enemies**, beyond these three
  named examples. Inventory enemy effect entry points to implement this
  consistently rather than only special-casing the new monsters.
- **PvP behavior requires closer inspection and adjustment before PvP is
  re-enabled**. Do not assume blanket protection against player-sourced
  effects has been approved. PvP remains off/on hold.

Exact examine text:

> A lightweight shield that doesn't block well but keeps you mobile

Implemented as item **3349** for **500 feathers + 110 Adept currency**.
Tier-3 bronze square-shield benchmark: **5 melee / 1 ranged / 0 magic defense**,
no wear requirement. Application-time checks cover the current enemy lock
entry points; see the [audit and tests](slayer-equipment-implementation.md).
Protection against the named debuffs does not establish poison, wail, lightning
or Feeding Frenzy immunity.

## Effect acceptance checks and future PvP gate

These are implementation/test requirements, not a claim that effects exist:

- Whip: one tick blocks attacks and movement, then three full ticks of
  immunity, then proc eligibility returns. Test repeated hits and multiple
  attackers; none may refresh, queue or stack delays during either window.
  Test unrelated action categories remain usable.
- Shield: cover every enemy immobilization route, including the attack-only
  frog debuff and full-action demon trap. Equipping after application must
  not cure an active effect. Unrelated enemy damage/debuffs remain effective.
  Verify demon hits do not accelerate Solvent expiry while shielded, but its
  ordinary countdown still runs.
- Staff: test radii 1/2/3, separate secondary rolls, primary exclusion,
  summons/dead/non-attackable exclusion, suppression and legal damage delivery.
  Verify tier splash coefficients of 15%/25%/40% respectively, applied before
  secondary rolls without reducing the primary hit.
- Pendant: positive primary damage can activate once; secondary/zero damage
  and retaliation chains cannot. Verify normal-enemy 10% ceiling, defense
  bypass, boss immunity/feedback and no player damage while PvP is disabled.
  Cover direct melee, ranged and magic hits, with splash and poison excluded.
- Bow: only primary hits contribute lifesteal; verify secondary and poison
  damage cannot produce additional heals. Test overkill exclusion, the maximum
  HP cap and fractional carry across repeated small hits.
- Before any future PvP activation, explicitly review the shield's scope;
  test the whip's shared-target immunity across multiple attackers; test staff
  party/clan exclusions and legal PvP areas; and test the pendant's 5% player
  ceiling and legal retaliation rules. **This plan does not enable PvP.**

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
