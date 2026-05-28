# Foundation Optimization Plan

This plan tracks low-risk optimization and modernization work for the old
OpenRSC/Cabbage foundation that `MyWorld` is built on.

The goal is not to rewrite the server. The goal is to reduce avoidable tick
cost, allocation churn, and maintenance risk while preserving current gameplay,
protocol behavior, and fork-specific validation.

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on evidence, design, or a prerequisite

## Ground Rules

- Preserve gameplay behavior first.
- Prefer measured, narrow changes over broad rewrites.
- Keep protocol output byte-compatible unless the change is explicitly about a
  custom-client protocol extension.
- Keep the main world tick effectively single-threaded unless a subsystem is
  already isolated and safe to move off-thread.
- Do not optimize by weakening validation, visibility rules, item ownership,
  combat attribution, quest state, or save durability.
- Add or update regression coverage before changing code that affects movement,
  visibility, combat, inventory, saving, or packet assembly.
- Run MyWorld validation after each phase:
  - `./scripts/check.sh`
  - `./tests/myworld/test-smoke.sh`
  - relevant focused Python checks
  - `./tests/myworld/test-all.sh` once the full suite is expected to be green

## Current Baseline Work Before Optimization

- `[done]` Fix or explicitly triage current red MyWorld validation checks before
  using full-suite results as an optimization safety gate:
  - `test-gathering-rework-plan.py` expects legacy shears id `144` to be
    renamed to `Tin shears`.
  - `test-combat-exceptions.py` appears to have a brittle Salarin contribution
    regex that no longer matches the current implementation shape.
- `[done]` Record a clean baseline run after those are resolved:
  - total boot time
  - normal tick duration
  - worst tick duration during scripted smoke/startup
  - player count and NPC count during the run
  - allocation or GC information if available

Initial short benchmark:

- command:
  `MYWORLD_BENCHMARK_TICKS=20 MYWORLD_BENCHMARK_WARMUP_TICKS=5 ./tools/benchmarks/benchmark-foundation.sh`
- summary:
  `samples=20`, `warmupTicks=5`, `avgTickMs=29`, `maxTickMs=133`,
  `avgProcessNpcsMs=26`, `maxPlayers=0`, `maxNpcs=3703`, `maxEvents=3795`
- result:
  NPC processing dominates the no-player baseline, so Phase 1 entity/NPC
  iteration work remains the right first optimization target.

## Measurement Harness

### Phase 0: Add Benchmark Guardrails

- `[done]` Add a small server-side benchmark command or dev script that can run
  a fixed number of ticks and print timing summaries without changing gameplay.
- `[done]` Capture at least these timings:
  - whole tick duration
  - event processing
  - world update
  - NPC processing
  - player processing
  - update packet generation
  - cleanup
- `[done]` Add counts beside timings:
  - players
  - NPCs
  - tracked game events
  - local NPC/player/object/item counts for sampled players
- `[done]` Add optional Java Flight Recorder or GC logging notes for local
  profiling on Java versions that support it.
- `[done]` Store benchmark output under ignored local artifacts, currently
  `output/benchmarks/optimization/` with logs under `output/logs/`.

Primary files:

- [`Server.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/Server.java)
- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)
- [`tools/benchmarks`](/home/justin/Core-Framework/tools/benchmarks)
- [`foundation-profiling-notes.md`](/home/justin/Core-Framework/docs/myworld/foundation-profiling-notes.md)

Acceptance criteria:

- The benchmark can be run repeatedly against the same local world.
- It does not require live external services.
- It does not alter committed runtime data.
- It gives enough information to compare before/after changes.

## Low-Risk Allocation Reductions

### Phase 1: EntityList Hot Iteration

Problem:

- [`EntityList.iterator()`](/home/justin/Core-Framework/server/src/com/openrsc/server/util/EntityList.java)
  streams the full backing array, filters nulls, collects an immutable snapshot,
  and then iterates that snapshot.
- This creates avoidable allocation in common player/NPC loops.

Plan:

- `[done]` Add a new explicit hot-path API such as `forEachLive(Consumer<T>)` or
  `snapshotLive()`.
- `[todo]` Keep the existing `iterator()` behavior at first to avoid breaking
  unknown callers.
- `[doing]` Convert hot loops gradually:
  - server tick player loops
  - `[done]` server tick NPC loops
  - `[done]` cleanup loops
  - movement-update loops
  - debug/profiling loops only after gameplay paths are stable
- `[done]` Add focused guard coverage for the new live-iteration API and the
  intended hot-path call sites.

Primary files:

- [`EntityList.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/util/EntityList.java)
- [`PlayerList.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/util/PlayerList.java)
- [`Server.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/Server.java)
- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)

