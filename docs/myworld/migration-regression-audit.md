# MyWorld Migration Recovery Record

This document used to track content that drifted back toward inherited
OpenRSC/Cabbage behavior during the standalone migration. That recovery sweep
is now considered complete.

Keep this file as a closure record and guardrail summary. New regressions should
be tracked in `work-items.md` only if they represent active follow-up work.

## Closure Status

Status: complete for the current migration pass.

The recovery pass restored or verified the major areas that were called out
during live testing:

- MyWorld loc-file loading was restored for custom runecraft content.
- Overworld rune altars were returned to direct altar interaction rather than
  legacy mysterious-ruins entry flow.
- Altar theming was brought forward with altar glyphs, obelisks, and rune-orb
  visuals.
- Shop stock was audited and corrected where stale IDs produced placeholder
  `Unobtanium` or inherited material progression.
- Standard shops were brought back toward the MyWorld material model where
  they are intended to sell progression gear.
- Drop tables were audited for obvious retired-item inflows.
- Crafting and production recipes were reviewed for legacy outputs.
- Standard staff attunement was brought into the same all-altars enchantment
  path as the other staff tiers.
- Item ID/client definition mismatch issues were investigated and fixed for the
  live item families that were blocking testing.
- Weapon-family and armor-stat drift was audited enough to continue normal
  feature work instead of treating the repo as in migration triage.

## Reclassified Follow-Up

The following are no longer considered migration recovery blockers. They remain
normal content, polish, or future-system work:

- retired item cleanup in edge-case drops, shops, quest rewards, or world spawns
- player-facing text cleanup for systems whose names changed
- any newly discovered potion-source leak
- high-end reward distribution and shop tuning
- further leather/cloth tuning discovered through play
- quest shortcut branch-outcome field testing
- fishing catch and side-reward tuning after live sessions
- summoning catalog tuning after field data

## Guardrail Method

For future regression work:

1. define the MyWorld intended model
2. identify live source files and runtime hooks
3. compare live data against MyWorld intent
4. fix the content
5. add or update a focused guardrail test when the issue is likely to recur

The project should not rely on memory for important migration-era behavior.
Anything that caused a live regression once should either be represented in a
source-of-truth document or guarded by a test.
