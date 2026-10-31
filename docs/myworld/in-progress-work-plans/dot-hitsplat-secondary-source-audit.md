# Hit-number catchall source audit

October 31, 2026. Current worker implementation, based on code at
`850783d55c88c0fb57bf4576b0d163363208e61d`; Core's concurrent main changes have
not been imported. This is a source inventory and proposal for Justin, not a
final handoff or an approved color implementation.

Justin has accepted the floating behavior and retaining both selectable styles.
Only glyph/background centering is changed in this increment. No additional
damage types, gameplay rules, policies or palette assignments are implemented.

## Implemented source grouping (supersedes palette proposal below)

Justin chose source ownership, with red as the fallback for anything not otherwise
classified. The inventory and original proposal below are historical audit evidence.
The implemented mapping is:

- Red/type 0: main attacks and weapon/spell offshoots, including Dragon weapon
  breath, elemental swords, Pitchfork Hell Blaze, Frostbite reflection, Splinter,
  Naga/Terror Dog extra main strikes, Balrog splash and Elder boss initial Fireshot.
  Cleric Thorns and Divine Retribution also use the confirmed red default.
- Purple/type 2: armor/jewelry procs, including Infernal/Hell's Inferno, dragon
  armor effects, Bear/Ferocious extra hits, Chaos necklace chain, Dark Beast
  discharge, blood/death robe splash and death jewelry effects. Recoil/type 5
  retains its distinct priority but shares the same purple palette.
- Yellow/type 4: summons, including Relentless, Spell Echo and Hellfire bonus hits.
- Green/type 1: independent poison; blue/type 3: healing.
- Orange/new additive type 6: explicit Elder armor and Elder boss burn pulses.
  Initial Elder armor direct/splash remains purple; initial boss attack is red.
- Unknown client presentation IDs normalize to red, preserving paired-update
  deduplication. Classic damage updates remain unchanged.

Generic auxiliary settlement keeps an armor default for its armor callers; weapon
callbacks pass standard presentation explicitly. Bear/Ferocious is purple because
its actual gate is an armor set, despite the extra hit happening during an attack.
No damage amounts, RNG/chances, policies, timing or settlement rules changed.
There is no remaining ambiguous existing burn producer in the audited code.

## Coverage and classification boundary

Audited all tracked Java under `server/src` and `server/plugins`: every
`TYPE_ARMOR_PROC` selector, every `hitSplatType(...)` assignment, `new HitSplat`,
both `Mob.damage` overloads, `damageAndGetActualDamage`, and the variable-type
forwarders. Parenthesized call arguments were checked, rather than treating a
comma inside a damage formula as a second type argument. There are **32 catchall
selector references in 10 production files**, excluding the constant declaration.
The selector table below accounts for all 32. Generic callbacks and their
source-specific callers were then followed separately.

Emission chain: [ResolvedDamageTransaction](../../../server/src/com/openrsc/server/runtime/ResolvedDamageTransaction.java)
uses the request's presentation type; [HitSplat](../../../server/src/com/openrsc/server/model/entity/update/HitSplat.java)
stores that type and amount; GameStateUpdater serializes update 11 unchanged;
the client has no source identity beyond its type byte. [DamageRequest](../../../server/src/com/openrsc/server/model/combat/DamageRequest.java)
defaults its int type to 0, not 2. Single-argument `Mob.damage(amount)` explicitly
chooses STANDARD. No plugin or external caller supplies a numeric/aliased type 2
through the two-argument Mob APIs in this tree. Names such as projectile attack
`type == 2` mean ranged attacks, not yellow hit splats.

Current presentation IDs: 0 primary/red; 1 poison/green; 2 catchall/orange
(historically yellow); 3 healing/blue; 4 summon/yellow; 5 jewelry recoil/purple.
The name ARMOR_PROC is misleading: type 2 is not restricted to armor.

Short source names below:

- COM: [CombatEvent](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java), reciprocal/shared melee.
- PVM: [PvmMeleeEvent](../../../server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java).
- PROJ: [ProjectileEvent](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java), spell/bow/thrown/cannon owners.
- Numbers are source line anchors at this audited revision.

## Complete catchall selector inventory