Risk:

- Low if the old iterator remains available.
- Medium if converted loops accidentally allow concurrent mutation behavior that
  the old snapshot concealed.

Acceptance criteria:

- Entity index assignment and reuse remain unchanged.
- Player/NPC unregister flows still work.
- Smoke startup and combat/runtime invariant checks pass.
- Benchmark shows lower allocation or lower tick/update duration under entity
  load.

Phase 1 implementation notes:

- `EntityList` now exposes `forEachLive(...)` and keeps the old snapshotting
  `iterator()` for compatibility.
- Hot NPC loops in `Server` and `GameStateUpdater` now use `forEachLive(...)`.
- `NpcBehavior` now captures one `System.currentTimeMillis()` value per NPC tick
  and reuses cached tick length instead of repeating the same clock lookup
  across roam/aggro/tackle checks.
- Added `tests/myworld/test-foundation-optimization-guards.py` to keep the new
  live-iteration path in place.

Follow-up benchmark after Phase 1 work:

- command:
  `MYWORLD_BENCHMARK_TICKS=20 MYWORLD_BENCHMARK_WARMUP_TICKS=5 ./tools/benchmarks/benchmark-foundation.sh`
- summary:
  `samples=20`, `warmupTicks=5`, `avgTickMs=30`, `maxTickMs=131`,
  `avgProcessNpcsMs=26`, `maxPlayers=0`, `maxNpcs=3703`, `maxEvents=3795`
- interpretation:
  This is a small improvement from the earlier `31/145/27` run, but still in a
  fairly tight noise band around the original `29/133/26` baseline. Phase 1 is
  still worth keeping because it reduces avoidable allocation and repeated clock
  calls, but it did not unlock a large measured win on the current no-player
  sample by itself.

### Phase 2: Packet Assembly Object Churn

Problem:

- `GameStateUpdater` builds many short-lived lists, queues, and boxed bit
  entries during player/NPC/object/item update packet assembly.
- Some method-local `ConcurrentLinkedQueue` instances are not shared across
  threads and do not need concurrent data structures.

Plan:

- `[done]` Replace method-local `ConcurrentLinkedQueue` instances with
  `ArrayDeque` where there is no cross-thread access.
- `[done]` Replace `AbstractMap.SimpleEntry<Integer, Integer>` bit entries with
  a small purpose-built type or direct packet-writer API.
- `[done]` Pre-size lists where the expected size is known from local player/NPC
  counts.
- `[done]` Keep packet order and packet contents identical.
- `[partial]` Add packet-shape tests for representative client modes before larger
  rewrites:
  - retro clients
  - authentic/custom clients
  - MyWorld custom movement stream

Primary files:

- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)
- [`PacketBuilder.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/net/PacketBuilder.java)
- `server/src/com/openrsc/server/net/rsc/generators/impl`
- `server/src/com/openrsc/server/net/rsc/struct/outgoing`

Risk:

- Medium. Packet contents are fragile even when the change looks mechanical.

Acceptance criteria:

- Existing clients still receive valid movement, NPC, player, scenery, boundary,
  and ground-item updates.
- MyWorld smoke test reaches the expected startup boundary.
- Packet-focused tests cover at least one moved player, moved NPC, added NPC,
  removed NPC, added object, removed object, added item, and removed item.

Phase 2 implementation notes:

- `updateNpcAppearances(...)` now uses method-local `ArrayDeque` instances
  instead of `ConcurrentLinkedQueue`.
- `MobsUpdateStruct` now carries a small immutable `BitUpdate` entry type, and
  the hot NPC/player coordinate builders use that instead of
  `AbstractMap.SimpleEntry<Integer, Integer>`.
- Hot packet/update paths in `GameStateUpdater` now pre-size several `ArrayList`
  instances from known local/view counts instead of growing from the default
  small backing array.
- `tools/benchmarks/benchmark-foundation-matrix.sh` now runs a repeatable `short`
  and `soak` scenario pair so optimization work can be compared against both a
  quick smoke sample and a longer, lower-noise sample.
- Added foundation guard coverage to keep the queue and bit-entry cleanup in
  place.
- Added packet-shape guard coverage for the retro bit-update path and the
  MyWorld custom movement stream shape. This is a static contract guard; it
  does not replace a future byte-for-byte packet fixture suite.
- Validation: `python3 ./tests/myworld/test-packet-shape-guards.py` passed.

Follow-up benchmark after Phase 2 work:

- command:
  `MYWORLD_BENCHMARK_TICKS=20 MYWORLD_BENCHMARK_WARMUP_TICKS=5 ./tools/benchmarks/benchmark-foundation.sh`
- summary:
  `samples=20`, `warmupTicks=5`, `avgTickMs=29`, `maxTickMs=134`,
  `avgProcessNpcsMs=25`, `maxPlayers=0`, `maxNpcs=3703`, `maxEvents=3795`
- interpretation:
  This is the first measured improvement that moves outside the weaker Phase 1
  noise band. It is still modest, but the bit-entry and queue cleanup appears
  to have reduced enough packet/update churn to pull the no-player baseline back
  to `29ms` average ticks with `25ms` average NPC processing.

Benchmark matrix after the list pre-sizing pass:

- short:
  `samples=20`, `warmupTicks=5`, `avgTickMs=31`, `maxTickMs=139`,
  `avgProcessNpcsMs=27`
- soak:
  `samples=300`, `warmupTicks=30`, `avgTickMs=25`, `maxTickMs=120`,
  `avgProcessNpcsMs=21`
- interpretation:
  The short run is still noisy enough to bounce around, but the 300-sample soak
  run gives a much clearer signal. On that longer sample, the packet/update
  cleanup plus list pre-sizing materially improved the empty-world NPC baseline.

Next measurement step:

- `[done]` Add a benchmark-only synthetic player scenario so packet/update work
  can be measured under player visibility load instead of only the empty-world
  NPC baseline.
- `[done]` Set the default player-load scenario to `64` synthetic players so the
  current millisecond summary has enough load to expose packet/update work.
- `[done]` Add decimal millisecond benchmark fields such as
  `avgUpdateClientsMsPrecise` so sub-millisecond packet/update changes can be
  compared without relying on coarse integer rounding.
- `[done]` Cache shared outgoing payload generator instances in `ActionSender`
  instead of allocating a new protocol generator for every packet finalization.
- `[done]` Store `MobsUpdateStruct.BitUpdate` values as primitives and read
  them directly in the 235 coordinate packet generator to avoid boxing in the
  hot player/NPC coordinate update path.
- `[done]` Add precise per-section `sendUpdatePackets(...)` benchmark fields so
  the `avgUpdateClientsMsPrecise` bucket can be attributed to player coords,
  player appearances, NPC coords, NPC appearances, object updates, ground items,
  clear-location work, timeout checks, and appearance keepalives.
- `[done]` Reuse one visible game-object collection across scenery and
  wall-object update assembly for each player update. The attribution benchmark
  showed those two sections were the largest measured `updateClients` costs.
- `[done]` Replace linear scans of `knownPlayersAppearanceIDs` with direct map
  lookups in `Player.requiresAppearanceUpdateFor(...)` and its peek variant.
- `[done]` Reuse one visible-player collection in `updatePlayers(...)` and
  replace per-new-player coordinate offset array allocation with primitive
  offset calculation.

### Phase 3: Visible Region And ViewArea Reuse

Problem:

- `ViewArea` repeatedly asks `RegionManager` for local players, NPCs, objects,
  and items.
- `RegionManager.getVisibleRegions` creates fresh small collections each call.
- A single player update can recompute overlapping region/view data several
  times.

Plan:

- `[todo]` Add a per-player-update view context that computes visible regions
  once and reuses them during:
  - player update
  - NPC update
  - scenery update
  - boundary update
  - ground-item update
- `[todo]` Keep old `ViewArea` methods as compatibility wrappers initially.
- `[todo]` Avoid caching across ticks until movement, teleports, object removal,
  and visibility rules are proven safe.
- `[todo]` Replace tiny `ArrayList`/`LinkedHashSet` construction in
  `getVisibleRegions` with a fixed-size local structure if profiling still
  shows pressure.

Primary files:

- [`ViewArea.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/ViewArea.java)
- [`RegionManager.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/world/region/RegionManager.java)
- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)

Risk:

- Medium. Visibility bugs can be subtle and client-dependent.

Acceptance criteria:

- Objects/items/NPCs appear and disappear correctly while walking across region
  boundaries.
- Invisible-to-player rules still apply.
- Quest-specific visibility exceptions still work.
- Custom movement mode still sends correct local movement deltas.

### Phase 3.5: NPC Processing Attribution

Problem:

- After the packet/update-client improvements, `processNpcs` is the largest
  measured tick bucket in the no-player and synthetic-player benchmarks.
- The aggregate NPC bucket is too broad to safely optimize. It combines
  unregister handling, behavior logic, and movement stepping across thousands
  of NPCs.

Plan:

- `[done]` Split the benchmark summary for `processNpcs` into:
  - NPC unregister work
  - NPC behavior work
  - NPC movement-only work
- `[done]` Preserve the existing total `avgProcessNpcsMsPrecise` field so older
  benchmark comparisons still line up.
- `[done]` Add benchmark-gated `NpcBehavior.tick()` attribution for roam,
  aggro, combat, tackle, and retreat states.
- `[done]` Avoid aggro/tackle player-view scans in idle NPC roam ticks when the
  world has no players, compute player presence once per NPC-processing pass,
  and cache immutable plague-sheep / gnome-baller identity checks.
- `[done]` Skip idle random-roam path setup while no players are online. This
  removes unobservable empty-world NPC movement work while preserving live-player
  behavior.
- `[done]` Skip idle random-roam path setup for NPCs that have no local players
  in view, using a short-circuit region lookup instead of building a full
  player collection.
- `[todo]` Run the short, soak, and synthetic-player benchmark matrix and pick
  the largest NPC sub-bucket as the next implementation target.
- `[done]` If roam dominates, split `handleRoam(...)` into idle/eligibility,
  aggro-scan, tackle-scan, and random-walk sections before changing behavior
  logic.
- `[todo]` Run the benchmark matrix against the roam subsection split and pick
  the largest subsection for the next behavior-safe optimization.
- `[todo]` If movement dominates, inspect `Mob.updatePosition()`,
  `WalkingQueue`, and collision/path validation before changing movement
  semantics.

Primary files:

- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)
- [`Server.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/Server.java)
- [`NpcBehavior.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java)
- [`Npc.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/npc/Npc.java)

