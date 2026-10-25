# Battleaxe Slow/poison coating — audit and design proposal

2026-10-25. **REVIEW REQUIRED: no coating balance, items or production art approved or implemented.**
Worker `feat/battleaxe-slow-poison-coatings`; starting HEAD
`ed8b55bfa94b4d2471b8421f0ba68c8ea2dbe0fa` (clean ACTIVE slot).
The handoff's earlier baseline is `821526e736e112c8db0c8716c65a80c14812628b`.
Core owns integration, private gameplay and visual acceptance. This is Phase 1.

## Approved requirements versus proposals

Approved: battleaxe focus, one Cockatrice Eye plus one Sticky Saliva Gland,
existing independent 1/128 ingredient drops unchanged, weaker accumulating
poison plus meaningful accumulating shared Slow. Preserve ordinary weapon
stats/speed and unrelated effects. Shared Slow already supplies 10 power per
tier, source caps 12/22, decay 2 per 4 ticks, retained highest cap, target-owned
pool and latched subsequent combat deadlines. No movement/utility delay,
percentage Slow, PvP activation or separate poison engine.

**Every recommendation and number below for the new coating is a proposal,
not an implementation default.** Antidote values below are approved preserved
requirements, not new balance suggestions. Silence cannot approve them.

## Audit: current and preserved poison/antidote

- `server/src/com/openrsc/server/content/PoisonPower.java`: ordinary poison
  applies tier×4, max tier×10 on a successful weapon proc. Titan Steel matches
  `STEEL` before `TITAN_STEEL`, incorrectly resolving to tier 5; preserved
  `4d98e17833135ae3f150f7ad953099b184fe91d1` fixes it to tier 7 (28/70).
- `content/PoisonProcChance.java`: target/source-qualified weapon chance starts
  100%, then 50%, steps down by 10 to 20%; five failures recharge it.
  This is not a constant 20% proc. Armor uses its separate existing policy.
- `event/rsc/impl/PoisonEvent.java`: one poison pulse each 8 ticks; damage
  floor(power/10), then drain 3 plus Nature necklace and carapace bonuses.
  Current subtraction is not zero-clamped. Antidote bonus is absent.
- `model/entity/Mob.java:applyPoison`, `model/combat/dot/PoisonTargetState.java`
  and `PoisonTargetPolicy.java`: retain modern atomic target update, highest
  source cap, bounded addition, provenance transfer only on an actual increase,
  typed damage ownership, durable poison state and current lifecycle rules.
  Relevant modern introduction: `56d75c79750277f48c5d784ac50795e35d4e068e`.
  Never replace these files wholesale with September versions.
- `plugins/authentic/itemactions/Drinkables.java` current `antidote` dispatch
  cures and grants five-minute protection. Approved September lineage instead
  adds +5/+10/+20 cleanse for ten wall-clock minutes, strongest potion only;
  equal/stronger refresh, weaker rejects without consumption. Session lifetime,
  dose transitions, status HUD, decanting and recipes are part of that lineage.
- `4d98e17833135ae3f150f7ad953099b184fe91d1` contains
  `content/AntidoteCleansing.java`, `defs/AntidoteItemDefs.json`, generator
  `tools/generators/generate-antidote-items.cjs`, override input
  `52-antidote-cleansing.json`, client mappings/icons/HUD, Drinkables/CombinePotions,
  `ItemHerbSecond.xml`, focused fixtures and documentation. These are absent
  here, not newly undecided. Preserved Slayer tip is
  `85ed0e8a05c13143d318336f680e786849a0b75a`; no branch-wide import is proposed.
- Approved recipes: unfinished mar(r)entill potion + yellow Giant Spider Eggs
  at level 8, red spider eggs at 22, green Jungle Spider Eggs at 38; three doses.
  Historical IDs 3400–3407 must be collision-checked on current generators.
  Historical audit says new yellow/green egg acquisition was not supplied;
  do not infer new drops. Necklace/carapace/potion additive cleanse, damage
  before drain, zero-clamp and full exhaustion cleanup must survive reconciliation.
- `plugins/authentic/itemactions/InvItemPoisoning.java` finds variants by name;
  no ordinary poisoned battleaxes currently exist. Use explicit future coating
  mapping and atomic inventory validation rather than extending its permissive
  name lookup or copying its sequential remove operations.
