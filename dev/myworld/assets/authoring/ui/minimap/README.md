# Minimap atlas

`scripts/build-surface-minimap.py` bakes the installed World Builder package
into `assets/runtime/ui/minimap/`. The terrain colors use the tile and boundary
definitions from this repository.

`landmarks.json` holds the named placements from the three-town preview.
`landmark-catalog.json` extends coverage to the rest of the world. Its shop
and workstation tables identify game definitions by catalog ID and name the
icon and hover label to use. Shop categories follow the vendor's current
stock. The placement table pins quest starts, banks, and mill entrances to
specific NPC or scenery coordinates on their own floor. Use one marker for
bankers sharing a counter.

The baker validates curated anchors against the installed placements. It
groups repeat workstations within the same facility and nudges nearby icons
to keep them legible. The client draws a connector for shifted icons and uses
the original tile for click-to-walk. The marker order in the preview towns
preserves priority for banks and quest starts.

Each floor has its own atlas and loads when the minimap shows that floor.
The atlas includes the source manifest hash. The baker verifies terrain and
placement checksums before writing it. Regenerate it after changing
landmarks or installing an approved map package. Use the same command with
`--check` to verify the runtime files.
