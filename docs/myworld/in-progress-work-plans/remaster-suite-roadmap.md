# RSC Remastered Product Roadmap

Status: owner-approved product roadmap; implementation is not yet authorized by
this document alone

Started: 2026-07-17

Public product direction refined: 2026-07-25

## Purpose

Spoiled Milk has produced substantial client, renderer, server, map, editor,
diagnostic, and workflow improvements that are useful beyond Spoiled Milk's
custom game. The public end product is now **RSC Remastered**: one definitive,
end-user-friendly remaster of the selected vanilla RuneScape Classic baseline,
with the improved renderer, server, explicit layered world, editor integration,
modder asset workflow, and universal launcher delivered as one coherent
project.

The internal Suite architecture remains useful for ownership, testing, and
versioned capability boundaries. It is no longer the primary public product or
a requirement that every capability become a separately marketed download.
RSC Remastered must support at least these outcomes:

- a complete remastered game whose definitive content baseline is vanilla RSC;
- a universal launcher that owns isolated installations and matching
  client/server profiles;
- named import adapters for supported external distributions such as Cabbage,
  with fingerprinted conversion into a separate launcher profile;
- a drag-and-drop `content/` workflow for modder assets such as wall and floor
  textures, with automatic editor discovery and stable identities;
- layered maps authored and expanded without packed-Y or a fixed `-2` depth
  ceiling; and
- optional third-party or Spoiled Milk content that does not redefine the
  vanilla RSC Remastered baseline.

This is a start-to-finish product and architecture roadmap. It defines ordering,
boundaries, dependencies, and completion gates. Detailed implementation remains
owned by focused module plans and short-lived topic branches.

## North-Star Product

The final public product is an integrated RSC Remastered installation. Its
internals remain versioned and modular so the launcher, editor, imports, and
content packs can reason about compatibility without turning the repository
back into an opaque monolith.

```text
                         RSC Remastered
                               |
                     Universal Launcher
                               |
            +------------------+------------------+
            |                                     |
     definitive vanilla                   imported profiles
     remastered profile               Cabbage / supported forks
            |
   Renderer + Server + Layered World + Builder integration
            |
      version-local content/ packs and modder assets
```

The launcher may expose tools and optional profiles, but the default download
must give a new player a clear **Install/Play RSC Remastered** path. A user
should not need to understand internal modules or install Spoiled Milk content
to receive the technical remaster.

## Terminology

- **RSC Remastered:** the public, definitive vanilla-content remaster and its
  integrated launcher/tooling project.
- **Remaster internals:** the module and capability boundaries retained inside
  RSC Remastered for ownership, compatibility, testing, and optional reuse.
- **Universal Launcher:** the profile manager that installs, imports,
  validates, updates, and launches matched game distributions in isolation.
- **Installation profile:** one self-contained launcher entry with exact
  client, server, map, definitions, content, settings, save/database policy,
  capabilities, and fingerprints.
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
- **Vanilla remaster baseline:** authentic vanilla terrain, placements,
  definitions, and gameplay content running on remastered technical
  foundations; technical improvements do not make it non-vanilla content.

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
15. **Profiles are isolated.** Importing Cabbage or another supported
    distribution creates a new launcher-owned profile. It never overwrites the
    RSC Remastered profile, another import, or an existing save/database.
16. **Drag-and-drop does not mean unstable IDs.** Content directories may be
    easy to populate, but discovery order, filename sorting, or directory order
    must never silently renumber an asset already used by a map.
17. **Remote catalogs are explicit and safe.** A launcher catalog may advertise
    community distributions only with provenance, permission, immutable
    version metadata, hashes, and an HTTPS source. Import never grants a
    downloaded archive permission to execute arbitrary installer code.

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
underground `-1`, deep underground `-2`, then continue to `-3`, `-4`, or any
later configured signed level. `-2` is the first validation target and a useful
semantic category, never an engine or format minimum. Adding a level consists
of adding level metadata, terrain sectors, placements, and transitions within
configured safety limits; it must not require a new coordinate codec or engine
constant. Ordinary vertical anchors preserve X/Y by default. Local walkable
arrival offsets are explicit, while long-distance transport, magical, quest,
and instance-like edges are classified separately.

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

