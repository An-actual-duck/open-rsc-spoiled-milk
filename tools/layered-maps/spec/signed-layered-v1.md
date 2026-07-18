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

## Dormant server binding

The Java 8 server binding lives under
`com.openrsc.server.model.world.coordinate`. Its immutable values match this
contract, while `LegacyPackedPointAdapter` is the only approved Slice 3 bridge
to the existing packed `com.openrsc.server.model.Point`.

The adapter decodes a legacy `Point` into the `global` world space. Reverse
conversion requires `global` explicitly and applies every checked legacy-domain
restriction above. The existence of this binding does not mean a runtime
entity, region, packet, map, or persisted record has adopted layered identity.
