# Code Cleanup And Modularization Plan

Plan status: **paused after B01-B11, the five immediate `mudclient` ownership
branches, the post-sequence client responsibility reassessment, and the
privately verified `PredictiveTerrainPreloader` extraction were integrated on
2026-07-16**. The preloader integration is `main` merge `2785f1a6f`; focused
branch measurements are labeled separately below. Resume structural work only
when feature pressure exposes a specific ownership boundary or a fresh review
justifies one.

Current size/churn measurements and the code-navigation recommendation are in
[`code-size-and-indexing-audit-2026-08-13.md`](code-size-and-indexing-audit-2026-08-13.md).
That audit supersedes this plan's historical line-count prioritization, while
this document retains the completed extraction contracts and stop conditions.

This is the AI-facing cleanup roadmap for Spoiled Milk code structure. It is
not a feature plan. Its job is to keep future work from getting lost inside
large inherited files, half-retired options, and renderer bridge code that was
useful during migration but should not remain the long-term shape of the
engine.

Use this file when a task touches renderer internals, graphics settings,
presentation, asset loading, telemetry, entity definitions, or other large
systems. The goal is not cleanup for its own sake. The goal is to make large
renderer swings easier, safer, and less likely to accidentally revive old
software-renderer assumptions.

For the repository-level folder/package direction and safe worktree workflow,
use
[`project-structure-refactor-plan.md`](project-structure-refactor-plan.md)
before moving files.

## Cleanup Rule

This project is in alpha and large under-the-hood changes are acceptable.
Cleanup should favor structural moves that unlock future renderer work:

- Break large files along real ownership boundaries.
- Prefer moving code into focused classes over adding more helper methods to
  already oversized files.
- Keep behavior stable during pure extraction passes.
- Remove or quarantine retired settings so future AI sessions do not mistake
  them for active player-facing systems.
- Keep compatibility shims only when a current launcher, saved setting, server
  config, or fallback path still needs them.
- Do not spend many iterations polishing one small subsystem if a larger
  ownership problem is blocking renderer modernization.

## Current Hotspots

Line counts measured with `wc -l` on 2026-07-16 after the completed B01-B11
code-health sequence, five merged client ownership branches, and the focused
`PredictiveTerrainPreloader` extraction. The published-main `mudclient`
baseline before that extraction was 26,255 lines.

| File | Lines | Why It Matters |
| --- | ---: | --- |
| `Client_Base/src/orsc/mudclient.java` | 26,171 | Owns gameplay UI/state plus external-asset content coordination, scene/collision orchestration, movement interpolation, combat effects, projectiles, and renderer integration. The completed ownership sequence also moved predictive terrain cache-warming decisions and throttle state while preserving the public and private compatibility facades. |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | 9,629 | B09 moved registry storage/access, prayer-book authorship, and fallback diagnostics to focused owners. This facade still contains authored definitions, MyWorld overrides, generated families, and load order. |
| `server/src/com/openrsc/server/model/entity/player/Player.java` | 5,931 | Central server entity state; likely too broad for continued gameplay feature growth. |
| `Client_Base/src/orsc/graphics/two/GraphicsController.java` | 4,346 | Legacy 2D drawing plus OpenGL capture/replay instrumentation, sprite scaling, text plotting, and sprite archive loading. |
| `Client_Base/src/orsc/graphics/three/World.java` | 4,264 | Client terrain, wall, roof, minimap, collision, sector loading, CPU product caching, and renderer chunk export are mixed. |
| `PC_Client/src/orsc/OpenGLFramePresenter.java` | 4,068 | B07 removed direct GLFW/window lifecycle and viewport policy. The presenter still owns GL resources, frame/pass orchestration, projection setup, texture upload, sprite/world composite decisions, capture coordination, and input forwarding. |
| `Client_Base/src/orsc/graphics/three/Scene.java` | 3,789 | Legacy scene sorting/raster flow and renderer-v2 frame export live together. |
| `Client_Base/src/orsc/PacketHandler.java` | 3,507 | B08 moved movement-snapshot diagnostics and scene-baseline state out. Opcode reads, decode order, direct client mutation, configuration, and UI reactions remain. |
| `server/src/com/openrsc/server/model/container/Equipment.java` | 3,341 | B10 moved pure slot/stat decisions out. Mutable container behavior, packet/stat ordering, appearance, charges/durability, and item-effect hooks remain. |
| `Client_Base/src/orsc/RenderTelemetry.java` | 3,154 | Many unrelated counters and reports in one static sink. Useful, but hard to extend safely. |
| `server/src/com/openrsc/server/net/rsc/ActionSender.java` | 2,711 | Outgoing protocol building and gameplay-specific sender helpers are mixed. |
| `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` | 2,418 | B10 moved pure classification and validation. Packet/player mutation, rune and equipment checks, targets, combat, teleports, AoE, scheduling, and finalization remain. |
| `server/src/com/openrsc/server/content/Summoning.java` | 1,872 | All Summoning profiles, runtime behavior, utility commands, combat, and scaling are in one utility class. |
| `server/src/com/openrsc/server/content/EnchantingItemEffects.java` | 1,854 | Jewelry, robe, staff, altar, charge, and tier-effect logic are accumulating in one class. |

Line count remains a prioritization clue, not an exit criterion. B07 proved
that a file below 5,000 lines can still own too many high-risk responsibilities.
Ownership, dependencies, and testability now decide whether an extraction is
complete.

## B01-B11 Reconciliation

The code-health sequence is complete and must not be left looking like future
work in this plan:

| Work | Status | Durable boundary/result |
| --- | --- | --- |
| B01 spell display metadata | Complete | One stable client elemental/dual-element metadata source serves `mudclient` and `SkillGuideInterface`; server damage authority is unchanged. |
| B02 desktop sound lifecycle | Complete | Desktop cleanup is non-throwing, idempotent, and backed by tracked active clips. |
| B03 server build authority | Complete | Bundled Ant and the operational scripts are the production source of truth; Gradle remains secondary until parity is proved. |
| B04 static-analysis baseline | Complete | Changed-code gates and reproducible javac, Checkstyle, PMD, SpotBugs, ShellCheck, and Ruff analysis exist for the maintained products. |
| B05 swallowed server failures | Complete | Social/offline-message database failures and best-effort plugin cleanup have bounded, redacted diagnostics. |
| B06 auxiliary client types | Complete | Twenty-six package-private auxiliary types moved to correctly named owners; the auxiliary-class warning family is zero. |
| B07 viewport/window lifecycle | Complete | `OpenGLViewportPresenter` and `OpenGLWindowController` own viewport policy and GLFW lifecycle; OpenGL and software fallback were privately verified. |
| B08 packet diagnostics | Complete | `MovementSnapshotDiagnostics` and `SceneBaselineState` own their state; packet reads and mutation order remain in `PacketHandler`. |
| B09 definition registry | Complete | `ClientDefinitionRegistry`, `PrayerBookDefinitions`, and `ClientDefinitionFallbackDiagnostics` own registry/access, prayer authorship, and bounded fallback reporting. |
| B10 equipment/spell boundaries | Complete | `EquipmentSlotRules`, `EquipmentStatCalculator`, `SpellClassification`, and `SpellValidationRules` own pure decisions; mutable/runtime orchestration remains in the original owners. |
| B11 compatibility/pruning proof | Complete | Active roots and compatibility paths are labeled; only proved-dead archive writers and stale IDE metadata were removed. Protocols, plugins, databases, legacy archive, and uncertain MySQL helpers were retained. |

## Priority Order

1. **Complete on `refactor/client-renderer-settings-panel`:** extracted
   `RendererSettingsPanel` without changing the Graphics/General/Android row
   contract or any action ID. Merged into `main` as `26145528f`.
2. **Complete on `refactor/client-renderer-profile-applier`:** extracted
   `RendererProfileApplier` and fingerprinted Classic, Remaster, Custom, resize,
   refresh, runtime-override, and persistence behavior. Merged into `main` as
   `83bf64a9d`.
3. **Complete on `refactor/client-legacy-software-scaling-settings`:** extracted
   `LegacySoftwareScalingSettings` while retaining the active software presenter
   and all three persisted compatibility keys. Merged into `main` as
   `20ffd97a8`.
4. **Complete on `refactor/client-external-asset-loader`:** extracted
   `ClientExternalAssetLoader` behind lookup, decode, frame-order, and
   packaged-resource parity tests. Repository and extracted release-layout
   clients were privately verified. Merged into `main` as `6cfaa6ca7`.
5. **Complete on `refactor/client-scene-instance-store`:** extracted
   `ClientSceneInstanceStore` for authoritative game-object and wall instance
   state while leaving model preparation and scene/collision side effects in
   `mudclient`. Merged into `main` as `74e4c4212`.
6. **Reassessment complete on `docs/client-ownership-reassessment`:** compared
   combat effects, movement, external-content coordination, and presenter
   composite glue. The recommendation is one preloader-only
   `PredictiveTerrainPreloader` branch; do not treat it as authority for broader
   movement work. Merged into `main` as `965008cb2`.
