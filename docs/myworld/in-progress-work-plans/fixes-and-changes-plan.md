# Fixes And Changes Backlog Plan

Status: ready for manager review; Tasks 1 through 6 implemented and guarded,
with Lumbridge banking explicitly deferred.
Owner: An-actual-duck, with independently checkpointed tasks coordinated from this plan.
Implementation branch: `feat/player-experience-backlog`.

## Summary

This plan preserves and coordinates the current "Fixes and Changes" backlog:

- [x] Default camera mode should be Manual.
- [x] Correct indoor/upper-floor roof hiding when roofs are enabled.
- [x] Explore adjustable terrain ambient occlusion/shading.
- [x] Explore adjustable object shading.
- [x] Add optional text-list layouts for Magic, Prayer, and Summoning.
- [ ] Add a Lumbridge bank or bank chest.

The list crosses client defaults, account persistence, renderer ownership,
interface layout, and server world content. It must not be implemented as one
code commit. This document is the coordinating ledger; the manager-approved
umbrella branch keeps each task in its own tested and pushed checkpoint before
the next task begins.

## Current-System Findings

### Camera default

- `mudclient.optionCameraModeAuto` currently initializes to `true` before the
  server sends the account setting.
- Camera mode is account-persisted as game-setting index `0` and the client
  already sends explicit Auto/Manual changes over packet `111`.
- New accounts currently inherit `cameraauto DEFAULT 1` in the MySQL and SQLite
  schema templates and in `server/inc/sqlite/myworld_seed.db`.
- `MySqlQueries.createPlayer` omits `cameraauto`, so both MySQL and the SQLite
  subclass depend on that database default.
- Existing players' saved choices should not be migrated or overwritten. The
  requested change is a new-account and pre-sync default, not removal of Auto.

### Roof visibility

- Global `Config.C_HIDE_ROOFS` currently controls whether roof geometry is
  included in cached world products at all. Toggling it performs a region
  reload through `reloadCurrentRegionForRoofVisibility()`.
- With roofs enabled, the legacy frame path removes current roof grids every
  frame, then re-adds them only when `lastHeightOffset == 0` and the player's
  collision tile does not contain bit `0x80`. This makes upper-floor behavior
  depend on a ground-plane-only condition.
- On the ground plane, the same loop also removes/re-adds plane 1 and 2 wall
  and roof grids as a group. It has no explicit, named contract for outdoor,
  indoor, upstairs, or roof-above-player states.
- The resident OpenGL paths primarily filter roofs by the global
  `C_HIDE_ROOFS` setting. A fix must keep legacy scene ownership, resident
  chunks, world-product cache keys, roof shadow coverage, and toggle/relog
  lifecycle in agreement.

### Terrain and object shading

- Remaster shading currently combines directional diffuse,
  `remasterClassicShadeFactor`, and `remasterLocalReliefFactor` in the resident
  shader. Terrain and non-terrain geometry use different fixed curves, but
  share one runtime-only `RendererReliefSettings` strength.
- The accepted terrain shadow mask also contains directional cast shadows and
  scenery contact shadows. The contact channel is described as AO-like
  grounding, while interior terrain receivers are suppressed.
- Therefore "terrain ambient occlusion/shading" could refer to at least two
  different controls: local terrain relief/diffuse contrast or terrain shadow
  mask/contact strength. Those channels must be measured separately before a
  player-facing label is chosen.
- Material-family metadata is now available, but model kind remains the safer
  first ownership boundary for terrain versus scenery. Exploration must not
  silently change Classic or normal Remaster defaults.

### Magic, Prayer, and Summoning layouts

- All three tabs are rendered and clicked inside the large
  `mudclient.drawUiTabMagic()` path.
- The current presentation is a four-column, two-visible-row icon grid with
  separate scroll-row state and click arithmetic for Magic, Prayer, and
  Summoning.
- Magic filters hidden spells and owns selection, autocast, self-cast, and
  inventory-target behavior. Prayer owns active/allocation checks and the
  current prayer-book ordering. Summoning sends an interface option by summon
  index. A text layout must call these same actions rather than create parallel
  gameplay rules.
- The option is purely presentational and should be stored in client settings,
  not consume another server game-setting/protocol index. Icon layout remains
  the default until the text mode is manually selected.
- Authentic/custom panel placement, Android's last-spell box, mouse wheel,
  scrollbar bounds, hover descriptions, disabled colors, and selected states
  all need explicit coverage.

