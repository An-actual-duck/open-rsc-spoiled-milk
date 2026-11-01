# Next-update candidate: report-only code review

Reviewed November 1, 2026. No implementation fixes, formatting changes, dependency changes, server launches or deployment operations were performed.

## Executive assessment

The reviewed gameplay paths are substantially better defended than a collection of untested patches: actor-lifetime receipts, server-owned admission, explicit damage provenance, atomic item exchanges and deterministic runtime fixtures are present and exercised. An additional economy pass found a **high-priority inherited auction transaction defect**, reproduced against real task classes using an injected in-memory database and a network-denying guard: failed purchases/claims can retain player item grants while leaving the durable listing/claim available again (F5). No real database was opened. No new combat/client-carrier failure was confirmed in the reviewed paths. This is a scoped review result, not a guarantee about every file or the final release package.

Alongside F5, there are four concrete problems in **release verification itself**. Several older tests reject the intentional current content; the coating generator's byte check fails on a pixel-identical image; the existing lint gate fails on three findings absent from its baseline; and two loot fixtures reach their success markers before asynchronous SQL logging makes their isolated JVMs exit unsuccessfully. These should be resolved or explicitly dispositioned before declaring the candidate fully validated. Do not change correct gameplay to satisfy obsolete tests.

The strict combat characterization gate passed **145/145 scenarios**. Focused checks also passed current Cleric buffs/hostiles, production and mixed-resource costs, high NPC indices and lifecycle reuse, shared Stagger/Slow, Cosmic luck, coatings, antidotes, Slayer encounters/rewards/leathers, tower guards, client schema4 and floating hit numbers. The final authored map, assembled distributable, combined private gameplay, real database upgrade/restore and live activation were not tested.

My recommendation is to resolve the inherited auction failure boundary, complete a small validation-repair pass, and perform the already-planned combined release acceptance. A broad architecture rewrite before this release is not justified by this review.

## Immutable scope and workspace

| Item | Verified value |
|---|---|
| Frozen source target | `6c487d699a77b98824ed568189609d690feab758` |
| Integrated implementation parent | `788a70c685abff5d078d77d1d90c168f4c8531ae` |
| Review worktree / branch | `/home/justin/Core-Framework-ai-2`, `docs/next-update-code-review` |
| Assignment | `docs/myworld/in-progress-work-plans/dot-next-update-code-review.md` at the frozen target |
| Broad comparison / common ancestor | `fec94c8731b5521410963575ef0f2fa5c05ef0b3` |
| Deployed source / latest local public tag | `a5043328f99995f1e77681b5b06f84dac72a9f9c`, `v0.2.89` |

The manager assignment explicitly reserves this worktree and permits only this tracked report addition. Initial status was clean at the supplied SHA. Main, other workers and live checkout were not edited. About 127 GiB was available at preflight; no repository copy or new backup was made. Builds and diagnostics used ignored worker artifacts and temporary fixture folders. Final review evidence is retained under ignored `output/next-update-code-review/`.

The broad diff contains **1,493 tracked paths**, 89,883 insertions and 6,746 deletions. The deployed-to-candidate final-tree diff contains **1,507 paths**. Deployed history is not an ancestor: `git rev-list --left-right --count DEPLOYED...TARGET` returned `17 333` including the review checkpoint. The common ancestor was verified. No missing-fix claim was inferred from differing cherry-pick hashes. The ordinary hosted/package scripts inspected against the deployed reference retain those deployed changes; this is not a proof of all deployed-side behavioral parity.

## Confirmed actionable findings

Severity here measures release/maintenance impact: P2 means address in a focused pre-release validation pass; P3 means optional cleanup. Confidence distinguishes a reproduced check failure from a claimed gameplay failure.

### F1 — P2: active validation fixtures still encode retired content assumptions

**Confidence: high; reproduced. Category: test correctness, not demonstrated gameplay defect.**