- `PvmMeleeEvent.java:applyWeaponPoison` runs before primary damage settlement;
  it is also called for scythe secondary targets. New Slow must attach to the
  resolved positive primary battleaxe hit, once, with surviving/living target
  validation. Do not put new Slow into generic damage callbacks or this shared
  poison helper blindly. Preserve existing poison roll order for other weapons.

Focused prerequisite reconciliation after approval: port antidote recipe/dose/
HUD/generator behavior, additive bounded drain and Titan ordering, adapted to
current status and persistence APIs. No blanket immunity restoration or
modern combat/lifecycle replacement. No prerequisite implementation in this
proposal checkpoint.

## Exhaustive definition-derived battleaxe matrix

Definitions merged in current MyWorld order: `ItemDefs.json` +
`ItemDefsCustom.json` + generated `ItemDefsMyWorld.json`. Full JSON/CSV evidence
will accompany the independent coverage checkpoint. MO = explicit melee offense;
A/P = legacy aim/power, retained as authored. All require skill 0 (Melee), main
hand slot 4, wearable mask 16. Speed **category** 3 is neutral 1.0 multiplier;
normal player PvM cadence is 3 ticks before armor/potion/set modifiers and Slow.
Do not misread category 3 as a universal fixed interval.

| ID / base name | Tier / Melee level | MO; A/P; prayer | Speed | Value | Inventory / appearance | Proposed counterpart |
|---|---|---|---|---|---|---|
| 2002 Tin battle Axe | 1 / 1 | 1; 4/5; 0 | 3 | 72 | items:84 / 710 | Viscous Tin battle Axe (ID unallocated) |
| 429 Black battle Axe | steel-equivalent / 5 | 38; 15/20; 3 | 3 | 1248 | items:84 / 115 | Viscous Black battle Axe (ID unallocated) |
| 2156 White battle Axe | steel-equivalent / 5 | 38; 15/20; 3 | 3 | 1800 | items:84 / 115 | Viscous White battle Axe (ID unallocated) |
| 3118 Grey battle Axe | steel-equivalent / 5 | 38; 15/20; 3 | 3 | 1800 | items:84 / 115 | Viscous Grey battle Axe (ID unallocated) |
| 2013 Copper battle Axe | 2 / 8 | 6; 5/7; 0 | 3 | 144 | items:84 / 711 | Viscous Copper battle Axe (ID unallocated) |
| 205 Bronze battle Axe | 3 / 15 | 12; 7/9; 0 | 3 | 300 | items:84 / 109 | Viscous Bronze battle Axe (ID unallocated) |
| 89 Iron battle Axe | 4 / 22 | 25; 9/13; 0 | 3 | 720 | items:84 / 110 | Viscous Iron battle Axe (ID unallocated) |
| 90 Steel battle Axe | 5 / 30 | 38; 15/20; 0 | 3 | 1800 | items:84 / 111 | Viscous Steel battle Axe (ID unallocated) |
| 91 Mithril battle Axe | 6 / 38 | 50; 21/29; 0 | 3 | 4800 | items:84 / 112 | Viscous Mithril battle Axe (ID unallocated) |
| 2024 Titan Steel battle Axe | 7 / 46 | 56; 25/35; 0 | 3 | 10800 | items:84 / 712 | Viscous Titan Steel battle Axe (ID unallocated) |
| 92 Adamantite battle Axe | 8 / 54 | 62; 30/41; 0 | 3 | 22800 | items:84 / 113 | Viscous Adamantite battle Axe (ID unallocated) |
| 2035 Orichalcum battle Axe | 9 / 62 | 69; 38/52; 0 | 3 | 39000 | items:84 / 713 | Viscous Orichalcum battle Axe (ID unallocated) |
| 93 Rune battle Axe | 10 / 70 | 75; 47/64; 0 | 3 | 60000 | items:84 / 114 | Viscous Rune battle Axe (ID unallocated) |
| 2752 Dragon battle Axe | 11 / 80 | 82; 69/75; 0 | 3 | 280000 | items:272 / 162 | Viscous Dragon battle Axe (ID unallocated) |
| 3269 Exalted Rune battle Axe | 12 / 90 | 88; 60/78; 0 | 3 | 156000 | items:84 / 1049 | Viscous Exalted Rune battle Axe (ID unallocated) |

