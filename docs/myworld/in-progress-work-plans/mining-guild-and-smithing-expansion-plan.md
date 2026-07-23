# Mining Guild And Smithing Expansion Plan

Status: in progress
Owner: Justin
Branch: no active branch; assign the next checklist item to a fresh topic branch in a neutral AI slot
Related submissions:
Related issues or PRs:

## Summary

Expand the underground Mining Guild with a new high-level gated area and use
that space to introduce a new alloy-making ore. The goal is to extend Mining
and Smithing beyond the current rune/dragon endpoint while preserving a
RuneScape Classic-feeling progression.

The current working direction is:

- Rune equipment remains the level `70` Smithing tier.
- Dragon equipment remains the level `80` tier if dragon armor is counted as
  a normal metal tier.
- A new purified rune tier fills the level `90` space.
- A new dragon-rune hybrid tier becomes the level `99` endpoint.
- Dragon sulfur access now aligns with dragon-tier progression at `80` Mining
  instead of being held until level `90`.

The new ore is named `Dragon sulfur`. Its role is similar to coal: it is not
primarily a metal with its own equipment line, but a required alloy ingredient
for higher-tier bars.

The expanded Mining Guild should also introduce the first purpose-built custom
guardian NPC for new world content: the `Elder Green Dragon`. This enemy should
guard the new sulfur rocks and should be built against the modern MyWorld
combat model instead of relying only on legacy attack, strength, and defense
shortcuts.

## Goal

Give late-game Mining and Smithing a clear progression path after rune without
turning every new rock into a standalone metal tier.

The intended player experience is:

- High-level miners gain a new Mining Guild destination.
- Smithing gains meaningful post-rune goals.
- Rune remains recognizable as the classic high-tier metal.
- Dragon and rune can combine into a final prestige alloy instead of replacing
  the classic ladder with unrelated materials.

## Scope

This plan includes:

- A new gated underground Mining Guild area.
- A Mining requirement of `80` for entering the expansion.
- A new mineable alloy ingredient named `Dragon sulfur`.
- New rock scenery and depleted-rock behavior for the new ore.
- A new purified rune bar, made from runite and the new ore at the repaired
  lava forge.
- A new purified rune equipment tier around Smithing level `90`.
- A new dragon-rune alloy bar or recipe path around Smithing level `99`.
- Dragon-rune equipment as the highest smithable metal tier.
- Skill-guide, item-definition, production UI, and map/object documentation
  updates.
- A new `Elder Green Dragon` guarding the Dragon sulfur area.
- Migration of the special King Black Dragon hide/leather/armor identity to
  the Elder Green Dragon line, while King Black Dragon returns to black dragon
  hide.
- NPC combat-definition expansion so new NPCs can define melee, ranged, and
  magic offense and defense intentionally.
- Summoning interaction for the Elder Green Dragon to scare away the Mischief
  Imp summon.

## Non-Goals

This plan does not yet define:

- Final names for the bars or equipment tiers.
- Exact stats, requirements, XP, or market prices.
- Exact Mining Guild terrain layout.
- Exact number or respawn timing of new rocks.
- Whether dragon metal enters the future dragon-rune recipe as bars, drops,
  salvage, fragments, or a new smelted component.
- Whether purified rune is required as an intermediate for dragon-rune gear or
  whether dragon-rune bars combine runite, dragon metal, and the new ore
  directly.
- Full rebalance of every legacy NPC into explicit modern combat stats.
- Final Elder Green Dragon drop table tuning after live combat/drop testing.

Do not implement final item IDs or balance values until those decisions are
confirmed.

## Affected Systems

- Map data: Mining Guild underground expansion and gate placement.
- Scenery/object data: new ore rocks, depleted rocks, and object IDs.
- Mining: rock requirements, yields, depletion, respawn timing, XP, and guide
  text.
- Smithing: bar recipes, equipment recipes, level gates, XP, production UI, and
  guide text.
- Item definitions: ore, bars, equipment, prices, examine text, stackability,
  and bank filters.
