# A07.2 Secondary-Effect Descriptor Inventory

## Result and boundary

A07.2 gives the current proc, debuff, healing, reflection, child, and
compatibility semantics 71 stable identities. `SecondaryEffectDescriptor` is
an immutable descriptive catalog. It does **not** register handlers, execute an
effect, select targets, draw random values, spend charges, settle damage, or
move death authority. No production combat caller reads it.

The `semantic.*` namespace is deliberately separate from A05's 39
`SecondaryEffectPolicy` settlement keys. That distinction preserves facts such
as:

- Bear Maul, dragon breath, elemental weapons, and leather effects retaining
  separate gameplay identities while sharing auxiliary damage helpers;
- one jewelry-recoil semantic retaining different melee/projectile settlement
  adapters;
- one summon-trait semantic selecting its existing Magic or melee bonus
  settlement; and
- delayed god-spell damage, poison/withering, and aggregate lifesteal retaining
  distinct semantics inside one delayed owner.

The descriptor fields record phase membership, combat styles, participant
gate, zero-damage eligibility, random-draw position, charge/state owner,
recursion, presentation, current code owner, and executable evidence. An effect
may belong to more than one phase when the current paths genuinely differ. For
example, melee poison is considered before primary damage while projectile
poison is considered only after a surviving impact.

## Current phase inventory and planning budget

The current count is the number of semantic descriptors that may be considered
in that phase, not the number that can all succeed on one hit. Equipment,
style, source/target, and mutually exclusive branch gates reduce real work, but
the conservative registration count prevents a future executor from silently
dropping candidates before those gates are evaluated.

| Phase | Current descriptors | Approved planned | Reviewed headroom | Planning budget |
| --- | ---: | ---: | ---: | ---: |
| Pre-primary damage | 8 | 0 | 4 | 12 |
| Pre-primary settlement | 5 | 0 | 4 | 9 |
| Post-primary damage | 12 | 0 | 4 | 16 |
| Surviving target | 30 | 0 | 4 | 34 |
| After root attack | 9 | 0 | 4 | 13 |
| Target terminal | 1 | 0 | 4 | 5 |
| Kill settlement | 3 | 0 | 4 | 7 |
| Delayed impact | 8 | 0 | 4 | 12 |
| Periodic tick | 2 | 0 | 4 | 6 |

Four slots are the smallest reviewed headroom that permits one coherent
effect family to be proposed without immediately changing the planning value.
It is not a permanent constant: every approved effect must be assigned a proven
phase and the budget recalculated before implementation. No runtime enforces
these values in A07.2.

The surviving-target budget is 34. Classic-Scape's rejected fixed value of 32
therefore cannot provide both the current inventory and even this minimal
reviewed headroom. The total catalog is unbounded by an unrelated fixed value
and currently contains 71 descriptors.

## Stable semantic inventory

The Java catalog is the exact field-level inventory; the groups below make its
review boundaries navigable.

### Incoming and pre-settlement semantics

- compatibility player-poison script and active NPC-poison script;
- Body Robe power charging and summon damage absorption;
- Frostbite reflection;
- Cleric Ward/Aegis protection; and
- Cleric Zeal damage adjustment.

Fervor, True Defense, base armor resistance, potion reductions, and ordinary
equipment/formula multipliers remain primary accuracy or mitigation policy.
They are not secondary effect registrations and are intentionally absent.

### Post-primary semantics

- summon-owner assist credit, Giant Bat lifesteal, Divine Grace, Blood Amulet
  lifesteal, Corrosive Aura, and Divine Retribution;
- Blood Robe splash and Balrog Magic splash;
- Cleric Rally and Thorns;
- Death Ring charged hit; and
- projectile Splinter.

Splinter is recorded after primary damage rather than under the later surviving
proc chain because the current projectile owner can run it before the primary
terminal branch.

### Surviving-target semantics

- summon trait on-hit;
- current-hit poison-marker reset, weapon poison, style-armor poison, Black
  Dragon poison, and King Black Dragon poison;
- Elemental Giant might, Ogre stagger, Baby Dragon smoke, Infernal Fire, and
  Hell's Inferno splash;
- Blue, Earth, and Red Dragon effects plus separate Black and King Black Dragon
  breath follow-ups;
- Elder Green Dragon armor breath and burn application;
- projectile Startle, Acid, Frostbite, Wind, Water, Earth, Fire, and ranged
  dragon breath;
- Elder Green Dragon projectile branch selection and immediate burn
  application;
- Ring of Life; and
- the maintained Tutorial Island rat safety script.

The compatibility poison and tutorial scripts are active compatibility code,
not removal candidates. Their dynamic script-owned ordering is described but
not normalized on this branch.

