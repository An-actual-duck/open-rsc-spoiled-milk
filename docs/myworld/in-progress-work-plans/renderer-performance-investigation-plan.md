# Renderer Performance Investigation Plan

Status: active investigation. Baseline instrumentation is implemented and
guard-tested; controlled current-branch baseline collection and profile
attribution are underway, and the first focused allocation optimization has
been accepted.

This is the living measurement and optimization ledger for the ongoing
renderer-v2 performance workstream. It complements
[renderer-and-shader-roadmap.md](renderer-and-shader-roadmap.md), the detailed
[renderer-v2-plan.md](renderer-v2-plan.md), and the implemented
[renderer-diagnostic-session-logging-plan.md](renderer-diagnostic-session-logging-plan.md).
Those documents retain architecture and historical implementation detail; this
document records repeatable workloads, measurements, hypotheses, experiments,
and accepted performance changes.

## Objective And Guardrails

The primary goal is a stable renderer with objectively lower CPU time, frame
time, allocation pressure, geometry submission, draw-call pressure, and
transition cost. Renderer work remains the priority, but client/server
synchronization, map loading, networking, pathing, and garbage collection are
in scope when measured evidence connects them to a hitch or sustained cost.

- Preserve accepted visuals, interaction, and game behavior. Any intentional
  quality/performance tradeoff must be discussed before implementation.
- Compare one focused change at a time against the same route, settings,
  viewport, JVM, and machine state.
- Keep diagnostics opt-in and bounded. Normal and release clients must not pay
  for profiling features that are not enabled.
- Treat `Ctrl+F9` as a visual/parity capture, not a performance benchmark. Its
  synchronous artifact capture is deliberately intrusive.
- Do not call a change an optimization merely because it feels smoother.
  Record before/after data and retain regressions as evidence too.
- Use the private/local server for experiments. Performance work does not
  authorize deployment to or interruption of the public server.

## Reference Machine And Runtime

The initial investigation machine is:

- CPU: AMD Ryzen 9 7900X, 12 cores / 24 logical processors.
- GPU: AMD Radeon RX 6700 XT using Mesa 25.2.8 and OpenGL 4.6.
- Memory: 30 GiB visible to Linux.
- Display: 1920x1080 borderless fullscreen.
- Client runtime: Temurin OpenJDK 8u482, `-Xms512m -Xmx2g`.
- Renderer: OpenGL primary, replacement composite, resident world chunks,
  spatial culling enabled, 3 ms chunk-upload budget, vsync disabled.
- Target cadence: 60 FPS / 16.667 ms.

The session manifest must remain the authority for the exact revision and
settings. Host load, power state, free memory, swap use, and unrelated
foreground work should be noted before benchmark runs. This machine is useful
for development comparisons but is not the eventual minimum target hardware.

## Existing Measurement Coverage

The diagnostic bundle already records:

- client-loop, scene, presentation, OpenGL, world, upload, draw, sprite,
  overlay, debug, and swap timing;
- geometry by owner and material family, triangles, resident chunks, batches,
  draw calls, texture binds, culling, reuse, upload, deferral, and eviction;
- texture-cache, sprite-ownership, 2D command-capacity, shadow-mask, and
  resident-replacement signals;
- heap pools, post-GC old generation, native buffer pools, GC count/time,
  runtime settings, login/logout boundaries, region transitions, and
  structured exceptions;
- `Ctrl+F9` visual/parity artifacts and `Ctrl+F8` observed-stutter markers.

Remaining limitations:

- CPU-side OpenGL submission time is not separated from GPU completion/wait
  time. There are no GPU timer-query measurements.
- Broad `openGL.world` time contains work not fully represented by the current
  upload/projected-mesh/chunk-draw split.
- The diagnostic measurements themselves have overhead, especially allocation
  sampling and serialization. JFR and ordinary diagnostic runs must remain
  separate comparisons.
- Camera zoom, pitch, rotation, allowed zoom range, first-person state, fog
  mode, and effective fog/draw distance are now structured at each diagnostic
  report and summarized per named phase. This was added after a user-observed
  zoom mismatch showed that geometry alone cannot prove test configuration.
