# Client Performance And Hardware Portability

Status: planning complete for manager/owner review; implementation has not started.
Owner: Core manager; one focused worker branch per measured change.
Planning baseline: `8aa93c01fcfc9d024c634c9181d571399533d048`.
Released game baseline: v0.3.14, `0555b0307bf9a64c96cbe7132777dae3a41ca676`.
Companion: [Movement and action responsiveness](movement-action-responsiveness-plan.md).

## Goal And Owner Direction

Reduce the actual work needed to run the client, giving slower computers more
headroom while retaining the approved shadows, relief plus valley shading,
content, draw distance at a given setting, and gameplay behavior. A development
PC reaching the 60 FPS cap is a baseline, not evidence of a hardware minimum.

This plan covers the whole desktop client: input and update work, packet
application, scene preparation, rendering, allocation and GC, asset loading,
menus, startup, and lifecycle retention. It extends the existing
[performance investigation ledger](renderer-performance-investigation-plan.md).
That ledger preserves prior optimization results; do not repeat accepted work
or treat historical machine/settings values as current measurements. Its old
statement that GPU timers are unavailable is superseded by the shipped shadow
benchmark's asynchronous GPU timing. This document sets the next work order.

No new visual compromise, forced quality reduction, global tick change, game
balance change, broad class rewrite, dependency migration, or public deployment
is implied. Any improvement requiring a visible quality tradeoff comes back
to the owner as a specific measured choice.

## Coordination With The Movement Work

Keep at most two workstreams in flight. Performance is stream P; movement and
action responsiveness is stream A. Each receives a separate registered worker
slot/topic branch when activated; no slot is assumed available without manager
inventory and preservation of its current task.

1. Both may investigate source and design bounded instrumentation in parallel.
   The manager assigns exact files/methods first. Shared telemetry, `mudclient`,
   `PacketHandler`, and the main input/update loop have one writer at a time.
   Private fixtures use separate ports/databases. The same neutral worker slot
   may execute both streams sequentially if that is the simpler allocation.
2. Benchmarks run in exclusive measurement windows: no second game client,
   competing benchmark, asset build, or profiler on the measured GPU/CPU.
3. Land P's baseline/instrumentation and measured optimization slices first.
   Rebase A through normal reviewed integration and remeasure its baseline
   before changing movement behavior. A reproduced correctness defect can take
   priority through an explicit manager sequencing decision.
4. Integrate behavior changes back to back. Two workers must not independently
   change the same input, packet, interpolation, or scheduling contracts.
5. Every slice ends in an exact pushed READY handoff, evidence, manager review,
   relevant tests, and a clean integration. Routine choices do not require
   repeated owner check-ins; intentional visual/gameplay changes and the final
   private acceptance do. A handoff never authorizes public-server shutdown.

## P0 — Freeze A Reproducible Baseline

- [ ] Record the exact source commit, client/server JAR hashes, map manifest and
  fingerprint, protocol, launcher arguments, saved settings, assets and scenario
  driver revision. Do not silently substitute rebuilt artifacts mid-comparison.
- [ ] Record CPU/GPU/driver, RAM, OS, Java/collector/heap, power state, display
  surface and content viewport, resolution, zoom/rotation/pitch, fog, shadow
  quality, terrain/object shading, sun state and received/submitted scene counts.
- [ ] Use 1920x1080 and the normal gameplay camera for the owner's main target;
  keep maximum zoom as a separate stress case. Add representative lower settings
  for a weaker device without calling them equivalent visual workloads.
- [ ] Distinguish a fresh-process startup, warmed asset startup, and a genuinely
  cold storage/cache case. Do not flush system-wide caches or disrupt other apps
  merely to claim a cold test.
- [ ] Preserve the approved v0.3.14 client as the comparison artifact. Audit
  existing diagnostics before adding fields; unknown observations stay unknown.

Use existing `scripts/renderer-benchmark.py`, bounded renderer diagnostics,
movement diagnostics and the existing capture/analysis tools where suitable.
Opt-in additions must record their own overhead and truncation. No credentials,
account names, public addresses, chat, or unrelated player traffic belong in
profiling reports. Use disposable private accounts and scenario IDs.

## Workload Matrix

| Case | Required exercise | What it separates |
| --- | --- | --- |
| Startup/login | Fresh process, normal login, first useful frame; warm repeat | Decode, archive I/O, initialization, upload and loading stalls |
| Dense idle | Same populated scene, normal and maximum zoom | Steady CPU/GPU work and allocation below the FPS cap |
| Camera motion | Fixed rotation/zoom route over that scene | Culling, scene preparation and submission changes |
| Active combat | Melee, ranged, magic, summons and effects | Actor/effect/UI preparation under real production events |
| Travel/transitions | Walking, chunk edge, level change, teleport and return | Streaming, cache invalidation, upload and recovery spikes |
| Interface | Inventory/bank/guide/spellbook open, scroll and selection | UI layout, text, sprites, input and packet application |
| Lifecycle | Repeated relog/area visits/window resize and minimize/restore | Retention, resource cleanup and recovery |

