# Remaster Lighting And Shadow Mini Plan

This document is the forward path for Spoiled Milk's remaster lighting work.
It exists so future AI sessions can start from the accepted raw-material
baseline instead of reopening old Classic-lighting and shadow-proof experiments.

For the combined renderer and shader status map, start with
[renderer-and-shader-roadmap.md](renderer-and-shader-roadmap.md). This mini plan
remains the focused implementation ledger for remaster lighting and shadows.

## Current Baseline

- The current player-facing Remaster alpha baseline uses the resident chunk
  directional-light shader, server-owned day/night time, dawn/dusk/night tone
  filters, and terrain-receiver shadow masks from semantic wall/scenery caster
  data.
- Terrain lighting shape and contrast are visually accepted for now. Terrain
  shadows move with server time/light direction, but movement is quantized by
  coarse light buckets and can look stepped during accelerated `::advtime`
  tests.
- The current terrain-receiver shadow pass is accepted for the alpha remaster
  baseline. It is not a final full shadow engine, but it is visually good
  enough to pause shadow work and move to other remaster visuals.
- Accepted shadow defaults are: cast length scale `0.5`, wall/strip blur radius
  `1`, scenery/game-object blur radius `4`, scenery/game-object alpha scale
  `1.3`, contact alpha `0.5`, contact radius scale `0.05`, and contact blur
  radius `2`.
- First-pass emissive ambient glow is active for the resident Remaster path.
  Lava overlay `11` and known fire-like scenery add a cached warm RGB glow mask
  in the shader. This is deliberately separate from directional shadows: it
  does not cast shadows, does not change shadow masks, and should not be used as
  a substitute for future point-light work.
- Static resident object chunks are separated from animated object chunks.
  Animated scenery may still rebuild a small dynamic chunk, but it should not
  dirty surrounding static scenery. Expanded F6 should report
  `animated-object-signature` when this expected dynamic update happens.
- The clean inspection starting point remains the resident chunk raw-material
  shader:
  `SPOILED_MILK_OPENGL_WORLD_CHUNKS_RAW_MATERIAL_SHADER=true` /
  `-Dspoiledmilk.openglWorldChunksRawMaterialShader=true`.
- This mode draws resident chunks through GLSL while ignoring baked resident
  lighting, texture-light values, classic shade bands, and shader fog.
- Textured faces sample textures directly. Flat fallback faces use raw material
  RGB.
- This is not a player-facing renderer. It is the inspection baseline for
  building new remaster lighting from first principles.

## Target

Build a movable directional lighting system for the OpenGL/resident-chunk
renderer:

- A directional light can move by changing azimuth/elevation.
- Terrain, walls, and scenery react visibly to the light direction.
- Terrain, walls, and scenery can cast shadows.
- Shadows move when the light direction moves.
- Scenery and wall objects have subtle first-pass contact shadows at their
  bases so they remain visually grounded even when cast shadows are long or
  weak.
- Emissive materials such as lava and fire can add local ambient color without
  becoming shadow casters.
- Walls and scenery respect indoor/outdoor classification.
- Shadows respect clipping well enough that they do not obviously pass through
  solid walls or closed building boundaries.
- Shadow receivers classified as interior are suppressed directly for the
  terrain shadow mask. This applies to directional cast shadows and contact
  shadows; ray-crossing is still used after that for outdoor receiver points
  so boundary walls can cast outward without leaking inward.
- Casting shadows onto scenery, walls, sprites, players, NPCs, and other
  objects is a later polish goal. Terrain receiving comes first.
- Do not reopen shadow tuning unless a concrete regression appears or the next
  intentional shadow swing is explicitly chosen.

## Hard Rules

- Do not reuse Classic/legacy lighting as the Remaster lighting source.
- Do not tune around old diagonal-wall shade artifacts.
- Do not extend the parked shadow proof unless intentionally mining it for
  data-shape lessons.
- Do not infer final shadow ownership from rendered triangle accidents when
  semantic world/build data is available.
- Prefer clear data ownership and coarse correct shadows over tiny visual
  tweaks to a weak foundation.

