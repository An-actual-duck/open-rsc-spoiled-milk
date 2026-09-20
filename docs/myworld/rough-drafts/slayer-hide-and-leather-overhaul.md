# Slayer hide sources and grandfathered leather equipment

Status: owner-directed design notes; not implemented.
Updated: 2026-09-20.

Related: [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md),
[Slayer Tower expansion](slayer-tower-npc-integration.md), and
[boss leather progression](../in-progress-work-plans/boss-leather-tier-progression-plan.md).
These notes describe a future source/roster overhaul, not an assertion that
existing item definitions, drops or accounts have already changed.

## Owner direction

- Give leather armor a stronger Slayer identity while retaining Crafting as
  its production route.
- Only monsters offered as Slayer tasks should drop hide. Every task monster
  should supply hide or an owner-approved thematic substitute where appropriate.
  Confirmed exceptions are **Slimey residue (1–3 per kill)** for Abyssal demons
  and **Cockatrice Feathers (1–3 per kill)** for cockatrices; see
  [special drops and assembly](slayer-special-drops-and-assembly.md).
  Substitute-material armor/Crafting uses are undecided, not automatic.
- Audit existing hide sources and Slayer task families in both directions.
  Some current hide drops will be removed; some qualifying monsters should
  instead gain assignments so their hide production remains part of Slayer.
- Add Balrog and Elder Green Dragon to Slayer assignments. Provide a way to
  opt out when a player lacks access. Elder Green Dragon has a Mining access
  restriction; audit the actual requirement rather than inventing a threshold.
- Review the roster for overly humanoid enemies. The owner believes it is
  already largely clean; do not assume wholesale removals are necessary.
  Giants were raised as a possible non-qualifying source, not a confirmed
  current assignment or finalized item-removal list.

Working interpretation: task eligibility defines a monster's hide drop, rather
than requiring its killer to have that specific task active. Confirm this before
implementation; the owner has not requested an on-task-only drop restriction.

## Confirmed grandfathering rule

When a hide/leather family is retired, player-owned hides, processed leathers
and armor made from those materials remain on their accounts and become
**untradeable, grandfathered items**. Do not delete, confiscate, replace with a
different armor family, or silently convert those holdings into currency.
No nerf to their equipment stats or requirements has been requested.

Preserve item identity and quantities in inventory, bank and equipment, and
audit every other supported persistent ownership location. Removing a drop
source must not remove the definitions needed to load and display old items.
The grandfathering rule applies to retired families; it does not make all
remaining leather equipment untradeable.

Before implementation, settle whether retained raw materials may still be
tanned/crafted. If allowed, their outputs must not provide a route around the
untradeable restriction. Do not infer recipe deletion or continued crafting
from the preservation rule alone.

Audit certificates/noted forms, shop sale and buyback, trade, player-visible
ground drops, death/loot, refunds and any other item-transfer paths so binding
cannot be bypassed. Exact death/recovery behavior is still a design decision:
preserving existing holdings during migration does not define future death rules.
Choose item-definition-wide retirement or another identity-safe mechanism only
after the final affected item list is approved.

## Audit and implementation preparation

1. Enumerate current task families and every included NPC variant against hide
   drop tables. Flag humanoids for owner review; do not auto-remove them.
2. Enumerate hide sources, tanning outputs and armor recipes, including boss
   sets, and classify each as retained, proposed task addition or proposed
   retirement. Review the eight new tower creatures as part of this coverage.
3. Present exact NPC/item IDs, hide types and leather-tier impacts before
   changing definitions. Beyond the explicitly approved 1–3 residue/feather
   quantities, no hide type, drop quantity/chance, new armor set or tier
   rebalance is implied merely by the universal hide-source rule.
4. Define Balrog/Elder assignment counts, tier, rewards and access handling.
   Suggested safeguard: filter inaccessible random assignments and provide a
   free replacement if an inaccessible assignment still occurs. These are
   proposals; the confirmed requirement is a usable opt-out for lack of access.
   Avoid mandatory progression dead ends; preserve unrelated account progress.
5. Specify and test an idempotent preservation/binding migration with backups
   and rollback. Test quantities, equipped items, bank holdings, alternate forms,
   trade rejection, transfer loopholes and any approved legacy crafting paths.
6. Verify retained/new eligible monsters drop their intended hides, retired
   sources no longer do, and existing Slayer credit and Crafting still work.

This pass authorizes documentation only, not asset imports, roster changes,
drop-table changes, account migration or live deployment.
