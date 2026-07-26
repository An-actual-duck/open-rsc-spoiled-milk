# Raw Layered Terrain Sector v1

`raw-layered-sector-v1` is the compact full-fidelity terrain payload for a
single `48 x 48` native layered storage sector.

The payload is exactly 23,040 bytes. Tiles are stored in
`x-major-y-minor` order: local Y `0..47` for local X `0`, then local Y
`0..47` for local X `1`, through local X `47`.

Each tile is exactly ten bytes:

| Offset | Width | Value |
| ---: | ---: | --- |
| 0 | 1 | unsigned elevation |
| 1 | 1 | unsigned ground texture |
| 2 | 1 | unsigned ground overlay |
| 3 | 1 | unsigned roof texture |
| 4 | 1 | unsigned vertical wall |
| 5 | 1 | unsigned horizontal wall |
| 6 | 4 | unsigned diagonal-wall bits, big-endian |

The vertical-before-horizontal order is the native package and client-wire
order. A legacy ORSC sector uses horizontal-before-vertical bytes, so
conversion must swap offsets 4 and 5 for every tile and prove the reverse
transformation reproduces the exact legacy payload.

Identity is never inferred from the payload or its filename. The enclosing
`layered-world-package-v1` manifest supplies world space, signed level, sector
X/Y, encoding, normalized relative path, and SHA-256.