7. **Complete on `refactor/client-predictive-terrain-preloader`:** extracted
   predictive cache warming, its three independent throttles, coordinate
   conversion, waypoint/camera target selection, and reset behind the existing
   `mudclient` facades. Automated checks and private edge-walk, camera,
   teleport/blink, logout, reconnect, and window-close verification passed.
   Merged into `main` as `2785f1a6f`.
8. Defer presenter sprite-composite glue and broad combat/external-content
   moves for the reasons and stop conditions recorded in the reassessment.
9. Keep `World`, `GraphicsController`, and telemetry splits as later candidates,
   not authorized follow-on work while this track is paused.
10. Revisit authored definition families and large server gameplay owners only
    through separate plans; B09 and B10 are foundations, not authority for
    broader rewrites.

Package moves and top-level folder renames are deliberately deferred until
the first five ownership extractions have landed and their compatibility
facades are known. That gate is now met, but this reassessment does not
authorize a package or top-level move; plan one separately after the recommended
preloader branch or an explicit client-cleanup stopping decision. See the
structure plan for that work.

## OpenGLFramePresenter Split Plan

This remains the long-term presenter direction, but its immediate viewport and
window milestone is complete. The file should become a coordinator, not the
place every OpenGL concept lives; further work is paused behind the ordered
`mudclient` sequence so settings/assets/state extractions do not overlap a
visually risky composite rewrite.

Keep the first extraction passes in package `orsc` unless there is a clear
reason to expose APIs across packages. Moving to `orsc.opengl` is desirable
later, but doing that first will force public APIs and make behavior-preserving
extraction noisier.

### Proposed Files

- `LwjglBindings.java`
  - Move the reflective LWJGL/GLFW/GL method table and callback proxy classes.
  - This is a near-mechanical extraction from the bottom of
    `OpenGLFramePresenter.java`.
  - Expected risk: low if moved unchanged.
  - Status: done.

- `OpenGLShaderProgram.java`
  - Move shader compilation, uniform binding, attribute binding, and inline
    shader source strings.
  - Longer term, shader source should become external `.glsl` resources so
    shader work does not require editing a Java string wall.
  - Expected risk: medium because shader uniforms/attributes are tightly coupled
    to vertex layouts.
  - Status: done.

- `OpenGLWorldChunkRenderer.java`
  - Move resident chunk upload, draw, batch culling, material passes, buffer
    lifetime, world matrices, and chunk shader input packing.
  - This class is currently the most important renderer runtime path.
  - Expected risk: medium-high. Extract after `LwjglBindings` and
    `OpenGLShaderProgram`.
  - Status: done.

- `RemasterShadowMaskBuilder.java`
  - Move terrain shadow mask rasterization, blur, cache signature, light-angle
    quantization, and mask texture data.
  - Keep pure data/math separated from GL upload so it can later be tested
    without a window.
  - Expected risk: medium. This is visually sensitive but has clear boundaries.
  - Status: done.

- `RemasterShadowClassifier.java`
  - Move roof coverage, indoor flood fill, wall blocker crossing, and caster
    eligibility/classification.
  - This should become the source of truth for "outdoor sunlight eligible"
    terrain and casters.
  - Expected risk: medium. Needs visual validation around roofless interiors.
  - Status: done.

- `OpenGLWorldSpriteComposite.java`
  - Move world sprite matching, anchor lookup, legacy scene sprite restore, and
    sprite/world occlusion ordering.
  - Expected risk: high. This area caused invisible sprites and layering bugs,
    so extract only after the GL foundation classes are stable.
  - Status: partially done through `OpenGLWorldSpriteDrawController` and
    `OpenGLWorldSpriteRenderer`; keep remaining sprite composite glue in the
    presenter until the input/window/viewport split is stable.

- `OpenGLFrameCapture.java`
  - Move frame capture directory writing, layer export, static-world TSV output,
    shader parity output, and capture burst bookkeeping.
  - Keep disabled by default for release builds.
  - Expected risk: low-medium.
  - Status: done.

- `OpenGLInputBridge.java`
  - Move GLFW key/mouse/scroll/char callbacks and AWT event posting.
  - Expected risk: medium. Needs text input, backspace repeat, mouse wheel zoom,
    chat scroll, and focus tests.
  - Status: done. `OpenGLInputBridge.java` owns callback install/free,
    mouse/key state, AWT event posting, and frame-capture hotkey delegation.
    `OpenGLKeyBindings.java` owns the GLFW-to-AWT key map and `KeyBinding`
    data class.

- `OpenGLWindowController.java`
  - Move borderless/windowed creation, monitor mode, viewport resize, close
    handling, and fullscreen-effectiveness checks.
  - Expected risk: medium. Needs testing through both dev and packaged launchers.
  - Status: done in B07. The controller owns GLFW initialization/context,
    event polling, buffer swaps, window-mode transitions, saved windowed bounds,
    native destruction, and GLFW termination. Cleanup is diagnostic,
    best-effort, and idempotent.

- `OpenGLViewportPresenter.java`
  - Move source framebuffer to destination viewport scaling, aspect-fit bars,
    integer-fit/stretch policy, and framebuffer upload for non-world UI layers.
  - Expected risk: medium. This must stay consistent with current aspect-ratio
    settings.
  - Status: done in B07. The presenter owns logical-window/HiDPI framebuffer
    layout, primary-window aspect-fit enforcement, integer/debug policies,
    text-smoothing selection, mouse remapping, and bar-edge clamping.

### Extraction Sequence

Completed through B07:

1. Extracted `LwjglBindings`.
2. Extracted `OpenGLShaderProgram`.
3. Extracted several plain renderer data holders and texture helpers.
4. Extracted `RemasterShadowClassifier`.
5. Extracted `RemasterShadowMaskBuilder`.
6. Extracted `OpenGLFrameCapture`.
7. Extracted `OpenGLWorldChunkRenderer`.
8. Extracted world mesh, world sprite, glow mask, texture cache, and material
   helper classes.
9. Extracted `OpenGLInputBridge` and `OpenGLKeyBindings`.
10. Extracted `OpenGLViewportPresenter`.
11. Extracted `OpenGLWindowController` and removed all direct GLFW calls from
    `OpenGLFramePresenter`.

Next sequence:

1. The five ordered `mudclient` ownership branches have landed.
2. The post-sequence reassessment found that the remaining sprite composite
   glue is still coupled to GL atlas/resource state, pass orchestration, depth
   reconstruction, capture, and telemetry; leave it stable for the next
   preloader-only client branch.
3. Re-characterize resource lifecycle and sprite-composite ownership only when
   deterministic ordering/occlusion fixtures exist or active renderer work
   requires the boundary. Do not infer readiness from line count.
4. Extract sprite composite code only with dedicated ordering/occlusion
   fixtures and private visual confirmation; do not combine it with settings,
   assets, scene-instance storage, or movement streaming.

B07 private verification covered OpenGL-primary and software-fallback
presentation, 4:3/16:9, resize, borderless/windowed transitions, input, world
content, and cleanup. Borderless mode still has no native title-bar close
button, so `Alt+F4` is the current close path. An in-client close affordance is
a separate UX follow-up, not an extraction prerequisite.

Each step should compile before moving to the next. For visual-risk steps, run
the client and validate at least:

- login screen graphic
- 4:3 and 16:9 aspect settings
- borderless on/off
- player and NPC sprites behind walls/scenery
- ground item sprites
- scenery animations
- roof hide/show
- day/night tone and shadow motion
- F6 simple/expanded debug overlay

## mudclient Split Plan

`mudclient.java` is larger than the OpenGL presenter and is still the main
client god object. It should not be split randomly. Start with pieces that are
already conceptually independent and have low gameplay risk.

### Proposed Extractions

- `RendererSettingsPanel`
  - First post-B11 branch. Move the desktop Graphics row construction,
    slider hit testing, and renderer-action dispatch behind a narrow host
    adapter. Keep General gameplay actions and Android options in `mudclient`.
  - Continue showing software scaling only when the OpenGL primary window is
    inactive; this branch delegates those actions to the existing state owner.
  - Status: complete and merged; detailed contract and implementation record
    below.

- `RendererProfileApplier`
  - Second branch. Move Classic/Remaster/Custom bundle application, profile
    transitions, resize/appearance-refresh callbacks, and persistence of the
    affected renderer setting bundle.
  - This becomes the one place that applies a profile, but it does not replace
    the individual settings classes or move renderer authority into the UI.
  - Status: complete and merged; detailed contract and implementation record
    below.

- `LegacySoftwareScalingSettings`
  - Third branch. Move `renderingScalar`, `newRenderingScalar`,
    `scalarChangedSinceLogin`, scalar lists, `scalingType`, scalar changes,
    type cycling, load validation, and saving.
  - Mark as active software-presenter compatibility. Removal is not part of
    this sequence because OpenGL remains optional and `ScaledWindow` is the
    maintained fallback.
  - Status: complete and merged; detailed contract and implementation record
    below.

