# Key Commands

These are dev-only commands for placing respawning NPC enemies in-game and turning them into official MyWorld map content.

- `::cnpc <id> <radius>`: spawns a respawning NPC on your current tile and queues that spawn for saving.
- `::cnpc <id> <radius> <x> <y>`: spawns a respawning NPC at explicit coordinates and queues that spawn for saving.
- `::rpc <npc_instance_id>`: removes a live NPC and queues its spawn point for persistent removal.
- `::worldedits`: lists pending scenery and NPC edits that have not been written to disk yet.
- `::saveworldedits`: writes pending NPC edits to `MyWorldNpcLocs.json` and `MyWorldNpcRemovals.json`, alongside any queued scenery edits.
- `::clearworldedits`: discards the pending save queue without reverting the live world.
- `::coords`: prints your current coordinates.
- `::tile`: prints tile traversal/debug data for your current tile.

Notes:
- Use `radius` to define how far the NPC can wander from its spawn tile. `0` pins it to the tile; `5` gives a normal small patrol box.
- `::cnpc` creates a live NPC immediately, marks it for respawn, and queues the spawn point for saving.
- Type `::dev` while logged into a developer account to enable the developer right-click menu. Right-clicking an NPC then exposes `Remove NPC`, which sends `::rpc` with the live instance ID automatically.
- If you need to read the live instance ID manually, use `::debugregion false true false false`; NPC rows are printed as `[NPC:<instance_id>:<name> @ (x, y)]`.
- NPC Examine currently shows the NPC definition ID, not the live instance ID.
- `::clearworldedits` only clears queued file changes. Restarting the server is the clean way to undo unsaved live NPCs.
- This reference intentionally lists only NPC definitions where `attackable` is enabled.

# Enemy NPC IDs

This is a builder-facing reference for attackable NPC IDs used when placing enemies in-game.
It mirrors `object-ids.md` but is restricted to NPCs that can actually be attacked.

Primary sources:
- NPC definitions: `server/conf/server/defs/NpcDefs.json`
- NPC spawn persistence: `server/conf/server/defs/locs/MyWorldNpcLocs.json`
- NPC spawn removals: `server/conf/server/defs/locs/MyWorldNpcRemovals.json`

## Low-Level Enemies

Combat levels `0-20`. Use these IDs with `::cnpc <id> <radius>`.

