# Dot assignment: Slow/poison coatings through full battleaxe coverage

Assigned by Core, October 25, 2026. Owner requested continuation from the
shared Slow foundation through complete battleaxe coverage, including sprites.
This is one focused task with a design checkpoint before unresolved balance
is implemented. Core retains integration and gameplay acceptance.

## Workspace and boundaries

Use only `/home/justin/Core-Framework-ai-2`, branch
`feat/battleaxe-slow-poison-coatings`, activated by Core. Read AGENTS.md and
`docs/workspaces/README.md`; run `git status --short --branch` and
`./scripts/ai-workspace.sh status` before editing. Record starting HEAD.
This is a maintainer-owned worker slot, not an external contributor clone.
Do not share it with another session. Checkpoint useful milestones and finish
with the local `ai-workspace.sh handoff` command and an exact pushed commit.

No manager commands, main merges/pushes, release/deployment, server/client
launches, database access, owner map edits, installed-editor changes, or live
operations. Offline tests/builds and worker-local asset work are authorized.
Preserve unexpected changes. No stash, clean, hard reset, force push or deletion
of saved work. Run heavy jobs sequentially with bounded heaps.

## Authoritative state

Starting game baseline before this assignment: `821526e736e112c8db0c8716c65a80c14812628b`.
Read current `shared-slow-foundation.md`, `shared-slow-reconciliation-review.md`,
`effect-standardization-follow-up.md`, `next-major-release-goals.md`, and
`../rough-drafts/slayer-unique-equipment-design.md`.
Shared Slow is already integrated; do not redo it or restore percentage slows.
10 power per tier, caps 12/22, decay 2 every 4 ticks, highest received cap retained
until clearance, latched combat deadlines and lifecycle cleanup are established.
Movement, consumables and utility actions are not part of shared Slow timing.
PvP remains disabled; actor-owned PvP cadence redesign is a separate task.

Most Slayer encounters/items/art are already approved and tested, with some work
preserved separately for release assembly. Missing from main does not mean
unfinished. Reference exact historical changes rather than importing an entire
Slayer branch. Approved antidote lineage is
`4d98e17833135ae3f150f7ad953099b184fe91d1`; preserved Slayer reference tip is
`85ed0e8a05c13143d318336f680e786849a0b75a`.
Audit main against that lineage first. Preserve approved +5/+10/+20 ten-minute
antidote cleansing, strongest potion only with refresh, additive necklace/armor
cleansing and zero-clamping. Recipes use marentill with yellow Giant spider eggs
at level 8, red spider eggs at 22, green Jungle spider eggs at 38. Reconcile only
concrete prerequisites needed here; do not overwrite modern combat/lifecycle
code or revive blanket poison immunity. Preserve current poison ownership,
persistence, kill credit, caps and source policies.

## Phase 1: inventory and balance proposal

Produce `battleaxe-coatings-design.md` in this plan directory, containing:

- Current poison/antidote implementation status with exact paths/commits and
  necessary focused reconciliation. Distinguish approved preserved work from
  genuinely missing or undecided behavior.
- Exhaustive battleaxe matrix: item ID/name, tier, base stats/requirements,
  inventory sprite, held family/palette, existing poison or special effects,
  generator/source of truth, proposed coated counterpart and exclusions.
  Include bronze, iron, steel, black, mithril, adamantite, rune, tin, copper,
  titan steel, orichalcum, white, dragon and grey wherever they actually exist.
  Search definitions/generators as well as constants; this list is not a roster
  to hardcode. Flag quest/special/retired variants separately. Ordinary axes,
  hatchets and non-battleaxe weapons are outside this pass unless owner approves.
- A concrete recommended recipe and progression: names, herb, level, yield,
  dose count, application/consumption behavior, value and availability.
  One Cockatrice Eye plus one Sticky Saliva Gland are established ingredients;
  their existing 1/128 drop rates must remain unchanged.
- Recommended per-hit poison and Slow power, trigger policy, source cap,
  any proc chance, persistence/charges, removal/recoating, ordinary poison
  interaction and intrinsic titan-steel interactions. Explain buildup and
  sustain with actual battleaxe speeds and established decay; compare against
  normal poison rather than inventing a second poison engine. The goal is
  weaker poison plus meaningful accumulating Slow, not immediate stun.
- Sprite plan and coverage matrix for coating container/doses, inventory/ground
  battleaxes and all held directions/attack frames. Recommend a clearly legible
  coating color compatible with existing poison visuals. Preserve hand gaps,
  authentic battleaxe family, layer ordering and native canvas scale.
- A concise list of owner decisions. Label all proposed numbers as proposals.

Checkpoint and report this proposal for Core/owner approval. Do not silently
choose unresolved gameplay values or add usable unapproved variants. While
awaiting approval, continue read-only coverage analysis, fixtures planning and
isolated visual candidates that do not change production assets. Once Core
relays approval, continue Phase 2 in this same branch; no second task is needed.

## Phase 2: complete approved implementation

