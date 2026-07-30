# Renderer V2 Plan

This document tracks the alpha-era renderer rewrite effort for Spoiled Milk.
The working assumption is that this is the right time to make drastic
under-the-hood changes if they remove old engine limits, improve visual
stability, and make the client easier to work with.

For the current AI-facing summary and future planning map, start with
[renderer-and-shader-roadmap.md](renderer-and-shader-roadmap.md). This file is
the detailed renderer implementation ledger and historical checklist.

## Baseline Decision

Do not treat integer, bilinear, and bicubic scaling as the main problem.
Those settings expose or soften instability that starts earlier in the render
pipeline.

The target is a renderer-v2 path that can run beside the legacy renderer until
it reaches acceptable parity. The new path should prioritize stable frame
timing, explicit transparency, deterministic ordering, and a depth-buffered
world renderer.

Treat renderer-v2 as an HD-remaster style engine effort, not a compatibility
wrapper. When old rendering constraints only exist because of applet-era,
software-era, or fixed-resolution assumptions, prefer promoting that area to a
new renderer-v2 design over permanently adapting the new path around the old
constraint. Legacy behavior can remain as a fallback or parity reference, but
it should not be the default design target when a cleaner engine boundary is
available.

## Goals

- Reduce sprite, object, wall, and texture flicker during normal camera motion.
- Remove see-through texture behavior caused by implicit color-key rules.
- Replace applet-era framebuffer plumbing with direct frame ownership.
- Separate game simulation, scene rendering, scaling, and presentation.
- Keep the existing server protocol, gameplay logic, cache data, maps, and
  content definitions usable.
- Remove limits that only exist because of legacy protocol or client storage
  widths, such as byte-sized local entity counts, narrow server ids, fixed
  local arrays, and small coordinate windows. Temporary prioritization or
  eviction logic is acceptable for alpha stability, but it should be treated as
  a bridge toward wider remaster-era state packets and data structures.
- Preserve a legacy-renderer fallback until renderer-v2 is proven in alpha.
- Make future rendering work easier to reason about, test, and profile.

## Non-Goals

- Do not rewrite server gameplay as part of this project.
- Do not change game rules, combat behavior, or map data unless a renderer bug
  exposes bad data that must be fixed.
- Do not rely on blur as the primary visual-quality solution.
- Do not keep compatibility with applet-era rendering internals once the new
  path is stable.

## Current Problem Areas

- `PC_Client/src/orsc/ScaledWindow.java`
  - Performs scaling and presentation work in a Swing component.
  - Uses synchronous painting from the draw path.
  - Allocates or rebuilds scaling state during frame presentation.
  - Splits interpolation work across per-frame async image tasks in some modes.
- `PC_Client/src/orsc/ORSCApplet.java`
  - Uses `ImageProducer`, `ImageConsumer`, and intermediate image copies.
  - Bridges old applet rendering into the current desktop client.
- `Client_Base/src/orsc/graphics/two/GraphicsController.java`
  - Owns the main software pixel buffer.
  - Several sprite paths treat black or zero-like values as transparent.
- `Client_Base/src/orsc/graphics/three/Shader.java`
  - Several texture paths skip writes when the sampled texture value is zero.
  - This can make real black texture pixels behave as transparency.
- `Client_Base/src/orsc/graphics/three/Scene.java`
  - Uses old polygon sorting/painter behavior rather than a z-buffer.
  - Near-equal-depth faces and projected sprites can change draw order as the
    camera moves.
- `Client_Base/src/orsc/mudclient.java`
  - Mixes camera logic, world scene setup, entity sprite placement, overlays,
    and final draw orchestration in one large class.

## Target Architecture

Renderer-v2 should move toward these boundaries:

1. **Frame source**
   - Owns a direct framebuffer or render target.
   - Exposes a stable `int[]` or GPU texture target to the renderer.
   - Avoids applet-era `ImageProducer` handoff.

2. **World renderer**
   - Converts visible terrain, walls, models, objects, and projected sprites
     into draw commands.
   - Uses a depth buffer or a design that gives equivalent stable ordering.
   - Treats transparency as explicit metadata, not a magic RGB value.

3. **UI and overlay renderer**
   - Draws UI, chat, inventory, menus, combat overlays, and debug output after
     the world render.
   - Can remain software-rendered initially if that keeps the migration
     smaller.

4. **Scaler**
   - Scales the completed frame only after the frame is stable.
   - Provides selectable modes such as nearest, sharp filtered, bilinear, and
     bicubic.
   - Reuses destination buffers and avoids per-frame task allocation.

5. **Presenter**
   - Presents frames using a clear swap model.
   - Does not block the game render path on synchronous Swing painting.
   - Can be backed by Java2D, `VolatileImage`, `BufferStrategy`, or a GPU
     windowing path.
   - Starts with an opt-in `VolatileImage` GPU-presenter bridge before moving
     to a true OpenGL/LWJGL presenter.

## Preferred Technical Direction

Default to a z-buffered renderer-v2 implementation. If early geometry and
texture extraction proves clean enough, prefer a GPU-backed implementation over
another large software-only renderer. Hardware depth testing and texture
sampling would remove several classes of legacy rendering problems at once.

A modernized software renderer is still acceptable as an intermediate step if
it allows faster parity, but it should use the same renderer-v2 boundaries so a
GPU backend can replace it later.

## Current Alpha Rollout State

The current renderer-v2 rollout baseline is the OpenGL primary-window path with
the OpenGL static world replacement composite enabled. In this mode the client
still keeps the legacy renderer available as the data source and parity
reference, but the in-game world is no longer presented as a scaled copy of the
completed software framebuffer. The OpenGL path draws the captured static world
from renderer-v2 mesh data, draws legacy entity scene sprites directly through
OpenGL, redraws texture-correct projected static-world ranges between entity
groups using legacy scene order, then replays remaining visible scene sprites,
world-overlay sprites, and UI commands above it.

The first visually acceptable baseline is now:

- Direct framebuffer source with OpenGL primary presentation.
- Aspect-fit fullscreen/window scaling with selectable render-surface sizes.
- Captured terrain, walls, static objects, scenery, and wall objects promoted
  into typed renderer-v2 geometry.
- OpenGL texture atlas upload for legacy world textures, with padded atlas
  edges and texel-center UV sampling to prevent texture bleeding.
- Legacy draw-order preservation for the static-world color pass while the
  final depth-buffer path is still being proven.
- Legacy-derived terrain lighting restored in OpenGL: texture-backed faces use
  the old four-band texture shade curve, while flat resource-color terrain uses
  the old 256-step shade ramp from captured per-render-vertex light/fog
  values.
- Entity scene sprites now bypass software-visible recovery and are submitted
  through the OpenGL sprite path so NPCs, players, and ground item drops are
  not made invisible by stale framebuffer occlusion tests. The replacement
  composite then redraws only projected wall, roof, scenery, and wall-object
  occluders whose legacy draw order falls after each world-sprite group and
  before the next group. Terrain, water, and floor faces are no longer replayed
  over sprites in this pass. Projectiles, teleport bubbles, and remaining
  custom world effects still need the same explicit world-sprite treatment.
- Login/menu/loading frames remain on the uploaded software framebuffer until a
  captured 3D world mesh is ready, preventing stale or partial world geometry
  from appearing during load transitions.
- OpenGL-primary mode now uses a runtime-gated modern client loop
  (`spoiledmilk.modernClientLoop` / `SPOILED_MILK_MODERN_CLIENT_LOOP`) instead
  of the legacy adaptive sleeper. The legacy loop could flip between a ~1ms and
  ~10ms sleep after small camera changes, making FPS swing dramatically even
  when mesh triangles, draw calls, entities, and GL timing were effectively
  unchanged. The modern loop keeps fixed-cadence updates with bounded catch-up
  while allowing rendering to pace toward the 60 FPS OpenGL target.
- Login viewport sprites still force a one-shot legacy software world raster
  before `storeSpriteVert(...)`. Those animated login graphics are captured
  from the software framebuffer, so they must not inherit the in-game
  `skipLegacyWorldRaster` optimization or they can capture stale UI pixels such
  as the minimap.
- Resident world chunks now own scaled per-vertex face normals in their captured
  payload. Classic visuals still use the accepted shade bands, but chunk diffuse
  lighting reads this normal stream instead of recomputing normals from triangle
  coordinates during renderer upload. This is a no-visual-change foundation for
  Directional/Toon/remaster lighting, terrain slope controls, and future
  semantic shadow casters.
- Resident wall chunks now also capture conservative semantic shadow caster
  metadata before triangulation: wall base endpoints, base height, caster
  height, width, opacity, and outdoor-only state. The disabled shadow proof now
  prefers these semantic casters and keeps triangle-derived casters only as a
  fallback. This starts the remaster shadow path from world/build data rather
  than final draw triangles.
- Resident object chunks now capture one conservative model-level semantic
  caster per transformed `GAME_OBJECT`/`WALL_OBJECT`: footprint axis, base Y,
  height, width, opacity, and outdoor-only state. This avoids deriving scenery
  shadows from individual object triangles and gives future terrain
  shadow-mask/decal work a compact caster list.
- Roof visibility is now an explicit per-frame renderer state rather than an
  implied side effect of legacy scene membership. Covered ground tiles,
  upper-floor views, and the saved Hide Roofs option resolve to stable named
  states shared by the legacy scene loop and resident chunk filtering. The
  resident path also suppresses walls from planes above an indoor ground-floor
  player while preserving active-floor walls upstairs. `Ctrl+F9` metadata
  records the active plane and resolved roof state for visual correlation.
- Roof-option reloads now preserve the active section window rather than
  resolving a new window from the player's tile. This closes a 48-tile
  visual/picking desync at movement hysteresis edges, where normal movement
  intentionally retained the old window but a toggle previously loaded its
  neighbor without a coordinate rebase. Structured `roof.visibility.reload`
  events record the active section and player-to-section delta.
  Live validation exercised 35 toggles, including non-zero X and Z section
  deltas, without desynchronizing visuals, picking, or collision; all 12
  indexed capture frames passed strict offline analysis.
- Remaster shading diagnostics now separate terrain and non-terrain/object
  local-relief strength while retaining the accepted `Max`/`2.0` parity
  default for both. F6 and `Ctrl+F9` also distinguish fixed model-kind diffuse
  response from the terrain-only directional/contact shadow mask. Scoped
  runtime overrides allow visual attribution before any player-facing shading
  setting is promoted.
- Live diagnostic hotkeys now expose independent ten-step terrain relief,
  object relief, world dimness, and world contrast comparisons without
  persisting provisional player settings. The controls compose after both
  Classic and Remaster resident shading, report values on-screen and in
  structured diagnostics, and are mirrored by uncluttered two-line,
  ten-segment Graphics-menu bars with direct click/drag selection. Owner review
  doubled relief increments, retained the dimness range, and widened contrast
  from the former level-5 result through ten stronger comparison stops. The
  shader-side relief ceiling now matches the `4.5` control endpoint instead of
  flattening values above the old maximum.
  Expanded F6 is intentionally bounded to summary lines; full phase/channel
  detail stays in JSONL telemetry and `Ctrl+F9` artifacts.
- The disabled shadow proof now builds a frame-wide semantic caster list and
  hashes that list into the resident chunk cache. Terrain chunks receive one
  cached shadow receiver layer made from affected terrain triangles with
  accumulated alpha, instead of drawing one blended quad per caster. This keeps
  the proof opt-in while removing the worst transparent-geometry stacking
  behavior from earlier experiments.

This state is suitable as the first alpha rollout target because it fixes the
major terrain flatness/neon-color regression, keeps sprites readable, keeps UI
above the world, and leaves the legacy renderer available for comparison. It is
not the final architecture: the current world color pass still leans on fixed
function OpenGL state, CPU-built color arrays, legacy sorted order, and
software-visible non-entity sprite recovery.

## Renderer Ownership Contract

This section is the short source-of-truth for AI sessions changing renderer-v2.
Read it before changing sprite, occlusion, depth, world-composite, or
framebuffer-replay behavior.

### Alpha Remaster Strategy

- The renderer-v2 goal is an alpha-era remaster architecture, not a local
  micro-optimization exercise. Big rendering overhauls are allowed and
  expected while the game is still in alpha.
- Do not get bogged down in overly optimizing one aspect of the renderer. Lock
  down a major rendering surface enough that visuals, ordering, and release
  safety are acceptable, then move to the next large architectural swing.
- Avoid tunnel vision. If repeated optimization passes on one subsystem stop
  producing meaningful gains, step back and prioritize the next structural
  migration: retained/static world ownership, GPU-first depth and ordering,
  chunk/entity culling, shader/material ownership, or another broad renderer
  boundary.
- Keep the player-facing target stable: authentic RuneScape Classic spritework,
  objects, readability, and game feel, with a substantially modernized engine
  underneath.

### Ownership Map

- **Static world geometry**
  - Terrain, walls, roofs, game objects, wall objects, bridges, water, and
    scenery should be treated as renderer-v2 world geometry.
  - The current accepted path still uses captured legacy mesh/order data, but
    the long-term owner is resident chunk geometry with explicit material and
    depth metadata.
- **Entity and ground-item sprites**
  - Players, NPCs, and ground-item drops are OpenGL-owned in the current alpha
    path.
  - Do not restore these from software-visible framebuffer pixels.
  - Do not let a failed or fully occluded depth-mask pass fall back to direct
    full-sprite drawing. A zero-visible sprite is a valid hidden result.
- **Projectiles and world effects**
  - Projectiles, teleport bubbles, spell effects, and similar world overlays
    are migration targets.
  - Until promoted, treat their software-visible recovery as a temporary bridge
    and prefer explicit renderer-v2 commands over new compatibility masks.
- **UI and screen overlays**
  - Chat, menus, panels, inventory, minimap details, hit splats, nameplates,
    and debug text remain overlay/UI work unless explicitly promoted into a
    world pass.
  - These should draw after the world and world-sprite passes.
- **Legacy framebuffer**
  - The legacy framebuffer is a fallback, data source, and parity reference.
  - It should not become the source of truth for renderer-v2 world visibility
    when an explicit renderer-v2 command or depth result exists.

### Ordering And Occlusion Rules

- Static world color currently preserves legacy draw order where parity still
  depends on it.
- Sprite occlusion should use explicit renderer-v2 depth/kind data when
  available. Terrain, walls, roofs, game objects, and wall objects are valid
  entity-sprite occluders.
- Entity and ground-item sprites should be hidden by later/closer valid world
  occluders, but should not be hidden by stale framebuffer pixels.
- The replacement composite may replay exact owned wall, game-object, and
  wall-object faces around world-sprite groups while the explicit static
  geometry scene queue is pending. These `FRONT_OCCLUDER_RANGE` commands must
  carry model/face ownership and must stay narrow.
- Terrain, water, and floor faces should not be broadly replayed over sprites
  as a post-sprite overlay. If terrain must hide a sprite, that should happen
  through the depth/kind occlusion mask.
- Broad fallback redraws are risky. Prefer a narrow, inspectable ownership rule
  over a large replay range that happens to fix one scene.

### Known Bridge Code To Retire

- Legacy screen-space sprite command replay is still used as a bridge for some
  non-entity world effects and UI-adjacent overlays.
- Exact front-occluder redraws are a bridge until renderer-v2 can submit a
  single scene command queue with static geometry, entities, items,
  projectiles, effects, and overlay depth metadata.
- Fixed-function OpenGL world drawing is a bridge until the parity shader owns
  material sampling, shade bands, fog, alpha, and brightness.
- Projected frame-space mesh upload is a bridge until resident chunk geometry
  fully replaces captured projected world meshes.

### Current File Split Status

This section tracks the active renderer-file refactor that started because
`OpenGLFramePresenter.java` had become too large to safely maintain.

Current ownership after the first split:

- `OpenGLFramePresenter`
  - Owns frame orchestration, input-bridge delegation, high-level pass
    sequencing, capture triggering, and remaining UI/world composite glue.
  - Should continue shrinking. Do not move shader source, atlas internals,
    resident chunk upload, projected mesh upload, or shadow-mask generation
    back into this file.
- `OpenGLWindowController`
  - Owns the GLFW window handle, initialization and teardown, primary-monitor
    borderless/windowed transitions, saved windowed bounds, surface-size
    queries, event polling, buffer swaps, and native cleanup diagnostics.
  - Calls narrow presenter delegates for input release, settings persistence,
    logging, and client-close compatibility behavior.
- `OpenGLViewportPresenter`
  - Owns draw/framebuffer viewport computation, automatic primary-window
    aspect fit, mirror/debug scale modes, fractional-scale text smoothing, and
    window-to-source mouse coordinate mapping.
- `LwjglBindings` and `MonitorMode`
  - Own LWJGL reflection, OpenGL/GLFW constants, and monitor-mode queries.
  - The presenter should call this facade instead of growing more reflection
    or raw LWJGL lookup code.
- `OpenGLFrame.java` / `Frame`
  - Owns the per-frame source pixels, target sizing, renderer 2D/3D snapshots,
    debug overlay lines, and reusable frame-buffer lifetime.
- `Renderer3DFrame.WorldSpriteSnapshot`
  - Owns the frame-local link between one sorted sprite anchor, its world-space
    submission, optional character metadata, pick state, and the exact ordered
    2D animation layers captured for that anchor.
  - The OpenGL path consumes these groups only after whole-frame validation;
    older or inconsistent frames remain owned by the compatibility builder.
- `OpenGLCompositeSceneCommand`
  - Owns package-local composite scene-command, world-sprite command,
    static-world command, and related sprite diagnostic records.
- `OpenGLCompositeSceneBuilder`
  - Owns pure composite scene-command assembly, legacy world-sprite
    classification, sprite-anchor matching, front-occluder face-key selection,
    and capture/static material command derivation.
