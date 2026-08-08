# A07.5J Bear Maul Second Hit

A07.5J extracts only Bear Maul's identical primary-melee eligibility gate:
player source, complete Bear set, positive settled primary hit, and living
target. `BearMaulSecondHit` invokes the existing event-owned auxiliary
true-damage callback with the unchanged primary amount.

Projectile combat, primary-hit ordering, Scythe cleave, mitigation,
contribution, hitsplats, lifesteal, packets, death handling, and all other
post-root effects remain event-owned. The compiled fixture covers full/partial
equipment, zero hit, dead target, NPC source, and callback payload.
