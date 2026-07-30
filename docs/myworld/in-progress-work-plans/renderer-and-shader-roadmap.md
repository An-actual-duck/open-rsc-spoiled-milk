# Renderer And Shader Roadmap

This is the current AI-facing roadmap for Spoiled Milk renderer-v2,
shader, lighting, shadow, and visual-performance work. It consolidates the
active state from `renderer-v2-plan.md` and
`remaster-lighting-and-shadow-plan.md`.

Use this file first when deciding what renderer work to do next. The older
documents remain detailed implementation ledgers and historical checklists.
Use
[renderer-performance-investigation-plan.md](renderer-performance-investigation-plan.md)
for controlled workloads, current measurements, instrumentation gaps, and the
profile-tweak-compare experiment ledger.
For structural cleanup, file splitting, and stale-option quarantine work, use
`code-cleanup-and-modularization-plan.md`.

## Working Rule

This is an alpha remaster effort. Do not get tunnel vision on tiny optimizations
inside one renderer subsystem. Lock down each major architectural swing well
enough that visuals are acceptable and ownership is clear, then move to the
next structural problem.

Major renderer changes are allowed when they remove legacy limits, simplify
future work, improve frame stability, or make the OpenGL/remaster path a better
engine foundation.

## Current Baseline

The current accepted player-facing renderer baseline is:

- OpenGL primary window and input bridge.
- Renderer-v2 replacement composite for the world.
- Resident chunk OpenGL backend for terrain, walls, roofs, scenery, wall
  objects, and game objects.
- Legacy renderer still available as the data/parity source and fallback, but
  not the desired long-term rendering owner.
- `Classic`, `Remaster`, and `Custom` graphics presets.
- `Aspect Ratio` instead of a long resolution list.
- `Borderless` as a player-facing Graphics toggle, default-on for Classic and
  Remaster.
- Server-owned 60-minute day/night cycle.
- Directional/remaster lighting path with movable sun azimuth/elevation.
- Strengthened dawn/dusk/night tone filters driven by server time.
- Terrain shadow mask/decal proof promoted into the normal directional
  lighting path.
- Terrain-receiver shadows are accepted for the current alpha remaster
  baseline. They are not final shadow technology, but tuning should pause here
  unless a specific regression appears.
- First-pass emissive ambient glow exists for Remaster resident chunks. Lava
  overlay `11` and known fire-like scenery emit a cached warm glow mask sampled
  by the resident chunk shader. This is not a point-light or shadow-casting
  system; it is a cheap visual layer for lava/fire warmth.
- Remaster lighting is player-facing alpha functionality, not only a hidden
  debug proof. It is good enough for active testing, but still intentionally
  documented as incomplete shadow/material work.
- F6 debug HUD with simple and expanded modes.

The renderer has reached playable alpha quality in many areas. The remaining
work is now less about proving OpenGL can draw Classic and more about removing
the remaining bridges that make performance, shaders, and shadows harder than
they need to be.

## Completed Foundation

- Direct framebuffer/presenter work is complete enough for the OpenGL-primary
  path to own presentation.
- OpenGL primary window, scaling, fullscreen/borderless behavior, mouse mapping,
  input bridge, and mouse-wheel zoom are live.
- Explicit texture/sprite transparency work is mostly complete for the current
  OpenGL atlas paths.
- Native OpenGL UI, glyph, primitive, minimap, and overlay replacement paths
  are partly in place and are good enough for current alpha use.
- Static world geometry is split into terrain, wall, roof, wall object, game
  object, and unclassified model kinds.
- Resident chunk mesh products and GPU buffers are the runtime default for
  static world rendering.
- Resident object chunks now own materialized scenery and wall-object models.
- The old projected static world path has been reduced to a bridge where still
  needed instead of being the main visual owner.
- Texture atlas ownership is shared between projected and resident renderers.
- Renderer telemetry covers OpenGL phases, resident chunks, batch culling,
  sprite overlay behavior, shadow mask cost, and rolling recent timings.
