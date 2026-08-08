# Current Combat Characterization Gate

The current Spoiled Milk combat characterization suite is an executable Java
regression gate over the current server implementation. It records behavior;
it does not define new balance, formulas, timing, or combat authority.

## Authoritative entry point

Run the Linux/macOS launcher from the repository root:

```bash
./server/test_combat
```

On Windows, run:

```bat
server\test_combat.cmd
```

Both launchers use the tracked Ant 1.10.5 distribution at
`tools/vendor/apache-ant-1.10.5` and the `test_combat` target in
`server/build.xml`. `test_combat_strict` is an equivalent Ant alias for tools
that expect a strict target name. The CI Java 8 build runs the Linux launcher.

The test target compiles production server sources and the ten combat fixture
sources into isolated directories under `output/combat-test`. It never adds the
fixture source root to `compile_core` or `compile_plugins`. A successful run
removes those class directories and leaves this machine-readable receipt:

```text
output/combat-test/summary.properties
```

The Ant gate requires the receipt and a positive integer
`combat.summary.scenarios`. A Java process that exits successfully without a
receipt, or a receipt that reports zero scenarios, fails the build.

## Current scenario inventory

The A01 fixture executes these production paths:

1. melee, ranged, and magic damage-share XP plus Hits-focus distribution;
2. NPC attack eligibility/approach before plugin block and action callbacks;
3. signed-level spatial-domain and hostile line-of-effect rejection;
4. ordinary NPC retreat followed by hostile-target re-engagement;
5. NPC poison clearing through death removal and respawn;
6. Cleric Ward, Aegis, Thorns, and Rally ordering and values;
7. summon-owner damage contribution, threat, and style-XP exclusion;
8. scythe secondary-target eligibility;
9. shuriken target cap, eligibility, and uniqueness;
10. shared Cleric AoE caster, range, level, duplicate, and line-of-effect
    filtering; and
11. exactly-once NPC death-listener/drop callback dispatch.

The fixture constructs the production `Server`, `World`, `Player`, `Npc`,
handler, event, plugin, party, and transient-effect types. Test-only reflection
is limited to invoking existing private behavior and installing isolated
fixture state; it does not patch production classes or replace calculations.

## Gate self-tests

Use the deliberate-failure property to prove a launcher or imported build entry
point propagates a failed combat gate:

```bash
./server/test_combat -Dcombat.injectFailure=true
cd server && ./gradlew -Dcombat.injectFailure=true test
```

Use this command to prove an empty successful-looking harness cannot pass:

```bash
./server/test_combat -Dcombat.forceZeroScenarios=true
```

The first two commands must report `Deliberate combat gate failure fixture
requested` and exit nonzero. The zero-scenario command must write a zero receipt,
then report `Combat harness executed zero scenarios` and exit nonzero.

Gradle remains secondary and non-authoritative. Its `test` task imports and
depends on the Ant `test_combat` target before supplemental JUnit discovery; it
does not produce deployment artifacts or establish Gradle dependency parity.

## Packaging and review checks

After `./scripts/build-server.sh`, verify that production artifacts do not
contain A01 fixtures:

```bash
if jar tf server/core.jar | grep -q 'com/openrsc/server/combat/CurrentCombat'; then exit 1; fi
if jar tf server/plugins.jar | grep -q 'com/openrsc/server/combat/CurrentCombat'; then exit 1; fi
```

The server build's artifact inventory enforces these fixture exclusions and
remains the authority for fat JAR contents and plugin discovery. Run
changed-code analysis and the focused
combat, poison, projectile, NPC, Cleric, summoning, dragon, and layered-world
Python suites alongside this gate.

## A01-A05.1 boundary

A01 established the real-Java gate without moving production authority. A02
adds the bounded production-preserving clock, random, and whole-tick test seams
recorded in [`combat-a02-required-seams.md`](combat-a02-required-seams.md), so
the same gate now also replays chosen hit/miss, NPC style, cooldown, retreat,
projectile, secondary-target, lifesteal, effect-expiry, and selected-drop
boundaries. Source-text checks remain supplemental and must not replace these
runtime scenarios.