- The original 64 MiB structured-log budget was exhausted after roughly
  11.5 minutes once true per-frame samples were added. The bounded diagnostic
  default is now 256 MiB, sufficient for the intended 30-minute profiling
  window, and live truncation is surfaced in both the manifest and analyzer.

## Baseline Ledger

These are reference observations, not yet an apples-to-apples benchmark
matrix:

| Session | Workload | Duration | Client loop p95 | GL render p95 | GL world p95 | Normal drops | GC share |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `session-20260710-202337-3014696` | accepted retention route | 444.6 s | 17.215 ms | 8.688 ms | 7.284 ms | 1.51% | 0.61% |
| `session-20260711-162902-3420265` | accepted renderer/shading route | 683.5 s | 16.675 ms | 7.121 ms | 5.111 ms | 0.81% | 0.47% |
| `session-20260728-194143-1164617` | opportunistic layered-map test | 320.4 s | 16.983 ms | 16.106 ms | 14.294 ms | 2.19% | 0.37% |

The July 28 session is the closest evidence to current `main`, but it mixes
login time, dense-area travel, hard region/layer transitions, and map-loader
validation. It is therefore a regression signal, not a controlled baseline.
Its late in-world samples show that OpenGL world work can dominate frame time:
one late report window averaged about 7.83 ms in `openGL.world`, including
about 4.08 ms in resident chunk drawing and 0.97 ms in chunk upload. That
window averaged about 183k submitted triangles and 278 draw calls, with maxima
of about 341k triangles and 558 draw calls. The unaccounted remainder inside
`openGL.world` and the CPU/GPU ownership of the draw cost need measurement
before optimization.

GC is not presently the leading explanation: the same July 28 session spent
about 0.37% of sampled time in collection. Allocation churn may still affect
frame tails and CPU use, so it remains an instrumentation target rather than a
presumed root cause.

The first controlled current-branch phase,
`session-20260728-205513-1276663` / `dense-idle`, ran for 109.5 seconds in
Seers under the Remaster preset and the explicit F3 zoom reset. F3 selects
zoom setting `75`, an effective camera zoom of roughly `750`; this is a
repeatable normal/default-view baseline, not a maximum-distance stress test.
Visual review passed. True OpenGL render p95/p99 was 9.952/10.901 ms and
OpenGL world p95/p99 was 7.417/7.895 ms across 6,550 frames. The process
averaged 0.826 CPU cores and diagnostic Java-thread allocation averaged
653.16 MiB/s. The renderer requested 34 resident chunks per frame, reused
about 33.28, and reuploaded about 0.72; the reuploads were associated with
`animated-object-signature`. Even at rest, the chunk-upload phase was about
1.5 ms p95. GC remained secondary at roughly 0.42% of elapsed time. This
makes animated resident-object rebuilds, chunk enumeration/draw submission,
and allocation-stack attribution the first profile targets.

No visual issue was reported during the first `dense-camera` attempt, but it is
not a valid timing comparison: its phase began after the original structured
telemetry budget had been exhausted. Its markers remain intact, but frame,
CPU, and allocation samples are unavailable. Repeat it in a fresh session
after the budget correction rather than inferring performance from the
incomplete capture.

The corrected `dense-camera-r2` phase on checkpoint `8b14d42c4` ran for
91.3 seconds with complete telemetry and no reported visual issue. The owner
then noted that this view was substantially zoomed in. Because the session did
not structure camera zoom, it cannot prove exact zoom equality with
`dense-idle`; do not treat the apparent frame-time improvement as a
heavy-scene camera comparison. Within the captured view, OpenGL render
p95/p99 was 8.776/10.325 ms and OpenGL world p95/p99 was 7.238/7.818 ms.
Average drawn chunks, batches, draw calls, and triangles were 28.38,
1,532.71, 362.56, and 167,707. Requested, reuploaded, and reused chunks
nevertheless remained effectively identical to idle at
34.0/0.716/33.284 per frame. That narrow result still supports animated
object reuploads being independent of camera movement, but maximum-distance
idle and camera phases are required before ranking heavy-view draw cost.

