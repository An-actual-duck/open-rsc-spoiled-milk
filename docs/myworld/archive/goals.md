# MyWorld Goals

For the consolidated active broad backlog, use
[remaining-work.md](/home/justin/Core-Framework/docs/myworld/remaining-work.md)
first. This file remains useful as the longer-form goal and design record.

This document tracks intended gameplay and architecture changes for `MyWorld`.

## Status legend

- `Idea`: desired direction, not yet designed in detail
- `Planned`: accepted goal with known implementation shape
- `In Progress`: currently being worked on
- `Done`: implemented
- `Implemented - Pending Playtest`: implemented and covered by automated checks,
  but final numbers are intentionally held until real encounter feedback exists

## Goals

### G-001: Consolidate melee combat skills

- Status: `Implemented - Pending Playtest`
- Priority: `High`

#### Summary

Replace the separate `Attack`, `Strength`, and `Defense` skills with a single
skill named `Melee`.

#### Intended outcome

- Rename the visible `Attack` skill to `Melee`
- Fold `Strength` progression into `Melee`
- Fold `Defense` progression into `Melee`
- Rework combat progression so melee combat trains one skill instead of three
- Remove or repurpose UI, progression, and reward paths that currently assume
  separate `Attack`, `Strength`, and `Defense` stats

#### Scope note

This is feasible, but it is a major core-systems change, not a simple plugin
change.

It touches at least:

- core skill definitions and indexing
- player stat storage and migration
- combat formulas and XP distribution
- UI/stat packet generation
- equipment requirements
- quest rewards
- any content that checks `Attack`, `Strength`, or `Defense` independently

#### Initial implementation direction

- Treat `Melee` as a true replacement, not as a cosmetic label only
- Design a migration strategy for existing characters before code changes begin
- Start with server-side rules and persistence, then update packets/UI, then
  content references
- Avoid partial rollout where all three skills still exist internally unless
  there is a strong migration reason

#### Open questions

- How should existing `Attack`, `Strength`, and `Defense` XP convert into
  `Melee` XP?
- Should old melee equipment requirements map directly to `Melee` levels?
- Should combat level calculation change, or should it preserve similar pacing?
- Should melee style selection remain, or be simplified?

#### Current progress

- `Melee` already exists as the visible replacement for the old `Attack` slot.
- Player migration now folds legacy `Attack`, `Strength`, and `Defense` XP into
  `Melee`.
- Old melee equipment requirements are already being remapped onto `Melee`.
- Live PvM combat formulas, XP attribution, and active spell/stat effects now
  route through the unified `Melee` model instead of treating legacy `Attack`,
  `Strength`, and `Defense` as active player combat stats.
- Remaining work is mostly player-facing cleanup, quest/reward text cleanup, and
  tuning after playtesting, not core implementation.

### G-002: Modernize combat interaction rules

- Status: `Implemented - Pending Playtest`
- Priority: `High`

#### Summary

Move combat away from the current hard engagement / retreat-lock model and
toward a more modern MMO-style interaction model.

#### Desired outcome

- No locked-in combat state for basic movement
- Players can walk past an enemy without being forced into a hard engagement
- An enemy can attack a moving player, and the player can continue moving and
  take the hit without being forced to stop and fully engage
- Players can leave combat at any point
- Players can perform other actions while in combat, especially consuming food
  and potions
- Multiple players can attack the same target at the same time
- Multiple enemies can attack the same player at the same time

#### Scope note

This is a major combat model change, not a small balance tweak.

It likely affects at least:

- combat engagement state
- pathing and movement interruption rules
- retreat / escape logic
- combat event ownership assumptions
- action restrictions during combat
- NPC behavior
- XP and loot attribution rules

MyWorld scope note:

- `MyWorld` is now explicitly PvM-only
- PvP, duels, and wilderness/OpenPK parity are not part of this goal
- shared PvP code only matters here when it blocks or complicates PvM behavior

#### Current progress

- PvM no longer uses the old hard retreat-lock behavior as its primary
  interaction model.
- Players can now flee PvM combat immediately.
- Food and potions can now be used during combat.
- Multiple players can now attack the same NPC, and multiple NPCs can now keep
  pressure on the same player.
- PvM melee now runs through the newer attacker-driven event path instead of
  relying entirely on the older reciprocal lock model.
- PvM XP is now shared across contributing damage dealers by damage share.
- NPC loot now rolls personally for each active contributor, with rare-table and
  rare-normal-drop access scaled by contribution.
- NPC targeting now prioritizes melee-range players first, then lowest combat
  level, so lower-level players still feel threatened in group PvM.