- `ClientExternalAssetLoader`
  - Fourth branch. Move the non-remastered external asset lookup and image
    decode/transform infrastructure for `dev/myworld/assets`, legacy `output`
    fallbacks, and embedded `myworld-assets` resources.
  - Do not absorb `RemasteredSpriteResolver`; it already owns the manifest-
    validated, per-frame canonical fallback contract.
  - Status: complete and merged; detailed contract and implementation record
    below.

- `CombatEffectSpriteSystem`
  - Move combat-effect constants, frame counts, scene sizes, detach/draw logic,
    projectile visual generation, and effect sprite lookup.
  - This will reduce the chance that renderer sprite changes alter gameplay
    combat state by accident.
  - Reassessment status: defer this broad shape. Asset storage/loading and
    runtime effect/projectile orchestration are separable boundaries and must
    not be moved as one class.

- `ClientSceneInstanceStore`
  - Fifth branch. Move authoritative game-object and wall-object arrays,
    counts, capacity checks, materialized/pending flags, frame marks, and
    accessor storage.
  - Keep scene/collision materialization, model creation/tinting, resident
    chunk construction, packet decode order, and baseline decisions outside
    the store.
  - Ground items remain a separate follow-up because their storage is coupled
    to render ordering, stack offsets, filters, nameplates, and menus.
  - Status: complete and merged as `74e4c4212`; detailed contract and
    implementation record below.

- `PredictiveTerrainPreloader`
  - Move section preload tracking for target, waypoint, and camera direction.
  - This keeps movement smoothing and terrain streaming from becoming scattered.
  - Reassessment status: recommended as the next focused branch, limited to
    predictive cache warming and its throttle state.

- `MovementInterpolator`
  - Move per-frame movement amount, reset, and custom waypoint append helpers.
  - Keep server-authoritative path state separate from visual interpolation.
  - Reassessment status: defer until after the preloader branch is reviewed;
    do not combine interpolation, custom movement, packet state, or terrain
    streaming merely because their methods are adjacent.

- `LoginSceneRenderer`
  - Move login camera/model drawing and login-screen scene setup.
  - This is low priority, but it isolates the login graphic/minimap regression
    class of bugs.

### mudclient Guardrails

- Do not move packet mutation or gameplay state into renderer classes.
- Do not make renderer settings depend on server settings except where the
  server actually owns state, such as day/night time.
- Do not expose hidden resolution or scaling options through the new settings
  panel.
- Keep `mudclient` as the integration point until each extracted class has a
  clear constructor dependency list.
- Preserve the current public/package-facing `mudclient` facade where B08
  diagnostics, packet handling, platform code, or renderer code still needs
  it. Removing facades is a later call-site migration, not proof that an owner
  extraction failed.
- Every renderer-facing branch requires owner visual confirmation. Do not use
  screenshot capture as the acceptance mechanism; provide a short private
  test checklist and record the owner's confirmation in the handoff.

### Ordered Post-B11 Implementation Branches

These are sequential integration branches because all five touch
`mudclient.java`. Only the first three have a semantic dependency; the last
two are serialized mainly to avoid overlapping extractions and brittle merges.

| Order | Suggested branch | Depends on | Primary exit |
| ---: | --- | --- | --- |
| 1 | `refactor/client-renderer-settings-panel` | B07 and current Graphics contract | Graphics presentation/action ownership leaves `mudclient`; no row, action, or visibility drift. |
| 2 | `refactor/client-renderer-profile-applier` | Branch 1 | One tested owner applies/persists current profiles; the panel only requests actions. |
| 3 | `refactor/client-legacy-software-scaling-settings` | Branches 1-2 | Software-scaler state leaves `mudclient`; `ScaledWindow` fallback and saved keys remain operational. |
| 4 | `refactor/client-external-asset-loader` | Branches 1-3 for merge order only | External lookup/decode leaves `mudclient`; every path, pixel, frame, and fallback contract matches. |
| 5 | `refactor/client-scene-instance-store` | B08; branches 1-4 for merge order | Game/wall instance storage leaves `mudclient`; packet, baseline, region, materialization, and renderer behavior match. |

Do not begin a later branch on an unmerged predecessor merely to reduce line
count faster. At the end of each branch, remeasure `mudclient` and the new
owner, record dependency changes, and confirm that the new class owns a whole
responsibility rather than a bag of forwarded methods.

#### 1. RendererSettingsPanel

Current evidence: `mudclient.drawGraphicsSettingsOptions` builds the desktop
Graphics list; renderer cases are interleaved into
`handleGeneralSettingsClicks`; slider layout/hit testing is split among
`rendererTuningSliderBar`, `handleRendererTuningSliderInput`, and the
`SETTINGS_*` constants. The same handler also sends unrelated gameplay option
packets. Software fallback changes the displayed rows through
`ScaledWindow.isOpenGLPrimaryWindowEnabled()`.

Ownership and boundaries:

- Own desktop Graphics sections, row text/order, stable action IDs, scroll-row
  calculations, tuning-slider rendering/hit testing, and renderer-action
  dispatch. Return handled/not-handled so `mudclient` retains General gameplay
  packet actions and logout behavior.
- Use a narrow adapter for panel/surface drawing, pointer/button state, server-
  owned roof/flicker actions, profile/settings application, appearance refresh,
  messages, and persistence. Do not give the panel unrestricted access to the
  entire client.
- Preserve the OpenGL-primary row matrix (`Preset`, `Sprites`, `Aspect Ratio`,
  `Borderless`, `Lighting`, `Geometry`, `Terrain Variation`, `Fog`, six tuning
  sliders, and visibility rows), including order, colors, labels, scroll
  behavior, and action numbers.
- Preserve the software-fallback matrix (`Sprites`, `Scaling`, `Scaling type`,
  and visibility rows). The panel may display and dispatch scaling, but branch
  1 must continue delegating its state/mutations to `mudclient` until branch 3.
- Leave the Android third tab and its compatibility sprite/roof/flicker rows,
  authentic settings, server option packets, settings file format, settings
  class parsers, and profile bundles unchanged.

Automated verification:

- Refactor `test-desktop-graphics-options-tab.py` and
  `test-renderer-v2-options-cleanup.py` to inspect the new owner rather than
  pinning implementation to `mudclient`.
- Add a compiled fixture that enumerates row IDs/order/labels and click results
  for OpenGL primary, software fallback, disabled server visibility flags,
  scrolled slider rows, click-versus-drag input, and no-action section rows.
- Run renderer shading, remastered-sprite, window/viewport, experimental-camera,
  packet-shape, client build, and changed-code static-analysis guards.

Private visual confirmation: ask the owner to open Graphics in OpenGL mode,
scroll the full list, exercise each profile/sprite/aspect/window/lighting/
geometry/variation/fog control and each slider, then check roof/flicker rows.
Repeat the tab in software fallback and confirm scaling rows appear while
OpenGL-only rows do not. Confirm General and authentic/Android behavior through
available devices or automated compatibility guards; an Android runtime is not
a prerequisite when none is maintained locally.

Stop if a row/action moves, a renderer click sends a gameplay settings packet,
slider drag or scroll coordinates change, fallback exposes an OpenGL-only
control, Android/general behavior must be rewritten, or the panel requires
packet/gameplay state beyond its adapter.

Implementation record (2026-07-16): the focused branch moved desktop Graphics
row construction, stable IDs, footer presentation, software-scaling control
layout/hit testing, tuning-slider mutation/hit testing, and stable-ID-to-
semantic-action mapping into a 566-line `RendererSettingsPanel`. The merged
baseline `mudclient` was 27,550 lines; it is 27,350 lines on the tested branch.
The new owner depends on the narrow `View`, `State`, `Input`, and `Actions`
boundaries plus the existing renderer setting classes. `mudclient` still owns
profile/settings application and persistence, appearance refresh, software-
scaler state, logout, and the two existing visibility packets. A compiled
fixture covers both row matrices, exact labels/action order, disabled
visibility flags, scrolled software controls, click/drag slider input, and
non-action section rows. The owner privately confirmed both OpenGL-primary and
software-fallback presentation and interaction; General remained unchanged.

#### 2. RendererProfileApplier

Current evidence: `mudclient.applyOpenGLRendererProfileMode` mutates eleven
settings families, resets relief/color tuning, applies surface resize, refreshes
appearance sprites for Classic, and performs many separate settings-file
writes. Manual renderer actions independently call
`RendererProfileSettings.markCustom()` and save. Classic deliberately disables
remastered sprites; selecting Remaster currently does not force them on.

Ownership and boundaries:

- Own profile transition/application and persistence of the complete affected
  bundle: profile, render surface, window, lighting, geometry, terrain
  variation, fog, brightness/tone state, relief/color tuning, and remastered
  sprite state.
- Accept explicit host callbacks only for render-surface resize and appearance
  preview refresh. The applier must not draw UI, send packets, manipulate world
  time, or reach into `mudclient` gameplay state.
- Preserve exact current-main fingerprints for Classic, Remaster, and Custom,
  including reset ordering, gamma/saturation behavior, runtime-override
  semantics, the Classic-only sprite disable/refresh behavior, and every saved
  key/value. Do not “complete” Remaster by automatically enabling enhanced
  sprites unless separately approved as a behavior change.