The maximum-distance pair in
`session-20260728-213204-1286508`, checkpoint `cbfe7ece3b`, removes that
configuration ambiguity. Both `dense-wide-idle` and `dense-wide-camera`
recorded zoom setting `900` (the allowed maximum), effective zoom `2400`,
40-tile draw distance, 28-tile fog start, and fog enabled. The owner considered
any visible stutter negligible. Wide idle ran for 153.0 seconds: OpenGL render
p95/p99 was 9.535/11.539 ms, OpenGL world was 7.207/8.440 ms, process use was
0.928 cores, and Java-thread allocation was 871.16 MiB/s. Wide camera motion
ran for 80.4 seconds: OpenGL render was 9.364/11.277 ms, OpenGL world was
7.161/7.568 ms, process use was 0.893 cores, and allocation was
865.99 MiB/s.

Compared with default-view `dense-idle`, maximum-distance idle did not
materially increase resident OpenGL work: upload remained about 1.47 ms p95,
the renderer still requested 34 chunks and reuploaded about 0.72
animated-object chunks per frame, and OpenGL world p95 was slightly lower.
Instead, client-side scene p95 rose from 1.592 to 3.316 ms, culling p95 from
0.356 to 0.859 ms, client-loop CPU from 0.150 to 0.231 cores, client-loop
allocation from 341.72 to 604.97 MiB/s, and total sampled allocation from
653.16 to 871.16 MiB/s. Wide camera motion was marginally cheaper than wide
idle because it culled more of the resident scene; there is no measured
rotation-specific penalty. This ranks allocation-heavy scene preparation and
culling ahead of GPU/world drawing for the next profile, while the steady
animated-object reupload remains an independent secondary target.

The opt-in JFR run
`session-20260728-214546-1289288` / `jfr-wide-idle` then captured 107.0
seconds at the same verified maximum camera state. JFR's weighted allocation
samples agreed with ordinary telemetry: 95,590.70 MiB was attributed across
the phase, while telemetry measured 890.70 MiB/s. The client loop owned 69.87%
of sampled bytes and the OpenGL presenter 30.12%. Six newly allocated
per-pixel arrays in `Renderer3DDepthFrame` accounted for 50,700.33 MiB, or
53.04% of the total. The next broad group contains reflection-bound OpenGL
calls that allocate boxed primitives and argument arrays; material-name
classification, sprite clip masks, 2D command snapshots, shadow inventory,
and texture-signature collections are smaller independently measurable
sources. The first optimization experiment therefore recycles the six depth
arrays through the presentation-frame lifetime. This preserves the immutable
depth-frame view until the presenter finishes or drops that frame while
testing the largest allocation source in isolation.

The first depth-buffer reuse attempt,
`session-20260728-215534-1310310` / `depth-pool-wide`, was correctly rejected
despite passing visual review. Allocation increased to 926.14 MiB/s and
process use to 0.981 cores. A code audit found that the bounded pool could fill
with smaller login-screen buffers and then discard every larger in-world
buffer. This is an important negative result: bounding a pool is insufficient
unless its retention policy preserves the capacities needed by the steady
workload.

The corrected run,
`session-20260728-220021-1312009` / `depth-pool-wide-r2`, retains the three
largest released buffers while acquiring the smallest adequate one. It ran
for 131.9 seconds at the same verified maximum camera configuration with no
reported visual issue. Total allocation fell from the original wide-idle
baseline's 871.16 to 341.32 MiB/s (-60.8%), and client-loop allocation fell
from 604.97 to 118.92 MiB/s (-80.3%). Collections fell from 4.20 to 2.08 per
second and sampled GC pause share from 0.95% to 0.40%. Process use also fell
from 0.928 to 0.824 cores. GL render p95/p99 improved from 9.535/11.539 to
9.251/10.894 ms.