1. `tests/myworld/test-npc-capacity-profile-configs.py:30` requires profiles to equal the *first* expanded-NPC client version, 10053. Line 38 also requires the client configuration to reference that old constant literally. The candidate intentionally uses `ClericBlessingContract.CLIENT_VERSION`, 10055, for schema4. The command fails immediately with `myworld.conf must use expanded client version 10053`; consequently `scripts/test-npc-capacity.sh` stops before its build and runtime checks. The independent production capacity fixture passed at 10055, including 16,384 registrations, 1,024 view budget, high carriers and reuse.
2. `tests/myworld/GemDragonClientFixture.java:108` expects IDs 3400–3407 to remain Unobtanium, and `server/test/com/openrsc/server/combat/GemDragonsFixture.java:61` expects those IDs to be absent. They are the intentionally integrated egg/antidote identities in `AntidoteCleansing.java:7`. Both client `test-gem-dragons.py` and Ant `test_gem_dragons` fail at these assertions. In the Ant target this prevents the guaranteed and normal-loot fixtures later in the target from running. Assertions below the obsolete server check, including equipment behavior, are also not reached in that run.
3. `tests/myworld/test-cleric-spellbook-foundation.py:104` still expects exactly two resource entries and the initial single-grade quantities. Current Tier2/Tier3 use current-and-previous grades, documented in `cleric-metal-sigil-production-review.md` and implemented in `ClericSigilCost`/`ClericSpellDefinition`. The foundation test fails at `mixed god+Grace`. Its hostile assertion at line105 has the same two-entry assumption. The current native metal-sigil fixture passed 206 assertions. The foundation test is still invoked by `tests/myworld/test-all.sh`.

**Consequence:** the general and feature gates are red for correct current behavior, and some useful checks are masked by early exits. This encourages selective testing and makes an unqualified “tests passed” handoff unreliable.

**Smallest direction:** update these fixtures to the current canonical version/content/cost contracts, retaining explicit checks that older incompatible clients are rejected, IDs remain unique, and full cost vectors are charged once. Separate obsolete historical assertions from current release gates. Do not restore the old version, delete antidotes, or revert the approved metal-cost model.

**Verification:** rerun the three failing entry points and both gem client/server paths; ensure their later checks actually execute. Use the current spell contract for exact paired version and the NPC contract for capacity capability, rather than conflating the two.

### F2 — P2: coating reproducibility check rejects the committed approved potion PNG

**Confidence: high for check failure; no visual corruption demonstrated.**

`scripts/generate-battleaxe-coatings.py:92` reads the approved practice-feedback bottle, then line98 re-encodes it with Pillow; `emit` at line37 requires exact compressed PNG bytes. `python3 tests/myworld/test-battleaxe-coatings.py` fails in the generator check on `dev/myworld/assets/sprites/items/inventory-ground/viscous-coating.png`, before its archive-preservation and client-renderer tests run.

With installed Pillow 12.2.0, the committed image is 905 bytes and the generated encoding is 898 bytes. Both decode to exactly the same 48×32 RGBA pixels. This establishes an encoding/reproducibility mismatch, not a changed silhouette. The potion and generator were both deliberately revised in `23a69d5b7`.

**Consequence:** the advertised full coating asset gate cannot currently certify this snapshot; blindly regenerating can introduce binary churn without changing the artwork.

**Smallest direction:** explicitly choose byte preservation for the already-approved source PNG, a pinned deterministic encoder, or pixel-level equivalence where compressed-byte identity is not the actual contract. Preserve the stricter unrelated archive-entry checks. Do not regenerate all assets as an incidental lint fix.

**Verification:** `generate-battleaxe-coatings.py --check`, then the full coating asset test, including its later Aseprite/archive/client checks. The separate gameplay fixture already passed 1,042 assertions for all 15 variants, recipe/application/removal identity, primary hits and poison.

### F3 — P2: current lint gate is not green; three SpotBugs findings need disposition

**Confidence: high for gate failure; these warnings do not establish three bugs.**

The repository's existing `lint.sh report --offline` completed all analyzer products, then returned1 at `scripts/lint.py:643` because three fingerprints are absent from `config/static-analysis/baseline/spotbugs.txt`:

| Finding | Location | Review assessment |
|---|---|---|
| `REC_CATCH_EXCEPTION` | `Client_Base/src/com/openrsc/interfaces/misc/DoSkillInterface.java:741`, `slayerPointCoin()` | Broad catch returns null on asset/decoding failure. Graceful fallback is plausible, but programming errors and repeated failed loads become invisible. Prefer a narrow, intentional fallback with bounded diagnostics. |
| `RpC_REPEATED_CONDITIONAL_TEST` | `Client_Base/src/com/openrsc/interfaces/misc/party/PartyInterface.java:285` | Redundant `inParty()` condition; no incorrect visible behavior established. The file is unchanged in the broad candidate diff: new-to-baseline does not mean introduced by this update. |
| `AT_NONATOMIC_64BIT_PRIMITIVE` | `server/src/com/openrsc/server/model/entity/player/Player.java:975`, `scheduleCombatCast()` | The timestamp and lifetime fields are not synchronized. Normal event execution defaults to direct serial execution (`GameEventHandler.shouldExecuteDirectly`); no current-default race reproduced. Enabling the optional threaded event mode requires a broader confinement review. Making only the long volatile would not prove the paired state atomic. |

**Consequence:** both full reporting and the existing whole-program SpotBugs gate fail until reviewed. Blanket baseline regeneration would conceal the decision.

**Smallest direction:** individually fix or narrowly document/suppress accepted findings, with a reviewed baseline adjustment only where justified. Keep style/import cleanup separate from behavior changes. Re-run the existing lint gate and retain its exit status.

### F4 — P2: gem-loot fixtures are not fully isolated from database logging

**Confidence: high; reproduced in fresh synthetic fixture environments. Category: verification isolation/reliability.**

To reach checks hidden by F1, I compiled and ran the unmodified `GuaranteedGemLootFixture` and `GemDragonStandardLootFixture` independently against freshly built jars, using `prepare-cleric-well-fixture.py` to create temporary terrain/configuration and dummy database settings. They printed respectively:

- `Guaranteed gem loot: PASS 7,379 assertions; all 897 tickets and 258 normal/personal necklace deliveries`.
- `Gem dragon standard loot: PASS 86,162 assertions; 10,240 common/rune combinations plus 2,688 rare gate tickets and 304 complete necklace/personal deliveries`.

**Both JVMs subsequently exited 1.** `Npc.java:1298` unconditionally submits asynchronous SQL drop logging. The harness constructs a real Server and only shuts its executors down in `CurrentCombatHarness.java:346`; queued logging reaches the dummy MySQL configuration. The connection was refused, and `MySQLDatabaseConnection.connect` exits the JVM. No real account database was opened. No game listener was started. These are assertion-completed **incomplete/failed process checks**, not clean passes.

**Smallest direction:** provide an explicit test-owned recording/no-op logging/database boundary, and settle/shut down asynchronous work before the final success marker. Do not make real database access a prerequisite for an advertised offline loot fixture. Do not change production loot logging merely to silence a test.

**Verification:** both fixtures exit 0 after all assertions and teardown, with connection attempts rejected/detected by the test harness. Keep exit status authoritative alongside completion markers.

### F5 — P1: inherited auction failure paths can duplicate grants and lose seller payment

**Confidence: high; reproduced with unmodified production task classes, fake database failures and blocked network/listener access. Inherited issue, not caused by the candidate's new untradable-item guard.**

Locations: `server/src/com/openrsc/server/content/market/task/BuyMarketItemTask.java:75` through `:114`; `PlayerCollectItemsTask.java:46` through `:57`; `Market.java:113` and `:177`; `Player.java:4866`.

**Purchase:** the task grants the item, removes the buyer's coins, reports success and enqueues a separate player save before inserting the seller's payout and updating the listing. If `addExpiredAuction` fails at line102, the outer catch returns without undoing player mutations or retiring the listing. If the later listing update fails, its inner catch also logs and continues. There is no shared transaction spanning the player mutation, seller claim and listing. `PlayerService.savePlayer` has its own transaction, but `Player.save()` here merely queues that independent operation; it does not make the auction purchase atomic.

