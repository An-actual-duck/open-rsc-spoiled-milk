# NPC offensive Power assignments

Order **Melee / Ranged / Magic**. Dash means a disabled style in ceiling columns. These are unmitigated raw bounds, not DPS or guaranteed damage. [Method and exceptions](README.md).

## Low

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 0 | Unicorn | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 3 | Chicken | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 4 | Tough Goblin | melee | 7 / 0 / 0 | **7 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 5 | Hans | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 6 | cow | melee | 7 / 0 / 0 | **7 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 8 | Bear | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 11 | Man | melee | 11 / 0 / 0 | **11 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 19 | Rat | melee | 10 / 0 / 0 | **10 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 21 | mugger | ranged | 8 / 0 / 0 | **3 / 8 / 0** | 2 / — / — → 1 / 2 / — | ranged:75, melee:25 | melee: 2 -> 1 ranged: new enabled attack, raw ceiling2 |
| 23 | Young Giant Spider | melee | 10 / 0 / 0 | **10 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 25 | Jonny the beard | melee | 5 / 0 / 0 | **5 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 29 | Rat | melee | 2 / 0 / 0 | **2 / 0 / 0** | 1 / — / — → 1 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 34 | spider | melee | 1 / 0 / 0 | **1 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 35 | Delrith | magic | 37 / 0 / 42 | **14 / 0 / 37** | 6 / — / 10 → 3 / — / 9 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 6 -> 3 [large delta] magic: 10 -> 9 |
| 37 | Weaponsmaster | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 40 | skeleton | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 41 | zombie | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 45 | skeleton | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 46 | skeleton | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 47 | Large Rat | melee | 15 / 0 / 0 | **15 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 50 | skeleton | melee | 21 / 0 / 0 | **21 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 52 | zombie | melee | 19 / 0 / 0 | **19 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 53 | Ghost | magic | 23 / 0 / 23 | **0 / 0 / 23** | 4 / — / 5 → — / — / 5 | magic:100 | melee: disabled (old raw ceiling4) |
| 57 | Darkwizard | magic | 12 / 0 / 15 | **0 / 0 / 12** | 2 / — / 4 → — / — / 2 | magic:100 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: disabled (old raw ceiling2) magic: 4 -> 2 |
| 60 | Darkwizard | magic | 27 / 0 / 27 | **0 / 0 / 27** | 4 / — / 6 → — / — / 6 | magic:100 | melee: disabled (old raw ceiling4) |
| 62 | Goblin | melee | 9 / 0 / 0 | **9 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 63 | farmer | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 64 | Thief | ranged | 23 / 24 / 0 | **0 / 23 / 0** | 4 / 4 / — → — / 4 / — | ranged:100 | melee: disabled (old raw ceiling4) |
| 65 | Guard | ranged | 31 / 31 / 0 | **0 / 31 / 0** | 5 / 5 / — → — / 5 / — | ranged:100 | melee: disabled (old raw ceiling5) |
| 70 | Scorpion | melee | 22 / 0 / 0 | **22 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 72 | Man | melee | 11 / 0 / 0 | **11 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 74 | Giant Spider | melee | 34 / 0 / 0 | **34 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 76 | Barbarian | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 79 | Witch | magic | 30 / 0 / 35 | **0 / 0 / 30** | 5 / — / 8 → — / — / 7 | magic:100 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: disabled (old raw ceiling5) magic: 8 -> 7 |
| 80 | Ghost | magic | 23 / 0 / 23 | **0 / 0 / 23** | 4 / — / 5 → — / — / 5 | magic:100 | melee: disabled (old raw ceiling4) |
| 81 | Wizard | magic | 18 / 0 / 18 | **0 / 0 / 18** | 3 / — / 4 → — / — / 4 | magic:100 | melee: disabled (old raw ceiling3) |
| 86 | Warrior | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 89 | Highwayman | ranged | 13 / 0 / 0 | **5 / 13 / 0** | 2 / — / — → 1 / 2 / — | ranged:75, melee:25 | melee: 2 -> 1 ranged: new enabled attack, raw ceiling2 |
| 93 | Monk | magic | 12 / 0 / 12 | **0 / 0 / 12** | 2 / — / 2 → — / — / 2 | magic:100 | melee: disabled (old raw ceiling2) |
| 94 | Dwarf | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 100 | Guard | ranged | 31 / 31 / 0 | **12 / 31 / 0** | 5 / 5 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] |
| 109 | Greldo | melee | 9 / 0 / 0 | **9 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 114 | Imp | melee | 5 / 0 / 0 | **5 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 137 | Pirate | ranged | 30 / 35 / 0 | **12 / 30 / 0** | 5 / 6 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] ranged: 6 -> 5 |
| 139 | Monk of Zamorak | magic | 28 / 0 / 28 | **0 / 0 / 28** | 5 / — / 7 → — / — / 7 | magic:100 | melee: disabled (old raw ceiling5) |
| 140 | Monk of Zamorak | magic | 18 / 0 / 18 | **0 / 0 / 18** | 3 / — / 4 → — / — / 4 | magic:100 | melee: disabled (old raw ceiling3) |
| 153 | Tough Goblin | melee | 13 / 0 / 0 | **13 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 154 | Tough Goblin | melee | 13 / 0 / 0 | **13 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 159 | Warrior | melee | 30 / 0 / 0 | **30 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 177 | Large Rat | melee | 7 / 0 / 0 | **7 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 178 | Ghost | magic | 23 / 0 / 23 | **9 / 0 / 23** | 4 / — / 5 → 2 / — / 5 | magic:75, melee:25 | melee: 4 -> 2 |
| 179 | skeleton | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 188 | Bear | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 192 | Wormbrain | melee | 9 / 0 / 0 | **9 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 199 | Dark Warrior | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 200 | Druid | magic | 28 / 0 / 28 | **0 / 0 / 28** | 5 / — / 7 → — / — / 7 | magic:100 | melee: disabled (old raw ceiling5) |
| 232 | Bandit | ranged | 26 / 32 / 0 | **10 / 26 / 0** | 4 / 5 / — → 2 / 4 / — | ranged:75, melee:25 | melee: 4 -> 2 ranged: 5 -> 4 |
| 234 | Bandit | ranged | 26 / 32 / 0 | **10 / 26 / 0** | 4 / 5 / — → 2 / 4 / — | ranged:75, melee:25 | melee: 4 -> 2 ranged: 5 -> 4 |
| 239 | White wolf sentry | melee | 31 / 0 / 0 | **31 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 244 | shapeshifter | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 251 | Thug | melee | 17 / 0 / 0 | **17 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 252 | Firebird | melee | 7 / 0 / 0 | **7 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 264 | Pirate | ranged | 33 / 38 / 0 | **13 / 33 / 0** | 5 / 6 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] ranged: 6 -> 5 |
| 270 | Chaos Druid | magic | 14 / 0 / 18 | **0 / 0 / 14** | 3 / — / 4 → — / — / 4 | magic:100 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: disabled (old raw ceiling3) |
| 271 | Poison Scorpion | melee | 27 / 0 / 0 | **27 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 296 | Black Unicorn | melee | 33 / 0 / 0 | **33 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 318 | Man | melee | 11 / 0 / 0 | **11 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 319 | farmer | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 320 | Warrior | melee | 30 / 0 / 0 | **30 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 321 | Guard | ranged | 31 / 31 / 0 | **0 / 31 / 0** | 5 / 5 / — → — / 5 / — | ranged:100 | melee: disabled (old raw ceiling5) |
| 338 | zoo keeper | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 342 | Rogue | ranged | 23 / 24 / 0 | **0 / 23 / 0** | 4 / 4 / — → — / 4 / — | ranged:100 | melee: disabled (old raw ceiling4) |
| 348 | Forester | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 351 | Thief | ranged | 23 / 24 / 0 | **0 / 23 / 0** | 4 / 4 / — → — / 4 / — | ranged:100 | melee: disabled (old raw ceiling4) |
| 356 | Mountain Dwarf | melee | 30 / 0 / 0 | **30 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 359 | zombie | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 364 | Lucien | magic | 23 / 0 / 24 | **9 / 0 / 23** | 4 / — / 5 → 2 / — / 5 | magic:75, melee:25 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 4 -> 2 |
| 367 | Dungeon Rat | melee | 22 / 0 / 0 | **22 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 399 | local gnome | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 402 | kalron | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 407 | Khazard troop | ranged | 31 / 31 / 0 | **12 / 31 / 0** | 5 / 5 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] |
| 409 | gnome troop | ranged | 3 / 3 / 0 | **1 / 3 / 0** | 1 / 1 / — → 1 / 1 / — | ranged:75, melee:25 |  |
| 416 | unhappy peasant | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 417 | happy peasant | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 420 | carnillean guard | ranged | 31 / 31 / 0 | **12 / 31 / 0** | 5 / 5 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] |
| 425 | cult member | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 438 | Citizen | melee | 10 / 0 / 0 | **10 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 439 | Citizen | melee | 8 / 0 / 0 | **8 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 440 | Citizen | melee | 14 / 0 / 0 | **14 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 441 | Citizen | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 442 | Citizen | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 462 | Platform Fisherman | melee | 15 / 0 / 0 | **15 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 463 | Platform Fisherman | melee | 15 / 0 / 0 | **15 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 464 | Platform Fisherman | melee | 15 / 0 / 0 | **15 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 473 | Rat | melee | 8 / 0 / 0 | **8 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 495 | Mourner | melee | 30 / 0 / 0 | **30 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 498 | skeleton mage | magic | 23 / 0 / 24 | **0 / 0 / 23** | 4 / — / 5 → — / — / 5 | magic:100 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: disabled (old raw ceiling4) |
| 502 | Mourner | melee | 25 / 0 / 0 | **25 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 516 | target practice zombie | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 519 | Soldier | melee | 31 / 0 / 0 | **31 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 550 | charlie | melee | 0 / 0 / 0 | **0 / 0 / 0** | 1 / — / — → 1 / — / — | preserve | Charlie level0/HP3 has zero Power but existing ordinary max1. Keep outside runtime migration until its role and enabled-zero semantics are reviewed. |
| 551 | Gnome guard | ranged | 31 / 31 / 0 | **12 / 31 / 0** | 5 / 5 / — → 2 / 5 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 5 -> 2 [large delta] |
| 562 | Gnome guard | ranged | 23 / 23 / 0 | **0 / 23 / 0** | 4 / 4 / — → — / 4 / — | ranged:100 | melee: disabled (old raw ceiling4) |
| 582 | Gnome guard | ranged | 17 / 31 / 0 | **0 / 17 / 0** | 3 / 5 / — → — / 3 / — | ranged:100 | melee: disabled (old raw ceiling3) ranged: 5 -> 3 |
| 583 | Gnome child | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 585 | Gnome child | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 586 | Gnome child | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 592 | Gnome local | melee | 9 / 0 / 0 | **9 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 593 | Gnome local | melee | 3 / 0 / 0 | **3 / 0 / 0** | 1 / — / — → 1 / — / — | melee:100 |  |
| 630 | Blessed Vermen | melee | 7 / 0 / 0 | **7 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 634 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 635 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 636 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 637 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 638 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 639 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 640 | slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 644 | Souless | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 655 | Souless | melee | 23 / 0 / 0 | **23 / 0 / 0** | 4 / — / — → 4 / — / — | melee:100 |  |
| 656 | Dungeon spider | melee | 10 / 0 / 0 | **10 / 0 / 0** | 2 / — / — → 2 / — / — | melee:100 |  |
| 658 | Iban disciple | magic | 18 / 0 / 0 | **7 / 0 / 18** | 3 / — / — → 2 / — / 4 | magic:75, melee:25 | melee: 3 -> 2 magic: new enabled attack, raw ceiling4 |
| 660 | Goblin | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 664 | Ghost | magic | 30 / 0 / 33 | **0 / 0 / 30** | 5 / — / 7 → — / — / 7 | magic:100 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: disabled (old raw ceiling5) |
| 671 | Mining Slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 694 | Dwarf | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 699 | Dwarf | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 713 | kolodion | magic | 10 / 0 / 20 | **10 / 0 / 20** | — / — / 4 → — / — / 4 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 718 | Rowdy Slave | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 721 | Desert Wolf | melee | 31 / 0 / 0 | **31 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 729 | civillian | melee | 18 / 0 / 0 | **18 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 863 | Giant frog | ranged | 1 / 30 / 0 | **0 / 30 / 0** | — / 5 / — → — / 5 / — | preserve | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. Keep authored Ranged30; disabled Melee becomes0. Existing ranged-only spit behavior and status effects remain unchanged. |

