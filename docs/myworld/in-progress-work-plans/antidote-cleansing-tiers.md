# Antidote cleansing tiers

Approved and implemented in the active worker build, 2026-09-24. No deployment or server restart authorized by this task.

| Potion | Full / two / one dose IDs | Herblaw | Secondary | Extra power removed per poison pulse | Recipe XP |
|---|---|---|---|---|---|
| Weak Antidote | 3402 / 3403 / 3404 | 8 | Giant Spider Eggs, 3400 | 5 | 160 |
| Antidote | 1474 / 1475 / 1476 | 22 | Red spiders eggs, 219 | 10 | 220 |
| Strong Antidote | 3405 / 3406 / 3407 | 38 | Jungle Spider Eggs, 3401 | 20 | 300 |

Each uses one secondary with unfinished Marrentill potion (455), made from Marrentill and water by the existing system. Standard quest, member, level and batch-crafting rules remain. XP is an implementation assumption: retain old level-8 antidote XP160, match level-22 stat restore XP220, and interpolate Strong XP300 between ordinary level30 XP250 and level50 XP350. These are authored recipe values subject to existing XP multipliers.

Each dose lasts exactly ten wall-clock minutes. There is one active antidote: equal tier refreshes, stronger tier replaces and refreshes, weaker tier is rejected without consuming the dose or extending the stronger buff. They do not instantly cure, block application, or grant immunity. Three-dose consumption/empty vial and within-family decanting use the existing behavior. Noted potions cannot be drunk. Status HUD shows the active potion icon/name and remaining duration.

Base cleansing remains 3 power each 8-server-tick poison pulse. Equipped Nature necklace (+1 to +5), full carapace (+2/+3/+5), and antidote (+5/+10/+20) add: maximum currently 33. Damage is calculated from pre-drain power. Subtraction is bounded at zero; reaching zero cures after that pulse, clearing the event, durable record, maximum and source provenance. Positive residual power1–9 retains the existing next-pulse threshold cleanup. NPCs retain baseline decay; these equipment/potion bonuses are player-only. Legacy immunity APIs are unchanged, but the actual Antidote dispatcher no longer calls them. Corrosive Aura is unchanged.

The buff uses ordinary session attributes like the prior session-only antidote protection; it is not saved across reconnection/restart. Death does not explicitly erase the potion timer, consistent with the previous protection. It is not amplified by other potion-duration/power gear: the requested ten minutes and flat cleansing values are exact.

Titan Steel poison tier now matches before Steel in the material name resolver: all five existing named poisoned families (bolts, darts, knives, spears, shurikens) apply28/max70. Ordinary Steel remains apply20/max50. No new weapon variants created.

## Assets and acquisition

Giant Spider Eggs is yellow; Jungle Spider Eggs is green. `tools/generators/generate-antidote-items.cjs` deterministically recolors the authentic48×32 red-spider-eggs reference, retaining every alpha pixel,21×15 occupied bounds and neutral detail. Potion visuals reuse dose-specific potion artwork, yellow/blue-green/green tints. Existing IDs, prices and name Antidote remain. New egg value7 mirrors red spiders eggs; Weak/Strong full values144/432 are ordinary tier-scaled placeholders, no rarity premium.

No new egg drop sources, shops, spawns or drop rates were authorized. The two new eggs are currently developer-spawnable and craftable ingredients once obtained, not naturally obtainable. This is the remaining content decision before the new tiers can be acquired through gameplay.

New server definitions are `server/conf/server/defs/AntidoteItemDefs.json`, loaded immediately after existing custom item definitions. Include this tracked configuration with the existing server configuration tree; the PNGs are embedded by the standard client build. The source export/reference is untouched.

## Validation

- `ant test_antidote_cleansing` from server: live tier values, nine actual potion dose transitions, noted and weaker rejection, refresh/replacement, no cure/immunity on drinking, additive33 drain, bounded cleanup, recipes, status and decant registrations.
- `node tests/myworld/test-antidote-icons.cjs`: exact recolor geometry/alpha, packaged PNG equality, both client catalogs, all dose sprites and bank notes.
- Existing Herblaw remap, potion runtime/HUD, poison model and strict combat regressions.

Held shears were separately approved for in-game testing and integrated before this work; this poison change does not alter them.
