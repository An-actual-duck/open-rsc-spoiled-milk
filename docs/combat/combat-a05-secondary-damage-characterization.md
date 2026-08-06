# A05.4 Secondary-Damage Characterization

## Scope and conclusion

A05.4 is an inventory and executable-characterization milestone. It changes no
combat formula, damage settlement, death authority, or production call site.
The inventory was made against published main `06580f6e0`, after the bounded
A05.3 primary-projectile migration.

The central finding is that "secondary damage" is not one policy. Current code
contains at least seven materially different contracts:

1. event-owned secondary hits with event-specific terminal adapters;
2. reflected hits with different attribution and recursion exclusions;
3. player-outgoing area hits with style-dependent contribution;
4. boss/player-incoming area hits with their own mitigation and party updates;
5. delayed spell hits whose scheduling is part of their contract;
6. poison and burn lifecycle state; and
7. the broad `Mob.damage` compatibility helper used by environmental and
   script content.

The scan found 32 direct Hits-subtraction sites outside
`ResolvedDamageTransaction`, 139 calls to `Mob.damage` or
`damageAndGetActualDamage` (129 calls in 54 plugin files), and two additional
debug/admin terminal `setLevel(HITS, 0)` paths. These are inventory facts, not
a proposal to migrate them together.

Implementation update: A05.4A subsequently moved only the six auxiliary Magic
and true HP/update/hitsplat blocks through the transaction. Their exact stable
identities and preserved boundaries are recorded in
[`combat-a05-auxiliary-damage-transaction.md`](combat-a05-auxiliary-damage-transaction.md).
All other inventory rows remain outside that migration.

## Reproducible inventory method

Run these from the repository root:

```bash
rg -n --glob '*.java' \
  'subtractLevel\(Skill(s)?\.HITS(\.id\(\))?' server/src server/plugins
rg -n --glob '*.java' \
  '\.(damage|damageAndGetActualDamage)\(' server/src server/plugins
rg -n --glob '*.java' \
  'set(Level|TemporaryLevelAndMaxStat)\(Skill(s)?\.HITS(\.id\(\))?' \
  server/src server/plugins
```

Healing, stat initialization, and NPC reset-to-maximum sites from the third
command are not damage. The two relevant zero assignments are the admin
`killPlayer` command and the development `killnpcs` command.

## Shared compatibility-helper contract

`Mob.damage(int)` selects a standard hitsplat and delegates to
`Mob.damageAndGetActualDamage(int, int)`. That helper currently:

- applies Goblin Tenacity only when the target is a player and damage is
  positive; no robe, potion, prayer, True Defense, Cleric, or summon mitigation
  lives here;
- returns factual damage capped to nonnegative current Hits, while displaying
  the post-Tenacity requested damage, including lethal overkill;
- attributes a lethal hit only through the target's current `getOpponent()`;
- records no combat/magic/ranged/summon contribution and applies no lifesteal
  or aggro itself;
- calls `killedBy(getOpponent())` before the player stat packet, damage update,
  and hitsplat; and
- accepts negative values, which can increase Hits while reporting zero factual
  damage. No migration may silently turn that compatibility behavior into
  rejection without a separately approved cleanup.

The A05.4 fixture executes the lethal order: the NPC death callback sees no
damage update or hitsplat, then the helper publishes the requested poison
overkill. Moving this helper wholesale would therefore change hook order even
if final Hits were identical.

## Core policy inventory

"Packet" below means explicit stat or party transport in addition to ordinary
update flags. "No lifesteal" means the local damage routine applies none; a
parent effect may aggregate damage later where called out.

