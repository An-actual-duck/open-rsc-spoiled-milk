# World Layer Capacity Exploration Plan

Status: discussion and architecture study

Branch: `docs/world-layer-capacity-exploration`

Started: 2026-07-17

Current discussion: explicit layered coordinates and geographic map alignment

## Purpose

This is a living planning document for deciding how Spoiled Milk should organize
future underground world space. The immediate concerns are that existing
underground content feels crowded and fragmented, some underground ladders act
as long-distance fast travel rather than geographic vertical movement, and a
deeper category of underground content may be useful.

The study compares expanding within the current underground plane,
reorganizing existing underground content, reserving separated dungeon
regions, supporting true instances, and adding a true fifth plane. It is
intentionally discussion-first: the architecture should emerge from a series
of smaller decisions rather than being selected before the intended world
experience is clear.

## Scope Boundary

In scope:

- documenting the current world-coordinate and terrain-archive architecture;
- auditing capacity, compatibility assumptions, and content dependencies;
- building an underground-area inventory and entrance/exit graph;
- defining possible coordinate-allocation and entrance-correspondence rules;
- comparing architecture options and migration risks;
- recording owner decisions as the discussion progresses;
- planning private-server validation for a later implementation phase.

Not authorized by this plan:

- editing terrain archives;
- relocating existing map content;
- changing runtime client or server code;
- changing ladders, portals, teleports, respawns, quests, or placements;
- modifying player databases or live world data;
- deploying experimental map work to the public server.

Documentation may be revised as decisions are made. Implementation requires a
separately approved focused plan.

## Executive Finding

The first audit indicates that plane 3 is not close to exhausting its raw map
space. Its practical problem is fragmented allocation and distributed content
ownership rather than physical capacity.

Only about one fifth of the archived plane-3 sector grid shows varied terrain
or placement usage. More than 350 sectors appear blank or unallocated at this
level of inspection. These figures are not permission to build in those
sectors: apparently blank areas may still be buffers, inactive variants, or
targets of hard-coded scripts. They do show that a formal inventory and
allocation policy could unlock substantial existing capacity.

A provisional direction worth discussing is to reserve organized shallow- and
deep-underground districts within plane 3 before undertaking a true fifth-plane
compatibility project. This is not yet the chosen architecture.

## Current Coordinate Architecture

The world is represented primarily by a two-dimensional `(x, y)` coordinate.
The plane is encoded in the Y coordinate rather than stored as an independent Z
dimension:

| Plane | Current role | Logical world-Y band |
| --- | --- | ---: |
| 0 | Surface | `0-943` |
| 1 | First floor | `944-1887` |
| 2 | Second floor | `1888-2831` |
| 3 | Underground | `2832-3775` |

The central relationship is:

```text
plane = floor(worldY / 944)
baseY = worldY - (plane * 944)
```

This means that a nominally vertical surface-to-underground connection at
surface `(x, y)` would arrive near `(x, y + 2832)` on plane 3.

The four plane roles are enforced by more than naming convention:

- the server world loader has a four-floor load loop;
- shared floor calculations divide Y by `944`;
- generic stair and ladder movement assumes the existing plane cycle;
- client wall and roof model grids have four plane slots;
- surface scene construction handles upper floors differently from the
  standalone underground scene;
- membership-area checks repeat some surface rectangles across four planes;
- the in-game terrain editor accepts only planes `0-3`;
- World Builder and terrain-save validation currently understand only the
  existing four-plane layout.

The configured server `MAX_HEIGHT` being greater than `3776` does not make the
remaining coordinate range a fifth plane. The loader, client, editor, and plane
semantics still stop at four planes, and the remaining range is not a complete
944-tile band.

## Focused Study: Explicit Layered Coordinates

### Owner Intent

The desired direction is more fundamental than increasing the packed-Y stride
or appending another band. The coordinate system should represent levels as
genuinely separate spaces so that:

- the surface, upstairs, underground, and future deep layers may all use the
  same readable `(x, y)` grid;
- expanding one layer does not cause its Y coordinates to cross into another
  layer's band;
- a surface location and the area physically above or below it can share the
  same `(x, y)` coordinates;
- coordinate displays, editor navigation, scripts, and planning documents no
  longer require mental addition or subtraction of legacy band offsets;
- the organization of the map reflects geographic relationships rather than
  inherited teleport destinations.

### Clarification of the Existing Offsets

The level stride in active world coordinates is exactly `944`, rather than
approximately `1200`. Other historic offsets can make the system appear less
regular:

- `2304` is added while translating world X to archive sector X;
- `1776` is added while translating normalized Y to archive sector Y and is
  also sent as the client plane-height offset;
- `944` is sent to the client as the distance between floors;
- plane 3 begins at packed Y `2832`, which is `3 * 944`.

The current formulas combine a logical world coordinate, an archive-sector
coordinate, and a protocol/client offset. Separating those concepts is an
important part of making the coordinate system understandable.

### Where Packed Y Is Currently Authoritative

| Domain | Current representation | Layered-system implication |
| --- | --- | --- |
| Server entity position | `Point` contains only short `x` and packed `y` | Needs a level-bearing coordinate or an explicit transitional wrapper |
| Region and tile storage | Region maps are keyed by packed region X/Y | Region identity must include the level |
| Distance and visibility | Packed-Y distance naturally separates floors | Every proximity operation must explicitly reject different levels |
| Collision and pathfinding | Tile lookup receives packed X/Y | Tile and path queries must carry level identity |
| Areas and wilderness | Rectangles and many special checks use packed Y | Area definitions must become layer-aware |
| Static placements | JSON positions contain only `X` and packed `Y` | New schema needs `level` plus normalized `Y` |
| NPC roaming bounds | Start/min/max positions use packed Y | Every bound must share an explicit level |
| Plugin and quest logic | Literal coordinates and `teleport(x,y)` are common | Ambiguous two-argument destinations must be migrated or adapted |
| Player persistence | Database stores packed `x` and `y` | Needs a preservation-safe versioned read/write strategy |
| Wire protocol | World info carries a plane, but many positions still use packed Y | Existing clients need a legacy coordinate codec at the boundary |
| Client terrain loading | Plane is explicit, while world-Z offsets still compensate for packed Y | The client is partly layered already but not consistently normalized |
| Terrain archive | Entry name contains plane and sector Y is based on normalized Y | Already close to the desired layered representation |
| World Builder | Terrain plane exists, but placement validation accepts only packed X/Y | Project and overlay schemas need a versioned layered adapter |

