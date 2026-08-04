# Server R2 Productization Plan

Status: ACTIVE IMPLEMENTATION. R2-0 is complete; R2-1 has not started.

Plan date: 2026-07-30

Governing direction:

- [Phase 4 of the Remaster Suite Roadmap](remaster-suite-roadmap.md#phase-4-server-module-productization)
- [Server build source of truth](../info/server-build-source-of-truth.md)
- [Project structure refactor plan](project-structure-refactor-plan.md)
- [Code-health audit](code-health-audit-2026-07-12.md)
- [Layered-world capacity and loader plan](world-layer-capacity-exploration-plan.md)
- [Live deployment safeguards](../../workspaces/live-deployment.md)

## Executive Decision

Server R2 is an incremental productization of the current server, not a rewrite
and not another name for the custom scene protocol. The current Ant-built
server, compatibility protocols, plugin triggers, persistence adapters, and
layered-world runtime remain the migration base.

The desired product is a content-neutral server foundation that can compose:

1. one explicit target profile;
2. zero or more declared content packages;
3. one supported persistence backend;
4. one declared protocol/capability set; and
5. operator-owned configuration and data.

The Server foundation must not require Spoiled Milk quests, skills, balance,
items, definitions, maps, dialogue, or progression. Spoiled Milk remains a
first-class tested distribution, but it becomes a consumer of Server R2 rather
than an implicit mode inside the foundation.

The initial extraction stays in this monorepo. No repository split, Gradle
promotion, dependency upgrade, protocol replacement, or live deployment is
part of the first Server R2 branches.

## Terminology

- **Server R2** is the independently packageable server-foundation product. It
  is not protocol v2 and does not imply a network reset.
- **Foundation** means bootstrap, configuration contracts, world/tick/entity
  runtime, networking, persistence services, extension contracts, diagnostics,
  and safe shutdown.
- **Target profile** means the exact vanilla-compatible definitions, IDs,
  protocol constraints, maps, fingerprints, and compatibility policy selected
  for a distribution.
- **Content package** means optional gameplay beyond the selected target
  baseline. Spoiled Milk is the first complete optional content package.
- **Distribution** means a tested composition of a Server version, target
  profile, content packages, configuration template, and persistence policy.
- **Compatibility facade** means a maintained adapter that preserves an
  existing call path while authority moves behind a new contract.

Capability identifiers and artifact names in this plan are provisional until
two independent consumers exercise them. They must not be published as stable
v1 contracts merely because an internal extraction compiles.

## Scope

This plan owns:

- the content-neutral server ownership boundary;
- bootstrap and lifecycle composition;
- target-profile and content-package registration;
- plugin and extension contracts;
- definition and layered-map inputs;
- protocol profiles and capability negotiation;
- persistence versions, migration receipts, validation, and rollback;
- server diagnostics and operator-facing failure behavior;
- Ant-authoritative server artifacts and standalone package contents;
- compatibility with the current Spoiled Milk distribution; and
- private acceptance gates before any release or live activation.

This plan does not own:

- choosing or redesigning Spoiled Milk gameplay;
- resuming the paused vanilla-content design without an owner decision;
- renderer implementation;
- World Builder editing features;
- the broader OGG/audio plan;
- a new database schema for unrelated gameplay;
- Java/dependency upgrades;
- a Gradle migration;
- a general plugin marketplace or untrusted-code sandbox;
- top-level folder/package moves before ownership is extracted;
- public-server shutdown, deployment, or live data mutation; or
- deletion of legacy protocols, packed-map adapters, cache keys, or proof code
  without a separate usage and compatibility audit.

## Current Evidence Snapshot

R2-0 established its reviewed ownership rules and dependency baseline from
published `main` commit `6e1720900bbdccd46ba06b9726ac7e91a89b56f3`.
The checked inventories regenerate from the current shipped-input tree; they
were most recently refreshed while integrating the accepted boundary-loading
work at merge commit `001d34c5d`. The human and JSON inventories are
[`server-r2-ownership-inventory.md`](../info/server-r2-ownership-inventory.md)
and `server-r2-ownership-inventory.json` beside it.

### Build and artifacts

- The production authority is bundled Ant 1.10.5 plus `server/build.xml`.
  `scripts/build-server.sh`, private launchers, hosted launchers, and the build
  audit all follow this path.
- Gradle is explicitly secondary and non-authoritative. Its combined source set
  and dependency declarations do not prove Ant artifact or runtime parity.
- Ant compiles 920 Java files from `server/src` into a fat executable
  `server/core.jar`.
- Ant compiles 492 Java files from `server/plugins` into the separately loaded
  `server/plugins.jar`.
- The checked-in library directory contains 21 JARs. Ant currently embeds all
  of them in `core.jar`, while run targets also put `server/lib/*` on the
  runtime classpath. That duplication is known packaging debt, not permission
  to delete or upgrade libraries.
- `scripts/audit-server-build.py` inventories the shipped Ant source roots,
  classpaths, libraries, artifacts, plugin expectations, and Gradle drift.
- The player release is client-only. The World Builder release carries a
  purpose-specific copy of `core.jar`, `plugins.jar`, `server/lib`,
  `server/conf`, and `server/database`; it is not a content-neutral Server
  release.

### Completed server foundations

The following work is complete enough to be treated as a foundation, subject to
preserving its tests and compatibility facades:

- B03 documented and guarded the Ant build authority.
- B04 established changed-code compiler/static-analysis baselines without
  forcing a repository-wide warning cleanup.
- B05 distinguished ordinary social lookup misses from database failures,
  added redacted diagnostics, and kept best-effort cleanup non-fatal.
- B10 extracted and tested pure equipment/spell boundaries without moving
  combat authority to the client.
- B11 labeled maintained compatibility systems and added prune proofs.
- Server synchronization work fixed known-player bookkeeping, batched outgoing
  writes to one flush per player cycle, added payload/timing telemetry, built
  parity-tested visibility snapshots and caches, and retained legacy packet
  paths.
- The custom scene-baseline and movement-snapshot lanes provide useful
  default-off or compatibility-gated protocol evidence. They are not yet a
  centralized Server R2 protocol contract.
- Idle-NPC tick throttling is enabled for the Spoiled Milk profiles after
  private validation and retains an explicit rollback flag. It is a profile
  policy until its applicability to other distributions is proven.
- Private and hosted launch scripts visibly distinguish their worktree,
  configuration, database, bind address, and port.
- Hosted launch requires the detached published-main live worktree, exact
  configuration, external database link, free public port, and a launch
  attestation.

### Layered-world foundation

Phase 5 is complete and owner-accepted. Server R2 must preserve, not recreate:

- `WorldLocation(worldSpace,x,y,level)` and checked legacy `Point` adapters;
- typed player persistence with legacy receipts and rollback behavior;
- level/world-space-aware spatial identity, visibility, collision, interaction,
  and transition boundaries;
- strict native package parsing, payload hashes, manifest fingerprints, terrain
  ownership, placement validation, and fail-closed transitions;
- native terrain wire-product caching, client residency, readiness receipts,
  predictive staging, and atomic activation;
- native terrain/collision/placement behavior without packed `Region` authority
  inside native scopes; and
- maintained packed-map compatibility outside those scopes.

The current `NativeLayeredWorldRuntimeProfile` contains distinct fixture,
Preservation, Spoiled Milk replacement, and Spoiled Milk Builder policies. The
active reviewed Spoiled Milk replacement is
`rsc-remastered.spoiled-milk-layered-world@0.5.0`, manifest
`f914d93e7abcf40dc281c06df5010269c7a9ce4fe4a16aaa6ae11f0d90a14306`,
with 1,782 native sectors and 33,512 effective placements.

The generic package parser/catalog is foundation code. Exact Preservation and
Spoiled Milk package IDs, versions, hashes, counts, definition ceilings, and
Builder descendant rules are target-profile or tool policy. Productization must
move those policies behind declared profile validators without weakening the
current fail-closed checks.

### Runtime and content coupling

The current source tree does not yet provide a content-neutral build graph:

- 208 files under `server/src` reference the current content/plugin namespaces.
  This is a search heuristic requiring classification, not proof that every
  reference is wrong.
- 63 files under `server/src` contain direct MyWorld/Spoiled Milk signals. The
  wider checked shipped-input inventory records 269 signal-bearing inputs across
  core, plugins, data, configuration, database, release, and layered-tool
  surfaces. Signals are evidence and never classify a file by themselves.
- `ActionSender` imports `MyWorldItemId`, owns the
  `myworld_starter_bank_v1` cache key, constructs the Spoiled Milk welcome and
  starter bank, and selects payload generators. This is a concrete example of
  content behavior at the protocol layer.
- `World` conditionally loads MyWorld definition foundations from
  `WANT_MYWORLD`.
- `ServerConfiguration` is a 1,152-line flat mutable configuration object that
  mixes transport, database, diagnostics, layered-world gates, legacy rules,
  World Builder settings, and Spoiled Milk gameplay switches.
- `Server` is a 2,819-line composition root, lifecycle owner, network bootstrap,
  database migrator, plugin initializer, benchmark store, and diagnostics
  registry.
- Other high-risk ownership concentrations remain in `Player` (7,037 lines),
  `GameStateUpdater` (4,910), `ActionSender` (2,872), `World` (1,354), and
  `RegionManager` (5,066).
- `LayeredCoordinateParityObserver` is a 12,403-line retained diagnostic/proof
  system. It must be classified before package inclusion; its size alone does
  not make it dead.

### Plugins and extension behavior

- `PluginJarLoader` reflectively loads every top-level class from exactly
  `./plugins.jar`.
- `PluginHandler` discovers trigger interfaces, injects the whole `Server`,
  recognizes quests, minigames, shops, `AbstractRegistrar`, and default
  handlers, and owns plugin-thread cleanup.
- The one plugin artifact currently mixes 389 authentic, 85 custom, 15 shared,
  and 3 retro Java files.
- Existing trigger interfaces cover many NPC, item, scenery, wall, spell,
  combat, login/logout, command, and timed-event actions.
- Registration is not a complete package contract: identity, version,
  dependency/capability checks, definition contributions, ownership receipts,
  deterministic conflict handling, scheduled-work cancellation, and package
  health are not centrally described.
- Existing reflection and the `plugins.jar` path are active compatibility
  behavior. They remain until a declared loader proves parity and rollback.

### Networking

- `ClientLimitations` combines numeric ceilings, map hash, and inferred feature
  booleans in one mutable version table; some entries remain explicitly
  guessed/TODO.
- Login parsing supports multiple authentic layouts and a custom client version,
  while `ActionSender.getGenerator` independently selects among the legacy and
  custom payload generators.
- Checked-in Spoiled Milk configuration requires custom client version `10050`.
- Layered terrain packets carry their own protocol/stage/readiness versions.
- TCP, optional WebSocket, legacy payload families, and the custom client are
  active compatibility surfaces.

There is no single authority that binds login version, payload generator,
capabilities, definition fingerprint, map/profile fingerprint, coordinate
model, and migration requirements. Server R2 must centralize that metadata
without dropping the old generators.

### Persistence and migrations

- `GameDatabase` has SQLite and MySQL implementations and a transaction helper.
- SQL patches are backend-specific and tracked by `_PREFIX_db_patches`.
- Startup currently applies patches directly from
  `database/<backend>/patches` before loading definitions/world state.
- Layered player location uses additive versioned cache state, exact legacy
  receipts, corrupt-state rejection, and copied-database private acceptance.
- Live SQLite state is external to Git and protected by symlink, backup, file
  descriptor, and deployment checks.

Server R2 does not yet have a distribution migration manifest, dry-run plan,
pre/post fingerprints, installation receipt, or one-command validated rollback.
The current patch runner also treats failure to read executed-patch history as
an empty history and logs but does not propagate a failure to mark a patch
executed. Those are migration-safety gaps for a focused persistence phase, not
an invitation to rewrite all database access.

MySQL compile and schema support must remain, but isolated MySQL runtime
acceptance is conditional on supported credentials and an isolated environment.
Lack of those credentials must be recorded rather than guessed around.

### Deployment safeguards

The existing hosted workflow is a required compatibility boundary:

- workers never launch or modify the public server;
- release preparation does not authorize shutdown;
- live activation requires fresh explicit shutdown permission, an online
  backup, the full in-game `::update` warning window, and confirmation that the
  listener exited;
- deployment requires clean published manager `main`, a detached live checkout,
  a free public port, and the external live database link;
- the guarded stop script is a post-countdown fallback, not permission; and
- an unsafe/deleted SQLite descriptor must be recovered before stopping.

Server R2 packaging may add verification around this workflow. It must not
weaken, bypass, or silently replace it.

## Ownership Boundary

### Content-neutral Server foundation

The foundation owns:

- process/bootstrap lifecycle and dependency composition;
- typed configuration parsing, validation, and redacted reporting;
- clocks, ticks, scheduling primitives, world/entity containers, pathing,
  collision, visibility, and authoritative mutation;
- transport bootstrap, connection limits, packet dispatch, protocol-profile
  selection, capability negotiation, and ordered output;
- persistence interfaces, transactions, migration orchestration, validation,
  recovery hooks, and shutdown flushing;
- extension discovery, registration transactions, ownership receipts,
  activation/deactivation, and health reporting;
- definition/map service interfaces and validation hooks;
- generic layered-world package parsing, catalog ownership, transitions,
  streaming, collision, and typed coordinate/persistence contracts;
- bounded diagnostics, metrics, structured failure reporting, and redaction;
  and
- clean shutdown, private launch safety, artifact verification, and package
  provenance.

### Target-profile compatibility infrastructure

A target profile owns:

- exact baseline definition sets and numeric ID ranges;
- legacy/client version mappings, payload families, feature ceilings, and known
  limitations;
- definition, map, asset, and package fingerprints;
- selected legacy packed-map inputs or reviewed layered map package;
- baseline content plugins required to reproduce that target;
- default rule/config values that belong to the selected baseline; and
- migrations required to interpret that baseline's existing rows.

Target profiles are not Spoiled Milk Content merely because they are optional.
They are explicit compatibility inputs to the foundation.

### Spoiled Milk Content

The Spoiled Milk package owns:

- custom skills such as Summoning, Devotion, and other non-baseline systems;
- custom quests, guilds, dialogue, minigames, commands, shops, drops, balance,
  rewards, and progression;
- custom item/NPC/scenery definitions and generated IDs;
- MyWorld starter state, welcome behavior, cache keys, and recovery logic;
- Spoiled Milk map additions, relocations, placements, transitions, and exact
  replacement-profile policy;
- content-owned persistence versions and migrations;
- Discord/community integrations that are not required by the foundation; and
- declarations of every foundation, target-profile, map, protocol, definition,
  and persistence capability it requires.

### Operator-owned state

No replaceable Server package owns:

- live databases, backups, credentials, certificates, secrets, or private
  environment files;
- logs, packet captures, diagnostic sessions, crash bundles, or launch markers;
- local target/content selections;
- Builder projects or authored working packages; or
- public-server shutdown authorization.

## Target Composition Model

The end-state composition is conceptually:

```text
server-r2 foundation
  + one target profile
  + zero or more content packages
  + one world/map selection
  + one persistence backend
  + operator configuration
  = validated server distribution
```

Startup must be staged:

1. parse and validate operator configuration without starting threads;
2. inventory foundation, target, content, world, protocol, and persistence
   manifests;
3. resolve capabilities, versions, fingerprints, and conflicts;
4. open persistence and produce a migration plan;
5. migrate only after backup/rollback prerequisites are satisfied;
6. load definitions and map inputs into isolated registries;
7. register extensions transactionally and reject partial registration;
8. validate the complete composition;
9. start schedulers/network listeners; and
10. emit a redacted startup receipt.

Failure before step 9 must not expose a listener. Failure after mutation must
leave a clear recovery receipt and must not pretend startup succeeded.

Shutdown reverses owned resources in a documented order and is idempotent:
stop acceptance, close listeners, drain or reject logins, save players, stop
content-owned scheduled work, unload extensions, stop world/event services,
flush persistence, close databases, close executors, and publish the final
health receipt. Best-effort diagnostic cleanup remains non-fatal.

## Architectural Gap Register

| ID | Gap | Impact | Confidence | Destination |
| --- | --- | --- | --- | --- |
| R2-G01 | No content-neutral composition root or startup profile exists | Critical | High | R2-1 |
| R2-G02 | Flat configuration mixes foundation, compatibility, tools, and content | High | High | R2-1 |
| R2-G03 | One reflective `plugins.jar` mixes authentic, shared, retro, and Spoiled Milk content | Critical | High | R2-2 |
| R2-G04 | Trigger interfaces exist, but registration identity, dependency checks, ownership, rollback, and health are incomplete | Critical | High | R2-2 |
| R2-G05 | Definitions, generated IDs, population sources, and content loaders are selected inside core runtime paths | Critical | High | R2-3 |
| R2-G06 | Generic layered loader and branded exact-profile policies share one enum | High | High | R2-3 |
| R2-G07 | Client version, generator selection, limitations, layered packet versions, map hash, and capabilities have no single authority | Critical | High | R2-4 |
| R2-G08 | SQL patch execution lacks distribution manifests, dry runs, durable receipts, and tested rollback | Critical | High | R2-5 |
| R2-G09 | Metrics/proof state is embedded in `Server`, `GameStateUpdater`, and a very large layered observer | Medium | High | R2-6 |
| R2-G10 | No standalone content-neutral Server archive, manifest, verifier, or updater exists | Critical | High | R2-7 |
| R2-G11 | Preservation is useful compatibility evidence, but the definitive vanilla product target is paused/unselected | Critical exit blocker | High | Owner decision before R2-8 |
| R2-G12 | Fat-jar/runtime dependency duplication and Java/dependency age complicate packaging | Medium | High | R2-7; upgrades deferred |

## Required Extension Contracts

These are contract families, not final Java class names.

### Package descriptor

Every target/content package declares:

- stable package ID, semantic package version, and source/build provenance;
- provided and required capability versions;
- compatible Server range;
- definition/map/protocol/persistence fingerprints;
- entry points;
- migration identifiers;
- configuration schema/default contributions;
- conflicts and exclusive capabilities; and
- support/deprecation status.

Unknown, duplicate, cyclic, or incompatible packages fail before registration.

### Registration transaction

One bounded registration transaction must support:

- commands;
- skills/stat metadata;
- quests and minigames;
- NPC, item, scenery, wall, spell, combat, login/logout, and timed triggers;
- item effects and drop-table contributions;
- definition and population contributions;
- map transitions;
- shops or comparable registries;
- scheduled events; and
- package diagnostics.

Each registration records its owner and stable identity. Duplicate identities
must either have an explicit, deterministic override contract or fail. If any
part fails, the whole package registration rolls back.

### Runtime context

New extensions receive narrow services instead of unrestricted construction
authority:

- world/entity queries and approved mutation commands;
- scheduler and cancellation ownership;
- player messaging and protocol-neutral presentation requests;
- typed cache/persistence namespaces;
- definition lookup;
- target capabilities;
- logging/metrics with redaction rules; and
- package health.

Existing Guice injection of `Server` and existing trigger signatures remain
available through a legacy adapter until authentic and Spoiled Milk parity is
proven. New contracts must not force a flag-day rewrite of 492 plugin sources.

### Lifecycle

Extensions have explicit `discover`, `validate`, `register`, `activate`,
`deactivate`, and `unregister` states. Hot reload is supported only where state,
scheduled work, threads, and registrations can be revoked truthfully.
Otherwise the operator receives an actionable restart-required result.

## Compatibility Requirements

### Builds and runtime

- Bundled Ant 1.10.5 and Java 8 source/target remain authoritative.
- Java 17 may remain recommended for operational performance, but Server R2
  cannot silently raise the minimum runtime in a packaging branch.
- Gradle may consume/import Ant tasks but may not publish a Server R2 artifact
  until byte/content/runtime parity is demonstrated.
- Existing `core.jar` and `plugins.jar` launch behavior remains available during
  migration.

### Clients and protocols

- Retain all currently supported authentic payload generator families and the
  custom generator.
- Retain TCP and existing optional WebSocket behavior.
- Unsupported client/profile/capability combinations fail with a bounded,
  non-sensitive explanation.
- Legacy clients continue to use inferred limitations where no handshake can
  exist.
- The maintained custom client gains explicit negotiation only beside the
  current version check.
- Unknown capability bits, fingerprints, packet versions, or coordinate models
  fail closed before world entry.
- Server combat, movement, collision, persistence, and damage authority remain
  server-side.

### Maps and coordinates

- Keep legacy packed maps and checked coordinate adapters for supported target
  profiles.
- Keep native layered package hashes, scope isolation, explicit cross-package
  transitions, readiness receipts, and atomic scene activation.
- A target profile selects exact map/package validators; the foundation never
  guesses a distribution from package shape.
- The Builder descendant policy remains tool/profile-specific and must not
  become a permissive hosted-server loader.

### Persistence

- Existing vanilla-compatible rows must load without Spoiled Milk-only fields,
  cache keys, or login mutation.
- Existing Spoiled Milk rows must retain all recognized content state.
- Unknown newer migration levels refuse downgrade unless an explicit converter
  or read-only recovery path exists.
- Every mutating migration has backup prerequisites, validation, a durable
  receipt, and a tested restoration procedure.
- Content removal may require a migration; deleting a JAR is not a state policy.

### Plugins

- Existing trigger ordering, blocker/default behavior, quest/minigame IDs,
  scheduled actions, and plugin discovery remain stable until parity fixtures
  say otherwise.
- The exact `./plugins.jar` loader remains a compatibility facade during the
  split.
- Reflection, configuration, and package descriptors must be inventoried before
  any class is declared dead or moved out of a release.

## Ordered Implementation Phases

Each phase is one or more focused branches. A phase advances only after its own
gate; a later phase must not be used to conceal an earlier boundary failure.

### R2-0: Reproducible ownership and dependency baseline

Status: **COMPLETE — 2026-08-04.** The implementation is audit/test/document
only and changes no runtime source, build definition, configuration, data, or
artifact composition.

Goal: turn this planning inventory into a machine-checkable current baseline
without changing runtime behavior.

Work:

- extend the Ant build audit or add a focused Server R2 audit that inventories
  source roots, artifact entries, plugin families, definitions, population
  sources, configs, SQL patches, layered profiles, and release inputs;
- classify files as foundation, target-profile, Spoiled Milk content,
  maintained compatibility, tool-only, diagnostic/proof, or unresolved;
- record current reverse dependencies and direct brand signals;
- enforce “no new foundation-to-Spoiled-Milk dependency” for changed code while
  retaining an explicit baseline for existing debt;
- emit deterministic human and JSON reports; and
- document ambiguous owners rather than guessing.

Dependencies: none.

Verification:

- deterministic report on two clean runs;
- `python3 scripts/audit-server-build.py --check`;
- `python3 tests/myworld/test-server-build-source-of-truth.py`;
- `python3 tests/myworld/test-myworld-plugin-layout.py`;
- `python3 tests/myworld/test-standalone-layout.py`;
- changed-code static analysis; and
- `./scripts/build-server.sh`.

Gate:

- every shipped server input has an owner class or explicit unresolved record;
- all current reverse-dependency exceptions are baseline-visible;
- no runtime or artifact contents change; and
- the manager approves the initial extraction boundary.

Stop if:

- a file cannot be classified without deciding gameplay ownership;
- the audit would encode generated/transient build output as source authority;
  or
- enforcing the guard would require a broad package move.

#### R2-0 completion record

R2-0 establishes `scripts/audit-server-r2.py` and its reviewed rules/debt data
under `config/server-r2/`. Two checked reports classified the initial 1,808
shipped inputs into the seven required owner classes and retain the reason and
matching rule for every entry. The inventory covers:

- 916 Ant core sources, 492 Ant plugin sources, both artifact targets, and all
  21 fat-JAR/runtime library inputs;
- plugin families of 389 authentic, 85 custom, 15 shared, and 3 retro sources;
- 42 definition inputs, 41 population inputs, 21 configuration inputs, 27
  database patch/upgrade inputs, plus database schemas, addons, queries, and
  the Spoiled Milk seed;
- legacy and native map/archive inputs, the five declared layered runtime
  profiles, layered schemas/tools/fixtures, and World Builder release inputs;
  and
- explicit Java imports, their reverse dependencies, file hashes, direct
  MyWorld/Spoiled Milk signals, classification reasons, and the complete Ant
  build-audit boundary.

The initial owner totals were 875 foundation, 412 target-profile, 178 Spoiled
Milk content, 91 maintained compatibility, 133 tool-only, 10
diagnostic/proof, and 109 explicitly unresolved inputs. The unresolved set is
deliberate: 26 definition/population inputs, 51 database inputs, and 32 mixed
`server.content` sources do not carry enough authority evidence for a safe
final owner decision. They remain shipped and visible rather than being
silently omitted or guessed.

The dependency guard baselines 83 exact foundation-to-known-Spoiled-Milk Java
import edges. An exact existing edge may remain or be removed, but a new or
redirected edge fails `--check`. Brand words do not reclassify a file, and
tests prove that suspicious names such as `CustomProtocol` cannot create false
content ownership. The import graph intentionally covers explicit,
non-wildcard Java imports only. Same-package, wildcard, reflective,
configuration, and data coupling remains visible through owner/signal
inventories and must receive a stronger contract in the appropriate later R2
phase rather than inferred here.

Verification completed headlessly against evidence base `6e1720900b`:

- two independent JSON generations were byte-identical at SHA-256
  `2aeaf25cb1edcdf89e72949c33d5e232830b6e2f63bcf2148012e85712c52324`;
- `python3 scripts/audit-server-r2.py --check --base spoiled-milk/main` passed;
- `python3 scripts/audit-server-build.py --check` passed with 21 libraries and
  no validation errors;
- `python3 tests/myworld/test-server-r2-boundary-audit.py` passed 6 tests;
- `python3 tests/myworld/test-server-r2-dependency-guard.py` passed 3 tests;
- `python3 tests/myworld/test-server-build-source-of-truth.py` passed;
- `python3 tests/myworld/test-myworld-plugin-layout.py` passed;
- `python3 tests/myworld/test-standalone-layout.py` passed;
- `./scripts/lint.sh all --base spoiled-milk/main --offline` completed the
  changed-code compiler, Checkstyle, PMD, ShellCheck, Ruff, CPD, and SpotBugs
  lane with a current metadata receipt and no new gated finding; and
- `./scripts/build-server.sh` compiled 916 core and 492 plugin sources and
  passed its required artifact audit.

No server/client process was launched, no release was built, and no live or
private data was accessed. The repository-wide `tests/myworld/test-all.sh` was
not run because it invokes `test-smoke.sh`, which launches a server and the
owner explicitly prohibited launches during this unattended milestone. R2-0
has no manual gameplay acceptance requirement because it changes only
repository analysis and documentation.

Recommended R2-1 start: characterize `Server` and `ServerConfiguration` into
typed read-only views first, beginning with process/network and diagnostics,
while retaining every current key, default, startup stage, and mutable facade.
Before extraction, use the R2-0 reverse graph to select one low-content-signal
view and add configuration/default/startup-order parity tests. Do not begin by
moving the 32 unresolved content sources or by interpreting profile keys as a
new stable plugin API.

### R2-1: Bootstrap, configuration, and lifecycle composition

Goal: create a testable composition root without changing selected content or
startup order.

Work:

- extract typed configuration views for process/network, persistence,
  diagnostics, world runtime, compatibility, tools, and content;
- retain old config keys through named adapters and produce deprecation
  diagnostics without rewriting checked-in configs in one commit;
- extract startup stages and resource ownership from `Server`;
- replace internal unconditional process exits with result/exception boundaries
  where needed for tests, while keeping the command-line exit contract;
- define idempotent rollback and shutdown for partially started services; and
- add a minimal fixture composition that starts no public listener.

Dependencies: R2-0.

Verification:

- configuration parity fixtures for `myworld.conf` and `myworld-host.conf`;
- missing/invalid/unknown-profile failure fixtures;
- startup-order and partial-start rollback tests;
- clean shutdown, repeated shutdown, and no-listener-before-validation tests;
- existing private-launcher guards;
- full server build and changed-code analysis; and
- private SQLite startup/shutdown on a localhost non-conflicting port only when
  runtime verification is required.

Gate:

- the existing Spoiled Milk composition starts in the same order and behavior;
- bootstrap can be constructed with a fixture profile in a compiled test;
- no service thread or listener survives a failed composition; and
- public launch/deployment scripts are unchanged unless their guards become
  stricter.

### R2-2: Extension registry and legacy plugin adapter

Goal: make content registration explicit and reversible while preserving all
existing plugin behavior.

Work:

- add package descriptors, ownership receipts, dependency resolution, and
  transactional registries;
- cover commands, quests, minigames, trigger handlers, item effects, drops,
  transitions, shops, and scheduled events;
- add a narrow runtime context for new extensions;
- adapt the current `plugins.jar` reflection path into one declared legacy
  package;
- prove unload cancels package-owned scheduled work and closes owned threads;
- separate discovery from activation; and
- do not split artifacts until parity exists.

Dependencies: R2-1.

Verification:

- deterministic discovery/registration order;
- duplicate identity, missing capability, cycle, partial-registration rollback,
  and reload/restart-required fixtures;
- current default-handler and blocker semantics;
- authentic quest/minigame/plugin counts;
- scheduled-event cancellation and plugin-thread shutdown;
- existing plugin layout/default fallback tests;
- full Ant core/plugin build; and
- private representative dialogue, command, shop, combat, and logout checks.

Gate:

- current `plugins.jar` runs through the adapter with behavior parity;
- a second compiled fixture extension uses only the narrow API;
- package registration can be completely attributed and rolled back; and
- no content package requires a new direct foundation import.

### R2-3: Target definitions, population, and world-package boundary

Goal: remove target/content selection from foundation loaders.

Work:

- define immutable definition-set descriptors and stable fingerprints;
- separate baseline definitions/population plugins from Spoiled Milk
  definitions, generated IDs, removals, and additions;
- make definition contribution conflicts fail before world population;
- move exact Preservation/Spoiled Milk/Builder native-package validation policy
  out of the generic package parser/catalog and into declared profile
  validators;
- retain strict hashes, counts, definition-range checks, and descendant policy;
- place MyWorld starter/welcome behavior behind a Spoiled Milk lifecycle
  extension; and
- add a minimal target fixture without presenting it as the definitive vanilla
  product.

Dependencies: R2-2 and the existing layered-world foundation.

Verification:

- stable fingerprint regardless of filesystem iteration order;
- duplicate/missing/out-of-range definition rejection;
- exact `0.5.0` Spoiled Milk package/profile acceptance and altered-hash/count
  rejection;
- Preservation fixture remains loadable as compatibility evidence, without
  promoting it to the selected product;
- legacy packed-map target still loads;
- layered package/catalog/foundation, player-location, spatial, protocol, and
  landscape sync tests;
- server/plugin builds; and
- private Spoiled Milk load, transition, interaction, reconnect, and fallback
  checks.

Gate:

- the foundation can load a target fixture with no Spoiled Milk definition or
  content source;
- Spoiled Milk selects all custom definitions/population through a manifest;
- exact layered fail-closed behavior is unchanged; and
- `ActionSender`, `World`, and foundation definition loaders no longer own
  Spoiled Milk welcome/starter/content selection.

### R2-4: Protocol profiles and capability negotiation

Goal: establish one authority for client compatibility metadata while
preserving every active packet generator.

Work:

- define immutable protocol profiles that bind login version/range, payload
  generator, client limitations, transport support, coordinate model, target
  definition fingerprint, map fingerprint, and capabilities;
- replace duplicated generator/version conditionals with profile lookup;
- add an explicit custom-client negotiation exchange with versioned,
  length-bounded capability data;
- keep legacy inference for clients that cannot negotiate;
- centralize layered scene/terrain/movement packet subprotocol versions;
- make mismatch responses bounded and non-sensitive; and
- retain packet timing/byte telemetry outside content code.

Dependencies: R2-2 for capability ownership and R2-3 for fingerprints.

Verification:

- table-driven coverage of every current payload generator family;
- legacy login/relogin and custom login/create flows;
- supported/unsupported version, target, map, definition, and coordinate
  combinations;
- unknown capability and downgrade/upgrade refusal;
- TCP and WebSocket protocol smoke where supported;
- server sync modernization and layered protocol/client authority tests;
- full server/client builds; and
- private login, reconnect, movement, combat, teleport, and layered transition
  checks with a maintained custom client.

Gate:

- one profile selection determines generator and limitations;
- all old clients retain their existing successful paths;
- custom clients enter the world only after compatible fingerprints and
  capabilities are established; and
- no content package parses raw login or packet-version internals.

### R2-5: Versioned persistence and recoverable migrations

Goal: make upgrades inspectable and reversible before independent packages can
mutate player state.

Work:

- define foundation, target, and content migration namespaces/levels;
- inventory existing SQL patches and typed player-cache migrations;
- fail closed when patch history cannot be read or recorded;
- produce a dry-run migration plan and preflight report;
- require backend-appropriate backup/restore prerequisites before mutation;
- write durable migration/install receipts with source and target versions;
- validate rows, definition references, layered locations, and package-owned
  state after migration;
- prove that a target-only row does not acquire Spoiled Milk state on login;
- define content removal/downgrade policy without deleting unknown state; and
- retain existing database APIs except where a bounded safety fix is required.

Dependencies: R2-1 lifecycle, R2-2 package ownership, and R2-3 definition/map
identity.

Verification:

- clean database, already-current database, partial history, unreadable history,
  failed patch, failed receipt, corrupt cache, unknown-newer level, and repeated
  migration fixtures;
- SQLite backup, migration, validation, restore, and byte/integrity checks on a
  copied private database;
- layered persistence round trips and exact legacy receipt rollback;
- target-only and Spoiled Milk row matrices;
- offline/social failure and redaction regressions;
- server/plugin builds and static analysis;
- MySQL SQL/compile parity, with isolated runtime testing only when credentials
  and a safe environment exist.

Gate:

- every mutation is attributable to a versioned owner;
- failed upgrades have a tested restoration path and actionable receipt;
- repeated successful upgrade is idempotent;
- vanilla-compatible rows run without Spoiled Milk-only behavior; and
- live activation remains a separate, permission-gated operator action.

### R2-6: Diagnostics, health, and shutdown hardening

Goal: make foundation diagnostics bounded and packageable without retaining
every research observer in the default runtime.

Work:

- move tick/network/cache/loader counters out of `Server` into typed metrics
  owners while preserving measurements;
- define structured startup, package, protocol, persistence, and shutdown health
  reports;
- classify layered parity/proof code as production diagnostic, private
  diagnostic pack, test fixture, historical proof, or unresolved;
- bound memory, file size, retention, and sample rates;
- preserve B05 redaction and best-effort cleanup behavior;
- ensure plugin/content failures include package identity but no credentials,
  private messages, message bodies, or personal data; and
- make shutdown idempotent for zero/partial/full startup.

Dependencies: R2-1 through R2-5 so reports describe the real ownership model.

Verification:

- exact counter parity for retained metrics;
- disabled-by-default and bounded-retention tests;
- log-redaction fixtures;
- package failure attribution;
- normal, forced-disconnect, repeated-cleanup, partial-start, and process
  shutdown tests;
- server synchronization/diagnostic tests;
- full server build and changed-code analysis.

Gate:

- production package diagnostics are explicitly inventoried;
- enabling diagnostics cannot change authoritative behavior;
- cleanup failures are visible but do not make successful cleanup fatal; and
- no default report contains secrets, message contents, or personal data.

### R2-7: Ant-authoritative standalone packaging

Goal: emit a reproducible Server R2 archive while keeping the current combined
repository and hosted workflow operational.

Work:

- define a Server package manifest and deterministic staging script;
- build only from clean exact source using `scripts/build-server.sh`;
- decide and document one dependency layout, proving class/artifact parity
  before changing the current fat/runtime duplication;
- include foundation binaries, target-profile slots, libraries, database
  schemas/patches, config templates, diagnostics, extension docs, licenses,
  source commit, hashes, and compatibility metadata;
- exclude credentials, databases, backups, logs, captures, local env files,
  Builder workspaces, and Spoiled Milk Content from the foundation archive;
- provide verify, install/stage, launch, update preview, backup prerequisite,
  rollback, and clean-uninstall behavior;
- keep Spoiled Milk as a separately composed distribution artifact;
- keep Gradle explicitly non-authoritative; and
- add packaging checks without changing live deployment authorization.

Dependencies: R2-1 through R2-6.

Verification:

- two clean builds produce identical inventories and explain any unavoidable
  byte differences;
- build audit with required artifacts;
- archive traversal/link safety;
- hash/provenance and license inventory;
- fresh extraction in an empty directory;
- target fixture startup/shutdown;
- plugin discovery;
- SQLite create/start/backup/restore;
- server-only version update and rollback;
- missing/tampered dependency, definition, map, migration, and package failures;
- Linux launch required for the initial alpha; Windows layout/launcher tests
  required before claiming Windows server support; and
- existing World Builder and Spoiled Milk player packages remain unaffected.

Gate:

- the archive runs without repository-relative hidden inputs;
- no Spoiled Milk content is present in the foundation archive;
- exact package contents and hashes are published;
- update failure restores the previous runnable installation and data; and
- the existing hosted Spoiled Milk deployment remains guarded and runnable.

### R2-8: Distribution acceptance

Goal: satisfy the Phase 4 product exit gate for both a selected vanilla target
and Spoiled Milk.

Dependencies: R2-7 and an explicit owner decision selecting/resuming the vanilla
target.

Required matrices:

- selected vanilla target with no Spoiled Milk package;
- complete Spoiled Milk target/content composition;
- legacy packed-map compatibility where supported;
- reviewed layered-world composition;
- SQLite fresh/upgrade/rollback;
- MySQL compile/schema and optional isolated runtime;
- legacy client families and maintained custom client;
- private launch, clean shutdown, restart, reconnect, backup/restore, and
  server-only update; and
- installation with diagnostics disabled and enabled.

Gate:

- the selected vanilla target runs with no Spoiled Milk content artifact or
  state dependency;
- Spoiled Milk uses only declared extension/capability boundaries;
- upgrades preserve and validate state with tested rollback;
- extension authors can build against the published contract and fixtures;
- packaging/provenance is reproducible; and
- private owner review passes before a release candidate is offered.

The current Preservation profile may be used as research and a compatibility
fixture. It does not satisfy this gate while vanilla promotion remains paused
and the owner has not selected it as the definitive target.

## Phase Dependencies

```text
R2-0 ownership baseline
  -> R2-1 bootstrap/config/lifecycle
      -> R2-2 extension registry
          -> R2-3 target definitions/maps
              -> R2-4 protocol profiles
              -> R2-5 persistence/migrations
                  -> R2-6 diagnostics/health
                      -> R2-7 package
                          -> R2-8 distribution acceptance
```

R2-4 and R2-5 may use separate branches after R2-3, but neither may publish a
stable contract until the other validates the same target/content composition.
Packaging inventories begin in R2-0, but a public artifact is not produced
until R2-7.

## Packaging Contract

The proposed foundation archive shape is illustrative:

```text
server-r2/
  bin/
    server launcher and verifier
  lib/
    authoritative server and dependency artifacts
  api/
    extension API artifact and generated documentation
  profiles/
    README and explicit install slots; no implicit Spoiled Milk default
  database/
    sqlite/
    mysql/
    migration manifests
  config/
    server.example.conf
    logging defaults
    schemas
  diagnostics/
    bounded operator tools and schemas
  docs/
    install, extension, compatibility, recovery, and security guidance
  LICENSES/
  MANIFEST.json
  SHA256SUMS.txt
  SOURCE-COMMIT.txt
  VERSION.txt
```

The final layout is decided only after R2-7 proves the classpath. The archive
must never include a real `.db`, secret-bearing config, certificate/private key,
packet capture, log, launch marker, or user-generated world package.

The package manifest records:

- Server package version and source commit;
- Java/runtime requirements;
- Ant build/audit version;
- file hashes and licenses;
- provided capability versions;
- supported protocol/target/persistence ranges;
- migration manifest level;
- config schema level;
- extension API level;
- known limitations; and
- whether each bundled item is foundation, profile, compatibility, diagnostic,
  or documentation.

## Verification Matrix

| Concern | Automated evidence | Private evidence |
| --- | --- | --- |
| Content neutrality | import/ownership guard; artifact inventory; no MyWorld definitions/plugins/cache keys in foundation | target-fixture startup without Spoiled Milk |
| Ant authority | build-source audit; core/plugin contents; classpath checks | extracted-package launch |
| Extensions | compiled fixture; ordering/conflict/rollback/unload tests; legacy adapter parity | quests, NPC dialogue, commands, shops, timed events |
| Definitions | stable fingerprints; conflict/range/missing-file rejection | representative authentic and Spoiled Milk definitions |
| Layered maps | current package/catalog/player/spatial/protocol suites; exact `0.5.0` checks | transitions, collision, reconnect, legacy fallback |
| Networking | table-driven profile/generator tests; negotiation failures; TCP/WS smoke | login, reconnect, movement, combat, teleport |
| Persistence | migration dry-run/receipt/idempotence/corruption tests; copied SQLite restore | private copied-account round trips |
| Diagnostics | metrics parity, bounds, redaction, disabled-mode tests | readable startup/failure/shutdown reports |
| Lifecycle | partial-start rollback; repeated shutdown; no leaked listener/thread tests | clean stop/restart on a private port |
| Packaging | clean-source provenance; hashes; tamper/fresh-install/update/rollback tests | launch from extracted archive |
| Hosted safety | existing launcher/deploy/stop guard tests | no public work in implementation branches |

Every runtime phase also runs:

- focused fixtures for the changed boundary;
- `python3 tests/myworld/test-static-analysis-baseline.py`;
- changed-code compiler analysis without broad baseline churn;
- `./scripts/build-server.sh`; and
- client build only when a protocol contract changes.

No phase reduces a warning/static-analysis baseline except for exact findings
removed by that phase.

## Risk Register

### Accidental gameplay drift

Risk: moving content selection or plugin registration changes ordering, default
handlers, timing, or balance.

Mitigation: compatibility facades, recorded discovery order, compiled parity
fixtures, representative private gameplay, and one ownership family per branch.

### False content neutrality

Risk: an archive omits `custom` plugins but still embeds MyWorld definitions,
cache keys, startup behavior, or map policy in `core.jar`.

Mitigation: class/resource scanning, reverse-dependency guards, target-only
startup, and negative artifact tests.

### Extension API frozen too early

Risk: current plugin internals are repackaged as a permanent public API.

Mitigation: mark capabilities provisional, keep an internal adapter, require two
consumers, and publish stable v1 only after upgrade/unload experience.

### Protocol fragmentation

Risk: another capability table is added beside `ClientLimitations`, generator
selection, and layered subprotocol versions.

Mitigation: R2-4 must replace duplicated metadata authority, not merely wrap it.

### Data loss or irreversible downgrade

Risk: independent content updates outrun SQL patch history or player cache
compatibility.

Mitigation: fail-closed history reads, dry runs, backups, receipts, post-checks,
unknown-newer refusal, and tested restore before release.

### Layered loader regression

Risk: separating branded profile policy weakens strict package validation,
transition isolation, or Builder/hosted separation.

Mitigation: retain exact current validators through adapters first; require all
current layered suites and private routes before authority moves.

### Packaging classpath conflict

Risk: changing fat-JAR duplication exposes library version/order differences.

Mitigation: inventory first, build from Ant, compare artifact/class contents,
and retain the current layout until a new layout proves launch parity.

### Diagnostic bloat mistaken for runtime API

Risk: large proof systems become mandatory package surface or are deleted
without understanding reflection/config use.

Mitigation: classify by references, flags, outputs, and operator value; move or
exclude only after a dedicated proof.

### Vanilla-target ambiguity

Risk: a fixture or paused Preservation profile is advertised as the definitive
vanilla product.

Mitigation: keep R2-8 blocked pending an explicit owner decision and use
“fixture” or “compatibility evidence” labels before then.

### Live-server disruption

Risk: packaging work is mistaken for deployment authorization.

Mitigation: preserve the current manager/live worktree, backup, `::update`,
warning-window, free-port, and explicit-permission gates. Server R2 work never
grants shutdown authority.

## Cross-Branch Stop Conditions

Stop the current implementation branch and return to planning if:

- it needs a gameplay, balance, quest, map-content, or definitive vanilla-target
  decision;
- successful extraction requires dropping a supported client, backend, plugin
  trigger, packed-map path, or layered-world invariant;
- a data migration has no tested backup and restore route;
- package neutrality can be achieved only by silently omitting unresolved code;
- the Ant and proposed artifact paths disagree without a reproducible
  explanation;
- the branch expands into dependency upgrades, Gradle authority, package moves,
  or repository splits;
- a private test would bind the public port or touch the public database; or
- public shutdown/restart would be needed without fresh explicit authorization.

## Recommended First Implementation Branch

Branch: `chore/server-r2-boundary-baseline`

Bounded purpose: implement R2-0 only.

Deliverables:

- a deterministic Server R2 ownership/dependency inventory in human and JSON
  form;
- explicit owner categories and an unresolved list;
- current Ant artifact/plugin/config/definition/database/map inputs;
- a changed-code guard preventing new foundation imports of Spoiled Milk
  content;
- refreshed counts in this plan; and
- tests for determinism, false classification, and build-audit integration.

Out of scope:

- runtime source moves;
- new plugin APIs;
- changed startup behavior;
- artifact splitting;
- config rewrites;
- migrations;
- dependency changes; and
- public/private server launch.

Acceptance:

- report determinism;
- no build artifact/content changes;
- layout, plugin, build-authority, and static-analysis fixtures pass;
- authoritative server build passes; and
- manager approves the boundary before R2-1.

## Decisions Reserved for Discussion

These choices are intentionally unresolved:

1. Which preserved vanilla baseline becomes the definitive Server R2 acceptance
   target, and when its paused promotion resumes.
2. Whether the first public Server R2 artifact carries a thin dependency
   directory or retains a fat core JAR after parity testing.
3. The final package/extension capability names and version numbers.
4. Whether authentic and retro content ship as separate target packages or as
   one initial baseline bundle.
5. Which diagnostics belong in the default foundation archive versus a private
   diagnostic pack.
6. The initial supported OS/runtime matrix beyond required Linux validation and
   retained Java 8 compatibility.
7. The isolated MySQL environment and operator-qualified backup/restore process.
8. Whether hot reload remains a supported product feature after lifecycle
   ownership is made explicit.

None of these decisions should be settled indirectly by the first extraction
branch.

## Final Phase 4 Acceptance

Server R2 satisfies the Remaster Suite Phase 4 gate only when:

- its content-neutral archive launches a selected vanilla target without
  Spoiled Milk content;
- Spoiled Milk loads through declared package, definition, protocol, map,
  persistence, and extension capabilities;
- all currently supported compatibility paths have an explicit support status;
- persistence upgrades have validated receipts and tested rollback;
- private launch, clean shutdown, backup/restore, server-only update, plugin
  discovery, target behavior, and Spoiled Milk behavior pass;
- package inputs, outputs, hashes, licenses, and provenance are reproducible;
- Gradle remains accurately labeled unless full parity is independently proven;
  and
- the public Spoiled Milk server continues to use the guarded, permission-gated
  live workflow.

## Repository Evidence Index

The principal current implementation evidence for this plan is:

- `server/build.xml`: Java 8 Ant compilation, fat `core.jar`, separate
  `plugins.jar`, and runtime classpaths.
- `scripts/build-server.sh` and `scripts/audit-server-build.py`: authoritative
  build entry point and reproducible Ant/Gradle/artifact inventory.
- `server/build.gradle`: explicitly secondary dependency/source description.
- `server/src/com/openrsc/server/Server.java`: current composition root,
  startup, patch application, listeners, telemetry state, and shutdown.
- `server/src/com/openrsc/server/ServerConfiguration.java`: current flat
  configuration and content/layered/diagnostic gates.
- `server/src/com/openrsc/server/plugins/io/PluginJarLoader.java` and
  `plugins/handler/PluginHandler.java`: reflective `plugins.jar` discovery,
  trigger registration, whole-Server injection, and unload behavior.
- `server/src/com/openrsc/server/net/rsc/ActionSender.java`: payload generator
  selection plus the concrete Spoiled Milk welcome/starter coupling.
- `server/src/com/openrsc/server/net/rsc/ClientLimitations.java` and
  `LoginPacketHandler.java`: separate version-limit and login-layout authority.
- `server/src/com/openrsc/server/database/patches/PatchApplier.java` and
  `JDBCPatchApplier.java`: current patch discovery, history, execution, and
  receipt behavior.
- `server/src/com/openrsc/server/io/NativeLayeredWorldPackage.java`,
  `NativeLayeredWorldPackageCatalog.java`, and
  `NativeLayeredWorldRuntimeProfile.java`: generic package behavior and the
  currently combined exact target/tool policies.
- `server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java`
  and telemetry fields in `Server`: retained proof and metrics ownership.
- `scripts/run-server.sh`, `scripts/run-hosted-server.sh`,
  `scripts/deploy-live-main.sh`, `scripts/stop-hosted-server.sh`, and
  `docs/workspaces/live-deployment.md`: private/hosted separation and live
  safety gates.

## Planning-Branch Verification

The planning branch ran:

- `git diff --check` — passed;
- `python3 tests/myworld/test-standalone-layout.py` — passed;
- `python3 tests/myworld/test-server-build-source-of-truth.py` — passed;
- `python3 tests/myworld/test-myworld-plugin-layout.py` — passed;
- `python3 tests/myworld/test-static-analysis-baseline.py` — passed; and
- `python3 scripts/audit-server-build.py --check --json` — passed with 21
  shipped libraries and no validation errors.

`python3 tests/myworld/test-private-server-launchers.py` passed three of four
tests and exposed an unrelated clean-main mismatch: the test expects tracked
`Client_Base/Cache/port.txt` to contain private port `43615`, while commit
`4506ccda2` contains public port `43605`. This documentation branch does not
change launchers, runtime cache defaults, or that test. The mismatch should be
handled by a focused launcher/default-endpoint branch.

## Planning Change Log

- **2026-08-04:** Refreshed the checked R2 ownership inventories after the
  accepted boundary-loading integration. The current tree contains 1,812
  shipped inputs and 920 Ant core sources; the four new inputs are foundation
  transport/delivery classes. The known dependency debt remains exactly 83
  edges and the unresolved set remains 109 inputs.

- **2026-08-04:** Completed R2-0 against published main `6e1720900b`. Added
  deterministic human/JSON ownership and dependency inventories, classified
  all 1,808 shipped inputs (including 109 explicit unresolved records),
  baselined 83 existing foundation-to-Spoiled-Milk import edges, enforced new
  edge refusal, integrated the Ant build audit, added focused determinism,
  classification, and dependency tests, and completed all headless acceptance
  checks. R2-1 remains unstarted pending manager integration of this boundary.

- **2026-07-30:** Created the dedicated Server R2 plan from Phase 4. Reconciled
  the current Ant authority, B01-B11 server work, synchronization foundations,
  layered package/runtime `0.5.0`, plugin/content coupling, protocol metadata,
  persistence patches, diagnostics, packaging gaps, and hosted safeguards.
  Selected a non-behavioral ownership baseline as the first bounded branch and
  left the definitive vanilla target as an explicit owner gate.
