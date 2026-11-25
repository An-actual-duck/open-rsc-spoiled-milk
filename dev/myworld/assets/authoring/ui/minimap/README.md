# Surface minimap

`scripts/build-surface-minimap.py` bakes the active World Builder package
into `assets/runtime/ui/minimap/surface.bin`. It uses the authored terrain
and the tile definitions from this repository. The generated colors simplify
the terrain for a small north-up display.

The atlas contains global surface sectors and a single marker at the
Lumbridge Bank Chest. It includes the source manifest hash and the baker
verifies the terrain and placement checksums before writing it. Regenerate it
after installing a new approved map package. Run the same command with
`--check` to verify the runtime file.
