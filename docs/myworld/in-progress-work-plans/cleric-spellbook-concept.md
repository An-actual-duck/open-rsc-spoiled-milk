# Cleric Spellbook Concept

## Status and Purpose

This is an active design document for a Worship-tiered Cleric spellbook. It is
not an implementation plan yet. Confirmed direction is recorded separately
from unresolved design questions, and the concept should not be treated as
finished until its owner confirms that the important details are resolved.

No spell list, numerical effect, production level, experience award, or
resource quantity is settled unless it appears under **Confirmed Direction**.
Ranges explicitly labelled as provisional tuning targets remain open even when
recorded beside an otherwise confirmed spell identity.

## Confirmed Direction

### Identity and Progression

- The Cleric spellbook is a support-focused counterpart to the Magic
  spellbook.
- Spell unlocks are tiered by the player's Worship level rather than Magic.
- Worship gates access to and progression through the spellbook; it does not
  gate production of the spellbook's resources and equipment.
- **Blessing** is a new production skill. It fills the relationship to Worship
  that Enchanting fills for Magic: Blessing levels gate the creation of sigils
  and blessed gear.
- Ordinary Cleric spells should be support spells. Direct combat spells are
  excluded except for the god-spell line.
- The initial rollout targets two support tiers, approximately the first half
  of the eventual spellbook, with progression extending through roughly
  Worship level `30`.
- The initial rollout contains `12` spells: six tier-one spells and six
  tier-two spells. Unlocks are staggered through the two level bands rather
  than placing all six spells at one level; exact levels remain open.
- The spellbook should create meaningful support and healing roles without
  replacing Magic's offensive and utility identity.
- Magic and Cleric remain simultaneously accessible. Players do not switch an
  exclusive active spellbook at an altar or through equipment.
- Simultaneous access does not mean equal simultaneous effectiveness. The
  design uses equipment, inventory, and targeting opportunity costs to
  encourage a player to specialize in Magic or Cleric support at a given time.
- Hybrid loadouts remain legal. They should be flexible but materially less
  effective and less inventory-efficient than a focused loadout.

### Magic/Enchanting Mirror

The established Magic and Enchanting relationship is the primary design guide
for the new Worship and Blessing relationship. It is a guide rather than a
requirement to copy every level, recipe, or effect one-to-one.

| Magic-side role | Cleric-side role | Settled ownership |
| --- | --- | --- |
| Magic | Worship | Gates and advances through the spellbook that consumes the crafted casting resource |
| Enchanting | Blessing | Gates production of casting resources and aligned equipment |
| Runes | Blessed sigils | Consumable spell resources |
| Enchanted equipment | Blessed equipment | Base gear transformed through the corresponding production skill |
| Elemental production identity | God-aligned production identity | Cleric production adds matching-god altars and Devotion |

Worship has a broader identity than Magic because it also owns the three
god-specific Devotion relationships. Devotion is an additional requirement and
economy for Blessing; it does not collapse Worship and Blessing back into one
skill.

### Sigils

- Cleric spells use new consumable resources called **sigils**, serving the
  spellbook role that runes serve for Magic.
- Saradomin, Guthix, Zamorak, and neutral sigils are **primary sigils**. Every
  Cleric spell requires the primary sigils associated with its god or neutral
  identity.
- Sigils begin with the same `Rune stone` resource used by Runecraft.
- Sigils have stone and advanced silver material families. A player uses a
  chisel on the relevant stone or silver input to carve the holy symbol into
  that material.
- Using the chisel opens the normal Crafting selection window. For each
  material family, the player chooses one of four sigils: Saradomin, Guthix,
  Zamorak, or neutral.
- Carving has a Crafting requirement.
- The carved sigil is then blessed by using it on the appropriate god altar.
- One sigil-on-altar `Use` interaction blesses all eligible matching carved
  sigils in the player's inventory, following the existing full-inventory rune
  production pattern rather than requiring one interaction per sigil.
- Silver is the material for more advanced sigils rather than an unrelated
  secondary casting reagent. Tier-two spells continue to use stone alongside
  silver rather than replacing the earlier material.
- Sigils are aligned to Saradomin, Guthix, or Zamorak. A Saradomin sigil can be
  blessed only at a Saradomin altar, and the same matching rule applies to the
  other two gods.
- The spellbook may also contain non-aligned support spells when an effect does
  not fit one god naturally. Those spells consume neutral sigils rather than
  being forced into a misleading god theme.
