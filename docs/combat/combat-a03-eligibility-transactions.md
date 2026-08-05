# A03 Combat Eligibility And Attack Transactions

A03 adapts the eligibility and validate/approach/commit concepts assessed from
Classic-Scape to Spoiled Milk's current combat, layered-world, plugin, and
summoning architecture. It does not adopt Classic-Scape combat formulas,
timing, engagement ownership, or content policy.

## Implemented boundary

`CombatEligibility` is a side-effect-free evaluator with stable style, phase,
and rejection identities. `Player.checkAttack` remains the compatibility entry
point for existing callers: it delegates its existing generic NPC/PvP rules to
the evaluator, sends the same player messages through
`CombatEligibilityMessageAdapter`, and retains the existing suspicious-player
diagnostic for an unattackable NPC.

Each player owns one `PlayerAttackTransaction`. A manual or retaliation start
captures the exact source and target lifetime, source session, world, style,
channel, selected spell where applicable, relevant ranged equipment, command
tick, and a bounded 100-tick lease. Only that exact intent can commit. A newer
manual command supersedes an older command; a pending manual command has
priority over passive retaliation. Replacing its exact walk action, changing
its loadout, a world or layered-domain mismatch, participant removal or respawn,
death, logout, failed resources, a blocking plugin, an unavailable default
plugin, or expiry rejects and clears it without disturbing a newer intent.

The lease is a stale-callback safety bound, not a new approach timer. Existing
walk actions, approach radii, path checks, and scheduler cadence remain the
movement authority. Expiry is enforced when pending state is observed or a new
attack is issued, so an abandoned manual intent cannot suppress retaliation
after its lease.

## Style and ordering inventory

- Manual melee preserves `WalkToMobAction` and dispatches the existing
  `AttackNpcTrigger`/`AttackPlayerTrigger` only after approach and compatibility
  eligibility. The default plugin commits the exact pending intent. A custom
  blocking plugin owns its callback and clears the default intent; its existing
  direct `startCombat` calls remain valid.
- Manual bow and throwing starts preserve the current projectile and approach
  radii, face direction, PvP side effects, event reuse, and plugin behavior.
  Destructive reset and summon-engagement recording occur only with a
  successful event commit.
- Immediate targeted magic preserves spell sanity, content/plugin gates,
  pathing, rune checks, spell-specific execution, and projectile settlement.
  Failed path, target, permission, or rune checks now close only their pending
  intent. Autocast installation and retargeting use the same transaction
  boundary.
- Melee, ranged, throwing, and autocast retaliation use transaction source
  priority. An accepted manual command cannot be overwritten by a passive
  counterattack. The old two-argument `MagicCombatEvent.start`, direct
  `Mob.startCombat`, and `Player.checkAttack` signatures remain compatibility
  facades.
- NPC and player combat lifetimes advance on terminal removal/death/logout.
  Delayed work captures that generation, preventing an event or approach from
  attaching to a reused respawn object or later player session.

## Content and compatibility ownership

A03 intentionally does not move stateful content restrictions into the pure
evaluator. Their callback timing, dialogue, animation, quest mutation, or NPC
side effects remain at their established layer:

- `AttackHandler` retains utility-summon interaction, summon attack rejection,
  ogre-training range restrictions, Mage Arena battle-mage progression, and
  NPC retreat/re-engagement gates.
- `SpellHandler` retains Delrith/Silverlight, Lucien/Temple of Ikov, special
  unattackable-opponent exceptions, and `SpellNpcTrigger`/`SpellPlayerTrigger`
  ownership.
- the default melee plugin retains Kolodion's protected area and
  `AttackPlayer.attackPrevented`, including bank and guard behavior;
- `Mob.startPvmCombat` remains the final summon-versus-player/NPC guard for
  scripted and compatibility starts; and
- `Player.checkAttack` retains PvP world mode, party, duel, reattack, PK-mode,
  banker, Wilderness-level, and invisibility/invulnerability semantics.

The shared evaluator can reason-code summon policy when a caller requests it,
but the structural transaction does not impose that optional policy. This is
deliberate: current manual AttackHandler attacks reject summons before issuing
an intent, while other compatibility paths have their own established gates.
Turning an optional structural check into a new global permission would violate
A03's stop condition. Any remaining cross-entry-point permission asymmetry
requires a separate behavior decision.

## Regression coverage

The isolated Ant combat gate now runs 26 executable scenarios. A03 adds
coverage for:

- generic PvP, party, non-attackable-NPC, summon, and signed-layer reason codes
  plus exact legacy message text;
- latest-command wins, exact default-plugin commit, stale plugin-facade refusal,
  and blocking-plugin callback order;
- denial preserving the active melee encounter;
- exact walk replacement, ranged-loadout change, lazy lease expiry, participant
  generation change, logout, and death cleanup;
- successful bow, throwing, and manual magic starts, plus missing-rune rollback;
  and
- manual-command priority over autocast retaliation.

The unchanged A01/A02 scenarios continue to pin formula output, XP sharing,
projectile cadence and settlement, retreat behavior, poison death/respawn,
Cleric ordering, summon contribution, scythe/shuriken behavior, support AoE,
and exactly-once death callbacks.

## Provenance

The implementation is a scoped adaptation of the concepts in Classic-Scape commits
`1f6bc8e4173d2686b30aaa0568164a473340c168` (central eligibility) and
`3665a52dda1f130637e1cbc729ae4c303234258c` (attack transactions), with the
plugin-callback risk highlighted by
`aa8e909d656ba5c5e5219d4507f50bec5cd96875`. Those commits are authored by
`aicovergod <lewisshuffle136@gmail.com>`; the final implementation commit retains
that substantial design/source attribution with a co-author trailer. No commit
was cherry-picked and no wholesale architecture or content tree was imported.
Both repositories retain the same AGPLv3 license.

## Verification record

- `./server/test_combat` passes all 26 scenarios. Its deliberate-failure and
  zero-scenario fixtures both fail at their advertised gates.
- secondary `server/gradlew test` delegates to the authoritative Ant combat
  target and passes; it does not establish Gradle build parity.
- `./scripts/build-server.sh` compiles 973 core and 492 plugin sources, validates
  the shipped Ant dependency/classpath inventory, and builds both artifacts.
- the focused combat, interaction, exception, Hits-XP, poison, Cleric,
  summoning, projectile, NPC-style/element, shuriken/scythe, and layered-world
  checks pass. The interaction source check was updated to the already-current
  `getCombatRandom()` signature and whitespace-insensitive matching; no
  production drop behavior changed.
- changed-code javac, Checkstyle, PMD, Ruff, and SpotBugs gates pass with no new
  warning fingerprints. SpotBugs observes the existing 394-finding baseline.
- neither production artifact contains `CurrentCombat` test classes; the
  plugin artifact retains 751 plugin class entries.

## Stop line and follow-up

A03 does not change damage, accuracy, defense, XP, costs, attack cadence,
projectile timing, aggression, loot, kill credit, packet schema, or persistence.
It also does not replace the existing independent combat/event fields. That
ownership problem remains A04. If review finds a changed permission, message,
approach radius, plugin callback order, or observable packet order, stop rather
than rationalizing it as a transaction improvement.