| Family and exact symbols | Mitigation | Attribution and contribution | Presentation and packets | Aggro, death, and hook order |
| --- | --- | --- | --- | --- |
| Unknown/compatibility projectile type: `ProjectileEvent.projectileDamage` fallback at line 416 | Common pre-impact robe/potion/summon absorption and Frostbite; no True Defense; Cleric only for types 1/2/4 | Existing projectile style branches do not recognize an unknown type; no style contribution | Summon-style hitsplat; player stat and surrounding projectile hooks still run | Existing `handleDeath`; compatibility settlement remains outside A05.3; no independent lifesteal policy |
| Projectile chain lightning: `applyChaosAmuletChainLightning` / `inflictChainLightningDamage` | Player targets receive potion Magic reduction for types 1/4 or potion Ranged reduction for 2/5; no robe, True Defense, or Cleric | Player caster; NPC contribution follows originating Magic/Ranged type and caps to Hits | Armor-proc hitsplat, chain projectile, player stat | Primary target uses event `handleDeath`; child uses `killedBy(caster)`; no local lifesteal or aggro |
| Melee chain lightning and jewelry recoil: `CombatEvent` lines 219–275 and `PvmMeleeEvent` lines 219, 845–875 | None | `inflictJewelryEffectDamage` always records player-to-NPC **combat** contribution, including chain and reflected jewelry damage | Armor-proc hitsplat; player stat | Event `onDeath` owns terminal state/reset; no local lifesteal; chain target selection has no new aggro |
| Projectile recoil: `ProjectileEvent.recoilDamage` | None | Defender is killer; unlike melee jewelry helper, no contribution is recorded | Armor-proc hitsplat; no explicit player stat packet | Ranged reset on lethal types 2/5; otherwise Ring of Life check; direct `killedBy`; no recursive reflection/lifesteal |
| Frostbite: `applyFrostbiteReflection` and `inflictFrostbiteReflectedDamage` in `CombatEvent`, `PvmMeleeEvent`, and `ProjectileEvent` | None on reflected half; reflected amount is subtracted from the pending primary hit | Consumed Frostbite source is credited; player-source-to-NPC contribution is Magic | Armor-proc hitsplat; player stat | Event adapters own melee death; projectile calls `killedBy(creditedSource)`; no lifesteal or aggro |
| Splinter: `ProjectileEvent.applySplinter` | None; guard-dog area suppression, radius 2, attackable living non-summon NPC selection | Projectile caster, Magic contribution | Armor-proc hitsplat | Starts chase when eligible and `shouldChase`; direct `killedBy(caster)`; no lifesteal |
| Blood robe splash: `ProjectileEvent.applyBloodRobeSplash` / `inflictBloodRobeSplashDamage` | None; guard-dog suppression, radius 2 | Player caster, Magic contribution plus summon-owner assist recording | Armor-proc hitsplat | Direct Magic kill type/state and `killedBy`; no aggro or local lifesteal |
| Death robe overkill splash: same-named methods in all three combat events | None; only positive primary overkill, guard-dog suppression, radius 2 | Player caster; projectile uses originating Magic/Ranged contribution, melee uses combat; summon-owner assist | Armor-proc hitsplat | Direct source-style kill type/state and `killedBy`; no aggro or lifesteal |
| Auxiliary Magic: `inflictAuxiliaryMagicDamage` in all three combat events | Player target: robe then potion Magic reduction | Player-to-NPC Magic contribution | Armor-proc hitsplat; player stat | Owning event's death adapter; no local aggro or lifesteal |
| Auxiliary "true": `inflictAuxiliaryTrueDamage` in all three combat events | Despite its name, player target still receives robe mitigation; no potion/True Defense/Cleric | Player-to-NPC combat contribution | Armor-proc hitsplat; player stat | Owning event's death adapter; no local aggro or lifesteal |
| Bear second hit, dragon-weapon breath, elemental sword, demon pitchfork, infernal armor, and blue/earth/red/black/elder-green dragon-armor procs | Delegate to the two auxiliary policies above | Delegate to auxiliary Magic or combat contribution | Their own projectile/combat-effect flag precedes an armor-proc hitsplat | Run after primary settlement in each event; terminal auxiliary hit stops later target-side work according to that event |
| Scythe cleave: `PvmMeleeEvent.inflictScytheCleaveDamage` | Summon outgoing cap helper only; no target mitigation | Player combat contribution plus summon-owner assist | Standard hitsplat, including an explicit zero splat | Zero starts aggro; positive applies Divine Grace, Blood Amulet and summon lifesteal, Death Ring, death-robe splash, party update, combat timer, and direct terminal state in that exact order |
| Balrog splash: `ProjectileEvent.applyBalrogMagicSplash` | Fire-element robe, potion Magic, then True Defense | Balrog is killer; no player contribution | Armor-proc hitsplat, inherited impact effect, damage/blocked tracking, player stat | No Cleric direct effects, party update, Ring of Life, reflection, or aggro; direct `killedBy(balrog)` |
| Elder Green Dragon sweep, fireshot, and burn: `ElderGreenDragonSpecialAttacks.inflictPlayerDamage` | Style robe + potion + summon absorption; True Defense for melee/ranged/magic but not burn | Dragon is killer; no contribution | Caller-selected standard or armor-proc hitsplat; player stat and party update | Corrosive Aura then Divine Retribution; reflected dragon death precedes victim death; otherwise victim death or combat timer/Ring of Life. Burn retains this owned source rather than `BurnEvent`'s helper attribution |
| Summon bonus damage: `Summoning.inflictSummonBonusDamage` | Player target robe, then potion Magic or Melee | NPC target records summon contribution to online owner | Armor-proc hitsplat; player stat | Returns a lethal boolean to its caller; no local death, aggro, or lifesteal |
| Divine Retribution: `DivineRetribution.apply` | None; damage is twice the already-applied incoming damage | Defending player; NPC attacker receives combat contribution | Divine effect plus armor-proc hitsplat; player attacker stat | Returns `killedAttacker`; each caller settles death in its own order; cannot recursively proc by itself |
| Cleric Thorns: melee event calls to `inflictJewelryEffectDamage`; `ProjectileEvent.inflictClericThornsDamage` | None | Protected player receives combat contribution for NPC attacker | Armor-proc hitsplat; player attacker stat | Runs after established lifesteal; direct/event-specific death adapter; no recursive Thorns, recoil, or lifesteal |
| Death Amulet burst: `Player.applyDeathAmuletBurst` | None; charged kill-triggered radius selection | Wearing player, combat contribution | Armor-proc hitsplat | Direct `killedBy`; no summon-owner assist, aggro, lifesteal, explicit kill type, or player packet |
| Death Ring charged hit: `Player.applyDeathRingChargeHit` | None | Wearing player, combat contribution plus summon-owner assist | Armor-proc hitsplat | Returns lethal boolean; caller owns terminal order; no local aggro/lifesteal |
| Generic burn: `BurnEvent.run` | Compatibility helper only, including player Goblin Tenacity | No stored source; helper uses current opponent only on lethal | Standard hitsplat; player cache/message before damage | Decrements pulse/cache before damage and extinguishes afterward; no contribution, aggro, or lifesteal |
| Poison: `PoisonEvent.run` | Poison-power cure/reduction gates, then compatibility helper; Goblin Tenacity can reduce player HP loss | Stored owner UUID is used only for Blood Necklace Leach, not helper kill credit or contribution | Poison hitsplat; power/cache/message before damage | Leach uses factual returned damage after settlement; helper death-before-presentation order; poison lifecycle/death reset stays an A08 concern |
| Desert heat: `DesertHeatEvent` | Waterskin prevention before random damage; helper Tenacity only after that | Environmental; lethal helper attribution is current opponent | Standard helper hitsplat/stat | No contribution/lifesteal/aggro; heat messages and waterskin mutation precede damage |
| Dragon breath compatibility helpers: `DragonFireBreath.executeScript`, `RangeUtils.applyDragonFireBreath`, and pre-cast branch in `SpellHandler` | Each caller computes shield/current-Hits formula first; helper Tenacity afterward | Lethal helper attribution is current opponent | Standard helper hitsplat/stat | Ranged drain and messages remain caller-specific; no contribution/lifesteal/Cleric policy |
| Delayed god/Iban area damage: `SpellHandler.applyGodSpellAreaEffects`, `applyIbanBlastAreaEffects`, `applyGodSpellSecondaryDamage` | Helper Tenacity only | Caster receives Magic contribution after helper settlement; outer god spell aggregates returned damage for one later lifesteal | Standard helper hitsplat/stat | Surviving NPC starts chase. Helper may already have called death using current opponent before the caster contribution is added; one-tick scheduling is authoritative |
| Delayed Salarin strike: anonymous `MiniEvent` at `SpellHandler:1993–2014` | Player target potion Magic reduction only | Caster receives Magic contribution on NPC | Damage update but **no hitsplat** and no explicit player stat; party send checks caster party | Direct `killedBy(caster)` after update; no lifesteal/aggro/XP; one-tick delay and primary-before-secondary order are authoritative |
| `Functions.substat` Hits compatibility | Delegates Hits reduction to helper specifically to preserve Potion of Zamorak splat | Current opponent on lethal only | Standard helper hitsplat/stat | No contribution/lifesteal/aggro |
| Tutorial rat safety script and `NpcBehavior` gnomeball tackle | Generic helper | Current opponent on lethal; tutorial script damages the attacker itself | Standard helper hitsplat/stat | Script-specific dialogue/state wraps helper; no contribution/lifesteal |
| Admin `kill`, `damage`, `damagenpc`, development `killnpcs`, `ResetCrystal.smiteNpc` | None | Explicit admin player is passed to direct `killedBy` | Damage update only; no hitsplat or normal stat policy | Debug/administrative compatibility paths, not production combat; preserve separately and do not use them as transaction exemplars |
| `ArmyOfObscurity` Necronomicon self-harm | Guarded above 3 Hits; no mitigation | None | Damage update only, no hitsplat/stat | Cannot kill due guard; dialogue follows mutation |

