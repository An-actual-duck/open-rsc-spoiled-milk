# Moonsilver sigil artwork

The owner approved the revised three-recess mould and the fourteen sprites.
`manifest.json` uses paths relative to the repository root. Every recorded file
exists at that maintained path and its SHA-256 is checked by the asset gate.

The original stone/silver PNGs are unchanged. Moonsilver sigils recolor only the
eight silver substrate tones; existing grey carved and gold charged glyphs are
preserved. The bar keeps the exact native metal-bar silhouette, alpha and48×32
canvas. Blanks and carved sigils retain the28×25 source and 24×21 presentation.
The new mould is a48×32 native canvas with three recessed wells, shaded for
upper-left lighting. The editable sources are in `aseprite/`.

Reproduce from a Core worktree using Aseprite 1.3.18.5, replacing the two absolute
paths with the current worktree and a **fresh output folder**:

```bash
mkdir -p /absolute/path/to/fresh-output/png /absolute/path/to/fresh-output/aseprite
aseprite -b --script-param root=/absolute/path/to/Core-Framework \
  --script-param out=/absolute/path/to/fresh-output \
  --script dev/myworld/moonsilver-sigil-production/author.lua
```

Compare the resulting `png/`, `aseprite/` and two review images against the
maintained files in the manifest. Copying the PNGs into the game-owned
`resources/sigils` folder is an installation step; authoring does not overwrite
those source inputs. The standalone editor discovers these assets from the
selected game's definitions and client selectors, with no editor-owned roster.