A03 grows the same gate from 20 to 26 scenarios. Its added scenarios exercise
reason-coded compatibility messages, exact melee intent/plugin ownership,
side-effect-free denial, lifecycle and lease cancellation, all maintained
player attack-start styles, missing-resource rollback, and manual priority over
passive autocast. The implementation and retained content-policy boundaries are
recorded in
[`combat-a03-eligibility-transactions.md`](combat-a03-eligibility-transactions.md).

A04 grows the gate from 26 to 32 scenarios. Its added scenarios exercise one
outgoing/many incoming engagement ownership, retargeting without peer teardown,
stale callback isolation, teleport/logout/death cleanup, passive retaliation,
explicit audit repair, and reciprocal melee teardown. The compatibility
projection deliberately preserves the narrower historical meaning of
`getOpponent()` and `inCombat()`. The ownership contract and deferred behavior
decisions are recorded in
[`combat-a04-engagement-event-ownership.md`](combat-a04-engagement-event-ownership.md).

A05.1 grows the gate from 32 to 35 scenarios. Its three added scenarios cover
resolved legacy request/result invariants, lifecycle-aware participant
snapshots, exact current PvM melee observation facts, and isolation from both
observer setup and callback failures. Production keeps the observer disabled,
and no damage or death authority moves. The source inventory and later
source-family boundaries are recorded in
[`combat-a05-damage-observation-foundation.md`](combat-a05-damage-observation-foundation.md).

A05.2 grows the gate from 35 to 38 scenarios. Its fixtures pin zero,
nonlethal, lethal/overkill, contribution, lifesteal, XP, hitsplat, terminal
callback, and transaction-result parity across both primary melee event
classes. They also exercise NPC-to-player settlement through Goblin's Tenacity
and preserve its distinct factual HP loss, displayed hit, and historical
post-hit hook value. Only the primary Hits/damage-update/hitsplat block moves;
the bounded authority contract is recorded in
[`combat-a05-direct-melee-damage-transaction.md`](combat-a05-direct-melee-damage-transaction.md).

A05.3 grows the gate from 38 to 42 scenarios. Its fixtures pin all maintained
player-ranged, thrown, player-magic, NPC, summon, Iban, and cannon primary
projectile variants; zero and overkill settlement; exact contribution, XP,
hitsplat, terminal callback, and transaction metadata; and shared-Hits
compatibility for both projectile styles. An executable exclusion fixture also
proves non-primary compatibility and chain-lightning damage remain outside the
transaction. Only the adjacent primary-impact Hits/damage-update/hitsplat block
moves; the boundary is recorded in
[`combat-a05-primary-projectile-damage-transaction.md`](combat-a05-primary-projectile-damage-transaction.md).

A05.4 grows the gate from 42 to 46 scenarios without moving production damage
authority. Its representative fixtures pin the compatibility helper's
death-before-presentation order, poison/burn distinctions, secondary
Magic/combat contribution, Frostbite/Thorns attribution, hitsplats, death
callback cardinality, and delayed god/Iban helper/chase behavior. The exhaustive
inventory and deliberately split follow-up families are recorded in
[`combat-a05-secondary-damage-characterization.md`](combat-a05-secondary-damage-characterization.md).

A05.4A grows the gate from 46 to 48 scenarios. Its fixtures exercise all six
event-local auxiliary Magic/true helpers across reciprocal melee, PvM melee,
and projectile owners. They pin stable transaction identities, mitigation
asymmetry, nonlethal and lethal/overkill settlement, return values,
contribution style, callback cardinality, and zero/fully mitigated exclusion.
Only Hits/update/hitsplat authority moves; the boundary is recorded in
[`combat-a05-auxiliary-damage-transaction.md`](combat-a05-auxiliary-damage-transaction.md).

A05.4B grows the gate from 48 to 52 scenarios by replacing one combined
reflection scenario with five policy-specific fixtures. They execute
Frostbite, Cleric Thorns, melee jewelry recoil, projectile recoil, and Divine
Retribution across every current event owner. The gate pins incoming-hit and
post-lifesteal order, attribution and contribution, stable transaction
metadata, overkill, player stat packets, Ring of Life, ranged reset, recursion
exclusions, caller-owned death, and simultaneous death. Chain lightning and
all later secondary families remain outside this migration. The boundary is
recorded in
[`combat-a05-reflection-damage-transaction.md`](combat-a05-reflection-damage-transaction.md).

