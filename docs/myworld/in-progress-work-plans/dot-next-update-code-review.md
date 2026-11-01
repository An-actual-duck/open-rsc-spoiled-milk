# Dot: comprehensive next-update code review

Owner-requested review checkpoint, November 1, 2026. Review the assembled update
without implementing fixes. Return findings to Core for approval before any
implementation begins. Continue independently until the review is complete.

## Frozen target and comparison scope

The exact target is the published checkpoint commit containing this assignment.
Core supplies its full SHA in the handoff prompt. Record that SHA before making
the report-only commit, and continue reviewing that immutable revision even if
`main` advances later.

Its integrated implementation parent is
`788a70c685abff5d078d77d1d90c168f4c8531ae`. All integrated source, definitions,
checked-in assets, tests and documentation are present in the target's tree and
ancestry. The checkpoint adds review instructions, not another gameplay change.
**Do not review only the checkpoint's last-commit diff.**

- Broad change baseline/common ancestor:
  `fec94c8731b5521410963575ef0f2fa5c05ef0b3`.
- Current deployed source reference:
  `a5043328f99995f1e77681b5b06f84dac72a9f9c`.
- The deployed reference is not an ancestor of the candidate: at checkpoint
  preparation there were 17 deployed-side and 332 candidate-side commits after
  their common ancestor, spanning 1,492 changed tracked paths on the candidate
  side. Compare final code as well as history; differing cherry-pick hashes do
  not establish a missing fix.

Use `git diff BASE TARGET`, `git log --first-parent BASE..TARGET` and focused
source reads to navigate. Review the full implemented feature paths and their
interactions, including relevant unchanged callers and lifecycle code. The
baseline is an inventory aid, not permission to ignore older dependencies.

## Exclusive workspace and report-only authority

- Assigned worktree: `/home/justin/Core-Framework-ai-2`.
- Assigned branch: `docs/next-update-code-review`, activated by Core at the
  checkpoint. This is a maintainer-owned worker, not an external contributor.
- Read `AGENTS.md` and `docs/workspaces/README.md`. Run
  `git status --short --branch` and `./scripts/ai-workspace.sh status` there.
  Confirm a clean checkout at the exact supplied target before proceeding.
- Do not change code, tests, assets, definitions, configuration, existing
  documentation or generated production files. Do not apply even obvious fixes.
  Do not merge, rebase, update the target, allocate workers, publish `main`, or
  operate any server/client, map import/export, database or installed toolkit.
- The only permitted tracked addition is the review report:
  `docs/myworld/in-progress-work-plans/next-update-code-review-findings.md`.
  This is the review deliverable, not an implementation change. Keep supporting
  non-sensitive scratch output under ignored `output/next-update-code-review/`.
- Read-only analysis and bounded offline verification in this worker are
  authorized. Inspect commands before running them: do not use generators or
  tests that rewrite tracked assets, configuration or shared caches. Do not
  access account data, credentials, live files, or owner-authored map projects.
- Run heavy checks serially with Java 8 and bounded heaps. Prefer targeted
  evidence over repeating every historical suite. Available Java:
  `/home/justin/jdk8u482-b08`; use `ANT_OPTS='-Xmx512m -XX:+UseSerialGC'`.
- Preserve unexpected state and report it; no stash, clean, hard reset, forced
  checkout, force push or deletion of ignored data. Do not try to clear other
  parked branches or treat the historical art worker as missing integration.

Checkpoint the report with the ordinary worker checkpoint command. At completion
use `./scripts/ai-workspace.sh handoff -m "Review assembled next-update candidate"`
and supply the exact pushed report commit. Before handoff, verify the complete
diff from the frozen target contains only the permitted findings report.
Core alone evaluates the findings and authorizes separate implementation tasks.

## Review priorities

Start with [the current release scope](next-major-release-goals.md) and
[migration/package rules](next-update-migration-package.md). Follow the linked
implementation reviews and actual source; historical proposals do not override
later owner decisions or current behavior.

1. **Combat and lifecycle interactions:** shared Slow/poison/antidote cleansing;
   all 15 battleaxe coatings, single-use recipe and permanent application/removal;
   shared critical identity, Stagger immunity/attack counting and independent
   Whip/Sticky delays; Cosmic combat luck and its favorable-roll selection.
   Trace launch/settlement, provenance, damage families, RNG/resource charging,
   caps/decay, zero hits, death/logout, immunity and source ownership.
2. **Cleric and production:** the current 17-spell catalog, ally/enemy admission,
   stacking/replacement, inherited components, Holy Power caps, regeneration,
   healing, undead classification, recoil, non-corporeal damage and rearming;
   Grace/Blight infusion, three-grade sigil crafting and atomic per-cast costs.
   Check real packet metadata, client/HUD/guide consistency and persistence.