## Plugin/environmental helper appendix

All 129 plugin calls below invoke `Mob.damage(int)` and therefore inherit the
shared helper contract unless their surrounding script prevents or sequences
the hit. The list is exhaustive for this baseline; numbers are source lines.

| Area | Exact files and call lines |
| --- | --- |
| Defaults, items, minigames, misc, NPCs | `DoorAction:468`; `InvAction:685`; `ExitBarrel:25`; `GnomeNpcs:319`; `MageArena:260`; `RandomObjects:167`; `StrangeBarrels:150`; `Zamorak:79`; `falador/Barmaid:52`; `varrock/Bartender:97` |
| Free and ordinary member quests | `VampireSlayer:333`; `ClockTower:264`; `DwarfCannon:532`; `GertrudesCat:415`; `HerosQuest:463`; `MerlinsCrystal:250`; `SeaSlug:641,667`; `TempleOfIkov:523`; `TribalTotem:443`; `Waterfall_Quest:342,353,471,502`; `WitchesHouse:297`; `digsite/DigsiteMiscs:25,33,41,50` |
| Legends Quest | `LegendsQuestInvAction:115`; `LegendsQuestEchnedZekin:88,425`; `LegendsQuestNezikchened:151`; `LegendsQuestUngadulu:667,796,827`; `LegendsQuestCaveAgility:52,56,60,64,68,95,123,151,179`; `LegendsQuestGameObjects:243,409,413,669`; `LegendsQuestWallObjects:121,142,189,194,201,402` |
| Shilo Village and Tourist Trap | `ShiloVillageNazastarool:90`; `ShiloVillageObjects:118,120,141,177,234,237,277,279,326,427,648`; `ShiloVillageUtils:45,48,51,224`; `TouristTrap:1400,2041,2765,3153`; `Tourist_Trap_Mechanism:437` |
| Underground Pass | `UndergroundPassDwarfs:48`; `UndergroundPassKardiaTheWitch:58`; `UndergroundPassAgilityObstacles:60,133`; `UndergroundPassDungeonFloor:51`; `UndergroundPassObstaclesMap1:142,157,221,237,264,328,402`; `Map2:116,126,175,201`; `Map3:65,96,163,200`; `UndergroundPassOrbs:59,125,136,354`; `UndergroundPassPuzzle:68`; `UndergroundPassWell:34` |
| Watchtower and skills | `WatchTowerGorad:52`; `WatchTowerShaman:29`; `AgilityShortcuts:125,128,137,171,320,343,446,484,499,624,660,705,742,773`; `BarbarianAgilityCourse:90,111,136`; `WildernessAgilityCourse:120,132,171`; `Herblaw:675`; `Thieving:473`; `Woodcutting:150` |
| Custom content | `custom/minigames/micetomeetyou/Death:74`; `custom/misc/AgilityCape:41` |

