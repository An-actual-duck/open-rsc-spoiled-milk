# Enchanting Tasklist

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a design decision or dependency

## Current Focus

- `[done]` Core altar-based enchanting framework and first jewelry implementation pass
- `[doing]` Staff enchanting redesign planning now lives in
  [magic-equipment-tasklist.md](/home/justin/Core-Framework/docs/myworld/magic-equipment-tasklist.md)
- `[todo]` Remaining work is polish, cleanup, balance, deeper behavior testing,
  and the later non-jewelry enchanting expansion

## Goal

Expand the newly renamed `Enchanting` skill beyond rune production by moving jewelry enchanting and related item-imbue effects out of `Magic` and onto altar-based interactions.

## Core Rules Locked So Far

- Enchanting happens at altars, not via magic spells.
- Rune costs scale by item tier in steps of `5`:
  - sapphire `5`
  - emerald `10`
  - ruby `15`
  - diamond `20`
  - dragonstone `25`
- Amulets are the first jewelry category to redesign.
- Necklaces are reserved for rune-preservation effects tied to the altar/rune they were enchanted with.
- Elemental rings are reserved for elemental spell-damage bonuses tied to their matching altar/element.
- Existing non-elemental ring effects should be audited and, where worthwhile, reassigned to specific altars with proper tier scaling.
- Ring destruction should be removed in all cases except the life ring line.
- `Law` is reserved for teleports and teleport-adjacent utility.
- `Soul` is reserved for the former life-ring survival line.
- `Law` amulets will use charges for guild teleports.
- `Law` rings will use charges for remote banking utility.

## Phase 1: Elemental Amulet Baseline

- `[done]` 1. Lock elemental amulets as the first enchanting target.
- `[done]` 2. Lock rune-cost scaling by gem tier.
- `[done]` 3. Define elemental altar identities for amulets:
  - wind = speed bonus
  - fire = offense bonus
  - earth = defense bonus
  - water = high-roll bias / "accuracy" bonus
- `[done]` 4. Define elemental amulet scaling:
  - wind: `2%` per item tier
  - fire: `3%` per item tier
  - earth: `3%` per item tier
  - water: `3%` per item tier
- `[done]` 5. Decide exact stat hooks for each amulet bonus in code:
  - wind speed multiplier handling
  - fire offense scaling across melee/ranged/magic
  - earth defense scaling across melee/ranged/magic
  - water high-roll bias implementation
- `[todo]` 6. Map legacy enchanted amulets onto the new elemental outputs.

## Phase 2: Non-Elemental Amulet Baseline

- `[done]` 7. Lock the first non-elemental amulet directions:
  - nature = food efficiency
  - mind = crafting XP utility
  - body = gathering XP utility
  - cosmic = skilling wealth
  - soul = retain more items on death
  - death = kill-chain damage bonus
  - blood = max HP increase
  - chaos = secondary-hit proc
- `[done]` 8. Lock the current scaling directions:
  - nature: `15%` increased HP gained per tier, rounded up
  - mind: `3%` bonus XP per tier for all crafting skills
  - body: `3%` bonus XP per tier for all gathering skills
  - soul: retain `1` extra item per tier on death
  - death: `1%` bonus damage per tier, stacks on kill up to `5` times, resets if no kill within `2` minutes
  - blood: `10%` max HP per tier
  - chaos: `10%` chance per tier to perform a second hit at `10%` damage
- `[done]` 9. Explore the exact cosmic amulet mechanic:
  - increase odds of skill-generated bonus loot
  - examples include gem-style bonus finds from gathering/crafting activities
  - confirmed current candidates include:
    - mining gem finds
    - harvesting herb-table rolls / seaweed variant produce
    - a small jungle woodcutting bonus-log roll
  - two-fold direction:
    - increase the chance of existing worthwhile bonus finds
    - add a `3%` chance per tier to grant one additional relevant gathered resource on successful gather actions
  - initial intended resource coverage:
    - logs from woodcutting
    - ore from mining
    - fish from fishing
    - herbs/produce from harvesting
  - initial intended bonus-find coverage:
    - increased gem-find chance while mining ore
  - if the current game does not provide enough meaningful skill-side bonus finds, add more later as part of broader skill expansion
- `[done]` 9a. Build the first combat-special amulet slice.
  - chaos amulets now craft at chaos altars and proc a second hit in melee and projectile combat
  - death amulets now craft at death altars and apply kill-chain bonus damage in runtime combat
  - blood amulets now craft at blood altars and dynamically increase player max HP while equipped
