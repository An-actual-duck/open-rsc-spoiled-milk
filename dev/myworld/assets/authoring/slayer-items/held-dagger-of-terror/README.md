# Dagger of Terror — held palette candidate

Full 18-frame set based on the custom `equipment:dagger`, not the generic
sword. This is a deterministic three-color palette swap requested by the owner.
No geometry changes or generation. Frames 00–14 are five movement groups of
three; 15–17 are the existing combat sequence. The client handles opposite
directions through mirroring, phase/position adjustments and layer order.

| Source | Bone palette sampled from approved inventory dagger |
| --- | --- |
| Blade highlight #d2d2d2 | Ivory #f4e7ce |
| Blade shade #a5a5a5 | Shaded bone #887165 |
| Yellow hilt #ffc932 | Bone #e6d2ab |

`source/` retains exact source PNGs. `frames/` contains matching cropped output;
use the original offsets and bounds from `manifest.json` when integrating.
`full-canvas/` restores those offsets for previews: movement 64x102, combat
84x102. Do not crop/recenter individual visible weapon fragments. Transparent
grip gaps and detached-looking pommel pixels are deliberate and preserved.

The attack poses are inherited from the original custom dagger; they have not
been compared with in-game combat screenshots or tested in game. The animated
preview is an isolated-frame review, not an exact reproduction of the client's
combat timing or layering. Integration must avoid a second metal tint over
these baked bone colors. No production definitions or archives changed.

Rebuild into a fresh output location with `../build-held-terror-dagger.cjs`,
passing `/home/justin/Core-Framework/output/sprite-png-export-20260702-154351`.
Existing PNG/source files are protected from overwrite. Bone geometry changes
and poisoned held variants are not part of this first pass.
