# Authoritative First-Step Responsiveness

Status: integrated and validated; owner private playtest pending.
No public deployment.
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
- At most one successful step is committed per admitted update. Success records the post-commit monotonic time and starts
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

The matched complete runs are `baseline-9.json` (4,916 passing checks) and
`candidate-9.json` (4,935). Both use the same fixture classes and injected clock;
the candidate overlays only `Mob` and `WalkingQueue` on the frozen baseline.

| Actor / assignment phases | Baseline p50 / p95 / max | Candidate p50 / p95 / max |
| --- | --- | --- |
| Players / all 430 one-millisecond phases | 215 / 409 / 430 ms | 5 / 9 / 10 ms |
| NPCs / 43 ten-millisecond phases | 220 / 410 / 430 ms | 0 / 0 / 10 ms |

These numbers isolate path-assignment-to-first-authoritative-step under a 10 ms
poll. All negative controls listed above passed, including live collision after
path admission, same-tile waypoints, exact player/NPC reclick trajectories and
NPC roam-boundary rejection. They do not include the still tick-bound packet
admission delay. Earlier `baseline-8-phases.json` remains a valid partial phase
receipt with a rejected fixture-only collision setup; it is superseded by the
complete matched run, not silently overwritten.

Reproduction entry point is Ant `test_movement_first_step`, with explicit
`movement.fixture.mode=baseline|candidate` and a fresh `movement.fixture.output`
JSON path. Completed validation:

- Matched frozen baseline/candidate: 4,916 / 4,935 checks; Ant candidate entry
  point independently passed the same 4,935 checks.
- Current combat characterization: 149 / 149 scenarios and nonzero summary.
- Six shuriken interaction routes: pickup, door and NPC dialogue both during
  throwing and after the target's removal.
- Full packed-location account loading: 57 assertions with a fresh synthetic
  SQLite/native fixture. The older driver emitted a legacy output-root path;
  this run's complete evidence was preserved in
  `output/tests/movement-first-step/legacy-login-bootstrap-c995e84475`.
- Production `scripts/build-server.sh`: generated-contract checks, core/plugin
  compilation and shipped classpath/artifact audit passed.
- `git diff --check` passed. Workspace layout validation still reports preserved
  historical output-root entries already present in this worker; no cleanup or
  deletion was attempted. New movement evidence uses declared categories.

The additional PvP foundation suite has one inherited failure:
`pvp_saradomin_curse_and_phase_wire`, asserting that the wire transmits another
player's translucent phase. The same fixture and failure reproduced against
both candidate and frozen baseline core. The aggregate Ant regression invocation
therefore exited failed at that extra gate; the remaining strict-combat and
login checks were subsequently run through their ordinary harness/driver entry
points and passed. This is an explicit coverage defect to triage, not a claimed
passing PvP gate or an attributed movement regression. Logs are preserved as
`regressions.log`, `baseline-pvp.log`, `candidate-combat.log` and
`candidate-bootstrap-driver.log` in the review directory.

Frozen artifacts are `baseline-core-30573f4fe.jar`,
`candidate-controlled-core.jar` (only the two changed classes over that baseline),
`candidate-production-core.jar`, `candidate-production-plugins.jar`, and
`fixture-classes.jar`. `artifact-receipt.json` records SHA-256 values for these,
production/fixture sources and the accepted result JSONs. The baseline is the
production-equivalent clock-seam build described above. None is an original
released binary. Owner native visual acceptance remains pending while the owner
is away; native input and client frame latency are outside this first slice.

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


### Next Admission Experiment: Resource And Ordering Gates

The shipped `mudclient.walkToArea` sends four absolute coordinate bytes and at
most 25 signed waypoint pairs (54 payload bytes). The general custom parser
currently accepts any walk payload of at least four bytes. A future fast lane
should only admit the normal even-length 4–54-byte shape. Larger or unusual
packets remain normal-tick FIFO barriers; this does not redefine valid protocol
shapes or replace the parser's existing malformed-packet response.

Per-player limits alone are insufficient at a 100 Hz poll rate. Use small
per-player/per-poll and per-world-tick allowances plus a global poll allowance.
A persistent rotating admission cursor must prevent the first players in the
world list from consuming the global allowance forever. Do not reorder the
existing authoritative movement loop to achieve that fairness. Budget exhaustion
leaves the exact remaining head and suffix queued for the ordinary tick.
Heartbeat consumes an allowance too; no unbounded bookkeeping prefix is allowed.

Before implementing such a lane, verify these behaviors with real `Packet`
objects through queue, custom parser and processor, then a normal authenticated
private account:

| Input / state | Required evidence |
| --- | --- |
| Idle ground walk | Admitted on GameThread between ticks; earliest eligible actual step |
| Two ground clicks | FIFO processing; newest destination replaces only preceding walking |
| Item/spell/action then walk | Earlier packet and following walk remain tick-bound in order |
| Approach then interaction | Both remain on ordinary tick; no stale arrival callback |
| Busy/combat/batch/menu/exchange | Entire prefix remains normal-tick; no early reset callbacks |
| Heartbeat then walk | Bounded ordinary parser/processor use; heartbeat cannot starve movement |
| Unknown/malformed/oversized walk | Barrier retained; ordinary error and rate paths preserved |
| Per-poll/per-tick exhaustion | Exact unconsumed head retained; no path superseding a barrier |
| 64/256 queued clients | Bounded CPU work and rotating fairness, not just one-player latency |
| Spam and reconnect/logout | Existing flood filter, queue bound and lifetime rules remain active |

The first successful-step slice deliberately does not include these admission
changes. It can be integrated independently without claiming this follow-up is
complete.
