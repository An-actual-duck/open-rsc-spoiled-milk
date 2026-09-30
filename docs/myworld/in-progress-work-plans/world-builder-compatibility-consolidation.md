# Spoiled Milk World Builder Compatibility Plan

Prepared September 30, 2026 for the owner and coordinating Core manager.

The goal is to make Spoiled Milk build and run its own maintained server and matching player client with the common World Builder map contract, preserving custom content, gameplay, maps, and player state. Compatibility must survive ordinary rebuilds and repeated map imports. Replacing the game with the editor runtime, or merely making discovery accept it, is not completion.

This is the execution guideline. Only baseline preparation and preliminary inspection are complete. No compatibility implementation, live rollout, or full preservation certification is implied.

## Authority and boundaries

The owner temporarily permits direct coordination and necessary work across Core, World Editor, and the runtime provider for this effort. Changes remain in their owning repositories, on focused branches, with exact commit reviews. Check each repository's current instructions and active worker ownership before assigning work; do not place two sessions in one checkout or silently repurpose an occupied worker. Do not merge another repository's history into Core.

The exception does not authorize public shutdown, restart, database replacement, deletion of historical evidence, or automatic acceptance of unfinished artwork. No Spoiled Milk name checks, special NPC IDs, fabricated capability receipts, or weakened verification checks may be used to force compatibility. Restore normal project separation at closeout.

## Starting evidence

- Preserved source-and-data baseline: `/home/justin/Core-Framework (copy)`, branch `baseline/map-compatibility-20260930`, commit `8ced8084cfbea1c4334d918fc6a6afebeab2b127`.
- Included Slayer revision: `50985ee43ea0b5b88bf35ecaee41f679322c1d55`. This includes later item art, Slow work, and a Naga artwork candidate; that candidate is not approved production art.
- Baseline manifest: `.baseline-preservation/baseline.json`. Original Git metadata, previous local configuration, and copied worktree registrations are preserved there. Normal pushes are disabled in the copy.
- Existing JARs were deliberately not rebuilt. The copy is not yet a coherent source/binary runtime or a certified current live-database backup. External paths and completeness of ignored data remain unverified.
- Preliminary manager-checkout archive inspection found `server/core.jar` expands to about 86.17 MiB, has 2,077 duplicated entry names, and has 373 duplicated class names with differing contents. This establishes an archive problem in that artifact, not the identity of every archive rejected in the owner's earlier attempt. Reproduce against the pinned disposable target.
- Both `server/world-builder-runtime/world-builder-managed-runtime.jar` and `server/core-gameplay-overlay.jar` are present; build checks refer to the gameplay overlay. Presence alone does not prove which classes a particular live launch loads.
- Other pending Core branches have not been integrated into this baseline. Inventory them and decide relevance without silently treating all pending work as part of this upgrade.

## Phase 1 Freeze versions and establish a safe workspace

1. Keep the prepared baseline unchanged. Create a separate disposable target and a focused implementation workspace under the relevant repository lifecycle. First resolve occupied worker slots through review/preservation; never reset or borrow one silently. The baseline copy is not a registered manager or worker.
2. Record baseline, Core, Editor, provider, packaged editor, Java, compiler, build-tool, and dependency versions and hashes. Record whether each test uses source-built or distributed artifacts.
3. Start from the handoff's references: Editor `9df39e131fe4f67f1c52074cc0658e2eca5ca1b1`, candidate `v0.8.1-alpha.6`, provider `deb55301702dc80f49497ac722341895145363e1`. The inspected Editor HEAD is now `128be0ccc06a7fa01d83875e693bbb29e27b9810`; review the relevant delta before choosing a newer test candidate. Do not assume current source documentation describes the old packaged candidate.
4. Inventory maps, placements, saved editor projects, assets, configurations, ignored files, links, and external paths. Create checksummed preservation manifests and restorable backups of everything the experiments could modify. Do not put credentials or player data into Git or public evidence.
5. Before launching anything, isolate database connections, bind addresses, ports, writable directories, scheduler jobs, Discord integrations, backups, and outbound integrations. Use disposable state and test accounts; no live database or real account writes.

Deliverables: pinned version manifest, workspace ownership, backup inventory, isolation checklist, and successful restoration of a disposable copy.

