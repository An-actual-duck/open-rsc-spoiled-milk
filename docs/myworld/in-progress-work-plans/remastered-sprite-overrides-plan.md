# Remastered Sprite Override Plan

Status: active foundation plan for AI-3 implementation and iterative visual
testing. Phase 0 architecture review began on 2026-07-11 against commit
`a2db9c82f`.

## Goal

Add an optional client-side sprite path that can display curated remastered PNG
art alongside the existing sprite system. The original archives and current
runtime sprites remain canonical. When the player enables remastered sprites,
the client uses a valid remastered replacement when one exists and falls back
to the exact original sprite for every missing or invalid replacement. When the
option is disabled, behavior must remain identical to the original path.

This plan deliberately permits partial coverage. A remaster pack does not need
to replace every item, character, object, interface image, or animation before
it is useful.

## Boundaries

- Do not replace, rewrite, or delete `Custom_Sprites.osar` or the authentic
  sprite archives.
- Do not reuse `Config.S_WANT_CUSTOM_SPRITES` as the player option. That flag
  selects an existing server/client sprite source and is a separate concern.
- Do not fold the combat VFX work in
  [`animation-asset-migration-plan.md`](animation-asset-migration-plan.md) into
  this project. Both systems may use PNGs, but their identities and rendering
  contracts differ.
- Let the authored workspace organize future texture replacements, but do not
  route textures through the ordinary sprite resolver. Texture runtime support
  has its own adapter and cache-invalidation phase.
- Treat collaborator drops such as `bobo-resprites/` as valuable migration
  inputs. Preserve attribution and Git history while converting flat/raw IDs
  into stable named sets; do not maintain parallel permanent sources.
- Do not require the server or network protocol to know which art style a
  player selected.
- Do not decode PNGs, read files, or emit repeated missing-asset messages from
  the render loop.
- Do not change gameplay bounds, click targets, animation timing, or sprite
  placement merely because replacement art has a different source resolution.
- Begin with packaged, curated assets. Loading arbitrary external mod folders
  can be considered later as a separate security and support decision.

## Terminology

- **Canonical sprite**: the existing sprite produced by the current archive,
  custom-sprite, or generated runtime path.
- **Remastered override**: an optional PNG plus the metadata needed to render
  it in place of one canonical sprite or frame.
- **Resolved sprite**: the canonical sprite or valid remastered override chosen
  for the current player setting.

Documentation and UI should use `remastered` or `override`, not the already
overloaded term `custom sprites`.

## Asset and Catalog Layout

Use a dedicated authored workspace beside the existing `sprites/` and
`animations/` trees:

```text
dev/myworld/assets/authoring/remastered-sprites/
  README.md
  CREDITS.md
  _schema/
  _templates/
  incoming/
    <contributor>/<batch>/
  npcs/
    demon/
      set.json
      frames/
        00.png
        01.png
        ...
        17.png
      source/
      work/
  players/
  equipment/
  items/
  textures/
  ui/
  static/
  manifest.json                 # generated; do not edit by hand
```

This root is the long-term source of truth for remastered art. It belongs under
`dev/myworld/assets/` because it is an authored distributable-asset workspace,
but it should not be nested inside today's `sprites/` or `animations/` runtime
layouts. Keeping it as their sibling gives collaborators one predictable drop
location while avoiding a mix of canonical project PNGs, combat VFX, source
files, drafts, and optional remaster overrides.

The primary navigation is art identity, not raw cache ID or contributor name.
Related frames live together. A demon submission belongs under `npcs/demon/`;
player body frames might live under `players/body1/`; a hatchet set under
`equipment/hatchet/`; and a single item icon under a named leaf such as
`items/weapons/dragon-sword/`. Category folders may gain sensible nested
families, but every independently reviewable leaf has one `set.json`.

Use lowercase kebab-case for authored directories and zero-padded frame names.
Raw cache IDs may be recorded as migration metadata, but must not be the only
human-facing filename after classification. The stable runtime key remains the
actual identity; the friendly folders are a navigation and authoring layer.

### Set folder contract

Each leaf set is self-describing and independently reviewable:

- `set.json`: stable targets, frame mapping, expected canonical metadata,
  status, contributor/provenance, policies, and notes;
- `frames/`: validated runtime-ready PNGs only; this is the only leaf image
  directory eligible for release packaging;
- `source/`: optional distributable editable masters or original sheets;
- `work/`: incomplete crops, experiments, or not-yet-approved frames; never
  packaged; and
- generated comparisons, contact sheets, and reports go under
  `output/art/remastered-sprites/`, never back into the authored set.

An 18-frame set should normally use `frames/00.png` through `frames/17.png`.
The descriptor may instead declare a spritesheet plus rectangles when that is
the actual authored source, but individual numbered frames are preferred for
drop-in review and per-frame fallback. A missing frame is legal only when the
descriptor explicitly declares partial coverage; an accidental numbering gap
is a validation error.

`incoming/<contributor>/<batch>/` is temporary classification space for flat
or externally named deliveries. It preserves an untouched incoming batch while
the importer produces a mapping report, but it is never a second runtime source
and is never packaged. After a reviewed promotion, move the accepted files into
named sets and remove the redundant incoming copy in the same checkpoint. Git
history and `set.json` retain the original batch attribution.

### Distributed descriptors and generated catalog

A drop-in workflow should not require hand-editing one ever-growing central
manifest. The authoritative declarations are the distributed `set.json` files.
A deterministic generator scans those descriptors and produces:

- the root `manifest.json` used for audits and packaging;
- a generated Java catalog data class used by the client; and
- a coverage/inventory report under `output/art/remastered-sprites/`.

The generated root manifest and Java data must be committed or regenerated in
the established project workflow and checked for drift. Never author the same
entry independently in a leaf descriptor and the root catalog.