- MyWorld is explicitly PvM-only; PvP, duels, OpenPK points, and PK bots are
  disabled in the MyWorld config and covered by combat exception guardrails.

#### Current gap from desired behavior

The core PvM interaction model is implemented. Remaining work is practical
validation rather than design uncertainty:

- run the manual combat playtest checklist once live testers or multiple local
  clients are available
- tune contribution scaling, encounter pressure, and reward feel after real
  play sessions
- keep shared PvP/duel code pruned or inert where it conflicts with the PvM-only
  server direction

#### Open questions

- Should all consumables be usable in combat, or only food and potions?
- Should movement ever break auto-attacking, or should attacks continue while
  moving if targets remain in range?
- Are the current damage-share XP and personal drop contribution scales generous
  enough in real group play?
- How much kiting should be allowed or encouraged?

### G-003: Rework combat mitigation formulas

- Status: `Idea`
- Priority: `High`

#### Summary

Rework damage, accuracy, and defense formulas into a more coherent model so
melee, ranged, and magic each have a sensible and equally represented place in
combat.

#### Desired outcome

- Replace ad hoc or uneven mitigation rules with a clear combat model
- Ensure melee, ranged, and magic each have meaningful offensive and defensive
  counterplay
- Define what stats and equipment protect against each combat style in a way
  that is easy to explain and maintain
- Reduce hidden inconsistencies between default and alternate combat formula
  modes

#### Scope note

This is a core combat-systems rebalance and should be treated separately from
movement / engagement changes.

It likely affects at least:

- hit chance formulas
- max hit formulas
- defense and mitigation formulas
- equipment stat meanings
- prayers and defensive effects
- spell, ranged, and melee balance
- NPC combat stats and tuning

#### Open questions

- What should defend against melee, ranged, and magic respectively?
- Should armor mitigate all damage styles, or should different gear stats split
  those responsibilities?
- Should magic have an explicit defensive stat or resistance model?
- Should there be one unified formula set, or different rules by mode/content?

### G-004: Add a player-crafted poison path

- Status: `Idea`
- Priority: `Medium`

#### Summary

Add a real player-driven method of creating weapon poison instead of relying on
it primarily as drop/reward content.

#### Intended outcome

- Add a craftable or otherwise skill-driven way to make poison
- Make poison application part of player progression instead of mostly a lucky
  acquisition path
- Preserve poison as a meaningful reward for effort and investment
- Keep poisoned weapons feeling better than their unpoisoned equivalents because
  poison was earned, not handed out freely

#### Scope note

This is both a progression-system goal and a combat-balance goal.

It likely affects at least:

- Herblaw / item-creation content
- poison item acquisition
- poisoned weapon access and pacing
- combat balance for daggers, spears, darts, knives, and other poison-enabled
  weapons

#### Open questions

- Which skill should own poison creation?
- What ingredients and level requirements should poison creation use?
- Should poison creation unlock in tiers?
- Which weapons and ammo types should remain poison-compatible?

### G-005: Improve item and combat clarity

- Status: `Idea`
- Priority: `Medium`

#### Summary

Add clearer naming, examine text, and UI support so the player can understand
the many new combat and equipment rules introduced by MyWorld.

#### Intended outcome

- Surface actual mechanical effects of gear in a clean way
- Use flavor text where appropriate, but also provide exact mechanical
  explanation somewhere accessible
- Make sidegrade armor and weapon identities understandable without requiring
  external documentation
- Keep upper-left hover text minimal and consistent with the rest of the game,
  primarily showing the item name

#### Scope note

This is both a UI/UX goal and a gameplay-clarity goal.

It likely affects at least:

- item naming and examine systems
- combat stat display
- equipment interfaces
- spell and item descriptions

#### Open questions

- Where should exact mechanical data live: examine text, inspect pane,
  equipment/status UI, or a dedicated UI?
- How much flavor text should coexist with exact stat text?
- Which effects should remain discoverable through examine rather than hover?

### G-006: Expand enemy combat-style variety

- Status: `Implemented - Pending Playtest`
- Priority: `Medium`

#### Summary

Make enemy defenses and weaknesses meaningfully varied across melee, magic, and
ranged instead of having most NPCs collapse into the same broad pattern.

#### Intended outcome

- Some enemies should be weak to melee
- Some enemies should be weak to magic
- Some enemies should be weak to ranged
- Some enemies should be weak to two styles
- Some enemies should be broadly even across all three styles
- Enemy style matchups should become part of encounter identity
- Enemies should also pressure the player's split defenses through offense, not
  just through authored weaknesses