### Lumbridge banking

- The reusable `Bank Chest` scenery is `SceneryId.BANK_CHEST` (`942`).
- The authentic Shantay Pass plugin currently claims every scenery object with
  id `942`, applies a members-world gate before coordinate-specific behavior,
  and only special-cases Shantay Pass and McGrubor's Wood. Placing another
  chest without changing ownership would make Lumbridge banking depend on an
  unrelated desert plugin.
- Standard banker NPCs refuse bank access unless their location is recognized
  by `Point.isInBank(...)`; adding a banker alone therefore also requires bank
  area data and PvP/safety review.
- A focused MyWorld bank-chest handler is the lower-content-cost direction,
  provided trigger ownership is narrowed and Ultimate Ironman plus bank-pin
  rules remain intact.
- Final placement needs an in-game map check. Proximity to the new-player spawn,
  castle routes, respawn/death flow, doors, quests, and existing scenery can
  materially change early-game convenience.

## Dependency And Risk Map

| Work item | Depends on | Primary risks |
| --- | --- | --- |
| Manual camera default | Account creation query and schema defaults | Overwriting existing choices; client/server disagreement before settings sync; retro-schema drift |
| Roof visibility | Plane/collision semantics, world-product cache, resident roof filtering | Entire floors disappearing; roofs leaking indoors; stale chunks after toggle/relog; shadow coverage disagreement |
| Shading exploration | Accepted roof behavior, renderer diagnostics, existing relief/shadow-mask channels | Mislabeling shadows as AO; double-darkening terrain; changing defaults; shader/cache churn |
| Terrain shading control | Exploration contract and visual route | Crushed dark terrain, noisy slopes, indoor mismatch, Classic contamination |
| Object shading control | Exploration contract, model-kind/material ownership | Flat or black two-sided scenery, foliage/ore over-tuning, sprite confusion |
| Text-list layouts | Shared tab action/index mapping and client-setting persistence | Wrong spell/prayer/summon activation; scroll/click mismatch; mobile/custom UI regressions |
| Lumbridge bank chest | Approved placement and chest trigger ownership | Bypassed UIM/pin rules; unwanted members restriction; spawn/economy distortion; scenery overlap |

## Focused Implementation Tasks And Order

### 1. Default new players to Manual camera

Recommended branch: `fix/default-manual-camera`

This is first because it is small, isolated, easy to guard, and establishes a
clean settings/default change without entangling renderer behavior. The full
implementation-ready specification is below.

### 2. Correct enabled-roof indoor and upper-floor visibility

Checkpoint scope: roof visibility only.

Define a named roof-visibility state from player plane and roof/collision
coverage, then apply the same decision to legacy scene grids and resident
OpenGL draw ownership. Do this before shading so indoor and upper-floor visual
tests have trustworthy geometry.

The first checkpoint should be diagnostic/test-only if the intended visibility
matrix cannot be proven from the current conditions. Required test states:

- ground-floor outdoor;
- ground-floor under a roof;
- first/second upper floors, both covered and uncovered;
- stairs/plane transitions;
- global Hide Roofs on/off;
- logout/relogin and section transition;
- Classic and Remaster renderer profiles.

#### Implementation record — 2026-07-11

- Added named per-frame states shared by both renderers. The original
  implementation preserved the legacy rule that hid every roof while the
  player was above the ground plane.
- The legacy scene loop and resident OpenGL chunks now consume the same state.
  This closes the split where projected geometry hid roofs indoors/upstairs
  but resident chunks filtered only from the global saved option.
- Global Hide Roofs still selects no-roof cached world products and reloads
  the region. Resident filtering additionally suppresses upper-plane wall
  batches from a ground-floor view, matching the legacy scene-grid behavior,
  without suppressing active-floor walls upstairs.
- `Ctrl+F9` capture metadata now records `activePlane`, `roofVisibility`, and
  `roofsVisible`, making a visual report directly correlatable with the
  resolved technical state.
- The focused executable matrix covers outdoor ground, covered ground, upper
  floors, global-option precedence, active-floor walls, upper-plane walls,
  roofs, and terrain. The full renderer guard suite and Java 8 client build
  pass.
- A live follow-up exposed a separate region-basis bug when the setting was
  toggled inside the eight-tile movement hysteresis band at a section edge.
  The toggle selected a neighboring 3x3 world window from the player's tile
  without rebasing collision, entities, scenery, or the camera, producing an
  exact 48-tile visual/picking shift. Roof reloads now rebuild the already
  active window from `midRegionBaseX/Z` and leave its bounds unchanged.

