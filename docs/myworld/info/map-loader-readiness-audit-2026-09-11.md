# Spoiled Milk map-loader readiness audit — 2026-09-11

Audited Core revision: `fec94c8731b5521410963575ef0f2fa5c05ef0b3`.

## Conclusion and limits

Core's recovery restored current server source as the build/runtime authority,
but Core is not yet a clean, self-describing map-only import target. The
remaining problems concern capability discovery, obsolete runtime artifacts,
package selection, and deployment failure handling. Passing the current
gameplay/terrain regressions does not establish compatibility with the current
Editor export contract. The follow-up below identifies concrete contract gaps.

This pass inspected Core's current source, metadata, existing compiled
artifacts, the previous recovery commit, and launch/release helpers. It did not
inspect or adopt the independent Editor runtime implementation, modify game
source, run an importer, or operate the public server. The current Editor
source contract was subsequently inspected read-only; see the dated follow-up
below. End-to-end compatibility is still unverified.

## Findings

### 1. High: declared capabilities lag behind the actual loader

`server/world-builder-capabilities.json` still declares map encoding `[1]`,
`legacy-packed-orsc-v1`, and `native-layered-package-v1`. The Core-owned
generator, `scripts/generate-world-builder-target-contract.py`, also emits
encoding `[1]` in fallback runtime evidence and the target capability document.
Simply editing generated JSON would therefore not be a durable correction.

By comparison, `NativeLayeredWorldPackage` accepts raw u16 terrain and placement
schemas 1–4, and the client contains u16 terrain decoders. Discovery metadata
does not clearly distinguish the historical packed-source adapter from the
current installed loader. This can lead an importer to classify Core as
requiring an upgrade; whether the latest importer actually does so must be
verified against that importer's contract.

Correction: define and generate a truthful Core-owned installed-runtime
capability contract, separate source-map discovery from destination loader
capability, and prove every advertised encoding behaviorally. Do not advertise
new capabilities merely to suppress the upgrade prompt.

### 2. High: the retired server snapshot and receipt remain in the repository

`server/world-builder-runtime/world-builder-managed-runtime.jar` remains tracked
and present (3,701,351 bytes). Its v2 receipt at
`server/conf/world-builder/installed-runtime-capability-v2.json` still declares
that archive and a `server/core.jar` fallback. It also names an installed client
source-upgrade manifest that is absent from that directory.

The current Ant build correctly excludes the managed runtime from active
server compilation and launch classpaths. This is a dormant artifact/discovery
hazard, not evidence that the running server currently uses that snapshot.
The receipt nevertheless describes a different runtime ownership model from
the one Core actually uses.

Correction: retire this artifact and receipt through reviewed Core changes,
retain historical evidence in Git/history documentation, and replace active
metadata with Core's actual contract. Verify importer discovery no longer
selects the retired runtime or a generic replacement core.

### 3. High: map identity is embedded in release/deployment code

`scripts/lib/layered-world-package.sh` hard-codes package version, manifest
hash, content fingerprint, sector count, placement count, and source path.
Hosted configuration and other profile checks also contain reviewed map
identity. An otherwise valid new map can therefore work through ordinary
primary-profile discovery but fail release or hosted validation unless those
code/config constants are updated together.

Correction: retain exact validation, but distinguish a reviewed map-selection
record from loader capability and implementation. Release/deployment should
derive and attest one approved package identity from data. A content update
should not require hand-editing loader-related source or shell constants.

### 4. High: automatic activation overrides explicit package and rollback settings

In `ServerConfiguration.initConfig`, environment/config values are read before
`applyInstalledWorldBuilderMap()`. When layered `primary.json` is active, that
method unconditionally enables the layered authority flags and replaces the
package path, manifest, and profile with those discovered in the checkout.

`run-hosted-server.sh` supplies a validated external live package via the
environment. Its documented `--legacy-map-rollback` alternative disables the
layered chain via environment flags. The later automatic activation conflicts
with both intents: it selects the checkout package over the external path and
re-enables layered flags during the requested rollback.