Risk:

- Low for attribution only. Medium for follow-up optimizations because NPC
  movement, combat, aggro, and interaction behavior are gameplay-visible.

Acceptance criteria:

- Benchmark summaries include `avgProcessNpcUnregisterMsPrecise`,
  `avgProcessNpcBehaviorMsPrecise`, and `avgProcessNpcMovementMsPrecise`.
- NPC roam summaries include `avgNpcRoamEligibilityMsPrecise`,
  `avgNpcRoamAggroScanMsPrecise`, `avgNpcRoamTackleScanMsPrecise`, and
  `avgNpcRoamRandomWalkMsPrecise`.
- The existing NPC processing total is still reported.
- Custom walk-speed mode still runs behavior on the game tick while skipping
  normal movement stepping.
- Combat, NPC attack style, and runtime invariant checks pass.

Follow-up benchmark after initial NPC roam work:

- command:
  `./tools/benchmarks/benchmark-foundation-matrix.sh`
- short result:
  `avgTickMsPrecise=29.815`, `avgProcessNpcsMsPrecise=26.064`,
  `avgProcessNpcBehaviorMsPrecise=25.794`,
  `avgNpcBehaviorRoamMsPrecise=25.290`
- soak result:
  `avgTickMsPrecise=24.819`, `avgProcessNpcsMsPrecise=21.736`,
  `avgProcessNpcBehaviorMsPrecise=21.459`,
  `avgNpcBehaviorRoamMsPrecise=20.898`
- player-load command:
  `MYWORLD_BENCHMARK_SCENARIOS="players" ./tools/benchmarks/benchmark-foundation-matrix.sh`
- player-load result:
  `avgTickMsPrecise=28.129`, `avgProcessNpcsMsPrecise=22.861`,
  `avgProcessNpcBehaviorMsPrecise=22.596`,
  `avgNpcBehaviorRoamMsPrecise=22.032`,
  `avgUpdateClientsMsPrecise=1.130`