The current Ant build packages PNGs only from `sprites/` and `animations/`, so
Phase 1 must add an explicit remastered-assets fileset. Package only generated
catalog data and approved `frames/` resources beneath
`myworld-assets/remastered-sprites/`; exclude `incoming/`, `source/`, `work/`,
templates, and local reports.

The desktop client has no general JSON library other than unrelated Discord
runtime code, so Phase 1 should not add an ad hoc render-time parser or a large
dependency. Use the repository's established generated-data pattern:

1. a small build/developer tool validates every `set.json` and generates the
   deterministic root manifest and Java catalog data class;
2. the generated class is committed and checked for drift by automation;
3. runtime loads the generated catalog without parsing arbitrary JSON; and
4. the generated root manifest is packaged for auditing, release validation,
   and future tooling.

If implementation evidence strongly favors a small dependency-free runtime
format instead, record that decision here before changing the source of truth.
Do not maintain two independently authored catalogs.

PNG pixels are not enough to reconstruct the current `Sprite` contract. Each
set descriptor must identify its logical target(s), frame files, policies, and
provenance. The generator expands that compact authoring declaration into
per-frame catalog entries and records the metadata relevant to each canonical
sprite, including:

- stable sprite key and PNG path;
- optional sheet frame, row, and column information;
- expected canonical pixel width/height, `requiresShift`, X/Y shift, and bound
  width/height;
- category and any scale or anchor policy;
- palette-mask/recolor policy where equipment or character art needs it.

Canonical placement metadata should be inventoried, inherited, and copied
automatically, not hand-authored across eighteen frame records. The expected
values emitted into the generated manifest are compatibility assertions: if
the runtime canonical frame no longer matches them, reject only that override
and use the runtime canonical frame. This also protects players whose active
`.osar` sprite pack changes a frame contract.

A compact Demon `set.json` should look broadly like:

```json
{
  "schemaVersion": 1,
  "setId": "npc-demon",
  "displayName": "Demon",
  "status": "ready",
  "contributor": {
    "id": "thatkidbobo",
    "displayName": "ThatKidBobo",
    "sourceCommit": "abd9d08fc23cb4752d3389ed127d388af2d18b2b",
    "provenance": "project-contribution"
  },
  "targets": [
    {
      "keyPrefix": "sprite/npc/demon",
      "frames": {
        "pattern": "frames/%02d.png",
        "first": 0,
        "count": 18
      },
      "sizePolicy": "exact-canonical",
      "recolorPolicy": "inherit",
      "alphaThreshold": 128,
      "legacySourceRange": {"first": 864, "last": 881}
    }
  ]
}
```

The generated catalog expands that declaration into keys
`sprite/npc/demon/0` through `sprite/npc/demon/17`, concrete resource paths,
and per-frame expected canonical contracts. `legacySourceRange` documents an
incoming numeric range; it is not used for runtime lookup.

A descriptor may use explicit frame maps or a `sourceRect` sheet declaration
instead of the pattern form. The generator must reject duplicate keys, invalid
or overlapping declarations when overlap is forbidden, out-of-bounds
rectangles, unknown policy values, orphan ready PNGs, and paths outside the
remaster root. One PNG may intentionally serve more than one key.

Catalog paths must be normalized beneath the packaged remaster root. Reject
absolute paths and traversal rather than allowing a descriptor or generated
catalog to read an arbitrary local file.

### Existing Bobo contribution as the first migration batch

`dev/myworld/assets/bobo-resprites/` is the reason this workspace is now
needed, not disposable clutter. Commit `abd9d08fc` added `198` improved PNGs
from ThatKidBobo with shading/color improvements based on the existing art:

| Incoming area | Files | Inventory result |
| --- | ---: | --- |
| `entity-animations/` | 90 | five complete 18-frame groups |
| `items-inventory-ground/` | 85 | all map to current `items` entries |
| `textures/` | 23 | all map to archive entries; 21 have active texture definitions |

The five flat entity ranges map by dimensions, visible-pixel shape, and current
archive identity as follows:

| Incoming IDs | Named destination | Stable target |
| --- | --- | --- |
| `27-44` | `players/body1/` | `sprite/player/body1/0-17` |
| `54-71` | `players/legs1/` | `sprite/player/legs1/0-17` |
| `108-125` | `players/fbody1/` | `sprite/player/fbody1/0-17` |
| `864-881` | `npcs/demon/` | `sprite/npc/demon/0-17` |
| `1674-1691` | likely `equipment/hatchet/` (review required) | provisional `sprite/equipment/hatchet/0-17` |

The first four mappings are high-confidence archive matches. The final group
visually and by mask similarity favors `hatchet`, but another equipment entry
shares its dimensions. Keep that target provisional until a side-by-side
contact sheet is approved; the migration is also a regression fixture proving
that classification reports ambiguity rather than silently choosing a target.

The item files use authentic array IDs. Their logical item entry is currently
`raw ID - mudclient.spriteItem (2150)`; the texture files similarly map through
`raw ID - mudclient.spriteTexture (3225)`. Those offsets belong only in import
metadata/tooling. Promoted set descriptors use stable logical keys.

All 90 grouped frames and all 23 textures match their canonical pixel
dimensions. Texture entries `57` and `58` are present in the custom archive but
do not currently have active `TextureDef` records; keep them staged and report
their dormant status instead of treating them as runtime proof assets.

Of the 85 item images, 84 match exactly. Incoming `id-02262.png` (logical
`items:112`) is `36x22` while the current canonical frame is `35x22`; keep that
file in `work/` until the one-pixel difference is deliberately resolved or a
later size policy supports it. Many logical item sprites are intentionally
shared by multiple definitions and palette masks. The classifier/report must
list every current `ItemDef` alias, then use a visual-family folder name such
as `items/weapons/mace/` rather than misleadingly naming a shared sprite after
only its first item.