## Phase 1: Clean Directional Light

Goal: prove lighting can move without any shadows.

- [x] Add an explicit remaster directional-light state:
  `azimuth`, `elevation`, `intensity`, `ambient`, and optional color.
- [x] Feed that state directly into the resident chunk shader behind
  `SPOILED_MILK_OPENGL_WORLD_CHUNKS_REMASTER_LIGHTING_SHADER=true` /
  `-Dspoiledmilk.openglWorldChunksRemasterLightingShader=true`.
- [x] Use raw material color, texture sample, vertex normal, and model kind as the
  lighting inputs.
- [x] Ignore legacy light, base legacy light, texture-light factor, and classic fog
  contribution in this path.
- [x] Keep the existing raw-material mode available as a comparison baseline.
  If both raw-material and remaster-lighting shader flags are set, raw-material
  mode wins so the no-lighting baseline remains easy to force.
- [x] Add a simple debug readout for the current light direction in expanded
  F6 debug. The temporary `Ctrl+F7` azimuth and `Ctrl+F8` showcase controls were
  removed after capture testing; future light motion should come from the
  server-owned world time system instead of client-only debug keys.
- [x] Add the first lighting-balance refinement after visual validation:
  higher ambient fill, lower direct intensity, terrain normal blending toward
  up-normal to reduce hard triangle facets, and wrapped/two-sided diffuse fill
  for walls and scenery so unlit sides do not collapse to near-black.
- [x] Replace the first-pass terrain faceting workaround with terrain-only
  smoothed normals in `Renderer3DWorldChunkFrame`: terrain triangle normals are
  accumulated by matching world vertex coordinate, then written back to all
  terrain vertices at that coordinate. Walls and scenery keep face normals for
  crisp directional response. The shader terrain blend was then tightened back
  toward the real normal so hills keep directional light while avoiding sharp
  pyramid-like points.
- [x] Increase terrain contrast after smoothed normals made slope shadows too
  subtle: lower default ambient fill, raise direct intensity, reduce terrain
  diffuse wrap, and apply a mild terrain-only `smoothstep` contrast curve.
  This should make hills read as rounded forms with a clear lit side and dark
  side rather than returning to faceted pyramids.
- [x] Visual validation accepted the terrain lighting shape/contrast after the
  terrain-only smoothed normal and contrast pass. Treat terrain directional
  lighting as good enough for this phase unless later shadow work exposes a
  concrete regression.

Acceptance:

- Rotating or changing the light direction visibly changes terrain, walls, and
  scenery.
- The scene no longer looks like Classic shade bands.
- No shadows are drawn yet during this phase; later phases now own the accepted
  terrain-receiver shadow mask.
- Visual errors are easy to classify as lighting-input problems, not shadow
  projection problems.

## Phase 2: Semantic Caster And Receiver Inventory

Goal: create trustworthy shadow data before drawing shadows.

- [x] Add a frame-level semantic inventory pass over resident world chunks.
  Expanded F6 now reports terrain receiver chunks/triangles and eligible caster
  counts as `all/w/go/wo/out/clip`, where clipping candidates currently mean
  opaque walls or wall objects. This is telemetry only; no shadow mask is drawn.
- [x] Add an opt-in visual inventory overlay:
  `SPOILED_MILK_REMASTER_SHADOW_INVENTORY_DEBUG=true` /
  `-Dspoiledmilk.remasterShadowInventoryDebug=true`. It draws translucent cyan
  terrain receiver coverage, colored caster edges, and red top bars for current
  clipping candidates. This overlay is only for validating source data
  placement before real shadow projection.
- [x] Visual validation accepted for the first inventory overlay after receiver
  tint was changed to draw as an always-visible debug layer. Terrain receiver
  coverage, caster edges, and clipping candidate markers all appear as
  described.

Receivers:

- Terrain is the first supported receiver.
- Wall/scenery/object receiving is deferred.
- Receiver data should include plane, tile/triangle bounds, material kind, and
  world position.

Casters:

- Walls.
- Terrain ridges/slopes where useful.
- Scenery and wall scenery, especially trees, fences, signs, counters,
  windmill blades, ladders, fountains, and other tall/solid objects.

Caster data should include:

- model kind.
- plane.
- base footprint or base edge.
- base height and top height.
- width/radius.
- opacity/strength.
- outdoor-only flag.
- optional explicit content override.
- clipping category, such as solid wall, fence, decorative scenery, or open
  pass-through object.

Acceptance:

- Debug view can show caster outlines and receiver terrain.
- Caster counts are stable while standing still and moving the camera.
- Hidden roofs and resident object toggles do not silently erase required
  caster metadata unless that is the intended setting.

## Phase 3: Indoor/Outdoor Classification

Goal: prevent building interiors and roofed areas from creating nonsense
shadows.

- [x] Add first-pass roof coverage data to resident world chunks. Coverage comes
  from roof input (`wallRoof`) and remains available even when roof geometry is
  hidden.
- [x] Add first-pass classification telemetry. Expanded F6 reports
  roof-covered/outdoor/unknown receiver triangles and casters.
- [x] Extend the opt-in inventory overlay to show classification: outdoor
  receiver terrain stays cyan, roof-covered receiver terrain is amber, unknown
  receiver terrain is white, and roof-covered casters get a blue top marker.
- [x] Add cross-chunk roof coverage lookup for caster classification. Object
  chunks without their own roof payload are classified against the resident
  world chunks' roof coverage, reducing unsafe unknown caster ownership.
- [x] Add first-pass sunlight eligibility counts. Expanded F6 reports casters
  that are eligible for sunlight shadows, suppressed because roof-covered, and
  suppressed because unknown. The debug overlay marks eligible casters with a
  green top edge.
- [x] Add first-pass roofless interior classification. The classifier now uses
  roof coverage when available, then falls back to a per-plane flood fill built
  from semantic wall blockers: loaded-region border tiles are outdoor, and
  enclosed unreached tiles are treated as indoor. This lets roofless interiors
  suppress outdoor sunlight receivers/casters without editing original map roof
  data.

- Classify terrain and casters as outdoor, indoor, or unknown.
- Use roof/building data when available.
- Use wall boundaries and enclosed-area heuristics only when stronger metadata
  is missing.
- Start conservative: unknown/indoor casters can be suppressed until manually
  allowed.
- Add explicit overrides for known awkward assets and map regions.

Wall-specific requirements:

- Exterior-facing walls may cast outdoor shadows.
- Interior-only walls should not cast outdoor shadows.
- Walls under roofs should generally be suppressed for outdoor sunlight.
- Building boundaries should be able to block or clip shadows.

Acceptance:

- Buildings do not fill interiors with outdoor shadow artifacts.
- Exterior walls cast only where they plausibly face open outdoor space.
- The system fails quietly by omitting uncertain shadows instead of drawing
  obvious wrong ones.

## Phase 4: Clipping-Aware Terrain Shadow Mask

Goal: draw movable shadows on terrain without obvious wall clipping.

- [x] Promote the accepted terrain shadow mask proof into the normal
  Directional lighting path. The preferred runtime switch is now
  `SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK=true|false` /
  `-Dspoiledmilk.remasterTerrainShadowMask=true|false`; the older
  `SPOILED_MILK_REMASTER_TERRAIN_SHADOW_MASK_DEBUG` alias is still recognized.
  The mask defaults on for Directional lighting and off for Classic. It draws a
  dark terrain-only mask from sunlight-eligible casters using the current
  remaster light direction. Roof-covered and unknown casters are excluded. The
  mask uses a small spatial lookup so terrain triangles only test nearby
  projected casters.
- [x] Replace the first mask's centroid-only triangle alpha with per-vertex
  alpha interpolation plus a small centroid carry. This is intended to reduce
  spiky wall endpoints and faceted scenery-shadow edges without changing the
  caster/receiver data model.
- [x] Start the world-space mask/decal proof. The terrain shadow debug path now
  rasterizes sunlight casters into a low-resolution world-space alpha texture,
  softens that texture, and projects it over terrain. This intentionally moves
  shadow softness and silhouettes away from receiver triangle geometry. The
  first proof still uses the current caster shapes; semantic tree/wall caster
  primitives are the next quality step if this direction is visually accepted.
