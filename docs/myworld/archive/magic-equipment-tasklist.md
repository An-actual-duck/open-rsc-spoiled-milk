# Magic Equipment Tasklist

This tracker now covers the immediate staff redesign and robe/cloth cleanup
only.

MyWorld does not have ranged armor, magic armor, melee armor, or combat-style
armor lanes. Armor is defensive gear, with occasional attack-speed sidegrades on
specific pieces. Broader leather and cloth production work has moved to the
long-term goals list because it depends on the wider crafting-skill restructure:

- `Crafting` owning leather and cloth defensive equipment production
- `Crafting` owning base magic-equipment production such as plain staffs and
  robe pieces
- `Enchanting` owning altar-based magical upgrades, attunement, and follow-up
  item modification

Those broader armor-production systems are no longer part of this active staff
todo tracker.

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a design/system dependency

## Current Focus

- `[done]` Implement the standard staff ladder and elemental attunement rules
- `[done]` Replace legacy elemental-staff assumptions with the new
  Enchanting-based attunement path
- `[done]` Implement the first wool robe baseline and dyeable cloth line
- `[done]` Implement the first altar-bound wool robe outputs while preserving
  the base cloth defenses
- `[doing]` Move from the now-live baseline into cleanup, later effect design,
  balancing passes, and remaining legacy retirement

## Locked Direction

- Standard gear balance is still centered on the current tier `1-6` baseline.
- Post-tier-6 content such as `dragon` or other special lines should be handled
  later, after the base ladder is stable.
- Keep the player-facing skill as `Enchanting`.
- Staves should no longer use elemental variants as the main progression ladder.
- Basic staff shaping belongs in `Crafting`.
- The first live cloth baseline uses plain white wool garments crafted through
  `Crafting`.
- The initial wool-garment line is:
  - `Wool wizard hat`
  - `Wool robe top`
  - `Wool robe skirt`
  - `Wool cape`
- The first wool line is intentionally simple:
  - hat, top, and skirt provide entry magic defense
  - cape is cosmetic and dyeable, not defensive
- The first altar-bound robe line is now live across the current altar set:
  - tier `1`: unenchanted wool base
  - tier `2`: air / water / earth / fire
  - tier `3`: mind / body
  - tier `4`: chaos
  - tier `5`: cosmic
  - tier `6`: nature
  - tier `7`: law
  - tier `8`: death
  - tier `9`: soul
  - tier `10`: blood
- Robe enchanting should no longer be treated as one fixed output per altar.
- Instead:
  - the altar determines the effect family
  - the player-selected tier determines the defense strength
  - the same altar family can therefore exist at multiple defense tiers
- The intended robe-enchanting interaction is now:
  - use the robe piece on an altar
  - open a tier-selection menu
  - show every robe tier the player can currently craft at that altar
  - charge `10 x selected tier` of that altar's associated rune
  - produce that altar-family robe at the chosen tier
- The structural rule is now:
  - tier controls defense growth
  - altar/rune controls the static effect identity
- The current robe-effect draft is:
  - `Earth`
    - magic/melee split defense
  - `Air`
    - magic/ranged split defense
  - `Water`
    - magic/ranged/melee split defense
  - `Fire`
    - magic-only defense specialization
    - each equipped piece also adds `+10%` to final total magic defense only
    - this bonus should be applied after total gear defense is summed so
      non-robe sources like gloves/boots are included
  - `Body`
    - increase HP regeneration by `33%` per equipped piece
  - `Mind`
    - reduce debuff effectiveness by `20%` per equipped piece
    - example: a debuff that would lower a stat by `10` only lowers it by `4`
      with all three pieces equipped
  - `Nature`
    - mitigate poison damage by `1` per equipped piece
  - `Cosmic`
    - each equipped piece gives a `10%` chance to reroll a defensive roll and
      keep the better result
  - `Chaos`
    - reflect damage in a recoil-like way at `5%` of incoming damage per
      equipped piece
    - unlike the chaos ring line, this is intended to apply on every hit
  - `Law`
    - no longer tied to the jewelry-side teleport identity for robe design
    - current direction is a defensive "balance" theme
    - enemy damage is rolled an additional time per equipped piece
    - use the mean of all rolls, rounded down, as the pre-armor damage roll
    - example: with all three pieces equipped, damage is rolled `4` times,
      summed, divided by `4`, then rounded down before armor is applied
    - this should smooth incoming spikes downward by averaging the roll rather
      than directly reducing max hit
  - `Death`
    - all-style defense bonus scales with missing HP
    - each equipped piece adds `+10%` defense for every `25%` HP lost
    - at three pieces and below `25%` HP, this reaches a total `+90%`
      defense bonus
  - `Soul`
    - grants a rechargeable protective shield
    - shield recharges only after `10` seconds out of combat
    - each equipped piece grants shield capacity equal to `20%` of max HP
    - example: `10` max HP gives `2` shield per piece, `6` total with all
      three pieces
    - shield recharge should be much faster than normal HP regeneration
  - `Blood`
    - spell lifesteal at `5%` per equipped piece
