# Standalone World Builder 2 Plan

Status: active; Phases 0-4 complete, Phase 5 standalone packaging and layered
feature-completeness work active

Owner: Spoiled Milk project owner

Initial branch: `feat/world-editor-standalone-builder`

Related plans:

- [`in-game-world-editor-plan.md`](in-game-world-editor-plan.md)
- [`project-structure-refactor-plan.md`](project-structure-refactor-plan.md)
- [`renderer-and-shader-roadmap.md`](renderer-and-shader-roadmap.md)

## Summary

Package the signed-layered in-game world editor as a separate, approachable
World Builder 2 release while retaining the same editor inside the normal
Spoiled Milk client and server.

The standalone release should feel like a single-player application: the user
drops the World Builder folder into the root of a compatible private server,
launches it, and arrives in game as an automatically authenticated,
invulnerable administrator named `Builder` with the world editor open. The
implementation may continue using a local client and server internally. That
preserves the existing authoritative editor, collision, entity, and protocol
architecture without exposing process-management complexity to the user.

Editing must happen in an isolated workspace. Starting or saving in World
Builder must never overwrite the private server's active map. A separate
`Import Map Changes` action deliberately installs an exported bundle only
after compatibility, base-revision, backup, and offline-server checks pass.
An `Undo Last Map Import` action must restore the exact previous files.

This is a separate product and release target, not a replacement for the
administrator-only editor in the regular game.

The packed-map editor is now a frozen legacy product. Its final release is
`rsc-world-editor-v1.03`; it remains available for creators who retain packed
maps, but receives no further maintenance. The signed-layered editor is
generation 2 with product ID and update channel `rsc-world-editor-v2`. It uses
the distinct install folder `Spoiled Milk World Builder 2`, artifact prefix
`rsc-world-editor-v2`, and workspace model `signed-layered-v1`. V1 never
automatically upgrades to v2, and v2 never adopts or silently converts a v1
workspace.

## Desired User Experience

The intended first-use flow is:

1. Extract `Spoiled Milk World Builder 2` into a supported private-server root
   alongside, rather than over, any legacy editor installation.
2. Run `Start World Builder`.
3. The launcher identifies the private server and creates an isolated project
   from its currently effective world files.
4. A private loopback server and local client start automatically.
5. Login is skipped. The user appears as `Builder`, invulnerable, with the
   world editor already open and Build renderer mode available.
6. The user edits terrain, scenery, NPCs, and collectible respawning ground
   items and saves normally.
7. The original private-server files remain unchanged.
8. The user runs `Import Map Changes` when ready and reviews a clear preview.
9. The importer backs up the target, installs and verifies the bundle, and
   records an import receipt.
10. `Undo Last Map Import` restores the prior world if the user changes their
    mind.

The advanced implementation details should remain visible in logs and
diagnostics, but should not be required knowledge for ordinary use.

## Product Boundaries

### Included in the first complete release

- A separate World Builder 2 package, product identity, update channel, and
  versioned release artifact.
- Automatic discovery of explicitly supported Spoiled Milk private-server
  layouts.
- An isolated local Builder server, client, database, configuration, logs, and
  workspace.
- A fixed `Builder` account identity with an install-local credential.
- Normal authenticated login performed automatically by the client.
- Server-enforced administrator access, invulnerability, and automatic world
  editor session opening.
- Terrain, ordinary scenery, NPC, and layered respawning ground-item editing
  supported by the existing editor.
- Isolated save, deterministic export, deliberate import, and exact rollback.
- Self-contained Linux-x64 and Windows-x64 packages with platform launch,
  import, and rollback scripts plus bundled Java runtimes.
- Automated filesystem, security, persistence, import, rollback, and
  compatibility tests plus private visual verification.

### Not included in the first release

- Connecting World Builder to a public or remote server.
- Editing a running target private server in place.
- Importing player accounts, inventory, quests, or other gameplay database
  state.
- Treating an arbitrary OpenRSC fork as compatible based only on similar
  folder names.
- Patching unknown server source code or binaries during import.
- Network-accessible default administrator credentials.
- Multi-user or collaborative editing.
- Cloud project storage or automatic publishing.
- A merged single-JVM client/server rewrite.
- Writable boundary-object support before the integrated editor gains a
  deterministic boundary persistence format.
- Advanced source-control merging of two independently edited maps.

## Non-Negotiable Safety Invariants

These rules are acceptance requirements for every implementation phase:

1. **The target is read-only while editing.** Launching, playing, painting,
   and saving in World Builder may only mutate the Builder workspace.
2. **Builder mode is loopback-only.** The Builder server must refuse startup
   unless its game and websocket listeners bind to a loopback address.
3. **Normal behavior is unchanged.** When Builder mode is disabled, the normal
   login screen, account rules, editor authorization, server configuration,
   saves, and release packages behave as they do today.
4. **Normal authorization remains authoritative.** Automatic UI behavior must
   not replace server-side administrator and editor-session validation.
5. **The target server must be offline for import and rollback.** Uncertain
   state produces a refusal, not a best guess.
6. **Imports are revision-aware.** If a target file changed since the project
   source snapshot or since the previous import receipt, the tool refuses to
   overwrite it without an explicit future conflict-resolution workflow.
7. **Every import is recoverable.** All replaced or newly created files are
   recorded and backed up before the first target replacement.
8. **Partial failure cannot become a claimed success.** A failed transaction
   rolls back already-replaced files and reports any recovery work still
   required.
9. **Server and client terrain remain identical.** The same verified terrain
   archive is installed in both locations.
10. **Builder credentials and databases never enter an export bundle.**
11. **Unknown layouts and schemas are rejected.** Folder-name resemblance is
    not sufficient compatibility evidence.
12. **The public server is out of scope.** Development and verification use a
    private local server only.
13. **Product generations never cross-update.** V1 and v2 have distinct
    product IDs, update channels, tag/archive prefixes, install folders, and
    workspace formats. Neither line may claim the other as an automatic
    update source.

## Existing Architecture and Reuse

