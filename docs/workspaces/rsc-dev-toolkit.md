# Core-managed RSC Development Toolkit

Owner-approved organizational and architectural reset, October 9, 2026.

The Core manager also manages `/home/justin/rsc-dev-toolkit`. This is a fresh
standalone Java development-tool repository, not a new independent manager AI
and not an embedded Spoiled Milk tool. Its initial product is an offline map
editor; reviewed loader upgrades and broader tooling follow separate milestones.

## Ownership

- Integration checkout: `/home/justin/rsc-dev-toolkit`, branch `main`.
- Initial dedicated worker: `/home/justin/rsc-dev-toolkit-ai-1`.
- Additional toolkit workers may be created only for bounded independent tasks.
- Core game changes use Core workers; toolkit changes use toolkit workers.
- Read `/home/justin/rsc-dev-toolkit/AGENTS.md` and `docs/README.md` before work.
- Do not merge toolkit Git history into Core or vice versa.

The previous Editor/runtime managers and their workers are retired from new
assignments. Preserve their branches and project data. Do not delete folders,
retire live game loaders, undo map imports or change the public server as part
of this reset. Historical instructions are not the new architecture's rules.

## Lifecycle

Run repository wrappers from the toolkit checkout, never from Core:

```bash
git status --short --branch
./scripts/ai-manager.sh status
./scripts/ai-workspace.sh start ai-1 docs/salvage-audit
```

Workers checkpoint and hand off exact commits with the same guarded workflow
used by the Core-managed Sprite Baker. The wrappers borrow workflow mechanics,
not editor code or gameplay dependencies. Review tests before merge; publish
toolkit `main` before recycling. The initial `origin` is a local bare backup at
`/home/justin/.local/share/rsc-dev-toolkit/git-backup.git`, not a public release
or off-machine backup. External publication requires a deliberate later choice.

## First assignment

Read-only salvage audit and a viewport integration experiment plan. Inventory
reusable formats/decoders/tests versus game-bound code. Do not copy legacy code,
assets or rule trees; do not implement a runtime upgrade or start a game server.
Return evidence, provenance questions and a small offline prototype proposal.

## Current loader acceptance gate

Owner direction, October 11, 2026: the initial Linux classic editor passed its
packaged end-to-end private-game test, including discovered content. The next
assignment is cleaning Spoiled Milk's maintained map loader, not extending the
editor first.

Every loader change must work in Spoiled Milk's maintained server and matching
client. Synthetic tests and editor fixtures support this acceptance but cannot
replace it. Use an isolated private Spoiled Milk copy with disposable accounts
to verify startup, map/layer transitions, collision, placements, custom visuals
and gameplay, save/reload behavior and relevant failure/recovery paths. Preserve
original maps, custom content and account state; do not operate the public
server as part of this gate.

Before editor or general upgrader development resumes, the Core manager must
review the cleaned loader and record evidence that Spoiled Milk is operational
and no unresolved major correctness, preservation, authority or migration issues
remain. Review must be thorough and proportionate, not an open-ended cosmetic
rewrite. Minor nonblocking debt can remain with a documented reason. The owner
delegates this technical readiness judgment to Core; it is not a claim that no
future bug is possible or authorization for public deployment.

The implementation order and test checklist are maintained in
`/home/justin/rsc-dev-toolkit/docs/MODERN-LOADER-CLEANUP.md`; toolkit
`docs/README.md` and `docs/ROADMAP.md` must retain this Spoiled Milk-first gate.

### First loader slice: startup authority (private-game acceptance passed)

`ServerConfiguration` resolves an immutable `MapSourceActivationPlan` once during
startup and applies it to the existing compatibility fields. The report includes
representation, additive/replacement ownership, selected package/manifest,
profile, effective gates and input provenance. It does not change terrain codecs,
wire bytes, placements, collision, art, population or default gate values.

Selection rules:

- Existing per-field precedence stays system property, environment, configuration,
  then default. Nonempty launch values are explicit; blank values remain absent.
- An explicit launch native-package switch, package path, manifest hash or runtime
  profile selects launch/configuration values rather than installed-primary values.
  This retains an external hosted package path and makes explicit legacy rollback
  effective. An explicit path with disabled native authority is an error.
