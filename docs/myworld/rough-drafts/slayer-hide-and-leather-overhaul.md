# Slayer hide sources and grandfathered leather equipment

Status: owner-directed design notes; not implemented.
Updated: 2026-09-20.

Related: [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md),
[Slayer Tower expansion](slayer-tower-npc-integration.md), and
[boss leather progression](../in-progress-work-plans/boss-leather-tier-progression-plan.md).
These notes describe a future source/roster overhaul, not an assertion that
existing item definitions, drops or accounts have already changed.
The separate [component pass](slayer-component-implementation.md) has added six
new collectible raw hides; it has not changed existing hide families, tanning,
armor recipes or grandfathered holdings.

## Owner direction

- Give leather armor a stronger Slayer identity while retaining Crafting as
  its production route.
- New ordinary hides follow standard **hide -> tanned leather -> crafted
  leather armor** production and receive unique set effects like existing
  leather armor. Exact hide/set identities, recipes, stats and effect designs
  remain separate audit/design outputs; do not invent them during drop setup.
- Only monsters offered as Slayer tasks should drop hide. Every task monster
  should supply hide or an owner-approved thematic substitute where appropriate.
  Confirmed exceptions are **Slimey residue (1–3 per kill)** for Abyssal demons
  and **Cockatrice Feathers (1–3 per kill)** for cockatrices; see
  [special drops and assembly](slayer-special-drops-and-assembly.md).
  Substitute-material armor/Crafting uses are undecided, not automatic.
- Audit existing hide sources and Slayer task families in both directions.
  Some current hide drops will be removed; some qualifying monsters should
  instead gain assignments so their hide production remains part of Slayer.
- Add Balrog and Elder Green Dragon as **optional, explicitly opted-in
  repeatable assignments only**, never mandatory tasks. Elder Green Dragon
  requires access to a special Mining Guild area; Balrog is behind a quest
  line. Audit exact access predicates rather than inventing levels or flags.
- Review the roster for overly humanoid enemies. The owner believes it is
  already largely clean; do not assume wholesale removals are necessary.
  Giants were raised as a possible non-qualifying source, not a confirmed
  current assignment or finalized item-removal list.

Confirmed: inclusion in the Slayer roster defines an eligible hide source;
the killer does **not** need that particular task active. Individual boss opt-in
controls assignments, not whether that creature can drop its hide.

## Optional access-gated boss assignments

- Balrog and Elder Green Dragon begin excluded from each player's random task
  chances. Unlocking access does not by itself opt a player in.
- Give the player a deliberate way to enable each boss independently. The
  owner's proposed presentation is nearby-associate dialogue:
  **"I'm ready to take on the Elder Green Dragon"** and
  **"I'm Ready to take on the Balrog"**. Exact associate IDs/locations and
  final dialogue routing remain to be selected.
- Choosing the relevant option adds that boss to eligible random task chances,
  not a guaranteed immediate boss assignment or a replacement for an active
  task. Keep the one-active-task contract and existing progress intact.
- Persist each choice per player and never silently opt existing accounts in.
  No boss opt-in may become a prerequisite for rank progression, guild quest
  completion or the mandatory King Black Dragon capstone.
- Dialogue must not grant access or bypass the Mining Guild/quest gates. Audit
  the actual requirements and recheck eligibility at assignment so players are
  not sent to an inaccessible encounter. Count, payout, challenge tier, weight
  and minimum rank for each boss are still undecided.
- A reversible "stop assigning this boss" option is recommended, not yet
  approved. Also settle how it interacts with an already active assignment;
  do not introduce a free task reroll or invent cancellation costs implicitly.

The earlier blanket exclusion of Balrog from Slayer is superseded for opted-in
repeatable tasks. Its exclusion from the mandatory line remains intact. Elder
Green Dragon's optional inclusion is confirmed; older suggestions of a specific
post-Legend assignment tier do not settle the current rank/count/payout design.

## Leather-source audit deliverable

Inventory all hide/leather armor families and their sources, including any
finished leather-armor drops. For each, present NPC IDs, raw hide IDs, tanned
leather IDs, crafted armor IDs, current set effects and task membership. Mark
the proposed disposition: retain with an existing task; retain and add a task;
or retire the source/family subject to owner approval.

Include Balrog and Elder Green Dragon as confirmed task additions, but do not
assume any other current source is approved for removal. Leather armor remains
Crafting-produced; this audit is not permission to add finished armor drops or
silently remove existing ones. Reconcile any discovered finished-item sources
with the owner alongside the hide sources.

Preserve existing mandatory entries during this audit. If a proposed source
retirement conflicts with that rule or a currently assigned monster's required
hide supply, surface the conflict before changing either system.

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
   changing definitions. The tanning/Crafting/unique-set-effect direction is
   approved, but specific armor recipes/bonuses and balance changes are not.
   Residue/feather quantities are 1–3 with equal chances for each amount.
4. Define Balrog/Elder assignment counts, tier, rewards and access handling.
   Implement the explicit opt-in model above, with default exclusion and real
   access checks. A free replacement for an unexpectedly inaccessible active
   assignment remains a proposal, not an approved reroll mechanism.
   Avoid mandatory progression dead ends; preserve unrelated account progress.
5. Specify and test an idempotent preservation/binding migration with backups
   and rollback. Test quantities, equipped items, bank holdings, alternate forms,
   trade rejection, transfer loopholes and any approved legacy crafting paths.
6. Verify retained/new eligible monsters drop their intended hides, retired
   sources no longer do, and existing Slayer credit and Crafting still work.

This pass authorizes documentation only, not asset imports, roster changes,
drop-table changes, account migration or live deployment.
