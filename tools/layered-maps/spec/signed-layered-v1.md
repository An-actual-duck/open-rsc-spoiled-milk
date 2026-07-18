# Signed Layered Coordinate Contract v1

Capability identifier: `world.layered-coordinates`

Coordinate model: `signed-layered-v1`

Legacy codec: `legacy-packed-y-v1`

## Geographic coordinate

`WorldCoordinate` is the immutable triple `(x, y, level)`:

- X and Y are signed 32-bit tile coordinates.
- Surface is level `0`.
- Each floor above increments level: `+1`, `+2`, and onward.
- Each underground depth decrements level: `-1`, `-2`, and onward.
- Ordinary vertical anchors preserve X/Y. Collision-adjusted arrival offsets
  are separate transition metadata.

## World-space identity

`WorldLocation` combines an opaque `WorldSpaceId` with a
`WorldCoordinate`. The initial static world uses ID `global`. Instance identity
is never packed into X, Y, or level.

## Terrain addressing

The first layered terrain format retains `48 x 48` storage sectors. Sector and
local coordinates use floor division and floor modulo so signed X/Y values are
well-defined:

```text
sectorX = floorDiv(x, 48)
localX  = floorMod(x, 48)
sectorY = floorDiv(y, 48)
localY  = floorMod(y, 48)
```

Storage-sector size does not determine renderer, server simulation, visibility,
or network streaming cell size.

`WorldMapSectorId(worldSpace, level, sectorX, sectorY)` gives this logical
terrain-sector identity a dedicated type. It is intentionally distinct from
`WorldRegionKey`: both currently use 48-tile floor-divided indices, but map
ownership/storage and simulation-region lifetime are different contracts and
may evolve independently.

## Legacy terrain-sector name codec

Legacy archive entries use `h{plane}x{archiveSectorX}y{archiveSectorY}`. Their
indices include historical whole-sector offsets:

```text
logicalSectorX = archiveSectorX - 48
logicalSectorY = archiveSectorY - 37
```

The `legacy-terrain-sector-name-v1` codec maps the plane through
`legacy-packed-y-v1`, assigns the `global` world space, and applies those
offsets. Reverse encoding requires `global`, one of the four representable
legacy levels, and logical indices that remain non-negative after the archive
offsets are restored. Unsupported names, overflow, layered-only levels,
instances, and archive-negative results are refused.

Normalized inventories retain `legacyEntry`, `legacyPlane`, and
`legacySectorX/Y` for exact reconstruction while using signed logical
`sectorX/Y` for map identity. Terrain payload bytes are not changed.

## Legacy packed-Y codec

The legacy format has four 944-tile Y bands:

| Legacy plane | Packed Y | Layered level | Layered Y |
| --- | --- | --- | --- |
| 0 | `0..943` | `0` | packed Y |
| 1 | `944..1887` | `+1` | packed Y - 944 |
| 2 | `1888..2831` | `+2` | packed Y - 1888 |
| 3 | `2832..3775` | `-1` | packed Y - 2832 |

X is unchanged. Reverse encoding is defined only for non-negative legacy X,
layered Y `0..943`, and levels `0`, `+1`, `+2`, or `-1`. Level `-2`, negative
layered X/Y, expanded extents, and other layered-only values are refused rather
than silently truncated or reinterpreted.

Legacy conversion is one way once layered-only features are used.

## Staged server binding

The Java 8 server binding lives under
`com.openrsc.server.model.world.coordinate`. Its immutable values match this
contract, while `LegacyPackedPointAdapter` is the checked bridge to the
existing packed `com.openrsc.server.model.Point`.

The adapter decodes a legacy `Point` into the `global` world space. Reverse
conversion requires `global` explicitly and applies every checked legacy-domain
restriction above.

`WorldArea` is an immutable rectangle bound to one world space and one signed
level. Its boundaries are open, matching the historical server
`Area.inBounds` comparisons. The mutable packed `Area` may produce a checked
snapshot only when both legacy boundaries decode into the same level; a
cross-band rectangle is refused instead of being silently flattened. This
first consumer does not replace packed area storage or authorize entity,
region, transition, map, packet, or persistence adoption.

`WorldTileBounds` is a separate immutable inclusive rectangle. It models
placement ranges such as NPC minimum/maximum roaming tiles, whose historical
checks include both endpoints. Open `WorldArea` and inclusive
`WorldTileBounds` must not be substituted merely because their fields look
similar.

The server's mutable packed `GameObjectLoc` and `ItemLoc` values may expose a
checked `WorldLocation` snapshot. `NPCLoc` may expose its checked start
location and inclusive roaming bounds. These projections are calculated from
current fields on demand; they do not replace JSON shape, loader behavior, or
runtime entity construction. A partial record remains partial: a valid NPC
start does not make an invalid or cross-level roaming maximum representable.

`WorldRegionKey(worldSpace, level, regionX, regionY)` is the immutable logical
identity of a 48-tile region. Region indices derive from layered X/Y with floor
division, so negative coordinates behave consistently. The server
`RegionManager` may calculate this key from either a checked legacy `Point` or
a `WorldLocation`, but current nested packed-coordinate maps remain the lookup
authority.

This is intentionally a projection rather than an identity attached to legacy
`Region`. The 944-tile packed floor stride is not divisible by 48. Packed region
Y 19 contains both Y 943 (level 0) and Y 944 (level +1), while packed region Y
39 contains both Y 1887 (level +1) and Y 1888 (level +2). A later storage
migration must split those regions; it must not assign either straddling object
one misleading layered key.

`WorldObjectTransition(source, destination, command)` is the immutable directed
projection of the existing object-command telepoint category. The server first
performs its historical packed `Point` lookup and case-insensitive command
match, then may expose the matched source, destination, and exact stored
command through this layered value. Existing handlers still consume
`TelePoint` and teleport with packed X/Y.

This type deliberately does not claim to be the universal transition schema.
Boats, spells, quest routing, death/recovery, area transport, and instance
entry/exit require their own typed metadata or a later common contract; they
must not be forced into an object-command field merely because object
telepoints were migrated first.