- Without explicit launch source selection, a verified layered primary remains
  preferred over configuration-file defaults. Configuration file settings cannot
  distinguish deliberate selection from old installed pins; this compatibility
  rule is intentional for existing imports. An explicitly disabled launch gate
  conflicting with automatically selected installed authority fails startup.
- Absent primary, inert packed primary and verified layered primary are distinct
  discovery outcomes. Malformed, symlinked/nonregular, mismatched paired packages
  or bad fingerprints fail startup, **even during launch override/rollback**.
  No corrupt selected source silently falls back to classic maps.
- `world-builder-installed` is an alias of `spoiled-milk-editor-installed`, with
  exactly the same strict validator, configured-manifest requirement and replacement
  ownership. It is not a new generic or release-pinned policy.

The matching client caches one pre-login bootstrap decision. Valid explicit
adaptive selection outranks a valid installed default (including a differing
identity); its report says the installed profile was not selected. Installed
profile parsing still fails closed if malformed. Ordinary installed and classic
bootstrap behavior remains unchanged. The report is available through
`WorldBuilderTerrainBootstrap.report()`.

Executable checks: `scripts/build-server.sh`, `scripts/build-client.sh`,
`tests/myworld/test-map-source-activation.py` (source-built JARs required),
`test-layered-native-server-source.py` and `test-native-terrain-atomic-activation.py`.
The native-source suite now uses maintained Core rather than a retired
class-shadowing runtime archive. Its historical exact-release rejection uses
`spoiled-milk-replacement`; installed validation intentionally permits added levels.
Custom combat characterization is supplementary, not gameplay acceptance.

The corrected first slice passed owner inspection in the isolated source-built
Spoiled Milk game described below. Executable rejection/recovery tests supplement
that inspection; they do not stand in for the private game. This is not public
deployment or overall loader-cleanup closeout. Further slices retain the same
Spoiled Milk-first acceptance gate.

Deferred boundaries: generic structural policy versus named product fixtures;
authoritative terrain/collision facade and compatibility projection isolation;
capability/version matrix, widening and instances; build-overlay consolidation.
Keep existing safety validation until those bounded slices provide replacements.

### Blocking private-review terrain recovery fix

The first private review exposed a cache/activation failure near Falador. Both
the original baseline and first-slice client reproduced it through the real
decoder and `PacketHandler`: a missing context reference followed by baseline
packets failed with “Scene baseline does not match layered scene context”.

The server already discards canonical residency transactions for predicted
symmetric visual/structure stages (protocol 4/5). The client decoder had committed
them, evicting confirmed sectors from its 64-entry cache without matching server
eviction. Predicted snapshots now retain their private payloads but do not change
canonical cache contents or LRU order. Confirmed context/halo behavior is unchanged.

Recovery sends one request per pending refresh, clears canonical residency once,
cancels speculative preparation and retains the last accepted context/scene.
Only a fully validated, self-contained native context decoded into an empty
temporary cache can replace that cache and finish recovery. Reconnect resets it.
No server packet layout, content, account or public-server change is involved.

Missing-reference contexts have their headers validated before their exact
sequence and world/level identity enter a bounded (16-entry) quarantine. Tagged
baseline/movement tails for those exact sequences retain normal schema, payload
length, page and record validation but cannot publish. Unknown context sequences,
wrong scope/projection, unsupported protocols and malformed packets still fail.
Legacy scene-only deltas have no scope tags: after a positively identified context
gap, they are counted/discarded until verified refresh. Their framing is checked,
but their body cannot be interpreted using the retained old scope; this is not
semantic validation of those discarded deltas. Non-scene traffic is unaffected.
The existing client compatibility projection is global-world-space-only; instance
presentation remains deferred, not newly restricted by recovery header checks.

