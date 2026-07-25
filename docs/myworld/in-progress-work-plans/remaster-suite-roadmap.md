# Remaster Suite Roadmap

Status: owner-approved product roadmap; implementation is not yet authorized by
this document alone

Started: 2026-07-17

## Purpose

Spoiled Milk has produced substantial client, renderer, server, map, editor,
diagnostic, and workflow improvements that are useful beyond Spoiled Milk's
custom game. The long-term product should therefore be more than one modified
server. It should provide a reusable **Remaster Suite** whose technical
capabilities can be installed together or selected independently.

The Suite must support at least these outcomes:

- a technically remastered game using otherwise vanilla content;
- a renderer-only installation where that is compatible;
- a server-foundation installation without Spoiled Milk gameplay content;
- layered maps authored by the new World Builder and run by a compatible
  client/server;
- third-party content that declares and uses selected Remaster capabilities;
- the complete Spoiled Milk game as one optional content distribution built on
  the same foundation.

This is a start-to-finish product and architecture roadmap. It defines ordering,
boundaries, dependencies, and completion gates. Detailed implementation remains
owned by focused module plans and short-lived topic branches.

## North-Star Product

The final product is a family of versioned capabilities and packages, not a
second monolithic fork.

```text
                          Remaster Suite
                                |
       +------------------------+------------------------+
       |             |              |          |         |
    Renderer       Server       Layered Maps  Builder  Content
       |             |              |          |         |
       +-------------+--------------+----------+---------+
                                |
                    tested end-user distributions
                                |
          vanilla remaster / custom world / Spoiled Milk
```

The downloadable Suite may present convenient bundles, but its internal
packages must retain explicit ownership and dependency metadata. A user should
not have to install Spoiled Milk content to receive technical improvements.

## Terminology

- **Remaster Suite:** the complete product family and its supported bundle
  combinations.
- **Primary module:** one of the five owner-selected product areas: Renderer,
  Server, Layered Maps, World Builder, or Content.
- **Capability:** a stable, versioned technical contract exposed by a module,
  such as `renderer.opengl` or `world.layered-coordinates`.
- **Package:** a distributable artifact with a manifest, hashes, requirements,
  compatibility claims, and installation contents.
- **Bundle:** a tested selection of packages presented as one download or
  installation choice.
- **Target profile:** an explicit adapter for a known client, server, content,
  definition, or repository layout. Similar folder names are not compatibility
  evidence.
- **Vanilla:** the selected supported baseline without any content that was not
  part of that baseline.
- **Content:** all gameplay or world material that was not part of vanilla,
  including Spoiled Milk quests, skills, balance, items, NPC behavior, shops,
  custom areas, placements, transitions, and associated definitions or UI.
- **Legacy map:** the current packed-Y/four-plane map and tooling format.
- **Layered map:** a map using explicit signed `(x,y,level)` coordinates and the
  new layered capability contract.

Calling something a module does not promise that it can be safely hot-loaded or
removed while a game is running. Some modules are source/build capabilities or
install-time bundles because they alter core runtime identity.

## Non-Negotiable Product Rules

1. **Content neutrality is provable.** The foundation must build and run against
   a supported vanilla target without silently loading Spoiled Milk content.
2. **Spoiled Milk is a consumer.** Foundation modules may expose capabilities to
   Spoiled Milk; they must not depend on Spoiled Milk gameplay behavior.
3. **Compatibility is explicit.** Every package identifies supported target
   profiles, capability versions, definition expectations, and known
   incompatibilities.
4. **Unknown targets fail safely.** Installers and tools must not patch an
   arbitrary OpenRSC-derived project because its folders happen to look
   familiar.
5. **Dependencies are directional.** Content may require foundation
   capabilities. Foundation code may not import optional content.
6. **Modules release independently where truthful.** A renderer package, server
   package, or Builder release can have its own version without pretending that
   unrelated modules changed.
7. **Bundles are tested combinations.** Being individually installable does not
   mean every version combination is supported.
8. **Legacy work is preserved.** The last packed-Y map format and compatible
   World Builder remain documented and available while layered tooling evolves.
9. **Conversion is non-destructive.** Legacy-to-layered conversion operates on
   copies, records fingerprints and receipts, and never rewrites the source
   project in place.
10. **State migrations are reversible.** Map imports, definition changes,
    configuration changes, and player-database migrations require backups,
    receipts, validation, and rollback paths.
11. **No public-server shortcuts.** Suite development does not weaken the live
    deployment, warning, backup, worktree, or database safety contracts.
12. **Distribution rights are verified.** Maps, sprites, textures, models,
    audio, dependencies, and bundled runtimes require an explicit provenance
    and redistribution decision before public packaging.
13. **Repository separation follows architecture.** Code is not moved into a
    separate repository merely to look modular. Stable ownership and build
    boundaries come first.
14. **Current module roadmaps remain active.** Productization does not imply
    that Renderer, Server, Layered Maps, World Builder, or Content are finished
    technically or creatively.

## Primary Module Boundaries

### 1. Renderer

The Renderer module owns technical client presentation:

- desktop OpenGL windowing and input integration;
- renderer-facing frame and world-data contracts;
- terrain, wall, roof, scenery, object, sprite, UI, and overlay presentation;
- lighting, tones, sky, fog, shadows, glow, terrain relief, and materials;
- remastered asset override loading with original fallback;
- renderer settings, profiles, migrations, and diagnostics;
- maintained software/classic fallback behavior where included;
- performance telemetry, capture, and visual-comparison tooling.

It does not own quests, combat balance, item effects, custom areas, server
authority, or Spoiled Milk-specific gameplay. It may consume definition and
asset identities through a target profile, but those dependencies must not be
hard-coded as Spoiled Milk assumptions.

The first reusable Renderer target is the maintained desktop client. Android,
web, and unmodified legacy clients require separately proven adapters and are
not implied by the initial module.

### 2. Server

The Server module owns content-neutral runtime foundations:

- bootstrap, configuration, private/hosted launch boundaries, and shutdown;
- world ticking, entity lifecycle, regions, pathing, collision, and scheduling;
- networking, synchronization, packets, compatibility negotiation, and limits;
- persistence APIs, migrations, database safety, and recovery;
- plugin discovery and stable extension contracts;
- definitions and generated-data loading contracts;
- performance, movement, failure, and operational diagnostics;
- administrator, deployment, backup, and release safety foundations;
- content-neutral world services needed by maps and plugins.

The Server module does not own Spoiled Milk quests, custom skill rules, custom
drops, balance, shops, guilds, NPC scripts, or optional map additions. Existing
code must be classified and extracted until a vanilla-profile build proves that
separation.

### 3. Layered Maps

The Layered Maps module has two internal artifact types because the feature is
too fundamental to be only a data plugin:

1. **Layered-world engine capability**
   - makes level part of point, region, tile, entity, object, item, collision,
     visibility, pathing, area, persistence, cache, protocol, and client-loading
     identity;
   - implements signed `WorldCoordinate(x,y,level)` semantics;
   - owns named compatibility codecs at legacy boundaries;
   - exposes map-format and transition APIs to tools and content.
2. **Layered map packages**
   - contain or reference terrain and every coordinate-bearing world element
     the package owns;
   - declare levels, extents, transitions, definitions, capabilities, and
     source fingerprints;
   - distinguish complete worlds, world modules, and focused map patches.

Canonical levels begin with surface `0`, upper floors `+1`, `+2`, and onward,
underground `-1`, and deep underground `-2`. Ordinary vertical anchors preserve
X/Y by default. Local walkable arrival offsets are explicit, while long-distance
transport, magical, quest, and instance-like edges are classified separately.