- Broad NPC families should eventually include meaningful melee, ranged,
  magic, or mixed attack identities where appropriate
- Existing NPCs should be updated first so all three combat styles have real
  encounter representation before broader enemy roster expansion
- New NPC additions should follow once there is a reliable sprite/art pipeline
  for producing usable in-game animation sheets

#### Scope note

This is primarily an NPC combat-design goal layered on top of the new combat
stat model.

It likely affects at least:

- NPC stat derivation rules
- NPC family templates
- boss and special-enemy tuning
- spell, ranged, and melee PvE balance
- reusable NPC attack-profile logic
- projectile and special-attack handling for non-melee enemies
- future NPC art and animation production workflow for wholly new monsters

#### Open questions

- Which enemy families should define the first style identities?
- How much of enemy defense should be automatic derivation versus authored data?
- Should weaknesses be obvious from visuals and flavor text?
- Should broader enemy style variety come from fixed family attack roles,
  alternate attacks, or both?
- How much of the enemy attack-side system can reuse existing special-case
  paths versus needing a generalized mixed-style framework?
- Which missing attack-style roles should be filled by modifying existing NPCs
  versus introducing entirely new monsters later?
- What sprite-generation or art workflow is reliable enough to support future
  new NPC additions without creating unusable animation sheets?

#### Current progress

- The first benchmark-family multiplier pass now exists for:
  - skeletons
  - zombies
  - demons
  - dragons
  - battle mages
- NPC ranged/magic attack profiles now exist for pure ranged, pure magic,
  melee+ranged, and melee+magic enemies.
- Mixed-style NPCs can use ranged or magic projectiles from distance, and still
  have a preferred chance to use projectile attacks while a player is in melee
  range.
- Initial lower-, mid-, and high-level candidates have been moved onto ranged
  or magic attacks, including demons, elementals/giants, undead/spectral
  enemies, bandits, tribesmen, pirates/mercenaries, and caster archetypes.
- Dragons keep breath as dragon-only special behavior.
- Automated checks now validate attack-profile coverage, projectile behavior,
  source stats used for ranged/magic damage, and representative scenario
  viability.
- Remaining work is final encounter tuning and adding more profiles later if
  playtesting shows gaps, not the baseline attack-side framework.
- Adding entirely new NPCs is still desired, but it is explicitly downstream
  of finding a more reliable way to produce consistent sprite sheets and
  animation frames.

### G-007: Audit underdeveloped skills

- Status: `Done`
- Priority: `Medium`

#### Summary

Review older or thinly implemented skills and decide whether they should remain
full progression tracks, be hidden, or be effectively collapsed.

#### Intended outcome

- Treat `Firemaking` as a retired compatibility skill, not a live progression
  system
- Keep the skill hidden and effectively maxed for old data/protocol/content
  compatibility until the skill slot can be safely removed
- Scrub active gameplay reliance over time so content does not require
  Firemaking levels or award Firemaking XP
- After Firemaking, review other skills for the same problem
- Identify which skills are too narrow, too redundant, or not fleshed out
  enough for the long-term design

#### Current decision

- `Firemaking` is retired for MyWorld progression
- It remains in the codebase only as a hidden compatibility skill slot because
  older client/stat/protocol/account structures still assume fixed skill IDs
- Players are treated as effectively maxed for compatibility with legacy checks
  while those checks are removed or converted
- It should not count as real player progression for purposes like total level
- Future tiering/progression audits should ignore Firemaking as an intentional
  retired system, but should still flag new active gameplay requirements, XP
  rewards, or itemization that depend on Firemaking
- `Tailoring` appears to already be effectively removed / dormant data rather
  than a live standalone gameplay skill, so no additional migration work is
  needed there right now

#### Scope note

This is a future progression and skill-identity goal rather than an immediate
implementation task.

Cleanup still affects at least:

- live Firemaking level checks
- Firemaking XP rewards
- Firemaking cape access
- quest and item gating
- fire-lighting utility behavior that should work without a progression skill

#### Open questions

- Which other skills are similarly underdeveloped?
- Should thin skills be expanded, merged, or effectively deprecated?

### G-008: Rework prayer into god-aligned allocation lines

- Status: `In Progress`
- Priority: `Medium`

#### Summary

Rework `Prayer` from drain-over-time upkeep into a god-aligned allocation
system. The accepted model is tracked in `docs/myworld/prayer-rework.md`.

#### Intended outcome

- Audit what prayer equipment already exists, especially silver-based items and
  holy/unholy symbol lines
