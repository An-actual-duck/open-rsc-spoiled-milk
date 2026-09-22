# Slayer hide sources and grandfathered leather equipment

Status: new-set foundations implemented; special bonuses pending. Source retirement
and passive exceptions now selected by the owner; runtime changes pending.
Updated: 2026-09-21.

Latest [set-bonus decisions](slayer-leather-set-bonuses.md): Banshee hide is
to become **Ectoplasm**, with no current armor/production route. Carapace
cleansing replaces offensive poison procs; magic-spider loses its special
magic-penalty exemption. Dark beast, Bloodveld and frog have new effect
directions; Terror dog's Ferocious is approved at two 75% melee hits. These runtime
changes are pending. The earlier implementation counts below are historical.

The owner has **completed the source-coverage audit**. Rats/large rats, giant
bats and animated axes are accepted no-material exceptions; leave their tasks
and drops alone. Ugthanki is the only new hide/armor family requested and its
[tier-4 foundation is implemented](ugthanki-leather-implementation.md), with its
bonus still undecided. This resolves the audit; retirement and boss opt-in
runtime changes remain pending rather than being implicitly completed.

Related: [Monster Slayer guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md),
[Slayer Tower expansion](slayer-tower-npc-integration.md), and
[boss leather progression](../in-progress-work-plans/boss-leather-tier-progression-plan.md).
These notes describe a future source/roster overhaul, not an assertion that
existing item definitions, drops or accounts have already changed.
The separate [component pass](slayer-component-implementation.md) has added six
new collectible raw hides. The [leather foundation pass](slayer-leather-implementation.md)
adds their tanning and five-piece armor recipes; existing hide families and
grandfathered holdings remain unchanged.

## Owner direction

- Give leather armor a stronger Slayer identity while retaining Crafting as
  its production route.
- New ordinary hides follow standard **hide -> tanned leather -> crafted
  leather armor** production. All six sets use the usual coif, gloves, boots,
  chaps and cuirass slots. Choose tiers by source-monster combat-level
  equivalency with existing leather families. Preserve the existing per-tier,
  per-slot total defense budget; distribute it proportionally to the source's
  current melee/ranged/magic defenses using the established rounding method.
  Standard tanning, crafting, material/thread costs and wear rules apply.
  Special set bonuses remain undecided: do not invent, borrow or activate
  another family's effect when adding these base items.
- Slayer monsters should supply the combat-focused hide families. Explicit
  passive exceptions are cows, regular unicorns and bears: retain their drops,
  tanning, crafting, armor and normal tradability, but keep them outside Slayer.
  Remove the existing bear assignment. Black unicorns remain Slayer tasks and
  retain their leather family (owner-confirmed exception to the unicorn wording).
  Task monsters should supply hide or an owner-approved thematic substitute,
  except the accepted rats/large rats, giant bats and animated axes.
  Confirmed exceptions are **Slimey residue (1–3 per kill)** for Abyssal demons
  and **Cockatrice Feathers (1–3 per kill)** for cockatrices; see
  [special drops and assembly](slayer-special-drops-and-assembly.md).
  Neither Abyssal demons nor cockatrices get hide, leather or an armor set.
  Their residue/feather component uses remain as separately approved.
- Audit existing hide sources and Slayer task families in both directions.
  Some current hide drops will be removed; some qualifying monsters should
  instead gain assignments so their hide production remains part of Slayer.
- Add Balrog and Elder Green Dragon as **optional, explicitly opted-in
  repeatable assignments only**, never mandatory tasks. Elder Green Dragon
  requires access to a special Mining Guild area; Balrog is behind a quest
  line. Audit exact access predicates rather than inventing levels or flags.
- Remove regular, moss, ice and fire giants, ogres and jogres from Slayer
  wherever assigned, and retire their hide/leather/armor acquisition. This
  concerns the giant humanoid families, not creatures merely named "giant"
  such as giant frogs, giant spiders or giant bats. Other families are not
  implicitly approved for retirement.

Confirmed: inclusion in the Slayer roster defines an eligible hide source;
the killer does **not** need that particular task active. Individual boss opt-in
controls assignments, not whether that creature can drop its hide.

## Review order

