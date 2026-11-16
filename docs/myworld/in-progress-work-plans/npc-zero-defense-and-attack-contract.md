# Zero Defense and Enemy Attack Power Contract

Recorded 2026-11-15 from Core `main` at
`97bebbb8dc450a7564b7523851896783ac193b65`.
Status: source investigation and planning decisions only. No gameplay changes,
NPC stat changes, new Holy Power field, or pilot have been implemented.

Companion references: [unified stat authoring guide](npc-explicit-stat-authoring-guide.md)
and [current NPC inventory](npc-stat-inventory.md).

### Later source compatibility check

Before finalizing the defense proposal, main advanced to
`edddf4d0ed351bf3f47224fabc0f3775b2d5a9d0` with god-crossbow effects. The source
trace and line references below remain pinned to 97bebbb8 unless noted. A focused
diff review found no change to player/NPC defense getters, the mitigation cap or
true-zero handling. Ordinary ranged resolution now uses a separate helper that
can lift eligible player offense for Saradomin Revelation before the same
independent defense roll; zero still skips that defense RNG draw. NPC baseline
stats and the recorded enemy special cases are unchanged by this merge. This is
a compatibility check, not a new full combat audit or a passing gameplay test.

Current references: [CombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java):328–352,557–559,628–649;
[GodCrossbowEffects.java](../../../server/src/com/openrsc/server/content/GodCrossbowEffects.java):82–91.


## 1. Owner direction and shared meaning

The owner clarified the defense relationship:

> "the old skill is basically what's being retired and we are effectively just
> giving enemies raw armor stats while the player is equipping armor to acquire
> the stats. but it's the same system"

NPC authored defense is therefore the armor-equivalent value itself. Players
acquire the same style-defense values from equipped armor and other applicable
equipment/effects. These feed the same modern defense interface and mitigation
system; they are not separate NPC and player kinds of defense. Retiring the
legacy skill-derived source does not require inventing a new damage model or
identical storage for both actors. It also does not authorize deleting saved
legacy skill positions as incidental cleanup.

The owner also specified:

> "all enemy attacks should tie to a power value, some attacks will ignore
> defense values like dragon's breath, however often these have caveats like
> with the dragon's breath's anti dragon fire shield mitigating damage in a
> unique way."

Power provenance and mitigation policy are separate concerns. A defense-bypassing
attack still needs an identifiable power source under this intended contract.
Its shield or other special mitigation can remain an explicitly named exception.
The source review below identifies both compliant paths and current gaps; it
does not claim the entire game already follows the rule.

For future NPC Holy Power:

> "holy power can be added as a field that exists, simply so if holy damage is
> something that is ever added then they'll already be ready for it, but
> otherwise it's inert."

This establishes the planned inert NPC field, not active Holy damage or NPC
Cleric casting. Current player Holy Power continues to govern Cleric effects.
The owner initially placed the pilot after these questions were sorted. After
this trace, the owner requested a [documentation-only defense pilot](npc-defense-pilot-proposal.md)
and deferred special-case treatment until the larger picture is established.
No runtime pilot starts from this investigation or proposal alone.

The owner also clarified the retirement policy:

> "of course we shouldn't outright delete it, sufficiently removing it from
> being active and properly annotating why for any future dev or ai is the best
> move. the goal, as stated before, is to make sure we don't accidentally re-use
> legacy stuff going forward, and have a clean cut system"

Retirement means removing active authority from the modern system, while keeping
and annotating legacy material as appropriate for history or compatibility.
Record why it is inactive, its replacement and any allowed compatibility use.
Do not leave silent fallbacks that future developers or AI could mistake for
current authority. Physical deletion is not this task's goal.

## 2. Answer: player armor defense has a true zero

All three modern player defense getters support **0**, with no inherited
minimum-one armor-defense floor. With no equipped defense contributions or
bonuses, the player has Melee Defense 0, Ranged Defense 0 and Magic Defense 0.

The full ordinary path is:

1. `Equipment.getMeleeDefense/getRangedDefense/getMagicDefense` each initializes
   its total to 0 and adds relevant equipped/wielded item contributions.
2. Player getters apply the relevant timed effects and reductions to those
   totals, not the old Defense skill level.
3. `CombatFormula` converts the matching style defense into a mitigation cap:
   `max(0, ceil(Defense / 7.0))`.
4. A nonpositive cap explicitly selects defense roll 0 without drawing defense
   RNG. Otherwise the roll is from 1 through the cap, with applicable luck logic.
5. Ordinary pre-modifier damage is `max(offenseRoll - defenseRoll, 0)`.

| Effective defense | Mitigation cap | Ordinary defense roll |
| ---: | ---: | --- |
| 0 | 0 | 0, no defense RNG |
| 1 | 1 | exactly 1 |
| 2–7 | 1 | exactly 1 |
| 8–14 | 2 | 1 or 2 |

