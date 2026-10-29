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
  for future Grace infusion; it does not imply charging a god's Devotion.
  Exact sites/access and new infusion balance/Devotion policy remain open.
- Reorganize the spellbook while salvaging existing effects and infrastructure.
  Area buffs affect allies and area debuffs affect enemies around the caster;
  existing PvP exclusions remain in force.

This supersedes the older four-primary/neutral-altar, Unholy-carving recipe and
Curse-sigil/ichor-river proposals. The shipped twelve-spell book is the current
runtime and reuse baseline, not a completed implementation of this overhaul.

### Current two-tier Grace mapping

Map all twelve existing spells as **Grace/support**, retaining their current god
assignments and Worship unlocks: tier one Mend, Unify, Fervor, Purify, Restore,
Ward; tier two Greater Mend, Zeal, Thorns, Aegis, Rally, Respite. Unify and Respite
are generic; the others use matching god sigils plus Grace. The [full mapping](cleric-spellbook-concept.md#current-two-tier-mapping-and-generic-grace-costs)
records each god and existing unlock without introducing a rebalance. Blight
remains for future debuffs; no mirrored hostile spell list is invented.

A generic Grace cost substitutes additional Grace for the god-resource share:
within compatible tier/grade, `A matching god + B Grace` becomes `A + B Grace`.
The owner's `1 god + 1 Grace` versus `2 Grace` example illustrates this rule;
it does not fix all tier-one costs or approve tier-two counts/material grades.
The old `2 stone + 1 silver` launch vector is not an approved new overhaul price.

### Decisions still needed

Blight's new carving materials, recipes and unallocated IDs; new infusion
requirements/XP/yields/quantities and Devotion policy; exact casting costs and
grade correspondence; and future spells/tiers and layout. Grace's existing
stone/silver carving baseline and same-ID stock preservation are settled.
Joint consumption during casting is settled; its quantities are not. Existing
god-production numbers do not automatically become new wellspring/ichor infusion
rules. The three god-production lines remain unchanged.

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