## Mid Low

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 43 | Giant bat | melee | 32 / 0 / 0 | **32 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 61 | Giant | melee | 40 / 0 / 0 | **40 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 66 | Black Knight | melee | 48 / 0 / 48 | **48 / 0 / 24** | 7 / — / 10 → 7 / — / 5 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 10 -> 5 [large delta] |
| 67 | Hobgoblin | melee | 34 / 0 / 0 | **34 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 68 | zombie | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 78 | Gunthor the Brave | melee | 38 / 0 / 0 | **38 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 96 | Count Draynor | melee | 28 / 0 / 0 | **28 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 99 | Deadly Red spider | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 108 | Black Knight | melee | 48 / 0 / 48 | **48 / 0 / 24** | 7 / — / 10 → 7 / — / 5 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 10 -> 5 [large delta] |
| 127 | Jailguard | ranged | 36 / 36 / 0 | **18 / 36 / 0** | 6 / 6 / — → 3 / 6 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 6 -> 3 [large delta] |
| 136 | King Scorpion | melee | 39 / 0 / 0 | **39 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 180 | zombie | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 182 | Melzar the mad | magic | 47 / 0 / 0 | **23 / 0 / 47** | 7 / — / — → 4 / — / 10 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 7 -> 4 [large delta] magic: new enabled attack, raw ceiling10 |
| 189 | Black Knight | melee | 48 / 0 / 48 | **48 / 0 / 24** | 7 / — / 10 → 7 / — / 5 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 10 -> 5 [large delta] |
| 203 | Baby Blue Dragon | melee | 84 / 0 / 84 | **84 / 0 / 42** | 13 / — / 19 → 13 / — / 10 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 19 -> 10 [large delta] |
| 214 | zombie | melee | 35 / 0 / 0 | **35 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 236 | Donny the lad | melee | 36 / 0 / 0 | **36 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 237 | Black Heather | melee | 36 / 0 / 0 | **36 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 238 | Speedy Keith | melee | 36 / 0 / 0 | **36 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 248 | White wolf | melee | 41 / 0 / 0 | **41 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 |  |
| 259 | Grip | melee | 31 / 0 / 0 | **31 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 262 | Guard Dog | melee | 46 / 0 / 0 | **46 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 265 | Jailer | melee | 53 / 0 / 0 | **53 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 277 | Renegade knight | melee | 53 / 0 / 0 | **53 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 293 | Monk of Zamorak | magic | 48 / 0 / 48 | **0 / 0 / 48** | 7 / — / 10 → — / — / 10 | magic:100 | melee: disabled (old raw ceiling7) |
| 295 | Animated axe | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 311 | Hobgoblin | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 343 | Shadow spider | melee | 52 / 0 / 0 | **52 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 352 | Head Thief | ranged | 33 / 34 / 0 | **0 / 33 / 0** | 5 / 5 / — → — / 5 / — | ranged:100 | melee: disabled (old raw ceiling5) |
| 358 | Necromancer | magic | 28 / 0 / 28 | **0 / 0 / 28** | 5 / — / 7 → — / — / 7 | magic:100 | melee: disabled (old raw ceiling5) |
| 386 | Khazard Scorpion | melee | 49 / 0 / 0 | **49 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 421 | Tribesman | ranged | 40 / 40 / 0 | **20 / 40 / 0** | 6 / 6 / — → 3 / 6 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 6 -> 3 [large delta] |
| 428 | Khazard commander | melee | 45 / 0 / 0 | **45 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 518 | Colonel Radick | melee | 30 / 0 / 0 | **30 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 521 | Jungle Spider | melee | 47 / 0 / 0 | **47 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 555 | Chaos Druid warrior | melee | 48 / 0 / 48 | **48 / 0 / 24** | 7 / — / 10 → 7 / — / 5 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 10 -> 5 [large delta] |
| 557 | Shipyard worker | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 558 | Shipyard worker | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 559 | Shipyard worker | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 574 | Yanille Watchman | ranged | 41 / 41 / 0 | **20 / 41 / 0** | 6 / 6 / — → 3 / 6 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 6 -> 3 [large delta] |
| 584 | Earth warrior | melee | 54 / 0 / 0 | **54 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 631 | Blessed Spider | melee | 34 / 0 / 0 | **34 / 0 / 0** | 5 / — / — → 5 / — / — | melee:100 |  |
| 651 | Goblin guard | ranged | 51 / 51 / 0 | **25 / 51 / 0** | 8 / 8 / — → 4 / 8 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 8 -> 4 [large delta] |
| 653 | Ugthanki | melee | 45 / 0 / 0 | **45 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 668 | Mercenary | ranged | 32 / 48 / 0 | **16 / 32 / 0** | 5 / 7 / — → 3 / 5 / — | ranged:75, melee:25 | melee: 5 -> 3 ranged: 7 -> 5 |
| 670 | Mercenary | ranged | 32 / 48 / 0 | **16 / 32 / 0** | 5 / 7 / — → 3 / 5 / — | ranged:75, melee:25 | melee: 5 -> 3 ranged: 7 -> 5 |
| 690 | Mercenary | ranged | 32 / 48 / 0 | **16 / 32 / 0** | 5 / 7 / — → 3 / 5 / — | ranged:75, melee:25 | melee: 5 -> 3 ranged: 7 -> 5 |
| 692 | Mercenary | ranged | 32 / 48 / 0 | **16 / 32 / 0** | 5 / 7 / — → 3 / 5 / — | ranged:75, melee:25 | melee: 5 -> 3 ranged: 7 -> 5 |
| 702 | Captain Siad | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 710 | Draft Mercenary Guard | ranged | 32 / 48 / 0 | **16 / 32 / 0** | 5 / 7 / — → 3 / 5 / — | ranged:75, melee:25 | melee: 5 -> 3 ranged: 7 -> 5 |
| 716 | Rowdy Guard | ranged | 32 / 48 / 0 | **0 / 32 / 0** | 5 / 7 / — → — / 5 / — | ranged:100 | melee: disabled (old raw ceiling5) ranged: 7 -> 5 |
| 717 | Shantay Pass Guard | ranged | 32 / 32 / 0 | **0 / 32 / 0** | 5 / 5 / — → — / 5 / — | ranged:100 | melee: disabled (old raw ceiling5) |
| 759 | kolodion | magic | 23 / 0 / 38 | **23 / 0 / 38** | 4 / — / 9 → 4 / — / 9 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 777 | Oomlie Bird | melee | 20 / 0 / 0 | **20 / 0 / 0** | 3 / — / — → 3 / — / — | melee:100 |  |
| 786 | Pit Scorpion | melee | 48 / 0 / 0 | **48 / 0 / 0** | 7 / — / — → 7 / — / — | melee:100 |  |
| 789 | Battle mage | magic | 52 / 0 / 52 | **52 / 0 / 52** | 8 / — / 12 → 8 / — / 12 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 790 | Battle mage | magic | 52 / 0 / 52 | **52 / 0 / 52** | 8 / — / 12 → 8 / — / 12 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 791 | Battle mage | magic | 52 / 0 / 52 | **52 / 0 / 52** | 8 / — / 12 → 8 / — / 12 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 864 | Cockatrice | melee | 40 / 0 / 0 | **40 / 0 / 0** | 6 / — / — → 6 / — / — | melee:100 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. |
| 865 | Banshee | melee | 55 / 0 / 55 | **55 / 0 / 55** | 8 / — / 12 → 8 / — / 12 | preserve | Retain existing authored powers and scripted attack owner; do not normalize by secondary ratios or add parallel generic attacks. |

