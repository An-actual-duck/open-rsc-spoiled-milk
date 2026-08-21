# In-Game World Editor Plan

Status: **historical Core implementation plan.** The foundation described here
was completed before RSC World Editor became an independent project. Current
editor work belongs to `/home/justin/rsc-world-editor` and its independent
runtime provider; this file must not be used to activate a Core worker.

Owner: Spoiled Milk project owner

Initial branch: `feat/in-game-world-editor-foundation`

Former next branch: `feat/world-editor-compact-toolbar` (historical only)

Related standalone product plan:
[`standalone-world-builder-plan.md`](standalone-world-builder-plan.md). The
integrated editor remains the shared editing implementation; standalone
workspace isolation, automatic Builder startup, import/rollback, and separate
release packaging are owned by that plan.

## Summary

Build an administrator-only in-game world editor that uses the desktop player
and world view as an editing cursor. The editor will provide a dedicated UI for
inspecting, copying, painting, placing, rotating, and removing terrain,
scenery, boundaries, and NPCs without requiring the editor to memorize command
syntax or encoded map values.

The existing developer commands and pending scenery/NPC edit files are useful
groundwork, but they are not the final editor architecture. Editing must be
owned by an explicit server-authorized session, represented as reversible
transactions, previewed consistently by the client and server, and saved into
reviewable project files from a private development server.

This plan is intentionally staged. The first implementation branch establishes
the permission, session, protocol, UI, and read-only inspection foundation. It
must not jump directly to terrain painting before the client and server agree
on raw tile values, coordinates, and session state.

## Goals

- Make routine world editing possible from a visual in-game interface.
- Let the world editor select exactly which terrain fields a stamp or brush
  changes; unchecked fields remain untouched.
- Use human-readable definition names, colors, previews, and orientation
  controls instead of requiring encoded numeric knowledge.
- Support single-tile stamps and true click-drag brush strokes with adjustable
  radius.
- Support scenery and NPC placement/removal through exclusive editor modes.
- Make every edit inspectable, undoable, recoverable, testable, and safe to
  hand off through the normal worker/manager release workflow.
- Keep the server's collision state, the client's rendered landscape, and the
  packaged server/client landscape data synchronized.

## Non-Goals

- Do not enable editing on the public live deployment by default.
- Do not permit an ordinary player, client-side developer toggle, or spoofed
  packet to obtain editing authority.
- Do not rewrite the complete landscape archive after every click or brush
  sample.
- Do not expose raw orientation offsets, collision masks, or archive quirks as
  values the editor must calculate manually.
- Do not support multiple simultaneous terrain editors in the first release.
- Do not include Android UI support in the first implementation. The existing
  Android experience remains unchanged unless a later plan explicitly adds a
  supported editor interface.
- Do not replace the standalone 2D landscape editor. The in-game editor is an
  additional visual workflow and an eventual source of compatible terrain
  patches.
- Do not add advanced fill, selection, prefab, scripting, or collaborative
  tools before the safe stamp-and-brush foundation is validated.

## Existing Foundation

The repository already includes partial editing support:

- `Development.java` can add, repeat, rotate, and remove ordinary scenery;
  add/remove NPCs; show pending counts; save pending scenery/NPC JSON; and
  clear pending queues.
- The desktop client has a developer menu with ground, object, and NPC actions
  that translate menu selections into development commands.
- Existing developer examines can expose object and NPC IDs and coordinates.
- `WorldSceneryEditFiles` and `WorldNpcEditFiles` provide deterministic custom
  location/removal files for ordinary scenery and NPC edits.
- The custom landscape archive stores a fixed raw tile record containing
  elevation, ground texture, overlay, roof texture, horizontal wall, vertical
  wall, and diagonal wall values.

These implementations should be refactored behind reusable editor services.
The UI must not build a second independent set of object/NPC mutation rules.

### Known gaps that shape the design

- Existing pending edit queues are static/global rather than owned by a player
  or editing session.
- Clearing the existing queues does not revert live entity mutations.
- Ordinary scenery is persisted, but boundary/wall-object editing does not yet
  have equivalent persistence.
- The server runtime `TileValue` does not retain all raw archive values. In
  particular, it is not a complete source for ground texture and roof texture.
- Landscape data is normally packaged separately in the client and server. It
  is not streamed as an authoritative complete map by the server.
- Terrain, wall, overlay, and elevation changes affect derived collision,
  projectiles, shared vertices, roofs, minimap data, and renderer caches.

## Terminology and Coordinate Rules

- **Raw tile**: the seven authored values stored in the landscape archive.
- **Derived tile state**: traversal, projectile, geometry, lighting, roof, and
  other runtime data calculated from raw values and definitions.
- **Editor session**: a server-authorized, bounded period during which one
  administrator may inspect or mutate a draft.
- **Draft**: the collection of unsaved terrain/entity transactions owned by an
  editor session.
- **Stamp**: one application of the current tool at one target tile.
- **Stroke**: one mouse-down through mouse-up brush operation, potentially
  affecting many tiles, recorded as one undo transaction.
- **Plane**: the world height/floor associated with the target tile. Every
  inspect and edit message must include it explicitly.
- **Base revision**: the landscape/edit-file revision from which the session
  began, used to prevent stale saves.

All editor displays should show world X, world Y, and plane. Conversion between
world coordinates, 48-by-48 archive sectors, local scene tiles, and client
rendering coordinates must live in tested helpers rather than duplicated UI
arithmetic.

## Permission and Environment Contract

`::worldeditormode` is the only normal entry point. It requests a session from
the server; it does not grant authority locally.

Entering the editor requires all of the following:

- a server-side administrator or dedicated world-builder capability;
- a server configuration flag such as `allow_in_game_world_editor=true`;
- a compatible custom desktop client and protocol version;
- no conflicting active editor session under the initial single-editor rule;
  and
- a server environment permitted to author project edits.

The configuration flag defaults to false. The public detached live deployment
must keep it disabled. A client-side `::dev` flag, local player property, or UI
visibility check is never sufficient authorization.

The server returns an opaque session ID and explicit capabilities. Every edit,
undo, redo, inspect, copy, save, discard, and exit request carries that session
ID and is validated server-side. Logout, disconnect, permission loss, server
shutdown, or explicit exit closes the authority. The draft must remain either
recoverable or explicitly discarded; it must never silently become published
world data.

For the first implementation, allow only one active mutating world-editor
session. Read-only inspection may remain part of that same session. Region
locks and multiple editors can be considered only after revision and conflict
handling are proven.

## Editor State and Exclusive Modes

The editor uses one exclusive mode enum, not independent top-level checkboxes:

1. `NAVIGATE`
2. `INSPECT`
3. `TERRAIN`
4. `SCENERY`
5. `NPC`
6. `BOUNDARY` after boundary persistence is implemented

Selecting one mode deactivates the others. Terrain field selection remains a
set of checkboxes inside `TERRAIN`; it does not compete with the top-level
mode.

`NAVIGATE` is necessary because a paint click cannot also be an ordinary walk
click. The initial behavior is:

- Navigate: ordinary supported movement and camera interaction.
- Inspect: clicking selects without changing the world.
- Terrain: click stamps; mouse drag paints a stroke.
- Scenery/NPC/Boundary: click places the selected definition; context actions
  inspect, copy, rotate where applicable, or remove.

Entering editor mode clears selected spells/items and blocks combat, trading,
ordinary object actions, and other gameplay interactions that could be confused
with editor input. Exiting restores the normal action menus without changing
the player's saved game settings.

## Desktop Editor Interface

Implement a dedicated movable desktop overlay rather than adding these tools
to the normal Options tabs. The first version can combine existing `NComponent`
layout support with existing numeric-input behavior; an inline numeric entry
control can follow when it is reliable.

Suggested compact layout:

```text
+ World Editor ------------------------------------------------+
| Mode: Navigate | Inspect | Terrain | Scenery | NPC           |
| Target: x=000 y=000 plane=0       Draft: 24 changes          |
|                                                              |
| Terrain fields                                               |
| [x] Elevation       [-] 12 [+] [Enter]                       |
| [x] Ground texture  [-] 37 [+] [Enter] Grass [preview]       |
| [ ] Overlay         [-]  0 [+] [Enter] None                  |
| [ ] Roof texture    [-]  0 [+] [Enter]                       |
| [ ] Horizontal wall [-]  0 [+] [Enter] None                  |
| [ ] Vertical wall   [-]  0 [+] [Enter] None                  |
| [x] Diagonal wall   [-]  1 [+] [Enter] Stone wall            |
|     Orientation: \  [Rotate]                                 |
|                                                              |
| Brush: Square   Radius: [-] 1 [+]                             |
| [Undo] [Redo] [Save] [Discard] [Exit]                        |
+--------------------------------------------------------------+
```

The actual component should fit supported desktop resolutions and may use
tabs, collapsible sections, or scrolling. It must not obstruct modal dialogs,
chat input, or the logout flow.

### Common UI behavior

- `-` and `+` change to the previous/next valid value, not merely the previous
  integer when definitions are sparse.
- `Enter` or the value field accepts a typed numeric value and validates it.
- Definition-backed values show ID and canonical name.
- Texture/overlay values show a color swatch or small preview when available.
- Invalid, absent, invisible, or collision-significant definitions receive a
  clear warning rather than silently painting.
- A checked field without a valid value blocks painting and explains why.
- Radius uses a clear footprint definition. Start with radius `0` meaning one
  tile and cap the first release at `5`.
- The current target and footprint are highlighted before application.
- UI state may persist locally for convenience, but authority and pending
  edits never do.

## Terrain Field Contract

The raw editable fields are:

- elevation;
- ground texture;
- ground overlay/tile decoration;
- roof texture;
- horizontal wall;
- vertical wall; and
- diagonal wall plus its normalized orientation.

Traversal masks, projectile flags, generated faces, normals, roof geometry,
and renderer chunks are derived/read-only. They can appear in the inspector for
diagnostics but cannot be painted directly.

Each terrain stamp includes a field mask and values. A raw field is changed
only when its mask bit is selected. An unchecked field remains byte-for-byte
unchanged.

Ranges must be validated against both the archive representation and loaded
definitions. Byte-backed fields are displayed as unsigned values where
appropriate. Do not permit silent wrapping from a typed integer to a byte.

### Diagonal wall orientation and rotation

The editor must never require the user to add or subtract `12000`.

The client and server represent the UI selection as two normalized properties:

- `baseDiagonalWallValue`: `0` for no wall or a valid base wall value; and
- `diagonalOrientation`: `A` or `B`.

The archive encoding remains an implementation detail:

- no diagonal wall: `0`;
- orientation A: base values `1..11999`;
- orientation B: `baseDiagonalWallValue + 12000`, producing
  `12001..23999`;
- raw `12000` and `24000` are boundary values, not valid selectable walls.

