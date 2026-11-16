# Unified Combat Statline and NPC Authoring Guide

Later owner decisions: [family authoring and zero-inclusive defense rolls](npc-family-authoring-decisions.md) (2026-11-16). These supersede conflicting recommendations below. Existing-source descriptions remain historical facts, not the future roll contract.

Status: active investigation and authoring reference; no NPC rebalance or runtime
migration has been implemented by this documentation task.

Recorded: 2026-11-14; owner decisions and focused source review updated
2026-11-15. The original source/census baseline is
`a89c475b47c755038aa35a435ca3f54e3d7e7870` on Core `main`; the first trace used
`7f88564eeb60adb24de382a4c35d8453a347df1b`. The later baseline includes Stirge.
The zero-defense and enemy-attack follow-up used
`97bebbb8dc450a7564b7523851896783ac193b65`; its findings and limits are in the
[focused investigation](npc-zero-defense-and-attack-contract.md). Inventory
artifacts keep their own captured revision rather than being silently refreshed.
A later compatibility comparison at `edddf4d0ed351bf3f47224fabc0f3775b2d5a9d0`
verified unchanged baseline NPC stats and defense-zero semantics after the
god-crossbow merge. Ranged offense gained a conditional Revelation adjustment
and a separate mitigation helper; the focused report and pilot proposal record
that difference. Older exact line references below remain snapshot-qualified,
not silently relabeled as current source.

## 1. Purpose and owner direction

The end state is a unified, readable power and defense statline shared by NPCs
and players. NPC authors should assign and adjust deliberate numbers directly,
without working backward through old Attack/Strength/Defense or conversion
ratios. The owner wants a thorough written guide to keep the current-state
investigation, destination, decisions and recommended migration path aligned.

The owner explicitly clarified the shared end state:

> "a unified power and and defense statline that npcs and players share, making
> it easier to read, understand, and allocate stat values to new npcs, as well as
> adjust old npc stat values without needing to work through a formula or other
> conversions."

They also requested recommendations for how to get there once the present state
and intended destination are clearly documented. Section 13 supplies a proposed
path, with review checkpoints; it is not an approved implementation sequence.

The intended vocabulary is:

- Melee Power and Melee Defense.
- Ranged Power and Ranged Defense.
- Magic Power and Magic Defense.
- Holy Power currently governs player Cleric buff/debuff effectiveness. The
  owner now wants an NPC Holy Power field reserved for possible future Holy
  damage, but inert now. No such NPC field has been implemented by this work.

### Settled combat semantics: owner clarification, 2026-11-14

The owner clarified that the general method is already established and must be
followed, rather than reopened as a design decision:

> "power is rolled, corresponding defense is rolled, simple math is done, damage
> is determined. holy power is it's own category because it simply determines
> the effectiveness of cleric spells which are buffs and debuffs."

> "i wanted to keep the way damage is calculated as simple as possible, each
> type of combat has a power and a defense, they are rolled against each other,
> and you get your result."

The shared contract is therefore Melee Power against Melee Defense, Ranged
Power against Ranged Defense, and Magic Power against Magic Defense. Holy Power
controls Cleric spell effectiveness rather than adding a fourth damage matchup
or requiring Holy Defense. These meanings are settled for this work.

This is a direction to preserve the established model while making stat values
explicit and readable, not a request to redesign combat math. Section 6 retains
the exact current implementation: stats determine scaled roll bounds, rolls are
subtracted with a zero floor, and existing modifiers or special cases can apply.
The clarification does not authorize replacing that implementation with literal
rolls from zero to each raw stat, identical actor rules, or a new formula.

### Further owner decisions, 2026-11-15

The owner clarified that NPC defense is raw armor-equivalent data, while players
acquire the same defense stats through equipped armor:

> "the old skill is basically what's being retired and we are effectively just
> giving enemies raw armor stats while the player is equipping armor to acquire
> the stats. but it's the same system"

The producer differs; the style-defense meaning and modern mitigation system do
not. This applies to defense and does not redefine how player power/progression
is acquired.

The intended attack contract is also explicit:

> "all enemy attacks should tie to a power value, some attacks will ignore
> defense values like dragon's breath, however often these have caveats like
> with the dragon's breath's anti dragon fire shield mitigating damage in a
> unique way."

The requirement for a power source is separate from whether an attack rolls
against armor defense or uses special equipment mitigation. Current source has
exceptions that are not yet power-bound; the focused investigation records them.

The planned NPC Holy Power field is reserved rather than activated:

> "holy power can be added as a field that exists, simply so if holy damage is
> something that is ever added then they'll already be ready for it, but
> otherwise it's inert."

Current player Holy Power continues to govern Cleric effects. The planned NPC
placeholder does not activate Holy damage, NPC Cleric casting or Holy Defense.
Its exact representation/default remains to be specified before implementation.

The owner then specified how legacy retirement should work:

> "of course we shouldn't outright delete it, sufficiently removing it from
> being active and properly annotating why for any future dev or ai is the best
> move. the goal, as stated before, is to make sure we don't accidentally re-use
> legacy stuff going forward, and have a clean cut system"

Preserve legacy material as appropriate for history and compatibility. Remove
its active authority from the modern stat path and annotate why it is inactive,
what replaces it, and which compatibility consumers remain. Physical deletion
is not the goal. Do not leave an undocumented fallback that can reactivate old
stats. That clarification initially held the pilot pending investigation. The
later direction below now authorizes a documentation-only pilot proposal, not
runtime changes or an executed test pilot.

### Defense pilot direction and deferred specials, 2026-11-15

The owner now wants a reviewable defense pilot: early enemies generally have one
obvious defense and two weaknesses; progression can add an intermediate defense
and later a second strong defense while retaining some weakness. These are
flexible guidelines, not a universal level formula. More evenly protected
exceptions and thematic variety remain desirable.

Family identity should persist. Ordinary zombie variants keep the same defensive
structure with modest improvements. Insect-like groups should generally remain
ranged-resistant while their other defenses vary. Current percentage conversions
are meaningful clues to this prior intent: the owner said they already try to
follow these principles but were assembled quickly and should now be refined.
Use them to inform proposals, not to mechanically approve every converted value.

The owner explicitly deferred special-case treatment:

> "anything special cases you find, like dragons breath, banshees wail, and elder
> burn, we can decide how to handle after. it'll be easier to fit them in once
> we have the larger picture done"

The [defense pilot proposal](npc-defense-pilot-proposal.md) now supplies 14 current
and proposed rows: four proposed numerical changes and ten preservation controls.
Every proposed value is unapproved. Known attack-source exceptions stay recorded
for later; their resolution does not block this larger defense-picture review.
No runtime/data edits or gameplay pilot have been performed.

The owner explained the original defense rule as follows:

> "every defense must receive atleast one 100% value of it's original defense,
> from there, if nothing else is decided, the others get 50%"

The owner's stated reason for moving to explicit values is:

> "the goal, then, is to get away from that confusing sort of syntax, where 100%
> defense sounds like it's got full defense against something, and instead
> having proper number values for each. similarly is power."

The practical goals are to make instructions to AI collaborators clearer and to
prevent accidental mixing of retired and current stats from causing unintended
combat behavior. The owner specifically identified Balrog's legacy Attack
connection as an example; section 7 traces the current implementation.

### Defense is independent per style, not a divided pool

The old Defense number is a reference for each style's resistance. The three
new defenses are not shares that must add up to the old number or to 100%.

For example, if old Defense is 80 and melee retains the original value, the
starting interpretation is 80 Melee Defense, 40 Ranged Defense, and 40 Magic
Defense, unless a different style identity has been decided. Dividing 80 into
roughly 27/27/27 reduces every resistance instead of preserving one intact.
Damage and mitigation are quantized, so this is a stat-scale explanation, not
an assertion that damage taken or encounter difficulty changes linearly.

