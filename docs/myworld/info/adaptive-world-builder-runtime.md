# Adaptive World Builder Runtime Contract

Status: implemented upstream runtime capability. This document describes the
runtime boundary consumed by the standalone World Builder repository. It does
not authorize a release, target installation, or production-profile change.

The adaptive runtime opens one already-prepared, isolated signed-layered
project. It does not discover, convert, import, or update a target server. The
standalone World Builder owns those operations and must verify that the target
is offline before it prepares this runtime. Once started, this runtime has no
target path and accepts authored output only inside the selected project.

## Stable identities

The machine-readable source of truth is
`server/conf/world-builder/adaptive-runtime-capability-v1.json`. Server and
client code independently pin the same values.

| Role | Identity |
|---|---|
| Capability | `adaptive-world-builder-runtime-capability-v1` |
| Runtime profile | `adaptive-world-builder` |
| Server build | `core-framework-adaptive-builder-server-v1` |
| Client build | `core-framework-adaptive-builder-client-v1` |
| Loader | `generic-signed-layered-loader-v1` |
| Authoring | `generic-signed-layered-authoring-v1` |
| Definition binding | `world-builder-definition-catalog-binding-v1` |
| Client asset binding | `world-builder-client-asset-binding-v1` |
| Protocol | `world-builder-native-layered-protocol-v1` |
| Effective composition | `world-builder-effective-static-composition-v1` |
| Package schema | `layered-world-package-v1` |
| Coordinate model | `signed-layered-v1` |
| Placement encoding | `layered-world-placements-v3` |

`scripts/write-adaptive-world-builder-runtime-evidence.py` emits canonical
`world-builder-runtime-evidence` schema-version-1 JSON for adaptive discovery.
It hashes a real, bounded, non-linked definition-catalog evidence file rather
than publishing a placeholder hash. Example:

```bash
./scripts/write-adaptive-world-builder-runtime-evidence.py \
  --side server \
  --definition-catalog working/runtime/server/evidence/definitions.bin \
  --definition-catalog-id creator.catalog.v1 \
  > working/runtime/server/evidence/runtime.json
```

Run it again with `--side client` and the client catalog. The resulting server
and client evidence must match the target capability descriptor exactly. The
standalone preparer remains responsible for inventorying those files and for
supplying the actual client asset identity and hash.

## Required project layout

The runtime accepts the Phase 3 project layout only:

```text
<project>/
  source/
    layered-baseline/package/       immutable source package
  working/
    layered-world/package/          only authored package destination
    runtime/server/                 isolated server process root
    runtime/client/                 isolated desktop client root
  run/
    world-builder/
      adaptive-runtime.lock
      effective-static-composition.json
      runtime-binding.properties
      ready
      shutdown.request              created only to request shutdown
```

The server process current directory must be the canonical
`working/runtime/server` directory. Every workspace component and package file
must be a normal contained path: symbolic links, hard-linked package/evidence
files, path escapes, case aliases, Windows-hostile names, untracked package
files, and oversized inventories fail before readiness. The source baseline
and working package must be different filesystem identities and neither may
contain the other.

The runtime holds `adaptive-runtime.lock` for its process lifetime. Readiness
is published only after the package, definitions, assets, composition, and
binding pass. A second runtime cannot own the same project. Unexpected control
failure removes readiness and requests local server shutdown.

## Explicit activation

Package shape never activates this profile. The isolated server must enable
all of the following as one bounded selection:

- `world_builder_mode=true`
- `world_builder_adaptive_mode=true`
- `world_builder_layered_review_mode=true`
- `layered_native_world_runtime_profile=adaptive-world-builder`
- all layered location, spatial, protocol-client, package, residency,
  readiness, prediction, symmetric-residency, and atomic-activation gates
- loopback-only bind address, SQLite database `world_builder`, empty table
  prefix, one maximum player, packet registration off, and in-game editor on

The preparer must also supply the working package path, exact manifest and
closed-inventory hashes, immutable source-baseline inventory hash, one of the
origins `target-layered`, `target-packed`, or `standalone-empty`, definition
and asset IDs/hashes/evidence paths, and the initial global coordinate. The
definition and asset evidence files must be inside `working/`.

The desktop client independently requires:

- `-Dopenrsc.worldBuilderMode=true`
- `-Dopenrsc.worldBuilderAdaptiveMode=true`
- loopback host and a project-owned credential
- the server-produced
  `<project>/run/world-builder/runtime-binding.properties`
- client definition and asset evidence files inside `<project>/working/`

The binding contains exact server/client builds, protocol, loader, package,
manifest, package inventory, definitions, assets, levels, origin, initial
location, and effective-composition hash. The client validates it before
login, validates its loaded definitions, and sends the binding fingerprint in
the private loopback login flow. The server opens the editor only after a
constant-time comparison with its in-memory fingerprint. Any mismatch fails
before an editable session.

