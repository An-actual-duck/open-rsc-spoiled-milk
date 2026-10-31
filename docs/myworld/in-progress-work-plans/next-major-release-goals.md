# Immediate goals for the next major release

Owner direction recorded October 24, 2026. These are the immediate content
goals before the next release. Beta designation remains a later decision.
This records intent and confirmed mechanics; it does not settle unspecified
balance values or authorize public deployment.

Owner confirmation, October 29, 2026: the Slayer Tower and the Crafting Guild's
gem-dragon cave are authored in the latest saved editor map. Both await final
migration with the coordinated update package; do not replace or re-author
that saved map while finishing gameplay and release integration.

## 1. Complete the Slayer Tower and associated content

- The owner accepted the tower decoration on October 26. Preserve the authored map.
- Core will guard initial entry and the later **upward stairs**, with associates
  in safe rooms behind freely accessible auto-closing doors. Final migration must
  include the reviewed NPC-capacity work and completed placement/binding; see
  [final tower placement and capacity](slayer-tower-final-placement-and-capacity.md).
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

## 2. Expand Cleric buffs and add the hostile debuff branch

### Confirmed overhaul direction — October 29, 2026

- Keep **Saradomin, Guthix and Zamorak sigils** and their existing stone/silver
  carving and god-altar production process unchanged.
- Repurpose existing neutral sigils as **Grace**, preserving their stable IDs,
  stone/silver forms, all quantities and artwork: raw IDs `3299`/`3307` still
  need infusion; usable IDs `3300`/`3308` remain ready for casting. Keep existing
  inventory, bank, container, map and note references; no duplicate Grace items,
  stock exchange, compensation or database rewrite is needed.
- Reuse the existing neutral stone/silver carving materials, recipes, Crafting
  requirements and XP as Grace's baseline, without a carving-cost rebalance.
- Use two distinct carved resource families with final owner-approved names:
  **Grace** for support/buffs and **Blight** for future hostile/debuff spells.
  “Holy/Unholy” are not their item names.
- God sigil + Grace supplies aligned support; god sigil + Blight will supply
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
Curse-sigil/ichor-river proposals. The shipped twelve-spell book is the current
runtime and reuse baseline, not a completed implementation of this overhaul.

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

### Implemented carving and infusion — October 30, 2026

The owner confirmed the existing altar's increased-yield progression for wells.
Grace and Blight both carve from Rune stone + chisel (Crafting 1, 5 XP) or Silver
nugget + chisel (Crafting 20, 10 XP), producing one uninfused sigil. Their stone/
silver infusion requirements are Blessing 1/16 with base 5/10 XP. Output increases
by one per ten levels above the requirement, using the exact existing altar
multiplier and diminishing-XP formula. Purpose wells charge **no Devotion**.

Grace retains raw/infused IDs 3299/3300/3307/3308, all stock and original artwork;
it now uses Divine wellsprings instead of god altars. Blight uses new IDs 3426–3429
and the approved Thorn artwork. Uninfused forms are nonstackable; infused forms
stack; neither is noteable. **Dip sigils** processes both matching grades
independently; using a raw sigil on its well processes all stock of that grade.
Wrong wells, already infused/god/noted items, unsupported output and fatigue
are refused without that grade's consumption or XP. God-sigil altars retain
their existing production and Devotion rules.

See [the implementation and validation review](grace-blight-well-infusion-review.md).
This completes production, not the mixed-resource casting or hostile spellbook
overhaul. Current casting remains the twelve legacy spells with unchanged
costs/effects/categories; their neutral resource is now displayed as Grace.

### Confirmed future progression — three tiers, six power levels

The new ordinary book has three tiers, each with one blessing per god and a
future debuff per god. Later blessings add the next god component to the earlier
ones. Tier unlocks use **Worship**, not Blessing. Tier 3 targets roughly Worship
40, matching elemental single-element tiers 1/20/40 (dual-element 10/30/50).
Cleric tier 1/2 unlocks 1/20 are a planning baseline pending explicit finalization.
Tier 4 unique buffs/debuffs remain deferred.

Shared power levels P1–P6 require Holy Power **0/20/45/75/115/160**. Tier caps
are **2/4/6**: two additional available ranks per tier, with no raised minimum.
Actual power is `min(Holy-Power-resolved rank, tier cap)`. Holy Power includes
**Worship + strongest applicable holy staff**; unequipping a staff does not
remove Worship's contribution. Maple/Yew/Ebony/Magicwood/Blood/god staffs add
32/40/44/48/56/64 respectively; 99+64=163 permits P6. Examples with sufficient
tier caps: Worship 40 + Maple 32 = 72→P3; 60 + Ebony 44 = 104→P4; 80 + god 64 = 144→P5;
96 + god 64 = 160→P6.
Staff-access cleanup is still needed: Zamorak Ebony/Magicwood currently require
Magic 54/62, versus Saradomin/Guthix legacy Magic 52/59 and legacy melee gates.
These are current inconsistencies, not approved new equipment restrictions.