A legacy multiplier of 1.0 means "use the old Defense number unchanged." It does
not mean 100% damage reduction, immunity, a chance to block, or full protection.
The resulting plain value, such as Magic Defense 80, is what the authoring
record should show.

This explains the original baseline. It does not silently repeal approved
exceptions, force every future NPC into the same distribution, or authorize a
new rebalance. Current exceptions must be recorded and reviewed as such.

### What is settled and what is not

Settled direction from this conversation:

- Use one understandable power/defense statline for NPCs and players, making
  new NPC allocation and old NPC adjustment direct and readable.
- Keep the established simple damage model: roll the relevant combat power and
  corresponding defense, then resolve damage. Document the exact existing
  implementation without reopening this general meaning.
- NPC defenses are authored raw armor-equivalent stats; players acquire the
  same style-defense stats through armor. Both use the same defense system.
- All enemy attacks should have a power source. Explicit defense bypass and
  special equipment mitigation are compatible with that requirement.
- Preserve current player Holy Power as Cleric effectiveness. Plan an inert NPC
  Holy Power field for possible future Holy damage, without activating it now.
- Replace confusing legacy-derived combat values with intentional per-style
  power and defense values, without redesigning the damage model.
- Preserve the independent-defense interpretation above when explaining the
  original migration.
- Separate current combat meaning from old Attack/Strength/Defense data so that
  retired fields do not accidentally control new attacks. Preserve and annotate
  legacy material for compatibility/history rather than deleting it outright.
  Remove active authority in the modern path and prevent accidental reuse.
- Use documentation to make the reasoning, current facts, and future choices
  clear before implementation.
- Use flexible progression and coherent family themes when proposing defenses;
  preserve meaningful weaknesses and use current ratios as historical guidance.
- Review a documentation-only pilot now, while deferring special-attack treatment
  rather than making it a blocker to the overall defense picture.

Not settled:

- Final numbers for the complete enemy roster, including Balrog's intended Magic
  Power and attack maximum.
- Whether each NPC keeps its current effective balance or receives a deliberate
  rebalance. Known mistakes must not be approved merely by copying them.
- Final schema, field names, storage location, handling of missing/zero values,
  or whether existing `*Offense` fields are renamed to `*Power`.
- Exact storage, initial/default value and validation for the planned inert NPC
  Holy Power field. Future activation as Holy damage would need its own design;
  NPC Cleric casting and Holy Defense are not part of the current request.
- The power mapping and magnitude rules for currently fixed/target-HP enemy
  attacks, while preserving explicitly intended special mitigation. The owner
  has deferred those decisions until the larger defense picture is established.
- Any broader redesign of ordinary damage formulas, attack styles, timing, HP,
  displayed combat levels, or special encounter mechanics. Such changes are
  not implied by explicit stat authoring. The power mapping of current exceptional
  attacks is a specific planning requirement above, not an excuse to redesign
  every combat formula.
- The implementation sequence, rollout scope, and exact isolation boundary for
  retained legacy compatibility. No deletion or gameplay deployment is requested.
- The documentation-only pilot is now requested and proposed. Its numbers,
  eventual implementation scope and gameplay-test authorization remain unapproved.
  Special-attack decisions are deliberately deferred, not a prerequisite to
  reviewing the defense proposal.

## 2. Evidence scope and reading order

This reference combines the owner's explanation with a read-only trace of the
checked-out production source and JSON definitions. File/line references below
are for the latest snapshot stated above unless a historical snapshot is named.
They are navigation aids and must be refreshed if code moves.

The investigation did not inspect the running Oracle deployment, replay a live
combat incident, or run gameplay tests. A statement about the current code path
is not proof that a particular binary or data revision is deployed publicly.

Use this order when evidence conflicts:

1. Current owner decisions establish intended behavior.
2. Current source, data, and verified tests establish implemented behavior.
3. This document records their relationship and known gaps.
4. Archived design documents explain history; they do not override newer owner
   decisions or current source facts.

Related history:

- [NPC stat migration plan](../completed-work-plans/archive/npc-stat-migration-plan.md).
- [NPC family audit](../completed-work-plans/archive/npc-family-audit.md).
- [Original combat notes](../completed-work-plans/archive/combat-notes.md).
- [Original combat spec](../completed-work-plans/archive/combat-spec.md).
- [NPC profile consolidation](../../combat/combat-a10-npc-profile-state-modernization.md).

The archived migration plan describes automatic derivation as the bridge that
made the new system usable without hand-authoring every NPC. Some prose is
outdated: Attack still participates in projectile power fallback, and current
gem dragons exceed the old suggested maximum multiplier of 1.0. Do not use those
historical statements as current invariants.

## 3. Old and current stat meanings

| Concept | Old role | Current NPC role |
| --- | --- | --- |
| Attack | Accuracy/hit-success input | Still stored; unexpectedly remains a ranged/magic fallback input |
| Strength | Melee damage potential | Fallback Melee Power, and part of projectile fallback |
| Defense | One legacy defensive stat | Input to three independent derived defenses |
| Ranged flag | Legacy boolean configuration | Loaded internally as 0 or 1; not a full authored Ranged Power stat |
| Hits | Health | Still health, separate from the six power/defense values |
| Combat level | Displayed threat indicator | Explicit definition value; not the input to the new damage formula |
| `*Offense` | New implementation terminology | The code's current names for the owner's Melee/Ranged/Magic Power |

The historical model separated accuracy checks from damage rolls. Current normal
MyWorld combat instead resolves an offensive roll against a style-specific
mitigation roll. A zero can still result; "attacks connect" does not mean every
attack must remove HP or bypass encounter immunity and eligibility checks.

`NPCDef` stores the six direct stats alongside the old fields. `Npc` still
initializes legacy skill slots, but the six current accessors read definition
values, not those mutable legacy skill levels. Audit remaining consumers and
compatibility contracts, then remove their authority from the modern path while
retaining and annotating legacy material as the owner directed. Do not treat
physical field/skill-slot deletion as the migration objective.

Sources: [NPCDef.java](../../../server/src/com/openrsc/server/external/NPCDef.java)
lines 37–51, 82–86; [Npc.java](../../../server/src/com/openrsc/server/model/entity/npc/Npc.java)
lines 208–212, 330–331, 675–751; [EntityHandler.java](../../../server/src/com/openrsc/server/external/EntityHandler.java)
lines 363–381; historical combat notes lines 46–72.

### Current shared interface already exists

`Mob` already declares the same six abstract accessors for both actors:
`getMeleeOffense`, `getRangedOffense`, `getMagicOffense`, and the three matching
`get*Defense` methods. `CombatFormula` consumes that common interface. The
missing work is a consistent, explicit source of NPC numbers and clear
vocabulary/presentation of the established shared semantics, rather than
inventing a second combat stat system alongside an existing common interface.

Sources: [Mob.java](../../../server/src/com/openrsc/server/model/entity/Mob.java)
lines 256–266; [CombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java)
lines 52, 65, 91, 124, 296, 352.

### How player values currently reach that interface

Players currently obtain totals through a different provider than NPCs:

- Melee Power is current Melee skill plus equipment combat offense plus the
  current Body robe power bonus. Without an equipped melee weapon the result
  is `max(1, floor(total * 0.70))`.
- Ranged Power is current Ranged skill plus equipment combat offense plus the
  Body robe power bonus.
- Magic Power is current Magic skill plus equipment combat offense plus the
  Body robe power bonus.
- Defenses start from the relevant equipment totals, including the applicable
  jewelry, robe and item-specific effects. Player accessors then apply timed
  Cleric/fire effects and, for magic, hostile magic-defense reduction.
- Holy Power is currently a separate player Cleric calculation, described in
  section 10, outside the shared six-method `Mob` contract.

Equipment offense itself distinguishes a displayed signed contribution from a
combat contribution clamped to zero. Equipment can include bonuses and penalties.
Do not impose the NPC parser's positive-only rule on equipment contributions:
item getters commonly accept nonzero values, including negative values.