Migration should use reviewed `git mv` operations into the named workspace so
history remains useful. Do not keep `bobo-resprites/` and
`remastered-sprites/` as parallel permanent sources. Preserve Bobo's identity,
source commit, and contribution terms in every promoted set descriptor and the
asset credits. Future Bobo drops follow the same incoming -> classify -> named
set -> validate -> promote workflow.

## Stable Sprite Identity

The resolver needs a stable key before a large art library is authored. Phase
0 inventory found that the normal Spoiled Milk client uses
`Custom_Sprites.osar` with `Config.S_WANT_CUSTOM_SPRITES = true`. The archive
currently contains `11` subspaces, `1,002` entries, `812` one-frame sprites,
and `190` animations:

| Subspace | Entries | Current contract |
| --- | ---: | --- |
| `clipping` | 1 | static sprite |
| `crowns` | 12 | static sprites |
| `equipment` | 154 | 2 entries with 15 frames, 151 with 18, 1 with 27 |
| `GUI` | 44 | static sprites |
| `GUIutil` | 10 | static sprites |
| `items` | 651 | static sprites |
| `npc` | 28 | 15-, 18-, or 27-frame animations |
| `player` | 8 | 18-frame animations |
| `projectiles` | 7 | static sprites |
| `skill_icons` | 20 | static sprites |
| `textures` | 67 | renderer materials; workspace-supported, excluded from sprite resolver v1 |

All current subspace and entry names fit `[A-Za-z0-9._-]+`, but the key parser
must still reject separators and traversal instead of assuming future names
remain simple.

### Selected version-1 key grammar

Use logical definition identity, not the physical array slot from whichever
archive happens to be active:

```text
sprite/<lowercase-subspace>/<lowercase-entry>/<zero-based-frame>
```

Examples:

- `sprite/items/273/0`
- `sprite/gui/7/0`
- `sprite/equipment/sword/15`
- `sprite/npc/dragon/4`

For `ItemDef` and `SpriteDef`, derive the subspace and entry from
`spriteLocation`. For `AnimationDef`, use `category`, `name`, and the requested
frame offset. This remains stable across custom and authentic sources and
correctly shares one override where multiple item definitions intentionally
alias the same visual. Type and equipment layer are compatibility metadata,
not part of the key.

The authentic item offset (`mudclient.spriteItem + spriteID`) is used only to
obtain the canonical fallback. It must never appear in a public remaster key.
Incidental animation `number` and render-array indices are also not keys.

Reserve a separate `texture/<lowercase-entry>` namespace for the texture
workspace, for example `texture/1`. Do not pretend a texture is an ordinary
`Sprite` merely because the custom OSAR stores its source pixels in a sprite
frame. Texture replacement has different transparency, atlas, resident chunk,
and live-reload responsibilities and receives its own adapter phase below.

### Source precedence

Canonical resolution already has multiple layers. Preserve this order and put
the remaster decision above it:

1. valid packaged remastered override, when enabled;
2. existing project PNG injection such as external equipment or an
   `external-png:` item;
3. active `.osar` sprite-pack replacement;
4. `Custom_Sprites.osar` or the existing authentic-array path; and
5. existing unknown-sprite behavior only where the current canonical path
   already uses it.

In practice external equipment is installed after active sprite packs today,
so Phase 1 must bind canonical frames only after `loadSprites()` has completed.
The exact fallback is the final canonical object after all existing layers,
not necessarily the base OSAR frame.

### Supported adapters and exclusions

Version 1 should adapt the three normal selection paths:

- `ItemDef` -> logical `spriteLocation` -> frame `0`;
- `SpriteDef` (GUI, crowns, projectiles) -> logical `spriteLocation` -> frame
  `0`; and
- `AnimationDef` -> category/name/requested frame.

The following need explicit later adapters and must not be silently treated as
covered by the foundation:

- generated altar glyph/orb sprites;
- spell, prayer, and summon icon arrays loaded from project PNGs;
- combat-effect, projectile-effect, mirrored, impact, and scaled derived
  sprites owned by the separate animation migration system;
- captcha and network-created images;
- the runtime minimap image and other generated buffers; and
- texture runtime replacement, terrain materials, models, walls, and scenery.
  Texture artwork is organized in this workspace, but RSC textures and scenery
  are not ordinary sprite resolver entries.

`Panel` stores the appearance-screen arrow `Sprite` objects at panel creation.
Those twelve cached controls are the known retained-reference exception to
otherwise per-draw selection. A live toggle must rebuild/rebind that panel or
resolve at its draw boundary. Add a regression test so this exception does not
leave mixed art after toggling.

The original minimum identity requirements remain useful guardrails:

- archive/array sprites by source subspace and canonical identifier;
- item icons by their real item sprite identity, respecting the
  `mudclient.spriteItem + spriteID` authentic offset described in
  [`client-sprite-reference.md`](../info/client-sprite-reference.md);
- sprite-tree/archive animation frames by subspace, entry, type/layer, and
  frame index, or an equally stable named identity;
- generated or runtime-only sprites that cannot safely be overridden yet.

Do not treat an incidental render-array index as a universal identity unless
the inventory proves that index is stable and namespaced. Different sources
may legitimately use the same numeric ID.

## Authoring Workspace Lifecycle

The clean workspace is a first-class deliverable, not incidental support code.
An artist or maintainer should be able to understand where a new drop belongs,
what is ready, what is incomplete, and why an asset maps to a game identity
without opening `mudclient.java`.

### Normal drop-in workflow

1. Place an untouched delivery under
   `incoming/<contributor>/<yyyy-mm-dd-or-batch-name>/`.
