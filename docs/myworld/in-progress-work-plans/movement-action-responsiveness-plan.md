# Movement And Action Responsiveness

Status: planning complete for manager/owner review; implementation has not started.
Owner: Core manager; one focused worker branch per measured change.
Planning baseline: `8aa93c01fcfc9d024c634c9181d571399533d048`.
Released game baseline: v0.3.14, `0555b0307bf9a64c96cbe7132777dae3a41ca676`.
Companion: [Client performance and portability](client-performance-portability-plan.md).

## Goal And Boundaries

Reduce the delay between a valid movement click and visible first movement, and
make walking, combat, casting, looting and object/NPC interaction replace or
interrupt each other consistently. Investigate the entire path before selecting
an overhaul; a longstanding symptom is not proof of an unavoidable engine limit
or proof that one particular rewrite will fix it.

Treat initial click latency, pauses during continuous walking, action-transition
latency and invalid/rejected actions as separate outcomes. Preserve server
authority, walking speed, combat/cast/skilling cadence, collision, signed levels,
readiness holds, teleport semantics, PvP legality and existing effect rules.
Do not change the global tick, enable a broad pathfinding flag, move gameplay to
Netty threads, or introduce local movement prediction as an assumed first fix.

The [earlier movement plan](movement-pathing-release-plan.md) and
[July stutter investigation](../completed-work-plans/movement-stutter-investigation.md)
remain historical evidence. Their measurements concern older artifacts/hosts
and continuous waypoint starvation, not a measured diagnosis of the owner's
current first-click delay. This plan owns the next work order and must reconcile
implemented paths before reusing their proposals or diagnostics.

## Current Source Findings — Not Yet Latency Measurements

- `server/myworld.conf` declares a 640 ms game tick and 430 ms custom walking
  interval. Record effective runtime configuration in each private fixture.
- `Server.start` schedules one GameThread every 10 ms. In `Server.run`, the
  game-tick branch calls `Player.processTick`, which drains incoming packets.
  The intertick custom-movement branch updates positions/actions and sends moved
  entity updates, but does not drain that incoming queue. A movement packet can
  therefore await command admission even while movement polling is frequent.
- `Mob.updatePosition` checks `lastMovementTime + getWalkingTick()` and records
  `now` after `processNextMovement`, including an empty/no-move path. An idle
  actor can inherit a movement-poll phase wait after a path is assigned.
  This method currently reads `System.currentTimeMillis`; a controllable-clock
  seam or monotonic deadline change is a narrow candidate, not a chosen rewrite.
- `WalkRequest.process` resets relevant combat/action state and builds a `Path`
  before assigning the walking queue. Interaction clicks can send approach and
  action packets in sequence; preserving their relationship is essential.
- `mudclient.walkToArea` performs local pathfinding, constructs a movement
  packet, requests predictive terrain preload and finishes the packet. Packet
  construction/finish is not proof of an immediate socket write or flush.
- `appendCustomMovementWaypoint` adds server positions and resets interpolation
  when idle; `movementAmountForFrame` advances presentation from elapsed time.
  First visible displacement still needs measurement through native presentation.

Queue admission and idle movement phase are strong source-supported candidates,
not proof of their contribution to this player's delay. Measure them independently
of network RTT, frame phase, pathfinding work and server load.

## Joint Worker Sequence

Use the companion plan's two-stream ownership and measurement rules. A may
characterize source and build isolated fixtures while P works, provided shared
files have one writer. Land shared instrumentation in a reviewed predecessor.
Implement/integrate P's measured performance slices, then rebaseline A on that
exact integrated artifact before behavioral changes. A reproduced correctness
bug may be prioritized explicitly; do not silently combine the two changes.

This task does not resume deferred browser work or change the map loader. Use
private disposable accounts/servers; no live profiling, map/player edits, public
shutdown or deployment is authorized by this plan. Routine engineering choices
remain with workers/manager; escalate concrete gameplay changes and final owner
acceptance, not every ordinary implementation decision.