| Settlement boundary | All catchall selectors | What reaches it |
| --- | --- | --- |
| Auxiliary magic | COM:768, PVM:939, PROJ:1206 | Infernal Fire; Hell's Inferno splash; melee Demon Pitchfork Hell Blaze. These are formula/settlement aliases, not three damage elements. |
| Auxiliary true | COM:801, PVM:972, PROJ:1239 | Water, Earth and Red Dragon armor procs; Black/KBD armor followups; Bear/Ferocious extra hit; Dragon weapon followup; elemental swords. PROJ has only the subset supported by projectile owners. |
| Offhand-flagged main settlement | COM:611, PVM:473 | Naga second weapon and Terror Dog Frenzy bites. The flag is reused; it does not mean player offhand damage generally. |
| Frostbite reflection | COM:408, PVM:664, PROJ:855 | Ice-spell-debuff return hit when its victim next attacks; source identity may fall back to target if missing. |
| Shared reflected combat | COM:369, PVM:1064 | Cleric Thorns remains type 2. Jewelry recoil takes the other conditional branch and is already type 5. |
| Chaos Necklace chains | COM:329, PVM:1024, PROJ:492 | Same chain-lightning source across three attack owners and alternating hop graphics. |
| Wood spell Splinter | PROJ:891 | Branch Spore/Wood Drill/Battering Ram secondary-target hit, selected through launch metadata. |
| Blood robe splash | PROJ:1100 | Blood-rune-spell-triggered equipment splash. This is not the primary spell hit or its healing component. |
| Death robe overkill spill | COM:735, PVM:602, PROJ:1124 | Same overkill-spill behavior reached by melee, magic and ranged owners. |
| Balrog magic splash | PROJ:1172 | Boss magic impact splashes nearby players; launch magic-element metadata is retained for mitigation. |
| Elder dragon boss specials | ElderGreenDragonSpecialAttacks:125,355 | Ranged Fireshot and periodic burn pulses. Melee sweep explicitly uses standard/red instead. |
| Elder dragon armor | ElderGreenDragonArmorEffect:157 | Direct Elder Breath proc, area secondary targets, and five burn pulses share one owned-damage helper, distinguished by effect policy. |
| Summon bonus effects | Summoning:2018 | Relentless extra melee, Spell Echo, Hellfire and Spirit Hellfire. Both bonus melee/magic keys still select catchall. |
| Death jewelry | Player:2391,2435 | Death amulet charged kill burst and Death ring charge hit. Neither is jewelry recoil. |
| Electrically Charged | SlayerLeatherEffects:139 | Dark Beast set accumulates charge when its wearer takes qualifying direct HP loss, then discharges around the wearer. |
| Sullen Pendant | SlayerRewardCombat:101 | Reactive percentage-of-attacker-max-HP hit; not a copy/fraction of the incoming hit. |

## Source families behind the generic helpers

