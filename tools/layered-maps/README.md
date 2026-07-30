# Layered Maps

This folder is the non-mutating foundation for the signed layered-map
capability. It currently provides:

- the `signed-layered-v1` coordinate contract;
- immutable Java 8 reference values;
- the checked `legacy-packed-y-v1` codec;
- the checked `legacy-terrain-sector-name-v1` archive-name codec;
- a read-only preflight for the first supported repository adapter; and
- lossless, non-relocating normalization of recognized terrain, placements,
  and transition data into a layered inventory;
- deterministic lexical classification of unresolved Java coordinate owners
  into migration families without parsing or rewriting them;
- a deterministic Preservation revision-64 baseline manifest; and
- the strict `layered-world-package-v1` descriptor for data-declared world
  spaces, arbitrary signed levels, hash-addressed terrain and entity-placement
  payloads, and presentation chunks smaller than the 48-tile storage page.

It does **not** edit source archives or modify player data. Conversion writes
an isolated package first; release and deployment tooling must independently
validate that package before copying it into a release or the external live
state. Ordinary private launches retain the legacy path unless
`scripts/run-server.sh --layered-production` is selected. Hosted activation is
fail-closed around the exact reviewed package identity.

## Active Spoiled Milk layered world

The active integration target is the complete current Spoiled Milk world:

```bash
./tools/layered-maps/layered-maps.sh spoiled-milk-package
```

This command preserves the frozen Preservation audit independently, then
validates the active `server/myworld.conf` selectors and reproduces the
configured Spoiled Milk world. Preservation package generation still requires
the repository to reproduce its frozen source set; active Spoiled Milk
generation uses its separately pinned current terrain and content composition.
It converts the exact matching
server/client `Custom_Landscape.orsc` pair (1,771 source sectors), inventories all
33,624 placement inputs selected from base, feature, and MyWorld sources, and
applies the same removal, cleanup, same-slot replacement, and Harvesting rules
as legacy population. The current conversion then applies the reviewed
Zanaris and lava-forge relocations described below, producing 1,782 native
terrain sectors.

The resulting six level-qualified sets contain 33,512 effective placements:
971 boundaries, 27,887 scenery objects, 3,775 NPCs, and 879 ordinary ground
items. The scenery count includes 143 ground-item locations reclassified as
their configured harvestable scenery. The one existing Hobgoblin maximum-Y
correction remains necessary for the legacy record to decode and retains its
explicit receipt. Scenery direction `8` is preserved for the travel cart;
boundaries remain restricted to directions `0..7`.

Package `0.4.0` moves the complete connected Zanaris/Fairy Dimension island
from global level `-1` to global level `+10`, preserving authored X/Y. The
fail-closed transform requires the reviewed 1,639-tile component at
`(126,686,-1)`, copies its exact one-tile presentation ring, clears the old
2,206-tile component-plus-ring footprint to canonical void, and relocates
exactly 28 NPCs, four ground items, 194 scenery objects, and six boundaries.
The cleanup includes 567 source ring tiles, 214 of which carry wall structure
despite their void overlay. Unexpected terrain connectivity, ring structure,
destination overlap, or placement-count drift refuses package generation.

Package `0.5.0` additionally moves the isolated lava-forge/demon miniquest
component from global level `-1` to level `-2`, preserving authored X/Y. Its
fail-closed transform requires 2,170 connected non-void tiles at
`(329,587,-1)`, copies the exact 204-tile presentation ring, clears all 2,374
source-footprint tiles to canonical void, and relocates exactly 20 NPCs, one
ground item, and three scenery objects. The neighboring Taverley blue-dragon
dungeon is a separate protected component: all 2,955 of its terrain tiles and
its exact 83 NPC, 10 item, 217 scenery, and 11 boundary placements must remain
unchanged on level `-1`, or generation refuses.

The active package is
`rsc-remastered.spoiled-milk-layered-world@0.5.0`, manifest SHA-256
`f914d93e7abcf40dc281c06df5010269c7a9ce4fe4a16aaa6ae11f0d90a14306`
and package fingerprint
`add42670f99f1f43465a86fd03857febdb053763ec22485746b58ba06ed6661b`.
It is consumed by the `spoiled-milk-replacement` runtime profile. The server
configuration API still defaults every layered gate off, while the guarded
hosted configuration explicitly selects the reviewed production profile and
the private runner exposes the same profile through `--layered-production`.
This complete distribution is the reference integration target while the
layered engine and authoring path stabilize.

