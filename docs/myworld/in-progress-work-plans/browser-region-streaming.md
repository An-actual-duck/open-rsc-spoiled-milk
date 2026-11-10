# Browser region presentation and loading

Focused worker: `fix/browser-region-streaming`. This changes browser presentation,
not terrain authority, protocol delivery, map data, collision, picking, or server state.

## Findings

The existing production static chunk keys already preserve immutable storage across
center/rebase changes. Changing those keys or retaining obsolete source geometry is
unnecessary. The browser previously restarted its initial 7–30 complete-frame GPU
warmup for every region/viewport epoch and drew incomplete newly uploaded regions
behind a modal cover. The core already has a coherent presentation latch while its
native terrain/static-scene activation completes. Hard transitions can nevertheless
produce an ordinary Java “Loading... Please wait” software frame.

The bridge retains its existing 32,768 discretionary vertex upload budget and 224MiB
chunk cache budget. Actual source packing is unchanged. Newly required resources
continue preparing incrementally; immutable overlapping/return resources remain
reusable under the ordinary LRU policy.

## Change and input safety

- Initial login keeps the full loading cover, completion checks, fence and bounded
  cadence warmup. Logout resets that initial-readiness policy.
- A core context transition issues an optional platform presentation notice before
  its latch. Same scope/level with a validated position change at most eight tiles
  per axis is classified as ordinary movement, using long arithmetic. Other cases
  conservatively gate keyboard input too. The default platform hook does nothing;
  the existing native/core latch and one-argument activation hook are preserved.
- Once an authoritative complete world has actually been submitted, the ordinary
  coverage browser bridge does not present missing new chunks, transition repacks,
  or non-modal Java loading images. It uploads resources and replays the previous
  final FULL texture until the complete current scene can be presented. The
  retained texture is necessary with `preserveDrawingBuffer=false`; a no-draw
  pause alone is insufficient as an explicit framebuffer-content guarantee.
- The browser compositor explicitly retains opaque fullscreen UI output as FULL
  too, resetting its fade BASE at the opaque boundary. This prevents an immediate
  post-modal transition from replaying an invalid/deleted cache. Standalone overlay
  default semantics are unchanged; the browser opt-in stays within the existing
  combined 32MiB texture bound. Consecutive opaque frames reuse the allocation.
- FULL replay uses existing bounded textures/shader. It makes no new allocation,
  copy, readback, fade, token advance or BASE change. Upload validation precedes
  replay, and the world filtering sampler is removed. No throughput timer or
  fresh-world-frame count is recorded for a held image.
- After first readiness, transitions show a small transparent status badge rather
  than a full-screen cover. Stale image pointer actions are gated; key releases and
  browser shortcuts pass. Ordinary movement keeps keyboard input; hard/unknown
  transitions gate gameplay keys. Fullscreen game interfaces still present normally.
- The first complete authoritative new source frame publishes immediately after
  its real draw, without repeating login warmup. Error/context-loss remains a full
  error boundary. Width/height, actual textures, sprites and lighting completion
  remain checked. No extra GPU fence is needed for each ordinary submitted frame.

This preserves a coherent screenshot during preparation. It does **not** eliminate
all core loading time, continue animating old world geometry, prefetch new source
regions, or prove seamless streaming under every network/device condition.
The private marker-UI benchmark lacks a complete FULL texture, so it retains its
previous Java bridge behavior; ordinary browser coverage is enabled by default.

## Bounded diagnostics

`gpu.stats.worldStreamSamples` retains the last 32 completed post-login transitions:
notice-to-complete elapsed milliseconds, source epoch, required/ready chunks,
ordinary classification, held frames, source declarations, and native upload-count
and byte deltas. Notice-free fallback timing starts at the first observed source
epoch and may omit its first upload batch. `heldWorldFrames` counts actual FULL
replays separately from real world frames. Existing core BoundaryLoadingDiagnostics
and RenderTelemetry transition phases remain available for separating source load
cost from browser preparation. This adds no per-face clocks or per-frame Java calls.

