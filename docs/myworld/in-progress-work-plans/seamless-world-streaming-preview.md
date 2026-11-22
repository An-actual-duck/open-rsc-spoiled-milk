# Covered Walking Preview

## Current Full HD browser preview

The loopback preview on port 8593 uses exact integer rounding in the
resident camera bounds check. It carries compact scenery conversion,
the empty roof removal guard, and cached transforms through
translation-only scene rolls. The separate WebGL worker presents at
1920×1080 with the original 960×540 logical UI. The disposable account
is `browserdemo` / `BrowserProof13`. Normal keyboard and mouse login
reached the playable scene at `(239,609)`. Port 8579 offers the previous
build for comparison.
[Play the updated preview](http://127.0.0.1:8593/?mode=webgl&physical=on) · [Manual login proof](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-player-preview/manual-login-result.json) · [Pacing results](#integer-camera-bounds) · [Compare on port 8579](http://127.0.0.1:8579/?mode=webgl&physical=on)

## Early covered walking fixtures

This is the first private step in the [active seamless streaming plan](seamless-world-streaming-plan.md).
It exercises ordinary walking across the middle of an already installed 3×3
surface scene. The server keeps the same center while the player stays within
the prepared walking area. The matched client keeps using that scene and
applies ordinary movement updates. At the outer margin the server still sends
a replacement scene through the existing path. Independent cell publication
and movement context changes remain follow-up work.

## Waiting for adjacent coverage

The next private server revision keeps the last acknowledged surface window
as its packet and visibility authority while an adjacent window prepares. It
can continue sending movement and normal updates within that available area.
A queued step cannot leave the installed window while the transition is
pending. At the last tile the server can fall back to sending a complete
context if prediction is still waiting.

The client still installs the eventual replacement scene through the existing
activation path. The reported long pause during sustained browser travel
remains an acceptance case for independent cell publication.

## Review route

On the private test world, start at Lumbridge `(120, 648, 0)` and walk to
`(109, 674, 0)`, then return. The former section boundary is `y = 672`.
Look for continuous walking animation and camera movement as you cross and
reverse. Check nearby actors and try a click or interaction during the walk.
Longer travel past the prepared area can still show a load.

The fresh native fixture sent ordinary walk requests on a matched local server
and client. Both modes reached the same server-confirmed endpoints. The
preview kept context sequence `1` on both crossings and the client sampled
zero retained-scene states. The control advanced `1 → 2 → 3` and sampled a
retained scene on both crossings. The repeat showed changing Java camera
positions, actor positions, walking phases, and scene frames on both legs.
All six shared nearby NPC indexes sampled at each crossing kept their client
actor instances. The fixture measures Java state and the presentation latch.
Displayed cadence, visual NPC motion, doorway interaction, and end-to-end
browser walking still need review.

The bounds fixture exercises the center-retention decision on all four sides.

## Outer coverage route

The signed surface walk from `(239, 609, 0)` to `(269, 609, 0)` crosses
the storage boundary at `x = 240` and the preview's scene handoff at
`x = 264`. The matched native client and server reached the destination in
one walk request. At the handoff the context advanced from sequence 1 to 2,
the fixture sampled the retained scene four times, and all 18 nearby NPCs
sampled on both sides kept their client actor instances. The longest gap
between fresh Java scene frames on the leg was 132 ms on the isolated native
display. That interval measures scene production, with display cadence
outside the fixture's scope.

The server and client had already prepared the predicted terrain and
structure on this run. The diagnostic trace recorded a hit on the prepared
terrain product at the transition. The scene handoff still went through the
client's context and baseline activation and held the previous frame.

Two headless Firefox WebGL runs waited for the initial world to report ready
before requesting the same walk. The private server confirmed arrival at
`(269, 609, 0)` both times. The Java client held the old scene for
1,011–1,062 ms across `x = 264`, and the browser's ordinary-walking readiness
transition blocked world input for 1,813–1,890 ms. Both runs counted 18 held
frames and 37 chunk uploads totaling 88,526,328 bytes. The longest gaps
in fresh Java scene production were 1,040 and 1,063 ms. These timings are
from headless Firefox and its software-reported presentation state.

### Predicted browser buffers

The private browser candidate takes completed static outer terrain and
structure meshes from the existing prediction. It packs and submits at
most one eligible mesh on an ordinary frame with no discretionary
active-scene upload. The existing renderer owns submission. Speculative
buffers use free space within the existing cache budget and have a separate
48 MiB cap. The active scene adopts an entry when the storage key, material
revision, and packing settings match. Movement and scene activation use the
same code paths in both modes.

In the matched headless Firefox runs, the candidate submitted seven meshes
by `x = 260` and adopted all seven at the handoff, reusing 12,141,720
bytes. The transition recorded 30 uploads and 76,167,456 bytes versus
37 uploads and 88,526,328 bytes in the control. The Java scene hold was
about one second in both runs. The browser world-input gate measured
1,610 ms for the candidate and 1,818 ms for the control. Both accounts
reached the server-confirmed destination. The longest measured time to
pack and submit one speculative mesh was 8 ms. The fixture measures Java
submission and subsequent resource reuse. GPU completion before the
boundary and hardware display cadence still need direct measurement.

A fresh candidate repeat adopted five staged meshes totaling 8,617,176
bytes. The transition submitted 32 uploads and 79,800,576 bytes, with the
browser input gate at 1,724 ms and the Java hold at 978 ms. Both candidates
moved measurable work earlier. The headless timings vary enough that the
input-gate reduction should be treated as provisional.

### Java handoff trace

A private headless Firefox trace used early uploads and ordinary rendering
settings. At `x = 264` the adjacent terrain prediction matched. The client
accepted the player update about 20 ms after context receipt and completed
the scenery baseline about 290 ms after receipt. Region installation took
151 ms within that interval. The first fresh frame then spent 658 ms in
resident object frame assembly. The second spent 3 ms in the same phase.
It passed the existing two-frame stability check and released the scene
about 1,026 ms after context receipt. The private server confirmed arrival
at `(269, 609, 0)`. The trace had no missing events. Durations can overlap.

The repeat with captured scenery origins spent 8 ms preparing object inputs
and 624 ms rebuilding meshes on the first replacement frame. It reused 67
of 95 object cells and rebuilt the other 28. The second frame took 3 ms for
object work, and Java released the scene about 1,003 ms after context receipt.
Both traces released after two matching presentation frames with 112 total
chunks and 568,324 triangles. Capturing the origin prepares the mesh builder
for future work on a pending scene. The repeat still built the new meshes
at the handoff.

The trace can run alongside an owner preview. Stage a fresh run with
`--preupload --trace` and a separate HTTP port. The trace fixture reserves
private ports `43669` and `43569`. Read the events in
`browserProof.regionHandoff.events`. Context `2` identifies the walk
handoff. Events distinguish baseline completion, world and object frame
assembly, presentation candidates, and release. This mode collects timings
without enabling the browser benchmark preset.

### Predicted outer scenery

In a matched headless Firefox pair, batching the predicted scenery jobs
reused all 17 cells from the entering strip. The first replacement frame
rebuilt 11 of 95 object cells instead of 28. Mesh construction inside that
frame took 339 ms with prebuild and 660 ms with the flag off. Java released
the scene at 708 ms with prebuild and 1,032 ms with the flag off. Both runs
arrived at `(269, 609, 0)` with 112 chunks and 568,324 triangles in the
released frame. The candidate's maximum observed approach gap was 1,412 ms
compared with 1,093 ms in the control. That result prompted the paced
dispatch experiment. The runs also adopted different numbers of terrain
buffers, which limits what can be attributed to scenery from the WebGL
input timing alone.

The paced build waits for the predicted terrain structure to be ready, then
dispatches at most one scenery cell every 75 ms. It waits for the previous
job to finish before dispatching another. The scene can take completed
matching jobs at the handoff. A pending job causes the ordinary mesh build
for that cell.

The paced candidate at `0ba3179c6` reused all 17 predicted cells again and
built 11 at the handoff. It spent 292 ms on reused meshes before the
handoff and 350 ms on mesh construction in the replacement frame. The
handoff released after 713 ms in Java. The WebGL input gate lasted 1,415 ms
and the renderer submitted 32 uploads totaling 79,800,576 bytes. The
largest measured approach gap was 1,112 ms at the start of the walk, before
the player left `x = 239`. The server confirmed the same destination and
the released frame had the same chunk and triangle counts as the unpaced
pair. This covers one direction on the private surface route.

The matched paced control rebuilt 28 object cells, spent 620 ms on mesh
construction in the replacement frame, released at 991 ms in Java, and
held the WebGL input gate for 1,655 ms. Its largest approach gap was 1,082
ms from `x = 239` to `x = 242`. Both runs reused the same five terrain
buffers and submitted the same bytes at the handoff. The shorter hold
shows why the prebuild is worth keeping in the private preview. The first
movement gap and roughly 80 MB of handoff uploads still need work.

Stage a fresh private run for manual testing with one HTTP port and two
unused loopback game ports.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py scenery-manual \
  --http-port 8228 --tcp-port 43689 --ws-port 43589 \
  --preupload --trace --predicted-scenery --manual
```

Sign in with the disposable credentials printed by the staging script. The
player starts at `(239, 609)`. Walk toward `(269, 609)` and watch for the
handoff near `x = 264`. Use fresh output names and add `--predicted-scenery`
to only the candidate to compare automatic runs.

### GPU staging and compact scenery

With `--preupload-scene`, the browser can stage the replacement center and
scenery meshes alongside the predicted outer terrain ring. The active scene
left room for about 10 MB in the existing GPU budget, so that run retained
only one additional scenery buffer. The matched control staged five buffers
and submitted 79.69 MB at the handoff. The candidate staged six buffers and
submitted 78.70 MB.

`--compact-scenery` uses the existing 48-byte shader vertex layout for local
static scenery when every used attribute can be represented exactly. Terrain
and animated object meshes keep their existing format. The GPU budget tracks
the stored bytes of each buffer. The same-commit Firefox comparison reused
26 predicted buffers totaling 41.56 MB, including all 17 prepared scenery
meshes. New uploads at the handoff dropped from 78.59 MB to 14.80 MB, and
held frames dropped from 16 to 7. The measured input hold went from 1.42
seconds to 1.60 seconds on those runs. The scene still depends on Java's
atomic activation and on the browser receiving a complete replacement draw
set. Both runs reached `(269, 609, 0)` and released 112 chunks with 568,324
triangles. The browser packing fixture compared the actual Java compact
shader attributes bit for bit against its legacy output.

`--pace-terrain` yields during predicted terrain mesh construction. In a
repeat with compact scenery enabled, the largest frame gap on approach
dropped from 1,105 ms to 556 ms. The paced run again reused 26 buffers and
submitted 14.80 MB at the handoff. Java released at 728 ms, including a
155 ms local region load and 356 ms of remaining mesh builds. The browser
still held input for 1,609 ms. The private manual build at port 8239 uses
this pacing and compact staging.

`--prebuild-ownership` adds candidate cells where scenery enters or leaves
the authoritative inner window. The server derives candidates from the
viewer's normal static queries. The client keeps the existing exact
placement and height check at adoption. In the paced Firefox follow-up,
all 20 prepared scenery cells matched, including three more than the
entering-strip run. The Java hold dropped from 728 ms to 523 ms, and the
browser input hold dropped from 1,609 ms to 986 ms. The final view had the
same 112 chunks and 568,324 triangles and the server confirmed arrival at
`(269, 609, 0)`. The browser reused 29 buffers and submitted 6.26 MB at the
handoff. The approach gap measured 725 ms and the longest single early
buffer submission measured 203 ms. Both require further work. The manual
build at port 8241 includes this experiment.

### Prepared local terrain

`--prepare-local-terrain` makes the prediction worker prepare a detached
copy of the native surface window, including base collision, structural
elevations, minimap layout, and wall and roof models. Only an exact terrain
scope and revision confirmed for ordinary walking can install the result.
Palette lookup and publication remain on the client thread. Live scenery
collision and actor updates still come from the authoritative packets.

The matched Firefox runs arrived at `(269, 609, 0)` with matching checksums
for collision, structural elevations, and minimap pixels. Both released
112 chunks and 568,324 triangles. The local terrain load went from 130 ms
to 2 ms, including a 1 ms prepared install. The Java hold went from 513 ms
to 392 ms and the browser input hold went from 999 ms to 842 ms. The longest
approach gap was 619 ms with local terrain preparation and 603 ms in the
control. The private manual build at port 8245 includes this change.

### Incoming interactive scenery

`--prepare-incoming-scenery` prepares detached models for objects that will
enter the interactive window. The authoritative delta still decides which
objects to place and supplies their positions and directions. The client
checks the exact placement and model source before reusing each clone.
Collision, picking, and live placement still apply on the client thread.

In the matched Firefox runs, the client reused all 271 requested models.
The same 3,786-byte scenery update took 9 ms with prebuild and 97 ms in the
control. Java released after 293 ms with prebuild and 389 ms in the
control. The browser input pause was 780 ms with prebuild and 822 ms in the
control. The longest frame gaps on approach were 568 ms and 728 ms in those
individual runs. Collision, structural elevation, and minimap checksums
matched again. Both runs arrived at `(269, 609, 0)` with 112 chunks and
568,324 triangles. This compares one ordinary crossing in Firefox.

The final repeat also yielded while collecting the predicted terrain's
vertices, faces, and overlays. It reduced the observed approach gap to
488 ms on that run. Java released after 293 ms and browser input reopened
after 793 ms. The remaining 141 ms mesh build and four held browser frames
keep the handoff visible. The server-confirmed endpoint, render counts,
and terrain checksums matched again. The private manual build at port 8250
includes the prepared terrain, incoming model reuse, and finer pacing.

## Sustained travel and delayed stutter

The 793 ms result above came from a single scripted crossing. The manual
playtest reported long loads, FPS drops, and delayed stutters during sustained
travel. The prior manual preview kept the server's last saved position
`(291, 597)` and had no record of frames from that session.

The new private Firefox route goes from `(239, 609)` through `(284, 609)` and
`(291, 597)` to `(322, 597)`, then returns to `(255, 609)`. Each walk uses the
normal client path and waits for server confirmation. The trace correlates
the timestamp and player pose of every fresh WebGL world submission with
server tick receipts. It records input release, speculative uploads and
elapsed client phases. Login is excluded from walking results.

| Measurement on the same six-leg route | Slicing off | Slicing on |
| --- | ---: | ---: |
| First handoff input pause | 780 ms | 1,212 ms |
| Second handoff input pause | 302 ms | 306 ms |
| Return handoff input pause | 384 ms | 418 ms |
| Longest gap outside a handoff | 593 ms | 580 ms |
| Longest speculative preupload step | 226 ms | 30 ms |

Both runs reached the same server position and produced identical collision,
elevation and minimap checksums. The first crossing with slicing on had
14.4 MB left to upload, compared with 6.3 MB with slicing off. Slicing stays
experimental. The private manual build at port 8253 uses the control path.

The control recorded a 515 ms gap on the return leg with zero geometry
uploads. It recorded another 514 ms gap about eight seconds after the first
handoff, with 338 ms inside the client connection update. The sliced run had
a 560 ms gap in the same area with 367 ms in that phase. The server advanced
at most two tiles during the captured long gaps. The reported ten-tile event
remains unexplained. Headless timings characterize these two runs and do not
predict the duration of a desktop freeze.

The manual preview submits long frame gaps and transition timings to its
own loopback HTTP server. Its `manual-travel.ndjson` stays beside the private
server's `travel-server.ndjson`. Captures include tile positions and timing
data. An ordinary recheck can compare the last submitted player pose,
authoritative position and gap between fresh frames at a scene transition.

The follow-up control breaks down the connection update into terrain-stage
polling, socket reads, and ordered packet handling with the slowest opcode per
frame. It captured a 519 ms gap at `(284, 609)`, about eight seconds after the
first handoff. Of that interval, 307 ms elapsed in the socket-read path and
15 ms elapsed in packet handlers. At `(291, 597)` one 418 ms gap included
276 ms in reads, 1 ms in packet handling, and zero geometry uploads. The
timers are elapsed time and can include runtime scheduling pauses.

The browser socket can cross between Java and JavaScript twice for an empty
poll, and five times for a complete frame under the legacy stream adapter.
The private `--socket-read-ahead` experiment combines availability and close
checks, and copies up to 16 KiB of buffered WebSocket bytes into Java for the
existing frame parser. The parser still owns framing, the receive order, and
packet drain limits. The private game server and movement state are unchanged
by the socket experiment.

Two buffered-read attempts ended before the first walking leg. One stayed at
login and one reached the game before the Java browser program stopped. A
third run completed all six legs and matched the control's position and terrain
checksums. That console capture verifies completion and does not contain frame
timings. The private manual build keeps the control socket path while the
candidate's startup reliability and frame pacing are under investigation.

## Movement without a terrain load

The matched, loopback-only `--persistent-movement` path gives authoritative
movement its own session. The server announces a new session after sending
its actor baseline. Fast movement then carries the stable session identity
and an ordered sequence through terrain-center changes. Login, relocation,
and world changes establish a new session. The client takes waypoints from
the server and updates the existing actor instances on installed ground.
That handler performs no terrain load or prediction request.

The private Firefox proof starts at `(239, 609)`, walks to `(257, 609)`,
crosses the current replacement boundary on the way to `(278, 609)`, and
returns to `(252, 609)` using ordinary walk requests. It requires server
confirmation of the final destination, one movement session across a change
of terrain scene, and no ignored movement outside installed coverage. The
receipt fixture also checks that ordinary movement cannot open a session and
that stale, mis-scoped, and out-of-order receipts are ignored.

The run at `d19d39663` applied 426 movement receipts under one session while
the terrain scene advanced from 1 to 2. The server confirmed `(252, 609)` and
the client ignored zero receipts for missing installed coverage. The reported
logical coordinates matched the server destination. The browser
still held four frames at the scene change. This verifies movement session
continuity and leaves the live-rendering requirement open.

The terrain and scenery publication path still replaces the scene as a unit.
The movement proof is an intermediate implementation check. A playtest
preview requires live rendering and input through the same crossing as
incoming cells publish, followed by sustained travel. The next code needs
to keep overlapping terrain and draw resources on stable world cell keys and
publish the incoming data independently of the legacy scene latch.
[Movement application](../../../Client_Base/src/orsc/mudclient.java#L26070) · [Continuity gate](seamless-world-streaming-plan.md#1-establish-continuity-across-a-former-boundary)

The current renderer assembles a merged center mesh and uses different edge
crops for surrounding sectors based on their position relative to that center.
The positive edge includes an extra face row to stitch the merged mesh. A
persistent renderer needs fixed cell ownership for those rows, then one
source of geometry across the overlap. The existing catalog baker is a
candidate source of independently addressed terrain meshes. Validate its
seams and material and lighting parity against the current signed map
before using it for the live path.
[Legacy cell crop](../../../Client_Base/src/orsc/graphics/three/World.java#L6905) · [Terrain catalog baker](../../../PC_Client/src/orsc/BrowserWholeZoneBaker.java#L140)

The catalog baker now uses the same canonical placement input as the runtime
scenery builder. A fresh bake of neighboring ground sectors `(4, 12)` and
`(5, 12)` produced 48 matching terrain edge segments at `x = 240` and the
same ground heights at all 49 shared edge vertices. No ground triangle
straddled the boundary. This was an offline check of drawn ground geometry
on one edge. Runtime cell publication, lighting, and interactive state still
need the live walking proof.

## Live publication of baked draw cells

The private bounded WebGL renderer accepts `livecells=on` on its existing
catalog path. Its requested draw area follows the player's server-confirmed
sector and can advance while the installed gameplay window stays in place.
The overlap remains drawable. Outer cells join the next live frame after
their individual uploads publish. The installed window still requires ready
ground and matching interactive scenery. A change outside that window does
not block clicks inside it.

Pending work shared by two consecutive draw areas keeps running in the
same movement session. Reversal cancels work that has left the desired
area, and relocation invalidates unfinished work from the previous session.
An optional download failure leaves active draws available. The cell can
retry if it becomes required. Existing GPU byte and resource bounds apply
to resident and partially uploaded buffers.

The fresh Firefox WebGL proof baked three cells from the signed map and
deliberately delayed delivery of the third. It submitted eight live frames
and sampled actual pixels from the first two cells during the delay. The
third produced pixels after it was published. Changing direction removed
that cell's draw while its GPU buffer was still cached. The scheduler
fixture also checks that an unfinished upload can move from optional to
required at capacity, and that an outer change does not invalidate the
input acknowledgement for the installed window.

This proves the draw-side behavior in isolation. The browser gameplay
crossing still needs an independently published collision and scenery
window so Java can keep submitting live frames through the current scene
transition. The full game path still uses the existing scene latch. The
draw proof has no actors, interactive clicks, or game-server traffic.

Run the focused scheduler fixture with
`node tests/myworld/seamless-streaming/live-cell-residency.mjs`. After a client
build, use `python3 tests/myworld/seamless-streaming/run-live-cell-webgl.py
<fresh-name>` with a loopback geckodriver. It bakes three signed ground cells
with the current client and saves the browser result and local logs under
`output/tests/persistent-terrain-cells/`.

## Collision cells and live blockers

The opt-in `--persistent-collision` client path separates static terrain from
live objects and walls. The normal pathfinder still reads its existing local
grid. The grid receives the combined flags for each tile while installed
terrain and object contributions remain separately owned. A removed object
changes only the flags it contributed. Overlapping blocks and reciprocal
wall edges survive until their own contributors are removed.

The terrain prediction worker can hand the game a prepared collision strip
as soon as the terrain and wall flags are known. It assembles the cells
before roof and mesh construction, and the game publishes cells outside the
interactive window under the matching signed-map scope. The current grid
continues to serve walks and interactions during that publication. A
predicted terrain cell alone cannot authorize a walk into the incoming area.
The live scenery and collision information for that area must also be
available before the playable window can expand.

The focused fixture covers overlapping objects, side-by-side cell edges,
terrain flags beneath a wall, and a changed origin and map scope. The private
Firefox run at `1d207dc9d` published all three incoming collision cells
while their render product was still pending. It crossed the terrain center
boundary, applied 432 movement receipts under the same travel session, and
returned to the server-confirmed destination `(252, 609)`. Both scene fences
matched the server's object and wall counts and hashes. The initial
experimental placement of the collision copy delayed browser startup. The
working candidate binds the existing active grid and prepares the new
collision cells on the terrain worker.

The browser still held four frames at the legacy scene swap. The incoming
strip contains static terrain flags. The terrain and the server's revisioned
live scenery still need to join the interactive window together before a
walking transition can skip that swap.

## Authoritative scenery in the incoming strip

The private `--surface-scenery-cells` path subscribes to the three incoming
48-tile cells of a cardinal surface transition. The server reads objects
and walls from its live spatial index using the viewer's visibility rules.
It emits a complete revision when a cell changes, including an empty
revision when the last placement leaves. The client scopes pages to the
travel session, installed context, and expected terrain prediction. A
partial revision makes that cell unavailable until all its pages arrive.
Relocation, recovery, or a new prediction clears the old subscription data.

The client combines the published records with the staged terrain flags
using the same footprint and reciprocal-edge rules as active collision.
That combined cell is available to the next window. The existing gameplay
lists and scene renderer still consume the old region until the ordinary
region activation runs.

The Firefox run at `941393ab9` received 90, 63, and 140 placements in the
three incoming cells. At `x = 258` the test server added an anvil at
`(305, 620)` through the normal object registration path. The incoming
cell advanced from revision 1 to 2 and gained its collision bit. At
`x = 259` the server removed it, and revision 3 cleared the collision bit.
The currently installed scene stayed live throughout those changes. The
server confirmed the return to `(252, 609)` under the same travel lifetime
with zero movement receipts rejected for missing installed coverage. Both
legacy scene fences matched.

The later context change still held 18 frames, with about 1.9 seconds
between the transition and the first fresh submitted frame. The collision
and scenery cells were ready before the crossing. The remaining blocking
path changes the region origin, dematerializes and rematerializes local
scenery, and waits for the replacement browser draw state. The rolling
path needs to retain the overlap and keep
the actor and camera coordinate conversion outside that region rebuild.
Use a separate acknowledgement for interactive coverage and verify that
changing the origin preserves existing local object identity and picking.
Login, relocation, and recovery retain their existing full baseline process.

Run the fresh local scenario with the following arguments after building
the matched client and server.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py <fresh-name> \
  --trace --travel-trace --persistent-movement --persistent-collision --surface-scenery-cells
```

## Rolling the interactive surface window

The private `--rolling-surface-window` path uses the prepared terrain
projection when the walking context moves by one cardinal cell and all
three incoming terrain and scenery cells are present. It prunes outgoing
placements and retires the old terrain models. Overlapping game objects and
walls keep their instances, picking indices are remapped during compaction,
and collision contributors remain keyed to their world tiles. The camera,
existing actors and their queued waypoints, and local effects use the
same origin change. The server's normal ordered scenery deltas still add
entering interactive instances and confirm them with the scene fence.

A fresh Firefox run at `3540125f5` kept 692 overlapping scenery model
references and 53 wall model references at the same world coordinates
and retained the local actor object. The picking indices agreed with the
compacted lists. The server's two scene fences matched. The walk reached
`(291, 597)` in the entering strip and returned to `(252, 609)` under
one travel session, with 562 movement receipts and zero dropped for
missing terrain coverage. The client and focused collision tests also
check that a partial or stale strip cannot qualify for the rolling path.

The browser still held 18 frames at the first crossing and reported
37 chunk uploads totaling 88.4 MB. The renderer still draws a merged
center mesh that changes identity when the origin moves. The next
implementation work is to use fixed world cells in actual gameplay
frames and keep the same GPU buffers for the overlap. Entering draw cells
must be admitted ahead of the origin change. Once that is proven, the
walking path can use the current live presentation instead of triggering
the whole-scene browser readiness gate.

## Fixed draw cells in the running browser

The private WebGL path draws terrain, structural walls, and roofs as
48-tile world cells with fixed half-open boundaries. The existing Java
terrain queries and picking still use the installed gameplay projection.
Changing the origin changes draw offsets while the cell's immutable
storage and GPU identity stay in place. The CPU reuses a cell when its
signed source neighborhood and roof settings agree.

The first Firefox run used the existing five-by-five source field and
kept 10 of 20 overlapping buffers. The footprint of a cell at a source
edge changed when a neighbor became available or left that field. The
matched expanded-border build supplies a seven-by-seven source field
for the same five-by-five draw area. It sends visual and structural
records using the existing bounded terrain pages. The extra ring supplies
neighbor data for meshes and leaves gameplay coverage and the draw radius
at their existing sizes.

The Firefox walk at `977f858f2` submitted all five entering terrain
cells while the original gameplay scene was ready. Their draws were
optional and contributed no work to its input readiness gate. The
subsequent rebase kept all 20 overlapping buffer IDs and promoted all
five entering buffers from the live draw list. The server confirmed the
walk into the new interactive strip and back to `(252, 609)`. Both
scenery fences matched and the client applied 567 movement receipts
in one travel session without rejecting movement for missing coverage.

The browser still held 12 frames at the scene change and went about
1.3 seconds without a fresh world frame. The trace recorded 581 ms in
the scenery mesh phase and 26 GPU uploads during the handoff. With the
complete terrain field already drawable, the remaining graphics step is
to maintain independent scenery draw cells and publish incoming scenery
while the existing view is live. The browser should stop treating an
ordinary surface rebase as a whole new scene after those two products
can both confirm live coverage.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py <fresh-name> \
  --http-port <unused-port> --trace --travel-trace --persistent-movement \
  --persistent-collision --surface-scenery-cells --rolling-surface-window \
  --fixed-surface-draw --expanded-surface-draw-border
```

## Scenery draw cells in the running browser

The private surface subscription delivers complete revisions for the
entering scenery ring and the sectors that change between interactive
and presentation-only ownership. A newer revision retires its pending
draw work. A worker builds canonical 24-tile static meshes from the
latest complete records and signed elevations. The browser stages the
meshes within its existing GPU budget and adds an entering draw only
when its terrain is already drawable. The active scene's readiness
declaration excludes those optional draws.

At the crossing, the client compares each prepared mesh against the
actual server scene using its sorted placements, orientations, and
elevations. Live animated objects stay on the active model path.
The matched handoff adopts exact prepared meshes and can fall back when
a cell changed, was cancelled, or was late. The existing scene fence
continues to verify interactive records.

The first run prepared 41 meshes but stalled at optional draw admission.
Compact packing freed GPU capacity. A second run still could not draw
incoming scenery. The working run allows large meshes to pack in slices
and resolves their materials using the same catalog and fallback rules
as active meshes.

The Firefox walk at `88e58f0ec` submitted 15 optional scenery buffers
before the scene changed and used all 15 of those same buffers afterward.
The Java client adopted 19 matching meshes. All 20 overlapping terrain
buffers and five entering terrain buffers also survived. The player
walked into the new strip and returned to `(252, 609)`. Both scene fences
matched, with 643 movement receipts in one travel session and zero
rejected for missing terrain.

The whole-scene gate still held five frames and went 1.07 seconds
without a fresh frame. On the first browser hold, 114 of the 120 draw
sources were already ready. The client waited for the complete legacy
scene baseline and two presentation samples, and the browser then
waited for every declared source. The trace recorded 112 ms building
scenery meshes during the handoff. The next architecture step is to
advance a rolling surface from the exact installed collision and local
scenery receipt, publish the distant ring by cell, and keep frame and
input readiness tied to the still-live coverage. For a real walking
preview, verify input and real actor poses through loading and under
reversal, while keeping the full baseline path for login and relocation.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py <fresh-name> \
  --http-port <unused-port> --trace --travel-trace --persistent-movement \
  --persistent-collision --surface-scenery-cells --rolling-surface-window \
  --fixed-surface-draw --expanded-surface-draw-border --surface-scenery-draw --compact-scenery
```

## Repeated crossings with live coverage

The current private surface path keeps gameplay ready when an exact Player
receipt, static fence, and prepared collision window agree. The browser uses
the installed terrain coverage for readiness. It can draw each scenery cell
as its own buffer becomes ready, and the server still owns movement and
interactions. Login and deliberate relocation retain their readiness checks.

The fresh `live-surface-single-terrain-build` run at `dcf5f460e` walked from
`(239,609)` through the first handoff, continued to `(322,597)`, and returned
to `(252,609)`. The server confirmed the destination. During that walk,
Firefox recorded 3,211 fresh WebGL frames, zero held frames, zero sampled
input blocks, and zero new browser load epochs. Movement stayed in generation
1 across four terrain contexts, with zero out-of-coverage movement receipts.
All nine incoming collision cells arrived before their corresponding draw
geometry completed. The fixture checked player identity, scene ownership,
retained GPU IDs, and a staged anvil addition and removal. [Firefox capture](../../../output/tests/browser-gameplay-spike/live-surface-single-terrain-build/firefox-result.json)

The same run recorded gaps of 391 ms during lookahead, 379 ms around the
first scene receipt, and 244 ms at the second crossing. It removed the
second full terrain preparation from the prediction sequence and kept the
Java terrain build cooperative. The gaps still fail the travel target.
Browser scheduling and Java presentation intervals are recorded independently
in the capture. The native client and a real diagonal route need separate
playthroughs.

CheerpJ Java threads share their owning browser thread. A separate browser
worker can run its own Java VM. The renderer experiment sends the checked
nine-sector footprint to that worker and receives fixed-cell and compatibility
meshes built by the same terrain code. The active client still prepares
interaction data and applies the server fence. Measure worker output parity
and real frame gaps over the same route before using this path for a player
preview. [CheerpJ architecture](https://cheerpj.com/docs/explanation/architecture)

The isolated mesher at `798e9a200` returned 15 entering terrain cells across
three crossings. The fixture completed at `(252,609)` with 3,355 fresh
WebGL frames, no held frames and no input blocks. The longest gap was 379 ms.
The worker removes a source of contention during lookahead. Exact window
installation and the Java render/packing path still have work on the frame
owner. [Isolated mesher capture](../../../output/tests/browser-gameplay-spike/live-surface-isolated-mesher/firefox-result.json)

The follow-up at `fea024cbb` exposed the five entering terrain cells before
the first authoritative context changed. Firefox completed the three crossings
with 3,330 fresh frames and zero loading or input blocks. The longest
submission gap was 353 ms. The signed-map fixture produced byte-identical
terrain, walls, and roofs when using the worker footprint instead of the full
source field. [Progressive publication capture](../../../output/tests/browser-gameplay-spike/live-surface-incremental-publication/firefox-result.json)

The native run used the same route in a separate virtual display. All three
crossings completed with one movement generation and the same player instance.
It retained 18, 2, and 10 nearby NPC instances at the respective crossings,
with zero replacements of shared NPCs. The three crossing legs had maximum
Java frame gaps of 28, 27, and 27 ms. The initial approach recorded a 123 ms
gap. The disposable native account was protected from incidental combat after
an earlier run died on the route. [Native travel log](../../../output/tests/seamless-world-streaming/live-surface-native-travel/native-travel.log)

At `3288cac39`, the server referenced all nine already checked inner terrain
cells on the first ordinary handoff. The client validated each reference
against its normal resident cache or the exact acknowledged prediction. The
context then installed the same gameplay bounds with zero terrain payloads.
Login, teleports, editor terrain and resynchronization retain the full-data
path whenever their cache requires it. Firefox produced 3,274 fresh frames
over the three crossings, with zero holds, input blocks or new load epochs.
The longest frame gap remained 379 ms. [Prepared-reference capture](../../../output/tests/browser-gameplay-spike/live-surface-reference-handoff/firefox-result.json)

The separate WebGL worker at `05d53c785` acknowledged 5,203 frames, including
3,747 during the walk. The walking input gate stayed open. Its longest gap
was 403 ms at the first handoff, and the draw worker recorded 5 ms on its
last completed frame. This run leaves game-thread publication in the
latency path. The manual preview uses the established renderer.
[WebGL worker capture](../../../output/tests/browser-gameplay-spike/live-surface-render-worker/firefox-result.json)

The direction-change run at `2de74ceec` prepared `(5,12)`, then turned north
toward `(250,574)` through normal walk requests. The server replaced the
prediction with `(5,11)` while the current gameplay context and movement
generation stayed installed. Firefox recorded 695 fresh frames with zero
holds, input blocks, or new epochs. [Direction-change capture](../../../output/tests/browser-gameplay-spike/live-surface-retarget-walk/firefox-result.json)

The diagonal fixture retargeted from `(5,12)` to `(5,11)` and walked through
`(250,550)`. It withheld all nine new visual terrain meshes until after the
server and browser confirmed the crossing. The browser drew all 16 overlapping
terrain buffers under the same IDs, with nine of nine gameplay cells ready.
It recorded 1,166 fresh frames, zero holds, zero input blocks, and zero new
load epochs. After the fixture released the worker, the draw field grew back
to 25 cells. [Diagonal capture with held optional terrain](../../../output/tests/browser-gameplay-spike/live-surface-fringe-gate-proof/firefox-result.json)

The next sustained run walked through three crossings and a reversal. It
recorded 3,393 fresh Firefox frames with zero holds, input blocks, or new
load epochs. The first handoff still reused all nine authoritative terrain
references, and arrival at `(252,609)` stayed in one movement generation.
The longest gaps were 352 ms during the approach and 348 ms near the first
handoff. Animated scenery alone sent 30.3 MB of geometry between `(259,609)`
and `(278,609)`. The approach trace also captured repeated three-buffer
geometry updates totaling approximately 364 KiB each while terrain uploads
were idle. [Sustained capture with independent terrain](../../../output/tests/browser-gameplay-spike/live-surface-direct-install/firefox-result.json)

The browser can now select an immutable GPU buffer for a recurring fishing
ripple pose. The original animation computes the pose. A focused fixture
reproduced the same mesh signatures over two cycles and checked that a model
rotation invalidates its pose basis. The sustained run finished at `(252,609)`
with 3,360 fresh frames and zero holds, input blocks, or new load epochs.
By the first crossing, Firefox had reused 1,308 fishing draws after 96 pose
uploads. The remaining animated geometry still repacked a 258 KiB buffer
in the approach. The encounter contains three fishing spots, a windmill
sail and four fires. The longest submission gap was 334 ms at the first
crossing. [Resident animation capture](../../../output/tests/browser-gameplay-spike/live-surface-resident-fishing/firefox-result.json) · [Latest diagonal capture](../../../output/tests/browser-gameplay-spike/live-surface-late-draws-final/firefox-result.json)

The warmed control repeated `(239,609)` to `(257,609)` and back inside the
same gameplay coverage. After prediction and initial uploads, 1,420 fresh
Firefox frames had a maximum interval of 36 ms. Its terrain and scenery
geometry byte totals stayed steady, and animated scenery still submitted
about 376 MB. The cold approach in that same capture peaked at 342 ms.
This gives the next changes a control that exercises real movement and
renders the same scenery without a terrain handoff.
[Warmed control](../../../output/tests/browser-gameplay-spike/live-surface-steady-profile/firefox-result.json)

The dedicated browser VM also built the exact 3×3 gameplay product. Its
collision, minimap palette, roof elevations, model data and query mesh
matched the local build on the signed fixture. Firefox installed three of
those detached products on the sustained route with zero holds, sampled
input blocks, or new load epochs. The biggest gap on approach was 226 ms.
The first crossing still had a 359 ms interval between submitted frames.
[Detached gameplay capture](../../../output/tests/browser-gameplay-spike/live-surface-detached-gameplay/firefox-result.json)

A separate scenery VM built 128 staged static meshes. The client accepted
126 before the end of a three-crossing route. The local and detached builders
returned the same hash for a rotated and lit fixture model. The first
crossing still spent 134 ms on lighting in this run, with a 357 ms gap
between fresh frames. [Detached scenery capture](../../../output/tests/browser-gameplay-spike/live-surface-detached-scenery/firefox-result.json)

The fixed terrain cells now carry their occlusion fields, including a
four-tile source margin around each cell. The browser renderer copies
those fields into world coordinates. A signed-map fixture matched the
occlusion bytes from the complete source footprint. Firefox installed four
resident lighting fields and produced 3,344 fresh frames with zero holds,
input blocks, or new load epochs. The first handoff spent 18 ms on lighting
and 133 ms preparing resident objects. The longest gap was 259 ms at that
handoff. [Resident lighting capture](../../../output/tests/browser-gameplay-spike/live-surface-resident-lighting/firefox-result.json)

The native regression crossed at maximum frame intervals of 29, 27 and
27 ms while preserving one movement generation and the same player. It
retained 9, 2 and 9 continuously visible NPC instances through those
crossings with zero replacements. The test account retained the fixture's
combat protection. [Native regression](../../../output/tests/seamless-world-streaming/live-surface-native-regression/native-travel.log)

The detailed first-crossing trace recorded five static meshes inside the
shared gameplay area, totaling 120 ms of mesh construction. The server now
stages viewer-authorized scenery for those shared cells. The client keeps
unchanged placement data and compiles only the differing cells before the
handoff. The next sustained run prepared all five in advance. Its first
handoff rebuilt two animated groups in under 1 ms of measured mesh work.
The test completed with 3,594 fresh frames and zero loading holds, input
blocks, or new load epochs. Its largest displayed movement between fresh
frames was 0.83 tile. The longest frame gap was 167 ms and occurred before
the first handoff. Sprite and overlay work are still visible in the trace.
[Shared-cell scenery capture](../../../output/tests/browser-gameplay-spike/live-surface-shared-scenery/firefox-result.json)

The browser also reused scenery publications on 3,657 frames between
placement changes and reused 652 resident ripple poses across coordinate
origins. The focused placement fixture checks object and wall changes and
array compaction. Its animation counterpart checks the original ripple
phases and their rebase against a freshly built mesh.

The next diagonal regression used the same client and server with shared
scenery preparation. It reached `(250,550)` with the nine incoming visual
meshes still held. It drew nine gameplay terrain cells, retained the sixteen
overlapping buffers, and reported no synchronous late scenery meshes.
Firefox recorded 1,221 fresh frames without holds, input blocks, or new
load epochs. Its largest gap was 245 ms on the approach and the largest
displayed step was half a tile. The browser reported an AMD WebGL renderer.
[Current diagonal capture](../../../output/tests/browser-gameplay-spike/live-surface-confirmed-diagonal/firefox-result.json)

A retained UI transport trial reported a frame gap above 14 seconds. A
frame-local sprite raster cache trial stalled during preparation and missed
the fixture's scenery revision. Both experiments were removed from the
preview. These runs do not establish the cause of the delays.

Two sustained controls ran with the Java and server travel traces disabled.
Both reached the server-confirmed endpoint after three window changes and
kept one movement generation, one browser load epoch, and zero frame holds
or sampled input blocks. The first had a 3,050 ms gap between crossings.
After pausing eight idle older private test servers, the repeat sampled
3,586 fresh frames and peaked at 199 ms. That gap occurred along
`(284,609)` to `(291,597)` with no geometry uploads in the sample. The
nearby Java bridge sample spent 105 ms in the sprite path. A return leg
sample spent 124 ms in the sprite path with no geometry upload. Those
samples point to browser frame production as the next performance target.
[First presentation control](../../../output/tests/browser-gameplay-spike/live-surface-presentation-control/firefox-result.json) · [Repeat with more host headroom](../../../output/tests/browser-gameplay-spike/live-surface-headroom-control/firefox-result.json) · [Host counters](../../../output/tests/browser-gameplay-spike/live-surface-headroom-control/vmstat.log)

The first frame-pacing optimization reuses each sprite layer's source
columns across its rows and writes the browser's RGBA bytes directly. The
baseline and candidate produced byte-identical pixel and cache receipts.
Two isolated Java raster comparisons ran about 15% faster. On the same
Firefox walking route, sampled gaps of at least 100 ms went from 16 to 14
and the longest gap went from 223 to 194 ms. The candidate completed all
three crossings with zero loading holds or sampled input blocks. These
browser timings describe one matched pair of runs.
[Baseline walk](../../../output/tests/browser-gameplay-spike/live-surface-sprite-opt-baseline/firefox-result.json) · [Optimized walk](../../../output/tests/browser-gameplay-spike/live-surface-sprite-opt-candidate/firefox-result.json) · [Isolated raster timings](../../../output/tests/seamless-world-streaming/browser-sprite-raster-matching/benchmark.log)

The finer stage trace separated sprite raster work, native atlas planning
and submission, overlay preparation, world progress, draw selection, and
presentation. A trial skipped the separate atlas plan when the last frame
already owned every current sprite. It applied to 123 of 3,721 atlas
submissions and was removed after the route showed slightly more long gaps.
The next change combines readiness and the full overlay plane with the live
frame submission, using the existing source and overlay validation. Loading,
retained images, and overlay resets still use their separate declarations.
The combined run batched both calls on 3,257 frames and completed all three
crossings and the reversal on one epoch with zero holds or sampled input
blocks. Compared with the stage baseline, the longest gap went from 231 to
182 ms and gaps of at least 100 ms went from 14 to 16. The matched pair shows
a smaller worst gap and limited evidence about overall pacing.
[Stage baseline](../../../output/tests/browser-gameplay-spike/live-surface-frame-stage-baseline/firefox-result.json) · [Atlas trial](../../../output/tests/browser-gameplay-spike/live-surface-resident-atlas-candidate/firefox-result.json) · [Combined frame capture](../../../output/tests/browser-gameplay-spike/live-surface-inline-overlay-retry/firefox-result.json)

The same build passed the delayed diagonal route with the WebGL render
worker. It crossed while the nine optional terrain meshes were held and
reported 206 frames carrying both declarations. The walk had zero holds,
input blocks, or new load epochs. [Render worker capture](../../../output/tests/browser-gameplay-spike/live-surface-inline-overlay-worker-proof/firefox-result.json)

#### Follow-up frame pacing checks

The presentation control now collects the browser backend's individual
world submission intervals and separately observes browser animation frame
callbacks. The 960×540 Firefox reference recorded a median of 18 ms, a
95th percentile of 30 ms, a 99th percentile of 62 ms, and a maximum of
220 ms. All 3,644 intervals were observed.
[Firefox reference](../../../output/tests/browser-gameplay-spike/live-surface-pacing-baseline/firefox-result.json)

| Candidate | 95th percentile | 99th percentile | Longest gap | Result |
| --- | ---: | ---: | ---: | --- |
| Compact animation geometry | 35 ms | 68 ms | 220 ms | Removed. A repeated upload fell from 258 KiB to 116 KiB while frame times worsened. |
| 30 FPS presentation cap | 47 ms | 90 ms | 306 ms | Removed. The client kept its update cadence and the presentation gaps grew. |
| 768×432 render surface | 29 ms | 60 ms | 263 ms | Removed. Typical gains were small and the worst gap grew. |
| Chrome with the Radeon renderer at 960×540 | 22.5 ms | 64.2 ms | 235.6 ms | Typical frames improved. The long gaps persisted. |

Each timed candidate completed all three crossings with zero loading holds
or sampled input blocks. CheerpJ's experimental WasmGC run exited before
login and produced no frame timing for this client.
[Compact animation](../../../output/tests/browser-gameplay-spike/live-surface-compact-animation/firefox-result.json) · [30 FPS](../../../output/tests/browser-gameplay-spike/live-surface-half-rate-candidate/firefox-result.json) · [Reduced surface](../../../output/tests/browser-gameplay-spike/live-surface-reduced-resolution/firefox-result.json) · [Chrome](../../../output/tests/browser-gameplay-spike/live-surface-chrome-pacing/chrome-result.json) · [WasmGC startup](../../../output/tests/browser-gameplay-spike/live-surface-wasmgc-trial/firefox-result.json)

The private layout candidate measured the GPU canvas and its AWT reference
only when the layout changed. In Firefox it measured 5 of 5,089
alignment requests. The route completed at 960×540 with zero holds or
sampled input blocks. Its maximum gap was 179 ms, with a 95th percentile
of 31 ms and a 99th percentile of 66 ms. The smaller maximum appeared
alongside slightly higher frame intervals at both percentiles. In Chrome
it measured 5 of 5,424 requests, with a 95th percentile of 22.3 ms and a
99th percentile of 63.3 ms. The longest gap grew from 235.6 to 268.9 ms.
The pacing gains varied by browser and by metric, so the layout trial was
removed.
[Firefox layout candidate](../../../output/tests/browser-gameplay-spike/live-surface-cached-layout/firefox-result.json) · [Chrome layout candidate](../../../output/tests/browser-gameplay-spike/live-surface-cached-layout-chrome/chrome-result.json)

#### Full HD world output with the original UI

The browser uses two sizes. The WebGL drawing buffer follows the fitted
display and device-pixel ratio up to 1920×1080. The game's UI, pointer
coordinates, and camera projection still use 960×540. The compositor maps
logical UI texels into the physical drawing buffer. This is the existing
browser display policy. [Physical sizing](../../../tools/browser-gameplay/web/browser-physical-output.js) · [UI compositor](../../../tools/browser-gameplay/web/browser-overlay.js) · [Pointer mapping](../../../tools/browser-gameplay/web/browser-display.js)

Both output sizes upload the same 4,147,200 bytes for the two logical UI
planes on each full overlay upload. A 1920×1080 logical UI would multiply
that payload by four and would need its own scaling and hit-testing work.
The existing Full HD setting does increase the size of the physical world
buffer, the composite pass, and the retained world image. The two retained
textures occupy 16,588,800 bytes at 1920×1080 and 4,147,200 bytes at
960×540. [Retained frame](../../../tools/browser-gameplay/web/browser-retained-frame.js)

For a matched comparison, stage a fresh private presentation-only
route per run with `--frame-stages --safe-walk-fixture`. Run
`run-browser-travel.py <run> --output logical` and
`run-browser-travel.py <run> --output full-hd` in the same browser. Use
`--browser chrome` to repeat in Chrome. The runner checks that each
output size holds throughout the walk. The optional stage recorder
measures wall time around the upload, draw, and copy calls on the
JavaScript side. Those timings exclude legacy Java UI drawing and
cannot resolve separate GPU completion times. Each run retains 600
samples for its stage summaries. Frame gaps measure
fresh world submissions in headless browsers.

| Browser | WebGL output | Median gap | 95th percentile | 99th percentile | Longest gap | Gaps ≥100 ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Firefox | 960×540 | 18 ms | 32 ms | 71 ms | 180 ms | 19 |
| Firefox | 1920×1080 | 18 ms | 34 ms | 70 ms | 195 ms | 16 |
| Chrome | 960×540 | 16.9 ms | 23.7 ms | 73 ms | 196.1 ms | 18 |
| Chrome | 1920×1080 | 17 ms | 23.8 ms | 64.9 ms | 281.6 ms | 18 |

All four walks completed three streaming rolls and the reversal with one
load epoch and zero held frames or sampled input closures. Each retained its
intended output size throughout the measured walk. The disposable test account
had combat damage protection. Frame pacing still misses the 60 FPS target.
[Firefox 960×540](../../../output/tests/browser-gameplay-spike/live-surface-ui-firefox-logical/firefox-result.json) · [Firefox 1920×1080](../../../output/tests/browser-gameplay-spike/live-surface-ui-firefox-full-hd/firefox-result.json) · [Chrome 960×540](../../../output/tests/browser-gameplay-spike/live-surface-ui-chrome-logical-exact/chrome-result.json) · [Chrome 1920×1080](../../../output/tests/browser-gameplay-spike/live-surface-ui-chrome-full-hd-exact/chrome-result.json)

The four JavaScript overlay stages together had a 3 ms median and 4 ms 95th
percentile at both sizes in Firefox. The Full HD run's 195 ms gap had zero
world geometry uploads, 3 ms in the nearby JavaScript overlay sample, and
92 ms in the Java bridge's actor sprite atlas upload phase. That Java phase
includes buffer packing and can include waiting on the browser callback.
Firefox provided no GPU timer samples for these runs.

Chrome measured a 1.2 to 1.3 ms median for the same JavaScript stages.
Median timed WebGL work rose from 2.27 ms to 3.52 ms at Full HD.
The largest Full HD gap had zero world geometry uploads and 1.7 ms in
the nearby JavaScript overlay sample. The four runs show that the world
buffer size adds measurable GPU work in Chrome and that long gaps occur
at both sizes. Repeated comparisons would be needed to attribute a
change in worst-case frame intervals to resolution.

A separate Radeon/WebGL pixel check in Firefox passed all 47 cases.
The added Full HD case used patterned RGB and partial transmittance at
960×540 and checked the resulting 1920×1080 frame against a CPU-expanded
reference. Across the whole suite, 31,026,120 compared bytes matched
exactly. The display mapping suite also passed its logical input checks.
These tests cover composition and input mapping, alongside the walking
test's checks on the world input gate. [Pixel comparison](../../../output/tests/browser-physical-output/hardware/live-surface-1080-ui-parity-full-size/firefox-result.json) · [Display tests](../../../tests/myworld/test-browser-display.mjs)

Prioritize the actor sprite handoff when investigating the long gaps.
Time atlas planning, Java buffer packing, and WebGL uploads on both
sides of the bridge and correlate them with the same frame. Time the
Java UI raster separately with panels open before choosing a UI
optimization. If that work contributes materially, prototype one
persistent UI panel rendered from stable assets and state changes.
Keep logical layout and hit targets independent of the world buffer.
The existing rectangle and glyph journal can help verify pixels.
It observes the legacy raster and leaves sprites, framebuffer reads,
and minimap raster operations to the existing path.
[Sprite handoff](../../../PC_Client/src/orsc/BrowserWebGLBridge.java#L994) · [Java UI timing boundary](../../../Client_Base/src/orsc/mudclient.java#L10328) · [Overlay journal](../../../tools/browser-gameplay/web/browser-overlay-journal.js)

#### Actor sprite handoff and open UI panels

The private frame-stage trace now measures Java descriptor and pixel packing,
elapsed time around the Java native calls, JavaScript atlas planning and
commit work, and the WebGL upload calls. The runner matches these measurements
to the submitted world frame when both receipts are available. The native-call
times can include time waiting for the browser to run or resume the call.
The WebGL upload timing measures CPU wall time around calls.

The Full HD control opened each panel for six seconds over the same stationary
scene. The two-item inventory and minimap were measured through their actual
Java draw branches. The private fixture cleared the first-login dialogs that
cover both panels and restored the original panel state before walking.

| Browser | Closed UI median | Inventory panel median | Minimap panel median |
| --- | ---: | ---: | ---: |
| Firefox | 1 ms | 2 ms | 8 ms |
| Chrome | 0.8 ms | 1.1 ms | 21.5 ms |

The closed column measures the full Java UI draw with the side panels closed.
The other columns measure the individual panel draws. Larger inventories and
other panels need their own measurements. [Firefox panel control](../../../output/tests/browser-gameplay-spike/live-surface-sprite-ui-firefox-panels/firefox-result.json) · [Chrome panel control](../../../output/tests/browser-gameplay-spike/live-surface-sprite-ui-chrome-panels/chrome-result.json)

Some walking hitches had a long native-call receipt while packing and atlas
work were short. One 135 ms Firefox gap had 84 ms around the plan call,
no measured JavaScript planning time at the 1 ms timer precision, and zero
world geometry uploads. One 86.5 ms Chrome gap had 43.7 ms around the plan and
commit calls and about 0.2 ms inside their JavaScript bodies. Other long gaps
had short sprite receipts or lacked a matched receipt. The data identifies
time around the handoff as a lead for some hitches.

The direct-return trial used the same Java build and identical JavaScript
callback bodies with the `async` declarations removed from sprite plan and
commit in two private stages. It completed all three streaming rolls in both
browsers. Firefox's 95th and 99th-percentile gaps went from 34 and 66 ms to
33 and 68 ms. Chrome's went from 23.7 and 62 ms to 24.1 and 65.5 ms. The
trial still recorded long Java elapsed times while the atlas callbacks were
short. The standard callbacks remain in use. [Firefox direct return](../../../output/tests/browser-gameplay-spike/live-surface-sprite-ui-firefox-direct/firefox-result.json) · [Chrome direct return](../../../output/tests/browser-gameplay-spike/live-surface-sprite-ui-chrome-direct/chrome-result.json)

#### Minimap pinned during sustained travel

The pinned-minimap baseline measured the full panel and its rotated map
image separately. In the last 600 reported frames, the panel's 95th-percentile
Java draw took 6 ms in Firefox and 14.7 ms in Chrome. The rotated image
accounted for 1 ms and 0.3 ms at the same percentile. Most of the panel work
is outside the rotated-image draw. [Firefox baseline](../../../output/tests/browser-gameplay-spike/live-surface-minimap-raster-firefox/firefox-result.json) · [Chrome baseline](../../../output/tests/browser-gameplay-spike/live-surface-minimap-raster-chrome/chrome-result.json)

A trial batched opacity writes for only the unmasked rotated scanlines.
The RGB and coverage digests matched the baseline across 1,536 combinations
of angle, zoom, clipping, and interlace. The pinned walking 95th-percentile
gap moved from 45 to 43 ms in Firefox and from 53.5 to 50.9 ms in Chrome.
The Firefox median was unchanged and its longest gap grew from 225 to
277 ms. The rotated scanline change was removed. [Pixel proof](../../../output/tests/browser-minimap-pixels/rotated-coverage/candidate.txt) · [Firefox trial](../../../output/tests/browser-gameplay-spike/live-surface-minimap-coverage-firefox/firefox-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/live-surface-minimap-coverage-chrome/chrome-result.json)

A second trial filled each solid rectangle row in bulk. It matched the
baseline RGB and coverage digests with the panel background included.
The median walking gap stayed at 24 ms in Firefox and 27.2 ms in Chrome.
The Chrome 99th-percentile gap went from 112.9 to 118 ms. Firefox's
stationary minimap draw stayed at 8 ms and Chrome's changed from 23.6
to 22.7 ms. The change was removed after the paired runs showed limited
pacing benefit. Both trials kept the original logical layout. The
pinned walking tests completed three streaming rolls and a reversal
with one load epoch and zero loading holds or sampled input closures.
[Pixel proof](../../../output/tests/browser-minimap-pixels/opaque-panel-fill/candidate.txt) · [Firefox fill trial](../../../output/tests/browser-gameplay-spike/live-surface-minimap-fill-firefox/firefox-result.json) · [Chrome fill trial](../../../output/tests/browser-gameplay-spike/live-surface-minimap-fill-chrome/chrome-result.json)

The next minimap investigation should time the marker loops. The
scenery loop walks the installed object list, projects each object and
sends five individually clipped pixels per dot. If that loop dominates
the panel time, use the existing scenery placement revision to keep a
local marker index and visit only the visible area. Keep live NPC,
player and ground-item dots current. Exercise marker overlap, clipping,
object updates at a streaming boundary and minimap click coordinates.
The actor handoff remains a separate performance lead when the minimap
is closed. [Marker drawing](../../../Client_Base/src/orsc/mudclient.java#L15970) · [Placement revision](../../../Client_Base/src/orsc/ClientSceneInstanceStore.java#L85)

Use `--frame-stages --profile-panels` with the private sustained
presentation control to capture the three stationary UI states before
walking. Add `--profile-minimap-walk` to leave the minimap pinned across
the route. Pass `--dependency-jar` to stage a specific client build
and use `--output full-hd` when running the browser capture.

#### Client-loop and render handoff timing

The current performance investigation uses ordinary travel with the
side panels closed. UI modernization is tracked with Rebirth. The
private frame-stage trace now records requested and actual client-loop
wait, completed updates, Java scene preparation, time in the browser
bridge before presentation, and WebGL submission. It saves the timing
of each slow submission from the renderer's own frame counter. These
receipts survive a delayed browser animation callback. Nested phase
times and elapsed times around native calls can overlap.

At Full HD, a 190 ms Firefox submission interval had 166 ms of Java
bridge time before presentation and 4 ms of JavaScript submission
time. It had zero geometry uploads. A 229 ms Chrome interval had
162 ms in the Java bridge before presentation, 2.9 ms submitting
WebGL work, and zero geometry uploads. Other long frames accumulated
work in game updates and scene capture. The long intervals generally
occurred without a requested wait from the frame scheduler.
[Firefox trace](../../../output/tests/browser-gameplay-spike/live-surface-loop-trace-firefox/firefox-result.json) · [Chrome trace](../../../output/tests/browser-gameplay-spike/live-surface-loop-trace-chrome/chrome-result.json)

An environment bundling trial moved lighting and sky declarations
into the already verified live frame submission. It eliminated two
separate native calls per bundled frame, on 3,155 Firefox frames and
3,409 Chrome frames. The 95th and 99th-percentile intervals moved from
34 and 68 ms to 33 and 65 ms in Firefox, and from 23.9 and 67.2 ms to
24.3 and 64.3 ms in Chrome. The number of intervals of at least 100 ms
rose from 14 to 17 in Firefox and from 15 to 16 in Chrome. There was no
consistent pacing gain, and the trial was removed. Both controls and
both trials completed three streaming rolls and a reversal with zero
loading holds or sampled input closures.
[Firefox trial](../../../output/tests/browser-gameplay-spike/live-surface-environment-bundle-firefox/firefox-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/live-surface-environment-bundle-chrome/chrome-result.json)

An additional Firefox run used the Gecko profiler over the same real
walk. It was a diagnostic run and recorded longer gaps than the
unprofiled control. The 336 ms interval contained 164 CheerpJ runtime
stacks out of 168 content-main-thread samples, 43 ms intersecting
garbage collection or cycle collection slices, and 3 ms intersecting
WebGL message spans. In the 271 ms interval with zero geometry uploads,
131 of 136 samples contained CheerpJ runtime frames. These overlaps do
not identify a particular Java method or account for the entire gap.
CheerpJ schedules Java threads on a single browser thread in the same
runtime. Java jobs using an executor can still compete with rendering
there. Actual browser workers already handle the detached terrain and
scenery mesh paths. Time the remaining Java work feeding the renderer
by job category before choosing a worker or allocation change.
[Firefox profile correlation](../../../output/tests/browser-gameplay-spike/live-surface-loop-gecko-profile/profile-correlation.json) · [Gecko capture route](../../../output/tests/browser-gameplay-spike/live-surface-loop-gecko-profile/firefox-result.json) · [CheerpJ threading](https://cheerpj.com/docs/explanation/architecture)

Add `--firefox-profile` to the private travel runner for a separate
browser diagnostic capture. It starts when the first walk leg is
observed and saves the profile in the private run directory before
closing Firefox. Use a separate unprofiled run for the pacing
comparison.

#### Move canonical scenery preparation into the worker

The matched control timed 133 detached scenery requests per browser. All jobs
together sent 9.80 MiB of prepared models and returned 96.80 MiB of meshes.
The response for the same dense cell exceeded the 4 MiB acceptance limit twice
per run and used local meshing. Successful scenery adoption through the
resident cache took at most 1 ms in Firefox.

| Browser | Build canonical models p50 / p95 | Encode request p50 / p95 | Decode response p50 / p95 |
| --- | ---: | ---: | ---: |
| Firefox | 6 / 39 ms | 6 / 20 ms | 7 / 22 ms |
| Chrome | 13.6 / 68.4 ms | 4.2 / 14 ms | 3.2 / 13 ms |

Canonical model preparation or request encoding overlapped 9 of the 16
sampled Firefox gaps of at least 100 ms, and 13 of the 15 Chrome gaps. These
are elapsed-time overlaps. The timings can include runtime scheduling and
garbage collection. The separate native-call duration includes waiting for
the worker and should not be counted as client-thread CPU time.
[Firefox control](../../../output/tests/browser-gameplay-spike/scenery-handoff-baseline-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/scenery-handoff-baseline-chrome/chrome-result.json)

The private placement candidate sends canonical placements, elevations, and
their content revision before constructing models. The scenery worker matches
the installed model archive by SHA-256, then keeps at most 64 source templates.
It uses the same model construction as the installed builder. Existing cell
revision and placement checks govern publication. Unsupported models and
worker refusals use the existing builders.

Firefox and Chrome each matched 127 complete serialized cell meshes,
including geometry, materials, shadows, and glow data. Both checked live
anvil revisions before the first roll and completed three rolls and a
reversal with zero loading holds or input closures. The parity runs build
each checked cell twice. The timed checks run separately.
[Firefox mesh parity](../../../output/tests/browser-gameplay-spike/scenery-handoff-parity-firefox/firefox-result.json) · [Chrome mesh parity](../../../output/tests/browser-gameplay-spike/scenery-handoff-parity-chrome/chrome-result.json)

In both timing runs, 127 of 133 jobs used placements. Total request size
fell from 9.80 to 1.28 MiB. Placement packing took 1 ms in Firefox and
0.3 ms in Chrome at the 95th percentile. Response size stayed at 96.80
MiB, and the dense cell still required local meshing. The first worker
call took about 2 seconds with the catalog cold, up from 0.54 seconds in
Firefox and 0.59 seconds in Chrome in the control. Warm median worker
turnaround was 19.5 ms in Firefox and 16.6 ms in Chrome, including the
response transfer.

| Browser | p95 interval | p99 interval | Longest interval | Intervals at least 100 ms |
| --- | ---: | ---: | ---: | ---: |
| Firefox | 36 → 29 ms | 73 → 49 ms | 211 → 245 ms | 16 → 11 |
| Chrome | 26.8 → 22.2 ms | 63.5 → 43.1 ms | 211.5 → 191.8 ms | 15 → 12 |

Both matched pairs used 1920×1080 world output and the 960×540 logical
UI. The candidate completed every route with one movement generation,
three rolls, and the server-confirmed return to `(252,609)`. The count of
intervals at least 100 ms fell from six to three on the return leg in
Firefox and from five to three in Chrome. These are single matched pairs.
The 245 ms Firefox outlier still spent 142 ms in the Java bridge before
presentation and 3 ms in JavaScript submission. No canonical model build
or packet packing was measured during that gap. The candidate is
available through `--worker-scenery-placements` for the private preview.
[Firefox placement timing](../../../output/tests/browser-gameplay-spike/scenery-handoff-candidate-firefox/firefox-result.json) · [Chrome placement timing](../../../output/tests/browser-gameplay-spike/scenery-handoff-candidate-chrome/chrome-result.json)

#### Count fresh frames during scenery completion bursts

Grouping the placement preview's fresh presentations into complete one-second
windows shows what the player feels during a walk. Both browsers reached
59–60 FPS in stretches without scenery jobs. Near four seconds into the
route Firefox presented 19 frames as 19 scenery results returned 13.8 MiB.
Chrome presented 16 frames as 14 results returned 16.9 MiB. The same route
showed a second dip in both browsers near the crossing on the return leg.

| Browser | Median FPS in seconds without a scenery result | Median FPS in seconds with at least 1 MiB returned |
| --- | ---: | ---: |
| Firefox | 55 | 34 |
| Chrome | 60 | 44.5 |

The per-job timings put about 170 ms in Firefox's response decoder during
its worst second. Chrome's worst second included about 261 ms preparing and
encoding legacy requests, along with 78 ms decoding responses. These are
elapsed timings and can contain runtime scheduling. Both traces also have
isolated gaps above 100 ms with zero geometry uploads and no scenery worker
request in progress. Streaming bursts and ordinary frame production both
affect the feel of the walk.
[Firefox placement timing](../../../output/tests/browser-gameplay-spike/scenery-handoff-candidate-firefox/firefox-result.json) · [Chrome placement timing](../../../output/tests/browser-gameplay-spike/scenery-handoff-candidate-chrome/chrome-result.json)

The actor sprite trial sent new exact image pixels and current quads in one
call. It used a second call when atlas compaction needed pixels from the
preceding frame. Firefox combined 3,801 submissions with four retries.
Chrome combined 4,102 with five retries. Both passed the walking route.
Tests of atlas state covered exact pixels, draw order, shared image IDs, and
the compaction retry. The table compares one matched pair per browser.

| Browser | p95 frame interval | p99 frame interval | Seconds below 50 FPS | Lowest one-second FPS |
| --- | ---: | ---: | ---: | ---: |
| Firefox | 31 → 29 ms | 51 → 48 ms | 33 → 25 | 18 → 16 |
| Chrome | 23.1 → 23.9 ms | 43.3 → 43.2 ms | 9 → 11 | 23 → 21 |

The combined call moved more than 40 MiB of sprite pixels into JavaScript
per run due to sending images that could still be resident after leaving
the preceding frame. The lowest one-second rates stayed in the teens and
twenties. The trial was removed, and the one-second pacing measurement is
now part of the sustained route runner.
[Firefox control](../../../output/tests/browser-gameplay-spike/sprite-submit-control-firefox-retry/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/sprite-submit-trial-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/sprite-submit-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/sprite-submit-trial-chrome/chrome-result.json)

#### Flat scenery replies and worker-packed draws

The flat reply carries the exact static triangle soup in one bounded typed
buffer. It preserves texture coordinates, normals, materials, shadow casters,
glow emitters, and the mesh signature. The client validates the cell origin,
section, revision and shape before creating the mesh. The format rebuilds
the sequential indices and empty terrain fields of the existing static
scenery builder without running its mesh construction again.

The Firefox parity route matched all 127 detached placement meshes. The
ordinary timed route returned 73.31 MiB including legacy responses, down
from about 96.80 MiB in the prior placement capture. Firefox's elapsed
placement decode time across the route fell from 934 to 172 ms. The
95th-percentile placement decode fell from 19 to 4 ms. These captures
completed the same walk at 1920×1080 with a 960×540 logical UI, a reversal,
three rolls, and zero sampled input closures or loading holds.

| Browser | p95 interval | p99 interval | Longest interval | Lowest one-second FPS |
| --- | ---: | ---: | ---: | ---: |
| Firefox | 29 → 28 ms | 49 → 42 ms | 245 → 163 ms | 19 → 19 |
| Chrome | 22.2 → 21.7 ms | 43.1 → 38 ms | 191.8 → 176.9 ms | 16 → 28 |

[Flat reply parity](../../../output/tests/browser-gameplay-spike/scenery-words-parity/firefox-result.json) · [Firefox flat reply](../../../output/tests/browser-gameplay-spike/scenery-words-firefox/firefox-result.json) · [Chrome flat reply](../../../output/tests/browser-gameplay-spike/scenery-words-chrome/chrome-result.json)

The separate `--packed-scenery` trial also returns compact vertices, indices
and material batches directly to WebGL. The queue uses exact cell identity
and texture/renderer settings, with a 24 MiB and 128-cell bound and a 1 MiB
packet cap. The parity runs matched 69 uploaded packets in Firefox and
83 in Chrome against local packing. The timing runs used 81 and 82 worker
packets respectively. The flat-mesh reply was still transferred alongside
each packed packet for the existing lighting metadata and CPU consumers.

| Browser | p95 interval | p99 interval | Longest interval | Lowest one-second FPS |
| --- | ---: | ---: | ---: | ---: |
| Firefox | 28 → 27 ms | 42 → 44 ms | 163 → 245 ms | 19 → 16 |
| Chrome | 21.7 → 21.6 ms | 38 → 37 ms | 176.9 → 264.3 ms | 28 → 20 |

The worker packing proof is recorded at `99dd73dab`. Carrying two complete
representations consumed additional transfer capacity and the worst seconds
regressed in both controls, so the duplicate-packed path was removed. The
flat reply and exact cell reuse are the pacing candidates under test.
[Firefox packed trial](../../../output/tests/browser-gameplay-spike/scenery-packed-firefox/firefox-result.json) · [Chrome packed trial](../../../output/tests/browser-gameplay-spike/scenery-packed-chrome/chrome-result.json)

Two dense distant jobs previously selected the serialized fallback because
the cell included a canonical windmill sail. Its 5.15 MB result exceeded the
legacy limit and forced another local build. The primitive reply includes
the exact distant model in 3.72 MB. The playable scene still handles the
live windmill animation through its existing path. The last three legacy
jobs included the generated, empty air-glyph source. Sharing that precise
placeholder recipe let the final Firefox parity route build all 133 jobs
from placements and compare every result. That route returned 69.81 MiB
and sent 114.60 KiB of input data over the entire session.
[Dense cell parity](../../../output/tests/browser-gameplay-spike/scenery-dense-parity/chrome-result.json) · [All placement sources](../../../output/tests/browser-gameplay-spike/scenery-all-placement-parity/firefox-result.json)

Repeated cell keys accounted for 53 of 133 jobs in the flat-reply route.
The private reuse trial retains signed-world and model-scope meshes within
a 64 MiB and 96-cell bound. It checks sorted placements and elevations,
then rebases geometry and shadow footprints to the current view. Fresh
authoritative placements still determine what can be drawn. The parity
run independently rebuilt all 38 reused meshes and matched the geometry,
lighting coordinates and shadows. The trial skipped 38 jobs and cut
scenery replies from 69.81 to 54.05 MiB. Firefox's 99th-percentile interval
moved from 41 to 38 ms and Chrome's moved from 33 to 32.3 ms. The lowest
one-second rates were 23 and 33 FPS, compared with 24 and 29 FPS in the
flat-reply controls. The reuse option remains separate from the terrain
comparison.
[Reuse parity](../../../output/tests/browser-gameplay-spike/scenery-shadow-reuse-parity/chrome-result.json) · [Firefox reuse trial](../../../output/tests/browser-gameplay-spike/scenery-shadow-reuse-firefox/firefox-result.json) · [Chrome reuse trial](../../../output/tests/browser-gameplay-spike/scenery-shadow-reuse-chrome/chrome-result.json)

#### Terrain delivery and gameplay preparation

The terrain worker returns two independent products. One installs the
collision, minimap, structures and query mesh for a gameplay context.
The other prepares entering terrain draw cells and their compatibility
meshes. The signed-source fixture compared both draw meshes byte for byte,
including prepared lighting. The browser fixture consumed 15 primitive
draw replies. Their median decode was 2 ms and the longest was 3.8 ms in
the detailed Chrome capture. The three serialized gameplay replies took
48–177 ms each in that capture. The worker's asynchronous build and
transfer wait are recorded separately.
[Terrain receipt capture](../../../output/tests/browser-gameplay-spike/scenery-shadow-reuse-parity/chrome-result.json)

In separate untraced draw-only trials the Firefox 99th-percentile frame
interval moved from 41 to 39 ms, with the lowest second moving from 24 to
23 FPS. Chrome's 99th percentile moved from 33 to 33.5 ms, with the lowest
second moving from 29 to 33 FPS. The larger gameplay reply remains the
priority. Its separate primitive reply carries exact terrain tiles, both
grids, minimap palette data, model arrays and the complete query mesh.
The signed-source fixture compares these products against the prior
serializer and rejects stale or truncated reply envelopes.
[Firefox draw trial](../../../output/tests/browser-gameplay-spike/terrain-reply-words-firefox/firefox-result.json) · [Chrome draw trial](../../../output/tests/browser-gameplay-spike/terrain-reply-words-chrome/chrome-result.json) · [Signed-source parity](../../../tests/myworld/seamless-streaming/SurfaceMeshSourceFixture.java)

Firefox accepted all three gameplay replies on both runs. In the matched
diagnostic, gameplay decode intervals changed from 127, 60 and 58 ms to
36, 16 and 15 ms. Total gameplay replies fell from 25.66 to 23.51 MiB.
The largest measured frame interval overlapping a gameplay decode fell
from 161 to 72 ms. The traced route's largest overall interval was 154 ms
and occurred after the second decode, with zero geometry upload bytes.
The sprite commit accounted for most of the measured Java bridge time
in that interval. That timing includes time while CheerpJ can run other
Java work. The untraced Firefox route reported a 37 ms 99th-percentile
interval, a 141 ms longest interval and a 29 FPS lowest full second.
[Firefox serialized trace](../../../output/tests/browser-gameplay-spike/terrain-reply-control-trace/firefox-result.json) · [Firefox primitive trace](../../../output/tests/browser-gameplay-spike/terrain-gameplay-words-trace/firefox-result.json) · [Firefox untraced walk](../../../output/tests/browser-gameplay-spike/terrain-gameplay-words-firefox/firefox-result.json)

The untraced Chrome trial also accepted all three gameplay replies and
15 draw replies. Its 99th-percentile frame interval was 34.7 ms, its
longest interval was 160.2 ms and its lowest complete second was 28 FPS.
The draw-only control recorded 33.5 ms, 181.4 ms and 33 FPS respectively.
Both browsers finished the route with three gameplay rolls, one
movement generation, and zero holds, input blocks or new load epochs.
The delayed diagonal proof turned away from the already prepared
destination and crossed with all nine gameplay cells ready while new
fringe geometry was held. It retained the 16 overlapping GPU buffers.
[Chrome untraced walk](../../../output/tests/browser-gameplay-spike/terrain-gameplay-words-chrome/chrome-result.json) · [Delayed diagonal proof](../../../output/tests/browser-gameplay-spike/terrain-flat-held-diagonal/firefox-result.json)

The synchronous sprite atlas trial kept both atlas calls and their
validation and removed the Promise handoff around their direct WebGL
bodies. Firefox used 7,654 synchronous calls and Chrome used 8,344.
Firefox's lowest complete second moved from 29 to 28 FPS and its
99th-percentile frame interval stayed at 37 ms. Chrome's lowest second
moved from 28 to 35 FPS and its 99th percentile from 34.7 to 32.3 ms.
Firefox had three additional seconds below 50 FPS. The trial was
removed after the browsers disagreed on its pacing benefit.
[Firefox atlas dispatch trial](../../../output/tests/browser-gameplay-spike/atlas-sync-firefox/firefox-result.json) · [Chrome atlas dispatch trial](../../../output/tests/browser-gameplay-spike/atlas-sync-chrome/chrome-result.json)

The paced gameplay decode trial yielded 8, 4 and 4 times while
reconstructing the same three replies, publishing each only when
complete. The worst overlap with a frame gap changed from 72 to
70 ms, and the lowest complete second stayed at 27 FPS. Decode
elapsed time grew from 36, 16 and 15 ms to 62, 25 and 34 ms. The
bounded yield experiment was removed after this result.
[Paced decode trace](../../../output/tests/browser-gameplay-spike/gameplay-reply-paced-trace/firefox-result.json)

#### WebGL worker at Full HD

After the terrain reply changes, the existing WebGL worker improved the
full walking route in both browsers. These runs used the normal applet
client and its existing input path. Each finished all three scene rolls
and the reversal with zero holds or sampled input closures.

| Browser | p99 frame interval, direct → worker | Lowest full second | Seconds below 50 FPS |
| --- | ---: | ---: | ---: |
| Firefox | 37 → 32 ms | 29 → 34 FPS | 23 → 6 |
| Chrome | 34.7 → 27.6 ms | 28 → 41 FPS | 8 → 5 |

The Firefox worker completed 4,204 atlas plans and 5,543 frames across
startup and travel. It moved 20.75 GB through its transferable-buffer
pool and recorded 841 backpressure waits. A comparison used the
worker's confirmed atlas inventory to batch resident-only plans with
their commits. The worker still validated every plan and received
fresh quads in the original order. It skipped 971 round trips in
Firefox and 1,491 in Chrome. The lowest seconds rose from 34 to 39
and 41 to 43 FPS respectively. The 99th percentiles rose from 32
to 33 ms and 27.6 to 28.5 ms. Firefox spent two additional seconds
below 50 FPS, and Chrome spent one fewer. The candidate was removed
after the pacing results remained mixed.
[Firefox render worker](../../../output/tests/browser-gameplay-spike/awt-client-render-worker-firefox/firefox-result.json) · [Chrome render worker](../../../output/tests/browser-gameplay-spike/awt-client-render-worker-chrome/chrome-result.json)
[Firefox atlas inventory trial](../../../output/tests/browser-gameplay-spike/sprite-lease-firefox/firefox-result.json) · [Chrome atlas inventory trial](../../../output/tests/browser-gameplay-spike/sprite-lease-chrome/chrome-result.json)

The canvas alignment trial used observed layout changes to bypass
96–98 percent of the coordinate measurements. Both browsers matched
the AWT canvas geometry before and after resizing. Firefox's 99th
percentile went from 32 to 33 ms and its lowest second from 34 to
31 FPS. Chrome's 99th percentile went from 27.6 to 27.1 ms and its
lowest second from 41 to 39 FPS. The trial was removed because the
reduction in layout reads did not carry through to pacing.
[Firefox alignment trial](../../../output/tests/browser-gameplay-spike/observed-alignment-firefox/firefox-result.json) · [Chrome alignment trial](../../../output/tests/browser-gameplay-spike/observed-alignment-chrome/chrome-result.json)

The traced Firefox run forwarded the normal render worker's exact frame
stages. Two of the largest walking gaps contained 111 and 122 ms from
the Java atlas plan call, with under 1 ms spent inside the worker's
planner. Other gaps spent most of their time in game updates and scene
capture. The atlas result includes worker queue and Java scheduling
time. Moving the deterministic allocation plan next to the client lets
Java receive the missing IDs immediately. The WebGL worker still
calculates and checks the same ordered IDs before uploading any sprite
pixels for that plan.
[Render worker stage trace](../../../output/tests/browser-gameplay-spike/current-render-worker-profile-firefox/profile-phase-summary.json)

The client-side planner removed 4,232 Firefox and 4,275 Chrome plan
round trips across startup and travel. The route again finished with
three scene rolls, one epoch, and zero holds or sampled input closures.
The Firefox worst second rose from 34 to 39 FPS and the 99th percentile
improved from 32 to 31 ms. Chrome stayed at 41 FPS and 27.6 ms, with
three seconds below 50 FPS instead of five. The number of Chrome gaps
over 100 ms rose from two to five. The Radeon oracle compared 27,915,808
bytes in each browser across 50 cases, including atlas resets and
resource retention after reusing the transfer buffers. All RGBA bytes
matched.
[Firefox walking capture](../../../output/tests/browser-gameplay-spike/local-atlas-plan-firefox/firefox-result.json) · [Chrome walking capture](../../../output/tests/browser-gameplay-spike/local-atlas-plan-chrome/chrome-result.json) · [Firefox pixel proof](../../../output/tests/browser-render-worker-oracle/local-atlas-plan-parity/firefox-hardware-result.json) · [Chrome pixel proof](../../../output/tests/browser-render-worker-oracle/local-atlas-plan-parity/chrome-hardware-result.json)

The Firefox repeat improved the worst second from 36 to 40 FPS and
the 99th percentile from 33 to 30 ms. The Chrome repeat improved its
worst second from 41 to 43 FPS and the 99th percentile from 29.7 to
26.5 ms. Its longest individual gap increased from 160.1 to 175.4 ms.
A separate trace with the local
planner found that the largest gaps spent at most 1 ms in the plan call.
The 171 ms gap spent 86 ms in five client updates and 33 ms capturing the
scene, with zero terrain or scenery uploads. A 170 ms gap included
132 ms around the readiness declaration. A 133 ms gap included 116 ms
around the call that queues the frame on the worker. These are elapsed
times across the Java-to-browser boundary. The matching worker draw
stage took 5 to 6 ms around those three gaps.
[Firefox repeat control](../../../output/tests/browser-gameplay-spike/atlas-recheck-control-firefox/firefox-result.json) · [Firefox repeat candidate](../../../output/tests/browser-gameplay-spike/atlas-recheck-local-firefox/firefox-result.json) · [Chrome repeat control](../../../output/tests/browser-gameplay-spike/atlas-recheck-control-chrome/chrome-result.json) · [Chrome repeat candidate](../../../output/tests/browser-gameplay-spike/atlas-recheck-local-chrome/chrome-result.json) · [Local planner stage trace](../../../output/tests/browser-gameplay-spike/atlas-local-profile-firefox/profile-phase-summary.json)

The same Full HD route with packet timing found 67, 46, and 35 ms in
opcode 143 receives inside gaps of 165, 144, and 102 ms. A receive can
replay the context and player packets that precede the static fence, so
this timing covers more than the fence hash. Other large gaps recorded
zero packet work. The stage trace also checks visible displacement and
server position. Its largest sampled player step was 0.336 tiles and the
server confirmed `(252,609)` after the reversal.
[Full HD packet capture](../../../output/tests/browser-gameplay-spike/atlas-local-packet-trace-firefox/firefox-result.json) · [Packet and frame stage correlation](../../../output/tests/browser-gameplay-spike/atlas-local-packet-trace-firefox/packet-stage-summary.json)

The finer boundary trace captured all three rolling contexts. Individual
baseline dispatches took at most 3 ms, while the context dispatch took
16–17 ms and player dispatch took 18–23 ms at those rolls. Publishing the
prepared prediction and activating its context took 14–15 ms. The
prepared gameplay terrain install itself took 1–2 ms. This points to
the bundle of context and player work replayed at the fence.
[Boundary trace](../../../output/tests/browser-gameplay-spike/atlas-baseline-stage-trace-firefox/firefox-result.json) · [Boundary phase analysis](../../../output/tests/browser-gameplay-spike/atlas-baseline-stage-trace-firefox/region-stage-summary.json)

A separate trial enqueued world readiness and draw data without waiting
on frontend promises. The worker still owned submission and queue
backpressure. The candidate ran more than 4,200 local world enqueues per
browser. Firefox's worst second fell from 40 to 36 FPS and its longest
gap grew from 155 to 206 ms. Chrome's worst second stayed at 43 FPS and
its 99th percentile rose from 26.5 to 29.1 ms, with a shorter maximum gap.
The trial was removed after the pacing results.
[Firefox enqueue trial](../../../output/tests/browser-gameplay-spike/worker-frame-queue-firefox/firefox-result.json) · [Chrome enqueue trial](../../../output/tests/browser-gameplay-spike/worker-frame-queue-chrome/chrome-result.json)

The rebuilt halo candidate prepares the outer and retained frame metadata
alongside its meshes and sends the activation message through the existing
streaming logger. Its context prediction and activation phase fell from
14–15 ms to 0–1 ms at the three rolls. The client still spent 18–19 ms
inside the region's world-load call while the logged world-section work
accounted for 2–3 ms. The untraced pacing results were mixed. Firefox's
worst second moved from 40 to 37 FPS and the number of seconds below 50
fell from six to four. Chrome's worst second moved from 43 to 42 FPS and
its 99th percentile rose from 26.5 to 30.3 ms. Both reached `(252,609)`
with three rolls, one epoch, and zero sampled holds or input closures.
[Firefox halo candidate](../../../output/tests/browser-gameplay-spike/detached-halo-rebuilt-firefox/firefox-result.json) · [Chrome halo candidate](../../../output/tests/browser-gameplay-spike/detached-halo-rebuilt-chrome/chrome-result.json) · [Halo phase analysis](../../../output/tests/browser-gameplay-spike/detached-halo-rebuilt-trace/region-stage-summary.json)

The rebuilt client also crossed the diagonal while the optional terrain
jobs were deliberately held. All nine gameplay cells were ready and all
16 overlapping draw buffers stayed resident. The server confirmed
`(250,550)` with zero holds or sampled input closures. Its signed native
fixture matched exact collision, minimap, roofs, picking geometry, and
scenery size and data across the primitive worker boundary.
[Delayed diagonal](../../../output/tests/browser-gameplay-spike/detached-halo-held-diagonal/firefox-result.json) · [Native parity](../../../output/tests/seamless-world-streaming/halo-prepared-build/native-parity.log)

The final timing pass isolated the remainder of the world-load call.
Collision installation took 0–1 ms and the activation message rounded
to 0 ms. The synchronous transition telemetry fell between those
measurements. Queuing the world and client transition summaries cut
the traced region's world-load phase from 17–21 ms to 2–5 ms. Firefox's
untraced worst second was 37 FPS with a 32 ms 99th percentile. Chrome's
was 41 FPS with a 28.1 ms 99th percentile. The private build on port 8482
had better overall pacing in both browser comparisons. The halo and
queued-transition trials were removed from the gameplay source after the
comparisons. Their exact builds and the boundary diagnostics are retained.
[Tail timing](../../../output/tests/browser-gameplay-spike/halo-install-tail-trace/region-stage-summary.json) · [Queued transition timing](../../../output/tests/browser-gameplay-spike/world-transition-logging-trace/region-stage-summary.json) · [Firefox queued transition](../../../output/tests/browser-gameplay-spike/world-transition-logging-firefox/firefox-result.json) · [Chrome queued transition](../../../output/tests/browser-gameplay-spike/world-transition-logging-chrome/chrome-result.json)

The travel trace now converts route events from the browser probe's
clock origin before comparing them with worker timestamps. A fresh run
of the queued-transition build passed the corrected walking window and
arrived at `(252,609)` with zero holds, input blocks, or transitions.
Its two longest gaps had zero time in packet handling. The full FPS
comparison uses its own presentation window and was already applying
the probe's clock offset.
[Corrected travel trace](../../../output/tests/browser-gameplay-spike/world-transition-logging-clock-proof/firefox-result.json)

The player preview uses the original applet input and the separate
WebGL worker on loopback port 8482. It uses the local atlas planner and
the same primitive terrain and scenery replies as the measured route.
A normal mouse and keyboard login reached the appearance screen,
accepted the character, and entered the playable world at 1920×1080.
The waiting cover now clears when the worker acknowledges a UI frame
so the appearance screen can receive input before the first world frame
arrives.
[Full HD preview](http://127.0.0.1:8482/?mode=webgl&physical=on) · [Manual login proof](../../../output/tests/browser-gameplay-spike/local-atlas-player-preview/manual-login-result.json)

#### Resident frame production and catch-up work

The Chrome main-thread CPU capture aligns the profiler and the game
frame clocks within 1.3 ms. Over the captured walk, 361 of 35,124
samples were in garbage collection. The longest measured gap with no
geometry uploads was 135 ms. It included four game updates totaling
60 ms and 34 ms in scene capture. The Chrome profiler recorded one idle
sample and zero garbage collector samples during that gap. Several
shorter gaps had garbage collector samples. The profile startup
interrupted the first leg, so its frame-rate summary is diagnostic.
[Chrome profile analysis](../../../output/tests/browser-gameplay-spike/chrome-resident-cpu-profile/profile-summary.json) · [Profiler API](https://chromedevtools.github.io/devtools-protocol/tot/Profiler/)

An untraced comparison used the player preview's exact client JAR with
CheerpJ 4.3 and Java 8. Firefox reached a 36 FPS worst second and a
30 ms 99th percentile. Chrome reached 40 FPS and 31 ms. Both completed
the same route at Full HD with zero holds and input blocks. The
player preview continues to use CheerpJ 5.0, which reached 40 and 43 FPS
in the prior matched Firefox and Chrome repeats.
[Firefox 4.3 walk](../../../output/tests/browser-gameplay-spike/runtime-43-firefox/firefox-result.json) · [Chrome 4.3 walk](../../../output/tests/browser-gameplay-spike/runtime-43-chrome/chrome-result.json)

With the same JAR and CheerpJ 5.0, Java 17 completed in Chrome with a
39 FPS worst second, a 29 ms 99th percentile, zero holds, and a
server-confirmed arrival. Firefox stopped before login. The player
preview and compact packing comparison use Java 8.
[Chrome Java 17 result](../../../output/tests/browser-gameplay-spike/runtime-java17-chrome/chrome-result.json) · [Firefox Java 17 result](../../../output/tests/browser-gameplay-spike/runtime-java17-firefox/firefox-result.json)

The private catch-up trial presents the actual game state when the
accumulated update work reaches one quarter of a frame. It carries
the original five-update limit across successive draws. A single
indivisible game update still runs to completion. The runtime continues
to apply the original server receipts and interpolation rules.

The first pair improved the worst second from 38 to 42 FPS in Firefox
and from 42 to 43 FPS in Chrome. The repeat in reversed order moved the
worst second from 39 to 37 FPS in Firefox and from 45 to 39 FPS in
Chrome. The repeat dropped Firefox's seconds below 50 from five to four
and Chrome's from five to three. The movement trace applied all three
rolls, reached the authoritative destination and sampled a maximum
visible step of 0.328 tiles through the walk. All sampled holds and
input closures were zero. The scheduler was removed after the mixed
repeat. Its exact source and staged candidate remain available.
[First Firefox candidate](../../../output/tests/browser-gameplay-spike/catch-up-budget-firefox/firefox-result.json) · [First Chrome candidate](../../../output/tests/browser-gameplay-spike/catch-up-budget-chrome/chrome-result.json) · [Firefox repeat control](../../../output/tests/browser-gameplay-spike/catch-up-repeat-control-firefox/firefox-result.json) · [Firefox repeat candidate](../../../output/tests/browser-gameplay-spike/catch-up-repeat-budget-firefox/firefox-result.json) · [Chrome repeat control](../../../output/tests/browser-gameplay-spike/catch-up-repeat-control-chrome/chrome-result.json) · [Chrome repeat candidate](../../../output/tests/browser-gameplay-spike/catch-up-repeat-budget-chrome/chrome-result.json) · [Movement trace](../../../output/tests/browser-gameplay-spike/catch-up-movement-trace-firefox/firefox-result.json)

The Chrome allocation sample attributed about 1 GB of transient
allocation to the browser model projection module across the walk. Its
largest application site was the whole-packet validation function, which
created arrays and buffer-range records on each call. The private
projection trial kept one scratch workspace per kernel. Weak buffer
keys retained only the validation ranges and the Java array views expired
when the native call completed. The same packet-wide alias checks ran
before any projection writes.
[Allocation analysis](../../../output/tests/browser-gameplay-spike/chrome-resident-allocations/allocation-summary.json) · [Allocation profiler API](https://chromedevtools.github.io/devtools-protocol/tot/HeapProfiler/)

The scratch path matched 12,000 projections against Java and reused its
buffer range records across 32,648 Firefox and 33,280 Chrome packets.
Firefox's worst second fell from 37 to 29 FPS and its 99th percentile
rose from 33 to 34 ms. Chrome's worst second rose from 37 to 46 FPS and
its 99th percentile fell from 28.9 to 27 ms. The number of seconds below
60 moved from 22 to 24 in Firefox and remained 13 in Chrome. All four
walks completed at `(252,609)` with three rolls, one epoch, zero holds
and zero sampled input closures. The scratch trial was removed after
the divergent pacing results. The projection source and exact staged
bundles remain available.
[Firefox control](../../../output/tests/browser-gameplay-spike/model-batch-scratch-control-firefox/firefox-result.json) · [Firefox scratch](../../../output/tests/browser-gameplay-spike/model-batch-scratch-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/model-batch-scratch-control-chrome/chrome-result.json) · [Chrome scratch](../../../output/tests/browser-gameplay-spike/model-batch-scratch-chrome/chrome-result.json)

The independent alias-check trial delegated inter-model alias checks
to the browser's full-packet buffer validation. Native Scene, array,
and rejected-packet replay digests matched the controls. The browser
walks removed more than 15 million Java role-map operations per browser.
Firefox's worst second fell from 39 to 36 FPS and Chrome's from 43 to
38 FPS. Firefox's seconds below 60 fell from 28 to 22 and Chrome's
remained 15. All four walks preserved movement authority, one epoch and
zero sampled holds or input closures. The change was removed after the
mixed pacing results.
[Native parity](../../../output/tests/seamless-world-streaming/single-model-validation-build/parity.json) · [Firefox control](../../../output/tests/browser-gameplay-spike/native-validation-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/native-validation-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/native-validation-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/native-validation-chrome/chrome-result.json)

#### Compact scenery conversion

The largest generated JavaScript allocation stack matched the Java
compact scenery encoder by its float word offsets and packed byte
groups. It accounted for an estimated 6.98 GB in the original Chrome
capture and 7.03 GB in the scratch capture, including memory collected
during the walks. The scratch trial sharply reduced the projection
module's sampled allocations while leaving this conversion cost intact.
[Source and stack match](../../../output/tests/browser-gameplay-spike/model-batch-scratch-allocations/compact-source-analysis.json) · [DevTools script source API](https://chromedevtools.github.io/devtools-protocol/tot/Debugger/#method-getScriptSource)

The private compact trial gives the browser up to 128 canonical float
vertices at a time. It writes exact compact words into the unpublished
destination using the existing attribute contract and byte lookup
tables. The Java renderer reuses one input block for these calls and
still calculates the original material, lighting and UV values.
Unsupported or inexact inputs run through the existing Java encoder
and its legacy fallback. The GPU receives the same compact layout.

The build passed the native renderer's 85 UV and material checks and the
browser geometry suite's 7,252 layout and contract checks. With every
accepted word compared to the literal Java encoder, Firefox verified
3,076,116 vertices across 24,127 blocks and Chrome verified 3,055,868
vertices across 23,968 blocks. Neither browser rejected a block. Both
walks finished at the server-confirmed destination after three rolls
at 1920×1080 and logical 960×540. Holds and sampled input closures were
zero. The comparison runs include the Java encoding cost for verification
and are separate from the frame-pacing controls.
[Firefox exact walk](../../../output/tests/browser-gameplay-spike/compact-parity-firefox/firefox-result.json) · [Chrome exact walk](../../../output/tests/browser-gameplay-spike/compact-parity-chrome/chrome-result.json) · [Native renderer suite](../../../output/tests/seamless-world-streaming/compact-vertex-blocks-build/native-variants.log) · [Browser geometry suite](../../../output/tests/seamless-world-streaming/compact-vertex-blocks-build/compact-layout.json)

The Chrome capture with conversion enabled sampled 13.42 GB of transient
allocations, down from 18.74 GB in the original diagnostic. It packed
3,044,288 vertices with zero rejections. The Java compact vertex encoder
disappeared from the largest sampled sites. The newest generated
profile identified about 436 MB under the draw key's hash computation.
The Java renderer constructs these keys several times per resident mesh
each frame, including when it orders required draws and looks up buffers.
[Compact conversion allocation capture](../../../output/tests/browser-gameplay-spike/compact-block-allocations/allocation-summary.json)

The untraced comparisons used the same JAR and Full HD output, with
verification disabled. The second pair reversed the order. All eight
walks reached the authoritative destination after three rolls, with one
epoch, zero holds and zero sampled input closures.

| Browser and pair | 99th-percentile frame interval | Gaps of at least 100 ms | Lowest one-second FPS | Seconds below 60 FPS |
| --- | ---: | ---: | ---: | ---: |
| Firefox first | 32 → 28 ms | 7 → 5 | 38 → 36 | 23 → 22 |
| Firefox repeat | 32 → 29 ms | 7 → 5 | 37 → 38 | 20 → 19 |
| Chrome first | 29.0 → 27.7 ms | 2 → 1 | 45 → 46 | 14 → 13 |
| Chrome repeat | 28.9 → 28.3 ms | 4 → 3 | 37 → 45 | 17 → 18 |

The compact converter reduced the tail in both browsers. The longest
individual interval still reached 173 ms in Firefox and 150.4 ms in
Chrome with the change enabled. Stable 60 FPS through the entire walk
remains the target.
[Firefox first control](../../../output/tests/browser-gameplay-spike/compact-block-control-firefox/firefox-result.json) · [Firefox first candidate](../../../output/tests/browser-gameplay-spike/compact-block-firefox/firefox-result.json) · [Firefox repeat control](../../../output/tests/browser-gameplay-spike/compact-block-repeat-control-firefox/firefox-result.json) · [Firefox repeat candidate](../../../output/tests/browser-gameplay-spike/compact-block-repeat-firefox/firefox-result.json) · [Chrome first control](../../../output/tests/browser-gameplay-spike/compact-block-control-chrome/chrome-result.json) · [Chrome first candidate](../../../output/tests/browser-gameplay-spike/compact-block-chrome/chrome-result.json) · [Chrome repeat control](../../../output/tests/browser-gameplay-spike/compact-block-repeat-control-chrome/chrome-result.json) · [Chrome repeat candidate](../../../output/tests/browser-gameplay-spike/compact-block-repeat-chrome/chrome-result.json)

The separate draw key trial kept a bounded weak map per browser
renderer, keyed by static mesh instance. Each stored key owned an
immutable hash and retained no mesh data. Native checks covered signed
planes, rebases, animated poses, texture changes, and buffer failure
paths. Each browser built 604 keys and reused more than 3 million
lookups. Chrome's worst second moved from 44 to 45 FPS, with two
fewer gaps over 100 ms. Firefox's worst second stayed at 41 FPS and
its longest interval improved from 163 to 134 ms. Firefox spent
24 seconds below 60 FPS instead of 18. The profile and the mixed
pacing results led to removal of the trial. The player build uses
compact conversion with the established draw keys.
[Native draw checks](../../../output/tests/seamless-world-streaming/stable-draw-key-build/animated-chunks.log) · [Chrome control](../../../output/tests/browser-gameplay-spike/stable-draw-keys-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/stable-draw-keys-chrome/chrome-result.json) · [Firefox control](../../../output/tests/browser-gameplay-spike/stable-draw-keys-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/stable-draw-keys-firefox/firefox-result.json)

#### Remaining client work after compact conversion

The Chrome CPU profile of the updated preview captured a 145 ms frame
interval with zero geometry uploads. Five client updates took 68 ms,
Java scene capture took 35 ms, and the WebGL worker spent 3.5 ms
submitting the next frame. Chrome recorded zero idle or garbage
collector samples during the interval. A second zero-upload gap of
106 ms spent 42 ms in client updates and 27 ms capturing the scene.
Some gaps included both upload work and garbage collection. The test
crossed all three rolls with one epoch and finished at `(252,609)`.
[Profile summary](../../../output/tests/browser-gameplay-spike/compact-resident-cpu-profile/profile-summary.json)

The private projection-demand capture counts the vertices of visible
resident models whose faces all fail the existing CPU cull after
projection. The early cull rejected every face for 8,681,625 of
8,710,772 projected vertices in Chrome and 8,672,011 of 8,702,851
in Firefox. About 8 million sampled vertices in each browser were
scenery. This measures the possible scope of moving rejection ahead
of projection.
[Chrome projection demand](../../../output/tests/browser-gameplay-spike/projection-demand-chrome/chrome-result.json) · [Firefox projection demand](../../../output/tests/browser-gameplay-spike/projection-demand-firefox/firefox-result.json)

The follow-up capture projects conservative model bounds with outward
rounding and falls back if an interval may cross the near plane or
overflow the literal integer transform. It checks every predicted
miss against the original rotated faces on the sampled frames. The
ordinary projection and picking paths still run.
Both browser captures moved the pointer through six screen positions
while walking. In Chrome, the bounds accounted for 8,655,751 of
8,713,382 sampled vertices. In Firefox they accounted for 8,586,333 of
8,642,026. Every predicted miss agreed with the literal face cull.
[Chrome bounds proof](../../../output/tests/browser-gameplay-spike/camera-pick-bounds-chrome/chrome-result.json) · [Firefox bounds proof](../../../output/tests/browser-gameplay-spike/camera-pick-bounds-firefox/firefox-result.json)

The opt-in candidate omits the transform and CPU face loop for a
proven miss. It requires resident GPU ownership, verified face
indices, an unchanged transform cache, and projection arrays owned
by the model. The ordinary rotation and face loop still handle
pointer hits, interaction-only models, dirty models and fallback draws.

The native Scene fixture matched picks, sprite anchors, surfaces,
and pixels over 576 frames and exercised the omission. An additional
6,000 fixed-point projection cases checked the conservative envelope
against literal face bounds. The envelope rejected 947 cases and kept
all 3,580 literal hits. The first untraced walks omitted 68.71 million
projections in Firefox and 69.33 million in Chrome. Both reached the
server-confirmed destination after three rolls and sampled zero loading
holds or input closures.
[Exact native proof](../../../output/tests/seamless-world-streaming/resident-camera-cull-build/native-picking-expanded.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/resident-camera-cull-firefox/firefox-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/resident-camera-cull-chrome/chrome-result.json)

The same-JAR pairs kept the compact converter enabled. The camera
cull improved the 99th-percentile interval in both directions of
test order. The Chrome repeat had more individual 100 ms gaps and a
slower worst second. The remaining dips still miss the 60 FPS goal.

| Browser and pair | 99th-percentile frame interval | Lowest one-second FPS | Seconds below 60 FPS |
| --- | ---: | ---: | ---: |
| Firefox first | 29 → 27 ms | 38 → 41 | 16 → 15 |
| Firefox repeat | 32 → 28 ms | 37 → 44 | 17 → 15 |
| Chrome first | 30.6 → 26.8 ms | 42 → 48 | 15 → 13 |
| Chrome repeat | 27.3 → 24.8 ms | 46 → 43 | 15 → 15 |

[Firefox first control](../../../output/tests/browser-gameplay-spike/resident-camera-cull-control-firefox/firefox-result.json) · [Firefox repeat control](../../../output/tests/browser-gameplay-spike/resident-camera-cull-repeat-control-firefox/firefox-result.json) · [Firefox repeat candidate](../../../output/tests/browser-gameplay-spike/resident-camera-cull-repeat-firefox/firefox-result.json) · [Chrome first control](../../../output/tests/browser-gameplay-spike/resident-camera-cull-control-chrome/chrome-result.json) · [Chrome repeat control](../../../output/tests/browser-gameplay-spike/resident-camera-cull-repeat-control-chrome/chrome-result.json) · [Chrome repeat candidate](../../../output/tests/browser-gameplay-spike/resident-camera-cull-repeat-chrome/chrome-result.json)

The separate Firefox movement trace captured 4,192 fresh frames, three
rolls, zero holds and zero sampled input closures. The largest sampled
player step was 0.422 tiles. Its 160 ms gap near `(264,609)` included
63 ms around the scene-baseline receive, which replayed the pending
context and player packets. A 149 ms gap had zero packet work.
In Chrome the new build still recorded a 137 ms interval with zero
geometry uploads, 64 ms in four updates and 33 ms in scene capture.
The worker submitted in 3.1 ms. These diagnostic traces distinguish
lingering client CPU gaps from terrain draws and movement continuity.
[Firefox movement](../../../output/tests/browser-gameplay-spike/resident-camera-cull-movement/firefox-result.json) · [Chrome CPU profile](../../../output/tests/browser-gameplay-spike/resident-camera-cull-cpu/profile-summary.json)

The detailed handoff run saw 16–20 ms dispatches of the context
packet, 10–23 ms dispatches of the player packet, and 2–3 ms at
the fence itself. The context spent 14–18 ms in prediction and
activation. The region load spent up to 10 ms dematerializing and
rebasing scenery, while the prepared terrain installation took at most
2 ms. Retained models marked dirty at the roll also have to refresh
their transforms before projection can omit them on a later frame.
[Handoff phase detail](../../../output/tests/browser-gameplay-spike/resident-camera-cull-handoff/region-stage-summary.json)

The private rebase candidate preserves a verified transform cache for
translation-only shifts of retained game-object models. It shifts X/Z
coordinates and bounds, retains translation-invariant lighting, and
invalidates the camera polygon-normal cache as before. Models with
aliased storage, dirty transforms or unsupported bounds use the literal
rebuild path.

The native comparison matched transformed coordinates, bounds, normals,
lighting, picks, and pixels across 280 cached shifts and 40 literal
fallbacks. Both browsers retained 1,740 models through the walk, covering
136,977 vertices. Firefox's untraced 99th percentile stayed at 28 ms
and Chrome's improved from 29.2 to 26.9 ms. The Chrome worst interval
fell from 139 to 119 ms, while eight more completed seconds fell at 59
frames. Firefox's worst interval was about 162 ms in both runs.

| Traced Firefox roll | Scene capture | Full frame interval |
| --- | ---: | ---: |
| First | 29 → 12 ms | 154 → 126 ms |
| Second | 25 → 11 ms | 91 → 89 ms |
| Return | 39 → 18 ms | 147 → 137 ms |

The traced candidate kept input live through all three rolls and
reached the authoritative destination. The largest sampled player
step was 0.328 tiles. Scene-baseline receive times remained significant,
at 34–58 ms across the three rolls. The new preview includes the
cached rebase based on the reduction in scene capture at every roll.
[Exact native proof](../../../output/tests/seamless-world-streaming/cached-origin-rebase-build/native-picking.json) · [Firefox control](../../../output/tests/browser-gameplay-spike/cached-origin-rebase-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/cached-origin-rebase-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/cached-origin-rebase-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/cached-origin-rebase-chrome/chrome-result.json) · [Scene-roll phase comparison](../../../output/tests/browser-gameplay-spike/cached-origin-rebase-phase-trial/roll-comparison.json)

The deferred-signature trial skipped both hashes across 4,291 Firefox
and 4,347 Chrome frames. Firefox's 99th-percentile interval fell from
29 to 28 ms and its seconds below 60 FPS fell from 22 to 14, while gaps
of at least 100 ms increased from four to six. Chrome's worst second
rose from 45 to 51 FPS, while its 99th-percentile interval rose from
26.3 to 27.3 ms and its seconds below 60 FPS increased from 13 to 17.
The mixed results did not justify carrying the experiment forward.
[Firefox control](../../../output/tests/browser-gameplay-spike/deferred-world-signatures-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/deferred-world-signatures-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/deferred-world-signatures-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/deferred-world-signatures-chrome/chrome-result.json)

The split Firefox boundary trace put halo publication at 0–1 ms and
the synchronous activation log at 6–7 ms on each of three rolls. The
local scenery retirement and rebase took 13, 6, and 6 ms. Layered
terrain decode was 1 ms each time, with terrain installation at 1–2 ms.
The longest ordinary submission interval was 155 ms with zero packet
work or geometry uploads. The accompanying timing sample attributed
93 ms to elapsed time around the sky bridge call. This span includes
time the browser or Java runtime can spend on other work.
[Split Firefox trace](../../../output/tests/browser-gameplay-spike/halo-activation-details-firefox/firefox-result.json) · [Region phase breakdown](../../../output/tests/browser-gameplay-spike/halo-activation-details-firefox/region-stage-summary.json)

Using the existing streaming log queue reduced the activation log
span to under 1 ms at all three rolls. The three enclosing submission
intervals moved from 124, 75, and 113 ms to 143, 86, and 121 ms in the
traced pair. In the untraced Firefox pair the 99th percentile fell
from 29 to 28 ms, the longest interval rose from 167 to 175 ms, and
seconds below 60 FPS rose from 17 to 20. In Chrome, the 99th percentile
fell from 28.7 to 24.7 ms, the longest interval rose from 119.3 to
134.6 ms, and seconds below 60 FPS held at 13. Each walk completed
the three rolls and reversal in one epoch with zero holds or input
closures. The isolated logging change was retired after the mixed
results. The control JAR still contained the disabled deferred-signature
trial. The split trace remains available.
[Queued-log Firefox trace](../../../output/tests/browser-gameplay-spike/queued-halo-log-phase-firefox/firefox-result.json) · [Firefox control](../../../output/tests/browser-gameplay-spike/queued-halo-log-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/queued-halo-log-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/queued-halo-log-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/queued-halo-log-chrome/chrome-result.json)

A Chrome CPU capture aligned main-thread samples with the submitted
frames to within 1.4 ms. A 134 ms gap with no geometry uploads or
packets had 20 garbage-collector samples out of 65. A 133 ms boundary
gap had one garbage-collector sample, 69 ms of client update time,
and a 54 ms fence packet span inside that update. The capture started
the profiler while approaching the first boundary and uses the queued
log JAR. Its timing is diagnostic.

The largest named compiled Java leaf was the identity scan in
`Scene.removeModel`, with 1,026 samples during the walk. The roof
loop calls it for unpopulated upper-plane slots each frame. The
existing whole-zone experiment guards this case. The walking trial
uses the same no-op for a missing model and preserves the existing
nonnull removals. Its native same-JAR comparison passed 6,251
operations, covering the full model array, parallel metadata, sentinel,
and duplicate behavior. The older whole-zone mode also passed its
6,251 operations.
[Chrome CPU and gap analysis](../../../output/tests/browser-gameplay-spike/resident-after-rebase-cpu-chrome/profile-summary.json) · [Sampled compiled methods](../../../output/tests/browser-gameplay-spike/resident-after-rebase-cpu-chrome/chrome-hot-jit-sources.json) · [Chrome Profiler API](https://chromedevtools.github.io/devtools-protocol/tot/Profiler/) · [Walking parity](../../../output/tests/seamless-world-streaming/empty-roof-removals-build/native-walking.json)

The Full HD controls and trials used the same JAR and configuration,
with the empty-removal guard toggled. Each completed three rolls and
the reversal to `(252,609)` in one epoch with zero holds or input
closures. The trial reduced intervals over 100 ms in both browsers.

| Browser | 99th percentile | Longest interval | Gaps ≥100 ms | Worst second | Seconds below 60 FPS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Firefox | 28 → 28 ms | 166 → 157 ms | 6 → 5 | 41 → 41 FPS | 16 → 14 |
| Chrome | 26.2 → 24.4 ms | 138.1 → 116.2 ms | 3 → 1 | 47 → 50 FPS | 18 → 19 |

These results measure fresh submissions over the private walking route.
The guard reduced a measured steady-state CPU cost and left the 60 FPS
target open. [Firefox control](../../../output/tests/browser-gameplay-spike/empty-roof-removals-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/empty-roof-removals-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/empty-roof-removals-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/empty-roof-removals-chrome/chrome-result.json)

The guard's follow-up Chrome CPU capture no longer placed the removal
scan among the hottest compiled methods. It still recorded a 144 ms
roll with 73 ms in client updates and one collector sample, and a
119 ms ordinary interval with zero geometry uploads and 16 collector
samples. The untraced runs above provide the pacing comparison.
[Follow-up Chrome profile](../../../output/tests/browser-gameplay-spike/empty-roof-removals-cpu-chrome/profile-summary.json)

#### Deferred cached transform rows

An isolated trial translated each carried model's bounds at the roll
and applied the same offset to its cached rows only when a reader
needed the vertex or face coordinates. Native checks matched the
existing transforms, pixel and picking digest, and bounds. Separate
checks exercised accumulated and cancelled shifts, batch reads, and
capacity growth. Both browsers wrote 2,248 of the 136,977 vertex rows
affected by the three rolls. Both reached `(252,609)` in one epoch with
zero holds or input closures.

| Browser | 99th percentile | Longest interval | Worst second | Seconds below 60 FPS |
| --- | ---: | ---: | ---: | ---: |
| Firefox | 28 → 28 ms | 149 → 142 ms | 40 → 40 FPS | 24 → 19 |
| Chrome | 25.0 → 25.6 ms | 132.5 → 135.2 ms | 49 → 49 FPS | 15 → 18 |

The traced Firefox pair showed that removing the row writes affected
some rolls. The three scene retirement and rebase phases took 21 → 17,
15 → 5, and 5 → 5 ms. The enclosing submission intervals took 162 → 139,
135 → 89, and 91 → 140 ms. The return roll also spent more time in
context logging and scene capture. The full frame sequence still had
long gaps and the browser pairs diverged. The trial was retired and
its exact artifact remains available for comparison.
[Native parity](../../../output/tests/seamless-world-streaming/deferred-rebase-rows-build/native-picking.json) · [Firefox control](../../../output/tests/browser-gameplay-spike/deferred-rebase-rows-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/deferred-rebase-rows-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/deferred-rebase-rows-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/deferred-rebase-rows-chrome/chrome-result.json) · [Traced roll comparison](../../../output/tests/seamless-world-streaming/deferred-rebase-rows-build/roll-comparison.json)

#### Integer camera bounds

The Chrome allocation capture of the roof-guard build estimated
14.58 GB of transient main-thread JavaScript allocations over the
walk. The largest named leaf accounted for 3.22 GB and its generated
code matches Java's floating-point floor and ceiling helper. The
camera bounds check uses that helper for fixed-point rotations and
projected bounds. The trial keeps the overflow checks and uses signed
integer shifts and exactly rounded integer quotients after those
checks. The native rejection digest matched across 6,000 cases, along
with the native picking and pixel digest.
[Allocation summary](../../../output/tests/browser-gameplay-spike/post-roof-guard-allocations-chrome/allocation-summary.json) · [Native camera and scene parity](../../../output/tests/seamless-world-streaming/integer-camera-bounds-build/native-picking.json)

Each browser pair used the same client JAR and Full HD display
configuration. Both browsers completed the three rolls and reversal
with one epoch, zero holds or input closures, and a server-confirmed
arrival at `(252,609)`.

| Browser | 99th percentile | Longest interval | Gaps ≥100 ms | Worst second | Seconds below 60 FPS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Firefox | 28 → 28 ms | 200 → 165 ms | 5 → 6 | 37 → 40 FPS | 14 → 16 |
| Chrome | 27.1 → 26.7 ms | 129.7 → 130.8 ms | 4 → 2 | 45 → 45 FPS | 21 → 11 |

[Firefox control](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-control-firefox/firefox-result.json) · [Firefox trial](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-firefox/firefox-result.json) · [Chrome control](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-control-chrome/chrome-result.json) · [Chrome trial](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-chrome/chrome-result.json)

The follow-up Chrome capture estimated 110.7 MB at the same rounding
helper and 10.54 GB across all sampled sites. The control estimated
3.22 GB at the helper and 14.58 GB overall. The reduction is in
temporary allocations over the walk. The untraced pairs provide the
pacing comparison. The traced candidate still recorded a 127 ms
boundary interval with 58 ms of packet work and a 106 ms ordinary
interval with zero packet work. Stable 60 FPS remains open.
[Follow-up allocation summary](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-allocations-chrome/allocation-summary.json) · [Traced candidate walk](../../../output/tests/browser-gameplay-spike/integer-camera-bounds-allocations-chrome/chrome-result.json)

#### Browser streaming diagnostics

The browser journal trial buffers terrain streaming messages for the
existing one-second browser observer. It removes synchronous console and
file writes from the halo activation phase and avoids waking a Java log
writer for routine streaming notices. The Java queue and browser history
have bounded retention. The private Firefox trace received all three
activation messages. The halo log phase dropped from 6, 14, and 13 ms to
under 1 ms at each roll. The enclosing submission intervals went from
140, 86, and 123 ms to 117, 134, and 90 ms. The second roll spent more
time retiring and installing the local scene in the trial.
[Traced roll comparison](../../../output/tests/seamless-world-streaming/browser-streaming-log-build/roll-comparison.json) · [Browser journal receipt](../../../output/tests/browser-gameplay-spike/browser-streaming-log-trace-firefox/firefox-result.json)

#### Java client worker feasibility

The existing browser renderer worker moves WebGL submissions off the
page. The Java client still runs its game updates, scene capture and
bridge preparation on the page thread. A private experiment replaces
the applet window with a small ClientPort and runs the same client JAR
in a browser worker. Both browsers fetched the disposable server's
real configuration and produced three 960×540 login framebuffers. All
six framebuffers had the same RGB digest. This proves worker startup,
private transport and login frame generation. Gameplay, GPU frame
pacing, interactive input and the audio path are separate checks.
[Firefox client worker startup](../../../output/tests/browser-gameplay-spike/worker-client-port-proof/firefox-worker-result.json) · [Chrome client worker startup](../../../output/tests/browser-gameplay-spike/worker-client-port-proof/chrome-worker-result.json) · [CheerpJ architecture](https://cheerpj.com/docs/explanation/architecture)

The welcome packet asked the desktop window to revalidate its size.
Moving that callback onto ClientPort lets the applet keep its existing
viewport behavior. A worker can supply its own viewport. The single
worker ran Java and WebGL together and completed the client's three
scene rolls. It reached a 16 FPS worst second in Firefox, with the
server observed one tile before the final destination at the first
read. This exploratory setup also used scoped GL state and validation.

The separate client and WebGL worker run used the same owned GL state
and upload validation as the applet control. The server confirmed
arrival at `(252,609)` after three rolls and a reversal. The
1920×1080 output used the original 960×540 logical framebuffer, with
one load epoch and zero reported holds or input closures. Firefox
recorded a 62 ms 99th-percentile interval and a 23 FPS lowest second,
compared with 32 ms and 34 FPS for the applet and WebGL worker. Keep the
applet preview for player testing. The private probe exercises scripted
gameplay and leaves physical input and audio parity for a future port.
[Single worker capture](../../../output/tests/browser-gameplay-spike/worker-client-compact-firefox/firefox-worker-result.json) · [Two worker capture](../../../output/tests/browser-gameplay-spike/worker-client-render-worker-firefox/firefox-worker-result.json)

The Java 17 configuration exited before its first fixture receipt.
The current preview uses the verified Java 8 configuration.
[Java 17 startup capture](../../../output/tests/browser-gameplay-spike/java17-render-worker-firefox/firefox-result.json)

## Reproduce the private preview

Build with `./scripts/build-client.sh` and `./scripts/build-server.sh` from the
worker checkout. Stage fresh disposable output with distinct unused loopback
ports for each mode.

```sh
python3 tests/myworld/seamless-streaming/prepare-native-preview.py preview \
  --tcp-port 43777 --ws-port 43778
python3 tests/myworld/seamless-streaming/prepare-native-preview.py control \
  --tcp-port 43779 --ws-port 43780 --control
python3 tests/myworld/seamless-streaming/prepare-native-preview.py outer \
  --tcp-port 43785 --ws-port 43786 --outer-coverage
```

Each run is under `output/tests/seamless-world-streaming/`. Start it with the
working directory and command recorded in `server-command.json`. Run
`Open Covered Walking Preview.sh` in that run directory to open its isolated
native client. The staging command prints the disposable account credentials.
To stop the private server gracefully, create a file called `STOP` in its
run directory. The process observes that marker and shuts itself down.

The [native fixture](../../../tests/myworld/seamless-streaming/CoveredWalkClient.java)
takes the staged `status.json` path and `true` for the preview or `false` for
the control. Start with the disposable account at the default spawn. Compile
the fixture against the built client JAR, then launch it from the staged
`client` directory with the fixture classes and `client.jar` on the classpath.
Supply the disposable password in `SPOILED_MILK_PRIVATE_WALK_PASSWORD` and set
`-Duser.home` to the staged `home` directory. The fixture uses the normal game
client, logs the context at the seam, and checks the server endpoints. For the
outer run, pass `true --outer-coverage` after the status path. Use a fresh
staged run because the private server saves the disposable character's
position on logout.

To stage the Firefox route, run the following with both browser fixture
ports `43639` and `43539` free.

```sh
python3 tests/myworld/seamless-streaming/prepare-browser-outer-preview.py outer-browser \
  --http-port 8201
```

The browser run is under `output/tests/browser-gameplay-spike/`. Start its
recorded private server, then serve its `web` directory with
`scripts/browser-gameplay/serve.py` on the same HTTP port. Open the URL in
`outer-coverage-fixture.json` and select Play. This fixture signs into its
disposable account, walks once initial graphics are ready, and reports the
crossing through the browser's `browserProof` data. The route emits
`outer-coverage:` events. The browser records the independent world input
gate and upload work in `browserProof.gpu.worldStreamSamples`.

Use a second fresh stage with `--preupload` to run the candidate. Leave that
option off for the control. Run the stages in sequence because their private
game ports are shared. The `outer-coverage:preupload-before` and
`outer-coverage:preupload-final` events show work submitted before the
handoff and the buffers adopted by the renderer.

For sustained travel, stage a fresh run with `--trace --travel-trace
--sustained-walk` and use unused private TCP, WebSocket and HTTP ports. Start a
loopback geckodriver, run `run-browser-travel.py <run>` with its port, then
pass the run to `analyze-travel-trace.py`. For a low-overhead control, keep
the sustained route and streaming features, remove `--trace` and
`--travel-trace`, and add `--presentation-only`. This records fresh WebGL
submission gaps from the page and checks the same actor and server-confirmed
endpoint. Add `--frame-stages` for the narrower bridge-stage timings. For a
Chrome comparison, start a loopback ChromeDriver and use `--browser chrome`.
Each presentation capture includes the browser's renderer and the
distribution of fresh frame intervals. For a
manual trace, stage with
`--manual --travel-trace` and serve it with `--travel-log
<run>/manual-travel.ndjson`. Omit `--travel-trace` for an ordinary manual
play build. The normal private server startup is the same.