2. Run an inventory/classification command. It reports image dimensions,
   consecutive ranges, likely canonical matches, collisions, and ambiguous
   mappings without moving files.
3. Review ambiguous results. Raw IDs and dimension matches are evidence, not
   permission to guess an identity.
4. Scaffold or select the named leaf set, such as `npcs/demon/`.
5. Move accepted files with `git mv` into `frames/` if runtime-ready or `work/`
   if incomplete. Normalize them to numbered/named files without discarding
   the incoming IDs recorded in `set.json`.
6. Complete contributor, source commit/batch, provenance, target key, policies,
   and notes in `set.json`.
7. Run set validation and generate the root manifest/catalog.
8. Generate canonical/remastered contact sheets and a coverage report under
   `output/art/remastered-sprites/` for human review.
9. Checkpoint the coherent set and remove its redundant `incoming/` copy. A
   failed or uncertain classification remains in incoming/work and never
   becomes a silent runtime override.

For an already-classified set, future work is simpler: drop the updated
numbered frames into that set, validate, regenerate, review the contact sheet,
and checkpoint. Updating Demon should not require touching unrelated item or
texture descriptors.

### Tooling surface

Provide one documented wrapper, tentatively
`./scripts/remastered-sprites.sh`, backed by focused tooling under
`tools/myworld/remastered-sprites/`. It should support at least:

- `inventory`: report canonical sprite/texture identities and current coverage;
- `classify <incoming-path>`: propose mappings without mutation;
- `scaffold <category> <set-name>`: create a descriptor and standard folders
  from `_templates/`;
- `validate [set-path]`: validate one set or the complete workspace;
- `generate`: deterministically rebuild root manifest and Java catalog data;
  and
- `report`: write coverage, orphan, size, provenance, and package-delta reports
  under `output/art/remastered-sprites/`.

The classifier should understand known legacy offsets only as import adapters,
then confirm candidates with canonical dimensions, alpha/visible-pixel bounds,
frame counts, and where useful mask similarity. It must report multiple
possible matches rather than auto-promoting an ambiguous set such as two
equipment entries that share dimensions.

For items, classification output includes all `ItemDef` IDs/names, picture
masks, and notes/certificate aliases that use the logical sprite. For textures,
it includes `TextureDef` data/animation names and clearly marks archive entries
without a current runtime definition.

### Workspace invariants

- One ready PNG belongs to exactly one declared set unless intentional reuse is
  explicit.
- Every `frames/` PNG is declared; every declared runtime PNG exists.
- Drafts, editable sources, incoming deliveries, and generated reports are not
  player-package inputs.
- No contributor-named folder becomes a permanent runtime namespace;
  attribution lives in metadata and credits.
- No raw ID is accepted as the sole stable target.
- Set status is explicit (`incoming`, `work`, `ready`, `retired` or the final
  validated equivalent), and only ready entries enter the generated runtime
  catalog.
- Generated files are deterministic and fail drift checks instead of silently
  changing during an ordinary client build.
- Case, path, frame numbering, orphan, duplicate, provenance, and canonical
  contract errors are actionable and identify the leaf set.
- Git history is preserved during reorganization; do not delete and recreate a
  collaborator's files when `git mv` expresses the migration.

The workspace README must include the folder tree, the normal drop-in workflow,
descriptor examples for an NPC animation, static item, and texture, command
reference, status meanings, provenance rules, and how to recover from a failed
validation without bypassing it. `CREDITS.md` provides the human-readable
collaborator/batch index, while `set.json` remains the machine-verifiable
per-set record. Update `dev/myworld/assets/README.md` and
`release/player/ASSET-SOURCES.txt` when promoted art changes their inventories;
do not let the remaster workspace become an unreferenced credit island.

## Runtime Architecture

Keep the feature out of `mudclient` as much as the inherited architecture
allows. Target these ownership boundaries:

- `RemasteredSpriteSettings`: persisted setting, runtime override for
  diagnostics, and profile-bundle behavior;
- `RemasteredSpriteKey`: normalized, validated key value;
- generated `RemasteredSpriteCatalogData`: declarations only;
- `RemasteredSpriteCatalog`: runtime validation and immutable entry lookup;
- `RemasteredSpriteResolver`: canonical binding, lazy decode/cache, fallback,
  counters, and one-shot failure reporting; and
- a small reusable packaged-asset reader extracted from or shared with the
  existing `mudclient` helpers instead of duplicating JAR/filesystem probing.

The runtime sequence is:

1. Load the player setting with the other desktop client settings.
2. Load and retain canonical sprites through the unchanged existing paths.
3. After sprite packs and existing project PNG injections finish, bind logical
   keys to the final canonical `Sprite` objects and their contracts.
4. Load the generated catalog declarations into an immutable lookup.
5. Resolve through the `ItemDef`, `SpriteDef`, and `AnimationDef` adapters,
   passing the canonical object as the guaranteed fallback.
6. When enabled and a compatible entry exists, return one immutable cached
   replacement object. Otherwise return the exact canonical object.
7. Make software and OpenGL rendering consume that same returned `Sprite` so
   the two renderers cannot drift.

Do not mutate canonical pixel arrays or install replacement pixels into
`spriteTree`. Canonical and remastered objects must have distinct identities.
The OpenGL texture cache already keys by `Sprite` object identity plus color
transform, so a normal on/off toggle can safely retain separate atlas entries
without returning stale pixels. Toggle itself should not tear down the whole
OpenGL presenter. A catalog reload or replacement-object revision does need an
explicit frame-boundary cache retirement strategy so abandoned atlas entries
do not accumulate forever.

