# Equipment Drop Redistribution Audit

This document is the working audit for NPC equipment drops in `MyWorld`.

It answers three questions:

1. Which live NPC drop tables currently emit equipment.
2. What families those drops fall into.
3. Which NPC level bands should map to which item tiers when redistribution is
   applied.

Use this before editing [NpcDrops.java](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java).

## Scope

This audit is focused on live NPC equipment drops authored directly in
[NpcDrops.java](/home/justin/Core-Framework/server/src/com/openrsc/server/constants/NpcDrops.java).

It includes:

- direct NPC equipment drops
- special shared equipment tables such as rune, ultra-rare, and arrow tables
- standard craftable metal, ranged, magic, and leather-adjacent equipment

It does not treat these as standard redistribution targets:

- dragon exception items such as `Dragon medium helmet` and `Dragon square shield`
- quest compatibility items
- appearance IDs or old item IDs that remain for compatibility
- hide guaranteed drops added by `applyMyWorldMaterialDrops()`

## Current Equipment-Bearing NPC Tables

The current live drop data is still heavily melee-metal weighted.

### Metal / weapon equipment drops

These tables currently emit metal weapons or armor:

- Goblin level 7, 13, and 19
- Men, farmers, warriors, thieves, rogues
- Mugger
- Skeleton 21, 25, 31
- Zombie 19, 24, 32, and Entrana zombie
- Giant
- Guards
- Black knights
- Hobgoblins
- Barbarians
- Dwarves
- White knight
- Moss giant
- Ice giant
- Monk of Zamorak
- Ice warrior / Ice queen
- Greater demon
- Chaos dwarf
- Dark warrior
- Red dragon
- Blue dragon
- Bandits
- Donny / Black Heather / Speedy Keith
- Thug
- Chaos druid
- Black demon
- Black dragon
- Animated axe
- Otherworldly being
- Paladin
- Fire giant
- Tribesman
- King Black Dragon
- Jogre
- Chaos druid warrior
- Salarin the Twisted
- Earth warrior
- Shadow warrior

### Ranged equipment drops

These tables currently emit bows, arrows, bolts, crossbows, or thrown weapons:

- Goblin level 7, 13, and 19
- Men / farmers / thieves / rogues
- Skeleton 21 and 25
- Zombie 19, 24, and Entrana zombie
- Guards
- Black knights
- Hobgoblins
- Barbarians
- White knight
- Dark warrior
- Druid
- Pirate
- Imp
- Bandits
- Tribesman
- Shadow warrior
- King Black Dragon

### Magic equipment drops

These tables currently emit staves or robes:

- Dark wizard 13
- Dark wizard 25
- Barbarian
- Goblin level 7
- Pirate
- Druid
- Necromancer
- Earth warrior
- Imp

### Leather / hide equipment drops

These are very limited in the current direct equipment tables:

- Skeleton level 21: `Leather gloves`
- Ice warrior / Ice queen: `Ice gloves`

The real hide/leather source path is currently the guaranteed material-drop
system rather than finished armor drops.

## Current Distribution Problems

The audit surfaced several recurring issues in the live data.

### Retired-family leakage

Even after cleanup work, many tables still author retired standard families
that are only centrally scrubbed later:

- `Chain mail`
- standard `Medium helms`
- standard `Square shields`
- standard `Metal skirts`
- standard `Black` and `White` gear

This is functional because cleanup removes many of them, but it is not a good
authoring state for redistribution work.

### Old rune-era assumptions

Several shared tables still reflect the old assumption that `Rune` was the top
normal tier instead of tier 10:

- `Rune Drop Table`
- `Mega Rare Drop Table`
- `Ultra Rare Drop Table`
- older dragon tables that jump from mithril/adamantite straight to rune

### Weak ranged and magic distribution

Ranged and magic drops are sparse and inconsistent:

- ranged drops are mostly low-end arrows, basic bows, and crossbows
- magic drops are mostly `Staff`, plus a few elemental staves
- robe distribution is almost non-existent outside druid/dark wizard style
  sources

### Humanoid tables are noisy

Many humanoid tables mix unrelated legacy drops:

- low-level humanoids drop helmets, chain, shields, and random ranged gear
- demons and dragons often mix late-tier items with obsolete mid-tier families
- specialty factions such as white knights and dark warriors are still serving
  as legacy content buckets rather than deliberate themed distributions

## Recommended Redistribution Categories

For authored redistribution, treat drops as these separate families:

### Standard metal melee

- helmets: large only for standard progression
- plate bodies
- plate legs
- kite shields
- melee weapons
- axes and battleaxes
- maces
- spears