## Generic package contract

The adaptive profile accepts exactly one package with one `global` static
world space. It does not pin a package ID, version, manifest, level list,
sector count, placement count, or Spoiled Milk content identity. Bounds are:

- 1 through 64 declared levels;
- 1 through 8,192 48-by-48 terrain sectors;
- exactly one v3 placement set for every declared level;
- at most 100,000 total static placements; and
- the closed package guard's file, directory, per-file, and total-byte limits.

Every level must have terrain. Every placement and NPC roam rectangle must be
covered by package terrain. Terrain, boundaries, scenery, NPCs, and respawning
ground items are definition-checked before world population. Direction, ID,
amount, respawn, collision, coordinate, and coverage rules remain those of the
native layered package loader. Unknown, unsupported, duplicate, ambiguous, or
out-of-coverage content is rejected; nothing is silently skipped.

The runtime loads all four static placement families and publishes their exact
effective ordered records, source paths, source encodings, and hashes in
`effective-static-composition.json`. The adaptive profile is a complete package
replacement, so legacy configuration overlays do not enter the effective
model. Package boundary placements are inspectable and preserved exactly;
terrain wall fields, scenery, NPCs, and ground-item spawns retain their
existing editor operations.

## Empty and existing-level authoring

Existing copied levels are editable only under explicit adaptive activation.
New terrain sectors use the shared canonical void tile:

```text
elevation=0, ground texture=1, ground overlay=8,
roof=0, horizontal wall=0, vertical wall=0, diagonal wall=0
```

The standalone-empty origin is stricter: one global level `0`, one sector
`0,0`, one empty v3 placement set, no placements, every tile exactly canonical
void, and initial coordinate `0,0` on level `0`. Adaptive player initialization
intentionally bypasses ordinary production login recovery, because production
correctly treats overlay 8 as non-playable while an empty Builder uses it as an
authoring canvas. The definition catalog must expose overlay 8's one-based tile
definition ID 7.

New adaptive placement IDs use only family, signed level, coordinate, and a
bounded deterministic collision slot under `world-builder.authored.*`. Host
paths, time, randomness, package identity, and Spoiled Milk identity never
enter them. Existing arbitrary placement and placement-set IDs are retained
across edit/save.

## Verified save and recovery

`::saveworldedits` materializes the complete effective package, including all
unchanged boundary records and all edited terrain/scenery/NPC/ground-item
records. Publication is copy-on-write:

1. verify the current working and immutable baseline closed inventories;
2. construct canonically ordered package bytes in a sibling stage;
3. force staged file content to storage;
4. reload and validate the exact staged bytes with the generic runtime and
   active definition contracts;
5. revalidate after observer boundaries and immediately before publication;
6. write durable transaction evidence and require atomic same-filesystem
   directory moves;
7. re-read and verify the published package; and
8. remove the prior package and transaction evidence only after success.

A failed save restores and verifies the prior complete working package. A
startup recovery marker distinguishes an old complete package, a new complete
package, and an interrupted swap. Unowned staging state or unverifiable
recovery state blocks startup for manual review. Source baseline and target
server data are never save destinations. The runtime fingerprints the source
baseline before and after publication; the standalone preparer retains target
fingerprint responsibility because no target path enters this process.

## Preservation and integration boundary

`preservation-r64-replacement`, `spoiled-milk-replacement`,
`spoiled-milk-builder-draft`, and `spoiled-milk-world-builder-export` retain
their previous identities, exact content/count checks, and behavior. Adaptive
code does not infer or alter any of those profiles.

The standalone repository must still:

1. perform read-only discovery and conversion;
2. prepare the exact isolated layout and truthful configuration/evidence;
3. prove source and target drift has not occurred;
4. launch only these isolated client/server copies; and
5. keep target installation behind its separate offline, preview,
   confirmation, backup, receipt, rollback, undo, and no-force transaction.

This upstream runtime does not patch unknown client/server binaries, distribute
maps to players, or grant import authority.

## Verification

Automated coverage lives in
`tests/myworld/test-adaptive-world-builder-runtime.py` plus the native layered
package, placement, protocol, persistence, editor, and runtime suites. It uses
temporary content-neutral packages and checks deterministic output across
absolute roots, all placement families, canonical empty terrain, strict client
binding, capability evidence, link/path/size attacks, staged-byte tampering,
interrupted recovery, and source/target preservation.

Before a downstream release, an owner must still visually run both an adopted
generic project and standalone empty project. The owner—not an AI screenshot
review—confirms rendering, navigation, existing-level edits, new void terrain,
placements/collision, save, close/reopen, and client reconnect.
