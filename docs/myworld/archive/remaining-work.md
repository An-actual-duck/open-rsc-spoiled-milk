# MyWorld Remaining Work

This is the consolidated broad backlog for `MyWorld`.

It is split into four buckets:

- cleanup
- new item distribution
- minor work
- major work

Use this file as the main high-level backlog. Narrow subsystem docs can hold
implementation details, but broad status should be reflected here.

## Cleanup

- Remove retired item families from remaining live drops:
  - `Chain`
  - `Medium helms`
  - `Square shields`
  - `Metal skirts`
  - obsolete `Black`/`White` standard-tier stock
- Finish the shop cleanup and retier pass:
  - remove invalid legacy stock
  - rebalance pricing
  - align shop ceilings with the current tier rules
  - preserve quest functionality where shops are also compatibility sources
- Continue the source-cleanup audit for:
  - NPC drops
  - ground spawns
  - quest rewards
  - dialogue exchanges
  - special/minigame sources
- Do the wording cleanup pass across quests, NPC dialogue, and item descriptions
  where old names or old systems are still referenced
- Continue retiring or explicitly documenting compatibility-only legacy skills
  and systems:
  - `Firemaking`
  - dormant `Tailoring`
  - any remaining `Fletching` references
- Finish the remaining potion-source cleanup so old potion lines stop leaking
  into live content
- Sweep compatibility-only retired item references so old potion, staff,
  battlestaff, and orb families are clearly documented as inert where they still
  appear in shared rules

## New Item Distribution

- Audit all shop inventories against the new ladders and fill missing early/mid
  tier stock where shops are supposed to support progression
- Audit NPC drops for new-tier distribution:
  - low tiers added below old bronze
  - shifted rune placement
  - newer ammunition tiers
  - leather-family outputs
  - revised staff and robe baselines
  - use [equipment-drop-redistribution-audit.md](/home/justin/Core-Framework/docs/myworld/equipment-drop-redistribution-audit.md)
    as the current source-of-truth for NPC bands and families
- Decide where the new higher tiers should enter the game:
  - premium shops
  - stronger NPC families
  - quests
  - rare normal drops
  - rare drop table access
- Build a deliberate live placement map for:
  - leather families
  - ammunition families
  - robe/magic entry gear
  - utility staffs
  - any standard craftable family that still lacks practical acquisition

## Minor Work

- Finish combat cleanup that is already functionally complete but still needs
  validation or consistency passes:
  - manual playtesting
  - tuning after real encounter feedback
  - further NPC family/profile auditing
- Audit skills, crafting flows, and item families for outliers that no longer
  match the current MyWorld formulas:
  - 10-tier standard ladders
  - 1-70 gating where intended
  - crafting-owned families staying under `Crafting`
  - explicit exceptions being documented instead of silently drifting
- Finish the broader tool and resource normalization work:
  - equip gating for tools
  - dragon tool tier-11 exceptions
  - raw ingredient and recipe consistency checks
- Continue the naming/examine/UI cleanup so players can understand the new item
  and system meanings
- Close the client/server item-definition gap for live MyWorld content:
  - prioritize enchanted jewelry
  - then sweep the remaining client-missing wearable/usable items by family
  - use [client-item-coverage-audit.md](/home/justin/Core-Framework/docs/myworld/client-item-coverage-audit.md)
    as the source-of-truth
- Audit quests and shortcut rewards for remaining legacy skill references,
  outdated items, or wrong remaps
- Continue documenting changed families and compatibility holdovers as they are
  discovered

## Major Work

- `Fishing` overhaul
  - rod-based progression
  - spot-quality progression
  - no failed gathering
  - catch quality and speed as the main advancement path
- broader gathering overhaul
  - always-successful ore/log/harvest gathering
  - yield-weight progression
  - multiplayer node participation rules
  - finalized unlock and yield scaling
- `Prayer` conversion
  - allocation pool model
  - altar-based god-line switching
  - new offensive, defensive, and skilling prayer sets
  - prayer UI overhaul and icon/content pass
  - god-aligned prayer gear:
    - a possible later robe-style head-slot piece
    - possible later god-aligned endgame bonuses
    - any future specialty shop/reward distribution beyond the first live drop pass
- larger `Herblaw` overhaul beyond the current remap
  - finish the new potion runtime/content economy
  - remove remaining obsolete behavior
  - ensure ingredients and outputs all have good live use
- broader content distribution and economy rebalance once the cleanup pass is
  done
- any future specialty-content passes for retired or repurposed families:
  - god-aligned shops/items
  - specialty black/white uses
  - reused square shields or medium helms
  - named high-end staves

## Supporting Docs

- [source-cleanup-audit.md](/home/justin/Core-Framework/docs/myworld/source-cleanup-audit.md)
- [equipment-drop-redistribution-audit.md](/home/justin/Core-Framework/docs/myworld/equipment-drop-redistribution-audit.md)
- [content-cleanup-and-distribution-plan.md](/home/justin/Core-Framework/docs/myworld/content-cleanup-and-distribution-plan.md)
- [gathering-rework-plan.md](/home/justin/Core-Framework/docs/myworld/gathering-rework-plan.md)
- [prayer-rework.md](/home/justin/Core-Framework/docs/myworld/prayer-rework.md)
- [client-item-coverage-audit.md](/home/justin/Core-Framework/docs/myworld/client-item-coverage-audit.md)
- [goals.md](/home/justin/Core-Framework/docs/myworld/goals.md)
