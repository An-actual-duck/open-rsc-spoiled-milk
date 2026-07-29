# Renderer Performance Investigation Plan

Status: active investigation. Baseline instrumentation is implemented and
guard-tested; controlled current-branch baseline collection and profile
attribution are underway, and the first twelve focused optimizations have been
accepted. The reduced steady-scene workload now needs a fresh profile before
selecting another implementation target.

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

The same JFR profile attributed another 7,215.69 MiB to allocations whose
first project frame was `LwjglBindings.glVertex3f` and 6,680.57 MiB to
`LwjglBindings.glColor4f`. Together those two reflection wrappers represented
14.54% of the original stress-phase allocation, primarily through argument
arrays and boxed primitives in the immediate-mode sky path. The second
experiment retained dynamic LWJGL discovery but used typed Java 8 method
handles for only these two calls. It did not change the call sites, submitted
vertices, colors, rendering order, or fallback boundary.

The first visual run,
`session-20260728-221126-1317175` / `gl-handles-wide`, passed owner review but
is not a performance result: the client had remained open overnight and its
bounded telemetry budget ended many hours before the named phase. The fresh
`session-20260729-104614-1372358` / `gl-handles-r2` phase captured 112.9
seconds with complete telemetry, the verified maximum camera configuration,
no renderer exception, and normal owner-observed behavior. Against the
accepted depth-pool run, total allocation fell from 341.32 to 221.59 MiB/s
(-35.1%), presenter allocation from 222.40 to 131.97 MiB/s (-40.7%), and
collection frequency from 2.08 to 1.36 per second. Process use was effectively
flat at 0.824 versus 0.830 cores. GL render p95/p99 was 9.047/10.760 ms and
world p95/p99 was 7.091/8.173 ms, with no frame-tail regression.

As with the depth-pool comparison, this is not an exact scene A/B: the method-
handle run returned to the original 34-chunk, 209,162-resident-triangle scene,
whereas the depth-pool run held 40 chunks and 229,928 triangles. The allocation
drop remains persuasive because it closely matches JFR's predicted removal
and is concentrated on the presenter thread that owns the changed wrappers.
Relative to the original same-geometry wide baseline, the two accepted changes
together reduced total allocation from 871.16 to 221.59 MiB/s (-74.6%), GC
frequency from 4.20 to 1.36 per second (-67.8%), and process use from 0.928 to
0.830 cores (-10.5%), while GL render p95/p99 improved from 9.535/11.539 to
9.047/10.760 ms.

The third experiment moved immutable renderer material-family and glow
classification from every resident-object frame to the existing game-object
assignment and wall-object construction boundaries. Static presentation
models already classified themselves at construction. This preserves
classification on initial materialization, array compaction, and animated
model replacement while eliminating repeated definition-string normalization
for every materialized model in every client frame.

`session-20260729-105544-1381138` / `matmeta` ran for 92.5 seconds with the
verified maximum camera configuration and passed owner visual review. Although
the camera details were not visible in the owner's F6 layout, the structured
phase records prove zoom setting `900`, effective zoom `2400`, 40-tile draw
distance, 28-tile fog start, and fog enabled. Material coverage remained
complete: 231,637 resident triangles, zero unclassified triangles, and
populated foliage, scenery, ore, and emissive families.

The best comparison is the earlier `depth-pool-wide-r2` phase rather than the
immediately preceding 34-chunk method-handle phase. Both 40-chunk phases
processed effectively identical work: 1.475 versus 1.476 chunk uploads per
frame, 181,602 versus 181,600 drawn triangles, and identical foliage
(`79,868`), scenery (`99,724`), and emissive (`3,536`) triangle counts. The
new phase had 231,637 resident triangles versus 229,928 and slightly more
batches and draw calls. Across those matched workloads, client-loop allocation
fell from 118.92 to 97.82 MiB/s (-17.7%) and client-loop CPU from 0.190 to
0.181 cores (-5.1%). Typed OpenGL dispatch accounts for the separate presenter
change between these revisions; the client-owned reduction aligns with the
material-classification code moved in this experiment. Total allocation fell
from 341.32 to 309.25 MiB/s (-9.4%) and process use from 0.824 to 0.813 cores.
GL render p95/p99 was 9.622/10.309 ms and world p95/p99 was 8.197/8.775 ms;
the mixed p95 movement and improved p99 do not indicate a meaningful tail
regression.

After those three changes, the fresh bounded JFR phase
`session-20260729-111830-1387005` / `currentjfr` re-ranked the reduced
workload rather than relying on the now-stale original profile. It captured
126.2 seconds at the verified maximum camera configuration with the original
34-chunk, 209,162-resident-triangle geometry and normal owner-observed
behavior. JFR attributed 25,350.17 MiB across the phase, agreeing with
telemetry's expected profiling-overhead rate of about 200 MiB/s. The OpenGL
presenter owned 65.21% of sampled bytes and the client loop 34.78%.

The remaining reflection-bound `LwjglBindings` methods formed the largest
group at 8,297.75 MiB (32.73% of all sampled allocation). Indoor-shadow flood
workspace construction followed at 5,752.58 MiB (22.69%), remastered sprite
key composition at 2,138.86 MiB (8.44%), world-face command construction at
1,260.44 MiB (4.97%), and sprite clip masks at 930.47 MiB (3.67%). A bounded
set of 16 per-frame state, buffer, pointer, and draw methods—17 handles because
`glBufferData` has float- and integer-buffer overloads—accounted for 96.75% of
the reflection group and 31.67% of all samples. Infrequent GLFW, initialization,
shader setup, and lifecycle reflection therefore remained unchanged while
only that measured hot group moved to typed Java 8 method handles.

