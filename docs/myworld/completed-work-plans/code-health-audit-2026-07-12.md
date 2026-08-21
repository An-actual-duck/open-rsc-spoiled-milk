# Repository Code-Health Audit — 2026-07-12

Status: **audit and B01-B11 follow-up sequence complete.** Future structural
changes require a new focused justification; this file is retained as the
historical evidence and acceptance record.

## Purpose And Scope

This audit records the current code-health risks in Spoiled Milk and turns them
into focused follow-up branches. It is an evidence pass, not authorization for
a mass cleanup. In particular, this branch does not refactor large classes,
remove compatibility paths, change formatting, or delete suspected dead code.

The audit was reconciled against published `main` commit `f84bd6664` on
`docs/code-health-audit-plan`. It covers the active desktop client, shared
client, server core, dynamically loaded server plugins, scripts, tests, build
definitions, and explicitly archived material. It complements the earlier
[`code-cleanup-and-modularization-plan.md`](../in-progress-work-plans/code-cleanup-and-modularization-plan.md)
with current measurements, band-aid and dead-code analysis, static-analysis
recommendations, and an ordered implementation plan.

## Implementation Progress — Updated 2026-07-16

This ledger preserves the audit's original evidence while tracking its ordered
follow-up branches against published `main` commit `ecfc3b35d`.

| Branch | Status | Published merge | Result |
| --- | --- | --- | --- |
| B01 — `fix/client-spell-display-metadata` | Complete | `c923ed8c9` | Added one stable elemental spell-display metadata source and removed the duplicated name-based classification from `mudclient` and `SkillGuideInterface`. |
| B02 — `fix/desktop-sound-lifecycle` | Complete | `0232d7337` | Defined the desktop sound lifecycle, removed the throwing disconnect path, and added lifecycle coverage. |
| B03 — `chore/server-build-source-of-truth` | Complete | `86d904bb9` | Established Ant as the documented authority, reconciled stale build paths, and added a repeatable build audit. |
| B04 — `chore/static-analysis-baseline` | Complete | `4e04d42ca` | Added changed-code CI gates and reproducible local Checkstyle, PMD, SpotBugs, ShellCheck, Ruff, and javac analysis for all three Java products. |
| B05 — `fix/server-swallowed-failures` | Complete | `e49801b9e` | Added bounded, privacy-safe handling of social and offline-message database failures plus diagnostic, idempotent plugin-loader cleanup. |
| B06 — `refactor/client-auxiliary-types` | Complete | `22615d00f` | Moved 26 package-private client types to stable owners and reduced auxiliary-class warning occurrences from 310 to zero without API or bytecode changes. |
| B07 — `refactor/client-renderer-window-viewport` | Complete | `53bce8587` | Extracted viewport presentation and GLFW window lifecycle, added cleanup diagnostics, and restored software ownership when OpenGL is unavailable; private OpenGL/fallback verification passed. |
| B08 — `refactor/client-packet-diagnostics` | Complete | `34d5cbb9b` | Moved movement-snapshot diagnostics and scene-baseline state out of `PacketHandler`; packet decode/mutation order and private movement/region/relog behavior were verified. |
| B09 — `refactor/client-definition-registry` | Complete | `762aab1a5` | Separated registry storage/access, prayer-book authorship, and fallback diagnostics while preserving every catalog index and definition fingerprint. |
| B10 — `refactor/server-equipment-spell-boundaries` | Complete | `c4930cb31` | Extracted pure equipment slot/stat calculations and spell classification/validation without balance or scheduling changes; parity tests and private verification passed. |
| B11 — `chore/compatibility-labels-and-prune-proof` | Complete | `bab0cbaae` | Corrected active compatibility labels, removed proved-dead archive writers and stale IDE metadata, and retained uncertain MySQL, archive, plugin, protocol, and platform surfaces. |

B04 completed lint rollout Stages 0 and 1. B05 began Stage 2 with focused
failure classification and cleanup diagnostics, B06 eliminated the 310-report
auxiliary-class warning family, and B11 removed four proved-dead SpotBugs
fingerprints without expanding analyzer rules. The checked-in baselines contain
46 gated javac entries and 410 SpotBugs entries. The B11 verification run
observed 409 existing SpotBugs findings and no new finding. The local end-to-end
contract, compiler, analyzer self-test, Python, and shell validation passed, as
did the GitHub Actions run for `4e04d42ca`. Stage 2 should reduce one warning or
failure family per focused branch; Stage 3 rule expansion remains deferred
until those gates have completed several stable cycles.

