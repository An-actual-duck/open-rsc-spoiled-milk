# Documentation Audit — 2026-08-21

## Purpose and baseline

This audit reconciles active MyWorld planning and the player-facing overview
against published `main` and the latest release. The exact baseline is commit
`daae1718c8a24cbafd7f71faf6773292dedcf330`, which is both current `main` and
the `v0.2.75` release tag.

The review covered:

- the repository-root player overview;
- the MyWorld index, roadmap, and work-item rollup;
- every file currently classified as an in-progress plan;
- recent merge history and GitHub release notes through `v0.2.75`;
- explicit status, next-branch, release-pending, and validation-pending claims;
- Core/World Editor/Sprite Baker project ownership boundaries.

This is a source-of-truth and classification audit. It does not claim a fresh
manual playthrough of every in-game guide page or every historical plan detail.

## Confirmed shipped baseline

| Area | Current state | Release evidence |
| --- | --- | --- |
| Enchanting rune XP | Geometric diminishing returns are live | `v0.2.50` |
| Devotion blessing/destruction | Accepted transaction, cost, limit, and cleanup rules are live | `v0.2.55` |
| Layered world | Signed layered loader, protocol, resident terrain, minimap, and deployment package are the production path | `v0.2.57`–`v0.2.63` |
| Cleric and Blessing | Twelve launch Cleric spells, Blessing, sigils, Holy Power, HUD status, and Mage/Cleric tabs are live | `v0.2.64` |
| Combat modernization | A01–A11 behavior-preserving ownership and characterization program is complete | `v0.2.67` |
| Production UI | Remember last input and Keep open are live | `v0.2.64`, `v0.2.69` |
| Monster Slayer | Six playable tiers, tasks, promotions, typed shops, and 30-to-40 satchel capacity are live | `v0.2.70` |
| Slayer kill credit | One eligible top-damage contributor receives task credit | `v0.2.71` |
| Pinned UI | Pinned panels permit world input outside their rendered bounds | `v0.2.72` |
| Projectile cover | Walls/closed doors block both sides; fences block hostile NPC projectiles but permit player/allied projectiles | `v0.2.73`, `v0.2.74` |
| Gorak fixture | Developer-only Sprite Baker visual-test NPC is present without production content hooks | `v0.2.75` |

## Corrections made by this audit

- Updated the roadmap's stale latest-release claim from `v0.2.9` to
  `v0.2.75` and replaced its old immediate-priority list.
- Updated the Monster Slayer plan from “release pending” to its actual shipped
  state, including later kill-credit and Sir Radimus follow-ups.
- Updated the root README with major shipped systems that were missing from its
  player-facing summary: Cleric/Blessing, Monster Slayer, production memory,
  pinned panels, and the production layered loader.
- Reclassified nine completed implementation/audit records from
  `in-progress-work-plans/` to `completed-work-plans/`.
- Reclassified the completed code-size/indexing audit as an `info/` reference;
  it does not authorize another broad refactor.
- Archived the old Core World Editor and standalone World Builder plans. Active
  editor/runtime work belongs to the independent RSC World Editor projects and
  must not be routed through Core workers.
- Updated affected internal documentation links.

After reclassification, `in-progress-work-plans/` contains 39 Markdown files
and approximately 47,162 lines. This remains larger than ideal because the
layered-world architectural ledger intentionally retains extensive validation
history.

## Highest-value next work

### 1. Monster Slayer post-release stabilization

This is the newest large player-facing system and therefore the likeliest place
for high-value live feedback. Prioritize concrete task eligibility, location,
promotion, shop, point-economy, satchel, and migration defects. Collect task
length and point-spend evidence before designing the explicitly deferred unique
reward catalog.

### 2. Renderer and boundary-transition performance

The renderer and layered loader are accepted, but reported CPU spikes and the
remaining transition hitch are still active player-experience concerns. Keep
the existing diagnostic-first route: measure a reproducible transition,
attribute cost to server/client/renderer ownership, then make one bounded
change. Do not reopen the accepted layered coordinate or residency design
without contrary evidence.

### 3. Layered-runtime regression hardening

Recent restricted passages, ranged cover, guild point areas, arena placement,
and layered interaction bugs show that legacy packed-coordinate assumptions can
still survive in content code. New reports in these families should receive
small shared helpers and focused tests rather than one-off coordinate patches.

### 4. Server R2 continuation

Server R2 is the strongest strategic backend stream once immediate live-game
defects are clear. Continue through its existing gates; do not combine it with
ordinary gameplay fixes or treat partially implemented R2 ownership as live
authority.

### 5. Ante design closure before implementation

Ante is confirmed but not started. It still needs authoritative value
thresholds, scaling caps, common/rare-table math, item recovery semantics,
death transaction ordering, and LootShare composition. Resolve those decisions
in the plan before opening a broad implementation branch.

## Work that does not need immediate attention

- The A01–A11 combat modernization program is closed. New combat behavior
  requires a newly scoped plan rather than an assumed A12.
- The current code-size/navigation audit recommends normal work, not preventive
  extraction. Refactor only when a file creates a concrete ownership,
  navigation, or test-isolation problem.
- Cleric's twelve-spell launch is complete. Unholy sigils and enemy debuffs are
  future concept work, not missing launch work.
- RSC World Editor work is outside this repository's manager/worker queue.
- Broad Monster Slayer reward expansion should wait for progression and economy
  observations from the shipped foundation.

## Remaining documentation debt

1. The layered-world plan is over 21,000 lines. Its concise active loader
   roadmap is authoritative, while numbered Slice 1–214 material is history.
   A later documentation-only pass should split that history into an archive
   without changing technical claims.
2. `work-items.md` remains a large implemented-state rollup. Future updates
   should prefer the change history for completed facts and keep the top active
   queue short.
3. Player-facing in-game guides still need as-found review whenever balance or
   terminology changes; release notes and plans cannot prove visual fit or every
   page's wording.
4. A lightweight local Markdown-link/status check would prevent moved-plan
   links and stale “awaiting merge/release” phrases from surviving future
   releases.

## Ongoing maintenance rule

At each substantial release:

1. update the latest release and current phase in the roadmap;
2. update the root README only for major player-facing capabilities;
3. change the governing plan's status from branch/handoff language to shipped
   state when appropriate;
4. move closed implementation records out of `in-progress-work-plans/`;
5. keep future extensions in a new focused plan rather than leaving a completed
   launch plan permanently active;
6. verify project ownership before assigning World Editor or Sprite Baker work.