- Let the panel request `cycle/apply` or `markCustom`; do not duplicate bundle
  values in the panel or create a second profile table.
- A single read-modify-write persistence transaction is desirable only if a
  fixture proves it writes the same complete result and retains unrelated
  properties. This branch is not authorization for a general config rewrite.

Automated verification:

- Add a compiled profile fingerprint fixture using isolated temporary settings
  files. Cover every mode, cycle order, manual Custom transitions, pre-existing
  unrelated keys, missing/malformed values, runtime overrides, resize callback
  count/dimensions, appearance-refresh count, and repeated application.
- Preserve the renderer options, settings migration, remastered sprite,
  day/night, shading, font, window/viewport, and client packaging tests.
- Build the client and run changed-code compiler plus static analysis.

Private visual confirmation: cycle Classic, Remaster, and Custom from Graphics;
verify current accepted visual bundles, 4:3/16:9 resize, borderless behavior,
sprite mode/appearance preview, lighting/fog/tone, and all tuning sliders.
Manually change representative settings and confirm the label becomes Custom.
Restart and confirm the exact selected values persist.

Stop on any profile value/default/order drift, unexpected enhanced-sprite
enablement, loss of unrelated config keys, extra/missing resize or appearance
refresh, runtime override being overwritten, or a need to change shader,
renderer, server-clock, or asset-loader behavior.

Implementation record (2026-07-16): the focused branch moved renderer-profile
transitions, complete-bundle application, manual Custom marking, and semantic
settings persistence into a 180-line `RendererProfileApplier`. The merged
baseline `mudclient` was 27,350 lines; it is 27,246 lines on the tested branch.
The new owner depends on the existing renderer setting classes plus narrow
settings-store, resize, and appearance-preview boundaries. Complete profile
application now retains unrelated properties in one read-modify-write
transaction while preserving the ten setting families current `main` actually
writes. Brightness remains intentionally runtime-only because the previous
path applied `HIGH` in memory but did not persist `opengl_brightness`.
`mudclient.saveOpenGLWindowSettings()` remains as the active presenter-facing
compatibility facade and persists only window state without marking Custom.
A compiled isolated-settings fixture covers exact Classic/Remaster/Custom
fingerprints, cycle order, missing/malformed values, runtime overrides,
unrelated-key retention, callback counts and dimensions, repeated application,
manual Custom transitions, transaction counts, and cross-process restart.
The owner privately confirmed Classic/Remaster/Custom cycling, resize and
renderer presentation, enhanced-sprite appearance refresh, and Custom/Enhanced
persistence after a private-client restart.

#### 3. LegacySoftwareScalingSettings

Current evidence: `mudclient` owns public static scaler state and mutation,
`OpenRSC` loads ordinal `scaling_type` and `ui_scale` with
`scaling_scalar` fallback, `ORSCApplet` applies pending scalar changes, and
`ScaledWindow` creates allowed scalar lists, clamps values, maps input, sizes
and paints the software framebuffer, and selects interpolation by
`ScaledWindow.ScalingAlgorithm`.

Ownership and boundaries:

- Move scaler state, allowed-scalar lists, pending/current transition state,
  type cycling, bounds validation, and load/save behavior to
  `LegacySoftwareScalingSettings` in package `orsc`.
- Preserve the three `ScalingAlgorithm` ordinals exactly. Keeping that enum in
  `ScaledWindow` during this extraction is acceptable and minimizes binary and
  persisted-value churn; relocating it is a later mechanical branch.
- Update `OpenRSC`, `ORSCApplet`, `ScaledWindow`, the panel adapter, frame
  telemetry, and presenter compatibility calls to query the owner. Retain a
  temporary `mudclient` facade only where a source/binary compatibility test
  proves it necessary, and label it.
- Preserve `scaling_type`, `ui_scale`, and `scaling_scalar`; preserve current
  clamping, scalar sequences, pending resize timing, login redraw suppression,
  mouse/key mapping, image types, and interpolation. No key deletion, default
  change, or new OpenGL control is allowed.
- This branch does not remove `ScaledWindow`, change OpenGL-primary presentation,
  fold in `OpenGLPresentationSettings`, or expose old resolutions.

Automated verification:

- Add a compiled compatibility fixture for all enum ordinals, integer and
  interpolation scalar sequences, bounds/clamping, up/down endpoints,
  fractional-to-integer transition, malformed/out-of-range saved input,
  `ui_scale` precedence, `scaling_scalar` migration, and round-trip persistence.
- Exercise `ScaledWindow` sizing/input math without requiring a visible window,
  plus the B07 software ownership fallback fixture.
- Run graphics-options, renderer options, compatibility-label, client build,
  packaged launcher contract, compiler, and changed-code analysis tests.

Private visual confirmation: launch with OpenGL disabled, log in, cycle
integer/bilinear/bicubic, change scale at every available bound, resize, and
verify crisp/expected presentation and accurate mouse/keyboard mapping. Restart
to confirm persistence, then launch OpenGL-primary and confirm legacy rows stay
hidden and aspect-fit presentation is unchanged.

Stop if software fallback cannot reach login/world, input coordinates drift,
the selected scale differs after restart, an ordinal/key would need migration,
OpenGL-primary starts consuming software state, or removing a facade would
require a package/top-level move.

Implementation record (2026-07-16): the focused branch moved the scaling
algorithm, current/pending scalar transition, login redraw flag, immutable
allowed-scalar sequences, bounds validation, cycling, and settings
load/persistence into a 250-line `LegacySoftwareScalingSettings`. The published
baseline `mudclient` was 27,246 lines; it is 27,194 lines on the tested branch.
No `mudclient` compatibility facade was retained because repository-wide
source, reflection, script, and packaging searches found no consumer outside
the jointly compiled desktop client. `ScaledWindow.ScalingAlgorithm` and its
ordinals remain unchanged, while `ScaledWindow`, `ORSCApplet`, `OpenRSC`, the
settings panel adapter, packet login handling, and telemetry now query the
owner. The active compatibility keys remain `scaling_type`, `ui_scale`, and
`scaling_scalar`, including `ui_scale` precedence and legacy-key fallback.
Malformed, non-finite, and unsupported stored scalars are now bounded to the
documented allowed values rather than reaching invalid framebuffer geometry.

A compiled fixture covers every ordinal, integer/interpolation scalar
sequence, immutable-list ownership, scale bounds, pending/current transitions,
login redraw suppression, fractional-to-integer truncation, image types,
headless sizing/input math, malformed/missing/out-of-range values, small-screen
clamping, key precedence and legacy migration, unrelated-key retention,
single-transaction persistence, and cross-process restart. The client build,
full MyWorld suite, renderer guardrails, B07 fallback fixture, graphics and
renderer option contracts, packaging checks, and changed-code javac,
Checkstyle, PMD, SpotBugs, ShellCheck, and Ruff analysis pass without a new
finding or baseline reduction. The owner privately confirmed software-fallback
login/world presentation, all three interpolation modes and available bounds,
resize and input mapping, Integer/1.0x persistence across restart, and normal
OpenGL-primary aspect-fit presentation with the legacy rows hidden.

#### 4. ClientExternalAssetLoader

Current evidence: roughly the `mudclient` block from
`getCustomTextureCount` through external world/icon/animation transforms owns
candidate roots, embedded-JAR inventory, existence/read helpers, caches,
equipment frames, item sprites, altar textures, UI/prayer/magic/summoning
icons, and animation sheet slicing. It searches current/parent worktrees,
legacy `output` paths, and packaged `myworld-assets`. Content-specific frame
maps and combat/projectile arrays are interleaved with this infrastructure.

Ownership and boundaries:

- First extract discovery and decoding: candidate-base construction, dev/output
  and embedded-resource lookup, archive inventory, image reads, transparent-
  pixel normalization, crop/nearest-neighbor transforms, item cache, equipment
  frame decode, icon decode, and generic directory/strip/grid frame slicing.
- Keep `mudclient` as the content coordinator for which named assets to request,
  destination sprite indexes/arrays, effect/projectile definitions, frame
  counts, procedural altar fallbacks, scene size/animation timing, and surface
  installation. Those belong to later content-specific owners.
- Preserve exact lookup precedence, accepted spellings/typo fallbacks, relative
  path handling, embedded resource names, alpha thresholds, opaque-black
  replacement, dimensions, offsets, frame order/count, cache keys, and
  missing/corrupt fallback behavior.
- Preserve the existing sibling `../Core-Framework` candidate during the
  extraction even though cross-worktree lookup is brittle. Characterize which
  launch/package path needs it, then propose removal separately; do not hide a
  behavior change inside the ownership move.
- Do not absorb or rewrite `RemasteredSpriteResolver`, its catalog/manifest,
  setting, cache, or guaranteed canonical per-sprite/per-frame fallback. Do not
  move packaged `.orsc`/`.mem` archive loading, models, sounds, or texture
  semantics in this first branch.

Automated verification:

- Add temporary-directory and packaged-JAR fixtures for every candidate-root
  class and precedence case, embedded directory enumeration, missing/corrupt
  files, cache reuse, and resource closing/diagnostics.
- Fingerprint decoded pixels, dimensions, shifts/bounds, equipment offsets and
  layers, icon crops, alpha thresholds, and strip/grid/nested-directory frame
  order against published main.
- Run remastered workspace/loader, animation migration, projectile/spell/prayer/
  summoning, item asset/reference, client packaging, client build, and
  changed-code analysis tests.

Private visual confirmation: test a branch-built client from the repository
and from a packaged release outside it. Inspect representative external item
and ground sprites, worn equipment in normal/combat directions, altar glyphs/
orbs, prayer/magic/summoning icons, and moving/impact combat effects. Toggle
Classic/Enhanced sprites and confirm missing remastered frames still fall back
to canonical sprites.

Stop on any path-precedence, frame-order, pixel/alpha, offset, cache, or
fallback mismatch; any packaged asset that loads only beside a source checkout;
any proposal to reorganize `dev/myworld/assets`; or any need to change combat
timing, sprite IDs, remastered catalog data, build packaging, or archive format.

Implementation record (2026-07-16): the focused branch moved candidate-path
construction, development/output/embedded lookup, packaged-JAR inventory,
image reads, item lookup/cache, equipment decode, pixel/alpha transforms, icon
decode, and generic directory/strip/grid animation slicing into the 838-line
`ClientExternalAssetLoader`. The published baseline `mudclient` was 27,194
lines; it is 26,369 lines on the tested branch. `mudclient` remains the content
coordinator and still owns asset-name catalogs and typo fallbacks, destination
sprite indexes, equipment composition, effect/projectile arrays, configured
animation maps, frame counts/timing, procedural altar fallback, and surface
installation. Thin configured-animation calls remain private adapters to the
new generic slicing owner rather than a second implementation.

The branch preserves development, legacy-output, and embedded-resource names
and precedence, including the brittle `../Core-Framework` candidate. With the
normal repository launcher, `user.dir` is `Client_Base` and repository assets
resolve from the parent-worktree candidate. The sibling-name candidate is only
meaningful for root/IDE-style working directories and is not required by the
packaged client, which resolves embedded `myworld-assets`; removal therefore
remains a separately approved compatibility decision. Missing and corrupt
assets retain their established fallbacks. Packaged inventory failure now emits
one bounded diagnostic, without changing fallback behavior. The independent
`RemasteredSpriteResolver`, canonical per-sprite/per-frame fallback, archive
loading, models, sounds, and texture semantics were not absorbed or changed.

A compiled fixture exercises all candidate classes and precedence, missing and
corrupt input, item-spec validation and cache identity, exact crop/alpha/black
normalization, equipment bounds and pixels, world/icon transforms, prayer-sheet
ordering, directory nesting and numeric order, grid/native-grid/strip slicing,
and input-stream closure. It also creates and loads a real temporary JAR to
verify embedded file/directory discovery, inventory caching, decoding,
diagnostic bounds, resource closing, and post-close JAR movement. Source guards
protect the ownership boundary and remastered-loader separation.

The client build, full MyWorld suite, renderer guardrails, remastered
workspace/loader tests, animation/projectile/spell/prayer/summoning migrations,
item coverage and sprite-reference audits, packaging contracts, and
changed-code javac, Checkstyle, PMD, SpotBugs, ShellCheck, and Ruff analysis all
pass. The extraction removes the exact pre-existing unused-import warning it
made obsolete and introduces no new finding. The release script correctly
refused to publish from a worker branch; instead, the tested branch JAR was
placed in the real release layout and extracted entirely outside the source
repository for the required private package-path smoke test. The owner
confirmed repository and extracted-package presentation matched for external
ground/item and worn-equipment sprites, altar assets, prayer/magic/summoning
icons, combat effects, and Classic/Enhanced canonical fallback.

#### 5. ClientSceneInstanceStore

Current evidence: `mudclient` owns parallel game-object and wall-object arrays
for x/z/id/direction/model/materialized/pending state, per-frame marks, dynamic
capacity, counts, and public accessors. `PacketHandler` mutates/copies them in
wire order, B08 `SceneBaselineState` reads and prunes through the facade, and
`mudclient` performs scene/collision materialization, hard-load retention,
renderer resident-chunk export, animation, picking, and diagnostics.

Ownership and boundaries:

- Move authoritative game-object and wall-object storage, count truncation
  cleanup, dynamic growth, per-frame marks, and exact indexed accessor semantics
  to `ClientSceneInstanceStore`.
- Preserve model kind/key invariants (`index` for game objects and the current
  wall key base), nulling on truncation, materialized/pending flags, and the
  current capacity-growth formula. Keep model preparation/tint callbacks
  explicit rather than giving the store definition, scene, world, or renderer
  authority.
- Keep packet reads, record matching, copy/compaction order, baseline decisions,
  area-load decisions, scene add/remove, collision changes, wall/model creation,
  animation, resident-chunk building, picking, and world-editor actions in their
  current owners. Initial wrappers may delegate from `mudclient` so B08 and
  `PacketHandler` do not have to change architecture simultaneously.
- Do not include ground-item arrays in this branch. Their authoritative data is
  entangled with render priority, stack offsets, filters, nameplates, menus,
  notes, and packet operations; characterize a later `ClientGroundItemStore`.
- Do not combine this branch with packet-family extraction, scene-baseline
  redesign, renderer optimization, object-ID cleanup, or fixed-capacity tuning.

Automated verification:

- Add a compiled store fixture for empty/repeated cleanup, growth boundaries,
  set/get identity, count shrink/null/reset, copy/compaction, model keys/kinds,
  materialized/pending transitions, frame marks, and overflow-safe growth.
- Fingerprint packet object/wall mutation order and B08 baseline/pruning/parity
  results before and after. Update source guards to follow ownership rather than
  requiring arrays in `mudclient`.
- Run packet diagnostic/shape, deferred scenery, region-load, relog resident
  world, scene lifecycle, rowboat death/respawn, world geometry/material,
  world-editor, client/server landscape parity, client build, and changed-code
  analysis tests.

Private visual confirmation: log in to a populated private area; walk/run over
a region boundary; teleport across distant regions; add/remove or observe
scenery and walls; inspect animated objects, doors, roofs, ground items, NPCs,
and resident object rendering; then relog/reconnect and test death/respawn or a
rowboat transition where practical. Confirm no stale, duplicated, missing, or
mis-keyed objects and no packet/scene/cleanup exceptions.

Stop on any packet/baseline ordering or count change, stale/duplicate/missing
world instance, collision/picking/model-key difference, OpenGL/software parity
failure, a need to change world-editor or protocol semantics, or pressure to
pull ground-item/rendering policy into the store.

Implementation record (2026-07-16): the focused branch moved the authoritative
game-object and wall-object x/z/id/direction/model arrays, counts, capacity
growth, truncation cleanup, materialized/pending flags, frame marks, model
key/kind assignment, and pending-area-load compaction into the 343-line
`ClientSceneInstanceStore`. The published baseline `mudclient` was 26,369
lines; it is 26,255 lines on the tested branch. Existing `mudclient` accessors
remain as a deliberate compatibility facade for `PacketHandler`, B08
`SceneBaselineState`, and other established callers. Packet reads and mutation
order were not changed.

Model preparation, including the sulfur-dragon tint rule, remains explicit in
`mudclient` before storage. Scene/world materialization, collision flags,
region rebase and area-load decisions, model construction, animation,
resident-chunk export, picking, diagnostics, and world-editor actions also
remain with their existing owners. Ground-item arrays and their rendering/menu
policy were not moved. The store preserves the existing game/wall model kinds,
index and wall-key-base rules, exact capacity-growth formula, truncation
nulling, pending retention behavior, and the distinction between moved and
same-index wall models.

A compiled fixture covers empty and repeated cleanup, exact growth boundaries,
overflow handling, every stored field, model identity/kinds/keys, count shrink,
pending compaction, materialized/pending transitions, and frame marks. Source
guards verify that storage no longer lives in `mudclient`, model preparation
remains outside the store, ground items and scene/world authorities were not
absorbed, packet mutation order is unchanged, and baseline state still uses
the facade. The focused packet, deferred-scenery, region-load, relog resident
world, scene-lifecycle, rowboat respawn, world-geometry/material,
world-editor, landscape-parity, server-sync, mining, renderer-guardrail, client
build, and changed-code analysis checks pass. The full MyWorld suite also
passes after updating one stale cosmic-sparkle source guard to follow the
existing accessor instead of requiring the removed backing array. Private
OpenGL presentation was confirmed unchanged; the practical rowboat/death paths
remain covered by automated lifecycle tests rather than a new manual run.

## Post-Sequence Client Responsibility Reassessment

Reassessment baseline: published `main` `efafde768` on 2026-07-16, after all
five ordered ownership branches were merged. This is an inventory and branch
recommendation only; it does not authorize production changes on the planning
branch.