There are still player-item legacy conversions. Examples include eligible melee
item power derived from scaled old weapon power divided by 7, staff magic power
from scaled magic bonus divided by 4, and eligible armor defenses from scaled
armor bonus divided by 14, with upward rounding and numerous classification or
item-specific exceptions. These are a related inventory item, not evidence that
all player totals should be replaced with static authored character values.

Sources: [Player.java](../../../server/src/com/openrsc/server/model/entity/player/Player.java)
lines 2333–2349, 2888–2895, 3014–3027;
[Equipment.java](../../../server/src/com/openrsc/server/model/container/Equipment.java)
lines 1969–2069, 2657–2737, 2961–3072;
[EquipmentStatCalculator.java](../../../server/src/com/openrsc/server/model/container/EquipmentStatCalculator.java)
lines 14–19.

### Current display is not a full resolved-stat snapshot

The equipment-stat packet mixes different kinds of values:

| Packet/display quantity | Current server source |
| --- | --- |
| Three defenses | Final `Player.get*Defense()` results |
| Three powers | `Equipment.getDisplayed*Offense()` contributions, not `Player.get*Offense()` totals |
| Holy Power | Equipment-only Holy Power contribution |
| Prayer | A separate equipment prayer value |

The client displays those values directly, and the bank interface reuses them.
It does not turn all of them into a complete server-authoritative player statline.
Some magic tooltip estimates separately reconstruct power from skill and item
information. Therefore neither "all equipment-only" nor "all full runtime
values" accurately describes the current panel.

Legacy packet member names also obscure meaning: `magicPoints` now carries melee
equipment power, while `weaponPowerPoints` carries melee defense. The custom
packet has five legacy bytes followed by an ordered integer extension. Changing
those fields' meanings without an explicit protocol/presentation decision risks
double counting or mismatched clients.

Sources: [ActionSender.java](../../../server/src/com/openrsc/server/net/rsc/ActionSender.java)
lines 570–582; [EquipmentStatsStruct.java](../../../server/src/com/openrsc/server/net/rsc/struct/outgoing/EquipmentStatsStruct.java)
lines 8–16; [PayloadCustomGenerator.java](../../../server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java)
lines 558–574;
[PacketHandler.java](../../../Client_Base/src/orsc/PacketHandler.java)
lines 4931–4946; [mudclient.java](../../../Client_Base/src/orsc/mudclient.java)
lines 453–455, 14810–14814, 14846–14854, 17357–17359;
[CustomBankInterface.java](../../../Client_Base/src/com/openrsc/interfaces/misc/CustomBankInterface.java)
lines 578–582. This was a targeted consumer trace, not an exhaustive client audit.

### Intended shared meaning, recommended implementation boundary

The owner-approved destination is one readable statline with the established
combat meanings in section 1. The following implementation boundary is
recommended for review; it does not reopen those meanings or settle a class or
file schema:

- Both actors expose Melee/Ranged/Magic Power and Defense in the same units and
  order, with an explicit distinction between baseline and current effective
  values. NPC baseline defense is raw armor-equivalent data and player defense
  comes from equipped armor; these are the same kind of value. Player Holy Power
  keeps its current Cleric role; the planned NPC Holy field remains inert.
- An NPC's baseline comes from reviewed authored numbers. A player's current
  values can continue to come from skills, equipment and applicable effects.
  Common meaning does not require identical storage or acquisition rules.
- Combat code reads that shared resolved contract. It should not need to know
  which deprecated field once supplied an NPC's number.
- Authoring views show plain NPC numbers and optional provenance. Player views
  distinguish total power/defense from equipment contributions and effects.
- HP, displayed combat level, attack styles, spell parameters, cadence and
  special mechanics remain separately named data, not hidden meanings of Power.

A common stat unit also does not automatically equalize actor-specific balance
rules. Currently Melee Power 50 gives a raw ordinary maximum of 8 for an NPC
and 9 for a player because their rounding differs. Magic also uses a spell
component. Recommendation: preserve those rules during the data-authority
migration unless a separate approved balance decision changes them.

### Do not confuse NPC independence with an armor budget

The owner's 100%/50% baseline rule concerns NPC legacy-defense migration. The
existing [combat equipment spec](../info/combat-equipment-spec.md) deliberately
allocates an armor item's resource/tier defense budget among styles, including
metal and leather allocation rules. These are different authoring problems.
A shared final statline should not accidentally convert all NPC defenses into
one budget or abolish the equipment allocation rules. Any item-budget changes
need their own deliberate decision.

### Player compatibility boundaries

The current skill order and persistence still retain legacy identities:

- Melee occupies the old Attack position, and `Skill.ATTACK` aliases Melee.
- Defense and Strength positions remain in skill arrays on server and client.
- Worship is the presentation name for the stable internal Prayer skill.
- Database adapters map Melee back to the `attack` column and load/save skills
  using positional IDs.

Do not delete or reorder those slots, rename saved columns, or migrate player
progression as a side effect of adopting the shared statline. Isolating NPC
legacy data can coexist with these compatibility adapters. Removing them later
would need its own migration plan.

There are also non-damage consumers of power. For example, Strong Heal currently
uses total player Magic Power, while Lesser Heal uses displayed equipment Magic
Power. A mechanical replacement of every `*Offense` call with a total-stat read
would alter behavior. Inventory consumers by meaning before changing them.

Sources: [Skills.java](../../../server/src/com/openrsc/server/constants/Skills.java)
lines 233–240; [Skill.java](../../../server/src/com/openrsc/server/constants/Skill.java)
lines 95–99; [mudclient.java](../../../Client_Base/src/orsc/mudclient.java)
lines 29574–29581;
[MySqlGameDatabase.java](../../../server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java)
lines 61–69, 898–923, 1939, 1954, 1969;
[MySqlQueries.java](../../../server/src/com/openrsc/server/database/impl/mysql/MySqlQueries.java)
lines 38–46;
[SpellHandler.java](../../../server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java)
lines 2839–2850. No player database was accessed for this source review.

## 4. Where the current NPC data comes from

### Load order and identities

`EntityHandler.load()` loads these definition arrays in order:

| Definition file under `server/conf/server/defs/` | Records at this snapshot |
| --- | ---: |
| `NpcDefs.json` | 794 |
| `NpcDefsCustom.json` | 52 |
| `MonsterSlayerNpcDefs.json` | 15 |
| `VisualTestNpcDefs.json` | 1 |
| `MyWorldNpcDefs.json` | 1 |
| `SlayerMovementPreviewNpcDefs.json` | 8 |
| `MonsterSlayerTowerNpcDefs.json` | 6 |
| `GemDragonNpcDefs.json` | 5 |
| `StirgeNpcDefs.json` | 1 |

The last seven are loaded when MyWorld is enabled. Full definitions are appended
in order; their position establishes runtime identity. The reader does not use
the JSON `id` to insert a full definition at an arbitrary position. All current
rows were checked to agree with their sequential position. Preserve published
IDs and load order.

Historical patches follow when `BASED_CONFIG_DATA < 85`. The checked-in MyWorld
and hosted MyWorld configurations use 85, so that patch branch is inactive in
those configurations. MyWorld then applies the optional ID-keyed
`NpcDefsMyWorld.json` overrides. That layer copies each original definition and
changes only supplied fields.

The similarly named files have different purposes:

- `MyWorldNpcDefs.json` appends new definitions; currently Green Dragon #862.
- `NpcDefsMyWorld.json` overrides existing definition IDs.

World-builder fallback `npcs.json` describes placements, not these combat-stat
profiles. A definition count is not a spawn count or a list of active enemies.

Sources: [EntityHandler.java](../../../server/src/com/openrsc/server/external/EntityHandler.java)
lines 241–265, 351–401, 452–468, 471–526;
[myworld.conf](../../../server/myworld.conf) line 90;
[myworld-host.conf](../../../server/myworld-host.conf) line 101.

### Census of direct values versus derivation

