# One-off World Builder 2 / Spoiled Milk presentation compatibility

Status: additive local presentation adapter implemented and tested for review;
**not installed**, interactive full-world acceptance remains pending (2026-09-16).

This is the user's explicit one-time exception to Core's World Editor project
boundary. It does not change that policy, add a supported product option, or
authorize editing either independent Editor repository. Work is staged in Core
ai-2; the installed application and all user data remain untouched.

## Implemented solution

After the user authorized read-only access to the exact runtime source commit,
inspection found that the packaged runtime already includes Spoiled Milk's
OpenGL rendering pipeline and modern presentation controls. Its supervisor
deliberately passes `openrsc.worldBuilderPreservationUi=true` and forces 21
rendering/UI properties off. `RendererRuntimeDefaults` and `OpenGLShaderProgram`
are identical to Core's versions. The initial direct-client-swap blockers below
are avoided entirely, not bypassed.

The optional **Start Spoiled Milk World Builder.sh** launcher loads a tiny Java
startup agent that changes only those presentation properties in the exact
hashed adaptive loopback client, before the unchanged renderer initializes.
The adapter registers no class transformers and replaces no client classes.
The standard launcher, editor JAR, client JAR, server, package manifests,
definition/asset binding, project inventories and map protocol remain unchanged.
The agent does nothing in the launcher/server JVMs and rejects a changed client
or non-loopback adaptive client. It preserves existing user presentation
preferences; absent overrides, the normal default is Remaster. If an existing
project has saved Classic preferences, choose Remaster in the now-available
renderer controls (F6). It does not silently reset those preferences.

This restores the packaged Spoiled Milk-derived renderer/UI, **not** a complete
10052 gameplay-client transplant. Newer Core gameplay UI, bank/inventory changes,
NPC animation additions, content assets, and renderer performance changes are
not imported. Terrain/object shaders are the same, but this is not a promise of
pixel-identical current-game scenes with different content or tuning. Project
content remains its existing authenticated source. Further requested visual
differences should be reviewed deliberately, not papered over by replacing JARs.

### Build and verify

```sh
python3 scripts/build-local-world-builder-presentation.py \
  '/home/justin/Core-Framework/World Builder 2' \
  --output output/local-world-builder-compat/presentation-reviewed
python3 tests/myworld/test-local-world-builder-presentation.py \
  '/home/justin/Core-Framework/World Builder 2' \
  output/local-world-builder-compat/presentation-reviewed --render
```

Use a new output directory on every build. The bundle contains only the optional
launcher and `local-spoiled-milk-client/` (agent and pins). No external sources,
client assets, secrets or application binaries are committed. `--render` uses a
hidden synthetic OpenGL scene; it never starts the game or connects to a server.

Verification passed:

- Real packaged class checks: preservation mode can be disabled; 960x540 logical
  surface, F6 renderer control, icon spellbook and enhanced-sprite option return;
  10048 version and v5 placement constants remain intact.
- Real packaged JRE `--dry-run` executes the agent without invoking client main.
- Changed-client rejection; launcher/server scope; non-loopback rejection.
- Launcher paths with spaces, existing Java-wide environment rejection,
  updater-skip isolation, and application drift refusal.
- Hidden RX 6700 XT OpenGL context compiled projected and resident shaders and
  rendered/read back a synthetic triangle (nonzero green channel; the existing
  day/night tone changes its exact value).
- Exact runtime source tests: keyboard shortcuts (2 tests), void picking,
  widescreen UI hit exclusion, and OpenGL modifier forwarding.
- Installed client hash unchanged after testing.

These do not constitute full end-to-end map editing acceptance. Before calling
the local installation fully accepted, use a disposable project to check terrain,
objects, NPCs, dock/brush selection, resize/input, save and reopen. No real
project was launched or changed during these probes.

### Reviewed additive installation and rollback

Manager installation is pending review. Confirm no local editor/update is active,
then copy the bundle's two additions into the installation **only if neither
destination exists**. Do not overwrite any existing file. Original launcher and
all packaged files are their own intact rollback; record their unchanged hashes.
Use the optional launcher for Spoiled Milk presentation. Use the original
launcher to return to preservation presentation; ordinary settings saved through
client controls remain user-owned.

The optional launcher sets `WORLD_BUILDER_SKIP_UPDATE=1` only in its child
environment and checks pinned application/adapter hashes every time. No public
update machinery is edited. If the normal updater later changes the package,
the optional launcher fails closed until re-reviewed. The normal updater's
inventory checks remain intact. Reverting the customization requires only
retiring the optional launcher and its dedicated directory, not restoring or
rewriting maps, source baselines, databases, or project inventories.

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

## Initial implementation assessment (superseded by additive solution above)

Retain the exact packaged runtime's connection, definition binding, map format,
and packet behavior. Backport only the requested Core presentation into a local
client build based on that runtime, or implement an explicitly scoped local
compatibility adapter in a staged source copy after the wire differences are
audited. Never concatenate class files and call that a verified client.

At the initial milestone, only the package and Core sources were authorized.
The user subsequently approved reading the exact matching runtime source. A
read-only `git archive` of client source/tests was staged under ignored worker
output; no independent checkout was changed or built. That source inspection
led to the narrower implementation above.

Before installation, demonstrate in an isolated scratch project:

1. Session proof, loopback login, definition and asset binding.
2. Terrain v5 blocked-void handling and native streaming.
3. Rendering, editor picking, keyboard/mouse and toolbar/UI controls.
4. Edit/save/close/reopen consistency with unchanged map semantics.
5. Desired Spoiled Milk visual settings/assets versus the original client.

No scratch server or real target application has been launched. Full-world
visual/input acceptance remains untested. This candidate retains the exact
original runtime/client rather than attempting the incompatible replacement.

## Initial replacement precautions (no replacement performed)

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