The remaining candidates are not equivalent slices of `mudclient`. Combat
effects and external content overlap at asset installation, movement combines
visual interpolation with protocol and terrain-streaming concerns, and the
presenter composite is already distributed across several owners. The useful
next step is therefore the smallest boundary with authoritative state and
observable invariants, not the candidate with the largest raw line count.

| Candidate | Evidence and current boundary | Impact | Confidence | Behavior risk | Estimated effort | Decision |
| --- | --- | --- | --- | --- | --- | --- |
| Predictive movement preloading | The former `mudclient` source IDs, camera tables, nine throttle fields, target/waypoint/camera decisions, conversion, and reset now live in the 259-line `PredictiveTerrainPreloader`. `PacketHandler` still calls the public incoming-position facade before region handling. | Medium | High | Low-medium | Small-medium | **Implemented, privately verified, and merged as `2785f1a6f`.** |
| Combat-effect/projectile visuals | IDs and sprite ranges are at `mudclient.java:134-245`; sprite, mirror, queue, and detached-effect state at `:1057-1137`; scene/screen drawing at `:19024-19299`; external loading at `:19600-19747`; lookup/overlay queues at `:20187-20713`; packet-driven timing/projectile policy at `:24207-24481`. | High | High | High | Large if kept as one system | Defer the broad `CombatEffectSpriteSystem`; characterize visual-asset ownership separately from runtime orchestration. |
| External-content coordination | The 838-line `ClientExternalAssetLoader` owns discovery/decoding, while `mudclient.java:19308-20132` still chooses equipment, altar, combat/projectile, spell, prayer, and summoning catalogs and destinations. | Medium-high | High | Medium-high | Large | Do not replace it with one new coordinator god object. Split only along destination owners. |
| Presenter sprite composite | `OpenGLFramePresenter` is 4,068 lines. Remaining composite work spans `:1693-3007` and `:3216-3690` despite the existing 420-line `OpenGLCompositeSceneBuilder`, 271-line `OpenGLWorldSpriteRenderer`, and draw controller. | High | High | Very high | Large-extra-large | Defer until deterministic ordering/occlusion fixtures or active renderer work justify the risk. |

### Candidate Findings

#### Combat effects and projectiles

This is important but not one safe extraction. The current label combines at
least four responsibilities:

- stable combat-effect/projectile IDs, ranges, names, geometry, and timing;
- external sprite loading, fallback catalogs, frame counts, mirroring, and
  scaled-static-projectile caching;
- scene and software-overlay queues, detached effects, screen anchoring, and
  draw order;
- packet-driven arrival, impact deferral, target/caster geometry, and mutation
  of `ORSCharacter` combat/projectile state.

`PacketHandler` actively calls `detachNpcCombatEffect`,
`detachPlayerCombatEffect`, `applyCombatEffectUpdate`, and
`getProjectileMaxRange`; `ORSCharacter` remains the runtime state owner. The
area also had concentrated recent behavior changes in the July 10 projectile
and spell-animation sequence (`b23e1c6b1`, `d993531e0`, `abee38f86`, and
`389157d6c`, among others). Existing migration and fallback tests are useful,
but they primarily fingerprint behavior in `mudclient`; they do not yet prove
an isolated runtime owner.

A later branch should first choose between a `ClientCombatVisualAssets` owner
for sprites/frame counts/mirror caches and a runtime effect coordinator. It
must not move both merely to satisfy the older broad class name, and it must
not move combat authority out of packet/character state.

#### Movement and predictive streaming

Broad movement is also not ready as one class. The player/NPC frame loop at
`mudclient.java:15617-15840` mutates animation, direction, current position,
waypoint indices, and diagnostics. Custom movement at `:22216-22585` combines
region checks, server-index caches, delayed NPC targets, combat-stance
invalidation, waypoint append behavior, and debug reporting. `PacketHandler`
owns wire reads and standard/custom snapshot order, while `ORSCharacter` owns
the waypoint ring and per-character interpolation remainder.

Predictive terrain preloading is different. Its only durable state is three
independent `(plane, sectionX, sectionZ)` throttles for click/packet targets,
the active waypoint, and camera look-ahead. It observes movement context but
does not mutate authoritative movement state. The established behavior is
already covered by `test-client-predictive-terrain-preload.py`, CPU section
window/cache tests, custom-movement stability tests, region-load performance,
and world-streaming tests. Most of the current preloader block dates to the
June 24 renderer-remaster foundation, with the July 11 movement diagnostics
remaining a separate owner.

That makes preloading a real ownership extraction rather than an arbitrary
helper move. It also creates a useful line between best-effort terrain cache
warming and movement interpolation before either system grows again.

#### External-content coordination

The fourth branch deliberately left content choice and destination authority
in `mudclient`. The external-loader fixture currently guards that distinction:
equipment assembly, configured animation-sheet selection, combat and
projectile arrays, spell/prayer aliases, and procedural altar fallback stay
outside the infrastructure loader.

Those responsibilities should not now be swept into a single
`ClientExternalContentCoordinator`. Equipment installation mutates
`Surface.spriteTree`; altar content targets textures and world-sprite arrays;
spell/prayer/summoning icons use different stable definition identities; and
combat/projectile installation targets the same state considered by the
combat-effect candidate. A whole-block extraction would reduce
`mudclient.java` line count while preserving or increasing cross-domain
coupling. Destination-specific owners should absorb their own catalogs when
there is feature pressure; the combat visual-asset inventory is the likeliest
first consumer after the preloader branch.

#### Presenter composite glue

The composite path already has meaningful extracted owners, but the remaining
presenter code is not just delegation. It coordinates replacement-composite
gates, world and overlay pass order, the dynamic sprite atlas, visible-pixel
reconstruction, direct-overlay coverage masks, entity depth evaluation,
occluder statistics, capture layers, telemetry, GL projections, and resource
lifecycle. Existing renderer tests provide broad source and end-to-end visual
guardrails, but the large world-geometry guard still asserts many presenter
implementation snippets rather than a deterministic composite result.

Moving this now would have the highest visual regression burden and would not
unblock the recommended preloader boundary. Before a composite extraction,
add or identify fixtures that can prove command classification, ordering,
anchor matching, mask coverage, depth fallback, and telemetry parity without a
window. Continue to require private checks for characters/NPCs behind walls,
ground items, roofs, animated scenery, alpha effects, UI ordering, 4:3/16:9,
and software fallback.

### Recommended Next Branch: PredictiveTerrainPreloader

Suggested branch: `refactor/client-predictive-terrain-preloader`.

Ownership contract:

- Move the three preload-source identities, camera direction step tables and
  look-ahead distance, nine section/plane throttle fields, section deduplication,
  reset, local-pixel conversion used by preloading, and target/waypoint/camera
  cache-warming decisions to `PredictiveTerrainPreloader`.
- Retain only throttle state in the new owner. Receive current `World`, plane,
  offsets, region base, tile size, camera direction, readiness/loading state,
  and local-player context as explicit inputs; do not retain the client,
  renderer, scene, packet handler, or platform window.
- Preserve three independent throttles. The same section requested by target,
  waypoint, and camera sources may warm once per source; a repeat from the same
  source/plane must remain suppressed.
- Preserve exact offset-adjusted coordinates, `World.worldTileToSection`
  conversion, camera quantization, one-section look-ahead, circular waypoint
  inactive-slot convention, and the current order that marks a section before
  calling `World.preloadSections`.
- Keep the public `mudclient.preloadTerrainForIncomingWorldPosition` facade so
  `PacketHandler` and existing callers do not change architecture. Keep the
  blink, click-walk, custom movement, standard movement, and main-loop call
  sites in their current order.

Explicit exclusions:

- Do not move or alter `movementAmountForFrame`,
  `resetMovementInterpolation`, the player/NPC update loops, waypoint append,
  delayed custom-NPC targets, `ORSCharacter` fields, movement diagnostics,
  packet reads, snapshot staging, region selection, or `loadNextRegion`.
- Do not change `World` cache size/eviction/loading policy, active-region
  selection, camera behavior, movement speeds, protocol/configuration, or
  renderer scene ownership.
- Do not combine this with `MovementInterpolator`, combat visuals, external
  content, presenter work, or a package/folder move.

Automated verification:

- Add a compiled owner fixture covering empty and repeated calls, each source
  throttle independently, same-section cross-source behavior, plane changes,
  reset, world offsets, negative/floor-divided local pixels, all eight camera
  directions, active/inactive waypoint ring states, null/not-ready/loading
  guards, and exact preload target/call counts.
- Update `test-client-predictive-terrain-preload.py` to follow ownership while
  retaining call-site/order fingerprints. Specifically preserve standard
  movement preload before region loading, custom movement preload before
  `loadNextRegion`, blink and click targets, main-loop waypoint/camera warming,
  and reset lifecycle.
- Run client predictive preload, CPU section window/cache, custom movement
  stability, movement timing diagnostics, packet diagnostics/shape, region-load
  performance, world streaming/backend, relog resident world, rowboat
  respawn, landscape parity, renderer guardrails, the full client build, and
  changed-code static analysis. Run the full MyWorld suite if any shared guard
  beyond the focused ownership test changes.

