# Covered Walking Preview

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
pass the run to `analyze-travel-trace.py`. For a manual trace, stage with
`--manual --travel-trace` and serve it with `--travel-log
<run>/manual-travel.ndjson`. The normal private server startup is the same.