Freeze routes, actor populations, sunlight and durations per case. Use the
existing factual combat/resource checks rather than a visual-only projectile
generator for claims about real combat workload. Keep warmup/loading windows
separate from steady measurements, but report both.

## Metrics And Hardware Evidence

At a 60 FPS cap, use CPU work time and GPU elapsed time to expose headroom;
sleeping/limiting time is not productive work. Do not add overlapping CPU and GPU
durations as though they are serialized. Keep asynchronous GPU query coverage,
completion delay and phase-edge trimming explicit; avoid forced GPU waits.
Do not sum separate stage p95 values and label the result an end-to-end p95.

For each case record:

- Completed renders, queue drops, present intervals and their p50/p95/p99/max;
  counts over 16.667, 25, 33.334 and 50 ms, with loading reported separately.
- A consistently defined 1% low: reciprocal of the mean interval of the slowest
  1% of completed frames. State sample count and distinguish hidden completed
  renders from visible compositor/display presentation.
- Main-loop, input/update, packet apply, scene, UI, render submission and swap
  work; process and relevant thread CPU use; CPU/allocation profile attribution.
- GPU frame/near/far pass timings where supported, draw calls, geometry,
  uploads, texture churn, reuse/eviction, and observed capability fallback.
- Allocated bytes per second, GC pause distribution/time, live heap after GC,
  RSS, direct/native buffer counts/bytes, and retained resources across lifecycle
  repetitions. Low average GC time does not explain away an isolated long pause.
- Startup/first-frame time and transition/input tails alongside steady averages.

Run at least three matched, interleaved baseline/candidate pairs with the same
warmup and duration. Profile separately: instrumented versus instrumented, then
repeat accepted changes without JFR/deep timers/captures. Record host contention,
thermal changes and configuration drift; reject mismatched runs explicitly.

The physical integrated GPU is a useful second GPU, but it shares the owner's
fast CPU and Linux driver family. Obtain a real slower CPU/iGPU or independent
volunteer machine for broader claims when available. CPU quotas, affinity,
smaller heaps and software GL are diagnostic stress cases, not substitutes for
weak hardware. Missing Windows/macOS/native-device evidence remains a stated
limit; no universal 60 FPS or minimum-spec promise follows from this plan.

## P1 — Rank Costs Before Selecting Changes

- [ ] Produce a ranked table of the top three costs by time, allocation or
  retention, with owning stack/method, scenario, frequency and confidence.
- [ ] Separate unavoidable work from redundant work, cache churn, synchronous
  loading, packet bursts, blocking calls and lifetime errors.
- [ ] Check normal input/update/asset/menu paths, not just OpenGL. Existing
  renderer optimizations and caches must be measured before replacing them.
- [ ] Select one narrow change with a predicted measurable effect and explicit
  correctness/parity contract. File size and source appearance are not evidence.

Candidate families include avoiding repeated transforms/classification/copies,
reusing safely owned temporary storage, reducing upload/texture rebuilds,
deferring genuinely independent decode work, or retaining only useful cache
entries. These are hypotheses, not preselected implementations. Threading must
respect renderer context ownership and immutable frame lifetimes.

## P2 — Optimize, Compare, Accept Or Reject

For each candidate:

1. Freeze its baseline, hypothesis, target metric, controls and tests.
2. Implement the smallest owner-bound change on its own branch/checkpoint.
3. Run focused correctness and renderer guardrails; inspect images/scene counts
   so missing actors, geometry or effects cannot masquerade as a speed gain.
4. Repeat the matched matrix cases it can affect, including a negative/control
   case, normal diagnostic-off execution, and a lifecycle recovery case.
5. Accept a repeatable gain larger than observed noise, without material
   regression elsewhere. As an initial selection target, seek roughly 10% of
   the identified costly stage, or a clearly reduced recurring long-frame tail;
   revise that target from the baseline rather than promising a whole-client gain.
6. Record rejected or inconclusive experiments too. After two equivalent
   inconclusive attempts on one cost, change target or stop instead of layering
   speculative caches and abstractions.

No initial numeric RSS/heap/latency reduction is promised. Set a budget once P0
establishes an ordinary workload and justify it from actual live resources.

## P3 — Close The Pass And Hand Over

After the first three justified optimization slices, or earlier if no material
cost remains, reassess. Do not keep the program open indefinitely for diminishing
returns. Complete a private owner route with the accepted visuals and ordinary
launcher/settings. Define any longer memory-retention run in advance; it need
not block unrelated short checks, and must not be falsely reported as completed.

Deliver: baseline manifests, workload driver/commands, complete raw measurements,
ranked findings, before/after table per accepted change, rejected experiments,
visual/correctness results, hardware limits, and exact READY commits. Store each
run under a declared `diagnostics`, `tests`, or `reviews` category using
`scripts/workspace-layout.py`; keep code/measurements/logs together by subject/run.

Completion means demonstrably less work or better tails in the affected cases,
unchanged approved output and gameplay, bounded diagnostic/resource behavior,
and an honest hardware-coverage statement. Hand A the accepted integrated
artifact and its baseline so movement gains are measured independently.