Do not redistribute standard:

- chain
- medium helms
- square shields
- plated skirts

### Standard ranged

- bows
- shortbows
- crossbows
- arrows
- bolts
- darts
- throwing knives
- spears where they fit the melee/ranged hybrid role

### Standard magic

- base `Staff`
- basic enchanted elemental wood staves where intentionally placed
- robe top
- robe bottom
- hat

### Finished leather equipment

Keep finished leather armor drops limited and deliberate. The main leather loop
should remain material-driven through hides and crafting, not broad finished
armor drops from generic mobs.

### Specialty and exception families

These should be handled deliberately after the standard pass, not mixed into the
main redistribution bands:

- `White` equipment
- `Black` equipment
- druid robes
- god staves
- dragon exception items
- shadow-warrior style specialty drops

## Recommended NPC Level Bands

These bands are intended for authored redistribution of standard equipment
families. They are based on the current MyWorld 10-tier progression plus
explicit tier-11 dragon exceptions.

| NPC combat level | Standard drop ceiling | Intended feel |
| --- | --- | --- |
| 1-15 | tier 1-2 | tutorial, goblin, weak human, weak undead |
| 16-30 | tier 2-3 | low combat loop, bandits, low skeletons/zombies |
| 31-45 | tier 3-4 | early-mid loop, hobgoblins, guards, dwarves |
| 46-60 | tier 4-5 | mid loop, stronger humanoids and lesser demons |
| 61-80 | tier 5-6 | strong mid loop, giants, knights, chaos dwarves |
| 81-100 | tier 6-7 | upper-mid loop, paladins, strong faction mobs |
| 101-130 | tier 7-8 | entry high loop, moss/ice giants, white-knight equivalents if still used |
| 131-170 | tier 8-9 | high loop, ice warriors, strong demons |
| 171-220 | tier 9-10 | endgame standard loop, greater demons, dragons |
| 221+ | tier 10 + tier-11 exceptions | bosses and explicit dragon/special drops |

## Redistribution Rules

Apply the bands with these rules:

- Standard enemies should mostly drop at or below the band ceiling, not always
  at the ceiling.
- One tier below the ceiling should be common; ceiling-tier items should be the
  aspirational drops.
- Low-level humanoids should stop dropping armor pieces that imply a fully
  armored progression loop too early.
- Ranged and magic equipment should be distributed intentionally by enemy theme,
  not as random filler.
- Leather-family progression should remain material-first.
- `Rune` should not appear in standard distribution until the tier-10 band.
- `Dragon` should remain explicit tier-11 exception content.

## Priority Redistribution Passes

These are the most efficient passes to do next.

### Pass 1: low-level humanoids and undead

- goblins
- men / thieves / rogues
- skeletons
- zombies
- guards

Goal:

- remove retired families from authorship
- normalize them to tiers 1-3
- make their drops cleaner and easier to read

### Pass 2: mid-level humanoids and faction mobs

- black knights
- hobgoblins
- dwarves
- barbarians
- bandits
- pirates
- chaos dwarves
- paladins

Goal:

- normalize them into tiers 3-6
- separate standard drops from specialty faction drops

### Pass 3: giants, demons, dragons, and high-level specials

- moss giant
- ice giant
- fire giant
- lesser demon
- greater demon
- black demon
- red dragon
- blue dragon
- black dragon
- king black dragon
- shadow warrior

Goal:

- move endgame standard drops onto the proper tier-7 through tier-10 ladder
- reserve dragon exception items for explicit late-game/boss placement

## Immediate Outliers

These tables should be treated as obvious reauthor candidates:

- `Lesser Demon (22)`
  - currently mixes mithril chain, mithril square, medium rune, and steel gear
- `White Knight (102)`
  - currently acts as a specialty white-gear bucket rather than a standard
    progression table
- `Moss Giant (104, 594)`
  - currently mixes black and retired shield/helmet families
- `Ice Giant (135)`
  - still contains retired skirt and square-shield era baggage
- `Greater Demon (184)`
  - currently jumps to rune/adamantite without matching the new ladder cleanly
- `Black Demon (290)`
  - still carries retired rune chain and medium-rune-helmet assumptions
- `King Black Dragon (477)`
  - should remain a top-end source, but needs authored tier-10 and tier-11
    placement instead of legacy addy/rune/dragon leftovers

## Recommendation

Use this doc as the authored target, then do redistribution in this order:

1. low-level humanoids and undead
2. mid-level humanoids and faction enemies
3. high-level giants, demons, dragons, and bosses

That keeps the pass readable and makes it easier to test progression after each
slice.