The UI provides a `Rotate` toggle/button beside the diagonal wall value and a
small `\` or `/` preview. Activating Rotate switches A to B or B to A without
changing the selected wall definition. The control is disabled when the
diagonal field is unchecked or the selected value is `0`.

Copy and inspection must decode an existing raw value into the base wall and
orientation. Painting must encode the normalized pair exactly once. Undo/redo
stores normalized values plus the exact before/after raw tile records so
repeated rotation cannot accumulate offsets.

The details inspector may show the raw encoded value in a diagnostic row, but
the primary editable field always shows the base wall value, definition name,
and visible orientation. Tests must explicitly cover `1 <-> 12001`, the
highest supported valid definition, `0`, invalid boundary values, both
collision masks, and copy/paint/undo round trips.

## Inspect, Examine, and Copy

While editor mode is active, normal world context menus are replaced or
filtered by the selected editor mode.

Terrain context actions include:

- `Inspect terrain`
- `Copy terrain`
- `Paint here` when Terrain mode is active
- relevant remove actions when a wall is present

`Inspect terrain` opens or updates a details panel with all raw fields, decoded
definition names, decoded diagonal orientation, coordinates, sector identity,
base revision, derived traversal/projectile state, and pending-draft state.

`Copy terrain` requests the authoritative raw tile from the server, populates
all raw terrain controls, decodes diagonal wall orientation, and checks every
terrain field. The server response, not an unverified client-local value, is
the final source for the copied state.

Object and NPC inspection includes:

- definition ID and name;
- world coordinates and plane;
- direction/orientation;
- object footprint or NPC roaming bounds;
- stable authored key and runtime instance ID where applicable;
- base-world versus custom-overlay source; and
- pending upsert/removal state.

Copying scenery/NPC data selects the matching exclusive mode and fills its
controls. It does not immediately place another entity.

## Scenery, Boundary, and NPC Tools

### Scenery

The scenery mode includes definition ID/name, direction `0..7`, footprint
preview, place, copy, rotate, and remove. Existing ordinary scenery mutation
and JSON persistence logic should be refactored into a reusable service and
called by both legacy commands and editor packets during migration.

Placement validation must cover occupied origins, multi-tile footprints,
collision impact, valid directions, plane, range, and session authority.

### Boundaries

Boundary objects are distinct from ordinary scenery and from diagonal wall
values embedded in raw terrain tiles. Do not present them as one interchangeable
system merely because each can render a wall.

Boundary mode should not become writable until it has deterministic upsert and
removal persistence equivalent to ordinary scenery. Read-only identification
may arrive earlier. Its controls include definition, direction/type, origin,
copy, rotate when meaningful, and remove.

### NPCs

NPC mode includes definition ID/name, direction where supported, start tile,
and roaming bounds/radius. Removal and editing must use a stable authored key;
the volatile runtime instance ID is diagnostic context, not the sole persisted
identity.

NPC placement/removal must reuse refactored `WorldNpcEditFiles` behavior and
support undo, redo, discard, and exact recovery of the previous live state.

## Brush and Stamp Input

Do not simulate repeated operating-system clicks or send one text command per
mouse frame.

The client implements a real editor stroke:

1. On world mouse-down, resolve the targeted world tile and plane.
2. Add it only when it differs from the last sampled tile.
3. Rasterize between samples so rapid movement cannot leave gaps.
4. Expand each sample by the selected radius and shape.
5. Deduplicate all tiles within the stroke.
6. Show the pending footprint locally.
7. Send bounded batches under one stroke ID and sequence.
8. End the transaction on mouse-up, cancellation, or loss of editor focus.

Start with a square footprint. A circle, line, rectangle, fill, and selection
tool are later enhancements.

The server enforces maximum radius, tile count, packet count, coordinate
bounds, plane, session ID, sequence ordering, capability, and rate limits. A
malicious client cannot bypass these limits by crafting packets.

The client may optimistically preview a small operation, but it must retain the
before state and roll back rejected tiles. Server acknowledgement is the
authoritative result.

## Session Draft, Undo, Redo, and Recovery

Introduce a `WorldEditSessionManager` or equivalent server service. It owns:

- the active editor identity and session ID;
- environment/capability checks;
- base revisions;
- terrain, scenery, boundary, and NPC draft changes;
- ordered stroke/stamp transactions;
- bounded undo and redo history;
- dirty regions/sectors;
- save/discard state; and
- recovery metadata.

Each stamp is one transaction. Each complete drag stroke is one transaction,
even when transported in several packets. Transactions store enough before and
after state to reconstruct both raw terrain and live entities exactly.

Any new edit after Undo clears the redo branch. Undo/redo limits must be based
on both transaction count and affected-tile/entity memory, not count alone.

Disconnect or server interruption must preserve a recoverable draft journal or
refuse to claim the work is safe. On reconnect, the editor can resume the
matching recoverable draft or explicitly discard it. Recovery files are local
development state until a deliberate save/checkpoint includes authored output.

`Discard` reverts the live preview and clears the draft. This is different from
the current `clearworldedits` behavior, which clears queues without reverting
live entities. Existing commands should eventually route to the same semantics
or clearly retain a separately named legacy action.

## Protocol

Text commands may remain compatibility entry points for simple legacy object
and NPC actions. Terrain inspection and brush editing require dedicated,
bounded protocol messages.

Required logical messages include:

- client to server: request open, close, inspect, copy, apply stamp/stroke
  batch, finish/cancel stroke, undo, redo, save, and discard;
- server to client: session opened/denied/closed, capabilities, tile/entity
  snapshot, draft summary, operation acknowledgement/rejection, authoritative
  patch, undo/redo result, save result, and forced close.

Every mutating message includes session ID, sequence, plane, operation/stroke
ID, base revision, and bounded payload. Terrain writes include a field mask and
normalized values; diagonal walls use base value plus orientation on the wire,
not an unchecked raw `+12000` integer.

Packets must not contain arbitrary filesystem paths. Unknown fields, invalid
enum values, excessive counts, invalid definitions, stale revisions, and
out-of-order stroke fragments are rejected without partial silent mutation.

## Authoritative Terrain Model and Runtime Rebuilds

The server needs a complete raw terrain source that retains all seven archive
fields. Runtime `TileValue` alone is insufficient. Build a terrain archive or
overlay service that can:

- load raw sectors deterministically;
- return an authoritative raw tile by world coordinate and plane;
- apply/revert masked patches;
- track dirty sectors and neighboring dependencies;
- derive server collision/projectile state through one reusable calculation;
  and
- serialize/materialize edits without losing untouched archive entries.

Extract reusable collision derivation from landscape loading rather than
copying its conditions into the editor. Wall changes can affect adjacent
tiles. Elevation and roof changes can cross tile and sector boundaries.

On an acknowledged terrain change, the desktop client updates its in-memory
sector data and invalidates the smallest safe affected region, including as
needed:

- terrain mesh/input;
- wall mesh/input;
- roof geometry and visibility;
- local collision/picking;
- minimap raster;
- lighting/normals; and
- renderer-v2 and legacy-renderer caches.

Both render paths must remain behaviorally supported. Rebuilding the entire
visible world per brush sample is not an acceptable final implementation; a
coarse rebuild may be used temporarily in a diagnostic prototype only when
clearly measured and replaced before brush rollout.

## Persistence and `::saveworldedits`

Current implementation note: the first durable commit path materializes the
coalesced in-memory terrain draft directly into both workspace landscape
archives. It verifies the captured base digest, exact client/server agreement,
every output entry, final matching hashes, and a timestamped rollback copy.
The draft clears only after the saved server archive reopens. The versioned
patch journal described below remains future recovery and transaction work.

Use a versioned, deterministic terrain patch representation during editing,
for example `MyWorldTerrainEdits.json`. The exact filename and schema may be
adjusted during the architecture phase, but the following contract is fixed:

- keys include plane and world coordinate;
- records preserve only deliberate authored changes plus required base
  revision/schema information;
- serialization order and formatting are deterministic;
- unknown schema versions are rejected;
- duplicate/conflicting tile records are rejected;
- client and server can apply the same normalized result; and
- the patch can be materialized into complete landscape archives without
  changing unrelated sectors or ZIP entries.

`::worldedits` reports per-session and total counts for terrain tiles,
scenery, boundaries, NPCs, transactions, and dirty sectors.

`::saveworldedits` becomes the deliberate commit point for the active draft.
It must:

1. require the active authorized session;
2. verify the base archives/edit files have not changed;
3. validate all terrain definitions, entity definitions, coordinates,
   collisions, packet completion, and persisted removals/upserts;
4. write temporary outputs and validate them before replacement;
5. atomically write deterministic patch/entity files;
6. materialize both server and client `Custom_Landscape.orsc` outputs when the
   selected persistence design requires it;
7. verify matching expected client/server landscape hashes;
8. preserve a timestamped backup or restorable prior revision;
9. emit a summary of coordinates, sectors, entities, and hashes; and
10. retain or close the session according to an explicit successful-save
    policy.

A failed validation or filesystem operation leaves the previous saved files
intact and the draft recoverable.

The normal project workflow remains mandatory after save:

```text
worker branch -> private editor server -> save -> tests -> checkpoint/handoff
              -> manager review/merge -> release -> detached live deployment
