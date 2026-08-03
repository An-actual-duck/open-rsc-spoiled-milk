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
- A Cleric spell does not require a holy staff to cast. With no equipped
  blessed or god staff, the caster has `0` Holy Power and receives only the
  lowest available effect rank.
- The three god-aligned blessed-staff lines share the same Holy Power ladder.
  Any of them empowers any ordinary Cleric support spell; staff alignment does
  not split the shared book or require three staff swaps. Matching-staff rules
  remain available for offensive god spells.
- Blessed armor does not grant Holy Power in the initial rollout.
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

The confirmed staff values are:

| Staff tier | Comparable dedicated-staff Magic Power | Blessed-staff Magic Power | Holy Power |
| --- | ---: | ---: | ---: |
| Staff | 8 | 4 | 1 |
| Pine | 12 | 6 | 2 |
| Oak | 16 | 8 | 3 |
| Willow | 24 | 12 | 4 |
| Palm | 28 | 14 | 5 |
| Maple | 32 | 16 | 6 |
| Yew | 40 | 20 | 7 |
| Ebony | 44 | 22 | 8 |
| Magic | 48 | 24 | 9 |
| Blood | 56 | 28 | 10 |
| Saradomin, Guthix, or Zamorak god staff | 56 | 28 | 11 |

Each blessed or god staff receives exactly half the Magic Power of its normal
comparison while equipped. The current MyWorld definitions already assign the
listed `8-56` Magic Power ladder to ordinary staves and `56` to god staves, but
the blessed variants presently use a flat value of `2`. Later implementation
must synchronize those definitions to this table rather than preserving that
flat compatibility value.

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
- The initial twelve Cleric spells support other players rather than the
  caster; none of them self-apply. Future spells may opt into self-application
  only through an explicit design decision, keeping the launch book
  intentionally unattractive as a second solo-combat book.
- The goal is to make dedicated Cleric support valuable in group play without
  preventing experimentation or situational hybrid builds.

### Area-of-Effect Casting Contract

- Cleric spells are centered on the casting player and affect an area rather
  than requiring the caster to select each recipient.
- Every Cleric spell is area-of-effect unless its definition explicitly says
  otherwise.
- The caster is excluded from the affected players unless the individual
  spell explicitly permits self-application.
- None of the initial twelve spells permit self-application. This includes the
  instant Purify and Restore effects as well as healing, buffs, protection, and
  movement support.
- Beneficial area effects apply only to eligible members of the caster's
  current party. Nearby non-party players are not affected merely because
  they are standing in the area.
- The standard tier-one area has a radius of `2` tiles around the caster.
- Standard area grows by exactly one tile of radius per spell tier: tier two
  reaches `3` tiles, tier three reaches `4`, and tier `N` reaches `N + 1`.
  Radius uses the server's familiar square/Chebyshev entity-range metric: a
  recipient two tiles away on both axes is still two tiles away. Thus a
  tier-one spell covers a caster-centered `5 x 5` square before eligibility and
  obstruction checks, while tier two covers `7 x 7`.
- `Unify` has a fixed radius of `4` tiles, two tiles beyond the standard
  tier-one area, so it can gather eligible players before follow-up support
  casts.
- Recipients must occupy the caster's current world space and signed map level.
  Cross-layer or cross-world support is never implied by matching X/Y
  coordinates.
- Each recipient also requires direct spell line-of-effect from the caster.
  Walls, closed doors, and other spell-blocking barriers exclude a player
  behind them. Eligibility is evaluated independently: one blocked party member
  does not prevent unobstructed members from receiving the spell.

### Unify Regrouping Contract

Unify is a one-time collision-respecting regroup, not a short-range teleport or
continuing tether:

- Eligible party members already within the standard tier-one radius of `2`
  tiles are not moved.
- Eligible members `3` or `4` tiles away move up to two collision-valid steps
  toward the caster, ideally bringing them inside that standard support area.
- Every applied step must use ordinary traversability rules. Unify cannot pull
  through walls, closed doors, blocked or missing terrain, unreachable
  boundaries, or between world spaces or signed map levels.
- If only one safe step is available, the recipient moves one step. If no safe
  progress is available, that recipient remains in place; failure to move one
  recipient does not invalidate safe movement for the others.
