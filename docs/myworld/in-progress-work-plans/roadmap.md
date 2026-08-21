# Spoiled Milk Roadmap

This document tracks the high-level path from the current alpha toward beta.
Detailed implementation plans remain in their subject-specific documents; this
roadmap defines priorities, milestone boundaries, and release progression.

## Versioning

The numbered limited-alpha release sequence ended with
`v0.1.0-alpha.85`. Normal semantic-version updates began with `v0.1.1`.

Current version progression:

- The current release line is `v0.2.x`; normal updates advance the patch
  version from the latest published tag. The latest release checked during this
  audit was `v0.2.75` on 2026-08-20.
- The `v0.1.x` line has already advanced through the `v0.2.0` minor milestone.
- Patch versions are used for fixes, balancing, polish, and incremental work
  within the current milestone.
- The minor version advances when a major alpha milestone is completed.
- Major-version changes are reserved for a later project-wide release stage
  and are not part of the current alpha plan.

The project remains in alpha while the milestones below are being built.
Finishing all four major milestones creates the checkpoint for deciding
whether the project is ready to enter beta.

### Versioning Transition

The packager, updater scripts, release tests, fixtures, and artifact naming now
support the normal semantic-version release sequence. GitHub updates use normal
releases so the auto-updater can resolve the latest published tag.

## Current Phase: Post-Monster-Slayer Stabilization And Runtime Refinement

The Monster Slayer foundation, Cleric/Blessing launch, combat A01-A11
modernization, and production layered-world loader are now shipped. The
immediate phase is to stabilize those large additions, improve the remaining
renderer/world-boundary hitch behavior, and keep documentation aligned before
starting another large gameplay system.

Current priorities:

- Treat concrete regressions in the layered loader, combat, or Monster Slayer
  progression as the first priority and add focused regression coverage with
  each correction.
- Gather Monster Slayer task, point-economy, satchel-cost, and shop feedback;
  keep unique reward design deferred until that evidence exists.
- Continue renderer and boundary-transition profiling, especially CPU spikes
  and the remaining visible hitch during residency changes.
- Keep Server R2 moving as a separately gated productization effort without
  letting it displace live-game reliability work.
- Finish smaller terrain and Cosmic Altar polish after the higher-impact
  stabilization work, without reopening the accepted layered-loader design.

This phase is continuing through the `v0.2.x` patch releases. Its completion
should coincide with the next minor-version milestone.

General terrain workflow and Wilderness requirements are maintained in
[`terrain-expansion-plan.md`](terrain-expansion-plan.md).

## Major Alpha Milestones

### 1. Minor Terrain And Cosmic Altar

Complete the outstanding focused terrain changes in established areas.

#### Cosmic Altar Vision

Rework the overworld path to the Cosmic Altar near `106,3565`. The intended
space should feel isolated, empty, and ethereal. The current irregular grass
path reads as visibly painted terrain rather than a deliberate route through
empty space.

Preferred direction:

- Remove the random grass-road pattern.
- Preserve a fully traversable route while making its ordinary ground surface
  visually disappear into the surrounding emptiness.
- Add a restrained shimmer, ripple, or flowing-light effect along the route so
  players can perceive the safe path without it becoming a conventional road.
- Keep the effect low to the ground and avoid scenery that blocks movement,
  targeting, or the view of the altar.
- Make the route readable enough that players can follow it without trial and
  error, even if its edges remain indistinct.

#### Route Layout

- The existing entrance begins at `150,3541`, between two torch scenery
  objects. The entrance position and torch opening must remain unchanged.
- Beyond the torches, remove the visible grass floor along the route.
- Remove the grass beneath the Cosmic Altar and its surrounding obelisks so
  the destination occupies the same ethereal space.
- Replace the current elongated, winding route with a simple L-shaped path.
- The existing route passes the altar and turns back, bringing the player to
  the altar from the side near `104,3566`.
- The replacement should approach the opposite side and end near `108,3566`.
- Choose the route's single corner after inspecting the affected chunks. It
  should create two direct segments, shorten travel substantially, and avoid
  moving the entrance, altar, obelisks, or torches.

The traversable path may be visually invisible apart from its shimmer or
moving-pixel markers. The L shape is therefore a navigation requirement, not
only a visual preference: players should be able to recover the route by
following one straight segment and making one clear turn.

#### Visual And Technical Findings

The existing `fountain` and `fishing` visuals may serve as references, but they
are ordinary scenery models rather than reusable particle effects. Their
object-definition width and height control footprint and placement; increasing
those values does not stretch the rendered model.

A long shimmer therefore requires one of:

- A new low-profile model derived from the useful water surface.
- A client-generated strip or tile model.
- Repeated one-tile shimmer scenery pieces designed to join cleanly.

Animation is a separate concern. Existing animated scenery such as fires and
torches swaps between explicitly named model frames. The fountain and fishing
models are not registered as an animated frame set. A moving path requires
either a small model-frame sequence or a dedicated client-side animation rule.

