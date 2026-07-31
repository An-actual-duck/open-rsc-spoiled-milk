# World-Boundary Loading Diagnostics and Decision Plan

Status: diagnostics implementation and evidence collection in progress

Branch: `refactor/boundary-loading-diagnostics`

Scope: desktop client world-boundary transitions, especially renderer-v2/OpenGL

Gameplay changes in this phase: none

## Goal

Identify which work causes the remaining visible frame drop at a world boundary
before selecting another optimization. The transition must continue to:

- retain the last complete scene while the replacement is incomplete;
- activate terrain, static scenery, players, and NPCs atomically;
- avoid the previously observed wrong-area flash;
- keep deterministic terrain variation;
- preserve classic/software rendering and all existing graphics settings.

This phase adds bounded diagnostics, defines repeatable cases, gathers evidence,
and compares solution families. It does not change loading cadence, scene
ownership, upload policy, or visual behavior.

## Transition Architecture

The current native-layered transition crosses several threads and ownership
boundaries:

```text
server context/stage packets
    |
    +-- predicted terrain stages 4/5
    |      decode -> CPU prebuild worker -> ready prediction
    |
    `-- atomic context packet 157
           decode -> accept scope -> publish prediction -> begin activation
                                      |
                                      v
client loadNextRegion
    dematerialize -> World.loadSections -> rebase/materialize entities
                                      |
                                      v
World.loadSections
    reset -> active/upper terrain products -> minimap/walls/roofs
          -> chunk frame -> symmetric compose -> preload
                                      |
                                      v
scene baseline + resident scenery construction
    input assembly -> cache reuse/miss -> worker mesh construction
                                      |
                                      v
OpenGL presenter
    GPU chunk upload -> projected/resident draw -> shadow mask
    -> overlays -> buffer swap
                                      |
                                      v
atomic presentation latch
    stable complete frame -> release retained prior frame
