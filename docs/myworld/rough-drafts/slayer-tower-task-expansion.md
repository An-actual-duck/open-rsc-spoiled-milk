# Slayer tower assignments, grandfathering and backpack-price comparison

Status: approved scope and grandfathering; proposed counts, payouts and prices.
Updated: 2026-09-20. Documentation only; no runtime or player-state changes.

Related: [tower roster](slayer-tower-npc-integration.md),
[reward designs](slayer-unique-equipment-design.md),
[drops and assembly](slayer-special-drops-and-assembly.md), and
[existing guild plan](../in-progress-work-plans/monster-slayer-guild-plan.md).

## Confirmed owner direction

- Add all eight approved tower monsters to Slayer assignments and establish
  their task payouts.
- Include them in the mandatory progression as **additional assignments**.
  Do not remove or replace existing mandatory entries to make room, or shorten
  existing tasks to offset the additions. The overall path becomes longer.
- **Preserve every completed tier**, not just completion of the entire path.
  Players partway through progression receive additions only in their current
  and future tiers. Fully completed players remain fully completed.
- Give useful preparation tips when assigning the task, including what
  consumables to bring and how to use them.
- Establish tasks and payouts first, then compare unique-item assembly prices
  with the existing backpack upgrades. Similar pricing is a design direction,
  not approval to reprice either system automatically.
- **Keep all six existing backpack prices unchanged** as mandatory tasks grow.
  The task expansion must deliberately decouple their pricing validation from
  the enlarged mandatory-payout totals.

## Inspected implementation baseline

The following is from tracked definitions/code at Core commit `b236036fd`, not
an assertion about which build the public server currently runs.

- [MonsterSlayer.json](../../../server/conf/server/defs/extras/MonsterSlayer.json)
  has **35 mandatory tasks / 1,113 kills**, plus two repeatable choices per
  challenge tier. None of NPC IDs 863–870 is yet assigned through this file.
- Currency is awarded **on task completion**, not once per kill. It is paid
  in the contact's challenge currency. The six balances are not interchangeable.
- Tier 2 is displayed as **Adept** but retains the stable `INITIATE` data key.
  Veteran uses contact key `brimhaven` although its contact is now at the Blue
  Moon Inn. Do not rename these persisted keys as part of this expansion.
- [MonsterSlayerState](../../../server/src/com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerState.java)
  stores integer mandatory cursors and stable active-task keys. Validation
  requires completed-tier cursors to equal the current list lengths. Extending
  JSON lists alone would invalidate existing promoted players' snapshots.
- [MonsterSlayerContacts](../../../server/plugins/com/openrsc/server/plugins/custom/myworld/npcs/MonsterSlayerContacts.java)
  already displays typed hazard warnings before committing the previewed
  assignment, on both ordinary Talk-to and the Task shortcut. Current hazards
  cover heat, Wilderness, Worship drain, poison and dragon fire only.
- [MonsterSlayerData](../../../server/src/com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerData.java)
  requires backpack prices to equal **2 × ceil(1.10 × that tier's total
  mandatory-task payouts)** at definition load time. It rejects mismatched
  prices; it does not silently recalculate the configured shop price.

## Proposed new assignments and payouts

**Numbers below are a starting proposal, not approved balance values.** Match
each creature to its already approved tower challenge tier. Add one mandatory
and one repeatable task definition for each, with distinct stable task keys.
Initially use the same kill count/payout for both forms so the preparation
lesson and repeatable work are easy to compare. Existing tasks remain intact.

| Floor / currency | Monster (NPC ID) | Kills per task | Completion payout |
| --- | --- | ---: | ---: |
| 1 / Fledgling | Giant frog (863) | 25 | 5 Fledgling |
| 2 / Adept | Cockatrice (864) | 25 | 8 Adept |
| 3 / Veteran | Banshee (865) | 25 | 12 Veteran |
| 3 / Veteran | Naga (866) | 25 | 12 Veteran |
| 4 / Elite | Terror dog (867) | 20 | 18 Elite |
| 4 / Elite | Bloodveld (868) | 20 | 20 Elite |
| 5 / Champion | Dark beast (869) | 15 | 28 Champion |
| 6 / Hero | Abyssal demon (870) | 15 | 35 Hero |

Rationale: moderate introductory assignments at low tiers; shorter assignments
for later enemies with longer fights, stronger mechanics or faster consumable
use. Payouts sit near their tier's current repeatable rewards rather than
making unprepared fights the baseline for compensation. Validate against
measured prepared kill times, consumable costs, spawn density and travel before
final approval; combat level alone is insufficient.

