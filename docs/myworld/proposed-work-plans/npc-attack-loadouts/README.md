# Explicit NPC attack loadouts and tags

Assigned 2026-11-16 in family/profile batches, with per-ID exceptions. **All 298
attackable definitions have an explicit primary style and exact enabled-style
list.** This is maintained authoring data, not installed runtime behavior.
Powers and numeric selection weights are a separate next step. No combat code,
pathing, item protection, weakness/resistance or live server was changed.

- [Readable per-NPC loadouts](loadouts.md).
- [CSV](loadouts.csv), [structured assignments](loadouts.json).
- [Tag vocabulary and semantics](tag-vocabulary.json).
- [Owner decisions and safeguards](../../in-progress-work-plans/npc-offense-owner-decisions.md).

## Assignment overview

| Primary label | Definitions, including preserved entries |
| --- | ---: |
| Melee | 213 |
| Ranged | 36 |
| Magic | 49 |

Modes:190 ordinary single-style assignments,82 ordinary mixed assignments,
4 preserved special-owner encounters and22 protected minigame/role entries.
Primary labels for protected entries are observational; they do not authorize
changes to their current combat behavior, including incidental melee.

There is no need to review every NPC individually: the ordinary batches follow
these defaults, and the full table keeps duplicate names separate by ID:

| Batch | Primary / other styles |
| --- | --- |
| Ordinary beasts, undead bodies, skeletons, insects, humanoids | Melee only, unless already a shooter/caster or listed exception |
| Existing specialist casters and approved ordinary ghosts | Magic only, except quest/minigame safeguards |
| Existing ranged users, plus Highwayman89/Mugger21 | Ranged primary, Melee secondary |
| Demonic casters | Magic primary, Melee secondary; Imp114 remains a melee nuisance |
| Physical dragons | Melee primary, Magic secondary; preserve breath separately |
| Armoured holy warriors / elemental giants and warriors | Melee primary, Magic secondary |
| Ghostly named warriors663/761/762/787 | Melee primary, neutral Magic secondary; new Magic where absent is a proposal |
| Eldest/special encounters | Explicit scripted-owner records, not generic dispatch |

Not every magical defense family becomes a caster. Ordinary spiders, bats,
Cockatrice864, Greatwood802 and beast-like Slayer enemies retain current ordinary
styles unless explicitly listed. Their optional new attacks from the audit are
not added just to fill a family pattern. Defense ordering never chooses offense.

## Owner-specific exceptions

- Zamorak monks139/140/293 and Salarin567 are Magic-only.
- Ice Queen254 is Magic primary / Melee secondary, with **both powers at100%
  Strength reference** when powers are authored. It is not a new boss flag.
- Lucien364, Gaia798, Melzar182, Iban Disciple658, Ungadulu766/767 and Viyeldi772
  are Magic primary / Melee secondary. This preserves the quest melee safeguard.
- Ordinary ghosts53/80/664 and Otherworldly being298 are Magic-only. Quest-related
  ghost178 and Nazastarool615 retain Melee secondary.
- Dark beast869: Magic primary / Melee secondary, with its existing charge and
  lightning owner preserved. No second generic Magic attack is added in parallel.
- Banshee865 is **unchanged**. Melee-primary is only a preservation label for its
  current adjacent-melee/distant-wail behavior; keep current powers and cadence
  instead of applying ordinary secondary-power scaling to this exception.
- Giant Frog863 retains its existing ranged-only spit and melee-disable rule.
- Elder Green Dragon844: Magic primary label (its strongest current explicit
  power), Melee secondary, Ranged tertiary. Its actual attack schedule remains
  scripted; sweep, fireshot and burn are not independent generic RNG casts.
- Balrog809 is Magic primary / Melee secondary. King Black Dragon477 is Melee
  primary / Magic secondary plus existing breath. Boss power values/weights are
  individually reviewed next; these labels do not overwrite them.
- Chaos Druid warrior555 retains Melee primary / Magic secondary. Regular Druids
  and Chaos Druids remain dedicated casters.

The new ranged set deliberately starts with existing projectiles. Knives serve
thieves/rogues, pirates/bandits, Highwayman and Mugger; Watchman uses bolts,
Tribesman darts, Naga thrown scimitars and guard/troop roles their existing arrow
routing. Guard/mercenary held-art readability remains an implementation check.
Potential new goblin/dwarf/ogre roles remain deferred; no blanket racial conversion.

## Quest and minigame isolation

The roster indexes direct `NpcId` references in authentic quest plugins, with
source paths stored per NPC.104 attackable IDs are referenced. Preserve Melee
for those IDs when adding Magic; retain named owner exceptions and all quest
phases/protection/invulnerability triggers. References are a conservative signal,
not proof that all uses of a shared ID belong to that quest. Dynamic numeric
references, custom quest code and spawned instance attributes still need review.

