# NPC offense and attack-style audit

2026-11-16. **Source audit and recommendations only. No profiles, powers,
pathfinding or gameplay behavior changed.** All 298 attackable definitions are
covered. Nonattackable NPCs are not being made attackable by this proposal.

- [Complete roster, grouped by current profile](roster.md).
- [CSV](audit.csv) and [structured audit](audit.json): every ID, current profile,
  power values, legacy inputs, recommendation, priority and special notes.
- [Agreed family/defense direction](../../in-progress-work-plans/npc-family-authoring-decisions.md).

The owner accepted the defense roster as the working assignments, then requested
this offense/behavior audit before assigning powers or implementing migration.
No offensive power values or style changes in this audit are owner-approved.

## Agreed offense and pursuit rules

- Prefer old loaded Strength as the primary reference when authoring powers;
  do not perpetuate max(Attack,Strength) automatically. Review intentional direct
  powers and outliers rather than flattening them blindly.
- Primary/secondary/tertiary authoring references progress through the six defense
  bands: 100/40/10, 100/50/20, 100/60/30, 100/70/40, 100/80/50, 100/90/60.
  Use only enabled styles; unused styles remain explicitly zero and disabled.
  Boss powers are individually assigned, not automatically full in every style.
  Ratios are one-time authoring guidance; runtime reads explicit values.
- Dedicated casters/shooters attack exclusively in that style, including adjacent
  targets. They hold position whenever a valid in-range projectile path exists,
  including while their attack is cooling down.
- With no valid shot, use existing pursuit pathing and stop when a shot becomes
  valid. No tactical repositioning, retreat/kiting or new navigation system.
- Mixed users retain weighted RNG favouring their primary style. Power ratios
  and selection weights are different decisions; no numeric weight is approved.
- Preserve current general damage feel; correct outliers deliberately. Magic's
  existing formula has a spell component in addition to Power/7, whereas melee
  and ranged use their own maximum calculation. Equal powers do not guarantee
  equal damage. Check actual resulting damage before final power assignments.

## Existing capabilities: what the source actually says

| General profile | Attackable definitions |
| --- | ---: |
| MELEE | 195 |
| PURE_MAGIC | 13 |
| MELEE_MAGIC | 46 |
| MELEE_FREQUENT_MAGIC | 3 |
| MELEE_RARE_MAGIC | 7 |
| PURE_RANGED | 20 |
| MELEE_RANGED | 14 |

**69 definitions have general magic support**, and **34 have general ranged
support**. This is configured capability, not a measured delivered-attack count.
Dark beast adds a separate scripted Magic attack despite general MELEE profile.
Elder Green Dragon already uses all three styles: general magic/melee, scripted
melee sweep and Ranged fireshot, plus distinct burn. Thus a three-style enemy
already exists. Do not remove Elder's Ranged Power as apparently unused.

The nominal magic-only group is:
- Darkwizards57/60, Witch79, Wizard81, Monk93, Druid200, Chaos Druid270,
  Necromancer358, Skeleton mage498, Kolodion human713, Battle mages789–791.

Mixed magic support includes:
- Lesser/Greater/Black demons22/181/184/290/568, Delrith35, Balrog809,
  Chronozon315; ordinary/gem dragons196/201/202/203/291/477/844/862/877–881.
- Ghosts53/80/178/664, Nazastarool ghost615, Otherworldly being298,
  Tree spirit216, Banshee865.
- Moss giants104/594, Ice Giant135, Fire Giant344, Ice Warrior158,
  Fire warriors361/843, Ice Queen254.
- Zamorak monks139/140/293, Chaos Druid warrior555, Paladins323/632/633,
  Lucien364, Salarin567, Black knights66/108/189, White Knight102,
  Grey Knight836, Black Knight titan401, and Kolodion forms757–760.

The full roster lists each separately, including baseline powers. Presence of a
power, spell-looking visual, name or profile alone does not prove every damage
path uses that style. Dragon breath, Banshee wail, owned summons, reflection,
status pulses and scripted quest damage require distinct records.

## Confirmed behavior gaps from source tracing

1. `PURE_MAGIC`/`PURE_RANGED` mean projectile preference; they do not enforce a
   universal prohibition on melee. `PvmMeleeEvent.isNpcMeleeDisabled()` explicitly
   excludes only Giant Frog863 and Kolodion human713. There is a reachable generic
   melee path for the other nominal specialists. This is source evidence, not
   a live reproduction of each NPC: quest/plugin gates may affect individual cases.
2. A clear shot on cooldown normally returns false from `tryProjectileAttack()`.
   The aggression caller then pursues and can engage melee. Only Banshee/Frog
   have the special hold-on-cooldown behavior there; Naga and ordinary pure
   specialists do not receive that general guarantee.
3. Generic mixed profiles prefer projectiles at every eligible distance beyond
   adjacency. Their 65%,85%,10% RNG applies to adjacent preference checks, not
   to every committed attack. Cooldowns, polling and path eligibility further
   affect results. Do not advertise those as true overall style percentages.
4. Banshee and Naga explicitly prefer projectiles only when distance>1. Changing
   their primary style therefore requires revisiting that exception, not merely
   relabeling a profile.
5. Existing selection is mostly name/ID inference. New authoring should explicitly
   state enabled styles, primary style and per-attack weights by ID/encounter,
   with deliberate compatibility adapters instead of another name-rule layer.

Sources (repository relative):
- `server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java`:39–82,
  89–94, 95–118, 240–245, 379–473.
- `server/src/com/openrsc/server/model/entity/npc/NpcCombatProfile.java`:22–68.
- `server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java`:418–440,493–495,
  537–576, 595–658.