- `[done]` 9b. Build the first non-combat amulet slice.
  - mind amulets now craft at mind altars and increase crafting-skill XP gains in the shared XP path
  - body amulets now craft at body altars and increase gathering-skill XP gains in the shared XP path
  - nature amulets now craft at nature altars and increase food healing
  - cosmic amulets now craft at cosmic altars and improve core gathering rewards:
    - extra-resource proc on woodcutting, mining, fishing, and harvesting
    - boosted mining gem chance
    - herb reroll quality improvement
- `[done]` 10. Lock current skill groupings for XP-utility amulets:
  - `Mind / crafting-processing`
    - Cooking
    - Firemaking
    - Fletching
    - Crafting
    - Smithing
    - Herblaw
    - Enchanting
    - Tailoring
    - Carpentry
  - `Body / gathering`
    - Woodcutting
    - Fishing
    - Mining
    - Harvesting
    - Thieving
    - Agility
- `[done]` 11. Define exact death-handling rules for soul amulets against the current death system.
  - soul amulets add `+1` kept item per tier on top of the normal death baseline
  - they do not protect themselves specially
  - kept-item ordering continues to use the existing item-value rules
  - they do not stack with other future extra-kept-item effects unless explicitly stated later
- `[done]` 12. Decide whether nature/mind/body/cosmic/soul/death/blood/chaos amulets are silver-only, gem-jewelry, or both.
  - standard amulets use the normal gold + gem + string progression
  - silver remains reserved for prayer-aligned neckwear and holy-symbol style equipment

## Phase 2.5: Law Amulet Utility

- `[done]` 12a. Lock the law amulet identity:
  - teleport jewelry
  - each amulet tier will have a different guild destination
- `[done]` 12b. Lock the law amulet charge rule:
  - each law amulet has `3` charges
  - once enchanted, it can be recharged at the law altar without paying additional runes
- `[done]` 12c. Lock the law amulet interaction rule:
  - the player uses one `Use` option on the amulet
  - the destination is selected through the chat window / menu flow
- `[done]` 12d. Choose the exact guild teleport ladder by tier:
  - tier 1: Crafting Guild / Mining Guild
  - tier 2: Woodcutting Guild / Prayer Guild
  - tier 3: Fishing Guild / Cooking Guild
  - tier 4: Heroes' Guild / Wizards' Guild
  - tier 5: Champions' Guild / Legends' Guild

## Phase 3: Necklace Baseline

- `[done]` 13. Reserve necklaces for rune-preservation effects tied to the altar/rune used to enchant them.
- `[done]` 14. Allow necklaces to be equippable in the neck slot for this role.
- `[done]` 15. Lock the necklace effect:
  - chance to avoid consuming `1` rune matching the necklace's altar/rune type when casting spells
- `[done]` 16. Lock necklace tier scaling:
  - `15%` chance per tier to preserve `1` matching rune
- `[todo]` 17. Decide whether every rune type should be supported immediately or rolled out in stages.
- `[done]` 17. Decide whether every rune type should be supported immediately or rolled out in stages.
  - the full necklace concept applies to all rune types
  - actual access/progression will still be staged behind `Enchanting` level requirements
  - those stages remain tied to the future rune/altar ordering after the magic rework
- `[done]` 18. Implement necklace checks in spell rune-consumption logic.

## Phase 4: Elemental Ring Baseline

- `[done]` 19. Reserve elemental rings for elemental spell-damage bonuses.
- `[done]` 20. Lock the elemental ring effect:
  - increase damage of spells matching that ring's element
- `[done]` 21. Lock elemental ring scaling:
  - `5%` extra damage per tier for the matching element
- `[todo]` 22. Decide which spells count as each element once the spell overhaul is complete.
- `[done]` 22. Decide which spells count as each element once the spell overhaul is complete.
  - elemental classification is face-value
  - fire spells use fire bonuses, water spells use water bonuses, and so on
- `[done]` 23. Decide whether the bonus applies only to direct damage or also to elemental side effects later.
  - first pass applies to direct spell damage only

## Phase 10: First Implementation Slice