The short names in this appendix are unambiguous under
`server/plugins/com/openrsc/server/plugins/`; rerun the inventory command
before implementation because line numbers will drift.

## Executable parity added in A05.4

`CurrentCombatSecondaryDamageCharacterization` adds four production-runtime scenarios to
the authoritative combat gate, growing it from 42 to 46:

- compatibility-helper lethal ordering, factual overkill, poison power/type,
  and burn state/type;
- projectile chain, auxiliary Magic, and auxiliary true damage presentation,
  contribution style, absence of local lifesteal, and chain aggro behavior;
- distinct Frostbite Magic attribution and Cleric Thorns combat attribution,
  including terminal callback cardinality; and
- delayed god/Iban secondary helper presentation, Magic contribution, chase,
  and absence of per-target local lifesteal.

Existing fixtures continue to cover chain exclusion from the A05.3
transaction, Cleric direct-effect/Thorns order, scythe targeting and
lifesteal, poison death/respawn cleanup, and summon contribution. No observer
event is expected from any newly characterized path because no authority moved.

Salarin's anonymous delayed event, Elder Green Dragon's private style enum,
and equipment-RNG-dependent robe/reflection entry points do not expose a small
isolated production seam. Their source policies are recorded above. A later
implementation family must add full-path deterministic fixtures before moving
them; this inventory branch does not introduce production seams merely to make
reflection convenient.