This was a stronger geometry workload rather than an exact scene match:
requested chunks increased from 34 to 40, draw calls from 397.48 to 465,
drawn triangles from 157,531 to 181,602, and resident triangles from 209,162
to 229,928. Therefore the frame-time comparison is directional rather than a
strict isolated benchmark. The allocation and GC result is nevertheless
strong evidence for acceptance because the corrected run removed roughly
530 MiB/s while processing more geometry. The implementation resets only the
active array range and releases storage on presented, dropped, disabled, and
failed frame paths; the pool remains bounded to three capacity-selected
entries.

## Controlled Workload Matrix

Every optimization comparison should use the same graphics preset, sliders,
roof/fog state, zoom, viewport, camera heading, and server/database. Record the
actual camera state rather than relying only on operator memory. Each scenario
gets a warm-up pass followed by at least two measured passes. F3/default zoom
and maximum supported zoom are separate rows: the former represents ordinary
play and the latter is the renderer stress case.

1. **Dense steady scene** — stand still for 90 seconds in a repeatable dense
   area with NPCs, scenery, walls, shadows, and animations.
2. **Dense camera motion** — rotate and zoom through a fixed 60-second sequence
   without crossing a world residency boundary.
3. **Boundary traversal** — walk the same short path back and forth across a
   resident-region boundary for six complete crossings.
4. **Hard relocation** — repeat a fixed teleport and layer-transition route
   three times, including a return to the original scene.
5. **Entity/effect pressure** — exercise a repeatable area or fixture with
   many visible actors, world sprites, projectiles, and UI overlays.

The initial controlled baseline should use Remaster defaults because that path
exercises the full accepted visual stack. Classic and Custom become separate
matrix rows only when a hypothesis concerns their different work.

## Metrics And Comparison Rules

Primary metrics:

- true per-frame p50/p95/p99/max for client draw, scene, OpenGL render,
  OpenGL world, chunk upload, chunk draw, world sprites, overlays, and swap;
- process CPU utilization and CPU time by important Java thread;
- allocated bytes/second by important Java thread, young/full GC frequency,
  pause time, post-GC old generation, and direct-buffer use;
- considered/drawn/culled chunks and batches, triangles by owner, draw calls,
  texture binds, uploads, deferrals, evictions, and shadow rebuilds;
- transition p50/p95/max by world-model, world-section, and client-region
  phase.

Secondary invariants:

- no new exceptions, 2D command drops, missing materials, or resident fallback;
- comparable geometry/material counts for the same view unless the experiment
  intentionally removes invisible work;
- visual review confirms terrain, walls, roofs, scenery, shadows, sprites,
  animations, and UI remain correct.

An optimization milestone is accepted when the targeted metric improves
repeatably without a meaningful regression in frame tails, transition tails,
memory, or visual invariants. A 10% repeatable improvement is a useful initial
signal, not a universal pass/fail threshold; small wins may still be valid
when they simplify ownership or remove known pathological tails.

## Current Hypotheses

These remain hypotheses until controlled measurements and profiles confirm
them:

1. Maximum-distance rendering increases client-side scene preparation,
   culling, and allocation substantially more than resident OpenGL drawing.
   JFR attributed 53.04% of stress-phase allocation to six per-frame
   `Renderer3DDepthFrame` arrays, and bounded lifetime-aware reuse reduced
   client-loop allocation by 80.3% in the accepted follow-up. Reflection-bound
   OpenGL calls now form the largest known broad allocation group and require
   a separately scoped profile/experiment.
2. A meaningful portion of `openGL.world` occurs outside the three existing
   sub-phases, potentially in visibility/material/shadow inventory or other
   per-frame preparation.
3. Resident draw submission may be CPU-bound, GPU-bound, or both. Wall-clock
   Java timing alone cannot distinguish submission overhead from driver/GPU
   waiting.
4. Per-frame temporary allocations drive frequent young collections. Depth
   storage reuse reduced collection frequency from 4.20 to 2.08 per second
   and pause share from 0.95% to 0.40%, confirming the relationship even
   though GC was not the leading source of visible stutter.
5. Region/layer changes can still create tail latency through synchronous
   world-product construction and buffer uploads, but should be optimized
   separately from steady rendering.

## Milestones

### Milestone 1: Measurement Foundation

- [x] Add true per-frame latency histograms or bounded samples for the primary
      timing stages and expose them in structured telemetry.
