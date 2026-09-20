# Slayer special drops and unique equipment assembly

Status: owner-directed design notes; not implemented.
Updated: 2026-09-20.

Related: [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md),
[tower roster](slayer-tower-npc-integration.md), and
[hide/material overhaul](slayer-hide-and-leather-overhaul.md).

Reward behavior, approved component recipes and exact examine text are recorded
in [unique equipment design](slayer-unique-equipment-design.md).

## Owner direction

- Expand monster-specific drops with distinctive body parts/materials, in the
  spirit of existing spider and zombie eyes.
- Expand Slayer shops to exchange required monster parts **plus Slayer
  currency** for unique weapons, shields and jewelry. The fiction is paying for assembly, rather
  than simply finding a finished weapon on the monster.
- This is a shop purchase, not a new requirement to turn in materials for
  Slayer task completion, rank progress or points. Preserve the existing
  guild's kill-credit and progression rules.
- Where appropriate, monsters may drop a thematically suitable material in
  place of hide. Confirmed substitutes are **Slimey residue** from Abyssal
  demons and **Cockatrice Feathers** from cockatrices.
  This revises the earlier literal requirement that every task monster drop
  hide; do not force ordinary hide onto incompatible creature anatomy.

## Confirmed drop roster

This is the current reference for all eight tower monsters. Names below retain
the owner's supplied spelling; do not silently rename items during asset or
definition work. Combat levels/floors follow the tower plan.

| Floor | NPC (ID; combat level) | Baseline material/remains | Unique rare components |
| --- | --- | --- | --- |
| 1 | Giant frog (863; 20) | Hide + bones | Sticky Saliva Gland |
| 2 | Cockatrice (864; 35) | **1–3 Cockatrice Feathers** instead of hide, plus bones | Cockatrice Eye |
| 3 | Banshee (865; 50) | Hide + bones | Frozen Tear |
| 3 | Naga (866; 60) | Hide + bones | None for now; Serpant's Tail deferred and must not drop |
| 4 | Terror dog (867; 75) | Hide + bones | Terror Fang |
| 4 | Bloodveld (868; 85) | Hide + bones | Leach Tongue |
| 5 | Dark beast (869; 105) | Hide + bones | Lightning Horn |
| 6 | Abyssal demon (870; 125) | **1–3 Slimey residue** instead of hide, plus Demon ash; **no ordinary bones** | Abyssal Vertibrae; Abyssal Rib |

Unless the owner specifies an exception, future monster discussions listing
only unique rare drops still imply hide and bones. Do not infer another
exception from anatomy: Banshee retains that default in this plan.
The Abyssal demon's rare skeletal components do not count as its ordinary
bone/remains drop. Existing **Demon ash (item 3112)** can supply its ash;
do not create a duplicate ash item.

The residue and feathers are baseline per-kill materials, each with a confirmed
quantity range of 1–3, not rare component rolls. The distribution within that
range, other hide identities/quantities, bone types/quantities and ash quantity
remain to be specified. Do not infer a separate new leather set for each monster.
**Frozen Tear** means a teardrop (tear rhymes with ear), not a rip in material.

Every monster also needs a complete, combat-level-appropriate ordinary loot
table drawn from existing content: runes, ammunition, armor, rare-loot-table
access and other suitable items. Unique parts supplement those tables.
The owner does not need to enumerate ordinary loot in every monster request.
Exact existing entries, quantities, weights and rare-table access rates still
require a balance pass.

## Rarity and assembly balancing

The owner's use of "rare" is descriptive, not an instruction to select the
game's named **Rare / 1 in 128** category. Use the less common rates available:
**1 in 1,000 and rarer are intended for appropriate components**. This is a
design range, not approval of 1/1,000 for every component or a universal floor.

- Both Abyssal skeletal components are very rare; **Abyssal Rib is rarer than
  Abyssal Vertibrae**.
- No exact component rate or rare-component drop quantity is approved yet.
  Use the approved recipe counts in the equipment design; the pendant's
  Frozen Tear count and currency costs remain open.
- Choose component rates together with assembly quantities, expected kills per
  hour, encounter difficulty, repeat rewards and Slayer-currency costs.
- Record numeric per-kill probabilities, not just rarity labels. Audit how the
  existing drop system expresses those probabilities before implementation.
- For a one-unit component with chance p and a recipe requiring q, q/p is its
  expected kill count. Compare components separately; several independently
  collected requirements overlap, so do not simply add their expected counts.
  Record variability and the bottleneck as well as the average.
- Decide independent versus shared rolls, simultaneous rare drops, luck
  modifiers, on-task restrictions and any duplicate/bad-luck rules explicitly.
  No pity system or guaranteed unique drop is implied.

## Abyssal demon: abyssal whip

The owner wants the abyssal whip to return from OSRS, reinterpreted through
the approved demon's spine, rib bones and sticky flesh. This does not
imply importing OSRS assets or copying its stats/requirements.

