# World Builder content lifecycle implementation handoff

Prepared October 2, 2026 by the Core manager for the RSC World Editor manager.

The owner needs to finish the Slayer Tower and other major Spoiled Milk content. Repeated compatibility failures have delayed that work and made the owner the integration tester between projects. The requested outcome is a dependable editing workflow, not another isolated compatibility exception. Substantial refactoring is appropriate where it removes a demonstrated architectural obstacle and shortens the path to that outcome.

The recommended approach is to retain the working editor, map conversion, isolated projects, and transactional import machinery, while correcting the boundaries between available content, map placements, saved project revisions, and target runtime compatibility. Do not begin a wholesale rewrite or a broader game platform replacement by default.

This is an implementation handoff and acceptance plan, not a claim that the architecture below already exists. The Editor manager owns the main effort. Core remains responsible for its maintained game integration and content export. The owner should receive a tested candidate with a short inspection checklist rather than successive requests to rediscover routine failures.

## Required user experience

The owner's example is the normative product intent:

1. Detect the server and scan its effective content.
2. Discover that NPC 866 is custom content, resolve its definition and visuals, and make it available to the project.
3. Save that content with the project so opening the project later does not depend on reconstructing the same discovery session.
4. Let the owner place, move, duplicate, or remove that NPC without invalidating the available content library.
5. Offer **Detect New Content** when the server gains content or changes existing content.
6. Preserve map edits while incorporating reviewed content updates.
7. Save and import maps into the maintained game through a compatible map loader, preserving gameplay.

Apply this behavior consistently to NPCs, ground items, scenery, boundaries, floor and wall materials, and their required sprites, animations, models, and textures. Available content includes unplaced definitions. Replacing a vanilla definition is also custom content; a numeric ID above the vanilla range is not the only discovery criterion.

The ordinary owner should not need to select provider JSON files, understand catalog fingerprints, recreate projects after adding an NPC, or ask an AI to repair evidence files. Advanced diagnostics remain available, but are not the ordinary workflow.

## Scope and ownership

### Editor manager

- Own discovery adapters, content normalization, provider and cache lifecycle, project creation and reopening, explicit content refresh, import preflight, UI, application updates, and packaged acceptance.
- Coordinate runtime-provider changes only where the editing runtime needs a new normalized content or loader capability. Use that repository's own manager and workers.
- Drive the cross-component test sequence and produce one consolidated handoff to Core.
- Reuse the existing format-aware discovery architecture where it is sound. Audit implementation against its claims rather than treating a prior phase label as proof of the complete workflow.

### Core manager

- Own the maintained Spoiled Milk server/client, authoritative content sources, export of effective content, catalog generation, and game-side map loading.
- Integrate reviewed Core changes through Core's workflow, preserving current Slayer work and unrelated modifications.
- Verify custom gameplay after imported maps are installed in an isolated private game session.
- Own any later normal-target migration or public deployment. This document does not authorize a public shutdown or deployment.

### Boundaries

Ordinary map imports may install verified map packages and the explicitly reviewed map activation metadata. Project refresh changes project content, not server gameplay. Any necessary target content installation or loader upgrade must be separately described, previewed, and confirmed; it must not be hidden inside a map import.

The editor must not replace Core's NPC behavior, combat rules, items, progression, accounts, or unrelated runtime code with its editing runtime. A compatible loader does not require identical gameplay across servers. Map format or protocol changes may require coordinated server and client work, but that does not grant authority to standardize the entire game.

Arbitrary executable custom behavior cannot be safely inferred from files or translated automatically. For code-generated definitions, Core should provide a trusted build-time content export. Editor discovery must consume inert data, not execute an unknown target JAR or plugin. Unsupported formats require a bounded adapter or actionable report, not guesses.

## Evidence and present state

The following observations distinguish confirmed failures from recommendations.