## A0 — Instrument The Actual Click-To-Movement Path

Freeze source/JAR/map/profile/driver hashes and settings as in P0. Extend existing
`MovementTimingDiagnostics`, server movement diagnostics and renderer telemetry
only where they cannot answer the question. Use bounded opt-in session/intent
IDs, not usernames or credentials. Verify disabled overhead and capture overhead.

| Stage | Required observation |
| --- | --- |
| C0–C1 | Native input received, dispatched to game logic, click accepted/rejected |
| C2–C3 | Local pathfinding start/end, preload request, packet built/queued |
| C4 | Actual socket write/flush submission and any client output queue delay |
| S0–S1 | Server network decode/queue arrival, GameThread admission and queue age |
| S2–S3 | Validation/reset/path assignment, first eligible and first successful step |
| S4 | Authoritative movement packet construction, output queue and flush submission |
| C5–C6 | Receipt/decode/apply, waypoint admission and first interpolation displacement |
| C7 | First displaced character in a completed native frame; visible confirmation |

Use monotonic durations within each process. Correlate local timestamps through
intent/sequence IDs and causal events; never subtract unsynchronized client and
server clocks. Record RTT and jitter separately; RTT/2 is an estimate, not a
measured one-way latency. A wire timestamp extension, if needed, needs explicit
capability/version handling and legacy packet-shape tests before implementation.
Distinguish queued GPU work, completed renders and actual visible presentation.
Measure the end-to-end click-to-first-character-displacement interval on the
client's own clock, paired with server-local stage intervals. Do not add stage
p95 values to manufacture an end-to-end p95. Current packets do not thereby gain
timestamp/intent fields; any such extension is a separately tested proposal.

Record rejection reasons, action owner, queue depth, game-tick/movement-deadline
phase, path depth, readiness status, successful-step times and correction snaps.
Do not count a click marker/animation or diagnostic log as movement improvement.

## A1 — Establish Reproducible Scenarios

| Family | Required scenarios |
| --- | --- |
| First step | Idle ground click, short/long idle, recently stopped, varied tick and walking phases |
| Route changes | Straight/diagonal, rapid new destinations, reversal, stop/restart spam |
| Continuous motion | Long route, waypoint depletion, streamed boundary and busy scene |
| Geometry | Corners, fences, counters, closed/open doors, unreachable and stale targets |
| Interactions | Ground-item pickup, door, NPC conversation and crafting after each combat style |
| Combat transition | Melee/bow/shuriken/autocast to movement/loot/retarget, before and after target death |
| Effects | Slow changes, Whip/Sticky delay, Stagger and explicit immobilization/telegraph locks |
| World/lifecycle | Positive/negative levels, stairs/ladders, teleport/Unify, death, logout/relog |
| Multiplayer | Moving target chase, PvP catching/escape, party/clan/friend and Wilderness boundaries |
| Load/network | Loopback plus measured RTT/jitter/loss profiles, busy server, slow client and backpressure |

Use a private per-connection network bridge or isolated namespace for latency
experiments, never host-wide traffic shaping affecting Oracle or unrelated apps.
Include deterministic clock-driven server tests and ordinary native client runs.
Sample click phases across the full 640/430 ms cycles instead of repeatedly
clicking at one convenient phase. Keep first-step and continuous-route cohorts
separate. Use at least 200 accepted clicks for primary p50/p95 comparisons;
claim p99/tail changes only with sufficient samples (normally 1,000+) and retain
sample counts/distributions. Rejections are a separate reported population.

Deliver a phase-by-phase latency budget and a ranked causal diagnosis before
choosing an implementation. Repeat with diagnostics reduced/off to check that
measurement has not created the delay. Rebaseline after P merges.

## A2 — Remove Confirmed Admission And Scheduling Delay

Evaluate these independently, using A1 evidence to choose order:

1. **Command admission:** if measured queue wait dominates, prototype a bounded
   command-admission lane on the existing GameThread between world ticks.
   Preserve per-player FIFO, approach/action pairs and ordering against earlier
   item, spell and door packets. A movement-only bypass must not overtake earlier
   actions. Keep fairness, queue limits, backpressure and spam handling bounded.
   Earlier admission does not authorize earlier combat damage or production
   progress; preserve each action's existing execution clock and revalidate.
2. **First-step scheduling:** if idle phase wait dominates, evaluate immediate
   authoritative eligibility after admission based on the last successful step,
   while preserving the minimum movement interval and all movement holds.
   Empty polls must not falsely count as progress; stop/reclick/spam must not
   award extra tiles, bypass Slow or change PvP catching/escape speed.
3. **Client dispatch/flush:** remove measured redundant waits or blocking work
   on the click path without reordering packets or starving other UI/network work.

Keep first-step eligibility and steady cadence separate. Do not compensate for
late polls with an unlimited burst of steps or change walking speed globally.
Each experiment needs a separate artifact, focused tests and matched A/B runs.

## A3 — Make Action Replacement Consistent

Inventory the actual states/owners and write a transition table before changing
their lifecycle: current action, incoming intent, admission rule, cancellation,
path/target ownership, cooldown retained, arrival condition, execution and cleanup.
Include movement, approach, attack, autocast, loot, dialogue and production.

Use that table to centralize one proven family at a time. Define which latest
intent supersedes which pending intent; do not install generic “last packet wins.”
Cancel obsolete paths/callbacks so they cannot execute after a new action,
teleport, death or relog. Preserve legitimate busy states, held terrain activation,
server-side reach/LOS checks, committed attacks and intentional cooldowns.
Failure messages and retry behavior must distinguish blocked, invalid, busy and
replaced actions rather than requiring an unrelated movement click to recover.

Reproduce the shuriken-to-interaction regression and its existing tests as a
control. Add deterministic tests for newly found defects and ordering/lifecycle
boundaries, rather than assertions that merely mirror a chosen implementation.

## A4 — Evaluate Presentation Only If Residual Delay Warrants It

Review exact walking-interval presentation, interpolation clocks and bounded
arrival-jitter handling against initial-latency and steady-motion results.
A buffer that smooths remote actors may worsen local first-click latency;
measure both. Keep local player, remote players and NPCs distinct.

Only after authoritative delays are understood, consider optional local visual
anticipation with reconciliation. It must not grant authoritative position,
damage, reach, pickups, hidden terrain knowledge or movement through blockers.
Specify rejection, correction, packet delay/loss, server-path divergence and
level/teleport resets first. If acceptable reconciliation cannot be proved,
retain authoritative movement; do not disguise networking limits as resolved.

## Acceptance And Completion

- [ ] Establish baseline-dependent goals before each slice: remove an identified
  extra queue/deadline wait and improve its p50/p95 beyond repeated-run noise;
  preserve negative controls and report the remaining RTT/frame-phase budget.
- [ ] In an unconstrained private case, target first successful step by the next
  eligible movement poll after valid admission, rather than another avoidable
  world tick; exact tolerance comes from measured scheduler behavior.
- [ ] Preserve distance travelled per elapsed time, collision and signed-level
  isolation across rapid clicks, effects, PvP and lifecycle tests.
- [ ] No stale/duplicate action, attack-rate increase, teleport regression,
  stranded busy state, queue starvation or packet compatibility regression.
- [ ] Compare three or more matched baseline/candidate runs, including actual
  native visual evidence and a slower-client control; no universal “instant” claim.
- [ ] Owner privately accepts ordinary ground walking and the combined action
  route after automated gates. State unavailable hardware/network cases plainly.

Deliver causal timing traces, transition table, scenario commands, before/after
distributions, regressions, limitations and exact pushed READY commits. Resolve
output destinations with `scripts/workspace-layout.py` under diagnostics/tests/
reviews, subject and run. Close the pass after the measured first-step and action
goals are met; propose remaining independent work rather than expanding forever.
