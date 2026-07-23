# Enchantment Runtime Audit

Status: implemented, verified, and merged into `main`

Audit date: 2026-07-23

This is the current checklist connecting the advertised enchanted-item effects
to their authoritative data, getters, and runtime activation points. The
authoritative item descriptions are in
`server/conf/server/defs/ItemDefsCustom.json`; the shared catalog and formulas
are in `server/src/com/openrsc/server/content/EnchantingItemEffects.java`.

## Audit conventions

- Jewelry tier order is Sapphire, Emerald, Ruby, Diamond, Dragonstone.
- A five-value list below is in that tier order.
- `data` coverage means `tests/myworld/test-enchanting-data.py`.
- `runtime` coverage means `tests/myworld/test-jewelry-runtime-effects.py`.
- `robes` coverage means `tests/myworld/test-enchanted-robe-effects.py`.
- XP jewelry is intentionally applied by the central `Player.incExp` path and
  excluded from quest XP.
- “Neck item” below includes necklaces and amulets because both occupy the
  single equipment neck slot. Effects from two neck items cannot coexist.

## Priority result: Blood jewelry

| Effect | IDs and advertised tiers | Calculation/getter | Runtime activation | Result and coverage |
| --- | --- | --- | --- | --- |
| Ring of Vitality | `3091-3095`; max Hits `+2/+4/+6/+10/+20` | `getBloodRingHitsBonus`; `Equipment.getBloodRingHitsBonus` | `Player.syncHitsEquipmentBonuses`, called by equipment synchronization | Description and runtime agree. Covered by data/runtime. |
| Necklace of Leach | `1668-1672`; heals `10/20/30/50/100%` of owned poison damage | `getBloodNecklaceLeachPercent`; `Equipment.getBloodNecklaceLeachPercent`; `Leach.calculateHealing` | `PoisonEvent.applyLeach`, using the latest player poison owner | Description and runtime agree after this audit’s actual-damage correction. Covered by data/runtime plus compiled Leach edge-case fixture. |
| Amulet of Siphoning | `1729-1733`; heals `5/10/15/20/25%` of damage dealt | `getBloodAmuletLifestealChance` (the legacy “Chance” name returns a deterministic percentage); `Equipment.getBloodAmuletLifestealChance`; `Player.applyBloodAmuletLifesteal`; `Leach.calculateHealing` | Direct melee in `CombatEvent` and `PvmMeleeEvent`; primary ranged/magic projectiles in `ProjectileEvent`; each scythe cleave target in `PvmMeleeEvent` | PvP melee and all ranged attacks were missing; projectile magic used nominal rather than actual damage. Fixed. Covered by data/runtime plus compiled Leach edge-case fixture. |

`Leach.calculateHealing` uses
`min(missing Hits, max(1, floor(actual damage * percentage)))`. Zero damage,
zero percentage, a removed player, and a full-health player heal zero.

### Siphoning path matrix

| Damage path | PvM | PvP | Siphoning decision |
| --- | --- | --- | --- |
| Direct melee | Yes | Yes | Supported once per resolved hit using target-capped `damageDealt`. PvP was fixed by this audit. |
| Direct ranged | Yes | Yes | Supported once for projectile types `2/5` using `damageDealt`. Both were fixed by this audit. |
| Direct magic | Yes | Yes | Supported once for projectile types `1/4`; overkill now uses `damageDealt`, not the nominal hit. |
| Scythe cleave | Yes | Not applicable | Supported once for each secondary NPC actually hit, using target-capped damage. |
| Chaos chain lightning | Indirect NPC AoE | No player target selection | Excluded. It is an effect-generated hit, and the Chaos Necklace that starts it cannot be worn with a Siphoning Amulet. A misleading dead projectile call was removed. |
| Blood robe splash | Indirect NPC AoE | No | Excluded to avoid effect-generated lifesteal cascades. The robe and amulet can coexist, so changing this is a balance decision. |
| Death amulet Burst / Death ring hit | Indirect NPC damage | No | Excluded. The Death Amulet cannot coexist with Siphoning; the Death Ring can, but its yellow hit is equipment-generated damage. |
| Recoil, Frostbite, Divine Retribution | Reflected/retaliatory | Reflected/retaliatory | Excluded. Reflected damage must not start lifesteal or recursive reflection/healing loops. |
| Summon attacks and summon lifesteal | Owner-attributed combat credit | No | Excluded. The summon is the hitter and has a separate Life-equipment/lifesteal system. |
| Poison ticks, including Corrosive Aura | Owned indirect damage | Owned indirect damage | Excluded from Siphoning. Poison Leach is the explicitly advertised Blood Necklace role. |
| Zero-damage hit | Yes | Yes | No healing. |
| Overkill | Yes | Yes | Healing uses only the victim’s remaining Hits. |