- A neutral carved sigil can be blessed at any Saradomin, Guthix, or Zamorak
  altar. The altar used determines which god's Devotion pays the blessing cost,
  but the resulting item remains one neutral sigil rather than retaining an
  altar-specific variant.
- Future **secondary sigils** may be added to individual spell recipes for
  flavor and balance. They supplement mandatory primary sigils rather than
  replacing the spell's god/neutral cost.
- The relationship is analogous to a tier-defining rune plus an elemental rune
  in Magic: the primary sigil establishes the Cleric tier and god identity,
  while a secondary sigil can later distinguish the spell's particular
  expression.
- Secondary sigils are outside the first rollout. Their names, materials,
  imbuing method, production skill requirements, and exact costs remain open.
- The player's currently selected worship alignment does not restrict sigil
  blessing. The relevant resource and eligibility checks use the god
  represented by the sigil and altar.
- Blessing is allowed while the corresponding god's Devotion is above `-1000`.
  A player at exactly `-1000` cannot bless that god's sigils.
- Blessing drains the corresponding god's Devotion.
- Each blessed sigil costs half the value generated by one ordinary offering:
  `0.05` displayed Devotion per sigil. A full 30-sigil inventory therefore
  costs exactly `1.5` Devotion.
- Sigil blessing prepays the Devotion component of casting. Casting consumes
  the required blessed sigils but does not drain Devotion again.

### Staves and Holy Power

- The existing blessed-staff progression should become relevant to the Cleric
  spellbook.
- Blessed staves and god staves gain a **Holy Power** stat.
- Ordinary Magic staves have no Holy Power.
- The existing blessed-staff line, culminating in god staves, has weaker Magic
  Power than comparable Magic-specialized staves. A player therefore cannot
  reach full Magic and Cleric effectiveness with one staff.
- Holy Power determines healing effectiveness and may scale other suitable
  support effects.
- Holy Defense will not be introduced.
- God spells and any associated offensive holy spells continue to use Magic
  Power and Magic Defense. Damage authority and offensive spell identity stay
  in the Magic combat model.
- `Holy staff` is only descriptive shorthand for the existing blessed-staff
  and god-staff progression. It is not a separate neutral equipment family.
- God staves remain below comparable dedicated Magic staves in Magic Power.
  Their required offensive god spells therefore retain a deliberate equipment
  compromise rather than creating a best-in-both-books staff.

### Soft Specialization Pressures

The separation between the books is enforced through tradeoffs rather than a
hard class, book-selection, or equipment lock:

- A Magic-focused staff contributes Magic Power but no Holy Power.
- A blessed or god staff contributes Holy Power but less Magic Power than the
  corresponding Magic-focused option.
- Runes and sigils are separate inventory resources. Carrying meaningful
  supplies for both books consumes enough inventory space to be a real cost.
- Higher Cleric tiers retain every lower sigil-material stack, creating an
  intentional additional inventory commitment as the book becomes stronger.
- Most Cleric spells support other players rather than the caster. The book has
  limited self-targeted support, making it intentionally less attractive as a
  second solo-combat book.
- The goal is to make dedicated Cleric support valuable in group play without
  preventing experimentation or situational hybrid builds.

### Area-of-Effect Casting Contract

- Cleric spells are centered on the casting player and affect an area rather
  than requiring the caster to select each recipient.
- Every Cleric spell is area-of-effect unless its definition explicitly says
  otherwise.
- The caster is excluded from the affected players unless the individual
  spell explicitly permits self-application.
- Beneficial area effects apply only to eligible members of the caster's
  current party. Nearby non-party players are not affected merely because
  they are standing in the area.
- The standard tier-one area has a radius of `2` tiles around the caster.
- Standard area grows by exactly one tile of radius per spell tier: tier two
  reaches `3` tiles, tier three reaches `4`, and tier `N` reaches `N + 1`.
  The precise tile-distance metric remains an implementation decision.
- `Unify` deliberately has a larger area than other spells in its tier so it
  can gather eligible players before follow-up support casts.
- Recipients must occupy the caster's current world space and signed map level.
  Cross-layer or cross-world support is never implied by matching X/Y
  coordinates.

### God Support Identities

The Cleric book is shared rather than split into three exclusive god books.
Spells may still have thematic god ownership, which determines their aligned
sigil costs and gives each Devotion economy a distinct purpose.

