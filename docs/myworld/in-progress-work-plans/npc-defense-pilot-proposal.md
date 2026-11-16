# NPC Defense Pilot Proposal

Status: documentation-only draft for owner review. **No proposed number is
approved or installed. No gameplay pilot has run.**

Drafted 2026-11-15 at Core `main`
`97bebbb8dc450a7564b7523851896783ac193b65`; final source comparison at
`edddf4d0ed351bf3f47224fabc0f3775b2d5a9d0`. Section 6 records the intervening
combat update without silently rewriting the archived inventory.

References: [authoring guide](npc-explicit-stat-authoring-guide.md),
[current inventory](npc-stat-inventory.md), and
[zero-defense / enemy-attack investigation](npc-zero-defense-and-attack-contract.md).

## 1. Purpose and owner direction

The owner asked to look at a pilot with recognizable defense identities:

> "the goal is to make early enemies have one singular obvious defense, for the
> most part, these are guidelines, not hard rules."

> "the weaknesses of the enemies become fewer, but are still retained in some
> way."

The intended progression can move from one strong defense with two weaknesses,
to one strong/one intermediate/one weak, then two strong/one weak. It is not a
mandatory level formula. Some creatures can remain specialized or more evenly
protected. Family identity matters: zombies should retain their structure as
their numbers improve; insect-like groups should generally favor ranged defense,
with variety in the other defenses.

The owner clarified that the current percentages are useful evidence:

> "the %s used to translate current defense into stats does try to follow these
> rules to a degree, but it was quickly put together, and we're going to try to
> be more meticulous with it"

> "but you can use it to potentially help"

This proposal therefore keeps existing strengths and many current numbers. It
uses the ratios as clues to prior intent, not a rule that future values must be
computed from old Defense. All proposed numbers below are plain armor-equivalent
stats, the same kind of defense players acquire through equipment.

Special attack decisions are explicitly deferred:

> "anything special cases you find, like dragons breath, banshees wail, and elder
> burn, we can decide how to handle after. it'll be easier to fit them in once
> we have the larger picture done"

Those findings remain recorded. They do not block this defense-picture proposal,
and this document does not choose new breath/wail/burn formulas or values.

## 2. Scope: fourteen rows, four proposed changes

The comparison contains three ordinary zombie variants, spider/scorpion family
progression, early contrasting identities, and already-authored broader or
two-defense controls. Four rows propose a numerical defense change; ten suggest
preserving current values for comparison. Preserving a number here is still a
proposal, not automatic approval of every current encounter.

All powers, HP, displayed levels, attack styles, timing, special mechanics,
placements and damage formulas remain unchanged in this draft. Current power
and HP are listed as context, not proposed edits. The planned inert NPC Holy
Power field remains a separate schema item, unimplemented and inactive.

No NPC is forced into an equal budget, and no three defenses must sum to the
old Defense. Every proposed row retains its current strongest defense value.
Changes focus on the relative weaker defenses rather than inflating the primary
stat just to make a spreadsheet pattern look sharper.

**Order everywhere below: Melee / Ranged / Magic (M/R/G).**
Roll caps are `max(0, ceil(Defense / 7))`, the unchanged ordinary mitigation
formula, not new stats, percentages, guaranteed reductions or actual damage.
Bold defense triples are the four changed proposals.

| ID | NPC | Level | Current defense | Proposed defense | Current roll caps | Proposed roll caps |
| ---: | --- | ---: | --- | --- | --- | --- |
| 52 | zombie | 19 | 20 / 10 / 10 | **20 / 7 / 7** | 3 / 2 / 2 | 3 / 1 / 1 |
| 41 | zombie | 24 | 28 / 14 / 14 | 28 / 14 / 14 | 4 / 2 / 2 | 4 / 2 / 2 |
| 68 | zombie | 32 | 31 / 15 / 15 | **31 / 14 / 14** | 5 / 3 / 3 | 5 / 2 / 2 |
| 23 | Young Giant Spider | 8 | 2 / 10 / 1 | 2 / 10 / 1 | 1 / 2 / 1 | 1 / 2 / 1 |
| 74 | Giant Spider | 31 | 7 / 31 / 3 | **8 / 31 / 3** | 1 / 5 / 1 | 2 / 5 / 1 |
| 263 | Ice spider | 64 | 16 / 66 / 33 | 16 / 66 / 33 | 3 / 10 / 5 | 3 / 10 / 5 |
| 70 | Scorpion | 21 | 6 / 24 / 2 | 6 / 24 / 2 | 1 / 4 / 1 | 1 / 4 / 1 |
| 136 | King Scorpion | 36 | 9 / 38 / 3 | **15 / 38 / 3** | 2 / 6 / 1 | 3 / 6 / 1 |
| 57 | Darkwizard | 13 | 1 / 1 / 15 | 1 / 1 / 15 | 1 / 1 / 3 | 1 / 1 / 3 |
| 62 | Goblin | 7 | 9 / 4 / 0 | 9 / 4 / 0 | 2 / 1 / 0 | 2 / 1 / 0 |
| 311 | Hobgoblin | 48 | 47 / 23 / 4 | 47 / 23 / 4 | 7 / 4 / 1 | 7 / 4 / 1 |
| 864 | Cockatrice | 35 | 35 / 40 / 30 | 35 / 40 / 30 | 5 / 6 / 5 | 5 / 6 / 5 |
| 866 | Naga | 60 | 20 / 65 / 95 | 20 / 65 / 95 | 3 / 10 / 14 | 3 / 10 / 14 |
| 882 | Stirge | 55 | 50 / 80 / 50 | 50 / 80 / 50 | 8 / 12 / 8 | 8 / 12 / 8 |

