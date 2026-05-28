# MyWorld Compatibility Area

This directory retains compatibility wrappers for older MyWorld development
commands. The primary command workflow lives at the repository root, while
`assets/` remains the active client visual-asset source.

## What it uses

- Server config: [`server/myworld.conf`](../../server/myworld.conf)
- Database name: `myworld_dev`
- Seed database: `server/inc/sqlite/myworld_seed.db`

## Common commands

Preferred root-level shortcuts:

```bash
make build-client
make build-server
make run
make start-fresh
make check
make test

./scripts/build-client.sh
./scripts/build-server.sh
./scripts/run-server.sh
./scripts/start-fresh.sh
./scripts/check.sh
./scripts/test.sh
```

Legacy task wrapper shortcuts:

```bash
make -C dev/myworld help
make -C dev/myworld sync-generated
make -C dev/myworld check
make -C dev/myworld build
make -C dev/myworld test
make -C dev/myworld combat-check
make -C dev/myworld prayer-check
```

Reset the SQLite database from the MyWorld seed:

```bash
./scripts/init-sqlite.sh
```

Run the full clean startup flow:

```bash
./scripts/start-fresh.sh
```

Compile core and plugins:

```bash
make sync-generated
./scripts/build-server.sh
```

Validate that generated override artifacts are current without rewriting them:

```bash
python3 ./tools/generators/run-generators.py --check
./scripts/check.sh
```

Inspect which generated artifacts are declared and what commands would run:

```bash
python3 ./tools/generators/run-generators.py --list
python3 ./tools/generators/run-generators.py --dry-run
python3 ./tools/generators/run-generators.py --only item --check
python3 ./tools/generators/run-generators.py --group combat --dry-run
```

Run the server in the foreground:

```bash
make sync-generated
./scripts/run-server.sh
```

Run the server with the Java 17+ launcher:

```bash
./scripts/run-server-zgc.sh
```

This launcher fails fast unless `java` is version 17 or newer.

Run the current smoke/data validation checks:

```bash
./scripts/check.sh
./tests/myworld/test-smoke.sh
python3 ./tests/myworld/test-combat-data.py
python3 ./tests/myworld/test-balance-fixtures.py
python3 ./tests/myworld/test-runecraft-data.py
python3 ./tests/myworld/test-enchanting-data.py
python3 ./tests/myworld/test-bank-shortcuts.py
python3 ./tests/myworld/test-source-cleanup.py
python3 ./tests/myworld/test-quest-system.py
python3 ./tests/myworld/test-quest-shortcuts.py
python3 ./tests/myworld/test-quest-choice-audit.py
python3 ./tests/myworld/test-quest-rollout-audit.py
python3 ./tests/myworld/test-production-ui.py
python3 ./tests/myworld/test-production-behavior.py
python3 ./tests/myworld/test-production-flow.py
python3 ./tests/myworld/test-prayer-rework.py
python3 ./tests/myworld/test-prayer-ui.py
python3 ./tests/myworld/test-myworld-plugin-layout.py
python3 ./tests/myworld/test-myworld-import-boundary.py
python3 ./tests/myworld/test-entrypoints.py
python3 ./tests/myworld/test-ranged-runtime-tables.py
python3 ./tests/myworld/test-npc-attack-styles.py
python3 ./tests/myworld/test-combat-runtime-invariants.py
python3 ./tests/myworld/test-combat-exceptions.py
python3 ./tests/myworld/test-combat-scenarios.py
./tests/myworld/test-all.sh
```

Run the opt-in foundation timing benchmark:

```bash
./scripts/benchmark.sh
./scripts/benchmark-matrix.sh
MYWORLD_BENCHMARK_TICKS=240 MYWORLD_BENCHMARK_WARMUP_TICKS=20 ./scripts/benchmark.sh
MYWORLD_BENCHMARK_SCENARIOS="short soak" ./scripts/benchmark-matrix.sh
MYWORLD_BENCHMARK_SYNTHETIC_PLAYERS=64 ./scripts/benchmark.sh
MYWORLD_BENCHMARK_SCENARIOS="players" ./scripts/benchmark-matrix.sh
```

## Recommended workflow

