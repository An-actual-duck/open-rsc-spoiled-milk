# Fishing Spot Map

This is the current source mapping for live fishing scenery used by `server/conf/server/defs/extras/ObjectFishing.xml`. It is intended as the planning base for the tiered fishing-pole pass.

Clustering uses connected components with a 24-tile proximity threshold. That keeps most local fishing areas together without merging distant coastline regions. Area names below are inferred from nearby NPCs, so coordinates remain the source of truth.

## Totals

- Live fishing scenery entries: 187
- Unique object/tile placements: 174
- Clusters at 24 tiles: 25
- Definition without a live placement: object 557 (`harpoon`/`cage`)
- Duplicate exact placements exist where `SceneryLocs.json` and `SceneryLocs27.json` both define the same spot; duplicates are shown as `x2`.

## Fishing Object Definitions

| Object | Commands | Current catches |
| - | - | - |
| 192 | Lure / Bait | Fly Fishing Rod + Feather: Raw Salmon L37, Raw Trout L25<br>Fishing Rod + Fishing Bait: Raw Pike L31 |
| 193 | Net / Bait | Net: Raw Anchovies L19, Raw Shrimp L1<br>Fishing Rod + Fishing Bait: Raw Herring L13, Raw Sardine L7 |
| 194 | Harpoon / Cage | Harpoon: Raw Swordfish L55, Raw Tuna L43<br>Lobster Pot: Raw Lobster L49 |
| 261 | Net / Harpoon | Big Net: Raw Bass L55, Raw cod L25, Raw Mackerel L16, oyster L16, Casket L16, Boots L16, Cow-hide gloves L16, seaweed L16<br>Harpoon: Raw Shark L70 |
| 271 | Bait / Examine | Oily Fishing Rod + Fishing Bait: Raw lava eel L62 |
| 376 | cage / harpoon | Lobster Pot: Raw Lobster L49<br>Harpoon: Raw Swordfish L55, Raw Tuna L43 |
| 493 | Net / Examine | Net: Raw Shrimp L1 |
| 557 | harpoon / cage | Harpoon: Raw Swordfish L55, Raw Tuna L43<br>Lobster Pot: Raw Lobster L49 |

## Clusters

### 1. Bounds (489,25) to (500,38)

- Center: (494.0, 32.7)
- Entries / unique object-tiles: 3 / 3
- Object mix: 192 x3
- Nearby NPC hints: happy peasant (490,31); maiden (515,33); King Percival (516,35)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 493,25:192; 489,35:192; 500,38:192

### 2. Bounds (254,290) to (259,294)

- Center: (256.5, 292.0)
- Entries / unique object-tiles: 2 / 2
- Object mix: 193 x2
- Nearby NPC hints: Bandit (263,299); Rat (257,303); Fat Tony (269,290); Speedy Keith (270,296); Black Heather (271,300)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19
- Coordinates: 254,290:193; 259,294:193

### 3. Bounds (524,424) to (524,427)

- Center: (524.0, 425.5)
- Entries / unique object-tiles: 4 / 4
- Object mix: 192 x4
- Nearby NPC hints: Bartender (524,451); Man (520,451); Guard (495,418); Banker (502,447); Chicken (499,406)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 524,424:192; 524,425:192; 524,426:192; 524,427:192

### 4. Bounds (736,434) to (764,520)

- Center: (745.4, 474.8)
- Entries / unique object-tiles: 37 / 37
- Object mix: 192 x32, 193 x1, 194 x4
- Nearby NPC hints: Gnome local (750,480); Goblin (748,482); Gnome child (735,480); Gnome Baller (741,456); Cheerleader (738,456)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19, Raw Trout L25, Raw Pike L31, Raw Salmon L37, Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 762,434:192; 760,435:192; 759,438:192; 757,445:192; 760,446:194; 760,448:194; 764,450:192; 758,451:192; 761,453:192; 756,454:192; 754,461:192; 745,463:193; 743,464:192; 747,464:192; 742,465:192; 740,466:192; 738,468:192; 739,469:192; 736,471:192; 739,471:192; 736,472:194; 736,473:192; 739,474:192; 737,475:192; 738,477:192; 741,479:192; 741,481:192; 737,496:192; 741,505:192; 739,510:192; 744,511:192; 737,512:192; 736,514:194; 744,514:192; 737,518:192; 738,519:192; 740,520:192

### 5. Bounds (657,473) to (663,473)

- Center: (660.0, 473.0)
- Entries / unique object-tiles: 7 / 7
- Object mix: 192 x7
- Nearby NPC hints: hudon (664,464); Gnome local (674,484); hadley (657,491); Almera (656,448); gerald (654,500)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 657,473:192; 658,473:192; 659,473:192; 660,473:192; 661,473:192; 662,473:192; 663,473:192

### 6. Bounds (585,496) to (596,505)