- `[done]` 38. Build altar-based elemental amulet enchanting.
- `[done]` 39. Build altar-based necklace enchanting for all altar/rune types.
- `[done]` 40. Build altar-based elemental ring enchanting for the four elemental lines.
- `[done]` 41. Move elemental ring spell bonuses into the spell-cast runtime.
- `[done]` 42. Build the first special ring line: chaos/recoil.
- `[done]` 43. Replace the legacy recoil durability pool with tiered activation chance.
  - sapphire-tier uses the existing `Ring of recoil` item as the chaos base result
  - higher tiers are explicit chaos-ring items
  - reflected damage remains `ceil(incoming damage / 10)`
  - activation chance scales from `10%` to `50%`
  - rings no longer break
- `[done]` 44. Build the second special ring line: nature/forging.
- `[done]` 45. Replace the legacy forging guaranteed-success durability model with tiered chance-based forging.
  - sapphire-tier uses the existing `Ring of forging` item as the nature base result
  - higher tiers are explicit nature-ring items
  - iron-smelt rescue chance scales from `10%` to `50%`
  - rings no longer break
- `[done]` 46. Build the third special ring line: cosmic/wealth.
- `[done]` 47. Replace the legacy wealth reroll and KBD-access bonus model with tiered extra-roll chance.
  - dragonstone-tier uses the existing `Ring of wealth` item as the strongest cosmic result
  - lower tiers are explicit cosmic-ring items
  - extra full drop roll chance scales from `5%` to `25%`
  - the old KBD access bonus is removed entirely
- `[done]` 48. Build the soul/life ring runtime.
  - the old `Ring of life` item is now treated as the diamond-tier soul ring
  - lower and higher soul-tier items exist in data/runtime
  - the emergency teleport trigger remains unchanged
  - each tier gets a `5%` chance not to break when it activates
- `[done]` 49. Add altar-based soul ring crafting once a soul altar exists in the altar set.
  - a custom soul altar now exists in the live world
  - soul amulets, soul rings, and soul necklaces now craft through the standard altar enchanting path
- `[done]` 50. Build the first special amulet runtime for chaos, death, and blood.
- `[done]` 51. Build the first skilling amulet runtime for mind, body, nature, and cosmic.
- `[done]` 52. Build the soul amulet runtime.
  - soul amulets now exist as a full gem-tier item line
  - they add `+1` kept item per tier on top of the current death baseline
  - they use the existing item-value ordering and still do not protect stackables
  - soul altar crafting is now live
- `[done]` 53. Add an enchanting-focused validation harness.
  - `tests/myworld/test-enchanting-data.py` now validates the current altar jewelry lines and key special-item mappings
  - `tests/myworld/test-all.sh` includes the new enchanting check

## Phase 5: Special Ring Effect Audit And Repurpose

- `[done]` 24. Identify the currently active ring effects that matter for migration:
  - recoil
  - forging
  - wealth
  - life
  - avarice
- `[done]` 25. Lock the initial altar assignments:
  - chaos = recoil
  - nature = forging
  - cosmic = wealth
  - law = teleports / teleport-adjacent utility (later)
  - soul = survival / former life-ring line (later)
  - blood = rings open for future redesign
  - death = rings open for future redesign
  - avarice = removed
- `[done]` 26. Lock the durability policy change:
  - remove destruction/charge loss from all repurposed ring lines
  - keep life-ring destruction, but allow tiering to reduce destruction chance
- `[done]` 27. Rebalance direction for recoil by tier under the chaos altar:
  - no breaking
  - tier controls chance to activate
  - `10%` to `50%`
- `[done]` 28. Rebalance direction for forging by tier under the nature altar:
  - no breaking
  - tier controls increased chance to successfully smelt iron bars
  - `10%` per tier
- `[done]` 29. Choose an altar identity for wealth.
- `[done]` 30. Move the life-ring line to a future soul-aligned identity.
- `[done]` 31. Drop avarice entirely unless a future redesign makes it worth revisiting.
- `[done]` 32. Choose an altar identity for blood.
- `[done]` 33. Choose an altar identity for death.
- `[done]` 33a. Lock the law ring identity:
  - remote banking utility
  - choose `Use` on the ring, then choose the target item/stack from inventory
  - the full selected stack is sent to the player's bank
- `[done]` 33b. Lock the law ring charge rule:
  - `3` charges per tier
  - law rings are charge-based utility, not durability-break items
- `[done]` 33c. Decide whether law rings recharge for free at the law altar like law amulets, or use a separate recharge rule.
  - law rings recharge for free at the law altar

## Phase 6: Non-Combat Skill Rings