Proposal: keep existing repeatable choices and add each new choice at weight 1,
matching current weights. This produces 3/3/4/4/3/3 choices by tier. Existing
individual choices consequently become less frequent; do not describe this as
unchanged selection probability. Keep the one-active-task rule.

Preserve existing mandatory tasks' keys, counts, payouts and relative order.
Place additions at suitable preparation/difficulty points; exact insertion
positions remain to be reviewed. In particular, **King Black Dragon remains the
final mandatory capstone**, so do not append the Abyssal demon after it. Mapping
progress by stable completed-task identity must handle insertion, not merely
increment an old numeric cursor.

Under this proposal, the full mandatory path becomes **43 tasks / 1,283 kills**,
an addition of eight tasks and 170 kills. Currency totals are listed by type
below; they must not be treated as one pooled spendable balance.

## Assignment preparation tips

Draft player-facing lines below are mechanically grounded; final NPC voice and
line wrapping can be polished without omitting essential preparation advice.
Render short separate lines, not one dense paragraph.

| Monster | Draft assignment advice |
| --- | --- |
| Giant frog | "Bring Slime Solvent so its sticky spit cannot stop you attacking." / "Bring an antidote for its poison as well." |
| Cockatrice | "Use Eye Drops before fighting; its glare can freeze you and stop your attacks." |
| Banshee | "Insert Wax earplugs before fighting. They dissolve after ten minutes." / "Her wail is deadly without them, whether she attacks up close or with magic." |
| Naga | "Bring food for its paired sword strikes." / "Melee gets through its defenses best, but fighting at a distance avoids the paired strikes while it throws swords and closes in." |
| Terror dog | "Scatter Dog Treats before fighting to prevent Feeding Frenzy for ten minutes." / "Nearby terror dogs add extra bites, so packs are dangerous without treats." |
| Bloodveld | "Bring food; it heals itself by feeding on the damage it deals." / "Its tongue can pull you into melee, so do not rely on keeping your distance." |
| Dark beast | "Bring Static discharge wipes. Use one when you feel the air turn tingly." / "Running away will not remove the mark. One wipe clears all your current marks, but a later charge needs another wipe." |
| Abyssal demon | "Use Slime Solvent before fighting and bring spare doses." / "Each damaging hit wears five seconds off its protection. Without it, sticky flesh stops you moving, fighting or using items." / "Bring a melee weapon; its magic and ranged defenses are very high." |

Implementation requirements:

- Advice must match the **actual committed assignment**, not another random
  preview. Cover both mandatory and repeatable tasks and the Task shortcut.
- Make advice available again when checking an active assignment, so returning
  players do not need to remember the initial dialogue. Preserve existing
  progress reporting rather than replacing it.
- Use typed preparation/strategy metadata, not string matching against task
  names. Update all exhaustive hazard switches if extending the existing enum;
  Bran and Doran's personality remarks must not throw on new hazards.
- Keep Naga/Bloodveld strategy advice distinct from mandatory-counter advice;
  neither has a special consumable requirement.
- Do not promise Solvent poison immunity or pre-emptive, timed immunity from
  discharge wipes. Wipes remove existing marks, not future ones.
- The feather shield is planned, not implemented. Only add it as an alternative
  counter tip once its prevention behavior is available in the same release.
- Ensure counter items can be obtained before the mandatory encounter. Verify
  actual shop stock/access/prices; the administrator `::slayergimmicks` kit is
  not a player acquisition route.
- Do not enable unavoidable tower assignments before their spawns, tier access
  and route are playable. Tower associate placement remains map integration
  work, not permission to fabricate coordinates here.

## Grandfathering and migration contract

Approved policy: **all already-completed tiers remain complete**. Preserve rank,
quest completion, balances, purchased backpack entitlements and promotion
acknowledgements. Fully completed players retain repeatable-task access without
doing the additions. They may receive new monsters through repeatable tasks.

Recommended implementation constraints (not yet implemented):

1. Add a versioned roster migration separate from the existing Combat Odyssey
   recognition. Decode and validate old snapshots against the old roster before
   applying new-roster validation. Never relabel corrupt state as completed.
2. For completed tiers, map to the new completed state without awarding payouts
   for waived additions or incrementing lifetime task-completion counts.
3. For an unfinished current tier, retain every completed old task and the
   exact active task/kill count. Require the new assignments in that tier even
   when their new insertion point falls before the old cursor. A stable-key
   completion/pending representation or equivalent explicit migration is needed;
   mapping only to the active task's new index would silently skip new work.
