# Bloodveld combat test contract

NPC 868, level 85, planned floor four alongside Terror dog. No special counter
item or addition to `::slayergimmicks`. No permanent spawns, shops or drops added.

## Combat

- Modern stats: 150 HP, melee offense 80, all three defenses 30.
- Standard two-tick melee, original gape/chomp animation.
- Melee heals `floor(actual damage / 2)`, capped at missing HP. No healing on
  zero damage and no resurrection. Both melee event paths use resolved damage.
- Tongue reaches five tiles, deals no damage, and has a shared three-tick
  attack cooldown. Melee cannot bypass that cooldown after a pull.
- At clear pulling range it holds position, including during cooldown. Outside
  usable range it approaches normally, subject to existing leash restrictions.
- One-tick windup before pulling to the adjacent tile along the direct path.
  Every walking edge and projectile sight must be valid; blocked paths fail
  closed, never drag through walls or corners. Adjacent targets get melee only.
- Impact rechecks actor lifecycle, health, range and collision. Movement by the
  target during windup cancels the pull; teleport/logout/death cannot pull a
  stale target. No root or attack lock is applied.
- Blank projectile presentation triggers the tongue animation without drawing
  a detached missile. The pull preserves combat ownership and authoritative
  layered player location, rather than calling ordinary teleport/disengage.
  A world-info reset clears local-client waypoint interpolation so the player
  snaps instantly at impact rather than visibly walking across the gap.

## Art

Owner approved tongue strip v3 on 2026-09-20. Original approved sheet retained
as `dev/myworld/assets/sprites/npcs/bloodveld-tongue/approved-base.png`; generated
source retained beside it as `approved-tongue-strip.png`.
`tools/myworld/PackBloodveldTongue.java` appends a 220x110 attack column using a
fixed nearest-neighbor 4.8:1 scale and common baseline. The original 720x330
movement/bite region is preserved pixel-for-pixel. Client slots 18–20 are tongue
poses; 15–17 remain bites. Wider canvas does not rescale the body per frame.

## Testing

Bundled Ant target `test_bloodveld_combat` exercises stats, range, blocked path,
hold/approach, delayed movement, cooldown, melee switch, cancellation and healing.
`python3 tests/myworld/test-slayer-movement-preview.py` verifies disk/JAR art,
definitions and frame mapping. Regression targets: `test_naga_combat`,
`test_ranged_approach`, `test_terror_dog_combat`.

Private acceptance: `::spawnnpc 868 4 10`, attack from melee and five tiles with
throwing weapons or magic, watch tongue/bite selection and green healing hits;
try a wall, move during windup, and walk away beyond the leash. Live visual and
balance approval remains a user test. No public restart is authorized here.
