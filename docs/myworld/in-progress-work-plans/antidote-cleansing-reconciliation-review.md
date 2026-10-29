# Antidote cleansing reconciliation — Core review

Reconciles the owner-approved September antidote/Titan correction from
`4d98e17833135ae3f150f7ad953099b184fe91d1` onto modern baseline
`67bed332372fb5925092cd68e48001bebe2952cc`. This is a selective source/asset port,
not a historical branch merge. The battleaxe coating is still a separate,
approved but separately pending implementation task; its single-dose yield does not change antidotes.

## Restored behavior

| Antidote | Power added to each poison pulse's cleansing | Recipe | Herblaw / XP | Full yield |
|---|---:|---|---|---|
| Weak |5|Unfinished marrentill455 + yellow Giant Spider Eggs3400|8 /160|3doses3402–3404|
| Antidote |10|Unfinished marrentill455 + existing red spiders eggs219|22 /220|3doses1474–1476|
| Strong |20|Unfinished marrentill455 + green Jungle Spider Eggs3401|38 /300|3doses3405–3407|

One dose provides exactly ten wall-clock minutes. Only the strongest active
potion contributes; equal or stronger doses refresh the full duration, while
weaker doses neither consume inventory nor extend the stronger timer. All nine
doses use real drinking/dose-depletion paths, ending in an empty vial; noted
potions remain non-drinkable. Standard three-dose decanting is restored.

No immediate cure or blanket immunity is granted. On each existing eight-tick
poison pulse, damage still uses power before drain. Drain is base3 plus the
existing Nature-necklace and Carapace bonuses plus antidote5/10/20. Bounded
`PoisonPowerReduction` clamps at zero. Full exhaustion calls the maintained
`Mob.curePoison` in that pulse, clearing the event, cap, provenance and durable
cache through the existing authority. Positive subthreshold power retains the
existing next-pulse cure timing. Potion expiry removes its session attributes
and HUD identity when evaluated; the potion is not persisted as poison state.
The existing ten-minute session behavior is retained rather than introducing
new death, logout or persistence policies for other potion families.

Titan Steel poison material matching now precedes generic Steel: its five
existing poisoned families apply 28, cap 70 (tier 7); ordinary Steel remains 20/50.
This does not add poison to a plain Titan Steel battleaxe or another unpoisoned
weapon. Modern target-owned accumulation, highest-source cap, ownership transfer,
typed DOT damage, restart persistence and lifecycle implementations are retained.
Shared Slow's power, lifetime, caps, decay and producers are unchanged.

## Content and discovery

IDs 3400–3407 were reserved, with no conflicting current custom definitions;
existing gem equipment 3408/3409 is retained. Server definitions, client selectors,
note forms, dose sprites, skill guide/mixing entries, generated descriptions,
status HUD key and deterministic generator inputs are included. Egg icons are
the exact historical 48×32 authored recolors of native reference 2250; alpha,
canvas, neutral pixels and packaged PNG bytes are verified. No new artwork or
custom-content roster was put into the editor.

**Acquisition gap:** the historical revision contains no NPC-drop, gathering,
shop or spawn acquisition implementation for the yellow/green eggs. The audit
finds their names/constants in definitions/helper only, plus recipes elsewhere.
No new drops were invented here. Their production acquisition still needs an
explicit content decision; normal red eggs retain their existing availability.

## Validation and limits

The focused antidote fixture exercises real poisoned players and production
PoisonEvent, not a parallel simulator:100→67 power and 10 HP damage with
3+5+5+20 drain, then 10→0 with immediate event/durable-state cleanup. It checks
refresh/rejection, nine dose transitions/noted rejection, expiry/HUD cleanup,
actual Herblaw recipe level/input paths and actual three-dose decanting.

Shared Slow and the 144-scenario strict combat gate pass. Maintained client
build/packaging, native ingredient/dose icon checks, generator reproducibility,
Herblaw/potion/HUD/poison lifecycle wiring and equipment/Slayer checks are recorded
in `antidote-cleansing-reconciliation-evidence/`. Each JVM build/test is run
sequentially with a 512MiB parent heap; fixture forks are bounded separately.
No alternative fixture logic replaces maintained gameplay.

The owner's October 29 confirmation that both the Slayer Tower and Crafting Guild
gem-dragon cave are authored and awaiting final saved-map migration is recorded
in `next-major-release-goals.md`. No saved map, account, running server, installed
toolkit or live deployment was changed. This is a source-review candidate;
private gameplay acceptance and coordinated update migration remain Core gates.