`tests/myworld/test-native-terrain-recovery.py` replays the real decoder across
the route `(2,13)` through `(6,11)`, back to `(2,13)` and toward `(4,12)`, exercising
full-cache pressure, confirmed halos and repeated private predictions. Its real
packet-handler replay checks repeated missing stages/contexts, deduplicated outgoing
resync, known tails versus unknown/malformed packets, scope/projection rejection,
verified refresh readiness, retained atomic cover and reconnect cleanup. Only the
renderer boundary is replaced with a disposable no-window double. Set
`MAP_RECOVERY_CLIENT_JAR` to a frozen pre-fix client to independently reproduce
failure. The corrected matching client has now passed private graphical review,
with the exact acceptance scope recorded below.

Baseline protocol validation enumerates supported schemas independently of
terrain/context versions: atomic fences use schema 7, while presentation pages
use schema 8. The fence constant is **not** a maximum supported version. The
recovery replay now generates baseline bytes using maintained server
`PayloadCustomGenerator`/`SceneBaselineStruct`, dispatching schema-7 fences and
schema-8 inner/outer scenery and wall pages through the actual client handler
for ordinary login, failed-context quarantine and verified recovery. Unsupported
schema 9, truncated schema-8 pages and schema-8 misuse of the schema-7-only fence
remain rejected. The earlier recovery fix introduced a login regression by
mistaking the schema-7 fence constant for the highest supported schema. This
was corrected in `cba549a8a926a070ed1c99c4f7f51408b8b879dc`, and the corrected
client passed the subsequent graphical owner retest.

### First-slice private-game acceptance evidence

Accepted implementation source: `cba549a8a926a070ed1c99c4f7f51408b8b879dc`
on `refactor/map-source-activation`, based on published Core
`d85e643987f452658ea09da91175cfccaa9ccbd9`. Documentation-only follow-up does
not change those runtime sources. The manager independently source-built the
accepted private client; its SHA-256 is
`74f3918d5d5e170a50016bf447cd894f9ac0a77d57c47afdb30d356ebf91e00f`.
Build timestamps/platform asset packaging can produce a different JAR hash from
the worker artifact; the accepted game artifact and source identity are pinned
separately, not inferred from matching archive filenames.

The isolated candidate is
`/home/justin/.local/share/spoiled-milk-loader-candidate-VhrrvGLi/game`, using
loopback port `43628`. Its server remained unchanged during the client recovery
and schema fixes. Original accounts, maps and the public deployment were not
changed by this task. The manager verified paired package/content inventories:
package fingerprint
`f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75`, manifest
`f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29`, with
1,789 files per role and the same authored placements/effective definitions as
the isolated baseline. Later unmerged art/content branches were not silently
adopted or claimed as part of that baseline.

The manager verified actual private login and region-transition atomic fences
1 and 2. The owner then reported custom areas and busy-area teleports working,
no crashes, no missing visuals, level transitions working, and object interactions
behaving correctly. This establishes the first-slice graphical acceptance in
the selected installed-native mode, not proof of every future area or mode.
Alternate selection/rollback, malformed evidence and forced cache-loss/recovery
were exercised by executable characterization/replay, not by corrupting the
owner's running private game.

Two failures remain part of the evidence, not erased by the successful retest:

- The original Falador cache/reference crash reproduced against both the frozen
  pre-cleanup baseline and first-slice client. Predicted cache transactions and
  exact failed-context recovery were corrected and replayed through real decoding
  and packet dispatch.
- The subsequent schema-8 login rejection was introduced by this work's incorrect
  upper-bound guard. Server-generated baseline replay reproduced it, then verified
  supported schema-7 fences and schema-8 pages, including the ordinary login path.

Retained private crash/retest evidence lives outside Git; no databases or account
backups belong in this documentation commit. Automated checks include maintained
server/client builds, activation (10), recovery (2), native source/wire (14),
atomic activation (4), residency (4), readiness (2), prediction (5), symmetric
residency (4) and stage paging (2) tests. The manager independently reran activation
and recovery checks. Existing unrelated fixture failures (broad combat's invalid
native-terrain movement coordinates and the deferred-scenery source-substring
assertion) are not concealed or treated as failures introduced by this slice.

The next meaningful boundary is **generic structural runtime validation versus
named product/release fixture policy**. Characterize the strict installed checks,
move historical package identity/count/level assertions into explicit distribution
validation, and retain supported-format, ownership, placement completeness and
effective-definition safety checks before world mutation. Preserve existing
reviewed manifests and release pinning. This boundary is now implemented in the
focused worker slice below; private startup acceptance remains a separate gate.
No widening, instances, editor/upgrader work or full-loader completion is implied.