**Collection:** the task adds items to the bank at line46 before marking claims collected at line53. A claim-update failure is logged without removing the bank grant. The same unclaimed record is eligible on the next collection.

**Controlled reproduction:** `MarketFailureReviewFixture.java` uses the maintained `CurrentCombatHarness`, real Player/Inventory/Bank and real `BuyMarketItemTask`/`PlayerCollectItemsTask`. A test-owned `MySqlGameDatabase` subclass overrides the invoked auction methods with in-memory listing/claim responses and injected `GameDatabaseException`s. No JDBC operation is invoked by these overrides. A Java8 security manager denies hostname resolution, socket connects and listeners. In a fresh generated runtime fixture, the diagnostic exited0 and verified:

- A one-bar/100-coin listing, purchased twice with the seller-credit operation forced to fail, delivered two bars and removed200coins while its durable one-bar listing was never updated. Neither seller credit succeeded.
- A one-bar unclaimed record, collected twice with claim updates forced to fail, added two bars to the bank while the claim remained available.

These observations reproduce the local inconsistency/duplicate-grant paths, not a claim that an incident occurred on the public server. A durable crash/restore reproduction against actual database engines was not performed. The market is enabled by both checked-in MyWorld profiles (`spawn_auction_npcs:true`) and runs its tasks on a separate scheduled executor, so normal tick confinement does not supply a missing cross-resource transaction. The problematic code exists at the broad baseline; only the purchase task's untradable-item refusal is newly changed in this candidate.

**Consequence:** a transient database failure can duplicate distributed items or claims and remove buyer currency without delivering seller payment. Repeated attempts can amplify the inconsistency. Asynchronous player saves add crash/order concerns; logging an error is insufficient compensation.

**Smallest sound direction:** introduce an explicit purchase/claim commit boundary with idempotent operation identity, validated exact actor/session and item admission, durable listing/claim updates and controlled player-state publication/rollback. Inspect listing creation/cancellation with the same standard before declaring the market safe. Merely wrapping the SQL calls in `atomically` leaves the in-memory grants outside rollback; merely adding try/catch does not repair it. Core should prioritize a focused fix or deliberately restrict the affected operation until corrected, without broad gameplay refactoring.

**Verification:** preserve a failure-injection regression for every boundary (grant/admission, payment, seller credit, listing update, claim update, queued-save failure and disconnect/relogin), including retries and crash/reload with disposable databases. Assert that exactly one side effect commits, retries cannot duplicate it, and both item/currency conservation and operation identity hold. No production code or test was modified during this review; the diagnostic source and log are supporting scratch evidence only.

## Credible release risks and remaining acceptance

These are not newly confirmed gameplay bugs.

1. **Final map is outside this commit.** The assignment explicitly says the staged export is older than the owner's latest save. Tower upward-only bindings are exact coordinate/object/direction matches in `MonsterSlayerTowerPassages`; well admission requires a reachable shoreline and authoritative object identity. Unit/runtime fixtures cannot certify the final placements. Refresh and validate the paired map through the manager's established process, then test entry/upward guards, free downward/exit travel, egg acquisition/respawn, cave placements and wells together. Do not infer that the current authored map is broken from an older report.
2. **Rollback must account for migrated progression.** `MonsterSlayerState.java:55` explicitly rejects unsupported roster versions; the state reader is deliberately not a general downgrade converter. The migration/package document requires backups and rollback material, but this review did not rehearse restoration. Demonstrate rollback with disposable database/map/configuration copies, including what happens to progression earned after migration. A source-binary rollback alone is not evidence of save compatibility.
3. **Discovery evidence deserves paired-package verification.** The committed legacy fallback generator advertises `encodingVersions:[1]` in `scripts/generate-world-builder-target-contract.py:188` and:385. The installed-runtime capability has a different signed-layered identity and supports[1,2,3,4]. These are different descriptors, not proof of an active loader incompatibility. The assignment flags machine-local differences. Verify discovery/build/install evidence for the actual final source/client/server/map combination without copying local descriptors into Git or assuming legacy advisory metadata is the runtime authority. The separate toolkit was not inspected or modified.
4. **Combined gameplay remains required.** Real simultaneous party casts, logout/reconnect during combat, full inventories, encounter combinations, persistence across restart, crowded tower play, hardware rendering and ordinary launcher/update behavior still need the exact release candidate. Offscreen renderer tests exercise logic and texture paths; they do not substitute for a hardware OpenGL play session.