- **Saradomin:** healing and protection.
- **Guthix:** cleansing, restoration, and balance.
- **Zamorak:** buffs and ally empowerment, with an offensive or forceful tone
  but without turning the normal Cleric catalog into direct-damage magic.
- **Non-aligned:** support effects that do not fit a god cleanly. Neutral
  design is preferable to assigning a misleading theme merely to fill a god's
  quota.

Sacrifice or health-exchange mechanics are not part of Zamorak's assumed
identity. They may not fit RSC's combat and support model well and should not be
used as a default design pattern.

### Initial Spell Roster

All twelve launch slots now have confirmed identities and effect directions.
Numerical ranges explicitly described as tuning targets below remain
provisional until separately approved; the confirmed Mend and Greater Mend
bands and fixed Ward/Aegis reductions are exceptions.

| Tier | Spell | Identity | Confirmed effect direction |
| ---: | --- | --- | --- |
| 1 | Mend | Saradomin | Three-pulse regeneration heal, scaling from `1` to `3` Hits per pulse with Holy Power (`3-9` total healing before healing-ceiling limits) |
| 1 | Unify | Neutral | Uses an enlarged radius and draws eligible same-party players closer to the caster to set up later area support |
| 1 | Fervor | Zamorak | Timed accuracy support applied before enemy defense mitigation; Holy Power increases the strength of the upward roll bias |
| 1 | Purify | Guthix | Reduces current poison power rather than fully curing every poison; current target range is approximately `10-40` power from Holy Power |
| 1 | Restore | Guthix | Restores reduced combat stats toward their normal maximum without boosting them; current Holy Power target is approximately `10-60%` of each maximum |
| 1 | Ward | Saradomin | Reduces each qualifying protected hit by a fixed `25%`; its four Holy Power ranks protect against `2/4/6/8` hits |
| 2 | Greater Mend | Saradomin | Three-pulse regeneration heal staggered above Mend, scaling from `2` to `5` Hits per pulse with Holy Power (`6-15` total healing before healing-ceiling limits) |
| 2 | Zeal | Zamorak | Timed percentage increase to damage after enemy defense has been applied; Holy Power selects its strength |
| 2 | Thorns | Guthix | Weak recoil placed on affected players; Holy Power increases reflected damage |
| 2 | Aegis | Saradomin | Reduces each qualifying protected hit by a fixed `50%`; its four Holy Power ranks protect against `1/2/3/4` hits and require higher thresholds than Ward |
| 2 | Rally | Zamorak | Players below half Hits gain temporary lifesteal until they recover above a Holy Power-dependent threshold |
| 2 | Respite | Neutral | Long-lived, modest increase to normal passive regeneration; it is not restricted to out-of-combat periods |

The spell directions intentionally scale back immediate power in exchange for
covering several nearby allies. Mend and Greater Mend are regeneration effects,
not large instant heals. Purify leaves room for later full poison cleansing,
and Restore is the only planned spell in its restoration line rather than the
first of repeated stronger copies.

### Protection Stacking Contract

Ward and Aegis must not turn prayer protection into complete damage immunity.
Their reduction magnitudes are fixed: Ward reduces a qualifying hit by `25%`
and Aegis reduces one by `50%`. Holy Power changes the number of protected
hits, not either spell's percentage.

| Effect rank | Ward charges | Aegis charges |
| ---: | ---: | ---: |
| I | 2 | 1 |
| II | 4 | 2 |
| III | 6 | 3 |
| IV | 8 | 4 |

Ward reaches each rank at a lower Holy Power threshold than Aegis. The raw
thresholds remain open until Holy Power values are assigned across the blessed
staff and god-staff progression.

Percentage mitigation combines in two layers:

1. The prayer system computes its normal aggregate reduction. Multiple prayer
   contributions continue to combine additively inside that existing system.
2. A Cleric protection effect reduces the damage remaining after prayer as an
   independent multiplier. Its percentage is never added to the prayer total.

For example, a `60%` aggregate melee-prayer reduction leaves `40%` of the
incoming damage. If illustrating the stacking rule with a separate `40%`
Cleric reduction, that layer leaves `60%` of the remainder:
`0.40 * 0.60 = 0.24`, or `76%` combined reduction. With the now-confirmed
`50%` Aegis value, the same prayer instead leaves `0.40 * 0.50 = 0.20`, or an
`80%` combined reduction. Other mitigation families should likewise retain
clear ownership rather than being folded into one uncapped additive
percentage.

