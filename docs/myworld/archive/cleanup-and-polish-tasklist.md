# Cleanup And Polish Tasklist

Authoritative prerequisites:

- Check
  [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
  before using this tracker for item-family or shop decisions.
- Record removals/replacements in
  [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md).

## Summary

Track the broad cleanup and player-facing polish work that now spans UI,
item naming, examine clarity, legacy item-source retirement, and general presentation
cleanup across `MyWorld`.

This exists for narrower cleanup/polish detail. For the consolidated broad
backlog, use
[remaining-work.md](/home/justin/Core-Framework/docs/myworld/remaining-work.md)
first.

For the broader source-retirement, re-tiering, and placement pass, use
[content-cleanup-and-distribution-plan.md](/home/justin/Core-Framework/docs/myworld/content-cleanup-and-distribution-plan.md)
as the primary working document. This tasklist stays focused on cleanup/polish
coordination rather than the full item-source rebalance itself.

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a dependency or design decision

## Current Focus

- `[doing]` establish clearer player-facing UI and item naming for the new
  combat, crafting, and enchanting systems
- `[doing]` remove obvious obsolete shops, drops, spawns, and world sources for
  retired item lines
- `[doing]` comb through live equipment inflows family-by-family to remove
  retired progression items that still leak in through old content
- `[doing]` keep a concrete source-cleanup ledger in
  [source-cleanup-audit.md](/home/justin/Core-Framework/docs/myworld/source-cleanup-audit.md)
  so removed versus remaining acquisition paths stay explicit
- `[doing]` drive the full source-retirement and re-tiered distribution sweep
  from
  [content-cleanup-and-distribution-plan.md](/home/justin/Core-Framework/docs/myworld/content-cleanup-and-distribution-plan.md)
  so shop/drop/spawn placement work stays centralized

## Phase 1: Immediate UI Cleanup

- `[todo]` 1. Fix the `Equipment status` layout so right-column labels fit
  cleanly on screen.
  - shorten `Weapon Power` to `Weapon Pow`
  - tighten the left/right spacing so the stat block no longer clips on the
    right edge
- `[doing]` 2. Remove `Fletching` from player-facing systems now that its live
  recipes have been folded into `Crafting`.
  - live production sessions and reward paths now use `Crafting`
  - remaining work is client/menu presentation cleanup where old skill slots are
    still hard-coded
- `[todo]` 3. Audit the stats/equipment screen for any other clipped labels,
  awkward spacing, or stale combat-stat wording caused by the new systems.
- `[todo]` 4. Audit the skill guide and related player-facing UI for any
  lingering references to retired or dormant skill paths.
- `[todo]` 4a. Remove old general-options combat-style toggles that still imply
  the retired three-skill melee model.
- `[todo]` 4b. Update skill info windows so they reflect the live `MyWorld`
  skill outputs, unlocks, and progression rather than old stock content.

## Phase 2: Naming, Examine, And Explanation Pass

- `[todo]` 5. Keep upper-left hover text minimal and consistent with the rest
  of the game:
  - item name only
  - no heavy mechanical detail in the hover text
- `[todo]` 6. Surface the new core combat/equipment meanings clearly:
  - melee, ranged, and magic offense
  - melee, ranged, and magic defense
  - weapon speed
  - high-roll or low-roll bias effects
  - rune-preservation and other enchanting effects
- `[todo]` 7. Put exact mechanics in the surfaces that fit the game best:
  - examine text for item-specific effects
  - equipment/status UI for stat breakdowns
  - skill or system-specific UI where needed
- `[todo]` 8. Add player-facing explanation support for the new enchanting
  jewelry effects through naming and examine text, not heavy hover tooltips.

## Phase 3: Naming Cleanup

- `[doing]` 9. Replace legacy enchanted-jewelry names that still reflect their
  old uses rather than their current effects.
- `[todo]` 10. Use a clearer direct naming convention for altar-enchanted
  jewelry as the baseline first pass:
  - `[Gem] [Jewelry] of [Rune]`
  - example: `Sapphire Ring of Nature`
- `[doing]` 11. Pair the clearer names with examine support so players
  can tell both what an item is aligned to and what it actually does.
- `[doing]` 11a. Move jewelry effect detail into examine text before adding any
  heavier tooltip UI, so it stays consistent with existing item presentation.
- `[doing]` 11b. Run one limited visual-distinction test on jewelry to see
  whether a subtle secondary tint reads cleanly before attempting any broader
  altar-color pass.
- `[todo]` 12. Audit other retired or repurposed item lines for misleading
  legacy names once jewelry naming is stable.

## Phase 4: Legacy Source Cleanup

- `[todo]` 13. Audit shops for obsolete or retired item lines that should no
  longer be sold.
  - includes old skirt and wizard-color stock that no longer belongs in active
    progression
  - `2026-04-09`: defer the full shop pass until the remaining item-content
    work is finished; the new metal/wood ladders and black/white/dragon
    exception sets mean most inventories will need one wider rebalance sweep
    after content settles
- `[todo]` 14. Audit world spawns for obsolete colored clothing and other
  retired equipment that still enters play through map content.
- `[todo]` 15. Audit NPC drops and other non-shop sources for retired items
  that should no longer enter the active progression path.
- `[todo]` 16. Decide which remaining legacy items should stay only for
  compatibility/lore versus being fully removed from active acquisition.
- `[doing]` 16a. Continue the live source sweep for retired magic-gear lines:
  - old wizard-color clothing and hats
  - retired battlestaff/orb items, including old crafting outputs
  - god-themed magic wear already hidden from new inflow
  - leave quest-only compatibility cases alone unless they become blockers
  - keep an explicit note of every quest-only holdover so it does not get
    forgotten during later prayer/magic cleanup

## Phase 5: Broader Presentation Polish

- `[todo]` 17. Revisit production-window polish once the first naming/examine
  pass is defined.
- `[todo]` 18. Audit crafting, enchanting, and combat messages for stale or
  unclear wording after the new system changes.
- `[todo]` 19. Revisit the broader item/examine/UI presentation pass after the
  first naming and examine cleanup are live.
- `[todo]` 20. Audit remaining multi-output production flows and migrate any
  leftover crafting-menu candidates onto the newer production UI.
- `[todo]` 20a. Rework magic presentation and combat QoL:
  - replace the text-list spellbook with a more visual spell-selection UI
  - wire in spell icons and upgraded magic/arrow combat visuals where assets
    are available
  - add a `repeat last offensive cast` style toggle so offensive magic can flow
    more smoothly during combat
  - keep non-offensive spell use able to interrupt temporarily, then resume the
    stored offensive cast choice

## Phase 6: QoL And Client Experience

- `[todo]` 21. Check whether multiple players can talk to the same NPC at once,
  and if not, remove that old single-conversation lock where safe.
- `[todo]` 22. Separate client scaling controls for world view and UI so the
  interface can be enlarged without forcing the game world to zoom in.
- `[todo]` 23. Review map/chunk loading behavior to reduce visible pop-in:
  - inspect whether a larger loaded area is feasible on the modern client
  - inspect whether chunk transitions can be made less abrupt
  - document any hard engine constraints if seamless loading is not practical

## Recommended Order

1. Fix the obvious stats-screen clipping and finish removing stale `Fletching`
   presentation from the skill menu.
2. Establish the first naming/examine explanation surface while keeping hover
   text minimal.
3. Rename enchanted jewelry into the direct `[Gem] [Jewelry] of [Rune]`
   convention.
4. Sweep shops, drops, and spawns for retired item inflow.
5. Continue broader UI and presentation cleanup after those foundations are in
   place, then take the QoL/client-experience pass.
