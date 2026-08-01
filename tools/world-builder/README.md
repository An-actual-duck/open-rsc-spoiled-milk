# World Builder Tools

This module owns standalone World Builder project discovery, manifests,
workspace management, export, import, rollback, and launch supervision as
those phases are implemented.

## Product generations

The packed-map editor is frozen and unmaintained at release tag
`v1.1.0`. Current signed-layered work belongs to the separate
`Spoiled Milk World Builder 2` product and update channel
`rsc-world-editor-v2`. Its archive prefix, install folder, signed-layered
workspace, and release identity are distinct; automatic updates are eligible
only from v2 itself, and the v2 packaged launcher refuses a legacy or
unidentified workspace.

The ambiguous `scripts/package-world-builder-release.sh` command therefore
fails closed. Future v2 artifacts use
`scripts/package-world-builder-v2-release.sh`, which embeds the reviewed
signed-layered package and remains production-locked until layered
export/import and final release validation are accepted.

Read-only target discovery remains available independently:

```bash
./scripts/build-world-builder-tools.sh
java -jar output/world-builder-tools/world-builder-tools.jar discover \
  --server-root /path/to/private-server
```

Discovery supports the versioned `spoiled-milk-repository-v1` layout and
writes its deterministic source manifest to standard output. It does not
create a workspace or change the target.

The Phase 1 runtime can prepare an isolated workspace and launch the local
Builder server/client pair:

```bash
./scripts/build-server.sh
./scripts/build-client.sh
./scripts/build-world-builder-tools.sh

java -jar output/world-builder-tools/world-builder-tools.jar launch \
  --server-root /path/to/private-server \
  --runtime-root /path/to/world-builder-release \
  --workspace /path/to/world-builder-project \
  --port 43615
```

`prepare` accepts the same arguments but stops after staging. `run` starts an
existing prepared workspace with `--workspace`; it reads the recorded port
from `runtime.json`. An explicit matching `--port` remains available for
diagnostics.

Preparation never replaces an existing workspace. It records the target's
verified authored files under immutable-by-contract `<workspace>/source` and
creates the complete runnable copy under `<workspace>/working`. The working
tree receives a clean Builder database, no generated client identity or
connection files, and a loopback-only configuration before the project is
published atomically. Launch re-verifies every source-snapshot hash and refuses
added, changed, missing, or symlinked source files. The target private-server
tree is read-only throughout preparation and use.

The Builder server receives the canonical workspace root explicitly and
refuses to start from any directory other than `<workspace>/working/server`.
Terrain, scenery, NPC and ground-item overlays, the client terrain mirror, and
terrain backups all resolve through that validated context. The editor shows
the project folder name, source revision, and current saved/unsaved state.

The launcher keeps logs under `<workspace>/logs`, active PID files and the
last-run receipt under `<workspace>/run`, and refuses a second process for the
same workspace. Closing the client requests an orderly local server shutdown.
Generated credentials are never printed or placed in manifests.

## Layered draft: Create Level

The first native layered writer is deliberately narrower than ordinary map
editing. With the layered Builder closed, create a workspace-owned signed
level around a geographic anchor:

```bash
java -jar output/world-builder-tools/world-builder-tools.jar create-level \
  --workspace /path/to/world-builder-project \
  --level -3 \
  --anchor-x 140 \
  --anchor-y 640
```

Optional `--name` and lowercase identifier `--role` values override the
generated level metadata. The transaction takes the same per-workspace lock as
the launcher, revalidates the immutable source package, stages a complete
copy-on-write draft, creates a void-backed 3-by-3 sector window with a
walkable 3-by-3 tile pad centered on the anchor plus an empty v3 placement
set, rewrites all manifest hashes deterministically, validates the full
descendant, and swaps it into `working` with rollback protection. The source
snapshot and target game are reverified unchanged before success.

Reopen the Builder to navigate to the new level. Repeating an existing level,
running the operation while the Builder is open, malformed metadata, source
drift, or a draft that changes accepted package content is refused. The
Builder-only runtime profile cannot start an ordinary server. Terrain/entity
editing on the accepted source levels and layered export remain disabled.

### Layered draft: generated-level authoring

Once at least one level has been created, the Builder enables terrain,
scenery, NPC, and collectible ground-item tools only on Builder-created
levels. Inspect or copy a tile, choose the checked elevation, floor-color,
floor-texture, roof, wall, or diagonal fields, and paint with the 1-by-1 or
3-by-3 brush. The server applies the working overlay immediately to terrain
presentation and collision. Scenery and NPC tools place and remove
package-owned entities through their native signed-level registries.