`session-20260729-113024-1390901` contains an accidental `ghost` phase followed
by the intended 105.4-second `glhot` phase; only `glhot` is used for the
accepted comparison. It passed visual review and recorded the verified maximum
camera state with no exception. Against `gl-handles-r2`, it is an unusually
close scene match: both requested 34 chunks, held 209,162 resident triangles,
considered 2,202 batches, and reported identical material-family counts;
drawn triangles differed by less than 0.9%.

Across that comparison, presenter allocation fell from 131.97 to
85.02 MiB/s (-35.6%), total allocation from 221.59 to 154.56 MiB/s (-30.2%),
collection frequency from 1.355 to 0.379 per second (-72.0%), and sampled GC
pause share from 0.314% to 0.093%. Process use fell from 0.830 to 0.778 cores
(-6.3%) while presenter CPU remained effectively flat. Client allocation also
fell from 89.62 to 69.54 MiB/s; the one-time material-metadata change between
these two revisions owns that separate client-side reduction, while the
presenter-owned reduction aligns with the hot bindings changed here. GL render
p95/p99 improved from 9.047/10.760 to 8.995/9.609 ms, and world p95/p99 was
7.168/7.638 ms.

Relative to the original same-geometry maximum-distance baseline, the four
accepted cycles together reduced total allocation from 871.16 to
154.56 MiB/s (-82.3%), client-loop allocation from 604.97 to 69.54 MiB/s
(-88.5%), collection frequency from about 4.20 to 0.38 per second (-91.0%),
and process use from 0.928 to 0.778 cores (-16.2%). GL render p95/p99 improved
from 9.535/11.539 to 8.995/9.609 ms without an accepted visual tradeoff.

The fifth audit found that the largest remaining sampled allocation was not a
normal shadow rebuild. `OpenGLFramePresenter` asked
`RemasterShadowClassifier` for diagnostic inventory counts on every telemetry
frame, and that call reconstructed `RemasterShadowRoofCoverage` and its indoor
flood workspace even though `OpenGLWorldChunkRenderer` already cached the same
coverage by the stable shadow-world signature. The accepted change keeps
inventory scanning and every reported classification current, but passes it
the renderer-owned cached coverage. The optional visual shadow-inventory
overlay now shares the same cache as well. Normal non-diagnostic behavior and
shadow-mask construction are unchanged.

`session-20260729-114323-1397725` / `shadowcache` captured 99.4 seconds at the
verified maximum camera configuration and passed indoor/outdoor, roof-toggle,
terrain, object, and shadow review. It is an exact geometry comparison with
`glhot`: both phases requested 34 chunks, held 209,162 resident triangles, and
considered 2,202 batches; drawn triangles differed by 0.85%. The diagnostic
inventory also remained exact at 40,934 receiver triangles, 2,700 casters,
1,800 roofed receivers, 39,134 outdoor receivers, and zero unknown receivers.

Presenter allocation fell from 85.02 to 54.66 MiB/s (-35.7%), total allocation
from 154.56 to 123.78 MiB/s (-19.9%), and presenter CPU from 0.484 to 0.438
cores (-9.5%). Client allocation remained effectively flat at 69.54 versus
69.11 MiB/s, which isolates the result to the changed presenter path. GL
render p95/p99 improved from 8.995/9.609 to 8.289/9.090 ms and world p95/p99
from 7.168/7.638 to 6.406/6.814 ms. GC frequency is not used for this isolated
comparison because `shadowcache` was an opt-in JFR run near the beginning of a
fresh process while `glhot` followed an earlier warm phase.

JFR directly confirms the mechanism: the earlier reduced profile attributed
5,752.58 MiB (22.69%) to `RemasterShadowIndoorFlood`, while the new phase
sampled zero bytes through either indoor-flood or roof-coverage construction.
The new profile agrees with ordinary telemetry at 12,251.30 weighted MiB over
the phase. Its next largest first-project-frame groups are direct-overlay
coverage-mask construction (12.62%), world-face capture (11.14%), remastered
sprite-key composition (7.29%), glow-mask construction (4.56%), sprite clip
masks (4.54%), and composite character-sprite texture construction (3.99%).
These are now the ranked candidates for separate lifetime audits rather than
reasons to optimize the flood implementation itself.

Relative to the original same-geometry maximum-distance baseline, the five
accepted cycles have reduced total allocation from 871.16 to 123.78 MiB/s
(-85.8%), client-loop allocation from 604.97 to 69.11 MiB/s (-88.6%), and
process use from 0.928 to 0.770 cores (-17.0%). GL render p95/p99 improved from
9.535/11.539 to 8.289/9.090 ms with no accepted visual tradeoff.

The sixth audit found that direct-overlay clipping allocated one
`boolean[sourceWidth * sourceHeight]` on every active composite frame. The
mask is mutable scratch storage: it is built, consumed synchronously while
restoring scene sprites, and never escapes the single OpenGL presenter thread.
The accepted change retains one grow-only presenter-owned array and clears only
the active source-pixel range before reuse. It does not alter mask generation,
clipping tests, replay order, or any captured command.

`session-20260729-115209-1402717` / `overlaymask` captured 91.7 seconds at the
same verified maximum camera state and passed owner review of movement, UI
tabs, text, right-click menus, scene sprites, and overlay ordering. It again
requested 34 chunks, held 209,162 resident triangles, considered 2,202
batches, classified 40,934 shadow receivers and 2,700 casters, and reproduced
the earlier `glhot` drawn-triangle count within 0.001%. Sprite accounting
remained internally complete: all 233.80 captured commands per frame were
replayed, with no invisible or atlas-full drops; the small difference from
236.09 in `shadowcache` is changing entity count rather than lost work.