The [current NPC stat inventory](npc-stat-inventory.md) now provides all 883
rows, per-stat provenance, reproducible CSV/JSON evidence, source-supported role
classifications and an outlier review queue. Observed values are not approved
replacement balance values.

Static census at `a89c475b47c755038aa35a435ca3f54e3d7e7870`:

- 883 total definitions; 298 marked attackable. These are not unique live
  encounters or placed-NPC counts. The Preview-named IDs 863–870 are now
  production encounters; the explicit developer visual fixture Gorak 861 is
  nonattackable, not part of the 298.
- 10 attackable definitions have positive explicit values for all three
  defenses. The remaining 288 still derive all three.
- Positive explicit power counts: Melee 9, Ranged 3, Magic 3. Counts overlap;
  they are not counts of distinct NPCs or proof of enabled attack styles.
- All 794 base `NpcDefs.json` records lack the six direct-stat fields.
- 325 MyWorld overrides: 323 set all three defense multipliers, 23 change legacy
  Strength, and none set any of the six direct combat values.
- No defense-divisor fields are authored in these loaded records, although the
  runtime still supports that fallback.

The 10 direct-stat examples are Elder Green Dragon #844, Giant frog #863,
Cockatrice #864, Banshee #865, Naga #866, Terror dog #867, Bloodveld #868,
Dark beast #869, Abyssal demon #870, and Stirge #882.

The first snapshot, before Stirge, had 882 definitions, 297 attackable, and nine
explicit-defense entries. Its 288 derived-defense entries are unchanged by the
addition of Stirge. Do not mix these two snapshots when quoting totals.

Method: read the nine arrays in production order; assert each JSON ID equals its
append index; merge supplied override keys by ID; filter `attackable == 1`; count
direct values strictly greater than zero, matching accessor precedence. This
was a static data audit, not an invocation of the production loader or combat
harness. Future censuses must check the then-current load order/configuration.

## 5. Exact legacy-to-current derivation

This section describes current behavior to be retired or deliberately retained
at a compatibility boundary. It does not prescribe the final authoring rules.

Let A, S, and D be the loaded legacy Attack, Strength, and Defense after relevant
definition overrides. Let an "explicit" stat mean a value greater than zero,
which is the current accessor condition, not a recommended future schema.

### Power

| Current stat | Current resolution order |
| --- | --- |
| Melee Power | Positive `meleeOffense`; otherwise S |
| Ranged Power | Positive `rangedOffense`; otherwise ranged-profile power when positive; otherwise legacy `ranged` value |
| Magic Power | Positive `magicOffense`; otherwise magic-profile power |

A profile using ranged projectiles derives Ranged Power as `max(1, A, S)`.
A profile using magic projectiles derives Magic Power as `max(1, A, S)`.
A profile not using a style returns zero for that style. The legacy ranged
fallback is a 0/1 flag loaded into an integer, not a separate numeric ranged
skill definition.

Sources: [Npc.java](../../../server/src/com/openrsc/server/model/entity/npc/Npc.java)
lines 675–703; [NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java)
lines 89–94, 240–245.

### Defense

For each style:

1. Use its positive explicit defense if present.
2. Otherwise, if D is nonpositive, return zero.
3. Otherwise choose its configured multiplier when that multiplier is at least
   zero. A multiplier of zero is valid in this existing derivation path.
4. If no multiplier is configured, use `1 / legacyDivisor` when the divisor is
   positive.
5. If neither is usable, use the default multiplier: melee 1.0, ranged 0.5,
   magic 0.5.
6. Return `max(0, floor(D * selectedMultiplier))`.

Existing family differences are stored as per-ID multipliers. There is no
separate dynamic family-defense resolver to treat as the authoring authority.

After baseline resolution, fire defense debuffs reduce all three defenses.
Cleric hostile effects can further reduce Magic Defense. A stat sheet should
separate authored baseline values from temporary effective values.

Sources: [Npc.java](../../../server/src/com/openrsc/server/model/entity/npc/Npc.java)
lines 48–50, 707–751; [Mob.java](../../../server/src/com/openrsc/server/model/entity/Mob.java)
lines 2636–2645; [ClericHostileRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericHostileRuntime.java)
lines 154–169.

### Attack style is a separate concern

The general profile chooses style largely through NPC names and IDs. Supplying
a positive Magic Power or Ranged Power alone does not make the ordinary NPC
behavior use that style. Dedicated encounter scripts can also use stats outside
the general profile's ordinary attack selection.

For example, Dark beast has a dedicated magic strike, while Elder Green Dragon
has all three powers authored even though the general dragon profile is mixed
melee/magic. Preserve these distinctions in the roster.

Sources: [NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java)
lines 379–473; [NpcCombatProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcCombatProfile.java)
lines 3–23; [NpcBehavior.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java)
lines 537–653; [DarkBeastCombat.java](../../../server/src/com/openrsc/server/content/monsterslayer/DarkBeastCombat.java)
lines 186–200.

## 6. Current combat math is a separate layer

The owner's settled model is the current high-level structure: a style's power
and corresponding defense drive opposing rolls, then simple arithmetic resolves
damage. The migration should preserve it. Replacing legacy-derived values does
not remove the formulas that turn canonical power and defense into damage.
Keep these two layers separate in every proposal and test:

1. Definition resolution: old data and overrides produce current power/defense.
   This is the conversion layer to retire from the modern NPC path.
2. Combat resolution: current power/defense produce rolls, mitigation and damage.
   This established layer remains; its formulas are not legacy NPC conversion.

### Intent versus implementation precision

The ordinary source path supports the owner's description. It scales power and
defense into roll bounds rather than rolling directly over each raw stat's full
range. NPC/player rounding and roll distributions differ, magic includes a
spell component, and later effects can modify damage. Those are existing details
to record and preserve, not evidence that the owner asked for a new system.
No ordinary-path mismatch with the high-level model was found in this trace.
Boss scripts and exceptional effects still need their own inventory; this is
not a claim that every damage event uses an identical sequence.

### Ordinary NPC melee and ranged

Before outgoing debuffs, special attacks and other modifiers:

- Attack maximum: `max(1, 1 + floor(Power / 7))`.
- Defense roll maximum: `max(0, ceil(relevant Defense / 7))`.
- NPC offense roll: `min(maximum, floor(U^1.5 * (maximum + 1)))`, where U is
  uniform in [0, 1). This is low-biased and can return zero.
- Ordinary defense roll: uniform integer 1 through the defense maximum, or zero
  when that maximum is zero. Player luck can choose a higher mitigation candidate.
- Damage before later modifiers: `max(offenseRoll - defenseRoll, 0)`.

Player offense rounding is upward rather than the NPC downward rule. Do not
copy a player formula into NPC authoring or vice versa.

### Ordinary NPC magic

The profile supplies `spellPower = max(1, MagicPower / 12)`.
The magic formula then computes:

`attackMaximum = ceil(max(1, MagicPower / 12)) + max(0, floor(MagicPower / 7))`

This maximum passes through outgoing debuffs and the same basic mitigation
process using Magic Defense. The two uses of Magic Power are both active;
ignoring either produces an incorrect benchmark maximum.

Normal eligible attacks no longer require the old accuracy roll. Old accuracy
helper methods remain in `CombatFormula`, but ordinary current callers use the
new roll methods. Both checked-in MyWorld configurations disable the alternate
OSRS melee/ranged formulas. Boss scripts and special effects still require
individual tracing rather than assuming one universal damage rule.

Sources: [CombatFormula.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java)
lines 88–105, 275–355, 544–560, 594–612, 630–635, 657–673;
[NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java)
lines 85–87; [CombatLuck.java](../../../server/src/com/openrsc/server/model/combat/CombatLuck.java)
lines 14–29; [PvmMeleeEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java)
(the `OSRS_COMBAT_MELEE` branch); [NpcBehavior.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java)
lines 595–627; [myworld.conf](../../../server/myworld.conf) lines 100–101;
[myworld-host.conf](../../../server/myworld-host.conf) lines 111–112.