The immediate Layered Maps deliverable is now the concrete RSC Remastered
vanilla conversion, not a polished general-purpose end-user map converter.
Conversion remains deterministic build tooling because the definitive map must
be reproducible: it first emits a parity-preserving normalized vanilla project,
then analyzes transition graphs and terrain components to perform reviewed
geographic alignment and deeper-level placement. Source artifacts stay
unchanged, every move receives a receipt, ambiguous ownership requires review,
and reverse export to packed-Y is not promised. General source adaptation
belongs to named launcher import profiles later; it does not block completing
the vanilla map.

The layered schema and converter are topology-neutral. Long-distance ladders,
same-level transport, unconventional transition objects, and disconnected
networks remain valid creator choices. Classification and reports describe
their behavior without treating geographic convention as a content rule;
validation enforces representability and integrity rather than aesthetics.

The conversion workspace remains isolated and reviewable, but it is an
RSC Remastered development facility rather than a promised drop-in converter
product. A focused Builder-derived workbench owns staged inspection, corrective
edits, navigation, reports, and a private dev client/server launcher.
Provisional conversion may be automatic because source and definitive target
remain separate. Promotion into the RSC Remastered map requires an explicit
reviewed transaction with fingerprints, diff, backup, receipt, and rollback.

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

## Universal Launcher and Modder Content

The Universal Launcher is the public shell around the remastered internals. It
must make the default path simple while preserving exact profile boundaries:

- install, update, repair, and launch the definitive RSC Remastered profile;
- create an isolated profile by selecting a supported distribution archive,
  such as a fingerprinted Cabbage ZIP;
- identify the source with a named adapter before conversion and refuse an
  unknown or ambiguous layout without mutating anything;
- stage extraction outside every installed profile, reject archive traversal
  and unsafe links, inventory files, and show the conversion/compatibility
  report before committing;
- install matching client, server, map, definitions, capabilities, and
  profile-local settings as one atomic profile version;
- preserve profile-local save/database policy and never copy credentials or
  player data unless a separately described migration is explicitly selected;
- retain receipts, source hashes, installed hashes, backups, repair data, and
  rollback for every profile version; and
- optionally consume a curated community catalog whose entries carry owner,
  license/provenance, version, source URL, hashes, adapter, capabilities, and
  support status. Direct hosting or mirroring requires explicit redistribution
  permission.

Each installation profile owns an automatically scanned `content/` root. The
first supported drag-and-drop asset surface is:

```text
content/
  walls/
  floors/
  packs/
    <namespace>/
      manifest.json
      walls/
      floors/
```

Files placed directly in `walls/` or `floors/` belong to a profile-local
`local` namespace for the simplest modder workflow. Pack directories provide
portable namespaced assets and declared dependencies. The scanner must:

- accept documented image formats and validate dimensions, color model, size,
  and resource budgets before registration;
- create or retain a stable content index/sidecar identity on first successful
  discovery, never deriving persistent map IDs from directory iteration order;
- detect duplicate namespaces, identity collisions, removed files, changed
  hashes, and incompatible renderer/map capabilities with actionable errors;
- expose newly registered wall/floor materials to World Builder without a
  source edit or client rebuild;
- make missing assets visible in the editor and runtime through an explicit
  placeholder/report rather than silently substituting another numeric ID;
- support refresh while editing where safe, while requiring a clean profile
  restart for runtime state that cannot be hot-reloaded truthfully; and
- keep executable scripts, plugins, definitions, and gameplay logic outside
  the implicit image-only drag-and-drop trust boundary.

Later asset families may add sprites, models, roofs, objects, audio, or UI
materials through versioned registries. They must reuse the same namespace,
identity, budget, compatibility, and editor-discovery rules rather than
expanding the initial folder scan informally.

## Required Dependency Direction

