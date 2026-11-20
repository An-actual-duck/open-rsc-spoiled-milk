# Seamless World Streaming Implementation Plan

**Active plan for Spoiled Milk chunk loading, November 20, 2026.** Start with
current published `main`. Build a persistent live scene for ordinary walking,
with server-authoritative movement and independently streamed terrain cells.
The [previous terrain pipeline plan](terrain-pipeline-improvement-plan.txt)
and [earlier browser streaming plan](browser-modern-streaming-plan.txt) retain
their original findings and proposals as history. The owner requested fresh
validation. Create new fixtures and routes from the maintained code and map.
Prior experiment branches, prototypes, routes, captures, and results are
excluded as implementation inputs and acceptance evidence for this plan.

## Player outcome

An ordinary walk across a map section should feel like any other stretch of
the route. The camera, walk animation, and the motion of continuously visible
NPCs keep advancing. The player can click, act, or reverse direction on ready
tiles while the next cells prepare. A section edge should cause neither a
loading cover nor an input lock. Login, deliberate relocation, and recovery
still have explicit readiness checks.

The first delivery covers ordinary same-floor surface travel with a matched
server and client. Allow changes to the wire protocol, server interest rules,
client coordinates, scene assembly, and renderers where they serve that goal.
Use the existing signed map, tile size, server gameplay rules, and walk-request
format for the first delivery. Extend to signed levels and other travel cases
after surface behavior is proved. Keep the existing path available during
development for clients that use the current protocol.

## Why the current boundary interrupts play