- Initial wool-defense values follow material cost rather than later full
  balance:
  - hat: `1`
  - skirt: `3`
  - top: `4`
- Staff attunement and magical upgrades belong in `Enchanting`.
- Orb items and orb-charging as the active staff-upgrade path should be removed
  from active staff progression.
- Wood tier should become the main staff base-tier identity.
- The base `staff` item is the only standard staff foundation.
- The standard wood-tier ladder is:
  - `Staff`
  - `Oak Staff`
  - `Willow Staff`
  - `Maple Staff`
  - `Yew Staff`
  - `Magic Staff`
- `Battlestaff` is not part of the standard ladder and should be retired from
  that progression.
- `Magic Staff` is not part of the standard ladder and should be retired from
  that progression.
- The legacy stock `Magic Staff` item is retired from the active standard path;
  the new `Magic Staff` name is reused as the magic-wood tier staff.
- Enchanted staves should reflect both:
  - wood tier on the shaft
  - active elemental identity on the bulb / focus
- For the first implementation pass, preserving the elemental cue matters more
  than perfect wood visuals:
  - use elemental staff visuals to indicate air / water / earth / fire
  - keep wood-tier palette-swap work as follow-up visual cleanup
- Special staffs remain separate from the standard `1-6` ladder and should be
  treated as above-standard-tier gear.
- Enchanting a staff should happen by using the staff directly on the altar.
- Elemental staff attunement keeps the old elemental effect:
  - air attunement removes air rune cost
  - water attunement removes water rune cost
  - earth attunement removes earth rune cost
  - fire attunement removes fire rune cost
- The altar attunement effect should exist across every standard staff tier.
- Staff naming should follow the rune + material pattern:
  - base examples: `Oak Staff`, `Yew Staff`
  - attuned examples: `Fire Oak Staff`, `Air Yew Staff`
- Enchanted and unenchanted versions of the same wood tier should share the same
  offense; attunement only grants the free-rune effect.
- Magic offense should be split between staff quality and spell tier:
  - player skill plus staff determine offense potential / high-end roll quality
  - spell tier determines max-hit cap
  - the current spell bands are mind, chaos, death, and blood
- Magic does not use weapon speed as its main style identity.
- Magic should instead skew toward high-roll consistency / "accuracy" while
  still respecting style defenses.
- Elements should ultimately be equal on direct damage and differentiated by
  side effects rather than by separate raw-damage ladders.
- The live elemental side-effect plan is:
  - wind: bias the target's outgoing damage rolls downward by `5%` per spell
    tier across melee, ranged, and magic
  - water: reduce the target's max hit by `5%` per spell tier across melee,
    ranged, and magic
  - earth: reduce the target's attack speed by `3%` per spell tier
  - fire: reduce the target's melee, ranged, and magic defense by `3%` per
    spell tier
- Magic-armor planning is deferred to the goals list under the wider
  `Crafting` + `Enchanting` split.

## Balance Note

- Full caster-gear balance is intentionally deferred until the migration is in
  place end to end.
- While this transition is underway, prefer tuning through player damage
  output, spell shaping, and item-offense baselines instead of repeatedly
  weakening partial robe/staff implementations mid-migration.

## Existing Item Reality

Current stock/live magic weapons are narrow and uneven:

- staves exist, but the current ladder is mixed between plain staves,
  elemental staves, battlestaves, enchanted battlestaves, and god staves
- the old orb and battlestaff paths still exist in code/runtime and must be
  explicitly retired or redirected
- spell damage now follows the first live
  `staff offense + spell cap` split model

That means the next pass is no longer baseline redesign. It is now cleanup,
visual follow-through, broader balance, and legacy-content retirement.

For robes specifically, the current live baseline is:

- old colored wizard gear is retired from the active progression path
- new robe crafting starts from plain white wool garments
- later enchantment and later dye/customization should build on that new line
  instead of reviving the retired wizard-color path

## Current Live State

- Basic wool hat, robe top, robe skirt, and cape crafting are live through
  `Crafting`