| Consumer | May depend on | Must not require |
| --- | --- | --- |
| Renderer | client API, target definitions/assets, shared contracts | Spoiled Milk gameplay content |
| Server | shared contracts, target profile, optional installed content APIs | Renderer implementation, Spoiled Milk content |
| Layered-world engine | client/server world APIs, protocol and persistence contracts | a particular map package or Spoiled Milk content |
| Layered map package | layered-world capability, declared definitions, optional renderer assets | unrelated content or an undeclared source tree |
| World Builder | map schemas, adapters, capability metadata, definitions | a running target or hidden Spoiled Milk release coupling |
| Content | any explicitly declared foundation capability and target profile | undeclared patches or accidental repository layout |
| Universal Launcher | manifests, target adapters, profile transactions, supported catalogs | unknown-layout patching, arbitrary installer execution, cross-profile mutation |
| Launcher profile | a tested version matrix of selected packages | unsupported arbitrary combinations |

Cross-module communication should use narrow value types, schemas, service
interfaces, or generated contracts. Direct imports into another module's
internal implementation are migration debt and should be tracked as such.

## Supported End-State Distributions

The roadmap is complete only when the launcher and packaging system can
truthfully produce and test these shapes:

1. **RSC Remastered default profile**
   - Installs and launches the definitive vanilla-content remaster without
     requiring the user to understand internal packages.
   - Includes the matched renderer, server, layered vanilla world, tools,
     profile manifest, and supported runtime.
2. **Imported distribution profile**
   - Imports a fingerprinted supported archive into a separate installation.
   - Records its source adapter, conversion report, exact installed versions,
     limitations, repair data, and rollback receipt.
3. **World Builder and content-author profile**
   - Opens the selected installation's layered world and auto-discovered
     `content/` assets without editing another profile.
   - Launches a private matched test environment for review.
4. **Renderer-only development package**
   - Installs or builds the renderer against an explicitly supported client
     target profile.
   - Includes required shared client contracts but no server or custom content.
5. **Server-foundation development package**
   - Runs an explicitly supported vanilla content profile.
   - Contains no Spoiled Milk quests, balance, custom map additions, or custom
     definitions unless the user selects them.
6. **Layered-world development bundle**
   - Combines layered client/server capability, a layered map package, and a
     compatible Builder without requiring Spoiled Milk Content.
7. **World Builder standalone package**
   - Runs independently and selects an explicit legacy or layered adapter.
8. **Third-party content bundle**
   - Adds a separately authored map or content package whose declared
     capabilities and definitions are satisfied.
9. **Spoiled Milk optional profile**
   - Combines the tested foundation versions with the optional Spoiled Milk
     Content package.

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
- The repository contains a usable Preservation/vanilla revision-64 baseline:
  the Preservation configuration and copied SQLite seed, the server's
  `maps64`/`land64` JAG and MEM pairs, matching server/client authentic ORSC
  archive, and the base boundary, scenery, NPC, and ground-item placement
  files. This is a baseline set rather than one interchangeable map file and
  its 12-file map-source subset is now frozen by the layered tool at source-set
  fingerprint
  `ffbf27806fbe8fb3287b1d9543b355deebf0e22a329aec13885a16c7399fb86a`.
  Definition and redistribution inventory still must join it before product
  promotion.
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
tests, and handoffs. Do not create a single long-running "RSC Remastered"
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

- Freeze the local Preservation revision-64 baseline as the first definitive
  vanilla source candidate. Its terrain set includes all four server archives
  (`maps64.jag`, `maps64.mem`, `land64.jag`, and `land64.mem`) plus the
  authentic ORSC consumed by the maintained client/tooling; its placements are
  the base `BoundaryLocs`, `SceneryLocs`, `NpcLocs`, and `GroundItems` files.
- Generate one checked-in machine-readable provenance manifest containing
  paths, sizes, hashes, archive member inventories, placement counts,
  configuration selectors, definition fingerprints, and redistribution
  status. The initial map-source manifest is implemented; extend it rather
  than replacing or silently broadening its fingerprint. Do not treat matching
  terrain alone as a complete vanilla baseline.
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

