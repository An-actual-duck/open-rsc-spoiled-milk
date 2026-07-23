# How To Acquire Dragon Armor

Status: audit
Owner: An-actual-duck
Branch: no active branch; assign the next checklist item to a fresh topic branch in a neutral AI slot

## Purpose

Record the current and legacy acquisition routes for dragon armor so future
work can preserve meaningful quest and progression content without
reintroducing retired skirt items.

Skirts should stay retired. The important legacy thread is the work that went
into Combat Odyssey, not the old skirt reward itself.

## Key Findings

- The remembered Legends Guild quest line appears to be `Combat Odyssey`.
  There is no current `Fezmog` reference in the repo. The quest uses Sir
  Radimus Erkle, Siegfried Erkle, and Biggum Flodrot.
- Combat Odyssey originally awarded a choice between `Dragon Plate Mail Legs`
  and `Dragon Plated Skirt`. That was added in commit `3a6787542 The Odyssey`.
- Commit `837de7256 Balance dragon gear and summoning fixes` removed the skirt
  choice and changed the final reward to `Dragon Plate Mail Legs`.
- Current high-end metal dragon armor is obtained by completing Dwarf Youth
  Rescue, repairing the lava forge, producing dragon bars, and smithing dragon
  gear at a normal anvil.
- Wayne no longer crafts dragon armor directly.
- Combat Odyssey is preserved in code for legacy progress, but new Radimus
  starts are hidden until Monster Slayer replaces that progression thread.
- Current dragon shield acquisition supports normal dragon-bar smithing. The
  classic shield-half path remains available as a compatibility route: right
  half plus left half plus hammer on an anvil.
- Item `1430` is legacy-sensitive. Base custom item defs still call it
  `Dragon Plated Skirt`, but MyWorld and the client override it to
  `Dragon Scale Mail Legs`. Treat `1430` as scale mail legs in current
  Spoiled Milk design.
- `Dragon Plate Mail Top` and `Dragon Scale Mail Top` exist, but no normal
  player-facing acquisition route was found for either. They appear to be
  compatibility, female-appearance, or hidden support items.
- Planning update: `dragon-gear-crafting-plan.md` supersedes these
  current-route findings as future direction. The intended route is Dwarf
  Youth Rescue, one-time lava forge repair, dragon bar production at the lava
  forge, and normal anvil Smithing for dragon equipment.
- Planning update: dragon metal chains, chipped dragon scales, dragon scale
  mail variants, and Dragon medium Helmet should be hidden or
  compatibility-only in the first normal-smithing pass.

## Current Acquisition Matrix