The descriptions do not explicitly define whether every indirect owned effect
should Siphon. The safest current interpretation is “direct attacks plus the
explicit weapon cleave path.” It preserves the distinct poison Leach item,
avoids recursive effect chains, and matches the existing exclusions. Expanding
Siphoning to blood-robe splash, Death-ring damage, poison, or other proc damage
requires a manager-approved balance rule rather than another incidental
callsite.

## Corrosive Aura attribution

`CorrosiveAura.apply(defender, attacker, incomingDamage)` runs after positive
actual damage reaches a Guthix player with Corrosive Aura active and the Guthix
mace equipped. It applies poison to the attacker with the **defender** passed as
the poison source:

```text
attacker.applyPoison(..., defender)
```

`Mob.applyPoison` stores that player UUID and updates an existing
`PoisonEvent`, so the latest player to apply or renew poison owns later ticks.
Consequences:

- A Corrosive Aura owner wearing a Necklace of Leach heals from the poison
  damage their aura deals. This agrees with “poison damage dealt” and the shared
  poison-owner rule in `combat-equipment-spec.md`.
- A Siphoning Amulet does not heal from Corrosive Aura. This keeps direct
  Siphoning and poison Leach distinct.
- The two effects cannot double-heal from one poison tick because both items
  occupy the neck slot and `PoisonEvent` calls only the necklace getter.
- Poison damage and Leach healing do not call combat/reflection hooks, so no
  recursive damage/healing loop is introduced.
- This audit changed `PoisonEvent` to use
  `Mob.damageAndGetActualDamage`. Leach now observes lethal overkill caps and
  Goblin’s Tenacity reductions instead of the nominal poison tick.

Corrosive Aura is reached from `CombatEvent`, `PvmMeleeEvent`,
`ProjectileEvent`, and `ElderGreenDragonSpecialAttacks`. Existing prayer tests
cover those activation points; the jewelry runtime test now guards ownership,
actual damage, and the separation from Siphoning.

## Complete jewelry checklist

### Elemental amulets

| Item/effect | IDs; tier values; description | Getter and all activation points | Status/coverage |
| --- | --- | --- | --- |
| Woodcutter’s Amulet | `1593-1597`; log yield `10/20/30/50/100%` | `getGatheringAmuletYieldBonusPercent` and `consumeGatheringAmuletBonusItems`; `Woodcutting` reward path | Deterministic carry points preserve fractional yield. data/runtime. |
| Angler’s Amulet | `1598-1602`; catch yield `10/20/30/50/100%` | Same shared getters; `Fishing` reward path | Complete. data/runtime. |
| Harvester’s Amulet | `1603-1607`; produce yield `10/20/30/50/100%` | Same shared getters; `Harvesting` reward path | Complete. data/runtime. |
| Miner’s Amulet | `1608-1612`; ore yield `10/20/30/50/100%` | Same shared getters; `Mining` reward path | Complete. data/runtime. |

### Standard necklaces

