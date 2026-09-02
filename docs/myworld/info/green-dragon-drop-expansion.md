# Ordinary Green Dragon Drop Expansion

This note covers ordinary Green Dragon NPC `862` only. Dragon/Elvarg NPC `196`
remains quest-only and has an empty 128-weight normal table plus guaranteed green
dragon hide and dragon bones. It has no hidden unique rolls.

## Blue Dragon and item-catalog audit

Adult Blue Dragon `202` provides the structural comparison: its 128-weight table
contains coins, herbs, the rare table, runes, metal equipment, ore, and food. It
does **not** drop a Water talisman. Blue Dragon separately guarantees blue dragon
hide and dragon bones and has hidden Ice Sword (`1/4096`) and raw dragon metal
(`1/1024`) rolls. Blue dragon scales and baby dragon eyes belong to Baby Blue
Dragon `203`, not the adult table, so they have not been copied.

Earth and Water talismans are both described as relics from older altar practices
and are retired from this drop design. The legacy Earth Orb and Battlestaff of
Earth IDs are literally named `Retired item` in the authoritative definitions and
are also excluded. Seasonal items, quest items, and finished green dragon-hide
armour remain excluded.

The selected additions are active standard catalog items. Earth Pine Staff is a
level-1 magic weapon (base price 875); Earth Maple Staff is level 31 (base price
2,500). Rune plate legs and the large Rune helmet are active level-40 armour with
base prices 64,000 and 35,200. Their separate `1/128` rolls match the existing
level-87 Greater Demon precedent rather than introducing an unprecedented armor
rate. Earth, Chaos, Death, and Blood runes are active non-quest consumables.

## Exact normal-table rebalance

Every weight is out of 128. OpenPK and non-OpenPK both use 27 branch weight,
99 shared reward weight, and 2 empty weight. Rune quantities controlled by the
OpenPK setting change amounts only, never table weight.

| Reward | Before | After | Rationale |
| --- | ---: | ---: | --- |
| Rune drop table (OpenPK) | 2 | 2 | Retained OpenPK category |
| Arrows/runes table (OpenPK) | 8 | 8 | Retained OpenPK category |
| Coins, branch roll | 88: 17 OpenPK / 27 normal | 250: 17 OpenPK / 27 normal | Worthwhile baseline coins |
| Herb table | 15 | 10 | Reduced to fund direct high-level runes |
| Rare table | 5 | 5 | Retained |
| 176 coins | 20 | 0 | Consolidated into useful coin rolls |
| 264 coins | 10 | 0 | Consolidated into useful coin rolls |
| 11 coins | 3 | 0 | Removed trivial roll |
| 440 coins | 1 | 0 | Consolidated into useful coin rolls |
| 500 coins | 0 | 18 | Common worthwhile coin roll |
| 1,000 coins | 0 | 8 | Uncommon worthwhile coin roll |
| 2,500 coins | 0 | 1 | Rare coin payout |
| 50 earth runes | 8 | 0 | Replaced by meaningful stacks |
| 25 earth runes | 1 | 0 | Replaced by meaningful stacks |
| 250 earth runes | 0 | 10 | Core earth-themed stack |
| 500 earth runes | 0 | 4 | Large earth-themed stack |
| 40 chaos runes | 0 | 9 | Meaningful direct Chaos roll |
| 100 chaos runes | 0 | 3 | High-quantity Chaos roll |
| 15 death runes | 0 | 6 | Useful direct Death roll |
| 40 death runes | 0 | 2 | High-quantity Death roll |
| 10 blood runes | 0 | 4 | Useful Blood roll |
| Nature runes | 10 at weight 5 | 25 at weight 4 | Larger utility stack |
| Law runes | 15 OpenPK / 2 normal at weight 3 | 20 OpenPK / 5 normal at weight 3 | Larger utility stack |
| Titan-steel plate legs | 4 | 2 | Retained armor tier, reduced frequency |
| Titan-steel axe | 3 | 0 | Replaced by deeper rewards |
| Titan-steel battle axe | 3 | 1 | Retained equipment category |
| Titan-steel spear | 2 | 0 | Replaced by deeper rewards |
| Titan-steel kite shield | 1 | 0 | Replaced by Rune armor |
| Rune dagger | 1 | 0 | Replaced by genuine Rune armor |
| Large adamantite helmet | 1 | 0 | Replaced by genuine Rune armor |
| Rune plate-mail legs | 0 | 1 | Active level-40 Rune armor |
| Large Rune helmet | 0 | 1 | Active level-40 Rune armor |
| Adamantite ore | 1 at weight 3 | 2 at weight 2 | Retains ore with useful quantity |
| Bass | two at weight 1; one at weight 2 | two at weight 2 | Consolidated food roll |
| Earth Pine Staff | 3 | 2 | Active entry-level earth staff |
| Earth Maple Staff | 1 | 1 | Active level-31 earth staff |
| Earth talisman | 3 | 0 | Removed retired altar relic |
| Empty | 2 | 2 | Dry-roll rate unchanged |

Guaranteed green dragon hide and dragon bones remain unchanged. The hidden Earth
Sword (`1/4096`) and raw dragon metal (`1/1024`) rates also remain unchanged and
do not consume normal-table weight.