Thus changing armor defense from 0 to 1 has a real effect: ordinary damage loses
one point before the zero floor. The table describes the armor-mitigation stage,
not every special attack or final damage outcome.

Sources: [Equipment.java](../../../server/src/com/openrsc/server/model/container/Equipment.java):2657–2737;
[Player.java](../../../server/src/com/openrsc/server/model/entity/player/Player.java):3014–3027;
[CombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java):559–560,630–643.

### All three ordinary styles use the matching zero-cap path

- Melee: `CombatFormula`275–301 uses Melee Defense.
- Ranged:328–352 uses Ranged Defense. NPC ranged routing reaches it through
  `RangeEventNpc`108–115 and `RangeUtils`422–427.
- Magic: 88–105 and 117–139 use Magic Defense, including capped/secondary overloads.
- The scimitar damage-reshaping helper also explicitly skips defense RNG at
  cap 0, although its later reshaping is a separate mechanic.

Ordinary PvM melee uses the new formula in `PvmMeleeEvent`293–300. The checked-in
MyWorld configs disable alternate OSRS melee/ranged formulas. This conclusion
is scoped to current ordinary MyWorld combat, not every historical/selectable
combat mode or an unverified deployed configuration.

Sources: [PvmMeleeEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java):293–300;
[RangeEventNpc.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEventNpc.java):108–115;
[RangeUtils.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java):422–427;
[myworld.conf](../../../server/myworld.conf):100–101;
[myworld-host.conf](../../../server/myworld-host.conf):111–112.

### Zero remains zero through current stat modifiers

- An absent elemental necklace adds 0. The current robe additional-defense
  methods return 0, and robe defense modifier methods return their input.
- The current Cleric ranged percentage-defense blessing cannot create defense
  from 0: the percentage of 0 is 0.
- Fire-defense reduction preserves 0 and floors reduced positive values. Defense 1
  can become 0 under a positive reduction.
- Hostile magic-defense reduction subtracts a rounded-up percentage and clamps
  at 0. It also preserves 0 and can turn 1 into 0.

Sources: `Equipment.java`:2781–2796,3205–3218;
[ClericTimedEffectRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericTimedEffectRuntime.java):92–105;
[Mob.java](../../../server/src/com/openrsc/server/model/entity/Mob.java):2640–2645;
[ClericHostileRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericHostileRuntime.java):154–169.

### Zero armor does not guarantee a positive hit

NPC ordinary offense rolls are low-biased and can return 0:
`min(M, floor(U^1.5 * (M + 1)))`, with uniform U in [0,1).
At defense 0, such a zero comes from the attacker, not hidden armor or the old
accuracy skill. Player ordinary offense candidates instead start at 1, with
applicable roll modifiers. Later protection, immunities and damage effects can
also alter the result.

Other minimums must not be mistaken for defense floors:

- Ordinary offense maximum and the base magic spell component have minimum 1.
- Positive outgoing maximum reductions can preserve minimum 1.
- Elemental robe resistance can preserve at least 1 of already-positive damage
  below full resistance; full resistance can yield 0. This is a later resistance
  stage, not a minimum armor stat.
- Player Cleric critical resolution can bypass the ordinary mitigation supplier;
  it is not the ordinary NPC roll provider.

Sources: `CombatFormula.java`:88–139,544–612,657–661;
`Player.java`:2982–3011;
[ClericCriticalRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericCriticalRuntime.java):30–37;
[CriticalHitResolver.java](../../../server/src/com/openrsc/server/model/combat/CriticalHitResolver.java):48–62.

### Where the old minimum-one behavior survives

`Player.getArmourPoints()` still returns at least 1. That is a legacy interface,
not the three modern defense accessors. Legacy accuracy helpers and alternate
OSRS formulas still reference it. The single-Melee-skill migration also leaves
old Defense and Strength current/max skill slots at 1 with zero XP. Those
compatibility values do not supply current armor defense.

This is not a claim that all legacy skill behavior has been removed or that
saved columns can safely be deleted. The authoring guide retains those migration
boundaries.

Sources: `Player.java`:2327–2330;
[Skills.java](../../../server/src/com/openrsc/server/model/Skills.java):621–651;
`CombatFormula.java`:790–798;
[OSRSCombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/OSRSCombatFormula.java):116–136,254;
[CombatEffectUtil.java](../../../server/src/com/openrsc/server/util/rsc/CombatEffectUtil.java):26–30.

### UI preserves zero

The server sends final three-style player defenses directly. The packet's byte
and integer representations preserve0; the client stores and displays the values
without a minimum-one clamp. This does not change the earlier finding that power
columns in the equipment panel are contributions rather than full player totals.

