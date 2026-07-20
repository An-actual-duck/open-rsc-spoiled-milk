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
packed region objects straddle logical level boundaries. Maps, packets,
authoritative region storage, and non-Player entities have not adopted the
contract yet. `EntityHandler` can project an already matched legacy object
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

RegionManager can also compare immutable layered tile snapshots with their
direct packed sources, evaluate the tile-mask portion of one adjacent step,
and compose those decisions across an explicit route of at most 50 adjacent
steps. These are dormant read-only projections: they do not choose a route,
inspect occupancy, enqueue movement, or replace `PathValidation`.

RegionManager can additionally recheck a bounded batch of dormant logical
retirement candidates and aggregate the results into immutable packed-source
readiness. Every logical Region covered by a packed source—including both
levels when a 48-tile source straddles a 944-tile legacy plane boundary—must
have an eligible decision in the same atomic snapshot. Missing or refused
coverage, partial multi-source residency, and partial legacy-domain edge sources
remain blocked. This readiness contains no Region handle and cannot unload,
unregister, remove, or evict packed storage; eager packed residency remains
authoritative. A second read-only assessment can snapshot exact player, NPC,
scenery-object, ground-item, and tile-storage presence for those sources. It
separates content quiescence from lifecycle readiness and currently reports
`RELOAD_PATH_UNAVAILABLE` for every source because the legacy runtime has only
whole-world loading, not a safe per-Region reload path.

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
not contain username text, IP addresses, credentials, or tile payloads. New
traces emit `schema/layered-map-parity-event-v19.schema.json`. Each v19 record
retains the complete v18 position, logical-window, interest-delta,
packed-coverage,
logical 48×48 snapshot, current-tile parity, and 3×3 neighborhood evidence.
Start, marker, teleport, and stop records also carry all eight dormant adjacent
tile-mask comparisons: directions and destinations, nullable decision/reason
pairs, required-state counts, and exactness summaries. Tile masks and tile
payloads are never written. Other event types carry explicit nulls instead of
repeating the tile comparisons on every movement. The v1-v18 schemas remain
alongside it—including
`schema/layered-map-parity-event-v18.schema.json`,
`schema/layered-map-parity-event-v17.schema.json`,
`schema/layered-map-parity-event-v16.schema.json`,
`schema/layered-map-parity-event-v15.schema.json` and
`schema/layered-map-parity-event-v14.schema.json`—so already-captured logs keep
explicit readable contracts.
Marker and stop records may additionally summarize the latest 16 contiguous
ordinary walking steps since the previous reset, including per-step decisions,
aggregate parity, capacity evictions, and discontinuities. Teleports, login,
and start reset that observer-local route; no route is stored on the player or
used by movement. Selected records also include versioned logical Region
residency counts and bounded missing/partial load, exited release, and
unsupported-current evidence. Ordinary moves omit that comparison unless their
logical interest window changes. These are diagnostic candidates only: they do
not load or unload Regions. v11 additionally records the current Player's
opaque interest-owner sequence, ledger version, owned/distinct Region counts,
minimum/maximum shared-reference count, and exact global/shared acquisitions
or releases at login, window changes, and logout. Ordinary same-window moves
carry an explicit null. Owner identities are process-local diagnostic handles,
not database IDs, username hashes, entity indexes, or persistence keys; these
reference counts likewise cannot retain, load, release, or evict a Region.
When a trace survives logout, login atomically rebinds its current-owner reader
to the newly constructed Player before recording the login event. v12 adds a
bounded retirement projection for exact transition Regions and recently
globally released Regions. Each entry records its server tick, 16-tick grace,
current reference/residency state, release and eligibility ticks, and one of
`PINNED`, `COOLING_DOWN`, `RETIREMENT_ELIGIBLE`, `NOT_RESIDENT`, `UNSUPPORTED`,
or `UNTRACKED`. The observer retains at most 4096 recent release candidates,
reports any diagnostic overflow, and removes canceled candidates after a
positive reference is observed. Expiry remains evidence only: no observer,
schema field, or candidate list can unload or evict a Region. v13 additionally
retains at most 4096 immutable eligible snapshots and asks the dormant
source-level arbiter to recheck each under the existing Region lifecycle lock.
Each decision records candidate/current ownership and residency versions,
release identity and timing, the current cooldown state, and an explicit
eligible or refusal reason. Refused candidates are reported once and then
removed; eligible candidates may be rechecked idempotently. These snapshots
remain observer-owned evidence rather than a loading, retention, retirement,
or eviction queue. v14 aggregates that same atomically rechecked decision batch
by legacy packed source. It records ready and blocked counts plus each source's
covered, missing, refused, and partially resident logical keys, cross-level
status, and exact readiness state. It does not call the manager preparation
method separately and gains no Region handle or lifecycle authority.
v15 adds the contents assessment for the exact emitted readiness value. It
records stable blocker names and counts for players, NPCs, scenery objects,
ground items, tile storage, and reload support. These counts are ephemeral
diagnostic evidence, not a claim or unload token; the current missing reload
path keeps lifecycle-ready count at zero.
v16 additionally projects the immutable whole-world population generation and
count-only authored construction origins onto those exact safety sources. It
separates scenery, boundaries, NPC spawns, ground-item spawns, and harvesting
conversions, and explicitly records `originCountsOnly=true` and
`reconstructionManifest=false`. The counts do not classify current entities,
retain placement definitions, or authorize teardown/reload.
v17 compares the manifest identities for those exact safety sources with a
bounded count-only census of current authored runtime identity metadata. It
separates exact matches, absent and duplicate identities, active and inactive
NPC state, NPCs roaming away from their authored source, temporary authored
object replacements, stale generations, and unrecognized identities, with
per-family expected/runtime counts. It explicitly records
`identityMetadataOnly=true`, `entityRegistry=false`, and
`lifecycleAuthority=false`; neither the observer nor its JSONL payload can
retain an entity or authorize loading, teardown, or reload.
v18 adds a closed, deterministic anomaly-detail list for the v17 count
categories. Each detail names the generation-fenced source, ordinal, family,
manifest definition and constructed ID, construction coordinate, and—when a
runtime instance exists—a detached current ID, source, activity flag, and
instance counts. The list contains at most 4,096 entries and reports the exact
number omitted beyond that limit. Nullable fields distinguish an absent
runtime instance or an identity not recognized by the current manifest. These
primitive facts remain observer-only: no entity, Region, registry, callback,
or lifecycle handle is serialized or retained.
v19 preserves the complete authored manifest as replay history while applying
the detached final-population outcome to provenance expectations. It reports
manifest, superseded, and final-live counts and emits bounded deterministic
predecessor/successor metadata for scenery-anchor and
boundary-anchor-and-direction collisions. Expected startup supersessions no
longer appear as false absences; a superseded identity that unexpectedly
reappears is an explicit anomaly. The outcome and JSON contain no entity,
Region, registry, callback, or lifecycle handle.