| Item/effect | IDs; tier values; description | Getter and all activation points | Status/coverage |
| --- | --- | --- | --- |
| Air, Evasion | `1613-1617`; ranged defense `+3/+6/+9/+12/+15` | `getElementalDefenseBonus`; `Equipment.applyDefenseBonus` for ranged | Complete. data/runtime. |
| Mind, Artifice | `1618-1622`; Crafting/Smithing/Enchanting XP `5/10/15/25/50%` | `getMindNecklaceXpBonus`; `Equipment.getMindJewelryXpBonus`; central `Player.incExp` | Complete. “Enchanting” maps to `RUNECRAFT`. data/runtime. |
| Water, Equilibrium | `1623-1627`; all three defenses `+2/+4/+6/+8/+10` | `getElementalDefenseBonus`; `Equipment.applyDefenseBonus` for melee/ranged/magic | Complete. data/runtime. |
| Earth, Bulwark | `1628-1632`; melee defense `+3/+6/+9/+12/+15` | Same defense getter/path for melee | Complete. data/runtime. |
| Fire, Warding | `1633-1637`; magic defense `+3/+6/+9/+12/+15` | Same defense getter/path for magic | Complete. data/runtime. |
| Body, Labor | `1638-1642`; Harvesting/Mining/Woodcutting XP `5/10/15/25/50%` | `getBodyNecklaceXpBonus`; `Equipment.getBodyJewelryXpBonus`; central `Player.incExp` | Complete. data/runtime. |
| Cosmic, Fortune | `1643-1647`; extra standard monster-drop roll `10/20/30/40/50%` | `getCosmicNecklaceStandardDropChance`; `DropTable.rollItem` | Extra roll suppresses rare tables. Complete. data/runtime. |
| Chaos, Chain Lightning | `1648-1652`; per-hop chance `10/20/30/50/90%`, at most 3 halving hits | `getChaosNecklaceChainLightningChance`; direct-hit follow-ups in `CombatEvent`, `PvmMeleeEvent`, and `ProjectileEvent` | NPC targets only; excludes summons. Complete. data/runtime. |
| Nature, Cleansing | `1653-1657`; poison power decays an extra `+1/+2/+3/+4/+5` per tick | `getNatureCleansingPoisonDecayBonus`; `PoisonEvent.run` | Complete. data/runtime. |
| Law, Loot Banking | `1658-1662`; `100/200/300/500/1000` charges | `getLawItemMaxCharges` and account-backed banking-charge accessors; NPC bones and all standard/invariable monster-drop paths call `Equipment.tryBankMonsterLootWithLawNecklace` | Non-stack monster loot; amount consumes equal charges. Complete. data/runtime. |
| Death, Reaping | `1663-1667`; guaranteed-drop `+1` chance `25/40/60/90/100%`; tier 5 has another `+1` at `10%` | `getDeathNecklaceGuaranteedDropBonusChance/ExtraChance`; `Equipment.rollDeathNecklaceGuaranteedDropBonus`; NPC bones/invariable drops | Complete. data/runtime. |
| Blood, Leach | `1668-1672`; poison Leach `10/20/30/50/100%` | Blood section above | Actual-damage defect fixed. data/runtime plus new edge coverage. |
| Soul, Preservation | `1759-1763`; keep `+1/+2/+3/+5/+8` items on death | `getSoulNecklaceExtraKeptItems`; `Inventory` death-item retention | Complete. data/runtime. |
| Life, Vigor | `3101-3105`; combat summon health `+10/+20/+30/+40/+50%` | `getLifeNecklaceSummonHealthPercent`; combat summon creation in `Summoning` | Utility/support summons unaffected. Complete. data/runtime. |

### Rings

| Item/effect | IDs; tier values; description | Getter and all activation points | Status/coverage |
| --- | --- | --- | --- |
| Air, Archery | `1673-1677`; ranged power `+3/+6/+9/+12/+15` | `getElementalPowerBonus`; `Equipment.getModifiedOffense` for ranged | Complete. data/runtime. |
| Water, Balance | `1678-1682`; all three powers `+2/+4/+6/+8/+10` | Same offense getter/path for all styles | Complete. data/runtime. |
| Earth, Force | `1683-1687`; melee power `+3/+6/+9/+12/+15` | Same offense getter/path for melee | Complete. data/runtime. |
| Fire, Sorcery | `1688-1692`; magic power `+3/+6/+9/+12/+15` | Same offense getter/path for magic | Complete. data/runtime. |
| Chaos, Recoil | `1314,1693-1696`; proc `10/20/30/50/90%`, reflect `25%` damage | `getChaosRingRecoilChance`; recoil helpers in `CombatEvent`, `PvmMeleeEvent`, and `ProjectileEvent` | Reflected damage uses yellow hits and does not Siphon. Complete. data/runtime. |
| Nature, Nourishment | `1316,1697-1700`; food healing `10/20/30/50/100%` | `getNatureFoodHealingBonus`; `Eating` | Complete. data/runtime. |
| Cosmic, Fortune | `1701-1704,3111`; rare-table-miss reroll `5/10/15/20/25%` | `getWealthAdditionalRollChance`; `DropTable` primary rare-table path | Does not duplicate a successful rare roll. Complete. data/runtime. |
| Soul, Lifesaving | `1705-1707,1317,1708`; non-break chance `10/20/30/50/90%` after rescue | `getSoulRingSurvivalChance`; `Player.checkRingOfLife`, invoked by melee, projectile, and elder-dragon damage paths | Teleport restrictions remain authentic. Complete. data/runtime. |
| Law, Skill Banking | `1714-1718`; `100/200/300/500/1000` charges | Law charge accessors; manual item use and Woodcutting/Mining/Fishing/Harvesting `Equipment.bankSkillingDropWithLawRing` paths | Non-stack resources only; one charge per item. Complete. data/runtime. |
| Mind, Hearthcraft | `3076-3080`; Cooking/Herblaw/Fishing XP `5/10/15/25/50%` | `getMindRingXpBonus`; central XP path | Complete. data/runtime. |
| Body, Gains | `3081-3085`; Melee/Attack/Defense/Strength/Hits XP `5/10/15/25/50%` | `getBodyRingXpBonus`; central XP path | Complete. data/runtime. |
| Death, Reckoning | `3086-3090`; charge caps `20/30/40/60/100`, `+1` yellow NPC damage per 10 charge | Death-ring charge/damage getters; NPC kills charge; `StatRestorationEvent` decays; melee, scythe, and projectile direct-hit paths spend no charge but apply current bonus | NPCs and non-summons only. Complete. data/runtime. |
| Blood, Vitality | `3091-3095`; see Blood section | Blood section above | Complete. |
| Life, Endurance | `3096-3100`; support summon duration `+20/+40/+60/+80/+100%` | `getLifeRingSupportDurationPercent`; support summon lifetime calculation in `Summoning` | Utility summons unaffected. Complete. data/runtime. |

