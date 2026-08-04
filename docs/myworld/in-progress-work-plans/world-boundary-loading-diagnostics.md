# World-Boundary Loading Diagnostics and Decision Plan

Status: active; bounded terrain transport and incremental frame receive are
implemented, with repeated visual stress validation still pending

Branch: `refactor/boundary-loading-diagnostics-followup`

Scope: desktop client world-boundary transitions, especially renderer-v2/OpenGL

Gameplay changes in this phase: one fail-closed upper-floor object-reach fix;
terrain-stage transport now pages otherwise unrepresentable payloads, and the
client incrementally receives partial frames without exposing them before
completion. Terrain contents and scene activation semantics are unchanged.

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
| Packet | opcode, byte count, total handling time, context header/body decode, state acceptance, scope application, ready receipt; scene-baseline protocol, server tick, context sequence, page category/index/total, record count, and arrival offset |
| Prediction | target center, decode/build/queue latency, cache result, triangles, reused/built cells, lead time, match at activation |
| Terrain | world-section phases, world-product phases, collision clear, terrain publication, wall emission/publication, roof publication |
| Minimap | clear and native-raster publication |
| Scenery | input assembly, cache hits/misses, wall-clock mesh time, worker CPU time, parallel worker count |
| Residency/GPU | requested/uploaded/reused/deferred chunks, uploaded bytes, chunk-upload time, projected draw, resident draw |
| Shadows | mask build, upload, reuse/preparation/request state |
| Atomic transition | player/static receipt offsets, completion time, presentation-product readiness, retained-frame attempts, stability samples, release time |
| Frames | bounded nearby client-loop/update/draw samples; OpenGL render and interval; presenter wait and submission-queue time; subphases; desktop frame commit/present timing |
| Runtime | heap delta, GC count/time, process CPU, client/OpenGL/preload/object-worker CPU and allocation, blocked/waited counters |
| External pressure | named cache/log-lock waits, actual tile-archive reads, runtime-log writes, and diagnostic-output flushes |
| Scene shape | crossing kind, first/return visit, plane change, entity counts, chunk/triangle counts |

Ctrl+F8 remains the manual “stutter observed now” marker. When a boundary trace
is active, its event now includes `boundary.traceId` and
`boundary.contextSequence`.

### Bounds and disabled overhead

- At most 256 transitions are accepted per client process by default.
- One active trace retains at most 192 spans, 64 phase keys, 96 OpenGL frames,
  96 client-loop samples, 96 presentation frames, and eight pre-transition
  OpenGL/client-loop samples.
- Scene-baseline packet details use a separate 32-entry aligned buffer so a low
  span limit cannot hide which page or server tick completed an activation.
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
matches, scope changes, and the enclosing named `::pf` test phase. It excludes
the bounded pre-transition rings and non-settled/superseded traces from timing
distributions. “Dense” should be selected from recorded scenery, wall, and
triangle counts instead of assuming a location is dense by name.

After closing the client:

```bash
python3 scripts/analyze-renderer-session.py \
  output/renderer-diagnostics/<session> --strict \
  --server-log server/logs/<private-current-log>
```

Repeat `--server-log` for a rotated private log when a run spans rotation;
plain-text and `.gz` rotations are both supported. If a server log is supplied
but missing, the analyzer fails instead of silently omitting it. It retains at
most the latest 4,096 recognized delivery records and parses only fixed
timestamp, mode, page, byte, queue/channel-pressure, and context fields;
unrelated account, address, credential, or chat text is never copied into the
report.

The generated `ai-summary.md` is the decision record. Retain the raw
`events.jsonl` until the optimization direction is selected.

### Current matrix status

- Cold, warm cardinal, return, and dense-scenery evidence has been captured.
- The attempted corner route under `boundary-diagonal` crossed the Y and X
  boundaries on separate steps, producing cardinal traces rather than a valid
  simultaneous diagonal trace. It must be rerun after resumption with a route
  that actually changes both section bases in one transition.