## RSC Remastered Preservation baseline (on hold)

The vanilla/Preservation distribution is on hold. Its baseline, audit,
generator, and receipts remain available so the work is not lost, but it is
not the active runtime or content target. A broadly reusable vanilla package
will resume after the complete Spoiled Milk layered stack is stable and its
content boundaries can be extracted deliberately.

From the repository root:

```bash
./tools/layered-maps/layered-maps.sh baseline
```

The read-only command verifies the Preservation selectors, revision-64 server
JAG/MEM inputs, byte-identical server/client authentic ORSC terrain, base
boundary/scenery/NPC/item placement sets, and copied Preservation seed. It
writes deterministic JSON and Markdown only under
`tools/layered-maps/workspace/baseline/`.

The accepted frozen result is checked in at
`baselines/rsc-remastered-preservation-r64-v1.json`. Regeneration must match it
byte-for-byte before direct vanilla conversion begins. This baseline is a
coordinated source set; neither the ORSC nor the SQLite seed alone is the
vanilla map.

The direct-conversion command generates a native parity review package:

```bash
./tools/layered-maps/layered-maps.sh preservation-package
```

The command first requires the current 12-file source set to reproduce the
frozen baseline byte-for-byte. It writes only under the isolated
`tools/layered-maps/workspace/preservation-package/` directory, transforms
all 1,764 authentic ORSC sectors into signed identities and fixed-width native
payloads, reverses every wall-byte swap to prove exact source fidelity, and
converts all base boundaries, scenery, ground items, and every exactly
representable NPC into four level-qualified v3 placement sets. Stable
placement IDs retain source family and ordinal.

The frozen source contains one known NPC anomaly at source index 3376: its
packed start/minimum are on underground plane 3 while its maximum Y `6549` is
outside the four-plane model. Owner review confirmed the symmetric maximum
`3549` as the intended concrete vanilla-map value. Conversion applies that
single repair only after the complete frozen baseline and exact source
index/definition/start/minimum/maximum tuple match. It leaves the source file
unchanged and emits a machine-readable `conversionRepairs` receipt.

The frozen placement files also contain 13 expansion records that are not part
of the revision-64 vanilla definition ranges: five scenery placements (four
inert obelisks and one ore crusher), two NPC placements (Arlen and the Master
tanner), and six cow-hide equipment ground items. Package `0.4.0` excludes
only those exact reviewed source tuples, emits one exclusion receipt per
record, and fails closed if any other placement crosses the vanilla definition
boundaries (boundary `213`, scenery `1189`, NPC `793`, item `1289`). The
ordinary MyWorld source files remain unchanged so their custom features still
work outside the Preservation replacement profile.

The resulting package contains 32,351 vanilla placements from 32,364 frozen
source records, with 13 explicit exclusions, zero unresolved records, and the
one Hobgoblin value repair. It remains
`transitions-pending` and `runtimePromotionApproved=false`. This receipt is
specific to the selected vanilla baseline; it is not a generic policy for
rewriting creator expansions. Transition conversion and complete-world
replacement ownership must finish before private runtime promotion. No
game/export path is modified.

The supplementary transition inventory is generated separately:

```bash
./tools/layered-maps/layered-maps.sh preservation-transitions
```

This command requires the accepted map baseline, losslessly normalizes the 20
explicit `ObjectTelePoints.xml` edges, and inventories every authentic Java
source with transition/location-mutation signals by path, hash, and lexical
call counts. The compact accepted lock is
`baselines/preservation-transition-compatibility-v1.json`; detailed output
stays in the isolated workspace. Scripted quest gates, random offsets,
transports, and unconventional or long-distance topology are deliberately
classified as compatibility-runtime behavior rather than guessed into a
declarative graph. The report is execution provenance supplementary to the
12-file map baseline, not an expansion rewrite policy.

## Native package check

`layered-world-package-v1` declares terrain with explicit
`(worldSpace,level,sectorX,sectorY)` identity:

```bash
./tools/layered-maps/layered-maps.sh package-check \
  --package tools/layered-maps/fixtures/native-package-v1
```

Validation is strict and read-only. It rejects undeclared levels, duplicate
sector identities, unsafe or reused paths, symlinks, changed payload hashes,
unknown fields, and presentation chunk sizes that do not divide the 48-tile
storage page. The fixture deliberately declares levels `0`, `-2`, and `-3`;
the latter proves `-2` is not a format or loader ceiling. Its 24-tile
presentation chunks are independent from 48-tile storage ownership.