## Ordered implementation families

Each item below is a separate follow-up branch with its own stop gate.

1. **A05.4A — duplicated event-local auxiliary hits (implemented; pending
   manager review).** Only the adjacent
   Hits/update/hitsplat blocks in `inflictAuxiliaryMagicDamage` and
   `inflictAuxiliaryTrueDamage` across `CombatEvent`, `PvmMeleeEvent`, and
   `ProjectileEvent`. Use separate effect identities and preserve robe/potion
   asymmetry, contribution style, returned value, and each event's death
   adapter moved. See the bounded implementation record linked above.
2. **A05.4B — reflection families.** Characterize and migrate Frostbite,
   Cleric Thorns, projectile recoil, melee jewelry recoil, and Divine
   Retribution individually. Preserve source attribution, incoming-hit
   reduction, post-lifesteal Thorns order, no-recursion rules, ranged reset,
   Ring of Life, and caller-owned simultaneous death.
3. **A05.4C — player-outgoing child/AoE hits.** Split chain lightning,
   Splinter, blood/death robe splashes, Scythe cleave, Death Amulet, and Death
   Ring into effect-specific requests. Preserve selection, layer/range/area
   suppression, style contribution, chase, zero-hit aggro, charge settlement,
   lifesteal, and per-child death order. Stop if an AoE eligibility policy must
   be generalized to migrate HP.
4. **A05.4D — owned NPC/summon/boss secondary hits.** Treat Balrog splash,
   Elder Green Dragon attacks/burn, and summon bonus damage as separate
   adapters. Preserve elemental mitigation, True Defense exclusions, blocked
   tracking, party packets, reflection order, Ring of Life, and returned
   lethal booleans.
5. **A05.4E — delayed spell secondaries.** Add full scheduled fixtures for
   Salarin and god/Iban area effects before moving HP. Preserve one-tick delay,
   rune/XP behavior, missing Salarin hitsplat/stat behavior, outer aggregated
   god-spell lifesteal, contribution/chase timing, and current death attribution.
6. **A08 — typed poison and burn provenance/lifecycle.** Do not fold DoTs into
   an A05 helper migration. First settle owner persistence, offline owner,
   replacement, death/respawn, Leach, and kill-credit rules as already required
   by A08.
7. **Compatibility-helper and script migration, last.** Inventory each of the
   129 plugin calls by intent before changing `Mob.damage`. Define explicit
   environmental, scripted, self-harm, and admin/debug source categories;
   preserve or deliberately replace death-before-presentation only with owner
   approval. Debug/admin direct mutations remain a separate cleanup branch.

## Acceptance and stop gates

For every implementation family, verify exact final Hits, displayed overkill,
hitsplat type/cardinality, contribution style and cap, lifesteal count, aggro,
kill source/type, update/stat/party packets, terminal callback count, and child
hook order. Run the 46-scenario combat gate, relevant focused content tests,
authoritative core/plugin builds, production-artifact exclusion, and
changed-code static analysis.

Stop on any formula, RNG draw/order, mitigation, target selection, radius,
line-of-effect, resource, XP, contribution, lifesteal, aggro, packet,
hitsplat, death, effect, timing, or callback-cardinality delta. A passing final
Hits assertion alone is not parity.