The implementation must not destructively overwrite the only copy of the
canonical sprite. A player must be able to return to original art without
restarting or re-reading all original archives. Cache keys must account for
catalog revision where object identity alone is insufficient. Toggling the
option must rebind the retained `Panel` references described above and any
later software-side derived caches.

Load or decode remastered PNGs at startup, during a controlled asset reload,
or lazily once per catalog entry. Never perform filesystem I/O or image decode
once per frame. Use a bounded cache if loading the complete catalog would be
materially expensive.

Use an immutable catalog/resolver state published in one swap. If a future
developer reload command is added, build and validate the next state away from
the active render state, then swap it at a safe client-frame boundary. A failed
reload keeps the previous valid state; it must not leave a half-populated
catalog active.

## Player Setting

Add a persisted client-local option labelled
`Sprites: Classic/Enhanced`, under the most discoverable graphics or
interface settings group. Default it to **Off** while coverage is experimental.
It is independent of the server and other players.

The setting is independent in storage but participates in renderer presets:

- choosing `Classic` sets remastered sprites **Off**;
- choosing `Remaster` sets remastered sprites **On** once the proof is approved
  for private testing;
- changing the row manually marks the displayed renderer preset `Custom`; and
- choosing `Custom` does not itself alter the current sprite setting.

Until the proof is approved, both a new installation and an existing saved
Remaster profile should remain Off unless the player explicitly enables the
row. Record the exact promotion checkpoint here rather than changing the
default incidentally.

The target behavior is an immediate live toggle. If the first proof requires a
controlled renderer/asset reload, make that explicit to the player and keep it
safe; do not silently leave half of the caches in the previous mode.

## Pixel, Metadata, and Rendering Contracts

### Transparency

The current `Sprite` pipeline is not true ARGB. It stores RGB pixels with zero
as the transparent key; software draw loops test zero, and OpenGL uploads every
nonzero sprite pixel with alpha `255`. The unused `transparentPixels` field is
not an alpha channel.

Therefore the safe foundation contract is binary transparency:

- decode the PNG as ARGB in a controlled loader;
- treat alpha below the catalog threshold as transparent zero;
- treat alpha at or above the threshold as opaque RGB;
- normalize opaque black away from zero, using the existing
  `RendererTransparency.OPAQUE_BLACK_REPLACEMENT` rule; and
- default the set/catalog threshold to `128` unless visual testing justifies a
  per-entry value.

Do not claim that fractional PNG alpha is preserved. Assets in the first proof
should use intentional hard transparency or antialiasing authored for the
binary threshold. True fractional-alpha sprites require a separate shared
`Sprite`/software/OpenGL contract extension and parity tests before the
catalog may declare such a mode.

### Dimensions and placement

The legacy scaled draw routines conflate source pixel dimensions with logical
frame/bound dimensions in several places. Simply attaching a 2x PNG to copied
shift/bound metadata can double destination coverage or change unscaled UI
layout. Version 1 therefore supports `sizePolicy: exact-canonical` only:

- the decoded frame dimensions must exactly equal the canonical frame's pixel
  dimensions;
- `requiresShift`, shift X/Y, bound width/height, ID, and package metadata are
  copied from the canonical frame; and
- generated expected-canonical fields must match before the override is valid.

This restriction is a foundation safety rail, not the final remaster ambition.
Before accepting higher-resolution art, add an explicit logical-source-size
contract to `Sprite`/draw commands or prove a category-specific resampling
policy. Test unscaled UI draws, scaled item draws, shifted entity layers,
mirroring, clipping, hit targets, and OpenGL command-sized texture building.
Never silently downsample master artwork in place; generated runtime renditions
must remain reproducible from the authored PNG.

### Recoloring

Version 1 supports `recolorPolicy: inherit`: the replacement receives the same
item/equipment/player mask transform exactly once. Mask-authored replacements
must preserve the grayscale and blue-mask regions expected by existing draw
code. For a static path that provably receives no recolor arguments,
`final-color` may be accepted later as an explicit no-op policy.

Do not enable final-color equipment or character art in the foundation. That
requires the resolved result to carry policy beside `Sprite` through both the
software mask loops and `RendererSpriteTransform`; returning only a different
pixel object is not sufficient. Reject unsupported policy/key combinations at
catalog generation and runtime validation.

### Fallback rules

- Resolve per sprite and per frame. A partially remastered animation may use
  its original frame wherever the corresponding PNG is absent.
- A missing file, malformed PNG, invalid frame declaration, or incompatible
  metadata logs one useful diagnostic and falls back to the canonical sprite.
  It must not crash, show a checkerboard, or hide the sprite.
- Disabled mode should avoid probing or decoding remastered assets except for
  any minimal catalog work required by the settings UI.
- Decode PNGs as ARGB input, then apply the versioned transparency contract
  above. Never interpret visible black as transparency.
- Keep logical dimensions, shifts, anchors, and bounds separate in the
  catalog model even while version 1 requires matching source dimensions.
- Never return an unknown/checkerboard sprite because only the remaster entry
  failed. Return the exact canonical object supplied to the resolver.

## Asset Safety, Provenance, and Budgets

Even curated packaged data needs deterministic validation:

- release builds resolve remaster art only from the embedded catalog-declared
  resources; a development build may prefer the matching repository path for
  iteration, but must not scan arbitrary user folders;
- normalize separators and require paths relative to
  `myworld-assets/remastered-sprites/`;
- reject absolute paths, `..`, empty segments, control characters, and
  case-colliding keys/files that behave differently on Windows and Linux;
- read and validate PNG headers before full `ImageIO` decode;
- cap width, height, total pixels, sheet frames, catalog entries, and estimated
  decoded bytes with named constants and actionable errors;
- reject duplicate keys and files missing from the packaged JAR;
- make generated catalog order stable; and
- make invalid entries fail individually while structural catalog corruption
  fails the new catalog/reload as a whole.

