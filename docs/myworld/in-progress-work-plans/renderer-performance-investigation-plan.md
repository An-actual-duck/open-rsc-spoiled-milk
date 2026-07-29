# Renderer Performance Investigation Plan

Status: active investigation. Baseline instrumentation is implemented and
guard-tested; a controlled current-branch comparison is the next milestone.

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

Important current limitations:

- Reported analyzer p50/p95/p99 timings are percentiles of approximately
  five-second report-window averages, not percentiles of individual frames.
  They describe sustained load but can conceal short hitches.
- Lifetime and window maxima show that a hitch happened, but do not provide a
  useful per-frame distribution or enough correlation to attribute it.
- Runtime records do not yet contain process CPU utilization, per-thread CPU
  ownership, or general allocated bytes/rates. The current allocation counters
  cover only selected large image allocations.
- CPU-side OpenGL submission time is not separated from GPU completion/wait
  time. There are no GPU timer-query measurements.
- Broad `openGL.world` time contains work not fully represented by the current
  upload/projected-mesh/chunk-draw split.
- Sessions have login and capture boundaries but no general-purpose named
  benchmark phase marker.
- The manifest does not yet carry the OpenGL device string; it currently
  exists only in the console log.

These gaps make targeted diagnostic instrumentation the first implementation
milestone, before changing renderer behavior.

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

## Controlled Workload Matrix

Every optimization comparison should use the same graphics preset, sliders,
roof/fog state, zoom, viewport, camera heading, and server/database. Each
scenario gets a warm-up pass followed by at least two measured passes.

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

1. The expanded resident scene introduced by the layered loader increases
   per-frame chunk enumeration, triangle submission, and draw-call pressure.
2. A meaningful portion of `openGL.world` occurs outside the three existing
   sub-phases, potentially in visibility/material/shadow inventory or other
   per-frame preparation.
3. Resident draw submission may be CPU-bound, GPU-bound, or both. Wall-clock
   Java timing alone cannot distinguish submission overhead from driver/GPU
   waiting.
4. Per-frame temporary allocations may explain frequent young collections
   even though measured GC pause share is currently small.
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
- [ ] Run one visual capture after timing runs to prove parity separately.
- [ ] Record a CPU/allocation profile for the dense steady and boundary
      workloads.
- [ ] Rank actual hotspots by inclusive CPU, allocation rate, frame-tail
      correlation, and estimated payoff.

### Milestone 3: Focused Optimization Cycles

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
| 2026-07-28 | pending checkpoint | diagnostic fixtures | Added bounded raw frame samples, process/thread CPU and allocations, named phases, structured GPU identity, analyzer support, and optional bounded JFR launch. | Client compiles and renderer diagnostic/analyzer/frame-capture/release-hotkey guards pass. | Checkpoint the measurement foundation, then collect a clean current-branch baseline. |