- The first resumed upstairs check found an unrelated ladder-interaction
  failure: directional object reach constructed a logical location from an
  already packed upper-floor Y coordinate plus the explicit level, causing an
  exact-projection exception on every ladder interaction tick.
  `Mob.isObjectReachClear` now derives the checked tile through the mob's
  current-level projection and fails closed on an invalid tile. This
  correctness repair is checkpointed as `d1d3acfe4`.
- The resumed client/server pair was then discovered to have been launched
  with `scripts/private-server/server.sh`. That convenience launcher uses
  `myworld.conf` without the production native-layered runtime flags. The
  public server enables the replacement package, residency, readiness,
  prediction, symmetric residency, atomic activation, and synchronized scene
  baseline. The player's visible world edges exposed the mismatch. Therefore
  all performance observations from
  `session-20260802-151808-2782853`--including its apparent 44--64 ms floor
  changes--are invalid for comparison with the public loader.
- Client class and source equality against current main was confirmed, but
  that comparison used the same incorrectly configured private server. It
  proves the topic branch did not alter those client classes; it does not prove
  loader parity because the effective loader is negotiated by the server.
- A fresh private package has now been generated and validated with
  `scripts/run-server.sh --layered-production`. The replacement-profile server
  and diagnostics client session `session-20260802-154618-2791426` are the
  corrected basis for renewed visual validation and capture.
- The player visually confirmed that the corrected production-equivalent
  profile no longer exposed world edges. The client negotiated the native
  authoritative protocol-v8 path, and the private server process was verified
  to have the replacement package, residency, readiness, prediction, symmetric
  residency, atomic activation, and synchronized-baseline flags enabled.
- A marked four-circuit ground/first/second-floor capture is now complete on
  that corrected profile. Its findings are recorded below.
- True diagonal crossings were captured around the corner shared by native
  centers `(11,11)` and `(12,12)`. Repetition exposed a deterministic packet
  framing failure after the connection-local terrain residency cache churned;
  that correctness defect must be fixed before completing the timing matrix.

## Diagonal Stress Crash and Exact Transport Cause

Session
`output/renderer-diagnostics/session-20260803-220210-3973356` contains several
successful true diagonal crossings followed by a client game crash during the
next prediction. This was not a JVM, GPU, native-driver, or out-of-memory
crash. The maintained client's game-crash handler caught:

```text
IllegalArgumentException: Native terrain source SHA-256 is unterminated
client.LD(dummy,3508,154)
```

Opcode 154 is `SEND_LAYERED_TERRAIN_STAGE`. The failing protocol-v4 predicted
symmetric halo was generated for center `(12,12)` after repeated returns to
`(11,11)`. The server and client each retain 64 content identities in matching
access-order caches. A radius-two receipt touches full inner terrain and
visual-only outer terrain; its following structure receipt touches a separate
set of structural identities. Repeated diagonal movement therefore evicted
enough `(12,12)` identities that the next prediction needed 19 payload-bearing
chunks and only six references.

Replaying the exact accepted context/stage sequence against the production
package and the same 64-entry LRU policy gives an opcode-154 payload of exactly
69,043 bytes. Including the custom transport's two-byte length and opcode, the
frame is 69,046 bytes. The custom-client branch of
`RSCProtocolEncoderMain` writes that total to an unsigned two-byte field without
a bounds check. It wraps as follows:

```text
69,046 mod 65,536 = 3,510
3,510 - the two length bytes = 3,508 bytes delivered to PacketHandler
```

That is the exact `3508` reported by the client. The decoder received the
beginning of a valid stage followed by a hard truncation in a later chunk's
SHA field. Earlier crossings succeeded because their receipts were smaller;
the first few cache cycles are part of the reproduction, not random crash
timing.

The current encoder also lacks a general guard against any non-raw custom
packet exceeding the 65,535-byte frame ceiling. Merely rejecting this terrain
packet would avoid corrupting the TCP stream but would leave prediction
readiness permanently incomplete. The terrain protocol therefore needs a
bounded multi-packet representation with client-side transactional assembly,
plus a general encoder fail-closed guard so another oversized packet cannot be
silently truncated.

Recommended correctness milestone before further performance experiments:

1. Add an explicit maximum custom-frame length and make the common encoder
   reject oversized frames rather than narrowing them.