Every shipped PNG needs redistribution permission and a provenance/credit
entry consistent with `dev/myworld/assets/README.md` and
`release/player/ASSET-SOURCES.txt`. Each set descriptor should carry a required
credit/provenance identifier, including an explicit project-author identifier
for original work, so validation can reject uncredited release art.
Do not commit speculative or license-unclear proof images merely to exercise
the loader; tests can generate temporary fixture PNGs.

Establish baseline budgets before broad coverage:

- catalog parse/generation time;
- startup or first-use decode time;
- decoded CPU bytes and OpenGL atlas allocation growth;
- packaged compressed/uncompressed size; and
- toggle/reload frame-time spike.

The proof does not need aggressive limits, but diagnostics and tests must make
growth visible before hundreds of frames are added.

## Diagnostics

Expose compact, AI-readable counters through the existing
`RendererDiagnosticSession` records and F6 diagnostic capture rather than
regular per-frame logs:

- catalog entries and successfully loaded replacements;
- remastered resolutions and canonical fallbacks;
- missing, invalid, or metadata-incompatible entries;
- cache hits, misses, memory estimate, and reload count;
- active art-mode setting and renderer backend.

Each invalid catalog entry should also produce one actionable startup/reload
message containing its stable key and reason.

Distinguish `not-declared` from `declared-but-invalid`: normal partial coverage
must not flood logs or inflate error counts. Track fallback reasons in bounded
counters, and retain only a bounded sample of keys for diagnostics. Add the
catalog schema version, revision, and generated-data signature so captures can
be compared to the exact asset set.

## Implementation Phases

### Phase 0: Inventory and key contract

- [x] Trace the canonical archive, sprite-array, sprite-tree, item, equipment,
  software-renderer, and OpenGL texture paths.
- [x] Record the initial archive counts, stable key grammar, precedence, and
  unsupported categories in this plan.
- [x] Confirm where settings persistence, preset behavior, retained panel
  references, and OpenGL texture identity belong.
- [ ] Add a repeatable inventory/audit tool that emits the archive counts,
  frame contracts, duplicate logical identities, and manifest coverage without
  changing assets.
- [ ] Verify every current `ItemDef`, `SpriteDef`, and `AnimationDef` logical
  location maps to the selected grammar, including aliases and authentic-mode
  fallback fixtures.
- [ ] Decide the initial named safety limits and generated catalog package.
- [ ] Select the smallest useful proof sprites with the owner before producing a
  large art set.

### Phase 0B: Authoring workspace foundation

- [x] Create `dev/myworld/assets/authoring/remastered-sprites/` with its README, schema,
  templates, category folders, incoming boundary, and output rules.
- [x] Add its human-readable contributor index and connect it to
  `dev/myworld/assets/README.md`.
- [x] Add the promoted remaster credit to the release asset-source inventory
  when the player package first begins shipping remastered PNGs.
- [x] Add the documented inventory, classify, scaffold, validate, generate, and
  report command surface before building a large runtime catalog.
- [x] Import the existing Bobo contribution as the first real classification test.
  Preserve its original commit and contributor metadata.
- [x] Migrate the four confirmed animation groups with reviewed `git mv`
  operations and per-set descriptors. Resolve the fifth group against the
  provisional `hatchet` mapping before promoting it from work to ready.
- [x] Map the 85 raw item IDs to stable `sprite/items/<entry>/0` sets. Keep
  `id-02262.png` in work until its one-pixel width mismatch is resolved.
- [x] Organize the 23 texture images into named or ID-audited texture sets with
  `texture/<entry>` targets, but keep them non-runtime/staged until the texture
  adapter phase.
- [ ] Generate the Java catalog data and canonical/remastered contact sheets.
  The aggregate JSON manifest and workspace report are implemented and repeat
  generation is byte-stable.
- [x] Add workspace guardrails before retiring the now-redundant
  `bobo-resprites/` path through reviewed `git mv` operations, then checkpoint
  the clean migration as its own milestone.

Workspace foundation result on 2026-07-11: `113` leaf descriptors account for
all `198` contributed PNGs and legacy IDs. `88` sets are authoring-ready (`156`
per-frame manifest entries); `25` sets remain deliberately in `work` (the
18-frame provisional hatchet target, the one width-mismatched pie image, and
23 textures). Phase 1 now packages and resolves the ready entries; work assets
remain excluded.

### Phase 1: Safe foundation

- [x] Add the setting, catalog schema/loader, resolver, validation, and diagnostics
  with zero required overrides.
- [x] Prove that disabled mode and enabled mode with absent catalog coverage both
  return the exact canonical object. The empty-catalog case uses the same null
  entry path.
- [x] Keep version 1 to exact-canonical dimensions, inherited recoloring, and
  binary-alpha conversion.
- [x] Bind canonical objects after OSAR packs and existing external equipment have
  completed loading.
- [x] Rebind the retained appearance-panel arrows on a live toggle.
- [x] Add packaging and fallback tests before expanding coverage.

Side-by-side loader result on 2026-07-11: generated catalog data provides `156`
ready entries to `RemasteredSpriteCatalog`; `RemasteredSpriteResolver` lazily
decodes a separately cached override `Sprite` while retaining the exact supplied
canonical object. Item, animation-frame, and `SpriteDef` selection all resolve
through stable keys. Disabled, absent, incompatible, or failed entries return
the same canonical object supplied by the existing final selection path. The
player JAR packages only declared `frames/` PNGs and the audit manifest. The
persisted `Sprites: Classic/Enhanced` setting defaults Classic, manual changes mark the
renderer profile Custom, and Classic forces Off unless an explicit diagnostic
runtime override is active. Remaster does not force On before private visual
approval.

### Phase 2: Visually testable proof