The current implementation provides strong foundations:

- `WorldEditorSessionManager` owns the single authorized editor session,
  sequence validation, terrain draft, archive inspection, and terrain save.
- `WorldEditorTerrainArchive` reads exact raw terrain records.
- `WorldEditorTerrainSaveFiles` verifies base hashes, materializes changed
  sectors, validates output, creates a backup, and keeps client/server terrain
  copies synchronized.
- `WorldSceneryEditFiles` saves ordinary scenery additions and removals into
  deterministic MyWorld JSON overlays.
- `WorldNpcEditFiles` does the same for NPC additions and removals.
- `WorldPopulator` consumes the MyWorld scenery and NPC overlay files.
- The server already supports loopback binding, SQLite configuration, custom
  landscapes, and an opt-in administrator-only editor flag.
- The client already receives an authoritative editor-open packet and can
  enter the Build rendering profile.
- Existing private-server launchers demonstrate local server/client startup,
  and the player release pipeline demonstrates bundled-runtime packaging.

The current save paths are unsuitable for the standalone product because they
write the active server and client archives directly. The first architectural
change must make editor persistence operate on an explicit project/workspace
context rather than hard-coded repository-relative paths.

## Authored World Data Contract

One exported project bundle has five canonical authored files:

| Canonical bundle file | Purpose | Target installation path |
| --- | --- | --- |
| `Custom_Landscape.orsc` | Terrain, elevation, floor, roof, and embedded wall fields | `server/conf/server/data/Custom_Landscape.orsc` and `Client_Base/Cache/video/Custom_Landscape.orsc` |
| `MyWorldSceneryLocs.json` | Added/replaced ordinary scenery | `server/conf/server/defs/locs/MyWorldSceneryLocs.json` |
| `MyWorldSceneryRemovals.json` | Removed base scenery positions | `server/conf/server/defs/locs/MyWorldSceneryRemovals.json` |
| `MyWorldNpcLocs.json` | Added/replaced NPC locations and roam bounds | `server/conf/server/defs/locs/MyWorldNpcLocs.json` |
| `MyWorldNpcRemovals.json` | Removed base NPC locations | `server/conf/server/defs/locs/MyWorldNpcRemovals.json` |

The five canonical files produce six installed files because the terrain
archive is duplicated for server authority and client rendering. The importer
must verify the two installed terrain copies have the same SHA-256 digest.

The target configuration must enable the matching data paths:

- `custom_landscape: true`
- `want_myworld: true` for the MyWorld scenery and NPC overlays

Configuration edits are part of the import transaction and backup. The
importer must preserve unrelated settings and comments, reject duplicate or
ambiguous keys, and verify the resulting configuration before committing it.

Absent optional overlay files are represented explicitly as absent/empty in
the source manifest. The tool must distinguish “the source had no file” from
“the source contained an empty array” so rollback can restore absence exactly.

### Layered-generation placement extension

The five-file table above remains the legacy packed-map export contract. It
must not be silently broadened or claimed to carry native layered placements.
The layered Builder instead writes package-owned
`layered-world-placements-v3` payloads inside its isolated working package.
Its first resumed feature-completeness milestone adds definition-aware ground
spawns with amount and respawn time, persisted with terrain, level allocation,
scenery, and NPC changes through one backward-compatible v5 draft journal and
one clean-close copy-on-write package swap. Import/export of that layered
package remains a later explicit transaction; authoring the spawn does not
write the target game.

## Compatibility Contract

The first release supports known Spoiled Milk private-server layouts, not all
OpenRSC-derived projects.

A supported target must provide enough evidence to identify:

- the server root and selected configuration file;
- the effective server landscape format and source file;
- the corresponding client landscape file;
- the MyWorld definition and overlay directories;
- the target server/client protocol and content version;
- object and NPC definition compatibility; and
- whether the installed server contains the required MyWorld overlay loaders.

The project manifest records compatibility fingerprints, including at least:

- Builder release and source commit;
- target layout adapter and schema version;
- relevant server/client version identifiers;
- hashes for the source terrain and four entity-overlay states;
- definition hashes or an equivalent stable content-version fingerprint;
- selected configuration name and relevant effective flags; and
- export file hashes and byte sizes.

The first version may support only the current `.orsc` MyWorld landscape path.
JAG/MEM map layouts, missing client data, mismatched client/server archives,
or unknown forks must fail with an actionable explanation. Additional layouts
should be added later as named, tested adapters rather than generalized path
guessing.

## Discovery Rules

Discovery starts from the World Builder folder's parent server root or an
explicit `--server-root` argument.

It must:

1. Canonicalize the root without following an unsafe path outside it.
2. Check explicit server, client, configuration, landscape, and definition
   fingerprints.
3. Enumerate viable configurations when more than one exists.
4. Select automatically only when there is exactly one unambiguous supported
   choice.
5. Determine the effective landscape from configuration, membership mode, and
   supported map format.
6. Verify the server and client source archives agree before copying them.
7. Record hashes before and after the copy to catch concurrent changes.
8. Create the project only after every required input validates.

Discovery itself is read-only. An unsupported or ambiguous root produces a
report; it must not create configuration files, copy into active directories,
or attempt to repair the target.

## World Builder Folder and Workspace

The intended release layout is:

```text
Spoiled Milk World Builder 2/
  Start World Builder.sh
  Start World Builder.cmd
  Import Map Changes.sh
  Import Map Changes.cmd
  Undo Last Map Import.sh
  Undo Last Map Import.cmd
  README.txt
  RELEASE-IDENTITY.json
  VERSION.txt
  SOURCE-COMMIT.txt
  builder-runtime/
    client/
    server/
    launcher/
    layered-world/package/
  workspace/
    project.json
    credentials/
    source/
    working/
    exports/
    backups/
    receipts/
    logs/
```

Generated workspace content must be excluded from release archives, Git, and
future updates. Updating the Builder binaries must preserve user projects,
exports, backups, and receipts.

### Source snapshot