This is confirmed control-flow evidence; no live rollback or restart was
performed. Matching package bytes can hide the path discrepancy during normal
operation. A launch attestation alone does not prove which path the Java
configuration ultimately selected.

Correction: establish explicit selection precedence, validate that the selected
package agrees with approved activation metadata, and test final effective
Java configuration for ordinary imports, explicit hosted package selection,
and rollback. Do not remove integrity checks to resolve precedence.

### 5. High: layered installation can continue after failures

`layered_world_install_live_package` reuses a fixed `workspace/live-deploy`
directory, while generation explicitly refuses an existing package there.
Several helper failures use a function returning status 1 but do not explicitly
return from their callers. The install helper is called inside command
substitution; relying on shell `set -e` propagation is insufficient.

This matches the previously observed v0.2.87 deployment incident: generation
and validation failed, yet the deployment reported success and advanced the
detached checkout with an incomplete package directory. The source still
contains that mechanism. It was not reproduced against live state in this pass.

Correction: use unique generation/staging directories, propagate every failure
explicitly, verify the complete staged package before promotion, and refuse to
advance the checkout when installation fails. Regression tests must inject
copy/validation errors and an existing workspace using temporary fixtures.

### 6. Medium: temporary gameplay overlay remains a permanent build dependency

Ant still creates and places `core-gameplay-overlay.jar` before `core.jar`.
The overlay audit confirms its selected classes match current Core byte for
byte. There is no demonstrated active conflict in this audit. However, the
overlay originated as protection against the now-excluded server snapshot and
is still required by the build audit and tests.

Correction: review removal of this redundant artifact/classpath layer with
matching tests and build-audit changes. Preserve inventory-capacity and guild
linkage tests; their coverage remains useful after the workaround is retired.

### 7. Verification gap: server/client encoding symmetry is not established

The client names uniform, RLE, and raw u16 terrain encodings. The server package
loader accepts legacy uniform/RLE and raw u16, but rejects uniform/RLE u16 at
manifest parsing. This is not proof of a current export incompatibility: the
new Editor's required output set has not been identified. It must be resolved
in the compatibility matrix rather than assuming every client constant is an
Editor requirement.

## Verification performed

- `python3 scripts/audit-server-build.py --check --require-artifacts`: PASS.
  Confirms current Ant/classpath/artifact consistency; not a clean rebuild or
  exhaustive comparison of every source class against existing binaries.
- `python3 tests/myworld/test-layered-native-placement-runtime.py`: 3 passed.
- `python3 tests/myworld/test-layered-native-server-source.py`: 14 passed.
- `python3 tests/myworld/test-native-terrain-wire-cache.py`: 3 passed.

The terrain-cache suite uses its configured/default fixture package. These
results do not certify all encodings emitted by a newer Editor, every gameplay
system, or an end-to-end import into a clean target.

## Completion criteria for the cleanup

1. Identify an exact current Editor export/import capability contract and
   representative exported package supplied through the project boundary.
2. Implement required loader behavior in Core's own current source; preserve
   custom gameplay and persistence APIs.
3. Publish truthful generated capability/activation metadata without active
   references to the retired provider snapshot or generic-core replacement.
4. Resolve package-selection precedence and deployment failure propagation.
5. Validate a clean server/client build, current gameplay regressions, every
   required terrain/placement representation, and installed-profile activation.
6. On an isolated target, demonstrate that importing two different valid maps
   changes only approved map/selection metadata, requires no runtime upgrade,
   and leaves source/build/runtime artifact ownership intact.

Map-only compatibility is a contract for supported representations, not a
promise that arbitrary future map formats will never require a Core update.

## Current Editor follow-up — 2026-09-11

