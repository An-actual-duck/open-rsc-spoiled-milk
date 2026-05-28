# Active MyWorld Workspace

This file defines the current active workspace while the standalone migration is in progress.

## Active MyWorld Areas

- `README.md`: current setup and development entrypoint.
- `Makefile`: root MyWorld task aliases.
- `scripts/`: current build, check, run, reset, test, and benchmark wrappers.
- `scripts/lib/`: shared shell helpers for MyWorld wrappers.
- `tools/generators/`: authored generated-data sources and generator code.
- `tools/benchmarks/`: current benchmark helpers.
- `tests/myworld/`: current MyWorld validation suite.
- `docs/myworld/`: current MyWorld planning, specs, audits, and migration notes.
- `server/myworld.conf`: active local server config.
- `server/connections.conf`: required shared connection config.
- `server/plugins/com/openrsc/server/plugins/custom/myworld/`: preferred namespace for MyWorld-owned runtime content.
- `server/conf/server/defs/ItemDefsMyWorld.json`: committed generated item override output.
- `server/conf/server/defs/NpcDefsMyWorld.json`: committed generated NPC override output.
- `server/conf/server/defs/locs/MyWorldSceneryLocs.json`: MyWorld scenery override data.
- `server/inc/sqlite/myworld_seed.db`: canonical local SQLite seed.
- `Client_Base/` and `PC_Client/`: active client build sources until a later client directory rename is done.

## Compatibility Areas

- `dev/myworld/`: compatibility wrappers for older local commands. New tools, tests, and docs should not be added there.
- `server/plugins/com/openrsc/server/plugins/authentic/` and broad `custom/` packages: inherited/shared gameplay code. MyWorld changes can still touch these when needed, but new MyWorld-specific handlers should prefer the `custom/myworld` namespace.
- `server/conf/server/defs/ItemDefsCustom.json`, `NpcDefsCustom.json`, and stock definition files: inherited/shared runtime data. Prefer MyWorld override files unless the base data itself must change.

## Archived Inherited Areas

- `docs/inherited-openrsc/`: upstream OpenRSC/Cabbage reference material.
- `docs/inherited-openrsc/legacy-launchers/`: old root launch and deployment scripts.
- `docs/inherited-openrsc/legacy-server-launchers/`: old server-local launcher wrappers.
- `docs/inherited-openrsc/legacy-Makefile`: inherited OpenRSC/Cabbage Make recipes kept for reference.
- `docs/inherited-openrsc/server-configs/`: inherited non-MyWorld server config presets.
- `docs/inherited-openrsc/sqlite-seeds/`: inherited non-MyWorld SQLite seeds.

Archived files are not active MyWorld workflow files. Do not update them for normal gameplay/content work unless preserving historical reference is the task.

## Local Scratch

The following are local/generated output and should not be treated as active workspace content:

- `output/`
- `build/`
- `bin/`
- `__pycache__/`
- `*.class`
- `dev/myworld/logs/`
- `dev/myworld/artifacts/`

## Still Under Review

- `Android_Client/`.
- `PC_Launcher/`.
- `Portable_Windows/`.

These should not be removed casually. Move them only after a reference scan and a build/check pass.