| Observation | What it establishes |
| --- | --- |
| The owner tested private game loading, old custom areas, floor transitions, collision, equipment, combat, bank and persistence successfully. | The maintained runtime candidate preserves important existing behavior. It does not establish the final edited-map workflow. |
| Alpha 9 discovery passed but fresh project creation omitted an integration proof from its copied evidence. Alpha 10 addressed that path. | Discovery success alone is insufficient to establish project creation success. |
| A Naga placement was editor-valid but failed after import mutation against stale target catalogs; rollback verified successfully. | Editor content and target catalogs were separate, inconsistent authorities. |
| Core refreshed its paired catalogs from effective definition sources. The corrected target contains 877 NPC IDs and 3,408 item IDs. | Core had a real stale-catalog issue, not a nonexistent NPC. Counts describe this snapshot, not permanent constants. |
| That correction triggered TARGET_DRIFT for the preexisting edited project. | A supported content refresh or rebinding workflow is necessary; rewriting immutable evidence is not a solution. |
| Alpha 11 added pre-write placement checks. Alpha 12 handles exact already-active packages without treating them as new destination collisions. | Useful safety fixes exist and should be retained. |
| A new edited project imported successfully. Its active map package differs from the earlier baseline. | A real edit/import has succeeded; do not revert the target to a pre-edit map during setup. |
| Rediscovery then rejected the provider because its recorded placed extension NPC ID set differed from the target. | Content/provider validity remains coupled to map population. Coordinates and counts are not the equality check; the set of extension types is. |

The original provider symptom does not justify simply changing equality to a permissive condition. Establish which available definitions and visual dependencies are authoritative, then validate placed IDs against that authority.

The owner's concern about repeated nudging is supported by these lifecycle gaps. The tool's age or the model that helped create it is not evidence that a full rewrite is necessary.

## Architectural requirements

### Separate content from placements

Maintain distinct concepts:

- **Content revision:** effective definitions, visual and semantic dependencies, provenance, stable identity mappings, and supported capabilities.
- **Map revision:** terrain and placements referencing that content.
- **Target compatibility:** loader/schema capabilities and the effective content supported by the destination.
- **Transaction evidence:** exact files and states bound to a particular preview and apply operation.

Moving an NPC, adding another copy, placing a supported type for the first time, or removing the last placement of a type changes the map, not the content revision. A provider covering a valid superset of placed definitions should not be rejected merely because some definitions are unused. A newly used ID absent from the project library is a missing-content case and should request refresh or resolution.

Keep strict hashes where exact byte identity matters, especially immutable history and preview-to-apply checks. Do not use a historical map's population or an obsolete whole-server snapshot as the permanent definition of runtime compatibility.

### Use one effective content model

Normalize selected base definitions, custom additions, overrides, removals, and asset lookup rules into one effective model. Derive renderer inputs, catalog membership, placement validation, dependency checks, and diagnostics from this model or verified projections of it.

Core must export what the game actually loads, not all similarly named files on disk. Inactive patches, backups, and examples must not become available content. Preserve numeric IDs and their meaning on round-trip; any remapping for portable region imports is a separate, explicit operation with a verified mapping.

Dependencies must resolve by the game's real lookup semantics. Copying an archive is not proof that an animation uses the right frames or that a scenery object has the right collision geometry. Missing optional presentation may use conspicuous placeholders under established policy; unresolved IDs, geometry, or collision semantics cannot be silently substituted.

The editor's project-local visualization definitions are not a license to write simplified gameplay definitions back to Core.

### Keep projects durable and self contained

A project should reopen from its captured map and content even when the target is offline or has subsequently changed. Opening must not silently refresh from the target. Target unavailability may disable import, not destroy access to saved editing work.

Keep historical content revisions, exports, receipts, and backups valid under their original evidence. Separate replaceable generated caches from durable project data through explicit, tested migration. Application upgrades must retain project registry entries, selected project, edits, and history; a save that exists only in a moved backup is not sufficient continuity for the user.

### Implement Detect New Content

Provide an explicit project action, not only a fresh-project discovery path or cache reset.