| ID | Name | Level | HP | Aggro | Members | Respawn |
| ---: | --- | ---: | ---: | --- | --- | ---: |
| 550 | charlie | 0 | 3 | No | Yes | 30 |
| 29 | Rat | 2 | 2 | No | No | 74 |
| 34 | spider | 2 | 2 | No | No | 82 |
| 3 | Chicken | 3 | 3 | No | No | 63 |
| 583 | Gnome child | 3 | 3 | No | Yes | 74 |
| 585 | Gnome child | 3 | 3 | No | Yes | 74 |
| 586 | Gnome child | 3 | 3 | No | Yes | 74 |
| 593 | Gnome local | 3 | 3 | No | Yes | 74 |
| 409 | gnome troop | 3 | 3 | No | Yes | 74 |
| 5 | Hans | 3 | 3 | No | No | 74 |
| 402 | kalron | 3 | 3 | No | Yes | 75 |
| 399 | local gnome | 3 | 3 | No | Yes | 74 |
| 114 | Imp | 5 | 8 | No | No | 251 |
| 252 | Firebird | 6 | 5 | No | Yes | 87 |
| 62 | Goblin | 7 | 5 | No | No | 88 |
| 109 | Greldo | 7 | 5 | No | No | 30 |
| 473 | Rat | 7 | 3 | No | No | 71 |
| 192 | Wormbrain | 7 | 5 | No | No | 88 |
| 6 | cow | 8 | 8 | No | No | 101 |
| 23 | Giant Spider | 8 | 5 | No | No | 75 |
| 19 | Rat | 8 | 5 | No | No | 71 |
| 592 | Gnome local | 9 | 9 | No | Yes | 74 |
| 11 | Man | 9 | 7 | No | No | 61 |
| 72 | Man | 9 | 7 | No | No | 61 |
| 318 | Man | 9 | 7 | No | Yes | 60 |
| 439 | Citizen | 10 | 13 | No | Yes | 63 |
| 25 | Jonny the beard | 10 | 8 | No | No | 79 |
| 21 | mugger | 10 | 8 | Yes | No | 73 |
| 438 | Citizen | 11 | 13 | No | Yes | 63 |
| 440 | Citizen | 12 | 13 | No | Yes | 63 |
| 713 | kolodion | 12 | 3 | No | Yes | 30 |
| 57 | Darkwizard | 13 | 12 | Yes | No | 76 |
| 4 | Goblin | 13 | 12 | No | No | 84 |
| 153 | Goblin | 13 | 12 | No | No | 74 |
| 154 | Goblin | 13 | 12 | No | No | 74 |
| 89 | Highwayman | 13 | 13 | Yes | No | 248 |
| 93 | Monk | 13 | 15 | No | No | 61 |
| 47 | Rat | 13 | 10 | Yes | No | 74 |
| 177 | Rat | 13 | 10 | Yes | No | 71 |
| 630 | Blessed Vermen | 14 | 30 | No | Yes | 73 |
| 442 | Citizen | 15 | 10 | No | Yes | 63 |
| 63 | farmer | 15 | 12 | No | No | 61 |
| 319 | farmer | 15 | 12 | No | Yes | 60 |
| 76 | Barbarian | 16 | 14 | No | No | 71 |
| 367 | Dungeon Rat | 16 | 12 | Yes | Yes | 125 |
| 671 | Mining Slave | 16 | 16 | No | Yes | 61 |
| 718 | Rowdy Slave | 16 | 16 | Yes | Yes | 61 |
| 634 | slave | 16 | 16 | No | Yes | 61 |
| 635 | slave | 16 | 16 | No | Yes | 61 |
| 636 | slave | 16 | 16 | No | Yes | 61 |
| 637 | slave | 16 | 16 | No | Yes | 61 |
| 638 | slave | 16 | 16 | No | Yes | 61 |
| 639 | slave | 16 | 16 | No | Yes | 61 |
| 640 | slave | 16 | 16 | No | Yes | 61 |
| 644 | Souless | 16 | 16 | No | Yes | 123 |
| 81 | Wizard | 16 | 14 | No | No | 72 |
| 729 | civillian | 18 | 19 | No | Yes | 63 |
| 94 | Dwarf | 18 | 16 | No | No | 61 |
| 694 | Dwarf | 18 | 16 | No | No | 61 |
| 699 | Dwarf | 18 | 16 | No | No | 61 |
| 251 | Thug | 18 | 18 | Yes | Yes | 81 |
| 86 | Warrior | 18 | 19 | No | No | 61 |
| 270 | Chaos Druid | 19 | 20 | Yes | Yes | 88 |
| 660 | Goblin | 19 | 16 | Yes | Yes | 86 |
| 658 | Iban disciple | 19 | 20 | No | Yes | 61 |
| 140 | Monk of Zamorak | 19 | 20 | No | No | 61 |
| 50 | skeleton | 19 | 18 | No | No | 84 |
| 52 | zombie | 19 | 22 | No | No | 86 |
| 441 | Citizen | 20 | 23 | No | Yes | 63 |
| 425 | cult member | 20 | 20 | No | Yes | 49 |
| 338 | zoo keeper | 20 | 20 | No | Yes | 74 |

## Mid-Level Enemies

Combat levels `21-50`. Use these IDs with `::cnpc <id> <radius>`.