- Equipment balance: combat stats, armor budgets, requirements, and repair or
  rarity assumptions if dragon metal is used.
- NPC definitions: explicit offense/defense stats for modern custom NPCs.
- NPC combat behavior: attack style, projectile power, magic power, and typed
  defense handling.
- Summoning: special-case Mischief Imp scare-away behavior for the Elder Green
  Dragon.
- Drops and leatherworking: former King Black Dragon hide/leather/armor naming,
  colors, source NPC, and guide text.
- Release notes: this is player-facing content once implemented.

## Player-Facing Changes

Players should eventually see:

- A new high-level section inside the underground Mining Guild.
- A new gate requiring `80` Mining.
- A new `Dragon sulfur` ore rock found only inside the expanded guild area.
- A new alloy ingredient with a dragon-adjacent identity.
- An `Elder Green Dragon` guarding the sulfur rocks.
- King Black Dragon dropping black dragon hide, while Elder Green Dragon takes
  over the former special hide/leather/armor tier under its own name.
- Level `90` purified rune equipment.
- Level `99` dragon-rune equipment as the top smithable tier.

## Proposed Progression

| Tier | Working level | Working material | Role |
| ---: | ---: | --- | --- |
| 70 | 70 | Runite | Existing classic high-tier metal. |
| 80 | 80 | Dragon | Existing dragon armor/item tier, if treated as smithing progression. |
| 90 | 90 | Purified rune / pure rune / true rune | New post-rune tier made by refining rune with the new ore. |
| 99 | 99 | Dragon rune | Final hybrid tier combining dragon metal, rune, and the new alloy ingredient. |

## Naming Direction

The new ore is named `Dragon sulfur`. It should read as an alloy reagent rather
than a standalone metal.

Possible purified rune tier names:

- `Purified rune`
- `Pure rune`
- `True rune`
- `Refined rune`

Possible dragon-rune tier names:

- `Dragon rune`
- `Draconic rune`
- `Dragon-forged rune`

Avoid finalizing names until the full item set can be reviewed in-game. The
names should read clearly in item lists, smithing menus, guide text, and
equipment examines.

## Recipe Direction

The new ore should behave like coal in the recipe structure:

- It is mined as a resource.
- It is consumed while creating stronger bars.
- It does not need a complete standalone armor and weapon family.

Likely recipe shapes to evaluate:

- Repaired lava forge: `Dragon Metal Scrap + 6 Dragon sulfur -> Dragon bar`
- Repaired lava forge: `Runite bar + 14 Dragon sulfur -> Purified Rune Bar`
- Future dragon-rune option:
  `Dragon metal + Runite bar + Dragon sulfur -> Dragon rune bar`
- Future dragon-rune option:
  `Dragon metal + Purified rune bar + Dragon sulfur -> Dragon rune bar`

The final recipe should account for:

- Rune ore/bar availability.
- Dragon material availability and rarity.
- Whether level `99` should consume level `90` bars as an intermediate sink.
- Whether the new ore should be the main bottleneck or only one bottleneck
  among several late-game ingredients.

## Mining Guild Expansion Direction

The high-level gate should be physically readable and clearly associated with
Mining Guild mastery.

Initial direction:

- Expand the existing underground Mining Guild rather than creating an
  unrelated mining location.
- Gate the new section with a Mining level requirement of `80`.
- Put the new ore only beyond this gate.
- Keep the area underground and mining-focused.
- Avoid placing the new rocks anywhere else until the economy and balance are
  tested.

The gated area should include enough rocks to be worth visiting, but scarcity
should remain part of the tier identity. It should not feel like ordinary coal
or mithril density.

The elite gate at `268,3401` is intentionally owned by the explicit boundary
loc entry, not by an embedded landscape wall byte. The sector tile
`h3x53y48:1385` should keep its wall fields clear so the old east-wall stub
does not protrude beside the custom door after map-editor imports.

## Elder Green Dragon Direction