Implement the approved recipe, potion/item-use paths, full eligible battleaxe
variant matrix, poison and shared-Slow producers, descriptions, values,
requirements, trade/equip rules, generators, and item/sprite mappings. Preserve
base weapon stats and speed except the explicit coating effects. Apply Slow
through the shared foundation once per eligible resolved hit, never through
secondary damage or readiness polls. Use existing poison policies and account
for intrinsic effects without duplicate or lost applications.

Read `dev/myworld/reference-library/items/README.md`, then relevant axe and
potion notes/contact sheets and individual full-canvas anchors before icon work.
Inspect current authentic/custom held battleaxe assets, not longsword fallback.
Read local asset instructions before touching caches. Prefer controlled palette
or blade-edge edits over regenerated geometry. Inspect every existing tier's
palette/mask behavior before assuming one recolor handles the full family.
No external paid services, tool installation or unrelated asset regeneration.
Preserve all unrelated archive entries and existing IDs. Allocate appended IDs
from current authoritative generators; ensure server/client parity and that
regeneration retains the changes. Record source and output hashes.

Deliver a browser-ready review sheet with base/coated inventory icons at native
scale plus nearest-neighbor zoom, held directional/attack contact sheets and
representative equipped composites if existing offline tooling supports them.
Clearly label placeholders, candidate art and limits; do not claim owner visual
or gameplay acceptance. If native sprite slots cannot safely represent the
proposed art, report the concrete issue rather than recycling unrelated slots.

## Verification and exact READY handoff

Test actual production paths, not just table existence: recipe level and exact
ingredient/dose consumption; insufficient inventory/invalid weapon no-loss;
application/removal/recoating and intrinsic-effect interaction; server/client
ID, stat, requirement, value and sprite parity; generators reproducible; all
eligible battleaxes represented with no ordinary hatchet or unrelated changes.
Exercise positive/zero hits and proc RNG, primary/secondary ownership, combined
poison/Slow buildup and caps, decay boundaries, multiple sources, death/logout/
respawn cleanup, retained deadlines and replacement/retargeting. Preserve HUD
semantics and poison cleansing. Preserve tests for all eight Slayer enemies.

Run current `test_slow`, `test_combat_strict`, focused poison/antidote tests,
appropriate item/generator/equipment/HUD/Slayer regressions, archive preservation
checks and client build. Discover current commands; do not freeze historical
scenario counts. Use Java 8 and bounded ANT_OPTS, sequential jobs. Record baseline
failures separately, never weaken tests to get green.

Final review report must include exact pushed READY commit and starting HEAD,
changed files, approved design reference, complete before/after battleaxe matrix,
IDs/assets and provenance, reproducible commands/results, visual preview paths,
known limitations and remaining owner gameplay/visual checks. Stop at READY;
Core will independently review, integrate and arrange private testing.

## Owner feedback after Phase 1 — October 25, 2026

Reviewed Phase 1 handoff: `b41d37ee22cf9af21e3c3429dfb96ed6a0a96cef`.
This feedback supersedes conflicting proposals, not unrelated undecided choices.

- Recipe yield is **one single-use dose**, sufficient for **one weapon only**.
  Do not produce three doses or retain three-dose economy assumptions. Proposed
  bottle dose concepts are candidates, not a requirement for additional doses.
- This remains a **primarily Slow coating with secondary poison**, not Slow-only.
  The owner explicitly rejected compensating for weak low-tier poison through
  a minimum effective damage, minimum application or minimum poison cap.
- Compare against the actual standard weapon poison payload for each material.
  Use approximately half standard poison strength: with today's standard tier×4
  application and tier×10 cap, coating application is **tier×2**, cap **tier×5**.
  Retain the established weapon proc policy and shared poison engine, including
  target-owned accumulation, highest-source cap and ownership rules. No extra
  independent poison pool or second proc roll.
- Tin's application2/cap5 can legitimately deal no poison damage by itself.
  Copper's application4/cap10 may be marginal. Multiple attackers/sources can
  still contribute to a shared pool where existing retention rules permit it;
  do not promise indefinite subthreshold persistence. Verify mixed-source
  contribution before clearance with real poison pulse/cleanup paths.
- Natural poison decay remains **3 power per 8 ticks**, with approved additive
  cleansing bonuses and zero-clamping. Weapon tier changes poison application
  and source cap, not pulse interval or natural decay.
- Preserve the reviewed Slow proposal as the current recommendation: application
  **4 power**, source cap **12 total**, natural decay **2 per 4 ticks**, all flat
  across weapon tiers. The owner asked for clarification of these values; this
  feedback approves weak poison and yield, and does not independently settle
  every outstanding design choice. Shared 10-power thresholds, cap retention
  and committed combat-deadline rules remain authoritative.

Do not implement the manager's superseded recommendation to remove poison or
add a minimum poison strength. Update the design/review documents to distinguish
this explicit feedback from remaining proposals. Recipe economy must be revisited
for the single-dose yield; other pending design approvals remain separate.