Reference: `/home/justin/rsc-world-editor`, branch
`fix/base-editor-presentation`, HEAD
`4dc820cc054ae8499d0afe38ffb4db1ee7a973b1`. This is actively edited source,
not an attested installed/released binary. At the final inspection its dirty
files were `docs/PRESERVATION-BASE-INTAKE.md`, `runtime-provider.lock`, and
`tests/myworld/test-world-builder-base-project-lifecycle.py`. The lock changed
during this work; the later observed provider commit was
`888e06d397059757fe4089c42cd09b4970ccaeff`. No provider implementation was
inspected or imported. No Editor worker, build, importer, or live-server action
was started. This follow-up is static control-flow/contract analysis, not a
transaction or gameplay test.

Editor Java paths below are relative to
`tools/world-builder/src/com/openrsc/worldbuilder/`.

### E1. High: two import routes have different runtime ownership requirements

`WorldBuilderCli.importActiveAdaptive` dispatches native Preservation Base
projects to `WorldBuilderCurrentRuntimeMapImport`; other eligible projects
still reach `WorldBuilderAdaptiveImporter`. It would be incorrect to describe
the new Base importer as the only available route.

The new route (`WorldBuilderCurrentRuntimeMapImport.java:30`) requires the
Preservation adapter/capability, installable `current-base-v1`, matching project
identity and composition ledger, and code-tree identities matching the
project's selected Base payload. Its map plan retains code and state paths
while publishing a new map generation and activation metadata. This is useful
separation, but this route is not generic acceptance of an independently built
Core loader. A shared protocol identifier alone is insufficient.

The adaptive route (`WorldBuilderRuntimeCompatibility.java:216`) requires
byte-identical project/target v3 host capability documents, specific provider
build/bootstrap/loader identifiers, exact advertised encodings 1–5, artifact
marker probes, and a pinned-core Ant build guard. It also rejects retired
runtime/overlay files merely for existing, whether or not on the classpath
(`:316`). Core's dormant managed archive and active gameplay overlay therefore
conflict directly with this gate. Removing those files alone is not sufficient.

The target artifact check is class-marker probing, not whole-JAR equality and
not a behavioral proof of gameplay compatibility (`:726–780`). Do not confuse
exact capability-document matching with comprehensive runtime verification.

### E2. High: the explicit adaptive upgrade still replaces game binaries

`WorldBuilderRuntimeCompatibility.prepareTargetUpgrade` (`:96–159`) constructs
actions for login-source alignment, pinned server-build integration, replacement
of `server/core.jar` and the client JAR with project runtime payloads, capability
replacement, and retirement of overlays/old receipts. This is an explicit
UPGRADE path, not an automatic side effect found in ordinary Import.

That separation is a real improvement. Nevertheless, accepting the suggested
runtime upgrade to clear an import blocker is not safe evidence that Spoiled
Milk's customized gameplay is preserved. Archive entries and marker probes do
not establish equivalence of inventory, commands, login, guild behavior,
persistence, or other local content. Do not use that upgrade on Core as an audit
shortcut; do not forge receipts or install a build guard that disables Core's
source ownership.

### E3. High: placement v5 is supported by Editor but rejected by Core

`WorldBuilderPlacementEncoding.java:13–100` supports v3/v4/v5, defaults to v4,
and promotes to v5 only explicitly. V5 adds the required header
`npcRoamCoverage: "blocked-void"`. Generic package validation permits NPC
roaming rectangles to cross absent terrain under that policy, while requiring
the spawn tile to have coverage (`WorldBuilderGenericLayeredPackage.java`,
`validatePlacements` and `validateNpcs`). V4 carries per-placement respawn time.

Core's `NativeLayeredWorldPackage` declares placements only through v4.
Consequently v5 is a real unsupported representation, but not every current
Editor export necessarily uses it. Implement and test absent-terrain blocking
semantics before advertising v5; relabeling a v5 payload v4 would discard
meaning. Preserve per-placement respawn and custom guild associations.

### E4. High: overlay 255 exposes a client/server semantic mismatch