## Checked Player mirror

`Player` is the first dual-representation runtime owner, but its inherited
packed `Point` remains the sole gameplay authority. `LayeredLocationMirror`
synchronizes only from that packed value during initial placement and existing
location changes. `LayeredRegionMembershipMirror` derives a checked
world-space/level-qualified `WorldRegionKey` shadow from that location.
`LayeredVisibilityWindowMirror` additionally shadows the manager projection
for the accepted Player location and configured view distance.
`Player.getLayeredLocation()`, `Player.getLayeredRegionKey()`, and
`Player.getLayeredVisibilityWindow()` are read-only and refuse stale or
uninitialized mirror state. Movement, authoritative region storage, caches,
collision, packets, scripts, terrain, and the client do not consume these
mirrors. The private `::layerparity` command verifies all three invariants
before starting or inspecting a trace.

## Checked legacy Player persistence shadow

`LegacyPlayerLocationPersistenceSnapshot` captures one authoritative packed
Player point, proves its exact layered round trip, and retains the original X/Y
for the unchanged database writer. Full saves and the separate offline-location
update entry point use that checked packed snapshot. Loads capture the same
snapshot before initial placement and compare its layered value to the Player
mirror on the single-threaded load path.

This is a legacy persistence shadow, not the future layered persistence format.
No column or SQL statement changes; no row is migrated; signed X, level `-2`,
and non-global world spaces remain deliberately unrepresentable. A later
versioned/additive persistence slice is still required before those capabilities
can become authoritative.

## Logical visibility-window projection

`WorldRegionWindow` defines inclusive logical-region bounds in one world space
and on one signed level. `RegionManager.getLayeredVisibleRegionWindow(...)`
projects the current view-distance units into that value with signed floor
division and checked arithmetic. Logical region size is declared separately on
`WorldRegionKey`, even though both logical regions and legacy terrain sectors
remain 48 tiles during parity migration.

