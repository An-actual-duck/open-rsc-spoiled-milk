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

### Third slice: authoritative lookup inventory (implementation pending)

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

Proposed boundary for manager review: one immutable source facade bound to the
resolved activation plan and validated catalog, with explicit legacy projection,
native server-field/collision conversion and opt-in authoring adapters. First
delegate typed terrain/owner lookup and catalog population-policy access; retain
the packed compatibility backend, actual object registry, transition guards and
all presentation/readiness/prediction machinery. Capture pre-change parity before
moving conversion. Missing authored terrain is not an unloaded scene, and no
neighbor lookup may silently cross to another owner or classic fallback.
This inventory does not start instances, widening or editor development.

## Active versus historical documentation

Toolkit `docs/README.md` is authoritative for new tool work. Core's previous
World Builder compatibility plans remain recovery/history references, not
current implementation assignments. Existing owner saves and private test
fixtures remain outside Git. The first milestone includes a migration strategy
for those saves without reusing their old embedded runtime.