- `[done]` 34. Reserve `body` for gathering-skill ring bonuses.
- `[done]` 35. Reserve `mind` for crafting/processing-skill ring bonuses.
- `[done]` 36. Lock the scaling rule:
  - `3%` bonus to all covered skills per tier
  - rounded up
  - example: level `1` with a `3%` bonus still becomes effective level `2`
- `[done]` 37. Group current non-combat skills:
  - `Body / gathering`
    - Woodcutting
    - Fishing
    - Mining
    - Harvesting
    - Thieving
    - Agility
  - `Mind / crafting-processing`
    - Cooking
    - Firemaking
    - Fletching
    - Crafting
    - Smithing
    - Herblaw
    - Enchanting
    - Tailoring
    - Carpentry
- `[todo]` 38. Decide whether any non-combat skills should be moved between `mind` and `body` after playtesting.

## Phase 7: Ring Behavior Details To Carry Forward

- `[done]` 39. Record the current ring baselines for redesign:
  - recoil currently reflects `ceil(incoming damage / 10)` and breaks after `40` reflected damage total
  - forging currently guarantees successful iron smelts and breaks after `75` successful uses
  - wealth currently improves KBD special-table access by an absolute `+1/128` chance and also grants a reroll when a normal drop-table roll lands on `nothing`
  - life currently teleports at `<= 10%` HP and always shatters
  - avarice currently auto-loots stackable NPC drops directly to inventory/equipment stacks when possible
- `[done]` 40. Translate those ring baselines into tierable altar effects:
  - recoil -> chance to activate, `10%` to `50%`
  - forging -> nature altar, iron-smelt success bonus, `10%` per tier
  - wealth -> cosmic altar, chance for an additional full drop roll, `5%` to `25%`
    - replaces both the old `nothing` reroll and the old KBD-specific access bonus
  - soul / survival -> same trigger as the current life ring, same emergency teleport behavior, but with `5%` per tier chance to avoid destruction
  - blood -> open
  - death -> open
- `[done]` 41. Drop avarice from the redesign set.

## Phase 8: Necklaces And Free Jewelry Space

- `[done]` 42. Confirm standard gem necklaces appear mostly unused compared with rings/amulets.
- `[done]` 43. Audit whether necklaces should become:
  - a second general enchantment slot family
  - teleport-specialized jewelry
  - or remain reserved until later
  - result: necklaces are reserved for universal altar/rune preservation effects

## Phase 9: Spell Migration

- `[todo]` 44. Remove jewelry enchant spells from active `Magic` progression.
- `[todo]` 45. Replace them with altar-based enchanting interactions.
- `[todo]` 46. Move orb imbuing onto altar interactions.
- `[todo]` 47. Remove or repurpose the old enchant/charge spell entries and icons.

## Phase 10: First Implementation Slice

- `[done]` 48. Add a dedicated altar-enchanting plugin path for jewelry.
- `[done]` 49. Add real elemental amulet item outputs for wind, water, earth, and fire across the gem tiers.
- `[done]` 50. Move elemental amulets off the old spell-enchant path and onto altar use with rune costs.
- `[done]` 51. Wire the first elemental amulet effects into runtime combat/equipment handling:
  - wind -> speed multiplier
  - water -> high-roll bias
  - earth -> defense bonus
  - fire -> offense bonus
- Current live scaling:
  - wind: `2%` speed per tier
  - water: `2%` high-roll bias per tier
  - earth: `+1` melee, ranged, and magic defense per tier
  - fire: `+1` melee, ranged, and magic offense per tier
  - `Amulet of accuracy`: `3%` high-roll bias, between water tier `1` and `2`
- `[done]` 52. Use a temporary Enchanting stage curve for elemental amulets until the final altar progression is locked.
- `[done]` 53. Replace the temporary altar-gate curve with the current staged Enchanting progression:
  - tier `1`: unenchanted baseline -> level `1`
  - tier `2`: elemental altars -> level `8`
  - tier `3`: mind / body -> level `15`
  - tier `4`: chaos -> level `22`
  - tier `5`: cosmic -> level `30`
  - tier `6`: nature -> level `38`
  - tier `7`: law -> level `46`
  - tier `8`: death -> level `54`
  - tier `9`: soul -> level `62`
  - tier `10`: blood -> level `70`
- `[done]` 54. Add real altar-enchanted necklace item outputs for all rune/altar lines across the gem tiers.
- `[done]` 55. Move necklaces onto the altar-enchanting framework with the same rune-cost and temporary tier-gate model.
- `[done]` 56. Implement necklace rune preservation in the shared spell rune-consumption path:
  - preserve `1` matching rune
  - `15%` chance per tier