1. Scan the selected target using its supported adapter or content export.
2. Compute additions, changed definitions/assets, removals, and identity conflicts relative to the project's current content revision.
3. Show a concise review with affected map references and blockers.
4. Validate dependency completeness before acceptance.
5. On confirmation, create a new content revision and regenerate necessary project caches atomically.
6. Keep map edits, terrain, placements, saved history, and prior content revisions intact.
7. Establish the supported relationship to the current target for subsequent import without rewriting old receipts or source snapshots.

Safe additive changes should require one straightforward confirmation. Changed identity meanings, unsupported schema changes, and removal of content used by the map require explicit resolution. If removal is deferred, retain the project data but block deployment to a target that cannot support it. Retaining an old visual locally must not falsely imply that the target still supports that ID.

A content refresh must not replace the project's working map with the target map. External map changes require a separate conflict/rebase decision. The implementation may use a successor project revision internally, but the owner should not have to abandon edits or manually copy files.

### Bound loader compatibility

Define a stable, versioned contract for map encoding, placement semantics, collision, content identifiers, required client assets, and activation. Distinguish input-format adapters from the destination loader contract.

Prefer a maintained Core integration that ordinary builds retain. If a newer map format requires an upgrade, preview the narrow server/client impact separately. Source and archive checks remain justified for a specific migration; they should not make every unrelated gameplay change a map migration.

Do not simply remove broad checks. Replace them with tests and evidence scoped to the loader interface and relevant dependencies. The initial delivery need not support every hypothetical custom engine: declare the supported format families truthfully and keep new support behind adapters.

### Preserve transaction safety

Retain paired server/client validation, full preflight, confirmation binding, offline requirements, atomic publication, rollback, and corruption refusal. Validate all exported references against the effective post-transaction target content before mutation and recheck after confirmation.

An exact already-active package is a no-op or an explicit metadata-only operation. Inactive package collisions, altered files, extra entries, missing dependencies, and unsafe paths are not automatically reusable. Caches are conveniences, not authority to overwrite a target or rewrite history.

## Delivery sequence

### Phase 1 Establish one reproducible lifecycle fixture

Before production refactoring, reproduce the latest provider mismatch on a sanitized fixture and capture the entire failing lifecycle in automation. Preserve the owner project separately.

Inventory the actual authorities used at discovery, project creation, content normalization, reopen, export, import preview, apply, and rediscovery. Identify duplicate catalogs and every exact-match dependency. Record which checks protect a transaction and which incorrectly bind ordinary editing to an old population.

Reconcile this plan with existing Editor architecture documents. In particular, broader Current Base/Advanced platform replacement work is not automatically a prerequisite for this delivery. Record a short decision explaining what existing components will be retained and what boundaries must change.

Exit: an automated reproduction plus a bounded implementation design. No additional owner retest is needed to establish already-reported failures.

### Phase 2 Make existing content survive the complete round trip

Separate placed IDs from available definitions, repair provider regeneration/selection semantics, and ensure catalog agreement derives from effective content. Make fresh detection and continuation both work after imports, including placing previously unplaced supported types.

Retain the alpha 10 through alpha 12 fixes and their regression cases. Do not solve the latest problem with an NPC 866 exception or a cache deletion instruction.

Exit: discover, edit, save, import, rediscover, reopen, and repeat pass for all supported content families using the maintained target. This is the first candidate that could unblock tower construction with existing content, but the refresh objective remains unfinished until Phase 3.

### Phase 3 Support safe content evolution

Implement Detect New Content and the project revision transition described above. Use one additive example and one conflict example in each relevant family. Preserve existing edited projects through a supported migration instead of requiring recreation.

Coordinate a Core change so content export/catalog generation is part of the maintained workflow, with a stale-content check before release/import readiness. The current manual catalog refresh is a recovery step, not the final user experience.

Exit: newly added server content becomes usable in the same edited project, imports successfully, and survives reopening and another application update.

### Phase 4 Verify the actual maintained game

Using isolated copies, import a visible terrain change, collision-bearing scenery, and representative custom NPC placements. Launch the actual Core server and player client, not only the editing runtime. Check the new placement locations, floor transitions, visuals, collision, and custom behavior.