| Real source | Trace and distinction | Candidate classification |
| --- | --- | --- |
| Infernal Fire | COM:833, PVM:694, PROJ:991 → auxiliary magic. Equipment:1868 distinguishes Demon, Black Demon and Balrog sets (different max hits/chances), not new presentation types. | One fire family. |
| Hell's Inferno | COM:892–924, PVM:750–755, PROJ:1054–1057 → same auxiliary magic. Triggered by the Balrog-set Infernal max-hit variant; extra area damage rather than a separate spell element. | Fire; share Infernal's color. |
| Demon Pitchfork Hell Blaze | COM:952–956, PVM:782–786 → auxiliary magic; `DemonPitchforkHellBlazeProc` owns chance and rolls. | Fire; same family as other explicit fire damage. |
| Blue Dragon water | COM:863–866, PVM:727–730, PROJ:1025–1028 → auxiliary true; `BlueDragonWaterProc` adds water max-hit debuff. | Cold/water. |
| Earth Dragon | COM:867–870, PVM:730–733, PROJ:1029–1032 → auxiliary true; `EarthDragonSlowProc` adds Slow. | Earth/nature; Slow itself creates no extra number. |
| Red Dragon fire | COM:871–874, PVM:733–736, PROJ:1033–1036 → auxiliary true; fire-defense debuff. | Fire, despite this helper's MELEE/true settlement style. |
| Black Dragon armor | COM:879–882, PVM:740–743, PROJ:1041–1044 → auxiliary true. It is activated by a poison-application marker. | Breath/armor bonus needs a choice; do not label the immediate hit poison just because of the marker. Poison pulses are separate green events. |
| KBD armor | COM:883–885, PVM:744–746, PROJ:1045–1047 → auxiliary true. Additional payload plus random water/earth/fire debuff. | Same ambiguity: no one fixed damage element follows from its random debuff. |
| Bear Maul / Ferocious | COM:931–933, PVM:762–764 → auxiliary true. `BearMaulSecondHit` accepts either full Bear hide or the Terror Dog Ferocious set. | One extra physical hit family; not two colors just because two sets enable it. |
| Dragon weapon followup | COM:936–940, PVM:767–770; ranged PROJ:1060–1069. Shared breath/slash followup; graphics can randomly choose BREATH or SLASH_2 for the same payload. | Weapon bonus/breath; animation is not provenance. Decide physical-extra versus fire/breath. |
| Fire/Ice/Earth Sword | COM:943–949, PVM:773–779 → auxiliary true. CombatFormula:390–435 chooses the real sword effect; `ElementalSwordProc` passes only damage to the generic callback. | Fire, cold/water, earth if an element-based taxonomy is chosen. Preserve selected effect without rerolling it. |
| Naga second weapon | COM:266–269, PVM:313–316 → offhand=true. `NagaCombat.canOffhand` admits NPC 866 specifically; this is not a global player offhand system. | Extra physical hit. |
| Terror Dog Frenzy | COM:720–723, PVM:587–590 → offhand=true, though these are extra bites scaled by nearby dogs. | Same extra physical family, despite the offhand implementation alias. |
| Chaos Necklace chains | `ChaosChainLightningProc` → per-owner chain helper. Three hop graphics are aliases for traversal/payload progression. | Electrical. |
| Dark Beast Electrically Charged | `SlayerLeatherEffects.afterDamage`:155–166 → chargedHit → discharge. Catalog labels it REFLECTION, but it is stored-charge area retaliation, not proportional recoil. | Electrical is more descriptive than blindly using REFLECTION. |
| Frostbite return hit | Ice launch metadata → applyFrostbiteDebuff → next incoming attack's applyFrostbiteReflection in COM/PVM/PROJ. | Choose reflected-purple versus cold-cyan precedence. |
| Cleric Thorns | Incoming attack's Cleric runtime result → reflected helper; two melee owners plus projectile owner. | Direct retaliation/reflection if purple is broadened beyond jewelry. |
| Divine Retribution | `DivineRetribution.apply`:36–43, called from central melee/projectile reflection phases and Elder boss special settlement. Returns twice incoming damage, not jewelry recoil. | Reflection-purple, or distinct divine family only if desired. |
| Sullen Pendant | `SlayerRewardCombat.pendantHit`:85–101, called after settled damage in COM/PVM/PROJ. Uses attacker maximum HP and its own roll. | Retaliation-purple versus blood/death-magenta is a design choice. |
| Wood Splinter | SpellClassification:330–331 identifies wood spells; projectile launch retains chance → applySplinterOnHitEffect → applySplinter. | Earth/wood; not a generic ranged splinter just because a projectile carries it. |
| Blood robe splash | PROJ:1079–1100 follows bloodSpell launch eligibility and equipment splash percent. SpellClassification:334–342 defines that flag by requiring a Blood Rune, not a dedicated blood-damage element. | Blood/death-secondary family; keep actual healing blue. |
| Death robe spill | Three event adapters use `DeathRobeOverkillSplash`, then owner-local mitigation/credit. | Blood/death secondary, not primary attack style. |
| Death jewelry bonus/burst | Player:2356–2440 owns charged kill burst and charged bonus hit, called from central combat and kill paths. | Blood/death-secondary or general equipment-proc; explicitly not recoil. |
| Elder armor | `ElderGreenDragonArmorEffect.applyProc` → primary/area `inflictTrueDamage`; burn event → same helper with ARMOR_BURN key. | Fire/burn candidate; direct proc and periodic burn can share a color or be split if desired. |
| Boss Fireshot / burn | Elder boss:125/355 → inflictPlayerDamage → selected type. | Fire/burn if classifying effects; not a new color per boss. |
| Balrog splash | PROJ:1143–1172; half the primary settled damage, around primary target, retained launch element. | Classify from known launch element, or keep special-splash fallback where element is unspecified. |
| Summon on-hit traits | PVM:575 / PROJ:752 → Summoning:1049–1081 → inflictSummonBonusDamage:1995–2018. Relentless, Spell Echo, Hellfire, Spirit Hellfire bypass getSummonDamageHitSplatType. | Important missed branch: include these in summon-yellow if source takes precedence over element. |

The summon finding is a gap in the first split: normal summon melee/magic/ranged
hits are yellow, but these additional trait hits currently remain catchall/orange.
No repair is silently bundled into the centering patch; it is called out for the
next color/classification decision.

## Things that are not currently yellow/catchall

- Ordinary melee, bow/thrown/cannon/magic and ordinary NPC hits remain standard/red.
  Crit changes an attack's amount/roll metadata, not its type; Stagger is not a
  separate damage-number source. This work does not change those rules.
