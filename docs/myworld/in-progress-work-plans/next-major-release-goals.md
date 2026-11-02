# Immediate goals for the next major release

Owner direction recorded October 24, 2026. These are the immediate content
goals before the next release, as narrowed by the owner checkpoint below. Beta designation remains a later decision.
This records intent and confirmed mechanics; it does not settle unspecified
balance values or authorize public deployment.

Owner confirmation, October 29, 2026: the Slayer Tower and the Crafting Guild's
gem-dragon cave are authored in the latest saved editor map. Both await final
migration with the coordinated update package; do not replace or re-author
that saved map while finishing gameplay and release integration.

<a id="current-release-checkpoint--october-31-2026"></a>
<a id="current-release-checkpoint--november-1-2026"></a>

## Current release checkpoint — November 2, 2026

This is the current release scope and closeout checklist. It supersedes earlier
17-spell, Zamorak-deferred, unselected-icon and undecided-coating summaries.
Source implementation, selected map, offline validation, owner acceptance and
public activation are separate states.

The accepted source scope includes **20 Cleric spells**: nine blessings, all
nine god-aligned curses (including Zamorak's Malice/Cruelty/Ruin), Unify and
Greater Unify. All 20 owner-selected spell icons are approved and installed.
The 18 god spells have selected cumulative effect animations; Unify remains
animationless. Buff effects are recipient-only; curse effects appear on NPCs
for nearby observers. The accepted concise spellbook and detailed guide remain.
See the [blessing](cleric-buff-animations-review.md) and
[curse animation](cleric-debuff-animations-review.md) receipts.

All 15 permanent single-use battleaxe coatings, shared Slow, poison/antidotes,
Grace/Blight/Moonsilver production, Cosmic combat luck, gem dragons and floating
hit numbers are implemented. The owner approved the thumb-hidden glove icon
and accepted no worn monocle visual; those are closed art decisions. The tower
and gem cave are authored, with repaired paired map `e343c03b…e58cf` selected for
private acceptance. Existing content approvals remain valid.

The November 2 audit found two release-closeout omissions: the prepared
`want_slayer_tower_tasks: false` setting excludes the eight new task families,
and Deathwing has placed NPCs/family data but no assignment. The owner approved
corrections. Those fixes and versioned progression migration are **in progress**
on `fix/slayer-release-task-activation`; they are not claimed integrated or
accepted by this documentation. Supplemental test repair is reviewed separately.

**Deferred:** Cleric Tier 4, Mystic Blue Dragon, Fierce Red Dragon, Corporeal
Beast and Ante. Boss rewards/hide sets and Ante's balance/honesty design remain
future work. No broader system, terrain or art expansion is required here.

### Release closeout checklist

- [ ] Integrate exact reviewed closeout handoffs. Confirm the prepared release
  configuration actually enables `want_slayer_tower_tasks`, all eight new
  families are assignable and Deathwing appears at the intended tier. Validate
  fresh and existing-player progression/version migration and contact/stair
  routes. Tests that force the flag on do not prove the shipped configuration.
- [ ] Run the final combined commit through maintained regressions, the five
  repaired supplemental gates, compiler checks and whole-program static review.
  Preserve failures and investigate new findings individually; a stale fixture
  or an unfinished analyzer is not a passing gate. Serialize heavy JVM jobs and
  give the final analyzer adequate heap and a bounded run window.
- [ ] Build paired artifacts and the exact source-selected map, then perform
  the final **ordinary-player** private walkthrough: assignment/access/travel,
  encounter combat, reagent and egg acquisition, coating recipe/apply/use/wipe,
  gem drops/equipment, sigil alloy/mould/carve/charge, Cleric/Cosmic interactions,
  all selected visuals, hit numbers, saving, reconnecting and restarting. Keep
  development-command checks separate from normal-account acceptance.
- [ ] Record owner gameplay/visual acceptance and any bounded exclusions against
  that exact candidate. Preserve the original editable map and prior packages;
  do not reopen approved content as a broad redesign/reapproval backlog.
- [ ] Complete final artifact/map hashes, migration rehearsal, backups/rollback,
  normal launcher/updater checks and exact source bookkeeping. Refresh staging
  after corrections; do not call earlier evidence current after changing inputs.
- [ ] Obtain the owner's final release label. Public activation requires a
  separate maintenance window, fresh explicit shutdown permission and the normal
  in-game warning/countdown. Preparation or publication does not authorize it.

The manager maintains `/home/justin/spoiled-milk-next-update`. See the
[candidate assembly](next-update-candidate-assembly-review.md) and
[migration package](next-update-migration-package.md) records. The active work is
bounded release closeout and combined acceptance, not feature expansion.

## 1. Slayer Tower and associated content — implemented; final assembly

- The owner accepted the tower decoration on October 26. Preserve the authored map.
- Core will guard initial entry and the later **upward stairs**, with associates
  in safe rooms behind freely accessible auto-closing doors. Final migration must
  include the reviewed NPC-capacity work and completed placement/binding; see
  [implemented tower bindings](slayer-tower-stair-guard-migration-review.md).
  The [initial capacity investigation](slayer-tower-final-placement-and-capacity.md)
  is historical, not a fresh implementation backlog.
- The axe-based coating implementation is integrated into Core and staged;
  owner visual/private-gameplay review remains. Preserve the approved October 29
  design rather than reopen its recipe, application and balance decisions.
  See [coating implementation](battleaxe-coatings-implementation-review.md).
- Most existing Slayer encounters, equipment and sprites are already approved
  and tested. They were separated to allow unrelated fixes-only releases.
  Assemble that approved content with normal integration checks; do not treat
  release separation as a new development or broad reapproval backlog.
- Antidote/poison cleansing and shared Slow are integrated and checked. The
  owner added ground spawns for the new antidote eggs to the latest saved map;
  their acquisition awaits that map's final migration, not invented new drops.
- Verify the assembled content and final map together before coordinated release.

References: [coating design](../rough-drafts/slayer-unique-equipment-design.md),
[Slow foundation](shared-slow-foundation.md), and the existing tower plans.

## 2. Cleric — current release stage complete

Owner acceptance covers the current twenty-spell book: nine blessings,
Unify/Greater Unify and all nine god-aligned hostile spells, together with
Grace/Blight production and Cosmic combat luck. On November 1 the owner
explicitly required Zamorak's Malice/Cruelty/Ruin before release, superseding
the temporary hold. Tier 4 remains deferred. Combined private acceptance and
the normal integration/release gates remain required.

### Confirmed overhaul direction — October 29, 2026

- Keep **Saradomin, Guthix and Zamorak sigils**, their existing identities and
  god-altar charging. Stone carving remains unchanged. The later owner-approved
  Moonsilver revision below supersedes direct Silver-nugget carving.
- Repurpose existing neutral sigils as **Grace**, preserving their stable IDs,
  stone/silver forms, all quantities and artwork: raw IDs `3299`/`3307` still
  need infusion; usable IDs `3300`/`3308` remain ready for casting. Keep existing
  inventory, bank, container, map and note references; no duplicate Grace items,
  stock exchange, compensation or database rewrite is needed.
- Preserve the existing neutral sigils as Grace's identity/art baseline. Stone
  keeps its recipe; Silver now uses moulded blanks alongside the new Moonsilver
  grade, as recorded in the current production rules below.
- Use two distinct carved resource families with final owner-approved names:
  **Grace** for support/buffs and **Blight** for hostile/debuff spells.
  “Holy/Unholy” are not their item names.
- God sigil + Grace supplies aligned support; god sigil + Blight supplies
  aligned debuffs. Unassigned Grace/Blight serves its respective generic role. This does not
  give generic spells every god's properties.
- **Consume the distinct resources together when casting**, like rune costs.
  This is owner-confirmed, not a physical item-combination recipe.
- Dip **Blight** carvings at **ichor-well edges under graveyards**: large
  underground liquid areas, not bucket-shaft wells or the prior named Fountain.
  Preserve the purple-water/ichor-leaking dirt-wall visual direction.
- Dip **Grace** carvings at **holy wellsprings beneath Entrana, the Monastery
  and other well-known holy areas**. This replaces neutral-at-any-god-altar
  for Grace infusion. Purpose wells charge no Devotion. Sites/access are map
  authoring; production rules are implemented as recorded below.
- Reorganize the spellbook while salvaging existing effects and infrastructure.
  Area buffs affect allies and area debuffs affect enemies around the caster;
  existing PvP exclusions remain in force.

This supersedes the older four-primary/neutral-altar, Unholy-carving recipe and
Curse-sigil/ichor-river proposals. The shipped twelve-spell book is the historical
reuse baseline; the current private implementation has twenty spells.

### Well visuals and edge access

The approved water/seep assets are installed as game-owned content: deep-purple
**Ichor** and pale-blue **Divine Water**, matching dirt/stone seep walls in
192/275-unit heights, floor definitions 27/28, texture definitions 67/68 and wall
definitions 220–227. See [the native asset review](cleric-well-assets-review.md).
They are automatically discovered from the selected game's definitions/assets.

**Ichor well 1354** and **Divine wellspring 1355** are installed as invisible,
1×1/nonblocking shoreline interactions with **Dip sigils** and appropriate liquid
examine flavor. Generic interaction-only metadata/native picking and discovered
editor-only markers are implemented and tested for software/OpenGL, occlusion
and land-side reach. Place them along the entire accessible liquid edge, next
to walkable shore; no hardcoded editor roster or visible well structure is used.
Blocked shores still need authoring; infusion does not bypass walls or water.

### Implemented carving and infusion — current metal-grade revision

The owner confirmed the existing altar's increased-yield progression for wells.
Stone Grace and Blight still carve from Rune stone + chisel (Crafting 1,
5 XP). Silver and Moonsilver now follow bar → three blanks → carving → charging.
Moonsilver alloys one Silver bar with one Mithril bar; the reusable Sigil mould
is stocked by Dommik and Rommik. Silver/Moonsilver moulding and carving require
Crafting 20/40 with base 10/15 XP. Stone/Silver/Moonsilver charging requires
Blessing 1/16/40 with base 5/10/15 XP. Output increases by one per ten levels above
the requirement, using the existing altar multiplier and diminishing-XP formula.
Purpose wells charge **no Devotion**. Existing stocks and identities are retained.
See [current metal production and costs](cleric-metal-sigil-production-review.md).

Grace retains raw/infused IDs 3299/3300/3307/3308, all stock and original artwork;
it now uses Divine wellsprings instead of god altars. Blight uses new IDs 3426–3429
and the approved Thorn artwork; Moonsilver adds its own grade. Uninfused forms
are nonstackable; infused forms stack; neither is noteable. **Dip sigils** processes all matching grades
independently; using a raw sigil on its well processes all stock of that grade.
Wrong wells, already infused/god/noted items, unsupported output and fatigue
are refused without that grade's consumption or XP. God-sigil altars retain
their existing production and Devotion rules.

See [the implementation and validation review](grace-blight-well-infusion-review.md).
Production and the mixed-resource overhaul are implemented in the candidate.
The launch twelve-spell roster is historical; the active source has 20 spells, including all nine curses.

### Implemented progression — three tiers, six power levels

The ordinary book has three tiers, each with one blessing and one implemented
curse per god. Later blessings add the next god component to the earlier
ones. Tier unlocks use **Worship**, not Blessing. Tier 3 targets roughly Worship
40, matching elemental single-element tiers 1/20/40 (dual-element 10/30/50).
Cleric tier 1/2 unlocks 1/20 are the implemented baseline accepted for this stage.
Tier 4 unique buffs/debuffs remain deferred.

Ordinary god buffs and implemented god debuffs have tier radii **2/3/4** (`tier + 1`),
independent of Holy Power. The universal curve selects rank; each component
uses its own approved incremental values. Standalone Tier 1 caps at P2; its
inherited component inside Tier 3 can reach P6. The owner approved the revised
values below on October 31, superseding earlier `3P`/`2P` proposals and the
former Saradomin `P+1` draft. The private implementation candidate exposes twenty
spells; retired launch IDs remain reserved and are refused. All blessings last
**60 seconds**, with one composite snapshot/expiry per god.

| Component | P1→P6 progression |
|---|---|
| Sara bonus damage against undead (new Tier 1+) | +25/35/45/55/65/75% |
| Sara regeneration (moved to Tier 2+) | ×2 /2.5 /3 /3.5 /4 /4.5 |
| Sara delayed HoT (moved to Tier 3) | 1/2/3/4/5/6 HP per pulse; 10 delayed pulses about 6s apart |
| Guthix extra cleanse | 2/3/4/5/6/7 per existing poison pulse, additive |
| Guthix drained non-HP stats | Restore 1 each, every 10/9/8/7/6/5s, cap at normal |
| Guthix Barkskin (Tier 2+) | +5/7/9/11/13/15% to ranged defense |
| Guthix reflection (Tier 3) | 5/6/7/8/9/10% factual direct damage |
| Zam maximum melee damage | +5/7/9/11/13/15% |
| Zam dealt melee damage (Tier 2+) | +3/4/5/6/7/8% |
| Zam lifesteal (Tier 3) | 5/6/7/8/9/10%, only while below half HP |

Positive maximum increments round up (zero remains zero); defense rounds down.
Dealt damage/lifesteal/reflection carry separate fractional hundredths, not
minimum-one damage per hit. Recasts preserve periodic cadence; delayed pulse 10
settles at the 60-second boundary. Staff requirements are normalized in the
candidate to Magic-only at the existing Zamorak ladder, preserving stats/art.

Shared power levels P1–P6 require Holy Power **0/20/45/75/115/160**. Tier caps
are **2/4/6**: two additional available ranks per tier, with no raised minimum.
Actual power is `min(Holy-Power-resolved rank, tier cap)`. Holy Power includes
**Worship + strongest applicable holy staff**; unequipping a staff does not
remove Worship's contribution. Maple/Yew/Ebony/Magicwood/Blood/god staffs add
32/40/44/48/56/64 respectively; 99+64=163 permits P6. Examples with sufficient
tier caps: Worship 40 + Maple 32 = 72→P3; 60 + Ebony 44 = 104→P4; 80 + god 64 = 144→P5;
96 + god 64 = 160→P6.

| God | Tier 1 | Tier 2 addition | Tier 3 addition |
|---|---|---|---|
| Guthix | Faster poison cleansing over time + gradual drained-stat restoration, on the same blessing timer | Barkskin defense increase (not Ward's flat damage reduction) | Thorns reflect |
| Saradomin | Hallowed Strikes: extra damage against undead, 25% +10 percentage points per power step | Quickened Pulse: natural HP regeneration ×2 +0.5 per power step | Mending Flesh: P HP per delayed pulse, ten pulses about six seconds apart |
| Zamorak | Maximum-damage percentage increase | Total-dealt-damage percentage increase | Lifesteal |

### Confirmed blessing names and recipient flavor

These messages are addressed to the recipient when the blessing is cast/applied
to them. Exact additional trigger policy and whether lower-tier messages also
appear are not specified here.

| God | Tier | Blessing name | Recipient message |
|---|---|---|---|
| Saradomin | 1 | Saradomin's Light | Your attacks carry a holy light that burns the restless dead. |
| Saradomin | 2 | Saradomin's Aura | You feel your blood rush and your spirits lift |
| Saradomin | 3 | Saradomin's Resplendence | Your wounds seal up as soon as they are opened |
| Guthix | 1 | Guthix's Respite | Your blood feels thick in your veins like sap. Your ailments slow to a halt |
| Guthix | 2 | Guthix's Protection | Your skin grows a thick layer of bark |
| Guthix | 3 | Guthix's Vengeance | Sharp spikes protrude from you, piercing your foes. |
| Zamorak | 1 | Zamorak's Fervor | Your muscles bulge and your mind feels sharper |
| Zamorak | 2 | Zamorak's Fury | Your enemies seem much smaller than before |
| Zamorak | 3 | Zamorak's Wrath | Your weapon will breathe new life into you should you feel weak |

**Guthix's Respite** names the new tier-one poison-cleansing/drained-stat
restoration blessing. It is distinct from the removed **old neutral Respite**
regeneration spell. **Zamorak's Fervor** names the new tier-one maximum-damage
blessing; it does not preserve the old accuracy mechanic. The new Guthix
recipient text supersedes the earlier sap line.

Flavor imagery adds no mechanics: it does not make healing instant, guarantee
ailment immunity, grant accuracy, or set a lifesteal activation threshold.
Existing approved component formulas, timings and open balance decisions remain
as recorded above. The candidate applies the revised names/messages and Saradomin tier order.

The owner revised Saradomin's order: undead damage → regeneration → delayed
healing. At Tier 3/P6 the revised design gives **+75% damage against undead,
×4.5 regeneration and 6 HP per pulse/60 HP over ten delayed pulses**. The healing
values still replace the earlier ×7/70 HP draft. The owner confirmed that the
undead bonus applies to **melee, ranged and Magic**. The owner approved
**Saradomin's Light → Hallowed Strikes** and the recipient message in the table
above. Existing legacy undead classification must be audited
and extended with server-owned nullable tags. Explicit true additions359/498/613/614/615/865 and Farmer319=false are implemented; old quest/classic predicates remain unchanged.

The progression/composite foundation and latest Saradomin revision are implemented in the **private candidate**;
the old twelve-spell roster is historical salvage, not its active roster. **Keep Unify unchanged
except for a flat cost of two Grace sigils per cast, no god sigil (not per
recipient); exclude the old neutral Respite from the
future book because of abuse concerns.** Unify uses Worship 3, radius 3,
party-allies-only targeting excluding the caster, at most one safe ordinary
walking step toward the caster stopping within two tiles, same-level/world
and line-of-effect checks, disabled PvP and no Holy Power ranks. The base unlock
and movement are unchanged. Excluding the old neutral Respite does not remove
Saradomin's regeneration, other potion/equipment regeneration, assets or item IDs.

**Greater Unify** is the only upgrade: Worship 30, radius 5, up to three safe
ordinary steps stopping within two tiles, costing 3 Stone Grace +3 Silver Grace,
no god sigil or Holy Power scaling. Base Unify costs 2 Stone Grace per cast.

The owner-approved current-plus-previous grade costs replace temporary early
private-build defaults. Each god spell spends matching god and purpose sigils:
Tier 1 uses 1 Stone of each; Tier 2 uses 1 Stone +1 Silver of each; Tier 3 uses
1 Silver +1 Moonsilver of each. Purpose is Grace for blessings and Blight for
hostile spells; Tier 3 spends no Stone. The entire vector is spent atomically
once per useful cast, never per recipient; no eligible/useful target means no
cost. Same-god versions do not stack or downgrade, equal strength safely
refreshes, and all three gods may coexist.

Owner-approved Guthix debuff design: **Toxiphobia** adds poison weakness,
**Agrizoophobia** adds summon weakness and **Hematophobia** adds recoil/reflected
damage weakness. All three use `10% + 5% * (P - 1)` → 10/15/20/25/30/35% with
the common tier caps 2/4/6. Tier 1 reaches 15%; Tier 2 reaches 25% on both
components; Tier 3 reaches 35% on all three. Poison weakness increases poison
damage and reduces cleansing per pulse, without inflicting poison or removing
immunity. Summon/recoil weakness increases only its corresponding damage.
The candidate implements exclusive per-god NPC slots, solo-capable logical area admission,
atomic flat God+Blight costs, poison ceil/cleansing floor1 and summon/recoil fractional
carry. No poison immunity is removed and PvP stays disabled.
The owner confirmed separate spell/effect naming: buffs need recipient flavor
messages as well as spell/effect names; debuffs need spell/effect names without
recipient flavor. Guthix's Respite grants **Sap Blood**, Protection adds
**Barkskin**, and Vengeance adds **Thorns**, retaining the earlier components.
Toxiphobia/Agrizoophobia/Hematophobia are **effect names**. The owner approved
the other defined buff effect names:
**Quickened Pulse** and **Mending Flesh**, now Saradomin's Tier 2/3 components;
**Unnatural Might**, **Bloodrage** and **Blood Hunger** for Zamorak's three.
Saradomin's Tier 1 flavor is approved: **Light → Hallowed Strikes**, with the
holy-light/restless-dead recipient message above.
The owner approved all three debuff spell-title chains: Guthix's
Unease/Dread/Terror; Saradomin's Censure/Condemnation/Unmaking; Zamorak's
Malice/Cruelty/Ruin. The governing Cleric document maps each title to its effect.
The candidate implements the approved presentation model for all active spells.

The owner's earlier order was **review power strengths/inherited P6 combinations →
Greater Unify → return to debuffs**. All three god curse lines are now implemented;
combined ordinary-player acceptance remains on the current checklist above.

### Zamorak critical-hit debuffs — owner direction, October 31

Implemented and required for this release following the owner's November 1
confirmation. The earlier temporary hold is superseded. Higher tiers inherit
earlier components under the shared Holy Power curve and power caps 2/4/6:

- **Expose Weakness:** melee/ranged/Magic damage rolls can become maximum-damage
  critical hits; chance5% +2 percentage points per power step, reaching15% atP6.
- **Weak Legs:** critical hits have25% +5 percentage points per power step to
  stagger, reaching50% atP6. Stagger loses one NPC attack turn. The NPC must then
  complete five uninterrupted attacks before it can be staggered again;
  recasts/multiple attackers must not bypass this protection.
- **Weak Constitution:** critical hits against sufficiently wounded ordinary
  enemies can instantly kill them. HP threshold15% +2 percentage points per
  power step; proc chance50% +5 percentage points per power step. P6 is at or
  below25% HP with75% chance per qualifying crit. **Bosses are exempt from the
  instant kill**, explicitly confirmed by the owner.

See the [governing debuff design](cleric-spellbook-concept.md#owner-selected-zamorak-debuffs--october-31-2026)
for all six values, conditional proc semantics and the completed shared
critical/Stagger integration. Do not infer boss immunity to all three components from
the execution restriction, or count five ticks as five completed NPC attacks.

The integrated implementation narrows Zamorak's maximum/dealt bonuses to melee
and Barkskin to ranged defense. Blood Hunger retains its eligible direct-hit
scope. The prepared package must track the final reviewed combined commit.
Saradomin's hostile effect names are now selected: **Magical Allergy**
(Magic-defense reduction), **Muscular Atrophy** (maximum-melee-damage reduction)
and **Molecular Antipode** (temporary reusable non-corporeal state, with Magic
permitted in both directions). Entry rolls occur each eligible tick at
5/7/9/11/13/15% for P1–P6; state duration is 1/1/1/2/2/3 game ticks, explicitly
confirmed by the owner. Re-entry requires at least one actual corporeal NPC
attack attempt. The owner approved Magic-defense reductions
10/13/16/19/22/25% and melee-maximum reductions 5/7/9/11/13/15%.
The candidate implements shared50% NPC opacity without sprite replacements,
with native packet and software/OpenGL alpha checks. Private gameplay acceptance
remains. See the governing Cleric document for decisions and open boundaries.
Other hostile suggestions remain unselected.

God+Grace buffs, all nine implemented god+Blight debuffs and atomic joint resource
consumption are present in the integrated source. Generic purpose-only costs replace the god
share with extra purpose sigils (`A god + B Grace` becomes `A+B Grace` within
compatible grades); Unify uses 2 Stone Grace and Greater Unify uses 3 Stone
Grace +3 Silver Grace. The approved mixed-grade vectors are recorded above.
Production and the scoped hostile casting branch are complete in source;
Core integration/private gameplay acceptance are separate.

### Later follow-up, not current release gates

Optional later cost/economy refinement and Tier 4 remain follow-up work.
Zamorak activation and its eligible-critical/execution decisions are implemented
required content. General release gameplay QA remains part of the combined
candidate pass; it does not reopen this accepted stage.
Unify and Greater
Unify behavior/unlocks are settled; the old neutral Respite remains excluded.
Tier 4 unique spells remain later work.
The [active governing design](cleric-spellbook-concept.md#active-overhaul-three-ordinary-tiers-and-six-power-levels--october-30-2026)
records the six thresholds, examples and distinction from the historical book.

Reference: [governing Cleric concept and impact audit](cleric-spellbook-concept.md).

Candidate completion now includes Guthix Unease/Dread/Terror and Saradomin
Censure/Condemnation/Unmaking (appended22–27), all inherited component names,
receiver flavor for buffs only, Hallowed undead damage, ranged-only Barkskin and
melee-only Zamorak damage bonuses. Non-corporeal durations1/1/1/2/2/3 game ticks
and the confirmed magical-combat-only exception are implemented with independent
50% NPC opacity. Poison keeps cleansing without harm while phased; no catch-up.
Rearm requires a tangible attack attempt and a following tick before another roll.
Matched schema5/client10056 is required; owner maps and public activation remain
separate. See [implementation review](cleric-consistency-implementation-review.md).

Zamorak's Malice/Cruelty/Ruin are now required and implemented in the feature
candidate as stable codes28–30: authored critical vulnerability, shared
one-attack Stagger with five-attack immunity, and pre-hit-threshold execution
for ordinary enemies only. Bosses and protected quest/script encounters cannot
be executed. Their normal damage, attribution and death rules remain intact.
The implemented Cosmic replacement is
**combat luck**:1percentage point per equipped robe tier, capped50%. Named damage/
defense rolls and beneficial combat checks can select one favorable extra candidate;
loot, skilling, random choices and recurring curse checks are excluded.
It can favor the named critical/Stagger/execution chance checks without
replaying attacks. The proposed penetration redesign remains unimplemented.

## 3. Boss-dragon expansion — deferred beyond this release

The **Elder Green Dragon** is already added, and the **King Black Dragon** is
the existing staple. The later roster expansion preserves these candidates:

- **Mystic Blue Dragon**.
- **Fierce Red Dragon**.

Encounter mechanics, stats, drops, locations and access requirements for the
new dragons need their own specifications. Their names alone do not establish
attack-style or balance rules, or authorize reworking the existing two bosses.
The owner has removed both additions from the next release. Worthwhile drops,
potential hides and associated armor sets should be designed before resuming them.

## 4. Gem dragons — implemented; acceptance and migration

- **Sapphire, Emerald, Ruby, Diamond and Dragonstone dragons** are implemented
  and staged, with the approved native gem-dragon model, scaling defenses,
  guaranteed gem outcomes, tiered Crafting-oriented normal loot and shared
  unique equipment. This is no longer an unstarted variety/drop-table design.
- The owner confirmed the **Crafting Guild gem-dragon cave is authored** in the
  latest saved editor map. Preserve that map alongside the finished Slayer Tower.
- Complete combined private testing/owner acceptance and final map migration
  with the update package. Do not invent new access requirements or re-author
  the owner's underground layout while doing integration.
- Both equipment art loose ends are closed: the owner approved the thumb-hidden
  v2 glove icon and chose no worn monocle visual. No dedicated worn art is required.
  Its inventory icon is already approved; the dragon model is also approved.

References: [gem-dragon implementation](gem-dragon-family-review.md),
[guaranteed gems](gem-dragon-guaranteed-gems-review.md) and
[normal rare Scrap outcome](gem-dragon-normal-scrap-review.md).

## 5. Corporeal Beast — deferred beyond this release

The owner has removed this boss from the next release. Resume only after its
worthwhile rewards, potential hide and armor-set direction are established.
The design below is preserved; no new boss/item implementation is authorized by
its presence here. Existing shared non-corporeal support remains in the game.

The boss phases between a **physical form** and a **spirit form**.

### Gimmick one: outgoing damage conversion

- A gimmick item changes the player's damage from **physical to spirit** so
  the player can handle the boss's form mechanic.
- The exact damage/form interaction and item operation remain to be specified.
  Spirit damage is a proposed distinction; do not silently equate it with the
  existing Magic attack style or invent a new trained skill/stat.

### Gimmick two: timed defensive form switching

- Add a timing-based protection mechanic comparable in concept to the Dark
  Beast's thunder-strike counterplay.
- The player uses a **potion** to switch their own state between **spiritual**
  and **corporeal**.
- Which state avoids the incoming attack depends on the attack the boss uses;
  the player must make the appropriate switch in time.
- This is a second requirement, distinct from converting outgoing damage.
  Whether the two item roles share any implementation is not yet decided.

Boss location/access, level, phase timing, attack warnings, correct defensive
state for each attack, damage, rewards and item recipes/durations/charges remain
open. The Dark Beast reference does not copy its numerical timers or damage
values into this encounter automatically.

## Scope boundary

**Ante remains on hold** for the owner's reconsideration of balance and honest
risk/reward. It is not part of these immediate goals. Older expansion plans
remain preserved; they do not automatically enlarge this release's scope.
New visual assets remain game-owned content discovered by the editor, rather
than a roster or asset bundle built into the editor itself.

### Implemented shared Stagger and critical-hit foundation

The integrated foundation consolidates Ogre/Startle into one actor-owned
Stagger authority: the next attack deals zero without surrendering its normal
turn/cadence, followed by five actual unstaggered attacks before reapplication.
Pending multi-attack losses cannot be replaced or queued. Natural zero attacks
count toward recovery; polls, incoming hits and elapsed time do not. NPCs and
the existing NPC-to-player Startle path share the rule, without new PvP admission.

Critical hits now have explicit immutable per-hit identity across ordinary
melee, player ranged/throwing, and Magic launch/settlement. Cosmic supplies combat luck rather than critical chance. Expose Weakness, Weak
Legs and Weak Constitution are implemented and enabled through the Zamorak
curse line, with ordinary-enemy execution restrictions and the shared recovery
rules. Their accepted values are recorded above.
Whip/Sticky extra attack delays compose separately from Stagger, preserving
normal style/base cooldown ownership and existing movement/utility scope.
Fresh uncaptured Slow affects the upcoming attack, adding only its new tier
delta to that wait; captured Slow is never multiplied again. This is the
disclosed working interpretation of the owner's three-ticks example, not a
claimed explicit reply to the optional timing clarification.
See [the foundation review](shared-critical-stagger-foundation-review.md).
