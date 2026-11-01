# Practice-session icon corrections

The owner approved these native-size revisions on November 1, 2026:

- Viscous weapon coating: a conventional glass potion bottle with murky teal liquid.
- Sticky Saliva Gland: a rounded organ with an asymmetric slime layer and drips, without an eye's iris/pupil.

Aseprite 1.3.18.5 is the authoring tool. `author.lua` derives the coating from
classic potion reference `consumables/potions-and-bottles/id-02198.png`, retaining
its contour, stopper and lighting. The gland is new native pixel art; the round
monster-organ family supplied the scale reference. Original library artwork and
the Cockatrice Eye are unchanged. The old coating is retained in
`art/reference/viscous-coating-before.png` for the before/after sheet.

Reproduce from the repository root (create the output folders first):

```sh
aseprite -b --script-param root=. --script-param out=dev/myworld/practice-feedback/art --script dev/myworld/practice-feedback/author.lua
```

The editable `.aseprite` and exported PNG candidates are under `art`. Production
PNGs live in `dev/myworld/assets/sprites/items/inventory-ground`. The coating
catalog generator consumes the authored PNG rather than reconstructing its old
bottle. Its pre-existing one-shot ID-collision guard is unchanged.

The gland selector uses `@16x18` so the external-asset loader keeps the approved
native footprint rather than enlarging it to fill an inventory slot. The coating
retains its existing default icon presentation. No gameplay values, item IDs,
monster drops or recipe ingredients changed. The editor discovers the assets
through game-owned definitions as usual.

`tests/myworld/test-practice-item-art.py` checks exact maintained bytes, native
Aseprite author replay and export, packaged resources and actual client loading
in both classic/custom sprite modes from a directory without development assets.