- Scythe cleave explicitly selects STANDARD in PVM:904. Thunder Spire splash
  explicitly selects STANDARD in SlayerRewardCombat:160 despite electrical flavor.
  God-spell and Iban delayed secondary settlement in SpellHandler uses STANDARD;
  Salarin's sparse delayed path can omit a typed splat and use legacy presentation.
  A future all-electrical/area taxonomy would need an explicit decision about
  these already-red paths, not just a catchall replacement.
- PoisonEvent selects POISON. Weapon poison, Corrode/acid, Corrosive Aura and
  Black/KBD poisoning eventually use those green pulses. The triggering hit and
  a later poison pulse are separate effects.
- Summon absorption damage is STANDARD/red on the summon (Summoning:1040), not
  summon-inflicted damage. Summon lifesteal healing is HEAL/blue. Do not infer a
  damage type from the target being a summon.
- Leach, blood amulet, Divine Grace, food, bone/offering and Cleric healing select
  HEAL. Healing remains blue even if its item/spell is named Blood or Death.
- Environmental/trap/agility/quest/DesertHeat and other one-argument Mob.damage
  callers default to STANDARD. Direct HP/damage-only updates have no type-2 tag
  either. No separate environmental producer of the old yellow type was found.
- Startle, smoke, Slow, elemental stat debuffs, Sticky, Kolodion Fire Claw's
  debuff and status graphics are not independently emitted HP numbers. A matching
  graphic or policy catalog enum is not evidence of a new damage producer.
- The Elder MAGIC_SECONDARY routing case exists in the policy mapper, but this
  class's active inflictPlayerDamage callers select only MELEE, RANGED and BURN.
  Count actual callers rather than treating every catalog entry as active damage.

## Proposed palette and decisions for Justin

Recommendation: classify by a few recognizable effect families, not by every
item or every adapter method. Preserve the five existing semantic colors. Add
only families Justin wants to distinguish; this is a **candidate menu**, not a
recommendation that all eleven colors must ship at once.

Background mode uses white foreground. The last column is the calculated
white/background luminance contrast before lifetime fading; all candidates exceed
5:1. Text mode retains the native dark shadow. These checks establish foreground
readability, not guaranteed color-only recognition for every color-vision profile
or every terrain. Closely related colors still need Justin's in-game review.

| Family | Colored text | Background | White/background contrast |
| --- | --- | --- | --- |
| Primary (existing) | `#FF6860` | `#A62B28` | 7.01:1 |
| Healing (existing) | `#69B8FF` | `#245B9B` | 6.90:1 |
| Poison (existing) | `#65DF80` | `#256938` | 6.65:1 |
| Summon (existing; include trait bonuses) | `#FFE263` | `#876611` | 5.33:1 |
| Recoil/reflection (existing hue; broader membership proposed) | `#CD96FF` | `#663897` | 8.15:1 |
| Extra physical hits | `#D3D6DE` | `#4B4B4B` | 8.72:1 |
| Fire/burn | `#FFB06D` | `#874413` | 7.33:1 |
| Cold/water | `#79DCEB` | `#075B70` | 7.67:1 |
| Earth/wood | `#D9B98B` | `#665039` | 7.58:1 |
| Electrical | `#74E1BD` | `#075744` | 8.55:1 |
| Blood/death secondary/spill | `#F49AC9` | `#7D305D` | 8.56:1 |

Important choices before implementation:

1. Source versus element: should summon Hellfire remain yellow (recommended for
   clear summon attribution), and should Frostbite return hits be purple or cyan?
2. Purple membership: jewelry recoil only, or all direct retaliation (Thorns,
   Divine Retribution, Sullen and possibly Frostbite)? Electrically Charged can
   remain electrical because its charge/discharge is a separate area mechanic.
3. How fine should elemental armor/weapon colors be? Fire/cold/earth are real
   distinctions; generic MAGIC/TRUE helpers are not. Black/KBD armor and random
   breath/slash weapon graphics do not establish a single fixed element, so keep
   a neutral equipment/secondary fallback until that meaning is chosen.
4. Should currently-red extra targets such as Thunder Spire and Scythe cleave
   adopt the new electrical/physical family, or stay red as ordinary attack hits?
5. Fire initial/burn pulses and blood/death spill may each share one family color
   without assigning separate colors to every armor set, tier, item or target hop.

Implementation after decisions must select presentation type at the actual
source callback, preserving the existing captured roll/effect and packet shape.
Do not reroll to rediscover a source, infer from mutable last-effect graphics,
change SecondaryEffectPolicy identity merely to color a number, or change
damage, mitigation, target selection, credit, death, timing or effect chances.
No running private client/server was restarted or modified for this audit/fix.