### Special amulets

| Item/effect | IDs; tier values; description | Getter and all activation points | Status/coverage |
| --- | --- | --- | --- |
| Law, Teleportation | `1709-1713`; all store 3 teleports | `getLawItemMaxCharges/getLawAmuletTier`; `LawJewelry` inventory action and Law-altar recharge | Tiers 1-4 offer ascending guild groups; tier 5 offers every rune altar. Complete. data/runtime. |
| Chaos, Chaos Weaving | `1719-1723`; base bonus rune yield `20/35/50/70/100%`; tiered Mind/Chaos/Death/Blood weights `50/25/20/5`, `42/27/23/8`, `35/28/25/12`, `27/29/28/16`, `20/30/30/20` | `getChaosAmuletYieldBonusPercent/BonusRuneWeights`; `Runecraft` | Bonus output is separate from base-rune XP. Complete. data/runtime and runecraft XP tests. |
| Death, Ruin | `1724-1728`; at 100 charge, 2-tile Burst `1-3/3-6/6-9/9-14/10-20` | Death Burst charge/radius/damage getters; `Npc.killedBy` → `Player.applyDeathAmuletBurst` | NPC kills charge at 10% of combat level; excludes killed target, summons, and invalid NPCs. Complete. data/runtime. |
| Blood, Siphoning | `1729-1733`; see priority section | Priority section above | Direct attack omissions fixed; indirect scope documented. |
| Mind, Attunement | `1734-1738`; Magic/Summoning/Prayer XP `5/10/15/25/50%` | `getMindCombatAmuletXpBonus`; central XP path, including good/evil magic and prayer subskills | Complete. data/runtime. |
| Body, Prowess | `1739-1743`; Agility/Thieving/Ranged XP `5/10/15/25/50%` | `getBodyDisciplineAmuletXpBonus`; central XP path | Complete. data/runtime. |
| Nature, Alchemy | `1744-1748`; auto-alch monster drops worth at least 1000 gp; `100/200/300/500/1000` charges | Nature Alchemy charge getters; all NPC drop routes call `Equipment.tryAlchemyMonsterLootWithNatureAmulet`; `NatureAlchemyAmulet` checks/recharges | Requires inventory room for coins; one charge per successful item conversion. Complete. data/runtime. |
| Cosmic, Bounty | `1749-1753`; rare gathering duplication `10/20/30/40/50%` | `getCosmicAmuletRareGatheringDoubleChance`; Fishing/Mining/Woodcutting/Harvesting rare-reward paths | Does not duplicate ordinary base resources. Complete. data/runtime. |
| Soul, Renewal | `1754-1758`; at 200 charge, 2-tile healing `1-2/1-3/2-4/3-6/5-10` | Soul Burst charge/radius/heal getters; `Npc.killedBy` → `Player.applySoulAmuletBurst` | NPC kills charge at 10% combat level; heals owner and nearby players, capped to max Hits. Complete. data/runtime. |
| Life, Command | `3106-3110`; combat summon max damage `+1/+2/+3/+4/+5` | `getLifeAmuletSummonMaxDamageBonus`; combat summon max-hit calculation in `Summoning` | Complete. data/runtime. |

## Enchanted staves and wool robes

### Staves