2. Page a serialized terrain stage into independently frame-safe packets with
   stage/context identity, page index/count, declared total size, and bounded
   client assembly.
3. Decode and commit the terrain residency transaction only after every page
   of the exact stage has arrived. Missing, duplicate, stale, out-of-order, or
   superseded pages must not mutate active terrain or send readiness.
4. Cover the exact 69,043-byte cache-churn fixture, maximum page bounds,
   normal small stages, and atomic publication in deterministic tests.
5. Repeat the same diagonal loop visually before resuming hitch comparisons.

Implementation is now present for local validation. Stages at or below 65,532
payload bytes retain their existing one-packet representation. Larger stages
are serialized once and split into 24,000-byte fragments. Each opcode-154 page
carries stage/context identity, total length, page index/count, and the complete
stage CRC32. Client storage is bounded to one 1 MiB transaction, and terrain
decode, resident-cache commit, prebuild, and readiness acknowledgement occur
only after exact ordered assembly and checksum verification. A shared encoder
guard now refuses every packet length that its selected legacy framing format
cannot represent instead of narrowing it. Automated coverage includes the
observed 69,043-byte receipt and stale, duplicate, out-of-order, corrupt,
wrong-context, and superseded page sequences. Repeated private diagonal visual
validation remains required before this milestone is considered complete.

## Dense Login Receive-Window Deadlock

The first private validation after terrain-stage paging repeatedly accepted the
login and built the native terrain, but remained on `Loading world` until the
client's 1,000-poll network watchdog disconnected roughly 17 seconds later.
The scene-baseline trace consistently stopped after presentation-scenery page
1 of 6, even though the server's page cursors reached all 11 data pages.

Bounded encoder and frame-reader diagnostics isolated the ownership boundary:

- the server encoded every baseline page, including presentation-scenery pages
  2--5 and the presentation-wall page;
- the client remained correctly frame-aligned and read a valid 6,221-byte
  frame header for presentation-scenery page 2;
- only 2,805 of its 6,219 opcode-plus-payload bytes were then available;
- `ss -tinmp` showed the client's 128 KiB receive allocation full and the
  server receive-window-limited with 36--45 KiB still unsent;
- the old reader would not consume any payload bytes until
  `InputStream.available()` reported the entire 6,219 bytes, so the unread
  2,805 bytes kept the receive window closed while the server needed an open
  window to send the remaining 3,414 bytes.

This was a deterministic TCP flow-control deadlock, not baseline loss, bad
framing, terrain decode, renderer work, or server page-cursor behavior. The
client now copies every currently available portion into its bounded packet
buffer, resets the no-progress watchdog when bytes arrive, and invokes the
packet handler only after the declared frame is complete. The regression
harness delivers one frame over three partial chunks, verifies no early packet
publication or premature timeout, and verifies alignment of the following
frame.

In diagnostic session
`output/renderer-diagnostics/session-20260803-231456-52833`, the exact stalled
frame completed as 2,805 plus 3,414 bytes immediately after this change. All
remaining baseline pages arrived, atomic activation reported player/static
receipt `ok/ok` in 399 ms, the structure stage completed, and both socket queues
returned to a healthy state. The client reached the playable world without a
watchdog disconnect. A user-visible repeat and the repeated diagonal stress
case remain required before declaring the transport milestone complete.

## Corrected-Profile Multi-Level Evidence

Session
`output/renderer-diagnostics/session-20260802-154618-2791426` contains the
production-equivalent validation and the marked `boundary-multilevel` phase
from 19:54:44 through 19:55:30 UTC. The player completed four full circuits:
ground to first, first to second, second to first, and first to ground. The
client's sixteen plane-changing region records confirm all four circuits; their
synchronous client-region work was only 1.368--4.082 ms.

The visible cost is strongly asymmetric by destination:

| Destination | Measured transitions | Baseline packets | Atomic baseline | Worst OpenGL interval | Worst client loop | Largest chunk upload |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| First floor from ground | 4 | 4 | 49--51 ms | 18.6--23.6 ms | 21.2--22.8 ms | 6.0--6.3 ms |
| Second floor | 4 | 4 | 50--51 ms | 22.4--22.7 ms | 20.0--20.4 ms | 1.3--1.5 ms |
| First floor from second | 4 | 4 | 49 ms | 19.1--19.6 ms | 20.5--21.1 ms | 0.7--0.8 ms |
| Ground floor | 4 | 9 | 219--303 ms | 146.8--215.6 ms for the three completed summaries | 52.5--94.8 ms including the marked final return | 81.2--83.8 ms |

