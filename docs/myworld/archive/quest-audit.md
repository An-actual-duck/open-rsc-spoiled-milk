# MyWorld Quest Audit

## Goal

MyWorld should not auto-complete every quest at login. That approach front-loads XP, quest points, and item rewards, and it blocks the better model:

- players can still talk to quest NPCs and play the quest normally
- players can also choose a future shortcut such as `I've already done this quest`
- that shortcut should complete only that quest, award only that quest's useful progression items and rewards, and leave every other quest untouched

## Current direction

The global login bootstrap has been removed. Any future quest shortcut system should be per-quest, not all-at-once.

## MyWorld per-quest shortcut rollout

First-wave shortcuts are now wired at the starter NPC for:

- `Lost City`
- `Waterfall Quest`
- `Family Crest`
- `Plague City`
- `Watchtower`
- `Gertrude's Cat`
- `Sea Slug`
- `Dwarf Cannon`
- `Imp Catcher`
- `Monk's Friend`
- `Murder Mystery`
- `Tree Gnome Village`
- `Biohazard`
- `Observatory`
- `Tourist Trap`
- `Legends' Quest`
- `Hazeel Cult`
- `Temple of Ikov`
- `Shield of Arrav`
- `Hero's Quest`

Each of those starters now offers `I've already done this quest` during the stage-0 conversation. The shortcut path completes only that quest, grants its audited reward package at that moment, and backfills the utility item or cache that still matters after quest completion. The normal quest path is still available if the player wants to play it through.

`Biohazard` is the first implemented line-quest exception: its starter shortcut now
also completes `Underground Pass`, grants the `King Lathas amulet`, the finished
`Staff of Iban`, the Iban end-fight rune payout, and the post-quest
`Iban blast_casts` cache state in one step from Elena's first interaction.

`Observatory`, `Tourist Trap`, and `Legends' Quest` are now implemented under
the `MyWorld` “grant all meaningful reward branches” policy. Their starter
shortcuts award every audited training or constellation branch at once, with
legacy combat rewards remapped to `Melee` and `Fletching` rewards remapped to
`Crafting`, while still preserving the important post-quest utility item
rewards such as the `Wrought iron key`.

`Hazeel Cult` and `Temple of Ikov` are now implemented under the approved
branch-sensitive defaults. `Hazeel Cult` settles only the shared lasting reward
package, intentionally skipping `Mark of Hazeel`, `Poison`, and route-only
memorabilia. `Temple of Ikov` now asks which ending the player took after the
shortcut prompt, then records either the Lucien `-2` completion state or the
guardian `-1` completion state without auto-granting either pendant.

`Shield of Arrav` and `Hero's Quest` are now implemented under the gang-state
shortcut policy. `Shield of Arrav` asks which gang the player joined and records
the matching completed route. `Hero's Quest` uses the existing `Shield of Arrav`
 state if present, or asks the same gang question and backfills the `Shield of
Arrav` completion before completing `Hero's Quest`, so the armband path and
follow-on gang checks stay coherent.

## Quest initiation audit

This section records where each quest is currently started in code. The main signal used here is the first transition to quest stage `1`, plus known off-file starters for quests whose starter lives outside the class that implements `QuestInterface`.

### Free quests

