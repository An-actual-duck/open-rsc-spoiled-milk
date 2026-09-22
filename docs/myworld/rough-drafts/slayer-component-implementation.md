# Slayer component implementation

Status: component definitions and component drops implemented; not deployed by this task.
Updated: 2026-09-20.

Update 2026-09-22: [Ectoplasm 3399 replaces Banshee hide drops](banshee-ectoplasm-implementation.md).
The original hide definition remains for existing holdings; the original
six-hide implementation below is historical for Banshee.

Source designs: [drops and assembly](slayer-special-drops-and-assembly.md),
[equipment](slayer-unique-equipment-design.md), [hide overhaul](slayer-hide-and-leather-overhaul.md).

## Implemented scope

- Six new raw hides, Cockatrice Feathers, Slimey Residue, and eight rare parts.
  Server identities live in `MyWorldItemId`; matching server and client item
  definitions preserve the owner's component spellings.
- Every item is tradable, non-wearable and has no use command or skill requirement.
  Feathers/residue stack; hides and rare parts use standard non-stackable,
  noteable behavior. Base prices are provisionally zero, avoiding a new
  shop/alchemy gold source until ordinary-loot and material pricing are balanced.
- Each hide-source NPC drops one raw hide and one ordinary Bones item.
  Cockatrices drop one Bones item and a uniformly selected 1–3 feathers.
  Abyssal demons drop one existing Demon ash (3112), no ordinary bones, and
  a uniformly selected 1–3 residue. These are base quantities: the existing
  death-necklace guaranteed-drop bonus and automatic loot handling still apply.
- Rare components use the approved independent base chances:
  gland/eye/vertebra 1/128; tear/fang/tongue 1/1000; horn/rib 1/2000.
  A successful unique roll supplies one component. Rib and vertebra can both
  drop on the same kill. No active task or new kill-credit policy is required.
- All new monster drop registrations are gated to MyWorld. Other NPCs' loot,
  existing hides and crafting recipes are unchanged.
- No Serpant's Tail, completed reward equipment, assembly purchases, coating,
  new leather recipes, set bonuses, task expansion or grandfathering migration.
  The later [ordinary loot pass](slayer-ordinary-loot-implementation.md) adds
  specialty supplies, coins, shared rare-table access and independent equipment
  drops without changing the component probabilities documented here.

## Temporary visual manifest

These reuse canonical existing sprites, with client picture-mask recolors.
No new generated artwork or external raster dependency is required. The hide
tints distinguish the six sources; the other icons are recognizable stand-ins
pending the final icon-art pass.

| Item ID | Item | Sprite | Tint | Stackable |
| --- | --- | --- | --- | --- |
| 3333 | Giant frog hide | `items:69` | `#378B83` | No; noteable |
| 3334 | Banshee hide | `items:69` | `#A58CAB` | No; noteable |
| 3335 | Naga hide | `items:69` | `#56753E` | No; noteable |
| 3336 | Terror dog hide | `items:69` | `#655951` | No; noteable |
| 3337 | Bloodveld hide | `items:69` | `#9B8065` | No; noteable |
| 3338 | Dark beast hide | `items:69` | `#393225` | No; noteable |
| 3339 | Cockatrice Feathers | `items:176` | `#8F9B5B` | Yes |
| 3340 | Slimey Residue | `items:262` | `#62798C` | Yes |
| 3341 | Sticky Saliva Gland | `items:116` | `#80A65B` | No; noteable |
| 3342 | Cockatrice Eye | `items:116` | `#B9A65B` | No; noteable |
| 3343 | Frozen Tear | `items:74` | `#99CDDD` | No; noteable |
| 3344 | Terror Fang | `items:145` | `#D6C5A0` | No; noteable |
| 3345 | Leach Tongue | `items:103` | `#AF6262` | No; noteable |
| 3346 | Lightning Horn | `items:145` | `#D8BC61` | No; noteable |
| 3347 | Abyssal Vertibrae | `items:20` | `#91A4B2` | No; noteable |
| 3348 | Abyssal Rib | `items:137` | `#8199AA` | No; noteable |
| 3399 | Ectoplasm | `items:262` | `#B5A4CF` | Yes |

Hides reuse the standard hide silhouette (69); feathers use 176; residue uses
swamp tar (262); gland/eye use eye of newt (116); tear uses a gem (74);
fang/horn use unicorn horn (145); tongue uses rope (103); vertebra/rib use
bone silhouettes (20/137). These icons do not confer the original items'
commands, recipes or equipment effects.

## Loot integration and modifiers

`SlayerComponentDrops` gives each rare part its own access table leading to a
rare reward table. Base access weights are 100 versus 100 × (denominator − 1),
preserving the exact base probability while retaining small potion-luck
increments through the existing integer-weight calculation.

This reuses `DropTable`'s existing rare-weight luck adjustment, wealth-ring
rare-access retry, and personal-loot contribution gate (including its existing
minimum contribution scaling). Each component is independent; a successful
component reward does not get another wealth retry for that same component.
Cosmic-necklace standard-loot retries suppress rare tables and therefore do
not supply these rare parts. No pity tracking or duplicate protection is added.

Materials join the existing guaranteed-drop delivery loop; rare parts join
the existing independent-drop delivery loop. Ground ownership, world item
filters, banking/collection equipment and drop logging stay on those paths.
The game's existing luck mechanics alter effective odds; the design rates
and average-kill estimates remain unmodified base rates, not guarantees.

## Verification

- `sh tools/vendor/apache-ant-1.10.5/bin/ant -f server/build.xml test_slayer_component_drops`:
  real definitions, all quantities, scripted roll boundaries, simultaneous
  rare parts, contribution gates, luck/wealth behavior, standard-retry exclusion,
  real NPC ground delivery, correct bones/ash, reload and MyWorld gating.
- `python3 tests/myworld/test-client-runtime-item-definitions.py`:
  builds the client and resolves the complete server catalog through item 3348.
- After building: `python3 tests/myworld/test-slayer-component-items.py`:
  16 matching material definitions, canonical sprite references/tints,
  tradability/stacking/note forms, inert flags, and no deferred Naga component.
- Existing item-ID and unrelated drop regression tests, plus `git diff --check`.

Server/client restarts and live deployment require a separate request.
