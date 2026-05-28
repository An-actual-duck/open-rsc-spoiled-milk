# MyWorld Combat Notes

This document is a working design note for combat and combat-adjacent systems.
It is intended to capture:

- how the current system behaves
- what feels weak or outdated
- possible redesign directions
- decisions before implementation work begins

## 2026-04-11 Baseline Status

The PvM combat baseline is now implemented and considered feature-complete until
playtesting. The older notes below remain useful for design history, but many
phrases that describe the system as still legacy-only are pre-implementation
context rather than current state.

Current implemented baseline:

- `Melee`, `Ranged`, and `Magic` are the active combat styles.
- Normal PvM combat uses always-hit offense-versus-defense damage resolution.
- Player melee progression is consolidated through `Melee`.
- Ranged and magic gear have explicit offense/defense support.
- NPC defense profiles and mixed attack profiles exist for representative
  melee, ranged, magic, and mixed enemies.
- NPC ranged and magic attacks use projectile events from distance.
- NPC target selection prioritizes melee-range threats, then lowest combat
  level.
- PvM supports multiple players attacking one NPC and multiple NPCs pressuring
  one player.
- XP is awarded by damage share.
- Loot rolls personally for each active contributor, with rare drops scaled by
  contribution.
- MyWorld is PvM-only; PvP, duels, OpenPK, and PK bots are not completion
  targets.

Remaining combat work is not core implementation. It is:

- manual playtesting with
  [combat-playtest-checklist.md](/home/justin/Core-Framework/docs/myworld/combat-playtest-checklist.md)
- final numerical tuning after real encounter feedback
- player-facing clarity and tooltip/help cleanup
- optional expansion of NPC profiles or special items if playtesting shows a
  gap

## Historical Pre-Implementation State

### General combat model

- Combat is still built around a legacy RSC structure
- Hit chance and damage are separate rolls
- A successful attack can still deal `0` damage
- Combat interactions are strongly shaped by older one-on-one engagement rules
- Several backend rules are uneven across melee, ranged, and magic

### Damage rolls

- Melee and ranged both use a hit check followed by a damage roll
- A miss returns `0`
- A successful hit can also still roll `0`
- This is one of the major reasons combat produces many zero hits
- Magic damage is generally rolled directly from spell max hit values

### Mitigation and defense

- Melee defense is clearly modeled through `Defense`, defensive prayers, combat
  style, and armor
- Ranged defense depends on formula mode and is not modeled as cleanly as it
  could be
- Magic currently appears much less coherent as a defensive system
- Equipment includes `magicBonus`, but it does not currently behave like a clear
  incoming magic resistance model

### Engagement and movement

- The current system still reflects classic locked-in combat assumptions
- Retreat and action restrictions are more rigid than desired for MyWorld
- Movement, combat state, and action permissions are tightly coupled

### Targeting and attacker counts

- The current design leans toward one-on-one engagement assumptions
- Shared target / multi-attacker behavior is not modeled in a modern MMO-style
  way

### Magic and spells

- Magic is both a combat system and a utility/progression system
- Some spells are useful and iconic, but others are weak, awkward, or not
  compelling enough to matter
- Spell usefulness should be reviewed as part of broader combat redesign, not as
  a disconnected system

## Pain points

- Too many `0` hits, including successful attacks that still do no damage
- Combat styles do not feel equally represented or equally well-defined
- Defensive rules are harder to explain than they should be
- Magic offense and magic defense are not balanced around a clean model
- Combat is not flexible enough in movement and in-combat actions
- Some spells feel lackluster or obsolete
- Utility magic and combat magic may need a clearer role split

## Design directions to explore

### Core combat model proposal

- Collapse combat progression into three combat skills: `Melee`, `Magic`, and
  `Ranged`
- Repurpose the current `Attack` skill slot and icon into `Melee`
- Treat the old `Attack` skill as obsolete under the new combat system
- Remove hit / miss success rolls entirely
- Resolve combat through opposed offensive and defensive rolls instead

#### Proposed roll structure