### Second slice: generic validation and whole-catalog preflight

Ordinary native operation defaults to `native-additive`: legacy terrain and
population remain active, with the existing authority gates still required when
native packages are enabled. `native-replacement` skips legacy terrain/population
and requires a configured manifest SHA-256 plus exactly one explicit placement
set for every declared level. Empty levels use an explicit empty supported set.
Additive mode permits partial placement coverage; an optional configured manifest
hash, when supplied, is verified rather than ignored.

The existing `world-builder-installed` and `spoiled-milk-editor-installed`
spellings select generic, manifest-bound replacement operation. This deliberately
removes product branding, fixed base-level membership and historical counts from
ordinary installed validation. Installed descriptor/path, paired inventory,
content identity and activation checks are unchanged. Existing named fixture,
Preservation, Spoiled Milk pinned release, draft and export profiles retain their
historical policy. `spoiled-milk-installed-review` explicitly selects the old
installed product-shape checks; its retained diagnostic wording uses the old name.

Generic validation is not unrestricted capability: the current matching client
requires global static world space and 24-tile presentation chunks. Supported
encodings, finite parser/catalog budgets, payload hashes, overlap/ownership and
placement/roam terrain checks remain enforced. Signed authored levels are not
restricted to the six Spoiled Milk fixture levels. Effective custom definition
IDs are not restricted to vanilla distribution ceilings. Actual maintained wire
identities are enforced: NPC definitions fit 10 bits (0–1023); ground-item IDs
fit 0–32511 so both addition and removal avoid bit-15 removal and first-byte-255
region-clear controls; scenery/boundary IDs fit 0–65279 excluding removal ID
60000. These are encoder/decoder control boundaries, not product content policy.
Existing packet widths, compatibility carriers and collision bounds are not widened.

Before any native entity is registered, the complete catalog is checked against
effective definitions, configured item restrictions and authored owner terrain.
Scenery/boundary collision and NPC-blocking footprints are checked with the
existing pure scalar planner: no gameplay entity is constructed and no collision
or registration state is mutated during preflight. The existing 1008-by-4032
object collision bounds and footprint resource budgets remain explicit refusals.
Per-object registration guards remain for dynamic staleness. The catalog's entire
NPC count must fit the currently available registration slots before the first
native NPC is added. EntityList's zero index is usable: the current 4000-entry
NPC list has 4000 total slots, not 3999. Available capacity is read from its
occupied count and free-index pool without reserving or consuming slots. World
startup preflights before legacy population and again afterward, accounting for
existing additive NPCs. This does not impose permanent reservations on later
dynamic NPCs or promise transaction rollback of concurrent gameplay exhaustion.

Failure-first characterization against the pre-change maintained server showed
a parsed independent package at level 42 rejected solely by installed branding,
and a later missing item definition leaving one earlier NPC registered. The new
focused tests exercise arbitrary signed levels/custom IDs, explicit product
refusals, additive/replacement completeness, manifest/payload mismatches,
unsupported instance-template capability, terrain-edge and collision-bound
footprints, all effective definition families, and configured item restrictions.
The added capacity counterexample against the first implementation showed 3999
existing NPCs plus two authored NPCs failing after the first raised the count to
4000; wire-ID counterexamples were also accepted before the guards. Both now
refuse before mutation, with legal edge IDs retained. The focused suite has 16
tests. Independent manager source-built private startup of the first implementation
loaded all 33,545 unchanged placements (3803 NPCs, 879 items, 27,892 scenery and
971 boundaries), 877 NPC definitions and 3400 item definitions without errors.
Final server acceptance was independently repeated from exact implementation
commit `03314d292d53b6bb9650da1b332f288d9529101a`, frozen and source-built in the
manager's isolated fixture. The maintained server JAR there had SHA-256
`a86e191b4e4da751f2a8a04989a3bfb80aed105ceae220a27d801695ed02612d`.
Whole-catalog preflight and real population again preserved all 33,545 placements
and the same 877/3400 effective NPC/item definition counts. Full private server
startup on loopback port 43629 completed in 3386 ms without exceptions, using
empty seed accounts and no copied player accounts. That isolated process was
stopped after its exact directory and absence of connections were verified;
the owner's existing private sessions and public server were untouched.

