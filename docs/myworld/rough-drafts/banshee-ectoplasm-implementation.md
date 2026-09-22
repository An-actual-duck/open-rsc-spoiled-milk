# Banshee material and armor withdrawal

Implemented 2026-09-22. No deployment, database migration or final artwork.

## Runtime contract

- Banshee (865) now drops one **Ectoplasm (3399)** instead of Banshee hide.
  This is the base guaranteed quantity; existing guaranteed-loot bonuses,
  ownership and collection behavior still apply. No Slayer task is required.
- Ectoplasm is tradable and stackable, has no use command, wear requirement,
  crafting/assembly recipe or current gameplay effect, and has a provisional
  value of zero. It is reserved for possible future spirit armor.
- Server/client definitions agree. The placeholder is the existing swamp-tar
  icon (`items:262`) tinted pale purple (`#B5A4CF`); final spritework is deferred.
- Ordinary bones, rune-weighted supplies and Frozen Tear's independent
  1/1,000 base drop chance are unchanged.
- Banshee hide (3334) no longer drops. It cannot be tanned into leather (3362),
  and that leather no longer exposes the five armor recipes (3363–3367).
  Stale production-interface requests also fail without consuming materials.
- Existing hide, leather and armor definitions/IDs, equipment stats, note
  forms and trading rules remain unchanged. Nothing is removed or converted
  on accounts. This withdrawal does not introduce a new grandfathering rule.

Quantity, stacking and placeholder tint are implementation choices for the
approved unused collectible. Six retained new leather families remain active:
giant frog, naga, terror dog, bloodveld, dark beast and Ugthanki. Broader
old-family retirement and optional boss tasks remain separate work.

## Verification

- `test_slayer_component_drops`: exact baseline identity/quantity, real Banshee
  ground delivery without old hide, unchanged bones/rare tear, MyWorld gating.
- Ordinary Slayer loot fixture: supplies, rare equipment and components coexist.
- `test_slayer_leather`: all six retained families craft normally; Banshee
  tanning/menu/recipe paths are absent, stale crafting requests fail, old
  possessions remain readable/equippable and rejected requests spend nothing.
- `tests/myworld/test-client-runtime-item-definitions.py`: rebuilt client
  resolves the complete server catalog through 3399.
- `tests/myworld/test-slayer-component-items.py`: matching Ectoplasm identity,
  description, inert flags, stacking and placeholder sprite/tint; old material
  definitions and note forms retained.
