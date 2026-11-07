# Slayer protection supplies in shops

Updated: 2026-11-06. Implemented and tested; no deployment or restart.

## Starter supplies on task assignment

Each newly assigned mandatory or repeatable gimmick task now includes one full
three-use counter item, even if the player already owns one. Giant frogs and
Abyssal demons receive Slime Solvent; cockatrices receive Eye Drops; banshees
receive Wax earplugs; terror dogs receive Dog Treats; dark beasts receive a
Static discharge wipe. Associates explain the starter gift and where to buy
more after announcing the task. The existing encounter warning explains its use.

Talk and Task shortcuts use the same assignment service. Task state and all
required starter items commit together; without enough inventory space, the
associate asks the player to make room and no task is assigned. Reminders,
reconnection and unsuccessful requests grant nothing. Already active tasks are
not retroactively refilled. Ordinary environmental hazards and strategy-only
tasks do not receive extra items. Shop availability and prices below remain
unchanged.

`test_slayer_task_supplies` exercises real inventories and cache persistence,
all six encounters, repeated and concurrent requests, full inventories,
unsupported clients, injected grant/cache failures and distinct multi-counter
tasks. `test_monster_slayer_tower_routes` covers both dialogue entry points.

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
