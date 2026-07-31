# World Builder Definition Catalog

This is a generated audit for World Builder 2's editor-only definition
catalog. Regenerate it together with
`dev/myworld/assets/ui/world-editor/definition-catalog-v1.tsv` by running:

```bash
python3 tools/world-builder/generate-definition-catalog.py
```

The catalog does not rename gameplay definitions or alter IDs, maps, saves,
protocols, commands, or server behavior. It gives the Builder concise labels
and searchable metadata derived from the authoritative definitions, behavior
tables, and active ID constants. Curated corrections live in
`tools/world-builder/definition-label-overrides.json`.

## Coverage

- Scenery: 1332 definitions; 135 repeated-name groups covering 1008 rows.
- Boundaries: 214 definitions; 13 repeated-name groups covering 173 rows.
- Scenery label sources: `behavior:fishing` 8, `behavior:harvesting` 11, `behavior:mining` 26, `behavior:runecrafting` 13, `behavior:woodcutting` 7, `canonical` 324, `constant` 705, `fallback-id` 48, `model` 189, `override` 1.
- Boundary label sources: `canonical` 40, `constant` 168, `model` 5, `override` 1.
- Explicit ID fallback rows still needing semantic review: 48.

Equivalent IDs may intentionally share a semantic label. Every editor context
reference still includes its numeric ID, and the future visual browser can
distinguish model variants without inventing unsupported gameplay meaning.

## Representative Labels

| Kind | ID | Canonical | Builder label | Source |
| --- | ---: | --- | --- | --- |
| scenery | 17 | Chest | Chest (generic, open) | `constant` |
| scenery | 104 | Rock | Rock (tin) | `behavior:mining` |
| scenery | 105 | Rock | Rock (tin) | `behavior:mining` |
| scenery | 193 | fish | Fishing spot (net / bait) | `behavior:fishing` |
| scenery | 223 | Ladder | Ladder (Mining Guild, down) | `constant` |
| scenery | 1190 | Mysterious Ruins | Mysterious Ruins (air) | `behavior:runecrafting` |
| boundary | 8 | Door | Door (Gray Bricks) | `constant` |
| boundary | 101 | Fence with loose pannels | Fence (loose panels) | `override` |

## Unresolved Legacy Variants

These rows have neither authoritative behavior metadata, an active semantic
constant, a useful model distinction, nor a curated override. Their labels
therefore expose the stable ID instead of guessing.

| Kind | ID | Canonical | Builder label |
| --- | ---: | --- | --- |
| scenery | 2 | Well | Well (variant #2) |
| scenery | 3 | Table | Table (variant #3) |
| scenery | 11 | Range | Range (variant #11) |
| scenery | 26 | fountain | Fountain (variant #26) |
| scenery | 47 | Bookcase | Bookcase (variant #47) |
| scenery | 50 | anvil | Anvil (variant #50) |
| scenery | 55 | sacks | Sacks (variant #55) |
| scenery | 118 | furnace | Furnace (variant #118) |
| scenery | 147 | Cauldron | Cauldron (variant #147) |
| scenery | 294 | beehive | Beehive (variant #294) |
| scenery | 461 | Waterfall | Waterfall (variant #461) |
| scenery | 527 | climbing rocks | Climbing rocks (variant #527) |
| scenery | 943 | dwarf multicannon | Dwarf multicannon (variant #943) |
| scenery | 968 | Hole | Hole (variant #968) |
| scenery | 969 | Hole | Hole (variant #969) |
| scenery | 981 | Ladder | Ladder (variant #981) |
| scenery | 986 | crate | Crate (variant #986) |
| scenery | 987 | crate | Crate (variant #987) |
| scenery | 1004 | Bookcase | Bookcase (variant #1004) |
| scenery | 1021 | Ladder | Ladder (variant #1021) |
| scenery | 1029 | climbing rocks | Climbing rocks (variant #1029) |
| scenery | 1039 | crate | Crate (variant #1039) |
| scenery | 1040 | crate | Crate (variant #1040) |
| scenery | 1041 | barrel | Barrel (variant #1041) |
| scenery | 1054 | crate | Crate (variant #1054) |
| scenery | 1055 | crate | Crate (variant #1055) |
| scenery | 1064 | signpost | Signpost (variant #1064) |
| scenery | 1074 | cupboard | Cupboard (variant #1074) |
| scenery | 1075 | sacks | Sacks (variant #1075) |
| scenery | 1076 | sacks | Sacks (variant #1076) |
| scenery | 1088 | signpost | Signpost (variant #1088) |
| scenery | 1090 | Bookcase | Bookcase (variant #1090) |
| scenery | 1118 | dwarf multicannon | Dwarf multicannon (variant #1118) |
| scenery | 1127 | beehive | Beehive (variant #1127) |
| scenery | 1130 | fountain | Fountain (variant #1130) |
| scenery | 1132 | barrel | Barrel (variant #1132) |
| scenery | 1133 | barrel | Barrel (variant #1133) |
| scenery | 1134 | barrel | Barrel (variant #1134) |
| scenery | 1135 | barrel | Barrel (variant #1135) |
| scenery | 1136 | barrel | Barrel (variant #1136) |
| scenery | 1137 | barrel | Barrel (variant #1137) |
| scenery | 1139 | sacks | Sacks (variant #1139) |
| scenery | 1144 | crate | Crate (variant #1144) |
| scenery | 1149 | cupboard | Cupboard (variant #1149) |
| scenery | 1150 | sacks | Sacks (variant #1150) |
| scenery | 1161 | Table | Table (variant #1161) |
| scenery | 1187 | Ladder | Ladder (variant #1187) |
| scenery | 1242 | Rowboat | Rowboat (variant #1242) |

## Naming Policy

1. Preserve unique canonical names unless a reviewed spelling/meaning override exists.
2. Prefer functional results from mining, fishing, woodcutting, harvesting, and runecrafting metadata.
3. Use active constants for location, quest, state, and direction context.
4. Use technical model qualifiers only when they add a real distinction.
5. Fall back to the stable numeric ID; never fabricate a semantic distinction.
