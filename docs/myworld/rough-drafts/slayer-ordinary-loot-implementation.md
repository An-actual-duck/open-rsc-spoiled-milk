# Slayer tower ordinary loot

Updated: 2026-09-21. Implements the owner's specialty-loot and equipment-drop
request for the eight new monsters. No sprite work, placement, new items,
existing-family retirement, deployment or server restart.

## Roll structure

Each kill retains its existing guaranteed hide/feathers/residue and bones/ash.
It also gets one ordinary roll with a fixed 128-weight table:

- Specialty supplies: 64/128 (50%).
- Coins: 32/128 (25%).
- Other stackable supplies: 28/128 (21.875%).
- Existing shared rare-drop table: 4/128 (3.125% access, not a guaranteed reward).

Coins are ordinary outcomes, not an additional guaranteed pile. Amounts follow
the displayed combat level, with three outcomes weighted 20, 10 and 2. Their
weighted average increases with combat level, using existing ordinary enemy
coin piles as the benchmark. No gear or unique part is added to this 128-weight
budget; their independent rolls keep exact base odds.

Existing potion luck, ring-of-wealth rare retry, cosmic-necklace standard extra
rolls, contribution scaling, world item restrictions, ownership, logging and
loot collection rules remain in force. Rates below are unmodified full-credit
base rates, not guarantees. Existing component odds are unchanged.