- Keep silver as the prayer-aligned material identity
- Revisit black and white knight equipment as a separate prayer-aligned
  side-line, currently mirrored to the steel-tier band but expected to diverge
  after the broader prayer rework
- Move holy-symbol enchanting away from the old spell-based model and onto an
  altar-based interaction similar to other jewelry, but without rune
  association or rune cost
- Review current prayer leveling methods and design better progression if the
  existing path is too thin or repetitive
- Review individual prayers and identify which ones are too weak, redundant, or
  not useful enough to keep unchanged
- Rework prayer from a draining pool into an allocation pool:
  - activating a prayer reserves a fixed number of prayer points
  - those reserved points stay committed while the prayer remains active
  - turning the prayer off refunds the reserved points
  - players can stack multiple active prayers so long as they have enough
    unallocated prayer points left
- Assign explicit point costs to prayers and rebalance prayer effects around
  that allocation model instead of around passive drain rates
- Split prayers into three god-aligned lines:
  - Zamorak: melee offense, ranged defense, smithing XP
  - Saradomin: magic offense, melee defense, enchanting XP
  - Guthix: ranged offense, magic defense, crafting XP
- Use point costs, not level thresholds, as the natural activation gate
- Convert prayer equipment so it adds worn-only prayer allocation points instead
  of slowing prayer drain
- Replace the server XP multiplier over time with in-game XP multipliers such
  as skilling prayers

#### Current progress

- `PrayerCatalog` now pins the accepted MyWorld prayer tiers in server source:
  combat costs `3/6/10/15/21`, combat effects `5/10/15/20/25`, skilling costs
  `2/8/20/35/55`, and skilling effects `10/15/20/25/30`.
- Combat prayer effects stack additively but cap at 60%; full skilling prayer
  stacks reach a 100% XP bonus and require 120 allocation points.
- God-line altar mapping is centralized for runtime wiring.
- The custom client now renders a 15-slot current-book prayer grid and can swap
  between Saradomin, Zamorak, and Guthix books from a server packet.
- Prayer activation now uses allocation costs instead of level requirements or
  drain checks, and equipment overflow prunes active prayers from highest tier
  downward.

#### Scope note

This is both a progression-system audit and a broader mechanical review.

It likely affects at least:

- prayer equipment and itemization
- silver crafting / blessing flow
- altar interaction design
- prayer XP sources
- prayer activation balance, point costs, and any remaining drain/recovery rules
- combat and utility systems touched by prayers
- UI and player feedback for allocated versus free prayer points
- content, lore, and progression structure for god-specific prayer lines

#### Open questions

- Which silver items should remain prayer-specific and which should be
  redesigned?
- Should holy symbols use different altars for different blessing types, or a
  more general silver-only blessing system?
- How should prayer be leveled in a way that feels more interesting than the
  current method?
- Which prayers deserve rework, consolidation, or removal?
- What point costs make individual prayers fair under an allocation model?
- Which prayers should be mutually exclusive, if any, once stacking is no
  longer constrained by drain-over-time upkeep?
- How should the three god prayer lines be divided thematically and
  mechanically?

### G-009: Finish enchanting migration and polish

- Status: `In Progress`
- Priority: `Medium`

#### Summary

The main altar-based jewelry framework is now in place. The remaining work is
follow-up cleanup, progression rebalance, UI clarity, deeper behavior testing,
and completion of the player-facing migration away from the older
`Magecraft` naming toward the now-live `Enchanting` identity.

#### Intended outcome

- Keep the new altar-based jewelry system as the permanent path
- Finish removing or repurposing old magic-based enchanting leftovers
- Rebalance the current rune-and-jewelry progression once the final altar order
  and broader magic rework are settled
- Add better player-facing explanations for jewelry effects, charges, and altar
  interactions
- Add dedicated visual distinction for enchanted jewelry later, once a sprite-
  or mask-based approach is chosen that reads clearly in inventory.
- Expand behavior testing so special jewelry effects can be tuned safely
- Audit dormant compatibility items and legacy enchanting content that no
  longer belongs in active play

#### Scope note

This is primarily a systems-polish and migration-completion goal rather than a
new feature category.

It likely affects at least:

- altar enchanting interactions
- legacy spell/enchant content
- item definitions and compatibility shims
- test coverage
- tooltip and UI messaging
- progression gates and naming cleanup for the current
  `Magecraft` -> `Enchanting` transition

#### Open questions

- Which old enchanting spells should be removed entirely versus repurposed?
- When should the remaining `Magecraft` naming and level-gate references be
  fully finalized as `Enchanting` relative to the magic rework?
- How much legacy enchanted jewelry should remain in the data layer for
  compatibility only?