- interpretation:
  NPC roam remains the dominant bucket, but the first low-risk roam cleanup
  improved the no-player soak from the previous attributed reference of
  `avgProcessNpcsMsPrecise=22.948` / `avgNpcBehaviorRoamMsPrecise=22.116` to
  `21.736` / `20.898`. The synthetic-player run remains packet-stable and
  improves the NPC bucket versus the earlier attributed reference
  (`avgProcessNpcsMsPrecise=23.727`, `avgNpcBehaviorRoamMsPrecise=23.030`,
  `avgUpdateClientsMsPrecise=1.089`).

Follow-up benchmark after observed-random-roam gating:

- no-player command:
  `./tools/benchmarks/benchmark-foundation-matrix.sh`
- no-player short result:
  `avgTickMsPrecise=5.975`, `avgProcessNpcsMsPrecise=2.160`,
  `avgProcessNpcBehaviorMsPrecise=1.903`,
  `avgNpcBehaviorRoamMsPrecise=1.374`,
  `avgNpcRoamRandomWalkMsPrecise=0.065`
- no-player soak result:
  `avgTickMsPrecise=4.480`, `avgProcessNpcsMsPrecise=1.500`,
  `avgProcessNpcBehaviorMsPrecise=1.299`,
  `avgNpcBehaviorRoamMsPrecise=0.858`,
  `avgNpcRoamRandomWalkMsPrecise=0.066`
- player-load command:
  `MYWORLD_BENCHMARK_SCENARIOS="players" ./tools/benchmarks/benchmark-foundation-matrix.sh`
- player-load result:
  `avgTickMsPrecise=8.107`, `avgProcessNpcsMsPrecise=2.937`,
  `avgProcessNpcBehaviorMsPrecise=2.701`,
  `avgNpcBehaviorRoamMsPrecise=2.213`,
  `avgNpcRoamAggroScanMsPrecise=0.654`,
  `avgNpcRoamRandomWalkMsPrecise=0.878`,
  `avgUpdateClientsMsPrecise=1.172`
- interpretation:
  The random-roam path setup was the main NPC processing cost. Skipping
  unobservable random roaming reduced the no-player soak NPC bucket from the
  earlier `21.736` measurement to `1.500`, and reduced the synthetic-player NPC
  bucket from `22.861` to `2.937`. The remaining player-load NPC work is mostly
  nearby random roaming plus aggro scan, both now small enough that Phase 4
  event execution is again a reasonable next implementation target.

## Tick And Event Modernization

### Phase 4: Direct Event Execution In Single-Thread Mode

Problem:

- Event handling preserves PID/order by using one event thread by default, but
  still pays executor and future overhead through `invokeAll`.

Plan:

- `[done]` Add a direct execution path when
  `WANT_THREADING__BREAK_PID_PRIORITY` is false.
- `[done]` Preserve the existing executor path for the explicitly unsafe
  threaded mode.
- `[done]` Ensure event duration accounting still works.
- `[done]` Ensure event removal and duplicate-event behavior are unchanged.

Primary files:

- [`GameEventHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java)
- `server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java`
- [`GameTickEvent.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/event/rsc/GameTickEvent.java)

Risk:

- Low to medium. The intended default is already single-threaded, but ordering
  and error-handling details must match.

Acceptance criteria:

- `[done]` Player-owned events and non-player events run in the same order as
  before.
- `[done]` Exceptions stop only the failing event as before.
- `[done]` Existing event duplication rules still hold.
- `[done]` Combat, production, prayer, and gathering checks pass.

Follow-up benchmark after direct single-thread event execution:

- player-load command:
  `MYWORLD_BENCHMARK_SCENARIOS="players" ./tools/benchmarks/benchmark-foundation-matrix.sh`
- player-load result:
  `avgTickMsPrecise=6.296`, `avgEventsMsPrecise=1.475`,
  `avgProcessNpcsMsPrecise=2.841`, `avgUpdateClientsMsPrecise=1.150`
- no-player command:
  `./tools/benchmarks/benchmark-foundation-matrix.sh`
- no-player short result:
  `avgTickMsPrecise=3.646`, `avgEventsMsPrecise=1.404`,
  `avgProcessNpcsMsPrecise=1.442`
- no-player soak result:
  `avgTickMsPrecise=3.521`, `avgEventsMsPrecise=1.294`,
  `avgProcessNpcsMsPrecise=1.482`
