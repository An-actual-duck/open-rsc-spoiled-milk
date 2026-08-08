# A07.5I Elder Green Dragon Armor Trigger

## Scope and authority

A07.5I extracts only the identical Elder Green armor trigger from reciprocal
melee, PvM melee, and projectile impact: positive settled primary damage,
equipped chance, one chance draw, and inclusive `0..10` primary-payload draw.

`ElderGreenDragonArmorProc` invokes an owner callback with that one payload.
The unchanged `ElderGreenDragonArmorEffect` retains all content authority:
source/target validation, presentation, true-damage settlement, target
selection, secondary damage, burn replacement/refresh, event scheduling,
attribution, and death behavior.

## Executable evidence

The compiled trigger fixture covers successful maximum payload, failed chance,
zero-primary no-draw, and missing-set no-draw behavior. Existing Elder armor
fixtures continue to cover the content-owned Breath and burn paths.