`workspace/source/` is an immutable copy of the target's effective terrain,
entity overlays, and relevant configuration state. Its hashes establish the
base revision. It is never used as a save destination.

### Working copy

`workspace/working/` contains a complete Builder-specific server configuration
and client cache. The Builder server and client load only these files. Existing
editor persistence should naturally write inside this tree after hard-coded
paths are replaced by the explicit workspace context.

### Exports

Each successful export receives its own immutable directory, manifest, and
human-readable summary. A stable `latest` pointer may identify the newest
complete export, but import must resolve it to an immutable export before
validation begins.

### Backups and receipts

Import backups and receipts remain inside the Builder workspace. A receipt
records the exact target-relative paths and before/after hashes, whether each
file previously existed, the backup location, configuration changes, and
transaction result.

Rollback may proceed only if the current target still matches the receipt's
installed hashes. This prevents `Undo Last Map Import` from erasing newer
manual changes.

## Builder Runtime Profile

Builder mode is an explicit server and client launch profile. It must not be
inferred from a username, current directory, or editable client preference.

### Local server requirements

- Bind game and websocket listeners to loopback only.
- Refuse Builder-mode startup on wildcard, LAN, or public addresses.
- Use a dedicated, dynamically selected or conflict-checked local port.
- Use a Builder-only configuration and SQLite database inside the workspace.
- Allow one player and disable public registration.
- Disable outbound/public integrations that are unnecessary for editing.
- Load terrain and entity data only from `workspace/working/`.
- Retain NPCs and world data needed for accurate inspection and collision.
- Emit a machine-readable readiness signal rather than relying on a fixed
  startup sleep.
- Shut down cleanly when requested by the launcher.

Gameplay systems should not be broadly disabled merely because they seem
expensive. Build-mode performance changes must be measured and must preserve
the authored world state needed for editing.

### Builder identity and login

The visible account name is always `Builder`.

On first launch, a provisioning service creates or validates a complete
Builder account in the isolated database with administrator group membership.
It also creates an install-local random credential. The credential is stored
outside exports and is not a universal password shipped in every release.

The client still performs the normal login handshake. When launched with the
explicit Builder profile, it reads the local credential, targets the verified
loopback endpoint, suppresses the login UI, and retries within a bounded
server-startup window. Outside that profile, the existing login UI and account
behavior are untouched.

After authenticated login, the server must:

- verify Builder mode and the exact Builder identity;
- verify administrator group membership;
- set invulnerability through the existing player cache/state API;
- request an editor session through the existing authorization service;
- send the normal authoritative editor-open packet; and
- report a clear failure if any step is denied.

Automatic opening should call a reusable editor service, not inject a text
command or bypass sequence/session validation.

### Client startup behavior

On successful login, the client should:

- open the world editor automatically;
- start in a safe non-painting mode such as Navigate;
- make the existing Build renderer profile available and enable the agreed
  Builder default without changing normal client persistence;
- retain the normal editor mode, save, close, and renderer controls; and
- show the selected project and unsaved/export state clearly.

## Save and Export Contract

`Save` remains the deliberate editor draft commit. In Builder mode it commits
only to the working copy.

Before save, the server verifies the working base revision just as the current
terrain saver verifies the active archive. Terrain is materialized into both
working server and client archives. Scenery and NPC overlays are saved under
the working configuration directory.

Export is a separate packaging step after a successful save. It must:

1. Verify there is no incomplete editor save.
2. Verify working server/client terrain hashes match.
3. Parse and validate all four JSON overlay files.
4. Compare working files to the immutable source snapshot.
5. Produce a deterministic bundle containing the five canonical authored
   files, manifest, and readable change summary.
6. Write to a temporary export directory.
7. Reopen and validate the completed bundle.
8. Atomically publish the immutable export directory.

An export with no changes should report that fact and avoid presenting itself
as a meaningful import candidate.

## Import Transaction

`Import Map Changes` is intentionally separate from editing and saving.

### Preview stage

The importer must show:

- target root and selected configuration;
- Builder/export version and creation time;
- files that will be added, replaced, or left unchanged;
- terrain and entity-overlay hashes;
- relevant configuration changes;
- compatibility and base-revision results;
- backup destination; and
- any warning that does not require refusal.

A non-interactive `--dry-run` mode must perform the same validation and make no
changes.

### Preconditions

Import is refused when:

- the target layout is unknown or ambiguous;
- the selected export is incomplete or fails its manifest hashes;
- target definitions or runtime support are incompatible;
- a target file no longer matches the recorded base revision;
- the target server appears to be running;
- server-running state cannot be determined safely;
- the client and server terrain destinations are unexpectedly different;
- backup space or write permissions are insufficient; or
- another import/rollback transaction owns the workspace lock.

### Commit sequence

After explicit confirmation, the importer:

1. Acquires an exclusive transaction lock.
2. Repeats all precondition and target-hash checks.
3. Creates a transaction directory and pending receipt.
4. Backs up every existing target and selected configuration file.
5. Records which target files were absent.
6. Stages all new files on the target filesystem.
7. Parses and verifies the staged landscape, overlays, and configuration.
8. Replaces targets in a deterministic order.
9. Verifies every installed file and server/client terrain equality.
10. Marks the receipt successful only after complete verification.

If a replacement or verification fails, the importer restores every target
from the pending receipt, verifies the restoration, releases the lock, and
reports the failed transaction. It must never print “import complete” while a
pending receipt remains.

## Rollback Contract

`Undo Last Map Import` selects the newest successful, non-reverted receipt for
the current target fingerprint.

It must:

1. Require the target server to be offline.
2. Verify current files match the receipt's installed hashes.
3. Preview files that will be restored or removed.
4. Back up the current installed state as a rollback-of-rollback safeguard.
5. Restore prior files and exact prior absence states.
6. Restore the prior configuration.
7. Verify all restored hashes.
8. Mark the original receipt reverted and create a rollback receipt.

Rollback refuses if later edits or imports changed the target. The first
release should not offer a force flag that could conceal such conflicts.

