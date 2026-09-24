# Thunder Spire Staff visual integration

Item 3351 uses one-based appearance 1095, resolving to animation index 1094,
`thunderspirestaff`. Neutral tint preserves the generated brown wood, natural
horn, and leather straps. No electric effect is present on held frames.
The approved inventory/ground icon retains electricity at native 34x30 visible bounds.

All 18 held frames use standard staff offsets, cropped dimensions and 64x102
movement / 84x102 combat bounds. The generation was registered separately
above and below the grip to the original staff segment bounds using nearest
sampling. This is a new generated held asset, not pixel-identical staff geometry;
live user testing is still required for its grip and attack readability.
The packaging script records this registration in the candidate directory.

No combat stats, recipe, requirements, or proc behavior changed in this visual pass.
Tests: `test-thunder-spire-assets.cjs` and `DaggerOfTerrorVisualAudit.java`.
