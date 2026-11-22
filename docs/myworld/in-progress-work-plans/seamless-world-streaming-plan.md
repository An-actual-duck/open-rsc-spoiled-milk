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

**Current direction.** Ordinary surface travel uses a rolling gameplay window,
verified collision and scenery receipts, and independently resident terrain
draw cells. The private trial keeps the same movement generation and browser
epoch through repeated crossings. The remaining acceptance requirement is
continuous rendering and input while cells are built and published. Keep
working through architectural dependencies before tuning individual stages.
Use [fresh travel evidence](seamless-world-streaming-preview.md#repeated-crossings-with-live-coverage)
to compare the remaining frame gaps with steady walking.

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

### Implementation order

1. Give ordinary walking a server and client movement lifetime independent of
   the terrain center. The server already has an actor scope that excludes
   the center. Use it as the starting point for visibility and add an explicit
   relocation identity so a same-floor teleport still resets the right state.
   The current movement snapshot is already sequenced. Its layered variant
   validates against the scene sequence, so changing only the server's fast
   send guard would leave the two ends coupled. A matched opt-in protocol
   must validate movement against the travel lifetime on both ends. Reuse
   the existing walk requests, server path rules, tick cadence, and actor
   waypoint application.
   [Server actor scope](../../../server/src/com/openrsc/server/GameStateUpdater.java#L3166) · [Fast movement sender](../../../server/src/com/openrsc/server/GameStateUpdater.java#L3358) · [Client snapshot validation](../../../Client_Base/src/orsc/PacketHandler.java#L3085)
2. Serve immutable terrain and prepared geometry independently of the ordered
   gameplay stream. One option is versioned map files fetched through the
   browser asset path and read from the installed package on desktop. Start
   by proving whether a compact, manifest-matched surface collision base can
   stay resident. The installed global surface has 445 sectors of 48×48
   tiles and about 9.8 MiB of raw source files. A hypothetical packed
   four-byte-per-tile collision-and-height base would take about 3.9 MiB
   before indexing. That is a sizing estimate from the current manifest.
   The real format must prove flag width, resolved heights, boundary wall
   parity, and startup and memory cost. Doors, walls, items, and other live
   changes still need viewer-authorized server state and ordered revisions.
   [Terrain sector](../../../server/src/com/openrsc/server/io/NativeLayeredTerrainSector.java#L12) · [Neighbor-aware collision derivation](../../../server/src/com/openrsc/server/model/world/region/NativeLayeredTerrainCollisionPlan.java#L10)
3. Use one live world with a rolling set of published cells. A worker builds
   incoming resources from immutable inputs and the renderer adopts completed
   cells in small batches. Keep the surrounding draw resources resident and
   apply collision, picking, and live-scene revisions consistently. Remove
   ordinary travel from the client's atomic presentation latch and browser
   whole-view readiness path by giving cell membership its own publication
   path. When data for a cell is late, interaction and motion on already
   installed ground keep running. No global loading cover or retained world
   image is part of an ordinary cell update.
   [Client scene context](../../../Client_Base/src/orsc/PacketHandler.java#L724) · [Browser replacement gate](../../../PC_Client/src/orsc/BrowserWebGLBridge.java#L328)

The private rolling path passes the held-fringe test. The diagonal crossing
retained 16 terrain buffers and drew all nine gameplay cells while the worker
withheld the incoming visual terrain. The detached workers prepare collision,
minimap, picking, and shared-area scenery before the crossing. Resident
terrain cells supply the lighting fields. The warmed control walked the same
tiles four times with terrain already installed and recorded a maximum of
36 ms between fresh frames. Its animated scenery continued uploading.

The current browser build still misses the frame-pacing acceptance criterion.
The sustained route also passes without the detailed traces. In the rerun
with more host memory headroom, its largest sampled gap was 199 ms between
crossings. There were zero world geometry uploads in that sample. The nearby
bridge sample spent 105 ms in the actor sprite stage. Separate source-pixel
composition, sprite atlas submission, and overlay export before changing
this pipeline. Use the same route in both directions and a warmed pass over
the affected tiles to check that any improvement survives routine walking.
[Current crossing evidence](seamless-world-streaming-preview.md#repeated-crossings-with-live-coverage)

The browser's bounded catalog and render worker are candidates for reuse after
the independent movement and publication path exists. Today the renderer
still derives catalog scope from the authoritative terrain snapshot, and a
region transition suspends its residency and enters world readiness. Reuse
the cell addressing, resource cache, and worker handoff where they help.
Supply their selection from the persistent world instead of its scene epoch.
[Catalog scope](../../../PC_Client/src/orsc/BrowserWholeOverworldZone.java#L132) · [Browser transition](../../../tools/browser-gameplay/web/webgl.js#L185)

Unreal's World Partition uses one persistent level and streams spatial cells
around a player or another streaming source. Sodium builds and uploads
individual render sections for Minecraft's live world. That scheduling model
can help the third step after ownership and publication are independent.
[Unreal World Partition](https://dev.epicgames.com/documentation/en-us/unreal-engine/world-partition-in-unreal-engine) · [Sodium section manager](https://github.com/CaffeineMC/sodium/blob/637883acfe2a2e160a377a65b27b78e91c5a6d3e/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager.java#L654)

The scene-swap preview is the comparison build. Packing, speculative uploads,
and browser socket buffering are recorded in its implementation notes. A
shorter time spent in the ordinary-walking scene swap does not advance this
plan.
[Preview findings](seamless-world-streaming-preview.md#sustained-travel-and-delayed-stutter)

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

For normal delivery, require zero ordinary-walking scene swaps, held world
frames, loading covers, and global input closures. Compare frame pacing during
streaming with ordinary walking on the same machine. Use sustained travel,
multiple cell arrivals, reversals, and first visits. Record browser frame gaps
and the server and displayed movement together. Any repeatable visible hitch
caused by streaming fails acceptance, including a hitch displaced from the cell
edge. A millisecond target or a single scripted crossing cannot override owner
play. For deliberate delivery delays, verify that ready terrain remains
playable and that stale data cannot publish. Confirm the result in owner play
on both clients before making it the default.

For browser frame pacing, retain the 960×540 logical UI, picking, and
camera projection. Evaluate 60 FPS with a 1920×1080 physical WebGL buffer
in both Firefox and Chrome. Measure a matched 960×540 physical buffer
with the same layout, route, and rendering settings to isolate the cost
of drawing more world pixels. Also compare high-percentile frame gaps
and the longest gaps. Entering cells must stay usable with ordinary
input and authoritative movement. [Physical output and UI evidence](seamless-world-streaming-preview.md#full-hd-world-output-with-the-original-ui)

## Stop conditions and follow-up decisions

- If movement still waits with coverage installed, trace the server hold, packet
  context check, or client region-load call. Fix that dependency before tuning
  delivery or expanding prefetch.
- If continuity passes with preinstalled coverage and fails during real
  streaming, separate the cost of delivery, preparation, publication, and GPU
  upload. Change the responsible stage and repeat the independent delay checks.
- If choppiness persists with resident cells, measure ordinary frame
  production, including sprites, the Java-to-browser handoff, and GPU
  composition. Use those measurements to decide whether a retained UI
  layer is worth prototyping. Require parity for UI size, legibility,
  input targets, and scaling before considering a broader redesign.
- Consider a cell-backed pathfinder only if the derived grid cannot support
  continuous coverage with correct paths and picking. Consider changing the
  package layout or splitting the transport only if measured delivery or
  preparation constraints survive bounded scheduling. Those decisions come
  after the first matched streaming milestone.