- The caster is never moved. A recipient retains their combat target and active
  statuses, and Unify does not establish a persistent movement relationship.
- Later implementation should build on the server's collision-aware walking or
  pathfinding primitives. Direct teleport behavior is not an acceptable
  implementation shortcut for this spell.

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
| 1 | Mend | Saradomin | Three-pulse regeneration heal on cast and approximately `5` and `10` seconds later; its three ranks heal `1/2/3` Hits per pulse |
| 1 | Unify | Neutral | Uses a four-tile radius and moves eligible distant party members up to two collision-valid steps toward the caster |
| 1 | Fervor | Zamorak | Timed accuracy support applied before enemy defense mitigation; Holy Power increases the strength of the upward roll bias |
| 1 | Purify | Guthix | Instantly reduces current poison power by `10/20/30/40`; the existing below-`10` rule cures sufficiently weakened poison |
| 1 | Restore | Guthix | Instantly restores every configured skill except Hits by `10/25/40/60%` of that skill's valid normal maximum without creating a boost |
| 1 | Ward | Saradomin | Reduces each qualifying protected hit by a fixed `25%`; its four Holy Power ranks protect against `2/4/6/8` hits |
| 2 | Greater Mend | Saradomin | Uses Mend's pulse cadence; its four ranks heal `2/3/4/5` Hits per pulse |
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

### Mend-Family Healing Ranks

Mend uses three meaningful integer ranks rather than forcing a duplicate value
into a four-rank table. Greater Mend has enough numerical range for four:

| Spell and effect rank | Healing per pulse | Three-pulse total before caps |
| --- | ---: | ---: |
| Mend I | 1 | 3 |
| Mend II | 2 | 6 |
| Mend III | 3 | 9 |
| Greater Mend I | 2 | 6 |
| Greater Mend II | 3 | 9 |
| Greater Mend III | 4 | 12 |
| Greater Mend IV | 5 | 15 |

Both spells use the confirmed immediate, `8`-tick, and `16`-tick pulse cadence.
Holy Power selects the healing rank but never accelerates the pulses. Each
pulse clamps independently to the current valid healing ceiling. Greater Mend
retains tier priority over Mend regardless of their numerical rank; within one
spell, the normal higher-rank replacement rule applies.

### Purify Poison-Reduction Contract

Purify is an instant four-rank effect:

| Effect rank | Poison-power reduction |
| ---: | ---: |
| I | 10 |
| II | 20 |
| III | 30 |
| IV | 40 |

- Purify subtracts its rank value from each eligible recipient's current
  poison power. If the result is below `10`, the existing poison system cures
  the recipient normally; otherwise poison continues from the reduced power.
- A partial reduction retains the existing poison source attribution and
  maximum accumulation state. Purify does not grant poison immunity or prevent
  a later poison application.
- Unpoisoned recipients receive no lingering status. Purify therefore has no
  timer or HUD entry after its instant resolution.
- Repeated casts are allowed. Severe poison can be removed with additional
  tier-one sigils and casting actions, while a future full-cleansing spell can
  remain the faster and more resource-efficient answer.

### Restore Skill-Recovery Contract

Restore is an instant four-rank effect:

| Effect rank | Recovery per affected skill |
| ---: | ---: |
| I | 10% of valid normal maximum |
| II | 25% of valid normal maximum |
| III | 40% of valid normal maximum |
| IV | 60% of valid normal maximum |

- Restore iterates every unique skill in the authoritative configured skill
  registry and skips only Hits. It is not limited to a hand-maintained combat
  list; production skills, Worship, Summoning, and the future Blessing skill
  are eligible when their current levels are reduced.
- For each eligible reduced skill, recovery is the rank percentage of that
  skill's valid normal maximum, rounded up to a whole level and capped at the
  missing amount. A skill already at or above that ceiling is unchanged.
- The valid ceiling follows the server's authoritative equipment- and
  temporary-effect-aware normal level. Restore can recover a legitimately
  active boost after a drain, but it can never exceed that active ceiling or
  create a new boost.
- Legacy aliases that resolve to the same stable skill index are processed
  once. In particular, compatibility Attack/Defense/Strength aliases must not
  restore My World's unified Melee level three times.