The Ctrl+F8 marker landed on the final ground return. At the marker the nearby
client-loop maximum was 94.810 ms and the current OpenGL render was 110.755 ms;
the atomic receipt record completed at 303 ms. The three preceding ground
returns each assembled 86 scenery inputs and spent 60.499--65.008 ms of worker
CPU on scenery meshes, then incurred an 81.2--83.8 ms maximum chunk upload.
Upper-floor transitions assembled only 8--26 scenery inputs and did not show a
comparable upload burst.

These ladder transitions start a fresh context each time. The next ladder use
therefore marks the preceding trace `superseded` before the collector's longer
settling timeout, even with a two-second visual pause. The direct packet,
region, CPU, GPU, and bounded frame rings are complete and internally
consistent, but the generic analyzer intentionally excludes them from its
settled-only aggregate. The table above is consequently derived from the
marked trace records directly rather than from the aggregate case matrix.

This case does not identify ladder handling or synchronous client-region work
as the hitch. Returning to the much denser ground scene combines more static
baseline data, materially more scenery construction, and an approximately
82 ms GPU upload. It is a separate cold/dense scene cost from the already
confirmed 640 ms server page-cadence threshold: these ground products use one
fence plus eight data pages and therefore do not spill into a second normal
world update.

## First Marked Cold-Case Evidence

Session
`output/renderer-diagnostics/session-20260802-104354-2428779` captured one
settled first-visit cardinal crossing under `boundary-cold`. This run predates
the added client-loop/retained-frame/presenter-wait fields, but its existing
offset arrays and atomic events establish the following timeline:

- The prior OpenGL frame completed 4.682 ms before context handling began.
- The atomic Player receipt arrived about 83.600 ms after context start.
- The complete static baseline arrived about 638.951 ms after context start.
- Presentation first reported that the terrain product was still pending at
  about 682.591 ms, sampled complete products around 726.149 and 742.142 ms,
  and released the retained scene at 742.205 ms.
- The first replacement OpenGL frame completed at 946.393 ms. Its render took
  203.257 ms, including 151.668 ms to upload 228,054,240 bytes and 35.562 ms to
  build the shadow mask.
- The resulting measured OpenGL completion interval was 951.075 ms. It is not
  a single 951 ms render call: roughly 639 ms precedes the final baseline, the
  release/stability work continues to 742 ms, and the replacement render then
  completes at 946 ms.
- Client-side named packet/region phases do not account for the quiet interval
  before the baseline. The largest recorded handlers were opcode 48 at 74.150
  ms, scenery mesh construction at 34.178 ms, context handling at 17.522 ms,
  and the client-region load at 9.878 ms.
- GC accounted for 21 ms, measured disk work for 1.078 ms, and named lock
  acquisition wait for 0.006 ms. None independently explains this cold hitch.

The strongest current interpretation is **multiple serial contributors**:
receipt/stage latency dominates the retained-scene duration, then a large cold
GPU upload plus shadow build delays the first replacement frame. The client
trace cannot yet distinguish network arrival from server update cadence during
the quiet receipt interval, so this remains a measured boundary rather than a
final server/network attribution. The newly added timeline fields separate
client-loop continuity, retained-frame attempts, OpenGL presenter wait, and
submission-queue time on the next run.

## Warm Cardinal Directional Evidence

Session
`output/renderer-diagnostics/session-20260802-110206-2437601` measured repeated
return crossings between centers `(11,11)` and `(11,12)` under
`boundary-warm-cardinal`. The player reported that one direction had an easy to
capture stutter while the reverse was nearly imperceptible. All 11 return
crossings toward `(11,11)` and all 12 return crossings toward `(11,12)` matched
the same direction-specific timing split:

| Destination | Return traces | Static baseline p50/p95/p99/max | Release p50/p95/max | Retained attempts | Max render/client loop |
| --- | ---: | --- | --- | --- | --- |
| `(11,11)` | 11 | 15.306 / 18.789 / 18.789 / 18.789 ms | 28.979 / 38.431 / 38.431 ms | 1 | 37.960 / 38.914 ms |
| `(11,12)` | 12 | 633.686 / 650.274 / 650.274 / 650.274 ms | 654.739 / 671.609 / 671.609 ms | 38–40 | 42.819 / 34.175 ms |

Every correlated Ctrl+F8 marker attached to a settled boundary trace selected
the slow `(11,12)` direction. Prediction was published and matched in both
directions. Scenery mesh construction, cache behavior, GL render work, disk,
GC, and measured locks were comparable and far below the 615 ms directional
gap.

The packet timeline identifies the exact gate. A representative slow trace
received:

- context 157, Player 191, and dynamic/static setup packets by 19.2 ms;
- the atomic scene fence plus eight scene-baseline data pages by 20.5 ms;
- the remaining two data pages at 633.4–633.7 ms;
- static-baseline completion at 633.8 ms and presentation release at 654.7 ms.

Page-level confirmation in
`output/renderer-diagnostics/session-20260802-111800-2459330` removed the one
remaining inference. The fast `(11,11)` product has exactly eight data pages:
two gameplay-scenery pages, one gameplay-wall page, four presentation-scenery
pages, and one presentation-wall page. With its fence, all nine packets arrived
on server tick 3862 and completed by 32.5 ms. The slow `(11,12)` product has ten
data pages: three gameplay-scenery pages, one gameplay-wall page, five
presentation-scenery pages, and one presentation-wall page. Its fence and first
eight data pages arrived on tick 3867 by 22.9 ms; presentation-scenery page 4
and presentation-wall page 0 arrived on tick 3868 at 633.4 and 633.7 ms. The
client handled both bursts promptly. This rules out client packet-drain cadence,
renderer work, and network throughput as the cause of this repeatable
warm-direction hold.

The server source explains the 640 ms quantization:

- protocol-v8 baseline data pages contain at most 512 records;
- `sendSceneBaselineIfEnabled` sends at most eight data pages per game-state
  update and retains category cursors for the next update;
- an atomic fence is sent before that eight-page burst;
- the high-frequency movement stream intentionally does not originate or
  finish a context/baseline; the next opportunity is the 640 ms normal world
  update.

The fast scene fits the eight-page burst exactly, while the slow scene exceeds
it by two pages and must wait for the next world update. That scene-density
threshold—not traversal direction itself—produces the consistent directional
asymmetry. The code comment promises that context and baseline are emitted as
one ordered atomic update, but the eight-page server burst limit violates that
promise for ordinary dense scenes.

### Solution comparison for the confirmed warm gate

1. **Complete protocol-v8 baseline in the initiating update, with an explicit
   page/byte safety bound (recommended prototype).** Compute the required page
   count first and send the complete atomic product when it fits the bound.
   Refuse or pre-stage an oversized product rather than silently splitting an
   activation across world ticks. This preserves the existing scene data and
   atomic release semantics.
2. **Raise the fixed burst from 8 to 16.** This is the smallest comparison
   experiment and should remove the measured 10-page wait, but it is brittle:
   a denser scene can cross the new threshold and recreate the same full-tick
   hitch.
3. **Pre-stage static presentation pages with the predicted center.** This is a
   stronger long-term bandwidth design because most baseline bytes arrive
   before activation, but it requires sequence-safe discard/replacement rules
   and is materially more complex.
4. **Finish baseline pages from the high-frequency movement poll.** This would
   reduce the delay but spreads game-state presentation ownership across two
   update paths and risks inconsistent snapshots; it is not recommended.
5. **Release on the fence before outer presentation pages finish.** This would
   hide the wait by exposing a mixed old/new scene and can restore the
   wrong-area flash. It fails the scene-correctness requirement and is rejected.

The next implementation decision should therefore separate this confirmed
server page-cadence gate from cold GPU/upload work. Removing the full-tick warm
wait is higher value and lower risk than another renderer micro-optimization;
cold, diagonal, dense, and multi-level measurements remain necessary before
selecting any broader GPU/residency change.

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