`WorldCoordinate(x,y,level)` remains purely geographic. The layered runtime
places it inside a separate world-space/location identity whose initial value is
the one global static world. Map and transition schemas reserve template and
capability metadata so a later `world.instances` server capability can create
isolated player/party spaces without packing instance identity into coordinates
or revising the layered map format. No current map is made instanced merely by
conversion.

Layered maps use sparse `48 x 48` terrain sectors keyed by world space, level,
and signed sector coordinates. Logical X/Y are signed 32-bit tile coordinates;
packages declare finite extents and runtimes enforce configured safety limits
rather than imposing another legacy canvas. Stable area IDs, exclusive base-
terrain ownership, explicit overlay contracts, and creator-controlled growth
reservations make allocation and package conflicts auditable over long-running
world expansion. Dynamic instances reuse template allocations instead of
consuming new permanent coordinates.

The 48-tile value is a storage-page contract, not a required renderer,
simulation, visibility, or network-streaming chunk size. The selected follow-up
is an incremental `world.streaming` boundary: smaller presentation
chunks remain resident across movement, use world-space/level-aware keys, load
predictively, and activate only when their terrain and static scene are ready.
Server interest management and renderer residency may use different internal
cell sizes while consuming the same layered location contract. The existing
legacy 3x3 section-window path remains an adapter during parity migration.
Streaming is implemented as the separately gated milestone immediately after
coordinate/behavior parity and before map realignment or conversion export
depends on it.

The end-user Layered Maps distribution also composes these artifacts with a
one-way converter, compatible World Builder, target adapter, and private test
harness. It must be usable to upgrade compatible RuneScape Classic-derived
worlds rather than being a prebuilt Spoiled Milk map alone. The converter first
emits a parity-preserving normalized project, then analyzes transition graphs
and terrain components to propose geographic alignment and deeper levels. The
source stays unchanged, ambiguous ownership requires review, and reverse export
to the packed format is not promised.

The layered schema and converter are topology-neutral. Long-distance ladders,
same-level transport, unconventional transition objects, and disconnected
networks remain valid creator choices. Classification and reports describe
their behavior without treating geographic convention as a content rule;
validation enforces representability and integrity rather than aesthetics.

The initial distribution targets map authors and server maintainers. It is a
tool folder extracted into a target repository root, followed by an explicit
preflight and conversion command; it is not presented as an in-game player
setting. Merely extracting the folder must not alter world data or runtime
source. Conversion emits deterministic human- and machine-readable reports of
all transformations, retained misalignments, accepted exceptions, unsupported
owners, and other topology oddities.

Its review UI may be a focused Builder-derived conversion workbench rather than
a duplicate of the full standalone World Builder. The workbench owns staged
inspection, corrective edits, convenient world navigation, reports, and a
private dev client/server launcher. Conversion may produce provisional output
for every area automatically because the target remains untouched. Only a
separate, explicitly confirmed final export script may modify target files,
after diff review, fingerprint verification, backup creation, and warning
acknowledgement.

A map depends on Renderer only when it truly requires renderer-specific assets
or behavior. Layered coordinates themselves depend on the layered-world engine
capability, not a particular visual style.

### 4. World Builder

World Builder remains an independently released tool and repository. It owns:

- safe target discovery through named layout adapters;
- isolated projects and local Builder runtime workspaces;
- terrain, placement, transition, and metadata editing;
- deterministic exports, manifests, fingerprints, and change summaries;
- transactional import, backups, receipts, and rollback;
- legacy packed-map support and read-only conversion;
- layered-map validation and authoring;
- conversion-review overlays and private launch/test-before-export workflow;
- compatibility checks against target capabilities and definitions;
- end-user launchers, updater behavior, documentation, and diagnostics.

The current Builder generation remains the legacy packed-map editor. The
layered generation must use a separately named schema/adapter and must never
guess a project's coordinate model.

World Builder does not own a target server's player accounts, live database,
public process, arbitrary source patches, or gameplay scripts it cannot fully
inventory.

### 5. Content

Content means material that was not part of the chosen vanilla baseline. The
Spoiled Milk Content module includes, where applicable:

- custom quests, skills, systems, guilds, progression, balance, and rewards;
- custom items, definitions, recipes, shops, drops, NPCs, and behaviors;
- custom interfaces or guide data whose purpose is the optional gameplay;
- custom terrain, areas, scenery, NPC placements, ground spawns, transitions,
  and geographic realignment;
- scripts, recovery paths, persistence fields, and migrations required by that
  content;
- content-owned visual and audio assets;
- explicit capability dependencies on Renderer, Server, or Layered Maps.

The Suite also needs target profiles for vanilla definitions, IDs, maps,
protocols, and assets. Those profiles are compatibility infrastructure, not
Spoiled Milk Content. This distinction prevents a supposedly content-free
installation from inheriting hidden Spoiled Milk definitions or behavior.

The first Content package may contain all Spoiled Milk content as one tested
bundle. Finer quest, skill, visual, or world packs may be considered only after
the primary separation is stable.

## Required Dependency Direction

| Consumer | May depend on | Must not require |
| --- | --- | --- |
| Renderer | client API, target definitions/assets, shared contracts | Spoiled Milk gameplay content |
| Server | shared contracts, target profile, optional installed content APIs | Renderer implementation, Spoiled Milk content |
| Layered-world engine | client/server world APIs, protocol and persistence contracts | a particular map package or Spoiled Milk content |
| Layered map package | layered-world capability, declared definitions, optional renderer assets | unrelated content or an undeclared source tree |
| World Builder | map schemas, adapters, capability metadata, definitions | a running target or hidden Spoiled Milk release coupling |
| Content | any explicitly declared foundation capability and target profile | undeclared patches or accidental repository layout |
| Suite bundle | a tested version matrix of selected packages | unsupported arbitrary combinations |

Cross-module communication should use narrow value types, schemas, service
interfaces, or generated contracts. Direct imports into another module's
internal implementation are migration debt and should be tracked as such.

## Supported End-State Distributions

The roadmap is complete only when the packaging system can truthfully produce
and test these shapes:

1. **Renderer-only development package**
   - Installs or builds the renderer against an explicitly supported client
     target profile.
   - Includes required shared client contracts but no server or custom content.
2. **Server-foundation package**
   - Runs an explicitly supported vanilla content profile.
   - Contains no Spoiled Milk quests, balance, custom map additions, or custom
     definitions unless the user selects them.
3. **Vanilla Remaster bundle**
   - Combines compatible Renderer and Server packages with a vanilla target
     profile and supported vanilla world/content data.
4. **Layered-world development bundle**
   - Combines layered client/server capability, a layered map package, and a
     compatible Builder without requiring Spoiled Milk Content.
5. **World Builder standalone package**
   - Runs independently and selects an explicit legacy or layered adapter.
6. **Third-party content bundle**
   - Adds a separately authored map or content package whose declared
     capabilities and definitions are satisfied.
7. **Spoiled Milk bundle**
   - Combines the tested foundation versions with the optional Spoiled Milk
     Content package.
8. **Complete Remaster Suite bundle**
   - Provides the supported foundation and tools as a convenience install,
     while still showing which modules and content selections are present.

"Any server/client" means any explicitly supported target profile or a target
that implements the published contracts. It does not mean silently modifying an
unknown fork.

## Current Starting Point

Useful foundations already exist, but they are not yet independently
distributable modules:

- Renderer-v2 has a playable OpenGL baseline, resident world geometry,
  lighting, shadows, sky, diagnostics, profiles, and legacy fallback.
- The server has production launch/build authority, hosted/private separation,
  external live-state protection, diagnostics, and a plugin boundary.
- The refactor program has begun extracting renderer, definition, equipment,
  spell, packet, settings, and scene ownership from oversized legacy classes.