The broad dependency scan found 74 server source files using entity Y access,
124 plugin files containing point construction, teleportation, or location
assignment, 40 coordinate-bearing location JSON files, and 31 server, plugin,
or client files directly mentioning the `944` stride or associated legacy plane
values. These are audit indicators rather than exact migration counts, but they
show that this is an architecture program rather than a small formula change.

### Terrain Is Already Partly Layered

The terrain archive is the most favorable part of the conversion. An archive
entry already has an explicit plane in its key:

```text
h{plane}x{sectorX}y{sectorY}
```

Its sector Y is calculated from normalized within-plane Y, not directly from
packed world Y. The runtime editor currently insists that the supplied packed Y
and plane agree, but that is a validation rule rather than a limitation of the
sector record itself.

For existing terrain, a layered coordinate can therefore be recovered without
moving or rewriting tile bytes:

```text
legacyPlane = floorDiv(packedY, 944)
layerY      = floorMod(packedY, 944)
```

The archive plane and normalized sector coordinate can then address the same
tile. This makes a lossless compatibility adapter realistic for all existing
four-plane terrain.

### Selected Canonical Coordinate Semantics

The owner selected the following conceptual model:

```text
WorldCoordinate(x, y, level)
```

with signed, geographically meaningful levels:

| Canonical level | Meaning | Existing legacy plane |
| ---: | --- | ---: |
| `0` | Surface | 0 |
| `+1` | First floor above surface | 1 |
| `+2` | Second floor above surface | 2 |
| `-1` | Underground | 3 |
| `-2` | Future deep underground | none |

Named layer identifiers could accompany the signed value, but the sign is
useful: `above()` and `below()` become obvious operations, while current plane
3 no longer misleadingly appears to be three floors above the surface.

Level values are sequential rather than limited to the presently existing
maps. Moving up one physical level adds one; moving down one physical level
subtracts one:

```text
up:   (x, y, level) -> (x, y, level + 1)
down: (x, y, level) -> (x, y, level - 1)
```

Therefore a ladder at surface `(100,400,0)` should ordinarily lead to
`(100,400,-1)`, and another descent at that coordinate should lead to
`(100,400,-2)`. The corresponding upper levels are `(100,400,+1)`,
`(100,400,+2)`, and so forth.

The canonical X/Y values should be ordinary map coordinates within that layer,
not values already transformed for archive or protocol use. Archive sector
offsets and legacy client offsets would belong in dedicated codecs.

### Exact Legacy Conversion

For the existing four layers, conversion can be reversible:

```text
surface:      packedY = y
first floor:  packedY = y + 944
second floor: packedY = y + 1888
underground:  packedY = y + 2832
```

Examples:

| Legacy coordinate | Canonical layered coordinate |
| --- | --- |
| `(120,648)` | `(120,648,0)` |
| `(120,1592)` | `(120,648,+1)` |
| `(120,2536)` | `(120,648,+2)` |
| `(120,3480)` | `(120,648,-1)` |

This immediately gives administrators, scripts, diagnostics, and World Builder
a neat coordinate vocabulary without changing where existing content is.

A future level `-2`, or a normalized Y outside `0-943`, has no lossless mapping
to the current packed four-band convention. Such locations require an extended
custom-client protocol or another explicit compatibility policy.

The server `Point` currently stores X and Y as signed shorts, and custom
movement paths also serialize coordinates through short-sized fields. True
independent layer extents remove the 944-tile band collision, but they do not
automatically remove those numeric limits. Intended maximum X/Y dimensions
must be chosen before the value type and custom protocol are finalized.

### Normalizing Coordinates Does Not Align Content

There are two separate operations:

1. **Coordinate normalization:** reinterpret an existing packed point as
   `(x, normalizedY, level)` without moving it.
2. **Geographic alignment:** relocate an underground or upstairs area so its
   canonical X/Y matches the relevant surface footprint.

Normalization is deterministic and can be lossless. Alignment is a content
design and migration operation.

For example:

- Surface `(499,469)` currently connects to packed underground `(499,3295)`,
  which normalizes to `(499,463,-1)`. It is already geographically close and
  could potentially be aligned with a very small offset.
- Surface `(223,110)` currently connects to packed underground `(446,3368)`,
  which normalizes to `(446,536,-1)`. A layered coordinate system exposes the
  mismatch but does not fix it. The dungeon, entrance, or the classification of
  that edge must change.

This distinction allows the engine migration to preserve behavior exactly
before any risky map relocation begins.

### Vertical Anchors and Walkable Arrival Tiles

The existing generic ladder behavior supports the owner's expectation that
most vertical realignment should be geometrically straightforward:

- one-tile ladders preserve the player's X and normalized Y while applying the
  legacy floor change;
- larger staircase or ladder objects keep the same general anchor and apply a
  small direction- and object-size-based landing offset;
- the large geographic mismatches come from explicit special-case teleports,
  not from the generic vertical formula.

The layered design should distinguish an entrance's **geographic anchor** from
its **walkable arrival tile**. Paired ladder or stair anchors should share exact
X/Y by default. If the object occupies the anchor tile, the arrival may be an
adjacent walkable tile derived from its direction and footprint. That local
collision adjustment should not be treated as a geographic mismatch.

This yields a clean authoring rule:

```text
source anchor:      (100,400, 0)
destination anchor: (100,400,-1)
arrival tile:       destination anchor plus a small local object offset
```

For self-contained areas, geographic alignment can often be implemented as a
rigid translation of the entire terrain and placement footprint. Fine tuning
should usually be limited to boundaries, walkable landing tiles, and conflicts
with other content already allocated on the destination level. Script,
placement, quest, and persistence references still require exhaustive
migration even when the geometry moves cleanly.

### Required Separation Invariants

True layering is achieved only if the level participates in every identity and
proximity decision. At minimum:

- `(x,y,0)` and `(x,y,-1)` must resolve to different regions and tiles;
- entities on different levels must never see, collide with, target, trade
  with, follow, attack, or path to one another;
- objects, walls, NPCs, and ground items must be keyed by level as well as X/Y;
- visible-region and object-snapshot cache keys must include level;
- point equality and hashing must include level;
- area and wilderness checks must state which levels they cover;
- stairs, ladders, and portals must be the only mechanisms that cross levels;
- a vertical transition should preserve X/Y by default and change only level;
- an intentional offset or transport edge must be explicit data;
- save, login, logout, reconnect, death, and recovery must retain level;
- archive and protocol conversion must occur only at named compatibility
  boundaries, never through scattered arithmetic.

The current packed-Y distance acts as an accidental safety barrier between
floors. Removing it before the region, equality, cache, view, and interaction
systems are level-aware would cause serious cross-floor leakage.