- Devotion balances, poison power, Hits, run energy, status conditions, and
  other non-skill resources are outside Restore. Devotion is associated with
  Worship but is not itself a current skill level.
- Repeated casts are allowed. Restore resolves immediately and leaves no timed
  status or HUD entry.

### Fervor Roll-Bias Contract

Fervor has four effect ranks. Each gives direct player attacks the listed
chance to raise their normal offense roll by exactly one before the target's
defense roll is subtracted:

| Effect rank | Upward-roll chance | Duration |
| ---: | ---: | ---: |
| I | 5% | 30 seconds |
| II | 10% | 45 seconds |
| III | 15% | 60 seconds |
| IV | 20% | 90 seconds |

- Fervor applies to direct melee, ranged, and Magic attacks made by the
  affected player.
- It adds to existing equipment-based upward-roll bias within that one roll
  mechanism, subject to a valid probability bound. It does not multiply the
  attack or accuracy stat.
- A successful shift cannot raise the offense roll above its normal maximum.
- Critical hits and indirect damage such as poison, recoil, summons, and other
  secondary effects do not receive the shift.
- Fervor changes the chance of a slightly better pre-defense roll. It does not
  guarantee that the resulting attack deals damage after defense.

### Zeal Final-Damage Contract

Zeal has four effect ranks and uses the tactical duration ladder:

| Effect rank | Final-damage bonus | Duration |
| ---: | ---: | ---: |
| I | 5% | 30 seconds |
| II | 8% | 45 seconds |
| III | 11% | 60 seconds |
| IV | 15% | 90 seconds |

- Zeal applies to final nonzero damage from direct melee, ranged, and Magic
  attacks made by the affected player, including critical hits.
- Enemy defense and applicable protection are resolved before Zeal. Prayer
  offense and Zeal remain independent multipliers rather than contributing to
  one additive percentage pool.
- Poison, recoil, summons, and other indirect or secondary damage do not gain
  Zeal damage.
- The server keeps a fractional bonus-credit accumulator for each active Zeal
  recipient. Each qualifying hit contributes `final damage * Zeal percent`
  hundredths; whole accumulated points become bonus damage and the remainder
  carries to later qualifying hits during that Zeal effect.
- Fractional carry is discarded when Zeal ends or is replaced. A zero-damage
  attack creates no credit. This avoids rounding each small hit upward and
  accidentally turning a modest percentage into a very large effective bonus.

### Thorns Reflection Contract

Thorns has four effect ranks and uses the tactical duration ladder:

| Effect rank | Reflected damage | Duration |
| ---: | ---: | ---: |
| I | 5% | 30 seconds |
| II | 8% | 45 seconds |
| III | 11% | 60 seconds |
| IV | 15% | 90 seconds |

- Thorns is calculated from actual nonzero direct melee, ranged, or Magic
  damage received after the protected player’s mitigation. It does not reduce
  or absorb any part of that incoming hit.
- Fractional reflected damage uses unbiased stochastic rounding: apply the
  whole-number portion and use the fractional remainder as the chance to add
  one. There is no guaranteed one-damage minimum, which would make rapid small
  hits disproportionately valuable.
- Poison, recoil, environmental damage, summons, and other indirect or
  secondary damage cannot trigger Thorns.
- Thorns reflection cannot trigger Thorns or equipment recoil in return. A
  reflected hit is terminal for reflection processing.
- Thorns may coexist with recoil equipment. Each source resolves independently
  rather than combining their rates into one effect or replacing the other.
- Reflected damage is attributed to the protected defender, not the Cleric who
  originally applied Thorns. It may kill the attacker through the normal
  attributed-death path.

### Rally Emergency-Lifesteal Contract

Rally uses one fixed `20%` lifesteal rate. Holy Power increases the recovery
threshold and duration instead of increasing all three dimensions at once:

| Effect rank | Lifesteal | Ends at | Maximum duration |
| ---: | ---: | ---: | ---: |
| I | 20% | 55% Hits | 30 seconds |
| II | 20% | 60% Hits | 45 seconds |
| III | 20% | 65% Hits | 60 seconds |
| IV | 20% | 70% Hits | 90 seconds |