- World Builder already provides isolated workspaces, deterministic authored
  bundles, strict fingerprints, transaction receipts, safe imports, rollback,
  standalone launchers, and its own repository/release channel.
- The terrain archive already stores an explicit plane in sector entry names,
  but active world coordinates, entities, regions, scripts, persistence, and
  other systems still rely on four packed-Y bands.
- AI-1's layered-world study selected signed levels, geographic anchors,
  intentional legacy-format divergence, capability-oriented manifests, and a
  one-way conversion strategy.
- Spoiled Milk-owned behavior is increasingly placed in custom namespaces, but
  gameplay content, definitions, base compatibility logic, client UI, maps, and
  inherited plugins remain interwoven.
- Build and release artifacts are currently oriented around the combined
  Spoiled Milk product rather than independently versioned module outputs.

## Delivery Strategy

### One roadmap, many focused plans

Each phase below should produce one or more focused plans with bounded branches,
tests, and handoffs. Do not create a single long-running "Remaster Suite"
implementation branch.

### Monorepo before unnecessary repository splits

Renderer, Server, Layered Maps, shared definitions, and Content should initially
remain in the current repository while ownership is extracted. The build may
emit independent artifacts before source moves. World Builder may remain in its
existing dedicated repository because it already has an independent lifecycle.

A new repository is justified only when:

- the module has a stable public contract;
- its build and tests no longer require private implementation details from the
  monolith;
- its version can advance independently without copying synchronized source;
- source history, contribution workflow, and release automation benefit from
  the split;
- cross-repository compatibility fixtures exist.

### Preserve a releasable Spoiled Milk throughout

Every extraction must preserve the current combined game until replacement
bundles pass parity. Feature work and public releases may continue while this
roadmap advances. A structural phase may not strand the live product between
old and new formats.

## Roadmap Phases

### Phase 0: Baseline, inventory, and freeze points

Goal: establish exactly what is being separated before changing ownership.

Work:

- Choose and fingerprint the supported vanilla baseline(s).
- Inventory source, definitions, maps, assets, scripts, configuration, database
  fields, generated data, and runtime artifacts.
- Classify each owned component as foundation, vanilla target profile, Spoiled
  Milk Content, shared compatibility, development-only, or legacy.
- Record every known Renderer-to-definition, Server-to-content,
  Map-to-script, Builder-to-repository, and client/server protocol dependency.
- Freeze and document the last legacy packed-map schema and adapter.
- Capture reproducible combined Spoiled Milk client/server builds and critical
  behavior baselines.
- Capture an independently identifiable vanilla behavior baseline suitable for
  later content-neutral testing.
- Inventory license, attribution, and redistribution status for source,
  dependencies, runtimes, maps, and assets.
- Identify secrets, player data, development caches, and generated artifacts
  that must never enter a package.

Exit gate:

- every active file class has an initial ownership classification;
- vanilla and Spoiled Milk baselines can be rebuilt or their blockers are
  explicitly recorded;
- the final legacy map/Builder generation is named and recoverable;
- no unknown redistribution dependency is silently assumed shippable.

### Phase 1: Minimal capability and package contracts

Goal: define the vocabulary all primary modules must implement.

Work:

- Select stable capability identifiers and initial versions.
- Define a minimal package manifest schema covering package type, version,
  hashes, target profiles, provided capabilities, required capabilities,
  conflicts, definitions, assets, coordinate model, and migration needs.
- Define exact package categories: engine capability, target profile, map
  package, content package, tool, asset pack, and tested bundle.
- Define compatibility-range and protocol-negotiation rules.
- Define installation receipts and a minimal rollback contract.
- Define configuration namespaces so modules do not overwrite one another's
  settings.
- Define definition/ID fingerprints and extension ranges needed by optional
  content.
- Decide which module interactions require source APIs, binary artifacts,
  generated schemas, or install-time composition.
- Publish example manifests for Renderer-only, Server-foundation, layered map,
  World Builder, vanilla target, and Spoiled Milk Content packages.

This phase creates only the minimum packaging foundation required by the five
primary modules. A general package manager, signing service, marketplace, and
advanced updater remain post-roadmap work.

Exit gate:

- package compatibility can be evaluated before filesystem or database
  mutation;
- Content can declare dependencies without foundation code importing Content;
- an unknown target produces an actionable refusal;
- manifests do not rely on Spoiled Milk branding as a technical capability.

### Phase 2: Source ownership and content-neutral seams

Goal: make the intended boundaries real inside the existing repository.

Work:

- Continue behavior-preserving ownership extraction from oversized client and
  server classes according to the structure/refactor plans.
- Establish stable client renderer-facing frame, asset, definition, input, and
  settings APIs.
- Separate server bootstrap, world, entity, protocol, persistence, definitions,
  and extension APIs from gameplay content implementations.
- Move new Spoiled Milk-owned gameplay toward one explicit content namespace
  and catalog inherited files that still require mixed ownership.
- Split vanilla target data from Spoiled Milk additions and overrides.
- Introduce content-neutral registries or extension points where custom IDs,
  definitions, scripts, map overlays, or UI entries currently require direct
  edits.
- Add dependency checks that prevent foundation packages from importing or
  packaging Spoiled Milk Content.
- Retain compatibility facades until callers and parity tests prove they can be
  removed.

Exit gate:

- the combined Spoiled Milk build remains behaviorally equivalent;
- a content-neutral build graph can be assembled, even if not yet ready for
  public release;
- dependency reports identify and block new foundation-to-content coupling;
- package and folder moves can proceed mechanically rather than redesigning
  behavior at the same time.

### Phase 3: Renderer module productization

Goal: produce the first independently versioned Renderer package.

Work:

- Complete the current high-priority ownership extractions and public renderer
  API.
- Remove implicit Spoiled Milk content assumptions from material, sprite,
  model, object, item, NPC, and UI lookup paths.
- Put target-specific identities behind versioned definition/asset adapters.
- Define Renderer package contents for source, compiled client, configuration,
  shaders, diagnostics, optional remastered assets, and software fallback.
- Keep remastered asset packs separable from renderer code.
- Add install/build support for at least one vanilla client target profile and
  the Spoiled Milk client profile.
- Add visual, input, settings-migration, performance, fallback, and clean-
  extraction smoke tests for both profiles.
- Publish an alpha Renderer artifact without claiming Android, web, or unknown
  legacy-client compatibility.

Exit gate:

- the same Renderer capability runs against supported vanilla and Spoiled Milk
  profiles without code forks;
- Renderer-only packaging contains no server or Spoiled Milk gameplay content;
- classic/software fallback and uninstallation/rollback behavior are known;
- ongoing renderer feature work can continue on the module contract.

### Phase 4: Server module productization

Goal: produce a content-neutral improved Server package.

Work:

- Complete the server foundation/content ownership audit.
- Define stable extension APIs for content registration, commands, skills,
  quests, NPC interactions, item effects, drops, map transitions, and scheduled
  events.
- Separate vanilla definitions and content plugins from engine/runtime code.
- Centralize protocol versions, definition fingerprints, migration levels, and
  client capability negotiation.
- Version persistence changes and prove vanilla rows can run without Spoiled
  Milk-only fields or behavior.
- Package authoritative server binaries, dependencies, schemas, configuration
  templates, diagnostics, migrations, and extension documentation.
- Preserve the production Ant build until an alternative proves artifact and
  runtime parity; build-system modernization must not be hidden in packaging.
- Test private launch, clean shutdown, backup/restore, server-only update,
  plugin discovery, vanilla behavior, and Spoiled Milk compatibility.

Exit gate:

- the Server package runs the selected vanilla target without Spoiled Milk
  Content;
- adding Spoiled Milk Content uses declared extension/capability boundaries;
- server upgrades preserve and validate data with rollback available;
- the hosted Spoiled Milk product still uses the guarded deployment workflow.

