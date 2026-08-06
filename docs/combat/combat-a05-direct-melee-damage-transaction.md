# A05.2 Direct Melee Damage Transaction

This is the historical direct-melee authority record. A05.3's bounded primary
projectile-impact continuation is documented in
[`combat-a05-primary-projectile-damage-transaction.md`](combat-a05-primary-projectile-damage-transaction.md).

## Outcome and authority boundary

A05.2 moves only the primary Hits mutation shared in shape by
`PvmMeleeEvent.inflictDamage` and `CombatEvent.inflictDamage` behind the
server-owned `ResolvedDamageTransaction`. Each event still resolves its own
formula, summon adjustment, mitigation, effect ordering, contribution,
lifesteal, packets, survivor hooks, and death adapter.

The transaction accepts only a `DamageRequest` at the existing
`RESOLVED_LEGACY` stage. It performs, in order:

1. capture current Hits;
2. call the existing `Skills.subtractLevel(HITS, damage, false)` boundary;
3. set the existing damage update;
4. append exactly one existing hitsplat;
5. construct an immutable `DamageResult`; and
6. safely publish that result to the optional observer.

It does not resolve accuracy or damage, apply new mitigation, own
contribution, dispatch packets, call effects, decide death, or provide
deduplication/rejection policy. Secondary, reflected, area, projectile, DoT,
environmental, script, and compatibility-helper damage remain outside this
branch.

The two stable source keys are `pvm-melee-primary` and
`reciprocal-melee-primary`. Both requests retain melee style, event identity,
optional current encounter identity, source/target lifecycle snapshots,
resolved damage, and hitsplat type.

## Result semantics and compatibility

`APPLIED_CURRENT_PATH` distinguishes a transaction-owned mutation from the
retained `OBSERVED_CURRENT_PATH` compatibility factory. The result records
Hits before/after, factual HP loss, requested overkill, and the immediate
terminal state.

The existing shared Hits setter contains Goblin's Tenacity. On a lethal
request which procs Tenacity, factual HP loss is smaller than resolved damage,
but both melee events historically retain the pre-Tenacity displayed damage
and pass `min(resolvedDamage, hitsBefore)` to their downstream hooks. The
result therefore exposes that historical value separately as
`legacyDamageDealt`. Both events consume it so summon/Blood/Cleric lifesteal
and subsequent effects retain their established input. This is compatibility
reporting, not a new mitigation or credit rule.

Observer enablement and callback failures remain non-authoritative. They are
caught only after the transaction has produced its gameplay result and cannot
prevent, repeat, or roll back Hits or hitsplats. Production still uses the
inert observer and stores no observations.

## Preserved event ordering

The direct transaction replaces only the adjacent Hits/damage-update/hitsplat
statements. In both events the surrounding order remains:

1. summon eligibility/outgoing adjustment and hit-attempt accounting;
2. side-effect/combat scripts and Paralyze compatibility;
3. robe, potion, summon, Frostbite, True Defense, and Cleric mitigation;
4. the resolved direct-damage transaction;
5. established lifesteal and contribution paths;
6. Death Ring, Blood Amulet, Cleric Rally/Thorns, Corrosive Aura, and Divine
   Retribution ordering;
7. sound, stat, party, survivor, retaliation, and equipment hooks; then
8. each event's unchanged terminal adapter, NPC XP/drop handling, and plugin
   callbacks.

No combat random source, formula, balance value, packet, animation, death
authority, or plugin contract moves into the transaction.

## Executable verification

The authoritative combat gate now contains 38 scenarios. A05.2 adds exact
fixtures for both primary melee classes covering:

- zero, nonlethal, lethal, and displayed-overkill hits;
- one damage update and one standard hitsplat per primary mutation;
- actual-damage contribution capping and damage ownership;
- established lifesteal order;
- exact terminal states, kill type, one death-listener callback, and NPC
  removal;
- exact current Melee/Hits XP settlement and parity between both event paths;
- `APPLIED_CURRENT_PATH` results, stable keys, styles, event identities, and
  one observer publication per mutation;
- player-to-NPC and NPC-to-player directionality; and
- Goblin's Tenacity preserving 1 Hit, the original displayed hit, factual HP
  damage, and the distinct historical downstream hook value in both events.

The existing combat and focused regression gates continue to cover formula
replay, Cleric direct-effect order, summons, secondary melee/AoE paths,
projectiles, poison lifecycle, layered ownership, and observer-failure
isolation. Production artifact checks must include
`ResolvedDamageTransaction.class` and exclude all combat fixture classes.

No client packets or player-visible presentation changed, so this branch has
no private visual acceptance surface. Any formula, mitigation, hitsplat,
contribution, lifesteal, XP, effect, death, packet, RNG-draw, or callback-count
delta remains a stop condition.

## Provenance and next boundary

The request/result and transaction separation continues the selectively
adapted architecture from Classic-Scape commit
`e00c154b4650dc0a80f9cca91fde99cde77d01fa` by aicovergod. Spoiled Milk keeps
its current event-specific policy and omits the imported pipeline's broad
flags, contribution authority, deduplication, and rejection policy.

A05.3 may consider only primary ranged/magic projectile impacts after their
launch/impact timing and complete hook order have exact executable coverage.
It must not use A05.2 as permission to migrate secondary or compatibility
damage families.
