# MyWorld Work Items

This is the active planning list. Scope labels describe implementation size, not importance.

## Recommended Next Work Order

Field-testing and balance tuning items in this document are intentionally
deferred until the limited release has testers and observed play data.

1. Finish documentation and guardrail alignment against the current runtime.
2. Keep the official root README aligned when player-facing feature behavior
   changes; art source attribution is recorded there.
3. Keep additional population, balance, and combat-presentation tuning for
   limited-release observations unless a concrete regression appears first.

## Implemented State

- Quest shortcuts are wired across the full free/member/MyWorld quest matrix
  using the player-facing option `I've already done this quest`.
- Quest shortcut rewards use the standard quest reward registrar, with explicit
  utility item backfills where the quest unlock depends on an item.
- Leather and carapace armor recipe coverage is in place and covered by tests.
- `Wolf` and `Hellhound` full-set bonuses now use armor-bound spirit companions
  backed by the summon runtime, without resource costs or damage absorption.
- `Wolf` and `Hellhound` spirit companion set bonuses passed field testing for
  the current implementation pass.
- Resource seed handling has a shared registry and active `Mithril seed` proof
  implementation, including owned temporary nodes, auto-repeat gathering, tool
  bubbles, overflow drops, and timed despawn.
- The first Woodcutting ore seed item/object set is implemented for tin,
  copper, iron, coal, mithril, silver, gold, adamantite, and runite.
- Woodcutting now has a rare ore seed side-reward table using the shared
  `reward tier <= tool tier + 2` weighting rule.
- Harvesting now has a generic `Resource Plant` temporary object and starter
  plant seed set for potatoes, onions, garlic, redberries, limpwurt, snape
  grass, guam, and ranarr. Harvesting can award these as rare side rewards
  using equipped shears tier and the same `+2` weighting rule.
- Harvesting side rewards now include finished-food seeds, all standard herb
  seeds, and the potion-ingredient seeds used by the current potion recipes.
  Finished-food rewards avoid raw cooking ingredients.
- Resource seed rewards now include `Knowledge` and `Money` seeds for both
  Woodcutting and Harvesting. These yield XP or coins from the player's current
  skill level instead of item resources, with three total harvests.
- Cloth dye rules are settled: only capes are player-dyeable. Wool robes stay
  white until enchanted, and enchanted robes use their altar palette.
- `White Cape` is the craftable wool cape so it is not confused with
  enchantable wool robes. Dye use can recolor dyeable capes into any supported
  dye color; `Black Cape` remains a fixed-color exception.
- Cloth/back-slot dye cleanup is complete for the current rules. Holiday/event
  cosmetics may keep dye interactions, but robes are no longer player-dyeable.
- Robe enchanting now uses direct altar upgrades instead of a production menu:
  unenchanted wool robes/hats become tier `1` altar robes/hats, enchanted robes
  can only upgrade by one tier at a time at their matching altar. The player
  must meet both the altar's level requirement and the target robe tier's level
  requirement.
- Cloth armor rework first pass is implemented:
  - `Wool Gloves` and `Wool Boots` exist and are craftable from balls of wool
  - gloves and boots can be enchanted at every altar and upgraded through the
    same tier path as hats/tops/bottoms
  - cloth defense is magic-defense-only
  - new Life cloth uses the intended `0.6x` defense budget while older altar
    cloth rows retain their current full tier-scaled magic-defense values;
    normalize those rows only as an explicit balance decision
  - robe-specific runtime effects have been retired from cloth armor
  - enchanted cloth preserves matching rune costs at `10%` per piece
  - matching enchanted staves preserve matching rune costs at `50%`
- Altar enchanting gate/cost audit is implemented:
  - spell rune costs are pinned against client/server parity
  - altar use requires the altar's Enchanting level for staff, jewelry, and
    cloth routes
  - staves also require their wood-tier Enchanting level
  - jewelry also requires its five-tier gem Enchanting level
  - cloth upgrades now cost `target tier * 100` matching altar runes and no
    metal bars
  - staff attunement now costs `staff tier * 200` matching altar runes and no
    cosmic runes
  - jewelry enchanting now costs `gem tier * 50` matching altar runes
