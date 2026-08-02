# Cleric Spellbook Concept

## Status and Purpose

This is an active design document for a Worship-tiered Cleric spellbook. It is
not an implementation plan yet. Confirmed direction is recorded separately
from unresolved design questions, and the concept should not be treated as
finished until its owner confirms that the important details are resolved.

No spell list, numerical effect, production level, experience award, or
resource quantity is settled unless it appears under **Confirmed Direction**.

## Confirmed Direction

### Identity and Progression

- The Cleric spellbook is a support-focused counterpart to the Magic
  spellbook.
- Spell unlocks are tiered by the player's Worship level rather than Magic.
- Ordinary Cleric spells should be support spells. Direct combat spells are
  excluded except for the god-spell line.
- The initial rollout targets two support tiers, approximately the first half
  of the eventual spellbook, with progression extending through roughly
  Worship level `30`.
- The spellbook should create meaningful support and healing roles without
  replacing Magic's offensive and utility identity.

### Sigils

- Cleric spells use new consumable resources called **sigils**, serving the
  spellbook role that runes serve for Magic.
- Sigils begin with the same `Rune stone` resource used by Runecraft.
- A player uses a chisel on the stone to carve a holy symbol into it.
- Carving has a Crafting requirement.
- The carved sigil is then blessed by using it on the appropriate god altar.
- More advanced sigils incorporate silver.
- Sigils are aligned to Saradomin, Guthix, or Zamorak. A Saradomin sigil can be
  blessed only at a Saradomin altar, and the same matching rule applies to the
  other two gods.
- The player's currently selected worship alignment does not restrict sigil
  blessing. The relevant resource and eligibility checks use the god
  represented by the sigil and altar.
- Blessing is allowed while the corresponding god's Devotion is above `-1000`.
  A player at exactly `-1000` cannot bless that god's sigils.
- Blessing drains the corresponding god's Devotion.
- The intended blessing cost is expressed as **0.5 of an offering per sigil**.
  The exact accounting interpretation remains unresolved below because the
  current game distinguishes stored offering units from displayed Devotion.

### Staves and Holy Power

- The existing blessed-staff progression should become relevant to the Cleric
  spellbook.
- Blessed staves and god staves gain a **Holy Power** stat.
- Holy Power determines healing effectiveness and may scale other suitable
  support effects.
- Holy Defense will not be introduced.
- God spells and any associated offensive holy spells continue to use Magic
  Power and Magic Defense. Damage authority and offensive spell identity stay
  in the Magic combat model.

### Devotion Economy

- Sigil blessing creates a new repeatable Devotion sink.
- The design must add or expand ways to gain Devotion alongside this sink.
- Devotion acquisition and expenditure must remain god-specific; blessing one
  god's sigil checks and drains only that god's Devotion.

## Verified Repository Context

These are current implementation facts, not new design decisions:

- `Worship` is the player-facing name of stable skill identity `Prayer` / skill
  ID `5`. Save, database, protocol, and compatibility-facing identifiers still
  use Prayer where required.
- Devotion is stored independently for Saradomin, Guthix, and Zamorak in
  integer **offering units**.
- One ordinary offering currently adds one stored offering unit. Ten stored
  offering units equal one displayed Devotion level.
- Current storage supports signed Devotion from `-1000` through `1000`.
- Existing blessing transactions can charge stored offering units and already
  protect inventory replacement and Devotion deduction as one transaction.
- `Rune stone` is existing item ID `1299`; it is mined from raw essence and is
  the base input for current Runecraft.
- The blessed wood-staff line currently contains ten base tiers, from the
  ordinary staff through the blood staff, with variants for all three gods.
- Current altar identity is centralized by god line rather than requiring new
  coordinate lists in each content plugin.

## Unresolved Design Questions

### Casting and Spellbook Contract

- Does casting consume sigils only, or does it also spend Devotion at cast
  time?
- Is the Cleric book available alongside Magic, or must players switch active
  spellbooks? If it is switched, where and under what conditions?
- Does casting award Worship experience, and if so, should healing/support XP
  depend on a successful useful effect rather than merely attempting a cast?
- Are Cleric spells available to all players who meet the Worship requirement,
  or is there an introductory unlock or quest?

### Sigil Taxonomy and Production

- Which carved symbols exist, which spells consume them, and whether spells
  use one sigil type or combinations.
- Whether every support effect has three god variants or whether some sigils
  are god-specific while the resulting spells remain shared.
- Exact Crafting levels, Crafting XP, Worship XP, silver quantities, batch
  behavior, failure behavior, and inventory transformations.
- Whether a chisel produces one carved sigil at a time or supports an explicit
  quantity/batch flow.

### Meaning of the 0.5-Offering Cost

The current resource model makes two readings materially different:

1. **Half of one offering action:** two sigils consume the Devotion value
   generated by one ordinary offering. Current integer storage cannot deduct
   this independently for every single sigil without finer accounting,
   batching, or a carried remainder.
2. **Half of one displayed Devotion:** one sigil costs five current offering
   units, equivalent to five ordinary offerings. This fits current accounting
   but is a much larger material cost.

The intended reading must be confirmed before production rates or spell costs
can be balanced.

### Holy Power and Support Rules

- Whether a staff is required to cast, merely recommended through stronger
  effects, or optional for non-healing utility.
- Holy Power values for each blessed-staff and god-staff tier.
- The healing formula, minimum effect without a staff, caps, target rules,
  range, cooldowns, and behavior at full health.
- Which non-healing effects may scale with Holy Power and which should have a
  fixed effect to avoid mandatory staff swapping.
- Self-casting, other-player targeting, party/group interaction, PvP behavior,
  experience attribution, and abuse safeguards.

### Initial Spell Content

- Exact tier breakpoints through Worship `30`.
- The number and categories of launch spells: direct healing, healing over
  time, cleansing, restoration, protection, travel, information, resource
  support, or other utility.
- Whether the three gods share the early support catalog or express distinct
  support identities.
- Placement and unlock rules for god spells, including their continued Magic
  requirements and staff requirements.

### Devotion Sources and Balance

- Additional active, passive, repeatable, and limited Devotion sources.
- How acquisition scales against expected sigil consumption without making
  ordinary offerings irrelevant.
- Safeguards against farming one cheap Devotion source or blessing enormous
  stockpiles without meaningful cost.
- How negative, neutral, high, capped, and permanently locked Devotion states
  interact with production.

## Compatibility Boundaries

- Player-facing text should say Worship; stable internal Prayer identifiers
  should not be renamed casually.
- Cleric support scaling must not introduce Holy Defense or silently reroute
  offensive god spells away from Magic Power and Magic Defense.
- Existing prayer allocation, god alignment, Devotion, blessed-equipment, and
  god-relic plans must be reconciled before implementation.
- Any finer fractional Devotion accounting must preserve existing saves and
  the three independent god balances.

## Decision Log

### 2026-08-02: Initial concept foundation

Recorded the Worship-tiered support identity, sigil production loop, matching
god-altar blessing rule, permissive alignment threshold above `-1000`, planned
silver use, Holy Power on blessed and god staves, continued Magic combat stats
for god spells, two-tier initial rollout through roughly level `30`, and need
for expanded Devotion acquisition.