- Resident chunk spatial culling is promoted into the default renderer-v2
  runtime path. It subdivides static-world material batches so frustum/fog
  culling can skip smaller terrain, wall, roof, scenery, and wall-object work
  before draw submission. Expanded F6 and frame captures report whether the
  spatial path is active. Dense-area testing showed the original 6-tile
  spatial cells culled aggressively but produced very high draw-call counts, so
  the default cell size is now a coarser 12 tiles with a runtime override
  available for A/B testing.
- Resident chunk uploads are now frame-budgeted. The default budget is
  `3.0ms` per rendered frame via `spoiledmilk.openglWorldChunkUploadBudgetMs`
  / `SPOILED_MILK_OPENGL_WORLD_CHUNK_UPLOAD_BUDGET_MS`; `0` disables the cap
  for A/B testing. Reused chunks are always accepted immediately, at least one
  dirty chunk upload is allowed per frame, and remaining dirty chunks are
  deferred so section/teleport bursts do not land entirely on one frame. Upload
  budgeting is fallback-aware: if the projected static-world bridge is not
  available for the current frame, resident uploads are forced to complete
  rather than presenting black terrain/scenery while chunks are partially
  resident. If resident chunks are incomplete and fallback exists, the
  projected-world bridge remains the visual fallback until resident ownership
  catches up. Expanded F6 reports `req/up/reuse/defer/evict` plus upload budget
  used/limit.
- Expanded F6 now includes `world load avg/max/recent`, measuring
  `World.loadSections` directly, plus `world load phases
  reset/main/up/bridge/chunk/preload`. Use these to confirm whether zoomed-out
  walking hitches line up with active section/window rebuilds rather than
  steady-state draw cost, then target the dominant blocking phase instead of
  tuning steady renderer draw.
- In OpenGL-primary mode, the F6 `fps` value is observed/rendered FPS from
  completed OpenGL frame cadence, not the legacy client loop counter. This is
  the number testers expect because it reflects what they visually see when
  frames are dropped or GL render time exceeds the frame budget.
- OpenGL-primary presentation defaults to game-loop pacing instead of GLFW
  vsync (`spoiledmilk.openglVsync=false`). The client loop already targets
  60 Hz; letting both the game loop and swap interval throttle presentation can
  create phase drift where cheap frames are still presented at an uneven
  cadence. `spoiledmilk.openglVsync` / `SPOILED_MILK_OPENGL_VSYNC` remain
  available for A/B testing.
- Remaster terrain shadow masks are intentionally kept cheaper than the first
  proof pass. The default mask is 512x512 with a smaller blur and coarser
  light-angle buckets so server day/night movement does not trigger 1024x1024
  CPU mask rebuilds during normal play. Runtime overrides remain available:
  `spoiledmilk.remasterShadowMaskTextureSize`,
  `spoiledmilk.remasterShadowMaskBlurRadius`,
  `spoiledmilk.remasterSceneryShadowBlurRadius`,
  `spoiledmilk.remasterShadowLengthScale`,
  `spoiledmilk.remasterSceneryShadowAlphaScale`,
  `spoiledmilk.remasterShadowMaskAzimuthBucket`, and
  `spoiledmilk.remasterShadowMaskElevationBucket`.
- Terrain shadow masks are applied in the resident chunk shader when that path
  is active. The old immediate-mode shadow overlay remains only as a fallback;
  normal remaster rendering should not pay for a second terrain pass.
- Remaster glow masks are independent of directional shadows. They are cached
  from resident chunk glow emitters, uploaded as a separate RGBA texture, and
  sampled on texture unit 2 after base remaster lighting/brightness but before
  day/night tone and fog.
- Remaster brightness is shader-owned for resident chunks. Day/night/player
  brightness should not dirty static chunk buffers while directional remaster
  lighting is active.
- Runtime release/debug function-key clutter has been removed except for the
  renderer debug overlay controls.
- Resident chunk draw submission coalesces adjacent compatible material ranges
  after culling. F6 `batch c/d/cull` still reports logical material batches,
  while `submit calls/binds` reports the reduced GL draw submissions and
  texture binds.
