# Code Size And Navigation Audit — 2026-08-13

Status: **planning/inventory only.** No production refactor is authorized by
this audit.

This refresh supersedes the size/churn measurements in
`code-health-audit-2026-07-12.md` and the paused-next-work list in
`code-cleanup-and-modularization-plan.md`. B01–B11 and the six documented
client ownership extractions remain complete; this is not a proposal to reopen
them wholesale.

## Method and exclusions

Measurements were taken on published `main` at `41e2c2765` on 2026-08-13.
Java line counts use `wc -l`; churn is added plus deleted lines and commit
touches from 2026-07-14 through 2026-08-13. Generated output, static-analysis
reports, JSON/XML definitions, ID constants, vendored dependencies, and the
intentionally exhaustive layered-map evidence plans are excluded from ranking.
Churn signals feature pressure, not code quality.

## Findings and recommended sequence

| Priority | Candidate | Lines | 30-day churn/touches | Why it is a candidate | Natural boundary and dependency rule | Benefit / risk / effort |
| --- | --- | ---: | ---: | --- | --- | --- |
| 1 | `Client_Base/src/orsc/mudclient.java` | 29,300 | 9,072 / 91 | Still combines client lifecycle, UI, scene mutation, combat presentation, renderer coordination, settings compatibility, and feature glue after the completed extractions. It grew 3,129 lines since the July 16 paused-plan measure. | Do **not** create another coordinator. Extract only a narrowly characterized destination owner when feature work exposes one: combat-effect presentation, external-content destination catalog, or presenter composite policy. Retain public facades and packet/scene ordering. | Very high benefit; very high regression/visual risk; L–XL. First only after a fresh candidate characterization branch. |
| 2 | `server/src/com/openrsc/server/model/entity/player/Player.java` | 7,275 | 2,480 / 75 | Central state now mixes session/login state, movement, UI/plugin state, skills, equipment consequences, party/cleric state, persistence hints, and combat ownership. It has grown 1,344 lines since the July audit. | Start with one passive typed state family or lifecycle adapter, not arbitrary getters. Candidate seams: transient client/status state, party/cleric lifecycle cleanup, or movement/session transitions. Must preserve cache/database, packet, combat, and plugin contracts. | Very high benefit; very high risk; L. Needs dedicated characterization before implementation. |
| 3 | `server/src/com/openrsc/server/GameStateUpdater.java` | 4,950 | 3,335 / 31 | Tick orchestration, login/logout, social state, database work, NPC/player updates, plugin scheduling, and exception boundaries coexist. Prior B05 fixed scoped swallowed failures but did not split ownership. | Extract tick-phase coordinators only after documenting exact tick order: player lifecycle, social/database reconciliation, entity processing, and cleanup. Never change timing while extracting. | High benefit; high risk; L. Good next server structural program after Player characterization. |
| 4 | `server/src/com/openrsc/server/model/world/region/RegionManager.java` | 5,066 | 5,235 / 72 | Legacy region lookup, layered residency, map activation, collision products, entity membership, cleanup, and native-package interaction meet in one high-churn authority. | Separate only an already-inventoried layer/residency product from legacy-region compatibility. `LayeredCoordinateParityObserver` evidence must remain detached and no extraction may weaken missing-tile fail-closed policy. | High benefit; very high spatial/compatibility risk; XL. Defer behind the current layered-map program unless it names the exact seam. |
| 5 | `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` | 9,945 | 979 / 52 | Registry access was extracted in B09, but authored definitions, load ordering, custom overrides, generated families, and compatibility fallback wiring remain together. | Definition **source/load-order** ownership is the remaining seam. Preserve numeric IDs, array order, fallback diagnostics, and generated override order. Do not move data tables just to reduce lines. | High benefit; high compatibility risk; L. Only when definition-feature pressure returns. |
| 6 | `Client_Base/src/orsc/PacketHandler.java` | 5,171 | 3,427 / 45 | B08 removed baseline/diagnostic state, but opcode decode, direct mutation, protocol compatibility, UI reactions, and renderer notifications remain coupled. | Extract one protocol family only where a typed decoded command already exists or can be characterized without changing decode order. Layered terrain is not a general precedent. | Medium-high benefit; high protocol risk; M–L. |
| 7 | `Client_Base/src/orsc/graphics/three/World.java` | 7,517 | 4,195 / 30 | Terrain loading, walls/roofs/minimap, collision, cache products, sector management, and renderer chunk export are mixed. | Prefer immutable terrain-product/cache seams. Keep collision and legacy sector behavior paired until a renderer roadmap slice proves an independent contract. | High renderer benefit; very high visual/cache risk; XL. Defer unless renderer work demands it. |
| 8 | `server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java` | 2,541 | 2,551 / 14 | Event queues, restoration, ownership/lifecycle, tick selection, diagnostics, and mutation controls have accumulated quickly. | Extract a read-only inventory/validation view or one restoration ledger, preserving scheduling order and cancellation semantics. | Medium-high benefit; high timing risk; M. Candidate only after A-program lifecycle work is rechecked. |
| 9 | `server/src/com/openrsc/server/model/container/Equipment.java` and `server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java` | 3,454 / 2,810 | 841 / 16; 1,452 / 22 | B10 removed pure calculations, but runtime packet/stat/effect sequencing remains intentionally central. | Keep mutations, packets, combat, and item effects together until a new independently testable runtime family appears. | Potential benefit, but high risk and weak current boundary. **Do not schedule now.** |

