# One-off World Builder 2 / Spoiled Milk presentation compatibility

Status: investigated, **not installed and not ready for use** (2026-09-16).

This is the user's explicit one-time exception to Core's World Editor project
boundary. It does not change that policy, add a supported product option, or
authorize editing either independent Editor repository. Work is staged in Core
ai-2; the installed application and all user data remain untouched.

## Exact inputs

- Core source: `fbbc79c5551328642336edf84f568ea80f5f001a`.
- Installation: `/home/justin/Core-Framework/World Builder 2`.
- Product/version: `rsc-world-editor-v2`, `0.8.0`.
- Editor source identity: `c4e14a82e25f72885e378c97cae4a2c940dce75e`.
- Packaged runtime source identity: `0355b2eccf1717a3235251d7b917f13b2fa454d6`.
- Packaged Builder client SHA256:
  `fccec5bb042594562e9496b8a3ac09e6c7029e5dbd73f0a2b8f26cfb0fb23479`.

## Why replacing the JAR is insufficient

The start script launches `world-builder-tools.jar desktop-launch`, with its
installation/runtime/target roots. Project launch uses an independent,
inventory-bound project-local client and server. It is not an ordinary client
shortcut connected to the user's server. Altering the packaged template alone
does not establish a safe upgrade for an existing project runtime.

Read-only `javap` inspection of the packaged classes and a fresh Core client
build established:

| Gate | Packaged client | Core client |
| --- | --- | --- |
| `Config.CLIENT_VERSION` | 10048 | 10052 |
| Placement admission | v3, v4, v5 blocked-void | v3, v4 |
| `CurrentBaseInstalledClient` | present | absent |

`AdaptiveWorldBuilderClientSession.validateConstants` checks the client version
and placement encoding before login. A direct Core replacement therefore rejects
the existing session. Do not merely spoof a version or weaken validation:
packet semantics and feature differences still require testing. The missing
current-base entrypoint is relevant to managed target/current-base launch, not
the adaptive editor's `orsc.OpenRSC` entrypoint; this local task should leave the
installed target runtime and current-platform payloads alone.

The inspected public APIs for `WorldBuilderClientProfile`,
`NativeLayeredTerrainPacketDecoder`, and `ProjectContentBundle` match. This is
encouraging, but not proof of semantic, UI, or wire compatibility. Core's current
inventory capacity, for example, is different from the packaged client's.

## Bounded implementation recommendation

Retain the exact packaged runtime's connection, definition binding, map format,
and packet behavior. Backport only the requested Core presentation into a local
client build based on that runtime, or implement an explicitly scoped local
compatibility adapter in a staged source copy after the wire differences are
audited. Never concatenate class files and call that a verified client.

Matching runtime source is not included in this task's allowed Core inputs;
only the package, its metadata/bytecode, and Core sources were inspected. A
matching, authorized source snapshot would make a reviewable presentation
backport preferable to reverse-engineering the packaged client. No independent
repository has been read or changed by this worker.

Before installation, demonstrate in an isolated scratch project:

1. Session proof, loopback login, definition and asset binding.
2. Terrain v5 blocked-void handling and native streaming.
3. Rendering, editor picking, keyboard/mouse and toolbar/UI controls.
4. Edit/save/close/reopen consistency with unchanged map semantics.
5. Desired Spoiled Milk visual settings/assets versus the original client.

No scratch server or real target application has been launched. Visual/input
acceptance remains untested. Runtime/client compatibility is not yet resolved.

## Installation / rollback requirements (not executed)

- Manager must review exact patch and test results before touching installation.
- Close only the local editor normally; public server must stay untouched.
- Record hashes and back up each exact application file changed, including
  launcher changes, to a uniquely named recoverable local backup. Never copy
  credentials, maps, databases, project registry, or workspaces into Git.
- The ordinary start script automatically invokes the updater unless
  `WORLD_BUILDER_SKIP_UPDATE=1`. The updater verifies every managed file using
  `PACKAGE-MANIFEST.sha256` and checks project/runtime state. A local modified
  application must have a clearly named opt-in launcher that disables that
  automatic update path. Do not falsify the publisher manifest or silently alter
  the public updater. Normal updating requires restoring the original files
  first; future versions require re-auditing compatibility.
- Existing project runtime inventories must not be edited opportunistically to
  hide a changed executable. Review an explicit project-safe local launch method
  before installation. No target runtime upgrade/import is part of this task.
- Rollback restores only the changed application files to their recorded hashes
  and removes/disables the opt-in shortcut. Preserve all map/project state.

## Repeatable first gate

From the worker checkout:

```sh
./scripts/build-client.sh
python3 tests/myworld/test-local-world-builder-client-audit.py
python3 scripts/audit-local-world-builder-client.py \
  '/home/justin/Core-Framework/World Builder 2' \
  Client_Base/Open_RSC_Client.jar
```

The audit is read-only and launches `javap`, not either application. Exit 2 means
a direct replacement is blocked (expected for the inputs above); exit 1 means
inspection failed closed; exit 0 means only the inspected metadata matches and
runtime validation is still required. Three unit tests passed. Core client
compilation produced a fresh local JAR; binaries/assets are not committed.