Exit gate: the test target can be changed and restored without touching the baseline, live deployment, or user-authored editor projects.

## Phase 2 Trace the actual runtime and preservation requirements

1. Inventory normal build, launcher, release, and deployment entry points. Record classpath order, manifests, Java agents, build-skip guards, runtime profiles, installed receipts, and historical overlays.
2. Determine class origins using archive analysis and, only in the isolated environment, class-loading evidence. Include server, client, plugins, and nested or bundled dependencies. Do not infer loaded implementation from source paths alone.
3. Compare source with active bytecode for affected classes and ABI consumers. Identify stale binaries, duplicate providers, and historical behavior absent from maintained source.
4. Build a behavior ledger for each overlay contribution: preserve in source, already supplied by source, obsolete with evidence, or unresolved. Review mixed classes such as `Player`, `Mob`, `Npc`, `World`, `Skills`, `Inventory`, `ActionSender`, and protocol enums individually.
5. Establish preservation tests and content inventories before modifying behavior: NPC/item/object IDs and overrides, appearance mappings, custom assets, dialogue, quests, shops, drops, equipment effects, plugins, collision rules, player progression, and saved map references.
6. Characterize all eight Slayer NPCs, their attacks/gimmicks and counter-items, unique equipment, leather effects, poison cleansing, and Slow. Include representative older custom content and overridden baseline IDs. Existing known bugs must be recorded separately from migration regressions.

Deliverables: class-origin report, source/binary mismatch list, overlay behavior ledger, and baseline preservation test matrix.

Exit gate: every active replacement affecting the upgrade is understood or explicitly blocks its dependent change. Unknown behavior is not silently retired.

## Phase 3 Correct archive and dependency packaging

1. Reproduce archive refusals with the pinned Editor and record exact archive, entry occurrences, expanded sizes, and byte differences.
2. Classify duplicate entries: identical notices, differing legal notices, classes, services, manifests, module descriptors, and other resources. Identify which dependency brings each occurrence.
3. Select coherent dependency versions using compatibility evidence. Preserve every required legal notice with deliberate packaging paths or combined notices; merge service registrations only where semantically valid. Do not blanket-drop duplicates or delete content to satisfy a size limit.
4. Fix source build configuration so normal builds reproduce the corrected packaging. Do not patch only an installed JAR. Preserve current runtime behavior and test plugin linkage against the chosen dependency graph.
5. Test archive intake, dependency resolution, server/client compilation, and ordinary packaging. Verify clean output builds twice in disposable directories; record toolchain-dependent differences rather than promising byte-identical archives without evidence.

Deliverables: reviewed dependency decisions, build changes, duplicate-entry report, legal-notice checks, and build results.

Exit gate: archives satisfy the pinned intake contract with no ambiguous class/resource resolution and no loss of required notices.

## Phase 4 Consolidate runtime behavior into Core source

1. Integrate required overlay behavior from the Phase 2 ledger into maintained Core source. Preserve Core content resolution, appearance loaders, interaction handlers, combat systems, and configuration ownership.
2. Rebuild server, matching client, plugins, and all affected consumers together. Resolve field-width and protocol ABI changes rather than keeping stale binary consumers or truncating values.
3. Only after equivalent behavior is verified, remove obsolete classpath injection and build-skip guards from build, launch, packaging, and deployment paths.
4. Archive retired overlays outside all active/discovered runtime paths, including `server/world-builder-runtime/world-builder-managed-runtime.jar`, `server/lib/world-builder-managed-runtime.jar` if present, and `server/core-gameplay-overlay.jar`. Preserve their hashes and historical transaction evidence. Do not erase receipts to silence a refusal.
5. Verify a normal rebuild and restart neither recreates nor loads retired shadow implementations and has no hidden dependency on editor binaries.

Deliverables: consolidated source, coherent artifacts, retained/retired behavior ledger, and class-loading evidence after restart.

Exit gate: the target owns its active implementation and passes preservation tests without shadow runtimes.

## Phase 5 Complete the shared map contract

Reference contract: `target-owned-layered-map-v1`, loader `generic-signed-layered-loader-v7-blocking-base-color`, protocol `world-builder-native-layered-protocol-v2-u16-elevation`, encodings 1–5. Read the exact pinned provider descriptor before implementing its requirements.