## 7. Balrog: confirmed legacy-stat authority problem

### Loaded values

Balrog #809 is authored with Attack 999, Strength 250, Defense 80, HP 500, and
combat level 217. Its MyWorld override supplies only defense multipliers
0.75 / 0.5 / 1.0. There is no explicit Magic Power override.

Thus its current baseline defenses are 60 / 40 / 80, Melee Power is 250, and
Magic Power derives as `max(1, 999, 250) = 999`.

Sources: [NpcDefsCustom.json](../../../server/conf/server/defs/NpcDefsCustom.json)
lines 603–617; [NpcDefsMyWorld.json](../../../server/conf/server/defs/NpcDefsMyWorld.json)
lines 1851–1854; `Npc` and profile accessors cited in section 5.

### Active magic maximum

Balrog resolves to the `MELEE_MAGIC` profile and Fire element. The normal NPC
behavior calls the current magic formula and launches the projectile.

- Spell component: `ceil(999 / 12) = ceil(83.25) = 84`.
- Offense component: `floor(999 / 7) = 142`.
- Raw magic maximum: `84 + 142 = 226`.
- For comparison, ordinary melee maximum: `1 + floor(250 / 7) = 36`.

The owner's concern is therefore supported by the active source path. The
legacy Attack field is still controlling modern Magic Power. The value found
is 999, not a missing or zero Attack value.

### Conditions and limits

The default profile projectile range is five tiles. A mixed melee/magic profile
prefers a projectile beyond adjacent range; at adjacent range its preference
check has a 65% success rate. Target validity, path, cooldown and attack
suppression still gate launch. This is a preference check, not a guarantee that
65% of delivered attacks will be magic.

226 is the unmodified raw attack ceiling, not guaranteed HP loss. The NPC roll
can be lower, Magic Defense subtracts mitigation, and applicable prayers,
Fire-resistant robes, magic-resistance potions, summon absorption and True
Defense can reduce the hit further. Actual HP loss is bounded by remaining HP.
No critical-hit multiplier is needed to explain a resolved attack above 200.
Player-only Cleric critical execution does not apply to Balrog as attacker.

Positive primary magic damage can also splash to eligible nearby players
within two tiles of the primary target. Splash starts from half of primary
`legacyDamageDealt`, rounded up. That primary measure is capped to the primary
target's remaining HP. Splash has its own robe/potion/True Defense reductions;
it is not another full 999-power roll.

Sources: [NpcAttackStyleProfile.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcAttackStyleProfile.java)
lines 16, 51–87, 321–330, 437–459;
[NpcBehavior.java](../../../server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java)
lines 537–562, 593–653;
[ProjectileEvent.java](../../../server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java)
lines 583–610, 669–673, 1212–1245;
[DamageResult.java](../../../server/src/com/openrsc/server/model/combat/DamageResult.java)
lines 30–47; [ClericCriticalRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericCriticalRuntime.java)
lines 20–35.

### Authoring consequence

Do not automatically bless Magic Power 999 by copying the current converted
value into a new explicit field. Record it as a confirmed anomaly requiring an
intentional balance decision. This guide does not choose a replacement number,
alter Balrog, or prove which binary/data revision produced any reported live
incident.

## 8. Other representative baselines

Defense order throughout this section is melee / ranged / magic. Values are
before temporary debuffs. They describe the snapshot, not approved future stats.

| NPC | Legacy/reference inputs | Current relevant power | Current defenses | Interpretation |
| --- | --- | --- | --- | --- |
| Lesser Demon #22 | A78, D79, Strength overridden from 80 to 70 | Melee 70; Magic 78 | 59 / 39 / 79 | Multipliers .75 / .5 / 1; old Attack still wins magic fallback |
| Skeleton #40 | D20, Strength overridden to 20 | Melee 20 | 5 / 20 / 10 | Multipliers .25 / 1 / .5 preserve ranged sturdiness |
| Blue Dragon #202 | D105, Strength overridden to 91 | Melee 91 | 105 / 78 / 105 | Fractional defense conversion floors 78.75 to 78 |
| Battle mage #789 | A0, D90, HP120, Strength overridden to 52 | Magic 52 | 9 / 9 / 90 | Pure-magic profile uses Strength when Attack is zero |
| Elder Green Dragon #844 | HP280; direct combat values | Melee 250; Ranged 235; Magic 270 | 265 / 210 / 265 | Direct stats already supported; behavior still separately authored |
| Cockatrice #864 | Legacy A/S/D all 1; HP45 | Melee 40 | 35 / 40 / 30 | Clear example of direct values independent of legacy stats |
| Sapphire Dragon #877 | A100, S90, D100 | Melee 90; Magic 100 | 125 / 93 / 100 | Multipliers 1.25 / .9375 / 1; current exception to old multiplier guidance |
| Stirge #882 | A60, S50, D50; HP60 | Melee 55 | 50 / 80 / 50 | Newly added direct-stat melee enemy |

Sources under `server/conf/server/defs/`:

- [NpcDefs.json](../../../server/conf/server/defs/NpcDefs.json): Lesser Demon
  lines 861–871; Skeleton from 1563; Blue Dragon from 7881; Battle mage from 30774.
- [NpcDefsMyWorld.json](../../../server/conf/server/defs/NpcDefsMyWorld.json):
  Lesser Demon 40–44; Skeleton 47–51; Blue Dragon 103–107; Battle mage 1824–1828.
- [NpcDefsCustom.json](../../../server/conf/server/defs/NpcDefsCustom.json):
  Elder Green Dragon 1997–2013.
- [SlayerMovementPreviewNpcDefs.json](../../../server/conf/server/defs/SlayerMovementPreviewNpcDefs.json):
  Cockatrice 49–63.
- [GemDragonNpcDefs.json](../../../server/conf/server/defs/GemDragonNpcDefs.json):
  Sapphire Dragon 4–14, 42–44.
- [StirgeNpcDefs.json](../../../server/conf/server/defs/StirgeNpcDefs.json): 4–24.

## 9. Zero, missing, and sentinel hazards

These semantics must be designed explicitly before a full authoritative roster
is loaded. The current implementation does not consistently distinguish an
intentional zero from a missing value.

### Full-definition path

Missing direct stats load as zero, and the `Npc` accessors accept an explicit
value only when it is greater than zero. A literal zero in a full definition
therefore follows fallback instead of saying "this stat is authoritatively
zero." This can matter when old Strength/Attack remain positive.

Stirge explicitly writes zero ranged/magic offense, but its current melee-only
profile and false legacy ranged flag also resolve those fallbacks to zero. Its
safe observed result does not prove that zero suppresses fallback universally.

### MyWorld override path

The optional override parser calls `ifZeroReserve` on all six direct integer
stats. That method maps zero to `ZERO_RESERVED`, which equals
`Integer.MAX_VALUE` (2,147,483,647). Unlike the older patch merge path, the
optional override path directly installs the staged definition. No sentinel
normalization was found on that direct path before the accessors consume it.

Static implication: adding a direct zero through this override path can install
an enormous positive stat. This was identified by source tracing, not by running
a reproducer. No current MyWorld override supplies any of the six direct fields,
so the census does not demonstrate an existing affected NPC from that path.
Do not bulk-populate zero-valued overrides without resolving and testing this.

Sources: [EntityHandler.java](../../../server/src/com/openrsc/server/external/EntityHandler.java)
lines 369–380, 506–525, 657–658;
[Functions.java](../../../server/src/com/openrsc/server/plugins/Functions.java)
line 108; `Npc.java` lines 675–727;
[StirgeNpcDefs.json](../../../server/conf/server/defs/StirgeNpcDefs.json) lines 15–20.

### Required distinctions in future documentation and schema proposals

Record these separately instead of silently treating them as equivalent:

- A number deliberately authored and approved, including an intentional zero.
- A missing or not-yet-reviewed number.
- A style the NPC does not use.
- A field retained only for legacy compatibility.
- A temporary effective value after buffs or debuffs.