### Root follow-ups, terminal work, and kill settlement

- Elder Green Dragon melee sweep as a pre-primary replacement attack;
- Bear Maul second hit, melee dragon breath, elemental sword, Demon Pitchfork
  Hell's Blaze, Kolodion Fire Claw, Chaos chain lightning, Scythe cleave, and
  jewelry recoil as post-root work;
- Death Robe overkill at target terminal; and
- Death Ring charge acquisition, Death Amulet burst, and Soul Amulet burst in
  `Npc.killedBy` kill settlement.

Scythe itself remains root-only, while its child retains exceptional
descendants. A child can still reach the characterized
Bear/dragon/elemental/poison, lifesteal, Death Ring, and Death Robe work.
Nothing in the descriptor catalog suppresses or expands that chain.
Child NPC deaths also retain the existing `Npc.killedBy` Death Ring/Death
Amulet/Soul Amulet callbacks; those are recorded as kill-descendant behavior,
not silently treated as non-recursive work.

### Delayed and periodic semantics

- Elder Green Dragon fireshot;
- god-spell area damage, Guthix poison, Zamorak withering, and Saradomin
  aggregate lifesteal;
- Iban Blast area damage and Salarin's second strike; and
- Elder armor and Elder boss burn pulses.

DoT **application** is relevant to A07 ordering, but tick provenance,
replacement, lifecycle, death, logout, and kill credit remain A08. The two
current Elder burn clocks stay content-owned; the catalog does not create a
generic DoT executor.

## Randomness and ordering evidence

The metadata distinguishes no draw, one effect draw, adaptive transactional
poison, launch/impact draws, a shared mutually exclusive branch draw, per-hop
chain chance/selection, and per-child/per-target/per-tick payload draws. These
are not interchangeable:

- the Elder projectile selector draws once, then chooses burn or fireshot;
- chain lightning draws chance and target on every hop;
- Splinter draws its proc before its one random target;
- adaptive poison owns success/failure settlement in `PoisonProcChance`;
- god-spell poison draws only for secondary Guthix targets; and
- deterministic debuffs do not gain a registry draw merely because nearby
  effects have one.

The 86-scenario compiled combat gate continues to execute the current event
owners. Its A05.4 families prove reflection, chain, Splinter, robe, Scythe,
jewelry, boss, summon, and delayed ordering. Existing Cleric, poison, leather,
jewelry, prayer, and summoning fixtures provide the remaining source evidence.
The new A07.2 fixture asserts exact stable keys, complete metadata, immutable
catalog/sets, phase counts, budgets, representative multi-phase/RNG/recursion
facts, and the absence of executor-shaped methods.

## Active-plan audit

No approved but unimplemented combat-effect semantic is concrete enough to
assign to a phase today:

- the Boss Leather plan's Balrog, KBD, Hell's Inferno, and Elder effects are
  already implemented and included as current descriptors;
- the Cleric plan records all twelve launch spells and C10 direct effects as
  implemented;
- the Tier 11 Magic plan's current gear effects are implemented, while its
  Mage Arena follow-up does not settle a new on-hit identity or policy; and
- `work-items.md` describes a provisional future Magic identity pass, not an
  approved effect with a phase and RNG contract.

Those provisional ideas are not hidden inside headroom or treated as promised
features. Once one is approved, its descriptor, phase evidence, and budget
impact must be reviewed explicitly.

## Compatibility and stop conditions

- A05 settlement identities and observations remain unchanged.
- `ELDER_GREEN_DRAGON_MAGIC_SECONDARY` remains an A05 compatibility/reserved
  settlement identity without an active call site; A07.2 does not invent a
  semantic effect for it.
- Dynamic combat scripts, Ring of Life, boss/summon owners, and delayed spell
  owners remain in their maintained architectural boundaries.
- No selector, registry, shared executor, random service, charge transaction,
  packet, animation, damage, balance, timing, or death path changes here.

A07.3 is now characterized and implemented as the bounded live/snapshot policy
recorded in
[`combat-a07-player-owned-npc-radius-selection.md`](combat-a07-player-owned-npc-radius-selection.md).
A07.4 now owns chain and random-single traversal through the separate
compatibility policies recorded in
[`combat-a07-chain-random-traversal-policy.md`](combat-a07-chain-random-traversal-policy.md).
A07.5 begins with the proven-identical Ogre Stagger proc recorded in
[`combat-a07-ogre-stagger-proc.md`](combat-a07-ogre-stagger-proc.md), followed
by the separately characterized Baby Dragon smoke proc recorded in
[`combat-a07-baby-dragon-smoke-proc.md`](combat-a07-baby-dragon-smoke-proc.md).
Later families still require separate characterization and branches.