```

Current players will not automatically receive newly packaged terrain merely
because the server archive changed. Until a future supported map-patch delivery
system exists, releasing materialized terrain requires a matching client
release and server deployment.

## Implementation Phases

### Phase 0: Architecture inventory and round-trip fixtures

- [ ] Document every raw tile field, range, definition lookup, diagonal
  orientation encoding, coordinate transform, plane mapping, and derived
  collision dependency.
- [ ] Add byte-exact sector/archive round-trip fixtures for representative
  terrain, walls, roofs, upper floors, and diagonal orientations.
- [ ] Prove how server and client archive copies are compared and
  materialized without unrelated drift.
- [ ] Inventory available custom protocol opcode ranges and UI integration
  points before assigning identifiers.
- [ ] Record the chosen terrain patch schema and recovery-journal location.
- [ ] Record any architectural decision that changes this plan before feature
  implementation proceeds.

### Phase 1: Authorized read-only editor foundation

This is the initial `feat/in-game-world-editor-foundation` branch scope.

- [ ] Add the disabled-by-default server configuration gate.
- [ ] Add `::worldeditormode` open/close handling with server-authoritative
  administrator/world-builder checks.
- [ ] Add a single active session state with opaque ID, sequence validation,
  logout/disconnect cleanup, and clear denial messages.
- [ ] Add the movable desktop interface shell and exclusive Navigate/Inspect/
  Terrain/Scenery/NPC mode controls; mutation controls remain disabled.
- [ ] Add authoritative terrain inspection messages and a complete raw tile
  snapshot model.
- [ ] Display raw fields, definition names, decoded diagonal orientation,
  coordinates, plane, sector, and derived collision diagnostics.
- [ ] Add read-only object/NPC inspection details and origin/source labels.
- [ ] Add `Copy terrain` UI population locally from the authoritative response,
  including normalized diagonal wall value and Rotate state; it must not paint.
- [ ] Ensure normal gameplay behavior is unchanged when editor mode is closed
  or unavailable.
- [ ] Do not enable Android editor UI.
- [ ] Handoff with changed files, protocol decisions, automated tests, manual
  private-server verification, and known risks.

### Phase 2: Session-scoped scenery and NPC mutations

- [ ] Refactor existing scenery/NPC command logic into reusable validated
  services.
- [ ] Replace global pending state with session-owned draft transactions.
- [ ] Implement scenery/NPC UI search/value controls, placement, copy, rotate,
  removal, undo, redo, discard, and pending summaries.
- [ ] Preserve compatible legacy commands by routing them through the shared
  services where practical.
- [ ] Add stable NPC/scenery authored keys and exact live-state reversal.
- [ ] Add boundary read-only inspection and design/persist its edit-file
  contract before enabling mutations.

### Phase 3: Single-tile terrain stamps

- [ ] Implement the versioned terrain patch/draft store.
- [ ] Add masked single-tile stamps with strict validation.
- [ ] Implement diagonal wall base-value/orientation encoding, Rotate, copy,
  inspect, apply, undo, redo, discard, and round-trip tests.
- [ ] Recompute server collision/projectile state for the complete dependency
  neighborhood.
- [ ] Apply acknowledged patches to client in-memory sectors and invalidate
  terrain/wall/roof/minimap/render caches safely.
- [ ] Validate both legacy and renderer-v2 paths on a private server/client.
- [ ] Add target/footprint preview and rejected-edit rollback.

### Phase 4: Brush strokes and radius

- [ ] Implement real mouse drag sampling, line gap filling, square radius,
  deduplication, bounded batching, and operation sequencing.
- [ ] Treat each complete stroke as one undoable transaction.
- [ ] Enforce client and server radius/count/rate limits.
- [ ] Handle sector edges, visible-scene edges, upper floors, focus loss,
  cancellation, rejection, and partial transport without partial silent save.
- [ ] Measure renderer rebuild and server collision costs before raising radius
  limits.

### Phase 5: Atomic save and landscape materialization

- [ ] Integrate terrain, scenery, boundary when available, and NPC drafts into
  `::worldedits`, `::saveworldedits`, and Discard.
- [ ] Add stale-base detection, deterministic ordering, temporary output,
  validation, atomic replacement, backup, and recovery.
- [ ] Materialize matching server/client archives or prove the chosen shared
  overlay packaging path.
- [ ] Hash-check both landscape outputs and confirm unrelated archive entries
  remain unchanged.
- [ ] Add release-check coverage for terrain patch schema, archives, and
  server/client parity.
- [ ] Document the private editor server through manager release workflow.

### Phase 6: Boundary editing and advanced tools

- [ ] Implement deterministic boundary upserts/removals and then enable its
  exclusive UI mode.
- [ ] Add circle/line/rectangle/fill/selection tools only after stamp and brush
  safety is proven.
- [ ] Add definition search, favorites, richer thumbnails, prefab support, and
  optional keyboard shortcuts.
- [ ] Consider region locks and multiple editors only with conflict-aware
  revisions and recoverable drafts.
- [ ] Consider a supported signed map-patch distribution system separately
  from the first editor release.

## Next Phase: Compact Toolbar, Custom Icons, and Editing Performance

Approved direction: July 12, 2026

The current editor proves the editing model, but its large form consumes too
much of the world view and its performance option is buried among terrain controls.
This phase turns that form into a small paint-editor-style tool dock, prepares
the client to use real custom PNG icons supplied by the project owner, and
then addresses measured renderer/rebuild costs in a separate follow-up branch.

The UI refactor and the rebuild optimization are deliberately separate. The
first branch must preserve editing behavior while changing presentation. The
second branch may change rebuild behavior only after baseline measurements and
private testing establish that it is safe.

### Goals

- Give the editor as much unobstructed world view as practical.
- Make the currently active mode, field mask, brush size, performance profile,
  pending operation count, and unsaved state visible at a glance.
- Use real, replaceable PNG assets for editor icons without making missing or
  invalid artwork capable of breaking the editor.
- Keep all existing safety semantics: ordinary clicks stamp, Ctrl+left-drag
  brushes, middle-drag rotates the camera, and modes remain exclusive.
- Move the editor performance mode to a global control and make it
  reversible when the editor closes.
- Measure input, acknowledgement, and world-rebuild latency before optimizing
  them.
- Preserve desktop-only scope. Android UI and controls remain unchanged.

### Non-goals for the first branch

- Do not redesign the packet protocol, saved patch format, collision rules, or
  server authority merely to support the compact UI.
- Do not add new map-editing shapes, undo/redo, multiplayer editing, or a
  different renderer backend.
- Do not silently change normal player graphics preferences or save editor
  performance choices as ordinary gameplay defaults.
- Do not remove terrain, scenery, NPC, wall, or roof rendering so completely
  that the editor can no longer pick or understand what it is editing.
- Do not ship invented font glyphs or code-drawn symbols as the final icon set.

### Custom PNG icon contract

The project owner will acquire the final artwork. Implementation must build a
small editor-icon registry and loader so replacing artwork is a file operation,
not a Java source edit.

- Author icons under `dev/myworld/assets/ui/world-editor/` and package them at
  `myworld-assets/ui/world-editor/` in player builds.
- Use transparent RGBA PNG files on an exact `24x24` pixel canvas. Icons should
  remain legible at native scale against both light and dark terrain.
- Use stable lowercase kebab-case filenames. Java code refers to semantic icon
  keys in one registry rather than scattered path strings.
- Load and decode icons once when editor assets initialize, cache the resulting
  sprites, and never read image files from the draw loop.
- Render hover, selected, enabled, disabled, warning, and dirty states through
  the button background, border, tint, or badge. Do not require a duplicate
  PNG for every state unless a specific icon cannot remain readable otherwise.
- Use nearest-neighbor/native-size drawing. If high-DPI variants are added
  later, name them consistently and select them in the registry rather than
  allowing arbitrary draw-loop scaling.
- A missing, malformed, or unsupported PNG must produce one concise diagnostic
  and a labeled code-drawn fallback button. It must not prevent login, editor
  entry, or use of the tool. The fallback is development resilience, not final
  release artwork.
- Add an asset README containing this contract and a credits/license file that
  records the source, author, license, and modifications for every acquired
  icon set. Do not merge artwork whose redistribution rights are unknown.
- Extend the Ant player-asset fileset explicitly so the new directory appears
  in packaged clients, then test both a development run and the produced player
  archive. An icon working only from the source tree is not sufficient.

Initial required icon keys and filenames:

| Key | Filename | Meaning |
| --- | --- | --- |
| `toolbar-collapse` | `toolbar-collapse.png` | Collapse or expand the dock |
| `toolbar-expand` | `toolbar-expand.png` | Expand the collapsed dock |
| `mode-navigate` | `mode-navigate.png` | Safe navigation mode |
| `mode-inspect` | `mode-inspect.png` | Inspect/copy tile values |
| `mode-scenery` | `mode-scenery.png` | Scenery placement/removal |
| `mode-npc` | `mode-npc.png` | NPC placement/removal |
| `field-elevation` | `field-elevation.png` | Elevation field mask/value |
| `field-floor-color` | `field-floor-color.png` | Ground color/material |
| `field-floor-texture` | `field-floor-texture.png` | Ground texture/overlay |
| `field-roof` | `field-roof.png` | Roof value |
| `field-wall-north` | `field-wall-north.png` | North wall value |
| `field-wall-east` | `field-wall-east.png` | East wall value |
| `field-wall-diagonal` | `field-wall-diagonal.png` | Diagonal wall value |
| `tool-brush-1x1` | `tool-brush-1x1.png` | Single-tile stamp/brush size |
| `tool-brush-3x3` | `tool-brush-3x3.png` | Centered three-by-three brush size |
| `profile-build` | `profile-build.png` | Faceted, gridded Build view |
| `action-save` | `action-save.png` | Persist the current draft |
| `action-pin` | `action-pin.png` | Keep the current flyout open |
| `action-close` | `action-close.png` | Exit the editor |

Undo, redo, shape, layer-visibility, and prefab icons may be reserved in the
registry later, but must not appear as active controls before those functions
exist.

### Compact desktop interaction model

The default editor presentation is a fixed two-column dock on the left edge,
approximately 70 pixels wide, plus at most one contextual flyout. The dock
must not cover the chat tabs, minimap, or other permanent desktop controls.
Supporting right-side docking can follow later; the first implementation
should prefer a dependable fixed location over a generalized window system.

The compact dock keeps collapse, Navigate, Inspect, Brush/Terrain, Build, Save,
and Close in its left column. Scenery, NPC, Elevation, Floor Color, Floor
Texture, Roof, North Wall, East Wall, and Diagonal Wall occupy the right
column. This makes every terrain field directly reachable without the compact
Surface/Structure tab switch. The detailed expanded form retains those tabs
as a learning/reference view.

Selecting a mode activates that mode and opens its flyout. Selecting the
already active mode toggles its flyout without deactivating the safe, explicit
mode. Only one flyout may be open. A flyout opens immediately to the right of
the dock, is roughly 220-260 pixels wide, and may be pinned. An unpinned flyout
closes when the user makes a valid world-view action, but the selected values
and field masks remain active.

Terrain field buttons are multi-select only within Terrain mode:

- Left-click opens that field's value editor and preview without changing
  whether the field will be painted.
- Right-click toggles the field into or out of the paint mask, matching the
  requested paint-editor workflow.
- An active field has an unmistakable selected border/check marker; inactive
  fields remain visible but subdued. Invalid values use a warning state.
- Turning a field off retains its last value so it can be re-enabled without
  re-entry. It must never apply while unchecked.
- Every button has a tooltip containing its name, current value/definition,
  active state, left-click action, and right-click action. Icons alone are not
  treated as sufficient discoverability.

Brush left-click opens its compact footprint controls. Brush right-click
toggles directly between 1x1 and 3x3, with a distinct cached PNG for each size.
Compact terrain flyouts intentionally show only decrement/value/increment,
the short definition name when one exists, and paint On/Off. The expanded form
remains available from a visible Compact/Full switch for explanatory detail.

The diagonal-wall flyout exposes wall definition and orientation separately.
Rotate changes the orientation control; the normal UI must not make users add
or subtract `12000`. Copying a tile must populate both the base definition and
orientation, and painting must preserve the existing encoded protocol rules.

Scenery and NPC flyouts expose definition search/ID, name, placement options,
and remove/copy actions as appropriate. Activating either mode deactivates the
Terrain field mask for application purposes without erasing its configured
values. Mode exclusivity remains enforced in the model, not merely by drawing
one selected button.

Save carries a dirty/pending badge. It is disabled while a stroke is still in
flight and reports success or failure without hiding an error in a closed
flyout. Close must retain the existing unsaved-change protection. A narrow
status line beside or beneath the dock shows coordinates/plane, active mode,
brush size, queued/acknowledged work, and last rebuild time without expanding
the full form.

### Input invariants

- Left-click in the world performs one stamp in an editing mode.
- Ctrl+left-drag is required for continuous brush sampling. Merely holding or
  dragging left-click must not become an implicit brush.
- Middle-click and middle-drag remain reserved for camera behavior and never
  stamp, sample, or toggle a toolbar control through button aliasing.
- Right-clicking a toolbar icon is consumed by the toolbar and never opens a
  world menu beneath it. Right-clicking the world keeps editor-specific
  inspect/copy/remove options appropriate to the active mode.
- Keyboard focus inside a numeric/search field prevents world shortcuts from
  firing. Escape closes the current flyout before it exits editor mode.
- The compact and temporarily expanded/fallback layouts share one editor
  state model; a presentation change must not reset configured fields.

### Performance profiles

Build mode is a global editor profile rather than a Terrain checkbox. It
snapshots the current lighting, geometry, terrain variation, fog, tone,
terrain/object relief, and scenery-animation state; applies a cheaper editor
configuration with a square tile-boundary grid; and restores the exact snapshot
when disabled or when the editor closes.

Profiles are explicit and reversible:

- **Normal:** the ordinary renderer with the user's pre-editor preferences.
- **Build:** faceted geometry, reduced terrain/object relief and variation,
  inexpensive lighting, paused nonessential scenery animation, and a true
  square terrain grid. The grid is derived only from active-plane terrain tile
  boundaries, excluding triangle diagonals, walls, roofs, and scenery.

Profile changes are client-local. The server continues collision and entity
simulation unless a separately designed private-editor-server mode is approved.
Layer visibility controls for roofs, scenery, NPCs, ground items, effects, or
the minimap may be added after the compact dock, but their saved state must be
editor-only and every hidden editable layer needs an obvious indicator.

### Measurement and optimization phase

The likely high-cost path is the visible-world/terrain rebuild after accepted
edit batches, but that is a hypothesis to measure rather than an assumed cause.
Before altering it, capture at least:

- client frame time and worst frame during idle, hover, stamp, and brush;
- input-to-preview, client-to-server acknowledgement, and acknowledgement-to-
  rebuilt-world time;
- tiles sent, accepted, rejected, and rebuilt per operation;
- number of rebuilds and minimap refreshes per complete stroke;
- queued batches/backpressure and time spent waiting for the final batch;
- renderer-v1 and renderer-v2 results under Normal and Build profiles.

Diagnostics should aggregate per operation/stroke and remain readable by an AI.
Avoid per-tile log floods. The UI status line may show the most recent frame,
acknowledgement, and rebuild figures while the detailed diagnostic log retains
operation IDs and counts.

After measurement, implement the smallest safe invalidation strategy:

- Update accepted values in the client-side editor state immediately while
  retaining the authoritative server acknowledgement and revision checks.
- Coalesce geometry and minimap invalidation so a complete stroke rebuilds at
  most once after its final accepted batch, rather than once per input sample
  or transport chunk.
- Track dirty sectors/chunks and field types. Material-only changes should not
  rebuild unrelated geometry; elevation, wall, and roof changes must include
  every neighboring tile whose derived mesh or occlusion depends on them.
- Preserve the current bounded batching, deduplication, and backpressure. A
  faster renderer is not permission to create an unbounded command queue.
- Prevent stale asynchronous work from overwriting a newer operation by using
  editor revision/operation IDs in any deferred rebuild.
- Throttle hover/brush-preview recomputation and reuse buffers where profiling
  shows avoidable per-frame allocation, without making cursor feedback laggy.
- Refresh the minimap once per completed operation when needed, not once per
  tile.
- Fall back to the known full reload when dirty-region validation fails. An
  optimization must never leave client visuals or collision understanding
  silently inconsistent.

### Branch and handoff sequence

#### Slice A: compact UI and icon infrastructure

Branch: `feat/world-editor-compact-toolbar`

- [x] Add the semantic icon registry, cached PNG loader, missing-icon fallback,
  asset README/credits template, and release-build asset packaging.
- [x] Refactor the current coordinate-coded form onto a shared editor state and
  compact dock/flyout presentation without changing packets or mutations.
- [x] Implement the left-click/value and right-click/field-mask behavior,
  tooltips, visible state markers, pinning, status line, dirty/pending Save,
  and unsaved Close behavior.
- [x] Move the performance mode to the global dock and prove exact preference
  restoration on Build-off, editor exit, disconnect, and error paths.
- [x] Extend it into Build mode with faceted geometry and a true active-plane
  terrain grid in legacy and OpenGL presentation paths.
- [x] Keep a temporary expanded/fallback presentation available until the
  acquired icons have been validated in a packaged client.
- [ ] Test privately with both missing fallback icons and the acquired final
  icons before release consideration.

This handoff may be code-complete before final artwork is delivered, provided
the asset contract, loader, packaging, fallbacks, and missing-icon inventory
are complete. It is not visually release-ready until the owner supplies and
approves all required icons.

#### Slice B: measured incremental rebuild

Branch: `perf/world-editor-incremental-rebuild`

- [ ] Record private baseline measurements and include them in the handoff.
- [ ] Coalesce accepted brush batches and add field-aware dirty-region rebuilds
  with revision protection and a safe full-reload fallback.
- [ ] Add aggregated AI-readable operation diagnostics and last-operation UI
  timing.
- [ ] Compare correctness and frame/rebuild latency against the baseline for
  stamp, rapid brush, large radius, sector edge, upper floor, void expansion,
  walls/roofs, undo/discard if present, save, and restart.

#### Slice C: Build-view follow-ups

The initial Build view now ships with Slice A. Further visibility controls or
entity markers should follow only when private testing demonstrates a concrete
need; they must preserve picking and exact Normal-state restoration.

### Next-phase acceptance criteria

- The collapsed dock leaves substantially more world view visible than the
  current 390x330 form and every existing editing value remains reachable.
- All required controls work with final PNGs, in the packaged player client,
  without source-tree file dependencies.
- Removing or corrupting one icon yields one diagnostic and a usable labeled
  fallback; no unrelated editor control breaks.
- Active mode and field masks are understandable from state markers and
  tooltips even when artwork is unfamiliar.
- Left/right/middle/Ctrl input invariants pass repeated private testing, with
  particular attention to camera rotation and accidental terrain placement.
- Build mode always restores the exact pre-editor graphics state and produces a
  measurable improvement on at least one representative expensive edit case.
- Incremental rebuild work demonstrates fewer or shorter rebuild stalls without
  visual, collision, minimap, save/reload, or server/client revision drift.
- A private server/client test and owner visual confirmation occur before the
  manager merges either behavior-changing branch for release.

## Test Plan

### Automated coverage

- [ ] Permission/configuration matrix denies ordinary players, disabled
  environments, incompatible clients, stale sessions, and spoofed packets.
- [ ] Session sequence, reconnect, disconnect, forced close, conflicting open,
  and recovery behavior are deterministic.
- [ ] Raw sector and complete archive unpack/pack are byte-exact when unchanged.
- [ ] Every field mask changes only selected values.
- [ ] Typed values reject overflow, negative invalid values, missing
  definitions, and reserved encodings.
- [ ] Diagonal orientation tests cover no wall, both directions, rotate twice,
  copy, stamp, undo, redo, save, reload, collision flags, and archive output.
- [ ] Wall/overlay/elevation changes recalculate required neighboring derived
  state.
- [ ] Scenery/NPC mutations preserve existing placement/removal behavior and
  revert exactly on Undo/Discard.
- [ ] Stroke rasterization has no gaps, duplicates, out-of-bounds tiles, or
  unbounded batches.
- [ ] Stale-base saves and injected write failures leave previous saved data
  intact and the draft recoverable.
- [ ] Deterministic materialization gives matching server/client hashes and
  does not alter unrelated sectors/entries.
- [ ] Editor-disabled builds preserve normal menus, movement, context actions,
  and protocol behavior.

### Private manual validation

- [ ] Enter/exit repeatedly with authorized and unauthorized accounts.
- [ ] Navigate and inspect terrain on ground and upper floors.
- [ ] Copy representative textures, overlays, elevation, roofs, straight
  walls, both diagonal orientations, and empty fields.
- [ ] Rotate the same diagonal wall between `\` and `/` without typing or
  seeing `+12000` as the normal workflow.
- [ ] Stamp one field while confirming every unchecked field is unchanged.
- [ ] Brush slowly and quickly across tiles, sector boundaries, and radius
  edges; verify no gaps and one-step Undo.
- [ ] Confirm server walking/projectile collision matches visible walls and
  overlays after apply, undo, reload, save, and restart.
- [ ] Place/copy/rotate/remove scenery and place/copy/remove NPCs, then Undo,
  Redo, Discard, Save, and restart.
- [ ] Compare legacy and renderer-v2 visuals, roof handling, minimap, picking,
  and performance after edits.
- [ ] Build a private client and server from the saved worker branch and verify
  the same terrain appears before any manager merge or live rollout.

## Safety and Performance Requirements

- The server is authoritative for permission, session state, definitions,
  limits, base revision, edit acceptance, and save.
- No editor packet accepts an arbitrary filesystem path or unbounded list.
- All persisted writes use validation and atomic replacement.
- No normal workflow writes authored map changes from the detached live
  deployment.
- No routine workflow depends on stash, destructive reset, forced worktree
  removal, or ignored local files as the only copy of edits.
- Expensive geometry/collision rebuilds are bounded and measured.
- Brush inputs coalesce rather than flooding commands or rebuilding per mouse
  frame.
- Logs identify session, editor, operation, affected count/region, result, and
  save hash without logging credentials or excessive per-tile spam.
- A failure defaults to rejecting or reverting the draft operation, not
  leaving a silent client/server disagreement.

## Decisions

- The editor is an administrator/world-builder tool with a separate
  disabled-by-default environment gate.
- The first supported editor UI is desktop-only; Android remains unchanged.
- The first mutating release permits one active editor at a time.
- Navigate and Inspect are explicit modes so painting never also means walking.
- Terrain field checkboxes control a field mask; unchecked fields are never
  changed.
- Terrain, scenery, NPC, and boundary are exclusive top-level modes.
- Click is a stamp and click-drag is a real brush stroke; no synthetic rapid
  click loop is used.
- Diagonal walls expose a base definition plus Rotate/orientation control. The
  `+12000` archive encoding is never required user input.
- Undo/redo and true live-state Discard are prerequisites for broad brush use.
- Terrain edits use a versioned draft/patch representation and are
  materialized atomically; the full ZIP is not rewritten per click.
- Private client/server visual validation is required before manager merge and
  live release.

## Open Questions for Later Phases

These do not block the Phase 0/1 foundation:

- Whether successful Save keeps the editor session open with a new base
  revision or closes it by default.
- Whether the durable authored source remains a terrain overlay in releases or
  is always materialized into both complete archives.
- The exact recovery journal retention and cleanup policy.
- Whether boundary editing belongs in the first mutating entity phase or a
  separate branch after terrain stamps.
- Which texture thumbnail source and cache provide the clearest low-cost UI.
- Whether Paint mode should also support an explicit `paint under player`
  hotkey after cursor painting is stable.

## Completion Criteria

This plan is complete only when:

- an authorized administrator can enter a private in-game editor and ordinary
  users/public deployments cannot;
- terrain, scenery, boundary, and NPC modes are mutually exclusive and usable
  without memorized command syntax;
- all raw terrain fields can be inspected, copied, selectively stamped, and
  brushed with adjustable radius;
- diagonal walls can be selected and rotated visually in both directions
  without manual `12000` arithmetic;
- object/NPC/boundary edits use deterministic persistent identities and files;
- every mutation supports safe Undo, Redo, Discard, recovery, and atomic Save;
- client rendering/picking/minimap and server collision/projectiles agree after
  edits, reloads, restarts, and materialization;
- client and server landscape outputs match and unrelated archive data remains
  unchanged;
- relevant automated tests, private manual client/server tests, and release
  checks pass; and
- the completed worker work is reviewed, merged, published, packaged, and
  deployed through the manager workflow rather than edited directly on live.