#### Upper-story correction — 2026-07-30

- Authentic and custom landscape fixtures both contain upper-plane roofs at
  Lumbridge Castle, Varrock, Falador, and Draynor Manor. The missing visual was
  therefore not map-data loss.
- The first visibility-only prototype exposed a second, independent regression:
  upper-plane geometry was submitted but appeared at first-story height. The
  world-product refactor had changed wall vertices from the shared completed
  elevation cache to each plane's raw terrain elevation, and independently
  initialized each roof workspace the same way. That discarded the authentic
  cumulative floor-to-floor construction used before the refactor.
- Floor-local and stacked-upper model products now have separate cache
  identities. A floor-local product still starts from its own terrain when the
  player is on that plane. A stacked product viewed from ground level builds
  both walls and roofs from the completed roof elevations of the floor below;
  plane 2 recursively continues from the stacked plane-1 result.
- The shared geometry products feed both classic scene grids and renderer-v2
  chunks, so the stacking correction does not introduce renderer-specific
  offsets or duplicated models.
- The visibility state is now `VISIBLE_ON_ACTIVE_FLOOR` for an uncovered player
  on plane 1 or 2. That player sees the active plane's roof, while roofs from
  other planes and walls above the active plane remain hidden.
- Covered tiles still resolve to `HIDDEN_INDOORS` on every plane, and the
  global Hide Roofs setting still takes precedence.
  `events.jsonl` records each successful `roof.visibility.reload` with active
  section, player world tile, and player-to-active section deltas so future
  reports remain attributable even when toggled while moving.
- Automated coverage distinguishes the cumulative second/third-story heights
  from floor-local height, requires both wall and roof builders to consume the
  lower-floor result, and verifies separate product-cache identities alongside
  the existing visibility and map-fixture matrix. Private visual validation is
  still required after the corrected model build.
- This task deliberately preserves the legacy whole-grid visibility unit. A
  connected-roof-volume refinement would require a separate spatial ownership
  design and is not necessary to correct renderer parity.

### 3. Add optional text-list spellbook layouts

Checkpoint scope: spellbook presentation only.

Add one persisted client-side `Icons / Text` presentation setting and a shared
row model for the three tabs. Reuse the current action methods and canonical
indices. Keep icon mode as default. This is one cohesive UI feature, but it
must not be bundled with roof, shading, or content changes.

#### Implementation record — 2026-07-11

- Added one local `spellbook_layout` preference with `Icons` as the default and
  `Text` as the optional mode. It is stored in `clientSettings.conf`; no server
  game-setting or protocol index was added.
- Added the preference to the General settings Interface section. One choice
  controls Magic, Prayer, and Summoning consistently, as approved in this
  plan.
- Text mode uses the existing 196x90 scrolling panel in authentic and custom UI
  placement. Each tab retains its own text scroll position, mouse-wheel and
  scrollbar behavior, hover description, and tooltip/cost area.
- Magic text rows preserve hidden-spell filtering, canonical spell indices,
  level/rune colors, selected state, and autocast state. Both layouts call the
  same validation and spell-selection/autocast action.
- Prayer text rows preserve prayer-book order, active and unavailable colors,
  current allocation-point rules, and the same activate/deactivate packets as
  icon mode. Summoning rows preserve canonical summon indices and call the
  existing interface action. The Android last-spell box remains available in
  either layout.
- The focused executable settings fixture verifies default, cycle, save/load,
  compatibility alias, and fallback behavior. Source guards verify all three
  row builders, shared actions, canonical Magic mapping, per-tab scrolling,
  persistence, and the settings control. Prayer UI guards and the Java 8 client
  build pass.
- Owner review found all three text layouts functionally correct. The shared
  Prayer point summary baseline was moved down four pixels so its glyphs clear
  the divider above it in both icon and text presentation.

### 4. Separate terrain and object shading diagnostics

Checkpoint scope: diagnostic-only shading ownership.

First add runtime/diagnostic-only terrain and object strength ownership with
parity defaults. Attribute whether the requested terrain control belongs to
local relief, diffuse response, shadow-mask directional strength, or contact
strength. Record the chosen terms in captures/F6 before exposing a normal
setting.

#### Implementation record — 2026-07-11

- Split the old shared runtime relief override into independent terrain and
  non-terrain/object scopes. Both retain the accepted `Max`/`2.0` default, and
  the legacy `SPOILED_MILK_OPENGL_RELIEF` override remains a compatibility
  fallback that drives both scopes when no scoped override is supplied.