- Wool hat, top, and skirt now have the permanent baseline magic-defense floor:
  - hat `1`
  - top `4`
  - skirt `3`
- Wool cape is live as a cosmetic dyeable piece with no defense
- Dye recolors are live for the wool hat, top, skirt, and cape line
- Altar-bound robe outputs are live for every current altar tier
- The robe-enchanting pass currently provides:
  - altar-bound item outputs
  - player-tier selection at every altar family from tier `2` upward
  - selected-tier level gates
  - persisted robe tiers on the enchanted robe items themselves
- The robe-enchanting pass now also provides first-pass altar-specific robe
  runtime effects on the enchanted robe outputs
- The robe-enchanting pass now uses the selected-tier model rather than the old
  fixed altar-output assumption:
  - baseline `1/4/3` hat/top/skirt defense persists
  - the currently implemented first-pass defense curve is:
    - hat: `1 + floor((tier - 1) / 2)`
    - top: `4 + (tier - 1) * 3`
    - skirt: `3 + (tier - 1) * 2`
  - altar/rune family provides a static defensive or magic-oriented effect
    regardless of chosen tier

## Remaining Work

- tighten or rebalance the first-pass robe defense growth curve if later combat
  testing shows the provisional values are off
- continue retiring or isolating any remaining legacy colored wizard-gear
  references that still matter for progression instead of compatibility
- revisit visual cleanup once the UI/equipment presentation direction is clearer
- do the final caster-gear balance pass only after robes, staffs, spells, and
  altar identities are all in place together

## Phase 1: Staff Redesign Specification

- `[done]` 1. Lock the standard staff base ladder around wood tiers instead of
  elemental item variants.
- `[done]` 2. Choose the exact tier `1-6` wood progression to use for staves.
  - `Staff`, `Oak`, `Willow`, `Maple`, `Yew`, `Magic`
- `[done]` 3. Reclassify existing staff item defs into:
  - base wooden staff tiers
  - enchanted elemental staff outputs
  - special/non-standard legacy staves
- `[done]` 4. Remove orb-charging as an active gameplay step for
  staff progression.
- `[done]` 5. Preserve old orb rune-cost expectations by reusing those costs as
  the altar cost for elemental staff attunement.
- `[done]` 6. Lock altar staff enchanting to direct altar use on a finished
  staff.
- `[done]` 7. Lock the exact elemental identities for enchanted staves:
  - fire staff removes fire rune cost
  - water staff removes water rune cost
  - air staff removes air rune cost
  - earth staff removes earth rune cost
- `[done]` 8. Retire `Battlestaff` and `Magic Staff` from the standard ladder.
- `[done]` 9. Expand staff attunement into one-rune standard outputs for every altar.
- `[done]` 10. Treat battlestaffs as retired standard content, not as a higher
  wood-tier family.
- `[done]` 11. Treat god staves, Iban staff, Armadyl staff, and dramen staff as
  separate above-standard or special-case items.
- `[done]` 12. Decide exactly how staff tier contributes to offense quality and
  equip progression.
  - tier controls staff offense
  - tier controls wield progression
  - attunement does not change offense
- `[done]` 13. Define the first pass of the new magic combat split:
  - player skill plus staff determine max offense potential
  - spell tier determines max-hit cap
  - low-tier spells clamp damage but preserve high-roll bias
  - style defenses must still mitigate cleanly
  - first live cap model:
    - mind `40%`
    - chaos `60%`
    - death `80%`
    - blood `100%`

## Phase 2: Staff Art And Data Conversion

- `[doing]` 14. Audit which existing staff sprites can be palette-swapped cleanly
  for wood-tier identity.
- `[todo]` 15. Define the target color language for each wood tier so staff
  tiers read clearly in-game.
- `[done]` 16. Define the target color language for each elemental bulb/focus so
  the active rune-preservation element remains obvious.
- `[done]` 17. Decide whether plain wood-tier staffs and enchanted elemental
  staffs should have separate item defs or use shared defs with remapped art.
- `[done]` 18. Build the first data mapping table:
  - base staff item ids
  - enchanted elemental outputs
  - rune costs
  - Enchanting level gates
  - wield requirements
- `[done]` 19. Mark obsolete orb/staff outputs that should no longer enter
  active play.

## Phase 3: Staff Runtime Migration

- `[done]` 20. Move elemental rune-cost removal for staffs fully onto the new
  altar-attuned staff outputs.
- `[done]` 21. Remove or neutralize the old battlestaff/orb upgrade paths that
  no longer match the redesign.
