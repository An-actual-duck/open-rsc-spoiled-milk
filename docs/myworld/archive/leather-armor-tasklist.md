# Leather Armor Tasklist

This tracker covers the new MyWorld leather-armor direction.

It intentionally replaces the earlier assumption that broader ranged armor
should live under a future `Fletchery` armor path.

## Status Key

- `[todo]` not started
- `[doing]` in progress
- `[done]` completed
- `[blocked]` waiting on a design/system dependency

## Current Focus

- `[doing]` Lock the leather-armor visual language and naming direction.
- `[doing]` Replace the old fixed hide ladder with a source-creature-derived
  armor model.
- `[todo]` Reassign leather and cloth armor production ownership into
  `Crafting`.

## Locked Direction

- Armor lines should not be typed primarily by combat style if they are not
  granting offense bonuses.
- The intended material families are currently:
  - metal armor
  - leather armor
  - cloth armor
- Each armor family should stand on its own merits rather than being named as a
  combat-style lane.
- Leather armor production should belong to `Crafting`.
- Cloth armor production should also move toward `Crafting`.
- Any older documentation that assumes leather armor belongs to `Fletchery`
  should be treated as outdated planning.
- Hide-derived armor should be sourced from valid creature materials first, not
  from a rigid one-line progression ladder.
- Relative armor tier should start from the source creature's level band, but
  can be intentionally overridden when the authored material progression calls
  for it.
- Defense allocation should be inferred from the source creature or source
  family rather than assigned as a generic fixed profile.
- `Carapace` is a material naming convention for insect / arachnid /
  scorpion-like sources within the same derived-material system, not a separate
  mechanics framework.
- Tanning should become an actual `Crafting` process rather than a pure
  exchange with an NPC.
- Material tier should drive both the required `Crafting` level and the total
  defense budget available to the resulting armor pieces.
- The same ingredient-budget defense model should later be adopted by the metal
  armor line as part of the wider blacksmithing rebalance.

## Leather Armor Visual / Naming Direction

- Reuse `Medium helm` visuals for the leather head slot.
  - target name: `Coif`
- Reuse `Chainmail` visuals for the leather chest slot.
  - target name: `Cuirass`
- Reuse `Protective trousers` visuals for the leg slot.
  - target name: `Chaps`
- Reuse existing leather gloves visuals with palette swaps.
  - target name: `Gloves`
- Reuse existing leather boots visuals with palette swaps.
  - target name: `Boots`
- Current slot-level visual recommendation:
  - head: repurpose `Medium helm`
  - chest: repurpose `Chainmail`
  - legs: prefer `Chain Mail Legs` as the strongest proven recolor family;
    `Protective trousers` remain a fallback if the silhouette is preferred
  - hands: keep `Leather Gloves` as the base silhouette
  - feet: keep the existing `Boots` family as the base silhouette
- `Chain Mail Legs` are currently the strongest proven leg-slot palette-swap
  family and should be preferred over weaker one-off options unless visual
  testing says otherwise.
- `Leather Gloves` have limited but real recolor precedent and are currently the
  recommended hide/leather hand-slot base.
- The metal `gauntlets` family should not be reused as the standard hide/leather
  hand-slot line unless a separate heavy-hand-armor branch is designed later.
- The existing `Boots` family already has multiple recolor examples and is the
  recommended base for leather/hide boots.
- Hand/foot audit notes:
  - there is no stock `greaves` family in the current item set
  - the existing `Boots` family is the best current foot-slot base because it
    already has multiple recolor/use variants
  - `Leather Gloves` remain the best current hand-slot base
  - the `gauntlets` family should be treated as a separate heavy/special-use
    handwear branch, not as the standard hide/leather gloves line
  - if the metal armor line wants a dedicated foot-slot counterpart before new
    art exists, reuse the current `Boots` family as the temporary `Greaves` /
    `Sabatons` visual line
- Remove the active metal-armor role for the old medium-helm / chainmail
  variant branch once the leather replacements are ready.
- Remove normal acquisition paths for `medium helm` and `chainmail` so those
  visuals can be repurposed cleanly into leather armor.
- Exceptions:
  - `Dragon medium helm` remains as-is
  - `Dragon scalemail` remains distinct and should not be treated as part of
    the retired chainmail line
- Build the leather family primarily through sprite reuse plus palette swaps.
- Future hide tiers should preferably share the same silhouette set and read as
  higher/lower materials through color treatment rather than entirely new
  shapes.
