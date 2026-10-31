# Production Blight sigils

The owner approved Thorn in `../blight-sigil-candidate`. Stone art is preserved
pixel-for-pixel. Silver changes only the existing eight-tone substrate palette;
the44 glyph pixels and transparency are identical. The sources remain28×25,
with the same24×21 nearest-neighbor silver presentation as existing sigils.

`author.lua` opens the approved candidate `.aseprite` documents and saves the
four editable production documents/PNGs. Run with Aseprite batch scripting and
`--script-param root=/absolute/Core-checkout`. The manifest records their hashes,
ordinary item definitions/selectors, old PNGs and unchanged native archives.

Production items3426–3429 use the existing game-owned external-PNG sprite path
and native fallback443/134. They are discovered from the selected server/client,
not installed into the toolkit. Validate with
`python3 tests/myworld/test-blight-sigil-assets.py` and the sigil item asset gate.