Presenter allocation fell from 54.66 to 26.55 MiB/s (-51.4%) and total
allocation from 123.78 to 94.47 MiB/s (-23.7%). Client allocation was
effectively flat at 69.11 versus 67.93 MiB/s. A 960x540 boolean mask at 60
frames per second predicts 29.66 MiB/s of allocation; the observed total
reduction was 29.30 MiB/s and the presenter-owned reduction was 28.12 MiB/s,
closely isolating the mechanism. Process and presenter CPU remained flat at
0.777 and 0.440 cores. GL render p95/p99 was 8.230/9.202 ms and world p95/p99
was 6.287/7.104 ms, with no meaningful tail regression.

Relative to the original same-geometry maximum-distance baseline, the six
accepted cycles have reduced total allocation from 871.16 to 94.47 MiB/s
(-89.2%), client-loop allocation from 604.97 to 67.93 MiB/s (-88.8%), and
process use from 0.928 to 0.777 cores (-16.2%). GL render p95/p99 improved from
9.535/11.539 to 8.230/9.202 ms without an accepted visual tradeoff. With the
coverage-mask group removed, world-face capture is the largest known
project-owned allocation target in the current maximum-distance workload.

The seventh audit traced world-face capture through both threads and every
consumer. Each visible projected face previously created a `FaceCommand`,
seven integer arrays, and two floating-point texture-coordinate arrays on
every client frame. The immutable snapshot must survive until the OpenGL
presenter finishes or drops its corresponding presentation frame, but none of
that storage escapes the existing frame-release boundary.

Checkpoint `10d951ee1` therefore retains up to three complete world-face
storages, preferring the largest steady-world capacities over smaller login
frames. Commands are pooled by exact vertex count through 64 vertices so their
public array lengths remain unchanged; larger unusual polygons keep the
allocation fallback. Reused commands reset coordinates, lighting, texture
coordinates, clipped geometry, draw order, kind counters, and the model/face
lookup. Presented, dropped, disabled, failed, null-image, and software-only
paths all release the storage. A focused executable fixture proves same-size
reuse, different-size separation, stale-state removal, idempotent release,
unmodifiable views, and the three-storage bound.

`session-20260729-120928-1409020` / `facepool` captured 101.4 seconds at the
same verified maximum camera state and passed owner review of movement,
near-wall clipping, textures, lighting, and entity occlusion. It is a close
comparison with `overlaymask`: both phases requested 34 chunks, held 209,162
resident triangles, and considered 2,202 batches. Projected face capture was
1.3% lower (356.64 versus 361.20 faces per frame) and sprite anchors were 1.8%
lower, small workload differences that do not explain the measured result.

Total allocation fell from 94.47 to 84.52 MiB/s (-10.5%) and client-loop
allocation from 67.92 to 60.07 MiB/s (-11.6%). Process use fell from 0.777 to
0.734 cores, while client and presenter use remained close at 0.201 and 0.416
cores. GL render p95/p99 was 7.914/8.583 ms and world p95/p99 was
6.386/6.881 ms, with no frame-tail regression. The remaining presenter
allocation difference is not assigned to this client-owned mechanism because
the two runs had slightly different entity counts.

Relative to the original same-geometry maximum-distance baseline, the seven
accepted cycles have reduced total allocation from 871.16 to 84.52 MiB/s
(-90.3%), client-loop allocation from 604.97 to 60.07 MiB/s (-90.1%), and
process use from 0.928 to 0.734 cores (-20.9%). GL render p95/p99 improved from
9.535/11.539 to 7.914/8.583 ms without an accepted visual tradeoff. The
earlier profile ranking is now stale after removing another measured group;
the next steady-scene action is a reduced-workload JFR pass before choosing
between residual face-map keys/nodes, sprite keys, glow masks, sprite clip
masks, or another allocation owner.

The follow-up JFR phase,
`session-20260729-121914-1413173` / `reducejfr`, captured 132.3 seconds on
checkpoint `bb0ec1618` at the same verified maximum camera configuration. The
shortened phase label was intentional only in the sense that labels are
arbitrary; it did not change or invalidate the capture. Visual behavior was
normal, geometry remained at 34 resident chunks, 209,162 triangles, and 2,202
batches, and there was no old-generation retention signal. Ordinary telemetry
reported 85.32 MiB/s of Java-thread allocation while JFR attributed 11,128.56
weighted MiB within the phase, or 84.12 MiB/s. That agreement makes the
reduced ranking suitable for selecting the next isolated change.

Repeated `RemasteredSpriteKey.compose` work is now the largest first-project-
frame allocation group at 2,222.07 MiB (19.97% of the phase). The next groups
are object-chunk vertex-array growth at 1,036.03 MiB (9.31%), sprite clip masks
at 794.54 MiB (7.14%), glow-mask construction at 692.55 MiB (6.22%),
residual world-face lookup-map entries at 347.26 MiB (3.12%), and
object-chunk triangle-array growth at 320.02 MiB (2.88%). The sprite-key group
is unusually well isolated: animation definitions account for 2,206.81 MiB
of it, UI/world sprite definitions for 15.26 MiB, and items for no sampled
bytes. Key normalization, two regular-expression matchers, substrings, and
string composition currently repeat for each selected animation layer on
each client frame even though the definition and frame number are stable.
The eighth experiment will therefore cache only animation-definition/frame
keys, retain mutation-safe invalidation for the definition's public name and
category, and leave catalog validation and the negligible item/sprite paths
unchanged.

The same profile also changes the CPU follow-up ranking. After excluding the
AWT event-wait thread, resident chunk access and texture-signature work lead:
`ChunkMesh.getVertexCoord` owns 1,872 samples,
`OpenGLWorldTextureCache.mixTextureSignature` 984,
`RemasterShadowRoofCoverage.classify` 628, and
`OpenGLWorldTextureCache.uploadReferencedTextures` 501. These are not bundled
with the animation-key experiment. Texture/chunk signature scanning should be
audited separately after the measured allocation change, because altering
both would make CPU and allocation results impossible to attribute.