## Lint and static-analysis inventory

Used the checked-in rules and pinned cached tools: Checkstyle 13.8.0, PMD/CPD 7.26.0, SpotBugs 4.10.3, ShellCheck 0.11.0 and Ruff 0.15.12. Builds used Java 8u482 with 512MiB/SerialGC; analyzers use the Java 21 installation required by the existing Checkstyle pipeline. No downloads, rule changes, baseline regeneration or autofixes were requested.

| Check | Result |
|---|---|
| Java8 client/server/plugins/world-builder-tools compilation with `-Xlint:all` | Passed;326 total reported compiler fingerprints,44 gated observations, **zero new gated warnings** |
| Full source scope | 2,086 Java,729 Python,72 shell files under existing configured roots |
| Checkstyle | 1,049 reports:760 star imports,196 unused imports,60 empty catches,32 multiple top-level classes,1 filename mismatch |
| PMD | 161 reports:74 resource,46 reference-comparison,41 empty-catch |
| ShellCheck | 27 reports |
| Ruff | 20 reports; unused imports/variables, one repeated import, one needless f-string prefix; no undefined-name/syntax finding in this configured scope |
| CPD |520 duplication groups at the existing 120-token threshold; includes generated shared contracts and intentional versioned protocol implementations |
| SpotBugs |Client 70, server 315, world-builder 2 observations; three new-to-baseline fingerprints; command exited 1 (F3) |

Using the existing changed-line parsers against the broad common ancestor yields 152 Checkstyle, 12 PMD, 1 ShellCheck and 4 Ruff findings touching changed lines. These numbers are overlap inventories, not confirmed defect counts. The full-report mode intentionally reports source findings without enforcing each source gate. The run's final nonzero exit comes from SpotBugs; its final metadata-writing step is therefore not reached.

Examples where automatic lint fixes would be harmful or misleading:

- `Slow.java:198`, `NonCorporealState.java:103`, `EntityList.java:164` and terrain-cache transaction checks compare object identity intentionally. They protect exact state ownership/lifetimes; substituting value equality is unjustified.
- Shared generated client/server contracts are deliberate duplication. Change the canonical generator, not one emitted copy.
- Versioned classic packet implementations preserve different wire behavior. CPD similarity is not evidence they can safely share all logic.
- ShellCheck flags the intentional POSIX `CDPATH= cd ...` idiom and quoted snippets for later shells. Review expansion context before editing.
- An unused import is not proof an entire class or feature is unused.

## Organization, dead code and performance assessment

**Worth a focused follow-up after release:**

- Add a compact active subsystem/navigation index: canonical spell and NPC contracts, cast admission→transaction→effect registry→damage settlement, lifecycle ownership, current tests and release gates. The stale-fixture findings demonstrate a concrete navigation/maintenance problem. Link superseded design reviews clearly instead of asking future agents to reconcile every historical proposal.
- Extract one cohesive client UI/state responsibility at a time from `mudclient.java`(29,625lines), using the existing `ClericSpellbookCatalog`, summaries and floating-hit renderer as seams. `PacketHandler.java`(5,254lines) can similarly delegate a single self-contained packet family. Keep packet ordering, context activation and renderer phase ownership explicit. File size motivates investigation; it is not by itself the benefit or acceptance criterion.
- Consider retiring obsolete build indirection only after source-origin tests: `server/build.xml:231` still describes an installed runtime as first on the classpath, although the maintained launch targets exclude that managed-runtime path. The gameplay overlay is built from the same current classes and retained by audits. Update the misleading explanation first; removal needs a separate class-ownership/launch-equivalence change. Its presence alone is not evidence that new combat code is ignored.
- Replace magic component numbers in combat adapters with existing `ClericBlessingContract` constants when next touching those paths. This reduces cross-file lookup mistakes without inventing another abstraction.