The **Items** tab places one respawning package-owned spawn per tile. Select an
item definition, stack amount, and `1..86400` second respawn time, then
right-click allocated terrain to place it. Non-stackable definitions always
use amount `1`. In Remove mode, right-click a visible authored ground item and
choose **Remove spawn**; removal permanently cancels any delayed respawn for
that slot. A picked-up spawn keeps its slot reserved while absent and must
respawn before the first removal control can select it.

All mutation on accepted source levels `-2`, `-1`, `0`, `1`, `2`, and `10`
is refused. Generated-level placement also refuses absent terrain, invalid
definitions, conflicting authored slots, and unallocated NPC roaming bounds.

Select **Save** to write one bounded deterministic v5 draft journal containing
terrain, sector growth, scenery, NPC, and ground-item operations. Saving does
not modify the source snapshot, target private server, or exported game files.
Close the Builder normally; while holding the workspace lock, the launcher
materializes that journal through a copy-on-write package transaction, verifies
the complete source descendant and hashes, then removes the journal. Reopen the
same workspace to review the durable result. The launcher remains backward
compatible with earlier v1-v4 journals.

Allocate one new void-backed sector at an existing edge from an active
Builder-created level with:

```text
::buildergrow 192 640 -3
```

The optional signed level defaults to the current level. Allocation must share
an edge with existing or already queued terrain; gaps, duplicates, source
levels, and more than 64 sectors per transaction are refused. New sector tiles
use Floor Color `1` plus blocking/invisible Floor Texture `8`, so creators
paint only the area they want instead of erasing a large floor or ocean.
Unallocated sectors use the same explicit-void presentation. Save, close, and
reopen before painting a new sector. Each transaction is also limited to 4,096
distinct edited tiles. Standalone boundary-object authoring, terrain deletion,
layered-package export, and target-game import remain separate future gates.

After closing the Builder, export the saved working map with explicit release
provenance:

```bash
java -jar output/world-builder-tools/world-builder-tools.jar export \
  --workspace /path/to/world-builder-project \
  --builder-version v0.2.39 \
  --source-commit 0123456789abcdef0123456789abcdef01234567
```

Export revalidates the immutable source snapshot, working layout, matching
server/client terrain archives, and all four authored JSON overlays. A changed
project publishes atomically under `<workspace>/exports/export-<fingerprint>`.
The directory contains only the canonical five authored files, a strict v1
manifest, and a readable change summary. Identical input and provenance reuse
the byte-identical verified export; no-op projects report `no-changes` without
creating an export. Active Builder sessions, source drift, malformed input,
unsafe paths, incomplete data, and tampered existing exports are refused.

Preview an import while the target private server is offline:

```bash
java -jar output/world-builder-tools/world-builder-tools.jar import \
  --workspace /path/to/world-builder-project \
  --export /path/to/world-builder-project/exports/export-0123456789abcdef \
  --target-root /path/to/private-server \
  --dry-run
```

After reviewing the exact additions/replacements, repeat with `--apply` in
place of `--dry-run`. Apply reserves the configured server port for the whole
transaction, rechecks the source revision, writes a pending receipt, verifies
backups and same-filesystem staging, replaces in deterministic order, and
marks success only after reopening every installed file. Any partial failure
restores the prior bytes and prior file absence before reporting failure.

Preview or apply the newest eligible receipt-based undo with:

```bash
java -jar output/world-builder-tools/world-builder-tools.jar undo-import \
  --workspace /path/to/world-builder-project \
  --target-root /path/to/private-server \
  --dry-run
```

Use `--apply` only after reviewing the undo. Undo refuses if any installed file
changed after import, safeguards the installed state, restores the exact prior
state, and records a successful rollback receipt. There is intentionally no
force option.

Packaged launchers use two human-oriented commands so shell and Windows batch
files do not parse transaction JSON or duplicate safety logic:

```bash
java -jar world-builder-tools.jar export-import \
  --workspace /path/to/world-builder-project \
  --target-root /path/to/private-server \
  --builder-version v0.1.0 \
  --source-commit 0123456789abcdef0123456789abcdef01234567

java -jar world-builder-tools.jar undo-latest-import \
  --workspace /path/to/world-builder-project \
  --target-root /path/to/private-server
```

The first command exports saved working data, prints the full import preview,
and requires the exact confirmation `IMPORT`. The second prints the rollback
preview and requires `UNDO`. Empty input or any other response cancels without
changing the target. Both retain the same offline, revision, backup, receipt,
and changed-after-import protections as the lower-level commands.

The manifest schemas in `schema/` are release contracts. Add a new schema
version instead of changing the meaning of an existing version.