```

The important distinction is that a small synchronous client region load does
not prove the visible transition is cheap. Large GPU uploads, a shadow rebuild,
or the first presented replacement frame can occur after `loadNextRegion`
returns.

## Measurement Gaps in the Previous Instrumentation

The existing renderer diagnostics were useful but could not provide one
causal record:

- CPU world, client-region, scenery, GPU, shadow, and frame events had separate
  identifiers or no common identifier.
- The old boundary trace wrote and flushed up to 90 individual JSON events per
  transition while the run was active. That output could perturb the workload.
- World-model `terrainNanos` and `wallNanos` combined terrain publication,
  collision initialization, and minimap work.
- Prediction build time, lead time, and whether the predicted product actually
  matched the activated center were not joined to the transition.
- Allocation, GC, named-thread CPU, lock wait, and archive reads were only
  visible in broad periodic windows.
- A Ctrl+F8 visual marker had nearby movement and frame data, but no direct
  boundary trace identifier.

## Bounded Diagnostic Design

`--boundary-diagnostics` enables renderer diagnostics, forces frame capture
off, and enables one correlated transition collector. Ordinary launches are
unchanged.

Each atomic context gets a privacy-safe trace ID. The trace retains data in
memory and writes one `boundary.transition-summary` after the presentation
latch releases and the configured settling frames have passed.

### Captured ownership

| Area | Measurements |
| --- | --- |
| Packet | opcode, byte count, total handling time, context header/body decode, state acceptance, scope application, ready receipt |
| Prediction | target center, decode/build/queue latency, cache result, triangles, reused/built cells, lead time, match at activation |
| Terrain | world-section phases, world-product phases, collision clear, terrain publication, wall emission/publication, roof publication |
| Minimap | clear and native-raster publication |
| Scenery | input assembly, cache hits/misses, wall-clock mesh time, worker CPU time, parallel worker count |
| Residency/GPU | requested/uploaded/reused/deferred chunks, uploaded bytes, chunk-upload time, projected draw, resident draw |
| Shadows | mask build, upload, reuse/preparation/request state |
| Atomic transition | player/static receipts, completion time, presentation samples, stability, release time |
| Frames | OpenGL render and interval; subphases; desktop frame commit/present timing |
| Runtime | heap delta, GC count/time, process CPU, client/OpenGL/preload/object-worker CPU and allocation, blocked/waited counters |
| External pressure | named cache/log-lock waits, actual tile-archive reads, runtime-log writes, and diagnostic-output flushes |
| Scene shape | crossing kind, first/return visit, plane change, entity counts, chunk/triangle counts |

Ctrl+F8 remains the manual “stutter observed now” marker. When a boundary trace
is active, its event now includes `boundary.traceId` and
`boundary.contextSequence`.

### Bounds and disabled overhead

- At most 256 transitions are accepted per client process by default.
- One active trace retains at most 192 spans, 64 phase keys, 96 OpenGL frames,
  96 presentation frames, and eight pre-transition OpenGL frames.
- Prediction and visited-center indexes are fixed-size access-order maps of 32
  and 64 entries.
- Additional transitions and spans are counted as suppressed/dropped.
- The existing diagnostic event-log byte limit remains a second output bound.
- Runtime management-bean paths are prewarmed at session start and sampled only
  at trace start/end.
- The old 90-event boundary-frame stream is suppressed while the bounded suite
  is active.
- The disabled path allocates no trace rings, maps, or runtime samplers.
- No account name, chat text, credential, host address, or network address is
  recorded.

The analyzer reports p50/p95/p99/max for raw boundary frame timings, phase
distributions, case dimensions, and the five worst correlated traces.

## Baseline Evidence from the Last Visual Session

This is a baseline from
`output/renderer-diagnostics/session-20260730-202915-412829`, not a final
finding from the new suite.

- There were 18 world-section transitions and 17 client-region transitions.
- World-section p50/p95/max was 3.900/253.053/253.053 ms. The 253.053 ms sample
  was the initial center `(50,50)` construction.
- Client-region p50/p95/max was 6.462/9.074/9.074 ms.
- Repeated cardinal crossings between centers `(50,50)` and `(51,50)` had
  client-region work of 4.536–9.074 ms.
- The first tracked GPU frame uploaded 117 chunks and 199,509,120 bytes in
  300.376 ms.
- The first ordinary adjacent crossing uploaded 95 chunks, reused eight, and
  uploaded 176,198,400 bytes in 120.662 ms.
- A relocation to the `(59,49)` area uploaded 124 chunks and 242,264,160 bytes
  in 179.962 ms.
- Its next adjacent transition uploaded 116 chunks, reused eight, and uploaded
  216,196,560 bytes in 137.793 ms.
- Once those views were resident, first-frame transition uploads fell to
  0.219–0.672 ms, with 4–12 uploads and 99–117 reused chunks.

This strongly motivates measuring GPU residency and upload work, but it does
not yet prove that every remaining one-frame dip is caused by upload. The old
trace did not join upload, CPU construction, GC, shadow, swap, and atomic
release into one bounded record.

## Repeatable Private Test Matrix

Run only against a private server. Use a normal diagnostic run first; reserve a
short JFR run for a remaining unexplained spike because profiling itself can
change timings.

Launch:

```bash
./scripts/run-client.sh --dev --boundary-diagnostics
```

Bracket each case with `::pf s <name>` and `::pf e <name>`. Press Ctrl+F8 as
soon as a visible hitch is observed. Each repeated case should include two
unmeasured warm-up crossings followed by at least ten measured crossings; 20
is preferred when practical.

| Case | Procedure | What it distinguishes |
| --- | --- | --- |
| Cold | Fresh private client/login, then first boundary crossing | archive/cache initialization, CPU mesh construction, first GPU allocation |
| Warm cardinal | Alternate across one east/west or north/south boundary | stable per-crossing cost after caches are populated |
| Return | A → B → A repeatedly | whether the prior center survives CPU and GPU residency |
| Diagonal/corner | Cross both section axes at a corner | larger ring churn and prediction coverage |
| Dense scenery | Repeat across the highest-scene-count candidate found in the trace | scenery input/mesh/cache and static GPU bytes |
| Multi-level | Change floor/plane, cross or activate a new center, then return | scope invalidation, roof/upper-plane products, cache identity |

The analyzer derives `cardinal`, `diagonal`, `level`, and `relocation` from the
actual deltas. It separately groups first versus return visits, prediction
matches, and scope changes. “Dense” should be selected from recorded scenery,
wall, and triangle counts instead of assuming a location is dense by name.

After closing the client:

```bash
python3 scripts/analyze-renderer-session.py \
  output/renderer-diagnostics/<session> --strict