### Phase 5: Layered-world engine capability

Goal: replace packed-Y runtime identity with explicit signed levels while first
preserving existing gameplay exactly.

Required design work before implementation:

- finish the open discussion modules in the world-layer capacity plan;
- decide deep-underground topology, geographic correspondence rules, migration
  eligibility, transport classifications, and allocation policy;
- specify the global world-space identity and extension boundary for the later
  `world.instances` capability without implementing its dynamic lifecycle;
- publish the layered coordinate, transition, region, map-package, protocol,
  and persistence specifications.

Focused foundation progress (2026-07-18): approved Slices 1-8 now provide the
extractable `signed-layered-v1` contract, immutable Java 8 reference values,
exhaustive `legacy-packed-y-v1` laboratory codec, deterministic read-only
`spoiled-milk-repository-v1` preflight, and lossless non-relocating terrain,
placement, and transition normalization under `tools/layered-maps/`. The
complete/compact inventories prove structured-source round trips and retain
unsupported coordinates raw. The server now also owns a dormant matching
location contract and checked packed `Point` adapter. The existing mutable
packed `Area` is the first narrow consumer: it exposes a checked immutable
`WorldArea` snapshot and layered containment overload without changing legacy
storage or existing call paths. `WorldRegionKey` and read-only `RegionManager`
projections now establish logical region identity while packed nested maps stay
authoritative; this also records the two packed regions that straddle legacy
level boundaries. Java coordinate owners remain fingerprinted but unparsed.
The legacy object-telepoint category can now expose an immutable directed
`WorldObjectTransition` after its existing packed lookup and command match;
runtime callers remain unchanged, and the object-specific type avoids freezing
the later universal transport/recovery/instance taxonomy. Map identity is now
explicitly separated from offset legacy archive indices: the shared
`WorldMapSectorId` and `legacy-terrain-sector-name-v1` codecs expose signed
logical sectors while preserving exact archive names and bytes. Static object,
item, and NPC placement models now expose checked layered snapshots and
correctly inclusive roaming bounds while packed JSON/loaders and runtime
construction remain authoritative. Entities, packets, persistence, and
authoritative region/terrain storage have not adopted the contract, so these
slices do not yet satisfy the later parity steps below.

Progress recalibration (2026-07-24): the layered-world branch has now carried
the read-only/dormant foundation through extensive Region-retirement,
reconstruction, preservation, authored-state, NPC-owner, and scheduler proof.
That pre-authority research is mature enough to freeze, but packed Y is still
runtime authority. The work therefore improves the safety foundation without
completing the Phase 5 exit gate.

The implementation sequence must now return to coordinate authority:
feature-gated `WorldLocation` ownership, copied-database persistence/migration
receipts, unchanged-world session parity, level/world-space-aware Region and
entity identity, then protocol/client adoption and a synthetic `-2` test.
Incremental streaming/source retirement resumes only after those gates. Private
diagnostic schema v60 is the stopping point for the proof-only chain unless an
authority milestone exposes a new concrete failure.

Authority Milestone A was approved on 2026-07-24 as one coarse implementation
body. Its Player boundary is disabled by default, derives legacy packed
`Point` only through the named adapter, and stores additive versioned
world-space/X/Y/level fields plus exact packed receipts in the existing
transactional Player cache. This is the rollback-safe unchanged-world bridge;
it does not claim Region/entity, protocol/client, extra-level, streaming, or
Builder authority.

Authority Milestone A was owner-accepted on the private copied-data server on
2026-07-25. Initial bootstrap, exact reconnect, real surface/underground
travel, upper-floor translation and interaction, surface return, death/respawn,
and a second reconnect all preserved normal gameplay and aligned layered and
legacy coordinates. Read-only inspection found exactly the nine expected
typed persistence fields at final location `(120,648,0)` with a matching
legacy receipt; the pre-migration backup remained byte-identical and no public
data was used.

Authority Milestone B was approved on 2026-07-25. It separates logical runtime
spatial identity from legacy packed terrain storage: every entity receives a
checked `WorldLocation`, a `WorldRegionKey` index owns membership, and
visibility/proximity/cache identity become world-space and level aware behind a
second default-off private gate. Existing packed Regions remain the checked
terrain/collision compatibility backend because the 944-tile level stride does
not align with 48-tile Region rows. Protocol/client and native layered terrain
remain later gates.

Milestone B is owner-accepted as of 2026-07-25. Its implementation checkpoint
compiles 848 core and 488 plugin sources and completes a private
both-gates-enabled population of 28,732 objects, 3,775 NPCs, and 882 ground
items. Logical membership, level-aware visibility/interaction/proximity, typed
scene identity, checked packed-terrain projection, and NPC respawn
re-registration are implemented. The owner route covered surface, upper-floor,
and underground movement and interaction; two underground death/respawn
returns; logout; and exact reconnect without a reported visual, collision,
interaction, membership, projection, or reconnect fault.

Authority Milestone C was approved on 2026-07-25. It adds a third default-off
private gate for a versioned server-to-client layered scene context containing
world space, signed logical X/Y/level, sequence, tick, and the checked legacy
receipt. Existing player/NPC/object/item packet layouts remain unchanged;
scene-baseline v6 and movement-snapshot v2 bind to the context sequence, and
the client clears spatial identity caches only when world space or level
changes. Native terrain, outgoing logical action coordinates, and level `-2`
remain later gates.

The first Milestone C implementation checkpoint adds custom opcode 152,
context-bound scene-baseline v6 and movement-snapshot v2, checked client
location ownership, scope-change cache isolation, and concise server/client
context summaries. The focused Milestone C/A/B lineage passes 10 tests; the
authoritative builds compile 849 core, 488 plugin, and 259 client sources.
Private three-gate startup and owner runtime acceptance remain pending.

Planning estimates at this checkpoint are approximately `40-45%` for Phase 5
and `20-25%` for the complete Layered Maps product across Phases 5-7. No
creator-testable conversion/export workflow exists yet; Phase 6 and Phase 7
remain substantial product work.

Implementation sequence:

1. Add a named, reversible codec for all existing packed coordinates.
2. Introduce immutable level-aware points and an enclosing world-space/location
   identity, initially fixed to the global static space.
3. Make areas, region keys, transition destinations, and map identities consume
   that location contract without changing behavior.
4. Prove exhaustive round trips for terrain, placements, teleports, scripts,
   and copied player locations.
5. Make region storage, tiles, objects, NPCs, ground items, collision, pathing,
   visibility, targeting, interactions, caches, wilderness, and area checks
   level-aware.
6. Version placement and definition schemas with explicit levels.
7. Add preservation-safe player persistence fields and migration receipts on
   copied databases.
8. Normalize the maintained client and protocol while confining packed
   arithmetic to named legacy adapters.
9. Run all current maps and content without relocation until parity is proven.
10. Reject cross-level and cross-world-space visibility, collision, following,
    trading, combat, pathing, and cache leakage by invariant tests.
11. Enable additional signed levels only after the unchanged four-level world
    is stable.
12. Gate any incremental-streaming promotion on unchanged gameplay parity and
    explicit readiness/scene-baseline tests; do not hide a streaming rewrite
    inside the coordinate codec milestone.

Exit gate:

- existing content runs unchanged through the explicit layered model;
- level participates in every world identity and proximity decision;
- the global world space is explicit and no cache or entity key prevents a
  later isolated-space implementation;
- save/login/logout/reconnect/death/recovery retain the correct level;
- old packed data can be imported losslessly and is never silently
  reinterpreted;
- legacy clients and maps receive clear compatibility results;
- a copied level `-2` test world passes private client/server validation.

### Phase 6: Layered World Builder generation

Goal: retool World Builder around the layered specification without sacrificing
legacy projects.