A05.4C grows the gate from 52 to 59 scenarios. Its fixtures execute chain
lightning in all three event owners, Splinter, blood and death robe splashes,
Scythe cleave, Death Amulet, and Death Ring. They pin target eligibility,
signed-level filtering, area suppression, style contribution, summon-assist
policy, aggro, charge ownership, lifesteal, child death order, and exact
transaction metadata. Only each family's existing Hits/update/hitsplat block
moves; selection, formulas, resources, and terminal adapters remain local. The
boundary is recorded in
[`combat-a05-player-child-damage-transaction.md`](combat-a05-player-child-damage-transaction.md).

An independent poison-weapon regression brought the gate from 59 to 60
scenarios after A05.4C. A05.4D then grows it from 60 to 63 scenarios. Its
fixtures execute Balrog Magic splash, Elder Green Dragon owned attacks/burn,
and summon trait bonus damage. They pin target eligibility, signed-level
filtering, style-specific mitigation, True Defense inclusion/exclusion,
tracking, contribution, stat/party packets, Ring of Life, reflection order,
returned lethal facts, caller-owned death, and exact transaction metadata.
Only each family's existing Hits/update/hitsplat block moves; formulas, RNG,
selection, effects, packets, hooks, and terminal adapters remain local. The
boundary is recorded in
[`combat-a05-owned-npc-summon-damage-transaction.md`](combat-a05-owned-npc-summon-damage-transaction.md).

A05.4E grows the gate from 63 to 66 scenarios. Its full cast fixtures execute
god spells, Iban Blast, and Salarin elemental strikes through production cast
approach, projectile, scheduler, inventory, and XP paths. They pin the one-tick
delay, child eligibility, rune/XP behavior, Magic contribution, chase,
aggregate god-spell lifesteal, Salarin's missing second hitsplat/stat behavior,
and exact transaction metadata. Safely representable Salarin and nonlethal NPC
god/Iban settlement moves; lethal god/Iban helper settlement remains outside
because its death-before-presentation/contribution and raw-Hits behavior cannot
move without a gameplay delta. The boundary is recorded in
[`combat-a05-delayed-spell-damage-transaction.md`](combat-a05-delayed-spell-damage-transaction.md).

A05.5 grows the gate from 66 to 71 scenarios. Its fixtures pin ordinary NPC
reward/listener/removal order, reentrant exactly-once ownership, NPC and player
respawn generations, a deliberate listener failure, player death cleanup and
packets, tutorial survival, logged-out no-op behavior, and the ten-ID
plugin-owned compatibility boundary. Existing simultaneous-death fixtures
remain authoritative. The bounded lifecycle is recorded in
[`combat-a05-atomic-death-lifecycle.md`](combat-a05-atomic-death-lifecycle.md).

A05.6 keeps the gate at 71 scenarios and strengthens the existing
unknown-projectile fixture. Non-negative unclassified settlement now reports a
stable transaction identity; nonlethal, lethal-overkill, zero-contribution,
presentation-before-death, callback, and child-effect cardinality remain
executable. A signed negative admin compatibility value remains a direct,
unobserved HP adjustment and is pinned separately. The residual inventory and
stop boundary are recorded in
[`combat-a05-direct-hit-mutation-cleanup.md`](combat-a05-direct-hit-mutation-cleanup.md).

The integrated A06.1 baseline contains 72 scenarios. A06.1 adds immutable
launch snapshots and an exactly-once per-event impact ledger to the existing
projectile lifecycle scenario. Cancellation, the current spatial gate,
participant lifecycle compatibility, duplicate callbacks, scripted/benign
events, and failed-callback non-replay remain executable. The boundary is
recorded in
[`combat-a06-projectile-lifecycle-foundation.md`](combat-a06-projectile-lifecycle-foundation.md).

A06.2 grows the gate from 72 to 74 scenarios. Its fixtures launch every typed
producer identity, compare positional compatibility facades with equivalent
typed specifications, freeze every former constructor-tail parameter, and pin
visual-before-impact presentation order. Damage, resource, impact-policy, and
packet authority remain unchanged. The producer inventory and boundary are
recorded in
[`combat-a06-projectile-launch-specifications.md`](combat-a06-projectile-launch-specifications.md).