- The native scene context includes the terrain center. Both fast movement send
  paths require an established context. A center change can hold those packets
  until the regular update establishes the new context. Terrain activation
  also has a separate server-side hold for queued steps while the initial
  player appearance update is pending. The hosted profile enables the layered
  protocol and atomic activation. [Context identity](../../../server/src/com/openrsc/server/GameStateUpdater.java#L2604) · [Fast movement guard](../../../server/src/com/openrsc/server/GameStateUpdater.java#L459) · [Step hold](../../../server/src/com/openrsc/server/model/WalkingQueue.java#L77) · [Hosted profile](../../../server/myworld-host.conf#L43)
- The client applies the region load before appending the next movement
  waypoint. The load changes the local origin, shifts actors and their
  waypoints, rebuilds local world products, and reconciles the static scene.
  If the baseline differs, reconciliation can remove and re-add object and
  wall instances. [Movement handler](../../../Client_Base/src/orsc/mudclient.java#L25736) · [Region load](../../../Client_Base/src/orsc/mudclient.java#L21690) · [Baseline reconciliation](../../../Client_Base/src/orsc/PacketHandler.java#L3489)
- The client can already hold a 5×5 terrain presentation around its 3×3
  gameplay window. The server already sends predictive terrain, and renderers
  already use world offsets for chunk meshes. The outstanding dependency is
  publishing a replacement scene before live rendering and world input can
  continue. [Terrain window extraction](../../../Client_Base/src/orsc/NativeLayeredTerrainSnapshot.java#L449) · [Server prediction](../../../server/src/com/openrsc/server/GameStateUpdater.java#L1037) · [Renderer chunk offsets](../../../PC_Client/src/orsc/BrowserWebGLBridge.java#L353) · [Browser readiness](../../../tools/browser-gameplay/web/browser-world-ready.js#L9)

## Target architecture

### Movement and actor lifetime

Give the client a movement context that persists through ordinary travel in
the same world space and signed level. Renew it for explicit relocations such
as teleports and respawns, including same-floor teleports. Send authoritative
positions with an ordered tick or sequence in that context. Terrain revisions
and receipts have their own sequence. Remove the ordinary coverage change
from the preconditions for server step processing and fast movement delivery.
Keep server step validation and the existing gameplay movement cadence.

Store the local player, camera, and nearby actors' positions and interpolation
history in a stable world coordinate system. Convert to local coordinates
where legacy drawing, picking, or pathfinding needs them. Preserve an actor's
identity, walk phase, and interpolation when that actor remains visible across
a cell boundary. Define how reused server indexes are distinguished from a
previous actor lifetime. Keep teleport and respawn discontinuities explicit.

Use the server's level-qualified spatial index to maintain a bounded area of
interest around the player. Diff membership by actor identity. Preserve the
existing viewer-specific visibility, priority, and capacity rules. Start with
coverage comparable to the current client and verify who enters and leaves
view at the edges. [Server spatial index query](../../../server/src/com/openrsc/server/model/world/region/RegionManager.java#L516) · [Current actor range rules](../../../server/src/com/openrsc/server/GameStateUpdater.java#L3359) · [Server step validation](../../../server/src/com/openrsc/server/model/PathValidation.java#L918)

### Terrain cells and live state

Key terrain by map identity, world space, signed level, and cell coordinates.
Start with the existing 48-tile storage sectors and retain the renderer's
smaller presentation cells where useful. Reuse the signed source data and
content verification. Build terrain, walls, roofs, elevation queries, and
minimap or lighting inputs from explicit cell and neighbor dependencies.
Keep overlapping products resident with stable identities. A cardinal move
of a 3×3 window shares six sectors with the previous window. Those sectors
should keep their data and draw resources through the move. [Map runtime contract](../../../runtime-contract/map-runtime-v1.json#L38)

Deliver viewer-authorized snapshots of live scenery, walls, and ground items
with an ordered stream of changes. A cell being prepared needs a snapshot at
a known revision and all relevant changes after it before activation. A stale
or superseded snapshot cannot overwrite a newer door state, restore a removed
item, or publish after a world or floor change. Define recovery for missing
references or a revision gap at the affected cells. Use the current spatial
queries and object update rules to preserve viewer-specific state.

Publish coherent data at the smallest safe boundary. If a wall or object
footprint affects two cells, update its render, picking, and collision
contributions together for those cells. Derive collision from the authoritative
terrain and object contributions so removing one contributor preserves the
blocking bits of any other contributor. The server continues to validate every
walk and interaction under its existing authority. [Native terrain collision](../../../server/src/com/openrsc/server/model/world/region/NativeLayeredTerrainCollisionPlan.java#L9) · [Object footprints and wall flags](../../../Client_Base/src/orsc/graphics/three/World.java#L2072)

### Ready coverage, rendering, and input

Retain the 144-tile collision and pathfinding grid as a derived view for the
first implementation. Prepare the next view while the current one serves
input. Publish its origin and query data consistently so clicks and paths use
the same world coordinates. Allow existing movement and interaction on ready
tiles. Requests beyond interactive coverage can use the existing “Walk toward”
behavior. A late cell can limit travel at the edge of usable coverage, with
the camera, animation, and interaction in the current area still running.
Define how server-confirmed coverage and the client agree on that edge and
how a queued path resumes when the next cells become ready. Treat readiness
receipts as flow control with no effect on server collision authority.
[Client pathfinder](../../../Client_Base/src/orsc/graphics/three/World.java#L2347) · [Terrain picking](../../../Client_Base/src/orsc/mudclient.java#L12275) · [Walk request encoding](../../../Client_Base/src/orsc/mudclient.java#L29094)

Draw live frames from ready cells while incoming cells prepare. Use stable
mesh keys, prepare immutable products away from movement and frame submission,
and budget GPU uploads. Protect visible and immediately useful cells under
memory pressure. Prioritize the player's travel direction, retain a bounded
margin behind for reversals, and request cells before the player approaches
the edge of usable coverage. Size that lead from movement speed and fresh
measurements of delivery, preparation, and upload time. Let current ready
frames and input continue during ordinary cell publication. Keep explicit
whole-view readiness for login, relocation, and context recovery.

Prioritize movement and interaction over bulk terrain on the ordered network
connection. Send terrain in bounded batches, budget outstanding bytes, and
resume or cancel incoming work as the route changes. Track decoded CPU data,
pending upload bytes, and resident GPU data separately. Choose concrete
budgets from fresh runs on the supported client and hardware profiles.

## Implementation milestones

The first [covered walking preview](seamless-world-streaming-preview.md)
exercises continuity inside an installed surface window. Use its fresh route
and control to review that part of milestone 1.

The outer coverage route shows where the preview still falls short. At
`x = 264` the server has a prepared replacement, yet publishing it advances
the client's scene context and sends the transition through the retained
frame and browser readiness gates. Use this crossing for milestone 2 and
keep the older context path as its control. Prove a live handoff with a
ready overlap before introducing artificial delivery or upload delays.

### Movement continuity gate

The owner reported rubber-banding, NPC jumps, and stalled or sliding walk
animations in earlier attempts. A walk that reaches its destination can
still fail this gate. Keep the current server movement cadence and actor
update path as the control. First verify that changing the local origin
alone preserves the same world position, each pending waypoint in order,
facing, animation phase, and interpolation state. Include wrapped
waypoint queues and test origin changes in both directions. Ordinary
handoffs must never reset these values. Real teleports and respawns
continue to use their explicit reset path.

On the fresh surface route, compare server steps and client receipt order
with the player and continuously visible moving NPCs' world positions
and poses around publication. Look for correction snaps, backward moves
without a turn, duplicate or discarded steps, a walk phase that stops
while an actor moves, and actors reappearing at a new position. The
existing endpoint and aggregate motion counts cannot establish this
continuity by themselves. Pass this gate with the existing movement
cadence before removing either presentation hold or adding new movement
packet behavior. [Actor rebase](../../../Client_Base/src/orsc/ORSCharacter.java) · [Focused waypoint fixture](../../../tests/myworld/seamless-streaming/ActorWaypointRebase.java)

### Prepare browser draw buffers before the scene handoff

Sodium builds render sections from captured world slices, queues the mesh jobs
on workers, and budgets mesh work and graphics uploads separately. Its
renderer uploads section results through region-owned buffers. It can defer
routine work while still waiting for selected urgent work or an explicit
immediate update. The relevant design is the handoff between captured inputs,
completed mesh work, and render-owned uploads. [Sodium section capture and scheduling](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java#L654) · [Sodium mesh workers](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkBuilder.java#L19) · [Sodium region uploads](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager.java#L60)

RSC already builds the adjacent center's terrain and structural meshes on the
sector preload executor. The completed prediction includes immutable mesh
products. The browser still packs the replacement frame's vertices, material
batches and indices and submits the uploads through its active frame path.
Materials depend on texture metadata and packing settings captured from the
client. The existing immutable storage key permits rebased meshes to reuse
their buffers. Reuse requires matching mesh and material variants. [Prediction worker](../../../Client_Base/src/orsc/graphics/three/World.java#L566) · [Browser upload path](../../../PC_Client/src/orsc/BrowserWebGLBridge.java#L331) · [Vertex packing](../../../PC_Client/src/orsc/OpenGLWorldChunkRenderer.java#L2923) · [Material dependencies](../../../PC_Client/src/orsc/BrowserChunkVariant.java)

Run the first private browser experiment with the existing movement packets,
path queue, actor presentation, and scene activation. Stage one adjacent
prediction and walk through `x = 264` with the current origin and scene
ownership rules. The candidate changes where draw buffers are packed and
uploaded. The existing preview supplies the control. Use the same signed map
and fresh route in both runs.

1. Start with incoming static terrain and structure meshes whose final
   browser storage keys can be matched to the predicted product. Capture
   the needed texture metadata and packing settings in an immutable input.
   Pack vertices, indices, and material batches off the live frame and
   compare the result with the current packer. The worker may read only
   captured inputs. Keep dynamic actors, animations, and live scenery
   changes on their existing paths during this experiment.
2. Budget render-owned uploads during ordinary frames before the boundary.
   Retain active buffers and admit only as many pending bytes as fit beside
   them. Give visible and soon-needed cells the first slots. A reversed
   route, changed renderer settings, replaced source, or graphics context
   reset can invalidate the pending product. Reject stale results and use
   the existing path for mismatches. The first implementation can use
   the browser's existing graphics backend for upload ownership. Validate
   actual completion separately from Java packing and native submission.
3. At the handoff, record which predicted buffers the active renderer
   adopts. Separate initial upload bytes from changed material variants and
   dynamic content. Record work completed before the handoff, remaining
   uploads, frame intervals throughout the walk, peak pending and resident
   bytes, Java scene retention, and the browser's world-input gate. A useful
   result moves verified work earlier without creating a new hitch in the
   approach. Keep the movement continuity gate for any subsequent change to
   scene publication. Browser buffer readiness alone cannot authorize new
   collision coverage or replace viewer-specific live object state.

Worker scratch and finished results need separate ownership. Sodium gives
each build worker its own context and hands off finished buffers separately.
For RSC, measure allocations before pooling mesh scratch. Reuse temporary
arrays whose contents expire with the current job. Keep completed buffers
owned by the result through submission, and bound both scratch capacity per
worker and bytes in flight. Start from the existing single terrain worker.
Increase the worker count only if fresh traces show that preparation misses
its lead time and extra CPU work leaves live frame cadence intact. [Sodium build context](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext.java) · [Sodium mesh scratch and finished buffers](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers.java#L39)

Native OpenGL already reuses direct vertex and index scratch buffers. A
later native experiment can add a bounded staging ring with explicit GPU
completion before reusing its space. Sodium checks for persistent mapping and
uses a write path when it is unavailable. Browser uploads already cross a
Java-to-JavaScript copy boundary and use WebGL buffer commands. Measure this
submission cost and GPU completion separately before changing that backend
or adding Java direct buffers. [Sodium upload fallback](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/gpu/arena/staging/MojangStagingBuffer.java#L7) · [RSC native upload scratch](../../../PC_Client/src/orsc/OpenGLWorldChunkRenderer.java#L2886) · [RSC WebGL buffer upload](../../../tools/browser-gameplay/web/webgl.js#L206)

The private browser scenery experiment uses the server's existing adjacent
terrain prediction. At a cardinal surface handoff, the server sends
viewer-filtered placements for complete 24×24-tile cells in the entering outer
strip. The client derives heights from the immutable terrain prediction and
paces mesh builds after terrain preparation. The normal activation and
baseline still establish the scene. A prepared mesh must be complete and
match the final cell's sorted placements and sampled heights before the
renderer can use it. A missing or changed cell takes the existing build
path. The preview measures mesh adoption and frame spacing on the approach
and at the handoff. [Private preview and results](seamless-world-streaming-preview.md)

### Publish the next coverage

The first Firefox handoff trace identified resident scenery construction as
the largest Java wait. The player and scenery baseline were both accepted
about 290 ms after context receipt. The first frame then spent 658 ms in
resident object frame assembly. The second spent 3 ms and released the
presentation latch. Terrain prediction had matched the destination.
Prepare static object cells before the context change, then compare their
content against the authoritative scenery baseline at publication.
[Firefox route evidence](seamless-world-streaming-preview.md#java-handoff-trace)

For the first private scenery stage, attach a candidate placement snapshot
to the existing adjacent terrain prediction. Use the same viewer-specific
queries as the normal baseline. Carry the destination center, signed map
and level, and exact placement revision or descriptor. Deliver pages within
the existing traffic budget. Keep the candidate snapshot separate from
the active collision and object lists, and capture the destination origin
and terrain heights in each worker input. At publication, adopt matching
cell meshes and build changed cells from the authoritative state. A door,
wall, or object changed during preparation must invalidate the affected
cell. Missing or stale predictions should use the existing full-scene
path during this first experiment.

At this handoff, keep the server's existing movement stream and the
client's current collision view active. Build the incoming strip and its
neighbors as one publishable group while the live frame still consumes
the overlap. Replace the derived collision view at a game-tick boundary
after confirming its map identity, coverage, and live-object revision.
Publish drawing resources per ready group. Track interactive coverage
and optional surrounding presentation separately. A late roof or an
offscreen mesh should add work to the queue without opening a world-wide
load epoch. Keep the full readiness path for login, relocation, and
recovery. Use an explicit server travel identity to distinguish these
cases. Firefox already identified this fixture as ordinary walking and
still blocked world input until the replacement's resources were ready.

### 1. Establish continuity across a former boundary

- In the focused Core worker, pass the movement continuity gate with the
  existing packet paths. Define the movement context, actor lifetime, and
  relocation rules for the rolling handoff. Adopt wire changes separately
  where the handoff needs an explicit movement or coverage identity.
- On one ordinary surface crossing, install sufficient authoritative coverage
  on both sides in advance so streaming latency is controlled. Use the normal
  walk entry point and actual server simulation, with no fixed camera or
  generated actor motion. Keep the existing local-grid behavior available for
  this fixture where it fits the installed coverage.
- Cross in both directions, turn around at the seam, follow a moving NPC, and
  interact with a nearby door. Verify continuous actor identity, movement
  packets, live poses, camera motion, input, and server validation. There must
  be zero seam-triggered movement context changes, queued-step holds, global
  input closures, or despawn/re-adds of continuously visible actors. Resolve
  failures here before adding dynamic cell publication.

### 2. Stream an incoming strip during the same live walk

- Start with the strip absent. Stage its terrain, neighbor dependencies,
  revisioned live state, pathfinding data, and render products while the current
  area stays live. Add the strip and retire distant cells after their use ends.
- Independently delay terrain delivery, live object changes, CPU preparation,
  and GPU upload. Reverse while a request is pending and open or close a door
  during preparation. Verify that stale data cannot publish, active-area input
  still works, and recovery stays local to the affected coverage.
- Repeat with a diagonal crossing and objects or walls that span the seam.
  Compare server-approved steps, collision, picking, scenery state, and minimap
  against the same map content. Verify both first visits and return trips.

### 3. Validate both clients and expand coverage

- Exercise the shared path through native OpenGL and browser WebGL. Cover
  multiple routes and populated scenes, varied view distances, slopes, roofs,
  bridges, and moving NPCs. Check the supported software path where shared
  code changed. Include sustained travel to exercise eviction and backpressure.
- At the boundary of coverage or context, test disconnection and recovery,
  wrong-map or stale data, graphics context loss, and real teleports and floor
  changes. Extend the rolling path to other signed levels with those checks in
  place. Verify the old protocol and genuine loading transitions independently.
- Keep rollout opt-in on a matched build until integration and owner play
  acceptance pass. Use the normal workspace handoff and private-world workflow.
  Public activation follows the manager release and shutdown procedures.
  [Manager workflow](../../workspaces/manager.md) · [Public hosting runbook](../../workspaces/oracle-hosting.md)

## Fresh acceptance evidence

Track the server's actual steps and hold reason, packet emission and client
receipt, cell preparation and publication, and displayed frame intervals with
distinct camera and actor poses. Count global input closures, rejected clicks
on ready tiles, retained or repeated frames, actor membership and interpolation
resets, and mismatched collision or picking. Record client and server commits,
map fingerprint, route, graphics and network settings, hardware, and CPU/GPU
residency and queue peaks. Keep fixtures and replay procedures in Git and their
generated evidence in declared workspace output categories. [Workspace output layout](../../workspaces/asset-output-organization.txt)

For normal delivery, require ordinary crossings to remain playable and their
frame pacing to match nearby ordinary walking on the same client and machine.
Use repeated boundary and non-boundary samples and report the longest gap and
the distribution. Record the route-specific frame pacing threshold from fresh
control runs before judging the candidate. Verify that every visible motion
trace remains continuous through publication and that sustained travel stays
within the measured resource budgets. For deliberate delay cases, require
bounded recovery and correctness in ready coverage. Confirm the result in
owner play on both clients before making it the default.

## Stop conditions and follow-up decisions

- If movement still waits with coverage installed, trace the server hold, packet
  context check, or client region-load call. Fix that dependency before tuning
  delivery or expanding prefetch.
- If continuity passes with preinstalled coverage and fails during real
  streaming, separate the cost of delivery, preparation, publication, and GPU
  upload. Change the responsible stage and repeat the independent delay checks.
- Consider a cell-backed pathfinder only if the derived grid cannot support
  continuous coverage with correct paths and picking. Consider changing the
  package layout or splitting the transport only if measured delivery or
  preparation constraints survive bounded scheduling. Those decisions come
  after the first matched streaming milestone.