The 14 rune lines are Air, Mind, Water, Earth, Fire, Body, Cosmic, Chaos,
Nature, Law, Death, Blood, Soul, and Life. Each line contains ten wood tiers:
Staff, Pine, Oak, Willow, Palm, Maple, Yew, Ebony, Magic, and Blood. Exact
IDs in that wood-tier order are:

| Rune | Staff IDs |
| --- | --- |
| Air | `101,2132,1765,1770,2137,1775,1780,2142,1785,2147` |
| Mind | `2238-2247` |
| Water | `102,2133,1766,1771,2138,1776,1781,2143,1786,2148` |
| Earth | `103,2134,1767,1772,2139,1777,1782,2144,1787,2149` |
| Fire | `197,2135,1768,1773,2140,1778,1783,2145,1788,2150` |
| Body | `2248-2257` |
| Cosmic | `2258-2267` |
| Chaos | `2268-2277` |
| Nature | `2278-2287` |
| Law | `2288-2297` |
| Death | `2298-2307` |
| Blood | `2308-2317` |
| Soul | `2318-2327` |
| Life | `2754-2763` |

- Advertised/runtime effect: a matching enchanted staff has a `50%` chance to
  preserve each matching rune.
- Getter: `getStaffRunePreservationChance(itemId, runeId)`.
- Runtime points: ordinary spell rune consumption in `SpellHandler` and summon
  rune consumption in `Summoning`.
- Elemental combination staves preserve their documented rune sets through
  `isStaffForRune`.
- Status: no missing runtime path found. data/runtime coverage.

### Wool robes

Each of the same 14 rune lines has five slots (hat, top, skirt, gloves, boots)
and ten tiers. Exact numeric IDs are the five matrices
`WOOL_HAT_PRODUCTS`, `WOOL_TOP_PRODUCTS`, `WOOL_SKIRT_PRODUCTS`,
`WOOL_GLOVE_PRODUCTS`, and `WOOL_BOOT_PRODUCTS`.

Every piece has two advertised/runtime layers:

1. `10%` matching-rune preservation through
   `getWoolRobeRunePreservationChance`, used by both `SpellHandler` and
   `Summoning`.
2. A rune-line effect based on the sum of the ten-tier values of all equipped
   pieces in that line:

| Rune effect | Advertised tier value | Getter/calculation and every runtime activation | Result/coverage |
| --- | --- | --- | --- |
| Air resistance | `2%` less Air magic damage per total tier, capped at `100%` | `getAirRobeTierTotal`; `Player.applyElementalRobeResistance`; typed projectile damage in `ProjectileEvent` | Complete. data/robes. |
| Mind spell caps | Mind-spell cap `+1%` per total tier, capped at `+50%` | `Player.getMindRobeSpellCapBonus`; applied after Chaos gauntlets in `SpellHandler` | Complete. data/robes. |
| Water resistance | `2%` less Water magic damage per total tier, capped at `100%` | Water tier getter and shared elemental resistance path | Complete. data/robes. |
| Earth resistance | `2%` less Earth magic damage per total tier, capped at `100%` | Earth tier getter and shared elemental resistance path | Complete. data/robes. |
| Fire resistance | `2%` less Fire magic damage per total tier, capped at `100%` | Fire tier getter and shared elemental resistance path | Complete. data/robes. |
| Body stored power | Damage taken stores up to `+1` melee/ranged/magic weapon power per total tier | `chargeBodyRobeWeaponPower/getBodyRobeWeaponPowerBonus`; every `applyRobeDamageMitigation` path charges after elemental resistance; player offense getters consume the bonus; `StatRestorationEvent` decays one per 10 ticks | Complete. data/robes. |
| Cosmic critical hit | `1%` crit chance per total tier, capped at `50%`; crit rolls attack maximum | `getCosmicRobeCritChance/rollCosmicRobeCrit`; all melee, ranged, and magic formulas in `CombatFormula` | Complete. data/robes. |
| Chaos surrounded damage | `+2%` outgoing damage per total tier **per adjacent valid enemy** | `getChaosRobeSurroundedDamageMultiplier`; all player melee/ranged/magic damage formulas through `CombatFormula.applyDamageMultiplier` | NPC adjacency only; excludes summons and invalid/dead NPCs. Complete. data/robes. |
| Nature potions | Potion values `+2%` and durations `+2%` per total tier, capped at `+100%` | `getNatureRobePotionBonusPercent`; `Player.applyPotionPowerBonus` and `applyPotionDurationBonus`, used by timed potion activation | Complete. data/robes. |
| Law runecrafting | `2%` extra runes per total tier | `getLawRobeRunecraftBonusPercent`; `Runecraft.addLawRobeBonusRunes` after successful base production | Per-rune fractional carry; bonus output awards no XP. Complete. data/robes/runecraft tests. |
| Death overkill splash | `2%` of overkill per total tier, capped at `100%` | `getDeathRobeOverkillSplashPercent`; NPC direct melee in both combat engines, scythe cleave, and direct projectiles | NPC area damage only; no players/summons. Complete. data/robes. |
| Blood-spell splash | `2%` of primary blood-spell damage per total tier, capped at `100%` | `getBloodRobeSpellSplashPercent`; blood classification in `SpellHandler`; direct projectile resolution and nearby NPC splash in `ProjectileEvent` | This audit corrected primary overkill to use actual damage dealt. Splash does not recursively Siphon. data/robes/runtime. |
| Soul regeneration | health regeneration speed `+2%` per total tier, capped at `+100%` | `getSoulRobeHealthRegenerationBonus`; Hits timing in `StatRestorationEvent` | Complete. data/robes. |
| Life summons | combat summon health or support duration `+2%` per total tier, capped at `+100%` | `getLifeRobeSummonBonusPercent`; `Summoning.getScaledHits/getDurationTicks` | Combat/support only; utility summons unaffected. Complete. data/robes. |