- Every attack connects by default
- Offensive power is determined by a roll based on the attacker's combat skill
  and weapon
- Defensive mitigation is determined by a roll based on the defender's armor
- Final damage is `offense roll - defense roll`
- Damage should be clamped so undefended targets can still take at least `1`
  damage
- Defensive mitigation should favor offense when fractions appear
- If defense would block `7.9`, it should block `7`, not `8`
- This is intended to keep the system offense-favored rather than letting
  mitigation round up too generously

#### Example

- If an attacker has up to `10` melee damage, they roll `1d10`
- If a defender has up to `5` melee defense, they roll `1d5`
- Final damage is `attacker roll - defender roll`

#### Equipment and stat implications

- Combat skills should primarily increase damage potential
- Weapons should also increase damage potential
- Armor should be the main source of defensive rolls
- Separate defensive stats should exist for melee, magic, and ranged so all
  styles are supported fairly
- Itemization will need to be reworked or expanded to support style-specific
  defense

#### Future extensions

- Weapons may later alter dice structure, not just flat maximums
- Example future variation: `2d5` instead of `1d10`
- Weapons may eventually gain more unique offensive behaviors once the base
  system is stable

#### Combat stance implications

- The current stance system mainly exists to direct melee XP
- That purpose goes away under the proposed three-skill model
- Stances will likely either be removed or repurposed later if a worthwhile new
  function is identified

### Hit and damage rules

- Remove successful zero-damage hits so landed attacks deal at least `1`
- Rework hit chance and max-hit formulas into a cleaner, easier-to-tune model
- Reduce cases where combat feels random without being interesting

### Combat style identity

- Give melee, ranged, and magic clearer offensive and defensive roles
- Define what each style is strong against and weak against
- Make gear and stat choices easier to understand

### Defense model

- Define what protects against melee
- Define what protects against ranged
- Define what protects against magic
- Decide whether armor is universal defense, split defense, or part of a more
  explicit resistance system

### Equipment identity

- Preserve armor and weapon variety, but remove strict within-tier upgrade paths
- Turn same-tier equipment into sidegrades with meaningful tradeoffs instead of
  obvious best-in-slot progression inside the tier

#### Armor direction

- Variants such as helm vs full helm, chainmail vs platebody, and square shield
  vs kite shield should remain
- Those variants should no longer be simple “better version / worse version”
  pairs
- Armor pieces should gain distinct defensive or combat-role identities
- Example direction: chainmail could trade some raw protection for a small
  attack-frequency or tempo benefit

#### Current lighter-gear sidegrade direction

- `Square shield`
  - keeps a split-defense identity instead of being a pure melee wall
  - half of its defense value should go to melee defense
  - half of its defense value should go to ranged defense
  - when the split is uneven, melee rounds up and ranged rounds down

- `Medium helm`
  - becomes the lighter offensive helm option
  - should provide only the defense value of `1 bar` for balance purposes
  - should grant a flat tier-independent damage-roll skew toward higher values
  - current target: roughly a `10%` skew toward higher damage rolls
  - this replaces old accuracy-style identity with a new always-hit-compatible
    output bonus

- `Chainmail`
  - becomes the lighter tempo armor option
  - should provide only the defense value of `2 bars` for balance purposes
  - should grant a flat tier-independent hit-frequency bonus
  - current target: roughly `+5%` hit frequency
  - this should stack as a real cadence effect, not extra damage

#### Weapon direction

- Weapon classes should also gain stronger identity under the new combat model
- Old distinctions based on accuracy will need replacement if hit chance is
  removed
- Candidate differentiators include:
  - attack frequency
  - damage profile
  - dice structure
  - higher peak damage versus steadier average damage
- Example direction: one weapon could use `1d10`, another `2d5`, and another a
  slower but higher-spike profile
- Weapon redesign should aim for meaningful role differences rather than flat
  statistical superiority

#### Poison direction

- Poison should become a player-earned progression feature rather than mainly a
  drop-only reward
- A proper poison-making path should be added to the game
- Because poison will require effort and progression to obtain, poisoned
  weapons should feel meaningfully better once poison is applied
