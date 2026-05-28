# Runecrafting Tasklist

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a design decision or dependency

## Current Focus

- `[done]` The intended simplified rune-production migration is complete.
- `[done]` The old special runecraft-only area now remains only as unreachable
  legacy content kept in code for possible future reuse.

## Goal

Replace the current Cabbage runecraft loop:
- mine special rune stone
- chisel into talisman blanks
- imbue talismans
- use talisman to enter altar
- craft inside altar

With the simplified loop:
- mine `Stone`
- use `Stone` on altar
- receive rune output

This tasklist is the active working tracker for that migration.

## Phase 1: Core Loop Replacement

- `[done]` 1. Rename `Rune Stone` to `Stone` in the item definition layer.
- `[done]` 2. Change the altar entrance objects into the actual crafting target.
- `[done]` 3. Remove the requirement to teleport into altar interiors to craft runes.
- `[done]` 4. Make altar interaction consume `Stone` directly and produce runes directly.
- `[done]` 5. Remove talisman checks from the rune-crafting path.
- `[done]` 6. Make all altar defs usable through the simplified direct-crafting path, including law, death, and blood.

## Phase 2: Progression Rebalance

- `[done]` 7. Rework altar level requirements so all elemental runes are available at level 1.
- `[done]` 8. Lower non-elemental rune requirements to the new intended progression.
- `[done]` 9. Review altar XP values and rebalance if needed for the simplified loop.
- `[done]` 10. Verify rune multiplier behavior still makes sense for the new progression.

## Phase 3: Mining Source Migration

- `[done]` 11. Identify the generic world rock object ids that should produce `Stone`.
- `[done]` 12. Add `Stone` mining to those selected rock nodes.
- `[done]` 13. Remove dependence on the special runecraft mining object for normal progression.
- `[done]` 14. Leave the old special runecraft mining area inaccessible for now
  instead of deleting it outright, so it remains available for future reuse if
  a better purpose appears later.

## Phase 4: Legacy Mechanic Unhooking

- `[done]` 15. Unhook talisman locating behavior.
- `[done]` 16. Unhook uncharged talisman creation from the core skill flow.
- `[done]` 17. Unhook talisman imbuing from the core skill flow.
- `[done]` 18. Unhook cursed talismans from the core skill flow.
- `[done]` 19. Unhook enfeebled talismans from the core skill flow.
- `[done]` 20. Unhook tiaras and any altar-access items that no longer serve a purpose.
- `[done]` 21. Hide or otherwise retire obsolete runecraft-only items from normal progression.

## Phase 5: Cleanup And Completion

- `[done]` 22. Remove or repurpose obsolete altar-entry logic.
- `[done]` 23. Remove or repurpose obsolete altar-interior logic and exit dependence.
- `[done]` 24. Remove or repurpose unused runecraft NPC/map content if it no longer serves the simplified system.
- `[done]` 25. Verify all rune types craft correctly through blood.
- `[done]` 26. Confirm law, death, and blood are fully implemented under the new direct-crafting path.

## Phase 6: Skill Identity Transition And Progression Cleanup

- `[done]` 27. Rename the skill from `Runecraft` to `Enchanting`.
- `[done]` 28. Update skill-facing text and unlock messaging.
- `[done]` 29. Preserve rune production as the first supported subset of the new `Enchanting` skill.
- `[done]` 30. Keep the player-facing skill as `Enchanting` instead of moving
  to `Magecraft`.
- `[done]` 31. Align rune production with the shared altar-tier progression:
  - elements at level `1`
  - mind `8`
  - body `15`
  - chaos `22`
  - cosmic `30`
  - nature `38`
  - law `46`
  - death `54`
  - soul `62`
  - blood `70`
- `[done]` 32. Add direct soul-rune altar support to the simplified altar path.
- `[done]` 33. Make rune multipliers scale from each rune's new unlock level
  instead of the old absolute thresholds.

## Notes

- Runecraft migration remains the backend foundation for the player-facing
  `Enchanting` skill.
- The intent is to simplify and finish rune production first.
- Broader altar-based item and staff work comes after the rune system is stable.

## Change Log

- `2026-03-19` Created task tracker from the runecrafting migration plan. Current work starts in Phase 1.
- `2026-03-19` Completed the first core migration slice: `Stone` naming, direct altar crafting, no talisman requirement, and direct support through blood altars.
- `2026-03-19` Unhooked talisman locate/buff mechanics and removed talisman use from the active rune-creation path.
- `2026-03-19` Lowered altar requirements to the first simplified progression pass, with all elemental runes now available at level 1.
- `2026-03-19` Added `Stone` mining to the generic rock pair `98/99` as the first broad world source, while leaving the old special rune-stone node in place temporarily.
- `2026-03-19` Removed Rune Mysteries as an active gameplay path, including Sedridor/Aubury teleport access and the altar-use quest gate. The old interior rune-stone area remains only as unreachable legacy content for now.
- `2026-03-19` Rebalanced altar XP for the simplified direct-crafting loop and completed rune multiplier support through blood, with a softer first-pass multiplier curve for lower-tier runes.
- `2026-03-19` Retired the remaining talisman item layer from normal progression by stripping stale Locate prompts and rewriting those items as obsolete relics. No active tiara mechanics remained to remove.
- `2026-03-19` Added a dedicated runecraft migration validator and confirmed all altar defs through blood, direct rune support, and obsolete item retirement are wired correctly.
- `2026-03-19` Retired the legacy rune-stone interior from active world loading by removing the dedicated runecraft scenery/NPC loc loads and the obsolete mine-exit portal behavior.
- `2026-03-19` Renamed the player-facing skill identity to `Enchanting` while preserving the legacy runecraft backend alias for compatibility. Updated visible potion and cape text to match.
- `2026-03-28` Extended the simplified altar path through soul, and aligned the
  direct rune-production ladder with the shared `1/8/15/22/30/38/46/54/62/70`
  Enchanting progression.
- `2026-03-29` Rebased rune multipliers onto each rune's current unlock level,
  with another output every 10 levels after unlock and continued multiplier
  growth all the way to level `99`.
- `2026-03-29` Marked the old special runecraft-only area as intentionally done
  in its current unreachable state: no active access, but no forced code/data
  deletion unless a future reuse direction appears.
