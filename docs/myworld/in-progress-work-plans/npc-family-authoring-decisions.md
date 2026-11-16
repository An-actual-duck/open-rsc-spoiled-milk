# NPC family authoring decisions

Owner-approved direction, 2026-11-16. This supersedes conflicting future-design
recommendations in the earlier investigation and defense pilot. Those documents
remain historical/source evidence. No runtime migration or individual stat values
are implemented or approved by this record.

## Explicit stats and rolls

Combat already uses matching Power and Defense. Replace NPC legacy A/S/D
conversion authority with explicit Melee/Ranged/Magic Power and Defense values.
Keep existing scaling from those values into roll bounds. Defense rolls will
include zero through their calculated maximum, rather than 1 through maximum.
A zero cap still produces zero without requiring RNG. This is a deliberate
mitigation change, separate from the data migration; characterize both.

An authored zero is authoritative. Missing canonical values are incomplete,
not requests for legacy fallback. Preserve and annotate legacy compatibility
material, but prevent it supplying canonical modern NPC values silently.
The NPC Holy Power placeholder remains planned and inert.

## Family themes

Rows describe preferred defensive ordering as progression develops, not a
requirement that every low-level member has positive secondary defenses.
Family membership is for stat authoring; it does not change undead tags,
attack selection, elemental resistance or quest mechanics automatically.

| Family | Members/examples | Preferred defense ordering |
| --- | --- | --- |
| Insects and arachnids | Spiders, scorpions, Stirges | Ranged, Melee, Magic |
| Ordinary beasts | Rats, wolves, dogs, bears, cows, unicorns, Ugthanki, Bloodvelds, Terror dogs | Melee, Ranged, Magic |
| Flying creatures | Giant bats, Death Wings, ordinary birds | Ranged, Magic, Melee |
| Goblinoids | Goblins and hobgoblins | Melee, Ranged, Magic |
| Giants and ogres | Ordinary/moss/ice/fire giants, ogres, Jogres | Melee, Magic, Ranged |
| Unarmoured humanoids | Men, thieves, pirates, bandits, barbarians, tribesmen | Melee primary, limited secondary protection |
| Armoured humanoids | Guards, knights, soldiers, mercenaries, paladins | Melee, Ranged, Magic |
| Casters | Wizards, witches, druids, necromancers, battle mages | Magic primary, weak physical defenses |
| Skeletons | Ordinary skeletons and skeletal variants | Ranged, Magic, Melee |
| Corporeal undead | Zombies, UndeadOnes; excludes Bloodvelds | Preserve zombie theme: Melee strongest, equal softer Ranged/Magic |
| Spirits | Ghosts, Banshees, shadow-like creatures | Physical primary, Magic secondary; other physical defense weakest |
| Demons | Imps, lesser/greater/black demons, Abyssal demons, Balrog | Magic, Melee, Ranged |
| Reptiles and amphibians | Naga, giant frogs | Magic, Ranged, Melee |
| Living plants | Tree spirits, Greatwood | Ranged, Magic, Melee |
| Dragons | Ordinary and gem dragons | Magic highest, generally high in all three |
| Constructs and animated objects | Animated axes, Rock of ages | Material-specific physical protection, weaker Magic |
| Magical beasts | Dark beasts | Magic, Melee, Ranged |
| Magical birds | Cockatrices | Magic, Ranged, Melee |

Spirits already have a family: the earlier suggestion that they needed individual
profiles was a presentation mistake. Magic is now their secondary defense.
The earlier physical-primary description did not pin down Melee versus Ranged;
that detail remains to resolve when producing their first concrete stat rows.
Tree spirits belong to the living-plant example, rather than automatically
joining ghosts because of their name. Mixed identities require deliberate
assignment, not an uncontrolled name-substring classifier.

## Progression and original-defense anchor

Use seven flexible encounter bands, without automatic combat-level cutoffs:

| Band | Typical defensive shape |
| --- | --- |
| Low | One strong defense; two zero or very small defenses |
| Low mid | One strong; one small secondary; one zero/very weak |
| Mid | One strong; one moderate; one weak |
| Mid high | One strong; one substantial; one weak |
| High | Two strong; one clear weakness |
| Very high | Two strong; smaller remaining weakness |
| Boss | Broad protection, potentially retaining a preferred attack style |

At least one defense must retain the full original Defense value during
initial authoring. The others develop with family identity and progression.
This is a one-time reference for choosing plain numbers, not a runtime percentage
conversion or a shared budget. Preserve stronger established exceptions unless
a deliberate rebalance is reviewed. Already-explicit encounters need individual
context; do not erase intentional values merely to fit a template.

Young Giant Spider is the concrete shape example: ranged defense only, with
zero Melee/Magic. Giant Spider gains ranged and some Melee, still zero Magic.
Magical variants can deliberately gain Magic protection. No final numeric values
for those examples are chosen here. Earlier pilot numbers remain unapproved
and must be reconciled with these newer zero-defense examples.

## Next authoring deliverable

Produce a proposed roster grouped by family and encounter band, showing current
and proposed explicit values, original-defense anchor, exceptions and reasons.
Review batches rather than requiring meticulous authoring of every NPC by the
owner. Keep powers, HP, displayed levels and attack behavior separate from this
defense-guideline exercise. Power values, special attacks and the canonical
loader schema still need their own concrete decisions before implementation.