- `[todo]` 57. Add targeted behavior tests for necklace rune preservation and soul-rune support.
- `[done]` 58. Implement `law` amulets:
  - altar-crafted at the law altar
  - `Use` opens a chat/menu destination selector
  - `3` charges per amulet
  - recharge free at the law altar
- `[done]` 59. Implement `law` rings:
  - altar-crafted at the law altar
  - `Use` on an inventory item sends the full stack to the bank
  - `3` charges per tier
  - recharge free at the law altar
- `[done]` 60. Implement altar-crafted soul jewelry once a soul altar exists in the world/object set.
- `[todo]` 61. Add targeted behavior tests for special jewelry effects:
  - law amulet teleport charges and recharge
  - law ring bank-send behavior and recharge
  - soul ring break-survival chance
  - soul amulet extra kept-item count
  - chaos second-hit proc
  - cosmic gather bonuses and gem/herb improvements
- `[done]` 61. Audit and remove legacy enchanted-jewelry inflow:
  - drops
  - shops
  - starter kits / PvP loadouts
  - quest rewards and replacement dialogs
  - direct spell-output paths that still create obsolete jewelry
- `[todo]` 62. Audit dormant legacy enchanting compatibility paths:
  - admin/dev item kits
  - old item defs still kept only for compatibility
  - the old dragonstone amulet line and gem-bonus behavior
- `[todo]` 63. Finish legacy magic-enchant cleanup:
  - remove or repurpose remaining jewelry enchant spells
  - move orb imbuing fully onto altar interactions
  - remove or repurpose obsolete enchant/charge spell assets where practical
- `[done]` 64. Rebalance temporary Enchanting progression once the post-magic altar order is locked.
- `[doing]` 64a. Extend the same altar-tier model into robe-specific altar effects once the cloth baseline is fully migrated:
  - robe enchanting should become altar-family driven rather than one fixed
    altar output per tier
  - the altar/rune used chooses the static robe effect family
  - the player chooses the strength tier they can craft at that altar
  - rune cost should be `10 x selected tier` of the altar's associated rune
  - baseline wool defense persists and selected tier adds the defense growth
    on top
  - currently defined altar-effect draft:
    - `Earth`: magic/melee split defense
    - `Air`: magic/ranged split defense
    - `Water`: magic/ranged/melee split defense
    - `Fire`: magic-only defense plus `+10%` final total magic defense per
      equipped piece
    - `Body`: `+33%` HP regeneration per equipped piece
    - `Mind`: reduce debuff effectiveness by `20%` per equipped piece
    - `Nature`: mitigate poison damage by `1` per equipped piece
    - `Cosmic`: `10%` defensive reroll chance per equipped piece, keeping the
      better roll
    - `Chaos`: recoil-style reflect for `5%` of incoming damage per equipped
      piece, applying on every hit
    - `Law`: average incoming pre-armor damage rolls by adding one extra roll
      per equipped piece and using the mean, rounded down
    - `Death`: `+10%` all-style defense per equipped piece for every `25%` HP
      lost
    - `Soul`: rechargeable out-of-combat shield worth `20%` of max HP per
      equipped piece
    - `Blood`: `5%` spell lifesteal per equipped piece
  - first-pass selected-tier defense growth is now:
    - hat: `1 + floor((tier - 1) / 2)`
    - top: `4 + (tier - 1) * 3`
    - skirt: `3 + (tier - 1) * 2`
- `[todo]` 65. Add player-facing explanation/polish for enchanting jewelry effects:
  - tooltip/examine support
  - clearer charge messaging
  - clearer altar interaction text

## Notes

- Rings are not a clean slate. Several already have active code hooks and must be handled individually.
- Standard necklaces look much more available for redesign than rings do, and are now assigned as the universal rune-preservation jewelry line.
- Existing ring effects may be dropped if tiering them into the new altar model proves awkward or low-value.
- `Law` jewelry should avoid cluttered per-destination item options. Teleport location selection should happen through the normal chat/menu flow after a single `Use`.
- Standard enchanting amulets use the normal gem-jewelry ladder; silver remains the prayer-aligned exception.
- The first live enchanting implementation no longer uses the old temporary
  gem-tier gate model.
- Current altar progression is shared across jewelry, robe binding, and the
  broader Enchanting progression foundation.
- Full balance is still deferred until the complete cloth/staff/spell path is
  in place; during migration, prefer adjusting player damage output and related
  offense shaping rather than repeatedly weakening partial robe implementations.