The manager independently reran all 16 focused validation tests successfully.
The same current capacity/wire counterexamples failed against the frozen
pre-guard `8a12b072a` JAR (partial 3999-to-4000 population and unsupported wire
ID acceptance), then passed against the corrected implementation. Evidence is
retained outside Git under
`/home/justin/.local/share/spoiled-milk-runtime-validation-emQrFR47/`, including
`server-startup-final-review.log` and `full-population-final-review.log`.

This is independent maintained-server startup/population acceptance, not a new
graphical owner review. The earlier owner acceptance covers the unchanged client
visual/gameplay behavior recorded for the first slice. Matching-client and broader
integration checks remain the manager's collection gate; no whole-loader or
future-instance completion is claimed.

The next boundary remains unification of terrain access/residency and truthful
capability exposure, after this slice's acceptance. Instances, coordinate/ID
widening, new schemas and editor/upgrader implementation remain deferred.

### Third slice: authoritative source facade (private server acceptance passed)

The activated `refactor/map-authority-facade` worker starts from published
`df4954b0108e69d17a934dc069ed107373ca3fb0`. Its bounded inventory found these
maintained boundaries; they are not interchangeable loaders:

- `RegionManager.getTile(WorldLocation)` selects exact native ownership, the
  explicit synthetic fixture, or exact legacy packed projection. Signed level
  and world identity must survive this selection.
- `getTile(int,int)` and `getTile(Point)` directly access the packed Region
  backend. They cannot simply infer a native level from their temporary carrier.
- `nativeLayeredTile` converts raw fields to the established server TileValue,
  derives immutable terrain collision from owner-local positive-axis neighbors,
  then applies actual registered-object collision. Client presentation and
  readiness caches are not part of this authority.
- `hasNativeLayeredTerrain`/`findNativeLayeredWorldPackage` currently repeat
  immutable catalog versus explicitly enabled authoring-draft resolution.
  Authoring edits are supplied by WorldEditorSessionManager; ordinary gameplay
  must not acquire an implicit authoring dependency.
- `requireLegacyTerrainProjection` performs exact logical-address validation.
  Nonrepresentable logical tiles refuse; empty packed Regions are blocked by
  default, not automatically authored or walkable terrain.
- `populateNativeLayeredPlacements` consumes the immutable catalog; runtime
  relookup uses registered identity and spatial membership. WorldPopulator's
  legacy base suppression and RegionManager's legacy archive suppression are
  deliberate replacement semantics and must remain aligned.

The approved extraction implements `RuntimeMapAuthority`, bound to the immutable
resolved activation plan and validated catalog. Typed terrain/owner lookup,
authored package iteration and legacy archive/population policy use this single
entry point. `LegacyTerrainProjectionAdapter` contains the existing exact packed
conversion; `NativeTerrainTileAdapter` contains the existing server field casts
and static collision derivation. Actual registered-object collision is applied
after static terrain collision, not inferred from rendering or speculative data.
Packed integer/Point APIs remain the compatibility backend and never infer a
signed native level. Transition validation, registration/spatial indexes, codecs,
readiness, prediction and client wire/presentation are not rewritten.

The retained authoring bridge is explicit and evaluated lazily. It preserves the
existing mutable WORLD_BUILDER_MODE condition and does not access session state
during Server/World construction. Disabled authoring cannot manufacture native
ownership; enabled historical drafts still resolve through their retained bridge.
No historical implementation or repository history was imported.

Compatibility narrowing is deliberate parity, not new wide-height support:
TileValue elevation/walls remain byte fields and diagonal remains a short;
immutable raw native terrain and packets retain their separate wider contracts.
The authoring parity fixture uses raw elevation 65535 and proves the existing
byte projection is unchanged, not that all gameplay height handling is widened.
Missing authored terrain is not an unloaded scene. Signed/nonrepresentable absence
still refuses; representable blank backend regions remain blocked. Native collision
neighbors resolve only through the original owner, even at adjacent catalog edges.