- Woodcutting side rewards now include log seeds and gem seeds alongside ore
  seeds. Reward weight order is `Knowledge`/`Money` most common, then log
  seeds, then ore seeds, then gem seeds. Tin, copper, standard-tree, and
  pine-tree acquisition has been retired from the side table as too low-interest.
- The in-game skill guides have been refreshed for current MyWorld tiering,
  recipes, prayer behavior, mining tools, ranged gear, crafting, smithing,
  enchanting, and runecrafting.
- The first skill-guide audit pass after the gathering/resource-seed work is in
  place: Herblaw now matches the live potion tables, Harvesting lists current
  node levels and shears, Fishing/Woodcutting mention their side-reward focus
  systems, and old Strength/Defense guide tabs route to MyWorld notes.
- Skill guides passed the current field-test review and are considered checked
  off unless new stale entries are discovered later.
- The second in-game skill-guide Info pass is in place for mechanics that are
  hard to infer from level tables alone: leather/carapace set traits,
  jewelry stacking and charges, gathering rare side-reward settings,
  cloth/staff rune preservation, and Summoning role/upkeep rules.
- The magic audit is complete for the current pass: spell guide copy reflects
  the new debuff names, god spells now describe their AoE roles, god equipment
  is listed with current requirements, and the Info tab notes that `Chaos
  gauntlets` make tier 1 spells hit like tier 2 spells.
- Dual-element spells have a first implementation pass through tier `3`:
  - Fire + Water = Thunder
  - Air + Water = Ice
  - Fire + Earth = Acid
  - Earth + Water = Wood
  - unlocks are level `10`, `30`, and `50`
  - costs use both matching elemental runes plus the normal tier rune
  - base spell power is `1.2x` the same-tier single-element line
  - Thunder `Startle`, Acid `Corrode`, Ice `Frostbite`, and Wood `Splinter` are live
  - `Splinter` has the normal tier proc chances and hits one random additional
    attackable NPC within `2` tiles for half the dealt damage, rounded up
- Agility pouches currently use the built-in casket item sprite while original replacement art is pending.
- Visual-asset attribution is recorded:
  - CraftPix-derived icons and Phoenix/Kraken animations were removed before distribution
  - Pimen supplied included animations and permitted source-available distribution
  - the project author made original additions including fishing rod equipment sprites
- The official root README mentions the implemented god-knight sources,
  Wilderness population pass, Wood `Splinter`, and custom-bank `Ctrl`-click
  shortcuts.
- The player-facing project overview has been promoted from its draft into the
  official repository-root `README.md`, with development entry points retained.
- Fishing now has the first gameplay conversion pass:
  - ten wood-line rods exist and are equipable main-hand Fishing tools
  - knife-on-log production includes fishing rods beside the other wood products
  - normal MyWorld fishing spots resolve through coordinate-based location pools
  - equipped rod tier filters eligible fish through a four-tier catch window:
    the rod's tier down through three tiers below it
  - Fishing level plus rod tier drives catch weighting and catch delay
  - spot examine text lists the fish available at that location
  - legacy click commands on normal MyWorld spots are redirected into the rod model
  - legacy fishing tools are blocked on normal MyWorld spots
  - normal fishing shops sell rods through tier 6, while the Fishing Guild sells
    the full tier 1-10 rod line
  - source tests guard the current rod model, shop cleanup, quest exceptions,
    and retired-tool use-on-spot behavior
  - fishing rare side rewards use the equipped-tool focus window:
    `Just the fish`, `A little loot`, `Plenty of loot`, and `Lots of loot`
  - fishing side rewards are `Oyster`, `Seaweed`, `Casket`, and tier-filtered
    leather/carapace boots and gloves
  - fishing side rewards use the shared `+2` tier-window gating model: normal
    weight at or below equipped rod tier, half weight at `+1`, and quarter
    weight at `+2`
  - legacy generic `Boots` are removed from the MyWorld fishing side table