## Launcher and Process Supervision

The launcher owns the local application lifecycle:

1. Locate and validate the target project.
2. Acquire a per-project Builder lock.
3. Provision or validate the isolated runtime and Builder account.
4. Select an available loopback port and write generated runtime settings.
5. Start the Builder server with identifiable logs and PID metadata.
6. Wait for bounded readiness and fail cleanly on startup errors.
7. Start the Builder client in explicit auto-login mode.
8. Monitor both processes and preserve useful logs.
9. Request clean server shutdown when the client exits.
10. Release the project lock after both processes stop.

Unexpected client termination must not kill the target private server or
delete the Builder workspace. Unexpected Builder-server termination must close
the client connection and explain where its logs are located.

## Release Packaging

World Builder 2 receives its own packaging entry point, product metadata, and
versioned artifacts, for example:

- `rsc-world-editor-v2-X.Y.Z-linux-x64.zip`
- `rsc-world-editor-v2-X.Y.Z-windows-x64.zip`
- `SHA256SUMS.txt`

Every archive contains `RELEASE-IDENTITY.json`. An updater must require exact
`productId` and `updateChannel` equality before offering an update. The only
allowed automatic source product is `rsc-world-editor-v2`; legacy
`rsc-world-editor-v1` is explicitly excluded, its final tag is recorded as
`rsc-world-editor-v1.03`, and legacy workspace migration is false.

The ambiguous `package-world-builder-release.sh` entry point now fails closed
instead of packaging current sources under the legacy identity. V2 packaging
uses `package-world-builder-v2-release.sh`, stages the reviewed signed-layered
package, launches with the layered profile, and remains production-locked
until layered export/import and final release validation are accepted.

The release must contain built jars and resources; users should not need Java,
Git, Ant, source code, or a development checkout. Both platform packages must
bundle a redistributable Java 17+ runtime using the same legal-file and version
checks as the player release. macOS is deferred from the first public release.

Packaging must verify:

- source commit and clean manager-main release state;
- client/server protocol agreement;
- required map and definition tooling;
- no generated workspace, Builder database, credential, backup, or log data;
- no public host/port defaults;
- loopback-only Builder configuration;
- launcher flags and platform scripts;
- required licenses, notices, and asset attribution; and
- archive contents and SHA-256 checksums.

The pending provenance/license details for any editor icons or other newly
added assets must be resolved before a public World Builder release.

## Implementation Phases and Gates

Only one phase should be treated as active at a time. Each phase receives a
useful checkpoint with its test evidence before the next phase begins.

| Phase | Status | Required proof before advancing |
| --- | --- | --- |
| 0. Contracts and fixtures | Complete | Approved plan, supported-layout fixtures, deterministic discovery/manifest tests |
| 1. Isolated Builder runtime | Complete | Loopback local launch, automatic Builder login, invulnerability, editor open, normal-mode regression coverage |
| 2. Workspace-owned persistence | Complete | Terrain/scenery/NPC saves change only working files; target hashes remain identical |
| 3. Deterministic export | Complete | Complete five-file bundle, manifest validation, repeatable hashes/content |
| 4. Transactional import and rollback | Complete | Dry-run, offline guard, failure rollback, successful restart, byte-exact undo |
| 5. Standalone release packaging | Active | Clean Linux/Windows artifacts, fresh-install smoke tests, attribution and checksum gates |
| 6. Visual acceptance and documentation | Pending | Owner-verified editing/import/revert flow and final user documentation |

### Phase 0: Contracts and fixtures

- [x] Approve the isolated local appliance architecture.
- [x] Define the five-file authored bundle and six target destinations.
- [x] Define import, rollback, credential, and loopback invariants.
- [x] Introduce a versioned project/export/receipt manifest schema.
- [x] Add representative supported private-server fixture layouts.
- [x] Add unsupported, ambiguous, missing-client, mismatched-archive, and
  incompatible-definition fixtures.
- [x] Implement read-only target discovery and effective-map selection.
- [x] Prove discovery has no writes on both success and failure.

Exit gate: fixture-backed discovery selects only known compatible targets and
produces a stable source manifest without mutating them.

Phase 0 evidence recorded on 2026-07-13:

- `python3 tests/myworld/test-world-builder-discovery.py`
- `python3 tests/myworld/test-standalone-layout.py`
- `./scripts/build-world-builder-tools.sh`
- Read-only discovery of the current repository with its expected content
  fingerprint produced a valid deterministic source manifest.

### Phase 1: Isolated Builder runtime

- [x] Add an explicit Builder-mode server configuration contract.
- [x] Enforce loopback-only binding before listeners start.
- [x] Create an isolated Builder SQLite database and provisioning service.
- [x] Generate and protect an install-local credential.
- [x] Add bounded client auto-login under an explicit Builder launch profile.
- [x] Refactor editor opening into a reusable server service.
- [x] Set Builder invulnerability and open the editor after authenticated login.
- [x] Add launcher readiness, PID, lock, shutdown, and log handling.
- [x] Preserve the normal login/editor/server behavior when Builder mode is off.

Phase 1 runtime-contract evidence recorded on 2026-07-13:

- `python3 tests/myworld/test-world-builder-runtime.py` (3 tests)
- `python3 tests/myworld/test-world-builder-discovery.py` (13 tests)
- `./scripts/build-server.sh`
- `./scripts/build-client.sh`
- Builder mode defaults off; enabled mode validates loopback-only binding,
  dedicated SQLite identity, one-player capacity, disabled registration, and
  editor/map requirements before server subsystems or listeners are created.
- The client profile defaults off, accepts only loopback endpoints and a valid
  install-local credential file, and leaves ordinary connection settings
  untouched when disabled.
- `python3 tests/myworld/test-world-builder-runtime-preparation.py` (3 tests)
- `python3 tests/myworld/test-world-builder-supervision.py` (1 process-level
  lifecycle test with two complete start/stop cycles and lock contention)
- A real prepared runtime booted on loopback from its copied world and clean
  SQLite seed, provisioned the Builder administrator, and reached network
  readiness without reading runtime state from the target tree.

