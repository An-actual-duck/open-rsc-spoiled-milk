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
  in safe rooms behind freely accessible auto-closing doors. Resolve the NPC
  capacity import blocker before final placement/binding; see
  [final tower placement and capacity](slayer-tower-final-placement-and-capacity.md).
- Finish the axe-based Slow coating using the shared Slow foundation, including
  its remaining recipe, application and balance decisions.
- Most existing Slayer encounters, equipment and sprites are already approved
  and tested. They were separated to allow unrelated fixes-only releases.
  Assemble that approved content with normal integration checks; do not treat
  release separation as a new development or broad reapproval backlog.
- Verify poison/cleansing and Slow behavior as part of the final combined build.

References: [coating design](../rough-drafts/slayer-unique-equipment-design.md),
[Slow foundation](shared-slow-foundation.md), and the existing tower plans.

## 2. Expand Cleric buffs and add the hostile debuff branch

### Curse sigils and ichor

- Add a **Curse sigil**.
- Its ichor-dipping route replaces taking it to an ordinary blessing altar.
- Ichor is found in **underground rivers beneath graveyards**.
- Reuse the existing water visual, recoloured **purple**, for the rivers.
- Add fishing-spot-style interactable scenery along the river edges for dipping
  the sigils. The spots are the interaction points; the water visual is separate.
- Add a new wall asset based on the existing **dirt walls**, with **ichor leaking
  from them**, to support these underground areas.

This is the current direction in place of the older standalone Fountain of
Ichor proposal. Exact raw/dipped item identities, materials, tier recipes,
requirements and costs still need design. Do not silently assume the older
Unholy-sigil names or stone/silver recipes define the new Curse-sigil line.

### Debuff casting and remaining tiers

- Hostile Cleric spells work like the support buffs spatially: they affect an
  area **around the caster**, but affect **enemies only**.
- The owner will define the remaining tiers for **both buffs and debuffs**.
- The shipped twelve-spell launch book remains the existing foundation.
- Exact hostile eligibility, boss/PvP rules, effects, radii, costs and numerical
  progression remain to be specified; existing PvP exclusions are not lifted.

Reference: [governing Cleric concept](cleric-spellbook-concept.md).

## 3. Round out the boss-dragon roster

The **Elder Green Dragon** is already added, and the **King Black Dragon** is
the existing staple. Complete the intended roster with:

- **Mystic Blue Dragon**.
- **Fierce Red Dragon**.

Encounter mechanics, stats, drops, locations and access requirements for the
new dragons need their own specifications. Their names alone do not establish
attack-style or balance rules, or authorize reworking the existing two bosses.

## 4. Add gem dragons beneath the Crafting Guild

- Expand beneath the **Crafting Guild** to give it a meaningful **high-level
  area** and additional functionality.
- Populate it with **gem dragons**.
- Their drop tables emphasize **high gem drop rates** and **other useful
  Crafting drops**.

Gem-dragon varieties, levels, encounter behavior, exact materials/drop rates,
entry requirements and the underground layout remain to be designed.

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