Naturally stackable items (including this game's uncut gems) are unnoted.
Herbs, cooked food, ore, coal and bars are noted so supply drops stack. Rare
equipment is one unnoted item. Noted food must be unnoted before eating.

## Ordinary tables

Weights below are out of 128. Every table additionally has the shared rare
access at weight 4. “Noted” denotes a stackable note, not a new item definition.

| NPC | Combat level | Specialty | Coin quantities (weights 20 / 10 / 2) |
| --- | ---: | --- | --- |
| Giant frog | 20 | Herbs | 20 / 60 / 160 |
| Cockatrice | 35 | Gems | 35 / 105 / 280 |
| Banshee | 50 | Runes | 50 / 150 / 400 |
| Naga | 60 | Food | 60 / 180 / 480 |
| Terror dog | 75 | Ammo | 75 / 225 / 600 |
| Bloodveld | 85 | Higher-tier herbs | 85 / 255 / 680 |
| Dark beast | 105 | Higher-tier runes | 105 / 315 / 840 |
| Abyssal demon | 125 | Higher-tier ammo | 125 / 375 / 1000 |

### Giant frog

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Unidentified guam leaf | 1 | 20 | Noted | Specialty |
| Unidentified marrentill | 1 | 16 | Noted | Specialty |
| Unidentified tarromin | 1 | 12 | Noted | Specialty |
| Unidentified harralander | 1 | 8 | Noted | Specialty |
| Unidentified ranarr weed | 1 | 6 | Noted | Specialty |
| Unidentified irit leaf | 1 | 2 | Noted | Specialty |
| Water rune | 10 | 8 | Natural stack | Other supply |
| Iron arrows | 10 | 8 | Natural stack | Other supply |
| Feather | 15 | 8 | Natural stack | Other supply |
| Copper ore | 3 | 4 | Noted | Other supply |

### Cockatrice

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Uncut sapphire | 1 | 28 | Natural stack | Specialty |
| Uncut emerald | 1 | 20 | Natural stack | Specialty |
| Uncut ruby | 1 | 12 | Natural stack | Specialty |
| Uncut diamond | 1 | 4 | Natural stack | Specialty |
| Air rune | 20 | 8 | Natural stack | Other supply |
| Iron arrows | 15 | 8 | Natural stack | Other supply |
| Coal | 3 | 8 | Noted | Other supply |
| Silver bar | 1 | 4 | Noted | Other supply |

### Banshee

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Air rune | 50 | 12 | Natural stack | Specialty |
| Water rune | 50 | 12 | Natural stack | Specialty |
| Earth rune | 50 | 12 | Natural stack | Specialty |
| Fire rune | 50 | 12 | Natural stack | Specialty |
| Chaos rune | 15 | 8 | Natural stack | Specialty |
| Death rune | 5 | 4 | Natural stack | Specialty |
| Law rune | 3 | 4 | Natural stack | Specialty |
| Unidentified harralander | 1 | 8 | Noted | Other supply |
| Steel arrows | 15 | 8 | Natural stack | Other supply |
| Coal | 5 | 8 | Noted | Other supply |
| Gold bar | 1 | 4 | Noted | Other supply |

### Naga

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Salmon | 4 | 20 | Noted | Specialty |
| Tuna | 4 | 20 | Noted | Specialty |
| Lobster | 3 | 16 | Noted | Specialty |
| Swordfish | 2 | 8 | Noted | Specialty |
| Water rune | 30 | 8 | Natural stack | Other supply |
| Steel arrows | 20 | 8 | Natural stack | Other supply |
| Coal | 5 | 8 | Noted | Other supply |
| Uncut sapphire | 1 | 4 | Natural stack | Other supply |

### Terror dog

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Steel arrows | 40 | 16 | Natural stack | Specialty |
| Mithril arrows | 30 | 16 | Natural stack | Specialty |
| Titan steel arrows | 20 | 8 | Natural stack | Specialty |
| Steel bolts | 30 | 8 | Natural stack | Specialty |
| Mithril bolts | 20 | 8 | Natural stack | Specialty |
| Mithril shuriken | 10 | 8 | Natural stack | Specialty |
| Chaos rune | 10 | 8 | Natural stack | Other supply |
| Coal | 8 | 8 | Noted | Other supply |
| Unidentified harralander | 2 | 8 | Noted | Other supply |
| Gold bar | 2 | 4 | Noted | Other supply |

### Bloodveld

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Unidentified irit leaf | 2 | 14 | Noted | Specialty |
| Unidentified avantoe | 2 | 14 | Noted | Specialty |
| Unidentified kwuarm | 2 | 12 | Noted | Specialty |
| Unidentified cadantine | 2 | 10 | Noted | Specialty |
| Unidentified dwarf weed | 2 | 10 | Noted | Specialty |
| Unidentified torstol | 1 | 4 | Noted | Specialty |
| Nature rune | 8 | 8 | Natural stack | Other supply |
| Mithril arrows | 20 | 8 | Natural stack | Other supply |
| Coal | 10 | 8 | Noted | Other supply |
| Uncut ruby | 1 | 4 | Natural stack | Other supply |

### Dark beast

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Chaos rune | 35 | 8 | Natural stack | Specialty |
| Nature rune | 15 | 12 | Natural stack | Specialty |
| Law rune | 15 | 12 | Natural stack | Specialty |
| Death rune | 20 | 16 | Natural stack | Specialty |
| Blood rune | 10 | 16 | Natural stack | Specialty |
| Adamantite arrows | 15 | 8 | Natural stack | Other supply |
| Coal | 12 | 8 | Noted | Other supply |
| Gold bar | 3 | 8 | Noted | Other supply |
| Uncut diamond | 1 | 4 | Natural stack | Other supply |

### Abyssal demon

| Item | Quantity | Weight | Form | Category |
| --- | ---: | ---: | --- | --- |
| Adamantite arrows | 30 | 16 | Natural stack | Specialty |
| Orichalcum arrows | 20 | 16 | Natural stack | Specialty |
| Rune arrows | 10 | 8 | Natural stack | Specialty |
| Adamantite bolts | 20 | 8 | Natural stack | Specialty |
| Orichalcum bolts | 15 | 8 | Natural stack | Specialty |
| Rune shuriken | 5 | 8 | Natural stack | Specialty |
| Death rune | 15 | 8 | Natural stack | Other supply |
| Coal | 15 | 8 | Noted | Other supply |
| Mithril bar | 3 | 8 | Noted | Other supply |
| Uncut diamond | 1 | 4 | Natural stack | Other supply |

## Independent equipment

Tier 9 metal is **Orichalcum**. Its five wearable armor slots are the requested
head, hands, feet, legs and torso; no additional shield roll is invented.
Tier 10 bows are **Blood** bows. This pass selects the Blood longbow as the
single requested bow reward, rather than adding a second shortbow chance.
Rune helm means the active Rune Helmet (112), not the legacy medium helm.
Rune legs means the plate-mail legs (402), not retired chain legs or a skirt.

| Source | Item | ID | Base chance per kill |
| --- | --- | ---: | --- |
| Bloodveld | Orichalcum helmet | 1977 | 1/1,000 |
| Bloodveld | Orichalcum greaves | 1979 | 1/1,000 |
| Bloodveld | Orichalcum gauntlets | 1978 | 1/1,000 |
| Bloodveld | Orichalcum plate mail legs | 1981 | 1/2,000 |
| Bloodveld | Orichalcum plate mail body | 1982 | 1/2,000 |
| Dark beast | Blood Longbow | 2129 | 1/2,000 |
| Dark beast | Rune Helmet | 112 | 1/1,000 |
| Dark beast | Rune gauntlets | 1993 | 1/1,000 |
| Dark beast | Rune greaves | 1994 | 1/1,000 |
| Abyssal demon | Rune Plate Mail Legs | 402 | 1/1,000 |

Each equipment piece has an independent access table (weight 100 success,
100 × (denominator − 1) empty) leading to a rare reward table. This matches
the existing Slayer-component integration, including potion luck's fractional
weight precision and personal-loot rare contribution gate. Multiple pieces can
drop together, alongside a unique component and ordinary supplies.
The shared rare table does not contain these equipment pieces, so it provides
no hidden second path that increases their listed base odds.

## Implementation and verification

- `SlayerOrdinaryDrops` registers all eight ordinary tables only for MyWorld,
  after pre-existing table adjustments, and separately rolls equipment.
- `NpcDrops` exposes that equipment roll; `Npc.dropHiddenUniqueItems` delivers
  it through the same path as existing independent components. Baseline
  materials/remains and other monsters' tables are unchanged.
- `ant test_slayer_ordinary_loot` enumerates every ordinary RNG ticket, checks
  quantities/noting and rare-table access, verifies every equipment boundary,
  independent co-drops, contribution/luck/jewelry behavior, actual ground
  delivery with components and remains, and reload/world isolation.
- `ant test_slayer_component_drops` retains component probability and delivery
  coverage. Existing loot/contribution regression suites also apply.

Regression results: content-item resolution, ordinary/dragon/baby-dragon/
paladin/dark-warrior drop checks, combat runtime invariants/scenarios, zombie
eyes, god-knight drops, and pitchfork/fire-sword checks pass. The runtime
invariant's stale three-argument melee-call assertion was updated to the
existing four-argument call without changing combat behavior.

Known pre-existing test failure: `test-loot-goblin-summon.py` still expects an
`arrowId` collection call; the current shared recovery method uses
`projectileItemId`. The same failure was reproduced on pre-change manager
`main`. Projectile recovery and that test are outside this loot-table pass.

## Remaining work

Protection consumables now have [shop access and prices](slayer-gimmick-shop-implementation.md).
Leather set-effect designs, tower associates,
final artwork and owner map placement remain separate. Optional access-gated
boss tasks and the deferred existing-hide retirement/grandfathering audit are
not altered by this pass.