| Component | Source and role | Rarity decision |
| --- | --- | --- |
| Slimey residue | Replaces this demon's hide; binds the whip together | Baseline 1–3 per kill |
| Abyssal Rib | Forms the handle | Very rare; rarer than the vertebrae; exact rate undecided |
| Abyssal Vertibrae | Supplies the spinal-column lash | Very rare; more common than the rib; exact rate undecided |

These are the current component names. The rib replaces the formerly planned
horn; the vertebrae replaces the spinal-fragment working name. Do not add the
old components alongside the new ones. Item IDs remain unallocated.
The [Abyssal Whip design](slayer-unique-equipment-design.md#abyssal-whip)
specifies the approved quantities, tier benchmarks, hit-delay effect and exact
examine text. All three component types plus Slayer currency are required.
Currency tier/cost vector and shop stock/access rules remain open. The demon
is planned for floor 6 / Hero challenge tier, but that does not itself approve
a Hero-only price or override existing shop-cost rules.

## Material and economy boundaries

- Decide separately whether substitute materials such as residue participate
  in leather-equivalent Crafting production. The owner has specified residue
  for whip assembly, not a residue armor recipe.
- Do not classify a source as retired merely because its approved material
  replaces literal hide. Apply the grandfathering policy only to the final
  approved retired item families.
- Decide whether parts and assembled weapons are tradeable, whether drops
  require an active task, and whether bought parts are eligible. No personal
  kill-proof or account-bound component restriction is implied.
- Determine each rare component's drop chance, eligible NPC variants, quantity,
  independent versus shared roll, and any luck modifiers deliberately.
- Six equipment rewards now have owner-directed designs in the linked
  equipment document. Naga's component is deferred; Cockatrice Eye and Sticky
  Saliva Gland are collectible ingredients for a future coating system, not
  an instruction to implement that system with their drops.

## Icon work and visual reference

Icon artwork is needed for the new drops, the equipment they will produce,
and the existing gimmick solutions. This is an artwork backlog, not a claim
that icons have been created, approved or imported.

- Active drop icons: the eight rare components in the roster plus Slimey
  residue and Cockatrice Feathers (ten named drop-icon subjects). Serpant's
  Tail is deferred with its drop; do not include it in the active backlog.
- Review/reuse hide and bone icons once their actual item families are chosen;
  reuse existing Demon ash. Six hide-dropping monsters do not automatically
  imply six new hide icons.
- Gimmick icons: **Slime Solvent, Eye Drops, Wax earplugs, Dog Treats, Static
  discharge wipe**. They currently borrow potion artwork. Solvent is shared
  by frog and Abyssal demon, not a second item/icon.
- Equipment icons: **Abyssal Whip, Thunder Spire Staff, Leaching Bow, Dagger
  of Terror, Sullen Pendant and the unnamed Cockatrice feather shield**.
  Use the linked reward descriptions and construction details. Future coating
  potion/weapon-variant icons are deferred with that system.
- Use established RSC inventory/ground icon styling and the existing item
  canvas constraints. Confirm the actual target canvas and scale in the
  item pipeline before generation; source asset sizes are not all identical.
- Review readability at native inventory size, transparent edges, consistent
  shading/palette, and distinct silhouettes. Where a component corresponds to
  an approved NPC feature, use that creature as its visual reference.
- Keep approved reference art and written item intent available for each
  pass; do not rely only on the last generated attempt. Larger canvases need
  explicit approval if the existing limit prevents the requested result.

Suggested sequence (not implementation approval): use the documented rewards
and recipes to balance rarity and currency costs, then produce drop, equipment
and counter-item icons against those designs. The already-defined gimmick
items can receive icons independently of unresolved reward balancing. Follow
with item definitions, full drop tables, atomic shop assembly and effects.

## Confirmed unique-reward equipment slots

All task monsters supply hides or equivalent materials, and leather armor sets
have set bonuses. Unique assembled rewards must complement those sets rather
than force players to choose between a unique armor piece and their set bonus.

- Available reward categories: **weapons, shields and jewelry**.
- Do not design unique rewards for slots occupied by leather armor pieces.
- Do not work around this rule by breaking, weakening or redesigning leather
  set bonuses. No such change is authorized.
- Check actual equipment-slot definitions and set membership before specifying
  each reward. Exact jewelry subslots, stats and effects remain item-specific
  decisions; this rule does not approve a particular new item or slot.

The abyssal whip fits the weapon category. The component-plus-Slayer-currency
assembly model may also produce shields and jewelry, not only weapons.

## Integration and acceptance goals

Extend the existing authoritative shop transaction to validate both component
counts and typed Slayer balances before consuming anything. Consume materials
and currency and deliver the reward atomically; missing parts, insufficient
currency, inventory failure, cancellation or duplicate submission must not
partially charge the player or duplicate the reward. Quantity selection must
use checked multiplication for all costs, respecting actual inventory/stack
rules and existing shop currency constraints.

Audit alternate/certificate forms and decide their acceptance explicitly.
Show the complete parts-and-currency recipe before confirmation. Test missing
each component, insufficient currency, successful exchange, inventory capacity,
rollback, repeated submissions, multi-buy if supported, and persistence.

This document adds design goals only. No items, drop tables, shops, player
accounts, sprite assets or live-server behavior have been changed.
