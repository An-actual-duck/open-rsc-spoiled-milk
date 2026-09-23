# Dagger of Terror visual integration

Local test implementation, 2026-09-23; not deployed to public server.

- Existing item 3353 now uses appended appearance 1092 (`daggerofterror`).
- Approved 18 custom-dagger bone frames are installed under
  `dev/myworld/assets/sprites/equipment/dagger-of-terror/numbered/`.
- Existing external combat equipment loader preserves canonical dagger
  anchors (identical to its sword anchor arrays), 64x102 movement bounds and
  84x102 combat bounds. No silhouette or grip-gap edits.
- Inventory/ground uses `external-png:dagger-of-terror-icon@28x19` with neutral
  tint, preserving the approved icon's visible size. Held tint is neutral too.
- Client build packages the icon and all frames; custom-sprite mode is required,
  consistent with the existing external equipment pipeline.
- Item stats, crafting, shop prices and effects are unchanged. Level 30 melee
  requirement remains. Poisoned item 3354 is not converted in this pass; its
  held poisoned variant still needs separate preparation.

Verification: build client/server, run DaggerOfTerrorVisualAudit.java against
the client JAR and `node tests/myworld/test-terror-dagger-assets.cjs`.
In-game review remains: inventory/ground size, equip, eight viewing directions,
walking and both combat facings, including grip occlusion. Test from this worker
checkout's rebuilt client/server until normal manager integration is performed.
