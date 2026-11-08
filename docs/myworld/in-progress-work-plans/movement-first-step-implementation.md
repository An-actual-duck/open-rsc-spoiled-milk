# Authoritative First-Step Responsiveness

Status: active implementation; candidate validation in progress. No public deployment.
Worker branch: `fix/movement-first-step-responsiveness`.
Plan: [Movement and action responsiveness](movement-action-responsiveness-plan.md).
Starting source: `c5567fcfa3ff4b4db90a0b75144536594df87d7e`.

## First Slice: Idle Polls Must Not Spend A Movement Interval

The original `Mob.updatePosition` records `lastMovementTime` after every eligible
`WalkingQueue.processNextMovement` call, including a null/empty path, collision
rejection, and held movement. Consequently an idle actor can receive a new path
just after an empty poll and wait almost another 430 ms before its first step.
This is independent of the separate main-tick incoming-command wait.

The causal fixture exercises actual `WalkRequest`, `Mob`, `WalkingQueue`, path
validation and NPC movement. It varies the path-assignment phase through all
430 milliseconds for players, and 43 phases for NPCs. It does not measure native
input, network transit, packet admission or first visible client displacement.
Never publish its results as an end-to-end click latency measurement.

Baseline checkpoint `30573f4fe07000f5d42edb622b77643f892feda2` introduces one
clock seam: the legacy calculation calls `Server.getGameClock().currentTimeMillis`
instead of `System.currentTimeMillis`. Production `SystemGameClock` delegates to
the same system call. This enables reproducible clock phases, but the baseline
must be labeled “released logic plus clock seam”, not a byte-identical released
JAR. Test-fixture revisions and class hashes are recorded separately.

## Candidate Contract

- Only MyWorld with custom walking uses successful-step scheduling. Other
  profiles keep the legacy empty-poll calculation and void method facade.
- A boolean initialization state handles arbitrary zero/negative `nanoTime`
  origins. Elapsed subtraction handles timer wrap for ordinary bounded spans.
- At most one successful step is committed per admitted update. Success starts
  the next configured interval; rapid stop/reclick cannot produce extra tiles.
- Empty, blocked or held attempts do not spend another interval. Existing
  collision, signed-location, NPC-boundary and effect/readiness checks remain
  the movement authority.
- `WalkingQueue.processNextMovement()` retains its existing void descriptor.
  Its internal reporting counterpart returns whether a queued displacement
  actually occurred. Ordinary moves use the existing destination commitment;
  scoped blink compares cheap compatibility points around the existing teleport.
- An actor without a path or pending end-of-path cleanup returns before reading
  the clock. The new path adds no checked `getWorldLocation` calls to idle polls.
  Existing qualified validations still execute when committing movement.
- The world tick, walking speed, movement packet format, action clocks, combat
  rules and incoming-command admission are unchanged in this slice.

## Validation And Artifacts

Frozen baseline/candidate artifacts, source receipts, fixture class hashes and
raw phase distributions belong under `output/tests/movement-first-step/`.
Review logs and failed fixture-development attempts are retained under
`output/reviews/movement-first-step/`. Resolve new run destinations through
`scripts/workspace-layout.py`; never overwrite a result JSON.

The phase fixture checks exact destinations as well as timing. Its controls
cover recent stop/restart, rapid reclick distance per elapsed time, commit-time
collision, real Cockatrice immobilization, native terrain readiness holds,
attack Stagger remaining distinct from movement locks, scoped blink outcome,
legacy polling behavior, negative/zero/wrapping nanoTime origins and wall-clock
jumps. NPC tests use `updateMovementOnly`, matching the intertick movement owner;
roam decisions are a separate workload.

Fixture development exposed the distinction between immutable native terrain
views and dynamic collision. The collision control therefore introduces a live blocking player after path
admission and restores the original collision configuration afterward, rather
than writing into immutable map authority. Scenery setup attempts did not
affect that fixture’s native collision view and were rejected as controls. Earlier failed runs remain evidence of
fixture setup, not claimed game regressions or accepted performance results.