### Private login follow-up: steady-state receive backlog

The incremental frame receiver removed the login deadlock, but the first
successful dense-scene session exposed a separate steady-state problem. The
client kept rendering at approximately 60 FPS while its kernel receive queue
grew to roughly 386 KiB and the server eventually became receive-window
limited again. The server packet counter advanced by about 49,000 packets over
ten minutes, which exceeds the legacy client's maximum consumption of one
packet per frame.

This was not an input, pathfinding, or teleport failure. Server logs confirmed
that the same session accepted player navigation and moved `devduck` from
`(576,576)` through `(574,575)`. It then accepted `tp 575 575` and `tp falador`,
ending at `(304,542)`. Those updates were applied authoritatively while the
client still appeared immobile because their ordered replies were delayed
behind the growing receive backlog.

The current bounded prototype therefore distinguishes two steady-state modes:

- legacy/non-layered connections retain one packet per client frame;
- native layered sessions may consume at most four packets per client frame;
- atomic activation retains its separate 32-packet hard bound and terrain-halo
  pause.

Four packets per frame supplies bounded headroom over the measured private
traffic without allowing an unlimited drain or changing legacy cadence. The
policy has deterministic coverage for all three bounds. Private visual
validation must confirm that login, walking, teleportation, boundary crossing,
and frame pacing remain correct before this candidate is accepted.

### Unattended bounded baseline-completion prototype

The next server-side candidate is implemented for automated review but has not
yet received private visual acceptance. Before sending baseline data, the
server now calculates the exact remaining page count and framed wire bytes
across gameplay scenery, gameplay walls, presentation scenery, and
presentation walls.

For an actively fenced protocol-v8 scene only, the complete remaining product
is queued in the initiating world update when it fits both hard bounds:

- at most 16 baseline data pages;
- at most 96 KiB including simplified frame overhead.

The captured dense `(11,12)` product was 11 pages and approximately 50 KiB, so
it qualifies without raising either ordinary paging limit. Non-atomic
protocol-v8 updates retain eight pages per world update, and legacy baseline
delivery retains four. An activation exceeding either cap uses the existing
bounded fallback and records `ATOMIC_OVERSIZED_FALLBACK` with its page and byte
measurements when boundary diagnostics are enabled.

Baseline cursors now advance only after the exact packet is successfully
generated and placed on the player's ordered outbound queue. A failed packet
therefore remains owned by the pending product and is retried instead of being
silently counted as delivered. The atomic-pending sequence is cleared only
after all four categories are complete.

Deterministic coverage exercises exact and partial pages, cursor accounting,
the reconstructed 11-page case, both exact caps, both over-cap fallbacks,
empty products, invalid inputs, and transactional integration ordering. Server
compilation and the surrounding atomic-activation, presentation-ring, packet,
wire-envelope, transition, and synchronization tests pass. The running private
server was not restarted and no client was launched for this milestone; the
first return test must load the checkpointed server build and repeat the same
warm directional crossing.

The session analyzer can now join that return run to the server evidence. Its
optional repeated `--server-log` input recognizes both the new bounded format
and the earlier `sent=N progress=...` format. The report includes delivery-mode
counts, remaining-page and wire-byte p50/p95/p99/max distributions, successful
atomic completions, incomplete attempts, oversized fallbacks, queue failures,
game-thread delivery duration, ordered-queue growth, channel writability,
encoder payload/headroom samples, bounded-retention coverage, and timestamped
context-sequence candidates for the client traces. Context sequence alone is
explicitly labeled supporting evidence because it can repeat across players or
server runs. The encoder diagnostic now carries the scene context alongside
protocol/category/page identity, allowing queued work to be distinguished from
work that reached Netty framing. A fixture containing an account name, address,
and password proves that unrelated text does not reach output.

Re-analysis of the preserved rotated pre-prototype server log found 21 legacy
page-summary records and 99 matching encoder page records. Ninety of those 99
encoder samples reported the channel unwritable, while the old identity format
did not include context. This does not establish that channel pressure caused
the visual hitch—the client trace still shows the decisive extra world tick—but
it makes queue and encoder measurements a required acceptance signal for the
larger same-tick burst. The new format supplies those missing fields.

