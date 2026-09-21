# Slayer tower assignments, grandfathering and backpack-price comparison

Status: task expansion, preparation tips, grandfathering and fixed backpack
prices implemented behind a **disabled** rollout setting. Equipment assembly
prices are implemented for Shield of Mobility; other rewards remain planned.
Updated: 2026-09-21. See [implementation and activation](slayer-task-expansion-implementation.md)
and [equipment progress](slayer-equipment-implementation.md).

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
- Task counts/payouts in the table below are approved for both mandatory and
  repeatable assignments. Compare unique-item assembly prices against backpack
  upgrades: **a sizable currency charge, but strictly below the matching
  backpack upgrade**, with tier determined by the component's source monster,
  not the finished weapon's stat tier.
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

## Approved new assignments and payouts

**These approved counts and payouts are implemented, but not activated.** Match
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
making unprepared fights the baseline for compensation. Monitor prepared kill
times, consumable costs, spawn density and travel during later playtesting;
do not silently change these approved values based only on combat level.

Approved: keep existing repeatable choices and add each new choice at weight 1,
matching current weights. This produces 3/3/4/4/3/3 choices by tier. Existing
individual choices consequently become less frequent; do not describe this as
unchanged selection probability. Keep the one-active-task rule.

Preserve existing mandatory tasks' keys, counts, payouts and relative order.
Insert additions by prepared difficulty while retaining the existing relative
order; this placement policy is approved, with concrete indices to be mapped
and tested during implementation. In particular, **King Black Dragon remains the
final mandatory capstone**, so do not append the Abyssal demon after it. Mapping
progress by stable completed-task identity must handle insertion, not merely
increment an old numeric cursor.

With these approved additions, the full mandatory path becomes **43 tasks / 1,283 kills**,
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
- Shield of Mobility's prevention behavior and shop purchase are implemented.
  Preparation tips still recommend consumables, which remain the accessible
  initial route before obtaining 500 feathers for the shield.
- Ensure counter items can be obtained before the mandatory encounter. Verify
  actual shop stock/access/prices; the administrator `::slayergimmicks` kit is
  not a player acquisition route.
- Do not enable unavoidable tower assignments before their spawns, tier access
  and route are playable. Tower associate placement remains map integration
  work, not permission to fabricate coordinates here.

## Grandfathering and migration contract

Balrog and Elder Green Dragon are separate **opt-in repeatable** additions under
the [leather/source audit](slayer-hide-and-leather-overhaul.md#optional-access-gated-boss-assignments).
Neither enters mandatory progression or changes the approved 43-task total.

Approved policy: **all already-completed tiers remain complete**. Preserve rank,
quest completion, balances, purchased backpack entitlements and promotion
acknowledgements. Fully completed players retain repeatable-task access without
doing the additions. They may receive new monsters through repeatable tasks.

Implemented migration contract (applies when the rollout is enabled):

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
the **existing formula would require** when the approved payouts are implemented.

| Currency | Existing mandatory tasks / kills | Existing payout total | Approved expanded payout total | Current backpack price | Formula price after additions |
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

### Approved assembly prices below the backpack benchmark

**The owner approved all six amounts below and source-tier-only currency.**
The amounts were chosen near 75% of each unchanged backpack price, rounded to
the nearest five (halfway values rounded upward). These are fixed approved
prices, not a new runtime auto-pricing formula. Shield of Mobility's price is
implemented; the other five await their equipment passes.

| Reward | Source-tier currency | Backpack benchmark | Approved assembly currency | Equivalent source-monster tasks from zero balance |
| --- | --- | ---: | ---: | ---: |
| Shield of Mobility | Adept | 148 | 110 | 14 at 8 each |
| Sullen Pendant | Veteran | 138 | 105 | 9 at 12 each |
| Dagger of Terror | Elite | 110 | 85 | 5 at 18 each |
| Leaching Bow | Elite | 110 | 85 | 5 at 20 each |
| Thunder Spire Staff | Champion | 268 | 200 | 8 at 28 each |
| Abyssal Whip | Hero | 282 | 210 | 6 at 35 each |

All components, feathers/residue and logs remain additional requirements; none
of these currency prices replaces them. The two Elite items share a currency
benchmark despite their different stat tiers. There is no new Fledgling
assembled equipment or Naga reward to price in this pass. Gimmick consumables
and future herbalism recipes are outside these six approved equipment prices.

Approved price vector: **source-tier currency only**, following the backpack
comparison. This is a specific assembly-rule exception to the existing
ordinary shop-item rule of native plus immediately preceding tier currency.
Keep that ordinary shop rule unchanged. Do not add an extra lower-tier charge
to these six recipes or merge the six non-interchangeable balances. Final shop placement
must support the source tier's native currency contract.

Task equivalents illustrate earnings, not a requirement to complete that
particular monster's assignments or a guaranteed sequence of random tasks.
For example, the approved 85 Elite bow cost takes five 20-point Bloodveld
tasks (100 points, 100 kills) from zero balance, while two tongues at the
approved 1/1,000 rate average 2,000 kills. Random selection, mandatory payouts,
other tasks, existing balances and bought parts alter the actual path. Currency
is an additional substantial assembly charge, not necessarily the dominant
time gate. Buyers of finished tradable equipment do not personally pay it.

## Implementation acceptance and decisions still needed

- Task counts/payouts, equal repeatable weights, difficulty-based insertion
  policy and the six assembly currency amounts/vectors are approved. Implement
  concrete insertion positions without losing saved progress or old tasks.
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
  times, counter consumption and earnings during implementation/playtesting.
  Any later price adjustment needs separate approval.
- Relevant existing suites include MonsterSlayerPlayerStateCharacterization,
  MonsterSlayerContactsRouteTest, CurrentMonsterSlayerShopRuntimeCharacterization
  and `tests/myworld/test-monster-slayer-foundation.py`; add expansion-specific
  migration and pricing fixtures when implementation starts.

The task implementation preserves the original roster as the disabled/default
configuration and adds an enabled-only overlay. No map spawns, combat mechanics,
live player records or running servers were changed by this implementation.
Subsequent passes implement [equipment assembly](slayer-equipment-implementation.md)
and [single-tier protection consumable pricing](slayer-gimmick-shop-implementation.md).