- What player-facing UI or tooltip changes are required so the new system is
  understandable in-game?

### G-010: Create full tiering for all weapon and armor types

- Status: `Planned`
- Priority: `Medium`

#### Summary

Create full tiering for all weapon and armor types, filling in any gaps.

#### Intended outcome

- Standard weapon families should have complete tier coverage with no missing
  internal steps
- Standard armor families should have complete tier coverage with no missing
  internal steps
- Ranged, melee, and magic gear should all follow intentional progression
  ladders instead of relying on uneven legacy availability
- Gear that exists beyond the current standard tier target should be reviewed
  separately after the base tier ladders are complete

#### Scope note

This is a long-term itemization and content-completeness goal layered on top of
the combat and equipment rewrites.

It likely affects at least:

- item definitions and custom item additions
- equipment stat mapping
- crafting, drops, shops, and reward sources
- progression balance across melee, ranged, and magic gear

#### Open questions

- Which missing tiers should be filled with new items versus remapped legacy
  items?
- Which weapon and armor families should be considered standard for the first
  complete pass?
- How should post-standard tiers like `dragon` be separated from the initial
  baseline ladder work?

### G-011: Align crafting skills with combat styles

- Status: `Planned`
- Priority: `Medium`

#### Summary

Use the main equipment-production skills to cover weapon and defensive-equipment
families without creating combat-style armor lanes:
`Blacksmithing` for metal weapons and armor, `Crafting` for non-metal defensive
gear and base staff/robe production, and `Enchanting` for magical upgrades.

#### Intended outcome

- `Blacksmithing` remains the main path for metal melee weapons and armor
- `Crafting` owns leather, cloth, base staff shaping, and robe production
- `Enchanting` owns magical upgrades, attunement, and post-crafting caster-gear
  specialization
- Equipment crafting should support combat progression without implying that
  armor belongs to offensive combat-style lanes.
- Standard equipment progression should follow shared tier logic across those
  three crafting paths

#### Scope note

This is a long-term progression and system-ownership goal, not a small content
patch.

It likely affects at least:

- skill definitions and visibility
- production recipes and stations
- item source ownership across weapons, armor, staffs, robes, and upgrades
- drop tables, shops, and reward sources that currently fill progression gaps
- future standard tier ladders for armor and weapons

#### Open questions

- How far should the remaining `Fletching` content be folded into `Crafting`
  before the old skill is retired?
- Which parts of robe/staff progression should stay as plain `Crafting`
  outputs before `Enchanting` takes over?
- Which equipment families should stay as exceptions outside those three main
  production skills?

### G-012: Keep armor as defensive material families

- Status: `Planned`
- Priority: `Medium`

#### Summary

Do not build ranged armor, magic armor, melee armor, or any other combat-style
armor lane. MyWorld armor is defensive equipment only.

#### Intended outcome

- Keep armor families based on material and production identity:
  - metal armor
  - leather armor
  - cloth / robe armor
  - special creature-material armor where appropriate
- Armor may provide melee, ranged, and magic defense values, but those are
  incoming-damage defenses, not offensive style identities.
- Some armor pieces may provide attack-speed modifiers as a sidegrade hook.
- Armor should not provide ranged, melee, or magic offense as its normal role.
- `Crafting` can own leather and cloth production without turning those items
  into ranged or magic armor.

#### Scope note

This is a terminology and progression guardrail for future equipment work.

It likely affects at least:

- equipment tasklists and balance notes
- leather, cloth, robe, and special-material production planning
- item definitions that currently inherit misleading combat-style labels
- future AI-assisted audits that might otherwise invent ranged/magic armor

#### Open questions

- Which material families need explicit production ladders first?
- Which existing items should be renamed or re-described to avoid implying
  combat-style armor?
- Which armor pieces, if any, should keep attack-speed sidegrade bonuses?

### G-013: Build the Crafting + Enchanting path for magic equipment

- Status: `Doing`
- Priority: `Medium`

#### Summary

Create a split magic-equipment path where `Crafting` handles base garment/staff
production and `Enchanting` handles magical upgrades, with the first live pass
now centered on simple wool garments and plain staff production before later
material and enchantment expansion.

#### Intended outcome

- `Crafting` should own base magic-equipment production alongside leather and
  cloth defensive gear production
- `Enchanting` should own the magical upgrade path for staffs and robes
- Staff progression should sit cleanly alongside the future broader
  magic-equipment path
- Add `4` more tree/material tiers so bows and plain staffs can scale alongside
  the expanded melee weapon ladder instead of stopping well short of the newer
  metal progression