One bounded correctness fix is intentionally distinct from extraction parity:
previous RegionManager.load suppressed classic archives whenever a retained
replacement profile was named, even when the resolved plan disabled native maps
and legacy placement population remained enabled. Archive and population policy
now both follow the plan/catalog's classic/additive/replacement ownership. The
failure-first rollback test uses a real minimal packed ZIP sector: the old build
skipped elevation-23 walkable data, leaving a blocked elevation-0 blank tile; the
corrected path invokes the real classic loader and reads the expected tile.
Malformed installed evidence and corrupt selected native sources still fail before
fallback, as verified by the prior activation tests.

`test-map-authority-facade.py` exercises eight maintained entry-point/contract scenarios:
classic typed/packed and mutable identity; additive signed/native plus registered
collision and classic availability; replacement absence/field parity; owner-local
neighbor collision; synthetic mutable refusal; explicit runtime-conditional drafts;
the intentional classic rollback fix; and resolved plan/catalog mismatch refusal
with inactive authority unable to consult authoring or silently select a catalog.
Null/overflow error snapshots and six
parity scenarios compare exactly with a frozen maintained baseline when
MAP_FACADE_BASELINE_CORE_JAR is supplied. The rollback counterexample is excluded
from old/new equality because its known bug is intentionally corrected.

Independent manager maintained-content lookup comparison from extraction source
`a1bf480d3f1a02b8da89e330593edef87ac5118d` read the four corners and center of
every sorted actual sector after all 33,545 placements were registered. The
1782-sector / 8910-tile snapshot digest matched the pre-facade build exactly:
`421fcabcdbdffba979349538892a174be31b1c83c4200e581212a498c725adfa`.
The independently built server JAR had SHA-256
`428e9bd15121ceb0d6e05aa1305c57600905697d556a67251c3d8b64c99a28d0`.
Evidence is retained in the isolated validation directory named above, including
`map-lookup-before-facade.log` and `map-lookup-after-facade.log`.
Final private server acceptance was independently completed with the same runtime
sources and exact test/documentation checkpoint
`1e6776b48c75eeeda2b4b9e57f396e990564626e`. All 54 focused checks passed:
facade (8), runtime validation (16), activation (10), native source/wire (14),
recovery (2) and atomic activation (4). The 8910-tile golden comparison remained
identical after all 33,545 placements were registered. Actual source-built private
startup on loopback port 43629 completed in 3493 ms without errors or exceptions,
with 877 NPC definitions, 3400 item definitions and 475 plugin handlers. Evidence
is retained in `server-startup-facade-review.log` in the isolated validation
directory above. The temporary process was stopped only after its exact private
directory and absence of client connections were verified; the owner's candidate,
baseline and public deployment were unchanged.

This acceptance is independent maintained-server startup, full population and
lookup parity. It is not a new graphical owner review: the owner's continued
successful gameplay was on the earlier candidate, not this source facade build.
Loader closeout still requires the remaining schema/capability contract slice and
the manager's cumulative readiness judgment. No next-slice, instance, widening or
editor implementation begins in this task.
The next contract slice must characterize effective terrain material IDs: current
native overlay lookup can dereference a missing TileDef. This extraction preserves
that behavior; typed pre-mutation material validation and its control-value rules
need their own bounded tests rather than an unrelated fix here.

### Fourth slice: schema/material contract (accepted)

The activated `feat/map-runtime-contract` worker starts from published
`64f3e48c0c73c6c41529126bad831eedcf32ed38`. Its initial audit keeps independent
axes separate: package schema 1; uniform/RLE v1 byte-height and raw v1/raw v2-u16
terrain codecs; placement schemas 1–4; scene-context header schemas 1–8;
terrain snapshot protocols 3–10 (9/10 are stage payloads, not context headers); terrain
stage protocols 1–5; readiness receipts 1/2; and scene baseline schemas including
schema 7 atomic fences versus schema 8 presentation pages. Numeric version order
within one axis is not proof of capability in another axis.

