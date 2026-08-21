# Renderer Material-Family Foundation Plan

Status: complete, merged to `main`, and live-accepted; the parent renderer-v2
refinement workstream remains active.

This is the next milestone in the ongoing renderer-v2 refinement workstream on
`feat/renderer-v2-refinement`. It follows the accepted visual/performance,
capture, 2D-capacity, diagnostic-session, and retention-characterization
milestones. It does not create a separate branch or renderer project.

The goal is to establish stable, auditable material-family data before using
that data to change visible Remaster lighting. This milestone must be
parity-preserving by default: it may add metadata, a shader input, diagnostics,
and an opt-in inspection view, but it must not tune the normal material
response yet.

The parent ledgers are
[renderer-v2-plan.md](../in-progress-work-plans/renderer-v2-plan.md),
[renderer-and-shader-roadmap.md](../in-progress-work-plans/renderer-and-shader-roadmap.md),
and
[remaster-lighting-and-shadow-plan.md](../in-progress-work-plans/remaster-lighting-and-shadow-plan.md).

## Current Ownership

Three concepts must remain separate:

- `Renderer3DModelKind` describes geometric/runtime ownership:
  `TERRAIN`, `WALL`, `ROOF`, `GAME_OBJECT`, `WALL_OBJECT`, or
  `UNCLASSIFIED`. It currently drives draw order, culling, terrain-only
  variation, shadows, and telemetry.
- `StaticWorldMaterialPass` describes alpha/render-state behavior:
  `OPAQUE`, `CUTOUT`, `TRANSLUCENT`, `DISCARDED`, or `UNRESOLVED`. It must
  continue to own transparency and pass ordering.
- Texture id and fallback color describe the sampled/baked source material.
  They are not reliable semantic categories on their own.

Resident world chunks already carry per-triangle model kind, texture,
fallback color, terrain-variation eligibility, normals, and glow/shadow
metadata. The shader receives model kind as a vertex attribute, but no stable
semantic material family. Lava/fire glow is separately recognized through
terrain overlay and object-definition heuristics; those existing facts can be
centralized as initial explicit family overrides instead of being rediscovered
inside later polish code.

## Material-Family Contract

Add a shared `Renderer3DMaterialFamily` contract with explicit numeric shader
ids and stable telemetry names. Do not use enum ordinals as serialized/shader
ids. The initial vocabulary is:

- `UNCLASSIFIED`: coverage is present but no trustworthy semantic family is
  known.
- `TERRAIN`: ordinary ground/floor terrain.
- `WATER`: water-like terrain surfaces whose tile semantics are already known.
- `WALL`: boundary/wall material without a more specific trusted override.
- `ROOF`: roof material without a more specific trusted override.
- `SCENERY`: game-object and wall-object material without a more specific
  trusted override.
- `FOLIAGE`: trees, plants, bushes, crops, ferns, and comparable vegetation.
- `ORE`: mineable ore/resource-rock surfaces.
- `EMISSIVE`: already-recognized lava, fire, torch, fireplace, furnace, and
  lava-forge surfaces.
- `EFFECT`: reserved for explicit world effects/portals and later sprite or
  projectile ownership; do not claim broad effect coverage in this milestone.

This first vocabulary intentionally mixes broad safe fallbacks with a small
number of trusted semantic overrides. It is better to report `SCENERY` or
`UNCLASSIFIED` than to guess that an arbitrary texture is wood, stone, or
metal. Substance-level categories can be added later when definition metadata
supports them.

## Classification Rules

Create one centralized classifier with documented precedence:

1. Existing trusted emissive facts override all broad families.
2. Known terrain tile semantics distinguish lava/emissive and water from
   ordinary terrain. Verify the relevant tile-definition values against the
   current world-building behavior before codifying them.
3. Game-object definitions may use stable name/model/command facts to identify
   foliage and mineable ore. Keep these rules centralized and fixture-tested;
   do not scatter string checks through builders or shaders.