The preferred prototype is deliberately minimal: a flat tile with a few bright
pixels or short streaks advancing in one direction across two or three frames.
This should suggest energy flowing toward the altar without water simulation,
transparency-heavy particles, or detailed models. Use one shared frame set for
all path tiles, varying starting frame or orientation so the complete path does
not pulse in lockstep.

Low-resource animation targets:

- Two or three frames total.
- A slow update rate rather than an update every rendered frame.
- One small reusable texture or flat model per frame.
- No collision, interaction option, shadow, or complex geometry.
- No per-tile server updates; animation should remain client-side.
- Sparse moving pixels with most of each tile left visually empty.

#### Prototype Order

1. Replace the grass path with visually empty but traversable terrain.
2. Test navigation, collision, camera readability, and route boundaries.
3. Place static, non-interactive one-tile shimmer models along a short test
   segment.
4. Compare fountain-water, fishing-ripple, and a purpose-built translucent or
   light-textured surface.
5. If the static version reads well, test the minimal moving-pixel animation
   before adapting a larger water effect.
6. Confirm the animation avoids synchronized flashing across the path.
7. Extend the selected treatment across the complete route only after
   movement, scene loading, and performance are stable.

#### Cosmic Altar Constraints

- The complete walkable route must connect `150,3541` to the altar approach at
  `108,3566`, with only one deliberate turn.
- An invisible path still requires matching server and client collision.
- Tiles outside the L-shaped route must remain blocked after the grass is
  removed.
- Test walking both toward and away from the altar without relying on camera
  rotation to locate the route.
- Decorative objects must use a non-blocking type and avoid right-click
  clutter.
- The route must remain visible enough under different camera angles and
  brightness settings.
- Repeated tiles must not expose obvious seams, z-fighting, or a rigid grid.
- Keep the original path chunks restorable throughout experimentation.

Supporting work:

- Resolve remaining terrain seams, elevation mismatches, collision problems,
  and malformed boundaries discovered during field testing.
- Document a repeatable landscape editing and validation workflow.
- Verify that terrain changes do not alter unrelated map chunks.

Completion gate:

- The Cosmic Altar route is visually intentional, navigable in both
  directions, collision-safe, and stable across scene reloads.
- The shimmer is readable without becoming a conventional road or adding
  interaction clutter.
- The entrance, altar, obelisks, and torches remain in their established
  positions.
- Remaining small terrain edits are either complete or explicitly deferred.

### 2. Legends' Guild Overhaul

Expand the Legends' Guild so it feels like a substantial high-level
destination comparable to the upgraded Heroes' Guild.

Initial goals:

- Increase the guild's usable space and visual identity.
- Give the guild meaningful high-level activities rather than relying mainly
  on access requirements and shops.
- Plan combat, gathering, service, reward, and travel functions around the
  guild's position in progression.
- Preserve all existing quest, shop, entrance, and travel behavior while the
  area is rebuilt.
- Avoid duplicating the Heroes' Guild room-for-room; both guilds should feel
  equally valuable but serve distinct purposes.

Details still needed:

- Desired rooms, floors, basement areas, and exterior changes.
- Which skills and services should be represented.
- Intended enemies, resources, shops, rewards, and repeatable activities.
- Any quest or reputation requirements beyond current guild access.

Completion gate:

- The guild has a coherent expanded layout, reliable navigation, meaningful
  high-level content, and a distinct role beside the Heroes' Guild.

### 3. New Enemy Roster And Area Population

Create the enemies needed to support the upgraded guilds, expanded caves, and
future Wilderness regions.

Initial goals:

- Identify gaps in combat levels, attack styles, defenses, drops, and visual
  themes.
- Add enemies that have clear locations and gameplay purposes rather than
  expanding the roster without encounter plans.
- Supply appropriate attack visuals, equipment, drops, devotion behavior,
  summon interactions, and ranged or Magic clipping rules.
- Use the new roster to populate completed areas without overcrowding or
  undermining existing training locations.
- Include encounter testing for melee, Ranged, Magic, and summons.

Details still needed:

- Enemy concepts, names, visual sources, combat bands, and factions.
- Which enemies belong in the Legends' Guild, Wilderness, caves, or other new
  areas.
- Boss or elite-enemy plans.
- Desired unique drops and their place in the economy.

Completion gate:

- The planned enemy families are implemented, visually complete, balanced,
  assigned to appropriate locations, and covered by combat and item tests.

### 4. Northern Wilderness Expansion

Extend the Wilderness east across the northern map toward the Tree Gnome
Stronghold.

Release approach:

- Release the expansion in waves rather than waiting for the entire northern
  Wilderness to be complete.
- Define each wave using RuneScape's established map grid and chunk boundaries.
- Select a manageable group of neighboring grid sections for each wave.
- Match every section to its existing neighboring landscape while retaining a
  coherent plan for the complete expansion.
- Treat each published wave as finished terrain, not a disconnected preview
  that depends on a later section to repair its edges or boundaries.