Before implementing the six retained new leather set bonuses (including Ugthanki, excluding Banshee), use the bounded
[material coverage and armor-theme review](#material-coverage-and-shared-armor-themes)
below. The wider effect-standardization project is deliberately deferred until
the Slayer Tower work is complete.

## Material coverage and shared armor themes

The owner wants a **partial leather-armor overhaul**, not merely six independent
new set bonuses. Review existing retained sets alongside the new sets so related
materials have a coherent theme before deciding their individual effects.

- Every creature actually assigned through mandatory or repeatable Slayer tasks
  must drop hide/carapace or an explicitly approved equivalent, unless it is
  one of the three accepted no-material exceptions above. For future additions, check the
  effective roster after approved removals, the staged eight-monster expansion,
  and the optional boss additions. Check every eligible NPC variant, not only
  one representative ID or a family definition that is never assigned.
- Produce a coverage table linking task family/NPC IDs to dropped material,
  processed material and armor family (or approved non-armor use). Identify
  missing materials and propose suitable names/types rather than silently
  inventing new exceptions or assigning arbitrary existing hides.
- This is a source-coverage rule, not an active-task drop restriction. Shared
  material families remain possible; it does not require a unique armor set
  for every NPC variant.
- Preserve the already approved non-armor equivalents: Cockatrice Feathers and
  Abyssal demon Slimey residue. Do not introduce hides or armor for those two
  without a new owner decision. Preserve the passive cow/regular-unicorn/bear
  leather exception and the black-unicorn Slayer decision above.
- **All carapace armor families will increase the player's poison cleanse
  rate.** This is faster clearance of poison affecting the wearer, not a new
  poison-application proc or a promise of complete poison immunity. Include
  existing scorpion, spider and magic-spider carapace and any future carapace.
- Exact cleanse amounts, tier scaling, piece-versus-full-set activation, mixed
  sets, and stacking with other cleansing sources still need design approval.
  Cleansing **replaces** the current offensive poison effects. Remove the
  magic-spider set's exemption from the normal leather magic penalty.
- Review the current bonuses first, then propose shared family themes and
  individual set identities together. Do not implement arbitrary numerical
  bonuses merely to fill the remaining sets. Retired grandfathered armor keeps
  its existing stats/effects under the separate preservation rule.

This theme/coverage direction is documented, not yet implemented. It does not
authorize the wider timing/damage refactor described in the
[post-tower effect-standardization follow-up](../in-progress-work-plans/effect-standardization-follow-up.md).

## Selected retirement scope

The owner's latest direction selects the following five material families for
retirement, superseding the earlier deferral for this specific scope. It does
not authorize unrelated source removals or a live database/deployment operation.

| Family | Raw hide | Processed leather | Five armor IDs |
| --- | ---: | ---: | --- |
| Giant | 1807 | 1808 | 1875–1879 |
| Moss giant | 1809 | 1810 | 1895–1899 |
| Ice giant | 1811 | 1812 | 1900–1904 |
| Fire giant | 1813 | 1814 | 1915–1919 |
| Ogre, including jogre sources | 1815 | 1816 | 1880–1884 |

This is **35 item definitions**. Leave every existing instance and item ID in
place. Bind the retired definitions as untradeable; do not delete or replace
holdings, reset quantities, change armor stats or remove existing set effects.
The preserved items are intentional "I was here for it" legacy collectibles.

Remove raw hide drops from every affected source variant, tanning/processing,
armor crafting, and the corresponding player-visible recipe/help listings.
Existing retained raw hide/leather cannot be used to make more retired items.
Also close alternative normal acquisition routes: the current Fishing special
reward table includes gloves and boots from all five retired families. Keeping
those rewards would continue issuing the purported legacy items to new players.
Audit shops, other loot, direct crafting calls and ingredient-use routes too.
Developer item-grant tools are not ordinary player acquisition.

Current assigned removals are `falador.bears`, `brimhaven.jogres`,
`brimhaven.moss_giants`, `champions.ice_giants`, `heroes.fire_giants`, and the
jogre/ice-giant repeatables. Regular giant/ogre family definitions exist but
are not currently assigned. Retain black unicorns and their assignment.

Task changes require a versioned migration for both the legacy roster and
the staged tower roster. Do not rewrite the historical roster in place:
existing numeric cursors and completed-task identities depend on it. Preserve
earned ranks/completed tiers, balances, backpack upgrades and unrelated active
tasks. Retired active assignments must not strand a player; specify and test
their resolution without silently paying unearned rewards. Backpack/shop
prices are not implicitly repriced by the shorter mandatory route.

## Optional access-gated boss assignments

- Balrog and Elder Green Dragon begin excluded from each player's random task
  chances. Unlocking access does not by itself opt a player in.
- Give the player a deliberate way to enable each boss independently. The
  owner's approved presentation is nearby-associate dialogue:
  **"I'm ready for Elder Green Dragon tasks"** and
  **"I'm ready for Balrog tasks"**. Both are handled by the existing Legends'
  Guild Slayer shop associate (857), near Radimus; no new placement is needed.
- Choosing the relevant option adds that boss to eligible random task chances,
  not a guaranteed immediate boss assignment or a replacement for an active
  task. Keep the one-active-task contract and existing progress intact.
- Persist each choice per player and never silently opt existing accounts in.
  No boss opt-in may become a prerequisite for rank progression, guild quest
  completion or the mandatory King Black Dragon capstone.
- Dialogue must not grant access or bypass the Mining Guild/quest gates. Audit
  the actual requirements and recheck eligibility at assignment so players are
  not sent to an inaccessible encounter. Both join the Legends' Guild repeatable
  pool and follow its existing rank/progression eligibility, not an earlier
  mandatory tier.
- Offer **"I'm unable to complete"** in the relevant boss dialogue while opted
  in. The owner confirmed this disables future assignments for that boss and
  cancels a currently active assignment for that same boss **without rewards**.
  Do not cancel a different active task, award partial completion, modify the
  other boss preference, or immediately roll a replacement. Players can opt in
  again deliberately after resolving their access/preparation problem.

Approved for each boss: **one kill per task, 80 Hero points, weight 1**, in the
Legends' Guild repeatable pool, controlled through associate 857. Existing
mandatory King Black Dragon stays unchanged at 60 Hero points. The pool's
normal eligibility requires completion of that contact's mandatory progression;
opting in does not bypass it. These values are approved but not yet implemented.

The earlier blanket exclusion of Balrog from Slayer is superseded for opted-in
repeatable tasks. Its exclusion from the mandatory line remains intact. Elder
Green Dragon's optional inclusion is confirmed; the approved settings above
replace older undecided rank/count/payout notes.

## Leather-source audit deliverable

Inventory all hide/leather armor families and their sources, including any
finished leather-armor drops. For each, present NPC IDs, raw hide IDs, tanned
leather IDs, crafted armor IDs, current set effects and task membership. Mark
the proposed disposition: retain with an existing task; retain and add a task;
or retire the source/family subject to owner approval.

Include Balrog and Elder Green Dragon as confirmed task additions. The five
families above are approved retirements; no others are assumed. Retained armor
remains Crafting-produced. Retire the selected families' finished-item sources
along with their material sources; preserve acquisition of unrelated families.

The selected task removals supersede the earlier instruction to only lengthen
mandatory tasks for these specific entries. Preserve completed progression and
unrelated mandatory entries, including the staged eight-monster additions.

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

The owner explicitly disallows continued tanning/crafting of retired materials.
Remove those recipes rather than showing new players inaccessible content.

Audit certificates/noted forms, shop sale and buyback, trade, player-visible
ground drops, death/loot, refunds and any other item-transfer paths so binding
cannot be bypassed. Exact death/recovery behavior is still a design decision:
preserving existing holdings during migration does not define future death rules.
The affected definition list is now fixed above; implementation must preserve
identity rather than converting holdings to a new item family.

## Audit and implementation preparation

1. Enumerate current task families and every included NPC variant against hide
   drop tables. Apply only the selected retirements and passive exceptions.
2. Enumerate hide sources, tanning outputs and armor recipes, including boss
   sets, and classify each as retained, proposed task addition or proposed
   retirement. Review the eight new tower creatures as part of this coverage.
3. Present exact NPC/item IDs, hide types and leather-tier impacts before
   changing existing definitions. New armor foundations follow the approved
   tier/ratio rules above; their special bonuses remain undecided.
   Residue/feather quantities are 1–3 with equal chances for each amount.
4. Use the approved Balrog/Elder counts, tier and rewards; audit access handling.
   Implement the explicit opt-in/out model above, with default exclusion and
   real access checks. Same-boss cancellation without rewards is approved;
   unrelated task cancellation and immediate rerolls are not.
   Avoid mandatory progression dead ends; preserve unrelated account progress.
5. Specify and test an idempotent preservation/binding migration with backups
   and rollback. Test quantities, equipped items, bank holdings, alternate forms,
   trade rejection, transfer loopholes and rejection/absence of retired recipes.
6. Verify retained/new eligible monsters drop their intended hides, retired
   sources no longer do, and existing Slayer credit and Crafting still work.

This document records approved policy, not completed runtime retirement. No
holdings, drops, recipes or tasks were changed by this documentation pass.
Live activation still requires its separate backup/deployment permissions.
See [new leather implementation](slayer-leather-implementation.md).