Current Phase 5 checkpoint: Authority Milestones A through D are accepted, and
Milestone E's package-wide native runtime through checkpoint 11 is
owner-accepted. A fifth
default-off private gate loads the versioned layered package; server terrain
lookup and scene-context protocol v3 identify native page `(9,12)`, declared
presentation chunk `24`, and the checked manifest; the client builds that room
without borrowing surface terrain. Entry, movement, item/NPC interaction,
depth logout/reconnect, exit, and surface death recovery pass. This does not
yet close Phase 5 or Milestone E. Full-fidelity non-uniform RLE storage,
detached server decode, and matched protocol-v4 radius-one delivery of explicit
24-tile terrain/void chunks now pass automated validation. Same-package chunk
window shifts no longer trigger full world-scope cache resets. The owner route
also passes across both presentation boundaries with correct `4/9 -> 6/9 ->
4/9` readiness, stable visuals/interactions, exact depth reconnect, and exit.
Hash-addressed package-owned NPC/item placements now pass strict decode,
world-load, layered item-respawn registry, and matched private-startup
validation. Their owner route also passes normal Man roam/dialogue, repeated
five-coin collection across package-owned respawn, exact depth reconnect,
duplicate-free population, and exit. Static package-owned scenery/boundaries
now pass the v2 package contract, independent decode, level-qualified
spatial/collision registration, matched private startup, and automated
authority-lineage validation. Their focused owner route also passes normal
visuals/examine actions, Table/Fence collision and navigation, unchanged
NPC/item interaction, exact depth reconnect, duplicate-free visibility, and
exit. Package `0.5.0` now adds generation-fenced dynamic object identity,
level-qualified replacement/removal transactions, exact collision deltas, and
an ordinary Door/Doorframe fixture. Automated validation and the focused owner
route pass repeated open/close collision transitions, crossing, nearby
NPC/item interaction, exact depth reconnect, open-state continuity across a
second reconnect, post-reconnect close, and exit. Package `0.6.0` now adds an
ordinary Tree/Treestump route through the real Woodcutting replacement and
delayed-spawn scheduler. Automated validation and focused owner acceptance pass
the natural harvest, stump, restoration, exact deep reconnect, stable live
counts, and collision continuity. Package `0.7.0` removes the bounded
compatibility receipt from native selection, movement, persistence, collision,
protocol scope, and client terrain application; accepted routes cover both
declared pages outside the former synthetic room.

Runtime checkpoint 12 now composes non-overlapping packages under one
fail-closed catalog, preflights explicit package changes before Player state
mutation, resolves protocol-v4 identity and package-scoped dynamic objects
through exact destination ownership, and adds an isolated `-4` transition
package. The 35-test A-through-E lineage and authoritative server/client builds
pass. Focused owner acceptance also passes both package changes, normal
walking and package-owned item interaction, exact level `-4` logout/reconnect,
return to the original `-2` package, and legacy surface exit without stale
scene state or a route-time exception. Retirement of packed Region
terrain/collision backing from native scopes is now the remaining Milestone E
engine boundary.

Runtime checkpoint 13 is the selected Region-free native runtime cut. Native
package terrain, collision, scenery, and boundaries were already independent
of packed Regions; this checkpoint removes the remaining packed-Region entity
carrier from package-owned locations. Players, NPCs, and ground items use only
the exact layered spatial index while inside native terrain, core interaction
and blocking lookups no longer acquire a Region as a facade, owner-attributed
runtime items retain the owner's signed domain, and private diagnostics enforce
and expose the carrier choice. Legacy archive locations and the synthetic-deep
rollback route keep their existing packed membership. Automated validation is
complete: 35 focused Authority A-through-E tests, the 865-core/488-plugin
server build, the 262-source client build, and matched two-package private
startup pass. The complete private-owner behavioral route now passes. Its
only finding was clipped single-line diagnostic presentation; the bounded-line
correction is built and awaits one visual invocation before Milestone E is
closed.

