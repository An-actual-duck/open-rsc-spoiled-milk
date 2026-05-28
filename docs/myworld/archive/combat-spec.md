# MyWorld Combat Spec

This document turns the high-level combat notes into an implementation-oriented
specification. It should answer:

- what the combat system is supposed to do
- which current systems are being replaced
- what data and rules need to exist in code
- which decisions are settled versus still open

This is the source document for future tasklists.

## Status

- Draft status: `Working Draft`
- Scope: combat, equipment stats, combat skills, and combat-adjacent spellbook
  structure

## Goals

- Replace legacy RSC combat with a simpler, more expressive combat model
- Collapse combat progression into three main combat skills:
  - `Melee`
  - `Magic`
  - `Ranged`
- Remove hit / miss accuracy rolls as a core combat mechanic
- Resolve combat through opposed offense and defense rolls
- Ensure melee, magic, and ranged all have clear offensive and defensive
  identities
- Rework armor and weapons into meaningful sidegrades instead of mostly linear
  within-tier upgrades
- Consolidate and redesign the spellbook so it is smaller, more coherent, and
  more interesting
- Add a real player-facing poison creation path so poison is a progression
  reward rather than mostly a loot shortcut

## Replaced systems

The following current systems are intended to be replaced or made obsolete:

- separate `Attack`, `Strength`, and `Defense` melee progression
- hit / miss accuracy rolls for normal combat
- melee XP-directed stance purpose
- elemental spell tier ladders where elements mostly act as damage ranks
- redundant spell pairs and spell lines where one spell is a weaker copy of a
  better spell
- same-tier armor and weapon variants that are mostly strict upgrades

## Combat skills

### Intended skill model

Combat progression should be based on three skills:

- `Melee`
- `Magic`
- `Ranged`

### Melee slot reuse

- The current visible `Attack` skill slot/icon should be repurposed into
  `Melee`
- The old `Attack` skill should not remain a meaningful independent stat once
  the new system is complete
- Existing `Strength` and `Defense` should also be folded into the new melee
  system

### Skill purpose

- Combat skills should primarily increase offensive potential
- In the current direction, armor is the main source of defensive mitigation
- This means skill growth is intended to feel more like increasing damage
  capability than increasing chance to hit

## Core combat resolution

### Settled direction

- Attacks should not use success / fail hit rolls
- Attacks connect by default
- Combat is resolved through an offensive roll versus a defensive mitigation
  roll
- Final damage is derived from the difference between those two rolls

### Baseline formula

Current intended structure:

1. Determine the attacker's offense roll range from combat skill and weapon
2. Determine the defender's defense roll range from armor and relevant defense
   type
3. Roll offense
4. Roll defense
5. Final damage = offense roll - defense roll

### Offense stacking direction

- Phase one offense should use simple additive stacking
- Total offense should be:
  - relevant combat skill contribution
  - plus weapon offense contribution
- The initial example discussed was a simple offense-to-max-hit conversion using
  a flat ratio
- That ratio is not final and should not be treated as locked
- The real conversion curve should be calibrated against current game damage so
  the new model stays roughly in line with expected combat output
- The important locked-in direction is the additive stacking model, not the
  example numbers

### Calibration reference points

Current code suggests these rough damage bands for player combat:

- Early melee with weak gear is roughly in the `1-2` max-hit range
- Mid melee progression is roughly in the `7-10` max-hit range
- Higher melee progression is roughly in the `11-14` max-hit range
- Endgame melee progression is roughly in the `18-22` max-hit range

Representative current examples:

- Bronze scimitar at very low strength: about `1-2` max hit
- Rune scimitar at `40` strength: about `8` max hit
- Rune 2-handed sword at `40` strength: about `10` max hit
- Rune scimitar at `60` strength: about `11` max hit
- Rune 2-handed sword at `60` strength: about `14` max hit
- Rune scimitar at `99` strength: about `18` max hit
- Rune 2-handed sword at `99` strength: about `22` max hit

Current code also suggests these rough magic bands:

- Strike spells: about `1-4` max hit
- Bolt spells: about `4.5-6` max hit
- Blast spells: about `6.5-8` max hit
- Wave spells: about `8.5-10` max hit
- Special spells like Iban and charged god spells go higher

