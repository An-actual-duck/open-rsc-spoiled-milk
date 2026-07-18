# Layered Maps

This folder is the non-mutating foundation for the signed layered-map
capability. It currently provides:

- the `signed-layered-v1` coordinate contract;
- immutable Java 8 reference values;
- the checked `legacy-packed-y-v1` codec; and
- a read-only preflight for the first supported repository adapter; and
- lossless, non-relocating normalization of recognized terrain, placements,
  and transition data into a layered inventory.

It does **not** convert maps, change runtime coordinates, edit archives, modify
player data, launch a server, or export into a game.

## Preflight

From the repository root:

```bash
./tools/layered-maps/layered-maps.sh preflight
```

The launcher compiles into the ignored `tools/layered-maps/build/` directory
and writes deterministic reports into the ignored
`tools/layered-maps/workspace/preflight/` directory:

- `preflight.json` for tools and AI analysis;
- `preflight.md` for a map author.

The command reads the target repository and writes only to its selected
workspace. The CLI can also be compiled independently and pointed at an
external workspace:

```bash
java -cp classes com.openrsc.layeredmaps.LayeredMapsCli preflight \
  --root /path/to/repository \
  --workspace /path/to/isolated/workspace
```

## Supported adapter

Slice 1 recognizes `spoiled-milk-repository-v1`. It requires the maintained
server/client build markers, `server/myworld.conf`, and byte-identical server
and client `Custom_Landscape.orsc` archives. It inventories location files,
transition definitions, and Java sources containing coordinate-related signals
as migration candidates. Candidate status is intentionally conservative: it
means a later converter must inspect the source, not that preflight has parsed
or rewritten it.

Unknown or inconsistent targets are refused with an actionable error.

## Normalize recognized sources

After preflight succeeds:

```bash
./tools/layered-maps/layered-maps.sh normalize
```

Normalization writes only under the ignored
`tools/layered-maps/workspace/normalize/` directory:

- `world-inventory.json` is the complete machine-readable inventory;
- `normalization-summary.json` is the compact AI-readable report; and
- `normalization.md` is the operator summary.

The inventory decodes terrain planes, known location JSON coordinates, and
directed object telepoints without changing their topology. It reverse-encodes
every supported coordinate and reconstructs every placement record to prove a
semantic legacy round trip. Coordinates outside the named legacy codec remain
raw, visible findings; they are never guessed or corrected.

Java coordinate owners remain fingerprinted, unresolved inputs. This command
does not rewrite Java, align areas, create a Builder project, launch a server,
or make anything eligible for game import/export.

## Staged server binding

The matching Java 8 server values and checked packed-`Point` bridge live in
`server/src/com/openrsc/server/model/world/coordinate/`. Preflight recognizes
that package as a resolved coordinate contract rather than an unresolved Java
owner. The existing server `Area` is the first deliberately narrow consumer:
it can expose a checked immutable `WorldArea` snapshot and test a
`WorldLocation`, while its packed fields and existing methods remain
authoritative. Regions, entities, transitions, maps, packets, and persistence
have not adopted the contract yet.