The retained Core target-evidence generator's encodingVersions `[1]` accompanies
mapFormatId `legacy-packed-orsc-v1`; it does not declare placement schemas 1–4 or
all wire versions. Owner-local capability edits remain preserved and are not used
as behavioral evidence or overwritten by this task. A separate data-only contract
and maintained-source golden tests are proposed rather than weakening historical
evidence parsers or treating their ambiguous version list as universal support.

Material audit findings before implementation:

- Native overlay lookup dereferences missing effective TileDefs. Overlay 0 is
  absent decoration, 250 aliases overlay 2 for collision, and 255 is blocking
  base color outside TileDef inventory; these controls must not become IDs.
- Native vertical/horizontal walls use effective DoorDefs with one-based terrain
  IDs; missing definitions currently silently lose collision. Diagonal ranges
  `(0,12000)` and `(12000,24000)` select the two wall orientations. Embedded
  scenery uses `(48000,60000)` and definition index `raw - 48001`; other raw
  ranges/endpoint controls require explicit existing-behavior characterization.
- Server EntityHandler has no roof/ElevationDef inventory. The matching client's
  EntityHandler creates six scalar ElevationDefs in code, with no other writer.
  A canonical data-only shared roof resource with preserved scalar pairs and
  byte-identical server/client build copies is proposed for review. It would
  supply effective roof inventory without inventing a vanilla ID ceiling or
  introducing art/format changes.
- Client Tile groundElevation is an integer; terrain presentation sampling uses
  three mesh units per raw elevation unit. Existing isolated wire tests exercise
  wide decoding, but an actual maintained generator-to-client materialization/
  geometry check with heights above 255 remains required before capability claims.

No codec widening, instance/editor/upgrader work, account/map/art changes or
public activation is authorized by this audit. Missing terrain, blocked void and
pending presentation remain different states; actual private-game closeout is
still required after the bounded contract implementation.

The bounded implementation introduces `runtime-contract/map-runtime-v1.json` as
advisory, source-backed data, not permissive configuration or a new negotiated
protocol. It separates accepted schemas from currently emitted schemas, stored
terrain/placement encodings from wire headers, and finite implementation budgets
from theoretical future expansion. In particular atomic fence schema 7 and
presentation baseline schema 8 remain independently supported.

`roof-definitions-v1.properties` is the canonical shared effective roof inventory.
Its six default `(height,fill)` pairs remain exactly `(64,6)`, `(64,3)`, `(96,2)`,
`(80,33)`, `(80,15)`, `(90,49)`. Separate strict readers require explicit ordered
contiguous zero-based IDs, at most 255 entries for the existing one-based byte
carrier. Reordering physical property rows does not renumber IDs; reordering,
duplicating or skipping declared IDs is rejected. Missing or malformed resources
fail closed without hardcoded fallback. Standard server/client builds and the
server's direct combat classpath copy the same canonical bytes. Read-only tools
that invoke the actual client EntityHandler must retain this packaged resource
on their classpath too.

This preserves current roof appearance and formats, but does not make arbitrary
extensions safe for old clients. Custom extensions require reviewed matching
server/client inventories and packaging/discovery. Existing package manifest and
paired-content fingerprints do **not** negotiate this new classpath resource;
no roof-resource hash handshake is added or claimed here.

Whole-catalog effective material preflight runs before first placement registration
without constructing entities or changing collision. Invalid overlays, walls,
roofs and embedded scenery now fail with package/sector/tile/field/raw selector
diagnostics. Existing overlay controls/aliases, diagonal control endpoints and
signed raw bit carriers remain unchanged. The failure-first maintained startup
test reproduced the prior overlay NPE after partial NPC population and missing
wall/roof/embedded-object acceptance; corrected cases refuse before population.
The check covers immutable native catalogs, not a new general authoring mutation
validator.

The wide-height replay uses the maintained server generator/compressor, real
client decoder and resident cache, actual World tile application and CPU terrain
mesh/sampling methods at 255, 256, 1024 and 65535. Context 5 is compatibility
coverage; context 8 covers current resident/atomic activation. Both use 48-tile
wire/source chunks, deliberately distinct from package presentation metadata 24.
The GPU/window constructor alone is bypassed; geometry methods are not mocked.
This is not graphical owner acceptance. Existing byte-height uniform/RLE formats
and byte/short server TileValue compatibility projections remain narrow; wider
raw-u16 terrain does not imply those compatibility fields were widened.