## 3. What each proposed change is testing

### Zombies: preserve one family shape

The existing ratio clue is consistently 100%/50%/50%, giving melee dominance and
equal ranged/magic sides. Retain that ordering and equality at all three levels.

- **#52, level 19: 20/10/10 → 20/7/7.** Keep primary melee 20. Lower each secondary
  cap from 2 to 1 to make the lower zombie's two weaknesses clearer. Seven is the
  largest value that gives cap 1, so this is the smallest decrease from 10 that
  tests that distinction.
- **#41, level 24: 28/14/14 unchanged.** Keep the existing modest family progression:
  caps 4/2/2. This is a preservation control, not a requirement that every higher
  zombie gain an additional strong defense.
- **#68, level 32: 31/15/15 → 31/14/14.** Preserve primary melee 31. A single point
  removed from each side changes cap 3 to 2, giving 5/2/2 instead of 5/3/3. This keeps
  secondary protection at the prior ordinary variant's tier while primary
  defense improves. It is a real rebalance, not cosmetic rounding.

Together, the proposed caps are 3/1/1 → 4/2/2 → 5/2/2. Family structure stays
M > R = G; it does not become a different defense theme at each level. The three
ordinary examples only reach level 32. They are not a proven late-game zombie
curve, and Nazastarool's quest form is not silently substituted as the next tier.

### Spiders: ranged identity, varied secondary protection

- **#23 Young Giant Spider remains 2/10/1.** The old 25%/100%/10% profile already
  makes ranged strongest. Its 1/2/1 caps are a modest beginner distinction; do not
  increase beginner armor merely to create a more dramatic-looking ratio.
- **#74 Giant Spider: 7/31/3 → 8/31/3.** Keep ranged 31 and magic 3. Eight is the
  smallest increase from 7 that gives a second melee mitigation tier. The adult
  becomes one strong ranged defense, some melee protection, and a clearer magic
  weakness: 2/5/1. This is a proposed adult-body/carapace interpretation, not an
  existing approved rule or a new formula for all spiders.
- **#263 Ice spider remains 16/66/33.** Its existing 25%/100%/50% clue already adds
  magic protection to the ranged-strong family. Caps 3/10/5 make melee the weakest
  side. This is a themed variant with a different secondary emphasis, not an
  accidental family-wide weakness swap.

### Scorpions: strengthen the higher variant's secondary defense

- **#70 Scorpion remains 6/24/2**, caps 1/4/1: ranged-strong with two weak sides.
- **#136 King Scorpion: 9/38/3 → 15/38/3**, caps 2/6/1 → 3/6/1. Preserve ranged 38 and
  magic 3. Fifteen is the smallest increase from 9 that reaches cap 3, making melee
  a meaningful intermediate defense while magic stays weak. The proposed
  tougher-carapace interpretation explains the departure from the old 25%/100%/10%
  baseline; it is not asserted as canonical lore or already-balanced behavior.

These four edited rows change six individual defense fields. No alternative
rounding convention or combat formula is introduced. The choices at 7/8 and 14/15
are deliberate test points, not an automatic authoring conversion rule.

## 4. Why the unchanged controls belong

- **Darkwizard #57:** 1/1/15 gives 1/1/3 caps, an early magic-defense identity with
  two weaker physical sides. Preserve the existing 10%/10%/100% clue.
- **Goblin #62:** 9/4/0 gives 2/1/0 caps. It keeps melee strongest and illustrates
  both a lesser ranged defense and true zero magic defense. Early enemies need
  not all have the identical two-zero pattern.
- **Hobgoblin #311:** 47/23/4 gives 7/4/1 caps, an existing primary/intermediate/weak
  pattern. This is a related-family comparison, not proof these exact IDs form
  a designed encounter sequence or that combat level defines world progression.