4. Model-kind fallback maps terrain/wall/roof/game-object/wall-object to their
   corresponding broad family.
5. Missing or unsafe input resolves to `UNCLASSIFIED`, never `null`.

The classifier must not alter game definitions, protocol payloads, collision,
object animation, chunk readiness, transparency, or draw order. Longer term,
renderer fields can move into authored client/server definition metadata, but
that protocol/data migration is outside this parity foundation.

## Milestone 1: Shared Metadata And Chunk Products

- [x] Add `Renderer3DMaterialFamily` in the shared client renderer contract,
      with explicit shader ids and telemetry names.
- [x] Add material-family metadata to `RSModel`, including copy/clone behavior,
      so resident object chunk construction retains an instance's classified
      family just as it retains model kind and glow metadata.
- [x] Classify resident object instances once, near the existing renderer glow
      metadata application, then let `RSModel` and the chunk builder carry the
      result without consulting definitions again.
- [x] Carry a material family for every world and object triangle through
      `Renderer3DWorldChunkFrame.ChunkMesh`; normalize missing arrays to
      `UNCLASSIFIED` and expose safe per-triangle/copy accessors.
- [x] Include material-family values in world/object chunk signatures and
      buffer identity so a semantic change cannot incorrectly reuse an older
      resident product.
- [x] Keep `Renderer3DModelKind`, `StaticWorldMaterialPass`, texture id, and
      family as distinct fields. Do not replace or overload any existing one.

Acceptance:

- Every resident triangle has a non-null family, and unknown coverage is
  counted explicitly.
- Relog, animated/static object chunk splitting, chunk cache/reuse, and world
  streaming behavior remain unchanged.

## Milestone 2: Shader-Ready Parity Path

- [x] Add a material-family float attribute to resident chunk vertices and a
      matching shader varying using the enum's explicit shader id.
- [x] Preserve the existing per-triangle duplication/attribute rules so one
      triangle cannot inherit another triangle's family through vertex reuse.
- [x] Do not split material batches or add draw calls solely for family;
      family is per-vertex shader data.
- [x] Compile and pass the value through the accepted resident shader without
      changing normal fragment color, alpha, lighting, fog, tone, brightness,
      terrain variation, glow, or shadow results.
- [x] No inspection view was added for this parity milestone; all normal
      profiles therefore keep the existing fragment output unchanged.

Acceptance:

- Default Classic/Remaster output remains visually equivalent before and after
  the metadata path.
- VBO stride/upload growth is measured, while draw-call and texture-bind counts
  do not increase because of family ownership.

## Milestone 3: AI-Readable Coverage

- [x] Add current/recent material-family triangle counts to expanded F6 and
      structured renderer diagnostic telemetry.
- [x] Record family coverage for resident chunk products in `Ctrl+F9` evidence,
      including model kind, family, texture/fallback identity, chunk role, and
      triangle count. Keep the existing projected-world material-pass capture
      intact rather than conflating the two contracts.
- [x] Extend the capture/session analyzer to report family totals, unknown
      coverage, and contradictory combinations without treating
      `UNCLASSIFIED` as a crash or silently dropping it.
- [x] Add guardrails for stable shader ids, classifier precedence, complete
      chunk-array lengths, signature participation, capture schema, and
      diagnostic-disabled behavior.

Initial contradictions worth flagging—not automatically failing—include an
emissive family with no known glow/emissive source, a foliage/ore family on a
non-object model kind, or water on a non-terrain model kind. The analyzer must
distinguish a suspicious classification from proven incorrect rendering.

Acceptance:

- An AI can answer how many resident triangles belong to each family, which
  rules produced special families, and where classification remains broad or
  unknown without inspecting shader code or guessing from screenshots.

## Validation Route