A06.3 characterization grows the gate from 74 to 75 scenarios without changing
production impact policy. Its executable matrix covers source/target death,
logout/reconnect, real same-object NPC respawn, source and paired movement,
teleport, signed-level transitions, the current global-only world-space gate,
collision changes, retargeting, launch-time protection, scripted/ball/base
benign differences, and independent shuriken siblings. The approved owner
choices and bounded implementation gates are recorded in
[`combat-a06-projectile-impact-policy-characterization.md`](combat-a06-projectile-impact-policy-characterization.md).

The focused A06.3 implementation retains 75 scenarios while converting the
decision-evidence fixture to the approved result matrix. It now asserts every
new typed invalidation reason; exact target/source/session and summon-owner
rules; player/NPC source-death exceptions; real target and source NPC reuse;
launch-domain and launch-origin range; general and hostile collision rechecks;
holy-water, gnome-ball, positional, and base-benign exceptions; and unchanged
retarget, protection, sibling, duplicate, and failure ordering. The production
boundary is recorded in
[`combat-a06-projectile-impact-policy.md`](combat-a06-projectile-impact-policy.md).

A06.4 grows the gate from 75 to 83 scenarios. Its fixtures prove typed,
exactly-once resource settlement for bow ammunition, shuriken duplication,
inventory-mode compatibility, ground/drop recovery, Magic runes and XP,
cannonballs, and shutdown after launch. The boundary is recorded in
[`combat-a06-projectile-resource-ledger.md`](combat-a06-projectile-resource-ledger.md).

A07.1 grows the gate from 83 to 84 scenarios. Its compiled fixture asserts the
39 exact current secondary-damage policy identities, family counts, immutable
lookup, and the evidence that 32 cannot be used as a total catalog capacity.
It does not select a per-phase work budget. Production call sites now consume
those same stable keys from the typed catalog without moving target selection,
proc execution,
damage, ordering, or death authority. The boundary is recorded in
[`combat-a07-secondary-effect-policy-foundation.md`](combat-a07-secondary-effect-policy-foundation.md).

A07.2 grows the gate from 84 to 85 scenarios. Its compiled fixture asserts the
71 exact current semantic effect identities, complete phase/style/gate/zero-hit/
RNG/state/recursion/presentation/owner metadata, immutable lookup and sets,
nine exact phase counts, and descriptive planning budgets with four reviewed
headroom slots. It also proves that semantic keys do not reuse A05 settlement
keys and that the catalog exposes no executor-shaped method. Current combat
callers do not consume the catalog. The inventory and active-plan audit are
recorded in
[`combat-a07-secondary-effect-descriptor-inventory.md`](combat-a07-secondary-effect-descriptor-inventory.md).

A07.3 grows the gate from 85 to 86 scenarios. Its compiled fixture proves
view-order preservation, signed-level isolation, current radius/living/
attackable/summon filters, intentional wall pass-through, Guard Dog
suppression, lazy per-child revalidation, eager compatibility snapshots, and
moving-primary versus fixed-terminal centers. Only compatible player-owned NPC
radius enumeration moves; damage, charge, contribution, aggro, presentation,
death, and unrelated AoE families remain with their owners. The boundary is
recorded in
[`combat-a07-player-owned-npc-radius-selection.md`](combat-a07-player-owned-npc-radius-selection.md).

A07.4 grows the gate from 86 to 88 scenarios. Its pre-migration and parity
fixtures prove chain revisits, its exact three-hop cap, chance-before-index RNG
order, continuation from a dead child's location, empty-selection draw count,
Splinter's proc-before-index order, signed-level membership, intentional wall
pass-through, removed anchor/candidate/owner behavior, and visible-respawning
compatibility. Candidate enumeration moves to separate chain and Splinter
policies; chance, damage, presentation, contribution, aggro, and death remain
event-owned. The boundary is recorded in
[`combat-a07-chain-random-traversal-policy.md`](combat-a07-chain-random-traversal-policy.md).

A07.5A grows the gate from 88 to 89 scenarios. Its pre-migration and parity
fixture executes reciprocal melee, PvM melee, and projectile-impact owners and
proves Ogre Stagger's full-set gate, one-draw success/failure behavior,
settled-zero eligibility, single-attack replacement state, and no-draw
rejection for incomplete equipment, dead targets, and non-player sources.
Only that identical proc unit moves; surrounding phase gates and leather
chains remain event-owned. The boundary is recorded in
[`combat-a07-ogre-stagger-proc.md`](combat-a07-ogre-stagger-proc.md).
