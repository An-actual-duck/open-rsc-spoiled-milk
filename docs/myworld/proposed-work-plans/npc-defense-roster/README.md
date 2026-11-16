# NPC defense assignments

Assigned draft, 2026-11-16. These are concrete authoring recommendations for all
**298 attackable definitions**, including the three confirmed bosses. They are
not installed gameplay values. The remaining 585 nonattackable definitions are
outside this balance roster; they have not been silently assigned zero stats.
No power, HP, level, attack style, encounter immunity or combat formula is changed.

- [Full roster grouped by family](roster.md): current and assigned values, IDs,
  bands, anchors and per-row review notes.
- [Spreadsheet-compatible CSV](roster.csv).
- [Explicit structured assignments](roster.json): includes proposed boss flags.
- [Owner decisions](../../in-progress-work-plans/npc-family-authoring-decisions.md).

This supersedes the older fourteen-row pilot as the recommended starting roster.
The family rules are approved direction; these individual assignments remain a
review draft, not evidence that the owner has approved each numeric change.

## Bands and assignment method

Slayer has ordered contact task rosters, not a general min/max-level classifier.
The six ordinary band boundaries below extend the maxima of its mandatory base
rosters, excluding confirmed bosses. Gaps are assigned to the next band. This
extension is a recommendation, not a claim these thresholds are enforced by
Slayer. Supplemental Slayer task membership may cross these numeric ranges.
Source: `server/conf/server/defs/extras/MonsterSlayer.json`.

| Band | Combat levels | Primary / secondary / weakest reference |
| --- | --- | --- |
| Low | 0–31 | Full / 0–25% / zero |
| Mid Low | 32–53 | Full / 40% / zero |
| Mid | 54–64 | Full / 60% / 20% |
| Mid High | 65–87 | Full / 80% / 35% |
| High | 88–140 | Full / 90% / 50% |
| Very High | 141+ | Full / 95% / 75% |
| Boss | Explicit membership, any level | Full / full / full |

For Low, levels below 10 have zero secondary defense; levels 10–19 use 10%,
20–29 use 20%, and 30–31 use 25%. These are one-time authoring scaffolds for a
consistent first pass. **Only explicit integer values are assigned in the
roster; none of these percentages are intended as runtime conversions.** Round
secondary values down. Very small anchors may therefore produce additional zeros.
The six ordinary bands remain flexible; the named overrides below are deliberate.

Families determine assignment order. For example, ordinary insects/arachnids put
primary in Ranged, secondary in Melee, weakest in Magic. All membership is explicit
per ID; there is no runtime name-substring classifier. Repeated names keep their
separate IDs, levels, baselines and quest/minigame notes.

## Original-defense preservation

For legacy-derived NPCs, the anchor is the original loaded Defense, after existing
legacy-field overrides. At least one assigned style retains that full value.
A sum across styles never satisfies this condition on its own.

For already-explicit NPCs, old Defense is often a placeholder (for example 1).
Use the larger of that value and the highest existing direct defense as the
proposed anchor. This retains their established strongest armor value while
rearranging their family strengths. Each such row is flagged; these anchor
choices require review rather than being a new hidden runtime inference rule.

Gem dragons have historical multipliers above 100%. Their rows explicitly flag
that this draft returns to the original Defense anchor and redistributes the
strengths toward Magic. That can reduce a previously elevated defense. Their
original full protection still survives in Magic, but these are intentional
balance deltas requiring particular attention, not conversion-parity claims.

All positive-anchor ordinary rows retain at least one weakness. None receives
full anchor protection in all three styles. The weakest style is never above
90% of its anchor; ordinary Very High dragons reach that ceiling. Bosses use
exactly their original Defense in every style: Balrog80, KBD240, Elder265.

## Deliberate family exceptions and review points

- Zombies/corporeal undead keep Melee primary and equal softer Ranged/Magic.
  Their secondary pair progresses through 15/30/40/55/70/85% across the bands
  (zero below level10). This preserves their agreed family shape.
- Dragons are generally protected, with Magic primary, Melee secondary, Ranged
  weakest. Their secondary/weakest pairs are 40/20, 65/40, 75/50, 80/60,
  90/75 and 95/90%. Confirmed dragon bosses override this ordering with equality.
- Ice, Shadow and Blessed spiders, plus Kolodion's spider form, gain Magic
  secondary instead of Melee. Skeleton mage gains Magic primary/Ranged secondary.
- Spirits use **provisional Melee primary**, Magic secondary, Ranged weakest.
  The owner has settled Magic secondary but not which physical style leads.
- Bloodvelds and Terror dogs use ordinary beast membership. Dark beast and
  Cockatrice retain their distinct owner-specified magical families.
- Hellhound is proposed as a demonic beast, unlike Terror dog. Earth/ice/fire
  warriors use a proposed elemental-armoured subtype (Melee/Magic/Ranged).
  Rock of ages is a stone-construct exception (Ranged/Melee/Magic).
- Kolodion's bodies are explicitly separated: human caster, ogre, magical spider,
  spirit-like form and demon. Their current sprite matches support those form
  assignments; attack scripts and quest phase mechanics remain unchanged.
- Charlie#550 has original Defense0 and remains 0/0/0; its level0/HP3 role needs
  review. Gnome Ball actors and the PK bot carry specific usage warnings.
- Only Balrog, King Black Dragon and Elder Green Dragon are flagged as bosses.
  Other named/quest encounters and level300 Greatwood are not automatically
  promoted. Review that membership separately if additional bosses are intended.

The current baseline comes from the preserved inventory at 92306a7b. All NPC
source definition hashes were checked unchanged against current source before
assignment. Source/config changes since that inventory are documented in the
Core review; no stale-generator guard was bypassed to produce a fresh census.
Current and assigned defenses are separate columns. Stat reductions and increases
are real balance proposals; this is not a promise of unchanged encounter DPS.

## Before runtime installation

Review family/anchor exceptions and desired defense shapes, choose the canonical
stat representation, and fix authoritative zero versus missing. Then implement
the separate zero-inclusive defense roll change with characterization and combat
regressions. Current production getters cannot safely consume these zero-valued
assignments through the old override path. The JSON here is authoring data only;
it is deliberately outside the game's loaded definitions.

Validation: `python3 scripts/validate-npc-defense-roster.py` checks exact roster
coverage, source baselines, preservation constraints, family examples and agreement
between the JSON, CSV and grouped Markdown. This is authoring validation, not a
gameplay regression suite.