**Possible retirement candidates, not approved deletions:** `ClericPurifyEffect`, `ClericRestoreEffect` and `ClericHealingPulseEffect` have no ordinary maintained gameplay callers found in the reviewed Java source; historical Python/compiled fixtures still reference them. The retired stable spell IDs and zero-critical compatibility APIs remain intentionally reserved. Plugin loading uses reflection, so a textual search is not a complete reachability proof. Before deleting, inventory packaged plugin consumers, configuration/scripts, reflective class-family loading, stable IDs/serialization and supported old clients. Prefer moving obsolete tests out of active release gates first. Do not delete reserved IDs or cache keys as “dead code.”

**No speculative optimization recommended.** Expanded NPC registration has a separate bounded view budget and an upper-bound registry traversal rather than an unconditional 16,384-slot scan. Runtime fixtures exercise the limits, but no representative live workload or before/after profile was collected. New caching, pooling, threaded event processing or combat-formula rewrites would need measured benefit and lifecycle/ordering proof. No broad restructuring of `Player`, `RegionManager` or combat settlement is justified immediately before release by this pass.

## Commands actually run and verification ledger

Evidence logs retain complete commands/results; all paths below are relative to the assigned worker unless absolute. Environment for Java checks: `JAVA_HOME=/home/justin/jdk8u482-b08`, its `bin` prepended to PATH, `ANT_OPTS='-Xmx512m -XX:+UseSerialGC'`; fixture batches also set `JAVA_TOOL_OPTIONS='-Xmx512m -XX:+UseSerialGC'` and `PYTHONDONTWRITEBYTECODE=1`.

- Preflight: `git status --short --branch`, `./scripts/ai-workspace.sh status`, `git cat-file -t TARGET`, `git worktree list`, tag/log inspection, `git merge-base DEPLOYED TARGET`, `git rev-list --left-right --count DEPLOYED...TARGET`, broad/deployed final-tree diffs and focused immutable-source reads. Read manager instructions; no workspace allocation or branch switching.
- Lint: `./scripts/lint.sh report --offline --base fec94c8731b5521410963575ef0f2fa5c05ef0b3 --reports output/next-update-code-review/lint` — exited 1 as described in F3; all products completed.
- First serial Python batch: `python3 tests/myworld/NAME` for each entry in `test-results.json`: 16 entry points, 13exit 0 and 3 exit 1 (capacity-profile, gem-client, coating-assets). Current Cleric buff fixture 88 checks, hostile 121 checks, well infusion 439 checks, metal 206 checks, shared runtime 173 checks, luck runtime 90 checks; production capacity and client schema4 passed.
- First Ant batch: `./server/test_combat test_network_encoder test_network_decoder test_slow test_battleaxe_coatings test_antidote_cleansing test_gem_dragons test_abyssal_demon_combat test_dark_beast_combat test_bloodveld_combat test_terror_dog_combat test_naga_combat test_slayer_rewards test_slayer_leather_effects test_slayer_tower_associates test_monster_slayer_player_state` — exited 1 at the stale gem-ID assertion. Targets before it passed; later targets were not claimed as run by this command.
- Follow-up Ant batch: `./server/test_combat test_abyssal_demon_combat test_dark_beast_combat test_bloodveld_combat test_terror_dog_combat test_naga_combat test_slayer_rewards test_slayer_leather_effects test_slayer_tower_associates test_monster_slayer_player_state` — **BUILD SUCCESSFUL**, including the wrapper's strict combat gate 145/145. Expected injected failed-save exceptions in characterization output are not real database incident evidence.
- Extended serial Python batch: `python3 tests/myworld/NAME` for each Python entry in `extended-results.json`: 15 entry points, 14exit 0; the old Cleric foundation exits 1(F1). Registry safety, login recovery, terrain residency, layered protocol, scene lifecycle, restricted-door/passage tests, cleanup, cast transaction, sigil production, effect state, baseline policy, server build audit and pure shared-state checks passed.
- Independent unmodified Java loot fixtures: compiled with `javac -proc:none -cp server/core.jar:server/plugins.jar:server/lib/* -sourcepath server/test -d TEMP/classes SOURCE`, then Java 512 MiB/SerialGC with those jars in a new runtime folder from `prepare-cleric-well-fixture.py`. Both completed assertion markers but exited 1 from asynchronous dummy-DB logging(F4). No retry against any real database was made.
- Read-only PNG diagnostic: decoded approved source/installed PNG toRGBA, compared dimensions/pixels, and encoded a fresh PNG in memory. No PNG was overwritten.