Work:

- Keep the final packed-Y release available as the Legacy World Builder.
- Add a separately named layered project, export, receipt, and adapter schema.
- Implement read-only legacy discovery and one-way conversion into a new copied
  project.
- Discover transitions and connected terrain components, propose explicit
  level/translation changes, and surface contradictory or unowned coordinates
  for review instead of guessing.
- Let owners edit, reclassify, or explicitly acknowledge legacy misalignments,
  while recording every retained exception in deterministic human- and
  machine-readable reports.
- Produce best-effort provisional output for all discovered areas, including
  quest-heavy content, without treating automatic choices as owner acceptance.
- Show X/Y/level directly throughout navigation, inspect, copy, terrain,
  placement, transition, and validation interfaces.
- Add level creation, bounds, naming, role, visibility, and allocation metadata.
- Render occupied sectors and growth reservations from the allocation registry,
  and validate package ownership conflicts before edits or export.
- Add optional instance-template metadata and required-capability declarations
  without claiming the Builder or converter creates live instances.
- Author geographic anchors separately from collision-adjusted arrival tiles.
- Author explicit vertical, regional, transit, magical, quest, and exceptional
  transition types.
- Expand the authored bundle or extension interface so a complete world module
  can declare all terrain, placement, transition, definition, and script
  ownership it requires.
- Validate engine capabilities and definition fingerprints before export or
  import.
- Preserve isolated workspace, deterministic export, offline-target import,
  backup, receipt, rollback, and crash-recovery guarantees.
- Launch a compatible private client/server against the copied workspace so an
  owner can test the converted world before export to the actual target.
- Provide a focused dev launcher and navigation workflow; do not require the
  conversion review surface to duplicate unrelated standalone Builder tools.
- Make final export a distinct confirmed transaction with preview, unchanged-
  target verification, backup, receipt, and rollback.
- Publish layered Builder releases independently from Spoiled Milk releases.

Exit gate:

- a legacy project converts without changing the original;
- conversion reports every unsupported or ambiguous content owner;
- a layered project round-trips through save/export/import with deterministic
  results;
- level `-2` and expanded extents are editable without packed-Y arithmetic;
- Builder refuses incompatible engine, map, and definition targets before
  mutation.

### Phase 7: Layered map packages and world migration

Goal: turn the engine and editor capability into usable, organized worlds.

Work:

- Begin validation with a generated coordinate laboratory, then advance through
  exact copied vanilla and Spoiled Milk worlds, the separately gated streaming
  fixture, alignment-workbench review, disposable export/rollback, and at least
  one additional adapter fixture before real-target acceptance.
- Build a machine-readable inventory of existing areas, terrain bounds,
  placements, scripts, entrances, exits, dependencies, persistence risks, and
  growth reservations.
- Build the directed transition graph and classify every ladder, stair, portal,
  door, boat, spell, minigame, and recovery edge.
- Convert an exact copied vanilla baseline into layered notation without
  relocations and prove byte/behavior parity where applicable.
- Convert an exact copied Spoiled Milk world separately; do not allow its custom
  map changes to become part of the vanilla profile.
- Generalize conversion behind explicit target adapters so other compatible
  RuneScape Classic-derived worlds can use the same normalize, analyze, review,
  private-test, and export workflow without pretending unknown sources are
  safe.
- Use void-bounded terrain-component analysis and transition constraints to
  propose aligned moves. A downward edge between two legacy-underground
  components may place its destination on `-2`; incompatible anchors, joined
  terrain, incomplete ownership, and quest-driven ambiguity remain explicit
  findings for workbench review and final-export acknowledgement.
- Establish sector/allocation policies for surface, upper floors, shallow
  underground, deep underground, transport, quest, expansion, and experimental
  regions.
- Generate stable area IDs, occupied-sector claims, and suggested growth
  reservations during conversion; keep planning categories descriptive rather
  than restrictive.
- After engine parity, generate automatic provisional geographic alignment for
  all discovered areas in the isolated workspace and record every move in an
  explicit old-to-new manifest.
- Preserve or reclassify established quest dungeons and long-distance travel
  according to the completed map design discussion.
- Add login redirects, quest recovery, death recovery, and rollback for every
  moved area.
- Introduce connected or separated deep-underground areas only after the
  shallow-world migration and allocation policy are proven.
- Package complete worlds, world modules, and map patches as different artifact
  types.
- Where redistribution rights prevent bundling base map data, support a
  fingerprinted local conversion workflow rather than shipping that data.

Exit gate:

- vanilla and Spoiled Milk layered worlds remain distinct packages;
- every package declares all coordinate-bearing data and runtime capabilities
  it owns;
- terrain, collision, placements, transitions, quests, login, logout,
  reconnect, death, minimap, and renderer baselines pass private testing;
- rollback restores the exact prior world and copied player state;
- the legacy map remains available for users who intentionally stay on v1.

### Phase 8: Spoiled Milk Content package

Goal: make all non-vanilla Spoiled Milk material an explicit optional consumer
of the Remaster foundation.

Work:

- Complete the content inventory and remove hidden Spoiled Milk defaults from
  foundation builds.
- Package custom definitions, generated IDs, skills, quests, systems, balance,
  NPCs, items, drops, shops, guilds, minigames, scripts, interfaces, assets, and
  map additions under explicit ownership.
- Declare required Renderer, Server, Layered Maps, target-profile, definition,
  persistence, and asset capabilities.
- Separate Spoiled Milk map changes from a vanilla layered-map package.
- Version content-owned database fields and migrations.
- Add installation, upgrade, removal, and downgrade policy. Removal may require
  an explicit migration rather than deleting files when player state refers to
  custom content.
- Prove that disabling the Content package restores the supported vanilla
  profile rather than leaving partial definitions, placements, scripts, or
  settings behind.
- Retain the complete Spoiled Milk game as a first-class tested distribution.

Exit gate:

- the Content package can be identified completely by its manifest and
  installation receipt;
- no foundation artifact requires it;
- every custom map, definition, script, asset, and persisted state owner is
  accounted for;
- full Spoiled Milk behavior and progression pass their existing tests and
  private field validation on the modular foundation.

### Phase 9: Suite composition and end-user releases

Goal: make supported combinations easy to install, understand, update, and
recover.

Work:

- Build deterministic package composition from the minimal manifest system.
- Publish a tested compatibility matrix across module versions and target
  profiles.
- Produce the supported end-state distributions listed above.
- Provide previews that show exactly which files, configuration, schemas,
  definitions, maps, assets, and database migrations will change.
- Create installation receipts, backups, rollback, and repair/verify commands.
- Keep user projects, saves, Builder workspaces, exports, configuration, and
  logs outside replaceable application payloads.
- Provide release channels and update behavior appropriate to each module,
  without forcing unrelated upgrades.
- Add fresh-install, upgrade, rollback, unknown-target, interrupted-install,
  content-neutrality, and cross-module smoke tests on Linux and Windows where
  supported.
- Document source installation, binary installation, supported customization,
  contribution boundaries, and recovery.

Exit gate:

- a new user can intentionally choose Renderer-only, Server-only, Vanilla
  Remaster, layered development, World Builder, third-party content, complete
  Suite, or Spoiled Milk where supported;
- unsupported combinations fail before mutation with an actionable explanation;
- updates preserve user state and can be rolled back;
- package contents and provenance are reproducible and published with hashes;
- independent module versions do not require synchronized branding releases.

### Phase 10: Stabilization and adoption

Goal: prove the modular product over time before declaring the roadmap complete.

Work:

- Run multiple release cycles of Renderer, Server, Layered Maps, World Builder,
  and Spoiled Milk Content independently.
