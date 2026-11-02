# Slayer Tower entrance and stair guards — migration candidate

Owner direction, October 26, 2026: preserve the finished tower and prepare one
coordinated future update. No import into the official server or public restart
is part of this task.

## Private review correction — November 2, 2026

Clicking a rank-restricted entrance or upward staircase now triggers the nearby
associate's ordinary NPC speech, like the existing restricted guild entrances.
Previously the object action sent the associate's refusal as a plain player
interaction message, while direct Talk-to already used NPC dialogue.

The object action uses the existing nearby-visible-NPC helper and its normal
script interaction/cleanup, selecting the corresponding associate on that floor.
Rank checks remain independent of NPC availability. A missing or busy associate
produces a neutral retry notice; it cannot grant access or steal an existing
conversation. Qualified players retain the ordinary door/stair transport.

The regression fixture reproduced the old failure at the actual entrance, then
passed production NPC lookup/chat/script binding and cleanup for all six mapped
guards, qualified passage, and busy-guard fallback. Existing direct Talk-to,
rank/floor, free-exit, room-door and player-state checks also pass. The release
gate passed against the exact owner-generation-4855 map, including its actual
configuration, placements, contacts and version-5 progression. Compiler checks
passed with no new gated warnings. Source data, map placements and client code
are unchanged by this correction; private visual acceptance remains separate.

Evidence and exact commands:
`/home/justin/.local/share/tower-guard-dialogue-fix-20261102/`.
The initial combined command stopped because its release target lacked the
mandatory explicit package argument; that log is preserved. The release gate
was rerun successfully with the reviewed package and manifest hash.

## Bindings

The game-owned `MonsterSlayerTowerPassages` class restricts only these authored
`global` world-space placements (all scenery, not wall objects):

| Passage | Position | Definition / direction | Rank |
| --- | --- | --- | --- |
| Tower entrance, entering from south | 22,474,L0 | 1337 closed / 1338 temporary open, direction 6 | Fledgling |
| Stairs to second floor | 6,472,L0 | 43, direction 0 | Adept |
| Stairs to third floor | 37,472,L1 | 43, direction 0 | Veteran |
| Stairs to fourth floor | 6,472,L2 | 43, direction 0 | Elite |
| Stairs to fifth floor | 37,472,L3 | 43, direction 0 | Champion |
| Stairs to sixth floor | 6,472,L4 | 43, direction 0 | Hero |

At entrance execution the south side is y474 (the approach is y475); both
x22/x23 lanes cross to y473 using the existing generic double-door passage.
The temporary open visual stays blocking, so another player cannot simply walk
through a qualified player's animation. Inside-to-outside use remains free.

Rank enforcement does not require talking to the associate or finding a nearby
NPC. The associate plugin claims only denied actions. Eligible use falls through
to the existing ladder/door behavior, preserving its native signed-level
transport, landing safety and auto-close semantics. Downward commands and
objects, other doors, other directions, coordinates, levels, world spaces and
non-MyWorld profiles remain outside these bindings. Missing/corrupt Slayer data
fails closed only on the mapped restricted passage.

Upper-floor dialogue now refers to stairs/upstairs. The initial friendly guard
still explains how to join via Hobart in Falador, and Veteran credentials remain
a button. No rank, enrollment, task, currency or inventory mutation was added.

## Stationary placement plan

The six existing definitions871–876 remain nonattackable and nonaggressive.
Manager-owned staged map work places them with min/max roam bounds equal to the
spawn tile (no wandering):
871 at23,475,L0;872 at8,471,L0;873 at39,471,L1;874 at8,471,L2;
875 at39,471,L3;876 at8,471,L4. Map ownership, original save preservation and
export/manifest hashes belong to the manager's separate migration ledger.

These coordinates intentionally keep safe-room auto-close doors unrestricted.
The staged map and this source candidate must move together. Future tower edits
that relocate a restricted passage require the matching reviewed binding change;
there is no broad definition-ID rule in the editor or map loader.

## Validation and remaining acceptance

The existing real-definition/dialogue/rank fixture now also tests all five signed
level scopes and accepted upward commands, direction/type/world-space exclusions,
free exits and safe-room doors, the actual plugin rank matrix, corrupt/missing
state and non-MyWorld exclusion. Definition assertions include nonaggression.

Offline commands (JDK8, ANT_OPTS=-Xmx768m), from `server`:

```sh
sh ../tools/vendor/apache-ant-1.10.5/bin/ant \
  test_slayer_tower_associates test_monster_slayer_tower_routes \
  test_monster_slayer_contact_routes test_monster_slayer_player_state \
  test_slayer_gimmick_shops test_slayer_retirement test_slayer_leather \
  test_slayer_leather_effects
```

PASS all eight requested targets (including underlying Influence access and
roster migration characterizations); source core and plugins compiled. Log:
`/home/justin/.local/share/slayer-tower-migration-focused-gates.log`.
A final focused associate fixture rerun covers the last harmless-command exclusion
and verifies nonaggressive flags:
`/home/justin/.local/share/slayer-tower-migration-final-associate-gate.log`.

The fixture exercises the production PluginHandler scheduler: a denied action
schedules only MonsterSlayerTowerAssociates.onOpLoc; an eligible action schedules
the existing Default.onOpLoc. It does not execute a substitute transport.
Reflection of the actual Ladders.coordModifier with real scenery43 confirms a
three-tile footprint and signed-level-up landing `(x,475,next level)` from the
bound `(x,472)` origin. Downstairs44 at the same origin uses `(x,471,previous
level)`. The manager validates those authored landing floors in the staged map.
The entrance captures only `open`; examination and unrelated commands are free.

 In-game visual placement,
actual door/stair transport and owner rank acceptance remain private gameplay
gates; no live behavior, owner save or installed map was changed by this worker.