| Requested armor | Current item and ID | Current route found | Legacy route found | Notes |
| --- | --- | --- | --- | --- |
| Platemail body | `Dragon Plate Mail Body` `1427` | Smith from dragon bars at a normal anvil after repairing the lava forge. | No Combat Odyssey route found. | Female characters use the female plate-top appearance automatically from the body item. |
| Platemail legs | `Dragon Plate Mail Legs` `1429` | Smith from dragon bars at a normal anvil after repairing the lava forge. | Original Combat Odyssey final choice could award this or the retired skirt. | Combat Odyssey new starts are hidden. |
| Skirt | Retired. Legacy `Dragon Plated Skirt` used item `1430`. | No current skirt route should exist. | Original Combat Odyssey final choice could award `Dragon Plated Skirt`. | Do not reintroduce. Current `1430` is `Dragon Scale Mail Legs`. |
| Top | `Dragon Plate Mail Top` `1428` | No normal acquisition route found. | No current legacy reward route found beyond compatibility/history. | MyWorld/client now label this as `Dragon plate mail body`; it should probably remain a compatibility/female visual support item unless a future plan says otherwise. |
| Scalemail body | `Dragon Scale Mail Body` `1368` | No active route. | Former Wayne route used dragon metal chains, chipped dragon scales, and coins. | Hidden until a better non-metal-line route exists. |
| Scalemail legs | `Dragon Scale Mail Legs` `1430` | No active route. | Same ID was formerly the retired `Dragon Plated Skirt`; former Wayne route used dragon metal chains, chipped dragon scales, and coins. | Hidden until a better non-metal-line route exists. |
| Scalemail top | `Dragon Scale Mail Top` `1537` | No normal acquisition route found. | No current legacy reward route found. | Exists in equipment logic and dragon-breath mitigation. Future plan keeps it hidden with the other scale mail variants. |
| Full helm | `Large Dragon Helmet` / MyWorld `Dragon Helmet` `1425` | Smith from dragon bars at a normal anvil; also remains a hidden unique drop from Black Demons. | No quest route found. | MyWorld renames this to `Dragon Helmet`, so this appears to be the current "full helm" slot. |
| Medium helm | `Dragon medium Helmet` `795` | No intended active route after the dragon crafting cleanup. Former Black Demon, Present, and Halloween Cracker reward slots should use `Large Dragon Helmet` / `Dragon Helmet` instead. | Authentic rare/event lineage exists. | Still exists as compatibility-only unless a future helm-variant plan reintroduces it. |
| Helm | No separate third dragon helm found. | `Dragon Helmet` appears to be the MyWorld name for item `1425`. | N/A | Treat `helm` as an alias question until a distinct item is added. |
| Shield | No plain `Dragon Shield` item found. | Closest routes are `Anti dragon breath Shield` from the Duke/Dragon Slayer flow, and `Dragon Square Shield` via shield halves. | Anti-dragon shield is authentic Dragon Slayer utility. | Keep anti-dragon shield separate from dragon armor tiering. |
| Kite shield | Constant `DRAGON_KITE_SHIELD` `1426`, displayed as `Dragon Paladin Shield`. | Smith from dragon bars at a normal anvil; shield-half repair route remains. | No separate drop route found. | Code calls it kite shield; player-facing name is paladin shield. |
| Square shield | `Dragon Square Shield` `1278` | Smith from dragon bars at a normal anvil; shield-half repair route remains. | Classic shield-half route. | Right half is sold by Siegfried Erkle. Left half is on the mega rare drop table. Direct hidden KBD/Black Dragon square shield drops only exist if OpenPK points are enabled, which MyWorld disables. |
| Paladin shield | `Dragon Paladin Shield` `1426` | Smith from dragon bars at a normal anvil; shield-half repair route remains. | No separate route found. | This is the current player-facing name for `DRAGON_KITE_SHIELD`. |
| Gloves / gauntlets | Dragonhide glove family: baby `1886`, blue `1921`, green/earth `1926`, red `1931`, black `1941`, elder green `1951`. | Crafted from matching tanned dragon leather with thread. Also available as fishing special rewards. | No metal dragon gauntlet route found. | No item named dragon gauntlets was found. |
| Boots / greaves | Dragonhide boot family: baby `1887`, blue `1922`, green/earth `1927`, red `1932`, black `1942`, elder green `1952`. | Crafted from matching tanned dragon leather with thread. Also available as fishing special rewards. | No metal dragon greaves route found. | Metal greaves exist for other metal tiers, but no dragon greaves were found. |

## Material Routes

### Repaired Lava Forge And Dragon Bars

Source:

- `server/plugins/com/openrsc/server/plugins/authentic/npcs/falador/WaynesChains.java`
- `server/plugins/com/openrsc/server/plugins/custom/minigames/DwarfRescue.java`
- `server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvAction.java`

Unlock:

- Player must have `miniquest_dwarf_youth_rescue == 2`.

Wayne's old armor route is retired. He remains a throwing weapon shop NPC.

Dragon material setup:

- `Black dragon scale` uses the former KBD scale item ID.
- Black Dragons drop `1 Black dragon scale`.
- King Black Dragon drops `2 Black dragon scale`.
- Repairing the lava forge requires Dwarf Youth Rescue completion,
  `100 Black dragon scale`, and `1,000,000 coins`.
- `Dragon bar` requires `1 Dragon metal scrap` and `6 Dragon sulfur` at the
  repaired lava forge.
- `Purified Rune Bar` requires `1 Runite bar` and `14 Dragon sulfur` at the
  repaired lava forge.
- Dragon metal chains and chipped dragon scales are compatibility-only unless a
  future route gives them a new purpose.

### Dragon Shield Halves

Source:

- `server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smithing.java`
- `server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/shop/SiegfriedErkel.java`
- `server/src/com/openrsc/server/constants/NpcDrops.java`

Route:

- Right half: sold by Siegfried Erkle in the Legends Guild shop. Also appears
  in the Present ultra-rare table.
- Left half: mega rare drop table. Also appears in the Present ultra-rare
  table.
- Smithing: use the halves on an anvil with a hammer at 60 Smithing.
- Output choice: `Dragon Square Shield` or `Dragon Paladin Shield`.

