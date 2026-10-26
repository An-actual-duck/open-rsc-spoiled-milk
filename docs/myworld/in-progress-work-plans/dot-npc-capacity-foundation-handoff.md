# Dot assignment: NPC capacity, registry safety and measured scaling

Owner approved this plan on October 26, 2026. This is an implementation task
ending in an exact tested Core READY handoff. Preserve existing gameplay and
Slayer content. This task unblocks the decorated tower's capacity, not its rank
bindings or public release.

## Workspace and authority

- Assigned worker: `/home/justin/Core-Framework-ai-2`.
- New branch: `feat/npc-capacity-foundation`, activated by Core.
- Role: maintainer-owned worker. Read AGENTS.md and docs/workspaces/README.md;
  run git status --short --branch and ./scripts/ai-workspace.sh status first.
  Confirm the assigned branch and record starting HEAD. Stop if unexpectedly dirty.
- The previous coating Phase 1 branch was integrated as a review-only package,
  then recycled. Its gameplay was not implemented. Do not resume it in this task.
- Core manager, other workers, live deployment, Toolkit and authoring repositories
  are not implementation workspaces for this assignment. Never cross-merge them.
- Checkpoint meaningful reviewed slices with ai-workspace.sh checkpoint; end
  using handoff and report the full pushed commit. Do not merge/push main, run
  manager/allocation/recycle/release/deployment scripts, or operate servers/clients.
- Offline builds, production-path fixtures, synthetic benchmarks and read-only
  copies of map data are authorized. Core arranges actual private-game acceptance.
  No account/credential access, owner map mutation or installed-tool changes.
- Keep heavy jobs sequential and bounded. No background gameplay servers. Preserve
  unexpected work; no stash, clean, hard reset, forced history or worktree deletion.

## Problem and approved scope

The saved tower import contains4047 NPC spawns, baseline3803. Current World
registers at most4000, and new-NPC packets encode global indexes in12 bits.
The owner approved a deeper bounded upgrade: expanded protocol/capacity,
registry hardening, and measured scan/overlap improvements. Existing AI/combat
rules, region/layer authority and lifecycle protections are foundations to retain.

Read slayer-tower-final-placement-and-capacity.md and next-major-release-goals.md
in this plan directory. Owner decoration is complete. First associate guards
entry; later associates guard UPWARD STAIRS in safe rooms behind freely accessible
auto-closing doors. Guard dialogue/bindings and new placements are a later Core
slice. Do not include them or any map import in this implementation.

Read-only tower fixture:
`/home/justin/RSC Development Toolkit/Projects/modern-session-bc36ad33-dc25-4129-b381-2827c0a793e4/saves/project-a398aa11-dedf-48cb-a1a3-ad9682cb3967`.
Use an independent copy for any export/fixture transformation; retain every
placement. Report if newer owner work supersedes this snapshot. Main baseline
before assignment preparation was5ca55c1821d9b89709caa3442195cd85d99a4960;
record actual activated HEAD, which also includes review-only coating docs/tools.

## Slice A: carrier and ownership inventory

Create npc-capacity-foundation-review.md with a before/after contract matrix:

- Global spawn registry indexes versus NPC definition IDs versus per-view counts.
- Spawn/movement/baseline/appearance/hits/chat/projectile/removal packets, inbound
  attack/range/magic/talk/item targeting, local caches, queued targets, reconnect,
  index reuse and packet/model-picking sprite tags that embed NPC identities.
- Every width, sign/sentinel, array allocation/growth, size limit and source of
  truth. Check unsigned16 semantics rather than assuming all short readers agree.
- Classic/retro protocol families and the custom client handshake/version gates.
  GameStateUpdater.safeNPCIndex currently uses modulo for authentic clients;
  unchanged classic packet bytes alone cannot make enlarged global indexes safe.
- Existing typed WorldLocation/spatial-domain ownership, combat participant
  lifetimes, NPC death/respawn and event-preservation contracts.

Useful starting paths: server/model/world/World.java, util/EntityList.java,
GameStateUpdater.java, Server.java, model/entity/npc/Npc.java and NpcBehavior.java,
model/world/region/RegionManager.java, client orsc/PacketHandler.java and
orsc/mudclient.java (under their maintained src roots).

Checkpoint the inventory and selected design before broad edits. Core may review
it while work continues; do not wait for owner confirmation of routine engineering
choices within this approved scope. Ask only for a genuine new product decision.

## Slice B: paired capacity/protocol upgrade

Target **16384 simultaneous NPCs** initially, subject to measured acceptance,
with an explicitly bounded larger carrier for future growth. Widen both global
runtime indexes and NPC definition identities in the custom protocol where needed;
16-bit carriers are the recommended direction, not permission to assume65536
safe entities in every ancillary path. Do not grow player limits accidentally.

Use one explicit numeric contract for supported capabilities and registry budget,
so code/protocol checks cannot drift across unrelated magic numbers. Keep configured
capacity within the proven carrier/client limits. Include sensible transient-spawn
headroom, diagnostics and startup checks; never silently discard map placements.
Do not claim the full16K population is proven until benchmarks and acceptance pass.

Version/gate changed custom packets explicitly. Old custom clients must not parse
new bytes as the old layout. Preserve tested classic/retro behavior in compatible
profiles, with explicit refusal of unsupported combinations before world entry;
do not use index modulo as a solution to identity collisions. A genuinely reviewed
per-view mapping is an alternative only if necessary and bounded, not an implicit
requirement to build a new networking architecture.

