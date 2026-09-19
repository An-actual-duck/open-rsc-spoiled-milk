# Banshee combat test contract

NPC **865**, level **50**, floor 3 (Veteran). Combat-ready for private/manual
spawning; tower placement, task roster, shop prices and loot remain separate.

## Positional attacks

- Initial tuning: 60 HP, explicit modern melee offense 55 and magic offense 55;
  melee/ranged/magic defenses all 40. Legacy structural stats remain 1.
- Always melee at adjacent range, always magic beyond adjacency when a clear
  projectile path exists within five tiles. No random adjacent spell selection.
- Holds casting range through cooldown, approaches only when outside range or
  obstructed; does not retreat/kite. Normal leash and spatial checks remain.
- Melee interval: two ticks. Magic interval: three ticks. Shared cooldown across
  behavior and melee-event processing prevents double attacks on a style change.
- Magic uses the existing holy projectile. The approved fifteen-frame sheet has
  no attack column: its three side poses are copied in memory into attack slots
  for both melee and casting. Original pixels/files remain unchanged.

## Wail replacement

Every primary melee/magic hit checks earplugs at damage application/impact time.
Without protection, replace normal damage with a uniform integer roll from zero
through `floor(maximum HP * 0.9)`, inclusive. Maximum HP includes active maximum-HP
bonuses, not current remaining HP. Never add a second wail hit or normal damage.
One attack therefore cannot kill a player who was at full maximum health.
Injured players can still die; separate hits from multiple attackers still add up.

The replacement bypasses defense rolls, armor/potion reductions, summon
absorption, Frostbite reflection, True Defense and Cleric damage prevention.
Ordinary attack admission, attack suppression, resolved damage/death handling and
post-damage reactions remain. Suppressed attacks do not receive a replacement.
With earplugs, the normal melee/magic damage and prevention paths apply.

Each damaging unprotected hit says exactly:
"The Banshee's wail pierces your eardrums and ripples through your body"
Zero rolls and protected hits do not show this message. This is attached to
ordinary attacks, not a separate scream cast, pulse or area attack.

## Wax earplugs

IDs **3324 / 3325 / 3326** contain **3 / 2 / 1 uses**. Potion placeholder sprite;
nonstackable, untradeable, nonnoteable. **Insert** is usable during combat and
grants/refreshes **10 minutes** of protection. The inventory follows the existing
three-use counter pattern, replacing the exact item in its slot, ending with an
empty vial. Stale actions and absent items cannot grant protection.

Examine text exactly: **Desolve after 10 minutes**.
Active HUD name: **Wax earplugs**, without the remaining-use suffix. No armor
slot is occupied. Protection expiry is wall-clock based, including offline time.
Inserting before projectile impact protects that hit; expiry before impact
restores the dangerous roll. It does not protect against other Slayer gimmicks.

`::slayergimmicks` grants one full bottle each of solvent, eye drops and wax
earplugs, requiring three free slots. Test spawn: `::spawnnpc 865 4 10`.

## Verification

`CurrentBansheeCharacterization` tests the modern values, both melee damage
paths and projectile damage, maximum/zero rolls, full-HP safety at small and
large HP values, protection applied/expired in flight, exact message, positional
switching, range holding, shared cooldown, uses, expiry and full inventory.
Run the frog, cockatrice and gimmick-kit fixtures as regressions. Client sprite
tests check reused attack pixels and unchanged movement, and HUD tests check
the dose-free label. In-game validation of the reused attack poses remains useful.

Focused fixtures and client checks pass. The broader combat suite still stops
at the pre-existing `current_projectile_impact_lifecycle_policy_is_characterized`
native-terrain fixture error: "Ordinary movement cannot leave native package
terrain". This does not count as a full-suite pass.