The representation and validation policy remain undecided. Do not choose a new
sentinel, default, or inference rule merely to complete a table.

### Player zero is already real; NPC authoring zero is a separate problem

The [zero-defense trace](npc-zero-defense-and-attack-contract.md) confirms that
modern player Melee/Ranged/Magic Defense supports true 0. Empty equipment totals
remain 0 through current modifiers. Ordinary mitigation uses
`max(0, ceil(Defense / 7))`; at 0 it skips the defense RNG and subtracts 0, whereas
Defense 1–7 subtracts exactly 1. Legacy skill slots/helper floors do not supply
these three modern defenses. NPC offense can itself roll 0, so zero armor does
not guarantee a positive hit.

That supports the shared raw-armor interpretation. It does not fix the NPC
loader's separate zero-as-fallback and override-sentinel behavior. Authoritative
NPC zero must be specified deliberately rather than copied through those paths.

## 10. Holy Power: current player use and planned inert NPC field

Current player Holy Power governs Cleric buff/debuff effectiveness, separate
from the three damage power/defense pairs. The 2026-11-15 owner clarification
also establishes a planned NPC Holy Power placeholder for possible future Holy
damage. It must be inert now; no fourth damage channel or Holy Defense is being
introduced, and no NPC field has been implemented by this documentation task.

Current Holy Power is player-focused Cleric casting power:

`HolyPower = max(0, current Worship) + max(0, strongest equipped staff Holy Power)`

Worship retains the internal Prayer skill ID for compatibility. Equipment Holy
Power uses the strongest eligible equipped Cleric staff, not the sum of every
staff. The equipment contribution is exposed separately for the equipment
panel. The value selects authored Cleric spell-effect ranks at cast time,
including support and hostile effects. This implements the effectiveness role;
it is not an opposing Holy Power versus Holy Defense damage roll. Effects can
influence later ordinary combat without creating a new damage category.

Do not overstate the rule as "every Cleric spell scales with Holy Power."
The current support caster explicitly assigns Unify rank zero instead of using
Holy Power to select its rank. Utility exceptions should be recorded, not
changed as a side effect of this documentation or stat-data migration.

The blessed staff progression is 8, 12, 16, 24, 28, 32, 40, 44, 48, 56. The three
god staves contribute 64. These are current player equipment facts, not an NPC
conversion formula.

No NPC Holy Power field or accessor exists in `NPCDef`/the current NPC combat
contract, and `holyPower` is absent from the allowed MyWorld override fields.
Some NPC attacks have holy-looking projectiles, but they still use Magic Power
and Magic Defense in the traced general attack path. A visual theme is not
proof of a Holy Power system.

The planned NPC field is not a request to implement NPC Cleric casting or to
reuse the player's Holy calculation. Its exact name/storage/default still needs
a concrete schema decision. Zero is a reasonable proposed inert initial value,
not an adopted value from this document. Changing the future placeholder must
not change current attacks or effects. Do not derive it from Magic Power or
legacy Attack, or turn holy-looking projectiles into a new damage category.
Future Holy damage activation is a separate design step if the owner pursues it.

Sources: [Equipment.java](../../../server/src/com/openrsc/server/model/container/Equipment.java)
lines 1654–1687; [EquipmentStatCalculator.java](../../../server/src/com/openrsc/server/model/container/EquipmentStatCalculator.java)
lines 8–10, 26–55;
[ClericSupportCasting.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericSupportCasting.java)
lines 57–59; [ClericHostileRuntime.java](../../../server/src/com/openrsc/server/content/cleric/runtime/ClericHostileRuntime.java)
lines 22–25;
[ClericSpellDefinition.java](../../../server/src/com/openrsc/server/content/cleric/ClericSpellDefinition.java)
lines 156–168; `NPCDef.java` lines 37–51; `EntityHandler.java` lines 46–54;
`NpcAttackStyleProfile.java` lines 123–134, 220–237.

## 11. Working record for future NPC authoring

The following is a suggested documentation structure, not an approved schema,
balance roster, or instruction to perform an automatic conversion.

For each NPC or deliberately grouped variant, record:

1. Identity: stable ID, name, definition source, quest/boss/summon/fixture role,
   and whether it is placed or only available as a definition.
2. Existing behavior: enabled attack styles, range, cadence, special attacks,
   scripted phases, immunities and relevant effects. For each attack/effect,
   record its power source (or current gap), defense-roll policy, special
   equipment mitigation and damage-settlement path separately.
3. Legacy reference: A/S/D/Hits and any overrides, retained as provenance rather
   than silently promoted to the new authority.
4. Current effective baseline: six resolved numbers, their exact source or
   derivation, HP, displayed level and applicable raw damage ceilings.
5. Proposed explicit values: each number, rationale, status, and owner decision.
   Mark undecided values explicitly in documentation; do not invent zeros.
6. Delta: what changes from the current baseline, whether that change is an
   intended rebalance or correction, and which encounters/tests could change.
7. Verification: test evidence, source revision, review outcome and unresolved
   limitations. Keep live-deployment verification distinct from local tests.

Suggested decision statuses are "current observed", "proposed", "owner
approved", "implemented", and "verified". A value is not approved simply
because a tool calculated it or a previous document listed it.

### Principles that keep the work aligned

- Present and discuss plain per-style values. Preserve old multipliers only as
  historical explanation or a deliberately bounded compatibility mechanism.
- Keep each NPC defense independent. Never normalize the three NPC defenses to
  a shared 100% budget. This does not repeal the separate equipment-item budget
  rules described in section 3.
- Do not automatically copy erroneous outputs, especially Balrog's magic value.
  The existing converted result is evidence, not design approval.
- Separate stat authoring from attack selection and special encounter behavior.
- Separate baseline stats from transient effective stats and from damage dice.
- Do not infer that positive power enables a style, or that zero disables it,
  until the new contract explicitly guarantees those semantics.
- Preserve existing IDs, HP, displayed levels and behavior unless a specific
  approved decision changes them. Do not tie displayed level to a new formula
  as an accidental side effect.
- Trace remaining old-field consumers before isolating legacy authority. Retain
  and annotate legacy data/code for history or required compatibility; document
  why it is inactive, its replacement, and any allowed legacy-mode consumers.
  A cosmetic rename alone does not prevent accidental reuse.
- Preserve the established matching-defense model, while recording permitted
  defense-bypass/special-mitigation attacks explicitly. Every enemy attack must
  still have a power source under the intended contract.
- Preserve player Holy Power's Cleric role and keep the planned NPC Holy field
  inert. A placeholder does not authorize new damage or casting behavior.
- Review current family/individual exceptions rather than reinstating obsolete
  constraints from archived plans.

## 12. Verification and completion criteria for later implementation

No gameplay implementation or test run is claimed by this guide. For a later
approved migration, the verification plan should cover at least:

- Loader tests for missing fields, authored positive values, intentional zero,
  invalid negative/out-of-range values, duplicate IDs, and all relevant load
  layers. Ensure reserved sentinels cannot become combat values.
- Accessor tests proving that explicit values are authoritative under the chosen
  contract, including inactive styles and temporary debuffs.
- Characterization of the settled combat model through the actual scaled roll
  bounds, matching defense, subtraction/zero floor, distributions and modifiers.
  Preserve player Holy Power's effect-rank role and documented utility exceptions;
  verify that the planned NPC placeholder remains inert when implemented. Do not
  invent a Holy Defense matchup or new raw-stat dice during migration.
- Focused player-zero characterization: naked 0/0/0 and packet zeros, defense 0 versus 1,
  zero-preserving effects, debuff 1 to 0, and retained skill 1 not supplying armor.
- Per-attack power provenance, including secondary/scripted damage and explicit
  defense-bypass/equipment-mitigation exceptions. Current fixed/HP-based paths
  need an approved mapping, not an unsupported claim of universal compliance.