### Feasibility by Scope

| Scope | Difficulty | What it achieves | What it does not achieve |
| --- | --- | --- | --- |
| Normalized display and editor notation | Low | Readable `(x,y,level)` coordinates | Runtime separation or extra capacity |
| Layered authoring schema with packed runtime adapter | Moderate | Clean future content data and World Builder organization | Removes neither the 944 runtime ceiling nor legacy packing |
| Layered server core with legacy wire/storage adapters | High but tractable | True runtime separation for existing layers; centralizes packing | New deep layers still cannot be shown by unmodified clients |
| Fully layered custom client/server and expanded extents | Very high | Independent map sizes and arbitrary additional levels | Automatic compatibility with legacy clients |
| Relocate existing maps into geographic alignment | High content risk | Actual surface/above/below correspondence | Engine separation by itself |

The terrain conversion is comparatively easy. Region identity, entity
visibility, two-argument script APIs, persistence, and exhaustive behavior
parity are the hard parts.

### Recommended Temporary Migration Architecture

If this direction is selected, the safest shape is an explicit canonical model
surrounded by temporary compatibility adapters:

```text
legacy maps and copied data
            |
 named one-way coordinate codec
            |
layered project / layered runtime
```

The codec for existing content would own all `944` packing and unpacking. New
core code would not calculate level from Y. This provides three benefits:

- old data can be imported without an immediate destructive rewrite;
- conversion can be tested before the layered project becomes authoritative;
- parity tests can prove that every old coordinate round-trips exactly.

This codec is migration infrastructure, not a promise that the layered runtime
will permanently emit or accept packed-Y maps. It may also support a temporary
development bridge while the server and client are converted, but that bridge
should have an explicit removal gate.

Changing the semantics of the existing `Point.getY()` in place would be
especially dangerous because current callers silently assume packed Y. A safer
implementation study should compare introducing a new immutable layered point
type against evolving `Point` through explicit transitional methods such as
`getLayerY()` and `toLegacyPackedY()`. Ambiguous two-argument constructors and
teleports should eventually be confined to a legacy adapter.

### Preservation-Safe Migration Sequence

No phase below is authorized yet. They describe how an eventual implementation
could avoid combining engine conversion and map relocation.

1. **Define semantics and codecs.** Choose level identifiers, coordinate bounds,
   and exact reversible mappings for all current locations.
2. **Add read-only auditing.** Inventory coordinate literals, placements,
   areas, teleports, persistence fields, archive entries, and packet paths.
3. **Introduce layered value types.** Add level-aware points, rectangles,
   region keys, and transition destinations without changing behavior.
4. **Prove source parity.** Exhaustively round-trip all terrain, placements,
   telepoints, and copied player coordinates through the codec.
5. **Make the server world layer-aware.** Migrate region storage, tiles,
   collision, pathing, visibility, entity equality, caches, and interaction
   checks, using temporary adapters only where the conversion sequence requires
   them.
6. **Version content schemas.** Allow old packed placement files to be read, add
   explicit-level output, and update World Builder validation and manifests.
7. **Version persistence.** Use copied databases and additive fields or another
   reviewed migration strategy; never reinterpret live player rows in place.
8. **Normalize the custom client.** Remove internal packed-Y assumptions and
   establish the layered protocol contract.
9. **Validate unchanged gameplay.** Run current maps with no relocations and
   compare terrain, collision, routes, quests, visibility, and saved positions.
10. **Align maps area by area.** Move approved complexes only after the engine
    model is stable, using explicit old-to-new manifests and recovery redirects.
11. **Consider expanded extents and deep levels.** Add these only after the
    custom layered path and format incompatibility checks are proven.

### Geographic Alignment Policy to Develop

Once coordinates are normalized, each area can be evaluated against its
surface footprint:

- **Exact stack:** matching X/Y footprint on adjacent levels. Preferred for
  buildings, basements, mines, sewers, and ordinary vertical ladders.
- **Local offset:** small documented adjustment for terrain shape, wall
  thickness, or entrance orientation.
- **Regional stack:** kept inside the corresponding surface-region allocation
  when exact overlap is impossible.
- **Non-geographic destination:** explicitly classified as transit, magical,
  quest-space, or instance-like content.

Alignment should operate on whole area manifests, not just ladder endpoints.
The manifest must translate terrain bounds, placements, NPC roam areas, all
entry and exit edges, quest checks, failure paths, and old saved locations.

The surface map should be treated as the reference grid unless a later decision
establishes a different canonical geographic layer.

### Focused Decisions Still Needed

Before this can become a selected architecture, decide:

1. Whether the first implementation goal is readable normalized coordinates,
   true server separation, or a fully expanded custom-client map model.
2. What independent X/Y bounds each layer should ultimately support.
3. Whether existing content should first be normalized in place and aligned
   later, or whether selected low-risk areas should be aligned during the
   migration pilot.
4. How explicit long-distance ladder destinations should be reclassified or
   reorganized once ordinary vertical entrances use exact anchors.

## Terrain Archive Organization

The current authoritative terrain is duplicated at:

- `server/conf/server/data/Custom_Landscape.orsc`
- `Client_Base/Cache/video/Custom_Landscape.orsc`

At the time of this audit, the two copies have the same SHA-256 digest:

```text
d50089fcc81d51aa461567f4416a8f1a329ed439bcf64606ca1441c600e7229b
```

There is also a differently hashed historical-looking archive under
`server/conf/server/defs/locs/`. No active loader, editor, import script, or
World Builder reference was found for it. It should be treated as a naming and
inventory hazard, not assumed to be authoritative or disposable.

The `.orsc` file is a ZIP archive whose entries use names such as:

```text
h{plane}x{sectorX}y{sectorY}
```

Each sector is `48 x 48` tiles. Each tile has a ten-byte terrain record covering
elevation, ground texture, overlay, roof, horizontal and vertical walls, and a
diagonal value. The documented coordinate conversion is:

```text
sectorX = floorDiv(worldX + 2304, 48)
sectorY = floorDiv(baseY + 1776, 48)
localX  = floorMod(worldX + 2304, 48)
localY  = floorMod(baseY + 1776, 48)
```

The archive currently has 1,771 entries and no duplicate entry names. The
observed unique sector ranges are:

| Plane | Entries | Sector-X range | Sector-Y range |
| --- | ---: | --- | --- |
| 0 | 445 | `48-69` | `36-57` |
| 1 | 444 | `47-68` | `36-57` |
| 2 | 441 | `48-68` | `37-57` |
| 3 | 441 | `48-68` | `37-57` |