- Resident object chunks distinguish static object cells from animated object
  cells. Animated scenery may still rebuild a small dynamic chunk, but it must
  not dirty or re-upload the surrounding static scenery cell. Expanded F6
  reports chunk upload reasons such as `static-object-signature` and
  `animated-object-signature` so this ownership rule can be checked in dense
  animated areas.
- Resident chunk shader setup now separates pass-level uniforms from per-range
  texture/fallback state. Lighting, tone, fog, shadow-mask, and matrix uniforms
  should be bound once for the resident pass unless the pass itself changes,
  not resent for every material range.
- The shadow-mask cache key has been narrowed to shadow-owned inputs only.
  Broad resident chunk render signatures must not invalidate the shadow mask.
- Remaster shadow preparation now has a signature-first steady-state path.
  Roof/interior classification is cached by static world-chunk signature, and
  the shadow mask builder returns the existing mask immediately when the world
  signature plus quantized sun bucket are unchanged. This keeps normal frames
  from rebuilding caster lists or indoor/outdoor data just to rediscover a
  cache hit.
- The first renderer file-size refactor pass has split shader, texture atlas,
  resident chunk, projected mesh, frame capture, logging, material-label, and
  remaster shadow helper code out of `OpenGLFramePresenter`. It also split the
  package-local composite scene-command/world-sprite records and pure
  scene-command builder/classifier logic out of the presenter. Camera-space
  world-sprite quad submission now lives in `OpenGLWorldSpriteRenderer`, while
  world-sprite draw orchestration and command-sized sprite texture construction
  live in `OpenGLWorldSpriteDrawController` and `OpenGLSpriteTextureBuilder`.
  The presenter still owns window/input/pass orchestration and some bridge
  callbacks, but it should no longer be treated as the place to add low-level
  renderer systems.
- The controlled maximum-distance performance campaign has reached its
  allocation-only stopping point after 16 accepted, visually neutral cycles.
  Against the original exact-geometry stress baseline, total allocation is
  down 96.4%, client-loop allocation 96.7%, process CPU 57.6%, OpenGL p95
  75.8%, and OpenGL world p95 87.5%. The accepted endpoint holds 34 resident
  chunks and 209,162 triangles at OpenGL/world p95 of 2.303/0.900 ms. The
  sixteenth cycle still lowered its measured presenter allocation owner by
  18.0%, but moved presenter CPU and world time negligibly. Do not continue
  small allocation changes without a fresh profile tied to an actual problem.

## Current Shader State

Implemented or started:

- Projected world shader path exists.
- Resident chunk parity shader exists.
- Resident raw-material inspection shader exists and remains the clean-slate
  baseline for remaster lighting work.
- Remaster directional-light shader path exists for resident chunks.
- Resident chunk vertex data includes shader-ready material RGB, normals, and
  model kind.
- Terrain normals are smoothed enough for the current remaster terrain lighting
  target.
- Tone filters are shader uniforms and are driven by server-owned world time.
- Targeted terrain material variation has a shader-owned proof. Resident world
  chunk build tags only base terrain faces whose overlay id is `0`; overlays
  `1+` remain authored hard lines and do not receive the variation pass even if
  they share a similar color. The chunk builder also computes a vertex-level
  transition color from neighboring overlay-`0` terrain faces; when adjacent
  base terrain textures differ, the resident shader interpolates toward that
  transition color before lighting. This gives authored terrain gradients, such
  as tan texture ids `106`/`120` near `309,494`-`309,495`, a softer edge without
  bleeding into roads, floors, water, or cliffs. The shader then applies smooth
  world-space variation to matching untextured terrain material RGB. The current
  procedural tuning favors short/mid-range organic variation and keeps broad
  drift light so the pass does not collapse large fields into a new uniform
  color. It defaults to the grass color `#109000` seen from base terrain texture
  id `70` at test tile `304,504`.
  Runtime test knobs are
  `SPOILED_MILK_TERRAIN_VARIATION_ENABLED`,
  `SPOILED_MILK_TERRAIN_VARIATION_TARGET_RGB`,
  `SPOILED_MILK_TERRAIN_VARIATION_STRENGTH`, and
  `SPOILED_MILK_TERRAIN_VARIATION_TOLERANCE`. The player-facing Graphics row
  is `Terrain Variation`, which toggles the pass on/off for visual A/B testing.
  This is intentionally narrower than full tile blending; if exact map texture
  ids become necessary, add a terrain-material id attribute to resident terrain
  vertices instead of widening the RGB match blindly.