All eleven ordered follow-up branches are complete. Further analyzer-rule
expansion, warning-family burn-down, or large-class decomposition requires a
new focused plan rather than extending this completed sequence implicitly.
The post-B11 ownership sequence is defined in
[`code-cleanup-and-modularization-plan.md`](../in-progress-work-plans/code-cleanup-and-modularization-plan.md#ordered-post-b11-implementation-branches);
package and source-root moves remain gated by
[`project-structure-refactor-plan.md`](../in-progress-work-plans/project-structure-refactor-plan.md).

## Rating Method

- **Impact** estimates the cost of leaving the issue in place: High, Medium,
  or Low.
- **Confidence** describes how directly repository evidence supports the
  finding: High, Medium, or Low.
- **Change risk** estimates regression risk when implementing the recommended
  correction, not the current severity.
- **Effort** uses S (hours to roughly one day), M (several focused days), L
  (roughly one to two weeks), and XL (multi-branch program).
- **Safe cleanup** means behavior should remain unchanged, but still requires
  compilation and relevant tests. **Behavior-changing** means the branch must
  prove intended runtime behavior explicitly.

## Evidence Summary

### Repository And Build Shape

- The active source/test/tool scope contains approximately **1,390 Java
  files**, **280 Python files**, and **37 shell scripts**. This count excludes
  archived source under `legacy/`.
- The authoritative script path uses the repository-bundled Ant 1.10.5 and
  Java 8 source/target settings:
  - `Client_Base/build.xml` compiles `Client_Base/src` and the active desktop
    source root `PC_Client/src` together.
  - `server/build.xml` separately builds `core.jar` and the dynamically loaded
    `plugins.jar`.
  - `scripts/build-client.sh` and `scripts/build-server.sh` are the documented
    production-facing wrappers.
- The client build compiled **209** Java source files. The server build compiled
  **692 core** and **488 plugin** source files. Both normal Ant builds passed.
- `server/build.gradle` exists and imports Ant targets, but its dependency graph
  does not match the Ant graph. It should not be assumed to be the production
  source of truth without reconciliation.
- `.github/` contains templates and ownership files but no active
  `.github/workflows/` directory. The old GitLab file is explicitly archived at
  `legacy/ci/gitlab/.gitlab-ci.yml`.
- Gradle declares JUnit Jupiter dependencies, but no active Java source contains
  `@Test` or JUnit imports. Current automated coverage is primarily the large
  Python/shell validation suite under `tests/myworld/`, including many valuable
  source-shape and data-integrity checks.

### Temporary Compiler-Warning Baseline

The Ant compiler arguments were changed locally to `-Xlint:all`, both builds
were run, and the build files were restored byte-for-byte to their committed
state. No analyzer configuration was committed.

- Client: javac stopped reporting at its default **100-warning cap**. The
  visible set was dominated by auxiliary top-level classes being declared in a
  different source file, including `ButtonHandler` in `AuctionHouse.java`,
  `Frame`/`FrameBufferPool` in `OpenGLFrame.java`, and renderer composite data
  types in `OpenGLCompositeSceneCommand.java`.
- Server core: javac also reached the **100-warning cap**.
- Server plugins: **48 warnings**.
- Across the server log, visible categories included **68 `serial`**, **44
  redundant `cast`**, **17 `fallthrough`**, **10 `overrides`**, **3 `static`**,
  **2 `varargs`**, **2 `unchecked`**, and **1 `overloads`** warning.
- High-signal examples include `Player`, `GameObject`, and `Item` overriding
  `equals` without `hashCode`; possible switch fallthroughs in `Npc` and
  `ScriptContext`; and unchecked reflective binding in
  `PayloadProcessorManager.bind`.

This is a lower bound of **248 warnings**, not a complete count. It proves that
repository-wide `-Werror` is not an appropriate first step.

### Size And Churn Method

Original audit line counts came from `wc -l`. “30-day touches” counts commits that
named the file from 2026-06-12 through 2026-07-12. “12-month churn” is added
plus deleted lines from `git log --numstat` since 2025-07-12. Churn includes a
file's initial addition and is therefore a prioritization signal, not a defect
score.

### Post-B11 Size And Ownership Reconciliation

The ranked table below remains the audit's 2026-07-12 evidence. These current
2026-07-16 measurements record the exact deltas after B01-B11 without
pretending that line count alone proves a successful boundary:

| File | Audit lines | Post-B11 lines | Delta | Disposition |
| --- | ---: | ---: | ---: | --- |
| `Client_Base/src/orsc/mudclient.java` | 27,594 | 27,550 | -44 | Spell-display duplication left in B01; renderer settings, profile application, software scaling, external assets, and scene-instance storage are the ordered next owners. |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | 9,778 | 9,629 | -149 | B09 moved registry/access, prayer definitions, and fallback diagnostics while retaining authored/load-order ownership and compatibility facade. |
| `server/src/com/openrsc/server/model/entity/player/Player.java` | 5,931 | 5,931 | 0 | Still a broad high-risk owner; no B01-B11 decomposition was authorized. |
| `Client_Base/src/orsc/PacketHandler.java` | 4,590 | 3,507 | -1,083 | B08 moved movement diagnostics and scene-baseline state; decode and mutation order remain. |
| `Client_Base/src/orsc/graphics/two/GraphicsController.java` | 4,346 | 4,346 | 0 | Still owns legacy raster/capture/archive concerns. |
| `PC_Client/src/orsc/OpenGLFramePresenter.java` | 4,295 | 4,068 | -227 | B07 moved GLFW window lifecycle and viewport presentation; frame/GL/composite coordination remains. |
| `Client_Base/src/orsc/graphics/three/World.java` | 4,253 | 4,264 | +11 | Still owns mixed world products and caches; no decomposition in this sequence. |
| `Client_Base/src/orsc/graphics/three/Scene.java` | 3,789 | 3,789 | 0 | Still owns legacy scene/export concerns. |
| `server/src/com/openrsc/server/model/container/Equipment.java` | 3,771 | 3,341 | -430 | B10 moved pure slot/stat calculations; mutable behavior and effects remain. |
| `Client_Base/src/orsc/RenderTelemetry.java` | 3,154 | 3,154 | 0 | Still a broad static facade/sink. |
| `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` | 2,754 | 2,418 | -336 | B10 moved pure classification/validation; packet/combat/scheduling authority remains. |

`OpenGLFramePresenter` again demonstrates why responsibility count, dependency
shape, and behavior tests are stronger exit criteria than a numeric size target.

## Ranked Findings

This ranking is the audit's original prioritization, not a current open-task
list. B01-B04 completed ranks 1-4, B05 addressed the scoped rank-6 failures,
B11 corrected/proved the bounded rank-8/rank-9 candidates, and B06-B10 made the
first safe boundaries under rank 5. The remaining rank-5 decomposition and the
renderer-settings portion of rank 7 continue through the refreshed plans.

| Rank | Finding | Impact | Confidence | Change risk | Effort | Class |
| ---: | --- | --- | --- | --- | --- | --- |
| 1 | Client combat-display rules have drifted from authoritative spell data | High | High | Medium | M | Behavior-changing |
| 2 | Desktop sound lifecycle hook throws on a normal disconnect path | High | High | Low | S | Behavior-changing bug fix |
| 3 | Ant and Gradle dependency/build definitions disagree | High | High | Medium | M | Build/reproducibility |
| 4 | Existing builds have no active CI gate and at least 248 lint warnings | High | High | Low | M | Safe foundation |
| 5 | Rapidly changing god objects concentrate unrelated responsibilities | High | High | High | XL | Behavior-preserving refactor program |
| 6 | Database/social failures and some cleanup failures are swallowed | Medium-High | High | Low-Medium | S-M | Reliability/observability |
| 7 | Renderer runtime/debug settings use dispersed compatibility parsing and flags | Medium | High | Medium | M | Safe extraction first |
| 8 | Active compatibility systems are still labeled “old,” “legacy,” or by misleading roots | Medium | High | Low | S-M | Labeling/documentation |
| 9 | Small dead/obsolete candidates exist, but broad call-site scans are unsafe here | Low-Medium | Medium | Low | S-M | Proof-before-prune |

### 1. Client Combat-Display Logic Is Duplicated And Already Inconsistent

**Evidence**

- `Client_Base/src/orsc/mudclient.java` methods
  `getDualElementalEffectName`, `getElementalSpellBaseMax`,
  `isTierTwoDualElementalSpell`, `getClientMagicOffense`,
  `getMyWorldStaffWoodTier`, and `getMagicArmorPowerPenalty` reconstruct server
  combat facts from spell-name strings, numeric item ranges, item names, and
  local constants.
- `mudclient.isTierTwoDualElementalSpell` recognizes `thunder splash`, `ice
  burst`, and `acid frog`.
- The actual definitions in both
  `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` and
  `server/conf/server/defs/SpellDef.xml` are **Thunder Bird**, **Ice Slash**, and
  **Acid Splash**.
- `Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java` has a
  separate `MagicSpellMenuItem.getDualElementalEffectName` and
  `getElementalDamageWord` table. That table uses the current spell names, so
  two client interfaces can disagree with each other.
- Server damage remains authoritative in
  `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` and
  `server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java`.
  Equipment offense is already sent from
  `server/src/com/openrsc/server/net/rsc/ActionSender.java` using
  `Equipment.getDisplayedMagicOffense`.

**Risk**

Tier-two tooltips can be classified as non-dual and use the wrong displayed
base/cap calculation. Any future balance change must currently be reproduced in
multiple client tables, server Java, XML definitions, and documentation.
String matching also makes harmless display-name edits behaviorally significant.

**Recommended boundary**

Create one client-facing immutable spell-display metadata catalog keyed by
stable spell index/enum mapping, not display text. Generate or validate it from
the same authored spell data where practical. The client may calculate a
tooltip from server-supplied current equipment totals, but it should not
re-derive item combat stats from names and ID ranges. Keep the server as the
authority for actual damage.

### 2. The Desktop Sound Lifecycle Contract Is Internally Contradictory

**Evidence**

- `PC_Client/src/orsc/OpenRSC.java` overrides `playSound` and
  `stopSoundPlayer` by throwing `UnsupportedOperationException("Not supported
  yet.")`.
- `PC_Client/src/orsc/ORSCApplet.java` provides the same throwing methods while
  implementing `orsc.multiclient.ClientPort`.
- `Client_Base/src/orsc/mudclient.java:closeConnection()` unconditionally calls
  `clientPort.stopSoundPlayer()` after disconnecting.
- Actual desktop playback bypasses `ClientPort.playSound`: `PacketHandler`
  calls `soundPlayer.playSoundFile`, and `PC_Client/src/orsc/soundPlayer.java`
  uses self-closing `Clip` objects. Its old `clientPort.playSound` call remains
  commented as Android code.

**Risk**

A normal disconnect/logout can enter a guaranteed throwing desktop hook. The
legacy mobile abstraction makes the method look unfinished even though desktop
sound has moved to a different implementation.

**Recommended correction**

Define the platform contract explicitly. For desktop, either track and stop
active clips or make `stopSoundPlayer` an intentional documented no-op. Remove
the duplicate throwing implementation only after the concrete `ClientPort`
contract is tested. Preserve Android/mobile hooks only in an active mobile
module; the Android client currently lives under `legacy/`.

### 3. Build And Dependency Metadata Has Drifted

**Evidence**

- The real wrappers invoke Ant, while `server/build.gradle` declares a second
  dependency graph.
- Version disagreements include:
  - Ant/vendor `xstream-1.4.18.jar` versus Gradle `xstream:1.4.9`.
  - Ant/vendor `netty-all-4.1.33.Final.jar` versus Gradle
    `netty-all:4.1.107.Final`.
  - Ant/vendor `emoji-java-5.1.1.jar` versus Gradle `emoji-java:4.0.0`.
  - Ant/vendor Guice `5.0.2` versus Gradle `5.0.1`.
- `server/build.xml` still names nonexistent `disruptor-3.3.0.jar`,
  `disruptor-3.3.5.jar`, and `xstream-1.4.9.jar` paths in run targets, then also
  adds `${lib}/*`. Ant tolerates the absent entries, obscuring which classpath
  is intended.
- `compile_core` creates a fat `core.jar` using every jar in `server/lib`, while
  run targets also place individual library jars on the classpath.
- Fifty jar files are tracked repository-wide; most active server dependencies
  are committed binaries rather than resolved from a single manifest.

**Risk**

IDE/Gradle analysis can compile against behavior different from packaged Ant
artifacts. Dependency updates may touch only one graph. Missing explicit paths
look active, and duplicate fat-jar/external classpaths make class loading harder
to reason about.

**Recommended boundary**

First declare the supported build matrix in one short document: Ant is
authoritative today; Gradle is secondary until parity is proven. Then generate
or validate both classpaths from one pinned dependency manifest, or deliberately
retire one build graph. Do not attach quality gates to Gradle merely because
plugins are convenient until its bytecode/classpath matches the shipped Ant
artifacts.

### 4. Static Analysis Has No Active CI Baseline

**Evidence**

- Client Ant enables only `-Xlint:unchecked`.
- Server Ant enables only `-Xlint:deprecation`, even though the normal core
  build reports unchecked/unsafe operations.
- Temporary `-Xlint:all` builds exposed at least 248 warnings.
- No active GitHub workflow runs the existing build/test suite.
- There is no repository-owned Checkstyle, PMD, or SpotBugs configuration.
- No Java unit tests currently use the declared JUnit dependencies.

This is a foundation issue, not a request for a giant warning cleanup. The
staged rollout is specified below.

### 5. Large, Rapidly Changing Files Are The Main Refactor Risk

Data-only files such as `ItemId.java`, generated files such as
`MyWorldItemOverrides.java` and `RemasteredSpriteCatalogData.java`, XML/JSON
definitions, and protocol word tables should not be ranked solely by line
count. The following files combine size with mixed runtime responsibilities.

| File | Lines | 30-day touches | 12-month churn | Responsibilities and sensible first boundary | Risk |
| --- | ---: | ---: | ---: | --- | --- |
| `Client_Base/src/orsc/mudclient.java` | 27,594 | 95 | 31,132 | Game loop/state, login, UI tabs, settings, tooltip combat math, asset loading, scene instances, projectiles/effects, movement and renderer bridges. First extract spell-display metadata, renderer settings panel, external assets, and scene instance store; do not start with arbitrary method moves. | Very high |
| `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | 9,778 | 34 | 15,872 | Static registry plus thousands of hardcoded NPC/item/spell/prayer/tile/door/object definitions, MyWorld overrides, fallback logging, and generated families. Separate read-only registry/API from authored/generated loaders and prayer-book state. | High |
| `server/src/com/openrsc/server/model/entity/player/Player.java` | 5,931 | 16 | 6,543 | Identity/session, persistence-facing state, combat/skills, social state, local entity caches, quest/cache attributes, protocol selection, and UI helpers. Extract immutable identity/session and bounded state components before gameplay behavior. | Very high |
| `Client_Base/src/orsc/PacketHandler.java` | 4,590 | 30 | 4,958 | Opcode dispatch, parsing, direct client mutation, movement snapshots, scene-baseline storage/parity, diagnostics, UI updates, and configuration. Move `Movement*DebugState` and `SceneBaselineDebugState` first; then split handlers by packet family while preserving wire order. | Very high |
| `Client_Base/src/orsc/graphics/two/GraphicsController.java` | 4,346 | 10 | 4,500 | Legacy pixel/sprite/font rasterization, archive loading, scaling/transforms, and renderer-v2 capture. Separate capture recorder and archive loader before touching raster behavior. | High |
| `PC_Client/src/orsc/OpenGLFramePresenter.java` | 4,295 | 28 | 35,354 | GLFW/GL lifecycle, window state, viewport/presentation, pass orchestration, texture upload, world/sprite composite glue, debug overlay, capture coordination, and cleanup. Continue the existing plan with viewport and window controllers; leave sprite ordering until visual parity tests exist. | High |
| `Client_Base/src/orsc/graphics/three/World.java` | 4,253 | 16 | 6,319 | Sector IO/cache, terrain/wall/roof construction, collision, minimap, renderer products, and streaming. Extract pure product builders and sector cache behind characterization tests. | Very high |
| `Client_Base/src/orsc/graphics/three/Scene.java` | 3,789 | 11 | 3,931 | Legacy sort/raster, picking, camera/frustum, sprite submission, and renderer-v2 export. Separate frame export/picking before changing legacy sort order. | Very high |
| `server/src/com/openrsc/server/model/container/Equipment.java` | 3,771 | 11 | 4,105 | Container behavior, slot validation, appearance, stat aggregation, derived MyWorld bonuses, charges/durability, and item-effect hooks. Extract pure stat/slot calculators first; retain mutation in the container. | High |
| `PC_Client/src/orsc/OpenGLWorldChunkRenderer.java` | 3,454 | 11 | 3,714 | Chunk buffers, material batches, culling, shadow/glow classification and upload, shaders, resident objects, and cleanup. Separate mask upload/cache and GL resource ownership from draw scheduling. | High |
| `Client_Base/src/orsc/RenderTelemetry.java` | 3,154 | 16 | 3,254 | Static counters, histograms, reflection-based snapshots, debug summaries, renderer/client/movement categories. Keep a facade but move category state to focused collectors. | Medium |
| `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` | 2,754 | 12 | 3,092 | Validation, rune removal, inventory/object/NPC/player dispatch, teleports, combat, AoE, debuffs, visuals, and finalization. Extract pure spell classification/validation before event scheduling. | Very high |
| `server/src/com/openrsc/server/net/rsc/ActionSender.java` | 2,711 | 6 | 2,777 | Protocol-version generator selection plus gameplay-specific outgoing packets. Separate version selection and packet builders without removing old protocol generators. | Very high |
| `server/src/com/openrsc/server/GameStateUpdater.java` | 2,639 | 11 | 3,003 | Tick orchestration, visibility/local caches, multi-version player/NPC encoding, appearance updates, private messages, offers, and cleanup. Extract protocol serializers and social delivery queues behind packet-shape tests. | Very high |

The renderer cleanup plan remains directionally correct, but its previous
“largest renderer file below 5,000 lines” target is now insufficient:
`OpenGLFramePresenter` is below 5,000 while still churning heavily and owning
too many roles. Responsibility and dependency count should be the exit criteria,
not a line threshold alone.

### 6. Failure Swallowing Hides Operational Problems

**High-confidence examples**

- `FriendHandler.process` catches broad database exceptions during friend and
  ignore name resolution and returns with no log or user-facing distinction
  between “unknown player” and “database failure.”
- `GameStateUpdater` catches and discards exceptions while resolving an offline
  private-message recipient. Delivery failure can become silent.
- `JContent.dump` and `JContentFile.dump` swallow every exception while writing
  archives.
- `PluginJarLoader.clear` discards every exception while closing its class
  loader.
- `OpenGLFramePresenter.cleanup` and `OpenGLInputBridge.close` catch
  `Throwable`. Best-effort teardown is sensible, but losing all diagnostics
  makes resource leaks and shutdown faults untraceable.

Not every ignored exception is a defect. Cleanup must continue after individual
GL resource failures, optional local settings may be absent, and malformed
legacy network input may intentionally fall back. The correction is to classify
expected exceptions narrowly and record unexpected failures once, without
turning teardown into a crash.

### 7. Runtime Option Parsing And Compatibility Flags Are Dispersed

There are **127** environment/system-property reads across **43** active Java
classes. Renderer settings classes repeatedly implement their own key aliases,
parsing, bounds, and fallback behavior. Examples include
`RendererReliefSettings`, `RendererFogSettings`, `RenderSurfaceSettings`,
`OpenGLPresentationSettings`, and numerous OpenGL helpers.

This has enabled useful incremental renderer work, but the architecture now has
three overlapping configuration layers:

1. persisted `clientSettings.conf` values;
2. runtime property/environment diagnostic overrides;
3. profile logic that forces or migrates settings.

Extract a typed `ClientRuntimeOptions` reader and an explicit saved-setting
migration layer. Do not change default values in the extraction branch. Keep
diagnostic-only flags out of the player settings UI unless deliberately
promoted.

### 8. Legacy And Compatibility Labels Need A Stronger Map

#### Genuinely active compatibility — do not prune

- **`Client_Base/` and `PC_Client/` are both active.** The names suggest base
  versus standalone clients, but `Client_Base/build.xml` compiles both roots
  into the current desktop jar. The existing project-structure plan correctly
  recommends eventual `client/common` and `client/desktop` names.
- **`ScaledWindow` and software scaling remain a real fallback.** OpenGL is
  primary but optional; `OpenRSC` still loads the explicitly labeled legacy
  bridge for `scaling_type`, `ui_scale`, and `scaling_scalar`.
- **Hidden render surface values are migration aliases.** They should be named
  as such, not deleted merely because they are not shown in the menu.
- **Packet parsers/generators for 38, 69, 115, 140, 177, 196, 198, 199, 201,
  202, 203, 235, and custom clients are active protocol compatibility.**
  `ActionSender.getGenerator`, `Player` parser selection, and
  `RSCProtocolDecoder` reference them directly.
- **Server plugin classes are discovered dynamically.** `PluginJarLoader`
  enumerates every top-level `.class` in `plugins.jar`, and
  `PluginHandler.initPlugins` registers trigger interfaces, quests, minigames,
  shops, and registrars by reflection. Lack of a Java call site does not imply
  a plugin is dead.
- **MySQL code is not automatically dead because the hosted alpha uses
  SQLite.** Both database implementations and configs must be checked against
  supported deployment policy before removal.
- At audit time, `OLD_PRAY_XP`, `OLD_QUEST_MECHANICS`, and `OLD_SKILL_DEFS`
  defaulted false in MyWorld configs but were referenced by active
  plugin/definition code. `OLD_PRAY_XP` was later retired only after its final
  consumer moved to the authoritative offering tier-and-devotion model. The
  remaining flags must not be deleted based on the `OLD_` prefix alone.

#### Explicit archive — non-runtime but retained by policy

- `legacy/README.md` clearly states that current scripts must not depend on
  `legacy/` and identifies active replacements.
- The archive contains **385 tracked files** and approximately **55 MiB**
  (58,195,019 tracked bytes), largely old clients and Windows portable tools.
- No current build path references it. Removing it would reduce checkout size,
  but that is a repository-preservation decision, not an urgent runtime cleanup.

#### Misleading or stale labels

- `PayloadProcessorManager` still comments that `KnownPlayersHandler` “needs to
  be implemented,” but the handler stores known-player IDs/appearance IDs and
  is used by numerous protocol parsers. Remove the stale comment; do not remove
  the handler.
- `server/database/mysql/depreciated/` is misspelled and has no repository call
  sites outside that directory. These appear to be manual old SQL helpers.
  Move them under an explicitly archived migration/reference path only after a
  MySQL operator review.
- No-op interface callbacks in `Bank`, `Duel`, `Trade`, `World`, and client UI
  classes still say “TODO Auto-generated method stub.” Some are intentional
  hooks, not unfinished behavior. Replace the comment with an explicit no-op
  contract or implement the listener; never infer deadness from that comment.
- `BREAK_NPC_LOCATION_CACHE` is an active inverted diagnostic switch in
  `GameStateUpdater`, despite having no visible MyWorld config entry. Rename it
  to describe behavior (`disable...`) and document its scope before deciding
  whether to remove it.

### 9. Proof-Before-Prune Candidates

No broad class deletion is recommended from this audit. The following are
small, bounded candidates for a dedicated proof branch:

| Candidate | Evidence | Required proof | Tentative disposition |
| --- | --- | --- | --- |
| `JContent.dump` and `JContentFile.dump` | No `.dump(` call sites in active Java; their containing classes are active world-archive readers. | Search packaged scripts/tools, run world-load and terrain editor tests, check reflective calls (unlikely), then remove methods only. | Safe prune candidate |
| Active-root IntelliJ metadata (`Client_Base/.idea`, `*.iml`, `server/Game Server.iml`) | Build scripts do not use it; equivalent metadata is already archived elsewhere. | Confirm contributor IDE onboarding does not promise checked-in project files; update `.gitignore`. | Safe repository cleanup |
| `server/database/mysql/depreciated/*.sql` | No external repository references; folder name marks historical intent. | MySQL operator/migration inventory and documentation review. | Archive or delete by policy |
| Desktop `ClientPort.playSound` byte-array path | Only declarations and commented Android call remain. | Confirm no maintained mobile build consumes shared sources; resolve `stopSoundPlayer` first. | Remove or move to mobile compatibility interface |
| Entire `legacy/` tree | Explicitly excluded from current workflow and 55 MiB. | Manager decision on preservation value and release/source-distribution expectations. | Optional history-repository split, not ordinary cleanup |

Generated files, dynamically loaded plugins, packet-version classes, reflection
targets, configuration-key-only features, and assets referenced by string/path
must never be removed solely by IDE “unused” output. Add an allowlist/registry
test before attempting automated unused-class enforcement.

## Static-Analysis Tool Evaluation

### Recommended Now

#### Javac `-Xlint`

Highest value and lowest integration cost because it uses the real compiler and
classpaths. Start by collecting `-Xlint:all` reports from the real Ant targets.
Gate new instances of selected high-signal categories (`unchecked`,
`fallthrough`, `overrides`, `varargs`, and `overloads`) against a committed
baseline. Do not enable global `-Werror` until the baseline is deliberately
small.

The auxiliary-class warnings identify useful safe extractions, but they should
be handled as a separate cleanup branch rather than blocking all current client
work.

#### Checkstyle — structural subset only

Use it initially for unambiguous source structure, not formatting. Candidate
checks include one top-level type per file, outer-type filename, illegal empty
catch blocks (or a repository-specific check), and import hygiene. Do not
enable indentation, naming, line length, Javadoc coverage, or wholesale Google
/Sun style rules on inherited code.

Current Checkstyle releases require a newer analysis JRE even though they can
parse Java 8 source: Checkstyle 13 requires JRE 21, 11/12 require JRE 17, and 10
requires JRE 11. It has a native Ant task and supports XML/SARIF output and
suppression files. Pin the chosen version and run it in a separate analysis JDK
lane. See the official [Checkstyle runtime matrix](https://checkstyle.org/) and
[Ant task documentation](https://checkstyle.org/anttask.html).

#### PMD — curated correctness rules plus CPD report

PMD is well matched to the observed empty catches, unnecessary casts, complex
conditionals, and suspicious duplicated classifications. Configure Java
language level 8 explicitly and select a small ruleset; do not enable hundreds
of defaults. Good first candidates are `EmptyCatchBlock`,
`AvoidCatchingGenericException` at low severity, `CloseResource`,
`CompareObjectsWithEquals`, and narrowly reviewed unused-private-code rules.
PMD supports Java 8 syntax and incremental caches; see [Java language
support](https://pmd.github.io/pmd/pmd_languages_java.html) and the [CLI cache
options](https://pmd.github.io/pmd/pmd_userdocs_cli_reference.html).

Run CPD report-only at first (for example, 100–150 minimum tokens). Exclude
generated definition catalogs and report protocol-version families separately,
because some duplication is intentional wire compatibility. CPD supports Java
and an Ant task; see the official [CPD guide](https://pmd.github.io/pmd/pmd_userdocs_cpd.html).

#### SpotBugs — post-compile, new high-confidence bugs only

SpotBugs analyzes bytecode, so run it against the exact Ant-built client jar,
server core, and plugin classes with the complete auxiliary classpath. Start
with high/medium confidence correctness findings and a committed baseline XML.
It is particularly valuable for dropped exceptions, equals/hashCode contracts,
null/resource mistakes, and bad synchronization. Do not use “unused” findings
to remove plugins or reflection targets.

SpotBugs currently requires JRE 11 or later to run, while it can analyze Java 8
bytecode. Official filters and baseline exclusion are supported; see the
[SpotBugs FAQ](https://spotbugs.readthedocs.io/en/stable/faq.html), [running
guide](https://spotbugs.readthedocs.io/en/stable/running.html), and [filter
format](https://spotbugs.readthedocs.io/en/stable/filter.html).

#### ShellCheck And Ruff — changed scripts only

- ShellCheck is appropriate for the 37 active shell scripts. The repository
  already contains targeted `shellcheck` source-disable comments, suggesting
  prior awareness. Gate new/changed scripts first and preserve deliberate
  dynamic-path exceptions with local annotations.
- Ruff is appropriate for fast syntax/undefined-name/import checks across the
  280 Python files. Begin with correctness families such as `E9` and `F`; do
  not run the formatter or enforce repository-wide style. Existing Python tests
  are operational scripts, so analyzer changes must not rewrite them en masse.

### Defer Or Use Selectively

- **Error Prone/NullAway:** potentially valuable, but javac integration,
  annotations, the Java 8 production toolchain, and two Ant source graphs make
  this a later project after build parity and warning baselines.
- **SonarQube/SonarCloud:** broad dashboards would duplicate initial PMD,
  SpotBugs, and CPD value while adding service/configuration overhead. Revisit
  after local reproducible gates exist.
- **Repository-wide formatter:** explicitly not recommended. The project uses
  tabs for Java in `.editorconfig`, contains inherited styles, and has very high
  churn. Formatting would obscure blame and conflict with active feature work.
- **Dependency vulnerability scanners:** valuable, especially with old vendored
  jars, but first establish a truthful dependency manifest. Scanning the drifted
  Gradle graph would provide false assurance about the shipped Ant classpath.

The repository's Gradle 8.9 wrapper can run on Java 8 according to Gradle's
[compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html),
but the analysis lane should use a newer JDK for current Checkstyle/SpotBugs.
That does not change the Java 8 target or the authoritative Java 8 build lane.

## Staged Lint And CI Rollout

### Stage 0 — Reproducible Baseline, Report Only

Create `chore/static-analysis-baseline` after build-source-of-truth labeling.

Scope reconciliation on 2026-07-15: published `main` now contains the
standalone World Builder. It is a separate maintained Java product with its
own bundled-Ant build, executable jar, release scripts, and focused tests. The
baseline must therefore treat the following as explicit build products rather
than assuming the client/server inventory is exhaustive:

| Product | Maintained Java roots | Authoritative build wrapper | Analyzed artifact |
| --- | --- | --- | --- |
| Desktop client | `Client_Base/src`, `PC_Client/src` | `scripts/build-client.sh` | `Client_Base/Open_RSC_Client.jar` |
| Server | `server/src`, `server/plugins` | `scripts/build-server.sh` | `server/core.jar`, `server/plugins.jar` |
| World Builder tools | `tools/world-builder/src` | `scripts/build-world-builder-tools.sh` | `output/world-builder-tools/world-builder-tools.jar` |

The loose `tools/myworld/ExportBasicProjectileSheets.java` developer utility
is source-analyzed when changed, but is not represented as a compiled product
until it has a maintained build contract. Active Python scope is repository
scripts, generator utilities, and `tests/myworld`; bundled Ant Python and
`legacy/` remain excluded. Active shell scope includes maintained root, `dev`,
player-release, World Builder release, script, test, and benchmark launchers;
archived launchers and bundled vendor scripts remain excluded.

World Builder verification is a dedicated lane. `tests/myworld/test-all.sh`
does not currently enumerate its process, filesystem-transaction, packaging,
or editor suites, so a general test-suite invocation must not be recorded as
World Builder coverage. At minimum, baseline verification builds the tools jar
and runs the static-analysis contract test; future Builder behavior changes
continue to run the focused suites named in
the archived `completed-work-plans/archive/standalone-world-builder-plan.md`.

1. Add `scripts/lint.sh` with pinned tool versions/checksums and explicit
   includes/excludes. Exclude `legacy/`, generated Java catalogs, build output,
   vendored source/tools, and assets by default.
2. Add an active CI workflow with two lanes:
   - Java 8 authoritative Ant compiles for the client, server, and World
     Builder tools plus existing focused smoke checks;
   - newer-JDK analysis lane for Checkstyle/PMD/CPD/SpotBugs/ShellCheck/Ruff,
     consuming the exact Ant-built jars from the Java 8 lane.
3. Store machine-readable reports as artifacts. Do not fail on the existing
   baseline.
4. Record tool version, ruleset hash, analyzed commit, source roots, and
   exclusions so results are reproducible.

**Verification:** clean checkout run, offline/cache-warm rerun, intentionally
broken fixture proves each analyzer executes, and both normal Ant builds produce
the same success result as before.

### Stage 1 — Enforce New/Changed Code

1. Feed changed Java/Python/shell paths from the merge base to source analyzers.
2. For whole-program javac and SpotBugs output, compare stable fingerprints
   against a committed baseline and fail only on new high-signal findings.
3. Gate changed Java on a narrow Checkstyle/PMD ruleset; keep formatting rules
   disabled.
4. Gate changed shell on ShellCheck and changed Python on Ruff correctness rules.
5. Keep CPD report-only; attach likely duplicate regions to follow-up issues.

**Verification:** a branch that introduces one known violation must fail; an
unchanged baseline must pass; renaming/moving a baseline file must not silently
erase the finding without review.

### Stage 2 — Burn Down High-Signal Baseline

Use one warning family per branch:

1. equals/hashCode and true fallthrough defects;
2. unchecked/varargs correctness;
3. empty/dropped exceptions;
4. auxiliary top-level type extraction;
5. resource handling;
6. redundant casts/serial IDs only when they do not bury active work.

Reduce the baseline file in the same commit as each fix. Never regenerate it
wholesale to make CI green.

**Verification:** focused behavior tests plus full Ant compiles; SpotBugs/PMD
finding count must decrease exactly as expected.

### Stage 3 — Expand Rules Deliberately

Only after several stable cycles, consider complexity thresholds, package
dependency rules, broader Checkstyle conventions for new packages, and
dependency scanning. Apply stricter rules to new MyWorld packages before
inherited packages. Formatting remains a separate opt-in project.

## Completed Ordered Follow-Up Branches (B01-B11)

This is the completed historical sequence. Its definitions remain here to show
the authorized boundaries and verification contract; do not treat these
entries as open work or silently append B12. New work starts from the refreshed
cleanup and structure plans linked above.

### B01 — `fix/client-spell-display-metadata` — Complete

- Correct the confirmed tier-two dual-spell name drift.
- Replace string-based tier/effect classification in both `mudclient` and
  `SkillGuideInterface` with one stable metadata catalog.
- Prefer generated/validated metadata over another hand-maintained table.
- Do not move server damage authority into the client.

**Verify:** client/server builds; spell definition parity test; existing combat,
spellbook text, and skill-guide tests; private-client tooltip checks for every
single/dual spell tier with representative equipment.

### B02 — `fix/desktop-sound-lifecycle` — Complete

- Define desktop `ClientPort` sound lifecycle semantics.
- Replace the throwing disconnect hook with tracked clip shutdown or an
  intentional no-op.
- Remove byte-array playback only if the mobile compatibility boundary is
  formally retired.

**Verify:** client build; connect, play sound, logout, forced disconnect,
reconnect, and window close; confirm no lingering clip/thread and no wrapped
`UnsupportedOperationException`.

### B03 — `chore/server-build-source-of-truth` — Complete

- Document Ant as current authority.
- Remove nonexistent classpath entries after proving they are not supplied by
  release tooling.
- Reconcile or clearly quarantine Gradle dependencies and test tasks.
- Produce a dependency/classpath inventory for the shipped jars.

**Verify:** `scripts/build-server.sh`, plugin discovery count, SQLite boot smoke,
packaging, representative Gradle task if retained, and bytecode/classpath
comparison. Exercise MySQL only if it remains supported and credentials are
available in an isolated test environment.

### B04 — `chore/static-analysis-baseline` — Complete

- Implement Stages 0 and 1 only.
- Cover the desktop client, server core/plugins, and standalone World Builder
  as separate Java products. Include changed active Python and shell code,
  including World Builder release and test surfaces.
- Do not claim `tests/myworld/test-all.sh` covers the dedicated World Builder
  suites.
- Add no formatting rules and perform no baseline cleanup in the setup commit.

**Verify:** clean/pass and intentional-violation/fail CI fixtures, offline/local
script reproducibility, all three Ant product builds, the static-analysis
contract test, and no generated source mutations.

### B05 — `fix/server-swallowed-failures` — Complete

- Start with `FriendHandler` and offline private-message lookup.
- Distinguish not-found from database failure, log unexpected faults with safe
  context, and send a bounded user response.
- Add best-effort cleanup diagnostics without making shutdown fatal.

**Verify:** injected database failures, unknown-user cases, privacy cases,
offline PM behavior, server build, social-name tests, and log redaction review.

### B06 — `refactor/client-auxiliary-types` — Complete

- Move `ButtonHandler`, frame/pool types, key bindings, and renderer composite
  data types to correctly named files or justified nested types.
- Keep APIs/package visibility unchanged where possible.
- Reduce only the client auxiliary-class warning family.

**Verify:** client build, `-Xlint:all` delta, interface UI click smoke, renderer
launch/input/frame capture; baseline decreases exactly by moved warnings.

### B07 — `refactor/client-renderer-window-viewport` — Complete

- Continue the existing renderer plan: extract viewport presentation and window
  mode/lifecycle from `OpenGLFramePresenter`.
- Add diagnostics for cleanup failures; do not alter sprite composite ordering
  in the same branch.

**Verify:** 4:3/16:9, borderless/windowed, resize, monitor selection, close,
software fallback, packaged launcher, login graphic, roofs, sprites behind
walls, items, animations, and day/night visuals.

### B08 — `refactor/client-packet-diagnostics` — Complete

- Move movement snapshot/debug state and scene-baseline parity/storage out of
  `PacketHandler` without changing parsing or mutations.
- Follow with separate packet-family branches only after characterization.

**Verify:** packet-shape, movement stability, scene lifecycle, region load,
world editor, client/server sync, and private movement/teleport/region testing.

### B09 — `refactor/client-definition-registry` — Complete

- Separate the registry/access API from authored base definitions, MyWorld
  overrides, generated families, prayer books, and fallback logging.
- Preserve item/sprite indexes exactly.

**Verify:** generated-artifact checks, item/NPC/animation/sprite coverage audits,
client build, login/load, banks/shops/equipment, prayers/spells, and fallback
logs for malformed test IDs.

### B10 — `refactor/server-equipment-spell-boundaries` — Complete

- Extract pure slot/stat calculations from `Equipment`.
- Extract pure spell classification/validation from `SpellHandler` in a
  separate commit/branch before event scheduling or balance changes.
- Do not combine extraction with rebalance.

**Verify:** equipment/stat packet parity, equip-slot tests, combat formulas,
spell costs, all elemental/dual/god spell cases, AoE targeting, teleports,
charges/durability, and live-like private combat scenarios.

### B11 — `chore/compatibility-labels-and-prune-proof` — Complete

- Correct the stale `KnownPlayersHandler` TODO and auto-generated no-op labels.
- Document active client roots and compatibility flags near their definitions.
- Audit/remove the two unused archive dump methods, active IDE metadata, and
  `mysql/depreciated` only where proof is complete.
- Leave `legacy/`, protocol variants, plugins, and database backends intact
  absent a separate manager policy decision.

**Verify:** reference scans including reflection/config/path strings; client and
server builds; world load/editor tests; plugin discovery; protocol smoke; docs
link check; clean IDE import if metadata is removed.

## Safe Cleanup Versus Behavior-Changing Summary

**Safe/foundation first:** build-source labeling, lint baseline, auxiliary type
extraction, diagnostic-state extraction, stale comments, explicit no-op
contracts, unused dump methods after proof, and IDE metadata cleanup.

**Behavior-changing and separately reviewed:** spell-display authority, desktop
sound lifecycle, user-visible database failure responses, compatibility flag
removal/default changes, protocol code changes, renderer sprite ordering, and
server equipment/spell behavior.

## Audit Verification Performed

- `git status --short --branch`
- `./scripts/ai-workspace.sh status`
- `./scripts/build-client.sh` — passed with committed warning configuration
- `./scripts/build-server.sh` — passed with committed warning configuration
- Temporary client/server `-Xlint:all` builds — passed; at least 248 warnings
  recorded; build files restored with no diff
- Size inventory across active Java/Python/shell source
- 30-day commit-touch and 12-month numstat churn inventories
- Reflection/dynamic plugin loading trace through `PluginJarLoader` and
  `PluginHandler`
- Protocol parser/generator selection trace through `ActionSender`, `Player`,
  and `RSCProtocolDecoder`
- Configuration, legacy archive, generated-source, assets/build inclusion,
  ignored-exception, unsupported-stub, and call-site searches
- Official documentation review for Checkstyle, PMD/CPD, SpotBugs, and Gradle
  runtime constraints

The full `scripts/test.sh` suite was not required to validate a documentation-
only change and was not used as evidence of runtime correctness. Each follow-up
branch above lists the behavior-specific verification it must add or run.