- Add a deliberately small cross-section: one same-sized UI sprite, one item
  icon exercised in inventory and on the ground, and one complete short
  animation or equipped entry when practical. Use test fixtures, not an
  intentionally incomplete shipped animation, to prove per-frame fallback.
- Test software and OpenGL paths, toggling on and off in the same client
  session.
- Use a private client/server for owner visual approval. Do not deploy this
  work directly to the public live server.

### Phase 2B: Higher-resolution and alpha decision gate

- Decide whether the next visual gain needs higher source resolution,
  fractional alpha, or only improved same-resolution art.
- If higher resolution is needed, introduce and test an explicit logical
  source-size/pixel-density contract before accepting such manifest entries.
- If fractional alpha is needed, extend the shared sprite representation and
  both renderer paths together; do not make it an OpenGL-only visual feature
  while claiming software parity.
- Keep version-1 assets valid and fallback-compatible after either extension.

### Phase 2C: Texture override adapter

- Keep texture art in the same authoring workspace and generated inventory,
  but route it through `texture/<entry>` keys and a texture-specific resolver.
- Resolve a remastered texture before the canonical sprite-tree texture is
  converted into scene texture data; retain the canonical texture as the exact
  per-entry fallback.
- Define texture transparency and resolution rules separately from sprite
  rules. Existing Bobo textures match canonical dimensions, making them the
  safe first candidates.
- A live texture toggle must deliberately invalidate/rebuild OpenGL world
  texture caches and any resident chunk/material state that captures texture
  identity. Do not claim immediate live parity until that rebuild is proven
  safe and bounded.
- Decide whether the player-facing row expands to `Remastered assets`, whether
  textures follow the same toggle invisibly, or whether textures receive a
  separate advanced option. Classic must always retain canonical textures.
- Test software texture loading, OpenGL atlases, animated textures, transparent
  materials, relog/world reload, and per-texture fallback before promoting the
  staged Bobo texture sets to runtime-ready.

### Phase 3: Character and animation coverage

- Expand the stable-key adapter to sprite-tree equipment, player, and NPC
  frames.
- Verify shifts, bounds, mirroring, palette masks, partial sequences, and cache
  invalidation under real movement and camera changes.
- Add replacements in reviewable batches rather than a bulk conversion.
- Add final-color recolor policy only after policy travels explicitly through
  software and OpenGL draw transforms.

### Phase 4: Authoring workflow refinement

- Refine the foundation tooling and contributor guide from real collaborator
  use, including naming, metadata, transparent padding, scaling, and visual
  review.
- Keep a ledger of implemented keys so PNG filenames do not become the only
  source of truth.
- Generate contact sheets or canonical/remastered/fallback comparison captures
  for review without overwriting source art.
- Validate provenance IDs, alpha usage, dimensions, transparent padding,
  canonical metadata assertions, and package-size deltas before checkpointing
  an art batch.

### Phase 5: Release promotion

- Measure startup cost, memory, frame pacing, and package size.
- Complete private visual testing across representative scenes and equipment.
- Decide whether the option remains opt-in or can become the default only
  after coverage and fallback behavior are proven.

## Required Automated Coverage

- Workspace validation discovers every leaf `set.json`, rejects orphan ready
  PNGs, and ignores `incoming/`, `source/`, `work/`, and `output/` as runtime
  inputs.
- Scaffolding creates the documented category/set shape without overwriting an
  existing set.
- Classification is read-only, translates known raw offsets only in import
  metadata, reports ambiguous matches, and never guesses/pushes a promotion.
- Distributed descriptors generate one deterministic manifest/catalog with no
  duplicate stable keys; a second generation produces no diff.
- The Bobo migration fixture accounts for all 198 contributed files, five
  complete frame groups, the 85 item mappings, 23 texture mappings, and the
  one known item width mismatch.
- Draft/work assets and staged textures are absent from the player JAR until
  their runtime status is explicitly promoted.
- Disabled mode returns the exact canonical path/object behavior.
- Enabled mode selects an available valid override.
- A missing or malformed saved setting defaults Off; an explicit supported
  runtime override wins predictably and is reported in diagnostics.
- Empty-catalog enabled mode returns exact canonical objects and performs no
  PNG decode.
- Missing, malformed, or metadata-incompatible entries fall back and log once.
- A partial multi-frame replacement falls back frame by frame.
- Logical dimensions, shifts, bounds, anchors, and transparency are preserved.
- Version-1 alpha thresholding preserves transparent zero and visible black.
- Unsupported size and recolor policies are rejected instead of partially
  applied.
- Mask-compatible equipment receives its normal recolor once.
- Toggling rebinds retained panel references, selects distinct immutable
  objects, and preserves software/OpenGL parity without full presenter
  teardown.
- Canonical precedence fixtures cover base OSAR, active sprite pack, existing
  external PNG injection, and remaster override/fallback.
- Logical keys remain the same across custom and authentic canonical sources.
- Duplicate keys, path traversal, case collisions, excessive PNG dimensions,
  invalid sheets, and structural catalog corruption are rejected safely.
- The player build contains the catalog and every declared packaged asset.
- Generated catalog data is deterministic and matches the manifest.
- Every release asset has a valid provenance/credit identifier.
- Repeated rendering performs no repeated disk I/O or PNG decode.
- Reload failure retains the previous valid catalog; successful reload retires
  abandoned GPU/cache state at a safe boundary.
- Diagnostics distinguish absent coverage from invalid declarations and remain
  bounded under repeated fallback.
- The settings row remains reachable and clickable at supported aspect ratios
  despite the existing tall, scrollable Graphics section.
- Existing client builds, renderer guardrails, and release hotkey policy remain
  green.

## Private Visual Test Matrix