## Mid

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 102 | White Knight | melee | 58 / 0 / 58 | **58 / 0 / 34** | 9 / — / 13 → 9 / — / 7 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 13 -> 7 [large delta] |
| 104 | Moss Giant | melee | 65 / 0 / 65 | **65 / 0 / 39** | 10 / — / 15 → 10 / — / 9 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 15 -> 9 [large delta] |
| 158 | Ice warrior | melee | 59 / 0 / 59 | **59 / 0 / 35** | 9 / — / 13 → 9 / — / 8 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 13 -> 8 [large delta] |
| 190 | chaos Dwarf | melee | 60 / 0 / 0 | **60 / 0 / 0** | 9 / — / — → 9 / — / — | melee:100 |  |
| 195 | skeleton | melee | 55 / 0 / 0 | **55 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 243 | Grey wolf | melee | 65 / 0 / 0 | **65 / 0 / 0** | 10 / — / — → 10 / — / — | melee:100 |  |
| 263 | Ice spider | melee | 65 / 0 / 0 | **65 / 0 / 0** | 10 / — / — → 10 / — / — | melee:100 |  |
| 276 | Sir Mordred | melee | 60 / 0 / 0 | **60 / 0 / 0** | 9 / — / — → 9 / — / — | melee:100 |  |
| 292 | Poison Spider | melee | 68 / 0 / 0 | **68 / 0 / 0** | 10 / — / — → 10 / — / — | melee:100 |  |
| 312 | Ogre | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 322 | Knight | melee | 58 / 0 / 0 | **58 / 0 / 0** | 9 / — / — → 9 / — / — | melee:100 |  |
| 361 | The Fire warrior of lesarkus | melee | 72 / 0 / 72 | **72 / 0 / 43** | 11 / — / 16 → 11 / — / 10 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 16 -> 10 [large delta] |
| 384 | Khazard Ogre | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 523 | Jogre | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 525 | Ogre | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 542 | UndeadOne | melee | 50 / 0 / 0 | **50 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 |  |
| 594 | Moss Giant | melee | 65 / 0 / 65 | **65 / 0 / 39** | 10 / — / 15 → 10 / — / 9 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 15 -> 9 [large delta] |
| 669 | Mercenary Captain | ranged | 48 / 48 / 0 | **28 / 48 / 0** | 7 / 7 / — → 5 / 7 / — | ranged:75, melee:25 | melee: 7 -> 5 |
| 704 | Ogre citizen | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 706 | Ogre | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 775 | Karamja Wolf | melee | 61 / 0 / 0 | **61 / 0 / 0** | 9 / — / — → 9 / — / — | melee:100 |  |
| 787 | Shadow Warrior | melee | 61 / 0 / 0 | **61 / 0 / 36** | 9 / — / — → 9 / — / 8 | melee:75, magic:25 | magic: new enabled attack, raw ceiling8 |
| 816 | PK Bot | melee | 230 / 0 / 0 | **230 / 0 / 0** | 33 / — / — → 33 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 836 | Grey Knight | melee | 58 / 0 / 58 | **58 / 0 / 34** | 9 / — / 13 → 9 / — / 7 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 13 -> 7 [large delta] |
| 843 | Fire warrior | melee | 64 / 0 / 64 | **64 / 0 / 38** | 10 / — / 15 → 10 / — / 9 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 15 -> 9 [large delta] |
| 866 | Naga | ranged | 65 / 55 / 0 | **39 / 65 / 0** | 10 / 8 / — → 6 / 10 / — | ranged:75, melee:25 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. Naga changes emphasis: Ranged65 / Melee39 instead of Ranged55 / Melee65; preserve its existing throw/melee cadence and protection rules. Review the resulting damage change. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 10 -> 6 [large delta] ranged: 8 -> 10 |
| 882 | Stirge | melee | 55 / 0 / 0 | **55 / 0 / 0** | 8 / — / — → 8 / — / — | melee:100 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. |