This contract settles percentage composition but not integer rounding,
minimum-damage behavior, eligible damage sources, or which layer consumes a
Ward/Aegis charge. Those details must be specified and regression-tested before
implementation, especially for small hits where sequential rounding can
change the result by one.

### Exclusive Effect Families

- Ward and Aegis occupy one protection-effect slot on each recipient. They can
  never be active together, and a new cast never adds its charges to the
  charges already present.
- Mend and Greater Mend occupy one three-pulse healing-effect slot on each
  recipient. Repeated casts and the two spell tiers cannot create overlapping
  pulse streams or add their remaining pulses together.
- Exclusivity is evaluated independently for each eligible party recipient of
  an area cast.
- A higher spell tier replaces a lower spell tier in the same family. Within
  one spell, a higher Holy Power effect rank replaces a lower rank. A lower
  tier or lower rank cannot overwrite the stronger active effect.
- An equal effect refreshes to its full charge count or restarts its complete
  three-pulse sequence. Refreshing never adds remaining charges or pulses to
  the new full amount.
- These rules mean Aegis can replace Ward, Greater Mend can replace Mend, and
  neither replacement works in reverse while the higher-tier effect remains
  active.
- Respite is mechanically separate and may coexist with a Mend-family effect.
  It modifies only ordinary passive regeneration and never increases,
  accelerates, or duplicates Mend or Greater Mend pulses.

### Effect Duration, Lifecycle, and Presentation

- Every non-instant Cleric effect has a bounded server-authoritative lifetime.
  The earlier fixed `60`-second suggestion is superseded.
- Holy Power snapshots one discrete effect rank at cast time. That rank owns
  every scalable part of the status, including its duration and, where
  applicable, its charges or pulse strength. Duration does not continue to
  change if the caster later changes equipment.
- A charge-based status ends when either its timer expires or its charges reach
  zero. A Mend-family status ends when its three-pulse sequence finishes or its
  timer expires.
- Active Cleric effects are transient session state. They clear when the
  recipient dies or logs out and are not restored on the next login.
- An effect also clears if its recipient no longer shares the originating
  caster's party. This includes the caster leaving or logging out and prevents
  joining a party only long enough to collect support buffs.
- Timers, charges, and remaining Mend pulses must be visible on the affected
  player's screen. Presentation extends the existing active-potion-effect HUD:
  an identifying icon, a countdown, a hover label, and an optional remaining
  charge/pulse count. Cleric effects do not introduce an unrelated second
  status overlay.
- The server remains authoritative. The client may count a supplied timer down
  for presentation, but a refreshed, consumed, replaced, or cleared status
  causes the server to send a new bounded snapshot.
- Exact duration tables are authored per effect rank. Sharing the rank model
  does not require every spell to share one duration ladder; long-lived Respite
  and short tactical protection may use different values while still deriving
  them from that spell's authored effect ranks.

### Devotion Economy

- Sigil blessing creates a new repeatable Devotion sink.
- Full-inventory blessing is expected to be a frequently repeated production
  loop, so the individually small cost is intended to accumulate over time.
- The design must add or expand ways to gain Devotion alongside this sink.
- Devotion acquisition and expenditure must remain god-specific; blessing one
  god's sigil checks and drains only that god's Devotion.

### Blessed-Equipment Skill Ownership

- Crafting, Smithing, or the relevant base-production skill creates the
  ordinary item.
- Blessing level and the appropriate god's Devotion gate conversion into
  blessed equipment.
- Successful altar conversion awards Blessing XP rather than Worship XP.
- Existing Worship production-level requirements for blessed equipment move to
  Blessing. Worship remains the spellbook skill and may gate the use of blessed
  equipment where appropriate to the item's support identity.
- Exact Blessing and Worship requirements remain tier-by-tier balance work;
  the ownership split itself is settled.

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
- Existing rune production accepts one altar interaction and processes the
  available inventory in one action. Sigil blessing intentionally adopts that
  interaction model.
- The current Magic definition contains `46` spells, including `26` at or below
  Magic level `30`. Much of that density comes from repeated elemental attack
  families and teleports; the Cleric launch catalog intentionally favors `12`
  distinct support functions rather than mirroring that repetition.