- Emissive ambient glow has a shader-owned first pass. Terrain overlay `11`
  creates lava glow emitters during terrain chunk construction on any visible
  map plane; it is not suppressed by roof/interior shadow classification.
  Non-overworld lava uses a lower emitter intensity because underground areas
  receive less directional light and otherwise make the same additive glow read
  too strong. Fire, fireplace, torch, skull torch, flame, furnace, cave furnace,
  and lava forge scenery are tagged before resident object chunks are built.
  The glow builder accumulates these emitters into a cached world-space RGB
  mask, and the resident shader adds the sampled glow before tone/fog. Future
  material work should make these emitters data-driven rather than keeping the
  first-pass classifier in `mudclient`.
- The parity-preserving resident material-family foundation is implemented and
  live-accepted. Static resident triangles now
  carry stable `TERRAIN`, `WATER`, `WALL`, `ROOF`, `SCENERY`, `FOLIAGE`, `ORE`,
  `EMISSIVE`, `EFFECT`, or `UNCLASSIFIED` ids through chunk signatures, VBOs,
  the resident shader interface, F6/session telemetry, and `Ctrl+F9` evidence.
  Twelve strict frames had complete `260,032`-triangle coverage with no unknown
  or contradictory classifications; dense visual/performance and relog/section
  lifecycle checks passed. The normal shader does not use the value to alter
  output yet.

Still incomplete:

- Fixed-function/OpenGL color-array behavior still exists in important bridge
  paths.
- Some fallback and bridge paths still bake visual state into CPU products.
  The accepted Remaster resident-chunk path keeps automatic tone/brightness
  presentation shader-owned, and these presentation-only changes must not dirty
  resident chunk buffers.
- Material behavior is not yet fully data-driven. Resident static families are
  explicit, but their visual responses remain parity-neutral and players,
  NPCs, ground items, projectiles, particles, and other world sprites still
  need a separate authored family contract before broad polish can be trusted.
- Shader comparison tooling is not yet strong enough to show legacy software,
  current OpenGL, and shader output for the same camera frame.
- Centered player controls for terrain/object shading, brightness/dimness,
  contrast, gamma, and saturation are implemented and owner-accepted. Further
  shader polish such as adjustable fog response, slope emphasis, or tile
  blending is not ready for release use.
- Glow is not yet bloom, a real point-light system, or a shadow caster. It is a
  cheap emissive mask meant to validate lava/fire warmth before heavier
  material-light work.

## Current Lighting And Shadow State

Implemented or started:

- Server owns world time and syncs custom clients.
- `::settime MMSS` and `::advtime MMSS` exist for development testing.
- Remaster lighting converts server cycle time into sun azimuth, sun elevation,
  shadow direction, and shadow length.
- Dawn, dusk, cool night, and deep blue presentation tones are active.
- The OpenGL world viewport now clears to a simple day/night sky color before
  world rendering: pale blue day, orange dawn, amber dusk, and dark blue night.
  The current pass is a lightweight procedural backdrop, not a full geometric
  skybox: it draws a strongly pitch-shifted vertical sky-to-fog gradient,
  and camera/time-rotated soft distant cloud blobs. The star layer is
  intentionally hidden for the public patch until capture/scale quality is good
  enough to show it cleanly. Clouds move with the same pitch field as the
  gradient, but are sampled from a taller repeated virtual sky so the player
  looks into more sky instead of losing detail at tilt limits.