Checkpoint `c8105af6e` implements the eighth experiment with a resolver-owned
identity cache. It creates a normalized key only once for each animation
definition/frame pair, starts with the legacy 18-frame capacity, grows only
when a real larger frame is requested, and retains no frame above 255. The
uncached fallback preserves unusual larger offsets. Because legacy animation
name and category fields are public, each lookup verifies both source values
and replaces all cached frames for that definition if either changes. Item and
UI/world-sprite key paths remain unchanged. An executable Java 8 fixture proves
stable key identity, capacity growth, the 255-frame retention bound, oversized
fallback, name/category invalidation, null/invalid-key caching, and definition
identity separation.

`session-20260729-123817-1421299` / `animkey` captured 107.3 seconds at the
same verified maximum camera state and passed owner review of player and NPC
sprites and animations. It retained the exact 34 requested chunks, 209,162
resident triangles, and 2,202 considered batches. Against `facepool`, client-
loop allocation fell from 60.07 to 49.70 MiB/s (-17.3%), while client CPU
remained flat at 0.201 versus 0.199 cores. Total allocation fell from 84.52 to
76.28 MiB/s (-9.8%).

The aggregate presenter comparison is not attributed to this client-only
change. The new phase captured 6.5% more sprite commands per frame (242.33
versus 227.58), and presenter allocation/CPU rose from 24.44 MiB/s and 0.416
cores to 26.57 MiB/s and 0.465 cores. GL render p95/p99 consequently moved
from 7.914/8.583 to 8.732/9.742 ms and world p95/p99 from 6.386/6.881 to
6.750/7.272 ms. Drawn triangles were 0.6% lower and projected faces 0.9%
lower, so this is a close resident-geometry comparison but not an exact entity
workload. The changed client thread improved in its targeted metric without a
CPU or visual regression; the unrelated presenter variance remains explicit
rather than being claimed as either a benefit or cost of key caching.

Relative to the original same-geometry maximum-distance baseline, the eight
accepted cycles have reduced total allocation from 871.16 to 76.28 MiB/s
(-91.2%) and client-loop allocation from 604.97 to 49.70 MiB/s (-91.8%).
The reduced JFR ranking now places object-chunk mesh array growth, sprite clip
masks, and glow masks ahead of residual face-map and 2D-command allocation.
Choose the next experiment only after auditing those lifetimes and the
separate texture-signature CPU opportunity.

Checkpoint `5d314c8b2` implements the ninth experiment by retaining the three
fixed-size red, green, and blue glow-mask accumulation arrays on the
presenter-owned `RemasterGlowMaskBuilder`. Each rebuild clears the arrays
before accumulation; they never escape the synchronous builder, while mask
signature checks, pixel encoding, and texture-upload behavior remain
unchanged. A focused Java 8 fixture proves array identity reuse, red/blue/green
channel reset across changing emitters, cached-mask behavior, clear-mask
behavior, and null/empty-frame handling.

`session-20260729-130549-1434940` / `glowscratch` captured 108.3 seconds and
passed owner review of the active fires available in the control area. The
complete phase intentionally covered zoom settings from 210 through 900 and
later moved into a different 40-chunk scene, so its aggregate is retained as
visual and robustness evidence rather than presented as a homogeneous
performance comparison.

The phase began with a 16.9-second fixed-zoom comparison window at zoom 900,
effective zoom 2,400, 40-tile draw distance, 34 requested chunks, 2,202
considered batches, and workload counts within 2.5% of `animkey`. In that
window, presenter allocation fell from 26.57 to 14.42 MiB/s (-45.7%), total
allocation from 76.28 to 62.75 MiB/s (-17.7%), and client allocation remained
close at 48.33 versus 49.70 MiB/s. Presenter CPU fell from 0.465 to 0.430
cores, while client CPU remained close at 0.203 versus 0.199 cores. GL
render p95/p99 improved from 8.732/9.742 to 7.976/8.572 ms and world
p95/p99 from 6.750/7.272 to 6.511/6.934 ms. This is a shorter measurement
than the accepted 90-second controls, but the large thread-local allocation
change agrees with the eliminated three-array allocation path and the
executable lifecycle guard. Accept the isolated change; use a full fixed-view
phase if a later cumulative baseline needs a longer ninth-cycle endpoint.

The tenth-candidate audit splits the reduced JFR sprite-clip group into
761.47 MiB of per-frame boolean-mask arrays and 33.08 MiB of per-row integer
arrays. Those arrays are created on the client loop, remain exclusively owned
by their `Renderer3DDepthFrame`, and cease to be observable at the same
presented/dropped-frame release boundary already used by the depth-array pool.
This gives the 794.54 MiB group a complete and already exercised lifetime,
unlike object-chunk mesh construction, whose boxed growth and immutable output
conversion need a broader builder rewrite.

Checkpoint `6b8cd1fd0` implements the tenth experiment by pooling only
sprite-clip mask and row arrays. It uses a separate synchronized three-entry
pool, selects the smallest storage that satisfies both pixel and row capacity,
retains the three largest combined capacities, clears every active mask pixel
and row bound before use, and allocates no storage for full/empty fallback
masks. Construction failures return acquired storage, while depth-frame
release remains idempotent. A focused Java 8 fixture covers array reuse, stale
pixel and row removal, different-size selection, the retention bound,
empty-frame behavior, and idempotent release.

`session-20260729-131938-1443031` / `clippool` captured a homogeneous 94.2
seconds at the verified maximum camera state and passed owner review of entity
occlusion around walls, trees, and scenery. Against the initial fixed-view
`glowscratch` window, it retained the same 34 requested chunks and 2,202
considered batches; world faces, drawn triangles, and sprite commands were all
within 1%. Client-loop allocation fell from 48.33 to 27.21 MiB/s (-43.7%) and
total allocation from 62.75 to 41.89 MiB/s (-33.2%), while presenter
allocation stayed close at 14.68 versus 14.42 MiB/s. Client CPU stayed close
at 0.199 versus 0.203 cores. The earlier glow window was only 16.9 seconds, so
these isolated percentages are supported by the exact thread ownership and
matched workload but are not treated as a new precision threshold.