3. **Slayer and gem dragons:** existing/new encounters and gimmicks, progression,
   tower entrance/upward-stair guards, downward/exit freedom, drops and reward
   equipment. Check the five gem-dragon tiers, guaranteed bones and gem roll,
   separate nullable normal table, scrap/uniques, and gem-cutting equipment.
4. **Capacity, networking and world integration:** 16,384-NPC registration versus
   the separate 1,024 view budget, client carriers/storage, index reuse, atomic
   region transitions, cleanup and compatibility. Examine layered-map authority,
   death/ground-item reconciliation, doors/transports/agility and migration hooks
   where they interact with the pending update or deployed fixes.
5. **Presentation and content integrity:** floating hit numbers and damage
   provenance; renderer-phase restoration; abilities panel geometry/input and
   compact Cleric summaries; guide completeness; stable IDs, selectors, paired
   archives/assets, generation receipts and server-owned scenery/materials.

Useful starting reviews (all in this directory):

- `shared-slow-reconciliation-review.md`
- `shared-critical-stagger-foundation-review.md`
- `antidote-cleansing-reconciliation-review.md`
- `battleaxe-coatings-implementation-review.md`
- `cosmic-combat-luck-review.md`
- `cleric-consistency-implementation-review.md`
- `cleric-metal-sigil-production-review.md`
- `cleric-well-assets-review.md` and `cleric-well-spots-review.md`
- `cleric-spellbook-guide-review.md` and `practice-feedback-review.md`
- `npc-capacity-core-acceptance.md`
- `slayer-tower-stair-guard-migration-review.md`
- `gem-dragon-family-review.md`, `gem-dragon-guaranteed-gems-review.md`,
  `gem-dragon-standard-loot-review.md`, `gem-dragon-normal-scrap-review.md`
- `dot-hitsplat-visual-rework-core-review.md`

## Boundaries and known remaining release work

- The editor is a separate repository. Do not cross-merge or review an embedded
  toolkit ZIP as if it were Core source. Custom game content remains game-owned;
  the editor discovers it rather than carrying a hard-coded custom roster.
- The owner's latest saved map is outside Git. This commit does not contain or
  export that project. The maintained package at
  `/home/justin/spoiled-milk-next-update` remains **prepared, not deployed**.
  Its frozen map is older than the owner's latest saved tower/cave/well/egg work.
  Treat latest-map export and combined gameplay acceptance as pending, not as
  evidence that the latest authored map is defective. Do not open or modify it.
- Frozen staged map fingerprint:
  `a9f4a30776cafce9253f6ddda9dc61f01b474e4aa6ea6ad6cf4379e2f7f339b7`;
  manifest SHA-256:
  `7ba8b691231cf93301229159803810a1ac2f9b5410a59f37ec532c372b25d78d`.
  These identify the existing staged export, not a claim to capture newer work.
- Four preserved manager-local edits are excluded from the source snapshot:
  the private port override and three runtime/capability descriptors. The latter
  locally advertise encoding versions `[1,2,3,4]`, whereas the committed copies
  advertise `[1]`. Review whether normal build/discovery/installation correctly
  produces the required capability declarations; distinguish actual packaging
  risks from machine-local selection state. Do not copy local overrides into
  the worker or silently assume private configuration proves the committed path.
- Preserved unmerged/parked branches and old art candidates are not part of this
  candidate. Approved content was integrated deliberately; do not merge the old
  branches to increase apparent completeness.
- Mystic Blue Dragon, Fierce Red Dragon, Corporeal Beast, Ante, Cleric Tier 4 and
  the remaining Zamorak hostile chain are deferred. They are not missing required
  implementations for this review. The current Cleric stage and visuals already
  approved by the owner should not become an unsolicited redesign backlog.
- Feature checks and earlier private approvals exist, but a comprehensive review
  and combined release-candidate acceptance have not yet been declared complete.
  The review is not deployment approval and does not authorize a public restart.

## Findings and completion

Prioritize concrete correctness, exploit, data-loss, compatibility and lifecycle
problems. Avoid stylistic nitpicks and speculative redesigns. Read existing
evidence critically without assuming a passing test proves every integration.

The report must record the frozen target SHA, baselines, reviewed subsystems and
remaining coverage limits. For each actionable finding provide severity, precise
file/line references, trigger and consequence, reasoning or reproduction evidence,
and a suggested direction **without an applied fix**. Separate confirmed defects
from questions, inherited issues, deferred features and pending acceptance. List
commands actually run and their results; do not repeat prior results as new tests.

Conclude with what Core should address before release, what can reasonably wait,
and what still requires private gameplay. A report with no confirmed findings is
valid if the reviewed scope and limits are explicit. Return the report-only READY
handoff and wait for Core approval; do not proceed into implementation.