```

The generated `ai-summary.md` is the decision record. Retain the raw
`events.jsonl` until the optimization direction is selected.

## Evidence Decision Gates

Use the following gates rather than optimizing whichever code is most visible:

1. If the worst frame and chunk upload overlap, uploaded bytes are high, and
   CPU phases are already below budget, prioritize residency/upload work.
2. If scenery cache misses or worker mesh time overlap the spike, improve
   prediction or cache identity before touching GPU scheduling.
3. If terrain/minimap/wall publication dominates the client thread, separate
   or prepare those products before activation.
4. If a shadow build/upload dominates, repair its cache key or prepare the
   replacement shadow product without weakening atomic presentation.
5. If GC time or allocation deltas overlap the spike, pool or retain the
   measured large products; do not guess based on heap size alone.
6. If named lock wait is material, reduce that specific ownership conflict.
   General parallelization is not justified without a measured lock owner.
7. If disk reads occur on warm/return crossings, repair preload/residency. Disk
   work on a truly cold run is a separate startup concern.
8. If the frame spike remains unattributed after these phases, run the same
   short case with `--jfr` and inspect driver/native/swap time.

## Candidate Solution Families

No option below is approved for implementation until the new case matrix is
captured.

### 1. Bounded GPU residency across adjacent centers

Retain the exact reusable chunks for the previous and predicted neighboring
centers, with a byte/chunk budget and deterministic eviction.

- Best when return crossings show low CPU cost but repeat large uploads.
- Low conceptual risk because it extends existing resident ownership.
- Main tradeoff is GPU memory; eviction must not reintroduce the wrong-area
  flash.

### 2. Predicted GPU preparation

Use the already acknowledged predicted radius-two products to queue OpenGL
uploads before atomic activation.

- Best when CPU prediction is ready early but activation still uploads most
  chunks.
- Upload commands must remain on the OpenGL context thread.
- A stale prediction must be discardable without becoming drawable.

### 3. Budgeted upload behind the retained frame

Keep presenting the last complete frame while replacement uploads are spread
over multiple frames, then release only when the complete new frame is stable.

- Best when one large upload burst is the dominant spike.
- Preserves atomic correctness if incomplete replacement chunks never leak.
- Adds transition latency even while improving frame pacing.

### 4. Strip/delta uploads

Represent an adjacent transition as retained overlap plus only the entering
outer strip/corner.

- Potentially the lowest steady-state bandwidth.
- Higher identity and invalidation complexity than bounded retention.
- Requires special care for scenery, terrain edits, roofs, and plane changes.

### 5. Reduce or repack resident geometry

Reduce vertex/index bytes, duplicate attributes, or upload conversions.

- Appropriate only if bytes remain high even when residency behaves correctly.
- Broad renderer risk; it should follow evidence from role-specific byte totals.

### 6. CPU product/preload changes

Extend prediction, cache prepared renderer chunks, or change scenery worker
ownership.

- Appropriate only for measured CPU construction or lock contention.
- Moving work earlier is not useful if the actual hitch is a later GPU upload.

### 7. Shadow/minimap double buffering

Prepare a new shadow/minimap product and swap it with the atomic scene.

- Appropriate only if their measured build/upload phases dominate.
- Must retain deterministic terrain variation and avoid partial old/new masks.

## Current Recommendation

Complete the new private case matrix before choosing an implementation.
The prior baseline makes bounded GPU residency or predicted GPU preparation the
leading candidates, but the choice between them depends on:

- whether the remaining visible dip still coincides with large upload bytes;
- whether the predicted product is ready with useful lead time;
- whether return crossings already reuse the intended chunks;
- whether dense and multi-level cases fail for a different reason.

Checkpoint the diagnostic implementation and this plan before any substantive
loader or renderer change. Visually compare the selected prototype on the same
private cases, and do not publish or deploy based on automated results alone.