Fifteen distinct catalog IDs, not fourteen: Exalted Rune is present beyond the
handoff examples. Recommend all fifteen; white/grey/black are side-grade
prayer weapons, not a reason to invent another level gate. Grey and Exalted
are missing from Equipment's battleaxe high-roll family array; preserve each
base's current behavior initially and ask Core before fixing the base omission.
Dragon has the existing Hero's quest equip gate when quest bypass is disabled;
coated form must preserve it. No battleaxe is marked retired in these item
records. Quest/special exclusions: bloody axe of Zamorak is an axe-shaped
quest object, Bound Battleaxe is a summon/UI entity, not another eligible
player battleaxe; ordinary axes/hatchets, scythes and all other weapons excluded.

Source of truth: base/custom server JSON plus client `EntityHandler.java`;
combat overrides generated by `tools/generators/generate-item-overrides.py`
from `10-melee-weapons.json`, `15-black-and-white-weapons.json`,
`49-equipment-economy.json`, `50-exalted-rune.json`. Future mapping should be
one reviewed data table generating server/client forms, source IDs and sprites;
allocate appended IDs against current definitions, not historical free slots.
No IDs allocated here. Client base palettes: bronze FF6619, iron EEDD DD
(spelling rendered numerically in coverage JSON), steel EEEEEE, mithril 99B3CC,
adamantite B2CC99, rune 00FFFF, black 303030, tin B7C9D9, copper C86A2B,
titan 8EA6BB, orichalcum 5A3F7D, white E7E2D6, grey 9EA59F,
dragon FF0044; Exalted uses EXALTED_RUNE_COLOR. Numeric extraction in coverage
is authoritative over informal color labels here. Held family is authentic
`battleaxe` for each appearance, including Dragon; inventory Dragon uses its
separate authentic silhouette. White/grey reuse appearance 115: verify actual
held color resolution, do not promise matching inventory tint.

## Recipe and economy recommendation — ALL PROPOSED

Name: Viscous weapon coating (3), (2), (1); weapons: Viscous <base name>.
One clean avantoe + water vial makes an unfinished avantoe base; add one Eye
and one Gland atomically to yield one three-dose coating, Herblaw level 46,
recipe XP 300 before ordinary multipliers. No extra Slayer rank to apply/wear.
A dedicated three-ingredient completion path should validate everything before
consuming either rare ingredient; do not consume one into a dead-end intermediate.
One dose transforms one eligible unnoted inventory battleaxe, preserving base
requirements and trade/equip/death policies; final dose returns empty vial.
Reject notes, equipped items, invalid weapons and identical recoating without
loss. No drinking effect. Within-family decanting only.
Recommend permanent item variants (no charges/timer), avoiding save migrations
and hidden expiry. Optional explicit wipe action returns base, no refund; confirm
wipe UI before implementation. Reject ordinary poison on coated weapons and
vice versa with no loss. No simultaneous ordinary weapon-poison layer.
Value proposal: 900/600/300 per 3/2/1-dose vial; counterpart base value +300;
tradeable under base policy. Availability crafting only through existing
ingredient drops/herb access; no new shop, spawn, drop change or rare-rate change.
These choices are a compact single progression, not newly invented low/mid/high
coating families. Existing weapon requirement tiers supply progression.

## Combat recommendation — ALL PROPOSED

Per resolved positive **primary main-hand battleaxe** hit to a surviving legal
NPC: apply **4 Slow power, source cap 12, 100% eligibility (no extra Slow RNG)**.
Zero/blocked/secondary/offhand/poison/reflection hits never produce Slow.
Use `Slow.apply` and its current retained-cap/decay rules; no extra timer.
Allow existing generic source eligibility; no new blanket boss immunity, no
PvP damage or timing activation. Owner must confirm this policy.

Poison proposal: successful existing `PoisonProcChance.rollWeapon` applies
**max(1,floor(tier×2))**, cap **tier×5**; side-grade black/white/grey use tier 5.
Use one existing poison pool, existing source policies, and one weapon poison
roll. This halves ordinary tier×4/tier×10 potential. Tier 1 cap5 contributes
no solo damaging poison under existing below10 cleanup; tier2 cap10 barely
crosses it. This low-tier limitation is explicitly a decision, not a hidden
minimum-cap change. Recommend accept tier-scaled weakness for consistency;
alternative flat4/cap20 would give meaningful poison even at tin, but would
make low-tier poison comparatively strong and is not the recommendation.