Core should verify representative Slayer mechanics, including Naga melee/ranged behavior and another status-effect enemy, along with custom equipment and persistence. Unrelated gameplay files and account data must remain unchanged by map imports.

Exit: automated checks and a concise private owner inspection both pass. Core then prepares exact-commit integration into the normal maintained checkout; temporary test activations and target-specific receipts are not blindly copied into normal Core.

### Phase 5 Package and hand off

Build the candidate from the exact tested commits and pinned runtime dependency. Verify the packaged artifact, not just source-tree tests. Exercise update-in-place over a populated installation and preservation of the selected project.

Supply one launch path, one selected project, the imported test coordinates, and a short owner checklist. State what has and has not passed. If a later fix changes shared validation or lifecycle code, rerun the relevant end-to-end sequence before requesting another owner test.

## Acceptance matrix

The Editor manager should implement these as automated product-level tests with synthetic fixtures, supplemented by a disposable copy of the real project. A passing helper test alone is not acceptance.

| Case | Required outcome |
| --- | --- |
| Discover supported custom content never placed in the map | It appears in the project library with dependencies and provenance. |
| Existing vanilla ID overridden by the server | Effective override is adopted without changing its ID or meaning. |
| Place a previously unplaced supported NPC or object | Save/import/rediscovery succeeds without regenerating a hand-authored provider. |
| Move or duplicate a placed type | Coordinates/counts change without invalidating content. |
| Remove the last placement of a type | The available definition remains valid; rediscovery succeeds. |
| New item, material, model or animation dependency | The same closure rules apply, not an NPC-only solution. |
| Add content to the server after project creation | Detect New Content adds it while preserving all map edits. |
| Change a referenced definition or remove it | Clear conflict review; no silent substitution, deletion or accepted unsupported import. |
| Change the target map externally | Explicit map conflict handling separate from content refresh. |
| Close and reopen with target unavailable | Saved map/content remain usable; import availability is reported separately. |
| Update the editor application | Project selection, edits, content revisions, exports and receipts survive. |
| Exact unchanged export | No unnecessary map installation and no false destination collision. |
| Two successive changed exports | Both imports succeed and retain prior history. |
| Additive content refresh followed by another import | New compatible lineage is established through a supported transition. |
| Tampered content or changed target after preview | Refused before target mutation; no weakened validation. |
| Injected write failure | Exact rollback or explicit recoverable state, retaining project edits. |
| Ordinary compatible Core rebuild | Supported verification succeeds without replacing gameplay. |
| Actual private game after import | New map/placements work and existing NPC mechanics remain intact. |

Include at least one test driven through the packaged desktop detect/create/import path, since CLI discovery success previously missed later failures. Confirm that a supposed edited export actually differs from the installed map before claiming an edited-map test passed.

Measure discovery, opening, refresh, preview, and import duration on representative fixtures. Report phase timing and visible progress; past imports have taken several minutes. Optimize repeated immutable reads only where justified by measurements, without treating cached success as proof that a mutable target stayed unchanged.

## Owner burden and implementation discipline

- The managers should coordinate technical dependencies directly through exact written handoffs. Do not make the owner relay unexplained hashes, provider paths, or code-level diagnoses repeatedly.
- Use focused workers in the owning repositories under their normal rules. Independent adapter, lifecycle, and acceptance tasks can proceed in parallel after the shared contract is settled; do not have multiple agents edit the same worktree.
- Provide milestone updates with outcome, remaining blocker, and whether owner input is genuinely needed. Do not present discovery-only or preview-only success as completion.
- Batch routine failures into a tested correction cycle. The owner should primarily supply product decisions and final visual/gameplay feedback.
- Make narrow implementation decisions autonomously within this scope. Escalate loss of data, ambiguous gameplay semantics, incompatible ID changes, broader runtime replacement, or new authority requirements.
- Set a bounded Phase 1 review, then implement. Do not turn urgency into either unreviewed patches or an open-ended architecture project. The manager should supply a dependency-based estimate after reproduction, not promise a speculative delivery date now.
- Deliver the earliest genuinely reliable existing-content workflow without calling the full objective complete until content refresh and preservation pass. Wider ecosystem support and unrelated editor features must not delay the agreed supported-target workflow.