- Collect compatibility reports from vanilla and third-party content users.
- Resolve accidental cross-module coupling revealed by real installations.
- Freeze stable v1 contracts only after migration and rollback paths have been
  exercised.
- Decide whether any mature module now benefits from its own repository.
- Publish long-term support, deprecation, legacy-map, and security-update policy.
- Keep ongoing feature roadmaps for every primary module rather than treating
  suite v1 as technical completion.

Exit gate:

- at least one supported vanilla distribution and the complete Spoiled Milk
  distribution have survived independent upgrades;
- Layered Maps and World Builder have completed real project round trips;
- module boundaries remain enforceable in builds and packages;
- recovery procedures have been tested rather than merely documented;
- maintainers can release one module without rebuilding unrelated source unless
  the compatibility matrix requires it.

## Cross-Cutting Validation Matrix

Every phase should add tests at the narrowest useful layer and retain the
combined product checks.

| Concern | Required evidence |
| --- | --- |
| Content neutrality | Foundation package inventory contains no non-vanilla gameplay, maps, definitions, or scripts |
| Dependency direction | Automated source/build checks reject foundation imports from optional Content |
| Renderer | Builds, visual baselines, input, settings migration, fallback, diagnostics, and profile adapters |
| Server | Build authority, launch safety, protocol, tick/sync, persistence, shutdown, plugin, and vanilla behavior |
| Layered world | Codec round trips, level isolation, collision/pathing, visibility, caches, persistence, and transitions |
| Maps | Terrain/placement parity, ownership manifests, entrance graph, recovery, migration, and rollback |
| Builder | Discovery, isolation, save/export, deterministic hashes, import, rollback, updater, and crash recovery |
| Content | Capability resolution, definitions, progression, quests, maps, persistence, installation, and removal policy |
| Packaging | Fresh install, upgrade, verify, rollback, unknown target, interruption, hashes, and provenance |
| Full products | Vanilla Remaster and Spoiled Milk private field tests plus release smoke tests |

Visual acceptance remains mandatory for renderer and map changes. Automated
tests can prove ownership, invariants, and determinism but cannot decide whether
lighting, terrain, transitions, or interfaces look and feel correct.

## Versioning and Compatibility Policy

Each package has its own semantic version and declares stable capability
versions separately. A package version may change without changing every
capability it provides.

A conceptual manifest fragment is:

```json
{
  "package": "remaster.layered-world-engine",
  "version": "1.0.0",
  "providesCapabilities": {
    "world.layered-coordinates": "1.0",
    "world.transition-graph": "1.0"
  },
  "requiresCapabilities": {
    "server.world-api": ">=1 <2",
    "client.world-api": ">=1 <2"
  },
  "coordinateModel": "signed-layered-v1"
}
```

Rules:

- capability names describe contracts rather than repositories or brands;
- incompatible schema, protocol, coordinate, or persistence changes require a
  new major capability version;
- target profiles name exact supported baselines and fingerprints;
- bundles record the exact package versions they contain;
- a map or content package cannot claim compatibility from product version
  similarity alone;
- package verification occurs before launch and before import/migration;
- deprecation includes a converter, recovery path, or explicit unsupported
  notice rather than silent fallback.

## Decisions That Require Focused Follow-Up Plans

This roadmap establishes direction but intentionally leaves these decisions to
bounded plans:

- exact supported vanilla baseline and its redistribution model;
- initial public capability identifiers and manifest schema;
- module artifact shapes: source kit, patch set, binary, installer, or
  combinations;
- per-layer coordinate bounds and expanded world dimensions;
- deep-underground topology and geographic alignment policy;
- true instancing versus static isolated regions;
- protocol and persistence migration mechanics;
- definition/ID extension and conflict policy;
- content uninstall/downgrade behavior when player state uses custom systems;
- module repository splits after stable boundaries;
- Windows, Linux, Android, web, and legacy-client support matrices;
- release branding and public repository organization.

These are not holes in the roadmap. They are explicit design gates that should
not be decided accidentally inside an implementation branch.

## Post-Roadmap Foundation Expansion

The following modules are valuable, but they are deliberately scheduled after
the five primary modules and supported bundles are complete. The primary work
must not stall while building a generalized ecosystem prematurely.

### Compatibility SDK

Turn the minimal shared contracts into a documented SDK containing stable
definition schemas, protocol contracts, IDs, extension interfaces, test
fixtures, adapter templates, and migration helpers. This would make third-party
targets and content easier to support without granting access to module
internals.

### Full package and capability manager

Expand minimal manifest composition into dependency solving, side-by-side
versions, conflict reporting, install/uninstall orchestration, repair, release
channels, delta updates, provenance display, and potentially a package catalog.

### Legacy compatibility pack

Collect packed-coordinate codecs, legacy map adapters, software-renderer
support, older protocol profiles, conversion tools, and clearly labeled
compatibility-only behavior into one maintained boundary.

### Diagnostics toolkit

Unify renderer captures, server timing, movement and synchronization traces,
map validation, migration reports, package verification, crash bundles, and
machine-readable logs into tools usable across all modules.

### Independent asset packs

Distribute remastered sprites, textures, models, audio, shaders, and optional
visual themes separately from renderer code. Asset packs need stable identities,
fallback behavior, compatibility metadata, licensing/provenance, and their own
release lifecycle.

### Automated compatibility laboratory

Maintain clean fixtures and CI lanes for vanilla, Spoiled Milk, legacy maps,
layered maps, Renderer-only, Server-only, full bundles, and supported third-
party profiles. Track compatibility over time rather than relying only on the
current repository checkout.

### Ongoing primary-module roadmaps

Renderer, Server, Layered Maps, World Builder, and Content remain living
products after suite stabilization. Each keeps its own feature, performance,
usability, security, and modernization roadmap. Suite completion means they can
evolve safely and independently; it does not mean their feature work is done.

## Overall Completion Criteria

The Remaster Suite roadmap is complete when:

- the five primary modules have explicit, enforced ownership boundaries;
- Renderer and Server can be packaged without Spoiled Milk Content;
- a supported vanilla remaster and complete Spoiled Milk distribution both run
  on the modular foundation;
- signed layered coordinates replace packed-Y identity in the maintained custom
  client/server path;
- layered map packages and the layered World Builder complete safe real-world
  round trips;
- legacy maps and the legacy Builder remain available and clearly labeled;
- Spoiled Milk Content is a complete optional package rather than a hidden
  foundation dependency;
- users can select supported module combinations with compatibility checked
  before mutation;
- installs, updates, migrations, and imports are reproducible, backed up,
  receipted, verifiable, and recoverable;
- package provenance and redistribution status are known;
- independent module releases have survived multiple upgrade cycles;
- the post-roadmap foundation work has a prioritized intake list without being
  required to call the primary Suite complete.

## Related Plans and References

- [`world-layer-capacity-exploration-plan.md`](world-layer-capacity-exploration-plan.md)
- [`project-structure-refactor-plan.md`](project-structure-refactor-plan.md)
- [`code-cleanup-and-modularization-plan.md`](code-cleanup-and-modularization-plan.md)
- [`renderer-and-shader-roadmap.md`](renderer-and-shader-roadmap.md)
- [`renderer-v2-plan.md`](renderer-v2-plan.md)
- [`standalone-world-builder-plan.md`](standalone-world-builder-plan.md)
- [`terrain-expansion-plan.md`](terrain-expansion-plan.md)
- [`legacy-limits-audit.md`](legacy-limits-audit.md)
- [`../info/static-analysis.md`](../info/static-analysis.md)
- [`../info/server-build-source-of-truth.md`](../info/server-build-source-of-truth.md)
- [`../../workspaces/README.md`](../../workspaces/README.md)

## Decision Log

