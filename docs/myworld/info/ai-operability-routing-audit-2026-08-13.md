# AI Operability and Change-Routing Audit — 2026-08-13

## Purpose and conclusion

This is a documentation-only audit of whether an AI worker can safely locate
authoritative code, ownership, and verification for a change. It evaluates
navigation and change safety, not conventional file-size thresholds.

The repository is workable for AI-assisted changes when a task begins from the
maintained roots, follows the authoritative Ant build, and treats configuration
and plugin discovery as runtime authority. The main demonstrated friction is
not that a single source file must be split: it is that a broad text search
returns multiple products, same-named classes, generated/data surfaces, and
dynamic routes. The existing [Code Navigation Guide](code-navigation.md) is
the starting point; the matrices below make its routing advice task-specific.

No production refactor is recommended from this audit. Small documentation and
search-discipline improvements are enough for the demonstrated cases. A Java
language server or local Ctags file can speed declaration lookup, but neither
can replace the dynamic-route checks listed here.

## How to use this document

1. Choose the row for the player-visible task, then start at its authoritative
   source/data owner rather than a repository-wide filename search.
2. Run the focused tests in the second matrix before a broader product gate.
3. Read the listed dynamic/compatibility caveat before declaring a search result
   unused or authoritative.
4. For an unfamiliar boundary, use `rg --files <root>` first, then `rg -n` with
   explicit maintained roots. Do not search the entire checkout by default.

## Task-to-owner matrix

| Task area | Primary authority | Adjacent owner(s) to inspect | Dynamic / secondary surface that changes the answer |
| --- | --- | --- | --- |
| UI and client interaction | `Client_Base/src/orsc/mudclient.java`, `Client_Base/src/com/openrsc/interfaces/` | `Client_Base/src/orsc/graphics/gui/`, client packet code, `PC_Client/src/orsc/` for desktop-only window behavior | Authentic-client packet/layout limits, saved settings, renderer mode, and client cache assets can make a correct UI edit appear ineffective. |
| Renderer / presentation | `Client_Base/src/orsc/graphics/three/`, `Client_Base/src/orsc/graphics/two/`, `PC_Client/src/orsc/` | `mudclient`, renderer settings/profile classes, `dev/myworld/assets/` | OpenGL and software presenter fallbacks are active compatibility paths; asset override selection is runtime/configuration driven. |
| Combat | `server/src/com/openrsc/server/model/combat/`, `server/src/com/openrsc/server/event/rsc/impl/combat/` | `model/entity`, status content, `server/plugins/.../skills/`, `server/test/` combat fixtures | Attacks, projectiles, effects, plugin hooks, NPC definitions, and packets divide authority. Use the combat characterization gate; do not infer policy from one HP mutation. |
| Equipment and stats | server `model/container/`, `external/`, player/entity classes | client entity definitions, equipment UI, combat calculation | Numeric item definitions and appearance/palette data cross server/client products; equipment effects may be status/combat or plugin driven. |
| NPCs and movement | `server/src/.../model/entity/npc/`, `model/world/` | NPC definitions/locations, interaction plugins, combat events | NPC locations and boundaries are definition/map driven. Plugin trigger selection and map layer are not visible in a class-only search. |
| Quests and dialogue | `server/plugins/com/openrsc/server/plugins/*/quests/` | dialogue/menu triggers, `server/src/.../plugins/`, quest/player state | Plugin discovery and numeric NPC/item/location registration are authoritative; the `authentic`, `custom`, and `retro` product folders are intentional variants, not interchangeable defaults. |
| Definitions and IDs | `server/conf/server/defs/`, `server/src/.../external/` | `Client_Base/src/com/openrsc/client/entityhandling/`, generator manifests | JSON/XML and generated overrides are runtime authority. Verify the generator/source manifest before editing checked-in output or assuming an ID has one definition. |
| Maps, terrain, and locations | server `model/world/`, map/region configuration and layered data | client `orsc/graphics/three/World.java`, scene/streaming classes, layered-map tools | Coordinates include world-space and signed layer semantics. The independent World Editor/runtime is explicitly outside this repository's Core ownership. |
| Persistence and player state | `server/src/.../database/`, player cache/state classes, `login/` | configuration, migrations/patches, social/content services | Database backend, cache keys, migration order, and login lifecycle decide reachability. Do not treat a raw key search as proof of safe removal. |
| Plugins and extensions | `server/src/.../plugins/`, `server/plugins/` | `extensions/`, configuration/profile selection, plugin tests | Discovery, trigger registration, defaults, and reflection make text references incomplete. Core and plugin artifacts must be compiled together. |
| Networking and packets | `server/src/.../net/rsc/`, `Client_Base/src/orsc/net/` | player `ActionSender`/handlers, packet structures, compatibility framing | Client version and packet-shape compatibility make a server-only or client-only search unsafe. Preserve protocol prefix/field order unless both owners and focused checks agree. |
| Builds, packaging, and releases | `scripts/build-client.sh`, `scripts/build-server.sh`, bundled Ant files | `server/build.xml`, `Client_Base/build.xml`, release scripts, artifact tests | Server Ant is production authority. `server/build.gradle` is retained secondary/non-authoritative until parity is demonstrated; never choose it merely because a Gradle file is present. |