- [x] Add the first semantic scenery primitive. `GAME_OBJECT` scenery no longer
  uses its raw model edge as the terrain-shadow caster; it projects an
  orientation-independent soft shadow made from a narrow trunk-like line plus a
  lower-opacity canopy/blob. Wall and wall-object strip shadows are left on the
  previous path because the current wall result is visually acceptable.
- [x] Add expanded F6 telemetry for the accepted world-space shadow mask path:
  mask size/visible pixels, build/upload time, cache hit/rebuild/upload/skip
  counts, and strip versus soft scenery caster counts. This makes it easier to
  verify that steady frames reuse the cached mask and only rebuild when chunks
  or remaster light state change.
- [x] Add first-pass wall-boundary clipping for the world-space shadow mask.
  Shadow samples now trace from the caster toward the receiver through the same
  tile-boundary blockers used by the roofless-interior flood fill, with a small
  start offset so a boundary wall does not block its own exterior shadow. This
  is intentionally coarse and should be visually checked around corners,
  diagonal walls, fences, and dense roofless interiors.
- [ ] Revisit direct wall-segment clipping on top of the tile-boundary blocker
  pass. A first implementation kept a small tile lookup of wall edges and made
  diagonal/irregular walls participate in clipping, but it pushed shadow-mask
  rebuilds during light rotation from sub-millisecond territory to roughly
  `8ms` in dense areas. The code is parked behind
  `REMASTER_SHADOW_MASK_DIRECT_WALL_SEGMENT_CLIP=false` until this can be
  moved to a cheaper structure, lower-resolution mask, or sparse caster-local
  clipping pass.

Roofless interior status:

- Roof coverage handles buildings that have valid roof metadata, but some
  interiors, such as the bank around `283,567`, are roofless in map data.
- The first flood-fill classifier is implemented and runs before shadow
  projection. It is deliberately conservative and wall-driven, so it should be
  visually checked in dense towns, roofless banks, fenced outdoor areas, and
  dungeons before it becomes a player-facing remaster lighting option.

Recommended first implementation:

- Build a terrain shadow mask/decal in world space from semantic casters.
- Project each caster along the current directional light vector.
- Accumulate/cap shadow strength per receiver tile or terrain triangle.
- Clip projected shadow spans against blocking wall/building boundaries.
- Keep the mask cached by chunk plus light direction bucket, caster signature,
  receiver signature, and clipping signature.

Clipping priority:

- Stop shadows at solid building walls and closed boundaries.
- Allow decorative pass-through scenery to be ignored at first.
- Fences can be tuned separately because some are visually solid but gameplay
  passable or semi-transparent.
- Object-to-object shadow receiving is deferred.

Acceptance:

- Shadows move when azimuth/elevation changes.
- Shadows are attached to terrain, not hovering above it.
- Shadows do not visibly pass through solid walls in common building cases.
- Diagonal walls do not produce large triangular artifacts.
- FPS remains acceptable in dense areas before expanding receiver complexity.

## Phase 5: Server-Owned Day/Night Cycle

Goal: make day/night a world system that renderers consume, not a renderer
mode.

- [x] Add a server-owned world clock with deterministic day progress and phase
      data. The server is the source of truth so future mechanics, NPC behavior,
      spawns, skilling, events, and client visuals can all agree on the same
      time.
- [x] Sync world time to clients on login and periodically after that. Clients
      should interpolate locally between sync packets so visuals remain smooth
      without high-frequency server traffic.
- [x] Keep old clients compatible. The sync path should be optional/custom so
      older clients can continue playing without day/night visuals.
- [ ] Have Classic presentation consume the same world time with a simple clock
      or phase indicator while preserving Classic lighting.
- [x] Have Directional/Remaster presentation convert server time into sun
      azimuth, sun elevation, shadow direction, and shadow length. The first
      server-cycle curve keeps dawn/dusk light low for longer shadows, raises
      elevation near midday for shorter shadows, and continues rotating through
      night without separate moon logic. Ambient/direct intensity remain fixed
      remaster-light settings for now.
