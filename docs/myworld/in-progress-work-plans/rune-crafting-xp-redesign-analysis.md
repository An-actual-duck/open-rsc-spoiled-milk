# Enchanting Rune-Crafting XP Redesign Analysis

Status: implemented on the active topic branch; awaiting manager review.

## Decision Summary

The configured per-rune XP table remains accepted, but XP no longer scales
linearly with every rune produced by a level multiplier. Each produced rune
batch contributes half as much XP as the preceding batch:

- `1x` output: 100% XP
- `2x` output: 100% + 50% XP
- `3x` output: 100% + 50% + 25% XP
- `4x` output: 100% + 50% + 25% + 12.5% XP

The number of runes produced is unchanged. XP approaches, but never
mathematically exceeds, twice the first-batch XP. This keeps later output
breakpoints meaningful without allowing the original linear model to compound
the proposed higher per-rune values into an approximately one-million-XP/hour
late-game ceiling.

Using the supplied route estimates, the diminishing-return model produces 14 route-normalized optimal regimes
and 13 actual method changes. Nature's
exceptionally short Stone route remains competitive, while Death, Soul, and
Blood become the strongest late-game options. The estimated peak is Blood at
`562,240 XP/hour` instead of the linear model's `1,035,616 XP/hour`.

Runtime XP is calculated only from successfully processed Stone and the base
rune multiplier. Law-robe and Chaos-amulet bonus runes remain excluded.

## Implemented Runtime Behavior

The implementation is in
`server/plugins/com/openrsc/server/plugins/custom/myworld/skills/runecraft/Runecraft.java`.

- Base rune output remains
  `1 + floor((current level - unlock level) / 10)`, with a minimum of one.
- Current/temporary skill level continues to control altar access and output
  breakpoints, preserving boost behavior.
- One altar interaction processes every available unnoted Stone in one action.
- A Stone enters the XP calculation only after its exact inventory removal
  succeeds. Missing or stale Stone ends processing without output or XP for
  that iteration.
- Every successful Stone still produces the full base rune multiplier.
- XP is calculated once over the complete successful action and rounded once.
- Law-robe and Chaos-amulet bonus output is added after XP and cannot increase
  the XP-eligible batch count.

In conceptual internal units:

```text
baseActionXP = configuredXmlXP * successfullyProcessedStone
batchWeight(multiplier) = 1 + 1/2 + 1/4 + ... + 1/2^(multiplier - 1)
actionXP = roundHalfUp(baseActionXP * batchWeight(multiplier))
```

The equivalent exact batch weight is:

```text
batchWeight(multiplier) = (2^multiplier - 1) / 2^(multiplier - 1)
```

The runtime uses integer rational arithmetic rather than floating point.
Half-up rounding occurs once after the complete action, avoiding repeated
per-Stone truncation and floating-point drift. Extremely high theoretical
multipliers safely converge to the rounded two-batch ceiling, and the final
value is bounded to the integer XP API.

## XP Units And Configuration

`ObjectRunecraft.xml` stores integer XP in quarter-XP units. Before other
bonuses:

- `1x` displayed XP is `internal XP / 4`
- normal `3x` My World displayed XP is `internal XP * 3 / 4`

| Rune | Unlock | Internal XP per first batch | Displayed first-batch XP at 3x |
| --- | ---: | ---: | ---: |
| Air | 1 | 20 | 15 |
| Water | 1 | 20 | 15 |
| Earth | 1 | 20 | 15 |
| Fire | 1 | 20 | 15 |
| Life | 1 | 20 | 15 |
| Mind | 8 | 21 | 15.75 |
| Body | 15 | 27 | 20.25 |
| Chaos | 22 | 28 | 21 |
| Cosmic | 30 | 29 | 21.75 |
| Nature | 38 | 30 | 22.5 |
| Law | 46 | 94 | 70.5 |
| Death | 54 | 168 | 126 |
| Soul | 62 | 186 | 139.5 |
| Blood | 70 | 256 | 192 |

Shared `Player.incExp` behavior remains unchanged. Mind jewelry, prayer, and
skiller-brew percentage modifiers apply to the internal action award. The
configured skill rate is applied later; ordinary `1x` mode bypasses the normal
My World `3x` skill rate, while other shared XP modifiers retain their existing
behavior.

## Multiplier Breakpoints

The rune multiplier still increases every ten levels after a rune's unlock.
Only the XP weight changes:

| Base output | XP weight | Marginal batch |
| ---: | ---: | ---: |
| 1x | 1.0000 | 100% |
| 2x | 1.5000 | 50% |
| 3x | 1.7500 | 25% |
| 4x | 1.8750 | 12.5% |
| 5x | 1.9375 | 6.25% |
| 6x | 1.96875 | 3.125% |
| 10x | 1.998046875 | 0.1953125% |

Regression coverage checks every configured rune at its unlock level,
unlock-plus-nine, unlock-plus-ten, representative late-game levels, and level
99. It also exercises multiple successful Stone counts and high theoretical
multipliers.

## Thirty-Stone Trip Comparison

The following level-99 values are the actual once-rounded action awards before
shared XP modifiers. Displayed values apply the normal `3x` rate after
converting internal quarter-XP units.

| Rune | Level-99 output | Internal XP | Displayed XP at 3x |
| --- | ---: | ---: | ---: |
| Air | 10x | 1,199 | 899.25 |
| Water | 10x | 1,199 | 899.25 |
| Earth | 10x | 1,199 | 899.25 |
| Fire | 10x | 1,199 | 899.25 |
| Life | 10x | 1,199 | 899.25 |
| Mind | 10x | 1,259 | 944.25 |
| Body | 9x | 1,617 | 1,212.75 |
| Chaos | 8x | 1,673 | 1,254.75 |
| Cosmic | 7x | 1,726 | 1,294.5 |
| Nature | 7x | 1,786 | 1,339.5 |
| Law | 6x | 5,552 | 4,164 |
| Death | 5x | 9,765 | 7,323.75 |
| Soul | 4x | 10,463 | 7,847.25 |
| Blood | 3x | 13,440 | 10,080 |

Blood is therefore the largest normal level-99 action at `10,080` displayed XP per 30 Stone before equipment
or shared XP bonuses. The calculation is well
below integer limits.

## Route-Normalized Evidence

The supplied pre-change estimates are based on each altar's fastest available
Stone route. Nature currently reaches about `134,600 XP/hour`, compared with
Blood at `75,300`, Soul at `68,500`, and Death at `51,800`. The redesign must
therefore account for route throughput rather than comparing only XP per Stone.

For the model cross-check, route Stone throughput is derived by dividing each
pre-change hourly estimate by its former displayed XP per Stone. That
throughput is then multiplied by the configured first-batch XP and the new
geometric batch weight.

| Starting level | Route-normalized best rune | Estimated XP/hour |
| ---: | --- | ---: |
| 1 | Water | 43,429 |
| 8 | Mind | 47,775 |
| 11 | Water | 65,143 |
| 18 | Mind | 71,663 |
| 21 | Water | 76,000 |
| 25 | Body | 89,910 |
| 38 | Nature | 126,188 |
| 54 | Death | 207,200 |
| 58 | Nature | 220,828 |
| 62 | Soul | 254,820 |
| 64 | Death | 310,800 |
| 70 | Blood | 321,280 |
| 72 | Soul | 382,230 |
| 80 | Blood | 481,920 |

Blood remains optimal through level 99 and reaches approximately
`562,240 XP/hour`. These figures use rounded source estimates and represent
fastest-path modeling, not guaranteed player rates. A consistent in-game timing
pass for Nature, Death, Soul, and Blood remains worthwhile, but the model
removes Nature's disproportionate dominance and avoids the linear model's
late-game spike.

## Equipment-Generated Runes

Equipment output remains outside the XP count:

- Law robes add production from the completed base output.
- Chaos amulets add randomized rune output when crafting Chaos runes.
- Both systems use `baseRuneCount`, which records actual base runes produced.
- XP uses successful Stone and the base multiplier before either equipment
  method executes.

Including these bonus runes would turn yield equipment into another training
multiplier and make randomized Chaos output alter XP. That is not part of the
accepted rule.

## Validation And Remaining Review

The focused regression suite verifies:

- all fourteen XML XP values;
- unlock, `unlock + 9`, `unlock + 10`, late-game, and level-99 breakpoints;
- exact weights for 1x through 4x output across low, mid, and high rune tiers;
- multiple complete-action Stone counts;
- convergence at high multipliers;
- no XP for unsuccessful Stone removal;
- one XP grant for a full-inventory action;
- Law-robe and Chaos-amulet bonus ordering and exclusion; and
- the revised route-regime model.

No rune-output, altar-access, inventory-processing, equipment-yield, shared XP
modifier, or one-action behavior changes are intended. Final balance review
should focus on whether the approximately `562,240 XP/hour` modeled ceiling and
the resulting 13 method changes match the desired Enchanting progression.