## Mid High

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 22 | Lesser Demon | magic | 70 / 0 / 78 | **49 / 0 / 70** | 11 / — / 18 → 8 / — / 16 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 11 -> 8 [large delta] magic: 18 -> 16 |
| 135 | Ice Giant | melee | 70 / 0 / 70 | **70 / 0 / 49** | 11 / — / 16 → 11 / — / 12 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 16 -> 12 [large delta] |
| 181 | Lesser Demon | magic | 70 / 0 / 78 | **49 / 0 / 70** | 11 / — / 18 → 8 / — / 16 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 11 -> 8 [large delta] magic: 18 -> 16 |
| 184 | Greater Demon | magic | 77 / 0 / 86 | **53 / 0 / 77** | 12 / — / 20 → 8 / — / 18 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 12 -> 8 [large delta] magic: 20 -> 18 |
| 249 | Pack leader | melee | 71 / 0 / 0 | **71 / 0 / 0** | 11 / — / — → 11 / — / — | melee:100 |  |
| 266 | Lord Darquarius | melee | 78 / 0 / 0 | **78 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 298 | Otherworldly being | magic | 66 / 0 / 66 | **0 / 0 / 66** | 10 / — / 15 → — / — / 15 | magic:100 | melee: disabled (old raw ceiling10) |
| 323 | Paladin | melee | 88 / 0 / 88 | **88 / 0 / 61** | 13 / — / 20 → 13 / — / 14 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 20 -> 14 [large delta] |
| 324 | Hero | melee | 88 / 0 / 0 | **88 / 0 / 0** | 13 / — / — → 13 / — / — | melee:100 |  |
| 531 | Ogre chieftan | melee | 90 / 0 / 0 | **90 / 0 / 0** | 13 / — / — → 13 / — / — | melee:100 |  |
| 567 | Salarin the twisted | magic | 68 / 0 / 68 | **0 / 0 / 68** | 10 / — / 15 → — / — / 15 | magic:100 | melee: disabled (old raw ceiling10) |
| 595 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 597 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 598 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 599 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 600 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 602 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 603 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 604 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 605 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 606 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 607 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 608 | Gnome Baller | melee | 70 / 0 / 0 | **70 / 0 / 0** | 11 / — / — → 11 / — / — | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 613 | Nazastarool Zombie | melee | 90 / 0 / 0 | **90 / 0 / 0** | 13 / — / — → 13 / — / — | melee:100 |  |
| 614 | Nazastarool Skeleton | melee | 90 / 0 / 0 | **90 / 0 / 0** | 13 / — / — → 13 / — / — | melee:100 |  |
| 615 | Nazastarool Ghost | magic | 90 / 0 / 95 | **63 / 0 / 90** | 13 / — / 21 → 10 / — / 20 | magic:75, melee:25 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 13 -> 10 magic: 21 -> 20 |
| 632 | Paladin | melee | 88 / 0 / 88 | **88 / 0 / 61** | 13 / — / 20 → 13 / — / 14 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 20 -> 14 [large delta] |
| 633 | Paladin | melee | 88 / 0 / 88 | **88 / 0 / 61** | 13 / — / 20 → 13 / — / 14 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 20 -> 14 [large delta] |
| 641 | Kalrag | melee | 78 / 0 / 0 | **78 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 645 | Othainian | magic | 78 / 0 / 0 | **54 / 0 / 78** | 12 / — / — → 8 / — / 18 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 12 -> 8 [large delta] magic: new enabled attack, raw ceiling18 |
| 647 | Holthion | magic | 78 / 0 / 0 | **54 / 0 / 78** | 12 / — / — → 8 / — / 18 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 12 -> 8 [large delta] magic: new enabled attack, raw ceiling18 |
| 683 | Gorad | melee | 90 / 0 / 0 | **90 / 0 / 0** | 13 / — / — → 13 / — / — | melee:100 |  |
| 697 | Ogre guard | ranged | 90 / 92 / 0 | **63 / 90 / 0** | 13 / 14 / — → 10 / 13 / — | ranged:75, melee:25 | melee: 13 -> 10 ranged: 14 -> 13 |
| 703 | Bedabin Nomad Guard | ranged | 70 / 70 / 0 | **49 / 70 / 0** | 11 / 11 / — → 8 / 11 / — | ranged:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 11 -> 8 [large delta] |
| 757 | kolodion | magic | 70 / 0 / 72 | **70 / 0 / 72** | 11 / — / 16 → 11 / — / 16 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 758 | kolodion | magic | 78 / 0 / 78 | **78 / 0 / 78** | 12 / — / 18 → 12 / — / 18 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 766 | Ungadulu | magic | 75 / 0 / 0 | **52 / 0 / 75** | 11 / — / — → 8 / — / 17 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 11 -> 8 [large delta] magic: new enabled attack, raw ceiling17 |
| 767 | Ungadulu | magic | 75 / 0 / 0 | **52 / 0 / 75** | 11 / — / — → 8 / — / 17 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 11 -> 8 [large delta] magic: new enabled attack, raw ceiling17 |
| 768 | Death Wing | melee | 80 / 0 / 0 | **80 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 772 | Viyeldi | magic | 80 / 0 / 0 | **56 / 0 / 80** | 12 / — / — → 9 / — / 18 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 12 -> 9 [large delta] magic: new enabled attack, raw ceiling18 |
| 776 | Jungle Savage | melee | 100 / 0 / 0 | **100 / 0 / 0** | 15 / — / — → 15 / — / — | melee:100 |  |
| 798 | Gaia | magic | 79 / 0 / 0 | **55 / 0 / 79** | 12 / — / — → 8 / — / 18 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. melee: 12 -> 8 [large delta] magic: new enabled attack, raw ceiling18 |
| 867 | Terror dog | melee | 80 / 0 / 0 | **80 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. |
| 868 | Bloodveld | melee | 80 / 0 / 0 | **80 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. |

