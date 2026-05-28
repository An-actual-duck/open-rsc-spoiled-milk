# MyWorld Changed And Removed Content

This file is the consolidated ledger of major content changes that materially
alter active `MyWorld` gameplay.

It is intentionally broader than an item-only retirement note. Use it to record:

- removed content
- replaced content
- repurposed content
- renamed content
- major system-level content shifts that affect future audits

For the narrower item-family retirement ledger, also keep
[retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md)
up to date.

## Core World Direction

- `MyWorld` is `PvM-only`.
  - PvP, duels, wilderness/OpenPK, and PK-driven progression are not part of
    the target design.
- Combat progression is centered on `Melee`, `Ranged`, and `Magic`.
  - old `Attack`, `Strength`, and `Defense` progression is consolidated into
    `Melee`
- The standard progression baseline is now `10` tiers.
  - `Rune` is tier `10`
  - `Dragon` and similar capstones are explicit tier `11` exceptions

## Retired Or Removed Standard Families

- `Chain` equipment
  - retired from active standard progression
  - visuals/palettes are being reused for leather work
- `Medium helms`
  - retired from active standard progression
- `Square shields`
  - retired from active standard progression
  - kept dormant only for possible later reuse or explicit special exceptions
- `Metal skirts`
  - retired from active standard progression
  - standard leg pieces should carry the progression instead
- `Black` and `White` equipment as normal tier ladder entries
  - retired from standard progression
  - reserved for possible specialty or god-aligned content later
- standard `Battlestaff` / orb progression
  - retired from active standard progression
- old vanilla elemental staff names such as `Staff of air` / `Staff of fire`
  - replaced by rune-first standard-staff products such as `Air Staff` and
    `Fire Staff`
  - standard staff attunement remains an active tier-1 path
- `Opal ring` as active progression content
  - retired from live crafting/enchanting progression

## Replaced Progression Ladders

- old metal ladder starting at `Bronze`
  - replaced by `Tin -> Copper -> Bronze -> Iron -> Steel -> Mithril -> Titan Steel -> Adamantite -> Orichalcum -> Rune`
- old `Rune as top normal tier`
  - replaced by `Rune as tier 10`
- old direct jewelry-enchant spell outputs
  - replaced by altar-based enchanting
- old elemental-staff identity as the main staff ladder
  - replaced by wood-tier staffs plus altar attunement
- old ranged-family assumptions tied tightly to legacy metals
  - replaced by the newer ammunition and family tiering pass

## Magic And Enchanting Changes

- `Runecraft` has been migrated in direction toward `Enchanting`
  - active rune production is simplified
  - old talisman-heavy flow is no longer the intended live path
- standard staff progression uses plain wood-tier staffs
- elemental attunement belongs to enchanting, not to separate base staff items
- named specialty staves remain special cases
  - utility staves such as `Dramen staff` and the renamed `Wizard staff`
    remain outside the standard ladder
  - stronger named staffs are intended to be the magic tier-11 counterparts and
    need a later dedicated balance pass
- staff enchanting and elemental necklaces now work through rune-cost negation
  rather than the old legacy assumptions

## Potion And Herblaw Changes

- old combat-stat potions are no longer the intended main line
- the active main-line direction is now utility/status/timed potions, including:
  - `Potion of Insight`
  - `Potion of Regeneration`
  - `Weapon Poison`
  - `Cure Poison`
  - `Potion of Speed`
  - `Potion of Luck`
  - `Potion of Notation`
  - magic/melee/ranged resistance potions
  - super variants for the designated higher-tier lines
- `Enchanting potion` and `Super enchanting potion`
  - retired from the live plan
- old runecraft/enchanting potion paths
  - retired
- quest and special potions remain outside the main-line cleanup

## Prayer Changes

- Prayer is planned to move from a drain-over-time pool to an allocation system
- prayers will belong to three god lines:
  - `Zamorak`
  - `Saradomin`
  - `Guthix`
- switching god lines will happen at altars
- offense, defense, and skilling prayer sets are intended to be stackable under
  the allocation model
- prayer gear is intended to add usable prayer points while worn, rather than
  slowing drain
- prayer robe factionization has started:
  - old `Druids robe` items are now the `Guthix` robe base
  - old `Priest robe` / `Priest gown` items are now the `Saradomin` robe base
  - `robe of Zamorak` is prayer-only and no longer carries magic bonus
  - `Monks robe` remains generic non-affiliated prayer gear for now
- prayer combat identity is now equipment-based rather than a separate unique
  prayer-attack system
  - staffs stay normal magic items until altar choice
  - enchanted staffs remain the magic path
  - blessed staffs become the prayer path and retain their wood-tier identity
  - maces, crossbows, and paladin shields are intended prayer-supporting
    equipment families

## Gathering And Skill-Flow Changes

- `Fishing` is planned to move away from bait/feather/tool clutter toward rod
  and spot quality as the main progression model
- `Woodcutting`, `Mining`, and `Harvesting` are planned around:
  - no failure
  - yield as the progression lever
  - tool tier and skill contributing to quantity/weighting
- `Stone` gathering is planned as a non-depleting mining case
- `Fletching` has been folded into `Crafting`
- `Tailoring` remains dormant rather than being treated as an active live skill
- `Firemaking` is a retired compatibility skill, not a live progression skill

## Shop And Source Direction Changes

- only magic shops should sell clothing-type magic gear:
  - hats
  - robe tops
  - robe bottoms
- tailors are the intended cloth/leather supply shops
  - current starter direction is `cloth`, base `leather`, `needle`, `thread`,
    and a small authored early leather set range
- quest functionality should be preserved when cleaning up shops and other
  sources
- apothecaries are utility/quest support, not the long-term model for broad
  finished-potion sales

## Content Cleanups Already Locked In Direction

- direct old potion inflow is being removed from shops, drops, and cert paths
- obsolete battlestaff/orb crafting has been reduced to a retirement blocker
- generic leather body leakage from broad stores has been removed
- magic shops are being redirected toward rune stock plus entry-level magic
  gear rather than old elemental-staff ladders
- tailors are being redirected away from generic clothing stock and toward
  cloth/leather support

## Compatibility Holdovers That Must Stay Explicit

- quest-critical clothing items may remain temporarily as compatibility sources
  until quests and dialogue are fully updated
- special dragon-tier items may still use otherwise retired counterpart forms
- retired families should be treated as dormant compatibility or later specialty
  content only when explicitly documented

## Maintenance Rule

When a family or system changes the answer to any of these questions:

- is it live?
- is it retired?
- is it compatibility-only?
- is it repurposed?
- is it renamed?
- is it now owned by a different skill/system?

record that change here.