- [x] Bucket the terrain shadow-mask sun input before cache/signature
      generation. The visual light can move continuously, but the software
      terrain mask must not rebuild every frame as the server-cycle angle
      changes; use coarse azimuth/elevation buckets until this becomes a
      cheaper GPU-side or incremental path.
- [x] Keep the terrain shadow-mask cache signature scoped to shadow-owned
      inputs only: mask constants, mask bounds, quantized light/caster
      projections, and caster geometry. Do not mix full resident chunk render
      signatures into this cache key, because animated or otherwise unrelated
      chunk state can force expensive 1024x1024 mask rebuilds even when the
      projected shadow set is unchanged.
- [x] Recheck terrain shadow movement after the renderer file-size refactor.
      Later visual testing on `2026-06-29` confirmed directional shadow
      movement is present and acceptable for the current alpha path. Remaining
      quality work is not whether shadows move at all, but how to make that
      movement smoother and cheaper than the current quantized CPU mask.
- [x] Add dev-only server time controls: `::settime MMSS` immediately sets the
      server-owned cycle position, while `::advtime MMSS` advances the server
      clock at an accelerated visible rate so day/night color and shadow motion
      can be inspected in-game.
- [x] Add a temporary client-side tone preview under `Graphics > Tone` so dawn,
      dusk, and night palettes could be tested, then retire the manual row once
      server-owned world time landed. Tone is now an internal presentation state
      driven by world time, not a player-facing option.
- [x] Use a 60-minute world cycle as the first target: 30 minutes logical day
      and 30 minutes logical night. The visual breakdown should be about
      25 minutes clear day, 25 minutes night, and 10 total minutes of
      transition. A practical first layout is 5 minutes dawn at the start of
      the day half, 25 minutes neutral day, 5 minutes dusk at the start of the
      night half, and 25 minutes night. The internal cycle now consumes the
      server-owned world clock over the custom client protocol and only uses
      local time as a pre-sync fallback.
- [x] Use the accepted preview tones as the initial visual palette:
      `Sunrise Amber` for dawn, `Rose Dusk` for dusk, `Cool Night` for the
      brief edges around night, and `Deep Blue` for most of night. Within the
      25-minute night visual block, target roughly 10 percent Cool Night on
      entry, 80 percent Deep Blue, and 10 percent Cool Night before dawn.
- [x] Runtime dawn/dusk presentation should temporarily dim one brightness step
      below the player's saved brightness. `High` presents as `Medium`,
      `Medium` presents as `Low`, and `Low` should use one extra internal dim
      step below the saved `Low`. This must not mutate or display as a changed
      player setting; it is only the active day/night presentation multiplier.
- [x] Ease dawn/dusk brightness into and out of that dimmed presentation rather
      than stepping brightness down at the exact phase boundary. This keeps
      dusk from looking like it gets dark before the rose/cool-night tones are
      visible, and keeps dawn from snapping darker before the amber tone reads.
      Implementation note: the runtime day/night cycle must keep
      `currentBrightnessMultiplier()` stable during automatic time changes,
      because renderer chunks bake/signature brightness. Temporary transition
      dimming is applied through the tone RGB uniforms instead.
- [x] Strengthen the first accepted `Sunrise Amber` and `Rose Dusk` values so
      they are clearly visible during normal play instead of only reading as a
      mild brightness shift.
- [x] Cross-fade the night edge phases instead of hard switching filters:
      dusk fades to `Cool Night`, `Cool Night` fades into `Deep Blue`, and the
      end of night fades back to `Cool Night` before dawn. Color interpolation
      uses an eased curve so phase handoffs do not flash black or visibly gap.