- The first live robe baseline should be plain white wool garments:
  - hat
  - robe top
  - robe skirt
  - cosmetic cape
- Initial robe defense should stay conservative and material-based:
  - `1` magic defense per wool used on the defensive pieces
  - cape remains cosmetic with no defense
- Those baseline cloth defenses are now a persistent floor, not a temporary
  placeholder:
  - hat keeps `1`
  - robe top keeps `4`
  - robe skirt keeps `3`
  - later enchanting may add to them, but should not replace them
- The old colored wizard-gear line should be retired from active progression
  rather than continuously remapped into the new path
- The first altar-bound robe path should follow the current altar ladder:
  - tier `1`: unenchanted
  - tier `2`: air / water / earth / fire
  - tier `3`: mind / body
  - tier `4`: chaos
  - tier `5`: cosmic
  - tier `6`: nature
  - tier `7`: law
  - tier `8`: death
  - tier `9`: soul
  - tier `10`: blood
- The next robe-enchanting direction is no longer "one altar = one fixed robe
  tier output".
- Instead:
  - the altar/rune family should determine the robe's static effect identity
  - the player should choose which robe tier to craft at that altar up to their
    current Enchanting limit
  - rune cost should scale by selected tier rather than by a single fixed altar
    output
  - the current first-pass defense growth curve is:
    - hat: `1 + floor((tier - 1) / 2)`
    - top: `4 + (tier - 1) * 3`
    - skirt: `3 + (tier - 1) * 2`
- Current robe-effect draft:
  - `Earth`: magic/melee split defense
  - `Air`: magic/ranged split defense
  - `Water`: magic/ranged/melee split defense
  - `Fire`: magic-only defense plus `+10%` final total magic defense per piece
  - `Body`: `+33%` HP regeneration per piece
  - `Mind`: reduce debuff effectiveness by `20%` per piece
  - `Nature`: mitigate poison damage by `1` per piece
  - `Cosmic`: `10%` defensive reroll chance per piece
  - `Chaos`: reflect `5%` of incoming damage per piece on every hit
  - `Law`: smooth incoming damage by averaging one additional pre-armor damage
    roll per piece
  - `Death`: all-style defense scaling upward with missing HP
  - `Soul`: rechargeable out-of-combat shield based on max HP
  - `Blood`: `5%` spell lifesteal per piece
- Robes should eventually gain a real tiered production path instead of relying
  on color/theme leftovers
- One current design direction is:
  - weave flax into tweed at a spinning wheel
  - convert tweed into `[metal] tweed` through a station/process that parallels
    smelting
  - sew robe pieces through staged production that parallels smithing
- Metal tiering should remain part of the robe progression logic if a coherent
  in-world material explanation can be found

#### Scope note

This is a large future system, not part of the current active staff todo list.

It likely affects at least:

- skill definition and visibility for the final `Enchanting` presentation
- flax/fabric/material processing
- new stations or production interactions
- robe and cloth defensive item definitions
- magic-equipment progression balance
- the relationship between `Enchanting` and magic-equipment production

Full balance for this path is still intentionally deferred until the robe,
staff, spell, and altar pieces are all in place together. During migration,
prefer tuning through player damage output and related offense shaping rather
than repeatedly weakening incomplete robe lines.

The live spell direction is also now narrowing toward a smaller core book:

- elemental strike / bolt / blast / wave tiers
- a healing line
- teleports
- `Alchemy`
- `Iban Blast`
- the mage arena god spells

The remaining spell balance and unique-effect work should be done after that
smaller book is stable rather than by keeping low-value legacy spells around as
filler.

#### Open questions

- What in-world process best explains turning tweed into metal-tier cloth?
- Should robes use only flax/tweed plus metal infusion, or additional fibers?
- Which parts of magic equipment should remain pure `Crafting` outputs versus
  later `Enchanting` upgrades?
- Which altar/rune effects belong on robes once defense growth and effect
  family are separated?

### G-014: Add a dedicated tool equipment slot

- Status: `Planned`
- Priority: `Medium`

#### Summary

Add a dedicated `Tool` equipment slot so gathering and utility tools can be
carried without consuming normal inventory space or replacing the weapon slot.

#### Intended outcome

- Hatchets, pickaxes, and similar non-combat tools can eventually be equipped
  into a dedicated slot
- The `Tool` slot should not replace the main weapon slot
- Tool equip state should support skilling convenience without turning tools
  back into combat weapons
- Inventory-only tools can remain the interim behavior until the slot exists

#### Scope note

This is a UI, equipment-model, and content-compatibility change rather than a
simple item-def tweak.