### Calibration recommendation

- The new offense-to-max-hit curve should aim to preserve these broad damage
  bands in phase one
- That means early combat should still feel low-impact, midgame should still
  land in the upper single digits, and endgame should still reach the high
  teens / low twenties for strong melee weapons
- A simple additive offense model is still appropriate, but the offense-to-hit
  conversion rate must be chosen to land in these ranges rather than copied from
  placeholder examples

### Phase 1 recommended offense curve

- Skill level should contribute offense on a `1:1` basis
- Weapon offense should add directly on top of skill offense
- Total offense should convert to max hit using this baseline rule:
  - `max_hit = 1 + ceil(total_offense / 7)`
- Rounding should always go upward
- Example:
  - if the calculated hit tier is `2.1`, it should round up to `3`

### Phase 1 note

- This is the starting calibration target, not a forever-locked rule
- Weapon offense values will still need tuning so the resulting max-hit ranges
  stay close to the desired early / mid / late combat bands

### Current bootstrap implementation

- Until item defs are manually rebalanced, the code uses a temporary derived
  conversion layer for equipped items
- Offensive stats are currently limited to weapon-path slots only:
  - melee offense: mainhand only
  - magic offense: mainhand only
  - ranged offense: mainhand and ammo paths only
- Defensive stats currently sum across all equipped armor slots
- Temporary fallback rules currently in code are:
  - melee offense defaults to `ceil(old weaponPowerBonus / 7)` for eligible
    melee weapons
  - magic offense defaults to a small value derived from old `magicBonus` on
    weapon-path items
  - melee defense defaults to `ceil(old armourBonus / 14)` on standard melee /
    general armor pieces
  - ranged defense defaults to `ceil(old armourBonus / 14)` on ranged-typed
    armor pieces
  - magic defense defaults to `ceil(old armourBonus / 14)` on robe / magic-type
    armor pieces
  - bootstrap style classification is currently name-based and is expected to be
    refined during the manual rebalance pass
- This bootstrap layer exists only to make the new combat model testable before
  a full item-by-item stat pass
- It should be treated as transitional and expected to change during
  rebalancing

### Minimum damage intent

- If the defender has no defense for the incoming style, a successful attack
  should deal at least `1`
- If the defender's mitigation roll fully cancels the offense roll, final damage
  may be `0`
- Because attacks no longer miss, this should still reduce the total number of
  `0` hits compared to the legacy system without forcing guaranteed chip damage
  on every attack

### NPC damage-roll direction

- NPCs should be allowed to deal `0` damage
- NPC damage rolls should be weighted toward lower values rather than using a
  perfectly flat distribution
- NPC damage should round downward rather than upward
- Example:
  - `2.9` should become `2` for NPC damage outcomes
- This is especially important for early-game combat where players may have
  little or no armor
- The intent is to keep early enemies threatening without making them feel too
  spiky or punishing
- This should be treated as a balance rule layered on top of the new combat
  model, not a return to hit / miss accuracy

### Example model

- Attacker has up to `10` melee damage: roll `1d10`
- Defender has up to `5` melee defense: roll `1d5`
- Final damage = attacker roll - defender roll

## Defense model

### Settled direction

- Separate defense tracks should exist for:
  - melee
  - magic
  - ranged
- Armor should be the primary source of these defensive values
- Defense should come from gear only
- Defensive rules should be easier to explain than the current system

### Intended implications

- Items will need new or reworked stat definitions
- Existing gear values will need rebalance or migration
- Magic should gain a clearer defensive model than it currently has

### Phase 1 defense curve direction

- Defense should follow the same broad conversion logic as offense
- Total defense for the incoming style should convert into a mitigation roll
  size using a simple upward-rounded formula
- The exact multiplier can be tuned during implementation, but the intended
  baseline is that defense scaling should closely mirror offense scaling
- When mitigation math produces fractional values, defense should round down
- Example:
  - if defense would block `7.9`, it should block `7`
- This is intended to keep offense slightly favored over defense

### Phase 1 armor calibration target

- All armor slots should be counted in total defense
- Armor tier restrictions should matter when determining total defense
- A full armor setup from a given tier should roughly mirror the damage
  potential of weapons from that same tier