The longer `animkey` phase provides a second cumulative control with the same
34 chunks, 209,162 resident triangles, and 2,202 batches. Across the glow and
clip cycles together, total allocation fell from 76.28 to 41.89 MiB/s
(-45.1%), client-loop allocation from 49.70 to 27.21 MiB/s (-45.3%), and
presenter allocation from 26.57 to 14.68 MiB/s (-44.7%). Process use remained
close at 0.789 versus 0.780 cores, client/presenter use at 0.199/0.463 versus
0.199/0.465 cores, GL render p95/p99 at 8.747/9.419 versus 8.732/9.742 ms,
and world p95/p99 at 6.738/7.344 versus 6.750/7.272 ms. There is no CPU or
frame-tail regression.

Relative to the original same-geometry maximum-distance baseline, ten accepted
cycles have reduced total allocation from 871.16 to 41.89 MiB/s (-95.2%) and
client-loop allocation from 604.97 to 27.21 MiB/s (-95.5%). GL render
p95/p99 improved from 9.535/11.539 to 8.747/9.419 ms without an accepted
visual tradeoff.

The fresh reduced profile,
`session-20260729-133105-1444995` / `jfrll`, captured 98.0 seconds at the
verified maximum camera state on checkpoint `e7216af51`. The mistyped phase
label is arbitrary and does not affect the capture. It retained the exact 34
chunks, 209,162 resident triangles, and 2,202 batches. JFR attributed
4,034.53 weighted MiB within the phase, or 41.16 MiB/s, against telemetry's
41.40 MiB/s. The client loop owned 2,646.79 MiB and the presenter 1,384.45
MiB, so the profile is suitable for ranking the now much smaller workload.

Allocation is no longer dominated by one simple array lifetime. Explicit
object-chunk builder stacks account for 681.71 MiB (16.9%); the five-frame
JFR stack limit separately leaves 598.10 MiB of client-loop `ArrayList`
growth without its calling project frame, much of which may belong to the
same boxed builders but is not assigned without proof. Remaining groups
include unconverted reflection-bound OpenGL wrappers at approximately 339
MiB, composite character-sprite texture construction at 165.05 MiB, and
several independent 2D-command, texture-data, face-lookup, and chunk-key
groups. Primitive object-chunk storage remains the leading allocation audit,
but its builder/output conversion is broader than a one-field change.

CPU attribution gives a safer and larger next target. Of 4,987 samples with a
project frame, 3,279 belonged to the OpenGL presenter and 1,708 to the client
loop. `RemasterGlowMaskBounds.from` alone owned 1,534 samples: 30.8% of all
project samples and 46.8% of presenter samples. It rescans every vertex of
every resident chunk before computing a signature and checking the glow-mask
cache. Texture-signature/upload work follows at 1,091 presenter samples and
roof-coverage classification at 463. Because chunk meshes already own cloned
coordinate arrays and presentation rebases are immutable copies, exact
per-chunk bounds can be computed once and aggregated into each lightweight
chunk frame without approximating geometry.

Checkpoint `bb5e6ba5c` implements the eleventh experiment by recording exact
X/Z vertex extrema when each immutable `ChunkMesh` is constructed, translating
those extrema with the same checked arithmetic as presentation rebasing, and
aggregating them while constructing `Renderer3DWorldChunkFrame`. Glow bounds
then read the exact aggregate in constant time while retaining emitter-radius
padding, signature, cache, and texture behavior. Empty geometry remains
distinct from a real zero-coordinate bound, and emitter-only frames retain
their existing result. A focused Java 8 fixture proves multi-chunk
aggregation, source-preserving rebase translation, empty-frame semantics,
exact glow-mask coordinates, and emitter-only behavior.

`session-20260729-134659-1453503` / `bounds11` captured 89.8 seconds at the
verified maximum camera state and passed owner review of active fire glows.
It is an exceptionally close comparison with `clippool`: both phases
requested 34 chunks and considered 2,202 batches, projected faces differed by
less than 0.01%, and the new phase drew 0.5% more triangles and captured 1.7%
more sprite commands.

Presenter CPU fell from 0.463 to 0.286 cores (-38.1%) and process use from
0.789 to 0.605 cores (-23.3%), while client CPU stayed flat at 0.196 versus
0.199 cores. GL render p95/p99 improved from 8.747/9.419 to
5.618/6.409 ms (-35.8%/-32.0%), and world p95/p99 from 6.738/7.344 to
3.684/4.142 ms (-45.3%/-43.6%). Allocation remained close at 42.54 versus
41.89 MiB/s, as expected from a CPU-only metadata change. This confirms that
the eliminated full-vertex scan—not GPU waiting or changed geometry—owned the
measured presenter time.

Relative to the original same-geometry maximum-distance baseline, eleven
accepted cycles have reduced total allocation from 871.16 to 42.54 MiB/s
(-95.1%), client-loop allocation from 604.97 to 26.75 MiB/s (-95.6%), process
use from 0.928 to 0.605 cores (-34.8%), and GL render p95/p99 from
9.535/11.539 to 5.618/6.409 ms (-41.1%/-44.5%), without an accepted visual
tradeoff.

The next presenter CPU audit resolves the 1,091 texture-cache samples from the
same `jfrll` profile. `OpenGLWorldTextureCache.mixTextureSignature` owned 681
samples and `uploadReferencedTextures` another 410, or 33.3% of sampled
presenter CPU. Before checking its one-entry cache, the presenter traversed
every triangle in all resident chunks to rediscover unique primary and legacy
transparent-fallback texture IDs and mix their texture signatures. A cache
miss then repeated the full triangle traversal to upload those same unique
textures. The upload entry point is also reached during both chunk upload and
diagnostic drawing.