- A legacy-independence test: varying deprecated Attack/Strength/Defense must not
  change a migrated NPC's modern power/defense or attacks unless that dependency
  is an expressly retained compatibility rule.
- Balrog regression coverage with an intentionally approved Magic Power and
  ceiling, including primary attack, mitigation and splash. Do not use 226 as
  the approved target merely because it is the current observed maximum.
- Ordinary melee, ranged, magic and mixed enemies; direct-stat and formerly
  derived enemies; low-level zero cases; bosses, quest phases and owned summons.
- Preservation of attack style selection, range, cadence, damage distribution,
  HP and displayed level for NPCs whose behavior is meant to remain unchanged.
- Definition-load order, ID stability and any editor/export/client surfaces
  that read or communicate these stats.
- Relevant production build and combat regression gates, with exact revision and
  pass/fail/not-run status. A static scan is not equivalent to a combat pass.

Existing navigation points include:

- [Combat test gate](../../combat/combat-test-gates.md), whose authoritative
  entry point is `./server/test_combat`.
- [NPC defense profile checks](../../../tests/myworld/test-npc-defense-profiles.py).
- [NPC attack-style checks](../../../tests/myworld/test-npc-attack-styles.py).
- [NPC elemental magic checks](../../../tests/myworld/test-npc-elemental-magic.py).
- [Current profile/lifecycle characterization](../../../server/test/com/openrsc/server/combat/CurrentNpcProfileLifecycleCharacterization.java).
- [Owned damage characterization](../../../server/test/com/openrsc/server/combat/CurrentCombatOwnedDamageCharacterization.java), including Balrog splash behavior.

Inspect those tests before selecting or extending them; existing assertions may
characterize legacy behavior that an approved change intentionally replaces.
Keep tests isolated from live servers, live databases and player state. Public
activation remains subject to the repository's separate deployment/shutdown
rules and owner authorization.

A future migrated roster is complete only when its intended numbers and
behavior are reviewed, its chosen zero/missing contract is unambiguous, remaining
legacy dependencies are documented or removed at the approved boundary, and the
required checks pass. Documentation, implementation, and deployment are three
different completion states.

## 13. Recommended path to the shared end state

The combat meanings in section 1 are settled owner direction. The migration
steps below are recommendations for owner review within that boundary, not a
request to agree those meanings again. They do not select final NPC balance
values or authorize implementation. Each phase has a reviewable output and a
checkpoint; a later phase should not silently settle an earlier open choice.

### Recommended overall approach

Keep the current shared combat accessors as the transition seam. Document the
established stat semantics and build explicit NPC authoring around that seam,
preserving player progression and existing damage rules. Migrate reviewed NPC
batches, then remove the old MyWorld fallback authority once coverage and tests
justify it. Update displays and tooling deliberately rather than silently
changing packet semantics.

Do not combine this into one large rebalance, protocol rewrite, player-save
migration and legacy deletion. That would make unintended changes difficult to
identify and reverse.

### Phase 1: document the established contract and remaining boundaries

The general stat meaning and same-system armor relationship are already decided.
So are the all-enemy-attacks power-source requirement, special-mitigation allowance,
planned inert NPC Holy field, and preservation/annotation retirement policy.
This phase records those decisions and checks their implementation boundaries;
it does not ask the owner to approve the same principles again.

Documentation outputs:

- A short stat dictionary grounded in the settled three power/defense pairings
  and separate Holy Power effectiveness role. Record units, baseline/current
  meaning, producers, consumers, allowed values and effect ordering. Mark
  unresolved representation details rather than treating the core model as open.
- The dated owner decision in section 1, the exact existing roll implementation
  in section 6, and Holy Power's cast-time ranks and utility exceptions in
  section 10, plus the focused zero-defense/attack-source findings. Specify the
  planned inert NPC Holy field without activating future damage or casting.
- A scope statement distinguishing NPC conversion removal from player item
  conversion cleanup or progression changes. Combat-math redesign is not part
  of the current task.
- A standard NPC record using section 11, plus a player comparison record with
  current skill, gear contribution, temporary effects and resolved totals.

Recommended checkpoint: verify that the dictionary accurately describes the
established model, then review only the remaining presentation, scope and data
boundaries before choosing a schema. The UI vocabulary should say Power; code
aliases may continue to say Offense while compatibility is being managed.

### Phase 2: inventory authority and review outliers

Produce a read-only inventory from the actual configured load order, expanded
from the census in section 4. For every NPC, include direct fields, resolved
baseline values, derivation provenance, enabled attacks, special owners, HP,
displayed level, and relevant raw damage ceilings. Classify ordinary enemies,
quest/boss variants, owned summons, fixtures and unused/unplaced definitions.

Also produce a consumer map covering:

- Shared getters and direct reads of legacy NPC fields or skill slots.
- Boss/quest scripts, summons, effects, projectile selection and utility spells.
  Identify a power source for every enemy attack, separately from ordinary
  defense, bypass, shield protection and later damage settlement. The focused
  trace already found fixed/target-HP gaps; do not assume universal compliance.
  Record them for later treatment as the owner directed. Their resolution is
  not a prerequisite to the current defense-only documentation pilot.
- Player equipment conversions, signed contribution/clamping rules and current
  progression providers.
- Packets, equipment/bank panels, tooltips, developer inspection, exports and
  editor surfaces, including sources outside the six getters.

Flag outliers instead of auto-accepting them: Attack far above Strength, zero
legacy values, sentinel values, unused positive powers, unexpected profiles,
large derived maxima and exceptions to historical family ratios. Balrog is the
first confirmed case, not proof that every other NPC is correct.

Recommendation: automate reporting and arithmetic checks, but do not have the
report rewrite production numbers. It may propose a baseline for owner review;
current behavior and approved intended behavior must remain separate columns.

Checkpoint: review the inventory and choose which cases preserve behavior,
which need corrections, and which remain undecided. Do not declare every
converted number official by importing the report wholesale.

### Phase 3: choose representation and zero/missing behavior

Options to review:

| Choice | Benefit | Cost/risk |
| --- | --- | --- |
| Keep current flat `*Offense`/`*Defense` fields, add explicit presence/version semantics | Smallest schema and consumer change | Naming remains mixed; omission and partial records require strong validation |
| Add a versioned structured combat-stat block in the NPC definition | Clear completeness and authority boundary; fits a shared typed view | Loader/export/tool migration and a deliberate transition are required |
| Maintain a separate ID-keyed canonical stat catalog | Easy focused roster review | Another authoritative file can drift from identity/behavior data unless duplicate/missing IDs are rejected |

Recommendation: one authoritative NPC stat record per ID, normalized to a shared
typed server representation. A versioned complete block is attractive, but the
exact storage option should follow the consumer inventory rather than be chosen
only for cleaner-looking JSON. Do not maintain two editable numeric authorities.

Recommended validation semantics for review:

- An authored zero means zero and never asks for fallback.
- Missing means missing, not zero, not disabled style and not legacy derivation.
- A record declaring itself canonical must supply the required six values;
  incomplete canonical records fail validation rather than partly falling back.
- The accepted final NPC domain should use explicit numeric bounds and reject
  sentinels/invalid values. The actual upper bounds remain a design decision;
  do not invent a universal cap from one boss.
- Signed equipment contributions remain a separate representation from resolved
  combat totals. Preserve their existing clamping and stacking semantics.
- During transition, any compatibility resolver is explicitly selected for
  legacy records. New canonical NPC definitions must not reach it silently.

Whether a non-attacking style is stored as zero or a deliberately unused positive
value must be decided independently of attack behavior. The planned inert NPC
Holy Power field needs an explicit storage/default/validation contract, without
borrowing player Cleric calculations or activating any new damage channel.

Checkpoint: approve one representation and transition rule; write loader and
zero/sentinel characterization tests before populating production values. The
MyWorld zero-sentinel issue must be resolved, not carried into the new format.

### Phase 4: author and validate a small representative pilot