1. Reset the DB once with `init-sqlite.sh`.
2. Compile with `compile-server.sh`.
3. Run with `run-server.sh`.
4. Add new content under the existing custom content paths:
   - `server/plugins/com/openrsc/server/plugins/custom`
   - `server/conf/server/defs/ItemDefsCustom.json`
   - `server/conf/server/defs/NpcDefsCustom.json`
   - `server/conf/server/defs/locs`

For fork-specific work, prefer the dedicated namespace:

- `server/plugins/com/openrsc/server/plugins/custom/myworld`
- `server/conf/server/defs/locs/MyWorld*.json`
- `docs/myworld`
- `tools/generators/item-overrides/*.json`
- `tools/generators/npc-overrides/*.json`

Architecture overview:

- `../../docs/myworld/README.md`
- `../../docs/myworld/archive/architecture-map.md`
- `../../docs/myworld/archive/standalone-extraction-plan.md`

## Notes

- This setup intentionally does not modify the stock `cabbage` database.
- The reset script clones `server/inc/sqlite/myworld_seed.db` into `server/inc/sqlite/myworld_dev.db`.
- The compile and run scripts use the repo-bundled Ant launcher in `Portable_Windows/apache-ant-1.10.5/bin/ant`.
- `build`, `run`, `smoke`, `test`, and `doctor` now validate generated artifacts in `--check` mode by default and do not rewrite them as a side effect.
- After editing `tools/generators/item-overrides` or `tools/generators/npc-overrides`, explicitly sync the committed generated defs with `make generators` or `make sync-generated` before compiling or running.
- `myworld.conf` keeps the Cabbage feature set but changes the world identity, ports, and dev-only operational settings.
- `generate-item-overrides.py` merges the authored item-override source files in `tools/generators/item-overrides` into `server/conf/server/defs/ItemDefsMyWorld.json`.
- `generate-npc-overrides.py` merges the authored NPC-override source files in `tools/generators/npc-overrides` into `server/conf/server/defs/NpcDefsMyWorld.json`.
- `generators.json` is the manifest for MyWorld generated artifacts and their authored source directories.
- `generators.json` also declares reusable generator groups such as `combat`, `items`, and `npcs`.
- `run-generators.py` executes every generator declared in `generators.json`, and also supports `--check`, `--list`, `--dry-run`, `--only <name>`, and `--group <name>`.
- The root `Makefile` provides short task aliases for the manifest-driven workflows such as `check`, `build-server`, `test`, and `combat-check`.
- `benchmark-foundation.sh` runs the opt-in foundation timing benchmark and writes summaries under `output/benchmarks/optimization`.
- `benchmark-foundation.sh` also accepts `MYWORLD_BENCHMARK_SYNTHETIC_PLAYERS` so benchmark-only dummy players can exercise the packet/update path without needing live clients.
- Benchmark summaries include both integer millisecond fields such as `avgUpdateClientsMs` and decimal fields such as `avgUpdateClientsMsPrecise` for smaller packet/update changes.
- `benchmark-foundation-matrix.sh` runs repeatable `short`, `soak`, and optional `players` scenarios so changes can be compared against both the no-player NPC baseline and a synthetic player-load sample.
- `docs/myworld/README.md` is the entry point for current MyWorld planning and documentation.
- `dev/myworld/assets/` remains the live source tree for client visual assets;
  it is not a compatibility-only wrapper path.