Private verification: on an isolated server, click-walk and run toward and
across a region edge, change camera direction near an edge, use a long/distant
movement or teleport transition, exercise a blink if practical, and relog.
Confirm terrain continues to appear without a new hitch, premature region
swap, stale/missing scenery, movement snap, camera change, or client exception.
Owner confirmation is the acceptance mechanism; screenshots are not required.

Stop conditions: stop if the extraction requires packet-order changes,
movement/waypoint mutation, a different region-load decision, new `World`
cache policy, renderer/scene ownership, or a broad host adapter exposing
`mudclient`. Stop on any preload target/count difference in the fixture, any
movement or region regression, or any pressure to include interpolation just
because the methods are adjacent.

After this branch, stop and reassess. Do not automatically continue into
`MovementInterpolator`; combat visual assets may become the better next owner
if animation work resumes, while no further client extraction is also an
acceptable result if feature work is not being blocked.

### PredictiveTerrainPreloader Implementation Record

Status: complete on `refactor/client-predictive-terrain-preloader`; private
verification passed on 2026-07-16 and the branch was merged into `main` as
`2785f1a6f`.

- Added a 259-line package-private `PredictiveTerrainPreloader` owner. It owns
  only the three source identities, camera lookup data, nine throttle values,
  coordinate conversion, request selection/deduplication, dispatch, and reset.
- Reduced `mudclient.java` from the 26,255-line published baseline to 26,171
  lines. Its incoming-position, local-target, active-movement-context, and
  reset methods remain compatibility facades with their original call sites
  and ordering.
- Left movement interpolation, packet reads and snapshot staging, waypoint
  mutation, region selection, `World` caching/loading policy, renderer/scene
  ownership, and diagnostics in their established owners. `World.java` did
  not change.
- Replaced the predictive-preload source-only check with a compiled fixture.
  It covers readiness/loading/null guards, exact and repeated calls, all three
  independent sources, same-section cross-source behavior, plane changes,
  reset/idempotent reset, offsets, negative floor division, waypoint ring
  states without mutation, all eight camera directions, and exact prepared
  request counts. Source guards continue to enforce standard/custom preload
  ordering and blink, click, main-loop, and reset call sites.
- Passed the full client build; focused predictive, custom movement, movement
  diagnostics/timing, packet diagnostics/shape, CPU section-cache, region-load,
  world-streaming, relog resident-world, rowboat lifecycle, landscape parity,
  and server-sync checks; the complete renderer guardrail suite; and
  changed-code javac, Checkstyle, PMD, SpotBugs, and Ruff analysis. No new
  changed-code warning was introduced. Manager review also ran the complete
  MyWorld consolidation suite successfully before publication.
- Private OpenGL testing passed across region-edge walking/running, camera
  changes, teleport/blink movement, normal logout, reconnect, and native
  window close. The branch client exited successfully; the isolated server
  cleanly saved/unregistered the player after window close. No terrain hitch,
  premature region swap, missing scenery, movement snap, camera regression, or
  client exception was observed.

The client structural-refactoring track is paused after this merge. Do not
infer authorization for interpolation, combat visuals, external-content
coordination, presenter composites, package moves, or top-level folder moves
from this extraction. Resume only for a concrete feature boundary or a fresh,
reviewed cleanup plan.

## Stale And Hidden Option Audit

These paths should be explicitly quarantined before deeper cleanup. The main
risk is future work seeing the old names and assuming they are active
player-facing graphics systems.

| Surface | Disposition | Extraction rule |
| --- | --- | --- |
| `ScaledWindow`, `ScalingAlgorithm`, `scaling_type`, `ui_scale`, `scaling_scalar` | Active software-presenter fallback and saved-setting compatibility. | Move ownership in branch 3; do not remove keys or implementation. |
| Hidden `RenderSurfaceSettings.Mode` values | Active migration aliases for old saved resolutions. | Keep parseable and out of the player selector. |
| `OpenGLPresentationSettings` integer/stretch modes | Active secondary/debug presentation policy; OpenGL primary enforces aspect fit. | Keep with `OpenGLViewportPresenter`; do not expose as normal settings. |
| Camera tilt/extra zoom implementation and runtime overrides | Active baseline behavior/diagnostics. | Do not revive obsolete player toggles. |
| Hidden body-font mapping and retained server fog byte | Active compatibility parsing/protocol surfaces; not normal renderer controls. | Preserve until their callers/protocols receive separate proof. |
| Old visible `Resolution`, renderer, font, manual tone, superseded brightness, and OpenGL scaler rows/hotkeys | Obsolete player-facing settings. | Keep absent; do not mistake retained parsers/runtime state for a reason to restore the UI. |

### Legacy Software Scaling

Current locations:

- `Client_Base/src/orsc/mudclient.java`
  - `scalingType`
  - `renderingScalar`
  - `newRenderingScalar`
  - `saveScalingSettings`
  - `scaleUp`
  - `scaleDown`
  - `cycleScalingType`
  - old `Scaling` and `Scaling type` settings rows
- `PC_Client/src/orsc/ScaledWindow.java`
  - software presenter resize, repaint, mouse mapping, and interpolation scale
    logic
- `PC_Client/src/orsc/OpenRSC.java`
  - loads `scaling_type` and `ui_scale`, with `scaling_scalar` as fallback
- `Client_Base/clientSettings.conf`
  - tracked defaults currently omit the scaler keys, but existing user files
    can still contain them and the desktop launcher continues to read them

Current intended status:

- Active software-presenter compatibility only. `ScaledWindow` remains the
  maintained fallback when OpenGL is unavailable or disabled.
- Not an OpenGL-primary player graphics setting.
- Not a replacement for true render-surface/aspect or camera zoom.

Cleanup target:

- Move to `LegacySoftwareScalingSettings`.
- Add a source-level comment that this is not for OpenGL-primary renderer work.
- Retain `scaling_type`, `ui_scale`, and `scaling_scalar` until OpenGL is
  mandatory and a separately approved migration/removal branch proves that no
  maintained launcher or saved config needs them.

### OpenGL Presentation Scale Mode

Current locations:

- `Client_Base/src/orsc/OpenGLPresentationSettings.java`
  - `aspect-fit`
  - `integer-fit`
  - `stretch`
- `Client_Base/src/orsc/mudclient.java`
  - `cycleOpenGLScaleMode`
- `PC_Client/src/orsc/OpenGLFramePresenter.java`
  - passes presentation inputs to the extracted viewport owner
- `PC_Client/src/orsc/OpenGLViewportPresenter.java`
  - `currentScaleMode`
  - viewport computation, filtering, text smoothing, and mouse remapping
- `Client_Base/clientSettings.conf`
  - tracked defaults currently omit `opengl_scale_mode`; existing user files
    and explicit runtime configuration remain accepted

Current intended status:

- Presentation policy, not a normal player-facing graphics option.
- Aspect-fit is the baseline. Borderless/windowed and aspect ratio are the
  current user-facing controls.

Cleanup target:

- B07 folded policy into `OpenGLViewportPresenter`. Keep non-aspect modes as
  secondary/debug presentation compatibility while primary-window presentation
  enforces aspect fit.
- Do not re-add it to the in-game Graphics menu without a deliberate design
  decision.

### Hidden Render Surface Values

Current locations:

- `Client_Base/src/orsc/RenderSurfaceSettings.java`
  - hidden values: `512x346`, `640x480`, `1024x576`, `1280x720`, `1920x1080`
  - visible values: `800x600` and `960x540`

Current intended status:

- Player-facing setting is `Aspect Ratio`, not a full resolution list.
- Old 720p/1080p and other wide saved/runtime values actively migrate to
  `960x540`.
- Old 4:3 values actively migrate to `800x600`.

Cleanup target:

- Keep hidden modes only as migration aliases or dev/runtime diagnostics.
- B11 made the migration/alias role explicit in source; preserve that label.
- Avoid restoring the old visible resolution list.

### Camera Tilt And Extra Zoom

Current locations:

- `Client_Base/src/orsc/RendererExperimentalSettings.java`
  - defaults `cameraTiltEnabled` and `extraZoomEnabled` to true
  - runtime properties/env vars can still override for diagnostics
- `Client_Base/src/orsc/mudclient.java`
  - camera zoom min/max and camera tilt behavior

Current intended status:

- Baseline-on, no longer player-facing experimental toggles.

Cleanup target:

- Rename or document away from "experimental" once the behavior is considered
  permanent.
- Do not re-add player-facing toggles unless a real accessibility or
  compatibility problem appears.

These are active camera behaviors and runtime diagnostic overrides, not active
saved player settings. Old player-facing rows are obsolete; the implementation
is not dead.

### Server Zoom View Toggle

Current locations:

- `Client_Base/src/orsc/Config.java`
  - `S_ZOOM_VIEW_TOGGLE`
- `Client_Base/src/orsc/PacketHandler.java`
  - receives and saves the server config
- `PC_Client/src/orsc/ORSCApplet.java`
  - checks server zoom permission for wheel behavior