Protected entries include all attackable direct `NpcId` references in authentic
minigame plugins (including Mage Arena/Kolodion and Battle mages), Gnome Ballers,
and a conservative PK-bot role hold:22 entries. Rat29 is shared with a minigame
reference and remains unchanged. No participant behavior or generic exclusivity
change may pass through this boundary. This includes keeping Kolodion's human
no-melee exception intact. Legacy custom task-list references alone are not
proof of ownership of every ordinary task monster's attack AI.

`genericDispatchEligible:false` and `implementationPolicy` explicitly identify
protected/special-owner entries. Do not point a future generic loader at their
style arrays and assume that preserves their existing event logic. Owned summons
have separate instance dispatch and are not migrated by this definition roster.

## Tags: keep each meaning separate

1. **Creature tags** describe identity or role: family, undead, demon, beast,
   spectral, dragon, boss, quest-reference and minigame protection.
2. **Creature affinities** describe proposed elemental identity. They are not
   automatically inherited from every spell the creature can cast.
3. **Attack tags** describe delivery, style, element options, god alignment,
   weapon/substance, and existing status/effect capabilities.

None of these tags grants a new resistance, weakness or status proc. Tags can
later serve explicit rules, but their presence never activates those rules by
itself. They do not change target eligibility or damage settlement.

Examples:
- Zamorak monk: Magic, neutral element, `holy`, `god.zamorak`.
- Druid: Magic, neutral element, `holy`, `god.guthix`.
- Darkwizard: Magic, Fire **or** Air; no god alignment inferred from “dark.”
- Ice Queen: ordinary physical Melee and Ice Magic; creature Ice affinity.
- Dark beast lightning: Magic, Thunder-themed metadata, area charge attack.
- Frog spit: Ranged, physical delivery plus acid/poison/attack-lock capability.
- Stirge: Melee contact with bleed/lifesteal capability; no new application rule.

An elemental pool lists alternatives selected per cast, not a combined hit that
receives every element's modifier. `neutral` explicitly means non-elemental;
an empty element list on ordinary physical attacks is intentional.

`holy` is a theme on Magic, **not a new Holy damage channel or Holy Defense**.
The inert NPC Holy Power placeholder remains a separate stat-schema decision.
Saradomin/Zamorak/Guthix aligned Magic has explicit god metadata and the approved
20% same-alignment reduction intent. Matching uses the selected prayer-book line
when implemented; positive Devotion or wearing a cape is not substituted.

God mappings retain existing monk/druid/knight follower meanings. Paladin Magic
is explicitly assigned Saradomin as an authoring choice; current name-based
follower code did not classify Paladins. Lucien, Salarin, neutral ghosts, generic
demons and holy-looking Banshee wails receive no inferred god. Iban disciples
have a separate Iban-devotion creature tag, not an invented selectable prayer
book or automatic Zamorak reduction. The matching-god trait stays excluded from
protected minigames and player/PvP spells in this work.

The `undead` tag follows current definition overrides and the legacy classification
list, rather than guessing from names/families. Explicit false overrides win:
Farmer319 is not tagged undead despite an old list entry. A stat family alone
never changes Hallowed Strikes eligibility.

## Special attacks and existing mechanical classification

Special entries remain separate from selectable ordinary styles. Dragon breath
is tagged fire/breath but keeps its existing special mitigation and deferred
Power mapping. It is not automatically converted to an ordinary Magic roll.
Elder's fireball-looking fireshot is currently **Ranged** damage; its `theme.fire`
tag does not silently apply a Magic/Fire resistance path. Burn is separate.
Dark beast lightning currently settles direct Magic without the ordinary elemental
projectile descriptor; Thunder metadata records intent without changing mitigation.
Banshee wail's holy-looking effect is not god-aligned Holy damage.

Tagging a status capability does not mean every hit applies it. Actual gates,
chance, protection items, target state, attack commitment and cadence remain
owned by the existing mechanics until explicitly migrated and tested.

## Next step

Review these batch assignments and exceptions, then author explicit offensive
powers from Strength using the approved band references, comparing real damage
ceilings and correcting outliers. Choose numeric style weights independently.
Only then implement canonical data, authoritative zero/disabled-style handling,
specialist pursuit/exclusivity, boss metadata and the god-aligned reduction.
Keep the already-agreed zero-inclusive defense roll change distinct in validation.

The current source/input hashes are embedded in the JSON for review. This is
not a runtime combat test or an exhaustive quest/minigame ownership proof; no
server, account, database or deployed asset was touched.

Validation: `python3 scripts/validate-npc-attack-loadouts.py` checks the full
roster, source hashes, ordered styles, owner exceptions, protected profiles,
tag semantics and agreement between the human/machine-readable artifacts.