- Hide colors should stay close to the source creature's visual identity.
- Processed leather should usually be a darker, richer, more finished version
  of the raw hide rather than a completely different hue.
- Near-black hides should not use flat pure black; lift them slightly and bias
  toward purple or blue where needed so sprite shading remains readable.

## Leather Armor Production Direction

- Keep leather and hide processing under `Crafting`.
- Replace the current NPC-exchange-only tannery flow with a real hide-processing
  action that grants `Crafting` XP.
- Extend the current leather workflow rather than creating a separate armor-only
  production skill.
- Treat this as the first concrete move away from the older incorrect idea that
  leather armor is a ranged-armor lane.
- Treat hide or carapace processing as the first material-conversion step, then
  armor assembly as the second.

## Tanning / Processing Direction

- The target loop is:
  - raw hide or raw carapace source
  - `Crafting` processing step at an in-world object
  - processed leather / cured carapace material
  - armor-piece crafting
- The old tannery NPC exchange should be treated as placeholder behavior, not
  the intended final `myworld` loop.
- The `Tanner` NPC tanning option should be removed once object-based tanning is
  in place so hide processing is exclusively skill-driven.
- Existing object-def candidates worth testing as the processing station are:
  - `Fur Stall` in [GameObjectDef.xml](/home/justin/Core-Framework/server/conf/server/defs/GameObjectDef.xml#L3567)
  - `Frame` in [GameObjectDef.xml](/home/justin/Core-Framework/server/conf/server/defs/GameObjectDef.xml#L6515)
- Current tentative implementation choice:
  - use `Frame` as the first tanning-rack stand-in
- No obvious built-in object named as a tanning rack currently exists in the
  stock object defs, so `Frame` is currently the preferred reuse target unless
  a dedicated new object is added later.

## Current Live State

- Standard leather crafting already exists through `Crafting`.
- MyWorld already supports custom leather handling and extra leather pieces.
- No live armor-production implementation was found under a `Fletchery`
  skill path; the ownership assumption currently appears only in planning docs.

## Phase 1: Doc And Ownership Cleanup

- `[todo]` Update higher-level planning docs so leather armor is owned by
  `Crafting`, not `Fletchery`.
- `[todo]` Review whether cloth-armor planning should also move cleanly into
  `Crafting` or remain a separate future material branch.
- `[todo]` Review `Magecraft` / `Enchanting` naming assumptions later once the
  armor-production ownership split is clearer.

## Phase 2: Leather Set Migration

- `[todo]` Rename the current leather set into the new slot language:
  - `Coif`
  - `Cuirass`
  - `Chaps`
  - `Gloves`
  - `Boots`
- `[todo]` Decide whether the names should carry their material tier explicitly
  on every item, for example:
  - `Animal-hide coif`
  - `Wolf-hide cuirass`
- `[todo]` Map each target leather slot to its source sprite / animation.
- `[todo]` Treat the current recommended source silhouettes as:
  - `Coif`: `Medium helm`
  - `Cuirass`: `Chainmail`
  - `Chaps`: `Chain Mail Legs` first, `Protective trousers` as fallback
  - `Gloves`: `Leather Gloves`
  - `Boots`: `Boots`
- `[todo]` Remove or retire the old medium-helm and chainmail metal variants
  from active progression once replacement items are in place.
- `[todo]` Remove `chain mail legs` from smithing and normal acquisition once
  their visuals are repurposed into the leather set.
- `[todo]` Remove `square shield` from the normal smithing/drop/shop branch if
  the stronger shield equivalents remain the intended standard path.
- `[todo]` Remove `medium helm` and `chainmail` from drop tables and crafting
  outputs so players cannot normally acquire them.
- `[todo]` Verify no shop, quest, or compatibility source still hands out the
  retired non-dragon `medium helm` / `chainmail` items.
- `[todo]` Remove `chain mail legs` from any remaining shops and compatibility
  sources once the leather-leg remap is live.
- `[todo]` Review whether the old custom leather-side alternates should be
  retired once the unified hide set exists:
  - `Leather chaps`
  - `Leather top`
  - `Leather skirt`
  - `Leather vest`
- `[todo]` If the metal line gains a named foot-slot branch before bespoke art
  exists, use the current `Boots` silhouette temporarily rather than blocking
  the system on new assets.
- `[todo]` Preserve the exception handling for `Dragon medium helm` and keep
  `Dragon scalemail` clearly separated from the retired chainmail branch.
- `[todo]` Decide how existing leather armor / leather top / leather chaps /
  leather skirt content maps into the new unified set.
- `[todo]` Decide whether the skirt remains a separate side piece or is retired
  from the standard progression set.

## Specialty Armor Cleanup Notes

- Items with strong reasons to keep as special-purpose equipment:
  - `Steel gauntlets` and the Family Crest gauntlet variants
    - they have active quest/skilling hooks and retrieval logic
  - `Klank's gauntlets`
    - active quest/obstacle usage
  - `Ice Gloves`
    - active utility behavior and compatibility with glove-gated content
  - `Boots of lightfootedness`
    - distinct utility identity
  - `Desert Boots`
    - quest/clothing identity
  - `Gasmask` / plague-style protective gear
    - quest/area-gating identity
  - `Anti-dragon shield`
    - distinct gameplay role
  - `Dragon medium helm`
    - explicit exception to medium-helm retirement
  - `Dragon scalemail` line
    - distinct dragon-scale identity; do not collapse into retired chainmail
- Good current candidates for retirement or visual repurposing once the new
  leather line is live:
  - non-dragon `medium helm`
  - non-dragon `chainmail`
  - `chain mail legs`
  - `square shield`
  - `Leather chaps` if `chain mail legs` becomes the final `Chaps` silhouette
  - `Leather top` if `chainmail` becomes the final `Cuirass` silhouette
  - `Leather skirt` if skirts are not retained as a side branch
  - `Leather vest` if it no longer has a meaningful niche after the unified
    leather set is defined
- Items that should be reviewed case-by-case rather than retired blindly:
  - `Protective jacket`
  - `Protective trousers`
  - `khazard Helmet`
  - `khazard chainmail`
  - faction, quest, or costume armor with named identity
- Future armor-visual cleanup worth planning separately:
  - the old `platebody` / `plate mail top` split exists largely to support
    body-type visual differences through separate items and NPC trade paths
  - a cleaner future direction is to let body type drive the displayed visual
    automatically rather than requiring players to swap between male/female
    plate variants as separate items

## Phase 3: Source-Creature Material Model

- `[doing]` Derive armor strength from source-creature level bands rather than
  from a rigid fixed ladder alone.
- `[doing]` Derive defense allocation from the source creature or source family
  profile rather than assigning generic hide stats.
- `[doing]` Lock the authored material-tier list for hide- and carapace-derived
  armor, including explicit overrides above the normal six-tier baseline.
- `[doing]` Lock the baseline defense budget for each relative tier.
- `[doing]` Lock the rule for how NPC defense ratios map into armor defense
  ratios while preserving the full tier budget.
- `[todo]` Decide when a family should stay under one material name versus
  splitting into stronger / variant names because its level spread is too wide.
- `[done]` Create a first-pass killable NPC family audit in
  `npc-family-audit.md` to support source-creature material grouping.

### Source Evaluation Questions

For each candidate NPC or source family, ask:

1. What relative standard tier does this NPC's level map to before any authored
   override?
2. Does the NPC belong to a family, and if so how wide is that family's level
   spread?
3. What defense ratios define that family?
4. Does this specific NPC differ meaningfully from the rest of its family?
5. Is this NPC a valid material source at all?
   - example: knights are not

### Armor Assignment Rules

When converting a creature source into armor:

1. Use the source NPC's level band as the starting point, then apply any
   intentionally authored material-tier override.
2. Use the source family's level spread to decide naming:
   - close family spread: one shared material name
   - wide family spread: stronger or variant names such as `Strong ogre hide`
3. Use the source's defense strengths and weaknesses to shape the armor's
   defense allocation.
4. Preserve the full defense budget for the derived tier.
   - example: if the tier budget is `20`, all `20` is allocated; only the split
     changes
5. If a family shares a name but has special variants, preserve those as
   distinct materials when their identity matters.
   - example: `Fire Giant Hide` should not collapse blindly into generic
     `Giant Hide`

### Authored Material Tier List

This is the current intended material list. Tiers above `6` are documented now,
but the first implementation pass should focus on actually building tiers `1`
through `6`.

- Tier `1`
  - `Cow hide`
  - `Goblin hide`
- Tier `2`
  - `Unicorn hide`
  - `Bear hide`
  - `Black unicorn hide`
  - `Scorpion carapace`
- Tier `3`
  - `Wolf hide`
  - `Spider carapace`
  - `Giant hide`
- Tier `4`
  - `Ogre hide`
  - `Baby dragon hide`
- Tier `5`
  - `Magic spider carapace`
  - `Moss giant hide`
  - `Ice giant hide`
- Tier `6`
  - `Demon hide`
- Tier `7`
  - `Hellhound hide`
  - `Fire giant hide`
  - `Blue dragon hide`
  - `Dragon hide`
- Tier `8`
  - `Red dragon hide`
  - `Black demon hide`
- Tier `9`
  - `Black dragon hide`
  - `Balrog hide`
- Tier `10`
  - `King Black Dragon hide`

### Current Material Source Definitions

- `Cow hide`
  - cows
- `Unicorn hide`
  - unicorns
- `Bear hide`
  - bears
- `Black unicorn hide`
  - black unicorns
- `Wolf hide`
  - all wolves except hellhounds
- `Hellhound hide`
  - hellhounds
- `Spider carapace`
  - all standard spiders except the level-`2` spider
- `Magic spider carapace`
  - the remaining higher-end spider variants
- `Scorpion carapace`
  - all scorpions
- `Goblin hide`
  - all goblins except imp
- `Giant hide`
  - giant
- `Moss giant hide`
  - moss giant
- `Ice giant hide`
  - ice giant
- `Fire giant hide`
  - fire giant
- `Ogre hide`
  - all ogres
- `Baby dragon hide`
  - baby blue dragon
- `Blue dragon hide`
  - blue dragon
- `Dragon hide`
  - dragon
- `Red dragon hide`
  - red dragon
- `Black dragon hide`
  - black dragon
- `King Black Dragon hide`
  - King Black Dragon
- `Demon hide`
  - lesser demon
  - greater demon
- `Black demon hide`
  - black demon

### Material Color Direction

- `Cow hide`
  - light tan / rawhide brown
- `Goblin hide`
  - muted lime green
- `Unicorn hide`
  - pale ivory / cream
- `Bear hide`
  - deep brown
- `Black unicorn hide`
  - dark charcoal with a slight purple-blue cast
- `Scorpion carapace`
  - sandy yellow-brown
- `Wolf hide`
  - cool grey
- `Spider carapace`
  - dark reddish brown
- `Giant hide`
  - dusty tan-grey
- `Ogre hide`
  - swampy green-brown
- `Baby dragon hide`
  - soft blue-green
- `Magic spider carapace`
  - violet-blue / arcane indigo
- `Moss giant hide`
  - moss green / olive
- `Ice giant hide`
  - pale blue-white
- `Demon hide`
  - dark red / crimson-black
- `Hellhound hide`
  - ash grey with ember-red undertone
- `Fire giant hide`
  - burnt orange / ember-red
- `Blue dragon hide`
  - strong cobalt blue
- `Dragon hide`
  - normal green
- `Red dragon hide`
  - rich red
- `Black demon hide`
  - dark charcoal-black with muted red undertones
- `Black dragon hide`
  - oil-dark grey with a subtle blue cast
- `Balrog hide`
  - lava-black kept above pure black so warm undertones remain visible
- `King Black Dragon hide`
  - very dark readable charcoal-black with highlight bias

### Leather Color Direction

- Leather should preserve the source material identity but read as processed.
- The normal shift from hide to leather is:
  - slightly darker
  - slightly richer or more saturated
  - less dusty / less raw-looking
- Carapace can follow the same rule when converted into cured material:
  - closer to a polished, finished shell than a raw dropped part
- `Balrog hide`
  - Balrog

### Carapace Naming Convention

- `Carapace` should be used as the naming convention for insect / arachnid /
  scorpion-derived materials.
- It is not intended to be mechanically separate from the hide-derived system.
- It follows the same core rules:
  - relative tier from level band
  - defense shape from source family
  - variant naming when the family spread or identity requires it
- For the current authored list, this means:
  - `Spider carapace`
  - `Magic spider carapace`
  - `Scorpion carapace`

### Ratio Conversion Rule

- NPC defense multipliers should first be converted into an integer-like ratio.
- Example:
  - multiplier set `1 : 0.5 : 0.25` becomes ratio `4 : 2 : 1`
- Defense budget should be based on ingredient weight:
  - `Helm` = `1` ingredient
  - `Boots` = `2` ingredients
  - `Gloves` = `2` ingredients
  - `Shield` = `3` ingredients
  - `Legs` = `3` ingredients
  - `Body` = `4` ingredients
- A full defensive loadout therefore represents `15` ingredient weight.
- The baseline tier rule is:
  - tier `1` = `1` defense per ingredient
  - tier `2` = `2` defense per ingredient
  - tier `3` = `3` defense per ingredient
  - and so on
- That gives these full-set baseline totals:
  - tier `1` = `15`
  - tier `2` = `30`
  - tier `3` = `45`
  - tier `4` = `60`
  - tier `5` = `75`
  - tier `6` = `90`
- Per-piece total defense budget therefore becomes:
  - `Helm` = `tier x 1`
  - `Boots` = `tier x 2`
  - `Gloves` = `tier x 2`
  - `Shield` = `tier x 3`
  - `Legs` = `tier x 3`
  - `Body` = `tier x 4`
- When assigning an armor's total defense budget:
  - distribute the total by that ratio
  - preserve the dominant defense type when rounding low-budget pieces
- Example:
  - total defense budget `20`
  - ratio `4 : 2 : 1`
  - raw split `12 / 6 / 3`
  - total is `21`, so the largest bucket loses `1`
  - final split `11 melee / 6 ranged / 3 magic`
- Low-budget example:
  - total defense budget `1`
  - ratio `4 : 2 : 1`
  - the final point should still go to melee, because melee is the dominant
    defense from the source

### Current Exclusions / Deliberate Non-Sources

- knights and similar armored humanoids are not valid hide sources
- the authored material list is intentionally documented above tier `6`, but
  implementation priority still stops at tier `6` for the first real pass

## Phase 4: Drop / Processing Rules

- `[doing]` Replace NPC-exchange-only tanning with a real object-based
  `Crafting` interaction that grants XP.
- `[todo]` Remove the tanning option from the `Tanner` NPC once the `Frame`
  tanning interaction is live.
- `[todo]` Decide whether all hide tiers use the same tanning pipeline.
- `[todo]` Decide whether the current custom `treated hide` path remains only
  for tier-1 leather or becomes the shared hide-processing baseline.
- `[todo]` Decide whether higher-tier hides need only tanning, or also a second
  treatment step before armor crafting.
- `[todo]` Decide whether all matching monsters should always drop hide, or
  whether hide rates should vary by family.
- `[todo]` Decide whether a single tier should intentionally be spread across
  multiple regions and creature families for supply stability.
- `[todo]` Decide whether leather and carapace use the same station, or whether
  chitin-like materials need a parallel curing step at the same object.

## Phase 5: Crafting Recipes And Progression

- `[todo]` Define the leather-armor slot recipe set for every standard tier.
- `[todo]` Balance tier `1` through tier `6` leather/carapace crafting around
  `Crafting` levels `1` through `40`, instead of inheriting the older
  late-crafting curve used by existing equipment lines.
- `[todo]` Use equip-ready progression as the balancing target:
  - tier `6` equipment should sit near the current level-`40` end of the
    standard wearable curve
  - lower tiers should fill the path cleanly from level `1`
- `[todo]` Use a slot-staging pattern within each material tier:
  - first unlock: tanning / curing that tier's material
  - same starting unlock: `Coif` because it uses `1` hide
  - next unlock: `Gloves` and `Boots` because they use `2` hides
  - next unlock: `Chaps` because they use `3` hides
  - final unlock in that tier: `Cuirass` because it uses `4` hides
- `[todo]` Define the exact level spacing for those within-tier unlocks once the
  first tier-`1` through tier-`6` recipe table is drafted.
- `[todo]` Decide whether every slot exists at every tier, or whether the early
  tiers stage in pieces gradually.
- `[todo]` Tune the set so leather remains materially distinct from metal and
  cloth armor without relying on offense bonuses.
- `[todo]` Review whether leather armor should keep any mild movement/speed or
  defense-shape identity once the full set exists.
- `[done]` Lock the total defense budget granted by each implemented tier from
  `1` through `6`.
- `[todo]` Treat blacksmithing's older progression as legacy for now; full
  smithing rebalance can happen later, but leather/carapace should start on the
  new craft-vs-equip model immediately.

## Recommended Next Order

1. Implement the first tanning / curing interaction using `Frame` as the
   tentative in-world tanning-rack object.
2. Convert each source NPC family into the final armor defense split using the
   documented ratio rule.
3. Define the first `Crafting` level requirements for tiers `1` through `6`.
4. Implement the first live leather/carapace armor slice on top of the shared
   ingredient-budget model.
5. Map the current leather item line into `Coif / Cuirass / Chaps / Gloves /
   Boots`.
6. Extend the same defense-per-ingredient model into the future metal rebalance.