Prepare these instructions before requesting handoff. Use the private server
and development client only.

1. Start with the option Off; capture the proof UI sprite, inventory item,
   ground item, and animation from representative camera zooms.
2. Toggle On without relogging; confirm each declared proof key changes and
   undeclared neighboring sprites remain byte-for-byte/capture-equivalent to
   canonical art.
3. Toggle Off again; confirm exact canonical art returns, including the
   appearance-screen arrows retained by `Panel`.
4. Repeat in OpenGL-primary and the available software-renderer diagnostic
   path. Compare placement, clipping, mirrors, masks, and visible black.
5. Temporarily remove or corrupt one proof asset in a development fixture;
   confirm only that key/frame falls back and one actionable diagnostic is
   emitted.
6. Exercise inventory, bank, trade/shop/production UI, ground-item projection,
   camera rotation/tilt/zoom, combat pose, walk pose, and relog where relevant
   to the selected keys.
7. Record F6/session diagnostics, startup time, first-use decode spike, atlas
   growth, and package-size delta.
8. Restore the validated packaged assets, rerun focused automation, and give
   the owner exact toggle and route instructions. Do not deploy to live.

## Acceptance Criteria

- `dev/myworld/assets/authoring/remastered-sprites/` is a documented, navigable source of
  truth organized by category and named related-frame sets.
- A collaborator drop can be inventoried, classified, scaffolded, validated,
  reported, and promoted without editing renderer code or a giant central
  manifest.
- The original Bobo batch is fully accounted for and reorganized without
  losing contributor attribution or Git history; no parallel permanent
  `bobo-resprites/` runtime tree remains.
- Draft/source/incoming files are visibly distinct from runtime-ready frames
  and cannot enter a player package accidentally.
- Original sprite archives and current custom-sprite behavior remain intact.
- The player has one clear, persisted, reversible remastered-sprite toggle.
- Mixed original/remastered coverage is safe at sprite and animation-frame
  granularity.
- Bad or absent remastered assets visibly degrade only to canonical art.
- Both renderers share the same resolution decision and remain stable after a
  live toggle.
- Version-1 behavior is honest about binary alpha and exact canonical source
  dimensions; unsupported richer formats fail back instead of degrading
  differently per renderer.
- At least the proof set is visually approved on a private client before any
  release handoff.
- Diagnostics allow another AI session to identify loaded, missing, invalid,
  and fallback entries without relying only on visual inspection.

## AI-3 Working Agreement

AI-3 owns this umbrella while it remains on one focused topic branch. It may
iterate through inventory, foundation, proof assets, testing, and refinement
without asking the manager to authorize every small adjustment. It should:

- checkpoint after the key contract, safe foundation, and each visually useful
  proof increment;
- update this plan with discovered identities, decisions, and the asset ledger;
- keep original fallback behavior working at every checkpoint;
- report private test instructions and stop short of live deployment;
- request a manager handoff once the foundation and a coherent visually
  testable proof are ready for review, rather than handing off every experiment.

## Open Decisions

- Named safety limits for catalog entries, PNG dimensions/pixels, sheet frames,
  decoded bytes, and diagnostic samples.
- Whether higher-resolution PNGs use an extended logical pixel-density
  contract or category-specific generated renditions after Phase 2B.
- Which three sprites form the first proof set.
- The proof-approval checkpoint at which the Remaster preset begins enabling
  the option automatically.
- Whether runtime-ready textures share a renamed `Remastered assets` toggle or
  receive a separate option after the texture adapter is proven.
- Friendly leaf names/families for the 85 Bobo item images and 23 textures
  after the stable keys are resolved to player-facing definitions/materials.
- Whether a future external user-mod directory is desirable after the curated
  packaged system is mature.

## Decision Ledger

- 2026-07-11: Keep remastered sprites separate from
  `Config.S_WANT_CUSTOM_SPRITES` and the combat-VFX animation migration.
- 2026-07-11: Use logical `sprite/<subspace>/<entry>/<frame>` keys; physical
  authentic array offsets remain fallback implementation details.
- 2026-07-11: Treat the final post-pack/post-injection object as canonical and
  preserve it unchanged.
- 2026-07-11: Use `dev/myworld/assets/authoring/remastered-sprites/` as a clean authored
  workspace beside `sprites/` and `animations/`, organized by category and
  named related-frame sets such as `npcs/demon/`.
- 2026-07-11: Make distributed leaf `set.json` descriptors authoritative and
  generate the aggregate `manifest.json` plus deterministic Java catalog data.
- 2026-07-11: Treat the 198-file ThatKidBobo `bobo-resprites` contribution as
  the first migration/import fixture; preserve attribution and history while
  retiring the contributor-named path after reviewed promotion.
- 2026-07-11: Organize textures in the same workspace under separate
  `texture/<entry>` identities, but do not route them through sprite resolver
  v1 or package staged texture work as active overrides.
- 2026-07-11: Foundation entries require exact canonical pixel dimensions,
  inherited recoloring, and thresholded binary transparency. Higher-resolution,
  final-color, and fractional-alpha contracts require later decision gates.
- 2026-07-11: Classic will force the feature Off. Remaster will force it On
  only after the private proof is approved; manual changes mark the renderer
  preset Custom.
- 2026-07-11: Generate `orsc.remastered.RemasteredSpriteCatalogData`; keep
  catalog validation, stable keys, settings, and resolution in the
  `orsc.remastered` package. Integrate the resolver at the three converged
  `GraphicsController.spriteSelect` paths, with the existing external-item
  injection resolved before the remaster choice.
- 2026-07-11: Label the persisted setting `Sprites: Classic/Enhanced`, place it in the
  OpenGL Graphics section (and the software Interface section), default it Off,
  and retain a runtime property/environment override for diagnostics.