Sector dimensions do not divide evenly into the 944-tile logical plane height,
so archive border sectors must not be mistaken for additional playable plane
space.

## Preliminary Plane-3 Capacity Audit

The plane-3 archive is a complete 21-by-21 sector rectangle containing 441
sectors. A preliminary terrain-content scan found:

- 83 sectors with more than one distinct terrain tile record;
- 358 sectors that appear uniform or nearly blank;
- approximately 84 plane-3 sectors touched by a conservative scan across the
  available location JSON files.

The terrain and placement results closely correspond. Large apparent gaps
include northern rows, several central rows, much of the eastern side, and many
smaller internal gaps between legacy complexes.

This is only a capacity signal. Before reserving coordinates, the inventory
must also check:

- inactive and alternate map-data variants;
- all base and conditional placement files;
- MyWorld addition and removal overlays;
- Java coordinate literals and rectangular area checks;
- quest stages and recovery locations;
- teleport, ladder, portal, and door destinations;
- saved-player locations;
- intentionally blank isolation and renderer-loading buffers;
- sector-boundary and plane-boundary behavior.

## Content Layers Affected by Relocation

Moving an underground area is not equivalent to copying its terrain. A complete
migration may need to update:

1. Terrain, overlays, roofs, walls, elevation, and collision.
2. Scenery and boundary placements.
3. NPC spawn points and roaming bounds.
4. Ground-item placements and respawns.
5. Central object telepoints.
6. Hard-coded ladder, door, portal, spell, boat, and transport destinations.
7. Quest coordinate checks, cutscenes, failure paths, and stage recovery.
8. Minigame entry, exit, logout, and emergency recovery behavior.
9. Death and respawn behavior.
10. Players whose saved coordinates still point to the old area.
11. Client/server terrain parity and World Builder compatibility.

Current world population is assembled from base scenery, boundary, item, and
NPC files plus conditional feature files and MyWorld overlays. World Builder's
current authored bundle covers terrain and the supported MyWorld scenery/NPC
overlays, but it does not own every legacy placement file or coordinate embedded
in Java. It therefore cannot perform a complete legacy-dungeon migration by
itself.

## Entrance and Exit Behavior

The existing transition graph mixes geographic vertical movement and explicit
long-distance travel.

Generic stairs and ladders can use the shared floor calculation. That logic
preserves the normalized X/Y position while moving through the established
surface, upper-floor, and underground relationships. Many exceptions bypass
that behavior and teleport to explicit coordinates.

The central `ObjectTelePoints.xml` currently contains only 20 connections. The
audit found seven cross-plane connections and numerous same-plane transitions,
including many whose endpoints are far apart. This XML is only one part of the
graph; ladder plugins, quests, sewers, doors, portals, pools, boats, rings,
spells, minigames, and other content contain additional explicit edges.

Examples of the mixed semantics include:

- Surface `(499,469)` to underground `(499,3295)`. The underground normalized
  point is `(499,463)`, only six tiles from geographic alignment.
- Surface `(223,110)` to underground `(446,3368)`. The normalized underground
  point is `(446,536)`, hundreds of tiles from the entrance.
- Hard-coded ladder handling includes other distant surface-to-underground and
  underground-to-underground transitions.

An eventual entrance policy could classify edges as:

- **Vertical:** exact or near-exact normalized correspondence.
- **Regional:** located beneath the same surface territory, with an intentional
  offset to prevent overlap.
- **Transit:** deliberate fast travel whose transport role should be explicit.
- **Magical or exceptional:** geographic correspondence intentionally does not
  apply.

This classification is a discussion proposal, not a decided rule.

## Player Persistence, Death, and Recovery

Player X/Y coordinates are stored directly in the player database and restored
as the login location. The inspected load path does not automatically detect
that a dungeon has moved and redirect a saved player to its replacement.

The current MyWorld configuration uses surface respawn `(120,648)`. Ordinary
player death ultimately teleports the player to that configured location after
combat and drop processing. That reduces one form of underground stranding but
does not cover:

- a player logged out inside an area before it moved;
- reconnecting during a quest or minigame;
- quest-specific failure and recovery destinations;
- special emergency teleports;
- stale caches or state that reference the former area;
- safe recovery when a destination archive or placement set is absent.

Every future relocation needs an explicit old-area login redirect and a policy
for when that redirect can safely be removed.

## Client Loading and Legacy Compatibility

The custom protocol can numerically carry a plane value larger than three in
some fields, but that does not make a fifth plane compatible. Both client and
server contain structural four-plane assumptions.

A true fifth plane would at minimum require auditing or changing:

- server terrain loading and post-load compression loops;
- region bounds and maximum world height;
- shared floor and vertical-travel calculations;
- membership and other repeated area checks;
- client wall and roof model arrays;
- surface/upper/underground scene construction and roof rules;
- terrain archive availability for plane 4;
- in-game editor plane validation and naming;
- World Builder import/export validation;
- world-info, movement, teleport, and coordinate packet paths;
- private and release client tests;
- every supported legacy-client parser and renderer.

Unmodified legacy clients should be presumed incompatible with a fifth plane
until proven otherwise. A plane-3 static region is much more likely to remain
compatible because it uses the established coordinate and archive contract.

The recent hard-area-load scenery work also makes distant-transition testing
important. Any later map experiment should validate full object and wall
baselines after teleport, death, login, logout, reconnect, and rapid travel
between dense and quiet areas.

## World Builder Compatibility

World Builder works from isolated copies and currently targets the existing
`.orsc` MyWorld layout. Its terrain addressing and validation understand planes
zero through three. It can support formally reserved static regions on plane 3
without changing the archive format.

A fifth plane requires a prior World Builder/tooling project. Reorganizing
existing legacy content also requires a broader manifest or migration audit
because the current authored bundle is not authoritative for all base and
feature placements or Java logic.

Any later implementation should retain the existing safeguards:

- edit isolated working copies rather than active archives;
- require identical client/server terrain before import;
- require compatibility fingerprints;
- import only while the private target is offline;
- compare unrelated sectors and placement records for unintended changes.

## Distribution and Format Compatibility Direction

### Broader Project Context

The owner expects Spoiled Milk to diverge progressively from legacy OpenRSC
rather than requiring every future optimization to remain readable by legacy
tools and clients. Major capabilities may eventually be distributed as easier
to use standalone packages, including:

- the standalone World Builder;
- renderer/client distributions;
- map packages with explicit compatibility requirements; and
- a layered-map capability that a map may declare as required.