Equipment stats are separately derived by `getWoolRobeMagicDefense`,
`getWoolRobeMeleeDefense`, and `getWoolRobeRangedDefense` and consumed by
`Equipment`.

## Retired compatibility APIs and inconsistencies

These methods remain for compatibility or historical callers but intentionally
advertise no current item effect:

- `getSpeedBonus`, `getHighRollBias`, `getDefenseBonus`, `getOffenseBonus`,
  `getElementalRingDamageBonus`, `getNatureRingForgingChance`, and
  `getRunePreservationRune` reflect superseded designs or aliases and are not
  active effect entry points.
- `getDeathAmuletDamagePerKillBonus`,
  `getMindAmuletPotionDurationBonus`,
  `getBodyAmuletRegenSpeedBonus`,
  `getCosmicAmuletExtraResourceChance`, and
  `getCosmicAmuletHerbQualityChance` return zero.
- `getCosmicAmuletGemChanceMultiplier` returns neutral `1.0`.
- Old Death-amulet stack methods remain on `Player`, but the getter is zero;
  current Death amulets use charged Ruin Burst instead.

They were not reactivated because current item names and descriptions advertise
the replacement effects. Removing the compatibility surface is a separate
cleanup task that should first prove no downstream plugin uses it.

The older `jewelry-and-retired-robe-effects.md` contains useful design history
but also superseded slot assignments, names, and Law charge values. It is no
longer the runtime source of truth and now points here.

## Regression checklist

Existing coverage retained:

- `test-enchanting-data.py`: generated IDs, names, descriptions, tier lines,
  and client/server definition agreement.
- `test-jewelry-runtime-effects.py`: formulas and runtime wiring for every
  jewelry family.
- `test-enchanted-robe-effects.py`: all robe matrices, tier defenses, and rune
  preservation.
- prayer tests: Corrosive Aura equipment/prayer gates and all direct combat
  activation points.
- runecraft tests: base rune XP versus Chaos/Law equipment bonus output.

Coverage added by this audit:

- compiled execution of the real `Leach` helper for zero damage, zero percent,
  full health, minimum-one rounding, flooring, exact percentage, missing-Hits
  cap, state mutation, and removed-player behavior;
- exactly-once Siphoning guards for PvM melee, PvP melee, ranged, magic, and
  scythe cleave;
- assertions that direct paths use actual target-capped damage;
- assertions excluding chain lightning, blood-robe splash, summon damage,
  Corrosive Aura, and poison from direct Siphoning;
- Corrosive Aura poison-owner attribution and Necklace-of-Leach routing;
- poison overkill/Goblin’s-Tenacity accounting through the shared actual-damage
  return value.

## Final recommendations

1. Keep the fixed direct-hit policy: all three direct styles in PvM/PvP plus
   explicit scythe cleave.
2. Keep poison—including Corrosive Aura—on Necklace of Leach only.
3. Keep reflected, summon, proc, and other indirect damage out of Siphoning
   unless a future balance decision explicitly names the included families.
4. In a later cleanup, rename the “lifesteal chance” getters to “Siphoning
   percent” and remove proven-unused retired getters. Their current names are
   misleading but do not alter behavior.