The [documentation-only pilot proposal](npc-defense-pilot-proposal.md) is now
prepared at the owner's request. It uses current percentages as clues, preserves
family identity, and lists exact current/proposed defenses with mitigation-tier
consequences. Four of 14 rows propose changes; none are approved or installed.
Special-attack treatment is deferred and does not block this proposal.

No runtime pilot has started. For a later separately approved implementation/test
pilot, recommend coverage of a simple melee enemy, a ranged enemy, a caster, a
mixed-style enemy, an already explicit-stat NPC, and Balrog as a deliberate
outlier decision. Exact IDs and final values remain for review. Include a zero
case and a scripted encounter; do not infer that one ordinary enemy proves all
special owners safe.

For each pilot NPC, approve the six plain values and separately record HP,
level, attack selection/timing and special mechanics. Review the old/current/new
comparison and intended behavior deltas. Historical family ratios may explain
current values, but future editing should directly change the authored numbers.

Use player comparison cases to prove shared meaning without changing player
progression: armed/unarmed, signed gear penalties, skill changes, Body robe
power, timed effects, and Holy Power contribution versus total.

Checkpoint: the pilot has an approved stat record and test expectations. Balrog
has an intentional target rather than an unexplained copied maximum of 226.
No pilot is considered complete on documentation alone if implementation is
later requested.

### Phase 5: implement the shared read path and migrate reviewed batches

Future implementation recommendation, not work performed by this guide:

- Expose the shared semantic view through the existing `Mob` seam or a small
  typed view behind it. NPCs read authored numbers; players retain their own
  dynamic provider. Existing `*Offense` names can temporarily delegate to that
  view instead of requiring a repository-wide rename in the same change.
- Keep temporary effects and actor-specific damage policy separate from baseline
  data. Preserve current rounding, clamping and order unless a change is approved.
- Preserve existing launch/impact and lifecycle timing. A shared snapshot must
  not introduce stale stats across equipment changes, buffs, respawn or later
  attacks, or move an existing calculation to a different event boundary.
- Use explicit, visible transition state for legacy versus canonical NPC records.
  Avoid a permanent "use old stats if anything looks wrong" safety net.
- Migrate small approved batches with parity comparisons. Record every intentional
  difference; investigate unexplained differences before proceeding.

For each batch, run the relevant isolated checks from section 12, including a
legacy-independence test: changing old Attack/Strength/Defense must not change a
canonical NPC's modern results. Test inactive styles and direct scripted attacks,
not only the general profile. Check player outputs have not changed accidentally.

Checkpoint per batch: approved numbers installed, expected deltas proven,
regressions passed, unresolved issues recorded, and exact revision identified.
Keep commits and review scope narrow enough to back out a batch without reverting
unrelated work. Actual rollback/deployment actions follow the repository workflow
and require their own applicable authorization.

### Phase 6: unify presentation and retire legacy authority

Recommend a clearly named server-authoritative total-stat view, with an optional
breakdown of skill, equipment and effects. NPC inspection should show the same
six names and units, with authored baseline/current values distinguished.

Display options:

- Keep the existing mixed panel with clear contribution/total labels and add a
  total combat view. This preserves its current value meanings but adds another
  surface to maintain. Making all existing fields contribution-only would itself
  change defense presentation.
- Redesign the panel to show totals and a contribution breakdown. This can be
  clearer, but needs deliberate protocol/client compatibility and tooltip work.

Do not silently put full player totals into fields interpreted as gear-only
power. Audit bank displays, spell estimates and utility spell consumers. A new
or versioned packet can make the meaning explicit; the exact UI and wire format
remain undecided. Keep player skill/save aliases intact.

Before removing the MyWorld NPC legacy resolver, require:

- Every NPC in the approved rollout scope has a complete reviewed canonical
  record, including fixtures/special actors that actually use that scope.
- All old-field consumers are removed, redirected, or explicitly isolated in a
  named non-MyWorld/legacy compatibility boundary.
- No migrated actor relies on a name-based power inference or a multiplier to
  obtain its canonical baseline. Attack-behavior name rules are a separate audit.
- Definition validation and creation tooling reject accidental fallback for new
  canonical records; inspection/reporting can identify any remaining legacy actor.
- Required parity, intentional-delta, protocol and lifecycle tests pass.

Then retire derivation authority from the modern path, not the historical
material itself. Preserve and annotate legacy data/code as appropriate for
history and required compatibility. At each retained boundary, state why it is
inactive for modern combat, which explicit stat path replaces it, and any
remaining allowed compatibility use. Use tests or appropriate guards to catch
accidental modern reuse rather than leaving an undocumented fallback.

Do not delete/reorder player Attack/Melee, Defense, Strength or Prayer/Worship
storage identities as cleanup. Old documents should point to the new authority
while retaining their historical explanations. Physical deletion is not this
migration's objective.

Checkpoint: an author can create or adjust an NPC by editing plain reviewed
numbers, inspect the same stat meanings used for players, and verify that old
A/S/D changes cannot influence that NPC's modern combat. Production activation
is a separate completion state from this source/data cutover.

### Suggested next documentation deliverable

The full definition inventory and zero-defense trace exist, and the
[14-row defense pilot proposal](npc-defense-pilot-proposal.md) is ready for review.
Use it to establish the larger defense picture before resolving special attacks.
Do not bulk-copy conversion outputs or reactivate retained legacy authority.

Recommended remaining decision order:

1. Review the proposed family shapes, four numeric changes and broad-defense
   controls. Record accepted/revised/rejected numbers rather than assuming that
   current values or this proposal are automatically approved.
2. Refine the flexible progression guidance and related-family coverage before
   expanding the roster. Explain deviations from meaningful existing ratios.
3. Before any actual implementation, choose authoritative NPC armor-stat storage,
   zero/missing rules and the inert Holy placeholder's representation/default;
   define retained legacy annotations/guards and verification expectations.
4. Once the larger picture is clear, return to the recorded attack-source gaps
   and special mitigation cases. Their final power mappings and values remain
   deferred; no definitive treatment is selected here.
5. Obtain separate approval for implementation or a gameplay pilot. This work
   has only prepared documentation and proposed numbers.

These are proposed checkpoints, not claims that the owner has approved a schema,
particular balance numbers, or implementation work.

## 14. Record maintenance

Update this guide as decisions are made, keeping observed facts and approved
intent visibly separate. Record the revision used for each new census or source
trace. Move resolved questions into a dated decision record rather than silently
rewriting history. Keep the NPC-level roster and implementation evidence linked
here when they are created.

### Investigation receipt

Completed for this document:

- Read repository guidance and workspace status.
- Traced definitions, loader precedence, six accessors, general attack profiles,
  ordinary combat math, Holy Power and Balrog primary/splash behavior.
- Extended the source review to player stat providers, equipment contributions,
  mixed packet/display semantics, selected utility consumers and skill/save
  compatibility. Added the owner's shared-statline goal and a proposed phased
  migration path without treating recommendations as approved decisions.
- Recomputed the static census against the stated later snapshot and checked
  append IDs against load order.
- Distinguished owner rationale, existing implementation, confirmed source
  hazards and undecided design choices.
- Recorded the owner's 2026-11-14 clarification as settled direction: preserve
  power-versus-corresponding-defense damage and separate Holy Power Cleric
  effectiveness. Removed those meanings from open decisions and revised the
  roadmap without changing or claiming changes to runtime formulas.

- Added the 2026-11-15 shared raw-armor clarification, true-zero source trace,
  enemy power-source requirement and confirmed exceptions, planned inert NPC
  Holy field, and preserve/annotate legacy retirement policy. Pilot remains
  unimplemented. The focused report records isolated arithmetic checks separately
  from unrun gameplay tests. The owner's later direction now has a separate
  14-row documentation pilot proposal; special-attack treatment is deferred.

Not performed: gameplay edits, NPC numeric changes, Holy field implementation,
pilot execution, live-server inspection, combat reproduction, runtime tests,
deployment or a final balance decision.