- The attacker should still have a slight edge because skill level contributes
  directly to offense
- Exact parity is not required because weapons and armor already differ within
  a tier and sidegrade identity is intentional
- The calibration target is rough same-tier equivalence, not perfect symmetry

## Equipment model

### Armor design direction

- Armor variants should remain in the game
- Variants inside the same material tier should become sidegrades rather than
  strict upgrades

Examples of variants that should remain meaningful:

- helm vs full helm
- chainmail vs platebody
- square shield vs kite shield

#### Settled lighter-gear identity direction

- `Square shield`
  - should use split defense rather than full pure-melee defense
  - half of its defense value goes to `melee_defense`
  - half goes to `ranged_defense`
  - if the value is odd, melee gets the rounded-up half and ranged gets the
    rounded-down half

- `Medium helm`
  - should count only as the defense value of `1 bar`
  - should provide a flat, tier-independent offensive roll-quality bonus
  - current target: about a `10%` skew toward higher damage rolls
  - this is intended to replace old accuracy-flavored identity in a system
    where attacks always connect

- `Chain mail body`
  - should count only as the defense value of `2 bars`
  - should provide a flat, tier-independent hit-frequency bonus
  - current target: about `+5%` hit frequency
  - this is intended as a tempo sidegrade against the heavier pure-defense
    platebody line

### Weapon design direction

- Weapons should also become sidegrades with stronger role identity
- Existing differences based on accuracy need replacement under an always-hit
  system
- Weapons should still meaningfully affect damage potential
- Poison-capable weapons should be balanced with poison in mind, not treated as
  if poison does not exist
- When poison is added to the game as a player-earned progression path,
  poisoned weapons should feel like an upgrade, not a sidegrade that sacrifices
  direct damage
- Poisoned versions of weapons should retain their base weapon damage profile
  unless there is a separate explicit redesign reason to change them

### Candidate differentiators

Potential equipment differentiators already identified:

- offense by combat style
- defense by combat style
- dice structure
- higher spike damage versus steadier average damage

### Phase 1 equipment restriction

- In phase one, armor should provide defense only
- Armor should not provide offense or new unique effects in the first pass
- Weapon contribution should remain the active gear-side offensive input
- Existing item-side special effects that already exist in the game should be
  documented and preserved where possible rather than silently dropped
- Existing non-damage weapon bonuses or special behaviors should also be
  documented and preserved or intentionally remapped

### Sidegrade exception note

- The lighter metal armor sidegrades above are accepted exceptions to the pure
  phase-one “defense only” restriction
- These should be treated as intentional first-pass identity rules, not scope
  drift
- Their effects should remain flat across tiers so the main scaling still comes
  from defensive value and weapon/skill progression

### Phase 1 item stat vocabulary

The first implementation should use these six item stats:

- `melee_offense`
- `ranged_offense`
- `magic_offense`
- `melee_defense`
- `ranged_defense`
- `magic_defense`

### Future extension

The system may later support more varied offensive roll structures, such as:

- `1d10`
- `2d5`
- other weapon-specific roll patterns

That is considered a later extension after the base system is functioning.

### Poison progression direction

- A proper poison-making path should be added to the game instead of relying on
  poison primarily as loot or quest reward content
- Poison creation should sit behind meaningful progression and effort
- Because poison is intended to be earned, poison should function as a real
  reward layer on top of compatible weapons
- Dagger balance should therefore target lower direct sustained output, roughly
  in the same neighborhood as spear, so poison can make the weapon feel worth
  pursuing without requiring the poisoned version to lose base damage

## Combat stances

### Settled direction

- The current reason for combat stances disappears when melee XP allocation
  goes away
- Combat stances should be removed in phase one
- If a worthwhile future use appears later, the concept can be revisited from a
  cleaner foundation

## Spellbook direction

### Elemental combat magic

- Full spell overhaul is phase two, not phase one
- Elements should have meaningful effects or identity
- Elements should not merely represent higher or lower damage tiers
- Elemental spells should be considered peers with different uses

### Spell consolidation

- The spellbook should be consolidated significantly
- Direct-damage spells and debuff spells are strong candidates for merging into
  cleaner combined spell lines
