# Slayer protection supplies in shops

Updated: 2026-11-07. Implemented and tested; no deployment or restart.

## Starter supplies on task assignment

The first successful mandatory or repeatable assignment requiring a particular
counter grants one full three-use item, even if the player already owns one.
Giant frogs and Abyssal demons share a single Slime Solvent starter entitlement;
cockatrices receive Eye Drops; banshees receive Wax earplugs; terror dogs receive
Dog Treats; dark beasts receive a Static discharge wipe. Later assignments never
refill a claimed item. Dropping, selling, banking or consuming it does not reopen
the entitlement. Replacements remain available from the existing shops.

Talk and Task shortcuts use the same assignment service. Task state, newly owed
items and their claims commit together. Failed or partial delivery/cache writes
restore the inventory, task and claims. Full inventories block only gifts still
owed; freeing space and retrying retains the initial entitlement. An assignment
whose required gifts are already claimed needs no spare inventory slot. Reminders
and reconnection grant nothing; already active tasks are not retroactively filled.
Ordinary environmental hazards and strategy-only tasks receive no extra items.

The assignment receipt reports only the starter items actually delivered. The
contact acknowledges those gifts after the existing personality remarks, hazard
warnings and task announcement. Reminders retain their warnings and progress.

Claims use Boolean player-cache entries `monster_slayer_starter_<full-item-id>`.
They fit the existing 32-character key / 150-character value schema and use the
normal typed cache save/load path. Ordinary state writes, cancellation, progression
and roster migration preserve these independent item identities. There is no
schema change, task-position bitmask or inventory-based inference.

Older saves have no dedicated starter-claim indicator. Existing completed-task,
rank and legacy-migration records do not prove which items were delivered; item
ownership does not establish a gift either. No historical claims are guessed or
backfilled. An untracked player can receive one more starter per required item
on a subsequent successful assignment, then the new claim prevents more. No live
player data was inspected or changed. Crash durability remains that of the normal
player save; the assignment transaction guarantees in-memory rollback on failure.

`test_slayer_task_supplies` covers all six encounters, first-only repeatables,
shared items, cancellation/reassignment, inventory removal and banking, a fresh
player reconstructed from the cache's typed text representation, full-inventory
retry, unsupported clients, injected grant/state/claim failures, concurrent requests
and partly claimed multi-counter tasks. No external database is used by the
fixture. `test_monster_slayer_tower_routes` covers both dialogue entry points and
first-only receipts. Shop and durable player-state fixtures cover the unchanged
replacement and progression mechanics.

## Approved pricing

Each purchase costs the unmodified point payout of one corresponding monster
task and supplies **one full three-use item**. This is the price of the entire
item, not of each use. Required-kill counts and task payouts are unchanged.

| Supply | Full item ID | Source task | Price | Currency / tier |
| --- | ---: | --- | ---: | --- |
| Slime Solvent | 3318 | 25 giant frogs | 5 | Fledgling / 1 |
| Eye Drops | 3321 | 25 cockatrices | 8 | Adept / 2 |
| Wax earplugs | 3324 | 25 banshees | 12 | Veteran / 3 |
| Dog Treats | 3327 | 20 terror dogs | 18 | Elite / 4 |
| Static discharge wipe | 3330 | 15 dark beasts | 28 | Champion / 5 |

Only the listed currency is charged. No mixed-tier cost, currency conversion,
ingredients or extra fee. No supply costs Hero/tier-6 points. Slime Solvent
remains a tier-1 purchase even when used against Abyssal demons. `INITIATE`
remains the internal identifier for player-facing Adept currency.

The matching mandatory and repeatable task payouts in `MonsterSlayerTower.json`
are the price benchmark. Prices are explicit shop data; the regression compares
them against both task forms so a future payout change cannot silently drift.

## Access, quantities and use

All five supplies are listed in the **Slayer protections** category in all six
shops: Falador, Port Sarim, Brimhaven, Champions', Heroes' and Legends'. Shops
are selectable through existing Slayer associates. Shopping adds no rank gate
or requirement to have completed the corresponding task. Points from other
tasks of the same tier can fund the first protection purchase.

Stock is unlimited under the existing Slayer shop rules. A quantity of three
buys three separate full-charge items for three times the listed price. Items
remain nonstackable and existing inventory-capacity checks apply. The historic
`stock`/`restockAmount` fields remain schema metadata; runtime stock is infinite.
Only full-charge variants are sold, not partial-use variants.

No consumable effects, duration, activation commands or three-use depletion
mechanics were changed. The first four protections retain their ten-minute
effects. Wipes remain reactive: each use clears all current Dark Beast marks;
they do not grant ten-minute lightning immunity. Existing placeholder icons
are retained pending the art pass.

## Implementation boundary

- `MonsterSlayer.json`: thirty listings, five per shop, each amount one.
- `MonsterSlayerData`: a narrowly scoped exception to native-shop pricing for
  the five known full-charge protection item IDs. They must use only their
  mapped source currency, amount one and no ingredients, even in another tier's
  shop. Arbitrary items and partial-charge variants cannot use this exception.
- Ordinary supply prices, assembly costs, backpack prices, currency accounting,
  purchase atomicity and existing mixed-currency validation are unchanged.
- The existing shop UI carries the one-component price vector; no client or
  packet changes are needed.
- Supply availability does not activate the tower task rollout. Shops work
  with either roster; the spawn-gated rollout remains separately controlled.

## Verification

`ant test_slayer_gimmick_shops` checks all thirty listings with both rosters,
matching task payouts, three-use IDs, displayed currency vectors, unlimited
stock, repeated/bulk real purchases, no substitution from other balances,
full inventory and insufficient-points rejection, schema isolation and existing
atomic transaction coverage.

Regression coverage: `test_monster_slayer_player_state`, `test_slayer_rewards`,
`test_slayer_mobility_shield`, `test_monster_slayer_tower_routes`, and the
Monster Slayer foundation checks. No private or public server was launched.