- [x] Add diagnostic-only process CPU and important-thread CPU/allocation
      deltas with explicit units and graceful fallback on unsupported JVMs.
- [x] Add named start/stop benchmark markers that are convenient during
      private visual testing, remain client-local, and require an explicitly
      enabled diagnostic session.
- [x] Record the OpenGL device/version/vendor in the session bundle.
- [x] Extend the offline analyzer to summarize named phases, true frame tails,
      allocation rates, CPU ownership, and transition distributions.
- [x] Add an optional JFR profiling launch path for hotspot and allocation
      stack attribution; do not make JFR part of normal diagnostic sessions.

Implementation checkpoint:

- Primary stages retain bounded raw nanosecond samples in memory and serialize
  them only at existing report/phase boundaries. The analyzer now distinguishes
  true per-frame distributions from percentiles of report-window averages.
- Diagnostic records include process CPU, Java-thread CPU, unattributed
  process CPU, Java-thread allocation rate, stable client-loop/OpenGL/world
  preload/AWT ownership, and the top eight CPU/allocation threads.
- `::pf s <name>` and `::pf e <name>` bracket a named workload;
  `::pf m <name>` records a point marker. These commands never reach the
  server, accept only a short sanitized label, and do nothing useful outside a
  renderer-diagnostics launch.
- The OpenGL device fields are promoted from console prose into the structured
  manifest and a typed event.
- `./scripts/run-client.sh --dev --renderer-diagnostics --no-frame-capture`
  runs low-overhead comparative diagnostics. Adding `--jfr` disables frame
  capture and writes a bounded `client-profile.jfr` beside the session logs.
- The JFR option is explicitly opt-in and bounded to 128 MiB / 30 minutes.
  It is for stack attribution after ordinary telemetry identifies a workload,
  not for every comparison run.

### Milestone 2: Current-Branch Baseline

- [ ] Run the controlled workload matrix without `Ctrl+F9`.
  - [x] Dense steady scene.
  - [x] Dense camera motion at a close/default-style view; useful for isolating
        animated chunk reuploads, but not accepted as the heavy-view row.
  - [x] Maximum-distance dense idle and camera motion.
  - [ ] Boundary traversal.
  - [ ] Hard relocation.
  - [ ] Entity/effect pressure.
- [ ] Run one visual capture after timing runs to prove parity separately.
- [x] Structure camera zoom, pitch, rotation, allowed range, first-person
      state, and effective draw/fog distance before collecting the remaining
      comparison rows.
- [x] Record a CPU/allocation profile for the maximum-distance dense steady
      workload.
- [ ] Record a CPU/allocation profile for the boundary workload.
- [ ] Rank actual hotspots by inclusive CPU, allocation rate, frame-tail
      correlation, and estimated payoff.

### Milestone 3: Focused Optimization Cycles

- [x] Complete the first focused cycle: bounded depth-frame storage reuse.
- [ ] Implement one evidence-backed change at a time.
- [ ] Run focused guards and compile the client.
- [ ] Repeat the affected workload with identical settings.
- [ ] Record accepted and rejected experiments in the ledger below.
- [ ] Create pushed checkpoints at useful accepted milestones while keeping
      this investigation active.

### Milestone 4: Broader Performance Matrix

- [ ] Establish minimum target hardware and repeat the accepted matrix there.
- [ ] Compare renderer-v2 with the retained legacy/fallback path only where
      the comparison remains behaviorally meaningful.
- [ ] Add Classic/Custom, roofs, fog, draw distance, and entity distance rows
      after real quality settings cull real work.
- [ ] Follow evidence into map loading, synchronization, networking, pathing,
      or server work only where renderer/client captures show a connection.

## Experiment Ledger