- interpretation:
  Direct event execution removed the executor/invokeAll overhead from the
  default safe single-thread mode. The synthetic-player event bucket dropped
  from the previous `avgEventsMsPrecise=3.081` reference to `1.475`, while
  preserving the explicitly unsafe threaded fallback for
  `WANT_THREADING__BREAK_PID_PRIORITY=true`.

### Phase 5: Remove Forced GC From Normal Profiling

Problem:

- Profiling/debug output currently forces `System.gc()` before memory reporting.
- That can produce avoidable pauses and distort live performance.

Plan:

- `[done]` Gate forced GC behind an explicit debug flag, default false.
- `[done]` Keep memory reporting using runtime values without forcing a
  collection.
- `[done]` Add wording to debug output when forced GC is enabled so results are
  not confused with normal runtime behavior.

Primary files:

- [`GameEventHandler.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java)
- [`ServerConfiguration.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/ServerConfiguration.java)
- [`server/myworld.conf`](/home/justin/Core-Framework/server/myworld.conf)

Risk:

- Low.

Acceptance criteria:

- `[done]` Debug/profiling command still reports memory.
- `[done]` Normal profiling no longer triggers a full GC.

Implementation notes:

- Added `want_force_gc_on_profiling`, default false.
- Profiling memory output now labels memory as `live` or `after forced GC`.
- `server/myworld.conf` leaves forced GC disabled.

## Movement And Pathing

### Phase 6: Safer Path Queue Internals

Problem:

- `Path` uses `LinkedList<Point>` for small path queues.
- Movement and path validation allocate many temporary `Point` objects.

Plan:

- `[done]` Replace `LinkedList<Point>` with `ArrayDeque<Point>` in `Path`.
- `[done]` Keep public behavior and max path size unchanged.
- `[partial]` Add regression tests for:
  - straight movement
  - diagonal movement
  - blocked movement
  - walking around walls
  - following/chasing
  - NPC roam bounds
  - player-blocking modes
- `[todo]` After `ArrayDeque` is stable, consider primitive coordinate helpers
  for hot adjacent checks.

Implementation notes:

- `Path` keeps exposing `Deque<Point>` but now uses an `ArrayDeque` backing
  queue sized to the existing maximum.
- `PathValidation` now uses `ArrayDeque` for temporary path queues and existing
  path snapshots.
- The existing path-size guard behavior is intentionally unchanged.
- Added an executable `Path` queue regression that compiles a package-local Java
  test against `server/core.jar`. It locks direct-path queue ordering,
  `finish()` behavior, iterator ordering, and the legacy `addDirect` maximum.
- The same regression now includes a small wrapper parity check for the
  primitive adjacent-distance helper on same-tile validation.

Validation:

- `./scripts/build-server.sh` passed.
- `python3 ./tests/myworld/test-foundation-optimization-guards.py` passed.
- `python3 ./tests/myworld/test-path-queue-regressions.py` passed.
- `./tests/myworld/test-all.sh` passed after wiring the path and packet guards into
  the full MyWorld wrapper.
- Focused movement-adjacent checks passed:
  `test-combat-runtime-invariants.py`, `test-combat-interaction.py`,
  `test-world-start.py`, and `test-entrypoints.py`.
- `./tests/myworld/test-all.sh` passed.
- Player-load benchmark after the queue swap:
  `avgTickMsPrecise=6.246`, `avgProcessNpcsMsPrecise=2.872`,
  `avgUpdateClientsMsPrecise=1.096`.

Primary files:

- [`Path.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/Path.java)
- [`WalkingQueue.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/WalkingQueue.java)
- [`PathValidation.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/PathValidation.java)

Risk:

- Medium. Movement bugs are highly visible and can break quests/combat.

Acceptance criteria:

- Existing movement behavior remains intact for players and NPCs.
- NPCs do not leave allowed roam bounds.
- Combat chasing and interaction pathing still work.
- Custom walking speed mode still works.

### Phase 7: Primitive Path Validation Helpers

Problem:

- `PathValidation` often accepts or creates `Point` objects where primitive
  `x/y` coordinates would be enough.

Plan:

- `[partial]` Add primitive overloads for the hottest adjacent and blocking checks.
- `[partial]` Convert callers gradually.
- `[done]` Keep object-based methods as wrappers until all risky callers are
  covered by tests.
- `[todo]` Avoid changing collision rules during this phase.

Implementation notes:

- Added primitive-coordinate overloads for `checkAdjacentDistance(...)`,
  `checkAdjacent(...)`, and diagonal pass-through collision helpers.
- Existing `Point`-based methods remain as wrappers over the primitive helpers.
- Converted the hot `Path.addStep(...)` adjacent checks to avoid throwaway
  destination `Point` allocations while still storing waypoints as `Point`.
- Converted `WalkingQueue` adjacent checks to call the primitive helper directly
  during movement processing and next-movement peeking.
- Converted remaining direct `checkAdjacentDistance(...)` follow, interaction,
  NPC-behavior, and PVM melee callers to primitive coordinates. PVM melee also
  reuses the computed adjacency result for debug logging instead of validating
  twice.

Validation:

- `./scripts/build-server.sh` passed.
- `python3 ./tests/myworld/test-foundation-optimization-guards.py` passed.
- `python3 ./tests/myworld/test-path-queue-regressions.py` passed after the
  server jar was rebuilt.
- Focused movement-adjacent checks passed:
  `test-combat-runtime-invariants.py`, `test-combat-interaction.py`, and
  `test-world-start.py`.
- `./tests/myworld/test-all.sh` passed.
- Player-load benchmark after the first primitive helper conversion:
  `avgTickMsPrecise=6.349`, `avgProcessNpcsMsPrecise=2.961`,
  `avgUpdateClientsMsPrecise=1.076`.
- Player-load benchmark after converting `WalkingQueue` adjacent checks:
  `avgTickMsPrecise=5.886`, `avgProcessNpcsMsPrecise=2.816`,
  `avgUpdateClientsMsPrecise=1.077`.
- Player-load benchmark after converting remaining direct adjacent-distance
  callers:
  `avgTickMsPrecise=6.446`, `avgProcessNpcsMsPrecise=2.872`,
  `avgUpdateClientsMsPrecise=1.182`.

Primary files:

- [`PathValidation.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/PathValidation.java)
- [`Path.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/Path.java)
- [`WalkingQueue.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/WalkingQueue.java)
- `server/src/com/openrsc/server/model/action`

Risk:

- Medium.

Acceptance criteria:

- Collision decisions match old helper results for a representative tile matrix.
- Movement and combat checks remain green.

## Database And Save Flow

### Phase 8: Audit Save Blocking

Problem:

- Player autosave is triggered from update-time logic.
- If save work blocks the game thread, it can harm tick stability under player
  load.

Plan:

- `[done]` Trace `player.save()` and classify which work is synchronous versus
  queued.
- `[partial]` Measure save latency during normal autosave and logout.
- `[todo]` If blocking work exists, design immutable save snapshots that can be
  handed to the SQL thread.
- `[todo]` Keep logout, death, trade, duel, and other high-integrity state
  transitions strict.

Trace notes:

- `GameStateUpdater.updateTimeouts(...)` calls `player.save()` from the game
  thread during autosave.
- `Player.save(...)` only checks `isSaving` / `isLoggingOut`, sets those flags,
  and enqueues `PlayerSaveRequest` on `LoginExecutor`; it does not run the SQL
  save transaction on the game thread.
- `LoginExecutor` processes save requests on the login thread before generic
  and login requests, preserving the existing anti-duplication ordering.
- `PlayerSaveRequest.processInternal()` runs `PlayerService.savePlayer(...)`,
  which performs the database transaction.
- Logout removal from the world still occurs after a successful save request.

Implementation notes:

- Added save timing counters to `Server`:
  `saveRequests`, `saveLogoutRequests`, `avgSaveQueueMsPrecise`,
  `maxSaveQueueMsPrecise`, `avgSaveProcessMsPrecise`, and
  `maxSaveProcessMsPrecise`.
- `PlayerSaveRequest` now records queue delay and processing duration, then
  reports both to `Server`.
- No save ordering, logout removal, or database transaction behavior changed.

Validation:

- `./scripts/build-server.sh` passed.
- `python3 ./tests/myworld/test-foundation-optimization-guards.py` passed.
- `./tests/myworld/test-all.sh` passed.
- Player-load benchmark summary now includes save timing fields. Synthetic
  players are marked `dummyplayer`, so this benchmark correctly reported
  `saveRequests=0`, `avgSaveQueueMsPrecise=0.000`, and
  `avgSaveProcessMsPrecise=0.000`.

Primary files:

- [`Player.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/entity/player/Player.java)
- [`GameStateUpdater.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/GameStateUpdater.java)
- [`PlayerSaveRequest.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/login/PlayerSaveRequest.java)
- `server/src/com/openrsc/server/database`

Risk:

- High if save ordering is changed.
- Low if this phase remains measurement/audit only.

Acceptance criteria:

- We know whether save work is a real tick bottleneck.
- No durability semantics change without a separate design doc.

## Deeper Structural Work

### Phase 9: Region Storage Review

Problem:

- Regions use synchronized Guava multimaps keyed by `Point`.
- This is convenient, but can be allocation-heavy and lock-heavy.

Plan:

- `[blocked]` Do not start until Phases 1 through 4 are measured.
- `[todo]` Profile actual region storage costs under player/NPC/object load.
- `[todo]` If needed, design a replacement that preserves:
  - point lookup
  - region iteration
  - invisible-to-player filtering
  - object/item removal
  - NPC/player movement between regions
- `[todo]` Build compatibility wrappers before replacing storage.

Primary files:

- [`Region.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/world/region/Region.java)
- [`RegionManager.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/world/region/RegionManager.java)
- [`World.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/world/World.java)

Risk:

- High. This is a foundational data-structure change.

Acceptance criteria:

- Only proceed if profiling shows region storage remains a meaningful bottleneck
  after earlier phases.

### Phase 10: Inventory And Container Encapsulation

Problem:

- Inventory/container internals expose mutable synchronized lists.
- That makes broad optimization risky because external code may mutate lists
  directly.

Plan:

- `[blocked]` Do not start as an optimization pass until there are tests around
  inventory, bank, equipment, trading, dropping, shops, and production.
- `[todo]` Add APIs for common direct-list operations.
- `[todo]` Convert external callers away from `getItems()` mutation.
- `[todo]` Only then consider internal representation changes.

Primary files:

- [`Inventory.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/container/Inventory.java)
- [`ItemContainer.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/container/ItemContainer.java)
- [`Bank.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/container/Bank.java)
- [`Equipment.java`](/home/justin/Core-Framework/server/src/com/openrsc/server/model/container/Equipment.java)

Risk:

- High. Item loss or duplication bugs are unacceptable.

Acceptance criteria:

- No representation change until callers are encapsulated and covered.

## Work Order Summary

Recommended implementation order:

1. Fix current validation baseline.
2. Add benchmark guardrails.
3. Optimize `EntityList` hot iteration.
4. Reduce packet assembly allocation.
5. Reuse visible-region/view calculations inside one player update.
6. Direct-run events in default single-thread mode.
7. Gate forced GC in profiling.
8. Modernize `Path` internals with `ArrayDeque`.
9. Add primitive path-validation helpers.
10. Audit save blocking.
11. Consider region storage only if still measured as a bottleneck.
12. Consider inventory/container internals only after encapsulation and tests.

## Verification Matrix

Run after every phase:

- `./scripts/check.sh`
- focused test for the changed subsystem

Run after phases touching runtime behavior:

- `./tests/myworld/test-smoke.sh`
- `python3 ./tests/myworld/test-entrypoints.py`

Run after movement/pathing phases:

- `python3 ./tests/myworld/test-world-start.py`
- `python3 ./tests/myworld/test-combat-interaction.py`
- `python3 ./tests/myworld/test-combat-runtime-invariants.py`
- movement/pathing tests added during the phase

Run after packet/update phases:

- `python3 ./tests/myworld/test-production-ui.py`
- `python3 ./tests/myworld/test-prayer-ui.py`
- packet-shape tests added during the phase

Run after combat-adjacent phases:

- `python3 ./tests/myworld/test-combat-data.py`
- `python3 ./tests/myworld/test-npc-attack-styles.py`
- `python3 ./tests/myworld/test-combat-scenarios.py`
- `python3 ./tests/myworld/test-balance-fixtures.py`

Run before considering a phase complete:

- `./tests/myworld/test-all.sh`

## Rollback Rule

Every optimization phase should be small enough to revert independently.

If a phase changes behavior or creates uncertain test failures, revert or isolate
that phase before starting the next one. Do not stack multiple foundation
changes on top of an unexplained failure.

## Change Log

- `2026-04-17` Added the initial foundation optimization plan with benchmark,
  entity iteration, packet assembly, region/view, event, pathing, save-flow,
  and deeper structural phases.