- Advance the minor version when a substantial Wilderness wave completes its
  release gate.

Primary goals:

- Build several recognizable Wilderness regions rather than one uniform open
  field.
- Make every released section useful, with resources to gather, enemies to
  fight, and reasons to explore beyond crossing the terrain.
- Add controlled routes, believable boundaries, landmarks, gathering areas,
  ruins, caves, and encounter spaces.
- Preserve Sinclair Mansion in place and build safely around it.
- Identify and correctly relocate or preserve the remaining special-purpose
  northern enclave.
- Prevent alternate entry into the Tree Gnome Stronghold or other restricted
  areas.
- Verify all coordinate-derived Wilderness behavior across the expansion.

Grid-section workflow:

1. Audit existing northern chunks, boundaries, restricted regions, and
   special-purpose areas.
2. Map the complete proposed expansion onto the established RuneScape grid.
3. Record the terrain, elevation, wall, vegetation, water, and route language
   on every existing edge neighboring the selected wave.
4. Design the selected sections as part of the full Wilderness plan while
   keeping the current release boundary complete and believable.
5. Establish terrain, elevation, walls, collision, and safe boundaries.
6. Field-test traversal and revise the broad terrain pass.
7. Add landmarks, entrances, resources, gathering activities, and scenery.
8. Populate approved sections with suitable enemies.
9. Validate Wilderness levels, PvP rules, teleports, death behavior, quests,
   ranged and Magic clipping, and map escape protection.
10. Publish the wave only after all exposed edges safely meet existing terrain
    or a deliberate impassable boundary.

World-protection requirements:

- Existing terrain is the visual and structural authority for each adjoining
  edge.
- New terrain must not open routes into quest areas, guilds, minigames,
  interiors, or other regions whose access is normally gated.
- Audit gates, fences, cliffs, water, dense forest, walls, elevation changes,
  portals, ladders, teleports, and coordinate-triggered entrances around every
  wave.
- Test the perimeter from both sides where possible; a boundary that blocks
  entry from the Wilderness may still permit escape from the protected area.
- Do not rely solely on invisible collision where a believable landscape
  barrier can preserve the established world structure.
- Neighboring chunks outside the intended wave should remain unchanged unless
  an edge transition explicitly requires a reviewed adjustment.

Content standard:

- Every wave should include a deliberate mix of combat, gathering, discovery,
  and travel value appropriate to its size.
- Resource placement should fit the region and its risk rather than existing
  only to fill empty space.
- Enemy placement should create recognizable encounter areas and routes while
  leaving enough room for traversal, gathering, and multiple combat styles.
- New rewards and resources must be checked against existing progression and
  the economy before they become reasons to bypass established content.
- A wave may reuse suitable existing enemies, variants, and visual assets when
  original enemy art is not ready, but placeholder encounters should still be
  coherent and balanced.

New enemy art:

- Original enemies remain a desired part of the Wilderness expansion.
- Their sprites need to match the low-resolution, chunky, early paint-program
  style of RuneScape Classic rather than modern pixel art or smoothly rendered
  AI output.
- AI-generated enemy sprites should not be accepted merely because they are
  technically low resolution; silhouette, palette, frame consistency, and the
  established visual language matter.
- Commissioned art, adapted compatible assets, manual sprite work, and other
  sourcing options should be explored and documented with their permissions.
- Terrain waves must not be blocked indefinitely by original sprite
  production. New enemy families can enter the release schedule when suitable
  art is available, with later waves or population revisions providing natural
  integration points.

Completion gate:

- Every released grid section is navigable, populated, visually coherent, and
  validated.
- Section edges match both neighboring terrain and the larger expansion plan.
- Established regions cannot be entered by unintended routes.
- Sinclair Mansion and all other northern enclaves remain functional.
- Each wave is ready for the normal hosted world rather than only the isolated
  terrain test environment.
- The complete milestone closes after all planned waves join into one coherent
  northern Wilderness.

## Beta Readiness Checkpoint

After all four major alpha milestones are complete, evaluate the project for a
beta transition.

Beta readiness should require:

- No known item-definition or player-data integrity failures.
- Reliable server and client launch and update behavior.
- Stable banking, inventory, death, travel, combat, and persistence systems.
- Completed validation of the new terrain and its boundaries.
- New enemies and rewards integrated without major balance or economy
  regressions.
- Core content usable without development commands or manual correction.
- Remaining work dominated by tuning, polish, presentation, and isolated bugs
  rather than unfinished foundational systems or empty planned regions.

The exact beta version number and prerelease naming should be chosen when this
checkpoint is reached rather than fixed prematurely.

## Roadmap Maintenance

- Add detail here when it changes milestone scope or order.
- Put implementation-level coordinates, formulas, item IDs, encounter tables,
  and technical constraints in the relevant subject plan.
- Mark completed milestone sections with the release that completed them.
- Record newly discovered work without silently expanding a milestone; decide
  whether it is required for the gate or belongs in a later release.