- Distance fog now blends resident world pixels toward the same day/night fog
  presentation color instead of black, so the horizon reads as haze in front of
  the sky backdrop. The fog ramp starts well before the draw cutoff so vertical
  walls and fences enter haze more gradually.
- Dawn/dusk dimming is applied through tone RGB uniforms rather than animating
  the baked brightness multiplier.
- Semantic caster and receiver inventory exists for resident chunks.
- Roof coverage and roofless-interior classification exist as first-pass
  indoor/outdoor data.
- Terrain receives shadows from semantic wall/scenery caster proofs.
- Wall-boundary clipping exists for the world-space shadow mask.
- Terrain receivers classified as interior are suppressed before directional
  and contact shadow channels are composited. This keeps roofless building
  interiors clean while exterior boundary walls can still cast outward.
- Directional shadow blur is split by caster family. Wall/strip shadows default
  to blur radius `1`; scenery/game-object shadows default to blur radius `4`.
- Directional cast-shadow length defaults to scale `0.5`, and scenery/game
  object shadow darkness defaults to scale `1.3`.
- Contact shadows ground scenery, walls, and wall objects with accepted defaults
  of alpha `0.5`, radius scale `0.05`, and blur radius `2`.
- Terrain shadow masks are quantized by light buckets so normal day/night
  motion does not rebuild the mask every frame.
- Unchanged shadow frames reuse prior roof coverage, caster inventory, and mask
  results from signatures instead of walking the full shadow path every frame.
- Terrain shadow movement is visible in Remaster lighting. It remains quantized
  and can look stepped during accelerated `::advtime` testing, which is
  acceptable for the current CPU mask but not final-quality shadow motion.
- Lava and fire-like scenery can add warm ambient glow without casting shadows.

Still incomplete:

- Shadows are a terrain-receiver proof, not a full shadow engine.
- Diagonal wall shadows are intentionally skipped or simplified to avoid false
  triangular artifacts.
- Scenery shadows use broad heuristics, not per-asset shadow metadata.
- Shadows do not yet receive onto objects, walls, sprites, players, or NPCs.
- Shadow movement is visibly stepped during `::advtime` because mask rebuilds
  are bucketed. This is acceptable for normal play but not a final high-quality
  shadow solution.
- The CPU-built 512x512 shadow mask is a proof. A GPU-side, incremental, or
  lower-cost path is the likely long-term direction when shadow work is
  intentionally reopened.
- Emissive glow is first-pass and hand-classified. It should eventually come
  from material/object metadata and may later feed bloom, particles, or real
  localized lights if those become worth the cost.

## Biggest Remaining Swings

1. Shader ownership unification

   Move lighting, fog, tone, brightness, alpha, material sampling, and shadow
   terms into explicit shader-owned inputs. Stop baking visual settings into
   resident chunk geometry unless the setting actually changes geometry. This
   is the best next swing for making future lighting and polish predictable.

2. Material-aware visual polish

   Validate the new resident static-family coverage live, then add narrowly
   controlled shader-owned polish for terrain, water, foliage, ore, roofs,
   walls, scenery, and emissive surfaces. Give sprites, projectiles, and effects
   their own ownership contract before including them in the same controls.

3. World-space entity and sprite renderer

   Move NPCs, players, ground items, projectiles, and effects away from
   screen-space legacy command replay and into world-space sprite/billboard
   submissions with explicit depth anchors, alpha rules, and batching.

4. True world streaming ownership

   Turn the current section-window caches and resident chunk products into an
   explicit world-stream manager. It should own decode, model-input products,
   GPU upload, predictive preload, dirty flags, roof inclusion, and ready-state
   telemetry. This is the biggest path toward stable high draw distance.

5. Visibility and quality settings that really cull work

   Make fog/draw distance, roof visibility, entity distance, and quality
   presets affect what gets built, submitted, or drawn. A lower-quality setting
   should reduce terrain/scenery/entity work, not only change appearance.

6. Performance validation harness

   Add repeatable benchmark routes, frame dumps, frame diffing, and shader
   comparison captures. F6 is useful, but release-quality renderer work needs
   reproducible routes and automated regression checks.