- An area cast applies Rally only to eligible party recipients who are below
  `50%` of their current valid healing ceiling at cast time.
- Rally heals from actual nonzero direct melee, ranged, and Magic damage dealt
  by the affected player, including critical hits and damage already increased
  by Zeal. Poison, recoil, summons, and other indirect or secondary damage are
  excluded.
- Each active Rally status carries fractional healing credit. Qualifying damage
  contributes at `20%`; whole accumulated points heal and the remainder carries
  until the effect ends. There is no guaranteed one-Hit minimum per attack.
- Existing blood-equipment and god-spell lifesteal resolve first and retain
  their established independent behavior. Rally does not combine their rates
  into a new global lifesteal percentage or alter their rounding rules.
- After those existing direct-hit lifesteal sources resolve, Rally contributes
  its own fractional healing only if the status remains active and the player
  is still below its ending threshold. This ordering makes Rally supplemental
  emergency support without adding another minimum-heal award.
- Rally healing cannot exceed the player's valid healing ceiling. The effect
  ends as soon as any healing source brings the player to or above that rank's
  threshold, even if one heal crosses past the exact percentage. Indirect
  healing sources excluded from Rally damage eligibility may still end the
  status by reaching that threshold. Any fractional Rally credit is discarded
  when the status ends.
- Rally also ends on its tactical timer or the general Cleric lifecycle
  conditions. Falling below half again does not reactivate an ended status; a
  new cast is required.

### Respite Regeneration Contract

Respite increases the speed of ordinary passive Hits regeneration:

| Effect rank | Regeneration speed | Duration |
| ---: | ---: | ---: |
| I | 10% faster | 5 minutes |
| II | 15% faster | 10 minutes |
| III | 20% faster | 15 minutes |
| IV | 25% faster | 20 minutes |

- Respite shortens the normal Hits-regeneration interval by dividing it by
  `1 + rank bonus`. At rank IV alone, the authentic interval falls from roughly
  `64` seconds to roughly `51.2` seconds.
- It remains active in combat and continues counting down while the recipient
  is already at the healing ceiling.
- It affects only ordinary passive Hits regeneration. It never accelerates or
  increases Mend/Greater Mend pulses and does not change restoration of other
  combat stats.
- Regeneration potions, soul robes, body amulets, and Respite remain separately
  owned speed factors and compose multiplicatively. Respite is not added into
  another system's percentage or multiplier.

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

Ward and Aegis protect against direct melee, ranged, and Magic hits, including
critical hits. They exclude poison, recoil, summons, environmental damage, and
other indirect or secondary effects. Existing defense and mitigation resolve
first. Cleric protection then calculates remaining damage as:

- Ward: `ceil(eligible damage * 0.75)`
- Aegis: `ceil(eligible damage * 0.50)`

The difference between eligible and remaining damage is recorded as prevented
damage. A charge is consumed only when that difference is at least one. A hit
already reduced to zero, or too small for the Cleric percentage to prevent a
whole point, neither consumes a charge nor becomes zero through favorable
rounding. The server sends an updated status snapshot immediately after a
charge is consumed. Zeal subsequently uses the protected result as part of its
final-damage calculation, and Thorns observes actual damage ultimately
received.

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

The initial duration ladders are confirmed as follows:

| Effect rank | Tactical duration | Respite duration |
| ---: | ---: | ---: |
| I | 30 seconds | 5 minutes |
| II | 45 seconds | 10 minutes |
| III | 60 seconds | 15 minutes |
| IV | 90 seconds | 20 minutes |

The tactical ladder applies to Ward, Aegis, Fervor, Zeal, Thorns, and Rally.
Charges or Rally's ending-health condition may end an effect before its timer;
the listed duration is its upper bound. Respite uses the longer ladder because
maintaining modest passive regeneration is its identity. Mend and Greater Mend
remain three-pulse effects rather than adopting either duration ladder.

Mend-family pulses occur immediately when the effect is applied, then after
`8` and `16` game ticks: approximately `5` and `10` seconds after casting at
the authentic `640 ms` game-tick rate. Both spell tiers use this cadence. Holy
Power changes healing per pulse but never makes the pulses arrive faster.
Combat does not interrupt the sequence. Each pulse uses the recipient's valid
healing ceiling at that moment; any excess healing is lost rather than stored,
and a capped or wasted pulse does not cancel the remaining sequence.

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
- The current full stat-restoration potion already iterates the configured
  skill registry and skips Hits. Restore adopts the same future-safe scope but
  restores a rank percentage instead of setting every reduced skill directly
  to its normal ceiling.