1. Produce a gap matrix for signed layers, terrain decoding, placements, activation/readiness, collision/void behavior, floor materials, and unsigned 16-bit elevation transport. Mark existing implementations verified, incomplete, or incompatible.
2. Integrate only missing or incorrect behavior. Review descriptor replacements and bounded edits against Core customizations; a map-related filename is not permission to replace a mixed gameplay class wholesale.
3. Preserve floor definition IDs and the full existing prefix. Verify server and client agree on appearance and walkability. Specifically reproduce base-color selection, black/void selection, and upper-layer behavior using the actual definitions; do not blindly assume IDs 0 and 10 mean the same thing everywhere.
4. Audit every known coordinate consumer before conversion: placements, NPC spawn/roaming bounds, doors/stairs, teleports, quest regions, saved locations, project exports, and custom scripts. Map known conversions consistently; stop affected migrations on unknown consumers. Do not assume a vertical offset fixes all coordinate conventions.
5. Preserve player/project data and stable content identities. Changing a custom NPC's position must not change its appearance, dialogue, drops, or combat behavior.
6. Test floor/wall browser paths if the selected Editor still reproduces the reported crashes. Any fixes there belong to the Editor and must be general, not ID-specific workarounds.

Deliverables: completed gap matrix, map integration commits, coordinate migration inventory, paired protocol tests, and restart evidence.

Exit gate: game and editor agree on map semantics while Core remains authoritative for custom gameplay and content.

## Phase 6 Establish verified imports and rebuild support

Investigate recognition early, after Phase 2 identifies the source lineage; do not wait until all implementation is finished to discover an unsupported adapter. Final acceptance follows Phases 3–5.

1. Try the existing reviewed `native-layered-v4-source-v1` path against a disposable target. Record each source-preimage, ABI, archive, or verification mismatch. Do not alter files solely to spoof accepted hashes.
2. If equivalent consolidated Core code cannot be admitted, implement a reusable verification/adoption mechanism or reviewed adapter in the owning Editor/provider repositories. It must prove the common contract and preserve target behavior; no server-name checks or blanket trust flags.
3. Use tool-generated, verified evidence binding the selected contract to actual source/artifacts. Never hand-author compatibility receipts or copy a project's capability JSON to force acceptance.
4. Complete discovery, upgrade/verification, first edited-map import, restart, and second map-only import. Compare before/after manifests: map-only imports may change only terrain, placements, and explicitly required activation metadata—not gameplay binaries, unrelated definitions, or captured editor presentation overrides.
5. Rebuild Core normally, then demonstrate the supported re-verification process and another successful import. Modified/stale source, tampered artifacts, incompatible pairs, and retired overlays must still be detected and refused.

Deliverables: verified upgrade/import evidence and a repeatable post-rebuild verification procedure with drift-refusal tests.

Exit gate: compatibility is maintainable after normal development, not just a one-time successful import.

## Phase 7 Run acceptance and recovery tests

| Area | Required evidence |
| --- | --- |
| Content | All eight Slayer NPC visuals and combat; unique items/effects; old custom content; overridden baseline IDs; shops, dialogue, plugins, and object interactions |
| State | Disposable account inventories, equipment, progression, quest state, and locations preserved; copied live-state handling reviewed separately |
| Maps | Ground, upper, and negative layers; layer transitions; sector boundaries; all supported encodings; elevations including 255, 256, and 65535; floors, voids, collision, projectiles, placements, and bounds |
| Import | First and repeated imports; unchanged non-map content; existing editor project edits retained; restart and reconnect |
| Rebuild | Clean source builds, paired artifacts, post-rebuild recognition/import, and absence of overlay reactivation |
| Failure | Preflight refusal without mutation; controlled failures at transaction stages; interrupted recovery; exact prior-state restoration or explicitly recoverable state |
| Distribution | Matching client packaged with required custom assets; supported launch environments tested and any untested platform stated |

Run relevant Core combat, content, status, asset, map/protocol, and packaging suites plus the pinned Editor/provider integration and transaction tests. Existing synthetic provider tests are necessary reference evidence, not proof that this customized game passed. Record commands, exact commits, results, skips, and limitations; do not remove tests merely to obtain green results.