Sources: [ActionSender.java](../../../server/src/com/openrsc/server/net/rsc/ActionSender.java):571–575;
[PayloadCustomGenerator.java](../../../server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java):558–568;
[PacketHandler.java](../../../Client_Base/src/orsc/PacketHandler.java):4931–4946;
[mudclient.java](../../../Client_Base/src/orsc/mudclient.java):453–455,17357–17359.

## 3. Enemy power sources: verified examples and gaps

The ordinary paths already consume modern power. That does not establish that
all scripted or secondary enemy damage is power-bound.

| Path | Current damage source | Relationship to intended contract |
| --- | --- | --- |
| Ordinary NPC melee | Melee Power | Directly power-bound |
| General NPC ranged/magic projectiles | Ranged/Magic Power; magic includes spell component | Directly power-bound |
| Dark beast lightning | Magic Power through magic formula | Power-bound despite MELEE general profile |
| Elder sweep / fireshot | Melee / Ranged Power through ordinary formulas | Power-bound; do not discard its Ranged Power |
| Balrog splash | Fraction of actual primary damage | Indirectly power-bound; not a fresh power roll |
| Dragon combat-start breath | Species constants, gem tier, or target current HP | Current gap: no NPC power input |
| Elvarg/KBD ranged/thrown/spell retaliation breath | Target current HP percentage | Current gap: no NPC power input |
| Banshee unprotected wail | Target healing-maximum HP /2 | Current gap: replaces ordinary power damage |
| Elder burn pulses | Random 1–3 | Current gap: no power-derived magnitude |

Sources: `PvmMeleeEvent.java`:290–299;
[NpcBehavior.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java):595–627;
[DarkBeastCombat.java](../../../server/src/com/openrsc/server/content/monsterslayer/DarkBeastCombat.java):185–201;
[ElderGreenDragonSpecialAttacks.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/ElderGreenDragonSpecialAttacks.java):77–87,122–124,353–355;
[ProjectileEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java):1212–1245.

This is a targeted source map, not a completed census of every attack owner.
Quest scripts, poison/status ticks, reflection and other secondary effects still
need tracing. Record whether damage reads a power directly, derives from a
power-bound triggering hit, or currently has no such source. Do not grant silent
exceptions merely because damage occurs in a script rather than a normal attack.

## 4. Dragon breath and shield behavior are separate from armor defense

### Combat-start breath

The PvM constructor dispatches combat-start scripts. `DragonFireBreath` recognizes
dragon names or Elvarg's ID without excluding MyWorld, checks path reachability,
and applies its own initiation/species conditions.

Current unprotected base maxima are 65 by default (including Elvarg/KBD), 50 for
Blue, 55 for Red/Black, 10% of current player HP for baby Blue, and 45+3×gem tier
for gem dragons. These are not obtained from NPC Power.

Anti-dragon shield subtracts 0.8 from an equipment-reduction factor. Other listed
armor can alter it too. The code rounds that factor and, for some species and
initiation branches, switches to target-current-HP percentage formulas at a
factor of 0.25 or lower. Therefore "the shield always removes 80% of rolled damage"
is not an accurate description of the whole implementation.

Example: with only the anti-dragon shield, factor 0.20, and 100 current HP, Blue
Dragon's protected branch uses 5% current HP, or 5 damage before settlement. It
is not simply an unprotected roll multiplied by 0.20. Other species/branches use
different rules; KBD's initiation/ranging conditions matter.

This path calls `damageFromCombat`, without the ordinary three-style defense
roll. Its legacy settlement applies applicable Goblin Tenacity and HP/death
handling, not the ordinary projectile robe/potion/True Defense processing.

Sources: [DragonFireBreath.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/scripts/all/DragonFireBreath.java):30–106,116–162;
[PvmMeleeEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java):100–106;
[CombatScriptLoader.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/scripts/CombatScriptLoader.java):35–45,65–74;
[GemDragons.java](../../../server/src/com/openrsc/server/content/gemdragons/GemDragons.java):31–36;
`Mob.java`:1884–1914.

### Ranged/thrown and spell retaliation

Elvarg/KBD also have retaliation branches using 20% of the player's current HP.
The anti-dragon shield changes these to 10% for Elvarg or 4% for KBD. First-hit,
path and caller conditions apply. Ranged/thrown and spell-handling versions are
separate code paths; the spell version calls plain `damage`, so settlement must
not be described as identical across all breath sources.

Sources: `RangeUtils.java`:328–359;
[RangeEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/RangeEvent.java):213;
[ThrowingEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java):565;
[SpellHandler.java](../../../server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java):1747–1775.