7. Legacy renderer decommission plan

   Keep the legacy renderer while renderer-v2 is still an alpha remaster path,
   but continue shrinking bridge code. The long-term goal is explicit renderer
   data contracts, not permanent compatibility masks around old software
   renderer behavior.

8. GPU-side or incremental shadows

   Parked for now. When shadow work is intentionally reopened, replace or
   supplement the CPU-built terrain shadow mask with a cheaper approach.
   Candidate paths include a GPU mask/decal, sparse caster-local updates,
   lower-resolution masks with better filtering, or interpolation between
   cached masks. Keep semantic casters and indoor/outdoor classification as the
   data source.

## Next Non-Shadow Visual Targets

Shadows are accepted for the current alpha pass. The next visual work should
come from one of these areas unless a concrete shadow regression appears:

- Material families and polish uniforms: live-validate the parity-only resident
  foundation, then make static terrain, water, foliage, ore, walls, roofs,
  scenery, and emissive surfaces respond through explicit shader/material data.
  Sprites, projectiles, and effects remain a separate ownership milestone.
- Terrain variation and tile-edge blending: reduce flat broad fields and hard
  grass/dirt transitions without changing original assets or map data. The
  current first pass is targeted RGB variation for one terrain color at a time;
  future work can promote this into material families, exact texture-id
  selection, biome-style noise, and edge blending between unlike neighboring
  tile materials.
- Water, fountains, fishing spots, and animated scenery polish: use the
  existing animated-object split to improve motion, transparency, and material
  response without dirtying static chunks.
- Emissive materials and ambient glow: promote lava/fire glow from first-pass
  heuristics into material metadata, then evaluate bloom-like filtering, heat
  shimmer, ember particles, and localized light contribution without adding
  point-light shadow complexity too early.
- World-space projectiles, spell effects, and particles: move visual effects
  toward explicit world anchors, depth, batching, and optional remaster polish.
- Sprite/entity rendering modernization: improve billboard anchoring, depth,
  alpha handling, and batching for NPCs, players, ground items, and combat
  effects.

## Long-Term Shader Goals

- More interesting scenery shadows without hand-authoring every object. The
  current broad scenery shadow proof is useful, but the long-term goal is to
  derive rough caster shape automatically from existing model data. Candidate
  approaches include projected model footprints, coarse top-down silhouette
  masks, convex hulls around visible/caster vertices, object bounding volumes,
  or material/model-kind heuristics that produce line/blob/canopy primitives
  without per-object code. The current active pass starts this by sweeping
  captured game-object footprint bounds along the directional light for scenery
  cast shadows, while retaining the older soft heuristic as fallback. The
  current cast-shadow reach scale is tunable through
  `SPOILED_MILK_REMASTER_SHADOW_LENGTH_SCALE`, which does not affect contact
  shadows. Directional cast-shadow blur is split by caster family:
  wall/strip shadows use `SPOILED_MILK_REMASTER_SHADOW_MASK_BLUR_RADIUS`, while
  scenery/game-object shadows use
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_BLUR_RADIUS`. The intent is crisp
  walls without forcing sharpened rectangular silhouettes onto scenery.
  Scenery/game-object cast-shadow darkness is separately tunable with
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_ALPHA_SCALE`, currently defaulting to a
  slightly darker `1.3` scale.
