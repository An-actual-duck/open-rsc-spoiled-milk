# Jewelry and Retired Robe Effects

> Historical design record: several names, slot assignments, and charge values
> below were superseded during implementation. The audited current catalog,
> getters, and runtime activation checklist is
> [`enchantment-runtime-audit.md`](../completed-work-plans/enchantment-runtime-audit.md).
> Use that audit and the generated item definitions as the runtime source of
> truth.

This document records cloth effects retired from robes and the implemented
altar-jewelry runtime behavior.

## Retired Robe Effects

These effects are retired from cloth armor. Cloth armor should focus on magic
defense and matching-rune preservation.

- Air: no runtime special beyond the old defense split.
- Water: no runtime special beyond the old defense split.
- Earth: no runtime special beyond the old defense split.
- Fire: per-piece magic-defense bonus.
- Mind: reduced incoming debuff strength.
- Body: faster hitpoint restoration.
- Cosmic: chance to reroll a defensive roll.
- Chaos: reflected damage.
- Law: incoming damage smoothing through extra damage rolls.
- Nature: flat poison tick damage mitigation.
- Death: charged damage, Reaping, and Burst damage.
- Blood: max Hits, poison Leach, and lifesteal.
- Soul: lifesaving, item preservation, and charged healing Renewal.
- Life: no established robe-specific runtime effect.

## Superseded Design Notes

Earlier robe-to-necklace candidates, elemental jewelry drafts, and necklace
rune-preservation concepts are retired. Cloth and matching staves own rune
preservation in the live model; the jewelry catalog below is the active
runtime source of truth.

## Current Jewelry Effects

Implementation status:

- Implemented: elemental ring/necklace offense, elemental amulet defense, mind
  ring/necklace crafting XP, mind amulet combat XP, body ring/necklace
  gathering XP, body amulet discipline XP, cosmic ring wealth rolls, cosmic
  necklace standard loot rolls, cosmic amulet rare gathering duplication, chaos
  recoil, chaos necklace chain lightning, chaos amulet chaos-rune yield weaving,
  nature food healing, nature amulet poison decay, law ring skilling banking,
  law necklace monster-loot banking, law amulet teleports, death ring charge,
  death guaranteed-drop bonuses, charged death burst, blood max Hits, poison
  Leach, and lifesteal, soul item saving/life saving/healing Renewal, and life
  summon bonuses.
- Scrapped in runtime: nature ring iron-smelting protection and old cosmic
  amulet normal-resource duplication.
- Item names and examine descriptions now match the implemented effects in the
  server data and client fallback definitions.

### Current Names

- Air: Ring/Necklace of Archery; Amulet of Evasion.
- Mind: Ring/Necklace of Craftsmanship; Amulet of Combat.
- Water: Ring/Necklace of Balance; Amulet of Balance.
- Earth: Ring/Necklace of Force; Amulet of Guarding.
- Fire: Ring/Necklace of Sorcery; Amulet of Warding.
- Body: Ring/Necklace of Discipline; Amulet of Discipline.
- Cosmic: Ring/Necklace of Fortune; Amulet of Bounty.
- Chaos: Ring of Recoil; Necklace of Chain Lightning; Amulet of Chaos Weaving.
- Nature: Ring/Necklace of Nourishment; Amulet of Cleansing.
- Law: Ring of Skill Banking; Necklace of Loot Banking; Amulet of Teleportation.
- Death: Ring of Reckoning; Necklace of Reaping; Amulet of Ruin.
- Blood: Ring of Vitality; Necklace of Leach; Amulet of Siphoning.
- Soul: Ring of Lifesaving; Necklace of Preservation; Amulet of Renewal.
- Life: Ring of Endurance; Necklace of Vigor; Amulet of Command.

### Air

Ring/necklace:

- Flat ranged power bonus.
- Ring: `+3` ranged power per tier.
- Necklace: `+3` ranged power per tier.
- Ring and necklace stack.

Amulet:

- Flat ranged defense bonus.
- `+3` ranged defense per tier.

### Mind

Ring/necklace:

- Crafting-style skill XP bonus.
- Ring: `+5%` XP per tier.
- Necklace: `+5%` XP per tier.
- Ring and necklace stack to `+50%` XP at tier 5.

Amulet:

- Combat XP bonus.
- Applies to Melee, Ranged, Magic, and Summoning XP.
- `+5%` XP per tier.

### Water

Ring/necklace:

- Flat mixed offensive power bonus.
- Ring: `+2` melee, ranged, and magic power per tier.
- Necklace: `+2` melee, ranged, and magic power per tier.
- Ring and necklace stack.

Amulet:

- Flat mixed defense bonus.
- `+2` melee, ranged, and magic defense per tier.

### Earth

Ring/necklace:

- Flat melee power bonus.
- Ring: `+3` melee power per tier.
- Necklace: `+3` melee power per tier.
- Ring and necklace stack.

Amulet:

- Flat melee defense bonus.
- `+3` melee defense per tier.

### Fire

Ring/necklace:

- Flat magic power bonus.
- Ring: `+3` magic power per tier.
- Necklace: `+3` magic power per tier.
- Ring and necklace stack.

Amulet:

- Flat magic defense bonus.
- `+3` magic defense per tier.

### Body

Ring/necklace:

- Gathering-style skill XP bonus.
- Ring: `+5%` XP per tier.
- Necklace: `+5%` XP per tier.
- Ring and necklace stack to `+50%` XP at tier 5.

Amulet:

- Discipline XP bonus.
- Applies to Hits, Agility, Prayer, and Thieving XP.
- `+5%` XP per tier.

### Cosmic

Ring/necklace:

- Theme: Fortune.
- Ring: if the primary monster drop roll misses the rare loot table, `5%` per
  tier chance to reroll the monster drop.
- Necklace: `10%` per tier chance to roll an extra standard monster drop. Rare
  tables are suppressed on this extra roll.

Amulet:

- Gathering rare-drop duplication.
- If a rare gathering-table item drops, such as seeds or gems, the amulet has a
  chance to double that rare item.
- Chance: `10%` per tier.
- Old extra-resource, gem-chance, and herb-quality effects are retired.

### Chaos

Ring/necklace:

- Theme: recoil.
- Ring: `8%` recoil chance per tier.
- Necklace: `8%` recoil chance per tier.
- Ring and necklace chance stacks.
- Reflected damage improves when both are worn:
  - one piece: reflect `damage / 10`
  - ring plus necklace: reflect `damage / 5`
- Example: tier 5 ring plus tier 5 necklace gives `80%` recoil chance and
  reflects `damage / 5`.

Amulet:

- `15%` chance per tier that damage dealt also deals half that damage,
  rounded up, to a random enemy within `4` tiles.
- The second-hit target pool can include the player's current target.

### Nature

Ring/necklace:

- Food healing bonus.
- Ring: `+10%` food healing per tier.
- Necklace: `+10%` food healing per tier.
- Ring and necklace stack; tier 5 ring plus tier 5 necklace doubles food
  healing.
- The old Nature ring smelting protection is retired.

Amulet:

- Faster poison decay.
- Poison decay increases by `1` per amulet tier.
- Sapphire Nature amulet: poison drains at `4` power per tick.
- Dragonstone Nature amulet: poison drains at `9` power per tick.

### Law

Ring/necklace:

- Theme: item teleportation to bank.
- Rings and necklaces have charges and break when charges are depleted.
- Charge model by tier:
  - tier 1: `50`
  - tier 2: `150`
  - tier 3: `300`
  - tier 4: `500`
  - tier 5: `750`
- Ring: transports skilling drops to the bank.
- Necklace: transports monster loot to the bank.
- Each transported item consumes one charge.
- If a gathering action produces 5 ore, it consumes 5 charges.
- Transport all non-stackable items to the bank.
- Stackable items are not transported by this effect because they do not
  clog inventory in the same way.
- Examples:
  - Woodcutting: logs transport, seeds do not.
  - Mining: ores and gems transport.

Amulet:

- Keep current direct guild teleport utility.
- Current behavior: 3 charges, recharged at the Law altar, destination pair
  determined by gem tier.

### Death

Ring/necklace:

- Death ring:
  - charged alternate damage hit.
  - NPC kills add `10` charge to the equipped Death ring, up to the tier cap.
  - each full `10` charge adds a constant `+1` yellow damage hit against NPCs.
  - charge decays by `10` per minute while the player is out of combat.
  - tier 1: `20` charge cap, up to `+2` yellow damage.
  - tier 2: `30` charge cap, up to `+3` yellow damage.
  - tier 3: `40` charge cap, up to `+4` yellow damage.
  - tier 4: `60` charge cap, up to `+6` yellow damage.
  - tier 5: `100` charge cap, up to `+10` yellow damage.
  - Death ring charge is stored on the player cache by ring item ID, not as
    separate item variants.
- Death necklace:
  - boosts NPC drops in the guaranteed `100%` drop-rate category, such as
    bones, demon ash, hides, and invariable material drops.
  - each eligible item rolls independently.
  - "Double" means `+1` item, not duplicating the full stack.
  - tier 1: `25%` chance to add `+1`.
  - tier 2: `40%` chance to add `+1`.
  - tier 3: `60%` chance to add `+1`.
  - tier 4: `90%` chance to add `+1`.
  - tier 5: guaranteed `+1`, plus `10%` chance to add another `+1`.
  - when the necklace grants at least one bonus item, the player sees
    `Your death necklace shines brightly`.

Amulet:

- Burst.
- Killing an NPC while a Death amulet is equipped adds death charge equal to
  `10%` of that NPC's combat level.
- At `100` death charge, the amulet spends `100` charge and bursts around the
  player, hitting NPCs within `2` tiles.
- Death charge is stored on the player cache by amulet item ID, not as separate
  item variants. This keeps bank storage clean while preserving charge through
  logout and banking.
- Tier 1: `1-3` damage.
- Tier 2: `3-6` damage.
- Tier 3: `6-9` damage.
- Tier 4: `9-14` damage.
- Tier 5: `10-20` damage.

### Blood

Ring:

- Max Hits bonus.
- Tier 1: `+2` max Hits.
- Tier 2: `+4` max Hits.
- Tier 3: `+6` max Hits.
- Tier 4: `+10` max Hits.
- Tier 5: `+20` max Hits.

Necklace:

- Leach.
- Leach heals the wearer for a percentage of poison tick damage dealt by poison
  they own.
- Tier 1: `10%` poison Leach.
- Tier 2: `20%` poison Leach.
- Tier 3: `30%` poison Leach.
- Tier 4: `50%` poison Leach.
- Tier 5: `100%` poison Leach.

Amulet:

- Lifesteal.
- `5%` lifesteal per tier.

### Soul

Ring:

- Life-saving effect.
- When the wearer falls to `10%` Hits or lower, the ring teleports them to the
  respawn location if teleport rules allow it.
- Tier 1: `10%` chance not to break after saving the wearer.
- Tier 2: `20%` chance not to break after saving the wearer.
- Tier 3: `30%` chance not to break after saving the wearer.
- Tier 4: `50%` chance not to break after saving the wearer.
- Tier 5: `90%` chance not to break after saving the wearer.

Necklace:

- Item preservation on death.
- Tier 1: keep `+1` extra item.
- Tier 2: keep `+2` extra items.
- Tier 3: keep `+3` extra items.
- Tier 4: keep `+5` extra items.
- Tier 5: keep `+8` extra items.

Amulet:

- Healing Renewal.
- Killing an NPC while a Soul amulet is equipped adds soul charge equal to `10%`
  of that NPC's combat level.
- At `200` soul charge, the amulet spends `200` charge and bursts around the
  player, healing the wearer and nearby players within `2` tiles.
- Soul charge is stored on the player cache by amulet item ID, not as separate
  item variants. This keeps bank storage clean while preserving charge through
  logout and banking.
- Tier 1: `1-2` Hits.
- Tier 2: `1-3` Hits.
- Tier 3: `2-4` Hits.
- Tier 4: `3-6` Hits.
- Tier 5: `5-10` Hits.

### Life

Ring/necklace:

- Summon endurance.
- Life necklace: combat summons gain `+10%` health per tier, with a minimum
  bonus of `1`.
- Life ring: support summons last `+20%` longer per tier.
- Utility summons are unaffected.
- Ring and necklace stack.
- Tier 5 necklace gives combat summons `+50%` health.
- Tier 5 ring doubles support summon duration.

Amulet:

- Summon max damage increased by `+1` per tier.

### Unresolved Effects

- None. Every current altar jewelry slot has an implemented effect.