The Layered Maps product must not be limited to the Spoiled Milk world. Its
end-user workflow should accept a compatible existing RuneScape Classic-derived
world, convert a copy into the layered format, help reorganize its vertical
areas, and let the owner inspect and privately run the result before exporting
it to the target game. Spoiled Milk is the first demanding validation target,
not the only intended input.

This context is relevant to the coordinate architecture because it removes the
need to disguise a true layered world as packed-Y data forever. Compatibility
should be explicit and versioned rather than inferred from a similar-looking
archive.

### Selected Compatibility Boundary

The intended divergence is:

- pre-layering maps remain identified as legacy packed-Y maps;
- post-layering maps use a new explicit layered coordinate format;
- old map readers and old World Builder releases are not expected to read the
  new format;
- the new format may be marked incompatible with legacy clients or servers;
- maintaining a safe one-way legacy importer is desirable, but making layered
  maps round-trip back into the old format is not a design requirement;
- a format or map package must fail clearly when its required capability is
  absent.

This replaces the earlier provisional assumption that all existing layers
would need permanent packed-wire compatibility. A temporary compatibility
codec may still be useful during migration and parity testing, but it is not
the architectural end state.

### Existing World Builder Foundation

The first World Builder release already establishes several useful concepts:

- a named `layoutAdapter`, currently `spoiled-milk-repository-v1`;
- versioned project, export, and import-receipt schemas;
- source and content fingerprints;
- strict rejection of unknown or changed targets;
- exact authored-file inventories and transaction receipts.

The current adapter is deliberately legacy-specific:

- terrain entries must match `h[0-3]x...y...`;
- scenery and NPC overlay positions contain only packed `X` and `Y`;
- the authored bundle contains the terrain archive and four MyWorld overlay
  states;
- the release version is currently expected to match its companion Spoiled
  Milk release.

The layered system should be introduced as a second named adapter and schema,
not as an ambiguous extension silently accepted by the v1 reader. A conceptual
name would be `spoiled-milk-layered-v1`; the final name remains undecided.

The legacy World Builder release can remain available for packed-Y projects.
A layered-capable release could either support both adapters explicitly or
provide a separate conversion workflow, but it must never guess which
coordinate model a project uses.

### Recommended Artifact Separation

The phrase "layered maps plugin" is useful from an end-user packaging
perspective, but the underlying capability affects core point, region, entity,
collision, persistence, protocol, and client behavior. It is unlikely to be a
safe hot-loaded plugin in the current server architecture.

A cleaner distribution model separates three artifacts:

1. **Layered-world engine capability**
   - Provides level-aware server, client, protocol, persistence, and tooling
     behavior.
   - Exposes a stable capability identifier and version.
2. **Layered map package**
   - Contains terrain and authored world data in a versioned coordinate format.
   - Declares the layered-world capability version it requires.
3. **Layered-capable World Builder**
   - Reads, validates, edits, exports, and imports that map format.
   - Refuses targets whose engine capability or definitions do not match.

These may be composed into an end-user **Layered Maps conversion workspace**.
The workspace can bundle or launch the compatible World Builder and private
client/server test harness while retaining their independent versions and
ownership boundaries. From the user's perspective this is a standalone
conversion module; internally it remains a tested bundle of the engine
capability, converter, Builder, target adapter, and validation tools.

The bundled interface need not be a one-for-one copy of the standalone World
Builder. Its initial product can be a focused **conversion workbench and dev
launcher** assembled from Builder capabilities. It needs to inspect inferred
levels and moves, show reports and diffs, permit corrective map edits, navigate
or teleport around the staged world, and launch/stop a private test client and
server easily. General-purpose authoring features can remain in the standalone
Builder unless conversion review actually needs them.

The intended initial installation is deliberately developer-oriented: download
the Layered Maps tool folder and extract it into the root of the repository to
be converted. The expected user is a map author or server maintainer with a
source checkout, not a player installing a graphical game option. Extraction
installs only the contained tooling and metadata; it must not rewrite maps,
runtime code, configuration, archives, or player data. A separately invoked
preflight identifies the repository adapter and previews all later changes.
Unknown layouts are refused or require an explicit adapter rather than being
treated as approximately compatible.

A renderer/client release is a separate compatibility dimension. A map should
require a renderer capability only when it truly depends on renderer-specific
assets or behavior; layered coordinates alone should depend on the
layered-world capability.

The downloadable product may still be presented to users as a plugin or
expansion. Internally, describing it as a **capability bundle** avoids promising
that it can be enabled or disabled safely at runtime.

### Proposed Capability-Oriented Manifest

A future map manifest should express requirements directly rather than relying
only on matching product release numbers. A conceptual subset is:

```json
{
  "mapFormat": "spoiled-milk-map",
  "schemaVersion": 2,
  "coordinateModel": "signed-layered-v1",
  "requiresCapabilities": {
    "world.layered-coordinates": ">=1 <2"
  },
  "layers": [
    {"level": 0, "role": "surface"},
    {"level": -1, "role": "underground"}
  ]
}
```

The exact version syntax and fields are undecided. The important properties
are:

- coordinate model is explicit;
- required engine/tool capabilities are explicit;
- declared levels and extents are explicit;
- unknown schema or capability versions are rejected;
- definition and source fingerprints remain available where exact content
  coupling is required;
- incompatibility is reported before a server, client, or map file is changed.

Capability identifiers should be more stable than branding. A map should not
need to know whether the supporting distribution was installed from a monolith,
standalone package, or user-facing plugin bundle.

### Map Package Versus Complete Content Package

The current World Builder export is a focused authored-map patch, not a
portable complete world. It covers terrain plus MyWorld scenery and NPC
addition/removal overlays. It does not contain every base placement,
conditional feature file, quest plugin, teleport script, definition, or player
migration.

Future packaging should distinguish:

- **Map patch:** tied to a specific compatible Spoiled Milk source and content
  fingerprint.
- **World content module:** contains or declares every coordinate-bearing
  placement, transition, definition, and script it owns.
- **Engine capability:** supplies runtime support such as layered coordinates.

Calling all three a map would make installation failures and compatibility
requirements difficult to diagnose. Geographic realignment of established
content will likely need a source-coupled world content module or integrated
release, even if new self-contained maps can eventually be more portable.

### Legacy-to-Layered Conversion

A reusable one-way converter is a required Layered Maps deliverable, not an
optional Spoiled Milk migration convenience. Its target is any compatible
RuneScape Classic-derived world for which the converter can inventory the map,
placements, and transition owners. Support should be capability- and
adapter-driven rather than restricted to one Spoiled Milk fingerprint, while
unknown structures and unresolved coordinate owners must still fail safely.

