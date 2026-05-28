# Herblaw Ingredient Audit

This document records the current Herblaw ingredient surface after retiring the
old runecraft/enchanting potion line and before the main MyWorld potion rework.

## Goal

Keep herbs and secondaries meaningfully used while replacing legacy skill-boost
potions with the new utility/combat/status potion line.

## Current standard herb secondaries

From [ItemHerbSecond.xml](/home/justin/Core-Framework/server/conf/server/defs/extras/ItemHerbSecond.xml):

- `Guam` + `Eye of newt` -> attack potion
- `Marrentill` + `Ground unicorn horn` -> cure poison potion
- `Tarromin` + `Limpwurt root` -> strength potion
- `Guam` + `Jangerberries` -> unfinished ogre potion
- `Harralander` + `Red spiders' eggs` -> stat restoration potion
- `Ranarr` + `White berries` -> defense potion
- `Ranarr` + `Snape grass` -> restore prayer potion
- `Irit` + `Eye of newt` -> super attack potion
- `Irit` + `Ground unicorn horn` -> poison antidote
- `Avantoe` + `Snape grass` -> fishing potion
- `Kwuarm` + `Limpwurt root` -> super strength potion
- `Kwuarm` + `Ground blue dragon scale` -> weapon poison
- `Cadantine` + `White berries` -> super defense potion
- `Dwarf weed` + `Wine of zamorak` -> ranging potion
- `Torstol` + `Jangerberries` -> potion of zamorak

## Current custom potion secondaries

From [Herblaw.java](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/herblaw/Herblaw.java):

- Retired now:
  - `Marrentill` + `Fish oil` -> runecraft/enchanting potion
  - `Avantoe` + `Fish oil` -> super runecraft/enchanting potion
- Still present but planned for removal/replacement with the main potion
  overhaul:
  - `Dwarf weed` + `Wine of saradomin` -> magic potion
  - `Torstol` + `Sliced dragonfruit` -> potion of saradomin
  - `Magic potion` + `Half coconut` -> super magic potion
  - `Ranging potion` + `Half coconut` -> super ranging potion

## Quest/special Herblaw ingredients

These should remain outside the standard potion rebalance:

- `Ground bat bones`
- `Blamish snail slime`
- `Snake weed`
- `Ardrigal`
- `Sito foil`
- `Volencia moss`
- `Rogues purse`
- `Jangerberries` when used for `Unfinished potion` / ogre-related paths

## Secondary status by current ecosystem

### Already useful outside standard potions

- `Eye of newt`
  - Witch's Potion quest
  - sold in multiple shops
- `Limpwurt root`
  - harvesting source
  - quests and certificates
  - apothecary strength potion path
- `Snape grass`
  - harvesting source
  - chocolaty milk interaction
- `Jangerberries`
  - quest/special Herblaw path
  - edible item
- `Wine of zamorak`
  - general item identity beyond Herblaw

### Mostly potion-defined right now

- `Ground unicorn horn`
- `Red spiders' eggs`
- `White berries`
- `Ground blue dragon scale`

### Left behind by retired or planned-retire potion lines

- `Fish oil`
- `Wine of saradomin`
- `Sliced dragonfruit`
- `Half coconut`

## Recommended MyWorld remap

### Base potions

- `Guam` + `Ground unicorn horn` -> `Cure Poison`
- `Marrentill` + `Eye of newt` -> `Potion of Insight`
- `Tarromin` + `Snape grass` -> `Potion of Regeneration`
- `Harralander` + `Ground blue dragon scale` -> `Weapon Poison`
- `Ranarr` + `White berries` -> `Potion of Speed`
- `Irit` + `Red spiders' eggs` -> `Potion of Magic Resistance`
- `Avantoe` + `Limpwurt root` -> `Potion of Melee Resistance`
- `Kwuarm` + `Jangerberries` -> `Potion of Ranged Resistance`
- `Cadantine` + `Wine of zamorak` -> `Potion of Luck`
- `Dwarf weed` + `Eye of newt` or `Snape grass` -> `Potion of Notation`

This keeps the standard herb ladder full and preserves obvious poison /
combat-oriented identity for several secondaries.

## Recommended super upgrade ingredients

Use `Torstol` as the herb driver for all five super upgrades and attach the
secondaries that would otherwise become stranded:

- `Potion of Insight` + `Torstol unfinished` + `Fish oil` -> `Super Potion of Insight`
- `Potion of Regeneration` + `Torstol unfinished` + `Half coconut` -> `Super Potion of Regeneration`
- `Potion of Magic Resistance` + `Torstol unfinished` + `Wine of saradomin` -> `Super Potion of Magic Resistance`
- `Potion of Melee Resistance` + `Torstol unfinished` + `Sliced dragonfruit` -> `Super Potion of Melee Resistance`
- `Potion of Ranged Resistance` + `Torstol unfinished` + `Ground blue dragon scale` or `Half coconut` -> `Super Potion of Ranged Resistance`

This ensures the retired runecraft/harvesting potion ingredients do not become
dead content after the potion rewrite.

## Immediate implementation priority

1. Replace standard Herblaw recipes with the new base line.
2. Replace the remaining custom magic/saradomin/ranging potion branch with the
   new super-upgrade model.
3. Remove the old special drink handlers once no live potion item still depends
   on them.
4. Audit NPC shops, drops, certificates, and quest rewards to swap any removed
   potion outputs to their new equivalents or to non-potion rewards where
   appropriate.