The first checkpoint-13 owner entry found and precisely bounded a client
transition defect: legacy-to-native entry at unchanged `(450,600,P0)` accepted
the correct native context but the same-region fast path suppressed its
requested hard terrain rebuild, leaving the legacy water floor until
reconnect. Hard layered scope changes now bypass that fast path so the accepted
native snapshot rebuilds immediately. The expanded 36-test Authority
A-through-E lineage, client region-load performance guard, and 262-source
client build pass. The 2026-07-26 focused owner retest also passes: a legacy
exit followed by immediate same-X/Y native entry advanced the client from
legacy context sequence `2` to package context sequence `3`, rendered the
native floor, and allowed movement without reconnect while diagnostics
retained `packedRegion=detached` and `spatialCarrier=layered-index`. The
scene-rebuild correction is accepted; the rest of checkpoint 13's
interaction, cross-package, reconnect, and legacy-reattachment route remains.

The following 2026-07-26 owner phase accepts checkpoint-13 native interaction
and ordinary runtime-item behavior. Man dialogue, authored coin take/respawn,
Door operation, Tree harvest/restoration, and an owner-attributed Logs
drop/recovery all behaved normally on level `-2`. Final diagnostics remained
on package `0.7.0`, readiness `4/9`, live counts `1n/1i/2s/2b`, and
`packedRegion=detached`. Cross-package switching, native reconnect, reverse
switching, and legacy packed-Region reattachment remain to close the
checkpoint.

The next owner phase accepts the forward cross-package and exact native
reconnect boundaries. The runtime committed
`native-loader-lab -> native-transition-lab`, selected transition package
`0.1.0` at level `-4`, and served its three-coin placement normally. Movement
to `(456,600,L-4)` was saved by normal logout and restored at that exact
signed location with a fresh client context, the correct transition terrain,
`spatialCarrier=layered-index`, and `packedRegion=detached`. No abnormality
was observed. Only reverse switching and legacy packed-Region reattachment
remain for checkpoint-13 owner acceptance.

The final owner phase accepts both remaining boundaries. The reverse switch
selected the original level `-2` package and retained its detached carrier;
explicit exit then selected `legacy-packed-y-v1` at level `0`. At a walkable
surface location, ordinary movement and Goblin combat completed normally, and
the final technical response reported `spatialCarrier=packed-region`.
Checkpoint 13 is therefore behaviorally owner-accepted.

The carrier response was complete in the client technical capture but too
long for the in-game chat line, hiding the field from visual inspection.
`::layerloc` now emits its unchanged AI-readable fields as bounded logical
lines. Focused diagnostic tests and the authoritative 865-core/488-plugin
server build pass; one private visual invocation remains before this
presentation correction and Milestone E are marked closed.

### Phase 6: Layered World Builder generation

Goal: make the layered RSC Remastered world and modder content directly
authorable while retaining a clearly separated legacy editor.

Work:

- Keep the final packed-Y release available as the Legacy World Builder.
- Add a separately named RSC Remastered layered project, export, receipt, and
  adapter schema.
- Import the frozen vanilla baseline into a new copied project through
  deterministic internal conversion tooling. A polished general-purpose
  converter is not a Phase 6 release dependency.
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
  The UI and schema must accept additional configured signed levels such as
  `-3` without an editor rebuild or a fixed level enumeration.
- Scan the selected profile's namespaced `content/walls`,
  `content/floors`, and pack manifests into the material palettes through
  stable registered identities; surface validation and missing-asset errors in
  the project rather than silently renumbering materials.
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

- the frozen vanilla source converts without changing the original;
- conversion reports every unsupported or ambiguous content owner;
- a layered project round-trips through save/export/import with deterministic
  results;
- level `-2`, later configured signed levels, and expanded extents are editable
  without packed-Y arithmetic;
- valid profile-local wall and floor assets appear in Builder palettes without
  a source edit, while identity conflicts refuse deterministically;
- Builder refuses incompatible engine, map, and definition targets before
  mutation.

### Phase 7: Layered map packages and world migration

Goal: turn the engine and editor capability into usable, organized worlds.

Work:

- Begin validation with a generated coordinate laboratory, then advance through
  exact copied vanilla and Spoiled Milk worlds, the separately gated streaming
  fixture, alignment-workbench review, and disposable promotion/rollback
  before accepting the definitive remastered target.
- Build a machine-readable inventory of existing areas, terrain bounds,
  placements, scripts, entrances, exits, dependencies, persistence risks, and
  growth reservations.
- Build the directed transition graph and classify every ladder, stair, portal,
  door, boat, spell, minigame, and recovery edge.
