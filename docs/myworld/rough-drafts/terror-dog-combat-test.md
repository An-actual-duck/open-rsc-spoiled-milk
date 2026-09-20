# Terror dog combat test contract

NPC 867 is the level 75, floor-four Terror dog. Initial modern combat tuning:
90 HP, melee offense 80, and melee/ranged/magic defense 55 each. Legacy combat
stats remain placeholders, not the source of its offense or defenses. It uses
ordinary two-tick melee attacks and its approved existing attack animation.

## Feeding Frenzy

A damaging primary melee hit rolls an extra ordinary melee hit for every other
living Terror dog within Chebyshev radius two of the attacker. Idle dogs and
engaged dogs both count; removed, respawning, dead, and other-plane dogs do not.
Primary hits are red; extra bites are yellow on the same tick, independently
rolled with normal defense and mitigation. Extra bites do not recurse. A miss,
suppressed hit, or zero actual primary damage does not trigger frenzy. Death or
participant replacement stops remaining bites. Each attacking dog counts its
own neighbors: three adjacent attacking dogs can produce nine hits per round.

## Dog Treats

Items 3327–3329 hold three, two, and one uses, using the temporary potion sprite.
They are nonstackable, untradeable, and usable during combat via Scatter.
Each use replaces the same inventory slot; the final use leaves an empty vial.

- Use text: `You scatter treats all around you.`
- Examine: `Something for your dog to chew on for awhile besides you`
- Protection: ten minutes, refreshed on reuse, displayed as `Dog Treats` in HUD.
- Protection suppresses frenzy only; ordinary melee remains unchanged.
- `::slayergimmicks` grants one full three-use item for each implemented counter,
  now four inventory slots total.

No tower spawns, shops, drops, or task assignments are added by this combat slice.

## Verification

Run `ant -f server/build.xml test_terror_dog_combat` using the bundled Ant, plus
`python3 tests/myworld/test-slayer-movement-preview.py` and
`python3 tests/myworld/test-cleric-status-hud.py`.
The server fixture covers radius boundaries, excluded neighbors, both melee
event paths, multiple attacking dogs, independent yellow bites, protection,
zero/suppressed hits, death/lifecycle cutoff, consumable depletion, stale items,
full inventory replacement, status expiry, and exact use/examine text.
