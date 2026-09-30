# Core Map Compatibility Initial Audit

September 30, 2026. This records the opening audit for the [compatibility plan](world-builder-compatibility-consolidation.md). It is not a runtime acceptance or deployment report.

## Preservation and workspace

The preserved baseline at `/home/justin/Core-Framework (copy)` remains unchanged. A separate disposable target was created at `/home/justin/core-map-compat-audit-5XvTqI/target`, beneath a private directory. A recursive comparison hashed regular files and compared symbolic-link destinations without following them.

- Compared 215,935 file/link entries, totaling 34,312,306,064 regular-file bytes.
- No differences between baseline and disposable target.
- No symbolic links resolving outside their respective copy roots.
- Private evidence: `/home/justin/core-map-compat-audit-5XvTqI/preservation-inventory.json`; reproduction script: `inventory.py` in that directory. Do not commit the inventory or copied private data.
- No game server or client was launched, stopped, rebuilt, or modified. The copied runtime artifacts are still the original artifacts, not certified matches for the newer source.

This establishes a preserved copy, not full launch isolation. The copied development config uses SQLite, loopback binding, and development ports, with Discord feature flags disabled. Launch scripts can source `server/local.env`; test environment overrides, unique ports, fresh test account/database state, output paths, and outbound integrations must be checked before a launch. No database records were inspected. A current consistent live database backup is still a rollout requirement.

No implementation worker was repurposed. Existing Core slots remain occupied by their original tasks. Read-only audit assistance did not edit those worktrees. Before implementation, resolve an appropriate slot through reviewed preservation/integration, or explicitly establish an approved focused workspace; the disposable copy is not a registered Core worker.

## Pinned inputs

| Input | Verified identity |
| --- | --- |
| Baseline source | `8ced8084cfbea1c4334d918fc6a6afebeab2b127` |
| Included Slayer source | `50985ee43ea0b5b88bf35ecaee41f679322c1d55` |
| Packaged alpha.6 Editor | `9df39e131fe4f67f1c52074cc0658e2eca5ca1b1` |
| Current Editor source | `128be0ccc06a7fa01d83875e693bbb29e27b9810` |
| Packaged and current provider | `deb55301702dc80f49497ac722341895145363e1` |
| Integration descriptor SHA256 | `ceaf0d7c1aa6dfb7f487ba1d753c97c00758511d0148ddbe08b87b487d0357c8` |
| Default Java and compiler | Temurin 8u482 |
| Available targeted-upgrade toolchain | Temurin 17.0.20+8, both `java` and `javac` verified |
| Bundled Ant | 1.10.5, invoked through `sh` |

The Java 17 toolchain is present in `/home/justin/world-builder-test-builds/targeted-runtime-v0.8.1-alpha.6/smoke/World Builder 2/runtime/bin`. No system Java default was changed. Pin this toolchain explicitly for subsequent compatibility checks.

The current Editor has archive diagnostics and narrow identical-legal-notice handling absent from alpha.6. The packaged and current integration descriptors match. Use current Editor source with the pinned provider for subsequent verification, recording the newly built application hash when available; keep alpha.6 only as a historical reproduction input. No new Editor build was made in this audit.

## Archive findings

The baseline `server/core.jar` expands to about 86.17 MiB. It contains 2,077 duplicate entry names, including 2,065 duplicated class names. Of the duplicated entries, 385 have differing contents; 373 of those are class names.

The conflicting class occurrences were attributed as follows:

| Conflict family | Count | Inputs |
| --- | ---: | --- |
| Guava classes | 367 | Standalone Guava 30.1.1 and Guice bundled Guava 30.1 |
| jsr305 classes | 5 | JDA bundled 3.0.2 and Guice bundled 3.0.1 |
| Java 9 module descriptor | 1 | Log4j API and SLF4J no-op provider |

`server/build.xml` packages every library archive through `zipgroupfileset`. The duplication is therefore a reproducible build concern, not merely an installed-file repair. Different notices, service registrations, and other resources also require deliberate treatment. The exact dependency versions to retain have not yet been selected or tested.

The newer Editor's tolerance for byte-identical legal notices will not resolve duplicate classes, conflicting notices, or ambiguous dependencies. Do not apply blanket duplicate removal or substitute generic runtime binaries.