The Elder Green Dragon is the first new NPC being created specifically for this
expansion, so it should set the standard for future custom monsters.

Current direction:

- Rename the prototype `Elder Dragon` to `Elder Green Dragon`.
- Keep the doubled green dragon sprite scale if it remains readable in-client.
- Place the NPC as the primary guardian of the new Dragon sulfur area.
- Make it stronger than King Black Dragon overall, using King Black Dragon as
  the baseline rather than a ceiling.
- Give it real melee, ranged, and magic combat values instead of deriving all
  special attacks from attack or strength.
- Give it real melee, ranged, and magic defenses.
- Keep normal dragonfire behavior, then layer Elder Green Dragon-only boss
  attacks on top: a ranged fireball AOE, a non-stacking burn debuff, and a
  melee sweep around the dragon.
- Add a special interaction where the Elder Green Dragon scares away a player's
  Mischief Imp summon.
- Move the current special King Black Dragon hide/leather/armor identity onto
  the Elder Green Dragon line.
- Keep existing item IDs for the former King Black Dragon hide/leather/armor
  items where possible so existing player data migrates through names, colors,
  examines, and source changes instead of item deletion.

King Black Dragon currently has:

- Combat level `245`.
- Attack `250`.
- MyWorld strength override `224`.
- Hits `240`.
- Defense `240`.
- Effective MyWorld defenses of melee `240`, ranged `180`, and magic `240`
  through defense multipliers.

Elder Green Dragon should be balanced against that profile, but its final
numbers should be set after the NPC combat stat expansion is in place.

Initial Elder Green Dragon combat profile:

- Combat level `275`.
- Attack `275`.
- Strength `250`.
- Hits `280`.
- Defense `265`.
- Explicit offense values of melee `250`, ranged `235`, and magic `270`.
- Explicit defense values of melee `265`, ranged `210`, and magic `265`.

This keeps it clearly above King Black Dragon without making it a completely
separate power class. The ranged value is defined now even though the current
dragon attack profile is melee plus magic, so future mixed attacks can use the
same player-style power/defense contract without another definition migration.

Elder Green Dragon special attacks:

- Normal dragonfire still applies through existing dragon combat scripts.
- Fireshot is a large-radius player-only AOE that uses the `fireball`
  projectile and the `elder-dragon-fireshot` on-player effect.
- Burn is a large-radius player-only AOE that uses the `fireball` projectile
  and the `elder-dragon-burn` on-player effect. It behaves like a short debuff:
  it does not stack, can be reapplied to refresh its duration, and deals
  alternate/yellow hitsplats for `1-3` damage over roughly `5` seconds.
- Melee sweep is its own melee special attack, not a rider on every melee hit.
  When selected, it replaces the normal single-target melee attack and can hit
  player targets within a `2` tile radius. The sweep should not hit NPCs or
  summons.

## NPC Combat Structure Direction

NPC defense is already partly modernized. NPC definitions support explicit
`meleeDefense`, `rangedDefense`, and `magicDefense`. If those are absent, the
server derives them from legacy `defense` using per-style multipliers or
divisors.

NPC offense was previously shortcut-based:

- Melee offense uses `strength`.
- Ranged offense for projectile-capable NPCs derives from
  `max(attack, strength)`.
- Magic offense for magic-capable NPCs derives from `max(attack, strength)`.
- The legacy `ranged` field is effectively only a boolean and should not be
  treated as a modern ranged power value.

NPC definitions now support explicit player-style power fields while keeping
legacy fallback behavior:

- `meleeOffense`, optional; falls back to `strength`.
- `rangedOffense`, optional; falls back to the existing attack/strength
  shortcut when an NPC profile uses ranged projectiles.
- `magicOffense`, optional; falls back to the existing attack/strength shortcut
  when an NPC profile uses magic projectiles.