- `server/src/com/openrsc/server/ServerConfiguration.java`
  - `ZOOM_VIEW_TOGGLE`
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`
  - sends config slot 15

Current intended status:

- Needs a dedicated audit before removal because it is a server/client config
  path, not only a renderer setting.

Cleanup target:

- Decide whether zoom is now always allowed for Spoiled Milk.
- If yes, migrate the server config into compatibility-only behavior and remove
  the dead conditionals from client input.

### Debug And Capture Flags

Current locations:

- `PC_Client/src/orsc/OpenGLFrameCapture.java`
  - frame/capture artifact emission and burst bookkeeping
- `PC_Client/src/orsc/OpenGLInputBridge.java`
  - capture hotkey delegation
- `PC_Client/src/orsc/OpenGLFramePresenter.java`
  - capture coordination plus remaining renderer/composite debug inputs
- `Client_Base/src/orsc/RendererDebugSettings.java`
  - F6 overlay state and mode

Current intended status:

- F6 debug HUD is acceptable for open-source diagnostics.
- Capture hotkeys and invasive debug toggles should remain disabled in release
  unless explicitly requested.

Cleanup target:

- Capture file writing is already extracted. Move only the remaining
  coordination when it has a clearer owner; do not re-merge it into the
  presenter.
- Centralize debug property names in one class.
- Keep release-default debug behavior obvious.

## World And Scene Cleanup

### Client World

`Client_Base/src/orsc/graphics/three/World.java` should be split by product:

- `WorldSectorCache`
  - sector template loading, section window cache, preload queueing.
- `TerrainModelBuilder`
  - terrain vertex/tile/overlay collection and terrain model emission.
- `WallModelBuilder`
  - wall segments, wall collision/elevation/minimap side effects.
- `RoofModelBuilder`
  - roof coverage, roof elevation workspace, hidden roof products.
- `WorldMinimapBuilder`
  - minimap tile and wall draw logic.
- `WorldCollisionBuilder`
  - collision flag mutation and wall/object collision handling.
- `RendererWorldChunkExporter`
  - build `Renderer3DWorldChunkFrame` and chunk mesh data for OpenGL.

This split matters because shader and shadow work needs terrain/wall/roof
semantics without dragging in minimap, collision, and sector IO.

### Client Scene

`Client_Base/src/orsc/graphics/three/Scene.java` should separate:

- legacy polygon sorting and raster path
- pick/hit testing
- sprite submission and anchors
- renderer-v2 frame export
- camera/frustum state

Renderer-v2 should consume structured scene data instead of reaching through
legacy sort/raster details whenever possible.

## GraphicsController Cleanup

`Client_Base/src/orsc/graphics/two/GraphicsController.java` should split:

- `LegacySurfaceRaster`
  - boxes, lines, circles, strings, sprite plotting, low-level pixel writes.
- `Renderer2DCaptureRecorder`
  - command capture for sprites, text, primitives, rotated sprites, circles.
- `SpriteArchiveLoader`
  - sprite tree loading and archive unpack.
- `SpriteDrawTransform`
  - scaled sprite clipping, masks, alpha thresholds, and transform decisions.

This reduces risk around UI replacement work. It also makes it easier to decide
which legacy 2D paths are still needed once OpenGL owns more of the UI.

## Telemetry Cleanup

`RenderTelemetry.java` is valuable but too broad. Split after renderer classes
are extracted:

- `FrameTimingTelemetry`
- `ClientLoopTelemetry`
- `SceneTelemetry`
- `OpenGLWorldTelemetry`
- `OpenGLSpriteTelemetry`
- `OpenGLShadowTelemetry`
- `AllocationTelemetry`
- `RendererDebugSummary`

Keep one public facade named `RenderTelemetry` if that avoids changing many
call sites. Internally, route to category objects.

## Entity Definition Cleanup

`Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` is a
large non-renderer hotspot. It should eventually split into:

- `ClientDefinitionRegistry`
  - read-only access to items, NPCs, spells, prayers, tiles, doors, textures,
    animations, sprites, and models.
  - Status: B09 extracted registry storage and indexed access. The public
    `EntityHandler` facade and active mutable-list aliases remain for
    compatibility.
- `MyWorldDefinitionOverrides`
  - MyWorld-specific overrides and generated additions.
  - Status: generated `MyWorldItemOverrides` was already separate; broader
    authored/generated-family extraction remains open and must preserve load
    order and every index.
- `PrayerBookDefinitions`
  - Saradomin/Guthix/Zamorak prayer definitions and active book state.
  - Status: done in B09.
- `GeneratedEquipmentDefinitions`
  - staff, cloth, pickaxe, and other generated visual/stat definitions.
  - Status: open.
- `DefinitionFallbackLogger`
  - one-shot fallback logging for missing or placeholder definitions.
  - Status: done as `ClientDefinitionFallbackDiagnostics` in B09 for malformed
    item IDs. NPC/animation/object fallback rules remain registry behavior.

Do not move thousands of authored definitions just to reduce line count. A
future family extraction needs generated-source/load-order/index fingerprints
at least as strict as B09. Keep it behind the five immediate `mudclient`
branches unless definition churn blocks active work.

## Server Cleanup Candidates

These are not the first priority for the renderer push, but they are worth
tracking:

- `Player.java`
  - split persistent state, runtime combat state, social/party/clan state,
    skill state, and UI/session state where possible.
- `Equipment.java`
  - separate slot rules, stat aggregation, item-effect hooks, durability/charge
    helpers, and visual appearance mapping.
  - Status: B10 completed pure slot and stat calculation owners. Mutable
    aggregation order, effects, durability/charges, packets, and appearance are
    still open and require separate behavior characterization.
- `EnchantingItemEffects.java`
  - split jewelry line effects, robe effects, staff effects, charge stores, and
    altar/tier requirements.
- `Summoning.java`
  - split profiles, casting/costs, runtime summon ownership, utility summons,
    combat summons, support upkeep, and UI command handlers.
- `SpellHandler.java`
  - split spell validation, rune costs, combat spell dispatch, utility spells,
    and special/AOE behavior.
  - Status: B10 completed pure classification and validation owners. Rune
    removal, target lookup, combat/teleport side effects, AoE, scheduling, and
    finalization remain in `SpellHandler` by design.
- `ActionSender.java`
  - separate packet writer primitives from gameplay-specific response helpers.

## Refactor Safety Rules

- One ownership extraction per commit when possible.
- Compile after every extraction.
- For renderer-presenter extraction, run at least a client smoke test before
  stacking visual-risk work.
- Keep moved code textually identical first, then improve naming in a second
  pass.
- Do not delete compatibility aliases in the same pass that extracts code.
- Do not convert hidden/debug options into player-facing settings unless the
  design docs explicitly say to.
- When removing stale settings, search launch scripts, packaged configs, server
  configs, and docs first.
- Any change touching sprite ordering, shadow masking, roof visibility, or
  region loading needs visual validation.

## Recommended First Cleanup Pass

The old presenter-first pass is complete through viewport/window extraction.
The first post-B11 branch completed `RendererSettingsPanel` and was merged as
`26145528f`; the second completed `RendererProfileApplier` and was merged as
`83bf64a9d`. The third completed `LegacySoftwareScalingSettings` and was merged
as `20ffd97a8`. The fourth completed `ClientExternalAssetLoader` and was merged
as `6cfaa6ca7`. The fifth completed `ClientSceneInstanceStore` and was merged as
`74e4c4212`.

Do not opportunistically combine these because they are adjacent in
`mudclient`. Each branch must leave a tested owner, keep compatibility visible,
and obtain private visual confirmation before handoff. The five owners are now
stable on `main`. The documentation-only reassessment selected
`PredictiveTerrainPreloader` as the only currently recommended next client
boundary. That focused extraction was implemented, privately verified, and
merged as `2785f1a6f`. The client structural-refactoring track is now paused.
Broad combat visuals, movement interpolation, external-content coordination,
and presenter composite work remain deferred.

## Definition Of Done

This cleanup track is succeeding when:

- `OpenGLFramePresenter` is a frame/GL coordinator with viewport and window
  lifecycle remaining in their B07 owners; success is based on responsibilities
  and dependencies, not a 5,000-line threshold.
- `mudclient.java` no longer owns renderer settings UI, external asset loading,
  profile application, software-scaling state, or game/wall scene instance
  storage directly. Predictive cache-warming decisions and their throttle state
  also have a focused owner, while movement interpolation remains explicitly in
  `mudclient`. Combat-effect sprite management remains a later explicit branch.
- Old scaling/resolution options are either visibly active compatibility
  (`ScaledWindow`, persisted scaler keys, hidden surface aliases) or proved
  obsolete and absent from the player UI.
- Shader source and shader program binding are not embedded in the presenter.
- Shadow classification and shadow mask generation can be reasoned about
  without reading the full presenter.
- Package moves and top-level source-root renames begin only after the five
  planned owners are stable and compatibility facades have been inventoried.
- Future AI sessions can identify the right file for renderer, shader, UI,
  asset, telemetry, and definition work without searching a 16k-25k line file.
