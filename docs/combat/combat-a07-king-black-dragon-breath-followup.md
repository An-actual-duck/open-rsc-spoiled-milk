# A07.5G King Black Dragon Breath Follow-up

## Scope and authority

A07.5G extracts only the duplicated King Black Dragon (KBD) post-poison
payload from the reciprocal-melee, PvM-melee, and projectile surviving-target
owners into `KingBlackDragonBreathFollowup`.

The extractor owns the complete KBD-set and exact `"king_black"` marker gate,
the inclusive `0..10` true-damage payload draw, and the second inclusive
`0..2` elemental choice: Water applies 10% max-hit reduction, Earth applies
6% attack-speed reduction, and Fire applies 6% defense reduction. Each target
debuff retains its existing five-target-attack lifecycle. The elemental choice
still occurs after the event-owned damage callback, including when its payload
is zero or the callback causes death.

## Explicit exclusions

The owners retain primary-hit eligibility, the 40% KBD poison chance, poison
application/cap, marker creation and clearing, shared Black/KBD breath
presentation, true-damage settlement, contribution, hitsplats, mitigation,
lifesteal, and death adapters. Black Dragon has its own lower-tier marker and
payload executor. No Elder Dragon, DoT, splash, reflection, or unrelated
combat family is changed here.

## Executable evidence

The compiled A07.5G fixture proves the exact KBD marker and full-set gate,
inclusive payload range, all three elemental outcomes, zero-payload elemental
application, five-attack expiration, and no random draws for partial or
wrong-marker calls. Existing compiled three-owner Black Dragon marker/poison
coverage and shared leather structural guards continue to protect the owner
boundaries.

This is a server-only preservation extraction; it needs no private visual
acceptance.