- `OpenGLWorldSpriteDrawController`
  - Owns high-level world-sprite draw orchestration: scene-command consumption,
    same-anchor character-layer grouping, dynamic atlas upload decisions,
    fallback routing, and anchored depth-owned sprite submission.
  - Still calls presenter bridge callbacks for projection setup, fallback
    screen-space draws, and capture-only depth diagnostics.
- `OpenGLWorldSpriteRenderer`
  - Owns camera-space world-sprite quad VBO/EBO lifecycle, depth interpolation,
    day/night tone application for sprite quads, and GPU submission for
    depth-owned NPC/player/item billboards.
- `OpenGLSpriteTextureBuilder`
  - Owns CPU-built command-sized dynamic sprite textures, including direct
    sprite texture rebuilds and same-character layer compositing.
  - This is still a bridge texture path, but it is no longer presenter-owned.
- `OpenGLShaderProgram`
  - Owns GLSL source strings, shader compilation/linking, attribute binding,
    and shader uniform setup.
  - New visual inputs should usually be shader uniforms or explicit vertex
    attributes here, not baked ad hoc into presenter code.
- `OpenGLWorldChunkRenderer`
  - Owns resident chunk VBO/EBO upload, chunk material batches, resident
    terrain/wall/roof/object draw calls, chunk culling, resident shader
    attributes, and terrain shadow-mask application/debug drawing.
  - This is the current owner for static world geometry.
- `OpenGLWorldMeshRenderer`
  - Owns the projected/captured world-mesh bridge.
  - Treat this as compatibility/diagnostic support. New renderer features
    should prefer resident chunk ownership instead of expanding this bridge.
- `OpenGLTextureAtlas`, `OpenGLDynamicTextureAtlas`, `OpenGLTextureRegion`,
  `OpenGLTexturePixels`, `OpenGLSpriteTextureCache`, `OpenGLGlyphTextureCache`,
  and `OpenGLWorldTextureCache`
  - Own texture upload, atlas packing, cached sprite/glyph/world texture
    regions, and pixel conversion.
  - Texture transparency rules should be centralized here or in material
    classifiers, not repeated in presenter draw paths.
- `OpenGLFrameCapture` and `OpenGLRendererLog`
  - Own renderer diagnostics, capture file emission, and OpenGL log messages.
  - Keep capture expansion here unless the data source belongs in a renderer
    owner first.
- `OpenGLStaticWorldMaterials` and `StaticWorldMaterialPass`
  - Own coarse material classification and static-world material pass labels.
  - Future material metadata should build on this rather than reintroducing
    magic texture checks in draw code.
- `RemasterShadowClassifier` and `RemasterShadowMaskBuilder`
  - Own shadow caster/receiver inspection and the CPU-built terrain shadow-mask
    proof.
  - They are shadow-system code, not presenter code. A later GPU/incremental
    shadow pass should replace or supplement them without merging them back
    into the presenter.

Current split completeness:

- Done enough for this pass: low-level LWJGL access, shader compilation,
  texture atlas/cache work, resident chunk drawing, projected mesh drawing,
  frame capture, renderer logging, static material labels, composite
  scene-command records, pure scene-command builder/classifier logic,
  world-sprite draw orchestration, command-sized sprite texture building,
  camera-space world-sprite quad submission, initial bridge/owner code labels,
  and remaster shadow mask helpers are separated from the presenter.
- Still in the presenter: input bridge, pass sequencing, UI replay, projection
  setup, capture-only depth diagnostics, and some
  compatibility decisions. These are
  acceptable short-term, but any new large renderer behavior should get its own
  owner instead of expanding the presenter.
- Not done: extracting a full static/entity scene-queue owner, native UI
  renderer ownership, and final deletion of bridge paths after the fallback
  window closes.

Follow-up window UX: borderless OpenGL-primary mode has no native title-bar
close control, so desktop users currently exit with `Alt+F4`. Add a clear
in-client close/exit affordance in a separate UI branch, preserving the native
window-close lifecycle and testing accidental-click protection. This is not a
window-controller extraction requirement.

Replacement safety contract: legacy world raster, projected world capture,
and software object ownership may be skipped only while an initialized OpenGL
presenter is available to replace them. Configuration flags express desired
features, not runtime readiness. Presenter initialization, failure, and cleanup
must update the shared readiness gate so disabled or failed OpenGL always
returns to a complete software presentation.

### Legacy Bridge Labeling Rules

When touching renderer-v2 code, separate active design from compatibility
bridges loudly enough that future AI sessions do not resurrect old paths.

- Use a short `LEGACY BRIDGE:` comment on code that still depends on legacy
  framebuffer pixels, projected software draw order, fixed-function OpenGL
  color arrays, old scaler settings, or software-visible sprite recovery.
- Use a short `RENDERER-V2 OWNER:` comment when a path has become the intended
  owner for a surface, such as resident chunks for static world geometry or
  shader uniforms for presentation-only lighting state.
- Do not add new behavior to a `LEGACY BRIDGE` path unless the change is a
  narrow parity fix and the long-term owner is named in the nearby comment or
  this document.
- Old software-presenter scaling modes and `ui_scale`/`scaling_scalar`
  settings are legacy-presenter compatibility. OpenGL-primary uses aspect-fit
  presentation and render-surface/aspect settings; do not reintroduce the old
  scaler as a modern graphics option.
- Projected mesh upload, screen-space sprite command replay, and exact
  front-occluder range replay are compatibility paths. If a new bug appears in
  one of those areas, first ask whether the fix belongs in resident chunks,
  world-space sprites, shader/material ownership, or an explicit scene queue.
- If a bridge is still needed for release safety, document what keeps it alive
  and what condition allows it to be removed.

### Debugging Guidance

- If an entity disappears, first check ownership and slot/protocol visibility,
  then verify the sprite command and anchor reach the OpenGL path.
- If an entity appears on top of terrain, walls, roofs, or scenery, first check
  the depth-kind occluder set and whether a zero-visible masked sprite is being
  converted into a direct full-sprite fallback.
- If a death or despawn sprite lingers, first check server visibility/removal
  state and the client local-entity cache before adding renderer workarounds.
- For deep renderer diagnostics, relaunch with
  `SPOILED_MILK_OPENGL_FRAME_CAPTURE=true` and use `Ctrl+F9` burst captures
  before changing ordering rules. Release/default clients keep this capture
  hotkey disabled. Keep the same frame range for legacy source, depth-kind,
  entity-occluder mask, OpenGL world, overlays, final output, sprite commands,
  and sprite anchors.
- Use the capture analyzer's entity visibility health, anchor match modes,
  anchor geometry context/outliers, and live depth evaluations before changing
  world-sprite ownership. `strict-id-bounds` anchor matches with nonzero
  geometry deltas usually mean the anchor identifies the right full entity,
  while the concrete command is a cropped body/animation piece. Command
  crop/placement data still needs to be understood before replacing the
  screen-space command bridge.
- Treat repeated software-visible recovery failures as a sign that ownership
  should move into explicit renderer-v2 passes.

## Shader Roadmap

The next large visual-quality step should be a shader-backed world renderer,
but it should be built in two deliberate stages.

Status note: the projected world shader, resident chunk parity shader,
resident raw-material inspection shader, and resident remaster lighting shader
now exist. The remaining shader roadmap is no longer about proving GLSL can
draw the world; it is about moving visual ownership out of fixed-function and
baked CPU geometry state into explicit shader/material inputs.

1. **Parity shader**
   - Reproduce the current accepted look before adding new visual effects.
   - Move texture sampling, color-key alpha, flat resource-color shade ramps,
     four-band legacy texture shading, fog, and per-vertex light interpolation
     into GLSL.
   - Treat legacy binary terrain data as the source of truth through the
     generated terrain mesh, normals, tile splits, overlays, texture ids, and
     captured light/fog values.
   - Use explicit material metadata instead of relying on magic texture ids or
     first-texel behavior.
   - Keep a side-by-side software/OpenGL capture mode so parity can be checked
     by frame diff, not only by visual tuning.

2. **Polish shader**
   - Add optional improvements only after parity is stable.
   - Support configurable saturation/contrast and fog response through named
     renderer settings with conservative defaults.
   - Add higher-quality distance fade, optional dithered fog bands, and
     material-aware brightness correction for terrain, water, walls, roofs,
     scenery, and sprites.
   - Explore normal-derived terrain/slope emphasis for higher render-surface
     sizes, but keep it separate from the parity shader so it can be disabled
     if it changes the classic look too much.
   - Keep low-resolution art crisp by default; do not use blur as a substitute
     for correct geometry, ordering, alpha, or lighting.

Long term, the shader path should replace the fixed-function OpenGL arrays with
explicit VBO attributes for position, UV, material id, model kind, legacy
light/fog, alpha mode, and draw/depth metadata. That will make visual changes
auditable, let us add proper GPU sprite/material passes, and remove more of the
old renderer's applet-era assumptions without losing a known-good baseline.

## Visual Overhaul Backlog

These are worthwhile renderer-v2 expansions that remain open beyond the
current alpha baseline:

- **GPU sprite world pass**
  - Promote players, NPCs, ground items, projectiles, spell effects, and other
    billboard sprites into a real OpenGL world pass.
  - Current first slice: legacy entity scene sprite commands are drawn directly
    by OpenGL instead of being reconstructed from software-visible pixels, and
    projected sprite occluders are redrawn in legacy-order ranges around
    entity groups. Ground item drop sprites are now also tagged with their
    legacy scene sprite ids and routed through the same ordered OpenGL
    world-sprite pass.
  - Current command-boundary slice: OpenGL composite entity/item drawing builds
    typed world-sprite commands that pair the exact legacy sprite command
    crop/mirror/skew/alpha data with the renderer-v2 depth anchor, anchor match
    mode, and legacy draw order. `Ctrl+F9` captures write
    `world-sprite-commands.tsv` so this boundary can be audited offline.
  - Accepted grouped-snapshot slice: each sorted renderer anchor owns one
    frame-local world-sprite snapshot linked to its sprite submission,
    character metadata, pick state, and ordered captured animation layers.
    Complete indexed/order/count validation lets the presenter consume these
    groups directly; any discrepancy routes the whole frame through the typed
    command builder. `world-sprite-snapshots.tsv` and stable group/layer/
    compatibility-fallback counters make that decision auditable. The owner
    visual route and 12 strict frames accepted `e3cced146` with 4,852/4,852
    snapshot-owned layers and zero compatibility fallback, invalid anchor/
    order, suspicious visibility, failed frame, or client exception.
  - Current scene-queue slice: the OpenGL replacement composite consumes a
    behavior-preserving scene command queue and captures it as
    `scene-commands.tsv`. The queue currently emits typed world-sprite commands
    only; static-world range commands are defined but not emitted until their
    depth and ordering rules are narrow enough to avoid covering shifted combat
    silhouettes.
  - Static range interleave candidates are captured separately as
    `static-range-candidates.tsv`. They are diagnostic only and must not be
    drawn until a capture proves the exact occluder subset is narrow enough.
    Candidate rows include total static faces plus screen-bounds overlap counts
    by terrain, wall, roof, game-object, and wall-object kind against
    world-sprite command bounds. The capture analyzer ranks the worst
    `staticRangeOutliers` by overlapping sprite commands/faces so future
    promotion can start with narrow low-risk ranges instead of broad replay.
    A separate `front-occluder-candidates.tsv` stream captures only overlapping
    wall, game-object, and wall-object faces from those ranges, excluding
    terrain by default as the safer first live-promotion target. The analyzer
    reports `expectedFromStaticOverlap` so the stream can be checked against
    the static overlap subset before any live rendering uses it. The first
    live experiment used exact `FRONT_OCCLUDER_RANGE` ownership before
    camera-space sprite depth replaced it. Candidate capture remains diagnostic,
    but the runtime replay command and flag have been retired.
  - Next slice: replace the remaining legacy screen-space command dependency
    with an explicit renderer-v2 scene command queue for static geometry,
    entities, items, projectiles, spell effects, and other world overlays.
  - Preserve depth anchors, draw order, alpha, clipping, mirroring, and skewed
    sprite transforms without relying on software-visible pixel recovery.
  - Define transparent and semi-transparent sprite sorting rules before making
    this path source-of-truth.
- **Frame capture and replay diagnostics**
  - Use burst renderer-v2 frame dumps before changing more ordering rules.
  - Capture legacy source, depth-color, depth-kind, entity-occluder mask,
    OpenGL world, scene restore, overlays, debug overlay, final output, sprite
    commands, sprite anchors, character projections, per-character body command
    stats, and entity-restore stats for the same frame range.
  - Capture per-command entity depth evaluations with live occluder kind,
    dominant occluder face/model, anchor match mode, and command-vs-anchor
    geometry deltas so sprite ownership changes can be audited offline.
  - Treat repeated software-visible sprite recovery failures as evidence to
    move ownership into explicit renderer-v2 passes instead of adding more
    compatibility masks.
- **Static world depth ownership**
  - Move terrain, walls, scenery, object models, roofs, bridges, water, and
    wall objects onto a coherent GPU depth model.
  - Replace legacy painter-order exceptions with deterministic depth and tie
    rules where possible, while documenting any intentional legacy matches.
- **Texture and material cleanup**
  - Convert legacy texture/palette transparency into explicit material
    metadata.
  - The initial world material contract is `OPAQUE` for flat colors and fully
    opaque textures, `CUTOUT` for textures containing the legacy transparent
    palette key, and `TRANSLUCENT` for future partial-alpha materials. Fully
    transparent legacy face sentinels are `DISCARDED`; missing texture data is
    `UNRESOLVED` and must fail strict capture analysis.
  - Preserve true black texture pixels without special black-to-1 behavior.
  - Add material categories for terrain, water, roof, wall, object, foliage,
    ore, and effect surfaces so shader work is data-driven.
  - The proposed parity-preserving resident-world metadata, shader-input, and
    diagnostic foundation is specified in
    [renderer-material-family-foundation-plan.md](renderer-material-family-foundation-plan.md).
- **Camera, zoom, and render-surface expansion**
  - Stress test larger render surfaces and mouse-wheel zoom limits now that the
    OpenGL world can draw a wider scene.
  - Record the first failure point for sprite placement, wall/object ordering,
    minimap clipping, UI panels, and projectiles before making expanded values
    normal options.
  - Audit camera rounding and section-load timing so increased draw distance
    does not reintroduce flicker or hitching.
- **Native UI and options cleanup**
  - Move the remaining UI primitives, text, minimap details, menus, and panels
    to native renderer-v2 drawing only after coverage is measurable.
  - Redesign the options menu so normal players see renderer/display settings
    rather than alpha debug flags.
  - Keep debug toggles available for testing, but separate them from shippable
    quality settings.
- **Scaler and presentation polish**
  - Add a sharp pixel-art-aware scaling mode after the renderer output itself
    is stable.
  - Verify integer, bilinear, bicubic, sharp filtered, and fullscreen modes
    against the same baseline routes.
  - Keep world clarity and UI readability as separate concerns if high
    resolutions make a single scale policy insufficient.
- **Validation tooling**
  - Add scripted camera routes and frame capture/diff tooling for known
    problem areas.
  - Track terrain-light parity, sprite depth, see-through black pixels,
    minimap clipping, section-load hitches, and scaling artifacts as distinct
    renderer-v2 bug classes.
  - Require visual checkpoints before promoting renderer-v2 from opt-in alpha
    to default.

## Current Opt-In Renderer Flags

These flags are intentionally separate so alpha testers can isolate each
renderer-v2 layer:

- `SPOILED_MILK_RENDERER_TELEMETRY=true`
  - Enables frame, scene, presentation, scaler, Java2D GPU presenter, and
    OpenGL presenter timing logs.
- `SPOILED_MILK_DIRECT_FRAMEBUFFER=true`
  - Bypasses the applet-era `ImageProducer` frame path and wraps the software
    framebuffer directly.
- `SPOILED_MILK_GPU_PRESENTER=true`
  - Uses the Java2D `VolatileImage` presenter bridge inside the Swing viewport.
- `SPOILED_MILK_OPENGL_PRESENTER=true`
  - Starts the LWJGL/OpenGL presenter probe. By itself this opens a separate
    mirror window and uploads the software framebuffer as a texture.
- `SPOILED_MILK_OPENGL_INPUT=true`
  - Enables the experimental GLFW input bridge for the OpenGL mirror. It polls
    cursor position and mouse buttons, receives native key, text, focus, and
    mouse wheel callbacks, then forwards AWT events into the existing applet
    input handlers.
- `SPOILED_MILK_OPENGL_PRIMARY_WINDOW=true`
  - Hides the Swing client window and uses the OpenGL window as the only
    visible client window. This implies the OpenGL input bridge and should be
    used for field testing so telemetry reflects a single visible client.
- `SPOILED_MILK_RENDER_SURFACE_MODE=4:3|800x600|16:9|960x540`
  - Selects the player-facing aspect/source framebuffer. The in-game option is
    `Graphics > Aspect Ratio`, where `4:3` maps to `800x600` and `16:9` maps to
    `960x540`. Older saved or runtime values such as `512x346`, `1280x720`, and
    `1920x1080` are migrated by aspect to one of those two active modes.
- `SPOILED_MILK_OPENGL_SCALE_MODE=aspect-fit|integer-fit|stretch`
  - Legacy/debug fit-policy override for non-primary OpenGL mirror testing.
    OpenGL-primary ignores this setting and always uses automatic aspect-fit
    presentation with black bars when the window and selected resolution do not
    match.
- `SPOILED_MILK_OPENGL_WINDOW_MODE=windowed|borderless-fullscreen`
  - Selects the OpenGL primary-window mode. `borderless-fullscreen` uses the
    primary monitor's current desktop mode without changing monitor resolution.
- `SPOILED_MILK_OPENGL_UI_FONT=legacy|h11p|h12p|h13b|h14b`
  - Selects the OpenGL-primary UI body-font policy. `legacy` keeps the old
    `h12b.jf` body font. The other modes remap legacy body text to the named
    bundled face so alpha testing can compare readability without a relaunch.