- Harvesting now uses equipped shears as its standard tiered tool family.
- Sheep shearing now follows the MyWorld equipped-shears model through a direct
  `Shear` NPC command, while legacy use-shears-on-sheep behavior remains
  available outside the MyWorld path.
- Harvestable node access is now gated by the equipped shears tier rather than
  a second direct Harvesting-level check. Harvesting level gates which shears
  the player can equip, and shears tier gates which produce can be harvested.
- Herb rolls now use the same four-tier window concept as fishing rods: the
  equipped shears tier down through three tiers below it.
- Mining, Woodcutting, Harvesting, and Fishing now require their tiered
  gathering tools to be equipped for normal gathering.
- Armor weapon-power penalties are live:
  - `Metal` head/chest/legs apply `-8 Ranged Power` per piece
  - `Leather/carapace` head/chest/legs apply `-8 Magic Power` per piece
  - `Cloth/robe` head/chest/legs apply `-8 Melee Power` per piece
  - gloves, boots, and back-slot items do not apply these penalties
- Smelting production is live:
  - furnaces open a production window directly
  - direct ore-on-furnace smelting shortcuts are retired for normal smelting
  - `Pig Iron bar` is awarded on failed iron smelts
  - `Pig Iron bar` can replace iron ore when making steel
  - revised coal/material costs are active through runite
  - mixed-material smelting recipes display ingredient icons with owned/required counts
- Stats/combat display polish is live for the current pass:
  - skill current/base values are right-aligned inside their stats-menu columns
  - Equipment Status no longer displays `Prayer`
  - `Weapon Pow` is split into `Melee Pow`, `Ranged Pow`, and `Magic Pow`
  - `Summoning` uses the shorter stats-menu label `Summon`
  - MyWorld player combat level uses `highest main combat type + ((Summoning + Prayer + Hits) / 3)`
  - NPC combat levels keep the existing formula
- Custom-bank quality-of-life shortcuts are live:
  - `Ctrl` plus left-click on a bank item withdraws its full available quantity
  - `Ctrl` plus left-click on an inventory item deposits all copies of that item
  - equipment mode retains its direct equip click behavior
- Combat click/animation issues noted during Fishing field testing are not
  currently reproducible after later combat/UI work. Treat them as resolved for
  now and reopen only if they reappear in field testing.
- A targeted dialogue cleanup pass covered the obvious recently touched systems:
  leather/tailoring shop dialogue is broadly aligned, fishing shop dialogue is
  aligned with rod-based fishing, and stale prayer-recharge wording found in
  grape empowerment was removed. Keep deeper dialogue cleanup ongoing as stale
  lines are encountered in field testing.
- A focused item/resource naming pass normalized the visible base ore/bar/hide
  names through MyWorld overrides and changed the custom metal material name to
  `Titan Steel`. Broader inherited legacy naming remains an as-found cleanup
  item rather than active migration work.
- Source-of-truth cleanup is complete for the current pass:
  completed Summoning foundation, Wolf/Hellhound armor companions, and Fishing
  rod-tier work have been moved out of active implementation wording and kept
  only as tuning/field-test references.
- Depleted ore placeholders are no longer treated as generic stone rocks:
  mining/prospecting a depleted ore node now reports that no ore is available,
  while true generic rocks still mine `Stone`.
- Guaranteed creature-material drops now de-duplicate per shared drop table, so
  NPC variants that share one table no longer stack multiple identical
  guaranteed hides or carapaces.
- Shearing overflow follows the current no-loss gathering rule: excess wool
  falls to the ground instead of being destroyed.
