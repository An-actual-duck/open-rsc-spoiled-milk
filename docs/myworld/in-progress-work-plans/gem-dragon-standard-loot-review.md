# Gem dragon standard loot — Core review

**Historical expansion record.** The later `gem-dragon-normal-scrap-review.md`
supersedes independent Scrap delivery and unconditional common probabilities/EV
below. The amounts, conditional common weights and guaranteed gem matrix remain.

This expands only the normal loot and adds a rare resource roll. The guaranteed
uncut-gem matrix, exact 1/3/5 quantities, single Dragonstone outcome, necklace
boundaries, stats, fire, unique equipment odds/effects, bones, native sprites,
IDs and map placements remain unchanged.

## Initial normal-loot balance

The retained crafting weights are gold bars 28, plain gold ring 8 and the own-tier
unenchanted ring 8. The previous 84 empty weights now become bolts 16, arrows 4,
runes 20, coins 36 and empty 8. Total 128; bolts have four times the arrow weight.
No generic rare/gem table is inherited, so ordinary loot cannot leak extra gems.

| Dragon | Bolts (16/128) | Arrows (4/128) | Rune subtable (20/128) | Coins (36/128) |
|---|---|---|---|---:|
| Sapphire | Mithril 2 | Mithril 1 | Chaos 10 |300 |
| Emerald | Titan Steel 3 | Titan Steel 2 | Chaos 15 / Nature 15 |450 |
| Ruby | Adamantite 4 | Adamantite 3 | Nature 20 / Law 20 |650 |
| Diamond | Orichalcum 5 | Orichalcum 4 | Law 25 / Death 25 |900 |
| Dragonstone | Rune 6 | Rune 5 | Death 30 / Blood 30 |1200 |

Each two-choice rune subtable has equal 8/8 child weights; Sapphire's single
Chaos outcome has weight 16. Both ammo grade and quantity rise each tier. Runes
use the actual Runecraft progression: Chaos 22, Nature 38, Law 46, Death 54 and
Blood 70. Rune prices do not define that ordering (Nature's base price 7 is
lower than Chaos 10). Common rune quantities rise 10/15/20/25/30. Gold bars stay
noted 2/3/4/5/6; ring quantities remain 1. Ammo, runes and coins are ordinary
stackable, unnoted drops. Eight empty normal tickets remain, including ticket 127;
the separate guaranteed gem still arrives on an empty normal roll.

## Dragon Metal Scrap

Reuse existing item 3228, **Dragon Metal Scrap**, base value 100000. One unit,
unnoted, is an independent hidden rare roll:

| Dragon | Scrap chance |
|---|---:|
| Sapphire |1/1024 |
| Emerald |1/768 |
| Ruby |1/512 |
| Diamond |1/256 |
| Dragonstone |1/128 |

Odds improve strictly. Existing ordinary green/blue 1/1024, red 1/512 and
black 1/128 resource rates provide the starting reference. The new roll does not
replace the guaranteed gem or normal loot, and does not change the two existing
unique-equipment rolls. All three rare outcomes may coexist on a lucky kill.
Cosmic/Death necklaces do not duplicate scrap; existing personal-loot
contribution scaling still applies.

## Nominal value context

Enhanced ammo is expensive: matching arrows/bolts have base values 320, 720, 1520,
2600 and 4000. We therefore chose small stacks and reduced the initial suggested
ammo weights to 16/4, avoiding OSRS-sized stacks. Top-tier ammo contributes 3625
nominal base value per normal roll, rather than 7125 under the earlier suggested
4..8/2..6 stacks with 24/6 weights.

| Dragon | Normal table base EV | Guaranteed gem base EV | Scrap base EV | Subtotal before bones/gear |
|---|---:|---:|---:|---:|
| Sapphire | 621.25 | 750.00 | 97.66 | 1,468.91 |
| Emerald | 1,098.98 | 1,142.86 | 130.21 | 2,372.05 |
| Ruby | 2,071.25 | 1,653.06 | 195.31 | 3,919.62 |
| Diamond | 3,728.12 | 2,328.57 | 390.62 | 6,447.32 |
| Dragonstone | 7,099.22 | 2,402.84 | 781.25 | 10,283.31 |

These values use actual loaded item prices and recursively weighted ordinary
tables, checked against Core's independently prepared matrix. Ordinary green,
blue, red and black normal-table base EVs remain approximately 1637, 1265, 1852
and 4385. That is a **normal-table comparison only**: existing guaranteed hides
are valuable (green 2100, black 8400). For example Black Dragon normal loot, hide
and scrap total approximately 13567 before bones/other uniques, while Gem
Dragonstone normal loot, guaranteed gems and scrap total approximately 10283
before bones/gear. This does not claim the gem family is more profitable than
ordinary Black Dragons. These are base-price estimates, not player trade values,
alchemy proceeds, profit-per-hour, combat benchmarks or measured kill times.

## Verification

- 80,616 new assertions exhaust all 10240 root/child ticket combinations and 264
 actual death deliveries spanning every ordinary outcome with no necklace,
 Cosmic necklace and Death necklace, normal and personal loot. Bolts' 4:1 priority,
 actual rising ammunition offense, rune progression, stack quantities, noting,
 owner/private flags and independent guaranteed gems/bones are checked.
- Scrap exact full/quarter-contribution boundaries, strictly rising chances,
 single quantity, no necklace duplication and coexistence with both unique
 equipment wins are verified through production rolling and actual delivery.
- The retained guaranteed fixture passes 7379 assertions across all 897 tickets
 and 258 normal/personal necklace deliveries. Only its obsolete normal-table
 assertions moved to the new fixture; gem behavior is unchanged.
- 232 retained equipment/fire/stat assertions and the Slayer ordinary-loot,
 component and reward regressions pass.
- The guaranteed method bodies and rank/quantity arrays are source-identical to
 the published baseline. No generic DropTable logic changed.

Commands, exact independently prepared matrix, concise summaries, read-only
runtime EV probe and current artifact hashes are in
`gem-dragon-standard-loot-evidence/`. No full combat/client/render reruns were
needed because those behaviors and assets are unchanged. This worker did not
operate on live servers, owner maps, player accounts, toolkit code or clients.
Core reviews/integrates and maintains the next-update package separately.