- The server has an active party system and already updates party health and
  combat state.
- Active prayers of the same combat style currently contribute their effect
  percentages to one additive prayer total. Combat damage applies that total
  once with integer floor rounding. Other current mitigation families, such as
  potion resistance, are applied separately in relevant combat paths. A later
  Cleric implementation must remain a separate multiplier rather than adding
  Ward or Aegis to the prayer accumulator.
- Current player-targeted spell packets flow through a hostile/PvP handler and
  are rejected when PvP is disabled. Existing heal spells are self-cast. The
  caster-centered Cleric area action should not reuse, weaken, bypass, or
  overload that hostile player-target path.
- Existing shared mechanics can heal up to the player's valid healing ceiling,
  restore reduced stats up to their normal levels, and cure poison. Those are
  available implementation building blocks, not automatic spell selections.
- Poison is represented as integer power, drains by `3` per poison pulse, and
  is cured when it falls below `10`. Purify can therefore reduce that same
  authoritative power while preserving a meaningful distinction from a full
  cure.
- Timed potion effects already use an integer magnitude plus an expiry, while
  several combat effects use an integer magnitude plus remaining attack count.
- The custom client's active-potion HUD currently displays an item-definition
  icon, a countdown, and the item name on hover. Its server snapshot is bounded
  to `16` entries and carries only `itemId` plus `remainingSeconds`; it has no
  charge field. Cleric presentation therefore needs a backward-compatible
  optional count and a deliberate policy for a full status list rather than
  silently hiding an active effect. Authentic clients do not receive this
  custom packet, so gameplay state must never depend on HUD support.
- The current damage roll already supports a chance to shift an offense roll
  one step upward before the defense roll is subtracted. This is a useful
  balance reference for Fervor, not a final Cleric formula.
- Recoil and lifesteal behavior already exist, but their hooks span melee,
  ranged/projectile, poison, and other damage paths. Thorns and Rally will need
  explicit shared eligibility and attribution rules across applicable styles.
- Normal Hits regeneration restores one point about every `100` game ticks
  before existing equipment and potion speed modifiers. It is not inherently
  limited to out-of-combat periods, which fits the revised Respite direction.

## Holy Power Effect Rank Model

The confirmed direction is to map Holy Power into a small number of discrete
effect ranks instead of persisting arbitrary floating-point strength. For
example, low Holy Power could apply `Aegis`, while crossing the next threshold
applies `Aegis II` with two protected hits. Percentage effects advance through
spell-specific authored values rather than a universal `5%` ladder. Earlier
percentage steps were illustrations only; each spell's thresholds and values
must be tuned for its duration, area, resource cost, and interaction with
existing systems.

This approach fits existing server patterns and has several advantages:

- the effect shown to a player has an understandable name and strength;
- each rank has an explicit, testable integer magnitude;
- durations and remaining-hit charges can use existing integer state shapes;
- balance changes edit a small rank table rather than continuous formulas; and
- server authority does not depend on client rounding or floating-point
  display.

The recommended implementation shape for later review is one stable effect
identity with `rank`, `magnitude`, `expiresAt`, and an optional
`chargesRemaining` or `pulsesRemaining`. Holy Power selects and snapshots the
rank when the spell is cast. Separate spell definitions for `Aegis`,
`Aegis II`, and `Aegis III` would duplicate one mechanic and should be avoided;
the rank belongs to the applied status.

The discrete rank, snapshot, replacement, lifecycle, and presentation rules
are settled. Spell-specific rank counts, Holy Power thresholds, numerical
values, durations, icons, and final protocol/data structures remain balance
and implementation design work except where this document explicitly confirms
them.

## Unresolved Design Questions

### Casting and Spellbook Contract

- Does casting award Worship experience, and if so, should healing/support XP
  depend on a successful useful effect rather than merely attempting a cast?
- Are Cleric spells available to all players who meet the Worship requirement,
  or is there an introductory unlock or quest?

### Sigil Taxonomy and Production

- Exact input form and production quantity for silver sigils.
- The third and later material identities. Gold remains possible, but the
  current preference is to expand silver through an enhanced form rather than
  immediately changing to a wholly separate precious metal.
- The eventual secondary-sigil catalog and how those lesser symbols are
  imbued. No secondary-sigil production path should be invented for the first
  rollout.
- Exact Crafting levels, Crafting XP, Blessing XP, silver quantities, batch
  behavior, failure behavior, and inventory transformations, including whether
  any Worship XP remains part of production.
