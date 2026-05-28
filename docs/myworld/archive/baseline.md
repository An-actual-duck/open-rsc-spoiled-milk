# MyWorld Baseline

## Purpose

`MyWorld` is the local development world for this fork.

It is explicitly a `PvM-only` server. Player-vs-player combat, duels, and
OpenPK-style gameplay are not part of the intended MyWorld feature set.

It is derived from `RSC Cabbage`, but it is not intended to modify the stock
`cabbage` world or database directly.

## Baseline choices

- Base ruleset: `RSC Cabbage`
- Server config: [`server/myworld.conf`](/home/justin/Core-Framework/server/myworld.conf)
- Local SQLite database: `server/inc/sqlite/myworld_dev.db`
- Local SQLite seed: `server/inc/sqlite/myworld_seed.db`
- World database name: `myworld_dev`
- Default local branch: `main`
- Default push remote: `openrsc-fun`

## Operational choices

- Keep inherited non-MyWorld SQLite seeds archived under `docs/inherited-openrsc/sqlite-seeds/` as reference data
- Reset local dev state by cloning the MyWorld seed database
- Use separate ports from stock Cabbage
- Keep MyWorld scoped to PvM rather than carrying duel / wilderness PvP
  obligations forward
- Disable external-facing dev friction where possible
  - registration limits off
  - pcap logging off
  - monitor-online off
  - Discord logging off

## Content conventions

Use these locations for fork-specific work:

- Plugins: `server/plugins/com/openrsc/server/plugins/custom/myworld`
- World/location files: `server/conf/server/defs/locs/MyWorld*.json`
- Project notes: `docs/myworld`

Avoid mixing new fork-specific work into unrelated stock content unless there is
no practical alternative.

Authoritative gameplay-rule references:

- [important-game-changes-that-must-be-adhered-to.md](/home/justin/Core-Framework/docs/myworld/important-game-changes-that-must-be-adhered-to.md)
- [retired-replaced-and-repurposed-items.md](/home/justin/Core-Framework/docs/myworld/retired-replaced-and-repurposed-items.md)
- [README.md](/home/justin/Core-Framework/docs/myworld/README.md)

See `docs/myworld/architecture-map.md` for the current authored-data ->
generated-defs -> runtime-loading flow.

## Near-term goals

1. Keep the MyWorld gameplay baseline runnable locally.
2. Add isolated custom content in the `myworld` namespace.
3. Establish a repeatable edit-reset-run loop.
4. Evolve toward cleaner separation from stock Cabbage over time.