It likely affects at least:

- equipment-slot definitions and packets
- client equipment UI
- wield/equip logic
- skilling systems that currently search inventory and equipment for tools
- future compatibility for non-gathering utility tools

#### Open questions

- Which tools should qualify for the slot besides hatchets and pickaxes?
- Should a player be able to keep both a combat weapon and a tool equipped at
  the same time by default?
- Should some tools remain inventory-only even after the slot exists?

### G-015: Make fishing spot and fishing-rod tier dependent

- Status: `Planned`
- Priority: `Medium`

#### Summary

Rework fishing so the main progression is driven by fishing-spot location plus
the quality tier of the fishing rod, instead of splitting catches across many
different bait/tool types and "wrong tool" spot variants.

#### Intended outcome

- Fishing rods become the main fishing tools, with rod quality/tier gating what
  the player can catch at a spot
- Rod tiers are level gated
- Fishing spots should no longer behave as hard "cannot fish here" branches
  just because the player brought the wrong fishing tool
- Spot location and rod tier together should determine the effective catch pool
- Many areas can retain their current broad catch identities while the spots
  inside those areas are expanded and cleaned up
- New fish can be added where needed to fill out the resource roster cleanly
- Legacy dependencies on bait, feathers, lobster pots, harpoons, nets, and
  similar fishing-specific tool splits can be reduced or removed over time

#### Scope note

This is a long-term gathering-system and resource-distribution goal, not a
small fishing-plugin tweak.

It likely affects at least:

- fishing spot definitions and catch tables
- fishing tool items and their progression tiers
- bait/feather/tool consumption rules
- tutorial, shop, and quest text that currently teaches or sells the old tool
  split
- future fish additions and regional resource identity
- any later tool-slot work if rods become equipable or otherwise standardized

#### Open questions

- How many rod tiers should exist, and how closely should they mirror the wood
  or metal-tier logic used elsewhere?
- Should all fishing catches ultimately route through rods, or should a small
  number of specialty exceptions remain?
- How should old bait- and feather-based catches be redistributed once the old
  consumable split is removed?
- Which areas need new fish to make the expanded spot model feel complete
  instead of just reshuffled?

### G-016: Repurpose Tutorial Island as late-game content

- Status: `Idea`
- Priority: `Medium`

#### Summary

New MyWorld characters should start on the mainland respawn point rather than
Tutorial Island. Tutorial Island should be preserved as a future late-game area
instead of being used as the new-player onboarding path.

#### Intended outcome

- First-login characters arrive at the configured mainland respawn point.
- Tutorial Island is not required for normal account progression.
- Existing tutorial-specific content can be audited later and either removed,
  disabled, or rebuilt into late-game content.
- The island should eventually become a deliberate destination with new
  late-game purpose, rewards, and access rules.

#### Current progress

- `server/myworld.conf` now enables `arrive_lumbridge`, so new MyWorld players
  start at the configured respawn point instead of Tutorial Island.

#### Open questions

- What late-game role should Tutorial Island fill: dungeon, challenge island,
  boss hub, skilling gauntlet, or quest area?
- How should players unlock access to the repurposed island?
- Which tutorial NPCs, objects, and scripts should be scrubbed versus reused?

### G-017: Remove fatigue, sleeping, and sleeping bags

- Status: `Done`
- Priority: `Medium`

#### Summary

MyWorld should not use the old fatigue/energy loop where players must sleep to
restore progression. Sleeping bags should not be part of the item economy or
normal player toolkit.

#### Intended outcome

- Fatigue remains disabled.
- Sleep interactions do not open the sleepword flow when sleep is disabled.
- Sleepwords are not loaded for MyWorld.
- Sleeping bags are not stocked by shops or handed out by tutorial/admin tool
  paths.
- XP control should be handled by explicit game/UI behavior, not bed or
  sleeping-bag interactions.

#### Current progress

- MyWorld config disables fatigue, sleep features, and sleepword loading.
- Sleep interaction handlers now no-op when the sleep feature is disabled.
- Sleeping bags were removed from known shop/tutorial/admin acquisition paths.

### G-018: Rework gathering around guaranteed success and scaling yield

- Status: `Planned`
- Priority: `Medium`

#### Summary

Rework resource gathering so completed actions always succeed, while skill level
and tool tier improve yield amount, yield weighting, speed, or resource quality.

#### Intended outcome

- Woodcutting, ore mining, and harvesting use depleting nodes with guaranteed
  success and scaling quantity.
- Stone mining uses the same yield model but remains non-depleting.
- Fishing removes failure but follows its separate rod tier, speed, spot, and
  fish-quality model.