## High

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 196 | Dragon | melee | 110 / 0 / 110 | **110 / 0 / 88** | 16 / — / 25 → 16 / — / 20 | melee:75, magic:25 | magic: 25 -> 20 |
| 201 | Red Dragon | melee | 98 / 0 / 140 | **98 / 0 / 78** | 15 / — / 32 → 15 / — / 18 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 32 -> 18 [large delta] |
| 202 | Blue Dragon | melee | 91 / 0 / 105 | **91 / 0 / 72** | 14 / — / 24 → 14 / — / 16 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 24 -> 16 [large delta] |
| 216 | tree spirit | melee | 105 / 0 / 105 | **105 / 0 / 84** | 16 / — / 24 → 16 / — / 19 | melee:75, magic:25 | magic: 24 -> 19 |
| 254 | Ice queen | magic | 104 / 0 / 105 | **104 / 0 / 104** | 15 / — / 24 → 15 / — / 23 | magic:75, melee:25 | Owner exception: both enabled powers are100% original Strength104. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. magic: 24 -> 23 |
| 294 | Hellhound | melee | 114 / 0 / 0 | **114 / 0 / 0** | 17 / — / — → 17 / — / — | melee:100 |  |
| 315 | Chronozon | magic | 182 / 0 / 183 | **145 / 0 / 182** | 27 / — / 42 → 21 / — / 42 | magic:75, melee:25 | Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 27 -> 21 |
| 344 | Fire Giant | melee | 105 / 0 / 110 | **105 / 0 / 84** | 16 / — / 25 → 16 / — / 19 | melee:75, magic:25 | magic: 25 -> 19 |
| 383 | General Khazard | melee | 78 / 0 / 0 | **78 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 388 | Bouncer | melee | 130 / 0 / 0 | **130 / 0 / 0** | 19 / — / — → 19 / — / — | melee:100 |  |
| 410 | khazard warlord | melee | 78 / 0 / 0 | **78 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 426 | Lord hazeel | melee | 78 / 0 / 0 | **78 / 0 / 0** | 12 / — / — → 12 / — / — | melee:100 |  |
| 646 | Doomion | magic | 98 / 0 / 0 | **78 / 0 / 98** | 15 / — / — → 12 / — / 23 | magic:75, melee:25 | melee: 15 -> 12 magic: new enabled attack, raw ceiling23 |
| 663 | San Tojalon | melee | 120 / 0 / 0 | **120 / 0 / 96** | 18 / — / — → 18 / — / 21 | melee:75, magic:25 | magic: new enabled attack, raw ceiling21 |
| 684 | Ogre guard | ranged | 90 / 98 / 0 | **72 / 90 / 0** | 13 / 15 / — → 11 / 13 / — | ranged:75, melee:25 | melee: 13 -> 11 ranged: 15 -> 13 |
| 760 | kolodion | melee | 98 / 0 / 105 | **98 / 0 / 105** | 15 / — / 24 → 15 / — / 24 | preserve | Minigame/role hold: keep raw getters and all existing behavior. Nonzero incidental powers are not permission to enable attacks. |
| 761 | Irvig Senay | melee | 125 / 0 / 0 | **125 / 0 / 100** | 18 / — / — → 18 / — / 23 | melee:75, magic:25 | magic: new enabled attack, raw ceiling23 |
| 762 | Ranalph Devere | melee | 130 / 0 / 0 | **130 / 0 / 104** | 19 / — / — → 19 / — / 23 | melee:75, magic:25 | magic: new enabled attack, raw ceiling23 |
| 862 | Green Dragon | melee | 98 / 0 / 110 | **98 / 0 / 78** | 15 / — / 25 → 15 / — / 18 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 25 -> 18 [large delta] |
| 869 | Dark beast | magic | 110 / 0 / 210 | **110 / 0 / 210** | 16 / — / 48 → 16 / — / 48 | preserve | Retain existing authored powers and scripted attack owner; do not normalize by secondary ratios or add parallel generic attacks. |
| 870 | Abyssal demon | magic | 125 / 0 / 0 | **100 / 0 / 125** | 18 / — / — → 15 / — / 28 | magic:75, melee:25 | Existing explicit reference retained instead of legacy placeholder; secondary powers still follow the band where this is an ordinary mixed assignment. melee: 18 -> 15 magic: new enabled attack, raw ceiling28 |
| 877 | Sapphire Dragon | melee | 90 / 0 / 100 | **90 / 0 / 72** | 13 / — / 23 → 13 / — / 16 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 23 -> 16 [large delta] |
| 878 | Emerald Dragon | melee | 110 / 0 / 122 | **110 / 0 / 88** | 16 / — / 28 → 16 / — / 20 | melee:75, magic:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. magic: 28 -> 20 [large delta] |

