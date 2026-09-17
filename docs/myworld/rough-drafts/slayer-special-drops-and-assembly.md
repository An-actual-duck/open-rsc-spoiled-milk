# Slayer special drops and weapon assembly

Status: owner-directed design notes; not implemented.
Updated: 2026-09-17.

Related: [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md),
[tower roster](slayer-tower-npc-integration.md), and
[hide/material overhaul](slayer-hide-and-leather-overhaul.md).

## Owner direction

- Expand monster-specific drops with distinctive body parts/materials, in the
  spirit of existing spider and zombie eyes.
- Expand Slayer shops to exchange required monster parts **plus Slayer
  currency** for unique weapons. The fiction is paying for assembly, rather
  than simply finding a finished weapon on the monster.
- This is a shop purchase, not a new requirement to turn in materials for
  Slayer task completion, rank progress or points. Preserve the existing
  guild's kill-credit and progression rules.
- Where appropriate, monsters may drop a thematically suitable material in
  place of hide. The abyssal demon's gooey residue is the first explicit case.
  This revises the earlier literal requirement that every task monster drop
  hide; do not force ordinary hide onto incompatible creature anatomy.

## Abyssal demon: abyssal whip

The owner wants the abyssal whip to return from OSRS, reinterpreted through
the approved demon's prominent spine, horns and sticky flesh. This does not
imply importing OSRS assets or copying its stats/requirements.

| Component | Source and role | Rarity decision |
| --- | --- | --- |
| Gooey residue | Replaces this demon's hide; binds the whip together | Chance and quantity undecided |
| Horn | Forms the handle | Very rare; exact rate undecided |
| Spinal fragment | Supplies the spinal-column lash | Very rare; exact rate undecided |

"Horn" is a descriptive working name; final item naming and IDs remain open.
Require the requisite quantity of **all three components**, plus Slayer
currency, to purchase an assembled abyssal whip from the shop. Quantities,
currency tier/cost vector, stock/access rules and output quantity are not yet
specified. The demon is planned for floor 6 / Hero challenge tier, but that
does not itself approve a Hero-only price or override existing shop-cost rules.

Owner-provided item description:

> A whip made from the spinal column of an abyssal demon with the handle of one of it's horns, it's held together by it's sticky flesh. Disgusting

Weapon stats, requirements, attack animation, effects, tradeability, death/reclaim
behavior and equipment slot details need separate decisions. No special attack,
drop-rate number or component quantity has been approved.

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
- Other monsters' unique drops and weapon recipes remain to be designed;
  their existence as a goal does not authorize invented reward definitions.

## Integration and acceptance goals

Extend the existing authoritative shop transaction to validate both component
counts and typed Slayer balances before consuming anything. Consume materials
and currency and deliver the weapon atomically; missing parts, insufficient
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
