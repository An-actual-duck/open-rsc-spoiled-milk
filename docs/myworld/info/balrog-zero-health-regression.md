# Balrog set zero-health NPC regression

Worker branch: `fix/balrog-zero-health-enemies`, based on
`fbbc79c5551328642336edf84f568ea80f5f001a`.

## Cause and evidence

The equipped five-piece Balrog set configures Infernal Fire with a 40% proc
chance, maximum hit 18, and 12% defense debuff. Its Hell's Inferno followup
selects secondary NPCs within radius two and deals half the settled primary
proc damage, rounded up. This followup runs synchronously inside the on-hit
callback; it is not a separate delayed burn event or the Balrog monster's
magic splash.

`ProjectileEvent.inflictAuxiliaryMagicDamage(hitter, target, damage)` correctly
settled HP and recorded Magic contribution against `target`, but on lethal
damage called parameterless `handleDeath()`. That handler always finalized
`opponent` using `caster`. With a living primary opponent and a lethal splash,
the secondary remained at zero HP without acquiring death ownership, running
hooks, setting `killed`, removing its combat events, or scheduling respawn.
The primary could incorrectly be killed and rewarded despite retaining HP.
Later auxiliary damage and splash selection reject zero-HP targets, so they
cannot repair this missed transition.

The isolated harness reproduced the bug before the production edit: an
equipped ranged player rolled a six-damage proc against a 20-HP primary and
three-damage splash against a 3-HP secondary. The secondary reached zero HP
but remained `killed == false`. The test failed at
`family=2 primaryLethal=false secondaryLethal=true zero-HP splash victim must enter death`.
Read-only comparison confirms the same defective call exists in live revision
`fec94c8731b5521410963575ef0f2fa5c05ef0b3`.

The reciprocal and modern PvM melee callbacks already pass their actual target
to death handling. `ResolvedDamageTransaction` intentionally owns only HP and
presentation; attribution and death remain caller responsibilities.
`Npc.killedBy` owns exactly-once death acquisition, XP/kill/personal-drop
distribution and death listeners. Removal advances combat lifetime, cancels
target combat/effects and schedules the normal respawn; respawn clears
contributions and restores the live death generation. None of those policies
needs changing.

## Change

For lethal auxiliary projectile magic damage, preserve `handleDeath()` for
the primary opponent and call `target.killedBy(hitter)` for a secondary victim.
Contribution is still recorded before death. A secondary kill therefore does
not reset the player's primary ranged engagement. The proc rate, payload,
radius, visuals, debuff, reward policy, NPC monster splash, and primary death
behavior are unchanged.

Production changes are limited to `ProjectileEvent.java`. Tests are in
`server/test/com/openrsc/server/combat/BalrogZeroHealthRegression.java`, invoked
by `tests/myworld/test-balrog-zero-health-enemies.py` and wired into
`tests/myworld/test-all.sh`. Deterministic R2 ownership inventory JSON/Markdown
were refreshed with `python3 scripts/audit-server-r2.py --write`; dependency
baselines were not changed.

## Validation

- `./scripts/build-server.sh`: PASS, rebuilt core, gameplay overlay and plugins;
  shipped Ant/classpath audit passed. No JAR-dependent tests ran during rebuild.
- `python3 tests/myworld/test-balrog-zero-health-enemies.py`: PASS. Twenty
  combinations cover magic, ranged, throwing, modern PvM and reciprocal melee,
  lethal/nonlethal primary and secondary damage. Additional cases cover zero
  payload, capped overkill, shared contribution XP, largest-contributor kill
  credit, personal drops, reentrant death listeners, same-tick damage, multiple
  lethal victims in one scheduled impact, cancellation, duplicate callbacks,
  respawn, and old callbacks after respawn. It uses real production entities,
  equipped-set callbacks, damage/death handling and events without starting a
  server listener, client or database service.
- Existing Python regressions: leather set bonuses, boss leather tier
  progression, combat runtime invariants, combat exceptions, combat scenarios,
  NPC poison/death lifecycle, R2 boundary audit (6 tests), R2 dependency guard
  (3 tests), and native blocking overlay (2 tests): PASS.
- Twelve existing Java cases run directly in `CurrentCombatHarness`: PASS.
  These are Infernal Fire policies; Hell's Inferno math and splash policies;
  Balrog monster splash policies; secondary policy and descriptor inventories;
  ordinary NPC death/respawn, failed-death replay, plugin-owned compatibility;
  projectile cancellation, participant lifecycle invalidation, and duplicate
  callback settlement. The temporary runner supplies the same enabled recording
  observer as the main combat suite. Log: `/tmp/balrog-focused.log`.
- `git diff --check`: PASS.

The full `test_combat_strict` Ant gate does **not** pass on this checkout:
`CurrentCombatProjectileLifecycleCharacterization.currentSpatialGateInvalidatesImpact`
throws `IllegalArgumentException: Ordinary movement cannot leave native package terrain`
at line 481. Running the gate with the original base-revision projectile class
reproduced the identical exception before the set tests. Logs:
`/tmp/balrog-combat.log` and `/tmp/balrog-baseline-combat.log`.
No gate, terrain rule, overlay-255 fix or dependency baseline was weakened to
work around this unrelated fixture failure. The entire MyWorld suite was not run.

## Integration limits

Server-only fix; no client update is required. This prevents new missed death
transitions; it does not sweep or retroactively award kills for already broken
zero-HP NPC instances. Live recovery and activation remain manager-owned.
No live files, player data, maps, public commands, servers, releases or main
branch were changed. The broader spatial-fixture failure remains for manager
triage; it is outside this combat correction.