This projection does not query or populate `RegionManager.regions`, use a
visibility cache, enumerate entities, alter the current packed visibility
window, or participate in client streaming. Its checked Player shadow and
private v3 diagnostics compare projected interest bounds, but neither becomes
an interest/residency authority.

`WorldRegionInterestDelta` can materialize deterministic X-major/Y-minor
entered, retained, and exited key lists between two windows. Its required
caller-supplied key budget limits one materialization operation, not world
capacity. World space and signed level are part of key identity, so equal X/Y
bounds on another level retain no keys. This value remains dormant: Player,
RegionManager lookup/caches, packets, terrain, and client residency do not
consume it.

`LayeredRegionInterestOwnershipLedger` further defines the future global
reference rule without adopting it. It allocates opaque, ledger-bound,
process-local owner handles and atomically replaces each owner's complete
logical window. Per-key before/after counts distinguish a local exit that is
still shared from the final `1 -> 0` global release. RegionManager now owns one
checked ledger, and each logged-in Player maintains one opaque handle across
logical-window changes and final logout cleanup. The shadow cannot load,
retain, release, or evict a Region, and ordinary movement within one logical
window does not rematerialize its keys.

`LegacyPackedRegionCoverage` describes every logical key touched by one current
packed 48-tile region cell. It distinguishes the nominal packed cell from the
portion accepted by the legacy point codec, so terminal partial cells and the
server's post-codec padded rows remain explicit. The RegionManager projection
does not access or alter packed storage.

`LegacyPackedRegionPartition` refines that coverage into contiguous,
non-overlapping tile fragments. Each fragment retains packed absolute and
cell-local bounds, signed logical bounds, logical region identity, and exact
tile count. Level straddles, same-level upper-plane misalignment, terminal
partial cells, and empty padding therefore have deterministic lossless split
plans without reading or replacing a runtime `Region` or its tile grid.

`LegacyLogicalRegionAssembly` inverts those fragments for one requested
logical region key. It retains nominal 48×48 target bounds separately from the
legacy-supported intersection and reports the ordered packed source cells,
assembled tile count, and complete/partial/unsupported status. Negative space,
new signed levels, isolated world spaces, and terminal legacy edges stay
explicit instead of being clamped or assigned invented packed sources.

`LegacyLogicalTileAddress` resolves one checked logical region-local X/Y to its
logical location and, when representable, the exact packed point, packed source
cell, cell-local X/Y, and assembly fragment. Unsupported terminal, negative,
deep-level, and isolated-space tiles retain their logical identity without a
fabricated packed address. The projection does not read a runtime `TileValue`.

`LayeredRegionTileSnapshot` is the first explicit read-only runtime tile seam.
RegionManager can copy all supported packed `TileValue` state into a detached
logical 48×48 snapshot, leaving unsupported positions absent and reporting
packed sources that were not already loaded. Snapshot internals use immutable
full-fidelity `LayeredTileState` values; logical callers may read those values
directly, while legacy callers receive fresh mutable compatibility copies. A
stable SHA-256 covers logical identity, support layout, source metadata, and
complete collision/terrain tile state using the accepted v5 field order. The
snapshot is not cached and is not collision, pathing, visibility, terrain,
packet, or entity authority.

`LayeredTileStateParityComparison` checks one current tile through two read-only
paths: its direct packed source cell and its immutable assembled logical
snapshot. Exact full-state equality, missing packed sources, and unsupported
logical tiles remain distinct. RegionManager exposes the comparison through a
non-mutating packed-region peek; no Player, diagnostic, collision, cache,
packet, or client path consumes it.

`LayeredTileNeighborhoodParityComparison` retains the nine checked tile-state
comparisons at offsets `-1..+1` around one logical center. RegionManager reuses
detached logical snapshots only within that call and reads direct sources
through its non-creating packed-region peek. The neighborhood reports explicit
supported, missing, unsupported, comparable, and exact counts; no movement,
collision, pathing, cache, Player, diagnostic, or client path consumes it.

`LegacyPackedVisibilityCoverageComparison` unions that coverage across the
current packed candidate window and compares it with the intended signed
logical window. It keeps expected, covered, missing, extra, and unsupported
states explicit under caller-supplied allocation budgets. Extra packed-union
keys are storage candidates, not proof of gameplay visibility: current region
lookup, caches, entity filters, packets, and client residency remain unchanged.