| ID | Name | Level | HP | Aggro | Members | Respawn |
| ---: | --- | ---: | ---: | --- | --- | ---: |
| 199 | Dark Warrior | 21 | 17 | No | No | 74 |
| 348 | Forester | 21 | 17 | No | Yes | 63 |
| 364 | Lucien | 21 | 17 | No | Yes | 63 |
| 342 | Rogue | 21 | 17 | No | Yes | 71 |
| 70 | Scorpion | 21 | 17 | Yes | No | 61 |
| 40 | skeleton | 21 | 17 | No | No | 84 |
| 498 | skeleton mage | 21 | 17 | Yes | Yes | 64 |
| 64 | Thief | 21 | 17 | No | No | 61 |
| 351 | Thief | 21 | 17 | Yes | Yes | 64 |
| 0 | Unicorn | 21 | 19 | No | No | 211 |
| 656 | Dungeon spider | 22 | 35 | Yes | Yes | 30 |
| 495 | Mourner | 22 | 19 | No | Yes | 12 |
| 562 | Gnome guard | 23 | 23 | Yes | Yes | 74 |
| 8 | Bear | 24 | 25 | No | No | 124 |
| 244 | shapeshifter | 24 | 21 | No | Yes | 12 |
| 655 | Souless | 24 | 24 | Yes | Yes | 84 |
| 516 | target practice zombie | 24 | 24 | No | Yes | 84 |
| 41 | zombie | 24 | 24 | Yes | No | 84 |
| 359 | zombie | 24 | 24 | Yes | Yes | 86 |
| 60 | Darkwizard | 25 | 24 | Yes | No | 73 |
| 53 | Ghost | 25 | 25 | Yes | No | 97 |
| 80 | Ghost | 25 | 25 | No | No | 97 |
| 178 | Ghost | 25 | 25 | Yes | No | 97 |
| 417 | happy peasant | 25 | 22 | No | Yes | 42 |
| 502 | Mourner | 25 | 25 | No | Yes | 74 |
| 46 | skeleton | 25 | 24 | Yes | No | 84 |
| 416 | unhappy peasant | 25 | 22 | No | Yes | 42 |
| 37 | Weaponsmaster | 25 | 20 | No | No | 88 |
| 79 | Witch | 25 | 10 | No | No | 59 |
| 188 | Bear | 26 | 27 | Yes | No | 61 |
| 271 | Poison Scorpion | 26 | 23 | Yes | Yes | 61 |
| 582 | Gnome guard | 27 | 31 | No | Yes | 74 |
| 137 | Pirate | 27 | 20 | Yes | No | 61 |
| 159 | Warrior | 27 | 20 | No | No | 61 |
| 320 | Warrior | 27 | 20 | No | Yes | 60 |
| 420 | carnillean guard | 28 | 22 | No | Yes | 74 |
| 65 | Guard | 28 | 22 | No | No | 60 |
| 100 | Guard | 28 | 22 | No | No | 60 |
| 321 | Guard | 28 | 22 | No | No | 60 |
| 407 | Khazard troop | 28 | 22 | Yes | Yes | 74 |
| 356 | Mountain Dwarf | 28 | 26 | No | Yes | 62 |
| 519 | Soldier | 28 | 22 | No | Yes | 63 |
| 232 | Bandit | 29 | 27 | Yes | No | 253 |
| 234 | Bandit | 29 | 27 | No | No | 253 |
| 200 | Druid | 29 | 30 | No | Yes | 61 |
| 664 | Ghost | 29 | 20 | No | Yes | 99 |
| 139 | Monk of Zamorak | 29 | 30 | No | No | 61 |
| 35 | Delrith | 30 | 7 | No | No | 38 |
| 264 | Pirate | 30 | 23 | No | Yes | 74 |
| 462 | Platform Fisherman | 30 | 30 | No | Yes | 61 |
| 463 | Platform Fisherman | 30 | 30 | No | Yes | 61 |
| 464 | Platform Fisherman | 30 | 30 | No | Yes | 61 |
| 296 | Black Unicorn | 31 | 29 | No | Yes | 74 |
| 721 | Desert Wolf | 31 | 34 | Yes | Yes | 74 |
| 74 | Giant Spider | 31 | 32 | Yes | No | 73 |
| 551 | Gnome guard | 31 | 31 | No | Yes | 74 |
| 45 | skeleton | 31 | 29 | Yes | No | 87 |
| 179 | skeleton | 31 | 29 | Yes | No | 87 |
| 239 | White wolf sentry | 31 | 34 | Yes | Yes | 225 |
| 43 | Giant bat | 32 | 32 | Yes | Yes | 87 |
| 67 | Hobgoblin | 32 | 29 | Yes | No | 84 |
| 777 | Oomlie Bird | 32 | 40 | No | Yes | 61 |
| 717 | Shantay Pass Guard | 32 | 32 | No | Yes | 60 |
| 68 | zombie | 32 | 30 | Yes | No | 88 |
| 180 | zombie | 32 | 30 | Yes | No | 88 |
| 214 | zombie | 32 | 30 | Yes | Yes | 84 |
| 574 | Yanille Watchman | 33 | 22 | No | Yes | 30 |
| 352 | Head Thief | 34 | 37 | Yes | Yes | 61 |
| 127 | Jailguard | 34 | 32 | No | No | 74 |
| 358 | Necromancer | 34 | 40 | No | Yes | 62 |
| 631 | Blessed Spider | 35 | 32 | No | Yes | 72 |
| 786 | Pit Scorpion | 35 | 32 | Yes | Yes | 59 |
| 99 | Deadly Red spider | 36 | 35 | Yes | No | 110 |
| 136 | King Scorpion | 36 | 30 | Yes | No | 63 |
| 61 | Giant | 37 | 35 | Yes | No | 73 |
| 78 | Gunthor the Brave | 37 | 35 | No | No | 61 |
| 237 | Black Heather | 39 | 37 | No | No | 253 |
| 236 | Donny the lad | 39 | 37 | No | No | 253 |
| 670 | Mercenary | 39 | 48 | No | Yes | 74 |
| 690 | Mercenary | 39 | 48 | No | Yes | 74 |
| 692 | Mercenary | 39 | 48 | No | Yes | 74 |
| 238 | Speedy Keith | 39 | 37 | No | No | 253 |
| 421 | Tribesman | 39 | 39 | Yes | Yes | 63 |
| 428 | Khazard commander | 41 | 22 | Yes | Yes | 187 |
| 248 | White wolf | 41 | 44 | Yes | Yes | 220 |
| 96 | Count Draynor | 43 | 35 | No | No | 30 |
| 555 | Chaos Druid warrior | 44 | 40 | Yes | Yes | 61 |
| 557 | Shipyard worker | 44 | 40 | No | Yes | 72 |
| 558 | Shipyard worker | 44 | 40 | No | Yes | 62 |
| 559 | Shipyard worker | 44 | 40 | No | Yes | 62 |
| 182 | Melzar the mad | 45 | 44 | Yes | No | 75 |
| 653 | Ugthanki | 45 | 45 | No | Yes | 86 |
| 295 | Animated axe | 46 | 44 | Yes | Yes | 61 |
| 66 | Black Knight | 46 | 42 | No | No | 61 |
| 108 | Black Knight | 46 | 42 | No | No | 61 |
| 189 | Black Knight | 46 | 42 | Yes | No | 61 |
| 259 | Grip | 46 | 62 | No | Yes | 62 |
| 262 | Guard Dog | 46 | 49 | Yes | Yes | 61 |
| 386 | Khazard Scorpion | 46 | 40 | Yes | Yes | 30 |
| 759 | kolodion | 46 | 78 | No | Yes | 30 |
| 521 | Jungle Spider | 47 | 50 | Yes | Yes | 75 |
| 293 | Monk of Zamorak | 47 | 40 | Yes | Yes | 74 |
| 702 | Captain Siad | 48 | 48 | No | Yes | 61 |
| 651 | Goblin guard | 48 | 43 | No | Yes | 62 |
| 311 | Hobgoblin | 48 | 49 | Yes | No | 84 |
| 203 | Baby Blue Dragon | 50 | 50 | Yes | Yes | 73 |
| 710 | Draft Mercenary Guard | 50 | 60 | No | Yes | 74 |
| 668 | Mercenary | 50 | 60 | No | Yes | 74 |
| 716 | Rowdy Guard | 50 | 60 | Yes | Yes | 61 |