- Whether carving with a chisel produces one sigil at a time or supports an
  explicit quantity/batch flow after the player makes a selection in the
  Crafting window. Altar blessing itself is already settled as a
  full-inventory action for the selected sigil.

### Blessing Skill and Exact Accounting

- Blessing's level curve, experience sources, production XP, cape, potion,
  guild or training support, and placement in skill interfaces.
- How carving Crafting XP and successful altar-conversion Blessing XP divide
  the total production reward.
- Exact accounting for odd batch sizes. The present Devotion store uses whole
  offering units, while the settled sigil cost is half of one such unit. The
  later implementation must preserve the exact cumulative price without
  rounding away every odd sigil and without changing the visible three-god
  Devotion balances unexpectedly.

### Holy Power and Support Rules

- Whether a staff is required to cast, merely recommended through stronger
  effects, or optional for non-healing utility.
- Holy Power values for each blessed-staff and god-staff tier.
- The healing formula, minimum effect without a staff, caps, target rules,
  range, cooldowns, and behavior at full health.
- Which non-healing effects may scale with Holy Power and which should have a
  fixed effect to avoid mandatory staff swapping.
- Per-spell self-application exceptions, party membership changes during an
  effect, PvP behavior, experience attribution, and abuse safeguards. The
  general recipient rule is settled as same-party only at cast time.
- Exact area geometry, line-of-sight/path requirements, Unify's bonus radius,
  and how Unify selects safe reachable destination tiles without
  forced-movement abuse. Standard tier radii are settled by the `N + 1` rule.
- Holy Power threshold tables and discrete ranks for each scalable effect.
- Cast-level resource and experience behavior when an area spell applies to
  only some recipients or is wholly ineffective because every recipient has a
  stronger active family effect.
- Exact per-rank duration tables, status icons and labels, optional-count
  packet representation, compatibility behavior, and priority/overflow rules
  for the existing `16`-entry HUD bound.
- Which damage sources consume Aegis charges, trigger Thorns, receive Zeal,
  and provide Rally lifesteal.
- Whether Rally's Holy Power-dependent percentage controls lifesteal strength,
  its ending health threshold, or both.
- How Respite combines with existing regeneration potions, robes, amulets, and
  passive-healing timing.

### Initial Spell Content

- Exact unlock levels for the six tier-one and six tier-two spells through
  Worship `30`.
- The exact Holy Power thresholds for the confirmed Ward and Aegis charge
  tables. Ward must reach each corresponding rank with less Holy Power than
  Aegis.
- The exact distribution of Saradomin, Guthix, Zamorak, and non-aligned spells
  in each early tier. The book is shared, but god identities remain distinct.
- Placement and unlock rules for god spells, including their continued Magic
  requirements and staff requirements.

## Cumulative Sigil Cost Ladder

Spell tiers retain and increase all earlier sigil materials. A tier does not
replace the tier below it.

| Spell tier | Required sigils per cast | Total sigils | Devotion embodied when blessed |
| --- | --- | ---: | ---: |
| 1 | `1` stone | 1 | `0.05` |
| 2 | `2` stone + `1` silver | 3 | `0.15` |
| 3 | `3` stone + `2` silver + `1` tier-three sigil | 6 | `0.30` |

The general tier-`N` pattern is `N` of the first material, `N - 1` of the
second, continuing upward until `1` of the newest material. This gives a total
cast cost of `N * (N + 1) / 2` sigils. At the settled blessing price, the
Devotion embodied in one cast is that total multiplied by `0.05`.

The ladder settles material quantities, not the symbol mixture within an
individual exceptional recipe. By default, every material in a spell's primary
cost uses the same alignment: Saradomin spells use Saradomin primary sigils,
Guthix uses Guthix, Zamorak uses Zamorak, and non-aligned spells use neutral.
An authored mixed-theme spell may depart from this only when the exceptional
cost is intentional and displayed explicitly.

Only tiers one and two are part of the initial rollout. Tier three documents
the accepted progression formula but does not settle its material, spells, or
release timing.

The first rollout uses only these primary costs. Later secondary sigils are
additional ingredients and do not alter or substitute for the cumulative
primary ladder.

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
- Current blessed-equipment code and plans use Worship requirements and
  Worship XP for some production actions. The confirmed Blessing ownership
  supersedes that production responsibility for this concept and requires a
  later, focused synchronization; it does not authorize runtime changes on
  this documentation branch.