Phase 1 owner acceptance recorded on 2026-07-13:

- The combined launcher prepared a fresh isolated runtime and launched it on
  `127.0.0.1:43645`.
- The owner confirmed login was skipped correctly, the visible identity was
  `Builder`, the character and world loaded in the correct states, and the
  authoritative world editor opened automatically.
- Server logs recorded successful normal authentication as the provisioned
  Builder administrator and the shared player-login/editor-session trigger.
- Normal mode remains the default and its connection, login, and editor paths
  are unchanged when the explicit Builder client/server flags are absent, as
  covered by `test-world-builder-runtime.py` and the production client/server
  builds.

Exit gate: a private visual test launches from a fixture root and arrives as
the invulnerable Builder with the editor open, while a normal private client
still shows the normal login screen.

### Phase 2: Workspace-owned persistence

- [x] Replace hard-coded terrain save paths with an explicit validated world
  edit storage context.
- [x] Create immutable source and mutable working trees.
- [x] Make the Builder runtime load only working terrain and overlays.
- [x] Route terrain backups into the Builder workspace.
- [x] Confirm scenery/NPC saves resolve through the working `CONFIG_DIR`.
- [x] Protect source and target paths against symlink/path traversal.
- [x] Display project, source revision, dirty, and saved state.

Phase 2 implementation evidence recorded on 2026-07-13:

- `WorldEditStorageContext` preserves the historical process-relative paths in
  ordinary mode and requires an explicit canonical workspace in Builder mode.
- Builder startup requires `<workspace>/working/server` as its actual process
  directory and validates both terrain copies, configuration ownership, path
  containment, and symbolic-link safety before world loading.
- Preparation publishes separate `source/` and `working/` trees. A bounded
  source inventory records present and absent authored inputs plus the selected
  configuration; every launch refuses source drift or untracked source files.
- Terrain loading/saving, its client mirror and backups, MyWorld scenery/NPC
  loading, and scenery/NPC saves now share the storage context.
- The desktop editor displays the Builder project folder and abbreviated source
  revision alongside its existing authoritative saved/unsaved status.
- `test-world-builder-runtime.py` covers opt-in defaults, storage ownership,
  missing-context refusal, traversal refusal, and symlink refusal.
- `test-world-builder-runtime-preparation.py` covers source/working separation,
  immutable target/source hash inventories, absent authored inputs, secret-free
  working preparation, and launch refusal after source mutation.
- `test-world-builder-working-persistence.py` performs two terrain, scenery,
  and NPC save cycles against one working tree and verifies accumulated reload
  state, synchronized terrain, workspace-owned backups, and unchanged source
  and target fixtures.
- A real project prepared from this checkout booted on `127.0.0.1:43655`; its
  server log named the terrain and MyWorld scenery/NPC inputs under the
  canonical `working/` tree.

Exit gate: repeated terrain, scenery, and NPC edits survive Builder restart,
and a before/after hash inventory proves the target private server did not
change.

Phase 2 owner acceptance recorded on 2026-07-14:

- The owner painted and saved three terrain tiles, closed the supervised client,
  and confirmed the same terrain after reopening the exact workspace.
- The owner then placed and saved one scenery object and one NPC. A second
  restart loaded 345 MyWorld scenery entries and 144 MyWorld NPC entries, one
  additional working entry in each file, and the owner visually confirmed both
  entities loaded properly.
- Both save/restart cycles ended with client and server exit code 0.
- The working server/client terrain hashes remained identical. The source
  snapshot and repository target retained their original terrain and overlay
  hashes throughout both edits and both restarts.

### Phase 3: Deterministic export

- [x] Validate working terrain and all entity overlays.
- [x] Produce the canonical five-file bundle.
- [x] Record source, compatibility, output, and change metadata.
- [x] Provide a readable change summary.
- [x] Reject incomplete or no-op exports appropriately.
- [x] Make published exports immutable and self-verifying.

Phase 3 evidence recorded on 2026-07-14:

- `python3 tests/myworld/test-world-builder-export.py` (5 tests)
- `python3 tests/myworld/test-world-builder-runtime-preparation.py` (4 tests)
- `./scripts/build-world-builder-tools.sh`
- Changed working input produced exactly the five canonical authored files,
  `manifest.json`, and `CHANGE-SUMMARY.txt`; a second export with identical
  input and provenance reused the same verified directory byte-for-byte.
- No-op input created no export. Malformed/duplicate JSON, an active project,
  source-snapshot drift, mismatched terrain mirrors, and tampered published
  output were refused without replacement or partial publication.
- The owner-confirmed working project exported three changed authored files:
  terrain, scenery locations, and NPC locations. The bundle terrain archive
  validated and its overlays parsed as 345 scenery locations and 144 NPC
  locations while the source snapshot and target remained unchanged.

Exit gate: two exports from identical working input have identical authored
files and semantic manifests, aside from explicitly documented creation or
identity metadata.

### Phase 4: Transactional import and rollback

- [x] Add dry-run and explicit-apply preview.
- [x] Add robust target-server-running detection and conservative refusal.
- [x] Add base-hash and compatibility revalidation.
- [x] Back up all existing/absent destination states.
- [x] Stage, parse, replace, and verify all target outputs.
- [x] Revalidate the v1 adapter's required configuration keys throughout the
  transaction; the adapter already requires both authored-data flags enabled,
  so it does not rewrite an unchanged configuration.
- [x] Add failure injection around every replacement stage.
- [x] Add automatic rollback for failed imports.
- [x] Add receipt-based `Undo Last Map Import`.
- [x] Refuse rollback over files changed after import.

Phase 4 implementation evidence recorded on 2026-07-14:

- `python3 tests/myworld/test-world-builder-import.py` (12 tests)
- `python3 tests/myworld/test-world-builder-export.py` (5 tests)
- `./scripts/build-world-builder-tools.sh`
- Dry-run holds a shared configuration lock and the target server-port lease,
  reports exact add/replace actions, and creates no target, backup, or receipt
  state. Apply uses an exclusive lock and repeats all revision checks.