- Dagger balance should be set around lower direct damage, roughly around spear
  sustained value, so poison becomes the reward on top
- Poisoned daggers should not lose direct weapon damage relative to their
  normal dagger version just because poison was added
- The reward should come from poison making the weapon better overall, not from
  replacing base weapon damage with a weaker profile

### Combat flow

- Support freer movement during combat
- Support in-combat consumable use
- Support multiple attackers on a target
- Make encounter flow feel more active and less locked-down

### Spell review

- Audit combat spells for usefulness, scaling, identity, and redundancy
- Audit utility spells for relevance and quality-of-life value
- Remove or rework spells that are lackluster, obsolete, or not worth using
- Consider whether some spells should become stronger utility tools rather than
  weak combat options
- Consider whether some spell families should gain secondary effects, clearer
  niches, or more interesting scaling

#### Current spellbook redesign direction

- Elements should have meaningful gameplay effects, not just serve as damage
  tiers
- Elemental combat spells should be treated as equals with different identities,
  not as a simple progression ladder
- The current elemental tiering model is intended to be removed
- Higher-tier combat spell progression can still exist, but it should sit above
  the elemental layer rather than being duplicated inside it

#### Consolidation direction

- The spellbook should be consolidated significantly
- The current live direction is now a reduced spellbook built around:
  - elemental strike / bolt / blast / wave tiers
  - healing spells
  - teleports
  - `Alchemy`
  - `Iban Blast`
  - mage arena god spells
- Direct-damage spells and related debuff spells were strong consolidation
  candidates, and the current live pass now drops the old debuff line instead
  of preserving it as filler
- Reclaimed spell icons should be available for new and more interesting spells

#### Utility spell direction

- `Low Alchemy` and `High Alchemy` are now being collapsed into one live spell:
  `Alchemy`
- `Bones to Bananas` is not part of the current live spellbook direction
- Healing is now an active spellbook path through:
  - `Weak Heal`
  - `Heal`
  - `Strong Heal`
- Full healing balance is still intentionally open
- Teleport spells are largely intended to remain unchanged
- Orb charging and enchanting spells currently feel too narrow in use and
  are being removed from active spell progression instead of kept as clutter

## Candidate questions

- Should every landed attack deal at least `1` damage?
- Should accuracy and mitigation be symmetric across all three combat styles?
- Should magic have its own defensive stat or resistance system?
- Should ranged rely on armor, defense, positioning, or some mix?
- How much of combat should be universal, and how much should differ between PvE
  and PvP?
- Which spells should be removed entirely versus redesigned?
- Should utility magic be expanded rather than trimmed?
- How should elemental identities map to combat effects?
- Which existing combat and debuff spells should merge, and what should the new
  combined spells actually do?
- Should healing magic remain bone-conversion based, or become a more direct
  spell family?
- Should orb charging and enchanting become broader crafting/progression tools,
  or be simplified?
- How should armor variants within the same material tier differ from each
  other?
- How should weapon classes differentiate themselves once accuracy is removed?
- Should attack frequency be a core differentiator, or only one tool among
  several?

## Decision log

Use this section to record settled decisions before implementation begins.

- Combat is moving toward a three-skill model: `Melee`, `Magic`, and `Ranged`
- The current `Attack` skill slot/icon is intended to be repurposed into
  `Melee`
- Combat is intended to remove hit / miss rolls in favor of always-hit opposed
  offense-versus-defense rolls
- Offensive rolls should come primarily from combat skill and weapon power
- Defensive rolls should come primarily from armor, with separate defense tracks
  for melee, magic, and ranged
- Spellbook redesign should remove elemental tiering and give elements distinct
  gameplay identity instead
- The spellbook is expected to be consolidated, especially around redundant
  combat, alchemy, and healing-adjacent spell lines
- Armor and weapons should move toward sidegrade-based identity, not strict
  within-tier linear upgrades
- Poison should become a real progression system, and poisoned weapons should
  retain their base weapon damage while poison acts as the earned upside