- [x] Add a lightweight sky presentation tied to the same day/night state. The
      OpenGL world viewport clears to pale blue for day, orange for dawn, amber
      for dusk, and dark blue for night before terrain/walls/scenery render.
      The current sky pass uses a cheap strongly pitch-shifted sky-to-fog
      gradient and camera/time-rotated soft distant cloud blobs. The star layer
      is intentionally hidden for the public patch until capture/scale quality
      is good enough to show it cleanly. Clouds move with the same pitch field
      as the gradient, but are sampled from a taller repeated virtual sky so the
      player looks into more sky instead of losing detail at tilt limits.
      Distance fog blends world pixels toward a matching day/night fog color
      instead of black and starts feathering well before the draw cutoff.
- [x] Keep gameplay ownership separate from renderer settings. Settings may
      control visual richness later, but they should not decide whether world
      time exists.

## Phase 6: Quality And Controls

Goal: turn the proof into a usable remaster option.

- [x] Add fixed soft edges after hard-edged projection became visually
  correct.
- [x] Add light debug presets only as development tools; production day/night
  should come from server-owned world time.
- [ ] Add per-asset shadow overrides for bad scenery cases. Parked until
  broad visual work has moved on; the current generic scenery shadows are
  accepted for alpha.
- [x] Add first-pass scenery contact shadows. These are small, soft,
  base-anchored mask contributions that help scenery read as attached to the
  ground. They do not rotate or stretch with the sun like directional shadows;
  they behave more like ambient occlusion or grounding contrast. The first
  version uses conservative footprint shadows derived from object bounds/model
  kind, and expanded F6 reports them as the third value in
  `caster strip/soft/contact`. Game objects use a thin soft halo around their
  captured rounded footprint bounds; walls use a thin line-footprint contact.
  The contact pass is combined after the directional-shadow blur so tiny
  grounding halos do not get expanded by the long-shadow blur. It has its own
  small diffusion radius so object-specific footprints read like soft grounding
  shadows instead of hard outlines. The contact radius scale controls visible
  outward bleed beyond the estimated footprint instead of total radius from the
  object center. Developer-only diagnostics can exaggerate this pass with
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_ALPHA`,
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_RADIUS_SCALE`, and
  `SPOILED_MILK_REMASTER_CONTACT_SHADOW_BLUR_RADIUS`; these are for visual
  validation, not player-facing settings. The accepted first-tuned defaults are
  alpha `0.5`, radius scale `0.05`, and contact blur radius `2`.
- [x] Add a first player-facing Remaster lighting path after visual validation.
  The live version is still alpha quality: terrain lighting, day/night tones,
  and terrain-receiver shadows are usable, while full shadow/material polish is
  still future work.
- [x] Split directional shadow blur by caster family. Wall/strip shadows use
  `SPOILED_MILK_REMASTER_SHADOW_MASK_BLUR_RADIUS` and default to radius `1`;
  scenery/game-object cast shadows use
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_BLUR_RADIUS` and default to radius `4`.
  This preserves crisp building shadows without making trees and irregular
  scenery read as rectangular.
- [x] Tune scenery/game-object cast-shadow darkness independently with
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_ALPHA_SCALE`, defaulting to `1.3`.
- [x] Suppress all terrain shadow-mask receiver pixels classified as interior
  before applying either directional or contact shadow channels. This restored
  roofless building interior shadow blocking while still allowing exterior
  boundary walls to cast outward.
- [x] Add first-pass emissive ambient glow. Terrain overlay `11` contributes
  lava glow emitters during world chunk construction; fire, fireplace, torch,
  skull torch, flame, furnace, cave furnace, and lava forge scenery are tagged
  before resident object chunks are built. `RemasterGlowMaskBuilder` accumulates
  those emitters into a cached world-space RGB texture sampled by the resident
  shader on texture unit 2 after base remaster lighting/brightness and before
  day/night tone/fog. This is a visual warmth layer only; it does not cast
  shadows.

## Next Refinement Options

Shadow work is paused after the accepted alpha pass. These are the future
directions to use only when intentionally returning to shadows, not the next
default work queue:

- GPU-side or incremental shadow masks. Move the CPU-built terrain mask toward
  GPU generation, sparse caster-local updates, or another lower-cost path so
  shadows can move more smoothly than the current bucketed cache permits.