### Large but intentionally retained

| File | Lines | Rationale |
| --- | ---: | --- |
| `server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java` | 12,403 | High churn (12,979 lines / 63 touches) reflects an intentionally explicit, bounded diagnostic evidence inventory for the layered-map program. It should be split only if its owning plan identifies a consumed product with a stable lifecycle; arbitrary decomposition would obscure proof fields. |
| `Client_Base/src/orsc/graphics/two/GraphicsController.java` | 4,585 | Legacy software presentation, capture/replay, sprite scaling, and archive compatibility remain coupled intentionally. The active software fallback prevents a cosmetic split from being low risk. |
| `Client_Base/src/orsc/graphics/three/Scene.java` and `RenderTelemetry.java` | 4,158 / 4,380 | Both are broad, but current renderer work relies on cross-cutting frame ordering and correlated telemetry. Split only with a measured presenter/telemetry requirement. |
| `server/plugins/.../Admins.java`, `Development.java`, and authentic quest plugins | 3,713 / 3,313 / 3,584+ | Command/quest scripts are large authored behavior units. Split only around a proven shared service; preserving script-local dialogue/control flow is generally safer. |
| `ItemId.java`, `NpcDrops.java`, and definition/override sources | 2,541 / 2,308 | Intentionally monolithic stable catalog data; generated/definition-like sources are not refactor candidates. |

## Navigation and indexing audit

### What exists

- `rg` and Git history are available and are the established reliable lexical
  navigation tools. Worker instructions already require `rg` for file/text
  discovery.
- Ant is the authoritative Java build. `scripts/build-server.sh`,
  `scripts/lint.py`, `scripts/audit-server-build.py`, focused Ant targets, and
  the compiled fixtures provide dependable compile/dependency evidence.
- Documentation indexes (`docs/myworld/README.md`) and focused ownership/
  build records provide useful human navigation, but no generated source-symbol
  or dependency graph is maintained.
- No Ctags executable, repository tags file, JDT language-server
  configuration, SCIP index, cscope database, Maven project, or IDE project
  metadata for the maintained Java products exists. `ctags`, `jdtls`, `scip`,
  and `scip-java` are not installed in the manager environment.

### Recommendation: committed navigation map + optional local Ctags

Do **not** add a persistent checked-in Java language-server or SCIP index now.
The repository has nonstandard Ant source roots, generated/compatibility
surfaces, multiple client/server products, and frequent branch/worktree
switching. A binary or large generated index would be stale across workers,
create merge noise, and falsely imply semantic completeness around reflection,
plugin discovery, definitions, and configuration.

The lightweight navigation map and optional helper are now provided by
[`docs/myworld/info/code-navigation.md`](../info/code-navigation.md) and
`scripts/generate-code-tags.sh`. Their remaining operating rules are:

1. List the authoritative source roots, Ant targets/artifacts, plugin roots,
   generated/definition boundaries, and the key ownership maps already in
   plans. Link it from `docs/myworld/README.md` and workspace guidance.
2. The non-committed helper checks for Universal Ctags and runs `ctags
   --languages=Java --fields=+n
   --extras=+q -R` over maintained Java roots into `output/navigation/tags`.
   Rebuild it on demand; never consume it as authority for reflection,
   configuration, JSON/XML definitions, or plugin discovery.
3. Keep `rg`, `git log -S/-G`, compiler errors, and focused tests as the
   authoritative cross-reference workflow. If repeated work proves that
   declaration/reference resolution is the bottleneck, revisit JDT LS using an
   Ant-derived classpath, with its index ignored and rebuilt per workspace.

This is low maintenance, avoids stale checked-in artifacts, and materially
helps AI sessions locate declarations and line numbers without changing build
authority. A dependency graph should remain plan-specific: the global graph
would be noisy and unable to model dynamic plugins/reflection safely.

## Ordered follow-up branches

1. `docs/code-navigation-map` — add the lightweight navigation guide and an
   ignored optional Ctags helper; verify it does not enter artifacts/releases.
2. `docs/player-ownership-characterization` — inventory `Player` state and
   lifecycle families, their persistence/packet/combat/plugin consumers, and
   nominate exactly one safe extraction.
3. `docs/game-state-updater-phase-characterization` — freeze current tick
   ordering and failure boundaries before any `GameStateUpdater` move.
4. Only after the active layered-map roadmap selects a real owner seam:
   `docs/region-manager-boundary-characterization`.
5. Defer `mudclient`, `EntityHandler`, `PacketHandler`, client `World`, and
   renderer composite work until their responsible feature plans produce a
   narrow testable boundary.

Every implementation branch must preserve compatibility facades, compile the
affected maintained product, run its focused regressions, and obtain private
visual verification where client/rendering behavior is involved. No package or
top-level folder move is bundled with these extractions.
