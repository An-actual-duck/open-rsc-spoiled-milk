# Layered Maps

This folder is the non-mutating foundation for the signed layered-map
capability. It currently provides:

- the `signed-layered-v1` coordinate contract;
- immutable Java 8 reference values;
- the checked `legacy-packed-y-v1` codec;
- the checked `legacy-terrain-sector-name-v1` archive-name codec;
- a read-only preflight for the first supported repository adapter; and
- lossless, non-relocating normalization of recognized terrain, placements,
  and transition data into a layered inventory; and
- deterministic lexical classification of unresolved Java coordinate owners
  into migration families without parsing or rewriting them.

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
- `normalization.md` is the operator summary;
- `coordinate-owner-classification.json` is stable AI-readable migration
  triage for every unresolved Java owner; and
- `coordinate-owner-classification.md` is its operator-readable companion;
- `java-coordinate-occurrences.json` inventories content-topology teleport,
  point, and area call shapes with file/line/argument evidence; and
- `java-coordinate-occurrences.md` summarizes those sources and counts.

The inventory decodes terrain planes, known location JSON coordinates, and
directed object telepoints without changing their topology. It reverse-encodes
every supported coordinate and reconstructs every placement record to prove a
semantic legacy round trip. Coordinates outside the named legacy codec remain
raw, visible findings; they are never guessed or corrected.

Terrain entries report both their original non-negative archive indices
(`legacySectorX/Y`) and their logical signed map-sector identity (`sectorX/Y`).
The legacy archive grid adds 48 sectors on X and 37 on Y, so `h0x48y37` is
logical global level-0 sector `(0,0)`. Archive coordinates, logical map sectors,
and runtime region keys are distinct contracts even where they share a 48-tile
size.

Java coordinate owners remain fingerprinted, unresolved inputs. The separate
classification report labels likely migration owners, ambiguous standalone
`944` literals, and definite substring signal collisions. Likely owners are
grouped by primary migration family and risk. This is lexical triage, not Java
coordinate parsing: it deliberately retains every candidate and its evidence.
The command does not rewrite Java, align areas, create a Builder project,
launch a server, or make anything eligible for game import/export.

The occurrence inventory masks comments and literals, follows balanced Java
parentheses, preserves normalized argument expressions, and fingerprints the
result. It does not resolve Java symbols or infer that every lexical
`teleport(...)` shape is a call rather than a declaration. Literal-only and
expression-bearing occurrences remain distinct so a later migration parser can
advance without hiding unresolved script behavior.

## Staged server binding

The matching Java 8 server values and checked packed-`Point` bridge live in
`server/src/com/openrsc/server/model/world/coordinate/`. Preflight recognizes
that package as a resolved coordinate contract rather than an unresolved Java
owner. The existing server `Area` is the first deliberately narrow consumer:
it can expose a checked immutable `WorldArea` snapshot and test a
`WorldLocation`, while its packed fields and existing methods remain
authoritative. `RegionManager` can also calculate a `WorldRegionKey` without
using it for storage or lookup. This distinction matters because 944-tile
legacy level bands do not divide evenly into 48-tile regions: two current
packed region objects straddle logical level boundaries. Entities,
maps, packets, persistence, and authoritative region storage have not adopted
the contract yet. `EntityHandler` can project an already matched legacy object
telepoint into `WorldObjectTransition`; the XML map, command matching, and
runtime teleport callers remain unchanged. This object-specific name leaves
the broader transport/recovery/instance transition model open for later
design. `WorldEditorTerrainArchive.Coordinates` may similarly expose a checked
`WorldMapSectorId`, but archive lookup and both authoritative terrain copies
remain unchanged. `GameObjectLoc`, `ItemLoc`, and `NPCLoc` expose checked
layered snapshots as well; their JSON, mutable packed fields, loaders, and
runtime construction remain authoritative. NPC roaming bounds use the
inclusive `WorldTileBounds` contract rather than the open-boundary
`WorldArea` contract.

## Private runtime parity observer

The first owner-testable runtime seam remains observational: it projects a dev
player's existing packed location into signed layered identity and writes
schema-versioned JSONL without changing movement, teleports, packets, regions,
terrain, or saved coordinates. It is disabled by default in both local and
hosted configuration and requires a dev/admin account.

Launch only the private development server with the capability enabled:

```bash
OPENRSC_LAYERED_MAP_PARITY_OBSERVER=true ./scripts/run-server.sh
```

Then use:

```text
::layerparity start
::layerparity mark before-ladder
::layerparity snapshot
::layerparity status
::layerparity stop
```

While ACTIVE, ordinary movement and teleports are captured automatically.
Leave capture active through logout/reconnect if that transition is under
test; `stop` deliberately ends it. Logs are isolated by database ID and
username hash under `server/logs/layered-map-parity/`. They contain packed and
layered positions, world space, level, logical region and terrain-sector keys,
local sector coordinates, transition deltas, and round-trip status. They do
not contain username text, IP addresses, or credentials. Each line conforms to
`schema/layered-map-parity-event-v1.schema.json`.