- Cached light-bucket interpolation. Keep the current CPU mask, but blend
  between neighboring cached light buckets in the shader so normal day/night
  motion appears smoother without rebuilding every frame.
- Model-derived scenery silhouettes. Replace broad trunk/blob heuristics with
  rough automatic shapes derived from existing object model data, such as
  top-down footprints, convex hulls, or coarse alpha/silhouette masks. This is
  the main path toward more interesting tree/scenery shadows without
  hand-authoring every asset. The current active pass reuses captured object
  footprint bounds to sweep scenery cast shadows along the directional light,
  with fallback to the older soft trunk/blob heuristic when footprint data is
  not usable. Directional cast-shadow reach can be tuned with
  `SPOILED_MILK_REMASTER_SHADOW_LENGTH_SCALE`; this affects cast shadows only,
  not contact shadows. Directional mask blur is now split by caster family:
  wall/strip casters use `SPOILED_MILK_REMASTER_SHADOW_MASK_BLUR_RADIUS`, while
  scenery/game-object cast shadows use
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_BLUR_RADIUS`. This lets wall shadows
  stay comparatively crisp while scenery shadows keep enough diffusion to hide
  footprint-box artifacts. Scenery/game-object cast-shadow darkness is also
  independently tunable through
  `SPOILED_MILK_REMASTER_SCENERY_SHADOW_ALPHA_SCALE`, currently defaulting to a
  slightly darker `1.3` scale after the split-blur pass made game-object
  shadows read too light.
- Scenery contact shadow refinement. The first cheap stable grounding shadow is
  active under scenery, walls, and wall objects, independent of directional
  cast-shadow length. It uses captured object footprint bounds plus a thin
  outward bleed for game objects, and line-footprint contact for walls. Game
  object corners are rounded by the contact distance function, then lightly
  diffused in a contact-only pass so grounding shadows do not read as square or
  hard-edged decals. Future refinement should replace this approximation with
  better model-derived footprints only where specific scenery still looks
  detached.
- Material-aware lighting. Teach the shader about terrain, walls, roofs,
  foliage, water, ore, scenery, sprites, projectiles, and effects as material
  families. This should come before heavy polish controls so Remaster lighting
  does not apply one generic response to every asset. The proposed first
  parity-preserving resident-world contract is now implemented and
  live-accepted in
  [renderer-material-family-foundation-plan.md](../completed-work-plans/renderer-material-family-foundation-plan.md).
  It covers static resident triangles without changing normal fragment output;
  twelve strict capture frames and a two-epoch relog/section route retained
  complete classification. Sprites, projectiles, and effects remain explicitly
  deferred.
- Emissive material metadata. Move lava/fire glow classification out of
  `mudclient` heuristics and into explicit material/object metadata. After that,
  test bloom-like glow filtering, fire/lava particles, and localized light
  contribution separately from shadow casting.
- Shadow receiver expansion. Terrain is the only accepted receiver. Receiving
  onto walls, scenery, sprites, players, or NPCs is later work after terrain
  shadows are cheaper and material ownership is cleaner.
- Deterministic terrain variation and tile-edge blending. Broad runs of
  same-color terrain can still look flat. Low-amplitude shader variation or
  optional tile-edge blending can be tested after shadow ownership is stable.

## Debug Tools Needed

- [x] Raw material baseline toggle.
- [x] Directional light vector display.
- [x] Caster outline overlay.
- [x] Receiver terrain overlay.
- [x] Indoor/outdoor classification overlay.
- [ ] Shadow mask overlay before final material application.
- [ ] Clipping boundary overlay.
- [x] Per-frame counts: casters, receivers, clipped spans, mask cache
  hits/misses.

## Open Questions

- Whether terrain ridges should cast shadows in the first terrain-shadow pass
  or wait until wall/scenery shadows are stable.
- Whether shadows should be cached by coarse light-angle buckets or rebuilt
  continuously for smooth day/night motion.
- Which scenery categories should default to casting shadows versus requiring
  explicit opt-in.
- Whether translucent/semi-transparent fences should block shadows, cast weak
  shadows, or allow shadows through.
