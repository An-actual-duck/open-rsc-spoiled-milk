# Client Performance And Hardware Portability

Status: active measured implementation; owner authorized autonomous worker execution on November 8, 2026 (UTC).
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

## November 8, 2026 — First Measured Resource Slice

Execution began from published planning commit `c5567fcfa`. No public operation
is part of this pass. The frozen released v0.3.14 client has SHA256
`18779f57d12f076413a7222235a6edf8717af9770e68b549733984981b85da28`.
A fresh loopback-only Java 21 normal-camera busy-scene profile used 30 seconds
of warmup and a 60-second marked phase. JFR events were filtered by that phase's
UTC boundaries; startup allocations are excluded. The weighted allocation
estimate was 71.59 MiB/s, of which character composite pixel arrays accounted
for 18.26 MiB/s (25.5%). Native direct composite and padding buffers were also
allocated per synchronous upload; their bytes are not included in that heap
estimate. Existing scenery identities/builders, text/glyph keys and remaining
float-uniform reflection are separate ranked follow-ups, not accepted changes.

The initial ranked observations (one sampled baseline, not universal CPU time):

| Rank | Cost | Evidence | Decision |
| --- | --- | --- | --- |
| Allocation 1 | Multipart composite arrays | 1,096.17 weighted MiB / 60 s | Bounded scratch reuse selected |
| Allocation 2 | Resident object canonical identities | 330 weighted MiB / 60 s | Separate scenery-lifetime investigation |
| Allocation 3 | Colored text and glyph command/key work | Several separate 200–272 MiB groups / 60 s | Separate UI/text ownership investigation |
| CPU 1 | Frame packed-RGB to RGBA copy | 163 execution samples | Bulk/format path needs its own parity scope |
| CPU 2 | Masked scaled software sprite plotting | 83 execution samples in leading method | Investigate only with exact image oracle |
| CPU 3 | Scene.endScene | 82 execution samples | Profile deeper before changing scene construction |

The first candidate retains bounded render-thread scratch for multipart sprite
composition and atlas-padding upload. Independent-buffer APIs remain independent;
borrowed buffers are consumed synchronously before the next acquire. Retention
is capped at 1,048,576 pixels / 4 MiB per buffer, oversized requests use
independent nonretained storage, and renderer/atlas cleanup releases references.

Checkpoint `86961b57cfb199b2dd66360713ee9e9dd6047857` freezes the initial candidate.
The released-JAR oracle and candidate match exactly across 1,000 seeded list and
snapshot composites, including shear, layer order, partial alpha and padding
(SHA256 `c460a449b004bf6ee0b8f6def7d9b3a9d1a1997364117349f47e1c1b98818098`).
Ownership, grow/shrink/clear, byte order, independent owners, oversized fallback
and idempotent release checks pass. Actual hardware GL texture readback proves
pixel/padding identity, full-atlas retry and oversized rejection/recovery; the
complete renderer guardrail suite passes.

The frozen candidate JAR is `output/tests/client-portability-pass/workspace-86961b57c.jar`,
SHA256 `cac7b49f6764a522ab94e43739129f56c518feca61996baceba920cff86dc2e6`.
It was not rebuilt or substituted between measurements. The candidate was built
from checkpoint `86961b57c`; this final ledger update changes documentation only.
The released artifact contains its release-build marker while the candidate is
a maintained development build; that marker controls developer hotkeys, unused
by the hidden benchmark. No synthesized hotkey/UI change is part of this test.

Three interleaved native pairs used Java 21 / G1 / 512 MiB initial and 2 GiB
maximum heap, 1920x1080, zoom 75, rotation 128 with the same rotation route,
High shadows, RELIEF_AO, fog on, fixed sun 135/45 and daytime tone. Each used
20-second warmup and a marked 40-second phase. No competing build or benchmark
ran in accepted timing windows. The map manifest is
`42e20c36f19bd7c5a2ce7cfd2d65c91809c8671ff4bdf7b4e8b45014e8290c2b`;
all server artifacts, maps, libraries, source receipts, arguments and hashes are
preserved per capture. Populations were 16 controlled players, 32 controlled
NPCs and 8 requested summons, with 57 visible multipart snapshot groups /
163 layers and zero snapshot-compatibility fallback in both variants. Resident
world geometry is 89 chunks / 435,422 triangles. Camera timing causes small
frustum-edge differences in submitted terrain triangles; no geometry or actor
quality reduction was introduced.

The mean of per-run metrics is below. Resource rates use the inner timed
periodic samples, excluding the first five seconds. CPU is process CPU seconds
per wall second; allocation is ThreadMXBean measured thread allocation, not JFR
weighted sampling. Direct/heap/RSS occupancy depends on collection timing.