## High-Level Enemies

Combat levels `51-90`. Use these IDs with `::cnpc <id> <radius>`.

| ID | Name | Level | HP | Aggro | Members | Respawn |
| ---: | --- | ---: | ---: | --- | --- | ---: |
| 518 | Colonel Radick | 51 | 65 | No | Yes | 74 |
| 265 | Jailer | 51 | 47 | Yes | Yes | 61 |
| 277 | Renegade knight | 51 | 48 | Yes | Yes | 61 |
| 789 | Battle mage | 52 | 120 | No | Yes | 75 |
| 790 | Battle mage | 52 | 120 | No | Yes | 75 |
| 791 | Battle mage | 52 | 120 | No | Yes | 75 |
| 584 | Earth warrior | 52 | 54 | Yes | Yes | 73 |
| 343 | Shadow spider | 53 | 55 | Yes | Yes | 105 |
| 195 | skeleton | 54 | 59 | Yes | No | 86 |
| 322 | Knight | 56 | 52 | No | Yes | 73 |
| 102 | White Knight | 56 | 52 | No | No | 63 |
| 158 | Ice warrior | 57 | 59 | Yes | No | 75 |
| 523 | Jogre | 58 | 60 | Yes | Yes | 73 |
| 384 | Khazard Ogre | 58 | 60 | Yes | Yes | 30 |
| 312 | Ogre | 58 | 60 | Yes | Yes | 74 |
| 525 | Ogre | 58 | 60 | No | Yes | 75 |
| 706 | Ogre | 58 | 60 | No | Yes | 74 |
| 704 | Ogre citizen | 58 | 60 | No | Yes | 74 |
| 276 | Sir Mordred | 58 | 54 | No | Yes | 62 |
| 190 | chaos Dwarf | 59 | 61 | Yes | No | 362 |
| 775 | Karamja Wolf | 61 | 61 | Yes | Yes | 225 |
| 104 | Moss Giant | 62 | 60 | Yes | No | 73 |
| 594 | Moss Giant | 62 | 60 | Yes | No | 73 |
| 542 | UndeadOne | 62 | 59 | Yes | Yes | 74 |
| 292 | Poison Spider | 63 | 64 | Yes | Yes | 73 |
| 361 | The Fire warrior of lesarkus | 63 | 59 | Yes | Yes | 49 |
| 243 | Grey wolf | 64 | 69 | Yes | Yes | 216 |
| 263 | Ice spider | 64 | 65 | Yes | Yes | 75 |
| 669 | Mercenary Captain | 64 | 80 | No | Yes | 74 |
| 787 | Shadow Warrior | 64 | 67 | Yes | Yes | 59 |
| 757 | kolodion | 65 | 65 | No | Yes | 30 |
| 298 | Otherworldly being | 66 | 66 | No | Yes | 74 |
| 135 | Ice Giant | 68 | 70 | Yes | No | 75 |
| 758 | kolodion | 68 | 78 | No | Yes | 30 |
| 567 | Salarin the twisted | 69 | 70 | Yes | Yes | 61 |
| 703 | Bedabin Nomad Guard | 70 | 70 | No | Yes | 74 |
| 595 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 597 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 598 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 599 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 600 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 602 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 603 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 604 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 605 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 606 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 607 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 608 | Gnome Baller | 70 | 70 | No | Yes | 30 |
| 249 | Pack leader | 71 | 74 | Yes | Yes | 225 |
| 323 | Paladin | 71 | 57 | No | Yes | 60 |
| 632 | Paladin | 71 | 57 | No | Yes | 36 |
| 633 | Paladin | 71 | 57 | No | Yes | 36 |
| 766 | Ungadulu | 75 | 75 | No | Yes | 30 |
| 767 | Ungadulu | 75 | 75 | No | Yes | 30 |
| 266 | Lord Darquarius | 76 | 72 | Yes | Yes | 73 |
| 683 | Gorad | 78 | 80 | No | Yes | 1 |
| 647 | Holthion | 78 | 78 | Yes | Yes | 75 |
| 641 | Kalrag | 78 | 78 | No | Yes | 72 |
| 531 | Ogre chieftan | 78 | 80 | Yes | Yes | 73 |
| 697 | Ogre guard | 78 | 80 | No | Yes | 84 |
| 645 | Othainian | 78 | 78 | Yes | Yes | 75 |
| 22 | Lesser Demon | 79 | 79 | Yes | No | 75 |
| 181 | Lesser Demon | 79 | 79 | Yes | No | 75 |
| 768 | Death Wing | 80 | 80 | Yes | Yes | 74 |
| 772 | Viyeldi | 80 | 80 | No | Yes | 30 |
| 324 | Hero | 83 | 82 | No | Yes | 60 |
| 615 | Nazastarool Ghost | 83 | 80 | No | Yes | 30 |
| 614 | Nazastarool Skeleton | 83 | 80 | No | Yes | 30 |
| 613 | Nazastarool Zombie | 83 | 80 | No | Yes | 30 |
| 184 | Greater Demon | 87 | 87 | Yes | No | 75 |
| 776 | Jungle Savage | 87 | 90 | No | Yes | 61 |

