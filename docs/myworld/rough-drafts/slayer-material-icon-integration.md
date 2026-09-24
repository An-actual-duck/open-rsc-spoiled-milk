# Slayer material and consumable icon integration

Implemented 2026-09-24 in the content worker. No server restart or deployment.

- Eleven approved components: Cockatrice Feathers, Slimey Residue, Sticky
  Saliva Gland, Cockatrice Eye, Frozen Tear, Terror Fang, Leach Tongue,
  Lightning Horn, Abyssal Vertibrae, Abyssal Rib and Ectoplasm.
- Five protection families: Slime Solvent, Eye Drops, Wax earplugs, Dog Treats
  and Static discharge wipe. Every 3/2/1-use item uses its family's approved
  icon; counts, actions, prices and effects are unchanged.
- Six raw/tanned pairs from `hide-leathers-v2`: giant frog, naga, terror dog,
  bloodveld, dark beast and ugthanki. Leather retains the approved darker
  tanning treatment. Banshee hide/leather/armor remain retired and untouched.

The packaging script copies 28 approved native PNGs byte-for-byte into
`dev/myworld/assets/sprites/items/inventory-ground/resources`. Its manifest
records all 38 item IDs and native visible dimensions. Client definitions use
external PNG references with those dimensions and no extra palette tint;
inventory and ground rendering share the same image. Existing noteability,
stackability, names and generated prices remain intact.

No new artwork, resizing or recoloring was performed. Existing equipment
icons, held frames and worn colors are unchanged.

## Verification

- `node tests/myworld/test-slayer-material-icons.cjs`: every installed/package
  byte equals the accepted source; all 38 actual runtime definitions, charge
  variants, zero masks, prices and note forms; actual image decoding from the
  packaged JAR with no development asset directory available.
- `python3 tests/myworld/test-slayer-component-items.py`: effective merged
  server/client definitions and prices, material identity and flags, note
  forms, preserved retired Banshee hide.
- Existing `tests/myworld/test-*-assets.cjs` checks guard all previously
  accepted unique equipment/armor/poison artwork.
- Server Slayer rewards, component drops and gimmick shops tests cover the
  unchanged behavior and corrected tier-8 bow/tier-9 staff recipes/stats.

The staged tower roster, physical map gates, broader terrain regression issue
and final manager integration/release remain separate work.