| Metric | Released | Candidate | Change |
| --- | ---: | ---: | ---: |
| All-Java-thread allocation | 69.36 MiB/s | 50.99 MiB/s | -26.5% |
| Presenter thread allocation | 33.41 MiB/s | 15.06 MiB/s | -54.9% |
| Main client thread allocation | 35.93 MiB/s | 35.91 MiB/s | -0.1% |
| Process CPU cores | 0.748 | 0.707 | -5.4% observed |
| Median direct-buffer bytes | 293.26 MiB | 70.92 MiB | -75.8% observed |
| Median direct-buffer count | 44,543 | 2,586 | -94.2% observed |
| Median RSS | 2,484 MiB | 2,011 MiB | -19.1% observed |
| Mean GL work | 3.617 ms | 3.550 ms | -1.8% observed |
| GL work p99 | 4.890 ms | 4.796 ms | -1.9% observed |
| GPU frame p99 | 8.173 ms | 8.665 ms | +6.0% observed |
| Completed-render interval p99 | 18.654 ms | 18.714 ms | +0.3% observed |
| 1% low completed-render FPS | 46.31 | 47.75 | +3.1% observed |

Per-run tails are retained rather than concealed by the average:

| Pair | Version | Renders | Queue drops | Interval max ms | 1% low FPS | GL p99 ms | GPU p99 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | Released | 2402 | 0 | 38.01 | 46.99 | 5.007 | 8.204 |
| 1 | Candidate | 2401 | 0 | 22.25 | 50.88 | 4.771 | 8.010 |
| 2 | Released | 2401 | 0 | 35.55 | 45.68 | 4.816 | 8.129 |
| 2 | Candidate | 2401 | 0 | 33.66 | 50.62 | 4.779 | 9.619 |
| 3 | Released | 2401 | 0 | 38.83 | 46.27 | 4.848 | 8.186 |
| 3 | Candidate | 2397 | 5 | 122.47 | 41.75 | 4.839 | 8.367 |

The third candidate's five queue drops occurred inside the marked phase, between
24.664 and 29.665 seconds, before post-phase screenshot capture. In that same
five-second telemetry interval, GL work max was 5.55 ms, main-loop max 19.14 ms,
and sampled GC count/time deltas were zero. The available telemetry cannot
attribute the 122.47 ms presenter interval to a specific cause; safepoint/OS
scheduling evidence is insufficient. The second candidate's higher GPU tail
also remains visible. These observations prevent a claim that frame pacing
improved universally. Preserve all three pairs and investigate with a further
matched control after the movement worker's CPU window; do not cherry-pick the
five cleaner runs. Source is ready for manager review as an allocation/resource
slice; final pacing acceptance and owner visual acceptance remain pending.

Separate matched 30-second warmup / 60-second JFR profiles support attribution:
weighted whole-client allocation was 71.59 -> 54.40 MiB/s; the composite builder
fell from 1,096.17 to 15.98 MiB over the phase (-98.5%, small return objects
remain). Samples identify packed-RGB frame conversion, software sprite plotting,
scene preparation/depth construction as remaining CPU costs. Resident scenery
identity/builders, text/glyph command/key construction, and float-uniform
reflection remain allocation candidates. JFR is sampled and perturbs execution;
it was disabled in all three acceptance pairs. Bounded existing telemetry,
asynchronous GPU timing and GC logging were enabled equally in those pairs;
its absolute overhead has not been established with a separate off cohort.

An additional 20-second native functional control selected the physical Raphael
iGPU (`DRI_PRIME=pci-0000_12_00_0`) and reported its actual Mesa renderer, 1,201
completed renders and zero queue drops, with native capture. The main three
pairs used the RX 6700 XT. Both use the same Ryzen 9 7900X and Linux/Mesa driver
family; this is not proof for a weaker CPU, Windows/macOS or a minimum hardware
specification. Hidden completed renders are not visible display scanout. No
cold-storage-cache, large interface matrix or long lifecycle soak is claimed;
the changed scratch lifetime is instead covered by focused ownership/release,
padding, grow/shrink and actual-GL fallback fixtures.

One released baseline startup attempt stalled fetching private server configs
before login, so it has no timed phase and is rejected/preserved separately.
A bounded thread-dump attempt did not yield useful stacks. Only its identity-
guarded private leftover was terminated after normal private stop did not exit.
Its cause is unproven; the resumed driver waits for both private status.json and
the listener, and all remaining startups completed. A separate stop-helper
ownership race occurred after one completed run; both registered PIDs, server
PID file and listener were already gone on inspection. Neither event is
silently counted as a successful lifecycle test or candidate regression.

Raw data, driver/analyzer source, immutable JARs, profiles, host inventory,
per-run hashes, resource samples, rejected runs, native captures and tests are
under `output/diagnostics/client-portability-pass/`,
`output/tests/client-portability-pass/` and marked
`output/tests/renderer-shadow-benchmarks/portability-*` fixtures. The compact
review bundle is `output/reviews/client-performance-session/`. No public build,
release, deployment or shutdown occurred.