| Date | Decision | Status |
| --- | --- | --- |
| 2026-07-17 | Build a reusable Remaster Suite rather than treating all technical improvements as inseparable Spoiled Milk changes. | Confirmed |
| 2026-07-17 | Primary modules are Renderer, Server, Layered Maps, World Builder, and Content. | Confirmed |
| 2026-07-17 | Content means material that was not part of vanilla. | Confirmed |
| 2026-07-17 | A vanilla-compatible target profile is foundation compatibility, not optional Spoiled Milk Content. | Confirmed |
| 2026-07-17 | Layered Maps internally separates the engine capability from map packages. | Confirmed |
| 2026-07-17 | Use signed `(x,y,level)` coordinates and deliberately diverge from packed-Y legacy maps. | Confirmed in the layered-world plan |
| 2026-07-17 | Preserve the legacy Builder/map generation and provide a non-destructive one-way layered conversion path. | Confirmed |
| 2026-07-17 | Establish internal module boundaries and independent artifacts before considering additional repository splits. | Confirmed |
| 2026-07-17 | Schedule the Compatibility SDK, full package manager, legacy pack, diagnostics toolkit, asset packs, and compatibility laboratory after the primary Suite roadmap. | Confirmed |
| 2026-07-18 | Package Layered Maps for reusable world conversion, with parity normalization followed by reviewed transition/component alignment and private test-before-export. | Confirmed in the layered-world plan |
| 2026-07-18 | Initially distribute Layered Maps as developer tooling extracted into a repository root, with non-mutating extraction, explicit preflight, and reported alignment exceptions. | Confirmed in the layered-world plan |
| 2026-07-18 | Permit fully automatic provisional conversion inside an isolated Builder-derived workbench, while reserving all target mutation for a separately confirmed transactional export. | Confirmed in the layered-world plan |
| 2026-07-18 | Keep Layered Maps neutral toward creator topology, including intentional long-distance and unconventional transitions. | Confirmed in the layered-world plan |
| 2026-07-18 | Reserve an explicit world-space identity and instance-template boundary now, while deferring dynamic true-instance lifecycle to a later `world.instances` capability. | Confirmed in the layered-world plan |
| 2026-07-18 | Adopt sparse 48-tile sector allocation with signed logical X/Y, stable ownership, package collision checks, and explicit growth reservations. | Confirmed in the layered-world plan |
| 2026-07-18 | Adopt directed transition recovery from exact restore through migration/instance/area/last-safe fallbacks to world spawn. | Confirmed in the layered-world plan |
| 2026-07-18 | Keep 48-tile terrain pages but adopt incremental presentation streaming as the separately gated milestone after layered-coordinate parity. | Confirmed in the layered-world plan |
| 2026-07-18 | Gate migration through synthetic, copied-world, streaming, workbench, disposable-export, alternate-adapter, and owner-acceptance fixtures with explicit rollback. | Confirmed in the layered-world plan |
| 2026-07-18 | Complete the first Layered Maps foundation checkpoint: signed coordinate contract, checked legacy codec, and deterministic read-only repository preflight, without runtime or world mutation. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the second Layered Maps foundation checkpoint: lossless structured-source normalization, directed transition inventory, compact AI report, and raw anomaly preservation without relocation or target mutation. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the third Layered Maps foundation checkpoint: dormant server-owned layered location values and a checked global packed-`Point` bridge with exhaustive tool parity and no consumer adoption. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the fourth Layered Maps foundation checkpoint: immutable world-space/level-qualified area values and a checked projection from legacy mutable `Area`, with exhaustive containment parity and no storage replacement. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the fifth Layered Maps foundation checkpoint: immutable logical region keys and read-only manager projections, preserving packed lookup while identifying the two packed regions that must split during later storage migration. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the sixth Layered Maps foundation checkpoint: an object-specific directed layered projection from the existing telepoint map after authoritative command matching, with no XML or runtime movement change. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the seventh Layered Maps foundation checkpoint: shared logical signed map-sector identity and a checked offset legacy archive-name codec, with normalized inventory semantics corrected and terrain bytes untouched. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the eighth Layered Maps foundation checkpoint: checked layered snapshots for static object, item, and NPC placements plus inclusive roaming bounds, preserving packed inputs and runtime construction. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the ninth Layered Maps foundation checkpoint: stable lexical migration-family classification for unresolved Java coordinate owners without interpreting or rewriting content. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the tenth Layered Maps foundation checkpoint: exact file/line/argument occurrence inventory for content teleport, Point, and Area shapes without symbol inference or runtime change. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the eleventh Layered Maps foundation checkpoint: opt-in private JSONL parity observation across movement, vertical travel, teleport, and reconnect while packed Player state remains authoritative. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the twelfth Layered Maps foundation checkpoint: a checked read-only layered Player mirror synchronized from packed initial placement, movement, and session transitions, with no gameplay consumer or write-back. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the thirteenth Layered Maps foundation checkpoint: checked world-space/level-qualified Player region membership synchronized from the accepted location mirror without replacing packed RegionManager storage. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the fourteenth Layered Maps foundation checkpoint: checked legacy Player persistence snapshots on load, save, and offline-location updates while retaining the exact packed X/Y schema. | Implemented and owner-validated in the layered-world plan |
| 2026-07-24 | Approve coarse Phase 5 Authority Milestone A: private-gated authoritative Player `WorldLocation`, derived legacy Point compatibility, additive cache-backed persistence receipts, and unchanged-world session parity. | Approved and implemented in the layered-world plan |
| 2026-07-25 | Accept coarse Phase 5 Authority Milestone A on the private copied-data server. | Owner-validated through bootstrap, surface/underground and upper-floor travel, interaction, death/respawn, logout, and reconnect; layered/legacy coordinates and the exact nine-field persistence record remained aligned, the pre-migration backup remained byte-identical, and public data was untouched |
| 2026-07-25 | Approve coarse Phase 5 Authority Milestone B: universal Entity `WorldLocation`, logical spatial membership, level-aware visibility/proximity/cache identity, and checked packed terrain/collision projection. | Approved and implemented in the layered-world plan; default-off private gate, unchanged archives/protocol/client, and one meaningful owner route |
| 2026-07-25 | Accept coarse Phase 5 Authority Milestone B on the private both-gates-enabled server. | Owner-validated through surface, upper-floor, and underground movement and interaction; two underground death/respawn returns; logout; and exact reconnect. No visual, collision, interaction, membership, projection, or reconnect fault appeared, and the public server was untouched |
| 2026-07-25 | Approve coarse Phase 5 Authority Milestone C: versioned layered scene context, client WorldLocation authority, context-bound static/movement snapshots, and scope-change cache isolation. | Approved for implementation in the layered-world plan; default-off matched-custom-client gate, unchanged legacy packet layouts, and no level `-2` or archive claim |
| 2026-07-25 | Accept coarse Phase 5 Authority Milestone C behind its private gate. | Custom opcode 152, checked client scope identity, context-bound scene-baseline v6 and movement-snapshot v2, and scope-change cache isolation are owner-validated across surface, upper, underground, same-scope movement, real ladder transitions, logout, and reconnect; the focused 10-test C/A/B lineage and authoritative 849/488 server plus 259-source client builds pass |
| 2026-07-25 | Approve coarse Phase 5 Authority Milestone D: one explicit synthetic level `-2` compatibility fixture. | Approved for implementation in the layered-world plan; fourth default-off gate, bounded named projection onto a checked object-free terrain template, protocol-v2 projection identity, deep persistence/recovery, generated private NPC/item route, and no production archive or placement mutation |
| 2026-07-18 | Complete the fifteenth Layered Maps foundation checkpoint: immutable level-qualified visibility windows and read-only manager projection while packed lookup, caches, and client behavior remain authoritative. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the sixteenth Layered Maps foundation checkpoint: a checked Player visibility-window shadow and versioned private diagnostic evidence while packed lookup, caches, and client behavior remain authoritative. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the seventeenth Layered Maps foundation checkpoint: deterministic, allocation-budgeted logical interest-window deltas while packed lookup, caches, packets, and client behavior remain authoritative. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the eighteenth Layered Maps foundation checkpoint: bounded entered/retained/exited logical-interest evidence in versioned private diagnostics while current server and client authorities remain unchanged. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the nineteenth Layered Maps foundation checkpoint: exact logical-key coverage for legacy packed region cells, including same-level misalignment and padded tails, without storage adoption. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twentieth Layered Maps foundation checkpoint: compare packed visibility candidate coverage with the signed logical window, reporting missing, extra, and unsupported coverage without storage adoption. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-first Layered Maps foundation checkpoint: emit bounded packed/logical coverage comparisons only through the versioned private diagnostic stream. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-second Layered Maps foundation checkpoint: partition packed region cells into exact contiguous signed-level tile fragments without storage adoption. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-third Layered Maps foundation checkpoint: invert packed fragments into logical-region legacy assembly plans without copying or rekeying runtime tiles. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-fourth Layered Maps foundation checkpoint: resolve logical region-local tiles to exact packed source addresses without reading runtime tile arrays. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-fifth Layered Maps foundation checkpoint: copy existing packed TileValues into detached logical-region snapshots while packed collision and storage remain authoritative. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-sixth Layered Maps foundation checkpoint: emit one bounded logical-region tile-snapshot fingerprint through opt-in private diagnostics while packed tile authority remains unchanged. | Implemented and owner-validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-seventh Layered Maps foundation checkpoint: define immutable full-fidelity logical tile state while retaining packed TileValue authority and a detached compatibility copy. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-eighth Layered Maps foundation checkpoint: compare one direct packed tile state with its assembled logical snapshot state without changing gameplay authority. | Implemented and validated in the layered-world plan |
| 2026-07-18 | Complete the twenty-ninth Layered Maps foundation checkpoint: expose bounded current-tile packed/logical parity through opt-in private v6 diagnostics. | Implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Complete the thirtieth Layered Maps foundation checkpoint: compare one checked 3×3 logical tile neighborhood with current packed sources without gameplay adoption. | Implemented and validated in the layered-world plan |
| 2026-07-19 | Add bounded 3×3 neighborhood counts to opt-in private v7 parity diagnostics without tile payloads or gameplay adoption. | Slice 31 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Compare one adjacent logical and packed tile-mask decision while retaining PathValidation and movement authority. | Slice 32 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit all eight adjacent tile-mask comparisons through bounded opt-in private v8 diagnostics. | Slice 33 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Compose adjacent layered/packed tile-mask decisions across an explicit bounded route while legacy routing and movement remain authoritative. | Slice 34 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit one bounded recent ordinary-walking segment through opt-in private v9 diagnostics while movement remains authoritative. | Slice 35 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Maintain a checked, versioned logical view of packed Region lifecycle without caching mutable tile/collision state or changing packed authority. | Slice 36 implemented and validated in the layered-world plan |
| 2026-07-19 | Compare bounded logical interest changes with checked Region residency while keeping load/release candidates dormant and preserving eager packed authority. | Slice 37 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded Region residency evidence through opt-in private v10 diagnostics while keeping all load/release candidates non-authoritative. | Slice 38 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Define monotonic process-local logical-interest owners and shared-reference counts without runtime or residency adoption. | Slice 39 implemented and validated in the layered-world plan |
| 2026-07-19 | Maintain one checked opaque logical-interest owner per Player session while preserving packed eager Region authority. | Slice 40 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded Player interest-owner and global/shared reference transitions through opt-in private v11 diagnostics without adopting Region loading or eviction. | Slice 41 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Project global logical-interest releases through a conservative tick-based retirement cooldown without adopting Region loading or eviction. | Slice 42 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded Region retirement cooldown and expiry evidence through opt-in private v12 diagnostics without adopting Region loading or eviction. | Slice 43 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Require a two-real-client private-runtime gate for shared acquisition, partial release, final global release, cooldown, expiry, and reacquisition before considering a Region retirement arbiter. | Slice 44 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Atomically recheck bounded Region retirement candidates through a pure source-level decision arbiter without loading, unregistering, unloading, or evicting packed Regions. | Slice 45 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded accepted/refused Region retirement-decision evidence through additive private v13 diagnostics without granting observer or arbiter lifecycle authority. | Slice 46 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Aggregate same-snapshot logical retirement decisions into conservative packed-source readiness while blocking incomplete cross-level coverage and partial legacy-domain edges. | Slice 47 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded packed-source readiness from the existing atomic retirement-decision batch through additive private v14 diagnostics without lifecycle authority. | Slice 48 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Assess packed-source contents and quiescence read-only while explicitly blocking lifecycle readiness until a per-Region reload path exists. | Slice 49 implemented and validated in the layered-world plan |
| 2026-07-19 | Emit bounded packed-source contents and blocker evidence through additive private v15 diagnostics without lifecycle authority. | Slice 50 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Inventory Region construction/teardown ownership and freeze exact count-only authored construction origins per packed source without lifecycle adoption. | Slice 51 implemented and validated in the layered-world plan |
| 2026-07-19 | Project immutable authored construction-origin counts onto exact safety sources through additive private v16 diagnostics without claiming current provenance or reconstructibility. | Slice 52 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Preserve full detached authored construction inputs and stable per-source placement identity before designing packed-source reconstruction. | Selected for Slice 53 by the layered-world owner evidence; lifecycle adoption remains gated |
| 2026-07-19 | Freeze every successfully constructed authored placement as detached primitive inputs with duplicate-safe per-source identity and an independent count-equivalence gate. | Slice 53 implemented and runtime-validated in the layered-world plan |
| 2026-07-19 | Measure conservative cross-source reach for every authored object footprint, NPC roaming bound, and anchor-only item without turning reach into lifecycle retention. | Slice 54 implemented and runtime-validated in the layered-world plan |
| 2026-07-19 | Formalize authored provenance as a generation-fenced source/ordinal/family identity while keeping runtime definitions and entities unchanged. | Slice 55 implemented and automated-validated in the layered-world plan |
| 2026-07-19 | Attach conflict-refusing authored identity metadata to accepted definitions/entities and preserve it through current respawn and explicit replacement paths without a global registry. | Slice 56 implemented and runtime-validated in the layered-world plan |
| 2026-07-19 | Compare exact safety-source manifest identities with a bounded count-only private runtime census while retaining no entity handle or lifecycle authority. | Slice 57 implemented and owner-validated in the layered-world plan |
| 2026-07-19 | Add bounded exact-identity details for absent, duplicate, replacement, stale-generation, and unrecognized authored-provenance anomalies without creating a registry. | Slice 58 implemented and owner-validated in the layered-world plan; all four prior absences are deterministic population-time supersessions |
| 2026-07-20 | Preserve complete authored replay history while projecting normal population-time collision supersessions into the final-live provenance expectation set. | Slice 59 implemented and owner-validated in the layered-world plan; the command trace path forwards the completed outcome, all 5,797 final-live expectations match, and lifecycle authority remains absent |
| 2026-07-20 | Derive ordered per-source reconstruction inputs only from final-live authored identities and aligned conservative reach without adding a runtime consumer. | Slice 60 implemented and private-runtime validated in the layered-world plan; all 33,515 final-live inputs retain aligned reach and lifecycle authority remains absent |
| 2026-07-20 | Project inert final-live recipe counts and unique dependency requirements onto an exact bounded safety-source selection without interpreting closure as a load request. | Slice 61 implemented and automated-validated in the layered-world plan; closure remains evidence only and lifecycle authority remains absent |