The conversion is a staged workspace operation:

1. Copy, discover, and fingerprint the source without changing it.
2. Inventory terrain archives, packed coordinate bands, placements, scripts,
   ladders, stairs, portals, teleports, and every other known transition owner.
3. Decode the legacy bands into explicit levels, initially mapping surface to
   `0`, current upper floors to positive levels, and the current underground
   band to `-1` without relocating anything.
4. Emit and validate an unchanged layered normalization snapshot. This is the
   parity checkpoint and rollback reference required before reorganization.
5. Build a directed transition graph. Infer candidate vertical relationships
   from paired destinations, interaction direction, object semantics, and
   scripts; never infer depth from the presence of a ladder model alone.
6. Identify movable terrain components around transition destinations using
   bounded flood-fill or connected-component analysis across non-void terrain.
   Include collision, boundaries, scenery, NPCs, ground items, and other owned
   placements when calculating the component's true extent.
7. Solve geographic alignment as explicit component translations. A normal
   up/down edge preserves its anchor X/Y. When a directional descent connects
   two components that both occupied the legacy underground band, keep the
   shallower component on `-1` and propose the destination component on `-2`.
8. Detect conflicts before moving anything: multiple incompatible anchors into
   one component, cycles with contradictory depth, terrain joined through
   unexpected non-void tiles, overlapping destinations, hard-coded coordinate
   owners, and placements outside the inferred component all require review.
9. Produce a deterministic best-effort staging result for every discovered
   component, including quest-heavy and high-dependency areas. Where constraints
   are ambiguous, preserve the safest available relationship or choose the
   converter's reported candidate without treating it as owner acceptance.
   Emit an old-to-new manifest, confidence and severity findings, transition
   rewrite report, warnings, and hashes with the layered staging project.
10. Open the result in the layered World Builder with source/destination
    overlays, inferred component bounds, transition classifications, and all
    unresolved findings visible to the owner.
11. Launch a private copied client/server workspace from the workbench. Provide
    convenient navigation between converted areas and flagged transitions so
    the map author can validate terrain, collision, placements, transitions,
    login, reconnect, death, and recovery without touching the target game.
12. Keep conversion, review, editing, and private testing entirely inside the
    staging workspace. None of those operations export changes into the target.
13. Make a separately invoked final export script the only target-mutating
    operation. Before mutation it must show the exact diff and unresolved
    findings, verify the source fingerprint has not changed, require explicit
    owner confirmation, create a backup, and write a transaction receipt and
    rollback material.

Conversion does not imply that the new project can be exported back to v1.
Once it uses level `-2`, expanded extents, layered-only metadata, or aligned
content, the incompatibility is intentional.

Automatic alignment is therefore best understood as a constraint solver plus
review workflow, not a blind rewrite. Cleanly separated terrain islands and
unambiguous reciprocal stairs should convert with high confidence. Shared
complexes, quest-driven edges, one-way travel, and incomplete source ownership
may still receive automatic provisional output, but every assumption remains
visible and editable in the staging workbench.

An owner may resolve a contradictory legacy relationship by editing terrain or
an entrance, reclassifying the edge as non-vertical transport, or explicitly
accepting a compatibility override. An override preserves the relationship but
does not let the conversion claim complete geographic alignment. The final
export presents every remaining conflict and requires explicit acknowledgement;
it does not require every legacy edge to become vertical.

Review is an additional safety boundary, not a substitute for converter
correctness. The workbench may automate every provisional relocation because
the result is isolated, but it must still preserve the source, report its
assumptions, make private testing convenient, and provide backup and rollback
for the eventual owner-authorized export.

Every conversion must generate both a human-readable report and a stable
machine-readable report. At minimum they record:

- every retained misalignment, its actual X/Y delta, transition class,
  rationale, and acknowledgement;
- unpaired, one-way, unmatched, or contradictory stairs and ladders;
- inferred level changes and every terrain-component translation;
- connected components whose void boundary was uncertain;
- hard-coded or otherwise unowned coordinate references;
- placements outside moved component bounds;
- overlaps, contradictory depth cycles, inaccessible areas, and areas with no
  known recovery exit;
- blocking findings, accepted exceptions, warnings, and informational
  oddities as distinct severities; and
- source fingerprints, adapter/capability versions, output hashes, and the
  corresponding old-to-new transformation manifest.

The reports should be useful to a map author, deterministic enough for version
control, and structured so automated tools or an AI assistant can inspect the
conversion without scraping Builder screenshots.

### Proposed Divergence Milestone

The eventual transition should have a named compatibility gate:

1. Freeze and document the last packed-Y map/tool format.
2. Publish the layered coordinate and package specifications.
3. Add a layered World Builder adapter and read-only legacy converter.
4. Complete and validate the layered engine capability.
5. Convert a copied Spoiled Milk map and prove unchanged behavior.
6. Begin geographic alignment in layered projects only.
7. Mark layered maps as incompatible with legacy readers and runtimes.
8. Retain legacy releases for users who intentionally remain on v1.

No particular release number or implementation milestone has yet been selected
for this gate.

### Packaging Questions Deferred from This Study

This plan should record, but not fully design, the larger distribution system.
A future packaging study may need to define:

- installer and uninstaller behavior;
- dependency resolution and capability discovery;
- signing, provenance, and integrity verification;
- compatibility-range syntax;
- release channels and update policy;
- ownership boundaries between engine, content, renderer, and tools;
- whether bundles modify a source checkout or install into a stable extension
  interface.

Those questions should not block deciding the layered coordinate and map format
contracts, but the contracts should avoid making them harder later.

## Architecture Options

### Option 1: Add a True Fifth Plane

Concept:

- Add plane 4 using another 944-tile Y band, nominally beginning at Y 3776.
- Give deep underground a distinct engine-level plane identity.

Benefits:

- clear semantic separation from the existing underground layer;
- approximately another full logical plane of coordinate space;
- no need to interleave new deep content around old plane-3 complexes;
- potential foundation for additional future layers.

Costs and risks:

- broad server, client, renderer, editor, World Builder, archive, limit, and
  regression-test changes;
- generic vertical movement currently encodes a four-plane cycle and would need
  new semantics;
- plane 3 has special underground rendering behavior that would not
  automatically apply to plane 4;
- unmodified legacy clients are unlikely to load or render it correctly;
- packet fields accepting the number do not guarantee end-to-end support;
- it adds engineering capacity without fixing the undocumented allocation and
  transition graph on plane 3.

Current assessment: technically possible as a dedicated compatibility project,
but disproportionate as the first response to present-day crowding.