- Demon ash prayer source is implemented:
  - demon-family bone drops produce `Demon ash` instead of generic `Ashes`
  - `Demon ash` scatters for Prayer XP through the normal inventory action path
  - the Greater Demon summon consumes `Demon ash`
  - generic `Ashes` remain for non-demon sources such as imps, firemaking, and
    quest requirements
- Targeted behavior coverage for special jewelry effects is in place:
  - elemental rings/necklaces, mind, body, nature, blood, death, chaos, law,
    soul, and cosmic effects have been checked in field testing
  - `tests/myworld/test-jewelry-runtime-effects.py` pins effect formulas and
    runtime wiring for combat, drops, gathering, poison, XP, summoning, law
    transport, and soul death-save behavior
- Compatibility-only content now has an active ledger in
  `compatibility-only-content.md`, covering inert item families, quest/utility
  exceptions, and retired system items that should not be mistaken for live
  progression.
- The full root validation entry point covers the current MyWorld guardrails
  and passes for this documentation-alignment pass; keep it passing before
  limited release.
- God knight conversion has a first implementation slice:
  - ordinary steel equipment can be blessed into existing Black Knight
    equipment at Zamorak altars
  - ordinary steel equipment can be blessed into existing White Knight
    equipment at Saradomin altars
  - ordinary steel equipment can be blessed into Grey Knight equipment at
    Guthix altars
  - conversion is free, steel-only, and one-way
  - black, white, and grey knight equipment now require matching worship to
    equip and only contribute prayer bonus while matching the current prayer
    book
- God knight direct sources have a first implementation slice:
  - existing White Knights now cover the supported direct white weapon/armor
    drop line
  - dedicated `Grey Knight` NPC `836` has a direct grey equipment table and
    four MyWorld locations in the Taverley druid/white-wolf combat region
  - standard Black Knights intentionally retain ordinary drops; direct black
    god-equipment sources remain Dark Warriors and altar conversion
- A broader Wilderness population overlay is implemented in
  `MyWorldNpcLocs.json`: `74` added Wilderness hostiles across all ten-level
  depth bands, including expanded Dark Warrior and Greater Demon pockets and a
  new deep-Wilderness Hellhound presence. A themed follow-up pass adds
  aggressive Zamorak altar monks, Chaos Druid Warriors, a Graveyard
  Necromancer, elite Hobgoblins, and Mage Arena-route Shadow Warriors.

## Small

- Add held shears equipment sprites when authored; record them as original
  project-author work in the final credits once included.
- Treat `migration-regression-audit.md` as a completed recovery record. Track
  new regressions here only if they become active work again.
- Fix newly discovered player-facing UI text and layout polish:
  - equipment/status labels that clip
  - stale player-facing text if live testing finds remaining cases
  - old combat-style wording in options or info windows
  - production/enchanting/combat messages that still refer to retired systems
- Review and tune skilling rare side-reward rates after more live sessions.
  Current base roll chances are:
  - Fishing: off, `1/30`, `1/20`, or `1/15` for `Just the fish`,
    `A little loot`, `Plenty of loot`, and `Lots of loot`
  - Mining: off, `1/50`, `3%`, or `4%` for `Just the ore`,
    `A few gems`, `Plenty of gems`, and `Lots of gems`
  - Woodcutting: off, `1/50`, `3%`, or `4%` for the four seed-focus options
  - Harvesting: off, `1/50`, `3%`, or `4%` for the four seed-focus options
- Clean up remaining inherited item/resource naming inconsistencies only as
  they are encountered in live MyWorld-facing systems.
- Audit NPC and quest dialogue for MyWorld consistency as issues are found:
  - smooth awkward quest-shortcut NPC responses discovered during field testing
  - remove stale references to retired systems where they are not quest-critical