- Added diagnostic overrides
  `SPOILED_MILK_OPENGL_TERRAIN_RELIEF` and
  `SPOILED_MILK_OPENGL_OBJECT_RELIEF`. The resident shader selects between the
  two from model kind; the object scope currently means all non-terrain static
  geometry, including walls, roofs, scenery, and wall objects.
- Identified the existing channels precisely: local relief reshapes captured
  legacy base light; directional diffuse uses fixed model-kind curves; the
  terrain-only shadow mask combines directional wall/scenery projection with
  a contact channel; non-terrain objects do not currently receive that shadow
  mask.
- Added `SPOILED_MILK_REMASTER_DIRECTIONAL_SHADOW_ALPHA_SCALE` as a
  diagnostic-only terrain shadow comparison knob with a parity default of
  `1.0`. The existing contact-alpha runtime override remains the contact
  comparison knob. Both participate in mask cache signatures.
- F6 now reports terrain/object relief modes and the directional/contact shadow
  tuning. `Ctrl+F9` metadata records stable individual shading fields,
  including the fixed diffuse response and the absence of object shadow-mask
  receiving, so owner observations can be attributed to one channel.
- Added live ten-step comparison controls: `F7` cycles terrain relief,
  `Shift+F7` cycles object relief, `F8` cycles world dimness, and `Shift+F8`
  cycles world contrast. Owner review expanded relief to `0.0..4.5` in `0.5`
  steps, placing the accepted `2.0` value at level 5. Dimness retains
  `1.0..0.55`. Contrast level 1 now starts at the previously tested level-5
  value (`1.2`) and advances in doubled `0.1` increments through `2.1`.
- Live review found the resident shader still capped relief input at `2.5`,
  flattening the expanded scale above its former maximum. The shader ceiling
  now matches the slider's `4.5` endpoint; final output remains conservatively
  clamped to prevent black or blown-out geometry.
- Added four two-line Graphics-menu controls so labels retain their full width
  and each ten-segment scale gets an uncluttered row. The bars use the requested
  `- [----o-----] + [5]` presentation and support minus, plus, direct track,
  and drag selection; hotkeys remain available for rapid comparisons.
- The color controls compose with the selected graphics profile. Brightness is
  now applied after either Classic or Remaster resident shading instead of
  only inside the Remaster-lighting branch. Dimness remains neutral at level 1;
  contrast uses the owner-selected `1.2` comparison floor while this diagnostic
  range is evaluated under Classic and Remaster ownership.
- Owner review promoted the four controls from session comparisons to persisted
  player settings. Terrain/object relief default to level 5, Dimness to level
  1, and Contrast to level 1. Relief now applies after Classic or Remaster base
  shading. The redundant player-facing Brightness row and saved-setting load
  were removed so hidden legacy brightness cannot compound with Dimness.
- Player-facing labels use `Terrain shading` and `Object shading`; internal
  renderer diagnostics retain the more precise local-relief terminology.
- Each hotkey reports its level/value in game, emits a structured
  `renderer.tuning.change` event, appears in F6, and is recorded by
  `Ctrl+F9`. Expanded F6 was condensed to the high-value timing, chunk,
  material, sync, capacity, and tuning summaries so it remains on-screen;
  detailed phases and per-channel shadow constants remain in structured logs
  and captures.
- The executable diagnostic fixture verifies parity defaults, legacy shared
  override compatibility, and independent scoped overrides. The full renderer
  guard suite and Java 8 client build pass without changing default visuals.

#### Iterative range-tuning record — 2026-07-11

- Owner review reopened the four Graphics controls for a wider visual tuning
  pass. The first candidate extends each persisted scale from 10 to 20 while
  preserving existing saved levels, all level `1..10` mappings, and defaults
  (`5/5/1/1`).
- The slider now derives segment and plus-button hitboxes from the glyphs it
  actually draws instead of assuming `o` and `-` have identical widths. This
  makes both endpoints directly selectable and keeps plus/minus and dragging
  aligned with the visible control.
- Terrain/object shading retain the accepted linear response through level 10.
  Levels 11–20 continue from that exact endpoint with a bounded exponential
  tail, avoiding the old final-factor clamp that would otherwise turn the new
  steps into duplicate output. Dimness preserves `1.0..0.55` through level 10
  and then tapers toward dark without reaching black; Contrast continues its
  existing `0.1` increments through `3.1`.