- Poison is represented as integer power, drains by `3` per poison pulse, and
  is cured when it falls below `10`. Purify can therefore reduce that same
  authoritative power while preserving a meaningful distinction from a full
  cure.
- Current poison sources commonly begin around `20-68` power and advanced
  effects can accumulate to `80`. Purify's `10-40` reductions can cure modest
  poison or materially weaken severe poison without making one cast a universal
  full cleanse.
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
  one step upward before the defense roll is subtracted. This existing
  mechanism is the implementation and balance model selected for Fervor.
- Current equipment can contribute `10%` upward-roll bias from a medium helmet
  and `20%` from the relevant weapon families. Those bonuses can already
  combine. Fervor intentionally joins that same bounded roll-bias family rather
  than introducing a second accuracy multiplier.
- Recoil and lifesteal behavior already exist, but their hooks span melee,
  ranged/projectile, poison, and other damage paths. The settled Thorns and
  Rally contracts therefore require a shared direct-damage eligibility hook
  rather than copying only one existing combat path.
- Existing Chaos recoil rings reflect one quarter of the triggering damage
  when they proc, with tier chances of `10/20/30/50/90%` and a minimum reflected
  hit of one. Thorns is intentionally weaker at `5-15%`, has no minimum hit,
  and is based on actual post-mitigation damage even though it is guaranteed to
  evaluate on every eligible direct hit.
- Normal Hits regeneration restores one point about every `100` game ticks
  before existing equipment and potion speed modifiers. It is not inherently
  limited to out-of-combat periods, which fits the revised Respite direction.
- Current ordinary and super regeneration potions last `30` and `60` minutes
  respectively. Respite's confirmed `5-20` minute duration ladder is shorter
  and supports active Cleric maintenance without displacing those longer-lived
  consumables solely through duration.

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

The launch roster uses these confirmed thresholds:

| Threshold family | Rank I | Rank II | Rank III | Rank IV |
| --- | ---: | ---: | ---: | ---: |
| Mend | 0 | 2 | 5 | — |
| Fervor, Purify, and Restore | 0 | 2 | 5 | 8 |
| Ward | 0 | 2 | 4 | 6 |
| Greater Mend, Zeal, Thorns, Aegis, Rally, and Respite | 0 | 4 | 8 | 11 |

Rank I is therefore available with no Holy Power staff. At Holy Power `5`, the
Palm-tier launch-era point, a Cleric reaches Mend III, rank III in most
tier-one effects, Ward III, and rank II in tier-two effects. Later staves keep
the early book progressing: most tier-one effects peak at Holy Power `8`, Ward
peaks earlier at `6`, and tier-two effects peak at the god-staff value of `11`.
Unify has no Holy Power rank because its area and movement contract are fixed.

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

- PvP behavior, experience attribution, and remaining abuse safeguards.
  Same-party eligibility, launch-spell caster exclusion, and clearing effects
  on party separation are settled.
- How Unify's one-time forced movement integrates with queued walking and the
  client movement presentation without desynchronization. This implementation
  detail may not weaken its settled collision, layer, or combat-state rules.
- Cast-level resource and experience behavior when an area spell applies to
  only some recipients or is wholly ineffective because every recipient has a
  stronger active family effect.
- Status icons and labels; optional-count packet representation; compatibility
  behavior; and priority/overflow rules for the existing `16`-entry HUD bound.
  Mend cadence and the tactical and Respite duration ladders are settled.
- Shared combat-path implementation and blocked-damage telemetry for the
  settled Ward/Aegis, Thorns, Zeal, and Rally direct-damage boundaries.
- Exact passive-healing clock synchronization when Respite is applied,
  refreshed, replaced, or expires. Its magnitude, duration, eligible healing
  stream, and multiplicative composition are settled.

### Initial Spell Content

- Exact unlock levels for the six tier-one and six tier-two spells through
  Worship `30`.
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