- Convert an exact copied vanilla baseline into layered notation without
  relocations and prove byte/behavior parity where applicable.
- Convert an exact copied Spoiled Milk world separately; do not allow its custom
  map changes to become part of the vanilla profile.
- Retain converter seams and receipts internally, but defer additional
  distribution adapters to the Universal Launcher's named import profiles.
  Completing a generic standalone converter must not delay the definitive
  vanilla map.
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
- Prove that adding a declared level below `-2` requires only package/editor
  data and ordinary allocation, not changes to coordinate, loader, protocol,
  collision, renderer, or persistence constants.
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

### Phase 9: Universal Launcher and end-user releases

Goal: make RSC Remastered immediately playable and supported external
distributions safe to import, isolate, launch, update, and recover.

Work:

- Build the Universal Launcher around deterministic profile composition from
  the minimal manifest system.
- Publish a tested compatibility matrix across module versions and target
  profiles.
- Make the definitive RSC Remastered profile the simple default install/play
  path, with Builder/content tools discoverable without obstructing players.
- Implement named, fingerprinted import adapters for supported external
  archives, beginning only after their source layouts and redistribution
  policies are known.
- Optionally publish a curated discovery catalog; catalog inclusion, direct
  hosting, and redistribution are separate permissions and every download is
  hash-verified before staging.
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

- a new user can install and play RSC Remastered through one clear default
  flow;
- a modder can open its Builder/content tools and add validated wall/floor
  assets without altering another profile;
- a supported external archive imports into a separate named profile with an
  exact adapter report and no cross-profile save or file mutation;
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
usability, security, and modernization roadmap. RSC Remastered product
stabilization means they can evolve safely and independently; it does not mean
their feature work is done.

## Overall Completion Criteria

The RSC Remastered product roadmap is complete when:

- the five primary modules have explicit, enforced ownership boundaries;
- Renderer and Server can be packaged without Spoiled Milk Content;
- the definitive vanilla remaster and complete optional Spoiled Milk
  distribution both run on the modular foundation;
- signed layered coordinates replace packed-Y identity in the maintained custom
  client/server path;
- layered map packages and the layered World Builder complete safe real-world
  round trips;
- legacy maps and the legacy Builder remain available and clearly labeled;
- Spoiled Milk Content is a complete optional package rather than a hidden
  foundation dependency;
- the Universal Launcher can install/repair/update the default profile and
  import supported external profiles with compatibility checked before
  mutation;
