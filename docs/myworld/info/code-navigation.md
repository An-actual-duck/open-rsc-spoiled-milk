# Code Navigation Guide

This guide is a quick map for Spoiled Milk manager and worker sessions. It is
navigation help, not build authority: use the documented build/test gates and
the owning plan before changing a subsystem.

## Maintained roots

| Product | Source / data root | Notes |
| --- | --- | --- |
| Custom client | `Client_Base/src/` | Main maintained desktop client. Client Ant build is `Client_Base/build.xml`; `scripts/build-client.sh` is the normal entry point. |
| Desktop launcher/presenter | `PC_Client/src/` | OpenGL desktop presentation and launcher-side integration; it is packaged with the client rather than a separate server product. |
| Server core | `server/src/` | Production core Java. Bundled Ant and `server/build.xml` are authoritative. |
| Server plugins | `server/plugins/` | Dynamically discovered gameplay/plugin sources compiled against `core.jar`; they are not safely discoverable by a Java text index alone. |
| Server tests | `server/test/` | Focused compiled fixtures. The combat gate is invoked through `server/test_combat`. |
| Definitions/configuration | `server/conf/server/defs/`, `server/conf/server/` | JSON/XML/YAML data, locations, and settings which are runtime authority for many IDs and behavior choices. |
| Client assets/content | `Client_Base/Cache/`, `dev/myworld/assets/` | Cache/runtime inputs and source artwork. Follow asset plans; do not infer an asset's runtime route from a filename alone. |
| Code generators | `tools/generators/`, `tools/world-builder/` | Generated definition/override inputs. Read generator manifests and validation scripts before editing generated outputs. |
| Tests and automation | `tests/myworld/`, `scripts/` | Repository guards, build wrappers, static analysis, release validation, and workspace controls. |

The independent World Editor and its runtime have separate repositories and
workspaces. They are not Core source roots.

## Authoritative build outputs

| Command / Ant target | Product / artifact |
| --- | --- |
| `./scripts/build-client.sh` | Maintained client build (`Client_Base/Open_RSC_Client.jar`). |
| `./scripts/build-server.sh` | Runs server generator validation, `compile_core`, `compile_plugins`, and Ant classpath/artifact validation. |
| `server/build.xml:compile_core` | `server/core.jar`, including the server runtime dependencies. |
| `server/build.xml:compile_plugins` | `server/plugins.jar`. |
| `./server/test_combat` | Compiled combat characterization gate. |
| `source scripts/lib/myworld-common.sh && myworld_ant_build <target>` | Focused Ant target execution, for example `test_monster_slayer_contact_routes`. |

`server/build.gradle` is secondary/non-authoritative until documented parity is
proved. See [server build source of truth](server-build-source-of-truth.md).

## Ownership maps worth opening first

- [Code cleanup and modularization plan](../in-progress-work-plans/code-cleanup-and-modularization-plan.md): completed B01–B11 and client extraction boundaries.
- [Code-size and indexing audit](../in-progress-work-plans/code-size-and-indexing-audit-2026-08-13.md): current large-file priorities and stop conditions.
- [Server R2 plan](../in-progress-work-plans/server-r2-plan.md): server composition, layered-map, build, diagnostics, persistence, and plugin boundaries.
- [Combat refactor audit](../in-progress-work-plans/classic-scape-combat-refactor-audit-2026-08-05.md): combat authority and characterization constraints.
- [Compatibility/prune proof](compatibility-and-prune-proof-b11.md): retained compatibility surfaces and proof-before-removal rules.
- [Testing quick reference](testing-quick-reference.md): routine focused and product gates.
- [AI operability routing audit](ai-operability-routing-audit-2026-08-13.md):
  task-owner and source-test routing matrices, duplicate-name hazards, and
  documented AI-friction priorities.

## Limits of text navigation

`rg`, Ctags, Git history, and compiler errors find declarations and lexical
uses. They do **not** prove runtime reachability or ownership for:

- Plugin discovery and trigger routing (`server/plugins/`, plugin registration,
  default handlers, and reflective loading).
- Reflection, configuration keys, persistence/cache keys, packets, JSON/XML
  definition references, or item/NPC numeric IDs.
- Generated overrides and data whose source is a manifest/generator rather than
  the checked-in result.
- Compatibility paths selected by client version, platform, renderer fallback,
  database backend, or feature flag.

Treat `rg` results as an inventory. Confirm dynamic routes with the owning
definition/configuration, build target, focused runtime fixture, and—where
applicable—private client verification.

## Practical `rg` and Git examples

Run these from the repository root:

```bash
# Declaration plus all maintained Java uses of a type or method name.
rg -n --glob '*.java' 'MonsterSlayerShopService|redeem\('

# Restrict a lookup to server runtime and plugins.
rg -n --glob '*.java' 'blockOpNpc|TalkNpcTrigger' server/src server/plugins

# Locate a config/definition key across code and data.
rg -n 'influence_instead_qp|INFLUENCE_INSTEAD_QP' server Client_Base

# Identify packet or protocol use without assuming a result is authoritative.
rg -n --glob '*.java' 'PRODUCTION_START|OpcodeIn' server/src Client_Base/src

# Find the history that added or removed a precise behavior string.
git log -S 'Monster Slayer contact plugin routes' -- server

# Find commits touching a pattern, then inspect the named files.
git log -G 'LayeredCoordinateParityObserver' --stat -- server/src

# List largest maintained Java files (not definitions or generated output).
find Client_Base/src PC_Client/src server/src server/plugins -type f -name '*.java' -print0 \
  | xargs -0 wc -l | sort -nr | head -30
```

Prefer `rg --files` before opening a guessed path. Use `git log -- <path>` to
identify current owners and prior verification before proposing a refactor.

## Optional local Ctags index

Run:

```bash
./scripts/generate-code-tags.sh
```

The helper checks for Universal Ctags and writes
`output/navigation/tags`. `output/` is ignored by Git, and release scripts
package explicit client/server inputs into `output/releases/`; the navigation
index is not an input or release artifact. Rebuild it after switching branch or
changing source roots. It is intentionally local and disposable.

The tags file is useful for declaration/line navigation. It is not a replacement
for the build, dynamic-plugin checks, or a semantic Java language server.
