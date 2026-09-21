# Slayer task expansion: staged implementation

Updated: 2026-09-20. Implemented, tested, **disabled by default**.
Design authority: [approved task expansion](slayer-tower-task-expansion.md).

## Scope and activation

`want_slayer_tower_tasks: false` in `server/myworld.conf` keeps the existing
35 mandatory / 12 repeatable assignments. Missing settings also default false.
When false, the tower overlay is not loaded and its monsters need no spawns.
No player roster migration happens merely because this code is installed.

After the owner builds the tower in the map editor, activation requires:

1. Install the reviewed tower map, place NPCs 863–870 and make routes/floor
   rank gates playable. This pass invents no map coordinates or associate spawns.
2. Implement player-accessible counter-item shop stock, prices and currencies,
   and verify players can obtain protection before their mandatory encounters.
   Those prices remain undecided. The administrator test kit is not sufficient.
3. Test tower access, assignments, preparation tips and kill credit in a private
   session with the setting enabled, using a backed-up test database.
4. Enable the setting in the **effective** server configuration for the later
   approved rollout. An existing `local.conf` takes priority over `myworld.conf`.
   Back up player data first; public restart still requires separate permission.

Enabled loading retains strict NPC existence, attackability and active-spawn
validation. Missing tower spawns reject the expanded definitions. The setting
does not automatically verify route geometry, access gates or shop availability:
those are release checks above.

This is a forward migration, not a live toggle. Once player records have been
migrated, turning the setting off causes those version-2 Slayer records to be
quarantined rather than downgraded or silently rewritten. Keep it enabled after
activation; any rollback needs a separately reviewed state/data recovery plan.

## Definition layout and ordered insertions

`MonsterSlayer.json` remains the legacy roster and shared shop definitions.
`MonsterSlayerTower.json` adds eight families and eight mandatory entries;
the loader derives eight separate repeatable entries with the same kills/payouts
and weight 1. It never edits the source JSON while loading.

| Contact | New tasks | Insert before |
| --- | --- | --- |
| falador | Giant frog | Desert wolves |
| port_sarim | Cockatrice | Animated axes |
| brimhaven | Banshee, then Naga | Ice spiders |
| champions | Terror dog, then Bloodveld | Greater demons |
| heroes | Dark beast | Red dragons |
| legends | Abyssal demon | King Black Dragon |

These introduce prepared encounters before later hazards/capstones while keeping
every original task's identity, order, kills and payout. Expanded total:
43 mandatory tasks / 1,283 kills. Repeatable pool sizes: 3/3/4/4/3/3.
The King Black Dragon stays last in the authored route.

Backpack prices are validated against explicit fixed amounts
84/148/138/110/268/282, not recalculated from task payouts. Existing ordinary-shop
currency rules, purchased capacity and all item assembly recipes are unchanged.

Do not insert/reorder mandatory entries in the legacy base file in a future
change: version-1 migration needs that original roster. Future roster changes
require their own reviewed migration, not an unversioned edit to either list.

## Progress migration

The existing `monster_slayer_state_version` distinguishes roster 1 from 2.
Combat Odyssey's separate `monster_slayer_migration_version` remains independent.
Offline players migrate when next loaded; no bulk/live database rewrite runs.

- Read/validate old records against the legacy roster first. Invalid and future
  versions are quarantined with evidence untouched.
- Completed tiers include their new tasks as waivers, without currency or
  lifetime-completion rewards. Promotion acknowledgement is not used to decide
  whether a tier is complete.
- Current-tier completed task keys are retained. Every new current/future task
  remains required, including an insertion before the old numeric cursor.
- Preserve active task identity and kills, repeatable assignments, balances,
  capacity purchases, promotion acknowledgements and Odyssey recognition.
- Version 2 uses stable completed-task identities; existing cursor fields
  become validated completion counts. The next task is the first uncompleted
  identity in authored order.
- An already-active old task is allowed to finish before an earlier inserted
  task. For example, an active KBD assignment is not reset during migration;
  remaining newly inserted work is required before promotion.
- Cache writes restore all owned fields if a mutator fails. Repeated successful
  initialization is read-only. Unrelated cache data is never changed.

Completed identities are encoded in bounded 150-character parts, under
`monster_slayer_done_parts` and `monster_slayer_done_0` etc. New keys fit the
existing 32-character MySQL key limit; values fit its 150-character value limit.
No database schema migration is necessary. Missing/duplicate/unknown identities,
bad part counts and mismatched completion counts fail validation.

## Dialogue

Typed metadata supplies specific counter advice for six gimmick monsters and
separate strategy-only advice for Naga/Bloodveld. Both Talk and Task share the
actual assignment preview. Checking an active task repeats that task's advice
and retains its kill-progress message; it does not randomly preview new work.
Bran/Doran handle all new metadata. No unimplemented Shield of Mobility advice
is offered, and no Solvent poison immunity or pre-emptive wipe immunity is promised.

## Verification

- `ant test_monster_slayer_player_state`: legacy regressions plus every valid
  legacy mandatory boundary, partial kills, completed tiers, repeatables,
  unacknowledged promotions, full Legend, reconnect/idempotency, corrupt state,
  independent Odyssey migration and injected write failures.
- The same fixture checks all 16 new task forms for correct/wrong-family credit
  and exactly-once payouts; all counts/payouts/pools, KBD ordering, fixed-price
  validation and cache storage bounds.
- `ant test_monster_slayer_tower_routes`: both dialogue routes for all eight
  monsters, active-task reminders, typed personality remarks and deterministic
  preview-to-commit repeatable selection. No server is launched.
- `tests/myworld/test-monster-slayer-foundation.py`: legacy definition, state,
  costs and Odyssey regression suite.

Full contact-route suite currently encounters an unrelated Heroes' Guild terrain
assertion at NPC 850's roam tile (369,435) under the installed native map.
An older ignored local configuration also advertised client 10051, causing its
backpack test to reject the client before reaching that terrain assertion.
The focused tower route gate avoids unrelated map-placement assumptions.
Local configuration used for diagnosis was restored.

Subsequent passes implemented [equipment assembly/effects](slayer-equipment-implementation.md),
[ordinary loot](slayer-ordinary-loot-implementation.md) and
[protection consumable shop pricing](slayer-gimmick-shop-implementation.md).
Tower construction/access NPC placement, optional Balrog/Elder Green Dragon
tasks and the deferred leather audit remain separate. No server restart or deployment.