- Hatchets, pickaxes, and rods add effective gathering level by tool tier.
- Pickaxes can eventually use the old combat-style selector as a mining focus
  selector for gem frequency.

#### Design document

- See [gathering-rework-plan.md](gathering-rework-plan.md).

### G-019: Overhaul Herblaw and potion effects

- Status: `Planned`
- Priority: `High`

#### Summary

Potion making, currently represented by Herblaw, needs a full audit and
overhaul because many legacy potion effects no longer match MyWorld's combat,
prayer, skilling, rune-cost, fatigue, and gathering changes.

#### Intended outcome

- Review every potion recipe, level gate, ingredient source, XP value, and
  resulting effect.
- Remove or replace potion effects that depend on retired systems such as
  fatigue, old prayer drain, old combat-stat assumptions, or obsolete rune and
  skilling behavior.
- Retire the old runecraft/enchanting potion line entirely instead of carrying
  skill-boost potions forward under a renamed skill.
- Decide whether the skill should remain named `Herblaw` or be reframed as
  alchemy/potion making in UI and documentation.
- Rebalance useful potions around MyWorld's current systems: allocated prayer
  points, combat role tuning, gathering yields, enchanting, crafting, and other
  in-game XP multipliers.
- Keep every standard herb relevant by mapping the unfinished herb ladder onto
  the new main potion line rather than leaving herbs stranded behind retired
  buff recipes.
- Treat `Torstol` as the super-tier catalyst/default top-end herb so base
  potions can upgrade cleanly without wasting the rest of the herb ladder.
- Audit secondary ingredients alongside the herb remap so common secondaries
  like eye of newt, unicorn horn, limpwurt, berries, scales, and wines retain a
  clear purpose.
- Add validation so potion effects cannot silently drift when adjacent systems
  are reworked.

#### Current ingredient direction

- Standard unfinished herb ladder should map onto the new base potion line:
  `Guam` -> `Cure Poison`, `Marrentill` -> `Insight`, `Tarromin` ->
  `Regeneration`, `Harralander` -> `Weapon Poison`, `Ranarr` -> `Speed`,
  `Irit` -> `Magic Resistance`, `Avantoe` -> `Melee Resistance`, `Kwuarm` ->
  `Ranged Resistance`, `Cadantine` -> `Luck`, `Dwarf weed` -> `Notation`.
- `Torstol` should remain the super-tier herb and be consumed when upgrading the
  five designated base potions into their super forms.
- Common legacy secondaries should be reassigned instead of discarded where
  possible so the item ecosystem stays useful:
  `Eye of newt`, `ground unicorn horn`, `limpwurt root`, `jangerberries`,
  `red spiders' eggs`, `white berries`, `snape grass`, `ground blue dragon
  scale`, and `wine of zamorak`.
- The retired runecraft/enchanting potion branch leaves `fish oil` without a
  main potion role; the harvesting potion branch leaves `wine of saradomin`,
  `sliced dragonfruit`, and `half coconut` in the same position. Those should
  be deliberately assigned to the new super line or repurposed elsewhere
  instead of being left as hidden dead ingredients.

#### Design document

- See [herblaw-ingredient-audit.md](herblaw-ingredient-audit.md).

### G-020: Normalize standard jewelry progression and audit gem/silver side lines

- Status: `Planned`
- Priority: `Medium`

#### Summary

Standard gold jewelry should follow the current MyWorld progression direction:
the live ring, necklace, and amulet line should fit comfortably into a 1-70
Crafting ladder. Crowns are excluded from this scale for now because they are
hidden legacy content pending a future redesign.

#### Intended outcome

- Rebalance standard no-gem through dragonstone ring, necklace, and amulet
  crafting requirements so no-gem rings start at level 1 and dragonstone
  amulets cap the line at level 70.
- Keep crowns out of the live scale unless they receive a new role and
  enchantment identity later.
- Audit opal, jade, and red topaz support before deciding whether they become
  part of the standard live jewelry ladder, stay special-case, or remain
  legacy/dormant.
- Audit silver crafting and silver jewelry separately, with emphasis on making
  silver more interesting without confusing it with the standard gold/gem
  jewelry line.
- Preserve the current altar-based jewelry enchantment direction while
  adjusting the underlying Crafting gates.

#### Scope note

This is a progression-normalization goal, not a request to redesign every
jewelry effect at once. Immediate work can safely adjust the standard gold
ring, necklace, and amulet requirements; opal/jade/topaz behavior, silver
identity, and crown redesign should be handled as follow-up design passes.