| Date | Revision | Workload | Change | Result | Decision |
| --- | --- | --- | --- | --- | --- |
| 2026-07-28 | `0c8cecafc` | evidence audit | Compared existing renderer-only and layered-map diagnostic sessions; identified missing true frame tails, CPU ownership, allocation rates, GPU timing, and benchmark phases. | Measurement gaps prevent safe hotspot attribution. | Build Milestone 1 before renderer behavior changes. |
| 2026-07-28 | `a0488f013` | diagnostic fixtures | Added bounded raw frame samples, process/thread CPU and allocations, named phases, structured GPU identity, analyzer support, and optional bounded JFR launch. | Client compiles and renderer diagnostic/analyzer/frame-capture/release-hotkey guards pass. | Collect a clean current-branch baseline. |
| 2026-07-28 | `a0488f013` | `dense-idle` | Controlled 109.5-second Remaster idle in dense Seers scene. | Visual pass; GL render p95/p99 9.952/10.901 ms, world 7.417/7.895 ms, 0.826 CPU cores, 653.16 MiB/s diagnostic allocation; 0.72 of 34 requested chunks reuploaded per frame from animated-object signatures. | Profile animated resident rebuilds, renderer allocation, and draw submission before changing behavior. |
| 2026-07-28 | `a0488f013` | `dense-camera` attempt 1 | Rotated camera for 271.8 seconds. | Markers captured, but the original 64 MiB structured-log budget had already stopped periodic telemetry; no valid frame/CPU comparison. | Increase the bounded full-session budget, expose truncation clearly, and repeat in a fresh session. |
| 2026-07-28 | `8b14d42c4` | `dense-camera-r2` | Controlled 91.3-second camera rotation in dense Seers. | Complete capture and visual pass, but the owner identified a substantially zoomed-in view and camera zoom was not structured. The phase is not a valid heavy-view comparison. Chunk request/reupload/reuse still remained 34.0/0.716/33.284 per frame, effectively identical to idle. | Retain only the narrow animated-reupload finding; add camera-state telemetry and collect maximum-distance idle/camera phases before boundary work. |
| 2026-07-28 | `cbfe7ece3` | camera-state diagnostic guard | Added structured current/allowed zoom, base/effective zoom, pitch, rotation, first-person, fog mode, and effective draw/fog distances, with stable per-phase analyzer summaries. | Client compiles and diagnostic runtime/analyzer guards pass. | Collect the maximum-distance stress baseline before any boundary workload. |
| 2026-07-28 | `cbfe7ece3` | `dense-wide-idle`, `dense-wide-camera` | Captured maximum-distance dense idle and camera motion with structured proof of zoom `900`, effective zoom `2400`, and 40-tile draw distance. | Visual pass with negligible stutter. OpenGL world p95 stayed near 7.2 ms, but wide idle client scene/cull p95 rose to 3.316/0.859 ms and client allocation to 604.97 MiB/s. Camera motion was not more expensive than idle. | Profile allocation stacks in maximum-distance idle before changing behavior; retain animated-object reuploads as a separate target. |
| 2026-07-28 | `d89c0cb66` | `jfr-wide-idle` | Captured a bounded 107.0-second JFR profile at verified maximum distance. | JFR and telemetry agree at roughly 891 MiB/s; 69.87% of sampled bytes belonged to the client loop, and six `Renderer3DDepthFrame` arrays alone accounted for 53.04% of all allocation. Reflection-bound OpenGL calls form the next broad allocation group. | Recycle depth arrays through completed/dropped presentation-frame lifetimes, then repeat the ordinary maximum-distance baseline before touching reflection bindings. |
| 2026-07-28 | depth-pool worktree, attempt 1 | `depth-pool-wide` | Reused depth arrays through a bounded three-entry pool. | Visual pass, but allocation regressed to 926.14 MiB/s and process use to 0.981 cores. The pool retained small login buffers and discarded larger world buffers once full. | Reject the measurement and correct capacity retention before checkpointing. |
| 2026-07-28 | depth-pool checkpoint | `depth-pool-wide-r2` | Acquire the smallest adequate depth buffer, retain the three largest released capacities, reset active ranges, and release on every presentation-frame exit path. | No reported visual issue. Against original wide idle, total allocation fell 60.8%, client-loop allocation 80.3%, GC frequency 50.6%, and process CPU 11.2%, despite 17.6% more requested chunks and 15.3% more drawn triangles. | Accept and checkpoint; use the remaining allocation profile to choose the next isolated target. |
