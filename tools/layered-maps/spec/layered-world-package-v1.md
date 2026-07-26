# Native Layered World Package v1

Capability: `world.layered-terrain`

Manifest: `layered-world-package-v1`

Coordinate model: `signed-layered-v1`

## Purpose

A package declares native terrain by explicit world space, signed level, and
logical sector coordinates. It does not encode level through Y offsets, archive
plane numbers, directory ordering, or a fixed list of floors.

The package root contains `manifest.json`. Every terrain or placement payload
is a contained regular file with an exact SHA-256 in that manifest. Validation
rejects unknown fields, unsafe paths, symlinks, duplicate identities, reused
payload paths, undeclared world spaces or levels, and changed payloads before
runtime loading.

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

The first runtime publication contract uses 24-tile chunks and a radius-one
readiness window centered on the Player's current global chunk. Its nine slots
are ordered x-major/y-minor and each is either complete terrain or explicit
void. A context packet commits all nine slots atomically. Crossing a 24-tile
boundary refreshes that window before the matching movement snapshot, but a
same-package window shift is not a world-space/level scope reset. The retained
48-tile page remains only storage/provenance.

## Terrain payloads

Each `terrainSectors` record declares:

- `worldSpace`, signed `level`, signed `sectorX`, and signed `sectorY`;
- a versioned `encoding`;
- a normalized relative payload `path`; and
- exact payload `sha256`.

`uniform-layered-sector-v1` is a compact laboratory encoding that describes one
complete 48-tile page using a single static tile value. It exists to prove the
native loader and arbitrary-level contract without aliasing a legacy plane.

`rle-layered-sector-v1` is the human-readable full-fidelity v1 encoding. Its `runs` expand to
exactly 2,304 tile values in `x-major-y-minor` order: all local Y coordinates
for local X `0`, followed by all local Y coordinates for local X `1`, through
local X `47`. Each run carries a positive `count` and the seven terrain scalars
used by the legacy sector representation. A page with no repeated neighbors
may use 2,304 one-tile runs, so compression never limits fidelity. The loader
rejects underfill, overfill, zero/negative counts, extra fields, invalid scalar
ranges, or a different tile order.

`raw-layered-sector-v1` is the compact full-scale package encoding. It stores
the same 2,304 tiles as exactly 23,040 bytes in the same
`x-major-y-minor` order. Each ten-byte tile contains elevation, texture,
overlay, roof, vertical wall, horizontal wall, then the big-endian 32-bit
diagonal-wall value. This order matches the native client wire contract. It
deliberately differs from legacy ORSC's horizontal-before-vertical byte order;
conversion must swap those two bytes and verify that reversing the transform
reproduces the exact source payload. The payload carries no implicit level or
sector identity.

## Entity-placement payloads

Each `placementSets` record declares:

- a stable set `id`;
- `worldSpace` and signed `level`;
- versioned `encoding`;
- a normalized relative payload `path`; and
- exact payload `sha256`.

The array may be empty for a terrain-only module or an intermediate isolated
review package. Runtime publication of a complete world remains a separate
profile-level decision; the package loader does not invent placements.

`layered-entity-placements-v1` owns NPC and ground-item spawns. Every placement
has a package-wide stable `placementId`; IDs cannot be reused across sets or
entity kinds. NPCs carry an ID, exact layered start, and bounded roam radius.
Ground items carry an ID, exact layered position, positive amount, and bounded
positive respawn time. The payload repeats its world space and level so a
manifest/payload disagreement refuses rather than reinterpreting coordinates.

Every NPC roaming tile and ground-item position must resolve to terrain owned
by the same package. The standalone tool and server source validate the same
identity, range, terrain-coverage, path, and hash boundaries independently.

The first private runtime registers these entities during world population.
The developer entry command only changes the Player's location and verifies
that package population already happened; it does not construct native
entities. Ground-item spawn identity includes complete `WorldLocation`, so the
same X/Y on different signed levels remains distinct. Respawn callbacks carry
a world-lifecycle generation and cannot repopulate after unload/reset.

Package-owned scenery/boundary placements and transitions remain separate
future versioned indexes because their layered collision/transition ownership
must be implemented explicitly rather than inferred from the entity format.
