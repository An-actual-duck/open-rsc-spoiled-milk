# Cockatrice combat test contract

NPC **864**, Slayer tower floor 2 (Adept), combat level **35**. This promotes
the approved movement preview into a manually spawnable combat test NPC; no
permanent tower spawns, shop placement, drops or Slayer roster changes yet.

## Initial tuning

- 45 HP; explicit modern melee offense 40.
- Explicit ranged defense 40, melee defense 35, magic defense 30.
- Legacy attack/strength/defense placeholders remain 1; they are not the source
  of the modern combat values. No ranged or magic attack profile.
- Normal NPC melee pursuit and two-tick attack cadence. Nonaggressive for this
  isolated test, like the frog; retaliates when attacked.
- Approved three-frame talon attack is enabled without modifying its artwork.

## Stony Glare

Every admitted, unsuppressed melee swing applies the glare to a living player,
even on zero damage. It is not dependent on the player's facing direction.
Movement freezes for **one game tick**. The queued path is preserved and may
continue after release. Multiple glares in that tick do not extend the freeze.
Release says exactly: **Your legs break free but you can't move your arms**.

Melee, ranged, thrown and magic attacks are blocked for **10 seconds**, refreshed
rather than stacked. Repeated normal attacks therefore sustain the attack lock.
Already-launched projectiles remain valid. Movement, food and counter usage
remain possible after the brief freeze. The leg timer is independent of the arm
timer and is not persisted across login; arm/protection expiry uses wall time,
as with the frog. Killing the attacker does not prematurely clear the arm lock.

## Eye Drops

IDs **3321 / 3322 / 3323** hold **3 / 2 / 1 uses**. Apply clears Stony Glare and
protects from both its effects for **10 minutes**, refreshed by each use.
It does not clear frog saliva or poison. Potion sprite placeholder; nonstackable,
untradeable, nonnoteable, no shop price assigned yet. Each use replaces the exact
bottle in its slot, ending with an empty vial, including in a full inventory.
Stale actions cannot consume another bottle. Apply is allowed during combat.

HUD labels: **Stony Glare**, **Eye Drops** (no dose suffix).
`::slayergimmicks` grants one full solvent and one full eye-drop bottle, requiring
two free slots, without activating either. Test spawn: `::spawnnpc 864 4 10`.

## Verification

Focused fixture `CurrentCockatriceCharacterization` covers modern values, melee
profile and actual swing integration, one-tick movement release and exact text,
refresh/expiry, all attack styles, effect isolation, immunity and dose conversion.
Run it alongside `CurrentGiantFrogCharacterization`, `SlayerGimmickTestKitFixture`,
`tests/myworld/test-slayer-movement-preview.py` and `test-cleric-status-hud.py`.
In-game check: unprotected glare locks attacks but permits retreat after one tick;
Apply Eye Drops and verify ordinary melee combat and the approved talon animation.