| God | Tier 1 | Tier 2 addition | Tier 3 addition |
|---|---|---|---|
| Guthix | Faster poison cleansing over time + gradual drained-stat restoration, on the same blessing timer | Barkskin defense increase (not Ward's flat damage reduction) | Thorns reflect |
| Saradomin | Natural HP regeneration multiplier `P+1` (minimum ×2) | `P+1` HP per pulse (minimum 2), 10 pulses roughly 6 seconds apart/about a minute | Open |
| Zamorak | Maximum-damage percentage increase | Total-dealt-damage percentage increase | Lifesteal |

Guthix flavor: “Your blood thickens like sap, slowing your ailments.” The old
Fervor accuracy effect is not part of Zamorak's new first component. Saradomin
at P6 means **×7 regeneration and 7 HP per pulse/70 HP per minute**; those are the
confirmed formulas' arithmetic consequences, with balance review still needed.
Do not silently reduce them or treat the formulas as unapproved.

These progression/composite rules are **design only**. The existing twelve-spell
runtime, costs/effects and spell-specific ranks remain unchanged. Its two-tier
roster is salvage/history, not the active future roster. **Keep Unify unchanged
except for a cost of two Grace sigils, no god sigil; exclude Respite from the
future book because of abuse concerns.** Unify retains Worship 3, radius 4,
party-allies-only targeting excluding the caster, a maximum of two safe ordinary
walking steps toward the caster stopping within two tiles, same-level/world
and line-of-effect checks, disabled PvP and no Holy Power ranks. No added tiers
or unlock shift are approved. Two Grace is the confirmed quantity; grade
correspondence remains open (current Stone Grace 3300 is a reference, not a
new grade decision). Excluding Respite does not remove Saradomin's planned
regeneration, other potion/equipment regeneration, assets or item IDs.

God+Grace buffs, god+Blight debuffs and joint resource consumption remain
confirmed future casting direction. Generic purpose-only costs replace the god
share with extra purpose sigils (`A god + B Grace` becomes `A+B Grace` within
compatible grades); Unify's two-Grace quantity is settled, while other exact
vectors and grade correspondence remain open. Production remains complete.

### Decisions still needed

Saradomin's third component; remaining magnitudes, exact tier unlocks, names,
durations and mixed casting costs/grades; composite targeting/stacking; debuff
components and book layout. Unify is retained and Respite excluded; neither
roster decision remains open. Tier 4 remains later work.
The [active governing design](cleric-spellbook-concept.md#active-overhaul-three-ordinary-tiers-and-six-power-levels--october-30-2026)
records the six thresholds, examples and distinction from the historical book.

Reference: [governing Cleric concept and impact audit](cleric-spellbook-concept.md).

## 3. Round out the boss-dragon roster

The **Elder Green Dragon** is already added, and the **King Black Dragon** is
the existing staple. Complete the intended roster with:

- **Mystic Blue Dragon**.
- **Fierce Red Dragon**.

Encounter mechanics, stats, drops, locations and access requirements for the
new dragons need their own specifications. Their names alone do not establish
attack-style or balance rules, or authorize reworking the existing two bosses.

## 4. Finish gem-dragon acceptance and migrate the Crafting Guild cave

- **Sapphire, Emerald, Ruby, Diamond and Dragonstone dragons** are implemented
  and staged, with the approved native gem-dragon model, scaling defenses,
  guaranteed gem outcomes, tiered Crafting-oriented normal loot and shared
  unique equipment. This is no longer an unstarted variety/drop-table design.
- The owner confirmed the **Crafting Guild gem-dragon cave is authored** in the
  latest saved editor map. Preserve that map alongside the finished Slayer Tower.
- Complete combined private testing/owner acceptance and final map migration
  with the update package. Do not invent new access requirements or re-author
  the owner's underground layout while doing integration.

References: [gem-dragon implementation](gem-dragon-family-review.md),
[guaranteed gems](gem-dragon-guaranteed-gems-review.md) and
[normal rare Scrap outcome](gem-dragon-normal-scrap-review.md).

## 5. Add the Corporeal Beast as a Slayer boss

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