4. Future tiers receive the expanded full sequence. Untouched old tasks remain
   required, once each. Never reset partially earned kills or replay rewards.
5. Preserve active repeatable tasks, bank/inventory items, typed currencies and
   capacity purchases. Persist the migration once, atomically/idempotently,
   including offline players when they next load.
6. A player who earned promotion but has not seen its ceremony must not lose
   completion. Determine eligibility from validated authoritative progression,
   not just dialogue acknowledgement or an unverified rank field.

## Backpack comparison before setting assembly prices

Current prices are evidence, not a proposed change. The last column shows what
the **existing formula would require** if the proposed payouts were adopted.

| Currency | Existing mandatory tasks / kills | Existing payout total | Proposed payout total | Current backpack price | Formula price after additions |
| --- | --- | ---: | ---: | ---: | ---: |
| Fledgling | 9 / 307 | 38 | 43 | 84 | 96 |
| Adept | 9 / 350 | 67 | 75 | 148 | 166 |
| Veteran | 6 / 230 | 62 | 86 | 138 | 190 |
| Elite | 3 / 90 | 50 | 88 | 110 | 194 |
| Champion | 5 / 105 | 121 | 149 | 268 | 328 |
| Hero | 3 / 31 | 128 | 163 | 282 | 360 |

**Owner decision: keep current prices** (84 / 148 / 138 / 110 / 268 / 282 in
their respective currencies). The final column is a diagnostic comparison,
not an intended price increase. Preserve strict validation using the approved
fixed-price contract rather than the changing task totals; update the loader
and tests deliberately, not by simply bypassing validation. Already-owned
upgrades remain owned without extra payment or refunds.

Backpack upgrades are permanent, sequential, untradable entitlements adding
1/1/1/2/2/3 slots, from 30 to 40. Equipment is tradable and its assembly can be
repeated, with monster parts and sometimes logs charged as well. Equal sticker
prices therefore do not mean identical total acquisition effort.

After counts/payouts are approved, a **one same-tier backpack-price starting
comparison** would be:

| Reward | Source-tier currency | Current-price comparison, not approved cost |
| --- | --- | ---: |
| Cockatrice feather shield | Adept | 148 |
| Sullen Pendant | Veteran | 138 |
| Dagger of Terror | Elite | 110 |
| Leaching Bow | Elite | 110 |
| Thunder Spire Staff | Champion | 268 |
| Abyssal Whip | Hero | 282 |

This is a native-currency comparison, not an approved shop placement or price
vector. Existing ordinary shop rewards may charge multiple lower/native
currencies, while backpacks are native-only. Do not collapse typed balances
or silently waive the native shop currency requirement for new equipment.

Compare task earnings, not just raw point numbers. With the proposed payouts,
110 Elite is six completed 20-point Bloodveld tasks (120 points, 120 kills),
whereas collecting two tongues at the proposed 1/1,000 rate averages 2,000
kills. Random task selection, existing balances and buying parts change the
player's actual path. Currency paid for assembly is work for the assembler;
buyers of finished tradable equipment do not personally pay that shop cost.

## Implementation acceptance and decisions still needed

- Approve task counts, payouts, insertion positions and repeatable weights.
- Implement the approved fixed backpack prices and corresponding loader/tests
  before modifying mandatory definitions; verify all six prices stay unchanged.
- Verify existing old-task identity/order/count/payout preservation; test all
  eight NPC families for correct credit, wrong-family rejection, exactly-once
  completion payouts and unchanged death eligibility.
- Test fresh accounts; every completed-tier boundary; partly finished current
  tiers; active mandatory/repeatable assignments; earned-but-unacknowledged
  promotions; fully completed Legend; repeated migration; offline load; and
  corrupt/future-version state. Preserve all owned capacity entitlements.
- Test preparation advice on every assignment route and active-task reminder;
  preview and committed task must agree. Include all new metadata in hazard
  and dialogue tests.
- Exercise definition-load price validation and compare actual task completion
  times, counter consumption and earnings before approving assembly prices.
- Relevant existing suites include MonsterSlayerPlayerStateCharacterization,
  MonsterSlayerContactsRouteTest, CurrentMonsterSlayerShopRuntimeCharacterization
  and `tests/myworld/test-monster-slayer-foundation.py`; add expansion-specific
  migration and pricing fixtures when implementation starts.

This document is a plan and balance proposal. No task definitions, prices,
player records, map spawns, combat mechanics or servers were changed.