- [x] Client compiles with Java 8.
- [x] Full renderer guardrail suite passes.
- [x] Classifier fixtures cover broad fallbacks plus representative water,
      foliage, ore, fire/torch/furnace, lava, and unknown inputs.
- [x] A strict `Ctrl+F9` capture has complete family coverage with no missing
      triangle metadata or capture artifacts.
- [x] Default-output screenshots/capture layers show no material-driven visual
      change in terrain, water, walls, roofs, foliage, ore rocks, ordinary
      scenery, lava/fire, sprites, shadows, or UI.
- [x] Dense-route telemetry shows no new draw calls/texture binds, no material
      regression in render or client-loop p95, and bounded VBO/direct-memory
      growth consistent with one additional attribute.
- [x] Relog and section/teleport transitions retain correct resident family
      coverage without stale chunk reuse.

## Live Validation Findings — 2026-07-10

- User visual review accepted the default Remaster output after travel through
  dense scenery/entity coverage. Terrain, water, scenery, foliage, ore,
  emissive surfaces, sprites, shadows, animations, and UI showed no visible
  regression. The expanded F6 family line was populated and readable.
- All 12 frames in diagnostic burst `session-20260710-210116-3032922` passed
  strict analysis. Every frame reported `260,032/260,032` resident triangles,
  `798` unique aggregate rows, and zero missing triangles, duplicate rows,
  invalid ids, contradictions, or unclassified coverage. The stable family
  totals were terrain `42,384`, water `244`, wall `6,036`, scenery `116,956`,
  foliage `83,440`, ore `2,322`, and emissive `8,650`; roof/effect were absent
  from this active resident scene rather than unclassified.
- The non-capture session had zero slow-frame and client-exception events.
  Client-loop p50/p95/p99 was `16.666/17.701/18.026ms`; OpenGL render was
  `9.395/10.581/13.322ms`. On report windows with the same `260,032` resident
  triangles as the earlier accepted baseline, world-render p50/p95 was
  `7.361/8.120ms` versus `7.187/7.491ms`, while the new run generally drew a
  denser visible triangle subset. Draw calls remained within `486..500` versus
  `481..536`, and texture binds remained exactly `1`.
- The new float raises the resident vertex stride from `108` to `112` bytes
  (`3.7%`, or `3,120,384` bytes for `780,096` resident vertices). JVM direct
  buffers settled into a bounded approximately `166..181MiB` sawtooth late in
  the dense/capture session rather than showing monotonic growth. Continue the
  already-established passive retention monitoring; this evidence does not
  justify a memory/cache change.
- Eleven section loads retained complete family coverage. A live logout/login
  cycle was then validated in `session-20260710-211104-3038022`: two login
  epochs separated by one logout reached `258,404` and `260,032` fully
  classified resident triangles respectively. The second epoch crossed another
  section boundary and settled at `213,350` triangles with a new stable family
  distribution. All observed resident states kept `UNCLASSIFIED=0`; the
  session recorded zero slow frames or client exceptions, and the user
  accepted visuals before and after relog.

## Explicitly Deferred

- Different diffuse/specular/saturation/contrast/fog responses by family.
- Water animation, refraction, reflection, or transparency changes.
- Foliage translucency/wind, ore sparkle, metal response, bloom, or heat
  shimmer.
- Moving the renderer-specific family field into server/authentic-client
  protocols or bulk editing the definition XML.
- Full material families for players, NPCs, ground items, sprites,
  projectiles, spell effects, particles, and UI. Those need their own explicit
  world-sprite/effect data contract rather than being guessed from resident
  static triangles.
- Removing the existing glow mask or changing shadow caster/receiver behavior.

## Completion Rule

This milestone is complete only when material family is carried from trusted
classification through resident products and shader input, coverage is
machine-auditable, default output remains parity-safe, and live performance is
accepted. Finish with a pushed checkpoint and keep the renderer workstream
ACTIVE. Do not hand off, merge, recycle, or create another branch without an
explicit final-roundup instruction.