- This candidate remains pending private-client visual acceptance. Developer
  F7/F8 comparison keys remain available in worktree builds; the release-build
  guard continues to suppress every non-F6 function key.
- Owner review accepted the expanded shading response and requested bipolar
  color controls. The second candidate keeps all four sliders at 20 positions
  and places the old color level-1 baseline at level 10. Brightness/dimness spans
  `1.5..1.0..0.192`; contrast spans `0.3..1.2..3.1`.
- Centered color saves carry `opengl_color_tuning_scale=centered-20-v2`.
  Unversioned 1–10 settings and the first 1–20 experimental scale migrate to
  the closest matching multiplier, so the old default loads at the new center
  without changing its output.
- The transient `centered-21-v1` test format also migrates by multiplier, so
  the tested level-11 baseline becomes level 10 with identical output.
- Owner clarification established that visual parity—not merely matching knob
  positions—is the level-10 contract. Terrain/object level 10 is therefore
  recalibrated to the original `2.0` default instead of the overly dark `4.5`
  test value. Levels 1–9 provide progressively lighter shading; levels 11–20
  retain the accepted expanded response through `9.5`.
- Relief saves now carry
  `opengl_relief_tuning_scale=centered-default-20-v1`. Older levels migrate by
  their effective strength, including old default level 5 becoming new level
  10 with unchanged `2.0` output. The worktree visual-test settings are reset
  to `10/10/10/10` for default-experience review.
- Owner visual tuning selected `18/18/14/7` as the Classic profile bundle
  (terrain shading, object shading, brightness/dimness, contrast). Selecting
  Classic now persists those values; selecting Remaster restores the intended
  `10/10/10/10` baseline. Subsequent manual edits still mark the profile
  Custom.
- Gamma and Saturation complete the accepted iterative color-control set. Both use the same
  persisted 20-position slider/input contract with neutral level 10 defaults.
  Gamma spans `0.5..1.0..1.5`; Saturation spans `0.0..1.0..2.0`. The resident
  and projected shaders apply them after tone/contrast and before fog, and F6,
  structured tuning events, and `Ctrl+F9` captures record their levels and
  effective values. F10 and Shift+F10 remain worktree-only comparison keys.
- Owner live review accepted both controls at neutral level 10 defaults and
  selected this milestone as the renderer-shading stopping point. The focused
  guards, complete renderer guard suite, Java 8 build, and runtime compilation
  of both projected and resident shaders pass; the branch is ready for manager
  handoff.

### 5. Promote an accepted terrain shading/AO control

#### Implementation record — 2026-07-11

- Promoted terrain shading to a persisted ten-step Graphics control with the
  owner-approved two-line `- [----o-----] + [5]` layout, direct track/drag
  selection, plus/minus controls, and retained F7 comparison shortcut.
- The accepted pre-control strength `2.0` maps to default level 5. The full
  range is `0.0..4.5` in `0.5` steps and its shader ceiling matches the exposed
  endpoint.
- Terrain shading applies after either Classic or Remaster base shading.
  Captures retain the precise `terrainRelief` level/strength terminology for
  technical attribution.

### 6. Promote an accepted object shading control

#### Implementation record — 2026-07-11

- Promoted object shading as a separately persisted ten-step control using the
  same slider/input contract and a level-5 (`2.0`) continuity default.
- Model kind remains the ownership boundary: object shading covers non-terrain
  resident world geometry, including walls, roofs, scenery, and wall objects.
  It now applies after Classic or Remaster base shading; material-family
  specialization remains a possible later refinement rather than an inferred
  release rule.
- Persisted Dimness and Contrast were promoted with the same control contract.
  Dimness defaults neutral at level 1; Contrast uses the owner-selected `1.2`
  level-1 floor. The redundant player-facing Brightness row and legacy saved
  Brightness load were retired.

### 7. Add a Lumbridge bank chest

Recommended branch: `content/lumbridge-bank-chest`

Prefer a bank chest over a banker unless map review shows a real bank room is
appropriate. Approve the exact tile before implementation. Narrow the existing
Shantay chest trigger to its owned coordinates or introduce explicit shared
chest routing, then add the Lumbridge placement and a MyWorld-owned handler.
Preserve bank-pin validation, Ultimate Ironman denial, and normal bank close /
movement behavior.

## Task 1 Implementation-Ready Specification

### Goal