- `Black Knights' Fortress`: [`BlackKnightsFortress.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/BlackKnightsFortress.java#L120)
- `Cook's Assistant`: [`CooksAssistant.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/CooksAssistant.java#L158)
- `Demon Slayer`: [`DemonSlayer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/DemonSlayer.java#L1059)
- `Doric's Quest`: [`Dorics.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/Dorics.java#L75)
- `Dragon Slayer`: starter lives outside the quest class in [`Guildmaster.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Guildmaster.java#L56)
- `Ernest the Chicken`: [`ErnestTheChicken.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ErnestTheChicken.java#L483)
- `Goblin Diplomacy`: starter lives outside the quest class in [`Bartender.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/portsarim/Bartender.java#L61)
- `Imp Catcher`: [`ImpCatcher.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ImpCatcher.java#L129)
- `The Knight's Sword`: [`KnightsSword.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/KnightsSword.java#L494)
- `Pirate's Treasure`: [`PiratesTreasure.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/PiratesTreasure.java#L175)
- `Prince Ali Rescue`: [`PrinceAliRescue.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/PrinceAliRescue.java#L109)
- `Romeo & Juliet`: [`RomeoAndJuliet.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/RomeoAndJuliet.java#L71)
- `Sheep Shearer`: [`SheepShearer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/SheepShearer.java#L81)
- `Shield of Arrav`: starter lives outside the quest class in [`Reldo.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/varrock/Reldo.java#L353)
- `The Restless Ghost`: starter lives outside the quest class in [`Priest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/npcs/lumbridge/Priest.java#L127)
- `Vampire Slayer`: [`VampireSlayer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/VampireSlayer.java#L89)
- `Witch's Potion`: [`WitchesPotion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/WitchesPotion.java#L156)

### Members quests

- `Biohazard`: [`BioHazard.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java#L117)
- `Clock Tower`: no stage-1 transition in the quest class; starter dialogue lives in [`ClockTower.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/ClockTower.java)
- `Druidic Ritual`: [`DruidicRitual.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/DruidicRitual.java#L186)
- `Dwarf Cannon`: [`DwarfCannon.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/DwarfCannon.java#L281)
- `Family Crest`: [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L212)
- `Fight Arena`: [`FightArena.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FightArena.java#L467)
- `Fishing Contest`: [`FishingContest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FishingContest.java#L387)
- `Gertrude's Cat`: [`GertrudesCat.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/GertrudesCat.java#L100)
- `The Hazeel Cult`: [`HazeelCult.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HazeelCult.java#L132)
- `Hero's Quest`: [`HerosQuest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HerosQuest.java#L309)
- `Jungle Potion`: no explicit stage-1 transition found in the quest class; start flow lives in [`Jungle_Potion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Jungle_Potion.java)
- `Lost City`: no explicit stage-1 transition found in the quest class; start flow lives in [`LostCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/LostCity.java)
- `Merlin's Crystal`: [`MerlinsCrystal.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/MerlinsCrystal.java#L472)
- `Monk's Friend`: [`MonksFriend.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/MonksFriend.java#L91)
- `Murder Mystery`: [`MurderMystery.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/MurderMystery.java#L310)
- `Observatory`: [`Observatory.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java#L257)
- `Plague City`: [`PlagueCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/PlagueCity.java#L582)
- `Scorpion Catcher`: [`ScorpionCatcher.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/ScorpionCatcher.java#L338)
- `Sea Slug`: [`SeaSlug.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/SeaSlug.java#L296)
- `Sheep Herder`: [`SheepHerder.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/SheepHerder.java#L140)
- `Temple of Ikov`: [`TempleOfIkov.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java#L164)
- `The Holy Grail`: no explicit stage-1 transition found in the quest class; start flow lives in [`TheHolyGrail.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TheHolyGrail.java)
- `Tree Gnome Village`: [`TreeGnomeVillage.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TreeGnomeVillage.java#L435)
- `Tribal Totem`: [`TribalTotem.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TribalTotem.java#L105)
- `Waterfall Quest`: [`Waterfall_Quest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Waterfall_Quest.java#L97)
- `Witch's House`: [`WitchesHouse.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/WitchesHouse.java#L103)
- `Digsite`: starter lives outside the quest class in [`DigsiteExaminer.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/digsite/DigsiteExaminer.java#L75)
- `Grand Tree`: [`GrandTree.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/grandtree/GrandTree.java#L163)
- `Legends' Quest`: starter lives in [`LegendsQuestSirRadimusErkle.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestSirRadimusErkle.java#L325)
- `Shilo Village`: starter lives outside the completion class in [`ShiloVillageMosolRei.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/shilovillage/ShiloVillageMosolRei.java#L82)
- `Tourist Trap`: [`TouristTrap.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java#L288)
- `Underground Pass`: starter is reached through the Biohazard/Lathas chain in [`BioHazard.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java#L807)
- `Watchtower`: [`WatchTowerDialogues.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerDialogues.java#L802)
- `Peeling the Onion`: start flow lives in the MyWorld quest and NPC dialogue paths under [`PeelingTheOnion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/myworld/quests/free/PeelingTheOnion.java) and [`Sedridor.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/myworld/npcs/Sedridor.java)

## Reward audit

For shortcut design, each quest should be treated as one of three categories.

### Category A: `handleReward` is close enough to the final reward

These quests mostly put XP/QP and the intended unlock into `handleReward`, so a future per-quest shortcut can likely call the quest reward hook directly and then layer only item-specific exceptions if needed.

- `Tree Gnome Village`: [`TreeGnomeVillage.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TreeGnomeVillage.java#L53)
- `Observatory`: base crafting XP/QP in [`Observatory.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java#L50), random branch rewards still separate
- `Peeling the Onion`: post-quest unlock flags in [`PeelingTheOnion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/myworld/quests/free/PeelingTheOnion.java#L802)

### Category B: final reward includes scripted item payout before or after `sendQuestComplete`

These quests are the main reason global auto-rewarding is dangerous. They need explicit per-quest shortcut compensation.

- `Imp Catcher`: `Amulet of accuracy` before completion in [`ImpCatcher.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/free/ImpCatcher.java#L85)
- `Family Crest`: `Steel gauntlets` and `famcrest_gauntlets` cache after completion in [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L149)
- `Monk's Friend`: `8 law runes` after completion in [`MonksFriend.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/MonksFriend.java#L168)
- `Plague City`: `Magic scroll` after completion in [`PlagueCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/PlagueCity.java#L681)
- `Biohazard`: `King Lathas amulet` before completion in [`BioHazard.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java#L887)
- `Gertrude's Cat`: `Kitten`, `Chocolate cake`, `Stew` before completion in [`GertrudesCat.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/GertrudesCat.java#L184)
- `Sea Slug`: `Quest oyster pearls` after completion in [`SeaSlug.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/SeaSlug.java#L331)
- `Digsite`: `2 gold bars` before completion in [`DigsiteExpert.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/digsite/DigsiteExpert.java#L247)
- `Murder Mystery`: `2000 coins` after completion in [`MurderMystery.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/MurderMystery.java#L552)
- `Watchtower`: `5000 coins` and `Spell scroll` in the obstacle flow, not in `handleReward`, at [`WatchTowerObstacles.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerObstacles.java#L132)

### Category C: branching or choice rewards

These quests need an explicit MyWorld design decision before any per-quest auto-complete option is added.

- `Observatory`: random constellation reward plus optional combat-skill XP branch in [`Observatory.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Observatory.java#L624)
- `Tourist Trap`: player chooses two skill rewards in [`TouristTrap.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/touristtrap/TouristTrap.java#L318)
- `Hazeel Cult`: branch-specific reward logic in [`HazeelCult.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HazeelCult.java#L58)
- `Temple of Ikov`: multiple ending paths and special stage handling in [`TempleOfIkov.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/TempleOfIkov.java#L446)

## Quest-unique items with use outside the original quest

These are the high-value items a per-quest shortcut should consider granting or restoring because they still matter after the quest is over.

- `Dramen staff`
  - Source: [`LostCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/LostCity.java#L441)
  - Use outside quest: required for Zanaris access checks in [`LostCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/LostCity.java#L457)
- `Glarial's amulet`
  - Source/retrieval: [`Waterfall_Quest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Waterfall_Quest.java#L434)
  - Use outside quest: still checked on waterfall access paths in [`Waterfall_Quest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/Waterfall_Quest.java#L715)
- `Staff of Iban`
  - Source: [`UndergroundPassIban.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/undergroundpass/npcs/UndergroundPassIban.java#L70)
  - Use outside quest: spell cast requirement in [`SpellHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java#L1655)
- `Steel gauntlets` and enchanted gauntlet variants
  - Source: [`FamilyCrest.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/FamilyCrest.java#L149)
  - Use outside quest: smithing/cooking/chaos/goldsmithing bonuses depend on `famcrest_gauntlets`
- `Magic scroll`
  - Source/retrieval: [`PlagueCity.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/PlagueCity.java#L681)
  - Use outside quest: unlocks Ardougne teleport via `ardougne_scroll` in [`InvAction.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvAction.java#L450) and [`SpellHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java#L1880)
- `Spell scroll`
  - Source/retrieval: [`WatchTowerObstacles.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerObstacles.java#L146) and [`WatchTowerDialogues.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/watchtower/WatchTowerDialogues.java#L743)
  - Use outside quest: unlocks Watchtower teleport via item action handling in [`InvAction.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/itemactions/InvAction.java#L83)
- `Quest oyster pearls`
  - Source: [`SeaSlug.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/SeaSlug.java#L331)
  - Use outside quest: cut into bolt tips in [`Fletching.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/skills/fletching/Fletching.java#L685)
- `Kitten`
  - Source: [`GertrudesCat.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/GertrudesCat.java#L184)
  - Use outside quest: unlocks the whole cat/kitten progression and care system under [`KittenToCat.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/minigames/kittencare/KittenToCat.java#L45)
- `Dwarf cannon parts`, `Cannon ammo mould`, `Instruction manual`
  - Source/retrieval: [`DwarfCannon.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/DwarfCannon.java#L92)
  - Use outside quest: cannon ownership/use and cannonball smithing
- `Tree gnome translation`
  - Source/retrieval: [`GrandTree.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/grandtree/GrandTree.java#L158)
  - Use outside quest: still retrievable after quest and used for reading quest-related text
- `King Lathas amulet`
  - Source: [`BioHazard.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/BioHazard.java#L887)
  - Use outside quest: currently more of a reward/identity item than a hard gate; still a quest-unique item worth preserving in shortcut flow
- `Mark of Hazeel` and `Carnillean armour`
  - Source: [`HazeelCult.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/HazeelCult.java#L957)
  - Use outside quest: branch-signaling quest items; important if MyWorld wants branch fidelity in shortcuts
- `Yommi tree seeds`
  - Source/retrieval: [`LegendsQuestUngadulu.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/authentic/quests/members/legendsquest/npcs/LegendsQuestUngadulu.java#L543)
  - Use outside quest: continued Legends quest progression and ritual mechanics
- `Ogre recipes` and makeover voucher flow
  - Source/retrieval: [`PeelingTheOnion.java`](/home/justin/Core-Framework/server/plugins/com/openrsc/server/plugins/custom/myworld/quests/free/PeelingTheOnion.java#L771)
  - Use outside quest: free makeover unlock and post-quest Sedridor dialogue

## Recommended shortcut design

Per quest, the future `I've already done this quest` option should:

1. mark only that quest complete
2. call or mirror the quest's `handleReward` only if MyWorld actually wants the XP/QP
3. separately grant only the audited non-XP progression items and post-quest-useful unlock items
4. write any required caches that the post-quest world logic expects
5. avoid granting transient handoff items that are only useful during the original quest path

## Immediate implementation recommendation

Build the shortcut system quest-by-quest in this order:

1. `Lost City`, `Waterfall Quest`, `Family Crest`, `Plague City`, `Watchtower`
2. `Gertrude's Cat`, `Sea Slug`, `Dwarf Cannon`
3. `Observatory`, `Tourist Trap`, `Hazeel Cult`, `Temple of Ikov`

That order handles the most important post-quest utility items first and defers the branching reward quests until the policy is explicit.
