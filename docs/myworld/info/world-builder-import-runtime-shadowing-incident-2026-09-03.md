# World Builder import runtime-shadowing incident

Status: the first symptoms were contained in Spoiled Milk `v0.2.83` and
`v0.2.84`; a subsequent audit found broad latent incompatibility. Core's
restoration removes the imported server snapshot from active compile/runtime
classpaths and runs the installed map from current Core source. The permanent
importer/runtime-provider correction belongs to the independent RSC World
Editor and RSC World Editor Runtime projects.

Incident date: 2026-09-03

Core baseline before import: `8aa09f70897dec5bdeb07ddedb30533118ab382f`
(`v0.2.80`)

Initial installed-runtime import: `14cac09c4`

Affected public build: `636d53bc520a7f904277048719116bb765596395`
(`v0.2.82`)

Core containment fix: `c2ff56d7cd69219ef78a6b51ab3b9efb0e1d7b1e`
(`v0.2.83`)

## Executive conclusion

The importer overstepped its safe ownership boundary. It correctly installed
the signed layered map and the runtime features needed to load it, but it also
installed a nearly complete compiled copy of the server and put that archive
ahead of the target server's own `core.jar` at runtime.

That is not a safe way to adapt an independently changing game server. It
silently replaced target-owned gameplay behavior through Java classpath
precedence, including `Player`, `Inventory`, `ActionSender`, bank handlers,
item handlers, trade handling, combat/event code, and `World`.

The result was a hybrid process:

- plugins were compiled against the target's current `core.jar`;
- the JVM loaded many foundational classes from the imported managed-runtime
  archive instead;
- classes absent from that archive continued to load from current Core;
- linkage therefore succeeded at build and startup but failed when a current
  plugin invoked a method missing from the imported copy of `Player`;
- other failures did not throw: the imported `Inventory` simply restored the
  older fixed-capacity behavior, so valid persisted entitlements appeared
  lost.

The correct direction is **narrower mutation plus broader detection**:

1. The importer should own map/package data, generated installation metadata,
   and a narrowly namespaced runtime provider.
2. It should not ship or shadow target-owned server classes.
3. It should inspect the target more broadly before installation and refuse an
   incompatible target with an actionable report.
4. Required changes to target-owned classes must be integrated and compiled
   from the target's current source, never supplied as stale bytecode in a
   precedence archive.

## User-visible symptom

Two accounts had valid Monster Slayer satchel entitlements in the live SQLite
database:

| Account | Stored entitlement mask | Derived capacity |
| --- | ---: | ---: |
| `devduck` | `1` | `31` |
| `FrankTheTank` | `63` | `40` |

After the import, the client showed only the base backpack size. The Slayer
associate still recognized the entitlement state, but attempting the upgrade
dialogue failed. This initially resembled player-data loss; no player-data loss
had occurred.

The affected public log records the exact runtime failure twice:

```text
2026-09-03 12:31:32 ERROR PluginHandler: InvocationTargetException
Caused by: java.lang.NoSuchMethodError:
  com.openrsc.server.model.entity.player.Player.supportsExpandedInventory()Z
at MonsterSlayerContacts.purchaseSatchelUpgrade(MonsterSlayerContacts.java:223)
```

The surrounding stack identifies imported archive classes executing the
plugin task and thread factory:

```text
PluginTask.call(...) ~[world-builder-managed-runtime.jar:?]
ServerAwareThreadFactory.lambda$newThread$0(...) ~[world-builder-managed-runtime.jar:?]
```

The same session logged in with custom client version `10052`, so this was not
an old-client capability issue.

## What the import changed

The initial import commit added two content-addressed copies of the map:

- 1,789 paths under `Client_Base/world-builder`;
- 1,789 paths under `server/world-builder`.

Each set consists of one manifest, 1,782 terrain sectors, and six placement
sets. These are expected map-product changes.

It also changed files outside the package trees:

- deleted both legacy `Custom_Landscape.orsc` archives;
- modified `server/build.xml` and added the managed runtime to compilation and
  both production runtime classpaths;
- added `server/world-builder-runtime/world-builder-managed-runtime.jar`;
- changed server launch, release, package, test, configuration, and deployment
  support;
- changed multiple client runtime sources and client build behavior;
- added installed client/server capability documents.

Some non-package integration is expected for a new map runtime. The unsafe
part was not merely that integration files changed. It was that the installed
runtime archive became an alternate compiled server implementation without a
safe ownership or compatibility check.

## Classpath mechanism

The imported `server/build.xml` compiled plugins with this order:

```text
core.jar
world-builder-runtime/world-builder-managed-runtime.jar
```

Current Core therefore won compilation. `MonsterSlayerContacts` legally
compiled a call to `Player.supportsExpandedInventory()`.

The importer added the managed runtime before `core.jar` to `runserver` and
`runserverzgc`:

```text
world-builder-runtime/world-builder-managed-runtime.jar
server/lib/*
core.jar
```

Java class loading is first-match. The runtime copy of `Player` therefore won,
and that copy has no `supportsExpandedInventory()` method. Startup did not
eagerly resolve the plugin call site, so the server reached an online state and
failed only when the player selected the satchel dialogue.

The imported `Inventory` class similarly exposes only the old fixed
`MAX_SIZE`. Current Core's class exposes `MAX_SUPPORTED_SIZE` and
`getCapacity()`. Loading the imported class restored fixed-capacity admission
without necessarily raising an exception.

## Archive inventory

The managed-runtime archive is not a small provider library.

| Measurement | Result |
| --- | ---: |
| Total class entries in managed runtime | 1,643 |
| Entries under `com/openrsc/server` | 1,643 |
| Classes also present in pre-import Core | 1,570 |
| Overlapping classes byte-identical to Core | 1,300 |
| Overlapping classes byte-different from Core | 270 |
| Classes present only in the managed runtime | 73 |
| Pre-import Core server classes absent from managed runtime | 792 |

The 1,643 classes are distributed across foundational server areas:

| Package family | Managed-runtime classes |
| --- | ---: |
| `com/openrsc/server/model` | 673 |
| `com/openrsc/server/net` | 327 |
| `com/openrsc/server/event` | 271 |
| `com/openrsc/server/content` | 108 |
| `com/openrsc/server/diagnostics` | 70 |
| `com/openrsc/server/util` | 66 |
| `com/openrsc/server/external` | 43 |
| `com/openrsc/server/io` | 35 |

The 270 byte-different overlaps include unrelated gameplay and infrastructure
classes such as:

- `Player`, `Inventory`, `CarriedItems`, `Equipment`, and `BankPreset`;
- `ActionSender`, login/protocol encoders, opcode definitions, and payload
  generators;
- bank, equip, item-use, trade, attack, spell, command, and NPC-talk handlers;
- `World`, `Region`, `RegionManager`, entity, skill, pathing, and event classes;
- `PluginTask`, `GameEventHandler`, and the server thread factory.

Only 73 classes are runtime-only. Most are World Builder/editor classes. The
archive nevertheless carries 1,570 target-class duplicates to supply those
features.

The archive manifest contains only:

```text
Implementation-Title: World Builder installed server upgrade
```

It does not identify a source commit, target commit, target ABI, build input
inventory, or compatible host API range. Its SHA-256 in this incident is:

```text
0661c95200c0597174c1a5a2904bb9e3dbf147fa14dd317a0f95084ac3cb1e30
```

The installed capability document also declares
`requiresExactInventorySha256: false`. Whatever that flag was originally
intended to cover, no enforced class/API inventory prevented this mismatch.

## Why using `core.jar` first is not a complete fix

Reversing classpath order restores current gameplay classes but does not
produce a valid installed-map runtime. The imported provider modified existing
host classes to add map-runtime behavior.

For example:

- the imported `World` defines
  `projectNativeLayeredGameObjectCollisionFootprint(...)`, which the current
  target `World` did not define;
- the current target `World` defines Monster Slayer task/shop/legacy service
  APIs which the imported `World` did not define.

A JVM can load only one `World` definition. Either classpath order discards
one side. During diagnosis, current-Core-first startup also could not use the
old terrain path because the importer had already deleted
`Custom_Landscape.orsc`.

This means the problem cannot be solved reliably by selecting which whole
server snapshot wins. The map runtime needs an extension boundary, or its
required host changes need to be reconciled into current target source and
compiled once.

## Scope and ownership contract recommended for the importer

### Safe direct ownership

The importer may atomically install or replace:

- one content-addressed map package subtree;
- one generated installed-package descriptor pointing to that subtree;
- generated validation reports and receipts;
- a narrowly namespaced provider archive containing no target-owned class;
- a clearly delimited generated configuration block, after validating all
  preconditions.

### Conditional integration

The importer may propose, but should not silently overwrite:

- build and launch configuration;
- target client bootstrap/source integration;
- target server adapter/source integration;
- release and deployment integration;
- legacy-map retirement.

Every conditional change should use an expected-before hash or structured
three-way merge, show the exact plan, and refuse on target customization or
merge conflict.

### Never replace or shadow

The importer and provider archive should never replace or take precedence over:

- `server/src` gameplay, model, networking, database, event, or service code;
- `server/plugins`;
- server definitions or custom content outside the imported map package;
- target `core.jar` or `plugins.jar` classes;
- player databases, caches, credentials, logs, or live state;
- unrelated build, release, test, workspace, or deployment policy.

The same rule applies if the files themselves remain untouched but imported
bytecode shadows their compiled classes. Classpath replacement is still
replacement.

## Preferred architecture

The durable design is a host-owned runtime interface and a provider-owned
implementation.

```text
Core server
  -> stable World Builder host API / service interfaces
  -> current Core Player, Inventory, World, networking, content, and events
  -> provider registration and lifecycle

World Builder runtime provider
  -> uniquely namespaced implementation classes
  -> map parsing, validation, residency, and placement services
  -> no duplicate com.openrsc.server host classes

Imported project
  -> signed/content-addressed map data and manifest
  -> declares required host/provider capability versions
```

If the current server lacks a required host extension point, installation
should stop and report that requirement. The Editor/Runtime project may then
produce a reviewed source integration change for Core. After that change is
merged, the importer can install data without replacing code.

If a transitional source upgrader is unavoidable, it should:

1. identify the exact supported target source revisions or semantic API
   contract;
2. preserve target-owned method bodies and custom additions;
3. apply the smallest source-level change with precondition hashes;
4. fail closed on an unexpected target;
5. compile one target-owned `core.jar` after integration;
6. prove there are no provider/host duplicate classes;
7. leave an exact receipt of every changed path and before/after hash.

It should never fall back to an opaque, first-on-classpath server snapshot.

## Broader detection the importer needs

Broader detection is valuable when it is used to prevent replacement, not to
decide that more files should be replaced.

Before changing the target, the importer should collect:

- target repository identity and exact commit;
- dirty/untracked state without assuming ignored files are disposable;
- active build and run entry points;
- source and compiled class ownership;
- existing map/runtime profile and package identity;
- provider/host duplicate class inventory;
- public/protected API signatures for every overlapping class;
- target plugin and custom-content references to affected APIs;
- custom definitions, database paths, credentials, and live-state exclusions;
- legacy assets required for rollback;
- current client/server protocol capability versions.

The compatibility decision should be based on explicit capabilities and API
symbols, not names such as `v1`, `v2`, or a claim that the managed runtime is
"current."

At minimum, installation must refuse when:

- the provider archive contains a target-owned class;
- the same class name has different bytes in provider and target artifacts;
- any target plugin or class has an unresolved runtime method or field;
- a proposed edit touches a path outside the declared ownership plan;
- a target-owned file differs from the upgrader's expected-before hash;
- legacy rollback inputs would be removed without an explicit, separately
  approved retirement operation;