Together with `meleeDefense`, `rangedDefense`, and `magicDefense`, this gives
new NPCs the same broad combat contract as players: a typed power value rolls
against the matching typed defense value. Old NPCs keep their current behavior
through fallback derivation, while new NPCs, starting with Elder Green Dragon,
can be built with intentional combat stats. It also gives future content a
cleaner definition format and avoids stacking more special cases into
`NpcAttackStyleProfile`.

Follow-up combat cleanup to evaluate:

- Ensure ranged accuracy and mitigation both respect ranged defense instead of
  falling through legacy melee-defense accuracy paths.
- Consider making NPC attack style explicit in definitions later, rather than
  inferring from name/id heuristics.
- Keep legacy derivation paths documented as compatibility fallbacks, not as
  the preferred path for new remaster content.

## Implementation Checklist

- [x] Confirm final Mining gate level.
- [x] Confirm final ore name.
- [x] Add initial `Dragon sulfur` resource item using the ash item sprite with
      an orange/red mask.
- [x] Confirm purified rune tier name as `Purified Rune Bar` and `Exalted Rune`
      equipment.
- [ ] Confirm dragon-rune tier name.
- [x] Confirm purified rune bar recipe: `1 rune bar` and `14 dragon sulfur`.
- [ ] Confirm whether dragon-rune requires purified rune as an intermediate.
- [x] Add ore and purified rune bar item definitions.
- [x] Add rock scenery definitions and object IDs.
- [ ] Add depleted-rock behavior if a new rock model or object type is needed.
- [x] Add Mining Guild terrain expansion.
- [x] Add Mining Guild level-gate interaction.
- [x] Keep the elite gate's protruding landscape east-wall stub clear while
      preserving the clickable boundary door at `268,3401`.
- [ ] Place new ore rocks only inside the new gated area.
- [x] Add Mining requirements, XP, respawn timing, and depletion rules.
- [ ] Move purified rune Smithing bar recipe to the repaired lava forge UI.
- [x] Add purified rune equipment recipes.
- [ ] Add dragon-rune equipment recipes.
- [x] Add Exalted Rune equipment stats, requirements, prices, and examine text.
- [x] Rename prototype `Elder Dragon` to `Elder Green Dragon`.
- [x] Add explicit NPC offense fields for modern melee, ranged, and magic
      power with legacy fallbacks.
- [x] Update NPC definition loading, patching, and override tests for explicit
      offense fields.
- [x] Set Elder Green Dragon's final combat stats after the explicit offense
      fields exist.
- [x] Add Elder Green Dragon-only fireshot AOE, burn debuff AOE, and melee
      sweep hooks.
- [ ] Place Elder Green Dragon spawns near the Dragon sulfur rocks.
- [x] Add Elder Green Dragon Mischief Imp scare-away behavior.
- [x] Move special King Black Dragon hide/leather/armor naming, colors,
      examines, and source drops to Elder Green Dragon.
- [x] Change King Black Dragon's guaranteed hide drop to black dragon hide.
- [x] Add the first Elder Green Dragon normal loot table with common,
      uncommon, and rare buckets.
- [ ] Update smithing and mining skill guides.
- [x] Update `docs/myworld/info/object-ids.md` with new ore rock IDs.
- [ ] Update release notes when the content is player-facing.

## Testing Checklist

- [ ] Verify players below the gate requirement cannot enter.
- [ ] Verify players at or above the gate requirement can enter.
- [ ] Verify the new rocks only exist in the intended Mining Guild expansion.
- [ ] Verify mining level checks, ore yield, depletion, and respawn timing.
- [ ] Verify bar recipes consume the correct ingredients.
- [ ] Verify Smithing menu flow matches existing two-screen production
      behavior.
- [ ] Verify level requirements, XP, and failure/success behavior.
- [ ] Verify equipment stats and requirements in worn item UI.
- [ ] Verify Elder Green Dragon attack behavior uses intended melee/ranged/magic
      power values.
- [ ] Verify Elder Green Dragon fireshot, burn, and melee sweep only hit
      players and do not hit NPCs or summons.