Deliverables: acceptance report, hash comparisons, screenshots or private-playtest evidence, and demonstrated rollback instructions.

Exit gate: no unresolved content-loss, state-corruption, unsafe-import, or source/binary-coherence issue. Obtain owner acceptance of private visual/gameplay checks.

## Phase 8 Integrate and deploy safely

1. Review exact READY handoffs in each owning repository. Reconcile the latest Slayer branch and any intervening work; do not merge the baseline's local port/capability snapshots as production defaults. Rerun integration tests after merges.
2. Publish tested changes through normal manager workflows. Pin reviewed provider dependencies and record the Editor version needed for the verified workflow.
3. Prepare the matching player client, deployment inventory, consistent live database/map backups, restore procedure, and post-activation checks. Do not assume the prepared copy contains the latest live state.
4. Request fresh explicit public-server shutdown permission for the maintenance window. Follow the required `::update` warning and graceful shutdown process; never substitute process signals to bypass it.
5. Deploy only after the gate is satisfied. Confirm runtime class origins, map loading, custom visuals/gameplay, account state, and compatibility evidence. Restore the backed-up paired runtime/map/state if acceptance fails; do not discard any new player progress without reconciliation.

Deliverables: reviewed commit list, release artifacts, backup/restore records, rollout results, and known limitations.

Exit gate: owner-approved rollout and validated operational behavior. If shutdown approval is absent, stop at release-ready and leave the public server running.

## Phase 9 Close out the exception

Document normal build, verification, map import, backup, and recovery procedures. Restore ordinary Core/Editor/provider separation, recycle only eligible merged workers, and retain historical backups according to an agreed retention decision. The owner should no longer need to juggle the managers for ordinary map imports.

## Stop conditions

Pause the affected change and report evidence when an unknown overlay contribution, unexplained dependency conflict, unsupported binary-only consumer, ambiguous floor-ID collision, unknown coordinate conversion, unsafe database endpoint, or unpreservable custom behavior is found. Continue independent read-only work where safe; do not guess or weaken validation.

Do not call this complete based on content discovery, a successful compile, one map import, or a static sprite preview. Both active behavior preservation and post-rebuild import verification are required.

## Progress and evidence tracking

- Complete: source-and-data baseline preparation, with the limitations above.
- Complete: exact disposable-copy verification and initial static source, archive, overlay, and adapter audit; see [initial audit](world-builder-compatibility-initial-audit.md).
- Phase 1 partial: versions pinned and 215,935 copied file/link entries verified; launch isolation and restoration drills remain.
- Phase 2 partial: dependency conflicts and overlay/API differences identified; source fits existing adapter checks, but actual consumer and active behavior verification remain.
- Next: resolve focused implementation workspace ownership, complete isolation/preservation checks, and correct Core dependency packaging. Generic post-rebuild Editor verification is confirmed as a separate required implementation.
- Pending: all consolidation, compatibility implementation, full acceptance, and rollout phases.

At each checkpoint, record the phase, repository and commit, affected paths, retained behavior, exact test commands/results, evidence locations, remaining blockers, and next action. Update this document as facts replace assumptions. Keep private artifacts local; publish only sanitized reports.

## Reference documents

- Owner handoff: `/home/justin/rsc-world-editor/docs/CORE-FRAMEWORK-MAP-COMPATIBILITY-HANDOFF.md`.
- Targeted upgrade boundary: `/home/justin/rsc-world-editor/docs/WORLD-BUILDER-TARGETED-MAP-UPGRADES.md`.
- Test scope and archive rules: `/home/justin/rsc-world-editor/docs/WORLD-BUILDER-TARGETED-INTEGRATION-TESTS.md` at the selected revision.
- Provider descriptor: `server/conf/world-builder/target-map-integration-v1.json` at `deb55301702dc80f49497ac722341895145363e1` in `/home/justin/rsc-world-editor-runtime`.
- Baseline record: `/home/justin/Core-Framework (copy)/.baseline-preservation/baseline.json`.
- Core lifecycle and deployment: `AGENTS.md`, `docs/workspaces/README.md`, and `docs/workspaces/live-deployment.md`.