## Source-path-to-test matrix

The `tests/myworld/test-*.py` checks are focused repository characterization
tests. They complement, rather than replace, compiled runtime tests and private
visual checks where a client presentation change is involved.

| Changed source/data path | Start with focused checks | Broader required gate / notes |
| --- | --- | --- |
| `Client_Base/src/orsc/mudclient.java`, `com/openrsc/interfaces/` | Feature-specific `tests/myworld/test-client-*.py`, `test-*-ui.py`, `test-*-layout.py`, or `test-spellbook-text-layouts.py` as applicable | `./scripts/build-client.sh`; private client verification for input, persistence, or visual behavior. |
| `Client_Base/src/orsc/graphics/three/`, `graphics/two/`, `PC_Client/src/orsc/` | Relevant `test-renderer-*.py`, `test-opengl-*.py`, `test-client-render-*.py`, and renderer extraction tests | Client build plus private OpenGL and required software-fallback checks. |
| Server combat model/events | `./server/test_combat`; `tests/myworld/test-combat-scenarios.py`, `test-combat-runtime-invariants.py`, and the precise family test | `./scripts/build-server.sh`; use the 52-scenario combat gate where the combat plan requires it. |
| Equipment, item effects, or stats | Matching `test-*-equipment*.py`, `test-*-staff*.py`, `test-*-power*.py`, and item-definition test | Server build; client build too whenever stat display, item definitions, or appearance changes. |
| NPC behavior, movement, poison, or spawns | Matching `test-npc-*.py`, `test-path-queue-regressions.py`, `test-npc-poison-death-lifecycle.py` | Server build; combat gate if damage/aggro/attribution changes. |
| Quest/dialogue/plugin interaction | The named feature test plus `test-quest-system.py`, `test-quest-choice-audit.py`, or route test | `compile_core`, `compile_plugins`; invoke precise Ant fixture targets when defined (for example Monster Slayer route/state targets). |
| Definitions, generated definitions, or content IDs | `test-definition-override-loading.py`, `test-content-item-resolution.py`, `test-generator-scripts.py`, feature catalog test | `./scripts/build-server.sh`, which validates generator inputs; client build if parity data is changed. |
| Layered maps, terrain, locations, doors, teleporting | Exact `test-layered-*.py` slice/feature test, `test-landscape-client-server-sync.py`, and passage/location check | Server and client build; private layer-transition visual verification when rendering/streaming is touched. |
| Persistence, login, database, or player cache | `test-player-data-integrity.py`, `test-cache-numeric-coercion.py`, feature state tests | Server build and backend-safe private startup only when the task explicitly needs it. Never use public data for experimentation. |
| Plugin discovery/configuration | `test-plugin-default-fallback.py`, `test-legacy-plugin-adapter-parity.py`, `test-server-r2-extension-registry.py` | Core + plugin compilation and the target plugin fixture. |
| Packets/networking | `test-packet-shape-guards.py`, `test-client-incremental-packet-frames.py`, relevant client/server route test | Both products build; retain authentic-client compatibility behavior. |
| Build/release scripts | `test-server-build-source-of-truth.py`, `test-client-fat-jar-archive.py`, `test-standalone-layout.py`, relevant release test | Execute the affected wrapper and inspect its intended artifact, not only a raw compiler target. |

Use `rg -n --glob 'test-*.py' '<feature symbol or stable ID>' tests/myworld`
to find the exact named fixture. The layered-map suite deliberately has many
slice files: select the named slice(s) from the current plan rather than running
all similarly named files without a reason.

## Inventory of non-obvious surfaces

