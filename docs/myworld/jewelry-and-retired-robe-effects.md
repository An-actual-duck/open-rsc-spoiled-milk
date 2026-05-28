# Jewelry and Retired Robe Effects

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
- Death: increased defenses while missing health.
- Blood: lifesteal from spell damage.
- Soul: rechargeable damage shield.
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
  gathering XP, body amulet discipline XP, cosmic ring wealth rolls, cosmic necklace standard loot
  rolls, cosmic amulet rare gathering duplication, chaos recoil, chaos amulet
  splash, nature food healing, nature amulet poison decay, law ring skilling
  banking, law necklace monster-loot banking, law amulet teleports, death
  low-health scaling and death burst, blood max Hits and lifesteal, soul item
  saving/life saving, and life summon bonuses.
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
- Chaos: Ring/Necklace of Recoil; Amulet of Echoes.
- Nature: Ring/Necklace of Nourishment; Amulet of Cleansing.
- Law: Ring/Necklace of Banking; Amulet of Teleportation.
- Death: Ring/Necklace of Desperation; Amulet of Ruin.
- Blood: Ring/Necklace of Vitality; Amulet of Siphoning.
- Soul: Ring/Necklace of Preservation; Amulet of Lifesaving.
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
- Ring: `5%` per tier chance to roll an extra full monster drop. Rare tables
  are allowed on this extra roll.
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
  - grants weapon power as the player's health drops below maximum.
  - tier 1: `+1` weapon power for every `25%` below max health.
  - tier 2: `+1` weapon power for every `20%` below max health.
  - tier 3: `+1` weapon power for every `15%` below max health.
  - tier 4: `+1` weapon power for every `10%` below max health.
  - tier 5: `+2` weapon power for every `10%` below max health.
- Death necklace:
  - same scaling concept as the ring, but grants defenses instead of weapon
    power.

Amulet:

- Whenever an enemy dies, nearby enemies take damage based on their own maximum
  health.
- Tier 1: enemies within `1` tile take `5%` max health damage, rounded up.
- Tier 2: enemies within `1` tile take `10%` max health damage, rounded up.
- Tier 3: enemies within `2` tiles take `10%` max health damage, rounded up.
- Tier 4: enemies within `2` tiles take `15%` max health damage, rounded up.
- Tier 5: enemies within `3` tiles take `15%` max health damage, rounded up.

### Blood

Ring/necklace:

- Max Hits bonus.
- Ring: `+2` max Hits per tier.
- Necklace: `+2` max Hits per tier.
- Ring and necklace stack.

Amulet:

- Lifesteal.
- `5%` lifesteal per tier.

### Soul

Ring/necklace:

- Save-from-death theme.
- Ring: keep `+1` additional item on death per tier.
- Necklace: keep `+1` additional item on death per tier.
- Ring and necklace stack unless later capped.

Amulet:

- Life-saving effect.
- `10%` chance per tier not to break when the life-saving effect activates.

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
