# World Layer Capacity Exploration Plan

Status: architecture design complete; Slices 1-59, 62, 64, 66, 68, 70, 72,
74, 78, 82, 85, 87, 91, 94, 97, 100, 103, 106, 107, 110, 113, and 117 owner-validated, Slice 60 private-runtime validated, Slice 76's
contained path owner-validated, and Slices 61, 63, 65, 67, 69, 71, 73, 75,
76, 77, 78, 79, 80, 81, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 118, 119, and 120 automated-validated on the active
refinement branch

Branch: `docs/layered-map-rebuild-refinement`

Started: 2026-07-17

Current milestone: owner-validated Slices 108-110 define, detach, privately
expose, and validate the next restoration prerequisite: known authored
spawns bind an exact destination slot, known authored removals bind an exact
existing entity, any mismatch or missing authored identity refuses, and timer/
transient state must be reconciled before the first visibility snapshot. The
bounded inventory and schema-v38 keep requirement completeness separate from
satisfied binding and executable restoration. They perform no lookup or
arrival gate;
replay, cancellation, reschedule, preservation, and all lifecycle authority
remain absent;
owner-validated Slices 111-113 define, detach, privately expose, and validate
generation matching and idempotent desired-state rules without inspecting a
target or performing a mutation;
automated-validated Slice 114 corrects the spawn prerequisite to accept an
empty slot or one exact-identity authored transient, preserving schema-v39 and
publishing the correction through inert schema-v40 diagnostics;
automated-validated Slice 115 defines the pure detached target-decision table
without adding runtime lookup, state capture, mutation, or arrival authority;
automated-validated Slice 116 adds bounded read-only exact-slot observation,
retaining scheduler/event correlation but no handles or mutation authority;
owner-validated Slice 117 exposes that detached observation through private
schema-v41 and proves one pending authored stump is classified as the exact
transient mutation prerequisite before natural completion clears the evidence,
without adding a commit token, mutation, or arrival authority;
automated-validated Slice 118 defines the dormant outer-event/inner-Region
revalidation order, requires scheduler identity, registration, generation, and
fresh in-boundary target agreement, and forbids carrying the scheduler-store
lock inward, while making no runtime revalidation or atomicity claim;
automated-validated Slice 119 moves exact constructor/identity comparison and
closed target classification inside the real Region object monitor, returns
only detached counts/state, and remains stale and non-atomic with mutation;
automated-validated Slice 120 exposes only the detached Region-boundary count,
completeness, and per-target fact through additive private schema-v42 while
retaining explicit non-atomic and non-authoritative flags;
Packed Region lookup, eager loading, release, eviction, pathing, packets, and
persistence remain unchanged

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

Not authorized beyond the approved foundation slices:

- editing terrain archives;
- relocating existing map content;
- changing ladders, portals, teleports, respawns, quests, or placements;
- modifying player databases or live world data;
- deploying experimental map work to the public server.

Documentation may be revised as decisions are made. The owner approved the
initial coordinate/preflight, normalization, dormant server contract, static
projection, source inventory, private observer, and checked Player runtime
slices on 2026-07-18. The owner then authorized continuing through focused
slices on the same active branch. Map relocation, Builder, database schema or
row migration, streaming, export, and live/public work remain out of scope.

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
| Player persistence | Database stores packed `x` and `y`; Slice 14 adds a checked legacy shadow | Still needs a preservation-safe versioned read/write strategy for true layered values |
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

Selected assessment: static isolated regions serve all current known content,
but the first layered architecture must be instance-ready. Keep
`WorldCoordinate(x,y,level)` as the geographic value and place it inside a
separate `WorldLocation(worldSpaceId, coordinate)` or equivalent runtime
identity. Current maps use one global static world space. A later
`world.instances` capability may create player/party spaces from declared map
templates without changing the coordinate format again.

Instance readiness does not authorize implementing the full dynamic lifecycle
inside the initial converter. Creation, membership, NPC/item/scenery isolation,
persistence, logout/reconnect, teardown, protocol behavior, and failure
recovery remain a focused server capability. Disjoint coordinate blocks must
not be presented as true instances.

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
9. Use the global static world space for converted content while reserving
   explicit world-space identity and instance-template metadata for a later
   true-instancing capability.

## Discussion Modules

The remaining decisions are deliberately divided so they can be handled one at
a time.

### Current Module: Private Migration and Validation

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
map rewrite. Modules A through H are resolved. The private
migration/validation sequence still requires discussion before a focused
implementation plan is authorized.

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

Long-distance travel is also resolved: it is a valid permanent design choice,
not a flaw that the layered model should suppress. Transition classifications
describe behavior and geometry; they do not impose a preferred aesthetic.
Ladders, stairs, portals, tunnels, or any other object may intentionally connect
distant points, same-level regions, or underground networks.

The converter therefore:

- preserves every recognized transition in its staging output unless a
  structural rewrite is necessary to represent it in the layered schema;
- uses direction, reciprocal edges, scripts, and component topology rather
  than distance alone when inferring a vertical relationship;
- never replaces a transport object's appearance or interaction merely to make
  its presentation more geographically conventional;
- reports large deltas and unusual topology as descriptive information, not as
  an error or recommendation to remove the feature; and
- validates that a transition has a representable destination, declared
  behavior, and recoverable runtime path without judging the creator's design.

Map authors remain free to preserve, reclassify, edit, remove, or add
long-distance connections in the workbench. The mapping system fixes early
coordinate limitations while remaining neutral about the worlds creators build
with it.

### Module E: Static Separation Versus True Instancing

Resolved: true player/party instancing is a desired future capability even
though no current known content requires it. The initial Layered Maps release
uses static terrain and one global world space, but its location, map-package,
transition, and Builder contracts must reserve a separate world-space identity
and instance-template metadata.

`WorldCoordinate(x,y,level)` remains the canonical geographic coordinate.
Instance identity is not another level and is not encoded into X or Y. A later
`world.instances` server capability owns creation, party/player association,
isolated entities and state, persistence, reconnect, cleanup, and protocol
semantics. This prevents the initial map conversion from absorbing an unused
runtime feature while ensuring creators can adopt it without another map-format
redesign.

### Module F: Allocation Policy

Resolved: use a sparse, sector-addressed world with machine-readable ownership
and growth reservations. The policy is designed for long-running MMORPG growth
without forcing creators into one world topology.

#### Coordinate and storage domain

- Logical X and Y are signed 32-bit tile coordinates. Existing non-negative
  coordinates remain unchanged during normalization, but the layered format is
  not bounded by the old `944`-tile height or a fixed positive-only canvas.
- Level remains an explicit signed integer. Runtime and package manifests
  declare practical supported ranges and reject arithmetic overflow or
  unreasonable resource requests rather than relying on a small wire field.
- Terrain storage retains the existing `48 x 48` sector unit for migration and
  tooling continuity. A sector key is conceptually
  `(worldSpaceId, level, sectorX, sectorY)` and uses floor division/modulo so
  negative X/Y coordinates behave predictably.
- Sector storage is sparse. A missing sector is absent/void, not a serialized
  blank sector. Each package declares finite extents even though the coordinate
  type provides a much larger growth domain.
- Runtime simulation regions and renderer chunks may use their own internal
  sizes. They must not redefine map ownership or coordinate semantics merely
  because terrain is stored in 48-tile sectors.

#### Stable identity and ownership

- Every area/component has a stable ID independent of its current coordinates.
  Movement changes its allocation record, not its identity or transition graph
  references.
- Exactly one package owns base terrain for a sector in a world space and
  level. Overlapping terrain ownership is rejected unless a separately defined
  patch/overlay contract explicitly permits it.
- Placement or content overlays may share a sector only through stable object
  identities, declared dependencies, and deterministic conflict rules.
- Different levels may and normally will own the same X/Y sectors. That overlap
  is geographic alignment, not an allocation collision.
- Dynamic instances do not reserve additional permanent coordinates. A map
  template owns its static sectors once; runtime world-space instances refer to
  the template through the future `world.instances` capability.

#### Growth reservations

- The allocation registry distinguishes occupied sectors from reserved-growth
  sectors. Reservations claim planning space without shipping artificial blank
  terrain.
- The converter/workbench proposes a one-sector same-level growth halo around
  isolated components by default. Creators may shrink, expand, reshape, share,
  or remove it, and may reserve larger directional corridors for settlements,
  quest chains, regional cave systems, or future continents.
- A reservation collision is a planning warning until accepted ownership is
  transferred or explicitly shared; occupied base-terrain collisions remain a
  blocking structural conflict.
- Categories such as shallow, deep, transport, quest, hub, expansion, and
  experimental are descriptive tags used for planning and filtering. They do
  not limit what may be built in an allocation.

#### Registry and package checks

The authoritative allocation registry records at least area ID, owner package,
world space, level, occupied sector set, exact tile bounds, growth reservation,
surface association, neighboring claims, transition IDs, template status, and
source/output fingerprints. World Builder renders these claims as an optional
overlay and checks proposed edits before sector ownership changes.

Package composition validates occupied-sector ownership, overlay contracts,
stable IDs, and reservations before import. The final export report lists new,
moved, released, shared, and conflicting claims so world growth remains
auditable across releases.

### Module G: Transition Graph and Recovery

Resolved: all transitions are explicit directed edges. A reverse edge is
optional and no-return, dangerous, quest-controlled, long-distance, and other
unconventional routes remain valid creator choices. Transition execution is
atomic: if its destination or requirements cannot be resolved, the player
stays at the source and the failure is reported rather than entering a partial
movement state.

Saved locations include world space, X/Y/level, and sufficient map/package
identity to validate or migrate them. Login and reconnect use this recovery
order:

1. Restore the exact saved world space and coordinate when valid.
2. Apply an explicit old-to-new migration redirect when the area moved.
3. Restore an instance-specific reconnect location when the instance exists.
4. Use the area or instance's declared recovery anchor.
5. Use the player's last known safe anchor in the global world space.
6. Use the configured world spawn only as the final fallback.

The original saved location, selected fallback, and reason are retained in
diagnostics or a migration receipt rather than silently discarded. Missing map
packages, invalid terrain, expired instances, failed transition destinations,
and fallback use appear in both technical logs and conversion/private-test
reports.

Every reachable area must have an administrative/system recovery path, but it
does not need a player-accessible exit. This prevents infrastructure failures
from permanently stranding a character without restricting intentional
gameplay topology.

### Module H: World Loading and Streaming

The new allocation study exposed four responsibilities currently coupled to
the number `48`:

- terrain archives store `48 x 48` sectors;
- the server's `RegionManager` also partitions tiles/entities into 48-tile
  regions;
- the client selects a `3 x 3` sector terrain window, producing a `144 x 144`
  local scene; and
- region reload, local-coordinate rebasing, visibility/static-scene baselines,
  collision products, and renderer resident products are coordinated around
  that window.

The maintained client already has predictive terrain preloading, decoded-sector
and CPU-window caches, model-input caches, resident renderer chunks, and a
`WorldStreamManager` telemetry/state foundation. Nevertheless, a window shift
still resets and rebuilds landscape models, dematerializes/rematerializes walls
and scenery, rebases entities, ground items, camera, and waypoints, and reapplies
a complete scene baseline. The active window is therefore larger than one
sector, but the presentation change can still look like a coarse scene swap.

#### Options

1. **Change every 48-tile unit to a different fixed size.** This moves the
   boundary but retains the coupling and makes legacy conversion harder.
2. **Increase the monolithic active window.** This hides boundaries farther
   away at greater memory/build cost but still causes large replacements.
3. **Keep 48-tile storage and introduce incremental streaming residency.**
   Decode archive sectors as pages, derive smaller independent presentation
   chunks, and add/remove only the chunks entering or leaving a budgeted
   resident set.
4. **Adopt a wholly new terrain-page format and streaming system together.**
   This can be revisited after the layered format is stable, but it combines
   two migrations without evidence that 48-tile storage pages are the visual
   problem.

Selected direction: Option 3. Keep `48 x 48` as the first layered terrain
storage page because it is migration-friendly, but stop treating storage page,
server simulation region, network interest area, local coordinate window,
collision build, and renderer draw chunk as one concept.

A target architecture would:

- derive presentation chunks smaller than a terrain sector, initially testing
  `24 x 24` because it divides 48 evenly and matches the existing static-object
  resident chunk size; retain `12 x 12` mesh subcells and smaller animated
  chunks where already useful;
- key resident products by world space, level, and global chunk coordinate;
- retain stable integer world locations and render camera-relative coordinates
  instead of rebasing authoritative entity state on routine walking;
- run an asynchronous lifecycle such as requested, decoded, CPU-built,
  GPU-ready, presentable, active, and retiring;
- preload from movement intent, velocity/waypoints, camera direction, teleports,
  and configured draw distance;
- keep old chunks visible until replacements are presentable, then activate
  changes atomically so terrain holes or whole-window flashes are not exposed;
- stream terrain, collision, scenery, walls, roofs, ground items, NPCs, and
  players through coordinated readiness/interest contracts so one category
  does not visibly precede its supporting world;
- stage a teleport or level change until a minimum destination ring is ready,
  while allowing a loading presentation rather than a partially materialized
  destination;
- make resident radius and cache budgets quality/performance settings rather
  than map-format properties; and
- retain a legacy-client/window adapter while the maintained custom client
  adopts incremental streaming.

Layered Maps owns sparse sector identity and terrain access. Server owns
simulation and network interest. Renderer/client owns presentation residency
and GPU lifetime. The contracts share world-space/level-aware keys, but one
module's preferred chunk size must not redefine another's storage or behavior.

The exact default presentation chunk and resident-ring budgets should be chosen
by benchmark and live visual testing rather than frozen by this discussion
alone.

Implementation sequencing is also selected. Coordinate codecs, explicit level
and world-space identity, persistence, and unchanged-world behavior reach their
parity gate through the current section-window adapter first. Incremental
streaming is the next separately tested layered-engine milestone. It must pass
walking, teleport, level-change, collision, static-scene, and entity-readiness
gates before geographic realignment or converted-world export depends on it.
This keeps the architecture coordinated without making coordinate and scene-
streaming regressions inseparable.

### Module I: Migration and Validation

Resolved: validation advances through isolated fixtures and explicit gates. No
phase mutates the public/live server or preservation-critical data.

#### Fixture ladder

1. **Synthetic coordinate laboratory**
   - Generated terrain and metadata exercise all four legacy bands, explicit
     positive/negative levels, signed X/Y, 48-tile sector edges, void regions,
     world spaces, transition classes, contradictory anchors, long-distance
     travel, and an instance-template declaration.
   - Codec boundary, overflow, unsupported legacy encoding, deterministic
     serialization, and source-preservation tests run without a game process.
2. **Copied vanilla baseline**
   - Normalize an exact fingerprinted supported vanilla world without moving
     content.
   - Require terrain, placements, transitions, collision, and gameplay to
     remain equivalent and keep the source copy unchanged.
3. **Copied Spoiled Milk world and player data**
   - Exercise custom terrain, overlays, definitions, quests, scripts,
     teleports, recovery paths, and copied player locations.
   - Cover login, logout, reconnect, death, respawn, walking, teleporting,
     scenery/NPC/item loading, combat, following, trading, and level/world-space
     isolation.
4. **Incremental-streaming fixture**
   - Keep the normalized world fixed while changing only loading/residency
     ownership.
   - Exercise continuous boundary crossings, reversals, camera look-ahead,
     fast movement, distant teleports, level changes, relogging, roofs,
     collision, and complete static/dynamic scene readiness.
5. **Automatic-alignment workspace**
   - Run transition inference, component movement, growth allocation, report
     generation, workbench review, corrective edits, and dev-launcher travel
     through every moved or flagged area.
6. **Disposable export rehearsal**
   - Export only to another copied target. Verify the preview, unchanged target
     fingerprint, changed-file inventory, backup, receipt, deterministic rerun,
     and byte-exact rollback.
7. **Additional compatible RSC-derived fixture**
   - Repeat discovery, normalization, report, private test, and disposable
     export with at least one non-Spoiled-Milk adapter or generated compatibility
     project so target assumptions remain explicit and reusable.
8. **Owner acceptance**
   - Combine automated evidence with visual traversal and gameplay checks.
     Real-target export may be proposed only after the relevant fixture gates
     pass and the owner accepts the staged result.

#### Gate evidence

Every gate retains:

- source adapter/capability versions and source fingerprints;
- deterministic manifests, reports, output hashes, and exact changed-file
  inventories;
- automated results for coordinate round trips, level/world-space isolation,
  terrain/collision/placement parity, transitions, persistence, streaming
  readiness, and rollback as applicable;
- technical diagnostics for warnings, fallbacks, missing owners, scene loads,
  recovery, and performance;
- visual evidence for terrain, roofs, walls, scenery, animations, transitions,
  streaming boundaries, teleports, and level changes where applicable;
- copied database identity and migration receipts without credential or
  password material; and
- an exact rollback target.

A failed gate stops advancement and returns to the last proven artifact. It
does not get waived merely because a later stage looks correct.

## Open Owner Questions

None for the architecture study. Focused foundation implementation is
continuing slice by slice; world mutation and live/public work remain gated.

## Implementation Progress Checkpoint: 2026-07-20

The slice number is not a denominator: the plan does not define a fixed final
slice count, and many slices deliberately pair one inert value with one private
diagnostic. Progress is therefore measured against capability gates:

- the architecture and product decisions in this plan are complete;
- the read-only/dormant foundation is mature: coordinate codecs, normalized
  inventories, level-aware value types, transition and placement projections,
  tile/collision parity evidence, interest/residency/retirement models,
  authored reconstruction analysis, and active-NPC boundary refinement all
  have automated or private-owner evidence through Slice 78;
- the current pre-authority proof stream is approximately `85-90%` complete;
  remaining proof must close fresh candidate-set reassessment and the other
  runtime preservation categories before any lifecycle consumer is safe;
- Phase 5's complete layered-world engine is approximately `35-40%` complete.
  Packed runtime state remains authoritative, so region storage, complete
  entity/collision/pathing adoption, persistence, schemas, protocol/client
  adoption, unchanged-world parity under layered authority, signed extra
  levels, and incremental streaming remain substantial gates; and
- the complete Layered Maps product spanning roadmap Phases 5-7 is
  approximately `20-25%` complete. Layered World Builder conversion/review,
  the dev launcher, deterministic export/import/backup/rollback, copied-world
  migration, geographic alignment, and a privately validated level `-2` world
  have not yet been implemented.

These ranges are planning estimates, not release percentages. The work to date
has retired much of the design and observability risk, but the remaining
authority-changing and tooling work is larger per milestone than most earlier
slices.

## Foundation Implementation Slices: Implemented and Validated

### Slice 1: Layered coordinate contract and read-only preflight

Objective: establish the extractable Layered Maps tool boundary, prove the
coordinate model and legacy codec exhaustively, and fingerprint a target
repository without changing its runtime or world.

Delivered:

- a self-contained `tools/layered-maps/` developer folder suitable for eventual
  extraction into a repository root;
- a versioned, language-neutral `signed-layered-v1` coordinate/manifest
  specification;
- dependency-free Java 8 immutable reference values for
  `WorldCoordinate(x,y,level)`, `WorldSpaceId`, and
  `WorldLocation(worldSpaceId, coordinate)` so the future Builder/dev launcher
  can consume the same reference contract;
- a named `legacy-packed-y-v1` codec mapping legacy bands to levels `0`, `+1`,
  `+2`, and `-1`, with checked reverse encoding and explicit refusal for `-2`,
  expanded extents, or other layered-only values;
- a read-only `preflight` command that identifies an exact supported repository
  adapter, inventories/fingerprints candidate terrain and coordinate-bearing
  sources, and emits deterministic human-readable and JSON reports inside the
  tool's isolated workspace; and
- focused fixtures/guards for codec behavior, deterministic output, unknown-
  target refusal, and proof that preflight does not modify target inputs.

Explicitly out of scope for Slice 1:

- replacing or adapting server `Point`;
- changing client coordinates, packets, persistence, regions, collision,
  visibility, caches, or streaming;
- decoding or rewriting complete placements/transitions;
- emitting a converted map;
- changing terrain archives, scripts, quests, ladders, player databases, or
  configuration;
- World Builder UI/dev-server integration; and
- any final export or live/public operation.

Acceptance evidence:

1. Exhaustive packed-Y decode/encode coverage for Y `0..3775` and representative
   X boundaries, plus checked failures outside the legacy domain.
2. Explicit level mapping and exact round trips for all legacy plane bands.
3. Layered-only values remain valid in the reference model but cannot be
   silently encoded as legacy data.
4. Two identical preflight runs produce identical normalized reports/hashes.
5. Unknown or changed layouts fail with actionable diagnostics.
6. Input hashes and Git status remain unchanged after preflight.
7. Focused tests and the relevant existing repository guards pass.

This slice creates no compatibility claim for runtime layered maps. Its output
is the coordinate laboratory and discovery evidence required before a later
slice can introduce unused server/client adapters behind parity tests.

### Slice 1 implementation record

The implementation is self-contained under `tools/layered-maps/`. It contains
the language-neutral contract and schemas, dependency-free Java 8 reference
types, the exact checked legacy codec, a command-line preflight, an ignored
build/workspace boundary, and operator documentation. The preflight recognizes
only `spoiled-milk-repository-v1`; it refuses missing markers, incompatible
configuration, malformed terrain, and server/client archive differences before
creating reports.

On the current repository, read-only preflight recorded:

- byte-identical server/client `Custom_Landscape.orsc` archives;
- 1,771 validated `48 x 48` sectors across legacy planes `0..3`, distributed
  as 445, 444, 441, and 441 sectors respectively;
- 253 conservatively identified configuration, placement, transition, server,
  and client candidate coordinate owners; the Builder source scan produced no
  current signal matches; and
- deterministic JSON and Markdown reports under the tool's ignored workspace.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-one.py` — 5 tests passed;
- the coordinate fixture exhaustively decoded and re-encoded packed Y
  `0..3775` at representative X boundaries, checked every legacy-plane
  mapping, proved signed-sector floor semantics and world-space isolation, and
  refused legacy-incompatible coordinates;
- two fixture preflights produced byte-identical JSON and Markdown while a
  full before/after filesystem snapshot proved no target mutation;
- inconsistent and unknown fixtures returned actionable refusals without
  creating a report workspace;
- the current-repository guard preserved its starting Git status and protected
  input hashes while validating the real adapter; and
- `python3 tests/myworld/test-world-builder-discovery.py` — 13 tests passed,
  confirming the existing Builder discovery contract remains intact.

The local environment does not provide an `ant` executable, so the optional
Ant JAR target was not exercised here. The same sources compiled with the Java
8 source/target flags in the focused guard and through the operator launcher,
and the launcher completed a real-repository preflight successfully.

### Slice 2: Canonical world inventory and lossless normalization

Objective: interpret every recognized structured coordinate source through the
signed layered model without changing topology, source data, runtime behavior,
or game files.

Delivered under `tools/layered-maps/`:

- the language-neutral `layered-world-inventory-v1` manifest and compact
  `normalization-summary-v1` report contracts;
- a dependency-free Java 8 JSON parser/canonical writer and security-hardened
  XML reader;
- terrain-sector normalization from legacy planes to signed levels with
  per-entry payload hashes and checked legacy-name reconstruction;
- lossless normalization of all six recognized placement roots while
  preserving arbitrary non-coordinate attributes;
- a directed transition graph for `ObjectTelePoints.xml`, retaining commands,
  raw endpoints, layered endpoints, level/X/Y deltas, and exact-anchor status;
- record-by-record semantic reconstruction through checked reverse encoding;
- explicit raw preservation and findings for coordinates outside the named
  codec; and
- fingerprinted unresolved Java coordinate owners, without parsing or
  rewriting source code.

The complete manifest is intentionally large because it retains every terrain
sector and placement record. A compact deterministic JSON report and Markdown
summary contain the fingerprints, aggregate/source counts, all 20 transition
edges, all unresolved Java owners, and all findings for practical AI/operator
review.

Current-repository results:

- 1,771 terrain sectors normalized across levels `0`, `+1`, `+2`, and `-1`;
- 40 placement sources and 49,816 records reconstructed semantically;
- 20 directed transition edges normalized and reverse-encoded;
- 60,680 total structured coordinate occurrences when transition endpoints are
  included: 60,679 normalized and one retained raw;
- 211 Java source owners fingerprinted for later parsing; and
- one existing warning in `NpcLocs.json` record 3,376: NPC 67 has start/minimum
  Y values `3534/3519`, but maximum Y `6549`, outside the four legacy bands.
  Slice 2 preserved that maximum unchanged and did not infer a correction.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-two.py` — 5 tests passed;
- two fixture runs produced byte-identical complete JSON, compact JSON, and
  Markdown reports while a full source-tree snapshot remained unchanged;
- all placement roots, all legacy levels, directed transitions, arbitrary
  attributes, partial normalization, and semantic reverse reconstruction were
  exercised;
- unknown placement roots and unsafe XML were refused before report output;
- both emitted JSON documents validated against their Draft 2020-12 schemas;
  and
- the real-repository guard preserved Git status and hashes for configuration,
  both terrain archives, NPC placements, and transitions.

Slice 2 is normalization only. It did not align terrain, infer area moves,
rewrite Java, create a Builder project, modify runtime code, touch a database,
or create an import/export path.

### Slice 3: Dormant server layered-location adapter

Objective: establish a server-owned Java 8 compatibility seam for
`signed-layered-v1` without changing any existing world, entity, region,
collision, pathing, visibility, transition, packet, or persistence consumer.

Delivered under
`server/src/com/openrsc/server/model/world/coordinate/`:

- immutable `WorldCoordinate(x,y,level)`, `WorldSpaceId`, and
  `WorldLocation(worldSpaceId,coordinate)` values matching the tool contract;
- signed 48-tile sector/local addressing with floor division and modulo;
- checked translation overflow and stable equality/hash behavior; and
- `LegacyPackedPointAdapter`, an explicit bridge between existing packed
  `Point` and global layered locations.

The adapter accepts only the exact `legacy-packed-y-v1` domain. Reverse
conversion refuses level `-2`, other unrepresentable levels, expanded or
negative coordinates, and every non-global world space. Existing `Point`
constructors, storage, behavior, and call sites were not modified.

Dormancy and validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-three.py` — 4 tests passed;
- all packed Y values `0..3775` at representative X boundaries were compared
  against the tool codec and round-tripped through a `Point`-compatible seam;
- signed negative coordinates, world-space identity, overflow, nulls, and all
  legacy refusal boundaries were exercised;
- a repository-wide guard confirmed that more than 500 existing server/plugin
  sources do not import or reference the new coordinate package;
- `./scripts/build-server.sh` passed through the authoritative bundled Ant
  build for 717 core and 488 plugin sources, and `core.jar` contained only the
  five approved package artifacts; and
- Layered Maps preflight classifies the adapter as a resolved
  `server-layered-coordinate-contract`, so the existing 211 unresolved Java
  owners remain unchanged rather than counting the adapter as unfinished
  migration work.

Slice 3 introduces no behavior change. It does not attach layered identity to
an entity, area, region, map, transition, packet, session, or database row and
does not enable a new level or world space.

### Slice 4: Checked layered area projection

Objective: make the existing server `Area` the first narrowly scoped consumer
of the layered location contract while preserving every packed constructor,
field, mutation, getter, and `inBounds` result.

Delivered:

- immutable `WorldArea` boundaries qualified by one `WorldSpaceId` and one
  signed level;
- strict world-space and level isolation plus signed/deep/instance-compatible
  area values;
- open-boundary containment matching the historical `Area.inBounds`
  comparisons;
- `Area.toWorldArea()`, which creates a checked immutable snapshot from the
  area's current packed bounds; and
- an `Area.inBounds(WorldLocation)` overload for layered callers, without
  rerouting any existing packed caller or storing a parallel mutable value.

The projection refuses negative or expanded legacy coordinates and packed
areas whose two boundaries decode onto different levels. Existing `Area`
mutations remain authoritative: a later snapshot reflects them, while an
already returned `WorldArea` remains immutable. All six current `Area`
definitions fit within one legacy plane.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-four.py` — 3 tests passed;
- packed and layered containment agreed for every packed Y `0..3775` at open,
  interior, and maximum X boundaries across all four legacy levels;
- mutation parity, immutable snapshot behavior, world-space/level isolation,
  deep instance areas, invalid bounds, nulls, and cross-plane refusal were
  exercised;
- a repository guard confirmed `Area.java` is the only source outside the
  coordinate package that imports the new contract;
- Slice 1, Slice 2, Slice 3, World Builder discovery, and server build-authority
  regressions all passed;
- the authoritative server build passed for 718 core and 488 plugin sources,
  with `Area` and all six coordinate-package classes present in `core.jar`;
  and
- read-only preflight still finds 254 candidates and normalization still
  retains 211 unresolved Java owners, 1,771 terrain sectors, 49,816 placements,
  20 transitions, and the one pre-existing raw NPC maximum. World-data inputs
  and counts are unchanged; the complete inventory intentionally fingerprints
  unresolved Java owners, so its overall hash may track staged source changes.

Slice 4 does not make region storage, entities, transitions, map identity,
collision, pathing, visibility, packets, persistence, or clients level-aware.
Packed `Area` storage and all existing call paths remain authoritative.

### Slice 5: Logical layered region-key projection

Objective: define an explicit world-space/level-aware region identity and let
`RegionManager` calculate it without replacing or consulting the current
packed region maps.

Delivered:

- immutable `WorldRegionKey(worldSpace, level, regionX, regionY)`;
- signed region indices derived from `WorldCoordinate`'s 48-tile floor-divided
  sector addressing;
- checked construction from legacy `Point` through
  `LegacyPackedPointAdapter`; and
- read-only `RegionManager.getLayeredRegionKey` overloads for packed and
  layered locations.

The audit found that an existing packed `Region` cannot always receive one
truthful layered key. The legacy 944-tile level stride is not divisible by the
48-tile region size. Packed region Y 19 contains both packed Y 943 at level 0
and packed Y 944 at level +1. Packed region Y 39 similarly contains packed Y
1887 at level +1 and Y 1888 at level +2. The Y 2831/2832 boundary already falls
between packed regions 58 and 59. Slice 5 therefore projects keys from
locations only; it does not attach a key to `Region` or alter nested map
identity. A later authoritative storage migration must split the two
straddling regions.

Later Slice 19 analysis expands this finding: even when a packed region does
not cross a level boundary, the 944/48 misalignment means many plane-1 and
plane-2 packed rows overlap two logical region rows. The two straddles remain
the most severe cases, but they are not the complete storage-split inventory.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-five.py` — 3 tests passed;
- every packed Y `0..3775` at six representative X values produced the same
  logical key whether entered through legacy `Point` or `WorldLocation`;
- both straddling boundaries, the already aligned third boundary, signed
  negative indices, deep levels, world spaces, equality, hashes, and null
  refusals were exercised;
- source guards prove that `Area` and `RegionManager` remain the only staged
  consumers and that `RegionManager` still stores
  `ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>` and retains
  its existing packed division/lookup path;
- Slice 1 through Slice 4, World Builder discovery, and server build-authority
  regressions all passed;
- the authoritative server build passed for 719 core and 488 plugin sources;
  and
- preflight/normalization still report 254 candidates, 211 unresolved Java
  owners, 1,771 terrain sectors, 49,816 placements, 20 transitions, and the one
  preserved raw coordinate. No world-data source or count changed.

Slice 5 does not change region storage, region construction, tiles, collision,
visibility caches, entities, map loading, transitions, packets, persistence,
or clients. It records the exact split requirement that a later storage slice
must meet.

### Slice 6: Directed object-telepoint projection

Objective: make existing object telepoint destinations consume the layered
location contract without modifying their XML, packed map, command selection,
or runtime teleport behavior.

Delivered:

- immutable directed
  `WorldObjectTransition(source, destination, command)`;
- checked projection of both legacy endpoints through
  `LegacyPackedPointAdapter`;
- exact preservation of the stored command text; and
- `EntityHandler.getObjectWorldTransition`, which first delegates to the
  authoritative existing `getObjectTelePoint` lookup/matcher and then returns
  a layered view of the matched edge.

The type is intentionally object-specific. Calling it the universal world
transition would prematurely require boats, spells, recovery, quest routes,
and future instance boundaries to carry an object command. The broader typed
transition schema remains a later design/adoption slice.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-six.py` — 4 tests passed;
- all packed Y values `0..3775` were exercised as directed source/destination
  pairs with exact command preservation;
- deep and cross-world-space destinations, direction-sensitive equality,
  command identity, hashes, descriptions, and null refusals were exercised;
- the current `ObjectTelePoints.xml` SHA-256 remains
  `957b32b927860170905460b9d2a5f6377256ce493503b62b738175fdda68f4ed`;
  all 20 directed entries are unique and fit the checked legacy domain;
- source guards prove the XML still loads into `HashMap<Point, TelePoint>`, the
  new projection delegates to existing command matching, and no runtime caller
  invokes the layered API;
- `Area`, `RegionManager`, and `EntityHandler` are the only staged consumers;
- Slice 1 through Slice 5, World Builder discovery, and server build-authority
  regressions all passed;
- the authoritative server build passed for 720 core and 488 plugin sources;
  and
- preflight/normalization remain at 254 candidates, 211 unresolved Java owners,
  1,771 terrain sectors, 49,816 placements, 20 transitions, and one preserved
  raw coordinate. Their Slice 5 fingerprints remain unchanged because the
  normalized source inputs and world data did not change.

Slice 6 changes no runtime teleport call, XML record, placement, terrain,
region, entity, packet, persistence, client, or map. It does not define the
universal transition taxonomy or enable layered-only destinations.

### Slice 7: Logical map-sector identity and legacy archive-name codec

Objective: distinguish canonical signed map-sector identity from the offset
indices embedded in legacy terrain archive entry names, without rewriting or
changing how either terrain archive is loaded.

Delivered in both the extractable tool and server binding:

- immutable `WorldMapSectorId(worldSpace, level, sectorX, sectorY)`;
- named `legacy-terrain-sector-name-v1` codecs for
  `h{plane}x{archiveSectorX}y{archiveSectorY}`;
- checked plane/level mapping, global-space restriction, signed logical
  sectors, overflow handling, and reverse encoding; and
- explicit archive-sector offsets of `+48` on X and `+37` on Y, derived from
  the historic tile offsets `2304/48` and `1776/48`.

`WorldMapSectorId` remains a separate semantic type from `WorldRegionKey`.
They both currently use 48-tile floor-divided indices, but terrain package
ownership and simulation-region storage/lifetime must be free to evolve
independently.

Normalization now records `legacySectorX/Y` alongside logical signed
`sectorX/Y`. This corrects the previous ambiguity where fields named
`sectorX/Y` still contained offset archive indices. The schema now permits
signed logical indices. For example, `h0x48y37` maps to global level 0 sector
`(0,0)`, and archive entries at X 47 or Y 36 correctly map to logical `-1`.

The existing `WorldEditorTerrainArchive.Coordinates` can expose a checked
logical sector snapshot. Its archive-entry construction, cache, reads, tile
records, and callers remain unchanged and no code invokes the new method yet.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-seven.py` — 3 tests passed;
- tool/server codecs agreed and round-tripped across 31,428 synthetic legacy
  names plus all 1,771 current archive entries;
- signed/deep/instance logical sectors, invalid names, unsupported levels and
  spaces, negative archive results, overflow, equality, hashes, and nulls were
  exercised;
- the real normalized inventory reconstructs every legacy name and reports
  logical ranges: level 0 X `0..21`, Y `-1..20`; level +1 X `-1..20`, Y
  `-1..20`; and levels +2/-1 X/Y `0..20`;
- both authoritative terrain archives remain byte-identical at SHA-256
  `d50089fcc81d51aa461567f4416a8f1a329ed439bcf64606ca1441c600e7229b`;
- Slice 1 through Slice 6, World Builder discovery, schema validation, and
  server build-authority regressions all passed;
- the authoritative server build passed for 722 core and 488 plugin sources;
  and
- preflight/normalization remain at 254 candidates, 211 unresolved Java owners,
  1,771 terrain sectors, 49,816 placements, 20 transitions, and one preserved
  raw coordinate. Fingerprints changed as expected because the editor source
  is fingerprinted and logical inventory sector semantics were corrected.

Slice 7 does not change terrain bytes, archive names, archive copies, loader
lookup, runtime regions, tiles, collision, entities, client behavior, Builder,
or export. It creates no layered terrain file and enables no new map extent.

### Slice 8: Checked static-placement projections

Objective: let the three server placement models expose layered locations and
correctly inclusive NPC roaming bounds while preserving their JSON, public
mutable packed fields, loaders, and entity construction.

Delivered:

- `GameObjectLoc.toWorldLocation()` from its current packed `Point`;
- `ItemLoc.toWorldLocation()` from its current packed integer X/Y;
- `NPCLoc.toWorldStartLocation()` from its current spawn X/Y;
- `NPCLoc.toWorldRoamingBounds()` from its current minimum/maximum X/Y; and
- immutable `WorldTileBounds`, qualified by world space and signed level with
  inclusive endpoints.

`WorldTileBounds` is deliberately separate from Slice 4's `WorldArea`. The
legacy `Area.inBounds` contract uses strict/open comparisons, while NPC roaming
and path checks include the minimum and maximum tiles. Reusing `WorldArea`
would have changed edge-tile behavior.

Each projection is calculated on demand. A later public-field mutation appears
in the next snapshot, while a previously returned layered value stays
immutable. No existing call site invokes the new methods. Partial validity is
also preserved: NPC 67's start at `(662,3534)` maps to level `-1`, but its
existing maximum Y `6549` remains outside the named legacy codec and causes the
roaming-bounds projection to refuse rather than infer a correction.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-eight.py` — 3 tests passed;
- all packed Y values `0..3775` round-tripped through object, item, NPC-start,
  and inclusive NPC-bounds projections;
- mutable-field snapshots, both inclusive boundary tiles, signed deep instance
  bounds, level/world-space isolation, inverted/cross-level bounds, invalid
  packed values, hashes, equality, and nulls were exercised;
- the real normalization remains at 40 placement sources and 49,816 records,
  with 60,679 normalized coordinates and only the existing NPC 67 maximum
  retained raw at record 3,376;
- source guards prove all public packed fields remain present, the projections
  are unused, and JSON/loaders/runtime construction remain authoritative;
- Slice 1 through Slice 7, World Builder discovery, schema validation, and
  server build-authority regressions all passed;
- the authoritative server build passed for 723 core and 488 plugin sources;
  and
- preflight/normalization retain 254 candidates, 211 unresolved Java owners,
  1,771 terrain sectors, 49,816 placements, 20 transitions, and one raw
  coordinate. Fingerprints changed as expected because the three placement
  model sources are fingerprinted inputs.

Slice 8 does not change a placement record, loader, spawned entity, roaming
decision, terrain, runtime region, packet, persistence row, client, Builder, or
export. It does not enable layered-only placement input.

### Slice 9: Read-only Java coordinate-owner classification

Objective: turn the 211 fingerprinted Java candidates into a deterministic
migration queue without parsing coordinate expressions, removing evidence, or
rewriting a source file.

Delivered in the extractable Layered Maps tool:

- a separate `coordinate-owner-classification-v1` JSON contract and Markdown
  companion emitted by `normalize` alongside the unchanged v1 world inventory;
- a `classified-unparsed` record for every unresolved Java owner, retaining its
  role, path, size, SHA-256, and original discovery signals;
- explicit `migration-owner`, `ambiguous-literal`, and `signal-collision`
  dispositions;
- primary migration family, risk, confidence, and stable reason codes for each
  source; and
- deterministic source/classification fingerprints and aggregate counts by
  disposition, family, risk, and source role.

The classifier deliberately distinguishes coordinate evidence from weaknesses
in the Slice 1 broad scan. That scan treats any `944` substring as a
`packed-floor-stride` signal, so values such as item IDs, colors, XP table
entries, and cryptographic constants can become candidates. Slice 9 does not
silently delete them. It retains all 211 owners and labels three standalone
`944` uses as ambiguous plus six substring-only matches as signal collisions.
Actual floor arithmetic such as `/ 944`, `* 944`, and
`Math.floorDiv(..., 944)` remains migration evidence.

Current classification:

| Disposition | Owners |
| --- | ---: |
| Migration owner | 202 |
| Ambiguous standalone literal | 3 |
| Definite substring signal collision | 6 |

| Primary migration family | Owners | Risk |
| --- | ---: | --- |
| Content topology | 135 | Medium |
| Protocol/session boundary | 29 | Critical |
| Simulation/spatial runtime | 22 | Critical |
| Terrain/region storage | 7 | Critical |
| Persistence/world bootstrap | 5 | Critical |
| Client world presentation | 4 | High |
| Ambiguous literal review | 3 | Review |
| Incidental signal review | 6 | Review |

No current source is classified under `builder-authoring`, because the present
World Builder source tree produced no unresolved Java coordinate candidate.
The contract and focused fixture still cover that family so an extracted
module or future Builder source is classified deterministically when present.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-nine.py` — 2 tests passed;
- focused fixtures exercised every migration family, all three dispositions,
  arithmetic versus substring-only `944` evidence, fallback/manual review,
  risk/confidence assignment, and null refusal;
- two real-repository runs emitted byte-identical JSON and Markdown reports,
  retained all 211 owners, validated count totals and the Draft 2020-12 schema,
  verified the classification fingerprint, and left Git status unchanged;
- Slice 1 through Slice 8 regressions all passed (30 tests total before the two
  Slice 9 tests), as did World Builder discovery, standalone-layout, and server
  build-authority guards;
- the authoritative server build remains successful for 723 core and 488
  plugin sources; and
- normalization retains source fingerprint
  `570e784428a139c6f5b5c5c516d0307fbf96a8087fc028bf231cb60bbfe820cf`
  and inventory fingerprint
  `d55148506c6172e4c54648ca09d2d85db6660e6760a28e39cde3376bc2d246ea`.
  The independent classification fingerprint is
  `315b8676a2f123ab76769a92f0a2ee77c16704a1b6bc51db478860ca54a79f8b`.

Slice 9 is lexical triage only. It does not assert that a source's coordinate
arguments are literals, infer topology, resolve expressions, alter preflight
candidate status, rewrite Java, change runtime behavior, touch persistence or
player data, modify terrain/placements, launch Builder, or enable import/export.

### Slice 10: Read-only content coordinate-occurrence inventory

Objective: replace source-level content classification with exact lexical
file/line evidence before any script migration or runtime dual representation
is attempted.

Delivered in the extractable Layered Maps tool:

- a separate `java-coordinate-occurrence-inventory-v1` JSON contract and
  Markdown companion emitted by `normalize`;
- balanced-parenthesis scanning for `teleport(...)`, `Point.location(...)`,
  `new Point(...)`, `new Area(...)`, and `.inBounds(...)` shapes;
- Java comment, string-literal, and character-literal masking so textual
  examples do not become occurrences;
- source path/SHA-256, one-based line/column, occurrence kind/form, normalized
  argument expressions, argument count, and lexical argument shape; and
- deterministic linkage to the repository source fingerprint and Slice 9
  owner-classification fingerprint, plus its own occurrence fingerprint.

Current content inventory:

| Occurrence kind | Count |
| --- | ---: |
| Teleport shape | 903 |
| Point construction | 341 |
| Area check | 37 |
| Area construction | 5 |
| **Total** | **1,286** |

All 135 `content-topology` sources contain at least one inventoried occurrence.
Of the 1,286 occurrences, 783 contain only integer-literal arguments, 499
contain one or more expressions, and four have no arguments. The no-argument
set demonstrates an intentional limitation: lexical `teleport(...)` matching
can include a method declaration. Slice 10 records that evidence but does not
claim symbol resolution or infer a directed transition. Expression-bearing
arguments likewise remain intact rather than being evaluated or guessed.

Validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-ten.py` — 2 tests passed;
- the scanner fixture exercised nested calls and commas, array expressions,
  negative literals, multiple shapes on one line, declarations, ignored
  comments/strings, unbalanced-source refusal, and null refusal;
- two real-repository runs produced byte-identical JSON and Markdown, retained
  all 135 sources and 1,286 occurrences, validated the Draft 2020-12 schema and
  fingerprint, and left Git status unchanged; and
- Slice 1 through Slice 10 regressions all pass (34 tests).

Slice 10 does not resolve Java symbols, determine teleport parameter meaning,
evaluate expressions, create transition edges, rewrite a script, alter runtime
coordinates, touch world/player data, or launch a game process. It is the last
planned report-only bridge before an opt-in private runtime parity observer.

### Slice 11: Private runtime layered-coordinate parity observer

Objective: provide the first owner-testable in-game layered milestone without
making layered state authoritative or changing the legacy world's behavior.

Delivered:

- `LayeredCoordinateParitySnapshot`, an immutable projection of an
  authoritative packed `Point` into world space, signed X/Y/level, logical
  region, logical terrain sector, local sector coordinates, and checked legacy
  round trip;
- `LayeredCoordinateParityObserver`, an opt-in JSONL trace isolated by database
  ID plus username hash, with monotonic per-trace sequence numbers and
  schema-versioned `start`, `move`, `teleport`, `marker`, `snapshot`, `logout`,
  `login`, and `stop` events;
- transition `from`/`to` snapshots and signed X/Y/level deltas, making ordinary
  movement, vertical changes, long-distance travel, death/respawn, and
  reconnect visible to AI analysis;
- dev/admin-only `::layerparity` commands for `start`, `status`, `snapshot`,
  `mark LABEL`, and `stop`;
- a second server capability gate controlled by system property, environment,
  or configuration, defaulting to false in both `myworld.conf` and
  `myworld-host.conf`; and
- the Draft 2020-12 `layered-map-parity-event-v1` schema plus exact private
  launch and command instructions in `tools/layered-maps/README.md`.

Privacy and safety boundaries:

- the observer is dormant unless
  `OPENRSC_LAYERED_MAP_PARITY_OBSERVER=true` (or its equivalent property/config
  key) is set and a dev/admin explicitly starts a trace;
- ordinary players cannot invoke the Development command handler;
- logs contain numeric database ID and username hash for reconnect-safe
  isolation, but no username text, IP address, password, credential, inventory,
  stats, or chat;
- distinct username hashes sharing a database ID receive distinct trace state
  and files;
- invalid/unrepresentable packed coordinates surface a capture error rather
  than being coerced; and
- both local and hosted tracked configurations remain disabled by default.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-eleven.py` — 2 tests passed;
- all eight legacy band boundaries (`0`, `943`, `944`, `1887`, `1888`, `2831`,
  `2832`, and `3775`) projected and round-tripped exactly;
- fixtures covered walking, level-boundary movement, multi-level teleport,
  duplicate-location suppression, marker validation, snapshot, logout/login,
  stop behavior, identity isolation, invalid-point visibility, sequential
  JSONL, privacy exclusions, and schema validation;
- source guards prove the capability/env/config gate, dev-only command path,
  Player movement/session observation, both disabled config defaults, and no
  layered value being written back into `Player`;
- the authoritative server build succeeds for 725 core and 488 plugin sources;
  and
- normalization content counts remain unchanged at 211 Java owners, 135
  content-topology sources, and 1,286 occurrences. Expected source hashes
  changed because `Player` and `Development` are fingerprinted inputs.

Owner runtime acceptance was completed on 2026-07-18. The intended private
test was:

1. Launch the private server with the observer environment gate enabled and
   log in using a dev/admin account.
2. Run `::layerparity start`, walk normally, cross a 48-tile boundary if
   convenient, and use `::layerparity mark walking-done`.
3. Exercise ladders/stairs between surface, upper floors, and underground;
   take at least one long-distance teleport; then mark `travel-done`.
4. Exercise death/respawn if practical. Leave capture ACTIVE through logout
   and reconnect so both session events use the same trace.
5. Run `::layerparity snapshot`, then `::layerparity stop`.
6. Review `server/logs/layered-map-parity/*.jsonl` for exact round trips,
   expected level deltas, and any behavioral or visual regression.

Owner runtime validation evidence:

- the log contained two independently bounded sessions for the same isolated
  private identity; the first 434-record session was retained only as
  corroboration because its marker itinerary was initially misunderstood;
- the corrected accepted session contained 207 records: 187 ordinary moves,
  nine teleports, six markers, one logout/login pair, one snapshot, and exact
  start/stop boundaries;
- all 207 records reported exact packed-to-layered-to-packed round trips and
  the event chain contained no coordinate discontinuity;
- the run exercised surface level `0`, underground level `-1`, and upper-floor
  level `+1` without changing logical X/Y during exact vertical transitions;
- surface `(216,468,0)` projected to underground `(216,468,-1)`, and surface
  `(226,440,0)` projected to upper floor `(226,440,+1)`; their return paths
  likewise retained their observed logical X/Y;
- same-level long-distance travel and logout/reconnect retained exact location
  identity; and
- level `-2` and death/respawn were not exercised. Level `-2` has no current
  legacy content to visit, and death/respawn remains a later focused recovery
  regression rather than a blocker for this observational parity gate.

The runtime JSONL is preserved locally and ignored by Git. It contains stable
identity metadata intended for diagnostics and is not a source artifact or a
candidate for checkpoint commits.

Slice 11 observes the existing packed location after existing movement/session
logic. It does not store a parallel location on `Player`, feed a layered value
into region lookup, alter collision/pathing/visibility, change teleport logic,
modify packets or persistence, enable level `-2`, load a converted map, or
touch the public server.

### Slice 12: Checked Player layered-location mirror

Objective: make `Player` the first deliberately narrow dual-representation
runtime owner while retaining its inherited packed `Point` as the sole
authoritative gameplay location.

Delivered:

- `LayeredLocationMirror`, an atomic checked mirror that can only synchronize
  from an authoritative packed `Point` and exposes no layered-to-packed entity
  mutation path;
- exact packed/layered round-trip validation before a mirror state is accepted,
  with stale, uninitialized, null, and unrepresentable inputs refused rather
  than silently coerced;
- `Player.setInitialLocation` initialization and synchronization immediately
  before every existing `Player.setLocation` mutation;
- a read-only `Player.getLayeredLocation()` accessor that verifies the mirror
  still describes the current inherited packed location;
- invariant checks after every completed movement and during login/logout
  session transitions; and
- a dev/admin `::layerparity` precondition that refuses capture if the Player
  mirror has diverged.

Safety boundary:

- all movement, collision, pathing, region lookup/storage, visibility, packets,
  persistence, scripts, terrain, and client behavior continue to read the
  inherited packed `Point`;
- no caller can supply a `WorldLocation` to the mirror, and Player contains no
  `WorldLocation`-to-`Point` conversion or write-back;
- the runtime observer remains separately opt-in and disabled in both tracked
  server configurations; and
- JSONL traces are preserved locally but ignored by Git so runtime evidence
  does not dirty or enter a checkpoint.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twelve.py` — 2 tests passed;
- the mirror fixture covered all eight legacy band boundaries, exact
  `(216,468,0)` to `(216,468,-1)` geographic alignment, equal-value Point
  instances, stale-state refusal, invalid-update rollback, and null refusal;
- Slice 1 through Slice 12 regressions all pass (38 tests), including updated
  explicit package/consumer allowlists naming Player as the new staged owner;
- World Builder discovery passed 13 tests, the standalone-layout guard passed,
  and the bundled-Ant server build authority remained internally consistent;
- the authoritative server build succeeds for 726 core and 488 plugin sources;
  and
- two real-repository normalizations were byte-stable at 1,771 terrain
  sectors, 49,816 placement records, 20 transition edges, one unresolved
  coordinate input, 211 classified Java owners, and 1,286 coordinate
  occurrences. The expected updated fingerprints are source
  `9152df71dfb9b8bb8512fe617dcabab9750d33075f2e136364fbc47d6f013070`,
  inventory
  `e10da8df32c1eb7d022f3b5bfce8a137d86d8ecaeee5aa16b81c853a4f79820c`,
  classification
  `e63b310467fc785f58b3e93f82621f043d4f206d8317e92f966bd55fd7ec43e6`,
  and occurrence
  `6afcd56ff5d43390acd8503f7661903dd62edbffa36d869e20686cb9cb02ed99`.

Owner runtime acceptance completed on 2026-07-18 from checkpoint `6ef67635b`:

- the independently bounded accepted session contained 97 records: 83 ordinary
  moves, five teleports, four markers, one logout/login pair, one snapshot, and
  exact start/stop boundaries;
- every record round-tripped exactly and the event chain contained no
  coordinate discontinuity;
- surface `(216,468,0)` projected exactly to underground `(216,468,-1)`, while
  surface `(226,440,0)` projected exactly to upper floor `(226,440,+1)`;
- a same-level 200-tile teleport retained level `0`, and logout, reconnect,
  snapshot, and stop all retained `(226,640,0)`; and
- the owner reported no crash, incorrect location, collision issue, missing
  scenery, or other behavioral/visual regression.

The accepted runtime proves both the legacy adapter and checked Player mirror
survive the same real movement/session path. A `-2` destination, converted map,
or changed gameplay consumer was not part of this acceptance test.

Slice 12 does not make layered Player location authoritative, add a second
persistence column, change `Entity`, mirror NPC movement, key runtime regions
by level, alter a packet, or enable signed coordinates beyond the legacy
adapter's representable range.

### Slice 13: Checked Player logical-region membership shadow

Objective: cross the next runtime boundary by maintaining level/world-space
qualified Player region membership without replacing or querying the existing
packed `RegionManager` storage.

Delivered:

- `LayeredRegionMembershipMirror`, an atomic checked `WorldRegionKey` shadow
  derived only from an authoritative layered location mirror;
- explicit membership initialization and synchronization alongside the Player
  location mirror during initial placement and every existing location change;
- a read-only `Player.getLayeredRegionKey()` accessor that first verifies the
  checked Player location mirror and then refuses stale/uninitialized region
  membership;
- movement-completion and login/logout invariant checks through the combined
  mirror chain; and
- a `::layerparity` precondition that validates both Player mirrors before any
  private trace command runs.

The shadow membership uses signed floor-divided 48-tile region coordinates and
includes world-space identity and level. Therefore the same geographic region
on levels `-1`, `0`, and `+1` is three distinct logical memberships, while
different tiles inside one region intentionally share a membership key.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirteen.py` — 2 tests passed;
- fixtures covered an exact tile `47` to `48` region boundary, same-region tile
  movement, level `-2`, negative X/Y floor division, and distinct instance
  world-space identity;
- Slice 1 through Slice 13 regressions all pass (40 tests), including the
  explicit coordinate-package and runtime-consumer allowlists;
- World Builder discovery passed 13 tests, the standalone-layout guard passed,
  and the bundled-Ant server build authority remained internally consistent;
- the authoritative server build succeeds for 727 core and 488 plugin sources;
  and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected updated fingerprints are source
  `7303eb74471c6d61d20f6411d762776ddc58218f2f9c8c3eafa9a80d40d06f62`,
  inventory
  `14de9781af919280a405a0bf71026f08e4406cfd07387ac781ecc9dfa2a8f314`,
  classification
  `005b4c252206bff3c644da94da11ab4843d5b87aea8888f048bf0eb78baed130`,
  and occurrence
  `546732da2a27273ab56a0a8a0c1d5a9242fa9f83ced3df4d785292870357c414`.

Safety boundary:

- `RegionManager.regions` remains the same packed nested integer map, and all
  registration, lookup, visibility, collision, and cache paths continue to use
  it exclusively;
- no `ConcurrentHashMap<WorldRegionKey,...>` runtime storage exists;
- the shadow key is not sent to the client or saved to the database; and
- no terrain, placements, maps, player data, or public server were changed.

Owner runtime acceptance is complete. The accepted geometry portion used Y
`620` because the originally suggested Y `640` tile was water:

- ordinary movement from `(239,620,0)` to `(240,620,0)` changed logical region
  `(4,12)` to `(5,12)`, and movement back changed it to `(4,12)` again;
- the retained marker names `region-east` and `region-west` are treated only as
  labels because in-game east/west presentation is opposite the increasing-X /
  decreasing-X wording originally supplied; future instructions must describe
  the direction numerically instead;
- vertical travel from `(216,468,0)` to `(216,468,-1)` retained geographic
  region `(4,9)` while the level-qualified membership changed, then returned
  exactly to level `0`; and
- all 14 records were round-trip exact, the trace completed with a snapshot and
  stop, and the owner reported no behavioral or visual issue.

A separate reconnect-only trace then recorded exactly five events in the
required order: start, logout, login, snapshot, and stop. Every event retained
legacy `(216,468)`, layered `(216,468,0)`, and logical region `(4,9)`, and every
round trip was exact. Together the two traces prove the checked Player location
and region-membership mirrors remain synchronized across ordinary region
crossings, vertical travel, and a real logout/reconnect without participating
in authoritative region behavior.

### Slice 14: Checked legacy Player persistence shadow

Objective: cross the current Player location into the persistence boundary
without adding a column, changing an existing value, or making the layered
representation authoritative.

Selected boundary:

- Player persistence precedes NPC mirroring because persistence is an explicit
  prerequisite to the streaming phase and can be proven without content
  changes;
- runtime NPC mirroring remains deferred because the preserved NPC 67 roaming
  maximum Y `6549` is outside the four-plane codec. Mandatory mirroring could
  turn that known source anomaly into a runtime failure before its content
  ownership is resolved; and
- projecting a packed location exactly once avoids comparing two independently
  changing Player atomics on the asynchronous save thread.

Implemented:

- `LegacyPlayerLocationPersistenceSnapshot`, an immutable
  `legacy-player-location-shadow-v1` projection captured from one authoritative
  packed `Point` read;
- checked packed-to-layered-to-packed construction with the original packed X/Y
  retained for the current database writer;
- save-path use of the snapshot for the existing `PlayerData.xLocation` and
  `PlayerData.yLocation` values;
- the same checked projection for the separate offline/admin player-location
  update entry point; and
- load-path capture before `Player.setInitialLocation`, followed by an exact
  comparison against the accepted Player layered mirror.

Safety boundary:

- the database schema, SQL statements, `PlayerData` shape, packed X/Y columns,
  and authoritative runtime `Point` remain unchanged;
- the snapshot deliberately has no layered-to-legacy factory and cannot encode
  level `-2`, signed legacy X, or non-global world spaces;
- asynchronous save captures `player.getLocation()` once and does not risk a
  false mismatch by separately sampling the moving Player mirror; and
- no automatic migration changes a database row, and no terrain, placement,
  archive, client, packet, public server, or live process is changed by the
  implementation.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-fourteen.py` — 2 tests passed;
- fixtures covered every legacy plane edge, aligned underground coordinates,
  exact retained packed writes, matching/mismatched load mirrors, nulls, and
  out-of-range X/Y refusal;
- Slice 1 through Slice 14 regressions all pass (42 tests), including the
  expanded coordinate-package and consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 728 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts: 1,771 terrain sectors, 49,816 placements, 20 transition edges, one
  unresolved coordinate, 211 classified owners, and 1,286 occurrences. The
  expected fingerprints are source
  `7a948be11d3d70999361af079895c205979f4a29d4505368f28a53124ea47a42`,
  inventory
  `bbe485a45eb44b0abb68e5d1c64c1f3376f4476e347ee364a68b9901ec2dbbbe`,
  classification
  `5e9f34d35d97aae12b0f22349871757e93aea79bbf62ec24a189edaf30cab620`,
  and occurrence
  `546732da2a27273ab56a0a8a0c1d5a9242fa9f83ced3df4d785292870357c414`.

Owner runtime acceptance is complete:

- the owner teleported the private `devduck` account to packed `(216,3300)`,
  started parity observation, logged out normally, and reconnected without
  leaving the underground location;
- the five-event trace contained start, logout, login, snapshot, and stop in
  order, and every event retained packed `(216,3300)`, layered
  `(216,468,-1)`, logical region `(4,9)`, and exact round-trip status;
- a read-only query after reconnect confirmed private SQLite player row `1`
  stores X `216`, Y `3300`; and
- the owner reported no visual, location, login, logout, or reconnect issue.

The private test changed only the normal saved location of the disposable dev
account through the existing logout save. It did not migrate a schema or row,
and no public/live data was accessed.

### Slice 15: Logical visibility-window projection

Objective: define the first incremental-streaming foundation value while
leaving the current packed simulation, entity-interest, cache, and client
window paths untouched.

Selected boundary:

- keep the configured `view_distance` semantics, where one unit expands to
  eight tiles, so the projection can be compared to current behavior;
- declare logical region size independently on `WorldRegionKey` rather than
  deriving it from terrain-sector identity. Both remain 48 tiles during
  parity, but one module can change later without silently redefining the
  other;
- use inclusive min/max logical-region bounds qualified by world space and
  signed level; and
- project only. Do not enumerate regions/entities, fill a cache, attach the
  window to Player, or send it to the client in this slice.

Implemented:

- immutable `WorldRegionWindow` with checked construction, signed floor-divided
  `around(...)` projection, containment, deterministic equality/hash/string,
  and overflow-safe region counts;
- a separate `WorldRegionKey.REGION_SIZE` contract replacing its incidental
  dependency on terrain-sector accessors; and
- default and explicit-distance
  `RegionManager.getLayeredVisibleRegionWindow(...)` projections using checked
  conversion from the existing eight-tile view-distance units.

Safety boundary:

- `RegionManager.regions`, current `Point` window calculation, visible-region
  and visible-object caches, entity enumeration, collision, and packets remain
  unchanged;
- no `WorldRegionWindow` cache or authoritative lookup exists;
- Player, NPC, client, renderer, persistence, terrain, and Builder do not
  consume the new value; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-fifteen.py` — 2 tests passed;
- fixtures covered current view-distance bounds, inclusive containment, signed
  floor division, level and world-space isolation, deep level `-2`, equality,
  nulls, inverted bounds, arithmetic overflow, and region-count overflow;
- Slice 1 through Slice 15 regressions all pass (44 tests), including the exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 729 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `72ea1e919715af18d63e4fdecdb9ae03b9e739360877a7b319e5af842cd5b45b`,
  inventory
  `250d623d536c2d943ae1cf41334e7fe029fa471a66380b5d64d511c718e9c985`,
  classification
  `909403149731c73741a813bed03cddb98d3166c8e2e68ddfea8b2c839d6f262c`,
  and occurrence
  `546732da2a27273ab56a0a8a0c1d5a9242fa9f83ced3df4d785292870357c414`.

No owner runtime route is required because no runtime state, lookup, cache, or
client consumes the projection in this slice.

### Slice 16: Checked Player visibility-window shadow

Objective: make the accepted logical interest-window projection a checked
Player shadow and expose it in stable private diagnostics without making it an
authority for entity lookup, residency, packets, or client loading.

Selected boundary:

- synchronize an immutable `WorldRegionWindow` shadow only from the accepted
  Player layered location and configured view distance during the existing
  initial-location and movement paths;
- require the current location mirror, region-membership mirror, and projected
  visibility window to agree at session and private-observer boundaries;
- retain the original parity-event v1 schema for existing logs while emitting
  an explicit v2 event whose snapshots add the world space, signed level,
  configured grid distance, tile radius, inclusive logical-region bounds, and
  checked region count; and
- keep traces doubly opt-in, dev-only, private/local, identity-isolated, and
  free of username text, address, or credential material.

Implemented:

- `LayeredVisibilityWindowMirror`, an initialized/stale-state refusing Player
  shadow with immutable value equality;
- Player synchronization and read-only validation across initial placement,
  movement, and login/logout, derived through the existing manager projection;
- parity observer v2 capture tied to the server's configured view distance,
  including refusal if an active trace is restarted with a different window
  configuration; and
- a retained v1 schema plus the additive Draft 2020-12
  `layered-map-parity-event-v2` schema.

Safety boundary:

- inherited packed Player location, packed RegionManager storage, current
  visibility/object caches, entity enumeration, collision, pathing, packets,
  terrain, and client loading remain authoritative and unchanged;
- the new Player window is checked only, never passed to `getRegion(...)`, used
  as a cache key, or written to persistence;
- the v2 diagnostic is not enabled in normal local or hosted configuration;
  and
- no database schema/row, terrain, placement, archive, Builder project,
  public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-sixteen.py` — 2 tests passed;
- fixtures covered uninitialized state, equivalent projections, stale bounds,
  signed-level and world-space mismatch, non-mutating refusal, null input,
  configured-distance/radius projection, JSON and compact output, the retained
  v1 snapshot layout, and arithmetic/invalid-distance refusal;
- the observer fixture validated v2 events and their Draft 2020-12 schema,
  including visibility fields on both sides of moves, trace identity isolation,
  active configuration mismatch, exact coordinate round trips, and exclusion
  of username text, address, and password material;
- Slice 1 through Slice 16 regressions all pass (46 tests), including exact
  coordinate-package and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 730 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts: 1,771 terrain sectors, 49,816 placements, 20 transition edges, one
  unresolved coordinate, 211 classified owners, and 1,286 occurrences. The
  expected fingerprints are source
  `2ff279f25945a1a9224e27a687da02bb6883e457db89391454c3fba12f123770`,
  inventory
  `23c90d3698a79b984995a6c7205609d69cac337695a878372bbebfac4440c6cb`,
  classification
  `5b308fc3dd094bf964ac3837d642d8397e2328a455e10ac0d8ed235ca3118017`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

Owner runtime acceptance is complete:

- the owner started at packed `(223,620)`, moved to X `224`, returned to X
  `223`, then teleported through surface packed `(216,468)` and underground
  packed `(216,3300)` before taking a final snapshot and stopping;
- all 11 v2 events used configured grid distance `16`, checked tile radius
  `128`, and exact coordinate round trips;
- the X `223` window was global level `0`, regions X `1..7`, Y `10..15`, count
  `42`; X `224` correctly advanced the minimum X region to `2` and count to
  `36`, and returning to X `223` restored the original bounds;
- surface and underground `(216,468)` projections retained identical region
  bounds X `1..7`, Y `7..12`, count `42`, while changing only from level `0`
  to level `-1`; and
- the owner reported no visual, loading, movement, teleport, or interaction
  issue.

Only the private development server and disposable dev account were involved.
No public/live server, database, map, placement, archive, or player data was
accessed or changed by this validation beyond the dev account's normal
location update.

### Slice 17: Deterministic logical interest delta

Objective: define the immutable entered/retained/exited region-key difference
between two accepted logical visibility windows without attaching it to Player,
RegionManager storage, packet production, or client residency.

Selected boundary:

- materialize window membership only through an explicit caller-supplied
  per-window allocation budget; this guards accidental large allocations
  without imposing a map/world capacity limit;
- preserve deterministic X-major/Y-minor ordering, matching the current packed
  manager's region-window iteration order for later parity comparison;
- compare complete `WorldRegionKey` identity, so a world-space or signed-level
  change has no retained keys even when X/Y bounds are identical; and
- expose immutable entered, retained, and exited lists plus level/world-space
  change indicators, but perform no loading, lookup, caching, or mutation.

Implemented:

- immutable `WorldRegionInterestDelta` with explicit previous/current windows,
  deterministic entered/retained/exited key lists, no-op detection, and
  level/world-space change indicators;
- complete `WorldRegionKey` comparison rather than X/Y-only comparison; and
- a required caller-owned materialization budget with checked refusal before
  list allocation when either window exceeds it.

Safety boundary:

- RegionManager's packed maps, packed window cache, object caches, region
  creation, and visibility enumeration are unchanged;
- the Slice 17 checkpoint introduced no Player or observer consumer; later
  private diagnostic adoption remains a separately recorded slice;
- packets, entity selection, terrain, client residency, persistence, and
  Builder remain unchanged; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-seventeen.py` — 2 tests passed;
- fixtures covered the accepted X `223→224→223` windows, deterministic key
  order, shrinking/growing and equal-size shifts, no-op retention, complete
  level/world-space separation, immutable outputs, exact/insufficient/invalid
  budgets, enormous-window early refusal, nulls, and summary output;
- Slice 1 through Slice 17 regressions all pass (48 tests), including exact
  coordinate-package and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 731 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts and the accepted Slice 16 fingerprints: source
  `2ff279f25945a1a9224e27a687da02bb6883e457db89391454c3fba12f123770`,
  inventory
  `23c90d3698a79b984995a6c7205609d69cac337695a878372bbebfac4440c6cb`,
  classification
  `5b308fc3dd094bf964ac3837d642d8397e2328a455e10ac0d8ed235ca3118017`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because the new value has no runtime
consumer.

### Slice 18: Private logical interest-delta diagnostics

Objective: observe the accepted logical interest delta across real private
movement and teleports without calculating or retaining it during normal
gameplay and without adopting it for server or client interest decisions.

Selected boundary:

- calculate deltas only inside the existing doubly opt-in, dev-only private
  parity observer and only for events that have both before and after points;
- cap each diagnostic materialization at 4,096 keys per window, far above the
  current private configuration but low enough to refuse accidental runaway
  debug allocations;
- emit full entered/exited key identities and compact entered/retained/exited
  counts, while omitting the usually much larger retained-key list already
  inferable from the two window snapshots; and
- retain v1 and v2 schemas for old logs while emitting an additive v3 event
  contract with a nullable `interestDelta` field.

Implemented:

- v3 observer events with a nullable logical-interest delta derived only from
  before/after diagnostic snapshots;
- previous/current and entered/retained/exited counts, world-space/level/no-op
  flags, and deterministic entered/exited key arrays;
- a 4,096-key per-window debug allocation ceiling whose refusal is captured as
  observer error state rather than changing gameplay; and
- a retained v1/v2 schema lineage plus a strict Draft 2020-12 v3 schema.

Safety boundary:

- delta computation occurs only after the private observer is explicitly
  enabled in server configuration and started by a dev/admin command;
- normal movement with the observer disabled performs no delta materialization;
- Player stores only the already accepted current-window mirror; it does not
  retain interest deltas or key lists;
- RegionManager packed lookup/caches, entity selection, packets, terrain,
  client residency, persistence, and Builder remain unchanged; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-eighteen.py` — 2 tests passed;
- the observer integration fixture emitted and schema-validated v3 start,
  movement, teleport, marker, snapshot, session, and stop events, including
  exact entered/exited key identities across level changes and nullable deltas
  where no before point exists;
- the schema-lineage guard validates retained v1/v2 contracts, required v3
  fields, region-key identity, observer allocation bounds, and absence from
  Player and RegionManager;
- Slice 1 through Slice 18 regressions all pass (50 tests), including exact
  coordinate-package and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 731 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts and fingerprints: source
  `2ff279f25945a1a9224e27a687da02bb6883e457db89391454c3fba12f123770`,
  inventory
  `23c90d3698a79b984995a6c7205609d69cac337695a878372bbebfac4440c6cb`,
  classification
  `5b308fc3dd094bf964ac3837d642d8397e2328a455e10ac0d8ed235ca3118017`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

Owner runtime acceptance is complete:

- the owner started at packed `(222,620)`, traversed X `223→224→223`, then
  teleported through surface packed `(216,468)` and underground packed
  `(216,3300)` before snapshot and stop;
- the 13-event v3 session was sequential and every point round-tripped exactly,
  with all count identities and entered/exited array lengths consistent;
- X `222→223` was a true no-op interest transition: 42 previous, current, and
  retained keys with zero entered/exited keys;
- X `223→224` retained 36 and exited exactly level-0 keys `(1,10..15)`;
  returning to X `223` re-entered those same six keys with none exited;
- the longer same-level teleport retained 21, entered 21, and exited 21 keys in
  deterministic X-major/Y-minor order; and
- the surface-to-underground transition retained zero, exited all 42 level-0
  keys, entered the corresponding 42 level-`-1` keys, and marked only the level
  identity as changed. The owner reported no visual, loading, movement,
  teleport, or interaction issue.

Only the private development server and disposable dev account were involved.
No public/live server, database, map, placement, archive, or player data was
accessed or changed by this validation beyond the dev account's normal
location update.

### Slice 19: Legacy packed-region coverage projection

Objective: describe every logical region key overlapped by one current packed
48-tile region cell before attempting any storage, cache, or interest migration.

Selected boundary:

- model the nominal packed cell independently from its intersection with the
  checked legacy point-codec domain;
- retain zero-key coverage for padded cells entirely beyond the legacy domain
  and partial coverage at the terminal X/Y edges rather than inventing layered
  coordinates for unsupported tiles;
- deduplicate logical keys in packed-Y traversal order and explicitly report
  whether one packed cell crosses signed levels; and
- expose a read-only RegionManager projection from packed region coordinates,
  without looking up, creating, rekeying, splitting, or mutating a `Region`.

Implemented:

- immutable `LegacyPackedRegionCoverage` with nominal bounds, checked legacy
  intersection, legacy tile count, immutable logical-key coverage, containment,
  level-straddle detection, and explicit empty/partial states;
- checked arithmetic and negative-coordinate refusal before calculating packed
  tile bounds; and
- `RegionManager.getLayeredRegionCoverage(...)`, a projection-only method that
  never reads or writes the packed region maps.

Audit findings:

- the server's padded height exposes 84 packed region rows per X column;
- 39 rows cover one logical key, 40 cover two logical keys, and five padded
  rows lie entirely beyond packed Y `3775` and cover none;
- packed rows 19 and 39 are the two true level straddles; the other 38 dual-key
  rows are same-level misalignments on legacy planes 1 and 2;
- terminal row 78 contains only 32 supported Y tiles per X tile, while rows
  79..83 are wholly outside the checked legacy codec; and
- all packed Y values `0..3775` resolve to a key contained by their packed
  region coverage.

Safety boundary:

- packed RegionManager maps, region construction, tile storage, visibility and
  object caches, entity enumeration, collision, and packets remain unchanged;
- no `Region` receives a logical key and no region is split or rekeyed;
- Player, diagnostics, persistence, terrain archives, client, and Builder do
  not consume the new coverage value; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-nineteen.py` — 2 tests passed;
- fixtures covered every server padded Y-region row, every valid packed Y at
  current X `1007`, both level straddles, same-level dual coverage, aligned
  surface/underground rows, terminal partial X/Y cells, post-codec padding,
  deterministic immutable keys, nulls, negatives, and arithmetic overflow;
- Slice 1 through Slice 19 regressions all pass (52 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 732 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `97a79efcd20d03a0f21337b2f9b87e6aa296eff45f03cdf016ec9129bebf1dbd`,
  inventory
  `47fb30ee315dd4026011335628c7bd87fcc16270bff5a18ee85ff33b36e5dcdb`,
  classification
  `cf6e307b0ccc534c540d150a2a461a22e1bc65f1e34622af02eafcae1057479b`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither runtime storage nor a
diagnostic consumes the projection.

### Slice 20: Packed/logical window coverage comparison

Objective: compare the union of logical keys covered by the current packed
visibility candidate window with the intended signed layered visibility window,
before attempting any region-storage or interest-authority migration.

Selected boundary:

- derive both windows from one checked legacy `Point` and the current
  grid-distance convention;
- report exact logical keys, packed-union keys, missing keys, extra keys, and
  packed cells outside the checked legacy codec rather than reducing the result
  to one pass/fail flag;
- require explicit caller budgets for packed cells and materialized logical
  keys; and
- expose the comparison as a read-only RegionManager projection without region
  lookup, construction, cache access, entity enumeration, or packet changes.

Important interpretation:

- extra logical keys in the packed candidate union describe coarse storage
  coverage, not proof that current gameplay exposes distant entities; existing
  per-entity distance, plane, and visibility filters remain authoritative; and
- missing keys at signed coordinate boundaries describe the legacy packed
  codec's inability to represent negative positions, not an instruction to
  silently clamp a future layered window.

Implemented:

- immutable `LegacyPackedVisibilityCoverageComparison`, including packed bounds
  and cell count, unsupported packed-cell count, deterministic expected and
  packed-union keys, missing and extra keys, and exactness;
- a reusable, immutable, caller-budgeted logical-window materializer on
  `WorldRegionInterestDelta`; and
- `RegionManager.compareLayeredVisibleRegionCoverage(...)`, a projection-only
  method that never reads or writes packed region maps or visibility caches.

Focused findings at the current grid distance of 16:

- representative surface packed `(223,620)` and underground packed
  `(216,3300)` windows each have exact 42-key coverage;
- representative upper-floor packed `(223,1564)` expects 42 keys while the
  legacy packed union covers 56, all expected keys plus 14 coarse same-level
  keys caused by the 944/48 alignment remainder;
- directly on packed Y `944`, the intended level-1 window includes 21 signed
  local-Y keys that legacy packed storage cannot name, while its 49-key union
  includes 28 extras from the prior level and its coarse trailing edge; and
- at signed world origin, 27 of 36 packed candidates are unrepresentable while
  the logical window correctly retains those 27 negative-X/Y keys as missing
  rather than clamping them.

Safety boundary:

- packed RegionManager storage, visibility/object caches, entity enumeration,
  distance/plane filtering, collision, packets, terrain, persistence, client,
  and Builder remain unchanged;
- comparison output is not stored on Player or emitted through diagnostics;
  and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty.py` — 2 tests passed;
- Slice 1 through Slice 20 regressions all pass (54 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 733 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `bd19756fd553986a748ce682fb3bcbad2cdc1834992fbeb2ea97e9bf7e7c6571`,
  inventory
  `ec3468830f910798c1e4f172ef8bd032d49189bc9a48bd9f3741b2049aa8ba3d`,
  classification
  `cb783fef5c4bfb6036429b193198d5858d078573db1af80e4a85034a0b33f159`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither gameplay nor private
diagnostics consumes the comparison.

### Slice 21: Private packed/logical coverage diagnostics

Objective: capture Slice 20's missing/extra packed-window evidence at real
private-server locations while preserving every current gameplay authority.

Selected boundary:

- advance new traces to an additive v4 JSONL schema while retaining v1-v3
  schemas for existing evidence;
- attach one bounded packed/logical comparison to each emitted current-point
  snapshot, including packed bounds, counts, exactness, and only the missing and
  extra key identities needed to explain discrepancies;
- reuse the existing dev-only command, disabled-by-default server capability,
  identity-safe log path, and explicit trace lifecycle; and
- keep comparison construction inside the observer rather than Player,
  RegionManager lookup/cache paths, packets, or the client.

Implemented:

- v4 observer output with packed bounds, packed/unsupported cell counts,
  expected/covered/missing/extra key counts, exactness, and deterministic
  missing/extra key arrays for each emitted current point;
- separate 4,096-cell and 4,096-key diagnostic budgets, with failures retained
  through the existing trace status rather than affecting gameplay; and
- additive `layered-map-parity-event-v4` JSON Schema while v1-v3 remain
  unchanged and readable.

Safety boundary:

- the observer remains disabled by default and requires both private-server
  capability enablement and an explicit dev/admin trace command;
- Player only invokes the existing observer hooks; it does not store or consume
  the comparison;
- RegionManager storage, visibility/object caches, entity enumeration,
  distance/plane filtering, collision, packets, terrain, persistence, client,
  and Builder remain unchanged; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-one.py` — 2 tests
  passed;
- the compiled observer fixture emitted eight v4 events across a level boundary,
  a distant teleport, markers, session events, and stop; all events validated
  against the v4 schema and exact missing/extra identities were asserted;
- Slice 1 through Slice 21 regressions all pass (56 tests), including schema
  lineage and exact runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 733 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged Slice 20
  content counts and fingerprints.

Owner runtime acceptance is complete:

- one 13-event v4 trace was sequential from start through stop; every packed
  point round-tripped exactly and every expected/missing/extra count matched its
  emitted array and logical-window count;
- aligned underground packed `(216,3300)` and aligned surface packed
  `(223,620)` each reported exact 42-key coverage, including after returning
  from the probes;
- upper-floor packed `(223,1564)` reported 42 expected keys, 56 covered keys,
  zero missing, and the predicted 14 same-level extras at logical Y `9` and
  `16` across seven X columns;
- boundary packed `(223,944)` reported 42 expected keys, 49 covered keys, the
  predicted 21 missing level-1 local-Y `-3..-1` keys, and 28 extras comprising
  prior-level Y `17..19` plus level-1 Y `3` across seven X columns;
- all sampled packed cells were inside the checked legacy codec, so unsupported
  packed-cell count remained zero; and
- the owner observed normal behavior at authored surface and underground
  locations. The upper-floor and exact boundary probes appeared out of bounds,
  which is expected because these mathematical samples are not authored
  destinations; no loading, movement, collision, or return-path defect was
  observed.

Only the private development server and disposable dev account were involved.
No public/live server, database schema, map, placement, archive, or player data
was accessed or changed by this validation beyond the dev account's normal
location update.

### Slice 22: Exact packed-cell tile partitions

Objective: turn Slice 19's logical-key coverage into exact, lossless tile
rectangles that a later region-storage or conversion milestone can split,
without adopting those rectangles at runtime.

Selected boundary:

- partition only the checked legacy-supported portion of one packed 48-tile
  region cell;
- retain packed absolute bounds, packed cell-local bounds, signed logical tile
  bounds, logical region key, and tile count for each contiguous fragment;
- preserve deterministic packed-Y order, including true level boundaries,
  same-level upper-plane misalignment, terminal partial cells, and empty padded
  cells; and
- expose a read-only RegionManager projection without reading or mutating a
  `Region`, tile grid, entity collection, cache, terrain archive, or placement.

Implemented:

- immutable `LegacyPackedRegionPartition` and nested fragment values retaining
  packed absolute/cell-local bounds, signed logical bounds, logical region key,
  containment, and exact tile count;
- checked losslessness invariants requiring fragment tile totals and key order
  to match `LegacyPackedRegionCoverage`; and
- `RegionManager.getLayeredRegionPartition(...)`, a projection-only method
  that never looks up or mutates a runtime `Region`.

Audit findings:

- per X column across the 84 current padded packed rows, 39 cells produce one
  fragment, 40 produce two fragments, and five padded cells produce none;
- rows 19 and 39 split across signed levels, while the other 38 two-fragment
  rows split between adjacent same-level logical region Ys on legacy planes 1
  and 2;
- an aligned full cell retains all 2,304 tiles in one 48×48 fragment;
- true boundary row 19 divides into 1,536 level-0 tiles and 768 level-1 tiles;
- terminal packed cell `(682,78)` retains exactly the supported 32×32 tile
  rectangle, and padded row 79 is explicitly empty; and
- every fragment key, order, and summed tile count matches the earlier coverage
  projection across all padded Y rows.

Safety boundary:

- runtime Region maps, Region tile arrays, entity collections, visibility and
  object caches, collision, packets, terrain, persistence, diagnostics, client,
  and Builder remain unchanged;
- no fragment is cached, stored on Player, or used for lookup; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-two.py` — 2 tests
  passed;
- Slice 1 through Slice 22 regressions all pass (58 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 734 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `5b8651e485ddcf951963d067e1a79e59f319724c8dc05dccf86e7d1a840cff6c`,
  inventory
  `0e45a348e959466ad82c82b5e02904fb578b3c16835c9d5993d9b9407890d7bc`,
  classification
  `f38a83a4b9da10beb975c5fb61860ece81866173de11106bedbd75289388408c`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither gameplay nor private
diagnostics consumes the partition.

### Slice 23: Logical-region legacy assembly plans

Objective: invert Slice 22 so one signed logical region key can identify every
packed-cell fragment required to assemble its legacy-supported tiles before any
runtime tile storage is copied or rekeyed.

Selected boundary:

- retain the complete nominal logical 48×48 bounds separately from their
  intersection with the global four-plane legacy codec;
- report ordered packed source-cell coordinates and exact partition fragments,
  assembled/target tile counts, and complete/partial/unsupported status;
- treat negative extents, levels outside `{-1,0,1,2}`, non-global world spaces,
  and post-codec extents as explicit unsupported or partial results rather than
  clamping or inventing packed coordinates; and
- expose a read-only RegionManager projection without reading a runtime
  `Region`, tile array, entity collection, cache, terrain archive, or placement.

Implemented:

- immutable `LegacyLogicalRegionAssembly` with nominal target bounds,
  nullable legacy-supported bounds, ordered packed source fragments,
  target/assembled tile counts, and mutually exclusive complete, partial, and
  unsupported states;
- losslessness checks requiring fragments to match the requested logical key,
  span the exact supported X bounds, and cover supported Y without a gap or
  overlap; and
- `RegionManager.getLegacyLogicalRegionAssembly(...)`, a projection-only
  method that never looks up or copies runtime regions or tiles.

Audit findings:

- the legacy codec can supply 54,640 logical region keys across four levels,
  683 X columns, and 20 local-Y rows;
- 51,832 are complete 2,304-tile logical regions and 2,808 are partial terminal
  regions: 76 on terminal X only, 2,728 on terminal local Y only, and four at
  both terminal edges;
- all 27,320 surface/underground assemblies use one packed fragment, while all
  27,320 level-1/level-2 assemblies use two ordered packed fragments because
  their 944-tile plane offsets are not 48-aligned;
- representative level-1 region `(4,0)` assembles 768 tiles from packed cell
  `(4,19)` and 1,536 from `(4,20)`, while level-2 `(4,0)` assembles 1,536 from
  `(4,39)` and 768 from `(4,40)`;
- terminal logical `(-1,682,19)` retains its exact 32×32 intersection and is
  partial rather than silently padded; and
- negative logical extents, level `-2`, and non-global world spaces retain
  their nominal target bounds but correctly report no legacy sources.

Safety boundary:

- runtime Region maps, Region tile arrays, entity collections, visibility and
  object caches, collision, packets, terrain, persistence, diagnostics, client,
  and Builder remain unchanged;
- no source fragment is resolved to a runtime Region or copied into a new tile
  container; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-three.py` — 2 tests
  passed;
- fixtures cover one- and two-fragment assemblies, both upper levels,
  underground, terminal corners, negative/deep/instance refusal, every legacy
  local-Y row on all four levels, and every legacy X column;
- Slice 1 through Slice 23 regressions all pass (60 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 735 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `06fc1e3ab6cb6e0a9254daa118864e7fffa6d70d09a661155532bfaaf3e77f2b`,
  inventory
  `941737361f82b79f9eab00958bcbbf7a6e24f232056fafd486242291dd45bc41`,
  classification
  `c9c63f6326e27380b2b5c1715b6be86ccc430e8aede8d001fbfa3a075989e48d`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither gameplay nor private
diagnostics consumes the assembly.

### Slice 24: Logical-tile packed-source addressing

Objective: resolve one logical region-local tile to the exact legacy packed
point, packed source cell, cell-local indices, and Slice 23 source fragment
before authorizing any read-only access to runtime tile arrays.

Selected boundary:

- require checked logical local X/Y in `0..47` and preserve the requested
  logical location even when no legacy representation exists;
- distinguish representable addresses from terminal-edge, negative, deep-level,
  and non-global unsupported addresses without clamping;
- prove the packed point belongs to the selected assembly fragment and its
  packed region/local coordinates agree exactly; and
- expose a read-only RegionManager projection without looking up a runtime
  `Region`, reading a `TileValue`, or affecting caches, collision, or packets.

Implemented:

- immutable `LegacyLogicalTileAddress` retaining logical region key/local X/Y
  and exact logical location for every request;
- checked representable addresses with legacy point, packed source region,
  packed cell-local X/Y, and the exact Slice 23 source fragment;
- unsupported addresses that retain logical identity with no fabricated packed
  point/source and refuse packed-only accessors; and
- `RegionManager.getLegacyLogicalTileAddress(...)`, a projection-only method
  that never resolves a runtime Region or reads a `TileValue`.

Focused findings:

- logical surface region `(0,4,12)` local `(31,44)` maps exactly to packed
  point `(223,620)`, packed cell `(4,12)`, and local `(31,44)`;
- logical level-1 region `(1,4,12)` at the same local position maps to packed
  `(223,1564)`, packed cell `(4,32)`, and local `(31,28)`;
- all 2,304 tiles in level-1 logical region `(1,4,0)` round-trip exactly, with
  768 addressed through packed row 19 and 1,536 through row 20;
- terminal logical region `(-1,682,19)` exposes exactly 1,024 representable
  addresses and retains the other 1,280 logical tiles as unsupported; its last
  supported tile maps from logical `(32767,943,-1)` to packed `(32767,3775)`,
  cell `(682,78)`, local `(31,31)`; and
- negative, level `-2`, and isolated-space requests retain their exact logical
  locations without a packed source.

Safety boundary:

- runtime Region maps, Region tile arrays, entity collections, visibility and
  object caches, collision, packets, terrain, persistence, diagnostics, client,
  and Builder remain unchanged;
- no address resolves or copies a runtime `TileValue`; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-four.py` — 2 tests
  passed;
- Slice 1 through Slice 24 regressions all pass (62 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 736 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged content
  counts. The expected fingerprints are source
  `5400a2be5bba0a10650949892d7d9f57c6b8d9c5ad414fe114de1d3f4e4a65a5`,
  inventory
  `148bc5c63085f239d9c6ff1bba43f6a19432e42c64dba6376e3531f76831cf9e`,
  classification
  `daaf54dbaca37bbaa3fc87c3bd99e0562d27d34f997f5d90329697e11ea73135`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither gameplay nor private
diagnostics consumes the address.

### Slice 25: Detached logical-region tile snapshots

Objective: make the first read-only runtime tile seam by copying current packed
`TileValue`s into one detached logical 48×48 snapshot through Slice 23/24
addressing, without making the copy authoritative.

Selected boundary:

- copy every legacy-supported tile value; never expose an internal mutable
  snapshot tile and never write back to the packed source;
- leave unsupported logical tiles absent, while representing an absent packed
  source Region with the exact blank `TileValue` the current lazy region path
  would create and reporting the missing source count;
- record source, supported, and target counts plus a deterministic SHA-256 over
  support markers and complete tile state; and
- expose an explicit read-only RegionManager method without caching the
  snapshot or routing collision, pathing, visibility, terrain, packets, or
  entities through it.

Implemented:

- `LayeredRegionTileSnapshot`, which assembles one logical 48×48 target from
  Slice 23 fragments and copies every supported `TileValue` into private
  detached storage;
- complete copying and hashing of the public terrain values plus private
  terrain/dynamic collision and projectile state, with defensive copies for
  mutable count arrays and every returned tile;
- explicit complete, terminal-partial, unsupported, and absent-packed-source
  behavior, including exact blank legacy tile values and missing-source
  reporting without creating a Region; and
- `RegionManager.getLayeredRegionTileSnapshot(...)`, backed by a non-mutating
  packed-region peek and deliberately unused by Player or any gameplay path.

Focused findings:

- logical level `+1` region `(4,0)` copies all 2,304 tiles from packed cells
  `(4,19)` and `(4,20)`, including the exact seam at logical local Y `15/16`;
- changing a returned `TileValue` cannot alter the snapshot or a later return,
  identical inputs produce an identical SHA-256, and changed full-fidelity tile
  state changes the fingerprint;
- when packed cell `(4,20)` is absent, the snapshot reports one missing source
  and represents that cell's 1,536 supported tiles with the current blank
  `TileValue` defaults rather than mutating RegionManager to create it;
- terminal logical region `(-1,682,19)` copies exactly 1,024 supported tiles
  and leaves 1,280 unsupported positions absent; and
- logical level `-2` remains explicitly unsupported by the legacy adapter,
  with no packed source lookup or tile read.

Safety boundary:

- packed Regions and `TileValue`s remain collision, pathing, visibility,
  terrain, packet, entity, and persistence authority;
- snapshots are uncached and cannot write back to packed storage;
- preflight recognizes the exact reviewed snapshot seam as resolved layered
  contract code, while the unresolved legacy Java-owner inventory remains 211;
  and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-five.py` — 2 tests
  passed;
- Slice 1 through Slice 25 regressions all pass (64 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 737 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `105a7538f52637385f25d241ae40a9e68c2d646a3a57bf40ef33a3954a2702d8`,
  inventory
  `7d3a71393321633a4d022b189b6dfe505090155b313b6def51b7fd57b745eff2`,
  classification
  `58cc12072cd73665066bfbf0e1994b93f90719246425e50022694a31ee4a6901`,
  and occurrence
  `60b2e4e975e11528c755e4c0127f6094b0b9fff2cdc5c07cd6471e535913bc7d`.

No owner runtime route is required because neither gameplay nor private
diagnostics consumes the snapshot. A later separately versioned private
diagnostic fingerprint remains the first proposed owner runtime consumer.

### Slice 26: Private logical-region tile-snapshot diagnostics

Objective: exercise Slice 25 against real private-server regions by emitting
only bounded metadata and a deterministic fingerprint for the player's current
logical region through the existing opt-in parity trace.

Selected boundary:

- advance new traces to an additive v5 JSONL schema while retaining v1-v4
  schemas for existing evidence;
- capture exactly one logical 48×48 tile snapshot for the emitted current
  location and record its region key, source/missing-source counts,
  supported/target counts, completeness, and SHA-256 without serializing tile
  payloads;
- bind the source only when a dev/admin explicitly starts a trace on a private
  server, retaining the existing identity-safe path and lifecycle; and
- keep snapshot capture under the observer's execution path and preserve
  packed RegionManager, collision, pathing, visibility, terrain, packets,
  entities, persistence, client, and Builder authority.

Implemented:

- additive `layered-map-parity-event-v5` JSONL output retaining every v4 field
  and adding one required `tileSnapshot` object;
- observer-owned source execution for the current logical region, with a
  required source binding when `::layerparity start` creates a trace;
- immutable observer metadata validating the exact 2,304-tile target,
  source/missing-source and supported counts, completeness, current-region
  identity, and lowercase SHA-256; and
- a dev-command adapter that captures Slice 25 snapshots through RegionManager
  without placing the snapshot on Player or any gameplay path.

Focused findings:

- the compiled eight-event observer trace emitted required v5 metadata at
  start, movement, teleport, marker, manual snapshot, logout, login, and stop;
- the terminal surface logical region containing packed `(100,943)` reported
  1,536 supported tiles of 2,304 and incomplete status;
- moving to packed `(100,944)` reported logical level `+1` region `(2,0)`, two
  packed source fragments, all 2,304 tiles, and complete status;
- teleporting to packed `(100,2832)` reported the corresponding complete
  underground logical region, while every metadata key matched the event's
  current logical region; and
- missing source bindings, inconsistent counts/completeness, non-2,304 targets,
  malformed fingerprints, null metadata, and wrong logical keys are refused
  into trace status rather than affecting gameplay.

Safety boundary:

- the capability remains disabled by default and can be started only through
  the existing dev/admin private command gate;
- each event copies and hashes one bounded 48×48 logical region but serializes
  metadata only, never tile payloads;
- Player still invokes only the established observer movement/session hooks
  and does not store, consume, or import the tile snapshot;
- packed RegionManager storage, collision, pathing, visibility/object caches,
  entity enumeration, terrain, packets, persistence, client, and Builder remain
  unchanged and authoritative; and
- no database, map, placement, archive, public server, or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-six.py` — 2 tests
  passed;
- the compiled observer fixture and all v1-v5 schema-lineage checks pass;
- Slice 1 through Slice 26 regressions all pass (66 tests), including exact
  resolved-contract and runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 737 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `b8ec78d3f2c951c52497287a27aac4b1a81ecc9340ddd0f8fade3664109aed9a`,
  inventory
  `fd2d8392fbf55562cfbc8fa20da266d558880ec0d42b1da572c6579d7c498a85`,
  classification
  `624d89e8005dc010ee5c84046c51646a109cbaa6f851dc8b44bfce3249f9862f`,
  and occurrence
  `bf61c1e731c6c0fdd0f7aee6d393ff61c89560b23e424bf5ada15a4dba92580f`.

Owner runtime acceptance is complete:

- the requested route produced 300 consecutive v5 events from start through
  stop: 287 moves, four vertical teleports, five markers, and two manual
  snapshots, with zero schema errors;
- every event round-tripped exactly, its snapshot key matched its current
  logical region, its supported/target and completeness identities held, its
  packed visibility coverage was exact, and no packed source was missing;
- 300 events crossed seven logical regions and produced exactly seven distinct
  fingerprints; every event within a given world-space/level/region key had
  one stable fingerprint, including 37 initial-surface samples and 101 samples
  in the most-traversed region;
- walking from X `239` to `240` changed surface region `(4,12)` to `(5,12)`
  and changed the fingerprint on precisely that logical boundary;
- the authored upper-floor marker at packed `(316,1493)` projected to logical
  `(316,549,+1)`, assembled all 2,304 tiles from two packed source fragments,
  and returning to surface region `(6,11)` restored its exact prior
  fingerprint;
- the underground marker at packed `(274,3397)` projected to logical
  `(274,565,-1)`, assembled all 2,304 tiles from one packed source, and the
  return to surface region `(5,11)` restored its exact prior fingerprint; and
- the owner completed the requested surface, boundary, upper-floor,
  underground, walking, and return route without reporting a visual, loading,
  collision, or interaction anomaly.

### Slice 27: Immutable logical tile state

Objective: detach read-only logical snapshot consumers from mutable legacy
`TileValue` while preserving every terrain, wall, collision, and projectile
field exactly and retaining a checked compatibility copy.

Selected boundary:

- define one immutable `LayeredTileState` with full legacy tile fidelity,
  defensive dynamic-count handling, value equality, and stable digest input;
- store that immutable value inside Slice 25 snapshots and expose it directly
  to future logical readers;
- retain `getTileValue(...)` only as a fresh detached legacy compatibility copy
  whose mutation cannot affect the state or snapshot; and
- preserve the existing snapshot fingerprint field order so adopted v5
  evidence remains comparable, without changing packed Region/TileValue
  ownership or any gameplay path.

Implemented:

- immutable `LayeredTileState` covering traversal, walls, overlay, elevation,
  public projectile flags, terrain/scenery blocking, terrain and dynamic
  collision state, and overlay/wall/dynamic projectile state;
- defensive dynamic-collision arrays, value equality/hash semantics, and
  deterministic digest contribution in the exact accepted Slice 25/v5 field
  order;
- a fresh full-fidelity `TileValue` compatibility bridge using a package-local
  state constructor, without changing the legacy default constructor, copy,
  mutation, or collision-refresh paths; and
- `LayeredRegionTileSnapshot` immutable internal storage plus
  `getTileState(...)`, while `getTileValue(...)` remains a fresh mutation-
  isolated legacy copy.

Focused findings:

- a tile with deliberately non-default public values, two blocking scenery
  owners, terrain mask `13`, overlapping dynamic-collision counts, two wall
  projectile owners, and three dynamic projectile owners round-trips with
  exact `TileValue.equals(...)` fidelity;
- changing either the original packed test tile, a returned collision-count
  array, or a returned legacy compatibility copy cannot alter immutable state
  or a later compatibility copy;
- equal states have equal hashes, changed private or public state compares
  unequal, and a separately encoded legacy digest exactly matches the new
  state's digest bytes; and
- complete, partial, unsupported, absent-source, fingerprint-stability, and
  returned-copy Slice 25 behavior remains unchanged after adopting immutable
  internals.

Safety boundary:

- current packed Regions and mutable `TileValue`s remain terrain, collision,
  pathing, visibility, packet, entity, and persistence authority;
- no cache, dual-write, logical mutation, collision consumer, or Player state
  adopts `LayeredTileState`;
- v5 diagnostics continue to consume only snapshot metadata and require no
  schema or runtime change; and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-seven.py` — 2 tests
  passed;
- Slice 1 through Slice 27 regressions all pass (68 tests), including the full
  Slice 25 snapshot fixture and exact runtime-consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 738 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `a55e6019ef03da666a541531901e494c4d1390a6755cd6b3f80f513a76521fa5`,
  inventory
  `fd2d8392fbf55562cfbc8fa20da266d558880ec0d42b1da572c6579d7c498a85`,
  classification
  `624d89e8005dc010ee5c84046c51646a109cbaa6f851dc8b44bfce3249f9862f`,
  and occurrence
  `bf61c1e731c6c0fdd0f7aee6d393ff61c89560b23e424bf5ada15a4dba92580f`.

No owner runtime route is required because existing v5 diagnostics consume
only snapshot metadata and the accepted fingerprint encoding is unchanged.

### Slice 28: Checked current-tile state parity

Objective: compare one immutable tile reached through the assembled logical
snapshot with the same tile reached directly through its current packed source,
without creating a Region or changing either path's authority.

Selected boundary:

- retain the checked Slice 24 address and exact logical location, direct packed
  immutable state, logical snapshot state, source-presence/comparability, and
  full-state equality result in one immutable comparison;
- distinguish exact, missing-packed-source, and unsupported logical tiles
  without treating a synthesized blank snapshot tile as direct parity;
- expose read-only RegionManager entry points for packed `Point` and
  `WorldLocation`, using only the existing non-mutating packed-region peek; and
- keep Player, collision, pathing, visibility, packets, caches, and diagnostics
  entirely outside this dormant comparison.

Implemented:

- immutable `LayeredTileStateParityComparison` retention of the checked tile
  address, exact logical location, packed-source presence, both immutable tile
  states, comparability, and full-state equality;
- explicit classification of exact parity, a comparable mismatch, a
  representable tile whose packed Region is absent, and a level that has no
  legacy packed representation;
- checked refusal when the logical snapshot key, supported state, or declared
  packed-source presence disagrees with the comparison input; and
- read-only RegionManager entry points for both a legacy packed `Point` and an
  explicit `WorldLocation`, using the established packed-region peek rather
  than the creating lookup.

Focused findings:

- logical `(223,44,+1)` resolves to packed `(223,988)` and compares exactly
  across a logical snapshot assembled from two packed source Regions;
- changing one direct packed tile field produces a comparable non-exact result
  without changing the captured logical state;
- an absent representable source retains its synthesized blank logical state
  but is explicitly missing and not comparable, rather than reporting false
  parity; and
- logical level `-2` remains explicitly unsupported with no direct or snapshot
  tile state, while wrong keys and contradictory source declarations are
  refused.

Safety boundary:

- comparison construction does not call the creating Region lookup or any
  mutable-tile method;
- current packed Regions and mutable `TileValue`s remain terrain, collision,
  pathing, visibility, packet, entity, and persistence authority;
- no Player, diagnostic, cache, collision, client, or World Builder path
  consumes the dormant comparison; and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-eight.py` — 2 tests
  passed;
- Slice 1 through Slice 28 regressions all pass (70 tests), including the
  exact coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 739 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `c50ede16e25e64b9edd08d87bd8fed49f7f2fab1d654bc94dd71e4c9f144cb53`,
  inventory
  `0b237d3a9e0ecd5ea3d29b2334e453f59381c730934eb780bd77fd840696bb25`,
  classification
  `1436c4e1e1ba17dcb72dcbb9717a235272c1c1efbbc289f8301367ebf415c8b4`,
  and occurrence
  `bf61c1e731c6c0fdd0f7aee6d393ff61c89560b23e424bf5ada15a4dba92580f`.

No owner runtime route is required because the comparison remains dormant.
Any private diagnostic adoption remains a separately versioned later slice.

### Slice 29: Bounded private current-tile parity diagnostics

Objective: make the dormant Slice 28 current-tile comparison AI-readable in an
opt-in private trace without adding another full comparison to every movement
event.

Selected boundary:

- advance new traces to additive v6 JSONL while retaining every v5 field and
  keeping the v1-v5 schemas beside it;
- emit logical location, nullable legacy packed address, representability,
  packed-source presence, missing-source, comparability, and exact full-state
  parity;
- sample only observer `start`, `marker`, `teleport`, and `stop` events, with an
  explicit JSON null on move, snapshot, login, and logout; and
- bind the metadata source only through the dev command's dormant read-only
  RegionManager comparison, outside Player and all gameplay consumers.

Implemented:

- additive `layered-map-parity-event-v6` JSONL retaining every v5 field and
  requiring a new nullable `tileParity` field;
- immutable observer metadata with checked logical location, nullable legacy
  packed address, representability, packed-source presence, missing-source,
  comparability, and exact full-state parity;
- location-consistency refusal before a sampled record is written, plus
  metadata consistency guards preventing unsupported sources, contradictory
  missing-source state, or exact uncomparable results; and
- dev-only source wiring from `RegionManager.compareLayeredTileState(...)`,
  sampled only at start, marker, teleport, and stop.

Focused findings:

- the compiled eight-event observer trace emits parity on exactly four primary
  events—start, teleport, marker, and stop—and null on move, snapshot, logout,
  and login;
- the emitted fixture metadata identifies logical `(100,943,0)`, legacy packed
  `(100,943)`, a present source, comparability, and exact state parity;
- a second trace remains identity-isolated while the bounded source records
  exactly its own start and stop; and
- all v1-v6 schemas remain individually valid, with v6 structurally preserving
  every required v5 field.

Safety boundary:

- the private capability remains disabled by default and is reachable only
  through the existing dev/admin command gate;
- ordinary movement keeps its established v5 snapshot evidence but does not
  execute the added current-tile comparison;
- Player contains no parity metadata or comparison state, and packed Regions
  remain the sole gameplay/collision authority; and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-twenty-nine.py` — 2 tests
  passed;
- the compiled observer fixture and all v1-v6 schema-lineage checks pass;
- Slice 1 through Slice 29 regressions all pass (72 tests);
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 739 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `5011f5976cfca506e0b7845d7fa3b10715e0c88527a38dc33855dcf5aa098fdf`,
  inventory
  `528dc5d33a4614ef4a6fa759c27c8c44f3db73ee9378d9e19d5327786da9d995`,
  classification
  `8f7fff29ee0b55d7350a11558fc6a84c364f99dfcad51e5af8782a04d5975a42`,
  and occurrence
  `b2089304574a5e572e7c1b28a1df141a1657a4c12cd28cd92ad03702ca2cc2d3`.

Owner runtime acceptance completed on 2026-07-19 from checkpoint `68cc4e6df`:

- the private route produced 124 consecutive schema-valid v6 events from start
  through stop: 110 moves, five teleports, six markers, one explicit snapshot,
  one start, and one stop;
- all events retained exact coordinate round trips, exact packed/logical
  visibility coverage with no missing, extra, or unsupported keys, and complete
  2,304-tile logical snapshots with no missing source Region;
- exactly 13 bounded events carried current-tile parity—six markers, five
  teleports, start, and stop—while every move and the explicit snapshot carried
  null as designed;
- all 13 comparisons were legacy-representable, source-present, comparable,
  and exact, and every logical and packed address matched the event's existing
  `to` snapshot;
- the requested markers proved surface level `0`, upper floor `+1`, returned
  surface `0`, underground `-1`, returned surface `0`, and the final surface
  teleport at level `0`;
- `underground-return` intentionally denotes the state after returning from
  underground; its trace correctly records logical `(274,565,0)` and packed
  `(274,565)`, while `underground` records logical `(274,565,-1)` and packed
  `(274,3397)`;
- five distinct logical regions each retained one stable fingerprint throughout
  the route, including restoration of surface region `(0,5,11)` fingerprint
  `4500089f49cb1f5e8560df5be0d249497bf06bb06778c5a1719f34f2980c7f6e`
  after leaving underground; and
- the owner reported no visual, loading, collision, or interaction issue beyond
  confirming the intended return-marker naming.

Slice 29 is owner-validated. Packed Regions and collision remain authoritative.

### Slice 30: Checked logical tile-neighborhood parity

Objective: expand the proven single-tile comparison into the smallest tile
neighborhood useful for later adjacent-step collision and pathing work, without
adopting it for either behavior.

Selected boundary:

- retain exactly nine row-major current-tile comparisons at offsets `-1..+1`
  around one exact world-space/level-qualified center;
- validate cell ordering and location identity, and summarize representable,
  unsupported, source-present, missing-source, comparable, and exact counts;
- reuse each detached logical 48×48 snapshot within one neighborhood call so a
  region corner captures at most four logical snapshots rather than nine, with
  no persistent cache; and
- compare direct packed sources only through the established non-creating
  Region peek while keeping Player, movement, collision, pathing, diagnostics,
  packets, and persistence outside the dormant contract.

Implemented:

- immutable `LayeredTileNeighborhoodParityComparison` retention of one exact
  center and nine ordered immutable current-tile comparisons;
- checked offset/location ordering, bounded cell access, immutable cell-list
  exposure, and explicit representable, unsupported, source-present,
  missing-source, comparable, and exact counts;
- read-only RegionManager entry points for packed `Point` and explicit
  `WorldLocation`, with logical snapshot reuse scoped to one call; and
- reuse of the established checked single-tile comparison and non-creating
  packed Region peek rather than adding another tile interpretation.

Focused findings:

- center `(239,16,+1)` spans logical X regions `4..5` and four packed source
  Regions by crossing packed X and packed Y boundaries; all nine immutable
  states compare exactly;
- removing packed Region `(4,20)` leaves all nine cells legacy-representable
  while explicitly reducing source-present/comparable counts and increasing
  missing-source count, without reporting complete parity;
- the same neighborhood at level `-2` preserves nine explicit unsupported
  cells with no packed source, missing-source, comparability, or false exact
  result; and
- wrong cell order, wrong cell count, out-of-range offsets, and mutation of the
  returned cell list are refused.

Safety boundary:

- each logical snapshot is detached and reused only inside one comparison call;
  there is no persistent cache or dual-write state;
- neighborhood lookup never calls the creating Region lookup or a mutable-tile
  method;
- packed Regions and mutable `TileValue`s remain terrain, collision, pathing,
  visibility, packet, entity, and persistence authority; and
- no Player, movement, collision, pathing, diagnostic, database, map,
  placement, archive, client, Builder project, public server, or live data is
  changed.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty.py` — 2 tests passed;
- Slice 1 through Slice 30 regressions all pass (74 tests), including exact
  coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 740 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content and 211 unresolved owners. The expected fingerprints are source
  `ddd878197077ef6fb7732890534a17de96c28a2810bcbcd1f2567d34953aa4f6`,
  inventory
  `6ba68a10a10229ce1083a916fb4c7d33ccfbd708d1264a767e61213eba0b096f`,
  classification
  `f702226cfaefdc4ae3e9d69bb1a9f83c2f41ce3ba4ed6471f5c586b326abdd5f`,
  and occurrence
  `b2089304574a5e572e7c1b28a1df141a1657a4c12cd28cd92ad03702ca2cc2d3`.

No owner runtime route is required because the neighborhood remains dormant.
Any private diagnostic adoption or actual collision/pathing consumer remains a
separately gated slice.

### Slice 31: Bounded private tile-neighborhood diagnostics

Objective: make the dormant Slice 30 neighborhood comparison AI-readable in
the opt-in private parity trace without adding tile payloads or running the
comparison on every movement event.

Selected boundary:

- advance new traces to additive v7 JSONL while retaining every v6 field and
  keeping the v1-v6 schemas beside it;
- emit only the logical center, fixed cell count, representable/unsupported
  counts, source-present/missing counts, comparable/exact counts, and
  complete/exact status;
- sample the summary on the same observer `start`, `marker`, `teleport`, and
  `stop` events as current-tile parity, with an explicit JSON null on movement,
  snapshot, login, and logout; and
- bind the source only through the existing dev command and read-only
  `RegionManager.compareLayeredTileNeighborhood(...)` projection.

Implemented:

- additive `layered-map-parity-event-v7` JSONL retaining every v6 field and
  requiring a nullable `tileNeighborhood` summary;
- immutable observer metadata with checked nine-cell count relationships,
  center identity, completeness, and exactness;
- fail-closed sampled-event capture that refuses a summary whose center differs
  from the event's current layered location; and
- dev-only RegionManager wiring with no Player field, tile payload, cache,
  gameplay consumer, or persistence state.

Safety boundary:

- the capability remains disabled by default and reachable only through the
  existing dev/admin command gate;
- movement, snapshot, login, and logout records do not execute either tile
  comparison;
- packed Regions and mutable `TileValue`s remain terrain, collision, pathing,
  visibility, packet, entity, and persistence authority; and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Focused findings:

- the compiled eight-event observer trace emits the neighborhood on exactly
  four event types—start, teleport, marker, and stop—and emits null on move,
  snapshot, logout, and login;
- the exact fixture emits nine representable, present, comparable, and exact
  cells around logical center `(100,943,0)`, with complete/exact true;
- neighborhood capture count exactly matches the existing bounded current-tile
  capture count, including identity-isolated traces; and
- inconsistent source counts, comparability, completeness, exactness, and
  event-center identity are refused before a record is accepted.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-one.py` — 2 tests
  passed;
- the compiled observer fixture and all v1-v7 schema-lineage checks pass;
- Slice 1 through Slice 31 regressions all pass (76 tests), including exact
  coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 740 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `302cf9a28a9128890bae0e6d16ce9a0bc87453c58b12df29c81ec3bfda56a1d0`,
  inventory
  `f2fb8bf8aaa5fc284cb3ac7918d5f397216dd4c740892303bc2adfbbdfcea23b`,
  classification
  `5a44e4fd10d90ec7181919f6bf2908643c8af8f3ae270147c3b22d0e614e79e0`,
  and occurrence
  `8f40b8d89ff28ded57c4ac6495ef50839b77fe01975a2855e1f8d94e5a1cc185`.

Owner runtime acceptance completed on 2026-07-19 from checkpoint
`0d43cbd4107cd65ab72cdd3710a648493939e10b`:

- the private route produced 73 consecutive schema-valid v7 events from start
  through stop: 40 moves, 23 teleports, eight markers, one start, and one stop;
- all 33 eligible start/marker/teleport/stop records carried a neighborhood,
  while every move carried null, exactly matching the bounded sampling policy;
- markers on the two sides of the 48-tile Y boundary—logical `(274,623,0)` at
  region/local `(5,12)/(34,47)` and `(274,624,0)` at
  `(5,13)/(34,0)`—each compared all nine cells exactly;
- upper-floor `(276,625,+1)`, returned surface `(276,625,0)`, underground
  `(274,565,-1)`, and returned surface `(274,565,0)` markers each compared all
  nine cells exactly;
- the distant route ended at logical `(110,510,0)` after 23 observed teleports
  with a complete exact neighborhood and exact packed/layered round trip;
- one intermediate teleport to logical origin `(0,0,0)` correctly reported
  four representable/present/comparable/exact cells and five unsupported
  negative-coordinate neighbors, rather than misclassifying them as missing
  sources or falsely claiming complete parity; and
- the owner reported no visual, loading, collision, or interaction issues.

Slice 31 is owner-validated. Packed Regions and collision remain authoritative.

### Slice 32: Dormant adjacent-step collision projection

Objective: use the proven immutable 3×3 neighborhood to compare one
orthogonal or diagonal tile-mask decision between logical snapshot states and
their current direct packed states, without moving a Player or changing
pathfinding.

Selected boundary:

- accept exactly one of the eight offsets in `-1..+1`, rejecting `(0,0)` and
  non-adjacent coordinates;
- mirror the tile-mask portion of the current adjacent walking rules, including
  current/side/destination walls, full blocks, diagonal walls, and the legacy
  diagonal pass-through lookups;
- preserve the current northwest pass-through auxiliary lookup at offset
  `(+1,+1)` as parity behavior rather than silently correcting it during the
  coordinate migration;
- report logical and packed decision availability, nullable passability,
  blocking reason, required cell count, exact required-state count,
  passability parity, and blocking-reason parity; and
- explicitly exclude Player/NPC occupancy, NPC-specific scenery enumeration,
  projectile checks, path selection, movement acceptance, and all authoritative
  `PathValidation` consumers.

Implemented:

- immutable `LayeredAdjacentStepCollisionComparison` with checked source,
  destination, direction, fixed required-cell selection, and stable blocking
  reasons;
- independent logical-snapshot and direct-packed evaluations over full current
  traversal masks, which already include terrain and dynamic scenery collision;
- explicit unavailable results for unsupported logical cells or absent packed
  source Regions instead of substituting a false pass/fail decision; and
- read-only RegionManager entry points for packed `Point` and explicit
  `WorldLocation`, built only from the dormant Slice 30 neighborhood, plus an
  immutable eight-direction batch that reuses one detached neighborhood.

Safety boundary:

- the new comparison has no Player, Mob, `PathValidation`, packet, script,
  movement, pathfinding, or collision consumer;
- it does not enumerate occupants and cannot authorize or reject an actual
  movement step;
- it uses non-creating packed Region lookups inherited through the detached
  neighborhood comparison; and
- no database, map, placement, archive, client, Builder project, public server,
  or live data is changed.

Focused findings:

- all eight directions remain passable with exact logical/packed decisions and
  reasons over an open synthetic 3×3 neighborhood;
- cardinal steps require two fixed cells, ordinary diagonals require four, and
  the legacy northwest direction explicitly retains its fifth auxiliary
  `(+1,+1)` lookup;
- matching current-wall and destination-full-block fixtures produce stable
  blocked reasons, while a changed packed traversal mask is detected as a
  passability mismatch;
- changing only elevation leaves passability/reason parity exact while still
  exposing that the required full tile states differ;
- an absent packed Region keeps the logical decision available but makes the
  packed decision explicitly unavailable, while level `-2` makes both sides
  unavailable under the legacy adapter; and
- the immutable eight-direction RegionManager batch reuses exactly one detached
  neighborhood rather than assembling it eight times.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-two.py` — 2 tests
  passed;
- Slice 1 through Slice 32 regressions all pass (78 tests), including updated
  exact coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 741 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `3dd278a67388dc5fe7d75ced03a3782fc980d16b5877f46e7a2cb64a0be56b22`,
  inventory
  `cf0d4b69282a1eabef26bfa3c61227cebce73d558f3e6554aa4c873442b351dd`,
  classification
  `1ee922292a5b3de8b16428e4e02a58b988d3b4e8ed4673c186492675cbc6cc74`,
  and occurrence
  `8f40b8d89ff28ded57c4ac6495ef50839b77fe01975a2855e1f8d94e5a1cc185`.

No owner runtime route is required because the comparison remains dormant. A
separately versioned private diagnostic adoption is the next owner-testable
boundary.

### Slice 33: Bounded private adjacent-collision diagnostics

Objective: expose the dormant eight-direction Slice 32 comparison in the
opt-in private parity trace so an owner can correlate visible walls, scenery,
region boundaries, and vertical areas with stable AI-readable tile-mask
decisions.

Selected boundary:

- advance new traces to additive v8 JSONL while retaining every v7 field and
  keeping the v1-v7 schemas beside it;
- sample all eight directions only on observer `start`, `marker`, `teleport`,
  and `stop`, with explicit null on movement, snapshot, login, and logout;
- emit center, direction/destination identity, required and exact state counts,
  nullable logical/packed passability and blocking reasons, comparability, and
  exactness summaries without traversal masks or tile payloads;
- reuse exactly one detached 3×3 neighborhood for the eight directions in one
  event; and
- keep actual movement acceptance, occupancy, path selection, and
  `PathValidation` outside the diagnostic contract.

Implemented:

- additive `layered-map-parity-event-v8` JSONL retaining every v7 field and
  requiring nullable `adjacentCollision` evidence;
- immutable observer metadata with exactly eight row-major directions, checked
  destinations, fixed 2/4/5 required-cell counts, consistent nullable
  decision/reason pairs, and derived aggregate counts;
- dev-only source wiring through the RegionManager eight-direction batch; and
- location-consistency refusal before a sampled record is written.

Safety boundary:

- the private capability remains disabled by default and reachable only
  through the existing dev/admin command gate;
- movement, snapshot, login, and logout records do not run the adjacent
  comparisons;
- no traversal mask or tile payload is serialized, and no Player/Mob stores
  diagnostic collision state; and
- no movement, `PathValidation`, database, map, placement, archive, client,
  Builder project, public server, or live data is changed.

Focused findings:

- the compiled eight-event observer trace emits adjacent collision evidence on
  exactly start, teleport, marker, and stop, with null on move, snapshot,
  logout, and login;
- the open fixture emits eight row-major directions with logical/packed
  decisions available, passable, reason `NONE`, and exact passability, reason,
  and required-state parity;
- the bounded adjacent source is captured exactly as often as current-tile and
  neighborhood parity, including the identity-isolated trace;
- inconsistent passability/reason pairs are refused, and parent metadata checks
  direction count, order, and destination identity; and
- the Draft 2020-12 v8 schema fixes eight directions and retains the complete
  v7 required-field contract.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-three.py` — 2 tests
  passed;
- the compiled observer fixture and all v1-v8 schema-lineage checks pass;
- Slice 1 through Slice 33 regressions all pass (80 tests), including exact
  coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 741 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `6a97b31445a2a989f2b2245d477a28c56ac8020dc8586bfcb026edab533d5382`,
  inventory
  `6b08310027eca8187cd23ec112379b94cf36b79a9d41f30275f8a162cdeb1aab`,
  classification
  `8ea94f361a844826010a8b85340fdf2490d1182e187ea7b8b59e85809cd881ee`,
  and occurrence
  `c706847eadc500b3db75554384e15a9a5624298625500c47493db162494b6af2`.

Owner runtime validation evidence:

- the owner completed the private route with no visible collision or movement
  issues;
- the doorway marker was captured before the wall marker because the wall step
  was repeated after a command syntax error; marker identity made the order
  irrelevant;
- the initial `start` sample provided the requested ordinary open-ground
  evidence with zero blocked directions, the doorway sample likewise had zero
  blocked directions, and the wall-side sample had three;
- every sampled direction at the wall, doorway, 48-tile region edge, upper
  floor, underground level, and both surface returns was comparable with exact
  passability, blocking reason, and required-state parity; and
- all sampled locations retained exact signed-coordinate round trips. Slice 33
  is owner-validated.

### Slice 34: Dormant bounded traversal collision projection

Objective: compose the validated adjacent-step comparison across one explicit,
already expanded route so future layered traversal can be evaluated without
asking the projection to choose or execute a path.

Selected boundary:

- accept 1-50 adjacent, distinct steps on one world-space and signed level;
- preserve every per-step logical/packed decision and aggregate availability,
  comparability, passability, blocking-reason, and required-state parity;
- report the first known logical block, packed block, passability mismatch, and
  blocking-reason mismatch as zero-based step indices;
- make whole-route passability nullable unless every step is available in that
  representation; and
- leave route expansion, zigzag selection, compressed high-speed waypoints,
  occupancy, NPC-specific scenery checks, projectile rules, and movement
  execution outside this projection.

Implemented:

- immutable `LayeredTraversalCollisionComparison` with a 50-step allocation
  bound, continuity checks, stable aggregate counts, nullable route decisions,
  and an immutable ordered step list; and
- a read-only RegionManager entry point accepting signed `WorldLocation`
  routes and refusing duplicate, non-adjacent, level-changing, or world-space-
  changing steps before composing the Slice 32 primitive.

Safety boundary:

- `Path`, `PathValidation`, `WalkingQueue`, Player, Mob, and A* do not import or
  consume the new value;
- the comparison does not inspect or mutate occupancy, select a route, enqueue
  movement, create Regions, cache snapshots, or write map/database state; and
- Packed Regions and all legacy movement decisions remain authoritative.

Focused findings:

- an all-open route and a route containing a shared logical/packed block retain
  exact whole-route decisions and stable first-block indices;
- a synthetic logical/packed mismatch identifies the first passability and
  reason mismatch while preserving full step-level evidence;
- an unavailable packed step keeps logical route passability but makes packed
  and whole-route comparability explicitly unavailable; and
- empty, oversized, discontinuous, duplicate, non-adjacent, level-changing,
  world-space-changing, and null routes are refused at their owning boundary.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-four.py` — 2 tests
  passed;
- Slice 1 through Slice 34 regressions all pass (82 tests), including updated
  exact coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 742 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `ea2308ecca0e858924ed8ed5159e33071673575cf349f2f33a36437f136a4bea`,
  inventory
  `9a1c393aed09ee7e8790f1353c78619bc59a8ecceab40e33e1e19b96cd7b67a3`,
  classification
  `fe9ccb21ac8780b54db314cd3c70d6ba8e500ac7e64052dec2bdbc401d3fa4e4`,
  and occurrence
  `c706847eadc500b3db75554384e15a9a5624298625500c47493db162494b6af2`.

No owner runtime route is required because the projection remains dormant. A
separately versioned private diagnostic adoption is the next owner-testable
boundary.

### Slice 35: Bounded private recent-traversal diagnostics

Objective: correlate real private-server walking segments with the dormant
Slice 34 comparison without retaining an authoritative path or changing any
movement decision.

Selected boundary:

- advance new traces to additive v9 JSONL while retaining every v8 field and
  keeping the v1-v8 schemas beside it;
- retain only the latest 16 contiguous ordinary one-tile movement steps since
  start, teleport, login, or the previous marker;
- emit route evidence only on `marker` and `stop`, with explicit null on every
  other event and when no ordinary step was retained;
- report capacity evictions and observed non-adjacent discontinuities rather
  than silently treating them as comparable steps; and
- serialize bounded source/destination identities, decisions, reasons, counts,
  exactness, and first noteworthy indices without traversal masks or tile
  payloads.

Implemented:

- additive `layered-map-parity-event-v9` JSONL with required nullable
  `recentTraversal` evidence;
- synchronized per-trace recent-route bookkeeping with a fixed 16-step cap,
  explicit dropped/discontinuity counters, and reset boundaries;
- immutable observer metadata that validates step indices, adjacency,
  continuity, decision/reason consistency, and aggregate route semantics; and
- dev-only source wiring through the dormant RegionManager traversal
  comparison.

Safety boundary:

- the observer remains disabled by default and behind the existing dev/admin
  command capability;
- the recent route is observer-local, bounded, discarded when the trace ends,
  and never stored on Player, Mob, Path, WalkingQueue, or RegionManager;
- teleports and level changes reset rather than fabricate walking steps; and
- no movement, route selection, `PathValidation`, database, map, placement,
  archive, client, Builder project, public server, or live data is changed.

Focused findings:

- a real compiled observer trace retained one ordinary same-level move after a
  teleport and emitted one exact marker-time route comparison;
- an 18-step synthetic walk retained the latest 16 steps, reported two
  capacity evictions, and preserved continuity and exact decisions;
- a non-adjacent ordinary location jump was refused as a traversal step,
  restarted the retained segment, and surfaced one discontinuity on the next
  marker;
- start, teleport, login, and successful markers reset observer-local route
  state, while move records never run the expensive traversal comparison; and
- the Draft 2020-12 v9 schema retains every v8 field, caps step arrays at 16,
  and requires null traversal evidence outside marker/stop events.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-five.py` — 2 tests
  passed;
- the compiled observer fixture and all v1-v9 schema-lineage checks pass;
- Slice 1 through Slice 35 regressions all pass (84 tests), including exact
  coordinate-package consumer allowlists;
- World Builder discovery passed 13 tests and the standalone-layout guard
  passed;
- the authoritative bundled-Ant build succeeds for 742 core and 488 plugin
  sources; and
- two real-repository normalizations were byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `6da32afdaad7090a00bcb1075eea52b0bc61d15f176c9282d419768f92081e75`,
  inventory
  `22d1245bba16406e70539341ae446a486aca4ab6b33ca7e7b71f226b509b605d`,
  classification
  `efd29d92296e29dabe53a9cec2ee5a7a793f4f9567e9119d358524b311b96f29`,
  and occurrence
  `3a1611dd5a89feff83cc88e85123f749485c50ab072510bea6b85a37bd83824e`.

Owner runtime validation evidence:

- the owner completed the full private route with no visible movement or
  collision issues;
- `long-open-route` retained 16 exact steps and reported 43 capacity evictions,
  while `obstacle-route` retained 16 exact steps and reported 13;
- the ordinary walked boundary step from `(274,623,0)` to `(274,624,0)` was
  retained as one exact, fully comparable step after the preceding teleport
  reset;
- `underground-walk` retained 16 exact level `-1` steps with 16 evictions, and
  `surface-after-return` retained 16 exact level `0` steps with 52 evictions;
- every teleport and ladder transition emitted null recent-route evidence and
  reset the segment as designed; and
- all five route markers reported zero discontinuities, no blocked-step or
  mismatch indices, exact signed-coordinate round trips, and complete logical/
  packed passability, reason, and required-state parity. Slice 35 is owner-
  validated.

### Slice 36: Checked logical Region residency mirror

Objective: establish the smallest synchronized logical Region lifecycle view
needed by future streaming work without caching mutable tile/collision payloads
or replacing current packed Region lookup.

Authoritative mutation and lifecycle audit:

- `RegionManager.getRegionFromSectorCoordinates(...)` is the only constructor
  path for runtime `Region` objects, and `RegionManager.unload()` is the only
  removal path; the one external `getRegions()` consumer only enumerates
  scenery for hourly reset;
- terrain initialization mutates tile fields and collision state through
  `WorldLoader`, while runtime scenery, boundary, and projectile changes flow
  through `World.getMutableTile(...)`; the in-game terrain editor additionally
  changes elevation, overlay, wall identity, terrain collision, and projectile
  state;
- uniform-region compression and later mutable expansion change tile storage
  representation without changing Region residency; and
- because `TileValue` remains deliberately mutable and some loader/editor
  fields are public, a persistent logical tile cache would need a separately
  approved comprehensive mutation/invalidation boundary. This slice therefore
  retains no `TileValue`, collision mask, entity, or visibility payload.

Implemented:

- a synchronized `LayeredRegionResidencyMirror` that indexes each packed Region
  lifecycle claim by every supported `WorldRegionKey` it overlaps;
- immutable per-key snapshots with explicit mirror version, ordered packed
  source contributions, target/supported/resident tile counts, missing-source
  counts, legacy completeness, and residency state;
- support for idempotent registration/removal, packed-only out-of-codec Region
  cells, split packed/logical boundaries, partial terminal logical regions,
  unsupported future levels/world spaces, and full unload clearing; and
- RegionManager lifecycle wiring under one lock plus a checked read-only query
  that compares every reported source with the authoritative packed Region map.

Safety boundary:

- `getTile(...)`, `getMutableTile(...)`, `PathValidation`, movement, collision,
  visibility caches, and entity membership continue to use packed Regions;
- residency queries never create a Region and the mirror never stores or reads
  tile, collision, scenery, player, NPC, item, or packet state;
- the mirror version changes only for actual Region lifecycle changes, making
  snapshot freshness explicit rather than implied; and
- per-Region removal is modeled for future streaming but is not adopted by the
  current eager world lifecycle.

Focused findings:

- logical upper-floor Region `(1,4,0)` correctly requires fragments from packed
  Regions `(4,19)` and `(4,20)`, exposes partial residency after the first is
  present, and becomes fully resident after the second;
- terminal underground Region `(-1,682,19)` distinguishes its 1,024 supported
  and resident tiles from its incomplete 2,304-tile logical target;
- level `-2` retains zero fabricated legacy sources and cannot report resident;
  and
- duplicate lifecycle notifications are no-ops, individual removal updates the
  affected logical claims, and full clear leaves no stale packed or logical
  entries.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-six.py` — 2 tests pass
  for the lifecycle mirror and its non-authoritative RegionManager boundary;
- Slice 1 through Slice 36 regressions all pass (86 tests), including the
  updated exact staged-contract inventories;
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 743 core and 488 plugin
  sources;
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `4ee7fb0260039d4ecdd9fe4c4356995a35accd0a6bea264c94d93c9498577c5c`,
  inventory
  `5d330294de99d5516836157a33c0f203a9cda5b2749d9b6b8b35bbd79d575964`,
  classification
  `5dfc35786421ced830aea0da2b20c58ad75541bb4a7cda718a7f9a58e4116f79`,
  and occurrence
  `3a1611dd5a89feff83cc88e85123f749485c50ab072510bea6b85a37bd83824e`.

No owner runtime route is required because the mirror remains dormant and has
no visual, movement, collision, packet, or persistence consumer.

### Slice 37: Dormant interest/residency projection

Objective: combine the allocation-bounded logical interest delta from Slice 17
with the lifecycle-only residency snapshots from Slice 36 so future streaming
requests can be studied without performing any load, retention, release, or
eviction.

Selected boundary:

- accept two caller-bounded logical interest windows and preserve Slice 17's
  deterministic entered, retained, and exited order;
- capture every involved residency snapshot under one Region lifecycle lock and
  require one explicit mirror version across the complete comparison;
- classify current keys as resident, partial, missing, or unsupported, exposing
  only missing/partial current keys as legacy load candidates;
- classify exited keys with one or more resident packed sources as release
  candidates, not unload instructions; and
- keep unsupported current keys separate because the legacy packed adapter has
  no valid source it could request.

Implemented:

- immutable `LayeredRegionInterestResidencyComparison` with per-key interest
  and residency states, shared freshness version, aggregate counts, immutable
  load/release/unsupported views, and strict identity/order validation; and
- a read-only RegionManager projection that builds the existing bounded
  `WorldRegionInterestDelta`, checks every Slice 36 snapshot against packed
  storage, and composes the comparison without creating a Region.

Safety boundary:

- load candidates are evidence that required legacy sources are missing or
  partial; no loader, archive, tile array, entity, cache, or packet consumes
  them;
- release candidates cannot unload a Region. Actual eviction remains blocked
  on a future global ownership/reference policy because one Player leaving a
  window cannot prove that no other Player or subsystem needs it;
- current eager world loading, packed Region maps, visibility caches,
  `PathValidation`, movement, collision, and Player state remain unchanged; and
- mixed-version, reordered, missing, duplicate, or null snapshot evidence is
  refused rather than normalized silently.

Focused findings:

- a two-key upper-floor window shift retains a fully resident key, identifies a
  partially resident entered key as a load candidate, and identifies a fully
  resident exited key as release evidence;
- removing the entered key's sole present packed source changes the same
  projection from partial to missing without changing interest identity;
- a transition into level `-2` classifies the entered key as unsupported rather
  than fabricating a legacy load candidate, while preserving the old level's
  release evidence; and
- the comparison does not change the residency mirror version and rejects
  snapshots captured across different lifecycle versions.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-seven.py` — 2 tests pass
  for deterministic classifications, freshness checks, and the dormant
  RegionManager boundary;
- Slice 1 through Slice 37 regressions all pass (88 tests), including updated
  exact staged-contract inventories;
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 744 core and 488 plugin
  sources; and
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `55c641d13b46a576783ea82655c753d3bf87b6e0fe7025ecd0eecc3089e5a92d`,
  inventory
  `2e4fa2dd3111eabd551ab2ddb1c10a2de98ce493b0700efdfde0fba2dfdce7b6`,
  classification
  `d60001c2f87adbe44af40cad3b01fef0a35c6c6f4cc27ecbde2f2084fbe5e186`,
  and occurrence
  `3a1611dd5a89feff83cc88e85123f749485c50ab072510bea6b85a37bd83824e`.

No owner runtime route is required because this projection remains dormant. A
bounded private diagnostic adoption is the next owner-testable boundary.

### Slice 38: Private Region residency diagnostics

Objective: expose Slice 37's bounded, versioned Region residency evidence in
the opt-in private JSONL trace so real world-window crossings, teleports, level
changes, and session events can be reviewed without adopting streaming.

Selected boundary:

- advance new traces to additive `layered-map-parity-event-v10` while retaining
  every required v9 field and keeping the v1-v9 schemas alongside it;
- capture one same-window baseline on start, snapshot, marker, login, logout,
  and stop, plus transition comparisons on teleports and only those ordinary
  moves whose logical interest delta is not a no-op;
- serialize aggregate entered/retained/exited and current residency counts,
  mirror freshness, and bounded detailed arrays only for missing/partial load
  candidates, resident exit release candidates, and unsupported current keys;
  and
- omit tile/collision payloads and emit explicit null on ordinary moves that do
  not change the logical interest window.

Implemented:

- v10 Draft 2020-12 JSON schema with bounded Region residency aggregates and
  stable per-candidate logical key, interest state, residency state, source
  counts, tile counts, and legacy-completeness fields;
- observer-owned immutable metadata with checked aggregate arithmetic,
  candidate-state semantics, unique keys, and exact comparison to the observed
  interest delta;
- event gating that avoids rebuilding the full logical-window residency view
  on every ordinary tile step; and
- dev-only source wiring from RegionManager's dormant Slice 37 comparison.

Safety boundary:

- the existing private/admin capability gate and default-disabled server
  configuration remain unchanged;
- no Region, tile, collision, entity, Player, path, cache, archive, packet,
  database, or map state is created or mutated by capture;
- `loadCandidates` and `releaseCandidates` are explicit diagnostic names, not
  commands, queues, or eviction authorization; and
- public/live deployment remains out of scope.

Automated validation evidence:

- `python3 tests/myworld/test-layered-maps-slice-thirty-eight.py` — 2 tests pass
  for v9/v10 schema lineage, bounded fields, capture gating, and the
  non-authoritative runtime boundary;
- the compiled observer fixture validates v10 events and metadata across start,
  logical-window crossing, level-changing move, teleport, ordinary move,
  marker, snapshot, logout, login, stop, an 18-step bounded route, and a
  discontinuous route;
- Slice 1 through Slice 38 regressions all pass (90 tests), including updated
  staged-boundary and schema-version guards;
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 744 core and 488 plugin
  sources; and
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `57a70382c188ee892805de46eddddf99ec2f15c728863d4c07f5851503547c94`,
  inventory
  `25f5347510e95372983db36dd0afa98af63befe6fb86c1a15516bcbc1b8397ac`,
  classification
  `d7df5cd3ac229c88ce00a0a94e78bab012c212d97a5ca10cb0c87ce88823a9cf`,
  and occurrence
  `cdfe9069333827eca2f52701e321cb7b6234246d07979599311d9e8922c71930`.

Owner runtime validation evidence (2026-07-19):

- the owner completed ordinary walking, logical-window changes, teleports,
  surface `0` to underground `-1` and return transitions, logout/reconnect,
  markers, and clean stop with no visual or gameplay issues;
- all 48 captured events validate against the v10 schema with a continuous
  start-through-stop sequence, and residency capture occurred on all non-move
  events and changed-window moves while same-window ordinary moves correctly
  emitted null residency metadata;
- the mirror remained stable at version `1842`; every current logical window
  reported 36-42 resident regions with zero partial, missing, unsupported, or
  load-candidate regions;
- 90 release candidates were reported across exited rows and both full
  36-region level changes, exactly as dormant evidence; no loading, eviction,
  or authority change occurred; and
- packed coverage, tile snapshots, current-tile and neighborhood parity,
  adjacent collision, recent traversal, and coordinate round trips reported
  zero failures.

### Slice 39: Global logical-interest ownership model

Objective: define the global ownership rule needed to distinguish one owner's
local exit from a Region that is no longer referenced by any active interest
source, without adopting the model in runtime gameplay.

Selected boundary:

- issue monotonic, process-local owner tokens rather than using database IDs,
  username hashes, or reusable Player indexes;
- let each open owner hold at most one complete level/world-space-qualified
  logical window and replace that window atomically;
- count references per `WorldRegionKey`, preserving deterministic entered,
  retained, and exited ordering with exact before/after counts;
- classify `0 -> 1` entries as global acquisitions, entries above one as
  shared acquisitions, `1 -> 0` exits as global releases, and exits that remain
  above zero as shared releases; and
- make repeated close safe and non-decrementing while opaque, ledger-bound
  handles reject cross-ledger use and closed-token reuse.

Implemented:

- `LayeredRegionInterestOwnershipLedger`, with checked opaque owner allocation,
  bounded window materialization, atomic reference-count replacement, explicit
  close/clear lifecycle, immutable changes, and versioned per-key snapshots;
- world-space and signed-level isolation through the existing immutable
  `WorldRegionKey`; and
- exact global-release evidence that can later gate residency comparison but
  cannot itself perform loading, retention, release, or eviction.

Safety boundary:

- the ledger is not yet held by RegionManager, Player, World, diagnostics,
  packets, persistence, pathing, collision, terrain, or caches;
- no owner token exists outside the focused fixture, so current eager packed
  Region behavior is byte-for-byte unchanged; and
- even a global release is only a reference-count fact. Future eviction must
  separately prove residency, lifecycle freshness, other owner classes,
  cooldown/pinning policy, and safe authority adoption.

Automated validation evidence:

- the compiled Slice 39 fixture proves two overlapping owners, shared entries,
  globally final exits, shared exits that cannot release residency, same-window
  no-ops, duplicate close, closed/cross-ledger-token refusal,
  allocation-budget refusal, world-space/level isolation, immutable results,
  and unload clear;
- Slice 1 through Slice 39 regressions all pass (92 tests), including the
  updated exact staged-contract inventory;
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 745 core and 488 plugin
  sources;
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `57a70382c188ee892805de46eddddf99ec2f15c728863d4c07f5851503547c94`,
  inventory
  `25f5347510e95372983db36dd0afa98af63befe6fb86c1a15516bcbc1b8397ac`,
  classification
  `d7df5cd3ac229c88ce00a0a94e78bab012c212d97a5ca10cb0c87ce88823a9cf`,
  and occurrence
  `cdfe9069333827eca2f52701e321cb7b6234246d07979599311d9e8922c71930`;
- the focused structural guard proves the ledger remains absent from
  RegionManager, Player, and `PathValidation`; and
- no owner runtime route is required because the ownership model remains
  completely dormant.

The next slice may add a checked RegionManager-owned shadow and explicit Player
session handles while leaving all reference results non-authoritative. Runtime
diagnostics and any loading/eviction experiment remain later gates.

### Slice 40: Checked Player-session interest ownership shadow

Objective: exercise Slice 39's ownership semantics across real Player session
and movement lifecycle boundaries while keeping every result observational.

Selected boundary:

- RegionManager owns exactly one process-local ledger and serializes ownership
  with its existing layered Region lifecycle lock;
- each Player object opens one opaque handle after successful login state,
  synchronizes it only when its checked logical visibility window changes, and
  closes it during final `setLoggedIn(false)` cleanup;
- initial placement and pre-login benchmark placement continue to initialize
  the existing location/window mirrors without acquiring global interest;
- repeated login-state or disconnect cleanup remains idempotent, and a world
  unload invalidates all outstanding handles while clearing all counts; and
- checked owner snapshots must match the Player's current visibility mirror
  and opaque handle sequence.

Implemented:

- RegionManager open, synchronize, close, owner-snapshot, and per-key snapshot
  methods with a 4,096-key per-owner allocation budget;
- Player-owned lock, opaque handle, last-owned window, boundary-only
  synchronization, read-only checked owner snapshot, and final close path; and
- ledger-wide clear inside the existing RegionManager unload boundary.

Safety boundary:

- `getRegion(...)`, `getTile(...)`, current visibility caches, entity Regions,
  movement, `PathValidation`, terrain, collision, packets, persistence, client
  loading, and eager world loading remain authoritative and unchanged;
- global acquisitions/releases and shared reference counts are not consumed by
  Region construction, unloading, residency, or any gameplay decision;
- ownership is absent from the parity observer in this slice, so no schema or
  diagnostic behavior changes; and
- ordinary movement inside an unchanged logical window performs no key
  materialization or ledger mutation.

Automated validation evidence:

- the focused Slice 40 guards verify the single manager-owned ledger, shared
  lifecycle lock, unload clear, bounded API, Player handle/window checks,
  boundary-only synchronization, login open, final logout close, and absence
  from pathing and diagnostics;
- Slice 1 through Slice 40 regressions all pass (94 tests), including the
  updated Slice 39 consumer boundary;
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 745 core and 488 plugin
  sources;
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `03487a17c0fb04390f00973c7179121b137536488afecc40f71c8f34454cf57e`,
  inventory
  `924b5a317e8440d5f583231ea7cfec91c892f822b2979574b8aadfae07332505`,
  classification
  `b202868a3fa5f6cf7b2c44d68fa38727034d109654fabed95fbd55cd8b403a45`,
  and occurrence
  `cdfe9069333827eca2f52701e321cb7b6234246d07979599311d9e8922c71930`; and
- no owner runtime route is required yet because the shadow has no observable
  client, packet, diagnostic, loading, eviction, movement, or persistence
  effect.

The next owner-testable slice should expose bounded ownership counts and
per-event global/shared transition evidence through a new additive private
diagnostic schema. Loading and eviction must remain disabled.

### Slice 41: Private global-interest ownership diagnostics

Objective: make Slice 40's process-local ownership shadow directly testable and
AI-readable before any Region loading, retention, release, or eviction policy is
considered.

Selected boundary:

- additive `layered-map-parity-event-v11` records retain the complete v10
  contract and add one bounded `interestOwnership` object;
- start, snapshots, markers, teleports, login, logout, stop, and ordinary moves
  that cross a logical-window boundary capture ownership; ordinary same-window
  moves carry an explicit null;
- current-state records use one atomic owner snapshot whose logical keys and
  global reference counts share the same ledger version;
- login, logical-window movement, level changes, teleports, and logout carry the
  exact immutable ledger `Change` produced for that Player operation, rather
  than reconstructing a transition after another owner could intervene;
- records expose only the monotonic process-local owner sequence, ledger
  version, open-owner/distinct-key counts, current owned-key count,
  minimum/maximum reference count, entered/retained/exited totals, exact
  global/shared acquisition and release totals, and bounded entered/exited
  Region transitions; and
- opaque owner sequences remain explicitly unrelated to player database IDs,
  username hashes, entity indexes, persistence, or reusable session identity.

Implemented:

- an atomic ledger `openOwner(window, budget)` result that carries both the
  opaque token and its exact first-window change;
- owner snapshots with same-version per-key global reference counts;
- Player propagation of the exact open, window-change, and final-close result
  into the existing observer hooks;
- atomic replacement of the trace's current-owner reader on reconnect, before
  the login record is written, so a surviving trace never queries the closed
  pre-logout Player object;
- a checked private command source for non-transition current-owner snapshots;
  and
- the retained v10 schema plus an additive, closed, bounded v11 schema and
  updated observer fixture coverage for unique/shared acquisition and release.

Safety boundary:

- the diagnostic stream reads ownership only after the existing Player shadow
  has been updated and refuses a mismatched owner/window or owner sequence;
- `PathValidation`, collision, movement permission, Region lookup and storage,
  eager loading, residency mirroring, packets, persistence, client loading,
  terrain, and world data do not consume the diagnostic result;
- reference counts are evidence, not pins; a zero count is not an unload order,
  a positive count is not a retention order, and v10 residency candidates
  remain dormant; and
- the observer remains opt-in and disabled by default for local and hosted
  configuration.

Automated validation evidence:

- Slice 1 through Slice 41 regressions all pass (96 tests), including the
  updated historical observer/schema consumers;
- the compiled observer fixture covers initial/current ownership, overlapping
  owners, unique/shared acquisition, unique/shared release, and closed-owner
  summaries; and
- World Builder discovery passes 13 tests and the standalone-layout guard
  passes;
- the authoritative bundled-Ant build succeeds for 745 core and 488 plugin
  sources;
- two real-repository normalizations are byte-stable with unchanged world
  content, 211 classified source owners, and one unresolved normalized
  coordinate. The expected fingerprints are source
  `5c86bb9de730c04dfd35c10e68c907a6f51df43b90ed41cdfeee4f8e9b0e0352`,
  inventory
  `c69ccf8673723be7644151762bd05e3650e5640b295984c2703562b1882b47e0`,
  classification
  `5aa650136625ec836b17b227d1ddc3209a01ca2bb59000b3162214dfcef6d1ab`,
  and occurrence
  `4e533d8513ed06ee84a5fcb15110054c97f87abb92bf2aa8592ee7c9e2a53d4f`;
  and
- the private runtime route is owner-validated as detailed below.

The private acceptance route should keep one trace active through ordinary
movement, one logical-window crossing, surface-to-underground travel,
logout/reconnect while underground, return to surface, and stop. The resulting
v11 stream must remain schema-valid; keep one stable owner sequence within each
session; allocate a new sequence after reconnect; show exact entered/exited and
global/shared counts only at real ownership boundaries; and report no packed,
tile, neighborhood, collision, traversal, or residency parity failure. A
compiled two-owner fixture supplies deterministic overlap coverage when a
second private human client is unavailable.

Owner validation evidence (2026-07-19):

- the accepted focused reconnect trace contains 12 contiguous, schema-valid
  v11 events: start, three teleports, three markers, logout, login, two ordinary
  moves, and stop;
- markers occur at `before-logout` underground, `reconnect-underground`, and
  `surface-return`, with the expected level sequence `0 -> -1 -> 0`;
- owner sequence 1 remains stable through the first session, then logout closes
  it at ledger version 14 with zero open owners, zero owned Regions, 36 exits,
  and 36 global releases;
- reconnect opens distinct owner sequence 2 at ledger version 16 with one open
  owner, 36 owned Regions, 36 entries, and 36 global acquisitions; the following
  reconnect marker successfully reads owner 2, proving the surviving trace was
  rebound to the newly constructed Player;
- the surface return advances owner 2 to ledger version 17 with exact 36-entry
  and 36-exit transitions, and every observed reference count remains one in
  this single-player route; deterministic compiled overlap coverage separately
  proves shared acquisition and release counts greater than one;
- Region residency mirror version 1,842 remains stable, all sampled windows
  contain 36 resident Regions, and partial, missing, unsupported, and load
  candidate counts remain zero; and
- sequence continuity, packed/layered round trips, packed coverage, current
  tiles, 3x3 neighborhoods, adjacent collision decisions, residency, and
  ownership aggregate/transition invariants report zero failures. The owner
  observed no visual or functional issues in the longer movement route or the
  focused reconnect route.

### Slice 42: Dormant Region retirement cooldown policy

Objective: define the first explicit anti-thrashing rule between global
interest reaching zero and any future Region retirement consideration, without
turning ownership or expiry into runtime authority.

Selected policy:

- every positive global logical-interest reference count is a pin;
- shared release from `N -> N-1`, where the result remains positive, leaves the
  Region pinned and never begins a cooldown;
- only an observed global `1 -> 0` release begins a cooldown, recorded from the
  monotonic server tick rather than wall-clock time;
- the provisional default grace is 16 server ticks. It is a benchmarkable
  policy constant, not a map-format promise or final production tuning value;
- any later global `0 -> 1` acquisition cancels the old release record; if the
  Region returns to zero again, the entire cooldown restarts from that newer
  release; and
- expiry produces `RETIREMENT_ELIGIBLE` evidence only when a supported logical
  Region still has a resident packed source. It is not an unload, eviction,
  cache-release, or lifecycle command.

Implemented:

- `LayeredRegionRetirementEligibilityLedger`, which consumes the exact
  versioned ownership changes from Slice 39/40 and mirrors their global
  reference counts with strict before-count, distinct-count, version-order,
  unique-key, and monotonic-tick checks;
- per-key release version, release tick, eligible tick, remaining grace, current
  ownership count, residency mirror version, source/resident-source counts, and
  the explicit states `PINNED`, `COOLING_DOWN`, `RETIREMENT_ELIGIBLE`,
  `NOT_RESIDENT`, `UNSUPPORTED`, and conservative `UNTRACKED`;
- RegionManager wiring under the existing layered lifecycle lock for exact
  owner open, window replacement, close, read-only snapshot, and unload clear;
  and
- deterministic overlap/reversal coverage proving that one owner leaving a
  shared Region does not start retirement and that reacquisition cancels stale
  eligibility.

Safety boundary:

- a never-owned resident Region remains `UNTRACKED`, not immediately eligible;
- logical eligibility is insufficient to retire a packed source because one
  packed source may contribute to multiple logical Regions, and NPC, system,
  transition-destination, instance, quest, editor, or preload pins do not exist
  yet;
- no timer scans Regions, no expiry callback exists, and no consumer enumerates
  eligible keys;
- `RegionManager.getRegion(...)`, packed storage, eager world loading, residency
  registration, tiles, entities, collision, pathing, packets, persistence,
  client loading, and diagnostics remain unchanged; and
- the policy is cleared during world unload and otherwise stores only keys,
  counts, versions, and ticks—never mutable Region or tile state.

Multi-owner gate decision:

- deterministic compiled two-owner coverage is sufficient for this dormant
  projection and the next diagnostic-only adoption because it exhaustively
  controls overlap, shared release, last release, reacquisition, and independent
  expiry timing;
- a real two-client or synthetic concurrent-owner private route is mandatory
  before any Region loader, retention cache, source-level arbiter, or eviction
  mechanism consumes the result.

Automated validation evidence:

- the focused Slice 42 contract passes both compiled policy behavior and the
  structural no-authority guard;
- all 98 layered-map slice tests pass, including the earlier staged-boundary
  inventory updated to name this approved coordinate package class;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant server build compiles 746 core and 488 plugin
  sources successfully without any loading or eviction consumer;
- two consecutive normalizations produced identical source
  `1dbbb97ded33091baaebee3af2b6403e88129c65ebd90adb26633cbf8fed377e`,
  inventory
  `117bedbe0449f182060fee672f900a68524fd9344a8ce919f2b77c6fad3ad05c`,
  classification
  `b376b91ae43f58fdd33e7b3394b15cf83b9832364ede230b1fff8bb2f1fca04a`,
  and occurrence
  `4e533d8513ed06ee84a5fcb15110054c97f87abb92bf2aa8592ee7c9e2a53d4f`
  fingerprints; and
- no owner runtime route is required because this slice has no diagnostic,
  client, packet, movement, or lifecycle effect.

The next owner-testable boundary should expose bounded cooldown state through
an additive private diagnostic schema. That evidence should prove release,
cooldown, reacquisition cancellation, logout/reconnect, and expiry timing while
remaining unable to enumerate or act on an eviction queue.

### Slice 43: Private Region retirement diagnostics

Objective: make Slice 42's pin, release, grace, cancellation, and expiry policy
visible in stable AI-readable private traces without turning the observer into
a Region scan, cache, loader, or eviction owner.

Implemented:

- additive `layered-map-parity-event-v12` JSONL records with a nullable
  `regionRetirement` block while the immutable v11 schema remains available for
  old traces;
- exact transition-Region observations plus a trace-local insertion-ordered set
  of recently globally released Regions. The observer retains at most 4096
  such candidates, drops the oldest on diagnostic overflow, and reports the
  cumulative dropped count;
- per-entry transition/candidate reasons, ownership and residency versions,
  observed server tick, provisional 16-tick grace, reference and packed-source
  counts, release/eligibility ticks, remaining grace, eligibility boolean, and
  the explicit Slice 42 state;
- aggregate transition, tracked, dropped, observed, pinned, cooling, eligible,
  nonresident, unsupported, and untracked counts so automated analysis does
  not need to reconstruct basic invariants from the entry list;
- a bounded RegionManager batch snapshot that captures every requested key at
  one server tick under the existing layered lifecycle lock; and
- login rebind of both ownership and retirement readers to the newly
  constructed Player, preserving an active trace across logout/reconnect.

Candidate behavior:

- a global `1 -> 0` release adds the Region after Slice 42 has begun its grace;
- any observed positive-reference transition removes that candidate before the
  event snapshot, while the transition entry still reports `PINNED` and null
  release timestamps;
- acquisition by another owner can be discovered on the next diagnostic event;
  a pinned snapshot with no release record is emitted once, then pruned;
- cooling and eligible records remain available for later marker/snapshot
  events until reacquisition, trace overflow, or trace stop; and
- ordinary same-window moves continue to emit null rather than repeatedly
  serializing unchanged retirement evidence.

Safety boundary:

- the trace set is observer-local diagnostic memory, not an authoritative
  retirement queue, and it stores only immutable logical keys;
- the batch API accepts only caller-supplied bounded unique keys and has no
  world enumeration path;
- no timer or expiry callback writes an event or performs work by itself;
- no diagnostic code calls Region lookup, registration, release, unload, or
  eviction; and
- packed eager residency, tiles, collision, entities, pathing, packets,
  persistence, and client loading remain unchanged.

Automated validation evidence:

- the focused v12 schema/wiring and compatibility tests pass;
- all 100 layered-map slice tests pass, including full v12 JSONL validation
  against the retained v11 definitions;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant server build compiles 746 core and 488 plugin
  sources successfully;
- two consecutive normalizations produced identical source
  `f2d7b08e019c12e93cb5e95b02e51c510df20f3fbba8ac774bc693f785e318d9`,
  inventory
  `e8a7e3f7a418505c56dd284fe391c786adb0c3fa41dab6f5fc43639ed7fa0eb3`,
  classification
  `2d928c483bf9a13b55cc7092213863502624323fcc7c4e656f3d32d66fa0d579`,
  and occurrence
  `cb475d534bebc8c848cfc0cb748b5290971196eb064f663f2e35aee0cdce89f2`
  fingerprints;
- the compatibility observer overload deliberately emits null retirement
  evidence when no source was supplied, while the private `::layerparity`
  command and reconnect path always supply the real bounded reader.

Owner validation evidence:

- the first private launch correctly refused `::layerparity` because the
  tracked local config retains its safe default `false`; the accepted rerun
  launched only the private process with
  `OPENRSC_LAYERED_MAP_PARITY_OBSERVER=true`, verified that exact variable on
  the listening process, and left hosted configuration/runtime untouched;
- the isolated log contains 12 consecutive, schema-valid v12 records covering
  start, baseline, four window-changing teleports, cooldown-expired and
  reacquired markers, logout, login, reconnect marker, and stop;
- the first global release occurred at server tick 99. Its 18 resident Regions
  reported the exact 16-tick grace and eligibility tick 115, while three
  legacy-unsupported Regions remained conservatively `UNSUPPORTED`; the
  delayed marker at tick 150 reported all 18 resident candidates
  `RETIREMENT_ELIGIBLE`;
- the additional return/Lumbridge/return route exercised two further release
  and reacquisition cycles. Every entered transition reported `PINNED`, a
  positive reference count, and null release/eligibility timestamps;
- logout at tick 274 released the current 36-Region window and reported 36
  cooling, 42 previously eligible, and 10 unsupported candidates. Login at
  tick 276 rebound owner sequence 1 to sequence 2, reported all 36 current
  Regions pinned, and preserved the older bounded candidates for the reconnect
  marker;
- every aggregate state count equals its observed-entry count, transition and
  tracked flags equal their declared counts, entry ownership versions equal the
  event ownership version, ticks are same-batch, and all cooldown-state
  arithmetic is exact;
- all records retain exact coordinate round trips, candidate overflow remained
  zero, and neither server nor client reported an observer/runtime error; and
- the owner completed the route without reporting a visual or functional
  regression.

### Slice 44: Concurrent-owner private-runtime gate

Objective: prove with two simultaneous real Player sessions that overlapping
logical interest remains pinned until the final owner releases it, then prove
the existing cooldown, expiry, and reacquisition behavior. This is a validation
gate over Slices 39-43, not adoption of a Region loader or retirement arbiter.

Why the gate uses real clients:

- each login receives the normal checked opaque Player-session owner and
  exercises the actual login, movement-window, logout, and global ownership
  paths;
- the primary observer can read the process-global reference totals after the
  second session acquires and releases the same Regions; and
- no synthetic owner, diagnostic mutation command, or privileged test-only
  lifecycle path can accidentally become a second source of authority.

Private test contract:

1. Start the trace on the primary development account at the normal Lumbridge
   spawn window before the second account logs in, and mark `single-owner`.
2. Log a new or disposable non-privileged account into the same private server;
   its normal spawn must overlap the primary window. Mark `shared-two` from the
   primary account.
3. Log the second account out normally while the primary account stays in
   place. Mark `shared-release` from the primary account.
4. Move the primary account directly to Varrock and mark `final-release`. This
   must create global `1 -> 0` releases for Regions that were shared rather
   than doing so when the second account left.
5. Wait longer than the 16-tick grace and mark `final-expired`.
6. Move the primary account directly back to Lumbridge, mark `reacquired`, and
   stop the trace.

Acceptance evidence:

- `single-owner` reports reference count one for the primary window;
- `shared-two` reports reference count two for the overlapping Regions;
- `shared-release` returns those Regions to reference count one and does not
  start their retirement cooldown;
- `final-release` reports the final global release and begins cooldown only
  after the primary owner leaves;
- `final-expired` reports resident supported candidates eligible only after
  the exact grace period, retaining conservative unsupported states;
- `reacquired` reports entered Regions pinned with positive references and null
  release/eligibility timestamps;
- owner-local/global aggregate arithmetic, retirement-state counts, sequence,
  coordinate round trips, and v12 schema validation all pass; and
- both clients remain visually and functionally normal throughout the route.

Safety and operation:

- use only the existing private `localhost:43615` server with
  `OPENRSC_LAYERED_MAP_PARITY_OBSERVER=true`; never involve the hosted port;
- preserve the existing trace by archiving it before the new trace starts;
- the secondary account needs no staff rights, commands, database mutation, or
  saved-state preparation because the normal new-account spawn supplies the
  overlap;
- use `::goto lumbridge` and `::goto varrock` explicitly rather than
  `::return`, whose saved destination can make the route ambiguous; and
- if the two spawn windows are not truly identical, analyze exact overlapping
  keys rather than treating aggregate minimum/maximum values as proof.

Owner validation evidence:

- the isolated trace contains 10 consecutive, schema-valid v12 records at the
  expected Lumbridge `(120,648)` and Varrock `(122,509)` destinations; every
  coordinate round trip is exact;
- `single-owner` reported one open owner and all 49 current Regions at reference
  count one. After the real secondary account logged into the identical
  Lumbridge spawn, `shared-two` reported two open owners and all 49 Regions at
  reference count two;
- the server log independently records the secondary account's normal login at
  `(120,648)` and normal player-requested logout through save, channel close,
  world unregistration, and PlayerSaveRequest removal;
- `shared-release` then reported one open owner and all 49 Regions back at
  reference count one. It produced no global-release transition and the
  retirement observer remained empty, proving the partial release did not
  begin cooldown;
- the primary move from Lumbridge to Varrock retained 28 Regions, globally
  released 21, and globally acquired 21 with zero shared-release transitions.
  Exactly 18 resident supported releases entered the 16-tick cooldown and the
  three unsupported releases remained conservatively `UNSUPPORTED`;
- the release event captured tick 1990 and eligibility tick 2006 exactly. Both
  `final-release` at tick 2008 and the later `final-expired` marker reported all
  18 supported candidates `RETIREMENT_ELIGIBLE`;
- the return to Lumbridge globally reacquired 21 Regions and emitted all 21 as
  `PINNED` with positive references and null release/eligibility timestamps.
  The owner named the following marker `acquired` rather than `reacquired`, but
  its `(120,648)` destination and state make the intended evidence unambiguous;
- candidate overflow remained zero; every retirement aggregate equals its
  entry-derived count, transition/tracked flags match, ownership versions are
  same-snapshot, and pinned, cooling, and eligible state arithmetic all pass;
- all 100 layered-map tests across 43 focused files pass, and both private
  client launches compiled successfully; and
- no error occurred during the accepted runtime gate, the owner completed it
  without noting a visual or functional problem, and the hosted server was not
  involved.

Status: implemented and owner-validated.

### Slice 45: Dormant Region retirement decision arbiter

Objective: introduce the smallest source-level consumer of Slice 42 eligibility
that can reject a stale or unsafe candidate after a fresh atomic recheck, while
remaining structurally incapable of changing packed Region lifecycle.

Implemented:

- `LayeredRegionRetirementDecisionArbiter`, a pure evaluator accepting one
  earlier candidate snapshot and one freshly captured snapshot for the same
  logical Region;
- an opaque per-ledger projection identity carried by immutable snapshots, so a
  candidate originating from another world/manager projection is rejected
  without retaining a reference back to the mutable ledger;
- explicit outcomes for eligible evidence, foreign projections, noneligible
  candidates, repinning, active cooldown, nonresidency, unsupported/untracked
  states, changed release identity, and changed residency version;
- comparison of release ownership version, release tick, and eligibility tick,
  preventing an old candidate from authorizing a later release even after that
  later release has completed its own cooldown;
- conservative residency-version equality, detecting source removal/re-addition
  and unrelated residency mutations so a caller must obtain a fresh candidate;
- deliberate tolerance for unrelated global ownership-version advances when
  the candidate Region's exact release identity remains current and its
  reference count remains zero; and
- single and bounded-batch RegionManager entry points that capture all current
  ownership, checked residency, release, and cooldown evidence under the
  existing lifecycle lock at one server tick. Batch inputs must fit the
  caller-supplied budget, remain within the existing 4096-Region hard ceiling,
  and identify unique non-null logical Regions.

Decision safety:

- evaluation is deterministic and non-consuming; repeating the same safe
  evaluation returns eligible evidence again rather than mutating or claiming
  the candidate;
- the arbiter has no `Region` reference, loader, registry, cache, tile/entity
  state, callback, timer, queue, or packed source mutator;
- the RegionManager boundary performs only checked snapshot reads and cannot
  call Region construction, registration, unregistration, unload, or eviction;
- no gameplay, pathing, packet, Player, persistence, or observer path consumes
  the new decision yet; and
- even `ELIGIBLE` is explicitly evidence rather than permission or an order to
  alter residency.

Automated validation evidence:

- the focused compiled fixture accepts a fresh candidate and repeated
  idempotent evaluation, then rejects reacquisition, pre-expiry input, a stale
  first-release candidate after re-release, changed residency, removed
  residency, a foreign projection, an unsupported candidate, mismatched keys,
  and null inputs;
- the fixture also proves an unrelated ownership-version advance does not
  invalidate an otherwise unchanged exact release;
- the complete layered-map suite passes 102 tests across 44 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 747 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `ad66d692e9ade0fea6dfb28b42b1c7fcad4fd42e2b8f56fff3823059bb4dbe6e`,
  inventory
  `9c66dbe1a50c4f16b928fc4eb632f14251f1c5b32f6734bdd17370cde037a5d8`,
  classification
  `5b22933eaf16beb52e16f804b20fdf46c3fc21cfcdd759e2b49a02e247d9841e`,
  and occurrence
  `cb475d534bebc8c848cfc0cb748b5290971196eb064f663f2e35aee0cdce89f2`
  fingerprints.

Status: implemented and validated; runtime adoption remains deliberately absent.

### Slice 46: Private Region retirement decision diagnostics

Objective: make Slice 45's accepted and refused decisions visible in stable
AI-readable private traces, while ensuring that observer retention remains a
bounded diagnostic mechanism rather than a Region retirement queue.

Implemented:

- additive `layered-map-parity-event-v13` JSONL records with a nullable
  `regionRetirementDecisions` block while the immutable v12 schema remains
  available for old traces;
- an observer-local insertion-ordered map retaining at most 4096 immutable
  `RETIREMENT_ELIGIBLE` snapshots from currently tracked release candidates;
- atomic recheck of that bounded snapshot list through the Slice 45
  RegionManager batch boundary at one current server tick;
- aggregate candidate, dropped, eligible, and refused counts plus one entry per
  candidate containing its logical key, candidate/current ownership and
  residency versions, candidate/current release ownership version, release and
  eligibility ticks, current cooldown state, exact decision state, and eligible
  boolean;
- explicit `ELIGIBLE`, `FOREIGN_PROJECTION`, `CANDIDATE_NOT_ELIGIBLE`, `PINNED`,
  `COOLING_DOWN`, `NOT_RESIDENT`, `UNSUPPORTED`, `UNTRACKED`, `RELEASE_CHANGED`,
  and `RESIDENCY_CHANGED` decision states;
- one-time reporting and removal of refused candidates, while safe candidates
  remain available for repeated idempotence evidence; and
- login rebind of the decision reader together with the existing ownership and
  retirement readers, preserving an active trace across reconnect.

Candidate behavior:

- a tracked release becomes a decision candidate only after a v12 retirement
  snapshot reports it eligible; cooling and unsupported entries never enter the
  decision set;
- the candidate is immediately rechecked rather than assuming the just-read
  snapshot is still current;
- a later positive-reference transition leaves the old immutable candidate in
  place until the event's arbiter recheck reports `PINNED`, after which it is
  pruned;
- a later release, residency change, source removal, or foreign projection is
  likewise reported as an explicit refusal once before pruning; and
- overflow evicts the oldest diagnostic candidate and increments its own
  cumulative dropped count. It does not alter the cooldown ledger or Region
  storage.

Safety boundary:

- retained snapshots carry an opaque projection identity but no mutable ledger
  reference and expose no Region handle;
- the observer cannot enumerate the world and can consider only bounded release
  keys already observed by the active trace;
- neither the schema, candidate map, metadata, nor source callbacks can call
  Region construction, registration, unregistration, unload, or eviction;
- refused and eligible results are diagnostic facts, not commands, leases,
  claims, callbacks, or timer work; and
- packed eager residency and all gameplay authority remain unchanged.

Automated validation evidence:

- the compiled observer fixture emits schema-valid v13 records and proves an
  eligible candidate, a later real repin refusal, and one-event refusal pruning;
- the v13 schema is closed, preserves the full v12 contract, caps entries at
  4096, and enumerates every Slice 45 decision state;
- compatibility start overloads emit explicit null decision evidence when no
  decision source was supplied;
- the complete layered-map suite passes 104 tests across 45 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 747 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `4cf3ff069054933af003ba7cebec3ee8c4f5af04aaa3db43c817000664e8df55`,
  inventory
  `a5cc7c2535ab86cc0f51820611a1e8a0dc2fd58cd55d6ded5db12dbe19773738`,
  classification
  `1d7c5e3e2b6b8f3533015693dc43cb8474404d20e39d7abc6b6a2008db158d66`,
  and occurrence
  `1f674f7fffcccb9d689c570d42900a3c2c8cba9c1082edc512e0f99ea2971d79`
  fingerprints.

Private owner-validation contract:

1. Start at Lumbridge, begin the trace, and mark `decision-baseline`.
2. Move directly to Varrock. Wait at least 15 seconds, then mark
   `decision-eligible`; every supported released Lumbridge candidate should be
   atomically accepted while unsupported Regions remain absent.
3. Move directly back to Lumbridge. The teleport event should report those old
   Lumbridge candidates `PINNED` exactly once while beginning cooldown for the
   released Varrock-only Regions.
4. Immediately mark `decision-refusals-pruned`; no refused Lumbridge candidate
   should remain.
5. Wait at least 15 seconds, mark `decision-second-wave`, and stop. The supported
   Varrock-only releases should now have fresh eligible decisions.

Private owner-validation evidence:

- all eight captured records validated against the closed v13 schema using its
  retained local v11/v12 schema registry;
- the first release produced 18 supported decision candidates and all 18 were
  accepted as `ELIGIBLE`, with the three unsupported release entries remaining
  outside the decision set;
- returning to Lumbridge refused those exact 18 candidate identities as
  `PINNED`, with zero accepted candidates in that event;
- no refused first-wave identity appeared in any later decision set, proving
  one-event reporting and pruning;
- the post-return marker was entered 33 ticks after the return teleport, so the
  newly released Varrock set had already passed its 16-tick cooldown; it
  correctly contained a disjoint second set of 18 eligible identities, which
  remained identically eligible at `decision-second-wave`; and
- dropped-candidate count remained zero throughout, and the owner reported no
  visual or functional issue.

Status: implemented and owner-validated.

### Slice 47: Dormant packed-source retirement readiness

Objective: cross the first logical-to-packed consumer boundary without treating
one logical Region decision as permission to retire a packed 48×48 source that
may also contain tiles from other logical Regions or levels.

Implemented:

- immutable `LayeredPackedRegionRetirementReadiness` aggregation over a
  bounded list of Slice 45 decisions captured at one ownership version,
  residency-mirror version, and server tick;
- expansion of every input logical key through its checked legacy assembly,
  followed by deduplication into at most two packed sources per logical Region
  and an explicit 8192-source hard ceiling for the 4096-key manager boundary;
- inverse coverage checks for each proposed packed source, requiring every
  logical Region represented by that source to have an `ELIGIBLE` decision in
  the same atomic input before the source becomes `READY`;
- explicit `INCOMPLETE_COVERAGE`, `REFUSED_COVERAGE`, and
  `PARTIAL_RESIDENCY` and `PARTIAL_LEGACY_DOMAIN` blocked states, with immutable
  covered, missing, refused, and partially resident logical-key lists;
- preservation of cross-level coverage as evidence rather than rejecting it:
  a packed source spanning a 944-tile legacy plane boundary becomes ready only
  when the logical Regions on both levels are eligible together; and
- a bounded RegionManager preparation method that freshly rechecks the earlier
  candidates under the existing lifecycle lock before source aggregation.

Important mapping finding:

- the 944-tile legacy plane stride is not divisible by the 48-tile Region size;
  therefore packed sources around each plane boundary can cover two levels,
  while one logical Region adjacent to the boundary can itself require two
  packed sources;
- source readiness must consequently be evaluated as an inverse-coverage
  problem. A set of logical candidates may yield safe interior packed sources
  while its boundary sources remain blocked until neighboring logical
  decisions are present; and
- this is a compatibility constraint of the current packed archive, not a
  property the future layered archive should reproduce.

Safety boundary:

- readiness contains coordinates, versions, decision states, and logical keys,
  but no mutable Region, tile, collision, entity, visibility-cache, loader, or
  registry handle;
- `READY` is revocable evidence, not a permit, claim, callback, lease, queue
  item, unload request, or commit token;
- partial edge sources remain blocked even when their represented logical key
  is eligible, pending an explicit legacy-domain edge policy; and
- a logically eligible multi-source Region with only some packed sources
  resident remains blocked, preventing older partial-residency evidence from
  becoming source-level readiness; and
- the manager boundary performs no Region lookup, construction, registration,
  unregistration, unload, removal, cache invalidation, or eviction.

Automated validation evidence:

- the focused compiled fixture proves ordinary one-to-one readiness, missing
  cross-level coverage refusal, complete cross-level source readiness, repin
  refusal of the whole shared source, incomplete adjacent-source protection,
  partial-residency and partial-domain blocking, same-snapshot enforcement,
  uniqueness, immutability, and both logical/source budgets;
- source guards prove the readiness value and manager preparation boundary have
  no packed Region lifecycle mutator and remain absent from PathValidation and
  private diagnostics;
- the complete layered-map suite passes 106 tests across 46 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 748 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `f84521fa71154e822d09704665c6a05062ea11fa42f7e0dbdef4522d8f952485`,
  inventory
  `8cb82502dc6999c8c94b433b36a35d7a629ebb1e5462baa2390307d2ca99ad40`,
  classification
  `9653761ab0f23fea4f6d0cbf63cf661ab875b5e1ceed05dcfd284087207347c3`,
  and occurrence
  `1f674f7fffcccb9d689c570d42900a3c2c8cba9c1082edc512e0f99ea2971d79`
  fingerprints.

Status: implemented and validated. Lifecycle adoption remains deliberately
absent; diagnostic exposure begins in Slice 48.

### Slice 48: Private packed-source retirement readiness diagnostics

Objective: make Slice 47's source aggregation observable in stable AI-readable
private traces before considering any source claim, commit token, or lifecycle
consumer.

Implemented:

- additive `layered-map-parity-event-v14` JSONL records with a nullable
  `packedRegionRetirementReadiness` block while retaining the immutable v13
  schema for old traces;
- direct aggregation from the exact atomic Slice 45 decision list already
  returned to the observer, avoiding a second manager read, tick, or
  preparation call;
- aggregate observation tick, ownership version, residency-mirror version,
  logical-decision count, packed-source count, ready count, and blocked count;
- one bounded entry per deduplicated packed source with its packed coordinates,
  complete logical coverage, missing decisions, refused decisions, partially
  resident decisions, cross-level flag, and exact Slice 47 source state; and
- a maximum of 8192 entries derived from the existing 4096 logical-candidate
  limit and checked two-source assembly fanout.

Safety boundary:

- the observer does not call
  `prepareLayeredPackedRegionRetirementReadiness`; it serializes the immutable
  result derived from the decision batch it already owns;
- nullable compatibility remains explicit when no retirement-decision source
  is installed, and an installed source with no candidates emits an explicit
  empty readiness aggregate;
- v14 adds no Region handle, manager mutator, loader, registry, entity,
  collision, cache, callback, timer, queue, claim, lease, or persistence field;
  and
- `READY` remains diagnostic evidence only and cannot cause a packed Region to
  be constructed, registered, unregistered, unloaded, removed, or evicted.

Automated validation evidence:

- the compiled observer fixture emits a ready source from an eligible logical
  decision, a `REFUSED_COVERAGE` source from a real repin, and an empty source
  set after one-event refusal pruning;
- every emitted record validates against the closed v14 schema through its
  local v11-v13 registry;
- the v14 schema retains the complete v13 contract, caps logical decisions at
  4096 and sources at 8192, caps one source's inverse coverage at two logical
  keys, and enumerates every Slice 47 state; and
- the complete layered-map suite passes 108 tests across 47 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 748 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `26926df17a4bf145252966f0fedbb457f5bf6a5761e79e1d6b87957285c59292`,
  inventory
  `8cb82502dc6999c8c94b433b36a35d7a629ebb1e5462baa2390307d2ca99ad40`,
  classification
  `9653761ab0f23fea4f6d0cbf63cf661ab875b5e1ceed05dcfd284087207347c3`,
  and occurrence
  `1f674f7fffcccb9d689c570d42900a3c2c8cba9c1082edc512e0f99ea2971d79`
  fingerprints.

Private owner-validation contract:

1. Start at Lumbridge, begin the trace, and mark `source-baseline`.
2. Move directly to Varrock. Wait at least 15 seconds, then mark
   `source-first-wave`; supported released Lumbridge logical decisions and
   their ordinary one-to-one packed sources should be `ELIGIBLE`/`READY`.
3. Move directly back to Lumbridge. The teleport event should refuse the old
   logical decisions as `PINNED` and classify their exact packed sources as
   `REFUSED_COVERAGE` once.
4. Wait at least 15 seconds, mark `source-second-wave`, and stop. The disjoint
   Varrock release should produce a fresh eligible/ready source wave.

Private owner-validation evidence:

- all seven captured records validated against the closed v14 schema through
  its local v11-v13 registry;
- the first release produced 18 eligible logical decisions and 18 one-to-one
  packed sources, all classified `READY`;
- returning to Lumbridge refused those exact 18 logical identities as `PINNED`
  and blocked those exact 18 packed-source identities as `REFUSED_COVERAGE`;
- the second release produced a disjoint set of 18 logical identities and 18
  packed-source identities, all `ELIGIBLE`/`READY`;
- every record preserved logical-decision/source aggregate arithmetic, the
  residency mirror remained at version 1842, and candidate overflow remained
  zero; and
- the owner completed the route without reporting a visual or functional
  problem.

Status: implemented and owner-validated. Runtime adoption remains deliberately
absent.

### Slice 49: Dormant packed-source contents safety assessment

Objective: determine whether a Slice 47 source that is logically `READY` is
also empty and recoverable enough to consider for later retirement, without
granting any unload authority.

Implemented:

- immutable `LayeredPackedRegionRetirementSafetyAssessment` values that combine
  one bounded readiness snapshot with resident/tile-storage state and exact
  player, NPC, scenery-object, and ground-item counts for each same-order
  packed source;
- separate `contentQuiescent` and `lifecycleReady` results so an empty Region
  cannot be confused with a Region that is safe to destroy and reconstruct;
- explicit blockers for refused logical readiness, absent residency, missing
  tile storage, every populated content family, and unavailable reload support;
- a Region-local snapshot that holds the four synchronized entity-collection
  monitors together while capturing counts, but returns no collection, entity,
  tile, or Region handle; and
- a bounded RegionManager assessment under the existing lifecycle lock that
  uses non-creating packed lookup and records the current server tick.

Critical lifecycle finding:

- current `Region.unload()` clears players, NPCs, scenery objects, ground items,
  and tile arrays;
- current loading is whole-world `WorldLoader.loadWorld()` behavior, with no
  packed-source reload/recovery operation that could rebuild one retired
  Region and its static/dynamic contents; and
- RegionManager therefore hardcodes packed-source reload support to `false`.
  Even a resident, tile-backed, empty, logically `READY` source receives
  `RELOAD_PATH_UNAVAILABLE` and has `lifecycleReady=false`.

Safety boundary:

- the assessment is ephemeral evidence that may become stale immediately; it
  is not a claim, permit, lease, queue entry, callback, token, or commit guard;
- the manager boundary cannot create, register, unregister, unload, remove,
  evict, or invalidate a packed Region or cache;
- an absent source reports `SOURCE_NOT_RESIDENT` and
  `TILE_STORAGE_UNAVAILABLE` rather than resembling an empty source; and
- at the Slice 49 checkpoint the assessment remained absent from
  PathValidation and private diagnostics; Slice 50 adds only the separately
  gated diagnostic exposure below.

Automated validation evidence:

- the compiled fixture proves exact content counts and blocker order,
  quiescence independent of reload capability, readiness-refusal precedence,
  absent-source handling, a future reload-capable success case, version/tick
  preservation, immutability, ordering, null rejection, and source budgets;
- source guards prove the manager uses the lifecycle lock, non-creating lookup,
  and consistent Region snapshot while granting no lifecycle or observer
  authority;
- the complete layered-map suite passes 110 tests across 48 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 749 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `c2a193a62ced3409a57bf7d74731619ccc3c2c77dd7cf4e2198ffa11082c439f`,
  inventory
  `8f485c302ac4832c113488cddf2e13425a192dc9052b7f837d417e0dbad87cc4`,
  classification
  `9521d960ffbf9acaf2554c1ec67fb0b5ff5b21421f452e045a9b2b12c7db34c8`,
  and occurrence
  `1f674f7fffcccb9d689c570d42900a3c2c8cba9c1082edc512e0f99ea2971d79`
  fingerprints.

Status: implemented and validated. Diagnostic exposure is implemented
separately in Slice 50 below; actual retirement remains blocked pending a
separately designed packed-source reload and recovery contract.

### Slice 50: Private packed-source contents safety diagnostics

Objective: expose Slice 49's ephemeral source-content assessment in stable,
AI-readable private traces so real-world content blockers can be measured
before any reload or retirement design.

Implemented:

- additive `layered-map-parity-event-v15` JSONL records with a nullable
  `packedRegionRetirementSafety` block while retaining the immutable v14 schema
  for old traces;
- capture through the exact `LayeredPackedRegionRetirementReadiness` value
  already emitted by the event, followed by the manager's bounded,
  non-creating contents assessment;
- aggregate observation/readiness ticks, ownership and residency versions,
  source count, content-quiescent count, lifecycle-ready count, and blocked
  count;
- one bounded entry per same-order source with packed coordinates, readiness
  state, residency/tile/reload flags, player/NPC/scenery-object/ground-item
  counts, quiescence/readiness booleans, and stable blocker names; and
- initial dev-command wiring plus Player-session rebinding so a trace retained
  across logout/reconnect reads the current RegionManager.

Safety boundary:

- the new source receives immutable readiness and returns immutable assessment;
  neither the observer nor schema receives a Region, entity, collection, tile,
  loader, registry, cache, claim, callback, queue, lease, or commit handle;
- safety capture occurs only after the same event has atomically rechecked its
  logical retirement candidates and derived packed readiness;
- compatibility overloads may emit explicit `null` when no safety source is
  installed, while a missing readiness value requires safety to be null; and
- current runtime entries always include `RELOAD_PATH_UNAVAILABLE`, leaving
  lifecycle-ready count zero regardless of content quiescence.

Automated validation evidence:

- the compiled observer fixture emits populated logically ready safety,
  repinned/refused safety, and an explicit empty safety aggregate from one
  retained trace;
- every emitted fixture record validates against the closed v15 schema through
  its local v11-v14 registry;
- the v15 schema retains the complete v14 contract, caps sources and entries at
  8192, closes aggregate and entry properties, and enumerates every readiness
  state and blocker;
- the complete layered-map suite passes 112 tests across 49 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 749 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produced identical source
  `c43873af00b0516aaa021e319fe3debf498d8086462030c5d960ebedabb49baa`,
  inventory
  `9db0494c996a73839e7d2815765a4fd6a87dcfaffeec4bfdf0f399683435d276`,
  classification
  `fd7279e556311b80f97cc5313eb488d39a92da123b5102d4ecf32b92287339c8`,
  and occurrence
  `14bdefc20442d9f4cfc6d728dc5fdb9deb57871c4fdccbaeee7886e269f243e5`
  fingerprints.

Private owner-validation contract:

1. Start at Lumbridge, begin the trace, and mark `safety-baseline`.
2. Move directly to Varrock. Wait at least 15 seconds, then mark
   `safety-first-wave`; released Lumbridge sources should expose their real
   NPC/object/item counts, any empty sources, and reload blockers.
3. Move directly back to Lumbridge. The return event should refuse the exact
   first-wave sources as `READINESS_NOT_READY`; interest ownership should keep
   the currently occupied source outside that retirement wave.
4. Wait at least 15 seconds, mark `safety-second-wave`, and stop. The disjoint
   Varrock release should expose its own content/blocker distribution.

Private owner-validation evidence:

- all seven records validated against the closed v15 schema through its local
  v11-v14 registry, with contiguous sequences and matching readiness/safety
  ticks, ownership versions, residency versions, source counts, and aggregate
  arithmetic;
- the first wave contained exactly 18 ready Lumbridge-edge sources at packed
  X `0..5`, Y `14..16`: six were content-quiescent, 11 contained NPCs, 12
  contained scenery objects, and six contained ground items, totaling 129
  NPCs, 637 objects, and 60 ground items;
- returning to Lumbridge refused those exact 18 source identities as
  `READINESS_NOT_READY` while preserving identical content counts; no source
  reported a player, confirming the occupied/retained interest window remained
  outside the retirement candidates;
- the second wave contained a disjoint 18 ready Varrock-edge sources at packed
  X `0..5`, Y `7..9`: three were content-quiescent, 12 contained NPCs, 15
  contained scenery objects, and eight contained ground items, totaling 120
  NPCs, 1985 objects, and 47 ground items;
- all 36 ready source observations were resident and tile-backed, all nine
  content-quiescent observations still reported `RELOAD_PATH_UNAVAILABLE`, and
  lifecycle-ready count remained zero throughout; and
- the route completed without a reported visual or functional problem.

Status: implemented and owner-validated. Actual retirement remains absent.

### Slice 51: Authored packed-source construction inventory

Objective: inventory the authoritative construction and teardown owners found
after Slice 50, then preserve an exact per-packed-Region count of authored
placements that the current configured population pass actually constructs.
This is the smallest useful prerequisite for a later detached reconstruction
study; it is not a reload path.

Construction and teardown ownership audit:

| Content family | Current construction owner | Current active owners after construction | Why `Region.unload()` alone is unsafe |
| --- | --- | --- | --- |
| Terrain and base collision | `WorldLoader.loadWorld()` opens the configured JAG/MEM or ZIP terrain source, loops all four legacy floors and every 48-tile packed cell, writes `TileValue`, then compacts uniform Regions | packed `Region` tile storage; source archives retained only in the whole-world loader | there is no one-Region loader; destroying tile storage also destroys terrain collision and all later dynamic collision mutations |
| Authored scenery and boundaries | `WorldPopulator` merges configured base/custom/MyWorld placement files, applies removals and membership filters, constructs `GameObject`, then `World.registerGameObject()` derives collision | the packed Region entity collection and mutable tile collision; scenery additionally leaves only a tile-to-base-ID entry in `World.sceneryLocs` | the scenery map omits boundaries, direction, type, replacement state, and teardown; unregistering an object also mutates neighboring collision tiles |
| Authored NPC spawns | `WorldPopulator` filters `NPCLoc` definitions, constructs an `Npc`, and adds it to both its current packed Region and the World-global NPC list | packed Region, global NPC list, NPC roaming/respawn state, combat references, and scheduled events | an NPC can roam away from its authored start Region; clearing one Region neither removes it from global ownership nor safely resolves respawn/combat/events |
| Authored ground-item spawns | `WorldPopulator` constructs through `World.registerAuthoredGroundItem()` | packed Region plus `AuthoredGroundItemRegistry` tile identity/generation and possible delayed respawn event | clearing a Region leaves the authored registry and delayed generation callbacks inconsistent; active absence during a respawn delay is valid state |
| Harvesting conversions | configured authored ground-item definitions are conditionally converted into scenery and registered through the object path | packed Region, dynamic collision as applicable, and `World.sceneryLocs` | the original source family and constructed runtime family differ, so raw placement-file counts cannot describe teardown/rebuild |
| Dynamic objects, NPCs, and items | plugins, quests, combat, inventory, summoning, minigames, and delayed events construct the same runtime entity classes | packed Regions plus family-specific global lists, attributes, owners, timers, and scripts | objects and NPCs have location definitions even when dynamically created, so `getLoc() != null` is not provenance; only ordinary ground items currently have a reliable `loc == null` distinction |
| Players | login/persistence constructs and registers Player state | packed Region, World player list, session/login ownership, social/party/clan references, persistence | player absence in a candidate is mandatory but not sufficient; a Region teardown must never own player logout or persistence |
| Visibility caches | RegionManager builds packed object windows and snapshots | multiple Region-indexed cache/reverse-index maps | object changes invalidate affected caches, but `Region.unload()` does not provide a complete transactional teardown/rollback boundary |

Critical audit findings:

- `Region.unload()` is a whole-world shutdown primitive in practice: it clears
  its four entity collections and nulls tile storage but does not unregister
  NPCs, objects, items, players, scheduled events, authored item generations,
  global NPC ownership, scenery base IDs, or cross-Region collision;
- `World.unload()` supplies the surrounding whole-world sequencing by saving
  players, stopping other systems, unloading all Regions, resetting authored
  item generations, and clearing global collections. That sequence cannot be
  reduced to a call on one Region;
- the filtered `gameobjlocs`, `npclocs`, and `itemlocs` arrays survive inside
  the startup `WorldPopulator`, but they are mutable definition lists rather
  than a versioned reconstruction manifest, and their source count can differ
  from constructed content after filters and harvesting conversion;
- object and NPC constructors do not encode authored-versus-dynamic
  provenance. Instance `GameObjectLoc`/`NPCLoc` presence therefore cannot be
  used to declare current content reconstructible;
- NPC authored ownership is based on its spawn definition while active Region
  ownership follows its roaming position. Any later comparison must keep
  "constructed from this source" separate from "currently in this source";
  and
- safe reconstruction ultimately needs a detached definition manifest,
  event/dynamic-state policy, collision rebuild, global-registry reconciliation,
  atomic recheck/commit token, failure rollback, and cache invalidation. Counts
  alone intentionally satisfy none of those requirements.

Implemented:

- immutable `LayeredPackedRegionAuthoredConstructionInventory` values with a
  monotonic whole-world population generation and deterministic packed
  coordinate ordering;
- exact count families for scenery, boundaries, NPC spawns, authored
  ground-item spawns, and ground-item-to-harvesting-scenery conversions;
- recording only after the configured membership/event/removal filters and the
  corresponding runtime construction/registration step have succeeded;
- a startup-only bounded builder that rejects negative coordinates, null
  kinds, non-positive generations, more than 8192 packed sources, arithmetic
  overflow, and reuse after completion; and
- one volatile immutable snapshot published only after the full population
  pass completes. Before that point callers see an explicit generation-zero
  empty inventory rather than a partially built value.

Safety boundary:

- the inventory retains counts only: no placement definition, Entity, Region,
  TileValue, collection, archive, event, callback, registry, cache, claim,
  permit, lease, or commit handle crosses the boundary;
- it reports authored construction origins, not current active content,
  teardown ownership, recoverability, or reconstructibility;
- it is not consulted by RegionManager, PathValidation, diagnostics, gameplay,
  population filtering, registration, collision, persistence, or clients; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains `false`; no Region can be
  unloaded, removed, evicted, or reconstructed through this slice.

Automated validation evidence:

- the compiled fixture proves exact family/source totals, deterministic source
  ordering, lookup, generation-zero emptiness, immutability, invalid-input
  refusal, and completed-builder refusal;
- source guards prove every supported configured construction path is recorded
  after runtime registration, while RegionManager and the private observer do
  not receive the inventory;
- the complete layered-map suite passes 114 tests across 50 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 750 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produce identical source
  `0d1dd6e6db756508498f91518493377f46dd59464d41e2c8bb63e8a7546278f8`,
  inventory
  `eedf7a507bc46f431caa5a8ec9fc48de1c36285d1b51062e8e8447826f817783`,
  classification
  `c19b983cf1691e3ab608596fce01db574d04e642fc4e2dea2f1d980c6274f70f`,
  and occurrence
  `3e9bf5f961cb3b7721eeccd0ad8bea3d2d3b192facd229c15e98c7ebdaf46e3b`
  fingerprints, confirming this runtime-only count inventory does not alter
  normalized map inputs or outputs.

Status: implemented and validated. Diagnostic exposure and any detached
definition manifest remain separate later slices; actual retirement remains
absent.

### Slice 52: Private authored-construction origin diagnostics

Objective: correlate Slice 50's active packed-source content counts with Slice
51's configured authored construction origins in stable, AI-readable private
traces, without inferring current entity provenance or reconstructibility.

Implemented:

- immutable `LayeredPackedRegionAuthoredConstructionObservation` values that
  project one whole-world inventory onto the exact same-order packed sources
  in a retirement-safety assessment;
- generation, safety/readiness ticks, whole-inventory family totals, observed
  source/family totals, and one exact count-only entry per safety source;
- absent inventory entries represented as zero authored origins rather than a
  missing or implicitly quiescent runtime Region;
- additive `layered-map-parity-event-v16` JSONL records with nullable
  `packedRegionAuthoredConstruction`, while the immutable v15 schema remains
  available for existing captures;
- schema constants `originCountsOnly=true` and
  `reconstructionManifest=false`, making the semantic boundary machine-checkable
  rather than relying only on prose; and
- initial dev-command wiring plus Player-session rebinding so a trace retained
  across logout/reconnect reads the current WorldPopulator inventory generation.

Safety boundary:

- construction projection occurs only after the exact same event has produced
  packed readiness and read-only safety; if safety is null, the new payload is
  required to be null;
- the source receives only an immutable safety value and returns only an
  immutable count projection. It receives no Region, entity, placement
  definition, TileValue, archive, event, registry, cache, lifecycle owner, or
  commit handle;
- an authored NPC count identifies its start/source Region, not its current
  roaming Region; object and item counts likewise do not prove that a current
  instance is the authored instance or currently present; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false. The observer and
  schema cannot unregister, unload, remove, evict, reconstruct, or roll back a
  packed Region.

Automated validation evidence:

- the compiled v16 observer fixture proves populated authored-origin counts for
  ready and repinned sources, explicit empty aggregates, compatibility nulls,
  source ordering, family and total arithmetic, and generation/tick
  correlation;
- every fixture record validates against the closed v16 schema through its
  local v11-v15 registry; v16 references the immutable v15 field contracts and
  adds only its closed count-only block;
- source guards prove the projection reads the completed WorldPopulator
  inventory and exact safety entries while serialization has no lifecycle or
  construction calls;
- the complete layered-map suite passes 116 tests across 51 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 751 core and 488 plugin sources
  successfully; and
- two consecutive normalizations produce identical source
  `44af58896e4ca44b508651ae78b7e9895932c8747378d14b37a20d7ff7d95c82`,
  inventory
  `b3988db5f4a2da2b7d3036ee343180da5b82a5cf92f162f9f63cc9284edd73d8`,
  classification
  `6524100a65a2beef07f8b44489d94562000f4f422c4495dad6e19f2da4c70c35`,
  and occurrence
  `590ee0ba4293283abe33ebce8c4063fdf0430d36a6a74d549e21af9122bbada6`
  fingerprints.

Private owner-validation contract:

1. Start at Lumbridge, begin a fresh trace, and mark `origin-baseline`.
2. Move directly to Varrock, wait at least 15 seconds, and mark
   `origin-first-wave`.
3. Return directly to Lumbridge, wait at least 15 seconds, mark
   `origin-second-wave`, and stop.
4. The owner need only report whether movement, scenery, NPCs, items, and
   collision remained normal. The AI will validate v16 schema/order/arithmetic,
   compare active contents with authored origin families, and identify where
   roaming, temporary removal/replacement, dynamic state, or zero authored
   origins require the next provenance design.

Private owner-validation evidence:

- the owner completed the Lumbridge-to-Varrock-to-Lumbridge route without a
  reported movement, scenery, NPC, item, collision, visual, or functional
  problem;
- the fresh trace contains seven contiguous v16 records (`start`, baseline,
  the two teleports and waves, then `stop`), all with exact coordinate round
  trips and valid closed-schema payloads;
- every construction projection shares its safety/readiness ticks and its
  exact same-order source coordinates with the corresponding safety value;
  all entry, observed, and whole-inventory family totals reconcile;
- the stable generation-one whole-world inventory contains 366 authored
  packed sources and 33,532 constructed origins: 27,759 scenery, 973
  boundaries, 3,775 NPC spawns, 882 ground-item spawns, and 143 harvesting
  conversions;
- the first departed 18-source wave contained 827 authored origins across 12
  non-empty sources. All 18 ground-item counts matched active state, 17 object
  counts matched, and 13 NPC counts matched; the aggregate active-minus-origin
  differences were zero ground items, minus one object, and minus one NPC;
- the second departed 18-source wave contained 2,152 authored origins across
  15 non-empty sources. All object and ground-item counts matched active state,
  while 14 NPC counts matched; the aggregate NPC difference was minus two; and
- NPC differences move in both directions among adjacent sources while staying
  close in aggregate, directly confirming why spawn-origin ownership must not
  be inferred from a roaming NPC's active Region. The one first-wave object
  difference likewise confirms that an origin count is not proof that its
  runtime object is presently active.

Decision from the evidence: preserve authored placement identity and full
detached construction inputs before attempting teardown or reconstruction.
The next bounded slice should define an immutable, deterministic per-source
definition manifest, including duplicates and original construction family,
without retaining live entities or granting a lifecycle consumer. NPC spawn
origin must remain distinct from active position; harvesting conversion must
remain distinct from ordinary scenery; dynamic state and delayed respawn state
remain later policies.

Status: implemented and owner-validated. Actual retirement remains absent.

### Slice 53: Detached authored-placement manifest

Objective: retain the exact primitive construction inputs and deterministic
authored identity of every placement accepted by the configured whole-world
population pass. This upgrades Slice 51's count-only origins into inert
definition evidence; it does not make those definitions executable for Region
reload.

Implemented:

- immutable `LayeredPackedRegionAuthoredPlacementManifest` generations grouped
  by deterministic packed source coordinate, with successful population order
  retained within each source;
- a stable one-based placement identity tuple of packed source X, packed source
  Y, and source ordinal. Exact duplicate definitions remain separate entries
  rather than being deduplicated or ambiguously addressed;
- detached object construction inputs: authored/constructed ID, packed tile,
  permanent object ID, direction, object type, and immutable owner text;
- detached NPC inputs: ID, authored start tile, and full roaming bounds. The
  source Region is derived from the authored start and never from the NPC's
  later active position;
- detached authored ground-item inputs: ID, packed tile, amount, respawn time,
  and noted state;
- explicit harvesting-conversion inputs that preserve both the source item
  definition and the scenery object actually constructed from it, rather than
  collapsing the conversion into ordinary scenery;
- bounded startup construction with the existing 8192-source ceiling, a
  262,144-placement ceiling, checked counters, closed-builder refusal,
  coordinate ordering, binary source lookup, and immutable placement lists;
  and
- a mandatory generation/source/family count-equivalence check against Slice
  51's independently accumulated inventory before either completed value is
  published by `WorldPopulator`.

Construction boundary:

- definitions are recorded only after the same membership, feature, event,
  removal, and replacement filters used by population and only after the
  corresponding object/NPC/item registration succeeds;
- the manifest contains primitives and immutable text only. No Entity,
  `GameObjectLoc`, `NPCLoc`, `ItemLoc`, Region, `TileValue`, terrain archive,
  event, authored-item registry, cache, callback, claim, permit, lease, or
  commit handle survives the builder boundary;
- a stable placement key identifies authored construction input, not a current
  runtime entity. It does not claim that an object or item is present, that an
  NPC remains in its source, or that dynamic state can be discarded;
- the manifest is not read by RegionManager, the private parity observer,
  pathing, collision, registration, persistence, plugins, or clients; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false. No source can be
  unloaded, removed, evicted, reconstructed, or rolled back through this slice.

Automated and private-runtime validation evidence:

- the compiled fixture proves all five definition families, exact field
  retention, stable one-based ordinals, duplicate preservation, deterministic
  source ordering, lookup, immutable lists, explicit generation-zero
  emptiness, count equivalence, invalid-input refusal, and completed-builder
  refusal;
- source guards prove all definitions are captured only along the successful
  configured construction paths, while RegionManager and the observer cannot
  access the manifest;
- the complete layered-map suite passes 118 tests across 52 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 752 core and 488 plugin sources
  successfully; and
- a normal private `myworld_dev` startup loaded all 28,732 configured objects,
  3,775 NPC spawns, and 882 ordinary ground items and reached the online state,
  exercising the complete-manifest equivalence gate across the same 33,532
  origins measured in Slice 52; and
- two consecutive normalizations produce identical source
  `9ea223c7f18ad3a6529e2a0389fe4254f2c6a521f0c99d9e9e8d7599501da71f`,
  inventory
  `54ea3fb6044728dd552d2904c515d4691ca129538de502d44bbcaf720da71887`,
  classification
  `2372075613128bee3e9cbfbae795fdfd74b9638b7dc85b3280082fa402d2ed12`,
  and occurrence
  `d8e52da68e1f019347eb2198ceef16285d09c9d1b20b88bf990af174b1a7b868`
  fingerprints, with the existing 1,771 terrain sectors, 49,816 placement
  records, 20 transition edges, and one explicitly unresolved coordinate
  unchanged.

Status: implemented and runtime-validated. No owner route is required because
the manifest is inert and has no client-visible or lifecycle consumer.

### Slice 54: Authored placement dependency envelopes

Objective: identify where a successful authored placement can reach beyond its
anchor packed source before any reload design assumes one-source ownership.
The result is a conservative dependency inventory, not a decision to retain all
reached Regions together.

Implemented:

- immutable
  `LayeredPackedRegionAuthoredPlacementDependencyInventory` generations with
  the exact same source coordinates, per-source ordering, construction family,
  and one-based ordinal identities as Slice 53;
- conservative object-footprint envelopes from the successfully constructed
  object's existing boundary calculation, always including its actual anchor;
- NPC roaming envelopes that normalize definition min/max ordering and always
  include the authored start tile, regardless of malformed or asymmetric
  source bounds;
- explicit anchor-only ground-item envelopes, while harvesting conversions
  retain object-footprint semantics;
- checked tile bounds, packed-source bounds, source containment, affected
  source counts, cross-source flags, deterministic source ordering, immutable
  lists, binary lookup, and the same source/placement budgets as Slice 53;
- aggregate counts and maxima separated into object-footprint, NPC-roaming, and
  anchor-only families; and
- a mandatory generation/source/ordinal/family alignment gate against the
  completed Slice 53 manifest before the dependency inventory is published.

Private configured-world findings:

- all 33,532 manifest placements have aligned dependency envelopes producing
  35,305 packed-source references;
- 28,875 object-footprint placements produce 28,887 references. Only 12 cross
  a packed-source boundary and none reaches more than two sources;
- 3,775 NPC spawn definitions produce 5,536 roaming references. 1,007 cross a
  packed-source boundary and the widest definition spans 64 sources;
- all 882 ordinary ground-item definitions are anchor-only and produce exactly
  882 references; and
- therefore object reconstruction has a small, explicit neighbor-coupling
  problem, while NPCs require stable spawn identity and mobile runtime
  ownership. Treating every theoretical NPC roaming source as a hard terrain
  dependency would create excessive retention and is rejected.

Safety boundary:

- dependency recording observes only successful population values and copies
  integer bounds, kinds, ordinals, and booleans;
- no Entity, Region, tile, archive, definition object, event, registry, cache,
  callback, claim, permit, lease, or commit handle survives the builder;
- an envelope is conservative reach evidence, not proof that a placement is
  active anywhere inside it and not authorization to acquire or retain every
  source in the rectangle;
- RegionManager, diagnostics, collision, pathing, plugins, persistence, and
  clients cannot read or act on the inventory; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false. No source can be
  loaded, unloaded, removed, evicted, reconstructed, or rolled back here.

Automated and private-runtime validation evidence:

- the compiled fixture proves exact aggregate/family counts, cross-source
  detection, stable ordinals, deterministic ordering, lookup, immutable lists,
  manifest alignment and mismatch refusal, bounds/family validation,
  generation-zero emptiness, and completed-builder refusal;
- source guards prove object, NPC, item, and harvesting envelopes are recorded
  in the same successful order as their manifest definitions, while
  RegionManager and the observer cannot access the inventory;
- the complete layered-map suite passes 120 tests across 53 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant private launch compiles 753 core and 488
  plugin sources, passes the full 33,532-entry alignment gate, logs the family
  findings above, and reaches the online state normally; and
- two consecutive normalizations produce identical source
  `679b03b3267ea5e209cc2b32a4538d13f84c9a0a42b34c5d9fa29f504f10c230`,
  inventory
  `b84fac3dae4e1391305e6e1baef476e6bc2384cc1ff45d49aba58132fc9b34e9`,
  classification
  `169aaac82f0ab853d1342ae37c575145dda8b0dfce48b5cea01240c2ca5d2c4d`,
  and occurrence
  `d8e52da68e1f019347eb2198ceef16285d09c9d1b20b88bf990af174b1a7b868`
  fingerprints, with terrain, placement, transition, and unresolved-coordinate
  totals unchanged.

Status: implemented and runtime-validated. No owner route is required because
the inventory is inert and has no client-visible or lifecycle consumer.

### Slice 55: Generation-fenced authored placement identity

Objective: formalize the stable authored address needed to follow mobile or
temporarily replaced content without yet attaching provenance to runtime state.

Implemented:

- immutable `LayeredAuthoredPlacementIdentity` values containing population
  generation, packed source X/Y, one-based source ordinal, and construction
  family;
- checked positive generation and ordinal budgets, non-negative current packed
  source coordinates, non-null family, value equality/hash semantics, and a
  deterministic compact technical string;
- canonical identities constructed inside the Slice 53 manifest builder and
  owned by each immutable placement entry, while existing ordinal/family
  accessors delegate to that identity; and
- generation fencing so an entity, callback, or registry value from an older
  whole-world population cannot silently equal a placement in a later pass.

Runtime lifecycle audit:

- authored NPC death/respawn reuses the same `Npc` and `NPCLoc`, so identity can
  survive ordinary movement, death, and respawn without making roaming Regions
  owners of the spawn;
- authored ground-item respawn creates a new `GroundItem` from the retained
  `ItemLoc` held by the authored registry callback, so identity must be carried
  by both the definition and each active instance;
- authored game objects frequently transition to temporary replacement
  instances, while delayed restoration usually reconstructs from the original
  `GameObjectLoc`; a central replacement propagation policy is therefore
  needed to avoid losing identity during the temporary state; and
- configured startup objects can intentionally replace earlier definitions at
  the same tile. Those remain distinct manifest identities, and ordinary
  collision replacement must not automatically transfer the earlier identity
  onto the later authored definition.

Safety boundary:

- the identity contains values only and has no Entity, definition, Region,
  tile, archive, event, registry, cache, callback, claim, permit, lease, or
  commit handle;
- `Entity`, `GameObjectLoc`, `NPCLoc`, and `ItemLoc` remain unchanged and cannot
  carry the identity in this slice;
- no runtime registry, lookup, teardown, reconstruction, observer payload, or
  persistence format consumes it; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false.

Automated validation evidence:

- the compiled fixture proves equality/hash behavior, generation/family/
  ordinal fencing, exact fields and technical text, invalid-input refusal, and
  canonical manifest identity for duplicate definitions; and
- source guards prove runtime entities and location definitions remain unaware
  of the new value;
- the complete layered-map suite passes 122 tests across 54 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 754 core and 488 plugin sources
  successfully; and
- two consecutive normalizations retain Slice 54's identical source,
  inventory, classification, and occurrence fingerprints and unchanged world
  totals, confirming the inert identity value does not alter normalized map
  inputs or outputs.

Status: implemented and automated-validated. Runtime attachment remains a
separately gated slice.

### Slice 56: Observational runtime provenance attachment

Objective: let existing authored runtime state carry its canonical Slice 55
identity through ordinary lifecycle transitions without creating a lookup
registry or changing any gameplay/lifecycle decision.

Implemented:

- a small `LayeredAuthoredPlacementIdentitySlot` whose initial state is absent,
  whose first assignment is retained, whose equal reassignment is idempotent,
  and whose null or conflicting reassignment is refused;
- one slot on `Entity`, `GameObjectLoc`, `NPCLoc`, and `ItemLoc`, with read and
  assign operations but no clear, replace, mutation, or lifecycle operation;
- constructors for `GameObject`, `Npc`, and authored `GroundItem` copy a
  definition identity onto the new entity when present;
- the startup population pass assigns each manifest builder's immediately
  prior canonical identity to the accepted source definition and constructed
  entity after registration succeeds;
- harvesting conversion assigns the same harvesting-family identity to both
  its source item definition and its constructed scenery definition/entity;
- authored ground-item delayed respawn inherits identity naturally because its
  retained `ItemLoc` constructs the replacement; NPC movement/death/respawn
  keeps identity because the same entity and `NPCLoc` survive; and
- `World.replaceGameObject()` explicitly copies an old authored identity to a
  temporary replacement definition/entity before unregistering the old object,
  allowing the existing delayed restoration path to inherit it.

Explicit non-propagation rule:

- ordinary `registerGameObject()` collision replacement does not copy
  identity. This is necessary because configured duplicate/replacement
  definitions at one tile are distinct authored placements and each receives
  its own manifest ordinal only after successful registration;
- an unassigned dynamic entity remains unassigned unless it is the explicit
  replacement of an authored object; and
- an old-generation identity remains detectable but receives no authority.
  No runtime consumer can act on it in this slice.

Safety boundary:

- the slots carry one immutable value only; they contain no Region, tile,
  archive, event, registry, cache, callback, claim, permit, lease, or commit
  handle;
- attachment does not change entity equality, indexing, location, visibility,
  collision, combat, respawn timing, registration, persistence, packets, or
  client state;
- RegionManager and the private observer remain unaware of provenance, and no
  global identity-to-entity registry exists; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false.

Automated and private-runtime validation evidence:

- the compiled fixture proves absent initial state, idempotent equal assignment,
  null/conflict refusal without mutation, exact manifest builder cursor
  identity, and refusal before a record or after builder completion;
- source guards prove all three authored entity constructors inherit definition
  identity, startup assignment follows successful construction, explicit
  object replacement propagates before unregister, ordinary collision
  registration does not propagate, and RegionManager/observer remain unaware;
- the complete layered-map suite passes 124 tests across 55 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build and private launch compile 755 core and
  488 plugin sources, populate and align all 33,532 authored definitions, and
  reach the online state normally; and
- two consecutive normalizations produce identical source
  `e9e727c6547256db100204887192c8e8175393fa5e1ca07ea4c63c342aecec19`,
  inventory
  `a25a035b4381fed98a3b1f94fffadb9c19dd027b7540d570756522e6dcc03bb9`,
  classification
  `5ef669d5745beb4a910c8fd482afe78e22e986b71d69fc2c21eb6d9a1e557698`,
  and occurrence
  `d8e52da68e1f019347eb2198ceef16285d09c9d1b20b88bf990af174b1a7b868`
  fingerprints, with world totals unchanged.

Status: implemented and runtime-validated. Provenance census diagnostics remain
the next gate before any registry or lifecycle consumer.

### Slice 57: Bounded authored runtime provenance census

Objective: compare the exact authored identities expected for each bounded
packed retirement-safety source with current runtime identity metadata, while
preserving the distinction between an authored origin and an entity's current
location or temporary lifecycle state.

Implemented:

- an immutable `LayeredPackedRegionAuthoredProvenanceObservation` built only
  from the detached placement manifest, the exact safety-source list, primitive
  runtime identity observations, IDs, packed source coordinates, and active
  flags;
- one expected-identity classification per safety source: exactly matched,
  absent, or duplicated, plus separately counted stale-generation and
  unrecognized runtime identities;
- active, inactive-respawn, at-authored-source, away-from-authored-source, and
  temporary authored-object replacement counts, with exact expected/runtime
  totals for scenery, boundaries, NPCs, ground items, and harvesting scenery;
- a Region-local read-only scan for active scenery and ground items plus a
  RegionManager scan for active or respawning authored NPCs, performed under
  the existing layered lifecycle lock and reduced immediately to detached
  counts; and
- additive `layered-map-parity-event-v17` JSONL diagnostics with a closed
  schema. v17 retains v16 intact and marks the new payload
  `identityMetadataOnly=true`, `entityRegistry=false`, and
  `lifecycleAuthority=false`.

Safety boundary:

- the census retains no entity, Region, collection, tile, archive, event,
  callback, cache, claim, permit, lease, or commit handle;
- it records at most 524,288 runtime identity observations and at most the
  existing 8,192 bounded safety sources;
- NPCs roaming into neighboring packed sources remain associated with their
  authored origin but do not cause those neighbors to retain terrain;
- absent and replacement states are diagnostic lifecycle evidence, not proof
  that an entity may be removed or reconstructed; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false.

Validation status:

- a compiled fixture proves exact expected/matched/absent/duplicate arithmetic,
  active/inactive and origin/roaming distinctions, temporary replacement
  detection, stale/unrecognized refusal, family totals, immutable output, and
  builder input/lifecycle validation;
- the authoritative bundled-Ant build compiles 756 core and 488 plugin sources;
  and
- the closed v17 schema resolves against the immutable v11-v16 registry, and
  the complete layered-map suite passes 126 tests across 56 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- two consecutive normalizations retain identical source
  `b18b6a5627d2b806bd21383216f5cad4bf1bc3ead239d6bf9fbb3b06a08b9fed`,
  inventory
  `b80658b28fc18987686ace066ff3d83ad153939b90017ba74ac721b30662aad5`,
  classification
  `403454e7d68265c8e9134afa4bffe461ac1e16fd078c26eda6f9d4cb86ce44ee`,
  and occurrence
  `d8ee498c4d527361da224bcb0efc786295b5e4f73c095f561985cd6f5e8c64f4`
  fingerprints; and
- the private server compiles 756 core and 488 plugin sources, populates all
  33,532 authored definitions, and reaches its isolated online state normally.

Owner validation evidence (2026-07-19):

- the completed trace contains 115 contiguous v17 records; every record
  validates against the closed schema, and every provenance payload satisfies
  exact expected-state, runtime-state, and active-location arithmetic;
- the initial restored farm wave covered 1,982 authored placements with all
  1,982 identities matched, zero absent/duplicate/stale/unrecognized
  identities, and ten valid NPCs currently roaming outside their authored
  packed sources;
- after the owner killed a chicken and picked up the authored egg, packed
  source `(2,12)` returned to all 155 expected identities matched, including
  all 17 NPC and three ground-item identities. The elapsed marker did not catch
  the intermediate absent/respawning state, but the restored ground-item
  entity and retained NPC entity were both recognized after their existing
  respawn paths completed;
- opening the authored generic chest at `(141,471)` produced one matched
  temporary replacement in packed source `(2,9)`: all 257 expected identities
  remained matched, the runtime object ID differed exactly once, and no stale,
  duplicate, or unrecognized identity appeared;
- the wider bounded safety set exposed four unrelated currently absent
  authored objects—three scenery and one boundary—and one other matched
  replacement. These states may be ordinary open/depleted/temporarily removed
  world interactions, but count-only v17 evidence cannot name their exact
  identity or construction coordinate; that is the next diagnostic limitation
  to resolve before considering a persistent identity registry; and
- the owner reported no visual, movement, interaction, or restoration issue,
  and every source remained lifecycle-blocked because per-Region reload support
  is still false.

Status: implemented and owner-validated. No lifecycle consumer is authorized.

### Slice 58: Bounded authored provenance anomaly details

Objective: make every anomalous Slice 57 count actionable in private traces by
attaching a stable, AI-readable identity and construction location while
retaining no runtime or lifecycle authority.

Implemented:

- the immutable authored-provenance observation now emits details for
  `ABSENT`, `DUPLICATE`, `REPLACEMENT_OBJECT`, `STALE_GENERATION`, and
  `UNRECOGNIZED_IDENTITY` classifications;
- recognized details include the generation-fenced packed source, source
  ordinal, construction family, authored definition ID, expected constructed
  entity ID, and exact packed construction X/Y;
- runtime-observed details additionally include one detached runtime ID,
  current packed source, active flag, total instance count, and replacement
  instance count, while absent details use explicit no-runtime state;
- detail ordering is deterministic by identity and anomaly kind; output is
  capped at 4,096 immutable entries and reports the exact number of further
  details dropped; and
- additive `layered-map-parity-event-v18` diagnostics serialize the detail list
  through a closed schema while leaving the v17 contract intact for existing
  traces.

Safety boundary:

- a detail contains only immutable identity values and primitive manifest or
  runtime observations; it retains no entity, Region, collection, tile,
  archive, event, callback, cache, claim, permit, lease, or commit handle;
- collection still occurs only for Slice 57's exact bounded safety-source set
  under the existing lifecycle lock, with the same 524,288 runtime-observation
  ceiling;
- the 4,096-entry cap bounds diagnostic output and memory growth, and the
  dropped count makes truncation explicit rather than silently hiding it;
- details remain evidence only: absence cannot authorize reconstruction,
  replacement cannot authorize teardown, and stale or unrecognized metadata
  cannot alter current provenance; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false.

Automated validation status:

- the compiled provenance fixture proves all five anomaly kinds, exact
  detached manifest/runtime fields, deterministic ordering, immutable output,
  and exact cap/drop arithmetic;
- the observer fixture emits and validates a non-null absent detail against the
  closed v18 schema while the immutable v17 schema continues to reject fields
  outside its prior contract; and
- the complete layered-map suite passes 129 tests across 58 focused files;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- the authoritative bundled-Ant build compiles 756 core and 488 plugin sources;
  and
- two consecutive normalizations retain identical source
  `671b7e116c15e38f871a61b34bd244693d7078e94574fe521e9ba4585a4741b7`,
  inventory
  `b80658b28fc18987686ace066ff3d83ad153939b90017ba74ac721b30662aad5`,
  classification
  `403454e7d68265c8e9134afa4bffe461ac1e16fd078c26eda6f9d4cb86ce44ee`,
  and occurrence
  `d8ee498c4d527361da224bcb0efc786295b5e4f73c095f561985cd6f5e8c64f4`
  fingerprints, with world totals unchanged.

Owner validation evidence (2026-07-20):

- the owner completed the focused farm-to-far transition and captured four
  contiguous v18 records—start, teleport, marker, and stop—with no reported
  test interruption;
- all four records validate against the closed v18 schema, sequences are
  contiguous, and the marker's expected classification, runtime activity,
  active-location, expected-family, runtime-family, and detail-count arithmetic
  are exact;
- the marker covers 42 exact safety sources and 5,801 expected placements:
  5,797 matched, four absent, zero duplicate, 5,797 runtime instances, zero
  replacements, zero stale-generation identities, zero unrecognized
  identities, four emitted details, and zero dropped details;
- packed source `(1,14)`, ordinal 74 is the legacy table (`TABLE`, ID `3`) at
  `(79,693)`. With harvesting enabled, the later authored cabbage ground item
  (`CABBAGE`, item ID `18`) at that coordinate constructs `CABBAGE` scenery
  (ID `1262`), and normal collision registration supersedes the table;
- packed source `(2,11)`, ordinal 9 is the base doorframe (`DOORFRAME`, boundary
  ID `1`) at `(115,532)`. The later custom-quest boundary file deliberately
  supplies a closed `DOOR` (ID `2`) at the same coordinate and direction, so
  registration supersedes the base doorframe;
- packed source `(3,13)`, ordinal 106 is a base leafy tree (`LEAFY_TREE`, ID
  `1`) at `(177,655)`. `SceneryLocsOther.json` later repeats the identical tree
  at the same coordinate, so the later definition supersedes the earlier one;
- packed source `(4,13)`, ordinal 7 is the legacy table (`TABLE`, ID `3`) at
  `(222,624)`. The later authored tomato ground item (`TOMATO`, item ID `320`)
  constructs `TOMATO_PLANT` scenery (ID `1268`) under harvesting and supersedes
  the table; and
- the same four details persist unchanged in marker and stop evidence. Their
  exact correspondence to configured load order and `World.registerGameObject`
  collision behavior rules out stale identity, missed respawn, roaming,
  transient interaction, or census loss. The current manifest records replay
  history correctly, but its full construction set is too broad to serve
  directly as the expected final-live identity set.

Status: implemented and owner-validated. The four absences are classified as
population-time supersession evidence, not runtime provenance failures. No
lifecycle consumer is authorized.

### Slice 59: Detached population supersession projection

Objective: preserve the complete authored placement manifest as construction
replay history while deriving an exact final-live expectation after normal
startup collision registration supersedes earlier object identities.

Implemented:

- whole-world population now observes the same scenery-anchor or
  boundary-anchor-and-direction collider that `World.registerGameObject` will
  replace, then records predecessor and successor authored identities after
  the successor has received its manifest identity. The pre-registration probe
  reads the immutable location definition because entity location is assigned
  by registration itself;
- an immutable generation-fenced population outcome resolves both identities
  against the completed manifest and retains only detached primitive placement
  metadata: source ordinal, construction family, definition/entity IDs,
  coordinate, direction, object type, and collision family;
- the complete manifest remains unchanged and ordered. The outcome separately
  reports manifest count, supersession count, and final-live expectation count;
- the private runtime provenance census includes superseded manifest identities
  in its recognized address set but excludes them from absence and expected
  family arithmetic. If a predecessor is unexpectedly present at runtime it is
  counted as `SUPERSEDED_IDENTITY_PRESENT`, not silently ignored; and
- additive `layered-map-parity-event-v19` diagnostics report manifest,
  superseded, and final-live counts plus a deterministic bounded list of
  predecessor/successor collision details. The immutable v18 contract remains
  available for existing traces.

Safety boundary:

- the outcome retains no entity, Region, collection owned by runtime storage,
  tile, archive, event, callback, cache, claim, permit, lease, or commit handle;
- collision lineage is collected only during the existing single population
  pass and published only after the manifest and dependency equivalence gates
  succeed;
- the manifest is not rewritten, source definitions are not deleted, and
  startup collision precedence remains unchanged;
- v19 details are capped at 4,096 for each provenance observation and report
  exact overflow; and
- supersession evidence grants no loading, reconstruction, teardown, reload,
  retirement, or eviction authority. `LAYERED_PACKED_REGION_RELOAD_SUPPORTED`
  remains false.

Automated validation status:

- a compiled fixture proves scenery-to-harvesting and boundary-to-boundary
  collision projection, detached metadata, immutable ordering, manifest versus
  final-live arithmetic, zero false absences, and explicit detection if a
  superseded predecessor reappears;
- the authoritative bundled-Ant build compiles 757 core and 488 plugin sources;
- the closed v19 observer fixture validates against the immutable v11-v18
  schema registry, and the complete layered-map suite passes 132 tests across
  58 focused files. The staged coordinate-package boundary and both Player and
  command-local provenance selectors are guarded explicitly;
- all 13 World Builder discovery tests and the standalone-layout guard pass;
- two consecutive normalizations retain identical source
  `da9ba5080a3e7bb06a1cc8b28f7db8a34364a18457796be60c3b6e132a8c9c7e`,
  inventory
  `12beea5c7ed88e6de7690bb3178cc1b6dd9fa6b962506b48560319f7ae68d9f1`,
  classification
  `d41a95db44e9e8967cb70928f225f38e8fe9aeeef4f86e4ef75658d4a95231ee`,
  and occurrence
  `d8ee498c4d527361da224bcb0efc786295b5e4f73c095f561985cd6f5e8c64f4`
  fingerprints; and
- the private server rebuilds 757 core and 488 plugin sources, populates all
  33,532 manifest placements, records 17 whole-world startup supersessions,
  derives 33,515 final-live expectations, and reaches its isolated online state
  normally; and
- the owner-accepted `slice59-command-fix` v19 marker covers 42 exact safety
  sources and 5,801 manifest placements, projects four deterministic
  predecessors out of the final-live set, matches all 5,797 expectations, and
  reports zero absent, duplicate, stale-generation, unrecognized,
  superseded-runtime, or other anomaly details. All four bounded
  predecessor/successor explanations were retained with zero detail overflow.
  Earlier owner attempts exposed a duplicated `Development` command source
  that called the legacy no-outcome overload; forwarding the completed outcome
  there produced the accepted result.

Status: implemented and owner-validated. No registry or lifecycle consumer is
authorized.

### Slice 60: Inert final-live reconstruction recipe

Objective: derive exact, ordered per-source reconstruction inputs from the
accepted final-live population model without constructing, registering,
removing, loading, or retaining any runtime entity.

Implemented:

- a generation-fenced immutable recipe derives only after the placement
  dependency inventory proves exact source/order/family alignment with the
  completed manifest and the population outcome proves generation, count, and
  metadata alignment;
- every retained entry is owned by its authored anchor source, preserves its
  original duplicate-safe source ordinal and order, and pairs the immutable
  primitive placement definition with its matching conservative dependency
  envelope;
- population-time collision predecessors remain in manifest replay history but
  are excluded from the recipe. Ordinal gaps are retained deliberately, so the
  recipe cannot silently renumber an identity or change collision precedence;
- per-source and whole-world arithmetic distinguishes manifest placements,
  superseded predecessors, and final-live recipe entries. Cross-source entry
  counts, affected-source references, and maximum fanout remain explicit; and
- `WorldPopulator` publishes the completed recipe only after the existing
  manifest, dependency, and outcome gates succeed and emits one startup summary
  for private/runtime verification.

Safety boundary:

- the recipe references only already immutable detached placement and
  dependency values. It retains no entity, Region, tile, archive, event,
  registry, cache, callback, claim, permit, lease, commit, teardown, loading,
  or rollback handle;
- source ownership follows the authored anchor even when an object footprint
  or NPC roaming envelope reaches neighboring sources. Cross-source reach is a
  prerequisite to solve, not authority to load or mutate a neighbor;
- this slice does not select a safety-source subset, calculate dependency
  closure, construct terrain or collision, bind events, compare current state,
  or expose a lifecycle command; and
- `LAYERED_PACKED_REGION_RELOAD_SUPPORTED` remains false. Recipe availability
  does not change any safety blocker or lifecycle-ready result.

Automated validation status:

- a compiled fixture covers scenery-to-harvesting and boundary-to-boundary
  supersession, two authored sources, cross-source object and NPC reach,
  anchor-only reach, final-live arithmetic, original ordinal gaps, exact source
  lookup, immutable lists, primitive metadata retention, and misaligned-
  generation refusal;
- the staged coordinate-package boundary includes the new inert value and
  source guards reject entity, RegionManager, registration, and unregistration
  dependencies; and
- the authoritative bundled-Ant build compiles 758 core and 488 plugin sources,
  and the complete layered-map suite passes 135 tests across 59 focused files;
  and
- the private server populates normally and derives 33,515 recipe entries from
  33,532 manifest placements across 366 authored sources after excluding all
  17 superseded predecessors. The retained recipe contains 1,019 cross-source
  entries, 35,288 affected-source references, and a maximum fanout of 64,
  consistent with removing 17 anchor-local predecessor references from the
  aligned dependency inventory.

Status: implemented and private-runtime validated. No lifecycle authority is
authorized.

### Slice 61: Safety-source reconstruction requirements

Objective: correlate the inert final-live recipe with one exact packed-source
safety selection and describe its dependency closure without interpreting that
evidence as a load request or lifecycle permit.

Implemented:

- one bounded immutable observation preserves whole-recipe generation and
  manifest/supersession/final-live context while projecting recipe arithmetic
  onto the safety sources in their existing exact order;
- each safety-source entry reports manifest, superseded, final-live,
  cross-source, and affected-reference counts plus the unique dependency
  sources required by its owned recipe entries;
- the selection-wide union of dependency sources is deterministic and sorted
  by packed coordinate. Each requirement reports whether it is already in the
  safety selection, whether it has authored recipe content, how many selected
  owner sources depend on it, and how many placement envelopes reference it;
- per-source and selection-wide `dependencyClosed` results mean only that every
  conservative dependency coordinate appears in the observed selection. Empty
  recipe sources are closed by definition and cannot confer authority; and
- selected-source and unique-requirement budgets are separate, explicit, and
  refusal-based. No details are truncated into a misleading closed result.

Safety boundary:

- the projection retains no entity, Region, tile, archive, event, registry,
  cache, callback, claim, permit, lease, commit, construction, teardown,
  loading, or rollback handle;
- neither a missing requirement nor a closed result changes retirement
  readiness, source ownership, residency, or the permanent
  `RELOAD_PATH_UNAVAILABLE` blocker;
- dependency rectangles are enumerated only into detached packed-coordinate
  evidence within an explicit bound; and
- there is no runtime consumer or diagnostic schema in this slice. Additive
  private trace exposure remains a separately checked step.

Automated validation status:

- a compiled fixture proves exact one-source projection for an NPC whose
  roaming envelope crosses into a second authored source, deterministic
  selected/missing requirement details, an anchor-only closed selection,
  immutable outputs, and refusal of undersized selection or requirement
  budgets; and
- source guards prove the value has no entity or RegionManager dependency and
  describes closure explicitly as evidence rather than a load request; and
- the authoritative bundled-Ant build compiles 759 core and 488 plugin sources,
  and the complete layered-map suite passes 138 tests across 60 focused files.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

### Slice 64: Fixed-point cohort diagnostics

Objective: expose Slice 63's bounded cohort analysis through the opt-in private
observer so real retirement selections reveal recursive authored expansion and
support-only perimeter size without acquiring either set.

Implemented:

- additive `layered-map-parity-event-v21` records retain the complete v20 event
  and add nullable `packedRegionAuthoredReconstructionCohort` evidence;
- cohort totals report seed, expanded-authored, authored-content, final-live
  placement, conservative reach, requirement, external-support, and maximum
  expansion-round counts plus authored-closure and self-contained status;
- each seed or expanded source reports its role, expansion round, recipe/content
  presence, final-live arithmetic, and direct cohort-versus-support dependency
  counts;
- the deterministic requirement union distinguishes cohort sources, recipe
  sources, sources with final-live authored content, and external support-only
  coordinates with exact owner and placement-reference counts; and
- both the Player session-rebind path and development-command start path derive
  the cohort from the completed `WorldPopulator` recipe and exact same safety
  assessment.

Safety boundary:

- cohort and requirement budgets are passed independently at the observer's
  established hard source limit; overflow refuses the event rather than
  truncating a fixed point or support perimeter;
- schema-v21 declares `identityMetadataOnly=true`, `entityRegistry=false`, and
  `lifecycleAuthority=false`, and the v11-v20 contracts remain immutable;
- authored closure or self-containment is descriptive evidence only. The
  observer neither acquires support sources nor validates terrain, collision,
  events, teardown, reload, or rollback; and
- the analysis and JSON contain no entity, Region, tile, archive, registry,
  callback, lifecycle, or transaction handle.

Automated validation status:

- the executable observer fixture emits a non-null self-contained cohort and
  validates its exact totals, entry, requirement, and inert flags against
  schema-v21;
- schema/source guards verify the additive nullable contract, serializer,
  refusal budgets, inert flags, and both runtime source paths;
- the complete layered-map suite passes 149 tests across 63 focused files;
- the authoritative bundled-Ant build compiles 760 core and 488 plugin
  sources;
- the accepted private trace contains six schema-v21 events and both owner
  markers validate against the additive v21 contract;
- `slice64-narrow` begins with 6 authored safety seeds and reaches 61 authored
  sources after adding 55 sources over 10 expansion rounds; the cohort contains
  11,677 final-live placements, 388 cross-source placements, 12,483 affected
  source references, and 75 requirements split into 61 cohort requirements and
  14 external support-only requirements;
- `slice64-broad` begins with 42 safety seeds, including 3 content-empty seeds,
  and reaches 65 sources after adding 23 authored sources over 5 expansion
  rounds; the cohort contains 11,781 final-live placements, 400 cross-source
  placements, 12,611 affected source references, and 78 requirements split into
  64 cohort requirements and 14 external support-only requirements;
- both cohorts are authored-closure complete but not fully self-contained. All
  14 external requirements have no authored recipe or final-live authored
  content, so they terminate authored expansion while remaining explicit
  support preconditions; and
- the narrow and broad seed selections respectively matched all 546 and 6,666
  selected final-live identities, with zero absences, duplicates, anomalies, or
  dropped details. The owner observed normal scenery, NPCs, collision, and
  loading throughout the route.

Status: implemented and owner-validated. The narrow result proves the authored
dependency component cannot be treated as a small local reconstruction unit.
No lifecycle authority is authorized.

### Slice 65: Cohort dependency-edge attribution

Objective: explain why a small retirement seed set reaches a large authored
cohort before considering any reconstruction consumer or changing the
deliberately conservative dependency model.

Implemented:

- a detached attribution value consumes an already completed recipe and cohort
  analysis, then verifies their generation and every source and requirement
  count before producing evidence;
- aggregate kind records separate unique placements, cross-source placements,
  affected-source references, cross-source references, expansion-frontier
  references, and external-support references for every observed construction-
  kind and dependency-kind pair;
- exact sorted owner-to-requirement edges retain primitive coordinates,
  expansion rounds, self/cohort/frontier/support roles, total placement
  references, and per-kind reference counts;
- one compact bridge record per final-live cross-source placement retains only
  its authored identity generation and ordinal, definition/entity IDs,
  conservative source envelope, and counts of cohort, expansion-frontier, and
  external-support targets; and
- edge and bridge-placement budgets refuse the entire result rather than
  truncating an attribution into misleading arithmetic.

Safety boundary:

- the analysis accepts immutable detached recipe and cohort values and exposes
  primitive metadata only; it has no entity, Region, tile, archive, event,
  registry, cache, callback, claim, permit, lease, transaction, commit, load,
  teardown, reconstruction, or rollback handle;
- frontier attribution describes the deterministic round relationship; it is
  not a load order, acquisition request, or proof that the required terrain,
  collision, events, or rollback boundary exists; and
- a large NPC-roaming or object-footprint contribution is evidence to inspect,
  not permission to weaken a conservative envelope.

Validation status:

- the executable fixture proves a two-round NPC-roaming chain, exact self,
  expansion-frontier, and support-only edges, typed arithmetic, primitive
  bridge identities, immutable outputs, and refusal of undersized independent
  edge and bridge budgets; and
- source guards prove the result has no entity, RegionManager, or lifecycle
  dependency;
- the complete layered-map suite passes 152 tests across 64 focused files; and
- the authoritative bundled-Ant build compiles 761 core and 488 plugin
  sources.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

### Slice 66: Cohort attribution diagnostics

Objective: expose Slice 65's typed dependency explanation through the opt-in
private observer so the accepted real-world narrow and broad cohorts can reveal
which conservative placement envelopes connect them.

Implemented:

- additive `layered-map-parity-event-v22` records retain the complete v21 event
  and add nullable `packedRegionAuthoredReconstructionCohortAttribution`
  evidence;
- the observer passes the exact cohort object it just captured into attribution
  rather than allowing the runtime source to recompute or substitute a cohort;
- kind totals report placement, cross-source placement, affected-reference,
  cross-source-reference, expansion-frontier, and external-support counts;
- exact sorted edges report owner/requirement coordinates and expansion rounds,
  self/cohort/frontier/support roles, and nonzero construction/dependency-kind
  reference counts;
- compact bridge records identify each final-live cross-source placement by
  primitive generation, source ordinal, definition/entity IDs, conservative
  envelope, and cohort/frontier/support target counts; and
- both the Player session-rebind path and development-command start path derive
  the attribution from the completed `WorldPopulator` recipe.

Safety boundary:

- edge and bridge-placement lists have separate 8,192-entry refusal budgets;
  overflow refuses the event instead of producing incomplete attribution;
- schema-v22 declares `identityMetadataOnly=true`, `entityRegistry=false`, and
  `lifecycleAuthority=false`, while v11-v21 remain immutable contracts; and
- the observer does not acquire a requirement, change an envelope, load or
  release a Region, retain an entity, or gain reconstruction authority.

Automated validation status:

- the executable observer fixture emits a non-null self-edge attribution from
  the same completed cohort and validates its typed totals, inert flags, and
  empty bridge list against schema-v22;
- schema/source guards verify the additive nullable contract, strict list
  bounds, serializer, inert flags, and both runtime paths; and
- the complete layered-map suite passes 156 tests across 65 focused files; and
- the authoritative bundled-Ant build compiles 761 core and 488 plugin
  sources.

Private owner validation status:

- all six captured schema-v22 events validate against the additive v22 schema,
  cohort/attribution arithmetic reconciles exactly, both marked cohorts are
  authored-closure complete, and selected-source provenance contains zero
  missing, duplicate, stale-generation, unrecognized, or dropped anomalies;
- `slice66-narrow` retains the accepted 6-to-61-source cohort and attributes
  its 11,677 placements to 225 exact edges and 388 bridge placements. NPC
  roaming accounts for 333 of 334 expansion-frontier references, all 38
  external-support references, and 801 of 806 cross-source references;
- `slice66-broad` retains the accepted 42-to-65-source cohort and attributes
  its 11,781 placements to 229 exact edges and 400 bridge placements. NPC
  roaming accounts for 194 of 195 expansion-frontier references, all 47
  external-support references, and 825 of 830 cross-source references;
- static scenery contributes no expansion-frontier reference in either cohort.
  One boundary footprint contributes the sole non-NPC frontier reference in
  each cohort; and
- Gnome child/local definitions 585, 592, and 593 alone account for 128 narrow
  frontier references. Their source envelopes explain the large narrow round-8
  expansion and the corresponding broad round-1 expansion. The owner observed
  normal scenery, NPCs, collision, and loading throughout the route.

Status: implemented, automated-validated, and owner-validated. The evidence
explains the large cohort without justifying a weaker roaming envelope. No
lifecycle authority is authorized.

### Slice 67: Whole-recipe directed topology audit

Objective: determine whether authored sources outside a forward-closed cohort
can depend on sources inside it, and quantify the broader conservative graph
before any reconstruction or retirement consumer is considered.

Implemented:

- one bounded detached analysis treats every final-live authored recipe source
  as a node and every owner-to-authored-requirement relationship as a directed
  edge, while retaining empty dependency coordinates as explicit external
  support rather than false authored nodes;
- the completed forward cohort is checked against the exact same recipe, and
  any forward edge to omitted authored content refuses the analysis instead of
  silently weakening the already-proved fixed point;
- iterative graph traversal reports exact weak and strong components without a
  recursion-depth dependency. Self relationships remain counted in whole-
  recipe arithmetic but do not manufacture cross-source connectivity or a
  cyclic strong component;
- source evidence records weak/strong component membership and distinguishes
  forward-cohort, conservative-connected, and incoming-only authored sources;
- kind evidence attributes authored, cross-source, external-support, direct-
  incoming, and conservative-connected references by construction and
  dependency family; and
- authored and support owner-to-requirement relationships share one explicit
  refusal budget, so the analysis cannot omit one side of the topology and
  still return a result.

Safety boundary:

- weak-component membership is deliberately conservative diagnostic evidence,
  not a proposed unit of loading, teardown, reconstruction, or rollback;
- the value retains primitive coordinates, counts, enums, and immutable lists
  only, with no entity, Region, tile, archive, event, registry, callback,
  transaction, claim, permit, lease, commit, or lifecycle handle; and
- the analysis neither changes conservative NPC roaming/object envelopes nor
  makes an incoming edge safe to quiesce.

Automated validation status:

- the executable fixture proves a two-source forward cycle, a third authored
  source with an incoming-only NPC-roaming edge, two unrelated weak components,
  one support-only coordinate, exact kind/reference arithmetic, immutable
  outputs, and full refusal at undersized source and relationship budgets;
- source guards prove the value has no entity, RegionManager, or lifecycle
  dependency;
- the complete layered-map suite passes 159 tests across 66 focused files; and
- the authoritative bundled-Ant build compiles 762 core and 488 plugin
  sources.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

### Slice 68: Whole-recipe topology diagnostics

Objective: expose Slice 67's bounded source and component topology through the
private observer so the accepted narrow and broad cohorts can be compared with
their actual incoming-only authored sources.

Implemented:

- additive `layered-map-parity-event-v23` records retain the complete v22 event
  and add nullable `packedRegionAuthoredReconstructionTopology` evidence;
- the observer passes the exact cohort it just captured into the topology
  source, while both the Player session-rebind path and development-command
  start path supply the completed `WorldPopulator` recipe;
- aggregate totals distinguish authored nodes, directed/self/cross-source
  edges, authored/support references, forward sources, touched weak components,
  incoming-only sources, and direct incoming relationships;
- bounded source entries expose only packed coordinates, placement counts,
  weak/strong component ordinals, and forward/connected/incoming roles;
- bounded kind entries attribute authored, cross-source, support, incoming, and
  connected references without serializing placement definitions; and
- bounded weak and strong component entries contain only ordinals and internal
  cross-source edge/reference totals.

Safety boundary:

- topology source and relationship budgets are independent observer constants;
  overflow refuses the event instead of truncating a component;
- schema-v23 declares `identityMetadataOnly=true`, `entityRegistry=false`, and
  `lifecycleAuthority=false`, while v11-v22 remain immutable contracts; and
- the observer neither acquires an incoming source nor treats a weak component
  as a lifecycle instruction.

Automated validation status:

- the authoritative bundled-Ant build compiles 762 core and 488 plugin sources;
- the v23 schema is a valid additive Draft 2020-12 contract; and
- focused schema, observer, runtime-wiring, and plan guards pass; and
- the complete layered-map suite passes 163 tests across 67 focused files.

Private owner validation status:

- all six captured schema-v23 events validate against the additive v23 schema;
  source, kind, weak/strong component, cohort, recipe, and provenance arithmetic
  reconciles exactly, and the route completed without a reported visual,
  loading, scenery, NPC, or collision issue;
- the whole recipe contains 366 authored source nodes split across 165 weak and
  271 strong components, with 777 directed edges (366 self and 411 cross-
  source), 35,051 authored references, and 237 support-only references. The
  largest weak component contains 123 sources while the next-largest contains
  21; the largest strong component contains 22 sources;
- NPC roaming contributes 1,524 of 1,536 whole-recipe cross-source authored
  references and every support-only reference. Scenery and boundary footprints
  contribute only 7 and 5 cross-source authored references respectively;
- `slice68-narrow` places its 61 forward authored sources inside two weak
  components of 123 and 13 sources. The conservative connected union is 136
  sources, including 75 incoming-only sources; all 10 direct incoming edges and
  all 38 direct incoming references are NPC roaming;
- `slice68-broad` contains 65 cohort sources, of which 62 have authored
  content. It touches the same 123- and 13-source components plus one isolated
  authored source, producing a 137-source connected union with the same 75
  incoming-only sources and the same 10 NPC edges/38 references; and
- both forward cohorts remain authored-dependency closed but are not weakly
  closed. The 123-source component spans packed source coordinates X `1-15`
  and Y `2-17`; recursively treating it as one lifecycle unit would couple a
  large fraction of the current world.

Status: implemented, automated-validated, and owner-validated. The result
rejects weak-component retirement as a practical design while preserving every
conservative spatial envelope. No lifecycle authority is authorized.

### Slice 69: Reconstruction dependency semantics

Objective: turn Slice 68's architectural finding into a bounded, executable
semantic split without creating a reconstruction or lifecycle consumer.

Implemented:

- the exact retirement-safety selection defines the source-local authored
  replay set; a support coordinate never imports its own unrelated recipe;
- every coordinate reached by a selected placement's existing conservative
  dependency envelope remains explicit outbound spatial-support evidence,
  including coordinates that also contain authored content;
- authored sources outside the selection whose placements can reach inward are
  reported separately as incoming owners rather than silently added to replay;
- object footprints, NPC roaming, and anchor-only dependencies are classified
  as static-footprint support, potential-mobile support, and anchor-only
  support respectively; and
- aggregate, per-source, and per-construction/dependency-kind arithmetic keeps
  replay placements, outbound references, external support, incoming
  placements, and incoming references independently reconcilable.

Safety boundary:

- selected-source, support-source, incoming-owner, and incoming-placement
  collections have independent refusal budgets; overflow cannot yield a
  partial classification;
- potential NPC reach is not active-instance evidence and does not assert that
  an NPC currently occupies, enters, or retains a selected Region;
- the immutable result retains primitive coordinates, counts, and enums only,
  with no entity, Region, tile, archive, registry, callback, claim, permit,
  lease, transaction, reconstruction, teardown, or rollback handle; and
- No lifecycle authority, recipe execution, envelope change, loading, release,
  eviction, or active-entity registry is authorized.

Automated validation status:

- the executable fixture proves that an exact selected source replays only its
  own scenery and NPC placements while preserving an authored neighboring
  coordinate as outbound support rather than recursively importing it;
- the same fixture reports the neighboring source's boundary and NPC as
  separate incoming-owner evidence and preserves their static versus
  potential-mobile meaning;
- immutable-output and four independent undersized-budget guards prove
  fail-closed behavior; and
- package/source guards keep the analysis inside the approved detached
  coordinate boundary;
- the complete layered-map suite passes 166 tests across 68 focused files; and
- the authoritative bundled-Ant build compiles 763 core and 488 plugin
  sources.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

### Slice 70: Reconstruction dependency semantics diagnostics

Objective: expose Slice 69's bounded semantic split through the opt-in private
observer so the accepted narrow and broad safety selections can be compared on
real data without creating active-instance or lifecycle state.

Implemented:

- additive `layered-map-parity-event-v24` records retain the complete v23 event
  and add nullable
  `packedRegionAuthoredReconstructionDependencySemantics` evidence;
- the observer passes the exact safety assessment used by the existing recipe,
  construction, provenance, and cohort diagnostics into the semantic source;
- aggregate totals distinguish selected replay sources and placements,
  outbound support sources and references, and external incoming owners,
  placements, and inward references;
- bounded selected-source, outbound-support, incoming-owner, and typed-kind
  entries preserve the source-local, static-footprint, potential-mobile, and
  anchor-only distinctions; and
- both the Player session-rebind path and development-command start path derive
  the analysis from the completed `WorldPopulator` recipe.

Safety boundary:

- selected, support, incoming-owner, and incoming-placement budgets are
  independent observer limits, and overflow refuses the event rather than
  truncating evidence;
- schema-v24 declares `sourceLocalReplay=true`,
  `spatialReachPreserved=true`, `activeInstanceEvidence=false`,
  `entityRegistry=false`, and `lifecycleAuthority=false`, while v11-v23 remain
  immutable contracts; and
- the observer cannot execute a recipe, acquire a dependency, alter an
  envelope, retain an NPC, or change packed Region loading or release.

Automated validation status:

- the v24 schema is an additive Draft 2020-12 contract with bounded collections
  and explicit inert flags;
- the executable observer fixture emits a non-null source-local semantics value
  and validates its aggregate, kind, inert-flag, and complete schema-chain
  representation;
- observer guards verify exact-safety capture, four independent budgets,
  serialization, and both runtime source paths;
- the complete layered-map suite passes 170 tests across 69 focused files; and
- the authoritative bundled-Ant build compiles 763 core and 488 plugin
  sources.

Private owner validation status:

- all 35 schema-v24 records validate against the complete additive schema
  chain; 8 records contain semantic evidence, sequences `1-35` are contiguous,
  and every exact-safety, selected-source, support, incoming-owner, typed-kind,
  and inert-flag invariant reconciles;
- `slice70-narrow` contains 6 selected authored replay sources with 546
  placements, 13 outbound support sources including 7 external sources, and 6
  incoming owners contributing 16 placements/16 inward references;
- `slice70-broad` contains 42 selected sources, 39 with authored content, and
  6,666 replay placements. Its 56 outbound support sources include 15 external
  sources, while 11 incoming owners contribute 52 placements/81 inward
  references;
- all 34 narrow and 164 broad external outbound references are NPC roaming,
  as are every incoming placement/reference. Static footprints and anchor-only
  entries create no external relationship in either labeled selection;
- outbound support and incoming ownership overlap only partially, confirming
  that they are independent directional relationships rather than a recursive
  replay closure; and
- the owner walked and inspected scenery, NPCs, interaction, collision, and
  loading after the broad capture and reported no issue.

Status: implemented, automated-validated, and owner-validated. No lifecycle
authority is authorized.

### Slice 71: Active NPC residency classification

Objective: distinguish authored NPC replay ownership from current packed
residency using one bounded point-in-time census, without creating a runtime
entity registry or granting the result any arrival, retention, or lifecycle
role.

Implemented:

- a detached census input records only authored identity, runtime NPC ID,
  current packed source coordinate, and active state for each observed NPC;
- valid identity requires the exact completed recipe generation, NPC
  construction kind, known recipe identity, and expected runtime NPC ID;
  missing, stale, non-NPC, unknown, and runtime-ID-mismatched identities remain
  separate unresolved statuses;
- relevant active instances are classified independently as selected-owner
  inside, selected-owner outside, external-owner inside, unresolved inside, or
  unresolved with a claimed selected owner outside;
- whole-census totals retain active/inactive, recognized/unrecognized,
  unique/duplicate recognized identity, relevant/irrelevant, and identity-
  status arithmetic; and
- inactive entries remain explicit census evidence but cannot be classified as
  active residency.

Safety boundary:

- independent instance and relevant-detail budgets refuse the complete
  observation on overflow rather than truncating it;
- the immutable result contains detached values only and imports neither live
  entities nor Region storage;
- duplicate recognized instances are reported, not silently collapsed, and an
  invalid identity never invents authored ownership; and
- No lifecycle authority, entity registry, arrival gate, retention decision,
  recipe execution, loading, release, eviction, movement, roaming, respawn, or
  combat behavior is created or changed.

Automated validation status:

- the executable fixture covers selected-owned NPCs both inside and outside,
  an external-owned NPC inside, unresolved inside/outside cases, active and
  inactive instances, duplicate identities, and exact identity-status totals;
- immutable-output and independent undersized-budget checks prove fail-closed
  behavior; and
- source guards keep this as point-in-time census evidence with explicit inert
  registry, arrival-gate, and lifecycle flags;
- the complete layered-map suite passes 173 tests across 70 focused files; and
- the authoritative bundled-Ant build compiles 764 core and 488 plugin
  sources.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

### Slice 72: Active NPC residency diagnostics

Objective: expose Slice 71's owner-versus-current-residency classification
through additive private schema-v25 evidence so authored potential roaming can
be compared with actual active NPC locations on the accepted narrow and broad
routes.

Implemented:

- `layered-map-parity-event-v25` retains every v24 field and adds nullable
  `packedRegionActiveNpcResidency` evidence;
- RegionManager takes one synchronized snapshot from the existing world NPC
  collection, copies one stable location per NPC, and detaches authored
  identity, runtime ID, packed source, and active state before classification;
- every observed NPC is retained in census arithmetic, including instances
  without authored identity, while only bounded relevant active-instance
  details are serialized;
- aggregate fields preserve active/inactive, recognized/unrecognized,
  unique/duplicate identity, relevant/irrelevant, selected-owner-inside,
  selected-owner-outside, external-owner-inside, and unresolved arithmetic;
- the six identity-resolution statuses and five active-residency
  classifications remain explicit stable enum strings for AI analysis; and
- both the Player session-rebind path and development-command start path supply
  the completed reconstruction recipe, exact safety assessment, current server
  tick, and independent instance/detail budgets.

Safety boundary:

- the census reads the existing bounded NPC collection and creates no second
  registry, index, cache, callback, listener, lease, or retained entity handle;
- missing, stale, non-NPC, unknown, or runtime-ID-mismatched identity is
  unresolved evidence and cannot acquire an authored owner;
- schema-v25 requires `pointInTimeCensus=true`,
  `activeInstanceEvidence=true`, `entityRegistry=false`,
  `arrivalGate=false`, and `lifecycleAuthority=false`;
- evidence is emitted only when the same exact retirement-safety assessment is
  available, and null remains required when that parent evidence is absent;
  and
- No lifecycle authority, arrival rejection, NPC retention, envelope change,
  recipe execution, loading, release, eviction, movement, roaming, respawn, or
  combat behavior is created or changed.

Automated validation status:

- the compiled observer fixture emits a non-null empty-census result tied to
  exact recipe/safety identity and validates the complete v11-v25 schema chain;
- schema and source guards prove additive bounded fields, exact inert flags,
  synchronized detachment from the existing NPC collection, and both runtime
  source paths; and
- earlier observer consumers continue compiling through the retained v24
  source overload, with the new required field serialized as null when no v25
  source is supplied;
- the complete layered-map suite passes 178 tests across 71 focused files; and
- the authoritative bundled-Ant build compiles 764 core and 488 plugin
  sources.

Private owner validation status:

- all 113 fresh records are schema-v25, sequences `1-113` are contiguous, and
  every record validates against the complete 25-resource schema registry;
- 12 events contain active-NPC census evidence, and 7,062 aggregate,
  selection, identity, classification, safety-count, and detail-membership
  reconciliation checks pass;
- every initial world NPC is active, uniquely recognized, and recipe-matched:
  3,775 observed, 3,775 recognized, zero missing/stale/non-NPC/unknown/
  runtime-ID-mismatched identities, and zero duplicate recognized instances;
- `slice72-narrow` selects 6 sources containing 78 authored NPCs. All 78 are
  active selected-owner-inside instances, match the 78 NPC recipe placements
  and Region safety counts exactly, and none currently crosses an exact packed
  source boundary;
- `slice72-broad` selects 42 sources containing 795 authored NPCs. All 795 are
  selected-owner-inside, while 12 have moved from their exact authored source
  into an adjacent selected source. None is selected-owner-outside,
  external-owner-inside, unresolved-inside, or unresolved-claimed-outside;
- after ordinary walking expanded the accumulated exact selection to 48
  sources, 860 active NPCs still match 860 recipe placements and safety counts.
  The same 12 cross-source NPCs remain contained, with no boundary or identity
  anomaly;
- one globally observed NPC is inactive at the final marker/stop because the
  owner attacked and killed a goblin during the route. It is correctly counted
  as inactive and irrelevant to the selected set rather than misclassified as
  active residency; and
- server evidence confirms the owner opened scenery, traded and talked with a
  shopkeeper, fought the goblin, and completed ordinary movement before
  reporting no visual, loading, interaction, NPC, or collision issue.

Status: implemented, automated-validated, and owner-validated. No lifecycle
authority is authorized.

### Slice 73: Active NPC containment assessment

Objective: reduce one complete Slice 71 observation to an explicit bounded
answer about whether active NPC evidence crosses the exact selected-source
boundary, without treating point-in-time containment as permission to retain,
remove, reload, or reconstruct an entity.

Implemented:

- `LayeredPackedRegionActiveNpcContainmentAssessment` consumes one immutable
  active-NPC residency observation and retains its generation, safety tick,
  census tick, source count, and active-instance census context;
- selected-owned NPCs that remain inside are separated into exact same-source
  and cross-source counts, so ordinary movement across an authored packed
  source remains visible without opening a boundary when both sources are
  selected;
- the assessment exposes six stable blockers: selected-owned NPC outside,
  external-owned NPC inside, unresolved NPC inside, unresolved claimed
  selected owner outside, relevant inactive instance, and relevant duplicate
  authored identity;
- boundary containment requires all six blocker counts to be zero and retains
  both the number of blocking conditions and the total blocking evidence;
- relevant active instances remain an explicit entity-preservation burden even
  when the boundary result is `contained now`; and
- point-in-time, containment-evidence, entity-preservation, lifecycle-ready,
  entity-registry, arrival-gate, and lifecycle-authority flags keep the result
  machine-readable and refusal-oriented.

Automated validation status:

- a compiled contained fixture proves one same-source and one cross-source NPC
  remain inside a two-source selection with zero blockers;
- a compiled open fixture independently exercises all six blockers, including
  duplicate authored identity and relevant inactive evidence, and preserves
  exact condition/evidence arithmetic;
- both fixtures require active entities to remain a preservation burden while
  refusing lifecycle readiness, registry, arrival-gate, and lifecycle
  authority;
- source guards keep the assessment detached from live entities, Region,
  RegionManager, callbacks, movement, loading, retention, and teardown; and
- the complete layered-map suite passes 181 tests across 72 focused files; and
- the authoritative bundled-Ant build compiles 765 core and 488 plugin
  sources.

Safety boundary:

- this assessment has no retained entity handle and cannot change an NPC,
  source selection, authored envelope, or packed Region;
- containment is a statement about one bounded census at one tick, not a proof
  that later movement, arrival, respawn, combat, or reconnect cannot change the
  boundary;
- an active preservation requirement is evidence of work still missing, not a
  preservation implementation; and
- No lifecycle authority, arrival rejection, NPC retention, recipe execution,
  loading, release, eviction, reconstruction, transaction, or rollback is
  created.

Status: implemented and automated-validated. Runtime diagnostic exposure and
all lifecycle adoption remain deliberately absent.

### Slice 74: Active NPC containment diagnostics

Objective: expose Slice 73's bounded point-in-time result through additive
private diagnostics, deriving it from the same exact census already emitted
for an event so owner validation can compare real NPC movement with the six
containment blockers.

Implemented:

- `layered-map-parity-event-v26` retains every v25 field and adds nullable
  `packedRegionActiveNpcContainment` evidence;
- the observer derives containment directly from the non-null
  `packedRegionActiveNpcResidency` value captured for that event and takes no
  second NPC snapshot, source callback, registry lookup, or later tick;
- schema-v26 retains the generation, safety tick, census tick, selected source
  and active-instance context from the parent observation;
- same-source and cross-source selected-owner-inside counts remain separate,
  while all six stable blocker kinds retain exact instance counts;
- boundary, preservation-required, condition-count, and evidence-count fields
  make `contained now` and remaining entity work directly machine-readable;
- the assessment must be null when its parent residency observation is null;
  and
- required point-in-time, containment-evidence, lifecycle-ready,
  entity-registry, arrival-gate, and lifecycle-authority fields preserve the
  refusal boundary in the schema and serializer.

Automated validation status:

- schema comparison proves the v26 field is additive to v25, required but
  nullable, six-blocker bounded, and non-authoritative;
- source guards prove the assessment consumes the same exact census and that no
  separate containment source or runtime callback exists;
- the executable observer fixture emits a zero-NPC contained assessment,
  reconciles its source/tick/count identity with the parent observation, and
  validates the complete v11-v26 schema resource chain;
- existing diagnostic consumers retain their prior start overloads and receive
  null containment when no active-NPC source is installed; and
- the complete layered-map suite passes 184 tests across 73 focused files; and
- the authoritative bundled-Ant build compiles 765 core and 488 plugin
  sources.

Private owner validation status:

- all 77 fresh records are schema-v26, sequences `1-77` are contiguous, and
  every record validates against the complete 26-resource schema registry;
- 11 events contain paired active-NPC residency and containment evidence, and
  25,087 schema, sequence, census arithmetic, identity, selected-source
  membership, safety-count, authored-replay, and containment reconciliation
  checks pass;
- the intended 6-source narrow set matured one tick after the labeled marker
  because the marker occurred at tick 15 of the 16-tick cooldown. Its next
  event contains 78 selected-owned NPCs, all in their exact authored source,
  with zero blockers. The owner followed the route correctly and no retest is
  required;
- the 42-source broad marker contains 795 active selected-owned NPCs. Of those,
  777 remain in their exact authored source and 18 have crossed into another
  selected source; all 795 current NPC counts and authored NPC replay counts
  reconcile and the boundary is contained;
- ordinary walking grows the accumulated set first to 48 and then 54 sources.
  One recognized Guard (`runtimeNpcId=65`) authored by source `(2,10)` with
  identity ordinal 181 moves into selected source `(2,9)`, producing exactly
  one `EXTERNAL_OWNER_INSIDE` blocker and changing the result to open;
- the matching authored Guard placements explicitly permit Y roaming across
  the source boundary at `479/480`, so the open result is valid mobile-entity
  evidence rather than an identity, movement, or placement error;
- the owner also opened scenery, killed one Goblin, collected its drops, and
  attempted Woodcutting. The inactive Goblin is outside the selected set and
  remains correctly irrelevant rather than becoming a containment blocker;
  and
- the owner reported no visual, NPC, interaction, collision, or loading issue.

Safety boundary:

- the diagnostic reports one assessment produced from one detached census; it
  does not predict a later arrival, departure, death, respawn, or movement;
- non-null containment cannot exist without non-null residency evidence from
  the same event;
- `entityPreservationRequired` reports an unmet obligation and does not retain,
  transfer, serialize, reconstruct, or own an NPC; and
- No lifecycle authority, arrival rejection, registry, loading, retention,
  release, eviction, recipe execution, transaction, or rollback is created.

Status: implemented, automated-validated, and owner-validated. All lifecycle
adoption remains deliberately absent.

### Slice 75: Active NPC boundary requirement projection

Objective: explain recognized active-NPC boundary crossings as exact source
requirements while preserving unresolved identity, inactive evidence, and
duplicate identity as hard blockers that source expansion cannot erase.

Implemented:

- `LayeredPackedRegionActiveNpcBoundaryRequirementProjection` consumes one
  complete Slice 71 observation and derives its Slice 73 assessment internally,
  preventing a mismatched caller-supplied containment result;
- a recognized selected-owned NPC outside proposes its current packed source,
  while a recognized external-owned NPC inside proposes its authored owner
  source;
- repeated crossings deduplicate into stable Y/X-sorted source requirements
  while retaining selected-owner-current and external-owner-authored reason
  counts separately;
- expandable instance count remains separate from unique required-source count,
  so multiple NPCs cannot silently appear to require multiple source loads;
- unresolved-inside, unresolved-claimed-selected-owner-outside,
  relevant-inactive, and relevant-duplicate-identity counts remain four
  explicit hard-blocker categories;
- a caller-supplied requirement budget refuses the complete projection before
  any unique source can be silently dropped; and
- fresh-safety, fresh-census, selection-mutated, boundary-closure-proved,
  entity-registry, arrival-gate, and lifecycle-authority flags make the safety
  boundary machine-readable.

Automated validation status:

- a contained fixture has same-source and internal cross-source movement,
  produces no requirement or hard blocker, and still refuses to prove future
  closure;
- an open fixture projects one selected-owner current source and one shared
  external-owner authored source from three crossing NPC instances;
- the same open fixture preserves one count in each hard-blocker category and
  proves that expansion reasons do not erase unresolved, inactive, or duplicate
  evidence;
- immutable-list and zero/overflow budget guards refuse truncation or caller
  mutation, and source guards keep the projection detached from entities,
  Region, RegionManager, callbacks, loading, and teardown; and
- the complete layered-map suite passes 187 tests across 74 focused files; and
- the authoritative bundled-Ant build compiles 766 core and 488 plugin
  sources.

Safety boundary:

- a source requirement explains which coordinate must be reconsidered; it is
  not a load, acquisition, retention, lease, or ownership request;
- every proposed source requires a fresh complete safety assessment and a new
  NPC census before the boundary can be assessed again;
- the projection never edits its input selection, recursively follows a new
  crossing, or claims that adding its requirements would reach a fixed point;
  and
- No lifecycle authority, arrival rejection, registry, entity retention,
  loading, release, eviction, reconstruction, transaction, or rollback is
  created.

Status: implemented and automated-validated. Runtime diagnostic exposure and
all lifecycle adoption remain deliberately absent.

### Slice 76: Active NPC boundary requirement diagnostics

Objective: expose Slice 75's bounded missing-source projection through
additive private diagnostics, deriving it from the same exact census already
emitted for the event so real mobile boundary crossings can identify their
required coordinates without becoming load or lifecycle instructions.

Implemented:

- `layered-map-parity-event-v27` retains every v26 field and adds nullable
  `packedRegionActiveNpcBoundaryRequirements` evidence;
- the observer derives the projection directly from the non-null
  `packedRegionActiveNpcResidency` captured for that event, alongside the
  containment assessment, without a second census, callback, or later tick;
- schema-v27 retains generation, safety tick, census tick, selected-source
  count, contained-now state, recognized crossing counts, and four kinds of
  non-expandable hard-blocker evidence;
- each stable Y/X-sorted requirement retains both possible reason counts and
  the total number of crossing NPC instances that requested its source;
- the projection must be null when its parent containment evidence is null;
  and
- required fresh-safety, fresh-census, selection-mutated,
  boundary-closure-proved, entity-registry, arrival-gate, and
  lifecycle-authority fields preserve the refusal boundary in the schema and
  serializer.

Automated validation status:

- schema comparison proves the v27 field is additive to v26, required but
  nullable, bounded to 8,192 requirements, and non-authoritative;
- source guards prove the projection consumes the same exact census as the
  parent containment assessment and that no separate requirement source or
  runtime callback exists;
- the executable observer fixture emits one recognized external-owned NPC in a
  selected current source, serializes exactly one
  `EXTERNAL_OWNER_AUTHORED_SOURCE` requirement for its unselected owner source,
  reconciles its generation and selected-source context with both parents, and
  validates the complete v11-v27 schema resource chain;
- existing diagnostic consumers retain their prior start overloads and receive
  null requirements when no active-NPC source is installed;
- the complete layered-map suite passes 190 tests across 75 focused files; and
- the authoritative bundled-Ant build compiles 766 core and 488 plugin
  sources.

Private owner validation status:

- all 112 fresh records are schema-v27, sequences `1-112` are contiguous, and
  every record validates against the complete 15-resource schema registry;
- 16 events contain one atomic active-NPC residency, containment, and boundary
  requirement triplet. All 33,385 schema, sequence, selected-source
  membership, census arithmetic, identity-status, containment-blocker,
  requirement-derivation, reason-count, and authority-flag checks pass;
- the matured narrow marker selects 6 exact sources, the broad marker selects
  42, ordinary walking grows the accumulated set to 48, and the final marker
  selects 55. Every observed census contains 3,774 or 3,775 active recognized
  NPCs with no unresolved or relevant duplicate identity;
- every observed boundary is contained in this run. No selected-owned NPC
  leaves, no external-owned NPC enters, every hard-blocker count is zero, and
  all 16 projections therefore emit exactly zero source requirements;
- the owner opens scenery, kills one Goblin, interacts with nearby animals,
  and attempts Woodcutting while moving through the broad route. The killed
  Goblin temporarily changes the active census from 3,775 to 3,774, remains
  correctly irrelevant to the selected boundary, and later respawns;
- the owner reports no visual, NPC, movement, interaction, collision, loading,
  or diagnostic issue; and
- the roaming Guard observed in Slice 74 does not happen to cross from authored
  source `(2,10)` into selected source `(2,9)` during this shorter fresh-server
  run. The contained/zero-requirement runtime path is owner-validated; a
  non-empty runtime requirement remains unobserved rather than contradicted.

Safety boundary:

- a serialized requirement is diagnostic evidence, not a source load,
  acquisition, ownership, lease, retention, or selection mutation;
- every proposed source still requires a fresh complete safety assessment and
  active-NPC census before any later boundary decision;
- a zero-requirement event proves only that its one observed census contained
  no expandable crossing, not that future movement is closed; and
- No lifecycle authority, arrival rejection, registry, entity retention,
  loading, release, eviction, reconstruction, transaction, or rollback is
  created.

Status: implemented and automated-validated; contained-path owner-validated.
A naturally occurring non-empty private-world capture remains unobserved but
is not required after deterministic observer/schema coverage; all lifecycle
adoption remains deliberately absent.

### Slice 77: Retirement source refinement proposal

Objective: combine one exact retirement-safety source set with its static
authored cohort, point-in-time active-NPC requirements, and non-expandable hard
blockers into a provenance-tagged candidate union that explains what a later
reassessment must observe without changing runtime state.

Implemented:

- `LayeredPackedRegionRetirementRefinementProposal` requires the original
  safety assessment and Slice 63 authored cohort to agree on every ordered
  seed coordinate, while the Slice 75 active-NPC projection must agree on
  generation, safety tick, and selected-source count;
- original safety seeds, recursively expanded authored sources, and both
  active-NPC requirement reasons deduplicate into one stable Y/X-sorted
  candidate list without losing their independent provenance or instance
  counts;
- authored expansion round remains attached to each cohort source, including
  active requirements that overlap an already-required authored coordinate;
- empty static support coordinates remain a separate bounded requirement list
  rather than silently becoming load candidates. If an independent active-NPC
  reason names the same coordinate, both roles remain explicit;
- every candidate added beyond the exact original safety set reports that
  fresh safety evidence and a fresh NPC census are required;
- all four non-expandable active-NPC blocker categories and their exact total
  evidence survive unchanged; and
- separate candidate/support budgets refuse the whole proposal before any
  source or reason can be truncated.

Automated validation status:

- a compiled aligned fixture begins with safety source `(4,0)`, recursively
  adds authored source `(5,0)`, retains empty static support `(6,0)`, and adds
  active-only authored-owner source `(7,0)`;
- the same fixture proves `(5,0)` deduplicates while preserving both its
  authored expansion and selected-owner-current reasons, so three input source
  families produce three exact candidates rather than four;
- unresolved-inside and relevant-inactive evidence remain two hard conditions
  with two evidence instances and cannot be erased by the candidate union;
- seed-coordinate mismatch, candidate overflow, support overflow, invalid
  budgets, null input, and immutable result guards all refuse safely;
- source guards keep the proposal detached from entities, Region,
  RegionManager, callbacks, loading, and teardown;
- the complete layered-map suite passes 193 tests across 76 focused files; and
- the authoritative bundled-Ant build compiles 767 core and 488 plugin
  sources.

Safety boundary:

- a candidate coordinate is a request for later evidence, not a load,
  acquisition, lease, ownership, retention, or selection mutation;
- original safety evidence is retained only as the input observation; every
  later decision still requires a fresh whole-set assessment and census;
- authored closure of one input cohort does not prove closure after adding an
  active-only source, and support coordinates remain spatial evidence rather
  than reconstructable content; and
- No lifecycle authority, arrival rejection, registry, entity retention,
  loading, release, eviction, reconstruction, transaction, commit, or rollback
  is created.

Status: implemented and automated-validated. Runtime diagnostic exposure and
all lifecycle adoption remain deliberately absent.

### Slice 78: Retirement refinement diagnostics

Objective: expose Slice 77's bounded candidate union through additive private
diagnostics, deriving it from the same event safety, authored cohort, and
active-NPC boundary values so runtime evidence can be reconciled without a
second snapshot or lifecycle consumer.

Implemented:

- `layered-map-parity-event-v28` retains every v27 field and adds nullable
  `packedRegionRetirementRefinement` evidence;
- the observer constructs the proposal only when retirement safety, authored
  cohort, and active-NPC requirements all exist for the same event, using no
  new callback, source interface, census, or later tick;
- schema-v28 preserves original safety, authored cohort, authored expansion,
  active-NPC requirement, deduplicated candidate, added candidate, overlap,
  external support, support-promotion, and hard-blocker totals;
- each candidate retains its exact coordinate, seed/cohort/support roles,
  authored expansion round, both active-NPC reason counts, added status, and
  missing-fresh-evidence flags;
- support coordinates retain owner-source and placement-reference counts and
  explicitly report whether an independent active reason also promoted the
  coordinate into the candidate union;
- the proposal must be null when any of its three parent values is null; and
- required reassessment, candidate-mutation, fixed-point-closure, load-request,
  entity-registry, arrival-gate, and lifecycle-authority fields preserve the
  refusal boundary in the schema and serializer.

Automated validation status:

- schema comparison proves the v28 field is additive to v27, required but
  nullable, separately bounded to 8,192 candidates/support coordinates, and
  non-authoritative;
- source guards prove the proposal consumes the same event parents and that no
  separate refinement source or runtime callback exists;
- the executable observer fixture emits one safety seed `(4,0)` and one
  external-owner candidate `(5,0)`, preserving exact parent ticks, source
  counts, active reason count, fresh-evidence flags, and zero hard blockers;
- the fixture validates the complete v11-v28 schema resource chain, including
  every nested non-empty candidate field;
- existing diagnostic consumers retain their prior start overloads and receive
  null refinement when any required parent source is absent;
- the complete layered-map suite passes 196 tests across 77 focused files; and
- the authoritative bundled-Ant build compiles 767 core and 488 plugin
  sources.

Private owner validation status:

- all 84 fresh records are schema-v28, sequences `1-84` are contiguous, the
  accepted narrow, broad, and broad-after-walk markers occur at sequences 3,
  5, and 83, and the route ends with an exact stop record;
- 13 events contain an atomic safety, authored-cohort, active-NPC requirement,
  and retirement-refinement chain. All 17,836 schema, sequence, parent-tick,
  seed-order, candidate-union, provenance, support, reason-count, ordering,
  hard-blocker, and authority-flag checks pass;
- the narrow marker expands 6 original safety sources to 61 candidate sources
  with 55 added authored sources and 14 support-only coordinates. The broad
  marker expands 42 originals to 65 candidates with 23 additions and 14
  support coordinates;
- ordinary walking grows the observed original set first to 48 sources and
  ultimately to 55. The final marker then reports 120 candidates, 65 additions,
  and 17 support coordinates without losing or duplicating a source;
- every active-NPC boundary is contained in this run, so active requirements,
  active/authored overlaps, support promotions, and hard blockers are all zero.
  The deterministic executable observer fixture remains the accepted coverage
  for the non-empty active-reason path; and
- the owner reports no visual, movement, collision, loading, interaction, or
  diagnostic issue.

Safety boundary:

- serialized candidates are explanatory evidence, not a changed selection,
  load request, acquisition, lease, ownership, or retention decision;
- `reassessmentRequired` and per-added-source flags mean a consumer must obtain
  new evidence; they do not authorize the observer to obtain or apply it;
- input authored closure and a contained active census still cannot prove
  fixed-point closure after source addition; and
- No lifecycle authority, arrival rejection, registry, entity retention,
  loading, release, eviction, reconstruction, transaction, commit, or rollback
  is created.

Status: implemented, automated-validated, and owner-validated. All lifecycle
adoption remains deliberately absent.

### Slice 79: Fresh retirement-refinement reassessment

Objective: close one detached refinement loop by requiring a proposal's exact
candidate set to receive strictly newer, atomically aligned safety,
authored-cohort, and active-NPC evidence, while keeping candidate-set
convergence separate from retirement readiness or lifecycle authority.

Implemented:

- `LayeredPackedRegionRetirementRefinementReassessment` requires the fresh
  safety source count and ordered coordinates to match every prior candidate
  exactly; a missing, extra, or reordered source refuses the whole result;
- authored manifest generation must remain unchanged, the safety observation
  must be newer than the prior safety tick, and the active census must be newer
  than the prior census tick;
- the existing Slice 77 proposal builder combines the fresh parents again, so
  its alignment, budgets, candidate provenance, support separation, and hard-
  blocker rules remain the single derivation authority;
- retained candidates and genuinely new candidates have exact arithmetic, and
  the new-candidate list preserves authored/active/support provenance plus the
  requirement for another fresh assessment and census;
- a stable source set with no non-expandable blocker reports refinement
  convergence only at that observation. A stable set with an unresolved
  blocker remains explicitly unconverged, while any new candidate requires
  another bounded iteration; and
- lifecycle-ready source counts remain visible as evidence but cannot turn
  candidate convergence into a load, retirement decision, or commit token.

Automated validation status:

- a compiled fixture begins with candidates `(4,0)`, `(5,0)`, and `(7,0)`,
  then reassesses those exact sources from newer ticks and proves a stable
  three-source result with external support `(6,0)` retained;
- a second census observes an external-owned NPC inside the selection and adds
  only `(8,0)`, retaining its exact active reason and fresh-evidence burden;
- a stable candidate set containing unresolved active identity evidence keeps
  one hard condition/evidence instance and does not report convergence;
- stale observations, incomplete and reordered candidate sets, candidate
  overflow, null input, and mutable-result attempts all refuse safely;
- source guards keep the reassessment detached from entities, Region,
  RegionManager, callbacks, loading, teardown, and lifecycle authority;
- the complete layered-map suite passes 199 tests across 78 focused files; and
- the authoritative bundled-Ant build compiles 768 core and 488 plugin
  sources.

Safety boundary:

- `isCandidateSetStableAtObservation` describes one exact point-in-time source
  set; it is not a durable admission barrier for mobile entities or mutations;
- `isRefinementConvergedAtObservation` requires both a stable set and no
  non-expandable active-NPC blocker, but it proves neither full entity
  preservation nor lifecycle readiness;
- the returned next proposal and additions are immutable evidence and cannot
  acquire, retain, load, release, unregister, reconstruct, or evict anything;
  and
- No lifecycle authority, retirement commit token, arrival rejection, entity
  registry, transaction, teardown, reconstruction, or rollback is created.

Status: implemented and automated-validated. Runtime diagnostic exposure and
all lifecycle adoption remain deliberately absent.

### Slice 80: Read-only refinement-candidate observation

Objective: let the private runtime observe one exact Slice 79 candidate set
without requiring those sources to be existing retirement candidates, loading
an absent Region, or manufacturing logical retirement/readiness evidence.

Implemented:

- `LayeredPackedRegionRetirementSafetyAssessment.assessDiagnosticSelection`
  accepts one bounded, ordered, duplicate-free list of count-only packed-source
  contents and explicitly records that retirement-readiness evidence is absent;
- diagnostic observations use readiness tick, ownership version, and residency
  version `-1`, and every source carries the distinct
  `DIAGNOSTIC_SELECTION_ONLY` state plus `READINESS_NOT_READY`;
- resident, tile-backed, empty sources may still report content quiescence, but
  diagnostic selection alone can never produce one lifecycle-ready source;
- ordinary readiness-backed safety assessments retain their existing
  semantics and explicitly report that they do contain retirement-readiness
  evidence;
- `RegionManager.assessLayeredPackedRegionRetirementRefinementCandidates`
  preserves proposal order, holds the existing observation lock, peeks each
  packed Region, captures only detached counts, and reports an absent source
  without calling a loading or registration path; and
- candidate, source, and list budgets refuse the whole observation before any
  truncation.

Automated validation status:

- a compiled fixture observes one occupied, one absent, and one quiescent
  source while preserving exact content/blocker arithmetic and input order;
- the absent source retains `SOURCE_NOT_RESIDENT` and
  `TILE_STORAGE_UNAVAILABLE`, while the otherwise quiescent source remains
  blocked solely by missing readiness evidence;
- negative ticks, null inputs/elements, duplicate coordinates, overflow, and
  mutable-result attempts all refuse safely;
- source guards prove the RegionManager seam uses
  `peekRegionFromSectorCoordinates` and detached content snapshots without
  calling Region loading, registration, removal, or unload paths;
- the complete layered-map suite passes 202 tests across 79 focused files; and
- the authoritative bundled-Ant build compiles 768 core and 488 plugin
  sources.

Safety boundary:

- `DIAGNOSTIC_SELECTION_ONLY` is deliberately not another route to
  `READY`; content counts cannot substitute for ownership, residency, release,
  cooldown, or covered-logical-Region decisions;
- an absent Region is valid negative evidence and is never loaded merely so a
  diagnostic can inspect it;
- the method returns detached count-only state and retains no Region or entity
  handle after its observation lock is released; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  or rollback is created.

Status: implemented and automated-validated. The one-callback fresh
reassessment and diagnostic exposure remain absent.

### Slice 81: Same-tick refinement reassessment source

Objective: compose exact refinement-candidate observation, authored closure,
active-NPC residency, boundary requirements, and Slice 79 reassessment behind
one bounded private runtime call without exposing or retaining the result yet.

Implemented:

- `LayeredPackedRegionRetirementRefinementReassessment.isFreshObservationTick`
  requires one shared non-negative tick to be strictly newer than both the
  previous safety and census ticks;
- `RegionManager.captureLayeredPackedRegionRetirementRefinementReassessmentIfFresh`
  returns `null` before sampling when that freshness rule is not satisfied,
  making same-tick command/movement bursts a normal deferral rather than an
  observer failure;
- one `layeredRegionLifecycleLock` scope captures the proposal-ordered
  diagnostic contents, analyzes the immutable authored recipe, snapshots the
  active NPC population, projects boundary requirements, and invokes Slice 79
  using the same server tick for safety and census evidence;
- candidate/support/NPC/detail/requirement budgets remain explicit and their
  existing builders refuse overflow without partial results;
- candidate contents continue to use Region peeks and detached counts, so an
  absent candidate is reported without loading it; and
- no observer interface, trace state, callback registration, serialization, or
  normal runtime caller is added in this slice.

Automated validation status:

- the executable Slice 79 fixture proves a shared tick equal to the previous
  census is not fresh while the next tick is, with null and negative-tick
  refusal preserved;
- source guards prove the RegionManager method checks freshness before
  sampling, composes the exact Slice 80/cohort/NPC/requirement/Slice 79 chain
  inside one observation lock, and contains no loading, registration, removal,
  teleport, or lifecycle operation;
- a separate guard proves the new method remains absent from the parity
  observer until diagnostic exposure is deliberately added;
- the complete layered-map suite passes 205 tests across 80 focused files; and
- the authoritative bundled-Ant build compiles 768 core and 488 plugin
  sources.

Safety boundary:

- `null` means only `not newer yet`; it cannot be interpreted as convergence,
  failure, readiness, or permission to discard the prior immutable proposal;
- same-tick alignment prevents the candidate-content and active-NPC portions
  from silently describing different server ticks, but it does not prevent a
  later mobile arrival or mutation;
- holding the private observation lock creates no lease, pin, admission gate,
  or retained Region/entity handle; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  or rollback is created.

Status: implemented and automated-validated. Observer state, schema exposure,
and private owner validation remain absent.

### Slice 82: Stateful refinement reassessment diagnostics

Objective: expose Slice 81 through additive private diagnostics while retaining
only the latest immutable proposal and keeping every result non-authoritative.

Implemented:

- additive `layered-map-parity-event-v29` records add
  `packedRegionRetirementRefinementReassessment` without changing any v28
  field;
- one optional callback invokes the Slice 81 RegionManager seam from both the
  dev-command and logged-in Player paths using the completed immutable authored
  recipe and the observer's existing explicit budgets;
- each trace retains at most the latest immutable proposal. A newer expanding
  or hard-blocked result replaces it with that result's immutable next proposal;
  a stable unblocked result clears it;
- a current event can seed tracking only when no proposal was pending at event
  start, preventing the ordinary same-event projection from overwriting an
  ongoing refinement chain or immediately reseeding a converged chain;
- `DEFERRED_NOT_NEWER` is serialized distinctly from no pending attempt and
  from stable, expanded, hard-blocked, or expanded-and-hard-blocked results;
- successful records include the prior and reassessed evidence ticks, exact
  before/after counts, full diagnostic-only fresh safety, exact new-candidate
  coordinates, and the full next proposal; and
- the layered-maps operator README now identifies v29 as the current trace
  contract and summarizes the retained-proposal state machine; and
- the result states that it has no retirement-readiness evidence, fixed-point
  lifecycle proof, load request, entity registry, arrival gate, retirement
  commit token, or lifecycle authority.

Automated validation status:

- the executable observer fixture creates a non-empty two-source proposal,
  proves one deferred attempt retains it unchanged, then proves one strictly
  newer stable reassessment clears it while serializing both diagnostic-only
  safety entries;
- schema validation covers every fixture event through v29 and retains the
  complete v11-v28 reference chain;
- focused source guards cover state replacement, convergence clearing, no
  same-event overwrite, private runtime wiring, bounded schema fields, and all
  authority flags;
- the complete layered-map suite passes 209 tests across 81 focused files; and
- the authoritative bundled-Ant build compiles 768 core and 488 plugin
  sources.

Private owner validation status:

- one five-record schema-v29 route covered start in Lumbridge, teleport to
  Varrock, the `proposal-ready` and `reassessment` markers, and stop; all five
  layered-coordinate round trips were exact and every record passed the full
  v29 schema chain;
- the first marker at tick 2307 expanded 18 safety sources into 40 candidates
  through 22 authored additions. One active-NPC requirement at packed source
  `(1,13)` already belonged to that authored cohort, the external static
  support requirement remained separate at `(1,17)`, and no hard blocker was
  present;
- the fresh tick-2320 reassessment retained all 40 candidates and added exactly
  packed source `(4,11)` for one external-owner-authored active instance,
  producing the expected `EXPANDED` 40-to-41 transition without a hard
  blocker;
- the fresh tick-2328 stop reassessment retained all 41 candidates, added none,
  reported no active-NPC requirement or hard blocker in its next proposal,
  produced `STABLE`, and cleared the pending proposal;
- both fresh safety observations remained entirely diagnostic: 0 sources were
  lifecycle-ready and all 40/41 observed sources were blocked, with readiness,
  ownership, and residency versions held at `-1` and every authority flag
  false; and
- the private server logged no observer error during the route, and the owner
  completed the requested client route without reporting a visual or movement
  issue. The accepted trace is preserved locally as
  `player-1-10651088446.slice82-owner-validated.jsonl`.

Safety boundary:

- retained state is an immutable diagnostic proposal, not a Region/entity
  handle, lease, pin, registry, arrival gate, teardown transaction, or load
  request;
- a deferred attempt samples nothing and cannot be interpreted as convergence,
  while a stable attempt is explicitly point-in-time only;
- diagnostic safety uses `DIAGNOSTIC_SELECTION_ONLY`, readiness/ownership/
  residency versions of `-1`, zero lifecycle-ready sources, and false
  retirement-readiness evidence; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  or rollback is created.

Status: implemented, automated-validated, and owner-validated. No lifecycle
authority is granted.

### Slice 83: Runtime preservation and reload burden contract

Objective: turn the remaining runtime-state audit into one bounded, immutable
contract without observing, preserving, reloading, or retiring anything.

Runtime ownership audit:

| Burden family | Evidence available now | Required policy | Missing capability |
| --- | --- | --- | --- |
| `PLAYER_SESSION` | packed Region has an exact local Player count; World, login/session state, persistence, social state, and the client connection remain separate owners | any Player present is a hard blocker; Region retirement must never own logout or persistence | a future atomic gate must prevent a Player entering between observation and commit; no Player snapshot/reload path is appropriate |
| `DYNAMIC_OBJECT` | active authored objects carry generation-fenced placement identity, so an identity-less active object can be distinguished from the authored recipe | preserve and restore dynamic identity, object state, ownership, attributes, replacement state, and affected caches | no dynamic-object state bundle, source-scoped registry, restore path, or rollback exists; one anchored object can also mutate collision in neighboring packed sources |
| `GROUND_ITEM` | Region contains active items and authored identity distinguishes active authored items from ordinary dynamic drops | preserve and restore active dynamic state, owner visibility, amount, noted/spawn timing, attributes, and authored-generation state | absent authored items may be valid pending respawn in `AuthoredGroundItemRegistry` and an unowned delayed event; no source-complete bundle or replay path exists |
| `COLLISION_PRODUCT` | `LayeredTileState` can copy full tile collision counters and projectile state | treat collision as derived state and rebuild it from terrain plus the accepted authored/dynamic object state | no source-attributed mutation ledger or transactional cross-source rebuild exists; raw tile state mixes terrain, scenery, walls, projectile blockers, and neighboring-object effects |
| `OWNED_EVENT` | `GameTickEventStore` can return a global event snapshot and each event may expose a Player/NPC/null owner | preserve and restore every event whose owner or spatial effect depends on a retiring source | events are keyed globally by class/duplication policy and Player username hash, not packed source; null-owned spatial callbacks and anonymous closure state have no stable serialization contract |

Implemented:

- immutable `LayeredPackedRegionPreservationBurdenAssessment` values correlate
  one exact same-order Slice 49/80 safety selection with a complete five-family
  inventory for every packed source;
- `COMPLETE`, `PARTIAL`, and `UNAVAILABLE` evidence remain distinct.
  Unavailable evidence uses count `-1`, so an unknown family can never be
  aggregated or displayed as zero;
- each family has a fixed policy: Players block while present; dynamic objects,
  ground items, and owned events require both preservation and restoration;
  collision products require a checked derived-state rebuild rather than blind
  serialization;
- exact complete Player and ground-item counts must agree with their parent
  safety entry, while observed dynamic-object counts cannot exceed the safety
  entry's total object count;
- per-source results preserve stable family and blocker order and expose only
  point-in-time `burdenSatisfiedAtObservation`; per-family summaries retain
  complete/partial/unavailable source counts, blocked-source counts, and known
  observed-instance totals without folding unknown evidence into arithmetic;
  and
- explicit false flags state that the value performs no preservation, reload,
  candidate mutation, entity registration, arrival gating, teardown
  transaction, or lifecycle action.

Automated validation status:

- a compiled fixture correlates two exact diagnostic safety sources and proves
  all five policies independently: an active Player hard-blocks, partial
  dynamic objects retain evidence plus two missing-path blockers, preserved
  ground items still require restoration, derived collision may be rebuilt
  without serialization, and unavailable event ownership remains unknown;
- a second complete empty source has zero observed burden while remaining
  explicitly point-in-time and non-authoritative;
- the fixture proves stable canonical family ordering, immutable values, exact
  aggregate arithmetic, safety-count correlation, null rejection, source-order
  matching, evidence-count conventions, and bounded refusal; and
- source guards prove the contract is absent from RegionManager,
  PathValidation, and private diagnostics and imports no Region, entity, or
  event handle;
- the complete layered-map suite passes 212 tests across 82 focused files; and
- the authoritative bundled-Ant build compiles 769 core and 488 plugin
  sources.

Safety boundary:

- this slice defines how later observations must describe the five burdens; it
  does not claim the current runtime can completely observe any family except
  the already available local counts;
- `burdenSatisfiedAtObservation` means only that supplied complete evidence had
  no unresolved state at that instant. It is not durable under Player/entity
  arrival, object replacement, item spawn/removal, collision mutation, or event
  scheduling;
- no evidence status can load an absent source, preserve or reconstruct state,
  remove an entity or event, mutate collision, invalidate a cache, or clear a
  pending proposal; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  or rollback is created.

Status: implemented and automated-validated. Runtime capture, schema exposure,
and owner validation remain absent.

### Slice 84: Bounded runtime preservation burden capture

Objective: populate Slice 83 from one non-creating Region-local snapshot per
exact refinement candidate while preserving incomplete evidence honestly and
keeping the capture disconnected from diagnostics and gameplay.

Implemented:

- `Region.RetirementContentsSnapshot` now additionally returns an exact count
  of active identity-less objects and one collision-product tile count without
  exposing an object, tile, collection, or Region handle;
- identity-less object counting uses Slice 56's generation-fenced authored
  identity. Authored objects and explicit replacements retain identity, while
  active dynamic constructions remain identity-less;
- collision-product counting identifies tiles with current terrain collision,
  blocking scenery, dynamic collision counters, or terrain/dynamic projectile
  blockers. A compact uniform Region contributes either zero or all 2,304
  tiles, and absent tile storage reports `-1` rather than zero;
- `currentRuntimeInventory` classifies the Region-local Player and dynamic-
  object counts as `COMPLETE`, active ground items and collision products as
  `PARTIAL`, and owned events as `UNAVAILABLE` with count `-1`;
- the partial ground-item status preserves the unobserved burden of authored
  items that are validly absent during registry-generation/respawn delay, and
  the partial collision status preserves missing mutation ownership and cross-
  source rebuild evidence;
- `RegionManager.assessLayeredPackedRegionPreservationBurden` walks the exact
  proposal order under the existing lifecycle observation lock, uses
  non-creating `peekRegionFromSectorCoordinates`, captures each Region once,
  constructs diagnostic-only safety and matching family evidence from that
  same detached snapshot, and enforces the existing source budget; and
- the seam is dormant: neither Player, the development command, private
  observer, PathValidation, nor any lifecycle consumer invokes it.

Automated validation status:

- a compiled fixture proves exact current-runtime classification for an
  occupied source: one Player hard blocker, three dynamic objects requiring
  preservation/reload, two active ground items retaining partial evidence,
  seven collision-product tiles retaining partial rebuild evidence, and
  unavailable owned events;
- an absent-source fixture proves exact zero local Players/objects without
  manufacturing ground-item completeness, collision evidence, or event
  evidence;
- invalid counts below the `-1` unavailable sentinel refuse;
- source guards prove one Region snapshot per source, the lifecycle lock,
  non-creating lookup, exact proposal order, and the absence of registration,
  removal, unload, runtime attachment, or diagnostic exposure; and
- the complete layered-map suite passes 216 tests across 83 focused files; and
- the authoritative bundled-Ant build compiles 769 core and 488 plugin
  sources.

Safety boundary:

- complete means only that the Region-local active count was exact during that
  one snapshot; it does not mean the corresponding runtime state is
  serializable, restorable, or protected against later arrival or mutation;
- the collision-product tile count is intentionally partial even when its
  numeric scan is exact, because it does not attribute products to terrain,
  anchored objects, neighboring objects, or events and cannot rebuild them;
- no absent Region is created, no event store is read, no registry or cache is
  retained, and no Player/entity/tile reference escapes the capture; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  or rollback is created.

Status: implemented and automated-validated. Private schema exposure and owner
validation remain absent.

### Slice 85: Private preservation-burden diagnostics

Objective: make Slice 84's exact, bounded runtime burden visible in the
existing private parity workflow while keeping every result observational and
preserving the proposal chain that produced it.

Implemented:

- additive `layered-map-parity-event-v30` records retain the complete v29
  contract and add nullable `packedRegionPreservationBurden` evidence;
- a newly created proposal is assessed on its creation event, a same-tick
  deferral assesses the retained proposal, and a fresh reassessment assesses
  that reassessment's immutable next proposal even when the stable result then
  clears observer state;
- the observer refuses a source-count or coordinate-order mismatch, so the
  burden families always describe the same exact proposal order serialized by
  the event rather than a nearby or independently selected set;
- each source reports all five policy families, evidence completeness, known
  instance count or the `-1` unavailable sentinel, preservation/reload support,
  ordered blockers, and a per-source satisfied result;
- family summaries retain complete, partial, unavailable, blocked, and known-
  instance totals without converting unknown values to zero;
- Player reconnect rebind and development-command start paths both call the
  single RegionManager assessment seam; and
- records without a non-empty proposal use an explicit null and perform no
  burden capture.

Automated validation status:

- the executable observer fixture emits and schema-validates non-null burdens
  for proposal creation, same-tick deferral, and stable reassessment, preserving
  two exact candidate sources and five ordered families per source;
- fixture evidence proves zero-instance complete Player/object families remain
  satisfied while partial ground-item/collision families and unavailable event
  ownership keep every source blocked;
- the closed v30 schema bounds source arrays to 8,192, family arrays to exactly
  five, blocker enumerations to the current contract, and every mutation or
  lifecycle flag to false;
- source guards cover exact proposal/result correlation and both duplicated
  private runtime wiring paths;
- the complete layered-map suite passes 220 tests across 84 focused files; and
- the authoritative bundled-Ant build compiles 769 core and 488 plugin
  sources.

Safety boundary:

- the assessment occurs only after a proposal exists and under the existing
  RegionManager observation lock; it does not influence which sources enter or
  leave that proposal;
- schema-v30 states that readiness evidence, preservation, reload, candidate
  mutation, entity registry, arrival gate, teardown transaction, and lifecycle
  authority are all false;
- exact Player/object counts are point-in-time observations, while ground-item,
  collision, and event evidence remains conservatively incomplete; and
- No lifecycle authority, retirement decision, commit token, source load,
  arrival rejection, entity registry, transaction, teardown, reconstruction,
  rollback, pathing, packet, persistence, or world-data mutation is created.

Status: implemented, automated-validated, and owner-validated.

Private owner validation status:

- the accepted private route contains four contiguous schema-v30 records:
  start at Lumbridge `(120,648)`, teleport to Varrock `(122,509)`, one marker,
  and stop. The intended first marker was entered without a recognized command
  prefix and produced no event, so the later `burden-reassment` marker became
  the proposal event and stop supplied the fresh follow-up; no equivalent
  rerun is needed;
- all four events validate against the complete v30 schema chain, sequences
  are `1-4`, every packed/layered round trip is exact, and 1,222 independent
  proposal-order, family-total, blocker, and authority reconciliations pass;
- the marker expands 18 exact safety sources into 40 proposal candidates. Its
  matching burden reports one Player in packed source `(2,10)`, zero active
  dynamic objects, 123 active ground items, 36,374 collision-product tiles,
  and unavailable owned-event counts for all 40 sources;
- 39 sources have the three conservative ground-item, collision, and event
  family blocks; `(2,10)` additionally has `ACTIVE_PLAYERS_PRESENT`. No source
  is burden-satisfied at observation;
- stop performs one fresh `EXPANDED` reassessment from 40 to 41 candidates,
  adding only packed source `(4,11)`. The assessment matches that next proposal
  exactly and reports 137 active ground items and 37,042 collision-product
  tiles; the new source contributes 14 and 668 respectively while containing
  no Player or active dynamic object;
- all 40/41 Player and dynamic-object observations are complete, all 40/41
  ground-item and collision observations remain partial, and all 40/41 event
  observations remain unavailable rather than being converted to zero;
- every readiness, candidate-mutation, preservation, reload, entity-registry,
  arrival-gate, teardown-transaction, and lifecycle-authority flag is false;
  and
- the private server records normal post-stop movement, Auctioneer dialogue,
  and scenery interaction with no parity-observer error. The owner completed
  the requested visual and interaction route without reporting an issue.

### Slice 86: Detached dynamic-object preservation record

Objective: define the first dormant family-specific recovery input by
detaching every current `GameObject` constructor value for identity-less
dynamic scenery in one exact proposal, without implying that construction data
alone is a complete restoration path.

Implemented:

- each proposal-ordered packed source records whether its Region is present
  and a deterministically ordered list of every identity-less active object;
- each object retains current object ID, permanent/replaced object ID, packed
  coordinates, direction, scenery/boundary type, optional owner, and the count
  of opaque runtime attributes attached at observation;
- source and object budgets refuse overflow rather than truncate, absent
  Regions cannot claim objects, source coordinates must be unique and in the
  canonical proposal order, and Region capture verifies every object anchor is
  still inside the source being recorded;
- the Region-local snapshot copies immutable scalar/string state while holding
  the existing collection lock, then sorts and exposes only detached values;
  neither the object nor the attribute map escapes; and
- the record distinguishes complete constructor state from standalone
  restoration completeness: opaque runtime attributes are counted but not
  copied, while event ownership is still external and unobserved.

Automated validation status:

- an executable fixture proves deterministic ordering of shuffled inputs,
  preservation of optional owner and permanent-ID constructor inputs, exact
  opaque-attribute counts, absent-source behavior, and all authority flags;
- refusal coverage rejects absent Regions with objects, noncanonical source
  order, object-budget overflow, and invalid constructor values;
- source guards prove the RegionManager walks the exact proposal candidates,
  uses non-creating Region peeks and one existing detached snapshot per source,
  and contains no registration, removal, replacement, loading, or location
  mutation; and
- the complete layered-map suite passes 224 tests across 85 focused files; and
- the authoritative bundled-Ant build compiles 770 core and 488 plugin
  sources.

Private owner validation status:

- accepted ten contiguous schema-v31 events, sequences 1-10, whose closed-
  schema validation, serialization round trips, and privacy checks all pass;
- three record-bearing events retain the exact 40-source proposal: the first
  capture, its stable fresh reassessment, and the new point-in-time proposal
  observed after the stable proposal cleared;
- every record identifies exactly one dynamic object in source `(3,13)`, with
  current/permanent ID `4`, packed coordinate `(145,660)`, direction/type `0`,
  no owner, zero runtime attributes, complete constructor state, and explicitly
  incomplete standalone restoration;
- all 40 per-source object counts and the aggregate object count agree with the
  corresponding dynamic-object preservation burden in all three events, for
  10,240 total cross-record reconciliations;
- raw owner fields, owner text, and the test account name are absent, while
  every preservation, reload, registry, arrival, teardown, and lifecycle-
  authority flag remains false; and
- the owner observed normal movement and scenery behavior, then removed the
  temporary object and cleared the queued world edit without a residual visual,
  collision, persistence, or cleanup issue.

Safety boundary:

- this is a point-in-time record, not an entity registry, preserved-object
  store, restoration command, commit token, arrival gate, or teardown input;
- arbitrary attribute values are not assumed serializable and delayed or
  object-related events cannot be attributed from Region contents, so every
  object remains standalone-restoration-incomplete even when its current
  runtime-attribute count is zero;
- owner strings remain detached internal construction data and are not yet
  published through diagnostics; a later schema should expose only the minimum
  privacy-safe evidence needed to validate capture; and
- No object is unregistered, removed, recreated, retained, or reindexed. No
  preservation, reload, teardown, rollback, loading, pathing, packet,
  persistence, world-data mutation, or lifecycle authority is created.

Status: implemented and automated-validated. Private diagnostics and owner
validation remain absent.

### Slice 87: Private dynamic-object preservation diagnostics

Objective: expose Slice 86's exact detached record through the opt-in parity
workflow so a deliberate live dynamic object can be reconciled against its
existing burden count without publishing owner text or granting recovery
authority.

Implemented:

- additive `layered-map-parity-event-v31` records retain the full v30 contract
  and add nullable `packedRegionDynamicObjectPreservation` evidence;
- proposal creation, same-tick deferral, and fresh reassessment use the same
  proposal-selection rule as preservation burden, and the observer refuses a
  generation, source-count, or coordinate-order mismatch;
- aggregate and per-source counts expose deterministic object records with
  source-local ordinals, current/permanent IDs, packed coordinates, direction,
  type, owner presence, runtime-attribute count, constructor completeness, and
  standalone-restoration incompleteness;
- owner text and opaque attribute keys/values are intentionally omitted from
  JSON; only owner presence and the attribute count cross the diagnostic
  boundary; and
- private start and reconnect paths both use the single bounded RegionManager
  capture seam, while traces without a proposal or source emit explicit null.

Automated validation status:

- the executable observer fixture emits one dynamic object in the first of two
  exact proposal sources, reconciles the existing dynamic-object burden count,
  and retains its current/permanent IDs, packed position, direction/type,
  owner-presence bit, and two opaque attributes;
- the fixture proves the sentinel owner text never appears anywhere in emitted
  JSON and schema-validates proposal creation, deferral, and stable
  reassessment records;
- schema-v31 closes every aggregate, source, and object shape; bounds candidate
  sources to 8,192 and dynamic records to 65,536; fixes every recovery and
  lifecycle flag to false; and keeps historical v30 immutable;
- current-head guards, private runtime wiring, proposal correlation, and
  privacy-safe serialization have focused regression coverage; and
- the complete layered-map suite passes 228 tests across 86 focused files; and
- the authoritative bundled-Ant build compiles 770 core and 488 plugin
  sources.

Safety boundary:

- diagnostics never serialize `owner`, attribute values, event handles, entity
  references, or Region references; `ownerPresent` is evidence only;
- constructor completeness does not imply gameplay restoration completeness,
  and no record can be consumed to create or register an object;
- the record remains an observation of the proposal actually created,
  retained, or reassessed rather than an independent selection; and
- No object is unregistered, removed, recreated, retained, or reindexed. No
  preservation, reload, teardown, rollback, loading, pathing, packet,
  persistence, world-data mutation, or lifecycle authority is created.

Status: implemented, automated-validated, and owner-validated. No recovery or
lifecycle authority is created.

### Slice 88: Dormant event-ownership inventory contract

Objective: define how a later global scheduler snapshot can describe event
affinity to an exact packed-source proposal without guessing from null owners,
retaining runtime handles, or granting event/lifecycle authority.

Implemented:

- immutable `LayeredPackedRegionEventOwnershipInventory` values correlate one
  bounded detached event snapshot with one exact canonical candidate order;
- event ownership (`NONE`, `PLAYER`, or `NPC`) remains independent from event
  attribution: `EXACT_SPATIAL`, `OWNER_POSITION_HINT`,
  `NON_SPATIAL_GLOBAL`, and `UNATTRIBUTED` cannot collapse into each other;
- exact events may retain multiple role-labelled spatial references, allowing
  a subject, target, owner, or fixed effect to touch more than one candidate
  source without double-counting the same event within one source;
- a Mob's current coordinate is only a position hint unless the event declares
  its actual effect. Anonymous null-owned callbacks remain unattributed rather
  than being mislabeled global or empty;
- contiguous snapshot ordinals retain input order without UUID, descriptor,
  callback class, owner identity, or live handle exposure, while running state,
  ticks-before-run, and execution count remain detached primitive evidence;
- aggregate and proposal-ordered per-source records report exact events, owner
  hints, global events, unattributed events, candidate relationships, and
  whether attribution is complete; and
- fixed refusal budgets allow at most 65,536 events and 262,144 spatial
  references, with no partial truncation on overflow.

Automated validation status:

- an executable compiled fixture distinguishes a fixed null-owned effect, one
  exact NPC event spanning two packed sources, a Player-owner position hint,
  an explicitly non-spatial global event, and an unattributed callback;
- the fixture proves deterministic proposal/event order, multi-source
  correlation, global uncertainty propagation, exact aggregate arithmetic,
  invalid attribution refusal, and event/reference budget refusal;
- source guards prove the inventory imports and retains no event, Mob, Region,
  scheduler, callback, UUID, or descriptor and remains disconnected from
  `RegionManager` and `GameEventHandler`; and
- the complete layered-map suite passes 231 tests across 87 focused files; and
- the authoritative bundled-Ant build compiles 771 core and 488 plugin
  sources.

Safety boundary:

- event affinity is classification evidence, not event serialization: callback
  captures, implementation state, scheduler key identity, and restoration
  behavior remain absent;
- `OWNER_POSITION_HINT` cannot make any candidate source attribution-complete,
  and one unattributed callback remains uncertainty for every candidate;
- an explicitly non-spatial global event is excluded from candidate counts only
  when supplied as explicit evidence, never inferred from a null owner; and
- No event is cancelled, stopped, removed, run, recreated, or rescheduled. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Runtime capture, private schema
exposure, owner validation, and all event/lifecycle authority remain absent.

### Slice 89: Bounded runtime event-affinity snapshot

Objective: detach the live scheduler's currently observable primitive state
into Slice 88 without inferring spatial safety from an owner, null owner,
descriptor, callback class, or closure implementation.

Implemented:

- immutable `GameTickEventSpatialAffinity` declarations default every existing
  event to `UNSPECIFIED`; an event must explicitly declare `EXACT_SPATIAL` with
  one or more role-labelled coordinates or `NON_SPATIAL_GLOBAL` with none;
- `GameTickEvent` exposes the immutable declaration through a default method,
  preserving source and behavior compatibility for every legacy event;
- one bounded `GameEventHandler` capture copies the tracked scheduler order and
  current running state, ticks-before-run, execution count, owner kind, and
  declared spatial references into Slice 88's detached values;
- legacy Player/NPC owners become `OWNER_POSITION_HINT` at their current
  coordinate, while legacy null-owned events remain unattributed; neither path
  can become exact evidence accidentally;
- exact proposal generation and canonical candidate order are retained, stale
  observation ticks and event/reference overflow refuse the complete capture,
  and no event is silently truncated; and
- descriptors, UUIDs, scheduler keys, callback types, closure fields, owner
  identities, Mob handles, event handles, and Region handles do not cross the
  snapshot boundary.

Automated validation status:

- an executable compiled fixture proves unspecified, exact multi-location,
  exact fixed-location, and explicitly non-spatial declarations are immutable
  and invalid declarations refuse;
- source guards prove runtime mapping distinguishes all four Slice 88
  attribution kinds, never reads descriptors, UUIDs, callback classes, or
  reflected fields, and invokes no scheduler or event mutation;
- source guards also prove the capture remains disconnected from the parity
  observer, Player session wiring, and development commands; and
- the complete layered-map suite passes 235 tests across 88 focused files; and
- the authoritative bundled-Ant build compiles 772 core and 488 plugin
  sources.

Private owner validation status:

- the first ordinary-tree route visually confirmed normal replacement,
  collision, interaction, and natural respawn, while its pending marker
  correctly contained no exact event because the accelerated ten-second
  callback had already completed;
- the follow-up magic-tree route captured one running exact fixed effect at
  `(524,489)`, with 41 ticks remaining, zero prior executions, no owner, and
  candidate source `(10,10)` at proposal ordinal 24;
- the pending record retained all 3,783 owner-position hints and 98
  unattributed callbacks, so the single exact event did not manufacture
  complete attribution or restoration evidence;
- after natural respawn, the completion marker contained zero exact events
  while the legacy hint/unattributed population remained visible; and
- the owner confirmed the tree returned with normal visuals, interaction, and
  collision. Every cancellation, reschedule, preservation, restoration,
  registry, arrival, teardown, and lifecycle-authority flag remained false.

Private route ergonomics:

- `::lp` is now an exact alias for `::layerparity`; and
- the pre-existing `::tp` alias for `::teleport` is documented beside it for
  time-sensitive private capture routes.

Safety boundary:

- the tracked-event list is a point-in-time observation, not a scheduler lock,
  lease, registry, commit token, or durable event identity;
- an owner coordinate remains a moving hint, exact scope is trusted only when
  explicitly supplied by the event implementation, and legacy null-owned
  events remain unattributed;
- even an exact location does not capture callback implementation state or make
  the event restorable; and
- No event is cancelled, stopped, removed, run, recreated, or rescheduled. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Exact-affinity adoption, private
schema exposure, owner validation, and all event/lifecycle authority remain
absent.

### Slice 90: Exact fixed-location scenery event affinity

Objective: adopt Slice 89's explicit exact-affinity declaration only for the
two existing World callbacks whose effect coordinate is identical to the
captured scenery input used when the callback runs.

Implemented:

- delayed scenery removal declares one exact fixed effect at the captured
  `GameObject` coordinate before unregistering that same object;
- delayed scenery spawn declares one exact fixed effect at the captured
  `GameObjectLoc` coordinate before constructing and registering that same
  placement;
- both declarations use the default-compatible event API and change no delay,
  duplication, action, registration, collision, or callback behavior; and
- no other callback is classified in this slice: ground-item/NPC respawns,
  timed NPCs, generic submitted runnables, projectiles, combat, plugins, and
  global events retain their prior conservative default.

Automated validation status:

- source guards isolate exactly two declarations in `World`, prove each uses
  the same captured input as its action, and prove the known ground-item, NPC,
  and generic plugin callbacks remain unclassified;
- source guards prove the runtime inventory remains disconnected from private
  diagnostics, so this metadata cannot affect current captures or gameplay;
  and
- the complete layered-map suite passes 239 tests across 89 focused files; and
- the authoritative bundled-Ant build compiles 772 core and 488 plugin
  sources.

Safety boundary:

- exact spatial affinity means only that the callback effect is anchored to a
  known coordinate. It does not serialize the captured object/placement,
  callback body, countdown identity, collision side effects, or restoration
  behavior;
- partial adoption must not make the global scheduler or any candidate source
  attribution-complete while other callbacks remain hints or unattributed; and
- No event is cancelled, stopped, removed by the observer, run, recreated, or
  rescheduled. No source is loaded, retained, retired, reconstructed, or gated,
  and no preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, callback restoration, and all event/lifecycle authority remain
absent.

### Slice 91: Private event-ownership diagnostics

Objective: expose Slice 89's complete bounded scheduler-affinity inventory
through the opt-in parity workflow so exact scenery callbacks can be measured
without hiding owner hints or unattributed legacy events.

Implemented:

- additive `layered-map-parity-event-v32` records retain the complete v31
  contract and add nullable `packedRegionEventOwnership` evidence;
- proposal creation, same-tick deferral, and fresh reassessment use the same
  proposal selected for preservation burden and dynamic-object records, with
  strict generation, source-count, and coordinate-order correlation;
- aggregate, proposal-source, event, candidate-ordinal, and spatial-reference
  records expose all four affinity classes and their completeness honestly;
- scheduler ordinals, owner kinds, running/countdown/execution counters, roles,
  and packed coordinates are serialized, while descriptors, UUIDs, scheduler
  keys, callback/closure implementation, and owner identity are omitted; and
- private command start and Player reconnect paths both use the one bounded
  `GameEventHandler` capture seam; records without a proposal emit null.

Automated validation status:

- the executable observer fixture emits one exact fixed-location event and one
  unattributed event over the same two-source proposal and validates both as a
  single incomplete inventory rather than dropping the unknown callback;
- schema-v32 closes every aggregate, source, event, ordinal, and reference
  shape, limits events to 65,536 and references to 262,144, fixes every
  scheduler/recovery/lifecycle authority flag false, and leaves v31 immutable;
- current-head guards, proposal correlation, both private runtime paths,
  privacy-safe serialization, and exact-plus-unknown coexistence have focused
  regression coverage; and
- the complete layered-map suite passes 244 tests across 90 focused files; and
- the authoritative bundled-Ant build compiles 772 core and 488 plugin
  sources.

Safety boundary:

- the diagnostic is a point-in-time scheduler inventory, not callback
  serialization, a scheduler key, event registry, cancellation request,
  reschedule request, restoration record, or commit token;
- exact spatial attribution does not imply restoration completeness, and
  unattributed events continue to block every candidate from claiming complete
  event evidence;
- no descriptor, UUID, callback class, closure field, owner identity, Mob/event
  handle, or Region handle crosses the JSON boundary; and
- No event is cancelled, stopped, removed by the observer, run, recreated, or
  rescheduled. No source is loaded, retained, retired, reconstructed, or gated,
  and no preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented, automated-validated, and owner-validated. Callback
restoration and all event/lifecycle authority remain absent.

### Slice 92: Dormant scenery-event restoration state

Objective: describe the detached inputs required by the two known delayed
scenery callbacks while distinguishing representable constructor/provenance
state from scheduler identity, target rebinding, and executable restoration.

Implemented:

- immutable `GameTickEventRestorationState` values default to `UNAVAILABLE`;
  existing callbacks gain no classification without an explicit override;
- the delayed scenery-spawn callback records current/permanent object IDs,
  packed coordinate, direction/type, optional owner, authored placement
  generation/source/ordinal/kind, and the `forceFullBlock` input used by its
  action;
- spawn state is a complete detached callback payload and requires no target
  binding, but remains non-executable and omits scheduler countdown/identity;
- the delayed scenery-removal callback records the target's same constructor
  values plus its opaque runtime-attribute count;
- an authored removal target reports
  `AUTHORED_PLACEMENT_IDENTITY` as detached binding evidence, while an
  identity-less target reports `LIVE_ENTITY_REFERENCE_ONLY` and cannot claim a
  complete detached callback payload; and
- authored provenance is copied into scalar state. No `GameObject`,
  `GameObjectLoc`, entity, event, World, Region, scheduler, callback, registry,
  or collection handle crosses the contract.

Automated validation status:

- an executable Java fixture proves complete authored spawn state, the
  force-full-block bit, identity-less removal refusal, authored-removal binding
  evidence, conservative legacy defaults, invalid-input refusal, and every
  inert authority flag;
- source guards isolate exactly two restoration-state overrides in `World`,
  verify both use the callback's existing captured inputs, and prove neither
  `GameEventHandler` nor the parity observer reads the new contract;
- the complete layered-map suite passes 248 tests across 91 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- detached callback-payload completeness means only that the known action's
  scalar inputs are representable. It is not proof that the event can be
  durably identified, paused, rebound, rescheduled, replayed, or recovered;
- authored placement identity is binding evidence only. No target lookup is
  performed, and identity-less removal remains dependent on an uncaptured live
  entity reference;
- opaque runtime-attribute values are not copied, and no state is exposed in
  private diagnostics yet; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No object or source is registered, removed, loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, teardown,
  transaction, rollback, or lifecycle authority is created.

Status: implemented and automated-validated. Runtime capture, private schema
exposure, owner validation, executable restoration, and all event/lifecycle
authority remain absent.

### Slice 93: Bounded scenery-event restoration capture

Objective: copy explicit Slice 92 state into the same bounded event snapshot
used by Slice 91, preserving event ordinals and exact candidate correlation
without reflection, scheduler mutation, or private schema exposure.

Implemented:

- `GameEventHandler` now reads each event's explicit restoration declaration
  while it detaches the existing scheduler snapshot; legacy unavailable state
  remains unavailable without inference from descriptor, class, owner, or null
  owner;
- spawn/remove constructor, provenance, target-binding, force-full-block,
  opaque-attribute-count, and callback-payload-completeness fields are copied
  into event-inventory-owned immutable values;
- restoration state is accepted only with `EXACT_SPATIAL` attribution and a
  `FIXED_EFFECT_LOCATION` whose coordinate exactly matches the scenery state;
  hints, unattributed/global events, and mismatched exact locations refuse;
- aggregate records distinguish available restoration state from a complete
  detached callback payload, while the existing standalone-restoration count
  remains zero;
- proposal-ordered sources list the exact scheduler ordinals carrying state,
  and event records retain state beside the same affinity/countdown evidence;
  and
- raw owner text remains internal construction input. The observer and
  schema-v32 serializer do not read or publish any new field in this slice.

Automated validation status:

- an executable Java fixture correlates one authored spawn and one identity-
  less removal with the exact candidate source, proves only the spawn has a
  complete detached callback payload, and retains standalone restoration at
  zero;
- the fixture refuses restoration state attached to an owner-position hint or
  a mismatched fixed-effect coordinate;
- source guards prove runtime mapping uses the explicit declaration without
  descriptor/class/closure reflection, scheduler mutation, object
  registration, or observer exposure;
- the complete layered-map suite passes 252 tests across 92 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- event ordinals are still point-in-time positions, not durable scheduler
  identities, registry keys, cancellation handles, leases, or commit tokens;
- callback-payload completeness does not include scheduler identity/countdown
  ownership, a performed authored-target lookup, runtime attribute values, or
  a tested replay path, so standalone restoration remains false;
- owner text is retained only inside the unpublished detached runtime value and
  must not cross a later diagnostic boundary; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  object or source is registered, removed, loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, teardown,
  transaction, rollback, or lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, executable restoration, and all event/lifecycle authority remain
absent.

### Slice 94: Private scenery-event restoration diagnostics

Objective: expose the minimum privacy-safe Slice 93 restoration facts through
the opt-in observer so one real delayed scenery callback can be checked without
publishing owner text or creating a recovery path.

Implemented:

- additive `layered-map-parity-event-v33` records preserve the complete v32
  event while adding restoration availability and detached-payload-completeness
  counts to the exact same proposal-correlated event inventory;
- proposal-ordered sources publish restoration-bearing scheduler ordinals and
  event records publish nullable spawn/removal state, force-full-block,
  target-binding evidence, and the existing false completeness boundary;
- scenery state publishes current/permanent IDs, packed coordinate,
  direction/type, owner presence, opaque runtime-attribute count, and nullable
  authored generation/source/ordinal/kind;
- Raw owner text is never serialized. The executable observer fixture uses a
  sentinel owner value and proves it is absent from the entire JSON trace; and
- historical schema-v32 remains closed and unchanged, while events without the
  explicit narrow contract retain null restoration state.

Automated validation status:

- the executable observer fixture emits one exact authored scenery-spawn
  payload beside one unattributed event, checks aggregate/source/event
  correlation, validates every record against schema-v33, and proves the raw
  owner sentinel is absent;
- structural guards prove schema-v32 has none of the new fields, schema-v33 is
  closed and bounded, only `ownerPresent` crosses the JSON boundary, and every
  scheduler/recovery/lifecycle-authority flag remains false;
- the complete layered-map suite passes 257 tests across 93 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- diagnostic availability and detached callback-payload completeness are not
  standalone restoration. No scheduler identity or callback closure is
  captured, and no target binding lookup is performed;
- authored placement metadata is evidence only, not a registry key, retained
  entity, reload request, replay instruction, or commit token;
- schema-v33 remains opt-in, private, point-in-time, detached, bounded, and
  read-only; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  object or source is registered, removed, loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, teardown,
  transaction, rollback, or lifecycle authority is created.

Private owner validation status: accepted after one timing-corrected repeat.
The earlier nine-record trace remains useful evidence of the 16-tick grace
boundary, but its too-early pending marker was not used as restoration proof.
The accepted six-record schema-v33 session validates against the closed schema,
has contiguous sequences and exact packed/layered round trips, and records:

- after 23.780 seconds away from the tree, `pending` has 60 exact proposal
  sources and 3,886 events. Exactly one event is `EXACT_SPATIAL`, carries
  restoration state, and has a complete detached callback payload;
- that event is a never-yet-run `SCENERY_SPAWN` with 22 ticks remaining for
  object 310 at `(524,489)`, candidate source ordinal 24, no owner, no runtime
  attributes, and complete constructor state;
- its authored identity is generation 1, source `(10,10)`, ordinal 22,
  `SCENERY`; target binding is `NOT_REQUIRED`, while scheduler identity,
  target lookup, standalone restoration, and every recovery/lifecycle flag
  remain false;
- the return teleport after natural respawn has zero exact events and zero
  restoration-state events, proving the first callback completed naturally;
- the owner then successfully chopped the returned tree to check interaction.
  The later `complete` marker therefore contains one new equivalent respawn
  callback with 65 ticks remaining rather than falsely describing the first
  callback as still pending; and
- raw owner text remains absent. The owner reported normal visuals, collision,
  natural respawn, and interaction.

Status: implemented, automated-validated, and owner-validated. Executable
restoration and all event/lifecycle authority remain absent.

### Slice 95: Scheduler-local event registration identity

Objective: distinguish one accepted stay in the live event store from a
rejected duplicate or a later replacement, without publishing or reusing the
event's existing UUID/key and without creating scheduler-control authority.

Implemented:

- `GameTickEventStore` assigns a positive monotonically increasing sequence
  only after an event registration is accepted;
- repeated atomic store snapshots retain the same sequence for the same
  registered instance and preserve the existing canonical tracked-event order;
- a rejected duplicate or rejected `addOrUpdate` does not consume a sequence;
  removal followed by re-registration and replacement of a stopped event each
  receive a new sequence, even if the same Java object is reused;
- registration identity is removed with the actual registered instance. A
  key-equivalent removal can no longer leave the original instance in the
  type/player indexes or identity bookkeeping; and
- the event-handle/sequence pair is package-private scheduler state. Neither
  `GameEventHandler`, the layered inventory, parity observer, schema-v33, nor
  any command or lifecycle consumer reads it in this slice.

Automated validation status:

- an executable store fixture proves accepted monotonic identity, stable
  repeated snapshots, rejected duplicate non-consumption, immutable snapshot
  lists, key-equivalent removal, re-registration, stopped-event replacement,
  and unchanged Player indexing;
- source guards prove the new snapshot boundary reads no UUID, descriptor,
  event key, owner UUID, callback, or execution/mutation method;
- source guards prove registration sequences remain scheduler-internal and are
  absent from the handler and observer;
- the complete layered-map suite passes 261 tests across 94 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- the sequence is process-local and store-local. It is not stable across
  server restart, not globally unique, not persisted, and cannot be compared
  across scheduler instances without later explicit instance evidence;
- it is neither the existing event UUID nor the duplication key and exposes no
  descriptor, class, callback, owner, coordinate, or private identity;
- snapshot entries retain event handles only inside the scheduler package so a
  later handler can detach primitive evidence; they do not cross diagnostics;
  and
- No event is cancelled, stopped, removed for lifecycle purposes, rescheduled,
  recreated, or run. No source is loaded, retained, retired, reconstructed, or
  gated, and no preservation, reload, registry, teardown, transaction,
  rollback, or lifecycle authority is created.

Status: implemented and automated-validated. Layered capture, private schema
exposure, owner validation, restart identity, executable restoration, and all
event/lifecycle authority remain absent.

### Slice 96: Bounded event registration identity capture

Objective: detach Slice 95's scheduler-local registration sequence into every
event in the existing bounded ownership inventory without changing private
schema-v33 or exposing any scheduler handle/key.

Implemented:

- `GameEventHandler` now obtains event order, live handle, and registration
  sequence from one atomic `GameTickEventStore` snapshot rather than obtaining
  an event list separately from identity bookkeeping;
- each detached event state and record carries one positive registration
  sequence beside, but distinct from, its contiguous snapshot ordinal;
- the inventory requires registration sequences to be strictly increasing in
  tracked-event order, so zero, missing, duplicate, or descending identity
  refuses the complete bounded capture rather than producing partial evidence;
- aggregate evidence reports identity for every captured event while keeping
  scheduler-instance identity and full scheduler identity explicitly false;
  and
- schema-v33 remains unchanged. Neither the observer nor JSON serializer reads
  the registration sequence in this slice.

Automated validation status:

- an executable inventory fixture proves non-contiguous positive registration
  sequences remain distinct from contiguous snapshot ordinals and that zero,
  duplicate, and descending identities refuse;
- source guards prove the handler consumes one store registration snapshot and
  copies no UUID, key, descriptor, class, callback, or owner identity;
- source guards prove the detached inventory has no event/store handles and
  the observer does not read or publish the new identity;
- the complete layered-map suite passes 265 tests across 95 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- a process-local registration sequence can correlate repeated observations
  only while the same scheduler instance remains alive. Because scheduler-
  instance identity is absent, it must not be compared across server restarts;
- complete identity capture means every bounded event has the store sequence;
  it does not mean callback, scheduler, or restoration state is complete;
- event handles stay inside `GameEventHandler` long enough to detach existing
  primitive evidence and never enter the inventory or diagnostics; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, scheduler-instance identity, executable restoration, and all
event/lifecycle authority remain absent.

### Slice 97: Private event registration identity diagnostics

Objective: expose only Slice 96's positive process-local registration sequence
through the opt-in private observer so repeated event instances can be
correlated without exposing the existing event UUID/key or creating scheduler
authority.

Implemented:

- additive `layered-map-parity-event-v34` records preserve the complete v33
  event and add aggregate registration-identity capture/count/completeness plus
  `schedulerInstanceIdentityCaptured=false`;
- every bounded event publishes its positive `registrationSequence` beside its
  existing contiguous `snapshotOrdinal`; the two values have intentionally
  different meanings and constraints;
- historical schema-v33 remains closed and unchanged. Schema-v34 closes the
  extended aggregate and event shapes and retains all existing bounds;
- neither aggregate nor event records publish UUIDs, scheduler keys,
  descriptors, classes, callbacks, owner identities, or username hashes; and
- scheduler identity, cancellation, reschedule, restoration, preservation,
  registry, arrival, teardown, and lifecycle-authority flags remain false.

Automated validation status:

- the executable observer fixture emits registration sequences 101 and 102,
  reconciles aggregate identity count with event count, and validates all
  records against closed schema-v34;
- structural guards prove schema-v33 contains none of the new fields, v34
  requires positive sequences and complete process-local evidence, and
  scheduler-instance identity remains false;
- observer source guards prove only the sequence and aggregate booleans cross
  the JSON boundary, without existing UUID/key/private scheduler state;
- the complete layered-map suite passes 270 tests across 96 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- the published sequence is meaningful only within one live scheduler
  instance. Without scheduler-instance identity it must never be compared
  across restarts or treated as a persistent/durable identifier;
- identity completeness proves only that every bounded record can be
  correlated within this process. It does not complete callback capture,
  scheduler state, target lookup, or standalone restoration;
- records remain opt-in, private, bounded, point-in-time, detached, and
  read-only; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Private owner validation status: accepted. Eight schema-v34 records validate
against the complete local schema chain. `same-a` and `same-b` contain the sole
exact magic-tree spawn callback at `(524,489)` with registration sequence 3929
while its countdown falls from 28 to 12 ticks. The return teleport after
natural respawn contains no exact callback. Re-chopping creates the same
authored spawn payload under the greater registration sequence 3968, which is
then stable through `new` and `stop`. Every bounded inventory reconciles event
and registration-identity counts, uses positive unique increasing sequences,
and retains `schedulerInstanceIdentityCaptured=false`,
`schedulerIdentityCaptured=false`, exact coordinate round trips, privacy, and
all inert-authority flags.

Status: implemented, automated-validated, and owner-validated.
Scheduler-instance identity, executable restoration, and all event/lifecycle
authority remain absent.

### Slice 98: Scheduler-instance identity scope

Objective: define one opaque scheduler-store lifetime identity so detached
registration sequences can eventually be compared only inside their valid
scope, without exposing an event identity or creating a scheduler handle.

Implemented:

- every `GameTickEventStore` creates one canonical opaque identity for its
  lifetime, independently of all event UUIDs, keys, owners, callbacks, and
  registration sequences;
- one synchronized `RegistrationSnapshot` now binds that identity to the same
  immutable accepted-order registration list. The compatibility list getter
  delegates to this atomic snapshot rather than rebuilding a second view;
- repeated snapshots from one store retain the same identity, while different
  scheduler stores receive different identities; and
- the token remains scheduler-internal. `GameEventHandler`, the detached event
  inventory, schema-v34, and the private observer do not consume or publish it.

Automated validation status:

- the executable scheduler fixture verifies canonical opaque syntax, stable
  same-store identity, distinct identity for different scheduler stores,
  registration sequence 1 in the same atomic snapshot, and immutable lists;
- structural guards prove the snapshot is built under the store lock and has
  no event stop, remove, run, clear, or scheduler-mutation path;
- current-head guards prove the handler does not yet read the identity and the
  inventory continues to report `schedulerInstanceIdentityCaptured=false`;
- the complete layered-map suite passes 275 tests across 97 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- the identity scopes one scheduler-store lifetime only. It is not durable,
  ordered, player-derived, or valid evidence that two different stores are the
  same, and it must never authorize authentication or persistence;
- the token is detached text, not a store or event reference, but it does not
  leave the scheduler boundary in this slice;
- random uniqueness distinguishes different scheduler stores for diagnostic
  correlation; it does not replace registration order or callback state; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Inventory capture, private
schema exposure, owner validation, executable restoration, and all
event/lifecycle authority remain absent.

### Slice 99: Detached scheduler-instance identity

Objective: copy Slice 98's opaque scheduler-store lifetime identity into the
bounded event inventory through the same atomic snapshot as registration order,
without exposing it through diagnostics or retaining scheduler authority.

Implemented:

- `GameEventHandler` consumes exactly one `RegistrationSnapshot`, then detaches
  its opaque scheduler-instance identity and immutable accepted-order event
  registrations into one inventory construction;
- the inventory requires canonical lowercase opaque identity for every
  capture, including an empty event list, retains it as detached text, and
  reports `schedulerInstanceIdentityCaptured=true`;
- registration sequence remains positive, unique, strictly increasing accepted
  order and independently reconciled with event count;
- full `schedulerIdentityCaptured` remains false because no scheduler state,
  handle, callback, key, execution cursor, or mutation interface is captured;
  and
- schema-v34 remains unchanged. Its observer writes literal
  `schedulerInstanceIdentityCaptured=false` and publishes neither the token nor
  the internal true inventory state until an additive schema is approved.

Automated validation status:

- the executable inventory fixture retains the exact detached token alongside
  non-contiguous registration sequences and refuses null or malformed instance
  scope, duplicate registration identity, and descending registration order;
- handler guards prove one atomic store snapshot supplies both token and list,
  with no independent scheduler read or event mutation path;
- current-head guards prove schema-v34 remains closed, false, and token-free,
  and the observer does not call the new inventory getter;
- the complete layered-map suite passes 280 tests across 98 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- captured instance scope permits only equality comparison between detached
  observations. It is not a durable server identifier, credential, replay key,
  scheduler handle, or proof that callback state is restorable;
- requiring identity for empty inventories prevents an empty capture from
  becoming ambiguously unscoped, but proves nothing about scheduler contents
  beyond the bounded point-in-time snapshot;
- diagnostics continue to state false until an additive private schema can
  publish both the token and its semantics atomically; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, executable restoration, and all event/lifecycle authority remain
absent.

### Slice 100: Private scheduler-instance scope diagnostics

Objective: expose only Slice 99's detached opaque scheduler-instance identity
through an additive private contract so registration comparisons can reject
cross-restart ambiguity without exposing scheduler state or control.

Implemented:

- additive `layered-map-parity-event-v35` retains the complete current event
  shape while extending only the event-ownership aggregate with one required
  canonical `schedulerInstanceIdentity` and
  `schedulerInstanceIdentityCaptured=true`;
- Historical schema-v34 remains closed, token-free, immutable, and explicitly
  `schedulerInstanceIdentityCaptured=false` for already-captured records;
- the observer serializes the inventory's detached identity through the shared
  JSON string escaper and does not read the store, registration snapshot,
  existing event UUID/key, callback, class, owner, or scheduler handle;
- full `schedulerIdentityCaptured`, callback-state, cancellation, reschedule,
  restoration, preservation, reload, registry, teardown, and lifecycle flags
  remain false; and
- the diagnostics README defines the equality rule: registration sequences may
  be compared only when the scheduler-instance identity also matches, and a
  private server restart must create a different scope.

Automated validation status:

- the executable observer fixture emits the canonical detached scope alongside
  registrations 101 and 102 and validates all emitted records against closed
  schema-v35 using the complete local schema chain;
- structural guards prove v34 remains false and token-free, v35 requires a
  canonical token and true capture, and every scheduler/event/lifecycle
  authority remains false;
- observer guards prove only the detached inventory getter crosses JSON and no
  scheduler or event handle reaches diagnostics;
- the complete layered-map suite passes 285 tests across 99 focused files; and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- the token permits equality comparison only; it is not ordered, durable,
  player-derived, a credential, a persistent server identity, or authority to
  inspect or mutate a scheduler;
- a matching token scopes registration sequences to one store lifetime but
  does not prove callback/restoration state complete or authorize replay;
- a differing token makes cross-instance registration comparison invalid even
  when numeric sequences happen to match; and
- No event is cancelled, stopped, removed, rescheduled, recreated, or run. No
  source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Private owner validation status: accepted. Fifteen schema-v35 records across
three closed sessions validate against the complete local schema chain. Phase
A retains token `83f7d250-5b6c-443f-848b-25aaee7a6880` and registration 3901
while countdown falls from 40 to 23 ticks; natural completion leaves the
return/stop records empty of exact callbacks. The mistaken first Phase B
attempt is a separate four-record session with no proposal/event inventory and
is ignored without contaminating either accepted session. The clean restarted
Phase B retains token `f8af12f8-2c71-4226-a99a-36ff379f230f` and registration
3960 while countdown falls from 31 to 14 to 4 ticks. The two runtime tokens
differ, so their numeric registration sequences are correctly incomparable.
Every proposal-bearing inventory reconciles event/identity counts, retains
exact coordinate round trips and the same complete authored spawn payload,
keeps full scheduler identity and every authority flag false, and the owner
reports normal tree return, visuals, collision, and interaction.

Status: implemented, automated-validated, and owner-validated. Executable
restoration and all event/lifecycle authority remain absent.

### Slice 101: Dormant scenery-event execution semantics

Objective: explicitly describe how the two known delayed scenery callbacks
execute and how their timers should progress while a packed source is absent,
without capturing live timing or creating replay behavior.

Implemented:

- `GameTickEventRestorationState` adds closed `ExecutionSemantics` and
  `TimeProgressionPolicy` values. Unavailable callbacks carry only
  `UNAVAILABLE`; known scenery spawn/removal callbacks carry `ONE_SHOT` and
  `CONTINUE_SERVER_TICKS`;
- both existing factories bind the semantics directly to their already-known
  callback contract rather than inferring behavior from a class name,
  descriptor, reflection, UUID, or scheduler key;
- the continuing-tick policy records the intended MMORPG behavior: resource
  respawn/removal time continues while its source is absent, so a later overdue
  reconstruction must happen before player arrival instead of freezing the
  timer off-screen;
- `isExecutionSemanticsCaptured` distinguishes an explicit contract from an
  unavailable legacy callback; and
- scheduler identity, countdown, atomic timing, target lookup, arrival
  ordering, replay, cancellation, reschedule, restoration, and lifecycle
  authority remain explicitly outside this value.

Automated validation status:

- the executable restoration-state fixture proves spawn, authored removal, and
  identity-less removal all carry the one-shot/continuing-tick contract while
  unavailable state carries neither;
- construction guards require the two enum values to move together and refuse
  a known scenery value without its explicit execution contract;
- current-head guards prove the handler, detached inventory, schema-v35, and
  observer do not yet consume or publish these semantics;
- the complete layered-map suite passes 290 tests across 100 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- `ONE_SHOT` describes callback lifecycle; it is not a callback class, event
  handle, or permission to invoke it;
- `CONTINUE_SERVER_TICKS` is a future timing policy, not an implemented clock,
  due tick, arrival gate, or replay instruction;
- current running/countdown/execution fields remain observational and are not
  promoted to atomic restoration state; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Inventory capture, private schema
exposure, owner validation, atomic timing, executable restoration, and all
event/lifecycle authority remain absent.

### Slice 102: Detached scenery-event execution semantics

Objective: carry Slice 101's explicit one-shot and timer-progression contract
into the bounded event inventory without promoting observational countdown
fields to atomic restoration timing.

Implemented:

- the handler copies `ExecutionSemantics` and `TimeProgressionPolicy` by exact
  closed-enum name from the declared callback restoration state; it does not
  infer either value from an implementation class, descriptor, UUID, key, or
  owner;
- detached known scenery spawn/removal states require `ONE_SHOT` together with
  `CONTINUE_SERVER_TICKS`; unavailable states require both values remain
  `UNAVAILABLE`, and mismatched combinations refuse construction;
- the aggregate records a separate execution-semantics captured-event count,
  whether any semantics are captured, and whether every restoration-state-
  available event carries semantics;
- callback-payload completeness, execution-semantics completeness, and
  standalone-restoration completeness remain distinct; and
- atomic timing remains false with zero captured events. Existing running,
  countdown, and `timesRan` values remain non-atomic observations.

Automated validation status:

- the executable inventory fixture proves two known restoration states produce
  two complete semantic records, retain exact one-shot/continuing-tick values,
  and still report zero atomic timing and zero standalone restoration;
- the fixture refuses an unavailable/continuing mismatch, while existing exact
  spatial-affinity and registration-order refusals remain intact;
- handler guards prove explicit enum mapping and no class/descriptor/UUID/key
  inference or event mutation;
- current-head guards prove schema-v35 and the observer publish none of the new
  fields yet;
- the complete layered-map suite passes 295 tests across 101 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- semantics completeness means every currently available callback-restoration
  state has an explicit execution contract; it does not mean every scheduler
  event is attributable or restorable;
- continuing server ticks do not yet produce a captured due tick, execute an
  overdue callback, or order reconstruction before arrival;
- non-atomic countdown fields cannot support cancellation/replay correctness
  and remain diagnostic only; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, atomic timing, executable restoration, and all event/lifecycle
authority remain absent.

### Slice 103: Private scenery-event execution-semantics diagnostics

Objective: expose only Slice 102's closed detached execution contract through
an additive private schema while keeping the existing countdown and execution
counters explicitly non-atomic and unusable for replay.

Implemented:

- additive `layered-map-parity-event-v36` retains the complete current event
  shape while adding aggregate semantic captured/complete evidence and
  per-restoration-state `ONE_SHOT` plus `CONTINUE_SERVER_TICKS` values;
- Historical schema-v35 remains closed and immutable, so already-captured
  records retain their exact contract without silently acquiring later claims;
- aggregate and event-level `atomicTimingCaptured` remain false, the captured
  atomic-timing count remains zero, and existing `running`, `ticksBeforeRun`,
  and `timesRan` fields remain point-in-time observations;
- callback-payload completeness, execution-semantics completeness, atomic
  timing, and standalone-restoration completeness remain separate claims; and
- the observer serializes only the detached inventory values. It receives no
  event/store handle, scheduler key, callback, target binding, execution
  cursor, or mutation operation.

Automated validation status:

- the executable observer fixture emits one known scenery restoration state
  with one-shot/continuing-tick semantics, reconciled aggregate counts, and
  explicit false/zero atomic timing, and validates every emitted record against
  schema-v36 through the complete local schema chain;
- structural guards prove v35 remains unchanged, v36 requires the new closed
  values, and full scheduler identity plus every event/lifecycle authority
  remains false;
- observer guards prove only detached inventory getters cross the JSON
  boundary and no store, event, callback, cancellation, reschedule, or run path
  is reachable;
- the complete layered-map suite passes 300 tests across 102 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- `ONE_SHOT` and `CONTINUE_SERVER_TICKS` describe intended callback behavior;
  they do not identify a live callback or authorize invoking it;
- a falling observed countdown can support owner inspection but cannot prove a
  due tick, preserve timing across teardown, or make replay safe because timing
  capture is explicitly non-atomic;
- semantic completeness covers only events with an available narrow
  restoration-state contract; it does not make unattributed callbacks or the
  whole scheduler restorable; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Private owner validation status: accepted. Six contiguous schema-v36 records
validate against the complete local schema chain. Both pending markers retain
scheduler-instance token `ae1af8a0-a355-4aeb-93dc-23d4de947e4e` and
registration 3900 while the observed countdown falls exactly from 24 to 10
across 14 server ticks. Each contains the same exact authored magic-tree spawn
at `(524,489)`, reports `ONE_SHOT` with `CONTINUE_SERVER_TICKS`, reconciles one
captured and complete semantic record, and keeps atomic timing false/zero and
standalone restoration false. The return teleport and stop retain the same
instance scope but contain no exact callback after natural completion. Every
registration/count invariant reconciles, all authority flags remain false,
coordinate round trips remain exact, and the owner completed the return and
interaction checks with no issue reported.

Status: implemented, automated-validated, and owner-validated. Atomic timing,
executable restoration, and all event/lifecycle authority remain absent.

### Slice 104: Atomic scheduler event-timing foundation

Objective: capture the smallest timing tuple for one accepted event under one
event-local timing lock and bind it to one scheduler observation tick and
registration set without publishing or consuming it yet.

Implemented:

- `GameTickEvent` owns one private timing lock for its tick/due decision,
  execution-count/countdown completion, stop transition, and immutable
  running/remaining-ticks/execution-count snapshot. A separate private
  execution lock serializes `doRun` calls;
- arbitrary callback code executes between the two timing transitions without
  holding the timing monitor. A concurrent snapshot may therefore return the
  coherent active tuple before callback completion without acquiring callback-
  owned plugin/entity monitors;
- `GameTickEventStore` adds a read-only two-phase timing snapshot: it first
  copies store scope and accepted registrations, releases the store lock while
  taking each event-local snapshot, then verifies the registration version is
  unchanged before returning one immutable store-scope/observation-tick list;
- this lock order prevents the store lock from being held across callback
  lifecycle capture. Any add, remove, replacement, or re-registration during
  capture changes the version and refuses the whole snapshot as mixed-
  registration evidence; and
- the existing registration-only snapshot remains available unchanged. The
  handler does not consume the new timing snapshot, and the detached inventory,
  schema-v36, observer, and already-captured records remain explicitly
  non-atomic.

Automated validation status:

- an executable event fixture recreates a callback/foreign-monitor inversion
  and proves timing capture returns the active due tuple without waiting for
  arbitrary callback code; after callback completion it receives the stopped/
  reset/one-execution tuple;
- the executable store fixture binds canonical scheduler scope, observation
  tick, registration identity, and event-local timing, keeps the returned list
  immutable, refuses a negative observation tick, and deterministically
  refuses a registration added during a blocked capture;
- structural guards prove the handler and observer do not reach the new store
  method, while detached atomic-timing counts and flags remain zero/false;
- the complete layered-map suite passes 305 tests across 103 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- atomicity applies to the captured primitive timing tuple and its validated
  accepted registration set; it does not make callback inputs, target binding,
  authored reconstruction, or the whole scheduler independently restorable;
- the observation tick labels the scheduler capture and does not become a due
  tick, wall-clock deadline, frozen-time policy, or replay instruction;
- a registration-version race refuses evidence rather than retrying, blocking
  an event, or mutating the scheduler; and
- No callback is cancelled, stopped by the capture, removed, rescheduled,
  recreated, or run by the capture. No source is loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, registry, teardown,
  transaction, rollback, or lifecycle authority is created.

Status: implemented and automated-validated. Detachment, private schema
exposure, owner validation, executable restoration, and all event/lifecycle
authority remain absent.

### Slice 105: Detached atomic scenery-event timing

Objective: make the handler consume exactly one Slice 104 scheduler snapshot
and detach its atomic timing tuple only when the same registration has the
known one-shot/continuing-tick restoration contract.

Implemented:

- the handler requests one `StoreAtomicTimingSnapshot` at the refinement
  observation tick and uses its detached scheduler-instance identity,
  registration order, and event-local timing values; it no longer reads
  `isRunning`, `ticksBeforeRun`, or `timesRan` independently from a live event;
- the inventory records atomic timing per event only when explicit execution
  semantics are present. Unavailable legacy callbacks retain their visible
  observational values but remain explicitly non-atomic;
- aggregate captured-event count, any-captured state, and completeness remain
  distinct from callback-payload completeness, execution-semantics
  completeness, and standalone-restoration completeness;
- construction refuses an atomic-timing claim without explicit execution
  semantics, while a known restoration state deliberately lacking atomic
  timing remains representable as incomplete evidence; and
- Historical schema-v36 remains immutable and pinned to false/zero atomic
  timing. The current observer emits those literal legacy values until an
  additive private schema is approved, so this internal detachment does not
  silently change already-defined diagnostics.

Automated validation status:

- the executable inventory fixture proves one known event retains its atomic
  running/remaining/execution tuple and reconciles complete aggregate timing,
  while an unknown event remains non-atomic;
- the same fixture refuses atomic timing on unavailable semantics and preserves
  a known but deliberately non-atomic state as visible incomplete evidence;
- handler guards require exactly one atomic store snapshot, its observation
  tick and timing object, and prohibit the previous independent live timing
  getters or any event mutation path;
- current-head guards prove schema-v36 and the observer remain explicit
  false/zero even though the private inventory can now carry true timing;
- the complete layered-map suite passes 310 tests across 104 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- atomic timing proves only that three primitive values belonged to the same
  accepted registration and observation. It is not a callback handle, due
  tick, scheduler cursor, reschedule instruction, or proof that target binding
  and reconstruction are complete;
- unknown callbacks remain explicitly non-atomic even though the scheduler
  snapshot obtains their point-in-time values, because no execution contract
  makes those values safe to interpret for restoration;
- standalone restoration remains false for every event, including a known
  authored scenery spawn with complete payload, semantics, and timing; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Private schema exposure, owner
validation, executable restoration, and all event/lifecycle authority remain
absent.

### Slice 106: Private atomic scenery-event timing diagnostics

Objective: expose Slice 105's already-detached timing evidence through one
additive private schema without granting the observer a live scheduler or
event handle.

Implemented:

- additive `layered-map-parity-event-v37` preserves the complete v36 record
  and adds aggregate `atomicTimingComplete` plus one required per-event
  `atomicTimingCaptured` flag;
- aggregate captured count, any-captured state, and completeness now serialize
  directly from the bounded inventory. A known restoration record publishes
  true at both the event and restoration levels, while unavailable callbacks
  remain visible with a false event flag and null restoration state;
- the existing observation tick, running state, remaining delay, and execution
  count are unchanged primitives from Slice 105's one version-fenced store
  snapshot. The observer receives only the inventory and does not query the
  scheduler, event lifecycle lock, callback, or due-event executor;
- Historical schema-v36 remains immutable with zero captured timing and false
  aggregate/restoration flags, so existing JSONL retains its exact closed
  contract; and
- the layered-maps guide identifies v37 as current, retains v36 as a historical
  contract, and describes the narrow meaning and explicit limits of atomic
  timing provenance.

Automated validation status:

- the executable observer fixture emits one known atomic event and one unknown
  non-atomic event, reconciles aggregate count one and completeness true, and
  validates every record against schema-v37 through the full historical schema
  registry;
- schema guards prove v36 remains closed false/zero while v37 adds only the
  aggregate, event, and known-restoration timing claims;
- observer guards require only detached inventory getters and prohibit store,
  live-event, callback execution, cancellation, reschedule, or mutation paths;
- the complete layered-map suite passes 315 tests across 105 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources; and
- after Slice 107 corrected the callback-lock cycle, the accepted private route
  emits six schema-valid v37 records. Markers at observation ticks 323 and 334
  retain scheduler token `eab805ac-36bb-4d5b-824d-21c0e4b8d52a`, registration
  3945, and the same magic-tree spawn at `(524,489)` while remaining ticks fall
  exactly 39 to 28. Aggregate/event/restoration atomic claims all reconcile,
  and the later teleport and stop report zero restoration/atomic events after
  natural completion.

Safety boundary:

- atomic timing means only that the published running/countdown/execution
  tuple belongs to the same accepted registration at the inventory observation
  tick. It is not a due tick, wall-clock deadline, replay cursor, or scheduler
  checkpoint;
- timing completeness is measured only against the known restoration records
  in this bounded observation. It does not claim unknown callbacks are
  restorable or that target binding, authored reconstruction, or arrival
  ordering is complete;
- standalone restoration, scheduler identity, callback state, target lookup,
  and preservation remain false; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented, automated-validated, and owner-validated. Executable
restoration, arrival ordering, and all event/lifecycle authority remain absent.

### Slice 107: Atomic timing callback-lock deadlock hardening

Objective: correct the lock inversion discovered by the first Slice 106 owner
route while preserving serialized event execution and the detached atomic
timing contract.

Owner finding and exact root cause:

- `::lp mark atomic-a` reached the private server at tick 239 but never
  completed. Normal ticks stopped, the client timed out, and later login
  attempts received response 4 because the original Player could not reach
  unregister cleanup;
- a read-only `jstack` of the wedged private process proved one Java-level
  deadlock. `GameThread` held the executing `PluginTickEvent` timing monitor
  while waiting for the plugin task's monitor; `PluginThread-0` held that task
  monitor while the observer requested the same event's atomic timing monitor;
  and
- the command text, client transport, account, and login cleanup were not the
  cause. The disconnect and stale logged-in state were downstream effects of
  the stalled game tick.

Implemented:

- `GameTickEvent` now has a private execution lock that retains one-at-a-time
  `doRun` behavior without participating in diagnostic capture;
- the timing lock covers only the pre-callback tick/due decision and the
  post-callback execution-count/countdown transition. Arbitrary `run()` code
  executes between those critical sections and can no longer carry the timing
  monitor into plugin, entity, or callback-owned locks;
- `captureAtomicTimingSnapshot` remains under the timing lock. During callback
  execution it may return the coherent active tuple—running lifecycle state,
  due countdown, and pre-completion execution count—then a later capture sees
  the complete reset/increment state; and
- schema-v37, inventory construction, registration version fencing, callback
  payloads, execution semantics, and every diagnostic authority flag are
  unchanged.

Automated validation status:

- the executable event fixture recreates the exact foreign-monitor cycle: the
  callback thread waits for a monitor owned by the capture thread while that
  owner requests the event timing snapshot. Capture must finish within one
  second with the active due tuple, release its foreign monitor, and allow the
  event to complete with one execution and a reset countdown;
- structural guards prove `run()` lies between—not inside—the timing critical
  sections, while the private execution lock retains serialized `doRun`;
- observer guards prove serialization still consumes detached primitives and
  gains no timing/execution lock, live event/store, callback, stop, or run path;
- the complete layered-map suite passes 320 tests across 106 focused files;
  and
- the authoritative bundled-Ant build compiles 773 core and 488 plugin
  sources.

Safety boundary:

- the change narrows a lock scope; it does not make event execution concurrent
  with another `doRun` for the same event, introduce callback interruption, or
  grant diagnostics an execution lock;
- an active timing tuple is an observation, not permission to replay, cancel,
  reschedule, complete, or reconstruct that callback;
- registration-version changes still refuse the store snapshot instead of
  publishing mixed accepted stays; and
- No callback is cancelled, stopped by capture, removed, rescheduled,
  recreated, or run by capture. No source is loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, registry, teardown,
  transaction, rollback, or lifecycle authority is created.

Status: implemented, automated-validated, and owner-validated. The corrected
route completes both markers without disconnect or tick stall, preserves exact
timing arithmetic, and reaches natural completion; executable restoration,
arrival ordering, and all event/lifecycle authority remain absent.

### Slice 108: Dormant scenery target and arrival requirements

Objective: define the fail-closed target-binding and pre-visibility ordering
requirements for the two known scenery callbacks without performing a target
lookup, reconstructing a source, or gating player arrival.

Runtime inspection finding:

- delayed scenery spawn currently calls `registerGameObject`, whose normal
  collision behavior unregisters the occupant at the destination. Exact X/Y is
  therefore insufficient recovery authority: a future restoration path must
  prove the authored destination slot before it may replace anything;
- delayed scenery removal closes over one live `GameObject` and calls
  `unregisterGameObject` on that reference. Once a source has been torn down,
  only detached authored placement identity can safely name the intended
  reconstructed entity; an identity-less live reference cannot be rebound;
- successful login is loaded on the asynchronous login executor, then queued
  as a non-player `ImmediateEvent`. Its `loadingComplete` path calls
  `ActionSender.sendLogin`, which registers the Player and builds the first
  scenery visibility snapshot inside that same event. A future source recovery
  cannot rely on finishing later in the tick; reconciliation must precede that
  first snapshot; and
- the same requirement applies to movement and teleport arrivals even though
  no source-loading path or shared arrival seam exists yet.

Implemented:

- immutable `GameTickEventRestorationRequirement` derives only from Slice 101's
  detached restoration state and copies authored target identity into scalar
  generation, source-coordinate, ordinal, and construction-kind fields;
- authored spawn declares `AUTHORED_DESTINATION_SLOT`, while authored removal
  declares `AUTHORED_EXISTING_ENTITY`. Both require authored placement identity
  and `REFUSE_MISMATCH_OR_AMBIGUITY`; exact coordinates alone never satisfy the
  binding;
- identity-less spawn and removal remain visible as
  `MISSING_AUTHORED_PLACEMENT_IDENTITY` with incomplete binding. Unknown
  callbacks remain wholly unavailable rather than inheriting scenery rules;
- `RECONCILE_BEFORE_FIRST_VISIBILITY` states the ordering rule for continuing
  server time: an overdue one-shot result must already be reflected before the
  first snapshot, while a not-yet-due callback must expose its pending transient
  state and resumed remaining timer before that snapshot; and
- the value explicitly reports false for target lookup, arrival gate,
  executable restoration, and lifecycle authority. It retains no World,
  Region, entity, event, scheduler, callback, registry, login, or packet handle.

Automated validation status:

- an executable Java fixture proves authored spawn/removal produce different
  exact target subjects with the same fail-closed conflict and arrival rules;
- the fixture proves identity-less variants cannot claim complete binding,
  unavailable callbacks gain no inferred rule, and every path remains dormant;
- structural guards trace the existing login event through registration to its
  immediate first visibility snapshot, and verify non-player events execute
  before normal Player processing;
- observer guards prove schema-v37 and private diagnostics do not consume or
  publish the dormant requirement; and
- the complete layered-map suite passes 325 tests across 107 focused files,
  while the authoritative bundled-Ant server build compiles 774 core and 488
  plugin sources.

Safety boundary:

- authored identity is evidence a future resolver must validate, not proof
  that a corresponding current entity exists. No target lookup is performed;
- the arrival requirement is an ordering invariant, not an arrival gate,
  player-registration hook, visibility suppressor, loader, or due-event
  executor;
- the contract does not decide how an authored predecessor is related to a
  later population generation, whether an absent same-identity target is an
  idempotent success, or how callback completion is journaled. Those questions
  remain fail-closed for later slices; and
- No callback is cancelled, stopped, removed, rescheduled, recreated, or run.
  No source is loaded, retained, retired, reconstructed, or gated, and no
  preservation, reload, registry, teardown, transaction, rollback, or
  lifecycle authority is created.

Status: implemented and automated-validated. Inventory detachment, private
diagnostic exposure, owner validation, executable restoration, arrival gating,
and all lifecycle authority remain absent.

### Slice 109: Detached scenery target and arrival requirements

Objective: copy Slice 108's dormant target and arrival requirements into the
bounded event inventory only when they reconcile with the same known scenery
restoration state.

Implemented:

- `GameEventHandler` derives one immutable requirement from the already-
  detached callback state, maps only its closed enums, and verifies every
  authored target scalar against both the source restoration state and its
  inventory copy before constructing an inventory record;
- each known inventory restoration record now retains target subject, binding
  evidence, fail-closed conflict policy, and pre-visibility ordering. Spawn
  must declare `AUTHORED_DESTINATION_SLOT`; removal must declare
  `AUTHORED_EXISTING_ENTITY`; and the authored/missing evidence must agree with
  the detached scenery provenance;
- identity-less callbacks still capture a complete target-binding requirement
  but report incomplete target binding. This distinguishes “the required rule
  is known” from “the evidence needed to satisfy it is present”;
- aggregate counts independently reconcile restoration-state availability,
  target-requirement capture, satisfied authored bindings, and arrival-order
  capture. Empty inventories remain vacuously complete without claiming any
  captured target or arrival evidence; and
- schema-v37 and the observer remain unchanged. The new values are private
  inventory state and do not yet alter JSONL, callback execution, source
  loading, Player registration, or visibility.

Automated validation status:

- the executable inventory fixture contains one authored spawn and one
  identity-less removal. It proves two captured binding requirements, one
  satisfied binding, and two captured arrival requirements while standalone
  restoration remains false;
- the same fixture verifies the distinct spawn/removal subjects, authored and
  missing evidence, conflict refusal, arrival rule, and rejection of an
  inconsistent explicit subject/evidence combination;
- handler guards require one Slice 108 derivation, closed-enum mapping, and
  scalar-by-scalar target reconciliation while prohibiting object mutation,
  event execution, scheduler mutation, and packet/arrival operations;
- observer guards prove the v37 serializer receives none of the new getters or
  enum values; and
- the complete layered-map suite passes 330 tests across 108 focused files,
  while the authoritative bundled-Ant server build compiles 774 core and 488
  plugin sources.

Safety boundary:

- a complete requirement says the recovery rule is explicit; a complete
  binding says the detached authored identity is present. Neither says that a
  current target was found, that its state matches, or that mutation is safe;
- target and arrival counts are inventory arithmetic over one observation, not
  a target index, population-generation translator, journal, due-event cursor,
  arrival gate, or restoration decision;
- historical schema-v37 and accepted private captures retain their exact
  contract; and
- No target lookup is performed. No callback is cancelled, stopped, removed,
  rescheduled, recreated, or run. No source is loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, registry, teardown,
  transaction, rollback, or lifecycle authority is created.

Status: implemented and automated-validated. Private diagnostic exposure,
owner validation, executable restoration, arrival gating, and all lifecycle
authority remain absent.

### Slice 110: Private scenery target and arrival diagnostics

Objective: expose Slice 109's already-detached target and arrival requirements
through one additive private schema without granting the observer a target,
callback, scheduler, or Player-arrival handle.

Implemented:

- additive `layered-map-parity-event-v38` preserves the complete v37 record and
  adds aggregate target-requirement, satisfied-binding, and arrival-order counts
  plus captured/completeness flags;
- each known restoration record publishes its closed target subject, authored-
  identity or missing-identity evidence, mismatch-refusal policy, requirement-
  capture and satisfied-binding flags, and pre-visibility ordering requirement;
- v38 schema conditions bind spawn to `AUTHORED_DESTINATION_SLOT`, removal to
  `AUTHORED_EXISTING_ENTITY`, authored evidence to a non-null authored placement
  and true binding, and missing evidence to null placement and false binding;
- Historical schema-v37 remains immutable and contains none of the new fields,
  so accepted JSONL preserves its exact closed contract; and
- the layered-maps guide identifies v38 as current and explains that the
  arrival value is an ordering invariant rather than an implemented gate.

Automated validation status:

- the executable observer fixture emits one authored atomic spawn and one
  unknown event, reconciles aggregate requirement/binding/arrival counts, and
  validates every record against schema-v38 through the full historical schema
  registry;
- schema guards prove v37 remains unchanged, v38 distinguishes complete rule
  capture from satisfied target binding, and authored/missing evidence is
  fail-closed;
- observer guards require only detached inventory getters and prohibit the
  Slice 108 requirement object, live events/store, object mutation, packet
  sends, callback execution, cancellation, or rescheduling;
- the complete layered-map suite passes 335 tests across 109 focused files;
  and
- the authoritative bundled-Ant server build compiles 774 core and 488 plugin
  sources.

Safety boundary:

- a true aggregate or per-restoration binding flag means authored identity was
  present in the detached snapshot. It does not prove the target currently
  exists or authorize a lookup, replacement, or removal;
- `RECONCILE_BEFORE_FIRST_VISIBILITY` describes the required future order. It
  does not intercept login, teleport, movement, Player registration, or packet
  visibility;
- standalone restoration, target lookup, callback execution, scheduler
  identity, preservation, and every event/lifecycle authority remain false;
  and
- No target lookup is performed. No callback is cancelled, stopped, removed,
  rescheduled, recreated, or run. No source is loaded, retained, retired,
  reconstructed, or gated, and no preservation, reload, registry, teardown,
  transaction, rollback, or lifecycle authority is created.

Private owner validation status: accepted. The first owner route produced two
complete start/marker/stop sequences and all 11 records validate against
schema-v38, but neither sequence exercised the bounded event inventory. The
player remained within source region `(10,10)`, so every marker correctly
reported zero tracked retirement candidates and null preservation-burden,
dynamic-preservation, and event-ownership sections. This is an inconclusive
route rather than a runtime or schema failure. A corrected repeat must create
the magic-tree callback, teleport outside the original visibility window, wait
past the 16-tick retirement grace, capture two pending markers, then return
after natural completion.

The corrected seven-record route validates against the complete schema-v38
chain. Both pending markers retain one scheduler-instance scope and
registration `4021`; observation ticks advance `3992` to `4014` while the
atomic remaining delay falls exactly `37` to `15`. Each marker contains the
sole exact magic-tree spawn at `(524,489)`, its authored placement identity at
source `(10,10)` ordinal 22, `AUTHORED_DESTINATION_SLOT`, authored binding,
mismatch refusal, and `RECONCILE_BEFORE_FIRST_VISIBILITY`. Aggregate and event
claims reconcile. The return teleport, marker, and stop contain zero
restoration records after natural completion. Exact coordinate round trips,
username privacy, and every inert authority flag hold; the owner reports no
visual, collision, interaction, or respawn issue.

Status: implemented, automated-validated, and owner-validated. Executable
restoration, arrival gating, and all lifecycle authority remain absent.

### Slice 111: Dormant generation and idempotency prerequisites

Objective: define the next fail-closed prerequisite for known scenery callback
restoration before permitting any target lookup, state inspection, or mutation.

Implemented:

- every known scenery requirement must match its authored placement generation
  to the reconstruction generation; a callback retained from another authored
  population pass cannot bind by coordinate or ordinal coincidence;
- spawn describes `AUTHORED_SCENERY_PRESENT` as its desired state and may only
  mutate what was initially described as an empty destination slot, while removal describes
  `AUTHORED_SCENERY_ABSENT` and may only mutate the exact authored entity;
- both operations use `ALREADY_SATISFIED_IS_NO_OP_SUCCESS`, separating a
  successful desired-state reconciliation from whether a side effect is
  needed and preventing repeated reconciliation from duplicating a spawn or
  removal;
- missing authored identity retains the closed rule descriptions but still
  cannot bind a target, while an unavailable callback gains none of the new
  requirements; and
- generation binding, desired state, idempotency, and mutation precondition are
  immutable detached enums. The value retains no reconstruction generation,
  target, entity, Region, World, event, scheduler, callback, packet, registry,
  or arrival handle.

Automated validation status:

- an executable compiled fixture proves the distinct spawn/removal desired-
  state and mutation-precondition pairs, the shared generation/idempotency
  rules, missing-identity refusal, and the unavailable state;
- constructor guards require every known rule and refuse a desired-state or
  precondition pair that disagrees with its target subject;
- source guards prohibit runtime model/network imports, target lookup, object
  registration/removal, packet sends, callback execution, and event mutation;
- the complete layered-map suite passes 339 tests across 110 focused files;
  and
- the authoritative bundled-Ant server build compiles 774 core and 488 plugin
  sources.

Safety boundary:

- `MATCH_RECONSTRUCTION_GENERATION` is a requirement only. This slice has no
  candidate reconstruction generation and performs no comparison;
- an idempotent desired state does not claim the target is present, absent,
  empty, exact, safe to mutate, or already satisfied;
- No target state is inspected. No generation match, target lookup, object
  mutation, callback execution, cancellation, reschedule, replay, arrival
  gate, reconstruction, or preservation is performed; and
- every event, scheduler, registry, teardown, transaction, rollback, packet,
  arrival, and lifecycle authority remains absent.

Status: implemented and automated-validated. Inventory detachment, generation
comparison, diagnostics, executable restoration, and all lifecycle authority
remain absent.

### Slice 112: Detached generation and idempotency prerequisites

Objective: copy Slice 111's closed rules into the bounded event inventory and
compare authored generation only with the exact proposal generation already
owned by that immutable inventory.

Implemented:

- each known restoration state detaches generation-binding requirement,
  desired state, idempotency policy, and mutation precondition alongside its
  existing authored placement scalars;
- the inventory separately counts captured generation rules, authored targets
  whose generation matches its exact proposal generation, and complete
  idempotency-rule tuples;
- requirement completeness and satisfied generation binding remain distinct:
  a missing authored identity or a stale generation retains the captured rule
  but cannot contribute to the satisfied-generation count;
- spawn accepts only present/empty-slot rules and removal accepts only absent/
  exact-entity rules, while both require no-op success for an already-satisfied
  desired state; and
- the handler copies enum names from the dormant requirement and the inventory
  compares only detached authored generation with its own proposal generation.
  Neither path reads a World, Region, entity registry, target slot, packet,
  callback, or Player-arrival state.

Automated validation status:

- the executable inventory fixture uses proposal generation 7, retains one
  matching authored spawn and one identity-less removal, reconciles two
  captured generation/idempotency rules with one satisfied generation, and
  proves the same authored event rejects comparison with generation 9;
- constructor guards retain the exact spawn/removal desired-state pairs and
  refuse mismatched target, binding, generation, idempotency, or mutation-
  precondition combinations;
- handler guards require all four detached requirement mappings and prohibit
  lookup, registration, removal, packet, callback, scheduler-mutation, and
  lifecycle paths;
- schema-v38 and the observer remain unchanged and expose none of the new
  private inventory values;
- the complete layered-map suite passes 343 tests across 111 focused files;
  and
- the authoritative bundled-Ant server build compiles 774 core and 488 plugin
  sources.

Safety boundary:

- a true generation-binding aggregate means only that an authored placement
  scalar equals the observation's reconstruction-proposal generation. It does
  not prove a live target exists, is exact, or is safe to mutate;
- idempotency completeness describes a closed future decision table, not an
  inspected or achieved desired state;
- No target lookup, state inspection, object mutation, callback execution,
  cancellation, reschedule, replay, arrival gate, reconstruction, or
  preservation is performed; and
- every event, scheduler, registry, teardown, transaction, rollback, packet,
  arrival, and lifecycle authority remains absent.

Status: implemented and automated-validated. Private diagnostic exposure,
target inspection, executable restoration, and all lifecycle authority remain
absent.

### Slice 113: Private generation and idempotency diagnostics

Objective: expose Slice 112's detached generation/idempotency evidence through
one additive private schema without granting the observer a reconstruction,
target, entity, callback, scheduler, packet, or arrival handle.

Implemented:

- additive `layered-map-parity-event-v39` preserves the complete v38 record and
  adds aggregate captured-generation-rule, satisfied-generation-match, and
  captured-idempotency-rule counts plus independent completeness flags;
- each known restoration record publishes the closed generation-binding rule,
  whether its authored generation matches the enclosing proposal, its desired
  state, no-op-success policy, mutation precondition, and rule-captured flag;
- schema conditions bind spawn to present/empty-slot, removal to absent/exact-
  authored-entity, identity-less evidence to false generation binding, and a
  true generation match to an authored placement identity;
- Historical schema-v38 remains immutable and contains none of the new fields,
  preserving already-accepted JSONL as an exact closed contract; and
- the layered-maps guide identifies v39 as current and states that the new
  fields describe a decision table rather than inspected or achieved state.

Automated validation status:

- the executable observer fixture emits one generation-matched authored spawn
  and one unknown event, reconciles aggregate generation/idempotency values,
  and validates every emitted record against schema-v39 through the complete
  historical schema registry;
- schema guards prove v38 remains unchanged, spawn/removal pairs are closed,
  identity-less generation binding is false, and captured rules remain
  distinct from satisfied generation binding;
- observer guards require only detached inventory/state getters and prohibit
  requirement, event/store, target lookup, object mutation, packet, callback,
  cancellation, or reschedule access;
- the complete layered-map suite passes 348 tests across 112 focused files;
  and
- the authoritative bundled-Ant server build compiles 774 core and 488 plugin
  sources.

Safety boundary:

- `generationBindingComplete=true` means only detached authored generation
  equals the enclosing proposal generation. It does not prove a runtime object
  exists or matches;
- desired state and mutation precondition are descriptive. They do not report
  an inspected slot, selected action, achieved state, or mutation result;
- No target state is inspected. No target lookup, object mutation, callback
  execution, cancellation, reschedule, replay, arrival gate, reconstruction,
  or preservation is performed; and
- every event, scheduler, registry, teardown, transaction, rollback, packet,
  arrival, and lifecycle authority remains absent.

Private owner validation status:

- the accepted route emitted seven complete schema-v39 records in the exact
  `start`, outbound teleport, `gen-a`, `gen-b`, return teleport, `gen-return`,
  `stop` sequence, and every record validates through the complete historical
  schema registry;
- both pending markers retain scheduler registration `3892`, proposal and
  authored generation `1`, and the exact authored spawn rule: present desired
  state, empty-slot mutation precondition, and already-satisfied no-op success;
- observation ticks `1419 -> 1435` and remaining delays `33 -> 17` reconcile
  by the same 16-tick delta without changing the scheduler registration;
- after natural completion, the return marker and stop record contain zero
  restoration, generation-binding, and idempotency requirements while all
  completeness arithmetic remains true;
- privacy round trips succeed and every registry, callback, mutation, packet,
  arrival-gate, reconstruction, and lifecycle authority flag remains false;
  and
- the owner completed the visual route without reporting an issue.

Status: implemented, automated-validated, and owner-validated. Target-state
inspection, executable restoration, and all lifecycle authority remain absent.

### Slice 114: Correct authored-transient spawn prerequisite

Objective: correct the dormant spawn decision table before target inspection
is introduced, preserving prior diagnostic history while representing the
existing harvest/replacement lifecycle accurately.

Audit finding:

- `World.replaceGameObject` transfers the old object's authored placement
  identity to both the replacement location and replacement entity before it
  unregisters the old object and registers the new one;
- authentic woodcutting and custom harvesting replace a resource with a stump
  or depleted object and then schedule the original authored location for
  delayed spawn; therefore a valid pending spawn commonly has one exact-
  identity authored transient at its destination rather than an empty slot;
- the accepted v39 route correctly captured the rule it was given, but it did
  not inspect the destination. Its `DESTINATION_SLOT_EMPTY` wording is thus a
  superseded descriptive assumption, not evidence that the live tree slot was
  empty; and
- treating every occupied destination as a conflict would reject the normal
  tree/stump lifecycle, while allowing arbitrary occupancy would recreate the
  destructive collision replacement behavior this fail-closed design is meant
  to avoid.

Implemented:

- the dormant requirement and detached inventory now use
  `DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT` for scenery spawn. Empty is
  valid; exactly one occupant with the same generation-fenced authored identity
  may be a valid transient; a different identity, identity-less occupant, or
  ambiguous occupancy must still refuse;
- scenery removal retains `EXACT_AUTHORED_ENTITY_PRESENT`, because a changed
  same-identity successor must not be removed merely because the original
  delayed callback once closed over its predecessor;
- corrective schema-v40 publishes the accurate spawn prerequisite while
  schema-v39 remains byte-for-byte compatible with already captured records;
  and
- the guide identifies v40 as current and explicitly distinguishes this
  prerequisite from a performed target lookup, target-state classification,
  achieved-state decision, or mutation.

Automated validation status:

- executable Slice 111 and inventory fixtures require the corrected spawn rule
  while retaining removal, missing-identity, stale-generation, and unavailable
  refusal behavior;
- schema guards prove v39 retains its historical empty-slot value and v40 uses
  the corrected authored-transient value only for spawn;
- source guards prove replacement identity transfer occurs before replacement
  registration and the audited harvesting paths replace before scheduling the
  delayed authored spawn; and
- the observer remains detached and publishes no target state, target lookup,
  achieved-state result, entity handle, callback handle, or mutation authority.
- the complete layered-map suite passes 352 tests across 113 focused files;
  and
- the authoritative bundled-Ant server build compiles 774 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- “exact authored transient” is a prerequisite category for a later classifier,
  not a claim that the current destination has been inspected or accepted;
- schema-v40 changes no callback timing or natural resource respawn behavior;
  and
- no target lookup, state inspection, mutation, callback execution, replay,
  arrival gate, reconstruction, preservation, registry, teardown, transaction,
  rollback, packet, or lifecycle authority is added.

Status: implemented and automated-validated. Runtime target inspection, owner
validation, executable restoration, and all lifecycle authority remain absent.

### Slice 115: Dormant target-state decision classifier

Objective: define the complete fail-closed decision table over explicit,
detached target observations before any runtime seam is allowed to produce
those observations.

Implemented:

- `GameTickEventRestorationTargetDecision` accepts only a dormant requirement,
  a positive reconstruction generation, and one closed detached observation
  category. It retains no supplied requirement, target, entity, or runtime
  handle;
- a binding failure or generation failure takes precedence over occupancy
  interpretation. An unavailable rule, missing authored identity, stale
  generation, or unavailable observation yields a typed refusal before any
  no-op or mutation-precondition result;
- spawn treats empty and exact-authored-transient destinations as satisfied
  mutation preconditions, treats exact restoration scenery as already-present
  no-op success, and refuses mismatched, identity-less, or ambiguous occupancy;
- removal treats empty as already-absent no-op success, permits only exact
  restoration scenery as its mutation precondition, and refuses an authored
  transient successor rather than deleting it by inherited identity alone;
- every result contains only outcome, reason, and the supplied detached
  observation category. `MUTATION_PRECONDITION_SATISFIED` is explanatory
  evidence, not permission or an executable operation.

Automated validation status:

- an executable Java fixture covers the complete spawn and removal matrices,
  including exact desired state, exact transient, empty, mismatched/identity-
  less, ambiguous, unavailable, missing-binding, and stale-generation cases;
- the fixture proves binding/generation refusal precedence and validates all
  typed outcome/reason pairs plus explicit inert capability flags;
- structural guards prohibit runtime model/network imports, World/Region/
  entity/event handles, target lookup, object registration/removal, packet
  sends, callback execution, and lifecycle authority;
- the complete layered-map suite passes 355 tests across 114 focused files;
  and
- the authoritative bundled-Ant server build compiles 775 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- the classifier does not discover or snapshot occupancy. It interprets only
  a caller-supplied detached category;
- no result claims that state was atomically observed with the event inventory,
  remains current, or is safe to consume after the decision returns; and
- no target lookup, runtime state inspection, entity retention, mutation,
  callback execution, replay, arrival gate, reconstruction, preservation,
  registry, teardown, transaction, rollback, packet, or lifecycle authority
  is added.

Status: implemented and automated-validated. Runtime observation, inventory
detachment, private diagnostics, executable restoration, and all lifecycle
authority remain absent.

### Slice 116: Read-only restoration target observation

Objective: produce bounded, detached point-in-time evidence from current exact
object slots for the Slice 115 classifier without retaining or mutating any
runtime target.

Implemented:

- `Region.captureRestorationTargetSlotSnapshot` copies every object in the
  exact collision slot while holding the Region object monitor. Scenery slots
  match type `0`; boundary slots match type `1` plus direction. The capture
  never trusts the existing first-match `getGameObject` lookup and returns no
  entity handle;
- each ephemeral object copy includes the constructor values and authored-
  identity scalars needed for comparison. Owner text is used only for the
  in-memory equality check and is not retained by the final observation;
- `RegionManager.captureLayeredPackedRegionEventTargetObservation` walks known
  restoration records in event snapshot order under the existing layered
  lifecycle lock, correlates snapshot ordinal, registration sequence,
  scheduler-instance scope, proposal generation, and both observation ticks,
  and applies the exact Slice 115 decision table;
- a missing packed Region becomes `UNAVAILABLE`, zero relevant objects becomes
  `EMPTY`, multiple relevant objects become `AMBIGUOUS_OCCUPANCY`, and one
  object becomes exact restoration scenery, exact authored transient, or
  mismatched/identity-less from explicit scalar comparisons;
- the immutable result reconciles available/unavailable, no-op, mutation-
  precondition, and refused counts. It explicitly states that it is point-in-
  time, not atomic with the event inventory, and incapable of mutation.

Automated validation status:

- an executable Java fixture covers exact transient spawn, exact removal,
  already-absent removal, ambiguous spawn, unavailable Region, and exact state
  with incomplete authored binding, and reconciles all aggregate outcomes;
- invalid observation order, tick order, record budget, and unavailable-Region
  contents fail closed;
- Region guards require the object monitor and complete exact-slot iteration,
  while RegionManager guards require scheduler/event correlation, constructor
  and identity comparisons, and prohibit first-match lookup, registration,
  removal, callback execution, packets, and entity mutation;
- the complete layered-map suite passes 359 tests across 115 focused files;
  and
- the authoritative bundled-Ant server build compiles 776 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- the target observation occurs after the already-detached event inventory and
  is not atomic with callback execution. It is diagnostic evidence, not a
  commit token or executable precondition;
- exact authored identity identifies the placement owner but does not retain
  the current object or permit later mutation through a stale observation; and
- no object is registered, unregistered, replaced, retained, or returned. No
  event is cancelled, rescheduled, invoked, or replayed; no arrival is gated;
  and no reconstruction, preservation, registry, teardown, transaction,
  rollback, packet, or lifecycle authority is added.

Status: implemented and automated-validated. Private diagnostic exposure,
owner validation, executable restoration, arrival gating, and all lifecycle
authority remain absent.

### Slice 117: Private restoration target diagnostics

Objective: expose Slice 116's already-detached exact-slot evidence through one
additive private schema while preserving the event-inventory correlation and
every inert boundary.

Implemented:

- the existing event-ownership source gains a default-null target capture so
  historical fixtures remain valid; both real Player and development-command
  sources override it with the RegionManager read-only observation;
- the observer requests the target observation immediately after the exact
  event inventory and verifies proposal generation, event-inventory tick,
  scheduler-instance identity, restoration count, snapshot ordinal,
  registration sequence, and target coordinate before serializing it;
- `packedRegionEventTargets` publishes aggregate available/unavailable, no-op,
  mutation-precondition, and refusal counts plus each target's exact-slot
  object counts, constructor/identity match counts, observation category,
  decision outcome, and reason;
- every record explicitly marks point-in-time-only, non-atomic-with-inventory,
  read-only lookup, no entity handle, no achieved-state claim, no commit token,
  no mutation, no executable restoration, no arrival gate, and no lifecycle
  authority; and
- additive schema-v41 requires the new nullable section and closes every target
  category/outcome pair. Schema-v40 remains immutable for prior captures.

Automated validation status:

- schema guards prove v40 lacks the new property, v41 requires it, every inert
  flag is constant, unavailable/empty/exact/transient/mismatch/ambiguity states
  retain closed count rules, and owner/entity payloads are absent;
- observer guards require immediate inventory-to-target correlation and the
  exact detached serializer while prohibiting entity handles, object mutation,
  callback execution, commit tokens, packets, and arrival operations;
- runtime wiring guards require both real source paths to invoke the same
  RegionManager capture, while the default-null method keeps non-runtime
  fixtures explicit;
- the complete layered-map suite passes 364 tests across 116 focused files;
  and
- the authoritative bundled-Ant server build compiles 776 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- the JSON is a point-in-time explanation of a read-only lookup. A satisfied
  mutation precondition is not proof that the slot remains unchanged and is not
  permission to replace or remove anything;
- observer correlation prevents mixing target evidence with a different
  detached inventory but does not make the two captures atomic with callback
  execution; and
- no entity or owner text is serialized or retained. No object/event/callback
  is mutated, invoked, cancelled, rescheduled, or replayed; no arrival is gated;
  and no reconstruction, preservation, registry, teardown, transaction,
  rollback, packet, or lifecycle authority is added.

Owner validation:

- the seven-record private magic-tree route validates against schema-v41 in
  exact `start`, source teleport, `target-a`, `target-b`, return teleport,
  `target-return`, `stop` order;
- at event-inventory tick 73761, `target-a` retains scheduler registration 3892
  with eight ticks remaining and reports one object at `(524,489)`, zero exact
  restoration-scenery matches, one exact authored-identity match,
  `EXACT_AUTHORED_TRANSIENT_PRESENT`, and
  `MUTATION_PRECONDITION_SATISFIED`;
- 29 ticks later, `target-b` reports zero restoration targets because the
  callback completed naturally between human-entered markers; the return
  teleport, `target-return`, and stop remain at zero; and
- future owner instructions must state tolerant real-time or tick windows.
  Human-entered commands are never assumed instantaneous; any gate requiring
  sub-second precision must use an automated private fixture instead.

Status: implemented and owner-validated. Executable restoration, arrival
gating, and all lifecycle authority remain absent.

### Slice 118: Dormant atomic revalidation contract

Objective: define the fail-closed boundary order that any future exact-slot
restoration mutation must satisfy, without acquiring a runtime lock, looking up
an event or target, authorizing mutation, or claiming atomicity.

Implemented:

- `GameTickEventRestorationAtomicRevalidationContract` evaluates only explicit
  detached declarations and the existing pure Slice 115 target decision;
- the contract first requires an outer event-execution boundary, refuses if a
  scheduler-store lock is carried inward, and requires scheduler registration
  validation before entering the Region object boundary;
- scheduler-instance identity, registration sequence, and reconstruction-
  proposal generation must match before the Region boundary is considered;
- the exact target must then be observed and classified again inside the Region
  object boundary. A stale external Slice 116/117 observation cannot satisfy
  this requirement;
- a target refusal remains a refusal with its original target reason retained;
  an idempotent no-op and a satisfied mutation precondition receive distinct
  contract outcomes; and
- even a satisfied contract explicitly provides no runtime revalidation, no
  atomicity claim, no entity handle, no mutation authorization or mutation, no
  executable restoration, no commit token, no arrival gate, and no lifecycle
  authority.

Automated validation status:

- an executable Java fixture covers the satisfied transient-spawn and
  already-restored no-op paths, every boundary/identity/generation fence,
  retained exact target refusal, invalid scalar declarations, and null inputs;
- source guards prohibit runtime model/network imports, synchronization,
  World/Region/GameObject/event handles, lookup, registration/removal, callback
  execution, packets, and mutation;
- ordering guards require event execution, absent scheduler-store lock,
  pre-Region registration validation, Region object boundary, and in-boundary
  target observation in that order;
- the complete layered-map suite passes 368 tests across 117 focused files;
  and
- the authoritative bundled-Ant server build compiles 777 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- boundary booleans are declarations consumed by an executable specification;
  this class does not prove that the caller actually holds a lock;
- a contract-satisfied mutation precondition is not a commit token and cannot
  be passed to `registerGameObject`, `unregisterGameObject`, or any callback;
- no scheduler/store lock may be acquired inside the Region object boundary;
  the outer event-execution boundary is the only declared bridge across the
  pre-Region scheduler check and inner target revalidation; and
- no live callback, Region, scheduler, observer, reconstruction path, arrival
  path, packet path, or teardown path consumes this contract.

Status: implemented and automated-validated. A runtime boundary implementation,
mutation, executable restoration, arrival gating, and all lifecycle authority
remain absent.

### Slice 119: Region-boundary target classification

Objective: prove that exact restoration-target comparison and classification
can run inside the real Region object monitor while returning only detached
evidence and granting no mutation or callback authority.

Implemented:

- `Region.captureRestorationTargetBoundarySnapshot` receives a detached
  constructor/provenance match requirement, enters `synchronized (objects)`,
  enumerates every relevant exact-slot object, performs constructor and authored
  identity comparisons, and derives the closed target state before release;
- `Thread.holdsLock(objects)` is captured at snapshot construction and a false
  value is rejected, making the internal boundary claim executable rather than
  a caller-supplied diagnostic boolean;
- the returned Region-local value contains only object/match counts, the closed
  state, and the boundary-held fact. It retains no GameObject, collection,
  Point, Region, monitor, or authored-identity handle;
- RegionManager constructs the detached match requirement from the existing
  event inventory, maps the in-boundary state to the existing Slice 115
  decision outside the object monitor, and retains prior scheduler/event
  correlation;
- available target records now retain whether classification occurred inside
  the object boundary, while the aggregate separately reports classified count
  and available-target classification completeness; and
- the observation remains point-in-time-only, non-atomic with the earlier event
  inventory and any later mutation, performs no runtime revalidation, and
  grants no achieved-state claim, commit token, executable restoration, arrival
  gate, or lifecycle authority.

Automated validation status:

- source-order guards require exact-slot enumeration, constructor comparison,
  authored-identity comparison, closed classification, detached snapshot
  construction, and `Thread.holdsLock(objects)` inside the synchronized block;
- detached-shape guards prohibit entity, collection, Point, RegionManager, and
  authored-identity handles from the returned boundary snapshot;
- RegionManager guards require the new inner-boundary path and prohibit
  scheduler/event locks, object registration/removal/replacement, callback
  execution, packets, and mutation;
- the complete layered-map suite passes 372 tests across 118 focused files;
  and
- the authoritative bundled-Ant server build compiles 777 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- the detached result is stale as soon as the object boundary is released. It
  cannot satisfy Slice 118's future in-boundary mutation requirement after
  being returned;
- the observer is not an event callback and holds no outer event-execution
  boundary, so it makes no atomic revalidation or mutation-readiness claim;
- no scheduler-store, event execution, or event timing lock is acquired from
  inside the Region object monitor; and
- no callback, observer, Region, scheduler, reconstruction, arrival, packet, or
  teardown path consumes the boundary result to alter scenery.

Status: implemented and automated-validated. Private diagnostic exposure of
the boundary fact, runtime mutation revalidation, executable restoration,
arrival gating, and all lifecycle authority remain absent.

### Slice 120: Private Region-boundary target diagnostics

Objective: expose Slice 119's detached object-boundary classification fact in
one additive private schema without converting it into runtime revalidation,
mutation readiness, or lifecycle authority.

Implemented:

- schema-v42 preserves the complete v41 record and adds aggregate
  `objectBoundaryClassifiedTargetCount`, available-target boundary-
  classification completeness, runtime-classification-performed, explicit
  non-atomic-with-mutation, and runtime-revalidation-performed fields;
- each target adds only `objectBoundaryHeldDuringClassification`; available
  Regions require true and unavailable Regions require false;
- the observer refuses a non-null target observation unless every available
  target carries the Region object-boundary fact, preserving exact inventory
  generation/tick/scheduler/ordinal/registration/coordinate correlation;
- zero available targets require zero boundary classifications and false
  runtime-classification-performed; one or more available targets require a
  positive classified count and true runtime-classification-performed; and
- schema-v41 remains immutable for the accepted Slice 117 capture.

Automated validation status:

- schema guards prove v41 lacks every new field, v42 requires the closed
  additions, and available/unavailable targets require the matching boundary
  boolean;
- every authority field remains constant false, including atomic-with-mutation,
  runtime revalidation, entity handle, achieved state, commit token, mutation,
  executable restoration, arrival gate, and lifecycle authority;
- observer guards require aggregate completeness before serialization and
  publish only detached counts/booleans plus the existing target categories;
- the complete layered-map suite passes 377 tests across 119 focused files;
  and
- the authoritative bundled-Ant server build compiles 777 core and 488 plugin
  sources and passes its build/classpath audit.

Safety boundary:

- `objectBoundaryHeldDuringClassification=true` describes where the earlier
  comparison ran; it does not state that the boundary remains held when JSON is
  serialized or read;
- the target observation remains non-atomic with the event inventory and
  non-atomic with any later mutation, and it cannot satisfy Slice 118 after the
  Region monitor has been released;
- no owner/entity text, monitor, event, callback, scheduler, Region, or object
  handle enters the schema; and
- no callback is invoked, cancelled, rescheduled, or replayed; no scenery is
  registered, removed, or replaced; and no arrival or lifecycle path consumes
  the diagnostic.

Status: implemented and automated-validated. Owner validation, runtime mutation
revalidation, executable restoration, arrival gating, and all lifecycle
authority remain absent.

### Slice 62: Authored reconstruction dependency diagnostics

Objective: expose Slice 61's bounded recipe/requirement projection through the
opt-in private observer so an owner can inspect real source selections without
granting the observation any loading or teardown role.

Implemented:

- additive `layered-map-parity-event-v20` records retain the complete v19
  event and add nullable `packedRegionAuthoredReconstruction` evidence;
- selected-source entries preserve the exact safety-source order and report
  final-live recipe counts, conservative reach counts, unique dependency
  counts, and per-source closure;
- the sorted requirement union reports selected-safety membership, authored
  recipe presence, owner-source count, and placement-reference count, allowing
  an open selection to explain exactly which packed sources are absent;
- whole-recipe totals distinguish global recipe size from the selected
  observation so a small source set cannot be mistaken for a complete world
  recipe; and
- both the Player session rebind path and the development-command start path
  obtain the completed reconstruction recipe from `WorldPopulator`, preventing
  duplicated command wiring from silently emitting a weaker projection.

Safety boundary:

- selected safety sources and unique dependency requirements each use an
  explicit refusal-based bound; overflow cannot be truncated into a false
  closed result;
- the JSON declares `identityMetadataOnly=true`, `entityRegistry=false`, and
  `lifecycleAuthority=false`, and the source produces primitive detached
  evidence only;
- schema-v20 is additive and the v11-v19 contracts remain immutable for prior
  captures; and
- open or closed dependency evidence changes no Region, tile, archive, entity,
  collision, event, player, persistence, or recovery state and carries no
  lifecycle authority.

Automated validation status:

- the authoritative bundled-Ant build compiles all 759 core and 488 plugin
  sources;
- the schema/source guard verifies the additive nullable contract, explicit
  inert flags, refusal budgets, serializer, and both runtime source paths; and
- the complete layered-map suite passes 142 tests across 61 focused files; and
- the owner-observed broad transition remained visually normal and emitted a
  schema-valid 36-source selection containing 5,590 final-live expectations:
  all 5,590 matched, with zero absences, duplicates, anomaly details, or
  dropped details;
- that selection reported 51 unique dependency sources, 35 selected and 16
  missing, so its open result is exact and explanatory rather than a silent
  boolean; and
- the attempted narrow runtime boundary `527 -> 528` was correctly a no-op for
  the 128-tile visibility window and emitted an empty closed observation. It is
  not evidence for a non-empty narrow selection; the compiled fixture supplies
  that closed-selection case. A later runtime test that specifically needs one
  exiting region column should use the actual `559 -> 560` threshold at this
  Y coordinate.

Status: implemented, automated-validated, and owner-validated. No lifecycle
authority is authorized.

### Slice 63: Fixed-point authored reconstruction cohort

Objective: determine how far an exact retirement-safety seed set expands when
every dependency coordinate with final-live authored content must contribute
its own recipe and dependencies, without treating empty neighboring coordinates
as reconstructable authored sources.

Implemented:

- one bounded immutable analysis preserves exact safety sources as round-zero
  seeds and recursively adds required sources with final-live authored content
  until no authored dependency remains outside the cohort;
- cohort order retains the exact safety-source order followed by deterministic
  expansion discovery. Every source records its seed/expanded role, expansion
  round, recipe presence, final-live placement/reach arithmetic, and direct
  cohort-versus-support requirement counts;
- the sorted requirement union records cohort membership, recipe-source
  presence, final-live authored-content presence, owner-source count, and
  placement-reference count;
- a dependency coordinate without final-live content is retained as an
  external support requirement rather than added as an empty authored cohort
  member. Such a coordinate may still matter for terrain, collision, roaming,
  or object footprint support; and
- cohort and requirement budgets are independent and refusal-based. Expansion
  never truncates into a false fixed point or self-contained result.

Safety boundary:

- the value retains detached recipe/dependency metadata only and has no entity,
  Region, tile, archive, event, registry, cache, callback, claim, permit, lease,
  transaction, commit, load, teardown, reconstruction, or rollback handle;
- `authoredClosureComplete` means only that every dependency with final-live
  authored content joined this analysis. `fullySelfContained` additionally
  means there are no support-only dependency coordinates, but neither result is
  permission to change lifecycle;
- external support is reported, not acquired, pinned, loaded, or validated;
  terrain replay, collision reconstruction, and event ownership remain absent;
  and
- this slice has no runtime consumer or diagnostic schema. Packed Region and
  entity lifecycle remain unchanged.

Automated validation status:

- a compiled fixture proves one seed recursively adding an authored neighbor,
  that neighbor exposing a support-only empty coordinate, exact shared-owner
  requirement arithmetic, and an independent anchor-only self-contained seed;
- the same fixture proves immutable result collections and refuses undersized
  cohort or requirement budgets; and
- source guards prove the analysis has no entity or RegionManager dependency
  and describes itself as detached evidence only;
- the complete layered-map suite passes 145 tests across 62 focused files; and
- the authoritative bundled-Ant build compiles 760 core and 488 plugin
  sources.

Status: implemented and automated-validated. No lifecycle authority is
authorized.

## Semantic Area Inventory: Pending Later Analysis

The completed planning document will include an underground-area inventory
with at least these fields:

| Field | Purpose |
| --- | --- |
| Area ID and name | Stable reference independent of coordinates |
| Current bounds | Exact terrain and placement extent |
| Layered sector claims | World-space, level, capacity, ownership, and adjacency tracking |
| Surface association | Geographic or narrative parent region |
| Area category | Shallow, deep, transit, quest, minigame, or exceptional |
| Entrances and exits | Directed edges, requirements, and recovery paths |
| Terrain source | Archive sectors and fingerprint |
| Placement sources | Base, feature, and MyWorld files |
| Script owners | Plugins, quests, commands, and coordinate checks |
| Persistence risks | Saved player, quest cache, minigame, or item state |
| Legacy-client status | Known compatibility requirements |
| Conversion confidence | Automatic inferences, warnings, and acknowledged findings |
| Growth reservation | Buffer and future expansion needs |
| World-space/template status | Global static area or future instance-template candidate |

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
4. Every transition works in each declared direction, and intentional one-way
   behavior remains one-way.
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
| 2026-07-18 | Treat long-distance and unconventional transitions as valid creator choices; classify and report them descriptively without replacing, discouraging, or judging their design. | Confirmed |
| 2026-07-18 | Make the layered architecture ready for future true instances through a separate world-space identity and template metadata, while keeping current converted content static in the global world space. | Confirmed |
| 2026-07-18 | Use sparse `48 x 48` sector storage keyed by world space and level, signed 32-bit logical X/Y, stable area IDs, exclusive base-terrain ownership, and explicit creator-controlled growth reservations. | Confirmed |
| 2026-07-18 | Use directed transitions and a layered recovery hierarchy: exact restore, migration redirect, valid instance, declared recovery anchor, last safe global anchor, then world spawn. | Confirmed |
| 2026-07-18 | Retain 48-tile terrain storage while adopting smaller incremental presentation chunks; implement streaming as the separately gated milestone immediately after coordinate/behavior parity. | Confirmed |
| 2026-07-18 | Validate through synthetic, copied vanilla, copied Spoiled Milk, incremental-streaming, alignment-workbench, disposable-export, alternate-adapter, and owner-acceptance gates with rollback at every stage. | Confirmed |
| 2026-07-18 | Approve and implement Slice 1 as a self-contained signed-coordinate laboratory plus deterministic, read-only `spoiled-milk-repository-v1` preflight; runtime and world conversion remain out of scope. | Implemented and validated |
| 2026-07-18 | Approve and implement Slice 2 as lossless, non-relocating normalization of recognized terrain, placement, and transition sources, with raw anomaly preservation and unresolved Java ownership. | Implemented and validated |
| 2026-07-18 | Approve and implement Slice 3 as a dormant server-owned layered-location contract and checked packed `Point` bridge, with exhaustive tool parity and no runtime consumer adoption. | Implemented and validated |
| 2026-07-18 | Continue with Slice 4 as a checked immutable layered projection from legacy `Area`, preserving packed storage and existing containment behavior while proving level/world-space isolation. | Implemented and validated |
| 2026-07-18 | Continue with Slice 5 as a logical `WorldRegionKey` projection while retaining packed region storage and recording the two legacy region objects that straddle level boundaries. | Implemented and validated |
| 2026-07-18 | Continue with Slice 6 as an object-specific directed transition projection, retaining the exact telepoint XML, packed lookup, command matching, and runtime teleport path. | Implemented and validated |
| 2026-07-18 | Continue with Slice 7 by separating logical signed map-sector identity from offset legacy terrain archive indices, retaining exact entry names and payload bytes. | Implemented and validated |
| 2026-07-18 | Continue with Slice 8 by projecting static object, item, and NPC placements into layered locations and correctly inclusive roaming bounds, retaining packed JSON and runtime construction. | Implemented and validated |
| 2026-07-18 | Continue with Slice 9 by classifying every unresolved Java coordinate owner into a stable lexical migration family while retaining all candidates and making false-positive signal shapes explicit. | Implemented and validated |
| 2026-07-18 | Continue with Slice 10 by inventorying exact content teleport, point, and area occurrence shapes with file/line/argument evidence while retaining declarations and expressions as unresolved lexical evidence. | Implemented and validated |
| 2026-07-18 | Continue with Slice 11 as a doubly opt-in, dev-only private runtime parity observer with stable JSONL movement/session capture while packed `Player` state remains authoritative. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 12 by maintaining a checked read-only layered mirror on Player initialization, movement, and session transitions while inherited packed Point remains the sole gameplay authority. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 13 by maintaining checked world-space/level-qualified Player region membership alongside the accepted location mirror while packed RegionManager storage remains authoritative. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 14 by projecting each loaded/saved legacy Player location through a checked immutable layered persistence snapshot while retaining the exact X/Y database contract. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 15 by defining a level-qualified logical visibility window and read-only RegionManager projection while retaining packed lookup, caches, and client behavior. | Implemented and validated |
| 2026-07-18 | Continue with Slice 16 by maintaining a checked Player visibility-window shadow and adding versioned private trace evidence while retaining packed lookup, caches, and client behavior. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 17 by defining a deterministic, allocation-budgeted logical interest delta while retaining packed lookup, caches, packets, and client behavior. | Implemented and validated |
| 2026-07-18 | Continue with Slice 18 by emitting bounded logical interest deltas only through versioned private diagnostics while retaining all current interest and residency authorities. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 19 by projecting every logical key overlapped by one legacy packed region cell while retaining packed storage, caches, and lookup. | Implemented and validated |
| 2026-07-18 | Continue with Slice 20 by comparing one current packed visibility candidate window with its signed logical window while retaining all runtime lookup and visibility authority. | Implemented and validated |
| 2026-07-18 | Continue with Slice 21 by emitting bounded packed/logical coverage evidence only through versioned private diagnostics while retaining all current interest and residency authorities. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 22 by partitioning one packed region cell into exact contiguous logical tile fragments while retaining packed Region and tile storage. | Implemented and validated |
| 2026-07-18 | Continue with Slice 23 by inverting packed fragments into complete, partial, or unsupported logical-region legacy assembly plans without copying tiles. | Implemented and validated |
| 2026-07-18 | Continue with Slice 24 by resolving logical region-local tiles to checked packed source cells and local indices without reading runtime tiles. | Implemented and validated |
| 2026-07-18 | Continue with Slice 25 by copying packed TileValues into detached logical-region snapshots while retaining packed collision and tile authority. | Implemented and validated |
| 2026-07-18 | Continue with Slice 26 by emitting bounded logical-region tile-snapshot metadata through versioned private diagnostics while retaining packed tile authority. | Implemented and owner-validated |
| 2026-07-18 | Continue with Slice 27 by replacing mutable logical snapshot internals with an immutable full-fidelity tile-state value and retaining a detached legacy-copy bridge. | Implemented and validated |
| 2026-07-18 | Continue with Slice 28 by comparing one direct packed immutable tile state with its assembled logical-snapshot state without adopting either for gameplay. | Implemented and validated |
| 2026-07-18 | Continue with Slice 29 by emitting bounded current-tile packed/logical parity metadata through additive private v6 diagnostics. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 30 by comparing a checked 3×3 logical tile neighborhood with its current direct packed sources without collision or pathing adoption. | Implemented and validated |
| 2026-07-19 | Continue with Slice 31 by emitting bounded 3×3 neighborhood counts through additive private v7 diagnostics without tile payloads or gameplay adoption. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 32 by comparing one adjacent logical and packed tile-mask decision without changing PathValidation or movement authority. | Implemented and validated |
| 2026-07-19 | Continue with Slice 33 by emitting all eight adjacent tile-mask comparisons through additive private v8 diagnostics without changing movement authority. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 34 by composing adjacent tile-mask comparisons across one explicit bounded route without selecting or executing a path. | Implemented and validated |
| 2026-07-19 | Continue with Slice 35 by emitting the latest bounded ordinary walking segment through additive private v9 diagnostics without changing path authority. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 36 by mirroring packed Region lifecycle as checked, versioned logical residency without caching tiles or changing lookup/path authority. | Implemented and validated |
| 2026-07-19 | Continue with Slice 37 by comparing bounded logical interest changes with versioned Region residency while treating load/release candidates as dormant evidence only. | Implemented and validated |
| 2026-07-19 | Continue with Slice 38 by emitting bounded interest/residency evidence through opt-in private v10 diagnostics without adopting Region loading or eviction. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 39 by defining process-local global interest ownership and shared-reference semantics without runtime adoption. | Implemented and validated |
| 2026-07-19 | Continue with Slice 40 by maintaining one checked opaque logical-interest owner per Player session without adopting loading, release, or eviction. | Implemented and validated |
| 2026-07-19 | Continue with Slice 41 by emitting bounded Player interest-owner and global/shared reference transitions through opt-in private v11 diagnostics without adopting loading, retention, release, or eviction. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 42 by projecting global interest releases through a conservative 16-tick retirement cooldown without adopting loading, retention, release, or eviction. | Implemented and validated |
| 2026-07-19 | Continue with Slice 43 by exposing bounded transition and recent-release cooldown evidence through opt-in private v12 diagnostics without adopting loading, retention, release, or eviction. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 44 as a two-real-client private-runtime gate proving shared acquisition, partial release, final global release, cooldown, expiry, and reacquisition before considering a retirement arbiter. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 45 by atomically rechecking bounded retirement candidates through a pure source-level decision arbiter that cannot alter packed Region lifecycle. | Implemented and validated |
| 2026-07-19 | Continue with Slice 46 by emitting bounded accepted/refused retirement-decision evidence through additive private v13 diagnostics without lifecycle authority. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 47 by aggregating same-snapshot logical retirement decisions into conservative packed-source readiness while blocking incomplete cross-level coverage and partial edge sources. | Implemented and validated |
| 2026-07-19 | Continue with Slice 48 by emitting bounded packed-source readiness from the existing atomic decision batch through additive private v14 diagnostics without lifecycle authority. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 49 by assessing packed-source contents and quiescence read-only while explicitly blocking lifecycle readiness until a per-Region reload path exists. | Implemented and validated |
| 2026-07-19 | Continue with Slice 50 by emitting bounded packed-source contents and blocker evidence through additive private v15 diagnostics without lifecycle authority. | Implemented and owner-validated |
| 2026-07-19 | Continue with Slice 51 by auditing construction/teardown owners and recording a bounded immutable count-only inventory of authored content actually constructed per packed Region. | Implemented and validated |
| 2026-07-19 | Continue with Slice 52 by correlating exact retirement-safety sources with immutable authored construction-origin counts through additive private v16 diagnostics. | Implemented and owner-validated |
| 2026-07-19 | Use Slice 52's active-versus-origin evidence to select a detached immutable authored-placement manifest as the next prerequisite, keeping spawn origin separate from active state. | Confirmed for Slice 53; lifecycle adoption remains gated |
| 2026-07-19 | Continue with Slice 53 by retaining exact detached construction inputs, duplicate-safe source ordinals, and harvesting conversion identity after successful population. | Implemented and runtime-validated; lifecycle adoption remains gated |
| 2026-07-19 | Continue with Slice 54 by aligning every authored placement with a detached conservative object, NPC-roaming, or anchor-only packed-source reach envelope. | Implemented and runtime-validated; lifecycle adoption remains gated |
| 2026-07-19 | Continue with Slice 55 by formalizing the manifest address as an immutable generation-fenced identity without attaching it to live definitions or entities. | Implemented and automated-validated; runtime attachment remains gated |
| 2026-07-19 | Continue with Slice 56 by attaching conflict-refusing authored identity metadata to accepted definitions/entities and preserving it through existing respawn and explicit replacement paths. | Implemented and runtime-validated; registry/lifecycle authority remains absent |
| 2026-07-19 | Continue with Slice 57 by comparing exact safety-source manifest identities with a bounded count-only private runtime census and additive v17 diagnostics. | Implemented and owner-validated; registry/lifecycle authority remains absent |
| 2026-07-19 | Continue with Slice 58 by adding bounded detached exact-identity details for every authored-provenance anomaly through additive v18 diagnostics. | Implemented and owner-validated; all four prior absences classified as deterministic population-time supersessions and registry/lifecycle authority remains absent |
| 2026-07-20 | Continue with Slice 59 by preserving complete manifest replay history while projecting deterministic population supersessions into a final-live expectation set and additive v19 diagnostics. | Implemented and owner-validated; the duplicated command trace source now forwards the completed outcome, all 5,797 final-live expectations match, and registry/lifecycle authority remains absent |
| 2026-07-20 | Continue with Slice 60 by deriving an inert per-source recipe from final-live authored identities and aligned dependency envelopes without a runtime consumer. | Implemented and private-runtime validated; all 33,515 final-live inputs retain aligned reach and lifecycle authority remains absent |
| 2026-07-20 | Continue with Slice 61 by projecting final-live recipe counts and unique dependency requirements onto an exact bounded safety-source set. | Implemented and automated-validated; closure remains evidence only and lifecycle authority remains absent |
| 2026-07-20 | Continue with Slice 62 by exposing bounded recipe and dependency-closure evidence through additive private schema-v20 diagnostics. | Implemented and owner-validated; all 5,590 selected final-live identities matched, the 16-source open dependency remainder was exact, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 63 by expanding safety seeds through recursively required final-live authored sources while retaining empty dependency coordinates as external support. | Implemented and automated-validated; lifecycle authority remains absent |
| 2026-07-20 | Continue with Slice 64 by exposing fixed-point cohort expansion and support-only requirements through additive private schema-v21 diagnostics. | Implemented and owner-validated; 6 narrow seeds reached 61 authored sources over 10 rounds, both accepted cohorts closed with 14 explicit support-only requirements, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 65 by attributing exact cohort dependency edges and cross-source placement bridges by construction and dependency kind. | Implemented and automated-validated; all arithmetic is exact and refusal-bounded, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 66 by exposing typed cohort attribution through additive private schema-v22 diagnostics. | Implemented and owner-validated; NPC roaming accounts for 333/334 narrow and 194/195 broad frontier references, every external-support reference, and 1,626/1,636 combined cross-source references; static scenery creates no frontier expansion, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 67 by comparing the completed forward cohort with whole-recipe incoming edges and strong/weak component topology. | Implemented and automated-validated; forward closure is proved distinct from incoming/weak closure, graph membership remains evidence only, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 68 by exposing bounded whole-recipe topology through additive private schema-v23 diagnostics. | Implemented and owner-validated; the narrow/broad forward cohorts gain the same 75 incoming-only sources through 10 NPC-roaming edges/38 references, the largest weak component contains 123 of 366 authored sources, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 69 by separating exact source-local replay, conservative outbound spatial support, and external incoming-owner reach. | Implemented and automated-validated; support content is never recursively imported, potential mobile reach is not active-instance evidence, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 70 by exposing the dependency-semantics split through additive private schema-v24 diagnostics. | Implemented and owner-validated; exact safety drives bounded replay/support/incoming evidence, all external relationships in both labeled selections are potential NPC roaming, active-instance evidence remains absent, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 71 by classifying detached point-in-time active NPC residency against exact safety sources and authored recipe identity. | Implemented and automated-validated; authored ownership remains independent from current residency, invalid identity stays unresolved, and no registry, arrival gate, or lifecycle authority exists |
| 2026-07-20 | Continue with Slice 72 by exposing active NPC owner/residency evidence through additive private schema-v25 diagnostics. | Implemented and owner-validated; 12 NPCs crossed their exact authored packed-source boundary but remained inside the broad selection, all identity and safety arithmetic reconciled, and no registry, arrival gate, or lifecycle authority exists |
| 2026-07-20 | Continue with Slice 73 by assessing whether one exact active-NPC observation is contained at its census tick while keeping preservation and lifecycle readiness separate. | Implemented and automated-validated; cross-source movement inside stays contained, six independent blockers are refusal-tested, and no registry, arrival gate, or lifecycle authority exists |
| 2026-07-20 | Continue with Slice 74 by exposing the active-NPC containment assessment through additive private schema-v26 evidence derived from the event's existing census. | Implemented and owner-validated; the broad selection began contained, then one Guard legitimately crossed from external owner source `(2,10)` into selected current source `(2,9)` and produced the sole blocker without registry, arrival-gate, or lifecycle authority |
| 2026-07-20 | Continue with Slice 75 by projecting exact missing sources for recognized active-NPC boundary crossings while preserving non-expandable hard blockers. | Implemented and automated-validated; requirements are deduplicated and refusal-bounded, every proposal requires fresh safety/census evidence, and no selection or lifecycle authority exists |
| 2026-07-20 | Continue with Slice 76 by exposing active-NPC boundary requirements through additive private schema-v27 evidence derived from the event's existing census. | Implemented and automated-validated; contained-path owner validation passes 33,385 checks with zero false requirements, and the executable observer fixture deterministically validates one exact non-empty external-owner requirement without a second census, selection mutation, registry, arrival gate, or lifecycle authority |
| 2026-07-20 | Continue with Slice 77 by combining exact safety seeds, authored-cohort expansion, active-NPC requirements, static support, and hard blockers into one inert refinement proposal. | Implemented and automated-validated; every added candidate retains provenance and explicitly requires fresh safety/census evidence, while support remains separate and no selection or lifecycle authority exists |
| 2026-07-20 | Continue with Slice 78 by exposing the retirement refinement proposal through additive private schema-v28 evidence derived from the same event parents. | Implemented and automated-validated; every candidate/support provenance and fresh-evidence requirement is serialized without a second snapshot, selection mutation, load request, or lifecycle authority |
| 2026-07-20 | Accept the Slice 78 narrow/broad owner route and measure progress by capability gates rather than raw slice count. | Owner-validated; 84 schema-v28 records pass 17,836 reconciliation checks, the candidate union grows from 61 to 120 without lost provenance, the current proof stream is mature, and authoritative runtime, Builder, migration, and export gates remain open |
| 2026-07-20 | Continue with Slice 79 by reassessing an exact proposed candidate set against strictly newer atomic evidence. | Implemented and automated-validated; stable, expanding, and non-expandable-blocked outcomes remain distinct, stale/incomplete inputs refuse, and candidate-set convergence grants no lifecycle authority |
| 2026-07-20 | Continue with Slice 80 by observing exact refinement candidates without manufacturing retirement eligibility or loading absent Regions. | Implemented and automated-validated; every diagnostic source is explicitly non-ready, absent sources remain absent, count evidence is detached, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 81 by composing one strictly newer, same-tick candidate observation and reassessment behind a dormant RegionManager seam. | Implemented and automated-validated; same-tick repeats defer before sampling, all evidence shares one observation lock/tick, the observer remains disconnected, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 82 by retaining and reassessing the latest immutable proposal through additive private schema-v29 diagnostics. | Implemented and automated-validated; deferral, stable, expanding, and hard-blocked states are explicit, diagnostic-only safety remains non-ready, and no lifecycle authority exists |
| 2026-07-20 | Accept the Slice 82 Lumbridge-to-Varrock refinement route. | Owner-validated; one fresh reassessment expanded the exact candidate set from 40 to 41 for active-NPC ownership at `(4,11)`, the next fresh reassessment stabilized at 41 and cleared the pending proposal, all five v29 records and round trips passed, and no lifecycle authority exists |
| 2026-07-20 | Continue with Slice 83 by defining a bounded five-family preservation/reload burden contract over exact safety sources. | Implemented and automated-validated; complete, partial, and unavailable evidence remain distinct, each family retains its own blocking or recovery policy, and the dormant value grants no preservation, reload, teardown, or lifecycle authority |
| 2026-07-21 | Continue with Slice 84 by capturing the current five-family burden from one non-creating Region snapshot per exact refinement candidate. | Implemented and automated-validated; exact local Player/dynamic-object counts remain distinct from partial ground-item/collision evidence and unavailable event ownership, the seam remains disconnected, and no lifecycle authority exists |
| 2026-07-21 | Continue with Slice 85 by exposing exact proposal-ordered preservation burdens through additive private schema-v30 diagnostics. | Implemented and automated-validated; proposal creation, deferral, and reassessment retain bounded five-family evidence, every mutating authority flag remains false, and private owner validation is pending |
| 2026-07-21 | Accept the Slice 85 Lumbridge-to-Varrock preservation-burden route. | Owner-validated; four v30 records pass 1,222 reconciliations, the exact assessment expands from 40 to 41 sources with `(4,11)`, one Player remains a hard block, incomplete ground-item/collision/event evidence blocks every source, and no lifecycle authority exists |
| 2026-07-21 | Continue with Slice 86 by recording detached dynamic-object constructor state for exact refinement candidates. | Implemented and automated-validated; deterministic bounded records retain recreation inputs and opaque-attribute counts, external event ownership keeps standalone restoration incomplete, and no object or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 87 by exposing privacy-safe dynamic-object preservation records through additive private schema-v31 diagnostics. | Implemented and automated-validated; exact proposal generation/order and constructor-state records are visible without owner text, while standalone restoration and all lifecycle authority remain false |
| 2026-07-21 | Accept the Slice 87 dynamic-object create-capture-remove route. | Owner-validated; ten v31 events pass 10,240 reconciliations, all three record-bearing proposals find the exact temporary stump in `(3,13)`, privacy and inert-authority boundaries hold, cleanup leaves no queued world edit, and the owner reports normal visuals, collision, and interaction |
| 2026-07-21 | Continue with Slice 88 by defining a dormant event-ownership inventory over exact refinement candidates. | Implemented and automated-validated; exact spatial affinity, owner-position hints, explicit non-spatial scope, and unknown callbacks remain distinct, multi-source effects are bounded, and no event or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 89 by detaching one bounded global scheduler snapshot into the event-ownership inventory. | Implemented and automated-validated; legacy Mob owners remain position hints, null-owned callbacks remain unattributed, exact/global scope requires an explicit declaration, and no scheduler or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 90 by declaring exact fixed-location affinity for delayed scenery spawn/remove callbacks only. | Implemented and automated-validated; both declarations use the action's captured coordinate, every other callback remains conservative, and no event or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 91 by exposing the complete event-affinity inventory through additive private schema-v32 diagnostics. | Implemented and automated-validated; exact events and unknown callbacks coexist in one bounded proposal-correlated record without descriptors, identities, scheduler mutation, or lifecycle authority |
| 2026-07-21 | Accept the Slice 91 scheduled-scenery owner route and add concise private command aliases. | Owner-validated; the pending magic-tree record contains the sole exact fixed effect at `(524,489)` in candidate source `(10,10)`, the post-respawn record contains none, legacy hint/unattributed callbacks remain blocking, visuals/collision/interaction remain normal, `::lp` aliases `::layerparity`, and the existing `::tp` alias is documented |
| 2026-07-21 | Continue with Slice 92 by defining dormant detached restoration state for the two known scenery callbacks. | Implemented and automated-validated; spawn constructor/provenance state is representable without a target, authored removal has binding evidence, identity-less removal remains live-reference-dependent, every other callback stays unavailable, and no scheduler or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 93 by copying explicit scenery restoration state into the bounded event inventory. | Implemented and automated-validated; state requires matching exact fixed affinity, source/event ordinals remain correlated, availability stays distinct from callback-payload and standalone-restoration completeness, raw owner text remains unpublished, and no event or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 94 by exposing the narrow scenery restoration state through additive private schema-v33 diagnostics. | Implemented and automated-validated; aggregate/source/event records retain exact correlation, owner presence replaces owner text, historical schema-v32 remains immutable, and every scheduler/recovery/lifecycle authority remains false |
| 2026-07-21 | Record the first Slice 94 private magic-tree route without overstating its evidence. | Inconclusive; the pending marker arrived 8.313 seconds after source release, before the 16-tick retirement grace, so its inventory is null. The post-respawn stop is coherent and empty of exact/restoration events, behavior looked normal, but paired owner validation remains pending |
| 2026-07-21 | Accept the timing-corrected Slice 94 magic-tree restoration-state route. | Owner-validated; pending contains exactly one complete detached authored spawn payload, the return teleport proves its natural completion, re-chopping the returned tree creates a new equivalent callback, privacy holds, and all scheduler/recovery/lifecycle authority remains false |
| 2026-07-21 | Continue with Slice 95 by assigning scheduler-local identity to accepted event registrations. | Implemented and automated-validated; repeated snapshots retain one process-local sequence, rejected duplicates consume none, removal/re-registration and replacement receive new identities, UUID/key/private state stays internal, and no scheduler-control or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 96 by copying accepted registration identity into the bounded event inventory. | Implemented and automated-validated; one atomic store snapshot preserves ordinal/identity distinction, invalid identity refuses the whole capture, scheduler-instance identity and schema-v33 remain absent, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 97 by exposing process-local registration identity through additive private schema-v34. | Implemented and automated-validated; every bounded event publishes one positive sequence and aggregate completeness, historical v33 and existing UUID/key/private state remain untouched, scheduler-instance identity stays false, and no event or lifecycle authority is created |
| 2026-07-21 | Accept the Slice 97 repeated-callback identity route. | Owner-validated; `same-a` and `same-b` retain registration 3929 while countdown falls 28 to 12 ticks, natural completion removes it, re-chopping creates greater registration 3968, all eight v34 records validate, and no scheduler or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 98 by defining an opaque scheduler-instance scope for registration identity. | Implemented and automated-validated; one immutable atomic snapshot binds a stable same-store identity to accepted registration order, different stores differ, no token leaves the scheduler yet, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 99 by detaching scheduler-instance scope into the bounded event inventory. | Implemented and automated-validated; one atomic snapshot supplies token and registrations, canonical scope is required even when empty, schema-v34 remains unchanged and private, full scheduler identity stays false, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 100 by exposing scheduler-instance scope through additive private schema-v35. | Implemented and automated-validated; one canonical detached token scopes registration comparison, historical v34 remains closed and false, full scheduler identity stays absent, and no event or lifecycle authority is created |
| 2026-07-21 | Accept the Slice 100 cross-restart scheduler-scope route. | Owner-validated; Phase A and clean Phase B each retain one stable token/registration with falling countdowns, their tokens differ across the private restart, the mistaken intermediate session is closed and inventory-free, all 15 v35 records validate, and no scheduler or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 101 by declaring dormant execution semantics for known scenery callbacks. | Implemented and automated-validated; spawn/removal explicitly declare one-shot execution and continuing server-tick progression, unavailable callbacks remain unclassified, no live timing is promoted, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 102 by detaching known execution semantics into the bounded event inventory. | Implemented and automated-validated; explicit enum mapping preserves one-shot/continuing-tick policy, aggregate semantic completeness stays distinct from payload/restoration completeness, atomic timing remains false, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 103 by exposing detached execution semantics through additive private diagnostics. | Implemented and automated-validated; schema-v36 publishes closed one-shot/continuing-tick values and reconciled counts, historical v35 remains immutable, atomic timing stays explicitly false and zero, and no event or lifecycle authority is created |
| 2026-07-21 | Accept the Slice 103 scenery-event execution-semantics route. | Owner-validated; both pending records retain token `ae1af8a0-a355-4aeb-93dc-23d4de947e4e` and registration 3900 while ticks fall 24 to 10, one-shot/continuing-tick semantics and counts reconcile, natural completion empties the exact callback, and atomic timing plus every authority remain false |
| 2026-07-21 | Continue with Slice 104 by defining an atomic scheduler event-timing foundation. | Implemented and automated-validated; one event-local lock captures the timing tuple, one version-fenced store snapshot binds it to tick/scope/registration, mixed-registration capture refuses, diagnostics remain non-atomic and unchanged, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 105 by detaching atomic timing for known scenery callbacks. | Implemented and automated-validated; the handler consumes one version-fenced snapshot, known timing/counts reconcile, unavailable callbacks remain non-atomic, schema-v36 stays pinned false/zero, and no event or lifecycle authority is created |
| 2026-07-21 | Continue with Slice 106 by exposing detached atomic timing through an additive private contract. | Implemented and automated-validated; schema-v37 publishes reconciled aggregate/per-event timing provenance, known restorations are atomic, unknown callbacks remain non-atomic, schema-v36 stays immutable, and no event or lifecycle authority is created |
| 2026-07-21 | Correct the private marker deadlock exposed by the first Slice 106 owner route. | Slice 107 implemented and automated-validated; an exact thread dump proves the callback/timing lock cycle, `doRun` remains serialized without holding the timing monitor across callback code, the executable inversion fixture completes, and no event or lifecycle authority is created |
| 2026-07-21 | Accept the corrected Slice 106/107 atomic-timing route. | Owner-validated; both markers complete without disconnect, retain one token and registration 3945, ticks 323→334 reconcile exactly with remaining delay 39→28, aggregate/event/restoration atomic claims agree, natural completion removes the callback, all six records validate against v37, and every authority flag remains false |
| 2026-07-21 | Continue with Slice 108 by defining fail-closed scenery target-binding and pre-visibility arrival requirements. | Implemented and automated-validated; authored spawn binds a destination slot, authored removal binds an existing entity, missing identity or mismatch refuses, reconciliation must precede first visibility, and no lookup, arrival gate, executable restoration, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 109 by detaching scenery target and arrival requirements into the bounded event inventory. | Implemented and automated-validated; target-rule capture, satisfied authored binding, and arrival ordering reconcile independently, identity-less callbacks remain explicitly incomplete, schema-v37 is unchanged, and no lookup, arrival gate, executable restoration, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 110 by exposing detached scenery target and arrival requirements through additive private diagnostics. | Implemented and automated-validated; schema-v38 publishes reconciled aggregate/per-restoration requirements, authored and missing binding remain distinct, schema-v37 is immutable, and no lookup, arrival gate, executable restoration, or lifecycle authority exists |
| 2026-07-21 | Record the first Slice 110 private magic-tree route without overstating its evidence. | Inconclusive; two complete sequences produce 11 schema-valid v38 records, but remaining inside source region `(10,10)` yields zero retirement candidates and null event inventories. The corrected route must teleport away and pass the 16-tick grace before its pending markers |
| 2026-07-21 | Accept the corrected Slice 110 target/arrival route. | Owner-validated; seven v38 records retain one authored spawn registration while its remaining delay falls exactly with the observation-tick delta, both pending markers reconcile authored destination binding and pre-visibility ordering, natural completion removes the restoration record, and every authority flag remains false |
| 2026-07-21 | Continue with Slice 111 by defining generation matching and idempotent desired-state prerequisites. | Implemented and automated-validated; stale authored generations must refuse, already-satisfied desired state is a no-op success, spawn/remove retain distinct mutation preconditions, and no comparison, inspection, mutation, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 112 by detaching generation and idempotency prerequisites into the bounded event inventory. | Implemented and automated-validated; captured rules, satisfied proposal-generation matches, and idempotency completeness reconcile separately, identity-less/stale callbacks remain incomplete, schema-v38 is unchanged, and no target inspection, mutation, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 113 by exposing detached generation and idempotency prerequisites through additive private diagnostics. | Implemented and automated-validated; schema-v39 publishes reconciled aggregate/per-restoration rules, schema-v38 remains immutable, and no target-state inspection, achieved-state claim, mutation, or lifecycle authority exists |
| 2026-07-21 | Accept the Slice 113 private generation/idempotency route. | Owner-validated; seven schema-v39 records retain registration 3892 while ticks 1419->1435 and remaining delay 33->17 reconcile exactly, proposal/authored generation 1 and all declared rules agree at both pending markers, natural completion removes the record, and every authority flag remains false |
| 2026-07-21 | Correct the authored spawn mutation prerequisite before target inspection. | Slice 114 implemented and automated-validated; the audited harvest path transfers authored identity to stump/depleted replacements, spawn now permits empty or one exact-identity authored transient, schema-v39 remains immutable, schema-v40 publishes the correction, and no target inspection or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 115 by defining a pure detached target-state decision table. | Implemented and automated-validated; binding/generation failures precede occupancy, spawn accepts empty or exact authored transient and no-ops on exact restored scenery, removal protects changed authored successors, every conflict is typed, and no runtime lookup, mutation, arrival gate, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 116 by capturing bounded read-only restoration target evidence. | Implemented and automated-validated; every exact collision-slot occupant is copied under the Region object monitor, missing/empty/exact/transient/mismatch/ambiguity remain distinct, scheduler/event correlation is retained, the capture is explicitly non-atomic with callback execution, and no entity handle, mutation, or lifecycle authority exists |
| 2026-07-21 | Continue with Slice 117 by exposing detached restoration-target evidence through private diagnostics. | Implemented and automated-validated; additive schema-v41 correlates the point-in-time target snapshot with the exact event inventory, publishes only bounded counts/categories/outcomes, preserves schema-v40, and explicitly grants no achieved-state claim, commit token, mutation, arrival gate, or lifecycle authority |
| 2026-07-22 | Accept the Slice 117 private restoration-target route and formalize human timing tolerance. | Owner-validated; `target-a` captures registration 3892 with eight ticks remaining and one exact authored stump classified as a satisfied spawn mutation precondition, 29 ticks later natural completion clears target evidence, all seven v41 records validate, and future sub-second gates require automation rather than instantaneous owner input |
| 2026-07-22 | Continue with Slice 118 by specifying fail-closed atomic target revalidation before any mutation seam. | Implemented and automated-validated; the dormant contract requires an outer event-execution boundary, forbids carrying the scheduler-store lock into the inner Region object boundary, matches scheduler scope/registration/generation, requires fresh in-boundary target classification, preserves exact refusal/no-op/precondition outcomes, and grants no atomicity claim, mutation authorization, commit token, or lifecycle authority |
| 2026-07-22 | Continue with Slice 119 by proving exact target classification inside the real Region object boundary. | Implemented and automated-validated; every relevant exact-slot object is compared and classified while `objects` is held, the boundary fact is checked with `Thread.holdsLock`, only detached counts/state return, scheduler/event locks and all mutations remain absent, and the result is explicitly stale after release |
| 2026-07-22 | Continue with Slice 120 by exposing only the detached Region-boundary target facts through private diagnostics. | Implemented and automated-validated; additive schema-v42 reports classified count/completeness and the per-target boundary boolean, preserves schema-v41, requires available targets to carry the fact, and explicitly remains non-atomic with mutation with no runtime revalidation, achieved-state claim, commit token, executable restoration, arrival gate, or lifecycle authority |

## Next Discussion

The accepted schema-v22 routes establish that conservative NPC roaming
envelopes, not scenery, create the long authored-cohort bridges. Preserve those
envelopes and all 14 support-only coordinates: their size is not evidence that
they are accidental, and an authored-closed forward cohort is still not proof
of a reconstructable or safely retireable unit.

The accepted schema-v23 evidence shows that a single recursive dependency rule
conflates three different concerns: which authored source owns replay, which
coordinates a placement may spatially affect, and which Regions an active
mobile entity currently needs. Preserve every conservative reach envelope, but
do not recursively import a target coordinate's unrelated authored recipe just
because the coordinate contains content.

The accepted schema-v25 route proves that a source selection can contain real
cross-source NPC movement without recursively importing the entire potential
roaming envelope. In the broad selection, 12 active NPCs crossed their exact
authored source boundary, yet all owners and current sources remained inside;
the conservative envelope still reports 164 external references. Potential
reach and active containment are therefore distinct evidence.

The Slice 73 assessment now makes that distinction mechanically. A selection
may be `contained now` when all selected-owned NPCs remain inside, including
ordinary cross-source movement within the selection. Selected-owned NPCs
outside, external-owned or unresolved NPCs inside, unresolved claimed owners
outside, relevant inactive instances, and duplicate relevant identities are
independent blockers. A contained result still retains the exact active-entity
preservation burden and has No lifecycle authority.

The accepted schema-v26 route proves both sides of the assessment. Its broad
selection begins contained while 18 NPCs move between selected sources, then
opens when one Guard authored in external source `(2,10)` enters selected
source `(2,9)`. All 25,087 reconciliation checks pass. A static selected-source
boundary therefore cannot assume that an initially contained mobile census
will remain contained.

Slice 75 now projects exact missing sources for recognized crossings. A
selected-owned NPC outside requests its current source; an external-owned NPC
inside requests its authored source. Repeated instances deduplicate without
losing reason counts, while unresolved, relevant inactive, and duplicate
identity evidence remains non-expandable. Every result requires fresh safety
and census evidence and can neither mutate the selection nor prove closure.

Slice 76 now exposes that projection through additive private schema-v27
diagnostics derived from the same event census. Its owner route validates 112
events and 16 contained triplets without one false requirement; the shorter
fresh-server run does not reproduce Slice 74's nondeterministic Guard crossing.
The executable observer fixture closes the serialization gap deterministically
by emitting one external-owner-inside instance and exactly one
external-owner-authored requirement for its unselected source. Another random
owner rerun is not required.

Slice 77 now supplies that inert refinement proposal. It keeps original safety
seeds, authored expansion, active-NPC reasons, support-only coordinates, and
hard blockers distinct while producing one deduplicated candidate union. Every
added candidate explicitly requires fresh safety and census evidence, and the
result neither mutates a selection nor claims fixed-point closure.

Slice 78 now exposes the proposal through additive private schema-v28
diagnostics derived from the same event's safety, cohort, and active-NPC
values. The accepted 84-record owner route passes 17,836 reconciliation checks:
every seed, added source, support coordinate, reason count, hard blocker, and
authority flag agrees with its three parents. The narrow selection expands 6
originals to 61 candidates, the broad selection expands 42 to 65, and walking
ultimately expands 55 originals to 120 candidates. All observed active-NPC
boundaries remain contained; the executable observer fixture covers the
non-empty active-reason path deterministically, so another random route is not
required.

Slice 79 closes that detached fresh-evidence loop. The prior ordered candidates
must become the exact strictly newer safety selection, after which one fresh
cohort and active census either stabilize the set, add precisely explained
candidates, or preserve a non-expandable blocker. Candidate-set convergence is
point-in-time evidence, not a commit token or lifecycle decision.

Slice 80 provides that bounded runtime seam. It observes the exact proposal
order through Region peeks, represents missing sources honestly, and marks the
whole selection as diagnostic-only rather than inventing retirement readiness.
Even an empty, resident source remains lifecycle-blocked without the original
logical decision evidence.

Slice 81 now composes candidate observation, authored cohort analysis, one
same-tick NPC census, and Slice 79 reassessment behind a single dormant
RegionManager call. It defers before sampling when the tick has not advanced,
and it retains no runtime state or authority.

Slice 82 supplies that private exposure. Observer state retains only the latest
immutable proposal, invokes Slice 81 on later events, preserves it across an
explicit same-tick deferral, replaces it only with an immutable expanding or
hard-blocked next proposal, and clears it only after a stable unblocked
point-in-time result. Schema-v29 exposes full diagnostic safety and visibly
states that it is not retirement-readiness evidence.

The real proposal-chain gate is now owner-validated. Its first fresh
observation legitimately expands the exact set from 40 to 41 for active-NPC
ownership at `(4,11)`; its next fresh observation retains all 41 sources,
requires no further active-NPC expansion, and clears the stable proposal.

Slice 83 now provides the required preservation/reload vocabulary. It refuses
to conflate a Player hard block, stateful entity restoration, derived collision
rebuild, or incomplete event ownership, and it never interprets unknown
evidence as zero. The safest next gate is a bounded, read-only runtime capture
for what can be observed honestly: exact Players, authored-versus-dynamic
active objects, active ground items, and collision-product counts, while event
ownership and absent authored-item respawn state remain explicitly partial or
unavailable. Only after that capture is deterministic should an additive
private schema expose it for owner validation.

Slice 84 now supplies that bounded runtime capture. It uses the exact proposal
order and one Region-local snapshot per source, but keeps active ground items
partial because absent authored respawns are external, keeps collision partial
because product ownership/rebuild is external, and keeps events unavailable
because the event store has no packed-source index. The next focused gate is
additive private diagnostic exposure of this exact assessment so a real route
can measure how those burdens vary across candidate sets without granting the
observer any runtime authority.

Slice 85 now supplies that private exposure and its owner gate is accepted. The event samples the proposal it
actually creates, retains, or freshly reassesses, checks the assessment against
the same exact proposal order, and emits all five burden families through
schema-v30. The route proves the family split is materially necessary: one
candidate really contains the Player, dynamic-object counts are exact and
empty, active ground items are widespread but incomplete, collision products
exist in every candidate, and event ownership cannot yet be observed. The next
focused gate should choose one missing family capability and define its dormant
preservation or rebuild contract before any arrival-gate, teardown, or recovery
implementation.

Slice 86 chooses active dynamic scenery as that first family capability. The
record now detaches complete constructor state and makes hidden runtime-
attribute burden measurable without copying arbitrary values. Because event
ownership remains unavailable, this is not yet a restorable object snapshot.
The next focused gate is privacy-safe additive diagnostics that correlate this
record with the existing dynamic-object burden count, followed by a deliberate
private object-creation/removal route; teardown and reload must remain absent.

Slice 87 supplies that private exposure and its owner route is accepted. The
temporary stump appears exactly once in the expected source across creation,
stable reassessment, and a fresh proposal; its record agrees with the dynamic-
object burden, privacy boundaries hold, and removal plus queued-edit cleanup
leave normal scenery interaction and movement. Dynamic-object restoration is
still honestly blocked by external event ownership, so the next focused gate
should inventory and classify event ownership without cancelling, rescheduling,
or otherwise adopting authority over any event.

Slice 88 supplies that classification contract. A Player or NPC owner now has
an explicitly weaker meaning than an event-declared spatial effect, and a null
owner means nothing without an explicit scope declaration. The next focused
gate should detach one bounded global scheduler snapshot into this vocabulary:
legacy events must default to an owner-position hint or unattributed callback,
while exact spatial and non-spatial classifications require a narrow explicit
contract on the event itself. Capture must occur without cancellation,
rescheduling, callback reflection, or scheduler-key mutation.

Slice 89 supplies that runtime seam and keeps it disconnected from diagnostics.
The next focused gate should explicitly annotate only the known fixed-location
scenery spawn/remove callbacks, then prove those exact events coexist with the
remaining unattributed scheduler population. Ground-item and NPC respawns,
projectiles, combat, plugins, and global maintenance events must stay unchanged
until each receives its own semantics; an event-location annotation still does
not capture or authorize restoration.

Slice 90 supplies only that narrow scenery annotation. The next focused gate
should expose Slice 89's complete bounded inventory through additive private
diagnostics, not merely the exact subset. A deliberate object-replacement route
should then prove one scheduled scenery respawn is exact while the real legacy
hint/unattributed counts stay visible and blocking. The route must let the
callback complete naturally and must not cancel, reschedule, restore, or retire
anything.

Slice 91 now supplies and owner-validates that complete private exposure. The
pending magic-tree record shows exactly one fixed effect at `(524,489)` in
candidate source `(10,10)`; after natural respawn the exact event is absent.
All 3,783 owner-position hints and 98 unattributed callbacks remain visible and
blocking, every mutating authority flag stays false, and the owner reports
normal replacement, collision, interaction, and natural respawn.

Slice 92 now defines the narrow dormant restoration-state contract for those
two callbacks. Spawn has complete detached constructor/provenance inputs and no
target requirement; removal distinguishes authored identity evidence from an
identity-less live-reference dependency. Neither result includes scheduler
identity/countdown or performs a target lookup, and both remain explicitly
non-restorable. The next focused gate should add this state to the bounded
event snapshot, correlate only exact scenery events, and keep owner text
private. It must not expose a replay command or implement cancellation,
reschedule, teardown, reload, or restoration.

Slice 93 now performs that bounded runtime copy. It rejects state on a hint,
unknown/global callback, or mismatched fixed-effect coordinate; retains the
same scheduler and candidate ordinals; and distinguishes state availability,
detached callback-payload completeness, and standalone restoration. Owner text
remains internal and schema-v32 is unchanged. The next focused gate should
publish only owner presence and the minimum constructor/provenance/binding
facts needed to validate the magic-tree spawn, using additive schema-v33. It
must preserve the complete hint/unattributed population and every false
scheduler/recovery/lifecycle flag.

Slice 94 now supplies and owner-validates that privacy-safe schema-v33
exposure. The timing-corrected route finds exactly one complete detached
authored spawn payload while pending; the return teleport proves that instance
completed naturally, and using the returned tree creates a new equivalent
callback. What remains missing is a safe way to prove across repeated snapshots
that the pending instance stayed the same and the re-chop instance is new. The
next focused gate should define an opaque, process-local, monotonically assigned
scheduler-registration identity. It must not expose existing UUIDs, event keys,
descriptors, classes, owner identities, or callback data; must distinguish an
accepted add, a rejected duplicate, removal, and `addOrUpdate` replacement; and
must initially remain disconnected from the layered inventory and all event
mutation consumers.

Slice 95 now supplies that scheduler-internal identity and distinguishes every
accepted registration lifetime without changing event execution. The next
focused gate should make `GameEventHandler` consume the store's single atomic
order/identity snapshot and copy only the positive registration sequence into
the bounded event inventory. Snapshot ordinals must remain contiguous and
separate from identity; rejected or missing identity must refuse the entire
capture; process/store-instance identity must remain explicitly uncaptured;
schema-v33 must remain unchanged; and no UUID, key, descriptor, class, owner
identity, event handle, or scheduler mutation may cross the inventory boundary.

Slice 96 now supplies that detached identity capture and refuses incomplete or
ambiguous registration order. The next focused gate should expose the positive
registration sequence and aggregate completeness through additive private
schema-v34. It must also publish `schedulerInstanceIdentityCaptured=false`,
retain `schedulerIdentityCaptured=false`, leave UUID/key/descriptor/class/owner
identity absent, and keep historical schema-v33 immutable. A private route can
then mark the same pending tree callback twice before respawn, prove the
registration sequence is stable while countdown decreases, prove it disappears
after natural completion, and prove re-chopping creates a greater new sequence.

Slice 97 now supplies that additive private exposure. Its owner route should
avoid the previous grace-period ambiguity: after chopping and leaving the
tree, wait 12 seconds, mark `same-a`, wait 5 seconds, and mark `same-b`. Both
records should identify the same exact spawn registration while its countdown
decreases. After natural respawn, the return teleport should contain no exact
spawn; re-chop the visibly returned tree, leave again, wait 12 seconds, and
mark `new`. That record should carry a greater registration sequence for the
same authored placement. Finish with `::lp stop`; no restart may occur during
the route because scheduler-instance identity is deliberately uncaptured.

The accepted Slice 97 route proves the registration identity behaves as
designed within one live scheduler instance: registration 3929 remains stable
across repeated observations, disappears naturally, and the next accepted
registration is 3968. It also demonstrates the remaining scope hazard: the
numeric sequence alone cannot safely distinguish a later capture produced by
a different server process. The next focused gate should define a detached,
opaque scheduler-instance identity with explicit process-lifetime semantics,
carry it through the event inventory without exposing a scheduler handle, and
keep it private and observational. It must make cross-instance comparison
detectably invalid, leave historical schema-v34 immutable, and grant no event
mutation, loading, retirement, restoration, or lifecycle authority.

Slice 98 now defines that scope at the scheduler boundary. The next focused
gate should make `GameEventHandler` consume the single atomic instance/order/
registration snapshot and copy only its opaque identity into the detached
inventory. The inventory must require a non-empty canonical identity even when
the event list is empty, retain existing registration-order validation, expose
no store/event reference, and change `schedulerInstanceIdentityCaptured` to
true while full `schedulerIdentityCaptured` remains false. Schema-v34 and the
observer must remain unchanged until a later additive private exposure.

Slice 99 now supplies that detached scope and keeps it unreachable from the
v34 observer. The next focused gate should add private schema-v35 with one
required canonical `schedulerInstanceIdentity`, change its capture flag to
true, and retain full `schedulerIdentityCaptured=false`. Historical schema-v34
must remain byte-for-byte closed and false. The observer may serialize only
the detached token—never the store, existing event UUIDs/keys, callbacks,
classes, owners, or mutation operations. An owner route can then capture one
pending callback, restart the private server, capture a new callback, and
prove registration sequences are comparable only when their instance identity
matches.

The accepted Slice 100 route proves the intended scope rule. Phase A keeps one
token and registration while its countdown falls; the clean post-restart Phase
B keeps a different token and registration while its countdown falls. The
mistaken intermediate attempt is a separate closed inventory-free session and
cannot contaminate either accepted capture. Numeric registration values are
therefore comparable only inside matching scheduler-instance identity.

Inspection of the live scheduler after acceptance shows the next restoration
gap is execution semantics, not more identity. Delayed scenery spawn/removal
callbacks are `SingleEvent` one-shots; `GameTickEvent.doRun()` decrements the
countdown, invokes a due callback, increments `timesRan`, and resets the
configured delay even though the one-shot stops itself. The inventory currently
records running/countdown/execution counters but does not explicitly declare
one-shot semantics or whether time continues while a source is absent, and
those fields are not captured under one event-lifecycle lock. The next focused
gate should first define a dormant explicit execution-semantics contract for
the two known scenery callbacks. For MMORPG resource behavior, the recommended
policy is that server ticks continue while a source is absent so an overdue
respawn is reconstructed before player arrival, rather than freezing depleted
resources off-screen. This slice must remain descriptive only; atomic timing
capture, arrival ordering, replay, cancellation, and rescheduling stay later
gates.

Slice 101 now supplies that dormant callback declaration. The next focused
gate should detach its execution semantics and time-progression policy into the
bounded event inventory only when restoration state is available. Unavailable
events must retain explicit unavailable semantics; aggregate counts must
distinguish semantic availability from callback-payload and standalone-
restoration completeness; and mismatched combinations must refuse. Existing
running/countdown/times-ran values must remain observational and explicitly
non-atomic. Schema-v35 and the observer should remain unchanged until a later
additive private contract.

Slice 102 now supplies that detached semantic inventory while keeping timing
non-atomic and private. The next focused gate should expose only these closed
semantic values and aggregate counts through additive private schema-v36.
Historical schema-v35 must remain immutable. The new contract must retain
`atomicTimingCaptured=false`, zero atomic-timing events, false standalone
restoration, and every event/lifecycle authority flag. A private owner route can
then verify a pending magic-tree spawn reports one-shot continuing-tick
semantics while its observed countdown falls, without claiming the countdown
is safe for replay.

The accepted Slice 103 route proves the descriptive contract matches a real
pending callback: one token and registration remain stable while the countdown
falls by the exact server-tick delta, one-shot/continuing-tick semantics remain
complete, and natural completion removes the event. It also confirms the
remaining gap rather than closing it: those timing values are still sampled
without one event-lifecycle lock and cannot support replay. The next focused
gate should define the smallest atomic timing snapshot at the scheduler
boundary—one observation tick bound to running state, remaining delay, and
execution count for the same accepted registration—then detach it without
adding cancellation, reschedule, callback invocation, due-event execution,
arrival ordering, or lifecycle authority. Historical schema-v36 and the
observer should remain unchanged until the atomic contract is executable and
refuses mixed-time or mixed-registration construction.

Slice 104 now supplies that executable scheduler boundary while leaving it
unconsumed. The next focused gate should make the handler request exactly one
atomic timing snapshot for a refinement observation and detach its observation
tick plus each event's running/remaining/execution tuple only when the same
registration carries an available one-shot/continuing-tick restoration state.
The detached inventory must require timing identity/count/order to reconcile,
refuse mismatched observation ticks or registrations, keep unavailable events
explicitly non-atomic, and distinguish atomic timing from standalone
restoration. Schema-v36 and the observer should remain unchanged until that
detached contract is executable and bounded.

Slice 106 supplies that additive private contract while keeping v36 immutable
and explicitly non-atomic. Its first owner marker exposed the Slice 104 timing-
monitor scope defect rather than producing valid evidence; Slice 107 corrects
that exact deadlock with an executable lock-inversion regression, and the clean
repeat now validates both slices through natural callback completion. Slice 108
adds the dormant, fail-closed target and arrival requirement: spawn and removal
have distinct authored subjects, exact coordinates cannot substitute for
authored identity, conflicts refuse, and restoration state must be reconciled
before first visibility. Slice 109 now detaches that requirement into the
bounded inventory, reconciles requirement/satisfied-binding/arrival counts, and
retains identity-less states as explicit incomplete evidence. Slice 110 exposes
only those detached values through additive private schema-v38 while preserving
v37 unchanged. The corrected owner route now proves a real pending authored
spawn satisfies both requirements and disappears after natural completion.
Slice 111 defines the next dormant generation/idempotency rule: a stale
authored generation must refuse, an already-satisfied exact desired state is a
no-op success, and spawn/removal have distinct mutation preconditions. Slice
112 detaches those rules, compares authored generation only to the inventory's
exact reconstruction-proposal generation, and keeps captured rules separate
from satisfied generation binding. Slice 113 exposes only those detached values
through additive schema-v39 while preserving v38 unchanged. The accepted
private authored magic-tree route shows a matched proposal/authored generation,
the declared spawn desired state and mutation precondition at both pending
markers, exact timer progression on one scheduler registration, and zero
restoration records after natural completion. Slice 114 audits the existing
resource-replacement lifecycle and corrects the spawn prerequisite: a stump or
depleted resource inherits the exact authored identity, so a safe future spawn
must accept an empty destination or one exact-identity authored transient while
still refusing unrelated, identity-less, or ambiguous occupancy. Corrective
schema-v40 publishes that rule and preserves v39 as historical evidence. The
Slice 115 classifier now distinguishes unavailable observation, empty
destination, exact restoration scenery, exact authored transient,
mismatched/identity-less occupancy, and ambiguity. Binding and generation
failures take precedence; spawn and removal retain distinct no-op, mutation-
precondition, and refusal outcomes. Slice 116 supplies the bounded read-only
runtime seam: it snapshots every relevant object in the exact collision slot,
retains scheduler/event correlation, classifies missing, empty, exact,
transient, mismatched, and ambiguous states, and explicitly remains non-atomic
with callback execution. Slice 117 now exposes that detached evidence through
additive private schema-v41: capture immediately follows the exact event
inventory, correlation covers proposal generation, inventory observation tick,
scheduler-instance scope, restoration count, snapshot ordinal, registration
sequence, and target coordinate, and only detached counts/categories/outcomes
are published. The owner route now proves one pending magic-tree target is an
exact authored transient with eight ticks remaining; natural completion clears
both event and target evidence before the second human-entered marker. Every
record remains schema-valid, and owner timing is explicitly treated as a broad
window rather than instantaneous input. No field claims an executed mutation,
commit token, achieved restoration, or arrival gate. The observer performs
only that bounded read-only target lookup; it still has no event, store,
callback, key, entity handle, due-event executor, cancellation, reschedule,
load, mutation, or arrival-gate authority. Slice 118 now defines the dormant
atomic-revalidation contract: an outer event-execution boundary bridges a
pre-Region scheduler identity/registration/generation check to fresh exact-slot
classification inside the Region object boundary, and the scheduler-store lock
is forbidden inside that boundary. The executable fixture proves the ordering
and every fail-closed outcome, but the declarations make no atomicity claim and
grant no mutation authority. Slice 119 supplies the read-only runtime boundary
proof: exact constructor/identity comparison and state classification occur
while the real Region object monitor is held, the returned value is detached,
and no scheduler/event lock or mutation enters that boundary. The result is
still stale immediately after release and is unconsumed by callbacks. Slice 120
now exposes only the boundary-performed count/completeness and per-target fact
through additive private schema-v42 while preserving schema-v41 and every
non-atomic/non-authoritative flag. The next gate is owner validation on the
same authored magic-tree transient. One human-tolerant marker inside the broad
pending window should show one available/classified target, complete boundary
coverage, `objectBoundaryHeldDuringClassification=true`, and the unchanged
exact-authored-transient mutation-precondition category. A later marker may
legitimately show zero after natural completion. No route needs sub-second or
instantaneous owner input; automate any future test that does.

The diagnostic must not shrink an envelope, permit retirement, retain an NPC,
or become a registry or arrival gate. Active census evidence is explanatory;
it is not by itself proof that a source can be loaded, retained, retired, or
reconstructed safely.
Do not create a global entity registry or grant loading, teardown, or reload
authority. Terrain replay, collision derivation, event ownership, transactional
teardown, and rollback remain later gates.
Any later commit token or lifecycle consumer must remain unable to alter the
authoritative packed Region registry until ownership, residency, players,
NPCs, objects, ground items, collision, reload, and recovery preconditions can
be proved together.
A new database schema, authoritative region storage, actual loading/eviction,
collision/pathing adoption, client protocol adoption, Builder, export,
relocation, and level `-2` remain separately gated.
