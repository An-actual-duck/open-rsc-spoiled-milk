# Layered Maps

This folder is the non-mutating foundation for the signed layered-map
capability. Slice 1 provides:

- the `signed-layered-v1` coordinate contract;
- immutable Java 8 reference values;
- the checked `legacy-packed-y-v1` codec; and
- a read-only preflight for the first supported repository adapter.

It does **not** convert maps, change runtime coordinates, edit archives, modify
player data, launch a server, or export into a game.

## Preflight

From the repository root:

```bash
./tools/layered-maps/layered-maps.sh preflight
```

The launcher compiles into the ignored `tools/layered-maps/build/` directory
and writes deterministic reports into the ignored
`tools/layered-maps/workspace/preflight/` directory:

- `preflight.json` for tools and AI analysis;
- `preflight.md` for a map author.

The command reads the target repository and writes only to its selected
workspace. The CLI can also be compiled independently and pointed at an
external workspace:

```bash
java -cp classes com.openrsc.layeredmaps.LayeredMapsCli preflight \
  --root /path/to/repository \
  --workspace /path/to/isolated/workspace
```

## Supported adapter

Slice 1 recognizes `spoiled-milk-repository-v1`. It requires the maintained
server/client build markers, `server/myworld.conf`, and byte-identical server
and client `Custom_Landscape.orsc` archives. It inventories location files,
transition definitions, and Java sources containing coordinate-related signals
as migration candidates. Candidate status is intentionally conservative: it
means a later converter must inspect the source, not that preflight has parsed
or rewritten it.

Unknown or inconsistent targets are refused with an actionable error.