### Unattended automated verification (2026-08-04)

No client was launched for this verification. The stale private server was
stopped before the build/test run, and no public/live service was touched.

- The authoritative client and server builds pass.
- The focused baseline policy, incremental packet-frame, terrain-stage paging,
  layered client-authority, atomic activation, visibility-ring, wire-envelope,
  transition/minimap, movement synchronization, packet diagnostics, boundary
  diagnostics, and session-analyzer tests pass.
- `./scripts/lint.sh all --offline --base $(git merge-base
  spoiled-milk/main HEAD)` passes with no new gated javac, Ruff, or SpotBugs
  findings.
- The repository-wide deterministic suite passes when continued around two
  guards already stale relative to published `main`: the registry fixture
  expects 1,060 animations and older item/animation hashes while runtime now
  resolves 1,080 animations and current item data; the repeating-action test
  still expects the skill-name table in `PacketHandler` after that ownership
  moved. This branch changes neither catalog/fixture nor the Harvest ownership.
  Both exact failures are retained for manager follow-up rather than silently
  changing unrelated baselines.
- The analyzer was exercised against the actual gzip-rotated private server log
  as well as privacy, missing-path, bounded-retention, old-format, new-format,
  queue-pressure, encoder-pressure, and context-correlation fixtures.

### Exact return validation for the baseline prototype

The next private run must restart the private server so it actually loads
checkpoint `d3b98d933`; the server that remained running while this milestone
was built still contains the older classes. After the user returns:

1. Launch one private client with `--boundary-diagnostics`; do not enable frame
   capture.
2. Re-establish the known warm-cardinal route between centers `(11,11)` and
   `(11,12)`. Perform two warm-up round trips, then at least six measured round
   trips in each direction under one named performance phase.
3. Press Ctrl+F8 immediately for any visible hitch or wrong-area flash. Confirm
   walking, teleporting, login, and return movement remain responsive, and
   sample the socket receive queue to ensure the steady-state backlog stays
   near zero.
4. In the server log, the captured dense direction should report
   `mode=ATOMIC_COMPLETE`, `sent=11`, and `remainingAfterPages=0` in the
   initiating update. Any `sendFailed=true`, incomplete atomic result, or
   unexpected oversized fallback is a stop condition.
5. Close the client normally and run the analyzer with the matching current
   and rotated private logs. Compare client static-baseline/release offsets,
   frame interval maxima, and server mode/page/byte records for the same
   timestamp and context candidates.
6. Only after the directional gate passes, resume cold, return, true diagonal,
   dense-scenery, and multi-level cases. A correct same-tick warm result does
   not establish that cold GPU uploads, shadows, or upper-level activation are
   solved.

Complete private visual validation of both the bounded terrain-stage transport
and incremental frame receive, then resume the diagonal matrix. After that,
prototype complete bounded protocol-v8 baseline delivery and visually repeat
the same directional case. Continue the remaining private case matrix before
choosing any broader renderer optimization. Bounded GPU residency or predicted
GPU preparation remain possible cold/dense-case candidates, but the choice
between them depends on:

- whether the remaining visible dip still coincides with large upload bytes;
- whether the predicted product is ready with useful lead time;
- whether return crossings already reuse the intended chunks;
- whether dense and multi-level cases fail for a different reason.

Checkpoint the diagnostic implementation and this plan before any substantive
loader or renderer change. Visually compare the selected prototype on the same
private cases, and do not publish or deploy based on automated results alone.

### Private atomic-baseline acceptance and next renderer gate (2026-08-04)

Session
`output/renderer-diagnostics/session-20260804-111401-620485` used private port
43615, the production-equivalent `spoiled-milk-replacement` layered package,
renderer diagnostics with frame capture disabled, and server delivery
diagnostics from `server/logs/spoiled_milk_dev_98.log`. The owner reported that
the prior worst area improved from an approximately 45 FPS boundary dip to
approximately 55 FPS. Lighter areas also converged on approximately 55 FPS,
which removed the old direction/density asymmetry but left a common visible
transition floor. Four intentional Ctrl+F8 observations were reported; the
bounded recorder contains five marker events because one physical key action
may have repeated. No visual wrong-area flash was reported.

