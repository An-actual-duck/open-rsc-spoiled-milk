# Native Layered World Package v1

Capability: `world.layered-terrain`

Manifest: `layered-world-package-v1`

Coordinate model: `signed-layered-v1`

## Purpose

A package declares native terrain by explicit world space, signed level, and
logical sector coordinates. It does not encode level through Y offsets, archive
plane numbers, directory ordering, or a fixed list of floors.

The package root contains `manifest.json`. Every terrain payload is a contained
regular file with an exact SHA-256 in that manifest. Validation rejects unknown
fields, unsafe paths, symlinks, duplicate sector identities, duplicate payload
paths, undeclared world spaces or levels, and changed payloads before runtime
loading.

## Level expansion

`levels` is a data collection. Surface `0`, shallow underground `-1`, and deep
underground `-2` are conventions. `-3`, `+3`, or another signed 32-bit level is
valid when declared for its world space. Adding a level must not require a new
coordinate codec, loader branch, protocol opcode, renderer constant, collision
rule, or persistence field.

World-space identity remains separate. Static global depth uses world space
`global`; future instance templates declare their own world-space entries and
do not reserve magic level values.

## Storage and presentation

The v1 storage page remains `48 x 48` tiles to preserve a straightforward
vanilla conversion boundary. `presentationChunkSize` is independently declared
and must be a positive divisor of 48. It is a readiness/streaming subdivision,
not a coordinate or package-ownership unit. The initial fixture uses `24`.

Runtime code may decode one 48-tile payload and publish smaller presentation
products keyed by world space, level, and global chunk coordinate. Crossing a
storage page must not force an abrupt 48-tile visual reload.

## Terrain payloads

Each `terrainSectors` record declares:

- `worldSpace`, signed `level`, signed `sectorX`, and signed `sectorY`;
- a versioned `encoding`;
- a normalized relative payload `path`; and
- exact payload `sha256`.

`uniform-layered-sector-v1` is a compact laboratory encoding that describes one
complete 48-tile page using a single static tile value. It exists to prove the
native loader and arbitrary-level contract without aliasing a legacy plane.
The definitive converted vanilla package will use a full-fidelity sector
encoding selected and parity-tested in the next conversion slice.

Placements and transitions will be separate, versioned, hash-addressed package
indexes. They are intentionally not improvised into this first terrain
descriptor before their runtime ownership cut is implemented.
