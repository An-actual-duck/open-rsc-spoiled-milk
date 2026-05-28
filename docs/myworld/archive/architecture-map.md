# MyWorld Architecture Map

This is the shortest path for understanding how MyWorld-specific work moves from
authored files into the running server.

## 1. Authored sources

The main MyWorld-authored inputs currently live in two places:

- Generated combat override sources in `tools/generators`
  - `tools/generators/item-overrides/*.json`
  - `tools/generators/npc-overrides/*.json`
- Fork-specific notes and plans in `docs/myworld`

These are the main human-edited inputs for the current MyWorld rebalance layer.

Use [README.md](/home/justin/Core-Framework/docs/myworld/README.md) as the
entry point for the current MyWorld documentation set.

Authoritative gameplay-rule references live in:

- [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
- [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md)

## 2. Generator layer

`tools/generators/generators.json` declares the generated artifacts and their source
directories.

`tools/generators/run-generators.py` reads that manifest and drives the individual
generators:

- `tools/generators/generate-item-overrides.py`
- `tools/generators/generate-npc-overrides.py`

Shared generator behavior lives in `tools/generators/generator_common.py`.

Local workflow policy:

- `make generators` or `make sync-generated` rewrites committed defs
- `make check`, `make build-server`, `make run`, `make smoke`, and `make test` validate in
  `--check` mode by default and fail on drift instead of silently rewriting

## 3. Generated runtime data

The generator output is committed into server runtime definition files:

- `server/conf/server/defs/ItemDefsMyWorld.json`
- `server/conf/server/defs/NpcDefsMyWorld.json`

This keeps the runtime input explicit and reviewable while still letting the
authored sources stay split into smaller files.

## 4. Runtime loading path

At server startup, MyWorld data is layered on top of the base game defs.

- `server/src/com/openrsc/server/external/EntityHandler.java`
  - loads stock defs
  - loads custom defs
  - applies `ItemDefsMyWorld.json`
  - applies `NpcDefsMyWorld.json`
- `server/src/com/openrsc/server/database/WorldPopulator.java`
  - loads `server/conf/server/defs/locs/MyWorldSceneryLocs.json` when present
- `server/myworld.conf`
  - selects the MyWorld DB, ports, feature toggles, and PvM-only runtime policy

The effective order is:

1. stock world data
2. shared custom data
3. MyWorld overrides

## 5. MyWorld gameplay code

Fork-owned runtime handlers should live under the dedicated namespace:

- `server/plugins/com/openrsc/server/plugins/custom/myworld`

Current MyWorld-owned packages:

- `server/plugins/com/openrsc/server/plugins/custom/myworld/skills/enchanting`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/misc`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/npcs`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/itemactions`
- `server/plugins/com/openrsc/server/plugins/custom/myworld/quests`

This namespace is where new MyWorld-specific handlers should go before adding
more code to broad shared `custom` packages.

MyWorld runtime policy:

- `MyWorld` is PvM-only
- duel and wilderness/OpenPK combat are not part of the intended gameplay scope
- PvP-specific shared code only matters here when it blocks or complicates PvM
  behavior

One current compatibility exception remains in
`server/plugins/com/openrsc/server/plugins/custom/quests/free/PeelingTheOnion.java`,
which forwards older shared hook points into the real MyWorld quest
implementation under `custom/myworld`. The MyWorld boundary test now also
checks that this bridge's exported states and helper methods stay in parity
with the real quest class.

## 6. Validation path

The main validation entrypoints are:

- `./scripts/check.sh` for prerequisites + generated-artifact drift
- `./tests/myworld/test-smoke.sh` for compile + boot/load validation
- `./tests/myworld/test-all.sh` for the full local MyWorld suite
- `.gitlab-ci.yml` job `myworldValidation` for CI coverage

The smoke test is the bridge between data-only checks and actual runtime startup.

## 7. Current boundary line

MyWorld is still a layered fork, not a fully isolated product. A lot of real
behavior still lives in shared server/core/plugin code, while `dev/myworld`
is now only a compatibility area for older local commands.

The near-term cleanup goal is to keep moving MyWorld-only handlers into the
`custom/myworld` namespace while preserving the current generated-data workflow.