- Any finer fractional Devotion accounting must preserve existing saves and
  the three independent god balances.
- A new Blessing skill affects skill identity, persistence, protocol, client
  layout, highscores, guides, commands, XP modifiers, and combat-level audits.
  Those surfaces must be inventoried before implementation rather than
  treating the skill as only a new server constant.

## Decision Log

### 2026-08-02: Initial concept foundation

Recorded the Worship-tiered support identity, sigil production loop, matching
god-altar blessing rule, permissive alignment threshold above `-1000`, planned
silver use, Holy Power on blessed and god staves, continued Magic combat stats
for god spells, two-tier initial rollout through roughly level `30`, and need
for expanded Devotion acquisition.

### 2026-08-02: Batch cost and Blessing skill

Confirmed that one sigil costs half the Devotion value of one ordinary
offering (`0.05` displayed Devotion), making a 30-sigil inventory cost `1.5`
Devotion. One `Use` interaction blesses the full eligible inventory. Blessing
prepays Devotion so casting does not charge it again. Added Blessing as a new
production skill that gates sigil and blessed-gear creation, while Worship
gates the Cleric spellbook itself.

### 2026-08-02: Magic/Enchanting mirror and equipment ownership

Adopted Magic/Enchanting as the design guide for Worship/Blessing without
requiring one-to-one content. Worship owns spellbook progression and retains
the additional Devotion system. Blessing owns sigil and blessed-equipment
production gates and XP. Relevant base skills still make the ordinary gear;
Worship may gate use, but no longer gates or receives XP from its altar
conversion.

### 2026-08-02: Simultaneous books with soft specialization

Confirmed that Magic and Cleric are always accessible together. Focused role
strength comes from tradeoffs: Magic staves have no Holy Power, holy staves
trade away Magic Power, carrying both runes and sigils strains inventory, and
most Cleric support is aimed at other players rather than the caster. Hybrid
play remains allowed but is intentionally less effective than specialization.

### 2026-08-02: Staff terminology and god-staff boundary

Clarified that `holy staff` was shorthand, not a new family. The existing
blessed-staff progression culminates in god staves. All receive Holy Power,
but even god staves retain less Magic Power than comparable dedicated Magic
staves; offensive god spells continue to use Magic Power and Magic Defense.

### 2026-08-02: Shared book with god support identities

Selected a shared Cleric book whose spells may have thematic ownership:
Saradomin healing/protection, Guthix cleansing/restoration/balance, and Zamorak
buffs/ally empowerment. Non-aligned spells are welcome when no god is a natural
fit. Sacrifice is not a default Zamorak mechanic because it is a poor fit for
the expected RSC support model.

### 2026-08-02: Four-symbol Crafting selection and neutral blessing

Confirmed four carve choices for each sigil material: Saradomin, Guthix,
Zamorak, and neutral. Using a chisel on stone or silver opens the normal
Crafting selection window. Aligned sigils retain matching-altar rules; neutral
sigils can be blessed at any god altar, charge that altar's god Devotion, and
remain one neutral item regardless of the altar used.

### 2026-08-02: Cumulative material and quantity progression

Confirmed that higher spell tiers retain all prior sigil materials with
staggered quantities: tier one costs `1` stone; tier two costs `2` stone and
`1` silver; tier three costs `3` stone, `2` silver, and `1` of its new
material. Later tiers continue the same descending quantity ladder. The
tier-three material remains open, with an enhanced use of silver preferred
over introducing gold without further design work.

### 2026-08-02: Primary and deferred secondary sigils

Confirmed that god/neutral sigils are mandatory primary resources and that all
materials in a normal primary cost share the spell's alignment. Future
secondary sigils may add spell-specific identity and balance in the way an
elemental rune supplements a tier-defining Magic rune. The first rollout has
no secondary sigils; their imbuing and production model remains unresolved.

### 2026-08-02: Twelve-spell initial catalog

Confirmed `12` initial spells, divided evenly into six tier-one and six
tier-two unlocks staggered through approximately Worship level `30`. The count
is intentionally smaller than Magic's level-30 catalog so each launch spell can
serve a distinct support purpose rather than repeating an elemental template.

### 2026-08-02: Caster-centered area support and revised roster