- Keep hover text and examine text minimal, accurate, and consistent.
- Limited-release review of god-aligned knight placement:
  - audit is documented in `god-knight-equipment-audit.md`
  - existing `White Knight` map placement is retained for the current pass
  - dedicated `Grey Knight` definition, drop source, and first placement are
    implemented
  - review whether Grey Knights should remain in the Taverley wolf/druid combat
    pocket; standard Black Knights intentionally remain ordinary-drop sources
  - map knight identities as `Black Knight` = Zamorak, `White Knight` =
    Saradomin, and `Grey Knight` = Guthix
- PvM population pass upkeep:
  - source of truth is `pvm-population-and-cluster-plan.md`
  - hostile NPC proximity audit, including a Wilderness-only mode, is generated by
    `tools/myworld/audit-npc-clusters.py` and captured in
    `pvm-npc-cluster-audit.md`
  - current Wilderness additions expand encounter regions through a
    MyWorld-only location overlay; new Wilderness enemy presence includes
    Hellhounds, aggressive Zamorak monks, Chaos Druid Warriors, a Necromancer,
    elite Hobgoblins, and Shadow Warriors
  - the level `31-40` Wilderness band improved to `36` hostile locations and
    remains the thinnest band for limited-release field review
  - keep quest, shop, town, and skilling spaces readable
  - include White Knight and Grey Knight placement/drop-table decisions in this
    broader pass
  - field-test multiple nearby monsters attacking the same player; use
    `::aggroall [radius]` to force local attackable NPCs into combat
  - after limited-release combat observation, decide whether chaos jewelry
    splash/recoil-style damage should provoke non-aggressive NPCs or only
    create loot/threat credit
- During limited-release gathering sessions, confirm that the depleted-rock
  fix eliminated the previous one-off `Unobtainium` mining report.
- Only add an in-game deterministic cosmic jewelry test command if future
  field testing needs forced proc visibility.
- Audit jewelry worn visuals only if a concrete bad item ID appears again. The
  current ring, necklace, and amulet slot data is internally consistent.

## Medium

- Future Magic identity pass:
  - Revisit elemental spell debuffs if the current named debuffs still feel too
    similar after more field testing.
  - Reconsider elemental enchantment effects in the same pass as spell debuffs,
    especially where robe/staff/jewelry effects should reinforce clearer
    elemental identities.
- Leather/carapace set-bonus upkeep:
  - keep bonuses creature-themed when new hide families are added
  - preserve source-based stacking rules and avoid duplicate stacking from
    multiple players using the same source
  - keep attack-counter buff/debuff behavior consistent with the live combat
    model
  - keep poison behavior aligned with the shared poison-power model
- Summoning upkeep:
  - field-test the full first catalog in level order
  - tune cost, XP, damage, hits, traits, replacement, dismissal, and logout
    cleanup from observed play
  - charge/arrival animation assets are wired; summon icon art is pending an
    original replacement set
- Known retired item-source redirects, potion-source cleanup, and client item
  definition coverage are guarded by current checks. Reopen that work only
  when a concrete new leak or coverage regression is found.
- Fishing upkeep:
  - continue field testing mixed-pool catches and quest exceptions
  - tune rod-tier catch weighting and rare side-reward rates from longer play
    sessions
- Continue expanding Resource Seed content after live testing:
  - tune seed rarity against real Woodcutting and Harvesting session lengths
  - add any missing higher-tier Harvesting plant/resource seed candidates
  - decide whether starter food/herb/ingredient seeds remain dev-only or gain
    a separate acquisition path
- Tune existing `Mithril seed` sources and pricing only after limited-release
  sessions show whether its current resource yield is too generous.
- Continue moving fork-owned behavior into `server/plugins/com/openrsc/server/plugins/custom/myworld/` where it does not create awkward shared-code coupling.
- Keep command workflow state out of `dev/myworld`; its `assets/` subtree
  remains the active client visual-asset source.

## Large

- Leather and cloth armor production:
  - leather/carapace slot recipes are covered for the standard tier families
  - keep recipe coverage guarded by `test-hide-armor-coverage.py`
  - keep gnome clothing as wool-equivalent cosmetic cloth, not enchantable robes
  - retire or remap old medium-helm, chainmail, skirt, square-shield, and side-slot assumptions where needed