- Center: (588.5, 500.1)
- Entries / unique object-tiles: 10 / 10
- Object mix: 194 x3, 261 x5, 376 x2
- Nearby NPC hints: Padik (603,502); Orven (604,501); Goblin (605,499); Big Dave (570,500); Joshua (570,503)
- Catch coverage: Boots L16, Casket L16, Cow-hide gloves L16, Raw Mackerel L16, oyster L16, seaweed L16, Raw cod L25, Raw Tuna L43, Raw Lobster L49, Raw Bass L55, Raw Swordfish L55, Raw Shark L70
- Coordinates: 585,496:261; 588,496:261; 585,498:261; 588,498:261; 588,500:376; 589,501:376; 593,501:261; 596,501:194; 585,505:194; 588,505:194

### 7. Bounds (645,499) to (661,524)

- Center: (655.0, 509.7)
- Entries / unique object-tiles: 7 / 7
- Object mix: 192 x7
- Nearby NPC hints: gerald (654,500); hadley (657,491); Gnome child (674,511); Moss Giant (635,511); Ogre (664,531)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 659,499:192; 661,502:192; 654,503:192; 661,504:192; 651,518:192; 654,518:192; 645,524:192

### 8. Bounds (398,500) to (418,507)

- Center: (406.6, 503.7)
- Entries / unique object-tiles: 7 / 7
- Object mix: 193 x2, 194 x1, 261 x4
- Nearby NPC hints: White wolf sentry (395,489); Harry (418,487); Hickton (427,489); Gaius (379,501); Firebird (404,532)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Boots L16, Casket L16, Cow-hide gloves L16, Raw Mackerel L16, oyster L16, seaweed L16, Raw Anchovies L19, Raw cod L25, Raw Tuna L43, Raw Lobster L49, Raw Bass L55, Raw Swordfish L55, Raw Shark L70
- Coordinates: 418,500:193; 414,502:193; 399,503:261; 409,504:194; 398,505:261; 406,505:261; 402,507:261

### 9. Bounds (208,501) to (212,507)

- Center: (210.0, 504.0)
- Entries / unique object-tiles: 4 / 2
- Object mix: 192 x4
- Nearby NPC hints: Goblin (204,500); Barbarian (230,509); Gunthor the Brave (233,499); Peksa (235,508); Unicorn (227,482)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 208,501:192 x2; 212,507:192 x2

### 10. Bounds (616,543) to (619,543)

- Center: (617.5, 543.0)
- Entries / unique object-tiles: 2 / 2
- Object mix: 192 x2
- Nearby NPC hints: Warrior (615,535); Chaos Druid (617,552); 3rd plague sheep (621,529); Farmer brumty (595,539); Goblin (602,525)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 616,543:192; 619,543:192

### 11. Bounds (420,551) to (422,551)

- Center: (421.0, 551.0)
- Entries / unique object-tiles: 2 / 2
- Object mix: 192 x2
- Nearby NPC hints: Monk of entrana (427,548); Chicken (412,545); npc 810 (419,562); Unicorn (425,540); Crone (411,560)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 420,551:192; 422,551:192

### 12. Bounds (480,608) to (496,621)

- Center: (488.7, 613.7)
- Entries / unique object-tiles: 16 / 16
- Object mix: 193 x14, 194 x2
- Nearby NPC hints: Platform Fisherman (490,614); Holgart (493,615); bailey (483,614); kent (510,636); Caroline (521,619)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19, Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 488,608:193; 489,608:193; 484,610:193; 485,610:193; 491,610:193; 491,611:193; 491,613:193; 494,613:193; 496,613:193; 480,614:194; 488,616:193; 491,616:193; 480,617:194; 488,618:193; 490,621:193; 493,621:193

### 13. Bounds (125,629) to (125,631)

- Center: (125.0, 630.0)
- Entries / unique object-tiles: 4 / 2
- Object mix: 192 x4
- Nearby NPC hints: Goblin (129,629); Giant Spider (120,636); Man (132,639); Sheep (138,633); Shop Assistant (135,640)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 125,629:192 x2; 125,631:192 x2

### 14. Bounds (221,645) to (233,664)

- Center: (225.8, 657.2)
- Entries / unique object-tiles: 6 / 5
- Object mix: 193 x6
- Nearby NPC hints: Black Knight (220,652); Darkwizard (229,642); Banker (220,637); npc 795 (219,636); spider (222,634)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19
- Coordinates: 233,645:193; 229,655:193; 224,659:193 x2; 224,661:193; 221,664:193

### 15. Bounds (368,678) to (373,687)

- Center: (370.0, 682.2)
- Entries / unique object-tiles: 12 / 6
- Object mix: 193 x8, 194 x4
- Nearby NPC hints: Scorpion (395,690); Chemist (344,665); Shop Assistant (363,714); npc 795 (369,715); DeVinci (346,658)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19, Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 370,678:194 x2; 368,680:193 x2; 373,680:193 x2; 368,684:194 x2; 373,684:193 x2; 368,687:193 x2

### 16. Bounds (246,681) to (328,745)