The hash-addressed `layered-world-placements-v2` payload gives every NPC,
ground-item, scenery, and boundary placement a stable package-wide placement
ID and an explicit world-space/level-qualified position. NPC roaming coverage,
item positions, and object anchors must be backed by terrain from the same
package. The first fixture owns one radius-2 Man, one five-coin spawn with a
five-second respawn, one Table, one normal Tree, one Fence, and one ordinary
Door. Duplicate IDs or object slots, unknown fields, invalid
amounts/timers/directions, identity disagreement, missing terrain, changed
hashes, and unsafe/reused paths are refused independently by both the tool and
server loaders. The earlier `layered-entity-placements-v1` NPC/item payload
remains readable. At runtime, package-owned scenery and boundaries retain a
generation-qualified placement identity across replacement and delayed
reconstruction. Registration, replacement, and removal update the level-aware
spatial index and exact collision overlay without entering a packed `Region`;
stale-generation callbacks are refused. The Tree exercises the ordinary
Woodcutting Tree-to-stump replacement and delayed Tree restoration path
without package-specific plugin behavior.

`layered-world-placements-v3` retains the same four placement families while
replacing the fixture-oriented NPC radius with exact inclusive minimum/maximum
roaming bounds. The bounds must contain the start, remain within the loader's
4,096-tile per-axis safety limit, and be backed by package terrain. This is the
parity-preserving format for legacy NPC records whose roaming rectangles are
not necessarily square or centered on their start.

The compact `uniform-layered-sector-v1` payload remains a laboratory encoding.
The definitive `rle-layered-sector-v1` payload expands positive runs in
`x-major-y-minor` order to exactly 2,304 independent tile values. It retains
all seven legacy terrain scalars and can represent an entirely non-repeating
page with 2,304 one-tile runs. Both the tool and server reject underfill,
overfill, invalid scalar ranges, changed ordering, and unknown fields.

The full-scale Preservation package uses `raw-layered-sector-v1`: exactly
23,040 bytes per 48-tile sector in the same x-major/y-minor order and the
native vertical-wall-before-horizontal-wall field order. It avoids expanding
1,764 vanilla sectors into large JSON payloads while retaining exact hashes
and full tile fidelity. Conversion from ORSC swaps only the two legacy wall
bytes and must reverse that transformation byte-for-byte before accepting a
generated payload.

Package `0.7.0` keeps the level `-2` RLE page fully runtime-renderable now that
native ownership covers every declared tile. Its western non-default band uses
defined blocking tile, roof, and wall IDs instead of the earlier maximum-value
codec proof that was safe only outside the former bounded runtime room. The
generic format continues to accept its full unsigned storage ranges; runtime
packages must additionally match the definitions available to their target
client and server profile.

The matching server-side reader lives in
`server/src/com/openrsc/server/io/NativeLayeredWorldPackage.java`. It performs
the same containment, identity, declaration, hash, and terrain-payload checks,
then exposes immutable detached sectors by `WorldMapSectorId`. The fixture has
two adjacent level `-2` pages and one same-X/Y level `-3` page. Tests resolve
distinct RLE tile bands and both sides of the 48-tile boundary, substitute
level `-37`, and copy a sector into an unregistered legacy-shaped value for
byte-fidelity checks.