New players and the client before account-setting synchronization start in
Manual camera mode. Auto remains selectable and existing saved Auto/Manual
values continue to load unchanged.

### Intended changes

- Change `mudclient.optionCameraModeAuto`'s initialization from `true` to
  `false`.
- Make the shared account-creation SQL explicitly insert `cameraauto = 0` so
  both MySQL and the SQLite subclass get the new behavior even on an existing
  deployed schema whose historical column default is still `1`.
- Change the MySQL core/retro and SQLite core/retro schema-template defaults to
  `0` for new databases.
- Update the empty canonical `myworld_seed.db` schema default reproducibly;
  preserve every other table, index, trigger, and row.
- Replace or supersede the historical `correct_cameraauto_default.sql` intent
  with a forward migration/default statement for fresh or externally managed
  MySQL deployments. Do not update existing player values.
- Add `tests/myworld/test-default-manual-camera.py` and include it in the
  appropriate small client/server guard suite.

### Guard assertions

- Client fallback is Manual.
- Account-creation SQL names `cameraauto` explicitly and supplies `0`.
- Every text schema template declares `cameraauto DEFAULT 0`.
- The canonical seed's `PRAGMA table_info(players)` reports default `0`.
- The setting packet and save/load paths still use game-setting index `0` and
  still accept both values.
- No SQL statement performs a bulk `UPDATE players SET cameraauto = 0`.

### Validation

- Run the focused camera-default guard.
- Run `./scripts/build-client.sh`.
- Run `./scripts/build-server.sh`.
- Create a disposable new account and confirm Settings shows `Camera mode -
  Manual` on first login.
- Toggle to Auto, logout/login, and confirm Auto persists.
- Toggle back to Manual, logout/login, and confirm Manual persists.
- Confirm an existing account saved as Auto is not changed by deployment.

### Completion criteria

- New accounts reliably start Manual on SQLite and MySQL paths.
- Existing account preferences are preserved.
- Auto camera remains functional and persistent.
- The task is committed and handed back independently of every other item in
  this plan.

### Implementation record — 2026-07-11

- Client pre-sync camera state now initializes to Manual.
- Shared account creation explicitly writes `cameraauto = 0`, covering both
  MySQL and the SQLite subclass without depending on a deployed column default.
- MySQL/SQLite core and retro schema templates plus the empty canonical MyWorld
  seed now declare default `0`. The seed table rebuild preserved all other
  schema objects and passed SQLite integrity validation.
- Existing player rows are not migrated. The unchanged account load/save and
  packet-index-`0` paths continue to preserve explicit Auto and Manual choices.
- `test-default-manual-camera.py` verifies client fallback, creation SQL,
  schema/seed defaults, a materialized fresh seed account, and the absence of
  preference-overwriting SQL. Client and server Java 8 builds pass alongside
  the focused camera and player-data guards.

## Documentation Workflow

- Keep the six owner requests verbatim at the top until each is completed.
- Each implementation task updates its section and relevant source plan, then
  checkpoints independently on the manager-approved umbrella branch.
- Keep completed task detail here until the overall backlog receives its final
  roundup and handoff.

## Open Decisions

- Roof behavior is resolved for this task: retain the legacy whole-grid unit
  while making its state explicit and consistent across renderers. Connected
  roof-volume hiding remains a possible later enhancement, not a parity fix.
- Shading controls remain in active visual refinement as four persisted
  20-position sliders, with the bipolar color baseline at level 10.
  Player-facing terminology is `Terrain shading` / `Object shading`; captures
  retain local-relief terminology. Terrain contact-shadow strength remains a
  separate technical channel and is not mislabeled as this control.
- Text layout is resolved as one shared Icons/Text preference for all three
  tabs. Per-tab modes would add preference and interaction complexity without
  changing any gameplay action, so they are outside this task.
- Lumbridge bank: approve a precise chest tile after an in-game map check and
  decide whether the area should merely provide bank access or become a formal
  protected bank zone. This content item is deferred from the current handoff
  and remains preserved for a later focused branch.

## Backlog Completion Criteria

- Every checked request has an independently reviewable implementation commit.
- Defaults and saved settings migrate without overwriting player intent.
- Roof and shading work passes Classic/Remaster, indoor/outdoor, plane,
  relog, and section-transition tests.
- Text mode preserves all actions and icon mode remains available.
- Lumbridge banking preserves pin/UIM rules and has an approved, non-overlapping
  location.
- Documentation records final decisions, tests, risks, and deferred follow-up.