- Merging does not require literal stacking of old spell effects
- Freed icons and spell slots should be reusable for new spells

### Tiering direction

- Higher-tier combat spell progression can remain
- Elemental tier ladders should not remain

### Utility spell direction

- `Low Alchemy` should likely be removed
- `High Alchemy` should move into its place and be renamed `Alchemy`
- `Bones to Bananas` and `Bones to Peaches` should either be consolidated or
  redesigned into a more direct healing path
- Teleport spells are largely intended to remain unchanged
- Orb charging and enchanting spells should be expanded or consolidated

### Phase 1 spell scope

- Phase one should not attempt the full spellbook redesign
- Phase one should only make `Magic` offense and `Magic` defense work under the
  new combat model
- Spell consolidation, elemental identities, and broader spellbook redesign are
  phase two work

## Migration implications

These are known future implementation concerns, not yet fully specified:

- convert existing `Attack`, `Strength`, and `Defense` progression into `Melee`
- update gear definitions to support style-specific offense and defense
- update spell definitions and spell progression
- remove or repurpose stance data and UI assumptions
- rebalance NPCs and encounters for the new damage model

### Player melee migration rule

- Existing player `Attack`, `Strength`, and `Defense` XP should be summed into
  the new `Melee` XP pool
- The resulting `Melee` level should be awarded from that total XP
- This is intentionally a time-investment preserving migration, not a balance
  preserving migration
- This may inflate some migrated players relative to the old system
- That inflation is acceptable for this project because:
  - the old melee model was already structurally imbalanced against `Magic` and
    `Ranged`
  - this is a major ruleset variant rather than a drop-in replacement for an
    existing live world
  - only players opting into the redesigned ruleset are expected to migrate

## NPC conversion rules

### Phase 1 direction

- NPCs currently have separate legacy combat values for:
  - `attack`
  - `strength`
  - `defense`
  - `ranged`
  - `hits`
- Phase one should preserve those source values so they are not lost during the
  transition
- Mechanical combat use should shift to the new combat model without requiring
  legacy `attack` to remain active

### Intended mapping

- NPC `strength` should be used as the baseline source for melee offense
- NPC `defense` should be used as the baseline source for melee defense
- NPC `ranged_defense` should start at `0` in phase one unless explicitly set
- NPC `magic_defense` should start at `0` in phase one unless explicitly set
- NPC `hits` should remain the NPC health pool as it does currently

### Legacy stat persistence

- NPC `attack` should remain stored for now even if it is no longer used by the
  combat formula
- This preserves compatibility and keeps the original data available for future
  use or display
- Existing NPC `combatLevel` should remain preserved as its own definition value
  rather than being recalculated from the new combat model in phase one

### Phase 1 rationale

- Starting NPC `ranged_defense` and `magic_defense` at `0` keeps the first
  implementation simple
- After the system is working, NPCs can be reviewed and assigned explicit
  ranged and magic defenses where needed
- This reduces up-front conversion work and makes it easier to validate the new
  formula before deeper balancing

## Open decisions

These need to be answered before detailed implementation tasklists are written.

### Core combat math

- Should all three styles share exactly the same resolution model?

### Skill contribution

- Skill offense and weapon offense should stack additively
- The exact offense-to-max-hit conversion curve is still open and must be
  calibrated against current combat values
- Should armor ever contribute offense, or only defense / utility?

### Speed

- `Speed` should be considered a later-phase stat, not part of phase one
- Phase one should keep focus on offense / defense rolls and gear stat
  conversion

### Equipment stat vocabulary

- Phase one item stats:
  - melee offense
  - ranged offense
  - magic offense
  - melee defense
  - ranged defense
  - magic defense

### NPC data model

- Should NPCs use the same six-stat vocabulary as players internally?
- If so, should phase one map legacy NPC values into that new model at spawn
  time instead of reading old combat stats directly during combat?

### Spellbook structure

- Phase one excludes full spellbook overhaul
- Phase two will answer:
  - which current elemental spell lines merge together
  - what each element's intended identity is
  - whether healing magic remains tied to bones/items or becomes direct healing

### Stances

- Stances should be removed in phase one