- Current robe-enchanting coverage is intentionally structural, not final-effect
  complete:
  - altar-bound robe items are live
  - selected-tier robe enchanting is now live through a tier-selection menu
  - selected-tier level gates are live
  - baseline robe defenses persist through enchanted forms
  - first-pass altar-specific robe bonuses are now live on the enchanted robe
    outputs
- Current intended robe-enchanting direction is now:
  - tier `1` remains the plain wool baseline
  - altar identity and robe tier are separate choices
  - the same altar family should be craftable at multiple tiers once unlocked
  - effects should be mostly defense-driven or magic-oriented, not simple raw
    damage bonuses copied from jewelry
  - `Mind` should mitigate debuff effectiveness rather than copying the old
    law-teleport utility theme
  - enemy debuffs still need a broader audit after the combat rewrite, since
    current NPC debuff coverage and correctness are not yet trusted

## Current Ring Values

- `Recoil`
  - Current chaos-ring line is proc-based, not the old always-on durability
    model.
  - Tier chances are `10%`, `20%`, `30%`, `40%`, and `50%`.
  - On proc, it reflects `ceil(incoming damage / 10)` on incoming melee and
    projectile hits.
  - Current runtime does not use the old `40`-damage reflection pool for the
    active chaos ring line.
- `Forging`
  - Converts iron-smelting failures into successes.
  - Has `75` uses before shattering.
- `Wealth`
  - Adds a second chance when a normal drop-table roll hits `nothing`.
  - On the KBD custom access roll, it increases the access chance by an absolute `1/128`.
- `Life`
  - Triggers at `<= 10%` HP.
  - Teleports the player to respawn and always shatters.
- `Avarice`
  - Does not improve rarity or quantity.
  - It auto-loots stackable NPC drops directly to the player when possible.

## Change Log

- `2026-03-19` Created the enchanting task tracker from the post-runecrafting design notes, including elemental amulet identities, ring repurpose direction, and current legacy ring baselines.
- `2026-03-19` Updated ring redesign direction: avarice dropped, recoil changed to tiered activation chance, forging changed to tiered iron-smelt success chance, wealth changed to tiered additional-drop-roll chance, and life set to keep destruction with a tiered chance to survive.
- `2026-03-19` Assigned `cosmic` to wealth and `body` to life, with the new wealth model fully replacing the old KBD-specific bonus path.
- `2026-03-19` Reserved necklaces for general altar/rune preservation effects and moved forging from fire to nature.
- `2026-03-19` Added death, blood, and chaos amulet directions as kill-chain damage bonus, max HP increase, and secondary-hit proc.
- `2026-03-19` Reassigned elemental rings to elemental spell-damage bonuses at `5%` extra damage per tier, since necklaces now own rune preservation.
- `2026-03-19` Reserved `law` for teleport utility, moved the former life-ring line to a future `soul` identity, and split the current non-combat skills into `body` gathering bonuses and `mind` crafting-processing bonuses.
- `2026-03-19` Added the first non-elemental amulet directions: nature food efficiency, mind/body XP utility, cosmic skill-loot utility, and soul extra item retention on death.
- `2026-03-19` Added the law jewelry interaction rules: law amulets are charged guild teleports selected through the chat window, and law rings are charged remote-banking utility for sending a selected inventory stack to the bank.
- `2026-03-19` Locked several remaining jewelry rules: law rings recharge for free, non-prayer amulets stay on the normal gem ladder, necklaces cover all rune types with staged Enchanting progression, and elemental ring spell matching is literal by spell element.
- `2026-03-19` Started implementation: elemental amulets now have real altar-based item outputs, runtime effect hooks, and amulet enchanting has been removed from the old spell path in favor of altar use. Temporary Enchanting level gates are in place until final altar progression is decided.
- `2026-03-19` Continued implementation: necklaces now have real altar-based item outputs for every altar/rune line, altar enchanting supports them directly, and rune preservation is hooked into the shared spell rune-consumption logic.
- `2026-03-19` Continued implementation again: elemental rings, chaos recoil rings, nature forging rings, cosmic wealth rings, soul ring runtime, and the full law amulet/ring utility line are now wired into altar enchanting and runtime behavior.
- `2026-03-28` Replaced the old temporary altar-gate placeholders with the current `1/8/15/22/30/38/46/54/62/70` progression model, and added the first altar-bound wool robe outputs so robe enchanting can progress before final altar-specific robe effects are designed.