- Magic/enchanting cleanup:
  - field-test the cloth rework:
    - base glove and boot crafting
    - glove and boot altar upgrades
    - full matching cloth plus matching staff reaching `100%` preservation
    - mismatched cloth preserving only its matching rune
    - retired robe effects no longer firing from cloth pieces
  - jewelry effects and player-facing item definitions match the current
    runtime and are covered by `test-jewelry-runtime-effects.py`
  - decide which removed spell slots/icons become new spellbook content
  - keep player-facing explanations aligned when spell or jewelry designs change
  - review enemy debuff coverage after combat tuning
- Agility course reward pouches:
  - tune final loot weights and quantities from live testing
  - all three pouches use a built-in casket sprite pending original replacement art
  - consider high-end Agility-exclusive equipment as rare pouch rewards
  - design unique equipment carefully so it has a clear role and does not invalidate existing gear progression
- Quest shortcut rollout:
  - implementation pass is complete across the current quest matrix
  - field-test branch outcomes intentionally
  - keep old reward skill remaps in the MyWorld skill model
  - keep audited utility-item backfills explicit when new quests or unlocks are added
- Foundation optimization:
  - continue benchmark-led optimization only one phase at a time
  - prioritize event execution, view/update context, and region storage only after measurement shows the need
  - avoid changing collision or gameplay rules as part of optimization
- Optional later repository rename:
  - consider `Client_Base/` and `PC_Client/` renames only after a clean full test baseline
  - do not mix source directory renames with gameplay changes

## Guardrail Notes

### Prayer Rework

The active prayer model has 45 prayers in a 45-prayer catalog with a 15-slot current-book UI. The old drain-first behavior is replaced by allocation capacity. The server XP multiplier path now uses active skilling prayer bonuses instead of legacy level gates.

Required doc guardrails:

- 45-prayer catalog
- 15-slot current-book UI
- server XP multiplier

### Gathering Rework

## Per-Resource Yield Ladder

The yield ladder repeats every 10 effective levels from the resource's unlock.

- unlock+10 to unlock+19
- tin `1`, copper `8`,
  iron `15`, coal `22`, mithril `38`, adamantite `54`, and runite `70`
- Stone rocks should unlock at level `1`.
- Yield beyond free inventory space should drop on the ground, not be lost.
- `Just the ore`, `A few gems`, `Plenty of gems`, and `Lots of gems` behavior.
- Future gathering rare-side-reward work should reuse this option style for
  fishing and any woodcutting/harvesting equivalents rather than adding one-off
  mechanics per skill.

Fishing should now follow `fishing-rework-plan.md`: location pools determine
available fish, rod tier determines the eligible fish window, and effective
Fishing determines catch quality and speed.

Current fishing caveats:

- `Fishing Contest` still explicitly uses the baseline `Fishing Rod` plus
  `Fishing Bait`/worms.
- `Dragon Slayer` still explicitly consumes a `Lobster Pot` for the map-piece
  door.
- `Hero's Quest` lava-eel guidance points MyWorld players at the tier `9`
  `Magic Fishing Rod`; old oily-rod creation is compatibility-only outside the
  MyWorld fishing model.
- These are quest exceptions, not normal Fishing progression tools.

### Quest Shortcut/Audit Guardrails

MyWorld per-quest shortcut rollout remains the active quest cleanup model.

The global login bootstrap has been removed.

Quest initiation audit must remain explicit. Quest-unique items with use outside the original quest need explicit backfill handling. The target player-facing shortcut flow is: "I've already done this quest".

Reward-skill remaps:

- `Attack` XP -> `Melee`
- `Defense` XP -> `Melee`
- `Strength` XP -> `Melee`
- `Fletching` XP -> `Crafting`