- **Cockatrice #864:** 35/40/30 gives 5/6/5 caps. It is intentionally a broad-defense
  control. Magic 30 is lower as a raw number than melee 35, but their ordinary caps
  are identical; do not advertise a distinct magic-versus-melee weakness here.
  The owner should review whether this relatively even exception is desirable.
- **Naga #866:** 20/65/95 gives 3/10/14 caps, already two substantial defenses and a
  clear melee weakness. Preserve its authored values rather than rewriting it
  to match a universal template.
- **Stirge #882:** 50/80/50 gives 8/12/8 caps. It is a more broadly protected,
  ranged-strong control, with equal softer melee/magic sides rather than a single
  sharply weak side. This tests the guideline's allowance for less specialized
  distributions. Its bleed/healing behavior is recorded separately and unchanged.

The list demonstrates intended shapes at varied threat levels, not a completed
worldwide balance curve. Player power/equipment, encounter mechanics, HP, rewards,
placement and progression access still determine actual difficulty.

## 5. Theme classification and family coverage

The existing ranged-heavy spider/scorpion group is a useful reference for the
owner's insect-theme direction. Spiders and scorpions are arachnids; this proposal
calls them a ranged-resistant arachnid/carapace grouping rather than silently
changing taxonomy. That naming detail is not a blocker to reviewing the theme.

Stirge is an insect-like fantasy comparator: its retained authoring reference
explicitly describes an insect-like design. That reference includes an
unapproved art-candidate revision, so it is not proof of a newly approved/current
appearance or biological classification. Its currently authored ranged-high
stats are independently verified in the NPC definition. No appearance change
is part of this pilot.

Sources: [Stirge definition](../../../server/conf/server/defs/StirgeNpcDefs.json):4;
[Stirge encounter notes](stirge-slayer-encounter.txt):7–46;
[retained Stirge authoring notes](../../../dev/myworld/assets/authoring/monsters/stirge/flight-v2/README.txt):1–9.

Before any eventual family-wide application:

- Keep IDs distinct even when names match. Zombies 180/214 currently match 68;
  zombie 359 and target-practice 516 share 41's defenses but have separate context.
  They are not included or changed by this proposal.
- Record those omitted variants for later family review so a future rollout
  does not create unexplained differences. Nazastarool 613 remains its own quest
  case, not a mechanically interchangeable high-level zombie.
- Other spiders/scorpions include quest, poison and dynamic-spawn roles. Their
  absence from this small set does not authorize copying a row to the whole group.
- Cockatrice and Naga are production NPCs despite their Preview-named source file.

## 6. Current-data provenance and frozen context

The archived inventory was generated at 92306a7b. Every inventoried NPC definition
JSON and configuration input still matches that snapshot at final review HEAD
edddf4d0. Twenty-five of the 28 tracked input hashes are identical. The three
reviewed differences are EntityHandler's unrelated item-appearance mapping,
Npc's new god-crossbow kill-credit hook, and CombatFormula's new ranged helper.
None changes these baseline NPC stat getters, authored definitions or the
`ceil(Defense / 7)` mitigation cap.

The intervening god-crossbow merge moves ordinary ranged mitigation into a
separate helper and can lift an eligible player's offense roll for Saradomin
Revelation. The helper still uses zero defense without a defense RNG draw,
otherwise the same independent `1..cap` defense roll. This is another reason not
to equate a raw-defense proposal with a universal damage prediction. The named
holy-bolt mechanic does not itself create an NPC Holy Power stat or Holy damage
channel.

Sources at edddf4d0: [CombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java):328–352,557–559,628–649;
[GodCrossbowEffects.java](../../../server/src/com/openrsc/server/content/GodCrossbowEffects.java):82–91;
[Npc.java](../../../server/src/com/openrsc/server/model/entity/npc/Npc.java):874–878.

The strict inventory generator was not bypassed or modified to force a refresh.
Current rows were cross-checked against unchanged definition/configuration inputs,
the getter/damage-bound diff, and their recorded provenance. The archived outputs
remain evidence of their own captured revision, not a newly executed census.

The ratios below are **historical/current derivation clues only**. Directly
authored rows have no defense conversion to preserve. No future percentage
formula is proposed.

