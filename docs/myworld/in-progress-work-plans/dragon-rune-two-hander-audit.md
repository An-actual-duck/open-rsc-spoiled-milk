# Dragon versus Rune two-hander audit

Status: investigation complete; no combat values changed.

## Scope

This audit compares the normal Rune, Dragon, and Exalted Rune two-handed
swords. It distinguishes the generated item data used by the server/client
from the one Dragon-specific runtime effect, so a future balance change does
not accidentally remove that effect.

## Current effective item data

| Item | ID | Melee requirement | Aim / power | My World melee offense | Speed | Price | Source |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Rune 2-handed sword | 81 | 70 | 70 / 70 | 152 | 2 | 60,000 | base `ItemDefs.json` plus `ItemDefsMyWorld.json` |
| Dragon 2-handed sword | 1346 | 80 | 99 / 99 | 163 | 2 | 360,000 | `ItemDefsCustom.json` plus `ItemDefsMyWorld.json` |
| Exalted Rune 2-handed sword | 3266 | 90 | 84 / 84 | 174 | 2 | 156,000 | generated Exalted Rune override and custom definition |

All three occupy main-hand slot 4 and use wearable ID 8216, so each excludes
the off-hand as a two-handed weapon. The same generated My World values are
also applied client-side in `MyWorldItemOverrides.java`.

## Acquisition and guide coverage

- Modern Smithing uses the shared three-bar two-hander recipe path. Rune comes
  from a runite bar, Dragon from a dragon bar, and Exalted Rune from a purified
  rune bar. The recipe dispatcher is
  `Smithing.getModernTwoHandedSwordId`.
- Rune is sold by Valaine and appears in the Rune drop tables. Dragon is sold
  by Fionella in the Legends Guild. Exalted Rune is the tier-90 Smithing
  outcome.
- The current Melee guide includes Rune at level 70 and Dragon at level 80.
  It does not list Exalted Rune in that tier list, consistent with the other
  tier-90 Exalted equipment being handled outside the classic guide sequence.

## Runtime behavior

`CombatFormula.usesDragonMeleeBreathWeapon` treats the Dragon 2-handed sword
as one of the Dragon melee weapons. It reduces the normal melee portion as
needed and enables the existing Dragon-breath component. Rune and Exalted Rune
do not receive that proc. No other two-hander-specific special behavior was
found.

Melee aim and power feed the normal accuracy and maximum-damage formula via
`Player.getWeaponAimPoints()` and `Player.getWeaponPowerPoints()`.

## Findings and decisions needed

- The intended tier ordering is intact for requirement and My World offense:
  70/152, 80/163, 90/174.
- Dragon has materially stronger normal aim/power than Rune (+29 each), plus
  the Dragon-breath behavior. Exalted Rune instead has the highest My World
  offense but lower normal aim/power (84) than Dragon (99). This is a real
  cross-system tradeoff, not a data-loading error.
- The price sequence is non-monotonic (60,000, 360,000, 156,000). That may be
  intentional because Exalted Rune is a crafted progression reward; do not
  normalize it without a separate economy decision.

Recommended next decision: choose whether the level-90 Exalted Rune weapon is
intended to be strictly stronger in both combat systems, or whether Dragon's
breath identity is deliberately allowed to retain higher normal aim/power. If
strict progression is desired, tune the Exalted Rune aim/power deliberately
and add a combat characterization test; otherwise add a short player-facing
note that Dragon trades higher direct aim/power and breath for lower My World
offense.