## Elite And Boss Enemies

Combat levels `91-999`. Use these IDs with `::spawnnpc <id> <radius>`.

| ID | Name | Level | HP | Aggro | Members | Respawn |
| ---: | --- | ---: | ---: | --- | --- | ---: |
| 216 | tree spirit | 95 | 85 | No | Yes | 30 |
| 684 | Ogre guard | 96 | 99 | No | Yes | 74 |
| 646 | Doomion | 98 | 98 | Yes | Yes | 75 |
| 760 | kolodion | 98 | 107 | No | Yes | 30 |
| 383 | General Khazard | 100 | 170 | No | Yes | 61 |
| 410 | khazard warlord | 100 | 170 | No | Yes | 62 |
| 426 | Lord hazeel | 100 | 170 | No | Yes | 30 |
| 254 | Ice queen | 103 | 104 | Yes | Yes | 72 |
| 202 | Blue Dragon | 105 | 105 | Yes | Yes | 73 |
| 344 | Fire Giant | 109 | 111 | Yes | Yes | 73 |
| 196 | Dragon (Elvarg) | 110 | 110 | Yes | No | 75 |
| 862 | Green Dragon | 110 | 110 | Yes | No | 75 |
| 294 | Hellhound | 114 | 116 | Yes | Yes | 75 |
| 663 | San Tojalon | 120 | 120 | Yes | Yes | 53 |
| 315 | Chronozon | 121 | 60 | Yes | No | 75 |
| 388 | Bouncer | 122 | 116 | Yes | Yes | 30 |
| 761 | Irvig Senay | 125 | 125 | Yes | Yes | 53 |
| 762 | Ranalph Devere | 130 | 130 | Yes | Yes | 53 |
| 201 | Red Dragon | 140 | 140 | Yes | Yes | 73 |
| 401 | Black Knight titan | 146 | 142 | No | Yes | 62 |
| 705 | Rock of ages | 150 | 150 | No | Yes | 30 |
| 290 | Black Demon | 156 | 157 | Yes | Yes | 73 |
| 769 | Nezikchened | 172 | 160 | No | Yes | 30 |
| 568 | Black Demon | 175 | 160 | Yes | Yes | 30 |
| 291 | Black Dragon | 200 | 190 | Yes | Yes | 73 |
| 477 | King Black Dragon | 245 | 240 | Yes | Yes | 75 |
| 844 | Elder Green Dragon | 275 | 280 | Yes | Yes | 75 |

## Placement Guidance

- For tight dungeon rooms, start with radius `0-2` so NPCs do not drift into walls or doorways.
- For camps, wilderness clusters, and open fields, radius `4-8` usually feels natural.
- For quest or display NPCs that should be fought in place, use radius `0` and verify their combat pathing after respawn.
- Aggressive enemies can start combat immediately when players enter range; keep them away from spawn points, banks, and narrow travel choke points unless that pressure is intentional.
- Members-only enemies should only be used where member content is intended to be available.