Worker matching server/client builds, including maintained source-authority
audits, pass. The focused checks currently pass 79 cases: material preflight (5),
resources and source-backed contract/direct combat classpath (4), wide height (1,
with context 5/8 subcases), activation (10), runtime validation (16), authority
facade (8), native source/wire (14), recovery (2), atomic activation (4), residency
(4), readiness (2), prediction (5), and symmetric residency (4). The recovery
replay also dispatches server-generated unsupported context headers 9/10 through
the real PacketHandler in ordinary and quarantined state: both refuse, while
generated baseline 7/8 remains supported and unknown schema 9 refuses.

Independent manager full-map review of runtime source checkpoint
`1cb5a2f8b9d3cf32a9c1b2150907c00cf54d4ffe` passed material preflight over all
1782 actual sectors and retained all 33,545 placements, 877 NPC definitions and
3400 item definitions. The 8910-tile typed lookup/collision digest remains exactly
`421fcabcdbdffba979349538892a174be31b1c83c4200e581212a498c725adfa`.
Its independently source-built private core JAR SHA-256 is
`7d4df4d625bf50b554b244f33e7b96e2f0d21f3732f5c875924304e6129f732c`.
Evidence is retained outside Git under the isolated validation directory above,
including `contract-fullmap-review.log` and independent material/resource/height
reviews. The manager also independently passed material (5), initial resource
(3) and wide-height (1) cases including current context 8. The added direct
combat classpath case and extended protocol counterexamples are test-only
checkpoint `a45bae2103c8186ed85a78f874aa20af6867f0a3`.

Final independent manager acceptance of exact source/test handoff
`8b180f2ee90af6bdf792f58d08ea8579aab5d2d1` passed all 79 focused checks above.
Whole-map population remains 33,545 placements with 877 NPC definitions and
3400 item definitions; the complete 1782-sector material scan and 8910-tile
lookup golden remain unchanged. Actual source-built private server startup on
loopback port 43629 completed in 3761 ms with 475 plugin handlers and no errors.
Evidence is retained in `server-startup-contract-review.log` under the isolated
validation directory above. The temporary process was stopped only after its
exact private directory and absence of client connections were verified.
The matching independently source-built private client SHA-256 is
`96fc644b3a87f78e95bcb699c42aa35b5ce6ac726c1260d31c7f61aeab5b4c6a`.

### Combined loader closeout — October 12, 2026

All four slices are integrated and published at Core commit
`0e7bebf7d92d049d68bcd47f4f7081cd2e9f1248`. Integrated server/client builds and
material, resource and wide-height checks passed after merge. The manager
launched the independently rebuilt combined server and matching OpenGL client
from the isolated validation directory on loopback port 43629. Only private
test accounts were copied consistently into its previously empty database;
the empty database was preserved separately. The public deployment and earlier
owner test session were not changed.

After reviewing the combined game, the owner reported "no issues found".
Logs are retained as `server-combined-owner-review.log` and
`client-combined-owner-review.log` in the validation directory. This completes
the combined gameplay gate alongside the 79 focused checks, unchanged full-map
population/material scan and typed lookup golden recorded above. It does not
claim exhaustive gameplay coverage or a graphical review of every wide height.

Manager readiness judgment: no remaining known major correctness,
content-preservation or source-authority issue within the four cleanup slices.
The loader is ready for copy-first migration and advanced toolkit adapter work.
Retained bounded carriers/codecs, global-only world-space support, classic
archive compatibility behavior, explicit authoring bridge and paired-build roof
extension requirements remain documented constraints, not promises of wider
support. A future converter must independently refuse corrupt input and prove
lossless conversion before offering activation. No public release, deployment
or runtime upgrade is authorized by this acceptance.

## Active versus historical documentation

Toolkit `docs/README.md` is authoritative for new tool work. Core's previous
World Builder compatibility plans remain recovery/history references, not
current implementation assignments. Existing owner saves and private test
fixtures remain outside Git. The first milestone includes a migration strategy
for those saves without reusing their old embedded runtime.