**Not run:** `scripts/test.sh`/the entire `test-all.sh`, which includes server-start and account-database checks outside this assignment; new-server/client launch smoke; public/practice server operations; owner project/map export; final package build/install/update; real-account persistence/migration; full hardware render QA; dependency vulnerability/network audit; sustained load/latency profiling. Existing historical evidence was used for navigation, never relabeled as tests run during this review.

## Coverage and decision order

| Subsystem | Review performed | Remaining limit |
|---|---|---|
| Combat/Slow/Stagger/Crit/luck | Read shared state and primary/projectile settlement, cadence, provenance, poison, non-corporeal and named proc paths; deterministic runtime and strict gate | No exhaustive proof of every legacy script or all simultaneous encounters |
| Cleric/production | Read catalog/cost vectors, targeting, inventory transaction, origins, periodic registry, buffs/hostiles, phase rearm, undead classification; current server/client/production fixtures | Final party gameplay, hardware presentation, and older validation drift remain |
| Slayer/economy | Read tower/rank/migration, reward/drop policies, auction purchase/create/claim task flows and executor/DB boundaries; reproduced failed purchase/claim duplication (F5) with fake database; encounter/reward/leather/tower/state targets | New-map acquisition, complete gem gate, real-engine market crash/recovery, and exhaustive trade/bank/duel transaction audit remain pending |
| Networking/lifecycle | Read expanded carriers/login gating, identity-owned registry, terrain recovery/context acceptance; production capacity, encoder/decoder and focused lifecycle tests | No live network session, fuzz campaign or full security audit |
| Saving/permissions | Read login version/authentication path, failed-save retention, PlayerService transaction and save queue, identity-owned registration/removal, admin command/test-kit gates and server-owned spell/well/target admission | No real DB migration/restore, full authentication/permission fuzz campaign, or complete trade/bank/duel audit |
| Content/presentation | Whole configured lint; asset/source checks and current HUD/schema4/hit-number fixtures | Coating asset check stops early; final authored map outside snapshot |
| General maintainability | Changed-file inventory, selected whole call paths, duplication and dead-code candidates, build authority | Not a line-by-line audit of all 1,493 paths or all inherited code |

**Before release:** resolve the inherited F5 auction failure boundary, then repair/disposition F1–F4; rerun the affected gates to completion; freeze exact paired artifacts and latest-map evidence; run combined private gameplay; demonstrate upgrade/restore/rollback. No new feature expansion is required by this review.

**After release:** small documented lint cleanup, explicit historical-test retirement, navigation improvements and one measured/cohesive extraction at a time. Preserve intentional identity/protocol/compatibility safeguards.

This report is review evidence, not deployment approval. Implementation and final release acceptance remain Core-manager decisions.


## Delivery status and publication blocker

The detailed report and ignored diagnostic evidence are complete locally. The worker remains on `docs/next-update-code-review` at frozen source `6c487d699a77b98824ed568189609d690feab758`; no report commit or READY handoff was published.

The attempted action was `./scripts/ai-workspace.sh checkpoint -m 'Document frozen next-update code review findings'` in `/home/justin/Core-Framework-ai-2`. Automatic approval review **denied the action before execution**, stating that creating the report was authorized but checkpointing and pushing it was an external repository commitment not explicitly approved by the user. The action was not retried or bypassed. The dependent remote handoff was not attempted. No publication retry is planned. This workflow remains blocked; local report delivery and the requested Library attachment do not depend on Git publication.