## Preserved artifacts and starting points

These paths are local evidence and coordination inputs, not portable production defaults. Inspect them read-only; copy sanitized fixtures for mutations. Check for running sessions before touching any shared installation.

- Upgraded disposable Core target: `/home/justin/core-map-alpha9-retest-ePUpKw/target`.
- Current test editor: `World Builder 2` beneath that target, last installed as alpha 12.
- Selected successful project: `World Builder 2/projects/aa1a3167-0acc-444e-8cd8-ddbe1b80b0ce`.
- Successful import receipt: `078bfd4b-2cd2-45a5-8d20-b835a7df0230` in that project's receipts.
- Last verified active server/client package: `9efa7d773dea15a75d349d06e9e7343f93e5c7aa4c6d575316a4cc3b6fafc725`. It matches that project's export and contains the successful edit; recheck current state before acting.
- Earlier failed edited project: `431eaf7b-6561-4fe6-85a7-7743da93bc8f`, with verified rollback receipt `d28a3ab8-9463-43bc-b0cc-a28d881a7546`.
- Intermediate fresh project: `746cdbe9-798c-4854-ba9c-6075a3e3be93`; its earlier export matched the active baseline and exposed the already-active package issue.
- Provider in the latest rediscovery error: `World Builder 2/providers/provider-0f3ae0dc87f6a864/npc-definitions-v1.json`.
- Prior installation backups reside beside the target under `world-builder-alpha10-preserved` and `world-builder-alpha11-preserved`; other older backups also exist. Do not delete or repurpose them.
- Separate private playtest runtime: `/home/justin/spoiled-milk-map-playtest-T0Jn3w`. This is not the import target and needs a reviewed refresh before testing the newest imported map. Preserve its private test database; do not copy public account data.
- Core integration source checkpoint: `72ef40014a1cc9c6df6c96af1a0ec44a69e20e4b`; catalog correction: `3b9ed5950` in the disposable target. These are not yet integrated into normal Core main.
- Alpha 12 packaged evidence: `/home/justin/world-builder-test-builds/targeted-runtime-v0.8.1-alpha.12/TESTING.txt`, `VALIDATION.txt`, and `acceptance-results.json`.

Relevant Editor code includes `WorldBuilderAdaptiveDiscovery`, `WorldBuilderProjectContentBundle`, `WorldBuilderSupplementalNpcDefinitions`, `WorldBuilderNpcDefinitionProvider`, `WorldBuilderPortableProvider`, `WorldBuilderContentReconciliation`, `WorldBuilderAdaptiveProjectLifecycle`, `WorldBuilderAdaptiveMutationProfile`, and `WorldBuilderTargetMapIntegration`. These are investigation points, not a mandate to rewrite every class.

Read the Editor's `docs/ARCHITECTURE.md` and `docs/WORLD-BUILDER-2-FORMAT-AWARE-DISCOVERY.md`, including the explicit deferral of existing-project refresh. Core's preceding context is in [World Builder compatibility consolidation](world-builder-compatibility-consolidation.md). The Editor manager should reconcile any conflicting broader roadmap assumptions explicitly.

## Required final handoff

Return exact Editor and runtime-provider commits, package checksums, Core prerequisites, schema/migration notes, and the affected-area and end-to-end test results. Distinguish manager-run evidence from owner verification and identify any untested platform.

State the target files allowed to change for map import, content refresh, and loader upgrade separately. Include preservation evidence for saved projects, custom content, prior receipts, maintained game code, and unrelated target files. Explain any intentional changes rather than comparing only total file counts.

Provide a short operational guide: detect, continue, detect new content, save, import, and recover. Include an update path that retains saves and a clear diagnostic route for genuinely unsupported content.

Completion means the owner can build the Slayer Tower, place the supported custom monsters, import, return later, add new supported content, and continue without compatibility surgery. Merely suppressing the latest error, recreating a project each time, or demonstrating one successful import does not meet that outcome.
