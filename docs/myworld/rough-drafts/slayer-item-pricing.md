# Slayer Tower item base values

Approved unique equipment values: Shield of Mobility 40,000; Sullen Pendant 100,000; Dagger of Terror and poisoned variant 150,000 each; Leaching Bow 300,000; Thunder Spire Staff 450,000; Abyssal Whip 600,000 gp.

Only these completed equipment items receive acquisition/rarity premiums. Authored source: `tools/generators/item-overrides/51-slayer-economy.json`, generating matching server/client overrides. Slayer currency costs and ingredient recipes are unchanged.

## Ordinary materials

| Material | Base gp |
|---|---:|
| Giant frog hide | 35 |
| Naga hide | 385 |
| Terror dog / Bloodveld hide | 980 |
| Dark beast hide | 2,100 |
| Ugthanki hide | 175 |
| Cockatrice Feathers | 5 |
| Slimey Residue | 50 |
| Sticky Saliva Gland | 20 |
| Cockatrice Eye | 30 |
| Frozen Tear | 250 |
| Terror Fang | 400 |
| Leach Tongue | 600 |
| Lightning Horn | 1,200 |
| Abyssal Vertibrae | 600 |
| Abyssal Rib | 1,500 |
| Ectoplasm | 60 |

Hides match existing raw materials for each armor tier. Existing six families' tanned material and armor values already match their tiers and are retained. Retired/hidden Banshee hide and armor are not reintroduced or repriced. Existing bones and Demon ash remain unchanged.

## Gimmick consumables

| Item | 3 uses | 2 uses | 1 use |
|---|---:|---:|---:|
| Slime Solvent | 60 | 40 | 20 |
| Eye Drops | 120 | 80 | 40 |
| Wax earplugs | 180 | 120 | 60 |
| Dog Treats | 240 | 160 | 80 |
| Static discharge wipe | 300 | 200 | 100 |

These are coin base values, not changes to Slayer-point shop purchase costs. Trading flags remain unchanged.

## Consumers and balance

SpellHandler pays 40% for low alchemy and 60% for high alchemy. The Alchemy Bangle likewise pays 60%, and its auto-loot conversion threshold is 1,000 gp proceeds. Each individual unique component is below that threshold (horn 720, rib 900 high-alch gp), avoiding accidental auto-conversion of single component drops. This is not an alchemy prohibition; stack value can cross the threshold. Completed equipment intentionally has the approved higher salvage values (whip high alch 360,000). Shop payouts continue using existing pricing rules; no shop, recipe, stat, or rarity changes.