- profile-local drag-and-drop wall/floor content has stable identities and
  automatic World Builder discovery;
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
| 2026-07-25 | Present the public project as **RSC Remastered**, a definitive vanilla-content remaster with one coherent launcher/tooling experience; retain modular capability boundaries as internal architecture. | Confirmed; refines the earlier Suite-first packaging direction |
| 2026-07-25 | Use the repository's Preservation revision-64 terrain, base placements, configuration, and definition set as the first definitive vanilla source candidate, subject to one complete provenance/fingerprint manifest. | Confirmed; local source set found, manifest remains a deliverable |
| 2026-07-25 | Make the Universal Launcher own isolated, matched installation profiles and named fingerprinted import adapters; importing Cabbage or another supported distribution must never patch the default profile in place. | Confirmed |
| 2026-07-25 | Begin modder drag-and-drop support with profile-local `content/walls` and `content/floors`, namespaced packs, stable registered identities, validation, and automatic World Builder discovery. | Confirmed architecture; implementation remains phased |
| 2026-07-25 | Complete the concrete vanilla layered map rather than blocking on a polished general standalone map converter. Keep deterministic normalization, reports, receipts, review, and rollback as internal build facilities; place later external conversions behind named launcher adapters. | Confirmed; supersedes the converter-as-primary-end-user-product direction |
| 2026-07-25 | Treat `-2` as the first deep validation level, not a format/runtime cap. Additional signed levels must be declared in package/editor data and load without new coordinate, protocol, renderer, collision, or persistence constants. | Confirmed |
| 2026-07-25 | Select native layered runtime ownership by exact loaded-package coverage rather than by the bounded synthetic `-2` fixture. Keep synthetic Milestone D as an independent rollback route while the temporary packed `Point` carrier is retired in later slices. | Owner-accepted after two focused corrections; page-crossing movement, exact deep reconnect, package terrain/placement interaction, and prior surface exit pass without the synthetic gate |
| 2026-07-25 | Apply all available native readiness-window terrain on the client rather than clipping it to the old synthetic fixture rectangle. | Owner-accepted after the first native-only route exposed and corrected void/water beyond the old bounds and a reconnect-time fixture-coverage exception |
| 2026-07-25 | Remove the runtime package's obsolete maximum-value codec band now that exact package coverage makes every declared tile reachable. | Package `0.7.0` replaces undefined client overlay/roof/wall IDs with a valid blocking non-default band, guards all runtime fixture runs against client definition ceilings, and passes owner floor/placement/interaction/exact-reconnect acceptance |
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
| 2026-07-25 | Implement coarse Phase 5 Authority Milestone D behind its fourth private gate. | The bounded named projection now carries authoritative global level `-2` through server location, logical membership, checked plane-0 terrain/collision reuse, nine-field persistence, and client scene-context v2; `::deepfixture` provides a clear-checked generated owner route. The 14-test D/C/B/A lineage, prerequisite-refusal startup, 850/488 server build, and 259-source client build pass with no production terrain or placement mutation; private owner acceptance remains pending |
| 2026-07-25 | Correct the rejected first Milestone D owner route. | The borrowed plane-0 rectangle was water, preventing walking and Man interaction, while a legacy-only Player-data snapshot rejected the authoritative level `-2` identity and rolled back deep saves. The layered plan now specifies a runtime-only 21-by-21 flat overlay-0 room, no borrowed upper-plane geometry, logical-room collision/pathing, and named-projection-aware snapshot capture. The 14-test D/C/B/A lineage, two Slice-14 persistence tests, and authoritative 850/488/259 builds pass; no archive, placement, surface, live-data, or public-server mutation occurred, and corrected owner acceptance remains pending |
| 2026-07-25 | Refine the Milestone D room after its first corrected owner retry. | Deep logout/reconnect and explicit surface exit now pass, proving the persistence correction, but the terrain remained water because the client room patch used logical coordinates against an archive loader that had already added client world offsets. Carry the active X/Z offsets into the runtime-only room selection and revise its cache identity when offsets change; owner walking/interaction acceptance remains pending |
| 2026-07-25 | Refine deep NPC adjacency after the offset-aware room retry. | The overlay-0 room, walking, `::layerloc`, Man lookup, and coin collection pass. Conversation initially occurs only after walking onto the stationary Man because `WalkToMobAction` still checks adjacency against packed surface water. Route layered Mob actions through bounded, same-domain `WorldLocation` adjacency while preserving the legacy Point path with spatial authority disabled |
| 2026-07-25 | Reject the first apparent deep-NPC adjacency success and trace the delayed overlap. | Runtime evidence records dialogue beginning at Player `(450,600)` with the Man at `(452,600)`, followed by the authoritative Player reaching `(452,600)`. The action check is now layered, but both follow-stop consumers still use packed Point collision and let the queued path resume. Route Mob-to-Mob follow stopping through the same gated `WorldLocation` adjacency seam and give the generated Man a bounded nonzero roam radius so stationary spawn configuration is no longer a test variable |
| 2026-07-25 | Accept the corrected Milestone D deep-NPC roam and adjacency route. | The owner confirmed repeated conversations stop correctly without delayed overlap. Runtime logs independently prove the radius-2 Man moved among `(452,600)`, `(453,600)`, and `(451,600)` while conversations continued to resolve. The Mob and Player follow-stop consumers now share the gated Mob-to-Mob `WorldLocation` adjacency seam; focused layered, movement, foundation, and combat guards plus the authoritative 850/488 server build pass |
| 2026-07-25 | Accept the Milestone D deep death/recovery transition. | The owner invoked `::kill devduck` from level `-2` and confirmed return to level 0. Runtime evidence records the command at deep `(452,602)`, then successful location checks and timed processing at Lumbridge `(120,648)`. The final surface logout/reconnect remains before closing the full milestone acceptance route |
| 2026-07-25 | Fully accept Phase 5 Authority Milestone D. | A clean post-death logout saved successfully, reconnect restored exact `global (120,648,L0)` authority with no fallback or rewrite, and `::layerloc` confirmed the recovered surface state. Together with the accepted room, movement, isolation, NPC/item interaction, deep reconnect, explicit exit, and death recovery routes, this closes the bounded synthetic level `-2` compatibility milestone. The broader layered-map workstream remains active |
| 2026-07-25 | Accept the first native-terrain cut of Phase 5 Authority Milestone E. | Fifth-gate server/package terrain and protocol-v3 client page identity are owner-validated at native page `(9,12)`, chunk `24`, including visuals, movement, coin/Man interaction, exact depth reconnect, exit, and surface death recovery. Full-fidelity terrain, package placements, incremental delivery, and compatibility-boundary removal remain |
| 2026-07-25 | Add the full-fidelity Milestone E terrain storage/decode format. | `rle-layered-sector-v1` now preserves arbitrary per-tile sequences and all seven terrain fields in explicit x-major/y-minor order. Tool/server strict validation, malformed-payload refusal, five distinct fixture bands, detached byte fidelity, and mixed encoding dispatch pass; client chunk delivery remains separately gated |
| 2026-07-25 | Add and accept matched full-fidelity 24-tile presentation delivery for Milestone E. | Protocol v4 atomically carries a radius-one window of nine complete-terrain/explicit-void chunks, uses actual per-tile RLE-derived values, stays within the two-byte custom frame at full population, rejects malformed wire data, and refreshes same-package readiness without a full scene-scope reset. Automated and owner routes pass both boundary axes, interaction, exact reconnect, and exit |
| 2026-07-25 | Add and accept package-owned NPC/item placements for Milestone E. | Package `0.3.0` strictly decodes stable-ID, world-space/level-qualified NPC and respawning item data; world load owns registration, the native developer command cannot create them, equal X/Y on different levels remain distinct, stale lifecycle timers refuse, and matched private startup passes. Owner evidence proves normal Man roam/dialogue, two five-coin collections across respawn, exact depth reconnect, no duplicates, and normal exit |
| 2026-07-25 | Add and accept static package-owned scenery/boundary authority for Milestone E. | Package `0.4.0` adds the backward-compatible v2 four-family placement payload; strict tool/server decode, world-load definition checks, level-aware visibility, canonical collision-footprint projection, same-X/Y level isolation, private startup, 30-test authority lineage, and authoritative builds pass. Owner evidence proves Table/Fence visuals, examine actions, blocking and alternate navigation, unchanged NPC/item interaction, exact depth reconnect, duplicate-free visibility, and exit. Dynamic object lifecycle remains pending |
| 2026-07-25 | Implement and accept generation-fenced package-object replacement/removal for Milestone E. | Package `0.5.0` adds an ordinary Door/Doorframe fixture; immutable placement identity survives replacements and delayed reconstruction, registry and spatial-index transactions apply exact level-qualified collision/visibility changes without packed Regions, stale generations refuse, 30 A-E tests and authoritative server/client builds pass. Combined automated/owner evidence proves the exact `6/4/6` collision model, repeated open/close and crossing, unchanged Man/coin interaction, exact depth reconnect, an open Doorframe surviving a second reconnect, post-reconnect close, and normal exit |
| 2026-07-25 | Implement and accept package-owned harvesting and delayed restoration for Milestone E. | Package `0.6.0` adds an ordinary Tree using the existing Woodcutting plugin, Tree-to-Treestump replacement, and delayed-spawn scheduler. Its original location record retains generation-qualified placement identity, allowing the callback to replace the current stump without packed-Region lookup; exact Tree/stump/restored identity, collision, and duplicate-free registry behavior plus the 30-test A-E lineage and authoritative server/client builds pass. Matched private startup and owner evidence confirm a normal harvest/restoration cycle, exact level `-2` reconnect, stable `1n/1i/2s/2b` population, seven collision-overlay tiles, and no stale or duplicate object |
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
