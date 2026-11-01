# Cosmic combat luck implementation review

Completed source candidate, October31,2026. This replaces Cosmic robes' critical chance;
Zamorak hostile critical/Stagger/execution spells remain inactive. The active
Cleric roster remains17. No item identities, art, archives or protocol change.

Each equipped Cosmic piece contributes1percentage point per tier, capped50%.
For each reviewed named roll, evaluate the existing pure candidate first. A
successful luck check permits one more candidate, selecting higher damage or
mitigation, or success for a boolean check. No luck at0means no extra draw.
Guaranteed checks retain their original sampler but do not roll luck. A10%
check at50% luck becomes14.5%; a15% authored critical check becomes21.375%.
Normal maxima remain noncritical. There is no enabled modern critical chance
provider after this replacement; the generic authored resolver remains available.

## Source-family audit

| Family | Integration and ownership |
| --- | --- |
| Modern melee/ranged/thrown/Magic | Complete `CombatFormula.rollDamage` distribution, including existing biases, once per amount; source Player owns luck. Offhand and player-owned secondary callers share it. |
| Defense mitigation | Separate victim-owned integer mitigation candidate before subtraction; no second luck on final subtraction. |
| Legacy God spell amount | Actor-aware legacy amount entrypoint opts in; source-less helpers cannot infer equipment. |
| Elemental sword, Hell Blaze, weapon breath | Chance and random damage are distinct named rolls. Random animation/element choice remains unchanged. |
| Blue/Red/Earth/Baby/Elder armor | Existing eligibility first; pure chance and random damage candidates selected before one payload invocation. |
| Infernal Fire and Chaos chain | Chance/payload independently selected; callbacks once; chain checks per admitted hop, target selection and hop caps unchanged. |
| Whip, Sticky and pendant | Actual source/wearer owns chance; pendant random damage percent favors wearer. Existing immunity/eligibility runs first. Sticky charge accumulator1..3 is unchanged. |
| Poison weapon/armor | Original integer predicate selected before exactly one adaptive chance/failure-state mutation. Poisoned ammo integer1/8or1/50 checks separately opt in. Dragon armor breath poison checks also opt in. |
| Startle/Acid/Frostbite/Splinter/Guthix poison | Attack-triggered actual Player caster checks opt in; NPC/summon casters inherit no owner luck. |
| Recoil and Divine Retribution | Defender/wearer owns the beneficial chance; deterministic reflected amounts are not independently rerolled. |
| Goblin Tenacity | Explicit typed combat boundary opts in. Generic Skills, quest/environment and ordinary raw HP subtraction retain the original chance. Reviewed raw dragon-flame callers use a narrow legacy combat adapter, preserving settlement/death order. Poison's typed pre-settlement survival check opts in only for durable PLAYER/NPC provenance, even after that actor disappears; source-less traps and ENVIRONMENT/SCRIPT provenance keep the base check. |
| Giant Might, death amulet, Soul Ring | Beneficial combat check / random outgoing burst amount opts in. Soul Ring snapshots luck before reset/teleport; generic death-item loss is unchanged. |
| Combat capes and rune preservation | Ranged/throwing cape checks opt in. Magic cape and rune preservation only opt in for explicitly damaging spell context; utility/skilling casts do not, even while fighting. |

## Preserved boundaries

Luck never replays an attack, effect, damage settlement, resource removal, XP,
death or target selection. Launch-owned damage is already selected at launch;
later equipment changes do not rewrite flight receipts. Existing impact-owned
proc equipment checks retain their prior timing. Forced Stagger zero still
bypasses outgoing rolls/procs; non-corporeal nature admission is unchanged.

NPC damage distribution and summons never acquire their owner's robe luck.
Recurring hostile curse ticks, random healing quantities, loot, gathering,
production, random choice between effects/targets, and stateful armor charge
increments are excluded. Kolodion's NPC-only proc is unchanged. Source-less
legacy helpers remain source-less. The optional OSRS formula/profile (disabled
in the supported MyWorld configuration) retains its separate hit/cape mechanics;
this change does not redesign that profile or its Strength-cape critical label.

## Verification

Durable evidence: `/home/justin/.local/share/cosmic-combat-luck-review-20261031/worker-evidence`.
`artifact-and-preservation.json` records the four matched artifact hashes and
preservation assertions. All50Cosmic inventory definitions change description
only: IDs, other fields, row count and art remain unchanged. No asset/archive,
map, configuration or protocol edits. No game/client/server session was launched.

Both maintained Java8 builds passed with512MiB SerialGC, followed by these
bounded offline gates (actual source/runtime, not live accounts):

- `test-combat-luck.py`:32 exact probability, threshold, sampler-count and
  candidate/tie/critical assertions.
- `test-combat-luck-runtime.py`:90 native checks: real robe aggregation,
  offense/defender ownership, authored critical source, adaptive poison once,
  Whip immunity, defensive pendant chance+amount effect once, durable poison
  actor/trap scope and base survival RNG, actual damaging SpellHandler packet
  versus utility Alchemy while fighting with exact inventory removal updates.
- `test-shared-attack-state.py`:88; `test-shared-attack-runtime.py`:173 actual
  melee/bow/thrown/Magic commitments and flight settlement, suppression,
  additive deadlines and launch-owned lucky damage surviving equipment removal.
- `test-enchanting-data.py` and `test-enchanted-robe-effects.py`:pass.
- `test-cleric-blessings.py`:361; `test-cleric-projectile.py`:30;
  `test-cleric-hostile-producers.py`:24, preserving17active entries, typed
  damage, phase, hostile effects and packet/resource boundaries.
- `test-floating-hit-splats.py`:all gates pass, including fresh-JVM settings3
  and9,216pixel/glyph/text-only appearance assertions.
- Ant `test_combat`:145 native characterization scenarios pass. AllnineSlayer
  targets pass (`test_slayer_leather_effects`, `test_slayer_rewards`,
  `test_slayer_mobility_shield`, `test_abyssal_demon_combat`,
  `test_dark_beast_combat`, `test_bloodveld_combat`, `test_terror_dog_combat`,
  `test_naga_combat`, `test_thunder_spell_casting`). Slow115, battleaxe coating
  runtime1,042 and antidote102 also pass.

Commands use `JAVA_HOME=/home/justin/jdk8u482-b08`, Python entrypoints under
`tests/myworld`, and `ANT_OPTS='-Xmx512m -XX:+UseSerialGC'` with
`scripts/build-server.sh` / `scripts/build-client.sh`. Ant targets run through
`myworld_ant_build` after sourcing `scripts/lib/myworld-common.sh`; forked
fixtures also use the512MiB SerialGC bound. Heavy jobs run serially. Failure
logs from fixture development are preserved alongside final success receipts.

Actual supported-profile producer paths, generic authored critical behavior,
zero-luck replay and exclusion boundaries are exercised; this is not a live
balance/playtest or a claim of a stochastic frequency benchmark. OptionalOSRS,
source-less legacy amounts, summon inheritance and all excluded families above
remain intentionally outside this implementation. Zamorak hostile activation
remains separate. Manager integration/private package activation follow review.