- `output/logs`, `output/benchmarks`, and Python `__pycache__` directories are treated as local scratch/output areas and are ignored.
- The initial NPC split groups entries into `strength-overrides-and-bosses`, `melee-resistant`, `ranged-resistant`, `magic-resistant`, and `mixed-and-balanced` source files.
- Both generators fail on duplicate ids, reject unknown entry fields, and print per-source summaries so balance edits are easier to audit before the merged runtime files are written.
- Both generators support `--check` to validate authored sources and fail if the committed merged runtime JSON has drifted.
- `test-smoke.sh` verifies compile + boot/load + override application.
- `test-runecraft-data.py` validates the simplified rune loop, altar defs, and obsolete-item retirement.
- `test-enchanting-data.py` validates the altar-jewelry product lines and key enchanting runtime mappings.
- `test-magic-enchanting-costs.py` validates client/server spell rune-cost parity plus the current altar, item-tier, and material-cost gates for Enchanting.
- `test-magecraft-data.py` validates the active wood-tier elemental staff ladder, and also pins battlestaffs as retired holdovers rather than an active progression family.
- `test-source-cleanup.py` validates key retired source redirects and the currently intentional specialty holdovers.
- `test-bank-shortcuts.py` validates the custom-bank `Ctrl`-click full-quantity withdraw/deposit shortcuts and their priority over organize-mode dragging.
- `test-quest-system.py` validates that MyWorld no longer auto-completes quests globally at login and that quest shortcuts remain per-quest behavior.
- `test-quest-shortcuts.py` validates the implemented per-NPC MyWorld quest shortcuts and the audited utility-item backfills they rely on.
- `test-quest-choice-audit.py` validates the branch-reward audit doc and the current MyWorld remap policy for quest reward skills.
- `test-quest-rollout-audit.py` validates the implemented full quest shortcut matrix and the current clarification ledger.
- `test-production-ui.py` validates the hardened production-session and packet-assembly structure that now backs the live smithing and Crafting production windows, including former fletchery actions.
- `test-production-behavior.py` validates the live production-window behavior invariants for default selection, recipe gating, and representative disabled-state truth across smithing and Crafting production.
- `test-production-flow.py` validates the live production-window handoff into batching, including quantity clamping, `productionStart` event scheduling, and routing former fletchery actions through Crafting.
- `test-prayer-rework.py` validates the accepted MyWorld prayer catalog, god-line mappings, altar assignment guardrails, and current 15-slot server prayer state.
- `test-prayer-ui.py` validates the client-side 3x5 prayer icon grid, placeholder cells, allocation-cost tooltip wording, and three swappable 15-slot god-line definition sets.
- `test-myworld-plugin-layout.py` guards a small set of handlers that should now live under `server/plugins/com/openrsc/server/plugins/custom/myworld`.
- `test-myworld-import-boundary.py` prevents new direct references from shared/authentic plugin packages into the MyWorld namespace, and also verifies that the one explicit `PeelingTheOnion` compatibility bridge still matches the real MyWorld quest surface.
- `test-entrypoints.py` validates that MyWorld run scripts propagate real startup failures and that the ZGC launcher rejects Java versions below 17.
- `test-ranged-runtime-tables.py` validates that the live ranged ammo and throwing-weapon lookup tables still cover the authored MyWorld ranged tiers.
- `test-npc-attack-styles.py` validates the NPC ranged/magic style profile, projectile attack paths, and source stats used to calculate ranged/magic damage.
- `test-combat-runtime-invariants.py` validates scripted-playtest combat invariants for projectile engagement, target priority, damage-share XP, personal contributor loot, and rare-drop contribution gating.
- `test-combat-exceptions.py` validates reviewed combat bypasses, PvM-only config guardrails, and direct kill/drop exceptions that should not silently expand.
- `test-combat-scenarios.py` replays representative PvM combat scenarios against merged MyWorld data and mirrored server formulas for group rewards, target priority, projectile-role intent, and style viability.
- `test-combat-data.py` validates MyWorld combat override JSON and a few key progression families.
- `test-balance-fixtures.py` prints and validates a small set of combat fixture snapshots for player loadouts and key NPCs.
- Battlestaffs are currently treated as retired legacy gear. MyWorld's active magic-weapon progression is the wood-tier staff line plus elemental attunements through Enchanting, while battlestaffs remain only as low-power holdovers until they are either promoted or removed.
- The specialty named staffs such as Iban, the god staffs, and relic-tier staffs still need a closer balance pass. The current plan is to treat them as the tier-11 magic counterparts to the other combat styles' tier-11 weapons, rather than as retired holdovers.
- `Dramen Staff` currently serves as a utility staff with tier-3 magic offense, and the older quest/special staff (`id 198`) currently serves as a utility staff with tier-5 magic offense.
- MyWorld now renames the older legacy `Magic Staff` (`id 198`) to `Wizard staff` to avoid colliding with the wood-line `Magic Staff` (`id 1784`).
- That rename may require a later quest/reference audit, because older quest dialogue, item checks, and guide text may still refer to the legacy item by its old `Magic Staff` name.
