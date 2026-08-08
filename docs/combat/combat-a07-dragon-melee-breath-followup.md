# A07.5K Dragon Melee Breath Follow-up

A07.5K extracts the identical two-owner post-root gate for Dragon melee breath:
living target, positive existing formula roll, one binary slash visual draw,
and the event-owned auxiliary true-damage callback.

`CombatFormula.rollDragonMeleeBreathDamage` remains an owner callback, so
weapon eligibility and its established random source are unchanged. Both event
owners retain primary ordering, combat-mode gates, mitigation, contribution,
hitsplats, lifesteal, packets, and death handling. Ranged Dragon breath and
all other weapon effects are excluded.

The compiled contract covers positive callback delivery, the first visual,
zero-roll rejection, and dead-target rejection.