The frozen baseline phase receipt `baseline-8-phases.json` currently confirms:
players (430 exhaustive assignment phases) p50 215 ms, p95 409 ms, maximum
430 ms; NPCs (43 phases) p50 220 ms, p95 410 ms, maximum 430 ms. These are
accepted causal phase observations, while that run's collision control failed
fixture setup. They are not a passing complete-suite result. Candidate source
compiles; its runtime controls and distributions remain pending.

Reproduction entry point is Ant `test_movement_first_step`, with explicit
`movement.fixture.mode=baseline|candidate` and a fresh `movement.fixture.output`
JSON path. Broader combat, shuriken interaction, movement-boundary and signed
location checks remain required before technical READY. Owner native visual
acceptance is pending while the owner is away.

## Action Transition Inventory And Next Slice Gate

The following is a source inventory, not authorization to move all handlers
between ticks. Current `Player.processIncomingPackets` is called from
`Player.processTick` on the main game tick. The custom intertick branch calls
movement and walk-to execution without draining incoming commands.

| Current owner | New ground walk | Timing risk to preserve |
| --- | --- | --- |
| Plain idle/walking | Existing handler resets path and assigns a new one | Preserve earliest legitimate step and per-player FIFO |
| Melee/ranged/throwing/magic | Walk handler resets combat/hostility through existing owners | Retarget cooldowns and committed attacks must not be reset or duplicated |
| Walk-to action/following | Reset clears target and following state | A stale arrival callback must never execute after replacement |
| Busy/batch/owned plugin | Processor and handler may interrupt plugins or stop a batch | Consumption, production progress, cleanup and thread ownership remain intact |
| NPC/menu/dialogue | Processor cancels menu; reset releases NPC interaction | Avoid unlocking or executing another interaction prematurely |
| Bank/shop/trade/duel | Reset may close interfaces or reset exchange state | Economy and exchange transaction ordering must stay on the normal path |
| Readiness/effect holds | Existing movement commitment refuses a step | Faster command intake cannot bypass a legitimate hold |
| Teleport/death/logout | Existing lifecycle invalidates relevant paths and owners | Old intents cannot resurrect after a location/lifetime change |

A narrowly gated next experiment may consume a bounded eligible HEAD prefix on
the same GameThread between world ticks, only for ordinary idle/walking custom
players. Custom wire 187 is `WALK_TO_POINT`; 16 is `WALK_TO_ENTITY` and remains
a barrier with its associated interaction. Wire 67 is HEARTBEAT, whose handler
is a no-op and is exempt from menu cancellation. Unknown and all other packets
are FIFO barriers. No packet may be searched for or reordered past a barrier.

`WalkRequest.resetAll` touches plugins, batches, menus, NPC interaction, trade,
duel, bank/shop, following and combat/cannon owners. A safe initial eligibility
gate must exclude every such active owner, rather than assume reset is a pure
path assignment. Full transition improvements require their own measured slice.

The existing `RSCPacketFilter` applies its per-second network rate check before
`RSCConnectionHandler` enqueues the packet. The queue also has `PACKET_LIMIT`.
Any new admission path must retain those boundaries and add small per-poll and
per-world-tick budgets, reuse the exact parser/processor/error path, and leave
remainder packets for normal processing. No off-thread gameplay mutation,
coalescing across barriers, early item/spell/economy execution or increased
attack/production cadence is acceptable.

That later slice requires ordinary authenticated-player eligibility, real packet
queue/parser evidence, FIFO/barrier and flood/fairness tests, plus a source
baseline rebased on accepted client performance work. Native visible proof needs
ordinary input or a bounded game-thread intent dispatch seam: reflecting into
`mudclient.walkToArea` from an observer thread risks shared packet-buffer races.
The first slice does not make or claim that admission change.
