# MyWorld Standalone Repository Shape

MyWorld now runs as a MyWorld-first repository built on the inherited OpenRSC/Cabbage engine foundation. The current shape keeps the proven source directories and package names, but moves day-to-day workflows, generated data, tests, docs, and active configs into MyWorld-owned locations.

## Guiding Rules

- Keep the server and client buildable at every phase.
- Move entrypoints before moving source directories.
- Keep package names and Ant builds until the standalone layout is proven.
- Treat Cabbage/OpenRSC code as inherited engine code, not as an external repo dependency.
- Remove obsolete worlds/configs only after MyWorld has first-class replacements.

## Current Shape

The first standalone shape keeps the proven source layout and promotes MyWorld commands to the repo root:

```text
Client_Base/
PC_Client/
server/
scripts/
  build-client.sh
  build-server.sh
  run-server.sh
  start-fresh.sh
  check.sh
  test.sh
tools/
  generators/
tests/
  myworld/
docs/
```

Archived inherited OpenRSC/Cabbage material lives under:

```text
docs/
  inherited-openrsc/
```

Later, if it is worth the churn, the source tree can be renamed into a more product-oriented layout:

```text
client/
  base/
  pc/
server/
tools/
tests/
docs/
```

## Completed Phase 1: Root Entrypoints

Root-level scripts provide the product workflow:

- `scripts/build-client.sh`
- `scripts/build-server.sh`
- `scripts/run-server.sh`
- `scripts/start-fresh.sh`
- `scripts/check.sh`
- `scripts/test.sh`

Status: complete. Client/server builds run from root scripts and root `make` targets. Server runs as MyWorld by default. Existing `dev/myworld` commands remain as compatibility wrappers.

## Completed Phase 2: MyWorld Seed Data

The active SQLite reset flow uses MyWorld seed data, not Cabbage seed data.

- Create or bless `server/inc/sqlite/myworld_seed.db`.
- Update `init-sqlite.sh` to clone `myworld_seed.db` into `myworld_dev.db`.
- Update docs and tests to refer to MyWorld seed data, not Cabbage baseline data.

Status: complete. Active SQLite files under `server/inc/sqlite/` are `myworld_seed.db` and local `myworld_dev.db`.

## Completed Phase 3: Promote Tools And Tests

MyWorld generators, benchmarks, and tests have moved out of `dev/myworld`.

Proposed moves:

- `dev/myworld/run-generators.py` -> `tools/generators/run-generators.py`
- `dev/myworld/generators.json` -> `tools/generators/generators.json`
- `dev/myworld/item-overrides` -> `tools/generators/item-overrides`
- `dev/myworld/npc-overrides` -> `tools/generators/npc-overrides`
- `dev/myworld/test-*.py` -> `tests/myworld/`
- `dev/myworld/test-*.sh` -> `tests/myworld/`

Status: complete. Generator manifest paths are root-relative, tests run from `scripts/test.sh`, and `dev/myworld` is compatibility-only.

## Completed Phase 4: Make MyWorld The Only Shipped World

Inherited root docs, legacy launchers, old server config presets, old SQLite seeds, and inherited Makefile recipes have been archived.

Status: complete for the active server workflow. Active `server/*.conf` files are `myworld.conf` and `connections.conf`. Active SQLite seeds are MyWorld-only. `docs/myworld/active-workspace.md` defines the active, compatibility, archived, and still-under-review areas.

## Optional Phase 5: Source Renames

After the root scripts, seed data, tools, and tests are stable, consider physical directory renames.

This phase should be done only after a clean compile/test baseline because it touches many paths.

Success criteria:

- `client/` and `server/` names describe the shipped product.
- Ant or a replacement build system uses the new paths.
- No runtime behavior changes are mixed into the rename.

## Current Status

- Root README and root `make` targets are MyWorld-first.
- Direct server Ant runs default to `server/myworld.conf`.
- MyWorld generators live under `tools/generators`.
- MyWorld benchmark helpers live under `tools/benchmarks`.
- MyWorld validation lives under `tests/myworld`.
- Inherited docs, launchers, configs, seeds, and Make recipes are archived under `docs/inherited-openrsc/`.
- The active workspace boundary is documented in `docs/myworld/active-workspace.md`.
- `test-standalone-layout.py` guards the current standalone layout.

No further major migration work is required before returning to normal MyWorld content and gameplay development. Remaining cleanup candidates are limited to optional client target decisions and future source renames.