Editor `WorldBuilderTerrainOverlay.java:5` reserves overlay 255 as blocking base
color, outside the target TileDef inventory. Its generic package validator
explicitly exempts this overlay from ordinary floor-definition lookup.
Core's client already recognizes it in `Client_Base/src/orsc/WorldBuilderTerrainOverlay.java`
and rendering code.

Core server collision does not make that distinction:

- `NativeLayeredTerrainCollisionPlan.java:123` passes overlay 255 to the
  supplied blocking-overlay predicate (only 250 is remapped).
- `RegionManager.java:4161` calls `getTileDef(overlayId - 1).getObjectType()`.
- `EntityHandler.java:1420` returns null for an out-of-range definition.
- Checked-in `server/conf/server/defs/TileDef.xml` has 26 TileDef entries,
  so index 254 is absent.

Thus materializing such a native tile with these definitions reaches a null
dereference. This is a source-proven conditional failure, not a reproduced live
incident and not proof the currently selected map contains overlay 255.
Treat the reserved overlay as an explicit shared semantic; test server movement
blocking and client appearance/prediction together. Null-checking alone would
avoid the exception but could incorrectly make the blocking terrain walkable.

Repair follow-up: worker commit `6d768898aa4a01f6cbcdfe7db94d6856812e71a3`
handles overlay 255 directly in the server collision plan, blocking movement
without a TileDef lookup and without adding projectile cover. Manager review
confirmed a successful server/plugin rebuild and passing overlay, combat
projectile, object-collision parity, native placement/server-source, Ranger
Guild, and build-authority regressions. The new overlay test is wired into
`test-all.sh`. This resolves E4 in source; it has not been deployed or tested
in a live client/server session. Other findings remain open.

### E5. Terrain representation concern narrowed

`WorldBuilderRawLayeredTerrainCodec` supports raw v1 and raw v2-u16; editable
packages use raw v2. Core already accepts raw v2-u16. The earlier observation
about Core server lacking uniform/RLE u16 is therefore not, by itself, evidence
that the inspected Editor's ordinary raw export is incompatible. Placement
versions and overlay semantics must be checked separately from terrain width.
The host contract's numeric encoding set 1–5 must not be read as five raw
terrain wire formats.

### Recommended boundary and verification

Keep Editor's Preservation migration path separate from Core maintenance.
The reviewed routes currently bind map import to managed runtime identity, not
just supported map behavior. Decide at the product boundary whether independent
current runtimes can register a verified capability contract or Core is to adopt
a reviewed Advanced composition. The latter is a larger ownership change, not
an implied authorization to replace Core with Base. No Spoiled Milk-specific
conditional is necessary to express a general capability/ownership contract.

Before declaring readiness:

1. Fix Core's overlay semantics, implement the required placement semantics in
   Core source, and address findings 1–6 above through focused reviewed changes.
2. Test representative exports: u16 elevation above 255, overlay 255, v4 respawn,
   v5 roaming over missing sectors, signed levels, boundaries/items/scenery.
3. On a disposable complete target, import two distinct maps and verify the
   exact approved write set. Hash source, builds, JARs, plugins, definitions,
   and offline player data before/after; only approved map/activation/transaction
   records may differ. Inject failure before and after activation and verify
   recovery without replacing unrelated state.
4. Exercise custom login, developer commands/graceful shutdown, backpack
   persistence, Ranger Guild credit, NPC respawn, and collision privately.
   Existing narrow regression passes do not substitute for these checks.

Selected Editor source SHA-256 values for this inspection:

- `WorldBuilderRuntimeCompatibility.java`: `636a3f757ac918c5e68376cf10cb851ef588e06696bcdf340bbd8cfaaeb24c25`
- `WorldBuilderCurrentRuntimeMapImport.java`: `f303ba4fc98394b74f0a2365be7b853dbd73487b9c7859faab51e4f35cd14c1e`
- `WorldBuilderPlacementEncoding.java`: `7f1582ab052b7fdc909f8f0ff3126e9f70a67ddde0d8e573b35d458b96c00c8e`