- `SPOILED_MILK_OPENGL_SPRITE_OVERLAY=true`
  - Enables the first atlas-backed 2D sprite overlay probe. The software
    framebuffer still renders normally; simple full-sprite draw calls are
    captured and replayed from the OpenGL atlas on top for validation.
- `SPOILED_MILK_OPENGL_SPRITE_OVERLAY_MODE=safe|visible|phased|native-ui|geometry`
  - Selects the sprite overlay replay policy. `safe` skips commands that need
    original draw order, `visible` visibility-filters every replayed sprite
    against the completed software frame, `phased` replays UI-phase commands
    directly while visibility-filtering scene/world commands, `native-ui`
    additionally omits captured full-opacity UI sprites from the software
    framebuffer so OpenGL becomes their source of truth, and `geometry` forces
    raw commands for debugging and can draw world/entity sprites above later
    software pixels.
- `SPOILED_MILK_OPENGL_UI_BASE_FRAME=true`
  - Diagnostic mode for `native-ui`. When enabled, OpenGL may present the
    pre-UI scene/world framebuffer and then draw captured UI commands on top,
    but only on frames where the command capture marks the native UI base as
    eligible. Unsupported post-base UI writes still fall back to the complete
    software framebuffer.
- `SPOILED_MILK_OPENGL_NATIVE_UI_REPLACE=true`
  - Experimental destructive mode for `native-ui`. Without this flag,
    `native-ui` is capture/telemetry-only and the complete software framebuffer
    remains the source of truth for NPC sprites, menus, messages, and minimap.
- `SPOILED_MILK_RENDERER_DEBUG_OVERLAY=true`
  - Enables the compact renderer-v2 overlay inside the presented framebuffer.
- `SPOILED_MILK_RENDERER3D_GEOMETRY_CAPTURE=true`
  - Captures visible legacy world model faces into a renderer-v2 geometry
    frame after frustum/orientation checks. This is a diagnostic bridge for
    the depth-buffered and GPU world renderer work; it does not change the
    displayed frame by itself.
- `SPOILED_MILK_RENDERER3D_VISIBLE_WORLD=true`
  - Draws the captured static world through the software diagnostic depth
    buffer before overlays and UI. This is an early visible renderer-v2 world
    prototype, flat-colored for validation before the OpenGL texture/shader
    backend replaces it.
- `SPOILED_MILK_OPENGL_WORLD_MESH=true`
  - Uploads the captured renderer-v2 world mesh through OpenGL VBO/EBO buffers
    and reports GPU-upload counts without changing the visible frame.