### 2026-08-02: Holy Power and half-Magic staff ladder

Confirmed that casting remains possible at `0` Holy Power and therefore does
not hard-require a staff; `0` receives the spell's lowest effect rank. The ten
blessed-staff tiers receive Holy Power `1-10`, and all three god staves receive
Holy Power `11`. Any alignment's staff empowers ordinary spells in the shared
Cleric book, while blessed armor contributes no Holy Power at launch.

Confirmed that blessed and god staves provide exactly half the Magic Power of
their normal tier comparison. This produces blessed-staff Magic Power
`4/6/8/12/14/16/20/22/24/28` and god-staff Magic Power `28`. Current data's
flat blessed-staff value of `2` will require a later implementation
synchronization; no runtime definition changes belong to this concept branch.

### 2026-08-02: Launch Holy Power thresholds

Confirmed rank thresholds `0/2/5` for Mend, `0/2/5/8` for the other scalable
tier-one effects, and `0/4/8/11` for all scalable tier-two effects. Ward is the
intentional tier-one exception at `0/2/4/6`, reaching its larger charge counts
with less Holy Power than Aegis. Rank I remains available at Holy Power zero,
Palm-tier Holy Power `5` gives strong but incomplete launch progression, and
the god-staff value of `11` uniquely reaches rank IV tier-two support. Unify
does not scale with Holy Power.

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
retains an intentionally larger area; its exact contract was settled later.

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

### 2026-08-02: Tactical and Respite duration ladders

Confirmed a shared four-rank duration ladder of `30/45/60/90` seconds for
Ward, Aegis, Fervor, Zeal, Thorns, and Rally. Charges and spell-specific ending
conditions can end these effects sooner. Confirmed a separate
`5/10/15/20`-minute ladder for Respite, keeping its modest passive-regeneration
support longer-lived while remaining shorter than current `30/60`-minute
regeneration potions. Mend-family timing remains governed by three pulses, with
their cadence still unresolved at this point.

### 2026-08-02: Mend-family pulse cadence

Confirmed that Mend and Greater Mend pulse immediately on application and
again `8` and `16` game ticks later, corresponding to approximately `5` and
`10` seconds at the authentic tick rate. Holy Power scales healing per pulse
without changing cadence. Combat does not interrupt the effect. Every pulse
clamps independently to the recipient's current valid healing ceiling; excess
healing is wasted without ending the remaining sequence.

### 2026-08-02: Mend-family healing ranks

Confirmed three Mend ranks healing `1/2/3` Hits per pulse and four Greater Mend
ranks healing `2/3/4/5` per pulse. This avoids a duplicate integer value merely
to force both spells into four ranks. Greater Mend retains tier replacement
priority over Mend, while Holy Power thresholds remain tied to the later staff
stat design.

### 2026-08-02: Ward and Aegis eligible-hit boundary

Confirmed that Ward and Aegis protect direct melee, ranged, and Magic hits,
including critical hits, after existing defense and mitigation. Remaining
damage uses ceiling rounding at the fixed `75%` Ward or `50%` Aegis multiplier.
A charge is consumed only if at least one whole point is prevented; zero or
too-small hits consume nothing. Indirect and secondary damage are excluded,
and charge consumption immediately refreshes the affected player's status HUD.

### 2026-08-02: Fervor roll-bias ranks

Confirmed Fervor ranks of `5/10/15/20%`. On each normal direct melee, ranged,
or Magic attack, this is the chance to lift the pre-defense offense roll by one
without exceeding its normal maximum. It combines with existing equipment
high-roll bias in the same bounded chance. Critical hits, poison, recoil,
summons, and other indirect damage are excluded. Fervor uses the confirmed
`30/45/60/90`-second tactical duration ladder.

### 2026-08-02: Zeal final-damage ranks

Confirmed Zeal ranks of `5/8/11/15%` and the tactical duration ladder. Zeal
multiplies final nonzero direct melee, ranged, or Magic damage after defense
and protection, including critical hits. Prayer offense remains a separate
multiplier. Poison, recoil, summons, and other indirect damage are excluded.
A per-effect fractional accumulator carries sub-point bonus credit between
qualifying hits so small-hit rounding cannot inflate the intended percentage;
its remainder is discarded when the status ends or is replaced.

