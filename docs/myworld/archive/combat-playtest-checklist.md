# Combat Playtest Checklist

This checklist is the first manual validation pass for the current MyWorld PvM
combat baseline. Combat is considered feature-complete until these tests produce
real tuning feedback.

## Scope

- MyWorld is PvM-only.
- PvP, duels, OpenPK, and PK bots are not part of combat completion.
- These checks are for feel, clarity, and edge cases that automated source/data
  tests cannot fully prove.

## Setup

- Use `make combat-check` before manual testing.
- Prefer two or three player clients when possible.
- Use comparable gear tiers when comparing styles.
- Record the NPC, player levels, gear, food used, time-to-kill, deaths/near
  deaths, and any surprising behavior.

## Core Scenarios

- Confirm a solo low-level melee player can fight starter enemies without
  excessive zero-feeling damage or runaway incoming damage.
- Confirm a solo ranged player can attack from distance and that projectiles
  feel visually distinct from melee damage.
- Confirm a solo magic player can attack from distance and that spell
  projectiles/effects feel visually distinct from melee damage.
- Confirm a melee+ranged+magic group can all damage the same NPC at the same
  time.
- Confirm damage-share XP feels reasonable when a group kills the same NPC.
- Confirm each contributor receives a personal loot roll, and that uncollected
  personal drops do not become public to other players.
- Confirm rare normal drops and rare-table drops feel properly reduced for very
  low contributors without making small contributions feel pointless.
- Confirm an NPC with multiple targets prioritizes a melee-range target before a
  lower-level player standing farther away.
- Confirm that if no player is in melee range, the NPC targets the lowest combat
  level threat.
- Confirm mixed-style NPCs still use ranged or magic attacks sometimes while the
  player is standing in melee range.
- Confirm ranged and magic NPC attacks use visible projectiles and can be used
  from distance.
- Confirm demons use their added magic attacks and still feel like demons rather
  than generic casters.
- Confirm giants/elemental enemies using magic attacks feel appropriate and not
  overly punishing.
- Confirm dragons still preserve dragon breath as their unique special pressure.
- Confirm players can flee PvM combat immediately.
- Confirm food and potions can be used in combat without reopening old action
  lock bugs.
- Confirm players can retarget between NPCs during PvM without getting stuck in
  old hard-lock behavior.

## Balance Questions

- Are tier-equivalent melee, ranged, and magic builds close enough in practical
  time-to-kill?
- Do ranged and magic enemies pressure the corresponding player defenses enough
  to make armor choices matter?
- Do weak players in groups feel threatened without making grouping miserable?
- Are personal loot chances generous enough for support or low-damage players?
- Are rare-drop contribution reductions understandable and acceptable?
- Are melee weapon family roles clear enough in real encounters?
- Are ranged weapon family roles clear enough in real encounters?
- Are magic staffs/spells close enough for now pending the deeper magic pass?

## Automated Coverage

- `test-combat-data.py`: generated combat data shape and core tier anchors.
- `test-combat-interaction.py`: PvM interaction gates, retargeting, XP, and
  personal loot source checks.
- `test-npc-attack-styles.py`: ranged/magic NPC profile and projectile source
  checks.
- `test-combat-runtime-invariants.py`: target priority, damage-share XP,
  personal loot, and rare contribution invariants.
- `test-combat-exceptions.py`: PvM-only config and reviewed kill/drop bypasses.
- `test-combat-scenarios.py`: representative group reward, target priority,
  projectile-role, and style-viability scenarios.
- `test-defense-distribution.py`: NPC defense-profile distribution.
- `test-balance-fixtures.py`: player/NPC fixture snapshots and matchup matrix.

## Closeout Rule

If the automated checks pass and this checklist has no severe feel or behavior
failures, combat should remain closed until broader playtesting or a later
feature explicitly requires reopening it.
