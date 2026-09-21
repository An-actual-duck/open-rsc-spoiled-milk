# New Slayer leather foundations

Updated: 2026-09-21. Baseline production and armor implemented; special set bonuses
await owner designs. No deployment, world placement, existing-family retirement
or player-item migration.

Design authority: [hide/leather scope](slayer-hide-and-leather-overhaul.md).
This implements six leather families, not eight. Cockatrice Feathers and Slimey
Residue remain their existing component drops and create no leather armor.

## Tier selection and production

Tiers follow comparable existing source combat levels, not tower floor number.
These are ordinary leather tiers; existing boss-specific exceptions are untouched.

| Source | Combat level | Tier | Existing comparison | Hide → leather | Armor IDs | Crafting levels: coif/gloves/boots/chaps/cuirass |
| --- | ---: | ---: | --- | --- | --- | --- |
| Giant frog (863) | 20 | 2 | Unicorn (21) | 3333 → 3356 | 3357–3361 | 8 / 9 / 9 / 10 / 11 |
| Banshee (865) | 50 | 4 | Baby Blue Dragon (50) | 3334 → 3362 | 3363–3367 | 22 / 23 / 23 / 24 / 25 |
| Naga (866) | 60 | 5 | Moss Giant (62) | 3335 → 3368 | 3369–3373 | 30 / 31 / 31 / 32 / 33 |
| Terror dog (867) | 75 | 6 | Lesser Demon (79) | 3336 → 3374 | 3375–3379 | 38 / 39 / 39 / 40 / 41 |
| Bloodveld (868) | 85 | 6 | Lesser Demon (79) | 3337 → 3380 | 3381–3385 | 38 / 39 / 39 / 40 / 41 |
| Dark beast (869) | 105 | 7 | Blue Dragon (105) | 3338 → 3386 | 3387–3391 | 46 / 47 / 47 / 48 / 49 |

All six families use the existing tanning rack, needle/thread crafting interface
and batch production. Tanning converts one raw hide into one leather, requiring
the base Crafting level in the table and granting `5 × (tier + 1)` XP. Armor
uses 1/2/2/3/4 leather respectively, two thread uses per leather, and the existing
`12 × tier × leather count` crafting XP. Every full set costs twelve leather
and twenty-four thread uses.

Armor is tradable, nonstackable and noteable with the usual five equipment slots.
As with existing leather armor, there is no wear-level requirement; Crafting
requirements apply to making it. Finished-item and processed-leather base prices
match the selected existing-tier comparison. Existing raw hide definitions and
their provisional prices are unchanged.

## Defense budgets

Each piece gets `ceil(tier × material cost × 0.9)` total style-defense points,
matching existing non-boss leather. Split proportionally to the monster's current
explicit melee/ranged/magic defenses. Round down first, then assign remaining
points by largest fractional remainder (ties: melee, ranged, magic). This
preserves the tier budget rather than independently rounding each style upward.
The complete-item definitions explicitly include zero defenses where necessary
and equipped-total tests verify that legacy fallback adds no extra defense.
The complete-definition loader now preserves literal zero modern stats rather
than converting them to the sentinel used only during partial-patch merging.
Patch merging is unchanged; a regression also checks the Sullen Pendant's zero
offense stats.

| Piece | Melee | Ranged | Magic | Total |
| --- | ---: | ---: | ---: | ---: |
| Giant-frog-hide coif | 0 | 1 | 1 | 2 |
| Giant-frog-hide gloves | 1 | 1 | 2 | 4 |
| Giant-frog-hide boots | 1 | 1 | 2 | 4 |
| Giant-frog-hide chaps | 1 | 2 | 3 | 6 |
| Giant-frog-hide cuirass | 1 | 3 | 4 | 8 |
| Banshee-hide coif | 2 | 1 | 1 | 4 |
| Banshee-hide gloves | 3 | 3 | 2 | 8 |
| Banshee-hide boots | 3 | 3 | 2 | 8 |
| Banshee-hide chaps | 4 | 4 | 3 | 11 |
| Banshee-hide cuirass | 5 | 5 | 5 | 15 |
| Naga-hide coif | 0 | 2 | 3 | 5 |
| Naga-hide gloves | 1 | 3 | 5 | 9 |
| Naga-hide boots | 1 | 3 | 5 | 9 |
| Naga-hide chaps | 2 | 5 | 7 | 14 |
| Naga-hide cuirass | 2 | 7 | 9 | 18 |
| Terror-dog-hide coif | 2 | 2 | 2 | 6 |
| Terror-dog-hide gloves | 4 | 4 | 3 | 11 |
| Terror-dog-hide boots | 4 | 4 | 3 | 11 |
| Terror-dog-hide chaps | 6 | 6 | 5 | 17 |
| Terror-dog-hide cuirass | 8 | 7 | 7 | 22 |
| Bloodveld-hide coif | 2 | 2 | 2 | 6 |
| Bloodveld-hide gloves | 4 | 4 | 3 | 11 |
| Bloodveld-hide boots | 4 | 4 | 3 | 11 |
| Bloodveld-hide chaps | 6 | 6 | 5 | 17 |
| Bloodveld-hide cuirass | 8 | 7 | 7 | 22 |
| Dark-beast-hide coif | 3 | 2 | 2 | 7 |
| Dark-beast-hide gloves | 5 | 4 | 4 | 13 |
| Dark-beast-hide boots | 5 | 4 | 4 | 13 |
| Dark-beast-hide chaps | 7 | 5 | 7 | 19 |
| Dark-beast-hide cuirass | 9 | 8 | 9 | 26 |

Ratios are Giant frog 10:20:35, Banshee 40:40:40, Naga 20:65:95,
Terror dog 55:55:55, Bloodveld 30:30:30 and Dark beast 120:100:120.
Bloodveld's low absolute NPC defenses do not reduce its armor's tier budget;
its equal proportions produce a balanced set.

## Deliberately absent special effects

The new item IDs do not enter any existing full-set effect detector. No frog
poison, banshee wail, naga effect, frenzy, lifesteal or lightning armor bonus has
been invented. Normal leather equipment rules/penalties still apply. Existing
sets retain their behavior. Special bonuses remain a separate owner decision,
not a completed feature claimed by this implementation.

## Placeholder visuals

Inventory icons reuse standard leather/coif/gloves/boots/chaps/cuirass sprites
69/5/17/223/590/7, tinted to match each existing raw hide. Worn appearance IDs
temporarily reuse the comparison armor family. Sharing an appearance does not
share its set effect. Final icons and worn visuals belong to the later art pass;
no new raster art was generated here.

## Verification

- `ant test_slayer_leather`: real definitions and plugins, all six tanning
  routes, below-level rejection, all thirty actual crafted outputs, crafting
  interface requirements, material/thread consumption, wear slots, defense
  allocation, no copied set effects, and rejected feather/residue recipes.
- Client runtime catalog audit through item 3391.
- Existing leather-defense, boss-leather, leather-effect, production and Slayer
  component/reward regressions.

## Deferred work and ordering

Retirement/source auditing and grandfathering are deferred until the eight
new NPC additions and their sprite work are finished. Nothing in this pass
removes old drops, alters old equipment, binds holdings or changes death rules.
New armor bonuses still need designs. [Ordinary monster loot](slayer-ordinary-loot-implementation.md)
is implemented. Consumable shop
pricing/access, optional boss tasks and tower gatekeepers are separate remaining
content tasks; map placement remains the owner's job.
