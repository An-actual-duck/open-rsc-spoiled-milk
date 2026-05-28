# Dual Element Spells

Dual element spells are a first-pass Magic identity expansion. They sit on the
same three early spell tiers as the elemental spell lines, require both matching
elemental runes, and use the normal tier rune.

## Element Pairs

- Fire + Water = Thunder
- Air + Water = Ice
- Fire + Earth = Acid
- Earth + Water = Wood

## Tiering

Dual spells are stronger than same-tier single-element spells and currently use
`1.2x` the base spell power before normal magic offense, defense, and tier cap
handling.

Current unlocks:

- Tier 1 dual spells: level `10`
- Tier 2 dual spells: level `30`
- Tier 3 dual spells: level `50`

No tier 4 dual spells are implemented yet.

## Spell List

| Tier | Spell | Elements | Cost |
| --- | --- | --- | --- |
| 1 | Thunder Ball | Fire + Water | 1 Fire, 1 Water, 1 Mind |
| 1 | Icicle Shot | Air + Water | 1 Air, 1 Water, 1 Mind |
| 1 | Acid Drop | Fire + Earth | 1 Fire, 1 Earth, 1 Mind |
| 1 | Spore | Earth + Water | 1 Earth, 1 Water, 1 Mind |
| 2 | Thunder Splash | Fire + Water | 2 Fire, 2 Water, 1 Chaos |
| 2 | Ice Burst | Air + Water | 2 Air, 2 Water, 1 Chaos |
| 2 | Acid Frog | Fire + Earth | 2 Fire, 2 Earth, 1 Chaos |
| 2 | Wood Drill | Earth + Water | 2 Earth, 2 Water, 1 Chaos |
| 3 | Thunder Strike | Fire + Water | 3 Fire, 3 Water, 1 Death |
| 3 | Ice Crystal | Air + Water | 3 Air, 3 Water, 1 Death |
| 3 | Acid Gush | Fire + Earth | 3 Fire, 3 Earth, 1 Death |
| 3 | Battering Ram | Earth + Water | 3 Earth, 3 Water, 1 Death |

## Effects

Current dual-element effects apply only when the spell deals damage.

| Element | Effect | Tier 1 | Tier 2 | Tier 3 |
| --- | --- | --- | --- | --- |
| Thunder | Startle: target's next attack is negated | 7% | 15% | 25% |
| Acid | Corrode: applies poison power | 7%, 20 poison | 15%, 30 poison | 25%, 40 poison |
| Ice | Frostbite: target's next attack reflects half its damage back as a yellow hit | 7% | 15% | 25% |
| Wood | Splinter: hits one random additional attackable NPC within `2` tiles for `50%` of dealt damage, rounded up | 7% | 15% | 25% |

Startle and Frostbite last for one qualifying attack. Frostbite reflects half
of the final damage after mitigation, rounded up against the attacker and
rounded down against the original target. Splinter applies only in PvM,
excludes the primary target, and does nothing if no additional valid target is
within range. A killing primary hit can still trigger it. Its tier scaling is
proc chance only.