- the exact production classpath has not been exercised successfully.

## Required preflight and post-install checks

### Preflight

1. Produce a dry-run receipt listing every create, modify, delete, config
   mutation, classpath change, and archive installation.
2. Classify each path as map data, generated metadata, provider code, target
   integration, or forbidden target content.
3. Inspect every provider archive and reject duplicate host classes.
4. Validate required host API symbols and protocol versions.
5. Record before hashes and ensure all edits are reversible.
6. Preserve legacy terrain/placement inputs unless retirement is separately
   requested and validated.

### Staged installation

1. Build in a fresh staging directory.
2. Compile the target's authoritative server and plugins.
3. Resolve and eagerly link every plugin/handler against the exact production
   runtime classpath.
4. Generate a class-origin report for critical packages.
5. Boot a private server with the installed package and production profile.
6. Exercise login, inventory, bank, item, NPC dialogue, trade, movement,
   placement, and map-residency smoke paths.
7. Atomically promote only after all checks pass; otherwise restore the exact
   pre-import tree.

### Receipt

The successful receipt should contain:

- importer, provider, target, and package versions/commits;
- package and archive hashes;
- all before/after file hashes;
- duplicate-class count, which must be zero for host-owned namespaces;
- host API compatibility result;
- build/test commands and results;
- rollback location and retained legacy assets.

## Regression fixtures for the Editor/Runtime projects

The importer suite should include a target fixture with intentional custom
server content:

- `Player` has an additional public capability method;
- `Inventory` derives capacity from persistent custom entitlements;
- a current plugin calls that method;
- `World` exposes a target-owned custom service;
- bank, item-use, trade, and opcode behavior differs from the provider's
  development baseline;
- legacy terrain archives exist before import;
- the map provider requires one host integration capability absent from the
  untouched target.

Acceptance criteria:

1. Import does not change or shadow any custom gameplay class.
2. Provider/host duplicate class count is zero.
3. Missing host capability produces an actionable refusal, not an automatic
   server snapshot install.
4. After approved source integration, target build and exact-production
   linkage pass.
5. Login sends the entitlement-derived capacity.
6. Capacity masks `1` and `63` produce 31 and 40 slots respectively.
7. The Slayer associate dialogue completes without linkage errors.
8. Bank, equip, item-use, trade, and movement smoke checks retain target
   behavior.
9. Map boot loads the expected 1,782 sectors and all four placement families.
10. A second import of the same package is idempotent.
11. Injected failure at each transaction stage restores the original tree.
12. Legacy rollback remains available until explicitly retired.

An additional negative fixture should deliberately supply the archive from
this incident. The new importer must reject it before making any target
changes and identify representative duplicates such as `Player`, `Inventory`,
`World`, and `ActionSender`.

## Core-side containment in v0.2.83

Spoiled Milk `v0.2.83` adds a narrow, generated Core gameplay overlay before
the managed runtime. It restores current inventory, bank, item-action, and
trade class families while allowing the imported map-specific `World` to
remain active. Login and purchase paths now send the current inventory-capacity
packet without calling the missing imported `Player` method.

This containment was validated with:

- an exact production-classpath class-origin test;
- capacity masks `1 -> 31` and `63 -> 40`;
- inventory-capacity wire receipt validation;
- Monster Slayer persistent-state tests;
- a private production-profile server boot;
- a public production boot at the exact published commit.

The containment is deliberately not the recommended importer architecture. It
protects this deployment from the observed stale gameplay classes while the
map still depends on other classes in the monolithic provider archive. The
permanent fix is to eliminate host-class shadowing at the provider/importer
boundary.

## Second production linkage failure