## Very High

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 290 | Black Demon | magic | 91 / 0 / 155 | **81 / 0 / 91** | 14 / — / 35 → 12 / — / 21 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 14 -> 12 magic: 35 -> 21 [large delta] |
| 291 | Black Dragon | melee | 182 / 0 / 210 | **182 / 0 / 163** | 27 / — / 48 → 27 / — / 37 | melee:75, magic:25 | magic: 48 -> 37 |
| 401 | Black Knight titan | melee | 148 / 0 / 148 | **148 / 0 / 133** | 22 / — / 34 → 22 / — / 31 | melee:90, magic:10 | Melee knight: keep supporting holy casts rare rather than ordinary75/25. magic: 34 -> 31 |
| 568 | Black Demon | magic | 91 / 0 / 195 | **81 / 0 / 91** | 14 / — / 44 → 12 / — / 21 | magic:75, melee:25 | Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. melee: 14 -> 12 magic: 44 -> 21 [large delta] |
| 705 | Rock of ages | melee | 150 / 0 / 0 | **150 / 0 / 0** | 22 / — / — → 22 / — / — | melee:100 |  |
| 769 | Nezikchened | magic | 178 / 0 / 0 | **160 / 0 / 178** | 26 / — / — → 23 / — / 40 | magic:75, melee:25 | melee: 26 -> 23 magic: new enabled attack, raw ceiling40 |
| 802 | Greatwood | melee | 245 / 0 / 0 | **245 / 0 / 0** | 36 / — / — → 36 / — / — | melee:100 |  |
| 879 | Ruby Dragon | melee | 132 / 0 / 146 | **132 / 0 / 118** | 19 / — / 33 → 19 / — / 26 | melee:75, magic:25 | magic: 33 -> 26 |
| 880 | Diamond Dragon | melee | 153 / 0 / 174 | **153 / 0 / 137** | 22 / — / 39 → 22 / — / 31 | melee:75, magic:25 | magic: 39 -> 31 |
| 881 | Dragonstone Dragon | melee | 178 / 0 / 202 | **178 / 0 / 160** | 26 / — / 45 → 26 / — / 36 | melee:75, magic:25 | magic: 45 -> 36 |