| ID | HP | Current power M/R/G | Legacy ratio clue M/R/G | Definition / override row lines |
| ---: | ---: | --- | --- | --- |
| 52 | 22 | 19 / 0 / 0 | 100% / 50% / 50% | NpcDefs.json:2031 / NpcDefsMyWorld.json:189 |
| 41 | 24 | 20 / 0 / 0 | 100% / 50% / 50% | NpcDefs.json:1602 / NpcDefsMyWorld.json:54 |
| 68 | 30 | 35 / 0 / 0 | 100% / 50% / 50% | NpcDefs.json:2655 / NpcDefsMyWorld.json:237 |
| 23 | 5 | 10 / 0 / 0 | 25% / 100% / 10% | NpcDefs.json:900 / NpcDefsMyWorld.json:1176 |
| 74 | 32 | 34 / 0 / 0 | 25% / 100% / 10% | NpcDefs.json:2889 / NpcDefsMyWorld.json:1238 |
| 263 | 65 | 65 / 0 / 0 | 25% / 100% / 50% | NpcDefs.json:10260 / NpcDefsMyWorld.json:1286 |
| 70 | 17 | 22 / 0 / 0 | 25% / 100% / 10% | NpcDefs.json:2733 / NpcDefsMyWorld.json:1232 |
| 136 | 30 | 39 / 0 / 0 | 25% / 100% / 10% | NpcDefs.json:5307 / NpcDefsMyWorld.json:1256 |
| 57 | 12 | 12 / 0 / 15 | 10% / 10% / 100% | NpcDefs.json:2226 / NpcDefsMyWorld.json:1445 |
| 62 | 5 | 9 / 0 / 0 | 100% / 50% / 10% | NpcDefs.json:2421 / NpcDefsMyWorld.json:201 |
| 311 | 49 | 48 / 0 / 0 | 100% / 50% / 10% | NpcDefs.json:12132 / NpcDefsMyWorld.json:529 |
| 864 | 45 | 40 / 0 / 0 | direct authored values | SlayerMovementPreviewNpcDefs.json:49 / none |
| 866 | 75 | 65 / 55 / 0 | direct authored values | SlayerMovementPreviewNpcDefs.json:139 / none |
| 882 | 60 | 55 / 0 / 0 | direct authored values | StirgeNpcDefs.json:4 / none |

Definition sources: [NpcDefs.json](../../../server/conf/server/defs/NpcDefs.json),
[NpcDefsMyWorld.json](../../../server/conf/server/defs/NpcDefsMyWorld.json),
[SlayerMovementPreviewNpcDefs.json](../../../server/conf/server/defs/SlayerMovementPreviewNpcDefs.json),
[StirgeNpcDefs.json](../../../server/conf/server/defs/StirgeNpcDefs.json).
The inventory contains full field-level provenance and override records.

## 7. Numerical limits and future checks

The draft's four changes are explicit proposed rebalances. Ten controls preserve
the current raw values, but even equal baseline caps do not establish universal
parity under temporary reductions, scripted effects or future item interactions.

- Defense 0 skips the ordinary mitigation roll. Values 1–7 all have cap 1; 8–14 cap 2;
  15–21 cap 3. A one-point edit at a boundary can matter more than a larger edit
  within a band.
- Strong/intermediate/weak labels are relative to that NPC. They do not mean
  immunity or a fixed damage-reduction percentage. In particular, a weak defense
  on a high-tier enemy can exceed an early enemy's strongest defense.
- At the ordinary defense-roll stage, cap 2 averages 1.5 before luck, while cap 1
  is exactly 1. The zombie reductions each lower that roll's mean by 0.5; the
  selected spider/scorpion tier increases raise it by 0.5. These are not predicted
  changes to final average HP damage because the damage floor and later effects
  still matter.
- Zero-valued proposal/control fields must not be bulk-written through the
  current NPC override sentinel path. Authoritative zero versus missing is a
  separate implementation contract, still required before any actual migration.
- Current NPC power values remain untouched, including any known power-source
  concerns elsewhere. Defenses alone are being proposed here.

Completed: read-only source/input validation, inventory-row checks, independently
recomputed old/new mitigation caps, and documentation review. No gameplay test,
player-facing contrast assessment, roster rebalance or runtime implementation
has been performed.

When an implementation/test pilot is separately approved, characterize both
unchanged controls and proposed deltas, preserve powers/HP/styles/timing, test
zero/missing behavior, and record changes with and without relevant temporary
effects. Retain and annotate legacy data/code while removing modern authority;
do not delete it as cleanup. Prove old Defense edits cannot alter a migrated
NPC's authored modern defenses.

## 8. Deferred work and requested review

Keep dragon breath, Banshee wail, Elder burn, Balrog's legacy Attack-driven Magic
Power, and other attack-source exceptions in the existing investigation register.
Their formulas, power mapping and final values are deferred until the larger
defense picture is clearer. They are not prerequisites to discussing this pilot.

The current review should decide:

1. Are the family shapes and four small, mechanically meaningful changes useful
   starting points, or should any retain their current values instead?
2. Are broad controls such as Cockatrice/Stirge acceptable deliberate exceptions,
   and is the small early-game contrast sufficient without inflating defenses?
3. Does the selected set give enough range to refine authoring guidance before
   expanding families or planning implementation?

Reviewing this document does not approve every proposed number. Record each
accepted/revised/rejected row explicitly. No implementation follows automatically,
and resolving deferred special attacks remains a later decision.