Current caveat:

- `Dragon Square Shield` has a Legends Quest equip gate unless quest item
  restrictions are disabled.
- Hidden direct square shield drops from Black Dragons and KBD are behind
  `want_openpk_points`; MyWorld and hosted MyWorld set this false.

### Dragonhide Gloves And Boots

Source:

- `server/src/com/openrsc/server/constants/NpcDrops.java`
- `server/plugins/com/openrsc/server/plugins/custom/skills/crafting/TanningRack.java`
- `server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java`
- `server/plugins/com/openrsc/server/plugins/authentic/skills/fishing/Fishing.java`

Route:

- Matching dragons drop hides as guaranteed drops.
- Tanning rack converts hides into matching leather.
- Crafting uses the hide armor production path with thread.
- Gloves and boots cost `2` matching leather each.
- Crafting levels are tier-based:
  - baby dragon gloves/boots: 23 Crafting
  - blue dragon gloves/boots: 47 Crafting
  - green/earth dragon gloves/boots: 47 Crafting
  - red dragon gloves/boots: 55 Crafting
  - black dragon gloves/boots: 63 Crafting
  - elder green dragon gloves/boots: 81 Crafting
- Fishing special rewards can also award matching dragonhide gloves and boots.

## Combat Odyssey Legacy

Source:

- `server/plugins/com/openrsc/server/plugins/custom/minigames/CombatOdyssey.java`
- `server/conf/server/defs/extras/CombatOdyssey.json`
- `server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestSirRadimusErkle.java`
- `server/plugins/com/openrsc/server/plugins/authentic/defaults/Ladders.java`

Current start flow:

1. Player talks to Sir Radimus Erkle in the Legends Guild.
2. Radimus offers "a combat odyssey" and sends the player upstairs to
   Siegfried.
3. The upstairs Legends Guild ladder flow gives the player `Biggum Flodrot`.
4. Biggum tells the player to start with the goblin generals in north
   Fallington.
5. Combat Odyssey uses Biggum as the tracking item and progresses through
   tiered kill assignments.

Current final reward:

- On tier 13 completion, Radimus awards `Dragon Plate Mail Legs`.
- This is now the restored dragon plate legs acquisition route.
- Biggum receives completion dialogue and prestige handling.
- The quest can be repeated after completion.

Legacy final reward:

- Commit `3a6787542 The Odyssey` awarded a final choice:
  - `Dragon Plate Mail Legs`
  - `Dragon Plated Skirt`
- Commit `837de7256 Balance dragon gear and summoning fixes` removed the
  choice and made the reward always `Dragon Plate Mail Legs`.

Quest scale:

- This is not a small leftover thread. It is a large combat progression system
  with many tiered kill tasks, multiple NPC masters, custom Biggum dialogue,
  repeat/prestige behavior, and a final KBD kill.
- The work should be preserved, but the final reward should be revisited now
  that skirts are retired and dragon gear progression is moving toward the
  repaired lava forge and normal anvil Smithing route.

## Follow-Up Design Questions

- Should Combat Odyssey's kill requirements be reduced or streamlined so the
  plate legs route is less grindy while still feeling legendary?
- Should Combat Odyssey eventually award a dragon armor voucher/claim choice
  that excludes skirts, or should plate legs remain its fixed reward?
- Should `Dragon Plate Mail Top` and `Dragon Scale Mail Top` stay hidden
  support items, or should they be removed from any player-facing language?
- Should `Dragon Plated Skirt` be fully renamed in base custom item defs to
  prevent future confusion, since MyWorld already treats item `1430` as
  `Dragon Scale Mail Legs`?
- Should Dragon medium Helmet stay compatibility-only permanently, be fully
  retired, or return later as a deliberate helm variant?
- Should Dragon Helmet move into the normal dragon Smithing route as the sole
  first-pass metal-line helm?
- Should the shield-half path be documented in a guide, since it now produces
  both square and paladin shield outputs?
- Should dragonhide gloves and boots be considered part of "dragon armor" in
  player-facing guides, or kept separate as ranged/leather armor?

## Recommended Next Step

Use this audit alongside `dragon-gear-crafting-plan.md` and
`monster-slayer-guild-plan.md`. The safest direction is to keep Combat Odyssey
content as monster-slayer progression, keep skirts retired, and move dragon
armor acquisition into the repaired lava forge plus normal anvil Smithing
route.
