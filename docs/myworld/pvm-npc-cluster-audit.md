# PvM NPC Cluster Audit

Generated from active `server/myworld.conf` NPC location files with:

```bash
python3 tools/myworld/audit-npc-clusters.py --limit 30
python3 tools/myworld/audit-npc-clusters.py --wilderness-only --min-size 1
```

- Cluster radius: `8` tiles
- Minimum cluster size: `3` hostile NPCs
- Hostile means `attackable` in the NPC definition; aggressive count is shown separately.

| # | Bounds | Count | Aggressive | Levels | NPCs | Source Files |
| - | - | -: | -: | - | - | - |
| 1 | `627,628 to 670,688` | 122 | 55 | 2-41 | Khazard troop `407` x52, gnome troop `409` x42, Goblin `4` x13, Rat `29` x7, kalron `402` x3, Bear `8` x1, Rat `47` x1, local gnome `399` x1, Giant `61` x1, Khazard commander `428` x1 | NpcLocs.json x122 |
| 2 | `712,563 to 764,669` | 104 | 58 | 2-78 | Souless `655` x31, spider `34` x26, Giant bat `43` x20, Goblin `4` x9, Blessed Spider `631` x9, Grey wolf `243` x7, Iban disciple `658` x1, Kalrag `641` x1 | NpcLocs.json x104 |
| 3 | `722,3459 to 784,3500` | 89 | 39 | 13-32 | Blessed Vermen `630` x36, Giant bat `43` x25, skeleton `46` x7, Goblin `4` x5, Souless `655` x4, skeleton `45` x3, slave `634` x2, slave `635` x1, slave `636` x1, slave `637` x1, slave `640` x1, slave `639` x1, slave `638` x1, Unicorn `0` x1 | NpcLocs.json x89 |
| 4 | `674,480 to 750,529` | 57 | 2 | 3-24 | Gnome local `592` x19, Gnome local `593` x17, Gnome child `585` x16, Gnome guard `562` x2, Goblin `4` x2, Bear `8` x1 | NpcLocs.json x57 |
| 5 | `580,3315 to 620,3355` | 51 | 8 | 2-32 | Goblin `4` x16, Rat `29` x11, Goblin `62` x8, Rat `19` x8, Giant bat `43` x4, Rat `47` x4 | NpcLocs.json x51 |
| 6 | `661,564 to 695,583` | 41 | 6 | 2-24 | Rat `29` x31, zombie `41` x6, civillian `729` x2, spider `34` x1, Man `11` x1 | NpcLocs.json x41 |
| 7 | `94,595 to 121,664` | 40 | 0 | 3-15 | Goblin `62` x20, cow `6` x10, Chicken `3` x6, Giant Spider `23` x2, farmer `63` x2 | NpcLocs.json x40 |
| 8 | `51,3605 to 92,3641` | 36 | 16 | 16-50 | Mining Slave `671` x13, Rowdy Slave `718` x9, Rowdy Guard `716` x7, Mercenary `668` x3, Mercenary `692` x2, Mercenary `670` x2 | NpcLocs.json x36 |
| 9 | `650,3272 to 670,3298` | 30 | 24 | 2-109 | skeleton `195` x8, Fire Giant `344` x6, Rat `29` x5, Shadow spider `343` x5, skeleton mage `498` x3, Giant bat `43` x1, skeleton `46` x1, Rat `19` x1 | NpcLocs.json x30 |
| 10 | `703,3411 to 725,3448` | 30 | 6 | 13-71 | Blessed Spider `631` x17, zombie `41` x5, Goblin `4` x4, Paladin `633` x2, Paladin `632` x1, Giant bat `43` x1 | NpcLocs.json x30 |
| 11 | `202,3231 to 233,3256` | 29 | 29 | 19-156 | skeleton `195` x8, Chaos Druid `270` x8, Deadly Red spider `99` x6, Black Demon `290` x3, Poison Spider `292` x3, Chronozon `315` x1 | NpcLocs.json x29 |
| 12 | `625,689 to 668,718` | 28 | 4 | 2-37 | Goblin `4` x15, Rat `47` x3, local gnome `399` x3, Rat `29` x3, kalron `402` x3, Giant `61` x1 | NpcLocs.json x28 |
| 13 | `311,433 to 333,467` | 25 | 0 | 2-29 | Goblin `154` x7, Goblin `153` x5, Rat `29` x5, Monk of Zamorak `140` x4, Monk of Zamorak `139` x2, Bear `8` x2 | NpcLocs.json x25 |
| 14 | `265,2948 to 286,2972` | 24 | 24 | 37-109 | Shadow spider `343` x11, Giant `61` x6, chaos Dwarf `190` x4, Fire Giant `344` x3 | NpcLocs.json x24 |
| 15 | `530,744 to 544,762` | 24 | 24 | 47-47 | Jungle Spider `521` x24 | NpcLocs.json x24 |
| 16 | `302,276 to 330,309` | 23 | 12 | 2-31 | Ghost `53` x6, Rat `29` x6, spider `34` x4, Rat `47` x3, Giant Spider `74` x3, Giant Spider `23` x1 | NpcLocs.json x23 |
| 17 | `320,125 to 335,158` | 23 | 23 | 57-68 | Ice warrior `158` x12, Ice spider `263` x6, Ice Giant `135` x5 | NpcLocs.json x23 |
| 18 | `686,634 to 706,695` | 23 | 0 | 2-29 | Goblin `62` x7, Ghost `664` x5, Monk of Zamorak `139` x4, Monk of Zamorak `140` x3, Rat `29` x2, Bear `8` x1, skeleton `40` x1 | NpcLocs.json x23 |
| 19 | `109,3285 to 164,3306` | 22 | 15 | 2-62 | Deadly Red spider `99` x6, zombie `41` x4, Rat `29` x3, spider `34` x3, skeleton `45` x2, Moss Giant `104` x2, Rat `19` x1, Giant Spider `74` x1 | NpcLocs.json x22 |
| 20 | `625,565 to 653,609` | 22 | 0 | 2-25 | Rat `29` x6, Mourner `502` x4, spider `34` x3, Citizen `442` x2, Citizen `438` x2, Citizen `440` x2, Citizen `441` x2, Citizen `439` x1 | NpcLocs.json x22 |
| 21 | `781,3408 to 813,3435` | 22 | 2 | 16-24 | Souless `644` x20, Souless `655` x2 | NpcLocs.json x22 |
| 22 | `256,296 to 278,324` | 21 | 12 | 8-39 | Bandit `232` x12, Bandit `234` x3, Rat `19` x2, Speedy Keith `238` x1, Giant Spider `23` x1, Black Heather `237` x1, Donny the lad `236` x1 | MyWorldNpcLocs.json x4, NpcLocs.json x17 |
| 23 | `675,3506 to 717,3533` | 21 | 16 | 13-48 | Goblin `660` x14, Goblin `4` x4, Dungeon Rat `367` x2, Goblin guard `651` x1 | NpcLocs.json x21 |
| 24 | `785,3457 to 807,3479` | 21 | 2 | 19-24 | Iban disciple `658` x19, Souless `655` x2 | NpcLocs.json x21 |
| 25 | `80,795 to 102,811` | 20 | 1 | 16-64 | Mercenary `668` x8, Mercenary `670` x4, Mining Slave `671` x3, Mercenary Captain `669` x3, Desert Wolf `721` x1, Ugthanki `653` x1 | NpcLocs.json x20 |
| 26 | `199,3273 to 222,3333` | 20 | 16 | 13-37 | Giant `61` x6, Hobgoblin `67` x5, skeleton `40` x4, Rat `47` x3, zombie `68` x2 | NpcLocs.json x20 |
| 27 | `216,245 to 231,265` | 19 | 19 | 32-48 | Hobgoblin `67` x16, Hobgoblin `311` x3 | MyWorldNpcLocs.json x3, NpcLocs.json x16 |
| 28 | `369,459 to 403,497` | 19 | 8 | 29-71 | Druid `200` x7, White wolf sentry `239` x5, Grey Knight `836` x4, Pack leader `249` x2, White wolf `248` x1 | MyWorldNpcLocs.json x4, NpcLocs.json x15 |
| 29 | `396,739 to 410,764` | 19 | 1 | 44-47 | Shipyard worker `559` x15, Shipyard worker `558` x2, Shipyard worker `557` x1, Jungle Spider `521` x1 | NpcLocs.json x19 |
| 30 | `577,3473 to 594,3484` | 19 | 10 | 13-16 | Dungeon Rat `367` x10, Goblin `4` x9 | NpcLocs.json x19 |