Discussion update: the owner-preferred direction supersedes this specific
packed-band design. A deep layer would be canonical level `-2`, not legacy
plane 4 at another Y offset. Most of the engine and tooling audit remains
relevant, but the goal is to remove band coupling rather than extend it.

### Option 2: Allocate Deep Regions Within Plane 3

Concept:

- Reserve large currently unused plane-3 rectangles for future deep-underground
  content.
- Treat "deep" as a documented world category rather than a separate engine
  plane.

Benefits:

- reuses established client, server, archive, editor, and World Builder paths;
- likely preserves legacy-client access;
- uses abundant apparent free capacity;
- can be adopted incrementally without moving existing content first;
- supports both geographically aligned shallow spaces and intentionally remote
  deep districts.

Costs and risks:

- depends on a trustworthy coordinate and reference inventory;
- needs buffers and reservation ownership to prevent renewed fragmentation;
- deep destinations will not all correspond directly to surface coordinates;
- care is needed to prevent accidental walking or loading between unrelated
  nearby regions;
- remains one global coordinate space rather than true instancing.

Current assessment: strongest near-term candidate, subject to the desired
meaning of "deep underground."

### Option 3: Reorganize or Partition Existing Plane-3 Content

Concept:

- Establish coherent plane-3 zones and selectively move existing complexes
  into them.
- Potentially restore geographic correspondence for suitable entrances.

Benefits:

- reduces historical fragmentation;
- makes future capacity easier to understand;
- can group related surface and underground areas;
- may clarify which ladders are vertical and which are transport.

Costs and risks:

- high content-migration risk despite requiring little engine-format change;
- quests, NPCs, items, scenery, collision, teleports, saved players, and
  recovery paths can all retain old coordinates;
- mature quest-heavy areas have a much larger dependency surface than new or
  unreleased content;
- wholesale reorganization could spend substantial effort without improving
  gameplay proportionally.

Current assessment: useful selectively after the inventory exists. Low-risk,
custom, or clearly misplaced regions would be better early candidates than
established quest complexes.

### Option 4: Separated or Instanced Dungeon Regions

This option has two materially different interpretations.

#### Static isolated regions

- Reserve disconnected blocks on plane 3.
- Use explicit entrances and exits.
- Keep enough unused space between unrelated complexes.

This is compatible with the current architecture and is effectively a more
formal version of Option 2.

#### True player- or party-specific instances

- Allow multiple independent copies of the same dungeon to exist at once.
- Keep players, NPCs, items, scenery state, and collision isolated by instance.

The current coordinate model cannot distinguish two copies at the same X/Y.
World regions and entity registries are global by coordinate. Genuine
instancing would require an additional instance identity or a comparable
server-world abstraction, plus visibility, persistence, packet, logout,
reconnect, cleanup, and client-loading rules.

Disjoint coordinate blocks could simulate a limited number of copies, but that
consumes map space and is not a scalable instance architecture.

Current assessment: static isolated regions are immediately viable. True
instancing should be treated as a separate engine project if it is actually a
gameplay goal.

## Selected Regional-Layer Direction

The selected direction is:

1. Treat `-1` and `-2` as physical depth, not arbitrary dungeon categories.
2. Give ordinary descents exact geographic X/Y anchors by default.
3. Let each depth contain disconnected static terrain components separated by
   void or unallocated space; sharing a level does not imply connectivity.
4. Organize `-2` as geographically anchored regional deep networks rather than
   requiring one globally traversable underworld.
5. Allow compatible regional networks to gain deliberate terrain corridors or
   explicit transport links later without assuming those links in advance.
6. Preserve established content through the normalization checkpoint, then use
   the conversion workspace to propose component-by-component realignment.
7. Reclassify same-legacy-underground directional descents as candidate
   `-1`-to-`-2` relationships when the transition graph and component analysis
   support that interpretation.
8. Keep ambiguous topology, fast travel, relocation eligibility, instancing,
   allocation, and recovery decisions explicit rather than hiding them inside
   the converter.

## Discussion Modules

The remaining decisions are deliberately divided so they can be handled one at
a time.

### Current Module: Long-Distance Ladders and Transport

The focused layered-coordinate study above is the active discussion. Signed
geographic levels, exact default vertical anchors, and an explicit legacy-format
divergence are now selected. The Remaster Suite roadmap selects the package
boundary: a Layered Maps product module contains a layered-world engine
capability and separately versioned map packages, while World Builder remains a
separate tool and non-vanilla Spoiled Milk maps remain optional Content.

The first migration scope is also selected at roadmap level: normalize an exact
copied legacy world into explicit levels without relocating content, prove
terrain, placement, transition, script, persistence, and gameplay parity, and
only then begin geographic alignment or introduce level `-2`. That sequence is
now part of a reusable conversion workspace rather than a Spoiled Milk-only
map rewrite. Modules A through C and the existing-content portion of Module D
are resolved. Long-distance travel plus Modules E, F, and G still require owner
discussion before a focused implementation plan is authorized.

### Module A: Meaning of Deep Underground

Resolved: deep underground is a shared physical depth containing geographically
anchored regional networks. Regional networks are disconnected by default and
separated by void or reserved space. They may later be joined through deliberate
terrain or explicitly classified transport, but `-2` is not required to become
one globally traversable underworld. It is also not a free-form allocation
category for unrelated dungeons.

### Module B: Client Compatibility Target

Resolved for layered maps: future layered content does not need to remain
readable by legacy clients, servers, or map readers. Legacy packed-Y maps and
tools may remain available as their own version, while layered projects declare
their newer capability requirement explicitly.

### Module C: Geographic Correspondence

Resolved: an ordinary vertical edge preserves an exact geographic X/Y anchor.
The walkable arrival tile may carry a small explicit object-footprint offset,
but that offset does not redefine the anchor. The converter moves a terrain
component rigidly and may align multiple entrances automatically only when
they imply the same translation. It must not rotate, stretch, reshape, or
silently choose among contradictory anchors.

Misaligned edges are permitted only after they are classified as intentional
transport, magical, quest, exceptional, or acknowledged compatibility
overrides. All retained misalignments and conversion oddities appear in the
human- and machine-readable conversion reports. A clean export has no
unresolved vertical conflict, although it may contain explicitly acknowledged
non-geographic relationships.

### Module D: Existing Content and Fast Travel

Existing-content eligibility is resolved: every discovered area, including an
established quest dungeon, is eligible for automatic provisional conversion
and relocation inside the isolated workspace. Age, quest ownership, and risk
change the confidence and severity report rather than preventing the staging
result. Nothing becomes final until the map author reviews/tests the staged
world and invokes the separate target export script.