- Successful imports preserve untouched files and configuration bytes, install
  matching server/client terrain, retain verified before-state backups, and
  publish a strict successful receipt.
- Injected failure after every replacement position restores every original
  target byte. A file absent before import is removed again during recovery.
- Undo safeguards the installed state, restores original bytes and original
  absence, and links a successful rollback receipt to the reverted import.
  Injected undo failures restore the imported state. Changed-after-import
  files, active target ports, target revision drift, unsafe paths, and pending
  transactions are refused.

Private import, target restart, visual verification, and undo/restart
acceptance remain required before this phase's exit gate is closed.

Phase 4 visual safety incident recorded on 2026-07-14:

- A disposable target import completed successfully and published receipt
  `20260714T143509009Z-43cbe8f7f5e8`. Its dry-run listed the matching server
  and client terrain replacements plus scenery-location and NPC-location
  replacements; apply verified the imported hashes and retained backups.
- Preparing a fresh isolated Builder workspace from that imported target also
  completed successfully. Launching its graphical client caused the host to
  become completely unresponsive, requiring a hard restart before visual
  inspection could begin.
- The hard restart cleared the `/tmp` workspaces and their local client/server
  logs. The prior boot journal contains no OOM kill, GPU reset, storage error,
  thermal fault, Java crash, or coredump near the event. It ends abruptly at
  `10:35:29` with a libinput/touchegg "system is too slow" warning and no clean
  shutdown record.
- Steam was actively rendering shortly before the event, and pre-launch
  diagnostics had shown several other Java server listeners. The Builder
  launcher permits up to 1536 MiB for its server and 2 GiB for its OpenGL
  client. Resource or GPU contention is plausible but unconfirmed; the exact
  cause cannot be established without persistent bounded diagnostics.
- No further graphical launch is authorized by this plan state. The next
  visual attempt must retain logs outside `/tmp`, inventory concurrent Java/GPU
  load, sample bounded process RSS/CPU, and use an agreed low-risk launch
  sequence. Phase 4 remains pending until import/restart and undo/restart are
  visually accepted without host instability.

Phase 4 launch-safety follow-up recorded on 2026-07-14:

- The surviving prior-boot journal and current hardware checks still show no
  OOM kill, Java fatal-error log, GPU reset, storage fault, thermal event, or
  exhausted RAM/VRAM. The abrupt journal end means the precise trigger remains
  unconfirmed.
- Earlier successful Builder logs prove the client was starting its primary
  LWJGL renderer in borderless fullscreen with vsync disabled while the JVM
  also enabled Java2D's separate OpenGL pipeline. A Steam game was rendering
  during the failed attempt. This combination is a plausible contention
  amplifier, but is not claimed as a proven root cause.
- The standalone Builder launch now uses a Builder-only safety profile:
  Java2D OpenGL is disabled, the actual LWJGL renderer remains enabled in a
  bounded window, and GLFW vsync is enabled. The normal game launcher and
  player graphics preferences are unchanged.
- The supervisor regression test asserts all three launch constraints. The
  supervisor, exporter, importer, preparation, and working-persistence tests
  pass, as do fresh server and client builds. The next visual workspace and
  its logs must remain on persistent storage and receive bounded external
  process monitoring during the acceptance run.

Phase 4 owner acceptance recorded on 2026-07-14:

- A persistent disposable project launched with the safety profile, and the
  owner painted one terrain tile, placed a Pine Tree at `(118, 652)`, placed a
  Unicorn at `(116, 648)`, saved, and closed cleanly. The working terrain
  mirrors agreed, scenery increased from 344 to 345 entries, and NPCs increased
  from 143 to 144 while the source snapshot and target remained unchanged.
- Export `export-180549cf5c8e3874` reported the three changed logical authored
  files. Import dry-run reported exactly four replacements: both terrain
  mirrors plus scenery locations and NPC locations. Apply transaction
  `20260714T150505201Z-04f1c74f54a1` retained verified before-state backups.
- A completely fresh Builder workspace prepared from the imported target
  loaded 345 scenery entries and 144 NPC entries. The owner visually confirmed
  the terrain edit, Pine Tree, and Unicorn were present after restart, then the
  client and server exited with code 0.
- Undo dry-run reported the inverse four restorations. Rollback transaction
  `20260714T150616964Z-c49f189f8550` restored the exact original terrain,
  scenery, and NPC hashes, retained a safeguard copy of the imported state,
  and linked to the now-reverted import receipt.
- A completely fresh post-undo workspace loaded the original 344 scenery and
  143 NPC entries. The owner visually confirmed all three edits were absent,
  then the client and server again exited with code 0.
- All three graphical runs retained persistent client/server logs and bounded
  host samples. Minimum available memory stayed above 19 GiB, maximum sampled
  discrete-GPU busy was 23%, and no swap, OOM, GPU reset, Java fatal error, or
  desktop stall occurred. This does not prove the earlier hang's precise root
  cause, but the Builder-only windowed/vsynced/single-GL-pipeline mitigation
  completed the full acceptance sequence without recurrence.

Phase 4 exit gate is satisfied.

Exit gate: imported terrain/scenery/NPC edits load after target server/client
restart, and rollback restores every original byte and original file absence.

### Phase 5: Standalone release packaging

- [x] Add a dedicated manager-only packaging script and packaging tests.
- [x] Package compiled client, server, launcher, definitions, assets, and docs.
- [x] Provide Linux-x64 and Windows-x64 launch/import/rollback wrappers.
- [x] Exclude all generated project and credential state.
- [x] Verify bundled Java metadata and legal files.
- [x] Verify asset redistribution and attribution.
- [x] Generate checksums and source provenance files.
- [ ] Test from clean extracted archives outside the repository.

Legacy v1 Phase 5 foundation evidence recorded on 2026-07-14:

- The historical `package-world-builder-release.sh` was manager-main-only,
  required clean
  published source, builds the client/server/tools, validates both bundled
  Java 17+ runtimes and legal files, checks protocol and required jar entries,
  stages separate self-contained Linux-x64 and Windows-x64 archives, and
  generates SHA-256 sums.
- Both archives contain the launch/import/undo scripts, compiled client,
  server, definitions, isolated seed database, tooling schemas, documentation,
  source provenance, licenses, notices, and asset records. Generated
  workspaces, credentials, databases, identities, endpoints, logs, exports,
  receipts, and backups are rejected from staging.
- Shell and Windows wrappers locate the parent private server, create or reopen
  the isolated workspace, and delegate import/undo confirmation to Java rather
  than parsing JSON in platform scripts. Existing projects read their recorded
  port from strict runtime metadata.
- `export-import` exports saved data, prints the authoritative preview, and
  requires exact `IMPORT` confirmation. `undo-latest-import` prints the
  authoritative rollback preview and requires exact `UNDO`; cancellation of
  either makes no target changes.
- The legacy release line was frozen at tag `rsc-world-editor-v1.03`. Its
  published artifact remains available but receives no further builds or
  automatic successor.

World Builder 2 release-line evidence recorded on 2026-07-30:

- The old ambiguous packager now refuses current source, naming the frozen v1
  endpoint and the separate v2 entry point.
- `package-world-builder-v2-release.sh` is manager-main-only, emits folder
  `Spoiled Milk World Builder 2`, archives under `rsc-world-editor-v2`, embeds
  the signed-layered package, and passes the exact layered launch flags.
- Each archive carries machine-readable identity restricting automatic
  upgrades to `rsc-world-editor-v2` itself and explicitly recording no legacy
  workspace migration. Production packaging remains fail-closed without the
  future `RELEASE-READY` gate.
- `python3 tests/myworld/test-world-builder-release.py` (6 tests) creates both
  archive types from a clean manager-main fixture, verifies archive contents,
  checksums, exclusions, Linux and Windows runtime files, substituted
  provenance, and runs every shell wrapper after extraction from outside the
  source repository using a captured Java boundary.
- `python3 tests/myworld/test-world-builder-import.py` (13 tests) now includes
  human-workflow preview, cancellation, confirmed import, cancelled undo, and
  byte-exact confirmed undo coverage. Supervisor, export, preparation, and
  working-persistence suites also pass with the packaged workflow changes.
- The editor icons are now attributed to Game-icons.net under CC BY 3.0, as
  confirmed by the project owner to match the prayer-icon source. A real
  clean-extraction launch/import/undo smoke test remains required after both
  redistribution-ready runtime inputs are available on manager main.
- The desktop editor shortcut layer defaults to keyboard-driven Builder mode:
  mode and dock actions, terrain field toggles/value focus, save, and repeated
  mode actions are available without chat capture. `Ctrl+Enter` deliberately
  switches between shortcut capture and ordinary chat/developer-command input;
  legacy AWT and OpenGL input use the same physical-key mapping.

Exit gate: a non-development machine or clean VM can perform the complete
workflow without Git, Ant, source code, or manual account creation.

### Phase 6: Visual acceptance and documentation

- [x] Document first launch, project reset/rebase, save/export, import, and undo.
- [x] Explain compatibility refusals and recovery receipts in plain language.
- [x] Visually verify terrain, floor, wall, roof, scenery, and NPC edits.
- [ ] Verify both legacy and OpenGL client presentation where supported.
- [x] Verify restart persistence before and after import.
- [x] Verify normal integrated-editor behavior remains available.
- [x] Record release limitations and supported server versions.

Owner acceptance recorded on 2026-07-14:

- The project owner accepted the integrated editor, isolated Builder launch,
  terrain/scenery/NPC editing and saving, import/restart persistence, exact
  rollback, and the desktop keyboard shortcut/chat-mode workflow.
- The latest shortcut acceptance used a fresh isolated OpenGL Builder client
  on port 43686. Automated legacy and OpenGL input/render guardrails pass, but
  a final real-archive clean-machine presentation check remains part of the
  release gate below.
- That acceptance applies to the frozen v1 artifact, `Spoiled Milk World
  Builder`, whose final release is `rsc-world-editor-v1.03`. The active layered
  product is `Spoiled Milk World Builder 2` on its independent
  `rsc-world-editor-v2` channel and requires its own final visual acceptance.

Exit gate: the project owner completes the intended workflow and explicitly
accepts the standalone release behavior.

## Test Strategy

### Deterministic unit and source tests

- Path canonicalization and root containment.
- Layout detection and ambiguity handling.
- Effective landscape selection.
- Manifest parsing, canonical serialization, and unknown-version rejection.
- SHA-256 and absent-file accounting.
- Builder configuration validation and non-loopback refusal.
- Credential generation without credential export/log leakage.
- Save-path resolution into the working tree.
- Import change planning and receipt generation.
- Rollback eligibility and changed-after-import refusal.

### Filesystem transaction tests

Use temporary private-server fixtures to cover:

- successful discovery with zero target writes;
- first project creation and subsequent reopen;
- server/client landscape mismatch;
- missing/empty scenery and NPC overlays;
- save and export while the target remains unchanged;
- no-op import;
- successful import of all six destinations and configuration;
- failure before backup, during staging, during each replacement, and during
  final verification;
- exact automatic restoration after each injected failure;
- successful undo including removal of files absent before import;
- refusal when any target changed after source capture or import; and
- transaction lock contention.

### Client/server integration tests

- Normal profile retains manual login.
- Builder profile cannot target a non-loopback server.
- Builder account is provisioned only in the isolated database.
- Auto-login still executes the normal authenticated initialization path.
- Builder receives admin authorization, invulnerability, and one editor
  session.
- Ordinary accounts cannot obtain Builder behavior by using the same username
  or a client property against a normal server.
- Logout/disconnect closes editor authority and reconnect restores it safely.
- Terrain, scenery, NPC, collision, and render state survive Builder restart.

### Release smoke tests