The client already grows global and visible arrays through ensureNpcServerCapacity
and ensureVisibleNpcCapacity: their5000/500 allocations are initial sizes, not
all hard limits. Preserve and audit every companion array and queued reference.
Large global population does not require rendering/sending every NPC to every
player. Preserve regional visibility and deterministic selection priorities.
Definition-width tests can use synthetic high-ID definitions; add no new monsters.

## Slice C: registry safety and lifecycle

EntityList is shared with players. Audit add/get/remove/contains/iteration and
registration failure paths, including repeated removal, stale entity handles,
negative/out-of-range indexes, full capacity, index reuse and queue/map/array
agreement. An empty removal currently offers its index back into the free queue;
prove and fix such cases without letting stale removal erase a replacement entity.
Test shared player callers too. Maintain stable processing order and documented
same-tick mutation semantics. Do not casually change target selection or RNG order.

Keep authored placement identity, live registry index, spatial location/domain and
combat lifetime conceptually distinct. Existing lifetimes protect delayed combat
and status effects; extend tests through reuse instead of replacing the mechanism.
Death, respawn, despawn, summons, quest NPCs and failed registration must leave no
half-registered region membership or surviving target/event from another lifetime.
Avoid broad generic registry rewrites unless the concrete invariant requires one.

## Slice D: measured efficiency improvements

Record baseline and final results under the same repeatable fixtures/JVM settings.
Use existing profiling/diagnostic infrastructure where practical.

1. EntityList.forEachLive scans the full allocated array; iterator also constructs
   a filtered snapshot. Inspect actual hot callers. Reduce empty-slot scanning or
   avoidable allocations only where measured worthwhile, keeping processing order,
   mutation visibility and concurrency guarantees explicit. A dense list with a
   different update order is not a drop-in substitute. Record justified deferrals.
2. mudclient.updateNpcOverlapVisualOffsets currently compares each visible NPC
   with every other. Group by visual tile and preserve exact ordering/rank/offset
   behavior, including ties and moving/stacked NPCs. Test output equivalence with
   the original algorithm and measure sparse versus dense scenes.
3. Preserve existing idle cadence, nearby-player roaming checks, region/signed-level
   visibility and snapshot caching. Fully active-region scheduling, sleeping-world
   simulation, multithreaded AI, instancing, pathfinding rewrites and new combat
   behavior are outside this task. Identify worthwhile follow-ups separately.

Exercise roughly4K/8K/16K registered populations, with sparse registry occupancy,
idle distant populations, many nearby players, dense visible groups and active
combat. Separate server tick cost, movement loop, visibility/packet generation,
client overlap/layout cost, allocation and memory. Report median/tail timing and
methodology rather than a single warm-up number. A registration count alone is
not a throughput result. Do not manufacture16K combat/world support by skipping
AI, collisions, statuses or networking. Practical default needs evidence.

## Verification and preservation

- Failure-first tests for known limit/index/registry problems, then boundary
  indexes around2047/4095/4096/8191/16383 and rejected out-of-contract values.
- High definition IDs across the old1023 boundary; count/ID/index remain separate.
- Real encoder-to-decoder round trips with length/sentinel/truncation checks,
  supported/unsupported handshake combinations, malformed and stale updates,
  world/level transitions, removal/re-add and reconnect cache reset.
- Actual high-index interaction/hit/projectile targeting and index reuse cannot
  alias low-index NPCs, players or ground/scenery picking namespaces.
- Registry repeated/stale removal, full/recovery, failed spawn, and player users.
- Preserve primary damage/RNG and combat deadlines, all eight Slayer enemies,
  poison/Slow lifecycle, summons, respawn/death and signed-level spatial behavior.
- Preserve existing art archives, definition/item/scenery IDs, prices, quest gates,
  map packages and approved release-separated content. No bulk historical merge.
- Source builds for matching server/client, full current combat gate, Slow and
  focused registry/protocol/spatial/client tests. Use current targets/scenario
  counts; never remove coverage or weaken fixtures to get green.
- Independently exported tower copy must validate against maintained Core data
  and fit runtime registration including a documented dynamic-spawn allowance.
  No author data relocation, import or public activation belongs here.

## Toolkit boundary and final handoff

Current toolkit preflight reads the server's registry scalar but its reviewed
Limits family rejects above4000 and still assumes the old wire definition bound.
Deliver a neutral, documented capacity/protocol contract with exact source paths
and supported-client policy for the separately managed Toolkit worker to consume.
Do not edit Toolkit, hardcode tower IDs or loosen its import validation from Core.
Flag source discovery compatibility risks caused by changed initializers/helpers.
Core will arrange paired Toolkit changes and import/private-game testing afterward.

Final exact READY report must include starting and pushed full commits, changed
files, contract matrix, old-client behavior, authoritative capacity/headroom,
registry invariants, benchmark fixtures/raw outputs/results, reproducible commands,
source-built artifact hashes, limitations and private acceptance checklist.
Stop before Core integration, owner map import, server/client launch or release.
A core-only READY does not claim the editor import blocker or gameplay acceptance
is complete; those require the subsequent Toolkit and isolated game gates.