## CPU validation

- Full client build passes.
- Actual production packing/cache/source-publication fixture: 73 checks. Seven
  12,000-vertex chunks require four initial submissions; moving to a seven-chunk
  view with four shared chunks takes two submissions, and returning uses one.
  Only ten unique resources upload. All final packed float/index/material bytes
  match production packing. Intermediate transition submissions are held; no
  incomplete region is published. Host fixture elapsed time is recorded as a CPU
  fixture measurement, not a browser/game timing or FPS result.
- Real-frame region/level classification and hold policy: 15 checks.
- Existing animated/storage/material/topology/budget failure suite passes all six
  LRU/active and animation-policy configurations.
- Readiness/input tests cover initial-only warmup, source incompleteness, early
  transition notice, retained holds, ordinary versus hard key gates, viewport,
  modal UI, logout/relogin, sticky errors and cleanup.
- Retained/overlay instrumented GL suites prove FULL-versus-BASE selection, no
  allocation/copy on hold, arbitrary caller state restoration, failed-copy
  invalidation, reset/resize/owner generations and disposal.
- Stream upload validation tests include held replay: queued faults reject before
  any FULL/world/sky/UI draw or new timer; context/error failures remain sticky.
- Existing owned/scoped state oracle passes 42 frames, 570 draw/copy/clear records,
  2,777 scoped state queries versus zero owned hot-frame queries.
- Native atomic activation (4), transition/minimap latch acceptance (12), protocol
  client authority (3), and real packet recovery/quarantine/LRU (2) tests pass.
  Source-shape assertion and minimal recovery mock were updated for the additive
  two-argument presentation hook; the original hook behavior is unchanged.
- Frontend contract and canonical layout checks pass.

## Manager hardware result and remaining acceptance

The maintained retained hardware oracle was extended from 233 to 249 cases. For
all 16 native fade-count corpora, it additionally captures a nonblank original,
clears the destination black and checks FULL replay byte-exactly against the
original, without additional capture. Existing native fade/RGBA8/state/resource
cases remain. Prepare-only source/hash contract passes:

```
node tests/myworld/run-browser-retained-hardware.mjs --run-hardware \
  output/tests/browser-retained-frame/hardware/region-streaming-full-v1
```

The manager executed the fresh `region-streaming-full-firefox-v2` stage in
Firefox154/WebGL2: all249 cases passed, zero pixel mismatches. The browser reported
`Radeon R9 200 Series, or similar` (Firefox's sanitized AMD/Radeon identity); this
is not evidence of the exact physical model or any FPS result. Evidence:
`/home/justin/Core-Framework/output/diagnostics/browser-motion-transitions/retained-firefox-3/result.json`.
The initial stage's Chrome-specific ANGLE/OpenGL identity guard failed before any
case in Firefox; its evidence was preserved and the fresh stage accepts the
reported AMD/Radeon identity while rejecting software renderers. The Chrome
runner retains its own strict ANGLE/OpenGL/SystemInfo checks. Pixel assertions
were not relaxed.

The isolated result proves FULL replay pixels, fade/resize/state/resource behavior;
it does not exercise the later browser opaque-UI caller opt-in or a whole-game
region transition. Only the manager may execute hardware tests or private gameplay
probes. Required
whole-game follow-up: initial login; ordinary region crossings and return; near and
far teleport; signed-layer changes; active modal interfaces; input releases, error,
context loss and Stop/restart. Verify no Java loading-image/partial-world flash,
actual source completion, correct current-scene picks after unblocking, and bounded
resource counts. Firefox-specific wall-clock load latency remains to be measured.
No public host, player data or owner browser was touched by this worker.

During a pending logical resolution change, the frontend changes CSS/layout only;
the GPU canvas backing width/height stays at the captured size until the next
actual present. Holds replay that original backing size. A native-boundary test
covers new declared dimensions with old backing; retained module dimensions are
still strictly validated. The complete next source frame resizes backing storage
and resets overlay ownership before its draw.
