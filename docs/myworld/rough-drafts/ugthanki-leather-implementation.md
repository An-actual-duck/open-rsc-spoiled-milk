# Ugthanki leather and post-audit tier spread

Updated: 2026-09-21. Ugthanki foundation and Storage Hump bonus implemented
(food heals 20% more, nearest-integer rounding). See
[runtime rules](slayer-leather-effects-implementation.md) for stacking approved on 2026-09-22.
See [current set-bonus decisions](slayer-leather-set-bonuses.md#ugthanki-storage-hump).
Retirement and optional boss-task implementation remain separate pending work.

Subsequent [set-bonus decision](slayer-leather-set-bonuses.md) removes Banshee
from the future leather roster in favor of unused Ectoplasm. The original
27-family table below therefore becomes **26** after that pending change;
tier 4 retains baby dragon and Ugthanki. All tiers remain represented.

## Audit conclusion

The owner accepted rats/large rats, giant bats and animated axes as task monsters
that do not need a hide/carapace or replacement material. Do not add materials
or remove those assignments just to satisfy the earlier blanket coverage rule.
Ugthanki is the only new material/armor family requested by the completed audit.
Cockatrice Feathers and Slimey residue remain the two non-armor material
substitutes. The prior selected giant/ogre retirements and passive exceptions
remain unchanged; this addition does not execute their runtime changes.

## Production and definitions

Ugthanki (NPC 653, combat level 45) now drops one hide, guaranteed under the
existing custom-leather rules, regardless of whether its task is active. Its
existing raw meat and other drop behavior are preserved. Ordinary guaranteed
drop equipment modifiers continue to apply; no special loot path is introduced.

| Item | ID |
| --- | ---: |
| Ugthanki hide | 3392 |
| Ugthanki leather | 3393 |
| Ugthanki-hide coif | 3394 |
| Ugthanki-hide gloves | 3395 |
| Ugthanki-hide boots | 3396 |
| Ugthanki-hide chaps | 3397 |
| Ugthanki-hide cuirass | 3398 |

This is tier 4, using the same production/economy rung as baby-dragon and
banshee leather. Tanning requires Crafting 22 and gives 25 XP per hide, converting
one hide into one leather. Armor requires Crafting 22/23/23/24/25 respectively,
costs 1/2/2/3/4 leather and two thread uses per leather, and follows the existing
`12 × tier × leather count` XP rule. A complete set costs twelve leather.

All seven items are tradable, nonstackable and noteable. Armor has the usual
five leather slots and no wear-level requirement. Base prices: hide 0, leather
250, armor 225/450/450/675/900. No new recipes for the accepted exceptions.

Current Ugthanki effective defenses are **45 melee / 4 ranged / 4 magic**, from
legacy defense 45 and configured multipliers 1.0/0.1/0.1 rounded down by the NPC
runtime. Preserve that ratio within the normal per-slot tier-4 defense budget,
using the existing largest-remainder allocation and tie order:

| Piece | Melee | Ranged | Magic | Total |
| --- | ---: | ---: | ---: | ---: |
| Coif | 4 | 0 | 0 | 4 |
| Gloves | 7 | 1 | 0 | 8 |
| Boots | 7 | 1 | 0 | 8 |
| Chaps | 9 | 1 | 1 | 11 |
| Cuirass | 13 | 1 | 1 | 15 |
| Full set | 40 | 4 | 2 | 46 |

Equipment reads this set's explicit defenses, including intentional zeroes,
without falling back to legacy name-based armor bonuses. Other sets' stat
derivation is unchanged.

Placeholder inventory icons reuse standard hide/leather/armor sprites: sandy
hide `#B99A70`, darker leather/armor `#947654`. Worn appearance temporarily uses
the existing baby-dragon armor sprites. Shared appearance does not activate its
Blow Smoke bonus. Final art remains part of the later sprite pass.

## Original bonus-design backlog (superseded)

Originally: giant frog, banshee, naga, terror dog, bloodveld, dark beast, and
**Ugthanki**. Banshee armor has since been withdrawn from the plan; all six
retained families now have concepts in the linked set-bonus document.
Review these alongside retained armor families and the shared carapace cleansing
theme. No new set effect was invented or inherited by this addition.

## Resulting target spread

These are the current Crafting progression tiers, including the intentional
boss progression at tier 10/11. The table excludes the five families selected
for retirement; they are legacy collectibles, not future obtainable choices.

| Tier | Retained/new families | Count |
| ---: | --- | ---: |
| 1 | Cow, goblin | 2 |
| 2 | Unicorn, black unicorn, bear, scorpion carapace, giant frog | 5 |
| 3 | Wolf, spider carapace | 2 |
| 4 | Baby dragon, banshee, Ugthanki | 3 |
| 5 | Magic-spider carapace, naga | 2 |
| 6 | Demon, terror dog, bloodveld | 3 |
| 7 | Hellhound, blue dragon, green dragon, dark beast | 4 |
| 8 | Red dragon, black demon | 2 |
| 9 | Black dragon | 1 |
| 10 | King Black Dragon | 1 |
| 11 | Balrog, Elder Green Dragon | 2 |
| **Total** | | **27** |

Current code still has **32 craftable families** because retirement is not yet
implemented. The five additional current choices are giant (tier 3), ogre
(tier 4), moss giant and ice giant (tier 5), and fire giant (tier 7).
Their removal yields the 27-family target above without repricing/rebalancing
the retained families. All tiers retain at least one set.

## Verification

- `ant test_slayer_leather`: all seven new families, 35 actual crafted pieces,
  Ugthanki effective defenses and guaranteed off-task drops, preserved meat,
  tanning, level gates, thread costs, equipment stats and no borrowed effects.
- Build client, then `python3 tests/myworld/test-ugthanki-leather-items.py`:
  server/client identities, slot flags, colors, sprites and note forms.
- Existing component-item, leather-defense and boss-leather regression checks.

No task roster, account holdings, grandfathering, existing bonuses, map placement
or live deployment changed in this pass.
