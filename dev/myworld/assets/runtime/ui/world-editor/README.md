# World Editor Icon Contract

World-editor icons are replaceable client assets. Each icon must:

- be a transparent RGBA PNG on an exact 24x24 pixel canvas;
- use the stable lowercase kebab-case filename listed in the editor plan;
- remain legible at native size against light and dark terrain;
- be drawn at native size with nearest-neighbor behavior; and
- have redistribution rights recorded in `CREDITS.md` before release.

The client loads and decodes each icon once, caches the resulting sprite, and
uses button backgrounds, borders, tint, or badges to communicate interaction
state. Missing, malformed, incorrectly sized, or non-alpha PNGs activate a
labeled code-drawn fallback without preventing editor use.

Do not add final artwork until its source and redistribution terms are known.

Author files live in this directory. The client build packages them at
`myworld-assets/ui/world-editor/` so a player archive never depends on the
source checkout.

Brush size uses two state-specific source files, `tool-brush-1x1.png` and
`tool-brush-3x3.png`. They occupy the same dock position and swap when the
configured footprint changes.

The dock arrow also uses two state-specific files. `toolbar-collapse.png`
points up while the dock is open; `toolbar-expand.png` points down while it is
collapsed. Terrain is entered through the active Brush control, and Rotate is
presented inside Scenery, so neither needs a separate dock icon.

## Definition Catalog Contract

`definition-catalog-v1.tsv` is a generated, editor-only search and naming
index for scenery and boundary definitions. It is packaged beside the icons,
but it is not artwork and is not covered by the icon license table.

Regenerate the catalog and its audit from authoritative server definitions,
behavior metadata, ID constants, and reviewed overrides with:

```bash
python3 tools/world-builder/generate-definition-catalog.py
```

The client accepts a semantic row only while its ID and canonical name still
match the loaded gameplay definition. Missing, malformed, or stale catalog
data falls back to the runtime name without changing gameplay behavior.