No source change, merge, release or deployment is waiting on this blocker. The only untracked project addition is this permitted findings report. The parent thread can deliver the local report and evidence immediately, without requesting publication permission merely to deliver the report.

## Supplemental scope pass: inventory, economy, persistence and security boundaries

After the initial release-feature pass, I explicitly inventoried changed inventory/economy/login/save/command paths and followed relevant unchanged callers. This extended the substantive review beyond the passing feature gates and produced F5.

- **Inventory/production:** inspected the changed `Inventory.replaceExactWithCopies` and existing `CarriedItems.removeWithStateChange` boundaries, exact instance IDs, noted/wielded state, capacity/client-ID admission, item-ID allocation, output overflow, all-grade well conversion, current production-session identity, furnace proximity and cancellation/revalidation. Traced `SigilMoulding`, `SigilProduction`, `SigilWells`, `Smelting`, coatings, and the current native fixtures. No new production duplication was established in those reviewed paths. Inventory returns a mutable list and uses explicit caller locks; replacing that design wholesale is not recommended as a release cleanup.
- **Economy:** inspected the complete changed `BuyMarketItemTask`, unchanged `NewMarketItemTask` and `PlayerCollectItemsTask`, market executor/queue, incoming auction restrictions/rate limit, admission helper, GameDatabase auction wrappers and separate player save queue. The new untradable-listing refusal is appropriate. Existing failure handling lacks a coherent transaction across durable auction state and player state; the two reproduced paths are F5. Listing-creation refund/note/identity and concurrent stale-session handling need a focused follow-up, not an unproven clean bill of health. I did not execute market requests against any real database or live player.
- **Persistence/lifecycle:** read `PlayerSaveRequest` success/failure cleanup, `PlayerService.savePlayer`'s `database.atomically` boundary, `Player.save` queueing and saving flags, `PlayerList`/`World.registerPlayer` exact membership, and Monster Slayer version/migration rejection. The strict combat gate includes failed-save, relog and DOT lifecycle characterization; the targeted registry and login-recovery checks passed. This establishes tested local lifecycle behavior, not actual database durability under disk-full, process crash, or engine-level deadlock.
- **Login/permissions:** traced mandatory NPC wire capability admission before admin/version exemptions; existing password/ban/IP/duplicate-login flow; the changed IP-player-list use; `CommandHandler` dispatch, Admins trigger and the second `SlayerGimmickTestKit.grant` admin check; common Abyssal action gating; Cleric version/code/level/PvP/target and well-object admission. No new privilege bypass was confirmed in this bounded pass. This was not a penetration test, credential audit or exhaustive check of inherited commands/SQL/query composition. No account data or owner projects were inspected.
- **Protocol and release packaging:** inspected capacity contracts, active client version, packet decoder/context recovery and build-source authority. The mutable local manager state was not substituted for the frozen snapshot. No external toolkit, owner save, release package or live runtime was modified or certified.
- **Dead code/fragility/performance/structure:** reachability searches included Java, Python tests, scripts, configuration/data and reflective plugin-loading context for the named candidates. No removal was made or declared safe solely on text search. Performance statements remain hypotheses unless backed by the existing bounded runtime tests; no new load benchmark was presented as live performance evidence. Structural recommendations are scoped to documented navigation, misleading retained build commentary and cohesive post-release extraction, not file-size-driven splitting.

**Additional command/evidence:** compiled the scratch diagnostic with Java8 `javac -proc:none`, the existing jars and `server/test` sourcepath, then ran it with `-Xmx512m -XX:+UseSerialGC` in a new runtime directory from `prepare-cleric-well-fixture.py`. The first attempt stopped before task execution because the security guard's AssertionError for hostname resolution prevented Log4j initialization; the guard was corrected to deny resolution with SecurityException (which Log4j handles), while still denying socket connects/listeners. The second run reproduced both F5 paths and exited0. `market-failure-review.log` and `MarketFailureReviewFixture.java` preserve this evidence. This is an authorized scratch test, not a new production test or applied fix.