### 2026-08-02: Thorns reflection ranks

Confirmed Thorns ranks of `5/8/11/15%` and the tactical duration ladder. It
reflects that percentage of actual post-mitigation direct melee, ranged, or
Magic damage without reducing the incoming hit. Unbiased stochastic rounding
handles fractional damage without a guaranteed minimum. Indirect and
secondary damage are excluded, and reflected damage cannot recursively trigger
any reflection. Thorns coexists independently with recoil equipment. Damage is
attributed to the protected defender and may kill the attacker normally.

### 2026-08-02: Rally emergency-lifesteal ranks

Confirmed a fixed `20%` Rally lifesteal rate with rank recovery thresholds of
`55/60/65/70%` Hits and the tactical duration ladder. Rally is applied only to
eligible recipients below half their current healing ceiling. It heals from
actual direct melee, ranged, or Magic damage, including critical hits and
Zeal-enhanced damage, while excluding indirect damage. Fractional credit
carries without a minimum heal. Any healing source ends Rally upon reaching
the rank threshold, and an ended effect does not reactivate without a new cast.

### 2026-08-02: Rally composition with existing lifesteal

Confirmed that blood-equipment and god-spell lifesteal retain their existing
independent mechanics and resolve before Rally. Rally then provides its own
`20%` fractional healing only while the player remains below the rank's ending
threshold; it contributes no separate minimum heal. Any healing source may end
Rally at the threshold, at which point its carried fraction is discarded. This
avoids a new combined lifesteal system and preserves established item and spell
behavior while keeping Rally bounded as supplemental emergency recovery.

### 2026-08-02: Unify collision-respecting regroup

Confirmed a fixed four-tile radius for Unify. Party recipients already inside
the normal tier-one two-tile area do not move; recipients three or four tiles
away move up to two safe steps toward the caster. Partial safe movement is
allowed, while blocked recipients remain in place. The spell cannot bypass
collision, closed boundaries, missing terrain, world-space, or signed-layer
separation, and it must not be implemented as a teleport. It is a one-time
regroup that leaves the caster fixed and preserves recipient combat targets and
active statuses.

### 2026-08-02: Area geometry and spell line-of-effect

Confirmed that Cleric area radii use the existing square/Chebyshev entity-range
model. Tier one therefore considers a `5 x 5` square and tier two a `7 x 7`
square before other eligibility checks. Every recipient must have direct spell
line-of-effect from the caster; walls, closed doors, and equivalent spell
barriers block support. Recipients are resolved independently, so an obstructed
party member does not invalidate the cast for unobstructed eligible members.

### 2026-08-02: Launch-spell caster exclusion

Confirmed that none of the initial twelve Cleric spells affect their caster.
This applies equally to Purify and Restore, Mend-family healing, protection,
buffs, regeneration, and Unify movement. Future content retains the ability to
opt explicitly into self-application, but the launch roster establishes Cleric
as party support rather than a personal healing and buff rotation.

### 2026-08-02: Respite regeneration ranks

Confirmed Respite speed bonuses of `10/15/20/25%` with its established
`5/10/15/20`-minute duration ladder. It shortens only the ordinary passive Hits
regeneration interval, remains active during combat and at full health, and
does not modify Mend pulses or other stat restoration. Existing regeneration
potions, soul robes, body amulets, and Respite retain independent ownership and
compose multiplicatively.

### 2026-08-02: Purify poison-reduction ranks

Confirmed instant Purify reductions of `10/20/30/40` current poison power. A
result below the existing cure threshold of `10` ends poison normally;
otherwise its reduced power, source attribution, and maximum accumulation
state continue. Purify grants no immunity, creates no timed status, and may be
cast repeatedly so severe poison trades additional sigils and actions for a
full cure.

### 2026-08-02: Restore all-stat recovery ranks

Confirmed instant Restore ranks of `10/25/40/60%` of each skill's valid normal
maximum. Restore covers every unique configured skill except Hits rather than
maintaining a combat-only allowlist. It rounds each reduced skill's recovery up
and caps it at the active legitimate ceiling, never creating a boost. Stable
aliases are deduplicated. Devotion and other non-skill resources remain
outside the effect, and repeated casts are allowed.