The twelfth experiment keeps all invalidation conservative while moving that
discovery to immutable ownership boundaries. Each `Renderer3DFrame` snapshots
the texture catalog array and computes one catalog signature from the already
immutable texture-data signatures. Each immutable chunk computes its sorted
unique primary/fallback texture IDs when it clones the triangle arrays, and
each lightweight chunk frame aggregates those small reference sets and mixes
their chunk identities. The presenter cache check is therefore constant-time,
and an actual upload iterates only precomputed unique IDs rather than roughly
209,000 resident triangles. A change to any catalog texture invalidates the
cache even when that texture is not currently referenced, which is more
conservative than the old behavior and preserves animated fire/water updates.
The legacy transparent sentinel remains non-uploadable while its real fallback
texture remains discoverable.

The client compiles and the full renderer guardrail suite passes. Focused Java
8 coverage proves source-array snapshotting, stable and changing catalog
signatures, sorted cross-chunk deduplication, transparent fallback capture,
stable world signatures, and cache invalidation for both catalog and chunk
changes.

The first private `texrefs12` phase on checkpoint `ba9827bba` ran for 114.7
seconds and passed visual review of fires, water, other textures, and movement,
but it is not a timing result. That client had remained open for about four
hours and exhausted the bounded bulk-telemetry budget roughly three hours
before the phase began. Its console retained exact control geometry and
directionally lower world-window averages, but not the phase CPU, allocation,
or raw frame distributions required for acceptance.

The fresh `session-20260729-182039-1488463` / `texrefs12r2` phase then captured
89.3 seconds with complete telemetry at the verified maximum camera state.
It is an exceptionally close comparison with `bounds11`: both held 34 chunks,
209,162 resident triangles, and 2,202 considered batches. The new phase drew
0.4% more triangles, captured 0.2% more projected faces, and differed by less
than 0.1% in sprite commands.

Presenter CPU fell from 0.286 to 0.148 cores (-48.4%) and process use from
0.605 to 0.464 cores (-23.2%), while client CPU remained flat at 0.195 cores.
GL render p95/p99 fell from 5.618/6.409 to 3.195/3.573 ms
(-43.1%/-44.3%), and world p95/p99 from 3.684/4.142 to 1.753/1.940 ms
(-52.4%/-53.2%). Total allocation remained effectively flat at 41.59 versus
42.54 MiB/s; presenter allocation was 10.0% lower and client allocation 2.4%
higher. No exception, material loss, or accepted visual regression occurred.
This matches the eliminated presenter scans and accepts the twelfth cycle.

Relative to the original same-geometry maximum-distance baseline, twelve
accepted cycles have reduced total allocation from 871.16 to 41.59 MiB/s
(-95.2%), client-loop allocation from 604.97 to 27.38 MiB/s (-95.5%), process
use from 0.928 to 0.464 cores (-50.0%), and GL render p95/p99 from
9.535/11.539 to 3.195/3.573 ms (-66.5%/-69.0%), without an accepted visual
tradeoff. The tenth-cycle JFR ranking is now stale; re-profile this reduced
endpoint before choosing between primitive object-chunk construction,
remaining OpenGL wrappers, composite sprite texture work, or another target.

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
   client-loop allocation by 80.3% in the accepted follow-up. Typed handles
   for the two hottest reflection-bound OpenGL calls then reduced presenter
   allocation by another 40.7%. Assigning immutable renderer material metadata
   once then reduced client-loop allocation by 17.7% in a matched 40-chunk
   comparison. A fresh profile then attributed 32.73% of remaining allocation
   to the reflection-bound per-frame state/buffer/draw group; typed handles for
   that measured set reduced presenter allocation by 35.6%. Reusing the
   renderer's stable roof-coverage cache for diagnostic inventory then removed
   all sampled indoor-flood construction and reduced presenter allocation by
   another 35.7%. Reusing direct-overlay coverage scratch storage then reduced
   presenter allocation by another 51.4%. Bounded world-face command and array
   reuse then reduced client-loop allocation by another 11.6%. The reduced
   profile attributed 19.97% of all remaining allocation to repeated
   remastered sprite-key composition, with 99.3% of that group owned by
   animation definition/frame lookups. Caching those stable keys then reduced
   client-loop allocation by 17.3%. Reusing presenter-owned glow-mask scratch
   arrays then reduced presenter allocation by 45.7% in the comparable
   fixed-view window and removed the measured three-array rebuild path.
   Sprite-clip arrays have the same proven frame-release lifetime as the
   accepted depth pool and account for 794.54 MiB in the reduced profile, so
   bounded clip-storage reuse became the tenth accepted experiment and reduced
   client-loop allocation by 43.7% in its matched comparison. Keep
   object-chunk array growth separate.
2. The tenth-cycle JFR profile attributes 46.8% of sampled presenter CPU to
   repeated glow-bound vertex scans before cache lookup. Exact immutable
   chunk/frame bounds removed that scan without changing cache or visual
   semantics, reducing presenter CPU by 38.1% and GL/world p95 by
   35.8%/45.3% in the accepted eleventh experiment. Texture signature and
   upload-reference scans then accounted for 33.3% of sampled presenter CPU.
   Replacing those full-triangle scans with immutable catalog and chunk-
   reference metadata reduced presenter CPU another 48.4% and GL/world p95
   another 43.1%/52.4% in the accepted twelfth experiment. The remaining CPU
   ranking now requires a fresh profile; primitive object-chunk storage
   remains the leading attributable allocation audit from the prior profile.
3. A meaningful portion of `openGL.world` occurs outside the three existing
   sub-phases, potentially in visibility/material/shadow inventory or other
   per-frame preparation.
