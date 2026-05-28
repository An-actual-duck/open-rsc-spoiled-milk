# MyWorld Docs

This folder keeps only the active MyWorld documentation surface.
The public project overview and player-facing feature summary now live in the
repository root [`README.md`](../../README.md).

## Active Docs

- [work-items.md](work-items.md): current work list, grouped by small, medium, and large scope.
- [change-history.md](change-history.md): consolidated record of major changes made so far.
- [combat-equipment-spec.md](combat-equipment-spec.md): source of truth for armor budgets, set traits, poison, and debuff stacking rules.
- [altar-enchantment-and-conversion-plan.md](altar-enchantment-and-conversion-plan.md): implemented rune-altar enchanting costs, two-part Enchanting gates, and steel-only god knight conversion.
- [god-knight-equipment-audit.md](god-knight-equipment-audit.md): implementation record and remaining tuning decisions for god-aligned knight equipment sources.
- [pvm-population-and-cluster-plan.md](pvm-population-and-cluster-plan.md): implemented pre-release PvM population passes and remaining expansion decisions.
- [pvm-npc-cluster-audit.md](pvm-npc-cluster-audit.md): hostile-NPC proximity audit, including the Wilderness-focused population view.
- [dual-element-spells.md](dual-element-spells.md): current dual-element Magic spell implementation and live effect identities.
- [jewelry-and-retired-robe-effects.md](jewelry-and-retired-robe-effects.md): source of truth for current jewelry effects and robe effects retired from cloth armor.
- [compatibility-only-content.md](compatibility-only-content.md): ledger for dormant item families, quest exceptions, and retired systems kept for compatibility.
- [summoning-plan.md](summoning-plan.md): current Summoning implementation source of truth and tuning notes.
- [fishing-spot-map.md](fishing-spot-map.md): current fishing spot cluster map and source coordinate list.
- [fishing-rework-plan.md](fishing-rework-plan.md): current rod-tier and location-pool Fishing implementation record.
- [resource-seed-plan.md](resource-seed-plan.md): implemented rare side-reward seed framework for Woodcutting and Harvesting.
- [migration-regression-audit.md](migration-regression-audit.md): resolved migration recovery record and guardrail notes.
- [dev-admin-commands.md](dev-admin-commands.md): MyWorld development account roles and practical command reference.
- [testing-quick-reference.md](testing-quick-reference.md): compact field-testing reference for commands, IDs, and targeted test fixtures.
- This README: current repository shape, active paths, and archived reference locations.

## Current Repository Shape

Active MyWorld workflow:

- `Makefile`: root MyWorld task aliases.
- `scripts/`: current build, check, run, reset, test, and benchmark wrappers.
- `tools/generators/`: authored generated-data sources and generator code.
- `tools/benchmarks/`: current benchmark helpers.
- `tests/myworld/`: current MyWorld validation suite.
- `docs/releases/`: player packaging, hosted-alpha, and publication checklists.
- `server/myworld.conf`: active local server config.
- `server/connections.conf`: required shared connection config.
- `server/inc/sqlite/myworld_seed.db`: canonical local SQLite seed.
- `server/inc/sqlite/myworld_dev.db`: local dev database state.
- `server/plugins/com/openrsc/server/plugins/custom/myworld/`: preferred namespace for MyWorld-owned runtime content.

Compatibility areas:

- `dev/myworld/`: compatibility wrappers for older local commands. Its
  `assets/` subtree remains the active visual asset source loaded by the
  client. New tools, tests, and planning docs should not be added there.
- Broad inherited server/client packages can still be changed when needed, but new MyWorld-specific handlers should prefer `custom/myworld`.

Archived reference:

- `docs/myworld/archive/`: old MyWorld planning docs and detailed audits.
- `docs/inherited-openrsc/`: inherited OpenRSC/Cabbage docs, launchers, configs, SQLite seeds, and Make recipes.

## Validation

Use these before and after structural cleanup:

```bash
make check
python3 ./tests/myworld/test-standalone-layout.py
make build-server
```

Run the full suite when local ports are clear:

```bash
make test
```