The bounded server prototype passes its private acceptance gate:

- all 37 atomic products used `ATOMIC_COMPLETE`;
- each product completed in its initiating update with zero pages/bytes left;
- products contained 8-11 pages and at most 46,195 framed bytes;
- game-thread delivery was 0.836 ms p50, 1.471 ms p95, and 2.262 ms max;
- no generation/queue failure or oversized fallback occurred;
- ordered queue growth was at most 11 entries, maximum queue depth was 38,
  and the channel remained writable after every game-thread burst;
- the sampled loopback socket queues returned to zero between transitions.

The remaining client cost has two measured forms:

1. Warm dense returns no longer wait a server tick. Their replacement frames
   are dominated by shadow-mask construction: normally about 26-32 ms in the
   marked sequence, with one 118.954 ms build coincident with 85 ms of GC.
2. First visits and returns along the lighter cardinal route synchronously
   upload nearly the entire resident frame. Individual crossings requested
   106-125 chunks, uploaded 98-117, reused only 8-9, transferred 148-223 MiB,
   and spent 99.650-145.998 ms inside chunk upload. The immediately repeated
   return from center `(2,10)` to `(3,10)` still uploaded 117 of 125 chunks.

The second result selects bounded residency before a broader prediction
pipeline. `ChunkMesh.rebasePresentation` and
`rebaseStaticObjectPresentation` intentionally preserve
`storageSignature`, and the resident shader already applies the difference
between a mesh's current vertex offset and the buffer's uploaded vertex
offset. However, `WorldChunkBufferKey` currently includes presentation center
and origin. Unchanged GPU storage therefore receives a new lookup key after a
boundary rebase and is uploaded again despite the existing draw-offset
contract.

The next reversible prototype will key shader/draw-offset resident buffers by
immutable storage identity plus plane/object role, while preserving the
position-specific key for legacy paths that cannot apply draw offsets. It must
prove that rebased meshes share a buffer key, different storage signatures do
not, object roles and planes remain isolated, legacy keys remain positional,
and buffer eviction/deletion stays single-owner and bounded. The same private
routes must then demonstrate substantially higher reused-chunk counts and
lower upload bytes without exposing stale or misplaced geometry. Shadow-mask
preparation remains a separate follow-up after residency is corrected.

### Storage-identity prototype correction (2026-08-04)

The first storage-key prototype was visually exercised in
`output/renderer-diagnostics/session-20260804-113602-643012`. The first two of
eight Ctrl+F8 records were explicitly identified by the tester as accidental
markers and are excluded. The six valid markers correspond to diagonal return
traces 9 and 11--15.

That run rejected the first key shape rather than validating it. Each marked
crossing still uploaded 75--84 of 124--133 requested chunks, transferred
135.843--166.045 MiB, and spent 98.464--123.091 ms in the maximum upload
frame. The associated maximum client-loop samples were 51.419--74.992 ms.
Shadow construction was normally 24.080--29.492 ms and therefore did not
explain the much larger synchronous upload burst.

The precise failure was cache-key conflation. Resident vertices include the
projected-shadow proof baked from the complete active caster set, and
`WorldChunkBuffer.matches` consequently includes that set's
`shadowProofSignature`. The first prototype keyed only by immutable geometry.
Two adjacent views containing the same geometry but different baked-shadow
variants therefore addressed the same buffer and overwrote one another on
every crossing. This traded the previous warm-return behavior for a consistent
re-upload and must not be retained as-is.

The corrected prototype keys shader/draw-offset storage by immutable geometry
*and* the baked-vertex variant signature. Rebased copies of one variant still
share a VBO, while the two boundary-specific shadow variants can coexist in
the bounded LRU. The fixed-function path remains position-specific. A compiled
fixture now proves rebased reuse, variant separation, plane/role isolation,
legacy positioning, and simultaneous lookup of both shadow variants. Private
visual validation must warm both directions before measuring; the expected
signature of success is a one-time population cost followed by near-zero
chunk uploads on repeated returns.
