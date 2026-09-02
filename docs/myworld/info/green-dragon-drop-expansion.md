# Ordinary Green Dragon Drop Expansion

This note covers ordinary Green Dragon NPC `862` only. Dragon/Elvarg NPC `196`
remains quest-only and has an empty 128-weight normal table plus guaranteed green
dragon hide and dragon bones. It has no hidden unique rolls.

## Blue Dragon comparison

Adult Blue Dragon `202` uses the same broad 128-weight structure as the original
ordinary Green Dragon table: coins, herbs, the rare table, runes, titan-steel and
rune equipment, adamantite ore, and bass. Its elemental normal drop is 50 water
runes at weight 8, while ordinary Green Dragon uses 50 earth runes at weight 8.
Blue Dragon separately guarantees blue dragon hide and dragon bones and has hidden
Ice Sword (`1/4096`) and raw dragon metal (`1/1024`) rolls. The blue dragon scale
and baby dragon eye are weight-4 drops from Baby Blue Dragon `203`; they are not
part of the adult Blue Dragon table and therefore have not been copied.

The catalog contains usable earth counterparts for the Earth talisman, Earth Pine
Staff, and Earth Maple Staff. The legacy Earth Orb and Battlestaff of Earth IDs are
named `Retired item` in the authoritative item definitions, so they are excluded.
Seasonal/quest items and finished green dragon-hide armour are also excluded to
avoid bypassing their intended sources and crafting progression.

## Normal table weights

Every listed weight is out of 128. The OpenPK and non-OpenPK branches both total
128. Amount differences controlled by configuration do not alter weights.

| Reward | Before | After | Reason |
| --- | ---: | ---: | --- |
| Rune drop table (OpenPK) | 2 | 2 | Retained category |
| Arrows/runes table (OpenPK) | 8 | 8 | Retained category |
| 88 coins | 17 OpenPK / 27 normal | 17 OpenPK / 27 normal | Retained |
| Herb table | 15 | 15 | Retained |
| Rare table | 5 | 5 | Retained |
| 176 coins | 25 | 20 | Funds thematic additions |
| 264 coins | 10 | 10 | Retained |
| 50 earth runes | 8 | 8 | Blue Dragon's water-rune counterpart |
| 10 nature runes | 5 | 5 | Retained |
| 11 coins | 5 | 3 | Funds thematic additions |
| Titan-steel plate legs | 4 | 4 | Retained |
| Titan-steel axe | 3 | 3 | Retained |
| Titan-steel battle axe | 3 | 3 | Retained |
| Law runes (15 OpenPK / 2 normal) | 3 | 3 | Retained |
| Adamantite ore | 3 | 3 | Retained |
| Two bass | 1 | 1 | Retained |
| One bass | 2 | 2 | Retained |
| Titan-steel spear | 2 | 2 | Retained |
| Rune dagger | 1 | 1 | Retained |
| Titan-steel kite shield | 1 | 1 | Retained |
| Large adamantite helmet | 1 | 1 | Retained |
| 25 earth runes | 1 | 1 | Retained |
| 440 coins | 1 | 1 | Retained |
| Earth talisman | 0 | 3 | Low-value earth utility counterpart |
| Earth Pine Staff | 0 | 3 | Entry-level farmable earth staff |
| Earth Maple Staff | 0 | 1 | Rare level-31 earth staff |
| Empty | 2 | 2 | Retained dry-roll rate |

Guaranteed green dragon hide and dragon bones remain unchanged. The hidden Earth
Sword (`1/4096`) and raw dragon metal (`1/1024`) rates also remain unchanged and
do not consume normal-table weight.