Choice-sensitive quest sections:

### Observatory

### Tourist Trap

### Hazeel Cult

### Temple of Ikov

### Shield of Arrav

### Family Crest

### Legends Quest

Default Legends Quest shortcut policy: grant all `12` training rewards.

## Full Quest Shortcut Matrix

Free quests:

- `Black Knights' Fortress`
- `Cook's Assistant`
- `Demon Slayer`
- `Doric's Quest`
- `Dragon Slayer`
- `Ernest the Chicken`
- `Goblin Diplomacy`
- `Imp Catcher`
- `The Knight's Sword`
- `Pirate's Treasure`
- `Prince Ali Rescue`
- `Romeo & Juliet`
- `Sheep Shearer`
- `Shield of Arrav`
- `The Restless Ghost`
- `Vampire Slayer`
- `Witch's Potion`

Members quests:

- `Biohazard`
- `Clock Tower`
- `Druidic Ritual`
- `Dwarf Cannon`
- `Family Crest`
- `Fight Arena`
- `Fishing Contest`
- `Gertrude's Cat`
- `The Hazeel Cult`
- `Hazeel Cult`
- `Hero's Quest`
- `Jungle Potion`
- `Lost City`
- `Merlin's Crystal`
- `Monk's Friend`
- `Murder Mystery`
- `Observatory`
- `Plague City`
- `Scorpion Catcher`
- `Sea Slug`
- `Sheep Herder`
- `Temple of Ikov`
- `The Holy Grail`
- `Tree Gnome Village`
- `Tribal Totem`
- `Waterfall Quest`
- `Witch's House`
- `Digsite`
- `Grand Tree`
- `Legends' Quest`
- `Shilo Village`
- `Tourist Trap`
- `Underground Pass`
- `Watchtower`
- `Peeling the Onion`

## Clarifications To Review

### 1. Shield of Arrav / Hero's Quest state

### 2. Biohazard / Underground Pass chain

### 3. Dwarf Cannon package

### 4. Observatory generosity

### 5. Tourist Trap generosity

### 6. Legends' Quest generosity

### 7. Hazeel Cult branch fidelity

### 8. Temple of Ikov canonical state

### 9. Gertrude's Cat consumables

### 10. Tree Gnome Village memorabilia

### 11. Underground Pass staff state

## Quest Shortcut Status

The current free, members, and MyWorld shortcut rollout is implemented. The
policy headings above remain as an acceptance record; branch-outcome testing
is deferred until limited-release field sessions.

## Combat Follow-Up

- Auto-retaliate pass status:
  - self-heal while already attacking now resumes outgoing combat
  - self-heal while only being attacked can still start auto-retaliation
  - manual walk-away followed by enemy pursuit now re-engages when
    auto-retaliate is enabled
  - remaining work is live field testing across food, potions, retargeting,
    ranged attacks, magic attacks, enemy death, and multiple attackers
  - keep the rule strict: auto-retaliate resumes only when the setting is
    enabled and the player is still being attacked
- Combat sprite/world-position pass status:
  - enemy and player combat sprites now stay anchored to map position rather
    than the old camera-relative locked-combat position
  - combat-facing direction now uses the correct side after direction reversal
  - remaining work is live field testing with multiple players attacking one
    NPC and multiple NPCs attacking one player
- Poison runtime needs field testing after the ramping-proc pass:
  - verify the `100% -> 50% -> 20%` weapon ramp feels visible without becoming
    dominant
  - verify the `50% -> 10%` armor ramp feels distinct from weapon poison
  - watch whether the `8`-tick poison pulse and `3`-power drain need another
    tuning pass after live fights
- Leather and carapace set presentation still needs a focused balance pass:
  - field-test proc frequency and damage ceilings
  - look for any set whose live behavior still feels mismatched to its examine text
- Combat presentation follow-up:
  - reopen intermittent player-side standard combat animation investigation
    only if it is reproduced during limited-release sessions