4. Resident draw submission may be CPU-bound, GPU-bound, or both. Wall-clock
   Java timing alone cannot distinguish submission overhead from driver/GPU
   waiting.
5. Per-frame temporary allocations drive frequent young collections. Depth
   storage reuse reduced collection frequency from 4.20 to 2.08 per second
   and pause share from 0.95% to 0.40%, confirming the relationship even
   though GC was not the leading source of visible stutter.
6. Region/layer changes can still create tail latency through synchronous
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
- [x] Complete the second focused cycle: allocation-free typed dispatch for
      the two hottest dynamically loaded OpenGL calls.
- [x] Complete the third focused cycle: classify immutable game-object and
      wall-object renderer material metadata once at model preparation.
- [x] Complete the fourth focused cycle: typed dispatch for the measured
      per-frame OpenGL state, buffer, pointer, and draw group.
- [x] Complete the fifth focused cycle: reuse renderer-owned roof coverage for
      per-frame diagnostic shadow inventory instead of rebuilding its indoor
      flood workspace.
- [x] Complete the sixth focused cycle: reuse the presenter-owned direct-overlay
      coverage mask across frames.
- [x] Complete the seventh focused cycle: reuse bounded world-face command and
      vertex-array storage through the presentation-frame release boundary.
- [x] Complete the eighth focused cycle: cache stable remastered animation
      definition/frame keys while preserving source-mutation invalidation.
- [x] Complete the ninth focused cycle: reuse presenter-owned glow-mask color
      accumulation arrays with complete per-rebuild channel reset.
- [x] Complete the tenth focused cycle: reuse bounded sprite-clip mask and row
      storage through the depth-frame release boundary.
- [x] Complete the eleventh focused cycle: replace repeated glow-bound
      full-vertex scans with exact immutable chunk/frame bounds.