## Wilderness Population Summary

The audit now loads `MyWorldNpcLocs.json` when MyWorld mode is active and can
filter output using the server wilderness-level formula. It also reports total
hostile locations by depth band:

| Wilderness Levels | Base/Other Hostiles | MyWorld Overlay | Total |
| - | -: | -: | -: |
| `1-10` | 81 | 10 | 91 |
| `11-20` | 49 | 13 | 62 |
| `21-30` | 84 | 14 | 98 |
| `31-40` | 22 | 14 | 36 |
| `41-50` | 94 | 13 | 107 |
| `51-60` | 63 | 10 | 73 |

## Expanded Wilderness Pockets

| Bounds | Wilderness Levels | Before | After | NPCs | Source Files |
| - | - | -: | -: | - | - |
| `308,408 to 318,417` | 2-4 | 8 | 12 | Darkwizard `57`, `60` | MyWorldNpcLocs.json x4, NpcLocs.json x8 |
| `164,391 to 176,401` | 5-7 | 6 | 10 | Thug `251` | MyWorldNpcLocs.json x4, NpcLocs.json x6 |
| `207,361 to 219,371` | 10-12 | 2 | 6 | Bear `188` | MyWorldNpcLocs.json x4, NpcLocs.json x2 |
| `247,345 to 267,360` | 12-14 | 9 | 13 | Dark Warrior `199` | MyWorldNpcLocs.json x4, NpcLocs.json x9 |
| `62,306 to 73,326` | 17-21 | 4 | 8 | Ghost `53` | MyWorldNpcLocs.json x4, NpcLocs.json x4 |
| `105,303 to 123,315` | 19-21 | 6 | 10 | Black Unicorn `296` | MyWorldNpcLocs.json x4, NpcLocs.json x6 |
| `165,305 to 187,326` | 17-21 | 15 | 16 | zombie `41`, `68`, new Necromancer `358` | MyWorldNpcLocs.json x1, NpcLocs.json x15 |
| `256,296 to 278,324` | 18-22 | 17 | 21 | Bandit `232`, `234` and local mix | MyWorldNpcLocs.json x4, NpcLocs.json x17 |
| `56,258 to 71,265` | 28-29 | 2 | 6 | Black Knight `189` | MyWorldNpcLocs.json x4, NpcLocs.json x2 |
| `216,245 to 231,265` | 28-31 | 16 | 19 | Hobgoblin `67`, new elite Hobgoblin `311` | MyWorldNpcLocs.json x3, NpcLocs.json x16 |
| `270,234 to 296,251` | 30-33 | 4 | 9 | chaos Dwarf `190` | MyWorldNpcLocs.json x5, NpcLocs.json x4 |
| `104,206 to 117,221` | 35-37 | 3 | 6 | chaos Dwarf `190`, new Chaos Druid warrior `555` | MyWorldNpcLocs.json x3, NpcLocs.json x3 |
| `309,194 to 324,201` | 38-39 | 0 | 4 | new aggressive Monk of Zamorak `293` | MyWorldNpcLocs.json x4 |
| `130,185 to 158,204` | 38-41 | 3 | 7 | Red Dragon `201` | MyWorldNpcLocs.json x4, NpcLocs.json x3 |
| `58,157 to 87,183` | 41-46 | 5 | 16 | Lesser Demon `22`, Greater Demon `184`, new Hellhound `294` | MyWorldNpcLocs.json x11, NpcLocs.json x5 |
| `110,124 to 140,131` | 50-51 | 4 | 8 | Grey wolf `243` | MyWorldNpcLocs.json x4, NpcLocs.json x4 |
| `188,109 to 195,126` | 51-54 | 2 | 6 | Rogue `342` | MyWorldNpcLocs.json x4, NpcLocs.json x2 |
| `205,105 to 230,133` | 50-54 | 13 | 16 | Giant bat `43`, Battle mage `789-791`, new Shadow Warrior `787` | MyWorldNpcLocs.json x3, NpcLocs.json x13 |

## Review Notes

- High-count, high-aggression clusters are the first places to field-test AoE and multi-aggro behavior.
- Low-aggression clusters may still be good PvM pockets if the intended behavior is player-initiated pulls.
- Quest-sensitive areas need manual review before adding density.
- The level `31-40` Wilderness band improved from `29` to `36` hostile
  locations through the altar and chaos pocket additions, but remains the
  thinnest band for later field review.