The fifth default-off private runtime gate consumes this source wherever the
exact signed location has package terrain; it no longer depends on the
synthetic gate or its X `440..460`, Y `590..610` room. Matched scene-context
protocol v4 projects a radius-one window of nine explicit 24-tile chunk slots.
Available slots carry all 576 fixed-width terrain tiles; unavailable slots are
explicit void. The client accepts the complete window atomically, retains
protocol-v3 uniform-page decoding for rollback evidence, and does not turn a
same-package chunk shift into a full world-scope reset. Every available
snapshot tile is applied to the active client window; native rendering no
longer clips package terrain to the synthetic fixture rectangle, while
genuinely unavailable slots remain explicit void. Native movement and
persistence preserve world space and signed level across adjacent package
pages and refuse absent package tiles. A saved synthetic record migrates only
when the exact coordinate is package-backed; package removal or coverage loss
safely rebases through the unchanged legacy receipt. The private server
registers the package NPC, item, Table, Tree, Fence, and Door once during world
population rather than creating them from the developer command. The layered
item registry keys full
`WorldLocation`, deduplicates the active spawn, and rejects stale respawn
timers after a world lifecycle reset. Package objects enter the level-aware
spatial index without occupying a packed `Region`; their collision is derived
through the canonical object-footprint planner and composed onto freshly
decoded native terrain by full `WorldLocation`. The static object route is
owner-accepted for visuals, examine actions, blocking, alternate navigation,
reconnect, and duplicate-free visibility. Dynamic door/object replacement and
Tree-to-stump-to-Tree delayed restoration routes are also owner-accepted
through their existing gameplay plugins, generation-fenced placement identity,
level-qualified collision transactions, and exact deep reconnect. The retained
packed `Point` is now a temporary non-negative X/Y carrier rather than native
scope authority; complete packed Region/carrier retirement, cross-package
transitions, and general world loading remain later work. The old synthetic
fixture remains an independent default-off rollback route.

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
::layerparity recover-noop
::layerparity preserve-noop
::layerparity status
::layerparity stop
```

For time-sensitive private routes, `::lp` is an exact alias for
`::layerparity`, so commands such as `::lp mark before-ladder` use the same
checks and capture path. The existing `::tp X Y` alias may be used in place of
`::teleport X Y`.

While ACTIVE, ordinary movement and teleports are captured automatically.
Leave capture active through logout/reconnect if that transition is under
test; `stop` deliberately ends it. Logs are isolated by database ID and
username hash under `server/logs/layered-map-parity/`. They contain packed and
layered positions, world space, level, logical region and terrain-sector keys,
local sector coordinates, transition deltas, and round-trip status. They do
not contain username text, IP addresses, credentials, or tile payloads. New
traces emit `schema/layered-map-parity-event-v60.schema.json`. Each v60 record
retains the complete v38 position, logical-window, interest-delta,
packed-coverage,
logical 48×48 snapshot, current-tile parity, and 3×3 neighborhood evidence.
Start, marker, teleport, and stop records also carry all eight dormant adjacent
tile-mask comparisons: directions and destinations, nullable decision/reason
pairs, required-state counts, and exactness summaries. Tile masks and tile
payloads are never written. Other event types carry explicit nulls instead of
repeating the tile comparisons on every movement. V44 retains v43's bounded
composed scheduler/Region revalidation and adds nullable
`packedRegionEventRecoveryNoOp` evidence. V45 adds a proposal-wide recovery
preflight to that result: total related callbacks, complete and incomplete
recovery counts, incomplete owner-position-hint and exact-spatial counts, and
the first stable incomplete registration/owner/attribution/requirement. A
preflight blocker remains a proposal-wide refusal; a complete callback elsewhere
in the proposal is never used to imply readiness. V46 additionally detaches
authored NPC event-owner identity and correlates
proposal-related owner-position callbacks with a fresh bounded active-NPC
census. It reports exact, missing, stale, duplicate, unrecognized, and
position-drifted owner cases separately. Production always reports owner
preservation as unproved and continuity eligibility as zero; the census is
diagnostic evidence, not permission to retain an NPC or bypass standalone
callback recovery. V47 derives one exact requirement per matched authored NPC,
includes proposal-related and supporting callback registrations, and reports
the nested scheduler, event-timing, World-registration, owner-reference, NPC
lifecycle, and Region-quiescence boundary result. A scope-ready result is
explicitly point-in-time and does not establish a durable preservation fact.
V48 adds nullable `packedRegionNpcOwnerPreservationNoOp` evidence. Only
`::layerparity preserve-noop` (or `::lp preserve-noop`) populates it. The
current expected result is `SOURCE_LIFECYCLE_UNAVAILABLE`: the exact owner
scope was entered, but no packed source was removed or reconstructed, no
preserved consumer ran, and no Region, arrival, or visibility authority was
enabled. V49 retains that refusal and adds its nullable
`sourceAbsencePreflight`. When the real source boundary is entered, the
preflight reports exact per-source and aggregate counts for players, NPC
membership, authored and dynamic objects, ground items, collision products,
tile storage, and every typed absence/reload blocker. It is a read-only
point-in-time inventory: every Region/mirror/cache mutation, source absence,
reconstruction, arrival-gate, and lifecycle-authority flag remains false.
V50 additionally binds that same exact source observation to the immutable
final-live authored recipe. Its nullable `sourceReloadRecipe` distinguishes
declared authored sources from exact empty replays and reports final-live,
manifest, supersession, dependency-reference, and unresolved runtime-family
counts without serializing entity or archive state. The recipe is detached and
non-executable: no Region container is created, no authored replay or collision
rebuild runs, and all registry, mirror, cache, arrival, visibility, and
lifecycle-authority facts remain false.
V51 adds nullable `sourceTerrainVerification` evidence to that same explicit
private preservation action. Under the already-held source lifecycle boundary,
each selected resident source is reduced to static terrain, applied to one
disposable unregistered Region, exactly verified, and discarded. The log keeps
only bounded per-source counts and a SHA-256 terrain fingerprint—never the
2,304 tile inputs or a Region handle. Every runtime-source terrain application,
source absence/reconstruction, authored replay, dynamic collision rebuild,
active-family preservation, registry, mirror, cache, arrival, visibility, and
lifecycle-authority fact remains false.
V52 adds nullable `sourceAuthoredCollisionVerification` evidence to the same
explicit private preservation action. Each selected source independently
verifies static terrain and final-live authored scenery membership on
disposable unregistered Regions, reads the active object/boundary definition
scalars, derives—but does not apply—the exact legacy register-collision
footprints, and discards every detailed intermediate. The JSON retains only
bounded aggregate/per-source counts and terrain, replay, definition-capture,
collision-plan, and whole-batch SHA-256 fingerprints. It contains no tile,
entity, definition-table, or collision-contribution payload and grants no
source, collision, registry, arrival, visibility, or lifecycle authority.
V53 adds nullable `sourceAuthoredCollisionApplicationVerification` evidence
only to that explicit private preservation action. It applies the already
verified footprints through the canonical ordered-boundary executor solely on
disposable unregistered Region unions, verifies every resulting collision
counter, and discards the Regions. Only bounded source/application/Region/tile
counts and the applied-state fingerprints are serialized. Runtime collision,
collision-registration provenance, retained handles, runtime-source mutation,
registry, arrival, visibility, and lifecycle authority remain false.
V54 adds nullable `sourceAuthoredStateVerification` evidence only to the same
explicit private action. For every exact selected source, it applies static
terrain, exact final-live authored scenery membership, and canonical collision
to one shared disposable unregistered Region union. It verifies and discards
that combined state, retaining only bounded aggregate/per-source counts and six
stage/final SHA-256 fingerprints. Runtime collision registration, runtime
source mutation, retained Region or entity handles, registry, mirror, cache,
arrival, visibility, and lifecycle authority remain false.
V55 adds nullable `sourceTransactionalAuthoredStateVerification` evidence only
to that explicit private action. It reconstructs every selected source through
the canonical atomic object/collision transaction inside disposable
unregistered Region unions and verifies exact per-object collision-registration
provenance. Only bounded transaction, boundary, registration, Region/tile, and
collision-family counts plus seven stage/final SHA-256 fingerprints survive.
Reported cache invalidations are disposable counters; runtime collision,
runtime cache invalidation, source mutation, retained handles, registry,
mirror, arrival, visibility, and lifecycle authority remain false.
V56 adds nullable `sourceRuntimeAuthoredObjectObservation` and
`sourceRuntimeAuthoredObjectBaselineComparison` evidence only to that explicit
private action. Under the same exact lifecycle boundary, the runtime source
census separates exact final-live objects, authored transients, missing,
duplicate, stale, non-object, and unknown identities, identity-less dynamics,
and missing or constructor-mismatched registration receipts. Only a complete
stable authored sequence is compared with V55's disposable transactional
registration fingerprint. Non-final state remains unresolved pending scheduler
correlation; shared live collision tiles are never compared. Only bounded
counts and fingerprints survive, and every mutation, cache, registry, mirror,
arrival, visibility, and lifecycle-authority fact remains false.
V57 adds nullable `sourceAuthoredObjectDetachmentVerification` evidence only
to that explicit private action. After the V56 census proves an exact
final-live runtime baseline, every selected source is independently rebuilt
and detached in reverse stable authored order inside disposable unregistered
Region unions. The JSON retains only bounded transaction, boundary,
registration, Region/tile counts and fingerprints before and after
detachment. Runtime sources and collision remain untouched; no handles survive
and source absence, scheduler correlation, active-family preservation,
registry, mirror, cache, arrival, visibility, and lifecycle authority remain
false.
V58 adds nullable `sourceAuthoredDetachmentSchedulerCorrelation` evidence only
to that explicit private action. It aligns the exact detachment definition with
the already-captured scheduler inventory and NPC-owner requirement set,
separating exact NPC callback fences and fully authored scenery restorations
from candidate NPC, player-owned, incomplete-restoration, and unattributed
blockers. The JSON retains aggregate and per-source counts plus fingerprints,
not a second copy of callback details. Correlation is point-in-time and
non-executable: no scheduler boundary, event cancellation/reschedule,
preservation, source mutation, arrival, visibility, or lifecycle authority is
enabled.
V59 adds nullable `sourceSchedulerBlockerFamilyInventory` evidence only to
that explicit private action. It groups the exact V58 blocker set by stable
runtime/family/direct-supertype identity, owner kind, attribution,
restoration kind, and blocker outcome. Each first-observation-ordered family
retains bounded event/reference counts, scheduler ordinal and registration
ranges, timing/run ranges, and a deterministic fingerprint. Type identity does
not reclassify an event: unattributed and incomplete callbacks remain
blockers, and cancellation, rescheduling, preservation, runtime mutation,
arrival, visibility, and lifecycle authority remain false.
V60 refines those same blocker families with a detached execution-context
identity. Ordinary callbacks report `NONE`; plugin ticks report only their
bounded code entry-point name and whether they are bound to a walk-to action.
This splits generic `PluginTickEvent` blockers by implementation context
without retaining plugin tasks, script data, actions, callbacks, or runtime
handles. Context identity is diagnostic only: every plugin tick remains a
blocker and no attribution, scheduling, preservation, mutation, arrival,
visibility, or lifecycle authority changes.
The owner correlation accompanies proposal-scoped event
inventories; only the explicit `::layerparity recover-noop` action may populate
the separate recovery result, while ordinary movement, snapshots, and markers
emit null there. The recovery result reports stable preparation,
lifecycle, candidate, future-snapshot, runtime-verification, mutation, and
terminal-consumption facts. It is verification-only: missing current state
refuses rather than being restored, and overdue callbacks are never consumed.
V43 records a bounded composed scheduler/Region revalidation for each
restoration-capable event: outer-fence outcome, lifecycle-version stability,
exact Region-boundary
target facts, target decision, and dormant contract result. These facts are
read-only and point-in-time; all mutation, commit-token, executable-restoration,
arrival-gate, and lifecycle-authority flags remain false. The v1-v58 schemas
remain alongside it—including
`schema/layered-map-parity-event-v58.schema.json`,
`schema/layered-map-parity-event-v57.schema.json`,
`schema/layered-map-parity-event-v56.schema.json`,
`schema/layered-map-parity-event-v51.schema.json`,
`schema/layered-map-parity-event-v50.schema.json`,
`schema/layered-map-parity-event-v49.schema.json`,
`schema/layered-map-parity-event-v48.schema.json`,
`schema/layered-map-parity-event-v47.schema.json`,
`schema/layered-map-parity-event-v46.schema.json`,
`schema/layered-map-parity-event-v45.schema.json`,
`schema/layered-map-parity-event-v44.schema.json`,
`schema/layered-map-parity-event-v43.schema.json`,
`schema/layered-map-parity-event-v42.schema.json`,
`schema/layered-map-parity-event-v41.schema.json`,
`schema/layered-map-parity-event-v40.schema.json`,
`schema/layered-map-parity-event-v39.schema.json`,
`schema/layered-map-parity-event-v38.schema.json`,
`schema/layered-map-parity-event-v37.schema.json`,
`schema/layered-map-parity-event-v36.schema.json`,
`schema/layered-map-parity-event-v35.schema.json`,
`schema/layered-map-parity-event-v34.schema.json`,
`schema/layered-map-parity-event-v33.schema.json`,
`schema/layered-map-parity-event-v32.schema.json`,
`schema/layered-map-parity-event-v31.schema.json`,
`schema/layered-map-parity-event-v30.schema.json`,
`schema/layered-map-parity-event-v29.schema.json`,
`schema/layered-map-parity-event-v18.schema.json`,
`schema/layered-map-parity-event-v17.schema.json`,
`schema/layered-map-parity-event-v16.schema.json`,
`schema/layered-map-parity-event-v15.schema.json` and
`schema/layered-map-parity-event-v14.schema.json`—so already-captured logs keep
explicit readable contracts. The v19-v50 schemas likewise remain immutable
contracts for earlier records.
When a bounded refinement proposal is available, v30 records a
same-order, point-in-time preservation-burden inventory. Its five explicit
families distinguish player-session blockers, dynamic objects, ground items,
derived collision products, and Region-owned events; partial or unavailable
evidence stays visible instead of being treated as zero. All preservation,
reload, registry, arrival-gate, teardown, candidate-mutation, and lifecycle-
authority flags remain false. Records without a proposal use an explicit null.
V31 additionally records privacy-safe constructor-state evidence for every
identity-less dynamic object in that proposal order. Current/permanent IDs,
packed coordinates, direction/type, owner presence, and opaque runtime-
attribute counts are visible, but owner text and attribute values are not.
Because event ownership remains uncaptured, standalone restoration stays false
and no object, reload, registry, teardown, or lifecycle authority is granted.
V32 records the bounded global scheduler-affinity snapshot that
corresponds to the same exact refinement proposal. Exact spatial effects,
Mob-owner position hints, explicit non-spatial global events, and unattributed
callbacks remain distinct. Records expose only scheduler ordinals, owner and
affinity kinds, running/countdown/execution counters, and packed coordinates;
descriptors, UUIDs, callback classes, closure state, and owner identities are
never written. V33 additionally records the explicit detached callback inputs
for exact delayed scenery spawn/removal events. It publishes current/permanent
IDs, coordinates, direction/type, owner presence (never owner text), opaque
runtime-attribute counts, authored placement identity, target-binding evidence,
and whether the known callback payload is complete. Scheduler identity, target
lookup, standalone restoration, cancellation, rescheduling, preservation, and
all lifecycle-authority flags remain false. Events without the narrow explicit
contract retain a null restoration state and remain visible in the same bounded
snapshot. V34 additionally publishes the positive process-local registration
sequence for every bounded event and explicit aggregate completeness. This
lets repeated records correlate one accepted registration without exposing the
event UUID, scheduler key, descriptor, class, callback, or owner identity.
Scheduler-instance identity remains false, so sequences must never be compared
across server restarts; cancellation, rescheduling, replay, restoration, and
all lifecycle-authority flags remain false.
V35 adds one opaque scheduler-instance identity and marks that narrow scope as
captured. Registration sequences are comparable only when this identity also
matches. A private server restart creates a different identity, making the
scope boundary explicit even if numeric registration sequences restart at low
values. The token is detached diagnostic text, not an event UUID, credential,
persistent server ID, scheduler handle, replay key, or lifecycle authority.
Full scheduler identity, callback state, cancellation, rescheduling, replay,
restoration, preservation, and every lifecycle-authority flag remain false.
V36 additionally publishes the detached execution contract already declared
for known delayed scenery spawn/removal callbacks. Those restoration records
report `ONE_SHOT` execution with `CONTINUE_SERVER_TICKS` progression, plus
aggregate captured/complete counts. Existing running, countdown, and execution
counters remain point-in-time observations: atomic timing is explicitly false
with zero captured events. These fields do not identify, cancel, reschedule,
invoke, or replay a callback, and standalone restoration and every scheduler/
lifecycle-authority flag remain false.
V37 publishes the atomic timing provenance already detached at the scheduler
boundary. Aggregate counts report how many known restoration records carry a
single-lock running/countdown/execution tuple and whether every available
restoration record is covered; each event and known restoration record also
states its own timing status. Unknown callbacks remain visible but explicitly
non-atomic. The shared observation tick labels the bounded capture—it is not a
due tick, replay cursor, or rescheduling instruction. Callback handles,
invocation, cancellation, replay, standalone restoration, and every scheduler/
lifecycle-authority flag remain absent or false.
V38 publishes the dormant target and arrival requirements already detached in
the bounded inventory. Aggregate counts keep known requirement capture,
satisfied authored target binding, and pre-visibility ordering separate.
Authored spawn records require an `AUTHORED_DESTINATION_SLOT`; authored removal
records require an `AUTHORED_EXISTING_ENTITY`; both refuse a mismatch or
ambiguity. Identity-less records explicitly report missing authored evidence
and incomplete binding. Every known record requires reconciliation before its
first visibility snapshot, but this remains an ordering statement only: target
lookup, arrival gating, callback invocation, replay, standalone restoration,
and every scheduler/lifecycle-authority flag remain absent or false.
V39 publishes only the subsequently detached generation and idempotency rules.
Aggregate counts distinguish complete rule capture from authored targets whose
generation actually matches the enclosing reconstruction proposal. Each known
spawn requires authored scenery present and permits a mutation only at an empty
destination slot; each known removal requires authored scenery absent and
permits a mutation only for the exact authored entity. Both state that an
already-satisfied desired state is a no-op success. These are decision-table
descriptions only: target lookup, target-state inspection, achieved desired
state, mutation, callback execution, replay, arrival gating, and every
scheduler/lifecycle-authority flag remain absent or false.
V40 corrects the spawn mutation prerequisite after auditing the existing
harvest/replacement path. `replaceGameObject` transfers the authored placement
identity to a stump or other transient replacement, so a pending authored spawn
may validly find either an empty destination or one exact-identity authored
transient (`DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT`). A different
identity, an identity-less occupant, or ambiguous
occupancy must still refuse. V40 remains descriptive: it performs no lookup,
target-state inspection, mutation, callback execution, replay, arrival gate, or
lifecycle operation. V39 remains the immutable contract for prior captures.
V41 adds the bounded read-only exact-slot observation for restoration records.
Each target is correlated by scheduler-instance scope, event snapshot ordinal,
registration sequence, coordinate, proposal generation, and observation ticks.
Counts distinguish unavailable, empty, exact restoration scenery, exact
authored transient, mismatched/identity-less, and ambiguous occupancy, then
publish the inert classifier outcome and reason. Target observation is not
atomic with the earlier event inventory and explicitly carries no entity
handle, achieved-state claim, commit token, mutation, executable restoration,
arrival gate, or lifecycle authority. Owner text and entity details are never
serialized. V40 remains the immutable contract for prior captures.
V42 additionally reports how many available targets were classified while the
real Region object monitor was held, whether every available target has that
boundary evidence, and the corresponding per-target fact. A positive value
means comparison occurred inside the monitor; it does not survive monitor
release as mutation authority. Records explicitly remain non-atomic with the
event inventory and with any later mutation, and runtime revalidation,
achieved-state claims, commit tokens, executable restoration, arrival gating,
and lifecycle authority remain false. V41 remains the immutable contract for
prior captures.
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
v20 additionally projects the inert final-live reconstruction recipe onto the
exact packed retirement-safety source selection. It reports whole-recipe and
selected-source counts, each selected source's conservative dependency closure,
and a deterministic union of required packed sources with selected/authored
membership and reference counts. Separate hard bounds cover selected sources
and requirements; overflow refuses the observation instead of truncating it
into a false closed result. The payload explicitly remains identity metadata
only, with no entity registry or lifecycle authority.
v21 adds a bounded fixed-point cohort analysis. Exact safety sources are seeds;
dependency coordinates with final-live authored content recursively join the
cohort, while coordinates without such content remain explicit external support
requirements. Cohort roles, expansion rounds, final-live counts, conservative
reach, support perimeter, and exact requirement references remain detached
diagnostic evidence. The payload cannot acquire, load, retain, reconstruct, or
retire a source and explicitly has no entity registry or lifecycle authority.
v22 attributes the exact detached cohort without changing it. Typed aggregates
separate construction and dependency kinds; sorted owner-to-requirement edges
identify expansion-frontier and external-support relationships; and compact
bridge records retain primitive authored identity and conservative envelope
metadata for cross-source placements. Edge and bridge lists each refuse beyond
8,192 entries. The observer passes the exact v21 cohort object into attribution,
and the payload remains identity-only evidence with no entity registry or
lifecycle authority.
v23 compares the forward cohort with bounded whole-recipe incoming-edge and
component topology. v24 separates source-local replay, outbound spatial
support, and incoming-owner evidence. v25 records detached active-NPC authored
ownership and current residency, while v26 assesses point-in-time containment
without treating it as readiness. v27 projects exact missing sources for
recognized active-NPC boundary crossings and retains non-expandable blockers.
v28 combines exact safety seeds, authored expansion, active-NPC requirements,
support coordinates, and hard blockers into one inert refinement proposal.
v29 retains only the latest immutable proposal inside the private trace and
reassesses it on a strictly newer atomic observation. It distinguishes
`DEFERRED_NOT_NEWER`, `STABLE`, `EXPANDED`, `HARD_BLOCKED`, and
`EXPANDED_AND_HARD_BLOCKED`, includes the full diagnostic-only fresh safety and
next proposal, and clears state only after a stable unblocked observation.
Diagnostic selections explicitly have no retirement-readiness evidence,
commit token, entity registry, arrival gate, load request, or lifecycle
authority.

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
