# Surface minimap

`scripts/build-surface-minimap.py` bakes the active World Builder package
into `assets/runtime/ui/minimap/surface.bin`. It uses the authored terrain
and the tile definitions from this repository. The generated colors simplify
the terrain for a small north-up display.

The atlas contains global surface sectors and the approved landmarks in
`landmarks.json`. Each landmark names a symbol, a hover label, and the
catalog ID and world tile of its NPC spawn or scenery placement. Add shops
by current stock. Use one marker for vendors sharing a counter and for
duplicate workstations at the same facility. Leave compact rows grouped by
town. The order within a town gives banks and quest starts first choice of
icon space.

The baker validates each anchor against the installed surface placements.
If nearby icons would overlap, it shifts the later icon a few tiles. The
client draws a connector and uses the original tile for click-to-walk.

The atlas includes the source manifest hash. The baker verifies terrain and
placement checksums before writing it. Regenerate it after changing
landmarks or installing an approved map package. Use the same command with
`--check` to verify the runtime file.