- Extract each archive into a clean supported fixture root.
- Launch without repository-relative dependencies.
- Edit and save each supported data family.
- Export and dry-run import.
- Import while the target is offline.
- Launch the target private server/client and inspect the result.
- Undo, restart, and verify exact original behavior.
- Confirm archives contain no credentials, databases, projects, logs, or
  backups.

### Required visual acceptance

Automated tests are not sufficient for the final release. Private visual
testing must confirm:

- login is skipped cleanly without a flash of unusable login UI;
- Builder spawns and remains visible/invulnerable;
- the editor opens in the intended safe mode;
- Build renderer mode and the compact/full editor controls work;
- terrain, embedded walls, scenery, and NPCs render and collide correctly;
- saved working changes survive Builder restart;
- imported changes appear in the target server and client; and
- rollback restores the prior visible and collision state.

## Logging and Diagnostics

Builder logs should make failures actionable without exposing secrets.

Record:

- Builder version and source commit;
- project/layout adapter and relative target identity;
- selected configuration and effective map mode;
- process lifecycle and readiness stages;
- file-relative paths, byte sizes, and shortened hashes;
- save/export/import/rollback transaction IDs and outcomes; and
- compatibility or refusal reasons.

Do not record:

- Builder passwords or complete credential files;
- private-server player database content;
- chat, account credentials, or network addresses beyond the fact that the
  endpoint was verified as loopback; or
- arbitrary absolute user paths in shareable export manifests.

Logs and transaction histories must be bounded by a documented retention or
manual cleanup policy. Backups should never be silently deleted merely due to
age while they are the only rollback source.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Builder overwrites the active server while editing | Separate source/working roots, validated storage context, target hash tests, path-containment tests |
| Shipped admin credential becomes remotely usable | Install-local random credential, loopback enforcement before bind, one-player Builder config, normal auth |
| Target changes after project creation | Base hashes in manifests; refuse import and provide a future rebase workflow |
| Partial multi-file import | Offline requirement, complete backup, pending receipt, deterministic staging/replacement, verified automatic rollback |
| Server/client terrain disagree | One canonical export archive copied to both destinations and hash equality required |
| Scenery/NPC changes are omitted | Five-file bundle contract and manifest completeness validation |
| Unknown OpenRSC fork looks compatible | Named versioned layout adapters and definition/runtime fingerprints |
| Importer damages configuration formatting | Narrow key-aware update, full backup, duplicate-key refusal, post-write configuration validation |
| Builder DB contaminates target accounts | Dedicated workspace SQLite database; database files excluded from exports/imports |
| Normal game inherits auto-login or admin behavior | Explicit Builder profile checks on both client and server plus normal-mode regression tests |
| Legacy editor auto-updates into an incompatible layered editor | Frozen v1 tag, distinct v2 product/update identity, archive and install names, exact-channel updater rule, no legacy workspace migration, and a retired ambiguous packager |
| User loses rollback data by moving/deleting Builder | Clear receipt/backup location messaging and optional future backup export |
| Release contains unlicensed assets | Packaging attribution gate and explicit provenance audit before publication |

## Decisions Recorded

- The standalone experience may use two local processes internally.
- The integrated editor remains part of the normal game.
- `Builder` is the fixed visible account name.
- The Builder account uses a separate database and install-local credential.
- Builder mode is strictly loopback-only.
- Editing and saving never mutate the target private server.
- Import and rollback are separate explicit actions.
- Terrain, scenery, and NPC changes ship together as one project bundle.
- The first release supports known Spoiled Milk layouts rather than arbitrary
  OpenRSC forks.
- Import requires the target server to be offline.
- Base-revision conflicts are refusals in the first release; no force import.
- Rollback requires the current target to match the installed receipt.
- The standalone release has its own packaging and versioned artifacts.
- The packed-map v1 line is frozen at `rsc-world-editor-v1.03`.
- The layered editor is World Builder 2, product/update channel
  `rsc-world-editor-v2`, and accepts automatic updates only from itself.
- V1 and v2 workspaces are intentionally incompatible; migration is never
  implicit.

## Open Product Decisions

These do not block Phase 0 and should be settled before their owning phase:

- Whether Build renderer mode starts enabled by default or remembers a
  Builder-project preference.
- First-run behavior when multiple compatible server configurations exist.
- Whether project rebase/reset is included before the first release or handled
  initially by creating a new project after exporting/backing up the old one.
- Backup retention UI and whether backups can be exported independently.
- Whether a later graphical project/import launcher replaces the initial
  double-click scripts.

## Progress Discipline

Keep this document current as implementation proceeds:

- Update the phase table and checklist in the same checkpoint that changes
  phase status.
- Record exact test commands and results in checkpoint or handoff notes.
- Do not mark a phase complete based only on source inspection.
- Require owner visual confirmation for client-facing phase gates.
- Stop and revise this plan if compatibility or persistence architecture
  changes materially.
- Keep standalone-only switches out of normal release defaults.
- Do not begin release packaging while import/rollback failure tests are
  incomplete.
- Do not publish the standalone artifact until asset provenance is complete.

## Completion Criteria

This plan can move to completed only when:

- a clean standalone package launches from a supported private-server root;
- the user arrives automatically as the authenticated, invulnerable Builder
  with the editor open;
- all supported edits save and survive in the isolated workspace;
- the target remains unchanged until explicit import;
- export includes complete terrain, scenery, and NPC state with verified
  manifests;
- import refuses unsafe/incompatible/running/conflicted targets;
- successful import survives target server/client restart;
- failed import restores the exact prior state;
- `Undo Last Map Import` restores exact prior files and behavior;
- normal client/server/editor behavior remains unchanged outside Builder mode;
- Linux and Windows release artifacts pass clean-extraction smoke tests;
- v1 installations cannot discover v2 as an automatic update and v2 refuses
  unidentified legacy workspace migration;
- licenses, notices, and asset attribution are complete;
- documentation is understandable without development knowledge; and
- the project owner visually approves the complete edit/export/import/revert
  workflow.