- `SPOILED_MILK_OPENGL_WORLD_MESH_VISIBLE=true`
  - Also draws the uploaded mesh over the frame with a screen-space depth test.
    This is intentionally diagnostic because un-clipped legacy triangles can
    cover unrelated screen regions until the GPU world path owns clipping and
    projection.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_VISIBLE=true`
  - Draws active resident world-space chunk VBO/EBO buffers as a wireframe
    diagnostic using the captured legacy camera matrix. This validates the
    chunk upload/projection contract without replacing the current accepted
    static-world composite.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_FILLED_VISIBLE=true`
  - Draws the same active resident chunk buffers as filled diagnostic
    material batches keyed by model kind, texture id, and fallback color. This
    is the first comparison path for replacing the projected world mesh with
    world-space chunk buffers.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE=true`
  - Draws texture-backed resident chunk material batches from the shared world
    texture atlas using chunk VBO texture coordinates and texture-light
    channels. Transparent-front flat batches draw with resolved fallback RGB;
    texture-backed batches without a valid atlas region fall back to average
    texture color when texture data exists. Texture-backed atlas batches are
    split into opaque and transparent passes. This is now part of the default
    static-world resident chunk path after terrain, wall, roof, roof-toggle,
    scenery bridge, and sprite-ordering parity were visually validated on
    2026-06-26.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE=true`
  - Requests textured resident chunk replacement. This is default-enabled for
    static terrain, walls, and roofs, but still fail-closes to the projected
    world unless resident readiness reports drawable terrain batches and
    trusted ownership is enabled.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT=true`
  - Allows resident chunks to suppress projected terrain, walls, roofs, game
    objects, and wall objects after readiness checks pass. This is
    default-enabled for the accepted resident world ownership path.
- `SPOILED_MILK_OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS=true`
  - Draws `GAME_OBJECT` and `WALL_OBJECT` scenery from resident chunk buffers
    instead of the projected object bridge. Object faces emit separate
    front/back material triangles, one-sided classic details are mirrored onto
    both culling windings, and captured front/back legacy side lighting is used
    directly. The projected object mesh export is skipped in this mode, but
    the software depth frame still includes walls, roofs, game objects, and
    wall objects so entity sprites can be clipped behind world occluders.
    After 2026-06-26 visual validation, this is default-enabled for the
    release client.
- `SPOILED_MILK_OPENGL_WORLD_TEXTURED_VISIBLE=true`
  - Draws texture-backed renderer-v2 terrain triangles from the OpenGL world
    texture atlas using atlas-space mesh UVs. It defaults to an opaque
    transparent-key cutout pass for parity testing, and can still be dialed
    down into a translucent comparison overlay through the alpha flag below.
- `SPOILED_MILK_OPENGL_WORLD_TEXTURED_STATIC_VISIBLE=true`
  - Also includes walls, roofs, scenery, static objects, and wall objects in
    the textured diagnostic. This remains intentionally separate while the
    world-sprite and alpha-ordering paths are still diagnostic.
- `SPOILED_MILK_OPENGL_WORLD_STATIC_TEXTURES=true|false`
  - Controls whether walls, roofs, scenery, static objects, and wall objects
    sample the OpenGL world texture atlas. It defaults on with the static
    textured world path; set it false to force the flat average-material
    fallback while debugging UV/order issues.
- `SPOILED_MILK_OPENGL_WORLD_TEXTURED_ALPHA=0.0..1.0`
  - Controls the visible textured OpenGL world diagnostic opacity. It defaults
    to `1.0` for parity testing; use `0.55` to restore the older translucent
    comparison overlay.
- `SPOILED_MILK_OPENGL_WORLD_REPLACEMENT_COMPOSITE=true|false`
  - Lets the visible textured/static OpenGL world replace the old full-frame
    software base for in-game frames, then replays anchored scene/entity
    sprites, world-overlay visible sprites, and captured UI commands over that
    world. It defaults on when the textured static world path is active,
    because drawing the GPU world over the final software frame cannot produce
    stable ordering. Login/menu/loading frames without captured 3D world mesh
    keep using the uploaded software framebuffer and do not run replacement
    replay.
- `SPOILED_MILK_OPENGL_WORLD_SPRITES_VISIBLE=true`
  - Draws matched scene/entity sprites from captured renderer-v2 depth anchors
    through the OpenGL sprite atlas as a translucent depth-tested diagnostic.
    This requires a visible OpenGL world mesh pass so sprites compare against a
    fresh depth buffer. It enables the 2D command capture stream needed to map
    anchors back to concrete sprite assets without enabling the older
    after-frame sprite overlay replay.

When the OpenGL-primary path is active, the in-game general options panel
exposes player-facing renderer rows under `Graphics`: `Preset`, `Aspect Ratio`, `Borderless`, `Lighting`, `Geometry`,
`Terrain Variation`, and `Fog`, followed by two-line `Terrain shading`,
`Object shading`, `Brightness / dimness`, `Contrast`, `Gamma`, and `Saturation`
sliders. The old `Video` section,
free-form `Resolution` row, manual `Tone` row, and superseded `Brightness` row
are retired.
`Aspect Ratio` is the source-framebuffer choice: `4:3` uses `800x600`, and
`16:9` uses `960x540`.
Wider field of view is handled by camera zoom rather than by exposing many
framebuffer sizes. Camera tilt and extended zoom are now default-on baseline
camera behavior rather than player-facing option rows; launch properties/env
vars remain available only for diagnostics.
`Preset` provides `Classic`, `Remaster`, and `Custom`. `Classic` applies `4:3`,
Borderless On, Classic lighting, Smooth geometry, neutral Day tone, Fog On, and
tuning levels `18/18/14/7` (terrain/object/brightness-dimness/contrast).
`Remaster` applies `16:9`, Borderless On,
Directional lighting, Smooth geometry, the server-synced day/night Cycle tone,
Fog On, and the baseline `10/10/10/10` tuning levels. Manual edits to any bundled row
mark the preset as `Custom`.
Fresh installs default to `Remaster`; existing saved settings are migrated by
aspect and retained as much as possible. `Geometry` offers Smooth, Faceted, and
Wire proof modes. Tone is now an internal day/night presentation state instead
of a player option. The accepted first target is `Sunrise Amber` for dawn,
`Rose Dusk` for dusk, `Cool Night` for the brief night edges, and `Deep Blue`
for most of night. The cycle consumes the server-owned 60-minute world clock
when connected to a custom server and locally interpolates between sync packets;
before a sync arrives it falls back to a local 60-minute preview clock.
Dimness supersedes the old Brightness choice so two controls cannot compound
the same presentation concern. The hidden internal brightness state remains
neutral for day/night calculations, while persisted Dimness owns player world
darkening. Contrast independently reshapes dark and light colors. Terrain and
object relief are persisted separately and apply after either Classic or
Remaster base shading. Both presets default terrain/object relief to level 5
(`2.0`), Dimness to level 1 (`1.0`), and Contrast to level 1 (`1.2`).
Automatic day/night presentation must not animate
`RendererDayNightCycle.currentBrightnessMultiplier()` directly, because the
world mesh and resident chunk paths bake/signature brightness; transition
dimming belongs in tone RGB uniforms until brightness is fully shader-owned.
`Sunrise Amber` and `Rose Dusk` are intentionally stronger than the first
preview constants so those phases are visible during normal play. Night edge
tones cross-fade between dusk, `Cool Night`, and `Deep Blue` instead of hard
switching filters. `Borderless` toggles between decorated windowed OpenGL and
borderless fullscreen. It defaults on for fresh/default settings and is
reapplied by both Classic and Remaster presets, while testers can explicitly
turn it off when investigating resize or window-manager issues. `Fog` is
persisted and participates in the
Classic/Custom preset state. It is intentionally binary after alpha testing:
`On` uses the accepted 28-to-40-tile camera-depth fade and keeps the far endpoint
as the scene/OpenGL projection cutoff, while `Off` removes the fog-light
contribution entirely and extends scene culling to the full loaded 3x3 world.
Older saved `Close`/`Far` fog values are migrated to `On`. The experimental
`SPOILED_MILK_OPENGL_WORLD_CHUNKS_SPATIAL_CULL=true` backend is available as an
A/B performance switch for resident chunk replacement. It subdivides world
material batches into spatial cells so fog/draw-distance culling can skip
terrain, wall, and object work before issuing draw calls. Keep it guarded until
dense-area captures prove the lower triangle count outweighs the extra
batch/draw-call pressure.
The legacy Interface `Fog` on/off row has been retired. Its server-backed byte
is still accepted for protocol compatibility, but it no longer alters camera
fog generation or the rendered frame; `Graphics > Fog` is the sole
player-facing fog control.
OpenGL-primary body-font remapping remains a hidden compatibility path for old
runtime/client settings. It is no longer exposed in the release-facing options
menu after the generated-font readability experiment was retired.
The F6 debug overlay uses a separate 13pt monospaced font so the telemetry is
more readable at 1280x720 and fullscreen desktop presentation without making
the main UI overflow. `F6`
still toggles the renderer debug overlay for development, but release/default
clients no longer expose quick function-key toggles for window mode,
aspect ratio, font, scaling mode, or scale size. Font remapping is hidden
compatibility-only; other supported settings must be changed through the
options menu or explicit runtime launch configuration. The legacy
software-presenter scaling controls and integer/bilinear/bicubic labels remain
available only outside OpenGL-primary fallback presentation.

Before using the OpenGL presenter locally, run:

```bash
./scripts/download-lwjgl.sh
./scripts/build-client.sh
```

The download script pins LWJGL to `3.3.4` by default and stores local jars under
`PC_Client/lib/lwjgl/`. The Ant build includes nested library jars when they
are present, while normal non-OpenGL builds still compile without LWJGL.

The current alpha renderer-v2 visual baseline uses these runtime flags:

```bash
SPOILED_MILK_DIRECT_FRAMEBUFFER=true
SPOILED_MILK_OPENGL_PRESENTER=true
SPOILED_MILK_OPENGL_INPUT=true
SPOILED_MILK_OPENGL_PRIMARY_WINDOW=true
SPOILED_MILK_RENDERER3D_GEOMETRY_CAPTURE=true
SPOILED_MILK_OPENGL_WORLD_MESH=true
SPOILED_MILK_OPENGL_WORLD_TEXTURED_VISIBLE=true
SPOILED_MILK_OPENGL_WORLD_TEXTURED_STATIC_VISIBLE=true
SPOILED_MILK_OPENGL_WORLD_STATIC_TEXTURES=true
SPOILED_MILK_OPENGL_WORLD_TEXTURED_ALPHA=1.0
SPOILED_MILK_OPENGL_WORLD_SPRITES_VISIBLE=true
SPOILED_MILK_SKIP_LEGACY_WORLD_RASTER=true
```

Telemetry and the debug overlay remain recommended for local validation, but
they are not visual requirements for the baseline.

## Migration Checklist

### Phase 0: Baseline Capture

- [ ] Pick three to five known bad visual-acuity locations.
- [ ] Record exact coordinates, camera angle, zoom, roof mode, and scaling mode.
- [ ] Capture short reference clips or frame sequences for each location.
- [x] Add frame timing around scene render, framebuffer commit, scaling, and
      presentation.
- [ ] Add counters for world section load, texture decode, sprite draw count,
      polygon count, and frame allocation count.
- [ ] Define pass/fail examples for flicker, see-through textures, camera
      hitching, and unacceptable blur.

### Phase 1: Direct Framebuffer and Presentation

- [x] Introduce an opt-in direct framebuffer path for the desktop client.
- [x] Back the legacy software renderer with a direct `BufferedImage` or
      equivalent render target.
- [x] Remove `ImageProducer` from the active renderer-v2 frame path.
- [x] Remove unnecessary copy steps between software pixel data and the final
      displayed frame for the opt-in renderer-v2 path.
- [x] Replace synchronous `paintImmediately` frame delivery with a stable
      present/swap model.
- [x] Reuse presentation source buffers across frames.
- [x] Reuse smooth-scaling destination buffers across frames.
- [ ] Keep the legacy renderer selectable for comparison.

### Phase 1A: GPU Presenter Bridge

- [x] Add an opt-in Java2D `VolatileImage` presenter path.
- [x] Track GPU-presenter timing separately from software smooth scaling.
- [x] Add recursive client library packaging for optional renderer backends.
- [x] Add an LWJGL dependency bootstrap script for local OpenGL validation.
- [x] Add an opt-in OpenGL/LWJGL framebuffer mirror that uploads the software
      framebuffer as a texture.
- [x] Track OpenGL snapshot, upload, render, and dropped-frame telemetry.
- [x] Reuse pooled direct OpenGL frame buffers instead of allocating a heap
      pixel snapshot every frame.
- [x] Add an opt-in GLFW input bridge for basic keyboard, cursor, and mouse
      button forwarding into the legacy applet handlers.
- [x] Add an opt-in OpenGL-primary launch mode that hides the Swing client
      window while keeping the legacy frame source internally available.
- [x] Move fullscreen scaling policy into the OpenGL viewport presenter with
      aspect-fit, integer-fit, and debug stretch modes.
- [x] Lock OpenGL-primary presentation to automatic aspect-fit bars so
      resolution changes alter the source framebuffer instead of exposing
      stretch/scaler choices to players.
- [x] Add scale-aware OpenGL presentation smoothing: exact integer viewport
      scales stay nearest-neighbor crisp, while fractional window scales blend
      in linear filtering with strength based on distance from the nearest
      integer scale.
- [x] Remap OpenGL mouse coordinates through the actual drawn viewport instead
      of the whole window surface.
- [x] Add in-game options and `F8` cycling for actual render-surface size
      modes.
- [x] Retire OpenGL fit from the player-facing options menu; keep the old fit
      override only for non-primary mirror/debug testing.
- [x] Add an in-game Graphics option for windowed versus borderless-fullscreen
      OpenGL primary mode. The old quick function-key toggle was removed for
      release-facing builds.
- [x] Add in-game options and `F6` cycling for a renderer-v2 debug overlay.
- [x] Forward OpenGL mouse wheel scroll into the existing zoom and scroll
      handlers.
- [x] Skip hidden Swing viewport buffering when OpenGL-primary mode owns
      presentation.
- [ ] Field-test GPU presenter across integer, bilinear, and bicubic modes.
- [ ] Decide whether the Java2D GPU presenter is worth keeping after a true
      OpenGL presenter exists.
- [x] Promote the OpenGL presenter from mirror/probe mode to the primary client
      window with GLFW input, focus, resize, and shutdown handling.
- [x] Replace remaining polled OpenGL input with callback-backed key, text, and
      focus events.
- [ ] Add release packaging rules for non-Linux LWJGL native classifiers.

### Phase 2: Explicit Transparency

- [x] Inventory every sprite, texture, and shader path that treats `0`, black,
      or `Scene.TRANSPARENT` as transparency.
- [x] Decide a single renderer-v2 transparency model.
- [x] Add asset metadata or conversion rules for opaque black versus masked
      pixels.
- [x] Add shared sprite-to-RGBA texture upload data for the OpenGL atlas path.
- [x] Route legacy world texture palette normalization and shader sample
      visibility through named transparency rules.
- [x] Route primary sprite mask checks through named transparency rules.
- [x] Add lazy OpenGL sprite atlas/cache scaffolding with padded alpha texture
      regions for the GPU path.
- [x] Add an opt-in atlas-backed OpenGL 2D sprite overlay probe for simple,
      unskewed sprite draws.
- [x] Add sprite source-rectangle commands so clipped simple sprite draws can
      replay from atlas UVs instead of cropped temporary textures.
- [x] Replay shifted and clipped scaled sprite draws from fixed-point source
      spans in the OpenGL overlay probe.
- [x] Keep semi-transparent sprite replay out of overlay probe mode to avoid
      double-blending over the already-rendered software framebuffer.
- [x] Add atlas texture variants for full-opacity legacy sprite mask and
      recolor commands, covering non-skewed, non-mirrored masked
      `drawSpriteClipping` calls without shader-side compatibility hacks.
- [x] Add command and OpenGL quad geometry support for full-opacity mirrored
      and top-skewed masked sprites.
- [x] Add sprite overlay replay-safety modes so after-frame overlay skips
      order-sensitive world/entity sprite commands by default.
- [x] Add an opt-in visible-pixel after-frame replay mode for order-sensitive
      sprite commands, using the completed software frame as the occlusion
      authority.
- [x] Add sprite overlay telemetry for captured, static-replayed,
      visible-replayed, skipped-order, skipped-invisible, dynamic-atlas-full,
      and visible-pixel counts, and show the summary in the renderer debug
      overlay.
- [x] Add per-phase replay telemetry for direct and visibility-filtered
      OpenGL sprite commands so phase-aware coverage is measurable in-game.
- [x] Add renderer-v2 capture rejection telemetry so alpha, bounds, source,
      transform, interlace, overflow, and invalid sprite blockers are visible
      before native UI replacement work.
- [x] Add renderer-v2 2D command phases for scene, world overlay, and UI
      overlay sprite captures, with phase bucket telemetry in the OpenGL
      presenter and debug overlay.
- [x] Add a phase-aware OpenGL sprite replay mode that draws UI-phase sprite
      commands directly while keeping scene/world commands visibility-filtered.
- [x] Add an opt-in native UI sprite replacement mode for captured
      full-opacity UI sprite draws, leaving alpha and masked/skewed UI paths on
      software until their native equivalents are exact.
- [x] Capture a pre-UI renderer-v2 base framebuffer at the world-to-UI phase
      boundary and route it to the OpenGL presenter behind a diagnostic flag.
- [x] Capture the legacy `spriteClipping` scaled-sprite path in the
      renderer-v2 command stream for full-opacity, replay-safe draws.
- [x] Replace replay-safe masked UI sprite draws in `native-ui` mode while
      keeping mirrored or skewed masked sprites on software until ordered
      native replay exists.
- [x] Add renderer-v2 text draw and glyph telemetry by phase to size the
      native OpenGL font/glyph pass before replacing software UI text.
- [x] Add an OpenGL glyph atlas and native replacement for replay-safe UI text
      draws in `native-ui` mode, preserving software fallback for clipped or
      antialiased text.
- [x] Add native OpenGL UI primitive commands for solid/alpha rectangles and
      lines, replayed only when pre-UI base-frame eligibility proves the full
      post-base UI stack is native-covered.
- [x] Capture UI `setPixel` calls as 1x1 native primitives so minimap/entity
      dots do not block eligible pre-UI base frames.
- [x] Add native UI base blocker reason telemetry so unsupported post-base UI
      writes identify their source instead of only clearing base eligibility.
- [x] Add native rotated minimap sprite commands, including an opaque texture
      variant for the main minimap where legacy color `0` is real black.
- [x] Carry the current renderer clip rectangle into native rotated minimap
      replay and enforce it with OpenGL scissor so rotated map pixels stay
      inside the minimap content square.
- [x] Capture UI `drawCircle` calls as native scanline circle commands so
      minimap dots and interface circles no longer block eligible pre-UI base
      frames.
- [ ] Replace remaining low-level sprite/glyph mask checks with explicit
      metadata where they are true sprite transparency rather than font masks.
- [ ] Update world texture sampling so true black can render as true black in
      renderer-v2 without relying on legacy black-to-1 substitution.
- [ ] Wire GPU sprite/UI draw calls to the OpenGL atlas and use alpha instead
      of color-key checks in those draw calls.
- [ ] Enable mirrored/top-skewed world sprite replay by default only after
      renderer-v2 can preserve original draw order instead of drawing captured
      sprites over the completed software frame.
- [ ] Expand the sprite overlay probe to alpha-dependent sprite draws and
      minimap-rotated draw paths.
- [ ] Add visual tests for black texture pixels, sprite masks, and UI icons.
- [ ] Verify that fixes do not break intentional transparent sprites.

### Phase 3: Renderer-V2 Boundary

- [ ] Add a renderer selection setting or launch flag.
- [ ] Create a renderer interface that can accept camera, scene, entity, and UI
      state from the current client.
- [ ] Extract world draw input from `mudclient` without changing gameplay
      behavior.
- [x] Mark the current software draw stream with renderer-v2 scene, world
      overlay, and UI overlay phases so native OpenGL passes can be enabled
      incrementally without guessing draw intent.
- [ ] Keep UI overlays on the old software path initially if needed.
- [ ] Add side-by-side debug output for legacy versus renderer-v2 frame timing.
- [x] Log OpenGL vendor, renderer, and version for support diagnostics.
- [x] Clean up OpenGL texture resources on presenter shutdown.
- [ ] Document every legacy render behavior that renderer-v2 intentionally
      drops.

### Phase 4: Depth-Buffered World Prototype

- [x] Normalize the known bad `tinrock1` model data at the load boundary so
      its legacy red back faces are hidden like the other ore rock models.
- [x] Add an opt-in renderer-v2 geometry frame that captures visible world
      model faces from `Scene.endScene()` without changing the legacy output.
- [x] Expose captured world model and face counts through renderer telemetry
      and the F6 debug overlay.
- [x] Classify captured world geometry as terrain, wall, roof, game object,
      wall object, or unclassified so the depth-buffer prototype can enable
      each category deliberately.
- [x] Render captured terrain, wall, game-object, and wall-object faces into
      an offscreen diagnostic depth buffer, with face, triangle, and
      pixel-write counts in telemetry.
- [x] Promote the diagnostic terrain/wall/object depth buffer into an opt-in
      visible renderer-v2 world backend.
- [x] Render static scenery and object models into the diagnostic depth buffer.
- [x] Render static scenery and object models visibly with depth testing in the
      flat-colored software prototype.
- [ ] Render player, NPC, item, projectile, and effect sprites with stable
      depth behavior.
- [ ] Define alpha rules for partially transparent sprites and effects.
- [ ] Replace near-depth painter tie instability with deterministic behavior.
- [ ] Compare frame captures against the legacy renderer for missing geometry.
- [ ] Stress test high draw distance, crowded entity scenes, roofs, fog, and
      underground areas.

### Phase 4A: OpenGL World Backend

- [ ] Keep the depth-buffered world prototype behind an opt-in renderer-v2
      setting until parity is proven.
- [x] Extract visible legacy world model faces into explicit renderer-v2
      geometry commands for diagnostics and backend prototyping.
- [x] Split captured terrain, wall, roof, scenery, and static-object geometry
      into explicit typed renderer-v2 draw commands instead of depending on
      legacy model identity and painter order.
- [x] Map typed renderer-v2 geometry commands into a CPU-side GPU-ready
      vertex/index stream.
- [x] Upload captured world geometry through an opt-in OpenGL VBO/EBO
      diagnostic pass.
- [x] Cache OpenGL world mesh VBO/EBO uploads by deterministic mesh signature
      so unchanged projected world frames can reuse resident GPU buffers instead
      of calling `glBufferData` every frame.
- [ ] Replace projected-frame upload signatures with persistent world-space
      chunk VBOs keyed by plane/section once static terrain, wall, roof, and
      scenery model products no longer depend on the legacy scene replay.
- [x] Isolate OpenGL upload, framebuffer, world, and sprite passes with
      explicit state guards so experimental passes cannot leak texture, depth,
      client-array, or scissor state into presentation.
- [x] Center projected renderer-v2 mesh coordinates for OpenGL and gate the
      visible diagnostic pass to positive-depth, viewport-intersecting,
      conservatively bounded triangles.
- [x] Invert uploaded OpenGL world mesh Z so smaller positive legacy camera
      depth wins the GPU depth test like the software depth prototype.
- [x] Draw the visible OpenGL world mesh diagnostic as a translucent
      depth-tested wireframe overlay so unfinished flat mesh colors cannot
      cover software sprites or replace textured scenery.
- [x] Carry per-triangle texture id, fallback color, model kind, source model,
      face id, and average depth through the renderer-v2 mesh for atlas and
      shader work.
- [x] Capture the final legacy sorted draw order for visible static world
      faces and carry it through renderer-v2 triangle metadata.
- [x] Capture the final near-plane clipped camera/projected vertices for
      visible static world faces and use them for renderer-v2 mesh generation.
- [x] Preserve captured legacy draw order for textured static OpenGL
      diagnostics by writing index runs in sorted face order instead of one
      global batch per texture, and keep the textured color pass free of GPU
      depth rejection until the depth path can match legacy terrain/static
      ordering.
- [x] Capture projected legacy sprite depth anchors from the final sorted draw
      loop, including sprite id, pick id, camera depth, screen rect, and
      scene-wide legacy draw order.
- [x] Capture legacy world texture palette/index data as immutable renderer-v2
      texture assets and attach the texture catalog to geometry and mesh
      frames.
- [x] Derive normalized face-basis texture coordinates from captured
      camera-space geometry and carry them in the renderer-v2 mesh vertex
      stream for future atlas sampling.
- [x] Upload referenced renderer-v2 world textures into an OpenGL atlas cache
      and report cache/upload/missing coverage without changing visible world
      rendering.
- [x] Upload world geometry and textures through OpenGL buffers and atlases.
- [x] Add an opt-in textured OpenGL world diagnostic that draws texture-backed
      terrain triangles from atlas-space UVs through texture-grouped index
      batches.
- [x] Extend the textured diagnostic to static geometry now that sprite depth
      anchors are available as renderer-v2 metadata.
- [x] Build an opt-in OpenGL world-sprite diagnostic batch from captured depth
      anchors, preserving legacy scene order until depth-tested alpha rules are
      proven.
- [x] Replay captured UI-phase commands over the visible OpenGL world pass so
      menus, message boxes, and text remain above terrain/static geometry while
      the software UI is still the source of truth.
- [x] Restore software-visible scene/world sprite pixels over the visible
      OpenGL world pass so NPCs, players, item sprites, projectiles, and world
      overlays are not buried while the GPU depth sprite path is still being
      built.
- [x] Render flat-colored static world triangles in the same legacy-ordered
      OpenGL draw stream as texture-backed triangles so the GPU world can stand
      on its own without borrowing the old completed software framebuffer.
- [x] Add an OpenGL world replacement composite mode that skips the old
      full-frame software base on in-game frames, draws the OpenGL static world
      first, and then replays anchored scene/entity sprites, world-overlay
      visible sprites, and captured UI commands above it.
- [x] Pad OpenGL texture-atlas uploads with duplicated border texels and sample
      atlas regions at texel centers so exact-edge UVs cannot bleed into
      unrelated packed textures during camera movement.
- [x] Add a conservative projected-triangle sanity guard to the OpenGL static
      world color pass so malformed or extreme near-plane triangles cannot
      paint large unrelated screen regions.
- [x] Capture legacy per-render-vertex light/fog intensity and use it as
      OpenGL texture modulation so textured terrain is no longer uniformly
      bright while the full shader path is still being built.
- [x] Apply the captured legacy light/fog intensity to OpenGL flat resource
      colors using the old 256-step shade ramp, so ground elevation and
      terrain-resource colors are no longer drawn full-bright and flat.
- [x] Add a flat material-color fallback for texture-backed walls, scenery,
      objects, and wall objects while their static UV/shader path is proven.
- [x] Use average opaque texture color as the flat material fallback for
      texture-backed static geometry so transparent or black first texels do
      not turn trees/scenery black when static texture sampling is disabled.
- [x] Add a dedicated `SPOILED_MILK_OPENGL_WORLD_STATIC_TEXTURES` switch so
      static texture detail can be reintroduced without removing the fallback.
- [x] Route exact-ID replacement-composite scene/entity sprites through captured
      3D sprite anchors, and restore missing-ID scene/world sprites from
      software-visible pixels so composite humanoid sprites are not guessed by
      rectangle alone.
- [x] Gate replacement-composite UI/world-overlay replay to frames with captured
      3D world mesh so login/menu/loading sprites cannot be replayed over an
      already-complete software framebuffer.
- [x] Gate renderer-v2 3D frame handoff until the initial region load completes,
      preventing the post-login loading interval from presenting partial or
      stale room-like world geometry.
- [x] Add a runtime-gated renderer-v2 frame capture dump on `Ctrl+F9`, writing
      layered PNGs plus world-face, sprite-command, sprite-anchor, character,
      depth, entity restore, and per-command entity depth-evaluation metadata
      for the exact frame range under investigation. This is disabled by
      default for release builds and requires
      `SPOILED_MILK_OPENGL_FRAME_CAPTURE=true`.
- [x] Add versioned renderer diagnostic sessions that join raw F6/periodic
      telemetry, JVM/GC context, structured events, console output, and indexed
      `Ctrl+F9` bursts into a bounded AI-readable bundle. Track the focused
      implementation and visual-refinement feedback loop in
      [renderer-diagnostic-session-logging-plan.md](renderer-diagnostic-session-logging-plan.md).
  - [x] Establish the opt-in session/launcher foundation with a versioned
        manifest, bounded console and structured logs, safe renderer setting
        inventory, runtime exception events, and no diagnostic output for
        ordinary client launches.
  - [x] Export raw report-window, recent, and lifetime values for every
        renderer timing/counter owner alongside frame context, allocation
        estimates, heap state, and GC deltas. The structured reporter shares
        the live telemetry sources and runs only at diagnostic boundaries.
  - [x] Correlate typed renderer/runtime events and each `Ctrl+F9` burst through
        stable burst/frame identifiers, relative artifact paths, failure state,
        and separately attributed capture timings. Add a session analyzer that
        validates the schema and produces an evidence-linked `ai-summary.md`
        with cautious timing/GC/chunk/drop/overflow correlations.
  - [x] Rate-limit and aggregate renderer reason transitions after the first
        live diagnostic launch showed normal `steady` /
        `animated-object-signature` alternation flooding the event stream.
        Preserve the suppressed transition count and flush it on shutdown.
  - [x] Accept the first structured live-session visual/performance baseline
        after dense NPC, sprite, scenery, shadow, and animation routes looked
        correct. OpenGL render p95 was `9.139ms` with a `10.057ms` worst sampled
        window and no slow-frame/exception events. Primitive overflow was
        isolated to load-transition reporting: all `32` events matched the
        `32` section loads, with zero current dense-frame drops. Heap-floor
        growth remains a retention signal for a later idle/relog comparison,
        not a diagnosed leak.
  - [x] Validate the first session-indexed live `Ctrl+F9` burst: all `12`
        frames and artifacts completed, all strict frame analyses passed,
        `954..956` world faces and `709..725` world-sprite commands were
        represented, and missing anchors, suspicious visibility, occlusion
        disagreements, and 2D drops were all zero. Full capture averaged
        `2.108s` of synchronous work per frame, so session analysis now excludes
        indexed capture intervals from normal performance rankings and reports
        their `1,517` replaced frames separately.
  - [x] Characterize the remaining heap-floor retention signal with
        post-collection old-generation, per-collector, direct-buffer, and
        account-free login-epoch telemetry. Run the approved no-capture
        idle/route/logout/relogin procedure in
        [renderer-retention-characterization-plan.md](renderer-retention-characterization-plan.md)
        before considering heap/cache changes or focused heap profiling. The
        `444.6s` two-epoch route showed `PS Old Gen` settle at exactly
        `398,790,048` bytes through logout/relogin and the complete second
        route. Direct buffers fluctuated/reclaimed below `77MB`, GC used
        `0.61%` of sampled time, and there were no slow-frame or exception
        events. Close the concern without heap/cache changes; continue passive
        monitoring in ordinary diagnostic sessions.
- [ ] Build a replay harness for captured renderer-v2 frames so a problematic
      entity/occlusion frame can be inspected without live combat timing.
  - [x] Add an offline capture analyzer that validates `Ctrl+F9` capture
        directories, summarizes world/sprite/entity tables, rejects missing
        replay inputs, and reports likely entity-sprite occlusion pressure from
        world-face draw order and screen overlap.
  - [x] Add an offline entity-sprite occlusion verifier that rasterizes later
        renderer-v2 world occluder faces into each sprite's local mask and
        reports estimated covered pixels, coverage percent, contributing
        occluder kinds, and captured depth-mask stats.
  - [x] Add live depth-evaluation summaries to the capture analyzer so command-
        level renderer decisions are visible separately from the coarse offline
        polygon replay estimate.
  - [x] Add per-command and grouped entity occluder attribution, including live
        occluder kind counts plus dominant occluding face/model/order/depth, so
        sprite hiding can be traced to exact world geometry instead of inferred
        from broad screen overlap.
  - [x] Add `entityVisibilityHealth` and `--fail-on-suspicious-visibility` to
        the capture analyzer so expected wall/object/terrain occlusion and
        out-of-frame commands do not fail the check, while unexplained projected
        sprite loss can be caught as a regression.
- [ ] Promote the OpenGL world-sprite batch from translucent diagnostic overlay
      to source-of-truth rendering after alpha, clipping, and replacement rules
      are stable.
- [ ] Use the GPU depth buffer for terrain, walls, scenery, and object models.
- [ ] Move world texture sampling and fog/light application into a shader path
      with explicit transparency rules.
- [ ] Add a side-by-side capture mode for software world output versus the GPU
      world backend.
- [x] Start replacing software-visible scene/entity sprite recovery with
      first-class OpenGL entity sprite submission by drawing legacy entity
      scene sprite commands after the static world pass.
- [x] Add an ordered projected-static overlay after direct entity sprites,
      redrawing only texture-correct static-world triangles whose legacy draw
      order falls between entity groups.
- [ ] Promote direct OpenGL entity sprite submission from screen-space legacy
      command replay to world-anchored sprite commands carrying depth anchor,
      legacy draw order, sprite frame, alpha, clipping, mirroring, and skew
      metadata directly.
- [x] Start that migration by introducing typed OpenGL world-sprite commands
      that bind each entity/item sprite command to its renderer-v2 anchor,
      anchor-match diagnosis, legacy draw order, source crop, mirror, and skew
      state, and by capturing those commands in `world-sprite-commands.tsv`.
- [x] Preserve the exact frame-local sprite-anchor index and legacy draw order
      while `Scene` expands one entity into multipart 2D commands. Composite
      assembly now uses a validated constant-time owner lookup and reports
      `owner-anchor`; the older ID/screen-bounds matcher remains only for
      invalid or compatibility commands. F6, structured telemetry, and all
      sprite/scene capture tables expose exact/fallback/unmatched ownership for
      the entity-pressure validation gate.
- [x] Accept the exact-owner slice after private visual review and a 12-frame
      strict capture covering 2,424 commands with 100% `owner-anchor`, zero
      fallback/unmatched/order mismatch, 79 NPCs, one player, three ground
      items, and `suspicious:0`. This capture proves parity at zoom `760`; it
      is not the later maximum-distance performance profile.
- [x] Profile the exact-owner endpoint at verified maximum-distance idle and
      under active combat/camera pressure, separately attributing legacy
      scene, 2D capture, composite scene, character texture, atlas, and sprite
      draw work.
- [x] Add a frame-owned grouped world-sprite snapshot that links each exact
      anchor to its submission, optional character metadata, pick state, and
      ordered captured layers. Consume it directly only when complete
      validation passes, with the typed command builder retained as an
      all-or-nothing compatibility fallback.
- [x] Add snapshot group/layer/fallback telemetry and strict
      `world-sprite-snapshots.tsv` capture analysis for anchor ownership and
      layer order.
- [x] Accept the grouped-snapshot slice after the owner visual route and 12
      strict capture frames covering 4,852 snapshot-owned layers, 842
      character groups, and 96 item groups with zero compatibility fallback,
      invalid anchor/order, suspicious visibility, failed frame, or client
      exception.
- [x] Profile the accepted grouped-snapshot endpoint. The former composite
      command/list/sort reconstruction recorded no allocation sample and
      compatibility fallback stayed zero. The new scene contained roughly
      three to five times as many sprite layers as the earlier profile, so raw
      frame-time, CPU, and allocation totals are not treated as a direct
      before/after delta.
- [x] Remove the grouped snapshot's measured per-anchor list, read-only
      wrapper, and iterator allocation with compact indexed layer storage.
      Checkpoint `87aed9cbf` preserves exact command identity/order and the
      all-or-nothing typed fallback. Compilation, the full renderer suite,
      owner visual review, and 12 strict frames passed; all 6,158 layers used
      exact snapshot ownership across 1,144 groups with zero fallback,
      invalid anchor/order, suspicious visibility, failed frame, or client
      exception.
- [x] Add the first live camera-space world-sprite depth path. It back-projects
      each existing command rectangle at the legacy sprite face's interpolated
      top/bottom camera depth, preserving exact screen framing and skew while
      testing against the authoritative world depth buffer. Sprite depth writes
      remain disabled so multipart character layers preserve legacy order.
- [x] Add normalized `depthOwned` capture metadata and strict analysis requiring
      every anchored entity and ground-item command to use GPU depth ownership.
      The software depth mask remains diagnostic-only during this validation
      step; direct unmasked sprite pixels feed the GPU path.
- [ ] Validate camera-space entity and ground-item sprites during combat,
      movement, wall crossings, terrain slopes, and crowded overlap. Keep exact
      front-occluder replay available as a kill-switch fallback until a strict
      capture and visual test pass with replay disabled.
- [x] Switch exact front-occluder replay to default-off for the GPU-depth
      validation cycle. The environment/property kill switch remains available
      temporarily, but the normal path now relies exclusively on camera-space
      sprite-versus-world depth.
- [x] Retire `FRONT_OCCLUDER_RANGE`, its mesh redraw filters, and its runtime
      flag after replay-disabled visual validation showed correct behavior. The
      captured frame had 60 of 60 world commands depth-owned, zero live replay
      ranges, and `suspicious:0`; candidate capture remains diagnostic only.
- [x] Remove per-pixel software depth-mask generation from normal GPU-depth
      sprite rendering. The legacy mask now runs only during Ctrl+F9 capture
      preserving attribution diagnostics without paying its CPU and
      direct-buffer allocation cost on every world-sprite command every frame.
- [x] Remove the temporary sprite-depth rollback flag and screen-space/software
      mask fallback after a corrected strict capture showed 190 of 190 world
      commands depth-owned, zero replay ranges, complete static ownership, and
      `suspicious:0`. Anchored camera-space GPU depth is now the sole world
      entity and ground-item rendering contract.
- [x] Retire the persistent transformed-sprite cache and atlas run batching
      branch after it selected incorrect sprite pixels in combat/body-layer
      cases. The stable world-sprite contract is exact command-sized source
      sampling through depth-owned camera-space VBO quads; any future batching
      must be reintroduced as a new path with sprite-frame parity tests.
- [x] Composite same-anchor character clothing/body layers into one
      command-sized transparent texture before GPU depth submission. This keeps
      legacy pants/armor/head layer overwrite behavior intact while still
      drawing the character as one depth-owned OpenGL sprite.
- [x] Remove the second mirror operation from command-sized world-sprite
      textures. The captured command source already folds mirroring into
      `sourceStartX16` and negative `sourceScaleX16`, so OpenGL UV mirroring
      would reverse combat and walking frames twice.
- [x] Add a wall-only depth priority in both projected mesh and resident chunk
      world paths so wall faces win near-tie depth tests against scenery that
      should be hidden behind them, without changing terrain/object/sprite
      depth behavior.
- [ ] Reintroduce persistent sprite texture caching only after command-sized
      parity tests cover mirror, skew, crop, combat frames, and layered player
      equipment composition.
- [ ] Collapse camera-space sprite projection/depth/blend setup to one pass and
      coalesce consecutive commands sharing an atlas texture into one ordered
      quad run. This must preserve exact legacy command order and the accepted
      command-sized character composite behavior.
- [x] Replace the stable command-sized camera-space world-sprite quad helpers
      with a reusable streamed VBO and static triangle index buffer. This
      removes immediate-mode submission from the default entity/ground-item
      depth path while keeping the disabled atlas-run experiment isolated.
- [x] Capture `world-sprite-batch-stats.tsv` after rendering and require strict
      command coverage, so captures report the concrete command-to-texture-batch
      reduction.
- [ ] Replace the remaining immediate-mode atlas runs with a reusable VBO while
      preserving legacy sprite-layer order.
- [ ] Replace the ordered static-overlay bridge with a renderer-v2 scene command
      queue that interleaves static geometry and sprites by explicit draw/depth
      rules instead of relying on post-sprite occluder replay.
- [x] Start the scene-command queue as a behavior-preserving OpenGL composite
      boundary that emits typed world-sprite commands and exact owned
      front-occluder commands. The old dormant broad static-world range command
      type has been removed so the next static migration must be explicit
      face-owned geometry.
- [x] Extract the pure OpenGL composite scene-command builder/classifier from
      `OpenGLFramePresenter` into `OpenGLCompositeSceneBuilder`, including
      legacy world-sprite classification, anchor matching, sorted scene-command
      assembly, front-occluder key selection, and capture/static material
      command derivation.
- [x] Extract camera-space world-sprite quad submission from
      `OpenGLFramePresenter` into `OpenGLWorldSpriteRenderer`, including the
      streamed VBO/EBO lifecycle, sprite-depth interpolation, day/night tone
      application, and depth-owned quad draw calls.
- [x] Extract the remaining world-sprite stopping-point ownership from
      `OpenGLFramePresenter` into `OpenGLWorldSpriteDrawController` and
      `OpenGLSpriteTextureBuilder`, including world-sprite scene-command
      consumption, same-anchor character-layer compositing, dynamic atlas
      upload decisions, fallback routing, and command-sized sprite texture
      construction. This is the chosen stopping point before returning to
      renderer optimization/shadow work.
- [x] Add capture-only static-world range candidates derived from sorted
      world-sprite legacy draw orders so the next interleave slice can be
      planned without changing current rendering.
- [x] Add capture-only static-range occluder attribution by model kind and
      world-sprite bounds overlap, so risky ranges can be identified before
      any static range is promoted into the live scene command queue.
- [x] Rank static range candidates in the capture analyzer by overlap risk,
      exposing the exact draw-order spans and terrain/wall/game-object mix
      that would be dangerous to promote wholesale.
- [x] Add capture-only front-occluder candidates for overlapping wall,
      game-object, and wall-object faces, excluding terrain so the first live
      interleave experiment can avoid broad ground replay.
- [x] Add analyzer coverage checks for front-occluder candidates versus the
      wall/game-object/wall-object subset of static range overlaps.
- [x] Add an opt-in front-occluder scene command and mesh filter so wall,
      game-object, and wall-object range replay can be tested without changing
      default rendering.
- [x] Promote front-occluder range replay to default-on for the replacement
      composite after visual testing and strict captures showed correct
      ordering with `suspicious:0`. This bridge was later retired after
      camera-space sprite depth validation.
- [x] Convert front-occluder replay from broad front-kind range filtering to
      exact model/face ownership carried by each `FRONT_OCCLUDER_RANGE` scene
      command and captured as `frontOccluderFaces`.
- [x] Add capture-only face-owned base-world commands grouped by model kind,
      with normalized ownership in `static-world-face-ownership.tsv`. Strict
      analysis now requires one-to-one coverage of every captured world face,
      no duplicate or orphan ownership, and ownership of every promoted front
      occluder. The first live capture covered 20,727 of 20,727 world faces
      across terrain, walls, game objects, and wall objects.
- [x] Route the visible projected base-world pass through an explicit owned
      mesh draw boundary. Exact ownership remains in the mesh's per-triangle
      model-kind/model-index/face-id arrays instead of allocating a large face
      hash set every frame; the capture invariant proves that this metadata
      covers the complete draw.
- [x] Add capture-only triangle-level world material classification in
      `static-world-material-triangles.tsv`. Strict analysis requires every
      owned mesh triangle to appear exactly once, rejects missing, duplicate,
      out-of-range, and unresolved triangles, and verifies that cutout versus
      opaque assignments agree with decoded texture alpha metadata.
- [x] Validate the first live material capture: all 26,791 owned mesh
      triangles were classified exactly once as 24,099 opaque, 2,224 cutout,
      and 468 intentionally discarded, with zero unresolved or translucent
      triangles and `suspicious:0` entity visibility.
- [x] Promote material classification into live depth passes: opaque first
      with depth writes, cutout second with alpha test and depth writes, then
      future translucent surfaces back-to-front with depth testing but no
      depth writes. Legacy order remains deterministic inside each pass.
- [ ] Validate the live opaque/cutout depth passes across walls, bridges,
      foliage, ore rocks, roof edges, and intersecting scenery before removing
      the legacy-ordered fallback.
- [x] Validate a post-promotion live capture with 19,797 material-owned
      triangles, zero unresolved classifications, and `suspicious:0`; visual
      inspection also found no regression in the tested scene.
- [x] Replace projected screen-space mesh positions with captured camera-space
      vertices and the legacy-compatible perspective matrix already used by
      resident world chunks. This gives OpenGL perspective-correct depth and
      texture interpolation while retaining legacy near-plane-clipped
      geometry.
- [x] Validate camera-space framing, near-plane transitions, sloped terrain,
      long walls, and texture alignment visually and with a strict capture.
      The validation frame covered 16,568 material-owned triangles with zero
      unresolved classifications and `suspicious:0` entity visibility.
- [x] Remove the projected-position upload branch and temporary camera-space
      kill switch after visual and strict-capture validation. Camera-space
      vertices are now the sole base-world mesh coordinate contract.
- [x] Remove the temporary legacy-ordered base-world fallback and material-pass
      kill switch after the same validation. Opaque/cutout depth passes are now
      the sole base-world submission contract; exact legacy-ordered front
      occluder replay remains isolated as a sprite compatibility bridge.
- [ ] Split renderer-v2 sprite submission into explicit passes:
      static world, entity sprites, front-wall/roof occlusion, effects/hit
      splats/nameplates, then UI.

### Phase 4B: Shader-Backed Visual Fidelity

- [x] Add OpenGL shader/program lifecycle plumbing for the projected world
      shader, with explicit shader creation, compile/link diagnostics, and
      cleanup in place for controlled migration.
- [x] Add the projected textured-world shader path behind
      `SPOILED_MILK_OPENGL_WORLD_TEXTURED_SHADER`. It originally shipped as an
      opt-in diagnostic, then graduated to default-on after capture validation;
      setting the flag false keeps the fixed-function fallback available.
- [x] Stamp frame captures with active renderer mode metadata, including
      textured shader opt-in state, textured alpha, static texture sampling,
      world replacement ownership, and resident chunk replacement settings.
      This keeps fixed-function versus shader comparisons auditable without
      relying on startup logs.
- [x] Move the opt-in textured world shader off compatibility color/UV built-ins
      and onto explicit position, atlas UV, and texture-light attributes. The
      fixed-function color and texcoord arrays remain the default fallback when
      the shader diagnostic is disabled.
- [x] Route projected flat-color static world batches through the same shader
      path with `uTextureEnabled=0`, using explicit position, UV, and
      material-color attributes while retaining the fixed-function color-array
      fallback when the shader path is disabled.
- [x] Promote the projected world shader path to default-on for visible
      projected world batches, with `SPOILED_MILK_OPENGL_WORLD_TEXTURED_SHADER=false`
      as the temporary fixed-function fallback.
- [x] Replace the projected world shader's `gl_ModelViewProjectionMatrix`
      dependency with an explicit `uProjectionMatrix` uniform populated from
      renderer-v2's camera-to-clip matrix. Compatibility projection state
      remains only for the fixed-function fallback and camera-space sprites.
- [x] Split the projected shader's overloaded texture-light color input into
      explicit legacy texture-light and material-color attributes.
      The first attempt aliased new attributes onto the compatibility-packed
      VBO and produced black flat-color base terrain while texture overlays
      remained valid. A second attempt on the shader-native VBO produced white
      resource-color terrain and scenery while sampled foliage remained valid;
      the root cause was a native-upload mismatch for flat materials that use
      the generated flat-color atlas region. The corrected native upload now
      matches the compatibility VBO with zero position, UV, or color parity
      mismatches, and the split-attribute shader was visually accepted in the
      2026-06-22 Ctrl+F9 capture with native VBO mode active.
- [x] Preserve the exact clamped `0..255` legacy light value per projected mesh
      vertex alongside the existing pre-shaded parity colors. This is source
      data only; the accepted shader and fixed-function paths remain unchanged
      until the shader-native vertex layout consumes it.
- [x] Remove the projected mesh's tight nominal-viewport triangle rejection
      after a strict capture found a left-edge wall captured by the scene but
      omitted from base-world ownership. The scene culler still bounds the
      candidate set, the oversized-geometry guard remains, and OpenGL now owns
      final viewport clipping.
- [x] Add a shader-native projected-mesh VBO with explicit position,
      atlas UV, material RGBA/alpha, raw legacy light, and raw material RGB.
      Shader draws now consume this compact native VBO directly. The coarse
      `SPOILED_MILK_OPENGL_WORLD_TEXTURED_SHADER=false` fallback still disables
      the shader path as a whole when fixed-function comparison is needed. The
      13-float compact layout was visually accepted in the 2026-06-22 Ctrl+F9
      capture with strict analyzer and live-layout parity checks clean.
- [x] Add `shader-vertex-parity.txt` to `Ctrl+F9` captures, comparing position,
      atlas UV, and live material RGBA/alpha between compatibility and
      shader-native CPU upload layouts. The removed CPU texture-light field is
      no longer part of shader-native parity because lighting now derives from
      raw legacy light in GLSL.
- [x] Add a GLSL parity shader for static world geometry that reproduces the
      current accepted OpenGL look before adding new effects.
- [x] Move flat resource-color lighting into the shader using the legacy
      256-step shade ramp and captured per-render-vertex light values. The
      shader-native VBO now carries raw material RGB and raw legacy light next
      to the pre-shaded parity color; the 2026-06-22 Ctrl+F9 capture accepted
      this path visually with strict analyzer and vertex parity checks clean.
- [x] Move texture-backed lighting into the shader using the legacy four-band
      texture shade curve. The shader now derives the accepted texture light
      factor from raw legacy light and brightness; the 2026-06-22 capture
      accepted the shader path visually and analytically.
- [x] Move the accepted legacy light/fog attenuation into shader code. The
      captured `renderLight` value already includes the legacy distance/fog
      addition from `Scene.m_r`; both flat and texture-backed shader lighting now
      consume that value directly.
- [x] Split material lighting and distance/fog into separate shader inputs so
      visual controls can tune fog distance independently from the accepted
      Classic lighting baseline. Classic should remain the default profile that
      mostly matches legacy shadows and shade bands, but fog and brightness are
      still normal player-facing controls rather than forbidden Classic edits.
- [x] Add the first player-facing fog setting and wire it into the
      preset state model. The row is persisted, supports runtime override
      plumbing, restores to Classic through the Classic preset row, marks the
      active preset Custom when manually changed, and drives the projected
      scene range and OpenGL fog-light contribution through binary On/Off.
- [x] Add first-pass remaster test controls for `Lighting` and `Geometry`.
      `Geometry` remains player-facing with Smooth, Faceted, and Wire. Lighting
      now has an accepted player-facing Remaster alpha path: server-owned
      day/night tone, directional terrain/wall/scenery lighting, and
      terrain-receiver shadow masks. Classic remains available for the original
      presentation, while Toon and other lighting proofs stay parked for later
      experiments. Deeper dynamic lights, object light sources, object
      receiving, and material-aware shadow ownership remain future work.
- [x] Add a staged remaster shadow system for directional lighting.
  - Forward work should follow
        [`remaster-lighting-and-shadow-plan.md`](remaster-lighting-and-shadow-plan.md).
        The current alpha result is accepted: movable directional light,
        semantic caster/receiver inventory, indoor/outdoor filtering,
        clipping-aware terrain-receiver shadows, first-pass contact shadows,
        split wall/scenery blur, and server-owned day/night motion. Future
        shadow work should be treated as a deliberate second pass, not the next
        default visual task.
  - [x] Restart the Directional lighting attempt from a clean slate. The active
        Remaster path is built from resident chunk shader inputs, explicit
        server-owned sun state, semantic shadow casters, terrain receivers, and
        roofless-interior classification instead of extending Classic shade
        bands or the parked overlay proof.
  - [x] Promote the accepted world-space terrain shadow mask into the normal
        Directional lighting path. `SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK`
        can force it on/off for diagnostics, and the old
        `SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK_DEBUG` name remains a
        compatibility alias. The legacy
        `SPOILED_MILK_OPENGL_WORLD_CHUNK_SHADOW_PROOF` proof remains parked and
        should not be treated as the player-facing shadow path.
  - [x] Park the triangle-derived overlay shadow proof for release. Visual
        testing confirmed the first receiver-triangle pass made shadows
        faceted, the terrain-sampled segmented pass produced swirled/boxy
        layered shadows, and the simplified flat merged caster pass removed the
        squiggles but read as rectangular hovering shadows cast from building
        tops. The old overlay VBO/draw path has been removed; the remaining
        proof uses cached light data only.
  - [x] Make terrain the only receiver for the first pass. Do not cast shadows
        onto scenery, walls, sprites, or other dynamic objects until terrain
        projection is visually accepted; clipping through objects is acceptable
        during the proof stage. The current proof darkens only terrain triangles
        during cached chunk upload, so it does not affect wall/scenery/sprite
        ordering.
  - [x] Restart shadows from semantic shadow casters. Build caster metadata
        during wall/scenery/world generation instead of deriving shadows from
        finished triangles: base edge/tile, height, width/radius, outdoor-only
        state, opacity, and optional explicit overrides. The current baked
        terrain mask consumes those casters first, using triangle-derived
        casters only as an old-data fallback.
  - [x] Add the first semantic wall caster payload to resident world chunks.
        Wall chunks now store base endpoints, base Y, height, width, opacity,
        and outdoor-only state before the wall face is triangulated. The
        disabled proof can consume these records first, using triangle-derived
        casters only as an old-data fallback.
  - [x] Add semantic object caster payloads to resident object chunks.
        Transformed game-object and wall-object models now emit one conservative
        model-level caster before their faces are triangulated. The baked
        shadow mask accepts these caster kinds when resident object chunks are
        present, but release/default clients still keep the proof off.
  - [x] Convert the disabled proof from stacked caster quads to an accumulated
        terrain receiver layer. The proof collects semantic casters from the
        whole chunk frame, hashes them into resident chunk cache invalidation,
        and uploads one affected terrain-triangle layer per receiver chunk with
        accumulated/capped alpha. This is still not release-quality shadowing,
        but it is a better foundation for a terrain shadow mask/decal because
        overlapping casters no longer draw multiple transparent quads over the
        same ground.
  - [x] Connect remaster sun azimuth/elevation and terrain shadow length to the
        server-owned 60-minute day/night cycle. The custom world-time packet now
        includes a rate multiplier so dev-only `::advtime MMSS` can visually
        fast-forward color grading and shadow movement without changing the
        player-facing lighting settings.
  - [x] Prevent server-cycle shadow movement from invalidating the software
        terrain shadow mask every frame. The mask builder now consumes coarse
        azimuth/elevation buckets for cache signatures and caster projection,
        keeping day/night shadow motion visible over time without repeating a
        full mask rebuild each frame. The accepted alpha mask defaults to
        512x512.
  - [x] Narrow terrain shadow-mask cache invalidation to shadow-owned inputs.
        The cache key intentionally excludes broad resident chunk render
        signatures; those signatures can change for animation, rebuild, or
        visual reasons that do not alter projected caster geometry. Shadow-mask
        reuse should be governed by mask bounds, mask constants, quantized light
        buckets, and caster signatures until the mask moves to a GPU-side or
        incremental path.
  - [ ] Refine diagonal-wall shadow quality. The current overlay proof skips
        diagonal wall casters to avoid false triangular artifacts. Proper
        diagonal-wall shadows need a shader/material mask or explicit wall
        shadow metadata instead of more overlay subdivision.
  - [ ] Add shadow-source metadata or heuristics for walls and scenery:
        `castsShadow`, `shadowHeight`, `shadowRadius`/width, `shadowOpacity`,
        and `outdoorOnly`. Begin with conservative heuristics for tall scenery,
        trees, fences, signs, windmill blades, and walls, then add explicit
        overrides for bad cases.
  - [x] Add a first-pass outdoor/indoor filter before enabling broad scenery
        shadows. Roof coverage, cross-chunk lookup, roofless-interior flood
        fill, and sunlight eligibility telemetry now suppress many interior
        shadow artifacts. Explicit area/material classification remains a
        later refinement.
  - [x] Restore building interior shadow blocking after contact/scenery shadow
        tuning. The mask now suppresses terrain receiver pixels classified as
        interior before compositing directional or contact shadow channels, so
        roofless building interiors stay clean while exterior boundary walls
        still cast outward.
  - [x] Accept current alpha shadow tuning and park further shadow work:
        directional cast length scale `0.5`, wall/strip blur radius `1`,
        scenery/game-object blur radius `4`, scenery/game-object alpha scale
        `1.3`, contact alpha `0.5`, contact radius scale `0.05`, and contact
        blur radius `2`.
  - [x] Separate diagnostic terrain/object relief ownership and expose shading
        channel identity. Scoped runtime overrides preserve the old shared
        override as a fallback, the shader selects relief by model kind, and a
        parity-default directional-alpha scale allows terrain relief,
        directional projection, and contact shadow comparisons to be captured
        independently before adding normal settings.
  - [ ] Treat full shadow maps, per-pixel dynamic shadows, object-to-object
        shadow receiving, and point-light shadows as later remaster work after
        material/light ownership is cleaner.
- [ ] Add an optional terrain tile edge-blending mode for remaster visuals.
  - Goal: reduce hard seams where adjacent ground tiles use very different
        terrain colors or textures, such as green grass immediately beside
        brown dirt, without requiring map authors to hand-place several
        intermediate shade tiles.
  - The Classic default should preserve original tile boundaries. A remaster
        terrain polish option can blend edge pixels or shader samples between
        neighboring tile materials so abrupt transitions appear softened while
        keeping the original map data unchanged.
  - First implementation target: terrain-only color/material blending across
        shared tile edges in the resident chunk path. Keep walls, roofs,
        objects, sprites, and minimap output unchanged during the proof stage.
  - Useful controls/names to evaluate later: `Terrain Blend: Off / Soft` or as
        part of a broader `Terrain Detail`/`Material Smoothing` option.
  - Watch for failure cases: paths and floors that intentionally need crisp
        edges, water/shore boundaries, bridge overlays, object footprint tiles,
        and any texture atlas sampling that bleeds the wrong material across a
        triangle edge.
- [x] Add renderer visual presets as setting bundles, not separate hard-coded
      engines. Initial player-facing presets should be `Classic`, `Remaster`,
      and `Custom`: Classic selects the accepted OpenGL Classic renderer/shader,
      legacy-like brightness, and conservative fog; Remaster selects the newer
      shader/material/fog defaults once validated; changing any bundled setting
      such as renderer type, shader style, brightness, fog strength, draw
      distance, or geometric/triangle visualization switches the active preset
      to Custom while preserving the edited values. The first player-facing
      preset pass is live with `Classic`, `Remaster`, and `Custom`; future
      profiles such as `High Performance` remain separate work.
- [ ] Replace fixed-function OpenGL color arrays with explicit shader
      attributes for position, UV, material id, model kind, alpha mode, and
      legacy light/fog.
- [ ] Add material metadata for terrain, water, walls, roofs, foliage, ore,
      objects, sprites, and effects so shader behavior is data-driven.
  - [x] Establish the parity-preserving resident static-world foundation in
        [renderer-material-family-foundation-plan.md](renderer-material-family-foundation-plan.md):
        explicit stable ids, centralized terrain/object classification,
        per-triangle chunk/signature/VBO/shader propagation, F6/session
        telemetry, and strict `Ctrl+F9` coverage analysis are implemented and
        live-accepted. Twelve strict capture frames each covered
        `260,032/260,032` resident triangles with zero unknown, invalid,
        duplicate, missing, or contradictory rows. Dense visual/performance and
        two-epoch logout/relogin/section-transition validation also passed.
        Normal fragment output remains intentionally unchanged.
  - [ ] Extend explicit ownership beyond resident static triangles to players,
        NPCs, ground items, projectiles, particles, and other world sprites;
        do not infer those families from screen-space sprite ids.
- [x] Promote resident chunk face normals into owned geometry data. The first
      implementation stores scaled per-vertex normals on `ChunkMesh` and routes
      chunk diffuse lighting through those accessors, removing another place
      where remaster lighting inferred geometry state from final triangle
      coordinates at draw/upload time.
- [ ] Add a frame-capture comparison mode that can show legacy software world,
      current OpenGL world, and shader world output for the same camera frame.
- [ ] Add conservative shader polish controls for saturation, contrast, fog
      strength, and slope emphasis, defaulting to parity values.
- [ ] Keep shader polish optional until it has been tested against towns,
      mines, forests, water, interiors, roofs, underground areas, and crowded
      entity scenes.

### Phase 5: Chunked World Streaming Backend

- [ ] Treat world loading as an explicit chunk lifecycle:
      requested, sector decoded, CPU chunk built, GPU uploaded, presentable,
      active, and stale.
- [ ] Keep legacy protocol/local-coordinate behavior stable while moving visual
      terrain, scenery, walls, roofs, and static collision readiness toward
      world-space chunks keyed by plane and section coordinates.
- [ ] Add chunk-state telemetry for preload requests, sector cache hits,
      sector decodes, active-window rebuilds, and slow section transitions.
- [ ] Separate raw sector decoding from active scene/model generation so
      movement can warm the next terrain window before a server region update
      forces a visible shift.
- [ ] Build a `WorldStreamManager` that owns terrain/scenery readiness and can
      later queue deferred scenery, wall objects, and GPU uploads when their
      supporting terrain chunk becomes presentable.
- [ ] Rework deferred scenery into chunk-readiness materialization instead of a
      one-off "do not draw until terrain exists" guard.
- [ ] Align client terrain preload radius with object, ground-item, player, and
      NPC visibility ranges so entities do not visibly arrive before the world
      they occupy is ready.
- [ ] Move static OpenGL terrain/scenery toward persistent GPU chunks uploaded
      once per chunk and drawn as resident world-space geometry instead of
      streaming the captured legacy mesh every frame.
- [ ] Add predictive preloading from the local walk queue/camera direction so
      likely next chunks are decoded before the player reaches a section edge.
  - [x] Start predictive terrain preload from client walk intent, ground-item
        walks, blink targets, and incoming custom movement positions.
  - [x] Extend prediction to active local-player waypoint/camera direction so
        long continuous movement can keep warming the next window even after
        the initial click.
  - [ ] Promote decoded sectors into persistent CPU terrain chunks so active
        window shifts can reuse built geometry instead of rebuilding from raw
        sector data.
    - [x] Start with a bounded CPU section-window cache warmed by prediction so
          likely 3x3 active windows are assembled and bridge-normalized before
          they become visible.
    - [ ] Split terrain, wall, roof, collision, and minimap products out of
          `generateLandscapeModel` so cached CPU chunks can hold reusable model
          inputs instead of only reusable sector windows.
      - [x] Isolate terrain and roof publication, wall product generation, and
            roof elevation preparation behind named methods without changing
            the live model output.
      - [x] Move terrain face emission and roof face emission out of the
            monolithic landscape generator.
      - [ ] Convert the named product boundaries into cacheable model-input
            records once the extracted phases are stable.
        - [x] Start terrain model-input records for vertices, tile faces, and
              overlay faces, replayed into the legacy `RSModel` path.
        - [x] Add cache storage keyed by CPU section-window key and plane for
              terrain model-input records.
        - [x] Refactor terrain input builders to read from supplied sector
              windows instead of the mutable active `sectors` array so
              prediction can build terrain inputs before the player arrives.
        - [x] Add cached wall model-input records for wall segments, minimap
              strokes, and collision side effects.
        - [x] Isolate roof elevation mutation behind a roof elevation workspace
              so the roof path can stop mutating global elevation state
              directly.
        - [x] Add roof model-input records for roof faces, replayed into the
              legacy `RSModel` path.
        - [x] Make roof model-input records cacheable by deriving the initial
              roof elevation workspace from CPU section-window data instead of
              the live `tileElevationCache`.
        - [x] Convert wall model-input records from replaying through
              `insertWallIntoModel` to storing final wall vertices, so wall
              products no longer depend on the live `tileElevationCache`.
        - [x] Add telemetry for terrain, wall, and roof model-input builds and
              cache hits so movement testing can show whether prediction is
              reducing active-window rebuild work.
        - [x] Group terrain, wall, and roof model inputs into bounded
              `WorldModelProduct` records so chunk presentation has a
              persistent CPU-side `PRESENTABLE` boundary before GPU upload.
        - [x] Build CPU-side active-window-local GPU mesh products from each
              `WorldModelProduct`, including expanded triangle vertices,
              indices, material ids, model-kind classifications, stable upload
              signatures keyed by plane/section window, and retained world
              origin metadata for the later true resident chunk transform.
        - [x] Key `WorldModelProduct` caches by roof-geometry inclusion so the
              saved roofs-off option can skip roof triangles and legacy roof
              model publication instead of only filtering roof batches at draw
              time. Hidden-roof products still copy the roof elevation result
              into the live elevation cache and publish a valid empty roof grid,
              preserving camera/toggle behavior while avoiding roof geometry
              export.
        - [x] Publish active resident chunk mesh snapshots on
              `Renderer3DFrame` so the OpenGL presenter can observe chunk
              counts and upload signatures without parsing projected
              frame-space meshes.
        - [x] Upload active resident chunk snapshots into a bounded
              resident OpenGL VBO/EBO cache keyed by plane, section window,
              and mesh signature. The current replacement path keeps draw
              vertices in active-window local coordinates to match the legacy
              camera while retaining origin metadata for a future
              camera-relative multi-section transform.
        - [x] Add an opt-in resident chunk wireframe diagnostic
              (`SPOILED_MILK_OPENGL_WORLD_CHUNKS_VISIBLE`) that draws active
              resident chunk buffers with the captured legacy camera matrix
              and per-triangle model-kind diagnostic colors. This proves the
              chunk camera/projection contract without changing the default
              OpenGL world composite.
        - [x] Add an opt-in filled resident chunk diagnostic
              (`SPOILED_MILK_OPENGL_WORLD_CHUNKS_FILLED_VISIBLE`) that draws
              resident chunk buffers through material-sorted index buffers
              keyed by model kind, texture id, and fallback color. This gives
              the future shader path explicit draw buckets before
              texture/material replacement is enabled.
        - [x] Extend CPU-side world-space chunk mesh products with immutable
              per-vertex texture coordinates and raw legacy light values, then
              upload those channels into the resident chunk VBO layout. The
              diagnostic path still draws kind colors, but the GPU chunk
              backend now has the data needed for real atlas sampling and
              shader-based lighting.
        - [x] Move OpenGL world texture atlas ownership to the presenter and
              share it between the projected mesh renderer and resident chunk
              renderer, including a chunk-frame texture warmup hook for the
              filled diagnostic path. This prevents the chunk backend from
              growing a duplicate atlas/cache when textured chunk drawing is
              enabled.
        - [x] Add an opt-in textured resident chunk diagnostic
              (`SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE`) that binds
              shared-atlas texture regions for texture-backed material batches
              and draws them from chunk VBO texture-coordinate/light channels.
              Transparent-front flat batches draw with resolved synthetic
              fallback RGB instead of being skipped.
        - [x] Add average-color fallback for textured resident chunk batches
              that cannot bind an atlas region, using the primary texture data
              first and then any fallback material texture average available
              from the chunk batch.
        - [x] Resolve positive resident chunk fallback resources through the
              captured texture catalog before treating fallbacks as literal RGB,
              preventing legacy terrain/resource ids from rendering as black
              when chunks own the world base.
        - [x] Split textured resident chunk diagnostics into opaque and
              transparent atlas-backed passes, drawing opaque/fallback
              material batches first and transparent texture batches second
              while keeping unique chunk draw telemetry stable.
        - [x] Draw resident chunk diagnostics by global model-kind layers
              instead of per-chunk material completion, so terrain is submitted
              across all active chunks before walls, roofs, wall objects, game
              objects, and unclassified geometry.
        - [x] Keep depth writes enabled for resident cutout textures. Legacy
              world texture alpha is binary and alpha-tested, so transparent
              texels do not stamp depth while opaque fence/scenery texels must
              occlude later entity sprites.
        - [x] Report resident chunk fallback and skipped-material triangle
              counts in renderer telemetry so missing atlas regions can be
              separated from intentionally flat fallback materials while
              proving the resident chunk replacement path.
        - [x] Add an opt-in resident chunk replacement composite gate
              (`SPOILED_MILK_OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE`) that
              skips the old software base only when textured chunks are active
              and the frame has resident chunk geometry, while reusing the
              accepted overlay/sprite replay path.
        - [x] Attempt default promotion of textured resident chunk
              replacement and roll it back after visual validation. Terrain
              and walls failed to draw, and toggling roofs off corrupted
              projected scenery objects without exposing usable roofs.
              Resident chunk replacement must stay opt-in until terrain,
              wall, roof, and roof-toggle parity are fixed.
        - [x] Fix resident static chunk parity before the next default
              promotion attempt: terrain/wall chunks render in the replacement
              composite, roof visibility exposes/removes roofs without
              corrupting scenery, and projected object bridge state survives
              roof option changes. 2026-06-26 visual validation showed correct
              terrain, walls, roofs, scenery bridge occlusion, and sprite
              ordering, with F6 reporting
              `resident req/active/fallback 1.0/1.0/0.0 | reason active`.
        - [x] Promote textured resident static chunk replacement to the
              renderer-v2 runtime defaults:
              `SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_VISIBLE=true`,
              `SPOILED_MILK_OPENGL_WORLD_CHUNKS_REPLACEMENT_COMPOSITE=true`,
              and
              `SPOILED_MILK_OPENGL_WORLD_CHUNKS_TRUSTED_REPLACEMENT=true`.
              Resident object chunks stayed disabled at this point until
              two-sided scenery material ownership could be proven.
        - [x] Remove resident-owned terrain from the projected legacy polygon
              path when static resident chunk ownership is active. This
              reduces cull/sort/export work now that terrain pixels and
              terrain depth are owned by resident chunks. The optimization is
              disabled during one-shot legacy raster captures, and it retains
              only terrain faces that are pickable and near the current mouse
              position during mouse-pick passes so ground interaction keeps
              the old picking behavior without projecting every terrain face.
              2026-06-26 F6 validation in the dense Seers/Camelot test area
              showed terrain projected faces dropping from roughly 8100 to
              about 5, scene cull dropping from about 5 ms to about 3 ms, scene
              draw dropping from about 10 ms to about 6 ms, and FPS reaching
              60 with no visible regressions.
        - [ ] Revisit projected scenery bridge culling with a better object
              and face-level visibility model. A 2026-06-26 experiment that
              changed the mesh gate from oversized containment padding to
              viewport intersection with a 128px safety margin lowered FPS in
              the dense farm/fence test area because near-camera oversized
              scenery triangles were retained more often. Do not reapply that
              simple intersection rule; the bridge needs model-cell,
              draw-order, and occluder-aware culling instead.
        - [x] Gate legacy picker scanline work on projected face pickability.
              The old scene loop still needs picker rasterization for
              selectable scenery/ground/sprite faces, but non-pickable
              projected faces no longer pay the expensive `setFrustum(...,
              5960, ...)` scanline path just because their screen bounds touch
              the current mouse position.
        - [x] Suppress visible projected-mesh drawing while the resident chunk
              replacement composite owns the world base, preventing a hybrid
              projected/chunk static world when both diagnostic paths are
              enabled for comparison.
        - [x] Keep resident chunk draw vertices in active-window local
              coordinates while storing section world-origin metadata, so the
              replacement composite matches the captured legacy camera instead
              of projecting active chunks outside the scene.
        - [x] Restore scenery objects in resident chunk replacement by drawing
              a filtered projected `GAME_OBJECT`/`WALL_OBJECT` pass after
              resident terrain, walls, and roofs. This keeps duplicate
              projected terrain out of the chunk path while the longer-term
              resident object buffer is built.
        - [x] Make the temporary projected scenery bridge depth-aware by
              drawing projected terrain/wall/roof occluders into depth only
              before the object color pass. The bridge still preserves legacy
              object draw order, but scenery no longer blindly paints over
              resident walls while resident object buffers are pending.
        - [x] Honor the saved roof-visibility option in the resident chunk
              replacement path by filtering `ROOF` batches at draw time, by
              removing hidden roofs from the projected scenery bridge's
              depth-only occluder pass, and by building a no-roof
              `WorldModelProduct` variant when roofs are hidden.
        - [x] Align automatic indoor and upper-floor roof visibility between
              legacy scene grids and resident chunks. A named frame state now
              distinguishes saved-option, covered-ground, upper-floor, and
              visible-outdoor cases; resident roof and above-floor wall batches
              consume that state, and frame captures expose it directly.
        - [x] Restore the first-pass resident chunk lighting baseline by
              applying wall endpoint terrain light mutations before GPU chunk
              upload, drawing flat fallback materials from per-vertex shaded
              material colors, and smoothing terrain diffuse light across
              shared terrain vertex coordinates in the chunk VBO path. This
              replaces the solid-color terrain/floor output without waiting
              for the longer-term shader pipeline.
        - [x] Establish `Classic` as the resident chunk visual target: legacy
              fallback resource materials such as wood floors are promoted to
              real atlas-sampled textures instead of average-color fills, so
              the OpenGL replacement preserves original material detail before
              alternate lighting modes are introduced.
        - [x] Promote materialized game-object and wall-object models into
              resident object buffers for diagnostics and future remaster work.
              The first visible-owner test exposed a classic material rule:
              scenery faces choose `faceTextureFront` or `faceTextureBack`
              from the camera-facing orientation, while the resident object
              slice used a single texture side. That made one-sided details
              such as ladders, counter tops, sign interiors, and windmill
              blades partially disappear. Until resident object drawing has a
              proper two-sided/front-back material path, the projected scenery
              bridge remains authoritative for visible `GAME_OBJECT` and
              `WALL_OBJECT` rendering.
        - [x] Add an opt-in resident two-sided/front-back material proof path
              for visible game-object and wall-object chunks behind
              `SPOILED_MILK_OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS=true`.
              Object faces now emit separate front/back material triangles
              with reversed winding for the back side, and resident object
              draw batches use OpenGL back-face culling so duplicate sides do
              not fight at the same depth. The same proof now omits the
              unused projected object mesh export while retaining object/wall
              depth export for entity sprite clipping. 2026-06-24
              short-window FPS telemetry showed resident terrain/wall chunk
              draw staying stable while frame-rate swings followed the
              projected scenery bridge: legacy scene draw, cull, depth export,
              object mesh export, and projected object upload/draw moved with
              camera panning. Follow-up visual validation failed: many object
              sub-materials rendered black or disappeared, and FPS stayed
              around the same level. Keep resident object ownership disabled
              and leave the projected scenery bridge authoritative until
              material-side selection, texture fallback, and tiny transparent
              detail handling are redesigned from first principles.
        - [x] Add a resident object one-sided material parity rule. If a
              legacy object face has exactly one visible side, the resident
              object chunk now emits that visible material on both culling
              windings. This keeps depth/culling behavior for normal
              two-sided faces while preventing classic one-sided details such
              as ladders, sign interiors, counter tops, windmill blades, or
              small foliage details from disappearing when resident object
              ownership is being tested.
        - [x] Let the resident object owner suppress projected
              `GAME_OBJECT` and `WALL_OBJECT` faces from the legacy CPU
              polygon list, while retaining only mouse-pickable candidates
              near the cursor and forced legacy capture frames. This is the
              first proof that resident object chunks can remove the old
              projected scenery bridge cost instead of only replacing the
              visible OpenGL draw.
        - [x] Preserve object-model legacy side lighting in resident object
              chunks. The object chunk builder records the same front/back
              side light that the projected renderer derived from
              `diffuseParam1`, `faceDiffuseLight`, `vertLightOther`, and
              `vertDiffuseLight`; the OpenGL resident object path then uses
              that captured value directly instead of recomputing scenery
              lighting from resident chunk normals.
        - [x] Promote resident object chunks to the renderer-v2 runtime
              defaults with
              `SPOILED_MILK_OPENGL_WORLD_CHUNKS_RESIDENT_OBJECTS=true`.
              2026-06-26 validation showed restored scenery details, matching
              classic object color, correct wall/scenery/sprite ordering,
              combat and movement still functioning, and dense-area FPS
              reaching the 60 FPS target in the accepted test location.
        - [x] Split resident chunk draw telemetry into terrain, wall, roof,
              game-object, wall-object, and other triangle buckets so object
              residency can be verified independently from static world
              geometry during draw-distance and ordering tests.
        - [x] Expand textured chunk drawing into a full default replacement
              path for terrain, walls, and roofs with readiness telemetry and
              fail-closed projected fallback. This was the static-world
              default before the later resident object promotion added
              game-object and wall-object ownership.
        - [ ] Replace the fixed-function resident chunk lighting approximation
              with an explicit shader/material pipeline that separates base
              material color, texture sampling, slope diffuse, object/wall
              shadow terms, global brightness, and future HD-remaster polish
              controls.
          - [x] Append shader-ready resident chunk vertex inputs to the
                existing fixed-function VBO layout without changing the active
                draw path: effective legacy light, base legacy light, raw
                material RGB, normalized vertex normal, and model-kind id.
                The fixed-function offsets stay first for parity, while the
                appended fields give the next chunk shader pass the same owned
                data contract already proven on the projected mesh shader path.
          - [x] Add the resident chunk parity shader opt-in behind
                `SPOILED_MILK_OPENGL_WORLD_CHUNKS_TEXTURED_SHADER=true` /
                `-Dspoiledmilk.openglWorldChunksTexturedShader=true`.
                This shader consumes the already-baked resident material color
                and texture-light attributes plus explicit world matrices and
                fog values. It is for shader-readiness validation only: do not
                port or extend the projected mesh shader's legacy light-band
                formulas here. Future remaster lighting should ignore the
                baked legacy-light attributes and instead use raw material
                color, normals, model kind, and new clean-slate light/shadow
                inputs.
          - [x] Add the raw-material resident chunk inspection opt-in behind
                `SPOILED_MILK_OPENGL_WORLD_CHUNKS_RAW_MATERIAL_SHADER=true` /
                `-Dspoiledmilk.openglWorldChunksRawMaterialShader=true`. This
                mode still draws through GLSL, but ignores baked resident
                texture-light and shaded material-color data: textured faces
                sample the texture directly, flat fallback faces use raw
                material RGB, and shader fog is disabled. Use it as the
                clean-slate visual baseline before building remaster lighting,
                not as an intended player-facing renderer.
        - [ ] Add selectable visual modes once Classic parity is stable:
              `Classic` for legacy resource textures/shade bands, then
              experimental/remaster modes for alternate shadow softness,
              stronger geometric lighting, richer material response, and
              optional stylized lighting.
- [ ] Audit camera smoothing and coordinate rounding after chunk presentation
      no longer depends on recentering the whole visible world.
- [ ] Ensure camera updates produce stable projected positions frame to frame.
- [ ] Avoid render-thread blocking on asset, sector, or GPU chunk uploads where
      possible.
- [ ] Verify that increased draw distance does not reintroduce flicker through
      unstable ordering or late-loaded assets.
- [ ] Revisit spatial resident plane subdivision only if a future draw-distance
      setting needs it. The accepted binary fog setting keeps the simpler
      far-distance path because Close-style spatial culling reduced triangles
      but underperformed due to extra batches and draw calls.
- [x] Spatially subdivide resident game/wall objects into independently cached
      24x24-tile cells. Animated scenery now invalidates and uploads only its
      local cells instead of rebuilding the full loaded object world.

### Phase 6: Scaler Rebuild

- [ ] Implement nearest/integer scaling using stable source and destination
      rectangles.
- [x] Implement bilinear and bicubic scaling without per-frame worker creation.
- [ ] Add a sharp filtered mode for low-resolution art.
- [ ] Consider Scale2x, hq2x, xBRZ, or a similar pixel-art-aware option.
- [ ] Separate world scaling and UI scaling if readability requires it.
- [ ] Ensure resize behavior does not rebuild heavyweight renderer state every
      frame.
- [ ] Verify text, menus, inventory, and combat overlays at common window sizes.

### Phase 7: Performance and Tooling

- [ ] Establish 60 FPS as the renderer-v2 performance target for normal play
      on the alpha desktop baseline, with 1280x720, Classic visuals, normal
      draw distance, common scenery density, and world/UI overlays enabled.
  - [x] Give OpenGL-primary presentation a client-owned 60 FPS loop target
        instead of inheriting the legacy server-provided 50 FPS client value.
        Server simulation and older-client configuration remain unchanged.
- [ ] Add a repeatable FPS benchmark route that logs frame time, upload time,
      render time, chunk uploads/reuse, resident object counts, sprite replay
      counts, allocation rate, and active draw-distance/resolution/profile.
- [ ] Profile the current 25 FPS OpenGL-primary scene before adding more visual
      features, then classify cost by CPU scene build, resident object export,
      chunk upload/reuse, sprite/UI replay, texture atlas uploads, and GPU draw.
  - [x] Add compact F6 phase telemetry for OpenGL render phases and legacy
        `Scene.endScene` phases. Current alpha measurements show CPU scene work
        around 19-21ms. After flat-material batching and chunk texture-scan
        caching, OpenGL render work dropped from roughly 11-13ms to roughly 2ms
        in the tested scene.
  - [x] Confirm the current OpenGL projected mesh pass is not draw-call bound:
        roughly 13k projected triangles are now submitted through 1 batch and 1
        draw call in the tested scene.
  - [x] Keep the legacy world-raster skip opt-in only
        (`SPOILED_MILK_SKIP_LEGACY_WORLD_RASTER=true`). A narrowed version that
        preserves picking but skips final software pixel raster improved scene
        time slightly, but still broke sprite/scenery ordering and NPC
        interaction visibility. The next CPU optimization must first separate
        sprite/order ownership from software raster side effects.
  - [x] In resident chunk replacement mode, stop capturing static projected
        faces and skip the unused software depth-frame and projected-mesh
        exports while retaining legacy rotation, sorting, sprite anchors, and
        picking as the first low-risk fast-path stage.
  - [x] In resident object chunk mode, skip the now-unused projected object
        mesh export while keeping the object/wall depth frame for entity sprite
        clipping. Add per-row sprite-clip spans to the depth rasterizer so it
        walks only screen columns that can affect entity sprites before doing
        triangle edge/depth math.
  - [x] Preserve resident-mode static picking while rejecting projected faces
        whose coarse screen bounds cannot contain the mouse before legacy
        clipping, lighting, and scan conversion. Sprite sorting and anchors
        remain on the established path.
  - [x] Move resident-mode static pick rejection ahead of global polygon
        collection so non-candidate static faces never enter legacy sorting or
        precise scan conversion. Only sprite faces and cursor candidates retain
        the old sorted path.
  - [x] Window the software depth-mask bridge to active sprite coverage. The
        depth frame still answers full-screen coordinate queries for entity and
        ground-item occlusion, but its backing arrays and raster loops are now
        bounded to the union of visible sprite rectangles instead of the whole
        viewport.
  - [x] Add a coarse face-bounds reject before the software depth bridge
        triangulates a world face. Faces that cannot overlap the sprite-depth
        clip window skip triangle setup entirely; intersecting faces continue
        through the existing conservative per-row and per-pixel mask checks.
  - [x] Make the software depth bridge sprite-scoped by default. When there
        are no projected sprite anchors, normal rendering now builds an empty
        depth frame instead of falling back to a full-viewport CPU depth pass;
        the full-viewport path is reserved for the visible-world diagnostic.
        Face rejection now also checks sprite row spans before rasterization,
        and expanded F6 telemetry reports depth `c/a/r` so dense-area captures
        show considered, accepted, and rejected depth faces directly.
  - [ ] Continue separating visual rendering from sprite-depth ownership. The
        current GLSL parity shader is intentionally small, while dense-scene
        captures show frame swings following scene cull/depth/draw CPU work.
        Future optimization should reject whole static cells before legacy
        polygon sort and produce CPU occlusion only for faces that can affect
        visible sprites or picking.
  - [x] Start collapsing projected flat-material world faces into the same
        atlas-backed textured path by assigning flat faces a white atlas texel
        and carrying their shaded material RGB through the texture color
        channel. This preserves legacy draw order while reducing texture/flat
        state splits.
  - [x] Add short-window renderer telemetry alongside lifetime averages so
        camera-pan FPS swings can be traced to the current few seconds instead
        of launch-wide cumulative averages. Ctrl+F9 layer readbacks are timed
        outside the normal OpenGL phase totals, so capture bursts no longer
        poison the render averages used for FPS diagnosis.
- [ ] Add a `High Performance` renderer profile once the bottlenecks are known.
      It should preserve correct ordering and readable visuals while allowing
      conservative tradeoffs such as shorter draw distance, reduced smoothing,
      fewer diagnostic overlays, lower default resolution, cheaper shadows, or
      lower sprite/world replay quality where safe.
- [ ] Evaluate JVM memory/GC settings after profiling allocation rate. Treat
      memory increases as a hitch/GC fix, not the primary path to 60 FPS unless
      telemetry shows allocation pressure or collection pauses.
  - [x] Confirm the local and packaged client target 64-bit Java rather than a
        32-bit/4GB ceiling, and set explicit client launch heap defaults to
        `-Xms512m -Xmx2g` for development and packaged launchers.
- [ ] Evaluate multicore work only after the renderer has clear ownership
      boundaries for immutable chunk products, dynamic object buffers, and
      sprite/UI command snapshots. Candidate work includes background chunk
      preparation, async texture/material upload staging, and predictive sector
      loads, but avoid parallelizing frame-critical mutable scene state first.
  - [x] Defer broad multicore work until renderer ownership boundaries are
        cleaner. Current safe candidates remain background chunk/mesh
        preparation, asset/texture staging, and predictive loading rather than
        parallelizing mutable live scene state.
- [ ] Add a debug overlay for frame time, render time, present time, polygon
      count, sprite count, and active scaling mode.
  - [x] Draw the F6 renderer debug overlay natively as a final OpenGL pass in
        OpenGL-primary mode instead of painting it into the software base
        framebuffer, so it remains visible during world replacement and cannot
        occlude sprites through captured overlay commands.
  - [x] Convert the F6 renderer debug overlay into a compact performance HUD
        for the 60 FPS push: FPS, frame/scene/present timing, OpenGL
        snapshot/upload/render timing, chunk upload/reuse, resident draw
        buckets, sprite replay counts, world face buckets, and allocation
        summary.
- [x] Add a command-line/config option to dump frame timing to a structured
      session log. `scripts/run-client.sh --renderer-diagnostics` records raw
      timing windows, renderer counters, events, runtime state, and capture
      correlation in bounded JSONL files.
- [ ] Add automated frame-diff tooling for the baseline acuity locations.
- [ ] Track allocation rate during camera movement and section loading.
- [ ] Establish minimum target hardware for alpha testing.
- [ ] Compare legacy renderer and renderer-v2 on the same route.

#### Modern Optimization Backlog

These are candidate renderer-v2 optimizations to evaluate after each
correctness pass. Do not apply them blindly: capture the relevant telemetry
first, prove that the optimization targets the current bottleneck, and keep
Classic visual ordering, entity occlusion, and sprite composition correct.

- [x] Add an FPS/visibility capture that records chunks considered versus
      drawn, entities considered versus drawn, static chunk rebuild count,
      terrain/wall/scenery/sprite submission counts, draw calls, texture binds,
      allocation rate, and CPU time split across world build, sort, upload,
      draw, and present.
  - [x] Extend renderer telemetry and the F6 performance HUD with chunk
        visibility (`considered/drawn/culled`), chunk submit
        (`draw calls/texture binds`), and world entity visibility
        (`considered/drawn/culled`) counters. Existing telemetry already covers
        chunk upload/reuse, terrain/wall/scenery/sprite submission buckets,
        allocation summaries, and CPU phase timing.
  - [x] Extend the F6 HUD with rolling recent timings and full client-loop
        timing (`total/sleep/update/reposition/draw`). A dense-area A/B showed
        FPS dropping from the high 50s to low 40s while render workload stayed
        nearly identical; the old loop had switched from ~1ms to ~10ms sleep.
        OpenGL-primary mode now defaults to the modern fixed-cadence loop, with
        the legacy loop retained behind the runtime flag.
  - [x] Instrument all bounded renderer 2D command streams with attempted,
        accepted, and overflow-drop counts without adding a per-frame helper
        allocation. Expanded F6 reports current/max/drop counts beside each
        cap, `Ctrl+F9` writes `renderer-2d-command-limits.tsv`, and the offline
        analyzer accepts the file without rejecting older captures. A
        Java-backed regression crosses the `256` rotated-sprite cap and proves
        that `259` submissions retain `256` commands while reporting `3`
        drops. Keep the caps unchanged until field captures show which stream
        needs capacity-managed growth.
- [ ] Prioritize retained static world chunks. Terrain, walls, roofs, static
      scenery, wall objects, and game objects should stay in reusable CPU/GPU
      chunk products and rebuild only when the area, plane, roof state,
      material state, or affected scenery animation cell changes.
- [ ] Add chunk-level frustum culling before per-face or per-object work. The
      first pass should reject whole terrain/scenery chunks outside the camera
      view, with conservative margins for Classic projection and camera
      rotation.
  - [x] Add conservative resident material-batch bounds culling before texture
        binds and draw calls. Batches that cross the near plane are retained,
        and projected screen bounds use a safety margin to avoid clipping walls
        or scenery at the viewport edge. This benefits object cells and
        smaller material batches now; full terrain/wall chunk subdivision
        remains the larger follow-up.
  - [x] Expose resident material-batch visibility in telemetry separately from
        whole-chunk visibility. The current world is still usually three large
        resident chunks, so chunk `considered/drawn/culled` can remain flat
        while batch `considered/drawn/culled` shows whether frustum and fog
        culling are actually removing submissions.
- [ ] Add whole-entity sprite visibility culling for NPCs, players, ground
      items, projectiles, and world effects. Cull by composed sprite bounds or
      world anchor bounds, never by individual body-part frames, so character
      assembly cannot regress into partial-body rendering.
- [ ] Treat draw distance/fog as a visibility ownership setting when a
      performance profile needs it. Fog-on can still hide world edges, but a
      performance-oriented draw-distance control should decide which chunks and
      entities are prepared, uploaded, and submitted.
- [ ] Make roof-off a hard layer exclusion where possible. When roofs are
      hidden, avoid building, uploading, and drawing roof geometry instead of
      only making it transparent or skipping it late.
- [ ] Reduce draw calls and texture binds through material and texture
      batching. Measure batches by terrain, walls, roofs, wall objects, game
      objects, sprites, UI, and animated scenery before adding more visual
      effects.
  - [x] Cache the currently bound resident chunk atlas texture during a chunk
        draw pass so repeated material batches sharing the same atlas do not
        issue redundant `glBindTexture` calls. The F6 HUD now reports actual
        chunk texture binds beside chunk draw calls.
  - [x] Batch the full projected-world opaque terrain pass by material before
        returning to legacy-ordered opaque non-terrain and cutout submissions.
        This targets the high projected mesh draw-call count while leaving
        walls, scenery, wall objects, game objects, and cutout ordering
        conservative.
  - [x] Extend projected-world opaque material batching to walls, roofs, game
        objects, and wall objects in the full static path. Cutout and
        translucent submissions remain legacy ordered until visual testing
        proves they can be safely grouped.
  - [x] Default the OpenGL replacement client to skip the legacy software
        world raster after geometry, depth, sprite-anchor, and mouse-pick data
        have been captured. This removes redundant software pixel drawing while
        preserving the old raster path as an explicit opt-out.
  - [x] Skip software framebuffer raster for scene sprites after their
        OpenGL replay command has been captured. Failed captures still fall
        through to the legacy sprite plotter, and ordered/masked sprite cases
        remain conservative.
  - [x] Precompute unclipped face light during visible-world capture and skip
        the sorted-loop clipped-geometry export for fully visible faces in
        OpenGL skip-raster mode. Near-plane faces and mouse-pick candidates
        still use the legacy clipped path.
- [ ] Evaluate texture atlases or texture arrays for high-bind paths. Terrain
      textures already have atlas work; extend the same idea only where
      telemetry shows repeated binds for sprites, scenery, effects, or UI.
- [ ] Evaluate instanced rendering for repeated quads and repeated static
      pieces, including ground-item sprites, simple world effects, wall pieces,
      and repeated scenery forms. Keep sprite ordering and occlusion ownership
      explicit before batching instances across visual layers.
- [ ] Add dirty-flag invalidation for renderer settings. Roof visibility, fog
      or draw distance, geometry mode, lighting mode, brightness, texture
      state, and animated scenery should invalidate only the chunks, buffers,
      or material batches they affect.
- [ ] Maintain a spatial partition for render queries. Reuse chunk grids or a
      similar broadphase for visible entities, scenery, click picking,
      projectile queries, and minimap/world-overlay updates instead of scanning
      full local arrays whenever possible.
- [ ] Throttle animated scenery work by visibility. Fountains, fishing spots,
      windmills, signs, counters, ladders, and similar animated or multi-part
      scenery should update/upload only when visible or near-visible, while
      preserving animation continuity when they re-enter view.
- [ ] Reduce per-frame allocation in renderer-critical paths. Reuse scratch
      lists, buffers, matrices, vectors, entity bounds, and command arrays so
      camera movement and section loading do not create avoidable GC pressure.
- [ ] Use a stable streaming-buffer strategy for dynamic OpenGL data. Prefer
      persistent mapped buffers, ring buffers, or safe buffer orphaning patterns
      over small per-frame allocations and blocking uploads for sprites,
      entities, and animated overlays.
- [ ] Prepare static chunks asynchronously only after ownership boundaries are
      immutable. Background work is appropriate for sector decode, CPU chunk
      mesh generation, material lookup, and upload staging; mutable live scene
      state should remain render-thread owned until it has explicit snapshots.
- [ ] Precompute tile metadata used by rendering and path queries. Cache tile
      bounds, walkability, roof layer, wall/object occupancy, terrain light,
      terrain normal or slope, floor material, and occlusion hints where the
      data is stable.
- [ ] Keep simulation interpolation consistent with the renderer. Players,
      NPCs, projectiles, and animated scenery should render between fixed
      server ticks without requiring extra simulation updates for smoother
      motion.
- [ ] Stagger NPC AI and path checks to avoid frame or tick spikes. NPCs should
      not all select movement or re-path on the same tick unless a shared event
      explicitly requires it.
- [ ] Add pathfinding broadphase and repath throttling. Use cheap reachability,
      local obstacle checks, path reuse, destination-change thresholds, and
      controlled repath intervals before invoking expensive A* work.
- [ ] Treat occlusion culling as a later, conservative optimization. Only test
      object/building occlusion after chunk culling, entity culling, and
      batching are measured, because Classic transparent fences and legacy
      wall/sprite ordering can make aggressive occlusion visually wrong.
- [ ] Treat GPU occlusion queries as experimental only. Avoid query patterns
      that stall the render thread; consider them only after CPU-side culling
      and batching are already stable.

### Phase 8: Options and Quality Settings Cleanup

- [ ] Redesign the options menu into user-friendly renderer, display, audio,
      controls, and gameplay groups instead of exposing raw debug rows.
- [x] Add first-pass section headers to the current settings list so renderer
      testing controls are grouped for release-facing use without changing the
      existing click IDs or settings persistence.
- [x] Collapse the OpenGL-primary player-facing render options to
      `Preset`, `Aspect Ratio`, `Borderless`, `Lighting`, `Geometry`,
      `Terrain Variation`, and `Fog`, followed by persisted relief, dimness,
      contrast, gamma, and saturation sliders under `Graphics`. The manual `Tone`, superseded
      `Brightness`, and release-facing font rows were retired.
      All six sliders use 20 positions and place the original default visual
      response at level 10. Terrain/object level 10 maps to relief strength
      `2.0`; brightness/dimness maps to `1.0`; contrast maps to `1.2`. Lower
      positions provide lighter shading, brighter output, or lower contrast,
      while higher positions retain the accepted expanded endpoints. Versioned
      persistence migrates older levels by effective strength/multiplier.
      Selecting Classic applies the owner-tuned `18/18/14/7` bundle; selecting
      Remaster restores the baseline `10/10/10/10` bundle. Manual tuning still
      marks the profile Custom.
      Gamma and Saturation use the same 1–20 centered control contract, with
      neutral shader parity at level 10. Gamma spans `0.5..1.0..1.5` and
      saturation spans `0.0..1.0..2.0`; both remain neutral when either preset
      is selected until owner testing chooses profile-specific values. The
      shader applies them after tone/contrast and before fog, without dirtying
      resident geometry. Worktree shortcuts are F10/Shift+F10 and remain
      suppressed in release builds by the existing non-F6 rule.
      Owner live review accepted both centered controls at their neutral level
      10 defaults. Runtime compilation passed for the projected and resident
      shader variants, completing this renderer-shading tuning milestone.
- [x] Remove release/default quick function-key toggles except `F6` renderer
      debug overlay. Resolution, font, scaling, and window-mode changes should
      go through options or explicit runtime launch configuration.
- [x] Retire the legacy Interface fog toggle and detach its retained protocol
      state from rendering so `Graphics > Fog` has sole ownership.
- [x] Add the official `Classic` and `Remaster` renderer presets plus `Custom`.
      Presets apply known bundles, and manual changes to bundled values switch
      the displayed preset to `Custom` without discarding the edited values.
- [x] Add a temporary client-side `Graphics > Tone` preview row for day/night
      palette testing, then retire the row once the server-owned clock landed.
      The internal cycle now consumes the server-owned 60-minute world clock
      over the custom `WORLD_TIME` packet and locally interpolates between
      syncs. It falls back to a local 60-minute preview clock until the first
      sync arrives.
- [ ] Add `High Performance` as a second saved renderer profile after Phase 7
      identifies the exact settings that recover FPS without hiding correctness
      problems behind reduced visual coverage.
- [x] Separate alpha/debug-only renderer switches from settings intended for
      normal players.
- [x] Replace the player-facing resolution list with `Graphics > Aspect Ratio`.
      Active player choices are `4:3` (`800x600`) and `16:9` (`960x540`);
      higher old values are migrated by aspect instead of remaining visible
      player choices.
- [ ] Replace integer/bilinear/bicubic terminology with OpenGL-native quality
      controls only if field testing shows a player-facing filter option is
      still needed.
- [x] Retire 720p/1080p from the normal in-game surface cycle after zoom testing
      showed aspect ratio and UI readability are the practical player-facing
      choices. Older 720p/1080p settings now migrate to `16:9`/`960x540`.
- [ ] Expand supported render-surface sizes in measured steps and record where
      sprites, UI panels, minimap, or world projection start to break.
- [ ] Stress test mouse-wheel zoom beyond the old limits and document the
      first failure point for sprites, walls, object ordering, and UI overlays.
- [ ] Decide which extreme resolution and zoom settings are shippable, which
      remain debug-only, and which should be blocked.

### Phase 9: Alpha Rollout

- [x] Establish the terrain-lit OpenGL replacement composite as the first
      alpha rollout baseline.
- [ ] Ship renderer-v2 behind an opt-in setting first.
- [ ] Ask alpha testers to run specific camera routes and crowded scenes.
- [ ] Collect screenshots or clips for regressions.
- [ ] Track bugs separately as renderer-v2 parity, visual-quality, or
      performance issues.
- [ ] Promote renderer-v2 to default only after the legacy path is no longer
      clearly better in any common play scenario.
- [ ] Keep the legacy renderer for one additional alpha cycle after promotion.

### Phase 10: Legacy Decommission

- [ ] Remove unused applet-era frame plumbing from the desktop client.
- [ ] Remove legacy scaler modes that only exist to compensate for old render
      instability.
- [ ] Replace legacy protocol and client-cache ceilings that constrain the
      remaster, including 8-bit local NPC/player counts, narrow local entity
      arrays, limited coordinate deltas, and sentinel values that collide with
      expanded direction or animation ranges.
- [ ] Remove temporary entity-prioritization workarounds once widened
      remaster-era packets can carry the full visible entity set directly.
- [ ] Delete dead compatibility branches after the fallback window closes.
- [ ] Update client settings, docs, and release notes to describe the new
      renderer behavior.
- [ ] Keep enough migration notes to explain old asset transparency decisions.

## Validation Checklist

- [ ] No see-through black pixels in opaque terrain textures.
- [ ] No red back-face flash when walking past `tinrock1` ore rocks.
- [ ] No rapid object, wall, or floor face flicker during slow camera rotation.
- [ ] No player/NPC/item sprite depth popping in crowded scenes.
- [ ] No major frame hitch when crossing active section boundaries.
- [ ] Integer or nearest scaling is crisp without excessive shimmer.
- [ ] Smooth scaling modes reduce shimmer without unacceptable blur.
- [ ] UI remains readable at supported desktop sizes.
- [ ] Draw distance remains at least as good as the current alpha client.
- [ ] High render-surface sizes and extended mouse-wheel zoom do not break
      sprite placement or world/UI clipping.
- [ ] Renderer-v2 can be disabled for comparison during the alpha period.

## Decision Gates

- If texture transparency fixes remove most see-through artifacts, keep that
  work independent and ship it before the full renderer rewrite.
- If direct framebuffer and presentation cleanup removes most camera hitching,
  ship that as the first renderer-v2 milestone.
- If flicker remains after transparency and presentation fixes, prioritize
  depth buffering over more scaler tuning.
- If the depth-buffer prototype becomes more complex than the legacy renderer
  itself, switch attention to a GPU-backed backend rather than expanding the
  software renderer indefinitely.

## First Implementation Slice

Start with a narrow but meaningful renderer-v2 foundation:

1. [x] Add frame timing and allocation counters around the current renderer.
2. [x] Add a direct framebuffer path that can display the existing software output.
3. [x] Remove the active frame path's dependency on `ImageProducer`.
4. [x] Replace current scaling presentation with persistent buffers and a clean
   present step.
5. Re-test the known bad acuity locations before touching polygon rendering.

Renderer telemetry is opt-in. Enable it with the JVM property
`-Dspoiledmilk.rendererTelemetry=true` or the environment variable
`SPOILED_MILK_RENDERER_TELEMETRY=true`. Optional JVM properties:
`-Dspoiledmilk.rendererTelemetryInterval=300` and
`-Dspoiledmilk.rendererSlowFrameMs=35`.
Telemetry reports include both lifetime averages and short-window averages
since the previous report. Use the `window` lines for live FPS diagnosis;
the cumulative `avg` lines are still useful for long-run drift and startup
effects.

The direct framebuffer path is also opt-in while it is being compared against
the legacy image-producer path. Enable it with the JVM property
`-Dspoiledmilk.directFramebuffer=true` or the environment variable
`SPOILED_MILK_DIRECT_FRAMEBUFFER=true`.

The GPU-presenter bridge is opt-in. Enable it with the JVM property
`-Dspoiledmilk.gpuPresenter=true` or the environment variable
`SPOILED_MILK_GPU_PRESENTER=true`. This path uses Java2D `VolatileImage`
surfaces and the existing Java2D OpenGL pipeline; it is not the final
LWJGL/OpenGL backend.

This slice gives immediate performance and timing data while creating the
surface that the deeper z-buffered or GPU renderer can plug into.

## Open Questions

- Should renderer-v2 require a modern desktop client only, or must it remain
  compatible with every inherited launch target?
- Should UI scale separately from the world, especially for high-resolution
  monitors?
- Should sprite and texture assets be converted offline into explicit alpha
  formats, or should conversion happen during client cache loading?
- How long should the legacy renderer remain available after renderer-v2
  becomes the default?