Confirmed caster-centered area casting as the default, an initial standard
tier-one radius of `3`, growth with later tiers, and exclusion of the caster
unless a spell explicitly opts in. This initial radius was superseded by the
fixed radius ladder recorded below. Replaced Discern with the enlarged-radius
Unify and Renewal with Thorns. Defined Mend/Greater Mend regeneration, partial
Purify, percentage Restore, roll-biased Fervor, post-defense Zeal,
charge-ranked Aegis, low-health Rally lifesteal, and long-lived
in-combat-compatible Respite. The former Ward slot and exact numerical tuning
were open at this point.

Recorded discrete Holy Power effect ranks as the preferred model for further
exploration because current timed and attack-count status systems already use
integer magnitudes, expiry times, and charges.

### 2026-08-02: Party eligibility, healing bands, and mitigation composition

Confirmed that caster-centered support affects same-party players only, with
the caster still excluded unless a spell explicitly opts into self support.
Confirmed three-pulse healing bands of `1-3` Hits per Mend pulse and `2-5`
Hits per Greater Mend pulse.

Restored Ward as the sixth tier-one spell and positioned it as the lesser
Saradomin protection spell. The initial `25-40%` range recorded here was
superseded by the fixed protection values below. Cross-system percentage
protection is multiplicative: prayer effects keep their current additive
behavior within the prayer system, while Ward/Aegis applies separately to the
remaining damage. Exact integer rounding, Ward charges, and the final Aegis
magnitude were still open at this point.

Clarified that discrete status ranks use values authored for each effect. The
earlier `5%` examples were explanatory rather than a global progression rule;
final rank tables remain balance work.

### 2026-08-02: Fixed protection strengths and spell-tier radii

Confirmed fixed mitigation strengths of `25%` for Ward and `50%` for Aegis.
Holy Power increases only their protected-hit charges. Ward reaches additional
charges with less Holy Power and has the higher charge ceiling, making it the
cheaper, longer-lived defense. Aegis provides stronger protection against each
hit but costs the tier-two sigil vector and must be reapplied more frequently.

Confirmed that area grows with the spell's tier rather than its Holy Power or
effect rank. Standard tier-one spells reach `2` tiles from the caster and each
later spell tier adds one tile, giving the general radius `tier + 1`. Unify
retains an intentionally larger area, with its exact bonus still unresolved.

### 2026-08-02: Ward and Aegis charge progression

Confirmed four charge ranks for both protection spells. Ward protects against
`2/4/6/8` qualifying hits at its fixed `25%` reduction. Aegis protects against
`1/2/3/4` qualifying hits at its fixed `50%` reduction. Ward reaches its ranks
at lower Holy Power thresholds, while Aegis concentrates comparable cumulative
mitigation into fewer, more heavily reduced hits. Raw thresholds remain tied
to the future Holy Power equipment-stat design.

### 2026-08-02: Exclusive protection and Mend families

Confirmed that Ward and Aegis share one mutually exclusive protection slot.
They cannot coexist or accumulate charges. Confirmed the same non-stacking
rule for Mend and Greater Mend: a recipient can have only one active
three-pulse Mend-family effect, and repeated casts cannot create concurrent or
additive pulse streams. Replacement, refresh, and Respite-interaction rules
remain open.

### 2026-08-02: Effect replacement hierarchy

Confirmed deterministic replacement within the two exclusive families. A
higher spell tier replaces a lower tier, and a higher Holy Power rank replaces
a lower rank of the same spell. Lower effects cannot overwrite stronger ones.
An equal effect refreshes to its normal full charges or complete three-pulse
sequence without adding the old remainder. Respite may coexist with Mend or
Greater Mend but affects only ordinary passive regeneration, never the active
Mend-family pulses.

### 2026-08-02: Ranked duration and status presentation

Confirmed that non-instant Cleric effects have bounded durations selected by
the same snapshotted Holy Power effect rank that selects their strength and
charges. Charge effects end on timeout or charge exhaustion. Mend effects end
after three pulses or timeout. Effects clear on recipient death/logout and
when the source and recipient stop sharing a party; they are not persisted
through login.

Confirmed that affected players see Cleric timers and remaining charges or
pulses through an extension of the existing active-potion-effect HUD. The
present custom packet carries only an item ID and seconds remaining, so later
implementation must add optional count presentation compatibly, keep gameplay
server-authoritative, and define overflow behavior for its bounded list.