## Boss

| ID | NPC | Primary | Current Power M/R/G | Assigned Power M/R/G | Raw ceilings before → after M/R/G | Weights | Notes |
| ---: | --- | --- | --- | --- | --- | --- | --- |
| 477 | King Black Dragon | melee | 224 / 0 / 250 | **224 / 0 / 201** | 33 / — / 56 → 33 / — / 45 | melee:75, magic:25 | KBD: full Strength-reference melee,90% supporting Magic. Breath remains separate. Magic no longer inherits Attack250. magic: 56 -> 45 |
| 809 | Balrog | magic | 250 / 0 / 999 | **250 / 0 / 250** | 36 / — / 226 → 36 / — / 56 | magic:75, melee:25 | Balrog: retain melee250, deliberately replace Attack999-derived Magic with250. Boss exception gives both active styles full reference; primary remains Magic by selection. Large raw-ceiling delta: review deliberate correction/secondary reassignment before installation; not proof of the same DPS. Old Attack exceeded Strength; proposed Magic deliberately uses the Strength reference. Inspect ceiling change rather than copying the old fallback. magic: 226 -> 56 [large delta] |
| 844 | Elder Green Dragon | magic | 250 / 235 / 270 | **250 / 235 / 270** | 36 / 34 / 61 → 36 / 34 / 61 | preserve | Retain existing authored powers and scripted attack owner; do not normalize by secondary ratios or add parallel generic attacks. |