- [x] Complete the twelfth focused cycle: replace repeated texture-signature
      and upload-reference triangle scans with immutable catalog and chunk
      reference metadata.
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
| 2026-07-28 | `58198f2b5` | `depth-pool-wide-r2` | Acquire the smallest adequate depth buffer, retain the three largest released capacities, reset active ranges, and release on every presentation-frame exit path. | No reported visual issue. Against original wide idle, total allocation fell 60.8%, client-loop allocation 80.3%, GC frequency 50.6%, and process CPU 11.2%, despite 17.6% more requested chunks and 15.3% more drawn triangles. | Accept and checkpoint; use the remaining allocation profile to choose the next isolated target. |
| 2026-07-29 | method-handle worktree, attempt 1 | `gl-handles-wide` | Route `glVertex3f` and `glColor4f` through typed Java 8 method handles while retaining dynamic LWJGL loading. | Visual pass, but the client had remained open overnight and exhausted bounded telemetry before the phase; no valid performance samples exist. | Retain the visual result only and repeat in a fresh session. |
| 2026-07-29 | `af1ff41b3` | `gl-handles-r2` | Repeat the two-call typed-dispatch experiment in a fresh maximum-distance session. | Complete capture and visual pass. Total allocation fell another 35.1%, presenter allocation 40.7%, and GC frequency 34.8%; CPU remained effectively flat and frame tails did not regress. | Accept and checkpoint; re-rank the remaining profile before expanding typed dispatch. |
| 2026-07-29 | `848a6d01d` | `matmeta` | Assign immutable material-family and glow metadata when game/wall models are prepared instead of reclassifying every resident-object frame. | Visual pass and complete maximum-distance capture. Against the nearly identical 40-chunk depth-pool workload, client allocation fell 17.7%, client CPU 5.1%, total allocation 9.4%, and process use 1.3%; zero resident triangles were unclassified. | Accept and checkpoint; profile the reduced client loop before selecting another temporary-allocation target. |
| 2026-07-29 | `0d545c359` | `currentjfr` | Re-profile the reduced 34-chunk maximum-distance workload after the first three accepted cycles. | Complete JFR and visual pass. Remaining reflection wrappers owned 32.73% of allocation, indoor-shadow flood workspaces 22.69%, sprite keys 8.44%, world-face commands 4.97%, and sprite clip masks 3.67%. | Convert only the 16 measured per-frame OpenGL methods that account for 96.75% of the reflection group. |
| 2026-07-29 | `98e3c278d` | `glhot` | Route the measured per-frame state, buffer, pointer, and draw wrappers through typed Java 8 method handles while retaining dynamic LWJGL discovery. | Complete maximum-distance capture and visual pass against matching 34-chunk geometry. Presenter allocation fell 35.6%, total allocation 30.2%, GC frequency 72.0%, and process use 6.3%; GL frame tails did not regress. | Accept and checkpoint; inspect indoor-shadow flood lifetime and reuse boundaries as the next ranked target. |
| 2026-07-29 | `cac788267` | `shadowcache` | Reuse renderer-owned roof coverage for diagnostic shadow inventory and the optional inventory overlay instead of reconstructing the indoor flood workspace per frame. | Exact 34-chunk geometry and shadow classifications; visual pass. Presenter allocation fell 35.7%, total allocation 19.9%, presenter CPU 9.5%, and world p95 10.6%. JFR sampled zero indoor-flood or roof-coverage construction bytes versus 5,752.58 MiB previously. | Accept and checkpoint; audit direct-overlay coverage-mask lifetime before changing its representation. |
| 2026-07-29 | `fae0cf296` | `overlaymask` | Reuse one grow-only presenter-owned direct-overlay coverage array, clearing the active source range before each synchronous scene-restore pass. | Exact maximum-distance geometry and complete sprite replay accounting; visual pass. Presenter allocation fell 51.4% and total allocation 23.7%, matching the 29.66 MiB/s predicted cost of a 960x540 boolean mask at 60 FPS. CPU and frame tails remained flat. | Accept and checkpoint; audit world-face capture lifetime and consumers as the next ranked target. |
| 2026-07-29 | `10d951ee1` | `facepool` | Reuse bounded world-face commands and their coordinate, light, texture, and clipped arrays through the presentation-frame release boundary. | Exact 34-chunk/209,162-triangle/2,202-batch workload and visual pass. Total allocation fell 10.5%, client-loop allocation 11.6%, and process use 5.5%; GL/world tails did not regress. Focused runtime coverage proves reuse, complete state reset, exact vertex-size separation, idempotent lifecycle, and a three-storage bound. | Accept and checkpoint; re-profile the now-reduced maximum-distance workload before selecting another allocation target. |
| 2026-07-29 | `bb0ec1618` | `reducejfr` | Re-profile the seven-cycle reduced maximum-distance workload and split the remaining sprite-key group by definition type. | Complete 132.3-second JFR and visual pass at exact 34-chunk/209,162-triangle/2,202-batch geometry. JFR measured 84.12 MiB/s against telemetry's 85.32 MiB/s. Sprite-key composition leads at 19.97% of all allocation, and animation definitions own 99.3% of that group. Resident chunk access and texture-signature scanning lead project-thread CPU samples. | Cache only animation definition/frame keys as the next isolated allocation experiment; retain texture-signature CPU work as a later separate audit. |
| 2026-07-29 | `c8105af6e` | `animkey` | Cache normalized remastered animation keys by definition identity and frame, with bounded growth, oversized fallback, and public source-field invalidation. | Visual pass and exact 34-chunk/209,162-resident-triangle/2,202-batch scene. Client allocation fell 17.3% and client CPU stayed flat; total allocation fell 9.8%. The phase had 6.5% more sprite commands, so higher presenter CPU/allocation and GL tails are recorded as an entity-workload difference rather than attributed to the client-only change. Focused Java 8 coverage proves key reuse and all cache bounds/invalidation paths. | Accept and checkpoint; audit the newly leading mesh-growth, clip-mask, glow-mask, and texture-signature candidates separately before selecting the ninth experiment. |
| 2026-07-29 | `5d314c8b2` | `glowscratch` | Reuse three presenter-owned fixed-size glow-mask accumulation arrays, clearing every color channel before each rebuild. | Fires remained visually stable. The complete 108.3-second phase varied zoom and scene, but its initial matched 16.9-second maximum-distance window retained 34 chunks and 2,202 batches while presenter allocation fell 45.7%, total allocation 17.7%, and GL/world p95 and p99 improved. Focused Java 8 coverage proves storage reuse and absence of stale channel state. | Accept and checkpoint the documented result; audit sprite-clip storage lifetime before choosing the tenth experiment. |
| 2026-07-29 | `6b8cd1fd0` | `clippool` | Reuse bounded sprite-clip mask and row arrays through the depth-frame release boundary, with complete active-range reset and full/empty fallback preservation. | Homogeneous 94.2-second maximum-distance phase and visual occlusion pass. Against the matched post-glow window, client allocation fell 43.7%, total allocation 33.2%, and presenter allocation stayed flat; exact runtime coverage proves reuse, stale-state clearing, capacity selection, bounded retention, empty frames, and idempotent release. The longer cumulative control shows CPU and GL/world tails remained flat. | Accept and checkpoint the documented result; re-rank the remaining reduced workload before another implementation. |
| 2026-07-29 | `e7216af51` | `jfrll` | Re-profile the ten-cycle maximum-distance workload and rank remaining CPU and allocation stacks. | Complete 98.0-second JFR phase at exact control geometry. JFR measured 41.16 MiB/s against telemetry's 41.40 MiB/s. Glow-bound full-vertex scans own 46.8% of sampled presenter CPU; explicit object-chunk builders lead attributable allocation at 16.9%, with additional stack-truncated list growth kept separate. | Precompute exact immutable chunk/frame bounds as the eleventh experiment; retain texture-signature, object-builder, and remaining GL-wrapper work as separate candidates. |
| 2026-07-29 | `bb5e6ba5c` | `bounds11` | Precompute exact immutable X/Z bounds for chunk meshes and aggregate frames, then consume them in glow-mask lookup instead of rescanning every resident vertex. | Exact 89.8-second maximum-distance comparison and visual fire-glow pass. Presenter CPU fell 38.1%, process use 23.3%, GL p95/p99 35.8%/32.0%, and world p95/p99 45.3%/43.6%; allocation stayed flat. Focused Java 8 coverage proves exact aggregation, rebase translation, empty frames, unchanged glow coordinates, and emitter-only behavior. | Accept and checkpoint; audit texture-signature scans separately before another CPU change. |
| 2026-07-29 | `ba9827bba` | `texrefs12` | Snapshot frame texture-catalog signatures and precompute sorted unique chunk/frame texture references, including transparent fallbacks, so cache checks and uploads no longer traverse every resident triangle. | Fires, water, other textures, and movement passed visual review, but the four-hour-old client had exhausted structured bulk telemetry roughly three hours before the 114.7-second phase. Console geometry was exact, but phase CPU/allocation/tails were unavailable. | Retain the visual result only and repeat in a fresh client. |
| 2026-07-29 | `ba9827bba` | `texrefs12r2` | Repeat the immutable texture-reference experiment in a fresh maximum-distance session. | Complete 89.3-second capture and prior visual pass at exact 34-chunk/209,162-triangle/2,202-batch control geometry. Presenter CPU fell 48.4%, process use 23.2%, GL p95/p99 43.1%/44.3%, and world p95/p99 52.4%/53.2%; allocation stayed flat. Focused runtime coverage proves catalog snapshotting, fallback capture, deduplication, and exact invalidation. | Accept and checkpoint; re-profile the reduced endpoint before selecting cycle 13. |