| Surface | Why ordinary navigation is insufficient | Required confirmation |
| --- | --- | --- |
| `server/plugins/` and trigger handlers | Runtime plugin discovery, defaults, and trigger registration may invoke a class without a direct lexical caller. | Inspect plugin handler/trigger route and compile `core.jar` with `plugins.jar`. |
| `server/conf/server/defs/` and server configuration | Definition IDs, locations, item/NPC behavior, and backend/profile choices can override a Java assumption. | Locate the definition/configuration key and its loader; validate generator source where applicable. |
| `tools/generators/`, `tools/world-builder/` | Some checked-in data is produced/validated through a manifest or tool. | Read tool instructions/manifests and run the focused generator validation; do not hand-edit derived output by guesswork. |
| `Client_Base/Cache/`, `dev/myworld/assets/`, remaster override paths | Runtime cache, archive order, fallback, and per-asset loading decide what the player sees. | Follow the asset plan and loader; test both override and default fallback. |
| `server/build.gradle` | It describes a dependency graph but is explicitly not production build authority. | Use `scripts/build-server.sh`/bundled Ant unless parity is separately proven. |
| Authentic, custom, and retro plugin folders | Similar class/function names represent deliberately different content profiles. | Confirm selected server profile/plugin route before copying or editing behavior. |
| Compatibility facades and legacy settings | A surface can remain active for software presentation, older client framing, database backend, or profile compatibility. | Consult the compatibility/prune proof and prove runtime selection before removal or reuse. |
| Reflection, persistence/cache keys, packets, numerical IDs | These references may not be discoverable as Java symbol references. | Search code plus JSON/XML/configuration/data and exercise a focused runtime route. |
| World Editor and runtime worktrees | They have overlapping domain vocabulary but are separate projects with independent manager/workspace ownership. | Do not include them in Core searches or builds; route work to their own project. |

## `mudclient` navigation assessment

`Client_Base/src/orsc/mudclient.java` is 29,300 lines, but the relevant question
is whether a particular scenario has a stable entry point and verification path.
It does for the representative scenarios below. Recent completed extractions
already establish named seams for renderer settings, profiles, legacy scaling,
external assets, scene instances, and predictive terrain preloading.

| Representative change | Safe first route | Why a broad edit is risky | Recommendation |
| --- | --- | --- | --- |
| Add or alter a renderer setting | `RendererSettingsPanel`, `RendererProfileApplier`, and `LegacySoftwareScalingSettings`, then their call sites in `mudclient` | The host still applies settings, preserves UI state, and selects renderer mode. Changing only a draw/menu block can bypass persistence or fallback. | Use the existing named seams and renderer tests; no new extraction justified. |
| Change an external/remastered sprite behavior | `ClientExternalAssetLoader`, asset plan, loader call sites around `mudclient` | Cache/archive fallback and toggle persistence determine whether a sprite is visible. | Start with loader and asset tests; private verify override and fallback. |
| Diagnose a terrain/scene issue | `ClientSceneInstanceStore`, `PredictiveTerrainPreloader`, client `World`, and server/map route | Scene identity, preloading, layer transition, and renderer presentation are separate stages. | Follow the layered-map test name/plan; do not use the line count as a reason to edit the central client. |
| Alter a gameplay UI panel (spell/guide/equipment) | Interface class/definition source first, then the narrow `mudclient` draw/input dispatch | The client centralizes dispatch, but definition/packet/stat source can be server-owned. | Search by UI label, stable ID, and packet/definition owner; run layout and parity test. |
| Change window lifecycle | `PC_Client/src/orsc/` presenter/window classes before `mudclient` | Desktop presentation is a separate product boundary and must retain software/OpenGL compatibility. | Use desktop lifecycle tests and private close/fallback confirmation. |

Conclusion: `mudclient` remains a high-context coordinator, but these scenarios
are navigable with the named classes and tests. Continue documenting a new
seam when one is extracted; do not refactor it merely to reach a line-count
target. Escalate an extraction only if a future change cannot be routed through
one existing boundary without touching unrelated renderer, UI, and world state.

## Duplicate-name and multi-product search hazards

The maintained products contain same-named Java files with different packages
and ownership. For example:

| Filename | Distinct maintained locations | Safe search convention |
| --- | --- | --- |
| `World.java` | `Client_Base/src/orsc/graphics/three/World.java`; `server/src/com/openrsc/server/model/world/World.java` | Always name the product root and package in search results. |
| `EntityHandler.java` | `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`; `server/src/com/openrsc/server/external/EntityHandler.java` | Search exact package/import plus source root. |
| `Item.java` | Client entity-handling instance; server container model | Search `new Item` only inside the intended product and inspect import. |
| `Skills.java` | Server constants and server player model | Search qualified method/constant, not filename alone. |
| `Tile`, `TileDef`, `SpellDef`, `NPCDef`, `ShopStruct`, packet structs | Two maintained products share these names | Filter with `--glob '*.java'` and explicit root; do not assume the first result owns the protocol or definition. |