- Scenery contact shadows. A first pass adds subtle, short-radius, soft
  footprint shadows at the base of scenery, walls, and wall objects to ground
  them against terrain. Game objects use captured rounded footprint bounds plus
  thin outward bleed; walls use line-footprint contact. This pass is combined
  after the directional-shadow blur so small contact halos do not get widened
  by the long-shadow blur. It has a separate tiny diffusion pass so object
  footprints read like soft grounding rather than hard outlines. Developer
  diagnostics can temporarily exaggerate alpha/radius/blur through
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_ALPHA`,
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_RADIUS_SCALE`, and
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_BLUR_RADIUS` when validating whether
  game objects are receiving this pass. The radius scale controls outward bleed
  beyond the estimated footprint, not total radius from object center. Future
  work can refine the shape beyond conservative footprint bounds. The accepted
  first-tuned defaults are alpha `0.5`, radius scale `0.05`, and contact blur
  radius `2`.
- Smoother shadow movement across terrain. The current terrain shadow mask uses
  quantized light buckets so normal play is affordable, but fast-forwarded time
  exposes stepped movement. Future options include GPU-side projected masks,
  interpolation between cached light buckets, temporal blending, lower-cost
  incremental updates, or a cheaper lower-resolution mask with better filtering.
- Better shader performance and cleaner shader ownership. Presentation-only
  state such as tone and day/night color should remain uniforms. Geometry or
  chunk rebuilds should happen only when geometry, material identity, or real
  visibility changes. Keep moving brightness, fog, material response, shadow
  contribution, and polish controls toward shader/material inputs rather than
  baked CPU vertex colors.
- Material-aware polish. Once parity is stable, shader work should understand
  terrain, water, walls, roofs, foliage, ore, scenery, sprites, projectiles, and
  effects as different material families instead of applying one generic rule to
  everything.
- Subtle material variation for broad flat areas. Large runs of identical
  terrain or scenery color can still read too flat even after relief lighting.
  A future remaster polish pass should test deterministic low-amplitude color
  variation or noise, keyed by tile/material position, so repeated same-texture
  areas gain visual breakup without changing original assets or flickering.

## Long-Term Renderer Goals

- Continue optimization until renderer-v2 reaches diminishing returns for the
  current alpha goals. The focus should remain on structural wins: culling real
  work, reducing bridge paths, stabilizing chunks, shrinking rebuilds, reducing
  draw calls/binds, and keeping high draw distance practical.
- Use the renderer as a capability unlock, not only as a faster Classic clone.
  Once the core renderer feels stable enough, explore what the old RuneScape
  Classic renderer made impractical:
  - camera tilt and richer camera controls without breaking billboard visuals
  - cleaner particle and spell effects
  - weather, ambient effects, fog variants, and post-process style polish
  - better projectile/effect anchoring in world space
  - richer debug/inspection views for map editing and content work
  - optional remaster presentation features that preserve Classic assets while
    taking advantage of depth, shaders, and modern GPU ownership
- Keep practical-use experiments separate from renderer foundation work. If a
  feature is mainly a showcase or gameplay-facing remaster addition, document
  it as a renderer capability experiment so optimization work does not get
  confused with content or feature design.

## Started Areas With Most Improvement Room

- Resident chunk backend: strong foundation, but still needs clearer dirty
  flags, async or incremental preparation, quality-aware build windows, and
  less dependence on baked visual settings. Static and animated object chunks
  are now separated, but the remaining animated chunk rebuild is still CPU-side
  proof work. Spatial draw culling is now on by default, but true streaming
  ownership should eventually avoid preparing work that cannot contribute to
  the visible frame.
- Shader path: enough exists to prove direction, but material ownership and
  fixed-function retirement remain large wins.
- Terrain/scenery shadows: accepted for the current alpha baseline and parked.
  The system is still proof-like internally, but current visuals are good
  enough. Future work should focus on smoother motion, better scenery/wall
  metadata, cheaper rebuilds, and broader receivers only after a deliberate
  decision to reopen shadows.
- Entity sprites: visually much better than earlier alpha work, but still an
  important modernization target because sprites are central to Classic. The
  first scene/entity ownership slice now carries each captured multipart layer's
  exact frame-local anchor index and legacy draw order into the composite
  renderer. Constant-time validated ownership is preferred, the old
  screen-bound matcher remains only as a compatibility fallback, and F6 /
  structured captures report exact/fallback/unmatched counts. Private visual
  review and 12 strict frames accepted the slice with 2,424 exact matches,
  zero fallbacks/unmatched/order mismatches, 79 NPCs, one player, three ground
  items, and no suspicious visibility loss. The follow-up maximum-distance
  idle and active combat JFR phases now separate legacy scene, capture,
  composite, atlas, and draw costs. They selected a frame-owned grouped
  world-sprite snapshot as the second slice. That candidate reuses the same
  captured animation-layer objects, validates the entire snapshot set, and
  retains the legacy typed command builder as an all-or-nothing fallback. It
  is compiled and guard-tested but still awaits private visual/capture
  acceptance.
- Quality settings: presets exist, but settings do not yet consistently reduce
  underlying renderer work.
- Tooling: F6 telemetry is good for live diagnosis, but capture replay and
  benchmark automation would prevent repeated manual rediscovery.
- Code organization: the initial OpenGL presenter split and first code-level
  `LEGACY BRIDGE` / `RENDERER-V2 OWNER` labels are in place. Pure composite
  scene-command building, camera-space world-sprite quad submission,
  world-sprite draw orchestration, and command-sized sprite texture building
  now live outside the presenter. The exact sprite-owner link is the first
  concrete seam across that split; later slices can replace legacy capture
  inputs without changing the proven depth-owned draw path all at once.

## Recommended Near-Term Order

1. Treat the completed retention-characterization route as sufficient: old
   generation plateaued across relog and the second route, direct buffers were
   bounded/reclaimed below `77MB`, and no heap/cache tuning is warranted.
2. Treat day/night color, Remaster directional lighting, terrain-receiver
   shadows, and the static/animated object chunk split as the current accepted
   alpha baseline.
3. Treat the completed parity-preserving material-family foundation as the
   baseline, then continue material-aware shader polish when visual work is
   selected:
   terrain, water, foliage, ore, walls, roofs, scenery, sprites, projectiles,
   and effects should get explicit material families instead of one generic
   response. The proposed first metadata/telemetry slice is tracked in
   [renderer-material-family-foundation-plan.md](renderer-material-family-foundation-plan.md).
4. Treat the completed first terrain-variation and tile-edge blending pass as
   the baseline; revisit it only with a specific captured visual regression.
5. Treat the 16-cycle steady-state allocation campaign as complete. Preserve
   its exact maximum-distance control and re-profile only when selecting a new
   architecture target or investigating a real regression.
6. Continue the explicit renderer-v2 scene/entity boundary. The first slice
   preserves exact anchor ownership through the legacy capture stream. The
   second candidate groups those exact captured layers in a renderer-owned
   frame snapshot and bypasses per-frame command-wrapper/list/sort
   reconstruction when complete validation passes. Accept it under dense
   actors, combat, ground items, and effects, then move players, NPCs, ground
   items, projectiles, and effects from captured screen-space layers toward
   direct world-space inputs, parity-complete persistent texture ownership,
   ordered batching, and culling. Retire matching legacy scene
   sort/rotation/removal and capture work incrementally as parity is proven.
7. Turn quality settings into real work culling: entity distance, draw distance,
   roof visibility, sprite/effect distance, and weak-hardware presets should
   reduce built/submitted/drawn work.
8. Preserve the completed maximum-distance idle and active entity/effect JFR
   phases as the first ownership-profile pair. Formal benchmark automation is
   still desirable; dense scenes and zoomed-out movement should not regress
   to manual F6-only observation.
9. Continue true world-stream lifecycle ownership—decoded, CPU-built,
   GPU-ready, active, and stale—as a separate boundary/relocation program.
   Do not mix its transition-tail measurements with steady renderer results.

## Open Long-Term Questions

- How far should Remaster lighting go before it becomes a distinct gameplay
  feature instead of only a visual renderer feature?
- Should Classic mode keep a simplified clock while Remaster owns visual
  day/night transitions?
- How much automatic model-derived shadow shape can we get before explicit
  per-asset shadow metadata becomes necessary?
- What renderer capability experiments should become real gameplay or showcase
  features after the foundation reaches diminishing returns?
- What is the final supported draw distance at 4:3 and 16:9?
- Which low-quality settings should be exposed for weak hardware?
- How long should the legacy renderer remain packaged once renderer-v2 is the
  default?