## Class ownership and overlay findings

Baseline Ant `runserver` and `runserverzgc` specify logging/XML/MySQL/lang dependencies, then `core-gameplay-overlay.jar`, `lib/*`, codec, and `core.jar`. The managed runtime archive is not in those launch paths. `compile_core` still creates the gameplay overlay, and `scripts/audit-server-build.py` still requires its launch precedence.

Read-only process metadata confirms the currently running public JVM also declares that general classpath pattern and excludes the managed-runtime path. This is evidence of launch arguments, not an instrumentation trace of loaded classes or proof that live artifacts equal baseline artifacts. No JVM attachment or live action was performed.

| Archive | Observation | Next treatment |
| --- | --- | --- |
| `core-gameplay-overlay.jar` | 18 classes; all byte-identical to their baseline `core.jar` counterparts; source exists | Verify equivalent behavior without it, then retire its build/launch dependency |
| `world-builder-runtime/world-builder-managed-runtime.jar` | 1,643 classes; 1,570 overlap Core, 277 overlapping classes differ; 73 managed-only classes | Preserve as historical evidence; do not reactivate; identify any required map behavior for deliberate source integration |

Of the managed-only classes, 67 lack a matching baseline source-class family. These need classification before a complete retained/retired behavior ledger can be claimed; absence alone does not establish that they are required.

Method/field signature inspection of mixed classes shows substantial differences. For example, Core has Slayer services/state absent from managed `World`; managed `World` has map operations absent from Core. Core-only signatures also exist in `Player`, `Mob`, `Npc`, `Inventory`, `ActionSender`, and `OpcodeOut`. Reintroducing the managed snapshot would risk removing custom behavior.

An important ABI difference is confirmed: managed `TileValue.elevation` is `int`, whereas baseline Core/source use `byte`, with constructor differences. The newer managed terrain tile also has void helpers absent from Core. Integrate required changes coherently through all consumers and plugins; do not truncate wide values to retain old binaries.

## Existing adapter source eligibility

A read-only comparison against `native-layered-v4-source-v1` found:

- All eight source policies satisfied: five accepted exact preimages and three permitted absent additions.
- All eight integration requirements satisfied their applicable hashes and executable fragments.
- All 32 bounded edits across 11 transforms passed an in-memory sequential executable-anchor simulation.

This is source eligibility evidence only. The actual consumer, isolated compilation, source-to-bytecode checks, transitive ABI checks, floor-prefix checks, and transaction have not run against this target. Archive admission and retired-overlay refusals remain independent gates.

No evidence currently justifies widening the adapter, loosening hashes, or adding a Spoiled Milk exception. First make Core's artifacts coherent and unambiguous, then exercise the existing adapter and record its real remaining refusals.

## Rebuild verification gap

The Editor's `WorldBuilderTargetMapIntegration` binds installed proof to exact source/archive hashes. `WorldBuilderAdaptiveImporter` rejects another upgrade after an outstanding successful transaction. The current `docs/WORLD-BUILDER-RUNTIME-REVERIFICATION-PLAN.md` explicitly describes proposed, not shipped, behavior.

Consequently, ordinary Core rebuilds do not yet have a demonstrated supported path back to imports from the existing project. This is a separate generic Editor transaction/history task. It must verify current binaries and contract behavior, preserve project edits and rebuilt artifacts, retain historical evidence, and reject unrelated drift. Updating hashes alone is not sufficient.

## Next checkpoint

1. Resolve implementation workspace ownership without discarding existing branches.
2. Complete launch isolation and the overlay/behavior preservation ledger; establish representative baseline tests.
3. Correct dependency packaging on a focused Core branch and produce a coherent server/client/plugin build with the selected toolchain.
4. Prove and retire obsolete runtime indirection, then try the existing map adapter on the disposable target.
5. Implement and test the generic post-rebuild verification workflow in the Editor as a separate bounded change.

Phase 1 is partially complete: versions and exact-copy evidence are established; launch isolation and restoration drills remain. Phase 2 has initial static evidence; active behavior preservation and consumer verification remain pending. No compatibility acceptance, game-content change, or deployment has occurred.