Further multi-product hazards are `PC_Client` versus `Client_Base`, the
authentic/custom/retro plugin profiles, `server/test` compiled fixtures versus
production source, and the separate World Editor repositories. A repository-
wide `rg` is useful only after the maintained-root search has established the
candidate owner.

## Demonstrated AI-friction risks and priority

| Priority | Demonstrated risk | Evidence / effect | Practical mitigation |
| --- | --- | --- | --- |
| P0 | Selecting Gradle because it is present, instead of production Ant | `server/build.gradle` is explicitly secondary while wrappers and `server/build.xml` produce shipped core/plugin artifacts. | Keep the build-authority warning in navigation docs; begin server work with `scripts/build-server.sh` and relevant Ant target. |
| P0 | Missing runtime plugin/configuration authority | Plugin discovery, JSON/XML definitions, profiles, packet IDs, and cache keys are not dependable Java-reference graphs. | Use the task matrix, inspect loader/trigger/config route, and compile/test the runtime path. |
| P0 | Cross-product same-name edits | `World`, `EntityHandler`, `Item`, and many definition/struct names occur in both client and server roots. | Require an explicit source root and package in search commands and review import lines. |
| P1 | Choosing a broad or wrong focused test | Hundreds of feature tests, especially numbered layered-map slices, make filename guessing unreliable. | Locate tests by feature symbol/plan and record exact focused command in the task brief. |
| P1 | Treating assets/generated data as direct source | Asset fallback and generator/definition pipelines make a source-file-only patch incomplete. | Read loader/manifest, validate the pipeline, and verify fallback/parity. |
| P1 | `mudclient` central dispatch obscures responsibility | It contains UI, packet, scene, settings, and presentation call sites, even after intentional extractions. | Begin from the named extracted owner; enter `mudclient` only for integration calls. |
| P2 | Stale local declaration index | Ctags is absent in the audited environment and any tags file becomes stale after branch/root changes. | Use `rg` as baseline; optionally run `scripts/generate-code-tags.sh` locally after changes. Never commit it. |

The following are **not currently demonstrated reasons for architecture work**:
replacing Ant, introducing a mandatory persistent Java index, generating a full
dependency graph, moving packages, or splitting `mudclient` further solely for
size. Each would add maintenance/staleness risk without solving the dynamic
ownership checks above.

## Recommended small improvements

1. **Adopt this routing matrix in task briefs (P0).** Every implementation
   brief should state the primary owner, dynamic caveat, focused test, and
   broader gate. This is the least-cost protection against the actual hazards.
2. **Add narrowly scoped README notes only when a boundary changes (P1).** The
   next useful locations are `server/plugins/` (discovery/profile conventions)
   and `tools/generators/` (source-versus-derived convention). Do not create a
   generic README forest preemptively; keep the durable route map here and in
   `code-navigation.md`.
3. **Use constrained search as the project convention (P1).** Prefer
   `rg --files <owner-root>` followed by `rg -n --glob '*.java' '<symbol>'
   <owner-root>`. Expand one adjacent owner at a time, and search data/config
   separately for numeric IDs and keys.
4. **Keep Ctags optional (P2).** `scripts/generate-code-tags.sh` already checks
   for Universal Ctags and writes only ignored `output/navigation/tags`; do not
   install packages or add a tracked index. A language server can be evaluated
   later for a specific editor workflow, not imposed as repository authority.

## Verification performed for this audit

- Inventoried maintained source roots, production Ant targets, plugin folders,
  and all top-level focused test filenames.
- Confirmed duplicate class-name examples in maintained client/server roots.
- Confirmed `mudclient` uses the documented extracted routing seams and remains
  29,300 lines at this audit point.
- Documentation link/content validation is recorded with this branch handoff.

## Related references

- [Code Navigation Guide](code-navigation.md)
- [Testing quick reference](testing-quick-reference.md)
- [Server build source of truth](server-build-source-of-truth.md)
- [Compatibility and prune proof](compatibility-and-prune-proof-b11.md)
- [Code-size and indexing audit](../in-progress-work-plans/code-size-and-indexing-audit-2026-08-13.md)
- [Code cleanup and modularization plan](../in-progress-work-plans/code-cleanup-and-modularization-plan.md)