At 14:05 on the same day, `FrankTheTank` cast a teleport. The managed
runtime's shadow copy of `Skills` still called the older
`RangersGuildPoints.awardFromExperience(Player,int,int)` API. Core commit
`829573b1e` had already replaced experience-based Rangers Guild points with
eligible ranged-kill awards before the import, and the provider archive did
not contain its own `RangersGuildPoints` class. The JVM therefore combined a
stale caller from the provider with the current callee from Core and raised a
`NoSuchMethodError`.

The error occurred after the teleport event added Magic experience but before
`SingleEvent.run()` marked the one-shot event stopped. `GameTickEvent.call()`
caught `Exception`, not linkage `Error`, so the event remained runnable and
retried every game tick. This produced continuous stack traces, repeatedly
added in-memory Magic experience, and aborted the remaining tick work. The
persisted experience still matched the pre-deployment backup when diagnosed.

The immediate Core compatibility bridge retains the removed binary signature
as an intentional no-op. This preserves the current kill-based reward design
while allowing the stale managed-runtime caller to finish. The v0.2.84
production-precedence regression test proved that `Skills` was loaded from the
managed runtime, `RangersGuildPoints` was loaded from current Core, one
experience award completed exactly once, and no Rangers Guild points were
created. This was further evidence that eager linkage and duplicate-class
rejection are required in the importer rather than relying on successful
startup.

## Broad Core restoration after latent-linkage audit

A later exact-classpath audit showed that the narrow overlay and Rangers bridge
did not contain the full incident. The production winners still omitted at
least 66 current project API symbols referenced by 138 current Core/plugin call
sites. The affected surface included ordinary combat initiation, resolved
damage, ranged/projectile validation, stat drains, poison, Summoning equipment
bonuses, Cleric transient effects, Monster Slayer services, and development
commands. Paired stale caller/callee classes also remained capable of silently
disabling current behavior without a linkage exception.

Core already contained the installed-package activation, manifest validation,
native terrain, placement, collision, protocol, and client integration needed
by the imported map. Its missing hosted-startup behavior was a small ownership
gate: replacement packages still called the retired legacy terrain loader.

The broad restoration therefore:

1. removes `world-builder-managed-runtime.jar` from plugin compilation and both
   production server run classpaths;
2. keeps current `core.jar` authoritative for all Core-owned classes;
3. makes native replacement profiles explicitly skip legacy terrain archives;
4. retains and validates the exact imported content-addressed map package;
5. changes class-origin tests to require current `World`, `Skills`, inventory,
   and Rangers Guild behavior; and
6. changes the build audit to reject reintroduction of the imported server
   snapshot into Core compile/runtime paths.

The provider archive remains tracked as import evidence but is inert in the
Core server. Editor-embedded adaptive runtime behavior remains the independent
runtime project's responsibility and is not borrowed into Core.

Verification included the full Core suite and a maintained-launcher private
boot. The private server activated the exact installed package, skipped the
deleted legacy archive, populated 3,803 NPCs, 879 ground items, 27,892 scenery
objects, and 971 boundaries, loaded all 469 plugin handlers, and reached both
loopback ports. The full client compiled and all package/protocol/rendering and
gameplay characterization gates passed without requiring another client source
change.

## Reproduction and audit commands

From the Core manager checkout:

```bash
git diff 8aa09f708..14cac09c4 -- server/build.xml
jar tf server/world-builder-runtime/world-builder-managed-runtime.jar
javap -classpath server/world-builder-runtime/world-builder-managed-runtime.jar \
  com.openrsc.server.model.container.Inventory
javap -classpath server/world-builder-runtime/world-builder-managed-runtime.jar \
  com.openrsc.server.model.entity.player.Player
javap -classpath server/core.jar \
  com.openrsc.server.model.container.Inventory
javap -classpath server/core.jar \
  com.openrsc.server.model.entity.player.Player
```

The pre-import backup supplied for this investigation is
`/home/justin/Core-Framework (copy)`. Its recorded Git head is the `v0.2.80`
baseline above, it has no managed-runtime archive, and its `core.jar` already
contains the expanded-inventory implementation.