Still decide:

- whether distant ladder fast travel is a valued convenience;
- whether transport ladders should use a different object or presentation;
- whether underground-to-underground shortcuts should form a deliberate
  network; and
- whether the converter should preserve detected long-distance edges by
  default or suggest a geographically local replacement.

### Module E: Static Separation Versus True Instancing

Decide whether the goal is isolated static dungeon blocks or genuine private
player/party copies. The latter is not merely a coordinate-allocation policy
and would need a separate architecture study.

### Module F: Allocation Policy

After Modules A-E, define:

- sector-sized reservation units;
- district boundaries and ownership;
- minimum buffers between unrelated areas;
- shallow, deep, transport, quest, expansion, and experimental categories;
- reserved future-growth space for every complex;
- rules for crossing sector and plane boundaries;
- a machine-readable allocation registry or manifest, if desired.

### Module G: Migration and Validation

After an architecture is selected, define the phased migration order and a
private-server test matrix covering terrain parity, collision, placements,
entrances, exits, quests, death, login, logout, reconnect, distant teleports,
client scene baselines, and rollback.

## Open Owner Questions

1. Is true player/party instancing a desired feature, or is static isolation
   sufficient?
2. Should existing long-distance ladder travel be preserved, re-presented as
   transportation, or gradually removed?

## Living Inventory: Pending Discussion and Audit

The completed planning document will include an underground-area inventory
with at least these fields:

| Field | Purpose |
| --- | --- |
| Area ID and name | Stable reference independent of coordinates |
| Current bounds | Exact terrain and placement extent |
| Plane-3 sectors | Capacity and adjacency tracking |
| Surface association | Geographic or narrative parent region |
| Area category | Shallow, deep, transit, quest, minigame, or exceptional |
| Entrances and exits | Directed edges, requirements, and recovery paths |
| Terrain source | Archive sectors and fingerprint |
| Placement sources | Base, feature, and MyWorld files |
| Script owners | Plugins, quests, commands, and coordinate checks |
| Persistence risks | Saved player, quest cache, minigame, or item state |
| Legacy-client status | Known compatibility requirements |
| Migration eligibility | Preserve, review, candidate, or prohibited |
| Growth reservation | Buffer and future expansion needs |

The entrance/exit graph should record directed edges rather than assuming that
every route is reversible. Each edge should record source, destination,
interaction object, entrance class, requirements, quest state, one-way
behavior, failure destination, death/reconnect behavior, and implementation
owner.

## Later Private-Server Validation Outline

No map experiment is authorized yet. When one is eventually approved, a
private environment should validate at least:

1. Client and server terrain archives begin and end identical.
2. Only approved sectors and placement records change.
3. Terrain appearance, roofs, walls, elevation, and collision match the design.
4. Every entrance and exit works in both intended directions.
5. Requirements and quest gates remain correct.
6. NPCs, scenery, boundaries, and ground items load after walking and hard
   teleports.
7. Death, emergency teleport, logout, login, and reconnect recover safely.
8. Players saved at legacy coordinates follow the approved migration path.
9. Relevant quests and minigames pass their entry, progress, failure, and
   completion flows.
10. Legacy clients behave according to the selected compatibility target.
11. World Builder round trips the approved content without touching unrelated
    data.
12. A rollback restores terrain, placements, scripts, and copied test data.

## Related Documentation

- `docs/myworld/info/in-game-world-editor-foundation.md`
- `docs/myworld/in-progress-work-plans/terrain-expansion-plan.md`
- `docs/myworld/in-progress-work-plans/in-game-world-editor-plan.md`
- `docs/myworld/in-progress-work-plans/standalone-world-builder-plan.md`
- `docs/myworld/in-progress-work-plans/remaster-suite-roadmap.md`
- `docs/myworld/in-progress-work-plans/legacy-limits-audit.md`
- `docs/workspaces/README.md`

## Decision Log

| Date | Decision | Status |
| --- | --- | --- |
| 2026-07-17 | Begin a discussion-first architecture and capacity study; documentation only. | Confirmed |
| 2026-07-17 | Divide the remaining design into smaller discussion modules before choosing an architecture. | Confirmed |
| 2026-07-17 | Pursue true `(x,y,level)` separation and geographic alignment instead of extending packed-Y bands. | Direction confirmed; scope pending |
| 2026-07-17 | Use signed sequential levels: surface `0`, each level up `+1`, and each level down `-1`. | Confirmed |
| 2026-07-17 | Ordinary vertical entrance anchors should preserve exact X/Y; local walkable arrival offsets may account for object footprint and direction. | Confirmed |
| 2026-07-17 | Treat layered maps as a deliberate format divergence; legacy readers and runtimes need not accept post-layering projects. | Confirmed |
| 2026-07-17 | Preserve compatibility through explicit versions, capability requirements, and an optional one-way importer rather than permanent packed-Y runtime support. | Confirmed |
| 2026-07-17 | Record standalone capability packaging as relevant context, but defer the general installer and distribution architecture to a future focused study. | Confirmed |
| 2026-07-17 | Place the layered-world engine capability and layered map packages inside the Remaster Suite's Layered Maps module; keep World Builder separate and non-vanilla map changes in optional Content. | Confirmed |
| 2026-07-17 | Normalize and prove unchanged legacy-world behavior before geographic realignment or level `-2` content. | Confirmed |
| 2026-07-18 | Define deep underground as geographically anchored regional networks on physical level `-2`, disconnected by default but deliberately connectable later. | Confirmed |
| 2026-07-18 | Make a reusable one-way conversion workspace a required Layered Maps deliverable, with transition-graph inference, terrain-component alignment, Builder review, and private test-before-export. | Confirmed |
| 2026-07-18 | Require exact anchors for ordinary vertical edges while permitting reported, explicitly classified or acknowledged legacy misalignments. | Confirmed |
| 2026-07-18 | Distribute the initial developer-oriented conversion tooling as a folder extracted into a target repository root; extraction alone performs no conversion or target mutation. | Confirmed |
| 2026-07-18 | Allow automatic provisional conversion and relocation of every discovered area, including quest content, because all output remains isolated until explicit final export. | Confirmed |
| 2026-07-18 | Use a focused Builder-derived conversion workbench and dev launcher for inspection, correction, navigation, and private testing; reserve target mutation for a separate confirmed export script. | Confirmed |

## Next Discussion

Continue with long-distance travel, static separation versus true instancing,
allocation policy, and detailed validation. No implementation plan should be
prepared until those remaining decisions have been resolved.