- [ ] Verify Elder Green Dragon burn does not stack and can be refreshed by a
      later burn application.
- [ ] Verify Elder Green Dragon defense behavior uses intended
      melee/ranged/magic defense values.
- [x] Verify Mischief Imp is dismissed or scared away by Elder Green Dragon and
      that other summons are unaffected unless explicitly intended.
- [x] Verify King Black Dragon drops black dragon hide after the migration.
- [x] Verify Elder Green Dragon drops the renamed special hide.
- [x] Verify former King Black Dragon hide/leather/armor item IDs retain player
      data while showing the new Elder Green Dragon identity.
- [ ] Verify Elder Green Dragon normal loot rolls from the intended common,
      uncommon, and rare buckets.
- [ ] Verify guide text reflects the final recipes and levels.
- [ ] Run item-definition, production, guide, and plugin reference tests.
- [ ] Manually test the area in-client for camera, clickability, and map
      readability.

## Decisions

- The new high-level mining resource should be an alloy ingredient, not a full
  standalone metal line.
- The new resource should be exclusive to the expanded Mining Guild gated area
  at first, with the gate set to `80` Mining.
- The desired late-game Smithing ladder is rune at `70`, dragon around `80`,
  purified rune around `90`, and dragon rune at `99`.
- Purified rune bar production should move to the repaired lava forge, not a
  regular furnace.
- New custom boss-style NPCs should use explicit modern combat stats instead of
  relying on attack/strength shortcuts for ranged and magic power.
- Existing legacy NPCs should keep fallback stat derivation until deliberately
  rebalanced.
- Elder Green Dragon inherits the special hide/leather/armor identity
  currently attached to King Black Dragon.
- Elder Green Dragon's first normal loot table uses `96/24/8` weights for
  common, uncommon, and rare buckets. The rare bucket is a true rare nested
  table so private-loot damage contribution can scale access to that bucket.
- Elder Green Dragon common drops include the base ebony staff plus every
  non-element altar ebony staff variant: mind, body, cosmic, chaos, nature,
  law, death, blood rune, soul, and life.
- Elder Green Dragon uncommon drops include a blood bow and a blood staff
  enchanted at the Death altar. Its rare bucket is the intended normal-table
  source for the current dragon weapon set.

## Open Questions

- Should the level `90` tier be called `Purified rune`, `Pure rune`,
  `True rune`, or something else?
- Should the level `99` tier be called `Dragon rune`, `Draconic rune`, or
  something else?
- Does dragon-rune Smithing consume dragon bars, dragon equipment, dragon
  fragments, or another dragon-metal source?
- Should level `99` dragon-rune bars require purified rune bars as an
  intermediate?
- Should the new ore produce any secondary byproducts, or stay a simple alloy
  reagent?
- How rare should the new ore be compared with runite?
- Should the new Mining Guild section include any enemies, hazards, or only
  high-level resource access?
- Do the initial Elder Green Dragon stats need adjustment after live combat
  testing?
- Should Elder Green Dragon use melee plus magic only like current dragons, or
  should it also gain a distinct ranged-style attack?
- Should NPC attack style become a definition field as part of this pass, or
  should that wait until after explicit offense fields are stable?
- What final color palette should Elder Green Dragon hide/leather/armor use?

## Completion Criteria

This plan can move to completed when:

- The Mining Guild expansion is live and gated correctly.
- The new ore is mineable only in the intended area.
- Elder Green Dragon is named, placed, balanced, and guarding the sulfur rocks.
- New NPC combat stats support explicit melee/ranged/magic offense and defense
  for modern content while retaining legacy fallbacks.
- King Black Dragon and Elder Green Dragon hide/leather/armor sources and names
  match the intended identity split.
- Purified rune production is available at the repaired lava forge, and
  dragon-rune recipes are implemented and documented when that tier is ready.
- Mining and Smithing guides match the live mechanics.
- Item definitions, object references, production flows, and guide tests pass.
- Manual client testing confirms the area is readable and the new production
  flow works end to end.
