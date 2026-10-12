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

### First loader slice: startup authority (private-game acceptance pending)

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

Manager acceptance still requires an isolated source-built Spoiled Milk server
and matching client with identical package/content inventories: startup/class
origin, normal/custom areas, layer transitions/collision, placements/custom
visuals and gameplay, reconnect/reload, and relevant rejection/recovery behavior.
No public deployment or overall loader-cleanup closeout is implied by this slice.

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
failure. The matching private graphical-route acceptance remains required.

Baseline protocol validation enumerates supported schemas independently of
terrain/context versions: atomic fences use schema 7, while presentation pages
use schema 8. The fence constant is **not** a maximum supported version. The
recovery replay now generates baseline bytes using maintained server
`PayloadCustomGenerator`/`SceneBaselineStruct`, dispatching schema-7 fences and
schema-8 inner/outer scenery and wall pages through the actual client handler
for ordinary login, failed-context quarantine and verified recovery. Unsupported
schema 9, truncated schema-8 pages and schema-8 misuse of the schema-7-only fence
remain rejected. This covers the login regression introduced by the earlier
incorrect upper-bound guard before graphical owner retest.

## Active versus historical documentation

Toolkit `docs/README.md` is authoritative for new tool work. Core's previous
World Builder compatibility plans remain recovery/history references, not
current implementation assignments. Existing owner saves and private test
fixtures remain outside Git. The first milestone includes a migration strategy
for those saves without reusing their old embedded runtime.