No intrinsic Titan battleaxe poison producer was found in current PoisonPower,
PvmMeleeEvent or Equipment: uncoated2024 does not qualify as a POISON-named
weapon. Do not invent intrinsic poison. Preserve current base behavior and
check release-separated approved lineage before implementing. If Core supplies
an intrinsic producer, resolve once: retain that approved weapon poison payload
and add only the new Slow, or explicitly approve replacement; do not double-roll
or stack a second weapon pool. Existing armor poison remains separate and its
current combined cap/max-applied behavior is retained.

Buildup at neutral3-tick cadence, no misses/modifiers, first hit tick0:
Slow power after hits at0/3/6/9/12 is4/8/10/12/12 (due decay before tick12 hit).
Slow1 first appears on third qualifying hit at tick6, not first-hit stun.
At cap12 it falls to10 then8 without hits: effective tier disappears at the
second due decay pulse (4–8 ticks after last top-up depending on phase).
A single source cannot reach Slow2. After a stronger cap22 source, this coating
may refill retained22 as the approved shared rule requires. Multiple attackers
fill the same pool faster; never independent per-attacker Slow caps.
At4-tick successful cadence net buildup2/hit reaches10 on fourth hit tick12;
at6-tick cadence average net1/hit, boundary-sensitive; at8 ticks it does not
accumulate beyond4 in sustained neutral conditions. Misses and attacker Slow
therefore matter. Speed buffs can shorten cadence and accelerate buildup.
Poison alone: rune proposal20/max50 versus ordinary40/max100; three-tick hits
can sustain it but dynamic proc failures and cleanse3/8ticks mean uptime is
not guaranteed. Existing weak poison may cure below10 before another proc;
use the real engine, no bespoke subthreshold grace period. Antidote+5/+10/+20
and equipment directly contest poison; they do not cleanse Slow by implication.

## Art plan and review coverage

Read library README, axe/potion notes, contact sheets and full-canvas anchors
before edits. Use authentic items84 (48×32, bounds5,1,39,30), Dragon272
(48×32, bounds5,1,41,30), poison52 (48×32) as independent shape anchors.
Propose dark teal slime with bright turquoise accents restricted to blade
edges, retaining each metal interior, handle and transparency. Existing ordinary
poison is green; teal distinguishes Slow while staying adjacent in meaning.
Palette compatibility is a visual review question; no production recolor yet.
Use controlled edits, not regenerated geometry, and inspect every tier at native
size plus nearest-neighbor zoom. Isolated output candidates are not usable items.

| Coverage | Required review artifact | Phase1 status |
|---|---|---|
| 15 inventory/ground forms | Base/candidate pairs at1× and4×, source/palette manifest | Planned isolated candidates |
| 3/2/1 coating doses | Same bottle silhouette, readable fill levels | Planned concept candidates |
| Held family | Every stored battleaxe frame, mirrored directions, attack frames, palette per tier | Read-only archive coverage pending |
| Equipped composition | Hand gaps, body overlap, directional layer order | Only if existing offline tooling supports; never fabricated acceptance |
| Archives/IDs | Before/after hashes, unrelated entries byte-identical | No production archive changes planned in Phase1 |

## Decisions requiring Core/owner feedback

1. Approve avantoe1 + Eye1 + Gland1, level46, XP300, one3-dose vial;
   names, values900/600/300 and base+300, crafting-only availability?
2. Approve permanent forms, one dose/weapon, wipe/no refund, reject identical
   recoating, no ordinary poison layering and no loss on rejection?
3. Approve4 Slow/cap12 on every positive resolved primary hit, no extra RNG;
   retained shared caps and existing NPC/boss eligibility?
4. Approve half-tier poison with existing dynamic proc; explicitly accept
   tin's non-damaging solo poison / copper's marginal threshold, or select a
   different low-tier rule? No silent minimum or fallback will be implemented.
5. Approve all15 including Exalted and side-grades; preserve base quest/prayer
   checks and current high-roll family behavior? Separate base omission fix?
6. Confirm no missing approved intrinsic Titan battleaxe effect in preserved
   release content; provide exact lineage if one exists.
7. Approve teal edge treatment after viewing isolated candidates. Held palette/
   direction coverage remains technical review, then owner visual acceptance.

No implementation dependent on these decisions begins before explicit feedback.
Approved antidote reconciliation is identified, but no historical bulk restore
will be attempted while the new coating design is awaiting review.
Broader post-Slayer effect-standardization and enemy-element audit remain open.