Ordinary dragon magic projectiles follow the general magic-defense path and do
not perform these shield checks. Also, `CombatFormula` has player dragon-weapon
breath helpers whose eligibility explicitly requires a Player. They are not the
NPC breath system and must not be used as evidence that enemy breath is already
power-bound (`CombatFormula.java`:387–402,474–515).

### Other confirmed special mitigation

- Banshee wail, without effective earplugs, replaces normal damage with a roll
  from 0 through floor(target healing-maximum HP/2). Its callers skip the normal
  mitigation branches, including True Defense. Earplugs prevent the replacement.
- Elder's fixed-range burn pulses bypass ordinary defense-roll mitigation but
  retain their Fire robe/potion mitigation and summon absorption. BURN does not
  enter that class's primary-style True Defense branch.

Sources: [BansheeCombat.java](../../../server/src/com/openrsc/server/content/monsterslayer/BansheeCombat.java):28–35;
`PvmMeleeEvent.java`:460–484; `ProjectileEvent.java`:583–608;
`ElderGreenDragonSpecialAttacks.java`:33–35,223–227,272–290,353–355.

These differences need an explicit attack/mitigation record. The owner permits
special mitigation and defense bypass; that permission does not remove the
separate requirement for a power source.

## 5. Holy Power: planned inert NPC field

Current facts remain unchanged: player Holy Power scales Cleric effect ranks;
NPC definitions and the shared current combat accessors do not expose an NPC
Holy Power field. The inventory correctly records that absence for its snapshot.

New planned state: reserve an NPC Holy Power field for possible future Holy
damage, but keep it inert now. Do not wire it into attacks, defense, profile
selection, healing, buffs/debuffs, or damage calculations as part of adding the
placeholder. Do not reinterpret current player Holy Power or change Cleric use.
The presence of a future field does not imply a Holy Defense stat or an active
fourth damage channel.

Recommendation for later schema review: explicitly name the field and make its
initial value/default unambiguous, with zero as a proposed inert starting value.
That value and storage detail have not been selected by this documentation.
An eventual test should prove that changing the placeholder does not change
current NPC combat. Future activation requires its own rules and review.

## 6. Verification and decisions before a pilot

Completed here:

- Independent source traces of equipment, player accessors, three ordinary damage
  styles, legacy skill/helper boundaries, UI and sampled enemy special attacks.
- Existing test inspection: `test-balance-fixtures.py`114–142 models the explicit
  zero-mitigation branch, 604–611 evaluates no-armor NPC cases, and 933–937 checks
  armor effects and NPC zero-hit probability. `SlayerLeatherFixture`75–87 checks
  authored item budgets and equipment totals.
- Non-mutating arithmetic checks for Defense 0, 1, 7, 8, 14; percentage buffs preserving 0;
  positive debuffs taking 1 to 0; NPC zero and maximum offense candidates; and
  shield-percentage examples. These mirrored the source formulas in Python.

Not performed: executing the production Java combat code, a server/client session,
a gameplay regression suite, an actual RNG-consumption test, deployment checks,
changes to runtime/data/tests, a new field implementation, or a pilot. Targeted
searches did not establish a dedicated naked-player zero-vs-one end-to-end fixture.
Some older source-string tests appear stale; no passing result is claimed for them.

Sources: [test-balance-fixtures.py](../../../tests/myworld/test-balance-fixtures.py):114–142,604–611,933–937;
[SlayerLeatherFixture.java](../../../server/test/com/openrsc/server/combat/SlayerLeatherFixture.java):75–87;
[test-combat-runtime-invariants.py](../../../tests/myworld/test-combat-runtime-invariants.py):136–138.

### Updated follow-through after the owner's pilot direction

The owner subsequently requested the defense pilot and explicitly deferred
special cases such as dragon breath, Banshee wail and Elder burn until the larger
picture is done. The source findings above remain valid; they are a record for
later, not prerequisites to the current defense-picture proposal.

1. Review the [defense pilot proposal](npc-defense-pilot-proposal.md), with family
   themes, flexible progression, current ratio clues, proposed plain values and
   explicit mitigation-tier consequences. No proposed number is installed.
2. Keep power-source gaps and special mitigation in this investigation register.
   Do not choose their replacement formulas/values now or block defense review
   on their resolution. The intended all-attacks-have-power-source rule remains.
3. Before implementation, specify authoritative NPC armor-stat storage and
   zero/missing behavior, the planned inert Holy field, and retained legacy
   isolation/annotations. Player true zero already works; NPC loader zero remains
   a separate compatibility problem.
4. Plan focused regressions for player zero and NPC authority without claiming
   that they have run. Preserve current powers, HP, styles and behavior in a
   defense-only test unless a later owner decision explicitly changes them.
5. Return to special-attack mappings after the broader defense picture is clear.
   Runtime changes or a gameplay pilot require their own approval.