- `server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java`:140–167,
  217–247, 276–284.
- `server/src/com/openrsc/server/content/monsterslayer/DarkBeastCombat.java`:65–85,
  185–219; `server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java`:60–125.

## Recommended dedicated magic users

**First pass: enforce the existing thirteen nominal magic specialists**, rather
than inventing a second set of spells. Keep existing visuals/elements initially.
Then convert these existing mixed casters to magic-only:

| Candidate | IDs | Why / caveat |
| --- | --- | --- |
| Monks of Zamorak | 139,140,293 | Caster identity; already cast |
| Ice Queen | 254 | Existing Ice magic, clear magical identity |
| Salarin the Twisted | 567 | Existing caster; preserve special player-spell response and vulnerabilities |
| Lucien | 364 | Existing magic, caster identity; retain quest gating |
| Ghosts and Otherworldly being | 53,80,178,664,615,298 | Existing supernatural casts; proposed magic-only, quest variants remain separate |
| Banshee | 865 | Wail fits magic-only; preserve earplugs, wail rules, windups and cadence first |

New ordinary magic candidates currently in general MELEE:
- **Melzar the Mad182, Iban Disciple658, Gaia798**: strong caster identity.
- **Ungadulu766/767 and Viyeldi772**: shaman/sorcerer identity, but quest/possessed
  forms and triggers require focused review before a profile change.

Not every magical creature should be exclusive magic. Recommend Magic-primary /
Melee-secondary for Dark beast and demonic hybrids; retain explicit scripted
mechanics. Giants, animated trees, elemental warriors and paladins can retain
melee-led mixed behavior. Defense themes do not dictate offensive primary style.
Ordinary Monk93 is already nominal magic-only; confirm that holy-caster treatment
is desired rather than assuming all religious NPCs must cast.

Optional later extensions: magical spider web/frost/shadow attacks, Cockatrice
magical gaze, and Greatwood wood/root attacks. These are **new attacks**, not
existing ordinary casting. Preserve protection items and encounter identity.

## Recommended ranged-primary users with melee secondary

The best starting candidates already have working projectile routing:

| Candidate | IDs | Existing projectile |
| --- | --- | --- |
| Yanille Watchman | 574 | Bolt |
| Tribesman | 421 | Throwing dart |
| Pirates and bandits | 137,264,232,234 | Throwing knife |
| Thieves, rogue, head thief | 64,351,342,352 | Throwing knife; currently nominal pure-ranged |
| Gnome guards / Goblin guard | 551,562,582,651 | Generic arrow |
| Bedabin / Shantay guards | 703,717 | Generic arrow |
| Mercenaries and troops | 668,669,670,690,692,407,409 | Generic arrow; inspect visible equipment |
| Naga | 866 | Thrown scimitar; keep its melee option and special cadence |

Other guard IDs65/100/127/321/420/684/697/710/716 are already nominal ranged,
but should receive equipment/role review instead of converting every guard into
an archer by name. In particular Ogre guards already use generic arrows despite
their appearance; actual weapon readability matters.

New candidates, in recommended order:
1. Highwayman89 and Mugger21 with thrown knives, following existing rogue support.
2. Selected goblin/hobgoblin variants as stone/spear throwers; prefer distinguishable
   roles rather than converting every member of the species.
3. Selected dwarf crossbow/throwing-weapon roles, where held equipment supports it.
4. Selected ogre/giant rock throwers. This would be newly authored ranged damage;
   an earthy-looking rock effect does not automatically make it physical Ranged.

There is no need to force pure-ranged humanoids. **Giant Frog863 already is a
ranged-only acid-spitter with explicit melee suppression**; recommend preserving
that specialized exception unless the owner wants to change it.

## Mixed selection and rollout recommendation

Choose primary style by encounter identity and intended authored power. A 75/25
primary/secondary split is a reasonable initial proposal for ordinary hybrids,
not an approved weight. Rare holy casts on melee knights could remain90/10;
boss scripts need their own distributions. Do not turn power ratios into attack
probabilities automatically. Choose among eligible styles at an attack decision,
not repeatedly consuming selection RNG while cooling down.

Prevent specialist melee in every entry path, including player-initiated close
combat. An NPC's inability to melee must not prevent the player from hitting it.
Keep one attack cadence/commitment so behavior polling plus melee events cannot
deliver duplicate attacks. Hold a valid shot on cooldown; otherwise pursue using
existing pathing, checking actual range, line of sight and spatial domain.
Preserve attack suppression, slow/stagger, leashes, prayer and protective-item
rules. No new tactical navigation is required.

Do not enable new magic profiles while leaving legacy max(Attack,Strength) power
in charge: pair approved style changes with explicit power assignment. Balrog's
Magic999 is a known example. Even existing specialists can be nerfed unintentionally
if Attack-derived power is replaced with Strength without checking actual maxima.

Suggested next decisions: approve magic-only candidate groups and the first
ranged-primary set; settle hybrid primaries/weights; then assign explicit powers
and preserve/adjust damage intentionally. Save special encounters for focused
rows rather than changing their phase logic through a generic switch.

## Evidence and limits

The complete roster is derived from the preserved 883-definition inventory
(298 attackable) at92306a7b. Current NPC definition and attack-profile source
hashes match that snapshot. The behavior, special-owner and melee gates were
reviewed directly at the audit's recorded current source commit. The strict
inventory generator was not bypassed or represented as newly passing.

This is source-level capability and recommendation coverage, not runtime proof
for every quest, a complete inventory of every historical damage callback, a
live population count or an exhaustive summon lifecycle audit. Owned summons
have separate attack dispatch and must not acquire generic changes accidentally.
No server/client was started and no live database or accounts were accessed.