- Center: (288.9, 730.5)
- Entries / unique object-tiles: 46 / 46
- Object mix: 193 x46
- Nearby NPC hints: Murphy (300,729); Thurgo (291,713); npc 814 (288,698); Customs Officer (325,713); Rat (297,691)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19
- Coordinates: 313,681:193; 311,686:193; 301,700:193; 299,704:193; 248,724:193; 252,724:193; 296,724:193; 300,724:193; 249,726:193; 255,726:193; 297,726:193; 303,726:193; 254,727:193; 255,727:193; 302,727:193; 303,727:193; 254,731:193; 255,731:193; 302,731:193; 303,731:193; 249,732:193; 255,732:193; 297,732:193; 303,732:193; 246,734:193; 253,734:193; 294,734:193; 301,734:193; 273,738:193; 278,738:193; 321,738:193; 326,738:193; 275,739:193; 280,739:193; 323,739:193; 328,739:193; 279,740:193; 280,740:193; 327,740:193; 328,740:193; 279,744:193; 280,744:193; 327,744:193; 328,744:193; 280,745:193; 328,745:193

### 17. Bounds (689,702) to (696,706)

- Center: (693.0, 703.7)
- Entries / unique object-tiles: 3 / 3
- Object mix: 192 x3
- Nearby NPC hints: Rat (697,695); Bear (685,709); Goblin (691,686); Professor (713,698); Observatory assistant (714,683)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 694,702:192; 696,703:192; 689,706:192

### 18. Bounds (453,710) to (453,710)

- Center: (453.0, 710.0)
- Entries / unique object-tiles: 1 / 1
- Object mix: 194 x1
- Nearby NPC hints: Bartender (451,705); Pirate (448,707); Davon (444,706); Scorpion (464,717); Rat (446,697)
- Catch coverage: Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 453,710:194

### 19. Bounds (85,718) to (89,719)

- Center: (87.0, 718.5)
- Entries / unique object-tiles: 4 / 2
- Object mix: 193 x4
- Nearby NPC hints: Scorpion (87,715); Camel (93,734); Banker (92,699); npc 795 (90,694); Shantay Pass Guard (63,725)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19
- Coordinates: 89,718:193 x2; 85,719:193 x2

### 20. Bounds (196,726) to (196,726)

- Center: (196.0, 726.0)
- Entries / unique object-tiles: 1 / 1
- Object mix: 493 x1
- Nearby NPC hints: fishing instructor (198,728); financial advisor (208,728); mining instructor (198,738); cooking instructor (214,726); Bank assistant (198,748)
- Catch coverage: Raw Shrimp L1
- Coordinates: 196,726:493

### 21. Bounds (395,754) to (395,754)

- Center: (395.0, 754.0)
- Entries / unique object-tiles: 1 / 1
- Object mix: 193 x1
- Nearby NPC hints: Gnome pilot (390,754); Shipyard worker (401,756); Jungle Spider (396,764); Shipyard foreman (396,740); Tribesman (416,761)
- Catch coverage: Raw Shrimp L1, Raw Sardine L7, Raw Herring L13, Raw Anchovies L19
- Coordinates: 395,754:193

### 22. Bounds (443,800) to (453,810)

- Center: (448.0, 805.0)
- Entries / unique object-tiles: 2 / 2
- Object mix: 192 x1, 194 x1
- Nearby NPC hints: Hobgoblin (450,802); Goblin (442,800); Scorpion (460,802); Jogre (440,816); Jungle Spider (450,820)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37, Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 453,800:194; 443,810:192

### 23. Bounds (389,833) to (399,836)

- Center: (393.8, 834.8)
- Entries / unique object-tiles: 4 / 4
- Object mix: 192 x4
- Nearby NPC hints: Fernahei (395,837); UndeadOne (390,846); Yohnus (399,846); Jungle Banker (402,849); Jungle Spider (381,820)
- Catch coverage: Raw Trout L25, Raw Pike L31, Raw Salmon L37
- Coordinates: 396,833:192; 389,834:192; 391,836:192; 399,836:192

### 24. Bounds (74,1639) to (74,1639)

- Center: (74.0, 1639.0)
- Entries / unique object-tiles: 1 / 1
- Object mix: 194 x1
- Nearby NPC hints: Banker (73,1643); Tower guard (77,1643); Giant (65,1639); Rat (65,1640); Warrior (65,1643)
- Catch coverage: Raw Tuna L43, Raw Lobster L49, Raw Swordfish L55
- Coordinates: 74,1639:194

### 25. Bounds (373,3374) to (373,3374)

- Center: (373.0, 3374.0)
- Entries / unique object-tiles: 1 / 1
- Object mix: 271 x1
- Nearby NPC hints: Baby Blue Dragon (372,3372); Black Demon (384,3370); Blue Dragon (370,3355); Kharid Scorpion (380,3353); Poison Scorpion (351,3370)
- Catch coverage: Raw lava eel L62
- Coordinates: 373,3374:271