- `[done]` 22. Retune `magicOffense` values for the new standard tier `1-6`
  staff ladder.
- `[done]` 22a. Align staff offense weights with the non-black longsword ladder:
  - `8`, `16`, `24`, `32`, `40`, `48`
- `[done]` 23. Revisit equip requirements so the new staff ladder lines up with
  the current combat model.
- `[done]` 24. Audit spell-cast logic for any remaining assumptions about legacy
  elemental staff item ids.
- `[done]` 25. Implement the first pass of the spell-tier damage-cap model for:
  - mind
  - chaos
  - death
  - blood

## Phase 4: Testing And Validation

- `[done]` 26. Add data validation for the new staff ladder:
  - complete tier coverage
  - consistent rune-cost mapping
  - valid altar/element mapping
- `[done]` 27. Add balance fixtures for representative magic gear tiers against
  sample NPCs.
- `[done]` 28. Add spell-casting validation that verifies enchanted staffs remove
  the correct rune cost.
- `[done]` 29. Add fixtures for the new spell-tier damage-cap model so capped
  spells still show the intended high-roll bias.
  - current benchmark includes tier-6 air spell tiers against Lesser Demon
- `[done]` 29a. Add source validation for the first fire burn implementation so
  Enchanting regressions are caught by automation.

## Phase 4a: Elemental Differentiators

- `[done]` 29b. Lock the simplified side-effect identities for all four
  elements.
- `[done]` 29c. Implement wind as an outgoing low-roll bias debuff across
  melee, ranged, and magic.
- `[done]` 29d. Implement water as an outgoing max-hit debuff across melee,
  ranged, and magic.
- `[done]` 29e. Implement earth as an attack-speed debuff.
- `[done]` 29f. Implement fire as an all-style defense debuff.
- `[done]` 29g. Lock the live per-tier values:
  - wind: `5 / 10 / 15 / 20%` low-roll bias chance
  - water: `5 / 10 / 15 / 20%` max-hit reduction
  - earth: `3 / 6 / 9 / 12%` attack-speed reduction
  - fire: `3 / 6 / 9 / 12%` defense reduction
  - duration: `24` game ticks, refreshed on reapply and preserving the stronger
    tier
- `[todo]` 29h. Revisit whether any later multi-target or splash behavior still
  belongs on wind once the rest of the spellbook is stable.

## Current Benchmarks

- Tier-6 staff benchmark now matches the standard tier ladder weights used in
  ranged and longswords.
- Current tier-6 air-staff vs Lesser Demon throughput:
  - mind: `4.69`
  - chaos: `6.19`
  - death: `7.12`
  - blood: `7.50`
- Current comparison points:
  - rune longsword: `6.50`
  - magic longbow: `8.00`

## Phase 5: Cleanup And Legacy Removal

- `[doing]` 30. Remove or neutralize obsolete orb items, spell entries, and staff
  upgrade paths that no longer belong in active play.
- `[todo]` 31. Document any magic items intentionally left dormant for
  compatibility only.
- `[todo]` 32. Update player-facing explanation surfaces once the new staff
  model is live.

## Recommended Next Order

1. Decide whether the current `40/60/80/100` spell caps are final or need a
   second balance pass after more NPC benchmarks.
2. Revisit air splash once the larger combat interaction rework is complete and
   the necessary targeting / multi-attacker hooks exist.
3. Continue retiring obsolete orb items and dormant spell entries that are now
   compatibility-only instead of part of active play.
4. Return later for palette-swap visual cleanup on the wood portions of
   elemental staffs.

## Change Log

- `2026-03-20` Kept the visible skill identity as `Enchanting`, retired the old
  battlestaff combine path, and added the first standard wood-tier staff ladder
  with direct altar attunement.
- `2026-03-20` Moved elemental rune-negation off the old hard-coded battlestaff
  list and onto the new attuned staff outputs.
- `2026-03-20` Implemented the first live magic damage-cap model with
  `40/60/80/100` caps for mind/chaos/death/blood spell tiers.
- `2026-03-20` Retuned staff offense to match the non-black longsword ladder
  and added Lesser Demon throughput fixtures for tier-6 spell tiers.
- `2026-03-20` Retired the reachable battlestaff/orb upgrade paths in shops,
  quest dialogue, and spell handling, and added direct Enchanting validation
  for staff-based rune negation.
- `2026-03-28` Replaced the earlier burn/root prototype with the simplified
  live elemental debuff model:
  - wind = outgoing low-roll bias
  - water = outgoing max-hit reduction
  - earth = attack-speed reduction
  - fire = all-style defense reduction
